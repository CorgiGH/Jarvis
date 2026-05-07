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
        // Phase 4.1: derived sleep-window summary fed alongside raw activity.
        // Cheaper than asking the LLM to infer it from the log every time.
        val lastSleep = jarvis.SleepInference.lastSleep(input.activity)
        val sleepLine = lastSleep?.let {
            "Last sleep window: ${it.startTs} → ${it.endTs} (${"%.1f".format(it.durationHours)}h)"
        } ?: "No recent sleep window detected in activity log."
        // Phase 4.3: heuristic stress proxy. Pre-computed so the model doesn't
        // re-derive it from raw log every turn.
        val stress = jarvis.StressProxy.current(input.activity, java.time.Instant.now())
        val stressLine = if (stress.reasons.isEmpty()) {
            "Stress proxy: ${"%.2f".format(stress.score)} (no notable signals)"
        } else {
            "Stress proxy: ${"%.2f".format(stress.score)} — ${stress.reasons.joinToString("; ")}"
        }
        val ctx = """
# Recent activity
${formatActivity(input.activity)}

# Sleep
$sleepLine

# Stress
$stressLine

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
