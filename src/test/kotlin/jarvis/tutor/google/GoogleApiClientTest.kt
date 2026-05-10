package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleApiClientTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        serverPort = server.address.port
        server.start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    private fun baseUrl() = "http://localhost:$serverPort"

    private fun cannedToken(expiresInSeconds: Long = 3600): OAuth2Token =
        OAuth2Token("valid-access-token", "refresh-token-abc",
            Instant.now().plusSeconds(expiresInSeconds))

    private fun cannedCreds() = ClientCreds("client-id-test", "client-secret-test")

    @Test
    fun `httpGet returns body from canned server`() {
        server.createContext("/test-get") { ex ->
            val resp = """{"ok":true}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(store, cannedCreds(), baseApiUrl = baseUrl())
        val result = client.httpGetRaw("/test-get")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("\"ok\""))
    }

    @Test
    fun `httpGet refreshes token on 401 and retries`() {
        var callCount = 0
        // Token refresh endpoint
        server.createContext("/token") { ex ->
            val resp = """{"access_token":"new-access","expires_in":3600,"token_type":"Bearer"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.createContext("/protected") { ex ->
            callCount++
            if (callCount == 1) {
                ex.sendResponseHeaders(401, -1)
                ex.responseBody.close()
            } else {
                val resp = """{"data":"secret"}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
        }
        // Expired token forces a refresh before the first real call
        val expiredToken = OAuth2Token("expired-access", "refresh-token-abc",
            Instant.now().minusSeconds(600))
        val store = InMemoryTokenStore(expiredToken)
        val client = GoogleApiClient(
            store, cannedCreds(),
            baseApiUrl = baseUrl(),
            tokenEndpoint = "${baseUrl()}/token"
        )
        val result = client.httpGetRaw("/protected")
        assertTrue(result.isSuccess)
        assertEquals("""{"data":"secret"}""", result.getOrThrow())
    }

    @Test
    fun `httpGet backs off on 429 and eventually succeeds`() {
        var calls = 0
        server.createContext("/rate-limited") { ex ->
            calls++
            if (calls < 3) {
                ex.sendResponseHeaders(429, -1)
                ex.responseBody.close()
            } else {
                val resp = """{"ok":true}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(
            store, cannedCreds(),
            baseApiUrl = baseUrl(),
            backoffMs = longArrayOf(10, 40, 160), // fast for test
        )
        val result = client.httpGetRaw("/rate-limited")
        assertTrue(result.isSuccess)
        assertEquals(3, calls)
    }

    @Test
    fun `httpGet fails after 3 consecutive 429 responses`() {
        server.createContext("/always-429") { ex ->
            ex.sendResponseHeaders(429, -1)
            ex.responseBody.close()
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(
            store, cannedCreds(),
            baseApiUrl = baseUrl(),
            backoffMs = longArrayOf(10, 40, 160),
        )
        val result = client.httpGetRaw("/always-429")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("429") == true)
    }

    @Test
    fun `httpPost sends JSON body and returns response`() {
        server.createContext("/post-endpoint") { ex ->
            val body = ex.requestBody.bufferedReader().readText()
            val parsed = Json.parseToJsonElement(body).jsonObject
            val echo = """{"received":${parsed["key"]}}""".toByteArray()
            ex.sendResponseHeaders(200, echo.size.toLong())
            ex.responseBody.use { it.write(echo) }
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(store, cannedCreds(), baseApiUrl = baseUrl())
        val result = client.httpPostRaw("/post-endpoint", """{"key":"hello"}""")
        assertTrue(result.isSuccess)
        val resp = Json.parseToJsonElement(result.getOrThrow()).jsonObject
        assertEquals("hello", resp["received"]?.jsonPrimitive?.content)
    }
}

/** Test-only in-memory TokenStore that never touches disk. */
class InMemoryTokenStore(private var token: OAuth2Token) : AbstractTokenStore() {
    override fun load(): OAuth2Token? = token
    override fun save(t: OAuth2Token) { token = t }
}
