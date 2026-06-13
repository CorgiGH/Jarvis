package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import jarvis.tutor.ProblemKcLinksTable
import jarvis.tutor.ProblemRubricItemsTable
import jarvis.tutor.ProblemSeed
import jarvis.tutor.ProblemsTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-6 Task 8 — route tests for the five practice endpoint groups (§0.9-G).
 *
 * Matrix:
 *  - 401 without a session on every endpoint.
 *  - problems list returns the seeded PA problem AND its JSON body contains NO reference-solution text
 *    (the INV-6.6 server half — server-enforced, NOT CSS).
 *  - proof grade returns per-substep item_verdicts.
 *  - trace step grade catches a wrong step-3 value at step 3 (REQ-5 pin).
 *  - code run returns bounded outputs (honest degraded copy when no runner).
 *  - code grade reply carries reference_solution_ro.
 *  - deliverables endpoint returns the honest empty list (Task-8 stub).
 */
class PracticeRoutesTest {

    private fun makeDb(): Pair<Database, Path> {
        val dbDir = Files.createTempDirectory("practice-routes-test")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(UsersTable, SessionsTable)
            SchemaUtils.createMissingTablesAndColumns(
                ProblemsTable, ProblemRubricItemsTable, ProblemKcLinksTable,
            )
        }
        // Seed the real-corpus problems (PA proof / ALO trace / PS code).
        ProblemSeed.seed(db, Path.of("content"))
        return db to dbDir
    }

    private fun makeUser(db: Database): String {
        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "u", UserScope.FRIEND, Instant.now(), Instant.now()))
        return SessionRepo(db).create(uid, 3600)
    }

    // ── auth ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `every endpoint 401 without a session`() = testApplication {
        val (db, dbDir) = makeDb()
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/practice/problems?subject=PA").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/practice/deliverables").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/api/v1/practice/proof/pa-prob-001/grade") {
                contentType(ContentType.Application.Json); setBody("""{"substeps":[]}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/api/v1/practice/trace/alo-prob-001/step") {
                contentType(ContentType.Application.Json); setBody("""{"step_index":0,"value":"-3"}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/api/v1/practice/code/ps-prob-001/run") {
                contentType(ContentType.Application.Json); setBody("""{"source":""}""")
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/api/v1/practice/code/ps-prob-001/grade") {
                contentType(ContentType.Application.Json); setBody("""{"source":""}""")
            }.status,
        )
    }

    // ── problems list + INV-6.6 server half (no reference leak) ──────────────────────────

    @Test
    fun `problems list returns seeded PA proof problem and never leaks the reference solution`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        val r = client.get("/api/v1/practice/problems?subject=PA&surface=proof") {
            header("Cookie", "jarvis_session=$sid")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("pa-prob-001"), "expected the seeded PA problem id; body: $body")
        assertTrue(body.contains("HAM-PATH"), "expected the statement text; body: $body")
        // INV-6.6 SERVER HALF: the reference solution's distinctive substring MUST NOT appear.
        // The PA reference_solution contains "Ghicim o permutare" / the construction prose.
        assertFalse(
            body.contains("Ghicim o permutare"),
            "INV-6.6 VIOLATION: the problems list leaked the reference solution text. body: $body",
        )
        assertFalse(
            body.contains("Reducerea este O"),
            "INV-6.6 VIOLATION: the problems list leaked reference prose. body: $body",
        )
    }

    @Test
    fun `problems list filters by surface`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        // PA problem is a proof — requesting surface=trace for PA returns an empty problem list.
        val r = client.get("/api/v1/practice/problems?subject=PA&surface=trace") {
            header("Cookie", "jarvis_session=$sid")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertFalse(r.bodyAsText().contains("pa-prob-001"), "PA proof must not appear under surface=trace")
    }

    // ── proof grade — per-substep verdicts (REQ-2) ───────────────────────────────────────

    @Test
    fun `proof grade returns per-substep item_verdicts`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        // pa-prob-001 substep step-np-guess matcher = contains "permutare"; step-backward = contains "ciclu".
        val r = client.post("/api/v1/practice/proof/pa-prob-001/grade") {
            header("Cookie", "jarvis_session=$sid; csrf=tok")
            header("X-CSRF-Token", "tok")
            contentType(ContentType.Application.Json)
            setBody(
                """{"substeps":[
                    {"id":"step-np-guess","text":"ghicim o permutare a nodurilor"},
                    {"id":"step-backward","text":"un ciclu hamiltonian"}
                ]}""",
            )
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("item_verdicts"), "expected per-substep verdicts; body: $body")
        assertTrue(body.contains("step-np-guess"), "expected the named substep id; body: $body")
        assertTrue(body.contains(""""decided_by":"rubric""""), "proof decides via rubric; body: $body")
        // step-np-guess matched ("permutare"); step-construction etc. unanswered → not all passed.
        assertTrue(body.contains(""""passed":true"""), "the matched substep must pass; body: $body")
    }

    // ── trace step — wrong value at step 3 caught at step 3 (REQ-5) ───────────────────────

    @Test
    fun `trace step grade catches a wrong value at step 3 at step 3`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        // alo-prob-001 step index 2 (the 3rd step) expected = "0.875".
        val wrong = client.post("/api/v1/practice/trace/alo-prob-001/step") {
            header("Cookie", "jarvis_session=$sid; csrf=tok")
            header("X-CSRF-Token", "tok")
            contentType(ContentType.Application.Json)
            setBody("""{"step_index":2,"value":"0.9"}""")
        }
        assertEquals(HttpStatusCode.OK, wrong.status)
        val wb = wrong.bodyAsText()
        assertTrue(wb.contains(""""passed":false"""), "wrong value at step 3 must fail AT step 3; body: $wb")
        assertTrue(wb.contains("\"verdict\""), "expected a verdict object; body: $wb")

        // Correct value at the same step passes.
        val right = client.post("/api/v1/practice/trace/alo-prob-001/step") {
            header("Cookie", "jarvis_session=$sid; csrf=tok")
            header("X-CSRF-Token", "tok")
            contentType(ContentType.Application.Json)
            setBody("""{"step_index":2,"value":"0.875"}""")
        }
        assertEquals(HttpStatusCode.OK, right.status)
        assertTrue(right.bodyAsText().contains(""""passed":true"""), "correct value must pass; body: ${right.bodyAsText()}")
    }

    // ── code run — bounded outputs (honest degraded when no runner) ───────────────────────

    @Test
    fun `code run returns a structured reply`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        // ps-prob-001 exam_language=r. If Rscript is unavailable the reply degrades honestly; either
        // way the reply has the bounded-output fields.
        val r = client.post("/api/v1/practice/code/ps-prob-001/run") {
            header("Cookie", "jarvis_session=$sid; csrf=tok")
            header("X-CSRF-Token", "tok")
            contentType(ContentType.Application.Json)
            setBody("""{"source":"cat('hi')"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("compiled"), "expected the compiled field; body: $body")
        assertTrue(body.contains("stdout_trunc"), "expected stdout_trunc; body: $body")
        assertTrue(body.contains("timed_out"), "expected timed_out; body: $body")
        assertTrue(body.contains("degraded_legs_ro"), "expected degraded_legs_ro; body: $body")
    }

    // ── code grade — the ONLY endpoint that serves the reference ──────────────────────────

    @Test
    fun `code grade reply carries reference_solution_ro`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        val r = client.post("/api/v1/practice/code/ps-prob-001/grade") {
            header("Cookie", "jarvis_session=$sid; csrf=tok")
            header("X-CSRF-Token", "tok")
            contentType(ContentType.Application.Json)
            setBody("""{"source":"interval_z = function(n, sample_mean, sigma, alfa) {}"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("reference_solution_ro"), "code GRADE must carry the reference; body: $body")
        // The PS reference source distinctive substring.
        assertTrue(body.contains("interval_z = function"), "the reference must be served on the grade reply; body: $body")
        assertTrue(body.contains("decided_by"), "expected decided_by audit field; body: $body")
    }

    // ── deliverables — Task-8 honest empty stub ───────────────────────────────────────────

    @Test
    fun `deliverables endpoint returns the honest empty list`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        val r = client.get("/api/v1/practice/deliverables") {
            header("Cookie", "jarvis_session=$sid")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains(""""deliverables":[]"""), "Task-8 stub returns empty; body: ${r.bodyAsText()}")
    }

    // ── csrf on a write endpoint ──────────────────────────────────────────────────────────

    @Test
    fun `proof grade without csrf returns 403`() = testApplication {
        val (db, dbDir) = makeDb()
        val sid = makeUser(db)
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installPracticeRoutes()
        }
        val r = client.post("/api/v1/practice/proof/pa-prob-001/grade") {
            header("Cookie", "jarvis_session=$sid")  // session, NO csrf
            contentType(ContentType.Application.Json)
            setBody("""{"substeps":[]}""")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }
}
