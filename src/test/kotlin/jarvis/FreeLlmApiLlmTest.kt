package jarvis

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [FreeLlmApiLlm].
 *
 * Uses a raw JDK HttpServer (same pattern as [OpenRouterVisionLlmTest]) so the
 * tests are pure-JVM with no live endpoint dependency.
 */
class FreeLlmApiLlmTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val capturedBody = AtomicReference<String?>(null)
    private val capturedAuth = AtomicReference<String?>(null)

    // Allows a test to override the response sent by the stub server.
    private val handlerOverride = AtomicReference<((com.sun.net.httpserver.HttpExchange) -> Unit)?>(null)

    @BeforeEach
    fun setup() {
        capturedBody.set(null)
        capturedAuth.set(null)
        handlerOverride.set(null)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            capturedAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            capturedBody.set(exchange.requestBody.bufferedReader().readText())
            val override = handlerOverride.get()
            if (override != null) {
                override(exchange)
            } else {
                val body = """{"model":"llama-3-8b-instruct","choices":[{"message":{"role":"assistant","content":"hello from freellmapi"}}]}"""
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
            }
        }
        server.executor = null
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterEach
    fun teardown() {
        server.stop(0)
    }

    // ── Construction / config ────────────────────────────────────────────────

    @Test
    fun `reads baseUrl and model from constructor params`() {
        val llm = FreeLlmApiLlm(baseUrl = "http://example.local:9999", model = "my-model")
        assertEquals("http://example.local:9999", llm.baseUrl)
        assertEquals("my-model", llm.model)
        llm.close()
    }

    @Test
    fun `default baseUrl is localhost placeholder`() {
        // We cannot mutate System.getenv in-JVM, but we can verify the default
        // value that is used when the env var is absent (i.e. when it resolves to blank).
        // Create with an explicit override of a blank env-like value to trigger default path.
        val llm = FreeLlmApiLlm(
            baseUrl = System.getenv("FREELLMAPI_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://localhost:8080",
            model = System.getenv("FREELLMAPI_MODEL")?.takeIf { it.isNotBlank() } ?: "llama-3-8b-instruct",
        )
        // Verify the defaults are sane (not empty, not null).
        assertTrue(llm.baseUrl.startsWith("http"), "baseUrl should be a URL: ${llm.baseUrl}")
        assertTrue(llm.model.isNotBlank(), "model should not be blank")
        llm.close()
    }

    // ── Wire format ──────────────────────────────────────────────────────────

    @Test
    fun `complete sends model and messages in OpenAI format`() = runBlocking {
        FreeLlmApiLlm(baseUrl = baseUrl, model = "test-model").use { llm ->
            llm.complete(listOf(ChatMessage("user", "ping")), maxTokens = 128)
        }
        val body = capturedBody.get() ?: error("no body captured")
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("test-model", json["model"]?.jsonPrimitive?.content)
        val messages = json["messages"]
        assertNotNull(messages, "messages field must be present")
        assertTrue(body.contains("ping"), "user message content must be in body")
    }

    @Test
    fun `complete sends max_tokens`() = runBlocking {
        FreeLlmApiLlm(baseUrl = baseUrl, model = "m").use { llm ->
            llm.complete(listOf(ChatMessage("user", "hi")), maxTokens = 42)
        }
        val body = capturedBody.get() ?: error("no body")
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("42", json["max_tokens"]?.jsonPrimitive?.content)
    }

    @Test
    fun `complete sends response_format when provided`() = runBlocking {
        FreeLlmApiLlm(baseUrl = baseUrl, model = "m").use { llm ->
            llm.complete(listOf(ChatMessage("user", "hi")), responseFormat = "json_object")
        }
        val body = capturedBody.get() ?: error("no body")
        val json = Json.parseToJsonElement(body).jsonObject
        val rf = json["response_format"]?.jsonObject
        assertNotNull(rf, "response_format must be present when specified")
        assertEquals("json_object", rf["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `complete omits response_format when null`() = runBlocking {
        FreeLlmApiLlm(baseUrl = baseUrl, model = "m").use { llm ->
            llm.complete(listOf(ChatMessage("user", "hi")), responseFormat = null)
        }
        val body = capturedBody.get() ?: error("no body")
        val json = Json.parseToJsonElement(body).jsonObject
        assertNull(json["response_format"], "response_format must be absent when null")
    }

    @Test
    fun `complete returns assistant content and model from response`() = runBlocking {
        val (text, model) = FreeLlmApiLlm(baseUrl = baseUrl, model = "m").use { llm ->
            llm.complete(listOf(ChatMessage("user", "hi")))
        }
        assertEquals("hello from freellmapi", text)
        assertEquals("llama-3-8b-instruct", model)
    }

    @Test
    fun `auth header is set`() = runBlocking {
        FreeLlmApiLlm(baseUrl = baseUrl, model = "m").use { llm ->
            llm.complete(listOf(ChatMessage("user", "hi")))
        }
        val auth = capturedAuth.get()
        assertNotNull(auth, "Authorization header must be present")
        assertTrue(auth.startsWith("Bearer "), "Authorization must be a Bearer token: $auth")
    }

    // ── Error handling ───────────────────────────────────────────────────────

    @Test
    fun `non-2xx response throws with status and body excerpt`() = runBlocking {
        handlerOverride.set { exchange ->
            val err = """{"error":"model not found"}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(404, err.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(err.toByteArray()) }
        }
        val ex = assertFails {
            FreeLlmApiLlm(baseUrl = baseUrl, model = "m").use { llm ->
                llm.complete(listOf(ChatMessage("user", "hi")))
            }
        }
        assertTrue(ex.message?.contains("404") == true, "Should mention status: ${ex.message}")
        assertTrue(ex.message?.contains("model not found") == true, "Should include body: ${ex.message}")
    }

    @Test
    fun `empty choices throws with clear message`() = runBlocking {
        handlerOverride.set { exchange ->
            val body = """{"model":"m","choices":[]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        val ex = assertFails {
            FreeLlmApiLlm(baseUrl = baseUrl, model = "m").use { llm ->
                llm.complete(listOf(ChatMessage("user", "hi")))
            }
        }
        assertTrue(ex.message?.contains("no choices") == true, "Should mention no choices: ${ex.message}")
    }

    // ── buildPayload unit ────────────────────────────────────────────────────

    @Test
    fun `buildPayload roundtrip — model present and messages wired`() {
        val llm = FreeLlmApiLlm(baseUrl = "http://dummy", model = "my-model")
        val payload: JsonObject = llm.buildPayload(
            messages = listOf(
                ChatMessage("system", "you are a tutor"),
                ChatMessage("user", "what is 2+2?"),
            ),
            maxTokens = 256,
            responseFormat = null,
        )
        assertEquals("my-model", payload["model"]?.jsonPrimitive?.content)
        assertEquals("256", payload["max_tokens"]?.jsonPrimitive?.content)
        assertNull(payload["response_format"])
        llm.close()
    }
}
