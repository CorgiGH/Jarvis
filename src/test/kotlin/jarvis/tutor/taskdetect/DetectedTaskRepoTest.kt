package jarvis.tutor.taskdetect

import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectedTaskRepoTest {
    private fun freshRepo(tmp: Path): Pair<DetectedTaskRepo, String> {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) { SchemaUtils.createMissingTablesAndColumns(UsersTable, DetectedTaskMappingTable) }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return DetectedTaskRepo(db) to userId
    }

    @Test
    fun `findExisting returns null when no mapping`(@TempDir tmp: Path) {
        val (repo, _) = freshRepo(tmp)
        assertNull(repo.findExisting("src1", "ext1"))
    }

    @Test
    fun `upsertMapping inserts then returns same taskId on re-upsert`(@TempDir tmp: Path) {
        val (repo, userId) = freshRepo(tmp)
        val taskId = TutorTypes.ulid()
        val a = repo.upsertMapping("src1", "ext1", taskId, userId)
        assertEquals(taskId, a)
        val b = repo.upsertMapping("src1", "ext1", "OTHER-ID", userId)
        assertEquals(taskId, b)
        assertEquals(taskId, repo.findExisting("src1", "ext1"))
    }

    @Test
    fun `aggregator dedups across sources by (sourceId, externalId)`() = runBlocking {
        val src = object : TaskDetector {
            override val sourceId = "src-test"
            override suspend fun discover(): List<DetectedTask> = listOf(
                DetectedTask("src-test", "e1", "PA", "Tema A", Instant.now()),
                DetectedTask("src-test", "e1", "PA", "Tema A duplicate", Instant.now()),
                DetectedTask("src-test", "e2", "PS", "Tema B", Instant.now()),
            )
        }
        val agg = TaskDetectorAggregator(listOf(src))
        val out = agg.discoverAll()
        assertEquals(2, out.size)
        assertEquals(setOf("e1", "e2"), out.map { it.externalId }.toSet())
    }
}
