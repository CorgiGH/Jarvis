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
}
