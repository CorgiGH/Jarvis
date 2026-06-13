package jarvis.content

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-3 Task 7 (spec §5.5 / INV-5.5) + Plan 4b Task 5 extension:
 *
 * Every figure binding in a served beat reveal must:
 *   (a) resolve to a known viz instance id (instance-existence leg), AND
 *   (b) have `binding.family_id == instance.family_id` (family-match leg — Plan 4b fold).
 *
 * Plan 4b also folds [ContentValidator.checkFigureBindings] into [ContentValidator.validate],
 * so tests (c)/(d) below assert the fold is live: dangling binding and family_id mismatch both
 * red `validate()` itself (not just the standalone check).
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

    // ── Standalone checkFigureBindings tests (original + updated to new Map signature) ──

    @Test fun `a KC with no figure binding is vacuously valid`() {
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", null))) { emptyMap() }
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `a figure binding to an existing instance with matching family passes`() {
        val fb = FigureBinding(family_id = "graph-tree", instance_id = "viz-pa-mergesort-001")
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", fb))) {
            mapOf("viz-pa-mergesort-001" to "graph-tree")
        }
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `a dangling instance_id is an error naming the KC and the missing instance`() {
        val fb = FigureBinding(family_id = "graph-tree", instance_id = "viz-does-not-exist")
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", fb))) {
            mapOf("viz-pa-mergesort-001" to "graph-tree")
        }
        assertEquals(1, issues.size)
        val it = issues.single()
        assertEquals("error", it.severity)
        assertEquals("figure_binding", it.rule)
        assertTrue(it.detail.contains("k1"), "names the KC: ${it.detail}")
        assertTrue(it.detail.contains("viz-does-not-exist"), "names the missing instance: ${it.detail}")
    }

    @Test fun `a figure binding with mismatched family_id on a real instance is an error (INV-5-5 family leg)`() {
        // The instance exists but the binding says the wrong family_id (e.g. a typo or stale copy).
        // FigureReveal dispatches on the BINDING's family_id → registry-miss → silent text fallback.
        val fb = FigureBinding(family_id = "sequence-array", instance_id = "viz-pa-mergesort-001")
        val issues = ContentValidator.checkFigureBindings(loaded(kcWithFigure("k1", fb))) {
            mapOf("viz-pa-mergesort-001" to "graph-tree") // real family_id is graph-tree
        }
        assertEquals(1, issues.size)
        val it = issues.single()
        assertEquals("error", it.severity)
        assertEquals("figure_binding", it.rule)
        assertTrue(it.detail.contains("k1"), "names the KC: ${it.detail}")
        assertTrue(it.detail.contains("sequence-array"), "names the binding's wrong family: ${it.detail}")
        assertTrue(it.detail.contains("graph-tree"), "names the instance's real family: ${it.detail}")
        assertTrue(it.detail.contains("viz-pa-mergesort-001"), "names the instance: ${it.detail}")
    }

    // ── validate() fold: the two INV-5.5 halves now red validate() itself ──

    @Test fun `dangling binding now reds validate() itself (fold check)`() {
        val fb = FigureBinding(family_id = "graph-tree", instance_id = "viz-does-not-exist")
        val sub = loaded(kcWithFigure("k1", fb))
        val report = ContentValidator.validate(
            subjects = listOf(sub),
            knownInstances = { mapOf("viz-pa-mergesort-001" to "graph-tree") },
            sourceText = { null },
        )
        assertFalse(report.ok, "validate() must be !ok when a binding references a missing instance")
        assertTrue(
            report.issues.any { it.severity == "error" && it.rule == "figure_binding" },
            "expected figure_binding error in issues: ${report.issues}",
        )
    }

    @Test fun `family_id mismatch on a real instance now reds validate() itself (INV-5-5 family leg in fold)`() {
        val fb = FigureBinding(family_id = "sequence-array", instance_id = "viz-pa-mergesort-001")
        val sub = loaded(kcWithFigure("k1", fb))
        val report = ContentValidator.validate(
            subjects = listOf(sub),
            knownInstances = { mapOf("viz-pa-mergesort-001" to "graph-tree") },
            sourceText = { null },
        )
        assertFalse(report.ok, "validate() must be !ok when binding.family_id mismatches instance.family_id")
        assertTrue(
            report.issues.any { it.severity == "error" && it.rule == "figure_binding" },
            "expected figure_binding error in issues: ${report.issues}",
        )
    }

    @Test fun `the 4 authored PA beat sets resolve on the real corpus (vacuous-green, all figure-null)`() {
        val repo = ContentRepo(Path.of("content"))
        val sub = repo.loadSubject("PA")
        // Build the real id→family_id map.
        val knownInstances = repo.loadVizInstances("PA").associate { it.id to it.family_id }
        val issues = ContentValidator.checkFigureBindings(sub) { knownInstances }
        // pa-kc-001/003/004 have figure=null → vacuous-green.
        // pa-kc-002 has figure bound to viz-pa-mergesort-001 (graph-tree) → must also pass after Task 5.
        assertTrue(issues.isEmpty(), "Real corpus should produce zero figure_binding issues: $issues")
    }
}
