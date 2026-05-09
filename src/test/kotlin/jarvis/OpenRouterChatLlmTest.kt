package jarvis

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [OpenRouterChatLlm]'s server-side `models:` fallback array
 * and client-side single-retry on 429.
 *
 * Background: 2026-05-09 probe (see council
 * .claude/council-cache/council-1778346847-or-fallback-chain.md and spec
 * docs/superpowers/specs/2026-05-09-openrouter-fallback-chain-design.md)
 * confirmed OpenRouter honors `models: [primary, ...fallbacks]` in the
 * chat-completions request body for server-side routing past 404/410, but
 * does NOT failover on 429. Hence the two-layer design exercised here:
 * `models:` injection + single-retry on transient 429 with Retry-After ≤ 5s.
 */
class OpenRouterChatLlmTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    /** Captured request bodies in order received. */
    private val capturedBodies = mutableListOf<String>()
    private val handlerRef = AtomicReference<HttpHandler?>(null)
    private val callCount = AtomicInteger(0)

    @BeforeEach
    fun setup() {
        capturedBodies.clear()
        callCount.set(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            callCount.incrementAndGet()
            val body = exchange.requestBody.bufferedReader().readText()
            synchronized(capturedBodies) { capturedBodies += body }
            val handler = handlerRef.get()
                ?: error("test forgot to set handlerRef")
            handler.handle(exchange)
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

    private fun reply200(model: String, content: String = "ok"): HttpHandler = HttpHandler { exchange ->
        val body = """{"model":"$model","choices":[{"index":0,"message":{"role":"assistant","content":"$content"},"finish_reason":"stop"}]}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    }

    @Test
    fun completeUsesPlainModelFieldWhenNoFallback() = runBlocking {
        handlerRef.set(reply200(model = "primary/model"))
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = emptyList(),
        )
        llm.use {
            val (_, model) = it.complete(
                listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10,
            )
            assertEquals("primary/model", model)
        }
        assertEquals(1, callCount.get())
        val body = Json.parseToJsonElement(capturedBodies[0]).jsonObject
        assertEquals(
            "primary/model",
            body["model"]?.jsonPrimitive?.contentOrNull,
            "single-model mode must send `model:` field",
        )
        assertNull(body["models"], "no fallback configured ⇒ no `models:` array")
    }

    @Test
    fun completeUsesModelsArrayWhenFallbackSet() = runBlocking {
        handlerRef.set(reply200(model = "fallback-1"))
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = listOf("fallback-1", "fallback-2"),
        )
        llm.use {
            it.complete(listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10)
        }
        val body = Json.parseToJsonElement(capturedBodies[0]).jsonObject
        assertNull(body["model"], "fallback configured ⇒ no plain `model:` field")
        val models = body["models"]?.jsonArray ?: error("expected `models:` array")
        assertEquals(3, models.size)
        assertEquals("primary/model", models[0].jsonPrimitive.content)
        assertEquals("fallback-1", models[1].jsonPrimitive.content)
        assertEquals("fallback-2", models[2].jsonPrimitive.content)
    }

    @Test
    fun responseModelFieldPropagatesToCaller() = runBlocking {
        // OR routed past primary (404 at edge) and served from fallback-1.
        handlerRef.set(reply200(model = "fallback-1", content = "served by fallback"))
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = listOf("fallback-1"),
        )
        llm.use {
            val (text, model) = it.complete(
                listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10,
            )
            assertEquals("served by fallback", text)
            assertEquals(
                "fallback-1",
                model,
                "model tag must reflect the OR-edge-routed model, not the configured primary",
            )
        }
    }

    @Test
    fun retriesOnceOn429WithShortRetryAfter() = runBlocking {
        val responseN = AtomicInteger(0)
        handlerRef.set(HttpHandler { exchange ->
            if (responseN.incrementAndGet() == 1) {
                exchange.responseHeaders.add("Retry-After", "1")
                exchange.responseHeaders.add("Content-Type", "application/json")
                val body = """{"error":{"message":"rate limited","code":429}}"""
                exchange.sendResponseHeaders(429, body.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
            } else {
                val body = """{"model":"primary/model","choices":[{"index":0,"message":{"role":"assistant","content":"recovered"},"finish_reason":"stop"}]}"""
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
            }
        })
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = emptyList(),
        )
        val started = System.currentTimeMillis()
        val (text, _) = llm.use {
            it.complete(listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10)
        }
        val elapsed = System.currentTimeMillis() - started
        assertEquals("recovered", text)
        assertEquals(2, callCount.get(), "must retry exactly once on 429 + Retry-After ≤ 5")
        assertTrue(
            elapsed >= 1000,
            "must honor Retry-After by sleeping ≥ 1000ms (got ${elapsed}ms)",
        )
    }

    @Test
    fun propagatesOn429WithLongRetryAfter() = runBlocking {
        handlerRef.set(HttpHandler { exchange ->
            exchange.responseHeaders.add("Retry-After", "30")
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """{"error":{"message":"rate limited","code":429}}"""
            exchange.sendResponseHeaders(429, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        })
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = emptyList(),
        )
        val ex = assertFails {
            llm.use {
                it.complete(listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10)
            }
        }
        assertEquals(1, callCount.get(), "must NOT retry when Retry-After > 5 (would block too long)")
        assertTrue(
            ex.message?.contains("429") == true,
            "thrown error must mention the 429: '${ex.message}'",
        )
    }

    @Test
    fun completeWithToolsHonorsModelOverride() = runBlocking {
        handlerRef.set(HttpHandler { exchange ->
            val body = """{"model":"specific/pinned","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        })
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = listOf("fallback-1", "fallback-2"),
        )
        llm.use {
            val msgArr = buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", "ping") })
            }
            it.completeWithTools(
                messages = msgArr,
                tools = buildJsonArray { },
                modelOverride = "specific/pinned",
            )
        }
        val body = Json.parseToJsonElement(capturedBodies[0]).jsonObject
        assertEquals(
            "specific/pinned",
            body["model"]?.jsonPrimitive?.contentOrNull,
            "modelOverride must pin to a single model and ignore the fallback chain",
        )
        assertNull(body["models"], "modelOverride present ⇒ no `models:` array")
    }

    @Test
    fun completeWithToolsReturnsActualModel() = runBlocking {
        // OR routed past primary (404 or 429) and served from fallback-2.
        handlerRef.set(HttpHandler { exchange ->
            val body = """{"model":"fallback-2","choices":[{"index":0,"message":{"role":"assistant","content":"served"},"finish_reason":"stop"}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        })
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = listOf("fallback-1", "fallback-2"),
        )
        val reply = llm.use {
            val msgArr = buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", "ping") })
            }
            it.completeWithTools(messages = msgArr, tools = buildJsonArray { })
        }
        assertEquals(
            "fallback-2",
            reply.model,
            "ToolCallReply.model must reflect OR-edge-routed model so caller can pin for next round",
        )
        assertEquals(
            "served",
            (reply.message["content"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull,
        )
    }
}
