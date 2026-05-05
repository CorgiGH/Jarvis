package jarvis

import java.nio.file.Path
import java.nio.file.Paths

object Config {
    val homeDir: Path = Paths.get(System.getProperty("user.home"))
    val stateDir: Path = homeDir.resolve(".life-os")
    val activityFile: Path = stateDir.resolve("activity.jsonl")
    val wikiFile: Path = stateDir.resolve("wiki.md")

    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
    const val MAX_TOKENS = 2048
    const val ACTIVITY_LOOKBACK_HOURS = 24L
    const val WIKI_RECENT_ENTRIES = 30
    const val ACTIVITY_LINE_CAP = 200

    val FALLBACK_CHAIN: List<String> = listOf(
        "qwen/qwen3-next-80b-a3b-instruct:free",
        "nvidia/nemotron-3-super-120b-a12b:free",
        "z-ai/glm-4.5-air:free",
        "google/gemma-4-31b-it:free",
        "openai/gpt-oss-120b:free",
        "openrouter/free",
    )
}
