package jarvis

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridRetrieverTest {

    private fun seedArchival(root: Path, files: Map<String, String>) {
        Files.createDirectories(root)
        for ((rel, content) in files) {
            val path = root.resolve(rel)
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
        }
    }

    @Test
    fun extractEntitiesFiltersStopwords() {
        val out = HybridRetriever.extractEntities("I love Kotlin and Python")
        assertEquals(listOf("Kotlin", "Python"), out)
    }

    @Test
    fun extractEntitiesDedups() {
        val out = HybridRetriever.extractEntities("Kotlin Kotlin Python")
        assertEquals(listOf("Kotlin", "Python"), out)
    }

    @Test
    fun extractEntitiesDropsSingleLetter() {
        val out = HybridRetriever.extractEntities("A B C is a Kotlin file")
        assertEquals(listOf("Kotlin"), out)
    }

    @Test
    fun extractEntitiesDropsCommonStopwords() {
        val out = HybridRetriever.extractEntities("Tuesday meeting about Kotlin")
        assertEquals(listOf("Kotlin"), out, "weekday filtered as stopword")
    }

    @Test
    fun emptyQueryReturnsEmpty(@TempDir tmp: Path) = runBlocking {
        val out = HybridRetriever.search("", archivalRoot = tmp)
        assertEquals(emptyList(), out)
    }

    @Test
    fun lexicalOnlyWhenSemanticAbsent(@TempDir tmp: Path) = runBlocking {
        seedArchival(tmp, mapOf(
            "kotlin-notes.md" to "# Kotlin\nKotlin sealed classes are nice.",
            "python-notes.md" to "# Python\nPython has duck typing.",
        ))
        val out = HybridRetriever.search(
            query = "Kotlin sealed",
            k = 5,
            archivalRoot = tmp,
            semanticEmbed = null,
        )
        assertTrue(out.isNotEmpty(), "lexical path returns hits")
        assertTrue(out.any { it.id.contains("kotlin") }, "kotlin file matches")
    }

    @Test
    fun rrfMergesAcrossSources(@TempDir tmp: Path) = runBlocking {
        seedArchival(tmp, mapOf(
            "kotlin-coroutines.md" to "Kotlin coroutines are suspendable.\nKotlin is great.",
        ))
        // Both lexical and entity passes will hit the same file. Without
        // RRF dedup we'd return it twice; with RRF the file appears once
        // with summed score.
        val out = HybridRetriever.search(
            query = "Kotlin coroutine cancellation",
            k = 5,
            archivalRoot = tmp,
            semanticEmbed = null,
        )
        val ids = out.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "RRF deduplicates by id")
        assertTrue(out.first().score > 0.0, "non-zero score")
    }

    @Test
    fun missingArchivalReturnsEmpty(@TempDir tmp: Path) = runBlocking {
        // Pass a non-existent archival path; lexical + entity yield nothing.
        val out = HybridRetriever.search(
            query = "anything",
            archivalRoot = tmp.resolve("does-not-exist"),
            semanticEmbed = null,
        )
        assertEquals(emptyList(), out)
    }

    @Test
    fun rrfScoreOrderingMatchesIntuition(@TempDir tmp: Path) = runBlocking {
        // File A matched by 2 entities + lexical → high RRF.
        // File B matched by 1 lexical only → low RRF.
        seedArchival(tmp, mapOf(
            "a.md" to "Kotlin and Python are languages",
            "b.md" to "Kotlin is great",
        ))
        val out = HybridRetriever.search(
            query = "Kotlin Python performance",
            k = 5,
            archivalRoot = tmp,
            semanticEmbed = null,
        )
        // File a should rank above file b because it matches both entities.
        val ranks = out.mapIndexed { i, h -> h.id to i }.toMap()
        val rankA = ranks.entries.firstOrNull { it.key.contains("a.md") }?.value
        val rankB = ranks.entries.firstOrNull { it.key.contains("b.md") }?.value
        if (rankA != null && rankB != null) {
            assertTrue(rankA <= rankB, "multi-entity-match should rank ahead (got A=$rankA, B=$rankB)")
        }
    }
}
