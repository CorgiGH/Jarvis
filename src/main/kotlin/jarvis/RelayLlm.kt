package jarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/**
 * HTTP-based provider that forwards completions to a Tailscale-reachable
 * PC running tools/pc-relay-server.py wrapping `claude --print`. The
 * Claude OAuth token never leaves the PC; the VPS just sees a JSON
 * request/response over the private tailnet.
 *
 * Used as the primary Llm in JARVIS_LLM=fallback mode so the bot
 * consumes the user's Claude Max subscription via residential IP rather
 * than running `claude` on the VPS itself — which is a stronger
 * abuse-detection vector per the council 2026-05-08 review (datacenter
 * IP + headless TTY + sustained off-hours traffic looks bot-shaped).
 * FallbackLlm wraps this with CopilotLlm for the PC-asleep window.
 *
 * Throws IOException on connect / read failure so FallbackLlm catches
 * and falls through cleanly. Non-2xx responses surface as
 * IllegalStateException carrying the body excerpt for debugging — also
 * caught by FallbackLlm.
 *
 * Env:
 *   JARVIS_RELAY_URL       base URL of PC server, e.g. http://100.100.0.5:9999
 *   JARVIS_RELAY_TOKEN     bearer token shared with PC server
 *   JARVIS_RELAY_CONNECT_S connect timeout, default 5s (council: fail fast
 *                          when PC asleep so fallback engages quickly)
 *   JARVIS_RELAY_READ_S    read timeout, default 120s (claude --print
 *                          cold-start can be 1-3s, plus generation time)
 */
class RelayLlm(
    private val baseUrl: String = System.getenv("JARVIS_RELAY_URL")
        ?: error("JARVIS_RELAY_URL not set"),
    private val token: String = System.getenv("JARVIS_RELAY_TOKEN")
        ?: error("JARVIS_RELAY_TOKEN not set"),
    connectTimeoutSeconds: Long =
        System.getenv("JARVIS_RELAY_CONNECT_S")?.toLongOrNull() ?: 5,
    private val readTimeoutSeconds: Long =
        System.getenv("JARVIS_RELAY_READ_S")?.toLongOrNull() ?: 120,
) : Llm {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): Pair<String, String> {
        val body = json.encodeToString(
            RelayRequest.serializer(),
            RelayRequest(messages = messages, max_tokens = maxTokens),
        )

        val req = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/complete"))
            .timeout(Duration.ofSeconds(readTimeoutSeconds))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val resp: HttpResponse<String> = withContext(Dispatchers.IO) {
            try {
                client.send(req, HttpResponse.BodyHandlers.ofString())
            } catch (e: HttpTimeoutException) {
                throw IOException("relay timeout after ${readTimeoutSeconds}s: ${e.message}", e)
            } catch (e: ConnectException) {
                throw IOException("relay connect refused: ${e.message}", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("relay call interrupted", e)
            }
        }

        if (resp.statusCode() !in 200..299) {
            error("relay HTTP ${resp.statusCode()}: ${resp.body().take(400)}")
        }

        val parsed = try {
            json.decodeFromString(RelayResponse.serializer(), resp.body())
        } catch (e: SerializationException) {
            error("relay malformed response: ${e.message?.take(160)}")
        }

        return parsed.reply to parsed.model.ifBlank { "claude-max-relay" }
    }

    @Serializable
    private data class RelayRequest(
        val messages: List<ChatMessage>,
        val max_tokens: Int,
    )

    @Serializable
    private data class RelayResponse(
        val reply: String,
        val model: String = "",
    )
}
