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

    /**
     * Outcome shape:
     *  - text: the sanitized + truncated query text (may be empty)
     *  - shouldFetch: true when the query is well-formed enough for retrieval
     *  - drillSelfPaste: true when the user's selection is a near-verbatim paste
     *    of the active drill statement — caller MUST short-circuit the LLM call
     *    and return a "use the drill answer textarea instead" reply per the
     *    2026-05-13 UX-reframe council's testing-effect guardrail.
     */
    data class Query(
        val text: String,
        val shouldFetch: Boolean,
        val drillSelfPaste: Boolean = false,
    )

    private const val MIN_LEN = 12
    private const val MAX_LEN = 300
    private const val MIN_WORD_RUNS = 2
    private const val MIN_WORD_LEN = 3
    private const val DRILL_PASTE_JACCARD = 0.7
    private val WORD_RUN = Regex("[A-Za-z]{$MIN_WORD_LEN,}")
    private val NON_ALNUM = Regex("[^a-z0-9 ]")
    private val WHITESPACE = Regex("\\s+")

    fun build(env: SidekickEnvelope): Query {
        val raw = env.selection?.trim().orEmpty()
        val sanitized = sanitize(raw)

        // Drill-self-paste gate (council mitigation B): if the user selected
        // (close to) the drill statement itself, refuse retrieval — the
        // testing-effect rule requires Alex to attempt the drill, not have
        // the LLM clarify it back at him.
        val drillRef = env.drillStatement?.trim().orEmpty()
        val pasteHit = drillRef.isNotBlank() && raw.isNotBlank() &&
            jaccard(raw, drillRef) >= DRILL_PASTE_JACCARD

        val candidate = if (sanitized.length < MIN_LEN) {
            env.anchorText?.trim()?.let { sanitize(it) }.orEmpty()
        } else sanitized

        val truncated = candidate.take(MAX_LEN)
        val ok = !pasteHit &&
            truncated.length in MIN_LEN..MAX_LEN &&
            hasContentChars(truncated)
        return Query(text = truncated, shouldFetch = ok, drillSelfPaste = pasteHit)
    }

    private fun tokens(s: String): Set<String> =
        s.lowercase()
            .replace(NON_ALNUM, " ")
            .split(WHITESPACE)
            .filter { it.length >= 2 }
            .toSet()

    private fun jaccard(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        return inter.toDouble() / union.toDouble()
    }

    private fun sanitize(s: String): String =
        s.replace("<retrieved_context", "&lt;retrieved_context", ignoreCase = true)
            .replace("</retrieved_context>", "&lt;/retrieved_context&gt;", ignoreCase = true)
            .replace("```", "` ``")

    /**
     * Require ≥2 alphabetic word-runs of ≥3 chars. Filters out LaTeX salad
     * like `\frac{1}{2b} e^{-|x-\mu|/b}` (1 run: "frac") while admitting
     * prose like "Laplace distribution" (2+ runs).
     */
    private fun hasContentChars(s: String): Boolean =
        WORD_RUN.findAll(s).count() >= MIN_WORD_RUNS
}
