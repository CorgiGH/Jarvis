package jarvis

import jarvis.web.runWeb
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val USAGE = """Usage: jarvis [chat | logger [--once] | reflect | sub [name] [query...] | subs | web | reindex | import-anki <subject> <path> | google-auth-bootstrap | seed-fsrs [opts]]

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
  seed-fsrs [opts]           Walk one or more corpus dirs and generate FSRS cards via
                             the claude CLI (free OAuth, NOT paid API). Run
                             'jarvis seed-fsrs --help' for the flag list.
  google-auth-bootstrap      One-time OAuth 2.0 consent flow — opens browser, writes
                             google-token.json. Run on your PC (needs a browser).
                             Reads client_secrets.json from GOOGLE_CREDS_PATH or
                             ./client_secrets.json. Writes token to GOOGLE_TOKEN_PATH
                             or ./google-token.json.
"""

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
