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
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.VerificationStatus
import jarvis.tutor.csrfProtect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
                val questions = kcs.take(n).map { kc -> toQuestion(kc) }

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
                    }
                }
                call.respond(HttpStatusCode.OK, ApiMockExamStartReply(exam_id = examId, questions = questions))
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
                val results = questions.map { q ->
                    val response = answerByQ[q.question_id]?.response.orEmpty()
                    val kc = kcById[q.kc_id]
                    // The honest per-KC trust badge from the B8 store (faithful/unverified/...).
                    val kcStatus = VerifyAdmin.resolveStatus(db, q.kc_id, kc)
                    gradeOpenQuestion(q, response, kc, kcStatus)
                }

                val score = if (results.isEmpty()) 0.0 else results.sumOf { it.score } / results.size
                val narrative = mockExamNarrative(results.size, results.count { it.correct })
                val now = Instant.now()
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
                    }
                }
                // ALWAYS 200 (the FREEZE).
                call.respond(
                    HttpStatusCode.OK,
                    ApiMockExamSubmitReply(score = score, kc_results = results, narrative = narrative),
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
                        ApiMockExamResultReply(
                            exam_id = examId,
                            score = score,
                            kc_results = results,
                            narrative = row[MockExamsTable.narrative].orEmpty(),
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
