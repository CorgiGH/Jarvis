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
 *  - `worked_example_first` / `mode` are SERVER decisions resolved here from the SERVE phase + authored
 *    flag (§4.1 "worked-example-first faded by mastery"): worked-example-first for a novice (intro phase)
 *    OR when authored true; `mode` = retrieve at the retrieval phase, worked when worked-first, else drill.
 *  - P1-3(b): the SERVE phase is resolved via [ScaffoldPlanner.planFor] — the FIRST remaining phase of
 *    the KC's `phase_plan ∩ not-yet-mastered`, already trimmed to the learner's `entry_phase` floor. This
 *    is the ONE place the per-action phase plan is consumed on the serve path, so an authored `phase_plan`
 *    (e.g. a KC that skips `intro`) and a placement-seeded `entry_phase` floor BOTH actually shape what is
 *    served. Falls back to the raw resolved [KcCandidate.phase] only when the plan is empty (defensive;
 *    an eligible — not-fully-mastered — candidate's plan is non-empty unless the floor sits above it).
 *    `mode`/`worked_example_first` stay the single source of truth here (no second resolver downstream).
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
        val headShape = head.resolvedMode(head.servePhase()).name
        val tailRun = recentShapes.takeLastWhile { it == headShape }.size
        if (tailRun < INTERLEAVE_CAP) return head
        return eligible.firstOrNull { it.resolvedMode(it.servePhase()).name != headShape } ?: head
    }

    private fun KcCandidate.ewma(): Double = mastery?.ewmaScore ?: 0.0

    /**
     * P1-3(b) — the phase the next action SERVES, resolved via [ScaffoldPlanner.planFor].
     *
     * `planFor(kc, mastery)` is the ordered `phase_plan ∩ not-yet-mastered`, already trimmed to the
     * `entry_phase` floor. That is the set of phases this KC is ALLOWED to run for this learner. The
     * served phase is the FIRST plan phase AT OR ABOVE the learner's current resolved progression
     * ([KcCandidate.phase] = kc_mastery.phase ?: entry_phase ?: intro) — so:
     *   - an authored `phase_plan` that skips a phase (e.g. no `intro`) is honored (the skipped phase is
     *     never in the plan, so it can never be served);
     *   - the learner is never REGRESSED below the phase they have already reached via graded history;
     *   - a placement-seeded `entry_phase` floor still raises a cold learner's start (it raises both the
     *     plan floor AND the resolved `phase`).
     * Falls back to the raw resolved [KcCandidate.phase] only when the plan has no phase at/above it
     * (defensive; e.g. an empty plan when the floor sits above every authored phase).
     */
    private fun KcCandidate.servePhase(): Phase {
        val plan = ScaffoldPlanner.planFor(kc, mastery)
        return plan.firstOrNull { it.ordinal >= phase.ordinal } ?: phase
    }

    /** SERVER decision (§4.1): worked-example-first for a novice (intro) or when authored true. */
    private fun KcCandidate.workedFirst(servePhase: Phase): Boolean =
        kc.worked_example_first || servePhase == Phase.intro

    /** SERVER decision: retrieve at retrieval phase; worked when worked-first; else drill. */
    private fun KcCandidate.resolvedMode(servePhase: Phase): QueueMode = when {
        servePhase == Phase.retrieval -> QueueMode.retrieve
        workedFirst(servePhase)       -> QueueMode.worked
        else                          -> QueueMode.drill
    }

    private fun KcCandidate.toQueueItem(): QueueItem {
        val serve = servePhase()
        return QueueItem(
            kc_id = kc.id,
            kc_name_ro = kc.name_ro,
            kc_name_en = kc.name_en,
            subject = kc.subject,
            phase = serve,
            mastery_ewma = ewma(),
            fsrs_card_id = fsrsCardId,
            verification_status = verificationStatus,
            worked_example_first = workedFirst(serve),
            mode = resolvedMode(serve),
        )
    }

    companion object {
        /** Max consecutive same-shape serves before the interleave-cap kicks in. */
        const val INTERLEAVE_CAP = 3
    }
}
