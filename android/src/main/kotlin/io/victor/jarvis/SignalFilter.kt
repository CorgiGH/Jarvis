package io.victor.jarvis

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * R7 — client-side signal filtering before notification post.
 * Pure function over the user's prefs + signal metadata.
 */
object SignalFilter {

    fun shouldSurface(
        sig: Signal,
        mutedKinds: Set<String>,
        importanceThreshold: Float,
        quietStartHour: Int,
        quietEndHour: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (sig.kind in mutedKinds) return false
        if (sig.importance < importanceThreshold) return false
        if (isInQuietHour(sig.ts, quietStartHour, quietEndHour, zone)) return false
        return true
    }

    fun isInQuietHour(
        tsIso: String,
        quietStartHour: Int,
        quietEndHour: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        // Equal bounds = zero-width window = quiet hours disabled.
        if (quietStartHour == quietEndHour) return false
        val ts = runCatching { Instant.parse(tsIso) }.getOrNull() ?: return false
        val hour = LocalDateTime.ofInstant(ts, zone).hour
        return if (quietStartHour < quietEndHour) {
            // Same-day window e.g. 1..5
            hour in quietStartHour until quietEndHour
        } else {
            // Wrapping window e.g. 23..7 means 23 OR <7
            hour >= quietStartHour || hour < quietEndHour
        }
    }
}
