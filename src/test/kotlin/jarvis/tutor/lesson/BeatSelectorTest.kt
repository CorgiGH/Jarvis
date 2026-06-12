package jarvis.tutor.lesson

import jarvis.content.ConceptType
import jarvis.tutor.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-3 Task 2 — INV-4.1 (spec §4.8): beat-plan legality property test over the BeatSelector.
 *
 * Exhausts EVERY (ConceptType × phase{null + all 4 Phase} × isFirstEncounter{T,F} × reLesson{T,F})
 * tuple — 8 × 5 × 2 × 2 = 160 cases — and asserts:
 *   1. output is exactly one of the FOUR plans in the §4.5 closed table;
 *   2. first encounters receive only FULL or STANDARD (and FULL carries ④ NAME);
 *   3. reLesson && !first ⇒ RE_LESSON; mastered && !first && !reLesson ⇒ MASTERED_REVISIT;
 *   4. every plan's beats follow the fixed order PREDICT<ATTEMPT<REVEAL<NAME<CHECK (index-monotonic);
 *   5. STANDARD lacks NAME; the closed table is exactly the 4 plans (no 5th plan exists).
 *
 * The selector chooses compression, never new vocabulary (council-1781052957).
 */
class BeatSelectorTest {

    /** The fixed beat order (§4.5, never reordered). Index in this list = the beat's legal position. */
    private val fixedOrder = listOf(
        BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.NAME, BeatType.CHECK,
    )

    private val phases: List<Phase?> = listOf(null) + Phase.entries

    @Test
    fun `every tuple yields one of the four closed-table plans`() {
        for (ct in ConceptType.entries) for (phase in phases)
            for (first in listOf(true, false)) for (re in listOf(true, false)) {
                val plan = BeatSelector.planFor(ct, phase, first, re)
                assertTrue(
                    plan in BeatPlan.entries,
                    "($ct, $phase, first=$first, re=$re) -> $plan must be a closed-table plan",
                )
            }
    }

    @Test
    fun `first encounters receive only FULL or STANDARD and FULL carries NAME`() {
        for (ct in ConceptType.entries) for (phase in phases)
            for (re in listOf(true, false)) {
                val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = true, reLesson = re)
                assertTrue(
                    plan == BeatPlan.FULL || plan == BeatPlan.STANDARD,
                    "first encounter ($ct, $phase, re=$re) -> $plan must be FULL or STANDARD",
                )
            }
        // first encounter ALWAYS carries ④ NAME (the selector returns FULL — never STANDARD —
        // for a first encounter per §0.9A; STANDARD's "minimum for first encounters" is the
        // schema floor, but the live selector promotes first contact to FULL).
        for (ct in ConceptType.entries) for (phase in phases) {
            val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = true, reLesson = false)
            assertEquals(BeatPlan.FULL, plan, "first contact ($ct, $phase) carries ④ NAME ⇒ FULL")
            assertTrue(BeatType.NAME in plan.beats, "$plan must contain NAME")
        }
    }

    @Test
    fun `reLesson on a non-first encounter yields RE_LESSON`() {
        for (ct in ConceptType.entries) for (phase in phases) {
            val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = false, reLesson = true)
            assertEquals(BeatPlan.RE_LESSON, plan, "reLesson+!first ($ct, $phase) -> RE_LESSON")
        }
    }

    @Test
    fun `mastered non-first non-reLesson yields MASTERED_REVISIT`() {
        for (ct in ConceptType.entries) {
            val plan = BeatSelector.planFor(
                ct, Phase.mastered, isFirstEncounter = false, reLesson = false,
            )
            assertEquals(BeatPlan.MASTERED_REVISIT, plan, "mastered+!first+!re ($ct) -> MASTERED_REVISIT")
        }
    }

    @Test
    fun `non-mastered non-first non-reLesson yields STANDARD`() {
        for (ct in ConceptType.entries)
            for (phase in listOf<Phase?>(null, Phase.intro, Phase.practice, Phase.retrieval)) {
                val plan = BeatSelector.planFor(ct, phase, isFirstEncounter = false, reLesson = false)
                assertEquals(BeatPlan.STANDARD, plan, "non-mastered ($ct, $phase) -> STANDARD")
            }
    }

    @Test
    fun `every plan's beats follow the fixed order strictly increasing`() {
        for (plan in BeatPlan.entries) {
            val indices = plan.beats.map { fixedOrder.indexOf(it) }
            assertTrue(
                indices.all { it >= 0 },
                "$plan contains a beat outside the fixed vocabulary: ${plan.beats}",
            )
            assertTrue(
                indices.zipWithNext().all { (a, b) -> a < b },
                "$plan beats must be strictly increasing in the fixed order: $indices",
            )
        }
    }

    @Test
    fun `STANDARD lacks NAME and the closed table is exactly four plans`() {
        assertTrue(BeatType.NAME !in BeatPlan.STANDARD.beats, "STANDARD elides ④ NAME")
        assertEquals(
            setOf(BeatPlan.FULL, BeatPlan.STANDARD, BeatPlan.MASTERED_REVISIT, BeatPlan.RE_LESSON),
            BeatPlan.entries.toSet(),
            "the legal plan set is CLOSED at exactly four (no 5th plan may be added)",
        )
        assertEquals(4, BeatPlan.entries.size)
    }

    @Test
    fun `BeatType vocabulary is exactly the five fixed beats in order`() {
        assertEquals(
            listOf(
                BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.NAME, BeatType.CHECK,
            ),
            BeatType.entries,
            "the beat vocabulary is FIXED at 5, never reordered (council-1781052957)",
        )
    }
}
