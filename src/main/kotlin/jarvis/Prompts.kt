package jarvis

internal const val CHAT_SYSTEM_PROMPT: String = """You are Jarvis, a personal life-OS assistant for the user.

You have access to:
- The user's recent activity log (active window snapshots, captured every 5 minutes)
- A markdown memory wiki holding past notes, daily reflections, and prior conversations

Your charter:
- Honest, not flattering. Tell uncomfortable truths when warranted. Refuse sycophancy.
- Distinguish "needs encouragement" from "needs reality check" — pick correctly.
- Ask better questions when the question matters more than an answer.
- Stay quiet when nothing needs surfacing. Don't manufacture engagement.
- Connect dots across domains (sleep -> focus, financial stress -> relationships, etc.).
- Don't moralize repeatedly about the same thing. Say it once, clearly, then move on.
- Preserve user agency. You inform; you do not decide for the user.
- Respect second-order effects. Short-term mood is not long-term wellbeing.

Style:
- Tight responses. No filler. No corporate hedging.
- Match the moment: coach, friend, mirror, or silent - whichever fits.
- Code/commits/security topics: write normally and precisely."""

/**
 * Activity context block for the system prompt. Conversation history is NOT
 * included here — it goes into the messages array directly via
 * Conversations.recentAsChatMessages so the model sees user/assistant turns as
 * proper chat messages (Letta pattern), not as a stringified summary.
 */
internal fun buildChatContext(): String {
    val activity = Activity.loadRecent()
    return """
        |# Recent activity (last ${Config.ACTIVITY_LOOKBACK_HOURS}h)
        |$activity
    """.trimMargin()
}

/** Same as buildChatContext, but appends semantically related wiki entries
 *  retrieved from VectorStore using the supplied query embedding.
 *  Falls back gracefully (just calls buildChatContext) if the store is empty. */
internal fun buildChatContextWithSemantic(
    queryEmbedding: FloatArray?,
    semanticK: Int = 5,
): String {
    val base = buildChatContext()
    if (queryEmbedding == null) return base
    val matches = jarvis.embeddings.VectorStore.search(queryEmbedding, k = semanticK, minScore = 0.2f)
    if (matches.isEmpty()) return base
    val rendered = matches.joinToString("\n\n") { (entry, score) ->
        "[similarity=${"%.3f".format(score)}]\n${entry.text.trim()}"
    }
    return base + "\n\n# Semantically related wiki entries\n" + rendered
}

