package jarvis

import io.github.cdimascio.dotenv.dotenv
import jarvis.win32.Win32
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories

/** Captures the currently-active window and either posts the entry to a
 *  remote jarvis backend (when JARVIS_BACKEND_URL + JARVIS_AUTH_TOKEN are
 *  set) or appends it to the local activity.jsonl. POST failures fall back
 *  to local writes so a temporarily unreachable VPS does not lose minutes. */
object ActivityCapture {
    private val json = Json { encodeDefaults = true }

    /** Resolve env from process env first, then from a `.env` in cwd. The
     *  always-on logger is launched via Startup-folder shortcut with cwd
     *  set to C:\Users\User\jarvis-kotlin so the .env right next to the
     *  build is picked up automatically. */
    private fun env(name: String): String {
        System.getenv(name)?.takeIf { it.isNotBlank() }?.let { return it }
        return try {
            val e = dotenv {
                ignoreIfMissing = true
                ignoreIfMalformed = true
            }
            e[name]?.takeIf { it.isNotBlank() }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private val backendUrl: String = env("JARVIS_BACKEND_URL").trimEnd('/')
    private val authToken: String = env("JARVIS_AUTH_TOKEN")

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    fun snapshot(): ActivityEntry {
        val w = Win32.activeWindow()
        val entry = ActivityEntry(
            ts = Instant.now().toString(),
            title = w.title,
            process = w.processName,
            pid = w.pid,
        )

        if (backendUrl.isNotEmpty() && authToken.isNotEmpty()) {
            try {
                postEntry(entry)
                return entry
            } catch (e: Exception) {
                System.err.println(
                    "[activity_logger] backend POST failed (${e.javaClass.simpleName}: " +
                        "${e.message?.take(160)}); falling back to local file",
                )
                // fall through to local write
            }
        }
        appendLocal(entry)
        return entry
    }

    private fun postEntry(entry: ActivityEntry) {
        val body = json.encodeToString(ActivityEntry.serializer(), entry)
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$backendUrl/api/activity"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $authToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()

        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        val code = resp.statusCode()
        if (code !in 200..299) {
            error("backend ${code}: ${resp.body().take(200)}")
        }
    }

    private fun appendLocal(entry: ActivityEntry) {
        Config.stateDir.createDirectories()
        val line = json.encodeToString(ActivityEntry.serializer(), entry) + "\n"
        Files.writeString(
            Config.activityFile,
            line,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }
}
