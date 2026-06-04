package jarvis.tutor

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Phase-1 data-model new tables (master-impl-plan-v2 §2.1 CHANGE 4–10).
 *
 * All enum-typed columns are VARCHAR, never native enum (wire/stored literal == enum name).
 * Every column is either part of a brand-new table (no pre-existing rows ⇒ client-side
 * `.default()` at INSERT is sufficient, no ALTER backfill needed) or nullable.
 *
 * These tables are registered in `TutorMigration.migrate` (wired into `installTutorContext`)
 * via `SchemaUtils.createMissingTablesAndColumns(...)`. The user-scoped ones
 * (session_summaries, attempts, report_wrong, exam_dates) reference UsersTable.id so
 * PRAGMA foreign_keys=ON is honored and the me/delete cascade (B6, Task 13) can append them.
 */

/** CHANGE 4 — session-wrap summaries (Surface 5). */
object SessionSummariesTable : Table("session_summaries") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val startedAt = timestamp("started_at")
    val closedAt = timestamp("closed_at")
    val cardsReviewed = integer("cards_reviewed")
    val masteryDeltaJson = text("mastery_delta_json")
    val narrative = text("narrative").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** CHANGE 5 — per-attempt log (calibration / far-transfer / self-explanation).
 *  Column is `student_confidence` (H1) — NOT `confidence` (the existing grader-confidence
 *  HIGH|LOW reply). `student_confidence` ∈ DEFINITELY|MAYBE|GUESS|IDK (nullable). */
object AttemptsTable : Table("attempts") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val kcId = varchar("kc_id", 64)
    val taskId = varchar("task_id", 26)
    val problemId = varchar("problem_id", 64)
    val phase = varchar("phase", 16)
    val studentConfidence = varchar("student_confidence", 12).nullable()
    val correct = bool("correct")
    val score = double("score")
    val scaffoldLevel = integer("scaffold_level") // 0..4
    val isFarTransfer = bool("is_far_transfer").default(false)
    val selfExplanation = text("self_explanation").nullable()
    val recorded = bool("recorded")
    val gradedAt = timestamp("graded_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, userId, kcId, gradedAt) }
}

/** CHANGE 6 — audit-of-record; one row per KC-claim per run. No UI consumer
 *  (owner-CLI / forensic only). NOT user-scoped (keys on kc_id) ⇒ not in me/delete cascade. */
object VerificationAuditTable : Table("verification_audit") {
    val id = varchar("id", 26)
    val claimId = varchar("claim_id", 96)
    val kcId = varchar("kc_id", 64)
    val subject = varchar("subject", 64)
    val claimKind = varchar("claim_kind", 32) // DEFINITION|INVARIANT|GRADER_RULE|MISCONCEPTION_REFUTATION|STEM
    val status = varchar("status", 16)
    val doc = varchar("doc", 256)
    val page = integer("page").nullable()
    val pageAnchorStatus = varchar("page_anchor_status", 16) // LIVE|DEGRADED|NONE
    val spanStart = integer("span_start").nullable()
    val spanEnd = integer("span_end").nullable()
    val relocatedOffset = integer("relocated_offset").nullable()
    val fuzzyDistance = integer("fuzzy_distance")
    val familyA = varchar("family_a", 24)
    val familyB = varchar("family_b", 24)
    val nonllmLeg = varchar("nonllm_leg", 16) // SYMPY|TEST_EXEC|HUMAN_GOLD|NONE
    val nonllmResult = text("nonllm_result").nullable()
    val agree = bool("agree")
    val roundtripPass = bool("roundtrip_pass")
    val collapsedToOneFamily = bool("collapsed_to_one_family")
    val auditedAt = timestamp("audited_at")
    val auditRunId = varchar("audit_run_id", 64)
    val notes = text("notes").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** CHANGE 7 — grading fail-safe trail. `resolution` ∈ OPEN|REVERIFIED_FAITHFUL|RETRACTED. */
object ReportWrongTable : Table("report_wrong") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val kcId = varchar("kc_id", 64)
    val cardId = varchar("card_id", 26).nullable()
    val gradeAttemptRaw = text("grade_attempt_raw")
    val reportedAt = timestamp("reported_at")
    val resolution = varchar("resolution", 24).nullable() // OPEN|REVERIFIED_FAITHFUL|RETRACTED
    override val primaryKey = PrimaryKey(id)
}

/** CHANGE 9 — exam dates (Day-Of, H14). Added to me/delete cascade (B6, Task 13). */
object ExamDatesTable : Table("exam_dates") {
    val id = varchar("id", 26)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val subject = varchar("subject", 64)
    val startAt = timestamp("start_at")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** CHANGE 10 — the runtime trust store (B8). SOLE runtime source of truth for a KC's
 *  resolved trust status. The YAML `verification_status` is the authored seed only and is
 *  NEVER written back at runtime. Client-side default 'unverified' is fine (new table,
 *  INSERT-time — no ALTER backfill needed). */
object KcVerificationStatusTable : Table("kc_verification_status") {
    val kcId = varchar("kc_id", 64)
    val status = varchar("status", 16).default("unverified")
    val lastAuditRunId = varchar("last_audit_run_id", 64).nullable()
    /**
     * D8 content-hash staleness gate. The KC-level fingerprint (sha256_8 over the KC's audited claim
     * texts + cited spans, [jarvis.content.ContentReconcile.kcContentHash]) AT the moment of the audit
     * that wrote [status]. The serve gate (`/verify/{kcId}/status`) shows `faithful` ONLY when
     * `hash(current serving content) == content_hash`; a mismatch (lecture edited after the audit) or a
     * NULL (legacy/partial row that can't prove a match) falls CLOSED to `HonestFloor.UNVERIFIED` —
     * never a lying "matches your lecture" badge over text that was never checked.
     *
     * Nullable + additive: the live table has 0 rows, so `createMissingTablesAndColumns` adds the bare
     * `ALTER … ADD COLUMN content_hash` with no backfill needed (any pre-existing row would read NULL =
     * fail-closed, which is the correct honest default). Mirrors the Phase-1 additive-column pattern.
     */
    val contentHash = varchar("content_hash", 16).nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(kcId)
}
