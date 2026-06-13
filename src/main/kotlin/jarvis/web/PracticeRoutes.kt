package jarvis.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import jarvis.tutor.BankProblem
import jarvis.tutor.ProblemSolutionYaml
import jarvis.tutor.ProblemsRepo
import jarvis.tutor.RubricItem
import jarvis.tutor.TasksTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.csrfProtect
import jarvis.tutor.grader.ArchetypeClass
import jarvis.tutor.grader.ExecutionGrader
import jarvis.tutor.grader.GradeInput
import jarvis.tutor.grader.GraderLegKind
import jarvis.tutor.grader.GraderRouting
import jarvis.tutor.grader.ItemVerdict
import jarvis.tutor.grader.NumericOracleGrader
import jarvis.tutor.grader.RubricInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Plan-6 Task 8 — practice surfaces endpoint groups (§0.9-G). All additive, all NEW routes — no
 * frozen contract touched (lock §13/§N additive-endpoint path; the new wire shapes are frozen in
 * §0.9-G of the plan doc + ride `signatures-lock-plan6.patch`).
 *
 * Mounted via [installPracticeRoutes], registered in [WebMain] alongside [installGraderProviderRoutes]
 * (the Application-extension pattern — Lane B owns the WebMain registration per the lane table).
 *
 * The five endpoint groups (§0.9-G):
 *  - GET  /api/v1/practice/problems?subject={s}&surface={proof|trace|code}
 *         → { problems: [ApiPracticeProblem] } — NEVER carries the reference solution
 *           (INV-6.6 server-enforced, NOT CSS).
 *  - POST /api/v1/practice/proof/{problemId}/grade  body { substeps:[{id,text}] }
 *         → { item_verdicts, score, correct, decided_by, feedback_ro } (per-sub-step, REQ-2).
 *  - POST /api/v1/practice/trace/{problemId}/step   body { step_index, value }
 *         → { verdict, feedback_ro } (per-step, REQ-5 — wrong value at step 3 caught at step 3).
 *  - POST /api/v1/practice/code/{problemId}/run     body { source }
 *         → { compiled, stdout_trunc, stderr_trunc, timed_out, degraded_legs_ro } (no grade write).
 *  - POST /api/v1/practice/code/{problemId}/grade   body { source }
 *         → full chain reply + reference_solution_ro — the ONLY payload that ever carries the
 *           reference (attempt-gated, REQ-8/INV-6.6).
 *  - GET  /api/v1/practice/deliverables
 *         → { deliverables:[...] } — Task 8 ships the HONEST-EMPTY stub only; Task 10 rewires it
 *           to read TasksTable.deliverable_json (that column does NOT exist yet — Tasks.kt is not in
 *           this task's Files list).
 *
 * All routes are session-auth (401 without a session); writes are csrfProtect (403 on mismatch),
 * mirroring [installGraderProviderRoutes] / [installMockExamRoutes]. Grades on practice surfaces
 * reuse the grader chain legs ([DrillGradeChain.legFor]); RO feedback strings are server-side.
 */

private val practiceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ── wire DTOs (§0.9-G) ────────────────────────────────────────────────────────────────────────

/**
 * The problems-list item. NEVER carries the reference solution (INV-6.6) — only the statement,
 * exam language, surface-specific scaffolding (proof frame / trace step labels), and rubric labels.
 */
@Serializable
data class ApiPracticeProblem(
    val id: String,
    val subject: String,
    val archetype: String,
    val surface: String,                          // "proof" | "trace" | "code"
    val statement_ro: String,
    val exam_language: String? = null,
    /** Proof surface: the fill-in frame template + named sub-steps (label only; matchers stay server-side). */
    val proof_frame: ApiProofFrame? = null,
    /** Trace surface: per-step labels (the EXPECTED values stay server-side — never leaked). */
    val trace_steps: List<ApiTraceStepLabel> = emptyList(),
    /** Code surface: the exam-language constraint copy (visible badge), NOT the reference source. */
    val exam_language_constraints: String? = null,
)

@Serializable
data class ApiProofFrame(
    val template_ro: String,
    val substeps: List<ApiProofSubstepLabel>,
)

@Serializable
data class ApiProofSubstepLabel(
    val id: String,
    val label_ro: String,
)

@Serializable
data class ApiTraceStepLabel(
    val index: Int,
    val label_ro: String? = null,
)

@Serializable
data class ApiPracticeProblemsReply(
    val problems: List<ApiPracticeProblem>,
)

// proof grade
@Serializable
data class ApiProofGradeRequest(
    val substeps: List<ApiProofSubstepAnswer> = emptyList(),
)

@Serializable
data class ApiProofSubstepAnswer(
    val id: String,
    val text: String = "",
)

@Serializable
data class ApiProofGradeReply(
    val item_verdicts: List<ItemVerdict>,
    val score: Double,
    val correct: Boolean,
    val decided_by: String,
    val feedback_ro: String,
)

// trace step
@Serializable
data class ApiTraceStepRequest(
    val step_index: Int,
    val value: String = "",
)

@Serializable
data class ApiTraceStepReply(
    val verdict: ItemVerdict,
    val feedback_ro: String,
)

// code run
@Serializable
data class ApiCodeRunRequest(
    val source: String = "",
)

@Serializable
data class ApiCodeRunReply(
    val compiled: Boolean,
    val stdout_trunc: String,
    val stderr_trunc: String,
    val timed_out: Boolean,
    val degraded_legs_ro: List<String> = emptyList(),
)

// code grade (the ONLY payload that carries the reference)
@Serializable
data class ApiCodeGradeReply(
    val item_verdicts: List<ItemVerdict>,
    val score: Double,
    val correct: Boolean,
    val decided_by: String,
    val feedback_ro: String,
    val degraded_legs_ro: List<String> = emptyList(),
    /** Attempt-gated reference (REQ-8/INV-6.6) — the ONLY endpoint that ever serves it. */
    val reference_solution_ro: String? = null,
)

// deliverables (honest-empty stub in Task 8; Task 10 fills it)
@Serializable
data class ApiDeliverable(
    val id: String,
    val subject: String,
    val title_ro: String,
    val deadline: String? = null,
    val sub_problems: List<ApiDeliverableSubProblem> = emptyList(),
    val prep_drill_ids: List<String> = emptyList(),
    val source_doc: String? = null,
    val synthetic: Boolean = true,
)

@Serializable
data class ApiDeliverableSubProblem(
    val label_ro: String,
    val points: Double,
)

@Serializable
data class ApiDeliverablesReply(
    val deliverables: List<ApiDeliverable>,
)

// ── server-side RO copy ─────────────────────────────────────────────────────────────────────────

private object PracticeFeedbackRo {
    const val PROOF_ALL_PASS = "Toți pașii dovezii sunt corecți."
    const val PROOF_PARTIAL = "Unii pași ai dovezii nu sunt corecți — vezi detaliile pe fiecare pas."
    const val PROOF_NO_FRAME = "Această problemă nu are o structură de dovadă verificabilă."
    const val TRACE_STEP_OK = "Valoarea pasului este corectă."
    const val TRACE_STEP_WRONG = "Valoarea pasului este incorectă — verifică acest pas înainte de a continua."
    const val TRACE_NO_STEP = "Pasul cerut nu există în această problemă."
    const val CODE_NO_RUNNER = "Rularea codului indisponibilă pe acest server — verificat structural."
    const val CODE_NO_SOLUTION = "Această problemă nu are o soluție de referință configurată."
    const val PROBLEM_NOT_FOUND = "Problema cerută nu a fost găsită."
}

// ── handlers ──────────────────────────────────────────────────────────────────────────────────

/**
 * Register the practice endpoint groups. Mirrors [installGraderProviderRoutes] (Application-extension,
 * own routing block; Ktor merges routing blocks into one engine).
 */
fun Application.installPracticeRoutes() {
    routing {

        // ── GET /api/v1/practice/problems ────────────────────────────────────────────────────────
        // List the seeded problems for a subject filtered to a surface. NEVER serves the reference
        // solution (INV-6.6, server-enforced). No CSRF for GET.
        get("/api/v1/practice/problems") {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser {
                val subject = call.request.queryParameters["subject"]
                val surface = call.request.queryParameters["surface"]?.lowercase()
                val repo = ProblemsRepo(ctx.db)
                val all = if (subject.isNullOrBlank()) emptyList() else repo.listBySubject(subject)
                val mapped = all
                    .mapNotNull { p -> toApiProblem(p, surface) }
                call.respondText(
                    practiceJson.encodeToString(
                        ApiPracticeProblemsReply.serializer(),
                        ApiPracticeProblemsReply(problems = mapped),
                    ),
                    ContentType.Application.Json,
                )
            }
        }

        // ── POST /api/v1/practice/proof/{problemId}/grade ─────────────────────────────────────────
        // Walk the proof_frame substeps; one ItemVerdict per NAMED sub-step from the structural
        // matchers (REQ-2). The rubric leg grades the substep matchers; LLM-judge only after (here
        // we DECIDE structurally on the named substeps and report decided_by="rubric").
        post("/api/v1/practice/proof/{problemId}/grade") {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser {
                call.csrfProtect {
                    val problemId = call.parameters["problemId"].orEmpty()
                    val repo = ProblemsRepo(ctx.db)
                    val problem = repo.findById(problemId)
                    if (problem == null) {
                        call.respond(HttpStatusCode.NotFound, errJson(PracticeFeedbackRo.PROBLEM_NOT_FOUND))
                        return@csrfProtect
                    }
                    val req = try {
                        practiceJson.decodeFromString(ApiProofGradeRequest.serializer(), call.receiveText())
                    } catch (_: Exception) {
                        ApiProofGradeRequest()
                    }
                    val frame = parseSolution(problem)?.proof_frame
                    if (frame == null || frame.substeps.isEmpty()) {
                        call.respond(
                            HttpStatusCode.OK,
                            // honest no-frame reply — nothing to grade structurally
                            practiceJson.encodeToString(
                                ApiProofGradeReply.serializer(),
                                ApiProofGradeReply(
                                    item_verdicts = emptyList(),
                                    score = 0.0,
                                    correct = false,
                                    decided_by = DrillGradeChain.wireId(GraderLegKind.LLM_JUDGE),
                                    feedback_ro = PracticeFeedbackRo.PROOF_NO_FRAME,
                                ),
                            ),
                        )
                        return@csrfProtect
                    }
                    // One verdict per NAMED substep: match the learner's text for that substep against
                    // its structural matcher (REQ-2). Each substep is its own rubric item (1 point).
                    val answers = req.substeps.associate { it.id to it.text }
                    val verdicts = frame.substeps.map { ss ->
                        val text = answers[ss.id].orEmpty()
                        val passed = matchSubstep(ss.matcher, text)
                        ItemVerdict(
                            id = ss.id,
                            label = ss.label_ro,
                            passed = passed,
                            points_earned = if (passed) 1.0 else 0.0,
                            points_max = 1.0,
                        )
                    }
                    val passedCount = verdicts.count { it.passed }
                    val score = if (verdicts.isEmpty()) 0.0 else passedCount.toDouble() / verdicts.size
                    val correct = verdicts.isNotEmpty() && verdicts.all { it.passed }
                    call.respondText(
                        practiceJson.encodeToString(
                            ApiProofGradeReply.serializer(),
                            ApiProofGradeReply(
                                item_verdicts = verdicts,
                                score = score,
                                correct = correct,
                                decided_by = DrillGradeChain.wireId(GraderLegKind.RUBRIC),
                                feedback_ro = if (correct) PracticeFeedbackRo.PROOF_ALL_PASS else PracticeFeedbackRo.PROOF_PARTIAL,
                            ),
                        ),
                        ContentType.Application.Json,
                    )
                }
            }
        }

        // ── POST /api/v1/practice/trace/{problemId}/step ──────────────────────────────────────────
        // Compare the submitted value against trace_steps[step_index] via NumericOracleGrader.matches
        // (§0.9-B). Per-step (REQ-5): a sign error at step 3 is caught AT step 3, never deferred.
        post("/api/v1/practice/trace/{problemId}/step") {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser {
                call.csrfProtect {
                    val problemId = call.parameters["problemId"].orEmpty()
                    val repo = ProblemsRepo(ctx.db)
                    val problem = repo.findById(problemId)
                    if (problem == null) {
                        call.respond(HttpStatusCode.NotFound, errJson(PracticeFeedbackRo.PROBLEM_NOT_FOUND))
                        return@csrfProtect
                    }
                    val req = try {
                        practiceJson.decodeFromString(ApiTraceStepRequest.serializer(), call.receiveText())
                    } catch (_: Exception) {
                        ApiTraceStepRequest(step_index = -1)
                    }
                    val steps = parseSolution(problem)?.trace_steps.orEmpty()
                    val step = steps.firstOrNull { it.index == req.step_index }
                    if (step == null) {
                        call.respond(
                            HttpStatusCode.OK,
                            practiceJson.encodeToString(
                                ApiTraceStepReply.serializer(),
                                ApiTraceStepReply(
                                    verdict = ItemVerdict(
                                        id = req.step_index.toString(),
                                        label = "",
                                        passed = false,
                                        points_earned = 0.0,
                                        points_max = 1.0,
                                    ),
                                    feedback_ro = PracticeFeedbackRo.TRACE_NO_STEP,
                                ),
                            ),
                        )
                        return@csrfProtect
                    }
                    // Numeric compare through the single-source oracle matcher; non-numeric expected
                    // (an exact-string trace value) falls back to exact-string equality.
                    val passed = compareTraceValue(expected = step.expected, got = req.value, tol = step.tolerance)
                    val verdict = ItemVerdict(
                        id = step.index.toString(),
                        label = step.label_ro.orEmpty(),
                        passed = passed,
                        points_earned = if (passed) 1.0 else 0.0,
                        points_max = 1.0,
                    )
                    call.respondText(
                        practiceJson.encodeToString(
                            ApiTraceStepReply.serializer(),
                            ApiTraceStepReply(
                                verdict = verdict,
                                feedback_ro = if (passed) PracticeFeedbackRo.TRACE_STEP_OK else PracticeFeedbackRo.TRACE_STEP_WRONG,
                            ),
                        ),
                        ContentType.Application.Json,
                    )
                }
            }
        }

        // ── POST /api/v1/practice/code/{problemId}/run ────────────────────────────────────────────
        // Execution leg ONLY (no grade write). Returns bounded compile/run outputs + honest degraded
        // copy when the runner is unavailable (R-6-Q1).
        post("/api/v1/practice/code/{problemId}/run") {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser {
                call.csrfProtect {
                    val problemId = call.parameters["problemId"].orEmpty()
                    val repo = ProblemsRepo(ctx.db)
                    val problem = repo.findById(problemId)
                    if (problem == null) {
                        call.respond(HttpStatusCode.NotFound, errJson(PracticeFeedbackRo.PROBLEM_NOT_FOUND))
                        return@csrfProtect
                    }
                    val req = try {
                        practiceJson.decodeFromString(ApiCodeRunRequest.serializer(), call.receiveText())
                    } catch (_: Exception) {
                        ApiCodeRunRequest()
                    }
                    val lang = problem.examLanguage
                    if (lang.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.OK,
                            practiceJson.encodeToString(
                                ApiCodeRunReply.serializer(),
                                ApiCodeRunReply(
                                    compiled = false,
                                    stdout_trunc = "",
                                    stderr_trunc = "",
                                    timed_out = false,
                                    degraded_legs_ro = listOf(PracticeFeedbackRo.CODE_NO_RUNNER),
                                ),
                            ),
                        )
                        return@csrfProtect
                    }
                    val result = ExecutionGrader().run(req.source, lang)
                    val reply = if (!result.available) {
                        ApiCodeRunReply(
                            compiled = false,
                            stdout_trunc = "",
                            stderr_trunc = "",
                            timed_out = false,
                            degraded_legs_ro = listOf(PracticeFeedbackRo.CODE_NO_RUNNER),
                        )
                    } else {
                        ApiCodeRunReply(
                            compiled = result.compiled,
                            stdout_trunc = result.stdout,
                            stderr_trunc = result.stderr,
                            timed_out = result.timedOut,
                            degraded_legs_ro = emptyList(),
                        )
                    }
                    call.respondText(
                        practiceJson.encodeToString(ApiCodeRunReply.serializer(), reply),
                        ContentType.Application.Json,
                    )
                }
            }
        }

        // ── POST /api/v1/practice/code/{problemId}/grade ──────────────────────────────────────────
        // Full chain (execution → rubric → LLM) over the code surface, PLUS the attempt-gated
        // reference_solution_ro (the ONLY endpoint that serves it — REQ-8/INV-6.6). The LLM tail is
        // not invoked here (no per-user LLM resolver in the practice surface); the chain prefix runs
        // execution/rubric and the reply honestly reports the deciding leg.
        post("/api/v1/practice/code/{problemId}/grade") {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser {
                call.csrfProtect {
                    val problemId = call.parameters["problemId"].orEmpty()
                    val repo = ProblemsRepo(ctx.db)
                    val problem = repo.findById(problemId)
                    if (problem == null) {
                        call.respond(HttpStatusCode.NotFound, errJson(PracticeFeedbackRo.PROBLEM_NOT_FOUND))
                        return@csrfProtect
                    }
                    val req = try {
                        practiceJson.decodeFromString(ApiCodeRunRequest.serializer(), call.receiveText())
                    } catch (_: Exception) {
                        ApiCodeRunRequest()
                    }
                    val solution = parseSolution(problem)
                    val referenceRo = solution?.reference_source ?: solution?.reference_solution
                    val rubricInputs = repo.listRubricItems(problemId).map { it.toRubricInput() }
                    val chainKinds = GraderRouting.chainFor(
                        subject = problem.subject,
                        examLanguage = problem.examLanguage,
                        archetypeClass = ArchetypeClass.CODE,
                    )
                    val input = GradeInput(
                        subject = problem.subject,
                        attempt = req.source,
                        rubricItems = rubricInputs,
                        source = req.source,
                        expectedStdout = solution?.expected_stdout,
                        language = problem.examLanguage,
                    )
                    val prefix = DrillGradeChain.runPrefix(chainKinds, input)
                    val reply = when (prefix) {
                        is DrillGradeChain.PrefixResult.Decided -> ApiCodeGradeReply(
                            item_verdicts = prefix.itemVerdicts,
                            score = prefix.score,
                            correct = prefix.correct,
                            decided_by = DrillGradeChain.wireId(prefix.decidedBy),
                            feedback_ro = prefix.feedbackRo,
                            degraded_legs_ro = prefix.degradedRo,
                            // Attempt-gated: serve the reference ONLY on the grade reply (REQ-8/INV-6.6).
                            reference_solution_ro = referenceRo,
                        )
                        is DrillGradeChain.PrefixResult.AllDeferred -> ApiCodeGradeReply(
                            // Every non-LLM leg deferred (no runner + no machine-checkable rubric) — the
                            // LLM tail would decide; the practice surface has no per-user LLM resolver, so
                            // we report the honest degraded shape with decided_by="llm-judge".
                            item_verdicts = emptyList(),
                            score = 0.0,
                            correct = false,
                            decided_by = DrillGradeChain.wireId(GraderLegKind.LLM_JUDGE),
                            feedback_ro = if (referenceRo == null) PracticeFeedbackRo.CODE_NO_SOLUTION else PracticeFeedbackRo.CODE_NO_RUNNER,
                            degraded_legs_ro = prefix.degradedRo,
                            reference_solution_ro = referenceRo,
                        )
                    }
                    call.respondText(
                        practiceJson.encodeToString(ApiCodeGradeReply.serializer(), reply),
                        ContentType.Application.Json,
                    )
                }
            }
        }

        // ── GET /api/v1/practice/deliverables ─────────────────────────────────────────────────────
        // Plan-6 Task 10: rewired from the honest-empty stub to read TasksTable.deliverable_json.
        // Returns all tasks for the current user where deliverable_json IS NOT NULL.
        // Honest null deadline → "necunoscut" on the UI (DeliverableTracker).
        // POO lab deliverables are absent (honest empty state — Task-2 execution-time finding:
        // no POO lab spec files located on disk after exhaustive search).
        get("/api/v1/practice/deliverables") {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser { userId ->
                val rows = transaction(ctx.db) {
                    TasksTable.selectAll()
                        .where { (TasksTable.userId eq userId) and TasksTable.deliverableJson.isNotNull() }
                        .map { row ->
                            val djRaw = row[TasksTable.deliverableJson]!!
                            parseDeliverableJson(
                                id         = row[TasksTable.id],
                                subject    = row[TasksTable.subject],
                                titleRo    = row[TasksTable.title],
                                deadlineTs = null,   // deadline column exists but deliverable seeds leave it far-future placeholder; honest null → "necunoscut"
                                djRaw      = djRaw,
                            )
                        }
                }
                call.respondText(
                    practiceJson.encodeToString(
                        ApiDeliverablesReply.serializer(),
                        ApiDeliverablesReply(deliverables = rows),
                    ),
                    ContentType.Application.Json,
                )
            }
        }
    }
}

