package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.Llm

class TimingSubsystem : Subsystem {
    override val name = "timing"
    override val description =
        "Decide whether to surface a candidate intervention now, defer, or skip; suggests better timing."

    private val systemPrompt = """You are the Timing subsystem of a personal life-OS.

Given a candidate intervention (e.g. "remind user about stale learning
topic", "flag a pattern", "ask about a stalled goal"), decide whether
to surface it NOW, DEFER to later, or SKIP entirely.

Output format (strict):
DECISION: now | defer | skip
REASON: <one sentence based on activity + wiki signals>
BETTER_TIME: <e.g. "after current focus block ends", "morning after a
              sleep note appears", "next idle window"> | N/A

Rules:
- No preamble. Start directly with DECISION:.
- "now" only if the data shows a clear receptive state (idle window,
  recent reflection, low context-switching).
- "skip" if the intervention is redundant (already addressed in the
  wiki) or contradicted by recent activity.
- Default to "defer" under uncertainty.
- Cite the specific signal driving the call.
"""

    override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
        val candidate = input.userQuery
            ?: return SubsystemOutput(
                "Timing subsystem needs a candidate intervention. Usage: /sub timing \"<candidate>\".",
                wikiEntry = null,
            )

        val ctx = """
# Candidate intervention
$candidate

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
            maxTokens = 400,
        )
        return SubsystemOutput(
            text = reply,
            wikiEntry = "timing ($model): \"${candidate.take(80)}\"",
        )
    }
}
