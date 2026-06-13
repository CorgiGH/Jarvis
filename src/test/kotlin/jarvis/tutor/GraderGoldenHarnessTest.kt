package jarvis.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plan-4a Task 5 (spec §9.2, §0.9E) — the grader-eval harness, DETERMINISTIC LEG.
 * Extended in Plan 6 Task 6 (§0.9-E) with the LLM-JUDGE recorded-replay leg.
 *
 * Loads golden JSON fixtures from:
 *   src/test/resources/fixtures/grader-golden/{subject}/grade-scoring/ — 12 items (plan 4a)
 *   src/test/resources/fixtures/grader-golden/{subject}/llm-judge/ — ≥6 items (plan 6)
 *
 * LLM-judge leg: recorded raw LLM outputs replayed through the REAL parseGradeJson + GradeScoring
 * (deterministic, zero live LLM calls in CI, no-paid-APIs compliant — R-6-Q5 ratified).
 * The fixture records ONE captured model output, computes the expected values from the REAL
 * scorers, and freezes them — pinning parser+scorer behavior, not model behavior.
 *
 * Per-leg discovery guards: grade-scoring >= 12, llm-judge >= 6 (fix-claim discipline —
 * ensures the harness cannot silently pass with 0 items due to a path/glob regression).
 *
 * INV-6.4 statement: both golden legs run inside :check → CI runs them on every merge →
 * "goldens green per grader per subject before any grader change merges" is structurally
 * satisfied. See fixtures/grader-golden/_README.md for the shape documentation.
 */
class GraderGoldenHarnessTest {

    // ---- grade-scoring leg (plan 4a, unchanged) ----

    @Serializable data class GoldenInput(val rubric: Map<String, Boolean>)
    @Serializable data class GoldenExpected(val score: Double, val correct: Boolean)
    @Serializable data class Golden(
        val grader: String,
        val subject: String,
        val id: String,
        val input: GoldenInput,
        val expected: GoldenExpected,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("graderGoldenCases")
    fun `GradeScoring matches the golden expected score and correctness`(name: String, g: Golden) {
        assertEquals("grade-scoring", g.grader, "this harness only runs grade-scoring goldens: ${g.id}")
        val actualScore = GradeScoring.scoreFromRubric(g.input.rubric)
        val actualCorrect = GradeScoring.correctFromRubric(g.input.rubric)
        assertEquals(g.expected.score, actualScore, 1e-9, "score mismatch for golden '${g.id}'")
        assertEquals(g.expected.correct, actualCorrect, "correct mismatch for golden '${g.id}'")
    }

    // ---- llm-judge leg (plan 6 task 6, R-6-Q5) ----

    /**
     * LLM-judge golden shape (§0.9-E):
     * {
     *   "grader": "llm-judge", "subject": "...", "id": "...",
     *   "input": { "raw_llm_output": "<verbatim recorded model text>" },
     *   "expected": {
     *     "parsed": true,
     *     "score": <Double>,
     *     "correct": <Boolean>,
     *     "misconception": <String?>,
     *     "confident": <Boolean>
     *   }
     * }
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("llmJudgeGoldenCases")
    fun `LLM-judge parseGradeJson and GradeScoring match recorded-replay golden`(
        name: String,
        id: String,
        rawLlmOutput: String,
        expectedParsed: Boolean,
        expectedScore: Double,
        expectedCorrect: Boolean,
        expectedMisconception: String?,
        expectedConfident: Boolean,
    ) {
        val result = DrillGrader.parseGradeJson(rawLlmOutput)
        assertEquals(expectedParsed, result != null,
            "parsed mismatch for golden '$id': got ${if (result != null) "parsed" else "null"}")

        if (expectedParsed) {
            assertNotNull(result, "expected parsed GradeResult for golden '$id'")
            assertEquals(
                expectedScore,
                GradeScoring.scoreFromRubric(result.rubric),
                1e-9,
                "score mismatch for golden '$id'"
            )
            assertEquals(
                expectedCorrect,
                GradeScoring.correctFromRubric(result.rubric),
                "correct mismatch for golden '$id'"
            )
            assertEquals(
                expectedMisconception,
                result.misconception,
                "misconception mismatch for golden '$id'"
            )
            assertEquals(
                expectedConfident,
                GradeScoring.isConfident(result),
                "confident mismatch for golden '$id'"
            )
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Resolve the fixtures dir off the compiled-test classpath (works in `gradle :check`). */
        private fun goldenRoot(): File {
            val url = requireNotNull(
                GraderGoldenHarnessTest::class.java.getResource("/fixtures/grader-golden"),
            ) { "fixtures/grader-golden missing from the test classpath — check src/test/resources" }
            require(url.protocol == "file") {
                "grader-golden fixtures must resolve to a file: URL for directory walking (got '${url.protocol}': $url). " +
                    "If resources moved into a JAR, switch to getResourceAsStream over a known id list."
            }
            return File(url.toURI())
        }

        @JvmStatic
        fun graderGoldenCases(): List<org.junit.jupiter.params.provider.Arguments> {
            val files = goldenRoot()
                .walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.parentFile.name == "grade-scoring" }
                .sortedBy { it.path }
                .toList()
            require(files.size >= 12) {
                "expected >= 12 grade-scoring golden fixtures, found ${files.size}: ${files.map { it.name }}"
            }
            return files.map { f ->
                val g = json.decodeFromString(Golden.serializer(), f.readText())
                org.junit.jupiter.params.provider.Arguments.of(g.id, g)
            }
        }

        @JvmStatic
        fun llmJudgeGoldenCases(): List<org.junit.jupiter.params.provider.Arguments> {
            val files = goldenRoot()
                .walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.parentFile.name == "llm-judge" }
                .sortedBy { it.path }
                .toList()
            // Discovery guard: prevents silent 0-case green (fix-claim discipline).
            require(files.size >= 6) {
                "expected >= 6 llm-judge golden fixtures, found ${files.size}: ${files.map { it.name }}"
            }
            return files.map { f ->
                val obj = json.parseToJsonElement(f.readText()).jsonObject

                val id = obj["id"]!!.jsonPrimitive.content
                val rawLlmOutput = obj["input"]!!.jsonObject["raw_llm_output"]!!.jsonPrimitive.content

                val expectedObj = obj["expected"]!!.jsonObject
                val expectedParsed = expectedObj["parsed"]!!.jsonPrimitive.content.toBoolean()
                val expectedScore = expectedObj["score"]!!.jsonPrimitive.content.toDouble()
                val expectedCorrect = expectedObj["correct"]!!.jsonPrimitive.content.toBoolean()
                val miscElement: JsonElement? = expectedObj["misconception"]
                val expectedMisconception: String? = when {
                    miscElement == null || miscElement is JsonNull -> null
                    miscElement is JsonPrimitive && miscElement.isString -> miscElement.content
                    else -> null
                }
                val expectedConfident = expectedObj["confident"]!!.jsonPrimitive.content.toBoolean()

                org.junit.jupiter.params.provider.Arguments.of(
                    id,
                    id,
                    rawLlmOutput,
                    expectedParsed,
                    expectedScore,
                    expectedCorrect,
                    expectedMisconception,
                    expectedConfident,
                )
            }
        }
    }
}
