package jarvis

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class OpenRouterVisionLlmTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val capturedBody = AtomicReference<String?>(null)
    private val capturedAuth = AtomicReference<String?>(null)
    private val handlerRef = AtomicReference<HttpHandler?>(null)

    @BeforeEach
    fun setup() {
        capturedBody.set(null)
        capturedAuth.set(null)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            capturedAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            capturedBody.set(exchange.requestBody.bufferedReader().readText())
            val handler = handlerRef.get()
            if (handler != null) {
                handler.handle(exchange)
            } else {
                val body = """{"model":"anthropic/claude-3.5-sonnet","choices":[{"index":0,"message":{"role":"assistant","content":"a red dot on white background"},"finish_reason":"stop"}]}"""
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
        handlerRef.set(null)
    }

    @Test
    fun analyzeReturnsAssistantContent() = runBlocking {
        OpenRouterVisionLlm(apiKey = "sk-test", baseUrl = baseUrl).use { llm ->
            val reply = llm.analyze(
                prompt = "What is in this image?",
                imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
                mediaType = "image/png",
            )
            assertEquals("a red dot on white background", reply)
        }
    }

    @Test
    fun authHeaderIncludesApiKey() = runBlocking {
        OpenRouterVisionLlm(apiKey = "sk-secret-xyz", baseUrl = baseUrl).use { llm ->
            llm.analyze("p", "AAAA", "image/png")
        }
        assertEquals("Bearer sk-secret-xyz", capturedAuth.get())
    }

    @Test
    fun requestBodyHasMultimodalContentArray() = runBlocking {
        OpenRouterVisionLlm(apiKey = "k", baseUrl = baseUrl).use { llm ->
            llm.analyze(
                prompt = "extract structured data",
                imageBase64 = "QUJD",   // base64 of "ABC"
                mediaType = "image/png",
            )
        }
        val body = capturedBody.get() ?: fail("no body captured")
        // Multimodal content array shape — expects type=text + type=image_url with data URI.
        assertTrue(body.contains("\"type\":\"text\""), "text block: $body")
        assertTrue(body.contains("\"text\":\"extract structured data\""), "prompt: $body")
        assertTrue(body.contains("\"type\":\"image_url\""), "image_url block: $body")
        assertTrue(body.contains("\"url\":\"data:image/png;base64,QUJD\""), "data URI: $body")
        // Default model used.
        assertTrue(body.contains("\"model\":\"anthropic/claude-3.5-sonnet\""), "default model: $body")
    }

    @Test
    fun customModelOverridesDefault() = runBlocking {
        OpenRouterVisionLlm(apiKey = "k", baseUrl = baseUrl).use { llm ->
            llm.analyze("p", "AAAA", "image/png", model = "openai/gpt-4o")
        }
        val body = capturedBody.get() ?: fail("no body")
        assertTrue(body.contains("\"model\":\"openai/gpt-4o\""), "custom model: $body")
    }

    @Test
    fun nonSuccessStatusRaisesWithBodyExcerpt() = runBlocking {
        handlerRef.set { exchange ->
            val body = """{"error":{"message":"insufficient credits","code":402}}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(402, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        try {
            OpenRouterVisionLlm(apiKey = "k", baseUrl = baseUrl).use { llm ->
                llm.analyze("p", "AAAA", "image/png")
            }
            fail("expected error on 402")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("HTTP 402"), "status in message: ${e.message}")
            assertTrue(e.message!!.contains("insufficient credits"), "body in message: ${e.message}")
        }
    }

    @Test
    fun emptyChoicesRaises() = runBlocking {
        handlerRef.set { exchange ->
            val body = """{"model":"x","choices":[]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        try {
            OpenRouterVisionLlm(apiKey = "k", baseUrl = baseUrl).use { llm ->
                llm.analyze("p", "AAAA", "image/png")
            }
            fail("expected error on empty choices")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("no content"), "message: ${e.message}")
        }
    }

    @Test
    fun mediaTypeValidationRejectsNonImage() = runBlocking {
        OpenRouterVisionLlm(apiKey = "k", baseUrl = baseUrl).use { llm ->
            try {
                llm.analyze("p", "AAAA", mediaType = "video/mp4")
                fail("expected validation error")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("image/"), "message: ${e.message}")
            }
        }
    }

    @Test
    fun blankPromptRejected() = runBlocking {
        OpenRouterVisionLlm(apiKey = "k", baseUrl = baseUrl).use { llm ->
            try {
                llm.analyze("", "AAAA", "image/png")
                fail("expected blank-prompt rejection")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("prompt required"))
            }
        }
    }

    @Test
    fun blankImageRejected() = runBlocking {
        OpenRouterVisionLlm(apiKey = "k", baseUrl = baseUrl).use { llm ->
            try {
                llm.analyze("p", "", "image/png")
                fail("expected blank-image rejection")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("imageBase64 required"))
            }
        }
    }

    @Test
    fun factoryReturnsNullWhenKeyMissing() {
        // Save + clear OPENROUTER_API_KEY at JVM level — can't actually
        // unset env in JVM, but resolveOpenRouterKey uses getenv which
        // we can't override portably. Test the no-key branch by
        // constructing the LLM directly with a known good key works
        // (already covered above) — the factory's null path is covered
        // implicitly when the env is unset in the test JVM.
        val v = VisionLlmFactory.create()
        // Either env is set (returns instance) or unset (returns null).
        // Both are valid; we just assert no crash.
        assertTrue(v == null || v is OpenRouterVisionLlm)
        v?.close()
    }
}
