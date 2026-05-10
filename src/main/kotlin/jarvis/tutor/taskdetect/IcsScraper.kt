package jarvis.tutor.taskdetect

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase 5.3 closer: real CourseScraper backed by iCalendar (.ics)
 * feeds. Most VLEs (Moodle, Google Classroom, Outlook, Stud.IP) can
 * export their assignment calendar as ICS — that's the lingua franca
 * we standardize on rather than per-platform HTML scraping (brittle).
 *
 * Configuration: env vars matching `JARVIS_COURSE_ICS_<SUBJECT>=<url>`.
 *   e.g. `JARVIS_COURSE_ICS_PA=https://courses.uaic.ro/.../calendar.ics`
 *
 * Each VEVENT becomes one DetectedTask:
 *   - externalId = UID  (RFC 5545 uniqueness guarantee)
 *   - subject    = the env-key suffix
 *   - title      = SUMMARY (truncated to 256)
 *   - deadline   = DTEND if present else DTSTART
 *   - sourceUrl  = the feed URL
 *   - kind       = "calendar"
 *
 * Failures are isolated per feed: a malformed or unreachable ICS does
 * not abort the run; the orchestrator (TaskDetectorAggregator) just
 * skips that source.
 */
class IcsScraper(
    private val subject: String,
    private val feedUrl: String,
    private val httpFetch: (String) -> String? = ::defaultFetch,
) : TaskDetector {
    override val sourceId: String = "ics:${subject.lowercase()}"

    override suspend fun discover(): List<DetectedTask> {
        val body = httpFetch(feedUrl) ?: return emptyList()
        val events = parseVevents(body)
        return events.mapNotNull { ev ->
            val uid = ev["UID"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val summary = ev["SUMMARY"]?.unescapeIcs()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val dtStartRaw = ev["DTSTART"]
            val dtEndRaw = ev["DTEND"] ?: dtStartRaw
            val deadline = parseIcsInstant(dtEndRaw ?: return@mapNotNull null) ?: return@mapNotNull null
            DetectedTask(
                sourceId = sourceId,
                externalId = uid,
                subject = subject.uppercase().take(32),
                title = summary.take(256),
                deadline = deadline,
                problemPath = null,
                sourceUrl = feedUrl,
                rawMetadata = mapOf(
                    "kind" to "calendar",
                    "uid" to uid,
                    "summary" to summary.take(256),
                ),
            )
        }
    }

    companion object {
        private val SHARED_HTTP: HttpClient by lazy {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
        }

        private fun defaultFetch(url: String): String? {
            return try {
                val req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "jarvis-tutor/1.0 (ICS reader)")
                    .GET().build()
                val resp = SHARED_HTTP.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() in 200..299) resp.body() else null
            } catch (e: Exception) {
                System.err.println("[ics:$url] fetch failed: ${e.message?.take(160)}")
                null
            }
        }

        /** Build all configured scrapers from the JARVIS_COURSE_ICS_* env namespace. */
        fun fromEnv(env: Map<String, String> = System.getenv()): List<IcsScraper> {
            val prefix = "JARVIS_COURSE_ICS_"
            return env.entries
                .filter { it.key.startsWith(prefix) && it.value.isNotBlank() }
                .map { (k, v) -> IcsScraper(subject = k.removePrefix(prefix), feedUrl = v.trim()) }
        }

        /**
         * Parse VEVENT blocks out of a raw ICS body. Returns each event as a
         * key→value map (last writer wins on duplicate keys; rare in practice).
         * Handles RFC 5545 line folding (continuation lines start with space/tab).
         */
        internal fun parseVevents(body: String): List<Map<String, String>> {
            val unfolded = unfoldLines(body)
            val out = mutableListOf<Map<String, String>>()
            var current: MutableMap<String, String>? = null
            for (line in unfolded) {
                when {
                    line.equals("BEGIN:VEVENT", ignoreCase = true) -> current = mutableMapOf()
                    line.equals("END:VEVENT", ignoreCase = true) -> {
                        current?.let { out += it }
                        current = null
                    }
                    current != null -> {
                        // Strip property params: "DTSTART;TZID=UTC:20260101T..." → name "DTSTART"
                        val colon = line.indexOf(':')
                        if (colon < 0) continue
                        val nameWithParams = line.substring(0, colon)
                        val value = line.substring(colon + 1)
                        val name = nameWithParams.substringBefore(';').trim().uppercase()
                        if (name.isNotEmpty()) current[name] = value
                    }
                }
            }
            return out
        }

        private fun unfoldLines(body: String): List<String> {
            val raw = body.replace("\r\n", "\n").split('\n')
            val out = mutableListOf<String>()
            for (line in raw) {
                if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                    out[out.size - 1] = out.last() + line.substring(1)
                } else {
                    out += line
                }
            }
            return out
        }

        /** Parse RFC 5545 DTSTART/DTEND values: "YYYYMMDDTHHMMSSZ", "YYYYMMDDTHHMMSS", or "YYYYMMDD". */
        internal fun parseIcsInstant(raw: String): Instant? {
            val v = raw.trim().substringBefore(';').trim()
            return runCatching {
                when {
                    v.endsWith("Z") -> {
                        val dt = LocalDateTime.parse(v.removeSuffix("Z"), ICS_DT_FMT)
                        dt.toInstant(ZoneOffset.UTC)
                    }
                    v.contains("T") -> {
                        // Floating local time — assume UTC. Real-world ICS feeds
                        // either suffix Z or carry a TZID param we ignore for now.
                        LocalDateTime.parse(v, ICS_DT_FMT).toInstant(ZoneOffset.UTC)
                    }
                    v.length == 8 -> {
                        // VALUE=DATE: end-of-day in UTC so deadline ranking
                        // doesn't collapse multiple same-day items.
                        val d = LocalDate.parse(v, ICS_DATE_FMT)
                        ZonedDateTime.of(d, java.time.LocalTime.of(23, 59), ZoneOffset.UTC).toInstant()
                    }
                    else -> null
                }
            }.getOrNull()
        }

        private val ICS_DT_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        private val ICS_DATE_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd")

        private fun String.unescapeIcs(): String =
            this.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
    }
}
