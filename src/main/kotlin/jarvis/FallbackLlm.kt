package jarvis

import kotlinx.coroutines.CancellationException

/**
 * Llm wrapper that tries [primary] first; on failure, falls through to
 * [fallback]. Used to keep chat alive when the primary provider has gone
 * dark — e.g. PC running RelayLlm sleeps, claude CLI session expires, or
 * Copilot CLI hits its 402 monthly quota wall.
 *
 * Council 2026-05-08 consensus: deliberately NO TCP probe + 30s cache.
 * At tens-of-turns/day we just try-and-fail; one fewer state machine, no
 * cache-poisoning windows where probe says PC reachable but the
 * subprocess is hung waiting on tty. Add caching back only if observation
 * shows real overhead (Pragmatist KEY CONCERN).
 *
 * The Pair<reply, tag> from whichever provider served is passed through
 * verbatim, so callers can attribute each turn to its actual provider.
 * Without per-turn tags, a 'weird answer' debug session 3 months from
 * now is impossible to triage.
 *
 * CancellationException is rethrown immediately — coroutine cancellation
 * must not silently retarget to the fallback, since the caller has
 * explicitly asked us to stop.
 *
 * If both providers throw, we surface a composite IllegalStateException
 * naming both underlying messages so the eventual phone-side error is
 * actionable, not a 300s spinner of silence.
 */
class FallbackLlm(
    private val primary: Llm,
    private val fallback: Llm,
) : Llm {

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): Pair<String, String> {
        val primaryError: Throwable = try {
            return primary.complete(messages, maxTokens)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            t
        }

        try {
            return fallback.complete(messages, maxTokens)
        } catch (ce: CancellationException) {
            throw ce
        } catch (fallbackError: Throwable) {
            error(
                "all LLM providers failed. " +
                    "primary=${primaryError.message?.take(160) ?: primaryError::class.simpleName}; " +
                    "fallback=${fallbackError.message?.take(160) ?: fallbackError::class.simpleName}",
            )
        }
    }

    override fun close() {
        runCatching { primary.close() }
        runCatching { fallback.close() }
    }
}
