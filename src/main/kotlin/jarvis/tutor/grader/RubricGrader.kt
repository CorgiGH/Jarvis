package jarvis.tutor.grader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One rubric item the [RubricGrader] grades — the SINGLE common item model fed from
 * EITHER source (Task 3 Step 5):
 *   (a) `ProblemRubricItemsTable` rows via `ProblemsRepo` (bank problems), or
 *   (b) request/task_prep-derived rubric items (the dominant live `/drill/grade` traffic
 *       — `drillGrader.ts` `rubricItems` ride the request with NO problem-bank row).
 *
 * [matcherJson] / [penaltyJson] are the STRUCTURAL config (mirroring `penaltyRulesJson`
 * on the bank row). When [matcherJson] is null/blank the item is NOT machine-checkable —
 * the leg defers THAT item to the LLM-judge pairing and records the deferral.
 */
data class RubricInput(
    val id: String,
    val label: String,
    val points: Double,
    val kind: String = "AP",          // "AP" (answer-points) | "WP" (working-points)
    val allOrNothing: Boolean = false,
    /** Structural matcher: {"kind":"exact|contains|regex","value":"..."}. Null/blank → not machine-checkable. */
    val matcherJson: String? = null,
    /** Penalty rule applied ONCE if its pattern fires: {"kind":"contains|regex","value":"...","deduct":0.5}. */
    val penaltyJson: String? = null,
)

/**
 * The structural rubric leg. Grades per-G-item verdicts from STRUCTURAL matchers
 * (presence/shape) declared per item — NEVER from LLM-emitted booleans, for either
 * item source. An item with no machine-checkable matcher DEFERS to the LLM-judge
 * leg pairing (REQ-26); the deferral is recorded so the chain's `decided_by`/
 * `degraded_legs` stays honest.
 *
 * If NO item in the input is machine-checkable, the WHOLE leg defers (the chain falls
 * through to the LLM-judge tail — the dominant prose-drill path).
 */
class RubricGrader : GraderLeg {

    override val kind: GraderLegKind = GraderLegKind.RUBRIC

    override suspend fun grade(input: GradeInput): LegOutcome {
        val items = input.rubricItems
        if (items.isEmpty()) return LegOutcome.Defer("no rubric items supplied")

        val machineCheckable = items.filter { !it.matcherJson.isNullOrBlank() }
        if (machineCheckable.isEmpty()) {
            return LegOutcome.Defer("no machine-checkable rubric items — deferring to LLM judge")
        }

        val attempt = input.attempt
        val verdicts = mutableListOf<ItemVerdict>()
        var earnedTotal = 0.0
        var maxTotal = 0.0
        var allMachinePassed = true

        for (item in machineCheckable) {
            maxTotal += item.points
            val passed = matchItem(item, attempt)
            var earned = if (passed) item.points else 0.0

            // Penalty: fires ONCE (not per-line) if its pattern is present.
            if (passed) {
                val deduct = penaltyDeduction(item, attempt)
                if (deduct > 0.0) {
                    earned = if (item.allOrNothing) 0.0 else (earned - deduct).coerceAtLeast(0.0)
                }
            }
            // allOrNothing already handled: passed→full, failed→0; penalty above.

            if (earned < item.points) allMachinePassed = false
            earnedTotal += earned
            verdicts.add(
                ItemVerdict(
                    id = item.id,
                    label = item.label,
                    passed = passed && earned >= item.points,
                    points_earned = earned,
                    points_max = item.points,
                ),
            )
        }

        val score = if (maxTotal > 0.0) earnedTotal / maxTotal else 0.0
        val correct = allMachinePassed && machineCheckable.size == items.size
        // If some items were NOT machine-checkable, the leg cannot fully decide correctness
        // (those items still need the LLM pairing) — record the partial decision honestly.
        val deferredCount = items.size - machineCheckable.size
        val feedback = if (deferredCount == 0) {
            if (correct) RUBRIC_ALL_PASS_RO else RUBRIC_PARTIAL_RO
        } else {
            RUBRIC_PARTIAL_WITH_DEFER_RO
        }

        // When items remain that only the LLM can judge, the rubric leg still DECIDES
        // the structural portion (its per-item verdicts ride the result); the chain
        // surfaces the LLM-pairing need through the integration layer. We decide here so
        // the machine-checkable verdicts are not lost; a pure prose drill (all items
        // non-checkable) already deferred above.
        return LegOutcome.Decided(
            correct = correct,
            score = score,
            itemVerdicts = verdicts,
            feedbackRo = feedback,
        )
    }

    private fun matchItem(item: RubricInput, attempt: String): Boolean {
        val matcher = parseMatcher(item.matcherJson) ?: return false
        return applyMatcher(matcher, attempt)
    }

    private fun penaltyDeduction(item: RubricInput, attempt: String): Double {
        val pj = item.penaltyJson
        if (pj.isNullOrBlank()) return 0.0
        val penalty = try {
            json.decodeFromString(PenaltyRule.serializer(), pj)
        } catch (_: Exception) {
            return 0.0
        }
        val fired = applyMatcher(Matcher(penalty.kind, penalty.value), attempt)
        return if (fired) penalty.deduct else 0.0
    }

    private fun parseMatcher(matcherJson: String?): Matcher? {
        if (matcherJson.isNullOrBlank()) return null
        return try {
            json.decodeFromString(Matcher.serializer(), matcherJson)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyMatcher(m: Matcher, attempt: String): Boolean = when (m.kind) {
        "exact" -> attempt.trim() == m.value.trim()
        "contains" -> attempt.contains(m.value)
        "regex" -> try {
            Regex(m.value).containsMatchIn(attempt)
        } catch (_: Exception) {
            false
        }
        else -> false
    }

    @Serializable
    private data class Matcher(val kind: String, val value: String)

    @Serializable
    private data class PenaltyRule(val kind: String, val value: String, val deduct: Double)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val RUBRIC_ALL_PASS_RO = "Toate criteriile de evaluare sunt îndeplinite."
        private const val RUBRIC_PARTIAL_RO = "Unele criterii nu sunt îndeplinite — vezi detaliile pe fiecare punct."
        private const val RUBRIC_PARTIAL_WITH_DEFER_RO =
            "Criteriile verificabile automat au fost evaluate; restul necesită verificare suplimentară."
    }
}
