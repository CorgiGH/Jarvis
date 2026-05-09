package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnowledgeGapsTest {
    @Test
    fun `append writes JSONL line and returns gap id`() {
        val dir = Files.createTempDirectory("gaps")
        val db = TutorDb.connect(dir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, KnowledgeGapsTable) }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = KnowledgeGapRepo(db, dir)
        val g = KnowledgeGap(
            id = TutorTypes.ulid(), userId = u, taskId = null,
            topic = "R: median()", language = "R", type = GapType.COMMAND,
            trigger = GapTrigger.EXPLICIT_ASK,
            filledAt = Instant.now(),
            source = GapSource.LLM_GROUNDED,
            content = "median(x) computes...",
            exampleCode = "median(c(1,2,3))",
            sourceCitation = "?median",
            resolvedBy = null, reusedCount = 0, fsrsCardId = null,
        )
        val id = repo.append(g)
        assertEquals(g.id, id)
        val file = dir.resolve("knowledge_gaps_$u.jsonl")
        val lines = Files.readAllLines(file)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("R: median()"))
    }

    @Test
    fun `findByTopic finds prior gap by exact topic for user`() {
        val dir = Files.createTempDirectory("gaps2")
        val db = TutorDb.connect(dir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, KnowledgeGapsTable) }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = KnowledgeGapRepo(db, dir)
        val g = KnowledgeGap(TutorTypes.ulid(), u, null, "R: median()", "R", GapType.COMMAND,
            GapTrigger.EXPLICIT_ASK, Instant.now(), GapSource.LLM_GROUNDED,
            "x", null, null, null, 0, null)
        repo.append(g)
        val found = repo.findByTopic(u, "R: median()")
        assertNotNull(found)
        assertEquals(g.id, found.id)
    }

    @Test
    fun `bumpReuse increments counter`() {
        val dir = Files.createTempDirectory("gaps3")
        val db = TutorDb.connect(dir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, KnowledgeGapsTable) }
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = KnowledgeGapRepo(db, dir)
        val g = KnowledgeGap(TutorTypes.ulid(), u, null, "R: median()", "R", GapType.COMMAND,
            GapTrigger.EXPLICIT_ASK, Instant.now(), GapSource.LLM_GROUNDED,
            "x", null, null, null, 0, null)
        repo.append(g)
        repo.bumpReuse(g.id)
        repo.bumpReuse(g.id)
        assertEquals(2, repo.findByTopic(u, "R: median()")!!.reusedCount)
    }
}
