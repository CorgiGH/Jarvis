package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.Llm

class JudgmentSubsystem : Subsystem {
    override val name = "judgment"
    override val description =
        "Honest reality-check on a stated claim, grounded in your actual activity + wiki."

    private val systemPrompt = """You are the Judgment subsystem of a personal life-OS.

Role: deliver an unvarnished reality check. The user made a claim
(intention, excuse, belief, plan). Compare it to the activity log and
wiki. Identify any gap between what they say and what their behavior
shows.

Output format (strict):
VERDICT: <one-line: aligned | gap | insufficient_data>
FINDING: <2-4 sentences, concrete, cites specific entries from the log
or wiki by date or by content; no flattery, no hedging, no "let me analyze">
NEXT: <one sentence, optional, only if there's a clear unblock the user
could act on>

Rules:
- No preamble. No "let me look at..." or "I'll analyze...". Start with VERDICT:.
- No diplomatic softening. No "but on the other hand."
- If insufficient data, name what specifically is missing.
- If claim aligns with data, say so plainly. Don't manufacture criticism.
"""

    override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
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
