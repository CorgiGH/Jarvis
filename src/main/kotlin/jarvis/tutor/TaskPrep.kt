package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object TaskPrepTable : Table("task_prep") {
    val taskId = varchar("task_id", 26)
    val generatedAt = timestamp("generated_at")
    val version = integer("version")
    val problemsJson = text("problems_json")
    val drillsJson = text("drills_json")
    val railJson = text("rail_json")
    override val primaryKey = PrimaryKey(taskId)
}

data class TaskPrep(
    val taskId: String,
    val generatedAt: Instant,
    val version: Int,
    val problemsJson: String,
    val drillsJson: String,
    val railJson: String,
)

class TaskPrepRepo(private val db: Database) {
    fun upsert(p: TaskPrep) = transaction(db) {
        TaskPrepTable.deleteWhere { TaskPrepTable.taskId eq p.taskId }
        TaskPrepTable.insert {
            it[taskId] = p.taskId
            it[generatedAt] = p.generatedAt
            it[version] = p.version
            it[problemsJson] = p.problemsJson
            it[drillsJson] = p.drillsJson
            it[railJson] = p.railJson
        }
    }

    fun findByTaskId(taskId: String): TaskPrep? = transaction(db) {
        TaskPrepTable.selectAll()
            .where { TaskPrepTable.taskId eq taskId }
            .singleOrNull()?.let(::rowToPrep)
    }

    private fun rowToPrep(row: ResultRow) = TaskPrep(
        taskId = row[TaskPrepTable.taskId],
        generatedAt = row[TaskPrepTable.generatedAt],
        version = row[TaskPrepTable.version],
        problemsJson = row[TaskPrepTable.problemsJson],
        drillsJson = row[TaskPrepTable.drillsJson],
        railJson = row[TaskPrepTable.railJson],
    )
}
