package jarvis.content

import kotlinx.serialization.Serializable

/** Char offsets into the committed source-of-record text (_sources/{doc}.md). */
@Serializable
data class Span(val start: Int, val end: Int)

/** A verbatim citation. [quote] must appear in the source-of-record of [doc].
 *  When [span] is present it is the authoritative anchor (raw offsets); [page]
 *  is 1-indexed (0 = unspecified). [provenance] is "pdftotext" (machine) or
 *  "vision-confirmed" (Claude re-read the rendered page and confirmed the span). */
@Serializable
data class SourceRef(
    val doc: String,
    val quote: String,
    val page: Int = 0,
    val span: Span? = null,
    val provenance: String = "pdftotext",
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
    /** "standard" | "strict". strict KCs (formula/algorithm) require every
     *  source ref to carry a span AND provenance == "vision-confirmed". */
    val grounding_tier: String = "standard",
    val source: List<SourceRef> = emptyList(),
    /** E3 routing: id of the visualization component (must appear in content/viz-ids.yaml). */
    val viz_id: String? = null,
    /** E3 routing: when true, the validator ERRORs if viz_id is null or unresolvable. */
    val requires_visual: Boolean = false,
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
