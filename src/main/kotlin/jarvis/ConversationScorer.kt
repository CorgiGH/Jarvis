package jarvis

/**
 * Phase 1.2 — heuristic importance scorer for [ConversationEntry] rows.
 *
 * Pure function: (entry) → Float in 0..1. No I/O, no mutation. Different shape
 * from [ActivityScorer] — chat turns score on textual signals only; no sliding
 * "recent" window required since each turn is judged on its own content.
 *
 * Lexicon kept under 25 entries by design — the autonomous-roadmap calls for a
 * council convene if it grows past 50 (subjective scoring is council-y). v1
 * stays well under that.
 *
 * Score composition:
 *   base 0.4
 * + 0.3 if pin-marker phrase ("remember this", "important:", "key insight", …)
 * + 0.2 if emotion word ("frustrated", "stuck", "breakthrough", …)
 * - 0.1 if short question with no other strong signal (Q→A is ephemeral)
 * - 0.05 if content is dominated by a code block and no emotion / pin signal
 * → coerceIn(0f, 1f)
 */
object ConversationScorer {

    fun score(entry: ConversationEntry): Float = score(entry.content)

    fun score(content: String): Float {
        val text = content.lowercase()
        val pin = if (hasPinMarker(text)) 0.3f else 0f
        val emotion = if (hasEmotionWord(text)) 0.2f else 0f
        val strongSignal = pin > 0f || emotion > 0f
        val questionPenalty = if (!strongSignal && isShortQuestion(text)) 0.1f else 0f
        val codePenalty = if (!strongSignal && isCodeDominant(content)) 0.05f else 0f
        return (0.4f + pin + emotion - questionPenalty - codePenalty).coerceIn(0f, 1f)
    }

    private val PIN_MARKERS = listOf(
        "remember this",
        "remember that",
        "remember:",
        "important:",
        "important note",
        "key insight",
        "key takeaway",
        "this matters",
        "don't forget",
        "tldr",
        "tl;dr",
        "note to self",
    )

    private fun hasPinMarker(lower: String): Boolean =
        PIN_MARKERS.any { lower.contains(it) }

    private val EMOTION_WORDS = listOf(
        "frustrated", "frustrat", // also catches "frustrating"
        "stuck", "blocked",
        "breakthrough",
        "excited", "exciting",
        "anxious", "worried",
        "panic",
        "love it", "hate it",
        "scared",
        "decided", "decision",
        "committed",
        "broken", "broke",
    )

    private fun hasEmotionWord(lower: String): Boolean =
        EMOTION_WORDS.any { lower.contains(it) }

    /** Heuristic for "this is just a quick Q the model answered": ends with a
     *  question mark, no extra body, no code, < 200 chars. */
    private fun isShortQuestion(lower: String): Boolean {
        if (!lower.contains("?")) return false
        if (lower.length > 200) return false
        if (lower.contains("```")) return false
        return true
    }

    /** Code-dominant: a fenced ``` block whose body is most of the content. */
    private fun isCodeDominant(content: String): Boolean {
        val opens = content.indexOf("```")
        if (opens < 0) return false
        val closes = content.indexOf("```", opens + 3)
        if (closes < 0) return false
        val codeLen = closes - (opens + 3)
        return codeLen >= content.length / 2
    }
}
