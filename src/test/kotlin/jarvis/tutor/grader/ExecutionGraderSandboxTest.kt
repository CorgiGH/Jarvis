package jarvis.tutor.grader

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-6 Task 5 — the execution grader sandbox (R-6-Q1, §0.9-C) + INV-6.2 known-good / known-bad
 * mutant pairs derived from the Task-2 real-corpus problems.
 *
 * STRICT: a missing toolchain is RED on PC + CI (the required-green environments). The env override
 * `JARVIS_EXEC_GRADER_OPTIONAL=1` downgrades a missing toolchain to skip-with-log (VPS only — never
 * set in CI). The execution leg itself NEVER throws on a missing toolchain (it defers); the STRICT-RED
 * here is a TEST policy, asserted via [requireOrFailStrict].
 */
class ExecutionGraderSandboxTest {

    private val grader = ExecutionGrader()

    // ─── Step 1: sandbox laws ─────────────────────────────────────────────────────

    @Test
    fun `KDoc carries the verbatim no-network limitation sentence (documented-limitation, grep-asserted)`() {
        // §0.9-C: the documented-limitation requirement is itself machine-checked. We assert the
        // source file's KDoc contains the no-network limitation statement.
        val src = locateSourceFile("ExecutionGrader.kt")
        val text = src.readText()
        assertTrue(
            text.contains("No-network is NOT guaranteed"),
            "ExecutionGrader.kt KDoc must carry the verbatim no-network limitation sentence (§0.9-C)",
        )
    }

    @Test
    fun `temp dir is created fresh and deleted even after a run (hygiene)`() {
        Assumptions.assumeTrue(pythonAvailable(), "python not available for the hygiene probe")
        val before = tempDirsMatching()
        val result = grader.run("print('hi')\n", "python")
        assertTrue(result.available)
        val after = tempDirsMatching()
        // No jarvis-exec-* temp dir should leak after the run completes.
        assertEquals(
            before, after,
            "execution sandbox leaked a temp dir (before=$before after=$after) — finally cleanup failed",
        )
    }

    @Test
    fun `a sleep-forever program times out within the limit and the temp dir is still cleaned`() {
        Assumptions.assumeTrue(pythonAvailable(), "python not available for the timeout probe")
        // A short run timeout so the test is fast; the program loops forever.
        val fast = ExecutionGrader(runTimeoutSeconds = 2L)
        val before = tempDirsMatching()
        val result = fast.run("while True:\n    pass\n", "python")
        assertTrue(result.timedOut, "an infinite loop must report timed_out=true")
        val after = tempDirsMatching()
        assertEquals(before, after, "the temp dir must be cleaned even after a timeout kill")
    }

    @Test
    fun `output bounded at 64 KiB with a truncation marker`() {
        Assumptions.assumeTrue(pythonAvailable(), "python not available for the bounded-output probe")
        // Print well over 64 KiB.
        val result = grader.run("print('x' * 200000)\n", "python")
        assertTrue(result.available && result.compiled)
        assertTrue(result.truncated, "over-64-KiB output must set truncated=true")
        assertTrue(
            result.stdout.contains("output truncated at 64 KiB"),
            "truncated output must carry the marker",
        )
        assertTrue(
            result.stdout.toByteArray().size <= ExecutionGrader.MAX_OUTPUT_BYTES + ExecutionGrader.TRUNCATION_MARKER.toByteArray().size + 4,
            "bounded output must not exceed 64 KiB + marker",
        )
    }

    @Test
    fun `a compile error reports compiled=false with a stderr excerpt`() {
        requireOrFailStrict("cpp", CppRunner().probe())
        Assumptions.assumeTrue(CppRunner().probe().available, "g++ not available")
        val result = grader.run("int main() { this is not valid c++ ; }\n", "cpp")
        assertFalse(result.compiled, "a syntactically invalid program must not compile")
        assertTrue(result.stderr.isNotBlank(), "a compile failure must surface a stderr excerpt")
    }

    @Test
    fun `a missing runner language makes the leg DEFER, never throw`() = runBlocking {
        // bash has NO runner (R-6-Q8) — the leg must defer (routed to rubric elsewhere).
        val out = grader.grade(GradeInput(source = "echo hi", language = "bash", expectedStdout = "hi"))
        assertTrue(out is LegOutcome.Defer, "no-runner language → defer")
    }

    @Test
    fun `no source makes the leg DEFER`() = runBlocking {
        val out = grader.grade(GradeInput(source = null, language = "python", expectedStdout = "x"))
        assertTrue(out is LegOutcome.Defer)
    }

    // ─── Step 4: INV-6.2 — known-good passes, known-bad mutant fails, per language ─

