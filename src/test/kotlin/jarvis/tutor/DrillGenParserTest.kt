package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrillGenParserTest {
    @Test fun `parses a full generated drill incl grading + render fields`() {
        val raw = """
          Here is the drill:
          {"statement":"Compute 6*7.","canonical_answer":"42","rubric_items":["arithmetic correct"],
           "reference_solution":null,"worked":"6*7=42","definition":"Multiplication.",
           "check":"Compute 7*8.","expected_answer_hint":"42"}
        """.trimIndent()
        val d = DrillGenParser.parse(raw)!!
        assertEquals("Compute 6*7.", d.statement)
        assertEquals("42", d.canonicalAnswer)
        assertEquals(listOf("arithmetic correct"), d.rubricItems)
        assertEquals("6*7=42", d.worked)
        assertEquals("42", d.expectedAnswerHint)
    }

    @Test fun `returns null on garbage`() {
        assertNull(DrillGenParser.parse("no json here"))
    }
}
