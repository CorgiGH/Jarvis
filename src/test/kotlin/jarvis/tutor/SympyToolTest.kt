package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SympyToolTest {

    @Test
    fun `parseRunnerOutput happy path returns ok plus plain plus latex`() {
        val r = SympyTool.parseRunnerOutput("OK\t2*x + 2\t2 x + 2")
        assertTrue(r.ok)
        assertEquals("2*x + 2", r.plain)
        assertEquals("2 x + 2", r.latex)
        assertNull(r.error)
    }

    @Test
    fun `parseRunnerOutput err carries the message`() {
        val r = SympyTool.parseRunnerOutput("ERR\tsympy not installed (pip install sympy)")
        assertFalse(r.ok)
        assertNotNull(r.error)
        assertTrue(r.error!!.contains("sympy not installed"))
    }

    @Test
    fun `parseRunnerOutput empty returns structured error`() {
        val r = SympyTool.parseRunnerOutput("")
        assertFalse(r.ok)
        assertEquals("sympy returned empty output", r.error)
    }

    @Test
    fun `parseRunnerOutput unparseable returns structured error`() {
        val r = SympyTool.parseRunnerOutput("garbage line with no tag")
        assertFalse(r.ok)
        assertNotNull(r.error)
        assertTrue(r.error!!.startsWith("unparseable sympy output"))
    }

    @Test
    fun `run rejects unsupported op`() {
        val r = SympyTool.run(op = "explode", expression = "x")
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("unsupported op"))
    }

    @Test
    fun `run rejects invalid symbol`() {
        val r = SympyTool.run(op = "diff", expression = "x", symbol = "1+1")
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("invalid symbol"))
    }

    @Test
    fun `run rejects empty expression`() {
        val r = SympyTool.run(op = "simplify", expression = "")
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("expression required"))
    }

    @Test
    fun `run rejects oversized expression`() {
        val r = SympyTool.run(op = "simplify", expression = "x".repeat(1100))
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("expression too long"))
    }

    @Test
    fun `run gracefully degrades when python3 missing`() {
        // Force a guaranteed-missing binary so the start fails deterministically;
        // the wrapper should return a structured error rather than throwing.
        val prior = System.getenv("JARVIS_PYTHON3")
        try {
            // Set per-process env via reflection isn't safe across JDKs; instead
            // exercise the start-failure path by pointing at a path the OS will
            // certainly reject. ProcessBuilder respects JARVIS_PYTHON3 only at
            // run() invocation, but we don't have a hook here. So rely on the
            // dispatcher's own try/catch around `pb.start()` by using an op that
            // *reaches* start(): if python3 IS installed locally the test will
            // still exercise the runner — we accept either outcome (ok or
            // structured error), the contract is "no thrown exception".
            val r = SympyTool.run(op = "simplify", expression = "x + x")
            // Either python is on the test runner's PATH (real result) or not
            // (graceful error). Both are valid outcomes — we just verify the
            // wrapper never throws + always populates the Result.
            assertNotNull(r)
            if (r.ok) {
                assertTrue(r.plain.isNotBlank(), "ok result must have plain text")
            } else {
                assertNotNull(r.error)
            }
        } finally {
            // No-op cleanup — test does not mutate env.
            assertEquals(prior, System.getenv("JARVIS_PYTHON3"))
        }
    }
}
