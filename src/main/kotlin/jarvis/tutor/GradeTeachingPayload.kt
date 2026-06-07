package jarvis.tutor

import jarvis.content.Misconception
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
) {
    companion object {
        /** Build the inline payload from a stored [Misconception], or null when none matched. */
        fun from(m: Misconception?): MisconceptionPayload? = m?.let {
            MisconceptionPayload(
                id = it.id,
                refutation = it.refutation,
                figure_spec = it.figure_spec,
                self_explanation_prompt = it.self_explanation_prompt,
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
