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
    // ── CHANGE 3 fields (B4) — all Kotlin-defaulted so existing YAMLs deserialize unchanged ──
    /** Authored seed only. Runtime truth = kc_verification_status table (Task 12).
     *  Valid authored literals: {unverified, pending, faithful, uncertain}. */
    val verification_status: String = "unverified",
    /** Precise math/logical invariant the two-family re-derivation + non-LLM leg checks.
     *  Required for grounding_tier=strict (H11). */
    val invariant: String? = null,
    /**
     * B5r-3 (D-R7) — a plain-English restatement of [invariant]: the natural-language meaning a
     * student would read (e.g. "the uniform size of a 3-element array, each element of size 1, is 3").
     * When present it becomes the LLM/NLI HYPOTHESIS for the INVARIANT claim (ContentReconcile.claimsFor
     * sets `content = invariant_statement`), so the family votes on the NL meaning against the lecture
     * quote rather than on a bare equation like `1 + 1 + 1 = 3` (which NLI correctly returns UNCLEAR on).
     * The raw [invariant] still feeds the SymPy leg unchanged. Optional/nullable so all existing YAML
     * parses; folded into the D8 content-hash so editing it re-triggers staleness. */
    val invariant_statement: String? = null,
    /** Machine-checkable rubric items the non-LLM leg runs.
     *  Required (non-empty) for grounding_tier=strict (H11). */
    val grader_rules: List<String> = emptyList(),
    /** Canonical drill stem; stops DrillGenerator hallucinating stems. */
    val stem_template: String? = null,
    /** Ordered subset of phases this KC runs; null ⇒ all four.
     *  Read by ScaffoldPlanner.planFor (Phase 3). */
    val phase_plan: List<String>? = null,
    /** Gick-Holyoak far-transfer stem. Wire in Phase 3 (TASK P3-GHOST-FIELDS(b)). */
    val far_transfer_stem: String? = null,
    /** Chi/Renkl self-explanation prompt. Served by TASK P3-LADDER-SERVE (Phase 3/5). */
    val self_explanation_prompt: String? = null,
    /** SERVER decision: true = present the worked example before the drill.
     *  Surfaced in queue/today; consumed by Phase-5 DrillStack. Default = false. */
    // content/authored default = false; the /queue serving layer (Phase 3) degrades to true when unauthored (worked-example-first scaffold for novices) — data-model-lock §default.
    val worked_example_first: Boolean = false,
    // ── Grounded-teaching layer (council 1780928193, CLAIM-vs-PRESENTATION) — both nullable + Kotlin-
    //    defaulted so existing YAML deserializes unchanged AND emits NO claim when null (kcContentHash
    //    is folded from claimsFor(), so a null field leaves every existing KC's content_hash untouched
    //    — no re-audit cascade). When authored, each emits ONE prose claim (EXPLANATION / WORKED_EXAMPLE)
    //    that flows through the EXISTING faithful-check (ContentReconcile.claimsFor → VerificationRunner),
    //    anchored on the KC's first span-bearing source ref. ──
    /** Authored plain-words restatement of the concept's core meaning (Romanian). The claim it emits
     *  is round-trip-anchored on the KC's first span-bearing source ref. Null/blank ⇒ no claim emitted. */
    val explanation_ro: String? = null,
    /** Authored worked solution demonstrating the concept (Romanian). The claim it emits is round-trip-
     *  anchored on the KC's first span-bearing source ref. Null/blank ⇒ no claim emitted. */
    val worked_example_ro: String? = null,
    // ===== Plan 2 (spec §3.1) — ADDITIVE; this field does NOT feed claimsFor()/content_hash =====
    /** Spec §3.1 — wire literal, validated against ConceptType.fromWire at load. Required for every real KC (INV-3.2). */
    val concept_type: String? = null,
    /** Spec §3.2/§3.7 — language-keyed beat content; key = language code ("ro"). Empty for pre-digestion KCs. */
    val beats: Map<String, KcBeats> = emptyMap(),
)

/** content/viz-ids.yaml — the canonical set of valid viz ids (Kotlin↔TS source of truth). */
@Serializable
data class VizIdsFile(val viz_ids: List<String> = emptyList())

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
    // ── CHANGE 3 fields (B4) — all Kotlin-defaulted so existing YAMLs deserialize unchanged ──
    /** Authored seed only. Valid literals: {unverified, pending, faithful, uncertain}. */
    val verification_status: String = "unverified",
    /** Chi/Renkl self-explanation prompt. Served inline on /drill/grade by TASK P3-MISC-SERVE. */
    val self_explanation_prompt: String? = null,
    /** Figure/diagram spec. Served inline on /drill/grade by TASK P3-MISC-SERVE. */
    val figure_spec: String? = null,
)
