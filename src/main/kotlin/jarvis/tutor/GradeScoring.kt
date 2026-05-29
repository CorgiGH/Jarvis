package jarvis.tutor

/**
 * Deterministic, LLM-independent scoring layer (E1 trustworthy-grader).
 * The LLM produces rubric booleans + prose; THIS object decides the score,
 * correctness, and confidence. The LLM's self-reported `score` float is never trusted.
 */
object GradeScoring {

    /** Score = fraction of rubric items the grader marked passed. */
    fun scoreFromRubric(rubric: Map<String, Boolean>): Double {
        if (rubric.isEmpty()) return 0.0
        return rubric.values.count { it }.toDouble() / rubric.size
    }

    /** Correct iff the rubric is non-empty and every item passed. */
    fun correctFromRubric(rubric: Map<String, Boolean>): Boolean =
        rubric.isNotEmpty() && rubric.values.all { it }

    /**
     * LOW confidence when the LLM's own correctness verdict is internally inconsistent
     * with the rubric booleans it emitted, OR when the rubric is empty (no evidence to
     * cross-check). Such grades MUST be deferred, never recorded.
     */
    fun isConfident(llm: GradeResult): Boolean =
        llm.rubric.isNotEmpty() && llm.correct == correctFromRubric(llm.rubric)

    /** Normalize a free-text answer for exact comparison. */
    fun normalizeAnswer(s: String): String =
        s.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', ',', ';', ':', ' ')

    /** Deterministic match of a student answer against a canonical answer (string or numeric). */
    fun answerMatches(canonical: String, attempt: String): Boolean {
        val c = normalizeAnswer(canonical)
        val a = normalizeAnswer(attempt)
        if (c == a) return true
        val cn = c.toDoubleOrNull()
        val an = a.toDoubleOrNull()
        return cn != null && an != null && kotlin.math.abs(cn - an) < 1e-9
    }
}
