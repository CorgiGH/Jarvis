package jarvis

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FsrsSeedMainTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Helper: encode [s] as a JSON string literal (with surrounding quotes
     *  and proper escaping) so it can be embedded inside a larger JSON object
     *  without hand-escaping backslashes in test sources. */
    private fun jsonString(s: String): String = JsonPrimitive(s).toString()

    @Test
    fun `extractCards parses plain inner JSON`() {
        val inner = """{"cards":[{"front":"What is Big-O","back":"Upper bound on growth"},{"front":"DFS vs BFS","back":"Stack vs queue"}]}"""
        val stdout = """{"type":"result","result":${jsonString(inner)}}"""
        val cards = extractCards(stdout, json)
        assertNotNull(cards)
        assertEquals(2, cards.size)
        assertEquals("What is Big-O", cards[0].front)
        assertEquals("Stack vs queue", cards[1].back)
    }

    @Test
    fun `extractCards strips markdown json fence`() {
        val inner = "```json\n{\"cards\":[{\"front\":\"Q1\",\"back\":\"A1\"}]}\n```"
        val stdout = """{"type":"result","result":${jsonString(inner)}}"""
        val cards = extractCards(stdout, json)
        assertNotNull(cards)
        assertEquals(1, cards.size)
        assertEquals("Q1", cards[0].front)
    }

    @Test
    fun `extractCards strips plain triple-backtick fence`() {
        val inner = "```\n{\"cards\":[{\"front\":\"Q\",\"back\":\"A\"}]}\n```"
        val stdout = """{"result":${jsonString(inner)}}"""
        val cards = extractCards(stdout, json)
        assertNotNull(cards)
        assertEquals(1, cards.size)
    }

    @Test
    fun `extractCards returns null on non-JSON outer`() {
        assertNull(extractCards("not json at all", json))
    }

    @Test
    fun `extractCards returns null when no recognized inner key`() {
        // Outer parses but has no result/text/content carrying the model output.
        val stdout = """{"type":"result","cost":0.001}"""
        assertNull(extractCards(stdout, json))
    }

    @Test
    fun `extractCards defaults to empty list when inner JSON lacks cards field`() {
        // ignoreUnknownKeys + CardsReply's default makes this parseable as empty.
        // Empty-list outcome is safe for the seeder (no inserts).
        val inner = """{"unexpected":"shape"}"""
        val stdout = """{"result":${jsonString(inner)}}"""
        val cards = extractCards(stdout, json)
        assertNotNull(cards)
        assertEquals(0, cards.size)
    }

    @Test
    fun `extractCards tolerates text field instead of result`() {
        val inner = """{"cards":[{"front":"X","back":"Y"}]}"""
        val stdout = """{"text":${jsonString(inner)}}"""
        val cards = extractCards(stdout, json)
        assertNotNull(cards)
        assertEquals(1, cards.size)
        assertEquals("X", cards[0].front)
    }

    @Test
    fun `extractCards walks event array and picks final type=result element`() {
        // Mirrors the real claude --output-format json stream: top-level array
        // of events, the last is {type:"result", result:"<text>"}.
        val inner = """{"cards":[{"front":"What is Big-O","back":"Upper bound"}]}"""
        val fenced = "```json\n$inner\n```"
        val resultText = jsonString(fenced)
        val stdout = """[
            {"type":"system","subtype":"init","session_id":"abc"},
            {"type":"assistant","message":{"id":"m1"}},
            {"type":"rate_limit_event","rate_limit_info":{"utilization":0.5}},
            {"type":"result","subtype":"success","result":$resultText,"total_cost_usd":0.001}
        ]"""
        val cards = extractCards(stdout, json)
        assertNotNull(cards)
        assertEquals(1, cards.size)
        assertEquals("What is Big-O", cards[0].front)
        assertEquals("Upper bound", cards[0].back)
    }

    @Test
    fun `extractCards returns null on event array with no result element`() {
        val stdout = """[
            {"type":"system","subtype":"init"},
            {"type":"assistant","message":{"id":"m1"}}
        ]"""
        assertNull(extractCards(stdout, json))
    }

    @Test
    fun `extractCards returns empty list when model says cards array is empty`() {
        val inner = """{"cards":[]}"""
        val stdout = """{"result":${jsonString(inner)}}"""
        val cards = extractCards(stdout, json)
        assertNotNull(cards)
        assertEquals(0, cards.size)
    }

    @Test
    fun `filterUnseeded drops docs whose derived source_ref is in seeded set`() {
        val docs = listOf(
            Paths.get("tmp-md", "PA", "courses", "lecture11_en.md"),
            Paths.get("tmp-md", "PA", "courses", "lecture12_en.md"),
            Paths.get("tmp-md", "PS", "courses", "ps1.md"),
            Paths.get("tmp-md", "PS", "courses", "ps2.md"),
        )
        val seeded = setOf("PA:lecture11_en.md", "PS:ps1.md")
        val kept = filterUnseeded(docs, seeded, subjectOverride = null)
        assertEquals(2, kept.size)
        assertTrue(kept.any { it.fileName.toString() == "lecture12_en.md" })
        assertTrue(kept.any { it.fileName.toString() == "ps2.md" })
    }

    @Test
    fun `filterUnseeded keeps everything when seeded set is empty`() {
        val docs = listOf(
            Paths.get("tmp-md", "ALO", "courses", "alo_c01.md"),
            Paths.get("tmp-md", "ALO", "courses", "alo_c02.md"),
        )
        val kept = filterUnseeded(docs, emptySet(), subjectOverride = null)
        assertEquals(2, kept.size)
    }

    @Test
    fun `filterUnseeded respects subjectOverride when deriving ref`() {
        // Path doesn't contain a known subject segment — override decides.
        val docs = listOf(
            Paths.get("scratch", "notes", "doc1.md"),
            Paths.get("scratch", "notes", "doc2.md"),
        )
        val seeded = setOf("XX:doc1.md")
        val kept = filterUnseeded(docs, seeded, subjectOverride = "XX")
        assertEquals(1, kept.size)
        assertEquals("doc2.md", kept[0].fileName.toString())
    }
}