    @ParameterizedTest(name = "INV-6.2 {0}")
    @MethodSource("executionGoldenCases")
    fun `INV-6_2 execution golden — good passes, mutant fails`(name: String, g: ExecGolden) {
        val runner = LanguageRunners.forLanguage(g.language)
            ?: error("fixture ${g.id}: no runner for language '${g.language}'")
        requireOrFailStrict(g.language, runner.probe())
        // If we reach here in OPTIONAL mode with a missing toolchain, skip-with-log.
        if (!runner.probe().available) {
            Assumptions.assumeTrue(false, "[OPTIONAL] ${g.language} toolchain missing — skip ${g.id}")
        }

        val result = grader.run(g.input.source, g.language)
        assertTrue(
            result.available,
            "${g.id}: runner reported unavailable at run time — ${result.reason}",
        )
        assertTrue(
            result.compiled,
            "${g.id}: program failed to compile — stderr: ${result.stderr.take(400)}",
        )
        assertFalse(result.timedOut, "${g.id}: unexpected timeout")
        val pass = normalize(result.stdout) == normalize(g.input.expectedStdout)
        assertEquals(
            g.expected.pass, pass,
            "${g.id}: expected pass=${g.expected.pass} but got pass=$pass (stdout='${result.stdout.trim()}', expected='${g.input.expectedStdout}')",
        )
    }

    // ─── helpers ──────────────────────────────────────────────────────────────────

    /** STRICT policy: a missing toolchain is RED on PC/CI; JARVIS_EXEC_GRADER_OPTIONAL=1 downgrades to skip. */
    private fun requireOrFailStrict(language: String, availability: RunnerAvailability) {
        if (availability.available) return
        val optional = System.getenv("JARVIS_EXEC_GRADER_OPTIONAL") == "1"
        if (optional) {
            // skip-with-log (VPS only)
            Assumptions.assumeTrue(false, "[OPTIONAL] $language toolchain missing: ${availability.reason}")
        } else {
            // STRICT: RED, not skip.
            error("STRICT: $language toolchain unavailable on a required-green environment (PC/CI): ${availability.reason}. Set JARVIS_EXEC_GRADER_OPTIONAL=1 only on VPS.")
        }
    }

    private fun pythonAvailable(): Boolean = PythonRunner().probe().available

    private fun tempDirsMatching(): Set<String> {
        val tmp = File(System.getProperty("java.io.tmpdir"))
        return tmp.listFiles { f -> f.isDirectory && f.name.startsWith("jarvis-exec-") }
            .orEmpty()
            .map { it.name }
            .toSet()
    }

    private fun normalize(s: String): String =
        s.replace("\r\n", "\n").trimEnd().lines().joinToString("\n") { it.trimEnd() }.trim()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        data class ExecInput(val source: String, val stdin: String? = null, val expected_stdout: String) {
            val expectedStdout: String get() = expected_stdout
        }

        @Serializable
        data class ExecExpected(val pass: Boolean)

        @Serializable
        data class ExecGolden(
            val grader: String,
            val subject: String,
            val id: String,
            val language: String,
            val input: ExecInput,
            val expected: ExecExpected,
            val corpus_note: String = "",
        )

        private fun goldenRoot(): File {
            val url = requireNotNull(
                ExecutionGraderSandboxTest::class.java.getResource("/fixtures/grader-golden"),
            ) { "fixtures/grader-golden missing from the test classpath" }
            require(url.protocol == "file") {
                "grader-golden fixtures must resolve to a file: URL (got '${url.protocol}': $url)"
            }
            return File(url.toURI())
        }

        @JvmStatic
        fun executionGoldenCases(): List<Arguments> {
            val files = goldenRoot()
                .walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.parentFile.name == "execution-grader" }
                .sortedBy { it.path }
                .toList()
            // Discovery guard: the 4-runner INV-6.2 pairs must actually be found (a path regression
            // would make this suite silently 0-case green — fix-claim discipline).
            require(files.size >= 8) {
                "expected >= 8 execution-grader golden fixtures (good+bad per R/Python/C++/Alk), found ${files.size}: ${files.map { it.name }}"
            }
            val langs = mutableSetOf<String>()
            val out = files.map { f ->
                val g = json.decodeFromString(ExecGolden.serializer(), f.readText())
                assertEquals("execution", g.grader, "non-execution golden in execution-grader dir: ${g.id}")
                langs.add(g.language)
                Arguments.of(g.id, g)
            }
            // Every required runner must be covered (INV-6.2 verbatim: per R/Python/C++/Alk).
            for (required in listOf("r", "python", "cpp", "alk")) {
                require(required in langs) {
                    "INV-6.2 requires an execution golden for '$required'; covered languages: $langs"
                }
            }
            return out
        }

        /** Locate a main-source .kt file for the KDoc grep assertion (build dir → source tree). */
        private fun locateSourceFile(fileName: String): File {
            // The compiled-test classpath resolves to build/; walk up to the module root, then into src.
            val rootCandidates = listOf(
                File("src/main/kotlin/jarvis/tutor/grader/$fileName"),
                File("../jarvis-kotlin-lane-b/src/main/kotlin/jarvis/tutor/grader/$fileName"),
            )
            rootCandidates.firstOrNull { it.exists() }?.let { return it }
            error("could not locate $fileName from the working dir ${File(".").absolutePath}")
        }
    }
}
