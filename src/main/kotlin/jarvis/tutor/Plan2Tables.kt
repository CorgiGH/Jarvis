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
