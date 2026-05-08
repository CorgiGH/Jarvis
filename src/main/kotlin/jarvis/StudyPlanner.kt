package jarvis

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * S3 — daily study plan. Pure function over (Schedule, KnowledgeState,
 * current Activity). Output is an ordered, rationale-tagged list of items
 * for the user to focus on today.
 *
 * Composition (priority order):
 *   1. Schedule blocks happening today (verbatim).
 *   2. Stale-concept revision queue (KnowledgeState.staleConcepts), boosted
 *      by next-exam-window proximity (≤7 days bumps the subject ahead).
 *   3. "Catch-up" items — concepts in catalog with NO confidence at all,
 *      sorted by subject closest to next exam.
 *
 * Surface: `[[plan: today]]` chat tool + can be wired into ctx-model.
 */
object StudyPlanner {

    @Serializable
    data class PlanItem(
        val kind: String,        // "block" | "review" | "catchup"
        val subject: String,
        val topic: String? = null,
        val rationale: String,
        val timeHint: String? = null,
    )

    private const val EXAM_PROXIMITY_BOOST_DAYS = 7L
    private const val MAX_REVIEW_ITEMS = 8
    private const val MAX_CATCHUP_ITEMS = 3

    // multiDay-specific constants. Council 2026-05-08 round 2 (DE):
    // review queue is uncapped logically — FSRS produces what it produces;
    // capping below FSRS-due count creates Anki "review hell" backlog.
    // Renderer caps display at 8/day; surplus surfaces as `reviewDebt: Int`
    // with a "review debt: N rows due" line so user can bulk-clear.
    // Council round 2 KEY_CONCERN (DE): mirrors Anki's actual UX of showing
    // ALL due reviews + Cram/Custom Study path for overflow.
    private const val REVIEW_DISPLAY_CAP = 8
    private const val MAX_NEW_CATCHUP_PER_DAY = 3
    private const val EXAM_NOTE_WINDOW_DAYS = 14L

    /** A single day in the [[catchup: N]] multi-day plan. Components are
     *  separated so the renderer can format each section independently and
     *  callers can construct alternate views (e.g. JSON for the Android UI). */
    @Serializable
    data class DayPlan(
        val date: String,           // ISO yyyy-MM-dd; serialized as String to keep
                                     // kotlinx.serialization happy without a
                                     // custom LocalDate serializer.
        val blocks: List<PlanItem>,
        val reviews: List<PlanItem>,
        val newCatchup: List<PlanItem>,
        val reviewDebt: Int,        // count above REVIEW_DISPLAY_CAP
        val examNote: String? = null,
    )

