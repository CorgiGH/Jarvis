package jarvis

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

/**
 * Phase 2.3 — surface routing for [ProactiveSignal] rows.
 *
 * Each signal can fan out to:
 *   PUSH — Android polling delivers a system Notification.
 *   WIKI — appended to wiki.md as a "proactive note" section.
 *   PIN  — appended to core_memory.md as a permanent system-prompt entry.
 *
 * Rules (start simple, retrain weights later via Phase 5 feedback loop):
 *   - kind="error"      → WIKI only (never push errors at user).
 *   - kind="reflection" → WIKI + PIN (durable, never push — reflections
 *     summarize patterns the user can browse; pushing them would spam).
 *   - importance < 0.5  → WIKI only.
 *   - 0.5 ≤ imp < 0.8   → WIKI + PUSH.
 *   - importance ≥ 0.8  → WIKI + PUSH + PIN.
 */
enum class Surface { PUSH, WIKI, PIN }

object SurfaceRouter {

    private val SIGNAL_TO_PIN_HEADING = "proactive pin"
    private val SIGNAL_TO_WIKI_HEADING = "proactive note"

    fun route(signal: ProactiveSignal): Set<Surface> {
        if (signal.status == "error") return setOf(Surface.WIKI)
        if (signal.kind == "reflection") return setOf(Surface.WIKI, Surface.PIN)
        return when {
            signal.importance < 0.5f -> setOf(Surface.WIKI)
            signal.importance < 0.8f -> setOf(Surface.WIKI, Surface.PUSH)
            else -> setOf(Surface.WIKI, Surface.PUSH, Surface.PIN)
        }
    }

    fun apply(signal: ProactiveSignal) {
        val surfaces = route(signal)
        if (Surface.WIKI in surfaces) {
            try {
                MemoryWiki.append(
                    "$SIGNAL_TO_WIKI_HEADING (${signal.kind} ${signal.id})",
                    "[ts=${signal.ts} imp=${"%.2f".format(signal.importance)}] ${signal.snippet}",
                )
            } catch (e: Exception) {
                System.err.println(
                    "[SurfaceRouter] WARN wiki append failed for ${signal.id}: ${e.message?.take(160)}",
                )
            }
        }
        if (Surface.PIN in surfaces) {
            try {
                pinToCoreMemory(signal)
            } catch (e: Exception) {
                System.err.println(
                    "[SurfaceRouter] WARN pin failed for ${signal.id}: ${e.message?.take(160)}",
                )
            }
        }
        // PUSH is implicit — Android polling /api/signals handles it.
    }

    /** Append-only line in core_memory.md. PII-scanned first; skipped silently
     *  with WARN if trips. Idempotent on signal id (we don't dedupe, but the
     *  signal id is unique per source-event so duplicates are rare). */
    private fun pinToCoreMemory(signal: ProactiveSignal) {
        val findings = CoreMemory.scanTextForPii(signal.snippet)
        if (findings.isNotEmpty()) {
            System.err.println(
                "[SurfaceRouter] WARN dropping PIN for ${signal.id} containing " +
                    findings.joinToString(",") { it.kind },
            )
            return
        }
        val file = Config.coreMemoryFile
        // De-dup: skip if signal id already pinned.
        val marker = "[$SIGNAL_TO_PIN_HEADING ${signal.id}]"
        if (file.exists()) {
            val existing = file.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (existing.contains(marker)) return
        }
        val line = "\n$marker [imp=${"%.2f".format(signal.importance)}] ${signal.snippet}\n"
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            file,
            line,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }
}
