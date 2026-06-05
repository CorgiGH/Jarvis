package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * TDD tests for VerificationStatus enum + VerificationStatus_.transition (Task 7, §I, §2.4).
 * Written FIRST before implementation; run these to see RED, then implement to get GREEN.
 *
 * §2.4 state machine:
 *   UNVERIFIED --(curate-tutor emits, sets PENDING directly, NOT via transition)--> PENDING
 *   PENDING + ALL_AGREE_ROUNDTRIP_NONLLM_PASS --> FAITHFUL
 *   PENDING + FAMILY_COLLAPSE                 --> UNCERTAIN
 *   PENDING + DEFINITIONAL_NO_GOLD_SPAN       --> UNCERTAIN
 *   PENDING + NONLLM_LEG_NONE                 --> UNCERTAIN
 *   PENDING + DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW --> FAILED (or UNCERTAIN — locked as FAILED)
 *   FAITHFUL + REPORT_WRONG                   --> PENDING
 *
 * §2.4 invariants:
 *   - No path reaches FAITHFUL without BOTH a non-LLM-leg-pass AND families-agree.
 *   - FAMILY_COLLAPSE => UNCERTAIN (never FAILED).
 *   - Thrown leg => UNCERTAIN (never a mid-txn crash).
 *   - No auto-clear from student attempts.
 *   - FAIL-LOUD: "never ran" != "disagreed".
 *
 * §I note on UNVERIFIED→PENDING: the UNVERIFIED→PENDING edge is set by curate-tutor Stage-9
 * directly (NOT via transition). The transition fn is for audit outcomes, not for curation events.
 * Test asserts transition never silently jumps UNVERIFIED→FAITHFUL.
 *
 * DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW choice: locked as FAILED per §2.4 "legs disagree /
 * roundtrip fails / span re-locate fails / a leg threw → FAILED-or-UNCERTAIN". We resolve to
 * FAILED for explicit disagreement (semantically "we know they disagree"); the thrown-leg case
 * maps to UNCERTAIN (genuinely uncertain — we don't know). Since the AuditOutcome
 * DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW covers both, we resolve to FAILED as the stricter/louder
 * signal (per FAIL-LOUD rule). This choice is pinned and tested here.
 */
class VerificationStatusTest {

    // ──────────────────────────────────────────────
    // Group 1 — PENDING → FAITHFUL (the only path to faithful)
    // ──────────────────────────────────────────────

    @Test
    fun `PENDING + ALL_AGREE_ROUNDTRIP_NONLLM_PASS yields FAITHFUL`() {
        assertEquals(
            VerificationStatus.faithful,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS)
        )
    }

    // ──────────────────────────────────────────────
    // Group 2 — PENDING → UNCERTAIN paths
    // ──────────────────────────────────────────────

    @Test
    fun `PENDING + FAMILY_COLLAPSE yields UNCERTAIN`() {
        assertEquals(
            VerificationStatus.uncertain,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.FAMILY_COLLAPSE)
        )
    }

    @Test
    fun `PENDING + DEFINITIONAL_NO_GOLD_SPAN yields UNCERTAIN`() {
        assertEquals(
            VerificationStatus.uncertain,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN)
        )
    }

    @Test
    fun `PENDING + NONLLM_LEG_NONE yields UNCERTAIN`() {
        assertEquals(
            VerificationStatus.uncertain,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.NONLLM_LEG_NONE)
        )
    }

    @Test
    fun `PENDING + EQUATIONAL_LLM_UNCONFIRMED yields UNCERTAIN (D-R9)`() {
        // B5r-3 / D-R9: SymPy machine-proved the equation but the LLM merely couldn't confirm the NL
        // meaning ⇒ not-yet-cross-checked, NOT a contradiction ⇒ uncertain (never failed, never faithful).
        assertEquals(
            VerificationStatus.uncertain,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED)
        )
    }

    @Test
    fun `PENDING + PROSE_LLM_UNCONFIRMED yields UNCERTAIN (MF-1 D-R17)`() {
        // MF-1 / D-R17: a content≠quote prose claim whose anchor round-tripped + no REFUTED but whose
        // rule text was NOT independently confirmed (NOT bothSupported) ⇒ not-yet-content-confirmed,
        // NOT a contradiction ⇒ uncertain (never failed, never faithful).
        assertEquals(
            VerificationStatus.uncertain,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.PROSE_LLM_UNCONFIRMED)
        )
    }

    // ──────────────────────────────────────────────
    // Group 3 — PENDING → FAILED (disagree/roundtrip fail — FAIL-LOUD)
    // ──────────────────────────────────────────────

    @Test
    fun `PENDING + DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW yields FAILED`() {
        // Choice locked as FAILED (stricter/louder signal per FAIL-LOUD rule).
        assertEquals(
            VerificationStatus.failed,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW)
        )
    }

    // ──────────────────────────────────────────────
    // Group 4 — FAITHFUL → PENDING (report-wrong)
    // ──────────────────────────────────────────────

    @Test
    fun `FAITHFUL + REPORT_WRONG yields PENDING`() {
        assertEquals(
            VerificationStatus.pending,
            VerificationStatus_.transition(VerificationStatus.faithful, AuditOutcome.REPORT_WRONG)
        )
    }

    // ──────────────────────────────────────────────
    // Group 5 — Invariant: no path to FAITHFUL except ALL_AGREE_ROUNDTRIP_NONLLM_PASS
    // ──────────────────────────────────────────────

    @Test
    fun `no_path_to_faithful_without_both_legs_from_pending`() {
        // Every AuditOutcome other than ALL_AGREE must NOT produce faithful
        val nonFaithfulOutcomes = listOf(
            AuditOutcome.FAMILY_COLLAPSE,
            AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN,
            AuditOutcome.NONLLM_LEG_NONE,
            AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED,
            AuditOutcome.PROSE_LLM_UNCONFIRMED,
            AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW,
            AuditOutcome.REPORT_WRONG,
        )
        for (outcome in nonFaithfulOutcomes) {
            assertNotEquals(
                VerificationStatus.faithful,
                VerificationStatus_.transition(VerificationStatus.pending, outcome),
                "Expected non-faithful result for outcome=$outcome from PENDING"
            )
        }
    }

    @Test
    fun `only_ALL_AGREE_from_PENDING_can_reach_FAITHFUL`() {
        // The one and only path to faithful
        assertEquals(
            VerificationStatus.faithful,
            VerificationStatus_.transition(VerificationStatus.pending, AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS)
        )
    }

    // ──────────────────────────────────────────────
    // Group 6 — UNVERIFIED: curate-side direct set; transition never jumps UNVERIFIED → FAITHFUL
    // ──────────────────────────────────────────────

    @Test
    fun `transition_never_silently_jumps_UNVERIFIED_to_FAITHFUL`() {
        // Every audit outcome from UNVERIFIED must NOT produce faithful.
        // UNVERIFIED→PENDING is set by curate-tutor directly (not via transition).
        for (outcome in AuditOutcome.entries) {
            assertNotEquals(
                VerificationStatus.faithful,
                VerificationStatus_.transition(VerificationStatus.unverified, outcome),
                "Expected non-faithful from UNVERIFIED + $outcome"
            )
        }
    }

    // ──────────────────────────────────────────────
    // Group 7 — UNCERTAIN / FAILED: idempotent or stable (no auto-clear)
    // ──────────────────────────────────────────────

    @Test
    fun `UNCERTAIN_does_not_auto_clear_to_faithful_on_any_outcome`() {
        for (outcome in AuditOutcome.entries) {
            assertNotEquals(
                VerificationStatus.faithful,
                VerificationStatus_.transition(VerificationStatus.uncertain, outcome),
                "Expected non-faithful from UNCERTAIN + $outcome"
            )
        }
    }

    @Test
    fun `FAILED_does_not_auto_clear_to_faithful_on_any_outcome`() {
        for (outcome in AuditOutcome.entries) {
            assertNotEquals(
                VerificationStatus.faithful,
                VerificationStatus_.transition(VerificationStatus.failed, outcome),
                "Expected non-faithful from FAILED + $outcome"
            )
        }
    }
}
