package jarvis

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Subprocess-based provider that delegates to the local 'copilot' CLI in
 *  non-interactive mode. Calls count against the user's GitHub Copilot
 *  subscription, NOT against the Anthropic API or any OpenRouter budget.
 *
 *  Used as a placeholder until 'claude login' is bound on the same machine;
 *  flip JARVIS_LLM=claude-max in /opt/jarvis/.env once that's done.
 *
 *  Requires the standalone GitHub Copilot CLI (NOT the older 'gh copilot'
 *  extension): `npm install -g @github/copilot` then `copilot login`.
 *
 *  Flag set used:
 *  - `-p <text>` non-interactive prompt; exits after the agent responds.
 *  - `--allow-all-tools` mandatory for non-interactive runs (CLI requires it).
 *  - `--available-tools=` empty list disables every agent tool, forcing the
 *    CLI to behave as pure completion (no shell, no file edits, no SQL
 *    hallucinations).
 *  - `-s` silent: drop stats / metadata, emit only the agent reply.
 *  - `--no-color` strips ANSI codes; output is plain UTF-8.
 *  - `--output-format text` keeps stdout flat.
 *  - `--no-ask-user` suppresses the agent's own follow-up question tool;
 *    would block forever in non-interactive mode otherwise.
 *
 *  Override the binary via JARVIS_COPILOT_BIN and the model via
 *  JARVIS_COPILOT_MODEL (e.g. "claude-sonnet-4.5", "gpt-5"). When unset,
 *  Copilot CLI uses the user's currently-selected default. */
class CopilotLlm(
    private val binary: String = System.getenv("JARVIS_COPILOT_BIN") ?: "copilot",
    private val model: String? = System.getenv("JARVIS_COPILOT_MODEL"),
    private val timeoutSeconds: Long = 300,
) : Llm {

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): Pair<String, String> {
        // Java ProcessBuilder + the Windows winget shim around copilot.exe
        // mangles embedded \n in argv: the receiving Node argv parser saw
        // 47 separate tokens instead of one prompt. Flattening to single
        // spaces fixes that without changing what the model sees — the
        // [INSTRUCTIONS] / [USER] / [ASSISTANT_REPLY] markers still
        // delimit turns. Linux preserves newlines fine but flattening is
        // harmless there too, so we do it unconditionally.
        val prompt = serialize(messages)
            .replace("\r\n", " ")
            .replace('\n', ' ')
            .replace(Regex("  +"), " ")
            .trim()

        val cmd = mutableListOf(
            binary,
            "-p", prompt,
            "--allow-all-tools",
            "-s",
            "--no-color",
            "--output-format", "text",
            "--no-ask-user",
            "--available-tools=",
        )
        if (model != null) {
            cmd += listOf("--model", model)
        }

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(false)

        val proc = try {
            pb.start()
        } catch (e: IOException) {
            error(
                "copilot CLI not found at '$binary'. Install with: " +
                    "'npm install -g @github/copilot' then 'copilot login'. " +
                    "Underlying error: ${e.message}",
            )
        }

        // Drain stdout + stderr in parallel so neither pipe blocks if the
        // child writes more than the OS pipe buffer (~64 KB on Windows
        // and Linux). Reading after waitFor was racy on Windows and gave
        // 'pipe has been ended' on long Copilot output.
        val stdoutFuture = CompletableFuture.supplyAsync {
            proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        }
        val stderrFuture = CompletableFuture.supplyAsync {
            proc.errorStream.bufferedReader(Charsets.UTF_8).readText()
        }

        if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            error("copilot CLI timeout after ${timeoutSeconds}s")
        }

        val stdout = stdoutFuture.get(5, TimeUnit.SECONDS).trim()
        val stderr = stderrFuture.get(5, TimeUnit.SECONDS).trim()

        if (proc.exitValue() != 0) {
            error(
                "copilot CLI exit ${proc.exitValue()}: " +
                    stderr.take(400).ifBlank { stdout.take(400) },
            )
        }

        val tag = model?.let { "copilot-cli/$it" } ?: "copilot-cli"
        return stdout to tag
    }

    /** Linearize message history into a single prompt with role tags.
     *  Copilot does not expose a system-prompt field separately, so the
     *  instructions get merged into the prompt body. */
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
