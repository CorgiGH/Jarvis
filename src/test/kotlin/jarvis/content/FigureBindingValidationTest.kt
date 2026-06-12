package jarvis.content

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-3 Task 7 (spec §5.5 / INV-5.5) — every figure binding in a served beat reveal must resolve to
 * an existing viz instance row. Additive author/audit check: vacuous-GREEN for the 4 Task-7 beat sets
 * (they bind NO figure, locked decision §0.6 #1), loud-RED on a dangling family_id/instance_id. The
 * family REGISTRY check (family_id registered) lands with the family registry in Task 8; this check
 * covers the instance-existence half (instance_id resolves to a content/{subject}/viz row).
 */
class FigureBindingValidationTest {

    private fun kcWithFigure(id: String, fb: FigureBinding?) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "ro", name_en = "en", cluster = "c",
        bloom_level = "understand", difficulty = 1, time_minutes = 10, exam_weight = 0.0,
        tier = 1, source = emptyList(), version = 1, concept_type = "definition-taxonomy",
        beats = mapOf(
            "ro" to KcBeats(
                reveal = BeatReveal(steps = listOf(RevealStep("t", "c")), figure = fb),
            ),
        ),
    )

    private fun loaded(vararg kcs: KnowledgeConcept) =
        LoadedSubject("PA", kcs = kcs.toList(), edges = emptyList(), misconceptions = emptyList())

    @Test fun `a KC with no figure binding is vacuously valid`() {
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", null))) { emptySet() }
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `a figure binding to an existing instance passes`() {
        val fb = FigureBinding(family_id = "graph-tree", instance_id = "viz-pa-mergesort-001")
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", fb))) {
            setOf("viz-pa-mergesort-001")
        }
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `a dangling instance_id is an error naming the KC and the missing instance`() {
        val fb = FigureBinding(family_id = "graph-tree", instance_id = "viz-does-not-exist")
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", fb))) {
            setOf("viz-pa-mergesort-001")
        }
        assertEquals(1, issues.size)
        val issue = issues.single()
        assertEquals("error", issue.severity)
        assertEquals("figure_binding", issue.rule)
        assertTrue(issue.detail.contains("k1"), "names the KC: ${issue.detail}")
        assertTrue(issue.detail.contains("viz-does-not-exist"), "names the missing instance: ${issue.detail}")
    }

    @Test fun `the 4 authored PA beat sets resolve (vacuous-green on the real corpus)`() {
        val repo = ContentRepo(Path.of("content"))
        val sub = repo.loadSubject("PA")
        // The real viz instance ids for PA (empty today; the MergeSort instance lands in Task 8).
        val knownInstances = repo.loadVizInstances("PA").map { it.id }.toSet()
        val issues = ContentValidator.checkFigureBindings(sub) { knownInstances }
        assertTrue(issues.isEmpty(), "Task-7 beat sets bind no figure → must be vacuous-green: $issues")
    }
}
