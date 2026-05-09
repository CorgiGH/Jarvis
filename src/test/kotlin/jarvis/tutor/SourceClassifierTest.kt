package jarvis.tutor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceClassifierTest {

    private fun extr(file: String? = null, raw: String = "") = ScreenshotExtraction(
        filePath = file,
        cursor = null,
        consoleOutput = null,
        error = null,
        rawReply = raw,
    )

    @Test
    fun localKotlinFileIsAllowed() {
        val r = SourceClassifier.classify(extr(file = "C:\\Users\\u\\proj\\src\\main\\kotlin\\Foo.kt"))
        assertEquals(SourceClassifier.Mode.ALLOWED, r.mode)
        assertTrue(r.reason.contains(".kt"))
    }

    @Test
    fun pythonFileAllowed() {
        val r = SourceClassifier.classify(extr(file = "/home/u/script.py"))
        assertEquals(SourceClassifier.Mode.ALLOWED, r.mode)
    }

    @Test
    fun stackoverflowUrlReadOnly() {
        val r = SourceClassifier.classify(extr(file = "https://stackoverflow.com/questions/123"))
        assertEquals(SourceClassifier.Mode.READ_ONLY, r.mode)
        assertTrue(r.reason.contains("stackoverflow"))
    }

    @Test
    fun youtubeReadOnly() {
        val r = SourceClassifier.classify(extr(file = "https://www.youtube.com/watch?v=xyz"))
        assertEquals(SourceClassifier.Mode.READ_ONLY, r.mode)
    }

    @Test
    fun browserTitleStringReadOnly() {
        // Window-title shape "Page - Browser" with .com → not a file path.
        val r = SourceClassifier.classify(extr(
            file = "Stack Overflow - Mozilla Firefox",
            raw = "stackoverflow.com/q/123",
        ))
        assertEquals(SourceClassifier.Mode.READ_ONLY, r.mode)
    }

    @Test
    fun rawOcrCatchesBareHostname() {
        val r = SourceClassifier.classify(extr(
            file = null,
            raw = "this looks like a reddit.com page screenshot",
        ))
        assertEquals(SourceClassifier.Mode.READ_ONLY, r.mode)
        assertTrue(r.reason.contains("reddit"))
    }

    @Test
    fun nullPathFailsClosed() {
        val r = SourceClassifier.classify(extr(file = null, raw = ""))
        assertEquals(SourceClassifier.Mode.READ_ONLY, r.mode)
        assertTrue(r.reason.contains("no code-editor signal"))
    }

    @Test
    fun unknownExtensionFailsClosed() {
        val r = SourceClassifier.classify(extr(file = "/home/u/document.weird"))
        assertEquals(SourceClassifier.Mode.READ_ONLY, r.mode)
    }

    @Test
    fun chatGptReadOnly() {
        val r = SourceClassifier.classify(extr(file = "https://chat.openai.com/c/abc"))
        assertEquals(SourceClassifier.Mode.READ_ONLY, r.mode)
    }

    @Test
    fun typescriptFileAllowed() {
        val r = SourceClassifier.classify(extr(file = "/work/app/src/main.tsx"))
        assertEquals(SourceClassifier.Mode.ALLOWED, r.mode)
    }

    @Test
    fun rustFileAllowed() {
        val r = SourceClassifier.classify(extr(file = "src/main.rs"))
        assertEquals(SourceClassifier.Mode.ALLOWED, r.mode)
    }

    @Test
    fun yamlFileAllowed() {
        val r = SourceClassifier.classify(extr(file = "k8s/deploy.yaml"))
        assertEquals(SourceClassifier.Mode.ALLOWED, r.mode)
    }
}
