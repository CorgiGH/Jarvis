package jarvis

import jarvis.embeddings.EmbeddingsClient
import jarvis.embeddings.StoredEmbedding
import jarvis.embeddings.VectorStore
import jarvis.subsystem.SubsystemInput
import jarvis.subsystem.Subsystems
import kotlin.system.exitProcess


internal suspend fun runChat() {
    val apiKey = resolveOpenRouterKey()  // optional now; only required for openrouter chat or any embeddings
    val embeddings: EmbeddingsClient? = apiKey?.let { EmbeddingsClient(it) }

    LlmFactory.create(apiKey).use { client ->
        val history = mutableListOf<ChatMessage>()
        val storeNote = if (embeddings != null) "${VectorStore.all().size} entries" else "disabled (no OPENROUTER_API_KEY)"
        println("Jarvis online (provider: ${System.getenv("JARVIS_LLM") ?: "claude-max"}, semantic store: $storeNote). Commands: exit, /save <note>, /ctx, /subs, /sub <name> [query]")

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
                println(buildChatContext())
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
            val queryEmb: FloatArray? = if (embeddings != null) {
                try { embeddings.embed(msg) } catch (_: Exception) { null }
            } else null
            val sysPrompt = CHAT_SYSTEM_PROMPT + "\n\n# Context\n" + buildChatContextWithSemantic(queryEmb)
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
            val sectionTitle = "conversation ($model)"
            val sectionContent = "**user:** $msg\n\n**jarvis:** $reply"
            MemoryWiki.append(sectionTitle, sectionContent)
            // Best-effort: also embed and store so this turn is searchable next time.
            if (embeddings != null) {
                try {
                    val joined = "## $sectionTitle\n\n$sectionContent"
                    val emb = embeddings.embed(joined)
                    VectorStore.add(StoredEmbedding(id = "$sectionTitle | ${msg.take(60)}", text = joined, embedding = emb))
                } catch (_: Exception) {
                    // ignore: chat keeps working without semantic store
                }
            }
        }
    }
}
