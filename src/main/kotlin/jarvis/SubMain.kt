package jarvis

import jarvis.subsystem.SubsystemInput
import jarvis.subsystem.Subsystems
import kotlin.system.exitProcess

internal suspend fun runSub(args: List<String>) {
    if (args.isEmpty()) {
        println("Available subsystems:")
        Subsystems.all.forEach { println("  ${it.name.padEnd(10)} - ${it.description}") }
        return
    }

    val name = args.first()
    val sub = Subsystems.get(name) ?: run {
        System.err.println("Unknown subsystem: $name")
        System.err.println("Available: ${Subsystems.all.joinToString(", ") { it.name }}")
        exitProcess(2)
    }
    val query = if (args.size > 1) args.drop(1).joinToString(" ") else null

    val apiKey = resolveOpenRouterKey()  // optional with claude-max / copilot providers

    val activity = Activity.loadEntries()
    val wiki = MemoryWiki.recent()

    LlmFactory.create(apiKey).use { client ->
        val output = try {
            sub.run(client, SubsystemInput(activity, wiki, query))
        } catch (e: Exception) {
            System.err.println("[sub:${sub.name}] failed: ${e.message}")
            exitProcess(1)
        }
        println(output.text)
        output.wikiEntry?.let { entry ->
            MemoryWiki.append(entry, output.text)
        }
    }
}
