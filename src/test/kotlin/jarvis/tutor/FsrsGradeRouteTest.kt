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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FsrsGradeRouteTest {
    private fun Application.freshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seed(ctx: TutorContext): Triple<String, String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        val cardId = TutorTypes.ulid()
        val now = Instant.now()
        FsrsCardRepo(ctx.db).insert(
            TutorCard(
                id = cardId, userId = userId,
                source = FsrsSource.MANUAL, sourceRef = "PA:test.md",
                front = "Q?", back = "A.",
                state = FsrsState(
                    difficulty = 5.0, stability = 0.5, retrievability = 1.0,
                    dueAt = now, lastReviewedAt = now, lapses = 0,
                ),
            ),
        )
        return Triple(userId, sid, cardId)
    }

    @Test
    fun `POST grade on freshly seeded MANUAL card returns 200 and advances state`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid, cardId) = seed(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.post("/api/v1/fsrs/$cardId/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"grade":3}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val body = r.bodyAsText()
        assertTrue(body.contains("\"cardId\":\"$cardId\""), "cardId missing: $body")
        assertTrue(body.contains("\"nextDueAt\""), "nextDueAt missing: $body")
    }

    @Test
    fun `POST grade on missing card returns 404 not 500`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seed(ctx!!).let { Pair(it.first, it.second) }
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.post("/api/v1/fsrs/NO_SUCH_CARD/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"grade":3}""")
        }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
    }

    @Test
    fun `FsrsCardRepo findById returns null for foreign-user card`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, _, cardId) = seed(ctx!!)
        val repo = FsrsCardRepo(ctx!!.db)
        assertNotNull(repo.findById(cardId, userId))
        // Different user should NOT see the card.
        val otherUserId = TutorTypes.ulid()
        UserRepo(ctx!!.db).insert(User(otherUserId, "o", UserScope.OWNER, Instant.now(), Instant.now()))
        assertEquals(null, repo.findById(cardId, otherUserId))
    }
}
