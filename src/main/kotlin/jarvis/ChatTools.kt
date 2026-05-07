package jarvis

import jarvis.subsystem.SearchSubsystem

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
 * Format the LLM is taught (see CHAT_SYSTEM_PROMPT):
 *   [[search: <query>]]
 *
 * Multiple markers in one reply are each executed in order. Their results are
 * concatenated into a single follow-up [TOOL_RESULT] message and the LLM is
 * called again. Capped at MAX_ITERATIONS rounds to bound cost.
 */
object ChatTools {

    private val TOOL_PATTERN = Regex("""\[\[search:\s*([^\]]+?)\]\]""")
    /** Broader strip for the final user-visible reply: catches any
     *  `[[name: args]]` shape so unrecognized tool markers don't leak. */
    private val ANY_MARKER_PATTERN = Regex("""\[\[\w+:\s*[^\]]+?\]\]""")
    /** One follow-up LLM call after a tool execution. Total LLM calls ≤ 2.
     *  Bounds /api/chat latency on Copilot CLI (~5-15s per call). */
    private const val MAX_ITERATIONS = 1

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
            .map { ToolCall("search", it.groupValues[1].trim()) }
            .toList()

    private fun executeTool(call: ToolCall): String {
        if (call.name != "search") return "(unknown tool: ${call.name})"
        val q = call.args.trim()
        if (q.isEmpty()) return "(empty search query)"
        val hits = SearchSubsystem.searchIn(Config.archivalDir, q, k = 5)
        if (hits.isEmpty()) return "(no matches for \"$q\")"
        return hits.joinToString("\n\n") { hit ->
            val rel = runCatching { Config.archivalDir.relativize(hit.path).toString() }
                .getOrElse { hit.path.toString() }
            "$rel (hits=${hit.hits})\n${hit.snippet}"
        }
    }
}
