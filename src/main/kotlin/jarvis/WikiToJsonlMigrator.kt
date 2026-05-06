package jarvis

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * One-shot migrator: walks wiki.md "## [<ts>] conversation (<model>)" sections
 * and emits ConversationEntry rows to conversations.jsonl.
 *
 * Council 1778104395 v1 ruling (CRITICAL on cross-process race): runs as a CLI
 * one-shot with the server STOPPED. Pre-flight backs up wiki.md and any
 * existing conversations.jsonl. Idempotent via deterministic msg_id (so a
 * re-run does not duplicate rows when caller dedups).
 */
object WikiToJsonlMigrator {

    private val WIKI_TS_FORMAT: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm 'UTC'")

    private val SECTION_HEADER = Regex("""^## \[([^\]]+)] conversation \(([^)]+)\)\s*$""")
    private val USER_BLOCK = Regex(
        """\*\*user:\*\*\s*(.*?)(?=\n\n\*\*jarvis:\*\*|\Z)""", RegexOption.DOT_MATCHES_ALL,
    )
    private val ASSISTANT_BLOCK = Regex(
        """\*\*jarvis:\*\*\s*(.*)""", RegexOption.DOT_MATCHES_ALL,
    )

    data class Report(
        val conversationsParsed: Int,
        val entriesEmitted: Int,
        val backupPath: Path?,
        val previewPath: Path?,
    )

    fun parse(wikiFile: Path): List<ConversationEntry> {
        if (!wikiFile.exists()) return emptyList()
        val text = wikiFile.readText(Charsets.UTF_8)
        val out = mutableListOf<ConversationEntry>()
        // Split on lookahead so each section keeps its "## " header line intact.
        val sections = text.split(Regex("(?=^## )", RegexOption.MULTILINE))
        for (sec in sections) {
            val firstLineEnd = sec.indexOf('\n').takeIf { it >= 0 } ?: continue
            val header = sec.substring(0, firstLineEnd).trim()
            val match = SECTION_HEADER.matchEntire(header) ?: continue
            val tsRaw = match.groupValues[1]
            val model = match.groupValues[2]
            val ts = parseWikiTs(tsRaw) ?: continue
            val body = sec.substring(firstLineEnd + 1)
            val userText = USER_BLOCK.find(body)?.groupValues?.get(1)?.trim() ?: continue
            val asstText = ASSISTANT_BLOCK.find(body)?.groupValues?.get(1)?.trim() ?: continue
            val userId = stableMsgId(ts, "user", userText)
            val asstId = stableMsgId(ts, "assistant", asstText)
            out += ConversationEntry(role = "user", content = userText, ts = ts,
                                     model = null, msgId = userId)
            out += ConversationEntry(role = "assistant", content = asstText, ts = ts,
                                     model = model, msgId = asstId)
        }
        return out
    }

    fun runOnce(wikiFile: Path, conversationsFile: Path, dryRun: Boolean): Report {
        val entries = parse(wikiFile)
        val conversationsParsed = entries.size / 2
        if (dryRun) {
            val preview = conversationsFile.resolveSibling(conversationsFile.fileName.toString() + ".preview")
            preview.parent?.let { Files.createDirectories(it) }
            Files.deleteIfExists(preview)
            entries.forEach { Conversations.appendTo(preview, it) }
            return Report(conversationsParsed, entries.size, backupPath = null, previewPath = preview)
        }

        // Pre-flight backup of wiki.md (always) + existing conversations.jsonl (if any).
        val nowEpoch = System.currentTimeMillis() / 1000
        val wikiBackup = wikiFile.resolveSibling("${wikiFile.fileName}.pre-migrate-$nowEpoch.bak")
        if (wikiFile.exists()) {
            Files.copy(wikiFile, wikiBackup, StandardCopyOption.COPY_ATTRIBUTES)
        }
        if (conversationsFile.exists()) {
            val convBackup = conversationsFile.resolveSibling(
                "${conversationsFile.fileName}.pre-migrate-$nowEpoch.bak",
            )
            Files.copy(conversationsFile, convBackup, StandardCopyOption.COPY_ATTRIBUTES)
        }
        entries.forEach { Conversations.appendTo(conversationsFile, it) }
        return Report(conversationsParsed, entries.size, backupPath = wikiBackup, previewPath = null)
    }

    private fun parseWikiTs(raw: String): String? = try {
        LocalDateTime.parse(raw, WIKI_TS_FORMAT).toInstant(ZoneOffset.UTC).toString()
    } catch (_: Exception) {
        null
    }

    private fun stableMsgId(ts: String, role: String, content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$ts|$role|$content".toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }
}

fun main(args: Array<String>) {
    val dryRun = args.contains("--dry-run")
    println("[migrate-wiki] wiki=${Config.wikiFile}")
    println("[migrate-wiki] conversations=${Config.conversationsFile}")
    println("[migrate-wiki] dry-run=$dryRun")
    val report = WikiToJsonlMigrator.runOnce(Config.wikiFile, Config.conversationsFile, dryRun)
    println("[migrate-wiki] conversations parsed: ${report.conversationsParsed}")
    println("[migrate-wiki] entries emitted: ${report.entriesEmitted}")
    report.backupPath?.let { println("[migrate-wiki] wiki backup: $it") }
    report.previewPath?.let { println("[migrate-wiki] preview: $it") }
    if (dryRun) {
        println("[migrate-wiki] DRY-RUN — no change to ${Config.conversationsFile}. Re-run without --dry-run to commit.")
    } else {
        println("[migrate-wiki] DONE.")
    }
}
