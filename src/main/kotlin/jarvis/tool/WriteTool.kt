package jarvis.tool

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

class WriteTool : Tool {
    override val name = "write"
    override val description =
        """Write content to file (creates or overwrites). Args: {"path": "path", "content": "text"}"""

    override suspend fun execute(args: Map<String, String>): String {
        val path = args["path"] ?: return "ERROR: missing 'path'"
        val content = args["content"] ?: return "ERROR: missing 'content'"
        val file = Path.of(path)
        file.parent?.createDirectories()
        Files.writeString(file, content, Charsets.UTF_8)
        return "wrote ${content.length} bytes to $path"
    }
}
