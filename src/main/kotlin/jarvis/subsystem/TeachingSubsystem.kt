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
        // Pull stale concepts from KnowledgeState — these have been touched
        // but not recently enough. Quizzing on them tests recall under decay.
        val staleConcepts = jarvis.KnowledgeState.staleConcepts(
            confidenceFloor = 0.4f, minStaleDays = 3L,
        ).take(8)
        val staleBlock = if (staleConcepts.isEmpty()) "(no stale concepts yet)" else
            staleConcepts.joinToString("\n") { c ->
                "  ${c.subject}/${c.concept} — stale=${c.staleDays}d, conf=${"%.2f".format(c.confidence)}"
            }

        // Detect the user's confidence band. Sub-0.3 globally → adapt teaching
        // style: low-confidence gets more scaffolded prompts; mid gets straight
        // recall; high gets edge-case probes.
        val avgConf = staleConcepts.takeIf { it.isNotEmpty() }
            ?.map { it.confidence }?.average()?.toFloat() ?: 0.4f
        val styleHint = when {
            avgConf < 0.2f -> "User has LOW confidence on these. Lean toward " +
                "scaffolded recall (give a definition, ask user to apply / give example)."
            avgConf < 0.5f -> "User has MID confidence. Ask straight recall + 1 step of application."
            else -> "User has HIGH confidence. Ask an edge case or probe an assumption."
        }

        val ctx = """
Pick a topic from the stale-concept ledger BELOW (preferred) or fall back
to the wiki when ledger is empty.

# Stale concept ledger (from KnowledgeState)
$staleBlock

# Adaptation hint
$styleHint

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
            wikiEntry = "teach ($model)",
        )
    }
}
