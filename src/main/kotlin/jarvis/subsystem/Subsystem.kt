package jarvis.subsystem

import jarvis.ActivityEntry
import jarvis.LlmClient

/** A pluggable life-OS subsystem invoked by chat /sub or CLI 'sub <name>'. */
interface Subsystem {
    /** Short identifier used on the command line. */
    val name: String

    /** One-line description shown by /subs. */
    val description: String

    suspend fun run(client: LlmClient, input: SubsystemInput): SubsystemOutput
}

data class SubsystemInput(
    val activity: List<ActivityEntry>,
    val wiki: String,
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
