package jarvis

import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatToolsRoundThreeTest {

    private fun llm(replies: List<String>): Llm {
        val q = ArrayDeque(replies)
        return object : Llm {
            override suspend fun complete(
                messages: List<ChatMessage>,
                maxTokens: Int,
                responseFormat: String?,
            ): Pair<String, String> = q.removeFirst() to "test"
        }
    }

    @Test
    fun activityToolParsedAndDispatched() = runBlocking {
        var seen: ChatTools.ToolCall? = null
        ChatTools.runTurnWith(
            client = llm(listOf("[[activity: 72]]", "Looked back 72h.")),
            messages = listOf(ChatMessage("user", "what was i doing 3 days ago")),
            executor = { call ->
                seen = call
                "  [2026-05-04T08:00Z] Code: file.kt"
            },
        )
        assertEquals("activity", seen?.name)
        assertEquals("72", seen?.args)
    }

    @Test
    fun statsToolParsedAndDispatched() = runBlocking {
        var seen: ChatTools.ToolCall? = null
        ChatTools.runTurnWith(
            client = llm(listOf("[[stats: now]]", "Got it.")),
            messages = listOf(ChatMessage("user", "how much have we built")),
            executor = { call ->
                seen = call
                "conversations.jsonl rows: 14\nwiki.md sections: 23"
            },
        )
        assertEquals("stats", seen?.name)
    }

    @Test
    fun subToolParsedWithNameAndQuery() {
        val calls = ChatTools.parseToolCalls(
            "[[sub: judgment I have been productive]]",
        )
        assertEquals(1, calls.size)
        assertEquals("sub", calls[0].name)
        assertEquals("judgment I have been productive", calls[0].args)
    }

    @Test
    fun subToolDispatchedThroughExecutor() = runBlocking {
        var seen: ChatTools.ToolCall? = null
        ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "[[sub: judgment I shipped a lot]]",
                    "Subsystem said you did ship.",
                ),
            ),
            messages = listOf(ChatMessage("user", "judge me")),
            executor = { call ->
                seen = call
                "VERDICT: aligned\nFINDING: 15 commits today, all pushed."
            },
        )
        assertEquals("sub", seen?.name)
        assertEquals("judgment I shipped a lot", seen?.args)
    }
}
