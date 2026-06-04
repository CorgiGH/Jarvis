package jarvis.content

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Batch-4 (b) — the two AUTHORED computational KCs `pa-kc-005` / `pa-kc-006` are the
 * FAITHFUL-target KCs of the trust-net acceptance. They are SYSTEM-DERIVED from the real
 * `Curs 1 PA.pdf` extraction (`pa-lecture-01.md`) — NEVER routed to a human (no oracle inversion).
 *
 * This test pins their authored shape:
 *  - `grounding_tier == strict`, so the H11 validator enforces invariant + grader_rules + spans;
 *  - `invariant != null` AND `grader_rules` non-empty (a machine-checkable equational claim exists);
 *  - every source ref carries a vision-confirmed RAW span that round-trips diacritic-EXACT via
 *    `SourceOfRecord.slice` against the committed source-of-record;
 *  - the strict validator (H11) reports ZERO errors for them.
 */
class AuthoredStrictKcsTest {

    private val repo = ContentRepo(Paths.get("content"))
    private val pa = repo.loadSubject("PA")
    private val rawSource: String =
        Files.readString(Paths.get("content", "PA", "_sources", "pa-lecture-01.md"))

    private fun kc(id: String): KnowledgeConcept =
        pa.kcs.single { it.id == id }

    @Test
    fun `pa-kc-005 and pa-kc-006 are authored strict with invariant and grader_rules`() {
        for (id in listOf("pa-kc-005", "pa-kc-006")) {
            val k = kc(id)
            assertEquals("strict", k.grounding_tier, "$id must be authored grounding_tier=strict")
            assertNotNull(k.invariant, "$id must carry a precise invariant")
            assertTrue(k.invariant!!.isNotBlank(), "$id invariant must be non-blank")
            assertTrue(k.grader_rules.isNotEmpty(), "$id must carry non-empty grader_rules")
        }
    }

    @Test
    fun `every authored span round-trips diacritic-exact via SourceOfRecord slice`() {
        for (id in listOf("pa-kc-005", "pa-kc-006")) {
            val k = kc(id)
            assertTrue(k.source.isNotEmpty(), "$id must carry source refs")
            for (ref in k.source) {
                val span = ref.span
                assertNotNull(span, "$id ref to '${ref.doc}' must carry an anchored RAW span (strict)")
                val slice = SourceOfRecord.slice(rawSource, span)
                assertNotNull(slice, "$id span ${span.start}..${span.end} must be in bounds")
                assertEquals(ref.quote, slice, "$id quote must equal the RAW slice character-for-character (diacritic-exact)")
                assertEquals("vision-confirmed", ref.provenance, "$id ref must be vision-confirmed (strict + system-derived)")
            }
        }
    }

    @Test
    fun `the strict validator reports zero errors for the two authored KCs`() {
        val sub = LoadedSubject("PA", kcs = listOf(kc("pa-kc-005"), kc("pa-kc-006")), edges = emptyList(), misconceptions = emptyList())
        val strict = ContentValidator.checkStrictGrounding(sub).filter { it.severity == "error" }
        assertTrue(strict.isEmpty(), "authored strict KCs must pass strict_grounding; errors: ${strict.map { it.detail }}")
        val verbatim = ContentValidator.checkVerbatimSources(sub) { doc ->
            if (doc == "pa-lecture-01") rawSource else null
        }.filter { it.severity == "error" }
        assertTrue(verbatim.isEmpty(), "authored strict KCs must pass verbatim_source; errors: ${verbatim.map { it.detail }}")
    }
}
