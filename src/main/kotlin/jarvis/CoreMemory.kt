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

    /** Council 1778105576 (3) / (E): WARN-loud privacy scanner. Patterns target
     *  the specific identifier shapes the user actually possesses (matricol +
     *  email + phone). Scanner runs at preamble() time and emits stderr WARN
     *  on hit but DOES NOT block — the design contract is "human is responsible
     *  for what's pinned"; this is the loud-fail tripwire that catches the
     *  accidental paste before it ships to providers for weeks. */
    private val PII_PATTERNS = listOf(
        // UAIC matricol shape: digits + uppercase letters + digits (e.g. 31091001031ROSL251002)
        "matricol" to Regex("""\b\d{8,15}[A-Z]{3,5}\d{4,8}\b"""),
        // RFC-ish email
        "email" to Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""),
        // Phone: international or local, 9+ digits with separators
        "phone" to Regex("""\+?\d[\d\s().-]{8,}\d"""),
    )

    data class PiiFinding(val kind: String, val match: String)

    fun read(): String = readFrom(Config.coreMemoryFile)

    fun readFrom(file: Path): String {
        if (!file.exists()) return ""
        return file.readText(Charsets.UTF_8)
    }

    fun scanForPii(file: Path): List<PiiFinding> = scanTextForPii(readFrom(file))

    /** Council 1778164081 post-impl HIGH fix: salient block in buildChatContext
     *  also surfaces conversation content into the system prompt, which means
     *  PII protection has to apply to ANY text headed to provider APIs, not
     *  just core_memory.md. Same regex set; pure function on text. */
    fun scanTextForPii(text: String): List<PiiFinding> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<PiiFinding>()
        for ((kind, pattern) in PII_PATTERNS) {
            for (m in pattern.findAll(text)) {
                out += PiiFinding(kind, m.value)
            }
        }
        return out
    }

    fun preamble(): String = preambleFrom(Config.coreMemoryFile)

    fun preambleFrom(file: Path): String {
        val raw = readFrom(file).trim()
        if (raw.isEmpty()) return ""
        val findings = scanForPii(file)
        if (findings.isNotEmpty()) {
            val summary = findings.joinToString(", ") { "${it.kind}=${it.match.take(20)}" }
            System.err.println(
                "[CoreMemory] WARN core_memory.md contains identifier-shaped tokens " +
                    "[$summary]. Every chat turn now ships these to your LLM provider. " +
                    "Edit ${file} to remove them or accept the leak.",
            )
        }
        return "\n\n# Pinned context\n$raw\n"
    }
}
