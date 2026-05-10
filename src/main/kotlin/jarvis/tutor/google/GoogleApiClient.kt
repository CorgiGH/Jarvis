package jarvis.tutor.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * Abstract base so tests can inject an in-memory store without hitting disk.
 */
abstract class AbstractTokenStore {
    abstract fun load(): OAuth2Token?
    abstract fun save(t: OAuth2Token)
    fun isExpired(token: OAuth2Token, now: Instant = Instant.now()): Boolean =
        token.expiresAtInstant().minusSeconds(60) <= now
}

/**
 * Disk-backed [AbstractTokenStore] using [TokenStore].
 */
class DiskTokenStore(path: java.nio.file.Path) : AbstractTokenStore() {
    private val inner = TokenStore(path)
    override fun load(): OAuth2Token? = inner.load()
    override fun save(t: OAuth2Token) = inner.save(t)
}

/**
 * Core Google REST client.
 *
 * - Uses [java.net.http.HttpClient] (JDK 11+, always on classpath).
 * - On 401: refreshes access token via the stored refresh_token, updates
 *   [store], then retries the original request exactly once.
 * - On 429: exponential back-off using [backoffMs] (default 1000/4000/16000ms,
 *   3 tries max). After all retries exhausted, returns failure.
 * - All requests attach `Authorization: Bearer <accessToken>` header.
 *
 * [baseApiUrl] and [tokenEndpoint] are overridable for tests.
 */
class GoogleApiClient(
    private val store: AbstractTokenStore,
    private val creds: ClientCreds,
    private val baseApiUrl: String = "https://www.googleapis.com",
    private val tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    private val backoffMs: LongArray = longArrayOf(1_000, 4_000, 16_000),
) {
    private val http = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Ensure the stored token is valid, refreshing if expired. Returns current token. */
    fun ensureValidToken(): OAuth2Token {
        val current = store.load()
            ?: error("GoogleApi: no token found — run `jarvis google-auth-bootstrap` on your PC and scp the token to VPS")
        if (!store.isExpired(current)) return current
        return refreshToken(current)
    }

    private fun refreshToken(expired: OAuth2Token): OAuth2Token {
        val form = "client_id=${urlEncode(creds.clientId)}" +
            "&client_secret=${urlEncode(creds.clientSecret)}" +
            "&refresh_token=${urlEncode(expired.refreshToken)}" +
            "&grant_type=refresh_token"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            error("GoogleApi: token refresh failed (HTTP ${resp.statusCode()}): ${resp.body().take(400)}")
        }
        val body = json.parseToJsonElement(resp.body()).jsonObject
        val newAccess = body["access_token"]?.jsonPrimitive?.content
            ?: error("GoogleApi: refresh response missing access_token")
        val expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        val newToken = OAuth2Token(
            accessToken = newAccess,
            refreshToken = expired.refreshToken, // refresh_token not rotated by Google in most flows
            expiresAt = Instant.now().plusSeconds(expiresIn),
        )
        store.save(newToken)
        return newToken
    }

    /** GET [path] (relative to [baseApiUrl]) with auth + retry. */
    fun httpGetRaw(path: String): Result<String> = withRetry {
        val token = ensureValidToken()
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$baseApiUrl$path"))
            .header("Authorization", "Bearer ${token.accessToken}")
            .GET()
            .build()
        http.send(req, HttpResponse.BodyHandlers.ofString())
    }

    /** POST [path] with JSON [body], with auth + retry. */
    fun httpPostRaw(path: String, body: String): Result<String> = withRetry {
        val token = ensureValidToken()
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$baseApiUrl$path"))
            .header("Authorization", "Bearer ${token.accessToken}")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        http.send(req, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * Execute [block] with 401-once-refresh + 429 exponential back-off.
     * 401 retry: refresh token, then call [block] again exactly once.
     * 429 retry: up to [backoffMs].size retries with inter-attempt sleep.
     */
    private fun withRetry(block: () -> HttpResponse<String>): Result<String> {
        var resp = try { block() } catch (e: Exception) { return Result.failure(e) }

        // Handle 401: refresh and retry once
        if (resp.statusCode() == 401) {
            val current = store.load() ?: return Result.failure(IllegalStateException("no token"))
            try { refreshToken(current) } catch (e: Exception) {
                return Result.failure(IllegalStateException(
                    "GoogleApi: consent expired — re-run google-auth-bootstrap. Detail: ${e.message}", e))
            }
            resp = try { block() } catch (e: Exception) { return Result.failure(e) }
        }

        // Handle 429 with backoff
        var attempt = 0
        while (resp.statusCode() == 429 && attempt < backoffMs.size) {
            Thread.sleep(backoffMs[attempt++])
            resp = try { block() } catch (e: Exception) { return Result.failure(e) }
        }

        if (resp.statusCode() == 429) {
            return Result.failure(RuntimeException(
                "GoogleApi: 429 rate-limited after ${backoffMs.size} retries — upstream error: ${resp.body().take(200)}"))
        }

        return if (resp.statusCode() in 200..299) {
            Result.success(resp.body())
        } else {
            Result.failure(RuntimeException(
                "GoogleApi: HTTP ${resp.statusCode()}: ${resp.body().take(400)}"))
        }
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
