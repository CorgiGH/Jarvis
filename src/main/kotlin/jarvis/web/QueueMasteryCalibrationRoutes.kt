package jarvis.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import jarvis.tutor.csrfProtect
import jarvis.content.ContentRepo
import jarvis.content.SubjectEntry
import jarvis.tutor.AttemptsTable
import jarvis.tutor.CardStatus
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.KcCandidate
import jarvis.tutor.KcMastery
import jarvis.tutor.KcMasteryTable
import jarvis.tutor.LockedNextKcSelector
import jarvis.tutor.Phase
import jarvis.tutor.PrereqGraph
import jarvis.tutor.QueueItem
import jarvis.tutor.TutorContextKey
import jarvis.tutor.VerificationStatus
import jarvis.tutor.verify.ReportWrongQuery
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Phase-3 Area C, GROUP 3 — the READ routes (queue/today · mastery · calibration). Mounted from
 * [installTutorRoutes]. These three are READ-ONLY over existing data: they aggregate `kc_mastery`,
 * `attempts`, `fsrs_cards`, the content corpus, and the B8 trust store; they NEVER write schema.
 *
 *  - GET /api/v1/queue/today  — the day queue (FSRS-due review cards + prereq-gated new-KC picks),
 *                               each item the FROZEN G1 [QueueItem] (§C). Non-faithful / OPEN-dispute
 *                               KCs are OMITTED (route-table line 109 + QueueItem §C + D-RF2).
 *  - GET /api/v1/mastery      — per-subject BAND mastery: scalar `ewma_score` + `observations` per KC
 *                               (interface-signatures-lock §M — NO history series). Bilingual subject name.
 *  - GET /api/v1/calibration  — predicted `student_confidence` vs actual correctness, aggregated from
 *                               `attempts` into reliability buckets (master §2.2 CHANGE 5 / Surface 6).
 *
 * Frozen wire shapes (canonical-on-conflict = interface-signatures-lock §C/§M + master-plan §2.2):
 *  - queue/today → { items:[QueueItem], total_due:Int, day:String }
 *  - mastery     → { subjects:[{subject_id, subject_name_ro, subject_name_en, kcs:[…band…]}] }
 *  - calibration → { buckets:[{student_confidence, attempts, correct, accuracy}], total_attempts }
 */

/** Resolve the content/ directory (matches CuratorRoutes / TrustRoutes resolution). */
private fun groupThreeContentDir(): Path =
    Path.of(
        System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )

// ── ROUTE 1 wire envelope ────────────────────────────────────────────────────────────────────────

/** GET /api/v1/queue/today envelope — interface-signatures-lock §C line 77. */
@Serializable
data class ApiQueueTodayReply(
    val items: List<QueueItem>,
    val total_due: Int,
    val day: String,   // ISO date
)

// ── ROUTE 2 wire envelope (BAND shape, interface-signatures-lock §M) ───────────────────────────────

@Serializable
data class ApiMasteryReply(
    val subjects: List<ApiMasterySubject>,
)

@Serializable
data class ApiMasterySubject(
    val subject_id: String,
    val subject_name_ro: String,
    val subject_name_en: String,
    val kcs: List<ApiMasteryKc>,
)

/** One KC's BAND datum — scalar `ewma_score` + `observations`, NO history series (§M / M-MASTERY-SHAPE). */
@Serializable
data class ApiMasteryKc(
    val kc_id: String,
    val kc_name_ro: String,
    val kc_name_en: String,
    val phase: Phase,
    val ewma_score: Double,
    val observations: Int,
    val last_graded_at: String?,        // ISO; null ONLY at 0 observations
    val verification_status: VerificationStatus,
)

// ── ROUTE 3 wire envelope (master §2.2 CHANGE 5) ───────────────────────────────────────────────────

@Serializable
data class ApiCalibrationReply(
    val buckets: List<ApiCalibrationBucket>,
    val total_attempts: Int,
)

@Serializable
data class ApiCalibrationBucket(
    val student_confidence: String,     // DEFINITELY|MAYBE|GUESS|IDK
    val attempts: Int,
    val correct: Int,
    val accuracy: Double,               // correct / attempts
)

/**
 * Grounded-teaching serve reply (council 1780928193). Served by GET /api/v1/teaching/{kcId} ONLY
 * when the KC resolves `faithful` + has no OPEN dispute. Both prose fields are AUTHORED and have
 * passed the faithful-check pipeline, so `provenance` is stamped {authored, faithful-checked=true}
 * — the type-honest counterpart to the generated DrillContentDto.provenance marker. A field is null
 * when the author did not write it. FAIL-LOUD: non-faithful / disputed KCs are 404 (never served).
 */
@Serializable
data class ApiTeachingReply(
    val kcId: String,
    val name_ro: String,
    val explanation_ro: String?,
    val worked_example_ro: String?,
    val provenance: jarvis.tutor.DrillProvenanceDto,
)

