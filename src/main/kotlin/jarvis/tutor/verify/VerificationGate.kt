package jarvis.tutor.verify

import jarvis.content.KnowledgeConcept
import jarvis.tutor.VerificationStatus

/**
 * §I2 — binary gate outcome. Wire-irrelevant (server-internal); ALLOW lets a KC enter
 * SR / cold-start seed / GapPromotion / the B1 card upsert, DENY blocks it.
 */
enum class GateDecision { ALLOW, DENY }

/**
 * §I2 — the ONE chokepoint for SR-entry / cold-start seed / GapPromotion.promote /
 * the B1 RUBRIC_CRITERION card upsert. Pure over its inputs (no DB read — the caller
 * does the B8-table read + the report-wrong lookup; the gate never touches the DB).
 *
 * ALLOW iff `status == faithful` (resolved from the kc_verification_status table, B8 —
 * NOT the YAML seed) AND no OPEN report_wrong for this kc. IGNORES student attempt
 * counts (no laundering by consistency, master-plan §2.3).
 *
 * Distinct from the per-claim citation chokepoint `CitationGuard.attach` (§Q): this is
 * the binary ALLOW|DENY KC-admission gate at SR-entry; that is the per-emit guard.
 */
object VerificationGate {
    fun gate(
        @Suppress("UNUSED_PARAMETER") kc: KnowledgeConcept,
        status: VerificationStatus,
        hasOpenReportWrong: Boolean,
    ): GateDecision =
        if (status == VerificationStatus.faithful && !hasOpenReportWrong) GateDecision.ALLOW
        else GateDecision.DENY
}
