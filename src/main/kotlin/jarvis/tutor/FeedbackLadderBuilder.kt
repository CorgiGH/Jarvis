package jarvis.tutor

import jarvis.content.KnowledgeConcept
import jarvis.content.Misconception

/**
 * Phase-3 GROUP 7 (TASK P3-LADDER-SERVE) — pure renderer of the L0–L4 feedback ladder from STORED
 * content, so `FeedbackLadder` (Phase 5, in `DrillStack`) renders the L0→L4 reveal from SERVED rungs
 * rather than re-deriving rung copy client-side.
 *
 * There is NO authored per-rung ladder text in the corpus (the schema stores teaching FIELDS, not
 * pre-rendered rungs). This builder composes the ordered ladder DETERMINISTICALLY from the stored
 * teaching content that DOES exist for the graded KC + its fired misconception:
 *
 *   L0  — lightest nudge: a generic "look again" rung (always present once a graded KC exists).
 *   L1  — the KC's self_explanation_prompt (Chi/Renkl), when stored.
 *   L2  — the fired misconception's trigger restatement, when a misconception matched.
 *   L3  — the fired misconception's refutation, when a misconception matched.
 *   L4  — the grader's elaborated feedback (the full reveal for THIS attempt), when present.
 *
 * H16 (no ghost): a rung is EMITTED only when its backing stored content is present; absent content
 * ⇒ that rung is omitted. `build` over a KC with NO teaching content + no misconception + no feedback
 * degrades to a single L0 rung (or empty when [kc] itself is null — nothing to scaffold).
 *
 * Pure; no DB, no side effects.
 */
object FeedbackLadderBuilder {

    /** Generic level-0 nudge copy (RO — learner-facing; matches the language-split rule). */
    private const val L0_NUDGE = "Mai uită-te o dată la enunț și la ce ai scris."

    /**
     * @param kc                 the graded KC (its self_explanation_prompt seeds L1), or null.
     * @param misconception      the fired stored misconception (seeds L2/L3), or null.
     * @param elaboratedFeedback the grader's full feedback for this attempt (seeds L4), or null/blank.
     * @return ordered L0..L4 rungs, omitting any rung whose stored backing is absent. Empty when
     *         [kc] is null (no KC ⇒ nothing to scaffold).
     */
    fun build(
        kc: KnowledgeConcept?,
        misconception: Misconception?,
        elaboratedFeedback: String?,
    ): List<LadderRung> {
        if (kc == null) return emptyList()
        val rungs = mutableListOf<LadderRung>()
        // L0 — always present once we have a KC to scaffold.
        rungs += LadderRung(0, L0_NUDGE)
        // L1 — the KC's self-explanation prompt, when authored.
        kc.self_explanation_prompt?.takeIf { it.isNotBlank() }?.let { rungs += LadderRung(1, it) }
        // L2 — restate the misconception trigger (what the student likely confused), when one fired.
        misconception?.trigger?.takeIf { it.isNotBlank() }?.let { rungs += LadderRung(2, it) }
        // L3 — the misconception refutation, when one fired.
        misconception?.refutation?.takeIf { it.isNotBlank() }?.let { rungs += LadderRung(3, it) }
        // L4 — the grader's elaborated feedback (full reveal for this attempt), when present.
        elaboratedFeedback?.takeIf { it.isNotBlank() }?.let { rungs += LadderRung(4, it) }
        return rungs
    }
}
