package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.Llm

class TeachingSubsystem : Subsystem {
    override val name = "teach"
    override val description =
        "Spaced-repetition prompt: pick a stale topic from your wiki and quiz you on it."

    private val systemPrompt = """You are the Teaching subsystem of a personal life-OS.

Role: surface knowledge the user may be losing. Scan the wiki for
learning notes (concepts, definitions, formulas, procedures). Pick
one that hasn't been touched in a while. Generate ONE open-ended
question testing genuine understanding (not recall).

Output format (strict):
TOPIC: <which wiki entry / concept this draws from>
QUESTION: <single question, no answer hints>
END: (I'll grade your answer when you reply.)

If the wiki has no learning notes, output exactly:
NO_NOTES_YET
Suggested /save commands to seed the system:
- /save <concept name>: <key formula or definition you want to retain>
- (3-5 concrete examples from your current studies)

Rules:
- No preamble. Start directly with TOPIC: or NO_NOTES_YET.
- Pick concrete content from the wiki, not generic trivia.
- One question per run.
"""

    override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
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
