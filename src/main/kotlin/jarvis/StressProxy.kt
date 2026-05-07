package jarvis

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phase 4.3 — heuristic stress score 0..1 from activity:
 *   churn(30min) + late-hour flag + social-scrolling share.
 * Cheap, no LLM call. Read by ctx-model alongside sleep.
 */
@Serializable
data class StressLevel(
    val score: Float,
    val reasons: List<String>,
)

object StressProxy {

    private val WINDOW = Duration.ofMinutes(30)
    private val LATE_HOURS = setOf(22, 23, 0, 1, 2, 3)
    private val BROWSER_PROCS = setOf(
        "chrome.exe", "firefox.exe", "msedge.exe", "brave.exe",
        "opera.exe", "vivaldi.exe", "arc.exe",
    )
    /** Title matchers — mirrors ActivityScorer's social-media detection.
     *  Includes the awkward "X (formerly Twitter)" page-title shapes. */
    private val SCROLLING_TITLE_HINTS = listOf(
        "twitter", "x.com", "reddit", "instagram", "tiktok",
        "facebook", "youtube shorts", " / x", " on x", "(@",
    )

    fun current(
        activity: List<ActivityEntry>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StressLevel {
        if (activity.isEmpty()) return StressLevel(0f, emptyList())
        val cutoff = now.minus(WINDOW)
        val window = activity.mapNotNull { e ->
            val ts = parseInstantOrNull(e.ts) ?: return@mapNotNull null
            if (ts < cutoff) null else e
        }
        val reasons = mutableListOf<String>()
        var score = 0f

        if (window.isNotEmpty()) {
            val distinctProcs = window.map { it.process ?: "?" }.toSet().size
            val churn = (distinctProcs / 30.0f).coerceIn(0f, 1f)
            if (churn > 0.1f) {
                score += churn
                reasons += "churn=$distinctProcs distinct procs in 30m"
            }
        }

        val nowHour = LocalDateTime.ofInstant(now, zone).hour
        if (nowHour in LATE_HOURS) {
            score += 0.3f
            reasons += "late-hour ($nowHour local)"
        }

        if (window.isNotEmpty()) {
            val scrolling = window.count { e ->
                val proc = e.process?.lowercase().orEmpty()
                val title = e.title?.lowercase().orEmpty()
                proc in BROWSER_PROCS && SCROLLING_TITLE_HINTS.any { title.contains(it) }
            }
            val share = scrolling.toFloat() / window.size.toFloat()
            if (share > 0.05f) {
                score += share * 0.4f
                reasons += "scrolling share=${"%.2f".format(share)}"
            }
        }

        return StressLevel(score.coerceIn(0f, 1f), reasons)
    }

    private fun parseInstantOrNull(s: String?): Instant? =
        if (s.isNullOrEmpty()) null else runCatching { Instant.parse(s) }.getOrNull()
}
