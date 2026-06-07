package jarvis.tutor

import kotlinx.serialization.Serializable

/**
 * One item of the `GET /api/v1/queue/today` wire JSON — interface-signatures-lock §C (lines 59-73).
 *
 * Returned by `NextKcSelector.select(...)` (one item); the `/queue/today` handler (Group 3)
 * aggregates a `List<QueueItem>` into `{ items, total_due, day }`. Quarantined / non-faithful
 * items are OMITTED server-side (never sent).
 *
 * Field names are FROZEN snake_case, 1:1 with the §2.2 wire JSON.
 */
@Serializable
data class QueueItem(
    val kc_id: String,
    val kc_name_ro: String,
    val kc_name_en: String,
    val subject: String,
    val phase: Phase,                  // serialized as lowercase name
    val mastery_ewma: Double,
    val fsrs_card_id: String?,         // null when no card seeded yet
    val verification_status: VerificationStatus,
    val worked_example_first: Boolean, // SERVER decision (CHANGE-3 field), surfaced here
    val mode: QueueMode,
)

/** The serving mode for a queue item — interface-signatures-lock §C (line 73). Wire literal == name. */
@Serializable
enum class QueueMode { worked, drill, retrieve }
