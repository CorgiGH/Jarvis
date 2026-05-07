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

    @Test
    fun scanTextForPiiCatchesEmail() {
        val findings = CoreMemory.scanTextForPii("ping me at user@example.org tomorrow")
        assertEquals(1, findings.size)
        assertEquals("email", findings[0].kind)
        assertTrue(findings[0].match.contains("user@example.org"))
    }

    @Test
    fun scanTextForPiiCatchesMatricol() {
        val findings = CoreMemory.scanTextForPii("My matricol is 31091001031ROSL251002")
        assertTrue(findings.any { it.kind == "matricol" }, "matricol shape detected")
    }

    @Test
    fun scanTextForPiiCatchesPhone() {
        val findings = CoreMemory.scanTextForPii("call +1 555 234 5678 after 3")
        assertTrue(findings.any { it.kind == "phone" }, "phone shape detected")
    }

    @Test
    fun scanTextForPiiOnCleanTextReturnsEmpty() {
        val findings = CoreMemory.scanTextForPii("just a normal sentence about Kotlin")
        assertEquals(emptyList(), findings)
    }

    @Test
    fun scanTextForPiiOnEmptyReturnsEmpty() {
        assertEquals(emptyList(), CoreMemory.scanTextForPii(""))
    }
}
