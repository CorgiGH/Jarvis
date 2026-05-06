package jarvis

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

object MemoryWiki {
    private val tsFormat: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm 'UTC'")
        .withZone(ZoneOffset.UTC)

    /** Process-wide lock guarding every wiki append. Council 1778078829 HIGH +
     *  1778102666 elevation: without this, concurrent POSTs to /api/chat,
     *  /api/sub, /api/wiki and the in-handler MemoryWiki.append from /chat all
     *  hit Files.writeString(APPEND) on the same wiki.md and can interleave
     *  bytes mid-section, silently corrupting the ^## split. */
    private val LOCK = Any()

    fun append(section: String, content: String) {
        appendTo(Config.wikiFile, section, content)
    }

    fun appendTo(file: Path, section: String, content: String) {
        val ts = tsFormat.format(Instant.now())
        val block = "\n## [$ts] $section\n\n${content.trim()}\n"
        synchronized(LOCK) {
            file.parent?.createDirectories()
            Files.writeString(
                file,
                block,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    fun readAll(): String {
        if (!Config.wikiFile.exists()) return ""
        return Config.wikiFile.readText(Charsets.UTF_8)
    }

    fun recent(nEntries: Int = Config.WIKI_RECENT_ENTRIES): String {
        val text = readAll()
        if (text.isEmpty()) return ""
        val parts = text.split(Regex("(?=^## )", RegexOption.MULTILINE))
        val head = parts.firstOrNull()?.takeIf { !it.startsWith("## ") } ?: ""
        val sections = parts.filter { it.startsWith("## ") }
        if (sections.isEmpty()) return text
        val tail = sections.takeLast(nEntries)
        return (head + tail.joinToString("")).trim() + "\n"
    }
}
