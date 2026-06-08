package jarvis.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jarvis.tutor.GraderProvider
import jarvis.tutor.GraderProviderSettingRepo
import jarvis.tutor.TutorContextKey
import jarvis.tutor.csrfProtect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Grader-provider preference endpoints.
 *
 * GET  /api/v1/me/grader-provider  — return the caller's current grader provider.
 *                                    Requires session (401 if missing). No CSRF needed for GETs.
 * PUT  /api/v1/me/grader-provider  — set the caller's grader provider.
 *                                    Requires session (401) + CSRF header (403 on mismatch).
 *                                    Body: {"provider":"free"|"claude"|"freellmapi"}
 *                                    Returns 400 on unknown provider value.
 *
 * Both routes are whitelisted from the legacy jarvis_auth static gate in WebMain.kt
 * (the `/api/v1/me/` prefix is already whitelisted there).
 *
 * Integration note: call [installGraderProviderRoutes] from the Application that
 * also has [TutorContextKey] in attributes (same pattern as installTutorRoutes).
 * The integration agent must add a call at the shared wiring site.
 */
fun Application.installGraderProviderRoutes() {
    routing {
        // GET /api/v1/me/grader-provider — read the setting; no CSRF for GET.
        get("/api/v1/me/grader-provider") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser { uid ->
                val provider = GraderProviderSettingRepo(ctx.db).get(uid)
                call.respondText(
                    """{"provider":"${provider.name}"}""",
                    ContentType.Application.Json,
                )
            }
        }

        // PUT /api/v1/me/grader-provider — write the setting; CSRF-protected.
        put("/api/v1/me/grader-provider") {
            val ctx = application.attributes.getOrNull(TutorContextKey)
                ?: return@put call.respond(HttpStatusCode.InternalServerError, "no ctx")
            requireUser { uid ->
                call.csrfProtect {
                    val body = runCatching {
                        graderProviderJson.decodeFromString(
                            GraderProviderBody.serializer(),
                            call.receiveText(),
                        )
                    }.getOrNull()
                    if (body == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            """{"error":"missing or malformed body; expected {\"provider\":\"free|claude|freellmapi\"}"}""",
                        )
                        return@csrfProtect
                    }
                    val parsed = runCatching {
                        GraderProvider.valueOf(body.provider)
                    }.getOrNull()
                    if (parsed == null) {
                        val valid = GraderProvider.entries.joinToString("|") { it.name }
                        call.respond(
                            HttpStatusCode.BadRequest,
                            """{"error":"unknown provider \"${body.provider}\"; valid values: $valid"}""",
                        )
                        return@csrfProtect
                    }
                    GraderProviderSettingRepo(ctx.db).set(uid, parsed)
                    call.respondText(
                        """{"provider":"${parsed.name}"}""",
                        ContentType.Application.Json,
                    )
                }
            }
        }
    }
}

private val graderProviderJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class GraderProviderBody(val provider: String = "")
