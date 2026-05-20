package jarvis.content

import kotlinx.serialization.Serializable

/** A verbatim citation: [quote] must appear in the extracted text of source [doc]. */
@Serializable
data class SourceRef(
    val doc: String,
    val quote: String,
)

/** content/subjects.yaml — the top-level manifest. */
@Serializable
data class SubjectsManifest(
    val version: Int = 1,
    val subjects: List<SubjectEntry> = emptyList(),
)

@Serializable
data class SubjectEntry(
    val id: String,
    val name_ro: String,
    val name_en: String,
)

/** content/{subject}/kcs/{id}.yaml — one knowledge concept. */
@Serializable
data class KnowledgeConcept(
    val id: String,
    val subject: String,
    val name_ro: String,
    val name_en: String,
    val cluster: String,
    val bloom_level: String,
    val difficulty: Int,
    val time_minutes: Int,
    val exam_weight: Double,
    val tier: Int,
    val source: List<SourceRef> = emptyList(),
    val version: Int = 1,
)

/** A single prerequisite edge: [kc] depends on [prereq]. */
@Serializable
data class PrereqEdge(
    val kc: String,
    val prereq: String,
    val rationale: String,
)

/** content/{subject}/edges.yaml — the prerequisite DAG for one subject. */
@Serializable
data class EdgesFile(
    val subject: String,
    val edges: List<PrereqEdge> = emptyList(),
)

/** content/{subject}/misconceptions/{id}.yaml — one misconception. */
@Serializable
data class Misconception(
    val id: String,
    val kc_id: String,
    val label_ro: String,
    val label_en: String,
    val trigger: String,
    val refutation: String,
    val source: List<SourceRef> = emptyList(),
    val version: Int = 1,
)
