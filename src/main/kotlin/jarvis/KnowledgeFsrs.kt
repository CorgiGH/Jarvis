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
 * Cycle 7 (fresh-eyes idea agent 2026-05-08): wires [Fsrs] to [KnowledgeState]
 * via a sidecar `knowledge_fsrs.jsonl` ledger. Each `[[grade]]` chat tool
 * call writes a row; latest-row-wins on read.
 *
 * Schema:
 *   {concept, subject, ts, difficulty, stability, lastReviewDayOffset, v=1}
 *
 * Why a sidecar rather than embedding in KnowledgeEntry:
 *  - Different cadence: every grade vs every passive activity touch.
 *  - Different lifecycle: FSRS state is an aggregate, not an event.
 *  - Backward compat: existing knowledge.jsonl readers untouched.
 */
@Serializable
data class FsrsRow(
    val concept: String,
    val subject: String,
    val ts: String,
    val difficulty: Double,
    val stability: Double,
    val lastReviewDayOffset: Double = 0.0,
    val v: Int = 1,
)

object KnowledgeFsrs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LOCK = Any()

    fun stateFor(concept: String, subject: String, file: Path = Config.knowledgeFsrsFile): Fsrs.State? {
        val rows = readAllFrom(file)
            .filter { it.concept == concept && it.subject == subject }
        val latest = rows.maxByOrNull { it.ts } ?: return null
        return Fsrs.State(
            difficulty = latest.difficulty,
            stability = latest.stability,
            lastReviewDayOffset = latest.lastReviewDayOffset,
        )
    }

    fun lastReviewTs(concept: String, subject: String, file: Path = Config.knowledgeFsrsFile): Instant? {
        val rows = readAllFrom(file)
            .filter { it.concept == concept && it.subject == subject }
        val latest = rows.maxByOrNull { it.ts } ?: return null
        return runCatching { Instant.parse(latest.ts) }.getOrNull()
    }

    /** Run a graded review. Loads prior state (or initial), calls Fsrs.update,
     *  writes new row, also calls KnowledgeState.touch with weight = grade/4
     *  so legacy confidence stays consistent for non-FSRS-aware code paths.
     *
     *  Council 2026-05-08 (rounds 2 + 3, RA): the read-compute-append
     *  sequence below MUST run under [LOCK] to close the TOCTOU window
     *  where two concurrent /api/chat calls grading the same concept
     *  read the same prior state and last-writer wins. Without this,
     *  one of the two graded reviews silently disappears.
     *
     *  KnowledgeState.touchTo is hoisted OUT of the synchronized block
     *  because it holds its own (independent) lock on knowledge.jsonl;
     *  nesting is unnecessary and would extend our hot section across
     *  two file IO operations. */
    fun recordReview(
        concept: String,
        subject: String,
        grade: Int,
        now: Instant = Instant.now(),
        file: Path = Config.knowledgeFsrsFile,
        knowledgeFile: Path = Config.knowledgeFile,
    ): Fsrs.State {
        val newState = synchronized(LOCK) {
            val prior = stateFor(concept, subject, file)
            val priorTs = lastReviewTs(concept, subject, file)
            val elapsed = priorTs?.let {
                Duration.between(it, now).toMinutes() / (60.0 * 24.0)
            } ?: 0.0
            val computed = if (prior == null) {
                Fsrs.initial(grade)
            } else {
                Fsrs.update(prior, grade, elapsed)
            }
            appendTo(file, FsrsRow(
                concept = concept, subject = subject, ts = now.toString(),
                difficulty = computed.difficulty, stability = computed.stability,
                lastReviewDayOffset = computed.lastReviewDayOffset,
            ))
            computed
        }
        val weight = grade.coerceIn(1, 4) / 4.0f
        KnowledgeState.touchTo(knowledgeFile, concept, subject, "review", weight, now)
        // Tutor task-context V0: bump state version so any cached
        // TaskHeader snapshot is invalidated on the next /api/chat read.
        jarvis.tutor.StateVersion.bump()
        return newState
    }

    fun appendTo(file: Path, row: FsrsRow) {
        val line = json.encodeToString(FsrsRow.serializer(), row) + "\n"
        synchronized(LOCK) {
            JsonlRotate.maybeRotate(file)
            file.parent?.createDirectories()
            Files.writeString(
                file, line, Charsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND,
            )
        }
    }

    fun readAllFrom(file: Path): List<FsrsRow> {
        if (!file.exists()) return emptyList()
        val out = mutableListOf<FsrsRow>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                try {
                    val entry = json.decodeFromString(FsrsRow.serializer(), line)
                    if (entry.v != 1) return@forEach
                    out += entry
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    /** Concepts due for review per FSRS retrievability < threshold. */
    fun dueConcepts(
        threshold: Double = 0.7,
        now: Instant = Instant.now(),
        file: Path = Config.knowledgeFsrsFile,
    ): List<Pair<FsrsRow, Double>> {
        val all = readAllFrom(file)
        // Latest row per (concept, subject)
        val latest = all.groupBy { it.concept to it.subject }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.ts }!! }
        return latest.values.mapNotNull { row ->
            val priorTs = runCatching { Instant.parse(row.ts) }.getOrNull() ?: return@mapNotNull null
            val elapsedDays = Duration.between(priorTs, now).toMinutes() / (60.0 * 24.0)
            val r = Fsrs.retrievability(row.stability, elapsedDays)
            if (r < threshold) row to r else null
        }.sortedBy { it.second }
    }
}
