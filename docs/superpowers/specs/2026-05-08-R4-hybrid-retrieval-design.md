# R4 — Hybrid retrieval (Mem0 3-pass parity)

## Context

`[[recall]]` is semantic-only via `VectorStore.search`. Mem0's 3-pass retrieval (sem + BM25-lexical + entity-graph) consistently outperforms any single signal. jarvis already has lexical (`SearchSubsystem`) and semantic; missing entity layer + fusion.

## Approaches

- **(a) New `HybridRetriever.search` doing 3 sub-searches + RRF fusion.** ✓ Picked. Composable; reusable from `[[recall]]` + R1 reflection.
- **(b) Inline merging inside `ChatTools.executeRecall`.** Less reusable; harder to test.
- **(c) Build full entity graph (Neo4j-style).** Overkill at single-user scale.

## Design

**New file:** `src/main/kotlin/jarvis/HybridRetriever.kt`

```kotlin
object HybridRetriever {
    data class HybridHit(val source: String, val id: String, val snippet: String, val score: Double)
    suspend fun search(query: String, k: Int = 5, semanticEmbed: (suspend (String) -> FloatArray)? = null): List<HybridHit>
    fun extractEntities(query: String): List<String>  // capitalized non-stopwords
}
```

Three sub-searches:
1. **Semantic** — if `semanticEmbed` provided, embed query → VectorStore.search. Skip when no embedder.
2. **Lexical** — `SearchSubsystem.searchIn(archivalDir, query, k)`.
3. **Entity** — extract capitalized non-stopwords from query, lexical-search each individually + sum hit counts.

**Fusion:** Reciprocal Rank Fusion. Score = Σ_lists 1/(60 + rank). Deduplicate by `(source, id)`. Take top-k.

**Wiring:** `ChatTools.executeRecall` calls `HybridRetriever.search(query, k=5, semanticEmbed = { q -> EmbeddingsClient(key).embed(q) })`. Falls back gracefully when key missing — semantic skipped, lexical+entity carry.

## Edge cases

- Empty query → empty result.
- All sub-searches empty → empty result.
- Same id from two lists → RRF aggregates.
- Capitalized stopwords ("The", "A", "I") → filtered.

## Acceptance criteria

- 6+ tests: RRF math, entity extraction filters stopwords, dedup by (source, id), empty query, one-list-empty fallback.
- Smoke: `[[recall: kotlin]]` returns archival hits even without OPENROUTER key.

## LOC estimate

~120 main + ~80 tests.
