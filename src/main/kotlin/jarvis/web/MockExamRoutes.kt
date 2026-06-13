package jarvis.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import jarvis.content.ContentRepo
import jarvis.content.KnowledgeConcept
import jarvis.tutor.DrillGrader
import jarvis.tutor.GradeScoring
import jarvis.tutor.MockExamsTable
import jarvis.tutor.ProblemsRepo
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.VerificationStatus
import jarvis.tutor.csrfProtect
import jarvis.tutor.grader.GraderChain
import jarvis.tutor.grader.GradeInput
import jarvis.tutor.grader.ItemVerdict
import jarvis.tutor.grader.RubricGrader
import jarvis.tutor.grader.RubricInput
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Path
import java.time.Instant

/**
 * Phase-3 Area C, GROUP 5 — mock-exam (master §2.2 POST /api/v1/mock-exam/{start,submit,result},
 * H13 / CONTRADICTION F1).
 * Mounted from [installTutorRoutes].
 *
 * THE FREEZE (NON-NEGOTIABLE): mock-exam is SYNC, **200-ONLY**. submit ALWAYS returns 200. There is
 * NO 202, NO poll/status route, and NO `mock_exam_jobs` table. Open-ended LLM-graded questions
 * DEGRADE to UNCERTAIN (the kc_result reports verification_status=`uncertain`, correct=false) rather
 * than blocking or going async. The spine's "async grading" is SUPERSEDED — this freeze WINS.
 *
 * Frozen wire shapes (master §2.2 route table line 120, interface-signatures-lock §"Wire consistency"):
 *  - POST /api/v1/mock-exam/start
 *      req  { subject?, n? }
 *      resp 200 { exam_id, questions:[{question_id, kc_id, stem, kind:"deterministic"|"open"}] }
 *  - POST /api/v1/mock-exam/{id}/submit
 *      req  { exam_id, answers:[{question_id, response}] }
 *      resp ALWAYS 200 { score, kc_results:[{question_id, kc_id, correct, score, verification_status}], narrative }
 *  - GET  /api/v1/mock-exam/{id}/result
 *      resp { exam_id, score, kc_results, narrative }
 *
 * Reuses (does NOT rebuild): requireUser auth, csrfProtect for writes, ContentRepo for the corpus,
 * VerifyAdmin.resolveStatus for the per-KC trust badge, GradeScoring for the deterministic correctness/
 * score layer over a grader's rubric, and the G2 DrillGrader + drillGraderLlmFactory seam for open-ended
 * grading. The LLM resolve for any question happens OUTSIDE any txn and DEGRADES to UNCERTAIN on
 * failure/ambiguity.
 *
 * Question classification (P2-5, council fix): EVERY question is `open` (LLM-graded →
 * degrade-to-UNCERTAIN). A KC's `invariant` is the verification re-derivation SEED (a math statement the
 * two-family audit checks), NOT a student answer key — equating an invariant-match with answer
 * correctness scored every "deterministic" question against the wrong oracle. There is NO
 * `canonical_answer` field in the KC schema, so nothing can be scored closed-form. The wire `kind` field
 * is retained ("deterministic"|"open" per §2.2) but `deterministic` is NEVER emitted. PROPER FUTURE FIX:
 * add a `KnowledgeConcept.canonical_answer` field, then classify `deterministic` off THAT (never the
 * `invariant`) — recorded in master-impl-plan-v2 §"Deferred" + interface-signatures-lock.
 */

/** Resolve the content/ directory (matches CuratorRoutes / TrustRoutes / Group-3/4 resolution). */
private fun mockExamContentDir(): Path =
    Path.of(
        System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )

private val mockExamJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Default exam length when the request omits `n`. */
private const val MOCK_EXAM_DEFAULT_N = 10

// ── wire DTOs (master §2.2 line 120, verbatim) ─────────────────────────────────────────────────────

@Serializable
data class ApiMockExamStartRequest(
    val subject: String? = null,
    val n: Int? = null,
    // ── Plan-6 Task 11 (§6.2.4, R-6-Q7) — ADDITIVE request fields ────────────────────────────────────
    // Both default null/empty so legacy `{subject,n}` payloads decode unchanged and keep the legacy
    // result-shaped reply (regression-pinned). When `format_id` is present the start stamps the format
    // structure + timer + phase + synthetic_tag and serves the additive reply shape.
    val format_id: String? = null,
    /** Bank-problem ids to include as rubric-scored questions (REQ-16) alongside the KC questions. */
    val problem_ids: List<String> = emptyList(),
)

@Serializable
data class ApiMockExamStartReply(
    val exam_id: String,
    val questions: List<ApiMockExamQuestion>,
)

@Serializable
data class ApiMockExamQuestion(
    val question_id: String,
    val kc_id: String,
    val stem: String,
    /** "deterministic" | "open" (wire shape, §2.2). P2-5: only "open" is ever emitted — see [toQuestion]
     *  (no canonical-answer field exists, so nothing can be scored closed-form). */
    val kind: String,
)

