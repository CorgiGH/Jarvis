package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class GmailClientTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach fun stop() = server.stop(0)

    private fun makeClient(): GmailClient {
        val store = InMemoryTokenStore(
            OAuth2Token("test-access", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(
            store, ClientCreds("cid", "csec"),
            baseApiUrl = "http://localhost:$port",
        )
        return GmailClient(api)
    }

    @Test
    fun `draftsCreate returns DraftId on 200 response`() {
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            val resp = """{"id":"draft-xyz-789","message":{"id":"msg-1","threadId":"t-1"}}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().draftsCreate(
            to = "professor@example.ro",
            subject = "Question about PS homework",
            body = "Dear professor, I have a question about Problem A3...",
        )
        assertTrue(result.isSuccess)
        assertEquals("draft-xyz-789", result.getOrThrow())
    }

    @Test
    fun `draftsCreate sends base64url-encoded RFC822 message in request body`() {
        var capturedBody = ""
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            capturedBody = ex.requestBody.bufferedReader().readText()
            val resp = """{"id":"draft-body-check"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().draftsCreate("to@example.com", "Subject line", "Body text")

        // Request must be JSON with message.raw field (base64url)
        val parsed = Json.parseToJsonElement(capturedBody).jsonObject
        val messageObj = parsed["message"]?.jsonObject
        val raw = messageObj?.get("raw")?.jsonPrimitive?.content
        assertTrue(raw != null && raw.isNotBlank(),
            "message.raw must be present and non-empty")

        // base64url decoded content must contain RFC822 headers
        val decoded = String(java.util.Base64.getUrlDecoder().decode(raw!!), Charsets.UTF_8)
        assertTrue(decoded.contains("To: to@example.com"))
        assertTrue(decoded.contains("Subject: Subject line"))
        assertTrue(decoded.contains("Body text"))
    }

    @Test
    fun `draftsCreate encodes special characters in subject safely`() {
        var capturedBody = ""
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            capturedBody = ex.requestBody.bufferedReader().readText()
            val resp = """{"id":"draft-special"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().draftsCreate("x@x.com", "Topic: μ̂ MLE", "Has math: Σ|xᵢ − μ|")
        val parsed = Json.parseToJsonElement(capturedBody).jsonObject
        val raw = parsed["message"]?.jsonObject?.get("raw")?.jsonPrimitive?.content ?: ""
        val decoded = String(java.util.Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        assertTrue(decoded.contains("Topic: μ̂ MLE"))
    }

    @Test
    fun `draftsCreate returns failure on API error`() {
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            val resp = """{"error":{"code":400,"message":"Bad Request"}}""".toByteArray()
            ex.sendResponseHeaders(400, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().draftsCreate("a@b.com", "s", "b")
        assertTrue(result.isFailure)
    }
}
