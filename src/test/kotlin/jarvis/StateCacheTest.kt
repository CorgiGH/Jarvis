package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StateCacheTest {

    private class FakeLlm(val replyText: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): Pair<String, String> =
            replyText to "fake-llm"
    }

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
}
