package jarvis.tutor

import jarvis.content.Misconception
import jarvis.content.SourceRef
import jarvis.tutor.verify.CitationGuard
import jarvis.tutor.verify.ClaimKind
import jarvis.tutor.verify.VerificationClaim
import kotlinx.serialization.Serializable

/**
 * Phase-3 GROUP 7 (SERVE WIRING) — the served teaching payload that rides INLINE on the
 * `/drill/grade` reply so the Phase-5 surfaces render from SERVED data, never local re-derivation.
 *
 * Frozen shapes: `interface-signatures-lock.md` §O (MisconceptionPayload / LadderRung) + §B
 * (NextPhaseAction). ADDITIVE to the grade reply DTO only — the existing G2 grade shape
 * (correct/score/rubric/misconception/elaboratedFeedback/confidence/recorded/answerMatch/
 * kc_quarantined) is untouched.
 *
 * H16 (no ghosts): every field below is POPULATED from stored content (the graded KC's
 * Misconception + KnowledgeConcept teaching fields) AND has a NAMED Phase-5 consumer (the spine
 * surface). Absent stored content ⇒ null / empty, never a throw, never an always-null ghost.
 */

/**
 * The misconception payload that rides INLINE on the `/drill/grade` reply (gap C, DECIDED #1 —
 * INLINE-only; the standalone `GET /misconception/{id}` endpoint is DROPPED as a no-consumer orphan).
 *
 * Present (non-null) ONLY when the graded answer matched a known stored Misconception for one of the
 * server-resolved kcIds; `null` otherwise. Fields 1:1 with the CHANGE-3 `Misconception` columns.
 *
 * NAMED Phase-5 consumer: `MisconceptionRibbon` (Surface 0f, mounted at ladder L4) reads
 * `refutation` + `figure_spec` (TS maps wire `figure_spec` → prop `figureSpec` at the fetch boundary).
 *
 * Lock §O — frozen field names serialize snake_case exactly as written.
 */
