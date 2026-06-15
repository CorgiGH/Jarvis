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
import jarvis.tutor.VerificationAuditTable
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
import jarvis.tutor.verify.ReportResolution
import jarvis.tutor.verify.ReportWrongQuery
import kotlinx.coroutines.runBlocking
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

/**
 * Topology guard (Bundle-2, master-plan §PHASE-2-REMAINING step 2 / D7). The offline audit
 * (`runner.audit`) is OWNER-CLI-ONLY — it re-derives every claim against two LLM families + a
 * non-LLM leg and, under D6, will load a ~0.4-0.8 GB NLI model. It MUST NOT run inside the
 * `POST /admin/verify` HTTP handler on the SERVED build: that would load a model on a request
 * thread of the weak ~1.9 GB-free VPS (OOM-kills the live box) and violates the LOCKED
 * "audit is OFFLINE batch, never request-path" rule.
 *
 * The served build leaves `JARVIS_AUDIT_ROUTE` UNSET ⇒ this returns false ⇒ the route short-
 * circuits with 503 "audit is owner-CLI-only" (AFTER owner-auth + CSRF) and NEVER calls
 * `runner.audit`. The canonical offline entry stays [jarvis.tutor.verify.VerifyContentCli]
 * (the `verifyContent` gradle task). Only an owner who explicitly sets `JARVIS_AUDIT_ROUTE=1`
 * on their PC re-enables the in-handler path. A test seam (an `internal var`) so route tests
 * flip it without touching the real process env.
 */
internal var auditRouteEnabled: () -> Boolean = {
    System.getenv("JARVIS_AUDIT_ROUTE")?.trim()?.lowercase() in setOf("1", "true", "yes", "on")
}

/** Pinned badge copy. NEVER "verified correct" (§P / master §1). */
internal fun badgeTextFor(status: VerificationStatus): String =
    when (status) {
        VerificationStatus.faithful -> "matches your lecture / faithful to your source"
        else -> "unverified"
    }

/**
 * B5r-2 (D-R5) — the PINNED badge copy keyed on the SERVED honest floor, not the raw status. The
 * locked product rule: the lecture badge promises lecture-GROUNDING (every cited quote relocates
 * LIVE), not the stronger cross-checked `faithful`, so a `faithful` KC AND a merely `lecture_grounded`
 * KC carry the SAME user-facing string. NEVER "verified correct" (§P / master §1).
 */
