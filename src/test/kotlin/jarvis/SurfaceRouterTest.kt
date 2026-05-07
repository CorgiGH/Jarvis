package jarvis

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceRouterTest {

    private fun sig(
        importance: Float,
        kind: String = "ctx_model_summary",
        status: String = "emitted",
    ) = ProactiveSignal(
        id = "test", ts = "2026-05-08T12:00:00Z", kind = kind,
        importance = importance, sourceTs = "2026-05-08T12:00:00Z",
        snippet = "test", rationale = "t", status = status,
    )

    @Test
    fun lowImportanceWikiOnly() {
        assertEquals(setOf(Surface.WIKI), SurfaceRouter.route(sig(0.3f)))
    }

    @Test
    fun midImportanceWikiPlusPush() {
        assertEquals(
            setOf(Surface.WIKI, Surface.PUSH),
            SurfaceRouter.route(sig(0.6f)),
        )
    }

    @Test
    fun highImportanceWikiPushPin() {
        assertEquals(
            setOf(Surface.WIKI, Surface.PUSH, Surface.PIN),
            SurfaceRouter.route(sig(0.9f)),
        )
    }

    @Test
    fun reflectionGoesWikiAndPinNeverPush() {
        val r = SurfaceRouter.route(sig(0.95f, kind = "reflection"))
        assertTrue(Surface.WIKI in r)
        assertTrue(Surface.PIN in r)
        assertTrue(Surface.PUSH !in r, "reflections must NOT push")
    }

    @Test
    fun errorGoesWikiOnly() {
        val r = SurfaceRouter.route(sig(0.95f, status = "error"))
        assertEquals(setOf(Surface.WIKI), r)
    }

    @Test
    fun thresholdEdgesAtBoundaries() {
        // Exactly 0.5 → WIKI + PUSH
        assertEquals(setOf(Surface.WIKI, Surface.PUSH), SurfaceRouter.route(sig(0.5f)))
        // Exactly 0.8 → WIKI + PUSH + PIN
        assertEquals(
            setOf(Surface.WIKI, Surface.PUSH, Surface.PIN),
            SurfaceRouter.route(sig(0.8f)),
        )
    }

    @Test
    fun zeroImportanceWikiOnly() {
        assertEquals(setOf(Surface.WIKI), SurfaceRouter.route(sig(0.0f)))
    }
}
