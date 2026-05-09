package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Concurrent appends to wiki.md must not interleave bytes mid-section.
 * Council 1778078829 HIGH + 1778102666 elevation. Reproduces the byte-interleaving
 * risk that wiki.md.bak.1778090717 was kept around to recover from.
 */
class MemoryWikiConcurrencyTest {

    @Test
    fun concurrentAppendsProduceNSectionsThatAllParse(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        val n = 1000
        val pool = Executors.newFixedThreadPool(32)
        val ready = CountDownLatch(n)
        val go = CountDownLatch(1)
        val done = CountDownLatch(n)

        repeat(n) { i ->
            pool.submit {
                ready.countDown()
                go.await()
                try {
                    // Body deliberately multi-line + embeds the index so we can verify
                    // each section landed atomically (no mid-body interleaving).
                    val bigBody = buildString {
                        repeat(80) { lineNum ->
                            append("line-$lineNum of $i ").append("x".repeat(120)).append('\n')
                        }
                    }
                    MemoryWiki.appendTo(wiki, "section-$i", bigBody)
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        go.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "all $n appends finished")
        pool.shutdown()

        // JsonlRotate may rotate wiki.md → wiki.md.1.gz mid-run when size > 5MB.
        // Concatenate both for the invariant check (archive is gzipped now).
        val primaryText = Files.readString(wiki, Charsets.UTF_8)
        val archiveText = JsonlRotate.readArchiveText(wiki) ?: ""
        val text = archiveText + primaryText
        val sections = text.split(Regex("(?=^## )", RegexOption.MULTILINE))
            .filter { it.startsWith("## ") }
        assertEquals(n, sections.size, "section count after concurrent append (primary+archive)")

        val seenIndices = mutableSetOf<Int>()
        for (sec in sections) {
            val idxMatch = Regex("""section-(\d+)""").find(sec)
                ?: error("section header missing index: ${sec.take(80)}")
            val idx = idxMatch.groupValues[1].toInt()
            assertTrue(seenIndices.add(idx), "duplicate section idx $idx")
            assertTrue(sec.contains("line-0 of $idx"), "body line 0 missing/mangled in $idx")
            assertTrue(sec.contains("line-79 of $idx"), "body line 79 missing/mangled in $idx")
            assertEquals(80, Regex("""line-\d+ of $idx """).findAll(sec).count(),
                "section $idx missing or duplicating body lines")
        }
        assertEquals((0 until n).toSet(), seenIndices, "every index 0..$n landed exactly once")
    }
}
