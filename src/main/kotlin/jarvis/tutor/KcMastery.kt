package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/** Per-KC mastery signal. PK = (userId, kcId). */
object KcMasteryTable : Table("kc_mastery") {
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val kcId = varchar("kc_id", 64)
    val ewmaScore = double("ewma_score")
    val observations = integer("observations")
    val lastGradedAt = timestamp("last_graded_at")
    // CHANGE 2 (added cols, nullable). `phase` STORED, not derived at call time; sole
    // owner = PhaseModel.transition (called in the CHANGE-2 backfill + recordIn in Phase 3).
    // `entry_phase` NULL => treat as intro.
    val phase = varchar("phase", 16).nullable()
    val entryPhase = varchar("entry_phase", 16).nullable()
    override val primaryKey = PrimaryKey(userId, kcId)
}

data class KcMastery(
    val userId: String,
    val kcId: String,
    val ewmaScore: Double,
    val observations: Int,
    val lastGradedAt: Instant,
    // CHANGE 2 — null for a never-phased / pre-migration row.
    val phase: Phase? = null,
    val entryPhase: Phase? = null,
) {
    /** entry_phase NULL => treat as intro (CHANGE 2 rule). */
    val effectiveEntryPhase: Phase get() = entryPhase ?: Phase.intro

    /** Explicit mastery rule: sustained high EWMA over enough observations. */
    val mastered: Boolean get() = ewmaScore >= MASTERY_THRESHOLD && observations >= MIN_OBSERVATIONS

    companion object {
        const val EWMA_ALPHA = 0.4         // weight on the newest score
        const val MASTERY_THRESHOLD = 0.8  // EWMA needed to count as mastered
        const val MIN_OBSERVATIONS = 3     // minimum graded attempts before mastery can latch
    }
}

class KcMasteryRepo(private val db: Database) {

    /** Read the row; MUST be called from within a transaction. */
    private fun readRow(userId: String, kcId: String): KcMastery? =
        KcMasteryTable.selectAll()
            .where { (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kcId) }
            .map {
                KcMastery(
                    it[KcMasteryTable.userId], it[KcMasteryTable.kcId],
                    it[KcMasteryTable.ewmaScore], it[KcMasteryTable.observations],
                    it[KcMasteryTable.lastGradedAt],
                    phase = it[KcMasteryTable.phase]?.let { p -> Phase.valueOf(p) },
                    entryPhase = it[KcMasteryTable.entryPhase]?.let { p -> Phase.valueOf(p) },
                )
            }
            .singleOrNull()

    fun get(userId: String, kcId: String): KcMastery? = transaction(db) { readRow(userId, kcId) }

    // NOTE: read-then-write. Safe for the current single-user deployment (writes serialize on
    // SQLite). If multi-user lands, replace with an atomic upsert to avoid a TOCTOU PK race.

    /**
     * NEW (B3, §G) — txn-LESS. Caller owns the transaction; opens NO transaction of its own.
     *
     * Applies one graded score via the EWMA rule AND computes the new Phase (via
     * PhaseModel.transition) and upserts kc_mastery.ewma_score/observations/last_graded_at
     * AND .phase in the SAME caller-owned txn.
     *
     * PhaseModel is called ONLY here (and the CHANGE-2 backfill in TutorMigration).
     *
     * @param tx  The caller's Exposed Transaction — all writes target this txn.
     */
    fun recordIn(
        tx: Transaction,
        userId: String,
        kcId: String,
        score: Double,
        now: Instant = Instant.now(),
    ): KcMastery {
        // Use the caller's transaction as the implicit receiver for Exposed table ops.
        // (Exposed table DSL methods like selectAll/insert/update use the thread-local
        //  transaction context; since the caller owns the txn, this executes inside it.)
        val existing = readRow(userId, kcId)
        val newEwma =
            if (existing == null) score
            else KcMastery.EWMA_ALPHA * score + (1 - KcMastery.EWMA_ALPHA) * existing.ewmaScore
        val newObs = (existing?.observations ?: 0) + 1
        val computedMastered = newEwma >= KcMastery.MASTERY_THRESHOLD && newObs >= KcMastery.MIN_OBSERVATIONS
        val newPhase = PhaseModel.transition(newEwma, newObs, computedMastered, current = existing?.phase)
        if (existing == null) {
            KcMasteryTable.insert {
                it[KcMasteryTable.userId] = userId
                it[KcMasteryTable.kcId] = kcId
                it[ewmaScore] = newEwma
                it[observations] = newObs
                it[lastGradedAt] = now
                it[phase] = newPhase.name
            }
        } else {
            KcMasteryTable.update({ (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kcId) }) {
                it[ewmaScore] = newEwma
                it[observations] = newObs
                it[lastGradedAt] = now
                it[phase] = newPhase.name
            }
        }
        return KcMastery(userId, kcId, newEwma, newObs, now, phase = newPhase)
    }

    /**
     * Thin wrapper (§G back-compat). Opens ONE transaction(db){ recordIn(this, …) }.
     * Existing callers of record() keep working unchanged.
     */
    fun record(userId: String, kcId: String, score: Double, now: Instant = Instant.now()): KcMastery =
        transaction(db) { recordIn(this, userId, kcId, score, now) }
}
