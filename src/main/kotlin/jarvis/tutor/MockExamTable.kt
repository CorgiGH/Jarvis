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

    // ── Plan-6 Task 11 (§6.2.4, R-6-Q7) — ADDITIVE nullable columns ───────────────────────────────────
    // All five are ADDITIVE NULLABLE columns on this already-registered table. Migration.kt is NOT
    // touched: Exposed `createMissingTablesAndColumns` adds nullable columns to a registered table on
    // first boot. Legacy rows (pre-plan6) decode unchanged — every column below is nullable and absent on
    // legacy rows; the existing start/submit/result wire shapes stay byte-compatible (regression-pinned in
    // MockExamAdditiveRouteTest). The SYNC-200 freeze (MockExamRoutes.kt:37) is preserved: these columns
    // carry no job status, no poll cursor; a row is still either un-submitted or fully graded.
    /** The chosen paper FORMAT structure (serialized; phases, sub-part ordering, point brackets,
     *  multiselect flags) from mock-exam-formats.json; NULL on legacy / format-less exams. */
    val formatJson = text("format_json").nullable()
    /** The timer anchor — when the exam clock started; NULL on legacy exams. */
    val startedAt = timestamp("started_at").nullable()
    /** The current permitted-materials phase index (REQ-15); NULL on legacy exams. */
    val phaseIndex = integer("phase_index").nullable()
    /** Per-G-item rubric breakdown (serialized ItemVerdict list) for bank-problem rubric-scored items
     *  (REQ-16/17); NULL until `submit` on a rubric-bearing exam. */
    val rubricResultsJson = text("rubric_results_json").nullable()
    /** Honesty flag (REQ-12): true until a real past paper is digested — every item built from a
     *  lecture/seminar-derived seed is synthetic. NULL on legacy exams. */
    val syntheticTag = bool("synthetic_tag").nullable()

    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, createdAt) }
}
