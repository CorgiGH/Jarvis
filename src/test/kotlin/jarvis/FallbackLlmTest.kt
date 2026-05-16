package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FallbackLlmTest {

    /** Tracks whether complete() was called and what to do when it is. */
    private class FakeLlm(
        val name: String,
        private val reply: String? = null,
        private val throwOn: (() -> Throwable)? = null,
    ) : Llm {
        var callCount = 0
            private set

        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> {
            callCount++
            throwOn?.let { throw it() }
            return (reply ?: "($name reply)") to name
        }
    }

    private val msg = listOf(ChatMessage(role = "user", content = "ping"))

    @Test
    fun primarySuccessReturnsPrimaryFallbackUntouched() = runBlocking {
        val primary = FakeLlm(name = "primary", reply = "from-primary")
        val fallback = FakeLlm(name = "fallback")
        val llm = FallbackLlm(primary, fallback)

        val (text, tag) = llm.complete(msg, maxTokens = 100)

        assertEquals("from-primary", text)
        assertEquals("primary", tag)
        assertEquals(1, primary.callCount)
        assertEquals(0, fallback.callCount, "fallback must not be called when primary succeeds")
    }

    @Test
    fun primaryIoExceptionFallsThroughToFallback() = runBlocking {
        val primary = FakeLlm(name = "primary", throwOn = { IOException("connect refused") })
        val fallback = FakeLlm(name = "fallback", reply = "from-fallback")
        val llm = FallbackLlm(primary, fallback)

        val (text, tag) = llm.complete(msg, maxTokens = 100)

        assertEquals("from-fallback", text)
        assertEquals("fallback", tag)
        assertEquals(1, primary.callCount)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun primaryIllegalStateExceptionFallsThroughToFallback() = runBlocking {
        // Llm subprocess providers (ClaudeMaxLlm, CopilotLlm) use error() which
        // throws IllegalStateException — must be caught for the fallback to
        // engage when claude --print exits non-zero.
        val primary = FakeLlm(name = "primary", throwOn = { IllegalStateException("claude CLI exit 1: foo") })
        val fallback = FakeLlm(name = "fallback", reply = "from-fallback")
        val llm = FallbackLlm(primary, fallback)

        val (text, _) = llm.complete(msg, maxTokens = 100)

        assertEquals("from-fallback", text)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun bothThrowSurfaceCompositeError() = runBlocking {
        val primary = FakeLlm(name = "primary", throwOn = { IOException("connect refused") })
        val fallback = FakeLlm(name = "fallback", throwOn = { IllegalStateException("copilot CLI exit 402: no quota") })
        val llm = FallbackLlm(primary, fallback)

        val ex = assertFails {
            llm.complete(msg, maxTokens = 100)
        }
        val message = ex.message ?: ""
        assertTrue(message.contains("primary"), "error names primary provider: '$message'")
        assertTrue(message.contains("fallback"), "error names fallback provider: '$message'")
        assertTrue(
            message.contains("connect refused") || message.contains("402"),
            "error includes at least one underlying cause: '$message'",
        )
    }

    @Test
    fun fallbackTagPreservedForObservability() = runBlocking {
        // Per-turn provider tag (council Pragmatist concern): caller must be
        // able to tell which path served. Tag from the underlying Llm is
        // returned verbatim, NOT wrapped or overridden.
        val primary = FakeLlm(name = "primary", throwOn = { IOException("down") })
        val fallback = FakeLlm(name = "copilot-cli", reply = "ok")
        val llm = FallbackLlm(primary, fallback)

        val (_, tag) = llm.complete(msg, maxTokens = 100)
        assertEquals("copilot-cli", tag, "fallback's own tag must surface so the caller can attribute the turn")
        assertFalse(tag.contains("primary"), "tag must not leak primary identity when fallback served")
    }
}
