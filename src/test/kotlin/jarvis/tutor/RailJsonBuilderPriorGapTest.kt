package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RailJsonBuilderPriorGapTest {

    private fun freshDb(tmp: Path): Triple<org.jetbrains.exposed.sql.Database, String, String> {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                TasksTable,
                KnowledgeGapsTable,
                FsrsCardsTable,
            )
        }
        val userId = TutorTypes.ulid()
        val now = Instant.now()
        UserRepo(db).insert(User(userId, "test", UserScope.OWNER, now, now))
        val taskId = TutorTypes.ulid()
        TaskRepo(db).insert(Task(
            id = taskId, userId = userId, subject = "PS", title = "T-gap",
            deadline = now.plusSeconds(86400),
            problemRef = ContentRef("user", "exam.pdf", "abc123"),
            conceptRefs = emptyList(),
            rubricRef = ContentRef("user", "exam.pdf", "abc123"),
            scratchpad = null, submission = null, grade = null,
            cardRefs = emptyList(),
            status = TaskStatus.ACTIVE,
            createdAt = now, updatedAt = now,
        ))
        return Triple(db, userId, taskId)
    }

    private fun mkGap(userId: String, taskId: String, topic: String, resolved: Boolean = false) = KnowledgeGap(
        id = TutorTypes.ulid(), userId = userId, taskId = taskId, topic = topic,
        language = null, type = GapType.CONCEPT, trigger = GapTrigger.EXPLICIT_ASK,
        filledAt = Instant.now(), source = GapSource.LLM_GROUNDED,
        content = "explanation of $topic", exampleCode = null,
        sourceCitation = null,
        resolvedBy = if (resolved) GapResolved.USER_TYPED else null,
        reusedCount = 0, fsrsCardId = null,
    )

    @Test
    fun `buildForTask emits PRIOR_GAP items for unresolved gaps`(@TempDir tmp: Path) {
        val (db, userId, taskId) = freshDb(tmp)
        val gapRepo = KnowledgeGapRepo(db, tmp)

        // Insert 2 unresolved gaps + 1 resolved gap
        val g1 = mkGap(userId, taskId, "Laplace distribution")
        val g2 = mkGap(userId, taskId, "MLE estimator")
        val g3Resolved = mkGap(userId, taskId, "Central limit theorem", resolved = true)
        gapRepo.append(g1)
        gapRepo.append(g2)
        gapRepo.append(g3Resolved)

        val items = RailJsonBuilder.buildForTask(db, taskId, userId, gapRepo)
        val priorGapItems = items.filter { it["type"] == "PRIOR_GAP" }

        assertEquals(2, priorGapItems.size, "Expected 2 PRIOR_GAP items (resolved gap excluded)")
        val topics = priorGapItems.map { it["label"] }.toSet()
        assertTrue(topics.contains("Laplace distribution"))
        assertTrue(topics.contains("MLE estimator"))
        assertTrue(!topics.contains("Central limit theorem"), "Resolved gap must NOT appear")

        // Verify payload contains gapId
        priorGapItems.forEach { item ->
            @Suppress("UNCHECKED_CAST")
            val payload = item["payload"] as? Map<String, Any?>
            assertTrue(payload?.containsKey("gapId") == true, "PRIOR_GAP item must have gapId in payload")
        }
    }

    @Test
    fun `buildForTask emits no PRIOR_GAP items when no gaps exist`(@TempDir tmp: Path) {
        val (db, userId, taskId) = freshDb(tmp)
        val gapRepo = KnowledgeGapRepo(db, tmp)

        val items = RailJsonBuilder.buildForTask(db, taskId, userId, gapRepo)
        val priorGapItems = items.filter { it["type"] == "PRIOR_GAP" }
        assertEquals(0, priorGapItems.size)
        // PDF and SCRATCHPAD still present
        val types = items.map { it["type"] }
        assertTrue(types.contains("PDF"))
        assertTrue(types.contains("SCRATCHPAD"))
    }
}
