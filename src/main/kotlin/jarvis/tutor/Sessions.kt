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

    /** Mint a fresh session; store sha256(sid); return the raw sid for the cookie. */
    fun create(userId: String, ttlSeconds: Long): String {
        val rawSid = ByteArray(32).also { rng.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val now = Instant.now()
        transaction(db) {
            SessionsTable.insert {
                it[SessionsTable.sid] = sha256Hex(rawSid)
                it[SessionsTable.userId] = userId
                it[SessionsTable.createdAt] = now
                it[SessionsTable.expiresAt] = now.plusSeconds(ttlSeconds)
            }
        }
        return rawSid
    }

    fun findUserId(rawSid: String): String? = transaction(db) {
        val now = Instant.now()
        SessionsTable.selectAll().where {
            (SessionsTable.sid eq sha256Hex(rawSid)) and (SessionsTable.expiresAt.greater(now))
        }.singleOrNull()?.get(SessionsTable.userId)
    }

    fun revoke(rawSid: String) = transaction(db) {
        SessionsTable.deleteWhere { SessionsTable.sid eq sha256Hex(rawSid) }
    }

    private fun sha256Hex(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
