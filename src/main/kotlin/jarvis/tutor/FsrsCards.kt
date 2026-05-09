package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

enum class FsrsSource { GAP_PROMOTION, RUBRIC_CRITERION, MANUAL }

data class FsrsState(
    val difficulty: Double, val stability: Double, val retrievability: Double,
    val dueAt: Instant, val lastReviewedAt: Instant, val lapses: Int,
)

data class TutorCard(
    val id: String, val userId: String,
    val source: FsrsSource, val sourceRef: String,
    val front: String, val back: String,
    val state: FsrsState,
)

object FsrsCardsTable : Table("fsrs_cards") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val sourceType = varchar("source", 24)
    val sourceRef = varchar("source_ref", 32)
    val front = text("front")
    val back = text("back")
    val difficulty = double("difficulty")
    val stability = double("stability")
    val retrievability = double("retrievability")
    val dueAt = timestamp("due_at")
    val lastReviewedAt = timestamp("last_reviewed_at")
    val lapses = integer("lapses")
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, dueAt) }
}

class FsrsCardRepo(private val db: Database) {
    fun insert(c: TutorCard) = transaction(db) {
        FsrsCardsTable.insert {
            it[id] = c.id
            it[userId] = c.userId
            it[sourceType] = c.source.name
            it[sourceRef] = c.sourceRef
            it[front] = c.front
            it[back] = c.back
            it[difficulty] = c.state.difficulty
            it[stability] = c.state.stability
            it[retrievability] = c.state.retrievability
            it[dueAt] = c.state.dueAt
            it[lastReviewedAt] = c.state.lastReviewedAt
            it[lapses] = c.state.lapses
        }
    }

    fun findDueForUser(userId: String, asOf: Instant): List<TutorCard> = transaction(db) {
        FsrsCardsTable.selectAll()
            .where { (FsrsCardsTable.userId eq userId) and (FsrsCardsTable.dueAt lessEq asOf) }
            .orderBy(FsrsCardsTable.dueAt, SortOrder.ASC)
            .map { it.toCard() }
    }

    private fun ResultRow.toCard() = TutorCard(
        id = this[FsrsCardsTable.id],
        userId = this[FsrsCardsTable.userId],
        source = FsrsSource.valueOf(this[FsrsCardsTable.sourceType]),
        sourceRef = this[FsrsCardsTable.sourceRef],
        front = this[FsrsCardsTable.front],
        back = this[FsrsCardsTable.back],
        state = FsrsState(
            difficulty = this[FsrsCardsTable.difficulty],
            stability = this[FsrsCardsTable.stability],
            retrievability = this[FsrsCardsTable.retrievability],
            dueAt = this[FsrsCardsTable.dueAt],
            lastReviewedAt = this[FsrsCardsTable.lastReviewedAt],
            lapses = this[FsrsCardsTable.lapses],
        ),
    )
}