/**
 * Faithful-gated first-encounter lesson reply (Task T7-1, §NEW-L). Served by
 * GET /api/v1/lesson/{kcId} ONLY when the KC resolves `faithful` + has no OPEN dispute — the
 * IDENTICAL gate as /teaching/{kcId}. Maps KnowledgeConcept authored fields to the lesson surface.
 * FAIL-LOUD: non-faithful / disputed / unknown KCs are 404 (OMIT, never a degraded payload).
 *
 * prediction_options is always an empty list for now (no option source on KC yet — honest degraded,
 * DO NOT fabricate). provenance is stamped {authored, hasBeenFaithfulChecked=true} because the
 * route only returns 200 when the faithful gate passes.
 */
@Serializable
data class ApiLessonReply(
    val kcId: String,
    val kc_name_ro: String,
    val kc_name_en: String,
    val concrete_question_ro: String?,    // stem_template or null
    val echo_source_ro: String?,          // kc.source[0].quote or null
    val prediction_options: List<String>, // 2-4 RO options; empty list = gate disabled (no source yet)
    val term_ro: String,                  // = kc_name_ro
    val definition_ro: String?,
    val explanation_ro: String?,
    val worked_example_ro: String?,
    val provenance: jarvis.tutor.DrillProvenanceDto, // {authored, hasBeenFaithfulChecked=true}
    // ===== Plan 3 §0.9B (spec §4) — ADDITIVE 12th field, appended LAST; lock §NEW-L + pin amended SAME commit.
    // null ⇒ legacy payload (never served in practice post-Task 3: incomplete beats are 404). =====
    val beats: ApiLessonBeats? = null,
)

/**
 * Plan-3 §0.9B — the ADDITIVE beats payload on the lesson reply (spec §4). Present only when the KC's
 * `beats["ro"]` is structurally complete for its concept_type (KcBeats.isCompleteFor). `plan` is the
 * served BeatSelector plan as lowercase BeatType names, in order; a beat sub-object is present iff the
 * plan contains that beat. `correct`/`callback`/`feedback` flags ride to the client for the gate/echo
 * UX, but the SERVER is grading truth via POST /api/v1/lesson/{kcId}/beat — the reply is never trusted
 * for writes (Task 4).
 */
@Serializable
data class ApiLessonBeats(
    val plan: List<String>,                  // BeatType lowercase names, in served order
    val concept_type: String,                // the KC's wire literal
    val predict: ApiBeatPredict? = null,     // present iff plan contains "predict"
    val attempt: ApiBeatAttempt? = null,
    val reveal: ApiBeatReveal? = null,
    val name: ApiBeatName? = null,           // present iff plan contains "name"
    val check: ApiBeatCheck? = null,
)

@Serializable data class ApiPredictOption(val text: String, val callback: String, val correct: Boolean)
@Serializable data class ApiBeatPredict(val prompt: String, val options: List<ApiPredictOption>)
@Serializable data class ApiAttemptChoice(val text: String, val correct: Boolean, val feedback: String)
@Serializable data class ApiSkeletonRow(val label: String, val formula: String?, val is_decision_row: Boolean)
@Serializable data class ApiTraceStep(val row_index: Int, val value: String, val callout: String?)
@Serializable data class ApiBeatAttempt(
    val statement: String,
    val choices: List<ApiAttemptChoice> = emptyList(),
    val skeleton_rows: List<ApiSkeletonRow> = emptyList(),
    val trace_steps: List<ApiTraceStep> = emptyList(),
    val input_schema: String? = null,
    // Plan-6 ADDITIVE (lock §NEW-L additive-amendment): numeric-variant ATTEMPT answer + tolerance.
    val numeric_answer: String? = null,
    val numeric_tolerance: Double? = null,
    val feedback_correct: String,
)
@Serializable data class ApiRevealStep(val text: String, val callout: String)
@Serializable data class ApiFigureBinding(val family_id: String, val instance_id: String)
@Serializable data class ApiBeatReveal(val steps: List<ApiRevealStep>, val figure: ApiFigureBinding? = null)
@Serializable data class ApiBeatName(val definition: String, val invariant_statement: String, val why_matters: String)
@Serializable data class ApiBeatCheck(val item_stem: String, val choices: List<ApiAttemptChoice> = emptyList(), val numeric_answer: String? = null, val numeric_tolerance: Double? = null)

/**
 * Plan-3 §0.9C — the per-beat grade request. Sent for the gated beats with learner input
 * (predict / attempt / check). The server grades from the KC's OWN beats data; the client reply
 * is never trusted for writes.
 */
@Serializable
data class ApiBeatGradeRequest(
    val beat_type: String,                // "predict" | "attempt" | "check"
    val selected_index: Int? = null,      // choice beats: index into the served options/choices
    val free_input: String? = null,       // numerical attempt / numeric check
    val prediction_text: String? = null,  // predict beats: the chosen option text (stored on the attempt row)
)

@Serializable
data class ApiBeatGradeReply(
    val correct: Boolean,
    val score: Double,                                // predict/attempt: 1.0/0.0 informational; check: feeds EWMA
    val feedback_ro: String,                          // both-path feedback / option callback (RO)
    val beat_type: String,
    val lesson_complete: Boolean,                     // true on the graded CHECK
    val first_encounter: Boolean,
    val phase: jarvis.tutor.Phase? = null,            // post-write phase (CHECK only)
    val verification_status: jarvis.tutor.VerificationStatus? = null,
)

