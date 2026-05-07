package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatToolsWriteTest {

    private fun llm(replies: List<String>): Llm {
        val q = ArrayDeque(replies)
        return object : Llm {
            override suspend fun complete(
                messages: List<ChatMessage>,
                maxTokens: Int,
            ): Pair<String, String> = q.removeFirst() to "test"
        }
    }

    @Test
    fun timeMarkerIsParsed() {
        val calls = ChatTools.parseToolCalls("[[time: now]] what time is it")
        assertEquals(1, calls.size)
        assertEquals("time", calls[0].name)
    }

    @Test
    fun rememberMarkerIsParsed() {
        val calls = ChatTools.parseToolCalls("[[remember: I prefer terse replies]]")
        assertEquals(1, calls.size)
        assertEquals("remember", calls[0].name)
        assertEquals("I prefer terse replies", calls[0].args)
    }

    @Test
    fun rememberWritesToWikiFileViaMemoryWikiAppendTo(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        // Direct check on MemoryWiki.appendTo (the seam ChatTools uses indirectly
        // via executeTool default executor). The executor side is provider-locked
        // to Config.wikiFile; this test pins the storage contract.
        MemoryWiki.appendTo(wiki, "user note (model-pinned)", "remember this fact")
        val content = wiki.toFile().readText()
        assertTrue("user note (model-pinned)" in content)
        assertTrue("remember this fact" in content)
    }

    @Test
    fun wikiToolDispatchedThroughExecutor() = runBlocking {
        var seen: ChatTools.ToolCall? = null
        ChatTools.runTurnWith(
            client = llm(listOf("[[wiki: caveman]]", "Found it.")),
            messages = listOf(ChatMessage("user", "what did i pin about caveman replies?")),
            executor = { call ->
                seen = call
                "## [2026-05-07 13:34 UTC] user note  (hits=2)\nUser preference — caveman replies."
            },
        )
        assertEquals("wiki", seen?.name)
        assertEquals("caveman", seen?.args)
    }

    @Test
    fun rememberToolDispatchedThroughExecutorWiring() = runBlocking {
        var observed: ChatTools.ToolCall? = null
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "Sure. [[remember: user wants terse replies]]",
                    "Saved.",
                ),
            ),
            messages = listOf(ChatMessage("user", "remember that I want terse replies")),
            executor = { call ->
                observed = call
                "(remembered: …)"
            },
        )
        assertEquals("remember", observed?.name)
        assertEquals("user wants terse replies", observed?.args)
        assertEquals("Saved.", reply)
    }
}
