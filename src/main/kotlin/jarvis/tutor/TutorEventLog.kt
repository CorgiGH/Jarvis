package jarvis.tutor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Serializable
data class RcodeRedacted(
    val rcode_sha256: String,
    val preview_head: String,
    val preview_tail: String,
    val length_chars: Int
)

@Serializable
data class TutorEvent(
    val event_type: String,
    val event_id: String,
    val ts_utc: String,
    val task_id: String?,
    val session_id: String,
    val prompt_template_id: String?,
    val system_prompt_sha256: String?,
    val retrieved_context_summary: List<String>?,
    val llm_input_full: String? = null,
    val llm_input_redacted: RcodeRedacted? = null,
    val llm_output_full: String?,
    val model_resolved: String?,
    val tokens_in: Int?,
    val tokens_out: Int?,
    val latency_ms: Long?,
    val status: String,
    val is_synthetic: Boolean = false
)

class TutorEventLog(
    private val privateDir: File = File("/opt/jarvis/data/private"),
    queueCapacity: Int = 1024
) {
    private val lock = ReentrantLock()
    private val queue = Channel<TutorEvent>(capacity = queueCapacity)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        privateDir.mkdirs()
        runCatching { privateDir.setExecutable(true, true); privateDir.setReadable(true, true); privateDir.setWritable(true, true) }
        GlobalScope.launch(Dispatchers.IO) {
            for (evt in queue) writeOne(evt)
        }
    }

    fun append(evt: TutorEvent) {
        if (!queue.trySend(evt).isSuccess) {
            System.err.println("[tutor-event-log] queue saturated, dropping ${evt.event_id}")
        }
    }

    suspend fun flush() {
        kotlinx.coroutines.delay(50)
    }

    private fun writeOne(evt: TutorEvent) {
        lock.withLock {
            val today = LocalDate.now().toString()
            val file = File(privateDir, "tutor_events.$today.jsonl")
            file.appendText(json.encodeToString(evt) + "\n")
        }
    }

    fun rotateAndRetain(maxAgeDays: Int = 14) {
        lock.withLock {
            val cutoff = LocalDate.now().minusDays(maxAgeDays.toLong())
            privateDir.listFiles { f -> f.name.startsWith("tutor_events.") && f.name.endsWith(".jsonl") }?.forEach { f ->
                val datePart = f.name.removePrefix("tutor_events.").removeSuffix(".jsonl")
                runCatching {
                    if (LocalDate.parse(datePart).isBefore(cutoff)) f.delete()
                }
            }
        }
    }

    companion object {
        /**
         * Singleton used by route handlers. Lazily reads the private-data
         * directory from the `jarvis.tutor.event_log.dir` JVM system property,
         * falling back to the production `/opt/jarvis/data/private` path.
         *
         * Tests inject a temp dir by setting the system property BEFORE the
         * first access (i.e. before any /api/v1/drill/grade call). Once the
         * lazy value initializes, the path is frozen for the JVM lifetime.
         */
        val GLOBAL: TutorEventLog by lazy {
            val dir = System.getProperty("jarvis.tutor.event_log.dir")
                ?.let { File(it) }
                ?: File("/opt/jarvis/data/private")
            TutorEventLog(privateDir = dir)
        }
    }
}
