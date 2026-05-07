package jarvis

import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatToolsTest {

    private fun llm(replies: List<String>, model: String = "test-model"): Llm {
        val q = ArrayDeque(replies)
        return object : Llm {
            override suspend fun complete(
                messages: List<ChatMessage>,
                maxTokens: Int,
            ): Pair<String, String> {
                require(q.isNotEmpty()) { "LLM called more times than scripted (got ${messages.size} messages)" }
                return q.removeFirst() to model
            }
        }
    }

    @Test
    fun parsesSearchMarker() {
        val calls = ChatTools.parseToolCalls("Let me check. [[search: probabilities]] standby.")
        assertEquals(1, calls.size)
        assertEquals("search", calls[0].name)
        assertEquals("probabilities", calls[0].args)
    }

    @Test
    fun parsesMultipleMarkers() {
        val calls = ChatTools.parseToolCalls(
            "[[search: kotlin coroutines]] and [[search: ktor]]",
        )
        assertEquals(listOf("kotlin coroutines", "ktor"), calls.map { it.args })
    }

    @Test
    fun parsesNoMarkers() {
        assertEquals(emptyList(), ChatTools.parseToolCalls("plain reply with no tools"))
    }

    @Test
    fun returnsReplyAsIsWhenNoToolUse() = runBlocking {
        val (reply, model) = ChatTools.runTurnWith(
            client = llm(listOf("hello world")),
            messages = listOf(ChatMessage("user", "hi")),
            executor = { _ -> error("should not invoke executor") },
        )
        assertEquals("hello world", reply)
        assertEquals("test-model", model)
    }

    @Test
    fun executesToolThenReturnsFinalReply() = runBlocking {
        var executed = ""
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "Need archival data. [[search: kotlin]]",
                    "Based on archival results: kotlin is the language.",
                ),
            ),
            messages = listOf(ChatMessage("user", "what's in my notes about kotlin?")),
            executor = { call ->
                executed = "${call.name}:${call.args}"
                "doc.md (hits=3)\n## Kotlin notes\nstuff"
            },
        )
        assertEquals("search:kotlin", executed)
        assertEquals("Based on archival results: kotlin is the language.", reply)
    }

    @Test
    fun stripsResidualMarkersFromFinalReply() = runBlocking {
        // Even if the second-pass model still emits a marker (because it's
        // confused), the user-facing reply has the marker stripped.
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "[[search: x]]",
                    "Done. [[search: leftover]] all good.",
                ),
            ),
            messages = listOf(ChatMessage("user", "go")),
            executor = { _ -> "(no matches)" },
        )
        assertTrue("[[search:" !in reply, "marker stripped from final reply: $reply")
        assertTrue("Done." in reply)
        assertTrue("all good." in reply)
    }

    @Test
    fun stopsAtMaxIterationsEvenIfModelKeepsToolCalling() = runBlocking {
        var calls = 0
        // Model emits markers on every turn — runtime must cap at MAX_ITERATIONS+1 LLM calls.
        val (reply, _) = ChatTools.runTurnWith(
            client = object : Llm {
                override suspend fun complete(
                    messages: List<ChatMessage>,
                    maxTokens: Int,
                ): Pair<String, String> {
                    calls++
                    return "[[search: q$calls]] still searching" to "m"
                }
            },
            messages = listOf(ChatMessage("user", "go")),
            executor = { _ -> "(no matches)" },
        )
        // Cap is MAX_ITERATIONS=1 so total LLM calls = 2 (initial + 1 follow-up).
        assertEquals(2, calls, "expected exactly 2 LLM calls under cap, got $calls")
        assertTrue("[[search:" !in reply, "final reply marker-stripped")
    }

    @Test
    fun stripsAnyUnrecognizedMarkerOnFinalReply() = runBlocking {
        // Model is supposed to use [[search: ...]] but if it emits a marker
        // shape we don't recognize (e.g., a hallucinated tool), the user
        // shouldn't see the literal marker.
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(listOf("[[browse: example.com]] no tool used here.")),
            messages = listOf(ChatMessage("user", "go")),
            executor = { _ -> error("should not invoke executor") },
        )
        assertTrue("[[" !in reply, "any marker shape stripped: $reply")
        assertTrue("no tool used here." in reply)
    }
}
