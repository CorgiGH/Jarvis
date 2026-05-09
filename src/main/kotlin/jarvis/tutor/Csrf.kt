package jarvis.tutor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.csrfProtect(block: suspend () -> Unit) {
    val cookie = request.cookies["csrf"]
    val header = request.header("X-CSRF-Token")
    if (cookie.isNullOrBlank() || header != cookie) {
        respond(HttpStatusCode.Forbidden, "CSRF check failed")
        return
    }
    block()
}
