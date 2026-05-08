package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

data class AuditLine(
    val seq: Long,
    val userId: String,
    val ts: Instant,
    val canonical: String,
    val outcome: Outcome,
    val prevHash: String,
    val thisHash: String,
)

object AuditLinesTable : Table("audit_lines") {
    val seq = long("seq")
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val ts = timestamp("ts")
    val canonical = text("canonical")
    val outcome = varchar("outcome", 16)
    val prevHash = varchar("prev_hash", 64)
    val thisHash = varchar("this_hash", 64)
    override val primaryKey = PrimaryKey(userId, seq)
}

class AuditRepo(private val db: Database) {
    fun append(userId: String, canonicalLine: String, outcome: Outcome): AuditLine = transaction(db) {
        val lastSeq = AuditLinesTable
            .selectAll()
            .where { AuditLinesTable.userId eq userId }
            .maxByOrNull { it[AuditLinesTable.seq] }
            ?.let { it[AuditLinesTable.seq] } ?: 0L
        val prev = if (lastSeq == 0L) "0".repeat(64) else
            AuditLinesTable.selectAll()
                .where { (AuditLinesTable.userId eq userId) and (AuditLinesTable.seq eq lastSeq) }
                .single()[AuditLinesTable.thisHash]
        val nextSeq = lastSeq + 1
        val now = Instant.now()
        val hash = HashChain.nextHash(prev, canonicalLine)
        AuditLinesTable.insert {
            it[seq] = nextSeq
            it[AuditLinesTable.userId] = userId
            it[ts] = now
            it[canonical] = canonicalLine
            it[AuditLinesTable.outcome] = outcome.name
            it[prevHash] = prev
            it[thisHash] = hash
        }
        AuditLine(nextSeq, userId, now, canonicalLine, outcome, prev, hash)
    }

    fun verifyChain(userId: String): Boolean = transaction(db) {
        val rows = AuditLinesTable.selectAll()
            .where { AuditLinesTable.userId eq userId }
            .orderBy(AuditLinesTable.seq)
            .toList()
        val links = rows.map {
            HashChain.Link(it[AuditLinesTable.prevHash], it[AuditLinesTable.canonical], it[AuditLinesTable.thisHash])
        }
        HashChain.verify(links)
    }
}
