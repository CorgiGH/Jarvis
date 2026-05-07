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
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.math.exp
import kotlin.math.ln

/**
 * S2 — append-only ledger tracking concept-level exposure + confidence.
 *
 * Two sub-systems:
 *   - **ConceptCatalog** — enumerates known concepts by walking the archival
 *     study-guide markdown corpus and extracting `## ` h2 headings. Cached
 *     in-memory after first read; cheap recompute since corpus is ~316 files.
 *   - **knowledge.jsonl** — append-only `{concept, subject, ts, source,
 *     weight}` rows. `staleness(concept)` = days since last touch.
 *     `confidence(concept)` = exponentially-decayed sum of recent weights.
 *
 * Designed to be read by S3 (StudyPlanner) + ctx-model.
 */
@Serializable
data class KnowledgeEntry(
    val concept: String,
    val subject: String,
    val ts: String,
    val source: String,        // "lecture" | "chat" | "activity" | "review" | "test"
    val weight: Float = 1.0f,
    val v: Int = 1,
)

@Serializable
data class ConceptStat(
    val concept: String,
    val subject: String,
    val lastSeenTs: String?,
    val staleDays: Long,
    val confidence: Float,
)

object KnowledgeState {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    /** Decay half-life for confidence — after 7 days unused, weight halves.
     *  Spaced-repetition-style: stale concepts surface for review. */
    private const val CONFIDENCE_HALF_LIFE_DAYS = 7.0

    fun touch(
        concept: String,
        subject: String,
        source: String,
        weight: Float = 1.0f,
        ts: Instant = Instant.now(),
    ) {
        touchTo(Config.knowledgeFile, concept, subject, source, weight, ts)
    }

    fun touchTo(
        file: Path,
        concept: String,
        subject: String,
        source: String,
        weight: Float,
        ts: Instant,
    ) {
        val entry = KnowledgeEntry(concept, subject, ts.toString(), source, weight)
        val line = json.encodeToString(KnowledgeEntry.serializer(), entry) + "\n"
        synchronized(LOCK) {
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

    fun readAll(): List<KnowledgeEntry> = readAllFrom(Config.knowledgeFile)

    fun readAllFrom(file: Path): List<KnowledgeEntry> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<KnowledgeEntry>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(KnowledgeEntry.serializer(), line)
                    if (entry.v != 1) return@forEach
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    /** Per-concept rollup. Confidence is exponentially-decayed sum of weights;
     *  older touches contribute less. */
    fun stats(now: Instant = Instant.now()): List<ConceptStat> =
        statsFrom(Config.knowledgeFile, now)

    fun statsFrom(file: Path, now: Instant): List<ConceptStat> {
        val all = readAllFrom(file)
        if (all.isEmpty()) return emptyList()
        val lambda = ln(2.0) / CONFIDENCE_HALF_LIFE_DAYS
        val grouped = all.groupBy { it.concept to it.subject }
        return grouped.map { (key, rows) ->
            val (concept, subject) = key
            val timestamps = rows.mapNotNull {
                runCatching { Instant.parse(it.ts) }.getOrNull()
            }
            val lastTs = timestamps.maxOrNull()
            val confidence = rows.fold(0.0) { acc, e ->
                val ts = runCatching { Instant.parse(e.ts) }.getOrNull() ?: return@fold acc
                val daysAgo = Duration.between(ts, now).toMinutes()
                    .coerceAtLeast(0).toDouble() / (60.0 * 24.0)
                acc + e.weight * exp(-lambda * daysAgo)
            }.toFloat().coerceAtMost(10.0f)  // soft cap so rare-concepts can compete
            val staleDays = lastTs?.let {
                Duration.between(it, now).toMinutes() / (60L * 24L)
            } ?: Long.MAX_VALUE
            ConceptStat(
                concept = concept,
                subject = subject,
                lastSeenTs = lastTs?.toString(),
                staleDays = staleDays,
                confidence = confidence,
            )
        }.sortedBy { it.confidence }
    }

    /** Concepts due for review: confidence < threshold + last-seen ≥ minStaleDays.
     *  Fed by S3 StudyPlanner into "today's revision queue". */
    fun staleConcepts(
        confidenceFloor: Float = 0.3f,
        minStaleDays: Long = 3L,
        now: Instant = Instant.now(),
    ): List<ConceptStat> = stats(now)
        .filter { it.confidence < confidenceFloor && it.staleDays >= minStaleDays }
        .sortedBy { it.confidence }
}

/**
 * Builds the catalog of known concepts by walking the archival study-guide
 * markdown. Heuristics:
 *  - First-level path component under archival → subject (e.g. "Probability/")
 *  - `^## ` lines → concept names within that subject's files
 *  - Skip files under "tests/" subdirectory (they're exam papers, not concepts)
 */
object ConceptCatalog {

    @Volatile private var cached: List<Concept>? = null

    @Serializable
    data class Concept(
        val name: String,
        val subject: String,
        val sourcePath: String,
    )

    fun all(root: Path = Config.archivalDir): List<Concept> {
        cached?.let { return it }
        if (!root.exists()) {
            cached = emptyList()
            return emptyList()
        }
        val out = mutableListOf<Concept>()
        Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.extension.equals("md", ignoreCase = true) }
                .forEach { path ->
                    val rel = runCatching { root.relativize(path).toString() }
                        .getOrElse { path.toString() }
                        .replace('\\', '/')   // Windows path-sep normalization
                    if (rel.contains("tests/") || rel.startsWith("tests/")) return@forEach
                    val subject = rel.substringBefore('/').takeIf { it.isNotEmpty() && it != rel }
                        ?: return@forEach
                    val lines = try {
                        path.readLines(Charsets.UTF_8)
                    } catch (_: Exception) {
                        return@forEach
                    }
                    for (line in lines) {
                        if (line.startsWith("## ")) {
                            val name = line.removePrefix("## ").trim()
                            if (name.isNotEmpty()) {
                                out += Concept(name, subject, rel)
                            }
                        }
                    }
                }
        }
        cached = out.distinctBy { it.name to it.subject }
        return cached!!
    }

    fun invalidate() {
        cached = null
    }

    /** Find concepts whose name appears in [text] (case-insensitive substring).
     *  Used by S4 active-doc detector. Bounded by concept length to avoid
     *  short-token false positives — only concepts ≥4 chars participate. */
    fun matchInText(text: String, root: Path = Config.archivalDir): List<Concept> {
        val lower = text.lowercase()
        return all(root).filter { c ->
            c.name.length >= 4 && lower.contains(c.name.lowercase())
        }
    }
}
