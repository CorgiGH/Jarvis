package jarvis.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Faithful-gated first-encounter lesson serve (Task T7-1) — GET /api/v1/lesson/{kcId}.
 *
 * Class-killers (mirroring TeachingServeRouteTest):
 *  - faithful KC ⇒ 200, body has provenance.hasBeenFaithfulChecked == true;
 *  - non-faithful (e.g. uncertain / no B8 row) ⇒ 404 (OMIT, never a degraded payload);
 *  - faithful KC with an OPEN report_wrong ⇒ 404 (D-RF2 always-on serve refusal);
 *  - unknown kcId ⇒ 404; no session ⇒ 401.
 */
class LessonServeRouteTest {

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

    /**
     * Same content fixture as TeachingServeRouteTest (explanation + worked example), updated for Plan-3
     * to include concept_type + complete beats so the lesson GET passes the Task-3 beats guard.
     * The original 5 gate tests use this fixture and must remain green.
     */
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
                quote: "An algorithm is a well-ordered collection of unambiguous and effectively computable operations."
                page: 4
                provenance: located
            version: 1
            concept_type: definition-taxonomy
            explanation_ro: "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile, care produc un rezultat și se opresc în timp finit."
            worked_example_ro: "Exemplu: pașii adunării a două numere sunt neambigui, se execută efectiv și se opresc în timp finit, deci formează un algoritm."
            beats:
              ro:
                predict:
                  prompt: "Care dintre urmtoarele este un algoritm?"
                  options:
                    - text: "O reteta de prajitura cu pasi clari"
                      callback: "Corect — pasii sunt neambigui."
                      correct: true
                    - text: "Instructiunea fii fericit"
                      callback: "Nu — nu este efectiv calculabila."
                      correct: false
                    - text: "O bucla care nu se opreste niciodata"
                      callback: "Nu — un algoritm trebuie sa se opreasca."
                      correct: false
                attempt:
                  statement: "Clasifica exemplul: este sau nu un algoritm?"
                  feedback_correct: "Da — indeplineste toate cele patru proprietati."
                  choices:
                    - text: "Este un algoritm"
                      correct: true
                      feedback: "Corect: neambiguu, finit, efectiv, produce rezultat."
                    - text: "Nu este un algoritm"
                      correct: false
                      feedback: "Reananlizeaza: pasii sunt neambigui si se opresc."
                reveal:
                  steps:
                    - text: "Un algoritm are operatii neambigue."
                      callout: "Fiecare pas are un singur inteles."
                name:
                  definition: "Un algoritm este o colectie bine ordonata de operatii neambigue."
                  invariant_statement: "Orice algoritm se termina in timp finit."
                  why_matters: "Distinge un algoritm de o procedura care nu se termina."
                check:
                  item_stem: "Este aduna doua numere un algoritm?"
                  choices:
                    - text: "Da"
                      correct: true
                      feedback: "Corect — pasi finiti, neambigui."
                    - text: "Nu"
                      correct: false
                      feedback: "Gresit — indeplineste toate proprietatile."
            """.trimIndent(),
        )
    }

    /** Stamp a runtime B8 status (mirrors TeachingServeRouteTest.seedB8). */
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

    @Test fun `faithful KC serves the lesson reply with hasBeenFaithfulChecked true`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"hasBeenFaithfulChecked\":true"), body)
        assertTrue(body.contains("\"type\":\"authored\""), body)
    }

    @Test fun `non-faithful KC is omitted with 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `faithful KC with an OPEN dispute is refused with 404 (D-RF2)`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (uid, sid) = seedUser(db)
        seedB8(db, content, "pa-kc-001", VerificationStatus.faithful); openReport(db, uid, "pa-kc-001")
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `unknown kcId is 404`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir); val (_, sid) = seedUser(db)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-999") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `no session is 401`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedContent(content)
        val db = freshDb(dir)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001")
        assertEquals(HttpStatusCode.Unauthorized, resp.status, resp.bodyAsText())
    }

    /**
     * Plan-3 Task 3 — a PA KC with COMPLETE `ro` beats for the definition-taxonomy (choice) variant.
     * isCompleteFor(DEFINITION_TAXONOMY) needs: predict (3-4 options, ≥1 correct, callbacks non-blank),
     * attempt (statement + feedback_correct + choices each with non-blank feedback — the NON-numerical
     * branch), reveal (≥1 step, text+callout non-blank), check (item_stem + ≥1 correct choice). Beat ④
     * name is present so FIRST-encounter (FULL plan) can populate definition_ro.
     */
    private fun seedBeatsContent(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
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
            source:
              - doc: pa-lecture-01
                quote: "An algorithm is a well-ordered collection of unambiguous and effectively computable operations."
                page: 4
                provenance: located
            version: 1
            concept_type: definition-taxonomy
            explanation_ro: "Un algoritm este o colecție bine ordonată de operații."
            worked_example_ro: "Exemplu: pașii adunării a două numere."
            beats:
              ro:
                predict:
                  prompt: "Care dintre următoarele este un algoritm?"
                  options:
                    - text: "O rețetă de prăjitură cu pași clari"
                      callback: "Corect — pașii sunt neambigui și se termină."
                      correct: true
                    - text: "Instrucțiunea fii fericit"
                      callback: "Nu — nu este efectiv calculabilă."
                      correct: false
                    - text: "O buclă care nu se oprește niciodată"
                      callback: "Nu — un algoritm trebuie să se oprească în timp finit."
                      correct: false
                attempt:
                  statement: "Clasifică exemplul: este sau nu un algoritm?"
                  feedback_correct: "Da — îndeplinește toate cele patru proprietăți."
                  choices:
                    - text: "Este un algoritm"
                      correct: true
                      feedback: "Corect: neambiguu, finit, efectiv, produce rezultat."
                    - text: "Nu este un algoritm"
                      correct: false
                      feedback: "Reanalizează: pașii sunt neambigui și se opresc."
                reveal:
                  steps:
                    - text: "Un algoritm are operații neambigue."
                      callout: "Fiecare pas are un singur înțeles."
                    - text: "Un algoritm se oprește în timp finit."
                      callout: "Nu există bucle infinite."
                name:
                  definition: "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile care se opresc în timp finit."
                  invariant_statement: "Orice algoritm se termină în timp finit."
                  why_matters: "Distinge un algoritm de o procedură care nu se termină."
                check:
                  item_stem: "Este aduna doua numere un algoritm?"
                  choices:
                    - text: "Da"
                      correct: true
                      feedback: "Corect — pași finiți, neambigui."
                    - text: "Nu"
                      correct: false
                      feedback: "Greșit — îndeplinește toate proprietățile."
            """.trimIndent(),
        )
    }

    /** A copy of seedBeatsContent with the `name:` beat block removed → beats INCOMPLETE only if a
     *  required beat is missing. Here all of ①②③⑤ stay; ④ stays optional so this is still COMPLETE.
     *  For the incomplete case we drop `check:` (a required beat) below. */
    private fun seedIncompleteBeatsContent(content: Path, kcId: String) {
        content.createDirectories()
        content.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
        )
        val pa = content.resolve("PA")
        pa.resolve("kcs").createDirectories()
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
            beats:
              ro:
                predict:
                  prompt: "Care este un algoritm?"
                  options:
                    - text: "O rețetă"
                      callback: "Corect."
                      correct: true
                    - text: "Fii fericit"
                      callback: "Nu."
                      correct: false
                    - text: "Buclă infinită"
                      callback: "Nu."
                      correct: false
                attempt:
                  statement: "Clasifică."
                  feedback_correct: "Da."
                  choices:
                    - text: "Este"
                      correct: true
                      feedback: "Corect."
                reveal:
                  steps:
                    - text: "Pași neambigui."
                      callout: "Un singur înțeles."
            """.trimIndent(),
        )
    }

    /** Seed a kc_mastery row at a given phase + observation count (drives the served plan). */
    private fun seedMastery(
        db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String,
        phase: jarvis.tutor.Phase, observations: Int,
    ) = transaction(db) {
        KcMasteryTable.insert {
            it[KcMasteryTable.userId] = userId
            it[KcMasteryTable.kcId] = kcId
            it[ewmaScore] = 0.5
            it[KcMasteryTable.observations] = observations
            it[lastGradedAt] = Instant.now()
            it[KcMasteryTable.phase] = phase.name
        }
    }

    @Test fun `complete beats serve a 200 with the beats payload and populated prediction_options`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        // beats payload present; concept_type echoed; plan carries the predict beat.
        assertTrue(body.contains("\"concept_type\":\"definition-taxonomy\""), body)
        assertTrue(body.contains("\"predict\""), body)
        // prediction_options populated from beats predict options (NOT empty list).
        assertTrue(body.contains("O rețetă de prăjitură cu pași clari"), "prediction_options populated: $body")
        assertFalse(body.contains("\"prediction_options\":[]"), "must not be the honest-empty list: $body")
    }

    @Test fun `incomplete beats are 404 beats-not-complete`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedIncompleteBeatsContent(content, "pa-kc-001")   // missing the required `check:` beat
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
        assertTrue(resp.bodyAsText().contains("beats not complete"), resp.bodyAsText())
    }

    @Test fun `non-faithful KC remains 404 even with complete beats`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
    }

    @Test fun `STANDARD plan (practice phase) omits name and leaves definition_ro null`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (uid, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        // mastery row present + practice phase ⇒ NOT first encounter, NOT mastered ⇒ STANDARD (①②③⑤).
        seedMastery(db, uid, "pa-kc-001", jarvis.tutor.Phase.practice, observations = 2)
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        // STANDARD has no ④ NAME beat in the plan ⇒ no name block + definition_ro null.
        assertTrue(body.contains("\"definition_ro\":null"), "STANDARD ⇒ definition_ro null: $body")
        assertFalse(body.contains("\"name\":{"), "STANDARD plan must not carry the name beat: $body")
    }

    @Test fun `first encounter serves FULL with the name beat and populated definition_ro`() = testApplication {
        val dir = Path.of(System.getProperty("java.io.tmpdir"), "lesson-${TutorTypes.ulid()}")
        val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
        seedBeatsContent(content, "pa-kc-001")
        val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
        // NO mastery row ⇒ first encounter ⇒ FULL (①②③④⑤).
        application { installRoutes(db, dir) }
        val resp = client.get("/api/v1/lesson/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
        assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"name\":{"), "FULL plan carries the name beat: $body")
        assertTrue(body.contains("se opresc în timp finit"), "definition_ro populated from beats name: $body")
        assertFalse(body.contains("\"definition_ro\":null"), "first encounter ⇒ definition_ro populated: $body")
    }
}
