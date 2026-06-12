package jarvis.tutor.verify

import jarvis.content.ConceptType
import jarvis.content.ContentRepo
import jarvis.content.KcBeats
import jarvis.content.KnowledgeConcept
import jarvis.tutor.ExamScheduleRowsTable
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.GradingModelsTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plan-2 Task 9 — the new trustInvariants legs: INV-3.2a (concept_type), INV-3.2b (beat
 * completeness for AUTHORED beats only), INV-3.3-link (satisfiable form), INV-3.4 (grade-model
 * + schedule provenance), and the SCHEMA pins for the 7 new tables. Each failure class is shown
 * BOTH green and red. Uses the REAL in-repo corpus (content/) for the green case (gradle test
 * workingDir = project root) so it exercises the exact ContentRepo path the serve gate uses.
 */
class TrustInvariantsPlan2Test {

    private val contentDir: Path = Path.of("content")

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("inv2.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    // Minimal valid grade-model + 12 schedule rows so INV-3.4 is GREEN unless a test breaks one.
    private fun seedGradeModels(db: Database, sourceUrl: String = "https://edu.info.uaic.ro/x") {
        transaction(db) {
            GradingModelsTable.insert {
                it[id] = "gm-x"; it[subject] = "ALO"; it[variant] = null; it[isPrimary] = true
                it[formula] = "F"; it[maxTotal] = 800.0; it[passRuleJson] = "{}"
                it[evidenceTier] = "official-site"; it[GradingModelsTable.sourceUrl] = sourceUrl
                it[sweepRef] = "verified-grade-models-exam-schedule.md#alo"
                it[createdAt] = Instant.now()
            }
        }
    }

    private val sweepAnchor =
        "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"

    private fun seedSchedule(db: Database, count: Int = 12, sourceRef: String = sweepAnchor) {
        transaction(db) {
            repeat(count) { i ->
                ExamScheduleRowsTable.insert {
                    it[id] = "es-$i"; it[subject] = "ALO"; it[rawDiscipline] = "d"
                    it[examType] = "examen"; it[startAt] = Instant.now(); it[datePrecision] = "exact"
                    it[ExamScheduleRowsTable.sourceRef] = sourceRef; it[createdAt] = Instant.now()
                }
            }
        }
    }

    @Test
    fun `INV-3-2a green on the real corpus once concept_type is backfilled`(@TempDir tmp: Path) {
        // Task 4 backfills concept_type onto all 8 PA KCs; this asserts the real corpus passes.
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.none { it.check == "INV-3.2a" }, "$failures")
    }

