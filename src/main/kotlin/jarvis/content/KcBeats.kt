package jarvis.content

import kotlinx.serialization.Serializable

/** One language version of the 5-beat teaching content (spec §3.2). Keyed by language in KnowledgeConcept.beats. */
@Serializable
data class KcBeats(
    val predict: BeatPredict? = null,
    val attempt: BeatAttempt? = null,
    val reveal: BeatReveal? = null,
    val name: BeatName? = null,
    val check: BeatCheck? = null,
) {
    /**
     * INV-3.2 structural minimum: beats ①②③⑤ present + each beat's own minimum fields,
     * with the §4.3 numerical-variant requirement for skeleton concept types.
     * Beat ④ (name) is optional at the schema level (STANDARD plan omits it, spec §4.5).
     */
    fun isCompleteFor(conceptType: ConceptType): Boolean {
        val p = predict ?: return false
        val a = attempt ?: return false
        val r = reveal ?: return false
        val c = check ?: return false
        if (p.prompt.isBlank() || p.options.size !in 3..4 || p.options.any { it.callback.isBlank() }) return false
        if (p.options.none { it.correct }) return false
        val numerical = conceptType in setOf(
            ConceptType.PROCEDURE, ConceptType.FORMULA_APPLICATION, ConceptType.PROBABILISTIC,
        )
        if (a.statement.isBlank() || a.feedback_correct.isBlank()) return false
        if (numerical) {
            if (a.skeleton_rows.isEmpty() || a.trace_steps.isEmpty()) return false
        } else {
            if (a.choices.isEmpty() || a.choices.any { it.feedback.isBlank() }) return false
        }
        if (r.steps.isEmpty() || r.steps.any { it.text.isBlank() || it.callout.isBlank() }) return false
        if (c.item_stem.isBlank() || !c.hasGradingData()) return false
        return true
    }
}

@Serializable
data class PredictOption(
    val text: String,
    /** Echoed at reveal: "you predicted X — here is where that holds/breaks" (§3.2 ①). */
    val callback: String,
    val correct: Boolean = false,
)

@Serializable
data class BeatPredict(
    val prompt: String,
    val options: List<PredictOption>, // 3-4
)

@Serializable
data class AttemptChoice(
    val text: String,
    val correct: Boolean,
    /** Both-path feedback — wrong-path text TEACHES, never just "incorrect" (§3.2 ②). */
    val feedback: String,
)

/** §4.3: one named skeleton row; row count is INSTANCE DATA, never a hardcoded 4. */
@Serializable
data class SkeletonRow(
    val label: String,
    /** The formula-line revealed for this row; null for structural rows. */
    val formula: String? = null,
    /** The sign/decision step gets its own named row (§4.3) — mark it. */
    val is_decision_row: Boolean = false,
)

@Serializable
data class TraceStep(
    val row_index: Int,
    val value: String,
    val callout: String? = null,
)

@Serializable
data class BeatAttempt(
    val statement: String,
    /** Choice variant (non-numerical types). */
    val choices: List<AttemptChoice> = emptyList(),
    /** Numerical variant (§4.3): the skeleton + per-click trace. */
    val skeleton_rows: List<SkeletonRow> = emptyList(),
    val trace_steps: List<TraceStep> = emptyList(),
    /** Free-input schema for the numerical variant (JSON-schema-ish string); null for choice variant. */
    val input_schema: String? = null,
    /**
     * Numeric-variant expected answer + tolerance for the free-input ATTEMPT grade path
     * (mirrors BeatCheck:numeric_answer/numeric_tolerance). When present, the ATTEMPT grade
     * compares free_input numerically via NumericOracleGrader.matches; absent ⇒ the legacy
     * exact-string-vs-trace-final fallback (still dead on served choice-variant KCs).
     */
    val numeric_answer: String? = null,
    val numeric_tolerance: Double? = null,
    val feedback_correct: String,
)

@Serializable
data class RevealStep(
    val text: String,
    /** Explanation FUSED to the step — a detached paragraph is a contract violation (§3.2 ③, §5.3). */
    val callout: String,
)

/** §3.2 ③ figure binding: family ID + reference to a typed viz instance (content row, see VizInstance). */
@Serializable
data class FigureBinding(
    val family_id: String,
    val instance_id: String,
)

@Serializable
data class BeatReveal(
    val steps: List<RevealStep>,
    val figure: FigureBinding? = null,
)

@Serializable
data class BeatName(
    val definition: String,
    val invariant_statement: String,
    val why_matters: String,
)

@Serializable
data class BeatCheck(
    /** Different-instance item — same concept, different surface values (§3.2 ⑤). */
    val item_stem: String,
    val choices: List<AttemptChoice> = emptyList(),
    val numeric_answer: String? = null,
    val numeric_tolerance: Double? = null,
) {
    fun hasGradingData(): Boolean =
        choices.any { it.correct } || numeric_answer != null
}
