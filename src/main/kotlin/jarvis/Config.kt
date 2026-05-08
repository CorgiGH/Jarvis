package jarvis

import java.nio.file.Path
import java.nio.file.Paths

object Config {
    val homeDir: Path = Paths.get(System.getProperty("user.home"))

    /** Where activity log, wiki, and embeddings live. Override with
     *  JARVIS_DATA_DIR env var (used on the VPS so state lands under
     *  /opt/jarvis/data instead of /root/.life-os). Defaults to
     *  ~/.life-os to match the original layout on the user's PC. */
    val stateDir: Path = run {
        val override = System.getenv("JARVIS_DATA_DIR")?.trim().orEmpty()
        if (override.isNotEmpty()) Paths.get(override) else homeDir.resolve(".life-os")
    }
    val activityFile: Path = stateDir.resolve("activity.jsonl")
    val wikiFile: Path = stateDir.resolve("wiki.md")
    val conversationsFile: Path = stateDir.resolve("conversations.jsonl")
    val coreMemoryFile: Path = stateDir.resolve("core_memory.md")
    val archivalDir: Path = stateDir.resolve("archival")
    val signalsFile: Path = stateDir.resolve("signals.jsonl")
    val lastAccessFile: Path = stateDir.resolve("last_access.jsonl")
    val feedbackFile: Path = stateDir.resolve("feedback.jsonl")
    val goalsFile: Path = stateDir.resolve("goals.jsonl")
    val scheduleFile: Path = stateDir.resolve("schedule.json")
    val knowledgeFile: Path = stateDir.resolve("knowledge.jsonl")
    val assignmentsFile: Path = stateDir.resolve("assignments.jsonl")
    val knowledgeFsrsFile: Path = stateDir.resolve("knowledge_fsrs.jsonl")
    val gradesFile: Path = stateDir.resolve("grades.jsonl")

    /** OpenRouter is no longer used for chat (the copilot / claude-max
     *  subprocess providers replaced it), but EmbeddingsClient still hits
     *  this URL when OPENROUTER_API_KEY is set, since neither Copilot CLI
     *  nor Claude expose embeddings. Leaving the constant so semantic
     *  memory keeps working for users who supply the key. */
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
    const val MAX_TOKENS = 2048
    const val ACTIVITY_LOOKBACK_HOURS = 24L
    const val WIKI_RECENT_ENTRIES = 30
    /** DM4 fix: previously 200, which truncated ~17h on a 5-min-cadence
     *  log. 350 covers a full 24-h window (288 entries at 5-min) + safety
     *  margin. Still bounded so [[activity: 168]] doesn't blow up the
     *  prompt budget — a 7-day window would be capped at the most recent
     *  350 / 5min = ~29h of activity, which is the right behavior at
     *  single-user scale. */
    const val ACTIVITY_LINE_CAP = 350
    const val CONVERSATION_RECENT_N = 30

    /** Phase 1.3 — number of salient prior turns rendered into the system
     *  prompt by buildChatContext(). Smaller than CONVERSATION_RECENT_N
     *  because each salient row stays at full content excerpt and the chat
     *  array already carries the chronological replay. */
    const val SALIENT_PRIOR_N = 6
}
