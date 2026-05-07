package jarvis

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Council retro 2026-05-08 fix: single source of truth for quiet hours.
 * Previously hardcoded 23-07 in three places (ProactiveLoop, BlockReminder
 * bypass, Allocator), which produced the 02:33 "go sleep for 18h" failure.
 *
 * Now opt-in via env. If `QUIET_HOURS_START` and `QUIET_HOURS_END` are
 * unset, quiet hours are DISABLED — system never silently overrides the
 * user's actual rhythm.
 *
 * Set example:
 *   QUIET_HOURS_START=01:00
 *   QUIET_HOURS_END=09:00
 *   QUIET_HOURS_ZONE=Europe/Bucharest   (defaults to system zone)
 */
object QuietHours {

    fun isActive(now: Instant = Instant.now()): Boolean =
        isActiveWith(
            now,
            startRaw = System.getenv("QUIET_HOURS_START"),
            endRaw = System.getenv("QUIET_HOURS_END"),
            zoneRaw = System.getenv("QUIET_HOURS_ZONE"),
        )

    fun isActiveWith(
        now: Instant,
        startRaw: String?,
        endRaw: String?,
        zoneRaw: String? = null,
    ): Boolean {
        val start = parseHourMin(startRaw) ?: return false
        val end = parseHourMin(endRaw) ?: return false
        if (start == end) return false
        val zone = runCatching {
            ZoneId.of(zoneRaw ?: "Europe/Bucharest")
        }.getOrDefault(ZoneId.systemDefault())
        val ldt = LocalDateTime.ofInstant(now, zone)
        val nowMins = ldt.hour * 60 + ldt.minute
        val startMins = start.first * 60 + start.second
        val endMins = end.first * 60 + end.second
        return if (startMins < endMins) {
            nowMins in startMins until endMins
        } else {
            // wrap-around window e.g. 23:00–07:00
            nowMins >= startMins || nowMins < endMins
        }
    }

    /** Returns "user said no quiet hours" or specific window for diagnostics. */
    fun describe(): String {
        val start = System.getenv("QUIET_HOURS_START")
        val end = System.getenv("QUIET_HOURS_END")
        return if (start.isNullOrBlank() || end.isNullOrBlank()) {
            "disabled (no QUIET_HOURS_START/END env)"
        } else {
            "$start-$end ${System.getenv("QUIET_HOURS_ZONE") ?: "Europe/Bucharest"}"
        }
    }

    private fun parseHourMin(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val m = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return h to m
    }
}
