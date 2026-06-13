package jarvis.content

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan 4b Task 5 — accommodation ledger item 1 (R-4b-Q1):
 * FigureBinding beats don't feed [ContentReconcile.claimsFor] / [ContentReconcile.kcContentHash],
 * so editing `beats.ro.reveal.figure` in a KC YAML does NOT stale the D8 hash (§0 #4).
 *
 * This test PINS that invariant with the REAL pa-kc-002 corpus KC. If a future change makes
 * beats feed claimsFor, this test turns RED and forces a re-verification cycle (the off-box
 * dump + migration + trust-net re-run the ruling demands) before the edit can land.
 *
 * Mechanism: load pa-kc-002, compute hash; copy with figure binding set to a non-null binding
 * (the actual viz-pa-mergesort-001 binding we are about to add); compute hash again — must be
 * IDENTICAL. Also assert claimsFor is identical across both copies.
 */
class FigureBindingHashNoRippleTest {

    @Test fun `figure binding edit does not change kcContentHash or claimsFor`() {
        val repo = ContentRepo(Path.of("content"))
        val sub = repo.loadSubject("PA")
        val kc = sub.kcs.single { it.id == "pa-kc-002" }

        // Confirm the corpus KC currently has figure = null on its ro beats
        val roBefore = kc.beats["ro"]?.reveal?.figure
        // (The figure may be null or non-null depending on when the YAML edit in Step 3 landed;
        //  either way, we test with a copy that has it null vs one that has it set.)

        // Make a copy with figure = null
        val withoutFigure = kc.withBeats(kc.beats.mapValues { (lang, beats) ->
            if (lang == "ro") beats.copy(reveal = beats.reveal?.copy(figure = null))
            else beats
        })

        // Make a copy with figure = the real binding
        val binding = FigureBinding(family_id = "graph-tree", instance_id = "viz-pa-mergesort-001")
        val withFigure = kc.withBeats(kc.beats.mapValues { (lang, beats) ->
            if (lang == "ro") beats.copy(reveal = beats.reveal?.copy(figure = binding))
            else beats
        })

        val claimsWithout = ContentReconcile.claimsFor(withoutFigure)
        val claimsWith = ContentReconcile.claimsFor(withFigure)

        assertEquals(
            claimsWithout.map { it.claimId },
            claimsWith.map { it.claimId },
            "claimsFor must be identical regardless of figure binding — beats don't feed claimsFor (§0 #4)",
        )
        assertEquals(
            claimsWithout.map { it.content },
            claimsWith.map { it.content },
            "claim content must be identical regardless of figure binding",
        )

        val hashWithout = ContentReconcile.kcContentHash(withoutFigure)
        val hashWith = ContentReconcile.kcContentHash(withFigure)
        assertEquals(
            hashWithout,
            hashWith,
            "kcContentHash must be identical regardless of figure binding — no re-audit cascade (§0 #4)",
        )
    }
}

/** Helper: return a copy of this KC with the given beats map (avoids exposing a public copy method). */
private fun KnowledgeConcept.withBeats(newBeats: Map<String, KcBeats>): KnowledgeConcept =
    copy(beats = newBeats)
