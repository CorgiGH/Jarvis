package jarvis.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.sql.Database
import java.time.Instant

/** Builds the rail_json array for a given task. Pure read-side — does NOT mutate. */
object RailJsonBuilder {
    /**
     * Returns rail items as a list of plain maps for ergonomic test assertions.
     * Callers that need the JSON string should encode via [toJsonArrayString].
     */
    fun buildForTask(db: Database, taskId: String, userId: String): List<Map<String, Any?>> {
        val task = TaskRepo(db).findById(taskId) ?: return emptyList()
        val items = mutableListOf<Map<String, Any?>>()

        // PDF entry — always first
        items.add(mapOf(
            "type" to "PDF",
            "label" to "${task.problemRef.path.substringAfterLast('/')} p.1",
            "action" to "OPEN_DRAWER",
            "payload" to mapOf("path" to task.problemRef.path),
        ))

        // Scratchpad entry — always
        items.add(mapOf(
            "type" to "SCRATCHPAD",
            "label" to "draft answers",
            "action" to "OPEN_DRAWER",
            "payload" to emptyMap<String, Any?>(),
        ))

        // Concept refs from task
        task.conceptRefs.forEach { c ->
            items.add(mapOf(
                "type" to "CONCEPT",
                "label" to c.path.substringAfterLast('/'),
                "action" to "OPEN_DRAWER",
                "payload" to mapOf("conceptId" to c.sha),
            ))
        }

        // FSRS due pill
        val due = try {
            FsrsDueQueue.due(db, userId, Instant.now(), 50).size
        } catch (_: Exception) { 0 }
        if (due > 0) {
            items.add(mapOf(
                "type" to "FSRS_DUE",
                "label" to "$due cards due",
                "action" to "NAVIGATE",
                "payload" to mapOf("count" to due, "route" to "/tutor/review"),
            ))
        }

        return items
    }

    /** Encode the items list to a JSON array string for storage in task_prep.rail_json. */
    fun toJsonArrayString(items: List<Map<String, Any?>>): String {
        val arr = buildJsonArray {
            items.forEach { add(it.toJsonObject()) }
        }
        return arr.toString()
    }

    private fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
        forEach { (k, v) -> put(k, v.toJsonElement()) }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonPrimitive(null as String?)
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> @Suppress("UNCHECKED_CAST")
            (this as Map<String, Any?>).toJsonObject()
        is List<*> -> buildJsonArray { forEach { add(it.toJsonElement()) } }
        else -> JsonPrimitive(this.toString())
    }
}
