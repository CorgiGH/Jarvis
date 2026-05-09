package jarvis

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.DriverManager
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * .apkg deck importer. Extracts notes from an Anki shared-deck export
 * (ZIP containing collection.anki2 SQLite db) and writes them as
 * markdown concept rows under archival/<subject>/anki-<deck>.md.
 *
 * ConceptCatalog scans archival for `^## ` headings, so imported
 * front-text becomes a concept name and back-text becomes the body.
 * No FSRS state is initialized — first review through the user's
 * existing [[lesson]] / KnowledgeFsrs path bootstraps that.
 *
 * Anki note schema: notes.flds is a single string with fields joined
 * by U+001F (information separator one). Most decks use the "Basic"
 * model = front | back. We take field 0 as the concept name and
 * fields 1..N as the body. HTML stripped to plain markdown text.
 *
 * AnkiWeb decks are not auto-discoverable by URL (the search UI is
 * JS-rendered). User downloads .apkg manually and feeds the path in.
 */
object AnkiImporter {

    data class ImportResult(
        val deck: String,
        val subject: String,
        val notesParsed: Int,
        val conceptsWritten: Int,
        val skippedDuplicates: Int,
        val outputPath: Path,
    )

    fun import(
        subject: String,
        apkgPath: Path,
        archivalRoot: Path = Config.archivalDir,
    ): ImportResult {
        require(apkgPath.exists() && apkgPath.isRegularFile()) {
            "apkg not found or not a regular file: $apkgPath"
        }
        require(subject.isNotBlank()) { "subject required" }
        val deckName = apkgPath.fileName.toString().substringBeforeLast(".apkg").substringBeforeLast(".colpkg")

        // 1. Unzip into a temp dir.
        val tempDir = Files.createTempDirectory("anki-import-")
        try {
            unzipApkg(apkgPath, tempDir)
            val dbPath = pickDb(tempDir)
                ?: error("no collection.anki2/anki21/anki21b found inside $apkgPath")

            // 2. Read notes from the SQLite db.
            val notes = readNotes(dbPath)

            // 3. Render to markdown.
            val outDir = archivalRoot.resolve(subject)
            outDir.createDirectories()
            val outPath = outDir.resolve("anki-$deckName.md")

            val existingHeadings = if (outPath.exists()) {
                Files.readAllLines(outPath, Charsets.UTF_8)
                    .filter { it.startsWith("## ") }
                    .map { it.removePrefix("## ").trim().lowercase() }
                    .toSet()
            } else emptySet()

            val sb = StringBuilder()
            if (!outPath.exists()) {
                sb.append("# Anki imported deck: $deckName\n\n")
                sb.append("Subject: $subject. Source: $apkgPath. ")
                sb.append("Auto-extracted ${java.time.Instant.now()}.\n\n")
            }
            var written = 0
            var skipped = 0
            for (note in notes) {
                val concept = note.front.trim().lineSequence().firstOrNull { it.isNotBlank() }?.take(120)
                if (concept.isNullOrBlank()) {
                    skipped++; continue
                }
                if (concept.lowercase() in existingHeadings) {
                    skipped++; continue
                }
                sb.append("## ").append(concept).append('\n')
                if (note.back.isNotBlank()) {
                    sb.append('\n')
                    sb.append(note.back.trim().take(2000))
                    sb.append('\n')
                }
                sb.append('\n')
                written++
            }

            if (written > 0) {
                if (outPath.exists()) {
                    Files.writeString(
                        outPath, sb.toString(), Charsets.UTF_8,
                        StandardOpenOption.APPEND,
                    )
                } else {
                    Files.writeString(
                        outPath, sb.toString(), Charsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE,
                    )
                }
            }

            // Force ConceptCatalog rescan so [[plan]] / FSRS pickers see new concepts.
            ConceptCatalog.invalidate()

            return ImportResult(
                deck = deckName,
                subject = subject,
                notesParsed = notes.size,
                conceptsWritten = written,
                skippedDuplicates = skipped,
                outputPath = outPath,
            )
        } finally {
            // Clean up temp dir.
            try {
                Files.walk(tempDir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private data class AnkiNote(val front: String, val back: String)

    private fun unzipApkg(apkg: Path, dest: Path) {
        Files.newInputStream(apkg).use { fis ->
            ZipInputStream(fis).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) {
                        zis.closeEntry(); continue
                    }
                    val out = dest.resolve(entry.name).normalize()
                    if (!out.startsWith(dest)) {
                        zis.closeEntry(); continue   // path-traversal guard
                    }
                    out.parent?.createDirectories()
                    Files.newOutputStream(out).use { os -> zis.copyTo(os) }
                    zis.closeEntry()
                }
            }
        }
    }

    private fun pickDb(dir: Path): Path? {
        // Anki uses one of these names depending on the version of the
        // exporting client. anki21b is the newer encrypted format
        // (Anki 23.10+) — we don't decrypt; fall back to anki21/anki2.
        for (name in listOf("collection.anki21", "collection.anki2")) {
            val p = dir.resolve(name)
            if (p.exists()) return p
        }
        return null
    }

    private fun readNotes(dbPath: Path): List<AnkiNote> {
        val out = mutableListOf<AnkiNote>()
        // sqlite-jdbc uses jdbc:sqlite:<path>
        val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
        DriverManager.getConnection(url).use { conn ->
            conn.prepareStatement("SELECT flds FROM notes").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val flds = rs.getString(1) ?: continue
                        val fields = flds.split('')   // anki separator
                        if (fields.isEmpty()) continue
                        val front = stripHtml(fields[0])
                        val back = if (fields.size > 1) {
                            fields.drop(1).joinToString("\n\n") { stripHtml(it) }
                        } else ""
                        out += AnkiNote(front, back)
                    }
                }
            }
        }
        return out
    }

    /** Conservative HTML strip: drop `<...>` tags + decode common entities.
     *  Real Anki cards often contain MathJax, CSS, audio/img tags. We don't
     *  preserve any of that — concept catalog only needs plain text. */
    internal fun stripHtml(raw: String): String {
        if (raw.isEmpty()) return ""
        var s = raw.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("</?(p|div|li)\\s*[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        // Collapse runs of whitespace per line; strip leading/trailing on each line.
        s = s.lineSequence().joinToString("\n") { it.trim() }
        // Collapse 3+ consecutive newlines to 2.
        s = s.replace(Regex("\n{3,}"), "\n\n").trim()
        return s
    }
}
