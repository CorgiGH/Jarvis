package jarvis

import jarvis.web.runWeb
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val USAGE = """Usage: jarvis [chat | logger [--once] | reflect | sub [name] [query...] | subs | web | reindex | import-anki <subject> <path> | google-auth-bootstrap | seed-fsrs [opts] | seed-knowledge-meta | backfill-kc-ids]

  chat                       Start interactive chat REPL (default).
  logger                     Always-on activity logger (5-min interval).
  logger --once              Single capture, exit.
  reflect                    Daily reflection over last 24h.
  sub <name> [query...]      Run a named subsystem.
  subs                       List available subsystems.
  web                        Start Ktor web server (HTMX UI on :8080 by default).
  reindex                    Embed any wiki entries not yet in the vector store.
  import-anki <subj> <apkg>  Import an Anki .apkg deck as concepts under archival/<subj>/.
  ingest-corpus              Build / refresh the knowledge graph from archival/ markdown.
  trust-export [out.json] [--run <id>]
                             D9 PC-side: dump kc_verification_status verdict rows
                             (frozen allowlist) to JSON for VPS import.
  trust-import <in.json>     D9 VPS-side: surgical, idempotent upsert of a verdict
                             dump into kc_verification_status. Never report_wrong.
  seed-knowledge-meta [--db PATH]
                             Plan-2: seed grade-model registry + exam schedule + exam_dates
                             primaries from the 2026-06-11 verified sweep (idempotent).
  backfill-kc-ids [--db PATH] [--dry-run]
                             Plan-2: link fsrs_cards to KCs by EXACT normalized source-doc
                             stem (confident-match only; no fuzzy/LLM). Prints per-subject
                             link counts + total. 0 links is the honest result today.
  seed-fsrs [opts]           Walk one or more corpus dirs and generate FSRS cards via
                             the claude CLI (free OAuth, NOT paid API). Run
                             'jarvis seed-fsrs --help' for the flag list.
  google-auth-bootstrap      One-time OAuth 2.0 consent flow — opens browser, writes
                             google-token.json. Run on your PC (needs a browser).
                             Reads client_secrets.json from GOOGLE_CREDS_PATH or
                             ./client_secrets.json. Writes token to GOOGLE_TOKEN_PATH
                             or ./google-token.json.
"""

/**
 * Phase-1 operator tool: apply TutorMigration to the DB at $JARVIS_DB
 * (default ~/.jarvis/tutor.db). Runs the idempotent schema migration + post-ALTER
 * backfills. The card cull (tools/card-cull.sql) is a SEPARATE step run BEFORE this
 * (deploy runbook). Prints fsrs_cards count before/after; exits non-zero on failure
 * (M-PARTIAL fires an auto-backup; recover by restoring the pre-migration dump).
 */
private fun runMigrate(pathArg: String?) {
    val dbPath = pathArg
        ?: System.getenv("JARVIS_DB")
        ?: (System.getProperty("user.home") + "/.jarvis/tutor.db")
    System.err.println("[migrate] target DB = $dbPath")
    val db = jarvis.tutor.TutorDb.connect(dbPath)
    fun cardCount(): Long = org.jetbrains.exposed.sql.transactions.transaction(db) {
        exec("SELECT COUNT(*) FROM fsrs_cards") { rs -> if (rs.next()) rs.getLong(1) else 0L } ?: 0L
    }
    val before = try { cardCount() } catch (e: Exception) { -1L }
    try {
        jarvis.tutor.TutorMigration.migrate(db)
    } catch (e: jarvis.tutor.MigrationException) {
        System.err.println("[migrate] FAILURE at column=${e.failingColumn}: ${e.message}")
        exitProcess(1)
    }
    val after = cardCount()
    println("[migrate] SUCCESS — fsrs_cards before=$before after=$after")
}

