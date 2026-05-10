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
}
