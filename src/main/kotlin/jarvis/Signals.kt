package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Phase 2.1 — proactive signal row written by [ProactiveLoop] when an
 * activity event crosses the importance threshold.
 *
 * Schema (council 1778165183 synthesis — minimal Pragmatist + hash-id Domain):
 *   id        sha256(sourceTs + hourBucket) — deterministic for restart-replay dedup.
 *   ts        emit instant.
 *   kind      "ctx_model_summary" or "error".
 *   importance triggering activity importance.
 *   sourceTs  the ActivityEntry.ts that triggered this signal.
 *   snippet   subsystem output capped at 200 chars (or error message).
 *   rationale why the loop fired ("imp=0.85, cooldown=elapsed, quiet=no").
 *   status    "emitted" or "error".
 *   v=1       schema version.
 */
@Serializable
data class ProactiveSignal(
    val id: String,
    val ts: String,
    val kind: String,
    val importance: Float,
    val sourceTs: String,
    val snippet: String,
    val rationale: String,
    val status: String = "emitted",
    val v: Int = 1,
    /** R1 — for `kind="reflection"` rows, the signal IDs that fed into this
     *  higher-order summary. Null for first-order (event-triggered) signals. */
    val parentIds: List<String>? = null,
)

object Signals {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun append(entry: ProactiveSignal) {
        appendTo(Config.signalsFile, entry)
    }

    fun appendTo(file: Path, entry: ProactiveSignal) {
        appendToWithCap(file, entry, JsonlRotate.DEFAULT_MAX_BYTES)
    }

    /** Test-friendly overload exposing the rotation byte cap so harnesses
     *  can force several rotations mid-stream without writing 10 MB of
     *  fixture data. Production callers go through [appendTo]. */
    fun appendToWithCap(file: Path, entry: ProactiveSignal, maxBytes: Long) {
        val line = json.encodeToString(ProactiveSignal.serializer(), entry) + "\n"
        synchronized(LOCK) {
            JsonlRotate.maybeRotate(file, maxBytes)
            file.parent?.createDirectories()
            Files.writeString(
                file,
                line,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    fun readAll(): List<ProactiveSignal> = readAllFrom(Config.signalsFile)

    fun readAllFrom(file: Path): List<ProactiveSignal> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<ProactiveSignal>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(ProactiveSignal.serializer(), line)
                    if (entry.v != 1) {
                        System.err.println(
                            "[Signals] WARN skipping unknown schema version v=${entry.v} (id=${entry.id})",
                        )
                        return@forEach
                    }
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    /** Last *emitted* signal, used by ProactiveLoop to evaluate cooldown.
     *  Error rows do NOT extend cooldown — a failed tick should not silence
     *  a real burst of high-importance activity. */
    fun lastEmittedFrom(file: Path): ProactiveSignal? =
        readAllFrom(file).lastOrNull { it.status == "emitted" }

    /** Cooldown helper: returns true if at least [duration] has elapsed since
     *  the last emitted signal in [file], or if the file is empty / unreadable. */
    fun cooldownElapsedFrom(file: Path, duration: Duration, now: Instant): Boolean {
        val last = lastEmittedFrom(file) ?: return true
        val lastTs = runCatching { Instant.parse(last.ts) }.getOrNull() ?: return true
        return Duration.between(lastTs, now) >= duration
    }
}
