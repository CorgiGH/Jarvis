package jarvis

import java.nio.file.Path
import java.time.Instant

/**
 * S4 — map the user's active window (process + title) to a known concept
 * via [ConceptCatalog.matchInText]. Best-match returned (longest concept
 * name wins on ties to favor specificity). Side-effect: bumps
 * KnowledgeState.touch with `source="activity"` and a small weight so
 * passive viewing counts as exposure.
 *
 * S5 — drift detection: if Schedule.currentBlock says "lecture: Probability"
 * but ActiveDoc says "Operating Systems" or "unknown", emit a high-importance
 * distraction signal. Caller (ProactiveLoop) handles cooldown + push.
 */
object ActiveDoc {

    /** Lightweight weight applied per passive activity-bumped concept. Low so
     *  it doesn't dominate explicit study touches. */
    private const val ACTIVITY_TOUCH_WEIGHT = 0.2f

    fun detect(
        entry: ActivityEntry,
        archivalRoot: Path = Config.archivalDir,
    ): ConceptCatalog.Concept? {
        val text = listOfNotNull(entry.title, entry.process).joinToString(" ").trim()
        if (text.isEmpty()) return null
        val matches = ConceptCatalog.matchInText(text, archivalRoot)
        // Longest concept name wins — "Markov chains" > "Markov" specificity.
        return matches.maxByOrNull { it.name.length }
    }

    /** Side-effecting helper: detect active concept for [entry] + write a
     *  KnowledgeState touch row. Cheap; called from /api/activity hook. */
    fun touchOnActivity(
        entry: ActivityEntry,
        archivalRoot: Path = Config.archivalDir,
        knowledgeFile: Path = Config.knowledgeFile,
        ts: Instant = runCatching { Instant.parse(entry.ts) }.getOrNull() ?: Instant.now(),
    ): ConceptCatalog.Concept? {
        val concept = detect(entry, archivalRoot) ?: return null
        KnowledgeState.touchTo(
            knowledgeFile,
            concept.name,
            concept.subject,
            "activity",
            ACTIVITY_TOUCH_WEIGHT,
            ts,
        )
        return concept
    }

    /** S5 — answer "is the user drifting from their current scheduled block?"
     *  Returns null when no current block OR no drift detected. */
    data class Drift(
        val expectedSubject: String,
        val expectedTopic: String?,
        val actualConcept: ConceptCatalog.Concept?,
        val actualReason: String,
    )

    fun detectDrift(
        entry: ActivityEntry,
        schedule: ScheduleFile,
        now: Instant = Instant.now(),
        archivalRoot: Path = Config.archivalDir,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Drift? {
        val block = Schedule.currentBlock(schedule, now, zone) ?: return null
        // Only nudge during *focused* block kinds.
        if (block.kind !in setOf("study", "review", "lecture", "lab")) return null
        val concept = detect(entry, archivalRoot)
        // Drift conditions:
        //   - actual concept's subject differs from block.subject, OR
        //   - no concept matched AND title looks like social/scrolling.
        val title = entry.title?.lowercase().orEmpty()
        val process = entry.process?.lowercase().orEmpty()
        val scrollingHint = listOf(
            "twitter", "x.com", " / x", "reddit", "instagram", "tiktok",
            "youtube", "facebook", "(@",
        ).any { title.contains(it) } ||
            // Android packages — phone activity logger reports package name.
            process in setOf(
                "com.instagram.android", "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill", "com.twitter.android",
                "com.x.android", "com.reddit.frontpage",
                "com.facebook.katana", "com.snapchat.android",
                "com.google.android.youtube",
            )
        if (concept != null && concept.subject != block.subject) {
            return Drift(
                expectedSubject = block.subject,
                expectedTopic = block.topic,
                actualConcept = concept,
                actualReason = "different subject (${concept.subject})",
            )
        }
        if (concept == null && scrollingHint) {
            return Drift(
                expectedSubject = block.subject,
                expectedTopic = block.topic,
                actualConcept = null,
                actualReason = "off-topic browser (likely social media)",
            )
        }
        return null
    }
}
