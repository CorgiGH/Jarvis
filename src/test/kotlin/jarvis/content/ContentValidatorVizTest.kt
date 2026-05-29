package jarvis.content

import kotlin.test.Test
import kotlin.test.assertTrue

class ContentValidatorVizTest {
    private fun kc(id: String, viz: String?, requires: Boolean) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "a", name_en = "a", cluster = "c",
        bloom_level = "understand", difficulty = 1, time_minutes = 1, exam_weight = 0.0,
        tier = 1, viz_id = viz, requires_visual = requires,
    )
    private fun sub(vararg kcs: KnowledgeConcept) = LoadedSubject("PA", kcs.toList(), emptyList(), emptyList())

    @Test fun `requires_visual with unresolvable viz_id is an error`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", "ghost-viz", true)), setOf("recursion-tree"))
        assertTrue(issues.any { it.severity == "error" && it.rule == "viz_reference" })
    }

    @Test fun `requires_visual with null viz_id is an error`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", null, true)), setOf("recursion-tree"))
        assertTrue(issues.any { it.severity == "error" && it.rule == "viz_reference" })
    }

    @Test fun `requires_visual with resolvable viz_id passes`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", "recursion-tree", true)), setOf("recursion-tree"))
        assertTrue(issues.isEmpty())
    }

    @Test fun `non-visual kc with null viz_id passes`() {
        val issues = ContentValidator.checkVizReferences(sub(kc("k1", null, false)), setOf("recursion-tree"))
        assertTrue(issues.isEmpty())
    }
}
