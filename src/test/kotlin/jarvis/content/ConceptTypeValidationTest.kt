package jarvis.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-2 Task 1 — load-time validation of concept_type. A non-null concept_type MUST be a valid
 * wire literal (ConceptType.fromWire non-null), else a `concept_type_enum` error. Null is ALLOWED
 * in this task (Task 4 tightens it to required-for-all after the YAML backfill).
 */
class ConceptTypeValidationTest {

    private fun kc(id: String, conceptType: String?) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "ro-$id", name_en = "en-$id",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = 0.0, tier = 1, source = emptyList(), version = 1,
        concept_type = conceptType,
    )

    private fun loaded(vararg kcs: KnowledgeConcept) =
        LoadedSubject("PA", kcs = kcs.toList(), edges = emptyList(), misconceptions = emptyList())

    @Test fun `a valid wire literal passes`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", "definition-taxonomy")))
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `null concept_type passes in this task`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", null)))
        assertTrue(issues.isEmpty(), "null is allowed until Task 4 tightens it: $issues")
    }

    @Test fun `an invalid literal is an error naming the KC, the bad value, and the 8 valid literals`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", "procedural")))
        assertEquals(1, issues.size)
        val it = issues.single()
        assertEquals("error", it.severity)
        assertEquals("concept_type_enum", it.rule)
        assertTrue(it.detail.contains("k1"), "names the KC id: ${it.detail}")
        assertTrue(it.detail.contains("procedural"), "names the bad literal: ${it.detail}")
        for (valid in listOf(
            "procedure", "proof", "definition-taxonomy", "code-trace",
            "timing", "probabilistic", "comparison", "formula-application",
        )) {
            assertTrue(it.detail.contains(valid), "lists valid literal '$valid': ${it.detail}")
        }
    }

    @Test fun `the check runs inside validate() over the full subject`() {
        val report = ContentValidator.validate(listOf(loaded(kc("k1", "not-a-type")))) { null }
        assertTrue(
            report.issues.any { it.rule == "concept_type_enum" && it.detail.contains("k1") },
            "validate() must surface the concept_type_enum error: ${report.issues}",
        )
    }
}
