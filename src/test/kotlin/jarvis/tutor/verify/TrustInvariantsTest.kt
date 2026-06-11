package jarvis.tutor.verify

import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-1 Task 6 — the `gradle trustInvariants` core: INV-3.3 / SCHEMA / FLIP / PARITY.
 * Uses the REAL in-repo corpus (content/, gradle test workingDir = project root) so the PARITY
 * leg exercises the exact ContentRepo + kcContentHashOf path the serve gate uses.
 */
class TrustInvariantsTest {

    private val contentDir: Path = Path.of("content")

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("inv.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private fun insertVerdict(db: Database, kcId: String, hash: String?) {
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[status] = "faithful"
                it[lastAuditRunId] = "run-test-1"
                it[contentHash] = hash
                it[lectureGrounded] = true
                it[sourceSpanHash] = "0123456789abcdef"
                it[updatedAt] = Instant.now()
            }
        }
    }

    private fun realHashOf(kcId: String): String {
        val repo = ContentRepo(contentDir)
        val kc = repo.loadManifest().subjects
            .flatMap { repo.loadSubject(it.id).kcs }
            .single { it.id == kcId }
        return ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
    }

    @Test
    fun `all green when a faithful row carries the recomputed corpus hash`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", realHashOf("pa-kc-001"))

        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)

        assertEquals(emptyList(), failures)
    }

    @Test
    fun `zero faithful rows fails the FLIP invariant`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "FLIP" }, "$failures")
    }

    @Test
    fun `tampered content_hash fails PARITY (machine parity, no human spot-check)`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", "ffffffffffffffff")
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "PARITY" && it.detail.contains("pa-kc-001") }, "$failures")
    }

    @Test
    fun `card floor violation surfaces INV-3-3`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", realHashOf("pa-kc-001"))
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 800)
        assertTrue(failures.any { it.check == "INV-3.3" }, "$failures")
    }
}
