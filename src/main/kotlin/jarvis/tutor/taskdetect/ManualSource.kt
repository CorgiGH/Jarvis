package jarvis.tutor.taskdetect

import jarvis.tutor.TaskRepo
import org.jetbrains.exposed.sql.Database

/**
 * Surfaces existing TasksTable rows back through the detector pipeline
 * so the aggregator preserves manual-entry tasks. ExternalId = task.id.
 */
class ManualSource(
    private val db: Database,
    private val userId: String,
) : TaskDetector {
    override val sourceId = "manual"
    override suspend fun discover(): List<DetectedTask> {
        val tasks = TaskRepo(db).listForUser(userId)
        return tasks.map { t ->
            DetectedTask(
                sourceId = sourceId,
                externalId = t.id,
                subject = t.subject,
                title = t.title,
                deadline = t.deadline,
                problemPath = t.problemRef.path.takeIf { it.isNotBlank() },
                sourceUrl = null,
                rawMetadata = mapOf("status" to t.status.name),
            )
        }
    }
}
