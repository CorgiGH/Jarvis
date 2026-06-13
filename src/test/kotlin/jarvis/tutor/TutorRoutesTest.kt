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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorRoutesTest {
    companion object {
        // The TutorEventLog.GLOBAL singleton's privateDir is frozen on first
        // access via `by lazy`. To let MULTIPLE @Test methods exercise it,
        // all event-log tests must point at the SAME dir. We allocate one
        // shared temp dir per class (static @TempDir = JUnit lifecycle PER
        // CLASS) and pin the system property in @BeforeAll BEFORE any test
        // runs. Each test then filters the shared JSONL file by its own
        // session_id (each testApplication creates a fresh session).
        @JvmStatic
        @TempDir
        lateinit var sharedEventLogDir: Path

        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            // TutorEventLog.GLOBAL freezes its dir to whatever this property
            // points at on FIRST access across the JVM lifetime. Another test
            // class in the same suite may touch GLOBAL (and thus freeze the
            // dir) before this @BeforeAll runs, so only set the property if it
            // is not already pinned, and read from the dir that actually got
            // frozen (System property), not unconditionally from our own
            // @TempDir field. This keeps the suite order-independent.
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                java.nio.file.Files.createDirectories(sharedEventLogDir)
                System.setProperty("jarvis.tutor.event_log.dir", sharedEventLogDir.toString())
            }
        }
    }

    // TutorEventLog.GLOBAL.append() is ASYNC: it enqueues onto a Channel that a
    // single background GlobalScope/Dispatchers.IO coroutine drains to disk. The
    // HTTP response returns before that drain completes, so the envelope for THIS
    // test's session may not be on disk yet when we read. Because the log is SHARED
    // across every test in this class (and frozen JVM-wide), the file is already
    // non-empty from prior tests — so a "wait until the file is non-empty" poll
    // exits immediately on stale lines and never waits for OUR session's line.
    // Under the full suite on CI (slower/contended IO) that surfaces as an
    // intermittent "no envelope for session_id=… in shared log" failure. Fix:
    // poll until a line carrying THIS test's session_id lands (or the deadline).
    private fun readSharedEventLogLines(sessionId: String? = null): List<String> {
        val today = java.time.LocalDate.now().toString()
        val dir = System.getProperty("jarvis.tutor.event_log.dir")
            ?.let { Path.of(it) } ?: sharedEventLogDir
        val logFile = dir.resolve("tutor_events.$today.jsonl").toFile()
        val needle = sessionId?.let { "\"session_id\":\"$it\"" }
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            val lines = if (logFile.exists()) logFile.readLines() else emptyList()
            val ready = if (needle != null) lines.any { it.contains(needle) } else lines.isNotEmpty()
            if (ready) return lines
            Thread.sleep(50)
        }
        return if (logFile.exists()) logFile.readLines() else emptyList()
    }

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
        // Seed AI-literacy confirmation so the gate passes for pre-gate tests.
        AiLiteracyRepo(ctx.db).confirm(userId, AI_LITERACY_VERSION, "ro")
        return userId to sid
    }

    @Test
    fun `POST drill grade writes a redacted TutorEvent`(@TempDir tmp: Path) = testApplication {
        // sharedEventLogDir is pinned in @BeforeAll — TutorEventLog.GLOBAL
        // is frozen the first time any of these tests calls into a route
        // that writes an envelope. We filter the shared log by session_id
        // so this test only inspects its own line.

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

        // Filter the shared log by this test's session_id.
        val mine = readSharedEventLogLines(sid).filter { it.contains("\"session_id\":\"$sid\"") }
        assertTrue(mine.isNotEmpty(), "no envelope for session_id=$sid in shared log")
        val tail = mine.last()
        assertTrue(tail.contains("\"event_type\":\"drill_grade\""), "missing event_type: $tail")
        assertTrue(tail.contains("\"is_synthetic\":true"), "missing is_synthetic=true: $tail")
        assertTrue(tail.contains("\"rcode_sha256\":"), "missing rcode_sha256: $tail")
        assertTrue(!tail.contains("RAW_RCODE_MIDDLE_MARKER"),
            "raw rcode middle slice must NOT appear in envelope: $tail")
    }

    // --- Student-stand-in Task 0.6 -----------------------------------------
    // The sidekick-ask endpoint must also append TutorEvent envelopes for the
    // two LLM-touching exit paths (drill-self-paste guard and LLM call). The
    // guardrail differs from drill-grade: sidekick stores the raw
    // selection+question in `llm_input_full` (no R-code redaction), and the
    // system_prompt_sha256 hashes the REAL prompt text returned by
    // SidekickContext.systemContext(), not just a template id.
    //
    // Both tests rely on the same temp-dir TutorEventLog wiring as Task 0.5.

    @Test
    fun `POST sidekick ask writes a synthetic-flagged TutorEvent`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-sidekick"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Selection long enough (≥12 chars) and non-paste to take the LLM
        // path. No OPENROUTER_API_KEY in test env → JarvisToolset() throws at
        // construction → handler falls through the catch arm with
        // model="(none)" and status="error". The envelope must still land.
        val body = """{
            "task_id":"task-1",
            "selection":"the Laplace distribution density",
            "user_question":"What is the Laplace distribution density formula?"
        }""".trimIndent()

        val resp = client.post("/api/v1/sidekick/ask") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            header("X-Standin-Run", "1")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val mine = readSharedEventLogLines(sid).filter { it.contains("\"session_id\":\"$sid\"") }
        assertTrue(mine.isNotEmpty(), "no envelope for session_id=$sid in shared log")
        val tail = mine.last()
        assertTrue(tail.contains("\"event_type\":\"sidekick_ask\""), "missing event_type: $tail")
        assertTrue(tail.contains("\"is_synthetic\":true"), "missing is_synthetic=true: $tail")
        assertTrue(tail.contains("\"prompt_template_id\":\"sidekick-v1\""),
            "missing prompt_template_id=sidekick-v1: $tail")
        // Unlike drill_grade, sidekick stores the RAW input — assert the
        // user's selection + question text round-trip into llm_input_full.
        assertTrue(tail.contains("Laplace"), "raw selection text should be present in llm_input_full: $tail")
        assertTrue(tail.contains("llm_input_full"), "missing llm_input_full field: $tail")
    }

    // 2026-05-17 hot-work #4: UI telemetry endpoint must append a
    // TutorEvent of type "ui_telemetry" carrying the event name in
    // prompt_template_id and the raw body in llm_output_full. Bad payloads
    // (malformed JSON, invalid name regex) return 400 without writing.
    @Test
    fun `POST sensor telemetry writes a ui_telemetry event`(@TempDir tmp: Path) = testApplication {
        application { installFreshTutor(tmp) }
        startApplication()
        val sidCookie = "telemetry-sess-${Instant.now().toEpochMilli()}"
        val csrf = "telemetry-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/sensor/telemetry") {
            cookie("jarvis_session", sidCookie)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"ledger.opened","ts":"2026-05-17T08:30:00Z"}""")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
        val mine = readSharedEventLogLines(sidCookie).filter { it.contains("\"session_id\":\"$sidCookie\"") }
        assertTrue(mine.isNotEmpty(), "no envelope for session_id=$sidCookie")
        val tail = mine.last()
        assertTrue(tail.contains("\"event_type\":\"ui_telemetry\""), "missing event_type: $tail")
        assertTrue(tail.contains("\"prompt_template_id\":\"ledger.opened\""), "missing name: $tail")
        assertTrue(tail.contains("ledger.opened"), "missing body echo: $tail")
    }

    @Test
    fun `POST sensor telemetry rejects bad name regex`() = testApplication {
        application { installTutorRoutes() }
        val csrf = "telemetry-csrf-bad"
        val resp = client.post("/api/v1/sensor/telemetry") {
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"BADNAME"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST sensor telemetry rejects malformed body`() = testApplication {
        application { installTutorRoutes() }
        val csrf = "telemetry-csrf-malformed"
        val resp = client.post("/api/v1/sensor/telemetry") {
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST sensor telemetry rejects request missing csrf`() = testApplication {
        application { installTutorRoutes() }
        val resp = client.post("/api/v1/sensor/telemetry") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"ledger.opened"}""")
        }
        // csrfProtect returns 403 when cookie/header pair is absent or mismatched.
        assertTrue(resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.Unauthorized,
            "expected 403 or 401 when CSRF missing, got ${resp.status}")
    }

    @Test
    fun `POST sidekick ask drill-self-paste path writes a guard envelope`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-sidekick-guard"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        // Selection ≈ drill_statement → Jaccard ≥ 0.7 → drillSelfPaste=true.
        // Handler short-circuits with model="(drill-self-paste-guard)". The
        // envelope must record status="guard" and the synthetic model token.
        val drill = "Generate 10000 samples from the Laplace distribution with location 0 and scale 1"
        val body = """{
            "task_id":"task-2",
            "selection":"Generate 10000 samples from the Laplace distribution with location 0 and scale 1",
            "user_question":"can you explain what to do?",
            "drill_statement":"$drill"
        }""".trimIndent()

        val resp = client.post("/api/v1/sidekick/ask") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val mine = readSharedEventLogLines(sid).filter { it.contains("\"session_id\":\"$sid\"") }
        assertTrue(mine.isNotEmpty(), "no envelope for session_id=$sid in shared log")
        val tail = mine.last()
        assertTrue(tail.contains("\"event_type\":\"sidekick_ask\""), "missing event_type: $tail")
        assertTrue(tail.contains("\"model_resolved\":\"(drill-self-paste-guard)\""),
            "missing drill-self-paste-guard model token: $tail")
        assertTrue(tail.contains("\"status\":\"guard\""), "missing status=guard: $tail")
        // No X-Standin-Run header → is_synthetic should default to false.
        assertTrue(tail.contains("\"is_synthetic\":false"),
            "expected is_synthetic=false when X-Standin-Run absent: $tail")
    }
}
