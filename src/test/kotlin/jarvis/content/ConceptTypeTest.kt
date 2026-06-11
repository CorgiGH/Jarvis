package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Plan-2 Task 1 — the ConceptType wire mapping (spec §3.1) and the additive nullable
 * KnowledgeConcept.concept_type field. The 8 wire literals are the spec's hyphenated
 * strings, FROZEN; fromWire is total (null for anything not in the set).
 */
class ConceptTypeTest {

    @Test fun `every enum constant round-trips through its wire literal`() {
        for (t in ConceptType.entries) {
            val wire = ConceptType.wireOf(t)
            assertEquals(t, ConceptType.fromWire(wire), "wireOf/fromWire must round-trip for $t")
        }
    }

    @Test fun `the 8 wire literals are exactly the spec set`() {
        assertEquals(
            setOf(
                "procedure", "proof", "definition-taxonomy", "code-trace",
                "timing", "probabilistic", "comparison", "formula-application",
            ),
            ConceptType.entries.map { ConceptType.wireOf(it) }.toSet(),
        )
    }

    @Test fun `fromWire returns null for an unknown literal`() {
        assertNull(ConceptType.fromWire("procedural"))   // close but wrong
        assertNull(ConceptType.fromWire("PROCEDURE"))    // case-sensitive
        assertNull(ConceptType.fromWire(""))
    }

    @Test fun `a KC YAML with concept_type deserializes the wire literal verbatim`() {
        val yaml = """
            id: pa-kc-005
            subject: PA
            name_ro: "Dimensiunea valorilor"
            name_en: "Value size"
            cluster: "Eficiența algoritmilor"
            bloom_level: apply
            difficulty: 3
            time_minutes: 30
            exam_weight: 0.13
            tier: 3
            concept_type: formula-application
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("formula-application", kc.concept_type)
        assertEquals(ConceptType.FORMULA_APPLICATION, ConceptType.fromWire(kc.concept_type!!))
    }

    @Test fun `a KC YAML without concept_type defaults to null`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: "Noțiunea de algoritm"
            name_en: "The notion of an algorithm"
            cluster: "Fundamentele algoritmilor"
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.22
            tier: 1
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertNull(kc.concept_type, "legacy YAML ⇒ concept_type defaults null")
    }
}
