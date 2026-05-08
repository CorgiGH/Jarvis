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
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

object TokensTable : Table("tokens") {
    val tokenHash = varchar("token_hash", 64)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val label = varchar("label", 64)
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()
    override val primaryKey = PrimaryKey(tokenHash)
}

class TokenRepo(private val db: Database) {
    private val rng = SecureRandom()

    fun issue(userId: String, label: String): String {
        val raw = ByteArray(32).also { rng.nextBytes(it) }.toHex()
        val hash = sha256Hex(raw)
        transaction(db) {
            TokensTable.insert {
                it[TokensTable.tokenHash] = hash
                it[TokensTable.userId] = userId
                it[TokensTable.label] = label
                it[TokensTable.createdAt] = Instant.now()
                it[TokensTable.revokedAt] = null
            }
        }
        return raw
    }

    fun findUserIdByToken(raw: String): String? = transaction(db) {
        val hash = sha256Hex(raw)
        TokensTable.selectAll().where {
            (TokensTable.tokenHash eq hash) and TokensTable.revokedAt.isNull()
        }.singleOrNull()?.get(TokensTable.userId)
    }

    fun revoke(raw: String) = transaction(db) {
        val hash = sha256Hex(raw)
        TokensTable.deleteWhere { TokensTable.tokenHash eq hash }
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
