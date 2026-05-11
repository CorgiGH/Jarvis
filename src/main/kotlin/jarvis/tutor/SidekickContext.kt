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
is the relative archival path returned by the search tool.
Do not invent filenames. Only cite paths the search tool actually returned.
If no source supports a claim, do not add a citation for it.
"""

    fun systemContext(env: SidekickEnvelope): String {
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
        sb.append(CITATION_INSTRUCTION)
        return PromptInjectionScrubber.wrap(
            source = "sidekick_context",
            trust = "user_anchor",
            content = sb.toString(),
        )
    }
}