    fun today(
        schedule: ScheduleFile,
        knowledgeStats: List<ConceptStat>,
        catalog: List<ConceptCatalog.Concept>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlanItem> {
        val out = mutableListOf<PlanItem>()

        // 1. Today's schedule.
        val blocks = Schedule.todaysBlocks(schedule, now, zone)
        for (b in blocks) {
            out += PlanItem(
                kind = "block",
                subject = b.subject,
                topic = b.topic,
                rationale = "scheduled ${b.kind}",
                timeHint = "${b.start}-${b.end}",
            )
        }

        // 2. Stale-concept revision queue.
        val nextExam = Schedule.nextExam(schedule, now, zone)
        val daysToExam = Schedule.daysUntilNextExam(schedule, now, zone)
        val examSubject = nextExam?.subject

        val stale = knowledgeStats
            .filter { it.confidence < 0.4f && it.staleDays >= 3L }
            .sortedWith(
                compareByDescending<ConceptStat> {
                    if (examSubject != null && it.subject == examSubject &&
                        (daysToExam ?: 999L) <= EXAM_PROXIMITY_BOOST_DAYS
                    ) 1 else 0
                }.thenBy { it.confidence }
            )
            .take(MAX_REVIEW_ITEMS)
        for (s in stale) {
            val examTag = if (s.subject == examSubject) " [exam soon]" else ""
            out += PlanItem(
                kind = "review",
                subject = s.subject,
                topic = s.concept,
                rationale = "stale ${s.staleDays}d, conf=${"%.2f".format(s.confidence)}$examTag",
            )
        }

        // 3. Catch-up: concepts in catalog with zero exposure.
        // Round-robin across subjects when no exam-window bias applies, so
        // a single subject doesn't dominate the catch-up list alphabetically.
        val seenConcepts = knowledgeStats.map { it.concept to it.subject }.toSet()
        val unseen = catalog.filter { (it.name to it.subject) !in seenConcepts }
        val catchup = if (examSubject != null) {
            unseen.filter { it.subject == examSubject }.ifEmpty { unseen }
        } else {
            // Round-robin across subjects.
            val bySubject = unseen.groupBy { it.subject }
            val keys = bySubject.keys.toList()
            val merged = mutableListOf<ConceptCatalog.Concept>()
            var idx = 0
            while (merged.size < MAX_CATCHUP_ITEMS && idx < keys.size * 10) {
                val subj = keys[idx % keys.size]
                val list = bySubject[subj] ?: continue
                val taken = merged.count { it.subject == subj }
                if (taken < list.size) merged += list[taken]
                idx++
            }
            merged
        }
        for (c in catchup.take(MAX_CATCHUP_ITEMS)) {
            out += PlanItem(
                kind = "catchup",
                subject = c.subject,
                topic = c.name,
                rationale = "never touched",
            )
        }

        return out
    }

    fun render(items: List<PlanItem>, schedule: ScheduleFile, now: Instant, zone: ZoneId): String {
        val nextExamLine = Schedule.nextExam(schedule, now, zone)?.let { ex ->
            val days = Schedule.daysUntilNextExam(schedule, now, zone)
            "Next exam: ${ex.subject} in ${days}d (${ex.date})\n\n"
        } ?: ""
        if (items.isEmpty()) {
            return if (nextExamLine.isNotEmpty()) {
                nextExamLine + "(no plan items — populate schedule.json + start studying)"
            } else {
                "(no schedule + no knowledge state — populate schedule.json + start studying)"
            }
        }
        val byKind = items.groupBy { it.kind }
        val sb = StringBuilder(nextExamLine)
        byKind["block"]?.let {
            sb.append("Today's schedule:\n")
            for (item in it) {
                sb.append("  [${item.timeHint}] ${item.subject}")
                item.topic?.let { t -> sb.append(" — $t") }
                sb.append(" (${item.rationale})\n")
            }
            sb.append("\n")
        }
        byKind["review"]?.let {
            sb.append("Revision queue (stale concepts):\n")
            for (item in it) {
                sb.append("  ${item.subject}/${item.topic} — ${item.rationale}\n")
            }
            sb.append("\n")
        }
        byKind["catchup"]?.let {
            sb.append("Catch-up (untouched concepts):\n")
            for (item in it) {
                sb.append("  ${item.subject}/${item.topic} — ${item.rationale}\n")
            }
        }
        return sb.toString().trimEnd()
    }

    /**
     * Multi-day catch-up planner. Returns a `DayPlan` per day in
     * `[today, today + days)`. Backs the `[[catchup: N]]` chat tool.
     *
     * Per council 2026-05-08 round 2:
     *  - Reviews uncapped logically; renderer caps at REVIEW_DISPLAY_CAP=8
     *    and surfaces overflow as `reviewDebt`.
     *  - New-catchup capped at MAX_NEW_CATCHUP_PER_DAY=3; deduped across
     *    days via internal `usedNewCatchup` set so a 7-day plan covers up
     *    to 21 distinct catchup concepts (if catalog has that many).
     *
     * `fsrsRows` is accepted for future FSRS-due-day-aware queueing but
     * v1 just uses [knowledgeStats] for the review queue. Once a row gets
     * graded, KnowledgeFsrs.recordReview also calls KnowledgeState.touchTo,
     * so the same concept surfaces via [knowledgeStats] anyway.
     */
    fun multiDay(
        days: Int,
        schedule: ScheduleFile,
        knowledgeStats: List<ConceptStat>,
        @Suppress("UNUSED_PARAMETER") fsrsRows: List<FsrsRow>,
        catalog: List<ConceptCatalog.Concept>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayPlan> {
        val today = LocalDateTime.ofInstant(now, zone).toLocalDate()
        val daysClamped = days.coerceIn(1, 30)

        val stale = knowledgeStats
            .filter { it.confidence < 0.4f && it.staleDays >= 3L }
            .sortedBy { it.confidence }
        val reviewsRendered = stale.take(REVIEW_DISPLAY_CAP).map { s ->
            PlanItem(
                kind = "review",
                subject = s.subject,
                topic = s.concept,
                rationale = "stale ${s.staleDays}d, conf=${"%.2f".format(s.confidence)}",
            )
        }
        val reviewDebt = (stale.size - REVIEW_DISPLAY_CAP).coerceAtLeast(0)

        val seenConcepts: Set<Pair<String, String>> =
            knowledgeStats.map { it.concept to it.subject }.toSet()
        val usedNewCatchup = mutableSetOf<Pair<String, String>>()

        return (0 until daysClamped).map { idx ->
            val d = today.plusDays(idx.toLong())
            val dInstant = d.atTime(8, 0).atZone(zone).toInstant()

            val blocks = schedule.blocks
                .filter { it.date == d.toString() }
                .sortedBy { it.start }
                .map { b ->
                    PlanItem(
                        kind = "block",
                        subject = b.subject,
                        topic = b.topic,
                        rationale = "scheduled ${b.kind}",
                        timeHint = "${b.start}-${b.end}",
                    )
                }

            val nextExam = Schedule.nextExam(schedule, dInstant, zone)
            val daysToExam = Schedule.daysUntilNextExam(schedule, dInstant, zone)
            val withinWindow = nextExam != null &&
                (daysToExam ?: Long.MAX_VALUE) <= EXAM_NOTE_WINDOW_DAYS
            val examNote = if (withinWindow && nextExam != null) {
                "${nextExam.subject} exam in ${daysToExam}d (${nextExam.date})"
            } else null
            val examSubject = if (withinWindow) nextExam?.subject else null

            val untouched = catalog.filter { c ->
                (c.name to c.subject) !in seenConcepts &&
                    (c.name to c.subject) !in usedNewCatchup
            }
            val candidates: List<ConceptCatalog.Concept> = if (examSubject != null) {
                untouched.filter { it.subject == examSubject }.ifEmpty { untouched }
            } else {
                val bySubject = untouched.groupBy { it.subject }
                val keys = bySubject.keys.toList()
                val merged = mutableListOf<ConceptCatalog.Concept>()
                var k = 0
                val maxIters = (keys.size + 1) * MAX_NEW_CATCHUP_PER_DAY * 2
                while (merged.size < MAX_NEW_CATCHUP_PER_DAY && k < maxIters && keys.isNotEmpty()) {
                    val subj = keys[k % keys.size]
                    val list = bySubject[subj].orEmpty()
                    val taken = merged.count { it.subject == subj }
                    if (taken < list.size) merged += list[taken]
                    k++
                }
                merged
            }
            val newCatchup = candidates.take(MAX_NEW_CATCHUP_PER_DAY).map { c ->
                usedNewCatchup += (c.name to c.subject)
                PlanItem(
                    kind = "catchup",
                    subject = c.subject,
                    topic = c.name,
                    rationale = if (examSubject != null) "exam soon" else "never touched",
                )
            }

            DayPlan(
                date = d.toString(),
                blocks = blocks,
                reviews = reviewsRendered,
                newCatchup = newCatchup,
                reviewDebt = reviewDebt,
                examNote = examNote,
            )
        }
    }

    /** Markdown render for `[[catchup: N]]` reply body. */
    fun renderMultiDay(
        plan: List<DayPlan>,
        @Suppress("UNUSED_PARAMETER") schedule: ScheduleFile,
        @Suppress("UNUSED_PARAMETER") now: Instant,
        @Suppress("UNUSED_PARAMETER") zone: ZoneId,
    ): String {
        if (plan.isEmpty()) return "(no plan — populate schedule.json + start studying)"
        val sb = StringBuilder()
        val firstDate = plan.first().date
        val lastDate = plan.last().date
        sb.append("# Catch-up plan: $firstDate → $lastDate\n\n")
        plan.first().examNote?.let { sb.append("Next exam: $it\n\n") }
        for (day in plan) {
            sb.append("## ${day.date}\n")
            if (day.blocks.isEmpty() && day.reviews.isEmpty() && day.newCatchup.isEmpty()) {
                sb.append("(no items)\n\n")
                continue
            }
            for (b in day.blocks) {
                sb.append("- [${b.timeHint}] ${b.subject}")
                b.topic?.let { t -> sb.append(" — $t") }
                sb.append(" (${b.rationale})\n")
            }
            if (day.reviews.isNotEmpty()) {
                sb.append("Reviews:\n")
                for (r in day.reviews) {
                    sb.append("- ${r.subject}/${r.topic} — ${r.rationale}\n")
                }
            }
            if (day.reviewDebt > 0) {
                sb.append("Review debt: ${day.reviewDebt} more rows due — bulk-clear when free.\n")
            }
            if (day.newCatchup.isNotEmpty()) {
                sb.append("Catch-up:\n")
                for (c in day.newCatchup) {
                    sb.append("- ${c.subject}/${c.topic} — ${c.rationale}\n")
                }
            }
            sb.append("\n")
        }
        return sb.toString().trimEnd()
    }
}
