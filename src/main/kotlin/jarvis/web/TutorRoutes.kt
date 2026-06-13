package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json as ktorJson
import jarvis.tutor.AuditLinesTable
import jarvis.tutor.ContentRef
import jarvis.tutor.DaemonClient
import jarvis.tutor.EffectorAttemptsTable
import jarvis.tutor.EffectorDispatcher
import jarvis.tutor.EffectorType
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.GradeResult
import jarvis.tutor.GradeScoring
import jarvis.tutor.GrantSource
import jarvis.tutor.NonceCache
import jarvis.tutor.Outcome
import jarvis.tutor.Position
import jarvis.tutor.Range
import jarvis.tutor.Task
import jarvis.tutor.TaskRepo
import jarvis.tutor.TaskStatus
import jarvis.tutor.TextEdit
import jarvis.tutor.TrustGrant
import jarvis.tutor.TrustGrantRepo
import jarvis.tutor.ApplyEditRequest
import jarvis.tutor.KcMasteryTable
import jarvis.tutor.KnowledgeGapsTable
import jarvis.tutor.ProviderConfigTable
import jarvis.tutor.ScreenshotExtractor
import jarvis.tutor.SensorEventsTable
import jarvis.tutor.SensorPayload
import jarvis.tutor.SensorRepo
import jarvis.tutor.SessionRepo
import jarvis.tutor.SessionsTable
import jarvis.tutor.AI_LITERACY_VERSION
import jarvis.tutor.AiLiteracyConfirmationTable
import jarvis.tutor.AiLiteracyRepo
import jarvis.tutor.ConsentLogTable
import jarvis.tutor.ConsentRepo
import jarvis.tutor.UserPreferencesTable
import jarvis.tutor.UserPreferencesRepo
import jarvis.tutor.MagicLinkRepo
import jarvis.tutor.MagicLinkTokensTable
import jarvis.tutor.UserRepo
import jarvis.tutor.TaskPrepTable
import jarvis.tutor.TasksTable
import jarvis.tutor.TokenRepo
import jarvis.tutor.TokensTable
import jarvis.tutor.TrustGrantsTable
import jarvis.tutor.TutorContext
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorEvent
import jarvis.tutor.TutorEventLog
import jarvis.tutor.MailResult
import jarvis.tutor.RcodeRedacted
import jarvis.tutor.UsersTable
import jarvis.tutor.CardActionLogTable
import jarvis.tutor.taskdetect.DetectedTaskMappingTable
import jarvis.tutor.AttemptsTable
import jarvis.tutor.ExamDatesTable
import jarvis.tutor.MockExamsTable
import jarvis.tutor.GraderProviderSettingTable
import jarvis.tutor.ReportWrongTable
import jarvis.tutor.SessionSummariesTable
import jarvis.tutor.csrfProtect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

private val rng = SecureRandom()

/**
 * GDPR Art. 17 erasure cascade — the SINGLE source of truth for which user-scoped CHILD tables
 * /me/delete purges, in dependency order (child-first; every table here FKs UsersTable.id only,
 * with no cross-FKs between them, so this flat order is FK-safe under PRAGMA foreign_keys=ON).
 * UsersTable itself (the parent root) is deleted LAST by the route, AFTER every entry here.
 *
 * P0-1 fix: a missing user-FK table here makes the final UsersTable.deleteWhere FK-throw and roll
 * the whole erasure back → /me/delete 500s for any affected user. The CI invariant
 * [jarvis.tutor.MeDeleteCascadeTest] reflects over [jarvis.tutor.ALL_TABLES], finds EVERY table
 * whose column foreign-keys UsersTable.id, and asserts each is either in THIS list or in
 * [ME_DELETE_RETAINED_USER_FK_TABLES] — so the next user-FK table can never be silently missed
 * (class-killer, not enumerate-the-siblings).
 *
 * Each "add a new user-FK table" change MUST add it here (or, with a written GDPR 17(3) basis, to
 * the retained allowlist) or the invariant test fails loud.
 */
internal val ME_DELETE_CASCADE_TABLES: List<org.jetbrains.exposed.sql.Table> = listOf(
    ConsentLogTable,
    UserPreferencesTable,
    AiLiteracyConfirmationTable,
    TrustGrantsTable,
    SensorEventsTable,
    EffectorAttemptsTable,
    CardActionLogTable,
    DetectedTaskMappingTable,
    TasksTable,
    FsrsCardsTable,
    KnowledgeGapsTable,
    KcMasteryTable,         // P0-1 — user-FK (KcMastery.kt:16); was missing ⇒ erasure 500 for any grader.
    ProviderConfigTable,    // P0-1 — user-FK (ProviderConfig.kt:25); was missing ⇒ erasure 500.
    TokensTable,
    // B6 (Task 13) — Phase-1 user-scoped tables.
    SessionSummariesTable,
    AttemptsTable,
    ReportWrongTable,
    ExamDatesTable,
    MockExamsTable,
    GraderProviderSettingTable,  // per-user grader provider setting; user-scoped, GDPR Art.17 erasure.
    // SessionsTable is itself a user-FK child; deleted just before the parent UsersTable.
    SessionsTable,
)

/**
 * User-FK tables INTENTIONALLY retained across an Art. 17 erasure, with a documented legal basis.
 * The CI invariant treats membership here as "covered" so it does not false-fail — but adding a
 * table here is a deliberate, basis-bearing decision, never a way to silence the check.
 *
 * - AuditLinesTable — append-only hash-chained audit log retained under GDPR Art. 17(3)(b)
 *   (legal obligation / public-interest accountability). Erasing it would break the hash chain.
 */
internal val ME_DELETE_RETAINED_USER_FK_TABLES: List<org.jetbrains.exposed.sql.Table> = listOf(
    AuditLinesTable,
)

/**
 * Tutor Layer A surface:
 *  - GET /tutor/      → SPA index.html from src/main/resources/tutor-dist
 *  - GET /tutor/...   → SPA static assets (js, css, etc.)
 *  - GET /api/v1/health → liveness probe, no auth required
 *  - GET /auth/setup?t=<raw-token> → exchanges a one-time link token for a
 *      session sid (httpOnly) + csrf cookie (readable from JS for double-
 *      submit pattern), then 302→/tutor/. 401 on missing/invalid token.
 *      Requires `TutorContext` to be installed in `application.attributes`
 *      under `TutorContextKey` — runWeb is responsible for that wiring.
 *
 * Wired into [runWeb] via a separate `routing { ... }` block — Ktor merges
 * routing blocks into the same engine, so this composes cleanly with the
 * existing routes in WebMain.kt.
 */
/**
 * Test seam for the drill-grade LLM. Production uses [jarvis.OpenRouterChatLlm];
 * tests override this to inject a fake [jarvis.Llm] so the deterministic scoring
 * + mastery-write path can be exercised without a live model. Internal so only
 * in-module (test) code reassigns it.
 */
internal var drillGraderLlmFactory: () -> jarvis.Llm = { jarvis.OpenRouterChatLlm() }

/**
 * Production-facing per-user grader LLM resolver. Reads [GraderProvider] from
 * [jarvis.tutor.GraderProviderSettingRepo] and returns the matching [jarvis.Llm].
 *
 * Null (default) = production path. Non-null tests override to a deterministic lambda.
 * The call site checks this first; falls back to [drillGraderLlmFactory] when null so
 * every existing test that sets `drillGraderLlmFactory = { FakeLlm() }` continues
 * to work unchanged.
 */
internal var drillGraderLlmResolver: ((userId: String, db: org.jetbrains.exposed.sql.Database) -> jarvis.Llm)? = null

/**
 * Production per-user grader resolver. Wired into [drillGraderLlmResolver] by
 * [jarvis.web.WebMain] at startup ONLY. Left as `null` by default so tests
 * (which never call the wiring) fall through to the overridable
 * [drillGraderLlmFactory] seam — keeping every `drillGraderLlmFactory = { FakeLlm() }`
 * test working unchanged. (Bug fixed 2026-06-09: the field used to default to this
 * lambda, which bypassed the test seam and reddened the whole drill-grade suite.)
 */
internal fun productionGraderResolver(userId: String, db: org.jetbrains.exposed.sql.Database): jarvis.Llm =
    when (jarvis.tutor.GraderProviderSettingRepo(db).get(userId)) {
        jarvis.tutor.GraderProvider.free        -> jarvis.OpenRouterChatLlm()
        jarvis.tutor.GraderProvider.claude      -> jarvis.ClaudeMaxLlm()
        jarvis.tutor.GraderProvider.freellmapi  -> jarvis.RetryingLlm(jarvis.FreeLlmApiLlm())
    }

/**
 * Plan-6 Task 7 — the grader-CHAIN integration seam for `/drill/grade`.
 *
 * Resolves the routing chain for the graded problem and runs ONLY the non-LLM prefix legs
 * (everything before the first `LLM_JUDGE`). When a non-LLM leg DECIDES, the handler serves
 * that verdict and NEVER constructs an LLM (oracle/execution/rubric short-circuit). When every
 * non-LLM leg DEFERS, the handler falls through to the existing LLM-judge path — INV-6.3 holds
 * non-vacuously on real traffic because the chain builder never sees `[LLM_JUDGE]` alone, and the
 * deferred legs surface honestly in `degraded_legs_ro` / `decided_by="llm-judge"`.
 */
internal object DrillGradeChain {

    /** The wire id for a leg kind (mirrors §0.9-F: "numeric-oracle"|"execution"|"rubric"|"llm-judge"). */
    fun wireId(kind: jarvis.tutor.grader.GraderLegKind): String = when (kind) {
        jarvis.tutor.grader.GraderLegKind.NUMERIC_ORACLE -> "numeric-oracle"
        jarvis.tutor.grader.GraderLegKind.EXECUTION -> "execution"
        jarvis.tutor.grader.GraderLegKind.RUBRIC -> "rubric"
        jarvis.tutor.grader.GraderLegKind.LLM_JUDGE -> "llm-judge"
    }

    /** RO copy for a non-LLM leg that degraded (skipped) before the deciding leg (R-6-Q1 honesty). */
    fun degradedRo(kind: jarvis.tutor.grader.GraderLegKind): String = when (kind) {
        jarvis.tutor.grader.GraderLegKind.NUMERIC_ORACLE ->
            "Verificarea numerică nu se aplică acestui răspuns — evaluat prin alt corector."
        jarvis.tutor.grader.GraderLegKind.EXECUTION ->
            "Rularea codului indisponibilă pe acest server — verificat structural."
        jarvis.tutor.grader.GraderLegKind.RUBRIC ->
            "Niciun criteriu verificabil automat — evaluat de corectorul lingvistic."
        jarvis.tutor.grader.GraderLegKind.LLM_JUDGE -> ""
    }

    /**
     * Map a drill-grade request + server-resolved problem to a routing archetype-class.
     *
     * NUMERIC (the oracle-leads, LLM-bypassing chain) fires ONLY for a bare numeric problem with NO
     * server-resolved KC obligation ([hasKcObligation] == false). A KC-bearing drill keeps the
     * established LLM-served-teaching path (PROSE/CODE → `[RUBRIC, LLM_JUDGE]`) so the served
     * misconception/ladder payload + atomic mastery recording stay byte-identical — bank-problem
     * NUMERIC/TRACE archetypes route through the oracle via PracticeRoutes (Task 8), not /drill/grade.
     */
    fun archetypeClassFor(
        language: String?,
        canonicalAnswer: String?,
        shape: String?,
        hasKcObligation: Boolean,
    ): jarvis.tutor.grader.ArchetypeClass {
        val lang = language?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "text" }
        // A real code language → CODE (execution leg may still defer when no source/expected ride
        // the request, falling through to rubric/LLM honestly).
        if (lang != null) return jarvis.tutor.grader.ArchetypeClass.CODE
        // A numeric canonical answer with no code language AND no KC obligation → NUMERIC (oracle leads,
        // no LLM). A KC-bearing numeric drill stays on the served-teaching LLM path (classify below).
        if (!hasKcObligation && canonicalAnswer?.trim()?.toDoubleOrNull() != null) {
            return jarvis.tutor.grader.ArchetypeClass.NUMERIC
        }
        // Otherwise classify off the descriptive shape; default PROSE → [RUBRIC, LLM_JUDGE].
        return jarvis.tutor.grader.GraderRouting.classify(shape ?: "", lang)
    }

    /** A concrete non-LLM leg for a routing kind, or null for LLM_JUDGE (resolved by the handler). */
    fun legFor(kind: jarvis.tutor.grader.GraderLegKind): jarvis.tutor.grader.GraderLeg? = when (kind) {
        jarvis.tutor.grader.GraderLegKind.NUMERIC_ORACLE -> jarvis.tutor.grader.NumericOracleGrader()
        jarvis.tutor.grader.GraderLegKind.EXECUTION -> jarvis.tutor.grader.ExecutionGrader()
        jarvis.tutor.grader.GraderLegKind.RUBRIC -> jarvis.tutor.grader.RubricGrader()
        jarvis.tutor.grader.GraderLegKind.LLM_JUDGE -> null
    }

    /** Outcome of running the non-LLM prefix: either a leg DECIDED, or all deferred (→ LLM decides). */
    sealed interface PrefixResult {
        data class Decided(
            val decidedBy: jarvis.tutor.grader.GraderLegKind,
            val correct: Boolean,
            val score: Double,
            val itemVerdicts: List<jarvis.tutor.grader.ItemVerdict>,
            val feedbackRo: String,
            val degradedRo: List<String>,
        ) : PrefixResult

        /** Every non-LLM leg deferred; [degradedRo] is the RO copy the LLM reply surfaces. */
        data class AllDeferred(
            val degradedKinds: List<jarvis.tutor.grader.GraderLegKind>,
            val degradedRo: List<String>,
        ) : PrefixResult
    }

    /**
     * Run the non-LLM prefix of [chainKinds] against [input]. Returns [PrefixResult.Decided] the moment
     * a non-LLM leg decides (NO LLM constructed by the caller in that case), or [PrefixResult.AllDeferred]
     * when every non-LLM leg defers (the caller then runs the existing LLM path).
     */
    suspend fun runPrefix(
        chainKinds: List<jarvis.tutor.grader.GraderLegKind>,
        input: jarvis.tutor.grader.GradeInput,
    ): PrefixResult {
        val degradedKinds = mutableListOf<jarvis.tutor.grader.GraderLegKind>()
        for (kind in chainKinds) {
            if (kind == jarvis.tutor.grader.GraderLegKind.LLM_JUDGE) break // LLM tail — caller owns it.
            val leg = legFor(kind) ?: continue
            when (val outcome = leg.grade(input)) {
                is jarvis.tutor.grader.LegOutcome.Decided -> return PrefixResult.Decided(
                    decidedBy = kind,
                    correct = outcome.correct,
                    score = outcome.score,
                    itemVerdicts = outcome.itemVerdicts,
                    feedbackRo = outcome.feedbackRo,
                    degradedRo = degradedKinds.map { degradedRo(it) },
                )
                is jarvis.tutor.grader.LegOutcome.Defer -> degradedKinds.add(kind)
            }
        }
        return PrefixResult.AllDeferred(
            degradedKinds = degradedKinds.toList(),
            degradedRo = degradedKinds.map { degradedRo(it) },
        )
    }
}

/** E3 test seams. Production: generator = free OpenRouter Llama; critic = Claude via relay (DEC-1 relay-only). */
internal var drillGeneratorLlmFactory: () -> jarvis.Llm = { jarvis.OpenRouterChatLlm() }
internal var drillCriticLlmFactory: () -> jarvis.Llm = { jarvis.RetryingLlm(jarvis.RelayLlm()) }
/** E3: resolve a KC for generation grounding. Default loads from the content corpus. Overridden in tests. */
internal var drillKcLookup: (subject: String, kcId: String) -> jarvis.content.KnowledgeConcept? = { subject, kcId ->
    try {
        val contentDir = java.nio.file.Path.of(
            System.getProperty("JARVIS_CONTENT_DIR")
                ?: System.getenv("JARVIS_CONTENT_DIR")
                ?: "content"
        )
        jarvis.content.ContentRepo(contentDir).loadSubject(subject).kcs.firstOrNull { it.id == kcId }
    } catch (_: Exception) { null }
}

/** E3 Task 12 seam: /reprep problem extractor. Production runs PdfProblemExtractor.identifyProblems;
 *  tests override to return a deterministic list without touching the PDF or the network. */
internal var reprepExtractorFn: suspend (pdfPath: java.nio.file.Path, llm: jarvis.Llm) -> List<jarvis.tutor.Problem> =
    { pdfPath, llm -> jarvis.tutor.PdfProblemExtractor.identifyProblems(pdfPath, llm) }
/** E3 Task 12 seam: the Llm instance used for reprep extraction (ignored when reprepExtractorFn is overridden). */
internal var reprepExtractorLlmFactory: () -> jarvis.Llm = { jarvis.OpenRouterChatLlm() }

