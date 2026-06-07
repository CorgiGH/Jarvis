package jarvis.tutor

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TASK P3-HONESTY (master-plan:205, §6.1 Calibration) — a SMALL grader-honesty spot-check,
 * NOT the deferred formal gold-set gate (§7 stays deferred; trust-badge language UNCHANGED).
 *
 * HONESTY NOTE (what is actually on disk — surveyed file-by-file before writing this):
 *   The PDFs master-plan:205 names as "prof-authored solved-exercise / exam-solution PDFs" are,
 *   on disk, NOT solved:
 *     - PA/local_extras/Algorithm Design - Evaluation.pdf  = course evaluation *policy* (no exercises)
 *     - PA/local_extras/PA_Rezultate_Partial (2).pdf       = a *grade-results table* (reg# + points, no keys)
 *     - PA/labs/seminar9_10*.pdf                           = *unsolved* exercise statements
 *     - ALO/hw/alo_t1..t5.pdf, ALO/labs/alo_sem1..7.pdf    = *unsolved* homework/seminar statements
 *   There are NO prof-authored worked answer keys on disk to read a solution off of.
 *
 *   So the keys here are SYSTEM-DERIVED, never via Alex (no oracle inversion): the vector OPERANDS
 *   are extracted verbatim from the REAL on-disk ALO/labs/alo_sem1.pdf (via pdftotext), and the
 *   correct answers are computed by PURE DETERMINISTIC MATH (vector p-norms) — the on-disk PDF is
 *   the source of record for the operands; the math is mechanically forced by the operands.
 *
 *   OFFLINE-CHECKABLE SUBSET (what this asserts): only deterministic/invariant-gradable items — the
 *   grader's LLM-INDEPENDENT leg `GradeScoring.answerMatches`/`scoreFromRubric`/`correctFromRubric`
 *   (the leg that decides correctness, NOT the LLM's self-reported float). The open-ended proof items
 *   (NP-completeness reductions in seminar9/10, derivations) are RELAY-graded → SKIPPED here.
 *
 *   Skips gracefully (assumeTrue) like E3RealRelayProofTest if the PDF or pdftotext is unavailable,
 *   so this never fails for environment reasons and never fakes a passing honesty check.
 */
class P3HonestyGraderSpotCheckTest {

    private val pdf = File(System.getProperty("user.dir"), "tmp-secondbrain-scrape/ALO/labs/alo_sem1.pdf")

    /** Run pdftotext on [file]; null if the binary is missing/unrunnable in this environment. */
    private fun pdftotext(file: File): String? {
        for (bin in listOf("pdftotext", "pdftotext.exe")) {
            try {
                val proc = ProcessBuilder(bin, "-q", file.absolutePath, "-").redirectErrorStream(false).start()
                val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
                if (!proc.waitFor(30, TimeUnit.SECONDS)) { proc.destroyForcibly(); continue }
                if (proc.exitValue() == 0) return out
            } catch (_: Exception) { /* binary not on PATH for the JVM — try next */ }
        }
        return null
    }

    /** Parse all `x = (a, b, c)T` integer vectors out of the extracted text (system-derived operands).
     *  Tolerant of BOTH the ASCII hyphen '-' and the Unicode minus '−' (U+2212) — different pdftotext
     *  builds emit different glyphs for the same on-disk operand; normalize before parsing. */
    private fun extractVectors(text: String): List<List<Int>> {
        // normalize Unicode minus (U+2212) and the en-dash to ASCII '-' so the operands parse identically.
        val norm = text.replace('−', '-').replace('–', '-')
        return Regex("""x = \((-?\d+(?:,\s*-?\d+)*)\)T""").findAll(norm).map { m ->
            m.groupValues[1].split(",").map { it.trim().toInt() }
        }.toList()
    }

    private fun l1(v: List<Int>) = v.sumOf { abs(it) }.toDouble()
    private fun lInf(v: List<Int>) = v.maxOf { abs(it) }.toDouble()
    private fun l2(v: List<Int>) = sqrt(v.sumOf { (it * it).toDouble() })

    @Test
    fun `grader's deterministic leg agrees with system-derived norm keys from the real on-disk ALO seminar PDF`() {
        assumeTrue(pdf.isFile) { "ALO/labs/alo_sem1.pdf absent — skipping P3-HONESTY spot-check" }
        val text = pdftotext(pdf)
        assumeTrue(text != null) { "pdftotext unavailable to the JVM — skipping P3-HONESTY spot-check" }

        val vectors = extractVectors(text!!)
        // The two clean integer vectors of seminar-1 ex.2 (a) and (b): (-2,2,1) and (2,-1,3,4).
        assumeTrue(vectors.isNotEmpty()) { "no clean integer vectors extracted from the PDF — skipping" }
        assertTrue(vectors.contains(listOf(-2, 2, 1)),
            "expected operand vector (-2,2,1) from the on-disk PDF; got $vectors")

        // ── Build the SAMPLED honesty items: (vector, norm-name, system-derived key) ──────────
        // Only the deterministic, exactly-representable norm values (L1, Linf, and L2 when it is an
        // exact integer) — the offline-checkable subset. L2 of (2,-1,3,4)=sqrt(30) is irrational →
        // EXCLUDED from the deterministic subset (it would force a tolerance the grader's exact
        // string/numeric matcher does not model) — RECORDED as skipped below.
        data class Item(val label: String, val key: String)
        val items = buildList {
            vectors.firstOrNull { it == listOf(-2, 2, 1) }?.let { v ->
                add(Item("(-2,2,1) L1", fmt(l1(v))))     // 5
                add(Item("(-2,2,1) Linf", fmt(lInf(v)))) // 2
                add(Item("(-2,2,1) L2", fmt(l2(v))))     // 3 (exact: sqrt(9))
            }
            vectors.firstOrNull { it == listOf(2, -1, 3, 4) }?.let { v ->
                add(Item("(2,-1,3,4) L1", fmt(l1(v))))     // 10
                add(Item("(2,-1,3,4) Linf", fmt(lInf(v)))) // 4
                // L2 = sqrt(30) irrational → NOT in the deterministic offline subset (skipped).
            }
        }
        assumeTrue(items.isNotEmpty()) { "no deterministic norm items derivable — skipping" }

        // ── Assert the grader's DETERMINISTIC leg is HONEST on every sampled item ──────────────
        // The leg under test is GradeScoring (LLM-independent): a correct attempt must match the
        // system-derived key; a wrong attempt must NOT, and must score 0 / not-correct.
        for (it in items) {
            // (1) the canonical/system-derived answer is accepted (deterministic match).
            assertTrue(GradeScoring.answerMatches(it.key, it.key),
                "grader must accept the system-derived key for ${it.label} (=${it.key})")
            // (1b) a numerically-equal variant (trailing .0 / spacing) also matches — no false NO.
            assertTrue(GradeScoring.answerMatches(it.key, "  ${it.key}.0 "),
                "grader must accept a numerically-equal formatting of ${it.label}")
            // (2) a deliberately wrong answer is rejected — no false YES (the honesty direction).
            val wrong = (it.key.toDouble() + 1.0).let { d -> if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString() }
            assertFalse(GradeScoring.answerMatches(it.key, wrong),
                "grader must REJECT a wrong answer ($wrong) for ${it.label} (=${it.key})")

            // (3) the rubric-derived score/correctness leg agrees: all-pass ⇒ correct & score 1.0;
            // any-fail ⇒ not-correct & score < 1.0. This is the deterministic scoring honesty.
            val allPass = mapOf("value" to true)
            val oneFail = mapOf("value" to false)
            assertTrue(GradeScoring.correctFromRubric(allPass), "all-pass rubric ⇒ correct for ${it.label}")
            assertEquals(1.0, GradeScoring.scoreFromRubric(allPass), 1e-9, "all-pass ⇒ score 1.0 for ${it.label}")
            assertFalse(GradeScoring.correctFromRubric(oneFail), "a failed criterion ⇒ NOT correct for ${it.label}")
            assertEquals(0.0, GradeScoring.scoreFromRubric(oneFail), 1e-9, "a failed criterion ⇒ score 0 for ${it.label}")
        }

        // RECORDED: checked = the ${items.size} deterministic L1/Linf/exact-L2 norm items above;
        // skipped = L2=sqrt(30) (irrational) + all open-ended proof/derivation items (relay-graded).
        println("[P3-HONESTY] checked ${items.size} deterministic norm items vs system-derived keys " +
            "from on-disk alo_sem1.pdf; skipped: irrational-L2 + relay-graded open items.")
    }

    /** Format an exact integer-valued double as a clean integer string ("5" not "5.0"). */
    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
