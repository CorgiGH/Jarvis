package jarvis

import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatToolsExtendedTest {

    private fun llm(replies: List<String>, model: String = "test-model"): Llm {
        val q = ArrayDeque(replies)
        return object : Llm {
            override suspend fun complete(
                messages: List<ChatMessage>,
                maxTokens: Int,
            ): Pair<String, String> = q.removeFirst() to model
        }
    }

    @Test
    fun parsesAnyToolName() {
        val text = "before [[search: foo]] middle [[read: bar.md]] [[recall: baz]] end"
        val calls = ChatTools.parseToolCalls(text)
        assertEquals(3, calls.size)
        assertEquals(listOf("search", "read", "recall"), calls.map { it.name })
        assertEquals(listOf("foo", "bar.md", "baz"), calls.map { it.args })
    }

    @Test
    fun readToolDispatchedToCorrectExecutor() = runBlocking {
        var seen: ChatTools.ToolCall? = null
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "Need full file. [[read: lecture3/notes.md]]",
                    "Per the file: <summary>",
                ),
            ),
            messages = listOf(ChatMessage("user", "summarize my lecture3 notes")),
            executor = { call ->
                seen = call
                "## lecture3 notes\nfull content here"
            },
        )
        assertEquals("read", seen?.name)
        assertEquals("lecture3/notes.md", seen?.args)
        assertEquals("Per the file: <summary>", reply)
    }

    @Test
    fun multipleDifferentToolsInOneTurn() = runBlocking {
        val executed = mutableListOf<String>()
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "[[search: kotlin]] [[recall: paging algorithms]]",
                    "Combined: search + recall both ran.",
                ),
            ),
            messages = listOf(ChatMessage("user", "do both")),
            executor = { call ->
                executed += "${call.name}:${call.args}"
                "result for ${call.name}"
            },
        )
        assertEquals(2, executed.size)
        assertEquals(listOf("search:kotlin", "recall:paging algorithms"), executed)
        assertTrue("Combined" in reply)
    }
}
