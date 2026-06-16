package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/**
 * Task 6 — TDD for RetryingLlm decorator (carried follow-up #2, §0.9-J).
 *
 * Tests:
 *   1. transport failure then success → one retry, success returned
 *   2. persistent failure → exhausts maxRetries then rethrows (bounded: total attempts == maxRetries+1)
 *   3. malformed-but-successful completion → NO retry (returned as-is)
 *   4. backoff invoked between attempts (injectable sleeper — no real sleeps)
 *   5. close() delegates to inner
 */
class RetryingLlmTest {

    // ---- fake Llm helpers ----

    /** Llm that throws [IOException] for the first [failCount] calls, then succeeds. */
    private class TransientFailLlm(
        private val failCount: Int,
        private val successResponse: Pair<String, String> = "ok" to "fake-model",
    ) : Llm {
        var callCount = 0
        var closed = false

        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
            imagePath: String?,
        ): Pair<String, String> {
            callCount++
            if (callCount <= failCount) throw IOException("transport failure #$callCount")
            return successResponse
        }

        override fun close() { closed = true }
    }

    /** Llm that ALWAYS throws [IOException]. */
    private class AlwaysFailLlm(private val maxRetries: Int) : Llm {
        var callCount = 0
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
            imagePath: String?,
        ): Pair<String, String> {
            callCount++
            throw IOException("permanent failure #$callCount")
        }
    }

    /** Llm that returns malformed output (not JSON) — NOT a transport failure. */
    private class MalformedOutputLlm : Llm {
        var callCount = 0
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
            imagePath: String?,
        ): Pair<String, String> {
            callCount++
            return "this is definitely not json ¯\\_(ツ)_/¯" to "bad-model"
        }
    }

    /** Injectable sleeper that records calls instead of sleeping. */
    private class RecordingSleeper {
        val sleptMs = mutableListOf<Long>()
        val sleeper: suspend (Long) -> Unit = { ms -> sleptMs.add(ms) }
    }

    private val noMessages = listOf(ChatMessage("user", "test"))

    // ---- test 1: transport failure then success → one retry ----

    @Test
    fun `transport failure then success returns success after one retry`() = runBlocking {
        val inner = TransientFailLlm(failCount = 1, successResponse = "result" to "model-x")
        val sleeper = RecordingSleeper()
        val retrying = RetryingLlm(inner, maxRetries = 2, sleeper = sleeper.sleeper)

        val result = retrying.complete(noMessages)
        assertEquals("result" to "model-x", result)
        assertEquals(2, inner.callCount, "expected 2 calls: 1 fail + 1 success")
        assertEquals(1, sleeper.sleptMs.size, "expected exactly 1 backoff sleep between attempts")
    }

    // ---- test 2: persistent failure → exhausts maxRetries then rethrows (bounded) ----

    @Test
    fun `persistent failure exhausts maxRetries and rethrows`() = runBlocking {
        val maxRetries = 2
        val inner = AlwaysFailLlm(maxRetries)
        val sleeper = RecordingSleeper()
        val retrying = RetryingLlm(inner, maxRetries = maxRetries, sleeper = sleeper.sleeper)

        val ex = assertThrows<IOException> {
            runBlocking { retrying.complete(noMessages) }
        }
        assertNotNull(ex)

        // Bounded: total attempts == maxRetries + 1
        assertEquals(maxRetries + 1, inner.callCount,
            "total attempts must be maxRetries+1 = ${maxRetries + 1}; got ${inner.callCount}")
        assertEquals(maxRetries, sleeper.sleptMs.size,
            "expected $maxRetries backoff sleeps (one between each retry); got ${sleeper.sleptMs.size}")
    }

    // ---- test 3: malformed-but-successful → NO retry ----

    @Test
    fun `malformed but successful completion is not retried`() = runBlocking {
        val inner = MalformedOutputLlm()
        val sleeper = RecordingSleeper()
        val retrying = RetryingLlm(inner, maxRetries = 2, sleeper = sleeper.sleeper)

        // Should return the malformed output as-is — no IOException, so no retry
        val (text, model) = retrying.complete(noMessages)
        assertTrue(text.contains("not json"), "malformed output should be returned as-is")
        assertEquals(1, inner.callCount, "malformed-but-successful must NOT be retried")
        assertTrue(sleeper.sleptMs.isEmpty(), "no sleep for non-failure path")
    }

    // ---- test 4: backoff grows between retries (injectable clock) ----

    @Test
    fun `backoff is invoked between each retry attempt`() = runBlocking {
        val inner = TransientFailLlm(failCount = 2, successResponse = "done" to "m")
        val sleeper = RecordingSleeper()
        val retrying = RetryingLlm(inner, maxRetries = 3, initialBackoffMs = 100L, sleeper = sleeper.sleeper)

        retrying.complete(noMessages)

        // 2 failures → 2 backoff sleeps before the 3rd call succeeds
        assertEquals(2, sleeper.sleptMs.size, "expected 2 sleeps for 2 retries")
        // Each sleep must be > 0 (backoff is applied)
        assertTrue(sleeper.sleptMs.all { it > 0 }, "all backoff values must be > 0ms")
    }

    // ---- test 5: close() delegates ----

    @Test
    fun `close delegates to inner`() {
        val inner = TransientFailLlm(failCount = 0)
        val retrying = RetryingLlm(inner, maxRetries = 1)
        retrying.close()
        assertTrue(inner.closed, "RetryingLlm.close() must delegate to inner.close()")
    }
}
