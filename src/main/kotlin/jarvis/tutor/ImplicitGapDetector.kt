package jarvis.tutor

/**
 * Phase 7.4 implicit-gap detector. Heuristic regex on user message in
 * the chat path: when the user asks "what is X" / "I don't get Y" /
 * "how do I Z", the system can auto-flag X/Y/Z as a gap topic without
 * the LLM having to emit a `<gap>` envelope.
 *
 * Detector is a pure function — persistence (POST to /api/v1/gap) is
 * the route layer's job. Today the route layer doesn't yet call this;
 * end-to-end wiring tracked as backlog (KnowledgeGapRepo dependency
 * injection through JarvisToolset).
 */
object ImplicitGapDetector {
    private val TRIGGER = Regex(
        """(?i)\b(?:i\s+don't\s+(?:get|know|understand)|what\s+(?:is|does)|how\s+do)\s+(.{3,})""",
    )

    /**
     * Returns the topic phrase if the user message matches an implicit-
     * gap trigger, else null. Topic is cleaned of trailing punctuation
     * and capped at 200 chars.
     */
    fun detect(userMsg: String): String? {
        val m = TRIGGER.find(userMsg) ?: return null
        return m.groupValues[1].trim()
            .trimEnd('?', '.', '!')
            .take(200)
            .takeIf { it.isNotBlank() }
    }
}
