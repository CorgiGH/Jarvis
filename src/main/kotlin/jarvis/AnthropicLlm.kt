package jarvis

/** Direct Anthropic API client. Stub. Fill in when migrating off OpenRouter.
 *
 *  When implementing:
 *  - Endpoint POST https://api.anthropic.com/v1/messages
 *  - Header x-api-key: $apiKey (NOT Authorization: Bearer)
 *  - Header anthropic-version: 2023-06-01 (or current)
 *  - Body shape: { model, max_tokens, system, messages: [{role, content}] }
 *    Note: 'system' is a top-level field, NOT a message in the array.
 *  - Response: { content: [{type: "text", text: "..."}] }
 *  - Default model: "claude-opus-4-7" or "claude-sonnet-4-6" depending on cost
 *  - Add prompt caching (cache_control) on system + long context messages.
 *
 *  Strip system messages out of [messages] before posting, send via 'system'
 *  field. The OpenAI-style "system as first message" convention does not
 *  apply to the native Anthropic API. */
class AnthropicLlm(
    @Suppress("unused") private val apiKey: String,
    @Suppress("unused") private val defaultModel: String = "claude-sonnet-4-6",
) : Llm {

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
        responseFormat: String?,
        imagePath: String?,
    ): Pair<String, String> {
        TODO(
            "AnthropicLlm not yet implemented. Set JARVIS_LLM=claude-max " +
                "(default) or openrouter for now."
        )
    }
}
