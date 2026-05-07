package jarvis.subsystem

import jarvis.Config
import jarvis.Llm
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/**
 * Lexical grep-as-tool over the archival corpus (default: $JARVIS_DATA_DIR/archival).
 *
 * Council 1778104395 verdict on FTS5-vs-embeddings: "deferred entirely. First
 * Principles' grep-as-tool is the v1.5 stop-gap if needed." This is that
 * stop-gap. No SQLite dep, no embedding cost, no native lib — pure Kotlin
 * directory walk + case-insensitive substring match. Adequate for personal
 * scale (316 files, 3.2 MB on the user's VPS).
 *
 * Retrieval is NOT auto-injected into chat context (per same council ruling).
 * Triggered only by explicit `/sub search <query>` from the user.
 */
class SearchSubsystem(
    private val archivalRoot: Path = Config.archivalDir,
    private val k: Int = 10,
) : Subsystem {

    override val name = "search"
    override val description =
        "Lexical search over personal archival markdown corpus. /sub search <query>."

    data class Hit(val path: Path, val hits: Int, val snippet: String)

    override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
        val q = input.userQuery
            ?: return SubsystemOutput(
                "Search subsystem needs a query. Usage: /sub search <query>.",
                wikiEntry = null,
            )
        val results = searchIn(archivalRoot, q, k)
        if (results.isEmpty()) {
            return SubsystemOutput("(no matches for \"${q.take(80)}\")", wikiEntry = null)
        }
        val rendered = results.joinToString("\n\n") { hit ->
            val rel = runCatching { archivalRoot.relativize(hit.path).toString() }
                .getOrElse { hit.path.toString() }
            "## $rel  (hits=${hit.hits})\n${hit.snippet}"
        }
        return SubsystemOutput(
            text = "Top ${results.size} matches for \"${q.take(80)}\":\n\n$rendered",
            // Search results are NEVER auto-persisted — keeps them out of
            // /wiki and out of chat context (council 1778104395 hard rule).
            wikiEntry = null,
        )
    }

    companion object {
        private const val SNIPPET_LINES_BEFORE = 1
        private const val SNIPPET_LINES_AFTER = 2

        fun searchIn(root: Path, query: String, k: Int): List<Hit> {
            val q = query.trim()
            if (q.isEmpty() || k <= 0) return emptyList()
            if (!root.exists()) return emptyList()
            val needle = q.lowercase()
            val candidates = mutableListOf<Hit>()
            Files.walk(root).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.extension.equals("md", ignoreCase = true) }
                    .forEach { path ->
                        val lines = try {
                            path.readLines(Charsets.UTF_8)
                        } catch (_: Exception) {
                            return@forEach
                        }
                        val firstMatchLine = lines.indexOfFirst { it.lowercase().contains(needle) }
                        if (firstMatchLine < 0) return@forEach
                        // Count substring occurrences across all lines (so two
                        // matches on one line count as 2, matching how search
                        // engines + grep -c behave).
                        var occurrences = 0
                        for (line in lines) {
                            val lower = line.lowercase()
                            var idx = 0
                            while (true) {
                                val found = lower.indexOf(needle, idx)
                                if (found < 0) break
                                occurrences++
                                idx = found + needle.length
                            }
                        }
                        val from = (firstMatchLine - SNIPPET_LINES_BEFORE).coerceAtLeast(0)
                        val to = (firstMatchLine + SNIPPET_LINES_AFTER).coerceAtMost(lines.lastIndex)
                        val snippet = lines.subList(from, to + 1).joinToString("\n").take(800)
                        candidates += Hit(path, occurrences, snippet)
                    }
            }
            return candidates.sortedByDescending { it.hits }.take(k)
        }
    }
}
