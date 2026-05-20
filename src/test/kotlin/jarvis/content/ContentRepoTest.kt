package jarvis.content

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentRepoTest {
    private fun seed(root: Path) {
        root.resolve("subjects.yaml").writeText(
            "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"P\"\n    name_en: \"Algorithm Design\"\n"
        )
        val pa = root.resolve("PA"); pa.resolve("kcs").createDirectories()
        pa.resolve("_sources").createDirectories()
        pa.resolve("kcs/pa-kc-001.yaml").writeText(
            "id: pa-kc-001\nsubject: PA\nname_ro: \"A\"\nname_en: \"Algorithm\"\n" +
            "cluster: f\nbloom_level: understand\ndifficulty: 1\ntime_minutes: 10\n" +
            "exam_weight: 1.0\ntier: 1\nversion: 1\n"
        )
        pa.resolve("edges.yaml").writeText("subject: PA\nedges: []\n")
        pa.resolve("_sources/pa-lecture-01.md").writeText("An algorithm is a finite sequence of steps.")
    }

    @Test
    fun `loads manifest`(@TempDir tmp: Path) {
        seed(tmp)
        val m = ContentRepo(tmp).loadManifest()
        assertEquals("PA", m.subjects.single().id)
    }

    @Test
    fun `loads a subject with its kcs and edges`(@TempDir tmp: Path) {
        seed(tmp)
        val sc = ContentRepo(tmp).loadSubject("PA")
        assertEquals(1, sc.kcs.size)
        assertEquals("pa-kc-001", sc.kcs.single().id)
        assertTrue(sc.edges.isEmpty())
        assertTrue(sc.misconceptions.isEmpty())
    }

    @Test
    fun `reads extracted source text`(@TempDir tmp: Path) {
        seed(tmp)
        val txt = ContentRepo(tmp).sourceText("PA", "pa-lecture-01")
        assertTrue(txt!!.contains("finite sequence"))
        assertNull(ContentRepo(tmp).sourceText("PA", "missing-doc"))
    }
}
