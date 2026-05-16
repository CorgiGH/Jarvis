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
    fun `parseGradeJson handles missing misconception as null`() {
        val raw = """{"correct":true,"rubric":{"a":true,"b":true},"score":1.0,
            "misconception":"null","elaborated_feedback":"ok"}"""
        val r = DrillGrader.parseGradeJson(raw)
        assertNotNull(r)
        assertNull(r.misconception, "literal string 'null' should map to null misconception")
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

    // Task A.2 — grade() now returns GradeAttempt carrying raw LLM output so
    // the caller can populate envelope llm_output_raw_truncated even when
    // parsing fails. Plan: docs/superpowers/plans/2026-05-16-grader-tripwire-reseed.md
    // Council 1778881174 verdict FLAWED — Layer 3 (raw output capture) is
    // the load-bearing observability fix. Without it, parse_error envelopes
    // tell us nothing about what the LLM actually returned.
    @Test
    fun `grade returns GradeAttempt carrying raw output on success`() = kotlinx.coroutines.runBlocking {
        val fakeLlm = object : jarvis.Llm {
            override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int): Pair<String, String> {
                return """{"correct":true,"rubric":{"numeric":true,"mechanism":true,"justification":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}""" to "fake/model"
            }
        }
        val attempt = DrillGrader.grade(
            problemStatement = "p", userAttempt = "a", expectedHint = "h",
            llm = fakeLlm,
        )
        assertNotNull(attempt.parsed)
        assertTrue(attempt.rawOutput.contains("\"correct\":true"))
        assertEquals("fake/model", attempt.modelResolved)
    }

    @Test
    fun `grade returns GradeAttempt carrying raw output even on parse fail`() = kotlinx.coroutines.runBlocking {
        val fakeLlm = object : jarvis.Llm {
            override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int): Pair<String, String> {
                return "Sure here is your grade I think it is good" to "fake/model"
            }
        }
        val attempt = DrillGrader.grade(
            problemStatement = "p", userAttempt = "a", expectedHint = "h",
            llm = fakeLlm,
        )
        assertNull(attempt.parsed)
        assertEquals("Sure here is your grade I think it is good", attempt.rawOutput)
    }
}
