package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jarvis.tutor.AuditLinesTable
import jarvis.tutor.EffectorAttemptsTable
import jarvis.tutor.FsrsCardsTable
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
    // Layer B: resolve vision LLM from env (OPENROUTER_API_KEY). Null when
    // unset — sensor route returns 503 with a helpful message rather than
    // 500ing on a NullPointerException.
    val vision = jarvis.VisionLlmFactory.create()
    attributes.put(TutorContextKey, TutorContext(db, ledgerDir, vision))
}
