package jarvis.web

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContext
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CuratorRoutesTest {
    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n")
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
            "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
            "exam_weight: 1.0\ntier: 1\nversion: 1\n")
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
    }

    private fun Application.installFresh(tmp: Path, content: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
    }

    private fun seedOwner(ctx: TutorContext): String {
        val uid = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(uid, "owner", UserScope.OWNER, Instant.now(), Instant.now()))
        return SessionRepo(ctx.db).create(uid, ttlSeconds = 3600)
    }

    private fun seedFriend(ctx: TutorContext): String {
        val uid = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(uid, "friend", UserScope.FRIEND, Instant.now(), Instant.now()))
        return SessionRepo(ctx.db).create(uid, ttlSeconds = 3600)
    }

    @Test
    fun `GET curator subjects returns the manifest for an OWNER`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content")
        seedContent(content)
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedOwner(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.get("/api/v1/curator/subjects") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("Algorithm Design"))
    }

    @Test
    fun `GET curator subjects rejects an unauthenticated caller with 401`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        application { installFresh(tmp, content) }
        startApplication()
        val client = createClient { install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val r = client.get("/api/v1/curator/subjects")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `GET curator subjects rejects a FRIEND-scope user with 403`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedFriend(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r = client.get("/api/v1/curator/subjects") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `GET curator kcs lists and fetches a single KC`(@TempDir tmp: Path) = testApplication {
        val content = tmp.resolve("content"); seedContent(content)
        var ctx: TutorContext? = null
        application { installFresh(tmp, content); ctx = attributes[TutorContextKey] }
        startApplication()
        val sid = seedOwner(ctx!!)
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val list = client.get("/api/v1/curator/subjects/PA/kcs") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("pa-kc-001"))
        val one = client.get("/api/v1/curator/subjects/PA/kcs/pa-kc-001") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, one.status)
        assertTrue(one.bodyAsText().contains("\"tier\":1"))
        val missing = client.get("/api/v1/curator/subjects/PA/kcs/pa-kc-999") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }
}
