package jarvis.tool

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class EditTool : Tool {
    override val name = "edit"
    override val description =
        """Find and replace exact string in file (must match exactly once). Args: {"path": "path", "old": "exact text", "new": "replacement"}"""

    override suspend fun execute(args: Map<String, String>): String {
        val path = args["path"] ?: return "ERROR: missing 'path'"
        val old = args["old"] ?: return "ERROR: missing 'old'"
        val new = args["new"] ?: return "ERROR: missing 'new'"
        val file = Path.of(path)
        if (!file.exists()) return "ERROR: file not found: $path"

        val text = file.readText(Charsets.UTF_8)
        val occurrences = text.split(old).size - 1
        if (occurrences == 0) return "ERROR: 'old' string not found in $path"
        if (occurrences > 1) return "ERROR: 'old' string appears $occurrences times in $path; must be unique. Add more context to disambiguate."

        Files.writeString(file, text.replace(old, new), Charsets.UTF_8)
        return "edited $path: replaced 1 occurrence (${old.length} -> ${new.length} chars)"
    }
}
