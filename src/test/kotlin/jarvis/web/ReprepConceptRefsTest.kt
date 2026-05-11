package jarvis.web

import jarvis.HybridRetriever
import jarvis.tutor.ContentRef
import jarvis.tutor.Task
import jarvis.tutor.TaskRepo
import jarvis.tutor.TaskStatus
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import jarvis.tutor.TasksTable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that the concept-ref population logic (Phase C5) correctly:
 * 1. Runs HybridRetriever against a corpus dir for a problem statement.
 * 2. Accumulates and deduplicates paths into conceptRefs.
 * 3. Subject-scopes hits when at least one hit matches `_extras/<subject>/`.
 */
class ReprepConceptRefsTest {

    /**
     * Reproduces the concept-ref accumulation logic from the /reprep handler.
     * Extracted here as a pure function so the test doesn't need a live Ktor server
     * or an LLM — only the corpus fs + HybridRetriever lexical path.
     */
    private suspend fun populateConceptRefs(
        statements: List<String>,
        subject: String,
        archivalRoot: Path,
        k: Int = 3,
    ): List<ContentRef> {
        val conceptRefs = mutableListOf<ContentRef>()
        for (stmt in statements) {
            val hits = HybridRetriever.search(stmt, k = k, archivalRoot = archivalRoot, semanticEmbed = null)
            // Normalize path separators to forward-slash for cross-platform consistency
            conceptRefs += hits.map { ContentRef(repo = "corpus", path = it.id.replace('\\', '/'), sha = "") }
        }
        // Subject-scope: if ≥1 hit matches _extras/<subject>/ keep only those; else full-corpus fallback
        val subjectPrefix = "_extras/${subject}/"
        val subjectScoped = conceptRefs.filter { it.path.startsWith(subjectPrefix) }
        val finalRefs = if (subjectScoped.isNotEmpty()) subjectScoped else conceptRefs
        return finalRefs.distinctBy { it.path }
    }

    @Test
    fun `concept refs populated from lexical corpus hit`(@TempDir tmp: Path) {
        // Build a tiny corpus under _extras/PS/
        val corpusRoot = tmp.resolve("archival")
        val psDir = corpusRoot.resolve("_extras").resolve("PS").resolve("courses")
        Files.createDirectories(psDir)
        val mdFile = psDir.resolve("ps_c4.md")
        Files.writeString(mdFile, """
            # Laplace Distribution

            The Laplace distribution MLE estimator for location parameter is the sample median.
            The scale parameter MLE is the mean absolute deviation from the median.

            Key property: double-exponential shape, heavier tails than Gaussian.
        """.trimIndent())

        val statements = listOf("Laplace distribution MLE estimator")
        val refs = runBlocking {
            populateConceptRefs(statements, "PS", corpusRoot, k = 3)
        }

        assertTrue(refs.isNotEmpty(), "Expected at least 1 concept ref from corpus hit")
        val paths = refs.map { it.path }
        // Subject-scope: all refs must be under _extras/PS/ when hits exist there
        assertTrue(
            paths.all { it.startsWith("_extras/PS/") },
            "Subject-scoped refs must be under _extras/PS/, got: $paths"
        )
        assertTrue(
            paths.any { it.contains("ps_c4") },
            "Expected ps_c4.md in concept refs, got: $paths"
        )
    }

    @Test
    fun `subject-scope falls back to full corpus when no subject hits`(@TempDir tmp: Path) {
        // Build corpus in a different subject dir than what we query for
        val corpusRoot = tmp.resolve("archival")
        val mathDir = corpusRoot.resolve("_extras").resolve("MATH").resolve("courses")
        Files.createDirectories(mathDir)
        Files.writeString(mathDir.resolve("math_c1.md"), """
            # Laplace Transform

            The Laplace transform converts a time-domain function into a complex frequency domain.
            Used extensively in control systems and signal processing.
        """.trimIndent())

        val statements = listOf("Laplace transform signal processing")
        // Query with subject "PS" which has no hits — should fall back to full corpus
        val refs = runBlocking {
            populateConceptRefs(statements, "PS", corpusRoot, k = 3)
        }

        // Full-corpus fallback: result can include MATH hits since PS has none
        assertTrue(refs.isNotEmpty(), "Expected corpus fallback to yield results")
        // Verify deduplication: each path appears only once
        val paths = refs.map { it.path }
        assertTrue(paths.size == paths.distinct().size, "Paths must be deduplicated")
    }

    @Test
    fun `TaskRepo updateConceptRefs persists refs and findById reads them back`(@TempDir tmp: Path) {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, TasksTable)
        }
        val userId = TutorTypes.ulid()
        val now = Instant.now()
        UserRepo(db).insert(User(userId, "test", UserScope.OWNER, now, now))
        val taskId = TutorTypes.ulid()
        val repo = TaskRepo(db)
        repo.insert(Task(
            id = taskId, userId = userId, subject = "PS", title = "T-conceptrefs",
            deadline = now.plusSeconds(86400),
            problemRef = ContentRef("user", "exam.pdf", "abc"),
            conceptRefs = emptyList(),
            rubricRef = ContentRef("user", "exam.pdf", "abc"),
            scratchpad = null, submission = null, grade = null,
            cardRefs = emptyList(),
            status = TaskStatus.ACTIVE,
            createdAt = now, updatedAt = now,
        ))

        val refs = listOf(
            ContentRef(repo = "corpus", path = "_extras/PS/courses/ps_c4.md", sha = ""),
            ContentRef(repo = "corpus", path = "_extras/PS/courses/ps_c5.md", sha = ""),
        )
        val updated = repo.updateConceptRefs(taskId, refs)
        assertTrue(updated)

        val loaded = repo.findById(taskId)
        assertTrue(loaded != null)
        val loadedPaths = loaded.conceptRefs.map { it.path }
        assertTrue(loadedPaths.contains("_extras/PS/courses/ps_c4.md"))
        assertTrue(loadedPaths.contains("_extras/PS/courses/ps_c5.md"))
    }
}
