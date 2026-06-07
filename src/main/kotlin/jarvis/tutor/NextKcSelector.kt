package jarvis.tutor

import jarvis.content.KnowledgeConcept

/**
 * Selects the single next KC to study — interface-signatures-lock §D (lines 81-104).
 *
 * Deterministic NOW; swap-safe: a future `ThompsonSelector` implements this SAME interface, so the
 * model swap never touches callers.
 */
interface NextKcSelector {
    /**
     * Returns the single next [QueueItem], or `null` when no eligible KC exists
     * (0-KC subject / all-mastered / all-locked ⇒ `null`, no crash — M-NEXTKC).
     *
     * @param userId        the learner (carried for future per-user scoring; unused by the locked policy).
     * @param subject       null = across all subjects; otherwise only candidates in this subject.
     * @param candidates    pre-filtered eligible KCs (faithful + ACTIVE-card or seedable).
     * @param recentShapes  last-N served `shape` strings, for the interleave-cap.
     */
    fun select(
        userId: String,
        subject: String?,
        candidates: List<KcCandidate>,
        recentShapes: List<String>,
    ): QueueItem?
}

/**
 * Input row the selector scores — interface-signatures-lock §D (lines 98-104).
 * Assembled by the queue handler (Group 3) from `kc_mastery` + the content corpus +
 * `kc_verification_status`. NOT a wire type.
 */
data class KcCandidate(
    val kc: KnowledgeConcept,             // [EXISTS] jarvis.content.KnowledgeConcept
    val mastery: KcMastery?,              // [EXISTS] null = never attempted (cold)
    val phase: Phase,                     // resolved (kc_mastery.phase or entry_phase or intro)
    val verificationStatus: VerificationStatus,
    val fsrsCardId: String?,
)

/**
 * The LOCKED (deterministic, non-Thompson) selection policy — interface-signatures-lock §D line 108:
 *
 *   1. PREREQ-GATED (via [PrereqGraph]): a KC whose transitive prereqs are not ALL mastered is
 *      NEVER surfaced. The mastered-set is derived from the candidate set itself (a candidate's
 *      `KcMastery.mastered`). This is the load-bearing safety invariant.
 *   2. LOWEST-MASTERY first: among unlocked, not-yet-mastered candidates, the one with the lowest
 *      `mastery.ewmaScore` (cold/null mastery ⇒ 0.0, the lowest) is the next work.
 *   3. INTERLEAVE-CAP on [recentShapes]: when the lowest-mastery pick's resolved shape (its `mode`)
 *      has been served `INTERLEAVE_CAP` or more times in a row at the tail of `recentShapes`, prefer
 *      the next unlocked candidate whose shape differs (so the learner isn't drilled on the same
 *      shape forever). Falls through to the lowest-mastery pick when no differently-shaped unlocked
 *      candidate exists. Deterministic; ties broken by `kc.id` for stable ordering.
 *
 * DECISIONS (recorded — §D underspecifies these at the selection layer):
 *  - The PrereqGraph is a CONSTRUCTOR dependency, NOT a `select` parameter (the `select` signature is
 *    frozen and carries no graph). The graph is memoized in TutorContext per M-NEXTKC.
 *  - A candidate's "shape" for the interleave-cap is its resolved [QueueMode] (the drill-shape
 *    taxonomy is a Group-6 generator concern; KcCandidate carries no shape field). Least-ripple.
 *  - `worked_example_first` / `mode` are SERVER decisions resolved here from phase + authored flag
 *    (§4.1 "worked-example-first faded by mastery"): worked-example-first for a novice (intro phase)
 *    OR when authored true; `mode` = retrieve at the retrieval phase, worked when worked-first, else drill.
 *
 * Pure over its args + the injected graph (no DB, no side effects).
 */
class LockedNextKcSelector(
    private val prereqGraph: PrereqGraph,
) : NextKcSelector {

    override fun select(
        userId: String,
        subject: String?,
        candidates: List<KcCandidate>,
        recentShapes: List<String>,
    ): QueueItem? {
        // Subject filter (null = across all subjects).
        val inSubject = if (subject == null) candidates else candidates.filter { it.kc.subject == subject }

        // The mastered set drives prereq unlocking (derived from the candidate set itself).
        val masteredKcIds: Set<String> =
            inSubject.filter { it.mastery?.mastered == true }.map { it.kc.id }.toSet()

        // Eligible = NOT already fully-mastered AND prereq-unlocked. Stable order by id.
        val eligible = inSubject
            .filterNot { it.mastery?.mastered == true }                       // drop mastered KCs
            .filter { prereqGraph.isUnlocked(it.kc.id, masteredKcIds) }       // PREREQ GATE (class-killer)
            .sortedWith(compareBy({ it.ewma() }, { it.kc.id }))               // lowest-mastery, id tie-break

        if (eligible.isEmpty()) return null                                  // 0-KC degrade (class-killer)

        val chosen = applyInterleaveCap(eligible, recentShapes)
        return chosen.toQueueItem()
    }

    /**
     * Interleave-cap: if the lowest-mastery pick's shape is over-served at the tail of [recentShapes],
     * prefer the first eligible candidate with a different shape; else fall through to the head.
     */
    private fun applyInterleaveCap(eligible: List<KcCandidate>, recentShapes: List<String>): KcCandidate {
        val head = eligible.first()
        val headShape = head.resolvedMode().name
        val tailRun = recentShapes.takeLastWhile { it == headShape }.size
        if (tailRun < INTERLEAVE_CAP) return head
        return eligible.firstOrNull { it.resolvedMode().name != headShape } ?: head
    }

    private fun KcCandidate.ewma(): Double = mastery?.ewmaScore ?: 0.0

    /** SERVER decision (§4.1): worked-example-first for a novice (intro) or when authored true. */
    private fun KcCandidate.workedFirst(): Boolean = kc.worked_example_first || phase == Phase.intro

    /** SERVER decision: retrieve at retrieval phase; worked when worked-first; else drill. */
    private fun KcCandidate.resolvedMode(): QueueMode = when {
        phase == Phase.retrieval -> QueueMode.retrieve
        workedFirst()            -> QueueMode.worked
        else                     -> QueueMode.drill
    }

    private fun KcCandidate.toQueueItem(): QueueItem = QueueItem(
        kc_id = kc.id,
        kc_name_ro = kc.name_ro,
        kc_name_en = kc.name_en,
        subject = kc.subject,
        phase = phase,
        mastery_ewma = ewma(),
        fsrs_card_id = fsrsCardId,
        verification_status = verificationStatus,
        worked_example_first = workedFirst(),
        mode = resolvedMode(),
    )

    companion object {
        /** Max consecutive same-shape serves before the interleave-cap kicks in. */
        const val INTERLEAVE_CAP = 3
    }
}
