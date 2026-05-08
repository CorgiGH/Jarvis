package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TutorDbTest {
    @Test
    fun `connect opens sqlite at given path and enables WAL`() {
        val tmp = Files.createTempDirectory("tutor-db-test").resolve("test.db")
        val db: Database = TutorDb.connect(tmp.toString())
        assertNotNull(db)
        transaction(db) {
            val journalMode = exec("PRAGMA journal_mode") { rs ->
                rs.next(); rs.getString(1)
            }
            assertEquals("wal", journalMode?.lowercase())
        }
    }
}
