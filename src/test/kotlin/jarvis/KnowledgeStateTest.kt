package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnowledgeStateTest {

    private val now: Instant = Instant.parse("2026-05-08T12:00:00Z")

    @Test
    fun touchAndReadBack(@TempDir tmp: Path) {
        val file = tmp.resolve("knowledge.jsonl")
        KnowledgeState.touchTo(file, "Markov chains", "Probability", "lecture", 1.0f, now)
        val all = KnowledgeState.readAllFrom(file)
        assertEquals(1, all.size)
        assertEquals("Markov chains", all[0].concept)
        assertEquals("Probability", all[0].subject)
    }

    @Test
    fun freshTouchHasHighConfidence(@TempDir tmp: Path) {
        val file = tmp.resolve("knowledge.jsonl")
        KnowledgeState.touchTo(file, "Markov", "Prob", "lecture", 1.0f, now)
        val stats = KnowledgeState.statsFrom(file, now)
        assertEquals(1, stats.size)
        assertTrue(stats[0].confidence > 0.99f, "fresh touch confidence ≈ 1 (got ${stats[0].confidence})")
        assertEquals(0L, stats[0].staleDays)
    }

    @Test
    fun confidenceDecaysOverTime(@TempDir tmp: Path) {
        val file = tmp.resolve("knowledge.jsonl")
        KnowledgeState.touchTo(file, "Markov", "Prob", "lecture", 1.0f, now)
        // 7 days later: half-life → confidence ~0.5
        val later = now.plus(Duration.ofDays(7))
        val stats = KnowledgeState.statsFrom(file, later)
        assertTrue(stats[0].confidence in 0.45f..0.55f,
            "confidence half-life 7d (got ${stats[0].confidence})")
        assertEquals(7L, stats[0].staleDays)
    }

    @Test
    fun multipleTouchesAccumulate(@TempDir tmp: Path) {
        val file = tmp.resolve("knowledge.jsonl")
        repeat(3) { KnowledgeState.touchTo(file, "Markov", "Prob", "lecture", 1.0f,
            now.minus(Duration.ofMinutes(it.toLong()))) }
        val stats = KnowledgeState.statsFrom(file, now)
        assertTrue(stats[0].confidence > 2.5f, "3 touches accumulate")
    }

    @Test
    fun staleConceptsFiltered(@TempDir tmp: Path) {
        val file = tmp.resolve("knowledge.jsonl")
        // Fresh concept — stays out of staleConcepts.
        KnowledgeState.touchTo(file, "Recent", "S", "lecture", 1.0f, now)
        // Old concept — past staleness threshold + low confidence.
        KnowledgeState.touchTo(file, "Stale", "S", "lecture", 1.0f,
            now.minus(Duration.ofDays(14)))
        // Hack: invoke stats with `now` to compute decay against fixture.
        val statsNow = KnowledgeState.statsFrom(file, now)
        val stale = statsNow.filter { it.confidence < 0.3f && it.staleDays >= 3L }
        assertTrue(stale.any { it.concept == "Stale" })
        assertTrue(stale.none { it.concept == "Recent" })
    }

    @Test
    fun missingFileReturnsEmpty(@TempDir tmp: Path) {
        val stats = KnowledgeState.statsFrom(tmp.resolve("nope.jsonl"), now)
        assertEquals(emptyList(), stats)
    }

    @Test
    fun catalogExtractsConceptsFromArchivalH2() {
        val tmp = Files.createTempDirectory("archival")
        try {
            val sub = tmp.resolve("Probability")
            Files.createDirectories(sub)
            Files.writeString(sub.resolve("notes.md"), """
                # File title
                ## Markov chains
                Some text.
                ## Bayes theorem
                More text.
                ### Sub-section (h3 ignored)
            """.trimIndent())
            ConceptCatalog.invalidate()
            val concepts = ConceptCatalog.all(tmp)
            val names = concepts.map { it.name }.toSet()
            assertTrue("Markov chains" in names)
            assertTrue("Bayes theorem" in names)
            assertTrue("Sub-section (h3 ignored)" !in names, "h3 not extracted")
            assertTrue(concepts.all { it.subject == "Probability" })
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun catalogSkipsTestsDirectory() {
        val tmp = Files.createTempDirectory("archival")
        try {
            val tests = tmp.resolve("Probability/tests")
            Files.createDirectories(tests)
            Files.writeString(tests.resolve("exam-2024.md"), "## Old exam concept\n")
            val sub = tmp.resolve("Probability/concepts")
            Files.createDirectories(sub)
            Files.writeString(sub.resolve("notes.md"), "## Real concept\n")
            ConceptCatalog.invalidate()
            val concepts = ConceptCatalog.all(tmp)
            val names = concepts.map { it.name }.toSet()
            assertTrue("Real concept" in names)
            assertTrue("Old exam concept" !in names, "tests/ skipped")
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun matchInTextFindsConceptByLowercaseSubstring() {
        val tmp = Files.createTempDirectory("archival")
        try {
            val sub = tmp.resolve("Probability")
            Files.createDirectories(sub)
            Files.writeString(sub.resolve("notes.md"), "## Markov chains\n## Bayes theorem\n")
            ConceptCatalog.invalidate()
            val matches = ConceptCatalog.matchInText("studying markov chains today", tmp)
            assertTrue(matches.any { it.name == "Markov chains" })
            assertTrue(matches.none { it.name == "Bayes theorem" })
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }
}
