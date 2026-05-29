package jarvis.tutor

import kotlinx.serialization.Serializable

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
)
