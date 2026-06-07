package jarvis.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import jarvis.content.ContentRepo
import jarvis.content.KnowledgeConcept
import jarvis.content.SubjectEntry
import jarvis.tutor.AttemptsTable
import jarvis.tutor.KcMastery
import jarvis.tutor.KcMasteryTable
import jarvis.tutor.Phase
import jarvis.tutor.PhaseModel
import jarvis.tutor.SessionSummariesTable
import jarvis.tutor.ExamDatesTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.csrfProtect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Path
import java.time.Instant

/**
 * Phase-3 Area C, GROUP 4 — session/close · placement · exam-dates. Mounted from [installTutorRoutes].
 *
 * Frozen wire shapes (canonical = master-plan §2.2 route table + CHANGE 9):
 *  - POST /api/v1/session/close
 *      req  { reviewed_card_ids:[String], session_start_at:ISO }
 *      resp { session_id, narrative, cards_reviewed, kcs_moved_to_mastered:[String], mastery_deltas:[…] }
 *      The SERVER recomputes the deltas authoritatively from the session window (L1: the client sends
 *      NONE). Writes a `session_summaries` row. Per-user scoped; an empty window degrades to empty deltas.
 *  - GET  /api/v1/placement/questions?subject? → { questions:[{question_id, cluster, kc_id, stem}] }
 *      one question per distinct cluster in the (optionally subject-filtered) corpus.
 *  - POST /api/v1/placement/submit { answers:[{question_id, response}] }
 *      → { placed:[{kc_id, entry_phase}], started_subject }. WRITES kc_mastery.entry_phase per placed KC,
 *      per-user, NEVER regressing an already-higher entry_phase (PhaseModel monotonicity).
 *  - GET/PUT /api/v1/me/exam-dates (CHANGE 9, H14) — per-user CRUD over `exam_dates`.
 *
 * Reuses: requireUser auth, csrfProtect for writes, ContentRepo for the corpus, PhaseModel for the
 * placement entry-phase derivation, the §2.2 wire DTOs verbatim. NO schema writes (tables registered in
 * Phase 1). All writes are per-user scoped + transactional.
 */

/** Resolve the content/ directory (matches CuratorRoutes / TrustRoutes / Group-3 resolution). */
private fun groupFourContentDir(): Path =
    Path.of(
        System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )

private val groupFourJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ── ROUTE 1 — session/close wire DTOs (master §2.2, L1) ────────────────────────────────────────────

@Serializable
data class ApiSessionCloseRequest(
    val reviewed_card_ids: List<String> = emptyList(),
    val session_start_at: String? = null,   // ISO; null ⇒ degrade to an empty window
)

@Serializable
data class ApiSessionCloseReply(
    val session_id: String,
    val narrative: String?,
    val cards_reviewed: Int,
    val kcs_moved_to_mastered: List<String>,
    val mastery_deltas: List<ApiMasteryDelta>,
)

/** One per-KC server-computed delta over the session window. */
@Serializable
data class ApiMasteryDelta(
    val kc_id: String,
    val attempts: Int,           // in-window attempts on this KC for this user
    val correct: Int,            // in-window correct attempts
    val ewma_after: Double,      // the KC's CURRENT ewma (post-window authoritative value)
    val phase: Phase,            // the KC's current resolved phase
)

// ── ROUTE 2 — placement wire DTOs (master §2.2 route table line 121) ───────────────────────────────

@Serializable
data class ApiPlacementQuestionsReply(
    val questions: List<ApiPlacementQuestion>,
)

@Serializable
data class ApiPlacementQuestion(
    val question_id: String,
    val cluster: String,
    val kc_id: String,
    val stem: String,
)

@Serializable
data class ApiPlacementSubmitRequest(
    val answers: List<ApiPlacementAnswer> = emptyList(),
)

@Serializable
data class ApiPlacementAnswer(
    val question_id: String,
    val response: String = "",
)

@Serializable
data class ApiPlacementSubmitReply(
    val placed: List<ApiPlacedKc>,
    val started_subject: String?,
)

@Serializable
data class ApiPlacedKc(
    val kc_id: String,
    val entry_phase: Phase,
)

