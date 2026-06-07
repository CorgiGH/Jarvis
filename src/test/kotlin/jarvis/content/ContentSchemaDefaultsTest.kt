package jarvis.content

import com.charleskorn.kaml.Yaml
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 10 (B4, CHANGE 3) — back-compat deserialize tests.
 * All 8 existing KC YAMLs must deserialize without the new fields present;
 * new fields must take their Kotlin defaults.
 */
class ContentSchemaDefaultsTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun minimalKcYaml(
        id: String = "pa-kc-x",
        examWeight: Double = 1.0,
        tier: Int = 1,
    ) = """
        id: $id
        subject: PA
        name_ro: "Nome RO"
        name_en: "Name EN"
        cluster: "c"
        bloom_level: understand
        difficulty: 1
        time_minutes: 10
        exam_weight: $examWeight
        tier: $tier
        source:
          - doc: pa-lecture-01
            quote: "an algorithm is finite"
        version: 1
    """.trimIndent()

    // Resolve the real content/PA/kcs directory relative to the repo root.
    private val contentRoot = Paths.get("content")

    private fun realKcYamls(): List<java.nio.file.Path> {
        val kcsDir = contentRoot.resolve("PA/kcs")
        return listOf(
            "pa-kc-001.yaml", "pa-kc-002.yaml", "pa-kc-003.yaml",
            "pa-kc-004.yaml", "pa-kc-005.yaml", "pa-kc-006.yaml",
            "pa-kc-fixture-compute.yaml", "pa-kc-fixture-recursion.yaml",
        ).map { kcsDir.resolve(it) }
    }

    // ── Task 10 tests — new KnowledgeConcept fields take defaults ────────────

    @Test
    fun `minimal yaml without new fields deserializes with KnowledgeConcept defaults`() {
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), minimalKcYaml())

        // New fields — verify defaults
        assertEquals("unverified", kc.verification_status)
        assertNull(kc.invariant)
        assertTrue(kc.grader_rules.isEmpty())
        assertNull(kc.stem_template)
        assertNull(kc.phase_plan)
        assertNull(kc.far_transfer_stem)
        assertNull(kc.self_explanation_prompt)
        // worked_example_first default = false
        assertEquals(false, kc.worked_example_first)
    }

    /** The two authored computational KCs (Batch-4 b) intentionally carry strict-grounding fields
     *  (invariant + grader_rules) — they are the FAITHFUL-target KCs of the trust-net. They are
     *  EXEMPT from the "invariant defaults to null" / "grader_rules defaults to empty" assertions;
     *  their authored shape is pinned by AuthoredStrictKcsTest instead. */
    private val authoredStrictKcs = setOf("pa-kc-005", "pa-kc-006")

    /** P1-3(c) — the four KCs that intentionally author the Phase-3 teaching scaffolding fields
     *  (phase_plan + far_transfer_stem + self_explanation_prompt + worked_example_first), each DERIVED
     *  from that KC's own source quotes. They are EXEMPT from the "teaching fields default to null/false"
     *  assertions; their authored shape is pinned by AuthoredTeachingKcsTest. This closes the council's
     *  P1-3(c) gap (no KC authored the teaching fields, so the loop never had teaching content to serve). */
    private val authoredTeachingKcs = setOf("pa-kc-001", "pa-kc-002", "pa-kc-003", "pa-kc-004")

    @Test
    fun `all 8 real KC yamls deserialize and the non-authored ones take defaults`() {
        val paths = realKcYamls()
        assertTrue(paths.isNotEmpty(), "No KC yaml paths found — check content/PA/kcs/ exists")
        for (path in paths) {
            assertTrue(path.exists(), "KC yaml not found: $path")
            val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), path.readText())
            // verification_status is the AUTHORED seed; all real KCs author the default 'unverified'.
            assertEquals("unverified", kc.verification_status,
                "KC ${kc.id}: verification_status should default to 'unverified'")
            // The two authored strict KCs intentionally set invariant + grader_rules — skip those two.
            if (kc.id !in authoredStrictKcs) {
                assertNull(kc.invariant, "KC ${kc.id}: invariant should default to null")
                assertTrue(kc.grader_rules.isEmpty(), "KC ${kc.id}: grader_rules should default to empty list")
            } else {
                assertNotNull(kc.invariant, "authored strict KC ${kc.id}: invariant must be set")
                assertTrue(kc.grader_rules.isNotEmpty(), "authored strict KC ${kc.id}: grader_rules must be non-empty")
            }
            // stem_template still defaults across ALL real KCs (none author it).
            assertNull(kc.stem_template, "KC ${kc.id}: stem_template should default to null")
            // The Phase-3 teaching fields: the authored-teaching KCs SET them (P1-3(c)); the rest default.
            if (kc.id !in authoredTeachingKcs) {
                assertNull(kc.phase_plan, "KC ${kc.id}: phase_plan should default to null")
                assertNull(kc.far_transfer_stem, "KC ${kc.id}: far_transfer_stem should default to null")
                assertNull(kc.self_explanation_prompt, "KC ${kc.id}: self_explanation_prompt should default to null")
                assertEquals(false, kc.worked_example_first,
                    "KC ${kc.id}: worked_example_first should default to false")
            } else {
                assertNotNull(kc.phase_plan, "authored teaching KC ${kc.id}: phase_plan must be set")
                assertNotNull(kc.far_transfer_stem, "authored teaching KC ${kc.id}: far_transfer_stem must be set")
                assertNotNull(kc.self_explanation_prompt,
                    "authored teaching KC ${kc.id}: self_explanation_prompt must be set")
            }
        }
    }

    @Test
    fun `KC yaml with new fields explicitly set round-trips correctly`() {
        val yaml = """
            id: pa-kc-test
            subject: PA
            name_ro: "Test RO"
            name_en: "Test EN"
            cluster: "c"
            bloom_level: apply
            difficulty: 3
            time_minutes: 20
            exam_weight: 1.0
            tier: 1
            source:
              - doc: pa-lecture-01
                quote: "a finite sequence"
            version: 1
            verification_status: "pending"
            invariant: "f(n) ∈ O(g(n)) ⟺ ∃c>0, n₀: ∀n≥n₀, f(n) ≤ c·g(n)"
            grader_rules:
              - "sympy: result == expected"
              - "units: same"
            stem_template: "Compute the complexity of algorithm X."
            phase_plan:
              - intro
              - practice
            far_transfer_stem: "Given algorithm Y, determine its complexity."
            self_explanation_prompt: "Why does this algorithm have this complexity?"
            worked_example_first: true
        """.trimIndent()

        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)

        assertEquals("pending", kc.verification_status)
        assertNotNull(kc.invariant)
        assertTrue(kc.invariant!!.contains("f(n)"))
        assertEquals(2, kc.grader_rules.size)
        assertEquals("sympy: result == expected", kc.grader_rules[0])
        assertEquals("Compute the complexity of algorithm X.", kc.stem_template)
        assertNotNull(kc.phase_plan)
        assertEquals(listOf("intro", "practice"), kc.phase_plan)
        assertEquals("Given algorithm Y, determine its complexity.", kc.far_transfer_stem)
        assertEquals("Why does this algorithm have this complexity?", kc.self_explanation_prompt)
        assertEquals(true, kc.worked_example_first)
    }

    // ── Task 10 tests — new Misconception fields take defaults ───────────────

    @Test
    fun `minimal misconception yaml without new fields deserializes with defaults`() {
        val yaml = """
            id: pa-misc-001
            kc_id: pa-kc-001
            label_ro: "Test RO"
            label_en: "Test EN"
            trigger: "trigger"
            refutation: "refutation"
            source:
              - doc: pa-lecture-01
                quote: "a finite sequence"
            version: 1
        """.trimIndent()
        val m = Yaml.default.decodeFromString(Misconception.serializer(), yaml)

        assertEquals("unverified", m.verification_status)
        assertNull(m.self_explanation_prompt)
        assertNull(m.figure_spec)
    }

    @Test
    fun `misconception yaml with new fields explicitly set round-trips correctly`() {
        val yaml = """
            id: pa-misc-002
            kc_id: pa-kc-001
            label_ro: "Test RO"
            label_en: "Test EN"
            trigger: "trigger"
            refutation: "refutation"
            source:
              - doc: pa-lecture-01
                quote: "a finite sequence"
            version: 1
            verification_status: "faithful"
            self_explanation_prompt: "Why is this wrong?"
            figure_spec: "diagram:asymptote-confusion"
        """.trimIndent()
        val m = Yaml.default.decodeFromString(Misconception.serializer(), yaml)

        assertEquals("faithful", m.verification_status)
        assertEquals("Why is this wrong?", m.self_explanation_prompt)
        assertEquals("diagram:asymptote-confusion", m.figure_spec)
    }
}
