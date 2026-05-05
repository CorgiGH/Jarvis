package jarvis.embeddings

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
import jarvis.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class EmbedRequest(val model: String, val input: String)

@Serializable
private data class EmbedItem(val embedding: FloatArray)

@Serializable
private data class EmbedResponse(val data: List<EmbedItem>)

class EmbeddingsClient(
    private val apiKey: String,
    private val model: String = "openai/text-embedding-3-small",
) : AutoCloseable {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
        }
    }

    suspend fun embed(text: String): FloatArray {
        val resp = client.post("${Config.OPENROUTER_BASE_URL}/embeddings") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://github.com/local/jarvis-kotlin")
            header("X-Title", "jarvis-kotlin")
            contentType(ContentType.Application.Json)
            setBody(EmbedRequest(model, text))
        }
        if (!resp.status.isSuccess()) {
            error("embed failed: ${resp.status.value} ${resp.bodyAsText().take(300)}")
        }
        val parsed: EmbedResponse = resp.body()
        return parsed.data.firstOrNull()?.embedding
            ?: error("embed returned no data")
    }

    override fun close() = client.close()
}