/** Local JSON for beat-grade request decode (matches the server CN config: ignore unknown, encode defaults). */
private val beatGradeJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun Route.installQueueMasteryCalibrationRoutes() {

    // ── GET /api/v1/queue/today ───────────────────────────────────────────────────────────────────
    // FSRS-due review cards (status=ACTIVE, due<=now) + prereq-gated new-KC picks. Non-faithful and
    // OPEN-dispute KCs are OMITTED (route-table line 109 + QueueItem §C + D-RF2). Empty corpus ⇒ empty.
    get("/api/v1/queue/today") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val db = ctx.db
            val now = Instant.now()

            val repo = ContentRepo(groupThreeContentDir())
            val subjects: List<SubjectEntry> = runCatching { repo.loadManifest().subjects }.getOrDefault(emptyList())

            // Assemble candidate KCs across every subject, resolving each KC's runtime trust status from
            // the B8 store (VerifyAdmin.resolveStatus folds the D8 staleness gate + the D3 OPEN-dispute
            // refusal). Only `faithful` KCs survive — non-faithful/disputed are OMITTED, never surfaced.
            val candidates = mutableListOf<KcCandidate>()
            val allEdges = mutableListOf<jarvis.content.PrereqEdge>()
            // P0-2 watch (c): the set of KCs that survived the SAME faithful + non-disputed filter the
            // queue items use. total_due is counted ONLY over due cards whose KC is in this set, so the
            // count can never report due work the queue omits (omit-non-faithful contract, line 109/§C).
            val faithfulKcIds = mutableSetOf<String>()
            for (sub in subjects) {
                val loaded = runCatching { repo.loadSubject(sub.id) }.getOrNull() ?: continue
                allEdges += loaded.edges
                for (kc in loaded.kcs) {
                    val resolved = VerifyAdmin.resolveStatus(db, kc.id, kc)
                    if (resolved != VerificationStatus.faithful) continue   // OMIT non-faithful (line 109)
                    // D-RF2: the always-on serve refusal. resolveStatus already returns unverified on an
                    // OPEN dispute (faithful→unverified), so the faithful filter above already excludes a
                    // disputed faithful KC. The explicit shared-helper check keeps the contract loud.
                    if (ReportWrongQuery.hasOpenReportWrong(db, kc.id)) continue
                    faithfulKcIds += kc.id
                    val mastery = readMastery(db, userId, kc.id)
                    candidates += KcCandidate(
                        kc = kc,
                        mastery = mastery,
                        phase = resolvePhase(mastery),
                        verificationStatus = resolved,
                        fsrsCardId = activeCardIdFor(db, userId, kc.id),
                    )
                }
            }

            // New-KC pick: the LOCKED deterministic selector (prereq-gated → lowest-mastery → interleave).
            val selector = LockedNextKcSelector(PrereqGraph.from(allEdges))
            val nextItem: QueueItem? = selector.select(
                userId = userId, subject = null, candidates = candidates, recentShapes = emptyList(),
            )

            // FSRS-due review cards (status=ACTIVE, due<=now), FILTERED to faithful KCs (§2.2 + line 109).
            // P0-2 watch (c): a due card whose KC is non-faithful / disputed (NOT in faithfulKcIds) is
            // EXCLUDED — total_due must match the omit-non-faithful queue contract, never pad the count
            // with due work the queue refuses to surface. A NULL-kcId card (GAP_PROMOTION / MANUAL) has
            // no content KC to prove faithful ⇒ fail-closed (excluded). Empty faithful set ⇒ count 0.
            val dueCardCount = if (faithfulKcIds.isEmpty()) {
                0
            } else {
                transaction(db) {
                    FsrsCardsTable.selectAll()
                        .where {
                            (FsrsCardsTable.userId eq userId) and
                                (FsrsCardsTable.dueAt lessEq now) and
                                (FsrsCardsTable.status eq CardStatus.ACTIVE.name) and
                                (FsrsCardsTable.kcId inList faithfulKcIds)
                        }
                        .count()
                }.toInt()
            }

            val items = listOfNotNull(nextItem)
            call.respond(
                HttpStatusCode.OK,
                ApiQueueTodayReply(
                    items = items,
                    total_due = dueCardCount,
                    day = LocalDate.now(ZoneOffset.UTC).toString(),
                ),
            )
        }
    }

    // ── GET /api/v1/mastery ───────────────────────────────────────────────────────────────────────
    // Per-subject BAND mastery (interface-signatures-lock §M): scalar ewma_score + observations per KC,
    // NO history series. Bilingual subject name straight from subjects.yaml (NOT hardcoded). A subject
    // with no mastery rows degrades cleanly (0 observations, null last_graded_at, cold ewma).
    get("/api/v1/mastery") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val db = ctx.db
            val repo = ContentRepo(groupThreeContentDir())
            val filterSubject = call.request.queryParameters["subject"]?.takeIf { it.isNotBlank() }
            val manifest = runCatching { repo.loadManifest().subjects }.getOrDefault(emptyList())

            val subjects = manifest
                .filter { filterSubject == null || it.id == filterSubject }
                .map { sub ->
                    val loaded = runCatching { repo.loadSubject(sub.id) }.getOrNull()
                    val kcs = (loaded?.kcs ?: emptyList()).map { kc ->
                        val mastery = readMastery(db, userId, kc.id)
                        ApiMasteryKc(
                            kc_id = kc.id,
                            kc_name_ro = kc.name_ro,
                            kc_name_en = kc.name_en,
                            phase = resolvePhase(mastery),
                            // Band: scalar EWMA (cold ⇒ 0.0) + observations (cold ⇒ 0).
                            ewma_score = mastery?.ewmaScore ?: 0.0,
                            observations = mastery?.observations ?: 0,
                            // null ONLY at 0 observations (M-MASTERY-SHAPE).
                            last_graded_at = mastery?.lastGradedAt?.toString(),
                            verification_status = VerifyAdmin.resolveStatus(db, kc.id, kc),
                        )
                    }
                    ApiMasterySubject(
                        subject_id = sub.id,
                        subject_name_ro = sub.name_ro,
                        subject_name_en = sub.name_en,
                        kcs = kcs,
                    )
                }
            call.respond(HttpStatusCode.OK, ApiMasteryReply(subjects = subjects))
        }
    }

    // ── GET /api/v1/calibration ───────────────────────────────────────────────────────────────────
    // Reliability-curve DATA (server returns DATA, never Plotly). Aggregate the user's `attempts` into
    // buckets keyed on student_confidence; per bucket: attempts, correct, accuracy = correct/attempts.
    // Attempts with a NULL student_confidence are excluded (no predicted-confidence ⇒ not on the curve).
    // 0 attempts ⇒ empty bucket list + total_attempts 0 (degraded, no throw).
    get("/api/v1/calibration") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val db = ctx.db
            val filterSubject = call.request.queryParameters["subject"]?.takeIf { it.isNotBlank() }

            // `attempts` carries no subject column; ?subject=X resolves to that subject's KC ids from the
            // content corpus and the aggregation keeps only attempts whose kc_id is in that set. A subject
            // that fails to load (or has no KCs) ⇒ empty set ⇒ zero attempts (degraded, not a throw).
            val subjectKcIds: Set<String>? = filterSubject?.let { sub ->
                runCatching { ContentRepo(groupThreeContentDir()).loadSubject(sub).kcs.map { it.id }.toSet() }
                    .getOrDefault(emptySet())
            }

            // Per-bucket running tallies. ordinal-stable bucket ordering (DEFINITELY→IDK).
            data class Tally(var attempts: Int = 0, var correct: Int = 0)
            val tallies = LinkedHashMap<String, Tally>()
            var total = 0
            transaction(db) {
                val rows = AttemptsTable.selectAll()
                    .where { AttemptsTable.userId eq userId }
                    .orderBy(AttemptsTable.gradedAt to SortOrder.ASC)
                for (row in rows) {
                    // Subject scope (membership over the content-resolved KC-id set).
                    if (subjectKcIds != null && row[AttemptsTable.kcId] !in subjectKcIds) continue
                    val conf = row[AttemptsTable.studentConfidence] ?: continue  // no prediction ⇒ off-curve
                    total++
                    val t = tallies.getOrPut(conf) { Tally() }
                    t.attempts++
                    if (row[AttemptsTable.correct]) t.correct++
                }
            }
            // Emit in the canonical confidence order, then any unexpected literal alphabetically.
            val order = listOf("DEFINITELY", "MAYBE", "GUESS", "IDK")
            val ordered = tallies.keys.sortedWith(
                compareBy({ order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }),
            )
            val buckets = ordered.map { conf ->
                val t = tallies.getValue(conf)
                ApiCalibrationBucket(
                    student_confidence = conf,
                    attempts = t.attempts,
                    correct = t.correct,
                    accuracy = if (t.attempts == 0) 0.0 else t.correct.toDouble() / t.attempts.toDouble(),
                )
            }
            call.respond(HttpStatusCode.OK, ApiCalibrationReply(buckets = buckets, total_attempts = total))
        }
    }

    // ── GET /api/v1/teaching/{kcId} ─────────────────────────────────────────────────────────────────
    // The authored grounded teaching (plain-words explanation + worked example) for ONE KC. Served
    // ONLY when the KC resolves `faithful` (VerifyAdmin.resolveStatus folds the D8 staleness gate)
    // AND has no OPEN report_wrong (D-RF2 always-on serve refusal). Non-faithful / disputed / unknown
    // ⇒ 404 (OMIT, never a degraded payload) — the SAME gate queue/today uses (lines 143-148).
    get("/api/v1/teaching/{kcId}") {
        requireUser { _ ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val db = ctx.db
            val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                ?: run { call.respond(HttpStatusCode.BadRequest, "kcId required"); return@requireUser }
            val repo = ContentRepo(groupThreeContentDir())
            // Mirror queue/today + the other findKc call sites (TrustRoutes, TutorRoutes): wrap in
            // runCatching so an absent/malformed subjects.yaml degrades to 404 (OMIT) rather than
            // propagating an unhandled IllegalStateException → Ktor default 500 leaking the file path.
            val kc = runCatching { VerifyAdmin.findKc(repo, kcId) }.getOrNull()
                ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"unknown kc"}"""); return@requireUser }

            // The faithful gate, mirrored from queue/today (lines 143-148):
            val resolved = VerifyAdmin.resolveStatus(db, kc.id, kc)
            if (resolved != VerificationStatus.faithful) {
                call.respond(HttpStatusCode.NotFound, """{"error":"not faithful"}"""); return@requireUser
            }
            if (ReportWrongQuery.hasOpenReportWrong(db, kc.id)) {
                call.respond(HttpStatusCode.NotFound, """{"error":"disputed"}"""); return@requireUser
            }

            call.respond(
                HttpStatusCode.OK,
                ApiTeachingReply(
                    kcId = kc.id,
                    name_ro = kc.name_ro,
                    explanation_ro = kc.explanation_ro?.takeIf { it.isNotBlank() },
                    worked_example_ro = kc.worked_example_ro?.takeIf { it.isNotBlank() },
                    // Served only behind the faithful gate above ⇒ honest authored+checked provenance.
                    provenance = jarvis.tutor.DrillProvenanceDto(type = "authored", hasBeenFaithfulChecked = true),
                ),
            )
        }
    }

    // ── GET /api/v1/lesson/{kcId} ─────────────────────────────────────────────────────────────────
    // First-encounter lesson surface for ONE KC. Applies the IDENTICAL faithful gate as /teaching:
    // resolveStatus (D8 staleness + D1 content-hash) + hasOpenReportWrong (D-RF2). Non-faithful /
    // disputed / unknown ⇒ 404 (OMIT, never a degraded payload). Maps KnowledgeConcept authored
    // fields to ApiLessonReply; prediction_options is honest-degraded empty list (no source yet).
    get("/api/v1/lesson/{kcId}") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val db = ctx.db
            val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                ?: run { call.respond(HttpStatusCode.BadRequest, "kcId required"); return@requireUser }
            val repo = ContentRepo(groupThreeContentDir())
            // Mirror /teaching: wrap in runCatching so absent/malformed subjects.yaml degrades to
            // 404 (OMIT) instead of a Ktor 500 leaking the file path.
            val kc = runCatching { VerifyAdmin.findKc(repo, kcId) }.getOrNull()
                ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"unknown kc"}"""); return@requireUser }

            // Identical faithful gate as /teaching (lines above):
            val resolved = VerifyAdmin.resolveStatus(db, kc.id, kc)
            if (resolved != VerificationStatus.faithful) {
                call.respond(HttpStatusCode.NotFound, """{"error":"not faithful"}"""); return@requireUser
            }
            if (ReportWrongQuery.hasOpenReportWrong(db, kc.id)) {
                call.respond(HttpStatusCode.NotFound, """{"error":"disputed"}"""); return@requireUser
            }

            // ── Plan-3 beats guard (spec §4.1/§4.5) — INLINE after the faithful gate, NOT a gate-signature
            //    change (§I.2 RF2 FROZEN). concept_type must parse; beats["ro"] must be structurally
            //    complete for that type (KcBeats.isCompleteFor). Either failing ⇒ 404 (OMIT, fail-loud).
            val conceptType = kc.concept_type?.let { jarvis.content.ConceptType.fromWire(it) }
                ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"concept_type invalid"}"""); return@requireUser }
            val roBeats = kc.beats["ro"]
            if (roBeats == null || !roBeats.isCompleteFor(conceptType)) {
                call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@requireUser
            }

            // The served plan: BeatSelector chooses compression from concept_type + mastery (Task 2).
            // No mastery row ⇒ first encounter ⇒ FULL/STANDARD only (INV-4.1). reLesson is never passed.
            val mastery = readMastery(db, userId, kc.id)
            val plan = jarvis.tutor.lesson.BeatSelector.planFor(
                conceptType = conceptType,
                phase = resolvePhase(mastery),
                isFirstEncounter = mastery == null,
            )
            val planBeats = plan.beats.map { it.name.lowercase() }  // ["predict","attempt",...]
            val carriesName = jarvis.tutor.lesson.BeatType.NAME in plan.beats

            // Map ONLY the beats the served plan contains (a compressed plan omits ① and/or ④).
            val apiBeats = ApiLessonBeats(
                plan = planBeats,
                concept_type = jarvis.content.ConceptType.wireOf(conceptType),
                predict = if (jarvis.tutor.lesson.BeatType.PREDICT in plan.beats) roBeats.predict?.let { p ->
                    ApiBeatPredict(
                        prompt = p.prompt,
                        options = p.options.map { ApiPredictOption(it.text, it.callback, it.correct) },
                    )
                } else null,
                attempt = if (jarvis.tutor.lesson.BeatType.ATTEMPT in plan.beats) roBeats.attempt?.let { a ->
                    ApiBeatAttempt(
                        statement = a.statement,
                        choices = a.choices.map { ApiAttemptChoice(it.text, it.correct, it.feedback) },
                        skeleton_rows = a.skeleton_rows.map { ApiSkeletonRow(it.label, it.formula, it.is_decision_row) },
                        trace_steps = a.trace_steps.map { ApiTraceStep(it.row_index, it.value, it.callout) },
                        input_schema = a.input_schema,
                        numeric_answer = a.numeric_answer,
                        numeric_tolerance = a.numeric_tolerance,
                        feedback_correct = a.feedback_correct,
                    )
                } else null,
                reveal = if (jarvis.tutor.lesson.BeatType.REVEAL in plan.beats) roBeats.reveal?.let { r ->
                    ApiBeatReveal(
                        steps = r.steps.map { ApiRevealStep(it.text, it.callout) },
                        figure = r.figure?.let { ApiFigureBinding(it.family_id, it.instance_id) },
                    )
                } else null,
                name = if (carriesName) roBeats.name?.let { n ->
                    ApiBeatName(n.definition, n.invariant_statement, n.why_matters)
                } else null,
                check = if (jarvis.tutor.lesson.BeatType.CHECK in plan.beats) roBeats.check?.let { c ->
                    ApiBeatCheck(
                        item_stem = c.item_stem,
                        choices = c.choices.map { ApiAttemptChoice(it.text, it.correct, it.feedback) },
                        numeric_answer = c.numeric_answer,
                        numeric_tolerance = c.numeric_tolerance,
                    )
                } else null,
            )

            call.respond(
                HttpStatusCode.OK,
                ApiLessonReply(
                    kcId = kc.id,
                    kc_name_ro = kc.name_ro,
                    kc_name_en = kc.name_en,
                    // stem_template → concrete question stem (null if not authored yet)
                    concrete_question_ro = kc.stem_template?.takeIf { it.isNotBlank() },
                    // First source quote → echo anchor (null if no source refs)
                    echo_source_ro = kc.source.firstOrNull()?.quote?.takeIf { it.isNotBlank() },
                    // Plan-3 (lock §NEW-L amended): BeatPredict.options IS the KC options source. Populated
                    // ONLY when the served plan carries ① PREDICT; a plan without it keeps the honest-empty
                    // list (no fabrication). beats complete is already proven by the guard above.
                    prediction_options = apiBeats.predict?.options?.map { it.text } ?: emptyList(),
                    // term = name_ro (primary Romanian label)
                    term_ro = kc.name_ro,
                    // Plan-3 (lock §NEW-L amended): BeatName.definition IS the dedicated definition field.
                    // Populated ONLY when the served plan carries ④ NAME and the definition is non-blank —
                    // STANDARD/MASTERED-REVISIT/RE-LESSON omit ④, so definition_ro stays null (the served
                    // plan carries no ④; still NOT a duplicate of explanation_ro, trust-first).
                    definition_ro = apiBeats.name?.definition?.takeIf { it.isNotBlank() },
                    explanation_ro = kc.explanation_ro?.takeIf { it.isNotBlank() },
                    worked_example_ro = kc.worked_example_ro?.takeIf { it.isNotBlank() },
                    // Served only behind the faithful gate ⇒ honest authored+checked provenance.
                    provenance = jarvis.tutor.DrillProvenanceDto(type = "authored", hasBeenFaithfulChecked = true),
                    beats = apiBeats,
                ),
            )
        }
    }

    // ── POST /api/v1/lesson/{kcId}/beat ───────────────────────────────────────────────────────────────────
    // Server-side beat grading + the §4.4 completion-writes contract (spec §4.4). Same faithful + dispute
    // + beats-complete gate as the GET. EVERY graded beat writes ONE attempts row (beat_type, prediction,
    // first_encounter). On beat_type=="check": same-txn recordIn (EWMA+phase) + upsertRubricCriterion FSRS
    // seed, mirroring TutorRoutes.kt:2222-2256, and lesson_complete=true. CSRF-gated (POST).
    post("/api/v1/lesson/{kcId}/beat") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"kcId required"}"""); return@csrfProtect }

                val req = try {
                    beatGradeJson.decodeFromString(ApiBeatGradeRequest.serializer(), call.receiveText())
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, """{"error":"bad body"}"""); return@csrfProtect
                }

                // beat_type must be one of the three gradable beats; anything else (reveal/name/garbage) ⇒ 400.
                val beatType = req.beat_type.lowercase()
                if (beatType !in setOf("predict", "attempt", "check")) {
                    call.respond(HttpStatusCode.BadRequest, """{"error":"beat_type not gradable"}"""); return@csrfProtect
                }

                val repo = ContentRepo(groupThreeContentDir())
                val kc = runCatching { VerifyAdmin.findKc(repo, kcId) }.getOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"unknown kc"}"""); return@csrfProtect }

                // Identical faithful gate as the GET.
                val resolved = VerifyAdmin.resolveStatus(db, kc.id, kc)
                if (resolved != VerificationStatus.faithful) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"not faithful"}"""); return@csrfProtect
                }
                if (ReportWrongQuery.hasOpenReportWrong(db, kc.id)) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"disputed"}"""); return@csrfProtect
                }

                // Identical beats guard as the GET (concept_type parse + structural completeness).
                val conceptType = kc.concept_type?.let { jarvis.content.ConceptType.fromWire(it) }
                    ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"concept_type invalid"}"""); return@csrfProtect }
                val roBeats = kc.beats["ro"]
                if (roBeats == null || !roBeats.isCompleteFor(conceptType)) {
                    call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@csrfProtect
                }

                // ── Grade from the KC's OWN beats data. correct flag + RO feedback.
                var correct = false
                var feedbackRo = ""
                when (beatType) {
                    "predict" -> {
                        val opts = roBeats.predict?.options ?: emptyList()
                        val sel = req.selected_index?.takeIf { it in opts.indices }
                            ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"selected_index out of range"}"""); return@csrfProtect }
                        correct = opts[sel].correct
                        feedbackRo = opts[sel].callback   // the option callback IS the both-path feedback (§3.2 ①)
                    }
                    "attempt" -> {
                        val a = roBeats.attempt
                            ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@csrfProtect }
                        if (a.choices.isNotEmpty()) {
                            val sel = req.selected_index?.takeIf { it in a.choices.indices }
                                ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"selected_index out of range"}"""); return@csrfProtect }
                            correct = a.choices[sel].correct
                            feedbackRo = if (correct) a.feedback_correct else a.choices[sel].feedback
                        } else if (a.numeric_answer != null) {
                            // Numeric attempt: parse free_input as double, compare to numeric_answer within
                            // tolerance via the single-source comparator (NumericOracleGrader.matches, §0.9-B).
                            // Mirrors the numeric CHECK path below; resolves the carried follow-up
                            // (docs/superpowers/plans/2026-06-12-plan6-practice-graders.md Task 4).
                            val answer = a.numeric_answer.toDoubleOrNull()
                            val got = req.free_input?.trim()?.toDoubleOrNull()
                            correct = answer != null && got != null &&
                                jarvis.tutor.grader.NumericOracleGrader.matches(answer, got, a.numeric_tolerance ?: 0.0)
                            feedbackRo = if (correct) a.feedback_correct else "Reanalizează pașii din schelet."
                        } else {
                            // Legacy numeric-attempt fallback (no numeric_answer authored): exact-string match
                            // against the trace's final value. trace_steps[].value is INSTANCE/teaching data
                            // (e.g. "3*t"), NOT the numeric a learner types — so this essentially always grades a
                            // real numeric answer incorrect. It exists only so the code compiles for the
                            // FORMULA_APPLICATION/PROCEDURE/PROBABILISTIC numerical variants that omit numeric_answer.
                            val expected = a.trace_steps.lastOrNull()?.value
                            val got = req.free_input?.trim()
                            correct = expected != null && got != null && got == expected
                            feedbackRo = if (correct) a.feedback_correct else "Reanalizează pașii din schelet."
                        }
                    }
                    "check" -> {
                        val c = roBeats.check
                            ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"beats not complete"}"""); return@csrfProtect }
                        if (c.choices.isNotEmpty()) {
                            val sel = req.selected_index?.takeIf { it in c.choices.indices }
                                ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"selected_index out of range"}"""); return@csrfProtect }
                            correct = c.choices[sel].correct
                            feedbackRo = c.choices[sel].feedback
                        } else {
                            // Numeric check: parse free_input as double, compare to numeric_answer within tolerance
                            // via the single-source comparator (NumericOracleGrader.matches, §0.9-B). Per-site
                            // default tol ?: 0.0 preserved (behavior pinned).
                            val answer = c.numeric_answer?.toDoubleOrNull()
                            val got = req.free_input?.trim()?.toDoubleOrNull()
                            correct = answer != null && got != null &&
                                jarvis.tutor.grader.NumericOracleGrader.matches(answer, got, c.numeric_tolerance ?: 0.0)
                            feedbackRo = if (correct) "Corect." else "Reanalizează: răspunsul numeric nu se potrivește."
                        }
                    }
                }
                val score = if (correct) 1.0 else 0.0

                // ── first_encounter: computed BEFORE any write (§0.9C). True iff no mastery row AND zero prior
                //    lesson attempt rows for (user, kc). taskId="lesson" marks an attempt as lesson-originated.
                val firstEncounter = transaction(db) {
                    val noMastery = KcMasteryTable.selectAll()
                        .where { (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kc.id) }
                        .empty()
                    val noPriorLessonAttempts = AttemptsTable.selectAll()
                        .where {
                            (AttemptsTable.userId eq userId) and
                                (AttemptsTable.kcId eq kc.id) and
                                (AttemptsTable.taskId eq "lesson")
                        }
                        .empty()
                    noMastery && noPriorLessonAttempts
                }

                val now = Instant.now()
                var postPhase: Phase? = null
                if (beatType == "check") {
                    // ── CHECK: completion writes in ONE atomic txn (mirror TutorRoutes.kt:2222-2256).
                    //    recordIn (EWMA + phase) → attempts row → upsertRubricCriterion FSRS seed.
                    val masteryRepo = jarvis.tutor.KcMasteryRepo(db)
                    val cardRepo = jarvis.tutor.FsrsCardRepo(db)
                    val initial = jarvis.Fsrs.initial(if (correct) 3 else 2)
                    // front derivation mirrors TutorRoutes:2218 (server problem statement → fallback). The lesson
                    // has no server problem; use the KC stem_template, else name_en (a stable rubric front).
                    val front = kc.stem_template?.takeIf { it.isNotBlank() } ?: kc.name_en
                    val back = roBeats.name?.definition?.takeIf { it.isNotBlank() } ?: kc.explanation_ro ?: ""
                    try {
                        transaction(db) {
                            val m = masteryRepo.recordIn(this, userId, kc.id, score, now)
                            postPhase = m.phase
                            AttemptsTable.insert {
                                it[id] = jarvis.tutor.TutorTypes.ulid()
                                it[AttemptsTable.userId] = userId
                                it[AttemptsTable.kcId] = kc.id
                                it[taskId] = "lesson"
                                it[problemId] = kc.id + ":" + beatType
                                it[phase] = m.phase?.name ?: Phase.intro.name
                                it[AttemptsTable.correct] = correct
                                it[AttemptsTable.score] = score
                                it[scaffoldLevel] = 0
                                it[AttemptsTable.recorded] = true
                                it[gradedAt] = now
                                it[AttemptsTable.beatType] = beatType
                                it[AttemptsTable.prediction] = req.prediction_text
                                it[AttemptsTable.firstEncounter] = firstEncounter
                            }
                            cardRepo.upsertRubricCriterion(
                                this, userId, kc.id, front, back,
                                jarvis.tutor.FsrsState(
                                    difficulty = initial.difficulty,
                                    stability = initial.stability,
                                    retrievability = 1.0,
                                    dueAt = now.plus(java.time.Duration.ofDays(1)),
                                    lastReviewedAt = now,
                                    lapses = 0,
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        System.err.println("[lesson-beat] atomic completion txn FAILED (rolled back) for kc=${kc.id}: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                        call.respond(HttpStatusCode.InternalServerError, """{"error":"completion write failed"}"""); return@csrfProtect
                    }
                } else {
                    // ── predict / attempt: ONE attempt row only, no mastery/FSRS write. Phase from mastery (or intro).
                    val phaseNow = resolvePhase(readMastery(db, userId, kc.id))
                    transaction(db) {
                        AttemptsTable.insert {
                            it[id] = jarvis.tutor.TutorTypes.ulid()
                            it[AttemptsTable.userId] = userId
                            it[AttemptsTable.kcId] = kc.id
                            it[taskId] = "lesson"
                            it[problemId] = kc.id + ":" + beatType
                            it[phase] = phaseNow.name
                            it[AttemptsTable.correct] = correct
                            it[AttemptsTable.score] = score
                            it[scaffoldLevel] = 0
                            it[AttemptsTable.recorded] = true
                            it[gradedAt] = now
                            it[AttemptsTable.beatType] = beatType
                            it[AttemptsTable.prediction] = req.prediction_text
                            it[AttemptsTable.firstEncounter] = firstEncounter
                        }
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiBeatGradeReply(
                        correct = correct,
                        score = score,
                        feedback_ro = feedbackRo,
                        beat_type = beatType,
                        lesson_complete = beatType == "check",
                        first_encounter = firstEncounter,
                        phase = if (beatType == "check") postPhase else null,
                        verification_status = if (beatType == "check") resolved else null,
                    ),
                )
            }
        }
    }
}

// ── shared read helpers (READ-ONLY; no schema writes) ─────────────────────────────────────────────

/** Read one (userId, kcId) mastery row, or null (cold). Mirrors KcMasteryRepo.readRow without exposing it. */
private fun readMastery(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String): KcMastery? =
    transaction(db) {
        KcMasteryTable.selectAll()
            .where { (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kcId) }
            .map {
                KcMastery(
                    it[KcMasteryTable.userId], it[KcMasteryTable.kcId],
                    it[KcMasteryTable.ewmaScore], it[KcMasteryTable.observations],
                    it[KcMasteryTable.lastGradedAt],
                    phase = it[KcMasteryTable.phase]?.let { p -> Phase.valueOf(p) },
                    entryPhase = it[KcMasteryTable.entryPhase]?.let { p -> Phase.valueOf(p) },
                )
            }
            .singleOrNull()
    }

/** Resolved phase for a candidate: kc_mastery.phase → entry_phase → intro (§D KcCandidate.phase). */
private fun resolvePhase(mastery: KcMastery?): Phase =
    mastery?.phase ?: mastery?.entryPhase ?: Phase.intro

/** The id of an ACTIVE FSRS card for (userId, kcId), or null (no seeded card yet). */
private fun activeCardIdFor(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String): String? =
    transaction(db) {
        FsrsCardsTable.selectAll()
            .where {
                (FsrsCardsTable.userId eq userId) and
                    (FsrsCardsTable.kcId eq kcId) and
                    (FsrsCardsTable.status eq CardStatus.ACTIVE.name)
            }
            .limit(1)
            .map { it[FsrsCardsTable.id] }
            .firstOrNull()
    }
