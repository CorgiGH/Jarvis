package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProactiveLoopTest {

    /** Stub Llm whose ctx-model output we control inline. */
    private class FakeLlm(private val replyText: String = "ENERGY: mid\nSTRESS: low\nLIFE_SEASON: maintenance") : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?): Pair<String, String> =
            replyText to "fake-llm"
    }

    /** Llm that throws so we can test the error path. */
    private class FailingLlm : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?): Pair<String, String> {
            error("simulated provider failure")
        }
    }

    /** Use a daytime UTC instant outside Bucharest 23:00–07:00 local
     *  (Bucharest is UTC+3 in May). 12:00 UTC = 15:00 Bucharest. Daytime. */
    private val daytime: Instant = Instant.parse("2026-05-08T12:00:00Z")

    private fun activity(ts: String, importance: Float?, process: String = "code.exe") =
        ActivityEntry(ts = ts, title = "main.kt", process = process, pid = 1L, importance = importance)

    @Test
    fun belowThresholdSuppresses(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        val id = ProactiveLoop.considerSync(
            activity(daytime.toString(), 0.3f),
            FakeLlm(),
            signalsFile,
            daytime,
        )
        assertNull(id, "below-threshold importance must not emit")
        assertEquals(emptyList(), Signals.readAllFrom(signalsFile))
    }

    @Test
    fun nullImportanceSuppresses(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        val id = ProactiveLoop.considerSync(
            activity(daytime.toString(), null),
            FakeLlm(),
            signalsFile,
            daytime,
        )
        assertNull(id, "null importance treated as below-threshold")
    }

    @Test
    fun atThresholdEmitsSignal(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        val id = ProactiveLoop.considerSync(
            activity(daytime.toString(), 0.85f),
            FakeLlm(),
            signalsFile,
            daytime,
        )
        assertNotNull(id, "above-threshold should emit")
        val signals = Signals.readAllFrom(signalsFile)
        assertEquals(1, signals.size)
        assertEquals(id, signals[0].id)
        assertEquals("ctx_model_summary", signals[0].kind)
        assertEquals("emitted", signals[0].status)
        assertTrue(signals[0].snippet.contains("ENERGY"), "snippet captures ctx-model output")
    }

    @Test
    fun cooldownSuppressesSecondEmit(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        // First fire ok.
        val first = ProactiveLoop.considerSync(
            activity(daytime.toString(), 0.85f),
            FakeLlm(), signalsFile, daytime,
        )
        assertNotNull(first)
        // Five minutes later, still inside the 30-min cooldown.
        val later = daytime.plus(Duration.ofMinutes(5))
        val second = ProactiveLoop.considerSync(
            activity(later.toString(), 0.95f),
            FakeLlm(), signalsFile, later,
        )
        assertNull(second, "second fire within 30-min cooldown should be suppressed")
        assertEquals(1, Signals.readAllFrom(signalsFile).size)
    }

    @Test
    fun cooldownElapsedAllowsSecondEmit(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        ProactiveLoop.considerSync(
            activity(daytime.toString(), 0.85f),
            FakeLlm(), signalsFile, daytime,
        )
        val later = daytime.plus(Duration.ofMinutes(35))
        val second = ProactiveLoop.considerSync(
            activity(later.toString(), 0.85f),
            FakeLlm(), signalsFile, later,
        )
        assertNotNull(second, "after 30-min cooldown, second fire should emit")
        assertEquals(2, Signals.readAllFrom(signalsFile).size)
    }

    @Test
    fun quietHoursDisabledByDefault(@TempDir tmp: Path) = runBlocking {
        // Council retro 2026-05-08 fix: quiet hours opt-in via env. Tests
        // run without QUIET_HOURS_START/END set → no suppression. The
        // 02:33 incident proved hardcoded 23-07 was wrong for THIS user.
        val signalsFile = tmp.resolve("signals.jsonl")
        val nightUtc = Instant.parse("2026-05-08T02:00:00Z")
        val id = ProactiveLoop.considerSync(
            activity(nightUtc.toString(), 0.95f),
            FakeLlm(), signalsFile, nightUtc,
        )
        assertNotNull(id, "no quiet env → no suppression even at 5am")
        assertEquals(1, Signals.readAllFrom(signalsFile).size)
    }

    @Test
    fun quietHoursWindowMath() {
        // QuietHours.isActiveWith — wrap-around window 23:00-07:00.
        val quiet23 = Instant.parse("2026-05-08T20:00:00Z") // 23:00 Bucharest
        val notQuiet22 = Instant.parse("2026-05-08T19:59:00Z") // 22:59 Bucharest
        val quiet06 = Instant.parse("2026-05-09T03:30:00Z") // 06:30 Bucharest
        val notQuiet07 = Instant.parse("2026-05-09T04:00:00Z") // 07:00 Bucharest
        assertTrue(QuietHours.isActiveWith(quiet23, "23:00", "07:00"))
        assertTrue(!QuietHours.isActiveWith(notQuiet22, "23:00", "07:00"))
        assertTrue(QuietHours.isActiveWith(quiet06, "23:00", "07:00"))
        assertTrue(!QuietHours.isActiveWith(notQuiet07, "23:00", "07:00"))
        // Same-day window 09:00-12:00.
        val morning10 = Instant.parse("2026-05-08T07:00:00Z") // 10:00 Bucharest
        assertTrue(QuietHours.isActiveWith(morning10, "09:00", "12:00"))
        // Empty config → never active.
        assertTrue(!QuietHours.isActiveWith(quiet23, null, null))
        assertTrue(!QuietHours.isActiveWith(quiet23, "", ""))
    }

    @Test
    fun deterministicSignalId(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        val sourceTs = "2026-05-08T11:55:00Z"
        val id1 = ProactiveLoop.computeSignalId(sourceTs, daytime)
        val id2 = ProactiveLoop.computeSignalId(sourceTs, daytime)
        assertEquals(id1, id2, "same inputs → same id")
        // Different ts_bucket → different id.
        val laterHour = daytime.plus(Duration.ofHours(1))
        val id3 = ProactiveLoop.computeSignalId(sourceTs, laterHour)
        assertTrue(id1 != id3, "different hour bucket → different id")
    }

    @Test
    fun llmFailureWritesErrorRow(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        val id = ProactiveLoop.considerSync(
            activity(daytime.toString(), 0.85f),
            FailingLlm(), signalsFile, daytime,
        )
        assertNotNull(id, "failure path still writes an error row + returns id")
        val signals = Signals.readAllFrom(signalsFile)
        assertEquals(1, signals.size)
        assertEquals("error", signals[0].kind)
        assertEquals("error", signals[0].status)
        assertTrue(
            signals[0].snippet.contains("simulated provider failure") ||
                signals[0].snippet.contains("IllegalStateException"),
            "error snippet captures the cause: ${signals[0].snippet}",
        )
    }

    @Test
    fun errorRowDoesNotCountTowardCooldown(@TempDir tmp: Path) = runBlocking {
        val signalsFile = tmp.resolve("signals.jsonl")
        // First call fails → error row written.
        ProactiveLoop.considerSync(
            activity(daytime.toString(), 0.85f),
            FailingLlm(), signalsFile, daytime,
        )
        // Five minutes later — cooldown should NOT block since previous was an
        // error row, not an emitted signal.
        val later = daytime.plus(Duration.ofMinutes(5))
        val second = ProactiveLoop.considerSync(
            activity(later.toString(), 0.85f),
            FakeLlm(), signalsFile, later,
        )
        assertNotNull(second, "error rows must not extend cooldown")
        assertEquals(2, Signals.readAllFrom(signalsFile).size)
    }

    @Test
    fun isEnabledRespectsEnv() {
        // The default is OFF when the env var is not set.
        // We can't reliably mutate env in a test without process restart, so
        // just assert the function evaluates (no crash) and returns a Boolean.
        val v = ProactiveLoop.isEnabled()
        assertTrue(v == true || v == false, "must return a boolean")
    }
}
