package jarvis.tutor

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

class GrantsRouteTest {

    private fun Application.installTutorWithCN(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seedSession(ctx: TutorContext): Triple<String, String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        val csrf = "grants-test-csrf"
        return Triple(userId, sid, csrf)
    }

    @Test
    fun postGrantsCreatesRowAndGetReturnsIt(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installTutorWithCN(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)

        val client = createClient { install(HttpCookies) }

        val create = client.post("/api/v1/grants") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"scope":["file:///c/work/**"],"ops":["APPLY_EDIT"],"ttlSeconds":600,"maxCalls":5}""")
        }
        assertEquals(HttpStatusCode.Created, create.status, "create body=${create.bodyAsText()}")
        val createBody = create.bodyAsText()
        assertTrue(createBody.contains("\"scope\":[\"file:///c/work/**\"]"))

        val list = client.get("/api/v1/grants") {
            cookie("jarvis_session", sid); cookie("csrf", csrf)
        }
        assertEquals(HttpStatusCode.OK, list.status)
        val listBody = list.bodyAsText()
        assertTrue(listBody.contains("file:///c/work/**"), "list body=$listBody")
    }

    @Test
    fun rateLimitsAfter5GrantsPerHour(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installTutorWithCN(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)
        val client = createClient { install(HttpCookies) }
        val payload = """{"scope":["file:///x"],"ops":["APPLY_EDIT"],"ttlSeconds":600,"maxCalls":1}"""
        // First 5 succeed.
        repeat(5) {
            val r = client.post("/api/v1/grants") {
                cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
                contentType(ContentType.Application.Json); setBody(payload)
            }
            assertEquals(HttpStatusCode.Created, r.status, "create #$it should succeed")
        }
        // 6th rejected.
        val r6 = client.post("/api/v1/grants") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(payload)
        }
        assertEquals(HttpStatusCode.TooManyRequests, r6.status)
    }

    @Test
    fun revokeMarksGrantInactive(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installTutorWithCN(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid, csrf) = seedSession(ctx!!)
        // Create directly via repo (faster setup).
        val gid = TutorTypes.ulid()
        TrustGrantRepo(ctx!!.db).insert(TrustGrant(
            grantId = gid, userId = userId,
            scope = listOf("**"), ops = setOf(EffectorType.APPLY_EDIT),
            expiresAt = Instant.now().plusSeconds(3600),
            maxCalls = 3, callsUsed = 0,
            grantedFrom = GrantSource.UI, revokedAt = null,
            createdAt = Instant.now(),
        ))
        val client = createClient { install(HttpCookies) }
        val r = client.post("/api/v1/grants/$gid/revoke") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
        }
        assertEquals(HttpStatusCode.NoContent, r.status)
        // Repo no longer finds it as active.
        assertEquals(null, TrustGrantRepo(ctx!!.db).findActive(gid, Instant.now()))
    }

    @Test
    fun ttlClampedToEightHours(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installTutorWithCN(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid, csrf) = seedSession(ctx!!)
        val client = createClient { install(HttpCookies) }
        // Request 30 days; server caps at 8h.
        val r = client.post("/api/v1/grants") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"scope":["**"],"ops":["APPLY_EDIT"],"ttlSeconds":2592000,"maxCalls":10}""")
        }
        assertEquals(HttpStatusCode.Created, r.status)
        // expiresAt minus now should be roughly 8h, not 30d.
        val body = r.bodyAsText()
        val expIso = Regex("""\"expiresAt\":\"([^\"]+)\"""").find(body)!!.groupValues[1]
        val sec = java.time.Duration.between(Instant.now(), Instant.parse(expIso)).seconds
        assertTrue(sec in 7 * 3600..8 * 3600 + 60, "ttl clamped to 8h; got ${sec}s")
    }

    @Test
    fun rejectsMissingSession(@TempDir tmp: Path) = testApplication {
        application { installTutorWithCN(tmp) }
        startApplication()
        val r = client.get("/api/v1/grants")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
