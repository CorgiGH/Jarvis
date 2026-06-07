package jarvis.content

import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.system.exitProcess

/**
 * Gradle `reconcileContent` task entry point — curate-tutor **Stage-9 reconcile**, the cheap
 * owner-run bridge that does NOT require a full LLM audit (unlike `verifyContent`).
 *
 * It is the PRODUCTION caller that finally reaches BOTH unwired Phase-2 legs in one flow, via
 * [ContentCli.reconcile]:
 *  1. [ContentReconcile.reconcileSourceSpans] (the D1 PC-side source-edit WATCHER) — recompute every
 *     audited KC's `source_span_hash` from the LIVE `_sources` bytes and, on a real fold-different
 *     edit, NULL `content_hash` + `source_span_hash` and re-pend the KC (so the serve gate fails
 *     CLOSED until a fresh audit). Idempotent on whitespace-only churn.
 *  2. [ContentReconcile.reconcile] (the Stage-9 setPending leg) — set every still-UNVERIFIED KC's
 *     `kc_verification_status` to `pending` (the §2.4 UNVERIFIED→PENDING edge), H10-safe (never
 *     regresses a faithful KC).
 *
 * [ContentCli.reconcile] validates the corpus FIRST (a malformed corpus is never reconciled into the
 * audit queue). This task NEVER calls an LLM and NEVER touches the serve path.
 *
 * PC-SIDE ONLY (D7): the watcher reads `_sources` bytes, which exist only on the authoring PC.
 *
 * SPLIT: [reconcileContent] is the PURE testable core (resolves the DB, migrates, runs the reconcile,
 * RETURNS the report or THROWS) — the test suite drives THIS, exactly the work the gradle-task main
 * performs. [main] is the thin process shell that adds `exitProcess` + stdout/stderr (which a test
 * cannot exercise, since `exitProcess` would halt the test JVM). No reconcile logic lives in [main].
 */
object ContentReconcileCli {

    /**
     * PURE testable core: resolve the content dir + tutor DB, migrate, and run the validate-gated
     * Stage-9 reconcile (which runs the D1 watcher then the setPending leg). RETURNS the report.
     *
     * @throws IllegalArgumentException if the content dir does not exist.
     * @throws IllegalStateException if [ContentCli.reconcile]'s validate gate fails (malformed corpus).
     */
    fun reconcileContent(args: Array<String>): ContentReconcile.ReconcileReport {
        val contentDir = Path.of(
            args.getOrNull(0)
                ?: System.getProperty("JARVIS_CONTENT_DIR")
                ?: System.getenv("JARVIS_CONTENT_DIR")
                ?: "content",
        )
        require(contentDir.exists()) { "[reconcileContent] content dir not found: $contentDir" }

        val db = connectDb()
        TutorMigration.migrate(db)
        return ContentCli.reconcile(contentDir, db, clock = Instant::now)
    }

    /** Resolve + connect the tutor DB (system property > env > Config). */
    private fun connectDb(): Database {
        val dbPath = System.getProperty("JARVIS_TUTOR_DB")
            ?: System.getenv("JARVIS_TUTOR_DB")
            ?: jarvis.Config.tutorDbPath
        return TutorDb.connect(dbPath)
    }
}

/**
 * Gradle `reconcileContent` task main. Owner/manual offline only; PC-side (D7). NOT on check.
 *
 * Thin shell over [ContentReconcileCli.reconcileContent]: maps the outcome to stdout + a process exit
 * code (0 = OK; 2 = content dir missing; 1 = validation aborted the reconcile on a malformed corpus).
 */
fun main(args: Array<String>) {
    val report = try {
        ContentReconcileCli.reconcileContent(args)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(2)
    } catch (e: IllegalStateException) {
        System.err.println("[reconcileContent] ${e.message}")
        exitProcess(1)
    }
    println(
        "[reconcileContent] reconciled ${report.claims.size} claim(s); " +
            "promoted ${report.pendingSet.size} KC(s) to pending.",
    )
}
