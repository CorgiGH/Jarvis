package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/** Domain model for a practice problem in the Plan-2 problem bank (mirrors ProblemsTable 1:1). */
data class BankProblem(
    val id: String,
    val subject: String,
    val archetype: String,
    val statementJson: String,
    val parameterSlotsJson: String?,
    val solutionPresent: Boolean,
    val solutionJson: String?,
    val examLanguage: String?,
    val examLanguageConstraintsJson: String?,
    val dataFilesJson: String?,
    val sourceDoc: String?,
    val sourcePage: Int?,
    val provenance: String?,
    val syntheticTag: Boolean,
)

/** Domain model for a rubric item. */
data class RubricItem(
    val id: String,
    val problemId: String,
    val label: String,
    val points: Double,
    val kind: String,        // "AP" | "WP"
    val allOrNothing: Boolean,
    val penaltyRulesJson: String?,
    val position: Int,
)

/**
 * Repository over the Plan-2 problem-bank tables (ProblemsTable / ProblemRubricItemsTable /
 * ProblemKcLinksTable). All writes are idempotent upsert-by-id. Constructor takes a [Database]
 * — tests pass an in-memory/scratch SQLite DB; production passes the live TutorDb.
 */
class ProblemsRepo(private val db: Database) {

    // ─── Problems ──────────────────────────────────────────────────────────

    /** Insert or update a problem by its id (UPDATE if present, else INSERT). */
    fun upsert(p: BankProblem) = transaction(db) {
        val now = Instant.now()
        val existing = ProblemsTable.selectAll().where { ProblemsTable.id eq p.id }.firstOrNull()
        if (existing == null) {
            ProblemsTable.insert {
                it[id] = p.id
                it[subject] = p.subject
                it[archetype] = p.archetype
                it[statementJson] = p.statementJson
                it[parameterSlotsJson] = p.parameterSlotsJson
                it[solutionPresent] = p.solutionPresent
                it[solutionJson] = p.solutionJson
                it[examLanguage] = p.examLanguage
                it[examLanguageConstraintsJson] = p.examLanguageConstraintsJson
                it[dataFilesJson] = p.dataFilesJson
                it[sourceDoc] = p.sourceDoc
                it[sourcePage] = p.sourcePage
                it[provenance] = p.provenance
                it[syntheticTag] = p.syntheticTag
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            ProblemsTable.update({ ProblemsTable.id eq p.id }) {
                it[subject] = p.subject
                it[archetype] = p.archetype
                it[statementJson] = p.statementJson
                it[parameterSlotsJson] = p.parameterSlotsJson
                it[solutionPresent] = p.solutionPresent
                it[solutionJson] = p.solutionJson
                it[examLanguage] = p.examLanguage
                it[examLanguageConstraintsJson] = p.examLanguageConstraintsJson
                it[dataFilesJson] = p.dataFilesJson
                it[sourceDoc] = p.sourceDoc
                it[sourcePage] = p.sourcePage
                it[provenance] = p.provenance
                it[syntheticTag] = p.syntheticTag
                it[updatedAt] = now
            }
        }
    }

    /** Find a problem by its unique id, or null if absent. */
    fun findById(id: String): BankProblem? = transaction(db) {
        ProblemsTable.selectAll().where { ProblemsTable.id eq id }.firstOrNull()?.toProb()
    }

    /** List all problems for a given subject (arbitrary order). */
    fun listBySubject(subject: String): List<BankProblem> = transaction(db) {
        ProblemsTable.selectAll().where { ProblemsTable.subject eq subject }.map { it.toProb() }
    }

    /** Count of seeded problems per subject — used by INV-6.1 pending-set probe. */
    fun countBySubject(): Map<String, Int> = transaction(db) {
        ProblemsTable.selectAll().toList()
            .groupBy { it[ProblemsTable.subject] }
            .mapValues { (_, rows) -> rows.size }
    }

    // ─── Rubric items ──────────────────────────────────────────────────────

    /**
     * Upsert rubric items for a problem. Replaces the full set (delete + re-insert) so that
     * re-seeding a problem's rubric is idempotent and ordering is always stable.
     */
    fun upsertRubricItems(items: List<RubricItem>) = transaction(db) {
        if (items.isEmpty()) return@transaction
        val problemId = items.first().problemId
        ProblemRubricItemsTable.deleteWhere { ProblemRubricItemsTable.problemId eq problemId }
        for (item in items) {
            ProblemRubricItemsTable.insert {
                it[id] = item.id
                it[ProblemRubricItemsTable.problemId] = item.problemId
                it[label] = item.label
                it[points] = item.points
                it[kind] = item.kind
                it[allOrNothing] = item.allOrNothing
                it[penaltyRulesJson] = item.penaltyRulesJson
                it[position] = item.position
            }
        }
    }

    /** List rubric items for a problem, ordered by position ascending. */
    fun listRubricItems(problemId: String): List<RubricItem> = transaction(db) {
        ProblemRubricItemsTable.selectAll()
            .where { ProblemRubricItemsTable.problemId eq problemId }
            .sortedBy { it[ProblemRubricItemsTable.position] }
            .map { it.toRubricItem() }
    }

    // ─── KC links ──────────────────────────────────────────────────────────

    /**
     * Upsert KC links for a problem. Replaces the full set (delete + re-insert) — idempotent.
     */
    fun upsertKcLinks(problemId: String, kcIds: List<String>) = transaction(db) {
        ProblemKcLinksTable.deleteWhere { ProblemKcLinksTable.problemId eq problemId }
        for (kcId in kcIds) {
            ProblemKcLinksTable.insert {
                it[ProblemKcLinksTable.problemId] = problemId
                it[ProblemKcLinksTable.kcId] = kcId
            }
        }
    }

    /** List KC ids linked to a problem. */
    fun listKcLinks(problemId: String): List<String> = transaction(db) {
        ProblemKcLinksTable.selectAll()
            .where { ProblemKcLinksTable.problemId eq problemId }
            .map { it[ProblemKcLinksTable.kcId] }
    }

    // ─── Converters ────────────────────────────────────────────────────────

    private fun ResultRow.toProb() = BankProblem(
        id = this[ProblemsTable.id],
        subject = this[ProblemsTable.subject],
        archetype = this[ProblemsTable.archetype],
        statementJson = this[ProblemsTable.statementJson],
        parameterSlotsJson = this[ProblemsTable.parameterSlotsJson],
        solutionPresent = this[ProblemsTable.solutionPresent],
        solutionJson = this[ProblemsTable.solutionJson],
        examLanguage = this[ProblemsTable.examLanguage],
        examLanguageConstraintsJson = this[ProblemsTable.examLanguageConstraintsJson],
        dataFilesJson = this[ProblemsTable.dataFilesJson],
        sourceDoc = this[ProblemsTable.sourceDoc],
        sourcePage = this[ProblemsTable.sourcePage],
        provenance = this[ProblemsTable.provenance],
        syntheticTag = this[ProblemsTable.syntheticTag],
    )

    private fun ResultRow.toRubricItem() = RubricItem(
        id = this[ProblemRubricItemsTable.id],
        problemId = this[ProblemRubricItemsTable.problemId],
        label = this[ProblemRubricItemsTable.label],
        points = this[ProblemRubricItemsTable.points],
        kind = this[ProblemRubricItemsTable.kind],
        allOrNothing = this[ProblemRubricItemsTable.allOrNothing],
        penaltyRulesJson = this[ProblemRubricItemsTable.penaltyRulesJson],
        position = this[ProblemRubricItemsTable.position],
    )
}
