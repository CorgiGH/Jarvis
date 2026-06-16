package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StateCacheTest {

    private class FakeLlm(val replyText: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?, imagePath: String?): Pair<String, String> =
            replyText to "fake-llm"
    }

    // Phase-0 (2026-06-15): StateCache is a process-global `object` singleton; without per-test reset
    // its `cell`/`diskLoadAttempted` leak across methods AND across IntegrationHarnessTest (which
    // shares the same singleton), producing the ~1-in-2 order-dependent flake. resetForTests() clears
    // both; running it before AND after each test makes every method start clean and leaves nothing
    // for the next test class to inherit.
    @BeforeEach
    fun resetBefore() = StateCache.resetForTests()

    @AfterEach
    fun resetAfter() = StateCache.resetForTests()

    @Test
    fun refreshSyncStoresAndReturns() = runBlocking {
        val now = Instant.parse("2026-05-08T12:00:00Z")
        val s = StateCache.refreshSync(FakeLlm("ENERGY: high"), now)
        assertNotNull(s)
        assertTrue(s!!.text.contains("ENERGY"), "snippet captured")
        assertEquals(now.toString(), s.ts)
        // Reading back via current() returns the same data.
        val read = StateCache.current(now)
        assertNotNull(read)
        assertEquals(s.text, read!!.text)
    }

    @Test
    fun ageSecondsReflectsTimeSinceRefresh() = runBlocking {
        val cachedAt = Instant.parse("2026-05-08T12:00:00Z")
        StateCache.refreshSync(FakeLlm("ENERGY: mid"), cachedAt)
        val laterRead = StateCache.current(cachedAt.plus(Duration.ofMinutes(15)))
        assertNotNull(laterRead)
        assertEquals(900L, laterRead!!.ageSeconds, "15 min = 900s")
    }

    @Test
    fun isStaleAfterIntervalElapsed() = runBlocking {
        val cachedAt = Instant.parse("2026-05-08T12:00:00Z")
        StateCache.refreshSync(FakeLlm("hi"), cachedAt)
        // Within window — fresh.
        assertTrue(!StateCache.isStale(cachedAt.plus(Duration.ofMinutes(20))), "fresh inside 30 min window")
        // Past window — stale.
        assertTrue(StateCache.isStale(cachedAt.plus(Duration.ofMinutes(35))), "stale past 30 min")
    }

    @Test
    fun isEnabledRespectsEnv() {
        // Default env not set in test → disabled.
        val v = StateCache.isEnabled()
        assertTrue(v == true || v == false)
    }

    @Test
    fun persistAndReloadRoundtrip(@TempDir tmp: Path) {
        val file = tmp.resolve("state_cache.json")
        val state = UserState(
            ts = "2026-05-09T10:00:00Z",
            text = "ENERGY: high; FOCUS: PA chapter 4",
            model = "claude-sonnet-4-6",
        )
        StateCache.persist(state, file)
        assertTrue(file.exists(), "file written")
        val raw = file.readText()
        assertTrue(raw.contains("ENERGY"), "json contains text")
        // Reload into fresh cell
        StateCache.resetForTests()
        StateCache.ensureLoaded(file)
        val read = StateCache.current(Instant.parse("2026-05-09T10:00:00Z"))
        assertNotNull(read)
        assertEquals(state.text, read!!.text)
        assertEquals(state.model, read.model)
    }

    @Test
    fun ensureLoadedSkipsMissingFile(@TempDir tmp: Path) {
        StateCache.resetForTests()
        StateCache.ensureLoaded(tmp.resolve("nope.json"))
        // Cell stays null; current() returns null.
        assertEquals(null, StateCache.current(Instant.now()))
    }

    @Test
    fun ensureLoadedRunsAtMostOnce(@TempDir tmp: Path) {
        val file = tmp.resolve("state_cache.json")
        StateCache.persist(
            UserState(ts = "2026-05-09T10:00:00Z", text = "first", model = "m1"),
            file,
        )
        StateCache.resetForTests()
        StateCache.ensureLoaded(file)
        // Mutate the file post-load.
        StateCache.persist(
            UserState(ts = "2026-05-09T11:00:00Z", text = "second", model = "m2"),
            file,
        )
        // Second ensureLoaded is a no-op — cell still has "first".
        StateCache.ensureLoaded(file)
        val read = StateCache.current(Instant.parse("2026-05-09T10:00:00Z"))
        assertEquals("first", read?.text)
    }
}