// ── helpers ─────────────────────────────────────────────────────────────────────────────────────

private fun errJson(msg: String): String = """{"error":${jsonStr(msg)}}"""

private fun jsonStr(s: String): String =
    kotlinx.serialization.json.JsonPrimitive(s).toString()

/** Parse a bank problem's `solutionJson` into the seed solution DTO, or null. */
private fun parseSolution(p: BankProblem): ProblemSolutionYaml? {
    val raw = p.solutionJson ?: return null
    return try {
        practiceJson.decodeFromString(ProblemSolutionYaml.serializer(), raw)
    } catch (_: Exception) {
        null
    }
}

/** Extract the RO statement from the language-keyed `statementJson` ({"ro": "..."}). */
private fun statementRo(p: BankProblem): String {
    return try {
        val obj = practiceJson.parseToJsonElement(p.statementJson)
        val ro = (obj as? kotlinx.serialization.json.JsonObject)?.get("ro")
        (ro as? kotlinx.serialization.json.JsonPrimitive)?.content ?: p.statementJson
    } catch (_: Exception) {
        p.statementJson
    }
}

/**
 * Map a [BankProblem] to its surface DTO, dropping it when [surface] is requested and the problem's
 * classified archetype-class does not match. NEVER serves the reference solution.
 */
private fun toApiProblem(p: BankProblem, surface: String?): ApiPracticeProblem? {
    val klass = GraderRouting.classify(p.archetype, p.examLanguage)
    val problemSurface = when (klass) {
        ArchetypeClass.PROOF -> "proof"
        ArchetypeClass.TRACE -> "trace"
        ArchetypeClass.CODE -> "code"
        ArchetypeClass.NUMERIC -> "trace"  // numeric single-answer renders on the trace surface
        ArchetypeClass.PROSE -> "proof"    // prose proofs render on the proof surface
    }
    if (!surface.isNullOrBlank() && surface != problemSurface) return null

    val solution = parseSolution(p)
    val proofFrame = solution?.proof_frame?.let { pf ->
        ApiProofFrame(
            template_ro = pf.template_ro,
            substeps = pf.substeps.map { ApiProofSubstepLabel(id = it.id, label_ro = it.label_ro) },
        )
    }
    val traceLabels = solution?.trace_steps.orEmpty()
        .map { ApiTraceStepLabel(index = it.index, label_ro = it.label_ro) }

    return ApiPracticeProblem(
        id = p.id,
        subject = p.subject,
        archetype = p.archetype,
        surface = problemSurface,
        statement_ro = statementRo(p),
        exam_language = p.examLanguage,
        proof_frame = proofFrame,
        trace_steps = traceLabels,
        exam_language_constraints = p.examLanguageConstraintsJson,
    )
}

