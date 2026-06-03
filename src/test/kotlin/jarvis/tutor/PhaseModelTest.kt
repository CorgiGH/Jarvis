package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD tests for PhaseModel.transition (Task 5, signatures-lock §A).
 * Written FIRST before implementation; run these to see RED, then implement to get GREEN.
 *
 * Threshold choices (VERIFY AT BUILD — no explicit numeric thresholds in the spine or master plan
 * for intro→practice and practice→retrieval; only mastered = ewma≥0.8 && obs≥3 is locked).
 * Derived thresholds consistent with the mastery model:
 *   intro     : obs < 2  OR  ewma < 0.50
 *   practice  : obs >= 2 AND ewma >= 0.50 AND NOT retrieval-eligible
 *   retrieval : obs >= 3 AND ewma >= 0.65 AND NOT mastered
 *   mastered  : mastered == true  (== ewma≥0.8 && obs≥3, per KcMastery.MASTERY_THRESHOLD/MIN_OBSERVATIONS)
 *
 * Regression IS allowed (DECISION, council 1780526710): there is NO no-regression floor.
 * The returned phase is the raw phase computed from (ewma, observations) — it MAY be LOWER
 * than `current` when mastery genuinely decays (FSRS is a forgetting model; a learner whose
 * accuracy collapses SHOULD drop retrieval→practice so the tutor stops drilling closed-book
 * recall on decayed knowledge). `current` is consumed ONLY for the null→intro resolution; the
 * `mastered` flag is the sole floor (mastered==true ⇒ Phase.mastered unconditionally).
 * There is no max(computed, current) anywhere — no test asserts one.
 */
class PhaseModelTest {

    // ──────────────────────────────────────────────
    // Group 1 — null current always resolves to intro
    // ──────────────────────────────────────────────

    @Test
    fun `null_current_resolves_to_intro`() {
        assertEquals(Phase.intro, PhaseModel.transition(0.0, 0, false, null))
    }

    @Test
    fun `null_current_high_mastery_resolves_to_mastered`() {
        // mastered==true supersedes null current
        assertEquals(Phase.mastered, PhaseModel.transition(0.9, 5, true, null))
    }

    // ──────────────────────────────────────────────
    // Group 2 — mastered flag immediately yields mastered
    // ──────────────────────────────────────────────

    @Test
    fun `mastered_true_from_retrieval_yields_mastered`() {
        assertEquals(Phase.mastered, PhaseModel.transition(0.85, 3, true, Phase.retrieval))
    }

    @Test
    fun `mastered_true_from_practice_yields_mastered`() {
        assertEquals(Phase.mastered, PhaseModel.transition(0.9, 4, true, Phase.practice))
    }

    @Test
    fun `mastered_true_from_intro_yields_mastered`() {
        assertEquals(Phase.mastered, PhaseModel.transition(0.85, 3, true, Phase.intro))
    }

    @Test
    fun `mastered_true_from_null_yields_mastered`() {
        assertEquals(Phase.mastered, PhaseModel.transition(0.85, 3, true, null))
    }

    // ──────────────────────────────────────────────
    // Group 3 — intro boundary cases
    // ──────────────────────────────────────────────

    @Test
    fun `cold_KC_zero_obs_zero_ewma_is_intro`() {
        assertEquals(Phase.intro, PhaseModel.transition(0.0, 0, false, null))
    }

    @Test
    fun `one_obs_any_ewma_stays_intro`() {
        // obs < 2 => intro regardless of ewma
        assertEquals(Phase.intro, PhaseModel.transition(1.0, 1, false, null))
    }

    @Test
    fun `two_obs_but_low_ewma_stays_intro`() {
        // ewma < 0.50 even with obs==2 => intro
        assertEquals(Phase.intro, PhaseModel.transition(0.3, 2, false, null))
    }

    // ──────────────────────────────────────────────
    // Group 4 — practice boundary cases
    // ──────────────────────────────────────────────

    @Test
    fun `two_obs_ewma_0_5_is_practice`() {
        // obs==2 AND ewma==0.50 => practice
        assertEquals(Phase.practice, PhaseModel.transition(0.5, 2, false, null))
    }

    @Test
    fun `two_obs_ewma_0_6_is_practice`() {
        // obs==2 AND ewma==0.60 < 0.65 retrieval threshold => practice
        assertEquals(Phase.practice, PhaseModel.transition(0.6, 2, false, null))
    }

    @Test
    fun `three_obs_ewma_0_55_is_practice`() {
        // obs==3 AND ewma==0.55 < 0.65 retrieval threshold => practice
        assertEquals(Phase.practice, PhaseModel.transition(0.55, 3, false, null))
    }

