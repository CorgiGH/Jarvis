package jarvis.tutor

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import jarvis.OpenRouterChatLlm
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Council R5 Risk Analyst HIGH risk: mid-tool-loop model swap.
 *
 * Background: when JARVIS_OPENROUTER_FALLBACK_MODELS is set, OR's edge
 * router picks ONE model per HTTP request. In a multi-round tool loop,
 * round 1 might land on model A (because primary 404'd) and round 2 might
 * land on model B (because A then 429'd). B inherits the assistant
 * tool_calls history shaped by A's dialect → degraded mid-task output.
 *
 * Fix: JarvisToolset captures the actually-used model from round 1's
 * ToolCallReply.model and pins it via modelOverride for rounds 2+.
 *
 * These tests assert: (1) round 1 sends the `models:` chain, (2) round 2
 * sends a single `model:` field equal to round 1's returned model,
 * (3) the final ToolReply.model reflects the pinned model.
 */
class JarvisToolsetPinningTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

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

    @Test
    fun pinsRound1ModelForRound2OfToolLoop() = runBlocking {
        // Round 1 returns tool_calls for an unknown tool name (dispatch returns
        // "unknown tool: ..." — short, deterministic, doesn't depend on a
        // KnowledgeGraph being loaded). Round 1's response model field is
        // "fallback-1" — simulating OR routing past the primary.
        // Round 2 returns the final text with no tool_calls.
        handlerRef.set(HttpHandler { exchange ->
            val n = callCount.get()
            val body = if (n == 1) {
                """{"model":"fallback-1","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_a","type":"function","function":{"name":"nonexistent_tool","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}"""
            } else {
                """{"model":"fallback-1","choices":[{"index":0,"message":{"role":"assistant","content":"done"},"finish_reason":"stop"}]}"""
            }
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
        val toolset = JarvisToolset(llm = llm)
        val nonexistentSkillsRoot = Path.of(System.getProperty("java.io.tmpdir"), "no-such-skills-${System.nanoTime()}")
        val reply = toolset.use {
            it.chat(
                systemPrompt = "test system prompt",
                userText = "trigger tool loop",
                skillsRoot = nonexistentSkillsRoot,
            )
        }

        assertEquals(2, callCount.get(), "expected exactly 2 LLM calls (round 1 = tool_calls; round 2 = final)")

        val round1 = Json.parseToJsonElement(capturedBodies[0]).jsonObject
        val round2 = Json.parseToJsonElement(capturedBodies[1]).jsonObject

        // Round 1: chain mode — `models:` array, no plain `model:`.
        assertNull(round1["model"], "round 1 must use `models:` array (chain mode)")
        val models = round1["models"]?.jsonArray ?: error("round 1 must include `models:` array")
        assertEquals(3, models.size, "chain = primary + 2 fallbacks")
        assertEquals("primary/model", models[0].jsonPrimitive.content)
        assertEquals("fallback-1", models[1].jsonPrimitive.content)
        assertEquals("fallback-2", models[2].jsonPrimitive.content)

        // Round 2: pinned to round-1's actually-used model.
        assertNull(round2["models"], "round 2 must NOT send `models:` array — must pin via `model:`")
        assertEquals(
            "fallback-1",
            round2["model"]?.jsonPrimitive?.contentOrNull,
            "round 2 must pin to round 1's actually-used model (fallback-1) to avoid mid-loop dialect swap",
        )

        assertEquals(
            "fallback-1",
            reply.model,
            "ToolReply.model must reflect the pinned model, not the legacy 'openrouter-tools' placeholder",
        )
        assertEquals("done", reply.text, "final reply text must come from round 2")
    }
}
