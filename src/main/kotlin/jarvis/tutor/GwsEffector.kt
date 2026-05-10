package jarvis.tutor

import kotlinx.serialization.json.JsonObject
import java.util.concurrent.TimeUnit

/**
 * Tutor task-context V1 (Stage E) — Google Workspace CLI effector.
 *
 * Adopts the @googleworkspace/cli (npm install -g @googleworkspace/cli)
 * binary as a subprocess effector. The CLI handles OAuth + OS keyring
 * credential storage; this wrapper just dispatches structured commands
 * and parses JSON output.
 *
 * Council R3 spec deviation: the gws binary runs ON THE SERVER (VPS),
 * not the daemon — the user's Google account credentials live on the
 * VPS keyring (already used for OPENROUTER_API_KEY). Avoids needing
 * the Rust daemon to add a Google-OAuth dependency.
 *
 * Available subcommands wrapped here for V1:
 *   - calendar.events.insert(summary, start, end)
 *   - calendar.events.list(timeMin, timeMax)
 *   - drive.files.list(query, pageSize)
 *   - drive.files.get(fileId)
 *   - gmail.drafts.create(to, subject, body)  — never auto-sends
 *
 * Defense: subprocess inherits ONLY the env vars we explicitly pass
 * (no JARVIS_AUTH_TOKEN, JARVIS_DAEMON_SECRET, OPENROUTER_API_KEY,
 * etc). All input is JSON-encoded into the gws --json arg. 30s
 * timeout. Stderr captured for diagnostics.
 *
 * Disabled by default — set `GWS_ENABLED=1` env to allow these tools.
 * When disabled, dispatch returns a stub message so the LLM knows
 * the surface is documented but not active.
 */
object GwsEffector {

    private const val DEFAULT_TIMEOUT_S = 30L

    private val safeEnv = setOf(
        "PATH", "HOME", "USER", "TEMP", "TMP", "SystemRoot",
        // gws-specific config dir overrides
        "XDG_CONFIG_HOME", "GWS_CONFIG_DIR", "APPDATA",
    )

    fun isEnabled(): Boolean =
        System.getenv("GWS_ENABLED")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false

    sealed class Result {
        data class Ok(val stdout: String, val parsed: JsonObject?) : Result()
        data class Err(val exitCode: Int, val stderr: String) : Result()
    }

