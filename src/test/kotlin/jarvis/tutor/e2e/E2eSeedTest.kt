package jarvis.tutor.e2e

import jarvis.content.ContentRepo
import jarvis.tutor.LanguageCheckTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorDb
import jarvis.tutor.VerificationStatus
import jarvis.web.VerifyAdmin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plan 4b Task 9 (§0.9J) — proves the real-backend seeder serves a faithful-gated lesson route over
 * the REAL corpus, and that the seed manifest is DERIVED (never a hardcoded kc-id literal). The
 * `lesson-gates.spec.ts` rendered gates are only honest if this seed serves; this test proves it.
 *
 * ASCII test method names (§0 #15 — a non-ASCII Kotlin test title caused a 4a merge fix 86e120c).
 */
class E2eSeedTest {

    private fun runSeed(tmp: Path): E2eSeed.SeedResult {
        val dbPath = tmp.resolve("tutor.db").toString()
        val outJson = tmp.resolve("seed.json")
        // The seeder reads the REAL content/ corpus (the gate's whole point — real admission path).
        val contentDir = Path.of("content")
        return E2eSeed.seed(dbPath, contentDir, outJson)
    }

    /** The beat-complete promoted set computed INDEPENDENTLY of the seeder (the derivation assert). */
    private fun expectedBeatCompleteSet(): List<String> {
        val repo = ContentRepo(Path.of("content"))
        return repo.loadManifest().subjects
            .flatMap { repo.loadSubject(it.id).kcs }
            .filter { E2eSeed.isBeatComplete(it) }
            .map { it.id }
            .sorted()
    }

    /** The figure-binding set computed INDEPENDENTLY of the seeder. */
    private fun expectedFigureSet(): List<String> {
        val repo = ContentRepo(Path.of("content"))
        return repo.loadManifest().subjects
            .flatMap { repo.loadSubject(it.id).kcs }
            .filter { E2eSeed.isBeatComplete(it) && E2eSeed.bindsFigure(it) }
            .map { it.id }
            .sorted()
    }

    @Test
    fun `seed creates the db file and writes seed json`(@TempDir tmp: Path) {
        val result = runSeed(tmp)
        assertTrue(tmp.resolve("tutor.db").exists(), "the seeded DB file must exist")
        val json = tmp.resolve("seed.json")
        assertTrue(json.exists(), "seed.json must be written")
        val body = json.readText()
        assertTrue(body.contains("\"sid\""), "seed.json carries sid: $body")
        assertTrue(body.contains("\"kcIds\""), "seed.json carries kcIds: $body")
        assertTrue(body.contains("\"figureKcIds\""), "seed.json carries figureKcIds: $body")
        assertEquals(result.sid, sidFromJson(body), "seed.json sid matches the returned sid")
    }

    @Test
    fun `every beat-complete KC resolves faithful via the real gate`(@TempDir tmp: Path) {
        val result = runSeed(tmp)
        val db = TutorDb.connect(tmp.resolve("tutor.db").toString())
        val repo = ContentRepo(Path.of("content"))
        assertTrue(result.kcIds.isNotEmpty(), "the promoted set must be non-empty (anti-vacuity)")
        for (kcId in result.kcIds) {
            val kc = VerifyAdmin.findKc(repo, kcId)
            assertNotNull(kc, "promoted KC $kcId must resolve in the corpus")
            val status = VerifyAdmin.resolveStatus(db, kcId, kc)
            assertEquals(
                VerificationStatus.faithful, status,
                "promoted KC $kcId must resolve FAITHFUL via the real serve gate (real recomputed hashes)",
            )
        }
    }

    @Test
    fun `kcIds equals the independently-computed beat-complete set and is non-empty`(@TempDir tmp: Path) {
        val result = runSeed(tmp)
        val expected = expectedBeatCompleteSet()
        assertTrue(expected.isNotEmpty(), "the corpus must have at least one beat-complete KC")
        assertEquals(
            expected, result.kcIds.sorted(),
            "kcIds is DERIVED from the corpus beat-complete set — NOT a hardcoded literal " +
                "(today pa-kc-001..004)",
        )
    }

    @Test
    fun `figureKcIds equals the independently-computed figure-binding set and is non-empty`(@TempDir tmp: Path) {
        val result = runSeed(tmp)
        val expected = expectedFigureSet()
        assertTrue(expected.isNotEmpty(), "the corpus must have at least one figure-binding beat-complete KC")
        assertEquals(
            expected, result.figureKcIds.sorted(),
            "figureKcIds is DERIVED from the corpus figure-binding set (today [pa-kc-002])",
        )
        // Every figure KC must also be in the promoted set (subset invariant).
        assertTrue(
            result.kcIds.containsAll(result.figureKcIds),
            "figureKcIds must be a subset of kcIds",
        )
    }

    @Test
    fun `the seeded session resolves to the seeded user`(@TempDir tmp: Path) {
        val result = runSeed(tmp)
        val db = TutorDb.connect(tmp.resolve("tutor.db").toString())
        val resolvedUser = SessionRepo(db).findUserId(result.sid)
        assertEquals(result.userId, resolvedUser, "the seeded sid must resolve to the seeded user")
    }

    @Test
    fun `language check records are present after reconcile`(@TempDir tmp: Path) {
        val result = runSeed(tmp)
        val db = TutorDb.connect(tmp.resolve("tutor.db").toString())
        val total = transaction(db) { LanguageCheckTable.selectAll().count() }
        assertTrue(total > 0, "reconcile must write language_check records (INV-8.1)")
        // Every promoted KC must have at least its name_ro language-check record.
        for (kcId in result.kcIds) {
            val rows = transaction(db) {
                LanguageCheckTable.selectAll()
                    .where { LanguageCheckTable.kcId eq kcId }
                    .count()
            }
            assertTrue(rows > 0, "promoted KC $kcId must have language_check records")
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private fun sidFromJson(json: String): String {
        val m = Regex("\"sid\"\\s*:\\s*\"([^\"]*)\"").find(json)
        return m?.groupValues?.get(1) ?: error("no sid in $json")
    }
}
