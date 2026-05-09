package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonlRotateTest {

    private fun writeBytes(file: Path, bytes: Int) {
        // Repeat a single JSONL-ish line to fill the requested byte count.
        val line = """{"k":"v","filler":"${"x".repeat(60)}"}""" + "\n"
        val sb = StringBuilder()
        while (sb.length < bytes) sb.append(line)
        file.writeText(sb.toString())
    }

    @Test
    fun rotationCompressesPrimaryToGzArchive(@TempDir tmp: Path) {
        val file = tmp.resolve("data.jsonl")
        writeBytes(file, 4096)
        val pre = file.fileSize()
        val rotated = JsonlRotate.maybeRotate(file, maxBytes = 1024)
        assertTrue(rotated)
        // Primary deleted, archive present at .1.gz.
        assertTrue(!file.exists(), "primary cleared after rotation")
        val archive = JsonlRotate.archivePath(file)
        assertTrue(archive.exists(), "archive at .1.gz")
        // Compression ratio sanity: archive smaller than original primary.
        assertTrue(
            archive.fileSize() < pre,
            "gz archive (${archive.fileSize()}) smaller than uncompressed (${pre})",
        )
        // Decompressed text equals the original primary bytes.
        val decoded = JsonlRotate.readArchiveText(file)
        assertNotNull(decoded)
        assertEquals(pre.toInt(), decoded!!.toByteArray(Charsets.UTF_8).size,
            "decompressed size matches original primary")
    }

    @Test
    fun secondRotationConcatenatesIntoSingleArchive(@TempDir tmp: Path) {
        val file = tmp.resolve("data.jsonl")
        writeBytes(file, 2048)
        val firstPrimary = Files.readString(file)
        JsonlRotate.maybeRotate(file, maxBytes = 1024)
        // Round 2: write fresh primary, rotate again — archive must
        // contain BOTH first + second primary content (oldest-first).
        writeBytes(file, 2048)
        val secondPrimary = Files.readString(file)
        JsonlRotate.maybeRotate(file, maxBytes = 1024)

        val combined = JsonlRotate.readArchiveText(file)
        assertNotNull(combined)
        assertEquals(
            firstPrimary + secondPrimary,
            combined,
            "archive holds full history oldest-first across rotations",
        )
    }

    @Test
    fun belowThresholdDoesNotRotate(@TempDir tmp: Path) {
        val file = tmp.resolve("data.jsonl")
        writeBytes(file, 256)
        val rotated = JsonlRotate.maybeRotate(file, maxBytes = 4096)
        assertEquals(false, rotated)
        assertTrue(file.exists(), "primary preserved when under cap")
        assertTrue(!JsonlRotate.archivePath(file).exists(), "no archive created")
    }

    @Test
    fun missingFileNoOps(@TempDir tmp: Path) {
        val file = tmp.resolve("nope.jsonl")
        assertEquals(false, JsonlRotate.maybeRotate(file))
        assertEquals(emptyList(), JsonlRotate.readArchiveLines(file))
        assertEquals(null, JsonlRotate.readArchiveText(file))
    }

    @Test
    fun readArchiveLinesYieldsNonBlankLines(@TempDir tmp: Path) {
        val file = tmp.resolve("data.jsonl")
        file.writeText("line1\nline2\n\nline3\n")
        // Force rotation with tiny cap.
        JsonlRotate.maybeRotate(file, maxBytes = 1)
        val lines = JsonlRotate.readArchiveLines(file)
        assertEquals(listOf("line1", "line2", "line3"), lines)
    }
}
