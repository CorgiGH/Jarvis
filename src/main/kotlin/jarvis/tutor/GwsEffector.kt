package jarvis.tutor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

    /** Run `gws <subcommand>` with structured args. Returns parsed
     *  JSON object on success, or stderr excerpt on failure. */
    fun run(
        subcommand: String,
        params: Map<String, Any>? = null,
        body: Map<String, Any>? = null,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_S,
    ): Result {
        if (!isEnabled()) {
            return Result.Err(0,
                "gws disabled — set GWS_ENABLED=1 + ensure `gws auth login` succeeded on this machine")
        }
        val parts = subcommand.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Result.Err(0, "subcommand required")
        // Only allow known subcommand prefixes.
        val first = parts[0]
        if (first !in setOf("calendar", "drive", "gmail", "sheets", "docs")) {
            return Result.Err(0, "subcommand not allowed: $first (allow: calendar/drive/gmail/sheets/docs)")
        }
        val cmd = mutableListOf("gws").apply { addAll(parts); add("--format"); add("json") }
        if (params != null) {
            cmd += "--params"; cmd += JSON.encodeToString(JsonObject.serializer(), mapToJsonObject(params))
        }
        if (body != null) {
            cmd += "--json"; cmd += JSON.encodeToString(JsonObject.serializer(), mapToJsonObject(body))
        }

        val pb = ProcessBuilder(cmd).redirectErrorStream(false)
        val env = pb.environment()
        env.keys.retainAll { it in safeEnv }
        val proc = try { pb.start() } catch (e: Exception) {
            return Result.Err(-1, "gws launch failed: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
        }
        val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return Result.Err(-1, "gws timeout after ${timeoutSeconds}s")
        }
        val stdout = proc.inputStream.bufferedReader().readText().take(20_000)
        val stderr = proc.errorStream.bufferedReader().readText().take(2_000)
        if (proc.exitValue() != 0) {
            return Result.Err(proc.exitValue(), stderr.ifBlank { "gws exit ${proc.exitValue()}" })
        }
        val parsed = try { JSON.parseToJsonElement(stdout) as? JsonObject } catch (_: Exception) { null }
        return Result.Ok(stdout, parsed)
    }

    private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun mapToJsonObject(m: Map<String, Any>): JsonObject {
        val out = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for ((k, v) in m) {
            out[k] = when (v) {
                is String -> JsonPrimitive(v)
                is Int -> JsonPrimitive(v)
                is Long -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is Double -> JsonPrimitive(v)
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") mapToJsonObject(v as Map<String, Any>)
                is JsonObject -> v
                else -> JsonPrimitive(v.toString())
            }
        }
        return JsonObject(out)
    }
}
