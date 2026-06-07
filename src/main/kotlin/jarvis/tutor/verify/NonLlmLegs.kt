package jarvis.tutor.verify

import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Batch-2 leg 1 — the concrete `NonLlmLeg` impls (the machine checker, H9).
 *
 * Domain-scoped per §K: PA ⇒ [SymPyLeg]; PS / SO-RC / POO / ALO / unknown ⇒ [NoneLeg]
 * (no runnable checker yet ⇒ `kind=NONE, ran=false` ⇒ the §2.5 NONLLM_LEG_NONE UNCERTAIN floor).
 *
 * FAIL-LOUD (H5): a leg that could not run reports `ran=false` and is NEVER coerced to a pass —
 * never-ran ≠ disagreed. The runner distinguishes the two.
 */

/**
 * PA non-LLM leg — checks an INVARIANT / GRADER_RULE claim by shelling to python + sympy and
 * asking whether the two sides of the claim's equation simplify to the same expression.
 *
 * The python call is an INJECTED seam ([pythonEquals]) so the default unit suite uses a fake and
 * never touches python/sympy/the network. The default constructor wires the REAL seam
 * ([realSymPyEquals]); the one guarded integration test exercises it only when sympy is present
 * (it returns `ran=false` when sympy/python is unavailable, which the test treats as SKIP).
 *
 * Only INVARIANT / GRADER_RULE claims carry a machine-checkable equation. A DEFINITION (or any
 * claim with a null [VerificationClaim.invariant]) has nothing to check ⇒ `ran=false` (the §2.5
 * DEFINITIONAL_NO_GOLD floor) — the seam is NOT invoked.
 */
class SymPyLeg(
    private val pythonEquals: (lhs: String, rhs: String) -> PyResult = ::realSymPyEquals,
) : NonLlmLeg {

    /** Output of the python+sympy seam. [ran]=false ⇒ the checker could not run (sympy/python
     *  absent, parse error, timeout) — propagated as `NonLlmResult.ran=false`, never a pass. */
    data class PyResult(val ran: Boolean, val equal: Boolean, val detail: String?)

    override fun check(claim: VerificationClaim): NonLlmResult {
        // FIX-C: a claim for which NO machine check applies (a non-equational / DEFINITION claim, or a
        // GRADER_RULE/INVARIANT whose text is not a plain `lhs = rhs` equation) reports kind=NONE
        // (ran=false) — NOT kind=SYMPY. The runner's NONLLM_LEG_NONE branch then floors it to the
        // UNCERTAIN floor; a SYMPY tag here would fall through decideOutcome's else ⇒ failed (the
        // multi-claim KC-poisoning bug). 'SYMPY' is reserved for a claim the sympy bridge ACTUALLY ran.

        // Only equational claims are machine-checkable. No invariant ⇒ nothing to run ⇒ NONE floor.
        if (claim.kind != ClaimKind.INVARIANT && claim.kind != ClaimKind.GRADER_RULE) {
            return noMachineCheck("no machine-checkable invariant for a ${claim.kind} claim")
        }
        val equation = claim.invariant
        if (equation.isNullOrBlank()) {
            return noMachineCheck("claim has no invariant equation to check")
        }
        val (lhs, rhs) = splitEquation(equation)
            ?: return noMachineCheck("invariant is not an equation (no single '=' to split): $equation")

        // A real equation: NOW the SYMPY checker genuinely runs (kind=SYMPY).
        val py = pythonEquals(lhs, rhs)
        return NonLlmResult(
            kind = NonLlmLegKind.SYMPY,
            ran = py.ran,
            pass = py.ran && py.equal,   // a non-run is NEVER a pass
            detail = py.detail,
        )
    }

    /** No machine check applies to this claim ⇒ the NONLLM_LEG_NONE uncertain floor (NOT failed). */
    private fun noMachineCheck(detail: String): NonLlmResult =
        NonLlmResult(kind = NonLlmLegKind.NONE, ran = false, pass = false, detail = detail)

    private companion object {
        /** Split an `lhs = rhs` invariant on its single top-level `=`. Returns null when the
         *  equation does not have exactly one `=` (e.g. `<=`, `==`, or no `=`).
         *
         *  MED-b: the parsing logic now lives in the ONE shared [EquationSyntax.split] (this leg is its
         *  canonical owner) — the previously copy-pasted siblings in `ContentReconcile.isPlainEquation`
         *  and `ContentValidator.checkTautologicalInvariants` delegate to the same helper, so the three
         *  cannot drift (guarded by `EquationSyntaxAgreementTest`).
         *
         *  NOTE (D4): a TAUTOLOGICAL invariant (`t = t`, `0 = 0`) is rejected at AUTHOR/AUDIT time by
         *  `ContentValidator.checkTautologicalInvariants` (the canonical D4 fix), so it can never reach
         *  a stamped claim. This split is intentionally NOT made tautology-aware: it is exercised by
         *  the hermetic test suite with `x = x` as a generic equation PLACEHOLDER, and a syntactic
         *  reject here would silently change that established convention. The `bothSupported`
         *  requirement upstream already blunts any weaponization (LOW severity). */
        fun splitEquation(eq: String): Pair<String, String>? = EquationSyntax.split(eq)
    }
}

/** Timeout + output caps for the real sympy subprocess. */
private const val SYMPY_TIMEOUT_SECONDS = 10L
private const val SYMPY_MAX_OUTPUT_BYTES = 4096

/**
 * The REAL python+sympy seam. Shells to python and asks sympy whether `simplify(lhs - rhs) == 0`.
 * Mirrors the hardening of `jarvis.tutor.SympyTool` (stdin payload, no shell injection, bounded
 * wall-clock, structured degrade). Returns `ran=false` on ANY failure (python missing, sympy
 * missing, parse error, timeout) so the leg degrades to the UNCERTAIN floor rather than crashing.
 */
