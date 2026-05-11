# Tutor Slice 2 — Corpus-RAG Sidekick + Polish

Status: spec — awaiting user review before plan
Author: Claude (in chat with Alex)
Date: 2026-05-11
Decided after: 4 council rounds + 1 UX/pedagogy validity check + Playwright live verify + PDF re-read + corpus sanity probe

---

## 1. Goal

Make the sidekick the **universal teaching surface** across all 5 subjects (PA / PS / POO / ALO / SO+RC) by ingesting the local secondbrain corpus into VPS archival so HybridRetriever has real material to retrieve, plus three independent polish items the live workspace currently lacks (LaTeX rendering, internal-status hiding, rail expansion).

**What this slice ships:**
- A sidekick that, when Alex selects text in a PDF or asks a question, retrieves from a 90+-PDF corpus across all 5 subjects, cites the source filename, and surfaces the citation as a clickable rail link.
- LaTeX `$$...$$` rendering fixed in `Sidekick.tsx` + `DrillCard` body paragraphs (BRIDGE.md:435 known-broken).
- `?debug=1` query flag toggle for DAEMON DOWN badge + domain footer (default-off cleaner UI).
- Resource rail expanded with auto-populated CONCEPT_REF items (HybridRetriever top-N during `/reprep`) + PRIOR_GAP items bound to existing `KnowledgeGapCard`.

**What this slice DOES NOT ship (explicit deferrals):**
- Code REPL (R / Python / C++ execution surface) — deferred to Slice 3 with proper systemd-run sandbox after Rscript env audit on VPS.
- LLM auto-generation of card content (DEFINITION / WORKED / DRILL / CHECK) — deferred to post-finals.
- `pdfTextRaw` column on `task_prep` table — dropped (PDF iframe already serves verbatim source; no need for an additional cached text column).
- Admin HTTP route for card upsert — dropped (SSH SQL upsert from Claude-in-chat sessions is zero new code; no need for an HTTP wrapper).
- Two-mode UI ("starting from zero" toggle), LLM time estimates, hover glossary, LLM prereqs+output per task — all council-rejected as anti-patterns or trust-drift on free-tier LLM.
- Brutalist redesign — explicit user preference, off-limits.

## 2. Why this shape (decision history)

The ride from "rail expansion + LaTeX" → this final shape went through:

1. **Round 1 brainstorm** — original Slice 2 = "tight: LaTeX + rail expansion only."
2. **External critique pasted by user** — claimed cards locked, drill ≠ homework, brutalist UI alienating, "starting from zero" mode needed.
3. **Council round 1** — invalidated (my context contained false premise that DEFINITION+WORKED were unlocked).
4. **Playwright live verify** — confirmed critique was right: only DRILL is unlocked; WORKED/DEFINITION/CHECK literally render "🔒 attempt drill first."
5. **Council round 2** — converged on "unlock + reorder cards" as the one-line fix; flagged drill content disconnect from PDF.
6. **PDF re-read** — confirmed extraction hallucinates: backend `/prep` returns A1="Derive MLE for Laplace" but PDF actually asks "simulate Laplace + plot density vs histogram." All 4 card types in DB are hand-seeded coherent with hallucinated statements (no LLM card-gen pipeline exists; `/reprep` writes `drillsJson = "{}"`).
7. **Council round 3** — recommended hand-curate + REPL + verbatim PDF + sidekick; defer LLM card-gen.
8. **User pushback** — "why am I curating if I'm a novice?"
9. **UX/pedagogy validity check** — user right that novice can't self-validate; user wrong about who curates (Pragmatist quote: "Alex *or Claude in chat* writes the cards"). Khan/Brilliant/Duolingo all run human-author + AI-deliver. Hard rule: Alex never validates **card quality**, but MUST commit DRILL answers + receive feedback (Roediger/Karpicke testing effect).
10. **Council round 4** — "REPL + PDF + sidekick + dormant cards" rejected on three axes:
    - (a) Single-subject overfit (REPL serves PS R + POO C++ only; ALO/SO/PA need proof scratchpads, not REPL).
    - (b) REPL security model missing (`Sys.getenv('OPENROUTER_KEY')` exfiltrates secrets one call away; no sandbox).
    - (c) "Claude-in-chat curates per-task" = ~25h synchronous prep across ~100 tasks; deferred LLM dependency disguised as human curation.
    - Universal teaching surface across all 5 subjects = **corpus-grounded sidekick** via `HybridRetriever`.