@Serializable
data class ApiMockExamSubmitRequest(
    val exam_id: String? = null,
    val answers: List<ApiMockExamAnswer> = emptyList(),
)

@Serializable
data class ApiMockExamAnswer(
    val question_id: String,
    val response: String = "",
)

@Serializable
data class ApiMockExamSubmitReply(
    val score: Double,
    val kc_results: List<ApiMockExamKcResult>,
    val narrative: String,
    // ── Plan-6 Task 11 (§6.2.4, R-6-Q7) — ADDITIVE nullable submit fields ───────────────────────────
    // Defaulted so a legacy submit reply (no format, no bank rubric items) serializes byte-compatibly.
    val rubric_result: List<ItemVerdict> = emptyList(),
    val common_errors_ro: List<String> = emptyList(),
    val timer: ApiMockExamTimer? = null,
    val phase: ApiMockExamPhase? = null,
    val synthetic_tag: Boolean? = null,
)

@Serializable
data class ApiMockExamKcResult(
    val question_id: String,
    val kc_id: String,
    val correct: Boolean,
    val score: Double,
    /** Per-KC trust badge data — ONE VerificationStatus enum, lowercase name (wire consistency §I).
     *  A degraded open question reports `uncertain` (the degrade floor). */
    val verification_status: VerificationStatus,
)

@Serializable
data class ApiMockExamResultReply(
    val exam_id: String,
    val score: Double,
    val kc_results: List<ApiMockExamKcResult>,
    val narrative: String,
    // ── Plan-6 Task 11 (§6.2.4, R-6-Q7) — ADDITIVE nullable result fields ────────────────────────────
    // Default values keep legacy result replies byte-compatible (a format-less exam serializes these
    // as null / empty, decoded unchanged by legacy clients). The SYNC-200 freeze is untouched.
    val timer: ApiMockExamTimer? = null,
    val phase: ApiMockExamPhase? = null,
    val rubric_result: List<ItemVerdict> = emptyList(),
    val common_errors_ro: List<String> = emptyList(),
    val synthetic_tag: Boolean? = null,
)

// ── Plan-6 Task 11 (§6.2.4, R-6-Q7) — ADDITIVE wire DTOs ───────────────────────────────────────────

/** Timer anchor surfaced on the start/result replies (REQ-11/17). All fields additive. */
@Serializable
data class ApiMockExamTimer(
    /** Epoch-millis the exam clock started. */
    val started_at_millis: Long,
    /** The total duration the chosen format allots, in seconds. */
    val duration_seconds: Long,
)

/** Current permitted-materials phase surfaced on start/phase/result (REQ-15). All fields additive. */
@Serializable
data class ApiMockExamPhase(
    val phase_index: Int,
    val label_ro: String,
    val materials_allowed_ro: String,
    val phase_count: Int,
)

/** Additive start request fields — a format id + optional bank-problem ids. Legacy callers omit both. */
@Serializable
data class ApiMockExamPhaseReply(
    val exam_id: String,
    val phase_index: Int,
    val phase: ApiMockExamPhase,
)

@Serializable
data class ApiMockExamStartReplyAdditive(
    val exam_id: String,
    val questions: List<ApiMockExamQuestion>,
    // Additive — null/false on a legacy (format-less) start; decoded-unchanged by legacy clients.
    val format: JsonElement? = null,
    val timer: ApiMockExamTimer? = null,
    val phase: ApiMockExamPhase? = null,
    val synthetic_tag: Boolean? = null,
)

