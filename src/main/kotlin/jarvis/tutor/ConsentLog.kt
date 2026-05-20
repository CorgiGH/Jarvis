package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object ConsentLogTable : Table("consent_log") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 26)
    val consentType = varchar("consent_type", 32)
    val granted = bool("granted")
    val recordedAt = timestamp("recorded_at")
    override val primaryKey = PrimaryKey(id)
}

data class ConsentEvent(val consentType: String, val granted: Boolean, val recordedAt: Instant)

class ConsentRepo(private val db: Database) {
    fun record(userId: String, consentType: String, granted: Boolean) = transaction(db) {
        ConsentLogTable.insert {
            it[ConsentLogTable.userId] = userId
            it[ConsentLogTable.consentType] = consentType
            it[ConsentLogTable.granted] = granted
            it[recordedAt] = Instant.now()
        }
    }

    fun listForUser(userId: String): List<ConsentEvent> = transaction(db) {
        ConsentLogTable.selectAll()
            .where { ConsentLogTable.userId eq userId }
            .orderBy(ConsentLogTable.recordedAt to SortOrder.DESC, ConsentLogTable.id to SortOrder.DESC)
            .map { ConsentEvent(it[ConsentLogTable.consentType], it[ConsentLogTable.granted], it[ConsentLogTable.recordedAt]) }
    }
}
