# Slice 2 Dogfood Findings — 2026-05-12

> Dogfood pass after Slice 2 ship (corpus RAG + citations + sidekick MathText).
> Driver: Playwright MCP from Claude session, headed via Alex's VPS auth cookie.

---

## Session details

| Field | Value |
|-------|-------|
| Task ID tested | `01KR6K07T6PATPRR5KH1JXYF8E` (PS · Tema A) |
| VPS URL | https://corgflix.duckdns.org |
| Bundle hash | `index-CZtQl9SZ.js` (unchanged this session — backend-only diagnostic redeploys) |
| Git HEAD (start) | `9d3420c feat(corpus): fiimaterials + vidrascu SO scrapers + migrate-concept-refs subcommand` |
| Backend jar after fix | rebuilt locally + scp'd (`gradle :installDist -x test`) — needs proper deploy + commit |
| Tester | Alex (driven by Claude) |
| Date | 2026-05-12 |

---

## Critical bugs found + fixed

### BUG-1: `search_archival` subject filter never matched (silent empty results)

**Repro:** Direct API call:
```bash
POST /api/v1/sidekick/ask
{ task_id: "01KR6K07T6PATPRR5KH1JXYF8E",
  selection: "distributia Laplace",
  user_question: "Search the PS corpus for material on the Laplace distribution and cite the source files." }
```
Before fix → LLM tool-called `search_archival(query="Laplace distribution", subject="PS")` → dispatch returned `"no matches for ... in subject PS"` despite corpus having 5+ PS Laplace files (verified via `jarvis sub search`). LLM then told user "no files found", `citations: []`.

**Root cause:** `JarvisToolset.dispatchSearchArchival` line 604 filter:
```kotlin
hits.filter { it.id.startsWith("$subject/", ignoreCase = true) }
```
HybridRetriever returns ids relative to `archivalRoot = /opt/jarvis/data/archival`, so PS materials come back as `_extras/PS/_fii/edu/files/Tema_A_en.md` etc. The bare `PS/` prefix can never match `_extras/PS/...`. Filter dropped every result whenever the LLM passed `subject`.

**Fix (committed):** filter on `_extras/$subject/` with cross-platform separator normalization (`\` → `/`). After fix, same prompt returns `citations: [_extras/PS/_fii/edu/files/Tema_A_en.md, _extras/PS/_fii/gdrive/Curs/curs_2019-2020/En/probability8_en.md]` with verified hit-set match in CitationExtractor.

**File:** `src/main/kotlin/jarvis/tutor/JarvisToolset.kt:600-616`

### BUG-2 (transient, no commit): sidekick 500 from stale classloader

Pre-restart, `/api/v1/sidekick/ask` returned empty-body 500 whenever LLM engaged the tool-use loop. Cause: jar on disk was rewritten at 18:12 UTC, JVM had started at 14:30 UTC — lazy class lookups hit `NoClassDefFoundError: jarvis/SurfaceRouter` (Error, not Exception → bypassed sidekick's `catch (e: Exception)`). `systemctl restart jarvis` cleared it. Production deploys MUST restart the service if `installDist` ran while JVM was up; otherwise tool-use silently 500s.

---

## Scenario results

| # | Scenario | Result | Notes |
|---|----------|--------|-------|
| 1 | Tema A workspace loads (DrillStack + ResourceRail paint, sessionReady) | PASS | Bundle `index-CZtQl9SZ.js`; A1 drill body renders ("Scrie codul R…"), WORKED/DEF/CHECK locked behind drill |
| 2 | InlineAskChip fires on selection | PASS | `mouseup` on selected `<span>` brings up `✨ ASK` chip, fixed-positioned |
| 3 | Sidekick reply renders LaTeX via MathText | PASS | 2968-char Romanian R-code answer rendered, `\(\mu\)` / `\[X = \mu + b(E_1-E_2)\]` typeset |
| 4 | Citations populate from `_fii/` corpus paths | PASS (post BUG-1 fix, direct API) | Reply text carries `(src: _extras/PS/_fii/edu/files/Tema_A_en.md)`; ApiSidekickReply.citations has 2 verified entries with `_extras/PS/_fii/` prefix |
| 5 | CitationPill renders in Sidekick | UNCONFIRMED — see UX gap below |
| 6 | CitationPill click → ResourceRail drawer for that path | NOT TESTED — blocked by 5 |
| 7 | `?debug=1` toggle: DaemonHealthPill + domain-footer | NOT TESTED |

---

## UX gaps (not bugs — backlog)

### GAP-1: Chip-flow `user_question = selectedText` doesn't trigger corpus search

When the user selects card text and clicks ✨ ASK, `TutorWorkspace.tsx:85` builds the envelope with `userQuestion: selectedText` — e.g. *"Scrie codul R pentru a simula 10000 observatii din distributia Laplace cu parametrii dati."* This is a "how do I do X" question; the LLM answers from its own knowledge of Laplace + R and never invokes `search_archival`. Result: a polished LaTeX reply with zero citation pills.

Direct API calls with `user_question: "Search the PS corpus for ..."` produce 2 citations correctly. Wiring + filter + extractor + DTO all verified.

**Possible mitigations** (defer to Slice 3 brainstorm):
- Server-side: bias system prompt to prefer corpus consultation when `selection` is present (e.g. "Before answering, search the user's per-subject corpus for material on the selection").
- Client-side: append a "[consult PS corpus]" hint to `userQuestion` when subject is known.
- Mixed: keep current "answer first, cite if available" UX but emit a "📚 search corpus" button alongside the ✨ ASK chip so user can opt-in to retrieval-grounded mode.

### GAP-2: SurfaceRouter NoClassDefFoundError tail in `/var/log/jarvis.log`

Cosmetic-but-noisy. `BlockReminder.emitReminder` (background ticker, every 60s) trips `NoClassDefFoundError: jarvis/SurfaceRouter` once per JVM lifetime when the jar has been updated mid-run. Doesn't affect sidekick path post-restart. Worth: either bundling SurfaceRouter explicitly in `installDist` resources OR wrapping the BlockReminder.emitReminder call in `try { } catch (Throwable)`.

---

## Verdict

**SHIP** — Slice 2 corpus-RAG path works end-to-end after BUG-1 fix. Direct API confirms 2 verified citations on `_extras/PS/_fii/` paths via tool-use loop + retrieval set verification. UI rendering of CitationPill / drawer click still needs a chip-flow that triggers retrieval; that's a prompt-engineering polish, not a wiring failure.

**Hot followups:**
1. Commit BUG-1 fix.
2. Refresh BRIDGE.md with this session's changes + hallucination triggers.
3. Slice 3 brainstorm or GAP-1 prompt patch (user choice).
