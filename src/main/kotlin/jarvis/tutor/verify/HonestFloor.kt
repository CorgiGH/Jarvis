package jarvis.tutor.verify

import jarvis.tutor.VerificationStatus

/**
 * §P — caps what the trust badge may CLAIM while the formal calibration gold-set gate is
 * deferred (master §6.1 / §7 item 2). A field on the `/verify/{kcId}/status` reply.
 * Wire literal == name (UPPER). DECIDED #2.
 */
enum class HonestFloor {
    /** Re-located + two-family-agreed + non-LLM-leg-passed against the LIVE source
     *  (VerificationStatus.faithful): the badge may say "matches your lecture / faithful to
     *  your source" — but NEVER "verified correct" / "grade-calibrated" (the gold-set gate
     *  does NOT yet exist). The CURRENT ceiling. */
    FAITHFUL_TO_SOURCE,

    /** Not (yet) cleared to faithful (unverified|pending|uncertain|failed): the badge pins at
     *  "unverified" and may claim NOTHING about correctness or source-faithfulness. */
    UNVERIFIED,
}

/**
 * §P — pure derivation of the honest floor from the resolved verification status
 * (CHANGE-10 / B8 table). NOT authored, NOT stored separately.
 * `faithful ⇒ FAITHFUL_TO_SOURCE`; every other status ⇒ `UNVERIFIED`.
 */
fun honestFloorOf(status: VerificationStatus): HonestFloor =
    when (status) {
        VerificationStatus.faithful -> HonestFloor.FAITHFUL_TO_SOURCE
        VerificationStatus.unverified,
        VerificationStatus.pending,
        VerificationStatus.uncertain,
        VerificationStatus.failed -> HonestFloor.UNVERIFIED
    }
