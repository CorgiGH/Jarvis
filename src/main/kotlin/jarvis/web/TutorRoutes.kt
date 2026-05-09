package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Tutor Layer A surface:
 *  - GET /tutor/      → SPA index.html from src/main/resources/tutor-dist
 *  - GET /tutor/...   → SPA static assets (js, css, etc.)
 *  - GET /api/v1/health → liveness probe, no auth required
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
    }
}
