package jarvis.tutor.taskdetect

import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseScraperTest {
    @Test
    fun `ManualEntryScraper discover returns empty list`() = runBlocking {
        val s = ManualEntryScraper()
        assertEquals("manual", s.sourceId)
        assertTrue(s.discover().isEmpty())
    }

    @Test
    fun `DetectedCourseItem holds the spec-required fields`() {
        val now = Instant.now()
        val item = DetectedCourseItem(
            sourceId = "uaic-fii-pa-2025",
            externalId = "tema-5",
            subject = "PA",
            title = "Tema 5",
            deadline = now.plusSeconds(86400),
            sourceUrl = "https://example.com/pa/tema-5",
            kind = "homework",
        )
        assertEquals("uaic-fii-pa-2025", item.sourceId)
        assertEquals("tema-5", item.externalId)
        assertEquals("PA", item.subject)
        assertEquals("Tema 5", item.title)
        assertEquals("homework", item.kind)
    }

    @Test
    fun `custom sourceId on ManualEntryScraper`() = runBlocking {
        val s = ManualEntryScraper(sourceId = "user-custom-source")
        assertEquals("user-custom-source", s.sourceId)
        assertTrue(s.discover().isEmpty())
    }
}
