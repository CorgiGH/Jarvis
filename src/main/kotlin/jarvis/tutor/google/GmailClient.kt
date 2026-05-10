package jarvis.tutor.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

typealias DraftId = String

/**
 * Google Gmail REST client (minimal subset: users.drafts.create).
 *
 * Scope required: [https://www.googleapis.com/auth/gmail.compose]
 * (compose only — this client NEVER sends emails; drafts only).
 *
 * The Gmail API requires RFC 822 message format, base64url-encoded,
 * in the `message.raw` field of the POST body. This client handles
 * the encoding internally so callers work with plain strings.
 *
 * All HTTP is delegated to [GoogleApiClient].
 */
class GmailClient(private val api: GoogleApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a Gmail draft. Never auto-sends.
     *
     * @param to      Recipient email address.
     * @param subject Email subject line.
     * @param body    Plain-text email body (UTF-8).
     * @param userId  Gmail user ID; always `"me"` for authenticated user.
     * @return        [DraftId] (`id` field from the created draft resource).
     */
    fun draftsCreate(
        to: String,
        subject: String,
        body: String,
        userId: String = "me",
    ): Result<DraftId> {
        val rfc822 = buildRfc822(to, subject, body)
        val raw = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rfc822.toByteArray(Charsets.UTF_8))
        val requestBody = """{"message":{"raw":"$raw"}}"""
        val path = "/gmail/v1/users/${urlEncode(userId)}/drafts"
        return api.httpPostRaw(path, requestBody).map { responseBody ->
            val parsed = json.parseToJsonElement(responseBody).jsonObject
            parsed["id"]?.jsonPrimitive?.content
                ?: error("Gmail draftsCreate: response missing 'id' field. Raw: ${responseBody.take(300)}")
        }
    }

    private fun buildRfc822(to: String, subject: String, body: String): String =
        "To: $to\r\n" +
            "Subject: $subject\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "\r\n" +
            body

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