11. **Live verify of `HybridRetriever` state** — `HybridRetriever` is shipped + wired into sidekick + works. VPS archival corpus is sparse: POO has rich `.md` files, PS has only `concepts.md`, ALO has nothing for "induction." `tmp-secondbrain-scrape/` (~90 PDFs across 5 subjects + RC) is **not** ingested into VPS archival.

This spec implements the round-4 final shape with the corpus-ingest cost (~1.5-2d) folded in as Phase A.

## 3. Scope (in / out)

### In scope (Slice 2)

| # | Deliverable | Phase | Approx hours |
|---|---|---|---|
| 3.1 | PDF → Markdown converter for `tmp-secondbrain-scrape/` | A | 4-6h |
| 3.2 | Romanian + math glyph normalization step (post-extract) | A | 1-2h |
| 3.3 | scp converted `.md` files to VPS `_extras/<subject>/<kind>/<file>.md` | A | 1h |
| 3.4 | Run `jarvis-kotlin ingest-corpus` on VPS, verify `knowledge.jsonl` covers all 5 subjects + RC | A | 1h |
| 3.5 | Sidekick prompt enrichment: instruct LLM to cite source filename when retrieval used | B | 1-2h |
| 3.6 | UI renders citations as clickable rail link or inline source pill | B | 2-3h |
| 3.7 | `<MathText>` wired in `Sidekick.tsx` + `DrillCard` body paragraphs | C | 1h |
| 3.8 | `?debug=1` query flag — hide `DaemonHealthPill` + domain footer when absent | C | 1h |
| 3.9 | Rail expansion — CONCEPT_REF auto-pop during `/reprep` (HybridRetriever top-N → `task.conceptRefs`) | C | 3-4h |
| 3.10 | Rail expansion — PRIOR_GAP items bound to existing `KnowledgeGapCard` adapter | C | 2-3h |
| 3.11 | Parallel ½-day spike — VPS Rscript + python3 + g++ env audit (gates Slice 3 REPL plan, not in Slice 2 acceptance) | — | 2-3h |

**Total estimate: 3-4 days.**

### Out of scope (deferred)

| Item | Defer to | Why |
|---|---|---|
| REPL (R/Python/C++ exec on VPS) | Slice 3 | Single-subject overfit; security sandbox missing; 4 of 5 subjects need different surface |
| Per-subject workspace shapes (proof scratchpad for ALO, UML editor for POO, etc.) | Slice 4+ | Premature decomposition; need dogfood-driven evidence first |
| LLM card auto-generation pipeline | Post-finals | Free-tier hallucination + 4-card template wrong for compute-heavy problems; revisit with constrained decoding + Marker OCR + Khanmigo-style templates |
| `pdfTextRaw` column on `task_prep` | Drop | PDF iframe IS verbatim source; no separate cached column needed |
| Admin HTTP route `POST /api/v1/task/{id}/prep/upsert` | Drop | SSH SQL is zero new code for Claude-in-chat curation |
| Two-mode UI / "starting from zero" toggle | Drop entirely | Anti-pattern; no production AI tutor at scale ships user-toggled beginner mode |
| LLM-generated time estimates per task | Drop entirely | Trust drift on free-tier; Duolingo/Khan/Anki ship XP / mastery / card counts, not minutes |
| LLM-generated prereqs + output per task | Drop entirely | Same trust-drift class as above |
| Hover glossary | Defer post-finals | Alex hasn't flagged vocab gap; speculative tooling |
| Cheatsheet rail items, prior-solution rail items, sympy workbench rail item | Defer post-finals | Slice 2 rail expansion limited to PRIOR_GAP + CONCEPT_REF |
| Free-chat ChatPane revival on `/tutor/` | Defer post-finals | Sidekick already covers the conversational need |
| Header task switcher dropdown / ⌘K palette | Defer post-finals | Polish, not blocker |
| Brutalist redesign | Off-limits | User-stated preference |
| a11y batch (touch targets, aria-pressed, focus traps) | Slice 5 | Tutor-overhaul-backlog has cumulative items; address as own sweep |
| Mobile responsive (rail collapse) | Slice 5 | Same |

## 4. Architecture

### 4.1 Phase A — corpus ingest (sidekick gets material to retrieve)

