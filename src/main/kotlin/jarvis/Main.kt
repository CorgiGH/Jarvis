package jarvis

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val USAGE = """Usage: jarvis [chat | logger [--once] | reflect]

  chat              Start interactive chat REPL (default).
  logger            Run always-on activity logger (5-min interval).
  logger --once     Capture a single activity snapshot and exit.
  reflect           Run the daily reflection job (Phase 3, stub).
"""

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "chat" -> runBlocking { runChat() }
        "logger" -> {
            val once = args.getOrNull(1) == "--once"
            runLogger(once)
        }
        "reflect" -> runBlocking { runReflect() }
        "-h", "--help", "help" -> println(USAGE)
        else -> {
            System.err.println("Unknown subcommand: ${args.firstOrNull()}")
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}
