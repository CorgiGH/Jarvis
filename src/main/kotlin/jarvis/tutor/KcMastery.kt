package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
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
    override val primaryKey = PrimaryKey(userId, kcId)
}

data class KcMastery(
    val userId: String,
    val kcId: String,
    val ewmaScore: Double,
    val observations: Int,
    val lastGradedAt: Instant,
) {
    /** Explicit mastery rule: sustained high EWMA over enough observations. */
    val mastered: Boolean get() = ewmaScore >= MASTERY_THRESHOLD && observations >= MIN_OBSERVATIONS

    companion object {
        const val EWMA_ALPHA = 0.4         // weight on the newest score
        const val MASTERY_THRESHOLD = 0.8  // EWMA needed to count as mastered
        const val MIN_OBSERVATIONS = 3     // minimum graded attempts before mastery can latch
    }
}

class KcMasteryRepo(private val db: Database) {

    fun get(userId: String, kcId: String): KcMastery? = transaction(db) {
        KcMasteryTable.selectAll()
            .where { (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kcId) }
            .map {
                KcMastery(
                    it[KcMasteryTable.userId], it[KcMasteryTable.kcId],
                    it[KcMasteryTable.ewmaScore], it[KcMasteryTable.observations],
                    it[KcMasteryTable.lastGradedAt],
                )
            }
            .singleOrNull()
    }

    /** Apply one graded score via the EWMA rule; upsert and return the updated mastery. */
    fun record(userId: String, kcId: String, score: Double, now: Instant = Instant.now()): KcMastery =
        transaction(db) {
            val existing = get(userId, kcId)
            val newEwma =
                if (existing == null) score
                else KcMastery.EWMA_ALPHA * score + (1 - KcMastery.EWMA_ALPHA) * existing.ewmaScore
            val newObs = (existing?.observations ?: 0) + 1
            if (existing == null) {
                KcMasteryTable.insert {
                    it[KcMasteryTable.userId] = userId
                    it[KcMasteryTable.kcId] = kcId
                    it[ewmaScore] = newEwma
                    it[observations] = newObs
                    it[lastGradedAt] = now
                }
            } else {
                KcMasteryTable.update({ (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kcId) }) {
                    it[ewmaScore] = newEwma
                    it[observations] = newObs
                    it[lastGradedAt] = now
                }
            }
            KcMastery(userId, kcId, newEwma, newObs, now)
        }
}
