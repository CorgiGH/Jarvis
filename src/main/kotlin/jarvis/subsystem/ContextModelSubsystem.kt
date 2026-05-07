package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.Llm

class ContextModelSubsystem : Subsystem {
    override val name = "ctx-model"
    override val description =
        "Infer current energy / stress / life-season from activity + wiki."

    private val systemPrompt = """You are the Context Model subsystem of a personal life-OS.

Read the activity log + wiki and infer the user's current state.

Output format (strict):
ENERGY: low | mid | high
  basis: <one sentence citing concrete data>
STRESS: low | mid | high
  basis: <one sentence citing concrete data>
LIFE_SEASON: <2-4 word label, e.g. "finals window", "deep build mode",
              "transition", "rest cycle">
  basis: <one sentence>
SIGNALS: <bullet list of 2-3 concrete observations driving the above>

Rules:
- No preamble. Start directly with ENERGY:.
- Cite concrete data points (specific log entries or wiki notes), not vibes.
- Use "insufficient_data" as the value for any field that lacks evidence.
- No advice. No moralizing. Just inference.
- This output is consumed by other subsystems; keep it parseable.
"""

    override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
        val ctx = """
# Recent activity
${formatActivity(input.activity)}

# Recent conversation
${formatRecentChat(input.recentChat)}

# Wiki context
${input.wiki.ifEmpty { "(empty)" }}
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
            wikiEntry = "ctx-model ($model)",
        )
    }
}
