package jarvis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Free-tier OpenRouter chat completion for tool_use round-trips.
 *
 * 2026-05-09 council (council-1778333402-tutor-next.md) recommended
 * wiring search_archival via real tool_use as the next ship. User
 * memory feedback_no_paid_apis.md says: only free-tier models. This
 * adapter targets that intersection.
 *
 * Default model: `google/gemini-2.0-flash-exp:free` — has tool_use
 * support, vision-capable, ~200 req/day cap on the free tier. If
 * the cap is hit the route should fall back to RelayLlm; that
 * fallback is the caller's responsibility (FallbackLlm composite).
 *
 * Wire format: OpenAI chat-completions with optional `tools` field.
 * When `tools` are passed, the response may include `tool_calls` on
 * the assistant message; caller is responsible for executing them
 * and feeding `role:"tool"` results back. The text-only [Llm] path
 * (no tools) just returns the assistant content as a String pair.
 */
class OpenRouterChatLlm(
    private val apiKey: String = resolveOpenRouterKey()
        ?: error("OPENROUTER_API_KEY required for OpenRouterChatLlm"),
    private val defaultModel: String = System.getenv("JARVIS_OPENROUTER_MODEL")
        ?: "meta-llama/llama-3.3-70b-instruct:free",
    private val baseUrl: String = Config.OPENROUTER_BASE_URL,
) : Llm {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 15_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): Pair<String, String> {
        val payload = buildJsonObject {
            put("model", defaultModel)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                for (m in messages) {
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
        }
        return postChat(payload)
    }

    /** Tool-using completion. Caller passes [tools] (already in OpenAI
     *  function-calling JSON shape) and pre-built [messages] (which may
     *  include prior tool_calls + tool_result entries). Returns the
     *  full message JsonObject so the caller can detect tool_calls vs
     *  final text. */
    suspend fun completeWithTools(
        messages: JsonArray,
        tools: JsonArray,
        maxTokens: Int = 1500,
        toolChoice: String = "auto",
        modelOverride: String? = null,
    ): JsonObject {
        val payload = buildJsonObject {
            put("model", modelOverride ?: defaultModel)
            put("max_tokens", maxTokens)
            put("messages", messages)
            put("tools", tools)
            put("tool_choice", JsonPrimitive(toolChoice))
        }
        val resp = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
            header("X-Title", "jarvis-kotlin tutor")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!resp.status.isSuccess()) {
            error("openrouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}")
        }
        val parsed = resp.body<JsonObject>()
        val choices = parsed["choices"] as? JsonArray ?: error("no choices in reply")
        val firstChoice = choices.firstOrNull() as? JsonObject ?: error("empty choices")
        return firstChoice["message"] as? JsonObject ?: error("no message in choice")
    }

    private suspend fun postChat(payload: JsonObject): Pair<String, String> {
        val resp = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
            header("X-Title", "jarvis-kotlin")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!resp.status.isSuccess()) {
            error("openrouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}")
        }
        val parsed: ChatResponse = resp.body()
        val choice = parsed.choices.firstOrNull() ?: error("openrouter returned no choices")
        return choice.message.content to (parsed.model.ifBlank { defaultModel })
    }

    override fun close() { client.close() }

    @Serializable
    private data class ChatResponse(
        val choices: List<Choice> = emptyList(),
        val model: String = "",
    )

    @Serializable
    private data class Choice(val message: AssistantMessage = AssistantMessage())

    @Serializable
    private data class AssistantMessage(
        val role: String = "assistant",
        val content: String = "",
    )
}
