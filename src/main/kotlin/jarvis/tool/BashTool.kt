package jarvis.tool

import java.util.concurrent.TimeUnit

class BashTool : Tool {
    override val name = "bash"
    override val description =
        """Run a PowerShell command. Args: {"command": "ps cmd", "timeout_seconds": 60}"""

    override suspend fun execute(args: Map<String, String>): String {
        val command = args["command"] ?: return "ERROR: missing 'command'"
        val timeout = args["timeout_seconds"]?.toLongOrNull() ?: 60L

        val pb = ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        if (!proc.waitFor(timeout, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            return "ERROR: timeout after ${timeout}s"
        }
        val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val truncated = if (output.length > 30_000) output.take(30_000) + "\n…(truncated)" else output
        return "exit=${proc.exitValue()}\n$truncated"
    }
}
