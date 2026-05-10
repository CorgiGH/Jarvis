package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TasksTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("tasks-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, TasksTable) } }

    private fun seedUser(db: org.jetbrains.exposed.sql.Database): String {
        val id = TutorTypes.ulid()
        UserRepo(db).insert(User(id, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return id
    }

    private fun makeTask(userId: String, deadline: Instant, title: String = "Tema ${TutorTypes.ulid().takeLast(6)}") = Task(
        id = TutorTypes.ulid(), userId = userId, subject = "PS",
        title = title, deadline = deadline,
        problemRef = ContentRef("study-guide", "ps/tema-a/problem.md", "abc"),
        conceptRefs = listOf(ContentRef("study-guide", "ps/laplace.md", "def")),
        rubricRef = ContentRef("study-guide", "ps/tema-a/rubric.v1.yaml", "ghi"),
        scratchpad = null, submission = null, grade = null, cardRefs = emptyList(),
        status = TaskStatus.TODO,
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @Test
    fun `insert and findById round-trips`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TaskRepo(db)
        val t = makeTask(u, Instant.now().plusSeconds(3600))
        repo.insert(t)
        val found = repo.findById(t.id)
        assertNotNull(found)
        assertEquals(t.title, found.title)
        assertEquals(1, found.conceptRefs.size)
    }

    @Test
    fun `listForUser returns deadline-sorted ascending`() {
        val db = freshDb()
        val u = seedUser(db)
        val repo = TaskRepo(db)
        val now = Instant.now()
        val a = makeTask(u, now.plusSeconds(100))
        val b = makeTask(u, now.plusSeconds(10))
        val c = makeTask(u, now.plusSeconds(1000))
        repo.insert(a); repo.insert(b); repo.insert(c)
        val ordered = repo.listForUser(u)
        assertEquals(listOf(b.id, a.id, c.id), ordered.map { it.id })
    }

    @Test
    fun `listForUser scopes to userId`() {
        val db = freshDb()
        val u1 = seedUser(db); val u2 = seedUser(db)
        val repo = TaskRepo(db)
        repo.insert(makeTask(u1, Instant.now().plusSeconds(60)))
        repo.insert(makeTask(u2, Instant.now().plusSeconds(60)))
        assertEquals(1, repo.listForUser(u1).size)
        assertEquals(1, repo.listForUser(u2).size)
    }
}
