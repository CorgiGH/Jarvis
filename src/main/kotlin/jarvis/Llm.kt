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
    /** Send the conversation; return (assistant text, model identifier used).
     *
     *  [responseFormat] is an OpenAI-style response_format hint, e.g.
     *  `"json_object"`. Providers that support it (OpenRouter) coerce the
     *  model to JSON-only output; providers that don't (Claude CLI,
     *  Copilot CLI, relay) MUST silently ignore the hint so callers can
     *  pass it unconditionally. Task A.5 of the grader-tripwire-reseed
     *  plan adds this so DrillGrader can ask OR for json_object output
     *  before falling through to the regex extract path. */
    suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int = Config.MAX_TOKENS,
        responseFormat: String? = null,
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
     *  subprocess providers (claude-max, copilot) ignore [apiKey].
     *
     *  When JARVIS_LLM=fallback, JARVIS_PRIMARY_LLM and JARVIS_FALLBACK_LLM
     *  pick the two wrapped providers (defaults: relay + copilot). Composite
     *  nesting is rejected — neither slot may itself be 'fallback'. */
    fun create(apiKey: String? = null): Llm =
        createByName(
            name = (System.getenv("JARVIS_LLM") ?: "claude-max"),
            apiKey = apiKey,
            allowComposite = true,
        )

    private fun createByName(name: String, apiKey: String?, allowComposite: Boolean): Llm {
        return when (name.lowercase()) {
            "claude-max", "claude", "max" -> ClaudeMaxLlm()
            "copilot", "gh-copilot", "github-copilot" -> CopilotLlm()
            "anthropic", "anthropic-api" -> AnthropicLlm(
                apiKey ?: error("JARVIS_LLM=anthropic requires ANTHROPIC_API_KEY"),
            )
            "openrouter", "or", "openrouter-free" -> OpenRouterChatLlm()
            "relay" -> RelayLlm()
            "fallback" -> {
                if (!allowComposite) {
                    error("JARVIS_PRIMARY_LLM / JARVIS_FALLBACK_LLM cannot be 'fallback' — would recurse")
                }
                val primaryName = System.getenv("JARVIS_PRIMARY_LLM") ?: "relay"
                val fallbackName = System.getenv("JARVIS_FALLBACK_LLM") ?: "copilot"
                FallbackLlm(
                    primary = createByName(primaryName, apiKey, allowComposite = false),
                    fallback = createByName(fallbackName, apiKey, allowComposite = false),
                )
            }
            else -> error(
                "Unknown JARVIS_LLM: $name. Use claude-max | copilot | anthropic | openrouter | relay | fallback.",
            )
        }
    }
}
