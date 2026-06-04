package jarvis.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import jarvis.content.ContentRepo
import jarvis.content.ContentReconcile
import jarvis.content.KnowledgeConcept
import jarvis.tutor.CardStatus
import jarvis.tutor.FsrsCardRepo
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorTypes
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.VerificationStatus
import jarvis.tutor.AuditOutcome
import jarvis.tutor.VerificationStatus_
import jarvis.tutor.csrfProtect
import jarvis.tutor.verify.CitationGuard
import jarvis.tutor.verify.CitedClaim
import jarvis.tutor.verify.HonestFloor
import jarvis.tutor.verify.honestFloorOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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
 * Phase-2 trust routes (Batch-5, master-plan Area B). Mounted from [installTutorRoutes].
 *
 *  - GET  /api/v1/verify/{kcId}/status   — the trust-badge read (B8 resolved status + cited claims +
 *                                          honest floor + pinned badge text; NEVER "verified correct").
 *  - POST /api/v1/admin/verify/{kcId}    — owner-only OFFLINE re-audit of one KC's claims.
 *  - POST /api/v1/fsrs/{id}/report-wrong — student correction: write report_wrong(OPEN) + pause the
 *                                          card + flip the KC faithful→pending via REPORT_WRONG.
 *
 * Trust-language rule (§P / master §1): the badge text is PINNED — `faithful` ⇒ "matches your lecture /
 * faithful to your source", everything else ⇒ "unverified". It MUST NEVER render "verified correct".
 */

private val trustJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Resolve the content/ directory (matches CuratorRoutes' resolution). */
private fun trustContentDir(): Path =
    Path.of(
        System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )

/** Wire reply for GET /api/v1/verify/{kcId}/status (§I/§P/§Q, master §2.2). */
@Serializable
data class ApiVerifyStatusReply(
    val verification_status: VerificationStatus,
    /** PINNED copy — NEVER "verified correct". */
    val badge_text: String,
    val claims: List<CitedClaim>,
    val honest_floor: HonestFloor,
)

/** Wire reply for POST /api/v1/admin/verify/{kcId}. */
@Serializable
data class ApiAdminVerifyReply(
    val kcId: String,
    val audited: Int,
    val newStatus: VerificationStatus,
    val results: List<ApiAdminVerifyResult>,
)

@Serializable
data class ApiAdminVerifyResult(
    val claimId: String,
    val priorStatus: VerificationStatus,
    val outcome: String,
    val newStatus: VerificationStatus,
)

/** Request + reply for POST /api/v1/fsrs/{id}/report-wrong. */
@Serializable
data class ApiReportWrongRequest(
    /** Optional free-text / raw grade context the student is disputing. */
    val gradeAttemptRaw: String = "",
)

@Serializable
data class ApiReportWrongReply(
    val reportId: String,
    val cardId: String,
    val kcId: String,
    val cardPaused: Boolean,
    val newStatus: VerificationStatus,
)

/**
 * Test seam — builds the offline [jarvis.tutor.verify.VerificationRunner] for `admin/verify`.
 * Production wires the real two-family runner (RELAY + OPENROUTER) via [VerifyAdmin.liveRunnerFor];
 * route tests override this to inject fakes (no network). Internal so only in-module (test) code
 * reassigns it.
 */
internal var verifyRunnerFactory: (db: org.jetbrains.exposed.sql.Database, repo: ContentRepo) ->
jarvis.tutor.verify.VerificationRunner = { db, repo -> VerifyAdmin.liveRunnerFor(db, repo) }

/** Pinned badge copy. NEVER "verified correct" (§P / master §1). */
internal fun badgeTextFor(status: VerificationStatus): String =
    when (status) {
        VerificationStatus.faithful -> "matches your lecture / faithful to your source"
        else -> "unverified"
    }

/** Helpers shared by the routes + the offline CLI. */
object VerifyAdmin {
    /** The real two-family + non-LLM runner from the env-provisioned families (offline). */
    fun liveRunnerFor(
        db: org.jetbrains.exposed.sql.Database,
        repo: ContentRepo,
    ): jarvis.tutor.verify.VerificationRunner =
        jarvis.tutor.verify.VerificationRunner(
            db = db,
            legA = jarvis.tutor.verify.TwoFamilyDeriver.Leg(jarvis.tutor.verify.LegFamily.RELAY, jarvis.RelayLlm()),
            legB = jarvis.tutor.verify.TwoFamilyDeriver.Leg(
                jarvis.tutor.verify.LegFamily.OPENROUTER, jarvis.OpenRouterChatLlm(),
            ),
            nonLlmLegFor = { subject -> jarvis.tutor.verify.nonLlmLegFor(subject) },
            rawSourceFor = { claim ->
                val doc = claim.source?.doc
                    ?: error("claim ${claim.claimId} has no source.doc to locate against")
                repo.sourceText(claim.subject, doc)
                    ?: error("no _sources/$doc.md for subject ${claim.subject}")
            },
        )

    /** Find a KC by id across every subject in the manifest, or null (H15). */
    fun findKc(repo: ContentRepo, kcId: String): KnowledgeConcept? =
        repo.loadManifest().subjects
            .firstNotNullOfOrNull { sub ->
                runCatching { repo.loadSubject(sub.id).kcs.firstOrNull { it.id == kcId } }.getOrNull()
            }

    /** Resolve the KC's runtime status from the B8 table; fall back to the YAML seed, else unverified. */
    fun resolveStatus(
        db: org.jetbrains.exposed.sql.Database,
        kcId: String,
        kc: KnowledgeConcept?,
    ): VerificationStatus = transaction(db) {
        val row = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq kcId }
            .singleOrNull()
        val live = row?.get(KcVerificationStatusTable.status)
            ?.let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
        live
            ?: kc?.verification_status?.let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
            ?: VerificationStatus.unverified
    }
}

