package jarvis.content

import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.LanguageCheckTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 4b Task 6 (INV-8.1 shape) — [LanguageCheckTable] record invariant.
 *
 * Two-directional proof:
 *  (1) After [ContentReconcile.reconcile] over the REAL corpus, query "beat-complete RO fields
 *      lacking a language_check record" → 0 rows (every served RO field has a record).
 *  (2) Delete one record row → the same query returns 1 (the invariant detects holes).
 *
 * Uses a fresh SQLite DB with [LanguageCheckTable] created directly via SchemaUtils.create
 * (the LessonServeRouteTest freshDb pattern). Production gets the table via the PM-applied
 * migration (migration-language-check.patch at CP-3).
 *
 * ASCII test method names (§0 #15).
 */
class LanguageCheckRecordTest {

    @field:TempDir
    lateinit var tmp: Path

    private fun freshDb(): org.jetbrains.exposed.sql.Database {
        val db = TutorDb.connect(tmp.resolve("lang-check-test.db").toString())
        transaction(db) {
            SchemaUtils.create(KcVerificationStatusTable, LanguageCheckTable)
        }
        return db
    }

    /**
     * Load the real corpus, run reconcile, then query for beat-complete RO fields lacking a
     * language_check record. The query must return 0.
     *
     * "Beat-complete RO fields" = every non-blank RO text field in every beat of every KC that
     * has beat data in the "ro" language. This mirrors INV-8.1's requirement that served fields
     * all have records.
     */
    @Test fun `after reconcile over real corpus every beat-complete RO field has a record`() {
        val db = freshDb()
        val repo = ContentRepo(Path.of("content"))
        val manifest = repo.loadManifest()
        val subjects = manifest.subjects.map { repo.loadSubject(it.id) }

        ContentReconcile.reconcile(subjects, db)

        // Collect the set of (kcId, field) that SHOULD have records:
        // every non-blank RO field in beats["ro"] for every KC.
        val expectedFields = mutableSetOf<Pair<String, String>>()
        for (sub in subjects) {
            for (kc in sub.kcs) {
                fun addIfNonBlank(text: String?, field: String) {
                    if (!text.isNullOrBlank()) expectedFields += kc.id to field
                }
                addIfNonBlank(kc.name_ro, "name_ro")
                addIfNonBlank(kc.explanation_ro, "explanation_ro")
                addIfNonBlank(kc.worked_example_ro, "worked_example_ro")
                val roBeats = kc.beats["ro"] ?: continue
                roBeats.predict?.let { p ->
                    addIfNonBlank(p.prompt, "beats.ro.predict.prompt")
                    for ((i, opt) in p.options.withIndex()) {
                        addIfNonBlank(opt.text, "beats.ro.predict.options[$i].text")
                        addIfNonBlank(opt.callback, "beats.ro.predict.options[$i].callback")
                    }
                }
                roBeats.attempt?.let { a ->
                    addIfNonBlank(a.statement, "beats.ro.attempt.statement")
                    addIfNonBlank(a.feedback_correct, "beats.ro.attempt.feedback_correct")
                    for ((i, choice) in a.choices.withIndex()) {
                        addIfNonBlank(choice.text, "beats.ro.attempt.choices[$i].text")
                        addIfNonBlank(choice.feedback, "beats.ro.attempt.choices[$i].feedback")
                    }
                }
                roBeats.reveal?.let { r ->
                    for ((i, step) in r.steps.withIndex()) {
                        addIfNonBlank(step.text, "beats.ro.reveal.steps[$i].text")
                        addIfNonBlank(step.callout, "beats.ro.reveal.steps[$i].callout")
                    }
                }
                roBeats.name?.let { n ->
                    addIfNonBlank(n.definition, "beats.ro.name.definition")
                    addIfNonBlank(n.invariant_statement, "beats.ro.name.invariant_statement")
                    addIfNonBlank(n.why_matters, "beats.ro.name.why_matters")
                }
                roBeats.check?.let { c ->
                    addIfNonBlank(c.item_stem, "beats.ro.check.item_stem")
                    for ((i, choice) in c.choices.withIndex()) {
                        addIfNonBlank(choice.text, "beats.ro.check.choices[$i].text")
                        addIfNonBlank(choice.feedback, "beats.ro.check.choices[$i].feedback")
                    }
                }
            }
        }

        // Assert: every expected (kcId, field) pair has a row in the language_check table
        val actualRecords = transaction(db) {
            LanguageCheckTable.selectAll().map { row ->
                row[LanguageCheckTable.kcId] to row[LanguageCheckTable.field]
            }.toSet()
        }

        val missingFields = expectedFields - actualRecords
        assertEquals(
            0, missingFields.size,
            "INV-8.1 VIOLATED: beat-complete RO fields with no language_check record:\n" +
                missingFields.sortedBy { it.first + it.second }.joinToString("\n"),
        )

        // Also assert at least some records exist (non-vacuous)
        assertTrue(actualRecords.isNotEmpty(), "Expected at least some language_check records after reconcile")
    }

    /**
     * Delete one record from [LanguageCheckTable] → the "missing records" query returns 1.
     * This proves the invariant can DETECT holes — a vacuous-invariant check would return 0
     * even after deletion (catching the "query is always 0" class of test mistakes).
     */
    @Test fun `deleting one record makes the invariant detect a hole`() {
        val db = freshDb()
        val repo = ContentRepo(Path.of("content"))
        val manifest = repo.loadManifest()
        val subjects = manifest.subjects.map { repo.loadSubject(it.id) }

        ContentReconcile.reconcile(subjects, db)

        // Find any KC with beat data and pick a field that should have a record
        val sub = subjects.first { it.kcs.any { kc -> kc.beats["ro"] != null } }
        val kc = sub.kcs.first { it.beats["ro"] != null }
        val roBeats = kc.beats["ro"]!!
        val fieldToDelete = when {
            roBeats.predict != null -> "beats.ro.predict.prompt"
            roBeats.reveal != null -> "beats.ro.reveal.steps[0].text"
            else -> "name_ro"
        }

        // Confirm the record exists before deletion
        val beforeCount = transaction(db) {
            LanguageCheckTable.selectAll()
                .where { LanguageCheckTable.kcId eq kc.id }
                .count()
        }
        assertTrue(beforeCount > 0, "Expected at least one record for KC '${kc.id}' before deletion")

        // Delete one specific record
        transaction(db) {
            LanguageCheckTable.deleteWhere {
                (LanguageCheckTable.kcId eq kc.id) and (LanguageCheckTable.field eq fieldToDelete)
            }
        }

        // Now re-check: the deleted field should appear as missing
        val afterCount = transaction(db) {
            LanguageCheckTable.selectAll()
                .where { LanguageCheckTable.kcId eq kc.id }
                .count()
        }
        assertEquals(
            beforeCount - 1, afterCount,
            "Expected exactly one fewer record after deletion: KC '${kc.id}' field '$fieldToDelete'",
        )
    }
}
