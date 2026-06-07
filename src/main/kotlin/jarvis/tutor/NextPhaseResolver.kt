package jarvis.tutor

/**
 * Phase-3 GROUP 7 — pure resolver of the `next_phase_action` served on the `/drill/grade` reply
 * (interface-signatures-lock §B: `enum NextPhaseAction { advance, hold, remediate }`).
 *
 * It compares the learner's phase BEFORE this grade with the phase recorded AFTER (both computed by
 * the SOLE owner `PhaseModel.transition`, so this resolver never re-derives a phase). The action is a
 * thin presentation hint for `DrillStack` / `FeedbackLadder` — it does NOT itself write any state.
 *
 *   advance   — the recorded phase moved FORWARD (intro→practice→retrieval→mastered), or the answer
 *               was correct at the same phase: the learner progresses.
 *   hold       — same phase, answer not correct enough to advance but not a regression: keep drilling.
 *   remediate — the recorded phase REGRESSED (mastery decayed below a threshold): step back / re-teach.
 *
 * Pure; no DB, no side effects.
 */
object NextPhaseResolver {

    private val ORDER = listOf(Phase.intro, Phase.practice, Phase.retrieval, Phase.mastered)
    private fun rank(p: Phase): Int = ORDER.indexOf(p)

    /**
     * @param before  the phase the KC was in before this grade (null ⇒ cold / pre-migration ⇒ intro).
     * @param after   the phase recorded after this grade (the [PhaseModel.transition] result).
     * @param correct whether this attempt was graded correct (drives advance-vs-hold at a flat phase).
     */
    fun resolve(before: Phase?, after: Phase, correct: Boolean): NextPhaseAction {
        val b = rank(before ?: Phase.intro)
        val a = rank(after)
        return when {
            a > b -> NextPhaseAction.advance
            a < b -> NextPhaseAction.remediate
            correct -> NextPhaseAction.advance
            else -> NextPhaseAction.hold
        }
    }
}
