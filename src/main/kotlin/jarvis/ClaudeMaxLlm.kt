package jarvis

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Subprocess-based provider that delegates to the local 'claude' CLI in
 *  non-interactive mode. Calls count against the user's Claude Max
 *  subscription rate limits, NOT against the Anthropic API budget.
 *
 *  Multi-turn conversations are linearized into a single prompt because
 *  'claude --print' is single-shot. Roles are tagged so the model still
 *  understands turn structure. Quality on multi-turn flows is ~95% of the
 *  proper API equivalent in informal testing — good enough for personal
 *  life-OS use, not a substitute for the streaming API in agent workloads.
 *
 *  Configurable via JARVIS_CLAUDE_BIN (override path) and
 *  JARVIS_CLAUDE_MODEL (e.g. "sonnet", "opus", or a specific model id;
 *  passed to --model). Default uses whatever the CLI's current default is. */
class ClaudeMaxLlm(
    private val binary: String = System.getenv("JARVIS_CLAUDE_BIN") ?: "claude",
    private val model: String? = System.getenv("JARVIS_CLAUDE_MODEL"),
    private val timeoutSeconds: Long = 300,
) : Llm {

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): Pair<String, String> {
        val prompt = serialize(messages)

        val cmd = mutableListOf(binary, "--print", "--output-format", "text")
        if (model != null) {
            cmd += listOf("--model", model)
        }

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(false)

        val proc = try {
            pb.start()
        } catch (e: IOException) {
            error(
                "claude CLI not found at '$binary'. Install Claude Code (https://claude.ai/download) " +
                    "and ensure it's on PATH, or set JARVIS_CLAUDE_BIN. Underlying error: ${e.message}"
            )
        }

        proc.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(prompt)
        }

        if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            error("claude CLI timeout after ${timeoutSeconds}s")
        }

        val stdout = proc.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        val stderr = proc.errorStream.bufferedReader(Charsets.UTF_8).readText().trim()

        if (proc.exitValue() != 0) {
            error("claude CLI exit ${proc.exitValue()}: ${stderr.take(400).ifBlank { stdout.take(400) }}")
        }

        val modelTag = model?.let { "claude-max-cli/$it" } ?: "claude-max-cli"
        return stdout to modelTag
    }

    /** Linearize message history into a single prompt with role tags. The
     *  CLI's 'text' input format is single-shot; for multi-turn fidelity an
     *  upgrade to 'stream-json' input is the natural next step. */
    private fun serialize(messages: List<ChatMessage>): String {
        if (messages.size == 1 && messages[0].role == "user") {
            return messages[0].content
        }
        return buildString {
            for (msg in messages) {
                val tag = when (msg.role) {
                    "system" -> "[INSTRUCTIONS]"
                    "user" -> "[USER]"
                    "assistant" -> "[ASSISTANT_PRIOR_TURN]"
                    else -> "[${msg.role.uppercase()}]"
                }
                append(tag).append('\n').append(msg.content).append("\n\n")
            }
            append("[ASSISTANT_REPLY]\n")
        }
    }
}
