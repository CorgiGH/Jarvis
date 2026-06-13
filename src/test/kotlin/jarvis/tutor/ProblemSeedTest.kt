package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Plan-6 Task 2 (fix-round) — ProblemSeed integration tests.
 *
 * These tests call [ProblemSeed.seed] to exercise the quote-anchor machine-verification path
 * (the critical path that the first round did NOT cover — no test called seed() at all).
 *
 * Two categories:
 *  1. Green path — seed from the real `content/` dir; confirm PA/ALO/PS loaded, POO absent (pending).
 *  2. RED path — seed a synthetic content dir with a bad quote anchor; MUST throw loudly.
 */
class ProblemSeedTest {

    private fun scratchDb(tmp: Path) = TutorDb.connect(tmp.resolve("seed-test.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                ProblemsTable,
                ProblemRubricItemsTable,
                ProblemKcLinksTable,
            )
        }
    }

    // ── Green path: seed from the real content/ directory ────────────────────────────

    @Test
    fun `seed from real content dir loads PA ALO PS - each subject gets exactly 1 row`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val contentDir = Path.of("content")
        val result = ProblemSeed.seed(db, contentDir)
        // 3 subjects × 1 problem each — after removing the fabricated POO entry
        assertEquals(3, result.inserted, "Expected 3 inserted (PA + ALO + PS); got ${result.inserted}")
        assertEquals(0, result.updated, "First seed must produce no updates")
        val repo = ProblemsRepo(db)
        assertEquals(1, repo.countBySubject()["PA"], "PA must have 1 problem")
        assertEquals(1, repo.countBySubject()["ALO"], "ALO must have 1 problem")
        assertEquals(1, repo.countBySubject()["PS"], "PS must have 1 problem")
    }

    @Test
    fun `POO has zero seeded problems - pending subject correctly absent`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        ProblemSeed.seed(db, Path.of("content"))
        val repo = ProblemsRepo(db)
        val counts = repo.countBySubject()
        assertTrue(counts["POO"] == null || counts["POO"] == 0,
            "POO must be absent from seeded problems (pending subject — no real corpus problem located)")
    }

    @Test
    fun `seed is idempotent - re-seeding the same content dir produces updates not duplicates`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        val contentDir = Path.of("content")
        val first = ProblemSeed.seed(db, contentDir)
        val second = ProblemSeed.seed(db, contentDir)
        assertEquals(3, first.inserted, "First seed: 3 inserts")
        assertEquals(0, first.updated, "First seed: 0 updates")
        assertEquals(0, second.inserted, "Second seed (idempotent): 0 inserts")
        assertEquals(3, second.updated, "Second seed (idempotent): 3 updates")
        // Total row count unchanged
        val repo = ProblemsRepo(db)
        assertEquals(3, repo.countBySubject().values.sum(), "Row count must stay at 3 after re-seed")
    }

    @Test
    fun `PA problem pa-prob-001 has exam_language alk per plan Step 4 R-LANG rule`(@TempDir tmp: Path) {
        val db = scratchDb(tmp)
        ProblemSeed.seed(db, Path.of("content"))
        val pa = ProblemsRepo(db).findById("pa-prob-001")
        assertEquals("alk", pa?.examLanguage,
            "pa-prob-001 exam_language must be 'alk' per plan Step 4 §3.4 R-LANG: PA=alk")
    }

    // ── RED path: bad quote anchor MUST be REFUSED with a loud error ─────────────────

    @Test
    fun `bad quote anchor - seed REFUSED with descriptive error (RED gate)`(@TempDir tmp: Path) {
        // Build a synthetic content dir with one problem whose source.quote does NOT
        // appear in the committed source doc — the quote-anchor verification must REFUSE.
        val syntheticContent = tmp.resolve("content")
        val subjectDir = syntheticContent.resolve("FAKE")
        subjectDir.resolve("_sources").createDirectories()
        subjectDir.resolve("problems").createDirectories()

        // Source doc that does NOT contain the quote we will reference
        subjectDir.resolve("_sources").resolve("fake-source.md").writeText(
            """
            # Fake Source
            This document contains some real text about algorithms.
            """.trimIndent()
        )

        // Problem YAML referencing a quote that is NOT in the source doc
        subjectDir.resolve("problems").resolve("fake-001.yaml").writeText(
            """
            id: fake-001
            subject: FAKE
            archetype: test archetype
            statement_ro: "Test statement"
            solution_present: false
            rubric_items: []
            kc_links: []
            source:
              doc: fake-source
              page: null
              quote: "THIS QUOTE DOES NOT EXIST IN THE SOURCE DOC"
            synthetic_tag: false
            """.trimIndent()
        )

        val db = scratchDb(tmp)
        val ex = assertFailsWith<IllegalArgumentException>(
            message = "ProblemSeed must REFUSE a problem with a quote anchor that is not in the source doc"
        ) {
            ProblemSeed.seed(db, syntheticContent)
        }
        assertTrue(
            ex.message?.contains("REFUSED") == true,
            "Error message must say REFUSED (system verifies, not the user). Got: ${ex.message}"
        )
        assertTrue(
            ex.message?.contains("fake-001") == true,
            "Error message must name the offending problem id. Got: ${ex.message}"
        )
    }

    @Test
    fun `missing source doc - seed REFUSED with descriptive error`(@TempDir tmp: Path) {
        // Problem references a source doc that does not exist at all
        val syntheticContent = tmp.resolve("content2")
        val subjectDir = syntheticContent.resolve("FAKE2")
        subjectDir.resolve("_sources").createDirectories()
        subjectDir.resolve("problems").createDirectories()

        // Source doc file is NOT written — the _sources/ dir exists but the doc does not

        subjectDir.resolve("problems").resolve("fake-002.yaml").writeText(
            """
            id: fake-002
            subject: FAKE2
            archetype: test archetype
            statement_ro: "Test statement"
            solution_present: false
            rubric_items: []
            kc_links: []
            source:
              doc: nonexistent-source
              page: null
              quote: "some quote"
            synthetic_tag: false
            """.trimIndent()
        )

        val db = scratchDb(tmp)
        val ex = assertFailsWith<IllegalArgumentException>(
            message = "ProblemSeed must REFUSE when the source doc file is missing"
        ) {
            ProblemSeed.seed(db, syntheticContent)
        }
        assertTrue(
            ex.message?.contains("REFUSED") == true,
            "Error message must say REFUSED. Got: ${ex.message}"
        )
    }
}
