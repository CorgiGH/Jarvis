package jarvis.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentValidatorTest {
    private fun kc(id: String, tier: Int = 2, weight: Double = 0.0) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "ro-$id", name_en = "en-$id",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = weight, tier = tier, source = emptyList(), version = 1,
    )

    @Test
    fun `clean acyclic graph has no cycle issues`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("a", tier = 1, weight = 1.0), kc("b")),
            edges = listOf(PrereqEdge("b", "a", "r")),
            misconceptions = emptyList(),
        )
        val issues = ContentValidator.detectCycles(sub)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `direct cycle is reported`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("a"), kc("b")),
            edges = listOf(PrereqEdge("a", "b", "r"), PrereqEdge("b", "a", "r")),
            misconceptions = emptyList(),
        )
        val issues = ContentValidator.detectCycles(sub)
        assertEquals(1, issues.size)
        assertEquals("cycle", issues.single().rule)
        assertFalse(issues.single().severity == "warning")
    }

    @Test
    fun `kc within 8 prereq-hops of a tier-1 root is not an orphan`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("root", tier = 1), kc("child")),
            edges = listOf(PrereqEdge("child", "root", "r")),
            misconceptions = emptyList(),
        )
        assertTrue(ContentValidator.detectOrphans(sub).isEmpty())
    }

    @Test
    fun `kc with no path to a tier-1 root is an orphan`() {
        val sub = LoadedSubject(
            "PA",
            kcs = listOf(kc("root", tier = 1), kc("floating", tier = 2)),
            edges = emptyList(),
            misconceptions = emptyList(),
        )
        val issues = ContentValidator.detectOrphans(sub)
        assertEquals(1, issues.size)
        assertEquals("orphan", issues.single().rule)
        assertTrue(issues.single().detail.contains("floating"))
    }

    @Test
    fun `exam weights summing to 1 within tolerance pass`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 0.6), kc("b", weight = 0.41)),
            edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkExamWeights(sub).isEmpty()) // 1.01 within 0.02
    }

    @Test
    fun `exam weights outside tolerance are reported`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 0.5), kc("b", weight = 0.3)),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkExamWeights(sub) // sum 0.8
        assertEquals(1, issues.size)
        assertEquals("exam_weight", issues.single().rule)
    }

    @Test
    fun `kc with both names passes bilingual check`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 1.0, tier = 1)),
            edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkBilingual(sub).isEmpty())
    }

    @Test
    fun `kc missing a romanian name is reported`() {
        val bad = kc("a", weight = 1.0, tier = 1).copy(name_ro = "  ")
        val sub = LoadedSubject("PA", kcs = listOf(bad), edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkBilingual(sub)
        assertEquals(1, issues.size)
        assertEquals("bilingual", issues.single().rule)
    }

    @Test
    fun `misconception missing a label is reported`() {
        val m = Misconception("m1", "a", label_ro = "ok", label_en = "",
            trigger = "t", refutation = "r", source = emptyList(), version = 1)
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 1.0, tier = 1)),
            edges = emptyList(), misconceptions = listOf(m))
        assertEquals(1, ContentValidator.checkBilingual(sub).size)
    }

    private val srcLookup: (String) -> String? = { doc ->
        if (doc == "pa-lecture-01") "An algorithm is a finite\n  sequence of unambiguous steps." else null
    }

    @Test
    fun `quote that is a verbatim substring of the source passes`() {
        val withSrc = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")))
        val sub = LoadedSubject("PA", kcs = listOf(withSrc), edges = emptyList(), misconceptions = emptyList())
        assertTrue(ContentValidator.checkVerbatimSources(sub, srcLookup).isEmpty())
    }

    @Test
    fun `quote not present in the source is an error`() {
        val withSrc = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "an algorithm always halts")))
        val sub = LoadedSubject("PA", kcs = listOf(withSrc), edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertEquals("verbatim_source", issues.single().rule)
        assertEquals("error", issues.single().severity)
    }

    @Test
    fun `empty source list is an error — every KC must be attributed`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 1.0, tier = 1)),
            edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertTrue(issues.single().detail.contains("no source"))
    }

    @Test
    fun `missing source file degrades to a warning, not an error`() {
        val withSrc = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("not-on-disk", "anything")))
        val sub = LoadedSubject("PA", kcs = listOf(withSrc), edges = emptyList(), misconceptions = emptyList())
        val issues = ContentValidator.checkVerbatimSources(sub, srcLookup)
        assertEquals(1, issues.size)
        assertEquals("warning", issues.single().severity)
    }

    @Test
    fun `validate aggregates all checks and self-labels the disclaimer`() {
        val good = kc("a", weight = 1.0, tier = 1)
            .copy(source = listOf(SourceRef("pa-lecture-01", "a finite sequence of unambiguous steps")))
        val sub = LoadedSubject("PA", kcs = listOf(good), edges = emptyList(), misconceptions = emptyList())
        val report = ContentValidator.validate(listOf(sub), srcLookup)
        assertTrue(report.ok, report.issues.toString())
        assertEquals(ContentValidator.DISCLAIMER, report.disclaimer)
    }

    @Test
    fun `validate returns ok=false when any error issue is present`() {
        val sub = LoadedSubject("PA",
            kcs = listOf(kc("a", weight = 0.3, tier = 1)), // weight sum 0.3 -> error
            edges = emptyList(), misconceptions = emptyList())
        val report = ContentValidator.validate(listOf(sub), srcLookup)
        assertFalse(report.ok)
        assertTrue(report.issues.any { it.rule == "exam_weight" })
    }
}
