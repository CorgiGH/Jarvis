package jarvis.tutor

import kotlinx.serialization.Serializable

@Serializable
data class SidekickEnvelope(
    val taskId: String? = null,
    val problemId: String? = null,
    val cardId: String? = null,
    val cardTitle: String? = null,
    val anchorId: String? = null,
    val anchorText: String? = null,
    val selection: String? = null,
    val userQuestion: String,
)

@Serializable
data class SidekickReply(
    val text: String,
    val model: String,
    val quotedContext: String?,
)

object SidekickContext {
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
        return PromptInjectionScrubber.wrap(
            source = "sidekick_context",
            trust = "user_anchor",
            content = sb.toString(),
        )
    }
}
