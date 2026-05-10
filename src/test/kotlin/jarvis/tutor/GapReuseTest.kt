package jarvis.tutor

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GapReuseTest {
    private fun freshRepo(tmp: Path): Pair<KnowledgeGapRepo, String> {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        org.jetbrains.exposed.sql.transactions.transaction(db) {
            org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns(UsersTable, KnowledgeGapsTable)
        }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return KnowledgeGapRepo(db, tmp) to userId
    }

    private fun mkGap(userId: String, topic: String, taskId: String? = null) = KnowledgeGap(
        id = TutorTypes.ulid(), userId = userId, taskId = taskId, topic = topic,
        language = "kotlin", type = GapType.CONCEPT, trigger = GapTrigger.EXPLICIT_ASK,
        filledAt = Instant.now(), source = GapSource.LLM_GROUNDED,
        content = "explain $topic", exampleCode = null, sourceCitation = null,
        resolvedBy = null, reusedCount = 0, fsrsCardId = null,
    )

    @Test
    fun `findSimilar returns hits above Jaccard threshold`(@TempDir tmp: Path) {
        val (repo, userId) = freshRepo(tmp)
        repo.upsertByTriple(mkGap(userId, "laplace transform", "T1"), taskId = "T1", content = "x")
        repo.upsertByTriple(mkGap(userId, "fourier transform", "T2"), taskId = "T2", content = "x")
        repo.upsertByTriple(mkGap(userId, "linear algebra"  , "T3"), taskId = "T3", content = "x")
        val hits = repo.findSimilar(userId, "laplace transform method", threshold = 0.5, k = 5)
        assertTrue(hits.isNotEmpty())
        assertEquals("laplace transform", hits.first().first.topic)
    }

    @Test
    fun `findSimilar empty when nothing above threshold`(@TempDir tmp: Path) {
        val (repo, userId) = freshRepo(tmp)
        repo.upsertByTriple(mkGap(userId, "closures", "T1"), taskId = "T1", content = "x")
        val hits = repo.findSimilar(userId, "binary search trees", threshold = 0.75, k = 5)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `findSimilar empty topic returns empty`(@TempDir tmp: Path) {
        val (repo, userId) = freshRepo(tmp)
        assertTrue(repo.findSimilar(userId, "", threshold = 0.5, k = 5).isEmpty())
    }
}
