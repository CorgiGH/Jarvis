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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int,
)

@Serializable
private data class ChatChoice(val message: ChatMessage)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice>)

class LlmClient(private val apiKey: String) : AutoCloseable {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
        }
    }

    suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int = Config.MAX_TOKENS,
        chain: List<String> = Config.FALLBACK_CHAIN,
    ): Pair<String, String> {
        var lastError: String? = null
        for (model in chain) {
            try {
                val resp = client.post("${Config.OPENROUTER_BASE_URL}/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header("HTTP-Referer", "https://github.com/local/jarvis-kotlin")
                    header("X-Title", "jarvis-kotlin")
                    contentType(ContentType.Application.Json)
                    setBody(ChatRequest(model, messages, maxTokens))
                }
                val status = resp.status

                if (status == HttpStatusCode.TooManyRequests || status.value in setOf(502, 503)) {
                    val body = resp.bodyAsText().take(160)
                    System.err.println("[llm] $model: ${status.value}, trying next ($body)")
                    lastError = "${status.value} $body"
                    delay(500)
                    continue
                }
                if (status == HttpStatusCode.NotFound) {
                    val body = resp.bodyAsText()
                    if (body.contains("No endpoints found")) {
                        System.err.println("[llm] $model: 404 no endpoints, trying next")
                        lastError = body.take(160)
                        continue
                    }
                }
                if (!status.isSuccess()) {
                    error("[llm] $model: ${status.value} ${resp.bodyAsText().take(300)}")
                }

                val parsed: ChatResponse = resp.body()
                val text = parsed.choices.firstOrNull()?.message?.content ?: ""
                if (model != chain.first()) {
                    System.err.println("[llm] fell through to $model")
                }
                return text to model
            } catch (e: Exception) {
                lastError = e.message
                if (e is IllegalStateException) throw e
                System.err.println("[llm] $model: ${e.javaClass.simpleName} ${e.message?.take(160)}, trying next")
                continue
            }
        }
        error("All ${chain.size} fallback models failed. Last error: $lastError")
    }

    override fun close() = client.close()
}
