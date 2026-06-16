package jarvis

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Decorator that retries [inner]'s [complete] call on transport-class failures
 * (IOException / timeout surfaces that the Llm implementations throw on connect
 * failures, HTTP errors, read timeouts, etc.).
 *
 * Retry contract (§0.9-J):
 *   - Retries ONLY on transport-class exceptions (IOException and subclasses).
 *   - Successful-but-malformed output is returned as-is — parse errors are the
 *     CALLER's concern; a malformed response is NOT a transport failure.
 *   - Bounded exponential backoff between attempts (first sleep = [initialBackoffMs],
 *     subsequent sleeps double up to [maxBackoffMs]).
 *   - Total attempts == [maxRetries] + 1 (one original + up to [maxRetries] retries).
 *   - After exhausting retries the last IOException is rethrown unchanged.
 *   - [close] delegates to [inner].
 *
 * Wrap sites (§0 #8 verified seams):
 *   - [FreeLlmApiLlm] inside [productionGraderResolver] (TutorRoutes.kt:193)
 *   - [RelayLlm] at [drillCriticLlmFactory] (TutorRoutes.kt:198)
 *
 * OpenRouterChatLlm keeps its own 429 handling.
 * ClaudeMaxLlm (CLI subprocess) stays unwrapped — outside the two named clients.
 *
 * Closes the pa-kc-006 false-negative class: a transient relay timeout is now
 * RETRY, not a failed grade.
 */
class RetryingLlm(
    private val inner: Llm,
    val maxRetries: Int = 2,
    private val initialBackoffMs: Long = 500L,
    private val maxBackoffMs: Long = 4_000L,
    /** Injectable sleeper — defaults to [delay]; test overrides pass a recording lambda. */
    private val sleeper: suspend (Long) -> Unit = { ms -> delay(ms) },
) : Llm {

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
        responseFormat: String?,
        imagePath: String?,
    ): Pair<String, String> {
        var lastException: IOException? = null
        var backoffMs = initialBackoffMs

        for (attempt in 0..maxRetries) {
            try {
                return inner.complete(messages, maxTokens, responseFormat, imagePath)
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    sleeper(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                }
            }
        }
        throw lastException!!
    }

    override fun close() = inner.close()
}
