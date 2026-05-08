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

    fun isEnabled(): Boolean {
        if (LoopsKillSwitch.loopsDisabled()) return false
        return System.getenv("REMINDER_LOOP_ENABLED")?.lowercase()?.let {
            it == "1" || it == "true" || it == "yes"
        } ?: false
    }

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

        // 1. Block-start reminder (5 min before any scheduled block).
        val due = schedule.blocks
            .filter { it.date == today }
            .firstOrNull { b ->
                val start = runCatching { LocalTime.parse(b.start) }.getOrNull() ?: return@firstOrNull false
                start in nowTime..cutoff
            }
        if (due != null) {
            val signalId = computeId(due, "start")
            val seen = Signals.readAllFrom(signalsFile).map { it.id }.toSet()
            if (signalId !in seen) {
                emitReminder(
                    signalsFile, signalId, now,
                    "Reminder: ${due.subject} ${due.kind}" +
                        (due.topic?.let { " — $it" } ?: "") + " starts ${due.start}",
                    "block ${due.subject} ${due.kind} starts at ${due.start}",
                )
                return signalId
            }
        }

        // 2. Pomodoro pulses inside an active study/review/lecture/lab block.
        //    Cycle 5 (council Domain Expert recommendation): instead of one
        //    block-start reminder, fire on every 25-min boundary inside the
        //    block. User wants intervention cadence, not just bookend.
        val active = schedule.blocks.firstOrNull { b ->
            b.date == today &&
                b.kind in setOf("study", "review", "lecture", "lab") &&
                runCatching { LocalTime.parse(b.start) }.getOrNull()?.let { s ->
                    runCatching { LocalTime.parse(b.end) }.getOrNull()?.let { e ->
                        nowTime >= s && nowTime < e
                    } ?: false
                } ?: false
        } ?: return null
        // Minutes since block start.
        val activeStart = LocalTime.parse(active.start)
        val minutesIn = nowTime.toSecondOfDay() / 60 - activeStart.toSecondOfDay() / 60
        // Pomodoro boundaries: 25 (work-end → break), 30 (break-end → work), 55, 60, 85, 90...
        val phase = when (minutesIn % 30) {
            25L.toInt() -> "break-start"   // user finishes a focus chunk
            0 -> if (minutesIn > 0) "focus-start" else null
            else -> null
        } ?: return null
        val pomodoroId = computeId(active, "pomo-$minutesIn")
        val seen = Signals.readAllFrom(signalsFile).map { it.id }.toSet()
        if (pomodoroId in seen) return null
        val (snippet, rationale) = when (phase) {
            "break-start" -> "5-min break — stand up, water, eyes off screen" to
                "${active.subject} pomodoro break at +${minutesIn}min"
            "focus-start" -> "${active.subject} — back to it (25-min focus)" to
                "${active.subject} pomodoro focus at +${minutesIn}min"
            else -> return null
        }
        emitReminder(signalsFile, pomodoroId, now, snippet, rationale)
        return pomodoroId
    }

    private fun emitReminder(
        signalsFile: java.nio.file.Path,
        id: String,
        now: Instant,
        snippet: String,
        rationale: String,
    ) {
        val signal = ProactiveSignal(
            id = id,
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
    }

    fun computeId(block: ScheduleBlock, suffix: String = "start"): String {
        val raw = "reminder|${block.date}|${block.start}|${block.subject}|$suffix"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)
    }
}
