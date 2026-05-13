package jarvis.tutor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape uses snake_case per Slice 1 spec §C — the frontend's
 * `lib/inlineAsk.ts` `buildSidekickEnvelope` emits `task_id`, `user_question`,
 * etc. Match exactly with @SerialName so kotlinx.serialization doesn't 400
 * on decode. The 2026-05-11 Slice 1.5 lesson: camelCase/snake_case mismatch
 * between TS and Kotlin sides silently shipped → InlineAskChip flow 400'd.
 */
@Serializable
data class SidekickEnvelope(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("problem_id") val problemId: String? = null,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("card_title") val cardTitle: String? = null,
    @SerialName("anchor_id") val anchorId: String? = null,
    @SerialName("anchor_text") val anchorText: String? = null,
    val selection: String? = null,
    @SerialName("user_question") val userQuestion: String,
    /**
     * The active DRILL card's statement text (the question Alex is supposed to
     * be solving). Used by SelectionQueryBuilder to reject sidekick queries
     * whose `selection` is a near-verbatim paste of the drill statement —
     * testing-effect guardrail per the 2026-05-13 UX-reframe council. Optional;
     * when null/blank the Jaccard gate is skipped.
     */
    @SerialName("drill_statement") val drillStatement: String? = null,
)

@Serializable
data class SidekickReply(
    val text: String,
    val model: String,
    val quotedContext: String?,
)

object SidekickContext {

    private const val CITATION_INSTRUCTION = """
# Source citation
When you use information from the corpus (lecture notes, lab sheets, themes),
cite the source filename inline using the format `(src: <path>)` where <path>
is the relative archival path. Eligible paths come from two places:
  - any path appearing in a retrieved-context block above (marked with the
    `[prefetched score=...]` header), AND
  - any path returned by the search_archival tool when you call it.
If you draw on a prefetched snippet to answer, you MUST attach the
`(src: <path>)` marker citing its exact path.
Do not invent filenames. Only cite paths that actually appear in one of the
two sources above. If no source supports a claim, do not add a citation for it.
"""

    private const val PREFETCH_SNIPPET_CAP = 200

    /**
     * Build the sidekick system prompt for [env]. When [prefetchedHits] is
     * non-empty, embed them as a <retrieved_context source="prefetched_corpus"
     * trust="indexed_data"> block BEFORE the citation instruction — the LLM
     * sees them as already-fetched corpus material and can cite their paths
     * directly via (src: <path>) without invoking search_archival.
     *
     * Snippets are capped at PREFETCH_SNIPPET_CAP chars to keep the system
     * prompt budget bounded (~600 tokens at k=3 vs. ~1800 uncapped). Per
     * council Pragmatist KEY CONCERN, this gates token blow-up on long
     * Tema A sessions.
     */
    fun systemContext(
        env: SidekickEnvelope,
        prefetchedHits: List<jarvis.HybridRetriever.HybridHit> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append("# Sidekick context\n")
        env.taskId?.let { sb.append("task: ").append(it).append('\n') }
        env.problemId?.let { sb.append("problem: ").append(it).append('\n') }
        env.cardTitle?.let { sb.append("card: ").append(it).append('\n') }
        env.anchorText?.let {
            sb.append("paragraph the user is asking about:\n")
            sb.append(it.take(800)).append('\n')
        }
        env.selection?.let {
            sb.append("specific selection inside that paragraph:\n  \"")
            sb.append(it.take(200)).append("\"\n")
        }
        if (prefetchedHits.isNotEmpty()) {
            val body = buildString {
                for (h in prefetchedHits) {
                    append("[prefetched score=")
                    append("%.3f".format(h.score))
                    append("] ")
                    append(h.id)
                    append('\n')
                    append(h.snippet.replace('\n', ' ').take(PREFETCH_SNIPPET_CAP))
                    append("\n\n")
                }
            }.trimEnd()
            sb.append(
                PromptInjectionScrubber.wrap(
                    source = "prefetched_corpus",
                    trust = "indexed_data",
                    content = body,
                )
            )
            sb.append('\n')
        }
        sb.append(CITATION_INSTRUCTION)
        return PromptInjectionScrubber.wrap(
            source = "sidekick_context",
            trust = "user_anchor",
            content = sb.toString(),
        )
    }
}
