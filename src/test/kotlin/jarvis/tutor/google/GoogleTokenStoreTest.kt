package jarvis.tutor.google

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleTokenStoreTest {

    @Test
    fun `load returns null when token file does not exist`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        assertNull(store.load())
    }

    @Test
    fun `save and load round-trip preserves all fields`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val expiresAt = Instant.parse("2026-06-01T12:00:00Z")
        val token = OAuth2Token(
            accessToken = "ya29.access",
            refreshToken = "1//refresh",
            expiresAt = expiresAt,
        )
        store.save(token)
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals("ya29.access", loaded.accessToken)
        assertEquals("1//refresh", loaded.refreshToken)
        assertEquals(expiresAt, loaded.expiresAtInstant())
    }

    @Test
    fun `isExpired returns true when expiresAt is in the past`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val past = Instant.now().minusSeconds(300)
        val token = OAuth2Token("a", "r", past)
        assertTrue(store.isExpired(token, now = Instant.now()))
    }

    @Test
    fun `isExpired returns false when expiresAt is well in the future`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val future = Instant.now().plusSeconds(3600)
        val token = OAuth2Token("a", "r", future)
        assertFalse(store.isExpired(token, now = Instant.now()))
    }

    @Test
    fun `isExpired applies 60s safety buffer — token expiring in 30s is considered expired`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val almostExpired = Instant.now().plusSeconds(30)
        val token = OAuth2Token("a", "r", almostExpired)
        assertTrue(store.isExpired(token, now = Instant.now()))
    }

    @Test
    fun `save overwrites existing file`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        store.save(OAuth2Token("old", "r", Instant.now().plusSeconds(3600)))
        store.save(OAuth2Token("new", "r", Instant.now().plusSeconds(7200)))
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals("new", loaded.accessToken)
    }
}
