package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Plan-2 Task 3 — VizInstance content (spec §3.3/§5.3/§5.5) + the content/{subject}/viz/ loader.
 * Mirrors the KC loader: one YAML per instance, sorted by id. Unknown family_id is ALLOWED
 * (registry = Plan 3); a duplicate id within a subject is a load ERROR. Empty/absent viz/ = fine.
 */
class VizInstanceLoaderTest {

    private fun writeSubjectsManifest(root: Path) {
        root.resolve("subjects.yaml").writeText(
            """
            version: 1
            subjects:
              - id: PA
                name_ro: "Proiectarea Algoritmilor"
                name_en: "Algorithm Design"
            """.trimIndent(),
        )
    }

    private fun writeViz(root: Path, fileName: String, body: String) {
        val dir = root.resolve("PA").resolve("viz")
        dir.createDirectories()
        dir.resolve(fileName).writeText(body.trimIndent())
    }

    @Test fun `a viz instance YAML round-trips`() {
        val yaml = """
            id: viz-pa-recursion-001
            subject: PA
            family_id: recursion-tree
            language: ro
            instance:
              method_kc_id: pa-kc-fixture-recursion
              data_json: '{"nodes": ["f(3)", "f(2)", "f(1)"], "depth": 3}'
        """.trimIndent()
        val v = Yaml.default.decodeFromString(VizInstance.serializer(), yaml)
        assertEquals("viz-pa-recursion-001", v.id)
        assertEquals("PA", v.subject)
        assertEquals("recursion-tree", v.family_id)
        assertEquals("ro", v.language)
        assertEquals("pa-kc-fixture-recursion", v.instance.method_kc_id)
        val parsed = v.instance.dataElement() as kotlinx.serialization.json.JsonObject
        assertTrue(parsed.containsKey("nodes"))
        assertEquals("3", parsed["depth"].toString())
    }

    @Test fun `loadVizInstances reads every viz yaml for a subject, sorted by id`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "b.yaml", """
            id: viz-pa-002
            subject: PA
            family_id: recursion-tree
            instance:
              method_kc_id: pa-kc-001
        """)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-001
            subject: PA
            family_id: number-line
            instance:
              method_kc_id: pa-kc-005
        """)
        val instances = ContentRepo(tmp).loadVizInstances("PA")
        assertEquals(listOf("viz-pa-001", "viz-pa-002"), instances.map { it.id })
    }

    @Test fun `an unknown family_id is allowed at load (registry lands in Plan 3)`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-001
            subject: PA
            family_id: a-family-that-does-not-exist-yet
            instance:
              method_kc_id: pa-kc-001
        """)
        val instances = ContentRepo(tmp).loadVizInstances("PA")
        assertEquals(1, instances.size)
        assertEquals("a-family-that-does-not-exist-yet", instances.single().family_id)
    }

    @Test fun `a duplicate instance id within a subject is a load error`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-dup
            subject: PA
            family_id: recursion-tree
            instance:
              method_kc_id: pa-kc-001
        """)
        writeViz(tmp, "b.yaml", """
            id: viz-pa-dup
            subject: PA
            family_id: number-line
            instance:
              method_kc_id: pa-kc-005
        """)
        val e = assertFailsWith<IllegalStateException> { ContentRepo(tmp).loadVizInstances("PA") }
        assertTrue(e.message!!.contains("viz-pa-dup"), "the error names the duplicate id: ${e.message}")
        assertTrue(e.message!!.contains("PA"), "the error names the subject: ${e.message}")
    }

    @Test fun `an absent viz dir yields an empty list (every subject today)`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        tmp.resolve("PA").createDirectories()   // subject dir exists, no viz/ subdir
        assertEquals(emptyList(), ContentRepo(tmp).loadVizInstances("PA"))
    }

    @Test fun `malformed data_json is a load error naming the instance`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-bad-json
            subject: PA
            family_id: recursion-tree
            instance:
              method_kc_id: pa-kc-001
              data_json: '{"nodes": [unclosed'
        """)
        val e = assertFailsWith<IllegalStateException> { ContentRepo(tmp).loadVizInstances("PA") }
        assertTrue(e.message!!.contains("viz-pa-bad-json"), "the error names the instance: ${e.message}")
        assertTrue(e.message!!.contains("data_json"), "the error names the field: ${e.message}")
    }
}
