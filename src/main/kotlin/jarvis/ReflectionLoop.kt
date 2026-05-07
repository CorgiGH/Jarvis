package jarvis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * R1 (deep-research recommendation #1 / Park et al. 2023 §4.2 reflection
 * cycle) — recursive higher-order summarizer over `signals.jsonl`. When Σ
 * importance over the last 24h ≥ θ AND no reflection has been written in
 * the last 6h, ask the LLM to summarize the dominant pattern in 1 sentence
 * and append a `kind="reflection"` row.
 *
 * Recursive: reflections themselves are eligible for the next reflection
 * cycle. Tree depth bounded by the 6h cooldown + Σ threshold.
 *
 * Default-OFF behind REFLECTION_LOOP_ENABLED env. Hooked into
 * [ProactiveLoop.considerSync] tail.
 */
object ReflectionLoop {

    private val SIGMA_THRESHOLD = 3.0f
    private val WINDOW = Duration.ofHours(24)
    private val COOLDOWN = Duration.ofHours(6)
    private val SUBSYSTEM_TIMEOUT = Duration.ofSeconds(60)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loopDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + loopDispatcher)

    fun isEnabled(): Boolean =
        System.getenv("REFLECTION_LOOP_ENABLED")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false

    fun maybeReflect(client: Llm) {
        if (!isEnabled()) return
        scope.launch {
            reflectSync(client, Config.signalsFile, Instant.now())
        }
    }

    /** Synchronous entry point used directly by tests. Returns the new
     *  reflection signal id, or null if suppressed. */
    suspend fun reflectSync(
        client: Llm,
        signalsFile: java.nio.file.Path,
        now: Instant,
    ): String? {
        val all = Signals.readAllFrom(signalsFile)
        if (all.isEmpty()) return null

        // 1. Σ-importance gate — only emitted (non-error) rows count.
        val cutoff = now.minus(WINDOW)
        val recent = all.filter { sig ->
            val ts = runCatching { Instant.parse(sig.ts) }.getOrNull() ?: return@filter false
            ts >= cutoff && sig.status == "emitted"
        }
        val sigma = recent.sumOf { it.importance.toDouble() }.toFloat()
        if (sigma < SIGMA_THRESHOLD) return null

        // 2. 6h dedup gate — don't recurse runaway. Reflections themselves
        //    count toward last-reflection-time check.
        val lastReflection = all.lastOrNull { it.kind == "reflection" }
        if (lastReflection != null) {
            val lastTs = runCatching { Instant.parse(lastReflection.ts) }.getOrNull()
            if (lastTs != null && Duration.between(lastTs, now) < COOLDOWN) return null
        }

        // 3. Compose prompt + call LLM.
        val parentIds = recent.map { it.id }
        val rationale = "sigma=${"%.2f".format(sigma)} over ${recent.size} signals"
        val (snippet, kind, status) = try {
            withTimeout(SUBSYSTEM_TIMEOUT.toMillis()) {
                val signalsText = recent.joinToString("\n") { sig ->
                    "[${sig.ts}] kind=${sig.kind} imp=${"%.2f".format(sig.importance)}: " +
                        sig.snippet.take(160)
                }
                val (reply, _) = client.complete(
                    messages = listOf(
                        ChatMessage("system",
                            "You are the Reflection subsystem. Read the signal stream " +
                                "below and write ONE sentence describing the dominant " +
                                "pattern across them. No filler, no preamble."),
                        ChatMessage("user", "Signals (last 24h):\n$signalsText"),
                    ),
                    maxTokens = 200,
                )
                Triple(reply.trim().take(300), "reflection", "emitted")
            }
        } catch (e: TimeoutCancellationException) {
            Triple("reflection timed out", "error", "error")
        } catch (e: Exception) {
            Triple(
                "${e.javaClass.simpleName}: ${e.message?.take(160).orEmpty()}",
                "error",
                "error",
            )
        }

        val signalId = computeReflectionId(parentIds, now)
        Signals.appendTo(
            signalsFile,
            ProactiveSignal(
                id = signalId,
                ts = now.toString(),
                kind = kind,
                importance = (sigma / recent.size).coerceIn(0f, 1f), // mean
                sourceTs = recent.first().ts,
                snippet = snippet,
                rationale = rationale,
                status = status,
                parentIds = parentIds,
            ),
        )
        return signalId
    }

    fun computeReflectionId(parentIds: List<String>, now: Instant): String {
        val bucket = LocalDateTime.ofInstant(now, ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"))
        val raw = "reflection|" + parentIds.sorted().joinToString(",") + "|$bucket"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
    }
}