fun Route.installMockExamRoutes() {

    // ── POST /api/v1/mock-exam/start ───────────────────────────────────────────────────────────────
    // Assemble a mock exam: pick up to `n` KCs across the (optionally subject-filtered) corpus, one
    // question per KC. P2-5: every question is `open` (no canonical-answer field exists; an `invariant`
    // is the audit seed, NOT an answer key — see [toQuestion]). Persist the assembled set (SYNC
    // result-of-record) so submit grades the SAME questions and result re-reads.
    // An unknown/empty subject ⇒ zero-question exam (degrade cleanly, still 200).
    post("/api/v1/mock-exam/start") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val req = try {
                    mockExamJson.decodeFromString(ApiMockExamStartRequest.serializer(), call.receiveText())
                } catch (_: Exception) {
                    ApiMockExamStartRequest()   // tolerate a missing/garbled body ⇒ all-subjects, default n
                }
                val n = (req.n ?: MOCK_EXAM_DEFAULT_N).coerceAtLeast(0)
                val repo = ContentRepo(mockExamContentDir())
                val filterSubject = req.subject?.takeIf { it.isNotBlank() }

                // Collect candidate KCs across the in-scope subjects (subject filter + deterministic order).
                val kcs = mutableListOf<KnowledgeConcept>()
                val subjects = runCatching { repo.loadManifest().subjects }.getOrDefault(emptyList())
                    .filter { filterSubject == null || it.id == filterSubject }
                for (sub in subjects) {
                    val loaded = runCatching { repo.loadSubject(sub.id) }.getOrNull() ?: continue
                    kcs += loaded.kcs.sortedBy { it.id }
                }
                val kcQuestions = kcs.take(n).map { kc -> toQuestion(kc) }

                // ── Plan-6 Task 11 (ADDITIVE): bank-problem rubric questions + format/timer/phase ──────
                // Bank-problem questions ride FIRST (rubric-scored, REQ-16); KC open questions follow.
                val problemsRepo = ProblemsRepo(db)
                val bankQuestions = req.problem_ids.mapNotNull { pid ->
                    val p = problemsRepo.findById(pid) ?: return@mapNotNull null
                    bankQuestion(pid, bankStatementRo(p.statementJson, pid))
                }
                val questions = bankQuestions + kcQuestions
                val format = MockExamFormats.byId(req.format_id)

                val examId = TutorTypes.ulid()
                val now = Instant.now()
                transaction(db) {
                    MockExamsTable.insert {
                        it[id] = examId
                        it[MockExamsTable.userId] = userId
                        it[subject] = filterSubject
                        it[questionsJson] = mockExamJson.encodeToString(
                            ListSerializer(ApiMockExamQuestion.serializer()), questions,
                        )
                        it[createdAt] = now
                        // Additive columns — only stamped when a format was requested+resolved.
                        if (format != null) {
                            it[formatJson] = format.raw.toString()
                            it[startedAt] = now
                            it[phaseIndex] = 0
                            it[syntheticTag] = format.syntheticDefault
                        }
                    }
                }

                if (format != null) {
                    // ADDITIVE reply shape (format/timer/phase/synthetic_tag) — legacy clients ignore them.
                    call.respond(
                        HttpStatusCode.OK,
                        ApiMockExamStartReplyAdditive(
                            exam_id = examId,
                            questions = questions,
                            format = format.raw,
                            timer = ApiMockExamTimer(now.toEpochMilli(), format.durationSeconds),
                            phase = format.phaseAt(0),
                            synthetic_tag = format.syntheticDefault,
                        ),
                    )
                } else {
                    // LEGACY reply shape — byte-compatible with pre-plan6 callers (regression-pinned).
                    call.respond(HttpStatusCode.OK, ApiMockExamStartReply(exam_id = examId, questions = questions))
                }
            }
        }
    }

    // ── POST /api/v1/mock-exam/{id}/submit ─────────────────────────────────────────────────────────
    // Grade the submitted answers SYNCHRONOUSLY and return the results inline with HTTP 200 — ALWAYS.
    // P2-5: EVERY question is graded OPEN (no canonical-answer field exists; the `invariant` is the audit
    // seed, NOT an answer key). Each question resolves via the G2 DrillGrader OUTSIDE any txn; an
    // unavailable/ambiguous grader DEGRADES that question to UNCERTAIN (verification_status=uncertain,
    // correct=false) — NEVER a 202/4xx/5xx, never a block.
    // The aggregate score = mean of per-question scores. An empty exam ⇒ empty results, score 0.
    post("/api/v1/mock-exam/{id}/submit") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val examId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"exam id required"}"""); return@csrfProtect }
                val req = try {
                    mockExamJson.decodeFromString(ApiMockExamSubmitRequest.serializer(), call.receiveText())
                } catch (_: Exception) {
                    ApiMockExamSubmitRequest()
                }

                // Load THIS user's exam (per-user scoped). Unknown/not-ours ⇒ 404.
                val questions: List<ApiMockExamQuestion>? = transaction(db) {
                    MockExamsTable.selectAll()
                        .where { (MockExamsTable.id eq examId) and (MockExamsTable.userId eq userId) }
                        .map {
                            runCatching {
                                mockExamJson.decodeFromString(
                                    ListSerializer(ApiMockExamQuestion.serializer()),
                                    it[MockExamsTable.questionsJson],
                                )
                            }.getOrDefault(emptyList())
                        }
                        .singleOrNull()
                }
                if (questions == null) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"exam not found"}""")
                    return@csrfProtect
                }

                val answerByQ = req.answers.associateBy { it.question_id }
                val repo = ContentRepo(mockExamContentDir())
                // Resolve each question's KC once (for the invariant + verification_status read).
                val kcById: Map<String, KnowledgeConcept> = run {
                    val ids = questions.map { it.kc_id }.toSet()
                    val m = HashMap<String, KnowledgeConcept>()
                    val subs = runCatching { repo.loadManifest().subjects }.getOrDefault(emptyList())
                    for (sub in subs) {
                        val loaded = runCatching { repo.loadSubject(sub.id) }.getOrNull() ?: continue
                        for (kc in loaded.kcs) if (kc.id in ids) m[kc.id] = kc
                    }
                    m
                }

                // Grade each question. ALL LLM work happens HERE, OUTSIDE any txn (H4), and degrades to
                // UNCERTAIN on failure/ambiguity — never throwing past this point, never going async.
                //
                // P2-5 (council fix): EVERY question is graded OPEN. There is NO canonical-answer field in
                // the KC schema, and a KC's `invariant` is the verification re-derivation SEED, NOT a
                // student answer key — so there is no closed-form oracle to score against. Treating
                // invariant-match as correctness scored every "deterministic" question against the wrong
                // oracle (always 0/false). Until a real `canonical_answer` field exists, the honest path
                // is: grade open via the G2 grader, degrade-to-UNCERTAIN on unavailable/ambiguous —
                // STILL 200, NEVER async (H13 / CONTRADICTION F1).
                // ── Plan-6 Task 11 (ADDITIVE): bank-problem questions grade through the RUBRIC leg
                //    (structural G-item verdicts, REQ-16/17) — NOT the KC open path. KC questions keep
                //    the P2-5 open path unchanged. Both stay SYNCHRONOUS (the SYNC-200 freeze holds).
                val problemsRepo = ProblemsRepo(db)
                val rubricVerdicts = mutableListOf<ItemVerdict>()
                val results = questions.map { q ->
                    val response = answerByQ[q.question_id]?.response.orEmpty()
                    if (q.question_id.startsWith(BANK_QUESTION_PREFIX)) {
                        // Bank-problem question — rubric-scored. q.kc_id carries the bank problem id.
                        val (verdicts, agg) = gradeBankRubric(q.kc_id, response, problemsRepo)
                        rubricVerdicts += verdicts
                        ApiMockExamKcResult(
                            question_id = q.question_id,
                            kc_id = q.kc_id,
                            correct = agg.first,
                            score = agg.second,
                            // Bank-problem rubric grade is structural, not KC-trust-resolved → uncertain
                            // floor (no per-KC badge applies to a bank problem).
                            verification_status = VerificationStatus.uncertain,
                        )
                    } else {
                        val kc = kcById[q.kc_id]
                        // The honest per-KC trust badge from the B8 store (faithful/unverified/...).
                        val kcStatus = VerifyAdmin.resolveStatus(db, q.kc_id, kc)
                        gradeOpenQuestion(q, response, kc, kcStatus)
                    }
                }

                val score = if (results.isEmpty()) 0.0 else results.sumOf { it.score } / results.size
                val narrative = mockExamNarrative(results.size, results.count { it.correct })
                val now = Instant.now()

                // Read the additive format/timer/phase/synthetic columns stamped at start (if any).
                val examMeta = transaction(db) {
                    MockExamsTable.selectAll()
                        .where { (MockExamsTable.id eq examId) and (MockExamsTable.userId eq userId) }
                        .map {
                            Triple(
                                it[MockExamsTable.formatJson],
                                it[MockExamsTable.startedAt],
                                Pair(it[MockExamsTable.phaseIndex], it[MockExamsTable.syntheticTag]),
                            )
                        }
                        .singleOrNull()
                }
                val format = examMeta?.first?.let { parseFormatRaw(it) }
                val startedAtMs = examMeta?.second?.toEpochMilli()
                val phaseIdx = examMeta?.third?.first ?: 0
                val syntheticTag = examMeta?.third?.second
                val timer = if (format != null && startedAtMs != null) {
                    ApiMockExamTimer(startedAtMs, format.durationSeconds)
                } else null
                val phase = format?.phaseAt(phaseIdx)
                // Common-error checklist (REQ-16): the distinct taxonomy codes that fired across the open
                // grades. (Bank rubric verdicts are structural; their failures surface per-G-item, not as
                // a misconception code.) Empty when nothing fired — honest.
                val commonErrors = mockExamCommonErrors(results)

                val rubricResultsJson = if (rubricVerdicts.isEmpty()) null else
                    mockExamJson.encodeToString(ListSerializer(ItemVerdict.serializer()), rubricVerdicts)

                transaction(db) {
                    MockExamsTable.update({
                        (MockExamsTable.id eq examId) and (MockExamsTable.userId eq userId)
                    }) {
                        it[kcResultsJson] = mockExamJson.encodeToString(
                            ListSerializer(ApiMockExamKcResult.serializer()), results,
                        )
                        it[MockExamsTable.score] = score
                        it[MockExamsTable.narrative] = narrative
                        it[submittedAt] = now
                        if (rubricResultsJson != null) it[MockExamsTable.rubricResultsJson] = rubricResultsJson
                    }
                }
                // ALWAYS 200 (the FREEZE). Additive fields default-out for legacy (format-less) exams.
                call.respond(
                    HttpStatusCode.OK,
                    ApiMockExamSubmitReply(
                        score = score,
                        kc_results = results,
                        narrative = narrative,
                        rubric_result = rubricVerdicts,
                        common_errors_ro = commonErrors,
                        timer = timer,
                        phase = phase,
                        synthetic_tag = syntheticTag,
                    ),
                )
            }
        }
    }

    // ── GET /api/v1/mock-exam/{id}/result ──────────────────────────────────────────────────────────
    // Re-read a graded exam (per-user scoped). 404 if not this user's exam or not yet graded.
    get("/api/v1/mock-exam/{id}/result") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val db = ctx.db
            val examId = call.parameters["id"]
                ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"exam id required"}"""); return@requireUser }
            val reply: ApiMockExamResultReply? = transaction(db) {
                MockExamsTable.selectAll()
                    .where { (MockExamsTable.id eq examId) and (MockExamsTable.userId eq userId) }
                    .mapNotNull { row ->
                        val score = row[MockExamsTable.score] ?: return@mapNotNull null   // not yet graded
                        val results = runCatching {
                            mockExamJson.decodeFromString(
                                ListSerializer(ApiMockExamKcResult.serializer()),
                                row[MockExamsTable.kcResultsJson].orEmpty(),
                            )
                        }.getOrDefault(emptyList())
                        // ── Plan-6 Task 11 (ADDITIVE) — re-read the additive columns. All null on a
                        //    legacy (format-less) exam → the reply serializes byte-compatibly.
                        val format = row[MockExamsTable.formatJson]?.let { parseFormatRaw(it) }
                        val startedAtMs = row[MockExamsTable.startedAt]?.toEpochMilli()
                        val phaseIdx = row[MockExamsTable.phaseIndex] ?: 0
                        val syntheticTag = row[MockExamsTable.syntheticTag]
                        val rubricResult = runCatching {
                            row[MockExamsTable.rubricResultsJson]?.let {
                                mockExamJson.decodeFromString(ListSerializer(ItemVerdict.serializer()), it)
                            }
                        }.getOrNull() ?: emptyList()
                        val timer = if (format != null && startedAtMs != null)
                            ApiMockExamTimer(startedAtMs, format.durationSeconds) else null
                        val phase = format?.phaseAt(phaseIdx)
                        ApiMockExamResultReply(
                            exam_id = examId,
                            score = score,
                            kc_results = results,
                            narrative = row[MockExamsTable.narrative].orEmpty(),
                            timer = timer,
                            phase = phase,
                            rubric_result = rubricResult,
                            common_errors_ro = mockExamCommonErrors(results),
                            synthetic_tag = syntheticTag,
                        )
                    }
                    .singleOrNull()
            }
            if (reply == null) {
                call.respond(HttpStatusCode.NotFound, """{"error":"exam result not found"}""")
            } else {
                call.respond(HttpStatusCode.OK, reply)
            }
        }
    }

    // ── POST /api/v1/mock-exam/{id}/phase ──────────────────────────────────────────────────────────
    // Plan-6 Task 11 (§6.2.4, REQ-15) — ADDITIVE: advance the permitted-materials phase. SYNC, 200-ONLY
    // (the FREEZE — no async, no 202). Clamps at the last phase. 404 if not this user's exam.
    post("/api/v1/mock-exam/{id}/phase") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val examId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"exam id required"}"""); return@csrfProtect }

                val current = transaction(db) {
                    MockExamsTable.selectAll()
                        .where { (MockExamsTable.id eq examId) and (MockExamsTable.userId eq userId) }
                        .map { it[MockExamsTable.formatJson] to (it[MockExamsTable.phaseIndex] ?: 0) }
                        .singleOrNull()
                }
                if (current == null) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"exam not found"}""")
                    return@csrfProtect
                }
                val format = current.first?.let { parseFormatRaw(it) }
                val phaseCount = format?.phases?.size ?: 1
                val nextIdx = (current.second + 1).coerceAtMost(phaseCount - 1).coerceAtLeast(0)
                transaction(db) {
                    MockExamsTable.update({
                        (MockExamsTable.id eq examId) and (MockExamsTable.userId eq userId)
                    }) {
                        it[phaseIndex] = nextIdx
                    }
                }
                val phase = format?.phaseAt(nextIdx)
                    ?: ApiMockExamPhase(nextIdx, "Faza ${nextIdx + 1}", "", phaseCount)
                // ALWAYS 200 (the FREEZE).
                call.respond(
                    HttpStatusCode.OK,
                    ApiMockExamPhaseReply(exam_id = examId, phase_index = nextIdx, phase = phase),
                )
            }
        }
    }
}

// ── helpers ────────────────────────────────────────────────────────────────────────────────────────

/**
 * Build a question for a KC.
 *
 * P2-5 (council fix): EVERY mock question is `open` (LLM-graded → degrade-to-UNCERTAIN). A KC's
 * `invariant` is the verification re-derivation SEED (a math statement the two-family audit checks),
 * NOT a student answer key — equating an invariant-match with answer correctness scored every
 * "deterministic" question against the wrong oracle. There is NO `canonical_answer` field in the KC
 * schema, so there is nothing to score a closed-form question against; until one exists, the honest
 * classification is `open`. The wire `kind` field is retained ("deterministic"|"open" per the §2.2
 * lock) but `deterministic` is never emitted.
 *
 * FUTURE FIX (proper, recorded in master-impl-plan-v2 §"Deferred" + interface-signatures-lock):
 * add a `KnowledgeConcept.canonical_answer: String?` field (authored, distinct from `invariant`);
 * then a question MAY be `deterministic` iff `canonical_answer` is non-blank, scored via
 * GradeScoring.answerMatches(canonical_answer, response). DO NOT reuse `invariant` for this.
 */
private fun toQuestion(kc: KnowledgeConcept): ApiMockExamQuestion {
    return ApiMockExamQuestion(
        question_id = "meq-${kc.id}",
        kc_id = kc.id,
        stem = kc.stem_template ?: kc.name_ro,
        // Always `open`: no canonical-answer field exists, so nothing can be scored closed-form.
        kind = "open",
    )
}

/**
 * Grade ONE open-ended question via the G2 DrillGrader, resolved OUTSIDE any txn. On a grader that is
 * unavailable (throws), returns malformed output, or is internally incoherent (ambiguous), the question
 * DEGRADES to UNCERTAIN: verification_status=`uncertain`, correct=false, score=0 — STILL part of a 200
 * reply, NEVER a 202/4xx/5xx and NEVER async (H13 / CONTRADICTION F1).
 */
private fun gradeOpenQuestion(
    q: ApiMockExamQuestion,
    response: String,
    kc: KnowledgeConcept?,
    kcStatus: VerificationStatus,
): ApiMockExamKcResult {
    // The DEGRADE result: the question's grade is UNCERTAIN. We pin verification_status=`uncertain`
    // (the degrade floor) so the degrade is visible in the one frozen per-field enum — never a 202, never
    // a block. This is the H13 / CONTRADICTION F1 behavior the freeze mandates.
    val degraded = ApiMockExamKcResult(
        question_id = q.question_id,
        kc_id = q.kc_id,
        correct = false,
        score = 0.0,
        verification_status = VerificationStatus.uncertain,
    )
    val attempt = try {
        drillGraderLlmFactory().use { llm ->
            runBlocking {
                DrillGrader.grade(
                    problemStatement = q.stem,
                    userAttempt = response,
                    expectedHint = "",
                    llm = llm,
                )
            }
        }
    } catch (e: Exception) {
        System.err.println("[mock-exam] open grader failed for ${q.question_id}: ${e.message?.take(160)}")
        return degraded
    }
    val parsed = attempt.parsed ?: return degraded                 // malformed/ambiguous ⇒ degrade
    if (!GradeScoring.isConfident(parsed)) return degraded         // incoherent grade ⇒ degrade
    // A confident open grade: the deterministic layer (GradeScoring) decides correctness + score, never
    // the LLM's self-reported verdict. The kc_result then carries the KC's REAL resolved trust badge
    // (faithful/unverified/...), exactly like a deterministic question — only a DEGRADE forces uncertain.
    val correct = GradeScoring.correctFromRubric(parsed.rubric)
    val score = GradeScoring.scoreFromRubric(parsed.rubric)
    return ApiMockExamKcResult(
        question_id = q.question_id,
        kc_id = q.kc_id,
        correct = correct,
        score = score,
        verification_status = kcStatus,
    )
}

/** A short server-authored narrative for the mock-exam result pane (RO, learner-facing). */
private fun mockExamNarrative(total: Int, correct: Int): String =
    when {
        total == 0 -> "Examen fără întrebări — nimic de evaluat."
        else -> "Ai răspuns corect la $correct din $total întrebări."
    }

// ─── Plan-6 Task 11 (§6.2.4, R-6-Q7) — ADDITIVE format / phase / rubric helpers ─────────────────────

/** The bank-problem question id prefix. KC questions are `meq-<kcId>`; bank-problem questions are
 *  `meq-prob-<problemId>` — the prefix is how submit routes a question to the RUBRIC leg vs the open
 *  KC path. Both still emit wire `kind:"open"` (P2-5 freeze: never "deterministic"). */
private const val BANK_QUESTION_PREFIX = "meq-prob-"

/** Parsed view of one format from `mock-exam-formats.json` — the fields the routes consume. */
internal data class MockExamFormat(
    val id: String,
    val syntheticDefault: Boolean,
    val durationSeconds: Long,
    val phases: List<MockExamPhaseDef>,
    /** The verbatim format JSON element (re-served on start/result so the UI renders ordering/brackets). */
    val raw: JsonElement,
)

internal data class MockExamPhaseDef(
    val index: Int,
    val labelRo: String,
    val materialsAllowedRo: String,
    val durationSeconds: Long,
)

/**
 * Loads `mock-exam-formats.json` from the classpath ONCE and resolves a format by id. Returns null for
 * an unknown id (caller degrades to a format-less exam — still 200, the SYNC freeze never breaks on a
 * bad format id).
 */
internal object MockExamFormats {
    private val json = Json { ignoreUnknownKeys = true }

    private val byId: Map<String, MockExamFormat> by lazy { loadAll() }

    fun byId(id: String?): MockExamFormat? = id?.let { byId[it] }

    private fun loadAll(): Map<String, MockExamFormat> {
        val text = MockExamFormats::class.java.classLoader
            .getResourceAsStream("practice/mock-exam-formats.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: return emptyMap()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyMap()
        val formatsObj = root.jsonObjectOrNull()?.get("formats")?.jsonObjectOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, MockExamFormat>()
        for ((id, el) in formatsObj) {
            val obj = el.jsonObjectOrNull() ?: continue
            val synthetic = obj["synthetic_default"]?.booleanOrNull() ?: true
            val duration = obj["duration_seconds"]?.longOrNull() ?: 0L
            val phases = obj["phases"]?.jsonArrayOrNull()?.mapIndexedNotNull { idx, pEl ->
                val p = pEl.jsonObjectOrNull() ?: return@mapIndexedNotNull null
                MockExamPhaseDef(
                    index = p["index"]?.intOrNull() ?: idx,
                    labelRo = p["label_ro"]?.stringOrNull() ?: "Faza ${idx + 1}",
                    materialsAllowedRo = p["materials_allowed_ro"]?.stringOrNull() ?: "",
                    durationSeconds = p["duration_seconds"]?.longOrNull() ?: duration,
                )
            } ?: emptyList()
            out[id] = MockExamFormat(
                id = id,
                syntheticDefault = synthetic,
                durationSeconds = duration,
                phases = phases.ifEmpty {
                    listOf(MockExamPhaseDef(0, "Faza 1", "", duration))
                },
                raw = el,
            )
        }
        return out
    }

    // ── tiny JSON accessors (avoid a kotlinx.serialization model for the data-driven format file) ──
    private fun JsonElement.jsonObjectOrNull() =
        (this as? kotlinx.serialization.json.JsonObject)
    private fun JsonElement.jsonArrayOrNull() =
        (this as? kotlinx.serialization.json.JsonArray)
    private fun JsonElement.stringOrNull() =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement.booleanOrNull() =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
    private fun JsonElement.intOrNull() =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonElement.longOrNull() =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
}

/**
 * Re-parse a STORED format JSON string (the `format_json` column = a single format's verbatim element)
 * back into a [MockExamFormat]. Returns null on a blank/garbled column (the exam degrades to format-less
 * — still 200). Used by submit / result / phase to re-derive timer + phases without re-reading the file.
 */
private fun parseFormatRaw(raw: String): MockExamFormat? {
    val el = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        as? kotlinx.serialization.json.JsonObject ?: return null
    val synthetic = (el["synthetic_default"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toBooleanStrictOrNull() ?: true
    val duration = (el["duration_seconds"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toLongOrNull() ?: 0L
    val phases = (el["phases"] as? kotlinx.serialization.json.JsonArray)?.mapIndexedNotNull { idx, pEl ->
        val p = pEl as? kotlinx.serialization.json.JsonObject ?: return@mapIndexedNotNull null
        fun str(k: String) = (p[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
        MockExamPhaseDef(
            index = str("index")?.toIntOrNull() ?: idx,
            labelRo = str("label_ro") ?: "Faza ${idx + 1}",
            materialsAllowedRo = str("materials_allowed_ro") ?: "",
            durationSeconds = str("duration_seconds")?.toLongOrNull() ?: duration,
        )
    } ?: emptyList()
    return MockExamFormat(
        id = (el["subject"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
        syntheticDefault = synthetic,
        durationSeconds = duration,
        phases = phases.ifEmpty { listOf(MockExamPhaseDef(0, "Faza 1", "", duration)) },
        raw = el,
    )
}

/**
 * The common-error checklist surfaced on the result (REQ-16): the DISTINCT taxonomy/misconception codes
 * the open graders fired, in first-seen order. Empty when nothing fired (honest — no fabricated errors).
 * (Bank rubric verdicts are structural; their failures surface per-G-item in `rubric_result`, not here.)
 */
private fun mockExamCommonErrors(@Suppress("UNUSED_PARAMETER") results: List<ApiMockExamKcResult>): List<String> {
    // The kc_result wire shape (frozen, lock §I / :236) carries no misconception field; common errors
    // are surfaced only once a future open-grade path attaches taxonomy codes. For now this is an honest
    // empty list (no ghost data) — the field EXISTS on the wire (REQ-16 surface) and is populated as the
    // misconception plumbing lands. Driven by [results] so the signature stays stable when it does.
    return emptyList()
}

/** Map a [MockExamPhaseDef] (at [phaseIndex]) onto the wire phase DTO. */
private fun MockExamFormat.phaseAt(phaseIndex: Int): ApiMockExamPhase {
    val idx = phaseIndex.coerceIn(0, (phases.size - 1).coerceAtLeast(0))
    val def = phases.getOrNull(idx) ?: MockExamPhaseDef(idx, "Faza ${idx + 1}", "", durationSeconds)
    return ApiMockExamPhase(
        phase_index = idx,
        label_ro = def.labelRo,
        materials_allowed_ro = def.materialsAllowedRo,
        phase_count = phases.size,
    )
}

/**
 * Build a bank-problem question for the mock-exam (REQ-16). The question id carries the
 * [BANK_QUESTION_PREFIX] so submit routes it to the RUBRIC leg; the wire `kind` stays "open" (P2-5
 * freeze — only KC-invariant deterministic grading is forbidden, and this never claims deterministic).
 */
private fun bankQuestion(problemId: String, statementRo: String): ApiMockExamQuestion =
    ApiMockExamQuestion(
        question_id = "$BANK_QUESTION_PREFIX$problemId",
        kc_id = problemId,         // the bank problem id (not a KC id) — used to re-resolve rubric items.
        stem = statementRo,
        kind = "open",             // P2-5: never "deterministic".
    )

/** Pull the RO statement out of a bank problem's `statementJson` ({"ro":"..."}); falls back to the id. */
private fun bankStatementRo(statementJson: String, fallback: String): String {
    val obj = runCatching { Json.parseToJsonElement(statementJson) }
        .getOrNull() as? kotlinx.serialization.json.JsonObject ?: return fallback
    val ro = (obj["ro"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    return ro?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * Grade ONE bank-problem question through the RUBRIC leg (the structural, machine-checkable verdicts —
 * REQ-16/17). Reuses the Task-3 [RubricGrader] via a single-leg [GraderChain] (RUBRIC is a legal
 * non-LLM first leg). The G-items come from the bank problem's authored rubric rows (via [ProblemsRepo]);
 * a rubric item is machine-checkable iff its `penaltyRulesJson` carries a `matcher` block (the
 * RubricInput.matcherJson convention). The grade stays SYNCHRONOUS — no async, the SYNC-200 freeze holds.
 *
 * Returns the per-G-item [ItemVerdict]s AND the aggregate (correct/score) so the submit handler folds
 * them into the kc_result + the additive rubric_result.
 */
private fun gradeBankRubric(
    problemId: String,
    response: String,
    repo: ProblemsRepo,
): Pair<List<ItemVerdict>, Pair<Boolean, Double>> {
    val rows = repo.listRubricItems(problemId)
    if (rows.isEmpty()) return emptyList<ItemVerdict>() to (false to 0.0)
    val inputs = rows.map { r ->
        // The bank row's penaltyRulesJson may carry both a structural matcher and a penalty rule.
        val matcherJson = extractMatcherJson(r.penaltyRulesJson)
        val penaltyJson = extractPenaltyJson(r.penaltyRulesJson)
        RubricInput(
            id = r.id,
            label = r.label,
            points = r.points,
            kind = r.kind,
            allOrNothing = r.allOrNothing,
            matcherJson = matcherJson,
            penaltyJson = penaltyJson,
        )
    }
    val chain = GraderChain.of(listOf(RubricGrader()))
    val result = runBlocking {
        chain.grade(GradeInput(attempt = response, rubricItems = inputs))
    }
    return result.itemVerdicts to (result.correct to result.score)
}

/** Extract the `matcher` sub-object (as JSON text) from a bank row's penaltyRulesJson, or null. */
private fun extractMatcherJson(penaltyRulesJson: String?): String? {
    if (penaltyRulesJson.isNullOrBlank()) return null
    val obj = runCatching { Json.parseToJsonElement(penaltyRulesJson) }
        .getOrNull() as? kotlinx.serialization.json.JsonObject ?: return null
    val matcher = obj["matcher"] ?: return null
    return matcher.toString()
}

/** Extract the `penalty` sub-object (as JSON text) from a bank row's penaltyRulesJson, or null. */
private fun extractPenaltyJson(penaltyRulesJson: String?): String? {
    if (penaltyRulesJson.isNullOrBlank()) return null
    val obj = runCatching { Json.parseToJsonElement(penaltyRulesJson) }
        .getOrNull() as? kotlinx.serialization.json.JsonObject ?: return null
    val penalty = obj["penalty"] ?: return null
    return penalty.toString()
}
