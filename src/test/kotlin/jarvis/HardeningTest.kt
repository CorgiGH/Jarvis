package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Council 1778105576 hardening checks.
 */
class HardeningTest {

    // (4) Schema v unvalidated guard — Risk Analyst caught alone.
    @Test
    fun readerSkipsUnknownSchemaVersionAndKeepsValidEntries(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        Conversations.appendTo(file, ConversationEntry(role = "user", content = "ok",
            ts = "2026-05-07T12:00:00Z", msgId = "v1a"))
        Files.writeString(
            file,
            """{"role":"user","content":"future","ts":"2026-05-07T12:00:01Z","msg_id":"v2a","kind":"chat","v":2}""" + "\n",
            Charsets.UTF_8,
            java.nio.file.StandardOpenOption.APPEND,
        )
        Conversations.appendTo(file, ConversationEntry(role = "assistant", content = "good",
            ts = "2026-05-07T12:00:02Z", msgId = "v1b"))

        val all = Conversations.readAllFrom(file)
        assertEquals(2, all.size, "v=2 row skipped, v=1 rows kept")
        assertEquals(listOf("ok", "good"), all.map { it.content })
    }

    // (1) ChatTurnWriter atomicity — orphan-turn defense.
    // Both rows for one turn must be visible to the reader simultaneously, never just one.
    @Test
    fun chatTurnWriterEmitsBothRowsAtomicallyVisible(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        ChatTurnWriter.appendTo(
            conversationsFile = file,
            userMsg = "hello",
            assistantReply = "hi",
            model = "claude-opus-4-7",
        )
        val all = Conversations.readAllFrom(file)
        assertEquals(2, all.size)
        assertEquals("user", all[0].role)
        assertEquals("assistant", all[1].role)
        assertEquals("hello", all[0].content)
        assertEquals("hi", all[1].content)
        // Same turn id prefix on both rows.
        val userPrefix = all[0].msgId.substringBefore("-")
        val asstPrefix = all[1].msgId.substringBefore("-")
        assertEquals(userPrefix, asstPrefix, "user+assistant share turn id prefix")
        // (D) seq field: user=0, assistant=1.
        assertEquals(0, all[0].seq)
        assertEquals(1, all[1].seq)
    }

    // (2) Migrator dedup — re-runnable per Stripe backfill SOP.
    @Test
    fun migratorReRunDoesNotDuplicateRows(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        val out = tmp.resolve("conversations.jsonl")
        Files.writeString(
            wiki,
            """
            |## [2026-05-06 18:05 UTC] conversation (claude-opus-4-7)
            |
            |**user:** hello
            |
            |**jarvis:** hi there
            """.trimMargin(),
            Charsets.UTF_8,
        )
        val r1 = WikiToJsonlMigrator.runOnce(wiki, out, dryRun = false)
        val r2 = WikiToJsonlMigrator.runOnce(wiki, out, dryRun = false)
        assertEquals(2, r1.entriesEmitted)
        assertEquals(0, r2.entriesEmitted, "second run dedups by msg_id")
        val lines = Files.readAllLines(out, Charsets.UTF_8).filter { it.isNotEmpty() }
        assertEquals(2, lines.size, "no duplicate rows after re-run")
    }

    // (3) Privacy scanner — preamble warns on identifier-shaped content but still returns content.
    @Test
    fun privacyScannerFlagsMatricolButPreambleStillRendered(@TempDir tmp: Path) {
        val file = tmp.resolve("core_memory.md")
        Files.writeString(file, "I am 31091001031ROSL251002 at UAIC\n")

        val findings = CoreMemory.scanForPii(file)
        assertTrue(findings.isNotEmpty(), "matricol-shaped token flagged")
        assertTrue(findings.any { it.kind == "matricol" }, "kind tagged matricol")

        // preamble still returns content (warn-not-block)
        val out = CoreMemory.preambleFrom(file)
        assertTrue(out.contains("31091001031ROSL251002"))
    }

    @Test
    fun privacyScannerFindsEmailAndPhone(@TempDir tmp: Path) {
        val file = tmp.resolve("core_memory.md")
        Files.writeString(file, "ping me at victor.vasiloi@gmail.com or +40 745 123 456\n")
        val findings = CoreMemory.scanForPii(file)
        assertTrue(findings.any { it.kind == "email" }, "email flagged")
        assertTrue(findings.any { it.kind == "phone" }, "phone flagged")
    }

    @Test
    fun privacyScannerCleanFileReturnsNoFindings(@TempDir tmp: Path) {
        val file = tmp.resolve("core_memory.md")
        Files.writeString(file, "English default. No sidetracks.\n")
        assertEquals(emptyList(), CoreMemory.scanForPii(file))
    }

    // (D) seq field is present on schema with default null for back-compat.
    @Test
    fun seqFieldDefaultsNullForOldRows(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        // Write a row WITHOUT seq field (simulates pre-hardening rows).
        Files.writeString(
            file,
            """{"role":"user","content":"old","ts":"2026-05-07T12:00:00Z","msg_id":"old1","kind":"chat","v":1}""" + "\n",
            Charsets.UTF_8,
        )
        val all = Conversations.readAllFrom(file)
        assertEquals(1, all.size)
        assertEquals(null, all[0].seq, "missing seq decodes as null for back-compat")
    }

    // (H) Integration: chat-turn write → recent() read round-trip catches
    // any future refactor that desyncs ChatTurnWriter and buildChatContext's
    // recency source. Cheaper than full Ktor TestApplication; covers the same
    // "buildChatContext silently returns empty" failure mode at the seam.
    @Test
    fun chatTurnWriterToRecentRoundTripPreservesOrderAndContent(@TempDir tmp: Path) {
        val file = tmp.resolve("conversations.jsonl")
        ChatTurnWriter.appendTo(file, "q1", "a1", "model1")
        ChatTurnWriter.appendTo(file, "q2", "a2", "model2")
        val recent = Conversations.recentFrom(file, 4)
        assertEquals(4, recent.size, "two turns × user+asst rows = 4")
        assertEquals(listOf("q1", "a1", "q2", "a2"), recent.map { it.content })
        assertEquals(listOf("user", "assistant", "user", "assistant"), recent.map { it.role })
        // turn ids stay paired across user+assistant rows
        assertEquals(recent[0].msgId.substringBefore("-"), recent[1].msgId.substringBefore("-"))
        assertEquals(recent[2].msgId.substringBefore("-"), recent[3].msgId.substringBefore("-"))
        // and across turns the prefix changes
        assertNotNull(recent[0].msgId)
        assertTrue(
            recent[0].msgId.substringBefore("-") != recent[2].msgId.substringBefore("-"),
            "different turns get different turn ids",
        )
    }

    // Migrator backfills seq.
    @Test
    fun migratorBackfillsSeqZeroAndOne(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        val out = tmp.resolve("conversations.jsonl")
        Files.writeString(
            wiki,
            """
            |## [2026-05-06 18:05 UTC] conversation (claude-opus-4-7)
            |
            |**user:** q1
            |
            |**jarvis:** a1
            """.trimMargin(),
            Charsets.UTF_8,
        )
        WikiToJsonlMigrator.runOnce(wiki, out, dryRun = false)
        val rows = Conversations.readAllFrom(out)
        assertEquals(0, rows[0].seq)
        assertEquals(1, rows[1].seq)
    }
}
