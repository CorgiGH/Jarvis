package jarvis.content

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan 4b Task 6 (spec §0.9F / INV-8.2) — the UNDER-BLOCKING guard for [LanguageGate].
 *
 * The seeded set: strings from the SESSION-55/56/57 class that should have been caught as
 * containing non-RO content. ALL must produce "error"-severity issues from [LanguageGate].
 *
 * These strings are in-test fixtures and NEVER live under content/ (they must not enter admission).
 *
 * Critical property: these fixtures must NEVER be reworded into stopword-heavy EN to make them
 * pass another leg — that would gut the gate. The EN-vocabulary leg (§0.9E plan-fix F1) is
 * REQUIRED precisely because these strings have EN-stopword ratio ≈ 0 and may contain diacritics,
 * making them structurally invisible to both other legs.
 *
 * ASCII test method names (§0 #15).
 */
class LanguageGateSeededRegressionTest {

    // ── QR-EN-LEAK: the flagship "skeleton"/"skeletonul" class (INV-8.2) ──────────────────────

    @Test fun `QR-EN-LEAK skeleton bare word is flagged`() {
        // "skeleton" — bare EN vocab word embedded in a Romanian sentence context.
        // EN-stopword ratio ≈ 0 → only the EN-vocab leg can catch this.
        val issues = LanguageGate.checkField("skeleton rămâne fix în memorie", "reveal_step_text", "kc-x")
        assertAllError(issues)
    }

    @Test fun `QR-EN-LEAK skeletonul ramane fix (canonical flagged form with diacritics)`() {
        // "skeletonul rămâne fix" — diacritics present, EN-stopword ratio=0; caught ONLY by EN-vocab leg.
        val issues = LanguageGate.checkField("skeletonul rămâne fix", "reveal_step_callout", "kc-x")
        assertAllError(issues)
    }

    @Test fun `QR-EN-LEAK skeletonul ramane fix (no diacritics variant)`() {
        // Same string without the â in "rămâne" — still caught by EN-vocab leg on "skeleton" stem.
        val issues = LanguageGate.checkField("skeletonul ramane fix", "reveal_step_text", "kc-x")
        assertAllError(issues)
    }

    @Test fun `QR-EN-LEAK arrayul pastreaza ordinea`() {
        // "arrayul" → strip "-ul" → "array" ∈ EN_VOCAB → EN-vocab leg fires.
        val issues = LanguageGate.checkField("arrayul păstrează ordinea elementelor", "beat_statement", "kc-y")
        assertAllError(issues)
    }

    @Test fun `QR-EN-LEAK heapul este gol`() {
        // "heapul" → "heap" ∈ EN_VOCAB
        val issues = LanguageGate.checkField("heapul este gol acum", "check_item_stem", "kc-z")
        assertAllError(issues)
    }

    // ── EN drill stems (SESSION-55/56/57 class) ──────────────────────────────────────────────

    @Test fun `EN drill stem 1 fails - the array is sorted in ascending order`() {
        val issues = LanguageGate.checkField(
            "the array is sorted in ascending order",
            "attempt_choice_text",
            "kc-drill-1",
        )
        assertAllError(issues)
    }

    @Test fun `EN drill stem 2 fails - this loop continues until the condition is false`() {
        val issues = LanguageGate.checkField(
            "this loop continues until the condition is false",
            "attempt_feedback",
            "kc-drill-2",
        )
        assertAllError(issues)
    }

    // ── EN grader-feedback strings (SESSION-55/56/57 class) ──────────────────────────────────

    @Test fun `EN grader feedback 1 fails - correct the answer matches the expected output`() {
        val issues = LanguageGate.checkField(
            "correct, the answer matches the expected output",
            "feedback_correct",
            "kc-grade-1",
        )
        assertAllError(issues)
    }

    @Test fun `EN grader feedback 2 fails - incorrect the stack should be empty at this point`() {
        val issues = LanguageGate.checkField(
            "incorrect, the stack should be empty at this point",
            "attempt_choice_feedback",
            "kc-grade-2",
        )
        assertAllError(issues)
    }

    // ── Helper ──────────────────────────────────────────────────────────────────────────────────

    private fun assertAllError(issues: List<ValidationIssue>) {
        assertFalse(
            issues.isEmpty(),
            "Expected ≥1 error-severity issue from LanguageGate but got NO issues",
        )
        assertTrue(
            issues.any { it.severity == "error" },
            "Expected 'error' severity but got: ${issues.map { it.severity }}",
        )
    }
}
