package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan-6 Task 2 — ProblemsRepo unit tests (in-memory/scratch SQLite only — NEVER touches live DB).
 * Covers: insert/find by id, list by subject, rubric items ordered by position, KC links round-trip,
 * countBySubject (the INV-6.1 pending probe).
 */
class ProblemsRepoTest {

    private fun scratchDb(tmp: Path) = TutorDb.connect(tmp.resolve("problems-test.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                ProblemsTable,
                ProblemRubricItemsTable,
                ProblemKcLinksTable,
            )
        }
    }

    private fun sampleProblem(id: String = "test-001", subject: String = "PA") = BankProblem(
        id = id,
        subject = subject,
        archetype = "NP reduction proof",
        statementJson = """{"ro":"Demonstrați că HAM-PATH ∈ NP."}""",
        parameterSlotsJson = null,
        solutionPresent = true,
        solutionJson = """{"proof_frame":{"template_ro":"Construiți un algoritm nondeterminist...","substeps":[]}}""",
        examLanguage = null,
        examLanguageConstraintsJson = null,
        dataFilesJson = null,
        sourceDoc = "np-complete-course-notes",
        sourcePage = null,
        provenance = "located",
        syntheticTag = false,
    )

    @Test
    fun `insert and findById round-trip`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        val prob = sampleProblem()
        repo.upsert(prob)
        val found = repo.findById("test-001")
        assertNotNull(found, "Should find inserted problem")
        assertEquals("test-001", found.id)
        assertEquals("PA", found.subject)
        assertEquals("NP reduction proof", found.archetype)
    }

    @Test
    fun `findById returns null for missing id`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        assertNull(repo.findById("nonexistent-id"))
    }

    @Test
    fun `listBySubject returns only matching problems`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        repo.upsert(sampleProblem("pa-001", "PA"))
        repo.upsert(sampleProblem("pa-002", "PA"))
        repo.upsert(sampleProblem("alo-001", "ALO"))
        val paProblems = repo.listBySubject("PA")
        assertEquals(2, paProblems.size, "Should find 2 PA problems")
        assertTrue(paProblems.all { it.subject == "PA" })
        val aloProblems = repo.listBySubject("ALO")
        assertEquals(1, aloProblems.size)
    }

    @Test
    fun `rubric items ordered by position`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        repo.upsert(sampleProblem("prob-rubric"))
        val items = listOf(
            RubricItem("ri-003", "prob-rubric", "G3", 2.0, "AP", false, null, 3),
            RubricItem("ri-001", "prob-rubric", "G1", 1.0, "AP", true, null, 1),
            RubricItem("ri-002", "prob-rubric", "G2", 1.5, "WP", false, null, 2),
        )
        repo.upsertRubricItems(items)
        val loaded = repo.listRubricItems("prob-rubric")
        assertEquals(3, loaded.size)
        assertEquals(listOf(1, 2, 3), loaded.map { it.position }, "Must be ordered by position")
        assertEquals("G1", loaded[0].label)
        assertEquals("G3", loaded[2].label)
    }

    @Test
    fun `KC links round-trip`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        repo.upsert(sampleProblem("prob-kc"))
        repo.upsertKcLinks("prob-kc", listOf("pa-kc-001", "pa-kc-002"))
        val links = repo.listKcLinks("prob-kc")
        assertEquals(setOf("pa-kc-001", "pa-kc-002"), links.toSet())
    }

    @Test
    fun `countBySubject returns accurate per-subject counts`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        repo.upsert(sampleProblem("pa-001", "PA"))
        repo.upsert(sampleProblem("pa-002", "PA"))
        repo.upsert(sampleProblem("alo-001", "ALO"))
        val counts = repo.countBySubject()
        assertEquals(2, counts["PA"])
        assertEquals(1, counts["ALO"])
        assertNull(counts["PS"])  // no PS seeded
    }

    @Test
    fun `upsert is idempotent - second upsert updates not duplicates`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        val prob = sampleProblem("idem-001")
        repo.upsert(prob)
        repo.upsert(prob.copy(archetype = "updated archetype"))
        val all = repo.listBySubject("PA")
        assertEquals(1, all.size, "Upsert must not duplicate")
        assertEquals("updated archetype", all[0].archetype)
    }
}
