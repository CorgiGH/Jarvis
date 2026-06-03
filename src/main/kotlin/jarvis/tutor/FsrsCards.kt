package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

enum class FsrsSource { GAP_PROMOTION, RUBRIC_CRITERION, MANUAL }

data class FsrsState(
    val difficulty: Double, val stability: Double, val retrievability: Double,
    val dueAt: Instant, val lastReviewedAt: Instant, val lapses: Int,
)

/** Card lifecycle status (CHANGE 1). 3-value, not boolean:
 *  ACTIVE = schedulable; QUARANTINED = audit-not-cleared; PAUSED = student REPORT-WRONG. */
enum class CardStatus { ACTIVE, QUARANTINED, PAUSED }

data class TutorCard(
    val id: String, val userId: String,
    val source: FsrsSource, val sourceRef: String,
    val front: String, val back: String,
    val state: FsrsState,
    // CHANGE 1 — nullable at DDL; status backfilled to ACTIVE post-ALTER.
    val kcId: String? = null,
    val status: CardStatus? = null,
    val pausedAt: Instant? = null,
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
    // CHANGE 1 (added cols, nullable — Exposed ALTER adds them NULL on existing rows;
    // explicit post-ALTER backfill sets status='ACTIVE' in the same boot txn).
    val kcId = varchar("kc_id", 64).nullable()
    val status = varchar("status", 16).nullable()
    val pausedAt = timestamp("paused_at").nullable()
    override val primaryKey = PrimaryKey(id)
    init {
        index(false, userId, dueAt)
        // CHANGE 1 / Task 9 (B2, §H): dedup key for RUBRIC_CRITERION cards.
        // uniqueIndex so a second raw INSERT for the same (userId, source, kcId) is storage-rejected
        // (mirrors TasksTable.uniqueIndex pattern).  GAP_PROMOTION / MANUAL cards have kcId=NULL and
        // SQLite treats each NULL as distinct, so the unique constraint does NOT block them.
        uniqueIndex("fsrs_cards_user_source_kc_unique", userId, sourceType, kcId)
    }
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
            it[kcId] = c.kcId
            // CHANGE 1 / M-SEED: a freshly inserted card is ACTIVE by default (the queue
            // filter is status='ACTIVE'; never insert a NULL status). Seed paths get ACTIVE
            // explicitly via the TutorCard.status default = null -> ACTIVE here.
            it[status] = (c.status ?: CardStatus.ACTIVE).name
            it[pausedAt] = c.pausedAt
        }
    }

    fun findDueForUser(userId: String, asOf: Instant): List<TutorCard> = transaction(db) {
        FsrsCardsTable.selectAll()
            .where {
                (FsrsCardsTable.userId eq userId) and
                    (FsrsCardsTable.dueAt lessEq asOf) and
                    (FsrsCardsTable.status eq CardStatus.ACTIVE.name)
            }
            .orderBy(FsrsCardsTable.dueAt, SortOrder.ASC)
            .map { it.toCard() }
    }

    /** Lookup by card id, scoped to user. Used by the grade route. */
    fun findById(cardId: String, userId: String): TutorCard? = transaction(db) {
        FsrsCardsTable.selectAll()
            .where { (FsrsCardsTable.id eq cardId) and (FsrsCardsTable.userId eq userId) }
            .singleOrNull()?.toCard()
    }

    /**
     * NEW (B2, §H) — SELECT-then-UPDATE-or-INSERT inside the CALLER's txn (no own transaction).
     *
     * Dedup key = (userId, source=RUBRIC_CRITERION, kcId): one card per (user, KC).
     * A re-call UPDATEs the existing row's front/back/state, never inserts a duplicate.
     *
     * Needs the kc_id column (CHANGE 1, Task 4) and the (userId, sourceType, kcId) unique index.
     * GAP_PROMOTION / MANUAL cards never go through this method; they keep kc_id=NULL (§H note).
     *
     * @param tx       The caller's Exposed Transaction.
     * @param userId   The owner user id.
     * @param kcId     The KC id — stored in the new kc_id column AND used as the dedup key.
     * @param front    The card front text (stem_template ?: name_en, resolved by caller).
     * @param back     The card back text (canonicalAnswer, resolved by caller).
     * @param state    The FSRS scheduling state for the new/updated card.
     * @return         The upserted TutorCard.
     */
    fun upsertRubricCriterion(
        tx: Transaction,
        userId: String,
        kcId: String,
        front: String,
        back: String,
        state: FsrsState,
    ): TutorCard {
        val existing = FsrsCardsTable.selectAll()
            .where {
                (FsrsCardsTable.userId eq userId) and
                    (FsrsCardsTable.sourceType eq FsrsSource.RUBRIC_CRITERION.name) and
                    (FsrsCardsTable.kcId eq kcId)
            }
            .singleOrNull()
        return if (existing == null) {
            // INSERT: new card for this (user, RUBRIC_CRITERION, kc)
            val newId = TutorTypes.ulid()
            FsrsCardsTable.insert {
                it[id] = newId
                it[FsrsCardsTable.userId] = userId
                it[sourceType] = FsrsSource.RUBRIC_CRITERION.name
                // sourceRef = kcId (the rubric/stem id per §H)
                it[sourceRef] = kcId
                it[FsrsCardsTable.front] = front
                it[FsrsCardsTable.back] = back
                it[difficulty] = state.difficulty
                it[stability] = state.stability
                it[retrievability] = state.retrievability
                it[dueAt] = state.dueAt
                it[lastReviewedAt] = state.lastReviewedAt
                it[lapses] = state.lapses
                it[FsrsCardsTable.kcId] = kcId
                it[status] = CardStatus.ACTIVE.name
                it[pausedAt] = null
            }
            FsrsCardsTable.selectAll()
                .where { FsrsCardsTable.id eq newId }
                .single()
                .toCard()
        } else {
            // UPDATE: re-grade → update front/back/state (never change the id or source)
            val existingId = existing[FsrsCardsTable.id]
            FsrsCardsTable.update({ FsrsCardsTable.id eq existingId }) {
                it[FsrsCardsTable.front] = front
                it[FsrsCardsTable.back] = back
                it[difficulty] = state.difficulty
                it[stability] = state.stability
                it[retrievability] = state.retrievability
                it[dueAt] = state.dueAt
                it[lastReviewedAt] = state.lastReviewedAt
                it[lapses] = state.lapses
            }
            FsrsCardsTable.selectAll()
                .where { FsrsCardsTable.id eq existingId }
                .single()
                .toCard()
        }
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
        kcId = this[FsrsCardsTable.kcId],
        status = this[FsrsCardsTable.status]?.let { CardStatus.valueOf(it) },
        pausedAt = this[FsrsCardsTable.pausedAt],
    )
}
