package jarvis.content

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Path

/**
 * Plan-2 Task 4 — HASH-STABILITY PROOF. concept_type + an (empty) beats map are ADDITIVE fields that
 * do NOT feed ContentReconcile.claimsFor (plan core §0.3), so adding them must leave every production
 * KC's content_hash byte-identical. This test pins kcContentHashOf(claimsFor(kc)) for pa-kc-001..006
 * to the 6 LIVE ground-truth values (read read-only from ~/.jarvis/tutor.db on 2026-06-11). It passes
 * on the pre-edit corpus AND must still pass after Step 4 adds concept_type to the YAML — proving the
 * 4 faithful badges (pa-kc-001..004) provably survive.
 */
class ConceptTypeHashStabilityTest {

    private val contentDir: Path = Path.of("content")

    /** The 6 production verdict content_hash values, live on 2026-06-11 (plan core §0.1 ground truth). */
    private val liveHashes = mapOf(
        "pa-kc-001" to "ca05c671",
        "pa-kc-002" to "56156563",
        "pa-kc-003" to "d5363220",
        "pa-kc-004" to "817aaf44",
        "pa-kc-005" to "1b67adce",
        "pa-kc-006" to "6af8f795",
    )

    @Test fun `production KC content hashes equal the live ground truth (badges survive)`() {
        val repo = ContentRepo(contentDir)
        val kcsById = repo.loadManifest().subjects
            .flatMap { repo.loadSubject(it.id).kcs }
            .associateBy { it.id }
        for ((kcId, expected) in liveHashes) {
            val kc = kcsById[kcId] ?: error("$kcId missing from $contentDir")
            val recomputed = ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
            assertEquals(
                expected, recomputed,
                "$kcId content_hash drifted from the live audited value — concept_type/beats must " +
                    "NOT feed claimsFor; if this fails after editing YAML, the hash contract broke",
            )
        }
    }
}
