package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreMemoryTest {

    @Test
    fun missingFileReturnsEmptyPreamble(@TempDir tmp: Path) {
        val file = tmp.resolve("core_memory.md")
        assertEquals("", CoreMemory.preambleFrom(file))
    }

    @Test
    fun emptyFileReturnsEmptyPreamble(@TempDir tmp: Path) {
        val file = tmp.resolve("core_memory.md")
        Files.writeString(file, "")
        assertEquals("", CoreMemory.preambleFrom(file))
    }

    @Test
    fun whitespaceOnlyFileReturnsEmptyPreamble(@TempDir tmp: Path) {
        val file = tmp.resolve("core_memory.md")
        Files.writeString(file, "   \n\n  \t\n")
        assertEquals("", CoreMemory.preambleFrom(file))
    }

    @Test
    fun fileWithContentReturnsPinnedSectionWrapper(@TempDir tmp: Path) {
        val file = tmp.resolve("core_memory.md")
        Files.writeString(file, "English default. No sidetracks.\n")
        val out = CoreMemory.preambleFrom(file)
        assertTrue(out.contains("Pinned context"), "preamble names a 'Pinned context' section")
        assertTrue(out.contains("English default. No sidetracks."), "user content embedded verbatim")
    }

    @Test
    fun readFromMissingFileReturnsEmptyString(@TempDir tmp: Path) {
        val file = tmp.resolve("nope.md")
        assertEquals("", CoreMemory.readFrom(file))
    }
}
