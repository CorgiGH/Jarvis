package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorTypesTest {
    @Test
    fun `ulid produces 26-char crockford base32 monotonic identifiers`() {
        val a = TutorTypes.ulid()
        Thread.sleep(2)
        val b = TutorTypes.ulid()
        assertEquals(26, a.length)
        assertEquals(26, b.length)
        assertTrue(a < b, "ULIDs must be lexicographically monotonic over time")
        assertTrue(a.matches(Regex("[0-9A-HJKMNP-TV-Z]{26}")))
    }

    @Test
    fun `tutorJson encodes and decodes simple data class`() {
        @kotlinx.serialization.Serializable
        data class Box(val v: String)
        val s = TutorTypes.tutorJson.encodeToString(kotlinx.serialization.serializer<Box>(), Box("x"))
        val back = TutorTypes.tutorJson.decodeFromString<Box>(kotlinx.serialization.serializer<Box>(), s)
        assertEquals("x", back.v)
    }
}
