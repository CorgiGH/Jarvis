package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [GoogleAuthBootstrap] — the one-time OAuth 2.0 consent flow.
 *
 * The full flow requires a browser. Tests cover:
 *   - OAuth URL construction (scopes, redirect_uri, response_type)
 *   - Code exchange: canned token server → OAuth2Token produced correctly
 *   - Token written to output (stdout capture via parameter injection)
 */
class GoogleAuthBootstrapTest {

    private val testCreds = ClientCreds("test-client-id", "test-client-secret")
    private val redirectUri = "http://localhost:9999/callback"

    @Test
    fun `buildConsentUrl includes all three required scopes`() {
        val url = GoogleAuthBootstrap.buildConsentUrl(
            creds = testCreds,
            redirectUri = redirectUri,
            state = "state-xyz",
        )
        assertTrue(url.contains("calendar.events"),
            "URL must contain calendar.events scope")
        assertTrue(url.contains("drive.readonly"),
            "URL must contain drive.readonly scope")
        assertTrue(url.contains("gmail.compose"),
            "URL must contain gmail.compose scope")
    }

    @Test
    fun `buildConsentUrl contains client_id and redirect_uri`() {
        val url = GoogleAuthBootstrap.buildConsentUrl(testCreds, redirectUri, "s")
        assertTrue(url.contains("test-client-id"))
        assertTrue(url.contains("localhost"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("access_type=offline"))
    }

    @Test
    fun `exchangeCode posts to token endpoint and returns OAuth2Token`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/token") { ex ->
            val resp = """{
                "access_token":"ya29.test-access",
                "refresh_token":"1//test-refresh",
                "expires_in":3600,
                "token_type":"Bearer"
            }""".trimIndent().toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val token = GoogleAuthBootstrap.exchangeCode(
                code = "auth-code-from-google",
                creds = testCreds,
                redirectUri = redirectUri,
                tokenEndpoint = "http://localhost:$port/token",
            )
            assertNotNull(token)
            assertEquals("ya29.test-access", token.accessToken)
            assertEquals("1//test-refresh", token.refreshToken)
            assertTrue(token.expiresAtInstant().isAfter(Instant.now()))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `exchangeCode throws on non-200 response`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/token") { ex ->
            val resp = """{"error":"invalid_grant"}""".toByteArray()
            ex.sendResponseHeaders(400, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            var threw = false
            try {
                GoogleAuthBootstrap.exchangeCode(
                    "bad-code", testCreds, redirectUri,
                    "http://localhost:$port/token"
                )
            } catch (_: Exception) { threw = true }
            assertTrue(threw, "exchangeCode must throw on 400")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `serializeToken produces valid JSON with accessToken and refreshToken`() {
        val token = OAuth2Token("acc", "ref", Instant.now().plusSeconds(3600))
        val json = GoogleAuthBootstrap.serializeToken(token)
        assertTrue(json.contains("accessToken") || json.contains("access_token"),
            "serialized token must have an access token field")
        assertTrue(json.contains("acc"))
        assertTrue(json.contains("ref"))
    }
}
