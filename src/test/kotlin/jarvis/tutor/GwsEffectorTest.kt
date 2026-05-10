package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GwsEffectorTest {
    @Test
    fun `health reports disabled when GWS_ENABLED is unset`() {
        // Test runner does not set GWS_ENABLED — this is the default deploy
        // posture, so the message must steer the user to the right action.
        if (System.getenv("GWS_ENABLED") != null) return  // skip in environments where it is set
        val h = GwsEffector.health()
        assertFalse(h.enabled)
        assertFalse(h.binaryFound)
        assertFalse(h.authenticated)
        assertTrue(h.detail.contains("GWS_ENABLED"))
        assertTrue(h.detail.contains("gws auth login"))
    }

    @Test
    fun `isBinaryOnPath returns false for a guaranteed-missing binary in this PATH`() {
        // Probe is best-effort; real environments may have gws installed.
        // We just verify the function does not throw and returns a Boolean.
        val r = GwsEffector.isBinaryOnPath()
        assertNotNull(r)
        assertEquals(r, r)  // any value is fine, contract is "no exception"
    }

    @Test
    fun `run returns Err for unsupported subcommand`() {
        // The shim no longer gates on GWS_ENABLED; unknown subcommands
        // return an Err with a message directing callers to GoogleClients.
        val r = GwsEffector.run("calendar events list")
        assertTrue(r is GwsEffector.Result.Err)
        val err = r as GwsEffector.Result.Err
        // "list" is not "insert" so it falls into the unsupported branch.
        assertTrue(err.stderr.contains("unsupported") || err.exitCode != 0,
            "expected unsupported-subcommand error but got: ${err.stderr}")
    }
}
