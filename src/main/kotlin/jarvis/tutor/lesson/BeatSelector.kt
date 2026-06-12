package jarvis.tutor.lesson

import jarvis.content.ConceptType
import jarvis.tutor.Phase

/** Spec §4.5 — the CLOSED set of legal beat plans. No fifth plan may ever be added by a prompt. */
enum class BeatPlan(val beats: List<BeatType>) {
    FULL(listOf(BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.NAME, BeatType.CHECK)),
    STANDARD(listOf(BeatType.PREDICT, BeatType.ATTEMPT, BeatType.REVEAL, BeatType.CHECK)),
    MASTERED_REVISIT(listOf(BeatType.ATTEMPT, BeatType.REVEAL, BeatType.CHECK)),
    RE_LESSON(listOf(BeatType.REVEAL, BeatType.CHECK)),
}

/** Wire literals are lowercase names; beats are NEVER reordered (council-1781052957). */
enum class BeatType { PREDICT, ATTEMPT, REVEAL, NAME, CHECK }

/**
 * Spec §4.2 — concept_type x mastery phase -> plan, vocabulary FIXED. The selector chooses
 * compression, never new vocabulary. First encounters (no mastery row / observations == 0)
 * receive only FULL or STANDARD (INV-4.1). RE_LESSON only via the §7.3 forgetting trigger
 * (Plan 7) — reLesson=true is passed by NO Plan-3 call site.
 */
object BeatSelector {
    fun planFor(conceptType: ConceptType, phase: Phase?, isFirstEncounter: Boolean, reLesson: Boolean = false): BeatPlan = when {
        reLesson && !isFirstEncounter -> BeatPlan.RE_LESSON
        isFirstEncounter -> BeatPlan.FULL          // first contact always carries ④ NAME
        phase == Phase.mastered -> BeatPlan.MASTERED_REVISIT
        else -> BeatPlan.STANDARD
    }
}
