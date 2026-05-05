package jarvis.tool

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class GlobTool : Tool {
    override val name = "glob"
    override val description =
        """Find files by glob pattern. Args: {"pattern": "**/*.kt", "root": "start directory (defaults to cwd)"}"""

    override suspend fun execute(args: Map<String, String>): String {
        val pattern = args["pattern"] ?: return "ERROR: missing 'pattern'"
        val root = args["root"] ?: System.getProperty("user.dir")
        val rootPath = Path.of(root)
        if (!rootPath.exists()) return "ERROR: root not found: $root"

        // Java NIO globs: '**' does not match an empty path segment, so
        // '**/*.kt' misses top-level files. Build a fallback matcher that
        // strips a leading '**/' so files directly under root are also found,
        // and also test against just the file name so '*.kt' works recursively.
        val primary = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val flat = if (pattern.startsWith("**/"))
            FileSystems.getDefault().getPathMatcher("glob:${pattern.removePrefix("**/")}")
        else null
        val nameOnly = FileSystems.getDefault().getPathMatcher("glob:$pattern")

        val matches = mutableListOf<String>()
        Files.walk(rootPath).use { stream ->
            stream.forEach { p ->
                val rel = rootPath.relativize(p)
                val name = p.fileName ?: return@forEach
                if (primary.matches(rel) ||
                    (flat?.matches(rel) == true) ||
                    nameOnly.matches(name)
                ) {
                    matches.add(p.toString())
                }
            }
        }
        return if (matches.isEmpty()) "(no matches)" else matches.joinToString("\n")
    }
}