    @Test
    fun `INV-3-2a red when a KC has a null concept_type`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val kcs = listOf(kc("k1", conceptType = null))
        val failures = TrustInvariantsCli.checkKcs(kcs)
        assertTrue(failures.any { it.check == "INV-3.2a" && it.detail.contains("k1") }, "$failures")
    }

    @Test
    fun `INV-3-2a red when a KC has an unknown concept_type literal`(@TempDir tmp: Path) {
        val failures = TrustInvariantsCli.checkKcs(listOf(kc("k1", conceptType = "not-a-type")))
        assertTrue(failures.any { it.check == "INV-3.2a" && it.detail.contains("k1") }, "$failures")
    }

    @Test
    fun `INV-3-2b red when an AUTHORED ro beat is incomplete`() {
        // beats non-empty but ro incomplete (predict missing) -> fail
        val k = kc("k1", conceptType = "procedure").copy(beats = mapOf("ro" to KcBeats()))
        val failures = TrustInvariantsCli.checkKcs(listOf(k))
        assertTrue(failures.any { it.check == "INV-3.2b" && it.detail.contains("k1") }, "$failures")
    }

    @Test
    fun `INV-3-2b GREEN when beats are EMPTY (beat-serving not live in Plan 2)`() {
        val k = kc("k1", conceptType = "procedure") // beats default emptyMap()
        val failures = TrustInvariantsCli.checkKcs(listOf(k))
        assertTrue(failures.none { it.check == "INV-3.2b" }, "empty beats must NOT fail in Plan 2: $failures")
    }

    @Test
    fun `INV-3-4 red when a grade model has a blank source_url`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db, sourceUrl = ""); seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("source_url") }, "$failures")
    }

    @Test
    fun `INV-3-4 red when the schedule row count is not 12`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db, count = 11)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("12") }, "$failures")
    }

    @Test
    fun `INV-3-4 red when a schedule row sourceRef is not the sweep anchor`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db)
        seedSchedule(db, count = 12, sourceRef = "some-other-doc.md")
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("sourceRef") }, "$failures")
    }

    @Test
    fun `INV-3-4 red when two grade models are is_primary for one subject`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db)
        transaction(db) {
            GradingModelsTable.insert {
                it[id] = "gm-x2"; it[subject] = "ALO"; it[variant] = "v2"; it[isPrimary] = true
                it[formula] = "F2"; it[passRuleJson] = "{}"; it[evidenceTier] = "official-site"
                it[sourceUrl] = "https://edu.info.uaic.ro/x"
                it[sweepRef] = "verified-grade-models-exam-schedule.md#alo"; it[createdAt] = Instant.now()
            }
        }
        seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("is_primary") }, "$failures")
    }

    @Test
    fun `SCHEMA pins green for the 7 new tables on a fully-migrated DB`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.none { it.check == "SCHEMA" }, "$failures")
    }

    // ── INV-3.3-link: both green and red (fix-claim discipline — a check that cannot be shown
    //    red does not exist). Needs a TEMP corpus whose KC source doc OVERLAPS a card source_ref
    //    (the real content/ has zero overlap, so it can only ever show the vacuous-green path).
    private fun writeOverlapCorpus(tmp: Path): Path {
        val root = tmp.resolve("corpus"); root.createDirectories()
        root.resolve("subjects.yaml").writeText(
            """
            version: 1
            subjects:
              - id: PA
                name_ro: "Proiectarea Algoritmilor"
                name_en: "Algorithm Design"
            """.trimIndent(),
        )
        val kcs = root.resolve("PA").resolve("kcs"); kcs.createDirectories()
        // KC doc 'lecture_x' and card ref 'PA:lecture-x.md' both normalize to stem "lecture x"
        kcs.resolve("pa-kc-t1.yaml").writeText(
            """
            id: pa-kc-t1
            subject: PA
            name_ro: "KC de test"
            name_en: "Test KC"
            cluster: "c"
            bloom_level: understand
            difficulty: 1
            time_minutes: 1
            exam_weight: 0.0
            tier: 1
            concept_type: definition-taxonomy
            source:
              - doc: lecture_x
                quote: "citat de test"
            """.trimIndent(),
        )
        return root
    }

    private fun insertCardWithUser(db: Database, cardId: String, sourceRef: String, kcId: String?) {
        transaction(db) {
            if (UsersTable.selectAll().where { UsersTable.id eq "owner" }.count() == 0L) {
                UsersTable.insert {
                    it[UsersTable.id] = "owner"; it[name] = "owner"; it[scope] = "OWNER"
                    it[createdAt] = Instant.now(); it[lastSeenAt] = Instant.now(); it[email] = "o@x"
                }
            }
            FsrsCardsTable.insert {
                it[FsrsCardsTable.id] = cardId
                it[userId] = "owner"; it[sourceType] = "MANUAL"
                it[FsrsCardsTable.sourceRef] = sourceRef
                it[front] = "f"; it[back] = "b"
                it[difficulty] = 5.0; it[stability] = 0.5; it[retrievability] = 1.0
                it[dueAt] = Instant.now(); it[lastReviewedAt] = Instant.now(); it[lapses] = 0
                it[FsrsCardsTable.kcId] = kcId; it[status] = "ACTIVE"
            }
        }
    }

    @Test
    fun `INV-3-3-link red when a subject HAS source-doc overlap but zero linked cards`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val corpus = writeOverlapCorpus(tmp)
        insertCardWithUser(db, "card-1", "PA:lecture-x.md", kcId = null) // overlap exists, link missing
        val failures = TrustInvariantsCli.check(db, corpus, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.3-link" && it.detail.contains("PA") }, "$failures")
    }

    @Test
    fun `INV-3-3-link green when the overlapping card IS linked`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val corpus = writeOverlapCorpus(tmp)
        insertCardWithUser(db, "card-1", "PA:lecture-x.md", kcId = "pa-kc-t1")
        val failures = TrustInvariantsCli.check(db, corpus, backupDir = null, floor = 0)
        assertTrue(failures.none { it.check == "INV-3.3-link" }, "$failures")
    }

    private fun kc(id: String, conceptType: String?): KnowledgeConcept =
        KnowledgeConcept(
            id = id, subject = "PA", name_ro = id, name_en = id, cluster = "c",
            bloom_level = "understand", difficulty = 1, time_minutes = 1, exam_weight = 0.0,
            tier = 1, concept_type = conceptType,
        )
}