@Serializable
data class MisconceptionPayload(
    /** Misconception.id — the per-KC misconception id (M-MISC-ENUM). */
    val id: String,
    /** Misconception.refutation — the refutation body text. */
    val refutation: String,
    /** CHANGE-3 Misconception.figure_spec; null ⇒ ribbon renders its FIG. NOT YET WIRED degraded state. */
    val figure_spec: String?,
    /** CHANGE-3 Misconception.self_explanation_prompt (per-misconception Chi/Renkl prompt); null ⇒ none. */
    val self_explanation_prompt: String?,
    /**
     * P0-2 (re-open fix) — the verbatim citation backing this served refutation, attached by the
     * `CitationGuard.attach` chokepoint (§Q write-site 2 / P2-RULE8). NON-NULL on the cited serve path
     * ([fromCited]): a misconception with no resolved [SourceRef] never becomes a payload (attach throws
     * first — FAIL-LOUD, never ships un-cited). ADDITIVE to the frozen §O shape (lock amended in the same
     * change); defaults null so the legacy [from] builder and existing wire-shape callers are untouched.
     */
    val source: SourceRef? = null,
) {
    companion object {
        /**
         * LEGACY un-cited builder — build the inline payload from a stored [Misconception], or null when
         * none matched. RETAINED for non-learner-facing / test call-sites only; the SERVE path MUST use
         * [fromCited] so the refutation crosses the citation chokepoint (P2-RULE8). `source` is left null.
         */
        fun from(m: Misconception?): MisconceptionPayload? = m?.let {
            MisconceptionPayload(
                id = it.id,
                refutation = it.refutation,
                figure_spec = it.figure_spec,
                self_explanation_prompt = it.self_explanation_prompt,
            )
        }

        /**
         * P0-2 (re-open fix) — the CITED serve builder. Routes the matched misconception's refutation
         * through the [CitationGuard.attach] chokepoint (interface-signatures-lock §Q write-site 2 /
         * P2-RULE8) carrying the misconception's [SourceRef], so the served refutation ships CITED and
         * FAIL-LOUD if it has no resolved source (attach THROWS — a learner-facing refutation must NEVER
         * reach the surface un-cited). Returns null when [m] is null (no match ⇒ no payload, no throw).
         *
         * The 1-arg `attach` form is used deliberately: a misconception refutation is NOT routed through
         * the `VerificationRunner` (it is never in `claimsFor`), so there is no audited status in hand —
         * the chokepoint pins `uncertain` (F3: span-presence must not launder trust into `faithful`). The
         * payload's §O wire shape carries the citation but no status (the trust badge rides the KC-level
         * `verification_status` on the grade reply, not the per-claim emit).
         */
        fun fromCited(m: Misconception?): MisconceptionPayload? = m?.let { misc ->
            // Build the MISCONCEPTION_REFUTATION claim, anchored on the misconception's first source ref.
            // A null source ⇒ attach() throws (FAIL-LOUD, never ships un-cited) — the locked behavior.
            val claim = VerificationClaim(
                claimId = "${misc.kc_id}:${ClaimKind.MISCONCEPTION_REFUTATION.name}:${misc.id}",
                kcId = misc.kc_id,
                subject = "", // not on the wire payload; the claim is built only to cross the chokepoint.
                kind = ClaimKind.MISCONCEPTION_REFUTATION,
                content = misc.refutation,
                invariant = null,
                source = misc.source.firstOrNull(),
            )
            val cited = CitationGuard.attach(claim) // FAIL-LOUD on a null/unresolved source (§Q / P2-RULE8).
            MisconceptionPayload(
                id = misc.id,
                refutation = misc.refutation,
                figure_spec = misc.figure_spec,
                self_explanation_prompt = misc.self_explanation_prompt,
                source = cited.source, // NON-NULL by construction (attach narrowed null→non-null).
            )
        }

        /**
         * Match the grader's free-text misconception CODE (e.g. "OFF_BY_ONE", "L2_ESTIMATOR_CONFUSION")
         * against the KC's stored [candidates]. Generic / non-content codes ("OTHER", "UNGRADED", null)
         * never match. The match is case-insensitive on the misconception `id`, `label_en`, or `label_ro`
         * (equality OR substring either way) so an authored code lines up with the stored row. Returns
         * the first matching stored [Misconception], or null (⇒ no inline payload — H16 no ghost).
         */
        fun matchByGraderCode(graderCode: String?, candidates: List<Misconception>): Misconception? {
            val code = graderCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (code.equals("OTHER", true) || code.equals("UNGRADED", true) || code.equals("null", true)) return null
            fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
            val n = norm(code)
            if (n.isEmpty()) return null
            return candidates.firstOrNull { m ->
                val keys = listOf(m.id, m.label_en, m.label_ro).map(::norm)
                keys.any { it.isNotEmpty() && (it == n || it.contains(n) || n.contains(it)) }
            }
        }
    }
}

/**
 * One rendered feedback-ladder rung (L0–L4). The grade reply carries the FULL ordered rung array so
 * `FeedbackLadder` (mounted in `DrillStack`, Phase 5) renders the L0→L4 reveal from SERVED text rather
 * than re-deriving rung copy client-side (gap C, TASK P3-LADDER-SERVE). Lock §O.
 *
 * NAMED Phase-5 consumer: `FeedbackLadder` (in `DrillStack`) reads `ladder_rungs`.
 */
@Serializable
data class LadderRung(
    /** 0..4 — the scaffold/reveal level (L0 = lightest nudge … L4 = full). */
    val level: Int,
    /** The rendered rung copy for this level. */
    val text: String,
)

/**
 * The next-phase action surfaced on the `/drill/grade` reply (interface-signatures-lock §B). A SEPARATE
 * small enum, NOT part of `Phase`. Wire literal == name (lowercase).
 *
 * NAMED Phase-5 consumer: `DrillStack` / `FeedbackLadder` advance vs hold vs remediate UI.
 */
@Serializable
enum class NextPhaseAction { advance, hold, remediate }
