package jarvis

import jarvis.embeddings.EmbeddingsClient
import jarvis.embeddings.VectorStore
import jarvis.subsystem.SearchSubsystem
import jarvis.subsystem.SubsystemInput
import jarvis.subsystem.Subsystems
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
    /** F3 — Letta v1 / ReAct multi-step. Up to 3 tool rounds, total LLM calls
     *  ≤ 4 (initial + 3 follow-ups). Lets the model chain search → read →
     *  respond. Worst-case latency on Copilot CLI = ~60s; acceptable for
     *  research-style turns. Original ceiling of 1 was conservative cost guard;
     *  the cost vs depth tradeoff favors more depth now that Phase 1-3 is
     *  shipped + chat is the sole user interactive surface. */
    private const val MAX_ITERATIONS = 3

    /** Cap per-file expansion so a [[read: ...]] of a 5MB doc doesn't blow the
     *  follow-up prompt past the model's context window. */
    private const val READ_MAX_BYTES = 32 * 1024

    data class ToolCall(val name: String, val args: String)

    suspend fun runTurn(client: Llm, messages: List<ChatMessage>): Pair<String, String> =
        runTurnWith(client, messages) { call -> executeTool(call, client) }

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

    private fun executeTool(call: ToolCall, client: Llm): String = when (call.name) {
        "search" -> executeSearch(call.args)
        "read" -> executeRead(call.args)
        "recall" -> executeRecall(call.args)
        "time" -> executeTime()
        "remember" -> executeRemember(call.args)
        "wiki" -> executeWiki(call.args)
        "activity" -> executeActivity(call.args)
        "stats" -> executeStats()
        "sub" -> executeSub(call.args, client)
        "calendar" -> executeCalendar(call.args)
        "pin" -> executePin(call.args)
        "goal_set" -> executeGoalSet(call.args)
        "goal_progress" -> executeGoalProgress(call.args)
        "goals" -> executeGoals()
        "plan" -> executePlan(call.args)
        "next_block" -> executeNextBlock()
        "study_now" -> executeStudyNow(call.args)
        "assignment_set" -> executeAssignmentSet(call.args)
        "assignment_progress" -> executeAssignmentProgress(call.args)
        "assignment_done" -> executeAssignmentDone(call.args)
        "assignments" -> executeAssignments()
        else -> "(unknown tool: ${call.name})"
    }

    /** Council retro 2026-05-08: ignore schedule entirely. Pick the
     *  weakest unmastered concept (optionally subject-filtered) and
     *  suggest a 25-min Pomodoro block. Works at any hour, regardless
     *  of quiet hours. The "user is asking → user is awake" answer. */
    private fun executeStudyNow(args: String): String {
        val subjectFilter = args.trim().takeIf { it.isNotEmpty() }
        val stats = KnowledgeState.stats()
        val catalog = ConceptCatalog.all()
        // Subject-filtered weak first, then untouched.
        val weak = stats
            .filter { subjectFilter == null || it.subject.equals(subjectFilter, true) }
            .filter { it.confidence < 0.5f }
            .sortedBy { it.confidence }
            .firstOrNull()
        if (weak != null) {
            return "STUDY_NOW 25-min Pomodoro\n" +
                "  ${weak.subject} / ${weak.concept}\n" +
                "  why: confidence=${"%.2f".format(weak.confidence)}, stale ${weak.staleDays}d\n" +
                "  break in 25 min, then 5-min rest"
        }
        val seenKeys = stats.map { it.concept to it.subject }.toSet()
        val untouched = catalog
            .filter { subjectFilter == null || it.subject.equals(subjectFilter, true) }
            .firstOrNull { (it.name to it.subject) !in seenKeys }
        if (untouched != null) {
            return "STUDY_NOW 25-min Pomodoro\n" +
                "  ${untouched.subject} / ${untouched.name}\n" +
                "  why: never touched\n" +
                "  break in 25 min, then 5-min rest"
        }
        return "(no concepts available — populate concept catalog first)"
    }

    private fun executeNextBlock(): String {
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.of("Europe/Bucharest")
        val schedule = Schedule.load()
        val assignments = Assignments.current(now, zone)
        val stats = KnowledgeState.stats(now)
        val catalog = ConceptCatalog.all()
        val activity = Activity.loadEntries(hours = 1)
        val stress = StressProxy.current(activity, now, zone)
        val nb = Allocator.suggest(schedule, assignments, stats, catalog, stress, now, zone)
        return "${nb.activity.uppercase()} ${nb.subject} — ${nb.target}\n" +
            "  ${nb.durationMinutes}min until ${nb.endTs.take(16)}\n" +
            "  why: ${nb.rationale}"
    }

    /** Assignment set. Args: "<subject>|<title>|<due-YYYY-MM-DD>" */
    private fun executeAssignmentSet(args: String): String {
        val parts = args.split("|").map { it.trim() }
        if (parts.size < 2) return "(usage: [[assignment_set: <subject>|<title>|<due-YYYY-MM-DD>]])"
        val subject = parts[0]
        val title = parts[1]
        val due = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
        val id = Assignments.computeId(subject, title, due)
        Assignments.append(AssignmentEntry(
            id = id, ts = java.time.Instant.now().toString(), kind = "set",
            subject = subject, title = title, dueDate = due,
        ))
        return "(assignment set: id=$id, $subject/$title due=$due)"
    }

    private fun executeAssignmentProgress(args: String): String {
        val tokens = args.trim().split(Regex("\\s+"), limit = 2)
        if (tokens.size < 2) return "(usage: [[assignment_progress: <id> <note>]])"
        Assignments.append(AssignmentEntry(
            id = tokens[0], ts = java.time.Instant.now().toString(),
            kind = "progress", subject = "?", title = "?",
            note = tokens[1].take(300),
        ))
        return "(progress logged on ${tokens[0]})"
    }

    private fun executeAssignmentDone(id: String): String {
        val trim = id.trim()
        if (trim.isEmpty()) return "(usage: [[assignment_done: <id>]])"
        Assignments.append(AssignmentEntry(
            id = trim, ts = java.time.Instant.now().toString(),
            kind = "done", subject = "?", title = "?",
        ))
        return "(marked done: $trim)"
    }

    private fun executeAssignments(): String {
        val views = Assignments.current()
        if (views.isEmpty()) return "(no assignments tracked — [[assignment_set: <subject>|<title>|<YYYY-MM-DD>]])"
        return views.joinToString("\n") { v ->
            val dueTag = v.daysToDue?.let { d ->
                when {
                    d < 0 -> " OVERDUE-${-d}d"
                    d == 0L -> " TODAY"
                    d <= 3 -> " due in ${d}d"
                    else -> " due ${v.dueDate}"
                }
            } ?: ""
            val statusTag = if (v.status == "done") " ✓" else ""
            val note = v.latestNote?.let { " — last: \"$it\"" } ?: ""
            "[${v.id}] ${v.subject}/${v.title}$dueTag$statusTag$note"
        }
    }

    /** S3 — render today's study plan from Schedule × KnowledgeState × catalog. */
    private fun executePlan(args: String): String {
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        val schedule = Schedule.load()
        val stats = KnowledgeState.stats(now)
        val catalog = ConceptCatalog.all()
        val items = StudyPlanner.today(schedule, stats, catalog, now, zone)
        return StudyPlanner.render(items, schedule, now, zone)
    }

    /** F6 — declare a goal. id is sha256(text + day-bucket) so same-day
     *  duplicates collapse. */
    private fun executeGoalSet(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return "(empty goal text)"
        val capped = if (t.length > 300) t.take(300) + " […truncated]" else t
        val now = java.time.Instant.now()
        val id = Goals.computeId(capped, now)
        Goals.append(GoalEntry(id = id, ts = now.toString(), kind = "set", text = capped))
        return "(goal set: id=$id, \"${capped.take(60)}\")"
    }

    /** F6 — progress note. First token of args = goal id, rest = note text. */
    private fun executeGoalProgress(args: String): String {
        val tokens = args.trim().split(Regex("\\s+"), limit = 2)
        if (tokens.size < 2 || tokens[0].isBlank() || tokens[1].isBlank()) {
            return "(usage: [[goal_progress: <goal-id> <note>]])"
        }
        val parent = tokens[0]
        val note = tokens[1].take(300)
        val now = java.time.Instant.now()
        Goals.append(GoalEntry(
            id = Goals.computeId("progress|$parent", now),
            ts = now.toString(),
            kind = "progress",
            text = note,
            parentId = parent,
        ))
        return "(progress logged on $parent: \"${note.take(60)}\")"
    }

    /** F6 — list current goals with staleness for the model to surface. */
    private fun executeGoals(): String {
        val views = Goals.current()
        if (views.isEmpty()) return "(no goals set yet — use [[goal_set: <text>]])"
        return views.joinToString("\n") { v ->
            val staleTag = if (v.staleDays >= 7) " STALE-${v.staleDays}d" else ""
            val progress = v.latestProgressText?.let { " — last: \"$it\"" } ?: ""
            "[${v.id}] ${v.text} (set ${v.ageDays}d ago$staleTag)$progress"
        }
    }

    /** F2 — Letta-style core_memory write tool. Pins to `core_memory.md` so
     *  the text is prepended to every future system prompt. Distinct from
     *  `[[remember]]` which writes to wiki.md (recall-tier). PII-scanned
     *  before write — drops with WARN if it trips, returns the cleaned
     *  result. Append-only line per pin. */
    private fun executePin(content: String): String {
        val text = content.trim()
        if (text.isEmpty()) return "(empty pin content)"
        // Cap aggressive — core_memory is on every future system prompt.
        val capped = if (text.length > 500) text.take(500) + " […truncated]" else text
        val findings = CoreMemory.scanTextForPii(capped)
        if (findings.isNotEmpty()) {
            return "(pin denied: contains ${findings.joinToString(",") { it.kind }} — " +
                "core_memory must stay PII-clean per privacy contract)"
        }
        return try {
            val file = Config.coreMemoryFile
            val line = "\n[model-pin ${java.time.Instant.now()}] $capped\n"
            file.parent?.let { java.nio.file.Files.createDirectories(it) }
            java.nio.file.Files.writeString(
                file,
                line,
                Charsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND,
            )
            "(pinned to core_memory: ${capped.take(80)}${if (capped.length > 80) "…" else ""})"
        } catch (e: Exception) {
            "(pin failed: ${e.javaClass.simpleName}: ${e.message?.take(160)})"
        }
    }

    /** R8 — read-only Google Calendar via gcalcli subprocess. Default-OFF
     *  behind JARVIS_CALENDAR_ENABLED env so the chat tool exists but no-ops
     *  cleanly until the user runs `gcalcli init` interactively on VPS. */
    private fun executeCalendar(args: String): String {
        if (System.getenv("JARVIS_CALENDAR_ENABLED")?.lowercase() !in setOf("1", "true", "yes")) {
            return "(calendar not configured — see docs/superpowers/specs/2026-05-08-R8-calendar-readonly-design.md)"
        }
        val range = args.trim().ifEmpty { "today" }
        // Whitelist range tokens — anything else passes through to gcalcli's
        // own argument parser. ProcessBuilder array-mode prevents shell injection.
        val gcalArgs: List<String> = when (range) {
            "today" -> listOf("agenda")
            "tomorrow" -> listOf("agenda", "tomorrow")
            "week" -> listOf("agenda", "next week")
            else -> listOf("agenda", range)
        }
        return try {
            val proc = ProcessBuilder(listOf("gcalcli") + gcalArgs)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return "(calendar fetch timed out)"
            }
            val out = proc.inputStream.bufferedReader().readText().take(2048)
            if (out.isBlank()) "(no calendar entries for \"$range\")" else out
        } catch (e: Exception) {
            "(calendar fetch failed: ${e.javaClass.simpleName})"
        }
    }

    private fun executeActivity(args: String): String {
        val hours = args.trim().toLongOrNull()?.coerceIn(1, 168)
            ?: Config.ACTIVITY_LOOKBACK_HOURS
        return Activity.loadRecent(hours = hours)
    }

    private fun executeStats(): String {
        val convCount = Conversations.readAll().size
        val wikiSections = MemoryWiki.readAll()
            .split(Regex("(?=^## )", RegexOption.MULTILINE))
            .count { it.startsWith("## ") }
        val archivalFiles = if (Config.archivalDir.exists()) {
            Files.walk(Config.archivalDir).use { stream ->
                stream.filter { it.isRegularFile() && it.toString().endsWith(".md") }.count()
            }
        } else 0
        val embeddings = VectorStore.all().size
        val activityEntries = Activity.loadEntries(hours = 24 * 365).size
        return "conversations.jsonl rows: $convCount\n" +
            "wiki.md sections: $wikiSections\n" +
            "archival/ .md files: $archivalFiles\n" +
            "embeddings entries: $embeddings\n" +
            "activity.jsonl entries (last year): $activityEntries"
    }

    private fun executeSub(args: String, client: Llm): String {
        val tokens = args.trim().split(Regex("\\s+"), limit = 2)
        if (tokens.isEmpty() || tokens[0].isEmpty()) {
            return "(empty sub call) — available: " +
                Subsystems.all.joinToString(", ") { it.name }
        }
        val name = tokens[0]
        val query = tokens.getOrNull(1)
        val sub = Subsystems.get(name)
            ?: return "(unknown subsystem: $name) — available: " +
                Subsystems.all.joinToString(", ") { it.name }
        // Don't recursively invoke `sub` from within sub (would call ChatTools
        // which could call sub again). Subsystems use client.complete directly,
        // not ChatTools.runTurn, so this is already safe — but still cap below.
        return try {
            val output = runBlocking {
                sub.run(
                    client,
                    SubsystemInput(
                        activity = Activity.loadEntries(),
                        wiki = MemoryWiki.recent(),
                        recentChat = Conversations.recentAsChatMessages(),
                        userQuery = query,
                    ),
                )
            }
            output.text
        } catch (e: Exception) {
            "(sub:$name failed: ${e.javaClass.simpleName}: ${e.message?.take(160)})"
        }
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
        // R4 — hybrid retrieval (Mem0 3-pass parity). Semantic pass is gated
        // on OPENROUTER_API_KEY; if absent the lexical+entity passes carry
        // alone (graceful degradation, no error).
        val key = resolveOpenRouterKey()
        val embedFn: (suspend (String) -> FloatArray)? = if (!key.isNullOrBlank()) {
            { q2 -> EmbeddingsClient(key).use { c -> c.embed(q2) } }
        } else null
        val hits = try {
            runBlocking { HybridRetriever.search(q, k = 5, semanticEmbed = embedFn) }
        } catch (e: Exception) {
            return "(recall failed: ${e.javaClass.simpleName}: ${e.message?.take(160)})"
        }
        if (hits.isEmpty()) return "(no matches for \"$q\")"
        return hits.joinToString("\n\n") { hit ->
            "[${hit.source} score=${"%.4f".format(hit.score)}] ${hit.id}\n${hit.snippet}"
        }
    }
}