/**
 * Plan-2 Task 8 — `jarvis backfill-kc-ids [--db PATH] [--dry-run]`. Confident-match-only kc_id
 * backfill: links each card to a KC iff their normalized source-doc stems are EXACTLY equal
 * (KcIdBackfill). Prints per-subject link counts + the total. `--dry-run` computes + prints but
 * writes NOTHING. The honest result on today's corpus is 0 links (PA cards = lecture11/seminar;
 * PA KCs = pa-lecture-01) — a 0 total is SUCCESS, exit 0.
 */
private fun runBackfillKcIds(args: List<String>) {
    val dryRun = "--dry-run" in args
    val dbIdx = args.indexOf("--db")
    val dbPath = (if (dbIdx >= 0) args.getOrNull(dbIdx + 1) else null)
        ?: System.getenv("JARVIS_TUTOR_DB")
        ?: System.getProperty("JARVIS_TUTOR_DB")
        ?: jarvis.Config.tutorDbPath
    val contentDir = java.nio.file.Path.of(
        System.getenv("JARVIS_CONTENT_DIR")?.takeIf { it.isNotBlank() } ?: "content",
    )
    System.err.println("[backfill-kc-ids] db=$dbPath content=$contentDir dryRun=$dryRun")

    val db = jarvis.tutor.TutorDb.connect(dbPath)
    jarvis.tutor.TutorMigration.migrate(db)

    // read all cards (id, source_ref) read-only
    val cards = org.jetbrains.exposed.sql.transactions.transaction(db) {
        val out = ArrayList<jarvis.tutor.KcIdBackfill.CardRef>()
        exec("SELECT id, source_ref FROM fsrs_cards") { rs ->
            while (rs.next()) out.add(jarvis.tutor.KcIdBackfill.CardRef(rs.getString(1), rs.getString(2)))
        }
        out
    }

    // load every KC from content/
    val repo = jarvis.content.ContentRepo(contentDir)
    val kcs = repo.loadManifest().subjects.flatMap { repo.loadSubject(it.id).kcs }

    val links = jarvis.tutor.KcIdBackfill.computeLinks(cards, kcs)
    val perSubject = links.groupingBy { it.subject }.eachCount().toSortedMap()

    val applied = if (dryRun) 0 else jarvis.tutor.KcIdBackfill.applyLinks(db, links)

    println("[backfill-kc-ids] candidate links: ${links.size} (cards=${cards.size}, kcs=${kcs.size})")
    if (perSubject.isEmpty()) {
        println("[backfill-kc-ids] per-subject: (none — 0 confident source-doc overlaps, the honest result)")
    } else {
        for ((subject, n) in perSubject) println("[backfill-kc-ids]   $subject: $n")
    }
    println(
        if (dryRun) "[backfill-kc-ids] DRY-RUN — wrote 0 rows (total candidate links=${links.size})"
        else "[backfill-kc-ids] applied $applied link(s) (skipped ${links.size - applied} already-linked)",
    )
}

/**
 * Plan-2 operator tool: seed the course-meta tables (grade models, components, exam schedule,
 * exam_dates primaries) from the 2026-06-11 verified sweep. Idempotent (upsert-by-id). Applies
 * TutorMigration first so the Plan-2 tables exist. DB path: `--db PATH`, else $JARVIS_TUTOR_DB,
 * else ~/.jarvis/tutor.db. Prints the SeedReport. NOTE on the live DB: migrate() over the 828-card
 * corpus fires the INV-3.1 backup gate (pending Plan-2 DDL) — run `python tools/db-backup.py` first.
 */
