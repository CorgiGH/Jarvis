package jarvis

import java.time.Duration
import java.time.Instant
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
}
