package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

data class FsrsForecast(val dueNow: Int, val tomorrow: Int, val thisWeek: Int, val thisMonth: Int)

object FsrsDueQueue {
    fun due(db: Database, userId: String, asOf: Instant, limit: Int = 50): List<TutorCard> =
        FsrsCardRepo(db).findDueForUser(userId, asOf).take(limit)

    fun forecast(db: Database, userId: String, now: Instant = Instant.now()): FsrsForecast = transaction(db) {
        val tomorrow = now.plus(Duration.ofDays(1))
        val week = now.plus(Duration.ofDays(7))
        val month = now.plus(Duration.ofDays(30))
        val rows = FsrsCardsTable.selectAll()
            .where { (FsrsCardsTable.userId eq userId) and (FsrsCardsTable.dueAt lessEq month) }
            .orderBy(FsrsCardsTable.dueAt, SortOrder.ASC)
            .toList()
        FsrsForecast(
            dueNow = rows.count { it[FsrsCardsTable.dueAt] <= now },
            tomorrow = rows.count { it[FsrsCardsTable.dueAt] <= tomorrow },
            thisWeek = rows.count { it[FsrsCardsTable.dueAt] <= week },
            thisMonth = rows.size,
        )
    }
}
