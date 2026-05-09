package jarvis.tutor

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

enum class ProviderType { CLAUDE_CLI_RELAY, ANTHROPIC_API, OPENAI_API, GEMINI_API, COPILOT_CLI }

data class UserProviderConfig(
    val userId: String,
    val primary: ProviderType,
    val relayEndpoint: String?,
    val apiKeyEncryptedRef: String?,
    val fallback: List<ProviderType>,
)

object ProviderConfigTable : Table("user_provider_config") {
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val primaryProvider = varchar("primary_provider", 24)
    val relayEndpoint = varchar("relay_endpoint", 256).nullable()
    val apiKeyEncryptedRef = varchar("api_key_encrypted_ref", 256).nullable()
    val fallbackJson = text("fallback_json")
    override val primaryKey = PrimaryKey(userId)
}

class ProviderConfigRepo(private val db: Database) {
    fun upsert(c: UserProviderConfig) = transaction(db) {
        ProviderConfigTable.deleteWhere { ProviderConfigTable.userId eq c.userId }
        ProviderConfigTable.insert {
            it[userId] = c.userId
            it[primaryProvider] = c.primary.name
            it[relayEndpoint] = c.relayEndpoint
            it[apiKeyEncryptedRef] = c.apiKeyEncryptedRef
            it[fallbackJson] = TutorTypes.tutorJson.encodeToString(
                ListSerializer(serializer<ProviderType>()), c.fallback)
        }
    }

    fun find(userId: String): UserProviderConfig? = transaction(db) {
        ProviderConfigTable.selectAll()
            .where { ProviderConfigTable.userId eq userId }
            .singleOrNull()?.toConfig()
    }

    private fun ResultRow.toConfig() = UserProviderConfig(
        userId = this[ProviderConfigTable.userId],
        primary = ProviderType.valueOf(this[ProviderConfigTable.primaryProvider]),
        relayEndpoint = this[ProviderConfigTable.relayEndpoint],
        apiKeyEncryptedRef = this[ProviderConfigTable.apiKeyEncryptedRef],
        fallback = TutorTypes.tutorJson.decodeFromString(
            ListSerializer(serializer<ProviderType>()),
            this[ProviderConfigTable.fallbackJson]),
    )
}
