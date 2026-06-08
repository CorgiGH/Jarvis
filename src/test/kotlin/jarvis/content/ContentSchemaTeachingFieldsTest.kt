package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Grounded-teaching layer (Task 1) — the two NEW authored fields on KnowledgeConcept are
 * nullable + Kotlin-defaulted so every existing YAML deserializes unchanged, AND a YAML that
 * DOES author them round-trips the Romanian text verbatim.
 */
class ContentSchemaTeachingFieldsTest {

    @Test fun `existing YAML without the new fields deserializes with null defaults`() {
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
        assertNull(kc.explanation_ro, "legacy YAML ⇒ explanation_ro defaults null")
        assertNull(kc.worked_example_ro, "legacy YAML ⇒ worked_example_ro defaults null")
    }

    @Test fun `authored Romanian explanation and worked example round-trip verbatim`() {
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
            explanation_ro: "Un algoritm este o colecție bine ordonată de operații neambigue."
            worked_example_ro: "Exemplu: pașii de adunare a două numere sunt neambigui și se opresc."
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals(
            "Un algoritm este o colecție bine ordonată de operații neambigue.",
            kc.explanation_ro,
        )
        assertEquals(
            "Exemplu: pașii de adunare a două numere sunt neambigui și se opresc.",
            kc.worked_example_ro,
        )
    }
}
