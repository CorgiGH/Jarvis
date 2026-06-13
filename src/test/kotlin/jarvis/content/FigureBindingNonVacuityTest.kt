package jarvis.content

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plan 4b Task 5 — anti-vacuity totality assert (R-4b-Q1).
 *
 * If every figure binding in the corpus is ever removed, CI reds — figure gates can never
 * silently go vacuous again (the "spec gates are alive only if they exercise something real"
 * principle, §9.3). This is the Kotlin-side totality assert; the rendered-DOM half lives in
 * `lesson-gates.spec.ts` (Task 9's spec anti-vacuity check from the SERVED payload/DOM).
 *
 * The test also asserts that every bound instance id actually exists in the corpus —
 * closing the "non-vacuous but dangling" gap (a null→non-null yaml edit that points nowhere
 * should not satisfy anti-vacuity without also satisfying INV-5.5).
 */
class FigureBindingNonVacuityTest {

    @Test fun `at least one beat-complete KC binds a reveal figure (anti-vacuity totality)`() {
        val repo = ContentRepo(Path.of("content"))
        val manifest = repo.loadManifest()
        val allSubjects = manifest.subjects.map { repo.loadSubject(it.id) }

        val figureBoundKcs = allSubjects.flatMap { sub ->
            sub.kcs.filter { kc ->
                kc.beats.values.any { beats -> beats.reveal?.figure != null }
            }
        }

        assertTrue(
            figureBoundKcs.isNotEmpty(),
            "Expected at least one KC with a non-null reveal.figure binding across ALL subjects, " +
                "but found none. The figure gates (INV-5.3, INV-4.4) are vacuous without a live binding — " +
                "add a valid binding to a beat-complete KC to fix this.",
        )
    }

    @Test fun `every bound figure instance_id exists in the corpus viz instances`() {
        val repo = ContentRepo(Path.of("content"))
        val manifest = repo.loadManifest()
        val allSubjects = manifest.subjects.map { repo.loadSubject(it.id) }

        // Build the full corpus instance map: subject → (instance_id → family_id)
        val instancesBySubject = manifest.subjects.associate { entry ->
            entry.id to repo.loadVizInstances(entry.id).associate { it.id to it.family_id }
        }

        val danglingBindings = mutableListOf<String>()
        for (sub in allSubjects) {
            val knownInstances = instancesBySubject[sub.subject] ?: emptyMap()
            for (kc in sub.kcs) {
                for ((lang, beats) in kc.beats) {
                    val fb = beats.reveal?.figure ?: continue
                    if (!knownInstances.containsKey(fb.instance_id)) {
                        danglingBindings += "${sub.subject}/${kc.id}[$lang]: " +
                            "instance_id='${fb.instance_id}' not found in content/${sub.subject}/viz/"
                    }
                }
            }
        }

        assertTrue(
            danglingBindings.isEmpty(),
            "Found figure bindings referencing non-existent instances:\n" +
                danglingBindings.joinToString("\n"),
        )
    }

    @Test fun `pa-kc-002 has the mergesort figure binding (current corpus anchor)`() {
        // This test pins the current state: pa-kc-002 binds viz-pa-mergesort-001 (graph-tree).
        // If the binding is moved or renamed, this test reds and forces a conscious update.
        val repo = ContentRepo(Path.of("content"))
        val sub = repo.loadSubject("PA")
        val kc002 = sub.kcs.single { it.id == "pa-kc-002" }
        val figure = kc002.beats["ro"]?.reveal?.figure

        assertNotNull(figure, "pa-kc-002 must have a non-null reveal.figure binding on the 'ro' beats")
        assertTrue(
            figure.instance_id == "viz-pa-mergesort-001",
            "pa-kc-002 must bind viz-pa-mergesort-001 (got: '${figure.instance_id}')",
        )
        assertTrue(
            figure.family_id == "graph-tree",
            "pa-kc-002's binding must have family_id='graph-tree' (got: '${figure.family_id}')",
        )
    }
}
