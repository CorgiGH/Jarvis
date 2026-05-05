package jarvis

import kotlin.system.exitProcess

private const val REFLECTION_MAX_TOKENS = 1024

private val REFLECTION_PROMPT = """Below is the user's activity log for the past 24 hours (active window sampled every 5 minutes).

Write a brief honest reflection:
- What did the user actually spend time on?
- Patterns worth noting (focus blocks, distractions, sleep gap, app churn)?
- One observation that might be useful tomorrow.
Skip anything obvious. Keep it short. No filler.

Activity log:
"""

internal suspend fun runReflect() {
    val apiKey = resolveOpenRouterKey() ?: run {
        System.err.println("ERROR: OPENROUTER_API_KEY not set. Copy .env.example to .env and set the key.")
        exitProcess(1)
    }

    val entries = Activity.loadEntries(hours = 24L)
    if (entries.isEmpty()) {
        println("No activity entries in last 24h. Nothing to reflect on.")
        return
    }
    val logText = entries.joinToString("\n") { e ->
        "[${e.ts}] ${e.process ?: "?"}: ${e.title ?: ""}"
    }

    LlmFactory.create(apiKey).use { client ->
        val (reply, model) = try {
            client.complete(
                messages = listOf(ChatMessage("user", REFLECTION_PROMPT + logText)),
                maxTokens = REFLECTION_MAX_TOKENS,
            )
        } catch (e: Exception) {
            System.err.println("[reflect] all models failed: ${e.message}")
            exitProcess(1)
        }
        MemoryWiki.append("daily reflection ($model)", reply)
        println("Reflection saved to ~/.life-os/wiki.md (model: $model):\n")
        println(reply)
    }
}
