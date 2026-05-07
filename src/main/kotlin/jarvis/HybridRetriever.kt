package jarvis

import jarvis.embeddings.VectorStore
import jarvis.subsystem.SearchSubsystem
import java.nio.file.Path

/**
 * R4 (deep-research recommendation #4 / Mem0 3-pass parity) — fuses semantic
 * (vector store), lexical (archival corpus), and entity-match retrieval into
 * one ranked list via Reciprocal Rank Fusion.
 *
 * Not a wrapper around any single primitive; it's a small fan-out/fan-in.
 * Caller supplies `semanticEmbed` (the only piece needing network/keys); when
 * null, semantic pass is skipped and the other two carry alone — graceful
 * degradation when OPENROUTER_API_KEY is absent.
 */
object HybridRetriever {

    data class HybridHit(
        val source: String,   // "semantic" | "lexical" | "entity"
        val id: String,        // VectorStore id or archival relative path
        val snippet: String,
        val score: Double,
    )

    /** RRF k constant — TREC-2009 standard 60. Smoothes rank-1-vs-rank-2 gap. */
    private const val RRF_K = 60.0

    /** Capitalized-stopwords filter — "I", "The", "A", and ISO-week-day-ish
     *  / month-ish words that match the entity-extract regex but don't help. */
    private val ENTITY_STOPWORDS = setOf(
        "I", "The", "A", "An", "And", "Or", "But", "This", "That",
        "Is", "Are", "Was", "Were", "Be", "Been", "Have", "Has",
        "Had", "Do", "Does", "Did", "Will", "Would", "Could", "Should",
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
        "Saturday", "Sunday", "January", "February", "March", "April",
        "May", "June", "July", "August", "September", "October",
        "November", "December",
    )

    fun extractEntities(query: String): List<String> {
        // Capitalized non-stopwords ≥ 2 chars. Single-letter capitals filtered.
        val pattern = Regex("""\b[A-Z][A-Za-z0-9]+\b""")
        return pattern.findAll(query)
            .map { it.value }
            .filter { it.length >= 2 && it !in ENTITY_STOPWORDS }
            .distinct()
            .toList()
    }

    suspend fun search(
        query: String,
        k: Int = 5,
        archivalRoot: Path = Config.archivalDir,
        semanticEmbed: (suspend (String) -> FloatArray)? = null,
    ): List<HybridHit> {
        val q = query.trim()
        if (q.isEmpty() || k <= 0) return emptyList()

        // Each list is a ranked sequence — list[0] = rank 1.
        val lists = mutableListOf<List<HybridHit>>()

        // 1) Semantic
        if (semanticEmbed != null) {
            try {
                val embedding = semanticEmbed(q)
                val matches = VectorStore.search(embedding, k = k * 2, minScore = 0.2f)
                lists += matches.map { (entry, score) ->
                    HybridHit(
                        source = "semantic",
                        id = entry.id,
                        snippet = entry.text.lineSequence()
                            .filter { it.isNotBlank() }.take(6).joinToString("\n").take(800),
                        score = score.toDouble(),
                    )
                }
            } catch (_: Exception) {
                // semantic failure is non-fatal; fall through to lex+entity.
            }
        }

        // 2) Lexical
        val lexHits = SearchSubsystem.searchIn(archivalRoot, q, k * 2)
        lists += lexHits.map { hit ->
            HybridHit(
                source = "lexical",
                id = runCatching { archivalRoot.relativize(hit.path).toString() }
                    .getOrElse { hit.path.toString() },
                snippet = hit.snippet,
                score = hit.hits.toDouble(),
            )
        }

        // 3) Entity — extract capitalized tokens, lexical-search each, merge.
        val entities = extractEntities(q)
        if (entities.isNotEmpty()) {
            val merged = mutableMapOf<String, HybridHit>()
            for (e in entities) {
                val hits = SearchSubsystem.searchIn(archivalRoot, e, k * 2)
                for (h in hits) {
                    val rel = runCatching { archivalRoot.relativize(h.path).toString() }
                        .getOrElse { h.path.toString() }
                    val prev = merged[rel]
                    val newScore = (prev?.score ?: 0.0) + h.hits
                    merged[rel] = HybridHit("entity", rel, h.snippet, newScore)
                }
            }
            lists += merged.values.sortedByDescending { it.score }.take(k * 2)
        }

        if (lists.isEmpty()) return emptyList()

        // RRF fusion: deduplicate by (source-agnostic id), score = Σ 1/(K + rank).
        // Source kept on the *first-encountered* hit so the snippet still surfaces.
        val rrf = mutableMapOf<String, HybridHit>()
        for (list in lists) {
            list.forEachIndexed { i, hit ->
                val rank = i + 1
                val contribution = 1.0 / (RRF_K + rank)
                val prev = rrf[hit.id]
                if (prev == null) {
                    rrf[hit.id] = hit.copy(score = contribution)
                } else {
                    rrf[hit.id] = prev.copy(score = prev.score + contribution)
                }
            }
        }
        return rrf.values.sortedByDescending { it.score }.take(k)
    }
}
