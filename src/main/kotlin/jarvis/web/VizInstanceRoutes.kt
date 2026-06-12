package jarvis.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import jarvis.content.ContentRepo
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.nio.file.Path

/**
 * Plan 4b Task 3 — GET /api/v1/viz/{instanceId}
 *
 * Contract frozen in interface-signatures-lock §NEW-V (shipped as signatures-lock-viz-route.patch,
 * same commit as this file per the lock's durable rule).
 *
 * Reply: [ApiVizInstanceReply] — exactly the [jarvis.content.VizInstance] fields the client needs
 * to mount a family renderer. Snake_case wire (backend casing wins, §0.9B).
 * 200 known id; 404 unknown id (OMIT — no information leak); 401 no session.
 * Read-only: no DB, no LLM.
 */

/** §0.9B reply shape — frozen. */
@Serializable
data class ApiVizInstanceReply(
    val id: String,
    val subject: String,
    val family_id: String,
    val language: String,
    val data_json: String,
)

/** Resolves the content/ directory — same pattern as CuratorRoutes / QueueMasteryCalibrationRoutes. */
private fun vizContentDir(): Path =
    Path.of(
        System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )

/**
 * Installs the viz instance serve route on the receiving [Application].
 *
 * Mounted by a ONE-LINE PM patch in [TutorRoutes.kt]'s [installTutorRoutes] (Lane B file — R-4b-Q11
 * discovered-need path; patch: tutor-routes-viz-mount.patch, applied at PM checkpoint CP-1).
 * The route is self-contained: tests install it directly without the mount patch.
 */
fun Application.installVizInstanceRoutes() {
    // Lazily enumerate the corpus once per request — corpus is small (O(10) instances) and
    // read from disk at dev time; production will be behind the real Ktor server which forks per
    // request. A request-scoped load is the same pattern as every other content route (CuratorRoutes,
    // QueueMasteryCalibrationRoutes) and avoids a stale-cache hazard during dev cycles.
    routing {
        get("/api/v1/viz/{instanceId}") {
            // Auth: jarvis_session cookie required (same check as all tutor GET routes).
            val uid = call.userIdOrNull()
            if (uid == null) {
                call.respond(HttpStatusCode.Unauthorized, """{"error":"not authenticated"}""")
                return@get
            }

            val rawId = call.parameters["instanceId"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "instanceId required"); return@get }
            // URL-decode (instance ids may contain hyphens/underscores, which are safe, but decode anyway).
            val instanceId = URLDecoder.decode(rawId, Charsets.UTF_8)

            // Enumerate the corpus. loadAllVizInstances loops per-subject loadVizInstances which
            // already parse-validates data_json at load time (load error = 500, malformed corpus).
            val allInstances = try {
                ContentRepo(vizContentDir()).loadAllVizInstances()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "corpus load failed: ${e.message?.take(200)}")
                return@get
            }

            val inst = allInstances.firstOrNull { it.id == instanceId }
                ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"not found"}"""); return@get }

            call.respond(
                HttpStatusCode.OK,
                ApiVizInstanceReply(
                    id = inst.id,
                    subject = inst.subject,
                    family_id = inst.family_id,
                    language = inst.language,
                    data_json = inst.instance.data_json,
                ),
            )
        }
    }
}
