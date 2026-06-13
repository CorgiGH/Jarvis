package jarvis.tutor.grader

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Plan-6 Task 3 Step 1 — the chain laws (INV-6.1 / INV-6.3) + deferral fall-through.
 *
 * Uses a scripted [FakeLeg] so the chain mechanics are tested in isolation from the
 * real leg implementations (oracle/rubric/execution/llm).
 */
class GraderChainTest {

    /** A leg with a fixed kind that either DECIDES or DEFERS, scriptable per test. */
    private class FakeLeg(
        override val kind: GraderLegKind,
        private val outcome: LegOutcome,
    ) : GraderLeg {
        override suspend fun grade(input: GradeInput): LegOutcome = outcome
    }

    private fun decide(score: Double = 1.0, correct: Boolean = true) =
        LegOutcome.Decided(correct = correct, score = score, feedbackRo = "decis")

    private val defer = LegOutcome.Defer("not applicable")

    @Test
    fun `of with LLM_JUDGE alone throws (INV-6_3)`() {
        assertFailsWith<IllegalArgumentException> {
            GraderChain.of(listOf(FakeLeg(GraderLegKind.LLM_JUDGE, decide())))
        }
    }

    @Test
    fun `of with first leg LLM_JUDGE throws (INV-6_1)`() {
        assertFailsWith<IllegalArgumentException> {
            GraderChain.of(
                listOf(
                    FakeLeg(GraderLegKind.LLM_JUDGE, decide()),
                    FakeLeg(GraderLegKind.RUBRIC, decide()),
                ),
            )
        }
    }

    @Test
    fun `of with empty legs throws`() {
        assertFailsWith<IllegalArgumentException> { GraderChain.of(emptyList()) }
    }

    @Test
    fun `a non-LLM first leg is accepted`() {
        val chain = GraderChain.of(
            listOf(
                FakeLeg(GraderLegKind.RUBRIC, decide()),
                FakeLeg(GraderLegKind.LLM_JUDGE, decide()),
            ),
        )
        assertEquals(listOf(GraderLegKind.RUBRIC, GraderLegKind.LLM_JUDGE), chain.legKinds)
    }

    @Test
    fun `first leg that decides wins and is reported in decidedBy`() = runBlocking {
        val chain = GraderChain.of(
            listOf(
                FakeLeg(GraderLegKind.NUMERIC_ORACLE, decide(score = 1.0, correct = true)),
                FakeLeg(GraderLegKind.LLM_JUDGE, decide(score = 0.0, correct = false)),
            ),
        )
        val result = chain.grade(GradeInput())
        assertEquals(GraderLegKind.NUMERIC_ORACLE, result.decidedBy)
        assertTrue(result.correct)
        assertEquals(1.0, result.score, 1e-9)
        assertTrue(result.degradedLegs.isEmpty(), "no leg deferred before the decider")
    }

    @Test
    fun `a deferring first leg falls through and records the degradation`() = runBlocking {
        val chain = GraderChain.of(
            listOf(
                FakeLeg(GraderLegKind.EXECUTION, defer),
                FakeLeg(GraderLegKind.RUBRIC, decide(score = 0.5, correct = false)),
                FakeLeg(GraderLegKind.LLM_JUDGE, decide(score = 1.0, correct = true)),
            ),
        )
        val result = chain.grade(GradeInput())
        assertEquals(GraderLegKind.RUBRIC, result.decidedBy)
        assertEquals(listOf(GraderLegKind.EXECUTION), result.degradedLegs)
        assertEquals(0.5, result.score, 1e-9)
    }

    @Test
    fun `multiple deferrals before the decider are all recorded`() = runBlocking {
        val chain = GraderChain.of(
            listOf(
                FakeLeg(GraderLegKind.NUMERIC_ORACLE, defer),
                FakeLeg(GraderLegKind.EXECUTION, defer),
                FakeLeg(GraderLegKind.RUBRIC, defer),
                FakeLeg(GraderLegKind.LLM_JUDGE, decide()),
            ),
        )
        val result = chain.grade(GradeInput())
        assertEquals(GraderLegKind.LLM_JUDGE, result.decidedBy)
        assertEquals(
            listOf(GraderLegKind.NUMERIC_ORACLE, GraderLegKind.EXECUTION, GraderLegKind.RUBRIC),
            result.degradedLegs,
        )
    }
}
