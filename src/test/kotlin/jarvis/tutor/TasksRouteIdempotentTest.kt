package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
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

class TasksRouteIdempotentTest {

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
    fun `second POST with same subject+title returns 200 with existing id`(@TempDir tmp: Path) =
        testApplication {
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

            val payload = """
                {"subject":"PA","title":"Tema 5","deadline":"2026-05-21T00:00:00Z",
                 "repo":"user","problemPath":"tema5.pdf","rubricPath":""}
            """.trimIndent()

            val r1 = client.post("/api/v1/tasks") {
                cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
                contentType(ContentType.Application.Json); setBody(payload)
            }
            assertEquals(HttpStatusCode.Created, r1.status, "first POST must create: ${r1.bodyAsText()}")
            val firstBody = r1.bodyAsText()
            val firstId = Regex("\"id\":\"([^\"]+)\"").find(firstBody)?.groupValues?.get(1)
                ?: error("no id in first response: $firstBody")

            val r2 = client.post("/api/v1/tasks") {
                cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
                contentType(ContentType.Application.Json); setBody(payload)
            }
            assertEquals(HttpStatusCode.OK, r2.status, "second POST must dedup: ${r2.bodyAsText()}")
            assertTrue(
                r2.bodyAsText().contains("\"id\":\"$firstId\""),
                "second POST must echo first task's id; got: ${r2.bodyAsText()}",
            )
        }

    @Test
    fun `different subjects same title remain distinct`(@TempDir tmp: Path) = testApplication {
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

        val pa = """
            {"subject":"PA","title":"Tema 5","deadline":"2026-05-21T00:00:00Z",
             "repo":"user","problemPath":"pa.pdf","rubricPath":""}
        """.trimIndent()
        val ps = """
            {"subject":"PS","title":"Tema 5","deadline":"2026-05-21T00:00:00Z",
             "repo":"user","problemPath":"ps.pdf","rubricPath":""}
        """.trimIndent()

        val r1 = client.post("/api/v1/tasks") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(pa)
        }
        val r2 = client.post("/api/v1/tasks") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(ps)
        }

        assertEquals(HttpStatusCode.Created, r1.status)
        assertEquals(HttpStatusCode.Created, r2.status, "different subject must NOT dedup: ${r2.bodyAsText()}")
    }
}
