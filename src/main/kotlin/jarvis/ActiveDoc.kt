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
        // 2026-05-09 expansion — user flagged actual distractions (Magic
        // Garden / Discord / Minecraft / streaming) weren't catching drift
        // because earlier hint list only matched social-media classics.
        // Three drift surfaces now:
        //   (a) different-subject concept matched in title (existing)
        //   (b) entertainment / scrolling title hint
        //   (c) known non-study process name (covers PC + phone packages)
        val entertainmentHints = listOf(
            // social
            "twitter", "x.com", " / x", "reddit", "instagram", "tiktok",
            "youtube", "facebook", "(@", "snapchat",
            // streaming
            "twitch", "netflix", "disney+", "prime video", "hbo max",
            "the boys", " s01e", " s02e", " s03e", "- google chrome",
            // games (browser + standalone)
            "magic garden", "minecraft", "league of legends", "valorant",
            "counter-strike", "csgo", "dota", "overwatch",
            "fortnite", "apex legends", "roblox",
            // misc browsing
            "google search", "athens", "wikipedia",
        )
        val nonStudyProcesses = setOf(
            "discord.exe", "spotify.exe", "steam.exe", "applicationframehost.exe",
            "leagueclient.exe", "league of legends.exe", "csgo.exe",
            "minecraft.exe", "minecraftlauncher.exe", "javaw.exe",
            "valorant.exe", "vanguard.exe", "dota2.exe", "overwatch.exe",
            "obs64.exe", "obs.exe", "vlc.exe",
            // Android packages (phone-side activity)
            "com.instagram.android", "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill", "com.twitter.android",
            "com.x.android", "com.reddit.frontpage",
            "com.facebook.katana", "com.snapchat.android",
            "com.google.android.youtube",
            "com.discord", "com.spotify.music",
            "com.netflix.mediaclient", "tv.twitch.android.app",
            "com.mojang.minecraftpe",
            "org.telegram.messenger", "phone:org.telegram.messenger",
            "com.whatsapp", "com.samsung.android.app.spage",
            "com.sec.android.app.launcher",
        )
        val processIsNonStudy = process in nonStudyProcesses ||
            // Match phone-prefixed entries — logger writes "phone:com.foo".
            (process.startsWith("phone:") &&
                process.removePrefix("phone:") in nonStudyProcesses) ||
            // Or title prefix.
            title.startsWith("phone:") &&
                title.removePrefix("phone:") in nonStudyProcesses
        val titleIsEntertainment = entertainmentHints.any { title.contains(it) }

        if (concept != null && concept.subject != block.subject) {
            return Drift(
                expectedSubject = block.subject,
                expectedTopic = block.topic,
                actualConcept = concept,
                actualReason = "different subject (${concept.subject})",
            )
        }
        if (concept == null && (titleIsEntertainment || processIsNonStudy)) {
            val hint = when {
                processIsNonStudy -> "non-study app (${process.take(40)})"
                else -> "entertainment / browsing (${title.take(40)})"
            }
            return Drift(
                expectedSubject = block.subject,
                expectedTopic = block.topic,
                actualConcept = null,
                actualReason = hint,
            )
        }
        return null
    }
}
