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
import jarvis.tutor.AttemptsTable
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.KcMasteryTable
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import jarvis.tutor.VerificationAuditTable
import jarvis.tutor.VerificationStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
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
 * Grounded-teaching serve (Task 7) — GET /api/v1/teaching/{kcId}.
 *
 * Class-killers:
 *  - faithful KC ⇒ 200 with explanation_ro + worked_example_ro served verbatim;
 *  - non-faithful (e.g. uncertain / no B8 row) ⇒ 404 (OMIT, never a degraded payload);
 *  - faithful KC with an OPEN report_wrong ⇒ 404 (D-RF2 always-on serve refusal);
 *  - unknown kcId ⇒ 404; no session ⇒ 401.
 */
class TeachingServeRouteTest {

    @AfterTest fun reset() { System.clearProperty("JARVIS_CONTENT_DIR") }

    private fun Application.installRoutes(db: org.jetbrains.exposed.sql.Database, dir: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        attributes.put(TutorContextKey, testTutorContext(db, dir, mailer = FakeMailer()))
        installTutorRoutes()
    }

    private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable, FsrsCardsTable, KcMasteryTable,
                AttemptsTable, ReportWrongTable, KcVerificationStatusTable, VerificationAuditTable,
            )
        }
    }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): Pair<String, String> {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "friend", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)
        return uid to sid
    }

    /** PA subject with pa-kc-001 authored WITH a grounded explanation + worked example, span-anchored
     *  on the real pa-lecture-01 quote so the content hash is computable + emits the two prose claims. */
    private fun seedContent(content: Path) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            """
            id: pa-kc-001
            subject: PA
            name_ro: "Noțiunea de algoritm"
            name_en: "The notion of an algorithm"
            cluster: "Fundamentele algoritmilor"
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.22
            tier: 1
            source:
              - doc: pa-lecture-01
                quote: "An algorithm is a well-ordered collection of unambiguous and effectively computable\noperations that when executed produces a result and halts in a finite amount of time."
                page: 4
                span:
                  start: 1184
                  end: 1353
                provenance: located
            version: 1
            explanation_ro: "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile, care produc un rezultat și se opresc în timp finit."
            worked_example_ro: "Exemplu: pașii adunării a două numere sunt neambigui, se execută efectiv și se opresc în timp finit, deci formează un algoritm."
            """.trimIndent(),
        )
    }

    /** Stamp a runtime B8 status. For faithful, supply the matching content_hash + a non-null
     *  source_span_hash so resolveStatus's D8 + D1 gate passes (mirrors QueueMastery test seedB8). */
    private fun seedB8(db: org.jetbrains.exposed.sql.Database, content: Path, kcId: String, status: VerificationStatus) =
        transaction(db) {
            val kc = jarvis.content.ContentRepo(content).loadSubject("PA").kcs.single { it.id == kcId }
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[KcVerificationStatusTable.status] = status.name
                it[contentHash] = if (status == VerificationStatus.faithful)
                    jarvis.content.ContentReconcile.kcContentHash(kc) else null
                it[sourceSpanHash] = if (status == VerificationStatus.faithful) "seedspanhash" else null
                it[updatedAt] = Instant.now()
            }
        }

    private fun openReport(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String) = transaction(db) {
        ReportWrongTable.insert {
            it[id] = TutorTypes.ulid()
            it[ReportWrongTable.userId] = userId
            it[ReportWrongTable.kcId] = kcId
            it[cardId] = null
            it[gradeAttemptRaw] = "looks wrong"
            it[reportedAt] = Instant.now()
            it[resolution] = "OPEN"
        }
    }

    @Test fun `faithful KC serves the grounded teaching verbatim`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/teaching/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        assertTrue(body.contains("Un algoritm este o colecție bine ordonată"), body)
        assertTrue(body.contains("pașii adunării a două numere"), body)
        assertTrue(body.contains("\"hasBeenFaithfulChecked\":true"), body)
        assertTrue(body.contains("\"type\":\"authored\""), body)
    }

    @Test fun `non-faithful KC is omitted with 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/teaching/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `faithful KC with an OPEN dispute is refused with 404 (D-RF2)`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (uid, sid) = seedUser(db)
        seedB8(db, content, "pa-kc-001", VerificationStatus.faithful); openReport(db, uid, "pa-kc-001")
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/teaching/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `unknown kcId is 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/teaching/pa-kc-999") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `no session is 401`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/teaching/pa-kc-001")
        assertEquals(HttpStatusCode.Unauthorized, resp.status, resp.bodyAsText())
    }

    /** Regression: a content dir with NO subjects.yaml (malformed manifest) must degrade to 404,
     *  never 500.  VerifyAdmin.findKc calls repo.loadManifest() bare; wrapping it in runCatching
     *  on the teaching route prevents the IllegalStateException from leaking as a 500. */
    @Test fun `missing subjects yaml degrades to 404 not 500`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
        // Point JARVIS_CONTENT_DIR at an EMPTY directory — no subjects.yaml present.
        val emptyContent = dir.resolve("empty-content")
        emptyContent.createDirectories()
        System.setProperty("JARVIS_CONTENT_DIR", emptyContent.toString())
        val db = freshDb(dir); val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/teaching/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(
            HttpStatusCode.NotFound, resp.status,
            "absent subjects.yaml must degrade to 404 (OMIT), not 500 — got: ${resp.bodyAsText()}",
        )
    }
}
