package jarvis

import jarvis.embeddings.EmbeddingsClient
import jarvis.embeddings.VectorStore
import jarvis.subsystem.SearchSubsystem
import jarvis.subsystem.SubsystemInput
import jarvis.subsystem.Subsystems
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        "catchup" -> executeCatchup(call.args)
        "next_block" -> executeNextBlock()
        "study_now" -> executeStudyNow(call.args)
        "wake" -> executeWake(call.args)
        "quiz" -> executeQuiz(call.args, client)
        "grade" -> executeGrade(call.args)
        "assignment_set" -> executeAssignmentSet(call.args)
        "assignment_progress" -> executeAssignmentProgress(call.args)
        "assignment_done" -> executeAssignmentDone(call.args)
        "assignments" -> executeAssignments()
        "grade_record" -> executeGradeRecord(call.args)
        "grades" -> executeGrades()
        "grades_sync_status" -> executeGradesSyncStatus()
        "push_status" -> executePushStatus()
        "feedback_summary" -> executeFeedbackSummary()
        "adherence" -> executeAdherence(call.args)
        "lesson" -> executeLesson(call.args, client)
        else -> "(unknown tool: ${call.name})"
    }

    /** Auto-fills today's schedule from NOW until end-of-day with study
     *  blocks. Anchors on user's actual wake time (call this when you
     *  wake up). Respects existing fixed blocks (lecture/lab/exam) — only
     *  fills GAPS. 90-min focus blocks separated by 30-min meal/break
     *  gaps every ~3 hours. Round-robins subjects with finals proximity
     *  bias. Writes to schedule.json directly.
     *
     *  Args: optional `bedtime=HH:MM` to cap fill before sleep (default
     *  23:30 since user's actual rhythm runs late). */
    private fun executeWake(args: String): String {
        val zone = java.time.ZoneId.of("Europe/Bucharest")
        val now = java.time.Instant.now()
        val ldt = java.time.LocalDateTime.ofInstant(now, zone)
        val today = ldt.toLocalDate()
        val nowTime = ldt.toLocalTime()
        // Parse optional bedtime arg.
        val bedtime = Regex("""bedtime=(\d{2}:\d{2})""").find(args)
            ?.groupValues?.get(1)
            ?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            ?: java.time.LocalTime.of(23, 30)
        if (nowTime >= bedtime) return "(it's already past bedtime — won't fill blocks past $bedtime)"

        val schedule = Schedule.load()
        // Existing today's blocks become "fixed" — don't overwrite or overlap.
        val existing = Schedule.todaysBlocks(schedule, now, zone)
            .mapNotNull { b ->
                val s = runCatching { java.time.LocalTime.parse(b.start) }.getOrNull()
                val e = runCatching { java.time.LocalTime.parse(b.end) }.getOrNull()
                if (s != null && e != null) Triple(s, e, b) else null
            }
            .sortedBy { it.first }

        // Subject rotation order — finals proximity bias if exam set,
        // else even spread. Catalog subjects.
        val subjects = ConceptCatalog.all().map { it.subject }.distinct().ifEmpty {
            listOf("PA", "ALO", "PS", "POO", "SO&RC")
        }

        // Walk forward from now. Free chunk = [cursor, nextFixedStart or bedtime].
        val newBlocks = mutableListOf<ScheduleBlock>()
        var cursor = nowTime
        var subjectIdx = 0
        var blocksSinceBreak = 0
        val focusMinutes = 90L
        val breakMinutes = 15L
        val mealMinutes = 30L
        // Round cursor up to next 5-min mark for clean times.
        cursor = cursor.plusMinutes((5 - cursor.minute % 5).toLong() % 5)

        while (cursor.isBefore(bedtime)) {
            // Find next fixed block start after cursor.
            val nextFixed = existing.firstOrNull { it.first.isAfter(cursor) }
            val chunkEnd = nextFixed?.first ?: bedtime
            val chunkMinutes = java.time.Duration.between(cursor, chunkEnd).toMinutes()
            if (chunkMinutes < 30) {
                // Skip tiny gap — jump to end of next fixed block.
                if (nextFixed != null) cursor = nextFixed.second else break
                continue
            }
            val blockMinutes = minOf(focusMinutes, chunkMinutes)
            val blockEnd = cursor.plusMinutes(blockMinutes)
            val subject = subjects[subjectIdx % subjects.size]
            newBlocks += ScheduleBlock(
                date = today.toString(),
                start = cursor.toString().substring(0, 5),
                end = blockEnd.toString().substring(0, 5),
                kind = "study",
                subject = subject,
                topic = "wake-fill: catch-up",
            )
            subjectIdx++
            blocksSinceBreak++
            // Insert break or meal after block.
            cursor = blockEnd
            if (cursor.isBefore(bedtime) && nextFixed?.first?.isAfter(cursor) != false) {
                val gapMins = if (blocksSinceBreak >= 3) {
                    blocksSinceBreak = 0; mealMinutes
                } else breakMinutes
                cursor = cursor.plusMinutes(gapMins)
                if (cursor.isAfter(bedtime)) break
            }
        }
        if (newBlocks.isEmpty()) return "(no fillable gaps found between now ($nowTime) and bedtime ($bedtime))"

        // Append new blocks to schedule.json.
        val merged = ScheduleFile(blocks = (schedule.blocks + newBlocks)
            .sortedWith(compareBy({ it.date }, { it.start })))
        try {
            val jsonText = kotlinx.serialization.json.Json {
                prettyPrint = true; encodeDefaults = true
            }.encodeToString(ScheduleFile.serializer(), merged)
            java.nio.file.Files.writeString(
                Config.scheduleFile, jsonText, Charsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            )
        } catch (e: Exception) {
            return "(wake fill failed: ${e.message?.take(160)})"
        }
        return "wake-filled ${newBlocks.size} blocks for $today (now → $bedtime):\n" +
            newBlocks.joinToString("\n") { b ->
                "  ${b.start}-${b.end} ${b.subject}"
            }
    }

    /** Cycle 7 — pending quiz state for [[quiz]]/[[grade]] flow. Single-slot
     *  is sufficient for a single user; lock guards concurrent /api/chat. */
    private val quizLock = Any()
    private var pendingQuiz: Pair<String, String>? = null  // concept to subject

    /** Cycle 7 — pick a concept (FSRS-due first, else weakest stale) and
     *  ask the LLM to generate ONE short recall question. Stores pending
     *  quiz so [[grade]] knows what to score. */
    private fun executeQuiz(args: String, client: Llm): String {
        val subjectFilter = args.trim().takeIf { it.isNotEmpty() }
        // 1. FSRS-due concepts (retrievability < 0.7)
        val due = KnowledgeFsrs.dueConcepts()
            .filter { subjectFilter == null || it.first.subject.equals(subjectFilter, true) }
            .firstOrNull()
        // 2. Else weakest from stale list
        val pick: Pair<String, String> = if (due != null) {
            due.first.concept to due.first.subject
        } else {
            val stale = KnowledgeState.staleConcepts()
                .filter { subjectFilter == null || it.subject.equals(subjectFilter, true) }
                .firstOrNull()
            if (stale != null) {
                stale.concept to stale.subject
            } else {
                val seen = KnowledgeState.stats().map { it.concept to it.subject }.toSet()
                val unseen = ConceptCatalog.all()
                    .filter { subjectFilter == null || it.subject.equals(subjectFilter, true) }
                    .firstOrNull { (it.name to it.subject) !in seen }
                if (unseen == null) return "(no concepts available — populate catalog)"
                unseen.name to unseen.subject
            }
        }
        synchronized(quizLock) { pendingQuiz = pick }
        val (concept, subject) = pick
        // Ask LLM for a single short question.
        val (reply, _) = try {
            runBlocking {
                client.complete(
                    messages = listOf(
                        ChatMessage("system",
                            "Generate ONE short open-ended recall question on the concept " +
                                "below. No multiple choice. No hints. Just the question."),
                        ChatMessage("user", "$subject — $concept"),
                    ),
                    maxTokens = 150,
                )
            }
        } catch (e: Exception) {
            return "QUIZ: $subject / $concept\n(LLM unavailable: ${e.javaClass.simpleName}; recall what you know, then [[grade: 1-4]])"
        }
        return "QUIZ: $subject / $concept\n${reply.trim()}\n\nWhen ready: [[grade: 1=again 2=hard 3=good 4=easy]]"
    }

    /** Cycle 7 — score the pending quiz with grade 1..4, write FSRS row. */
    private fun executeGrade(args: String): String {
        val grade = args.trim().toIntOrNull()?.coerceIn(1, 4)
            ?: return "(usage: [[grade: 1-4]])"
        val pick = synchronized(quizLock) {
            val p = pendingQuiz ?: return "(no pending quiz — run [[quiz: subject?]] first)"
            pendingQuiz = null
            p
        }
        val (concept, subject) = pick
        val state = KnowledgeFsrs.recordReview(concept, subject, grade)
        val nextDays = Fsrs.nextIntervalDays(state)
        val gradeName = listOf("", "again", "hard", "good", "easy")[grade]
        return "graded $subject/$concept = $gradeName\n" +
            "  next review in ${"%.1f".format(nextDays)}d (S=${"%.1f".format(state.stability)}, " +
            "D=${"%.1f".format(state.difficulty)})"
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

    /** Record a graded result. Slash-separated args:
     *  `SUBJECT / COMPONENT / EARNED / MAX [/ NOTE]`. Latest-row-wins
     *  per (subject, component) so re-recording a corrected grade
     *  just appends a new row.
     *
     *  Example: `[[grade_record: PA / Test 1 / 14.5 / 35 / midterm]]`
     */
    private fun executeGradeRecord(args: String): String {
        val parts = args.split("/").map { it.trim() }
        if (parts.size < 4) {
            return "(usage: [[grade_record: SUBJECT / COMPONENT / EARNED / MAX [/ NOTE]]])"
        }
        val subject = parts[0]
        val component = parts[1]
        if (subject.isEmpty() || component.isEmpty()) {
            return "(subject and component cannot be empty)"
        }
        val earned = parts[2].toDoubleOrNull()
            ?: return "(invalid earned: ${parts[2]})"
        val max = parts[3].toDoubleOrNull()
            ?: return "(invalid max: ${parts[3]})"
        val note = parts.drop(4).joinToString("/").trim().takeIf { it.isNotEmpty() }
        val entry = Grades.record(subject, component, earned, max, note)
        val pct = if (max > 0.0) (earned / max * 100).toInt() else 0
        return "(recorded ${entry.subject}/${entry.component} = " +
            "${"%.1f".format(earned)}/${"%.1f".format(max)} (${pct}%)" +
            (note?.let { " — $it" } ?: "") + ")"
    }

    /** Council 2026-05-08 round-3 (Devil's Advocate): surface the
     *  hourly grade-sync cron's failure state to chat. Without this,
     *  matricol-not-found / fetch-error / parse-error states only
     *  exist in /opt/jarvis/data/grades-sync-status.json — readable
     *  via [[read]] but not loud enough to catch a silent ledger
     *  corruption between sessions. */
    private fun executeGradesSyncStatus(): String {
        val statusPath = Config.stateDir.resolve("grades-sync-status.json")
        if (!statusPath.exists()) {
            return "(no sync-status file yet — cron has not run, or wrote elsewhere)"
        }
        val text = try {
            java.nio.file.Files.readString(statusPath, Charsets.UTF_8)
        } catch (e: Exception) {
            return "(failed to read $statusPath: ${e.message?.take(160)})"
        }
        val parsed = try {
            kotlinx.serialization.json.Json
                .parseToJsonElement(text)
                .jsonObject
        } catch (_: Exception) {
            return "(malformed sync-status JSON; raw: ${text.take(200)})"
        }
        val ts = parsed["ts"]?.jsonPrimitive?.contentOrNull ?: "?"
        val appended = parsed["appended"]?.jsonPrimitive?.intOrNull ?: 0
        val unchanged = parsed["unchanged"]?.jsonPrimitive?.intOrNull ?: 0
        val failures = parsed["failures"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        val subjects = parsed["subjects_synced"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        val sb = StringBuilder()
        sb.append("Last sync: $ts\n")
        sb.append("  appended: $appended, unchanged: $unchanged\n")
        sb.append("  subjects synced: ${subjects.joinToString(", ").ifEmpty { "none" }}\n")
        if (failures.isNotEmpty()) {
            sb.append("  ⚠ failures (").append(failures.size).append("):\n")
            for (f in failures) sb.append("    - ").append(f).append("\n")
        } else {
            sb.append("  no failures.")
        }
        return sb.toString().trimEnd()
    }

    /** Reads the last_push_status.json snapshot the daily Telegram cron
     *  writes after every send-daily-push.py run. Council 1778256562
     *  Risk Analyst HIGH mitigation: the push pipeline silent-fails
     *  (token revoke, network blip, .env syntax error all log to a
     *  file the user never reads). Surfacing the status as a chat tool
     *  turns silent-fail into a poll-visible flag. */
    private fun executePushStatus(): String {
        val statusPath = Config.stateDir.resolve("last_push_status.json")
        if (!statusPath.exists()) {
            return "(no push-status file yet — cron has not fired, or wrote elsewhere)"
        }
        val text = try {
            java.nio.file.Files.readString(statusPath, Charsets.UTF_8)
        } catch (e: Exception) {
            return "(failed to read $statusPath: ${e.message?.take(160)})"
        }
        val parsed = try {
            kotlinx.serialization.json.Json
                .parseToJsonElement(text)
                .jsonObject
        } catch (_: Exception) {
            return "(malformed push-status JSON; raw: ${text.take(200)})"
        }
        val ts = parsed["ts"]?.jsonPrimitive?.contentOrNull ?: "?"
        val today = parsed["today"]?.jsonPrimitive?.contentOrNull ?: "?"
        val dayN = parsed["day_n"]?.jsonPrimitive?.intOrNull ?: 0
        val httpCode = parsed["http_code"]?.jsonPrimitive?.intOrNull ?: -1
        val subject = parsed["subject"]?.jsonPrimitive?.contentOrNull
        val title = parsed["title"]?.jsonPrimitive?.contentOrNull
        val due = parsed["due_in_days"]?.jsonPrimitive?.intOrNull
        val err = parsed["error"]?.jsonPrimitive?.contentOrNull
        val sb = StringBuilder()
        val healthy = httpCode in 200..299
        sb.append(if (healthy) "✓ Last push OK\n" else "✗ Last push FAILED\n")
        sb.append("  Day $dayN ($today), pushed at $ts UTC\n")
        sb.append("  HTTP $httpCode")
        if (err != null && err.isNotEmpty()) sb.append(" — $err")
        sb.append("\n")
        if (subject != null) {
            val dueStr = due?.let { d ->
                when {
                    d < 0 -> "OVERDUE by ${-d}d"
                    d == 0 -> "DUE TODAY"
                    else -> "due in ${d}d"
                }
            } ?: "no due date"
            sb.append("  Surfaced: $subject — ${title ?: "?"} ($dueStr)\n")
        }
        // Staleness check — if the last push was more than 26 hours ago
        // (cron runs daily; tolerate a 2h slip), flag it. This catches
        // missed runs even when http_code last recorded was 200.
        val nowMs = java.time.Instant.now().toEpochMilli()
        val tsMs = runCatching { java.time.Instant.parse(ts).toEpochMilli() }
            .getOrNull()
        if (tsMs != null) {
            val ageH = (nowMs - tsMs) / 3_600_000
            if (ageH > 26) {
                sb.append("  ⚠ STALE — last push was ${ageH}h ago (cron runs every 24h)\n")
            }
        }
        return sb.toString().trimEnd()
    }

    /** Structured lesson tool (2026-05-09). User asked: "does it have a
     *  proper way to teach me stuff?" — existing [[quiz]] is one Q at a
     *  time, no scaffold. This generates a 4-section lesson:
     *    DEFINITION (1 paragraph from archival via inline search)
     *    WORKED EXAMPLE (step-by-step, fully solved)
     *    DRILL (a problem; solution withheld)
     *    CHECK (1 understanding question)
     *  v1: single-turn, no persistent lesson-state. Multi-turn (resume
     *  mid-lesson) deferred to next session.
     *
     *  Args parse:  "SUBJECT/CONCEPT"  e.g. "PA/greedy algorithms"
     *  If only SUBJECT given, picks weakest stale concept for that subject. */
    private fun executeLesson(args: String, client: Llm): String {
        val (subj, concept) = parseLessonArgs(args)
        // Pick concept if user gave only subject
        val targetConcept = concept ?: pickStaleConceptFor(subj)
        if (targetConcept == null) {
            return "(no concept to teach. Provide SUBJECT/CONCEPT or seed " +
                "ConceptCatalog with $subj entries.)"
        }
        // Pull lesson material from archival via lexical search.
        val searchHits = jarvis.subsystem.SearchSubsystem
            .searchIn(Config.archivalDir, targetConcept, k = 5)
            .take(3)
        val refsBlock = if (searchHits.isEmpty()) {
            "(no archival hits — bot will rely on general knowledge)"
        } else {
            searchHits.joinToString("\n\n") { hit ->
                val rel = runCatching {
                    Config.archivalDir.relativize(hit.path).toString()
                }.getOrElse { hit.path.toString() }
                "## $rel  (hits=${hit.hits})\n${hit.snippet}"
            }
        }
        val systemPrompt = """You are a structured tutor. Output a lesson on the given concept in this EXACT 4-section format:

DEFINITION
<one paragraph, plain prose, no jargon without expansion>

WORKED EXAMPLE
<a concrete instance of the concept, fully solved with each step labelled>

DRILL
<a problem similar to the worked example, OR a slight variation. Do NOT include the solution.>

CHECK
<a single conceptual question testing understanding (not memorization)>

Rules:
- Cite the archival files used (file path, one per cited claim) where relevant.
- No preamble. Start directly with "DEFINITION".
- Use Romanian if the source material is in Romanian; otherwise English.
- Total length under 600 words.
"""
        val userMsg = """
Subject: $subj
Concept: $targetConcept

# Reference material from archival
$refsBlock
""".trimIndent()
        val (reply, model) = kotlinx.coroutines.runBlocking {
            client.complete(
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userMsg),
                ),
                maxTokens = 800,
            )
        }
        return reply
    }

    private fun parseLessonArgs(args: String): Pair<String, String?> {
        val parts = args.split("/", limit = 2).map { it.trim() }
        val subj = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "PS"
        val concept = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        return subj.uppercase() to concept
    }

    private fun pickStaleConceptFor(subject: String): String? {
        val stale = jarvis.KnowledgeState.staleConcepts(
            confidenceFloor = 0.6f, minStaleDays = 1L,
        ).filter { it.subject.equals(subject, ignoreCase = true) }
            .sortedBy { it.confidence }
        return stale.firstOrNull()?.concept
            ?: ConceptCatalog.all()
                .firstOrNull { it.subject.equals(subject, ignoreCase = true) }
                ?.name
    }

    /** Closed-loop adherence check (2026-05-09). Reads push_history.jsonl
     *  + activity.jsonl, runs the daily classifier, and reports for the
     *  last N days (default 3) what was pushed vs what got worked on.
     *  Catches the failure mode where the same priority pushes daily and
     *  user ignores it for a week. */
    private fun executeAdherence(args: String): String {
        val n = args.trim().toIntOrNull()?.coerceIn(1, 14) ?: 3
        val historyFile = Config.stateDir.resolve("push_history.jsonl")
        val activityFile = Config.activityFile
        if (!historyFile.exists()) {
            return "(no push_history.jsonl yet — first daily push hasn't run via cron)"
        }
        // Parse recent push history rows
        val histRaw = try {
            java.nio.file.Files.readAllLines(historyFile, Charsets.UTF_8)
        } catch (e: Exception) {
            return "(failed to read push_history.jsonl: ${e.message?.take(160)})"
        }
        val history = histRaw.mapNotNull { line ->
            try {
                kotlinx.serialization.json.Json.parseToJsonElement(line.trim()).jsonObject
            } catch (_: Exception) { null }
        }
        // Build set of unique target dates (most recent first)
        val dates = history.mapNotNull { it["today"]?.jsonPrimitive?.contentOrNull }
            .distinct()
            .sortedDescending()
            .take(n)
        if (dates.isEmpty()) return "(push_history empty after parse)"
        val sb = StringBuilder("Adherence — last ${dates.size} day${if (dates.size == 1) "" else "s"}:\n")
        for (date in dates) {
            // For each date find latest push + sum activity by classify rules
            val push = history.filter { it["today"]?.jsonPrimitive?.contentOrNull == date }
                .maxByOrNull { it["ts"]?.jsonPrimitive?.contentOrNull ?: "" }
            val rec = push?.get("subject")?.jsonPrimitive?.contentOrNull ?: "?"
            // Re-implement classifier minimally inline: read activity.jsonl,
            // filter to date, count minutes per classify result. Mirror the
            // Python adherence.py rules. Kept short by relying on a small
            // ruleset; if the rules diverge, the chat tool may show a
            // different number than the morning push — acceptable since
            // the morning push is the load-bearing surface.
            val obs = classifyDateMinutes(activityFile, date)
            val total = obs.values.sum() - (obs["OTHER"] ?: 0)
            val recMin = obs[rec] ?: 0
            val pct = if (total > 0) (recMin * 100 / total) else 0
            sb.append("  $date — pushed $rec, ${pct}% adherence ($recMin/${total}m study)")
            val others = obs.entries.filter { it.key != rec && it.key != "OTHER" }
                .sortedByDescending { it.value }.take(2)
            if (others.isNotEmpty()) {
                sb.append("; instead: ").append(
                    others.joinToString(", ") { "${it.key} ${it.value}m" }
                )
            }
            sb.append("\n")
        }
        sb.append("(classifier rules in tools/adherence.py SUBJECT_RULES — flag a misclass and I'll tighten the regex)")
        return sb.toString().trimEnd()
    }

    private fun classifyDateMinutes(
        activityFile: java.nio.file.Path, dateIso: String
    ): Map<String, Int> {
        if (!activityFile.exists()) return emptyMap()
        val rules = listOf(
            "PS" to listOf("rstudio", "rmarkdown", "tema_a", "tema_b", "tema_c", "tema_d",
                "ps lab", "ps hw", "probabilit", "statistic", " ps -",
                "monte carlo", "kolmogorov", "laplace", "poisson", "binomial"),
            "ALO" to listOf("alo curs", "alo lab", "alo seminar", "alo tema",
                "spațiu liniar", "algebra liniar", "diagonalizab",
                "vector propriu", "valori proprii"),
            "PA" to listOf("algorithm design", "pa test", "pa curs", "pa seminar",
                "proiectarea algoritm", "np-complete", "np complete",
                "greedy algorithm", "huffman", "dijkstra", "kruskal",
                "dynamic programming", " pa ", "ed-pa", "pa-tests"),
            "POO" to listOf("object-oriented", "poo curs", "poo lab",
                "intellij idea", " poo ", " oop ", "schildt", "kaler",
                ".java", "javac", "polymorphism", "inheritance"),
            "RC" to listOf("socket", "tcp", "udp", "wireshark", "rc lab", "rc curs",
                "retele de calculatoare", "computer networks",
                " rc -", "ip header", "subnet", "ethernet"),
            "SO" to listOf("wsl", "linux", "ubuntu", "kernel", "fork()", "syscall",
                "/proc", "/dev/", "bash script", "shell script",
                "sisteme de operare", "sisteme-de-operare",
                "operating system", "operating-systems-and-computer-networks",
                "cristian.vidrascu", "tlpi", "kerrisk", "tanenbaum",
                "pid 0", "posix"),
        )
        val nonStudyProcs = setOf("discord.exe", "spotify.exe", "steam.exe",
            "league of legends.exe", "leagueclient.exe",
            "applicationframehost.exe", "explorer.exe",
            "minecraftlauncher.exe", "javaw.exe")
        val rows = mutableListOf<ActivityEntry>()
        java.nio.file.Files.readAllLines(activityFile, Charsets.UTF_8).forEach { line ->
            try {
                val e = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString(ActivityEntry.serializer(), line.trim())
                if ((e.ts).startsWith(dateIso)) rows.add(e)
            } catch (_: Exception) {}
        }
        rows.sortBy { it.ts }
        val out = mutableMapOf<String, Int>()
        for (i in rows.indices) {
            val e = rows[i]
            val proc = (e.process ?: "").lowercase()
            val title = (e.title ?: "").lowercase()
            val haystack = "$proc $title"
            val cls = if (proc in nonStudyProcs) {
                "OTHER"
            } else {
                rules.firstOrNull { (_, kws) -> kws.any { it in haystack } }?.first ?: "OTHER"
            }
            val gapMin = if (i + 1 < rows.size) {
                val curT = runCatching { java.time.Instant.parse(e.ts) }.getOrNull()
                val nxtT = runCatching { java.time.Instant.parse(rows[i + 1].ts) }.getOrNull()
                if (curT != null && nxtT != null) {
                    val g = java.time.Duration.between(curT, nxtT).toMinutes().toInt()
                    if (g > 10) 5 else if (g < 0) 0 else g
                } else 5
            } else 5
            out[cls] = (out[cls] ?: 0) + gapMin
        }
        return out
    }

    /** Phase 5.1 — feedback-driven retraining summary. Reads signals.jsonl +
     *  feedback.jsonl, surfaces per-action + per-kind action distribution
     *  and the threshold offset that ProactiveLoop is currently applying.
     *  Surfaces the basis text so the user can sanity-check the offset
     *  before the loop actually self-tunes. */
    private fun executeFeedbackSummary(): String {
        val signals = Signals.readAll()
        val feedback = Feedback.readAll()
        val stats = FeedbackConsumer.analyze(signals, feedback)
        val rec = FeedbackConsumer.recommend(stats)
        val effective = FeedbackConsumer.effectiveThreshold(rec)
        val sb = StringBuilder()
        sb.append("Feedback retraining summary:\n")
        sb.append("  signals: ${stats.totalSignals}, feedback rows: ${stats.totalFeedback}, ")
        sb.append("acked-distinct: ${stats.totalAcked}\n")
        sb.append("  by action: ")
        sb.append(stats.byAction.entries.sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}=${it.value}" }
            .ifEmpty { "(none)" })
        sb.append("\n")
        if (stats.byKind.isNotEmpty()) {
            sb.append("  by kind:\n")
            for ((kind, actions) in stats.byKind) {
                sb.append("    $kind: ")
                sb.append(actions.entries.joinToString(", ") { "${it.key}=${it.value}" })
                sb.append("\n")
            }
        }
        sb.append("  base: ${"%.2f".format(rec.baseThreshold)}, ")
        sb.append("offset: ${"%+.2f".format(rec.globalOffset)}, ")
        sb.append("effective: ${"%.2f".format(effective)}\n")
        if (rec.perKindOffset.isNotEmpty()) {
            sb.append("  per-kind offsets (informational): ")
            sb.append(rec.perKindOffset.entries
                .joinToString(", ") { "${it.key}${"%+.2f".format(it.value)}" })
            sb.append("\n")
        }
        sb.append("  basis: ${rec.basis}")
        return sb.toString().trimEnd()
    }

    /** List current per-subject grade summary, weakest subjects first
     *  so catch-up logic can use it as an importance signal. */
    private fun executeGrades(): String {
        val summary = Grades.summaryBySubject()
        if (summary.isEmpty()) {
            return "(no grades recorded yet — use [[grade_record: SUBJECT/COMPONENT/EARNED/MAX]])"
        }
        val lines = summary.joinToString("\n") { s ->
            val pct = if (s.totalMax > 0.0) (s.ratio * 100).toInt() else 0
            "  ${s.subject}: ${"%.1f".format(s.totalEarned)}/${"%.1f".format(s.totalMax)} " +
                "(${pct}%, ${s.componentCount} component${if (s.componentCount == 1) "" else "s"})"
        }
        return "Grades by subject (weakest first):\n$lines"
    }

    /** Multi-day catch-up planner. `[[catchup: N]]` returns a per-day plan
     *  for the next N days (default 7, max 30). Backed by
     *  StudyPlanner.multiDay — schedule blocks + stale review queue
     *  (uncapped logically, renderer caps at 8/day with reviewDebt
     *  overflow line) + 3 new untouched concepts/day with cross-day dedup
     *  and exam-window subject bias. */
    private fun executeCatchup(args: String): String {
        val days = args.trim().toIntOrNull()?.coerceIn(1, 30) ?: 7
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        val schedule = Schedule.load()
        val stats = KnowledgeState.stats(now)
        val fsrsRows = KnowledgeFsrs.readAllFrom(Config.knowledgeFsrsFile)
        val catalog = ConceptCatalog.all()
        val plan = StudyPlanner.multiDay(days, schedule, stats, fsrsRows, catalog, now, zone)
        return StudyPlanner.renderMultiDay(plan, schedule, now, zone)
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
            // Council 2026-05-08 round-2 (DE): apply the same path filter
            // as ConceptCatalog.scan so [[stats]] doesn't show inflated
            // counts that include _extras/ course-info docs + tests/
            // exam material. Single source of truth via isCatalogPath.
            Files.walk(Config.archivalDir).use { stream ->
                stream.filter { p ->
                    if (!p.isRegularFile() || !p.toString().endsWith(".md")) return@filter false
                    val rel = runCatching {
                        Config.archivalDir.relativize(p).toString().replace('\\', '/')
                    }.getOrElse { return@filter false }
                    ConceptCatalog.isCatalogPath(rel)
                }.count()
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
