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
     *  final text after at most [maxToolRounds] tool round-trips.
     *
     *  Skill resolution (Stage C): if [userText] matches a SKILL.md
     *  trigger under [skillsRoot], that skill's body is appended to
     *  the system prompt AND its tool_allowlist filters the tool
     *  surface. Empty allowlist = full surface. No match = full
     *  default surface. */
    suspend fun chat(
        systemPrompt: String,
        userText: String,
        skillsRoot: java.nio.file.Path = SkillLoader.defaultRoot(),
    ): ToolReply {
        val skills = try { SkillLoader.load(skillsRoot) } catch (_: Exception) { emptyList() }
        val active = SkillLoader.resolve(skills, userText)
        val effectiveSystem = if (active != null) {
            "$systemPrompt\n\n# Active skill: ${active.name}\n${active.systemPromptBody}"
        } else systemPrompt
        val effectiveTools = if (active != null)
            SkillLoader.applyAllowlist(active, JarvisToolDefs.openAiToolDefs)
        else JarvisToolDefs.openAiToolDefs

        val messages = mutableListOf<JsonObject>()
        messages += buildJsonObject {
            put("role", "system"); put("content", effectiveSystem)
        }
        // Phase 7.7 Layer 3: sanitize user input before LLM round-trip.
        // Strips role-marker tokens + jailbreak prefixes; logs to stderr
        // when present. Layer 4 (tool-return wrap) already lives below.
        val sanitizedUserText = PromptInjectionScrubber.sanitizeInput(userText)
        messages += buildJsonObject {
            put("role", "user"); put("content", sanitizedUserText)
        }

        var rounds = 0
        var lastModel = ""
        // Pinned model for the rest of this tool loop. Risk Analyst council
        // R5 HIGH: round 1 may land on model A and round 2 on model B because
        // OR's `models:` array routes around 404/429 — but the conversation
        // history then includes A's `assistant.tool_calls` dialect. Pin once
        // we know which model OR picked for round 1, and force every later
        // round at the same model.
        var pinnedModel: String? = null
        while (true) {
            val toolsToOffer = if (rounds < maxToolRounds) effectiveTools else buildJsonArray {}
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
                val reply = llm.completeWithTools(
                    messages = JsonArray(messages),
                    tools = toolsToOffer,
                    maxTokens = 1500,
                    modelOverride = pinnedModel,
                )
                if (pinnedModel == null) pinnedModel = reply.model
                lastModel = reply.model
                reply.message
            }
            val toolCalls = message["tool_calls"] as? JsonArray
            val content = extractAssistantText(message)

            if (toolCalls.isNullOrEmpty()) {
                // Some free-tier models (gpt-oss-*:free, glm-4.5-air:free) occasionally
                // return both content+reasoning empty under tool_use mode with long
                // system prompts. extractAssistantText already tried the alternates;
                // if we still have nothing, signal failure so the caller can fall
                // back to the legacy chat path instead of persisting an empty turn.
                if (content.isBlank()) {
                    error("openrouter empty response: model=$lastModel rounds=$rounds — caller should retry via legacy chat")
                }
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

    /**
     * Pull assistant-visible text out of an OR chat-completion message.
     *
     * Most OR models populate `content`. Some "reasoning-style" free-tier
     * models (z-ai/glm-4.5-air:free, certain OpenInference variants of
     * openai/gpt-oss-*:free) instead emit `content: null` plus a populated
     * `reasoning` field — this happens especially under tool_use mode and
     * with longer system prompts. The user-visible chat reply was a blank
     * string in 2026-05-09 21:25Z (`gpt-oss-120b:free`, 0 tool rounds)
     * because the loop returned `content="".orEmpty()` without checking
     * the alternate field.
     *
     * Order: content → reasoning_text (OpenRouter's structured field) →
     * reasoning (older shape) → empty string. Whitespace trimmed.
     */
    private fun extractAssistantText(message: JsonObject): String {
        val direct = (message["content"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (direct != null) return direct
        val reasoningText = (message["reasoning_text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (reasoningText != null) return reasoningText
        val reasoning = (message["reasoning"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (reasoning != null) return reasoning
        return ""
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
        add(toolDef("search_archival",
            "Hybrid lexical+semantic search over the user's personal archival corpus " +
                "(lecture notes, textbooks, prior solutions). Use for definitions, " +
                "theorems, worked examples. Returns top-K matching files with snippets.",
        ) {
            put("query", strParam("Single-keyword or short phrase. Lexical match preferred over compound queries.", required = true))
            put("k", intParam("Number of hits to return (default 3, max 5)."))
            put("subject", strParam("Optional subject filter (PA, PS, POO, ALO, SO, ...)."))
        })
        add(toolDef("query_graph",
            "Query the cross-corpus knowledge graph by concept name OR snippet text. " +
                "Cheaper + more structural than search_archival — use when the user " +
                "asks 'what concepts mention X' or 'what's related to Y'. Returns " +
                "matching graph nodes (subject, concept, source path, snippet).",
        ) {
            put("query", strParam("Concept name or substring.", required = true))
            put("k", intParam("Top-K (default 5, max 10)."))
        })
        add(toolDef("get_node",
            "Fetch a single graph node by its full id (`<subject>/<concept>`). Use " +
                "after query_graph to expand a single result.",
        ) {
            put("id", strParam("Full node id, e.g. `PA/Greedy algorithm`.", required = true))
        })
        add(toolDef("get_neighbors",
            "Return all graph nodes connected to the given node id, optionally filtered " +
                "by edge kind (mentions / sibling / subject). Use to explore " +
                "neighborhood of a concept.",
        ) {
            put("id", strParam("Full node id.", required = true))
            put("kind", strParam("Optional edge kind filter: mentions / sibling / subject."))
        })
        add(toolDef("shortest_path",
            "Find the shortest path between two concepts in the knowledge graph. " +
                "Returns the chain of node ids. Empty when no path exists. Use to " +
                "answer 'how does X connect to Y'.",
        ) {
            put("from", strParam("Source node id.", required = true))
            put("to", strParam("Target node id.", required = true))
        })
        add(toolDef("wiki_read",
            "Read the user-specific wiki page for a concept. Each page accumulates " +
                "what the user has historically struggled with, prereqs, and " +
                "successful clarifications. Use to personalize an explanation.",
        ) {
            put("subject", strParam("Subject (PA, PS, POO, ALO, SO, ...).", required = true))
            put("concept", strParam("Concept name (e.g. `Greedy algorithm`).", required = true))
        })
        add(toolDef("wiki_append",
            "Append a bullet to one section of the user's wiki page for a concept. " +
                "Use when YOU notice the user struggled with something, a worked " +
                "clarification landed, or a missing prereq was identified. Sections: " +
                "Brief / Confusions / Worked clarifications / Prereqs needed / Last user state.",
        ) {
            put("subject", strParam("Subject.", required = true))
            put("concept", strParam("Concept name.", required = true))
            put("section", strParam("Section name (case-sensitive — see description).", required = true))
            put("bullet", strParam("Single-line note. Max 400 chars.", required = true))
        })
        add(toolDef("calendar_create_event",
            "Create a Google Calendar event in the user's primary calendar. Use " +
                "to schedule a study block / pomodoro / deadline reminder. NEVER " +
                "schedules without explicit user intent. Requires GWS_ENABLED on " +
                "the server.",
        ) {
            put("summary", strParam("Event title.", required = true))
            put("startIso", strParam("Start time ISO-8601 with TZ.", required = true))
            put("endIso", strParam("End time ISO-8601.", required = true))
        })
        add(toolDef("drive_search",
            "Search the user's Google Drive for files matching a query.",
        ) {
            put("query", strParam("Drive query string (e.g. 'name contains syllabus and mimeType=application/pdf').", required = true))
            put("pageSize", intParam("Max results (default 5, max 20)."))
        })
        add(toolDef("gmail_create_draft",
            "Create a Gmail draft (NEVER auto-sends).",
        ) {
            put("to", strParam("Recipient email.", required = true))
            put("subject", strParam("Email subject line.", required = true))
            put("body", strParam("Plain-text email body.", required = true))
        })
        add(toolDef("search_subject_corpus",
            "Search the user's per-subject materials for past exams, " +
                "homework problems, lab tasks, seminar exercises, lecture " +
                "notes, or practice problems. Covers what ConceptCatalog + " +
                "search_archival miss (`_extras/<subject>/` tree). Use when " +
                "the user asks for a 'similar partial', 'practice problem on " +
                "X', 'last year's exam', or 'what's the lab spec for week N'.",
        ) {
            put("subject", strParam("Subject (PA / PS / POO / ALO / SO / RC).", required = true))
            put("query", strParam("Substring to look for in filenames + content.", required = true))
            put("kind", strParam("Optional kind filter: courses / seminars / labs / hw / practice / tests / source / study_guide. Use list_subject_kinds to see what the subject has."))
            put("k", intParam("Top-K hits (default 5, max 10)."))
        })
        add(toolDef("list_subject_kinds",
            "List the materials types available for a subject (e.g. tests, " +
                "hw, labs, seminars). Use BEFORE search_subject_corpus when " +
                "you don't know what kind to filter by.",
        ) {
            put("subject", strParam("Subject (PA / PS / POO / ALO / SO / RC).", required = true))
        })
        add(toolDef("symbolic_math",
            "Evaluate a symbolic-math expression via sympy. Supports simplify, " +
                "differentiate, integrate, solve, expand, factor. Use when the user " +
                "needs a verified algebraic answer (not a guess) — derivatives, " +
                "integrals, equation solutions, simplifications. Returns the result " +
                "in plaintext + LaTeX. Requires `pip install sympy` on the server " +
                "(returns a clear error if missing).",
        ) {
            put("op", strParam("Operation: simplify | diff | integrate | solve | expand | factor.", required = true))
            put("expression", strParam("The expression. Example: \"x**2 + 2*x + 1\".", required = true))
            put("symbol", strParam("Variable name when relevant (default \"x\")."))
        })
    }

    private fun toolDef(name: String, description: String, params: JsonObjectBuilderScope.() -> Unit): JsonObject {
        val propsBuilder = JsonObjectBuilderScope()
        propsBuilder.params()
        return buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", name)
                put("description", description)
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", propsBuilder.build())
                    put("required", buildJsonArray {
                        propsBuilder.required().forEach { add(it) }
                    })
                })
            })
        }
    }

    private class JsonObjectBuilderScope {
        private val out = mutableMapOf<String, JsonObject>()
        fun put(key: String, schema: JsonObject) { out[key] = schema; if (schema["__required__"] != null) requiredKeys += key }
        private val requiredKeys = mutableListOf<String>()
        fun build(): JsonObject = buildJsonObject {
            for ((k, v) in out) put(k, v)
        }
        fun required(): List<String> = requiredKeys
    }

    private fun strParam(description: String, required: Boolean = false): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        if (required) put("__required__", true)
    }

    private fun intParam(description: String, required: Boolean = false): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
        if (required) put("__required__", true)
    }

    fun dispatch(toolName: String, argsJson: String): String {
        return when (toolName) {
            "search_archival" -> dispatchSearchArchival(argsJson)
            "query_graph" -> dispatchQueryGraph(argsJson)
            "get_node" -> dispatchGetNode(argsJson)
            "get_neighbors" -> dispatchGetNeighbors(argsJson)
            "shortest_path" -> dispatchShortestPath(argsJson)
            "wiki_read" -> dispatchWikiRead(argsJson)
            "wiki_append" -> dispatchWikiAppend(argsJson)
            "calendar_create_event" -> dispatchCalendarCreate(argsJson)
            "drive_search" -> dispatchDriveSearch(argsJson)
            "gmail_create_draft" -> dispatchGmailDraft(argsJson)
            "search_subject_corpus" -> dispatchSubjectCorpus(argsJson)
            "list_subject_kinds" -> dispatchListSubjectKinds(argsJson)
            "symbolic_math" -> dispatchSymbolicMath(argsJson)
            else -> "unknown tool: $toolName"
        }
    }

    private fun parseArgs(argsJson: String): JsonObject? = try {
        Json.parseToJsonElement(argsJson) as? JsonObject
    } catch (_: Exception) { null }

    private fun dispatchQueryGraph(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val q = (obj["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (q.isEmpty()) return "query_graph: query required"
        val k = ((obj["k"] as? JsonPrimitive)?.intOrNull ?: 5).coerceIn(1, 10)
        val graph = KnowledgeGraphBuilder.load() ?: return "query_graph: graph not built — run gradle ingestCorpus"
        val hits = KnowledgeGraphQuery.query(graph, q, k)
        if (hits.isEmpty()) return "query_graph: no matches for \"$q\""
        return hits.joinToString("\n\n") { n ->
            "[${n.id}] (${n.sourcePath})\n${n.snippet.take(400)}"
        }
    }

    private fun dispatchGetNode(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (id.isEmpty()) return "get_node: id required"
        val graph = KnowledgeGraphBuilder.load() ?: return "get_node: graph not built"
        val node = KnowledgeGraphQuery.getNode(graph, id) ?: return "get_node: not found: $id"
        return "[${node.id}] subject=${node.subject} source=${node.sourcePath}\n${node.snippet}"
    }

    private fun dispatchGetNeighbors(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (id.isEmpty()) return "get_neighbors: id required"
        val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull?.trim().takeIf { !it.isNullOrEmpty() }
        val graph = KnowledgeGraphBuilder.load() ?: return "get_neighbors: graph not built"
        val ns = KnowledgeGraphQuery.neighbors(graph, id, kind)
        if (ns.isEmpty()) return "get_neighbors: none for $id${kind?.let { " (kind=$it)" } ?: ""}"
        return ns.joinToString("\n") { (n, k) -> "[$k] ${n.id}" }
    }

    private fun dispatchWikiRead(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val subject = (obj["subject"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val concept = (obj["concept"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (subject.isEmpty() || concept.isEmpty()) return "wiki_read: subject + concept required"
        val text = WikiPage.read(subject, concept)
            ?: return "wiki_read: no wiki page yet for $subject/$concept"
        return text.take(2000)
    }

    private fun dispatchSubjectCorpus(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val subject = (obj["subject"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val query = (obj["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (subject.isEmpty() || query.isEmpty()) return "search_subject_corpus: subject + query required"
        val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull?.trim().takeIf { !it.isNullOrEmpty() }
        val k = ((obj["k"] as? JsonPrimitive)?.intOrNull ?: 5).coerceIn(1, 10)
        val hits = SubjectCorpus.search(subject, query, kindFilter = kind, k = k)
        if (hits.isEmpty()) {
            return "search_subject_corpus: no matches for \"$query\" in $subject" +
                (kind?.let { " (kind=$it)" } ?: "")
        }
        return hits.joinToString("\n\n") { h ->
            "[${h.subject}/${h.kind}] ${h.relPath} (score=${h.score})\n${h.snippet}"
        }
    }

    private fun dispatchListSubjectKinds(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val subject = (obj["subject"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (subject.isEmpty()) return "list_subject_kinds: subject required"
        val kinds = SubjectCorpus.listKinds(subject)
        if (kinds.isEmpty()) return "list_subject_kinds: no kinds for $subject (subject not in _extras/?)"
        return "kinds for $subject:\n  " + kinds.joinToString("\n  ")
    }

    private fun dispatchSymbolicMath(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val op = (obj["op"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val expression = (obj["expression"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val symbol = (obj["symbol"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (op.isEmpty() || expression.isBlank()) {
            return "symbolic_math: op + expression required"
        }
        val r = SympyTool.run(op = op, expression = expression, symbol = symbol.ifEmpty { "x" })
        return if (r.ok) {
            buildString {
                append("op=$op  ")
                append(r.plain)
                if (!r.latex.isNullOrBlank()) {
                    append("\nlatex: ")
                    append(r.latex)
                }
            }
        } else {
            "symbolic_math error: ${r.error.orEmpty()}"
        }
    }

    private fun dispatchCalendarCreate(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val summary = (obj["summary"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val startIso = (obj["startIso"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val endIso = (obj["endIso"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (summary.isEmpty() || startIso.isEmpty() || endIso.isEmpty()) {
            return "calendar_create_event: summary + startIso + endIso required"
        }
        val r = GwsEffector.run(
            subcommand = "calendar events insert",
            params = mapOf("calendarId" to "primary"),
            body = mapOf(
                "summary" to summary,
                "start" to mapOf<String, Any>("dateTime" to startIso),
                "end" to mapOf<String, Any>("dateTime" to endIso),
            ),
        )
        return when (r) {
            is GwsEffector.Result.Ok -> "calendar event created (raw: ${r.stdout.take(400)})"
            is GwsEffector.Result.Err -> "calendar_create_event failed (exit ${r.exitCode}): ${r.stderr.take(400)}"
        }
    }

    private fun dispatchDriveSearch(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val q = (obj["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (q.isEmpty()) return "drive_search: query required"
        val pageSize = ((obj["pageSize"] as? JsonPrimitive)?.intOrNull ?: 5).coerceIn(1, 20)
        val r = GwsEffector.run(
            subcommand = "drive files list",
            params = mapOf("q" to q, "pageSize" to pageSize),
        )
        return when (r) {
            is GwsEffector.Result.Ok -> r.stdout.take(2000)
            is GwsEffector.Result.Err -> "drive_search failed (exit ${r.exitCode}): ${r.stderr.take(400)}"
        }
    }

    private fun dispatchGmailDraft(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val to = (obj["to"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val subject = (obj["subject"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val body = (obj["body"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (to.isEmpty() || subject.isEmpty() || body.isEmpty()) {
            return "gmail_create_draft: to + subject + body required"
        }
        // Gmail API expects RFC822 message in base64url under message.raw.
        val rfc = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$body"
        val raw = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rfc.toByteArray(Charsets.UTF_8))
        val r = GwsEffector.run(
            subcommand = "gmail users drafts create",
            params = mapOf("userId" to "me"),
            body = mapOf("message" to mapOf<String, Any>("raw" to raw)),
        )
        return when (r) {
            is GwsEffector.Result.Ok -> "gmail draft created (raw: ${r.stdout.take(400)})"
            is GwsEffector.Result.Err -> "gmail_create_draft failed (exit ${r.exitCode}): ${r.stderr.take(400)}"
        }
    }

    private fun dispatchWikiAppend(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val subject = (obj["subject"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val concept = (obj["concept"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val section = (obj["section"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val bullet = (obj["bullet"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (subject.isEmpty() || concept.isEmpty() || section.isEmpty() || bullet.isEmpty()) {
            return "wiki_append: subject + concept + section + bullet required"
        }
        val ok = WikiPage.append(subject, concept, section, bullet)
        return if (ok) "wiki_append: ok ($subject/$concept § $section)"
            else "wiki_append: rejected (PII or empty bullet)"
    }

    private fun dispatchShortestPath(argsJson: String): String {
        val obj = parseArgs(argsJson) ?: return "bad args json"
        val from = (obj["from"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val to = (obj["to"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (from.isEmpty() || to.isEmpty()) return "shortest_path: from + to required"
        val graph = KnowledgeGraphBuilder.load() ?: return "shortest_path: graph not built"
        val path = KnowledgeGraphQuery.shortestPath(graph, from, to)
        if (path.isEmpty()) return "shortest_path: no path between $from and $to"
        return "path (${path.size - 1} hops):\n  " + path.joinToString("\n  → ")
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
