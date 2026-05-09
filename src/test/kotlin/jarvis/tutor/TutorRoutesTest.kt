package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorRoutesTest {
    @Test
    fun `GET tutor returns index html`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/tutor/")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("<div id=\"root\">") || body.contains("<div id=\\\"root\\\">"),
            "expected root div in served bundle, got: ${body.take(200)}")
    }

    @Test
    fun `GET api v1 health returns ok json`() = testApplication {
        application { installTutorRoutes() }
        val r = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"ok\":true"))
    }
}
