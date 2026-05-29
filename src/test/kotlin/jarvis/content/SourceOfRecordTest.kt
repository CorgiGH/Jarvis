package jarvis.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceOfRecordTest {
    private val text = "page one alphapage two beta"

    @Test
    fun `pageOf returns 1-indexed page for a global offset`() {
        assertEquals(1, SourceOfRecord.pageOf(text, 0))
        assertEquals(1, SourceOfRecord.pageOf(text, 13))
        assertEquals(2, SourceOfRecord.pageOf(text, 15))
    }

    @Test
    fun `slice returns the exact raw substring for an in-bounds span`() {
        assertEquals("alpha", SourceOfRecord.slice(text, Span(9, 14)))
    }

    @Test
    fun `slice returns null for an out-of-bounds span`() {
        assertNull(SourceOfRecord.slice(text, Span(100, 110)))
        assertNull(SourceOfRecord.slice(text, Span(10, 5)))
    }

    @Test
    fun `pageOf at the form-feed offset returns the preceding page`() {
        assertEquals(1, SourceOfRecord.pageOf(text, 14))   // the form-feed char itself
        assertEquals(2, SourceOfRecord.pageOf(text, text.length))  // clamped, after the break
    }

    @Test
    fun `slice allows full-range and zero-length spans`() {
        assertEquals(text, SourceOfRecord.slice(text, Span(0, text.length)))
        assertEquals("", SourceOfRecord.slice(text, Span(5, 5)))
    }
}
