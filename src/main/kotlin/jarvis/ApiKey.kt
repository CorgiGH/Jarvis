package jarvis

import io.github.cdimascio.dotenv.dotenv

internal fun resolveOpenRouterKey(): String? {
    System.getenv("OPENROUTER_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
    return try {
        val env = dotenv {
            ignoreIfMissing = true
            ignoreIfMalformed = true
        }
        env["OPENROUTER_API_KEY"]?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
