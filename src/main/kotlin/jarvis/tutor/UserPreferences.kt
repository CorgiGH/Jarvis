package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object UserPreferencesTable : Table("user_preferences") {
    val userId = varchar("user_id", 26)
    val hintMode = varchar("hint_mode", 16).default("static")
    val loggingPausedUntil = timestamp("logging_paused_until").nullable()
    override val primaryKey = PrimaryKey(userId)
}

data class UserPreferences(val hintMode: String, val loggingPausedUntil: Instant?)

class UserPreferencesRepo(private val db: Database) {
    fun get(userId: String): UserPreferences = transaction(db) {
        UserPreferencesTable.selectAll()
            .where { UserPreferencesTable.userId eq userId }
            .singleOrNull()
            ?.let { UserPreferences(it[UserPreferencesTable.hintMode], it[UserPreferencesTable.loggingPausedUntil]) }
            ?: UserPreferences(hintMode = "static", loggingPausedUntil = null)
    }

    fun set(userId: String, hintMode: String, loggingPausedUntil: Instant?) = transaction(db) {
        UserPreferencesTable.deleteWhere { UserPreferencesTable.userId eq userId }
        UserPreferencesTable.insert {
            it[UserPreferencesTable.userId] = userId
            it[UserPreferencesTable.hintMode] = hintMode
            it[UserPreferencesTable.loggingPausedUntil] = loggingPausedUntil
        }
    }
}
