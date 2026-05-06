package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikiToJsonlMigratorTest {

    private val sample = """
        |starter prose at top
        |
        |## [2026-05-06 18:00 UTC] user note
        |
        |raw note, NOT a conversation; should be skipped
        |
        |## [2026-05-06 18:05 UTC] conversation (claude-opus-4-7)
        |
        |**user:** hello
        |
        |**jarvis:** hi there
        |
        |## [2026-05-06 18:10 UTC] daily reflection (claude-opus-4-7)
        |
        |today went fine
        |
        |## [2026-05-06 18:15 UTC] conversation (copilot)
        |
        |**user:** what's up
        |
        |**jarvis:** not much, you?
    """.trimMargin()

    @Test
    fun extractsOnlyConversationSections(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        Files.writeString(wiki, sample, Charsets.UTF_8)

        val turns = WikiToJsonlMigrator.parse(wiki)
        // 2 conversation sections × 2 turns each = 4 ConversationEntry rows
        assertEquals(4, turns.size, "user-note and reflection sections excluded")
        assertEquals(listOf("user", "assistant", "user", "assistant"), turns.map { it.role })
        assertEquals("hello", turns[0].content)
        assertEquals("hi there", turns[1].content)
        assertEquals("what's up", turns[2].content)
        assertEquals("not much, you?", turns[3].content)
        assertEquals("claude-opus-4-7", turns[1].model)
        assertEquals("copilot", turns[3].model)
    }

    @Test
    fun emitsCorrectKindAndSchemaVersion(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        Files.writeString(wiki, sample, Charsets.UTF_8)
        val turns = WikiToJsonlMigrator.parse(wiki)
        assertTrue(turns.all { it.kind == "chat" })
        assertTrue(turns.all { it.v == 1 })
    }

    @Test
    fun preservesTimestampFromWikiHeader(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        Files.writeString(wiki, sample, Charsets.UTF_8)
        val turns = WikiToJsonlMigrator.parse(wiki)
        // Wiki header format "yyyy-MM-dd HH:mm 'UTC'" → ISO-8601 instant
        assertEquals("2026-05-06T18:05:00Z", turns[0].ts)
        assertEquals("2026-05-06T18:05:00Z", turns[1].ts)
        assertEquals("2026-05-06T18:15:00Z", turns[2].ts)
    }

    @Test
    fun deterministicMsgIdAcrossRuns(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        Files.writeString(wiki, sample, Charsets.UTF_8)
        val first = WikiToJsonlMigrator.parse(wiki)
        val second = WikiToJsonlMigrator.parse(wiki)
        assertEquals(first.map { it.msgId }, second.map { it.msgId },
                "msg_id must be deterministic so re-run dedups")
        // All msg_ids unique within one parse
        assertEquals(first.size, first.map { it.msgId }.toSet().size,
                "all msg_ids unique per parse")
    }

    @Test
    fun missingWikiFileReturnsEmpty(@TempDir tmp: Path) {
        val absent = tmp.resolve("never.md")
        assertEquals(emptyList(), WikiToJsonlMigrator.parse(absent))
    }

    @Test
    fun runOnceWritesJsonlAndCreatesBackup(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        val out = tmp.resolve("conversations.jsonl")
        Files.writeString(wiki, sample, Charsets.UTF_8)

        val report = WikiToJsonlMigrator.runOnce(
            wikiFile = wiki,
            conversationsFile = out,
            dryRun = false,
        )
        assertEquals(4, report.entriesEmitted)
        assertEquals(2, report.conversationsParsed)
        assertTrue(Files.exists(out), "conversations.jsonl created")
        val lines = Files.readAllLines(out, Charsets.UTF_8).filter { it.isNotEmpty() }
        assertEquals(4, lines.size)
        assertTrue(report.backupPath != null && Files.exists(report.backupPath!!),
                "wiki backup created at ${report.backupPath}")
    }

    @Test
    fun dryRunDoesNotMutateConversationsFile(@TempDir tmp: Path) {
        val wiki = tmp.resolve("wiki.md")
        val out = tmp.resolve("conversations.jsonl")
        Files.writeString(wiki, sample, Charsets.UTF_8)
        Files.writeString(out, "{\"role\":\"system\",\"content\":\"existing\",\"ts\":\"2026-01-01T00:00:00Z\",\"msg_id\":\"x\"}\n", Charsets.UTF_8)

        val report = WikiToJsonlMigrator.runOnce(
            wikiFile = wiki,
            conversationsFile = out,
            dryRun = true,
        )
        assertEquals(4, report.entriesEmitted)
        // Original file unchanged
        val lines = Files.readAllLines(out, Charsets.UTF_8).filter { it.isNotEmpty() }
        assertEquals(1, lines.size, "dry-run leaves real conversations.jsonl untouched")
        assertTrue(report.previewPath != null && Files.exists(report.previewPath!!),
                "preview file written at ${report.previewPath}")
    }
}
