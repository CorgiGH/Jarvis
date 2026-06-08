package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Valid grader LLM provider identifiers.
 *
 * - "free"        → OpenRouterChatLlm (default; free-tier OpenRouter)
 * - "claude"      → ClaudeMaxLlm (local `claude` CLI subprocess)
 * - "freellmapi"  → FreeLlmApiLlm (self-hosted OpenAI-compatible proxy;
 *                   requires FREELLMAPI_BASE_URL to point at a running server)
 */
enum class GraderProvider { free, claude, freellmapi }

/**
 * Per-user grader-provider preference.
 *
 * Stored as a single row per user (upsert via delete+insert, mirroring
 * [UserPreferencesRepo]). Missing rows return the default "free" provider.
 *
 * Schema note: this table is intentionally separate from [UserPreferencesTable]
 * to keep the provider-selector orthogonal to the existing UX preferences and
 * to make the /me/delete cascade straightforward (user_id FK, same pattern).
 */
object GraderProviderSettingTable : Table("grader_provider_setting") {
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val provider = varchar("provider", 16).default(GraderProvider.free.name)
    override val primaryKey = PrimaryKey(userId)
}

class GraderProviderSettingRepo(private val db: Database) {
    /**
     * Return the grader provider for [userId]. Returns [GraderProvider.free] if
     * no row exists (so new users get the free default without an explicit row).
     */
    fun get(userId: String): GraderProvider = transaction(db) {
        GraderProviderSettingTable
            .selectAll()
            .where { GraderProviderSettingTable.userId eq userId }
            .singleOrNull()
            ?.let {
                runCatching {
                    GraderProvider.valueOf(it[GraderProviderSettingTable.provider])
                }.getOrDefault(GraderProvider.free)
            }
            ?: GraderProvider.free
    }

    /**
     * Persist [provider] for [userId] (upsert: delete then insert).
     * Idempotent — a second call with the same value is a no-op from the
     * caller's perspective.
     */
    fun set(userId: String, provider: GraderProvider) = transaction(db) {
        GraderProviderSettingTable.deleteWhere { GraderProviderSettingTable.userId eq userId }
        GraderProviderSettingTable.insert {
            it[GraderProviderSettingTable.userId] = userId
            it[GraderProviderSettingTable.provider] = provider.name
        }
    }
}
