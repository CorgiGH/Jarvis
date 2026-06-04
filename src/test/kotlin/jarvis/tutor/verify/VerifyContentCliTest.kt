package jarvis.tutor.verify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Batch-5 — FAIL-LOUD env-check for the `verifyContent` offline batch (H4). The check is PURE: the
 * env lookup is injected, so the default suite never reads the real process env and never starts a DB.
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
            "OPENROUTER_API_KEY" to "or-key",
        )
        assertEquals(emptyList(), VerifyContentCli.missingFamilyEnv(env))
    }

    @Test
    fun `a missing relay env var is reported (FAIL-LOUD, no one-family audit)`() {
        val env = envOf(
            "JARVIS_RELAY_URL" to "http://relay",
            // JARVIS_RELAY_TOKEN intentionally missing
            "OPENROUTER_API_KEY" to "or-key",
        )
        val missing = VerifyContentCli.missingFamilyEnv(env)
        assertEquals(listOf("JARVIS_RELAY_TOKEN"), missing)
        assertTrue(VerifyContentCli.abortMessage(missing).contains("JARVIS_RELAY_TOKEN"))
    }

    @Test
    fun `a missing openrouter key is reported`() {
        val env = envOf(
            "JARVIS_RELAY_URL" to "http://relay",
            "JARVIS_RELAY_TOKEN" to "tok",
            // OPENROUTER_API_KEY missing
        )
        assertEquals(listOf("OPENROUTER_API_KEY"), VerifyContentCli.missingFamilyEnv(env))
    }

    @Test
    fun `a blank env value counts as missing (a stray empty export is not a provisioned family)`() {
        val env = envOf(
            "JARVIS_RELAY_URL" to "",
            "JARVIS_RELAY_TOKEN" to "   ",
            "OPENROUTER_API_KEY" to "or-key",
        )
        val missing = VerifyContentCli.missingFamilyEnv(env)
        assertTrue("JARVIS_RELAY_URL" in missing)
        assertTrue("JARVIS_RELAY_TOKEN" in missing)
        assertTrue("OPENROUTER_API_KEY" !in missing)
    }

    @Test
    fun `everything missing names all required family vars`() {
        val missing = VerifyContentCli.missingFamilyEnv { null }
        assertEquals(
            listOf("JARVIS_RELAY_URL", "JARVIS_RELAY_TOKEN", "OPENROUTER_API_KEY"),
            missing,
        )
        val msg = VerifyContentCli.abortMessage(missing)
        assertTrue(msg.contains("FAIL-LOUD"))
        assertTrue(msg.contains("RELAY"))
        assertTrue(msg.contains("OPENROUTER"))
    }
}