**Current state (verified 2026-05-11):**
- `HybridRetriever` exists at `src/main/kotlin/jarvis/HybridRetriever.kt`, wired into `JarvisToolset.chat` which the sidekick endpoint calls.
- VPS archival (`/opt/jarvis/data/archival/`) has thin coverage:
  - POO: rich (`_extras/POO/courses/poo_c5.md`, `Course05.jsx.md`, `study_guide/courses/course-05.md`) — multi-format Romanian + English `.md`.
  - PS: only `_extras/PS/concepts.md` — no lecture content (`ps_c4` etc absent).
  - ALO: zero matches for "induction."
  - SO, RC, PA: unknown (probe rate-limited mid-test).
- Local repo has `tmp-secondbrain-scrape/` with ~90 PDFs across 5 subjects + RC (extracted by `tools/scrape-local-secondbrain.py`, NOT by the broken fiimaterials crawler).

**Phase A pipeline:**

```
tmp-secondbrain-scrape/<subject>/<kind>/<file>.pdf
        │
        ├─→ tools/pdf-to-md.py (NEW)
        │     - pypdf (pip-installable, verified working on Tema_A.pdf this session)
        │     - Extract per-page text
        │     - Run NFC normalize + Romanian glyph-fix table
        │       (˘a→ă, ¸s→ș, ¸t→ț, ˆ ı→î, ˘ A→Ă; µ→μ left as-is)
        │     - Drop hyphenation + line wraps that broke at column boundaries
        │     - Emit ./tmp-md/<subject>/<kind>/<file>.md with frontmatter
        │       (source_pdf, sha256, pages, extracted_at)
        │
        ├─→ tools/sync-corpus-to-vps.sh (NEW)
        │     - rsync ./tmp-md/ → root@VPS:/opt/jarvis/data/archival/_extras/
        │     - Preserve subject/kind/file structure
        │     - Idempotent (rsync --update flag)
        │
        └─→ ssh root@VPS "/opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin ingest-corpus"
              - Re-builds knowledge.jsonl from archival/
              - HybridRetriever's lexical pass (SearchSubsystem.searchIn) +
                semantic pass (VectorStore via embeddings) re-pick up new files
              - VectorStore embeddings re-computed via OpenRouter for new chunks
                (one-time cost; subsequent retrieval is in-memory)
```

**Design constraints:**
- Output `.md` not `.pdf` because the existing `_extras/POO/...` corpus is `.md` and HybridRetriever's lexical search assumes text files (PDFBox extraction would re-run on every retrieval otherwise).
- Glyph-fix table is small + targeted; avoid OCR (heavyweight + may degrade clean text). Marker-pdf or Mistral OCR can be a Slice 3+ enhancement if quality is poor.
- Romanian content kept verbatim. Do not translate; HybridRetriever can match Romanian queries against Romanian text.
- Idempotent: re-running the pipeline doesn't duplicate index entries (rsync skips unchanged files; `ingest-corpus` is sha256-deduped per the existing tool).

### 4.2 Phase B — sidekick citations

**Current state:**
- `JarvisToolset.chat(systemPrompt, userText)` is invoked by `/api/v1/sidekick/ask`.
- The system prompt template lives in `jarvis.tutor.SidekickContext` (per Slice 1 spec §C).
- HybridRetriever is called inside `JarvisToolset.chat` via existing search-subsystem wiring.
- LLM replies WITHOUT citing source filenames (verified via probe — replies say "I found a reference to X" without naming the source `.md`).

**Phase B changes:**
1. `SidekickContext.systemContext()` extends prompt with: "When you use information from the corpus, cite the source filename inline using the format `(src: <path>)` where `<path>` is the relative archival path. Do not invent filenames; only cite ones returned by the search tool."
2. `JarvisToolset.chat` wraps each retrieval result with the source path and includes that in the model's context window.
3. Frontend `Sidekick.tsx` parses `(src: <path>)` markers in the response and renders them as clickable pills that open the rail item or PDF drawer for that source.
4. Sidekick reply payload extended with optional `citations: [{path, snippet, score}]` field. Frontend renders citations as a strip below the reply.

**Schema additions (`ApiSidekickReply`):**
```kotlin
@Serializable
data class ApiSidekickReply(
    val text: String,
    val model: String,
    val quotedContext: String?,
    val citations: List<ApiCitation> = emptyList(),  // NEW
)

@Serializable
data class ApiCitation(
    val path: String,      // archival-relative path, e.g. "_extras/POO/courses/poo_c5.md"
    val snippet: String,   // 200-char excerpt
    val score: Double,
)
```

### 4.3 Phase C — UI polish

