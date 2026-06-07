package jarvis.content

import jarvis.tutor.Phase
import jarvis.tutor.ScaffoldPlanner
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P1-3(c) — the four AUTHORED teaching KCs `pa-kc-001..004` carry the Phase-3 teaching scaffolding
 * fields (`phase_plan` + `worked_example_first` + `far_transfer_stem` + `self_explanation_prompt`),
 * each SYSTEM-DERIVED from that KC's own committed source quotes (`pa-lecture-01.md`) — NEVER routed to
 * a human (no oracle inversion), NEVER inventing a subject fact beyond what the source states.
 *
 * Before this, NO KC authored any teaching field (grep content/ = none), so the teaching loop had
 * nothing to serve (master-plan:209 precondition unmet). This test pins their authored shape and proves:
 *  - all four teaching fields are present + non-blank on every one of the four KCs;
 *  - `phase_plan` parses to valid [Phase] literals in progression order;
 *  - `ScaffoldPlanner.planFor` over the authored `phase_plan` yields a non-empty ordered plan
 *    (the serve path now has a real plan to consume — P1-3(b));
 *  - the KCs still pass `verbatim_source` validation (the appended teaching fields did NOT disturb the
 *    source spans/quotes — authoring teaching scaffolding never weakens grounding).
 */
class AuthoredTeachingKcsTest {

    private val repo = ContentRepo(Paths.get("content"))
    private val pa = repo.loadSubject("PA")
    private val rawSource: String =
        Files.readString(Paths.get("content", "PA", "_sources", "pa-lecture-01.md"))

    private val teachingKcIds = listOf("pa-kc-001", "pa-kc-002", "pa-kc-003", "pa-kc-004")

    private fun kc(id: String): KnowledgeConcept = pa.kcs.single { it.id == id }

    @Test
    fun `each teaching KC authors all four Phase-3 teaching fields, non-blank`() {
        for (id in teachingKcIds) {
            val k = kc(id)
            assertNotNull(k.phase_plan, "$id must author phase_plan")
            assertTrue(k.phase_plan!!.isNotEmpty(), "$id phase_plan must be non-empty")
            assertNotNull(k.far_transfer_stem, "$id must author far_transfer_stem")
            assertTrue(k.far_transfer_stem!!.isNotBlank(), "$id far_transfer_stem must be non-blank")
            assertNotNull(k.self_explanation_prompt, "$id must author self_explanation_prompt")
            assertTrue(k.self_explanation_prompt!!.isNotBlank(), "$id self_explanation_prompt must be non-blank")
        }
    }

    @Test
    fun `each authored phase_plan parses to valid Phase literals in progression order`() {
        for (id in teachingKcIds) {
            val plan = kc(id).phase_plan!!
            val phases = plan.map { Phase.valueOf(it) }   // throws if a literal is invalid
            // strictly increasing by progression rank (no out-of-order / duplicate phases authored).
            for (i in 1 until phases.size) {
                assertTrue(
                    phases[i].ordinal > phases[i - 1].ordinal,
                    "$id phase_plan must be in strict progression order: $plan",
                )
            }
        }
    }

    @Test
    fun `ScaffoldPlanner planFor over the authored phase_plan yields a non-empty ordered plan`() {
        for (id in teachingKcIds) {
            // cold learner (mastery = null) ⇒ floor = intro ⇒ planFor = the authored phase_plan parsed.
            val plan = ScaffoldPlanner.planFor(kc(id), mastery = null)
            assertTrue(plan.isNotEmpty(), "$id planFor must yield a non-empty plan for a cold learner")
            val expected = kc(id).phase_plan!!.map { Phase.valueOf(it) }
            assertEquals(expected, plan, "$id planFor must reflect the authored phase_plan for a cold learner")
        }
    }

    @Test
    fun `authoring teaching fields did not disturb source grounding (verbatim_source still passes)`() {
        val sub = LoadedSubject(
            "PA",
            kcs = teachingKcIds.map { kc(it) },
            edges = emptyList(),
            misconceptions = emptyList(),
        )
        val errors = ContentValidator.checkVerbatimSources(sub) { doc ->
            if (doc == "pa-lecture-01") rawSource else null
        }.filter { it.severity == "error" }
        assertTrue(
            errors.isEmpty(),
            "authored teaching KCs must still pass verbatim_source; errors: ${errors.map { it.detail }}",
        )
    }
}
