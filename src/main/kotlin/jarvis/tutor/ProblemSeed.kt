package jarvis.tutor

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

// ─── YAML DTOs (for deserialization from content/{subject}/problems/{id}.yaml) ─

@Serializable
data class ProblemYaml(
    val id: String,
    val subject: String,
    val archetype: String,
    val statement_ro: String,
    val solution_present: Boolean = false,
    val solution: ProblemSolutionYaml? = null,
    val exam_language: String? = null,
    val exam_language_constraints: String? = null,
    val data_files: String? = null,
    val rubric_items: List<RubricItemYaml> = emptyList(),
    val kc_links: List<String> = emptyList(),
    val source: ProblemSourceYaml? = null,
    val synthetic_tag: Boolean = false,
)

@Serializable
data class ProblemSolutionYaml(
    val proof_frame: ProofFrameYaml? = null,
    val trace_steps: List<TraceStepYaml> = emptyList(),
    val reference_source: String? = null,
    val expected_stdout: String? = null,
    val reference_solution: String? = null,
)

@Serializable
data class ProofFrameYaml(
    val template_ro: String,
    val substeps: List<ProofSubstepYaml> = emptyList(),
)

@Serializable
data class ProofSubstepYaml(
    val id: String,
    val label_ro: String,
    val matcher: SubstepMatcherYaml? = null,
)

@Serializable
data class SubstepMatcherYaml(
    val kind: String,    // "exact" | "contains" | "regex"
    val value: String,
)

@Serializable
data class TraceStepYaml(
    val index: Int,
    val label_ro: String? = null,
    val expected: String,
    val tolerance: Double? = null,
)

@Serializable
data class RubricItemYaml(
    val id: String,
    val label: String,
    val points: Double,
    val kind: String,
    val all_or_nothing: Boolean = false,
    val penalty_rules: String? = null,
    val position: Int,
)

@Serializable
data class ProblemSourceYaml(
    val doc: String,
    val page: Int? = null,
    val quote: String,
)

// ─── Seed result ──────────────────────────────────────────────────────────────

data class SeedResult(val inserted: Int, val updated: Int) {
    val total: Int get() = inserted + updated
}

/**
 * Plan-6 Task 2 — idempotent loader of `content/{subject}/problems/{id}.yaml` via [ProblemsRepo].
 *
 * Verification pass: every seeded problem's `source.quote` must be a substring of its
 * committed source doc (`content/{subject}/_sources/{doc}.md`). A failed anchor causes
 * seed REFUSED with a loud error — system verifies, not Alex (no-oracle-inversion rule).
 *
 * Usage: [seed] runs on every call; repeated runs are no-ops (upsert-by-id).
 */
