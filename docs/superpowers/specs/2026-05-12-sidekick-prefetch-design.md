# Sidekick Pre-Fetch Retrieval — Design

> Slice 2.5 follow-up. Closes GAP-1 from `docs/superpowers/findings/2026-05-12-slice2-dogfood.md`.
> Council reviewed 2026-05-12; verdict FLAWED-but-fixable at `.claude/council-cache/council-1778534960.md`. This spec embeds the 4 council-required mitigations.

---

## 1. Goal

When the user clicks the InlineAskChip on a selected passage in the tutor workspace, the sidekick reply MUST include citation pills pointing into the user's own corpus (`_extras/<subject>/...`) whenever such material exists, without requiring the LLM to decide to invoke the `search_archival` tool.

**Non-goals (deferred):**
- Re-shaping the chip-flow UX so retrieval is shown to the user BEFORE the LLM answers (First Principles council concern; deserves its own brainstorm — backlog as "your-notes-first" iteration).
- Cross-subject material surfacing (e.g. POO graph-theory hits on a PA task).
- Reranking pre-fetched vs. LLM-fetched hits beyond simple union + dedup in `CitationExtractor`.

---

## 2. Motivation

Dogfood pass 2026-05-12 on Tema A revealed:
- Slice 2 wiring works end-to-end via direct API call with retrieval-prompted `user_question`.
- Chip-flow real path (`user_question = selectedText`) yields zero citations because the LLM answers Romanian/R drill questions from baseline knowledge and never invokes `search_archival`.
- Domain Expert council agent: free-tier LLMs skip retrieval tools ~30–50% of the time on short queries — observed behavior matches the literature.

Server-side pre-fetch is the canonical RAG pre-retrieval pattern (LangChain `RetrievalQA`, LlamaIndex `RetrieverQueryEngine`, OpenAI Assistants `file_search`, Perplexity, Cohere Coral, Claude Projects). Keeping `search_archival` as a fallback tool so the LLM can broaden when pre-fetch misses is the established hybrid (Vespa, Weaviate).

---

## 3. Architecture

```
[chip click in TutorWorkspace]
        │
        ▼  POST /api/v1/sidekick/ask  { task_id, selection, user_question, ... }
┌────────────────────────────────────────────────────────────────────┐
│ TutorRoutes.kt /api/v1/sidekick/ask handler                         │
│   csrfProtect → session check → envelope decode                     │
│                                                                    │
│   STEP A (NEW): selection-quality gate + pre-fetch                  │
│   ┌──────────────────────────────────────────────────────────┐    │
│   │ if (env.selection valid) {                               │    │
│   │   query = SelectionQueryBuilder.build(env)               │    │
│   │   if (query.shouldFetch) {                               │    │
│   │     subject = TaskRepo.findSubject(env.task_id)          │    │
│   │     try {                                                │    │
│   │       prefetchedHits = HybridRetriever.search(           │    │
│   │           query.text, k=3, subject=subject)              │    │
│   │           .map { normalizePath(it) }                     │    │
│   │           .take(3)                                       │    │
│   │     } catch (e: Exception) {                             │    │
│   │       stderr.println("[sidekick prefetch] " + e.message) │    │
│   │       prefetchedHits = emptyList()                       │    │
│   │     }                                                    │    │
│   │   }                                                      │    │
│   │ }                                                        │    │
│   └──────────────────────────────────────────────────────────┘    │
│                                                                    │
│   STEP B: build systemContext WITH prefetchedHits                   │
│   STEP C: JarvisToolset.chat (unchanged — search_archival still in  │
│           tool palette so LLM can broaden)                          │
│   STEP D: union accumulatedHits ← prefetchedHits ∪ LLM-fetched hits │
│   STEP E: CitationExtractor.extract(reply.text, unionedHits)        │
│   STEP F: respond ApiSidekickReply                                  │
└────────────────────────────────────────────────────────────────────┘
```

Pre-fetch sits before LLM call. Citations populate from pre-fetched hits regardless of LLM tool-use behavior. LLM still has the option to broaden via `search_archival`; its hits get unioned, deduped, and rendered as additional citation pills.

