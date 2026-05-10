package jarvis.tutor.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

typealias EventId = String

/**
 * Google Calendar REST client (minimal subset: events.insert).
 *
 * Scope required: [https://www.googleapis.com/auth/calendar.events]
 *
 * All HTTP is delegated to [GoogleApiClient] which handles
 * auth, 401-refresh, and 429 back-off transparently.
 */
class CalendarClient(private val api: GoogleApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a Calendar event and return its [EventId] (`id` field from response).
     *
     * @param calendarId  Calendar identifier; use `"primary"` for the user's default.
     * @param summary     Event title.
     * @param startIso    ISO-8601 datetime with timezone offset, e.g. `2026-05-10T14:00:00+03:00`.
     * @param endIso      ISO-8601 datetime with timezone offset.
     */
    fun eventsInsert(
        calendarId: String = "primary",
        summary: String,
        startIso: String,
        endIso: String,
    ): Result<EventId> {
        val body = buildEventBody(summary, startIso, endIso)
        val path = "/calendar/v3/calendars/${urlEncode(calendarId)}/events"
        return api.httpPostRaw(path, body).map { responseBody ->
            val parsed = json.parseToJsonElement(responseBody).jsonObject
            parsed["id"]?.jsonPrimitive?.content
                ?: error("Calendar eventsInsert: response missing 'id' field. Raw: ${responseBody.take(300)}")
        }
    }

    private fun buildEventBody(summary: String, startIso: String, endIso: String): String =
        """{"summary":${jsonString(summary)},"start":{"dateTime":${jsonString(startIso)}},"end":{"dateTime":${jsonString(endIso)}}}"""

    private fun jsonString(s: String): String =
        "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
