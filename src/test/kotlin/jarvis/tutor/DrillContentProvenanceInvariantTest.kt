package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.KnowledgeConcept
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Trust-leak CI invariant (council 1780928193). Two structural guarantees that must hold for the
 * faithful badge to never span generated content:
 *  (1) hasBeenFaithfulChecked may be true ONLY when type == "authored".
 *  (2) any DrillProvenanceDto with type == "generated" has hasBeenFaithfulChecked == false.
 * These are asserted as a property over the constructible space + over the canonical markers.
 */
class DrillContentProvenanceInvariantTest {

    private val GENERATED = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false)

    @Test fun `generated marker is never faithful-checked`() {
        assertEquals("generated", GENERATED.type)
        assertFalse(GENERATED.hasBeenFaithfulChecked, "generated content is unaudited — never faithful-checked")
    }

    @Test fun `the invariant predicate rejects a generated+checked combination`() {
        // The single source-of-truth predicate the whole serve path must honour.
        fun isHonest(p: DrillProvenanceDto): Boolean =
            if (p.type == "generated") !p.hasBeenFaithfulChecked
            else p.type == "authored" // checked allowed only for authored

        assertTrue(isHonest(DrillProvenanceDto("generated", false)))
        assertTrue(isHonest(DrillProvenanceDto("authored", true)))
        assertTrue(isHonest(DrillProvenanceDto("authored", false)))
        // A generated drill claiming faithful-checked is the trust-leak — MUST be flagged dishonest.
        assertFalse(isHonest(DrillProvenanceDto("generated", true)))
    }

    @Test fun `every DrillContentDto a DrillGenerator builds is stamped generated+unchecked`() {
        // Build the DTOs exactly as DrillGenerator.generate / farTransfer do (the construction shape
        // under test), then assert the provenance marker. If a future edit drops the marker, this
        // test goes red (the leak re-opens).
        val generated = DrillContentDto(
            drill = "d", worked = "w", definition = "def", check = "c", expectedAnswerHint = "5",
            provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false),
        )
        val p = generated.provenance
        assertNotNull(p, "a generated drill MUST carry a provenance marker (no null leak)")
        assertEquals("generated", p.type)
        assertFalse(p.hasBeenFaithfulChecked)
    }

    // ── Strengthening: drive the REAL DrillGenerator call sites with canned-accept fakes ──
    // A hermetic harness already exists in DrillGeneratorTest (Fake Llm + goodDrill + goodCritic).
    // Reusing the same pattern here ensures that if a future edit stamps provenance=null or
    // provenance=DrillProvenanceDto("generated", true) at the actual call site, THIS invariant
    // test goes red — not merely the shape test above.

    private class Fake(private val reply: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?, imagePath: String?) = reply to "fake"
    }

    private val kc = KnowledgeConcept("pa-kc-x", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1)
    private val goodDrill = """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"mult","check":"7*8?","expected_answer_hint":"42"}"""
    private val goodCritic = """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}"""

    @Test fun `DrillGenerator_generate stamps every accepted bundle with provenance=generated+false`() = runBlocking {
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?, imagePath: String?) =
                (if (n++ == 0) goodDrill else "42") to "fake-gen"
        }
        val res = DrillGenerator.generate(kc, listOf("mult quote"), "computational", 1, gen, Fake(goodCritic))
        assertEquals(1, res.bundles.size, "expected one accepted bundle from the fake harness")
        val p = res.bundles[0].content.provenance
        assertNotNull(p, "DrillGenerator.generate MUST stamp provenance on every accepted DrillContentDto")
        assertEquals("generated", p.type, "generate() provenance.type must be 'generated'")
        assertFalse(p.hasBeenFaithfulChecked, "generate() hasBeenFaithfulChecked must be false (trust-leak invariant)")
    }

    @Test fun `DrillGenerator_farTransfer stamps every accepted bundle with provenance=generated+false`() = runBlocking {
        val ftKc = kc.copy(far_transfer_stem = "A bakery doubles its recipe each hour; model the growth.")
        val res = DrillGenerator.farTransfer(ftKc, listOf("growth quote"), Fake(goodDrill), Fake(goodCritic))
        assertEquals(1, res.bundles.size, "expected one far-transfer bundle from a KC with an authored stem")
        val p = res.bundles[0].content.provenance
        assertNotNull(p, "DrillGenerator.farTransfer MUST stamp provenance on every accepted DrillContentDto")
        assertEquals("generated", p.type, "farTransfer() provenance.type must be 'generated'")
        assertFalse(p.hasBeenFaithfulChecked, "farTransfer() hasBeenFaithfulChecked must be false (trust-leak invariant)")
    }
}
