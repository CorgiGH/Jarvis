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

class GapsRouteTest {
    private fun Application.freshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seed(ctx: TutorContext): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        return userId to sid
    }

    @Test
    fun `POST gap returns 201 + GET gaps lists it for taskId`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seed(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r1 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"topic":"closures","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"explain","taskId":"T1"}""")
        }
        assertEquals(HttpStatusCode.Created, r1.status, r1.bodyAsText())
        val r2 = client.get("/api/v1/gaps?taskId=T1") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r2.status)
        assertTrue(r2.bodyAsText().contains("\"topic\":\"closures\""))
    }

    @Test
    fun `POST gap twice with same triple returns same id (reusedCount bumps)`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seed(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val payload = """{"topic":"laplace","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"x","taskId":"T2"}"""
        val r1 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(payload)
        }
        val r2 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(payload)
        }
        val id1 = Regex("\"id\":\"([^\"]+)\"").find(r1.bodyAsText())?.groupValues?.get(1)
        val id2 = Regex("\"id\":\"([^\"]+)\"").find(r2.bodyAsText())?.groupValues?.get(1)
        assertEquals(id1, id2)
        assertTrue(r2.bodyAsText().contains("\"reusedCount\":1"))
    }

    @Test
    fun `POST gap status with GapResolved value updates resolvedBy`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seed(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r1 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"topic":"derivative","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"x","taskId":"T3"}""")
        }
        val gapId = Regex("\"id\":\"([^\"]+)\"").find(r1.bodyAsText())!!.groupValues[1]
        val r2 = client.post("/api/v1/gap/$gapId/status") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"status":"USER_MARKED_DONE"}""")
        }
        assertEquals(HttpStatusCode.OK, r2.status)
        val r3 = client.get("/api/v1/gaps?taskId=T3") { cookie("jarvis_session", sid) }
        assertTrue(r3.bodyAsText().contains("\"resolvedBy\":\"USER_MARKED_DONE\""))
    }
}
