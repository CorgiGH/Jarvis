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
}
