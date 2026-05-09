package jarvis.tutor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-function tests on dispatch logic. The LLM round-trip itself
 * (JarvisToolset.chat) requires a real OpenRouter endpoint with
 * tool_use support; covered by an integration smoke test rather than
 * mocked here (network mocking the OpenAI-compatible tools field
 * across multi-turn round-trips is a heavy fake without payoff).
 */
class JarvisToolDefsTest {

    @Test
    fun unknownToolReturnsExplicitMessage() {
        val r = JarvisToolDefs.dispatch("not_a_tool", "{}")
        assertEquals("unknown tool: not_a_tool", r)
    }

    @Test
    fun searchArchivalRejectsBadJsonArgs() {
        val r = JarvisToolDefs.dispatch("search_archival", "not json")
        assertTrue(r.startsWith("bad args json"), "expected json error: $r")
    }

    @Test
    fun searchArchivalRejectsNonObject() {
        val r = JarvisToolDefs.dispatch("search_archival", "[]")
        assertTrue(r.contains("bad args"), r)
    }

    @Test
    fun searchArchivalRejectsBlankQuery() {
        val r = JarvisToolDefs.dispatch("search_archival", """{"query":""}""")
        assertEquals("search_archival: query required", r)
    }

    @Test
    fun searchArchivalReturnsNoMatchesGracefully() {
        // The real archival corpus may or may not have a hit for this
        // string. Either way the response must NOT throw — graceful
        // empty result.
        val r = JarvisToolDefs.dispatch(
            "search_archival",
            """{"query":"zzzunlikelytohitanythingzzz","k":3}""",
        )
        assertTrue(
            r.contains("no matches") || r.contains("[lexical") || r.contains("[semantic") || r.contains("[entity"),
            "graceful response: ${r.take(200)}",
        )
    }

    @Test
    fun toolDefsExposeSearchArchivalSchema() {
        val defs = JarvisToolDefs.openAiToolDefs.toString()
        assertTrue(defs.contains("\"name\":\"search_archival\""), defs.take(200))
        assertTrue(defs.contains("\"query\""), defs.take(200))
        assertTrue(defs.contains("\"required\":[\"query\"]"), defs.take(200))
    }
}
