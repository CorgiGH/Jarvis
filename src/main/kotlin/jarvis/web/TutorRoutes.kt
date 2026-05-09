package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jarvis.tutor.SessionRepo
import jarvis.tutor.TokenRepo
import jarvis.tutor.TutorContextKey
import java.security.SecureRandom

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
    }
}
