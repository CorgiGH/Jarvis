package jarvis

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily allocator. Given the user's current state (schedule + assignments +
 * knowledge gaps + sleep + stress), proposes the SINGLE next free block to
 * spend on what gives the most leverage right now.
 *
 * Constraints:
 *   - Honor scheduled blocks (don't overwrite a class).
 *   - Skip the user's typical sleep hours (use Schedule TZ + 23–07 default).
 *   - Bias toward overdue / due-soon assignments.
 *   - Bias toward the next-exam subject's stale concepts.
 *   - Bias against high stress windows (defer hard tasks to later).
 *
 * Output: ONE [[NextBlock]] suggestion with rationale.
 */
@Serializable
data class NextBlock(
    val startTs: String,
    val endTs: String,
    val subject: String,
    val activity: String,         // "study" | "review" | "practice" | "assignment" | "rest"
    val target: String,            // concept name / assignment title / etc.
    val rationale: String,
    val durationMinutes: Long,
)

object Allocator {

    private const val DEFAULT_BLOCK_MINUTES = 90L
    private const val MIN_BLOCK_MINUTES = 30L
    private const val SLEEP_START = 23
    private const val SLEEP_END = 7

    fun suggest(
        schedule: ScheduleFile,
        assignments: List<AssignmentView>,
        knowledgeStats: List<ConceptStat>,
        catalog: List<ConceptCatalog.Concept>,
        stress: StressLevel,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NextBlock {
        val ldt = LocalDateTime.ofInstant(now, zone)
        val nowTime = ldt.toLocalTime()

        // 1. If currently in a scheduled block, follow that.
        val current = Schedule.currentBlock(schedule, now, zone)
        if (current != null) {
            val endLdt = ldt.toLocalDate().atTime(parseTime(current.end) ?: nowTime)
            val endInstant = endLdt.atZone(zone).toInstant()
            return NextBlock(
                startTs = now.toString(),
                endTs = endInstant.toString(),
                subject = current.subject,
                activity = current.kind,
                target = current.topic ?: current.subject,
                rationale = "currently in scheduled ${current.kind} block — keep going",
                durationMinutes = Duration.between(now, endInstant).toMinutes().coerceAtLeast(0),
            )
        }

        // 2. If user is in sleep window, suggest rest.
        val hour = nowTime.hour
        if (hour >= SLEEP_START || hour < SLEEP_END) {
            return NextBlock(
                startTs = now.toString(),
                endTs = now.plus(Duration.ofMinutes(DEFAULT_BLOCK_MINUTES)).toString(),
                subject = "rest",
                activity = "rest",
                target = "sleep",
                rationale = "sleep window — finals catch-up depends on sleep, take rest",
                durationMinutes = DEFAULT_BLOCK_MINUTES,
            )
        }

        // 3. Find next scheduled block today; allocate freely up to it.
        val todays = Schedule.todaysBlocks(schedule, now, zone)
        val nextBlock = todays.firstOrNull { b ->
            parseTime(b.start)?.let { it > nowTime } == true
        }
        val freeUntilLdt = nextBlock?.let { ldt.toLocalDate().atTime(parseTime(it.start) ?: nowTime) }
            ?: ldt.toLocalDate().atTime(SLEEP_START, 0)
        val freeUntilInstant = freeUntilLdt.atZone(zone).toInstant()
        val freeMinutes = Duration.between(now, freeUntilInstant).toMinutes()

        if (freeMinutes < MIN_BLOCK_MINUTES) {
            return NextBlock(
                startTs = now.toString(),
                endTs = freeUntilInstant.toString(),
                subject = nextBlock?.subject ?: "break",
                activity = "break",
                target = nextBlock?.let { "next: ${it.subject} ${it.start}" } ?: "rest",
                rationale = "<${MIN_BLOCK_MINUTES} min until next obligation — short break instead",
                durationMinutes = freeMinutes.coerceAtLeast(0),
            )
        }

        val durationMinutes = freeMinutes.coerceAtMost(DEFAULT_BLOCK_MINUTES)
        val endInstant = now.plus(Duration.ofMinutes(durationMinutes))

        // 4. Priority: overdue → due-soon assignment → exam-soon stale concept
        //    → exam-soon untouched → general stale → general untouched.
        val overdue = assignments.firstOrNull { it.status == "overdue" }
        if (overdue != null) {
            return NextBlock(
                startTs = now.toString(), endTs = endInstant.toString(),
                subject = overdue.subject,
                activity = "assignment",
                target = "${overdue.title} (id=${overdue.id})",
                rationale = "OVERDUE by ${-(overdue.daysToDue ?: 0)}d — clear it first",
                durationMinutes = durationMinutes,
            )
        }

        // High-stress: prefer review (lighter) over deep study.
        val highStress = stress.score > 0.6f

        val dueSoon = assignments.firstOrNull { it.status == "due-soon" }
        if (dueSoon != null) {
            return NextBlock(
                startTs = now.toString(), endTs = endInstant.toString(),
                subject = dueSoon.subject,
                activity = "assignment",
                target = "${dueSoon.title} (id=${dueSoon.id})",
                rationale = "due in ${dueSoon.daysToDue}d — start now",
                durationMinutes = durationMinutes,
            )
        }

        val nextExam = Schedule.nextExam(schedule, now, zone)
        val examSubject = nextExam?.subject
        val daysToExam = Schedule.daysUntilNextExam(schedule, now, zone)

        val examWeak = if (examSubject != null && (daysToExam ?: 999L) <= 14L) {
            knowledgeStats
                .filter { it.subject == examSubject && it.confidence < 0.5f }
                .sortedBy { it.confidence }
                .firstOrNull()
        } else null

        if (examWeak != null) {
            return NextBlock(
                startTs = now.toString(), endTs = endInstant.toString(),
                subject = examSubject!!,
                activity = if (highStress) "review" else "study",
                target = examWeak.concept,
                rationale = "exam in ${daysToExam}d, weakest in $examSubject (conf=${"%.2f".format(examWeak.confidence)})",
                durationMinutes = durationMinutes,
            )
        }

        // Untouched in exam subject?
        val seenInExam = if (examSubject != null) {
            knowledgeStats.filter { it.subject == examSubject }.map { it.concept }.toSet()
        } else emptySet()
        val untouchedExam = if (examSubject != null) {
            catalog.firstOrNull { it.subject == examSubject && it.name !in seenInExam }
        } else null
        if (untouchedExam != null) {
            return NextBlock(
                startTs = now.toString(), endTs = endInstant.toString(),
                subject = examSubject!!,
                activity = "study",
                target = untouchedExam.name,
                rationale = "exam in ${daysToExam}d, never touched: ${untouchedExam.name}",
                durationMinutes = durationMinutes,
            )
        }

        // General stale.
        val generalStale = knowledgeStats
            .filter { it.confidence < 0.4f && it.staleDays >= 3L }
            .sortedBy { it.confidence }
            .firstOrNull()
        if (generalStale != null) {
            return NextBlock(
                startTs = now.toString(), endTs = endInstant.toString(),
                subject = generalStale.subject,
                activity = "review",
                target = generalStale.concept,
                rationale = "no exam-window pressure; refreshing weakest stale (${generalStale.staleDays}d)",
                durationMinutes = durationMinutes,
            )
        }

        // Fallback: any untouched concept, any subject.
        val anyUntouched = catalog.firstOrNull { c ->
            knowledgeStats.none { it.concept == c.name && it.subject == c.subject }
        }
        if (anyUntouched != null) {
            return NextBlock(
                startTs = now.toString(), endTs = endInstant.toString(),
                subject = anyUntouched.subject,
                activity = "study",
                target = anyUntouched.name,
                rationale = "open canvas — pick one untouched concept",
                durationMinutes = durationMinutes,
            )
        }

        // Truly nothing to do — rest.
        return NextBlock(
            startTs = now.toString(), endTs = endInstant.toString(),
            subject = "rest",
            activity = "rest",
            target = "free time",
            rationale = "no overdue, no exam window, no stale, no untouched — take a break",
            durationMinutes = durationMinutes,
        )
    }

    private fun parseTime(s: String): LocalTime? =
        runCatching { LocalTime.parse(s) }.getOrNull()
}
