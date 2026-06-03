package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.sql.DriverManager

/**
 * Task 9 (B2, §H) — FsrsCardRepo.upsertRubricCriterion(tx, …) + (userId, source, kcId) index.
 *
 * TDD: these tests are written FIRST and must fail (unresolved reference) until the
 * implementation lands in FsrsCards.kt.
 *
 * Tests:
 *  1. First call inserts: source=RUBRIC_CRITERION, kc_id=kcId, exactly 1 row.
 *  2. Second call same (userId, kcId) UPDATES not duplicates — still exactly 1 row (B2 fix).
 *  3. Different kcId inserts a second row (different dedup key).
 *  4. Runs in caller's txn — row rolls back if enclosing txn fails.
 *  5. The (user_id, source, kc_id) index exists after migration.
 *  6. GAP_PROMOTION cards inserted via insert() keep kc_id=NULL.
 */
class UpsertRubricCriterionTest {

    private fun freshDb(tmp: Path): Pair<org.jetbrains.exposed.sql.Database, String> {
        val db = TutorDb.connect(tmp.resolve("u.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, FsrsCardsTable) }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return db to userId
    }

    private fun defaultState(): FsrsState {
        val now = Instant.now()
        return FsrsState(
            difficulty = 5.0,
            stability = 1.0,
            retrievability = 0.9,
            dueAt = now.plusSeconds(86400),
            lastReviewedAt = now,
            lapses = 0,
        )
    }

    private fun countCards(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String): Long =
        transaction(db) {
            FsrsCardsTable.selectAll()
                .where {
                    (FsrsCardsTable.userId eq userId) and
                        (FsrsCardsTable.sourceType eq FsrsSource.RUBRIC_CRITERION.name) and
                        (FsrsCardsTable.kcId eq kcId)
                }
                .count()
        }

    @Test
    fun `first call inserts with source RUBRIC_CRITERION and kc_id`(@TempDir tmp: Path) {
        val (db, userId) = freshDb(tmp)
        val repo = FsrsCardRepo(db)
        val card = transaction(db) {
            repo.upsertRubricCriterion(this, userId, "pa-kc-001", "front text", "back text", defaultState())
        }
        assertNotNull(card)
        assertEquals(FsrsSource.RUBRIC_CRITERION, card.source)
        assertEquals("pa-kc-001", card.kcId)
        assertEquals("front text", card.front)
        assertEquals("back text", card.back)
        assertEquals(userId, card.userId)
        assertEquals(1L, countCards(db, userId, "pa-kc-001"))
    }

    @Test
    fun `second call same userId and kcId updates not duplicates (B2 fix)`(@TempDir tmp: Path) {
        val (db, userId) = freshDb(tmp)
        val repo = FsrsCardRepo(db)
        // First call
        transaction(db) {
            repo.upsertRubricCriterion(this, userId, "pa-kc-001", "front-v1", "back-v1", defaultState())
        }
        // Second call — different front/back
        val card2 = transaction(db) {
            repo.upsertRubricCriterion(this, userId, "pa-kc-001", "front-v2", "back-v2", defaultState())
        }
        // Still exactly 1 row (no duplicate)
        assertEquals(1L, countCards(db, userId, "pa-kc-001"), "must not duplicate — exactly 1 row for (userId, RUBRIC_CRITERION, kcId)")
        // The returned card has the updated front/back
        assertEquals("front-v2", card2.front)
        assertEquals("back-v2", card2.back)
    }

    @Test
    fun `different kcId inserts a second row`(@TempDir tmp: Path) {
        val (db, userId) = freshDb(tmp)
        val repo = FsrsCardRepo(db)
        transaction(db) {
            repo.upsertRubricCriterion(this, userId, "pa-kc-001", "f1", "b1", defaultState())
        }
        transaction(db) {
            repo.upsertRubricCriterion(this, userId, "pa-kc-002", "f2", "b2", defaultState())
        }
        assertEquals(1L, countCards(db, userId, "pa-kc-001"))
        assertEquals(1L, countCards(db, userId, "pa-kc-002"))
        // total RUBRIC_CRITERION rows for this user = 2
        val total = transaction(db) {
            FsrsCardsTable.selectAll()
                .where {
                    (FsrsCardsTable.userId eq userId) and
                        (FsrsCardsTable.sourceType eq FsrsSource.RUBRIC_CRITERION.name)
                }
                .count()
        }
        assertEquals(2L, total)
    }

    @Test
    fun `upsert runs in caller txn — row rolls back on enclosing txn failure`(@TempDir tmp: Path) {
        val (db, userId) = freshDb(tmp)
        val repo = FsrsCardRepo(db)

        try {
            transaction(db) {
                repo.upsertRubricCriterion(this, userId, "pa-kc-003", "f", "b", defaultState())
                // Force rollback
                throw RuntimeException("forced rollback")
            }
        } catch (_: Throwable) { /* expected */ }

        assertEquals(0L, countCards(db, userId, "pa-kc-003"), "row must have rolled back with the txn")
    }

    @Test
    fun `index (user_id, source, kc_id) exists after schema creation`(@TempDir tmp: Path) {
        // Verify the composite index is present by querying sqlite_master.
        val db = TutorDb.connect(tmp.resolve("idx.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, FsrsCardsTable) }

        val dbPath = tmp.resolve("idx.db").toString()
        val indexExists = DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            val rs = conn.createStatement().executeQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='fsrs_cards'"
            )
            val names = mutableListOf<String>()
            while (rs.next()) names.add(rs.getString(1))
            // Look for an index covering all three columns; Exposed names it by the columns
            names.any { name ->
                // The index Exposed generates for uniqueIndex/index(cols...) is typically
                // "fsrs_cards_user_id_source_kc_id" or similar — check sqlite_master index_info
                val infoRs = conn.createStatement().executeQuery(
                    "SELECT * FROM pragma_index_info('$name') ORDER BY seqno"
                )
                val cols = mutableListOf<String>()
                while (infoRs.next()) cols.add(infoRs.getString("name"))
                cols == listOf("user_id", "source", "kc_id")
            }
        }
        assertTrue(indexExists, "a composite index on (user_id, source, kc_id) must exist in fsrs_cards")
    }

    @Test
    fun `GAP_PROMOTION card inserted via insert keeps kc_id null`(@TempDir tmp: Path) {
        val (db, userId) = freshDb(tmp)
        val repo = FsrsCardRepo(db)
        val now = Instant.now()
        val card = TutorCard(
            TutorTypes.ulid(), userId, FsrsSource.GAP_PROMOTION, "gap-ref",
            "f", "b", FsrsState(5.0, 1.0, 0.9, now.plusSeconds(3600), now, 0),
            kcId = null,
        )
        repo.insert(card)
        val fetched = repo.findById(card.id, userId)
        assertNotNull(fetched)
        assertEquals(FsrsSource.GAP_PROMOTION, fetched.source)
        assertNull(fetched.kcId, "GAP_PROMOTION cards must keep kc_id=NULL")
    }
}
