package jarvis

import jarvis.web.runWeb
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val USAGE = """Usage: jarvis [chat | logger [--once] | reflect | sub [name] [query...] | subs | web | reindex | import-anki <subject> <path>]

  chat                       Start interactive chat REPL (default).
  logger                     Always-on activity logger (5-min interval).
  logger --once              Single capture, exit.
  reflect                    Daily reflection over last 24h.
  sub <name> [query...]      Run a named subsystem.
  subs                       List available subsystems.
  web                        Start Ktor web server (HTMX UI on :8080 by default).
  reindex                    Embed any wiki entries not yet in the vector store.
  import-anki <subj> <apkg>  Import an Anki .apkg deck as concepts under archival/<subj>/.
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
        "-h", "--help", "help" -> println(USAGE)
        else -> {
            System.err.println("Unknown subcommand: ${args.firstOrNull()}")
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}
