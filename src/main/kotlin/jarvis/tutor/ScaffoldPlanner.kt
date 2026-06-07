package jarvis.tutor

import jarvis.content.KnowledgeConcept

/**
 * Ordered phase plan for one KC and one learner — interface-signatures-lock §F (lines 137-147).
 *
 * `planFor` returns the ordered subset of `[intro, practice, retrieval, mastered]` that is:
 *   - `kc.phase_plan` (CHANGE-3 `List<String>?`; `null` ⇒ all four phases), parsed to `List<Phase>`,
 *     in the natural progression order;
 *   - trimmed BELOW the learner's phase floor — `mastery.entry_phase` (`null` ⇒ intro, the §4.1 gate;
 *     a `null` mastery row is treated as a cold learner ⇒ intro floor);
 *   - INTERSECT not-yet-mastered: a fully-mastered KC (`KcMastery.mastered`) ⇒ `emptyList()`
 *     (§F line 147 — the planner is the ONLY place this is computed; no caller re-derives).
 *
 * Pure; no DB, no side effects.
 */
object ScaffoldPlanner {

    /** Natural progression order — the rank used for the floor trim. */
    private val ORDER = listOf(Phase.intro, Phase.practice, Phase.retrieval, Phase.mastered)
    private fun rank(p: Phase): Int = ORDER.indexOf(p)

    /**
     * @param kc       the KC (reads CHANGE-3 `phase_plan: List<String>?`; `null` ⇒ all four).
     * @param mastery  the learner's `KcMastery` row [EXISTS], or `null` for a never-attempted KC.
     * @return ordered `List<Phase>`; `emptyList()` for a fully-mastered KC OR when the floor sits
     *         above every authored phase.
     */
    fun planFor(kc: KnowledgeConcept, mastery: KcMastery?): List<Phase> {
        // INTERSECT not-yet-mastered: a fully-mastered KC has no remaining work.
        if (mastery != null && mastery.mastered) return emptyList()

        // phase_plan = null ⇒ all four, else the authored subset parsed to Phase, in ORDER.
        val planned: List<Phase> =
            kc.phase_plan
                ?.mapNotNull { runCatching { Phase.valueOf(it) }.getOrNull() }
                ?.let { authored -> ORDER.filter { it in authored } }   // canonical order, dedup
                ?: ORDER

        // Floor = entry_phase (null mastery OR null entry_phase ⇒ intro, the §4.1 default).
        val floorRank = rank(mastery?.effectiveEntryPhase ?: Phase.intro)

        // Trim BELOW the floor (keep phases at/above the learner's current floor).
        return planned.filter { rank(it) >= floorRank }
    }
}
