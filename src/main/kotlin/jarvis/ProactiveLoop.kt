package jarvis

import jarvis.subsystem.ContextModelSubsystem
import jarvis.subsystem.SubsystemInput
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
 * Phase 2.1 — event-triggered proactive signal emitter.
 *
 * Council 1778165183 verdict (synthesis of Devil's Advocate / Domain Expert /
 * Pragmatist): event-triggered (NOT cron), single subsystem (ctx-model only),
 * hardcoded quiet hours during finals, deterministic hash IDs for
 * restart-replay safety, default-OFF behind PROACTIVE_LOOP_ENABLED env.
 *
 * Hook point: [WebMain] /api/activity handler calls [consider] AFTER
 * [Activity.append] succeeds. We do not modify Activity.append itself —
 * the CLI logger fallback path (offline PC) doesn't have an LLM available
 * anyway.
 *
 * Failure isolation:
 *  - Dedicated dispatcher (Dispatchers.IO.limitedParallelism(1)) so the loop
 *    never contends with /api/chat handlers.
 *  - 60s subprocess timeout (vs 300s default for chat) — proactive must not
 *    starve the user-facing chat path.
 *  - Catch-all writes an error row to signals.jsonl and never crashes the
 *    coroutine.
 *
 * Schema for what this writes: see [ProactiveSignal] in Signals.kt.
 */
object ProactiveLoop {

    private const val IMPORTANCE_THRESHOLD = 0.7f
    private val COOLDOWN = Duration.ofMinutes(30)
    private val SUBSYSTEM_TIMEOUT = Duration.ofSeconds(60)

    // Phase 5.1 — feedback-driven re-weighting cache. Recompute every 30 min
    // rather than on every consider() to amortize the JSONL re-read; staleness
    // is fine since feedback rates change slowly. Null on first call until
    // the first consider() runs, OR after a forced reset for tests.
    @Volatile private var thresholdCache: ThresholdRecommendation? = null
    @Volatile private var thresholdCacheAt: Instant = Instant.EPOCH
    private val THRESHOLD_REFRESH = Duration.ofMinutes(30)

    private fun currentThreshold(now: Instant): Float {
        val age = Duration.between(thresholdCacheAt, now)
        if (thresholdCache == null || age > THRESHOLD_REFRESH) {
            try {
                thresholdCache = FeedbackConsumer.current()
                thresholdCacheAt = now
            } catch (_: Exception) {
                // If reading the JSONLs fails, fall back to the static base.
                return IMPORTANCE_THRESHOLD
            }
        }
        return FeedbackConsumer.effectiveThreshold(thresholdCache)
    }

