package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradeScoringTest {
    @Test fun `score is fraction of rubric items passed`() {
        assertEquals(0.5, GradeScoring.scoreFromRubric(mapOf("a" to true, "b" to false)), 1e-9)
        assertEquals(1.0, GradeScoring.scoreFromRubric(mapOf("a" to true, "b" to true)), 1e-9)
    }

    @Test fun `empty rubric scores zero, not crash`() {
        assertEquals(0.0, GradeScoring.scoreFromRubric(emptyMap()), 1e-9)
    }

    @Test fun `correct iff every rubric item passed`() {
        assertTrue(GradeScoring.correctFromRubric(mapOf("a" to true, "b" to true)))
        assertFalse(GradeScoring.correctFromRubric(mapOf("a" to true, "b" to false)))
        assertFalse(GradeScoring.correctFromRubric(emptyMap()))
    }

    @Test fun `confident when llm correct flag agrees with its rubric`() {
        val coherentPass = GradeResult(true, mapOf("a" to true, "b" to true), 1.0, null, "ok")
        val coherentFail = GradeResult(false, mapOf("a" to true, "b" to false), 0.5, "m", "fb")
        assertTrue(GradeScoring.isConfident(coherentPass))
        assertTrue(GradeScoring.isConfident(coherentFail))
    }

    @Test fun `not confident when llm correct flag contradicts its rubric`() {
        val saysCorrectButItemFalse = GradeResult(true, mapOf("a" to true, "b" to false), 0.9, null, "fb")
        val saysWrongButAllPass = GradeResult(false, mapOf("a" to true, "b" to true), 0.2, "m", "fb")
        assertFalse(GradeScoring.isConfident(saysCorrectButItemFalse))
        assertFalse(GradeScoring.isConfident(saysWrongButAllPass))
    }
}
