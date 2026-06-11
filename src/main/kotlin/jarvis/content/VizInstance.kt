package jarvis.content

import kotlinx.serialization.Serializable

/**
 * Spec §3.3 — a generated example (fresh numbers / new tiny graph). EXEMPT from verbatim grounding;
 * verified by method-consistency + family trace-match (gate work, Plans 3-5). The schema carries the
 * hooks those verifiers need: the method anchor and the reference execution.
 */
@Serializable
data class PedagogicalInstance(
    /** KC whose source-taught method this instance must be solved by (method-consistency anchor). */
    val method_kc_id: String,
    /**
     * Family-typed instance payload (nodes/edges/cells/labels/step-deltas/callouts — §5.3) as a JSON
     * string. STORED AS STRING, not JsonElement: kaml cannot bind kotlinx JsonElement (its serializer
     * is Json-format-only); instances are machine-authored (digestion), so string-JSON costs nothing.
     * The loader MUST parse-validate it at load time — malformed JSON = load error naming the instance.
     * Typed per-family payload schemas land with the family registry (Plan 3).
     */
    val data_json: String = "{}",
) {
    /** Parsed view for consumers/validators; throws on malformed JSON. */
    fun dataElement(): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.Json.parseToJsonElement(data_json)
}

/** Spec §5.3/§5.5 — one typed figure instance: (family_id, instance_data), nothing else. */
@Serializable
data class VizInstance(
    val id: String,
    val subject: String,
    val family_id: String, // one of the ~6 families (§5.2); registry lands in Plan 3
    val language: String = "ro",
    val instance: PedagogicalInstance,
)
