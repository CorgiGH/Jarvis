package jarvis.tutor

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

enum class GrantSource { UI, CHAT }

data class TrustGrant(
    val grantId: String,
    val userId: String,
    val scope: List<String>,
    val ops: Set<EffectorType>,
    val expiresAt: Instant,
    val maxCalls: Int,
    val callsUsed: Int,
    val grantedFrom: GrantSource,
    val revokedAt: Instant?,
    val createdAt: Instant,
)

object TrustGrantsTable : Table("trust_grants") {
    val grantId = varchar("grant_id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val scopeJson = text("scope_json")
    val opsJson = text("ops_json")
    val expiresAt = timestamp("expires_at")
    val maxCalls = integer("max_calls")
    val callsUsed = integer("calls_used")
    val grantedFrom = varchar("granted_from", 8)
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(grantId)
}

class TrustGrantRepo(private val db: Database) {
    fun insert(g: TrustGrant) = transaction(db) {
        TrustGrantsTable.insert {
            it[grantId] = g.grantId
            it[userId] = g.userId
            it[scopeJson] = TutorTypes.tutorJson.encodeToString(
                ListSerializer(String.serializer()), g.scope)
            it[opsJson] = TutorTypes.tutorJson.encodeToString(
                SetSerializer(serializer<EffectorType>()), g.ops)
            it[expiresAt] = g.expiresAt
            it[maxCalls] = g.maxCalls
            it[callsUsed] = g.callsUsed
            it[grantedFrom] = g.grantedFrom.name
            it[revokedAt] = g.revokedAt
            it[createdAt] = g.createdAt
        }
    }

    fun findActive(grantId: String, now: Instant): TrustGrant? = transaction(db) {
        TrustGrantsTable.selectAll().where {
            (TrustGrantsTable.grantId eq grantId) and
            (TrustGrantsTable.expiresAt greater now) and
            TrustGrantsTable.revokedAt.isNull()
        }.singleOrNull()?.toGrant()
    }

    fun revoke(grantId: String, now: Instant) = transaction(db) {
        TrustGrantsTable.update({ TrustGrantsTable.grantId eq grantId }) {
            it[revokedAt] = now
        }
    }

    fun tryConsume(grantId: String): Boolean = transaction(db) {
        val row = TrustGrantsTable.selectAll().where { TrustGrantsTable.grantId eq grantId }
            .singleOrNull() ?: return@transaction false
        val used = row[TrustGrantsTable.callsUsed]
        val max = row[TrustGrantsTable.maxCalls]
        if (used >= max) return@transaction false
        TrustGrantsTable.update({ TrustGrantsTable.grantId eq grantId }) {
            it[callsUsed] = used + 1
        }
        true
    }

    private fun ResultRow.toGrant() = TrustGrant(
        grantId = this[TrustGrantsTable.grantId],
        userId = this[TrustGrantsTable.userId],
        scope = TutorTypes.tutorJson.decodeFromString(
            ListSerializer(String.serializer()),
            this[TrustGrantsTable.scopeJson]),
        ops = TutorTypes.tutorJson.decodeFromString(
            SetSerializer(serializer<EffectorType>()),
            this[TrustGrantsTable.opsJson]),
        expiresAt = this[TrustGrantsTable.expiresAt],
        maxCalls = this[TrustGrantsTable.maxCalls],
        callsUsed = this[TrustGrantsTable.callsUsed],
        grantedFrom = GrantSource.valueOf(this[TrustGrantsTable.grantedFrom]),
        revokedAt = this[TrustGrantsTable.revokedAt],
        createdAt = this[TrustGrantsTable.createdAt],
    )
}
