package jarvis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Append-only lesson event log. Persists every [[lesson]] invocation
 * + every [[lesson_check]] reply so:
 *   1. Multi-turn lessons are stitchable across separate chat turns
 *      without parsing prior conversation history.
 *   2. FSRS / KnowledgeState can later consume "concepts the user got
 *      a CHECK question wrong on" as weight signal.
 *   3. The user can run [[lesson_status]] to see what's in flight.
 *
 * Schema kept minimal:
 *   {id, ts, subject, concept, stage, kind, content?, v=1}
 *
 * kind ∈ {"spec", "check", "graded", "completed"}.
 *   spec       — initial lesson body emitted by [[lesson]] (full 4-section)
 *   check      — user's attempt at the DRILL or answer to CHECK
 *   graded     — bot's feedback on user's attempt
 *   completed  — explicit close (e.g., next concept queued)
 *
 * stage ∈ {1..5} — 1 spec emitted, 2 user replied, 3 graded, 4 next
 * suggested, 5 closed. Not strict; consumers pick latest by ts.
 *
 * id = sha256(subject|concept|kind|ts)[:16]. Idempotent on identical
 * (subject, concept, kind, ts) — same payload on re-run collapses.
 */
@Serializable
data class LessonEvent(
    val id: String,
    val ts: String,
    val subject: String,
    val concept: String,
    val stage: Int,
    val kind: String,
    val content: String? = null,
    val v: Int = 1,
)

object Lessons {
    val ALLOWED_KINDS = setOf("spec", "check", "graded", "completed")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun computeId(subject: String, concept: String, kind: String, ts: String): String {
        val raw = "$subject|$concept|$kind|$ts"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
    }

    fun append(entry: LessonEvent) = appendTo(Config.lessonsFile, entry)

    fun appendTo(file: Path, entry: LessonEvent) {
        require(entry.kind in ALLOWED_KINDS) {
            "kind '${entry.kind}' not in $ALLOWED_KINDS"
        }
        val line = json.encodeToString(LessonEvent.serializer(), entry) + "\n"
        synchronized(LOCK) {
            JsonlRotate.maybeRotate(file)
            file.parent?.createDirectories()
            Files.writeString(
                file, line, Charsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND,
            )
        }
    }

    fun readAll(): List<LessonEvent> = readAllFrom(Config.lessonsFile)

    fun readAllFrom(file: Path): List<LessonEvent> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<LessonEvent>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(LessonEvent.serializer(), line)
                    if (entry.v != 1) return@forEach
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    /** Latest event for the (subject, concept) pair, or null. Used by
     *  [[lesson_check]] to find which lesson the user's reply belongs to.
     *  Defaults to the most recent SPEC across all subjects when args
     *  omitted. */
    fun latestSpec(subject: String? = null, concept: String? = null): LessonEvent? {
        val all = readAll().filter { it.kind == "spec" }
        val filtered = when {
            subject != null && concept != null ->
                all.filter { it.subject == subject && it.concept == concept }
            subject != null -> all.filter { it.subject == subject }
            else -> all
        }
        return filtered.maxByOrNull { it.ts }
    }

    /** All events for a (subject, concept) ordered by ts. */
    fun history(subject: String, concept: String): List<LessonEvent> =
        readAll().filter { it.subject == subject && it.concept == concept }
            .sortedBy { it.ts }
}
