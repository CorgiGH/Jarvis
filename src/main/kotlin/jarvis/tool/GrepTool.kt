package jarvis.tool

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class GrepTool : Tool {
    override val name = "grep"
    override val description =
        """Search for regex pattern in file or recursively in directory. Args: {"pattern": "regex", "path": "file or dir", "max_matches": 50}"""

    override suspend fun execute(args: Map<String, String>): String {
        val pattern = args["pattern"] ?: return "ERROR: missing 'pattern'"
        val path = args["path"] ?: return "ERROR: missing 'path'"
        val maxMatches = args["max_matches"]?.toIntOrNull() ?: 50
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            return "ERROR: bad regex: ${e.message}"
        }

        val root = Path.of(path)
        if (!root.exists()) return "ERROR: path not found: $path"

        val files = if (root.isDirectory()) {
            Files.walk(root).use { stream ->
                stream.filter { it.isRegularFile() }.toList()
            }
        } else {
            listOf(root)
        }

        val results = mutableListOf<String>()
        outer@ for (file in files) {
            try {
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEachIndexed { i, line ->
                        if (regex.containsMatchIn(line)) {
                            results.add("$file:${i + 1}: $line")
                            if (results.size >= maxMatches) return@useLines
                        }
                    }
                }
            } catch (_: Exception) {
                // skip binary or unreadable file
            }
            if (results.size >= maxMatches) break@outer
        }
        return if (results.isEmpty()) "(no matches)" else results.joinToString("\n")
    }
}
