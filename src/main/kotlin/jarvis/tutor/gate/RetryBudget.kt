package jarvis.tutor.gate

/**
 * Spec §9.1 — "the retry budget is a constant in config, not a vibe."
 *
 * Consumed by Plan 5's digestion pipeline (R-4b-Q3 split: seam + proof here,
 * REJECT-loop UI with Plan 5's checkpoint screen).
 *
 * NOT dead config — Plan 5 builds against exactly these names:
 *   [MACHINE_GATE_SELF_RETRIES] → the digestion pipeline's gate-retry loop
 *   [USER_REJECT_REGENERATIONS] → the checkpoint-screen regeneration counter
 *   [rejectAction]              → the checkpoint-screen branching logic
 *   [RetryLoop]                 → the gate-retry seam wired in Plan 5
 *   [GapRecordSink]             → Plan 5 wires its gap-record store; tests use
 *                                  an in-memory sink (λ)
 */
object RetryBudget {
    /** §9.1 row (a): machine gate red → ≤3 self-retries, failure data fed back; still red → park. */
    const val MACHINE_GATE_SELF_RETRIES: Int = 3

    /** §9.1 row (b): user REJECT #1 → exactly one re-generation incorporating the one-line note. */
    const val USER_REJECT_REGENERATIONS: Int = 1

    /** §9.1 rows (b)+(c): REJECT #2 on the same artifact → STOP (checkpoint-design-review, §10.3). */
    fun rejectAction(rejectCount: Int): RejectAction =
        if (rejectCount <= 1) RejectAction.REGENERATE_ONCE else RejectAction.STOP_DESIGN_REVIEW
}

enum class RejectAction { REGENERATE_ONCE, STOP_DESIGN_REVIEW }

/** One gate-attempt record, carried in the park payload so Plan 5 can surface failure data. */
data class GateAttempt(val attempt: Int, val failureData: String)

/**
 * Park-as-gap-record seam: Plan 5 wires its gap-record store; tests use an in-memory sink.
 *
 * Called by [RetryLoop] after all budget retries are exhausted. Parameters:
 *   [artifactId] — the thing being generated/graded (e.g. a drill id or KC slug)
 *   [gate]       — the name of the gate that kept failing (e.g. "machine-grade")
 *   [attempts]   — the full ordered list of [GateAttempt]s (size == budget at exhaustion)
 */
fun interface GapRecordSink {
    fun park(artifactId: String, gate: String, attempts: List<GateAttempt>)
}

/**
 * Gate-retry loop per spec §9.1 row (a).
 *
 * Runs [attempt] up to [budget] times, feeding the prior failure message into each retry.
 * On success returns the value immediately (no park, no extra calls).
 * On budget exhaustion, calls [sink.park] with the full attempt list and returns null.
 *
 * Plan 5 consumption contract:
 *   val loop = RetryLoop(RetryBudget.MACHINE_GATE_SELF_RETRIES, gapRecordStore)
 *   val result: T? = loop.run(artifactId, gateName) { priorFailure -> … }
 */
class RetryLoop(private val budget: Int, private val sink: GapRecordSink) {

    /**
     * Runs [attempt] with the prior failure fed back (§9.1 row a); parks at budget exhaustion.
     *
     * @param artifactId  Identifies the artefact being generated (forwarded to [sink])
     * @param gate        Gate name (forwarded to [sink])
     * @param attempt     Lambda receiving the prior failure message (null on first call);
     *                    returns [Result.success] on pass, [Result.failure] on fail.
     * @return            The successful value, or null if all [budget] attempts failed.
     */
    fun <T> run(artifactId: String, gate: String, attempt: (priorFailure: String?) -> Result<T>): T? {
        val history = mutableListOf<GateAttempt>()
        var priorFailure: String? = null

        for (i in 1..budget) {
            val result = attempt(priorFailure)
            if (result.isSuccess) {
                return result.getOrThrow()
            }
            val failureMessage = result.exceptionOrNull()?.message ?: "failure at attempt $i"
            history.add(GateAttempt(attempt = i, failureData = failureMessage))
            priorFailure = failureMessage
        }

        sink.park(artifactId, gate, history)
        return null
    }
}
