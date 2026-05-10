package jarvis.tutor.taskdetect

import jarvis.tutor.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

object DetectedTaskMappingTable : Table("detected_task_mapping") {
    val sourceId = varchar("source_id", 64)
    val externalId = varchar("external_id", 256)
    val taskId = varchar("task_id", 26)
    val firstSeenAt = timestamp("first_seen_at")
    val lastSeenAt = timestamp("last_seen_at")
    val userId = varchar("user_id", 26).references(UsersTable.id)
    override val primaryKey = PrimaryKey(sourceId, externalId)
}

class DetectedTaskRepo(private val db: Database) {
    fun findExisting(sourceId: String, externalId: String): String? = transaction(db) {
        DetectedTaskMappingTable.selectAll()
            .where { (DetectedTaskMappingTable.sourceId eq sourceId) and (DetectedTaskMappingTable.externalId eq externalId) }
            .singleOrNull()
            ?.get(DetectedTaskMappingTable.taskId)
    }

    fun upsertMapping(sourceId: String, externalId: String, taskId: String, userId: String,
                      now: Instant = Instant.now()): String = transaction(db) {
        val existing = DetectedTaskMappingTable.selectAll()
            .where { (DetectedTaskMappingTable.sourceId eq sourceId) and (DetectedTaskMappingTable.externalId eq externalId) }
            .singleOrNull()
        if (existing != null) {
            DetectedTaskMappingTable.update({
                (DetectedTaskMappingTable.sourceId eq sourceId) and (DetectedTaskMappingTable.externalId eq externalId)
            }) {
                it[lastSeenAt] = now
            }
            return@transaction existing[DetectedTaskMappingTable.taskId]
        }
        DetectedTaskMappingTable.insert {
            it[DetectedTaskMappingTable.sourceId] = sourceId
            it[DetectedTaskMappingTable.externalId] = externalId
            it[DetectedTaskMappingTable.taskId] = taskId
            it[DetectedTaskMappingTable.userId] = userId
            it[firstSeenAt] = now
            it[lastSeenAt] = now
        }
        taskId
    }
}
