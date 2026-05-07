package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.Llm

class DotConnectorSubsystem : Subsystem {
    override val name = "dots"
    override val description =
        "Find non-obvious cross-domain patterns connecting activity + wiki entries."

    private val systemPrompt = """You are the Dot-Connector subsystem of a personal life-OS.

Role: find non-obvious cross-domain patterns the user might miss.

Output format (strict, one block per pattern, max 3 patterns):
PATTERN <N>: <one-line summary>
EVIDENCE: <bullet list of 2+ concrete data points from activity or wiki>
CONFIDENCE: low | medium | high

If insufficient data, output exactly:
INSUFFICIENT_DATA: <one sentence on what's missing>

Rules:
- No preamble. No "let me extract the timeline...". No reasoning shown.
- Start the response directly with PATTERN 1: or INSUFFICIENT_DATA:.
- A pattern is "non-obvious": skip tautologies like "reflections appear after activity".
- 1-3 patterns max. Quality over quantity.
- No moralizing or advice. Just patterns.
"""

    override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
        val ctx = """
Find non-obvious patterns connecting activity, recent chat, and wiki entries.

# Activity (recent window)
${formatActivity(input.activity)}

# Recent conversation
${formatRecentChat(input.recentChat)}

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
