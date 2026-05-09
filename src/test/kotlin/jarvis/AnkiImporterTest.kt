package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnkiImporterTest {

    /** Anki's field separator (U+001F unit separator). */
    private val SEP = ''

    private fun writeFakeApkg(
        dest: Path,
        notes: List<Pair<String, String>>,
    ) {
        // Build a minimal SQLite db with the notes table Anki uses.
        // Real anki2 has many more tables; AnkiImporter only reads `notes.flds`.
        val tmp = Files.createTempFile("fake-anki-", ".sqlite")
        Files.deleteIfExists(tmp)
        val url = "jdbc:sqlite:${tmp.toAbsolutePath()}"
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE notes (id INTEGER PRIMARY KEY, flds TEXT)")
            }
            conn.prepareStatement("INSERT INTO notes (id, flds) VALUES (?, ?)").use { ps ->
                notes.forEachIndexed { i, (front, back) ->
                    ps.setLong(1, (i + 1).toLong())
                    ps.setString(2, front + SEP + back)
                    ps.executeUpdate()
                }
            }
        }
        // Zip the db as collection.anki2 inside the apkg.
        Files.newOutputStream(dest).use { fos ->
            ZipOutputStream(fos).use { zos ->
                zos.putNextEntry(ZipEntry("collection.anki2"))
                Files.newInputStream(tmp).use { it.copyTo(zos) }
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("media"))
                zos.write("{}".toByteArray())
                zos.closeEntry()
            }
        }
        Files.deleteIfExists(tmp)
    }

    @Test
    fun importsNotesAsConceptHeadings(@TempDir tmp: Path) {
        val apkg = tmp.resolve("greedy.apkg")
        writeFakeApkg(apkg, listOf(
            "What is a greedy algorithm?" to "An algorithm that picks the local optimum at each step.",
            "Activity selection problem" to "Sort by end time, pick non-overlapping greedily.",
            "Huffman coding" to "Greedy bottom-up tree construction by frequency.",
        ))
        val archival = tmp.resolve("archival")
        val r = AnkiImporter.import("PA", apkg, archival)
        assertEquals(3, r.notesParsed)
        assertEquals(3, r.conceptsWritten)
        assertEquals(0, r.skippedDuplicates)

        val written = Files.readString(r.outputPath, Charsets.UTF_8)
        assertTrue(written.contains("## What is a greedy algorithm?"))
        assertTrue(written.contains("## Activity selection problem"))
        assertTrue(written.contains("Greedy bottom-up tree construction by frequency."))

        // ConceptCatalog rescan picks up the three new entries.
        ConceptCatalog.invalidate()
        val concepts = ConceptCatalog.all(archival)
            .filter { it.subject == "PA" }
            .map { it.name }
        assertTrue(concepts.contains("What is a greedy algorithm?"))
        assertTrue(concepts.contains("Activity selection problem"))
        assertTrue(concepts.contains("Huffman coding"))
    }

    @Test
    fun reimportSkipsDuplicates(@TempDir tmp: Path) {
        val apkg = tmp.resolve("dup.apkg")
        writeFakeApkg(apkg, listOf(
            "Concept A" to "body A",
            "Concept B" to "body B",
        ))
        val archival = tmp.resolve("archival")
        AnkiImporter.import("PA", apkg, archival)
        val r2 = AnkiImporter.import("PA", apkg, archival)
        assertEquals(2, r2.notesParsed)
        assertEquals(0, r2.conceptsWritten, "second import = full dedup")
        assertEquals(2, r2.skippedDuplicates)
    }

    @Test
    fun stripsHtmlInFields(@TempDir tmp: Path) {
        val apkg = tmp.resolve("html.apkg")
        writeFakeApkg(apkg, listOf(
            "<p>What is <b>O(n log n)</b>?</p>" to
                "<div>Used by mergesort.</div><br/>Also heapsort.<br/><img src='x.png'/>",
        ))
        val archival = tmp.resolve("archival")
        val r = AnkiImporter.import("ALO", apkg, archival)
        val written = Files.readString(r.outputPath, Charsets.UTF_8)
        assertTrue(written.contains("## What is O(n log n)?"),
            "html stripped from front: $written")
        assertTrue(written.contains("Used by mergesort"))
        assertTrue(!written.contains("<"), "no html residue: $written")
    }

    @Test
    fun stripHtmlEntities() {
        val s = AnkiImporter.stripHtml("a &amp; b &lt;c&gt; &quot;d&quot;&nbsp;e&#39;f")
        assertEquals("a & b <c> \"d\" e'f", s)
    }
}
