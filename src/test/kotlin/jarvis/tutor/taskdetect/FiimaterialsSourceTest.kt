package jarvis.tutor.taskdetect

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiimaterialsSourceTest {
    @Test
    fun `discover returns empty when root missing`() = runBlocking {
        val src = FiimaterialsSource(Path.of("/nonexistent/path/abc-phase6-test"))
        assertTrue(src.discover().isEmpty())
    }

    @Test
    fun `discover walks meta_json sidecars + maps to DetectedTask`(@TempDir tmp: Path) = runBlocking {
        val pa = tmp.resolve("PA").resolve("extracted").resolve("abcd1234")
        Files.createDirectories(pa)
        Files.writeString(pa.resolve("Subiect_partial_2021.pdf"), "%PDF-fake")
        Files.writeString(pa.resolve("meta.json"), """
            {"sourceUrl":"https://x/y.pdf","fetchedAt":"2026-05-10T00:00:00Z",
             "sha256":"abcd1234","subject":"PA","kind":"exam"}
        """.trimIndent())

        val src = FiimaterialsSource(tmp)
        val tasks = src.discover()
        assertEquals(1, tasks.size)
        val t = tasks[0]
        assertEquals("fiimaterials", t.sourceId)
        assertEquals("abcd1234", t.externalId)
        assertEquals("PA", t.subject)
        assertEquals("Subiect partial 2021", t.title)
        assertEquals("exam", t.rawMetadata["kind"])
    }
}