    /** Compat shim — delegates to GoogleApiClient. Kept temporarily so any
     *  future caller that still uses GwsEffector.run() is not broken.
     *  New code uses GoogleClients directly. Scheduled for deletion post-Slice-1. */
    fun run(
        subcommand: String,
        params: Map<String, Any>? = null,
        body: Map<String, Any>? = null,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_S,
    ): Result {
        val parts = subcommand.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Result.Err(0, "subcommand required")
        return try {
            when {
                parts.size >= 3 && parts[0] == "calendar" && parts[2] == "insert" -> {
                    val summary = (body?.get("summary") as? String).orEmpty()
                    val startIso = ((body?.get("start") as? Map<*, *>)?.get("dateTime") as? String).orEmpty()
                    val endIso = ((body?.get("end") as? Map<*, *>)?.get("dateTime") as? String).orEmpty()
                    val calId = (params?.get("calendarId") as? String) ?: "primary"
                    val r = jarvis.tutor.GoogleClients.calendar()
                        .eventsInsert(calId, summary, startIso, endIso)
                    if (r.isSuccess) Result.Ok("""{"id":"${r.getOrThrow()}"}""", null)
                    else Result.Err(0, r.exceptionOrNull()?.message?.take(400).orEmpty())
                }
                parts.size >= 3 && parts[0] == "drive" && parts[2] == "list" -> {
                    val q = (params?.get("q") as? String).orEmpty()
                    val ps = (params?.get("pageSize") as? Int) ?: 5
                    val r = jarvis.tutor.GoogleClients.drive().filesList(q, ps)
                    if (r.isSuccess) {
                        val json = r.getOrThrow().joinToString(",") {
                            """{"id":"${it.id}","name":"${it.name}"}"""
                        }
                        Result.Ok("""{"files":[$json]}""", null)
                    } else Result.Err(0, r.exceptionOrNull()?.message?.take(400).orEmpty())
                }
                parts.size >= 4 && parts[0] == "gmail" && parts[2] == "drafts" -> {
                    val raw = (body?.get("message") as? Map<*, *>)?.get("raw") as? String
                    if (raw == null) return Result.Err(0, "gmail shim: message.raw required")
                    val decoded = String(java.util.Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
                    val to = decoded.lines().firstOrNull { it.startsWith("To:") }
                        ?.removePrefix("To:")?.trim().orEmpty()
                    val subjectLine = decoded.lines().firstOrNull { it.startsWith("Subject:") }
                        ?.removePrefix("Subject:")?.trim().orEmpty()
                    val bodyText = decoded.substringAfter("\r\n\r\n", "")
                    val r = jarvis.tutor.GoogleClients.gmail().draftsCreate(to, subjectLine, bodyText)
                    if (r.isSuccess) Result.Ok("""{"id":"${r.getOrThrow()}"}""", null)
                    else Result.Err(0, r.exceptionOrNull()?.message?.take(400).orEmpty())
                }
                else -> Result.Err(0, "GwsEffector shim: unsupported subcommand '$subcommand' (use GoogleClients directly)")
            }
        } catch (e: Exception) {
            Result.Err(-1, "GwsEffector shim error: ${e.message?.take(400)}")
        }
    }

    /**
     * Lightweight health probe: reports whether (a) the GWS_ENABLED env
     * is set, (b) a `gws` binary is on PATH, and (c) `gws auth status`
     * exits 0. Used by the `/api/v1/gws/status` route so the user can
     * tell from the UI whether the workspace tools will actually fire.
     *
     * Interactive login (`gws auth login`) still requires the user to
     * run it on the VPS — we cannot drive a browser-based OAuth flow
     * from a long-running headless process. The status endpoint just
     * surfaces "logged in / not logged in" so the LLM doesn't try to
     * call calendar/drive tools that will fail.
     */
    data class Health(
        val enabled: Boolean,
        val binaryFound: Boolean,
        val authenticated: Boolean,
        val detail: String,
    )

    fun health(): Health {
        val enabled = isEnabled()
        if (!enabled) {
            return Health(false, false, false,
                "set GWS_ENABLED=1 then run `gws auth login` on the server")
        }
        val binaryFound = isBinaryOnPath()
        if (!binaryFound) {
            return Health(true, false, false,
                "install @googleworkspace/cli: `npm install -g @googleworkspace/cli`")
        }
        val (exit, stderr) = runProbe(listOf("gws", "auth", "status", "--format", "json"))
        return Health(
            enabled = true,
            binaryFound = true,
            authenticated = exit == 0,
            detail = if (exit == 0) "ok" else "run `gws auth login` on the server (exit=$exit, ${stderr.lines().firstOrNull { it.isNotBlank() }?.take(200).orEmpty()})",
        )
    }

    internal fun isBinaryOnPath(): Boolean {
        val path = System.getenv("PATH") ?: return false
        val sep = if (System.getProperty("os.name").lowercase().contains("win")) ";" else ":"
        val candidates = if (System.getProperty("os.name").lowercase().contains("win"))
            listOf("gws.cmd", "gws.exe", "gws") else listOf("gws")
        for (dir in path.split(sep)) {
            for (cand in candidates) {
                val p = java.nio.file.Paths.get(dir, cand)
                if (java.nio.file.Files.isRegularFile(p)) return true
            }
        }
        return false
    }

    private fun runProbe(cmd: List<String>): Pair<Int, String> {
        val pb = ProcessBuilder(cmd).redirectErrorStream(false)
        val env = pb.environment()
        env.keys.retainAll { it in safeEnv }
        val proc = try { pb.start() } catch (e: Exception) {
            return -1 to "probe-launch-failed: ${e.javaClass.simpleName}: ${e.message?.take(120)}"
        }
        val finished = proc.waitFor(8, TimeUnit.SECONDS)
        if (!finished) { proc.destroyForcibly(); return -1 to "probe timeout" }
        val stderr = proc.errorStream.bufferedReader().readText().take(800)
        return proc.exitValue() to stderr
    }

}
