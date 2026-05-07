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
        // Even on the final pass (after MAX_ITERATIONS=3 rounds of tool use)
        // any residual marker the model emits is stripped from user reply.
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "[[search: x]]",
                    "[[search: y]]",
                    "[[search: z]]",
                    "Done. [[search: leftover]] all good.",  // final pass
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
        // Model emits markers on every turn — runtime must cap at
        // MAX_ITERATIONS+1 LLM calls. With MAX=3, that's 4 calls.
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
        // F3 — Cap is MAX_ITERATIONS=3 so total LLM calls = 4 (initial + 3 follow-ups).
        assertEquals(4, calls, "expected exactly 4 LLM calls under cap, got $calls")
        assertTrue("[[search:" !in reply, "final reply marker-stripped")
    }

    @Test
    fun chainedSearchThenReadThenRespond() = runBlocking {
        // F3 — verify the model can chain >1 tool round to land at a useful reply.
        var seq = 0
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "[[search: kotlin]]",            // round 1
                    "[[read: notes/kotlin.md]]",     // round 2
                    "Notes say: sealed classes.",    // final
                ),
            ),
            messages = listOf(ChatMessage("user", "kotlin notes?")),
            executor = { call -> seq++; "tool ${call.name} result $seq" },
        )
        assertEquals(2, seq, "two tool calls executed in chain")
        assertEquals("Notes say: sealed classes.", reply)
    }

    @Test
    fun stripsAnyUnrecognizedMarkerOnFinalReply() = runBlocking {
        // Model uses search on the first round, then on the FINAL round emits
        // an unrecognized marker shape. The user shouldn't see the literal
        // marker even though the parser would flag it as a tool call.
        val (reply, _) = ChatTools.runTurnWith(
            client = llm(
                listOf(
                    "[[search: foo]]",                          // round 1: real tool
                    "[[search: bar]]",                          // round 2
                    "[[search: baz]]",                          // round 3
                    "all done. [[browse: example.com]] bye.",   // final — stripped
                ),
            ),
            messages = listOf(ChatMessage("user", "go")),
            executor = { call ->
                if (call.name == "search") "(no matches)"
                else "(unknown tool: ${call.name})"
            },
        )
        assertTrue("[[" !in reply, "any marker shape stripped: $reply")
        assertTrue("all done." in reply)
        assertTrue("bye." in reply)
    }
}
