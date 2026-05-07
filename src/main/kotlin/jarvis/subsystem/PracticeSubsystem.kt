package jarvis.subsystem

import jarvis.ChatMessage
import jarvis.ConceptCatalog
import jarvis.KnowledgeState
import jarvis.Llm

/**
 * Generates exam-style practice problems for a subject. Pulls a sample of
 * stale + untouched concepts from the catalog so the questions hit the
 * weakest spots first.
 *
 * Invoked via `/sub practice <SUBJECT>` or `[[sub: practice PA]]`.
 */
class PracticeSubsystem : Subsystem {
    override val name = "practice"
    override val description =
        "Generate 3 exam-style practice problems for the named subject, " +
        "weighted toward your weakest concepts."

    private val systemPrompt = """You are the Exam-Practice subsystem of a personal life-OS.

Generate exactly 3 exam-style problems for the requested subject.
Difficulty should match a university year-1 final at UAIC FII.
Mix problem styles: 1 conceptual, 1 computational, 1 applied/proof.

Output format (strict):
PROBLEM 1: <single problem statement>
TOPICS: <comma-separated concept names this tests>
HINT: <one-line nudge for stuck users>

PROBLEM 2: ...
TOPICS: ...
HINT: ...

PROBLEM 3: ...
TOPICS: ...
HINT: ...

END: when you're ready, paste your solutions and I'll grade.

Rules:
- No preamble. Start directly with PROBLEM 1.
- Cite concept names from the provided weak-concept list when possible.
- Romanian or English subject material — match the input language.
- Don't reveal solutions in the problem text.
"""

    override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
        val subject = input.userQuery?.trim()?.takeIf { it.isNotEmpty() }
            ?: return SubsystemOutput(
                "Practice subsystem needs a subject. Usage: /sub practice <SUBJECT>.",
                wikiEntry = null,
            )

        // Subject-scoped weak concepts (low confidence + stale).
        val staleInSubject = KnowledgeState.staleConcepts(
            confidenceFloor = 0.5f, minStaleDays = 1L,
        ).filter { it.subject.equals(subject, ignoreCase = true) }
            .take(10)

        // Subject-scoped untouched concepts as fallback.
        val seenInSubject = KnowledgeState.stats()
            .filter { it.subject.equals(subject, ignoreCase = true) }
            .map { it.concept }.toSet()
        val untouched = ConceptCatalog.all()
            .filter { it.subject.equals(subject, ignoreCase = true) && it.name !in seenInSubject }
            .take(10)

        val weakBlock = if (staleInSubject.isEmpty() && untouched.isEmpty()) {
            "(no concept catalog for $subject — falling back to general)"
        } else {
            buildString {
                if (staleInSubject.isNotEmpty()) {
                    append("Stale concepts (test these):\n")
                    staleInSubject.forEach { append("  - ${it.concept} (stale=${it.staleDays}d)\n") }
                }
                if (untouched.isNotEmpty()) {
                    append("\nUntouched concepts (test these):\n")
                    untouched.forEach { append("  - ${it.name}\n") }
                }
            }
        }

        val ctx = """
Subject: $subject

# Weak concept list (priority order)
$weakBlock

Generate 3 exam-style problems per the format. Cite concept names from
the weak list when possible. Keep difficulty at the level of a UAIC FII
year-1 final.
""".trimIndent()

        val (reply, model) = client.complete(
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", ctx),
            ),
            maxTokens = 1200,
        )
        return SubsystemOutput(
            text = reply,
            wikiEntry = "practice $subject ($model)",
        )
    }
}
