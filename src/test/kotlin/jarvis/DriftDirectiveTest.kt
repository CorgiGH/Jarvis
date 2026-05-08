package jarvis

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DriftDirectiveTest {

    private fun drift(
        subject: String = "PA",
        topic: String? = "dynamic programming",
        actualReason: String = "off-topic browser (likely social media)",
    ) = ActiveDoc.Drift(
        expectedSubject = subject,
        expectedTopic = topic,
        actualConcept = null,
        actualReason = actualReason,
    )

    @Test
    fun startsWithGetBackTo() {
        val out = DriftDirective.build(drift())
        assertTrue(out.startsWith("GET BACK TO PA"), "expected leading directive, got: $out")
    }

    @Test
    fun mentionsTopicWhenSet() {
        val out = DriftDirective.build(drift(topic = "greedy"))
        assertTrue(out.contains("PA/greedy"))
    }

    @Test
    fun omitsTopicWhenNull() {
        val out = DriftDirective.build(drift(topic = null))
        assertFalse(out.contains("/"), "expected no slash with null topic, got: $out")
    }

    @Test
    fun under200Chars() {
        val long = drift(topic = "a".repeat(200))
        val out = DriftDirective.build(long)
        assertTrue(out.length <= 200, "directive should cap at 200 chars, got ${out.length}")
    }

    @Test
    fun hasLessonHint() {
        val out = DriftDirective.build(drift(subject = "POO"))
        assertTrue(out.contains("[[lesson: POO]]"))
    }

    @Test
    fun fallsBackToStudyPrompt() {
        // No assignments + no concepts in test env → falls back to "STUDY: any X"
        val out = DriftDirective.build(drift(subject = "ZZZ", topic = null))
        assertTrue(
            out.contains("STUDY: any ZZZ") || out.contains("STUDY:") || out.contains("NEXT:"),
            "expected fallback hint, got: $out",
        )
    }
}
