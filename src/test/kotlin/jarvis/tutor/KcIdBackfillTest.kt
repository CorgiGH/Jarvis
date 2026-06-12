package jarvis.tutor

import jarvis.content.KnowledgeConcept
import jarvis.content.SourceRef
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-2 Task 8 (spec §3.6 step 4 / §0.8 #2) — kc_id backfill is CONFIDENT-MATCH ONLY:
 * a link exists iff a card's normalized source-doc stem == a KC's normalized source[*].doc
 * stem. No fuzzy/similarity scoring. The production reality is 0 links (PA cards =
 * lecture11/seminar9_10; PA KCs = pa-lecture-01) — a 0-link run is the CORRECT result.
 * applyLinks NEVER overwrites a non-null kc_id.
 */
class KcIdBackfillTest {

    private fun kc(id: String, subject: String, docs: List<String>): KnowledgeConcept =
        KnowledgeConcept(
            id = id, subject = subject, name_ro = id, name_en = id, cluster = "c",
            bloom_level = "understand", difficulty = 1, time_minutes = 1, exam_weight = 0.0,
            tier = 1, source = docs.map { SourceRef(doc = it, quote = "q") },
        )

    private fun card(id: String, sourceRef: String) = KcIdBackfill.CardRef(id = id, sourceRef = sourceRef)

    @Test
    fun `exact normalized stem match links the card`() {
        // card "PA:lecture-01.md" -> stem "lecture 01"; KC doc "pa-lecture-01" (subject PA) ->
        // stems {"pa lecture 01", "lecture 01"} via kcDocStems — the subject marker is
        // non-identifying on both sides (PM reconciliation 2026-06-12), so the link holds.
        val links = KcIdBackfill.computeLinks(
            cards = listOf(card("c1", "PA:lecture-01.md")),
            kcs = listOf(kc("pa-kc-001", "PA", listOf("pa-lecture-01"))),
        )
        assertEquals(listOf(KcIdBackfill.Link(cardId = "c1", kcId = "pa-kc-001", subject = "PA")), links)
    }

    @Test
    fun `a stem claimed by TWO distinct KCs is ambiguous and links NOTHING`() {
        // Both KCs cite the same doc -> the stem is ambiguous -> dropped (no forced guesses;
        // a silent first-wins would be a hidden guess).
        val links = KcIdBackfill.computeLinks(
            cards = listOf(card("c1", "PA:lecture-01.md")),
            kcs = listOf(
                kc("pa-kc-001", "PA", listOf("pa-lecture-01")),
                kc("pa-kc-002", "PA", listOf("pa-lecture-01")),
            ),
        )
        assertEquals(emptyList(), links)
    }

    @Test
    fun `near miss does NOT link (lecture11 vs pa-lecture-01)`() {
        // This is the live PA reality: lecture11 normalizes to "lecture11", KC doc to "pa lecture 01".
        val links = KcIdBackfill.computeLinks(
            cards = listOf(card("c1", "PA:lecture11_en.md")),
            kcs = listOf(kc("pa-kc-001", "PA", listOf("pa-lecture-01"))),
        )
        assertEquals(emptyList(), links)
    }

    @Test
    fun `SO and RC prefixes both map to subject SO-RC for KC lookup`() {
        val kcs = listOf(kc("sorc-kc-1", "SO-RC", listOf("so-rc-lecture-03")))
        val soLink = KcIdBackfill.computeLinks(listOf(card("c-so", "SO:so-rc-lecture-03.md")), kcs)
        val rcLink = KcIdBackfill.computeLinks(listOf(card("c-rc", "RC:so-rc-lecture-03.md")), kcs)
        assertEquals(listOf(KcIdBackfill.Link("c-so", "sorc-kc-1", "SO-RC")), soLink)
        assertEquals(listOf(KcIdBackfill.Link("c-rc", "sorc-kc-1", "SO-RC")), rcLink)
    }

    @Test
    fun `a card only links to a KC of the SAME subject`() {
        // PA card stem matches a POO KC's doc stem by coincidence -> still NO link (subject differs).
        val links = KcIdBackfill.computeLinks(
            cards = listOf(card("c1", "PA:shared-doc.md")),
            kcs = listOf(kc("poo-kc-1", "POO", listOf("shared-doc"))),
        )
        assertEquals(emptyList(), links)
    }

    @Test
    fun `applyLinks sets kc_id and never overwrites a non-null kc_id`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertCard(db, id = "c-null", kcId = null)
        insertCard(db, id = "c-set", kcId = "already-linked")

        val applied = KcIdBackfill.applyLinks(
            db,
            listOf(
                KcIdBackfill.Link("c-null", "pa-kc-001", "PA"),
                KcIdBackfill.Link("c-set", "pa-kc-999", "PA"), // must NOT overwrite "already-linked"
            ),
        )

        assertEquals(1, applied, "only the null-kc_id card is written")
        assertEquals("pa-kc-001", kcIdOf(db, "c-null"))
        assertEquals("already-linked", kcIdOf(db, "c-set"))
    }

    @Test
    fun `production reality (lecture11 vs pa-lecture-01) yields zero links`() {
        val links = KcIdBackfill.computeLinks(
            cards = listOf(
                card("c1", "PA:lecture11_en.md"),
                card("c2", "PA:seminar9_10_np.md"),
                card("c3", "PA:lecture9_10_np.md"),
            ),
            kcs = listOf(
                kc("pa-kc-001", "PA", listOf("pa-lecture-01")),
                kc("pa-kc-006", "PA", listOf("pa-lecture-01")),
            ),
        )
        assertTrue(links.isEmpty(), "today's corpus has ZERO confident overlap (the honest 0-link result)")
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────
    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("backfill.db").toString())
        TutorMigration.migrate(db) // fresh DB: the INV-3.1 gate self-skips (0 protected rows)
        return db
    }

    private fun insertCard(db: Database, id: String, kcId: String?) {
        transaction(db) {
            // a real owner user is required by the FK; insert one if absent.
            // DSL insert against the REAL users columns (Users.kt:25-32: id, name, scope,
            // created_at, last_seen_at, email, lang — there is NO display_name column);
            // guarded for idempotency across multiple insertCard calls in one test.
            val ownerExists = UsersTable.selectAll()
                .where { UsersTable.id eq "owner" }.count() > 0
            if (!ownerExists) {
                UsersTable.insert {
                    it[UsersTable.id] = "owner"
                    it[name] = "owner"
                    it[scope] = "OWNER"
                    it[createdAt] = Instant.parse("2026-06-11T00:00:00Z")
                    it[lastSeenAt] = Instant.parse("2026-06-11T00:00:00Z")
                    it[email] = "o@x"
                    // lang keeps its default 'ro'
                }
            }
            FsrsCardsTable.insert {
                it[FsrsCardsTable.id] = id
                it[userId] = "owner"
                it[sourceType] = "MANUAL"
                it[sourceRef] = "PA:lecture11_en.md"
                it[front] = "f"; it[back] = "b"
                it[difficulty] = 5.0; it[stability] = 0.5; it[retrievability] = 1.0
                it[dueAt] = Instant.now(); it[lastReviewedAt] = Instant.now(); it[lapses] = 0
                it[FsrsCardsTable.kcId] = kcId
                it[status] = "ACTIVE"
            }
        }
    }

    private fun kcIdOf(db: Database, cardId: String): String? = transaction(db) {
        FsrsCardsTable.selectAll().where { FsrsCardsTable.id eq cardId }.single()[FsrsCardsTable.kcId]
    }
}