**C1 · LaTeX `<MathText>` wiring**
- `tutor-web/src/components/MathText.tsx` exists (KaTeX-based per Slice 1 Phase G).
- `Sidekick.tsx` currently renders sidekick reply via `<div style={{ whiteSpace: "pre-wrap" }}>{fetchState.text}</div>` (per BRIDGE.md:435).
- Replace with `<MathText text={fetchState.text} className="text-sm" />`.
- DrillCard body paragraphs (`tutor-web/src/components/DrillCard.tsx`) also need same treatment for `definition` / `worked` / `check` body text.

**C2 · `?debug=1` toggle**
- Read `URLSearchParams(window.location.search).get('debug') === '1'` once at App level.
- Pass `debug: boolean` prop down to header components.
- `App.tsx` header: when `!debug`, render `null` for `<DaemonHealthPill />` and `domain` footer text. Replace footer with plain "READY" (single word, no host).
- When `debug=1`, render full chrome as today.

**C3 · Rail expansion**
- **CONCEPT_REF auto-pop during `/reprep`:** after problem extraction, for each problem, run `HybridRetriever.search(problem.statement, k=3)` against the corpus. Top-3 retrieved doc paths get added to `task.conceptRefs` (existing column, currently empty `[]` for live tasks). `RailJsonBuilder.buildForTask` already reads `task.conceptRefs` and renders CONCEPT items per Slice 1 spec — verify current behavior, fix if it doesn't render them.
- **PRIOR_GAP rail items:** when `RailJsonBuilder.buildForTask` runs, also query `KnowledgeGapRepo.listForTask(userId, taskId)` for unresolved gaps. For each, emit a rail item with `type: "PRIOR_GAP"`, `label: gap.topic`, `payload: { gapId: gap.id }`. Frontend `ResourceRail.tsx` adds a PRIOR_GAP item type that on click opens a drawer rendering the existing `KnowledgeGapCard` adapter for that gap (read-only — no resolve/dismiss UI inside the rail drawer; that lives elsewhere).

## 5. Data model changes

| Table / file | Change | Phase |
|---|---|---|
| `task_prep` | None (pdfTextRaw column DROPPED from earlier proposals) | — |
| `tasks.concept_refs_json` | Populated for the first time via Phase C3 (`/reprep` writes top-3 retrieved doc paths) | C |
| `archival/_extras/<subject>/...` | Bulk-populated with ~90 new `.md` files from Phase A | A |
| `knowledge.jsonl` | Re-built via `ingest-corpus` after Phase A scp | A |
| `ApiSidekickReply` (DTO) | Add optional `citations: List<ApiCitation>` field | B |

## 6. API changes

| Endpoint | Change | Phase |
|---|---|---|
| `POST /api/v1/sidekick/ask` | Reply payload extended with `citations: []` | B |
| `POST /api/v1/task/{id}/reprep` | After problem extraction, run HybridRetriever.search per problem; populate `task.conceptRefs` (currently writes empty); `RailJsonBuilder` re-runs over the new conceptRefs | C |
| New: `tools/pdf-to-md.py` | Local CLI (not HTTP) | A |
| New: `tools/sync-corpus-to-vps.sh` | Local CLI invoking rsync + ssh | A |

No new HTTP routes. Admin upsert deliberately omitted (SSH SQL covers the case).

## 7. Frontend components

| Component | Change | Phase |
|---|---|---|
| `tutor-web/src/components/Sidekick.tsx` | Replace pre-wrap div with `<MathText>`; render `citations[]` strip below reply | C1, B |
| `tutor-web/src/components/DrillCard.tsx` | Body paragraphs through `<MathText>` | C1 |
| `tutor-web/src/components/DaemonHealthPill.tsx` | Wrap render in `if (debug) ... else null` | C2 |
| `tutor-web/src/App.tsx` | Read `?debug=1` query flag; pass `debug` prop down; replace domain footer with "READY" when `!debug` | C2 |
| `tutor-web/src/components/ResourceRail.tsx` | New item type `PRIOR_GAP` (renders gap topic + danger color); click opens KnowledgeGapCard drawer | C3 |
| New: `tutor-web/src/components/CitationPill.tsx` | Renders single `(src: <path>)` citation as clickable pill that triggers rail drawer | B |

## 8. Backend changes

