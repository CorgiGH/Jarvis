package jarvis

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class FeedbackConsumerTest {

    private fun sig(id: String, kind: String = "ctx_model_summary") = ProactiveSignal(
        id = id, ts = "2026-05-08T12:00:00Z", kind = kind, importance = 0.85f,
        sourceTs = "2026-05-08T11:59:00Z", snippet = "...", rationale = "...",
    )

    private fun fb(id: String, action: String) = FeedbackEntry(
        signalId = id, ts = "2026-05-08T12:30:00Z", action = action,
    )

    @Test
    fun emptyInputs() {
        val s = FeedbackConsumer.analyze(emptyList(), emptyList())
        assertEquals(0, s.totalSignals)
        assertEquals(0, s.totalFeedback)
        assertEquals(0, s.totalAcked)
        assertTrue(s.byAction.isEmpty())
        assertTrue(s.byKind.isEmpty())
    }

    @Test
    fun analyzeCountsActionsAcrossSignals() {
        val signals = listOf(sig("a"), sig("b"), sig("c"))
        val feedback = listOf(fb("a", "useful"), fb("b", "dismissed"), fb("c", "dismissed"))
        val s = FeedbackConsumer.analyze(signals, feedback)
        assertEquals(3, s.totalAcked)
        assertEquals(1, s.byAction["useful"])
        assertEquals(2, s.byAction["dismissed"])
    }

    @Test
    fun analyzeIgnoresFeedbackForUnknownSignals() {
        val signals = listOf(sig("a"))
        val feedback = listOf(fb("a", "useful"), fb("orphan", "dismissed"))
        val s = FeedbackConsumer.analyze(signals, feedback)
        // byAction counts ALL feedback regardless of signal-known status
        assertEquals(2, s.byAction.values.sum())
        // totalAcked dedupes against the signals set
        assertEquals(1, s.totalAcked)
        // byKind only includes the known-signal feedback
        assertEquals(mapOf("useful" to 1), s.byKind["ctx_model_summary"])
    }

    @Test
    fun recommendNoOpWhenSignalSparse() {
        // <5 acked rows → 0 offset
        val signals = listOf(sig("a"), sig("b"), sig("c"))
        val feedback = listOf(fb("a", "dismissed"), fb("b", "dismissed"), fb("c", "dismissed"))
        val r = FeedbackConsumer.recommend(FeedbackConsumer.analyze(signals, feedback))
        assertEquals(0f, r.globalOffset)
    }

    @Test
    fun recommendRaisesOnHeavyDismiss() {
        val signals = (1..10).map { sig("s$it") }
        // 7/10 dismissed, 1 noise, 1 useful, 1 pinned
        val feedback = (1..7).map { fb("s$it", "dismissed") } +
            listOf(fb("s8", "noise"), fb("s9", "useful"), fb("s10", "pinned"))
        val r = FeedbackConsumer.recommend(FeedbackConsumer.analyze(signals, feedback))
        // dismiss-rate (7+1)/10 = 0.8 > 0.6 → +0.10
        assertEquals(0.10f, r.globalOffset)
    }

    @Test
    fun recommendMidRaiseOnModerateDismiss() {
        val signals = (1..10).map { sig("s$it") }
        // 5/10 dismissed → rate 0.5 in (0.4, 0.6] → +0.05
        val feedback = (1..5).map { fb("s$it", "dismissed") } +
            (6..10).map { fb("s$it", "useful") }
        val r = FeedbackConsumer.recommend(FeedbackConsumer.analyze(signals, feedback))
        assertEquals(0.05f, r.globalOffset)
    }

    @Test
    fun recommendLowersOnHighUsefulPinned() {
        val signals = (1..10).map { sig("s$it") }
        // 5 useful + 1 pinned + 4 dismissed → useful-rate 0.6, dismiss 0.4 (not >0.4 strict)
        val feedback = (1..5).map { fb("s$it", "useful") } +
            listOf(fb("s6", "pinned")) +
            (7..10).map { fb("s$it", "dismissed") }
        val r = FeedbackConsumer.recommend(FeedbackConsumer.analyze(signals, feedback))
        // dismiss-rate 4/10 = 0.4 NOT >0.4, useful+pinned 6/10 = 0.6 >0.4 → -0.05
        assertEquals(-0.05f, r.globalOffset)
    }

    @Test
    fun perKindOffsetForHighDismiss() {
        val signals = (1..6).map { sig("c$it", kind = "ctx_model_summary") } +
            (1..4).map { sig("e$it", kind = "error") }
        // ctx_model_summary: 4/6 dismissed → rate 0.67 > 0.6 → +0.10 per-kind
        // error: 1/4 dismissed (0.25) BUT useful 3/4 = 0.75 > 0.4 → -0.05
        val feedback = (1..4).map { fb("c$it", "dismissed") } +
            (5..6).map { fb("c$it", "useful") } +
            listOf(fb("e1", "dismissed")) +
            (2..4).map { fb("e$it", "useful") }
        val r = FeedbackConsumer.recommend(FeedbackConsumer.analyze(signals, feedback))
        assertEquals(0.10f, r.perKindOffset["ctx_model_summary"])
        assertEquals(-0.05f, r.perKindOffset["error"])
    }

    @Test
    fun perKindSkipsLowVolumeKinds() {
        // Kind with <3 acked rows is skipped entirely
        val signals = listOf(sig("c1", "ctx_model_summary"), sig("c2", "rare_kind"))
        val feedback = listOf(fb("c1", "dismissed"), fb("c2", "dismissed"))
        val r = FeedbackConsumer.recommend(FeedbackConsumer.analyze(signals, feedback))
        assertNull(r.perKindOffset["rare_kind"])
        assertNull(r.perKindOffset["ctx_model_summary"])
    }

    @Test
    fun effectiveThresholdClampsAndApplies() {
        val rec = ThresholdRecommendation(
            ts = "...", baseThreshold = 0.7f, globalOffset = 0.10f,
            perKindOffset = emptyMap(), basis = "...",
        )
        assertEquals(0.80f, FeedbackConsumer.effectiveThreshold(rec))

        val recLower = rec.copy(globalOffset = -0.05f)
        assertEquals(0.65f, FeedbackConsumer.effectiveThreshold(recLower))

        // clamps to [0, 1]
        val extreme = rec.copy(globalOffset = 1.5f)
        assertEquals(1.0f, FeedbackConsumer.effectiveThreshold(extreme))
    }

    @Test
    fun basisIsHumanReadable() {
        val signals = (1..10).map { sig("s$it") }
        val feedback = (1..7).map { fb("s$it", "dismissed") } +
            (8..10).map { fb("s$it", "useful") }
        val r = FeedbackConsumer.recommend(FeedbackConsumer.analyze(signals, feedback))
        assertTrue(r.basis.contains("totalFeedback=10"))
        assertTrue(r.basis.contains("dismiss-rate=0.70"))
        assertTrue(r.basis.contains("ackedSignals=10/10"))
    }
}
