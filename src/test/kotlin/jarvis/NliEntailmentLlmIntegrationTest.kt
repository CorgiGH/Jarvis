package jarvis

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D6 / D-R12 (B5r-6) — the ONE GUARDED integration test for `NliEntailmentLlm`. It runs the REAL
 * embedded python NLI runner via `JARVIS_PYTHON3`. Mirrors `NonLlmLegsTest`'s SymPy integration
 * style: it is SKIPPED (assumeTrue) when python / transformers / the model is absent, so it NEVER
 * fails the default suite on a box without the model.
 *
 * Set `JARVIS_PYTHON3=C:/Users/User/AppData/Local/Programs/Python/Python312/python.exe` to run it.
 * If the harness does not propagate the env, the runner throws ⇒ we catch ⇒ assumeTrue(false) ⇒ SKIP.
 */
class NliEntailmentLlmIntegrationTest {

    /** Run the real runner; SKIP (not fail) when the local model/python stack is unavailable. */
    private fun runOrSkip(premise: String, hypothesis: String): String {
        val verdict = try {
            NliEntailmentLlm.runNli(premise, hypothesis)
        } catch (e: RuntimeException) {
            assumeTrue(false) { "NLI python/model unavailable (${e.message}) — skipping the live NLI integration check" }
            error("unreachable")
        }
        return verdict
    }

    @Test
    fun `INTEGRATION the real NLI confirms an ENTAILED hypothesis as SUPPORTED when the model is present`() {
        val premise = "An algorithm halts in a finite amount of time."
        val hypothesis = "An algorithm finishes after finitely many steps."
        val verdict = runOrSkip(premise, hypothesis)
        assertEquals("SUPPORTED", verdict, "an entailed hypothesis must verify as SUPPORTED")
    }

    @Test
    fun `INTEGRATION the real NLI flags a CONTRADICTING hypothesis as REFUTED when the model is present`() {
        val premise = "An algorithm halts in a finite amount of time."
        val hypothesis = "An algorithm may run forever and never halt."
        val verdict = runOrSkip(premise, hypothesis)
        assertEquals("REFUTED", verdict, "a contradicting hypothesis must verify as REFUTED")
    }
}
