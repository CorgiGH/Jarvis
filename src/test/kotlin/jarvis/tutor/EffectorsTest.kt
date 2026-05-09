package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectorsTest {
    @Test
    fun `ApplyEditRequest round-trips JSON`() {
        val req = ApplyEditRequest(
            taskId = "T01HVY",
            effectorId = "E01HVZ",
            targetUri = "file:///c/uaic/ps/laplace_mle.R",
            expectedDocVersion = "abcd1234",
            edits = listOf(TextEdit(Range(Position(3, 5), Position(3, 12)), "median(x)")),
            nonce = "nonce-1",
            grantId = "G01HVY",
        )
        val json = TutorTypes.tutorJson.encodeToString(ApplyEditRequest.serializer(), req)
        val back = TutorTypes.tutorJson.decodeFromString(ApplyEditRequest.serializer(), json)
        assertEquals(req, back)
    }

    @Test
    fun `EffectorType enum covers expected operations`() {
        val expected = setOf("APPLY_EDIT", "RUN_R", "NAVIGATE", "INSERT_SCRATCHPAD")
        val actual = EffectorType.values().map { it.name }.toSet()
        assertTrue(actual.containsAll(expected))
    }

    @Test
    fun `Outcome enum covers expected statuses`() {
        val expected = setOf("SUCCESS", "REJECTED", "ROLLED_BACK", "STALE_DOC", "PATH_DENIED")
        val actual = Outcome.values().map { it.name }.toSet()
        assertTrue(actual.containsAll(expected))
    }
}
