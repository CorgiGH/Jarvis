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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

/**
 * Tutor Layer B Task 1 prerequisite (Council R3 follow-up 2026-05-09):
 * existing relay path (RelayLlm) is text-only by construction —
 * `RelayRequest.messages: List<ChatMessage>` and `ChatMessage.content:
 * String`. Cannot route image content blocks. Vision sensor must use
 * a dedicated provider that accepts multimodal content.
 *
 * OpenRouter exposes the OpenAI chat-completions wire format with
 * multimodal `content` arrays — `image_url` blocks accept either a
 * fully-qualified URL or a `data:image/png;base64,...` data URI. We
 * use the data URI form so screenshots stay in-process; never have to
 * upload to an external blob store.
 *
 * Default model: `anthropic/claude-3.5-sonnet` — strong vision +
 * structured-extraction performance. Override per-call via [model] arg
 * when a cheaper/faster model is appropriate.
 */
interface VisionLlm : AutoCloseable {
    /**
     * Send a single user turn carrying [prompt] text + [imageBase64]
     * (no `data:` prefix — caller supplies raw base64). [mediaType]
     * is the MIME, e.g. `image/png` / `image/jpeg`. Returns the
     * assistant text reply.
     */
    suspend fun analyze(
        prompt: String,
        imageBase64: String,
        mediaType: String = "image/png",
        maxTokens: Int = 1024,
        model: String? = null,
    ): String

    override fun close() {}
}

class OpenRouterVisionLlm(
    private val apiKey: String,
    private val defaultModel: String = "anthropic/claude-3.5-sonnet",
    private val baseUrl: String = Config.OPENROUTER_BASE_URL,
) : VisionLlm {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            // Vision calls can take 5-15s; pad both timeouts vs the
            // text-only relay path so we don't fail-fast on a normal
            // screenshot extraction.
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun analyze(
        prompt: String,
        imageBase64: String,
        mediaType: String,
        maxTokens: Int,
        model: String?,
    ): String {
        require(prompt.isNotBlank()) { "prompt required" }
        require(imageBase64.isNotBlank()) { "imageBase64 required" }
        require(mediaType.startsWith("image/")) { "mediaType must be image/*: $mediaType" }

        // Build the multimodal payload manually via JsonObject so the
        // polymorphic `content` array (text + image_url blocks) doesn't
        // require a dozen sealed-class wrappers.
        val payload = buildJsonObject {
            put("model", model ?: defaultModel)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:$mediaType;base64,$imageBase64")
                            })
                        })
                    })
                })
            })
        }

        val resp = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
            header("X-Title", "jarvis-kotlin tutor")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        if (!resp.status.isSuccess()) {
            error("vision call failed: HTTP ${resp.status.value} ${resp.bodyAsText().take(400)}")
        }

        val parsed: ChatCompletionResponse = resp.body()
        return parsed.choices.firstOrNull()?.message?.content
            ?: error("vision response had no content: ${json.encodeToString(JsonObject.serializer(), payload)}")
    }

    override fun close() {
        client.close()
    }

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
        val model: String = "",
    )

    @Serializable
    private data class Choice(
        val index: Int = 0,
        val message: AssistantMessage = AssistantMessage(),
        @SerialName("finish_reason") val finishReason: String = "",
    )

    @Serializable
    private data class AssistantMessage(
        val role: String = "assistant",
        val content: String = "",
    )
}

object VisionLlmFactory {
    /** Resolve a vision-capable LLM. Currently OpenRouter-only (relay +
     *  claude-max + copilot are all text-only). Returns null if no
     *  OPENROUTER_API_KEY env is set so callers can degrade gracefully
     *  rather than crash on missing config. */
    fun create(): VisionLlm? {
        val key = resolveOpenRouterKey() ?: return null
        return OpenRouterVisionLlm(apiKey = key)
    }
}
