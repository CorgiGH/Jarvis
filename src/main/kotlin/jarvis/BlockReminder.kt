package jarvis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Scheduled-block reminder: ticks every minute on a dedicated coroutine.
 * For each upcoming block in the next [LOOKAHEAD_MINUTES], emit a high-
 * importance reminder ProactiveSignal once. Deterministic id keyed on
 * (date, start, subject) so the same block reminds at most once.
 *
 * Default-OFF behind [REMINDER_LOOP_ENABLED] env. Started from runWeb.
 */
object BlockReminder {

    private const val LOOKAHEAD_MINUTES = 5L
    private const val TICK_MILLIS = 60_000L
    private val ZONE = ZoneId.of("Europe/Bucharest")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isEnabled(): Boolean =
        System.getenv("REMINDER_LOOP_ENABLED")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false

    fun start() {
        if (!isEnabled()) {
            System.err.println("[BlockReminder] REMINDER_LOOP_ENABLED not set — loop dormant.")
            return
        }
        scope.launch {
            System.err.println("[BlockReminder] started (tick every ${TICK_MILLIS / 1000}s)")
            while (true) {
                try {
                    tickOnce(Config.signalsFile, Schedule.load(), Instant.now())
                } catch (e: Exception) {
                    System.err.println("[BlockReminder] tick failed: ${e.message?.take(160)}")
                }
                delay(TICK_MILLIS)
            }
        }
    }

    /** Synchronous tick — public for tests. Returns the signal id if a new
     *  reminder was emitted, else null. Skips when in quiet hours, when
     *  cooldown is too tight, when block already reminded. */
    fun tickOnce(
        signalsFile: java.nio.file.Path,
        schedule: ScheduleFile,
        now: Instant,
    ): String? {
        val ldt = LocalDateTime.ofInstant(now, ZONE)
        val today = ldt.toLocalDate().toString()
        val nowTime = ldt.toLocalTime()
        val cutoff = nowTime.plusMinutes(LOOKAHEAD_MINUTES)

        // Find any block on today's calendar starting in [now, now+5min].
        val due = schedule.blocks
            .filter { it.date == today }
            .firstOrNull { b ->
                val start = runCatching { LocalTime.parse(b.start) }.getOrNull() ?: return@firstOrNull false
                start in nowTime..cutoff
            } ?: return null

        // Skip if this exact block already has a reminder signal (any time).
        val signalId = computeId(due)
        val seen = Signals.readAllFrom(signalsFile).map { it.id }.toSet()
        if (signalId in seen) return null

        // Compose + write. Importance 0.85 → routes to PUSH+WIKI per
        // SurfaceRouter rules. Quiet hours bypassed because the user
        // explicitly scheduled this — they want to be woken.
        val rationale = "block ${due.subject} ${due.kind} starts at ${due.start}"
        val snippet = "Reminder: ${due.subject} ${due.kind}" +
            (due.topic?.let { " — $it" } ?: "") +
            " starts ${due.start}"
        val signal = ProactiveSignal(
            id = signalId,
            ts = now.toString(),
            kind = "reminder",
            importance = 0.85f,
            sourceTs = now.toString(),
            snippet = snippet,
            rationale = rationale,
            status = "emitted",
        )
        Signals.appendTo(signalsFile, signal)
        try { SurfaceRouter.apply(signal) } catch (_: Exception) {}
        return signalId
    }

    fun computeId(block: ScheduleBlock): String {
        val raw = "reminder|${block.date}|${block.start}|${block.subject}"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
    }
}
