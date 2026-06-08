package jarvis.tutor

import kotlinx.serialization.Serializable

/**
 * Provenance marker (council 1780928193 trust-leak fix). Tells the frontend whether the
 * surrounding DrillContentDto's prose (`worked` / `definition`) is AUTHORED + faithful-checked
 * or GENERATED + unaudited, so the faithful "matches your lecture" badge can NEVER visually span
 * generated content. The Phase-5 frontend renders `type=generated` under a DISTINCT honest badge
 * ("AI practice — not checked against your lecture"); `data-testid="provenance-badge"` carries
 * `data-provenance-type`. Additive + nullable so existing task_prep.drills_json decodes unchanged.
 *
 * Invariant (enforced by CI, DrillContentProvenanceInvariantTest): `hasBeenFaithfulChecked` may be
 * true ONLY when `type == "authored"`. A generated drill is ALWAYS `type="generated"`,
 * `hasBeenFaithfulChecked=false`.
 */
@Serializable
data class DrillProvenanceDto(
    /** "authored" (faithful-checked teaching) | "generated" (LLM, unaudited). */
    val type: String,
    /** true ONLY when type == "authored". Generated drills are always false. */
    val hasBeenFaithfulChecked: Boolean,
)

/** E3 render store: Kotlin mirror of tutor-web's DrillContent (DrillStack.tsx).
 *  drillsJson is a JSON object problemId -> DrillContentDto. */
@Serializable
data class DrillContentDto(
    val drill: String,
    val worked: String,
    val definition: String,
    val check: String,
    val expectedAnswerHint: String,
    val language: String? = null,
    val referenceSolution: String? = null,
    val rubricItems: List<String>? = null,
    val vizId: String? = null,
    /** Trust-leak fix: provenance of the prose (`worked`/`definition`). Null on legacy blobs ⇒ the
     *  frontend treats absent provenance as "unknown / not faithful-checked" (fail-closed render). */
    val provenance: DrillProvenanceDto? = null,
)
