package jarvis.content

import jarvis.tutor.verify.TrustInvariantsCli
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-3 Task 7 — the 4 faithful PA KCs (pa-kc-001..004) now carry authored ro beats; each must be
 * STRUCTURALLY COMPLETE for its concept_type (KcBeats.isCompleteFor) so the lesson handler (Task 3)
 * serves them and Plan-2 INV-3.2b stays green. Runs the SAME pure leg the trustInvariants CLI runs
 * (TrustInvariantsCli.checkKcs) over the loaded REAL corpus — not a fixture (phase-done = acceptance
 * on the real corpus, fix_claim_discipline). The other 4 PA KCs (005/006 + 2 fixtures) have NO beats
 * yet, which is allowed (empty beats skip the completeness check, Plan-2 §0.8 #5).
 */
class AuthoredBeatsCompletenessTest {

    private val repo = ContentRepo(Path.of("content"))
    private val paKcs by lazy { repo.loadSubject("PA").kcs.associateBy { it.id } }

    @Test fun `pa-kc-001 to 004 each carry complete ro beats for their concept_type`() {
        for (id in listOf("pa-kc-001", "pa-kc-002", "pa-kc-003", "pa-kc-004")) {
            val kc = paKcs[id] ?: error("$id missing from content/PA/kcs")
            val type = ConceptType.fromWire(kc.concept_type ?: "")
                ?: error("$id has missing/invalid concept_type=${kc.concept_type}")
            val ro = kc.beats["ro"] ?: error("$id has no ro beats")
            assertTrue(ro.isCompleteFor(type), "$id ro beats incomplete for $type")
        }
    }

    @Test fun `the full PA corpus passes the INV-3_2b authored-beats leg`() {
        // checkKcs runs INV-3.2a (concept_type valid) + INV-3.2b (authored beats complete) — DB-free.
        val failures = TrustInvariantsCli.checkKcs(repo.loadSubject("PA").kcs)
        assertEquals(
            emptyList(), failures,
            "INV-3.2a/3.2b failures over the real PA corpus: $failures",
        )
    }
}
