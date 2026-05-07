package jarvis

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * FSRS-5-lite: simplified Free Spaced Repetition Scheduler. Replaces the
 * Ebbinghaus exp-decay (council Domain Expert verdict 2026-05-08).
 *
 * Three-component memory model:
 *   D — Difficulty (1..10): how hard the concept is for THIS user.
 *   S — Stability (days): how long memory persists at high retrievability.
 *   R — Retrievability (0..1): probability of correct recall right now.
 *       R = 0.9 ^ (elapsedDays / S)
 *
 * After a review, S grows (or shrinks if the user grades themselves "hard").
 * Difficulty drifts based on recall outcome.
 *
 * Inputs to update():
 *   prior state (D, S, lastReview)
 *   grade — 1=again, 2=hard, 3=good, 4=easy
 *   now
 *
 * Output: new (D, S, lastReview, nextDueAt). Caller persists.
 *
 * Design choices:
 * - Defaults from FSRS-5 paper Wozniak/Lipinski/Su 2024 + open-spaced-rep.
 * - Single-day granularity (no sub-day intervals); fine for student use.
 * - No fancy parameter optimization — uses the published defaults.
 * - Pure function; no IO. Caller wires to KnowledgeState.
 */
object Fsrs {

    /** FSRS-5 default parameters (slight simplification — full FSRS-5 has 21
     *  weights; we use a 7-weight subset that captures the dominant dynamics). */
    private val W = doubleArrayOf(
        0.4, 0.6, 2.4, 5.8,    // initial S by grade 1..4
        4.93, 0.94, 0.86,       // D drift weights
    )
    private const val REQUEST_RETENTION = 0.9
    private val FACTOR = -ln(REQUEST_RETENTION) // for R formula

    data class State(
        val difficulty: Double,    // 1..10
        val stability: Double,     // days
        val lastReviewDayOffset: Double = 0.0,  // tracking only
    )

    /** Initial state on first-ever review with [grade]. */
    fun initial(grade: Int): State {
        val g = grade.coerceIn(1, 4)
        val s = W[g - 1].coerceAtLeast(0.1)
        // Difficulty starts higher when user grades "again", lower for "easy".
        val d = (W[4] - 1.0 * (g - 3)).coerceIn(1.0, 10.0)
        return State(difficulty = d, stability = s, lastReviewDayOffset = 0.0)
    }

    /** Update after [grade] given prior [state] and [elapsedDays] since last review. */
    fun update(state: State, grade: Int, elapsedDays: Double): State {
        val g = grade.coerceIn(1, 4)
        // Retrievability before this review.
        val r = retrievability(state.stability, elapsedDays)
        // New difficulty: drifts toward higher when grade low.
        val nextD = (state.difficulty - W[5] * (g - 3)).coerceIn(1.0, 10.0)
        // New stability: success cases grow; failure shrinks toward 1 day.
        val nextS = if (g == 1) {
            // Lapse: short re-stabilization.
            max(0.1, state.stability * 0.2)
        } else {
            val factor = 1.0 + W[6] * (11 - nextD) * state.stability.pow(-0.5) *
                (exp(1 - r) - 1) * gradeBoost(g)
            (state.stability * factor).coerceAtMost(36500.0) // cap 100 yrs
        }
        return State(
            difficulty = nextD,
            stability = nextS,
            lastReviewDayOffset = state.lastReviewDayOffset + elapsedDays,
        )
    }

    /** Days until next review at [REQUEST_RETENTION] retention target. */
    fun nextIntervalDays(state: State): Double =
        state.stability * (ln(REQUEST_RETENTION) / FACTOR / -1.0)
            // simplifies to S * 1.0; fixed because REQUEST_RETENTION uses log-base-0.9
            .coerceAtLeast(1.0 / 24.0)

    fun retrievability(stability: Double, elapsedDays: Double): Double =
        REQUEST_RETENTION.pow(elapsedDays / stability.coerceAtLeast(0.1))

    private fun gradeBoost(grade: Int): Double = when (grade) {
        2 -> 0.5
        3 -> 1.0
        4 -> 1.3
        else -> 1.0
    }
}