// ── ROUTE 3 — exam-dates wire DTOs (master CHANGE 9, H14) ──────────────────────────────────────────

@Serializable
data class ApiExamDatePut(
    val subject: String,
    val start_at: String,   // ISO
)

@Serializable
data class ApiExamDatesReply(
    val exam_dates: List<ApiExamDate>,
)

@Serializable
data class ApiExamDate(
    val subject: String,
    val start_at: String,   // ISO
)

fun Route.installSessionPlacementExamRoutes() {

    // ── POST /api/v1/session/close ──────────────────────────────────────────────────────────────────
    // Closes a study session. The SERVER recomputes the per-KC mastery deltas authoritatively from the
    // session window [session_start_at, now] over THIS user's `attempts` (L1 — the client sends NONE),
    // and persists a `session_summaries` row. Empty window ⇒ empty deltas, no throw.
    post("/api/v1/session/close") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val req = try {
                    groupFourJson.decodeFromString(ApiSessionCloseRequest.serializer(), call.receiveText())
                } catch (_: Exception) {
                    ApiSessionCloseRequest()   // tolerate a missing/garbled body
                }
                val now = Instant.now()
                val startedAt = req.session_start_at
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: now   // unparseable/absent ⇒ a zero-width window ⇒ empty deltas

                // Aggregate THIS user's in-window attempts per KC (per-user scoped via user_id predicate).
                data class Agg(var attempts: Int = 0, var correct: Int = 0)
                val perKc = LinkedHashMap<String, Agg>()
                transaction(db) {
                    AttemptsTable.selectAll()
                        .where {
                            (AttemptsTable.userId eq userId) and
                                (AttemptsTable.gradedAt greaterEq startedAt) and
                                (AttemptsTable.gradedAt lessEq now)
                        }
                        .orderBy(AttemptsTable.gradedAt to SortOrder.ASC)
                        .forEach { row ->
                            val a = perKc.getOrPut(row[AttemptsTable.kcId]) { Agg() }
                            a.attempts++
                            if (row[AttemptsTable.correct]) a.correct++
                        }
                }

                // For each touched KC, read its CURRENT authoritative mastery (post-window) and resolve
                // the phase. kcs_moved_to_mastered = the touched KCs whose current phase is `mastered`.
                val deltas = mutableListOf<ApiMasteryDelta>()
                val moved = mutableListOf<String>()
                transaction(db) {
                    for ((kcId, agg) in perKc) {
                        val mastery = readMasteryRow(userId, kcId)
                        val phase = mastery?.phase ?: mastery?.entryPhase ?: Phase.intro
                        deltas += ApiMasteryDelta(
                            kc_id = kcId,
                            attempts = agg.attempts,
                            correct = agg.correct,
                            ewma_after = mastery?.ewmaScore ?: 0.0,
                            phase = phase,
                        )
                        if (phase == Phase.mastered) moved += kcId
                    }
                }

                val sessionId = TutorTypes.ulid()
                val narrative = sessionNarrative(req.reviewed_card_ids.size, deltas.size, moved.size)
                // Persist the summary row (per-user; mastery_delta_json holds the computed deltas).
                transaction(db) {
                    SessionSummariesTable.insert {
                        it[id] = sessionId
                        it[SessionSummariesTable.userId] = userId
                        it[SessionSummariesTable.startedAt] = startedAt
                        it[closedAt] = now
                        it[cardsReviewed] = req.reviewed_card_ids.size
                        it[masteryDeltaJson] = groupFourJson.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(ApiMasteryDelta.serializer()), deltas,
                        )
                        it[SessionSummariesTable.narrative] = narrative
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiSessionCloseReply(
                        session_id = sessionId,
                        narrative = narrative,
                        cards_reviewed = req.reviewed_card_ids.size,
                        kcs_moved_to_mastered = moved,
                        mastery_deltas = deltas,
                    ),
                )
            }
        }
    }

    // ── GET /api/v1/placement/questions ─────────────────────────────────────────────────────────────
    // One question per distinct cluster in the (optionally subject-filtered) corpus. The corpus is the
    // SOLE cluster source: questions = the first KC of each cluster, its stem = stem_template ?: name_en.
    get("/api/v1/placement/questions") {
        requireUser { _ ->
            val repo = ContentRepo(groupFourContentDir())
            val filterSubject = call.request.queryParameters["subject"]?.takeIf { it.isNotBlank() }
            val subjects: List<SubjectEntry> =
                runCatching { repo.loadManifest().subjects }.getOrDefault(emptyList())
                    .filter { filterSubject == null || it.id == filterSubject }

            // Collect KCs across the in-scope subjects, then pick ONE representative KC per cluster
            // (first by KC id for determinism). Cluster ordering follows first-seen.
            val byCluster = LinkedHashMap<String, KnowledgeConcept>()
            for (sub in subjects) {
                val loaded = runCatching { repo.loadSubject(sub.id) }.getOrNull() ?: continue
                for (kc in loaded.kcs.sortedBy { it.id }) {
                    byCluster.putIfAbsent(kc.cluster, kc)
                }
            }
            val questions = byCluster.values.map { kc ->
                ApiPlacementQuestion(
                    question_id = "plq-${kc.id}",
                    cluster = kc.cluster,
                    kc_id = kc.id,
                    stem = kc.stem_template ?: kc.name_en,
                )
            }
            call.respond(HttpStatusCode.OK, ApiPlacementQuestionsReply(questions = questions))
        }
    }

    // ── POST /api/v1/placement/submit ───────────────────────────────────────────────────────────────
    // Grades the placement answers deterministically (non-blank response ⇒ correct=1.0, else 0.0 — the
    // serve path NEVER calls the LLM, council/no-paid-apis), derives an entry_phase per placed KC via
    // PhaseModel.transition, and WRITES kc_mastery.entry_phase per-user. The write NEVER regresses an
    // already-higher entry_phase (PhaseModel monotonicity — placement only ever raises the floor).
    // Unknown question_ids are dropped (placed stays empty). started_subject = the subject of the placed
    // KCs (the first; placement is single-subject in practice).
    post("/api/v1/placement/submit") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val req = try {
                    groupFourJson.decodeFromString(ApiPlacementSubmitRequest.serializer(), call.receiveText())
                } catch (_: Exception) {
                    ApiPlacementSubmitRequest()
                }
                val repo = ContentRepo(groupFourContentDir())
                // Resolve the (question_id → kc) map across all subjects: question_id == "plq-<kcId>".
                val allKcs: Map<String, KnowledgeConcept> = run {
                    val m = LinkedHashMap<String, KnowledgeConcept>()
                    val subs = runCatching { repo.loadManifest().subjects }.getOrDefault(emptyList())
                    for (sub in subs) {
                        val loaded = runCatching { repo.loadSubject(sub.id) }.getOrNull() ?: continue
                        for (kc in loaded.kcs) m["plq-${kc.id}"] = kc
                    }
                    m
                }

                val placed = mutableListOf<ApiPlacedKc>()
                var startedSubject: String? = null
                transaction(db) {
                    for (ans in req.answers) {
                        val kc = allKcs[ans.question_id] ?: continue   // unknown ⇒ drop
                        // Deterministic placement score: a substantive response counts as correct.
                        val score = if (ans.response.isNotBlank()) 1.0 else 0.0
                        // Derive a candidate entry_phase from a single placement observation.
                        // mastered needs MIN_OBSERVATIONS(3) so a single correct answer can reach at most
                        // `practice`/`retrieval` per PhaseModel thresholds — placement raises the floor,
                        // never claims mastery off one item.
                        val candidate = PhaseModel.transition(
                            ewma = score, observations = 1, mastered = false, current = null,
                        )
                        // Read the existing entry_phase (per-user) and NEVER regress (monotonicity).
                        val existing = readMasteryRow(userId, kc.id)
                        val existingEntry = existing?.entryPhase
                        val newEntry = maxPhase(existingEntry, candidate)

                        if (existing == null) {
                            KcMasteryTable.insert {
                                it[KcMasteryTable.userId] = userId
                                it[kcId] = kc.id
                                it[ewmaScore] = 0.0          // placement seeds entry_phase only, not mastery
                                it[observations] = 0
                                it[lastGradedAt] = Instant.now()
                                it[entryPhase] = newEntry.name
                            }
                        } else if (newEntry != existingEntry) {
                            KcMasteryTable.update({
                                (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kc.id)
                            }) {
                                it[entryPhase] = newEntry.name
                            }
                        }
                        placed += ApiPlacedKc(kc_id = kc.id, entry_phase = newEntry)
                        if (startedSubject == null) startedSubject = kc.subject
                    }
                }
                call.respond(
                    HttpStatusCode.OK,
                    ApiPlacementSubmitReply(placed = placed, started_subject = startedSubject),
                )
            }
        }
    }

    // ── GET /api/v1/me/exam-dates ───────────────────────────────────────────────────────────────────
    // Per-user read of all the user's exam dates. Whitelisted from the static-auth gate by the /me/ prefix.
    get("/api/v1/me/exam-dates") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val db = ctx.db
            val dates = transaction(db) {
                ExamDatesTable.selectAll()
                    .where { ExamDatesTable.userId eq userId }
                    .orderBy(ExamDatesTable.startAt to SortOrder.ASC)
                    .map {
                        ApiExamDate(
                            subject = it[ExamDatesTable.subject],
                            start_at = it[ExamDatesTable.startAt].toString(),
                        )
                    }
            }
            call.respond(HttpStatusCode.OK, ApiExamDatesReply(exam_dates = dates))
        }
    }

    // ── PUT /api/v1/me/exam-dates ───────────────────────────────────────────────────────────────────
    // Set (upsert) the exam date for one subject, for THIS user. One row per (user, subject):
    // re-PUT updates in place, never duplicates. Per-user scoped + transactional.
    put("/api/v1/me/exam-dates") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val db = ctx.db
                val req = try {
                    groupFourJson.decodeFromString(ApiExamDatePut.serializer(), call.receiveText())
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, """{"error":"subject + start_at required"}""")
                    return@csrfProtect
                }
                val subject = req.subject.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"subject required"}"""); return@csrfProtect }
                val startAt = runCatching { Instant.parse(req.start_at) }.getOrNull()
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"start_at must be ISO-8601"}"""); return@csrfProtect }
                val now = Instant.now()
                transaction(db) {
                    val existing = ExamDatesTable.selectAll()
                        .where { (ExamDatesTable.userId eq userId) and (ExamDatesTable.subject eq subject) }
                        .map { it[ExamDatesTable.id] }
                        .firstOrNull()
                    if (existing == null) {
                        ExamDatesTable.insert {
                            it[id] = TutorTypes.ulid()
                            it[ExamDatesTable.userId] = userId
                            it[ExamDatesTable.subject] = subject
                            it[ExamDatesTable.startAt] = startAt
                            it[createdAt] = now
                        }
                    } else {
                        ExamDatesTable.update({ ExamDatesTable.id eq existing }) {
                            it[ExamDatesTable.startAt] = startAt
                        }
                    }
                }
                call.respond(HttpStatusCode.OK, ApiExamDate(subject = subject, start_at = startAt.toString()))
            }
        }
    }
}

// ── shared helpers (READ; the only WRITES are in the route bodies, all per-user + transactional) ────

/** Read one (userId, kcId) mastery row. MUST be called from within a transaction. */
private fun readMasteryRow(userId: String, kcId: String): KcMastery? =
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

/** Phase ordering (intro<practice<retrieval<mastered). Returns the higher of [a] (nullable) and [b]. */
private fun maxPhase(a: Phase?, b: Phase): Phase {
    if (a == null) return b
    return if (a.ordinal >= b.ordinal) a else b
}

/** A short server-authored narrative for the session-wrap pane. */
private fun sessionNarrative(cardsReviewed: Int, kcsTouched: Int, mastered: Int): String =
    when {
        cardsReviewed == 0 && kcsTouched == 0 -> "Sesiune închisă fără activitate înregistrată."
        mastered > 0 -> "Ai revizuit $cardsReviewed carduri și ai consolidat $kcsTouched concepte; $mastered au atins nivelul stăpânit."
        else -> "Ai revizuit $cardsReviewed carduri și ai lucrat la $kcsTouched concepte."
    }