/**
 * Phase-3 GROUP 2 (B1) atomicity test seam. Invoked INSIDE the single atomic grade transaction,
 * immediately BEFORE the LAST in-txn write (the rubric-criterion card upsert), once per gated-faithful
 * KC. Production is a no-op; the B1 class-killer test injects a throw here to prove that a failure on
 * the last write step rolls back ALL prior writes (recordIn + attempts) — no orphaned partial state.
 */
internal var drillCardUpsertHook: () -> Unit = { }

fun Application.installTutorRoutes() {
    routing {
        // Static SPA bundle. Vite build output is committed at
        // src/main/resources/tutor-dist/ — placed on the runtime classpath
        // by the standard Gradle source-set layout.
        staticResources("/tutor", "tutor-dist") {
            default("index.html")
            // Hashed asset filenames (index-XYZ.js) are immutable + can be
            // aggressively cached. The shell index.html MUST be re-fetched
            // every visit so a deploy replaces the bundle on phones /
            // service workers / aggressive browsers.
            cacheControl { url ->
                val p = url.path
                if (p.isEmpty() || p.endsWith("/") || p.endsWith("/index.html") ||
                    p.endsWith("/tutor") || p.endsWith("/tutor/")) {
                    listOf(io.ktor.http.CacheControl.NoCache(null))
                } else {
                    listOf(io.ktor.http.CacheControl.MaxAge(31536000, mustRevalidate = false))
                }
            }
        }
        // Health endpoint (no auth required — see WebMain auth interceptor;
        // when this route is mounted alongside /healthz, both are public).
        get("/api/v1/health") {
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }

        // Gate 3: content-authoring curator routes (read + validate only).
        installCuratorRoutes()

        // Phase 2: trust-net routes (verify status / owner re-audit / report-wrong).
        installTrustRoutes()

        // Phase 3 Area C GROUP 3: read routes (queue/today, mastery, calibration).
        installQueueMasteryCalibrationRoutes()

        // Phase 3 Area C GROUP 4: session/close, placement, exam-dates.
        installSessionPlacementExamRoutes()

        // Phase 3 Area C GROUP 5: mock-exam (SYNC 200-only, H13 / CONTRADICTION F1).
        installMockExamRoutes()

        // Daemon health probe: checks if the local background daemon at
        // port 7331 is reachable. No auth required (public liveness data).
        get("/api/v1/daemon/health") {
            val url = "http://127.0.0.1:7331/health"
            val client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build()
            val now = java.time.Instant.now()
            val (reachable, tunnelUp) = try {
                val req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build()
                val resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
                Pair(resp.statusCode() in 200..299, true)
            } catch (_: java.net.ConnectException) {
                Pair(false, false)
            } catch (_: Exception) {
                Pair(false, false)
            }
            val lastSeenAt = if (reachable) now.toString() else null
            val body = buildString {
                append("""{"reachable":$reachable,"tunnelUp":$tunnelUp,"lastSeenAt":""")
                if (lastSeenAt != null) append('"').append(lastSeenAt).append('"')
                else append("null")
                append("}")
            }
            call.respondText(body, ContentType.Application.Json)
        }

        // Layer A auth bootstrap. Visited via a magic link emailed/sent to
        // the user. Mints a server-side session row + sets two cookies:
        //   jarvis_session — httpOnly, holds the sid (auth credential).
        //   csrf — JS-readable random nonce, mirrored into X-CSRF-Token on
        //          mutating calls (double-submit pattern; see Csrf.kt).
        // Both Strict + Secure (HTTPS-only); test transport still receives
        // the Set-Cookie headers — the secure attribute does not gate the
        // server response, only client storage.
        get("/auth/setup") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run {
                    call.respond(HttpStatusCode.InternalServerError, "TutorContext not installed")
                    return@get
                }
            val raw = call.request.queryParameters["t"]
            if (raw.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "missing token")
                return@get
            }
            val tokens = TokenRepo(ctx.db)
            val userId = tokens.findUserIdByToken(raw)
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "bad token")
                    return@get
                }
            val sessions = SessionRepo(ctx.db)
            val sid = sessions.create(userId, ttlSeconds = 60L * 60 * 24 * 14) // 14 days
            val csrf = ByteArray(16).also { rng.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            call.response.cookies.append(
                Cookie(
                    name = "jarvis_session",
                    value = sid,
                    httpOnly = true,
                    secure = true,
                    extensions = mapOf("SameSite" to "Strict"),
                    path = "/",
                    maxAge = 60 * 60 * 24 * 14,
                ),
            )
            call.response.cookies.append(
                Cookie(
                    name = "csrf",
                    value = csrf,
                    httpOnly = false,
                    secure = true,
                    extensions = mapOf("SameSite" to "Strict"),
                    path = "/",
                    maxAge = 60 * 60 * 24 * 14,
                ),
            )
            call.respondRedirect("/tutor/")
        }

        // Gate 2: email-based magic-link request.
        // User submits {email, lang} → server issues a token + emails the link.
        // Always returns 200 for valid emails (no enumeration). Requires no auth.
        // /auth/* is in the WebMain static-gate allowlist — no session needed.
        post("/auth/request-link") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            val body = try {
                sensorJson.decodeFromString(RequestLinkBody.serializer(), call.receiveText())
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, """{"error":"invalid body"}""")
            }
            val email = body.email.trim().lowercase()
            if (!EMAIL_REGEX.matches(email)) {
                return@post call.respond(HttpStatusCode.BadRequest, """{"error":"invalid email"}""")
            }
            val lang = if (body.lang == "en") "en" else "ro"
            val baseUrl = System.getenv("JARVIS_PUBLIC_BASE_URL") ?: "https://corgflix.duckdns.org"
            val rawToken = jarvis.tutor.MagicLinkRepo(ctx.db).issue(email, lang, ttlSeconds = 15 * 60)
            val link = "$baseUrl/auth/verify?token=$rawToken"
            val mailResult = ctx.mailer.send(email, link, lang)
            if (mailResult == MailResult.FAILED) {
                System.err.println("[request-link] mail delivery failed for $email")
            }
            call.respond(HttpStatusCode.OK, """{"ok":true}""")
        }

        // Gate 2: magic-link verification.
        // Consumes the one-time token, upserts the user by email, mints a
        // session, sets jarvis_session + csrf cookies, then 302→/tutor/.
        // Invalid/expired/already-consumed tokens redirect to login with an
        // error query param — no session cookie is set.
        get("/auth/verify") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
            val raw = call.request.queryParameters["token"]
            if (raw.isNullOrBlank()) {
                return@get call.respondRedirect("/tutor/login?error=missing")
            }
            val claim = MagicLinkRepo(ctx.db).consume(raw)
                ?: return@get call.respondRedirect("/tutor/login?error=expired")
            val userRepo = UserRepo(ctx.db)
            val user = userRepo.upsertByEmail(claim.email, claim.lang)
            userRepo.touchLastSeen(user.id, java.time.Instant.now())
            // Fresh sid every time — never adopt a client-supplied value (kills session fixation).
            val sid = SessionRepo(ctx.db).create(user.id, ttlSeconds = 60L * 60 * 24 * 14)
            val csrf = ByteArray(16).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            call.response.cookies.append(
                Cookie(
                    name = "jarvis_session",
                    value = sid,
                    httpOnly = true,
                    secure = true,
                    extensions = mapOf("SameSite" to "Strict"),
                    path = "/",
                    maxAge = 60 * 60 * 24 * 14,
                ),
            )
            call.response.cookies.append(
                Cookie(
                    name = "csrf",
                    value = csrf,
                    httpOnly = false,
                    secure = true,
                    extensions = mapOf("SameSite" to "Strict"),
                    path = "/",
                    maxAge = 60 * 60 * 24 * 14,
                ),
            )
            call.respondRedirect("/tutor/")
        }

        // Multi-user auto-session bootstrap. SPA hits this on mount to
        // check whether the visitor already has a valid session.
        // - Valid jarvis_session cookie → return current userId + csrf (no
        //   rotation) so the SPA can resume without a round-trip to /login.
        // - No cookie / invalid / expired → 401 so the SPA routes the
        //   visitor to /login. We no longer auto-mint an OWNER session —
        //   doing so would silently elevate any unauthenticated visitor to
        //   the owner role, which is a security hole in multi-user mode.
        get("/api/v1/tutor/auto-session") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val existingSid = call.request.cookies["jarvis_session"]
            val existingCsrf = call.request.cookies["csrf"]
            val uid = existingSid?.let { SessionRepo(ctx.db).findUserId(it) }
            if (uid == null) {
                return@get call.respond(HttpStatusCode.Unauthorized, """{"error":"login required"}""")
            }
            // Already authenticated — ensure csrf is always a real token, never null.
            // Only echo the existing cookie if it matches the exact [0-9a-f]{32} format;
            // a tampered/malformed value would otherwise be interpolated raw into JSON.
            val csrf = if (existingCsrf != null && Regex("^[0-9a-f]{32}$").matches(existingCsrf)) {
                existingCsrf
            } else {
                val fresh = ByteArray(16).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
                call.response.cookies.append(
                    Cookie(
                        name = "csrf",
                        value = fresh,
                        httpOnly = false,
                        secure = true,
                        extensions = mapOf("SameSite" to "Strict"),
                        path = "/",
                        maxAge = 60 * 60 * 24 * 14,
                    ),
                )
                fresh
            }
            call.respond(HttpStatusCode.OK, """{"ok":true,"userId":"$uid","csrf":"$csrf"}""")
        }

        // Layer B Task 1 — vision-LLM screenshot sensor.
        //
        // Body: JSON {imageBase64: string, mediaType?: string, taskId?: string,
        //             sensorId?: string, sensorVersion?: string, hint?: string}
        // Auth: jarvis_session cookie + X-CSRF-Token header (csrfProtect).
        // Response: {extracted: ScreenshotExtraction, eventSeq: long}
        // 503 when no vision LLM is configured (OPENROUTER_API_KEY unset).
        post("/api/v1/sensor/screenshot") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run {
                    call.respond(HttpStatusCode.InternalServerError, "TutorContext not installed")
                    return@post
                }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                if (sid.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, "missing session")
                    return@csrfProtect
                }
                val userId = SessionRepo(ctx.db).findUserId(sid)
                    ?: run {
                        call.respond(HttpStatusCode.Unauthorized, "invalid session")
                        return@csrfProtect
                    }
                if (!call.aiLiteracyGate(userId)) return@csrfProtect
                val vision = ctx.visionLlm
                if (vision == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        "vision LLM not configured — set OPENROUTER_API_KEY on the server",
                    )
                    return@csrfProtect
                }
                val req = try {
                    sensorJson.decodeFromString(ScreenshotRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "malformed request: ${e.message?.take(160)}",
                    )
                    return@csrfProtect
                }
                if (req.imageBase64.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "imageBase64 required")
                    return@csrfProtect
                }
                // Cap raw image size — base64 inflates ~33%, so 4 MB encoded
                // ≈ 3 MB PNG, plenty for a screen capture, prevents trivial
                // DoS via giant payloads.
                if (req.imageBase64.length > 4_000_000) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "imageBase64 over 4 MB cap")
                    return@csrfProtect
                }

                val extraction = try {
                    ScreenshotExtractor.extract(
                        llm = vision,
                        imageBase64 = req.imageBase64,
                        mediaType = req.mediaType ?: "image/png",
                        promptHint = req.hint,
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        "vision LLM failed: ${e.javaClass.simpleName}: ${e.message?.take(200)}",
                    )
                    return@csrfProtect
                }

                // Server-side source classification (council R3 fix #3):
                // never trust client-asserted source field. The same
                // ScreenshotExtraction the LLM produced is sufficient
                // signal — file path / raw OCR drives the verdict.
                val classification = jarvis.tutor.SourceClassifier.classify(extraction)
                val event = SensorRepo(ctx.db).append(
                    userId = userId,
                    sensorId = req.sensorId ?: "screenshot-default",
                    sensorVersion = req.sensorVersion ?: "1",
                    taskId = req.taskId,
                    payload = SensorPayload.ScreenshotMeta(
                        capturedAt = Instant.now().toString(),
                        focusedRegion = extraction.filePath,
                        ocrSummary = extraction.rawReply.take(2000),
                    ),
                )
                call.respond(
                    ScreenshotResponse(
                        eventSeq = event.eventSeq,
                        extracted = extraction,
                        readOnlyMode = classification.mode == jarvis.tutor.SourceClassifier.Mode.READ_ONLY,
                        readOnlyReason = classification.reason,
                    ),
                )
            }
        }

        // 2026-05-17 hot-work #4: UI telemetry endpoint. Lightweight ping
        // for feature-usage tracking (e.g. ledger.opened) so council 1778988899
        // can decide on Option B (delete KnowledgeLedger) at the 2026-05-31
        // window if zero usage is observed. Appends a TutorEvent with
        // event_type="ui_telemetry" carrying the event name + payload.
        // Best-effort + bounded: payload capped at 1500 chars to bound
        // poisoning blast-radius. csrfProtect for consistency with every
        // other state-changing POST route — defense-in-depth even though
        // the route only appends to the event log.
        post("/api/v1/sensor/telemetry") {
          call.csrfProtect {
            val sid = call.request.cookies["jarvis_session"] ?: "anon"
            val rawBody = try {
                call.receiveText().take(1500)
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, "missing body"); return@csrfProtect
            }
            val req = try {
                sensorJson.decodeFromString(ApiTelemetryRequest.serializer(), rawBody)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(120)}")
                return@csrfProtect
            }
            // Reject names that aren't [a-z][a-z0-9.-]* (32 chars) — bounds
            // event_type pollution from arbitrary clients.
            if (!req.name.matches(Regex("^[a-z][a-z0-9._-]{0,31}$"))) {
                call.respond(HttpStatusCode.BadRequest, "name must match [a-z][a-z0-9._-]{0,31}")
                return@csrfProtect
            }
            runCatching {
                TutorEventLog.GLOBAL.append(TutorEvent(
                    event_type = "ui_telemetry",
                    event_id = java.util.UUID.randomUUID().toString().replace("-", ""),
                    ts_utc = Instant.now().toString(),
                    task_id = null,
                    session_id = sid,
                    prompt_template_id = req.name,
                    system_prompt_sha256 = null,
                    retrieved_context_summary = null,
                    llm_input_full = null,
                    llm_input_redacted = null,
                    llm_output_full = rawBody,
                    model_resolved = null,
                    tokens_in = null,
                    tokens_out = null,
                    latency_ms = null,
                    status = "ok",
                ))
            }
            call.respond(HttpStatusCode.NoContent)
          }
        }

        // Layer B1 — effector dispatch route. Single mutating endpoint
        // for the chat surface; routes through EffectorDispatcher so
        // every dispatch goes preSeal → backend → postSeal/rollback +
        // audit. Backend selection (clipboard v0 vs daemon v1) is per
        // ApiDispatchRequest; daemon path requires JARVIS_DAEMON_URL +
        // JARVIS_DAEMON_SECRET env on the server (or reads them from
        // ProviderConfig in a future commit).
        post("/api/v1/effector/dispatch") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                if (sid.isNullOrBlank()) { call.respond(HttpStatusCode.Unauthorized, "missing session"); return@csrfProtect }
                val userId = SessionRepo(ctx.db).findUserId(sid)
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }

                val req = try {
                    sensorJson.decodeFromString(ApiDispatchRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed request: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                val backendChoice = when (req.backend.uppercase()) {
                    "DAEMON" -> EffectorDispatcher.Backend.DAEMON
                    "CLIPBOARD" -> EffectorDispatcher.Backend.CLIPBOARD
                    else -> {
                        call.respond(HttpStatusCode.BadRequest, "backend must be DAEMON or CLIPBOARD")
                        return@csrfProtect
                    }
                }
                val daemonUrl = System.getenv("JARVIS_DAEMON_URL")?.trim().orEmpty()
                val daemonSecret = System.getenv("JARVIS_DAEMON_SECRET")?.trim().orEmpty()
                if (backendChoice == EffectorDispatcher.Backend.DAEMON &&
                    (daemonUrl.isBlank() || daemonSecret.isBlank())) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        "daemon not configured — set JARVIS_DAEMON_URL + JARVIS_DAEMON_SECRET",
                    )
                    return@csrfProtect
                }
                val daemonClient = DaemonClient(
                    baseUrl = daemonUrl.ifBlank { "http://127.0.0.1:7331" },
                    secret = daemonSecret.toByteArray(),
                )
                val dispatcher = EffectorDispatcher(
                    db = ctx.db,
                    nonces = NonceCache(),
                    shadowRoot = ctx.ledgerDir.resolve("shadow"),
                    ledgerDir = ctx.ledgerDir,
                    daemonClient = daemonClient,
                    backend = backendChoice,
                )
                try {
                    val applyReq = ApplyEditRequest(
                        taskId = req.taskId,
                        effectorId = req.effectorId,
                        targetUri = req.targetUri,
                        expectedDocVersion = req.expectedDocVersion,
                        edits = req.edits,
                        nonce = req.nonce,
                        grantId = req.grantId,
                    )
                    val out = dispatcher.dispatch(userId, applyReq, req.expectedDocVersion)
                    call.respond(out)
                } finally {
                    daemonClient.close()
                }
            }
        }

        // Layer B1 — trust grant CRUD for /settings/trust UI.
        get("/api/v1/grants") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val grants = TrustGrantRepo(ctx.db).listForUser(userId).map { g ->
                ApiGrantView(
                    id = g.grantId, scope = g.scope, ops = g.ops.map { it.name },
                    expiresAt = g.expiresAt.toString(),
                    callsUsed = g.callsUsed, maxCalls = g.maxCalls,
                    revokedAt = g.revokedAt?.toString(),
                )
            }
            call.respond(ApiGrantsList(grants))
        }
        post("/api/v1/grants") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiCreateGrantRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                // Council fix: per-user grant-creation rate limit.
                // 5 grants per hour caps prompt-injection-driven grant
                // explosion without blocking legit power use.
                val recent = TrustGrantRepo(ctx.db)
                    .grantsCreatedSince(userId, Instant.now().minusSeconds(3600))
                if (recent >= 5) {
                    call.respond(HttpStatusCode.TooManyRequests,
                        "grant rate limit: 5/hour exceeded (have $recent)")
                    return@csrfProtect
                }
                val ttlSeconds = req.ttlSeconds.coerceIn(60L, 8 * 3600L)  // 1 min .. 8h cap
                val grant = TrustGrant(
                    grantId = jarvis.tutor.TutorTypes.ulid(),
                    userId = userId,
                    scope = req.scope,
                    ops = req.ops.mapNotNull {
                        runCatching { EffectorType.valueOf(it.uppercase()) }.getOrNull()
                    }.toSet().ifEmpty { setOf(EffectorType.APPLY_EDIT) },
                    expiresAt = Instant.now().plusSeconds(ttlSeconds),
                    maxCalls = req.maxCalls.coerceIn(1, 1000),
                    callsUsed = 0,
                    grantedFrom = GrantSource.UI,
                    revokedAt = null,
                    createdAt = Instant.now(),
                )
                TrustGrantRepo(ctx.db).insert(grant)
                call.respond(HttpStatusCode.Created, ApiGrantView(
                    id = grant.grantId, scope = grant.scope, ops = grant.ops.map { it.name },
                    expiresAt = grant.expiresAt.toString(),
                    callsUsed = 0, maxCalls = grant.maxCalls, revokedAt = null,
                ))
            }
        }
        // Stage F — multi-channel chat gateway. Forwards Telegram /
        // Signal / iMessage etc inbound messages through the tutor
        // chat path with hard-gated read-only tool surface. Default-
        // OFF behind JARVIS_GATEWAY_ENABLED + secret token check.
        post("/api/v1/gateway/inbound") {
            // Council R2 fix: capture RAW BYTES (NOT receiveText().toByteArray)
            // so HMAC body-binding survives non-UTF-8 payloads — the
            // replacement-char roundtrip would silently invalidate
            // signatures. call.receive<ByteArray>() bypasses
            // ContentNegotiation (no Kotlinx ByteArray serializer
            // registered) and gives raw bytes off the wire.
            val rawBytes = call.receive<ByteArray>()
            val req = try {
                sensorJson.decodeFromString(
                    ApiGatewayInboundRequest.serializer(),
                    rawBytes.toString(Charsets.UTF_8),
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                return@post
            }
            // HMAC-only — bearer fallback removed (council R2 downgrade
            // attack fix). Producers MUST send X-Jarvis-Hmac +
            // X-Jarvis-Timestamp + X-Jarvis-Nonce.
            val hmac = jarvis.tutor.GatewayInbound.HmacHeaders(
                method = "POST",
                path = "/api/v1/gateway/inbound",
                timestamp = call.request.headers["X-Jarvis-Timestamp"],
                nonce = call.request.headers["X-Jarvis-Nonce"],
                signature = call.request.headers["X-Jarvis-Hmac"],
                body = rawBytes,
            )
            val pre = jarvis.tutor.GatewayInbound.preflight(
                channel = req.channel,
                fromUser = req.fromUser,
                hmacHeaders = hmac,
            )
            if (pre is jarvis.tutor.GatewayInbound.Result.Err) {
                call.respond(HttpStatusCode(pre.httpStatus, "Gateway"), pre.reason)
                return@post
            }
            val source = (pre as jarvis.tutor.GatewayInbound.Result.Ok).source
            // Run through JarvisToolset with hard read-only allowlist.
            val gatewaySpec = jarvis.tutor.SkillSpec(
                name = "_gateway",
                description = "synthetic gateway-context spec",
                triggers = emptyList(),
                toolAllowlist = jarvis.tutor.GatewayInbound.GATEWAY_TOOL_ALLOWLIST.toList(),
                systemPromptBody = "You are Jarvis answering a $source message from ${req.fromUser}. " +
                    "Effectors disabled — read-only tools only. Reply concisely.",
                sourcePath = "(synthetic)",
            )
            val systemPrompt = "Source: $source\nUser: ${req.fromUser}\n\n${gatewaySpec.systemPromptBody}"
            val text = try {
                jarvis.tutor.JarvisToolset().use { ts ->
                    val r = ts.chat(systemPrompt, req.text)
                    r.text
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadGateway, "LLM failed: ${e.message?.take(200)}")
                return@post
            }
            call.respond(ApiGatewayInboundResponse(text = text, source = source))
        }

        // Per-task PDF — workspace embed targets this URL. Resolves to
        // <ledgerDir>/task-pdfs/<taskId>.pdf if present; 404 otherwise
        // (PdfPane shows friendly fallback). Drop a PDF at that path
        // to wire it up.
        get("/api/v1/tasks/{id}/pdf") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val id = call.parameters["id"]?.takeIf { it.isNotBlank() }
                ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@get }
            // Path-sanitize: id is ULID-shaped (Crockford base32, 26 chars).
            // Reject anything else so we don't traverse out of task-pdfs/.
            if (!id.matches(Regex("^[0-9A-Za-z]{1,32}$"))) {
                call.respond(HttpStatusCode.BadRequest, "malformed id")
                return@get
            }
            // Verify task belongs to caller.
            val task = jarvis.tutor.TaskRepo(ctx.db).findById(id)
            if (task == null || task.userId != userId) {
                call.respond(HttpStatusCode.NotFound, "task not found")
                return@get
            }
            // 1. Primary: per-task copy at <ledgerDir>/task-pdfs/<id>.pdf
            val taskPdfPath = ctx.ledgerDir.resolve("task-pdfs").resolve("$id.pdf")
            if (java.nio.file.Files.exists(taskPdfPath)) {
                call.respondBytes(
                    bytes = java.nio.file.Files.readAllBytes(taskPdfPath),
                    contentType = io.ktor.http.ContentType.Application.Pdf,
                )
                return@get
            }
            // 2. Fallback: task.problemRef.path under archival root.
            //    Presets pre-fill this with a real path so workspaces
            //    open with content instantly.
            val refPath = task.problemRef.path.trim()
            if (refPath.isNotEmpty() && refPath.endsWith(".pdf")) {
                // Path-sanitize: must resolve UNDER archivalDir (no ../).
                val archivalRoot = jarvis.Config.archivalDir.toAbsolutePath().normalize()
                val resolved = archivalRoot.resolve(refPath).normalize().toAbsolutePath()
                if (resolved.startsWith(archivalRoot) && java.nio.file.Files.exists(resolved)) {
                    call.respondBytes(
                        bytes = java.nio.file.Files.readAllBytes(resolved),
                        contentType = io.ktor.http.ContentType.Application.Pdf,
                    )
                    return@get
                }
            }
            call.respond(HttpStatusCode.NotFound,
                "no PDF for $id — drop one at ${taskPdfPath} OR set task.problemRef.path " +
                    "to a real archival/*.pdf at task creation")
        }

        // Companion upload endpoint for the per-task PDF viewer. Frontend
        // POSTs the raw PDF bytes (Content-Type: application/pdf) which we
        // store at <ledgerDir>/task-pdfs/<id>.pdf — exactly where the GET
        // handler above looks first. Limits + magic-byte check guard the
        // disk so a 401/403 client can't write garbage.
        put("/api/v1/tasks/{id}/pdf") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@put }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val id = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                if (!id.matches(Regex("^[0-9A-Za-z]{1,32}$"))) {
                    call.respond(HttpStatusCode.BadRequest, "malformed id"); return@csrfProtect
                }
                val task = TaskRepo(ctx.db).findById(id)
                if (task == null || task.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
                }
                val bytes = call.receive<ByteArray>()
                if (bytes.size < 5 || !(bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
                        bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte() && bytes[4] == '-'.code.toByte())) {
                    call.respond(HttpStatusCode.BadRequest, "not a PDF (missing %PDF- magic)")
                    return@csrfProtect
                }
                if (bytes.size > 50 * 1024 * 1024) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "max 50 MB"); return@csrfProtect
                }
                val dir = ctx.ledgerDir.resolve("task-pdfs")
                java.nio.file.Files.createDirectories(dir)
                val target = dir.resolve("$id.pdf")
                java.nio.file.Files.write(target, bytes)
                call.respond(HttpStatusCode.OK, ApiPdfUploadReply(bytes = bytes.size))
            }
        }

        // Phase 3.4 — task scratchpad text persistence. Plain text only,
        // bound to a single task; the SPA debounces PUTs while the user
        // types and rehydrates from GET on workspace load.
        get("/api/v1/tasks/{id}/scratchpad") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val taskId = call.parameters["id"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@get }
            val task = TaskRepo(ctx.db).findById(taskId)
            if (task == null || task.userId != userId) {
                call.respond(HttpStatusCode.NotFound, "task not found"); return@get
            }
            call.respond(HttpStatusCode.OK, ApiScratchpadView(text = task.scratchpadText ?: ""))
        }

        put("/api/v1/tasks/{id}/scratchpad") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@put }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = TaskRepo(ctx.db).findById(taskId)
                if (task == null || task.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
                }
                val req = try {
                    sensorJson.decodeFromString(ApiScratchpadView.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                if (req.text.length > 50_000) {
                    call.respond(HttpStatusCode.BadRequest, "scratchpad too large (max 50000 chars)")
                    return@csrfProtect
                }
                val ok = TaskRepo(ctx.db).updateScratchpadText(taskId, req.text)
                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
                }
                call.respond(HttpStatusCode.OK, ApiScratchpadView(text = req.text))
            }
        }

        // Layer B / task-context V0 — task CRUD so user can seed real
        // PS Tema A / PA Tema 5 / etc. Without this, TaskHeaderBuilder
        // has nothing to look up. Minimal shape: subject + title +
        // deadline. ContentRef slots are placeholders so the schema
        // is satisfied; real content-ref wiring lands when archival
        // ↔ task linkage UI ships.
        get("/api/v1/tasks") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val tasks = TaskRepo(ctx.db).listForUser(userId).map { t ->
                ApiTaskView(
                    id = t.id, subject = t.subject, title = t.title,
                    deadline = t.deadline.toString(),
                    status = t.status.name,
                )
            }
            call.respond(ApiTasksList(tasks))
        }
        // Phase 6.3a — single-task fetch with materialPaths surfaced.
        // The earlier-declared /tasks/{id}/pdf + /tasks/{id}/scratchpad
        // routes coexist via Ktor's tree router (it picks the longer
        // segment-match regardless of declaration order).
        get("/api/v1/tasks/{id}") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val taskId = call.parameters["id"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@get }
            val task = TaskRepo(ctx.db).findById(taskId)
            if (task == null || task.userId != userId) {
                call.respond(HttpStatusCode.NotFound, "task not found"); return@get
            }
            call.respond(HttpStatusCode.OK, ApiTaskDetailView(
                id = task.id, subject = task.subject, title = task.title,
                deadline = task.deadline.toString(), status = task.status.name,
                materialPaths = task.materialPaths,
            ))
        }
        // Slice 1.5 A1 — mark task SUBMITTED; validates ownership before flipping status.
        post("/api/v1/tasks/{id}/submit") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = TaskRepo(ctx.db).findById(taskId)
                    ?: run { call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect }
                if (task.userId != userId) {
                    call.respond(HttpStatusCode.Forbidden, "not your task"); return@csrfProtect
                }
                val now = java.time.Instant.now()
                try {
                    sensorJson.decodeFromString(ApiTaskSubmitRequest.serializer(), call.receiveText())
                } catch (_: Exception) { /* tolerate missing/malformed body */ }
                val ok = TaskRepo(ctx.db).updateStatus(taskId, jarvis.tutor.TaskStatus.SUBMITTED, now)
                if (!ok) {
                    call.respond(HttpStatusCode.InternalServerError, "status update failed"); return@csrfProtect
                }
                call.respond(HttpStatusCode.OK, ApiTaskSubmitReply(
                    taskId = taskId,
                    status = jarvis.tutor.TaskStatus.SUBMITTED.name,
                    submittedAt = now.toString(),
                ))
            }
        }
        // Slice 1.5 A2 — fetch cached task_prep for frontend bootstrap.
        // Returns 401 with no session, 404 when prep is not yet cached.
        get("/api/v1/tasks/{id}/prep") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@get }
            val task = TaskRepo(ctx.db).findById(taskId)
                ?: run { call.respond(HttpStatusCode.NotFound, "task not found"); return@get }
            if (task.userId != userId) {
                call.respond(HttpStatusCode.Forbidden, "not your task"); return@get
            }
            val prep = jarvis.tutor.TaskPrepRepo(ctx.db).findByTaskId(taskId)
                ?: run { call.respond(HttpStatusCode.NotFound, "no prep cached"); return@get }
            call.respond(HttpStatusCode.OK, ApiTaskPrepReply(
                taskId = prep.taskId,
                generatedAt = prep.generatedAt.toString(),
                version = prep.version,
                problemsJson = prep.problemsJson,
                drillsJson = prep.drillsJson,
                railJson = prep.railJson,
            ))
        }
        // Audit MED closer: per-task delete so the user can prune dupes
        // from the UI instead of needing direct sqlite access. Cascades
        // any detected_task_mapping rows pointing at this task; refuses
        // when the task has knowledge_gaps or fsrs_cards back-pointing
        // to preserve audit history.
        delete("/api/v1/tasks/{id}") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@delete }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = TaskRepo(ctx.db).findById(taskId)
                if (task == null || task.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
                }
                val gaps = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir).listForTask(userId, taskId)
                if (gaps.isNotEmpty()) {
                    call.respond(HttpStatusCode.Conflict,
                        "task has ${gaps.size} knowledge gaps; resolve or detach first")
                    return@csrfProtect
                }
                val tid: String = taskId
                transaction(ctx.db) {
                    jarvis.tutor.taskdetect.DetectedTaskMappingTable.deleteWhere {
                        jarvis.tutor.taskdetect.DetectedTaskMappingTable.taskId eq tid
                    }
                    TasksTable.deleteWhere { TasksTable.id eq tid }
                }
                call.respond(HttpStatusCode.OK, ApiTaskDeleteReply(taskId = taskId))
            }
        }

        post("/api/v1/tasks") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiCreateTaskRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                if (req.subject.isBlank() || req.title.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "subject + title required")
                    return@csrfProtect
                }
                val deadline = try {
                    Instant.parse(req.deadline)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "deadline must be ISO-8601: ${e.message?.take(80)}")
                    return@csrfProtect
                }
                val subjectTrim = req.subject.trim().take(32)
                val titleTrim = req.title.trim().take(256)
                // Phase-1 idempotency band-aid: spec §1.3. Replaced by TaskDetector
                // dedup in Phase 6. Match key is (subject, title) per user — same
                // subject+title from this user is interpreted as a re-click of an
                // in-flight POST, not a deliberate second task. Not race-free —
                // two POSTs interleaved can both miss the lookup and both INSERT;
                // Phase 6 closes that with TaskDetector + a unique index.
                val existing = TaskRepo(ctx.db).listForUser(userId)
                    .firstOrNull { it.subject == subjectTrim && it.title == titleTrim }
                if (existing != null) {
                    call.respond(HttpStatusCode.OK, ApiTaskView(
                        id = existing.id, subject = existing.subject, title = existing.title,
                        deadline = existing.deadline.toString(), status = existing.status.name,
                    ))
                    return@csrfProtect
                }
                val id = jarvis.tutor.TutorTypes.ulid()
                val now = Instant.now()
                // Phase 6.3a — auto-attach reference materials at INSERT.
                // HybridRetriever.search runs lexical+optional-semantic over
                // archivalDir; we keep the top-5 distinct hit ids (relative
                // archival paths) so the workspace can show a "Reference
                // materials" rail without a per-task scrape. Empty corpus
                // (test harness) → empty list, NOT a failure.
                val materialPaths: List<String> = try {
                    kotlinx.coroutines.runBlocking {
                        jarvis.HybridRetriever.search(titleTrim, k = 5, semanticEmbed = null)
                    }.map { it.id }.distinct().take(5)
                } catch (e: Exception) { emptyList() }
                try {
                    TaskRepo(ctx.db).insert(Task(
                        id = id, userId = userId,
                        subject = subjectTrim,
                        title = titleTrim,
                        deadline = deadline,
                        problemRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.problemPath, sha = "pending"),
                        conceptRefs = emptyList(),
                        rubricRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.rubricPath.ifBlank { req.problemPath }, sha = "pending"),
                        scratchpad = null, submission = null, grade = null,
                        cardRefs = emptyList(),
                        status = TaskStatus.ACTIVE,
                        createdAt = now, updatedAt = now,
                        materialPaths = materialPaths,
                    ))
                } catch (e: Exception) {
                    // Race-loser: idx_tasks_user_subject_title unique
                    // constraint kicked in. Re-fetch + return the row
                    // that won so caller treats it as idempotent.
                    val winner = TaskRepo(ctx.db).listForUser(userId)
                        .firstOrNull { it.subject == subjectTrim && it.title == titleTrim }
                    if (winner != null) {
                        call.respond(HttpStatusCode.OK, ApiTaskView(
                            id = winner.id, subject = winner.subject, title = winner.title,
                            deadline = winner.deadline.toString(), status = winner.status.name,
                        ))
                        return@csrfProtect
                    }
                    call.respond(HttpStatusCode.InternalServerError,
                        "task insert failed: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                jarvis.tutor.StateVersion.bump()
                call.respond(HttpStatusCode.Created, ApiTaskView(
                    id = id, subject = subjectTrim, title = titleTrim,
                    deadline = deadline.toString(), status = TaskStatus.ACTIVE.name,
                ))
            }
        }

        post("/api/v1/grants/{id}/revoke") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val id = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val grant = TrustGrantRepo(ctx.db).findActive(id, Instant.now())
                if (grant == null || grant.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "grant not found"); return@csrfProtect
                }
                TrustGrantRepo(ctx.db).revoke(id, Instant.now())
                call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/api/v1/gap") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiCreateGapRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                if (req.topic.isBlank() || req.topic.length > 256) {
                    call.respond(HttpStatusCode.BadRequest, "topic 1-256 chars")
                    return@csrfProtect
                }
                val typeEnum = try { jarvis.tutor.GapType.valueOf(req.type) } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "type must be one of GapType"); return@csrfProtect
                }
                val triggerEnum = try { jarvis.tutor.GapTrigger.valueOf(req.trigger) } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "trigger must be one of GapTrigger"); return@csrfProtect
                }
                val now = Instant.now()
                val gap = jarvis.tutor.KnowledgeGap(
                    id = jarvis.tutor.TutorTypes.ulid(), userId = userId, taskId = req.taskId,
                    topic = req.topic.take(256), language = req.language, type = typeEnum,
                    trigger = triggerEnum, filledAt = now, source = jarvis.tutor.GapSource.LLM_GROUNDED,
                    content = req.content, exampleCode = req.exampleCode, sourceCitation = req.sourceCitation,
                    resolvedBy = null, reusedCount = 0, fsrsCardId = null,
                )
                val gapRepo = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir)
                // Phase 7.3: cross-task similarity reuse. If a similar gap exists
                // in a DIFFERENT task at or above threshold, bump reusedCount on
                // the existing gap and return it instead of creating a new row.
                // Same-task duplicates already collapse via upsertByTriple below.
                val simThreshold = System.getenv("JARVIS_GAP_SIMILARITY_THRESHOLD")?.toDoubleOrNull() ?: 0.75
                val similar = gapRepo.findSimilar(userId, req.topic, threshold = simThreshold, k = 1).firstOrNull()
                if (similar != null && similar.first.taskId != req.taskId) {
                    gapRepo.incrementReused(similar.first.id)
                    val refreshed = gapRepo.findById(similar.first.id)
                    call.respond(HttpStatusCode.OK, ApiGapView(
                        id = similar.first.id, taskId = refreshed?.taskId,
                        topic = refreshed?.topic ?: req.topic,
                        language = refreshed?.language,
                        type = refreshed?.type?.name ?: req.type,
                        trigger = refreshed?.trigger?.name ?: req.trigger,
                        content = refreshed?.content ?: req.content,
                        exampleCode = refreshed?.exampleCode,
                        sourceCitation = refreshed?.sourceCitation,
                        resolvedBy = refreshed?.resolvedBy?.name,
                        reusedCount = refreshed?.reusedCount ?: 1,
                    ))
                    return@csrfProtect
                }
                val id = gapRepo.upsertByTriple(gap, taskId = req.taskId, content = req.content,
                    exampleCode = req.exampleCode, sourceCitation = req.sourceCitation, now = now)
                val saved = gapRepo.findById(id)
                call.respond(HttpStatusCode.Created, ApiGapView(
                    id = id, taskId = saved?.taskId, topic = saved?.topic ?: req.topic,
                    language = saved?.language, type = saved?.type?.name ?: req.type,
                    trigger = saved?.trigger?.name ?: req.trigger,
                    content = saved?.content ?: req.content,
                    exampleCode = saved?.exampleCode, sourceCitation = saved?.sourceCitation,
                    resolvedBy = saved?.resolvedBy?.name,
                    reusedCount = saved?.reusedCount ?: 0,
                ))
            }
        }

        post("/api/v1/gap/{id}/search-docs") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val gapId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val repo = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir)
                val gap = repo.findById(gapId)
                if (gap == null || gap.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "gap not found"); return@csrfProtect
                }
                val hits = try {
                    kotlinx.coroutines.runBlocking {
                        jarvis.HybridRetriever.search(gap.topic, k = 6, semanticEmbed = null)
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                val results = hits.take(6).map {
                    ApiSearchDocResult(filename = it.id, snippet = it.snippet, lineRef = null)
                }
                call.respond(HttpStatusCode.OK, ApiSearchDocsReply(results = results))
            }
        }

        get("/api/v1/last-task") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val taskId = call.request.cookies["jarvis_last_task"]
            val valid = taskId?.let { TaskRepo(ctx.db).findById(it)?.takeIf { t -> t.userId == userId } }
            call.respond(HttpStatusCode.OK, ApiLastTaskReply(taskId = valid?.id))
        }

        post("/api/v1/last-task") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiLastTaskReply.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                val taskId = req.taskId ?: ""
                val valid = if (taskId.isNotBlank())
                    TaskRepo(ctx.db).findById(taskId)?.takeIf { t -> t.userId == userId } else null
                if (valid == null) {
                    call.respond(HttpStatusCode.BadRequest, "taskId not found"); return@csrfProtect
                }
                call.response.cookies.append(
                    Cookie(
                        name = "jarvis_last_task",
                        value = valid.id,
                        httpOnly = true,
                        secure = true,
                        extensions = mapOf("SameSite" to "Strict"),
                        path = "/",
                        maxAge = 60 * 60 * 24 * 30,
                    ),
                )
                call.respond(HttpStatusCode.OK, ApiLastTaskReply(taskId = valid.id))
            }
        }

        // Renamed from /api/v1/gws/status (Phase I). Reports Google OAuth token
        // state so the user can diagnose calendar/drive/gmail availability from
        // the Tutor settings page without SSHing into the VPS.
        get("/api/v1/google/status") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val tokenPath = java.nio.file.Path.of(
                System.getenv("GOOGLE_TOKEN_PATH") ?: "/opt/jarvis/data/google-token.json"
            )
            val store = jarvis.tutor.google.TokenStore(tokenPath)
            val token = store.load()
            val enabled = System.getenv("GWS_ENABLED")?.lowercase()?.let {
                it == "1" || it == "true" || it == "yes"
            } ?: false
            call.respond(HttpStatusCode.OK, ApiGoogleStatusReply(
                enabled = enabled,
                tokenPresent = token != null,
                tokenExpiresAt = token?.expiresAt,
                tokenRefreshable = token?.refreshToken?.isNotBlank() == true,
            ))
        }

        post("/api/v1/task-detect/run") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val sources: List<jarvis.tutor.taskdetect.TaskDetector> = buildList {
                    add(jarvis.tutor.taskdetect.ManualSource(ctx.db, userId))
                    add(jarvis.tutor.taskdetect.FiimaterialsSource(ctx.ledgerDir.resolve("archival/_extras")))
                    addAll(jarvis.tutor.taskdetect.IcsScraper.fromEnv())
                }
                val agg = jarvis.tutor.taskdetect.TaskDetectorAggregator(sources)
                val detected = kotlinx.coroutines.runBlocking { agg.discoverAll() }
                val repo = jarvis.tutor.taskdetect.DetectedTaskRepo(ctx.db)
                val taskRepo = TaskRepo(ctx.db)
                var inserted = 0; var existing = 0
                for (d in detected) {
                    val mappedId = repo.findExisting(d.sourceId, d.externalId)
                    if (mappedId != null) {
                        repo.upsertMapping(d.sourceId, d.externalId, mappedId, userId)
                        existing++
                        continue
                    }
                    // Detect-by-mapping missed (different sourceId/externalId in
                    // a prior run, OR manual entry) but a row with the same
                    // (userId, subject, title) triple already exists. Reuse it
                    // and adopt this detection's mapping — otherwise the
                    // UNIQUE constraint at the storage layer 500s the whole run.
                    val subjectCap = d.subject.take(32)
                    val titleCap = d.title.take(256)
                    val byTriple = taskRepo.findByUserSubjectTitle(userId, subjectCap, titleCap)
                    if (byTriple != null) {
                        repo.upsertMapping(d.sourceId, d.externalId, byTriple.id, userId)
                        existing++
                        continue
                    }
                    val newId = jarvis.tutor.TutorTypes.ulid()
                    val now = Instant.now()
                    taskRepo.insert(jarvis.tutor.Task(
                        id = newId, userId = userId,
                        subject = subjectCap, title = titleCap,
                        deadline = d.deadline,
                        problemRef = jarvis.tutor.ContentRef(repo = "detected", path = d.problemPath ?: "", sha = "pending"),
                        conceptRefs = emptyList(),
                        rubricRef = jarvis.tutor.ContentRef(repo = "detected", path = d.problemPath ?: "", sha = "pending"),
                        scratchpad = null, submission = null, grade = null,
                        cardRefs = emptyList(),
                        status = jarvis.tutor.TaskStatus.ACTIVE,
                        createdAt = now, updatedAt = now,
                    ))
                    repo.upsertMapping(d.sourceId, d.externalId, newId, userId)
                    inserted++
                }
                call.respond(HttpStatusCode.OK, ApiTaskDetectReply(inserted = inserted, existing = existing, total = detected.size))
            }
        }

        // Phase B5: re-run PDF problem extraction for a task and cache the result.
        post("/api/v1/task/{id}/reprep") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = TaskRepo(ctx.db).findById(taskId)
                if (task == null || task.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
                }
                val pdfPath = jarvis.Config.archivalDir
                    .resolve(task.problemRef.path)
                    .normalize()
                    .toAbsolutePath()
                val freshProblems = try {
                    reprepExtractorLlmFactory().use { llm ->
                        kotlinx.coroutines.runBlocking {
                            reprepExtractorFn(pdfPath, llm)
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, "LLM error: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                // C5: populate conceptRefs via HybridRetriever (lexical; semantic skipped — no API key in prod)
                val conceptRefs = mutableListOf<jarvis.tutor.ContentRef>()
                for (p in freshProblems) {
                    val hits = kotlinx.coroutines.runBlocking {
                        jarvis.HybridRetriever.search(p.statement, k = 3, semanticEmbed = null)
                    }
                    conceptRefs += hits.map { hit ->
                        // Normalize path separators to forward-slash for cross-platform consistency
                        jarvis.tutor.ContentRef(repo = "corpus", path = hit.id.replace('\\', '/'), sha = "")
                    }
                }
                // Subject-scope: if ≥1 hit matches _extras/<subject>/ keep only those; full-corpus fallback otherwise
                val subjectPrefix = "_extras/${task.subject}/"
                val subjectScoped = conceptRefs.filter { it.path.startsWith(subjectPrefix) }
                val finalConceptRefs = (if (subjectScoped.isNotEmpty()) subjectScoped else conceptRefs)
                    .distinctBy { it.path }
                jarvis.tutor.TaskRepo(ctx.db).updateConceptRefs(taskId, finalConceptRefs)

                val now = java.time.Instant.now()
                // E3 Task 12 guard: preserve any existing Problem with non-empty kcIds (merge by problemId).
                // Fresh LLM-extracted problems (empty kcIds) are merged in; authored/generated problems
                // (non-empty kcIds) survive. Existing drillsJson is preserved so authored/generated drills
                // are not clobbered.
                val prepRepo = jarvis.tutor.TaskPrepRepo(ctx.db)
                val existingPrep = prepRepo.findByTaskId(taskId)
                val preserved = (existingPrep?.problemsJson?.let {
                    try { jarvis.tutor.TutorTypes.tutorJson.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()), it)
                    } catch (_: Exception) { emptyList() }
                } ?: emptyList()).filter { it.kcIds.isNotEmpty() }
                val merged = (preserved + freshProblems).associateBy { it.problemId }.values.toList()
                val problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        jarvis.tutor.Problem.serializer()),
                    merged,
                )
                val railItems = jarvis.tutor.RailJsonBuilder.buildForTask(
                    ctx.db, taskId, userId,
                    jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir),
                )
                val railJsonStr = jarvis.tutor.RailJsonBuilder.toJsonArrayString(railItems)
                prepRepo.upsert(jarvis.tutor.TaskPrep(
                    taskId = taskId,
                    generatedAt = now,
                    version = 1,
                    problemsJson = problemsJson,
                    drillsJson = existingPrep?.drillsJson ?: "{}",
                    railJson = railJsonStr,
                ))
                call.respond(HttpStatusCode.OK, ApiTaskRepRepReply(
                    taskId = taskId,
                    problems = freshProblems.size,
                    generatedAt = now.toString(),
                ))
            }
        }

        // E2 Task 4b: persist authored, kcId-bearing Problems for a task so the
        // curate-tutor flow can populate the grade loop in production without an
        // LLM reprep round-trip.
        post("/api/v1/task/{id}/prep-authored") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = TaskRepo(ctx.db).findById(taskId)
                if (task == null || task.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
                }
                val body = try {
                    sensorJson.decodeFromString(ApiPrepAuthoredRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}"); return@csrfProtect
                }
                val now = java.time.Instant.now()
                val problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()),
                    body.problems,
                )
                val prepRepo = jarvis.tutor.TaskPrepRepo(ctx.db)
                val existing = prepRepo.findByTaskId(taskId)
                prepRepo.upsert(jarvis.tutor.TaskPrep(
                    taskId = taskId, generatedAt = now, version = body.version,
                    problemsJson = problemsJson,
                    drillsJson = existing?.drillsJson ?: "{}",
                    railJson = existing?.railJson ?: "[]",
                ))
                call.respond(HttpStatusCode.OK, ApiTaskRepRepReply(taskId, body.problems.size, now.toString()))
            }
        }

        // E3: generate fresh, safeguard-gated practice drills for a knowledge concept.
        // Reads existing problemsJson + drillsJson, adds/replaces by problemId, re-encodes
        // BOTH, upserts — never clobbers existing drills.
        post("/api/v1/task/{id}/generate-drills") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val task = TaskRepo(ctx.db).findById(taskId)
                if (task == null || task.userId != userId) { call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect }
                val body = try { sensorJson.decodeFromString(ApiGenerateDrillsRequest.serializer(), call.receiveText()) }
                    catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}"); return@csrfProtect }

                val kc = drillKcLookup(task.subject, body.kcId)
                    ?: run { call.respond(HttpStatusCode.NotFound, "kc not found: ${body.kcId}"); return@csrfProtect }
                val shape = body.shape ?: "fact-conceptual"
                val sources = kc.source.map { it.quote }

                val result = try {
                    drillGeneratorLlmFactory().use { gen ->
                        drillCriticLlmFactory().use { critic ->
                            kotlinx.coroutines.runBlocking {
                                val regular = jarvis.tutor.DrillGenerator.generate(kc, sources, shape, body.count, gen, critic)
                                // TASK P3-GHOST-FIELDS(b): ALSO run the far-transfer branch for any KC that authors a
                                // far_transfer_stem, so the CHANGE-3 field is WIRED into the production generate path
                                // (no silent half-ghost). A KC with no stem returns zero bundles + an explicit reject
                                // reason (no throw, no hallucinated stem) — the regular result is then returned as-is.
                                if (kc.far_transfer_stem?.isNotBlank() == true) {
                                    val ft = jarvis.tutor.DrillGenerator.farTransfer(kc, sources, gen, critic)
                                    jarvis.tutor.DrillGenerator.GenerateResult(
                                        bundles = regular.bundles + ft.bundles,
                                        rejectReasons = regular.rejectReasons + ft.rejectReasons,
                                    )
                                } else regular
                            }
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, "generation failed (relay/LLM): ${e.message?.take(160)}"); return@csrfProtect
                }

                // read-merge-write BOTH stores by problemId.
                val prepRepo = jarvis.tutor.TaskPrepRepo(ctx.db)
                val existing = prepRepo.findByTaskId(taskId)
                val existingProblems: List<jarvis.tutor.Problem> = if (existing?.problemsJson != null) {
                    try {
                        sensorJson.decodeFromString(
                            kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()),
                            existing.problemsJson
                        )
                    } catch (_: Exception) { emptyList<jarvis.tutor.Problem>() }
                } else emptyList<jarvis.tutor.Problem>()
                val existingDrillsRaw: Map<String, jarvis.tutor.DrillContentDto> = if (existing?.drillsJson != null) {
                    try {
                        sensorJson.decodeFromString(
                            kotlinx.serialization.builtins.MapSerializer(
                                kotlinx.serialization.serializer<String>(),
                                jarvis.tutor.DrillContentDto.serializer()
                            ),
                            existing.drillsJson
                        )
                    } catch (_: Exception) { emptyMap<String, jarvis.tutor.DrillContentDto>() }
                } else emptyMap<String, jarvis.tutor.DrillContentDto>()
                val problems = existingProblems.associateBy { it.problemId }.toMutableMap()
                val drills = existingDrillsRaw.toMutableMap()
                for (b in result.bundles) { problems[b.problem.problemId] = b.problem; drills[b.problem.problemId] = b.content }

                val now = java.time.Instant.now()
                prepRepo.upsert(jarvis.tutor.TaskPrep(
                    taskId = taskId, generatedAt = now, version = existing?.version ?: 1,
                    problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()),
                        problems.values.toList(),
                    ),
                    drillsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                        kotlinx.serialization.builtins.MapSerializer(
                            kotlinx.serialization.serializer<String>(),
                            jarvis.tutor.DrillContentDto.serializer()
                        ),
                        drills as Map<String, jarvis.tutor.DrillContentDto>,
                    ),
                    railJson = existing?.railJson ?: "[]",
                ))
                call.respond(HttpStatusCode.OK, ApiGenerateDrillsReply(
                    taskId = taskId,
                    accepted = result.bundles.map { AcceptedDrill(it.problem.problemId, it.problem.shape ?: shape) },
                    rejectedCount = result.rejectReasons.size,
                    rejectReasons = result.rejectReasons,
                    criticUsed = "relay/claude",
                    generatedAt = now.toString(),
                ))
            }
        }

        // Phase 7.5 deferral closer: promote a knowledge gap to an FSRS card.
        // Idempotent — the gap row's fsrs_card_id is the source of truth.
        post("/api/v1/gap/{id}/promote") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val gapId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val gap = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir).findById(gapId)
                    ?: run { call.respond(HttpStatusCode.NotFound, "gap not found"); return@csrfProtect }
                if (gap.userId != userId) {
                    call.respond(HttpStatusCode.Forbidden, "gap belongs to a different user")
                    return@csrfProtect
                }
                val r = jarvis.tutor.GapPromotion.promote(ctx.db, ctx.ledgerDir, gapId)
                    ?: run { call.respond(HttpStatusCode.InternalServerError, "promotion failed"); return@csrfProtect }
                call.respond(HttpStatusCode.OK, ApiPromoteGapReply(
                    cardId = r.cardId, createdNew = r.createdNew,
                ))
            }
        }

        get("/api/v1/gaps") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val taskId = call.request.queryParameters["taskId"]
            val repo = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir)
            val gaps = if (taskId != null) repo.listForTask(userId, taskId) else repo.listForUser(userId)
            // S-24 fix: gap.taskId may point to a task that's since been deleted.
            // Null it out so the ledger UI renders the row as non-clickable
            // (KnowledgeLedger.tsx already gates the button on g.taskId != null)
            // instead of navigating to /?taskId=DELETED and 404ing on /prep.
            val knownTaskIds = TaskRepo(ctx.db).listForUser(userId).mapTo(mutableSetOf()) { it.id }
            call.respond(HttpStatusCode.OK, ApiGapsList(gaps.map { g ->
                ApiGapView(
                    id = g.id,
                    taskId = g.taskId?.takeIf { it in knownTaskIds },
                    topic = g.topic, language = g.language,
                    type = g.type.name, trigger = g.trigger.name,
                    content = g.content, exampleCode = g.exampleCode, sourceCitation = g.sourceCitation,
                    resolvedBy = g.resolvedBy?.name, reusedCount = g.reusedCount,
                    fsrsCardId = g.fsrsCardId,
                )
            }))
        }

        post("/api/v1/gap/{id}/status") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val cardId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiCardStatusRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                if (req.status.isBlank() || req.status.length > 32) {
                    call.respond(HttpStatusCode.BadRequest, "status must be 1-32 chars")
                    return@csrfProtect
                }
                val logId = jarvis.tutor.CardActionLogRepo(ctx.db).insert(userId, "GAP", cardId, req.status)
                // Phase 4: status → KnowledgeGap.resolvedBy if it parses as one.
                val resolved = try { jarvis.tutor.GapResolved.valueOf(req.status) } catch (e: Exception) { null }
                if (resolved != null) {
                    jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir).markResolved(cardId, resolved)
                }
                call.respond(HttpStatusCode.OK, ApiCardStatusReply(logId = logId))
            }
        }

        post("/api/v1/edit/{id}/status") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val cardId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiCardStatusRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                if (req.status.isBlank() || req.status.length > 32) {
                    call.respond(HttpStatusCode.BadRequest, "status must be 1-32 chars")
                    return@csrfProtect
                }
                val logId = jarvis.tutor.CardActionLogRepo(ctx.db).insert(userId, "EDIT", cardId, req.status)
                call.respond(HttpStatusCode.OK, ApiCardStatusReply(logId = logId))
            }
        }

        // Phase C1: inline-help Sidekick — takes a structured envelope describing
        // what the user is looking at (task, card, anchor paragraph, selection) and
        // returns an LLM reply scoped to that context.
        post("/api/v1/sidekick/ask") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val t0 = System.currentTimeMillis()
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                if (!call.aiLiteracyGate(userId)) return@csrfProtect
                val env = try {
                    sensorJson.decodeFromString(jarvis.tutor.SidekickEnvelope.serializer(), call.receiveText())
                } catch (e: Exception) {
                    // Student-stand-in Task 0.6: skip envelope on malformed JSON
                    // — no useful Surface X/Y signal in a 400 path, and we
                    // don't have an env object to populate task_id from.
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                if (env.userQuestion.isBlank()) {
                    // Same reasoning as malformed — 400 has no LLM signal.
                    call.respond(HttpStatusCode.BadRequest, "userQuestion required")
                    return@csrfProtect
                }
                // Student-stand-in Task 0.6: capture sidekick outcomes to the
                // tutor-event log. is_synthetic flips when X-Standin-Run:1 is
                // present so Surface Y runs partition cleanly. Unlike Task 0.5
                // (drill-grade redacts R-code), sidekick raw input is not
                // credential-grade — we log it whole in llm_input_full.
                val isSynthetic = call.request.headers["X-Standin-Run"] == "1"
                val sessionId: String = sid ?: "anon"
                val llmInputFull = "${env.selection ?: env.anchorText ?: ""}\n${env.userQuestion}"
                // Spec §3 STEP A — pre-fetch corpus material when selection is
                // a usable query. Mirrors RAG pre-retrieval pattern; closes
                // GAP-1 (chip-flow never triggered search_archival on its own).
                val selectionQuery = jarvis.tutor.SelectionQueryBuilder.build(env)
                // Drill-self-paste guardrail (council 2026-05-13 mitigation B):
                // if the user selected the drill statement itself, short-circuit
                // the LLM call and return a coaching reply that redirects them
                // to the drill answer textarea. Preserves Roediger/Karpicke
                // testing-effect by refusing to let the LLM clarify the drill
                // itself back at the user.
                if (selectionQuery.drillSelfPaste) {
                    val guardText = "That looks like the drill question itself — work it out in the answer textarea below and hit CHECK ANSWER. The sidekick is for clarifying specific concepts in the worked example or definition, not for solving the drill."
                    call.respond(HttpStatusCode.OK, ApiSidekickReply(
                        text = guardText,
                        model = "(drill-self-paste-guard)",
                        quotedContext = env.selection ?: env.anchorText?.take(160),
                        citations = emptyList(),
                    ))
                    // Student-stand-in Task 0.6: log the guard hit so Surface X
                    // can flag drill-self-paste rate per task (high rate = the
                    // drill statement is too long to teach against, or the UI
                    // chip-flow is mis-anchored).
                    runCatching {
                        val evt = TutorEvent(
                            event_type = "sidekick_ask",
                            event_id = java.util.UUID.randomUUID().toString().replace("-", ""),
                            ts_utc = Instant.now().toString(),
                            task_id = env.taskId,
                            session_id = sessionId,
                            prompt_template_id = "sidekick-v1",
                            // No LLM call → no real system prompt to hash. Use
                            // the template id as a stable identity so Surface X
                            // can still group guard events by prompt version.
                            system_prompt_sha256 = sha256Hex("sidekick-v1"),
                            retrieved_context_summary = emptyList(),
                            llm_input_full = llmInputFull,
                            llm_input_redacted = null,
                            llm_output_full = guardText,
                            model_resolved = "(drill-self-paste-guard)",
                            tokens_in = null,
                            tokens_out = null,
                            latency_ms = System.currentTimeMillis() - t0,
                            status = "guard",
                            is_synthetic = isSynthetic,
                        )
                        TutorEventLog.GLOBAL.append(evt)
                    }.onFailure { e ->
                        System.err.println("[sidekick-ask] guard envelope append failed: ${e.message?.take(160)}")
                    }
                    return@csrfProtect
                }
                val prefetchedHits: List<jarvis.HybridRetriever.HybridHit> = if (selectionQuery.shouldFetch) {
                    val subject = env.taskId
                        ?.let { jarvis.tutor.TaskRepo(ctx.db).findById(it)?.subject }
                        ?.takeIf { it.isNotBlank() }
                    try {
                        // Subject-scoped retrieval: walk only `_extras/$subject/` so
                        // we never pull in second-brain council transcripts or
                        // cross-subject hits. Mirrors MigrateConceptRefs.kt:65 —
                        // 2026-05-13 dogfood revealed entity-pass for "Laplace"
                        // returned second-brain/.claude/council-cache/* as top hit
                        // (more raw "laplace" mentions than any single _extras/PS
                        // course file), wiping the entire `kept=` set after the
                        // legacy post-hoc filter.
                        //
                        // Semantic disabled: VectorStore is wiki/conversation-seeded
                        // only (79 entries, 0 archival), so semantic hits are always
                        // `conversation (...)` ids that fail any path-based filter
                        // AND aren't cite-eligible. Saving the embed call also saves
                        // the OpenRouter quota when it matters most.
                        val subjectRoot = subject?.let {
                            jarvis.Config.archivalDir.resolve("_extras").resolve(it)
                        }
                        val raw = kotlinx.coroutines.runBlocking {
                            if (subjectRoot != null && java.nio.file.Files.isDirectory(subjectRoot)) {
                                jarvis.HybridRetriever.search(
                                    selectionQuery.text, k = 3,
                                    archivalRoot = subjectRoot, semanticEmbed = null,
                                )
                            } else {
                                jarvis.HybridRetriever.search(selectionQuery.text, k = 3, semanticEmbed = null)
                            }
                        }
                        // Spec §4.4 — normalize OS-native separators to '/' at the
                        // pre-fetch boundary. When we scoped to subjectRoot, also
                        // re-prefix `_extras/$subject/` so the prefetched_corpus
                        // block paths match what CitationExtractor expects when the
                        // LLM emits `(src: _extras/PS/...)` markers.
                        val normalized = raw.map { hit ->
                            val cleaned = hit.id.replace('\\', '/')
                            val prefixed = if (subjectRoot != null) {
                                "_extras/$subject/$cleaned"
                            } else cleaned
                            hit.copy(id = prefixed)
                        }
                        System.err.println("[sidekick prefetch] q='${selectionQuery.text.take(60)}' subject=$subject raw=${raw.size} kept=${normalized.size} rawIds=${normalized.take(5).joinToString("|") { "${it.source}:${it.id.take(60)}" }}")
                        normalized
                    } catch (e: Exception) {
                        // Spec §6 critical invariant — pre-fetch failure must
                        // degrade to empty hits, NEVER 500. Mirror the LLM
                        // exception handler's graceful pattern.
                        System.err.println("[sidekick prefetch] ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                        emptyList()
                    }
                } else emptyList()

                val systemContext = jarvis.tutor.SidekickContext.systemContext(env, prefetchedHits = prefetchedHits)
                var text: String
                var model: String
                var llmHits: List<jarvis.HybridRetriever.HybridHit> = emptyList()
                try {
                    jarvis.tutor.JarvisToolset().use { ts ->
                        val r = kotlinx.coroutines.runBlocking {
                            ts.chat(systemPrompt = systemContext, userText = env.userQuestion)
                        }
                        text = r.text
                        model = r.model
                        llmHits = r.hits
                    }
                } catch (e: Exception) {
                    text = "(LLM unavailable; rate-limited? ${e.message?.take(120) ?: ""})"
                    model = "(none)"
                }
                // Spec §3 STEP D — union pre-fetched ∪ LLM-fetched, dedupe by id.
                val unionedHits = (prefetchedHits + llmHits).distinctBy { it.id }
                val quoted = env.selection ?: env.anchorText?.take(160)
                val citations = jarvis.tutor.CitationExtractor.extract(text, unionedHits)
                call.respond(HttpStatusCode.OK, ApiSidekickReply(text = text, model = model, quotedContext = quoted, citations = citations))

                // Student-stand-in Task 0.6: log every LLM-touching sidekick
                // call (success and graceful-degraded). status=="error" when
                // model is "(none)" — i.e. JarvisToolset construction or
                // ts.chat() threw and we returned the rate-limited fallback.
                runCatching {
                    val ctxSummary = unionedHits.map { "${it.id}:${it.snippet.take(80)}" }
                    val evt = TutorEvent(
                        event_type = "sidekick_ask",
                        event_id = java.util.UUID.randomUUID().toString().replace("-", ""),
                        ts_utc = Instant.now().toString(),
                        task_id = env.taskId,
                        session_id = sessionId,
                        prompt_template_id = "sidekick-v1",
                        // Hash the REAL prompt text (not just the template id)
                        // so Surface X can detect when prefetched-corpus drift
                        // changes the prompt body even though template_id is
                        // unchanged. Matches the spec's "real prompt" note.
                        system_prompt_sha256 = sha256Hex(systemContext),
                        retrieved_context_summary = ctxSummary,
                        llm_input_full = llmInputFull,
                        llm_input_redacted = null,
                        llm_output_full = text,
                        model_resolved = model,
                        tokens_in = null,
                        tokens_out = null,
                        latency_ms = System.currentTimeMillis() - t0,
                        status = if (model == "(none)") "error" else "ok",
                        is_synthetic = isSynthetic,
                    )
                    TutorEventLog.GLOBAL.append(evt)
                }.onFailure { e ->
                    System.err.println("[sidekick-ask] envelope append failed: ${e.message?.take(160)}")
                }
            }
        }

        // Phase C5: FSRS due-card queue. Returns up to `limit` cards whose
        // dueAt <= now, ordered by dueAt ASC. Limit clamped 1..100, default 20.
        get("/api/v1/fsrs/due") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val cards = jarvis.tutor.FsrsDueQueue.due(ctx.db, userId, java.time.Instant.now(), limit)
            call.respond(HttpStatusCode.OK, ApiFsrsDueReply(cards = cards.map { c ->
                ApiFsrsCardView(
                    id = c.id, front = c.front, back = c.back,
                    sourceTaskId = if (c.source == jarvis.tutor.FsrsSource.GAP_PROMOTION) c.sourceRef else null,
                    difficulty = c.state.difficulty, stability = c.state.stability,
                    retrievability = c.state.retrievability,
                    dueAt = c.state.dueAt.toString(), lapses = c.state.lapses,
                )
            }))
        }

        // Phase C5: FSRS grade route. Accepts grade 1..4, updates card state
        // inline via Fsrs.update, returns new dueAt + updated D/S values.
        post("/api/v1/fsrs/{id}/grade") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val cardId = call.parameters["id"]
                    ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiFsrsGradeRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(120)}")
                    return@csrfProtect
                }
                if (req.grade !in 1..4) {
                    call.respond(HttpStatusCode.BadRequest, "grade must be 1..4"); return@csrfProtect
                }
                // Previously: findDueForUser(userId, Instant.MAX) — used as a hack
                // to bypass the dueAt filter and look up by id. SQLite's timestamp
                // serializer overflows on Instant.MAX → uncaught 500. Use findById.
                val card = jarvis.tutor.FsrsCardRepo(ctx.db).findById(cardId, userId)
                    ?: run { call.respond(HttpStatusCode.NotFound, "card not found"); return@csrfProtect }
                val now = java.time.Instant.now()
                val elapsed = java.time.Duration.between(card.state.lastReviewedAt, now).toMinutes() / (60.0 * 24.0)
                val next = jarvis.Fsrs.update(
                    jarvis.Fsrs.State(card.state.difficulty, card.state.stability),
                    req.grade, elapsed,
                )
                org.jetbrains.exposed.sql.transactions.transaction(ctx.db) {
                    jarvis.tutor.FsrsCardsTable.update({
                        jarvis.tutor.FsrsCardsTable.id eq cardId
                    }) {
                        it[jarvis.tutor.FsrsCardsTable.difficulty] = next.difficulty
                        it[jarvis.tutor.FsrsCardsTable.stability] = next.stability
                        it[jarvis.tutor.FsrsCardsTable.retrievability] = jarvis.Fsrs.retrievability(next.stability, 0.0)
                        it[jarvis.tutor.FsrsCardsTable.dueAt] = now.plus(java.time.Duration.ofMinutes((next.stability * 24 * 60).toLong()))
                        it[jarvis.tutor.FsrsCardsTable.lastReviewedAt] = now
                        it[jarvis.tutor.FsrsCardsTable.lapses] = card.state.lapses + (if (req.grade == 1) 1 else 0)
                    }
                }
                val newDue = now.plus(java.time.Duration.ofMinutes((next.stability * 24 * 60).toLong()))
                call.respond(HttpStatusCode.OK, ApiFsrsGradeReply(
                    cardId = cardId, nextDueAt = newDue.toString(),
                    newDifficulty = next.difficulty, newStability = next.stability,
                ))
            }
        }

        // Phase C5: FSRS forecast — counts cards due within tomorrow / 7 days / 30 days.
        get("/api/v1/fsrs/forecast") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val sid = call.request.cookies["jarvis_session"]
            val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
            val f = jarvis.tutor.FsrsDueQueue.forecast(ctx.db, userId, java.time.Instant.now())
            call.respond(HttpStatusCode.OK, ApiFsrsForecastReply(
                dueNow = f.dueNow, tomorrow = f.tomorrow, thisWeek = f.thisWeek, thisMonth = f.thisMonth,
            ))
        }

        // Phase C3: drill answer grader — delegates to DrillGrader.grade() via
        // OpenRouterChatLlm. Returns a structured rubric + misconception code.
        post("/api/v1/drill/grade") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val t0 = System.currentTimeMillis()
                val sid = call.request.cookies["jarvis_session"]
                val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                if (!call.aiLiteracyGate(userId)) return@csrfProtect
                val req = try {
                    sensorJson.decodeFromString(ApiDrillGradeRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }

                // E2: resolve the persisted Problem server-side — client conceptIds/canonicalAnswer NOT trusted.
                val serverProblem: jarvis.tutor.Problem? = run {
                    val prep = jarvis.tutor.TaskPrepRepo(ctx.db).findByTaskId(req.taskId) ?: return@run null
                    val problems = try {
                        sensorJson.decodeFromString(
                            kotlinx.serialization.builtins.ListSerializer(jarvis.tutor.Problem.serializer()),
                            prep.problemsJson,
                        )
                    } catch (e: Exception) {
                        System.err.println("[drill-grade] could not read problemsJson for task=${req.taskId}: ${e.message?.take(120)}")
                        emptyList()
                    }
                    problems.firstOrNull { it.problemId == req.problemId }
                }

                // Student-stand-in Task 0.5: capture every grade outcome to the
                // tutor-event log for downstream Surface X (curriculum
                // sufficiency) + Surface Y (synthetic learner) audits. We pick
                // the per-path status + reply, respond once, then append a
                // single envelope before returning. Envelope build uses
                // placeholders for fields DrillGrader does not yet expose
                // (model id, token counts, real system-prompt text).
                val isSynthetic = call.request.headers["X-Standin-Run"] == "1"
                val sessionId: String = sid ?: "anon"

                // ── Plan-6 Task 7 — grader-CHAIN integration. Resolve the routing chain for this
                //    problem and run its NON-LLM prefix FIRST. canonical (server-resolved Problem,
                //    client fallback) feeds the numeric-oracle leg; the request rubric items (label-only,
                //    no machine-checkable matchers on the dominant task_prep traffic) feed the rubric leg
                //    (it DEFERS them → LLM decides). When a non-LLM leg DECIDES we serve that verdict and
                //    NEVER construct an LLM; when all defer we fall through to the existing LLM path with
                //    decided_by="llm-judge" + the degraded RO copy (INV-6.3 non-vacuous on real traffic).
                val canonicalForChain = serverProblem?.canonicalAnswer ?: req.canonicalAnswer
                val chainKinds = jarvis.tutor.grader.GraderRouting.chainFor(
                    subject = null,
                    examLanguage = req.language,
                    archetypeClass = DrillGradeChain.archetypeClassFor(
                        language = req.language,
                        canonicalAnswer = canonicalForChain,
                        shape = serverProblem?.shape,
                        // A KC-bearing drill stays on the LLM served-teaching + atomic-recording path; only a
                        // bare numeric (no KC obligation) routes to the oracle-leads/LLM-bypass chain.
                        hasKcObligation = !serverProblem?.kcIds.isNullOrEmpty(),
                    ),
                )
                val chainInput = jarvis.tutor.grader.GradeInput(
                    problemStatement = req.problemStatement,
                    attempt = req.userAttempt,
                    expected = canonicalForChain,
                    rubricItems = req.rubricItems.orEmpty().map { label ->
                        // Request rubric items are LABEL-ONLY (no matcher) — the rubric leg defers them
                        // to the LLM pairing (the dominant prose/task_prep path; structural matchers ride
                        // only bank-problem rows, Task 8). Carrying them keeps the deferral honest.
                        jarvis.tutor.grader.RubricInput(id = label, label = label, points = 1.0)
                    },
                    // No runnable student source rides /drill/grade (the request carries the REFERENCE
                    // solution, not the student's program), so the execution leg always DEFERS here and
                    // its degradation surfaces honestly in degraded_legs_ro / decided_by.
                    source = null,
                    language = req.language,
                )
                val prefix = kotlinx.coroutines.runBlocking {
                    DrillGradeChain.runPrefix(chainKinds, chainInput)
                }

                // The chain's audit trail for the served reply. decided_by + item_verdicts + degraded copy
                // ride EVERY reply path below (the LLM `else` branch sets decided_by="llm-judge" itself).
                val chainDecided = prefix as? DrillGradeChain.PrefixResult.Decided
                val decidedByForReply: String? = chainDecided?.let { DrillGradeChain.wireId(it.decidedBy) }
                val itemVerdictsForReply: List<jarvis.tutor.ItemVerdict> = chainDecided?.itemVerdicts ?: emptyList()
                val chainDegradedRo: List<String> = when (prefix) {
                    is DrillGradeChain.PrefixResult.Decided -> prefix.degradedRo
                    is DrillGradeChain.PrefixResult.AllDeferred -> prefix.degradedRo
                }

                // Compute reply + status across all three code paths. A.2 plan:
                // grader now returns GradeAttempt carrying raw LLM output so
                // A.3 can populate envelope llm_output_raw_truncated below.
                //
                // Plan-6 Task 7 — when a NON-LLM chain leg DECIDED (oracle/execution/rubric), synthesize a
                // GradeAttempt from that verdict and NEVER construct an LLM (test (a): a throwing resolver
                // proves the oracle path never touches the LLM). The synthetic rubric ({"<leg>": correct})
                // makes the EXISTING deterministic GradeScoring layer record mastery exactly as it would
                // for an LLM grade — so a numeric-correct oracle decision still records, unchanged. Only
                // when every non-LLM leg defers do we resolve + call the LLM judge.
                val attempt: jarvis.tutor.GradeAttempt? = if (chainDecided != null) {
                    val legKey = DrillGradeChain.wireId(chainDecided.decidedBy).replace('-', '_')
                    jarvis.tutor.GradeAttempt(
                        parsed = jarvis.tutor.GradeResult(
                            correct = chainDecided.correct,
                            // Single-item rubric coherent with `correct` ⇒ GradeScoring.isConfident is true,
                            // so a confident chain verdict records mastery (or records a confident wrong=0).
                            rubric = mapOf(legKey to chainDecided.correct),
                            score = chainDecided.score,
                            misconception = null,
                            elaboratedFeedback = chainDecided.feedbackRo,
                        ),
                        rawOutput = "",
                        modelResolved = DrillGradeChain.wireId(chainDecided.decidedBy),
                    )
                } else try {
                    (drillGraderLlmResolver?.invoke(userId, ctx.db) ?: drillGraderLlmFactory()).use { llm ->
                        kotlinx.coroutines.runBlocking {
                            jarvis.tutor.DrillGrader.grade(
                                problemStatement = req.problemStatement,
                                userAttempt = req.userAttempt,
                                expectedHint = req.expectedAnswerHint,
                                llm = llm,
                                language = req.language,
                                referenceSolution = req.referenceSolution,
                                rubricItems = req.rubricItems,
                                prediction = req.prediction,
                                giveUp = req.giveUp,
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Graceful degraded path: 200 with "ungraded" body so the
                    // frontend treats transient LLM failures as "re-attempt"
                    // instead of a wiring error. Slice 1 spec §E error handling:
                    // "LLM grader 5xx / timeout → fall back ... tag attempt as
                    // `ungraded`. Don't auto-pass." Exception message captured
                    // separately so the degraded reply can still echo it.
                    System.err.println("[drill-grade] LLM call failed: ${e.message?.take(160)}")
                    null
                }

                // HTTP status for the single response below. The atomic-grade branch may flip this to
                // 409 (a targeted card is not ACTIVE — M-GATE-PATHS) or 500 (fail-loud on an in-txn
                // throw — the whole txn rolled back, B1). All other paths stay 200.
                var httpStatus = HttpStatusCode.OK
                val (reply, status) = try {
                when {
                    attempt == null -> {
                        ApiDrillGradeReply(
                            correct = false, score = 0.0, rubric = emptyMap(),
                            misconception = "UNGRADED",
                            elaboratedFeedback = "LLM unavailable. Please re-attempt or ask sidekick.",
                            confidence = "LOW", recorded = false, answerMatch = null,
                        ) to "error"
                    }
                    attempt.parsed == null -> {
                        // Grader returned malformed LLM output — A.3 will route
                        // attempt.rawOutput into envelope.llm_output_raw_truncated.
                        ApiDrillGradeReply(
                            correct = false, score = 0.0, rubric = emptyMap(),
                            misconception = "OTHER",
                            elaboratedFeedback = "LLM grader returned malformed output; please re-attempt or ask sidekick.",
                            confidence = "LOW", recorded = false, answerMatch = null,
                        ) to "parse_error"
                    }
                    else -> {
                        // E1 trustworthy-grader: the LLM emits rubric booleans +
                        // prose, but the DETERMINISTIC layer (GradeScoring) decides
                        // correctness, score, and confidence. The LLM's self-reported
                        // `score`/`correct` are never trusted. Only confident grades
                        // (rubric-coherent AND, if a canonical answer was supplied,
                        // agreeing with the deterministic match) record KC mastery.
                        //
                        // Phase-3 GROUP 2 — the ATOMIC GRADE (master §2.2 line 113; B1/B2/B3/H1/H4/
                        // M-GATE-PATHS/M-B1-CARD). EVERYTHING that touches an LLM (the grader, above)
                        // is ALREADY resolved OUTSIDE any txn (H4) — `attempt` is in hand here, no
                        // model call remains. We then: (1) faithful-gate each server-resolved KC via
                        // VerificationGate.gate (the D-RF2 admission gate — its frozen caller-set names
                        // "B1 upsert"; non-faithful ⇒ recorded=false, kc_quarantined=true, NO write);
                        // (2) 409 if a targeted ACTIVE-only card is not ACTIVE; (3) ONE transaction{}
                        // doing recordIn (+phase) + attempts + upsertRubricCriterion per gated KC, all
                        // or nothing (a throw anywhere rolls back everything — B1).
                        val g: GradeResult = attempt.parsed!!
                        val canonical = serverProblem?.canonicalAnswer ?: req.canonicalAnswer
                        val answerMatch: Boolean? = canonical?.let { GradeScoring.answerMatches(it, req.userAttempt) }
                        val rubricCorrect = GradeScoring.correctFromRubric(g.rubric)
                        val deterministicCorrect = answerMatch ?: rubricCorrect
                        val deterministicScore = GradeScoring.scoreFromRubric(g.rubric)
                        val coherent = GradeScoring.isConfident(g)
                        val answerAgrees = answerMatch == null || answerMatch == rubricCorrect
                        val confident = coherent && answerAgrees
                        val allKcs = serverProblem?.kcIds ?: emptyList()

                        // Phase-3 GROUP 7 (H15) — snapshot the PRIMARY graded KC's phase BEFORE recording,
                        // so next_phase_action can compare before→after (the recorded phase is read back
                        // after the txn). Primary = first server-resolved KC (the one the surfaces show).
                        val primaryKcId = allKcs.firstOrNull()
                        val phaseBefore: jarvis.tutor.Phase? = primaryKcId?.let { kcId ->
                            runCatching { jarvis.tutor.KcMasteryRepo(ctx.db).get(userId, kcId)?.phase }.getOrNull()
                        }

                        var recorded = false
                        var kcQuarantined = false
                        var replyStatus = "ok"

                        if (!confident) {
                            // LOW-confidence grade: show the verdict, record nothing (unchanged E1).
                            if (allKcs.isEmpty()) {
                                System.err.println("[drill-grade] confident grade for task=${req.taskId} problem=${req.problemId} but no server-side kcIds — mastery NOT recorded")
                            }
                        } else {
                            if (allKcs.isEmpty()) {
                                System.err.println("[drill-grade] confident grade for task=${req.taskId} problem=${req.problemId} but no server-side kcIds — mastery NOT recorded")
                            } else {
                                // (1) FAITHFUL-GATE each server-resolved KC. resolveStatus reads the B8
                                // table (D8/D1 fresh-faithful) + the gate's ALWAYS-ON OPEN-report-wrong
                                // refusal. A KC not in the content corpus (kc==null) can never be
                                // faithful ⇒ DENY. Gating is per-KC (1:N, each independent — M-B1-CARD).
                                val contentRepo = jarvis.content.ContentRepo(drillContentDir())
                                val faithfulKcs = allKcs.filter { kcId ->
                                    val contentKc = runCatching { jarvis.web.VerifyAdmin.findKc(contentRepo, kcId) }.getOrNull()
                                    if (contentKc == null) {
                                        false
                                    } else {
                                        val st = jarvis.web.VerifyAdmin.resolveStatus(ctx.db, kcId, contentKc)
                                        val openRw = jarvis.tutor.verify.ReportWrongQuery.hasOpenReportWrong(ctx.db, kcId)
                                        jarvis.tutor.verify.VerificationGate.gate(contentKc, st, openRw) ==
                                            jarvis.tutor.verify.GateDecision.ALLOW
                                    }
                                }
                                kcQuarantined = faithfulKcs.size < allKcs.size

                                // (2) 409-IF-NOT-ACTIVE (M-GATE-PATHS). A targeted RUBRIC_CRITERION card
                                // for a gated KC that exists but is not ACTIVE (QUARANTINED / PAUSED)
                                // refuses the grade entirely — 409, NO write. A non-existent card is fine
                                // (the upsert will INSERT it ACTIVE). Read-only pre-check, before the txn.
                                val notActive = faithfulKcs.firstOrNull { kcId ->
                                    val existing = jarvis.tutor.FsrsCardRepo(ctx.db)
                                        .findRubricCriterionCard(userId, kcId)
                                    existing != null && existing.status != jarvis.tutor.CardStatus.ACTIVE
                                }
                                if (notActive != null) {
                                    httpStatus = HttpStatusCode.Conflict
                                    replyStatus = "conflict"
                                } else if (faithfulKcs.isNotEmpty()) {
                                    // (3) THE SINGLE ATOMIC TXN (B1). recordIn (+phase, B3) + attempts
                                    // (H1) + upsertRubricCriterion (B2) per gated KC. A throw anywhere
                                    // inside rolls back ALL — no orphaned mastery without its card/attempt.
                                    val now = Instant.now()
                                    val masteryRepo = jarvis.tutor.KcMasteryRepo(ctx.db)
                                    val cardRepo = jarvis.tutor.FsrsCardRepo(ctx.db)
                                    val initial = jarvis.Fsrs.initial(if (deterministicCorrect) 3 else 2)
                                    val front = serverProblem?.statement?.takeIf { it.isNotBlank() }
                                        ?: req.problemStatement
                                    val back = canonical ?: ""
                                    try {
                                        org.jetbrains.exposed.sql.transactions.transaction(ctx.db) {
                                            for (kcId in faithfulKcs) {
                                                // recordIn — EWMA + phase, in THIS txn (B3).
                                                val m = masteryRepo.recordIn(this, userId, kcId, deterministicScore, now)
                                                // attempts — the graded attempt (H1).
                                                AttemptsTable.insert {
                                                    it[id] = jarvis.tutor.TutorTypes.ulid()
                                                    it[AttemptsTable.userId] = userId
                                                    it[AttemptsTable.kcId] = kcId
                                                    it[taskId] = req.taskId
                                                    it[problemId] = req.problemId
                                                    it[phase] = m.phase?.name ?: jarvis.tutor.Phase.intro.name
                                                    it[studentConfidence] = req.student_confidence
                                                    it[correct] = deterministicCorrect
                                                    it[score] = deterministicScore
                                                    it[scaffoldLevel] = (req.scaffold_level ?: 0).coerceIn(0, 4)
                                                    it[isFarTransfer] = req.is_far_transfer
                                                    it[selfExplanation] = req.self_explanation
                                                    it[AttemptsTable.recorded] = true
                                                    it[gradedAt] = now
                                                }
                                                // LAST in-txn write: the rubric-criterion card upsert (B2).
                                                // The B1 atomicity hook fires immediately before it.
                                                drillCardUpsertHook()
                                                cardRepo.upsertRubricCriterion(
                                                    this, userId, kcId, front, back,
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
                                        }
                                        recorded = true
                                    } catch (e: Exception) {
                                        // FAIL-LOUD (H4/B1): the single txn rolled back — NOTHING persisted.
                                        // 500 so a real write failure is never silently swallowed as a pass.
                                        System.err.println("[drill-grade] atomic grade txn FAILED (rolled back) for task=${req.taskId}: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                                        httpStatus = HttpStatusCode.InternalServerError
                                        replyStatus = "error"
                                        recorded = false
                                    }
                                }
                            }
                        }
                        // ── Phase-3 GROUP 7 (SERVE WIRING) — assemble the served teaching + H15 payload.
                        //    All reads are off the STORED corpus + the recorded mastery (H16: populated
                        //    from stored content; absent ⇒ null/empty). Wrapped in runCatching so a serve
                        //    assembly hiccup never breaks the already-decided grade verdict — EXCEPT the
                        //    citation chokepoint (P0-2 / P2-RULE8): a matched misconception with no resolved
                        //    SourceRef must FAIL-LOUD (CitationGuard.attach throws), NOT be swallowed into a
                        //    silently-dropped refutation. That throw is re-raised below ⇒ 500, never un-cited.
                        val served = runCatching {
                            val repo = jarvis.content.ContentRepo(drillContentDir())
                            val primaryKc = primaryKcId?.let {
                                runCatching { jarvis.web.VerifyAdmin.findKc(repo, it) }.getOrNull()
                            }
                            // Misconceptions stored for the primary KC's subject, narrowed to this KC.
                            val kcMiscs = primaryKc?.let { kc ->
                                runCatching { repo.loadSubject(kc.subject).misconceptions.filter { it.kc_id == kc.id } }
                                    .getOrNull().orEmpty()
                            }.orEmpty()
                            // TASK P3-MISC-SERVE — match the grader's code to a stored misconception, inline it.
                            val matched = jarvis.tutor.MisconceptionPayload.matchByGraderCode(g.misconception, kcMiscs)
                            // P0-2 (re-open fix) — route the served refutation through the CitationGuard
                            // chokepoint (§Q write-site 2 / P2-RULE8): it ships CITED (carrying the
                            // misconception's SourceRef) and FAIL-LOUD if the matched misconception has no
                            // resolved source. fromCited THROWS in that case — handled in getOrElse below.
                            val miscPayload = jarvis.tutor.MisconceptionPayload.fromCited(matched)
                            // TASK P3-LADDER-SERVE — render L0–L4 from stored content (+ this attempt's feedback).
                            val ladder = jarvis.tutor.FeedbackLadderBuilder.build(
                                kc = primaryKc, misconception = matched, elaboratedFeedback = g.elaboratedFeedback,
                            )
                            // TASK P3-GHOST-FIELDS(a) — the drill-level self-explanation prompt (DrillStack rung).
                            val selfExplainPrompt = primaryKc?.self_explanation_prompt
                            // H15 — verification_status served per primary KC (honest B8 value; badge unchanged).
                            val vStatus = primaryKc?.let { jarvis.web.VerifyAdmin.resolveStatus(ctx.db, it.id, it) }
                            // H15 — phase from the recorded mastery (after the grade); next_phase_action from before→after.
                            val phaseAfter = primaryKcId?.let { kcId ->
                                runCatching { jarvis.tutor.KcMasteryRepo(ctx.db).get(userId, kcId)?.phase }.getOrNull()
                            }
                            val nextAction = phaseAfter?.let {
                                jarvis.tutor.NextPhaseResolver.resolve(phaseBefore, it, deterministicCorrect)
                            }
                            ServedTeaching(
                                misconception = miscPayload, ladder = ladder, selfExplain = selfExplainPrompt,
                                verificationStatus = vStatus, phase = phaseAfter, nextAction = nextAction,
                            )
                        }.getOrElse { e ->
                            // P0-2 / P2-RULE8 — the citation chokepoint is FAIL-LOUD: an IllegalStateException
                            // from CitationGuard.attach means a matched refutation has no resolved SourceRef,
                            // which must NEVER be silently dropped (the re-opened hole). Re-raise it so the
                            // handler's outer catch turns it into a 500 — un-cited learner-facing text never
                            // ships. Any OTHER assembly hiccup degrades the served teaching (the grade still ships).
                            if (e is IllegalStateException) throw e
                            ServedTeaching()
                        }
                        // cross_checked: this grade went through the deterministic answer↔rubric cross-check
                        // (a canonical answer was present AND it agreed with the rubric) on a confident grade.
                        // NOT a trust upgrade — purely "the two grading signals agreed" (H15 cross_checked).
                        val crossChecked = confident && answerMatch != null && answerAgrees

                        ApiDrillGradeReply(
                            correct = deterministicCorrect,
                            score = deterministicScore,
                            rubric = g.rubric,
                            misconception = g.misconception,
                            elaboratedFeedback = g.elaboratedFeedback,
                            confidence = if (confident) "HIGH" else "LOW",
                            recorded = recorded,
                            answerMatch = answerMatch,
                            kc_quarantined = kcQuarantined,
                            misconception_payload = served.misconception,
                            ladder_rungs = served.ladder,
                            self_explanation_prompt = served.selfExplain,
                            verification_status = served.verificationStatus,
                            phase = served.phase,
                            next_phase_action = served.nextAction,
                            cross_checked = crossChecked,
                            // Plan-6 Task 7 — the chain audit trail. decided_by = the deciding leg's id when
                            // a non-LLM leg decided (oracle/execution/rubric), else "llm-judge" (every non-LLM
                            // leg deferred). degraded_legs_ro names the legs that degraded first; item_verdicts
                            // carries the structural per-item verdicts. (R-6-Q1 honesty / REQ-26 audit.)
                            decided_by = decidedByForReply ?: "llm-judge",
                            degraded_legs_ro = chainDegradedRo,
                            item_verdicts = itemVerdictsForReply,
                        ) to replyStatus
                    }
                }
                } catch (e: IllegalStateException) {
                    // P0-2 / P2-RULE8 FAIL-LOUD — the citation chokepoint (CitationGuard.attach) threw:
                    // a matched, learner-facing misconception refutation has NO resolved SourceRef, so it
                    // must NEVER ship (un-cited) and must NOT be silently dropped (the re-opened hole).
                    // Respond a loud 500 (the malformed-corpus authoring bug surfaces immediately). The
                    // grade verdict itself was already decided/recorded; only the un-citable teaching serve fails.
                    System.err.println("[drill-grade] served-teaching citation FAIL-LOUD for task=${req.taskId}: ${e.message?.take(200)}")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiDrillGradeReply(
                            correct = false, score = 0.0, rubric = emptyMap(),
                            misconception = "CITATION_ERROR",
                            elaboratedFeedback = "Served teaching could not be cited to your lecture — refusing to show un-cited content.",
                            confidence = "LOW", recorded = false, answerMatch = null,
                        ),
                    )
                    return@csrfProtect
                }

                call.respond(httpStatus, reply)

                // Build + enqueue the envelope (non-blocking via the bounded
                // channel inside TutorEventLog). If serialization or hashing
                // throws, swallow it — log telemetry must never break the
                // user-facing response.
                runCatching {
                    val llmOutputFull = sensorJson.encodeToString(
                        ApiDrillGradeReply.serializer(),
                        reply,
                    )
                    // TODO(standin-task-0.5): expose real system-prompt text from DrillGrader.
                    val systemPromptSha256 = sha256Hex("drill-grader-v3")
                    val redacted = RcodeRedacted(
                        rcode_sha256 = sha256Hex(req.userAttempt),
                        preview_head = req.userAttempt.take(40),
                        preview_tail = req.userAttempt.takeLast(40),
                        length_chars = req.userAttempt.length,
                    )
                    val evt = TutorEvent(
                        event_type = "drill_grade",
                        event_id = java.util.UUID.randomUUID().toString().replace("-", ""),
                        ts_utc = Instant.now().toString(),
                        task_id = req.taskId,
                        session_id = sessionId,
                        prompt_template_id = "drill-grader-v3",
                        system_prompt_sha256 = systemPromptSha256,
                        retrieved_context_summary = emptyList(),
                        llm_input_full = null,
                        llm_input_redacted = redacted,
                        llm_output_full = llmOutputFull,
                        // A.3: capture raw LLM output on parse_error so Surface
                        // X status_in=parse_error queries can see what the LLM
                        // actually returned (rendered reply alone is useless
                        // for debugging the grader prompt). Truncate to 1500
                        // chars per plan + council 1778881174 guidance. Null
                        // on ok (rendered reply already in llm_output_full)
                        // and error (no LLM output to capture).
                        llm_output_raw_truncated = if (status == "parse_error" && attempt?.rawOutput != null)
                            attempt.rawOutput.take(1500)
                        else null,
                        // A.3: wire real model id from GradeAttempt.modelResolved
                        // (paired with raw-output capture per council reviewer
                        // note 6). On LLM-exception path attempt is null, so
                        // this stays null — preserves today's behavior.
                        model_resolved = attempt?.modelResolved,
                        tokens_in = null,      // TODO(standin-task-0.5): expose token counts from DrillGrader.
                        tokens_out = null,     // TODO(standin-task-0.5): expose token counts from DrillGrader.
                        latency_ms = System.currentTimeMillis() - t0,
                        status = status,
                        is_synthetic = isSynthetic,
                    )
                    TutorEventLog.GLOBAL.append(evt)
                }.onFailure { e ->
                    System.err.println("[drill-grade] envelope append failed: ${e.message?.take(160)}")
                }
            }
        }

        // Gate 2: AI-literacy confirmation.
        // Records the authenticated user's acceptance of the AI-literacy notice
        // (AI Act Art. 4). requireUser is OUTER (401 on no session) so a bare
        // POST with no cookies short-circuits before the CSRF check. csrfProtect
        // is INNER (403 on mismatch). /api/v1/me/ is whitelisted from the legacy
        // jarvis_auth static gate in WebMain.kt — this route does its own auth.
        post("/api/v1/me/ai-literacy/confirm") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser { uid ->
                call.csrfProtect {
                    val body = runCatching {
                        sensorJson.decodeFromString(ConfirmLiteracyBody.serializer(), call.receiveText())
                    }.getOrNull()
                    val lang = if (body?.lang == "en") "en" else "ro"
                    AiLiteracyRepo(ctx.db).confirm(uid, AI_LITERACY_VERSION, lang)
                    ConsentRepo(ctx.db).record(uid, "ai-literacy", granted = true)
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }
            }
        }

        // GDPR Art. 15 — right of access / data portability.
        // Returns the authenticated user's own data as JSON (no CSRF needed for
        // GETs). requireUser is the sole auth gate (401 on missing/expired session).
        // /api/v1/me/ is whitelisted from the legacy static-auth gate in WebMain.kt.
        get("/api/v1/me/export") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser { uid ->
                val user = UserRepo(ctx.db).findById(uid)
                val consent = ConsentRepo(ctx.db).listForUser(uid)
                val prefs = UserPreferencesRepo(ctx.db).get(uid)
                val export = MeExport(
                    user = user?.let { MeUserDto(it.id, it.name, it.scope.name, it.email, it.lang) },
                    consentEvents = consent.map { MeConsentEventDto(it.consentType, it.granted, it.recordedAt.toString()) },
                    preferences = MePreferencesDto(prefs.hintMode, prefs.loggingPausedUntil?.toString()),
                    aiLiteracyConfirmed = AiLiteracyRepo(ctx.db).hasConfirmedCurrent(uid),
                    exportedAt = java.time.Instant.now().toString(),
                )
                call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=jarvis-export.json")
                call.respondText(
                    sensorJson.encodeToString(MeExport.serializer(), export),
                    ContentType.Application.Json,
                )
            }
        }

        // GDPR Art. 17 — right to erasure ("right to be forgotten").
        // Deletes all user-scoped rows in dependency order (child tables first,
        // then sessions, then the user row itself) inside a single transaction.
        // requireUser OUTER (401 no session) → csrfProtect INNER (403 on mismatch).
        post("/api/v1/me/delete") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser { uid ->
                call.csrfProtect {
                    // GDPR Art 17 erasure — complete across all user-scoped tables.
                    // Deletion order: every user-FK CHILD table first (in dependency
                    // order — all FK to UsersTable only, no cross-FKs), then the user
                    // row itself. The child set is the SINGLE source of truth
                    // [ME_DELETE_CASCADE_TABLES] — the CI invariant
                    // [jarvis.tutor.MeDeleteCascadeTest] asserts it covers EVERY user-FK
                    // table in the production schema, so the next one cannot be silently
                    // missed (P0-1 class-killer). AuditLinesTable is intentionally
                    // EXCLUDED (append-only hash-chained audit retained under GDPR Art
                    // 17(3)(b) — see [ME_DELETE_RETAINED_USER_FK_TABLES]). verification_audit
                    // + kc_verification_status are NOT user-scoped (keyed on kc_id).
                    transaction(ctx.db) {
                        for (table in ME_DELETE_CASCADE_TABLES) {
                            val userIdCol = table.columns.single { it.name == "user_id" }
                            @Suppress("UNCHECKED_CAST")
                            table.deleteWhere { (userIdCol as org.jetbrains.exposed.sql.Column<String>) eq uid }
                        }
                        UsersTable.deleteWhere { UsersTable.id eq uid }
                    }
                    call.respondText("""{"deleted":true}""", ContentType.Application.Json)
                }
            }
        }

        // GDPR Art. 18 — right to restriction of processing.
        // Toggles the loggingPausedUntil preference: if currently unset, sets it
        // to now + 30 days (pause logging); if already set, clears it (resume).
        // requireUser OUTER (401) → csrfProtect INNER (403).
        post("/api/v1/me/restrict") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser { uid ->
                call.csrfProtect {
                    val prefs = UserPreferencesRepo(ctx.db).get(uid)
                    val newUntil = if (prefs.loggingPausedUntil == null)
                        java.time.Instant.now().plusSeconds(60L * 60 * 24 * 30) else null
                    UserPreferencesRepo(ctx.db).set(uid, prefs.hintMode, newUntil)
                    call.respondText("""{"loggingPaused":${newUntil != null}}""", ContentType.Application.Json)
                }
            }
        }
    }
}

