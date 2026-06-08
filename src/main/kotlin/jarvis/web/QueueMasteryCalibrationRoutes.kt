package jarvis.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
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
