package jarvis.tutor

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Phase-3 Area C, GROUP 5 — mock-exam SYNC persistence (H13 / CONTRADICTION F1).
 *
 * THE FREEZE: mock-exam is SYNC, 200-ONLY. This table is **NOT** the forbidden `mock_exam_jobs`
 * async-job table — it has no job status, no poll cursor, no 202 surface. It is the synchronous
 * result-of-record: `start` persists the assembled question set; `submit` grades SYNCHRONOUSLY and
 * stamps the score + per-KC results + narrative inline (same request, HTTP 200); `GET /{id}/result`
 * re-reads it. There is no async path: a row is either un-submitted (questions only) or fully graded.
 *
 * Per-user scoped (references UsersTable.id) so PRAGMA foreign_keys=ON holds and the me/delete
 * cascade can append it. Registered in TutorMigration alongside the Phase-1 tables.
 */
object MockExamsTable : Table("mock_exams") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val subject = varchar("subject", 64).nullable()
    /** The assembled question set (serialized ApiMockExamQuestion list), written at `start`. */
    val questionsJson = text("questions_json")
    /** The graded per-KC results (serialized ApiMockExamKcResult list); NULL until `submit`. */
    val kcResultsJson = text("kc_results_json").nullable()
    /** The aggregate exam score in [0,1]; NULL until `submit`. */
    val score = double("score").nullable()
    /** The server-authored result narrative; NULL until `submit`. */
    val narrative = text("narrative").nullable()
    val createdAt = timestamp("created_at")
    val submittedAt = timestamp("submitted_at").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, createdAt) }
}
