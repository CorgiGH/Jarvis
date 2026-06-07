package jarvis.tutor

import jarvis.content.KnowledgeConcept
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TASK 2 — ScaffoldPlanner.planFor (interface-signatures-lock §F, lines 137-147).
 *
 * Frozen contract:
 *   object ScaffoldPlanner { fun planFor(kc: KnowledgeConcept, mastery: KcMastery?): List<Phase> }
 *
 * Returns the ordered subset of [intro, practice, retrieval, mastered] =
 *   kc.phase_plan  (CHANGE-3 List<String>?, null => all four)
 *   INTERSECT not-yet-mastered
 *   trimmed BELOW the learner phase floor (entry_phase; null => intro) and above mastered.
 *
 * Empty result: a fully-mastered KC => emptyList() (§F line 147).
 *
 * Class-killing focus: the entry_phase floor trim + the not-yet-mastered intersection.
 */
class ScaffoldPlannerTest {

    private fun kc(phasePlan: List<String>? = null): KnowledgeConcept = KnowledgeConcept(
        id = "pa-kc-001", subject = "PA", name_ro = "RO", name_en = "EN",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = 1.0, tier = 1, grounding_tier = "standard",
        phase_plan = phasePlan,
    )

    /** Mastery row helper. `entryPhase` is the §4.1 floor; `phase`/ewma drive `mastered`. */
    private fun mastery(
        entryPhase: Phase? = null,
        ewma: Double = 0.0,
        obs: Int = 0,
        phase: Phase? = null,
    ): KcMastery = KcMastery(
        userId = "u", kcId = "pa-kc-001", ewmaScore = ewma, observations = obs,
        lastGradedAt = Instant.EPOCH, phase = phase, entryPhase = entryPhase,
    )

    private val ALL = listOf(Phase.intro, Phase.practice, Phase.retrieval, Phase.mastered)

    // ── phase_plan = null => all four (minus mastered if mastered) ───────────
    @Test
    fun `null phase_plan and cold learner yields all four phases`() {
        assertEquals(ALL, ScaffoldPlanner.planFor(kc(phasePlan = null), mastery()))
    }

    // ── null mastery => treat as intro floor, not-mastered ───────────────────
    @Test
    fun `null mastery is treated as intro floor`() {
        assertEquals(ALL, ScaffoldPlanner.planFor(kc(phasePlan = null), mastery = null))
    }

    // ── explicit phase_plan subset honored, in order ────────────────────────
    @Test
    fun `explicit phase_plan subset is honored`() {
        val plan = ScaffoldPlanner.planFor(
            kc(phasePlan = listOf("intro", "retrieval")), mastery(),
        )
        assertEquals(listOf(Phase.intro, Phase.retrieval), plan)
    }

    // ── entry_phase floor trims EARLIER phases (THE class-killer) ────────────
    @Test
    fun `entry_phase floor trims phases below the floor`() {
        // floor = retrieval => intro + practice are below the floor and dropped.
        val plan = ScaffoldPlanner.planFor(kc(phasePlan = null), mastery(entryPhase = Phase.retrieval))
        assertEquals(listOf(Phase.retrieval, Phase.mastered), plan)
    }

    @Test
    fun `entry_phase floor applies to an explicit phase_plan too`() {
        // plan = [intro, practice, retrieval]; floor = practice => intro dropped.
        val plan = ScaffoldPlanner.planFor(
            kc(phasePlan = listOf("intro", "practice", "retrieval")),
            mastery(entryPhase = Phase.practice),
        )
        assertEquals(listOf(Phase.practice, Phase.retrieval), plan)
    }

    // ── fully-mastered KC => emptyList (§F line 147, THE class-killer) ───────
    @Test
    fun `fully mastered kc yields empty list`() {
        // ewma>=0.8 && obs>=3 => KcMastery.mastered == true
        val plan = ScaffoldPlanner.planFor(
            kc(phasePlan = null), mastery(ewma = 0.95, obs = 5, phase = Phase.mastered),
        )
        assertEquals(emptyList(), plan)
    }

    @Test
    fun `mastered kc with explicit plan still yields empty`() {
        val plan = ScaffoldPlanner.planFor(
            kc(phasePlan = listOf("intro", "practice")),
            mastery(ewma = 0.9, obs = 4, phase = Phase.mastered),
        )
        assertEquals(emptyList(), plan)
    }

    // ── floor above the entire plan => empty (nothing left after trim) ───────
    @Test
    fun `floor above whole plan yields empty`() {
        val plan = ScaffoldPlanner.planFor(
            kc(phasePlan = listOf("intro", "practice")),
            mastery(entryPhase = Phase.retrieval),
        )
        assertEquals(emptyList(), plan)
    }
}
