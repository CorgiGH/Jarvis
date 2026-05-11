package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
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
import jarvis.tutor.TaskPrepTable
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
                    val newId = jarvis.tutor.TutorTypes.ulid()
                    val now = Instant.now()
                    taskRepo.insert(jarvis.tutor.Task(
                        id = newId, userId = userId,
                        subject = d.subject.take(32), title = d.title.take(256),
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
                val now = java.time.Instant.now()
                val problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        jarvis.tutor.Problem.serializer()),
                    problems,
                )
                val railItems = jarvis.tutor.RailJsonBuilder.buildForTask(ctx.db, taskId, userId)
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
            call.respond(HttpStatusCode.OK, ApiGapsList(gaps.map { g ->
                ApiGapView(
                    id = g.id, taskId = g.taskId, topic = g.topic, language = g.language,
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
                val sid = call.request.cookies["jarvis_session"]
                sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val env = try {
                    sensorJson.decodeFromString(jarvis.tutor.SidekickEnvelope.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                if (env.userQuestion.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "userQuestion required")
                    return@csrfProtect
                }
                val systemContext = jarvis.tutor.SidekickContext.systemContext(env)
                var text: String
                var model: String
                var retrievalHits: List<jarvis.HybridRetriever.HybridHit> = emptyList()
                try {
                    jarvis.tutor.JarvisToolset().use { ts ->
                        val r = kotlinx.coroutines.runBlocking {
                            ts.chat(systemPrompt = systemContext, userText = env.userQuestion)
                        }
                        text = r.text
                        model = r.model
                        retrievalHits = r.hits
                    }
                } catch (e: Exception) {
                    // Graceful degraded reply: 200 with an "unavailable" body so
                    // the frontend renders the existing "(LLM unavailable)" branch
                    // and the interaction-smoke gate doesn't flag transient
                    // upstream OpenRouter failures as a wiring bug. Slice 1 spec
                    // §I: "Sidekick LLM 5xx → render `(LLM unavailable; rate-limited?)`".
                    text = "(LLM unavailable; rate-limited? ${e.message?.take(120) ?: ""})"
                    model = "(none)"
                }
                val quoted = env.selection ?: env.anchorText?.take(160)
                val citations = jarvis.tutor.CitationExtractor.extract(text, retrievalHits)
                call.respond(HttpStatusCode.OK, ApiSidekickReply(text = text, model = model, quotedContext = quoted, citations = citations))
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
                val card = jarvis.tutor.FsrsCardRepo(ctx.db).findDueForUser(userId, java.time.Instant.MAX)
                    .firstOrNull { it.id == cardId }
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
                tomorrow = f.tomorrow, thisWeek = f.thisWeek, thisMonth = f.thisMonth,
            ))
        }

        // Phase C3: drill answer grader — delegates to DrillGrader.grade() via
        // OpenRouterChatLlm. Returns a structured rubric + misconception code.
        post("/api/v1/drill/grade") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
            call.csrfProtect {
                val sid = call.request.cookies["jarvis_session"]
                sid?.let { SessionRepo(ctx.db).findUserId(it) }
                    ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
                val req = try {
                    sensorJson.decodeFromString(ApiDrillGradeRequest.serializer(), call.receiveText())
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
                    return@csrfProtect
                }
                val result = try {
                    jarvis.OpenRouterChatLlm().use { llm ->
                        kotlinx.coroutines.runBlocking {
                            jarvis.tutor.DrillGrader.grade(
                                problemStatement = req.problemStatement,
                                userAttempt = req.userAttempt,
                                expectedHint = req.expectedAnswerHint,
                                llm = llm,
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Graceful degraded reply: 200 with "ungraded" body so the
                    // frontend treats transient LLM failures as "re-attempt"
                    // instead of a wiring error. Slice 1 spec §E error handling:
                    // "LLM grader 5xx / timeout → fall back ... tag attempt as
                    // `ungraded`. Don't auto-pass."
                    call.respond(HttpStatusCode.OK, ApiDrillGradeReply(
                        correct = false, score = 0.0, rubric = emptyMap(),
                        misconception = "UNGRADED",
                        elaboratedFeedback = "LLM unavailable (${e.message?.take(120) ?: ""}). Please re-attempt or ask sidekick.",
                    ))
                    return@csrfProtect
                }
                if (result == null) {
                    call.respond(HttpStatusCode.OK, ApiDrillGradeReply(
                        correct = false, score = 0.0, rubric = emptyMap(),
                        misconception = "OTHER",
                        elaboratedFeedback = "LLM grader returned malformed output; please re-attempt or ask sidekick.",
                    ))
                    return@csrfProtect
                }
                call.respond(HttpStatusCode.OK, ApiDrillGradeReply(
                    correct = result.correct,
                    score = result.score,
                    rubric = result.rubric,
                    misconception = result.misconception,
                    elaboratedFeedback = result.elaboratedFeedback,
                ))
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

@Serializable
private data class ApiScratchpadView(val text: String)

@Serializable
private data class ApiDrillGradeRequest(
    val taskId: String,
    val problemId: String,
    val problemStatement: String,
    val userAttempt: String,
    val expectedAnswerHint: String,
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
private data class ApiFsrsForecastReply(val tomorrow: Int, val thisWeek: Int, val thisMonth: Int)

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
