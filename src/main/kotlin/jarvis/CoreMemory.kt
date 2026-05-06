package jarvis

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Pinned context block prepended to the chat system prompt on every turn.
 *
 * Council 1778104395 v1 ruling (HIGH-irreversible privacy concern):
 * - Ships EMPTY. No auto-import from MEMORY.md, no auto-fact-extraction.
 * - User hand-curates at $JARVIS_DATA_DIR/core_memory.md.
 * - DO NOT pin identifiers (matricol, finals dates, ADHD self-report). Provider
 *   prompt-logging is opaque and pinning identifiers is a one-way leak across
 *   third-party APIs (OpenRouter / Copilot CLI / future providers). Use this
 *   for preferences/style/anti-patterns only ("English default", "no
 *   plan-tweaking sidetracks", "council pattern", etc.).
 */
object CoreMemory {

    fun read(): String = readFrom(Config.coreMemoryFile)

    fun readFrom(file: Path): String {
        if (!file.exists()) return ""
        return file.readText(Charsets.UTF_8)
    }

    fun preamble(): String = preambleFrom(Config.coreMemoryFile)

    fun preambleFrom(file: Path): String {
        val raw = readFrom(file).trim()
        if (raw.isEmpty()) return ""
        return "\n\n# Pinned context\n$raw\n"
    }
}
