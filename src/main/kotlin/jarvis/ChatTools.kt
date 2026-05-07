package jarvis

import jarvis.embeddings.EmbeddingsClient
import jarvis.embeddings.VectorStore
import jarvis.subsystem.SearchSubsystem
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * ReAct-style tool-calling on top of the plain text Llm interface. Lets the
 * LLM invoke a small set of tools mid-turn by emitting a marker; the runtime
 * parses, executes, and feeds the result back as a follow-up turn.
 *
 * Council 1778104395 hard rule: archival corpus is NEVER auto-injected into
 * chat context. Tool-calling preserves that — the LLM has to explicitly ask.
 *
 * Why a marker pattern instead of provider-native tool_use:
 * - Copilot CLI is a subprocess that takes plain text in and emits plain text
 *   out. No structured tool API.
 * - The same marker contract works across every Llm provider (Anthropic,
 *   OpenRouter, Copilot, Claude Max). No provider-specific code.
 *
 * Tools (see CHAT_SYSTEM_PROMPT for the prompt-side contract):
 *   [[search: <query>]]   — lexical grep over archival corpus
 *   [[read: <path>]]      — full content of a file under the archival root
 *   [[recall: <query>]]   — semantic search over the embedding store
 *
 * Multiple markers in one reply are each executed in order. Their results are
 * concatenated into a single follow-up [TOOL_RESULT] message and the LLM is
 * called again. Capped at MAX_ITERATIONS rounds to bound cost.
 */
object ChatTools {

    /** Captures any `[[name: args]]` marker so the parser surfaces every tool
     *  the LLM tries to call (including unknowns, which the executor reports
     *  as errors back to the LLM). */
    private val TOOL_PATTERN = Regex("""\[\[(\w+):\s*([^\]]+?)\]\]""")
    /** Same shape, used to strip residual markers from the user-visible reply. */
    private val ANY_MARKER_PATTERN = Regex("""\[\[\w+:\s*[^\]]+?\]\]""")
    /** One follow-up LLM call after a tool execution. Total LLM calls ≤ 2.
     *  Bounds /api/chat latency on Copilot CLI (~5-15s per call). */
    private const val MAX_ITERATIONS = 1

    /** Cap per-file expansion so a [[read: ...]] of a 5MB doc doesn't blow the
     *  follow-up prompt past the model's context window. */
    private const val READ_MAX_BYTES = 32 * 1024

    data class ToolCall(val name: String, val args: String)

    suspend fun runTurn(client: Llm, messages: List<ChatMessage>): Pair<String, String> =
        runTurnWith(client, messages, ::executeTool)

    internal suspend fun runTurnWith(
        client: Llm,
        messages: List<ChatMessage>,
        executor: (ToolCall) -> String,
    ): Pair<String, String> {
        var working = messages
        var lastModel = "n/a"
        for (iteration in 0..MAX_ITERATIONS) {
            val (reply, model) = client.complete(working)
            lastModel = model
            val calls = parseToolCalls(reply)
            if (calls.isEmpty() || iteration == MAX_ITERATIONS) {
                // Strip residual markers — model may still emit one even on
                // the final pass; user shouldn't see internal control syntax.
                return ANY_MARKER_PATTERN.replace(reply, "").trim() to model
            }
            val resultBlock = calls.joinToString("\n\n") { call ->
                "[TOOL_RESULT ${call.name}(${call.args})]\n${executor(call)}"
            }
            working = working + ChatMessage("assistant", reply) +
                ChatMessage("user", resultBlock)
        }
        return "" to lastModel
    }

    fun parseToolCalls(text: String): List<ToolCall> =
        TOOL_PATTERN.findAll(text)
            .map { ToolCall(it.groupValues[1].trim(), it.groupValues[2].trim()) }
            .toList()

    private fun executeTool(call: ToolCall): String = when (call.name) {
        "search" -> executeSearch(call.args)
        "read" -> executeRead(call.args)
        "recall" -> executeRecall(call.args)
        "time" -> executeTime()
        "remember" -> executeRemember(call.args)
        "wiki" -> executeWiki(call.args)
        else -> "(unknown tool: ${call.name})"
    }

