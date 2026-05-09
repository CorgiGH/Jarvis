package jarvis.tutor

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

enum class GapType { COMMAND, CONCEPT, SYNTAX, LIBRARY, THEOREM }
enum class GapTrigger { EXPLICIT_ASK, SYNTAX_ERROR, REPEATED_FAILURE, MANUAL_FLAG }
enum class GapSource { LESSON_LOCAL, PAST_GAP_REUSE, EXTERNAL_DOC, LLM_GROUNDED, LLM_PURE }
enum class GapResolved { USER_TYPED, USER_DISMISSED, USER_MARKED_DONE }

@Serializable
data class KnowledgeGap(
    val id: String, val userId: String, val taskId: String?,
    val topic: String, val language: String?, val type: GapType,
    val trigger: GapTrigger,
    @Serializable(InstantIso8601Serializer::class) val filledAt: Instant,
    val source: GapSource,
    val content: String, val exampleCode: String?, val sourceCitation: String?,
    val resolvedBy: GapResolved?,
    val reusedCount: Int,
    val fsrsCardId: String?,
)

object KnowledgeGapsTable : Table("knowledge_gaps") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val topic = varchar("topic", 256)
    val language = varchar("language", 16).nullable()
    val type = varchar("type", 16)
    val filledAt = timestamp("filled_at")
    val reusedCount = integer("reused_count")
    val fsrsCardId = varchar("fsrs_card_id", 26).nullable()
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, topic) }
}

class KnowledgeGapRepo(private val db: Database, private val ledgerDir: Path) {
    fun append(g: KnowledgeGap): String = transaction(db) {
        KnowledgeGapsTable.insert {
            it[id] = g.id
            it[userId] = g.userId
            it[topic] = g.topic
            it[language] = g.language
            it[type] = g.type.name
            it[filledAt] = g.filledAt
            it[reusedCount] = g.reusedCount
            it[fsrsCardId] = g.fsrsCardId
        }
        val file = ledgerDir.resolve("knowledge_gaps_${g.userId}.jsonl")
        Files.createDirectories(ledgerDir)
        val line = TutorTypes.tutorJson.encodeToString(KnowledgeGap.serializer(), g)
        Files.write(file, (line + "\n").toByteArray(),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        g.id
    }

    fun findByTopic(userId: String, topic: String): KnowledgeGap? = transaction(db) {
        val row = KnowledgeGapsTable.selectAll()
            .where { (KnowledgeGapsTable.userId eq userId) and (KnowledgeGapsTable.topic eq topic) }
            .orderBy(KnowledgeGapsTable.filledAt, SortOrder.DESC)
            .firstOrNull() ?: return@transaction null
        loadFromJsonl(userId, row[KnowledgeGapsTable.id])
    }

    fun bumpReuse(id: String) = transaction(db) {
        val cur = KnowledgeGapsTable.selectAll()
            .where { KnowledgeGapsTable.id eq id }
            .single()[KnowledgeGapsTable.reusedCount]
        KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
            it[reusedCount] = cur + 1
        }
    }

    private fun loadFromJsonl(userId: String, id: String): KnowledgeGap? {
        val file = ledgerDir.resolve("knowledge_gaps_$userId.jsonl")
        if (!Files.exists(file)) return null
        Files.lines(file).use { stream ->
            val match = stream
                .map { TutorTypes.tutorJson.decodeFromString(KnowledgeGap.serializer(), it) }
                .filter { it.id == id }
                .findFirst()
                .orElse(null) ?: return null
            val current = transaction(db) {
                KnowledgeGapsTable.selectAll()
                    .where { KnowledgeGapsTable.id eq id }
                    .single()[KnowledgeGapsTable.reusedCount]
            }
            return match.copy(reusedCount = current)
        }
    }
}

object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
