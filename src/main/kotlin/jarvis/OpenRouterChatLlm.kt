package jarvis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
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
/**
 * Parse the comma-separated `JARVIS_OPENROUTER_FALLBACK_MODELS` env var
 * into a list of model IDs. Used by [OpenRouterChatLlm]'s constructor to
 * populate its server-side fallback chain. Whitespace is trimmed per
 * element; blank entries are dropped. Unset / empty ⇒ empty list ⇒ no
 * `models:` array is sent (status quo, single-model `model:` field only).
 *
 * Validation: invalid model IDs are NOT pre-flighted — they surface as
 * HTTP 400 from OpenRouter on first request. See the design doc at
 * docs/superpowers/specs/2026-05-09-openrouter-fallback-chain-design.md
 * for the rationale + V2 mitigation.
 */
private fun parseFallbackModels(): List<String> =
    System.getenv("JARVIS_OPENROUTER_FALLBACK_MODELS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

class OpenRouterChatLlm(
    private val apiKey: String = resolveOpenRouterKey()
        ?: error("OPENROUTER_API_KEY required for OpenRouterChatLlm"),
    private val defaultModel: String = System.getenv("JARVIS_OPENROUTER_MODEL")
        ?: "meta-llama/llama-3.3-70b-instruct:free",
    private val baseUrl: String = Config.OPENROUTER_BASE_URL,
    private val fallbackModels: List<String> = parseFallbackModels(),
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
        responseFormat: String?,
    ): Pair<String, String> {
        val payload = buildCompletePayload(
            messages = messages,
            maxTokens = maxTokens,
            defaultModel = defaultModel,
            fallbackModels = fallbackModels,
            modelOverride = null,
            responseFormat = responseFormat,
        )
        return postChat(payload)
    }

    companion object {
        /**
         * Build the chat-completions request body. Extracted from the
         * instance [complete] so unit tests can exercise the JSON shape
         * (including A.5's response_format wiring) without spinning up an
         * HTTP server. The instance method delegates here with its
         * constructor-bound default/fallback model fields.
         */
        fun buildCompletePayload(
            messages: List<ChatMessage>,
            maxTokens: Int,
            defaultModel: String,
            fallbackModels: List<String>,
            modelOverride: String?,
            responseFormat: String? = null,
        ): JsonObject = buildJsonObject {
            when {
                modelOverride != null -> put("model", modelOverride)
                fallbackModels.isEmpty() -> put("model", defaultModel)
                else -> put("models", buildJsonArray {
                    add(JsonPrimitive(defaultModel))
                    fallbackModels.forEach { add(JsonPrimitive(it)) }
                })
            }
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                for (m in messages) {
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
            if (responseFormat != null) {
                put("response_format", buildJsonObject {
                    put("type", responseFormat)
                })
            }
        }
    }

    /**
     * Reply from a tool-using completion. [message] is the assistant
     * message JsonObject (caller checks for `tool_calls` to decide whether
     * to dispatch or treat as final text). [model] is the actually-used
     * model returned by OR's edge router — important for callers that
     * want to PIN the same model across multiple tool rounds (Risk
     * Analyst HIGH risk in council R5: mid-loop swap with tool-call
     * history shaped by a different model's dialect).
     */
    @Serializable
    data class ToolCallReply(val message: JsonObject, val model: String)

    /** Tool-using completion. Caller passes [tools] (already in OpenAI
     *  function-calling JSON shape) and pre-built [messages] (which may
     *  include prior tool_calls + tool_result entries). Returns the
     *  assistant message + actually-used model so the caller can detect
     *  tool_calls vs final text AND pin the model for subsequent rounds. */
    suspend fun completeWithTools(
        messages: JsonArray,
        tools: JsonArray,
        maxTokens: Int = 1500,
        toolChoice: String = "auto",
        modelOverride: String? = null,
    ): ToolCallReply {
        val payload = buildJsonObject {
            putModelField(modelOverride)
            put("max_tokens", maxTokens)
            put("messages", messages)
            put("tools", tools)
            put("tool_choice", JsonPrimitive(toolChoice))
        }
        val resp = postWithRetry(payload, titleHeader = "jarvis-kotlin tutor")
        if (!resp.status.isSuccess()) {
            error("openrouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}")
        }
        val parsed = resp.body<JsonObject>()
        val choices = parsed["choices"] as? JsonArray ?: error("no choices in reply")
        val firstChoice = choices.firstOrNull() as? JsonObject ?: error("empty choices")
        val message = firstChoice["message"] as? JsonObject ?: error("no message in choice")
        val actualModel = (parsed["model"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: modelOverride
            ?: defaultModel
        return ToolCallReply(message = message, model = actualModel)
    }

    private suspend fun postChat(payload: JsonObject): Pair<String, String> {
        val resp = postWithRetry(payload, titleHeader = "jarvis-kotlin")
        if (!resp.status.isSuccess()) {
            error("openrouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}")
        }
        val parsed: ChatResponse = resp.body()
        val choice = parsed.choices.firstOrNull() ?: error("openrouter returned no choices")
        return (choice.message.content ?: "") to (parsed.model.ifBlank { defaultModel })
    }

    /**
     * Pick which model field to send. Per the design doc, `modelOverride`
     * takes precedence (caller is explicitly pinning); otherwise an
     * empty fallback chain ⇒ single `model:` field; non-empty chain ⇒
     * `models: [primary, ...fallbacks]` for OR-edge server-side routing.
     */
    private fun JsonObjectBuilder.putModelField(modelOverride: String?) {
        when {
            modelOverride != null -> put("model", modelOverride)
            fallbackModels.isEmpty() -> put("model", defaultModel)
            else -> put("models", buildJsonArray {
                add(JsonPrimitive(defaultModel))
                fallbackModels.forEach { add(JsonPrimitive(it)) }
            })
        }
    }

    /**
     * POST with single retry on transient 429. Honors the upstream
     * `Retry-After` header up to 5 seconds; longer waits propagate so the
     * caller's degraded chat path (WebMain.kt's `tutorSurface` short-circuit)
     * serves the turn instead of blocking the user. 429s without a
     * `Retry-After` header also propagate without retry — we won't guess
     * the cooldown.
     */
    private suspend fun postWithRetry(
        payload: JsonObject,
        titleHeader: String,
    ): HttpResponse {
        suspend fun once(): HttpResponse =
            client.post("${baseUrl.trimEnd('/')}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
                header("X-Title", titleHeader)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

        val first = once()
        if (first.status.value != 429) return first

        val retryAfterSec = first.headers["Retry-After"]?.toIntOrNull() ?: return first
        if (retryAfterSec <= 0 || retryAfterSec > 5) return first

        System.err.println(
            "openrouter: 429 with Retry-After ${retryAfterSec}s — sleeping then retrying once",
        )
        delay(retryAfterSec.toLong() * 1000)
        return once()
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
        // Some OpenRouter models (refusal filters, certain free-tier models)
        // return `content: null`. Default to empty string + nullable so
        // kotlinx.serialization doesn't blow up at parse time. The caller
        // collapses null/blank to "" via `?: ""`.
        val content: String? = "",
    )
}
