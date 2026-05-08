package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CsrfTest {
    @Test
    fun `POST without csrf header is rejected with 403`() = testApplication {
        application { routing { post("/api/v1/x") { call.csrfProtect { call.respond(HttpStatusCode.OK) } } } }
        val r = client.post("/api/v1/x") { cookie("csrf", "abc") }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `POST with matching csrf header passes`() = testApplication {
        application { routing { post("/api/v1/x") { call.csrfProtect { call.respond(HttpStatusCode.OK) } } } }
        val r = client.post("/api/v1/x") {
            cookie("csrf", "abc")
            header("X-CSRF-Token", "abc")
        }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun `POST with mismatched csrf header is rejected`() = testApplication {
        application { routing { post("/api/v1/x") { call.csrfProtect { call.respond(HttpStatusCode.OK) } } } }
        val r = client.post("/api/v1/x") {
            cookie("csrf", "abc")
            header("X-CSRF-Token", "wrong")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun `POST with missing csrf cookie is rejected`() = testApplication {
        application { routing { post("/api/v1/x") { call.csrfProtect { call.respond(HttpStatusCode.OK) } } } }
        val r = client.post("/api/v1/x") {
            header("X-CSRF-Token", "abc")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }
}
