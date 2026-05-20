package jarvis.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.RoutingContext
import jarvis.tutor.AiLiteracyRepo
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorContextKey

/** Resolve the user id from the jarvis_session cookie, or null. */
fun ApplicationCall.userIdOrNull(): String? {
    val ctx = application.attributes.getOrNull(TutorContextKey) ?: return null
    val sid = request.cookies["jarvis_session"] ?: return null
    return SessionRepo(ctx.db).findUserId(sid)
}

/**
 * Run [block] with the authenticated user id, or respond 401 and skip it.
 *
 * Defined as an extension on [RoutingContext] so it can be called without a
 * `call.` prefix inside Ktor 3 route handler lambdas (where `this` is
 * [RoutingContext], not [ApplicationCall]). The lambda receives the user id
 * and has the outer [RoutingContext]'s `call` in scope for responding.
 */
suspend fun RoutingContext.requireUser(block: suspend (userId: String) -> Unit) {
    val uid = call.userIdOrNull()
    if (uid == null) {
        call.respond(HttpStatusCode.Unauthorized, """{"error":"not authenticated"}""")
        return
    }
    block(uid)
}

/**
 * AI-literacy gate. Responds 403 and returns false if [userId] has not confirmed the
 * current AI-literacy version; returns true (and responds nothing) if confirmed.
 * Caller pattern:  if (!call.aiLiteracyGate(userId)) return@csrfProtect
 */
suspend fun ApplicationCall.aiLiteracyGate(userId: String): Boolean {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run {
            respond(HttpStatusCode.InternalServerError, "no ctx")
            return false
        }
    if (!AiLiteracyRepo(ctx.db).hasConfirmedCurrent(userId)) {
        respondText(
            """{"error":"ai-literacy-required"}""",
            ContentType.Application.Json,
            HttpStatusCode.Forbidden,
        )
        return false
    }
    return true
}
