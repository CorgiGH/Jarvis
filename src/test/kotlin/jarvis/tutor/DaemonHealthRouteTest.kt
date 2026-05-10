package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DaemonHealthRouteTest {
    private fun noLiveDaemonApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { installTutorRoutes() }
            block()
        }

    @Test
    fun `GET api v1 daemon health returns 200 with reachable=false when no daemon running`() =
        noLiveDaemonApp {
            val r = client.get("/api/v1/daemon/health")
            assertEquals(HttpStatusCode.OK, r.status)
            val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
            assertTrue(json.containsKey("reachable"))
            assertTrue(json.containsKey("tunnelUp"))
            assertTrue(json.containsKey("lastSeenAt"))
            assertEquals(false, json["reachable"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `GET api v1 daemon health shape has all three fields`() =
        noLiveDaemonApp {
            val r = client.get("/api/v1/daemon/health")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = r.bodyAsText()
            assertTrue(body.contains("\"reachable\""))
            assertTrue(body.contains("\"tunnelUp\""))
            assertTrue(body.contains("\"lastSeenAt\""))
        }

    @Test
    fun `GET api v1 daemon health content-type is application json`() =
        noLiveDaemonApp {
            val r = client.get("/api/v1/daemon/health")
            assertNotNull(r.headers[HttpHeaders.ContentType])
            assertTrue(r.headers[HttpHeaders.ContentType]!!.contains("application/json"))
        }
}
