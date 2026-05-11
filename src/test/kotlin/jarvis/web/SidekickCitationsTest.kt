package jarvis.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SidekickCitationsTest {

    @Test
    fun apiSidekickReply_encodes_with_citations_field() {
        val reply = ApiSidekickReply(
            text = "The Laplace MLE is the median (see ps_c4.md).",
            model = "test-model",
            quotedContext = null,
            citations = listOf(
                ApiCitation(
                    path = "_extras/PS/courses/ps_c4.md",
                    snippet = "MLE estimator for Laplace location parameter…",
                    score = 0.78,
                )
            ),
        )
        val json = Json.encodeToString(reply)
        assertTrue(json.contains("\"citations\""))
        assertTrue(json.contains("_extras/PS/courses/ps_c4.md"))
        assertTrue(json.contains("\"score\":0.78"))
    }

    @Test
    fun apiSidekickReply_decodes_without_citations_default_empty_list() {
        // Backwards compat: replies without citations decode with empty list.
        val raw = """{"text":"hi","model":"m","quotedContext":null}"""
        val reply = Json { ignoreUnknownKeys = true }.decodeFromString<ApiSidekickReply>(raw)
        assertEquals(emptyList(), reply.citations)
    }
}
