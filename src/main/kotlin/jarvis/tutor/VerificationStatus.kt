package jarvis.tutor

/**
 * A KC's resolved trust status. Runtime source of truth = kc_verification_status table
 * (B8), NOT the YAML seed. Wire literal == name (lowercase).
 *
 * Five literals:
 *   unverified  — authored seed (default); never audited
 *   pending     — audit in progress (curate-tutor Stage-9 sets this directly, NOT via transition)
 *   faithful    — all legs agree + roundtrip + non-LLM pass; badge "matches your lecture"
 *   uncertain   — audit inconclusive (family collapse / no gold span / no non-LLM leg / thrown leg)
 *   failed      — audit found explicit disagreement (legs disagree / roundtrip fails)
 *
 * YAML authoring caveat: the four authored literals are {unverified, pending, faithful, uncertain}.
 * `failed` is runtime-only (ContentValidator's enum check accepts only the four authored literals).
 */
enum class VerificationStatus { unverified, pending, faithful, uncertain, failed }

/**
 * What one audit run concluded. Drives the §2.4 / §2.5 state machine.
 *
 * Per §I (interface-signatures-lock):
 *   ALL_AGREE_ROUNDTRIP_NONLLM_PASS     -> faithful
 *   FAMILY_COLLAPSE                     -> uncertain  (both legs same configured family)
 *   DEFINITIONAL_NO_GOLD_SPAN           -> uncertain  (no verbatim citation to anchor)
 *   NONLLM_LEG_NONE                     -> uncertain  (domain has no non-LLM checker; UNCERTAIN floor)
 *   EQUATIONAL_LLM_UNCONFIRMED          -> uncertain  (SymPy proved the math; the LLM merely couldn't
 *                                                      confirm the NL meaning — not-yet-cross-checked,
 *                                                      NOT a contradiction; B5r-3 / D-R9)
 *   DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW -> failed     (FAIL-LOUD; explicit disagreement/failure)
 *   REPORT_WRONG                        -> faithful -> pending  (student filed a correction)
 *
 * NOTE on the frozen surface: [VerificationStatus] (the 5 wire literals) is FROZEN and untouched.
 * [AuditOutcome] is an internal driver of the §2.4/§2.5 machine, NOT a wire enum — adding
 * [EQUATIONAL_LLM_UNCONFIRMED] (which maps to the existing `uncertain` status) does not widen the
 * frozen status surface.
 */
enum class AuditOutcome {
    ALL_AGREE_ROUNDTRIP_NONLLM_PASS,
    FAMILY_COLLAPSE,
    DEFINITIONAL_NO_GOLD_SPAN,
    NONLLM_LEG_NONE,
    /** B5r-3 (D-R9): equational claim, SymPy ran+passed, round-trip passed, nothing threw, NOT
     *  agreed-REFUTED — but the LLM family was merely UNCLEAR (not bothSupported). Maps to `uncertain`. */
    EQUATIONAL_LLM_UNCONFIRMED,
    DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW,
    REPORT_WRONG,
}

/**
 * Companion-style pure transition holder. Name per signatures-lock §I (trailing underscore).
 *
 * §2.4 invariants enforced here:
 *  - No path reaches faithful without BOTH a non-LLM-leg pass AND families-agree.
 *    The sole faithful path is: pending + ALL_AGREE_ROUNDTRIP_NONLLM_PASS.
 *  - FAMILY_COLLAPSE => uncertain (never failed).
 *  - Thrown leg (covered by DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW) => failed (FAIL-LOUD).
 *  - No auto-clear from student attempts — UNCERTAIN/FAILED have no auto-resolve path here.
 *  - UNVERIFIED → PENDING is set by curate-tutor Stage-9 directly, NOT via this function.
 *    transition(unverified, *) never returns faithful (by the exhaustive when below).
 *  - DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW choice: resolved as FAILED per FAIL-LOUD rule
 *    (stricter signal; we know they disagreed, not merely that we couldn't get a result).
 */
object VerificationStatus_ {

