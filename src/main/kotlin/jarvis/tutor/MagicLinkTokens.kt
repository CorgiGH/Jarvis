package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

object MagicLinkTokensTable : Table("magic_link_tokens") {
    val tokenHash = varchar("token_hash", 64)
    val email = varchar("email", 320)
    val lang = varchar("lang", 8)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val consumedAt = timestamp("consumed_at").nullable()
    override val primaryKey = PrimaryKey(tokenHash)
}

data class MagicLinkClaim(val email: String, val lang: String)

class MagicLinkRepo(private val db: Database) {
    private val rng = SecureRandom()

    /** Generate a fresh raw token, store only its SHA-256 hash, return the raw. */
    fun issue(email: String, lang: String, ttlSeconds: Long): String {
        val raw = ByteArray(32).also { rng.nextBytes(it) }.toHex()
        val now = Instant.now()
        transaction(db) {
            MagicLinkTokensTable.insert {
                it[tokenHash] = sha256Hex(raw)
                it[MagicLinkTokensTable.email] = email
                it[MagicLinkTokensTable.lang] = lang
                it[createdAt] = now
                it[expiresAt] = now.plusSeconds(ttlSeconds)
                it[consumedAt] = null
            }
        }
        return raw
    }

    /**
     * Atomic single-use consume. The UPDATE both checks (unconsumed + unexpired)
     * and marks consumed in one statement; only a rows-affected count of 1 means
     * this caller won the token. A second call, or an expired token, affects 0 rows.
     */
    fun consume(raw: String): MagicLinkClaim? = transaction(db) {
        val hash = sha256Hex(raw)
        val now = Instant.now()
        val updated = MagicLinkTokensTable.update({
            (MagicLinkTokensTable.tokenHash eq hash) and
                MagicLinkTokensTable.consumedAt.isNull() and
                MagicLinkTokensTable.expiresAt.greater(now)
        }) {
            it[consumedAt] = now
        }
        if (updated != 1) return@transaction null
        MagicLinkTokensTable.selectAll()
            .where { MagicLinkTokensTable.tokenHash eq hash }
            .singleOrNull()
            ?.let { MagicLinkClaim(it[MagicLinkTokensTable.email], it[MagicLinkTokensTable.lang]) }
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
