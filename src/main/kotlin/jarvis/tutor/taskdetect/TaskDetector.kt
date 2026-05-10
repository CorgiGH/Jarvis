package jarvis.tutor.taskdetect

import java.time.Instant

/**
 * Phase 6 task source. Each source produces a list of [DetectedTask]s
 * the aggregator dedups + upserts into TasksTable + DetectedTaskMappingTable.
 */
interface TaskDetector {
    val sourceId: String
    suspend fun discover(): List<DetectedTask>
}

data class DetectedTask(
    val sourceId: String,
    val externalId: String,
    val subject: String,
    val title: String,
    val deadline: Instant,
    val problemPath: String? = null,
    val sourceUrl: String? = null,
    val rawMetadata: Map<String, String> = emptyMap(),
)

/**
 * Aggregates multiple [TaskDetector]s; calls discover() on each;
 * dedupes by (sourceId, externalId); per-source failures are logged + skipped.
 */
class TaskDetectorAggregator(private val sources: List<TaskDetector>) {
    suspend fun discoverAll(): List<DetectedTask> {
        val seen = mutableSetOf<Pair<String, String>>()
        val out = mutableListOf<DetectedTask>()
        for (src in sources) {
            val tasks = try {
                src.discover()
            } catch (e: Exception) {
                System.err.println("[detector] source ${src.sourceId} failed: ${e.message?.take(160)}")
                continue
            }
            for (t in tasks) {
                val key = t.sourceId to t.externalId
                if (key in seen) continue
                seen += key
                out += t
            }
        }
        return out
    }
}