internal fun badgeTextForFloor(floor: HonestFloor): String =
    when (floor) {
        HonestFloor.FAITHFUL_TO_SOURCE -> "matches your lecture / faithful to your source"
        HonestFloor.UNVERIFIED -> "unverified"
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
            // Phase-0 (2026-06-15): both legs are NETWORK; wrap each in RetryingLlm (bounded exp-backoff,
            // retries ONLY on IOException) so a transient relay/OpenRouter blip no longer flips a KC to a
            // permanent `failed` (the pa-kc-006 false-negative class). Mirrors the request path.
            legA = jarvis.tutor.verify.TwoFamilyDeriver.Leg(
                jarvis.tutor.verify.LegFamily.RELAY, jarvis.RetryingLlm(jarvis.RelayLlm()),
            ),
            legB = jarvis.tutor.verify.TwoFamilyDeriver.Leg(
                jarvis.tutor.verify.LegFamily.OPENROUTER, jarvis.RetryingLlm(jarvis.OpenRouterChatLlm()),
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

    /**
     * Resolve the KC's runtime status from the B8 table (`kc_verification_status`) — the ONLY source
     * of served trust. F4-serve: a KC with NO B8 row resolves to `unverified`, NEVER the authored YAML
     * seed. The seed `verification_status: faithful` means "the author hopes so" — with zero audit legs
     * run there is no evidence to serve `faithful`. Only an actual audit (which writes a B8 row) may
     * promote past `unverified`.
     *
     * D8 content-hash staleness gate: a `faithful` row is served as `faithful` ONLY when the row's
     * `content_hash` (the fingerprint of the content AT audit time) matches the hash of the CURRENT
     * serving content ([ContentReconcile.kcContentHash] over the live [kc]). If the lecture was edited
     * after the audit (hash mismatch), the row carries no hash (NULL — a legacy/partial row that cannot
     * prove a match), or there is no live [kc] to recompute against, the served status falls CLOSED to
     * `unverified` — the badge NEVER lies "matches your lecture" over text it never actually checked.
     * The gate applies ONLY to `faithful`; any non-faithful status is returned verbatim.
     */
    fun resolveStatus(
        db: org.jetbrains.exposed.sql.Database,
        kcId: String,
        kc: KnowledgeConcept?,
    ): VerificationStatus = transaction(db) {
        val row = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq kcId }
            .singleOrNull()
        val status = row?.get(KcVerificationStatusTable.status)
            ?.let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
            ?: VerificationStatus.unverified

        // D8: only `faithful` may be staled out; every other status is honest as-is.
        if (status != VerificationStatus.faithful) return@transaction status

        // D3: an OPEN report_wrong dispute outranks a faithful row — refuse the faithful badge while a
        // learner-filed dispute is unresolved. FAIL-CLOSED on a query throw (assume OPEN ⇒ unverified).
        if (ReportWrongQuery.hasOpenReportWrong(this, kcId)) return@transaction VerificationStatus.unverified

        val rowHash = row?.get(KcVerificationStatusTable.contentHash)
        val currentHash = kc?.let { ContentReconcile.kcContentHash(it) }
        // D1: the SECONDARY source-span fingerprint must be PRESENT to serve faithful. The VPS cannot
        // recompute it (no `_sources` bytes, D7) — it presence/version-checks it: a NULL source_span_hash
        // (never source-audited, or NULLed by the PC-side source-edit watcher) fails CLOSED exactly like a
        // NULL content_hash. (DETECTION of a source EDIT is PC-side, ContentReconcile.reconcileSourceSpans.)
        val sourceSpanHash = row?.get(KcVerificationStatusTable.sourceSpanHash)
        // Fail CLOSED unless the audited hash and the live-content hash both exist AND match,
        // AND the source-span fingerprint is present.
        if (rowHash != null && currentHash != null && rowHash == currentHash && sourceSpanHash != null) {
            VerificationStatus.faithful
        } else {
            VerificationStatus.unverified
        }
    }

    /**
     * B5r-2 (D-R5/D-R6) — the SERVED honest floor, DECOUPLED from the strict `faithful` status. The
     * lecture badge promises lecture-GROUNDING, not the stronger cross-checked `faithful`. Returns
     * [HonestFloor.FAITHFUL_TO_SOURCE] (⇒ the "matches your lecture / faithful to your source" badge)
     * when EITHER:
     *   - the B8 row's status is `faithful`, OR
     *   - the B8 row's `lecture_grounded == true` (a grounded KC that is only `uncertain` — e.g. an
     *     equational claim still awaiting its math check — still lights the lecture badge, D-R5),
     * and in BOTH cases ONLY after the SAME D8 [contentHash] staleness gate passes. Otherwise
     * [HonestFloor.UNVERIFIED].
     *
     * CRITICAL (D-R6) — the D8 staleness gate wraps `lecture_grounded` EXACTLY as it wraps `faithful`:
     * a hash mismatch (lecture edited after the audit), a NULL `content_hash`, a NULL `lecture_grounded`
     * (legacy/partial row), or no live [kc] to recompute against ⇒ fail CLOSED to `UNVERIFIED`. A
     * grounded badge over edited / unproven content is forbidden, same severity as a faithful one.
     *
     * NOTE: this is a SERVE-ONLY display floor — it does NOT change [resolveStatus]; the served
     * `verification_status` stays honest (a grounded-but-not-faithful KC reports `uncertain`).
     */
    fun servedHonestFloor(
        db: org.jetbrains.exposed.sql.Database,
        kcId: String,
        kc: KnowledgeConcept?,
    ): HonestFloor = transaction(db) {
        val row = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq kcId }
            .singleOrNull()
            ?: return@transaction HonestFloor.UNVERIFIED

        val status = row[KcVerificationStatusTable.status]
            .let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
        val grounded = row[KcVerificationStatusTable.lectureGrounded] == true
        // The lecture badge is gated on faithful OR grounded — but NOTHING shows without fresh content.
        if (status != VerificationStatus.faithful && !grounded) return@transaction HonestFloor.UNVERIFIED

        // D3: an OPEN report_wrong dispute outranks the lecture badge too — refuse it (fail-closed to
        // UNVERIFIED) while a learner-filed dispute is unresolved, regardless of the hash match. The
        // shared helper fails CLOSED on a query throw (assume OPEN). (MF-2 already clears grounded +
        // NULLs the hashes on report-wrong; this is the belt-and-braces serve-side refusal D3 mandates.)
        if (ReportWrongQuery.hasOpenReportWrong(this, kcId)) return@transaction HonestFloor.UNVERIFIED

        // D8 staleness gate — wraps `lecture_grounded` EXACTLY as it wraps `faithful`. Fail CLOSED
        // unless the audited content_hash and the live-content hash both exist AND match.
        val rowHash = row[KcVerificationStatusTable.contentHash]
        val currentHash = kc?.let { ContentReconcile.kcContentHash(it) }
        // D1: the source-span fingerprint must ALSO be present (the VPS can only presence-check it; a
        // NULL fails CLOSED like a NULL content_hash — see resolveStatus). Source-EDIT detection is
        // PC-side (ContentReconcile.reconcileSourceSpans), which NULLs this column + re-pends on a change.
        val sourceSpanHash = row[KcVerificationStatusTable.sourceSpanHash]
        if (rowHash != null && currentHash != null && rowHash == currentHash && sourceSpanHash != null) {
            HonestFloor.FAITHFUL_TO_SOURCE
        } else {
            HonestFloor.UNVERIFIED
        }
    }

    /**
     * D8 content-staleness predicate. True iff the KC's audited content_hash can NO LONGER prove it
     * matches the live serving content — i.e. the row's `content_hash` is absent (NULL / no B8 row),
     * there is no live [kc] to recompute against, or the recomputed hash differs from the stored one
     * (the lecture was edited after the audit). This is the SOLE condition under which a per-claim
     * `faithful` must be demoted (a faithful verdict over text that was actually re-checked stays
     * honest while the text is unchanged).
     *
     * NOTE (multi-claim KC, FIX-B): content-staleness is INDEPENDENT of the KC-level aggregate. A
     * multi-claim KC legitimately aggregates to `uncertain` (some claims floor to the uncertain floor)
     * while a sibling INVARIANT claim is genuinely `faithful` at the CURRENT content. The cap must key
     * on actual content change, NOT on the aggregate being non-faithful — otherwise it would wrongly
     * bury every honestly-faithful per-claim verdict the moment any sibling claim floors to uncertain.
     */
    fun contentStale(
        db: org.jetbrains.exposed.sql.Database,
        kcId: String,
        kc: KnowledgeConcept?,
    ): Boolean = transaction(db) {
        val row = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq kcId }
            .singleOrNull()
        val rowHash = row?.get(KcVerificationStatusTable.contentHash)
        val currentHash = kc?.let { ContentReconcile.kcContentHash(it) }
        // Stale unless BOTH hashes exist AND match.
        !(rowHash != null && currentHash != null && rowHash == currentHash)
    }

    /**
     * F5 — resolve each claim's OWN per-claim verdict from `verification_audit` (the audit-of-record),
     * keyed on `claim_id`, NOT the single KC-level status broadcast onto every claim. For each claimId
     * the LATEST audit row (by `audited_at`) supplies the claim's `status`. A claim with NO audit row
     * falls CLOSED to `unverified` (it was never audited — never inherit the KC's faithful).
     *
     * D8 staleness cap: a per-claim `faithful` is only honest while the KC's content is unchanged since
     * the audit. [contentStale] is the D8 content-change verdict ([contentStale]) — when the live
     * content no longer hashes to the audited content (lecture edited / NULL hash / no live kc), every
     * per-claim `faithful` is demoted to `unverified`. Non-faithful per-claim verdicts pass through
     * verbatim. The cap is keyed on CONTENT CHANGE only, never on the KC aggregate being non-faithful —
     * so a genuinely-faithful claim under a multi-claim `uncertain` KC still serves faithful (FIX-B).
     *
     * Returns a `claimId -> VerificationStatus` map (absent ⇒ caller falls closed to `unverified`).
     */
    fun resolvePerClaimStatuses(
        db: org.jetbrains.exposed.sql.Database,
        claimIds: Collection<String>,
        contentStale: Boolean,
    ): Map<String, VerificationStatus> {
        if (claimIds.isEmpty()) return emptyMap()
        // A per-claim faithful is only honest while the audited content is unchanged. When the content
        // is stale (hash mismatch / NULL hash / no live kc), demote it to unverified.
        val faithfulIsStale = contentStale
        return transaction(db) {
            val out = HashMap<String, VerificationStatus>(claimIds.size)
            for (cid in claimIds.toSet()) {
                val latest = VerificationAuditTable.selectAll()
                    .where { VerificationAuditTable.claimId eq cid }
                    .orderBy(VerificationAuditTable.auditedAt to SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()
                    ?: continue   // no audit row ⇒ caller falls closed to unverified
                val claimStatus = latest[VerificationAuditTable.status]
                    .let { runCatching { VerificationStatus.valueOf(it) }.getOrNull() }
                    ?: VerificationStatus.unverified
                out[cid] = if (claimStatus == VerificationStatus.faithful && faithfulIsStale) {
                    VerificationStatus.unverified
                } else {
                    claimStatus
                }
            }
            out
        }
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
            //
            // F5: stamp each claim's OWN per-claim verdict (read from verification_audit by claimId),
            // NOT the single KC-level `status` broadcast onto every claim. An unaudited claim falls
            // closed to `unverified`; a per-claim `faithful` is capped down when the KC-level D8 gate
            // already demoted faithful (stale content / NULL hash / no live kc).
            val claims: List<CitedClaim> = if (kc == null) {
                emptyList()
            } else {
                val kcClaims = ContentReconcile.claimsFor(kc)
                // D8 cap keys on CONTENT change only, not on the KC aggregate. A genuinely-faithful
                // per-claim verdict under a multi-claim `uncertain` KC still serves faithful (FIX-B).
                val perClaim = VerifyAdmin.resolvePerClaimStatuses(
                    ctx.db, kcClaims.map { it.claimId },
                    contentStale = VerifyAdmin.contentStale(ctx.db, kcId, kc),
                )
                kcClaims.mapNotNull { claim ->
                    val claimStatus = perClaim[claim.claimId] ?: VerificationStatus.unverified
                    runCatching { CitationGuard.attach(claim, claimStatus) }.getOrNull()
                }
            }

            // B5r-2 (D-R5/D-R6): the SERVED badge + honest floor are DECOUPLED from the strict
            // `faithful` status — a `lecture_grounded` KC (cited quotes all relocate LIVE) lights the
            // "matches your lecture" badge even at `uncertain`, while `verification_status` stays
            // honest. The D8 staleness gate wraps grounded EXACTLY as it wraps faithful (stale / NULL
            // hash / NULL grounded ⇒ fail closed to UNVERIFIED). verification_status is unchanged.
            val servedFloor = VerifyAdmin.servedHonestFloor(ctx.db, kcId, kc)
            call.respond(
                HttpStatusCode.OK,
                ApiVerifyStatusReply(
                    verification_status = status,
                    badge_text = badgeTextForFloor(servedFloor),
                    claims = claims,
                    honest_floor = servedFloor,
                ),
            )
        }
    }

    // ── POST /api/v1/admin/verify/{kcId} ───────────────────────────────────────────────────────
    // TOPOLOGY GUARD (Bundle-2 / D7): the offline audit is OWNER-CLI-ONLY. On the SERVED build
    // (JARVIS_AUDIT_ROUTE unset) this short-circuits with 503 AFTER owner-auth + CSRF and NEVER
    // calls runner.audit in-thread — no model-load on a request thread of the weak VPS. The
    // canonical offline entry is VerifyContentCli (the `verifyContent` gradle task). Only when an
    // owner explicitly opts in (JARVIS_AUDIT_ROUTE=1, PC-side) does the in-handler path run the
    // two-family + non-LLM + round-trip audit and write verification_audit + kc_verification_status (B8).
    post("/api/v1/admin/verify/{kcId}") {
        requireOwnerTrust {
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireOwnerTrust }
            call.csrfProtect {
                // Served-build guard: the audit never runs on the request path here. FAIL-CLOSED to
                // 503 (owner-CLI-only) BEFORE building the runner or touching the LLM families.
                if (!auditRouteEnabled()) {
                    call.respondText(
                        """{"error":"audit is owner-CLI-only — run the verifyContent gradle task (offline batch); the request-path audit is disabled on this build"}""",
                        ContentType.Application.Json, HttpStatusCode.ServiceUnavailable,
                    )
                    return@csrfProtect
                }
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

                // The KC this card teaches. RUBRIC_CRITERION cards carry kc_id. F6: a kc-less card
                // (e.g. GAP_PROMOTION) has NO KC to key the report on — writing one keyed on '' would
                // create an orphan row polluting the kc_id space. Reject with 422 (never store '' as a
                // real key, never write an orphan report_wrong).
                val kcId = card.kcId?.takeIf { it.isNotBlank() }
                    ?: run {
                        call.respondText(
                            """{"error":"this card has no kc_id — report-wrong needs a KC to flag; nothing to report on a kc-less card"}""",
                            ContentType.Application.Json, HttpStatusCode.UnprocessableEntity,
                        )
                        return@csrfProtect
                    }
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

                // 3. flip the KC via the REPORT_WRONG transition (faithful→pending). The card always
                //    maps to a KC here (kc-less cards were already rejected 422 above); the gate now
                //    DENYs SR-entry for it (open report_wrong + pending).
                //
                //    MF-2 (D-R18) — in the SAME transaction, CLEAR the lecture-grounded badge state on a
                //    learner-disputed KC: set `lecture_grounded = false` and NULL out `content_hash` +
                //    `last_audit_run_id`. Otherwise `servedHonestFloor` would keep serving the
                //    "matches your lecture" badge over content a student just flagged as wrong (the audit
                //    that wrote grounded=true + a matching content_hash is now in dispute). With
                //    grounded=false AND a NULL content_hash, servedHonestFloor fails CLOSED to UNVERIFIED
                //    (status is no longer faithful AND the D8 staleness gate can't prove a match).
                val newStatus = transaction(ctx.db) {
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
                            it[lectureGrounded] = false
                            it[contentHash] = null
                            // D1 (MF-2): also NULL the source-span fingerprint on a dispute — the audit
                            // that wrote it is now in dispute; the badge must fail closed on BOTH hashes.
                            it[sourceSpanHash] = null
                            it[lastAuditRunId] = null
                            it[updatedAt] = now
                        }
                    } else {
                        KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kcId }) {
                            it[status] = next.name
                            it[lectureGrounded] = false
                            it[contentHash] = null
                            it[sourceSpanHash] = null
                            it[lastAuditRunId] = null
                            it[updatedAt] = now
                        }
                    }
                    next
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

    // ── POST /api/v1/admin/report-wrong/{kcId}/retract ───────────────────────────────────────────
    // D3 closing edge (the OWNER accept-the-report terminal). OWNER-ONLY. Writes resolution=RETRACTED
    // + stamps resolved_by (owner) + resolved_at on every OPEN report_wrong for the KC, leaving the KC
    // DARK (the content was genuinely wrong; the badge does NOT relight). This is the SECOND escape
    // from OPEN (the FIRST is an owner re-audit that re-grounds ⇒ REVERIFIED_FAITHFUL in finalizeKc) —
    // so a dispute is never a permanent DoS-on-truth trap. The full multi-actor moderation UI stays
    // deferred; this exit edge does not.
    post("/api/v1/admin/report-wrong/{kcId}/retract") {
        requireOwnerTrust { ownerId ->
            val ctx = call.application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireOwnerTrust }
            call.csrfProtect {
                val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"kcId required"}"""); return@csrfProtect }
                val now = Instant.now()
                val closed = transaction(ctx.db) {
                    ReportWrongQuery.closeOpenReports(
                        this, kcId, ReportResolution.RETRACTED, resolvedBy = ownerId, resolvedAt = now,
                    )
                }
                call.respondText(
                    """{"kcId":"$kcId","resolution":"RETRACTED","closed":$closed}""",
                    ContentType.Application.Json, HttpStatusCode.OK,
                )
            }
        }
    }
}
