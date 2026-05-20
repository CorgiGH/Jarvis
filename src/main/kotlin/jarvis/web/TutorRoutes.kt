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
import jarvis.tutor.csrfProtect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

private val rng = SecureRandom()

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
                val problems = try {
                    jarvis.OpenRouterChatLlm().use { llm ->
                        kotlinx.coroutines.runBlocking {
                            jarvis.tutor.PdfProblemExtractor.identifyProblems(pdfPath, llm)
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, "LLM error: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                // C5: populate conceptRefs via HybridRetriever (lexical; semantic skipped — no API key in prod)
                val conceptRefs = mutableListOf<jarvis.tutor.ContentRef>()
                for (p in problems) {
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
                val problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        jarvis.tutor.Problem.serializer()),
                    problems,
                )
                val railItems = jarvis.tutor.RailJsonBuilder.buildForTask(
                    ctx.db, taskId, userId,
                    jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir),
                )
                val railJsonStr = jarvis.tutor.RailJsonBuilder.toJsonArrayString(railItems)
                jarvis.tutor.TaskPrepRepo(ctx.db).upsert(jarvis.tutor.TaskPrep(
                    taskId = taskId,
                    generatedAt = now,
                    version = 1,
                    problemsJson = problemsJson,
                    drillsJson = "{}",
                    railJson = railJsonStr,
                ))
                call.respond(HttpStatusCode.OK, ApiTaskRepRepReply(
                    taskId = taskId,
                    problems = problems.size,
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

                // Student-stand-in Task 0.5: capture every grade outcome to the
                // tutor-event log for downstream Surface X (curriculum
                // sufficiency) + Surface Y (synthetic learner) audits. We pick
                // the per-path status + reply, respond once, then append a
                // single envelope before returning. Envelope build uses
                // placeholders for fields DrillGrader does not yet expose
                // (model id, token counts, real system-prompt text).
                val isSynthetic = call.request.headers["X-Standin-Run"] == "1"
                val sessionId: String = sid ?: "anon"

                // Compute reply + status across all three code paths. A.2 plan:
                // grader now returns GradeAttempt carrying raw LLM output so
                // A.3 can populate envelope llm_output_raw_truncated below.
                val attempt: jarvis.tutor.GradeAttempt? = try {
                    jarvis.OpenRouterChatLlm().use { llm ->
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

                val (reply, status) = when {
                    attempt == null -> {
                        ApiDrillGradeReply(
                            correct = false, score = 0.0, rubric = emptyMap(),
                            misconception = "UNGRADED",
                            elaboratedFeedback = "LLM unavailable. Please re-attempt or ask sidekick.",
                        ) to "error"
                    }
                    attempt.parsed == null -> {
                        // Grader returned malformed LLM output — A.3 will route
                        // attempt.rawOutput into envelope.llm_output_raw_truncated.
                        ApiDrillGradeReply(
                            correct = false, score = 0.0, rubric = emptyMap(),
                            misconception = "OTHER",
                            elaboratedFeedback = "LLM grader returned malformed output; please re-attempt or ask sidekick.",
                        ) to "parse_error"
                    }
                    else -> {
                        val r = attempt.parsed!!
                        ApiDrillGradeReply(
                            correct = r.correct,
                            score = r.score,
                            rubric = r.rubric,
                            misconception = r.misconception,
                            elaboratedFeedback = r.elaboratedFeedback,
                        ) to "ok"
                    }
                }

                call.respond(HttpStatusCode.OK, reply)

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
                    // Deletion order: child tables first (all FK to UsersTable only;
                    // no cross-FKs between the tables below), then SessionsTable,
                    // then the user row itself. AuditLinesTable is intentionally
                    // EXCLUDED — it is an append-only hash-chained audit log retained
                    // by design under GDPR Art 17(3)(b) (legal obligation / public
                    // interest / accountability).
                    transaction(ctx.db) {
                        ConsentLogTable.deleteWhere { ConsentLogTable.userId eq uid }
                        UserPreferencesTable.deleteWhere { UserPreferencesTable.userId eq uid }
                        AiLiteracyConfirmationTable.deleteWhere { AiLiteracyConfirmationTable.userId eq uid }
                        TrustGrantsTable.deleteWhere { TrustGrantsTable.userId eq uid }
                        SensorEventsTable.deleteWhere { SensorEventsTable.userId eq uid }
                        EffectorAttemptsTable.deleteWhere { EffectorAttemptsTable.userId eq uid }
                        CardActionLogTable.deleteWhere { CardActionLogTable.userId eq uid }
                        DetectedTaskMappingTable.deleteWhere { DetectedTaskMappingTable.userId eq uid }
                        TasksTable.deleteWhere { TasksTable.userId eq uid }
                        FsrsCardsTable.deleteWhere { FsrsCardsTable.userId eq uid }
                        KnowledgeGapsTable.deleteWhere { KnowledgeGapsTable.userId eq uid }
                        TokensTable.deleteWhere { TokensTable.userId eq uid }
                        SessionsTable.deleteWhere { SessionsTable.userId eq uid }
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
)

@Serializable
private data class ApiDrillGradeReply(
    val correct: Boolean,
    val score: Double,
    val rubric: Map<String, Boolean>,
    val misconception: String?,
    val elaboratedFeedback: String,
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
    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(
            UsersTable,
            TokensTable,
            SessionsTable,
            MagicLinkTokensTable,        // Gate 2 — magic-link auth
            AiLiteracyConfirmationTable, // Gate 2 — AI-literacy confirmation (AI Act Art. 4)
            ConsentLogTable,             // Gate 2 — GDPR consent audit log
            UserPreferencesTable,        // Gate 2 — per-user settings (hint mode, GDPR Art-18 logging pause)
            TasksTable,
            SensorEventsTable,
            TrustGrantsTable,
            AuditLinesTable,
            KnowledgeGapsTable,
            FsrsCardsTable,
            ProviderConfigTable,
            EffectorAttemptsTable,
            jarvis.tutor.CardActionLogTable,
            jarvis.tutor.taskdetect.DetectedTaskMappingTable,
            TaskPrepTable,
        )
    }
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