    private fun executeWiki(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "(empty wiki query)"
        val all = MemoryWiki.readAll()
        if (all.isEmpty()) return "(wiki is empty)"
        val needle = q.lowercase()
        // Section split mirrors MemoryWiki.recent: each section starts with "## ".
        val sections = all.split(Regex("(?=^## )", RegexOption.MULTILINE))
            .filter { it.startsWith("## ") }
        val matches = sections
            .map { it to it.lowercase().windowed(needle.length, 1)
                .count { window -> window == needle } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
        if (matches.isEmpty()) return "(no wiki matches for \"$q\")"
        return matches.joinToString("\n\n") { (section, hits) ->
            // Header line + first 6 non-blank lines (≤ ~600 chars total).
            val header = section.lineSequence().firstOrNull()?.trim() ?: "(unnamed)"
            val body = section.lineSequence().drop(1)
                .filter { it.isNotBlank() }
                .take(6)
                .joinToString("\n")
                .take(600)
            "$header  (hits=$hits)\n$body"
        }
    }

    private fun executeTime(): String = Instant.now().toString()

    private fun executeRemember(content: String): String {
        val text = content.trim()
        if (text.isEmpty()) return "(empty remember content)"
        // Cap to keep one append from blowing up wiki.md if model hallucinates
        // a giant blob; at single-user scale a sane note is well under 4KB.
        val capped = if (text.length > 4000) text.take(4000) + " […truncated]" else text
        return try {
            MemoryWiki.append("user note (model-pinned)", capped)
            "(remembered: ${capped.take(80)}${if (capped.length > 80) "…" else ""})"
        } catch (e: Exception) {
            "(remember failed: ${e.javaClass.simpleName}: ${e.message?.take(160)})"
        }
    }

    private fun executeSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "(empty search query)"
        val hits = SearchSubsystem.searchIn(Config.archivalDir, q, k = 5)
        if (hits.isEmpty()) return "(no matches for \"$q\")"
        return hits.joinToString("\n\n") { hit ->
            val rel = runCatching { Config.archivalDir.relativize(hit.path).toString() }
                .getOrElse { hit.path.toString() }
            "$rel (hits=${hit.hits})\n${hit.snippet}"
        }
    }

    private fun executeRead(rawPath: String): String {
        val arg = rawPath.trim()
        if (arg.isEmpty()) return "(empty read path)"
        val root = Config.archivalDir.toAbsolutePath().normalize()
        val resolved = root.resolve(arg).normalize().toAbsolutePath()
        // Reject path-traversal: resolved file must live under archival root.
        if (!resolved.startsWith(root)) {
            return "(read denied: path \"$arg\" escapes archival root)"
        }
        if (!resolved.exists()) return "(no such file: $arg)"
        if (!resolved.isRegularFile()) return "(not a regular file: $arg)"
        val bytes = try {
            Files.readAllBytes(resolved)
        } catch (e: Exception) {
            return "(read failed: ${e.message?.take(160)})"
        }
        val truncated = bytes.size > READ_MAX_BYTES
        val text = String(
            if (truncated) bytes.copyOfRange(0, READ_MAX_BYTES) else bytes,
            Charsets.UTF_8,
        )
        val tail = if (truncated) "\n\n[…truncated at $READ_MAX_BYTES bytes of ${bytes.size}…]" else ""
        return text + tail
    }

    private fun executeRecall(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "(empty recall query)"
        val key = resolveOpenRouterKey()
        if (key.isNullOrBlank()) {
            return "(recall unavailable: OPENROUTER_API_KEY not set)"
        }
        val embedding = try {
            EmbeddingsClient(key).use { c -> runBlocking { c.embed(q) } }
        } catch (e: Exception) {
            return "(recall embed failed: ${e.javaClass.simpleName}: ${e.message?.take(160)})"
        }
        val hits = VectorStore.search(embedding, k = 5, minScore = 0.2f)
        if (hits.isEmpty()) return "(no semantic matches for \"$q\")"
        return hits.joinToString("\n\n") { (entry, score) ->
            val snippet = entry.text.lineSequence()
                .filter { it.isNotBlank() }
                .take(6)
                .joinToString("\n")
                .take(800)
            "[similarity=${"%.3f".format(score)}] ${entry.id}\n$snippet"
        }
    }
}