---

## 4. Components

### 4.1 `SelectionQueryBuilder` (new, `src/main/kotlin/jarvis/tutor/SelectionQueryBuilder.kt`)

Pure function that decides whether and what to pre-fetch.

```kotlin
object SelectionQueryBuilder {
    data class Query(val text: String, val shouldFetch: Boolean)

    private const val MIN_LEN = 12
    private const val MAX_LEN = 300

    /** Strip prompt-injection markers + LaTeX/code-only fragments + length-gate. */
    fun build(env: SidekickEnvelope): Query {
        val raw = env.selection?.trim().orEmpty()
        val sanitized = sanitize(raw)

        // Fallback: too-short selection → use anchor paragraph if present.
        val candidate = if (sanitized.length < MIN_LEN) {
            env.anchorText?.trim()?.let { sanitize(it) }.orEmpty()
        } else sanitized

        // Skip when still too short, too long, or only operators/code.
        val ok = candidate.length in MIN_LEN..MAX_LEN && hasContentChars(candidate)
        return Query(text = candidate.take(MAX_LEN), shouldFetch = ok)
    }

    private fun sanitize(s: String): String =
        s.replace("<retrieved_context", "&lt;retrieved_context", ignoreCase = true)
         .replace("</retrieved_context>", "&lt;/retrieved_context&gt;", ignoreCase = true)
         .replace("```", "` ``")

    /** True when ≥ 4 letter/digit chars exist (i.e. NOT a pure math/code/operator blob). */
    private fun hasContentChars(s: String): Boolean =
        s.count { it.isLetterOrDigit() } >= 4
}
```

Rationale per council:
- `MIN_LEN = 12` catches the "ecuația de mai sus" → empty case Devil's Advocate flagged (too short = low BM25 recall).
- `MAX_LEN = 300` caps the full Romanian drill paragraph case (too long = noisy BM25).
- `anchor_text` fallback handles the 2-word-selection case where the surrounding paragraph IS the better query.
- `hasContentChars` rejects pure-LaTeX selections like `f(s) = ...` which would otherwise tokenize to operator noise.
- `sanitize` neutralizes `</retrieved_context>` paste-injection attacks per Risk Analyst HIGH#2.

### 4.2 Task subject lookup (reuse existing `TaskRepo.findById`)

`jarvis.tutor.TaskRepo.findById(id: String): Task?` already exists in `src/main/kotlin/jarvis/tutor/Tasks.kt:159`. `Task.subject: String` is non-null on the entity. The sidekick handler inline:
```kotlin
val subject = env.taskId
    ?.let { TaskRepo(ctx.db).findById(it)?.subject }
    ?.takeIf { it.isNotBlank() }
```

No new repo method. No new file.

### 4.3 `SidekickContext.systemContext` (extended)

Add optional `prefetchedHits: List<HybridRetriever.HybridHit>` parameter (default `emptyList()`). When non-empty, append a `<retrieved_context source="prefetched_corpus" trust="indexed_data">` block BEFORE `CITATION_INSTRUCTION`.

Snippet rendering inside the block:
```
[prefetched score=0.95] _extras/PS/_fii/edu/files/Tema_A_en.md
<first 200 chars of hit.snippet, single-line>
```

Rationale per council Pragmatist KEY CONCERN: 200-char cap × 3 hits ≈ 600 tokens injected, not 1800.

### 4.4 `pathNormalize` (private helper or extend `HybridHit` in place)

