package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorRoutesTest {
    @Test
    fun `GET tutor returns index html`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/tutor/")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("<div id=\"root\">") || body.contains("<div id=\\\"root\\\">"),
            "expected root div in served bundle, got: ${body.take(200)}")
    }

    @Test
    fun `GET api v1 health returns ok json`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"ok\":true"))
    }

    // --- Student-stand-in Task 0.5 -----------------------------------------
    // The drill-grade endpoint must append a redacted TutorEvent to the event
    // log on every code path. This test exercises the LLM-exception path
    // (OpenRouterChatLlm requires OPENROUTER_API_KEY at construction time —
    // when unset, the route falls through the catch arm with status="error").
    // The envelope must (a) appear in the configured log dir, (b) carry
    // is_synthetic=true when X-Standin-Run:1 is present, (c) carry a sha256
    // of the userAttempt rather than the raw R-code body.

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seedSession(ctx: TutorContext): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        return userId to sid
    }

    @Test
    fun `POST drill grade writes a redacted TutorEvent`(@TempDir tmp: Path) = testApplication {
        // Point the TutorEventLog.GLOBAL lazy singleton at our temp dir. This
        // must happen BEFORE the first /api/v1/drill/grade call (which is the
        // first access to GLOBAL). Once the lazy initializer fires, the path
        // is frozen for the JVM, so a previous test in this run could have
        // captured a different dir — but no other test currently triggers
        // GLOBAL, so this property win.
        val eventLogDir = tmp.resolve("eventlog")
        java.nio.file.Files.createDirectories(eventLogDir)
        System.setProperty("jarvis.tutor.event_log.dir", eventLogDir.toString())

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Use a sufficiently long userAttempt so the head/tail 40-char
        // previews are strictly partial — proves the FULL body never lands
        // in the envelope. The middle slice "RAW_RCODE_MIDDLE_MARKER" should
        // not appear anywhere on the JSONL line.
        val rcode = "library(VGAM); set.seed(42); s <- rlaplace(10000, 0, 1); " +
            "RAW_RCODE_MIDDLE_MARKER ; " +
            "hist(s, breaks=50, main=laplace_samples, col=steelblue)"
        val body = """{
            "taskId":"task-1",
            "problemId":"d1",
            "userAttempt":"$rcode",
            "problemStatement":"...",
            "expectedAnswerHint":"hist of laplace samples",
            "language":"r",
            "prediction":"approximate"
        }""".trimIndent()

        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            header("X-Standin-Run", "1")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        // The route returns 200 even on LLM error (graceful degraded reply).
        assertEquals(HttpStatusCode.OK, resp.status)

        // Async writer needs a beat — wait up to 2s for the line to land.
        val today = java.time.LocalDate.now().toString()
        val logFile = eventLogDir.resolve("tutor_events.$today.jsonl").toFile()
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline && (!logFile.exists() || logFile.readLines().isEmpty())) {
            Thread.sleep(50)
        }
        assertTrue(logFile.exists(), "envelope log not written at ${logFile.absolutePath}")
        val lines = logFile.readLines()
        assertTrue(lines.isNotEmpty(), "envelope log is empty")
        val tail = lines.last()
        assertTrue(tail.contains("\"event_type\":\"drill_grade\""), "missing event_type: $tail")
        assertTrue(tail.contains("\"is_synthetic\":true"), "missing is_synthetic=true: $tail")
        assertTrue(tail.contains("\"rcode_sha256\":"), "missing rcode_sha256: $tail")
        assertTrue(!tail.contains("RAW_RCODE_MIDDLE_MARKER"),
            "raw rcode middle slice must NOT appear in envelope: $tail")
    }
}
