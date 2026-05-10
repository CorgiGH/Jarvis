package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GapRepoTest {

    private fun freshCtx(tmp: Path): Pair<KnowledgeGapRepo, String> {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, KnowledgeGapsTable)
        }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return KnowledgeGapRepo(db, tmp) to userId
    }

    private fun mkGap(userId: String, topic: String, taskId: String? = null) = KnowledgeGap(
        id = TutorTypes.ulid(), userId = userId, taskId = taskId, topic = topic,
        language = "kotlin", type = GapType.CONCEPT, trigger = GapTrigger.EXPLICIT_ASK,
        filledAt = Instant.now(), source = GapSource.LLM_GROUNDED,
        content = "explanation of $topic", exampleCode = "val x = 1",
        sourceCitation = null, resolvedBy = null, reusedCount = 0, fsrsCardId = null,
    )

    @Test
    fun `upsertByTriple inserts on miss + bumps reusedCount on hit`(@TempDir tmp: Path) {
        val (repo, userId) = freshCtx(tmp)
        val g = mkGap(userId, "closures", taskId = "T1")
        val id1 = repo.upsertByTriple(g, taskId = "T1", content = g.content)
        assertNotNull(repo.findById(id1))
        val id2 = repo.upsertByTriple(mkGap(userId, "closures", taskId = "T1"), taskId = "T1", content = "x")
        assertEquals(id1, id2)
        assertEquals(1, repo.findById(id1)?.reusedCount)
    }

    @Test
    fun `markResolved + incrementReused update values`(@TempDir tmp: Path) {
        val (repo, userId) = freshCtx(tmp)
        val id = repo.upsertByTriple(mkGap(userId, "lambdas", "T2"), taskId = "T2", content = "x")
        assertTrue(repo.markResolved(id, GapResolved.USER_TYPED))
        assertEquals(GapResolved.USER_TYPED, repo.findById(id)?.resolvedBy)
        assertTrue(repo.incrementReused(id))
        assertEquals(1, repo.findById(id)?.reusedCount)
    }

    @Test
    fun `listForTask scopes to single task + ascending createdAt`(@TempDir tmp: Path) {
        val (repo, userId) = freshCtx(tmp)
        repo.upsertByTriple(mkGap(userId, "a", "T3"), taskId = "T3", content = "x")
        Thread.sleep(5)
        repo.upsertByTriple(mkGap(userId, "b", "T3"), taskId = "T3", content = "x")
        repo.upsertByTriple(mkGap(userId, "c", "T4"), taskId = "T4", content = "x")
        val t3 = repo.listForTask(userId, "T3")
        assertEquals(2, t3.size)
        assertEquals(listOf("a", "b"), t3.map { it.topic })
    }
}
