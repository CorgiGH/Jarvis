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
            override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int, responseFormat: String?): Pair<String, String> {
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
            override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int, responseFormat: String?): Pair<String, String> {
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

    // Task A.3 — envelope-build block at TutorRoutes.kt:1669+ must populate
    // TutorEvent.llm_output_raw_truncated from GradeAttempt.rawOutput when
    // status == "parse_error". The conditional helper below mirrors the
    // exact wiring in TutorRoutes so the three status branches (ok /
    // parse_error / error=attempt-null) can be unit-tested without an LLM
    // injection seam in the HTTP route. The HTTP-level path is already
    // covered by `POST drill grade writes a redacted TutorEvent` in
    // TutorRoutesTest (which exercises the error branch via no-API-key).
    private fun envelopeRawTruncated(status: String, attempt: GradeAttempt?): String? =
        if (status == "parse_error" && attempt?.rawOutput != null)
            attempt.rawOutput.take(1500)
        else null

    @Test
    fun `envelope raw-truncated populated only on parse_error`() {
        val garbage = "Sure here is your grade"
        val attempt = GradeAttempt(parsed = null, rawOutput = garbage, modelResolved = "fake/model")
        assertEquals(garbage, envelopeRawTruncated("parse_error", attempt))
        assertEquals(null, envelopeRawTruncated("ok", attempt),
            "rendered reply lives in llm_output_full on ok; raw stays null")
        assertEquals(null, envelopeRawTruncated("error", null),
            "no GradeAttempt on LLM-exception path; raw stays null")
    }

    @Test
    fun `envelope raw-truncated capped at 1500 chars`() {
        val big = "x".repeat(3000)
        val attempt = GradeAttempt(parsed = null, rawOutput = big, modelResolved = "fake/model")
        val captured = envelopeRawTruncated("parse_error", attempt)
        assertNotNull(captured)
        assertEquals(1500, captured!!.length, "truncation hard-cap from plan + council 1778881174")
    }

    // Task A.3 — envelope-build block at TutorRoutes.kt:1669+ must populate
    // TutorEvent.llm_output_raw_truncated from GradeAttempt.rawOutput when
    // status == "parse_error". This test validates the serialization-level
    // contract: the field round-trips through TutorEvent encode/decode.
    // The A.1 schema change is already in place — this is end-to-end
    // validation, not red-then-green TDD.
    @Test
    fun `parse_error envelope carries llm_output_raw_truncated`() {
        val evt = TutorEvent(
            event_type = "drill_grade",
            event_id = "test1",
            ts_utc = "2026-05-16T00:00:00Z",
            task_id = null,
            session_id = "s1",
            prompt_template_id = null,
            system_prompt_sha256 = null,
            retrieved_context_summary = null,
            llm_output_full = "{...rendered reply...}",
            model_resolved = "fake/model",
            tokens_in = null,
            tokens_out = null,
            latency_ms = null,
            llm_output_raw_truncated = "Sure here is your grade",
            status = "parse_error",
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val encoded = json.encodeToString(TutorEvent.serializer(), evt)
        assertTrue(encoded.contains("\"llm_output_raw_truncated\":\"Sure here is your grade\""))
        val decoded = json.decodeFromString(TutorEvent.serializer(), encoded)
        assertEquals("Sure here is your grade", decoded.llm_output_raw_truncated)
    }

    // Task A.6 — code-grading path runs longer (multi-line rubric_chip_text +
    // elaborated_feedback referencing specific code lines), so the 600-token
    // cap was triggering JSON truncation. Council 1778881174 Risk Analyst
    // flagged maxTokens=600 as probable root cause of truncated JSON in
    // code-grade responses. Split: 1200 for code, 600 stays for text.
    // Plan: docs/superpowers/plans/2026-05-16-grader-tripwire-reseed.md (A.6)
    @Test
    fun `grade uses maxTokens=1200 for code grading path`() = kotlinx.coroutines.runBlocking {
        var observedMaxTokens = -1
        val capturingLlm = object : jarvis.Llm {
            override suspend fun complete(
                messages: List<jarvis.ChatMessage>,
                maxTokens: Int,
                responseFormat: String?,
            ): Pair<String, String> {
                observedMaxTokens = maxTokens
                return """{"correct":true,"rubric":{"foo":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}""" to "fake/model"
            }
        }
        DrillGrader.grade(
            problemStatement = "p", userAttempt = "a", expectedHint = "h",
            llm = capturingLlm, language = "r", referenceSolution = "x", rubricItems = listOf("foo"),
        )
        assertEquals(1200, observedMaxTokens)
    }

    @Test
    fun `grade keeps maxTokens=600 for text grading path`() = kotlinx.coroutines.runBlocking {
        var observedMaxTokens = -1
        val capturingLlm = object : jarvis.Llm {
            override suspend fun complete(
                messages: List<jarvis.ChatMessage>,
                maxTokens: Int,
                responseFormat: String?,
            ): Pair<String, String> {
                observedMaxTokens = maxTokens
                return """{"correct":true,"rubric":{"numeric":true,"mechanism":true,"justification":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}""" to "fake/model"
            }
        }
        DrillGrader.grade(
            problemStatement = "p", userAttempt = "a", expectedHint = "h",
            llm = capturingLlm, language = null,
        )
        assertEquals(600, observedMaxTokens)
    }
}
