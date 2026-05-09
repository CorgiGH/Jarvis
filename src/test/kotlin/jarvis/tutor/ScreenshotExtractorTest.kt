package jarvis.tutor

import jarvis.VisionLlm
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenshotExtractorTest {

    private class FakeVisionLlm(private val replyText: String) : VisionLlm {
        override suspend fun analyze(
            prompt: String, imageBase64: String, mediaType: String,
            maxTokens: Int, model: String?,
        ): String = replyText
    }

    @Test
    fun parsesPlainJsonReply() {
        val raw = """{"file_path":"src/foo.kt","cursor":{"line":42,"col":7},"console_output":"$ ./run","error":null}"""
        val r = ScreenshotExtractor.parseExtraction(raw)
        assertEquals("src/foo.kt", r.filePath)
        assertEquals(42, r.cursor?.line)
        assertEquals(7, r.cursor?.col)
        assertEquals("$ ./run", r.consoleOutput)
        assertNull(r.error)
    }

    @Test
    fun stripsMarkdownFences() {
        val raw = """```json
{"file_path":"a.py","cursor":null,"console_output":null,"error":"NameError: x"}
```"""
        val r = ScreenshotExtractor.parseExtraction(raw)
        assertEquals("a.py", r.filePath)
        assertEquals("NameError: x", r.error)
        assertNull(r.cursor)
    }

    @Test
    fun stripsBareFences() {
        val raw = """```
{"file_path":"x"}
```"""
        val r = ScreenshotExtractor.parseExtraction(raw)
        assertEquals("x", r.filePath)
    }

    @Test
    fun fallsBackToFirstObjectInGarbageReply() {
        val raw = """Sure, here's the JSON: {"file_path":"deep.py","cursor":null,"console_output":null,"error":null} let me know if you want more."""
        val r = ScreenshotExtractor.parseExtraction(raw)
        assertEquals("deep.py", r.filePath)
    }

    @Test
    fun garbageReplyReturnsAllNullsButPreservesRaw() {
        val raw = "I cannot see any code in this image."
        val r = ScreenshotExtractor.parseExtraction(raw)
        assertNull(r.filePath)
        assertNull(r.cursor)
        assertNull(r.consoleOutput)
        assertNull(r.error)
        assertTrue(r.rawReply.contains("I cannot see any code"))
    }

    @Test
    fun consoleOutputCapped1000Chars() {
        val long = "x".repeat(2000)
        val raw = """{"file_path":null,"cursor":null,"console_output":"$long","error":null}"""
        val r = ScreenshotExtractor.parseExtraction(raw)
        assertEquals(1000, r.consoleOutput?.length)
    }

    @Test
    fun errorCapped2000Chars() {
        val long = "e".repeat(3000)
        val raw = """{"file_path":null,"cursor":null,"console_output":null,"error":"$long"}"""
        val r = ScreenshotExtractor.parseExtraction(raw)
        assertEquals(2000, r.error?.length)
    }

    @Test
    fun missingKeysParseToNull() {
        val r = ScreenshotExtractor.parseExtraction("""{"file_path":"a.kt"}""")
        assertEquals("a.kt", r.filePath)
        assertNull(r.cursor)
        assertNull(r.consoleOutput)
        assertNull(r.error)
    }

    @Test
    fun cursorWithMissingFieldsSkipped() {
        val r = ScreenshotExtractor.parseExtraction("""{"cursor":{"line":5}}""")
        assertNull(r.cursor, "cursor missing col → null")
    }

    @Test
    fun blankReplyAllNulls() {
        val r = ScreenshotExtractor.parseExtraction("")
        assertNull(r.filePath)
        assertNull(r.cursor)
    }

    @Test
    fun extractInvokesVisionLlmAndParses() = runBlocking {
        val fake = FakeVisionLlm(
            """{"file_path":"main.rs","cursor":{"line":1,"col":0},"console_output":"hello","error":null}""",
        )
        val r = ScreenshotExtractor.extract(fake, "AAAA", "image/png")
        assertEquals("main.rs", r.filePath)
        assertEquals(1, r.cursor?.line)
        assertEquals("hello", r.consoleOutput)
    }

    @Test
    fun promptHintAppendedToBasePrompt() = runBlocking {
        // Capture prompt via a recording fake.
        var seenPrompt = ""
        val fake = object : VisionLlm {
            override suspend fun analyze(
                prompt: String, imageBase64: String, mediaType: String,
                maxTokens: Int, model: String?,
            ): String { seenPrompt = prompt; return "{}" }
        }
        ScreenshotExtractor.extract(fake, "AAAA", promptHint = "user just ran cargo build")
        assertTrue(seenPrompt.contains("Additional hint"), "hint embedded: $seenPrompt")
        assertTrue(seenPrompt.contains("cargo build"))
        // Base prompt still present.
        assertTrue(seenPrompt.contains("file_path"), "base prompt preserved")
    }

    @Test
    fun explicitNullStringsParsedAsNull() {
        // Some vision LLMs emit "null" as a string instead of JSON null.
        val raw = """{"file_path":"null","error":null}"""
        val r = ScreenshotExtractor.parseExtraction(raw)
        // We treat any blank/non-blank string as the value; "null" string IS
        // a valid filename, so we don't second-guess. Caller filters if needed.
        assertEquals("null", r.filePath)
    }
}
