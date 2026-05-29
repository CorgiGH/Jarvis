package jarvis.tutor

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.KnowledgeConcept
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrillGeneratorTest {
    private class Fake(private val reply: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) = reply to "fake"
    }
    private val kc = KnowledgeConcept("pa-kc-x", "PA", "a", "a", "c", "understand", 1, 1, 0.0, 1)
    private val goodDrill = """{"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"mult","check":"7*8?","expected_answer_hint":"42"}"""
    private val goodCritic = """{"confidence":0.9,"grounded":true,"leak":false,"solvable":true}"""

    @Test fun `accepts a self-consistent computational drill the critic approves`() = runBlocking {
        // generator: 1st call = drill, 2nd call = self-solve answer "42"
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) =
                (if (n++ == 0) goodDrill else "42") to "fake-gen"
        }
        val res = DrillGenerator.generate(kc, listOf("mult quote"), "computational", 1, gen, Fake(goodCritic))
        assertEquals(1, res.bundles.size)
        assertEquals(listOf("pa-kc-x"), res.bundles[0].problem.kcIds)
        assertEquals("42", res.bundles[0].problem.canonicalAnswer)
        assertEquals("Compute 6*7.", res.bundles[0].content.drill)
        assertNull(res.bundles[0].content.vizId) // kc.viz_id is null here; the route sets it (see GenerateDrillsRouteTest)
    }

    @Test fun `rejects when self-solve disagrees with canonical answer`() = runBlocking {
        val gen = object : Llm {
            var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) =
                (if (n++ == 0) goodDrill else "99") to "fake-gen"  // self-solve says 99 != 42
        }
        val res = DrillGenerator.generate(kc, listOf("q"), "computational", 1, gen, Fake(goodCritic))
        assertEquals(0, res.bundles.size)
        assertTrue(res.rejectReasons.any { it.contains("self-solve") })
    }

    @Test fun `rejects when critic is not confident`() = runBlocking {
        val gen = object : Llm { var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = (if (n++ == 0) goodDrill else "42") to "g" }
        val lowCritic = """{"confidence":0.2,"grounded":true,"leak":false,"solvable":true}"""
        val res = DrillGenerator.generate(kc, listOf("q"), "computational", 1, gen, Fake(lowCritic))
        assertEquals(0, res.bundles.size)
        assertTrue(res.rejectReasons.any { it.contains("critic") })
    }

    @Test fun `rejects when the stem leaks its own answer`() = runBlocking {
        val leaky = """{"statement":"Compute 6*7. The answer is 42.","canonical_answer":"42","rubric_items":["ok"],"worked":"42","definition":"m","check":"c","expected_answer_hint":"42"}"""
        val gen = object : Llm { var n = 0
            override suspend fun complete(m: List<ChatMessage>, t: Int, r: String?) = (if (n++ == 0) leaky else "42") to "g" }
        val res = DrillGenerator.generate(kc, listOf("q"), "computational", 1, gen, Fake(goodCritic))
        assertEquals(0, res.bundles.size)
        assertTrue(res.rejectReasons.any { it.contains("leak") })
    }
}
