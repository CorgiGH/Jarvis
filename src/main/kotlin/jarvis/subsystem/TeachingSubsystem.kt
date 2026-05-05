package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.LlmClient

class TeachingSubsystem : Subsystem {
    override val name = "teach"
    override val description =
        "Spaced-repetition prompt: pick a stale topic from your wiki and quiz you on it."

    private val systemPrompt = """You are the Teaching subsystem of a personal life-OS.

Role: surface knowledge the user may be losing. Look at the wiki
for learning notes (concepts, definitions, formulas, procedures).
Pick one that hasn't been touched in a while. Generate a single
open-ended question that tests genuine understanding, not recall.

Rules:
- Pick something concrete from the wiki, not generic trivia.
- One question per run. Wait for the user's answer.
- Don't give away the answer in the question.
- After the question, add the line: "(I'll grade your answer when you reply.)"
- If the wiki has no learning notes, say so plainly and suggest
  what kinds of notes the user could /save to enable this subsystem.
"""

    override suspend fun run(client: LlmClient, input: SubsystemInput): SubsystemOutput {
        val ctx = """
Pick a stale topic from the wiki and pose a single question.

# Wiki
${input.wiki.ifEmpty { "(empty - no learning notes saved yet)" }}
""".trimIndent()

        val (reply, model) = client.complete(
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", ctx),
            ),
            maxTokens = 600,
        )
        return SubsystemOutput(
            text = reply,
            wikiEntry = "teach ($model)",
        )
    }
}
