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
 * Append-only grades ledger. Every `[[grade_record]]` chat tool call
 * writes one row. Schema:
 *   {id, ts, subject, component, earned, max, note?, v=1}
 *
 * id = sha256(subject|component)[:16] — repeated record-of-same-component
 * COLLAPSES so the latest value wins on read. Use a different `component`
 * string when you intentionally want a separate grade row (e.g.
 * "Tema 1 — re-graded 2026-05-15" vs "Tema 1").
 *
 * Why a separate ledger from FSRS / KnowledgeState: grades feed an
 * importance-weighting signal for catch-up + planning ("user is failing
 * POO, surface POO concepts more"), which is a different cadence than
 * passive concept exposure or graded-recall reviews.
 */
@Serializable
data class GradeEntry(
    val id: String,
    val ts: String,
    val subject: String,
    val component: String,
    val earned: Double,
    val max: Double,
    val note: String? = null,
    val v: Int = 1,
)

@Serializable
data class SubjectGradeSummary(
    val subject: String,
    val totalEarned: Double,
    val totalMax: Double,
    val ratio: Double,        // totalEarned / totalMax, NaN-safe (0.0 if max=0)
    val componentCount: Int,
    val latestTs: String?,
)

object Grades {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun computeId(subject: String, component: String): String {
        val raw = "$subject|$component"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
    }

    fun record(
        subject: String,
        component: String,
        earned: Double,
        max: Double,
        note: String? = null,
        ts: Instant = Instant.now(),
    ): GradeEntry = recordTo(Config.gradesFile, subject, component, earned, max, note, ts)

    fun recordTo(
        file: Path,
        subject: String,
        component: String,
        earned: Double,
        max: Double,
        note: String?,
        ts: Instant,
    ): GradeEntry {
        val entry = GradeEntry(
            id = computeId(subject, component),
            ts = ts.toString(),
            subject = subject,
            component = component,
            earned = earned,
            max = max,
            note = note,
        )
        synchronized(LOCK) {
            JsonlRotate.maybeRotate(file)
            file.parent?.createDirectories()
            Files.writeString(
                file,
                json.encodeToString(GradeEntry.serializer(), entry) + "\n",
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
        return entry
    }

    fun readAll(): List<GradeEntry> = readAllFrom(Config.gradesFile)

    fun readAllFrom(file: Path): List<GradeEntry> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<GradeEntry>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(GradeEntry.serializer(), line)
                    if (entry.v != 1) return@forEach
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    /** Latest-row-wins per (subject, component). Older rows for the same
     *  component are masked, so re-recording a corrected grade just
     *  appends a new row and the consumer sees only the corrected one. */
    fun latestPerComponent(file: Path = Config.gradesFile): List<GradeEntry> {
        val all = readAllFrom(file)
        return all.groupBy { it.id }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.ts }!! }
            .values
            .toList()
            .sortedWith(compareBy({ it.subject }, { it.component }))
    }

    /** Per-subject earned/max rollup. NaN ratios become 0.0. */
    fun summaryBySubject(file: Path = Config.gradesFile): List<SubjectGradeSummary> {
        val latest = latestPerComponent(file)
        return latest.groupBy { it.subject }
            .map { (subject, rows) ->
                val totalEarned = rows.sumOf { it.earned }
                val totalMax = rows.sumOf { it.max }
                val ratio = if (totalMax > 0.0) totalEarned / totalMax else 0.0
                val latestTs = rows.maxByOrNull { it.ts }?.ts
                SubjectGradeSummary(
                    subject = subject,
                    totalEarned = totalEarned,
                    totalMax = totalMax,
                    ratio = ratio,
                    componentCount = rows.size,
                    latestTs = latestTs,
                )
            }
            .sortedBy { it.ratio }     // weakest subjects first → catch-up bias
    }
}
