package jarvis.tutor

import jarvis.content.SourceRef
import jarvis.content.Span
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 14 (L4, CHANGE 8) — Problem widen: sourceRefs + modelTag.
 *
 * Back-compat contract:
 *   - Old blobs (no sourceRefs / no modelTag) decode with defaults (emptyList, null).
 *   - New Problems with both fields set round-trip via tutorJson.
 *   - No DDL: Problem is a JSON blob in task_prep.problems_json.
 */
class ProblemWidenDecodeTest {

    /**
     * "Old blob" — encoded by tutorJson (kotlinx.serialization, camelCase keys).
     * Represents a problems_json row written by a server BEFORE sourceRefs/modelTag were added.
     * Back-compat requirement: decode must produce sourceRefs=emptyList(), modelTag=null.
     */
    private val oldBlobSingleProblem =
        """[{"problemId":"A1","page":4,"statement":"derive MLE","equationRefs":[],"dataGivens":[],"kcIds":[],"rubricItems":[],"referenceSolution":null,"canonicalAnswer":null,"shape":null}]"""

    /** Old blob with all pre-existing optional fields but still no sourceRefs / modelTag. */
    private val oldBlobFull =
        """[{"problemId":"B1","page":2,"statement":"compute median","equationRefs":["eq1"],"dataGivens":["x=5"],"kcIds":["pa-kc-001"],"rubricItems":["step1"],"referenceSolution":"sol","canonicalAnswer":"42","shape":"computational"}]"""

    @Test
    fun `old blob without new fields decodes with defaults`() {
        val problems = TutorTypes.tutorJson.decodeFromString(
            ListSerializer(Problem.serializer()),
            oldBlobSingleProblem
        )
        assertEquals(1, problems.size)
        val p = problems[0]
        assertEquals("A1", p.problemId)
        assertEquals(4, p.page)
        assertEquals("derive MLE", p.statement)
        // New fields must be their defaults.
        assertTrue(p.sourceRefs.isEmpty(), "sourceRefs should default to emptyList()")
        assertNull(p.modelTag, "modelTag should default to null")
    }

    @Test
    fun `old blob with all pre-existing optional fields decodes with new-field defaults`() {
        val problems = TutorTypes.tutorJson.decodeFromString(
            ListSerializer(Problem.serializer()),
            oldBlobFull
        )
        assertEquals(1, problems.size)
        val p = problems[0]
        assertEquals("B1", p.problemId)
        assertEquals(listOf("eq1"), p.equationRefs)
        assertEquals(listOf("pa-kc-001"), p.kcIds)
        assertTrue(p.sourceRefs.isEmpty(), "sourceRefs should default to emptyList()")
        assertNull(p.modelTag, "modelTag should default to null")
    }

    @Test
    fun `round-trips with new fields set`() {
        val original = Problem(
            problemId = "C1",
            page = 3,
            statement = "prove NP-hardness",
            sourceRefs = listOf(
                SourceRef(
                    doc = "sipser-ch8.md",
                    quote = "NP-completeness via reduction",
                    page = 312,
                    span = Span(4200, 4260),
                    provenance = "vision-confirmed"
                )
            ),
            modelTag = "claude-sonnet-4-5"
        )
        val encoded = TutorTypes.tutorJson.encodeToString(Problem.serializer(), original)
        val decoded = TutorTypes.tutorJson.decodeFromString(Problem.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals(1, decoded.sourceRefs.size)
        assertEquals("sipser-ch8.md", decoded.sourceRefs[0].doc)
        assertEquals("claude-sonnet-4-5", decoded.modelTag)
    }

    @Test
    fun `round-trips with emptyList sourceRefs and null modelTag`() {
        val original = Problem(
            problemId = "D1",
            page = 1,
            statement = "base case",
            sourceRefs = emptyList(),
            modelTag = null
        )
        val encoded = TutorTypes.tutorJson.encodeToString(Problem.serializer(), original)
        val decoded = TutorTypes.tutorJson.decodeFromString(Problem.serializer(), encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.sourceRefs.isEmpty())
        assertNull(decoded.modelTag)
    }

    @Test
    fun `multiple problems in array round-trip including new fields`() {
        val problems = listOf(
            Problem("E1", 1, "old-style problem"),
            Problem("E2", 2, "new problem", modelTag = "claude-haiku-3-5"),
            Problem(
                problemId = "E3",
                page = 3,
                statement = "with sourceRef",
                sourceRefs = listOf(SourceRef(doc = "doc.md", quote = "some quote", page = 10))
            )
        )
        val encoded = TutorTypes.tutorJson.encodeToString(
            ListSerializer(Problem.serializer()), problems
        )
        val decoded = TutorTypes.tutorJson.decodeFromString(
            ListSerializer(Problem.serializer()), encoded
        )
        assertEquals(3, decoded.size)
        assertTrue(decoded[0].sourceRefs.isEmpty())
        assertNull(decoded[0].modelTag)
        assertEquals("claude-haiku-3-5", decoded[1].modelTag)
        assertTrue(decoded[1].sourceRefs.isEmpty())
        assertEquals("some quote", decoded[2].sourceRefs[0].quote)
    }
}
