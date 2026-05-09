package jarvis.tutor

import java.util.concurrent.atomic.AtomicLong

/**
 * Tutor task-context V0 — monotonic state-version counter.
 *
 * Council fix #3: cache invalidation needs `state_version` in the
 * cache key so a concurrent state write evicts the snapshot the next
 * read would otherwise serve. V0 ships an in-process counter; V1
 * upgrades to a `tutor_state_writes` audit table linked to the audit
 * chain so multi-instance + crash-replay are coherent.
 *
 * Bump points (initial set, expand as new write paths appear):
 *  - KnowledgeFsrs.recordReview
 *  - KnowledgeState.touchTo
 *  - KnowledgeGapRepo.append + bumpReuse
 *  - AssignmentRepo.append (via Assignments.kt)
 *  - GradeRepo.record
 *  - TrustGrantRepo.insert / revoke / tryConsume
 *  - SensorRepo.append (sensor events bias Source classifier; ok to bump)
 *
 * Reads are cheap (atomic load); the bump cost is one atomic
 * increment per write.
 */
object StateVersion {
    private val counter = AtomicLong(0L)

    /** Current monotonic version. Returns 0 on a fresh process. */
    fun current(): Long = counter.get()

    /** Bump on every state-mutating call. Returns the new value (for
     *  callers that want to log the post-write version). */
    fun bump(): Long = counter.incrementAndGet()

    /** Test hook — reset to 0. */
    internal fun resetForTests() {
        counter.set(0L)
    }
}
