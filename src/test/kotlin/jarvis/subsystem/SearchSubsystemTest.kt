package jarvis.subsystem

import jarvis.Llm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchSubsystemTest {

    @Test
    fun findsLiteralMatchAcrossFilesAndScoresByHitCount(@TempDir tmp: Path) {
        val a = tmp.resolve("a.md").also {
            Files.writeString(it, "# course on Kotlin\nKotlin coroutines section.\nKotlin DSL example.\n")
        }
        val b = tmp.resolve("nested/b.md").also {
            Files.createDirectories(it.parent)
            Files.writeString(it, "# Java\nKotlin appears once here.\n")
        }
        tmp.resolve("c.md").also {
            Files.writeString(it, "no relevant token here.\n")
        }

        val results = SearchSubsystem.searchIn(tmp, "Kotlin", k = 5)
        assertEquals(2, results.size, "files c.md skipped (zero hits)")
        // a.md has 3 hits, b.md has 1 — a ranks first
        assertEquals(a.fileName.toString(), results[0].path.fileName.toString())
        assertEquals(3, results[0].hits)
        assertEquals(1, results[1].hits)
        assertTrue(results[0].snippet.contains("Kotlin"))
    }

    @Test
    fun caseInsensitive(@TempDir tmp: Path) {
        Files.writeString(tmp.resolve("x.md"), "# Operating Systems\nPaging is paging.\n")
        val results = SearchSubsystem.searchIn(tmp, "PAGING", k = 5)
        assertEquals(1, results.size)
        assertEquals(2, results[0].hits)
    }

    @Test
    fun ignoresNonMarkdownFiles(@TempDir tmp: Path) {
        Files.writeString(tmp.resolve("note.md"), "kotlin\n")
        Files.writeString(tmp.resolve("note.txt"), "kotlin\n")
        Files.writeString(tmp.resolve("data.json"), "{\"text\":\"kotlin\"}\n")
        val results = SearchSubsystem.searchIn(tmp, "kotlin", k = 10)
        assertEquals(1, results.size, "only .md scanned")
        assertEquals("note.md", results[0].path.fileName.toString())
    }

    @Test
    fun missingDirReturnsEmpty(@TempDir tmp: Path) {
        val absent = tmp.resolve("never")
        assertEquals(emptyList(), SearchSubsystem.searchIn(absent, "anything", k = 5))
    }

    @Test
    fun capsAtK(@TempDir tmp: Path) {
        repeat(15) { i ->
            Files.writeString(tmp.resolve("f$i.md"), "match-token here\n")
        }
        val results = SearchSubsystem.searchIn(tmp, "match-token", k = 5)
        assertEquals(5, results.size)
    }

    @Test
    fun snippetIncludesContextAroundMatch(@TempDir tmp: Path) {
        Files.writeString(
            tmp.resolve("ctx.md"),
            "line above\nline above 2\nthe matching line goes here\nline below\nline below 2\n",
        )
        val results = SearchSubsystem.searchIn(tmp, "matching", k = 1)
        val snippet = results.single().snippet
        assertTrue(snippet.contains("the matching line"), "match line in snippet")
        assertTrue(snippet.contains("line above 2") || snippet.contains("line below"),
                "context around match in snippet")
    }

    @Test
    fun emptyQueryRejected(@TempDir tmp: Path) {
        assertEquals(emptyList(), SearchSubsystem.searchIn(tmp, "", k = 5))
        assertEquals(emptyList(), SearchSubsystem.searchIn(tmp, "   ", k = 5))
    }

    @Test
    fun runReturnsFormattedResultsWithoutLlmCall(@TempDir tmp: Path) {
        Files.writeString(tmp.resolve("doc.md"), "# Note\nAlpha beta.\nAlpha gamma.\n")
        val sub = SearchSubsystem(archivalRoot = tmp)
        val never = Llm { _, _ -> error("LLM should not be called for search") }
        // run is suspend — bridge via runBlocking
        val out = kotlinx.coroutines.runBlocking {
            sub.run(never, SubsystemInput(activity = emptyList(), wiki = "", userQuery = "alpha"))
        }
        assertTrue(out.text.contains("doc.md"), "result names file")
        assertTrue(out.text.contains("hits=2"), "result counts hits")
        assertEquals(null, out.wikiEntry, "search results NOT auto-persisted to wiki")
    }
}

private fun Llm(block: suspend (List<jarvis.ChatMessage>, Int) -> Pair<String, String>): Llm =
    object : Llm {
        override suspend fun complete(
            messages: List<jarvis.ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
            imagePath: String?,
        ): Pair<String, String> = block(messages, maxTokens)
    }
