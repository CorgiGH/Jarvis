package jarvis.tutor.verify

import jarvis.tutor.TutorDb
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * HIGH-hardening — every fail-closed THROW path of [ReportWrongQuery.hasOpenReportWrong] MUST emit a
 * diagnostic before swallowing the throw. Fail-closed (return true ⇒ darken the badge) is CORRECT and
 * must stay; the defect is the SILENT swallow — a recurring DB fault would darken every faithful badge
 * corpus-wide with zero diagnostic trail.
 *
 * Class-killing: the assertion runs over BOTH entry points (the [Database] overload AND the
 * [Transaction] overload) via [assertFailsClosedAndLogs], so a future copy-pasted silent swallow on
 * EITHER path is caught by the SAME every-throw-path-must-emit invariant. We force a throw by querying
 * a DB whose `report_wrong` table does NOT exist (never migrated) ⇒ "no such table" inside the query.
 */
class ReportWrongQueryFailClosedTest {

    /** A connected DB with NO schema — any `report_wrong` query throws "no such table". */
    private fun unmigratedDb(tmp: Path, name: String): Database =
        TutorDb.connect(tmp.resolve(name).toString())

    /**
     * The shared every-throw-path invariant: a thrown query must (a) FAIL CLOSED (return true) AND
     * (b) emit a tagged diagnostic mentioning hasOpenReportWrong + the kcId. Applied to whichever
     * overload [invoke] dispatches to, so both entry points are held to it identically.
     */
    private fun assertFailsClosedAndLogs(overload: String, kcId: String, invoke: () -> Boolean) {
        val captured = ByteArrayOutputStream()
        val originalErr = System.err
        val result: Boolean = try {
            System.setErr(PrintStream(captured, true, StandardCharsets.UTF_8))
            invoke()
        } finally {
            System.setErr(originalErr)
        }

        // (a) FAIL-CLOSED preserved: a query throw must still return true (assume OPEN dispute).
        assertTrue(result, "[$overload] a query throw must FAIL CLOSED (return true), never serve over a dispute")

        // (b) the silent swallow is closed: a diagnostic naming the method + the kcId must be emitted.
        val log = captured.toString(StandardCharsets.UTF_8)
        assertTrue(
            log.contains("hasOpenReportWrong"),
            "[$overload] the fail-closed throw must emit a diagnostic mentioning hasOpenReportWrong — got:\n$log",
        )
        assertTrue(
            log.contains(kcId),
            "[$overload] the diagnostic must name the kcId so the darkened badge is traceable — got:\n$log",
        )
    }

    @Test
    fun `every fail-closed throw path returns true AND emits a tagged diagnostic naming the kc`(
        @TempDir tmp: Path,
    ) {
        val kcId = "pa-kc-005"

        // Database overload — opens its own read txn; the inner throw bubbles to the outer catch.
        val dbA = unmigratedDb(tmp, "noschema-db.db")
        assertFailsClosedAndLogs("db", kcId) {
            ReportWrongQuery.hasOpenReportWrong(dbA, kcId)
        }

        // Transaction overload — the throw is caught inside the Transaction-scoped try/catch.
        val dbB = unmigratedDb(tmp, "noschema-tx.db")
        assertFailsClosedAndLogs("tx", kcId) {
            transaction(dbB) { ReportWrongQuery.hasOpenReportWrong(this, kcId) }
        }
    }
}
