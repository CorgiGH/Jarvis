package jarvis.tutor

/**
 * Decides whether the user's text selection is a usable retrieval query for
 * the sidekick pre-fetch step, and sanitizes it before any prompt embedding.
 *
 * Spec: docs/superpowers/specs/2026-05-12-sidekick-prefetch-design.md §4.1
 * Council mitigations bundled here:
 *  - Length gate 12..300 chars (Devil's Advocate + Domain Expert + Pragmatist).
 *  - anchor_text fallback when selection too short (Domain Expert).
 *  - Strip `</retrieved_context>` literal to prevent paste-injection
 *    (Risk Analyst HIGH#2).
 *  - Reject pure-LaTeX / operator-only selections via letter-or-digit count.
 */
object SelectionQueryBuilder {

    data class Query(val text: String, val shouldFetch: Boolean)

    private const val MIN_LEN = 12
    private const val MAX_LEN = 300
    private const val MIN_CONTENT_CHARS = 4

    fun build(env: SidekickEnvelope): Query {
        val raw = env.selection?.trim().orEmpty()
        val sanitized = sanitize(raw)

        val candidate = if (sanitized.length < MIN_LEN) {
            env.anchorText?.trim()?.let { sanitize(it) }.orEmpty()
        } else sanitized

        val truncated = candidate.take(MAX_LEN)
        val ok = truncated.length in MIN_LEN..MAX_LEN && hasContentChars(truncated)
        return Query(text = truncated, shouldFetch = ok)
    }

    private fun sanitize(s: String): String =
        s.replace("<retrieved_context", "&lt;retrieved_context", ignoreCase = true)
            .replace("</retrieved_context>", "&lt;/retrieved_context&gt;", ignoreCase = true)
            .replace("```", "` ``")

    private fun hasContentChars(s: String): Boolean =
        s.count { it.isLetterOrDigit() } >= MIN_CONTENT_CHARS
}
