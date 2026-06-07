package jarvis.tutor

import jarvis.content.KnowledgeConcept
import jarvis.content.PrereqEdge
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * TASK 3 — NextKcSelector (interface-signatures-lock §D, lines 81-104; master-plan:203).
 *
 * Frozen interface:
 *   interface NextKcSelector {
 *       fun select(userId, subject:String?, candidates:List<KcCandidate>, recentShapes:List<String>): QueueItem?
 *   }
 *   data class KcCandidate(kc, mastery:KcMastery?, phase:Phase, verificationStatus, fsrsCardId:String?)
 *
 * Scoring (§D line 108): prereq-gated (via PrereqGraph) -> lowest-mastery -> interleave-cap.
 * Returns QueueItem? — null when 0 eligible KC exists (M-NEXTKC: 0-KC degrade, never throw).
 *
 * The concrete impl `LockedNextKcSelector(prereqGraph)` holds the PrereqGraph (NOT in the frozen
 * select signature) — the candidate set's mastered KCs drive `isUnlocked`.
 *
 * Class-killing focus: prereq-gating (a KC with unmastered prereqs is NEVER surfaced) + 0-KC degrade.
 */
class NextKcSelectorTest {

    private fun kc(id: String): KnowledgeConcept = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "RO-$id", name_en = "EN-$id",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = 1.0, tier = 1, grounding_tier = "standard",
    )

    private fun masteryRow(kcId: String, ewma: Double, obs: Int): KcMastery = KcMastery(
        userId = "u", kcId = kcId, ewmaScore = ewma, observations = obs,
        lastGradedAt = Instant.EPOCH, phase = Phase.practice, entryPhase = null,
    )

    private fun candidate(
        id: String,
        ewma: Double = 0.0,
        obs: Int = 0,
        phase: Phase = Phase.intro,
        status: VerificationStatus = VerificationStatus.faithful,
    ): KcCandidate = KcCandidate(
        kc = kc(id),
        mastery = if (obs == 0) null else masteryRow(id, ewma, obs),
        phase = phase,
        verificationStatus = status,
        fsrsCardId = "card-$id",
    )

    private fun edge(kc: String, prereq: String) = PrereqEdge(kc, prereq, "r")

    // ── picks a KC whose prereqs are mastered ───────────────────────────────
    @Test
    fun `picks a kc whose prereqs are all mastered`() {
        // B depends on A; A is fully mastered, B is cold => B is unlocked and selected.
        val graph = PrereqGraph.from(listOf(edge("B", "A")))
        val selector = LockedNextKcSelector(graph)
        val candidates = listOf(
            candidate("A", ewma = 0.95, obs = 5),  // mastered
            candidate("B", obs = 0),               // cold, unlocked (A mastered)
        )
        val picked = selector.select("u", "PA", candidates, recentShapes = emptyList())
        assertNotNull(picked)
        assertEquals("B", picked.kc_id)            // A mastered => not surfaced; B is the work
    }

    // ── SKIPS a KC with unmastered prereqs (THE class-killer) ───────────────
    @Test
    fun `never surfaces a kc whose prereqs are unmastered`() {
        // B depends on A; A is NOT mastered. B must NOT be selected even if B is lowest-mastery.
        val graph = PrereqGraph.from(listOf(edge("B", "A")))
        val selector = LockedNextKcSelector(graph)
        val candidates = listOf(
            candidate("A", ewma = 0.30, obs = 2),  // NOT mastered
            candidate("B", obs = 0),               // LOCKED (A not mastered)
        )
        val picked = selector.select("u", "PA", candidates, recentShapes = emptyList())
        assertNotNull(picked)
        assertEquals("A", picked.kc_id)            // only A is unlocked
    }

    @Test
    fun `a transitively-locked kc is never surfaced`() {
        // C->B->A. A mastered, B not. C must stay locked (B unmastered); B is the only pick.
        val graph = PrereqGraph.from(listOf(edge("C", "B"), edge("B", "A")))
        val selector = LockedNextKcSelector(graph)
        val candidates = listOf(
            candidate("A", ewma = 0.9, obs = 4),   // mastered
            candidate("B", ewma = 0.2, obs = 1),   // unlocked (A mastered), NOT mastered
            candidate("C", obs = 0),               // locked (B unmastered)
        )
        val picked = selector.select("u", "PA", candidates, recentShapes = emptyList())
        assertNotNull(picked)
        assertEquals("B", picked.kc_id)
    }

    // ── lowest-mastery ordering among unlocked candidates ───────────────────
    @Test
    fun `picks the lowest-mastery unlocked candidate`() {
        val graph = PrereqGraph.from(emptyList())   // all roots, all unlocked
        val selector = LockedNextKcSelector(graph)
        val candidates = listOf(
            candidate("hi", ewma = 0.7, obs = 4),
            candidate("lo", ewma = 0.1, obs = 2),   // lowest mastery => selected
            candidate("mid", ewma = 0.4, obs = 3),
        )
        val picked = selector.select("u", "PA", candidates, recentShapes = emptyList())
        assertNotNull(picked)
        assertEquals("lo", picked.kc_id)
    }

    // ── 0-KC / all-mastered => graceful degrade, no throw (THE class-killer) ─
    @Test
    fun `empty candidates degrades to null`() {
        val selector = LockedNextKcSelector(PrereqGraph.from(emptyList()))
        assertNull(selector.select("u", "PA", emptyList(), recentShapes = emptyList()))
    }

    @Test
    fun `all-mastered candidates degrade to null`() {
        val selector = LockedNextKcSelector(PrereqGraph.from(emptyList()))
        val candidates = listOf(
            candidate("A", ewma = 0.95, obs = 5),
            candidate("B", ewma = 0.9, obs = 4),
        )
        assertNull(selector.select("u", "PA", candidates, recentShapes = emptyList()))
    }

    @Test
    fun `all-locked candidates degrade to null`() {
        // Every candidate depends on an absent/unmastered prereq => nothing unlocked => null.
        val graph = PrereqGraph.from(listOf(edge("B", "A"), edge("C", "A")))
        val selector = LockedNextKcSelector(graph)
        // A is NOT among the candidates (and not mastered) => B and C both locked.
        val candidates = listOf(candidate("B", obs = 0), candidate("C", obs = 0))
        assertNull(selector.select("u", "PA", candidates, recentShapes = emptyList()))
    }

    // ── interleave-cap diverts away from an over-served shape ───────────────
    @Test
    fun `interleave cap diverts to a different shape when one is over-served`() {
        val selector = LockedNextKcSelector(PrereqGraph.from(emptyList()))
        // lowest-mastery candidate resolves to mode=drill (practice phase, not worked-first).
        // A second unlocked candidate resolves to mode=retrieve (retrieval phase).
        val drill = candidate("drill", ewma = 0.1, obs = 2, phase = Phase.practice)
        val retrieve = candidate("retr", ewma = 0.5, obs = 3, phase = Phase.retrieval)
        // recentShapes = the same "drill" served INTERLEAVE_CAP times in a row.
        val recent = List(LockedNextKcSelector.INTERLEAVE_CAP) { "drill" }
        val picked = selector.select("u", "PA", listOf(drill, retrieve), recentShapes = recent)
        assertNotNull(picked)
        // Cap fires: the lowest-mastery "drill" is skipped for the differently-shaped "retr".
        assertEquals("retr", picked.kc_id)
        assertEquals(QueueMode.retrieve, picked.mode)
    }

    @Test
    fun `interleave cap falls through when no differently-shaped candidate exists`() {
        val selector = LockedNextKcSelector(PrereqGraph.from(emptyList()))
        // Both candidates resolve to mode=drill; cap can't divert => keep the lowest-mastery one.
        val a = candidate("a", ewma = 0.1, obs = 2, phase = Phase.practice)
        val b = candidate("b", ewma = 0.4, obs = 2, phase = Phase.practice)
        val recent = List(LockedNextKcSelector.INTERLEAVE_CAP) { "drill" }
        val picked = selector.select("u", "PA", listOf(a, b), recentShapes = recent)
        assertNotNull(picked)
        assertEquals("a", picked.kc_id)
    }

    // ── QueueItem shape is populated from the candidate ─────────────────────
    @Test
    fun `selected QueueItem carries the candidate fields`() {
        val selector = LockedNextKcSelector(PrereqGraph.from(emptyList()))
        val picked = selector.select(
            "u", "PA", listOf(candidate("A", ewma = 0.2, obs = 2, phase = Phase.practice)),
            recentShapes = emptyList(),
        )
        assertNotNull(picked)
        assertEquals("A", picked.kc_id)
        assertEquals("RO-A", picked.kc_name_ro)
        assertEquals("EN-A", picked.kc_name_en)
        assertEquals("PA", picked.subject)
        assertEquals(Phase.practice, picked.phase)
        assertEquals(VerificationStatus.faithful, picked.verification_status)
        assertEquals("card-A", picked.fsrs_card_id)
        assertEquals(0.2, picked.mastery_ewma)
    }
}
