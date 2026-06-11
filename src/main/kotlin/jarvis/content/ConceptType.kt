package jarvis.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spec §3.1 — drives beat-variant selection (§4.2-4.3) and viz-family selection (§5.2).
 * Wire/YAML literals are the spec's hyphenated strings, FROZEN.
 */
@Serializable
enum class ConceptType {
    @SerialName("procedure") PROCEDURE,
    @SerialName("proof") PROOF,
    @SerialName("definition-taxonomy") DEFINITION_TAXONOMY,
    @SerialName("code-trace") CODE_TRACE,
    @SerialName("timing") TIMING,
    @SerialName("probabilistic") PROBABILISTIC,
    @SerialName("comparison") COMPARISON,
    @SerialName("formula-application") FORMULA_APPLICATION;

    companion object {
        /** YAML literal -> enum; null for unknown (validator turns unknown into a load error). */
        fun fromWire(s: String): ConceptType? = entries.firstOrNull { wireOf(it) == s }
        fun wireOf(t: ConceptType): String = when (t) {
            PROCEDURE -> "procedure"; PROOF -> "proof"
            DEFINITION_TAXONOMY -> "definition-taxonomy"; CODE_TRACE -> "code-trace"
            TIMING -> "timing"; PROBABILISTIC -> "probabilistic"
            COMPARISON -> "comparison"; FORMULA_APPLICATION -> "formula-application"
        }
    }
}