    // ──────────────────────────────────────────────
    // Group 5 — retrieval boundary cases
    // ──────────────────────────────────────────────

    @Test
    fun `three_obs_ewma_0_65_is_retrieval`() {
        // obs==3 AND ewma==0.65 => retrieval (mastered==false)
        assertEquals(Phase.retrieval, PhaseModel.transition(0.65, 3, false, null))
    }

    @Test
    fun `four_obs_ewma_0_75_is_retrieval`() {
        // ewma<0.8 => not mastered; obs>=3 && ewma>=0.65 => retrieval
        assertEquals(Phase.retrieval, PhaseModel.transition(0.75, 4, false, null))
    }

    @Test
    fun `three_obs_ewma_just_below_mastery_is_retrieval`() {
        // ewma=0.79 < 0.80, obs=3 => mastered==false; ewma>=0.65 && obs>=3 => retrieval
        assertEquals(Phase.retrieval, PhaseModel.transition(0.79, 3, false, null))
    }

    // ──────────────────────────────────────────────
    // Group 6 — regression IS allowed (no floor); `mastered` is the sole floor
    // (DECISION, council 1780526710 — these tests assert the FROZEN behavior:
    //  the returned phase is the raw computed phase, NOT max(computed, current).)
    // ──────────────────────────────────────────────

    @Test
    fun `phase_regresses_retrieval_to_practice_when_ewma_decays`() {
        // FROZEN-BEHAVIOR boundary test (council 1780526710): a KC currently at a HIGH phase
        // (retrieval) whose (ewma, observations) now compute to a LOWER phase MUST regress to the
        // LOWER computed phase — there is NO max(computed, current) floor. ewma=0.55 < 0.65
        // retrieval threshold (obs=3) ⇒ computed=practice ⇒ returns practice, NOT retrieval.
        assertEquals(Phase.practice, PhaseModel.transition(0.55, 3, false, Phase.retrieval))
    }

    @Test
    fun `phase_regresses_retrieval_to_intro_when_accuracy_collapses`() {
        // Deeper collapse from a high current phase: ewma=0.2 < 0.50 ⇒ computed=intro.
        // Asserts regression spans more than one bucket — current=retrieval ⇒ returns intro.
        assertEquals(Phase.intro, PhaseModel.transition(0.2, 5, false, Phase.retrieval))
    }

    @Test
    fun `mastered_flag_is_the_sole_floor_regardless_of_current`() {
        // mastered==true ⇒ Phase.mastered unconditionally, even from a LOW current phase and
        // regardless of `current`. This is the ONLY floor (line-56 `if (mastered) return mastered`).
        assertEquals(Phase.mastered, PhaseModel.transition(0.95, 6, true, Phase.intro))
        assertEquals(Phase.mastered, PhaseModel.transition(0.95, 6, true, null))
        assertEquals(Phase.mastered, PhaseModel.transition(0.95, 6, true, Phase.retrieval))
    }

    @Test
    fun `phase_does_not_regress_from_practice_to_intro_when_still_meeting_practice_threshold`() {
        // current=practice; obs=2, ewma=0.5 => computed=practice => no change
        assertEquals(Phase.practice, PhaseModel.transition(0.5, 2, false, Phase.practice))
    }

    @Test
    fun `phase_regresses_from_practice_to_intro_when_obs_drops_below_threshold`() {
        // obs=1 < 2 => computed=intro; regression is allowed (mastery genuinely dropped)
        assertEquals(Phase.intro, PhaseModel.transition(0.8, 1, false, Phase.practice))
    }

    @Test
    fun `mastered_phase_cannot_be_reached_via_current_no_regression_only`() {
        // mastered flag false => mastered phase never returned
        val result = PhaseModel.transition(0.79, 10, false, Phase.mastered)
        // mastered==false so result should NOT be mastered; drops to retrieval (ewma>=0.65, obs>=3)
        assertEquals(Phase.retrieval, result)
    }

    // ──────────────────────────────────────────────
    // Group 7 — pure / deterministic (same inputs = same output)
    // ──────────────────────────────────────────────

    @Test
    fun `pure_same_inputs_same_output`() {
        val a = PhaseModel.transition(0.7, 4, false, Phase.practice)
        val b = PhaseModel.transition(0.7, 4, false, Phase.practice)
        assertEquals(a, b)
    }

    @Test
    fun `pure_null_current_same_inputs_same_output`() {
        val a = PhaseModel.transition(0.0, 0, false, null)
        val b = PhaseModel.transition(0.0, 0, false, null)
        assertEquals(a, b)
    }
}
