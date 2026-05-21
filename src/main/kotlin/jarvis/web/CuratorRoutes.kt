package jarvis.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import jarvis.content.ContentRepo
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContextKey
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import kotlinx.serialization.json.Json
import java.nio.file.Path

private val curatorJson = Json { encodeDefaults = true; prettyPrint = false }

@kotlinx.serialization.Serializable
private data class GraphNode(val id: String, val name_en: String, val tier: Int)

@kotlinx.serialization.Serializable
private data class GraphResponse(
    val subject: String,
    val nodes: List<GraphNode>,
    val edges: List<jarvis.content.PrereqEdge>,
)

/** Resolves the content/ directory: JARVIS_CONTENT_DIR env/property, else "content" (CWD-relative). */
private fun contentDir(): Path =
    Path.of(System.getProperty("JARVIS_CONTENT_DIR")
        ?: System.getenv("JARVIS_CONTENT_DIR")
        ?: "content")

/**
 * Runs [block] with the authenticated OWNER-scope user id. Responds 401 if not
 * authenticated, 403 if authenticated but not OWNER. Gate 3 gates all curator
 * routes on OWNER; a dedicated `curator` role lands with Gate 7 RBAC.
 */
private suspend fun RoutingContext.requireOwner(block: suspend (userId: String) -> Unit) {
    val ctx = call.application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, """{"error":"not authenticated"}"""); return }
    val user = UserRepo(ctx.db).findById(userId)
    if (user?.scope != UserScope.OWNER) {
        call.respondText("""{"error":"curator access requires OWNER scope"}""",
            ContentType.Application.Json, HttpStatusCode.Forbidden)
        return
    }
    block(userId)
}

/**
 * Gate 3 curator routes — READ + VALIDATE only (council 1779311876 amendment 2:
 * the write/commit/version-lock path ships with the Gate 4 curator SPA).
 * Mounted from installTutorRoutes()'s routing { } block.
 */
fun Route.installCuratorRoutes() {

    get("/api/v1/curator/subjects") {
        requireOwner {
            val manifest = ContentRepo(contentDir()).loadManifest()
            call.respondText(
                curatorJson.encodeToString(jarvis.content.SubjectsManifest.serializer(), manifest),
                ContentType.Application.Json,
            )
        }
    }

    get("/api/v1/curator/subjects/{subject}/kcs") {
        requireOwner {
            val subject = call.parameters["subject"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "subject required"); return@requireOwner }
            val knownSubjects = ContentRepo(contentDir()).loadManifest().subjects.map { it.id }
            if (subject !in knownSubjects) {
                call.respond(HttpStatusCode.NotFound, """{"error":"unknown subject"}""")
                return@requireOwner
            }
            val kcs = ContentRepo(contentDir()).loadSubject(subject).kcs
            call.respondText(
                curatorJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(jarvis.content.KnowledgeConcept.serializer()),
                    kcs,
                ),
                ContentType.Application.Json,
            )
        }
    }

    get("/api/v1/curator/subjects/{subject}/kcs/{id}") {
        requireOwner {
            val subject = call.parameters["subject"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "subject required"); return@requireOwner }
            val id = call.parameters["id"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@requireOwner }
            val knownSubjects = ContentRepo(contentDir()).loadManifest().subjects.map { it.id }
            if (subject !in knownSubjects) {
                call.respond(HttpStatusCode.NotFound, """{"error":"unknown subject"}""")
                return@requireOwner
            }
            val kc = ContentRepo(contentDir()).loadSubject(subject).kcs.firstOrNull { it.id == id }
                ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"KC not found"}"""); return@requireOwner }
            call.respondText(
                curatorJson.encodeToString(jarvis.content.KnowledgeConcept.serializer(), kc),
                ContentType.Application.Json,
            )
        }
    }

    get("/api/v1/curator/subjects/{subject}/graph") {
        requireOwner {
            val subject = call.parameters["subject"]
                ?: run { call.respond(HttpStatusCode.BadRequest, "subject required"); return@requireOwner }
            val knownSubjects = ContentRepo(contentDir()).loadManifest().subjects.map { it.id }
            if (subject !in knownSubjects) {
                call.respond(HttpStatusCode.NotFound, """{"error":"unknown subject"}""")
                return@requireOwner
            }
            val loaded = ContentRepo(contentDir()).loadSubject(subject)
            val resp = GraphResponse(
                subject = subject,
                nodes = loaded.kcs.map { GraphNode(it.id, it.name_en, it.tier) },
                edges = loaded.edges,
            )
            call.respondText(
                curatorJson.encodeToString(GraphResponse.serializer(), resp),
                ContentType.Application.Json,
            )
        }
    }

    get("/api/v1/curator/validate") {
        requireOwner {
            val report = jarvis.content.ContentCli.validateOnly(contentDir())
            call.respondText(
                curatorJson.encodeToString(jarvis.content.ValidationReport.serializer(), report),
                ContentType.Application.Json,
            )
        }
    }
}
