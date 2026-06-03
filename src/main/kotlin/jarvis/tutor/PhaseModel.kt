package jarvis.tutor

/**
 * Phase of a KC for one learner. Stored in kc_mastery.phase as the lowercase
 * `name` (VARCHAR(16), never native enum). Backfill + queue/today + grade reply
 * all read these literals.
 *
 * Wire literal == name (lowercase). Four phases, in progression order.
 */
enum class Phase { intro, practice, retrieval, mastered }

/**
 * Pure, deterministic phase-transition model.
 * SOLE owner of kc_mastery.phase. Called INSIDE KcMasteryRepo.recordIn(tx,…)
 * in the same txn (B3), and replayed over (ewma_score, observations) at the CHANGE-2 backfill.
 *
 * Threshold choices (VERIFY AT BUILD comment, per data-model-lock §5 — thresholds not
 * explicitly stated in the spine/master plan for intro→practice or practice→retrieval;
 * derived here to be consistent with KcMastery.MASTERY_THRESHOLD=0.8, MIN_OBSERVATIONS=3):
 *
 *   intro     : obs < 2  OR  ewma < 0.50
 *               (cold or very early attempts — student still in acquisition mode)
 *   practice  : obs >= 2 AND ewma >= 0.50 AND NOT retrieval-eligible
 *               (student has seen enough, needs repeated practice)
 *   retrieval : obs >= 3 AND ewma >= 0.65 AND mastered == false
 *               (sufficient accuracy for closed-book retrieval; still needs consolidation)
 *   mastered  : mastered == true  (== ewma >= 0.8 && obs >= 3, per KcMastery constants)
 *
 * These thresholds are the BUILD-TIME pin. If the teaching-engine council revises them,
 * update both this comment and the PhaseModelTest golden table.
 */
object PhaseModel {

    // Thresholds — pinned at build time (see class KDoc above).
    private const val PRACTICE_EWMA_MIN = 0.50   // below this => intro regardless of obs
    private const val PRACTICE_OBS_MIN = 2        // below this => intro regardless of ewma
    private const val RETRIEVAL_EWMA_MIN = 0.65   // below this => practice (if obs ok)
    private const val RETRIEVAL_OBS_MIN = 3        // below this => practice (if ewma ok)

    /**
     * Pure. Returns [Phase] (never null).
     *
     * @param ewma         The KC's current EWMA score (== KcMastery.ewmaScore).
     * @param observations The KC's current observation count (== KcMastery.observations).
     * @param mastered     True iff ewma >= KcMastery.MASTERY_THRESHOLD && obs >= KcMastery.MIN_OBSERVATIONS.
     *                     Callers derive this from KcMastery.mastered; no re-derivation here.
     * @param current      The row's existing phase, or null for a never-phased / pre-migration row.
     *                     Null current resolves to intro (unless mastered == true).
     * @return The new Phase. mastered==true always returns Phase.mastered.
     *         A null current is treated as no existing phase (returned = computed from thresholds).
     *         No-regression: the returned phase is the raw computed phase (regression IS allowed when
     *         mastery genuinely drops below a threshold, e.g. retrieval->practice after bad runs).
     */
    fun transition(ewma: Double, observations: Int, mastered: Boolean, current: Phase?): Phase {
        // mastered flag wins unconditionally — checked before anything else
        if (mastered) return Phase.mastered

        // Compute phase from thresholds
        return when {
            observations < PRACTICE_OBS_MIN || ewma < PRACTICE_EWMA_MIN -> Phase.intro
            observations < RETRIEVAL_OBS_MIN || ewma < RETRIEVAL_EWMA_MIN -> Phase.practice
            else -> Phase.retrieval  // mastered==false keeps us off mastered
        }
    }
}
