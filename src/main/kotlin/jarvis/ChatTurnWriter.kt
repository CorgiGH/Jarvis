package jarvis

import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/**
 * Council 1778105576 (A): atomic dual-write for one chat turn.
 *
 * Original code did 3 separate synchronized blocks per turn (Conversations user,
 * Conversations assistant, MemoryWiki combined). A SIGTERM/OOM/ENOSPC between
 * blocks 1 and 2 left an orphan user-turn in conversations.jsonl with no
 * assistant reply, which buildChatContext then surfaced into every future
 * system prompt — silent corruption with no detector.
 *
 * This writer collapses all writes for one turn into ONE Conversations.appendBoth
 * call (single critical section: both lines hit disk under the same lock acquire,
 * so a crash either lands neither or both). The wiki write happens after — its
 * failure is recoverable (just degrades the legacy /wiki page) and does NOT leave
 * a half-conversation in the recency source-of-truth.
 *
 * Council 1778105576 (D): user=seq 0, assistant=seq 1 so future sort-by-(ts,seq)
 * preserves causal order even when ts ties (which it does for the migrator).
 */
object ChatTurnWriter {

    fun append(userMsg: String, assistantReply: String, model: String) {
        appendTo(Config.conversationsFile, userMsg, assistantReply, model)
        // Best-effort wiki write (legacy /wiki page). Failure here does NOT
        // corrupt chat recency; the Conversations write already landed atomically.
        try {
            MemoryWiki.append(
                "conversation ($model)",
                "**user:** $userMsg\n\n**jarvis:** $assistantReply",
            )
        } catch (e: Exception) {
            System.err.println(
                "[ChatTurnWriter] WARN wiki write failed (${e.javaClass.simpleName}: " +
                    "${e.message?.take(160)}); conversations.jsonl already has this turn.",
            )
        }
        // Council 1778155110 follow-up: feed semantic store from chat turns on
        // VPS too, not just local CLI. Async + best-effort — chat HTTP response
        // returns immediately, embed latency does not block /api/chat.
        EmbeddingsPipeline.indexTurnAsync(userMsg, assistantReply, model)
    }

    fun appendTo(
        conversationsFile: Path,
        userMsg: String,
        assistantReply: String,
        model: String,
    ) {
        val ts = Instant.now().toString()
        val turnId = UUID.randomUUID().toString().take(8)
        Conversations.appendBothTo(
            conversationsFile,
            ConversationEntry(role = "user", content = userMsg, ts = ts,
                              msgId = "$turnId-u", seq = 0),
            ConversationEntry(role = "assistant", content = assistantReply, ts = ts,
                              model = model, msgId = "$turnId-a", seq = 1),
        )
    }
}
