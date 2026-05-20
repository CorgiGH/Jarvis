package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

const val AI_LITERACY_VERSION = "tutor-v1.0"

object AiLiteracyConfirmationTable : Table("ai_literacy_confirmation") {
    val userId = varchar("user_id", 26)
    val version = varchar("version", 32)
    val lang = varchar("lang", 8)
    val confirmedAt = timestamp("confirmed_at")
    override val primaryKey = PrimaryKey(userId, version)
}

class AiLiteracyRepo(private val db: Database) {
    fun confirm(userId: String, version: String, lang: String) = transaction(db) {
        AiLiteracyConfirmationTable.insertIgnore {
            it[AiLiteracyConfirmationTable.userId] = userId
            it[AiLiteracyConfirmationTable.version] = version
            it[AiLiteracyConfirmationTable.lang] = lang
            it[confirmedAt] = Instant.now()
        }
    }

    fun hasConfirmedCurrent(userId: String): Boolean = transaction(db) {
        AiLiteracyConfirmationTable.selectAll().where {
            (AiLiteracyConfirmationTable.userId eq userId) and
                (AiLiteracyConfirmationTable.version eq AI_LITERACY_VERSION)
        }.any()
    }
}
