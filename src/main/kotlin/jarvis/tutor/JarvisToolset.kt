package jarvis.tutor

import jarvis.Config
import jarvis.HybridRetriever
import jarvis.OpenRouterChatLlm
import jarvis.embeddings.EmbeddingsClient
import jarvis.resolveOpenRouterKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Tutor task-context V1 — typed tool-use round-trip via OpenRouter.
 *
 * Council 2026-05-09 task-context council: replace static-inject of
 * deep context with tools the LLM invokes lazily. The LLM gets the
 * always-on TaskHeader (V0) PLUS a typed tool palette that exposes
 * the spec §4 source-priority pipeline as on-demand functions.
 *
 * V1 ships ONE tool: `search_archival(query, k=3, subject?)` —
 * lexical+vector hybrid retrieval over the archival corpus via
 * existing HybridRetriever. Subsequent versions add:
 *   - get_concept_state(concept, subject)
 *   - get_past_gap(topic, subject?)
 *   - get_assignments(subject?)
 *   - get_fsrs_due(subject?)
 *
 * Each tool internally implements spec's source-priority chain:
 * cheapest source first, escalate on miss.
 *
 * Wire format: OpenAI chat-completions tools field. OpenRouter
 * proxies to whichever model the request names; Anthropic
 * (claude-3.5-sonnet etc) and OpenAI both honor it.
 *
 * Round-trip cap: max 3 tool turns per user message to prevent
 * runaway spend. After 3, server forces final-answer mode by
 * dropping tools from the next call.
 */
class JarvisToolset(
    private val llm: OpenRouterChatLlm = OpenRouterChatLlm(),
    private val maxToolRounds: Int = 3,
) : AutoCloseable {

    @Serializable
    data class ToolReply(val text: String, val model: String, val toolRounds: Int)

    /** Chat with the LLM; allow it to invoke tools. Returns the
     *  final text after at most [maxToolRounds] tool round-trips. */
    suspend fun chat(systemPrompt: String, userText: String): ToolReply {
        // Conversation state — we mutate as we add tool_use/tool_result.
        val messages = mutableListOf<JsonObject>()
        messages += buildJsonObject {
            put("role", "system"); put("content", systemPrompt)
        }
        messages += buildJsonObject {
            put("role", "user"); put("content", userText)
        }

        var rounds = 0
        var lastModel = ""
        while (true) {
            val toolsToOffer = if (rounds < maxToolRounds) JarvisToolDefs.openAiToolDefs else buildJsonArray {}
            val message = if (toolsToOffer.isEmpty()) {
                // Final round — text-only completion via Llm interface.
                val (text, model) = llm.complete(
                    messages.map { jsonObjectToChatMessage(it) }, maxTokens = 1500,
                )
                lastModel = model
                buildJsonObject {
                    put("role", "assistant"); put("content", text)
                }
            } else {
                llm.completeWithTools(
                    messages = JsonArray(messages),
                    tools = toolsToOffer,
                    maxTokens = 1500,
                ).also { lastModel = "openrouter-tools" }
            }
            val toolCalls = message["tool_calls"] as? JsonArray
            val content = (message["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()

            if (toolCalls.isNullOrEmpty()) {
                return ToolReply(text = content, model = lastModel, toolRounds = rounds)
            }
            // Append the assistant message AS-IS so the model sees its own
            // tool_calls in the next turn (OpenAI requires this).
            messages += message
            for (call in toolCalls) {
                val callObj = call as? JsonObject ?: continue
                val callId = (callObj["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val function = callObj["function"] as? JsonObject ?: continue
                val name = (function["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val argsRaw = (function["arguments"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val result = try {
                    JarvisToolDefs.dispatch(name, argsRaw)
                } catch (e: Exception) {
                    "tool error: ${e.javaClass.simpleName}: ${e.message?.take(200)}"
                }
                val wrapped = PromptInjectionScrubber.wrap(
                    source = "tool_result:$name",
                    trust = "indexed_data",
                    content = result,
                )
                messages += buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", callId)
                    put("content", wrapped)
                }
            }
            rounds++
        }
    }

    private fun jsonObjectToChatMessage(o: JsonObject): jarvis.ChatMessage {
        val role = (o["role"] as? JsonPrimitive)?.contentOrNull ?: "user"
        val content = (o["content"] as? JsonPrimitive)?.contentOrNull ?: ""
        return jarvis.ChatMessage(role, content)
    }

    override fun close() { llm.close() }
}

/** Tool definitions + dispatch logic. Pure object so tests can call
 *  dispatch() directly without constructing the LLM client. */
object JarvisToolDefs {

    val openAiToolDefs: JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", "search_archival")
                put("description",
                    "Search the user's personal archival corpus (lecture notes, " +
                        "textbooks, prior solutions) for material relevant to the " +
                        "user's current question. Use this when the user asks about " +
                        "a concept, theorem, definition, or worked example. Returns " +
                        "top-K matching files with snippets.")
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "Single-keyword or short phrase. Lexical match preferred over compound queries.")
                        })
                        put("k", buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of hits to return (default 3, max 5).")
                        })
                        put("subject", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional subject filter (PA, PS, POO, ALO, SO, ...).")
                        })
                    })
                    put("required", buildJsonArray { add("query") })
                })
            })
        })
    }

    fun dispatch(toolName: String, argsJson: String): String {
        return when (toolName) {
            "search_archival" -> dispatchSearchArchival(argsJson)
            else -> "unknown tool: $toolName"
        }
    }

    private fun dispatchSearchArchival(argsJson: String): String {
        val obj = try {
            Json.parseToJsonElement(argsJson) as? JsonObject ?: return "bad args: not an object"
        } catch (e: Exception) {
            return "bad args json: ${e.message?.take(120)}"
        }
        val query = (obj["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (query.isEmpty()) return "search_archival: query required"
        val k = ((obj["k"] as? JsonPrimitive)?.intOrNull ?: 3).coerceIn(1, 5)
        val subject = (obj["subject"] as? JsonPrimitive)?.contentOrNull?.trim()

        // Hybrid retrieval (lexical + semantic when OPENROUTER_API_KEY
        // is set for embeddings; degrades to lexical otherwise).
        val key = resolveOpenRouterKey()
        val embedFn: (suspend (String) -> FloatArray)? = if (!key.isNullOrBlank()) {
            { q -> EmbeddingsClient(key).use { c -> c.embed(q) } }
        } else null

        val hits = try {
            runBlocking { HybridRetriever.search(query, k = k, semanticEmbed = embedFn) }
        } catch (e: Exception) {
            return "search_archival: ${e.javaClass.simpleName}: ${e.message?.take(160)}"
        }
        if (hits.isEmpty()) return "search_archival: no matches for \"$query\""

        // Optional subject post-filter — HybridRetriever.search doesn't
        // accept a subject param in V1, so we filter on the returned ids
        // by path prefix when subject is supplied.
        val filtered = if (subject.isNullOrBlank()) hits
            else hits.filter { it.id.startsWith("$subject/", ignoreCase = true) }

        if (filtered.isEmpty()) return "search_archival: no matches for \"$query\" in subject $subject"

        return filtered.joinToString("\n\n") { hit ->
            "[${hit.source} score=${"%.3f".format(hit.score)}] ${hit.id}\n${hit.snippet.take(600)}"
        }
    }
}
