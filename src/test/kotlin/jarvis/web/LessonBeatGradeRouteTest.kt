package jarvis.web

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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan-3 Task 4 — POST /api/v1/lesson/{kcId}/beat. Server-side grading + attempt/completion writes.
 *
 * Class-killers:
 *  - every graded beat (predict/attempt/check) writes ONE attempts row with the right beat_type;
 *  - predict stores prediction_text on the row;
 *  - check flips mastery (recordIn → kc_mastery row appears w/ observations 1) + seeds the FSRS card;
 *  - first_encounter is true exactly once across two lesson runs;
 *  - a wrong choice grades incorrect with the served wrong-path feedback;
 *  - numeric tolerance pass/fail;
 *  - non-faithful ⇒ 404; incomplete beats ⇒ 404; bad beat_type ⇒ 400.
 */
class LessonBeatGradeRouteTest {

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

    /** definition-taxonomy KC with complete choice-variant beats (option[0] correct in every choice set). */
    private fun seedChoiceKc(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/$kcId.yaml").writeText(
            """
            id: $kcId
            subject: PA
            name_ro: "Noțiunea de algoritm"
            name_en: "The notion of an algorithm"
            cluster: "Fundamentele algoritmilor"
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.22
            tier: 1
            version: 1
            concept_type: definition-taxonomy
            stem_template: "Este X un algoritm?"
            beats:
              ro:
                predict:
                  prompt: "Care este un algoritm?"
                  options:
                    - text: "O rețetă clară"
                      callback: "Corect — pași neambigui, finiți."
                      correct: true
                    - text: "„Fii fericit”"
                      callback: "Nu — nu este efectiv calculabilă."
                      correct: false
                    - text: "Buclă infinită"
                      callback: "Nu — nu se termină."
                      correct: false
                attempt:
                  statement: "Clasifică exemplul."
                  feedback_correct: "Da — îndeplinește toate proprietățile."
                  choices:
                    - text: "Este un algoritm"
                      correct: true
                      feedback: "Corect: neambiguu, finit."
                    - text: "Nu este un algoritm"
                      correct: false
                      feedback: "Greșit — reanalizează proprietățile."
                reveal:
                  steps:
                    - text: "Operații neambigue."
                      callout: "Un singur înțeles per pas."
                name:
                  definition: "Un algoritm este o colecție bine ordonată de operații."
                  invariant_statement: "Se termină în timp finit."
                  why_matters: "Distinge un algoritm de o procedură infinită."
                check:
                  item_stem: "Este „adună două numere” un algoritm?"
                  choices:
                    - text: "Da"
                      correct: true
                      feedback: "Corect."
                    - text: "Nu"
                      correct: false
                      feedback: "Greșit."
            """.trimIndent(),
        )
    }

    /** formula-application KC with complete numeric-variant beats (skeleton + trace + numeric check). */
    private fun seedNumericKc(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/$kcId.yaml").writeText(
            """
            id: $kcId
            subject: PA
            name_ro: "Costul de timp"
            name_en: "Time cost"
            cluster: "Eficiența algoritmilor"
            bloom_level: apply
            difficulty: 3
            time_minutes: 30
            exam_weight: 0.13
            tier: 3
            version: 1
            concept_type: formula-application
            beats:
              ro:
                predict:
                  prompt: "Care este costul total?"
                  options:
                    - text: "3*t"
                      callback: "Corect — trei operații de cost t."
                      correct: true
                    - text: "t"
                      callback: "Nu — sunt trei operații."
                      correct: false
                    - text: "0"
                      callback: "Nu — fiecare operație costă t."
                      correct: false
                attempt:
                  statement: "Calculează costul însumând rândurile."
                  feedback_correct: "Corect — 3*t."
                  input_schema: "{\"type\":\"number\"}"
                  skeleton_rows:
                    - label: "op 1"
                      formula: "t"
                      is_decision_row: false
                    - label: "op 2"
                      formula: "t"
                      is_decision_row: false
                    - label: "total"
                      formula: "3*t"
                      is_decision_row: true
                  trace_steps:
                    - row_index: 0
                      value: "t"
                      callout: "Prima operație."
                    - row_index: 2
                      value: "3*t"
                      callout: "Suma."
                reveal:
                  steps:
                    - text: "Fiecare operație costă t."
                      callout: "Trei operații → 3*t."
                name:
                  definition: "Costul de timp este suma costurilor operațiilor."
                  invariant_statement: "t + t + t = 3*t."
                  why_matters: "Modelează eficiența."
                check:
                  item_stem: "Dacă t=2, care este costul total?"
                  numeric_answer: "6"
                  numeric_tolerance: 0.01
            """.trimIndent(),
        )
    }

    private fun attemptsFor(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String) =
        transaction(db) {
            AttemptsTable.selectAll()
                .where { (AttemptsTable.userId eq userId) and (AttemptsTable.kcId eq kcId) }
                .map { row ->
                    Triple(
                        row[AttemptsTable.beatType],
                        row[AttemptsTable.prediction],
                        row[AttemptsTable.firstEncounter],
                    )
                }
        }

    @Test fun `predict beat writes a row with beat_type predict and the prediction text`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"predict","selected_index":0,"prediction_text":"O rețetă clară"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertTrue(r.bodyAsText().contains("\"correct\":true"), r.bodyAsText())
        val rows = attemptsFor(db, uid, "pa-kc-001")
        assertEquals(1, rows.size)
        assertEquals("predict", rows.single().first)
        assertEquals("O rețetă clară", rows.single().second, "prediction text stored on the row")
    }

    @Test fun `attempt beat writes a row with beat_type attempt`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"attempt","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertTrue(r.bodyAsText().contains("\"correct\":true"), r.bodyAsText())
        assertEquals("attempt", attemptsFor(db, uid, "pa-kc-001").single().first)
    }

    @Test fun `check beat flips mastery, seeds the FSRS card, and completes the lesson`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val body = r.bodyAsText()
        assertTrue(body.contains("\"lesson_complete\":true"), body)
        assertTrue(body.contains("\"verification_status\":\"faithful\""), body)
        assertTrue(body.contains("\"phase\":"), body)
        // recordIn ran: a kc_mastery row exists with observations 1.
        val obs = transaction(db) {
            KcMasteryTable.selectAll()
                .where { (KcMasteryTable.userId eq uid) and (KcMasteryTable.kcId eq "pa-kc-001") }
                .map { it[KcMasteryTable.observations] }.singleOrNull()
        }
        assertEquals(1, obs, "recordIn flipped mastery into existence")
        // FSRS card seeded.
        val cards = transaction(db) {
            FsrsCardsTable.selectAll()
                .where { (FsrsCardsTable.userId eq uid) and (FsrsCardsTable.kcId eq "pa-kc-001") }
                .count()
        }
        assertTrue(cards >= 1, "upsertRubricCriterion seeded an FSRS card")
    }

    @Test fun `first_encounter is true exactly once across two lesson runs`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        // Run 1: predict then check (first contact).
        client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"predict","selected_index":0,"prediction_text":"O rețetă clară"}""")
        }
        client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        // Run 2: another check (no longer first contact).
        client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        val firstEncounterTrue = attemptsFor(db, uid, "pa-kc-001").count { it.third == true }
        assertEquals(1, firstEncounterTrue, "first_encounter set on exactly one attempt row")
    }

    @Test fun `a wrong choice grades incorrect with the served wrong-path feedback`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"attempt","selected_index":1}""")   // the wrong choice
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val body = r.bodyAsText()
        assertTrue(body.contains("\"correct\":false"), body)
        assertTrue(body.contains("reanalizează"), "served wrong-path feedback echoed (teaches, not just 'incorrect'): $body")
    }

    @Test fun `numeric check within tolerance grades correct, outside tolerance grades incorrect`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedNumericKc(content, "pa-kc-006")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-006", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val pass = client.post("/api/v1/lesson/pa-kc-006/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","free_input":"6.001"}""")   // within 0.01 of 6
        }
        assertEquals(HttpStatusCode.OK, pass.status, pass.bodyAsText())
        assertTrue(pass.bodyAsText().contains("\"correct\":true"), pass.bodyAsText())

        val fail = client.post("/api/v1/lesson/pa-kc-006/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","free_input":"7"}""")        // outside tolerance
        }
        assertEquals(HttpStatusCode.OK, fail.status, fail.bodyAsText())
        assertTrue(fail.bodyAsText().contains("\"correct\":false"), fail.bodyAsText())
    }

    @Test fun `non-faithful KC is 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
    }

    @Test fun `incomplete beats are 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        // Write a KC with concept_type but NO beats key (incomplete).
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"PA\"\n    name_en: \"PA\"\n",
        )
        val pa = content.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"x\"\nname_en: \"x\"\ncluster: c\nbloom_level: understand\n" +
                "difficulty: 1\ntime_minutes: 10\nexam_weight: 0.0\ntier: 1\nversion: 1\nconcept_type: definition-taxonomy\n",
        )
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"check","selected_index":0}""")
        }
        assertEquals(HttpStatusCode.NotFound, r.status, r.bodyAsText())
        assertTrue(r.bodyAsText().contains("beats not complete"), r.bodyAsText())
    }

    @Test fun `a beat_type outside the gated set is 400`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "beat-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedChoiceKc(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val r = client.post("/api/v1/lesson/pa-kc-001/beat") {
            header("Cookie", "jarvis_session=$sid; csrf=c"); header("X-CSRF-Token", "c")
            contentType(ContentType.Application.Json)
            setBody("""{"beat_type":"reveal","selected_index":0}""")   // reveal is not gradable
        }
        assertEquals(HttpStatusCode.BadRequest, r.status, r.bodyAsText())
    }
}