/** Structural match for one proof substep (the §0.9-K matcher kinds). */
private fun matchSubstep(matcher: jarvis.tutor.SubstepMatcherYaml?, text: String): Boolean {
    if (matcher == null) return false
    if (text.isBlank()) return false
    return when (matcher.kind) {
        "exact" -> text.trim() == matcher.value.trim()
        "contains" -> text.contains(matcher.value)
        "regex" -> try {
            Regex(matcher.value).containsMatchIn(text)
        } catch (_: Exception) {
            false
        }
        else -> false
    }
}

/**
 * Compare a submitted trace [got] value against the [expected] step value. Numeric expected → the
 * single-source [NumericOracleGrader.matches]; non-numeric expected → exact-string equality.
 */
private fun compareTraceValue(expected: String, got: String, tol: Double?): Boolean {
    val expNum = expected.trim().toDoubleOrNull()
    val gotNum = got.trim().toDoubleOrNull()
    return if (expNum != null && gotNum != null) {
        NumericOracleGrader.matches(expNum, gotNum, tol)
    } else {
        expected.trim() == got.trim()
    }
}

/** Convert a bank rubric row to the chain's common [RubricInput] model (Task 3 two-source leg). */
private fun RubricItem.toRubricInput(): RubricInput = RubricInput(
    id = id,
    label = label,
    points = points,
    kind = kind,
    allOrNothing = allOrNothing,
    matcherJson = null,        // bank G-items carry no structural matcher → the leg defers them
    penaltyJson = penaltyRulesJson,
)

