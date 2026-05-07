package jarvis

import jarvis.embeddings.EmbeddingsClient
import jarvis.embeddings.StoredEmbedding
import jarvis.embeddings.VectorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Council 1778155110 follow-up + 1778105576 (G): wire VectorStore feeding into
 * the chat turn write path so semantic recall works on VPS, not just local CLI.
 *
 * Prior state: ChatMain (CLI) embedded each turn into VectorStore inline; WebMain
 * never did. Result: VPS shipped with semantic-recall feature dead because
 * `/api/chat` never grew the embedding store. Pre-impl council 1778104395
 * already deferred archival index decisions; this fixes only the chat-turn
 * embedding pipeline that already existed for CLI.
 *
 * Design:
 * - Singleton EmbeddingsClient lazy-initialized from OPENROUTER_API_KEY.
 *   Absent key → indexing silently no-ops (embeddings are optional).
 * - indexTurnAsync launches on a SupervisorJob+Dispatchers.IO scope so the
 *   chat HTTP response returns immediately; embed latency (200-500ms typical)
 *   does NOT block /api/chat. Failures log but do not propagate — the chat
 *   recency in conversations.jsonl already landed atomically before this runs.
 */
object EmbeddingsPipeline {

    private val client: EmbeddingsClient? by lazy {
        val key = resolveOpenRouterKey()
        if (key.isNullOrBlank()) {
            System.err.println(
                "[EmbeddingsPipeline] OPENROUTER_API_KEY not set; chat-turn semantic " +
                    "indexing disabled. Chat recency still works via Conversations.jsonl.",
            )
            null
        } else {
            EmbeddingsClient(key)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun indexTurnAsync(userMsg: String, assistantReply: String, model: String) {
        val c = client ?: return
        val title = "conversation ($model)"
        val ts = Instant.now()
        val text = "## [$ts] $title\n\n**user:** $userMsg\n\n**jarvis:** $assistantReply"
        val id = "$title | ${userMsg.take(60)}"
        scope.launch {
            try {
                val emb = c.embed(text)
                VectorStore.add(StoredEmbedding(id = id, text = text, embedding = emb))
            } catch (e: Exception) {
                System.err.println(
                    "[EmbeddingsPipeline] WARN embed failed (${e.javaClass.simpleName}: " +
                        "${e.message?.take(160)}); turn already in conversations.jsonl, " +
                        "semantic recall just won't see this one.",
                )
            }
        }
    }
}
