package jarvis.tutor

import jarvis.HybridRetriever
import jarvis.web.ApiCitation

/**
 * Extracts `(src: <path>)` markers from an LLM reply and matches each path
 * against the set of HybridRetriever hits that were actually fed to the LLM.
 * Fabricated paths are dropped silently. Deduplicates by path.
 */
object CitationExtractor {

    // Pattern: `(src: <path>)` where <path> is non-space, non-paren chars.
    // Allows nested slashes, dots, dashes. Min 1 char.
    private val CITE_RX = Regex("""\(src:\s*([^\s\)]+)\s*\)""")

    fun extract(replyText: String, hits: List<HybridRetriever.HybridHit>): List<ApiCitation> {
        val verifiedIds = hits.associateBy { it.id }
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<ApiCitation>()
        for (match in CITE_RX.findAll(replyText)) {
            val path = match.groupValues[1]
            if (path !in verifiedIds) continue       // fabricated
            if (!seen.add(path)) continue            // dedupe
            val hit = verifiedIds.getValue(path)
            out.add(ApiCitation(path = hit.id, snippet = hit.snippet, score = hit.score))
        }
        return out
    }
}
