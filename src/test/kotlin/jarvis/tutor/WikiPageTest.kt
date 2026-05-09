package jarvis.tutor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiPageTest {

    private val now = Instant.parse("2026-05-09T10:00:00Z")

    @Test
    fun slugLowercaseDashHandling() {
        assertEquals("greedy-algorithm", WikiPage.slug("Greedy algorithm"))
        assertEquals("o-n-log-n", WikiPage.slug("O(n log n)"))
        assertEquals("c", WikiPage.slug("C++"))
        // Empty / pure-symbol input falls back to sha8 hex.
        assertEquals(8, WikiPage.slug("@@@@").length)
    }

    @Test
    fun appendCreatesPageWithFrontmatter(@TempDir tmp: Path) {
        WikiPage.append("PA", "Greedy algorithm", "Brief", "picks local optimum each step",
            archivalRoot = tmp, now = now)
        val text = WikiPage.read("PA", "Greedy algorithm", archivalRoot = tmp)
        assertNotNull(text)
        assertTrue(text!!.contains("concept: Greedy algorithm"))
        assertTrue(text.contains("subject: PA"))
        assertTrue(text.contains("touch_count: 1"))
        assertTrue(text.contains("- 2026-05-09T10:00:00: picks local optimum each step"))
    }

    @Test
    fun appendCreatesNewSectionWhenMissing(@TempDir tmp: Path) {
        WikiPage.append("PA", "Greedy algorithm", "Brief", "first bullet",
            archivalRoot = tmp, now = now)
        WikiPage.append("PA", "Greedy algorithm", "Confusions", "user confused with DP",
            archivalRoot = tmp, now = now.plusSeconds(60))
        val text = WikiPage.read("PA", "Greedy algorithm", archivalRoot = tmp)!!
        assertTrue(text.contains("## Brief"))
        assertTrue(text.contains("## Confusions"))
        assertTrue(text.contains("user confused with DP"))
    }

    @Test
    fun touchCountIncrements(@TempDir tmp: Path) {
        repeat(5) { i ->
            WikiPage.append("PA", "Greedy algorithm", "Brief", "bullet $i",
                archivalRoot = tmp, now = now.plusSeconds(i.toLong()))
        }
        val text = WikiPage.read("PA", "Greedy algorithm", archivalRoot = tmp)!!
        assertTrue(text.contains("touch_count: 5"), "expected touch_count: 5; text: $text")
    }

    @Test
    fun lastUpdatedRefreshes(@TempDir tmp: Path) {
        WikiPage.append("PA", "Greedy algorithm", "Brief", "first",
            archivalRoot = tmp, now = now)
        val later = now.plusSeconds(3600)
        WikiPage.append("PA", "Greedy algorithm", "Brief", "second",
            archivalRoot = tmp, now = later)
        val text = WikiPage.read("PA", "Greedy algorithm", archivalRoot = tmp)!!
        assertTrue(text.contains("last_updated: ${later}"), text)
    }

    @Test
    fun piiBulletDropped(@TempDir tmp: Path) {
        val ok = WikiPage.append("PA", "Greedy algorithm", "Brief",
            "user matricol 31091001031ROSL25100",
            archivalRoot = tmp, now = now)
        assertEquals(false, ok, "PII bullet must be rejected")
        // Page may or may not exist yet (first call rejected); read returns null.
        val text = WikiPage.read("PA", "Greedy algorithm", archivalRoot = tmp)
        assertNull(text)
    }

    @Test
    fun emptyBulletRejected(@TempDir tmp: Path) {
        assertEquals(false, WikiPage.append("PA", "X", "Brief", "   ",
            archivalRoot = tmp, now = now))
    }

    @Test
    fun listEnumeratesAllPages(@TempDir tmp: Path) {
        WikiPage.append("PA", "Greedy algorithm", "Brief", "x", archivalRoot = tmp, now = now)
        WikiPage.append("PA", "Dynamic programming", "Brief", "y", archivalRoot = tmp, now = now)
        WikiPage.append("PS", "Markov chains", "Brief", "z", archivalRoot = tmp, now = now)
        val pages = WikiPage.list(archivalRoot = tmp)
        val ids = pages.map { "${it.first}/${it.second}" }.toSet()
        assertEquals(3, ids.size)
        assertTrue("PA/greedy algorithm" in ids, ids.toString())
        assertTrue("PS/markov chains" in ids)
    }

    @Test
    fun readReturnsNullWhenMissing(@TempDir tmp: Path) {
        assertNull(WikiPage.read("PA", "Never written", archivalRoot = tmp))
    }

    @Test
    fun longBulletTruncated(@TempDir tmp: Path) {
        val long = "x".repeat(800)
        WikiPage.append("PA", "X", "Brief", long, archivalRoot = tmp, now = now)
        val text = WikiPage.read("PA", "X", archivalRoot = tmp)!!
        // 400-char cap → bullet line ≤ ~430 chars (timestamp + dash + colon + body)
        val bulletLine = text.lineSequence().first { it.startsWith("- 2026-") }
        assertTrue(bulletLine.length <= 440, "bullet length ${bulletLine.length} > 440")
    }
}
