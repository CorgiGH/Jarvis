package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 3 (Plan 4b) — GET /api/v1/viz/{instanceId}.
 *
 * Contract (§0.9B, interface-signatures-lock §NEW-V):
 *  - 200 + exact fields { id, subject, family_id, language, data_json } for a known instance.
 *  - 404 unknown instance id.
 *  - 401 no session.
 *
 * The route is installed via [installVizInstanceRoutes] (new file VizInstanceRoutes.kt, Lane A).
 * This test does NOT depend on the mount patch in TutorRoutes.kt — it installs the route directly
 * on the test application. The mount patch (tutor-routes-viz-mount.patch) is for production wiring
 * via TutorRoutes.kt (PM applies at CP-1).
 */
class VizInstanceRouteTest {

    @AfterTest fun reset() { System.clearProperty("JARVIS_CONTENT_DIR") }

    private fun Application.installRoutes(db: org.jetbrains.exposed.sql.Database, dir: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        attributes.put(TutorContextKey, testTutorContext(db, dir, mailer = FakeMailer()))
        installVizInstanceRoutes()
    }

    private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.create(UsersTable, SessionsTable)
        }
    }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): Pair<String, String> {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "friend", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        return uid to sid
    }

    /**
     * Seed a minimal content directory with subjects.yaml + the real PA viz directory
     * (symlinked by pointing JARVIS_CONTENT_DIR at a dir that has the PA/viz/ layout).
     *
     * Rather than copying the real YAML (brittle), we create a minimal temp content dir
     * and a synthetic viz YAML whose fields we can assert byte-for-byte.
     */
    private fun seedVizContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val vizDir = content.resolve("PA").resolve("viz")
        vizDir.createDirectories()
        // Minimal synthetic instance — only the fields used by ApiVizInstanceReply.
        vizDir.resolve("viz-test-001.yaml").writeText(
            """
            id: viz-test-001
            subject: PA
            family_id: graph-tree
            language: ro
            instance:
              method_kc_id: pa-kc-fixture-test
              data_json: '{"nodes":[],"steps":[]}'
            """.trimIndent(),
        )
    }

    @Test
    fun `known instance id returns 200 with exact fields`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "viz-${TutorTypes.ulid()}")
        val content = dir.resolve("content")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedVizContent(content)
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }

        val resp = client.get("/api/v1/viz/viz-test-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        // Exact field presence per §0.9B — snake_case on the wire.
        assertTrue(body.contains("\"id\":\"viz-test-001\""), "id field: $body")
        assertTrue(body.contains("\"subject\":\"PA\""), "subject field: $body")
        assertTrue(body.contains("\"family_id\":\"graph-tree\""), "family_id field: $body")
        assertTrue(body.contains("\"language\":\"ro\""), "language field: $body")
        assertTrue(body.contains("\"data_json\""), "data_json field: $body")
        // data_json content verbatim from the YAML instance.
        assertTrue(body.contains("nodes"), "data_json content nodes: $body")
    }

    @Test
    fun `unknown instance id returns 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "viz-${TutorTypes.ulid()}")
        val content = dir.resolve("content")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedVizContent(content)
        val db = freshDb(dir)
        val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }

        val resp = client.get("/api/v1/viz/viz-does-not-exist") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test
    fun `no session returns 401`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "viz-${TutorTypes.ulid()}")
        val content = dir.resolve("content")
        System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedVizContent(content)
        val db = freshDb(dir)
        application { installRoutes(db, dir) }

        val resp = client.get("/api/v1/viz/viz-test-001")
        assertEquals(HttpStatusCode.Unauthorized, resp.status, resp.bodyAsText())
    }
}
