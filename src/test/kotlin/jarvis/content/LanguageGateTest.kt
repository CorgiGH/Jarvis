package jarvis.content

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan 4b Task 6 (spec §0.9E / INV-8.4) — unit tests for [LanguageGate].
 *
 * Covers the three legs:
 *   (1) EN-vocabulary leg (checked FIRST, any string length): stem in EN_VOCAB after RO suffix strip → FAIL
 *   (2) diacritic-presence leg (≥3 words): any ăâîșțĂÂÎȘȚ → PASS
 *   (3) EN-stopword ratio leg (≥3 words): ratio >= threshold → FAIL
 *
 * §0.9E normalization: «…»/‹…› quoted spans stripped first; then glossary terms / per-call exempt
 * list stripped; then numbers / code-ish tokens stripped; then the three legs apply.
 * Short strings (< shortStringWords=3) are exempt from the diacritic leg but NOT the stopword leg
 * and NOT the EN-vocabulary leg.
 *
 * ASCII test method names (§0 #15 — no non-ASCII in JUnit 5 @Test method names).
 */
class LanguageGateTest {

    // ── Pass cases ──────────────────────────────────────────────────────────────────────────────

    @Test fun `ro string with diacritics passes`() {
        val issues = LanguageGate.checkField("Algoritmul parcurge fiecare nod al grafului.", "field", "kc-1")
        assertNoErrors(issues)
    }

    @Test fun `short ro string without diacritics passes the diacritic leg exemption`() {
        // "pas 3/8" — less than shortStringWords words, numbers stripped → trivially short
        val issues = LanguageGate.checkField("pas 3/8", "field", "kc-1")
        assertNoErrors(issues)
    }

    @Test fun `ro formula label with digits and slash passes`() {
        val issues = LanguageGate.checkField("O(n log n)", "field", "kc-1")
        assertNoErrors(issues)
    }

    @Test fun `ro text with guillemet-quoted EN span passes (quote stripped before legs)`() {
        // §0.9E: strip «…» first, then check the remaining RO framing
        val issues = LanguageGate.checkField(
            "Cursul spune: «The general formal description of this is that of computation model».",
            "field",
            "kc-1",
        )
        // After stripping the guillemet span the remainder is purely RO ("Cursul spune: .")
        assertNoErrors(issues)
    }

    @Test fun `ro text with single guillemet-quoted EN span passes`() {
        val issues = LanguageGate.checkField(
            "Conform cursului: «An algorithm must solve a problem given by a pair (input,output)».",
            "field",
            "kc-1",
        )
        assertNoErrors(issues)
    }

    @Test fun `glossary term stripped before EN-vocab leg so it passes`() {
        val glossary = setOf("skeleton") // skeleton is in EN_VOCAB but exempted via glossary
        val issues = LanguageGate.checkField("skeleton-ul grafului", "field", "kc-1", glossaryTerms = glossary)
        assertNoErrors(issues)
    }

    @Test fun `per-call exempt term passes EN-vocab leg`() {
        val exempt = setOf("heap") // exempt for this call
        val issues = LanguageGate.checkField("heap-ul din memorie", "field", "kc-1", exempt = exempt)
        assertNoErrors(issues)
    }

    // ── EN-vocabulary leg fail cases ─────────────────────────────────────────────────────────────

    @Test fun `skeletonul ramane fix fails via EN-vocab leg (stopword ratio is 0)`() {
        // "skeletonul rămâne fix" — EN-stopword ratio ≈ 0, but "skeleton" stem is in EN_VOCAB.
        // Without the EN-vocab leg, BOTH other legs pass: diacritics present in "rămâne".
        // Plan-fix F1 / §0.9E: the EN-vocab leg is checked FIRST and catches this class.
        val issues = LanguageGate.checkField("skeletonul rămâne fix", "field", "kc-1")
        assertHasError(issues, "kc-1", "field")
    }

    @Test fun `skeletonul ramane fix without diacritics also fails via EN-vocab leg`() {
        // No diacritics in "ramane" — the diacritic leg would also fail, but EN-vocab fires first.
        val issues = LanguageGate.checkField("skeletonul ramane fix", "field", "kc-1")
        assertHasError(issues, "kc-1", "field")
    }

    @Test fun `bare EN vocab word fails EN-vocab leg`() {
        val issues = LanguageGate.checkField("heap property holds", "field", "kc-1")
        assertHasError(issues, "kc-1", "field")
    }

    @Test fun `RO-inflected EN vocab word fails EN-vocab leg`() {
        // "stackul" → strip "-ul" → "stack" ∈ EN_VOCAB → FAIL
        val issues = LanguageGate.checkField("stackul din memorie este gol", "field", "kc-1")
        assertHasError(issues, "kc-1", "field")
    }

    // ── EN-stopword ratio leg fail cases ─────────────────────────────────────────────────────────

    @Test fun `three-plus word EN sentence fails stopword ratio leg`() {
        val issues = LanguageGate.checkField("the heap property holds", "field", "kc-1")
        assertHasError(issues, "kc-1", "field")
    }

    @Test fun `the heap passes stopword ratio for two-word string (short, but still flagged by EN-vocab)`() {
        // "the heap" — 2 words, exempt from diacritic leg; but EN-vocab catches "heap"
        val issues = LanguageGate.checkField("the heap", "field", "kc-1")
        assertHasError(issues, "kc-1", "field")
    }

    @Test fun `mixed RO frame with EN trailing sentence fails`() {
        // The RO part passes diacritics, but the EN part has high stopword ratio
        val issues = LanguageGate.checkField(
            "Algoritmul sortează lista. The array is sorted correctly with this approach.",
            "field",
            "kc-1",
        )
        assertHasError(issues, "kc-1", "field")
    }

    // ── Diacritic leg pass (when no EN-vocab and stopword OK) ────────────────────────────────────

    @Test fun `ro sentence without EN vocab or high stopword passes`() {
        val issues = LanguageGate.checkField("Nodurile grafului sunt parcurse în ordinea adâncimii.", "field", "kc-1")
        assertNoErrors(issues)
    }

    @Test fun `KC name_ro from corpus passes`() {
        // From pa-kc-002: "Problemă, model de calcul și perechea (intrare, ieșire)"
        val issues = LanguageGate.checkField(
            "Problemă, model de calcul și perechea (intrare, ieșire)",
            "name_ro",
            "pa-kc-002",
        )
        assertNoErrors(issues)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun assertNoErrors(issues: List<ValidationIssue>) {
        val errors = issues.filter { it.severity == "error" }
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    private fun assertHasError(issues: List<ValidationIssue>, kcId: String, field: String) {
        val errors = issues.filter { it.severity == "error" }
        assertFalse(errors.isEmpty(), "Expected at least one error but got none. Issues: $issues")
        assertTrue(
            errors.any { it.detail.contains(kcId) || it.detail.contains(field) },
            "Error should mention '$kcId' or '$field'. Got: $errors",
        )
    }
}
