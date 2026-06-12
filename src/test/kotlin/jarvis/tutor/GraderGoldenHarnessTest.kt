package jarvis.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import kotlin.test.assertEquals

/**
 * Plan-4a Task 5 (spec §9.2, §0.9E) — the grader-eval harness skeleton, DETERMINISTIC LEG ONLY.
 *
 * Loads golden JSON fixtures from src/test/resources/fixtures/grader-golden/{subject}/grade-scoring/,
 * runs each rubric through the REAL GradeScoring object (the LLM-independent leg that DECIDES the score
 * and correctness — the LLM's self-reported float is never trusted), and asserts score+correct match
 * the golden expected. JUnit 5 @ParameterizedTest over classpath resources; runs inside `:check`.
 *
 * SCOPE: only grader == "grade-scoring". LLM-judge + execution-grader golden sets are Plan 6; their
 * dir convention is reserved (see fixtures/grader-golden/_README.md) but no items ship here.
 *
 * The 12 golden items span PA/ALO/PS rubric shapes incl. the edge cases the real scorer branches on:
 * empty rubric (→ 0.0 / not-correct), all-false, single-item, and the all-or-nothing semantic
 * (one false ⇒ not-correct even at a high partial score — GradeScoring.correctFromRubric's all-pass rule).
 */
class GraderGoldenHarnessTest {

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

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Resolve the fixtures dir off the compiled-test classpath (works in `gradle :check`). */
        private fun goldenRoot(): File {
            val url = requireNotNull(
                GraderGoldenHarnessTest::class.java.getResource("/fixtures/grader-golden"),
            ) { "fixtures/grader-golden missing from the test classpath — check src/test/resources" }
            // Gradle :test copies resources to build/resources/test (a file: URL); guard the
            // directory-walk against a future JAR-packaged classpath (jar: URL → File(uri) throws).
            // finding #9: explicit, actionable failure instead of a NoSuchFileException.
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
            // Guard: the 12 goldens must actually be discovered (a glob/path regression would make this
            // suite silently 0-case green — fix-claim discipline: the harness must run real items).
            require(files.size >= 12) {
                "expected >= 12 grade-scoring golden fixtures, found ${files.size}: ${files.map { it.name }}"
            }
            return files.map { f ->
                val g = json.decodeFromString(Golden.serializer(), f.readText())
                org.junit.jupiter.params.provider.Arguments.of(g.id, g)
            }
        }
    }
}