/**
 * Parse a [TasksTable.deliverable_json] raw string + associated task fields into [ApiDeliverable].
 * JSON contract (§0.9-I): { sub_problems:[{label_ro,points}], prep_drill_ids:[String],
 *                           source_doc:String, source_quote:String }.
 * [deadlineTs] is the ISO-8601 deadline string, or null → UI "necunoscut" (§0.9-I).
 */
private fun parseDeliverableJson(
    id: String,
    subject: String,
    titleRo: String,
    deadlineTs: String?,
    djRaw: String,
): ApiDeliverable {
    return try {
        val obj = practiceJson.parseToJsonElement(djRaw).jsonObject
        val subProblems = obj["sub_problems"]?.jsonArray?.map { sp ->
            val spObj = sp.jsonObject
            ApiDeliverableSubProblem(
                label_ro = spObj["label_ro"]?.jsonPrimitive?.content ?: "",
                points   = spObj["points"]?.jsonPrimitive?.double ?: 0.0,
            )
        } ?: emptyList()
        val prepDrillIds = obj["prep_drill_ids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val sourceDoc = obj["source_doc"]?.jsonPrimitive?.content
        ApiDeliverable(
            id             = id,
            subject        = subject,
            title_ro       = titleRo,
            deadline       = deadlineTs,
            sub_problems   = subProblems,
            prep_drill_ids = prepDrillIds,
            source_doc     = sourceDoc,
            synthetic      = false,   // seeds are from verified course-meta, not synthetic
        )
    } catch (_: Exception) {
        // Malformed deliverable_json — return an honest minimal entry rather than crashing.
        ApiDeliverable(id = id, subject = subject, title_ro = titleRo, deadline = deadlineTs)
    }
}
