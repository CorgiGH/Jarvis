package jarvis

import java.nio.file.Files
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

    fun append(section: String, content: String) {
        Config.stateDir.createDirectories()
        val ts = tsFormat.format(Instant.now())
        val block = "\n## [$ts] $section\n\n${content.trim()}\n"
        Files.writeString(
            Config.wikiFile,
            block,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
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