Single point — at the pre-fetch boundary in TutorRoutes — replace `\` with `/` on every `id` before downstream consumption. Mirrors the `/reprep` flow already doing this normalization.

### 4.5 `CitationExtractor` (no functional change)

Already iterates over a `verifiedIds = hits.associateBy { it.id }` map. With normalized paths, the union of pre-fetched and LLM-fetched hits will key cleanly. Just verify the `_extras/<subject>/` path emitted in the system-prompt prefetched block matches what CitationExtractor expects (same canonical form).

---

## 5. Data flow (end-to-end, happy path)

1. Alex selects "distributia Laplace" on the drill card.
2. Chip renders. Click → POST `/api/v1/sidekick/ask` with `{ task_id: 01KR..., selection: "distributia Laplace", user_question: "distributia Laplace" }` (chip currently echoes selection as question; that does NOT change in this spec).
3. Handler: `SelectionQueryBuilder.build(env)` → `Query(text="distributia Laplace", shouldFetch=true)` (16 chars, ≥ MIN_LEN, has letters).
4. `TaskRepo.findSubject("01KR...")` → `"PS"`.
5. `HybridRetriever.search("distributia Laplace", k=3, subject="PS")` returns 3 normalized `HybridHit`s under `_extras/PS/`.
6. `SidekickContext.systemContext(env, prefetchedHits = [...])` produces system prompt with the `<retrieved_context source="prefetched_corpus" trust="indexed_data">` block.
7. `JarvisToolset.chat(systemContext, user_question)` round-trips with LLM. LLM emits `(src: _extras/PS/_fii/edu/files/Tema_A_en.md)` markers because the prefetched block makes those paths visible.
8. `CitationExtractor.extract(reply.text, unionedHits)` returns 1–3 `ApiCitation`s — all verified against the union set.
9. Frontend `Sidekick` renders reply + `CitationPill` strip. Pill click → `ResourceRail.forceOpenPath` drawer for that path.

---

## 6. Error handling

| Failure | Behavior | Source of design |
|---------|----------|------------------|
| `env.selection` null/blank | Skip pre-fetch entirely; behave like Slice 2 baseline. | unchanged |
| `env.selection` too short (< 12 chars) AND no `anchor_text` | `shouldFetch=false`; no prefetched block; LLM may still call `search_archival`. | §4.1, Pragmatist condition |
| `env.selection` too long (> 300) | Truncate to 300 chars before query; flag the truncation in stderr. | §4.1, Domain Expert KEY CONCERN |
| `env.selection` contains injection tag (`</retrieved_context>`, ` ``` `) | Tag escaped before query AND before snippet wrap. | §4.1 `sanitize`, Risk Analyst HIGH#2 |
| `env.task_id` unknown / null | Skip subject filter; pre-fetch global (lexical-only) with `subject=null`. | §4.2 |
| `HybridRetriever.search` throws (IO, ClassNotFound, etc.) | Catch `Exception`; log stderr `[sidekick prefetch] <ex>`; pre-fetch hits = empty; LLM call proceeds normally. | §3 STEP A try/catch, Risk Analyst HIGH#1 |
| LLM throws | Existing graceful 200 `(LLM unavailable; rate-limited?)` path. CitationExtractor runs on pre-fetched hits only (still produces useful citations if LLM reply happened to land before exception). | unchanged |
| `CitationExtractor` finds no `(src:)` markers | `citations: []` in reply (LLM didn't cite); pre-fetched hits NOT auto-surfaced as pills — user gets baseline reply. | known UX gap, accept for now |

Critical invariant per Risk Analyst HIGH#1: ANY pre-fetch failure must degrade silently to `prefetchedHits = []`, NEVER to a 500. The existing sidekick handler `catch (e: Exception)` wraps only the LLM call; STEP A pre-fetch needs its OWN catch.

---

## 7. Testing

| Test | Type | File | Coverage |
|------|------|------|----------|
| `selectionQuery_buildShortSelectionUsesAnchorText` | unit | `SelectionQueryBuilderTest.kt` (new) | §4.1 fallback |
| `selectionQuery_buildSanitizesInjectionTags` | unit | same | §4.1 sanitize |
| `selectionQuery_buildRejectsPureLatex` | unit | same | §4.1 `hasContentChars` |
| `selectionQuery_buildTruncatesOver300Chars` | unit | same | §4.1 cap |
| `sidekickContext_includesPrefetchBlockWhenHitsPresent` | unit | `SidekickContextTest.kt` (extend) | §4.3 |
| `sidekickContext_omitsPrefetchBlockWhenHitsEmpty` | unit | same | §4.3 |
| `taskRepo_findSubjectReturnsNullForMissingTask` | unit | `TaskRepoTest.kt` (extend) | §4.2 |
| `sidekick_prefetchExceptionDoesNotFail500` | integration | `TutorRoutesTest.kt` (extend) | §6 critical invariant |
| `sidekick_chipFlowProducesCitations` | Playwright | `slice2-prefetch-gate.mjs` (new) | end-to-end §5 |

The Playwright gate is the load-bearing one per the 2026-05-11 selectors-painted-≠-selectors-work rule. It must:
1. Open `/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E`.
2. Select drill text (Laplace passage).
3. Click `[data-testid="inline-ask-chip"]`.
4. Wait up to 30s for `[data-testid="sidekick-reply"]` to appear.
5. Assert `[data-testid^="citation-pill"]` count ≥ 1.
6. Assert at least one pill `title` or `aria-label` contains `_extras/PS/`.
7. Click first pill → assert `[data-testid="concept-drawer"]` paints (ResourceRail synthesizes a transient CONCEPT item for the path via `forceOpenPath`); no 4xx network response.

---

## 8. Acceptance criteria

What Alex SHOULD see at `https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E` after this slice ships:

- Select "distributia Laplace" or similar concept phrase → `[data-testid="inline-ask-chip"]` paints.
- Click chip → `[data-testid="sidekick-reply"]` paints within ~30s with prose reply.
- BELOW the prose reply, at least 1 `[data-testid^="citation-pill"]` is visible, label includes a path under `_extras/PS/_fii/` or `_extras/PS/ps_hw/`.
- Click a citation pill → `[data-testid="concept-drawer"]` paints with the file content (no 404, no HTTP-error text on screen). ResourceRail synthesizes a transient CONCEPT item for the pill's path via `forceOpenPath` (see `ResourceRail.tsx:68-87`).
- No NEW 4xx/5xx network responses during the chip-click → reply flow beyond what Slice 2 baseline already produces.

Bundle hash unchanged (backend-only); no frontend recompile needed. The acceptance is gated on backend deploy + service restart + Playwright run-through.

---

## 9. Out of scope / deferred

- **FP reframe** — "show YOUR notes BEFORE LLM generation" as the primary sidekick affordance. Real concern: chip-flow `user_question = selectedText` lets the LLM solve the drill for Alex, which inverts Roediger/Karpicke testing-effect. Solving this means UI changes (show retrieval panel first, gate LLM behind "I've tried, now help"). Brainstorm separately. Don't conflate with this fix.
- **Cross-subject hits** — current scope hard-filters to `_extras/<subject>/`. POO graph-theory results on a PA task are invisible. Accept for now; widen later if dogfood shows it matters.
- **Query rewriting** — LangChain `MultiQueryRetriever`-style LLM rewrite of selection → better BM25 query. Heavyweight; the cheap length-gate + anchor-fallback in §4.1 buys most of the value at near-zero cost. Revisit if dogfood shows precision is still poor.
- **Re-ranking** — Cohere Rerank or a cheap heuristic to re-order union of pre-fetched + LLM-fetched hits. Not needed for k=3.

---

## 10. References

- Slice 2 spec: `docs/superpowers/specs/2026-05-11-tutor-slice2-corpus-rag-sidekick-design.md`
- Slice 2 plan: `docs/superpowers/plans/2026-05-11-tutor-slice2-corpus-rag-sidekick.md`
- Dogfood findings (BUG-1 + GAP-1): `docs/superpowers/findings/2026-05-12-slice2-dogfood.md`
- Council verdict (FLAWED → 4 mitigations): `.claude/council-cache/council-1778534960.md`
- Subject-filter fix commit: `8d17644`
- HybridRetriever: `src/main/kotlin/jarvis/HybridRetriever.kt`
- CitationExtractor: `src/main/kotlin/jarvis/tutor/CitationExtractor.kt`
- Sidekick handler: `src/main/kotlin/jarvis/web/TutorRoutes.kt:1296-1339`
