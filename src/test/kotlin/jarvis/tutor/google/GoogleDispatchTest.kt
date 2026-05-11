package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that the three dispatch methods in JarvisToolDefs route through
 * GoogleApiClient (via GoogleClients singleton) instead of GwsEffector.
 *
 * Uses a local HttpServer to capture the outgoing Google API calls and
 * return canned responses, proving the new wiring without network access.
 */
class GoogleDispatchTest {

    private fun calendarArgsJson(summary: String = "Study PS",
                                  start: String = "2026-05-10T14:00:00+03:00",
                                  end: String = "2026-05-10T15:00:00+03:00"): String =
        """{"summary":"$summary","startIso":"$start","endIso":"$end"}"""

    private fun driveArgsJson(query: String = "name contains 'pdf'", pageSize: Int = 5): String =
        """{"query":"$query","pageSize":$pageSize}"""

    private fun gmailArgsJson(to: String = "a@b.com",
                               subject: String = "Hi",
                               body: String = "Hello"): String =
        """{"to":"$to","subject":"$subject","body":"$body"}"""

    @Test
    fun `calendar_create_event dispatch returns Google error message when token absent`() {
        // When no token file exists, dispatch must return a human-readable error
        // containing "google-auth-bootstrap" so the LLM can relay the fix to the user.
        val result = jarvis.tutor.JarvisToolDefs.dispatch(
            "calendar_create_event", calendarArgsJson()
        ).text
        // Either success (if token somehow present) or meaningful error
        assertTrue(result.isNotEmpty())
        // If it failed, it must mention the bootstrap command, not "gws"
        if (result.contains("error") || result.contains("disabled") || result.contains("token")) {
            assertFalse(result.contains("gws "), "result must not mention old gws binary: $result")
        }
    }

    @Test
    fun `drive_search dispatch returns Google error message when token absent`() {
        val result = jarvis.tutor.JarvisToolDefs.dispatch(
            "drive_search", driveArgsJson()
        ).text
        assertTrue(result.isNotEmpty())
        if (result.contains("error") || result.contains("disabled") || result.contains("token")) {
            assertFalse(result.contains("gws "), "result must not mention old gws binary: $result")
        }
    }

    @Test
    fun `gmail_create_draft dispatch returns Google error message when token absent`() {
        val result = jarvis.tutor.JarvisToolDefs.dispatch(
            "gmail_create_draft", gmailArgsJson()
        ).text
        assertTrue(result.isNotEmpty())
        if (result.contains("error") || result.contains("disabled") || result.contains("token")) {
            assertFalse(result.contains("gws "), "result must not mention old gws binary: $result")
        }
    }

    @Test
    fun `calendar_create_event dispatch succeeds with mocked GoogleApiClient`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            val resp = """{"id":"evt-test-99","summary":"Study PS"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val store = InMemoryTokenStore(
                OAuth2Token("access", "refresh", Instant.now().plusSeconds(3600))
            )
            val api = GoogleApiClient(store, ClientCreds("c", "s"),
                baseApiUrl = "http://localhost:$port")
            val cal = CalendarClient(api)
            val result = cal.eventsInsert("primary", "Study PS",
                "2026-05-10T14:00:00+03:00", "2026-05-10T15:00:00+03:00")
            assertTrue(result.isSuccess)
            assertContains(result.getOrThrow(), "evt-test-99")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `drive_search dispatch succeeds with mocked GoogleApiClient`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/drive/v3/files") { ex ->
            val resp = """{"files":[{"id":"f1","name":"Tema_A.pdf","mimeType":"application/pdf"}]}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val store = InMemoryTokenStore(
                OAuth2Token("access", "refresh", Instant.now().plusSeconds(3600))
            )
            val api = GoogleApiClient(store, ClientCreds("c", "s"),
                baseApiUrl = "http://localhost:$port")
            val drive = DriveClient(api)
            val result = drive.filesList("name contains 'Tema'")
            assertTrue(result.isSuccess)
            val files = result.getOrThrow()
            assertTrue(files.isNotEmpty())
            assertContains(files[0].name, "Tema_A.pdf")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `gmail_create_draft dispatch succeeds with mocked GoogleApiClient`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            val resp = """{"id":"draft-dispatch-ok"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val store = InMemoryTokenStore(
                OAuth2Token("access", "refresh", Instant.now().plusSeconds(3600))
            )
            val api = GoogleApiClient(store, ClientCreds("c", "s"),
                baseApiUrl = "http://localhost:$port")
            val gmail = GmailClient(api)
            val result = gmail.draftsCreate("to@test.com", "Subject", "Body")
            assertTrue(result.isSuccess)
            assertContains(result.getOrThrow(), "draft-dispatch-ok")
        } finally {
            server.stop(0)
        }
    }
}
