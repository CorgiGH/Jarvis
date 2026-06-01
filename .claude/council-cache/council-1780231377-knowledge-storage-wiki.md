# Council review — 1780231377 (knowledge storage: LLM-wiki vs activate-dormant; port os-study-guide?)

**Problem:** (Q1) Should jarvis implement an "LLM-wiki" knowledge store (the Karpathy idea) for (A) a textbook KB for verbatim verification [oracle for rule #2] + (B) concept navigation/teaching — OR activate/extend existing dormant infra? (Q2) Is Alex's os-study-guide implementation appropriate to port?
**Hard constraint:** the verification oracle (A) must compare against VERBATIM source text; a distilled wiki/summary cannot be the oracle (drift).
**Verdict:** Don't build/import a new wiki. ACTIVATE dormant infra. Port ~nothing runtime from os-study-guide.
**Timestamp:** 2026-05-31 (epoch 1780231377). 4/5 agents code-verified their claims against the repo.

---

## 🔴 Devil's Advocate — REJECT
Q1 REDUNDANT — don't implement a new store; activate dormant infra. The oracle substrate already physically exists + is verbatim: `content/PA/_sources/pa-lecture-01.md` is extracted verbatim from the slides, and each KC `source[].quote` (pa-kc-001.yaml:13) is a verbatim span with a provenance `doc:` pointer. The only gap for (A) is a verbatim string/span comparison against that existing text — not a new store, emphatically not a distilled one. For (B), RAG is built but provably dormant: the only callers of `VectorStore.add` are `EmbeddingsPipeline.indexTurnAsync` (chat turns) + `ReindexMain` (legacy MemoryWiki); KC corpus + `_sources` never embedded. Q2 — port essentially nothing: prior council 1780007694 ruled os-study-guide a category mismatch; the Obsidian vault is a gitignored human-curator artifact, the React JSON-reader is the wrong domain, and porting a Node curate pipeline forks the corpus into two divergent extractors (jarvis already ships its own Kotlin extraction via the curate-tutor skill).
KEY CONCERN: A distilled wiki silently wired in as the oracle — laundering model drift as ground truth. Seductive trap: `WikiPage.kt` is NAMED "Karpathy LLM Wiki" so it tempts reuse as the knowledge store, but its own contract declares it a per-USER learner-state journal (append-only, PII-scrubbed). The oracle must compare against `_sources/*.md` verbatim only; WikiPage must stay a learner journal.

## 📚 Domain Expert — APPROVE (of "activate, don't build")
Verified against code. Two distinct primitives for two jobs. (A) verification oracle = verbatim-chunk retrieval over immutable text — jarvis already has the CORRECT oracle: `SourceOfRecord` holds raw pdftotext `_sources/{doc}.md`, `SourceRef` carries char-offset `span`+`provenance`, `ContentValidator.kt:205` does byte-exact `slice != ref.quote` (strict KCs require vision-confirmed span). A distilled wiki as oracle would be the classic drift anti-pattern. (B) navigation = structured/graph retrieval (GraphRAG) — jarvis already has the KC prereq DAG (`edges.yaml`/`PrereqEdge`) + `KnowledgeGraph`. So Q1: ACTIVATE/extend dormant infra; the only gap is wiring (`VectorStore.add` only from chat turns + ReindexMain; KC corpus + `_sources` unindexed). `WikiPage` is the WRONG primitive for reference truth (per-user learner-state journal). Q2: port ONLY the extraction approach, which jarvis largely already owns (`SourceOfRecord.extract` + `PdfProblemExtractor`); Obsidian vault + React reader are curator-side/category-mismatched. NOTE: could not locate `curate.mjs` under os-study-guide currently (only src/, public/, package.json) — re-verify before citing as a port source.
KEY CONCERN: During activation, index KCs/`_sources` into VectorStore for retrieval/navigation ONLY — the verbatim oracle must keep hitting `SourceOfRecord.slice(text, span)` against frozen `_sources`, never the embedded/chunked copy. If a grader cross-checks against a vector hit (paraphrasable chunk), rule #2's verbatim guarantee degrades into similarity-matching — the exact drift forbidden.

## 🧱 First Principles — REJECT (of "build a wiki")
(A) requires exactly two primitives: an immutable verbatim-text store + a retriever — both exist (`content/{subject}/_sources/{doc}.md` committed raw pdftotext + `SourceRef`/`Span`/`SourceOfRecord.slice()` + dormant `HybridRetriever`/`VectorStore`); the ONLY gap is the embedding pipeline indexes chat turns + legacy MemoryWiki, never the KC sources, and every tutor-route call passes `semanticEmbed = null`. (B) navigation primitive = the concept-graph that exists (`KnowledgeConcept` nodes + `PrereqEdge` directed edges) — concepts ARE the wiki pages, edges ARE the links; a separate LLM-curated wiki would be a second fallible un-grounded copy. `WikiPage.kt` by its own docstring is "NOT a reference store" and "NOT a graph" — structurally cannot be the oracle nor the nav graph. "Implement the Karpathy wiki" solves a problem the KC-graph + dormant RAG already structurally solve; the real task is WIRE THE DORMANT INFRA.
KEY CONCERN: The deliverable is one indexing job + retrieval wiring, not a new subsystem — point the existing `HybridRetriever`/`VectorStore` at `content/**/_sources/*.md` (verbatim, the legitimate oracle for A) and read navigation off the existing `PrereqEdge` DAG (for B). Any LLM-distilled wiki as the comparison target would break (A)'s verbatim constraint.

## ⚙️ Pragmatist — CONDITIONAL
Cheapest path = ACTIVATE dormant infra, even cheaper than the brief implies. `HybridRetriever.search` is ALREADY called in 5 prod tutor routes (`TutorRoutes.kt:1029,1176,1348,1740,1745`), but every callsite passes `semanticEmbed = null` (semantic pass dead in prod), and `VectorStore` is fed only by chat turns (`EmbeddingsPipeline.kt:56`) + MemoryWiki (`ReindexMain.kt:41`) — KC corpus + verbatim `_sources` indexed nowhere. So the textbook-oracle = one indexer job (point ReindexMain at `content/**/_sources` + KC text, reuse `EmbeddingsClient`/`VectorStore.add`) + pass a real embedFn at the prod callsites — days of glue over an already-tested retriever, vs a parallel system. (B) KnowledgeGraph edges + per-user WikiPage already exist. Q2: do NOT port curate.mjs — jarvis owns a Kotlin extraction path (`PdfProblemExtractor`, E2 ingest); a 2nd Node pipeline is net-negative cross-language burden for a solo builder; steal only the prompt templates / bibliography-cross-ref staging IDEAS as text.
KEY CONCERN: The verbatim-oracle needs EXACT-substring verification; embedding cosine is fuzzy/lossy — semantic retrieval finds the candidate `_source` passage but must NOT be the verbatim checker. Wire the existing lexical/exact-match path (or a literal substring validator over the retrieved span) as the oracle, vector pass to FIND only — else "verbatim verification" silently degrades to "approximately similar."

## ⚠️ Risk Analyst — CONDITIONAL
Q1 — activate/extend; do NOT add an LLM-wiki as the oracle. The verbatim oracle exists + is correct: `ContentValidator.checkVerbatimSources` diacritic-EXACT byte-compares each KC `quote` vs `SourceOfRecord.slice(rawText, span)` (raw pdftotext `_sources`), strict tier requires vision-confirmed span. Only gap = retrieval (VectorStore indexes only chat turns). Q2 — never let os-study-guide's Obsidian wiki / React reader become a parallel reference corpus; `WikiPage.kt` is already correctly scoped (per-user PII-scrubbed journal), don't repurpose.
KEY CONCERN: CRITICAL — oracle-comparing-model-output-to-model-output (laundered drift): if any LLM-distilled wiki page becomes the comparison text, drift is stamped "verified truth" + FSRS-burned into a learner who can't self-vet. Mitigation (enforceable): verification accepts ONLY `SourceRef`+`Span` resolving to `_sources/{doc}.md` raw text — add a guard/test asserting no wiki/embedding-derived text reaches `checkVerbatimSources`. Secondary HIGH: (2) semantic-neighbor false-confirm — retrieval = candidate surfacing ONLY; confirm step runs the exact-span diacritic-exact `contains` check, fail-closed. (3) parallel-store drift — single source of record (`content/`+`_sources/`); the embedding index is a derived, rebuildable projection with back-pointers to the canonical span, holding NO authoritative text.

---

## Sanity Check
SANITY [Devil's Advocate]: PASS — code-verified (15 tool uses); the WikiPage-name-trap + distilled-wiki-as-oracle are both real + concrete. In lane.
SANITY [Domain Expert]: PASS — named GraphRAG vs verbatim-chunk RAG; verified ContentValidator/SourceOfRecord; honestly flagged the missing curate.mjs. In lane.
SANITY [First Principles]: PASS — derived the two required primitives + showed both exist; the "wire, don't import" thesis is code-grounded. In lane.
SANITY [Pragmatist]: PASS — verified the 5 callsites + semanticEmbed=null + the two VectorStore feeders; cost estimate concrete (no time estimate, just "glue vs parallel system"). In lane.
SANITY [Risk Analyst]: PASS — the model-output-vs-model-output CRITICAL is exactly the hard constraint; mitigations are enforceable (guard/test). In lane.

No flags. 2 REJECT-the-wiki + 1 APPROVE-activate + 2 CONDITIONAL — unanimous: activate dormant, don't build/import; strict verbatim/navigation separation.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: WRONG APPROACH (to "implement/port a new LLM-wiki") — the right approach is to ACTIVATE what already exists.

CORE FINDING:
jarvis does not need a new knowledge store, and should port almost nothing from os-study-guide. Code-verified by 4 agents: the verbatim verification oracle ALREADY EXISTS and is correct (`ContentValidator.checkVerbatimSources` does a diacritic-exact byte-compare of each KC quote against raw `pdftotext` `_sources` via `SourceOfRecord.slice`); the navigation structure ALREADY EXISTS (the KC concept nodes + `PrereqEdge` graph — concepts are the pages, edges are the links); and the RAG infra (`HybridRetriever` + `VectorStore`) EXISTS but is DORMANT — it indexes only chat turns, the KC corpus + `_sources` are indexed nowhere, and all 5 prod tutor routes pass `semanticEmbed = null` so the semantic pass is dead. The task is WIRING, not importing.

AGENT CONSENSUS: unanimous (2 reject-the-new-wiki, 1 approve-activate, 2 conditional) — 0 flagged.

ANSWERS:
- **Q1 — Do NOT implement/import a new LLM-wiki store. ACTIVATE the dormant infra.** (A) textbook oracle = index the verbatim textbook text (+ KC `_sources`) into the existing `VectorStore`/`HybridRetriever`, and turn the semantic pass on (pass a real embedFn at the 5 callsites; extend the `ReindexMain` indexer to `content/**/_sources` + KC text). (B) navigation = the existing KC graph + `PrereqEdge` edges, with `WikiPage` as the per-user overlay. No new subsystem.
- **Q2 — Port ~nothing runtime from os-study-guide.** Prior council 1780007694 (category mismatch) holds: the Obsidian vault is a gitignored curator artifact (not runtime), the React reader is the wrong domain, and a Node extraction pipeline would fork the corpus into two divergent cross-language extractors (jarvis already has its own Kotlin extraction). At most, steal the LLM prompt-template / bibliography-cross-ref staging IDEAS as written notes — nothing runtime. (Honest flag: a `curate.mjs` was reported by one scan but a code-verifying agent could not find it in os-study-guide now — re-verify before relying on it; doesn't change the verdict.)

HARD INVARIANT (all agents): STRICT SEPARATION.
- Verification (A) compares ONLY against verbatim `_sources` via `SourceRef`+`Span` exact/diacritic-exact match. A distilled wiki/summary or an embedded chunk must NEVER be the comparison text (model-output-vs-model-output = laundered drift, FSRS-burned into a can't-self-vet learner). Enforce with a guard/test: no wiki/embedding-derived text reaches `checkVerbatimSources`.
- The embedding index / any wiki = navigation/teaching surface ONLY: a derived, rebuildable projection with back-pointers to the canonical span, holding NO authoritative text of its own.
- Vector retrieval FINDS the candidate passage (fuzzy); the EXACT-substring contains-check CONFIRMS (fail-closed on a semantic neighbor that lacks the literal asserted condition).
- Do NOT repurpose `WikiPage.kt` (a per-user learner-state journal) as the reference-truth KB despite its name.

RECOMMENDED PATH:
Add to the roadmap a "wire the dormant retrieval infra" component (index textbooks + KC `_sources` into the existing VectorStore/HybridRetriever; activate the semantic pass) and bind rule #2's verbatim oracle to the existing `ContentValidator`/`SourceOfRecord` exact-span path. Treat "the Karpathy wiki" as ALREADY-SOLVED by the KC graph; do not build it. From os-study-guide, capture only prompt-template ideas as notes.

CONFIDENCE: 9
4 agents independently code-verified the dormancy + the existing oracle/graph. What would change it: confirming whether a textbook is best indexed as one big corpus vs per-subject (affects index design, not the verdict), and re-verifying os-study-guide's current extraction code state.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
