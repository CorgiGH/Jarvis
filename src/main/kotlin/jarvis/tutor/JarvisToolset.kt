package jarvis.tutor

import jarvis.Config
import jarvis.HybridRetriever
import jarvis.embeddings.EmbeddingsClient
import jarvis.resolveOpenRouterKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
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
    private val apiKey: String = resolveOpenRouterKey()
        ?: error("OPENROUTER_API_KEY required for JarvisToolset"),
    private val model: String = "anthropic/claude-3.5-sonnet",
    private val baseUrl: String = Config.OPENROUTER_BASE_URL,
    private val maxToolRounds: Int = 3,
) : AutoCloseable {

    @Serializable
    data class ToolReply(val text: String, val model: String, val toolRounds: Int)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 15_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        while (true) {
            val req = buildJsonObject {
                put("model", model)
                put("max_tokens", 1500)
                put("messages", JsonArray(messages))
                if (rounds < maxToolRounds) {
                    put("tools", JarvisToolDefs.openAiToolDefs)
                    put("tool_choice", JsonPrimitive("auto"))
                }
            }
            val resp = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
                header("X-Title", "jarvis-kotlin tutor")
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (!resp.status.isSuccess()) {
                error("openrouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}")
            }
            val parsed = resp.body<JsonObject>()
            val choices = parsed["choices"] as? JsonArray ?: error("no choices in reply")
            val firstChoice = choices.firstOrNull() as? JsonObject ?: error("empty choices")
            val message = firstChoice["message"] as? JsonObject ?: error("no message")
            val toolCalls = message["tool_calls"] as? JsonArray
            val content = (message["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()

            if (toolCalls.isNullOrEmpty()) {
                // No tool calls → this is the final answer.
                return ToolReply(text = content, model = model, toolRounds = rounds)
            }

            // Append the assistant message AS-IS so the model sees its own
            // tool_calls in the next turn (OpenAI requires this).
            messages += message
            // Resolve each tool call and append a tool_result message.
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
            if (rounds >= maxToolRounds) {
                // One final round WITHOUT tools to force a text answer.
                continue
            }
        }
    }

    override fun close() { client.close() }
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
