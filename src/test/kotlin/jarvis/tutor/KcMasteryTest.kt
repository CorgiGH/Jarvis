package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class KcMasteryTest {
    private fun freshDb(tmp: Path): Database {
        val db = Database.connect("jdbc:sqlite:${tmp.resolve("m.db")}", "org.sqlite.JDBC")
        transaction(db) { SchemaUtils.create(KcMasteryTable) }
        return db
    }

    @Test fun `first observation seeds ewma to the score`(@TempDir tmp: Path) {
        val repo = KcMasteryRepo(freshDb(tmp))
        assertNull(repo.get("u1", "pa-kc-001"))
        val m = repo.record("u1", "pa-kc-001", 1.0)
        assertEquals(1.0, m.ewmaScore, 1e-9)
        assertEquals(1, m.observations)
        assertFalse(m.mastered) // observations < MIN_OBSERVATIONS
    }

    @Test fun `ewma blends prior and new with alpha`(@TempDir tmp: Path) {
        val repo = KcMasteryRepo(freshDb(tmp))
        repo.record("u1", "pa-kc-001", 1.0)        // ewma 1.0
        val m = repo.record("u1", "pa-kc-001", 0.0) // 0.4*0 + 0.6*1.0 = 0.6
        assertEquals(0.6, m.ewmaScore, 1e-9)
        assertEquals(2, m.observations)
    }

    @Test fun `mastered after sustained high ewma over enough observations`(@TempDir tmp: Path) {
        val repo = KcMasteryRepo(freshDb(tmp))
        repo.record("u1", "pa-kc-001", 1.0)
        repo.record("u1", "pa-kc-001", 1.0)
        val m = repo.record("u1", "pa-kc-001", 1.0)
        assertTrue(m.mastered) // ewma 1.0 >= 0.8 and observations 3 >= 3
    }
}
