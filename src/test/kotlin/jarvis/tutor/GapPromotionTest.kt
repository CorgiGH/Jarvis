package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GapPromotionTest {

    private fun freshCtx(tmp: Path): Triple<Database, String, KnowledgeGapRepo> {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable, KnowledgeGapsTable, FsrsCardsTable,
            )
        }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return Triple(db, userId, KnowledgeGapRepo(db, tmp))
    }

    private fun seedGap(repo: KnowledgeGapRepo, userId: String, topic: String): String {
        val g = KnowledgeGap(
            id = TutorTypes.ulid(), userId = userId, taskId = null, topic = topic,
            language = "kotlin", type = GapType.CONCEPT, trigger = GapTrigger.EXPLICIT_ASK,
            filledAt = Instant.now(), source = GapSource.LLM_GROUNDED,
            content = "$topic primer paragraph",
            exampleCode = "val x = 1",
            sourceCitation = "lecture-3.pdf",
            resolvedBy = null, reusedCount = 0, fsrsCardId = null,
        )
        return repo.append(g)
    }

    @Test
    fun `promote creates a card and back-links onto the gap`(@TempDir tmp: Path) {
        val (db, userId, repo) = freshCtx(tmp)
        val gapId = seedGap(repo, userId, "closures")
        val r = GapPromotion.promote(db, tmp, gapId) ?: error("expected promotion")
        assertTrue(r.createdNew)
        assertEquals("closures", r.front)
        assertTrue(r.back.contains("primer paragraph"))
        assertTrue(r.back.contains("```kotlin"), "fenced code block tagged with language")
        assertTrue(r.back.contains("source: lecture-3.pdf"))
        // Gap row now points back at the card.
        val refreshed = repo.findById(gapId)
        assertEquals(r.cardId, refreshed?.fsrsCardId)
    }

    @Test
    fun `promote is idempotent when fsrs_card_id already set`(@TempDir tmp: Path) {
        val (db, userId, repo) = freshCtx(tmp)
        val gapId = seedGap(repo, userId, "monads")
        val first = GapPromotion.promote(db, tmp, gapId) ?: error("expected")
        val second = GapPromotion.promote(db, tmp, gapId) ?: error("expected")
        assertEquals(first.cardId, second.cardId)
        assertTrue(first.createdNew)
        assertTrue(!second.createdNew, "second call must not insert a duplicate card")
    }

    @Test
    fun `promote returns null for unknown gap id`(@TempDir tmp: Path) {
        val (db, _, _) = freshCtx(tmp)
        assertNull(GapPromotion.promote(db, tmp, gapId = "no-such"))
    }

    @Test
    fun `promote seeds the card with FSRS initial state due in the future`(@TempDir tmp: Path) {
        val (db, userId, repo) = freshCtx(tmp)
        val gapId = seedGap(repo, userId, "futures")
        val before = Instant.now()
        val r = GapPromotion.promote(db, tmp, gapId) ?: error("expected")
        val cards = FsrsCardRepo(db).findDueForUser(userId, asOf = Instant.now().plusSeconds(86400 * 30))
        val card = cards.single { it.id == r.cardId }
        assertEquals(FsrsSource.GAP_PROMOTION, card.source)
        assertEquals(gapId, card.sourceRef)
        assertNotEquals(0.0, card.state.difficulty)
        assertNotEquals(0.0, card.state.stability)
        // Due ~24h after promotion (allow ±5 min slack).
        val expected = before.plusSeconds(86400)
        val diffSec = Math.abs(card.state.dueAt.epochSecond - expected.epochSecond)
        assertTrue(diffSec < 300, "expected due ≈ +1d, got diff $diffSec s")
        assertNotNull(card.state.lastReviewedAt)
    }

    @Test
    fun `renderBack handles missing exampleCode + citation`() {
        val g = KnowledgeGap(
            id = "x", userId = "u", taskId = null, topic = "t", language = null,
            type = GapType.CONCEPT, trigger = GapTrigger.EXPLICIT_ASK,
            filledAt = Instant.now(), source = GapSource.LLM_GROUNDED,
            content = "minimal body", exampleCode = null, sourceCitation = null,
            resolvedBy = null, reusedCount = 0, fsrsCardId = null,
        )
        val back = GapPromotion.renderBack(g)
        assertEquals("minimal body", back)
    }
}
