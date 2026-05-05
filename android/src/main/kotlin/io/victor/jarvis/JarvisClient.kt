package io.victor.jarvis

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
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

    suspend fun chat(baseUrl: String, msg: String): ChatReply {
        val resp = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(msg))
        }
        if (!resp.status.isSuccess()) {
            return ChatReply("[server ${resp.status.value}] ${resp.bodyAsText().take(300)}", "n/a")
        }
        return resp.body()
    }

    suspend fun runSub(baseUrl: String, cmd: String): SubReply {
        val resp = client.submitForm(
            url = "$baseUrl/api/sub",
            formParameters = parameters { append("cmd", cmd) },
        )
        if (!resp.status.isSuccess()) {
            return SubReply("[server ${resp.status.value}] ${resp.bodyAsText().take(300)}", "n/a")
        }
        return resp.body()
    }

    fun close() = client.close()
}
