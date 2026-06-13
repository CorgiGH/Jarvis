package jarvis.tutor.grader

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Plan-6 Task 3 Step 9 — INV-6.3 (LLM never alone), double-covered:
 *   - TABLE level: every chain shape [GraderRouting] can emit has a non-LLM first leg
 *     and is never `[LLM_JUDGE]` alone;
 *   - TYPE level: the [GraderChain.of] builder throws on the forbidden shapes.
 */
class LlmNeverAloneTest {

    private class FakeLeg(override val kind: GraderLegKind) : GraderLeg {
        override suspend fun grade(input: GradeInput): LegOutcome =
            LegOutcome.Decided(correct = true, score = 1.0, feedbackRo = "ok")
    }

    @Test
    fun `every routing-table chain shape has a non-LLM first leg and is never LLM-alone`() {
        val shapes = GraderRouting.allChainShapes()
        assertTrue(shapes.isNotEmpty(), "the table must produce at least one chain shape")
        for (shape in shapes) {
            assertTrue(shape.isNotEmpty(), "no empty chain shape allowed")
            assertTrue(
                shape.first() != GraderLegKind.LLM_JUDGE,
                "INV-6.1: first leg must be non-LLM, got $shape",
            )
            assertTrue(
                !(shape.size == 1 && shape.first() == GraderLegKind.LLM_JUDGE),
                "INV-6.3: chain must never be [LLM_JUDGE] alone, got $shape",
            )
        }
    }

    @Test
    fun `every routing-table chain shape is buildable into a real GraderChain`() {
        // Type-level + table-level cross-check: each emitted shape, instantiated with fake
        // legs, must survive GraderChain.of's require()s (proving the table and the builder agree).
        for (shape in GraderRouting.allChainShapes()) {
            val chain = GraderChain.of(shape.map { FakeLeg(it) })
            assertTrue(chain.legKinds.first() != GraderLegKind.LLM_JUDGE)
        }
    }

    @Test
    fun `the builder rejects the LLM-only and LLM-first shapes (type-level)`() {
        assertFailsWith<IllegalArgumentException> {
            GraderChain.of(listOf(FakeLeg(GraderLegKind.LLM_JUDGE)))
        }
        assertFailsWith<IllegalArgumentException> {
            GraderChain.of(listOf(FakeLeg(GraderLegKind.LLM_JUDGE), FakeLeg(GraderLegKind.RUBRIC)))
        }
        assertFailsWith<IllegalArgumentException> {
            GraderChain.of(emptyList())
        }
    }
}
