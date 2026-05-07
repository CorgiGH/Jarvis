package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * Letta-style: chat history is DERIVED from conversations.jsonl, not held in-memory.
 * Server restart preserves context; in-memory list can't grow unbounded.
 */
class ConversationsHistoryTest {

    @Test
    fun recentAsChatMessagesMapsRoleAndContent(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        ChatTurnWriter.appendTo(file, "q1", "a1", "model1")
        ChatTurnWriter.appendTo(file, "q2", "a2", "model2")

        val msgs = Conversations.recentAsChatMessagesFrom(file, n = 4)
        assertEquals(4, msgs.size)
        assertEquals(ChatMessage("user", "q1"), msgs[0])
        assertEquals(ChatMessage("assistant", "a1"), msgs[1])
        assertEquals(ChatMessage("user", "q2"), msgs[2])
        assertEquals(ChatMessage("assistant", "a2"), msgs[3])
    }

    @Test
    fun recentAsChatMessagesEmptyWhenNoFile(@TempDir tmp: Path) {
        val absent = tmp.resolve("never.jsonl")
        assertEquals(emptyList(), Conversations.recentAsChatMessagesFrom(absent, n = 10))
    }

    @Test
    fun recentAsChatMessagesCapsAtN(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        repeat(10) { i ->
            ChatTurnWriter.appendTo(file, "q$i", "a$i", "m")
        }
        // 10 turns × 2 rows = 20 rows total. Cap n=6 returns last 6.
        val msgs = Conversations.recentAsChatMessagesFrom(file, n = 6)
        assertEquals(6, msgs.size)
        assertEquals("q7", msgs[0].content)
        assertEquals("a9", msgs[5].content)
    }
}
