package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ContentSchemaVizTest {
    @Test fun `kc without viz fields decodes with defaults`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: a
            name_en: a
            cluster: c
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 1.0
            tier: 1
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertNull(kc.viz_id)
        assertFalse(kc.requires_visual)
    }

    @Test fun `kc with viz fields decodes`() {
        val yaml = """
            id: x
            subject: PA
            name_ro: a
            name_en: a
            cluster: c
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.0
            tier: 2
            viz_id: recursion-tree
            requires_visual: true
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("recursion-tree", kc.viz_id)
        assertEquals(true, kc.requires_visual)
    }
}
