package jarvis

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Phase 5.1 — importance feedback consumer (vision spec §Phase 5).
 *
 * Reads [signals.jsonl] + [feedback.jsonl], computes per-action and per-kind
 * action distributions, and recommends a global threshold offset that
 * [ProactiveLoop] applies on top of [ProactiveLoop.IMPORTANCE_THRESHOLD].
 *
 * Per-spec ("regex/heuristic re-weighting, not fine-tuning") this is an
 * arithmetic re-weight, not an ML retrain. The feedback signal is small
 * (currently ≤200 rows) so we recompute live each call rather than persisting
 * a sidecar; keeps the pipeline auditable and avoids stale-cache failure modes.
 *
 * Recommendation rules (council-style heuristic, conservative):
 *   - <5 acked rows → noOp (signal too sparse to learn from).
 *   - dismiss-rate >0.6 → +0.10 (be more conservative; false-positive heavy).
 *   - dismiss-rate >0.4 → +0.05.
 *   - (useful + pinned) rate >0.4 → -0.05 (lower threshold — confirmed signal).
 *   - else 0.0.
 *
 * Per-kind offset is computed for kinds with ≥3 acked rows; it's reported in
 * [FeedbackStats.basis] for chat-tool inspection but the loop itself only
 * applies the global offset (kind isn't known until after the LLM spends).
 */
@Serializable
data class FeedbackStats(
    val totalSignals: Int,
    val totalFeedback: Int,
    val totalAcked: Int,
    /** Action -> count across all feedback rows. */
    val byAction: Map<String, Int>,
    /** Kind -> action -> count. Only kinds with feedback rows appear. */
    val byKind: Map<String, Map<String, Int>>,
)

@Serializable
data class ThresholdRecommendation(
    val ts: String,
    val baseThreshold: Float,
    /** Added to baseThreshold; negative = lower the bar (surface more). */
    val globalOffset: Float,
    /** Per-kind offsets for inspection; loop applies global only. */
    val perKindOffset: Map<String, Float>,
    /** Human-readable summary of the rate calculations. */
    val basis: String,
)

object FeedbackConsumer {
    /** Mirror of [ProactiveLoop.IMPORTANCE_THRESHOLD] — kept here so analyze()
     *  is pure (testable without ProactiveLoop dependency). */
    const val BASE_THRESHOLD: Float = 0.7f
    const val MIN_ACKS_FOR_GLOBAL: Int = 5
    const val MIN_ACKS_PER_KIND: Int = 3

    fun analyze(
        signals: List<ProactiveSignal>,
        feedback: List<FeedbackEntry>,
    ): FeedbackStats {
        val sigById = signals.associateBy { it.id }
        val ackedFeedback = feedback.filter { it.signalId in sigById }
        val byAction = feedback.groupBy { it.action }
            .mapValues { (_, v) -> v.size }
        val byKind = ackedFeedback
            .groupBy { sigById.getValue(it.signalId).kind }
            .mapValues { (_, fbs) ->
                fbs.groupBy { it.action }.mapValues { (_, v) -> v.size }
            }
        return FeedbackStats(
            totalSignals = signals.size,
            totalFeedback = feedback.size,
            totalAcked = ackedFeedback.map { it.signalId }.toSet().size,
            byAction = byAction,
            byKind = byKind,
        )
    }

    fun recommend(stats: FeedbackStats): ThresholdRecommendation {
        val total = stats.byAction.values.sum()
        val dismissed = stats.byAction["dismissed"] ?: 0
        val useful = stats.byAction["useful"] ?: 0
        val pinned = stats.byAction["pinned"] ?: 0
        val noise = stats.byAction["noise"] ?: 0

        val globalOffset: Float = when {
            total < MIN_ACKS_FOR_GLOBAL -> 0f
            (dismissed + noise).toFloat() / total > 0.6f -> 0.10f
            (dismissed + noise).toFloat() / total > 0.4f -> 0.05f
            (useful + pinned).toFloat() / total > 0.4f -> -0.05f
            else -> 0f
        }

        val perKind = stats.byKind.mapNotNull { (kind, actions) ->
            val kTotal = actions.values.sum()
            if (kTotal < MIN_ACKS_PER_KIND) return@mapNotNull null
            val kDismiss = (actions["dismissed"] ?: 0) + (actions["noise"] ?: 0)
            val kUseful = (actions["useful"] ?: 0) + (actions["pinned"] ?: 0)
            val offset = when {
                kDismiss.toFloat() / kTotal > 0.6f -> 0.10f
                kUseful.toFloat() / kTotal > 0.4f -> -0.05f
                else -> 0f
            }
            if (offset == 0f) null else kind to offset
        }.toMap()

        val rate = if (total > 0) "%.2f".format(dismissed.toFloat() / total) else "n/a"
        val basis = "totalFeedback=$total dismiss-rate=$rate " +
            "(d=$dismissed n=$noise u=$useful p=$pinned), " +
            "ackedSignals=${stats.totalAcked}/${stats.totalSignals}"

        return ThresholdRecommendation(
            ts = Instant.now().toString(),
            baseThreshold = BASE_THRESHOLD,
            globalOffset = globalOffset,
            perKindOffset = perKind,
            basis = basis,
        )
    }

    /** Live re-derive: read both jsonls + analyze + recommend. */
    fun current(): ThresholdRecommendation {
        val signals = Signals.readAll()
        val feedback = Feedback.readAll()
        return recommend(analyze(signals, feedback))
    }

    /** Effective threshold for [ProactiveLoop] — base + global offset, clamped
     *  to [0,1]. Per-kind offset is *not* applied here because kind isn't
     *  known until after the ctx-model call (which the threshold is gating). */
    fun effectiveThreshold(rec: ThresholdRecommendation? = null): Float {
        val r = rec ?: current()
        return (r.baseThreshold + r.globalOffset).coerceIn(0f, 1f)
    }
}
