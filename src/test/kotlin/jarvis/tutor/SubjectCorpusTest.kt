package jarvis.tutor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubjectCorpusTest {

    private fun seed(tmp: Path) {
        val pa = tmp.resolve("_extras/PA"); pa.createDirectories()
        pa.resolve("study_guide/tests").createDirectories()
        pa.resolve("study_guide/tests/partial-2017-a.md").writeText("greedy algorithm activity selection")
        pa.resolve("study_guide/tests/exam-2014-a.md").writeText("dynamic programming knapsack")
        pa.resolve("hw").createDirectories()
        pa.resolve("hw/pa_t1.md").writeText("Tema 1: greedy practice problem")
        pa.resolve("courses").createDirectories()
        pa.resolve("courses/pa_c01.md").writeText("Lecture 1: introduction to algorithm design")

        val so = tmp.resolve("_extras/SO"); so.createDirectories()
        so.resolve("study_guide_curated/tests/source/exam-2021").createDirectories()
        so.resolve("study_guide_curated/tests/source/exam-2021/SO T2 1.md").writeText("scheduling round-robin")

        val ps = tmp.resolve("_extras/PS"); ps.createDirectories()
        ps.resolve("courses").createDirectories()
        ps.resolve("courses/ps1.md").writeText("Markov chains memoryless property")
    }

    @Test
    fun resolvesSubjectAliases() {
        assertEquals("PA", SubjectCorpus.resolveSubject("PA"))
        assertEquals("PA", SubjectCorpus.resolveSubject("pa"))
        assertEquals("SO", SubjectCorpus.resolveSubject("SO&RC"))
        assertEquals("SO", SubjectCorpus.resolveSubject("OS"))
        assertEquals(null, SubjectCorpus.resolveSubject("NotASubject"))
    }

    @Test
    fun searchFindsExamFile(@TempDir tmp: Path) {
        seed(tmp)
        val hits = SubjectCorpus.search("PA", "greedy", k = 5, archivalRoot = tmp)
        assertTrue(hits.isNotEmpty())
        // partial-2017-a.md mentions "greedy algorithm" → should rank.
        assertTrue(hits.any { it.relPath.contains("partial-2017-a.md") })
    }

    @Test
    fun filenameMatchScoresHigh(@TempDir tmp: Path) {
        seed(tmp)
        // Filename "exam-2014-a.md" contains "exam" → big bonus.
        val hits = SubjectCorpus.search("PA", "exam", k = 5, archivalRoot = tmp)
        assertTrue(hits.first().relPath.contains("exam-2014-a"))
    }

    @Test
    fun kindFilterRestrictsResults(@TempDir tmp: Path) {
        seed(tmp)
        val all = SubjectCorpus.search("PA", "greedy", k = 10, archivalRoot = tmp)
        val testsOnly = SubjectCorpus.search("PA", "greedy", kindFilter = "tests", k = 10, archivalRoot = tmp)
        assertTrue(all.size > testsOnly.size)
        assertTrue(testsOnly.all { it.kind == "tests" })
    }

    @Test
    fun missingSubjectReturnsEmpty(@TempDir tmp: Path) {
        seed(tmp)
        assertEquals(emptyList(), SubjectCorpus.search("ALO", "anything", archivalRoot = tmp))
    }

    @Test
    fun emptyQueryReturnsEmpty(@TempDir tmp: Path) {
        seed(tmp)
        assertEquals(emptyList(), SubjectCorpus.search("PA", "", archivalRoot = tmp))
    }

    @Test
    fun listKindsEnumeratesAvailable(@TempDir tmp: Path) {
        seed(tmp)
        val pa = SubjectCorpus.listKinds("PA", tmp)
        assertTrue("courses" in pa, pa.toString())
        assertTrue("hw" in pa, pa.toString())
        // tests live under study_guide; expressed as "study_guide/tests"
        assertTrue("study_guide" in pa, pa.toString())
        assertTrue("study_guide/tests" in pa, pa.toString())
    }

    @Test
    fun listKindsHandlesCuratedTests(@TempDir tmp: Path) {
        seed(tmp)
        val so = SubjectCorpus.listKinds("SO", tmp)
        assertTrue("study_guide_curated/tests" in so, so.toString())
    }

    @Test
    fun snippetIncludesSurroundingContext(@TempDir tmp: Path) {
        seed(tmp)
        val hits = SubjectCorpus.search("PS", "memoryless", k = 1, archivalRoot = tmp)
        assertTrue(hits[0].snippet.contains("Markov"), hits[0].snippet)
    }

    @Test
    fun infersKindFromPath() {
        assertEquals("tests", SubjectCorpus.inferKind("_extras/PA/study_guide/tests/x.md"))
        assertEquals("courses", SubjectCorpus.inferKind("_extras/PS/courses/ps1.md"))
        assertEquals("hw", SubjectCorpus.inferKind("_extras/ALO/hw/alo_t1.md"))
        assertEquals(null, SubjectCorpus.inferKind("_extras/PA/random/x.md"))
    }
}
