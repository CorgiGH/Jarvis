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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenAI-compatible ChatLlm that targets a self-hosted FreeLLMAPI proxy (or any
 * OpenAI-compatible endpoint). Mirrors [OpenRouterChatLlm]'s wire format.
 *
 * Configuration (environment variables):
 *   FREELLMAPI_BASE_URL   Base URL of the OpenAI-compatible endpoint.
 *                         Default: "http://localhost:8080" (placeholder — no live
 *                         endpoint exists in-repo; the service must be started
 *                         externally before this provider is selected).
 *   FREELLMAPI_MODEL      Model ID to use.
 *                         Default: "llama-3-8b-instruct" (sane placeholder).
 *
 * Fail-loud contract: if the server is unreachable or returns a non-2xx status
 * the call throws [IllegalStateException] with a clear message including the
 * HTTP status and a body excerpt — the same pattern used by [OpenRouterChatLlm].
 *
 * NOTE: responseFormat is accepted and forwarded to the endpoint. If the target
 * endpoint does not support it the server will ignore or reject it; callers that
 * rely on json_object coercion should be aware.
 */
class FreeLlmApiLlm(
    /** Base URL of the OpenAI-compatible endpoint. Override via FREELLMAPI_BASE_URL. */
    val baseUrl: String = System.getenv("FREELLMAPI_BASE_URL")
        ?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8080",
    /** Model identifier forwarded in the request body. Override via FREELLMAPI_MODEL. */
    val model: String = System.getenv("FREELLMAPI_MODEL")
        ?.takeIf { it.isNotBlank() }
        ?: "llama-3-8b-instruct",
) : Llm {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 10_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
        responseFormat: String?,
    ): Pair<String, String> {
        val payload = buildPayload(messages, maxTokens, responseFormat)
        val resp = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer freellmapi")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!resp.status.isSuccess()) {
            error(
                "freellmapi HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}. " +
                    "Ensure FREELLMAPI_BASE_URL points to a running OpenAI-compatible server.",
            )
        }
        val parsed: ChatResponse = resp.body()
        val choice = parsed.choices.firstOrNull()
            ?: error("freellmapi returned no choices — endpoint may be misconfigured")
        return (choice.message.content ?: "") to (parsed.model.ifBlank { model })
    }

    /**
     * Build the OpenAI chat-completions JSON payload. Extracted for unit-test
     * access (same pattern as [OpenRouterChatLlm.buildCompletePayload]).
     */
    internal fun buildPayload(
        messages: List<ChatMessage>,
        maxTokens: Int,
        responseFormat: String?,
    ): JsonObject = buildJsonObject {
        put("model", model)
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
        val content: String? = "",
    )
}
