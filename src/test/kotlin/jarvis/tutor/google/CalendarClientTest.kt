package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class CalendarClientTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach fun stop() = server.stop(0)

    private fun makeClient(): CalendarClient {
        val store = InMemoryTokenStore(
            OAuth2Token("test-access", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(
            store,
            ClientCreds("cid", "csec"),
            baseApiUrl = "http://localhost:$port",
        )
        return CalendarClient(api)
    }

    @Test
    fun `eventsInsert returns EventId on 200 response`() {
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            val resp = """{"kind":"calendar#event","id":"event-abc-123","summary":"Study PS"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val client = makeClient()
        val result = client.eventsInsert(
            calendarId = "primary",
            summary = "Study PS",
            startIso = "2026-05-10T14:00:00+03:00",
            endIso = "2026-05-10T15:00:00+03:00",
        )
        assertTrue(result.isSuccess)
        assertEquals("event-abc-123", result.getOrThrow())
    }

    @Test
    fun `eventsInsert returns failure on non-200 response`() {
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            val resp = """{"error":{"code":403,"message":"Calendar usage limits exceeded."}}""".toByteArray()
            ex.sendResponseHeaders(403, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().eventsInsert(
            calendarId = "primary",
            summary = "Test",
            startIso = "2026-05-10T14:00:00+03:00",
            endIso = "2026-05-10T15:00:00+03:00",
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
    }

    @Test
    fun `eventsInsert sends correct JSON body shape`() {
        var capturedBody = ""
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            capturedBody = ex.requestBody.bufferedReader().readText()
            val resp = """{"id":"evt-body-check"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().eventsInsert("primary", "Deadline reminder",
            "2026-05-11T09:00:00+03:00", "2026-05-11T10:00:00+03:00")
        assertTrue(capturedBody.contains("\"summary\""))
        assertTrue(capturedBody.contains("Deadline reminder"))
        assertTrue(capturedBody.contains("\"dateTime\""))
        assertTrue(capturedBody.contains("\"start\""))
        assertTrue(capturedBody.contains("\"end\""))
    }
}