    // Council retro 2026-05-08: quiet hours moved to QuietHours object.
    // Single source of truth, opt-in via env, default disabled.

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loopDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + loopDispatcher)

    fun isEnabled(): Boolean {
        if (LoopsKillSwitch.loopsDisabled()) return false
        return System.getenv("PROACTIVE_LOOP_ENABLED")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false
    }

    /**
     * Hook called from /api/activity after [Activity.append] succeeds. Returns
     * immediately — the actual LLM call runs on the dedicated dispatcher.
     * If the loop is default-disabled this is a no-op.
     */
    fun consider(entry: ActivityEntry, client: Llm) {
        if (!isEnabled()) return
        scope.launch {
            considerSync(entry, client, Config.signalsFile, Instant.now())
            // R1 — after each event-triggered signal write, check if a
            // higher-order reflection is due. Default-off via separate env.
            ReflectionLoop.maybeReflect(client)
        }
    }

    /** Synchronous entry point used directly by tests (skips the dispatcher
     *  launch). Returns the emitted signal id, or null if the loop suppressed
     *  this event (cooldown / quiet / threshold / dedup). */
    suspend fun considerSync(
        entry: ActivityEntry,
        client: Llm,
        signalsFile: java.nio.file.Path,
        now: Instant,
    ): String? {
        // S5 — schedule-drift detection runs FIRST, before the normal
        // importance threshold. Drift during a study/review/lecture/lab
        // block bumps effective importance to 0.85 so the gate still fires
        // when the activity itself scored low (browser titles often score
        // ~0.3 even though they're the load-bearing distraction signal).
        val schedule = Schedule.load()
        val drift = ActiveDoc.detectDrift(entry, schedule, now)
        val imp = if (drift != null) 0.85f else (entry.importance ?: 0f)
        // Phase 5.1: threshold re-weighted by user feedback history (heavier
        // dismiss-rate raises the bar; sustained useful/pinned lowers it).
        // Falls back to IMPORTANCE_THRESHOLD if feedback signal is sparse
        // (<MIN_ACKS_FOR_GLOBAL acked rows) or jsonl reads fail.
        val effective = currentThreshold(now)
        if (imp < effective) return null

        // 2. Quiet-hours gate (now opt-in via env, default disabled).
        if (QuietHours.isActive(now)) return null

        // 3. Cooldown gate.
        if (!Signals.cooldownElapsedFrom(signalsFile, COOLDOWN, now)) return null

        // 4. Dedup gate (deterministic hash on triggering activity ts +
        //    hour-bucket of now, so the same triggering event within an hour
        //    cannot emit twice even on restart-replay).
        val signalId = computeSignalId(entry.ts, now)
        val seenIds = Signals.readAllFrom(signalsFile).map { it.id }.toSet()
        if (signalId in seenIds) return null

        // 5. Build the signal payload.
        // 5a. DRIFT path — bypass ctx-model entirely. User feedback
        //     2026-05-09: vague "off-track" snippet doesn't help; user
        //     needs a directive ("GET BACK TO PA / NEXT: Tema 5 / OPEN:
        //     <file>"). Drift detection is deterministic, so an LLM call
        //     just adds latency + non-determinism. DriftDirective.build
        //     pulls schedule + assignments + concept catalog directly.
        // 5b. NORMAL path — spend the LLM via ctx-model with timeout.
        val rationale = if (drift != null) {
            "drift: scheduled=${drift.expectedSubject}" +
                (drift.expectedTopic?.let { "/$it" } ?: "") +
                ", actual=${drift.actualReason}, cooldown=elapsed"
        } else {
            "imp=${"%.2f".format(imp)}, cooldown=elapsed, quiet=no"
        }
        val (snippet, kind, status) = if (drift != null) {
            try {
                Triple(DriftDirective.build(drift, now), "drift_alert", "emitted")
            } catch (e: Exception) {
                Triple(
                    "drift detected; directive build failed: ${e.message?.take(120)}",
                    "drift_alert", "emitted",
                )
            }
        } else {
            val ctxModel = ContextModelSubsystem()
            try {
                val out = withTimeout(SUBSYSTEM_TIMEOUT.toMillis()) {
                    ctxModel.run(
                        client,
                        SubsystemInput(
                            activity = Activity.loadEntries(),
                            wiki = MemoryWiki.recent(),
                            recentChat = Conversations.recentAsChatMessages(),
                            userQuery = null,
                        ),
                    )
                }
                Triple(out.text.take(200), "ctx_model_summary", "emitted")
            } catch (e: TimeoutCancellationException) {
                Triple("ctx-model timed out after ${SUBSYSTEM_TIMEOUT.seconds}s", "error", "error")
            } catch (e: Exception) {
                Triple(
                    "${e.javaClass.simpleName}: ${e.message?.take(160).orEmpty()}",
                    "error",
                    "error",
                )
            }
        }

        val signal = ProactiveSignal(
            id = signalId,
            ts = now.toString(),
            kind = kind,
            importance = imp,
            sourceTs = entry.ts,
            snippet = snippet,
            rationale = rationale,
            status = status,
        )
        Signals.appendTo(signalsFile, signal)
        // Phase 2.3 — fan out to wiki / pin per routing rules.
        try {
            SurfaceRouter.apply(signal)
        } catch (e: Exception) {
            System.err.println(
                "[ProactiveLoop] WARN SurfaceRouter.apply failed: ${e.message?.take(160)}",
            )
        }
        return signalId
    }

    /** Kept as facade for tests; delegates to [QuietHours]. */
    fun isQuietHour(now: Instant, zone: ZoneId = ZoneId.of("Europe/Bucharest")): Boolean =
        QuietHours.isActive(now)

    fun computeSignalId(sourceTs: String, now: Instant): String {
        val bucket = LocalDateTime.ofInstant(now, ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"))
        val raw = "$sourceTs|$bucket"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
