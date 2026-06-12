package jarvis.tutor

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Plan-2 additive DB tables (spec §3.4/§3.5). NONE of these ALTERs any existing table; all are
 * brand-new tables registered in TutorMigration ALL_TABLES, created by
 * SchemaUtils.createMissingTablesAndColumns. Task 5 adds the three problem-bank tables; Task 6
 * appends the registry/glossary/schedule tables below.
 *
 * House rules (mirrors Phase1Tables.kt): enum-typed columns are VARCHAR (stored literal == enum
 * name), JSON payloads are `text`, every value here is on a fresh table so a client-side
 * `.default()` at INSERT is sufficient (no ALTER backfill needed).
 */

/** Spec §3.4 — problems are first-class rows, not KC appendages. */
object ProblemsTable : Table("problems") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    /** The problem family, e.g. "Dijkstra on weighted digraph, find shortest path + predecessor table". */
    val archetype = varchar("archetype", 256)
    /** Language-keyed statement: JSON map lang -> statement text. */
    val statementJson = text("statement_json")
    /** Re-rollable values (graph weights, array contents, lambda...) — JSON; null = fixed instance. */
    val parameterSlotsJson = text("parameter_slots_json").nullable()
    /** Whether the source contains a worked solution (R-SOLPRESENT). */
    val solutionPresent = bool("solution_present").default(false)
    val solutionJson = text("solution_json").nullable()
    /** R-LANG: the exam-language constraint id: "alk" | "r" | "cpp" | "bash" | "posix-c" | null. */
    val examLanguage = varchar("exam_language", 32).nullable()
    /** R-LANG details, e.g. banned-STL/banned-function list for POO — JSON; null = none. */
    val examLanguageConstraintsJson = text("exam_language_constraints_json").nullable()
    /** R-DATAFILES: CSV/txt deps the problem names — JSON list of {name, bundled: bool}; null = none. */
    val dataFilesJson = text("data_files_json").nullable()
    val sourceDoc = varchar("source_doc", 256).nullable()
    val sourcePage = integer("source_page").nullable()
    /** Provenance tier (§2.5); null until digestion stamps it. */
    val provenance = varchar("provenance", 24).nullable()
    /** §6.2.4 — honest label for items not derived from a real past paper. */
    val syntheticTag = bool("synthetic_tag").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, subject, archetype) }
}

/** Spec §3.4 rubric rows — G1..Gn, AP/WP, all-or-nothing, penalty rules. Mirrors real grading-doc structure. */
object ProblemRubricItemsTable : Table("problem_rubric_items") {
    val id = varchar("id", 64)
    val problemId = varchar("problem_id", 64).references(ProblemsTable.id)
    /** Rubric row label, "G1".."Gn". */
    val label = varchar("label", 16)
    val points = double("points")
    /** "AP" (answer-points) | "WP" (working-points). */
    val kind = varchar("kind", 4)
    val allOrNothing = bool("all_or_nothing").default(false)
    /** e.g. wrong sign penalized once, not per-line — JSON; null = none. */
    val penaltyRulesJson = text("penalty_rules_json").nullable()
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
    init { index(false, problemId) }
}

/** Spec §3.4 — many-to-many; mastery writes fan out across links. */
object ProblemKcLinksTable : Table("problem_kc_links") {
    val problemId = varchar("problem_id", 64).references(ProblemsTable.id)
    val kcId = varchar("kc_id", 64)
    override val primaryKey = PrimaryKey(problemId, kcId)
}

/** Spec §3.5 R-GRADEMODEL — one verified row per subject per model variant; seeded ONLY from the 2026-06-11 sweep. */
object GradingModelsTable : Table("grading_models") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    /** Variant discriminator, e.g. "live-page" vs "fisa-2024-25-ro" vs "fisa-2025-26-en"; null = sole model. */
    val variant = varchar("variant", 64).nullable()
    /** The model the dashboard/scheduler consumes; at most one true per subject. */
    val isPrimary = bool("is_primary").default(false)
    /** Verbatim formula from the source, e.g. "Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris". */
    val formula = text("formula")
    val maxTotal = double("max_total").nullable()
    /** Pass conditions — JSON, e.g. {"all":[{"component":"exam","min":3,"scale":10},{"total_min":360}]}. */
    val passRuleJson = text("pass_rule_json")
    /** Curve description — JSON; null = unknown (a gap, not a guess). */
    val curveJson = text("curve_json").nullable()
    /** "official-site" | "corpus-evidence" (spec §3.5 evidence tier). */
    val evidenceTier = varchar("evidence_tier", 24)
    val sourceUrl = varchar("source_url", 512)
    /** Errata/gap annotations carried from the sweep — JSON list of strings. */
    val notesJson = text("notes_json").nullable()
    /** Anchor into the sweep doc, e.g. "verified-grade-models-exam-schedule.md#alo". */
    val sweepRef = varchar("sweep_ref", 256)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("grading_models_subject_variant", subject, variant) }
}

/** Spec §3.5 — per-component rows under a grading model. */
object GradeComponentsTable : Table("grade_components") {
    val id = varchar("id", 64)
    val modelId = varchar("model_id", 64).references(GradingModelsTable.id)
    val name = varchar("name", 128)
    val maxPoints = double("max_points").nullable()
    val weight = double("weight").nullable()
    /** Per-component minimum gate — JSON; null = none stated. */
    val minGateJson = text("min_gate_json").nullable()
    /** "yes" | "no" | "unknown" — never guess; unknown is honest (G1/G9). */
    val reexamPolicy = varchar("reexam_policy", 12).nullable()
    val evidenceTier = varchar("evidence_tier", 24)
    val sourceUrl = varchar("source_url", 512).nullable()
    /** Free detail (schedule week, duration, format facts) — JSON. */
    val detailJson = text("detail_json").nullable()
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
    init { index(false, modelId) }
}

/** Spec §3.5 — per-subject EN<->RO term pairs; language validator exemption list (§8.2). */
object GlossaryTermsTable : Table("glossary_terms") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    val termEn = varchar("term_en", 256)
    val termRo = varchar("term_ro", 256)
    val sourceDoc = varchar("source_doc", 256).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("glossary_subject_term_en", subject, termEn) }
}

/**
 * Spec §3.5 exam_dates seeding, LOCK-ROUTED (core §0.8 #1): the 12 verbatim IA12 sweep rows live HERE;
 * the frozen exam_dates table keeps its one-row-per-(user,subject) contract untouched.
 */
object ExamScheduleRowsTable : Table("exam_schedule_rows") {
    val id = varchar("id", 64)
    /** Tutor subject code (ALO/SORC/POO/PA) or the raw discipline name when unmapped (EF, Pedagogie 1, Reverse Engineering — G13: NEVER map RE to SORC). */
    val subject = varchar("subject", 64)
    val rawDiscipline = varchar("raw_discipline", 128)
    /** "examen" | "restanta" | "test-practic" | "examen-facultativ". */
    val examType = varchar("exam_type", 24)
    val startAt = timestamp("start_at")
    val endAt = timestamp("end_at").nullable()
    val room = varchar("room", 128).nullable()
    /** "exact" | "vague" — dates may be vague ("session week"); scheduler consumes both (§3.5/§7.4). */
    val datePrecision = varchar("date_precision", 12)
    /** Trace to the verified sweep (INV-3.4). */
    val sourceRef = varchar("source_ref", 256)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, subject, startAt) }
}
