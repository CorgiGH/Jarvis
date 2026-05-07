package jarvis

/**
 * Single global kill switch for all always-on coroutines (council retro
 * Risk Auditor CRITICAL 2026-05-08).
 *
 * Set `JARVIS_LOOPS_DISABLE=true` in /opt/jarvis/.env + restart to halt
 * ProactiveLoop + ReflectionLoop + StateCache + BlockReminder + future
 * always-on coroutines simultaneously. Faster than unsetting four
 * separate env vars under emergency RAM/cost pressure.
 */
object LoopsKillSwitch {
    fun loopsDisabled(): Boolean =
        System.getenv("JARVIS_LOOPS_DISABLE")?.lowercase() in setOf("1", "true", "yes")
}
