package jarvis.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MisconceptionCode(
    val code: String,
    val label_ro: String,
    val judge_hint_en: String,
)

@Serializable
private data class MisconceptionRegistry(
    val subjects: Map<String, List<MisconceptionCode>>,
)

/**
 * Data-driven misconception registry (R-6-Q4, E18).
 *
 * Loaded once from `src/main/resources/grader/misconception-codes.json`
 * on first access. Plan 5 mines R-ERRORS breadth into this same file —
 * mechanism-now, mining-later.
 *
 * Unknown subjects → `[OTHER]` only (never throws).
 */
object MisconceptionTaxonomy {

    private val OTHER_ONLY = listOf(
        MisconceptionCode(
            code = "OTHER",
            label_ro = "Altă eroare",
            judge_hint_en = "answer is wrong but does not match any classified pattern",
        )
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val registry: MisconceptionRegistry by lazy {
        val resource = MisconceptionTaxonomy::class.java
            .getResourceAsStream("/grader/misconception-codes.json")
            ?: error("misconception-codes.json not found on classpath at /grader/misconception-codes.json")
        val text = resource.bufferedReader(Charsets.UTF_8).readText()
        json.decodeFromString(MisconceptionRegistry.serializer(), text)
    }

    /**
     * Returns the list of [MisconceptionCode]s for the given subject.
     * Unknown subjects → `[OTHER]` only.
     */
    fun codesFor(subject: String): List<MisconceptionCode> =
        registry.subjects[subject] ?: OTHER_ONLY
}