private fun runSeedKnowledgeMeta(args: List<String>) {
    val dbIdx = args.indexOf("--db")
    val dbPath = (if (dbIdx >= 0) args.getOrNull(dbIdx + 1) else null)
        ?: System.getenv("JARVIS_TUTOR_DB")
        ?: System.getProperty("JARVIS_TUTOR_DB")
        ?: jarvis.Config.tutorDbPath
    System.err.println("[seed-knowledge-meta] target DB = $dbPath")
    val db = jarvis.tutor.TutorDb.connect(dbPath)
    try {
        jarvis.tutor.TutorMigration.migrate(db)
    } catch (e: jarvis.tutor.MigrationException) {
        System.err.println("[seed-knowledge-meta] migration FAILED: ${e.message}")
        exitProcess(1)
    }
    val report = jarvis.tutor.Plan2Seed.seedKnowledgeMeta(db, java.time.Instant.now())
    println(
        "[seed-knowledge-meta] inserted=${report.inserted} updated=${report.updated} " +
            "skipped=${report.skipped} (applied=${report.applied})",
    )
}

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "chat" -> runBlocking { runChat() }
        "logger" -> runLogger(once = args.getOrNull(1) == "--once")
        "reflect" -> runBlocking { runReflect() }
        "sub" -> runBlocking { runSub(args.drop(1).toList()) }
        "subs" -> runBlocking { runSub(emptyList()) }
        "web" -> runBlocking { runWeb() }
        "reindex" -> runBlocking { runReindex() }
        "ingest-corpus", "ingestCorpus" -> {
            val graph = jarvis.tutor.KnowledgeGraphBuilder.build()
            val path = jarvis.tutor.KnowledgeGraphBuilder.persist(graph)
            println(
                "knowledge graph built → $path\n" +
                    "  nodes: ${graph.nodes.size}\n" +
                    "  edges: ${graph.edges.size}\n" +
                    "  by edge kind: ${graph.edges.groupingBy { it.kind }.eachCount()}",
            )
        }
        "migrate-concept-refs" -> runMigrateConceptRefs()
        "migrate" -> runMigrate(args.getOrNull(1))
        "backfill-kc-ids" -> runBackfillKcIds(args.drop(1))
        "trust-export" -> jarvis.tutor.verify.TrustSyncCli.runExport(args.drop(1))
        "trust-import" -> jarvis.tutor.verify.TrustSyncCli.runImport(args.drop(1))
        "seed-knowledge-meta" -> runSeedKnowledgeMeta(args.drop(1))
        "seed-fsrs" -> runSeedFsrs(args.drop(1).toList())
        "import-anki" -> {
            val subject = args.getOrNull(1)
            val path = args.getOrNull(2)
            if (subject.isNullOrBlank() || path.isNullOrBlank()) {
                System.err.println("Usage: jarvis import-anki <subject> <path-to-deck.apkg>")
                exitProcess(2)
            }
            val r = AnkiImporter.import(
                subject = subject,
                apkgPath = java.nio.file.Paths.get(path),
            )
            println(
                "imported ${r.deck} → ${r.outputPath}\n" +
                    "  notes parsed:        ${r.notesParsed}\n" +
                    "  concepts written:    ${r.conceptsWritten}\n" +
                    "  duplicates skipped:  ${r.skippedDuplicates}",
            )
        }
        "google-auth-bootstrap" -> {
            val credsPath = java.nio.file.Path.of(
                System.getenv("GOOGLE_CREDS_PATH") ?: "client_secrets.json"
            )
            val tokenPath = java.nio.file.Path.of(
                System.getenv("GOOGLE_TOKEN_PATH") ?: "google-token.json"
            )
            if (!credsPath.toFile().exists()) {
                System.err.println(
                    "ERROR: client_secrets.json not found at $credsPath\n" +
                    "  1. Go to https://console.cloud.google.com/apis/credentials\n" +
                    "  2. Create OAuth client ID → Desktop app → download JSON\n" +
                    "  3. Place it at $credsPath (or set GOOGLE_CREDS_PATH=<path>)\n" +
                    "  4. Re-run: jarvis google-auth-bootstrap"
                )
                exitProcess(2)
            }
            jarvis.tutor.google.GoogleAuthBootstrap.run(credsPath, tokenPath)
        }
        "-h", "--help", "help" -> println(USAGE)
        else -> {
            System.err.println("Unknown subcommand: ${args.firstOrNull()}")
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}
