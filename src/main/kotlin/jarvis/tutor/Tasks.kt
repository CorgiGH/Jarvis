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
import org.jetbrains.exposed.sql.update
import java.time.Instant

enum class TaskStatus { TODO, ACTIVE, SUBMITTED, GRADED, ARCHIVED }

@Serializable
enum class CardState { LOCKED, OPEN, COMPLETE, ATTEMPTED_NOT_SOLVED }

@Serializable
data class ProblemProgress(
    val problemId: String,
    val cards: Map<Int, CardState> = emptyMap(),
    @Serializable(InstantIso8601Serializer::class) val completedAt: Instant? = null,
    val hintsUsed: Int = 0,
)

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
    val scratchpadText: String? = null,
    val materialPaths: List<String> = emptyList(),
    val problemRefs: List<ContentRef> = emptyList(),
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
    val scratchpadText = text("scratchpad_text").nullable()
    val materialPaths = text("material_paths").nullable()
    val problemRefsJson = text("problem_refs_json").nullable()
    override val primaryKey = PrimaryKey(id)
    init {
        // Closes the Phase-6 deferred unique-index gap. Two concurrent
        // POST /api/v1/tasks with the same (subject, title) slipped past
        // the pre-INSERT lookup and both inserted; SQLite now rejects
        // the second at the storage layer. Repo wraps the insert in
        // try/catch so the API still returns the surviving row.
        uniqueIndex("idx_tasks_user_subject_title", userId, subject, title)
    }
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
            it[scratchpadText] = t.scratchpadText
            it[TasksTable.materialPaths] = if (t.materialPaths.isEmpty()) null
                else TutorTypes.tutorJson.encodeToString(
                    ListSerializer(String.serializer()), t.materialPaths)
            it[TasksTable.problemRefsJson] = if (t.problemRefs.isEmpty()) null
                else TutorTypes.tutorJson.encodeToString(
                    ListSerializer(ContentRef.serializer()), t.problemRefs)
        }
    }

    fun updateScratchpadText(taskId: String, text: String?, now: Instant = Instant.now()): Boolean = transaction(db) {
        val n = TasksTable.update({ TasksTable.id eq taskId }) {
            it[scratchpadText] = text
            it[updatedAt] = now
        }
        n > 0
    }

    fun updateStatus(taskId: String, newStatus: TaskStatus, updatedAt: Instant): Boolean = transaction(db) {
        val n = TasksTable.update({ TasksTable.id eq taskId }) {
            it[status] = newStatus.name
            it[TasksTable.updatedAt] = updatedAt
        }
        n > 0
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
        scratchpadText = this[TasksTable.scratchpadText],
        materialPaths = this[TasksTable.materialPaths]?.let { json ->
            runCatching {
                TutorTypes.tutorJson.decodeFromString(
                    ListSerializer(String.serializer()), json)
            }.getOrDefault(emptyList())
        } ?: emptyList(),
        problemRefs = this[TasksTable.problemRefsJson]?.let { json ->
            runCatching {
                TutorTypes.tutorJson.decodeFromString(
                    ListSerializer(ContentRef.serializer()), json)
            }.getOrDefault(emptyList())
        } ?: emptyList(),
    )
}
