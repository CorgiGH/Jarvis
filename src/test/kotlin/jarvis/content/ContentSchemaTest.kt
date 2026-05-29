package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentSchemaTest {
    @Test
    fun `parses a subjects manifest`() {
        val yaml = """
            version: 1
            subjects:
              - id: PA
                name_ro: "Proiectarea Algoritmilor"
                name_en: "Algorithm Design"
        """.trimIndent()
        val m = Yaml.default.decodeFromString(SubjectsManifest.serializer(), yaml)
        assertEquals(1, m.version)
        assertEquals("PA", m.subjects.single().id)
        assertEquals("Algorithm Design", m.subjects.single().name_en)
    }

    @Test
    fun `parses a knowledge concept with source refs`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: "Ce este un algoritm"
            name_en: "What is an algorithm"
            cluster: "foundations"
            bloom_level: "understand"
            difficulty: 1
            time_minutes: 10
            exam_weight: 0.05
            tier: 1
            version: 1
            source:
              - doc: pa-lecture-01
                quote: "An algorithm is a finite sequence of steps"
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("pa-kc-001", kc.id)
        assertEquals(1, kc.tier)
        assertEquals(0.05, kc.exam_weight)
        assertEquals("pa-lecture-01", kc.source.single().doc)
    }

    @Test
    fun `parses an edges file`() {
        val yaml = """
            subject: PA
            edges:
              - kc: pa-kc-002
                prereq: pa-kc-001
                rationale: "complexity needs the definition of an algorithm first"
        """.trimIndent()
        val e = Yaml.default.decodeFromString(EdgesFile.serializer(), yaml)
        assertEquals("pa-kc-002", e.edges.single().kc)
        assertEquals("pa-kc-001", e.edges.single().prereq)
    }

    @Test
    fun `parses a misconception`() {
        val yaml = """
            id: pa-misc-001
            kc_id: pa-kc-001
            label_ro: "Algoritmii trebuie să fie infiniti"
            label_en: "Algorithms must be infinite"
            trigger: "Student confuses loop count with infinite execution"
            refutation: "By definition an algorithm must terminate in finite steps"
            source:
              - doc: pa-lecture-01
                quote: "An algorithm is a finite sequence of steps"
            version: 1
        """.trimIndent()
        val m = Yaml.default.decodeFromString(Misconception.serializer(), yaml)
        assertEquals("pa-misc-001", m.id)
        assertEquals("pa-kc-001", m.kc_id)
        assertEquals("pa-lecture-01", m.source.single().doc)
    }

    @Test
    fun `legacy KC yaml without new source fields still decodes with defaults`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: "A"
            name_en: "Algorithm"
            cluster: f
            bloom_level: understand
            difficulty: 1
            time_minutes: 10
            exam_weight: 1.0
            tier: 1
            source:
              - doc: pa-lecture-01
                quote: "a finite sequence of steps"
            version: 1
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("standard", kc.grounding_tier)
        val ref = kc.source.single()
        assertEquals(0, ref.page)
        assertNull(ref.span)
        assertEquals("pdftotext", ref.provenance)
    }

    @Test
    fun `source ref with page span provenance round-trips`() {
        val yaml = """
            doc: pa-lecture-01
            quote: "x"
            page: 3
            span:
              start: 100
              end: 101
            provenance: vision-confirmed
        """.trimIndent()
        val ref = Yaml.default.decodeFromString(SourceRef.serializer(), yaml)
        assertEquals(3, ref.page)
        assertEquals(100, ref.span?.start)
        assertEquals(101, ref.span?.end)
        assertEquals("vision-confirmed", ref.provenance)
    }
}
