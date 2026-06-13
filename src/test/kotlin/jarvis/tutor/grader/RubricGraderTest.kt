package jarvis.tutor.grader

import jarvis.tutor.ProblemsRepo
import jarvis.tutor.ProblemKcLinksTable
import jarvis.tutor.ProblemRubricItemsTable
import jarvis.tutor.ProblemsTable
import jarvis.tutor.RubricItem
import jarvis.tutor.TutorDb
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-6 Task 3 Step 5 — the structural rubric leg, exercised through BOTH item sources
 * via the ONE common [RubricInput] model:
 *   (a) `ProblemRubricItemsTable` rows via `ProblemsRepo` (bank problems),
 *   (b) request/task_prep-derived rubric items (dominant live `/drill/grade` traffic).
 *
 * Verdicts come from STRUCTURAL matchers, NEVER from LLM-emitted booleans, for either source.
 */
class RubricGraderTest {

    private val grader = RubricGrader()

    private fun scratchDb(tmp: Path) = TutorDb.connect(tmp.resolve("rubric-test.db").toString()).also { db ->
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                ProblemsTable, ProblemRubricItemsTable, ProblemKcLinksTable,
            )
        }
    }

    // ─── Source (b): request-derived items ──────────────────────────────────────

    @Test
    fun `request-derived items — points summed, AP-WP split reported, all pass`() = runBlocking {
        val items = listOf(
            RubricInput("g1", "G1", points = 2.0, kind = "AP",
                matcherJson = """{"kind":"contains","value":"permutare"}"""),
            RubricInput("g2", "G2", points = 3.0, kind = "WP",
                matcherJson = """{"kind":"regex","value":"O\\(n"}"""),
        )
        val out = grader.grade(GradeInput(attempt = "ghicim o permutare; verificam in O(n^2)", rubricItems = items))
        assertTrue(out is LegOutcome.Decided)
        out as LegOutcome.Decided
        assertTrue(out.correct, "both structural matchers pass")
        assertEquals(1.0, out.score, 1e-9)
        assertEquals(2, out.itemVerdicts.size)
        assertEquals(2.0, out.itemVerdicts[0].points_max)
        assertEquals(2.0, out.itemVerdicts[0].points_earned)
        assertTrue(out.itemVerdicts.all { it.passed })
    }

    @Test
    fun `request-derived items — a failing matcher gives partial score, not correct`() = runBlocking {
        val items = listOf(
            RubricInput("g1", "G1", points = 2.0, matcherJson = """{"kind":"contains","value":"permutare"}"""),
            RubricInput("g2", "G2", points = 2.0, matcherJson = """{"kind":"contains","value":"NU_APARE"}"""),
        )
        val out = grader.grade(GradeInput(attempt = "doar o permutare aici", rubricItems = items))
        assertTrue(out is LegOutcome.Decided)
        out as LegOutcome.Decided
        assertFalse(out.correct)
        assertEquals(0.5, out.score, 1e-9, "1 of 2 items, equal points")
        assertFalse(out.itemVerdicts[1].passed)
    }

    // ─── allOrNothing semantics ─────────────────────────────────────────────────

    @Test
    fun `allOrNothing item — full points on pass`() = runBlocking {
        val items = listOf(
            RubricInput("g1", "G1", points = 4.0, allOrNothing = true,
                matcherJson = """{"kind":"contains","value":"corect"}"""),
        )
        val out = grader.grade(GradeInput(attempt = "raspuns corect", rubricItems = items)) as LegOutcome.Decided
        assertEquals(4.0, out.itemVerdicts[0].points_earned)
        assertTrue(out.correct)
    }

    @Test
    fun `allOrNothing item with a firing penalty — zero, not a deduction`() = runBlocking {
        val items = listOf(
            RubricInput("g1", "G1", points = 4.0, allOrNothing = true,
                matcherJson = """{"kind":"contains","value":"corect"}""",
                penaltyJson = """{"kind":"contains","value":"semn gresit","deduct":0.5}"""),
        )
        val out = grader.grade(
            GradeInput(attempt = "raspuns corect dar semn gresit", rubricItems = items),
        ) as LegOutcome.Decided
        assertEquals(0.0, out.itemVerdicts[0].points_earned, "all-or-nothing + penalty fired → 0")
        assertFalse(out.correct)
    }

    // ─── penalty fires ONCE, not per-line ───────────────────────────────────────

    @Test
    fun `penalty deducts once even when the pattern appears on multiple lines`() = runBlocking {
        // wrong-sign appears 3 times; deduct (0.5) must hit ONCE, not 1.5.
        val items = listOf(
            RubricInput("g1", "G1", points = 3.0,
                matcherJson = """{"kind":"contains","value":"reducere"}""",
                penaltyJson = """{"kind":"contains","value":"semn gresit","deduct":0.5}"""),
        )
        val attempt = "reducere\nsemn gresit\nsemn gresit\nsemn gresit"
        val out = grader.grade(GradeInput(attempt = attempt, rubricItems = items)) as LegOutcome.Decided
        assertEquals(2.5, out.itemVerdicts[0].points_earned!!, 1e-9, "3.0 - 0.5 once = 2.5")
    }

    // ─── deferral when nothing is machine-checkable ─────────────────────────────

    @Test
    fun `no machine-checkable item — the whole leg DEFERS to the LLM judge`() = runBlocking {
        val items = listOf(
            RubricInput("g1", "G1", points = 2.0, matcherJson = null),  // prose, no matcher
            RubricInput("g2", "G2", points = 2.0, matcherJson = "   "),  // blank
        )
        val out = grader.grade(GradeInput(attempt = "an essay answer", rubricItems = items))
        assertTrue(out is LegOutcome.Defer, "all items prose-only → defer to LLM")
    }

    @Test
    fun `empty rubric — the leg DEFERS`() = runBlocking {
        val out = grader.grade(GradeInput(attempt = "x", rubricItems = emptyList()))
        assertTrue(out is LegOutcome.Defer)
    }

    @Test
    fun `mixed items — machine-checkable graded, non-checkable item recorded as not-correct decision`() = runBlocking {
        val items = listOf(
            RubricInput("g1", "G1", points = 2.0, matcherJson = """{"kind":"contains","value":"permutare"}"""),
            RubricInput("g2", "G2", points = 2.0, matcherJson = null),  // needs LLM
        )
        val out = grader.grade(GradeInput(attempt = "o permutare", rubricItems = items)) as LegOutcome.Decided
        // only the structural item is verdicted; the leg cannot claim overall correctness
        // because g2 still needs the LLM pairing.
        assertFalse(out.correct, "an LLM-only item remains — rubric leg cannot claim full correctness")
        assertEquals(1, out.itemVerdicts.size, "only the machine-checkable item gets a structural verdict")
    }

    // ─── Source (a): bank rows via ProblemsRepo → SAME RubricInput model ─────────

    @Test
    fun `bank rubric rows map to RubricInput and grade identically`(@TempDir tmp: Path) = runBlocking {
        val db = scratchDb(tmp)
        val repo = ProblemsRepo(db)
        // Seed a bank rubric row carrying a structural matcher in penaltyRulesJson's sibling
        // path: we mirror the matcher into RubricInput.matcherJson when mapping (the production
        // mapping in Task 7/8 reads the bank row's matcher config the same way).
        repo.upsertRubricItems(
            listOf(
                RubricItem(
                    id = "bp-g1", problemId = "bank-prob", label = "G1", points = 2.0,
                    kind = "AP", allOrNothing = false,
                    penaltyRulesJson = """{"matcher":{"kind":"contains","value":"permutare"}}""",
                    position = 1,
                ),
            ),
        )
        val rows = repo.listRubricItems("bank-prob")
        assertEquals(1, rows.size)
        // Map the bank row → the common RubricInput model (matcher pulled from penaltyRulesJson).
        val mapped = rows.map { row ->
            RubricInput(
                id = row.id, label = row.label, points = row.points, kind = row.kind,
                allOrNothing = row.allOrNothing,
                matcherJson = extractMatcher(row.penaltyRulesJson),
            )
        }
        val out = grader.grade(GradeInput(attempt = "ghicim o permutare", rubricItems = mapped)) as LegOutcome.Decided
        assertTrue(out.correct)
        assertEquals("G1", out.itemVerdicts[0].label)
    }

    /** Pull a nested {"matcher":{...}} object out of a bank row's penaltyRulesJson, if present. */
    private fun extractMatcher(penaltyRulesJson: String?): String? {
        if (penaltyRulesJson.isNullOrBlank()) return null
        return try {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(penaltyRulesJson)
                as? kotlinx.serialization.json.JsonObject ?: return null
            obj["matcher"]?.toString()
        } catch (_: Exception) {
            null
        }
    }
}
