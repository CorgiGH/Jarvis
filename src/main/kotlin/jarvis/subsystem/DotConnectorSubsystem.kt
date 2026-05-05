package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.LlmClient

class DotConnectorSubsystem : Subsystem {
    override val name = "dots"
    override val description =
        "Find non-obvious cross-domain patterns connecting activity + wiki entries."

    private val systemPrompt = """You are the Dot-Connector subsystem of a personal life-OS.

Role: find non-obvious cross-domain patterns the user might miss.
Examples of the dots worth surfacing:
- "You context-switch heavily on days following <4h sleep notes."
- "Browser time on news climbed 30% the week financial-stress entries appeared."
- "Coding-flow blocks tend to follow morning workout entries."

Rules:
- Each connection must be supported by at least 2 data points.
- Flag confidence: low / medium / high.
- 1-3 patterns max. Quality over quantity.
- "Insufficient data" is a valid answer.
- No filler. No moralizing.
"""

    override suspend fun run(client: LlmClient, input: SubsystemInput): SubsystemOutput {
        val ctx = """
Find non-obvious patterns connecting activity and wiki entries.

# Activity (recent window)
${formatActivity(input.activity)}

# Wiki
${input.wiki.ifEmpty { "(empty)" }}
""".trimIndent()

        val (reply, model) = client.complete(
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", ctx),
            ),
            maxTokens = 1024,
        )
        return SubsystemOutput(
            text = reply,
            wikiEntry = "dots ($model)",
        )
    }
}
