package jarvis.tool

import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

class ReadTool : Tool {
    override val name = "read"
    override val description =
        """Read file contents. Args: {"path": "absolute or relative path", "max_lines": 2000}"""

    override suspend fun execute(args: Map<String, String>): String {
        val path = args["path"] ?: return "ERROR: missing 'path' arg"
        val maxLines = args["max_lines"]?.toIntOrNull() ?: 2000
        val file = Path.of(path)
        if (!file.exists()) return "ERROR: file not found: $path"
        val sb = StringBuilder()
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.take(maxLines).forEachIndexed { i, line ->
                sb.append(i + 1).append('\t').append(line).append('\n')
            }
        }
        return sb.toString().ifEmpty { "(empty file)" }
    }
}
