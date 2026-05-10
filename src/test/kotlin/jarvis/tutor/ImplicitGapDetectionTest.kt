package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImplicitGapDetectionTest {
    @Test fun `triggers on i don't understand`() {
        assertEquals("closures", ImplicitGapDetector.detect("I don't understand closures"))
    }
    @Test fun `triggers on what is`() {
        assertEquals("a closure", ImplicitGapDetector.detect("What is a closure?"))
    }
    @Test fun `triggers on how do`() {
        assertEquals("I solve quadratics", ImplicitGapDetector.detect("how do I solve quadratics"))
    }
    @Test fun `no trigger on plain statement`() {
        assertNull(ImplicitGapDetector.detect("the quick brown fox jumps over the lazy dog"))
    }
    @Test fun `trims trailing punctuation`() {
        assertEquals("derivatives", ImplicitGapDetector.detect("what is derivatives?!"))
    }
    @Test fun `caps at 200 chars`() {
        val long = "what is " + "x".repeat(500)
        val out = ImplicitGapDetector.detect(long)!!
        assertTrue(out.length <= 200)
    }
}