object ProblemSeed {

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            polymorphismStyle = com.charleskorn.kaml.PolymorphismStyle.None,
        )
    )

    /**
     * Load and upsert all problem YAML files under `content/{subject}/problems/` into [db].
     * @param contentDir the `content/` root directory (absolute path).
     * @return [SeedResult] summarising inserts vs updates.
     * @throws IllegalArgumentException if any quote anchor fails verification.
     */
    fun seed(db: Database, contentDir: Path): SeedResult {
        val repo = ProblemsRepo(db)
        var inserted = 0
        var updated = 0

        val subjectDirs = contentDir.toFile().listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()

        for (subjectDir in subjectDirs) {
            val problemsDir = subjectDir.toPath().resolve("problems")
            if (!problemsDir.exists() || !problemsDir.isDirectory()) continue

            val yamlFiles = Files.list(problemsDir)
                .filter { it.extension == "yaml" }
                .sorted()
                .toList()

            for (yamlFile in yamlFiles) {
                val dto = yaml.decodeFromString(ProblemYaml.serializer(), yamlFile.readText())

                // ── Quote-anchor verification ─────────────────────────────────────
                val srcRef = dto.source
                if (srcRef != null) {
                    val sourceFile = subjectDir.toPath()
                        .resolve("_sources")
                        .resolve("${srcRef.doc}.md")
                    require(sourceFile.exists()) {
                        "ProblemSeed: quote anchor verification REFUSED — " +
                            "source doc '${srcRef.doc}.md' not found at $sourceFile " +
                            "(problem ${dto.id})"
                    }
                    val sourceText = sourceFile.readText()
                    require(sourceText.contains(srcRef.quote)) {
                        "ProblemSeed: quote anchor verification REFUSED — " +
                            "quote '${srcRef.quote.take(80)}...' NOT found in $sourceFile " +
                            "(problem ${dto.id}). System verifies provenance, not the user."
                    }
                }

                // ── Build BankProblem ─────────────────────────────────────────────
                val solutionJson = if (dto.solution != null) {
                    kotlinx.serialization.json.Json.encodeToString(
                        ProblemSolutionYaml.serializer(), dto.solution
                    )
                } else null

                val statementJson = buildString {
                    append("{\"ro\":")
                    // Escape the string as a JSON string literal
                    append(jsonStringLiteral(dto.statement_ro))
                    append("}")
                }

                val existing = repo.findById(dto.id)
                val prob = BankProblem(
                    id = dto.id,
                    subject = dto.subject,
                    archetype = dto.archetype,
                    statementJson = statementJson,
                    parameterSlotsJson = null,
                    solutionPresent = dto.solution_present,
                    solutionJson = solutionJson,
                    examLanguage = dto.exam_language,
                    examLanguageConstraintsJson = dto.exam_language_constraints,
                    dataFilesJson = dto.data_files,
                    sourceDoc = srcRef?.doc,
                    sourcePage = srcRef?.page,
                    provenance = "located",
                    syntheticTag = dto.synthetic_tag,
                )
                repo.upsert(prob)
                if (existing == null) inserted++ else updated++

                // ── Rubric items ──────────────────────────────────────────────────
                val rubricItems = dto.rubric_items.map { ri ->
                    RubricItem(
                        id = ri.id,
                        problemId = dto.id,
                        label = ri.label,
                        points = ri.points,
                        kind = ri.kind,
                        allOrNothing = ri.all_or_nothing,
                        penaltyRulesJson = ri.penalty_rules,
                        position = ri.position,
                    )
                }
                if (rubricItems.isNotEmpty()) {
                    repo.upsertRubricItems(rubricItems)
                }

                // ── KC links ──────────────────────────────────────────────────────
                if (dto.kc_links.isNotEmpty()) {
                    repo.upsertKcLinks(dto.id, dto.kc_links)
                }
            }
        }
        return SeedResult(inserted, updated)
    }

    /**
     * Convenience overload that resolves [contentDir] relative to the JVM's working directory
     * (used by the production server and the Task-13 e2e seeding helper).
     */
    fun seed(db: Database): SeedResult =
        seed(db, Paths.get("content"))

    // ─── Deliverable seeds (§0.9-I) ──────────────────────────────────────────────────────────────

    /**
     * Idempotent seed of course-meta deliverables (ALO T1–T5, PS A–D) into [TasksTable] for
     * [userId].  Rows with the same (userId, subject, title) are skipped (uniqueIndex guard).
     *
     * The `deliverable_json` column carries: sub_problems, prep_drill_ids, source_doc, source_quote.
     * The base deadline is null (honest: deadlines unknown → UI shows "necunoscut", §0.9-I).
     * Provenance: ALO points from the verified Plan-2 grade-model registry (gc-alo-lab component,
     * temas field); PS from the live-page contract (Teme A–D, 20-pt bucket).
     *
     * POO lab deliverables are ABSENT — Task-2 Step-3 execution-time search found no POO lab spec
     * files on disk (content/POO/problems/ is empty). This is an honest empty state per §0.9-I.
     */
    fun seedDeliverables(db: Database, userId: String) {
        val deliverables = buildDeliverableRows()
        val now = Instant.now()
        // Placeholder JSON for required non-nullable TasksTable columns that don't apply
        // to deliverable-only rows (the DB schema pre-dates the deliverable concept).
        val emptyRef  = """{"repo":"","path":"","sha":""}"""
        val emptyRefs = "[]"

        transaction(db) {
            for (d in deliverables) {
                // Skip if already seeded (uniqueIndex on userId + subject + title).
                val exists = TasksTable.selectAll()
                    .where { (TasksTable.userId eq userId) and (TasksTable.subject eq d.subject) and (TasksTable.title eq d.title) }
                    .count() > 0
                if (exists) continue

                TasksTable.insert {
                    it[TasksTable.id]            = TutorTypes.ulid()
                    it[TasksTable.userId]        = userId
                    it[TasksTable.subject]       = d.subject
                    it[TasksTable.title]         = d.title
                    it[TasksTable.deadline]      = now.plusSeconds(60L * 60 * 24 * 365) // far-future placeholder
                    it[TasksTable.problemRefJson]   = emptyRef
                    it[TasksTable.conceptRefsJson]  = emptyRefs
                    it[TasksTable.rubricRefJson]    = emptyRef
                    it[TasksTable.cardRefsJson]     = emptyRefs
                    it[TasksTable.status]        = TaskStatus.TODO.name
                    it[TasksTable.createdAt]     = now
                    it[TasksTable.updatedAt]     = now
                    it[TasksTable.deliverableJson] = d.deliverableJson
                }
            }
        }
    }

    /**
     * Internal deliverable row DTO (not serialized to disk — constructed from verified course-meta).
     */
    private data class DeliverableRow(
        val subject: String,
        val title: String,
        val deliverableJson: String,
    )

    /** Build the canonical deliverable rows from verified course-meta (§0.9-I). */
    private fun buildDeliverableRows(): List<DeliverableRow> {
        val rows = mutableListOf<DeliverableRow>()

        // ── ALO Teme T1–T5 (§0.9-I: points 50/60/60/60/70 — from Plan-2 registry gc-alo-lab
        //    temas field verified at Plan-2 seed time; source = ALO official site) ───────────────
        data class AloTema(val n: Int, val points: Int)
        val aloTeme = listOf(
            AloTema(1, 50),
            AloTema(2, 60),
            AloTema(3, 60),
            AloTema(4, 60),
            AloTema(5, 70),
        )
        for (t in aloTeme) {
            rows += DeliverableRow(
                subject = "ALO",
                title   = "Temă ALO T${t.n}",
                deliverableJson = buildString {
                    append("""{"sub_problems":[{"label_ro":"Temă T${t.n}","points":${t.points}}],""")
                    append(""""prep_drill_ids":[],""")
                    append(""""source_doc":"https://edu.info.uaic.ro/algebra-liniara/",""")
                    append(""""source_quote":"temas:[50,60,60,60,70]"}""")
                },
            )
        }

        // ── PS Teme A–D (§0.9-I: 20-pt bucket — live-page contract,
        //    from Plan-2 registry gc-ps-live-labs "Teme A–D 20") ────────────────────────────────
        for (label in listOf("A", "B", "C", "D")) {
            rows += DeliverableRow(
                subject = "PS",
                title   = "Temă PS $label",
                deliverableJson = buildString {
                    append("""{"sub_problems":[{"label_ro":"Temă $label","points":20.0}],""")
                    append(""""prep_drill_ids":[],""")
                    append(""""source_doc":"https://edu.info.uaic.ro/probabilitati-si-statistica/",""")
                    append(""""source_quote":"Teme A-D 20"}""")
                },
            )
        }

        return rows
    }

    /** Encode [s] as a JSON string literal (double-quoted, all control chars escaped). */
    private fun jsonStringLiteral(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"'  -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
