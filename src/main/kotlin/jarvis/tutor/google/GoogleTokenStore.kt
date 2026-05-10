package jarvis.tutor.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Persistent OAuth 2.0 token store backed by a JSON file at [path].
 *
 * Thread safety: callers should synchronize externally if concurrent
 * refresh is possible. In practice, tool dispatch in JarvisToolDefs is
 * serialized per LLM round-trip, so this is safe for the tutor surface.
 *
 * [isExpired] applies a 60-second safety buffer so the access token is
 * refreshed before Google actually rejects it.
 */
@Serializable
data class OAuth2Token(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String, // ISO-8601, serialized as String for Kotlin 1.9 compat
) {
    fun expiresAtInstant(): Instant = Instant.parse(expiresAt)
}

/** Convenience constructor that accepts a java.time.Instant. */
fun OAuth2Token(accessToken: String, refreshToken: String, expiresAt: Instant) =
    OAuth2Token(accessToken, refreshToken, expiresAt.toString())

data class ClientCreds(val clientId: String, val clientSecret: String)

class TokenStore(private val path: Path) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Returns the stored token, or null if the file is absent or unreadable. */
    fun load(): OAuth2Token? = try {
        json.decodeFromString<OAuth2Token>(path.readText())
    } catch (_: Exception) { null }

    /** Serialises [token] to disk, creating parent directories as needed. */
    fun save(token: OAuth2Token) {
        path.parent?.toFile()?.mkdirs()
        path.writeText(json.encodeToString(token))
    }

    /** Returns true if the token expires within 60 seconds of [now]. */
    fun isExpired(token: OAuth2Token, now: Instant = Instant.now()): Boolean =
        token.expiresAtInstant().minusSeconds(60) <= now
}
