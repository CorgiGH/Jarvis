package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationsByImportanceTest {

    private val now: Instant = Instant.parse("2026-05-08T12:00:00Z")

    private fun ts(hoursAgo: Long): String =
        now.minus(Duration.ofHours(hoursAgo)).toString()

    private fun entry(
        msgId: String,
        hoursAgo: Long,
        importance: Float?,
        content: String = "msg-$msgId",
        role: String = "user",
        seq: Int = 0,
    ) = ConversationEntry(
        role = role, content = content, ts = ts(hoursAgo),
        msgId = msgId, seq = seq, importance = importance,
    )

    @Test
    fun emptyFileReturnsEmpty(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        assertEquals(emptyList(), Conversations.recentByImportanceFrom(file, n = 5, now = now))
    }

    @Test
    fun zeroNReturnsEmpty(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        Conversations.appendTo(file, entry("a", 1, 0.8f))
        assertEquals(
            emptyList(),
            Conversations.recentByImportanceFrom(file, n = 0, now = now),
        )
    }

    @Test
    fun higherImportanceWinsAtSameRecency(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        // Both 1 hour old; importance differs.
        Conversations.appendTo(file, entry("low", 1, 0.2f, content = "low"))
        Conversations.appendTo(file, entry("hi", 1, 0.9f, content = "hi"))
        val out = Conversations.recentByImportanceFrom(file, n = 1, now = now)
        assertEquals(1, out.size)
        assertEquals("hi", out[0].content, "higher-importance should be picked")
    }

    @Test
    fun newerWinsAtSameImportance(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        Conversations.appendTo(file, entry("old", 48, 0.5f, content = "old"))
        Conversations.appendTo(file, entry("new", 1, 0.5f, content = "new"))
        val out = Conversations.recentByImportanceFrom(file, n = 1, now = now)
        assertEquals("new", out[0].content)
    }

    @Test
    fun veryHighImportanceCanBeatNewerLowImportance(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        // Recency contribution: now=1h ago, exp(-ln2/24 * 1) ≈ 0.972 vs
        //                       72h ago,  exp(-ln2/24 * 72) ≈ 0.125
        // 72h-old at importance 1.0 → 1.125 total
        // 1h-old at importance 0.05 → 1.022 total
        Conversations.appendTo(file, entry("oldhi", 72, 1.0f, content = "oldhi"))
        Conversations.appendTo(file, entry("newlo", 1, 0.05f, content = "newlo"))
        val out = Conversations.recentByImportanceFrom(file, n = 1, now = now)
        assertEquals("oldhi", out[0].content, "old-but-very-important should beat new-but-trivial")
    }

    @Test
    fun nullImportanceTreatedAsFallback(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        Conversations.appendTo(file, entry("null", 1, null, content = "null-imp"))
        Conversations.appendTo(file, entry("low", 1, 0.1f, content = "explicit-low"))
        val out = Conversations.recentByImportanceFrom(file, n = 1, now = now)
        // null fallback (0.4) should beat explicit 0.1 at same recency.
        assertEquals("null-imp", out[0].content)
    }

    @Test
    fun outputIsChronologicallySorted(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        // Three entries chosen by score; assert they come back in time order.
        Conversations.appendTo(file, entry("c", 1, 0.9f, content = "newest"))
        Conversations.appendTo(file, entry("a", 5, 0.95f, content = "middle"))
        Conversations.appendTo(file, entry("b", 10, 0.99f, content = "oldest"))
        val out = Conversations.recentByImportanceFrom(file, n = 3, now = now)
        assertEquals(listOf("oldest", "middle", "newest"), out.map { it.content })
    }

    @Test
    fun poolCapKeepsOldHighImportanceOutOfReach(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        // First entry: very old, importance=1.0.
        Conversations.appendTo(file, entry("ancient", 999, 1.0f, content = "ancient"))
        // Then 10 newer-but-low entries to push 'ancient' outside a tiny pool.
        repeat(10) { i ->
            Conversations.appendTo(file, entry("n$i", 1, 0.1f, content = "n$i"))
        }
        // poolSize=5 → ancient drops out of the candidate window entirely.
        val out = Conversations.recentByImportanceFrom(
            file, n = 1, poolSize = 5, now = now,
        )
        assertTrue(
            out[0].content != "ancient",
            "ancient was outside pool=5; should not be picked (got ${out[0].content})",
        )
    }

    @Test
    fun mixedNullAndScoredCoexist(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        Conversations.appendTo(file, entry("scored", 2, 0.8f, content = "scored"))
        Conversations.appendTo(file, entry("null1", 2, null, content = "null1"))
        Conversations.appendTo(file, entry("null2", 1, null, content = "null2"))
        val out = Conversations.recentByImportanceFrom(file, n = 3, now = now)
        // All three returned; in chronological order; scored still ranked highest by score
        // but emit is chronological, so check by membership instead.
        assertEquals(3, out.size)
        assertEquals(setOf("scored", "null1", "null2"), out.map { it.content }.toSet())
    }

    @Test
    fun recentNRegressionGuard(@TempDir tmp: Path) {
        // Council 1778164081 fix: assert recent(n) was not accidentally rerouted
        // through the importance-weighted path. Identical input → identical
        // chronological-tail output, regardless of importance values.
        val file = tmp.resolve("c.jsonl")
        Conversations.appendTo(file, entry("a", 5, 0.9f, content = "a", seq = 0))
        Conversations.appendTo(file, entry("b", 4, 0.1f, content = "b", seq = 0))
        Conversations.appendTo(file, entry("c", 3, 0.05f, content = "c", seq = 0))
        Conversations.appendTo(file, entry("d", 2, 1.0f, content = "d", seq = 0))
        Conversations.appendTo(file, entry("e", 1, 0.5f, content = "e", seq = 0))
        val tail = Conversations.recentFrom(file, n = 3)
        assertEquals(listOf("c", "d", "e"), tail.map { it.content },
            "recent(n) must remain pure chronological tail")
    }

    @Test
    fun parsesMalformedTimestampGracefully(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        // Corrupt-ts row should be picked up but contribute 0 recency, so it
        // ranks by importance only — and not crash.
        Conversations.appendTo(file, ConversationEntry(
            role = "user", content = "weirdts", ts = "not-a-real-timestamp",
            msgId = "weird", seq = 0, importance = 0.95f,
        ))
        Conversations.appendTo(file, entry("good", 1, 0.5f, content = "good"))
        val out = Conversations.recentByImportanceFrom(file, n = 2, now = now)
        // Both should land; no crash. Order is by ts string lexicographically
        // since corrupt-ts sort is undefined — just assert size.
        assertEquals(2, out.size)
    }
}
