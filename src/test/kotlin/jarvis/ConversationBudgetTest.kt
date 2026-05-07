package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationBudgetTest {

    private fun seed(file: Path, count: Int, contentLen: Int) {
        repeat(count) { i ->
            Conversations.appendTo(
                file,
                ConversationEntry(
                    role = if (i % 2 == 0) "user" else "assistant",
                    content = "x".repeat(contentLen) + "_$i",
                    ts = "2026-05-08T12:00:${"%02d".format(i % 60)}.${"%03d".format(i)}Z",
                    msgId = "m$i",
                    seq = if (i % 2 == 0) 0 else 1,
                ),
            )
        }
    }

    @Test
    fun underBudgetReturnsAll(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        seed(file, count = 10, contentLen = 100)
        val out = Conversations.recentAsChatMessagesWithBudgetFrom(
            file, charBudget = 60_000, maxN = 30,
        )
        assertEquals(10, out.size, "all fit under budget")
    }

    @Test
    fun overBudgetTrimsOldest(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        // 30 entries × 1000 chars = 30K — set budget to 5K, expect ~5 newest.
        seed(file, count = 30, contentLen = 1000)
        val out = Conversations.recentAsChatMessagesWithBudgetFrom(
            file, charBudget = 5_000, maxN = 30,
        )
        assertTrue(out.size in 4..6, "trimmed to ~5 entries within budget (got ${out.size})")
        // Verify newest-end retained.
        val lastContent = out.last().content
        assertTrue(lastContent.contains("_29"), "newest entry kept")
    }

    @Test
    fun budgetSmallerThanSingleEntryReturnsLatestOne(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        seed(file, count = 5, contentLen = 5_000)
        val out = Conversations.recentAsChatMessagesWithBudgetFrom(
            file, charBudget = 100, maxN = 30,
        )
        // At least the latest entry returns, even when it exceeds budget on
        // its own — the "if out.isNotEmpty()" guard is the protection.
        assertEquals(1, out.size)
    }

    @Test
    fun emptyFileReturnsEmpty(@TempDir tmp: Path) {
        val out = Conversations.recentAsChatMessagesWithBudgetFrom(
            tmp.resolve("nope.jsonl"), 60_000, 30,
        )
        assertEquals(emptyList(), out)
    }

    @Test
    fun maxNCapsBeforeBudget(@TempDir tmp: Path) {
        val file = tmp.resolve("c.jsonl")
        seed(file, count = 50, contentLen = 100)
        val out = Conversations.recentAsChatMessagesWithBudgetFrom(
            file, charBudget = 99_999, maxN = 5,
        )
        assertEquals(5, out.size, "maxN caps the pool even when budget allows more")
    }
}
