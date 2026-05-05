package jarvis

import jarvis.subsystem.SubsystemInput
import jarvis.subsystem.Subsystems
import kotlin.system.exitProcess

private const val SYSTEM_PROMPT = """You are Jarvis, a personal life-OS assistant for the user.

You have access to:
- The user's recent activity log (active window snapshots, captured every 5 minutes)
- A markdown memory wiki holding past notes, daily reflections, and prior conversations

Your charter:
- Honest, not flattering. Tell uncomfortable truths when warranted. Refuse sycophancy.
- Distinguish "needs encouragement" from "needs reality check" — pick correctly.
- Ask better questions when the question matters more than an answer.
- Stay quiet when nothing needs surfacing. Don't manufacture engagement.
- Connect dots across domains (sleep -> focus, financial stress -> relationships, etc.).
- Don't moralize repeatedly about the same thing. Say it once, clearly, then move on.
- Preserve user agency. You inform; you do not decide for the user.
- Respect second-order effects. Short-term mood is not long-term wellbeing.

Style:
- Tight responses. No filler. No corporate hedging.
- Match the moment: coach, friend, mirror, or silent - whichever fits.
- Code/commits/security topics: write normally and precisely."""


private fun buildContext(): String {
    val activity = Activity.loadRecent()
    val wiki = MemoryWiki.recent().ifEmpty { "(empty)" }
    return """
        |# Recent activity (last ${Config.ACTIVITY_LOOKBACK_HOURS}h)
        |$activity
        |
        |# Memory wiki (recent ${Config.WIKI_RECENT_ENTRIES} entries)
        |$wiki
    """.trimMargin()
}

internal suspend fun runChat() {
    val apiKey = resolveOpenRouterKey() ?: run {
        System.err.println("ERROR: OPENROUTER_API_KEY not set. Copy .env.example to .env and set the key.")
        exitProcess(1)
    }

    LlmClient(apiKey).use { client ->
        val history = mutableListOf<ChatMessage>()
        println("Jarvis online (chain head: ${Config.FALLBACK_CHAIN.first()}). Commands: exit, /save <note>, /ctx, /subs, /sub <name> [query]")

        while (true) {
            print("you> ")
            val msg = readlnOrNull()?.trim() ?: break
            if (msg.isEmpty()) continue
            if (msg in setOf("exit", "quit", "/exit")) break
            if (msg.startsWith("/save ")) {
                val note = msg.removePrefix("/save ").trim()
                if (note.isNotEmpty()) {
                    MemoryWiki.append("user note", note)
                    println("(saved to wiki)")
                }
                continue
            }
            if (msg == "/ctx") {
                println(buildContext())
                continue
            }
            if (msg == "/subs") {
                println("Available subsystems:")
                Subsystems.all.forEach { println("  ${it.name.padEnd(10)} - ${it.description}") }
                continue
            }
            if (msg.startsWith("/sub ") || msg == "/sub") {
                val raw = msg.removePrefix("/sub").trim()
                if (raw.isEmpty()) {
                    Subsystems.all.forEach { println("  ${it.name.padEnd(10)} - ${it.description}") }
                    continue
                }
                val tokens = raw.split(" ", limit = 2)
                val subName = tokens[0]
                val subQuery = tokens.getOrNull(1)?.trim()
                val sub = Subsystems.get(subName) ?: run {
                    println("(unknown subsystem: $subName; try /subs)")
                    null
                }
                if (sub != null) {
                    val activity = Activity.loadEntries()
                    val wiki = MemoryWiki.recent()
                    try {
                        val output = sub.run(client, SubsystemInput(activity, wiki, subQuery))
                        println("[${sub.name}]> ${output.text}\n")
                        output.wikiEntry?.let { MemoryWiki.append(it, output.text) }
                    } catch (e: Exception) {
                        println("[sub:${sub.name}] failed: ${e.message}")
                    }
                }
                continue
            }

            history += ChatMessage("user", msg)
            val sysPrompt = SYSTEM_PROMPT + "\n\n# Context\n" + buildContext()
            val messages = listOf(ChatMessage("system", sysPrompt)) + history

            val (reply, model) = try {
                client.complete(messages)
            } catch (e: Exception) {
                println("[error] ${e.message}")
                history.removeLast()
                continue
            }
            println("jarvis> $reply\n")
            history += ChatMessage("assistant", reply)
            MemoryWiki.append("conversation ($model)", "**user:** $msg\n\n**jarvis:** $reply")
        }
    }
}
