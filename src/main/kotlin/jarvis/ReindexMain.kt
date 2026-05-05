package jarvis

import jarvis.embeddings.EmbeddingsClient
import jarvis.embeddings.StoredEmbedding
import jarvis.embeddings.VectorStore
import kotlin.system.exitProcess

internal suspend fun runReindex() {
    val apiKey = resolveOpenRouterKey() ?: run {
        System.err.println("ERROR: OPENROUTER_API_KEY not set.")
        exitProcess(1)
    }

    val wikiText = MemoryWiki.readAll()
    if (wikiText.isEmpty()) {
        println("Wiki empty. Nothing to index.")
        return
    }

    // Each wiki entry starts with a "## " header. Split keeping the marker.
    val sections = wikiText
        .split(Regex("(?=^## )", RegexOption.MULTILINE))
        .filter { it.startsWith("## ") }

    val existingIds = VectorStore.ids()
    val toIndex = sections.filter { sectionIdOf(it) !in existingIds }

    if (toIndex.isEmpty()) {
        println("All ${sections.size} wiki entries already indexed (${existingIds.size} in store).")
        return
    }

    println("Indexing ${toIndex.size} new entries (${sections.size} total in wiki, ${existingIds.size} already in store)...")
    EmbeddingsClient(apiKey).use { client ->
        var ok = 0
        var fail = 0
        for ((i, section) in toIndex.withIndex()) {
            val id = sectionIdOf(section)
            try {
                val emb = client.embed(section)
                VectorStore.add(StoredEmbedding(id, section, emb))
                ok++
                println("  [${i + 1}/${toIndex.size}] $id")
            } catch (e: Exception) {
                fail++
                println("  [${i + 1}/${toIndex.size}] FAILED: $id (${e.message?.take(160)})")
            }
        }
        println("Indexed: $ok ok, $fail failed. Store now has ${VectorStore.all().size} entries.")
    }
}

internal fun sectionIdOf(section: String): String =
    section.lineSequence().firstOrNull()?.trim() ?: section.take(60)
