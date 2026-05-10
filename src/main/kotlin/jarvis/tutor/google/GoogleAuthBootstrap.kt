package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * One-time OAuth 2.0 authorization code flow for jarvis.
 *
 * Runs on the USER'S PC (browser-capable), not on the VPS.
 * After consent, outputs token JSON to stdout so the user
 * can scp it to the VPS.
 *
 * Scopes:
 *   - https://www.googleapis.com/auth/calendar.events
 *   - https://www.googleapis.com/auth/drive.readonly
 *   - https://www.googleapis.com/auth/gmail.compose
 */
object GoogleAuthBootstrap {

    private const val CALLBACK_PORT = 9999
    private const val CALLBACK_TIMEOUT_SECONDS = 120L

    private val SCOPES = listOf(
        "https://www.googleapis.com/auth/calendar.events",
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/gmail.compose",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Full interactive bootstrap. Opens browser, waits for callback, exchanges
     * code for tokens, prints JSON to [out].
     *
     * @param credsPath  Path to client_secrets.json downloaded from Google Console.
     * @param tokenPath  Output path for google-token.json (also printed to stdout).
     * @param out        Output sink (defaults to System.out for normal CLI use).
     * @param tokenEndpoint Overridable for tests.
     */
    fun run(
        credsPath: java.nio.file.Path,
        tokenPath: java.nio.file.Path,
        out: java.io.PrintStream = System.out,
        tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    ) {
        val credsJson = credsPath.toFile().readText()
        val root = json.parseToJsonElement(credsJson).jsonObject
        val block = (root["installed"] ?: root["web"])?.jsonObject
            ?: error("client_secrets.json: missing 'installed' or 'web' block")
        val clientId = block["client_id"]?.jsonPrimitive?.content
            ?: error("client_secrets.json: missing client_id")
        val clientSecret = block["client_secret"]?.jsonPrimitive?.content
            ?: error("client_secrets.json: missing client_secret")
        val creds = ClientCreds(clientId, clientSecret)

        val redirectUri = "http://localhost:$CALLBACK_PORT/callback"
        val state = java.util.UUID.randomUUID().toString().take(16)
        val consentUrl = buildConsentUrl(creds, redirectUri, state)

        out.println("=== JARVIS Google OAuth Bootstrap ===")
        out.println("Opening browser for consent. If it doesn't open automatically, visit:")
        out.println("  $consentUrl")
        out.println("Waiting for callback on $redirectUri (timeout ${CALLBACK_TIMEOUT_SECONDS}s)...")

        try {
            java.awt.Desktop.getDesktop().browse(URI(consentUrl))
        } catch (_: Exception) {
            out.println("(Could not open browser automatically — please open the URL above manually)")
        }

        val code = waitForCallback(state, redirectUri)
            ?: error("OAuth callback timed out or returned an error. Re-run and complete consent within ${CALLBACK_TIMEOUT_SECONDS}s.")

        out.println("Received auth code. Exchanging for tokens...")
        val token = exchangeCode(code, creds, redirectUri, tokenEndpoint)

        val tokenJson = serializeToken(token)
        out.println("\n=== TOKEN JSON (save as google-token.json) ===")
        out.println(tokenJson)
        out.println("==============================================")
        out.println("Next: scp the file above to /opt/jarvis/data/google-token.json on the VPS")
        out.println("Then set GWS_ENABLED=1 in /opt/jarvis/.env and restart the service.")

        // Also write to tokenPath so user can scp directly
        tokenPath.parent?.toFile()?.mkdirs()
        tokenPath.toFile().writeText(tokenJson)
        out.println("Token also written to: $tokenPath")
    }

    /** Build Google OAuth 2.0 consent URL. */
    fun buildConsentUrl(creds: ClientCreds, redirectUri: String, state: String): String {
        val scopeParam = urlEncode(SCOPES.joinToString(" "))
        val redirectParam = urlEncode(redirectUri)
        val stateParam = urlEncode(state)
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${urlEncode(creds.clientId)}" +
            "&redirect_uri=$redirectParam" +
            "&response_type=code" +
            "&scope=$scopeParam" +
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=$stateParam"
    }

    /**
     * Spin up a local [HttpServer] on [CALLBACK_PORT], wait for the OAuth
     * redirect callback, and return the authorization code.
     * Returns null on timeout or error.
     */
    private fun waitForCallback(expectedState: String, redirectUri: String): String? {
        val codeLatch = CountDownLatch(1)
        val codeRef = AtomicReference<String?>(null)

        val server = HttpServer.create(InetSocketAddress(CALLBACK_PORT), 0)
        server.createContext("/callback") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            val params = parseQueryString(query)
            val code = params["code"]
            val state = params["state"]
            val responseBody: String
            if (code != null && state == expectedState) {
                codeRef.set(code)
                responseBody = "<html><body><h2>Authorization successful!</h2>" +
                    "<p>You can close this tab and return to the terminal.</p></body></html>"
            } else {
                responseBody = "<html><body><h2>Error</h2>" +
                    "<p>Missing code or state mismatch. Please retry.</p></body></html>"
            }
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            codeLatch.countDown()
        }
        server.start()
        try {
            codeLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            server.stop(0)
        }
        return codeRef.get()
    }

    /**
     * Exchange an authorization [code] for an [OAuth2Token] via the token endpoint.
     * Exposed for testing.
     */
    fun exchangeCode(
        code: String,
        creds: ClientCreds,
        redirectUri: String,
        tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    ): OAuth2Token {
        val form = "code=${urlEncode(code)}" +
            "&client_id=${urlEncode(creds.clientId)}" +
            "&client_secret=${urlEncode(creds.clientSecret)}" +
            "&redirect_uri=${urlEncode(redirectUri)}" +
            "&grant_type=authorization_code"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val http = HttpClient.newHttpClient()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            error("Token exchange failed (HTTP ${resp.statusCode()}): ${resp.body().take(400)}")
        }
        val body = json.parseToJsonElement(resp.body()).jsonObject
        val accessToken = body["access_token"]?.jsonPrimitive?.content
            ?: error("Token response missing access_token")
        val refreshToken = body["refresh_token"]?.jsonPrimitive?.content
            ?: error("Token response missing refresh_token — ensure access_type=offline and prompt=consent")
        val expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        return OAuth2Token(accessToken, refreshToken, Instant.now().plusSeconds(expiresIn))
    }

    /** Serialize token to JSON string for stdout + file output. */
    fun serializeToken(token: OAuth2Token): String =
        Json { prettyPrint = true; encodeDefaults = true }.encodeToString(token)

    private fun parseQueryString(query: String): Map<String, String> =
        query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
