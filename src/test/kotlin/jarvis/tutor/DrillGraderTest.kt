package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrillGraderTest {

    @Test
    fun `parseGradeJson happy path`() {
        val raw = """{"correct":true,"rubric":{"numeric":true,"mechanism":true,"justification":false},
            "score":0.66,"misconception":null,
            "elaborated_feedback":"correct number, mechanism named, but justification missing"}"""
        val r = DrillGrader.parseGradeJson(raw)
        assertNotNull(r)
        assertTrue(r.correct)
        assertEquals(0.66, r.score, 0.001)
        assertEquals(true, r.rubric["numeric"])
        assertEquals(false, r.rubric["justification"])
        assertNull(r.misconception)
    }

    @Test
    fun `parseGradeJson with misconception`() {
        val raw = """{"correct":false,"rubric":{"numeric":false,"mechanism":false,"justification":false},
            "score":0.0,"misconception":"L2_ESTIMATOR_CONFUSION",
            "elaborated_feedback":"you computed the mean"}"""
        val r = DrillGrader.parseGradeJson(raw)
        assertNotNull(r)
        assertFalse(r.correct)
        assertEquals("L2_ESTIMATOR_CONFUSION", r.misconception)
    }

    @Test
    fun `parseGradeJson returns null on malformed`() {
        assertNull(DrillGrader.parseGradeJson("not json"))
        assertNull(DrillGrader.parseGradeJson("{}"))
    }

    @Test
    fun `parseGradeJson strips code-fence wrappers from LLM output`() {
        val raw = """```json
{"correct":true,"rubric":{"uses_rlaplace":true,"n_eq_10000":true,"hist_overlay_pdf":true,"iterates_b":true},
"score":1.0,"misconception":null,"elaborated_feedback":"complete"}
```"""
        val r = DrillGrader.parseGradeJson(raw)
        assertNotNull(r)
        assertTrue(r.correct)
        assertEquals(true, r.rubric["uses_rlaplace"])
        assertEquals(true, r.rubric["iterates_b"])
        assertEquals(1.0, r.score, 0.001)
    }

    @Test
    fun `parseGradeJson handles dynamic rubric keys for code grading`() {
        val raw = """{"correct":false,
            "rubric":{"uses_rlaplace":true,"n_eq_10000":false,"hist_overlay_pdf":false,"iterates_b":true},
            "score":0.5,"misconception":"OFF_BY_TEN",
            "elaborated_feedback":"good shape but n is 1000 not 10000"}"""
        val r = DrillGrader.parseGradeJson(raw)
        assertNotNull(r)
        assertFalse(r.correct)
        assertEquals(0.5, r.score, 0.001)
        assertEquals(false, r.rubric["n_eq_10000"])
        assertEquals(true, r.rubric["uses_rlaplace"])
        assertEquals(4, r.rubric.size, "all 4 rubric items present")
        assertEquals("OFF_BY_TEN", r.misconception)
    }
}
