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
        // Council 1778155110 follow-up: feed semantic store from chat turns on
        // VPS too, not just local CLI. Async + best-effort — chat HTTP response
        // returns immediately, embed latency does not block /api/chat.
        EmbeddingsPipeline.indexTurnAsync(userMsg, assistantReply, model)
        // Wiki dual-write dropped 2026-05-07: conversations.jsonl is the
        // chat recency source, EmbeddingsPipeline handles semantic indexing,
        // and subsystems now receive recent conversation directly via
        // SubsystemInput.recentChat. wiki.md keeps human-authored content
        // (/save, /reflect, /sub outputs) only.
    }

    fun appendTo(
        conversationsFile: Path,
        userMsg: String,
        assistantReply: String,
        model: String,
    ) {
        val ts = Instant.now().toString()
        val turnId = UUID.randomUUID().toString().take(8)
        // Phase 1.2: score both rows at write time. Pure function, no I/O —
        // safe to invoke under whatever caller lock context.
        val userImp = ConversationScorer.score(userMsg)
        val asstImp = ConversationScorer.score(assistantReply)
        Conversations.appendBothTo(
            conversationsFile,
            ConversationEntry(role = "user", content = userMsg, ts = ts,
                              msgId = "$turnId-u", seq = 0, importance = userImp),
            ConversationEntry(role = "assistant", content = assistantReply, ts = ts,
                              model = model, msgId = "$turnId-a", seq = 1,
                              importance = asstImp),
        )
    }
}
