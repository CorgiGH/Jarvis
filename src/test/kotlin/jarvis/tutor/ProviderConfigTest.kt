package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProviderConfigTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("pc-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, ProviderConfigTable) } }

    @Test
    fun `upsert + find returns latest config`() {
        val db = freshDb()
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = ProviderConfigRepo(db)
        repo.upsert(UserProviderConfig(u, ProviderType.CLAUDE_CLI_RELAY,
            relayEndpoint = "http://laptop.tail.ts.net:51234/chat",
            apiKeyEncryptedRef = null, fallback = listOf(ProviderType.COPILOT_CLI)))
        val cur = repo.find(u)
        assertNotNull(cur)
        assertEquals(ProviderType.CLAUDE_CLI_RELAY, cur.primary)
        repo.upsert(cur.copy(primary = ProviderType.ANTHROPIC_API,
            relayEndpoint = null, apiKeyEncryptedRef = "vault:abc"))
        assertEquals(ProviderType.ANTHROPIC_API, repo.find(u)!!.primary)
    }

    @Test
    fun `find returns null for absent user`() {
        val db = freshDb()
        assertNull(ProviderConfigRepo(db).find("missing"))
    }
}
