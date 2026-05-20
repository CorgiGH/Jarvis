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
import jarvis.VisionLlm
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScreenshotSensorRouteTest {

    private val tinyPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

    private class StubVisionLlm(private val reply: String, val capturedPrompts: MutableList<String> = mutableListOf()) : VisionLlm {
        override suspend fun analyze(
            prompt: String, imageBase64: String, mediaType: String,
            maxTokens: Int, model: String?,
        ): String {
            capturedPrompts.add(prompt)
            return reply
        }
    }

    private fun Application.installTutorWithVision(tmp: Path, vision: VisionLlm?) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        // Replace the auto-resolved (env-based) vision LLM with our stub.
        val ctx = attributes[TutorContextKey]
        attributes.put(TutorContextKey, ctx.copy(visionLlm = vision))
        installTutorRoutes()
    }

    private suspend fun seedSession(
        ctx: TutorContext,
    ): Triple<String, String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val raw = TokenRepo(ctx.db).issue(userId, "primary")
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        // Seed AI-literacy confirmation so the gate passes for pre-gate tests.
        AiLiteracyRepo(ctx.db).confirm(userId, AI_LITERACY_VERSION, "ro")
        // Synthesize a CSRF cookie value the test will mirror in headers.
        val csrf = "test-csrf-12345"
        return Triple(userId, sid, csrf)
    }

    @Test
    fun returnsExtractionAndPersistsSensorEvent(@TempDir tmp: Path) = testApplication {
        val stub = StubVisionLlm(
            """{"file_path":"src/foo.kt","cursor":{"line":10,"col":3},"console_output":"compiled","error":null}""",
        )
        var ctx: TutorContext? = null
        application {
            installTutorWithVision(tmp, stub)
            ctx = attributes[TutorContextKey]
        }
        startApplication()

        val (userId, sid, csrf) = seedSession(ctx!!)

        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val r = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf)
            header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng","mediaType":"image/png","taskId":"T-001"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, "body=${r.bodyAsText()}")
        val body = r.bodyAsText()
        assertTrue(body.contains("\"filePath\":\"src/foo.kt\""), "extracted file path: $body")
        assertTrue(body.contains("\"line\":10"))
        assertTrue(body.contains("\"eventSeq\":1"))
        // Source classifier verdict on a .kt path = ALLOWED.
        assertTrue(body.contains("\"readOnlyMode\":false"), "kotlin file → ALLOWED: $body")

        // Vision LLM was actually invoked with the extraction prompt.
        assertEquals(1, stub.capturedPrompts.size)
        assertTrue(stub.capturedPrompts[0].contains("file_path"))
    }

    @Test
    fun setsReadOnlyModeWhenSourceIsBrowserTab(@TempDir tmp: Path) = testApplication {
        val stub = StubVisionLlm(
            """{"file_path":"https://stackoverflow.com/q/123","cursor":null,"console_output":null,"error":null}""",
        )
        var ctx: TutorContext? = null
        application {
            installTutorWithVision(tmp, stub)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)
        val r = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("\"readOnlyMode\":true"), "stackoverflow URL → READ_ONLY: $body")
        assertTrue(body.contains("stackoverflow"), "reason includes host: $body")
    }

    @Test
    fun rejects403OnMissingCsrf(@TempDir tmp: Path) = testApplication {
        application { installTutorWithVision(tmp, StubVisionLlm("{}")) }
        startApplication()
        val r = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", "any-sid")
            // intentionally no csrf cookie / header
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun rejects401OnMissingSession(@TempDir tmp: Path) = testApplication {
        application { installTutorWithVision(tmp, StubVisionLlm("{}")) }
        startApplication()
        val r = client.post("/api/v1/sensor/screenshot") {
            // CSRF cookie + header match (passes csrfProtect) but no session.
            cookie("csrf", "x")
            header("X-CSRF-Token", "x")
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun returns503WhenVisionLlmAbsent(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installTutorWithVision(tmp, vision = null)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)
        val r = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf)
            header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertTrue(r.bodyAsText().contains("OPENROUTER_API_KEY"))
    }

    @Test
    fun rejectsBlankImageBase64(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installTutorWithVision(tmp, StubVisionLlm("{}"))
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)
        val r = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf)
            header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun rejectsOversizedImageBase64(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installTutorWithVision(tmp, StubVisionLlm("{}"))
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)
        val huge = "A".repeat(4_000_001)
        val r = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf)
            header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$huge"}""")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
    }

    @Test
    fun returns502OnVisionLlmFailure(@TempDir tmp: Path) = testApplication {
        val failing = object : VisionLlm {
            override suspend fun analyze(
                prompt: String, imageBase64: String, mediaType: String,
                maxTokens: Int, model: String?,
            ): String = error("upstream 503")
        }
        var ctx: TutorContext? = null
        application {
            installTutorWithVision(tmp, failing)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)
        val r = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf)
            header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng"}""")
        }
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertTrue(r.bodyAsText().contains("upstream 503"), "body should carry upstream error: ${r.bodyAsText()}")
    }
}
