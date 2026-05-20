package jarvis.tutor

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

enum class MailResult { SENT, LOGGED_ONLY, FAILED }

@Serializable
data class ResendPayload(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String,
)

/** Pure builder — unit-tested without any network. */
fun buildResendPayload(from: String, to: String, link: String, lang: String): ResendPayload {
    val subject = if (lang == "ro") "Conectare la Jarvis Tutor" else "Sign in to Jarvis Tutor"
    val body = if (lang == "ro") {
        "Apasă linkul pentru a te conecta (expiră în 15 minute):"
    } else {
        "Click the link to sign in (expires in 15 minutes):"
    }
    val html = """|<div style="font-family:monospace">
        |<p>$body</p>
        |<p><a href="$link">$link</a></p>
        |</div>""".trimMargin()
    return ResendPayload(from = from, to = listOf(to), subject = subject, html = html)
}

interface MagicLinkMailer {
    suspend fun send(toEmail: String, link: String, lang: String): MailResult
}

/** Dev fallback: logs the link to stdout so a developer can click it. */
class LoggingMailer : MagicLinkMailer {
    override suspend fun send(toEmail: String, link: String, lang: String): MailResult {
        println("[MagicLink] (dev, no RESEND_API_KEY) link for $toEmail -> $link")
        return MailResult.LOGGED_ONLY
    }
}

/** Real delivery via the Resend REST API, using the ktor-client already on the classpath. */
class ResendMailer(
    private val apiKey: String,
    private val fromAddress: String,
    private val http: HttpClient,
) : MagicLinkMailer {
    override suspend fun send(toEmail: String, link: String, lang: String): MailResult {
        val payload = buildResendPayload(fromAddress, toEmail, link, lang)
        return try {
            val resp: HttpResponse = http.post("https://api.resend.com/emails") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (resp.status.isSuccess()) MailResult.SENT else MailResult.FAILED
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            println("[MagicLink] Resend send failed for $toEmail: ${e.message}")
            MailResult.FAILED
        }
    }
}
