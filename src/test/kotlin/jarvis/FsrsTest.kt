package jarvis

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsTest {

    @Test
    fun initialStateGradeAgainShortStability() {
        val s = Fsrs.initial(grade = 1)
        assertTrue(s.stability < 1.0, "again → short stability (got ${s.stability})")
        assertTrue(s.difficulty >= 5.0, "again → higher difficulty")
    }

    @Test
    fun initialStateGradeEasyLongStability() {
        val s = Fsrs.initial(grade = 4)
        assertTrue(s.stability >= 5.0, "easy → long stability (got ${s.stability})")
        assertTrue(s.difficulty <= 5.0, "easy → lower difficulty")
    }

    @Test
    fun retrievabilityDecaysOverTime() {
        val r0 = Fsrs.retrievability(stability = 10.0, elapsedDays = 0.0)
        val r5 = Fsrs.retrievability(stability = 10.0, elapsedDays = 5.0)
        val r20 = Fsrs.retrievability(stability = 10.0, elapsedDays = 20.0)
        assertTrue(r0 > r5)
        assertTrue(r5 > r20)
        assertTrue(r0 > 0.99)
        assertTrue(r20 < 0.85)
    }

    @Test
    fun goodGradeGrowsStability() {
        val s0 = Fsrs.initial(grade = 3)
        val s1 = Fsrs.update(s0, grade = 3, elapsedDays = 5.0)
        assertTrue(s1.stability > s0.stability,
            "good review grows stability (${s0.stability} → ${s1.stability})")
    }

    @Test
    fun lapseShrinksStability() {
        val s0 = Fsrs.initial(grade = 4).copy(stability = 30.0)
        val s1 = Fsrs.update(s0, grade = 1, elapsedDays = 5.0)
        assertTrue(s1.stability < s0.stability,
            "lapse shrinks stability (${s0.stability} → ${s1.stability})")
    }

    @Test
    fun stabilityCappedAt100Years() {
        val s0 = Fsrs.State(difficulty = 1.0, stability = 30000.0)
        val s1 = Fsrs.update(s0, grade = 4, elapsedDays = 100.0)
        assertTrue(s1.stability <= 36500.0)
    }

    @Test
    fun nextIntervalGrowsWithStability() {
        val small = Fsrs.State(difficulty = 5.0, stability = 1.0)
        val large = Fsrs.State(difficulty = 5.0, stability = 30.0)
        assertTrue(Fsrs.nextIntervalDays(large) > Fsrs.nextIntervalDays(small))
    }

    @Test
    fun difficultyClampedToOneToTen() {
        val veryHard = Fsrs.State(difficulty = 9.5, stability = 1.0)
        val s1 = Fsrs.update(veryHard, grade = 1, elapsedDays = 1.0)
        assertTrue(s1.difficulty in 1.0..10.0)
        val veryEasy = Fsrs.State(difficulty = 1.5, stability = 30.0)
        val s2 = Fsrs.update(veryEasy, grade = 4, elapsedDays = 30.0)
        assertTrue(s2.difficulty in 1.0..10.0)
    }
}
