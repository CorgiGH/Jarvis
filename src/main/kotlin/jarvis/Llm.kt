package jarvis

import kotlinx.serialization.Serializable

/** Single conversation turn. Used by every Llm provider. */
@Serializable
data class ChatMessage(val role: String, val content: String)

/** Minimal LLM contract. Every chat-completion provider implements this so
 *  callers do not depend on a concrete client. Embeddings stay on a separate
 *  client (see jarvis.embeddings.EmbeddingsClient) since not every provider
 *  supplies them. */
interface Llm : AutoCloseable {
    /** Send the conversation; return (assistant text, model identifier used). */
    suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int = Config.MAX_TOKENS,
    ): Pair<String, String>

    override fun close() {}
}

object LlmFactory {
    /** Pick provider based on JARVIS_LLM env var. Defaults to claude-max so
     *  the 20x Max plan covers chat without hitting OpenRouter rate limits or
     *  spending API credits. Use copilot as a placeholder until claude login
     *  lands on the box.
     *
     *  Pass the ANTHROPIC_API_KEY only when JARVIS_LLM=anthropic; the
     *  subprocess providers (claude-max, copilot) ignore [apiKey]. */
    fun create(apiKey: String? = null): Llm {
        val provider = (System.getenv("JARVIS_LLM") ?: "claude-max").lowercase()
        return when (provider) {
            "claude-max", "claude", "max" -> ClaudeMaxLlm()
            "copilot", "gh-copilot", "github-copilot" -> CopilotLlm()
            "anthropic", "anthropic-api" -> AnthropicLlm(
                apiKey ?: error("JARVIS_LLM=anthropic requires ANTHROPIC_API_KEY"),
            )
            else -> error(
                "Unknown JARVIS_LLM: $provider. Use claude-max | copilot | anthropic.",
            )
        }
    }
}
