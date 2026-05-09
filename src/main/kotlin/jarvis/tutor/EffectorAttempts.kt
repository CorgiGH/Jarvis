package jarvis.tutor

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.Instant

/**
 * Tutor Layer B1 — explicit effector state machine.
 *
 * Council R3 fix #1 (council-1778325410-layer-b.md): "synchronously
 * blocks effector return" was a one-line constraint that splits
 * across server↔daemon HTTP roundtrips + a shadow-git commit.
 * Without an EffectorAttempt row tracking the dispatch-to-completion
 * lifecycle + a watchdog scanning for orphaned pre_sealed entries,
 * "atomic" is aspirational.
 *
 * State diagram:
 *
 *   PRE_PENDING  (row inserted, before shadow-git pre-seal)
 *       │
 *       ▼  ShadowGit.preSeal returns ok
 *   PRE_SEALED   (snapshot taken, daemon dispatched)
 *       │
 *       ├──── daemon SUCCESS ──▶ FIRED (effector landed in editor)
 *       │                              │
 *       │                              ▼  ShadowGit.postSeal
 *       │                          POST_SEALED   ✓ terminal
 *       │
 *       ├──── daemon FAILURE ──▶ FAILED   ✓ terminal
 *       │
 *       └──── watchdog timeout ──▶ ROLLED_BACK  (ShadowGit.rollback
 *                                                applied; live file
 *                                                restored to pre-state)
 *                                  ✓ terminal
 *
 * Watchdog cadence: every 1s, scan PRE_SEALED rows older than 2s,
 * roll back via ShadowGit + flip status to ROLLED_BACK. The 2s window
 * is the council recommendation; tighter risks rolling back legit
 * slow daemon calls, looser risks user-visible orphan windows.
 */
enum class EffectorAttemptState {
    PRE_PENDING, PRE_SEALED, FIRED, POST_SEALED, ROLLED_BACK, FAILED;

    val terminal: Boolean
        get() = this == POST_SEALED || this == ROLLED_BACK || this == FAILED
}

@Serializable
data class EffectorAttempt(
    val id: String,
    val userId: String,
    val taskId: String?,
    val effectorType: String,
    val targetUri: String,
    val grantId: String,
    val nonce: String,
    val shadowSnapshotId: String?,
    val state: EffectorAttemptState,
    @Serializable(InstantIso8601Serializer::class) val createdAt: Instant,
    @Serializable(InstantIso8601Serializer::class) val updatedAt: Instant,
    val failureReason: String?,
)

object EffectorAttemptsTable : Table("effector_attempts") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val taskId = varchar("task_id", 26).nullable()
    val effectorType = varchar("effector_type", 32)
    val targetUri = varchar("target_uri", 1024)
    val grantId = varchar("grant_id", 26)
    val nonce = varchar("nonce", 64)
    val shadowSnapshotId = varchar("shadow_snapshot_id", 32).nullable()
    val state = varchar("state", 16)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val failureReason = varchar("failure_reason", 512).nullable()
    override val primaryKey = PrimaryKey(id)
    init {
        index(false, userId, state)
        index(false, state, updatedAt)
    }
}

class EffectorAttemptRepo(private val db: Database) {

    fun insertPrePending(
        id: String, userId: String, taskId: String?, effectorType: EffectorType,
        targetUri: String, grantId: String, nonce: String,
    ): EffectorAttempt = transaction(db) {
        val now = Instant.now()
        EffectorAttemptsTable.insert {
            it[EffectorAttemptsTable.id] = id
            it[EffectorAttemptsTable.userId] = userId
            it[EffectorAttemptsTable.taskId] = taskId
            it[EffectorAttemptsTable.effectorType] = effectorType.name
            it[EffectorAttemptsTable.targetUri] = targetUri
            it[EffectorAttemptsTable.grantId] = grantId
            it[EffectorAttemptsTable.nonce] = nonce
            it[shadowSnapshotId] = null
            it[state] = EffectorAttemptState.PRE_PENDING.name
            it[createdAt] = now
            it[updatedAt] = now
            it[failureReason] = null
        }
        EffectorAttempt(id, userId, taskId, effectorType.name, targetUri, grantId, nonce,
            null, EffectorAttemptState.PRE_PENDING, now, now, null)
    }

    fun transition(id: String, to: EffectorAttemptState, snapshotId: String? = null,
                   failureReason: String? = null) = transaction(db) {
        EffectorAttemptsTable.update({ EffectorAttemptsTable.id eq id }) {
            it[state] = to.name
            it[updatedAt] = Instant.now()
            if (snapshotId != null) it[shadowSnapshotId] = snapshotId
            if (failureReason != null) it[EffectorAttemptsTable.failureReason] = failureReason
        }
    }

    fun get(id: String): EffectorAttempt? = transaction(db) {
        EffectorAttemptsTable.selectAll()
            .where { EffectorAttemptsTable.id eq id }
            .singleOrNull()
            ?.toAttempt()
    }

    /** Watchdog query: PRE_SEALED rows older than [staleAfter]. */
    fun listStalePreSealed(
        staleAfter: Duration, now: Instant = Instant.now(),
    ): List<EffectorAttempt> = transaction(db) {
        val cutoff = now.minus(staleAfter)
        EffectorAttemptsTable.selectAll()
            .where {
                (EffectorAttemptsTable.state eq EffectorAttemptState.PRE_SEALED.name) and
                    (EffectorAttemptsTable.updatedAt lessEq cutoff)
            }
            .orderBy(EffectorAttemptsTable.updatedAt, SortOrder.ASC)
            .map { it.toAttempt() }
    }

    /** Diagnostic — count attempts per state for the user. */
    fun countByState(userId: String): Map<EffectorAttemptState, Int> = transaction(db) {
        val out = mutableMapOf<EffectorAttemptState, Int>()
        EffectorAttemptsTable.selectAll()
            .where { EffectorAttemptsTable.userId eq userId }
            .forEach { row ->
                val st = EffectorAttemptState.valueOf(row[EffectorAttemptsTable.state])
                out[st] = (out[st] ?: 0) + 1
            }
        out
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toAttempt() = EffectorAttempt(
        id = this[EffectorAttemptsTable.id],
        userId = this[EffectorAttemptsTable.userId],
        taskId = this[EffectorAttemptsTable.taskId],
        effectorType = this[EffectorAttemptsTable.effectorType],
        targetUri = this[EffectorAttemptsTable.targetUri],
        grantId = this[EffectorAttemptsTable.grantId],
        nonce = this[EffectorAttemptsTable.nonce],
        shadowSnapshotId = this[EffectorAttemptsTable.shadowSnapshotId],
        state = EffectorAttemptState.valueOf(this[EffectorAttemptsTable.state]),
        createdAt = this[EffectorAttemptsTable.createdAt],
        updatedAt = this[EffectorAttemptsTable.updatedAt],
        failureReason = this[EffectorAttemptsTable.failureReason],
    )
}
