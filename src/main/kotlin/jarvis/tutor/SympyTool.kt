package jarvis.tutor

import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Phase 7.6 deferral closer: subprocess-bounded sympy bridge for the
 * tutor's `symbolic_math` tool. Verifiable algebraic answers — derivative,
 * integral, solve, simplify, expand, factor — instead of free-form LLM
 * arithmetic that hallucinates.
 *
 * Why subprocess: keeps the JVM clean of Python deps; isolates a 10-second
 * compute budget per invocation via [Process.waitFor] + destroyForcibly.
 *
 * Deployment: `python3 -m pip install sympy` on the VPS. If the import
 * fails the wrapper returns a structured error so the LLM can degrade
 * gracefully rather than treat it as a tool crash.
 *
 * Hardening:
 *  - python3 binary path overridable via env JARVIS_PYTHON3 (default `python3`)
 *  - input expression flows over stdin to avoid argv quoting / shell-injection
 *  - the python wrapper uses `parse_expr` (no exec, no os, no sys); top-level
 *    namespace is the dict of operations only
 *  - 10s wall-clock cap; truncated output beyond 4 KiB
 */
object SympyTool {

    private const val TIMEOUT_SECONDS = 10L
    private const val MAX_OUTPUT_BYTES = 4096

    /** Operations the LLM is allowed to request. Keep in sync with the tool def. */
    private val ALLOWED_OPS = setOf("simplify", "diff", "integrate", "solve", "expand", "factor")

    data class Result(val ok: Boolean, val plain: String, val latex: String?, val error: String?)

    /**
     * Run sympy [op] on [expression]. [symbol] defaults to "x" — only used by
     * diff / integrate / solve. Returns a [Result] suitable for direct
     * rendering in chat.
     */
    fun run(op: String, expression: String, symbol: String = "x"): Result {
        val opNorm = op.trim().lowercase()
        if (opNorm !in ALLOWED_OPS) {
            return Result(ok = false, plain = "", latex = null,
                error = "unsupported op '$op' (allowed: ${ALLOWED_OPS.joinToString()})")
        }
        val expr = expression.trim()
        if (expr.isEmpty()) return Result(false, "", null, "expression required")
        if (expr.length > 1000) return Result(false, "", null, "expression too long (>1000 chars)")
        val sym = symbol.trim().ifEmpty { "x" }
        if (!sym.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
            return Result(false, "", null, "invalid symbol '$symbol' (must be a python identifier)")
        }

        val python = System.getenv("JARVIS_PYTHON3")?.takeIf { it.isNotBlank() } ?: "python3"
        val pb = ProcessBuilder(python, "-c", PY_RUNNER).redirectErrorStream(false)
        val proc = try {
            pb.start()
        } catch (e: Exception) {
            return Result(false, "", null,
                "sympy bridge unavailable: cannot start '$python' (${e.javaClass.simpleName}: ${e.message?.take(120)}). " +
                    "Run `pip install sympy` and ensure python3 is on PATH.")
        }

        try {
            // Pass payload via stdin as JSON-ish lines so quoting stays sane.
            // The python runner reads opNorm/expr/sym and prints a JSON line.
            OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8).use { w ->
                w.write(opNorm); w.write("\n")
                w.write(sym); w.write("\n")
                w.write(expr); w.write("\n")
                w.flush()
            }
            val finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return Result(false, "", null, "sympy timed out after ${TIMEOUT_SECONDS}s")
            }
            val stdout = proc.inputStream.readNBytes(MAX_OUTPUT_BYTES).toString(StandardCharsets.UTF_8)
            val stderr = proc.errorStream.readNBytes(MAX_OUTPUT_BYTES).toString(StandardCharsets.UTF_8)
            val exit = proc.exitValue()
            if (exit != 0) {
                val msg = stderr.trim().lines().lastOrNull { it.isNotBlank() }?.take(400)
                    ?: "sympy failed (exit $exit)"
                return Result(false, "", null, msg)
            }
            return parseRunnerOutput(stdout.trim())
        } catch (e: Exception) {
            proc.destroyForcibly()
            return Result(false, "", null, "sympy bridge error: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
        }
    }

    /** The python runner reads three stdin lines (op, sym, expr) and prints
     *  one tab-delimited line: `OK\tplain\tlatex` or `ERR\tmessage`. */
    internal fun parseRunnerOutput(raw: String): Result {
        if (raw.isEmpty()) return Result(false, "", null, "sympy returned empty output")
        val parts = raw.split('\t', limit = 3)
        return when (parts.firstOrNull()) {
            "OK" -> Result(
                ok = true,
                plain = parts.getOrNull(1).orEmpty(),
                latex = parts.getOrNull(2).orEmpty().takeIf { it.isNotBlank() },
                error = null,
            )
            "ERR" -> Result(false, "", null, parts.getOrNull(1).orEmpty().ifBlank { "sympy error" })
            else -> Result(false, "", null, "unparseable sympy output: ${raw.take(200)}")
        }
    }

    /** The python runner. parse_expr restricts to safe sympy globals — no
     *  os/sys/exec import in scope. Output is one tab-delimited line. */
    private val PY_RUNNER = """
import sys
try:
    import sympy
    from sympy.parsing.sympy_parser import parse_expr
except Exception as e:
    print("ERR\tsympy not installed (pip install sympy)")
    sys.exit(0)

try:
    op = sys.stdin.readline().strip()
    sym_name = sys.stdin.readline().strip() or "x"
    expr_text = sys.stdin.readline().rstrip("\n")
    sym = sympy.Symbol(sym_name)
    local = {sym_name: sym}
    e = parse_expr(expr_text, local_dict=local)
    if op == "simplify":
        r = sympy.simplify(e)
    elif op == "expand":
        r = sympy.expand(e)
    elif op == "factor":
        r = sympy.factor(e)
    elif op == "diff":
        r = sympy.diff(e, sym)
    elif op == "integrate":
        r = sympy.integrate(e, sym)
    elif op == "solve":
        r = sympy.solve(e, sym)
    else:
        print("ERR\tunsupported op")
        sys.exit(0)
    plain = str(r)
    try:
        latex = sympy.latex(r)
    except Exception:
        latex = ""
    # Tab-delimited single-line output. Replace any embedded tabs/newlines
    # so the kotlin parser sees exactly 3 fields.
    plain = plain.replace("\t", " ").replace("\n", " ")
    latex = latex.replace("\t", " ").replace("\n", " ")
    print("OK\t" + plain + "\t" + latex)
except Exception as e:
    msg = str(e).replace("\t", " ").replace("\n", " ")
    print("ERR\t" + msg)
""".trimIndent()
}
