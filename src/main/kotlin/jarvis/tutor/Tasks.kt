package jarvis.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

enum class TaskStatus { TODO, ACTIVE, SUBMITTED, GRADED, ARCHIVED }

@Serializable
data class ContentRef(val repo: String, val path: String, val sha: String)

@Serializable
data class ScratchpadRef(val docId: String, val version: Int)

@Serializable
data class SubmissionRef(val docId: String, val version: Int, val submittedAt: String)

@Serializable
data class GradeRecord(
    val score: Double,
    val rubricVersion: String,
    val gradedAt: String,
    val modelId: String,
)

data class Task(
    val id: String,
    val userId: String,
    val subject: String,
    val title: String,
    val deadline: Instant,
    val problemRef: ContentRef,
    val conceptRefs: List<ContentRef>,
    val rubricRef: ContentRef,
    val scratchpad: ScratchpadRef?,
    val submission: SubmissionRef?,
    val grade: GradeRecord?,
    val cardRefs: List<String>,
    val status: TaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

object TasksTable : Table("tasks") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val subject = varchar("subject", 32)
    val title = varchar("title", 256)
    val deadline = timestamp("deadline")
    val problemRefJson = text("problem_ref_json")
    val conceptRefsJson = text("concept_refs_json")
    val rubricRefJson = text("rubric_ref_json")
    val scratchpadJson = text("scratchpad_json").nullable()
    val submissionJson = text("submission_json").nullable()
    val gradeJson = text("grade_json").nullable()
    val cardRefsJson = text("card_refs_json")
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

class TaskRepo(private val db: Database) {
    fun insert(t: Task) = transaction(db) {
        TasksTable.insert {
            it[id] = t.id
            it[userId] = t.userId
            it[subject] = t.subject
            it[title] = t.title
            it[deadline] = t.deadline
            it[problemRefJson] = TutorTypes.tutorJson.encodeToString(ContentRef.serializer(), t.problemRef)
            it[conceptRefsJson] = TutorTypes.tutorJson.encodeToString(
                ListSerializer(ContentRef.serializer()), t.conceptRefs)
            it[rubricRefJson] = TutorTypes.tutorJson.encodeToString(ContentRef.serializer(), t.rubricRef)
            it[scratchpadJson] = t.scratchpad?.let { sp ->
                TutorTypes.tutorJson.encodeToString(ScratchpadRef.serializer(), sp)
            }
            it[submissionJson] = t.submission?.let { s ->
                TutorTypes.tutorJson.encodeToString(SubmissionRef.serializer(), s)
            }
            it[gradeJson] = t.grade?.let { g ->
                TutorTypes.tutorJson.encodeToString(GradeRecord.serializer(), g)
            }
            it[cardRefsJson] = TutorTypes.tutorJson.encodeToString(
                ListSerializer(String.serializer()), t.cardRefs)
            it[status] = t.status.name
            it[createdAt] = t.createdAt
            it[updatedAt] = t.updatedAt
        }
    }

    fun findById(id: String): Task? = transaction(db) {
        TasksTable.selectAll().where { TasksTable.id eq id }.singleOrNull()?.toTask()
    }

    fun listForUser(userId: String): List<Task> = transaction(db) {
        TasksTable.selectAll().where { TasksTable.userId eq userId }
            .orderBy(TasksTable.deadline, SortOrder.ASC)
            .map { it.toTask() }
    }

    private fun ResultRow.toTask() = Task(
        id = this[TasksTable.id],
        userId = this[TasksTable.userId],
        subject = this[TasksTable.subject],
        title = this[TasksTable.title],
        deadline = this[TasksTable.deadline],
        problemRef = TutorTypes.tutorJson.decodeFromString(ContentRef.serializer(), this[TasksTable.problemRefJson]),
        conceptRefs = TutorTypes.tutorJson.decodeFromString(
            ListSerializer(ContentRef.serializer()), this[TasksTable.conceptRefsJson]),
        rubricRef = TutorTypes.tutorJson.decodeFromString(ContentRef.serializer(), this[TasksTable.rubricRefJson]),
        scratchpad = this[TasksTable.scratchpadJson]?.let {
            TutorTypes.tutorJson.decodeFromString(ScratchpadRef.serializer(), it)
        },
        submission = this[TasksTable.submissionJson]?.let {
            TutorTypes.tutorJson.decodeFromString(SubmissionRef.serializer(), it)
        },
        grade = this[TasksTable.gradeJson]?.let {
            TutorTypes.tutorJson.decodeFromString(GradeRecord.serializer(), it)
        },
        cardRefs = TutorTypes.tutorJson.decodeFromString(
            ListSerializer(String.serializer()), this[TasksTable.cardRefsJson]),
        status = TaskStatus.valueOf(this[TasksTable.status]),
        createdAt = this[TasksTable.createdAt],
        updatedAt = this[TasksTable.updatedAt],
    )
}