fun realSymPyEquals(lhs: String, rhs: String): SymPyLeg.PyResult {
    if (lhs.length > 1000 || rhs.length > 1000) {
        return SymPyLeg.PyResult(ran = false, equal = false, detail = "expression too long (>1000 chars)")
    }
    val python = System.getenv("JARVIS_PYTHON3")?.takeIf { it.isNotBlank() } ?: "python3"
    // D-R13: run the runner as a temp FILE, NOT `python -c "<script>"`. On Windows the JVM's
    // CreateProcess command-line quoting MANGLES embedded double-quotes in a `-c` arg (python then
    // hits a SyntaxError → empty stdout → ran=false), which silently degraded every equational
    // claim to the UNCERTAIN floor. The file path is quote-safe.
    val scriptFile = try {
        java.nio.file.Files.createTempFile("jarvis-sympy-", ".py").also {
            java.nio.file.Files.write(it, PY_EQUALS_RUNNER.toByteArray(StandardCharsets.UTF_8))
        }
    } catch (e: Exception) {
        return SymPyLeg.PyResult(false, false, "sympy bridge unavailable: cannot stage runner (${e.javaClass.simpleName})")
    }
    val proc = try {
        ProcessBuilder(python, scriptFile.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    } catch (e: Exception) {
        runCatching { java.nio.file.Files.deleteIfExists(scriptFile) }
        return SymPyLeg.PyResult(
            ran = false,
            equal = false,
            detail = "sympy bridge unavailable: cannot start '$python' (${e.javaClass.simpleName})",
        )
    }
    return try {
        OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8).use { w ->
            w.write(lhs.replace("\n", " ")); w.write("\n")
            w.write(rhs.replace("\n", " ")); w.write("\n")
            w.flush()
        }
        val finished = proc.waitFor(SYMPY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return SymPyLeg.PyResult(false, false, "sympy timed out after ${SYMPY_TIMEOUT_SECONDS}s")
        }
        val out = proc.inputStream.readNBytes(SYMPY_MAX_OUTPUT_BYTES).toString(StandardCharsets.UTF_8).trim()
        parseEqualsRunnerOutput(out)
    } catch (e: Exception) {
        proc.destroyForcibly()
        SymPyLeg.PyResult(false, false, "sympy bridge error: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
    } finally {
        runCatching { java.nio.file.Files.deleteIfExists(scriptFile) }
    }
}

/** Parse the runner's single tab-delimited line: `EQ\tdetail` / `NEQ\tdetail` / `ERR\tmessage`. */
internal fun parseEqualsRunnerOutput(raw: String): SymPyLeg.PyResult {
    if (raw.isEmpty()) return SymPyLeg.PyResult(false, false, "sympy returned empty output")
    val parts = raw.split('\t', limit = 2)
    return when (parts.firstOrNull()) {
        "EQ" -> SymPyLeg.PyResult(ran = true, equal = true, detail = parts.getOrNull(1).orEmpty().ifBlank { "simplify(lhs-rhs)=0" })
        "NEQ" -> SymPyLeg.PyResult(ran = true, equal = false, detail = parts.getOrNull(1).orEmpty().ifBlank { "simplify(lhs-rhs)!=0" })
        "ERR" -> SymPyLeg.PyResult(ran = false, equal = false, detail = parts.getOrNull(1).orEmpty().ifBlank { "sympy error" })
        else -> SymPyLeg.PyResult(false, false, "unparseable sympy output: ${raw.take(200)}")
    }
}

/** The python runner: read lhs/rhs from stdin, simplify the difference, print one tab line.
 *  parse_expr restricts to safe sympy globals — no os/sys/exec in scope. */
private val PY_EQUALS_RUNNER = """
import sys
try:
    import sympy
    from sympy.parsing.sympy_parser import parse_expr
except Exception:
    print("ERR\tsympy not installed (pip install sympy)")
    sys.exit(0)

try:
    lhs_text = sys.stdin.readline().rstrip("\n")
    rhs_text = sys.stdin.readline().rstrip("\n")
    lhs = parse_expr(lhs_text)
    rhs = parse_expr(rhs_text)
    diff = sympy.simplify(lhs - rhs)
    if diff == 0:
        print("EQ\tsimplify(lhs-rhs)=0")
    else:
        d = str(diff).replace("\t", " ").replace("\n", " ")
        print("NEQ\tsimplify(lhs-rhs)=" + d)
except Exception as e:
    msg = str(e).replace("\t", " ").replace("\n", " ")
    print("ERR\t" + msg)
""".trimIndent()

/**
 * The non-PA non-LLM leg — there is no runnable machine checker for PS / SO-RC / POO / ALO yet,
 * so it returns `kind=NONE, ran=false` (the §2.5 NONLLM_LEG_NONE UNCERTAIN floor). FAIL-LOUD:
 * ran=false marks "never ran", which the runner must NOT confuse with a disagreement.
 */
object NoneLeg : NonLlmLeg {
    override fun check(claim: VerificationClaim): NonLlmResult =
        NonLlmResult(
            kind = NonLlmLegKind.NONE,
            ran = false,
            pass = false,
            detail = "no non-LLM checker for subject '${claim.subject}' (UNCERTAIN floor)",
        )
}

/**
 * Domain selector — picks the non-LLM leg for a [subject]. PA ⇒ [SymPyLeg] (real seam);
 * every other subject ⇒ [NoneLeg]. Case-insensitive on the subject tag.
 */
fun nonLlmLegFor(subject: String): NonLlmLeg =
    when (subject.trim().uppercase()) {
        "PA" -> SymPyLeg()
        else -> NoneLeg
    }