| File | Change | Phase |
|---|---|---|
| `src/main/kotlin/jarvis/tutor/SidekickContext.kt` | System prompt extension instructing LLM to cite | B |
| `src/main/kotlin/jarvis/tutor/JarvisToolset.kt` | Wrap retrieval results with source path; pass through to LLM context | B |
| `src/main/kotlin/jarvis/web/TutorRoutes.kt` (sidekick endpoint) | Reply DTO updated to include `citations[]` | B |
| `src/main/kotlin/jarvis/web/TutorRoutes.kt` (`/reprep` endpoint) | After problem extraction, populate `task.conceptRefs` via HybridRetriever search per problem | C3 |
| `src/main/kotlin/jarvis/tutor/RailJsonBuilder.kt` | Add PRIOR_GAP item type; emit one per unresolved gap from `KnowledgeGapRepo.listForTask` | C3 |
| New: `tools/pdf-to-md.py` | PDF→Markdown converter with NFC + glyph-fix | A |
| New: `tools/sync-corpus-to-vps.sh` | rsync + ssh invocation | A |

## 9. Visual-presence acceptance criteria (post Slice 1 ghost-component lesson)

After deploy, the live URL `https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E` must paint these `data-testid` selectors AND pass interaction-smoke:

| `data-testid` | Visible on first paint? | Interaction-smoke |
|---|---|---|
| `sidekick-citations-strip` | After sidekick reply with retrieval citations | Click pill → opens rail drawer for that path; drawer 200 |
| `math-rendered` (KaTeX `.katex` class) | After Sidekick reply containing `$$...$$` | Reply paragraph renders rendered math, not raw `$$...$$` text |
| `daemon-health-pill` | NOT visible at default URL; visible at `?debug=1` | n/a |
| `domain-footer` | NOT visible at default URL; visible at `?debug=1` | Footer reads "READY" by default |
| `rail-item-CONCEPT_REF` | At least 1 visible after `/reprep` against a Tema_A.pdf-equivalent task | Click → opens ConceptDrawer (Slice 1 Phase 7) for that concept |
| `rail-item-PRIOR_GAP` | At least 1 visible if KnowledgeGapRepo has unresolved gaps for the task | Click → opens KnowledgeGapCard drawer; gap content visible |

**Playwright headless gate must:**
1. Assert all `data-testid` selectors above visible / hidden as specified.
2. Capture ZERO 4xx/5xx network responses on first paint of `/tutor/?taskId=...`.
3. Click each interactive element (PDF rail, scratchpad rail, CONCEPT_REF rail, PRIOR_GAP rail, sidekick chip on selection).
4. After each click, assert no on-screen text matches `/404|HTTP \d{3}|not found|error/i` AND no new 4xx/5xx network responses.

These gates derive from the workflow rules ratified after Slice 1 + 1.5 (interaction-smoke gate; component-reuse contract; underscore-dead-prop). See `docs/notes/2026-05-11-workflow-rules-snapshot.md`.

## 10. Hard rules (load-bearing)

1. **Alex never validates card QUALITY.** Cards in DB are author-trusted (Claude-curated when present). UI never asks Alex "is this card right?" — that's the domain expert's job.
2. **Alex MUST commit DRILL answers + receive feedback.** This is the testing effect (Roediger & Karpicke 2006; Dunlosky 2013 meta-review). The "no validation" rule applies to card quality, NOT to answer commitment.
3. **Per-task UI variance is acceptable when communicated.** Tasks without curated cards show "no cards · use PDF + sidekick" empty state. Tasks with cards show the existing 4-card stack (whether unlocked + reordered later in a Slice 3 polish or not).
4. **Sidekick replies cite source filename when from corpus retrieval.** Hallucinated filenames are forbidden; LLM is instructed to only cite paths returned by the search tool.
5. **No new HTTP route for card upsert.** SSH SQL upserts via Claude-in-chat sessions are the curation workflow; no front-end / no auth surface needed.
6. **No REPL in this slice.** Slice 3 will spec REPL with proper sandbox (systemd-run + DynamicUser + PrivateTmp + NoNewPrivileges + cgroups + env scrubbing). Pre-Slice-3 verification: `Rscript -e 'library(VGAM); library(ggplot2); png(tempfile()); plot(1:10); dev.off()'` on VPS must succeed.

