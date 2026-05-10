package jarvis.tutor.taskdetect

import java.time.Instant

/**
 * Phase 5.3 stub. Per-faculty / per-subject scrapers will implement
 * this; Phase 6 [TaskDetector] aggregates results across all
 * registered scrapers + dedups by externalId.
 *
 * Each scraper is responsible for one source (one course page, one
 * VLE site, one calendar). Returning an empty list is fine when
 * nothing is due.
 */
interface CourseScraper {
    /** Stable id for the scraper itself (e.g. "uaic-fii-pa-2025"). */
    val sourceId: String

    /**
     * Fetch the source + parse out current homework / lecture-due /
     * exam announcements. Idempotent. Best-effort: a single source
     * failing should not crash the crawl run; throw and the orchestrator
     * logs + skips.
     */
    suspend fun discover(): List<DetectedCourseItem>
}

/**
 * What a scraper returns. Phase 6 [TaskDetector] turns this into a
 * persisted Task row, dedupping by (sourceId, externalId).
 */
data class DetectedCourseItem(
    val sourceId: String,
    /** Stable id within source (URL, ICS UID, etc) for dedup. */
    val externalId: String,
    val subject: String,
    val title: String,
    val deadline: Instant,
    /** Optional raw URL or path the user can visit. */
    val sourceUrl: String? = null,
    /** Optional kind: "homework" | "exam" | "reading" | etc. Free-form. */
    val kind: String? = null,
)

/**
 * No-op placeholder so wiring code in Phase 6 + later phases can
 * reference [CourseScraper] without forcing a real scraper to exist
 * for every subject yet. Returns empty list always.
 *
 * Real scrapers (`UaicFiiPaScraper`, `UaicFiiPsScraper`, etc) land
 * in Phase 6 once the user provides the URLs in
 * `JARVIS_COURSE_PAGE_<SUBJECT>` env vars.
 */
class ManualEntryScraper(override val sourceId: String = "manual") : CourseScraper {
    override suspend fun discover(): List<DetectedCourseItem> = emptyList()
}
