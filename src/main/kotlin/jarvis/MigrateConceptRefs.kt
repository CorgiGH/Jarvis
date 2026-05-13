package jarvis

import jarvis.tutor.ContentRef
import jarvis.tutor.Problem
import jarvis.tutor.TaskPrepRepo
import jarvis.tutor.TaskRepo
import jarvis.tutor.TasksTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import kotlin.io.path.exists
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * One-shot migration: for every task with empty concept_refs_json, populate
 * concept refs from existing problems_json via HybridRetriever lexical search.
 *
 * NOT a re-prep — does not call the LLM. Reuses problems already extracted in
 * a prior /reprep run (cached in task_prep.problems_json). If a task has never
 * been prep'd, its conceptRefs will be derived from {subject + title} as a
 * minimum-signal fallback rather than re-running PdfProblemExtractor.
 */
fun runMigrateConceptRefs() {
    val db = TutorDb.connect(Config.tutorDbPath)
    val taskRepo = TaskRepo(db)
    val prepRepo = TaskPrepRepo(db)

    data class Cand(val id: String, val subject: String, val title: String)
    val candidates: List<Cand> = transaction(db) {
        TasksTable.selectAll()
            .where { (TasksTable.conceptRefsJson eq "") or (TasksTable.conceptRefsJson eq "[]") }
            .map { Cand(it[TasksTable.id], it[TasksTable.subject], it[TasksTable.title]) }
    }
    if (candidates.isEmpty()) {
        println("migrate-concept-refs: 0 tasks need migration; exiting.")
        return
    }
    println("migrate-concept-refs: ${candidates.size} tasks need conceptRefs:")
    candidates.forEach { println("  - ${it.id}  subject=${it.subject}  title='${it.title.take(60)}'") }

    var updated = 0
    var skipped = 0
    for (cand in candidates) {
        val prep = prepRepo.findByTaskId(cand.id)
        val problems: List<Problem> = if (prep != null && prep.problemsJson.isNotBlank()) {
            try {
                TutorTypes.tutorJson.decodeFromString(
                    ListSerializer(Problem.serializer()), prep.problemsJson)
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        // Drop the subject token from fallback query — it leaks into the
        // entity-pass as a generic 2-letter substring (e.g. "PA" matches "pa"
        // in any Romanian text) and floods hits with cross-subject material.
        // The subject-scoped search below already enforces the boundary.
        val queries: List<String> = when {
            problems.isNotEmpty() -> problems.map { it.statement }
            else -> listOf(cand.title)
        }

        // Subject-scoped search: confine archival root to `_extras/<subject>/`
        // so we never need to filter cross-subject hits out post-hoc. Within
        // that root HybridRetriever gives us entity-pass tokenization (the
        // whole-string substring lex pass would 0-match a title like
        // "Tema 5 — dynamic programming"). Falls back to corpus-wide
        // HybridRetriever only when subject dir is missing.
        val subjectDir = Config.archivalDir.resolve("_extras/${cand.subject}")
        val hits = mutableListOf<ContentRef>()
        for (q in queries) {
            val rawHits = if (subjectDir.exists()) {
                runBlocking {
                    HybridRetriever.search(q, k = 5, archivalRoot = subjectDir, semanticEmbed = null)
                }.map { "_extras/${cand.subject}/${it.id.replace('\\', '/')}" }
            } else {
                runBlocking { HybridRetriever.search(q, k = 5, semanticEmbed = null) }
                    .map { it.id.replace('\\', '/') }
            }
            hits += rawHits.map { path -> ContentRef(repo = "corpus", path = path, sha = "") }
        }

        val subjectPrefix = "_extras/${cand.subject}/"
        val subjectScoped = hits.filter { it.path.startsWith(subjectPrefix) }.distinctBy { it.path }

        // Subject-scoped strict: do NOT fall back to corpus-wide hits.
        // Cross-subject conceptRefs (e.g. SO content on a PA task) confuse
        // the sidekick. Leave conceptRefs empty rather than seed wrong subject;
        // a real /reprep from UI will populate correctly once a real problem
        // statement is available.
        val finalRefs = subjectScoped

        if (finalRefs.isEmpty()) {
            println("  SKIP ${cand.id} — 0 subject-scoped hits (queries=${queries.size}, subject=${cand.subject})")
            skipped++
            continue
        }

        val ok = taskRepo.updateConceptRefs(cand.id, finalRefs)
        if (ok) {
            updated++
            println("  OK   ${cand.id}  refs=${finalRefs.size}  scope=${if (subjectScoped.isNotEmpty()) "subject" else "corpus"}")
            finalRefs.forEach { println("       - ${it.path}") }
        } else {
            println("  FAIL ${cand.id} — UPDATE returned 0 rows")
            skipped++
        }
    }
    println("\nDone. updated=$updated  skipped=$skipped  total=${candidates.size}")
}
