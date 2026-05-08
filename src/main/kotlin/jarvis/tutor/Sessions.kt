package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.Instant

object SessionsTable : Table("sessions") {
    val sid = varchar("sid", 64)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(sid)
}

class SessionRepo(private val db: Database) {
    private val rng = SecureRandom()

    fun create(userId: String, ttlSeconds: Long): String {
        val sid = ByteArray(32).also { rng.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val now = Instant.now()
        transaction(db) {
            SessionsTable.insert {
                it[SessionsTable.sid] = sid
                it[SessionsTable.userId] = userId
                it[SessionsTable.createdAt] = now
                it[SessionsTable.expiresAt] = now.plusSeconds(ttlSeconds)
            }
        }
        return sid
    }

    fun findUserId(sid: String): String? = transaction(db) {
        val now = Instant.now()
        SessionsTable.selectAll().where {
            (SessionsTable.sid eq sid) and (SessionsTable.expiresAt.greater(now))
        }.singleOrNull()?.get(SessionsTable.userId)
    }

    fun revoke(sid: String) = transaction(db) {
        SessionsTable.deleteWhere { SessionsTable.sid eq sid }
    }
}
