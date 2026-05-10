package jarvis.tutor.taskdetect

import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcsScraperTest {

    private val sampleIcs = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//course//EN
        BEGIN:VEVENT
        UID:tema-5-2026@courses.uaic.ro
        SUMMARY:PA Tema 5 - greedy algorithms
        DTSTART:20260515T180000Z
        DTEND:20260515T200000Z
        DESCRIPTION:Submit on the platform
        END:VEVENT
        BEGIN:VEVENT
        UID:exam-final-2026
        SUMMARY:PA Final Exam
        DTSTART;VALUE=DATE:20260620
        DTEND;VALUE=DATE:20260620
        END:VEVENT
        BEGIN:VEVENT
        UID:no-date-event
        SUMMARY:Should be skipped
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `parseVevents extracts UID + SUMMARY + DTSTART per VEVENT block`() {
        val events = IcsScraper.parseVevents(sampleIcs)
        assertEquals(3, events.size)
        assertEquals("tema-5-2026@courses.uaic.ro", events[0]["UID"])
        assertEquals("PA Tema 5 - greedy algorithms", events[0]["SUMMARY"])
        assertEquals("20260515T180000Z", events[0]["DTSTART"])
        assertEquals("20260620", events[1]["DTSTART"])
    }

    @Test
    fun `parseIcsInstant handles UTC timestamp + date-only + invalid`() {
        val utc = IcsScraper.parseIcsInstant("20260515T180000Z")
        assertNotNull(utc)
        assertEquals(Instant.parse("2026-05-15T18:00:00Z"), utc)

        val dateOnly = IcsScraper.parseIcsInstant("20260620")
        assertNotNull(dateOnly)
        // DATE values yield end-of-day in UTC so deadline ranking doesn't collapse same-day rows.
        assertEquals(Instant.parse("2026-06-20T23:59:00Z"), dateOnly)

        assertNull(IcsScraper.parseIcsInstant("not-a-date"))
    }

    @Test
    fun `discover yields one DetectedTask per parseable VEVENT`() = runBlocking {
        val scraper = IcsScraper(subject = "PA", feedUrl = "memory://test") { sampleIcs }
        val tasks = scraper.discover()
        assertEquals(2, tasks.size, "skips event with no DTSTART/DTEND")
        val tema5 = tasks.first { it.externalId == "tema-5-2026@courses.uaic.ro" }
        assertEquals("PA", tema5.subject)
        assertEquals("PA Tema 5 - greedy algorithms", tema5.title)
        assertEquals(Instant.parse("2026-05-15T20:00:00Z"), tema5.deadline)
        assertEquals("ics:pa", tema5.sourceId)
        assertEquals("calendar", tema5.rawMetadata["kind"])
        assertEquals("memory://test", tema5.sourceUrl)
    }

    @Test
    fun `discover returns empty list when feed unreachable`() = runBlocking {
        val scraper = IcsScraper(subject = "X", feedUrl = "memory://offline") { null }
        assertTrue(scraper.discover().isEmpty())
    }

    @Test
    fun `fromEnv builds one scraper per JARVIS_COURSE_ICS_ key`() {
        val env = mapOf(
            "JARVIS_COURSE_ICS_PA" to "https://example.com/pa.ics",
            "JARVIS_COURSE_ICS_LFA" to "https://example.com/lfa.ics",
            "JARVIS_COURSE_ICS_BLANK" to "  ",
            "OTHER" to "ignored",
        )
        val scrapers = IcsScraper.fromEnv(env)
        assertEquals(2, scrapers.size)
        val ids = scrapers.map { it.sourceId }.toSet()
        assertTrue("ics:pa" in ids)
        assertTrue("ics:lfa" in ids)
    }

    @Test
    fun `discover unescapes ICS escape sequences in summary`() = runBlocking {
        val ics = """
            BEGIN:VEVENT
            UID:abc
            SUMMARY:Step 1\, then step 2\nFinish
            DTSTART:20260101T000000Z
            END:VEVENT
        """.trimIndent()
        val scraper = IcsScraper(subject = "T", feedUrl = "x") { ics }
        val task = scraper.discover().single()
        assertTrue(task.title.contains(",") && task.title.contains("\n"))
    }

    @Test
    fun `parseVevents handles RFC 5545 line folding`() {
        val folded = """
            BEGIN:VEVENT
            UID:folded-uid
            SUMMARY:A very long
              continuation summary
            DTSTART:20260101T000000Z
            END:VEVENT
        """.trimIndent()
        val ev = IcsScraper.parseVevents(folded).single()
        assertEquals("A very long continuation summary", ev["SUMMARY"])
    }
}
