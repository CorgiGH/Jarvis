package jarvis

import jarvis.web.runWeb
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val USAGE = """Usage: jarvis [chat | logger [--once] | reflect | sub [name] [query...] | subs | web]

  chat                       Start interactive chat REPL (default).
  logger                     Always-on activity logger (5-min interval).
  logger --once              Single capture, exit.
  reflect                    Daily reflection over last 24h.
  sub <name> [query...]      Run a named subsystem.
  subs                       List available subsystems.
  web                        Start Ktor web server (HTMX UI on :8080 by default; JARVIS_PORT overrides).
"""

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "chat" -> runBlocking { runChat() }
        "logger" -> runLogger(once = args.getOrNull(1) == "--once")
        "reflect" -> runBlocking { runReflect() }
        "sub" -> runBlocking { runSub(args.drop(1).toList()) }
        "subs" -> runBlocking { runSub(emptyList()) }
        "web" -> runBlocking { runWeb() }
        "-h", "--help", "help" -> println(USAGE)
        else -> {
            System.err.println("Unknown subcommand: ${args.firstOrNull()}")
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}
