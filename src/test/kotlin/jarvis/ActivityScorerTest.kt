package jarvis

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityScorerTest {

    private val t0: Instant = Instant.parse("2026-05-08T10:00:00Z")
    private fun ts(offsetMin: Long): String = t0.plus(Duration.ofMinutes(offsetMin)).toString()
    private fun e(offsetMin: Long, process: String, title: String = "") =
        ActivityEntry(ts = ts(offsetMin), title = title, process = process, pid = 1L)

    @Test
    fun ideOutscoresEntertainment() {
        val ide = ActivityScorer.score(e(0, "code.exe", "main.kt"), emptyList())
        val yt = ActivityScorer.score(
            e(0, "chrome.exe", "lofi hip hop - YouTube"),
            emptyList(),
        )
        assertTrue(ide > yt, "IDE ($ide) should outscore YouTube ($yt)")
    }

    @Test
    fun stackOverflowOutscoresGenericBrowserTab() {
        val so = ActivityScorer.score(
            e(0, "chrome.exe", "Kotlin sealed class - Stack Overflow"),
            emptyList(),
        )
        val random = ActivityScorer.score(
            e(0, "chrome.exe", "Some random page about cats"),
            emptyList(),
        )
        assertTrue(so > random, "SO ($so) should outscore random tab ($random)")
    }

    @Test
    fun terminalScoresHigh() {
        val term = ActivityScorer.score(e(0, "wt.exe", "PowerShell - gradle test"), emptyList())
        assertTrue(term >= 0.7f, "terminal base should be ≥0.7, got $term")
    }

    @Test
    fun socialMediaScoresVeryLow() {
        val twitter = ActivityScorer.score(
            e(0, "chrome.exe", "Home / X"),
            emptyList(),
        )
        val tiktok = ActivityScorer.score(
            e(0, "chrome.exe", "TikTok - Make Your Day"),
            emptyList(),
        )
        assertTrue(twitter < 0.1f, "twitter ($twitter) should be <0.1")
        assertTrue(tiktok < 0.1f, "tiktok ($tiktok) should be <0.1")
    }

    @Test
    fun errorKeywordBoostsScore() {
        val plain = ActivityScorer.score(e(0, "code.exe", "main.kt"), emptyList())
        val err = ActivityScorer.score(
            e(0, "code.exe", "main.kt — NullPointerException at L42"),
            emptyList(),
        )
        assertTrue(err > plain, "error keyword should boost ($err > $plain)")
    }

    @Test
    fun keywordRegressionDoesNotMatchSubword() {
        // DM5: "fail" must NOT match inside "failsafe", "bug" must NOT match
        // inside "debug". CamelCase Exception/Error suffix DOES still match.
        val nope = ActivityScorer.score(e(0, "code.exe", "failsafe debug routine"), emptyList())
        val plain = ActivityScorer.score(e(0, "code.exe", "main.kt"), emptyList())
        assertEquals(plain, nope, "subword false-positives are gone (got $nope vs $plain)")
        // CamelCase exception must still match.
        val withExcept = ActivityScorer.score(
            e(0, "code.exe", "main.kt — IllegalStateException"),
            emptyList(),
        )
        assertTrue(withExcept > plain, "CamelCase Exception suffix still boosts ($withExcept > $plain)")
    }

    @Test
    fun keywordBonusCappedAt0_2() {
        // Six trigger words in title; bonus must not exceed 0.2.
        val many = ActivityScorer.score(
            e(0, "code.exe", "error error error TODO TODO TODO bug exception fail crash"),
            emptyList(),
        )
        // Base 0.7 + cap 0.2 = 0.9. Without cap would be > 0.9.
        assertTrue(many <= 0.9f + 1e-4, "kw bonus must cap at 0.2 (got total $many)")
    }

    @Test
    fun continuityBonusKicksInAfter10Min() {
        // 11 minutes of same-proc activity → +0.1 over the bare base.
        val recent = (0L..11L).map { e(it, "code.exe", "main.kt") }
        val current = e(11, "code.exe", "main.kt")
        val withHistory = ActivityScorer.score(current, recent)
        val noHistory = ActivityScorer.score(current, emptyList())
        assertTrue(withHistory > noHistory, "continuity should add bonus ($withHistory > $noHistory)")
    }

    @Test
    fun continuityBonusGrowsAt30Min() {
        val short = (0L..11L).map { e(it, "code.exe", "main.kt") }
        val long = (0L..31L).map { e(it, "code.exe", "main.kt") }
        val sShort = ActivityScorer.score(e(11, "code.exe", "main.kt"), short)
        val sLong = ActivityScorer.score(e(31, "code.exe", "main.kt"), long)
        assertTrue(sLong > sShort, "30-min run ($sLong) should outscore 11-min run ($sShort)")
    }

    @Test
    fun continuityResetsOnProcessSwitch() {
        // Long IDE block, then a single browser sample, then back to IDE.
        // Continuity for the new IDE entry should be ~0 since the immediately
        // previous entry was a different process.
        val recent = (0L..30L).map { e(it, "code.exe") } +
            e(31, "chrome.exe", "x.com") +
            e(32, "code.exe")
        val current = e(33, "code.exe")
        val with = ActivityScorer.score(current, recent)
        // Should be ≈ base 0.7 (no continuity yet because the run just restarted).
        assertTrue(
            with < 0.85f,
            "process switch should reset continuity bonus (got $with)",
        )
    }

    @Test
    fun distractionPenaltyAppliesOnHighChurn() {
        // 7 distinct procs in the last 5 min → −0.2.
        val procs = listOf("a.exe", "b.exe", "c.exe", "d.exe", "e.exe", "f.exe", "g.exe")
        val recent = procs.mapIndexed { i, p -> e(i.toLong(), p) }
        val current = e(6, "code.exe")
        val churned = ActivityScorer.score(current, recent)
        val calm = ActivityScorer.score(current, emptyList())
        assertTrue(churned < calm, "high churn should penalize ($churned < $calm)")
    }

    @Test
    fun scoreClampedToZeroOne() {
        val zero = ActivityScorer.score(e(0, "tiktok.exe", ""), emptyList())
        assertTrue(zero in 0f..1f, "score must be in [0,1], got $zero")
        val maxish = ActivityScorer.score(
            e(60, "code.exe", "error TODO bug crash exception"),
            (0L..60L).map { e(it, "code.exe", "error TODO") },
        )
        assertTrue(maxish in 0f..1f, "score must be in [0,1], got $maxish")
    }

    @Test
    fun nullProcessDoesNotCrash() {
        val s = ActivityScorer.score(
            ActivityEntry(ts = ts(0), title = null, process = null, pid = null),
            emptyList(),
        )
        assertTrue(s in 0f..1f, "score must handle null fields, got $s")
    }

    @Test
    fun expectedOrderingOnSyntheticDay() {
        // Manually-constructed series ordered by intuition — score must agree.
        val cases = listOf(
            "ide_long_focus" to (e(60, "code.exe", "main.kt error") to (0L..60L).map { e(it, "code.exe") }),
            "ide_short" to (e(0, "code.exe", "main.kt") to emptyList()),
            "so_browser" to (e(0, "chrome.exe", "kotlin coroutine cancellation - Stack Overflow") to emptyList<ActivityEntry>()),
            "unknown_browser" to (e(0, "chrome.exe", "weather forecast") to emptyList<ActivityEntry>()),
            "youtube" to (e(0, "chrome.exe", "lofi - YouTube") to emptyList<ActivityEntry>()),
            "twitter" to (e(0, "chrome.exe", "Home / X") to emptyList<ActivityEntry>()),
        )
        val scored = cases.map { (k, c) -> k to ActivityScorer.score(c.first, c.second) }
        // Pull values for clarity.
        val map = scored.toMap()
        assertTrue(map["ide_long_focus"]!! >= map["ide_short"]!!, scored.toString())
        assertTrue(map["ide_short"]!! > map["so_browser"]!!, scored.toString())
        assertTrue(map["so_browser"]!! > map["unknown_browser"]!!, scored.toString())
        assertTrue(map["unknown_browser"]!! > map["youtube"]!!, scored.toString())
        assertTrue(map["youtube"]!! >= map["twitter"]!!, scored.toString())
    }

    @Test
    fun scoreEqualsBaseWhenNoBonusOrPenalty() {
        // Pristine isolated entry — should equal base prior exactly.
        val s = ActivityScorer.score(e(0, "wt.exe", "PowerShell"), emptyList())
        assertEquals(0.7f, s, "terminal isolated → base 0.7")
    }
}