    /**
     * Pure state-machine transition. No DB, no side effects.
     *
     * @param from    The KC's current resolved status.
     * @param outcome What the audit run (or report-wrong event) concluded.
     * @return        The new VerificationStatus after applying the outcome.
     */
    fun transition(from: VerificationStatus, outcome: AuditOutcome): VerificationStatus =
        when (from) {
            VerificationStatus.pending -> when (outcome) {
                AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS     -> VerificationStatus.faithful
                AuditOutcome.FAMILY_COLLAPSE                      -> VerificationStatus.uncertain
                AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN            -> VerificationStatus.uncertain
                AuditOutcome.NONLLM_LEG_NONE                      -> VerificationStatus.uncertain
                AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED           -> VerificationStatus.uncertain
                AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW  -> VerificationStatus.failed
                // REPORT_WRONG from pending: already pending; no change meaningful — stay pending
                AuditOutcome.REPORT_WRONG                         -> VerificationStatus.pending
            }
            VerificationStatus.faithful -> when (outcome) {
                AuditOutcome.REPORT_WRONG -> VerificationStatus.pending
                // Any re-audit outcome from faithful re-runs the audit: treat like pending->outcome
                AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS     -> VerificationStatus.faithful
                AuditOutcome.FAMILY_COLLAPSE                      -> VerificationStatus.uncertain
                AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN            -> VerificationStatus.uncertain
                AuditOutcome.NONLLM_LEG_NONE                      -> VerificationStatus.uncertain
                AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED           -> VerificationStatus.uncertain
                AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW  -> VerificationStatus.failed
            }
            // UNVERIFIED: curate-tutor sets PENDING directly; an audit MUST NOT run until
            // curate promotes to pending first. Transition from unverified never returns faithful
            // (FAIL-LOUD: "never ran" != "agreed"). Audit outcomes from unverified are treated
            // conservatively: non-LLM and disagreement outcomes go to uncertain/failed; REPORT_WRONG
            // has no meaning on an unverified KC (stays unverified). ALL_AGREE from an unverified KC
            // is impossible in a correct system, but if somehow called, go to pending (not faithful)
            // to force a proper audit cycle.
            VerificationStatus.unverified -> when (outcome) {
                AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS     -> VerificationStatus.pending  // NOT faithful — must go pending first
                AuditOutcome.FAMILY_COLLAPSE                      -> VerificationStatus.uncertain
                AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN            -> VerificationStatus.uncertain
                AuditOutcome.NONLLM_LEG_NONE                      -> VerificationStatus.uncertain
                AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED           -> VerificationStatus.uncertain
                AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW  -> VerificationStatus.failed
                AuditOutcome.REPORT_WRONG                         -> VerificationStatus.unverified
            }
            // UNCERTAIN and FAILED: no auto-clear per §2.4 ("No auto-clear from student attempts").
            // The §2.4 machine only specifies paths FROM pending; uncertain/failed are terminal
            // states for the current audit run. To re-enter the machine, the admin must explicitly
            // re-submit the KC to curate-tutor (which resets to pending) — not via this fn.
            // Therefore: ALL_AGREE from uncertain/failed does NOT produce faithful (the prior
            // audit cycle's conclusion stands; a new audit cycle requires going through pending).
            // REPORT_WRONG on uncertain/failed: stays in the current state (already not trusted).
            VerificationStatus.uncertain -> when (outcome) {
                AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS     -> VerificationStatus.uncertain  // no auto-clear; re-run via pending
                AuditOutcome.FAMILY_COLLAPSE                      -> VerificationStatus.uncertain
                AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN            -> VerificationStatus.uncertain
                AuditOutcome.NONLLM_LEG_NONE                      -> VerificationStatus.uncertain
                AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED           -> VerificationStatus.uncertain
                AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW  -> VerificationStatus.failed
                AuditOutcome.REPORT_WRONG                         -> VerificationStatus.uncertain
            }
            VerificationStatus.failed -> when (outcome) {
                AuditOutcome.ALL_AGREE_ROUNDTRIP_NONLLM_PASS     -> VerificationStatus.failed    // no auto-clear; re-run via pending
                AuditOutcome.FAMILY_COLLAPSE                      -> VerificationStatus.uncertain
                AuditOutcome.DEFINITIONAL_NO_GOLD_SPAN            -> VerificationStatus.uncertain
                AuditOutcome.NONLLM_LEG_NONE                      -> VerificationStatus.uncertain
                AuditOutcome.EQUATIONAL_LLM_UNCONFIRMED           -> VerificationStatus.uncertain
                AuditOutcome.DISAGREE_OR_ROUNDTRIP_FAIL_OR_THREW  -> VerificationStatus.failed
                AuditOutcome.REPORT_WRONG                         -> VerificationStatus.failed
            }
        }
}
