package jarvis.subsystem

import jarvis.ActivityEntry
import jarvis.ChatMessage
import jarvis.Llm

/** A pluggable life-OS subsystem invoked by chat /sub or CLI 'sub <name>'. */
interface Subsystem {
    /** Short identifier used on the command line. */
    val name: String

    /** One-line description shown by /subs. */
    val description: String

    suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput
}

data class SubsystemInput(
    val activity: List<ActivityEntry>,
    /** User-authored notes + reflections + prior sub outputs. NOT chat turns. */
    val wiki: String,
    /** Recent chat turns from conversations.jsonl. Wiki used to carry these as
     *  serialized "## conversation (model)" sections; that dual-write was
     *  dropped after the Letta-split landed. */
    val recentChat: List<ChatMessage> = emptyList(),
    val userQuery: String? = null,
)

data class SubsystemOutput(
    val text: String,
    /** Wiki section title to append under, or null to skip persistence. */
    val wikiEntry: String? = null,
)

internal fun formatActivity(activity: List<ActivityEntry>): String =
    if (activity.isEmpty()) "(no activity in window)"
    else activity.joinToString("\n") { e ->
        "  [${e.ts}] ${e.process ?: "?"}: ${e.title ?: ""}"
    }

internal fun formatRecentChat(messages: List<ChatMessage>): String =
    if (messages.isEmpty()) "(no recent chat)"
    else messages.joinToString("\n") { m ->
        "  ${m.role}: ${m.content.replace("\n", " ").take(400)}"
    }
