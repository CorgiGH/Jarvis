package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.LlmClient

class JudgmentSubsystem : Subsystem {
    override val name = "judgment"
    override val description =
        "Honest reality-check on a stated claim, grounded in your actual activity + wiki."

    private val systemPrompt = """You are the Judgment subsystem of a personal life-OS.

Role: deliver an unvarnished reality check. The user just made a claim
(intention, excuse, belief, plan). Compare it against the activity log
and wiki below. Identify any gap between what they say and what their
behavior actually shows.

Rules:
- No flattery. No diplomatic softening. No "but on the other hand."
- Be specific. Cite concrete observations from the log or wiki.
- One core finding. Skip exhaustive lists.
- If claim and data line up, say so plainly. Don't manufacture criticism.
- If insufficient data, say "insufficient data" and what would unlock judgment.
- Keep it under 6 sentences. Bullets OK.
"""

    override suspend fun run(client: LlmClient, input: SubsystemInput): SubsystemOutput {
        val q = input.userQuery
            ?: return SubsystemOutput(
                "Judgment subsystem needs a user claim to evaluate. Try: /sub judgment <claim> (chat) or 'sub judgment <claim>' (CLI).",
                wikiEntry = null,
            )
        val ctx = """
# User claim
$q

# Recent activity
${formatActivity(input.activity)}

# Wiki context
${input.wiki.ifEmpty { "(empty)" }}
""".trimIndent()

        val (reply, model) = client.complete(
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", ctx),
            ),
            maxTokens = 800,
        )
        return SubsystemOutput(
            text = reply,
            wikiEntry = "judgment ($model): \"${q.take(80)}\"",
        )
    }
}