## 11. Risks + mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Phase A converter loses Romanian fidelity (NFC + glyph-fix table doesn't cover all cases) | MEDIUM | Spot-check 5 random converted `.md` files manually; iterate the glyph-fix table; if irrecoverable for a subject, defer that subject's ingest to Slice 3 |
| `ingest-corpus` rebuild on VPS takes longer than expected (it re-embeds via OpenRouter; quota concern) | MEDIUM | Run during off-hours; if free-tier daily quota exhausted (saw rate-limit 429 today during sanity probe), skip semantic pass on first run, lexical-only retrieval still works |
| PRIOR_GAP rail items expose unresolved gaps that aren't actually relevant to the current task (over-inclusive) | LOW | `KnowledgeGapRepo.listForTask(userId, taskId)` already filters by taskId; only gaps explicitly bound to this task surface; safe |
| CONCEPT_REF auto-pop during `/reprep` adds latency to reprep call | LOW | HybridRetriever search is in-memory + fast; budget 1s per problem; cap at top-3 per problem |
| Sidekick citations LLM still hallucinates filenames | LOW-MEDIUM | Backend post-processing: strip any `(src: <path>)` where `<path>` not in retrieval result set; UI never renders pills for unverified citations |
| `?debug=1` toggle accidentally exposed in production share-link | LOW | Single-user app; toggle is harmless info-only (no credential exposure) |
| HybridRetriever returns POO content for PS query (cross-subject confusion) | MEDIUM | Add subject-scope hint to retrieval call: when the current task has a subject, pass `archivalSubdir = "_extras/<subject>/"` so search is scoped; full-corpus fallback only if 0 hits |
| Phase A converter takes longer than estimated | MEDIUM | Honest re-estimate budget = 2 days; if still over by EOD-2, defer SO+RC+PA subjects' ingest to a follow-up half-slice |

## 12. Dependencies + assumptions

- VPS access intact (`root@46.247.109.91`) — verified live this session.
- `jarvis-kotlin` CLI on VPS supports `ingest-corpus` subcommand — assumed per BRIDGE.md Phase 5 runbook entry.
- OpenRouter free-tier daily quota replenishes overnight — verified empirically (BRIDGE notes prior daily reset behavior).
- `HybridRetriever.search` is concurrency-safe under multiple `/reprep` calls — assumed per existing tests.
- KaTeX bundle (`tutor-web/src/components/MathText.tsx`) handles inline + display math correctly — verified by existing usage in `ChatPane.tsx`.
- `tmp-secondbrain-scrape/` PDFs are not encrypted + are extractable via PDFBox / pypdf — verified for Tema_A.pdf this session; assumed for the rest.

## 13. Slice-3 escalation list (defer, but flag)

These are out-of-scope for Slice 2 but should be the first decisions in Slice 3 brainstorm:

1. **REPL surface for compute subjects (PS R, POO C++, possibly Python for prototype work).** Requires systemd-run sandbox spec + Rscript env audit + plot pipeline (base64 PNG return).
2. **Per-subject workspace shapes** — proof scratchpad with LaTeX preview for ALO + theoretical PS + parts of POO; UML editor for POO design tasks; flowchart/Gantt for PA algorithm design + timing analysis. Avoid premature template proliferation; let dogfood drive the next 1-2 templates.
3. **Card auto-generation** — only if Slice 2 dogfood reveals Claude-in-chat curation isn't keeping up with task throughput. Then build with constrained decoding (`response_format=json_schema`) + Marker OCR pre-step + Khanmigo-style template-with-slot-fill (NOT free-form gen) + `verbatim_quote` substring-of-`pdfTextRaw` validator (where verified, not self-quoted).
4. **DrillStack unlock + reorder** — when curated cards exist, default order DEFINITION → WORKED → DRILL → CHECK with only CHECK locked. ~30min CSS work. Defer to Slice 3 because (a) requires Slice 2 to validate that Claude-curated cards are working, (b) better tested with multiple curated tasks than just Tema A.
5. **Card-quality flag/badge for auto-gen vs Claude-curated** — when LLM gen exists in Slice 4+, every card needs `(authored_by, authored_at, source: claude|llm-auto, last_verified_at)` provenance metadata + UI badge so Alex can distinguish.

---

## 14. Acceptance summary (one-screen)

Slice 2 ships when:
- Live URL `/tutor/?taskId=...` paints sidekick replies with rendered LaTeX + citation pills (when retrieval used).
- Sidekick can answer questions like "what does ps_c4 say about Laplace?" with a citation pointing to a specific archival path.
- Resource rail shows ≥1 CONCEPT_REF item + ≥1 PRIOR_GAP item (when applicable) on Tema A workspace.
- DAEMON DOWN badge + domain footer hidden by default; visible at `?debug=1`.
- All Playwright `data-testid` selectors above paint as specified; interaction-smoke passes (no 4xx/5xx; no error text after click).
- Corpus ingest verified by sample sidekick queries against each of the 5 subjects + RC returning at least one citation.

Bundle hash + green tests is NOT a ship gate. Live URL paint + interaction-smoke is.

---

End of spec.
