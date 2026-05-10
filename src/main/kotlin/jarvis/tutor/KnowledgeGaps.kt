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
    val taskId = varchar("task_id", 26).nullable()
    val topic = varchar("topic", 256)
    val language = varchar("language", 16).nullable()
    val type = varchar("type", 16)
    val trigger = varchar("trigger", 32)
    val filledAt = timestamp("filled_at")
    val content = text("content")
    val exampleCode = text("example_code").nullable()
    val sourceCitation = text("source_citation").nullable()
    val resolvedBy = varchar("resolved_by", 32).nullable()
    val reusedCount = integer("reused_count")
    val fsrsCardId = varchar("fsrs_card_id", 26).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, topic) }
}

class KnowledgeGapRepo(private val db: Database, private val ledgerDir: Path) {
    fun append(g: KnowledgeGap, taskId: String? = g.taskId, trigger: GapTrigger = g.trigger,
               content: String = g.content, exampleCode: String? = g.exampleCode,
               sourceCitation: String? = g.sourceCitation,
               now: Instant = Instant.now()): String = transaction(db) {
        KnowledgeGapsTable.insert {
            it[id] = g.id
            it[userId] = g.userId
            it[KnowledgeGapsTable.taskId] = taskId
            it[topic] = g.topic
            it[language] = g.language
            it[type] = g.type.name
            it[KnowledgeGapsTable.trigger] = trigger.name
            it[filledAt] = g.filledAt
            it[KnowledgeGapsTable.content] = content
            it[KnowledgeGapsTable.exampleCode] = exampleCode
            it[KnowledgeGapsTable.sourceCitation] = sourceCitation
            it[resolvedBy] = g.resolvedBy?.name
            it[reusedCount] = g.reusedCount
            it[fsrsCardId] = g.fsrsCardId
            it[createdAt] = now
            it[updatedAt] = now
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

    private fun rowToGap(row: org.jetbrains.exposed.sql.ResultRow): KnowledgeGap = KnowledgeGap(
        id = row[KnowledgeGapsTable.id],
        userId = row[KnowledgeGapsTable.userId],
        taskId = row[KnowledgeGapsTable.taskId],
        topic = row[KnowledgeGapsTable.topic],
        language = row[KnowledgeGapsTable.language],
        type = GapType.valueOf(row[KnowledgeGapsTable.type]),
        trigger = GapTrigger.valueOf(row[KnowledgeGapsTable.trigger]),
        filledAt = row[KnowledgeGapsTable.filledAt],
        source = GapSource.LLM_GROUNDED,
        content = row[KnowledgeGapsTable.content],
        exampleCode = row[KnowledgeGapsTable.exampleCode],
        sourceCitation = row[KnowledgeGapsTable.sourceCitation],
        resolvedBy = row[KnowledgeGapsTable.resolvedBy]?.let { GapResolved.valueOf(it) },
        reusedCount = row[KnowledgeGapsTable.reusedCount],
        fsrsCardId = row[KnowledgeGapsTable.fsrsCardId],
    )

    fun findById(id: String): KnowledgeGap? = transaction(db) {
        KnowledgeGapsTable.selectAll().where { KnowledgeGapsTable.id eq id }
            .singleOrNull()?.let(::rowToGap)
    }

    fun listForUser(userId: String, limit: Int = 200): List<KnowledgeGap> = transaction(db) {
        KnowledgeGapsTable.selectAll()
            .where { KnowledgeGapsTable.userId eq userId }
            .orderBy(KnowledgeGapsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map(::rowToGap)
    }

    fun listForTask(userId: String, taskId: String): List<KnowledgeGap> = transaction(db) {
        KnowledgeGapsTable.selectAll()
            .where { (KnowledgeGapsTable.userId eq userId) and (KnowledgeGapsTable.taskId eq taskId) }
            .orderBy(KnowledgeGapsTable.createdAt, SortOrder.ASC)
            .map(::rowToGap)
    }

    /** Idempotent on (userId, taskId, topic). Hit increments reusedCount + returns existing id. */
    fun upsertByTriple(g: KnowledgeGap, taskId: String?, content: String,
                       exampleCode: String? = null, sourceCitation: String? = null,
                       now: Instant = Instant.now()): String = transaction(db) {
        val existing = KnowledgeGapsTable.selectAll().where {
            (KnowledgeGapsTable.userId eq g.userId) and
            (if (taskId == null) KnowledgeGapsTable.taskId.isNull()
             else KnowledgeGapsTable.taskId eq taskId) and
            (KnowledgeGapsTable.topic eq g.topic)
        }.firstOrNull()
        if (existing != null) {
            val id = existing[KnowledgeGapsTable.id]
            KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
                it[reusedCount] = existing[KnowledgeGapsTable.reusedCount] + 1
                it[updatedAt] = now
            }
            return@transaction id
        }
        append(g.copy(taskId = taskId), taskId = taskId, content = content,
               exampleCode = exampleCode, sourceCitation = sourceCitation, now = now)
    }

    fun markResolved(id: String, by: GapResolved, now: Instant = Instant.now()): Boolean = transaction(db) {
        val n = KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
            it[resolvedBy] = by.name
            it[updatedAt] = now
        }
        n > 0
    }

    fun incrementReused(id: String, now: Instant = Instant.now()): Boolean = transaction(db) {
        val cur = KnowledgeGapsTable.selectAll()
            .where { KnowledgeGapsTable.id eq id }
            .firstOrNull()?.get(KnowledgeGapsTable.reusedCount) ?: return@transaction false
        KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
            it[reusedCount] = cur + 1
            it[updatedAt] = now
        }
        true
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