private val sensorJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Resolve the content/ directory (mirrors TrustRoutes.trustContentDir + the drillKcLookup seam).
 * The atomic grade route uses it to load the content KC for the Phase-3 faithful gate.
 */
private fun drillContentDir(): Path =
    Path.of(
        System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )

// Gate 2: request body for POST /auth/request-link.
@kotlinx.serialization.Serializable
private data class RequestLinkBody(val email: String, val lang: String = "ro")

// Gate 2: request body for POST /api/v1/me/ai-literacy/confirm.
@kotlinx.serialization.Serializable
private data class ConfirmLiteracyBody(val lang: String = "ro")

/** RFC-5322 simplified: at least one non-whitespace/@ char, @, domain with dot. */
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/**
 * SHA-256 of a UTF-8 string, lowercase hex (64 chars). Used by the drill-grade
 * envelope writer to hash R-code attempts before logging — keeps raw student
 * code out of `tutor_events.*.jsonl` while still letting Surface X group
 * repeated attempts by identity.
 */
private fun sha256Hex(s: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

@Serializable
private data class ScreenshotRequest(
    val imageBase64: String,
    val mediaType: String? = null,
    val taskId: String? = null,
    val sensorId: String? = null,
    val sensorVersion: String? = null,
    val hint: String? = null,
)

@Serializable
private data class ScreenshotResponse(
    val eventSeq: Long,
    val extracted: jarvis.tutor.ScreenshotExtraction,
    /** Layer B0 — server-side classification. true = effectors auto-
     *  disabled in the UI per spec §4 item 5. */
    val readOnlyMode: Boolean = false,
    val readOnlyReason: String = "",
)

@Serializable
private data class ApiDispatchRequest(
    val taskId: String,
    val effectorId: String,
    val targetUri: String,
    val expectedDocVersion: String,
    val edits: List<TextEdit>,
    val nonce: String,
    val grantId: String,
    /** Layer B0/B1 selector: "CLIPBOARD" or "DAEMON". */
    val backend: String = "CLIPBOARD",
)

@Serializable
private data class ApiGrantView(
    val id: String,
    val scope: List<String>,
    val ops: List<String>,
    val expiresAt: String,
    val callsUsed: Int,
    val maxCalls: Int,
    val revokedAt: String? = null,
)

@Serializable
private data class ApiGrantsList(val grants: List<ApiGrantView>)

@Serializable
private data class ApiTaskView(
    val id: String,
    val subject: String,
    val title: String,
    val deadline: String,
    val status: String,
)

@Serializable
private data class ApiTasksList(val tasks: List<ApiTaskView>)

@Serializable
private data class ApiTaskDetailView(
    val id: String, val subject: String, val title: String,
    val deadline: String, val status: String,
    val materialPaths: List<String>,
)

@Serializable
private data class ApiCreateTaskRequest(
    val subject: String,
    val title: String,
    /** ISO-8601 instant. */
    val deadline: String,
    val repo: String = "user",
    val problemPath: String = "",
    val rubricPath: String = "",
)

@Serializable
private data class ApiCardStatusRequest(val status: String)

@Serializable
private data class ApiCardStatusReply(val logId: String)

@Serializable
private data class ApiCreateGapRequest(
    val topic: String,
    val language: String? = null,
    val type: String,
    val trigger: String,
    val content: String,
    val exampleCode: String? = null,
    val sourceCitation: String? = null,
    val taskId: String? = null,
)

@Serializable
private data class ApiGapView(
    val id: String,
    val taskId: String?,
    val topic: String,
    val language: String?,
    val type: String,
    val trigger: String,
    val content: String,
    val exampleCode: String?,
    val sourceCitation: String?,
    val resolvedBy: String?,
    val reusedCount: Int,
    val fsrsCardId: String? = null,
)

@Serializable
private data class ApiGapsList(val gaps: List<ApiGapView>)

@Serializable
private data class ApiSearchDocResult(val filename: String, val snippet: String, val lineRef: String?)

@Serializable
private data class ApiSearchDocsReply(val results: List<ApiSearchDocResult>)

@Serializable
private data class ApiLastTaskReply(val taskId: String?)

@Serializable
private data class ApiTaskDetectReply(val inserted: Int, val existing: Int, val total: Int)

@Serializable
private data class ApiPromoteGapReply(val cardId: String, val createdNew: Boolean)

@Serializable
private data class ApiPdfUploadReply(val bytes: Int)

@Serializable
private data class ApiTaskDeleteReply(val taskId: String)

@Serializable
private data class ApiTaskSubmitRequest(val note: String? = null)

@Serializable
private data class ApiTaskSubmitReply(
    val taskId: String,
    val status: String,
    val submittedAt: String,
)

@Serializable
private data class ApiTaskRepRepReply(
    val taskId: String,
    val problems: Int,
    val generatedAt: String,
)

@Serializable
private data class ApiTaskPrepReply(
    val taskId: String,
    val generatedAt: String,
    val version: Int,
    val problemsJson: String,
    val drillsJson: String,
    val railJson: String,
)

@Serializable
data class ApiCitation(
    val path: String,
    val snippet: String,
    val score: Double,
)

@Serializable
internal data class ApiSidekickReply(
    val text: String,
    val model: String,
    val quotedContext: String?,
    val citations: List<ApiCitation> = emptyList(),
)

@Serializable
private data class ApiGwsStatusReply(
    val enabled: Boolean,
    val binaryFound: Boolean,
    val authenticated: Boolean,
    val detail: String,
)

@Serializable
private data class ApiGoogleStatusReply(
    val enabled: Boolean,
    val tokenPresent: Boolean,
    val tokenExpiresAt: String?,
    val tokenRefreshable: Boolean,
)

@Serializable
private data class ApiGatewayInboundRequest(
    val channel: String,
    val fromUser: String,
    val text: String,
)

@Serializable
private data class ApiGatewayInboundResponse(
    val text: String,
    val source: String,
)

@Serializable
private data class ApiCreateGrantRequest(
    val scope: List<String>,
    val ops: List<String> = listOf("APPLY_EDIT"),
    /** Default 1 hour, hard-capped at 8 hours per spec §4 item 4. */
    val ttlSeconds: Long = 3600,
    val maxCalls: Int = 10,
)

// GDPR DSR (Data Subject Rights) response DTOs — Task 16.
// Serialization lives at the route boundary; domain classes (User, ConsentEvent,
// UserPreferences) are NOT annotated.

@kotlinx.serialization.Serializable
private data class MeUserDto(
    val id: String,
    val name: String,
    val scope: String,
    val email: String?,
    val lang: String,
)

@kotlinx.serialization.Serializable
private data class MeConsentEventDto(
    val consentType: String,
    val granted: Boolean,
    val recordedAt: String,
)

@kotlinx.serialization.Serializable
private data class MePreferencesDto(
    val hintMode: String,
    val loggingPausedUntil: String?,
)

@kotlinx.serialization.Serializable
private data class MeExport(
    val user: MeUserDto?,
    val consentEvents: List<MeConsentEventDto>,
    val preferences: MePreferencesDto,
    val aiLiteracyConfirmed: Boolean,
    val exportedAt: String,
)

@Serializable
private data class ApiScratchpadView(val text: String)

@Serializable
private data class ApiTelemetryRequest(
    val name: String,
    val ts: String? = null,
    val payload: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
private data class ApiPrepAuthoredRequest(
    val problems: List<jarvis.tutor.Problem>,
    val version: Int = 1,
)

@Serializable
private data class ApiGenerateDrillsRequest(val kcId: String, val shape: String? = null, val count: Int = 1)

@Serializable
private data class ApiGenerateDrillsReply(
    val taskId: String,
    val accepted: List<AcceptedDrill>,
    val rejectedCount: Int,
    val rejectReasons: List<String>,
    val criticUsed: String,
    val generatedAt: String,
)
@Serializable
private data class AcceptedDrill(val problemId: String, val shape: String)

@Serializable
private data class ApiDrillGradeRequest(
    val taskId: String,
    val problemId: String,
    val problemStatement: String,
    val userAttempt: String,
    val expectedAnswerHint: String,
    val language: String? = null,
    val referenceSolution: String? = null,
    val rubricItems: List<String>? = null,
    val prediction: String? = null,
    /**
     * Set by frontend when the user clicks GIVE UP. The grader renders a
     * teaching-pass prompt and never sees the legacy "ATTEMPTED_NOT_SOLVED"
     * sentinel. Default false preserves wire compat with older callers — the
     * grader also auto-detects the sentinel server-side.
     */
    val giveUp: Boolean = false,
    // E1/E2 trustworthy-grader: optional deterministic-grading inputs.
    // [canonicalAnswer] is a FALLBACK only — the server prefers the persisted
    // Problem.canonicalAnswer (looked up by taskId+problemId).
    // [conceptIds] is RETAINED for wire-compat but NO LONGER drives recorded
    // mastery (E2: mastery records on the server-side Problem.kcIds, never client input).
    val canonicalAnswer: String? = null,
    val conceptIds: List<String>? = null,
    // Phase-3 GROUP 2 (H1) — per-attempt calibration / scaffolding inputs (master §2.2).
    // student_confidence ∈ DEFINITELY|MAYBE|GUESS|IDK | null; NOT the grader HIGH|LOW confidence.
    val student_confidence: String? = null,
    val scaffold_level: Int? = null,
    val is_far_transfer: Boolean = false,
    val self_explanation: String? = null,
)

@Serializable
private data class ApiDrillGradeReply(
    // `correct` is the displayed verdict; `recorded` is the mastery-authoritative flag (a LOW-confidence grade can show a verdict yet record nothing).
    val correct: Boolean,
    val score: Double,
    val rubric: Map<String, Boolean>,
    val misconception: String?,
    val elaboratedFeedback: String,
    // E1 trustworthy-grader.
    val confidence: String,            // "HIGH" | "LOW"
    val recorded: Boolean,             // true iff this grade updated mastery
    val answerMatch: Boolean? = null,  // present iff canonicalAnswer was supplied
    // Phase-3 GROUP 2 (H15 / faithful-gating) — trust signals on the served grade. `kc_quarantined`
    // is true iff at least one server-resolved KC was DENY-gated (non-faithful) so its mastery was
    // NOT recorded; the displayed verdict still ships, only the mastery write is withheld.
    val kc_quarantined: Boolean = false,
    // ── Phase-3 GROUP 7 (SERVE WIRING) — ADDITIVE served teaching + H15 fields. All default so the
    //    G2 grade shape is untouched. Frozen in interface-signatures-lock §O (teaching) + §B/§N (H15).
    //    H16: each is POPULATED from stored content with a NAMED Phase-5 consumer; absent ⇒ null/empty.
    //
    //    TASK P3-MISC-SERVE — the INLINE misconception payload (gap C, surface 0f MisconceptionRibbon).
    //    Named `misconception_payload` (NOT `misconception`) because the frozen G2 reply already carries
    //    `misconception: String?` (the grader's misconception CODE — a different thing). Renaming that
    //    would break G2; the structured §O payload rides under a distinct field. DECISION (owner away).
    val misconception_payload: jarvis.tutor.MisconceptionPayload? = null,
    // TASK P3-LADDER-SERVE — the rendered L0–L4 ladder + the drill-level self-explanation prompt.
    val ladder_rungs: List<jarvis.tutor.LadderRung> = emptyList(),
    val self_explanation_prompt: String? = null,
    // H15 grade-reply fields G2 deferred to GROUP 7. verification_status carries the honest B8 value
    // (VerifyAdmin.resolveStatus per primary graded KC); badge language is unchanged (always "matches
    // your lecture", never "verified correct"). phase/next_phase_action from the recorded mastery.
    val verification_status: jarvis.tutor.VerificationStatus? = null,
    val phase: jarvis.tutor.Phase? = null,
    val next_phase_action: jarvis.tutor.NextPhaseAction? = null,
    // cross_checked = the grade went through the deterministic cross-check layer (answerMatch agreed
    // with the rubric) — true only on a confident, recorded, agreement grade. NOT a trust upgrade.
    val cross_checked: Boolean = false,
    // ── Plan-6 Task 7 — grader-CHAIN integration (§0.9-F). ADDITIVE, all defaulted so the frozen
    //    G2 reply shape + the §O served-teaching fields are byte-identical for legacy callers.
    //    decided_by = which leg produced the verdict (REQ-26 audit trail); the value is the
    //    routing-leg id, "numeric-oracle"|"execution"|"rubric"|"llm-judge". null on the legacy
    //    UNGRADED/parse_error degraded replies (no chain ran). degraded_legs_ro = RO copy for each
    //    non-LLM leg that was SKIPPED (disabled/unavailable/non-applicable) before the deciding leg
    //    (R-6-Q1 honesty — the reply says which legs degraded). item_verdicts = per-item structural
    //    verdicts (rubric G-items / oracle), empty when the deciding leg emits none.
    val decided_by: String? = null,
    val degraded_legs_ro: List<String> = emptyList(),
    val item_verdicts: List<jarvis.tutor.ItemVerdict> = emptyList(),
)

/**
 * Phase-3 GROUP 7 — internal (NOT a wire type) holder for the served teaching + H15 fields assembled
 * off the stored corpus in the grade handler, then spread onto [ApiDrillGradeReply]. Defaults = the
 * fully-degraded payload (no KC / serve hiccup) so the grade verdict always ships.
 */
private data class ServedTeaching(
    val misconception: jarvis.tutor.MisconceptionPayload? = null,
    val ladder: List<jarvis.tutor.LadderRung> = emptyList(),
    val selfExplain: String? = null,
    val verificationStatus: jarvis.tutor.VerificationStatus? = null,
    val phase: jarvis.tutor.Phase? = null,
    val nextAction: jarvis.tutor.NextPhaseAction? = null,
)

@Serializable
private data class ApiFsrsCardView(
    val id: String, val front: String, val back: String,
    val sourceTaskId: String?,
    val difficulty: Double, val stability: Double, val retrievability: Double,
    val dueAt: String, val lapses: Int,
)

@Serializable
private data class ApiFsrsDueReply(val cards: List<ApiFsrsCardView>)

@Serializable
private data class ApiFsrsGradeRequest(val grade: Int)

@Serializable
private data class ApiFsrsGradeReply(
    val cardId: String, val nextDueAt: String,
    val newDifficulty: Double, val newStability: Double,
)

@Serializable
private data class ApiFsrsForecastReply(val dueNow: Int, val tomorrow: Int, val thisWeek: Int, val thisMonth: Int)

/**
 * Wires the Tutor Layer A persistence + ledger context onto the running
 * Application. Closes the prod gap from Task 21: without this, /auth/setup
 * 500s because TutorContextKey is missing from application.attributes.
 *
 * Side effects:
 *  - Ensures [ledgerDir] exists.
 *  - Ensures the parent directory of [dbPath] exists.
 *  - Opens a SQLite connection via [TutorDb.connect] (sets WAL + foreign_keys).
 *  - Runs SchemaUtils.create() for every Layer A table — idempotent, will
 *    no-op if the schema is already present (acceptance criterion: safe to
 *    call on a hot DB across restarts).
 *  - Stores the resulting [TutorContext] under [TutorContextKey] so route
 *    handlers can pull it off `application.attributes`.
 *
 * Must be called from inside an `application { ... }` / module block BEFORE
 * [installTutorRoutes], which is what reads the attribute.
 */
fun Application.installTutorContext(dbPath: String, ledgerDir: Path) {
    Files.createDirectories(ledgerDir)
    val parent = Path.of(dbPath).parent ?: Path.of(".")
    Files.createDirectories(parent)
    val db = TutorDb.connect(dbPath)
    // Phase-1 migration runner (data-model-lock Task 3): registers all tables (incl. the
    // CHANGE 4–10 new tables) + runs the explicit post-ALTER backfills (status='ACTIVE',
    // kc_id, kc_mastery.phase replay) inside ONE try/catch guard. On a failed ALTER it names
    // the failing column, fires an auto off-box backup, and aborts (recovery = restore-from-backup;
    // SQLite ALTER is NOT transactional — M-PARTIAL). Idempotent (safe to run every boot — M-IDEMP).
    jarvis.tutor.TutorMigration.migrate(db)
    // Bootstrap OWNER account (idempotent). This row is the fallback admin
    // identity; the auto-session route does NOT auto-login into it — callers
    // must authenticate via /auth/request-link + /auth/verify.
    if (jarvis.tutor.UserRepo(db).findById("owner") == null) {
        try {
            val now = java.time.Instant.now()
            jarvis.tutor.UserRepo(db).insert(
                jarvis.tutor.User(
                    id = "owner",
                    name = "owner",
                    scope = jarvis.tutor.UserScope.OWNER,
                    createdAt = now,
                    lastSeenAt = now,
                ),
            )
        } catch (e: Exception) {
            System.err.println("[installTutorContext] WARN owner insert failed: ${e.message?.take(160)}")
        }
    }

    // Layer B: resolve vision LLM from env (OPENROUTER_API_KEY). Null when
    // unset — sensor route returns 503 with a helpful message rather than
    // 500ing on a NullPointerException.
    val vision = jarvis.VisionLlmFactory.create()

    // Gate 2: build mailer. Uses Resend when RESEND_API_KEY is set; falls
    // back to LoggingMailer (prints link to stdout) in dev.
    val resendKey = System.getenv("RESEND_API_KEY")
        ?: io.github.cdimascio.dotenv.dotenv { ignoreIfMissing = true }["RESEND_API_KEY"]
    val mailFrom = System.getenv("JARVIS_MAGIC_LINK_FROM") ?: "jarvis@corgflix.duckdns.org"
    val mailer: jarvis.tutor.MagicLinkMailer = if (!resendKey.isNullOrBlank()) {
        val mailClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                ktorJson()
            }
        }
        jarvis.tutor.ResendMailer(resendKey, mailFrom, mailClient)
    } else {
        jarvis.tutor.LoggingMailer()
    }

    attributes.put(TutorContextKey, TutorContext(db, ledgerDir, vision, mailer))
    // Layer B1: start the effector watchdog (default-OFF behind
    // EFFECTOR_WATCHDOG_ENABLED env). Reaps stale PRE_SEALED rows
    // every 1s.
    jarvis.tutor.EffectorWatchdog.start(db, ledgerDir)

    // Stage F: cron skill runner (default-OFF, JARVIS_CRON_ENABLED).
    // Reads SKILL.md frontmatter `cron_minutes` + `cron_enabled`.
    jarvis.tutor.CronRunner.start()
}
