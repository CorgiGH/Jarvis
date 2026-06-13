package jarvis.tutor.grader

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * The execution grader leg (E17, R-6-Q1, §0.9-C) — compiles + runs a student program in a
 * subprocess sandbox and decides pass/fail by comparing trimmed stdout against the problem's
 * expected stdout.
 *
 * Sandbox per §0.9-C, per run:
 *  - a FRESH temp dir (`Files.createTempDirectory("jarvis-exec-")`); the student source is
 *    written inside; the process working dir IS that temp dir,
 *  - a hard wall-clock timeout (compile <= 20s, run <= 10s) that destroys the process tree,
 *  - stdout + stderr captured bounded at 64 KiB (truncated with a marker),
 *  - the temp dir deleted in `finally` (even on timeout/crash).
 *
 * No-network is NOT guaranteed — documented limitation: this is a single-user app running on the
 * user's own machine, so the sandbox does not block outbound network access; a malicious program
 * could reach the network, which is an accepted limitation for this single-user/own-machine context.
 *
 * Toolchain self-detection ([ToolchainProbe] via [LanguageRunners]): a missing binary makes the
 * runner report `available=false`, the leg DEFERS (the chain degrades honestly to the next leg),
 * the degradation is recorded — never thrown, never silently passed.
 */
class ExecutionGrader(
    private val compileTimeoutSeconds: Long = DEFAULT_COMPILE_TIMEOUT_SECONDS,
    private val runTimeoutSeconds: Long = DEFAULT_RUN_TIMEOUT_SECONDS,
) : GraderLeg {

    override val kind: GraderLegKind = GraderLegKind.EXECUTION

    /**
     * The leg entry point. Reads [GradeInput.source], [GradeInput.language],
     * [GradeInput.expectedStdout]. DEFERS when the source/language is absent or the toolchain is
     * unavailable; otherwise runs the program and DECIDES pass/fail on exact (trimmed) stdout match.
     */
    override suspend fun grade(input: GradeInput): LegOutcome {
        val source = input.source
        if (source.isNullOrBlank()) return LegOutcome.Defer("no source program for the execution leg")
        val runner = LanguageRunners.forLanguage(input.language)
            ?: return LegOutcome.Defer("no execution runner for language '${input.language}' (routed to rubric)")
        val availability = runner.probe()
        if (!availability.available) {
            return LegOutcome.Defer("execution leg unavailable: ${availability.reason}")
        }
        val expected = input.expectedStdout
            ?: return LegOutcome.Defer("no expected stdout — execution leg cannot decide pass/fail")

        val result = run(source, input.language!!)
        if (!result.compiled) {
            return LegOutcome.Decided(
                correct = false,
                score = 0.0,
                feedbackRo = "Codul nu compilează — verifică erorile de compilare.",
            )
        }
        if (result.timedOut) {
            return LegOutcome.Decided(
                correct = false,
                score = 0.0,
                feedbackRo = "Execuția a depășit timpul limită.",
            )
        }
        val pass = normalize(result.stdout) == normalize(expected)
        return LegOutcome.Decided(
            correct = pass,
            score = if (pass) 1.0 else 0.0,
            feedbackRo = if (pass) "Ieșirea programului este corectă." else "Ieșirea programului nu corespunde rezultatului așteptat.",
        )
    }

    /**
     * Run [source] in [language] inside a fresh sandbox temp dir. Returns the raw execution
     * outcome (no grading) — used by the practice `/run` endpoint and the INV-6.2 fixture test.
     */
    fun run(source: String, language: String): ExecutionResult {
        val runner = LanguageRunners.forLanguage(language)
            ?: return ExecutionResult(
                available = false,
                compiled = false,
                timedOut = false,
                stdout = "",
                stderr = "",
                truncated = false,
                reason = "no runner for language '$language'",
            )
        val availability = runner.probe()
        if (!availability.available) {
            return ExecutionResult(
                available = false,
                compiled = false,
                timedOut = false,
                stdout = "",
                stderr = "",
                truncated = false,
                reason = availability.reason,
            )
        }

        val tempDir = Files.createTempDirectory("jarvis-exec-").toFile()
        try {
            val commands = runner.commandsFor(source, tempDir, availability.binary!!)
            File(tempDir, commands.sourceFileName).writeText(source, StandardCharsets.UTF_8)

            // Compile (if the language needs it).
            if (commands.compileCommand != null) {
                val compile = execute(commands.compileCommand, tempDir, compileTimeoutSeconds)
                if (compile.timedOut) {
                    return ExecutionResult(true, compiled = false, timedOut = true, stdout = "", stderr = compile.stderr, truncated = compile.truncated, reason = "compile timed out")
                }
                if (compile.exitCode != 0) {
                    return ExecutionResult(true, compiled = false, timedOut = false, stdout = "", stderr = compile.stderr, truncated = compile.truncated, reason = "compile failed (exit ${compile.exitCode})")
                }
            }

            // Run.
            val out = execute(commands.runCommand, tempDir, runTimeoutSeconds)
            return ExecutionResult(
                available = true,
                compiled = true,
                timedOut = out.timedOut,
                stdout = out.stdout,
                stderr = out.stderr,
                truncated = out.truncated,
                reason = if (out.timedOut) "run timed out after ${runTimeoutSeconds}s" else null,
                exitCode = out.exitCode,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Run one command list in [workDir] with a hard [timeoutSeconds] wall-clock cap.
     *
     * stdout + stderr are drained CONCURRENTLY on background threads so the OS pipe buffer never
     * fills (a program that floods stdout past the ~64 KiB pipe buffer would otherwise block on
     * write and force a false timeout). Each drainer bounds itself at [MAX_OUTPUT_BYTES].
     */
    private fun execute(command: List<String>, workDir: File, timeoutSeconds: Long): ProcOutput {
        val proc = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(false)
            .start()
        // Close stdin so a program reading stdin sees EOF instead of blocking forever.
        try {
            proc.outputStream.close()
        } catch (_: Exception) {
            // ignore
        }

        val stdoutDrainer = BoundedDrainer(proc.inputStream).also { it.start() }
        val stderrDrainer = BoundedDrainer(proc.errorStream).also { it.start() }

        return try {
            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                destroyTree(proc)
            }
            // Either the process exited (drainers will hit EOF) or we killed it (streams close →
            // EOF). Join the drainers so we capture everything that was written.
            stdoutDrainer.join(2000)
            stderrDrainer.join(2000)
            val (so, soTrunc) = stdoutDrainer.result()
            val (se, seTrunc) = stderrDrainer.result()
            ProcOutput(
                exitCode = if (finished) proc.exitValue() else -1,
                stdout = so,
                stderr = se,
                timedOut = !finished,
                truncated = soTrunc || seTrunc,
            )
        } catch (e: Exception) {
            destroyTree(proc)
            ProcOutput(exitCode = -1, stdout = "", stderr = "execution error: ${e.javaClass.simpleName}: ${e.message?.take(200)}", timedOut = false, truncated = false)
        }
    }

    /**
     * A background thread that drains an input stream up to [MAX_OUTPUT_BYTES], then keeps reading
     * (and discarding) so the writer never blocks on a full pipe. [result] is the bounded text +
     * a truncation flag.
     */
    private class BoundedDrainer(private val stream: java.io.InputStream) : Thread() {
        private val kept = java.io.ByteArrayOutputStream()
        @Volatile private var truncated = false

        init { isDaemon = true }

        override fun run() {
            try {
                val buf = ByteArray(8192)
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    val remaining = MAX_OUTPUT_BYTES - kept.size()
                    if (remaining > 0) {
                        val take = minOf(remaining, n)
                        kept.write(buf, 0, take)
                        if (take < n) truncated = true
                    } else {
                        truncated = true
                    }
                    // keep reading past the cap to unblock the writer; discard the surplus
                }
            } catch (_: Exception) {
                // stream closed on kill — stop draining
            }
        }

        fun result(): Pair<String, Boolean> {
            val text = kept.toByteArray().toString(StandardCharsets.UTF_8)
            return if (truncated) (text + TRUNCATION_MARKER) to true else text to false
        }
    }

    /** Destroy the process AND its descendants (kills a sleep-forever child tree). */
    private fun destroyTree(proc: Process) {
        try {
            proc.descendants().forEach { it.destroyForcibly() }
        } catch (_: Exception) {
            // descendants() can fail on some JVMs/OSes — fall through to the direct kill.
        }
        proc.destroyForcibly()
    }

    /** Normalize stdout for the pass comparison: trim, normalize CRLF→LF, strip trailing blank lines. */
    private fun normalize(s: String): String =
        s.replace("\r\n", "\n").trimEnd().lines().joinToString("\n") { it.trimEnd() }.trim()

    private data class ProcOutput(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
        val truncated: Boolean,
    )

    companion object {
        /** §0.9-C tunable runtime constants. */
        const val DEFAULT_COMPILE_TIMEOUT_SECONDS: Long = 20L
        const val DEFAULT_RUN_TIMEOUT_SECONDS: Long = 10L

        /** §0.9-C: stdout/stderr bounded at 64 KiB, truncated with a marker. */
        const val MAX_OUTPUT_BYTES: Int = 64 * 1024
        const val TRUNCATION_MARKER: String = "\n…[output truncated at 64 KiB]"
    }
}

/**
 * Raw execution outcome (no grade). `available=false` means the toolchain was missing — the
 * caller degrades the chain leg honestly (the §0.9-C / `degradedLegs` honesty surface).
 */
data class ExecutionResult(
    val available: Boolean,
    val compiled: Boolean,
    val timedOut: Boolean,
    val stdout: String,
    val stderr: String,
    val truncated: Boolean,
    val reason: String? = null,
    val exitCode: Int = 0,
)
