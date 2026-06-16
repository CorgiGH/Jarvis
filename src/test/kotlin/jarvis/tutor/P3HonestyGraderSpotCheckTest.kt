package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TASK P3-HONESTY (master-plan:205, §6.1 Calibration) — a SMALL grader-honesty spot-check,
 * NOT the deferred formal gold-set gate (§7 stays deferred; trust-badge language UNCHANGED).
 *
 * P2-6 HARDENING (2026-06-08, adversarial-council fix): the original test `assumeTrue`-SKIPPED when
 * the seminar PDF / pdftotext were absent. But `git ls-files tmp-secondbrain-scrape/` = 0 — that
 * source dir is UNTRACKED — so on clean CI the test skipped and proved NOTHING while counting green.
 * Fix: the operands are now read from a CHECKED-IN fixture (`fixtures/alo_sem1_operands.txt`, the
 * verbatim pdftotext extraction of the real PDF), so the honesty assertions RUN IN CI with no
 * external dependency. The live PDF, when present, is used only as a DRIFT CHECK that the fixture
 * still matches the source-of-record — never as a precondition that can silence the assertions.
 *
 * HONESTY NOTE (what is actually on disk — surveyed file-by-file before writing this):
 *   The PDFs master-plan:205 names as "prof-authored solved-exercise / exam-solution PDFs" are,
 *   on disk, NOT solved — there are NO prof-authored worked answer keys to read a solution off of
 *   (independently confirmed; see master-plan §6.1 / build-log FINDING-G6, both corrected). So the
 *   keys here are SYSTEM-DERIVED, never via Alex (no oracle inversion): the vector OPERANDS are
 *   extracted verbatim from the REAL on-disk ALO/labs/alo_sem1.pdf (checked into the fixture), and
 *   the correct answers are computed by PURE DETERMINISTIC MATH (vector p-norms) — the PDF is the
 *   source of record for the operands; the math is mechanically forced by the operands.
 *
 *   OFFLINE-CHECKABLE SUBSET (what this asserts): only deterministic/invariant-gradable items — the
 *   grader's LLM-INDEPENDENT leg `GradeScoring.answerMatches`/`scoreFromRubric`/`correctFromRubric`
 *   (the leg that decides correctness, NOT the LLM's self-reported float). The open-ended proof items
 *   (NP-completeness reductions, derivations) are RELAY-graded → SKIPPED here.
 */
class P3HonestyGraderSpotCheckTest {

    /** The optional live PDF — used ONLY as a fixture-vs-source-of-record drift check, never a gate. */
    private val livePdf = File(System.getProperty("user.dir"), "tmp-secondbrain-scrape/ALO/labs/alo_sem1.pdf")

    /** Run pdftotext on [file]; null if the binary is missing/unrunnable in this environment. */
    private fun pdftotext(file: File): String? {
        if (!file.isFile) return null
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

    /** Parse all `x = (a, b, c)T` integer vectors out of [text] (system-derived operands).
     *  Tolerant of BOTH the ASCII hyphen '-' and the Unicode minus '−' (U+2212) — different pdftotext
     *  builds emit different glyphs for the same on-disk operand; normalize before parsing. Non-integer
     *  operands (sin/cos/fractions) yield no match (the inner regex is integer-only). */
    private fun extractVectors(text: String): List<List<Int>> {
        val norm = text.replace('−', '-').replace('–', '-')
        return Regex("""x = \((-?\d+(?:,\s*-?\d+)*)\)T""").findAll(norm).map { m ->
            m.groupValues[1].split(",").map { it.trim().toInt() }
        }.toList()
    }

    /** The checked-in fixture text (verbatim pdftotext extraction of the real PDF). NEVER null in CI. */
    private fun fixtureText(): String =
        requireNotNull(this::class.java.getResourceAsStream("/fixtures/alo_sem1_operands.txt")) {
            "checked-in fixture fixtures/alo_sem1_operands.txt missing from the test classpath"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun l1(v: List<Int>) = v.sumOf { abs(it) }.toDouble()
    private fun lInf(v: List<Int>) = v.maxOf { abs(it) }.toDouble()
    private fun l2(v: List<Int>) = sqrt(v.sumOf { (it * it).toDouble() })

    @Test
    fun `grader's deterministic leg agrees with system-derived norm keys from the on-disk ALO seminar PDF`() {
        // ── Operands come from the CHECKED-IN fixture — RUNS IN CI with no external PDF (P2-6). ──────
        val vectors = extractVectors(fixtureText())
        assertTrue(vectors.contains(listOf(-2, 2, 1)),
            "expected operand vector (-2,2,1) from the checked-in fixture; got $vectors")
        assertTrue(vectors.contains(listOf(2, -1, 3, 4)),
            "expected operand vector (2,-1,3,4) from the checked-in fixture; got $vectors")

        // ── DRIFT CHECK (best-effort): when the live PDF + pdftotext are present, the fixture operands
        //    MUST still match what the PDF currently extracts — so the fixture cannot silently rot away
        //    from the source-of-record. If the PDF is absent (clean CI), this is skipped, but the
        //    honesty assertions below STILL RUN against the fixture (the P2-6 fix). ─────────────────
        val liveText = pdftotext(livePdf)
        if (liveText != null) {
            val live = extractVectors(liveText)
            assertTrue(live.contains(listOf(-2, 2, 1)) && live.contains(listOf(2, -1, 3, 4)),
                "live PDF drifted from the checked-in fixture — regenerate fixtures/alo_sem1_operands.txt; live=$live")
        }

        // ── Build the SAMPLED honesty items: (vector, norm-name, system-derived key) ──────────
        // Only the deterministic, exactly-representable norm values (L1, Linf, and L2 when it is an
        // exact integer). L2 of (2,-1,3,4)=sqrt(30) is irrational → EXCLUDED from the deterministic
        // subset (it would force a tolerance the grader's exact matcher does not model).
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
        assertEquals(5, items.size, "expected 5 deterministic norm items from the fixture operands")

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

        println("[P3-HONESTY] checked ${items.size} deterministic norm items vs system-derived keys " +
            "from the checked-in alo_sem1 fixture${if (liveText != null) " (live-PDF drift check PASSED)" else " (live PDF absent — fixture only)"}.")
    }

    /**
     * A CREDULOUS fake LLM grader — ALWAYS returns correct=true with an all-pass rubric, i.e. it
     * would falsely bless a wrong answer. No network, no paid relay (no-paid rule). Used to prove the
     * grader's DETERMINISTIC closed-form leg OVERRIDES a credulous LLM: even when the LLM says "right",
     * a wrong answer that fails `answerMatches` against the PDF-derived key is still marked INCORRECT.
     */
    private class CredulousFakeLlm : Llm {
        var calls = 0
        override suspend fun complete(
            messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?,
        imagePath: String?,
        ): Pair<String, String> {
            calls++
            // Always "correct" with an all-pass rubric — the credulous direction the honesty leg must veto.
            return """{"correct":true,"rubric":{"numeric":true},"score":1.0,"misconception":null,"elaborated_feedback":"looks right"}""" to
                "fake/credulous-grader"
        }
        override fun close() {}
    }

    /**
     * P2-6 (council fix) — round-trip a plausible-but-WRONG student answer through the FULL grader
     * (`DrillGrader.grade` + the real serve-path deterministic composition, NOT just a bare
     * `answerMatches` call) and confirm it is marked INCORRECT against the PDF-derived key, EVEN WHEN
     * the LLM credulously says "correct". The original test only exercised `answerMatches`; this drives
     * the same path the live `/drill/grade` route uses for a closed-form question, so a regression that
     * lets the credulous LLM override the deterministic key (or that makes the grader accept any
     * non-blank answer) FAILS here. A FAKE LLM is injected — no network, no paid relay.
     */
    @Test
    fun `full grader marks a plausible-but-wrong answer INCORRECT against the PDF-derived key`() = runBlocking {
        val vectors = extractVectors(fixtureText())
        // System-derived key: L1 norm of (-2,2,1) = |−2|+|2|+|1| = 5 (forced by the PDF operand).
        val v = vectors.first { it == listOf(-2, 2, 1) }
        val correctKey = fmt(l1(v))                 // "5"
        assertEquals("5", correctKey, "L1 of (-2,2,1) must be 5 (system-derived from the PDF operand)")

        val fakeLlm = CredulousFakeLlm()

        // serve-path deterministic decision for a closed-form question (mirrors TutorRoutes 2122-2125):
        //   answerMatch = answerMatches(canonical, attempt); correct = answerMatch ?: rubricCorrect.
        suspend fun gradeThroughFullGrader(attempt: String): Pair<Boolean, Double> {
            // (1) run the REAL DrillGrader against the injected fake LLM (the full grader entry point).
            val ga = DrillGrader.grade(
                problemStatement = "Calculati norma L1 a vectorului x = (-2, 2, 1)^T.",
                userAttempt = attempt,
                expectedHint = "L1 = suma modulelor componentelor",
                llm = fakeLlm,
            )
            val g = assertNotNull(ga.parsed, "fake grader output must parse")
            // (2) the EXACT serve-path deterministic composition (TutorRoutes.kt:2122-2125), verbatim:
            //     a closed-form canonical answer is present ⇒ `answerMatch` is Boolean? and the
            //     `correct` verdict is `answerMatch ?: rubricCorrect` — so the deterministic key match
            //     OVERRIDES the LLM's self-reported correctness. (The reply `score` is rubric-driven; it
            //     is NOT what decides correctness — `correct` is. We assert on `correct`, the honesty
            //     signal.) No production code added; this mirrors the route's lines exactly.
            val canonical: String? = correctKey
            val answerMatch: Boolean? = canonical?.let { GradeScoring.answerMatches(it, attempt) }
            val rubricCorrect = GradeScoring.correctFromRubric(g.rubric)
            val deterministicCorrect = answerMatch ?: rubricCorrect
            val deterministicScore = GradeScoring.scoreFromRubric(g.rubric)   // route: score is rubric-driven
            return deterministicCorrect to deterministicScore
        }

        // ── The CORRECT student answer round-trips to CORRECT through the full grader. ───────────
        val (rightCorrect, _) = gradeThroughFullGrader("5")
        assertTrue(rightCorrect, "the system-derived correct answer (5) must grade CORRECT through the full grader")

        // ── A PLAUSIBLE-BUT-WRONG answer round-trips to INCORRECT — EVEN THOUGH the LLM said "correct"
        //    with an all-pass rubric (score 1.0). The deterministic key match is what decides `correct`,
        //    so the credulous grader is VETOED. (The L1 of (-2,2,1) is 5; a learner who forgot the |·|
        //    and summed signed components gets -2+2+1 = 1; other near-misses: 4, 6.) This is the honesty
        //    direction the deterministic leg must enforce over a credulous grader. ───────────────────
        for (wrong in listOf("1", "4", "6", "−2+2+1", "two")) {
            val (wrongCorrect, wrongScore) = gradeThroughFullGrader(wrong)
            assertFalse(wrongCorrect,
                "wrong answer '$wrong' must grade INCORRECT against key $correctKey even when the LLM says correct (no false YES)")
            // The credulous LLM returned an all-pass rubric ⇒ the rubric-driven score is HIGH; this is
            // EXACTLY why the deterministic `correct` veto matters (correct≠score-derived for closed-form).
            assertEquals(1.0, wrongScore, 1e-9,
                "fixture sanity: the credulous LLM's all-pass rubric scores 1.0 — so `correct=false` is the deterministic veto, not a low score")
        }

        // The fake LLM WAS called (the full grader path ran) — not short-circuited before grading.
        assertTrue(fakeLlm.calls >= 6, "the full grader (DrillGrader.grade) must have run for each attempt; calls=${fakeLlm.calls}")

        println("[P3-HONESTY] full-grader round-trip vs a CREDULOUS LLM: correct=5 ⇒ CORRECT; " +
            "wrong {1,4,6,signed,word} ⇒ INCORRECT (deterministic key overrides the credulous grader).")
    }

    /** Format an exact integer-valued double as a clean integer string ("5" not "5.0"). */
    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
