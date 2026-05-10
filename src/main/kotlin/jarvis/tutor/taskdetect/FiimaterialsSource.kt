package jarvis.tutor.taskdetect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.streams.toList

/**
 * Reads the Phase 5 crawler's output (_extras/<subject>/extracted/<sha8>/meta.json)
 * and surfaces fetched PDFs as low-priority "reading" tasks. Deadlines
 * are placeholders (now + 14 days) — the real "due" signal comes from
 * other sources; this one just makes the corpus visible in the dashboard.
 */
class FiimaterialsSource(private val extrasRoot: Path) : TaskDetector {
    override val sourceId = "fiimaterials"

    @Serializable
    private data class Meta(
        val sourceUrl: String? = null,
        val fetchedAt: String? = null,
        val sha256: String? = null,
        val subject: String? = null,
        val kind: String? = null,
    )

    override suspend fun discover(): List<DetectedTask> {
        if (!extrasRoot.exists()) return emptyList()
        val now = Instant.now()
        val out = mutableListOf<DetectedTask>()
        val json = Json { ignoreUnknownKeys = true }
        Files.walk(extrasRoot).use { stream ->
            for (p in stream.toList()) {
                if (p.name != "meta.json") continue
                val txt = try { Files.readString(p) } catch (_: Exception) { continue }
                val meta = try { json.decodeFromString(Meta.serializer(), txt) } catch (_: Exception) { continue }
                val sha = meta.sha256 ?: continue
                val subject = meta.subject ?: "OTHER"
                val pdfFile = Files.list(p.parent).use { stream2 ->
                    stream2.filter { it.name != "meta.json" && it.name.endsWith(".pdf") }
                        .findFirst().orElse(null)
                } ?: continue
                val title = pdfFile.name.removeSuffix(".pdf").replace('_', ' ').take(256)
                out += DetectedTask(
                    sourceId = sourceId,
                    externalId = sha,
                    subject = subject,
                    title = title,
                    deadline = now.plus(14, ChronoUnit.DAYS),
                    problemPath = pdfFile.toString(),
                    sourceUrl = meta.sourceUrl,
                    rawMetadata = mapOf(
                        "kind" to (meta.kind ?: "other"),
                        "sha256" to sha,
                    ),
                )
            }
        }
        return out
    }
}