/** Owner-scope gate (mirrors CuratorRoutes.requireOwner; 401 unauth, 403 non-owner). */
private suspend fun RoutingContext.requireOwnerTrust(block: suspend (userId: String) -> Unit) {
    val ctx = call.application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, """{"error":"not authenticated"}"""); return }
    val user = UserRepo(ctx.db).findById(userId)
    if (user?.scope != UserScope.OWNER) {
        call.respondText(
            """{"error":"admin verify requires OWNER scope"}""",
            ContentType.Application.Json, HttpStatusCode.Forbidden,
        )
        return
    }
    block(userId)
}

fun Route.installTrustRoutes() {

    // ── GET /api/v1/verify/{kcId}/status ───────────────────────────────────────────────────────
    // Reads the B8 resolved status, builds cited claims via CitationGuard.attach, derives the
    // honest floor (§P), pins the badge copy. H15: an unknown kcId degrades to `unverified` with
    // empty claims (never a 500, never an over-claim).
    get("/api/v1/verify/{kcId}/status") {
        requireUser {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"kcId required"}"""); return@requireUser }

            val repo = ContentRepo(trustContentDir())
            val kc = runCatching { VerifyAdmin.findKc(repo, kcId) }.getOrNull()

            // H15: null kc_id / unknown KC ⇒ degrade to unverified + no claims (never over-claim).
            val status = if (kc == null) {
                // No content KC: still consult the B8 row (a runtime audit may exist), else unverified.
                VerifyAdmin.resolveStatus(ctx.db, kcId, null)
            } else {
                VerifyAdmin.resolveStatus(ctx.db, kcId, kc)
            }

            // Build the cited claims. Only span/quote-resolved claims survive the CitationGuard
            // chokepoint; a claim with a null SourceRef is dropped (it throws — never shipped uncited).
            val claims: List<CitedClaim> = if (kc == null) {
                emptyList()
            } else {
                ContentReconcile.claimsFor(kc).mapNotNull { claim ->
                    runCatching { CitationGuard.attach(claim, status) }.getOrNull()
                }
            }

            call.respond(
                HttpStatusCode.OK,
                ApiVerifyStatusReply(
                    verification_status = status,
                    badge_text = badgeTextFor(status),
                    claims = claims,
                    honest_floor = honestFloorOf(status),
                ),
            )
        }
    }

    // ── POST /api/v1/admin/verify/{kcId} ───────────────────────────────────────────────────────
    // Owner-only OFFLINE re-audit. Emits the KC's claims (Stage-9), runs the two-family +
    // non-LLM + round-trip audit, writes verification_audit + kc_verification_status (B8).
    post("/api/v1/admin/verify/{kcId}") {
        requireOwnerTrust {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireOwnerTrust }
            call.csrfProtect {
                val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"kcId required"}"""); return@csrfProtect }
                val repo = ContentRepo(trustContentDir())
                val kc = runCatching { VerifyAdmin.findKc(repo, kcId) }.getOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"KC not found"}"""); return@csrfProtect }

                val claims = ContentReconcile.claimsFor(kc)
                if (claims.isEmpty()) {
                    call.respond(HttpStatusCode.UnprocessableEntity, """{"error":"KC has no auditable claims"}""")
                    return@csrfProtect
                }
                val runner = verifyRunnerFactory(ctx.db, repo)
                val results = try {
                    runBlocking { runner.audit(claims) }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        """{"error":"audit failed: ${e.javaClass.simpleName}"}""",
                    )
                    return@csrfProtect
                }
                val finalStatus = VerifyAdmin.resolveStatus(ctx.db, kcId, kc)
                call.respond(
                    HttpStatusCode.OK,
                    ApiAdminVerifyReply(
                        kcId = kcId,
                        audited = results.size,
                        newStatus = finalStatus,
                        results = results.map {
                            ApiAdminVerifyResult(
                                claimId = it.claimId,
                                priorStatus = it.priorStatus,
                                outcome = it.outcome.name,
                                newStatus = it.newStatus,
                            )
                        },
                    ),
                )
            }
        }
    }

    // ── POST /api/v1/fsrs/{id}/report-wrong ────────────────────────────────────────────────────
    // Student correction. Writes report_wrong(resolution=OPEN), pauses the card (status=PAUSED),
    // and flips the KC faithful→pending via the REPORT_WRONG transition (§2.5).
    post("/api/v1/fsrs/{id}/report-wrong") {
        requireUser { userId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
            call.csrfProtect {
                val cardId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"id required"}"""); return@csrfProtect }

                val card = FsrsCardRepo(ctx.db).findById(cardId, userId)
                    ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"card not found"}"""); return@csrfProtect }

                val req = try {
                    trustJson.decodeFromString(ApiReportWrongRequest.serializer(), call.receiveText())
                } catch (_: Exception) {
                    ApiReportWrongRequest()  // tolerate a missing/empty body
                }

                // The KC this card teaches. RUBRIC_CRITERION cards carry kc_id; if absent, there is
                // no KC to flip — we still pause the card + log the report (FAIL-LOUD: never silently drop).
                val kcId = card.kcId ?: ""
                val now = Instant.now()
                val reportId = TutorTypes.ulid()

                transaction(ctx.db) {
                    // 1. log the report (resolution = OPEN)
                    ReportWrongTable.insert {
                        it[id] = reportId
                        it[ReportWrongTable.userId] = userId
                        it[ReportWrongTable.kcId] = kcId
                        it[ReportWrongTable.cardId] = cardId
                        it[gradeAttemptRaw] = req.gradeAttemptRaw.take(8000)
                        it[reportedAt] = now
                        it[resolution] = "OPEN"
                    }
                    // 2. pause the card (status=PAUSED, pausedAt=now) — out of the schedulable queue.
                    FsrsCardsTable.update({
                        (FsrsCardsTable.id eq cardId) and (FsrsCardsTable.userId eq userId)
                    }) {
                        it[status] = CardStatus.PAUSED.name
                        it[pausedAt] = now
                    }
                }

                // 3. flip the KC via the REPORT_WRONG transition (faithful→pending). Only when this
                //    card maps to a KC; the gate now DENYs SR-entry for it (open report_wrong + pending).
                var newStatus = VerificationStatus.unverified
                if (kcId.isNotBlank()) {
                    newStatus = transaction(ctx.db) {
                        val row = KcVerificationStatusTable.selectAll()
                            .where { KcVerificationStatusTable.kcId eq kcId }
                            .singleOrNull()
                        val prior = row?.get(KcVerificationStatusTable.status)
                            ?.let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
                            ?: VerificationStatus.unverified
                        val next = VerificationStatus_.transition(prior, AuditOutcome.REPORT_WRONG)
                        if (row == null) {
                            KcVerificationStatusTable.insert {
                                it[KcVerificationStatusTable.kcId] = kcId
                                it[status] = next.name
                                it[updatedAt] = now
                            }
                        } else {
                            KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kcId }) {
                                it[status] = next.name
                                it[updatedAt] = now
                            }
                        }
                        next
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiReportWrongReply(
                        reportId = reportId,
                        cardId = cardId,
                        kcId = kcId,
                        cardPaused = true,
                        newStatus = newStatus,
                    ),
                )
            }
        }
    }
}
