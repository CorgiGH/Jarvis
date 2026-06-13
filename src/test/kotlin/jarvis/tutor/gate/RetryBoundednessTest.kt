package jarvis.tutor.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * INV-9.2 — RetryBudget boundedness assertions (Plan 4b §0.9H; R-4b-Q3).
 *
 * Verifies every row of spec §9.1:
 *   (a) machine gate: ≤3 self-retries, still red → park
 *   (b) user REJECT #1 → exactly one re-generation
 *   (c) REJECT #2 on same artifact → STOP_DESIGN_REVIEW
 */
class RetryBoundednessTest {

    // ── rejectAction pins (§9.1 rows b+c) ──────────────────────────────────

    @Test
    fun rejectAction_1_is_REGENERATE_ONCE() {
        assertEquals(RejectAction.REGENERATE_ONCE, RetryBudget.rejectAction(1))
    }

    @Test
    fun rejectAction_2_is_STOP_DESIGN_REVIEW() {
        assertEquals(RejectAction.STOP_DESIGN_REVIEW, RetryBudget.rejectAction(2))
    }

    @Test
    fun rejectAction_3_is_STOP_DESIGN_REVIEW() {
        assertEquals(RejectAction.STOP_DESIGN_REVIEW, RetryBudget.rejectAction(3))
    }

    // ── always-failing gate: exactly MACHINE_GATE_SELF_RETRIES attempts then park ──

    @Test
    fun always_failing_gate_runs_exactly_budget_attempts_then_parks() {
        val parked = mutableListOf<Triple<String, String, List<GateAttempt>>>()
        val sink = GapRecordSink { artifactId, gate, attempts ->
            parked.add(Triple(artifactId, gate, attempts))
        }
        val loop = RetryLoop(RetryBudget.MACHINE_GATE_SELF_RETRIES, sink)

        val result = loop.run<String>("artifact-1", "test-gate") { _ ->
            Result.failure(RuntimeException("always fails"))
        }

        // return is null (never succeeded)
        assertNull(result)
        // park was called exactly once
        assertEquals(1, parked.size)
        val (artifactId, gate, attempts) = parked[0]
        assertEquals("artifact-1", artifactId)
        assertEquals("test-gate", gate)
        // exactly budget attempts in the park payload — a 4th is structurally impossible
        assertEquals(RetryBudget.MACHINE_GATE_SELF_RETRIES, attempts.size)
        // attempt indices are 1-based sequential
        attempts.forEachIndexed { index, attempt ->
            assertEquals(index + 1, attempt.attempt)
        }
    }

    @Test
    fun a_fourth_attempt_is_structurally_impossible() {
        // Count actual lambda invocations — must not exceed MACHINE_GATE_SELF_RETRIES
        var callCount = 0
        val sink = GapRecordSink { _, _, _ -> }
        val loop = RetryLoop(RetryBudget.MACHINE_GATE_SELF_RETRIES, sink)

        loop.run<String>("artifact-4th", "gate-4th") { _ ->
            callCount++
            Result.failure(RuntimeException("fail"))
        }

        assertEquals(RetryBudget.MACHINE_GATE_SELF_RETRIES, callCount,
            "Lambda must be called exactly ${RetryBudget.MACHINE_GATE_SELF_RETRIES} times, never more")
    }

    // ── success on attempt 2 → 2 attempts, no park ──────────────────────────

    @Test
    fun success_on_attempt_2_returns_value_and_does_not_park() {
        val parked = mutableListOf<List<GateAttempt>>()
        val sink = GapRecordSink { _, _, attempts -> parked.add(attempts) }
        val loop = RetryLoop(RetryBudget.MACHINE_GATE_SELF_RETRIES, sink)

        var callCount = 0
        val result = loop.run("artifact-2", "gate-2") { _ ->
            callCount++
            if (callCount < 2) Result.failure(RuntimeException("fail attempt $callCount"))
            else Result.success("ok-on-2")
        }

        assertEquals("ok-on-2", result)
        assertEquals(2, callCount)
        assertEquals(0, parked.size, "No park when gate succeeds")
    }

    // ── failure data of attempt k is fed into attempt k+1 ───────────────────

    @Test
    fun failure_data_is_fed_forward_from_attempt_k_to_k_plus_1() {
        val sink = GapRecordSink { _, _, _ -> }
        val loop = RetryLoop(RetryBudget.MACHINE_GATE_SELF_RETRIES, sink)

        val receivedPriors = mutableListOf<String?>()

        loop.run<String>("artifact-feed", "gate-feed") { priorFailure ->
            receivedPriors.add(priorFailure)
            Result.failure(RuntimeException("fail-data-${receivedPriors.size}"))
        }

        // First call: no prior failure
        assertNull(receivedPriors[0])
        // Subsequent calls: prior failure message forwarded
        for (i in 1 until receivedPriors.size) {
            assertNotNull(receivedPriors[i],
                "Attempt ${i + 1} must receive the prior failure data, got null")
        }
        // The park payload should also carry all failure data
    }

    @Test
    fun park_payload_carries_failure_data_for_all_attempts() {
        var parkAttempts: List<GateAttempt> = emptyList()
        val sink = GapRecordSink { _, _, attempts -> parkAttempts = attempts }
        val loop = RetryLoop(RetryBudget.MACHINE_GATE_SELF_RETRIES, sink)

        loop.run<String>("artifact-payload", "gate-payload") { priorFailure ->
            val idx = (parkAttempts.size).let { (priorFailure?.substringAfterLast("-")?.toIntOrNull() ?: 0) + 1 }
            Result.failure(RuntimeException("failure-data-$idx"))
        }

        assertEquals(RetryBudget.MACHINE_GATE_SELF_RETRIES, parkAttempts.size)
        parkAttempts.forEach { attempt ->
            assert(attempt.failureData.isNotBlank()) {
                "Park payload attempt ${attempt.attempt} must carry non-blank failure data"
            }
        }
    }

    // ── constants match spec §9.1 ─────────────────────────────────────────────

    @Test
    fun machine_gate_self_retries_constant_is_3() {
        assertEquals(3, RetryBudget.MACHINE_GATE_SELF_RETRIES)
    }

    @Test
    fun user_reject_regenerations_constant_is_1() {
        assertEquals(1, RetryBudget.USER_REJECT_REGENERATIONS)
    }
}
