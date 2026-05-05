package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.LlmClient
import jarvis.tool.ToolRegistry

class CoderSubsystem : Subsystem {
    override val name = "coder"
    override val description =
        "Agentic coder. Reads/edits files, greps, runs shell commands. Usage: /sub coder <task>"

    private val maxIterations = 30
    private val toolTagRegex = Regex(
        """<tool>\s*(\w+)\s+(\{[\s\S]*?})\s*</tool>""",
        setOf(RegexOption.MULTILINE),
    )
    private val doneTagRegex = Regex("""<done>([\s\S]*?)</done>""", setOf(RegexOption.MULTILINE))

    private val systemPrompt = """You are a coding assistant with access to tools.

Available tools:
${ToolRegistry.descriptions}

To call a tool, output exactly one block in this format:
<tool>tool_name {"arg1": "value1", "arg2": "value2"}</tool>

The harness will execute the tool and reply with the result. Then continue.

When the task is complete, output:
<done>brief summary of what you did</done>

Rules:
- One tool call per turn. Wait for the result before calling another.
- Always read files before editing them.
- EditTool requires the 'old' string to match exactly and uniquely; add surrounding context if needed.
- For destructive bash commands (rm, git reset, etc.), state your plan in plain English first; the user can stop you.
- Stay focused on the task. No surrounding cleanup, no scope sprawl.
- If the task is unclear, ask one clarifying question instead of calling a tool.
"""

    override suspend fun run(client: LlmClient, input: SubsystemInput): SubsystemOutput {
        val task = input.userQuery
            ?: return SubsystemOutput(
                "Coder needs a task. Usage: /sub coder <task> (chat) or 'sub coder <task>' (CLI).",
                wikiEntry = null,
            )

        val history = mutableListOf(ChatMessage("user", task))
        var lastModel = ""

        for (i in 0 until maxIterations) {
            val (response, model) = client.complete(
                messages = listOf(ChatMessage("system", systemPrompt)) + history,
                maxTokens = 2048,
            )
            lastModel = model
            history.add(ChatMessage("assistant", response))
            println("[coder iter ${i + 1}, $model]")
            println(response)
            println()

            val doneMatch = doneTagRegex.find(response)
            if (doneMatch != null) {
                return SubsystemOutput(
                    text = "Coder finished: ${doneMatch.groupValues[1].trim()}",
                    wikiEntry = "coder ($model): \"${task.take(80)}\"",
                )
            }

            val toolMatch = toolTagRegex.find(response)
            if (toolMatch == null) {
                return SubsystemOutput(
                    text = "Coder produced no <tool> call and no <done> tag. Last response retained for context.",
                    wikiEntry = "coder ($model): \"${task.take(80)}\"",
                )
            }

            val toolName = toolMatch.groupValues[1]
            val argsJson = toolMatch.groupValues[2]
            val result = ToolRegistry.executeTool(toolName, argsJson)
            println("[tool $toolName result]")
            println(result.lines().take(40).joinToString("\n"))
            if (result.lines().size > 40) println("…(${result.lines().size - 40} more lines)")
            println()
            history.add(ChatMessage("user", "TOOL RESULT [$toolName]:\n$result"))
        }

        return SubsystemOutput(
            text = "Coder hit max iterations ($maxIterations) without <done>. Task may be incomplete; see iteration log.",
            wikiEntry = "coder (incomplete - $lastModel): \"${task.take(80)}\"",
        )
    }
}
