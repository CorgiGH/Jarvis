package jarvis.tutor

import jarvis.tutor.verify.ClaimKind
import jarvis.tutor.verify.TrustSync
import jarvis.web.ApiLessonReply
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import org.jetbrains.exposed.sql.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * INV-3.5 (spec §3.8) — the CI diff against the interface-signatures-lock. This test makes INV-3.5
 * a REAL, always-on CI check (it runs in `gradle :check`, the backend CI job) — closing the
 * "INV-3.5 does not exist anywhere" gap found in the Plan-2 recon (§0.7). Every assertion pins ONE
 * frozen signature and cites its lock section in KDoc. A renamed/removed frozen column or wire field
 * FAILS here with the exact mismatch. New Plan-2 tables are ADDITIVE: they must not collide with any
 * frozen table name (last test).
 *
 * These pins are NOT redundant with LiveShapeColumnsMigrationTest (which pins the live-DB DELTA the
 * Plan-1 flip closes) or TrustSyncTest (which pins the D9 dump CONTENTS): this pins the frozen TYPE
 * shapes themselves, so a code change that drifts from the lock is caught at the type boundary.
 */
class SignatureLockPinTest {

    private fun colNames(t: Table): List<String> = t.columns.map { it.name }
    @OptIn(ExperimentalSerializationApi::class)
    private fun SerialDescriptor.elementNamesList(): List<String> =
        (0 until elementsCount).map { getElementName(it) }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> wireFieldNames(): List<String> =
        serialDescriptor<T>().elementNamesList()

    /** lock §R — exam_dates is the frozen one-row-per-(user,subject) schedule pointer. */
    @Test
    fun `exam_dates columns are frozen`() {
        assertEquals(
            listOf("id", "user_id", "subject", "start_at", "created_at"),
            colNames(ExamDatesTable),
        )
    }

    /** master-plan CHANGE-1 — fsrs_cards is the 828-card store; 15 frozen columns. */
    @Test
    fun `fsrs_cards has exactly the 15 frozen columns`() {
        assertEquals(
            listOf(
                "id", "user_id", "source", "source_ref", "front", "back", "difficulty",
                "stability", "retrievability", "due_at", "last_reviewed_at", "lapses",
                "kc_id", "status", "paused_at",
            ),
            colNames(FsrsCardsTable),
        )
    }

    /** lock §I.1 (RF1) — the 7-column trust store + the frozen D9 audit-column allowlist. */
    @Test
    fun `kc_verification_status has exactly the 7 frozen columns`() {
        assertEquals(
            listOf(
                "kc_id", "status", "last_audit_run_id", "content_hash",
                "lecture_grounded", "source_span_hash", "updated_at",
            ),
            colNames(KcVerificationStatusTable),
        )
    }

    /** lock §I — the 5 wire literals (4 authored + runtime-only `failed`). */
    @Test
    fun `VerificationStatus enum literals are frozen`() {
        assertEquals(
            listOf("unverified", "pending", "faithful", "uncertain", "failed"),
            VerificationStatus.entries.map { it.name },
        )
    }

    /** lock §A — the 4 phase literals (wire == lowercase name). */
    @Test
    fun `Phase enum literals are frozen`() {
        assertEquals(
            listOf("intro", "practice", "retrieval", "mastered"),
            Phase.entries.map { it.name },
        )
    }

    /** lock §K + the §K AMENDMENT — the 7 frozen claim kinds (UPPER wire names). */
    @Test
    fun `ClaimKind literals are frozen (7, incl the grounded-teaching amendment)`() {
        assertEquals(
            listOf(
                "DEFINITION", "INVARIANT", "GRADER_RULE", "MISCONCEPTION_REFUTATION",
                "STEM", "EXPLANATION", "WORKED_EXAMPLE",
            ),
            ClaimKind.entries.map { it.name },
        )
    }

    /** lock §NEW-L (Plan-3 amended) — the lesson reply field names IN ORDER; `beats` appended 12th. */
    @Test
    fun `ApiLessonReply wire field names match the NEW-L list in order`() {
        assertEquals(
            listOf(
                "kcId", "kc_name_ro", "kc_name_en", "concrete_question_ro", "echo_source_ro",
                "prediction_options", "term_ro", "definition_ro", "explanation_ro",
                "worked_example_ro", "provenance", "beats",
            ),
            wireFieldNames<ApiLessonReply>(),
        )
    }

    /** lock §I.1 — the D9 VerdictRow type (TrustSyncTest pins the DUMP; this pins the TYPE). */
    @Test
    fun `TrustSync VerdictRow field names are the 7 frozen allowlist keys`() {
        assertEquals(
            listOf(
                "kc_id", "status", "last_audit_run_id", "content_hash",
                "lecture_grounded", "source_span_hash", "updated_at",
            ),
            wireFieldNames<TrustSync.VerdictRow>(),
        )
    }

    /** lock §C — the /queue/today element field names. */
    @Test
    fun `QueueItem wire field names match the lock §C list`() {
        assertEquals(
            listOf(
                "kc_id", "kc_name_ro", "kc_name_en", "subject", "phase", "mastery_ewma",
                "fsrs_card_id", "verification_status", "worked_example_first", "mode",
            ),
            wireFieldNames<QueueItem>(),
        )
    }

    /** INV-3.5 additivity — NEW Plan-2 tables must not collide with any frozen table name. */
    @Test
    fun `Plan-2 tables are disjoint from the frozen table names`() {
        val frozen = setOf(
            "exam_dates", "fsrs_cards", "kc_verification_status", "report_wrong",
            "kc_mastery", "attempts", "verification_audit", "users",
        )
        val plan2 = setOf(
            ProblemsTable, ProblemRubricItemsTable, ProblemKcLinksTable,
            GradingModelsTable, GradeComponentsTable, GlossaryTermsTable, ExamScheduleRowsTable,
        ).map { it.tableName }.toSet()
        val collisions = plan2.intersect(frozen)
        assertTrue(collisions.isEmpty(), "Plan-2 tables collide with frozen tables: $collisions")
        assertEquals(7, plan2.size, "expected exactly the 7 new Plan-2 tables")
    }
}
