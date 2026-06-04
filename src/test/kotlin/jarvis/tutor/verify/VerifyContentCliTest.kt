package jarvis.tutor.verify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Batch-5 — FAIL-LOUD env-check for the `verifyContent` offline batch (H4). The check is PURE: the
 * env lookup is injected, so the default suite never reads the real process env and never starts a DB.
 *
 * B5r-6/D6: family B is now the LOCAL NLI (provisioned by JARVIS_PYTHON3), NOT OpenRouter — the audit
 * is network-free + PC-side (D7); OPENROUTER_API_KEY is no longer a required family env var.
 */
class VerifyContentCliTest {

    private fun envOf(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = pairs.toMap()
        return { map[it] }
    }

    @Test
    fun `all families provisioned reports zero missing`() {
        val env = envOf(
            "JARVIS_RELAY_URL" to "http://relay",
            "JARVIS_RELAY_TOKEN" to "tok",
            "JARVIS_PYTHON3" to "C:/py/python.exe",
        )
        assertEquals(emptyList(), VerifyContentCli.missingFamilyEnv(env))
    }

    @Test
    fun `a missing relay env var is reported (FAIL-LOUD, no one-family audit)`() {
        val env = envOf(
            "JARVIS_RELAY_URL" to "http://relay",
            // JARVIS_RELAY_TOKEN intentionally missing
            "JARVIS_PYTHON3" to "C:/py/python.exe",
        )
        val missing = VerifyContentCli.missingFamilyEnv(env)
        assertEquals(listOf("JARVIS_RELAY_TOKEN"), missing)
        assertTrue(VerifyContentCli.abortMessage(missing).contains("JARVIS_RELAY_TOKEN"))
    }

    @Test
    fun `a missing NLI python env var is reported (the local NLI family is not provisioned)`() {
        val env = envOf(
            "JARVIS_RELAY_URL" to "http://relay",
            "JARVIS_RELAY_TOKEN" to "tok",
            // JARVIS_PYTHON3 missing
        )
        assertEquals(listOf("JARVIS_PYTHON3"), VerifyContentCli.missingFamilyEnv(env))
    }

    @Test
    fun `a blank env value counts as missing (a stray empty export is not a provisioned family)`() {
        val env = envOf(
            "JARVIS_RELAY_URL" to "",
            "JARVIS_RELAY_TOKEN" to "   ",
            "JARVIS_PYTHON3" to "C:/py/python.exe",
        )
        val missing = VerifyContentCli.missingFamilyEnv(env)
        assertTrue("JARVIS_RELAY_URL" in missing)
        assertTrue("JARVIS_RELAY_TOKEN" in missing)
        assertTrue("JARVIS_PYTHON3" !in missing)
    }

    @Test
    fun `everything missing names all required family vars`() {
        val missing = VerifyContentCli.missingFamilyEnv { null }
        assertEquals(
            listOf("JARVIS_RELAY_URL", "JARVIS_RELAY_TOKEN", "JARVIS_PYTHON3"),
            missing,
        )
        val msg = VerifyContentCli.abortMessage(missing)
        assertTrue(msg.contains("FAIL-LOUD"))
        assertTrue(msg.contains("RELAY"))
        assertTrue(msg.contains("NLI"))
    }
}
