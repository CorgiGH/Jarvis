package io.victor.jarvis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatRequest(val msg: String)

@Serializable
data class ChatReply(val reply: String, val model: String)

@Serializable
data class SubReply(val text: String, val model: String)

/** Phase 3.1 — wire format mirroring server-side jarvis.ProactiveSignal. */
@Serializable
data class Signal(
    val id: String,
    val ts: String,
    val kind: String,
    val importance: Float,
    val sourceTs: String,
    val snippet: String,
    val rationale: String,
    val status: String = "emitted",
    val v: Int = 1,
)

@Serializable
data class SignalsReply(val signals: List<Signal>)

@Serializable
data class AckRequest(val signalId: String, val action: String)

class JarvisAuthException(message: String) : Exception(message)

class JarvisClient {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 15_000
        }
    }

    suspend fun chat(baseUrl: String, msg: String, authToken: String): ChatReply {
        val resp = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            if (authToken.isNotBlank()) {
                header("Authorization", "Bearer $authToken")
            }
            setBody(ChatRequest(msg))
        }
        if (!resp.status.isSuccess()) {
            return ChatReply("[server ${resp.status.value}] ${resp.bodyAsText().take(300)}", "n/a")
        }
        return resp.body()
    }

    suspend fun runSub(baseUrl: String, cmd: String, authToken: String): SubReply {
        val resp = client.submitForm(
            url = "$baseUrl/api/sub",
            formParameters = parameters { append("cmd", cmd) },
        ) {
            if (authToken.isNotBlank()) {
                header("Authorization", "Bearer $authToken")
            }
        }
        if (!resp.status.isSuccess()) {
            return SubReply("[server ${resp.status.value}] ${resp.bodyAsText().take(300)}", "n/a")
        }
        return resp.body()
    }

    /** R3 — POST a feedback action for a [signalId]. Best-effort: caller
     *  swallows network failures silently per spec. */
    suspend fun ackSignal(
        baseUrl: String,
        signalId: String,
        action: String,
        authToken: String,
    ): Boolean {
        return try {
            val resp = client.post("$baseUrl/api/signals/ack") {
                contentType(ContentType.Application.Json)
                if (authToken.isNotBlank()) {
                    header("Authorization", "Bearer $authToken")
                }
                setBody(AckRequest(signalId, action))
            }
            resp.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    /** Phase 3.1 — pull new signals server-side of [since] (ISO-8601). Empty
     *  [since] returns the most-recent [limit] signals. Throws
     *  [JarvisAuthException] on 401/403 so the caller can surface a re-auth
     *  notification + back off; throws plain Exception on other server errors. */
    suspend fun fetchSignals(
        baseUrl: String,
        since: String,
        authToken: String,
        limit: Int = 10,
    ): List<Signal> {
        val url = buildString {
            append("$baseUrl/api/signals?limit=$limit")
            if (since.isNotBlank()) {
                append("&since=")
                append(java.net.URLEncoder.encode(since, "UTF-8"))
            }
        }
        val resp = client.get(url) {
            if (authToken.isNotBlank()) {
                header("Authorization", "Bearer $authToken")
            }
        }
        if (resp.status.value == 401 || resp.status.value == 403) {
            throw JarvisAuthException("auth rejected: ${resp.status.value}")
        }
        if (!resp.status.isSuccess()) {
            error("server ${resp.status.value}: ${resp.bodyAsText().take(200)}")
        }
        return resp.body<SignalsReply>().signals
    }

    fun close() = client.close()
}
