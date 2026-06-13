package jarvis.tutor.grader

import kotlinx.serialization.Serializable

/**
 * The leg that produced (or could produce) a verdict — the audit trail (REQ-26).
 *
 * Ordering matters only for the routing table; the enum identity is what the
 * chain records in [ChainGradeResult.decidedBy] / [ChainGradeResult.degradedLegs].
 */
enum class GraderLegKind { NUMERIC_ORACLE, EXECUTION, RUBRIC, LLM_JUDGE }

/**
 * One per-item verdict — reused for proof sub-steps (REQ-2), trace steps (REQ-5)
 * and rubric G-items (REQ-16/17).
 */
@Serializable
data class ItemVerdict(
    val id: String,            // substep id / step index / rubric label "G1".."Gn"
    val label: String,         // learner-visible RO label
    val passed: Boolean,
    val points_earned: Double? = null,
    val points_max: Double? = null,
)

/**
 * The full verdict a [GraderChain] produces. [decidedBy] is the leg that actually
 * decided; [degradedLegs] are legs that were SKIPPED because they were disabled,
 * unavailable, or non-applicable (R-6-Q1 honesty — the reply must say which leg
 * decided and which were degraded).
 */
data class ChainGradeResult(
    val decidedBy: GraderLegKind,
    val correct: Boolean,
    val score: Double,
    val itemVerdicts: List<ItemVerdict>,
    val misconception: String?,
    val feedbackRo: String,
    val degradedLegs: List<GraderLegKind>,
)

/**
 * The input a chain grades. A given leg consumes only the fields it understands
 * (the oracle reads [expected]/[attempt]; the rubric leg reads [rubricItems]; the
 * execution leg reads [source]; the LLM leg uses [problemStatement] etc. through
 * its own integration). Carrying a superset keeps the chain leg-agnostic.
 */
data class GradeInput(
    val subject: String? = null,
    val problemStatement: String = "",
    /** The learner's submitted answer / proof text / code / trace value. */
    val attempt: String = "",
    /** Expected numeric/symbolic answer for the oracle leg (a plain numeric literal,
     *  or an `expr:`-prefixed symbolic expression). Null → oracle defers. */
    val expected: String? = null,
    /** Per-site numeric tolerance for the oracle leg; null → DEFAULT_ABS_TOL. */
    val tolerance: Double? = null,
    /** Rubric items the rubric leg grades — either bank-row-derived or request-derived
     *  (one common model, §0.9 Task-3 Step 5). Empty → rubric leg finds nothing machine-
     *  checkable and defers. */
    val rubricItems: List<RubricInput> = emptyList(),
    /** Source program for the execution leg (set on code surfaces only). */
    val source: String? = null,
    val expectedStdout: String? = null,
    val language: String? = null,
)

/**
 * The outcome a single leg returns: either it DECIDED the grade, or it DEFERS
 * (not applicable / disabled / no machine-checkable evidence) so the chain falls
 * through to the next leg and records the degradation.
 */
sealed interface LegOutcome {
    data class Decided(
        val correct: Boolean,
        val score: Double,
        val itemVerdicts: List<ItemVerdict> = emptyList(),
        val misconception: String? = null,
        val feedbackRo: String,
    ) : LegOutcome

    /** [reason] is logged + can surface in RO copy via the chain integration. */
    data class Defer(val reason: String) : LegOutcome
}

/** One link in the chain. [kind] drives the builder's type-level invariants. */
interface GraderLeg {
    val kind: GraderLegKind
    suspend fun grade(input: GradeInput): LegOutcome
}

/**
 * The ordered grader chain (E16). Walks its legs in order; the first leg that
 * DECIDES wins and is reported in [ChainGradeResult.decidedBy]; every leg that
 * DEFERS before it is recorded in [ChainGradeResult.degradedLegs].
 *
 * Construction enforces (type-level, INV-6.1/INV-6.3):
 *  - the leg list is non-empty,
 *  - the FIRST leg is never [GraderLegKind.LLM_JUDGE] (INV-6.1),
 *  - the list is never `[LLM_JUDGE]` alone (INV-6.3).
 */
class GraderChain private constructor(private val legs: List<GraderLeg>) {

    val legKinds: List<GraderLegKind> get() = legs.map { it.kind }

    /**
     * Walk the legs in order. A leg that DEFERS is added to [degradedLegs] and the
     * chain falls through; the first leg that DECIDES produces the result. If EVERY
     * leg defers, the last leg's deferral becomes an honest no-decision fallback
     * (score 0, not-correct) — but on a well-formed chain the LLM_JUDGE tail always
     * decides, so this is a defensive floor, not a normal path.
     */
    suspend fun grade(input: GradeInput): ChainGradeResult {
        val degraded = mutableListOf<GraderLegKind>()
        for (leg in legs) {
            when (val outcome = leg.grade(input)) {
                is LegOutcome.Decided -> return ChainGradeResult(
                    decidedBy = leg.kind,
                    correct = outcome.correct,
                    score = outcome.score,
                    itemVerdicts = outcome.itemVerdicts,
                    misconception = outcome.misconception,
                    feedbackRo = outcome.feedbackRo,
                    degradedLegs = degraded.toList(),
                )
                is LegOutcome.Defer -> degraded.add(leg.kind)
            }
        }
        // Every leg deferred — honest no-decision floor.
        return ChainGradeResult(
            decidedBy = legs.last().kind,
            correct = false,
            score = 0.0,
            itemVerdicts = emptyList(),
            misconception = null,
            feedbackRo = "Niciun corector disponibil nu a putut evalua acest răspuns.",
            degradedLegs = degraded.dropLast(1),
        )
    }

    companion object {
        /**
         * Builder. THROWS on: empty legs; first leg == LLM_JUDGE (INV-6.1);
         * legs == [LLM_JUDGE] alone (INV-6.3).
         */
        fun of(legs: List<GraderLeg>): GraderChain {
            require(legs.isNotEmpty()) { "GraderChain requires at least one leg (INV-6.1)." }
            require(legs.first().kind != GraderLegKind.LLM_JUDGE) {
                "GraderChain's first leg must never be LLM_JUDGE (INV-6.1): got ${legs.map { it.kind }}"
            }
            require(!(legs.size == 1 && legs.first().kind == GraderLegKind.LLM_JUDGE)) {
                "GraderChain must never be [LLM_JUDGE] alone (INV-6.3)."
            }
            return GraderChain(legs)
        }
    }
}
