package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TaskPrepTest {
    private fun freshDb(tmp: Path) =
        TutorDb.connect(tmp.resolve("t.db").toString()).also { db ->
            transaction(db) {
                SchemaUtils.create(UsersTable, TasksTable, TaskPrepTable)
            }
        }

    @Test
    fun `upsert + findByTaskId round-trip`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = TaskPrepRepo(db)
        repo.upsert(TaskPrep(
            taskId = "01ABC",
            generatedAt = Instant.now(),
            version = 1,
            problemsJson = """[{"problem_id":"A1","page":4,"statement":"derive MLE"}]""",
            drillsJson = """{"A1":{"definition":"...","drill":"..."}}""",
            railJson = """[{"type":"PDF","title":"Tema_A.pdf","page":4}]""",
        ))
        val found = repo.findByTaskId("01ABC")
        assertNotNull(found)
        assertEquals(1, found.version)
        assertEquals("01ABC", found.taskId)
    }

    @Test
    fun `findByTaskId returns null on miss`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertNull(TaskPrepRepo(db).findByTaskId("missing"))
    }

    @Test
    fun `upsert is idempotent`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = TaskPrepRepo(db)
        val now = Instant.now()
        repo.upsert(TaskPrep("01X", now, 1, "[]", "{}", "[]"))
        repo.upsert(TaskPrep("01X", now.plusSeconds(60), 2, "[]", "{}", "[]"))
        val found = repo.findByTaskId("01X")
        assertEquals(2, found?.version)
    }
}
