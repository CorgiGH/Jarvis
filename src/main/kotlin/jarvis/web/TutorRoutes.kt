package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
import jarvis.tutor.TasksTable
import jarvis.tutor.TokenRepo
import jarvis.tutor.TokensTable
import jarvis.tutor.TrustGrantsTable
import jarvis.tutor.TutorContext
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.UsersTable
import jarvis.tutor.csrfProtect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
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

        // Single-user auto-session: SPA hits this on mount. If session
        // cookie is missing OR invalid, mint a fresh session bound to
        // the canonical "owner" user + set the matching csrf cookie.
        // Idempotent — repeated calls with a valid session no-op.
        // The legacy bearer JARVIS_AUTH_TOKEN cookie still gates this
        // route (interceptor allowlist exempts /api/v1/tutor/auto-session
        // so logged-in users via /login can bootstrap their tutor side).
        get("/api/v1/tutor/auto-session") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
            val existingSid = call.request.cookies["jarvis_session"]
            val existingCsrf = call.request.cookies["csrf"]
            val valid = existingSid?.let { SessionRepo(ctx.db).findUserId(it) }
            if (valid != null && !existingCsrf.isNullOrBlank()) {
                // Already set up — return current state without rotating.
                call.respond(HttpStatusCode.OK, """{"ok":true,"userId":"$valid","csrf":"$existingCsrf"}""")
                return@get
            }
            // Mint new session for owner.
            val sid = SessionRepo(ctx.db).create("owner", ttlSeconds = 60L * 60 * 24 * 14)
            val csrf = ByteArray(16).also { rng.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            call.response.cookies.append(
                Cookie(
                    name = "jarvis_session", value = sid,
                    httpOnly = true, secure = true,
                    extensions = mapOf("SameSite" to "Strict"),
                    path = "/", maxAge = 60 * 60 * 24 * 14,
                ),
            )
            call.response.cookies.append(
                Cookie(
                    name = "csrf", value = csrf,
                    httpOnly = false, secure = true,
                    extensions = mapOf("SameSite" to "Strict"),
                    path = "/", maxAge = 60 * 60 * 24 * 14,
                ),
            )
            call.respond(HttpStatusCode.OK, """{"ok":true,"userId":"owner","csrf":"$csrf"}""")
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
            val pdfPath = ctx.ledgerDir.resolve("task-pdfs").resolve("$id.pdf")
            if (!java.nio.file.Files.exists(pdfPath)) {
                call.respond(HttpStatusCode.NotFound,
                    "no PDF for $id — drop one at ${pdfPath}")
                return@get
            }
            call.respondBytes(
                bytes = java.nio.file.Files.readAllBytes(pdfPath),
                contentType = io.ktor.http.ContentType.Application.Pdf,
            )
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
                val id = jarvis.tutor.TutorTypes.ulid()
                val now = Instant.now()
                TaskRepo(ctx.db).insert(Task(
                    id = id, userId = userId,
                    subject = req.subject.trim().take(32),
                    title = req.title.trim().take(256),
                    deadline = deadline,
                    problemRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.problemPath, sha = "pending"),
                    conceptRefs = emptyList(),
                    rubricRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.rubricPath.ifBlank { req.problemPath }, sha = "pending"),
                    scratchpad = null, submission = null, grade = null,
                    cardRefs = emptyList(),
                    status = TaskStatus.ACTIVE,
                    createdAt = now, updatedAt = now,
                ))
                jarvis.tutor.StateVersion.bump()
                call.respond(HttpStatusCode.Created, ApiTaskView(
                    id = id, subject = req.subject, title = req.title,
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
    }
}

private val sensorJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        SchemaUtils.create(
            UsersTable,
            TokensTable,
            SessionsTable,
            TasksTable,
            SensorEventsTable,
            TrustGrantsTable,
            AuditLinesTable,
            KnowledgeGapsTable,
            FsrsCardsTable,
            ProviderConfigTable,
            EffectorAttemptsTable,
        )
    }
    // Single-user owner row (idempotent). The tutor surface is
    // single-tenant; one User row backs the auto-session route.
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
    attributes.put(TutorContextKey, TutorContext(db, ledgerDir, vision))
    // Layer B1: start the effector watchdog (default-OFF behind
    // EFFECTOR_WATCHDOG_ENABLED env). Reaps stale PRE_SEALED rows
    // every 1s.
    jarvis.tutor.EffectorWatchdog.start(db, ledgerDir)

    // Stage F: cron skill runner (default-OFF, JARVIS_CRON_ENABLED).
    // Reads SKILL.md frontmatter `cron_minutes` + `cron_enabled`.
    jarvis.tutor.CronRunner.start()
}
