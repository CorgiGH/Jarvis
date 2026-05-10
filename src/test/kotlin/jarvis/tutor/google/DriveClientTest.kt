package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class DriveClientTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach fun stop() = server.stop(0)

    private fun makeClient(): DriveClient {
        val store = InMemoryTokenStore(
            OAuth2Token("test-access", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(
            store, ClientCreds("cid", "csec"),
            baseApiUrl = "http://localhost:$port",
        )
        return DriveClient(api)
    }

    @Test
    fun `filesList returns DriveFile list on 200`() {
        server.createContext("/drive/v3/files") { ex ->
            val resp = """
                {"files":[
                    {"id":"file-1","name":"Tema_A.pdf","mimeType":"application/pdf"},
                    {"id":"file-2","name":"Curs_PS.pdf","mimeType":"application/pdf"}
                ]}
            """.trimIndent().toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().filesList("name contains 'PS'")
        assertTrue(result.isSuccess)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertEquals("file-1", files[0].id)
        assertEquals("Tema_A.pdf", files[0].name)
        assertEquals("application/pdf", files[0].mimeType)
    }

    @Test
    fun `filesList returns empty list when files array is absent`() {
        server.createContext("/drive/v3/files") { ex ->
            val resp = """{"kind":"drive#fileList"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().filesList("nothing")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `filesList appends query and pageSize to request URL`() {
        var capturedPath = ""
        server.createContext("/drive/v3/files") { ex ->
            capturedPath = ex.requestURI.toString()
            val resp = """{"files":[]}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().filesList(query = "mimeType='application/pdf'", pageSize = 7)
        assertTrue(capturedPath.contains("pageSize=7"))
        assertTrue(capturedPath.contains("q="))
    }

    @Test
    fun `filesList returns failure on API error`() {
        server.createContext("/drive/v3/files") { ex ->
            val resp = """{"error":{"code":401,"message":"Invalid Credentials"}}""".toByteArray()
            ex.sendResponseHeaders(401, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        // Use a client whose refresh also fails (expired token, no refresh server)
        val store = InMemoryTokenStore(
            OAuth2Token("bad-token", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(store, ClientCreds("c", "s"),
            baseApiUrl = "http://localhost:$port",
            tokenEndpoint = "http://localhost:1/nonexistent"
        )
        val result = DriveClient(api).filesList("q")
        assertTrue(result.isFailure)
    }
}
