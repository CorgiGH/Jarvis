# Tutor + life-OS overhaul — 8-phase design

**Date:** 2026-05-10
**Status:** Spec — pending review
**Origin:** Brainstorming session 2026-05-09 → 2026-05-10. Five parallel review agents (SO UX extractor, tutor visual auditor, live Playwright tester, layout-chain code reviewer, TaskQuickStart dedup analyzer) returned with concrete findings; user picked layered approach (C) + scope expansion to include task autonomy and corpus auto-population.
**Backlog file:** `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` (created in Phase 1; appended to during gates).

## Goals

1. Personal-first AI tutor + life-OS that survives daily dogfood. Single user (you), with friend-share possible later via BYOK.
2. Both tutor (priority for finals) and life-OS (legacy `/` chat surface) viable. Each must remain usable while the other evolves.
3. Mobile + desktop both first-class. Mobile is not "best-effort" — it must work.
4. UX held to general production/industry standard per `C:/Users/User/Desktop/SO/os-study-guide/wiki/architecture/UX Playbook.md` (30+ cited research-backed principles). Visual identity stays brutalist-yellow (do NOT copy SO's slate/stone/ocean themes).
5. Self-updating: system observes inbound feeds (course pages, fiimaterials, eventually calendar/email) and registers tasks itself. User works on what's there, not on what they remembered to type into a form.

## Scope (what is and isn't in this spec)

**In scope** — everything below.
**Out of scope (acknowledged, deferred):** Telegram bot daemon producer (needs your bot token), gws OAuth (needs interactive `gws auth login`), daemon PC-boot autostart (admin perms), public release.
**Abandoned:** none currently. (`core_memory.md` PII scrub previously declined 3+ times — un-abandoned in Phase 3.)

## Architecture overview

The tutor has three concentric layers today (Layer A schema · B0 sensor+effector · B1 daemon-or-clipboard) plus task-context V0/V1. This overhaul keeps that core and bolts on:

- **Design tokens + UX foundation** (Phase 2) — every later phase inherits semantic CSS tokens, accessible focus states, motion guards, and touch targets.
- **Server-side knowledge persistence** (Phase 4) — the gap envelope that has been client-only since Layer B0 finally lands in `tutor_gaps` table.
- **Corpus expansion + auto-attachment** (Phases 5+6) — the corpus grows beyond `_extras/<subject>/` via Playwright crawls, and tasks pull their own attached materials at registration time.
- **Task autonomy** (Phase 6) — TaskDetector abstraction with pluggable sources. Replaces the manual preset-button workflow with feed-driven detection. Course-page scrapers slot in when URLs are provided.
- **Knowledge-aware surfacing** (Phase 7) — inline reference popups gated by `knowledge.jsonl` + FSRS confidence. Symbolic math tool (sympy) for verifiable answers. Cross-task gap reuse + Knowledge Ledger drawer.

## Per-phase gate model

Every phase ends with the same 4-gate sequence before Phase N+1 starts:

1. **Code gate.** Implementation lands as one or more commits. `gradle :test` + `npm test` green. CI green on push.
2. **Live gate.** Deploy via `bash tools/deploy.sh`. `curl https://corgflix.duckdns.org/healthz` returns `ok`. Bundle hash matches local build.
3. **Playwright gate.** Spawn a focused test agent with the Playwright MCP tools and the phase's surfaces. Re-run a scripted scenario covering the bugs the phase claimed to fix. Pass = no reproduction + no new regressions in features outside the phase. Test agent has a written checklist; result is structured pass/fail per scenario.
4. **UX-Playbook gate.** Spawn an audit agent. Inputs: the SO `wiki/architecture/UX Playbook.md` + `wiki/architecture/Design Principles.md` (cross-domain arbiter) + the list of files changed in this phase. Audit ONLY the surfaces touched. Output structure:
    ```
    [category] [principle] [pass|fail|n/a] [severity high|med|low] [finding] [recommended action]
    ```
    Failures with severity HIGH block phase advancement. MED/LOW go to the backlog file.

If any gate fails: fix in-place, re-run that gate. Don't advance.

---

## Phase 1 — Critical bugs (~2h, 1-2 commits)

**Goal:** Tutor is usable again. Three production-blocking bugs go away.

### 1.1 Scroll fix
- `tutor-web/src/components/TutorWorkspace.tsx:32` — replace `<div className="grid grid-cols-2 h-full min-h-0 flex-1">` with `<div className="flex h-full min-h-0 flex-1 flex-col sm:flex-row">`.
- Each child column gets `flex-1 min-w-0 min-h-0 sm:w-1/2`.
- `tutor-web/src/components/ChatPane.tsx:89` add `min-w-0` to root: `className="h-full flex flex-col bg-white font-mono min-w-0"`.
- `tutor-web/src/components/ChatPane.tsx:101` change `overflow-y-auto overflow-x-hidden` to `overflow-auto` so wide KaTeX/long replies scroll within the column.

### 1.2 Mobile reflow
The `flex-col sm:flex-row` from 1.1 stacks PDF on top of chat below sm breakpoint. Sidebar already `hidden sm:flex` so mobile = stacked single-column. Reconciliation per user choice (a): keep desktop 2-col (PDF + chat), mobile drops/stacks PDF.

### 1.3 Server-side task idempotency (band-aid; replaced in Phase 6)
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` POST `/api/v1/tasks` — before INSERT, query `taskRepo.listForUser(userId).firstOrNull { it.subject == subject && it.title == title }`. If found → 200 with existing task view. Else → INSERT + 201.
- Frontend (`TaskQuickStart.tsx`) unchanged — both 200 and 201 are `r.ok` → existing navigate path works.

### Tests
- New backend: `TasksRouteIdempotentTest` — POST same payload twice, assert second returns 200 with same id.
- New frontend: `TutorWorkspace.scroll.test.tsx` — render with long mocked reply, assert inner scroll container has `scrollHeight > clientHeight`.

### Gates
All 4. Playwright agent specifically reproduces today's failure modes (long laplace reply scroll, 3× preset click) and confirms fix.

---

## Phase 2 — UX foundation (~3-4h, 2-3 commits)

**Goal:** Tokenize the design system, harden a11y, set the substrate every later phase inherits. Visual identity stays brutalist-yellow; no SO theme copy.

### 2.1 Design tokens (`tutor-web/src/index.css` + Tailwind v4 `@theme` block)
Replace hardcoded `bg-yellow-300`, `text-black`, etc. with CSS vars in `:root`:
```
--bg-page: #ffffff
--fg-text: #000000
--bg-panel-dark: #000000
--fg-panel-dark: #fde047  /* yellow-300 */
--accent-yellow: #fde047
--accent-yellow-hover: #facc15  /* yellow-400 */
--border-strong: #000000
--border-thin: rgb(0 0 0 / 0.2)
--ring-focus: #facc15
```
Tailwind v4 `@theme` exposes them as utilities (e.g. `bg-[var(--bg-page)]` or via shortname mapping). Components migrate to tokens. Future light/dark mode = swap the `:root` block; no component churn needed.

### 2.2 Typography scale
Per wiki [[Typography & Readability]]: ≥16px body, 1.5 line-height, 50-75ch line length.
```
--type-sm: 12px
--type-body: clamp(14px, 13.5px + 0.1vw, 16px)
--type-lg: 18px
--type-h2: 20px
```
Body line-height 1.5; mono code 1.6; headings 1.3. Cap chat reply prose at `60ch` max-width.

### 2.3 Focus + motion + touch
- Global `*:focus-visible { outline: 2px solid var(--ring-focus); outline-offset: 2px }` in `index.css`.
- `@media (prefers-reduced-motion: reduce) { *, *::before, *::after { transition-duration: 0ms !important; animation-duration: 0ms !important } }`.
- Touch-target audit per wiki [[Touch Target Sizing]] (44×44px minimum on mobile): sidebar task buttons `py-3 sm:py-1.5`; chip pills `py-2 sm:py-1`; header × close `p-2`.

### 2.4 Accessibility
- ARIA labels on icon-only buttons (× close, screenshot capture button).
- ScreenshotCapture: emoji wrapped in `<span aria-hidden>📷</span><span>Capture</span>`.
- `aria-current="page"` on active nav link.
- Sidebar: `<aside>` → `<nav>` with `<ul role="list">`.
- Empty states (Sidebar empty, no-tasks page) get visible "what to do next" copy per wiki [[Empty States]].

### 2.5 Visible-state coverage
Per wiki [[State Visibility]]: every button/form has loading state (spinner after 400ms per wiki [[Feedback & Response Time]]), every fetch has error state, every list has empty state, every success has confirmation. Audit + formalize gaps.

### Tests
- Vitest snapshot of computed `--type-body` on test viewport.
- `@axe-core/playwright` a11y scan on `/tutor/` workspace and `/tasks` page (zero AA violations).
- Manual focus-tab traversal test recorded as a Playwright scenario.

### Gates
All 4. UX-Playbook agent runs full Visual Polish + Responsive & Accessible columns. Block on any WCAG AA violation.

---

## Phase 3 — Hygiene cleanup (~3h, 3-4 grouped commits)

**Goal:** Eliminate the 12 small bugs the agents found. Wire card actions to backend. Add core_memory PII scrub. Add scratchpad server-persist (foundation for cross-device sync in Phase 4).

### 3.1 ChatPane robustness (1 commit)
- `AbortController` on `/api/chat` fetch. `useRef(new AbortController())` + cleanup on unmount. Prevents `setSending(false)` after teardown.
- ScreenshotCapture stale closure: add `taskId` to hotkey effect deps array.
- ensureTutorSession race: guard with `useRef<boolean>(false)` so concurrent App mounts/strict-mode double-renders don't double-bootstrap.

### 3.2 Card actions wired to backend (1 commit, frontend + backend)
- POST `/api/v1/gap/{id}/status` (route + repo extension; full repo lands Phase 4). KnowledgeGapCard `onResolve(USER_TYPED|USER_DISMISSED|USER_MARKED_DONE)` POSTs.
- POST `/api/v1/edit/{id}/status` for SuggestedEdit REJECT/APPLIED.
- Cards optimistically update local state, confirm on response. Failure → inline error.
- Tests: backend route tests (200 ok + 404 missing); frontend test for resolve click → server call → status badge update.

### 3.3 Form ergonomics (1 commit)
- Enter-to-submit on TasksScreen + TrustSettings inputs.
- Card "show more" toggle when truncated text > 240/320 chars per wiki [[Progressive Disclosure]].
- PdfPane uses `jarvisFetch` not raw `fetch` (CSRF token attached).

### 3.4 Scratchpad server-persist (1 commit, frontend + backend)
Foundation for Phase 4 cross-device sync.
- Backend: `tasks` table gets `scratchpad TEXT NULL` column (migration). PUT `/api/v1/tasks/{id}/scratchpad` (debounced server-side write); GET returns current.
- Frontend: TutorWorkspace's scratchpad state lifts to fetch on task mount; debounced PUT (500ms) on local change. localStorage stays as offline-write cache.

### 3.5 core_memory.md PII scrub (1 commit)
- Edit `/opt/jarvis/data/core_memory.md` directly: remove the matricol token. Verify CoreMemory.scanTextForPii returns empty after.
- Documented in commit message: "Removed matricol per user instruction. PII warning should clear on next chat turn."

### 3.6 Sidebar polish (small, fold into 3.1)
- `useEffect` dep `[activeTaskId]` → `[]` (run once). Add explicit refresh trigger via window event when a task is created so sidebar updates.
- Scratchpad: replace fixed `h-32` with `min-h-[6rem] max-h-[40vh] resize-y`.

### Gates
All 4. UX-Playbook agent focuses on Interaction & Feedback + Error Prevention & Recovery columns.

---

## Phase 4 — Layer B §4 close + cross-device sync (~4h, 3 commits)

**Goal:** Server-side gap persistence (gaps survive page reload). SHOW DOCS gap-card action. PDF-line "I don't know this" button (closes Layer B §4 item 6 fully). Cross-device session sync.

### 4.1 Server-side KnowledgeGap persistence
- DB: `tutor_gaps` table (id PK, user_id, task_id nullable, topic, language, type, trigger, content, example_code, source_citation, resolved_by nullable, created_at, updated_at, reused_count int default 0). Migration via existing tutor SQLite schema-version pattern.
- Repo: `GapRepo.kt` — `insert`, `findById`, `listForUser`, `listForTask`, `markResolved(id, by)`, `incrementReused(id)`.
- Routes: POST `/api/v1/gap` (idempotent on `(user_id, task_id, topic)` triple), POST `/api/v1/gap/{id}/status` (finalizes 3.2's drafted route), GET `/api/v1/gaps?taskId=`.
- ChatPane: when `parseKnowledgeGaps` extracts an envelope, immediately POST. On reload, GET historical gaps for the active task and merge above the live conversation.

### 4.2 SHOW DOCS gap-card action
- POST `/api/v1/gap/{id}/search-docs` calls `HybridRetriever.search(topic, k=3)` + `KnowledgeGraphQuery.query(topic, k=3)`, returns up to 6 results (filename + snippet + line ref).
- Frontend renders results inline below the card (collapsible, default-collapsed per [[Progressive Disclosure]]).

### 4.3 PDF-line "I don't know this" button
- Replace `<embed>` in PdfPane with `react-pdf` (pdf.js renderer with DOM-accessible TextLayer).
- Listen to `selectionchange` on the text layer. When user highlights ≥3 chars, show a floating tooltip "🤷 I don't know this" near selection.
- Click → emit a `<gap>` envelope with `topic = selection.text.slice(0, 200)`, `trigger = "EXPLICIT_ASK"`, source citation `{file, page, line approx}`. Sent through the regular chat path so the LLM sees it as a user question with attached selection context.
- Bundle cost: ~200KB. Acceptable.

### 4.4 Cross-device session sync
The chat history is already server-side via `/opt/jarvis/data/conversations.jsonl`. Scratchpad is server-side after 3.4. Active-task continuation already works via `/?taskId=...` URL.
- Add: `localStorage.lastTaskId` syncs to a server-side cookie (`jarvis_last_task`) on each task switch. App.tsx prefers the server cookie when present, falls back to localStorage. So opening `/tutor/` on a new device resumes the same task.
- Tests: open task on device A → switch to device B → land on the same workspace.

### Tests
- Backend: `GapRepoTest` (insert, dedup on triple, status update); `GapsRouteTest` (POST + GET round-trip, resolve, search-docs).
- Frontend: gap envelope persists across reload (RTL + msw); SHOW DOCS click renders results; selection in PdfPane fires gap-create flow.
- Cross-device: cookie roundtrip test.

### Gates
All 4. UX-Playbook agent focuses on State Visibility (gap card lifecycle visible) + Direct Manipulation (selection → tooltip → action chain feels immediate).

---

## Phase 5 — Corpus expansion (~4h, 2-3 commits)

**Goal:** Grow `_extras/<subject>/` beyond what's manually in repo. Crawl fiimaterials.web.app + (eventually) per-faculty course pages. Re-runnable. Outputs feed into Phase 6's auto-corpus attachment.

### 5.1 Playwright crawler — fiimaterials.web.app
- New tool: `tools/extract-fiimaterials.ts` (Node script using `playwright` directly).
- Crawl strategy: site-map via DOM walk. Each page that links to a PDF: download, hash (sha256 → dedup), classify by URL/breadcrumb into subject + kind (course / seminar / lab / hw / exam / other), drop into `/opt/jarvis/data/archival/_extras/<subject>/extracted/<hash>/<filename>.pdf` with sidecar `meta.json` (source URL, fetch date, crawl-run-id).
- Re-runnable: subsequent runs skip already-downloaded hashes; only fetch new content.
- Best-effort on access-controlled materials (log, skip). User can re-run later with manual cookies.

### 5.2 KnowledgeGraph reingest
- After 5.1, run `gradle ingestCorpus` on VPS to rebuild graph including new `_extras/<subject>/extracted/`.
- Verify node count grew + edges connect to new files.

### 5.3 Course-page scraper scaffolding (deferred sources)
- Define `CourseScraper` Kotlin interface in `jarvis.tutor.taskdetect` package (cron runs server-side via CronRunner, so Kotlin is the right home).
- One reference impl per existing subject lands when URLs are provided. Until then: stub interface + `ManualEntryScraper` placeholder so Phase 6 can be built atop the abstraction.
- Document the URL slots in `.env.example`: `JARVIS_COURSE_PAGE_PA`, `_PS`, `_POO`, `_ALO`, `_SO_RC`. User fills when known.

### Tests
- Crawler: dry-run mode that lists URLs that would be fetched, with expected dedup behavior on second run.
- KnowledgeGraph: assert reingest grows node count.

### Gates
All 4. UX-Playbook agent focused on State Visibility (crawl progress + result) + Error Prevention (don't break existing corpus).

---

## Phase 6 — Task autonomy (~6h, 4-5 commits)

**Goal:** Tasks ARE what the system knows is due, not what the user typed. Replace the preset-button workflow with feed-driven detection + auto-corpus + active-task dashboard.

### 6.1 TaskDetector abstraction
- New `jarvis.tutor.TaskDetector` Kotlin interface:
    ```kotlin
    interface TaskDetector {
        val sourceId: String
        suspend fun discover(): List<DetectedTask>
    }
    data class DetectedTask(
        val sourceId: String,
        val externalId: String,  // stable ID per source (URL, etc) for dedup
        val subject: String,
        val title: String,
        val deadline: Instant,
        val problemPath: String?,  // local archival path if available
        val sourceUrl: String?,
        val rawMetadata: Map<String, String>,
    )
    ```
- Repo: `DetectedTaskRepo` — track `(sourceId, externalId)` → existing taskId mapping so re-runs upsert rather than dup.

### 6.2 Source impls
- `ManualSource` — reads from `/tasks` page (fallback). User can still create tasks manually if a feed misses something.
- `FiimaterialsSource` — uses 5.1's fetched metadata to infer tasks (e.g. "PS Tema A" from past-year filenames + manual heuristics, OR explicit "current homework" listings if found).
- `CoursePageSource<T : CourseScraper>` — generic over a scraper. Inactive until URLs land.

### 6.3 Auto-corpus attachment
- At task INSERT (manual or auto-detected), query `HybridRetriever.search(task.title, k=5, subject=task.subject)` + `KnowledgeGraphQuery.query(task.title, k=5)`. Merge results, dedupe by file path, keep top 5 distinct paths. Store as `task.materialPaths: List<String>`.
- DB: extend `tasks` table with `material_paths TEXT` (JSON array of relative paths under archival root). Migration script.
- API: `GET /api/v1/tasks/{id}` returns `materialPaths`. Frontend renders them as a "Reference materials" rail in the task workspace (clickable; opens that PDF in PdfPane via the existing per-task PDF route, repurposed to accept `?materialPath=`).

### 6.4 Cron-driven re-scrape
- Reuse existing CronRunner (Stage F). Drop `~/.jarvis/skills/task-detect/SKILL.md` with `cron_minutes: 60` + `cron_enabled: true`.
- Cron handler: `for source in sources: source.discover() → diff → upsert into TasksTable + DetectedTaskRepo`.
- Set `JARVIS_CRON_ENABLED=1` in `/opt/jarvis/.env` after testing manually.

### 6.5 Frontend overhaul: replace TaskQuickStart with active-task dashboard
- Delete: preset POST flow + preset button row.
- New `ActiveTaskDashboard` component renders the existing tasks list, ranked by composite score `(deadline_urgency × 0.5) + (weight × 0.2) + (readiness × 0.3)` where each is normalized 0..1:
    - `deadline_urgency`: `max(0, 1 − days_remaining / 14)` (saturates at 14 days out).
    - `weight`: per-kind constant — exam=1.0, tema=0.8, lab=0.5, seminar=0.3, other=0.4. Stored on task or derived from title.
    - `readiness`: `1 − avg(KnowledgeRepo.confidence(c))` over task's required concepts.
- Required concepts = nouns + technical tokens extracted from `task.title + task.body` (via existing tokenization in HybridRetriever) intersected with the KnowledgeGraph node set. Falls back to subject-level confidence if no concept overlap.
- Each row shows: subject · title · days-until-deadline · readiness bar (3 dots filled out of 3) · "open" button.
- "+ Manual entry" button stays as fallback (opens existing TasksScreen modal).

### 6.6 Schedule + assignments integration
- `assignments.jsonl` entries become readable by TaskDetector (existing source). Existing manual-entry path preserved.
- `schedule.json` blocks consult task list when planning study time.

### Tests
- `DetectedTaskRepoTest`: upsert dedup, no-double-insert on re-discover.
- `FiimaterialsSourceTest`: parses sample metadata correctly.
- Auto-corpus: `materialPaths` populated on insert; resolves to existing files.
- Frontend: ActiveTaskDashboard renders + ranks correctly; manual-entry fallback still works.
- E2E: spawn Playwright, simulate cron run, verify new tasks appear in dashboard.

### Gates
All 4. UX-Playbook agent focused on Recognition Over Recall (visible options, no need to remember to "add task") + Hick's Law (≤7 visible options per decision) + Goal Gradient + Zeigarnik (incomplete tasks visible drive return).

---

## Phase 7 — Layer C: knowledge-aware tutor (~7h, 4-5 commits)

**Goal:** Tutor surfaces exactly the help the user needs based on their knowledge state. Inline reference popups gated by FSRS confidence. Knowledge Ledger drawer. Cross-task gap reuse. Implicit gap detection. Sympy symbolic-math tool. 5-layer prompt-injection defense.

### 7.1 Inline reference popups
- When LLM emits a term wrapped in `<concept>name</concept>` (new envelope), frontend renders it as an underlined inline link.
- Click → opens a side drawer with the concept's wiki entry (from Karpathy Wiki, Stage D) + relevant passages from the corpus (HybridRetriever lookup, k=3).
- Gating threshold: env-tunable via `JARVIS_CONCEPT_LINK_CONFIDENCE_THRESHOLD` (default 0.7). If `KnowledgeRepo.confidence(concept) > threshold`, render concept as plain text (no underline). Surface affordance ONLY for weak/unknown concepts. Per wiki [[Progressive Disclosure]] + [[Recognition Over Recall]].

### 7.2 Knowledge Ledger drawer
- New side drawer (slide-in from right edge) listing: every gap ever recorded for current user, status badges, last-seen date, reuse count, "open in chat" action.
- Filter: by subject, by status, by recency.
- Per wiki [[Recognition Over Recall]] + [[F-Pattern]]: scannable list, dominant column = topic title.

### 7.3 Cross-task gap reuse
- When a new gap envelope arrives, check `GapRepo.findSimilar(topic, k=5)`. Similarity = max(token-overlap-jaccard, embedding-cosine). Threshold env-tunable via `JARVIS_GAP_SIMILARITY_THRESHOLD` (default 0.75). If a similar gap exists above threshold, increment its `reused_count` instead of creating new; otherwise insert.
- Retrieval boost: in HybridRetriever, gaps with `reused_count ≥ 3` get a multiplicative score bonus (default ×1.3, env-tunable `JARVIS_GAP_REUSE_BOOST`). The system "remembers" what the user has needed before.

### 7.4 Implicit gap detection
- Heuristic on user message in JarvisToolset.chat: if user msg matches `/i don't (get|know|understand)|what (is|does)|how do/`, infer an implicit gap with topic = extracted noun phrase. Auto-POST to gap repo without requiring the LLM to emit `<gap>` envelope.

### 7.5 FSRS card promotion from gaps
- Existing FSRS deck system (`knowledge_fsrs.jsonl`) gains gap-derived cards. When a gap reaches `reused_count ≥ 3`, promote to FSRS deck for spaced retrieval. User reviews in existing FSRS UI.

### 7.6 sympy symbolic-math tool
- New `JarvisToolDefs.symbolic_math(expression)` that subprocesses sympy via `python3 -c "from sympy import *; print(simplify(parse_expr('...')))"`. Returns LaTeX-rendered result.
- LLM uses for: integration / differentiation / limits / linear algebra / equation solving / probability density manipulation.
- Sandbox: subprocess with timeout + stdout cap. No filesystem / network access.
- Adds `python3 -m pip install sympy` to VPS bootstrap.

### 7.7 5-layer prompt-injection defense
Currently ~2 layers (system preamble + retrieved_context envelope wrapping). Add:
- Layer 3: input sanitization on user msg (strip role-marker tokens like "Assistant:", `<|im_start|>`, `<|system|>`, etc. via regex; replace with neutral text). Logged when triggered.
- Layer 4: audit `JarvisToolDefs.dispatch` — every tool that returns user-influenced strings (search_archival, query_graph, get_node, get_neighbors, wiki_read, search_subject_corpus) wraps results in `PromptInjectionScrubber.wrap` before returning. Add unit test asserting the scrubber is called in each branch.
- Layer 5: post-hoc check in `JarvisToolset.chat` — if reply contains an effector tool call (clipboard / apply_edit / run / calendar_create / gmail_create / drive_search) AND no matching user-side trust grant active, demote to read-only mode + emit a refusal note. Read-only state already plumbed via Layer B0.

### Tests
- Inline references: knowledge confidence threshold gate, click → drawer render.
- Ledger drawer: filter behavior, sort by recency, "open in chat" navigation.
- Cross-task reuse: similar gap upsert; retrieval boost test (rank delta).
- Implicit detect: regex matches expected phrasings, doesn't over-trigger on definitions ("what is a deadline" still answers, doesn't infer gap).
- FSRS promotion: reused_count ≥ 3 → promotion; FSRS review surfaces it.
- sympy: subprocess returns expected LaTeX for a battery of test exprs; timeout enforced.
- Injection defense: red-team test cases.

### Gates
All 4. UX-Playbook agent focused on Progressive Disclosure (only weak concepts get inline-link affordance) + Direct Manipulation (click → drawer feels immediate) + Recognition Over Recall (Ledger surfaces past work).

---

## Phase 8 — Layer D + ops + final audit (~3h, 2-3 commits)

**Goal:** Mockup-gap step 4 closure. Operational hygiene. Final UX-Playbook full-pass eval.

### 8.1 Plotly inline plot rendering
- Detect fenced ```` ```plotly ```` code blocks in chat replies.
- **Dynamic-import** Plotly only when first plot is detected — keeps base bundle <600KB. Plotly is ~3MB; fetched once, then cached.
- Render via `react-plotly.js` lazy-loaded.
- Update CHAT_SYSTEM_PROMPT teaching LLM the fenced-block syntax.
- Bundle cost: 0KB until first plot. ~3MB on demand.

### 8.2 Cron install for fallback-model probe
- Edit `/etc/cron.d/jarvis-probe` on VPS:
    ```
    30 6 * * 1 root /opt/jarvis/jarvis-kotlin/tools/probe-fallback-models.sh >> /var/log/jarvis-fallback-probe.log 2>&1
    ```
- One ssh + write. Documented in deploy.sh comments + README.

### 8.3 Final UX-Playbook full-pass
- Run the COMPLETE checklist (all 6 categories, all surfaces) against the deployed surface.
- Output: backlog file populated with leftover findings, plus a "polish complete" summary.
- Backlog triage: HIGH severity → spawn Phase 9 (out of scope here); MED/LOW → file for next overhaul.

### Tests
- Vitest + Playwright snapshot of plotly-rendered cell.
- Cron install verified by `systemctl status cron` + `tail /var/log/jarvis-fallback-probe.log` after first run.

### Gates
All 4. Phase 8's UX-Playbook gate IS the final-audit pass — covers all surfaces touched in Phases 1-8.

---

## Open items / deferred

- **Subject course-page URLs** — user provides later. Phase 5.3 scaffolds the abstraction; Phase 6.2 plugs them in when supplied.
- **Telegram bot daemon producer** — needs your bot token from @BotFather. Gateway endpoint + HMAC ready (Stage F).
- **gws OAuth** — needs interactive `gws auth login` on VPS. Tools wired (Stage E).
- **Daemon PC-boot autostart** — Windows Task Scheduler entry; admin perms required.
- **Public release / multi-user** — out of scope. BYOK design for friend-share documented if/when relevant.

## Backlog file

Created at Phase 1, lives at `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md`. Each phase's UX-Playbook gate appends MED/LOW findings. Phase 8's final audit rolls them up.

## Test surface summary

| Phase | Backend new tests | Frontend new tests | E2E |
|---|---|---|---|
| 1 | TasksRouteIdempotentTest | TutorWorkspace.scroll | scroll + dup-task + mobile reflow |
| 2 | — | typography + axe a11y | focus traversal |
| 3 | gap/edit status routes | card-action click→server | — |
| 4 | GapRepoTest, GapsRouteTest | gap-persist roundtrip, PdfPane selection | cross-device sync |
| 5 | crawler dry-run | — | reingest count grows |
| 6 | DetectedTaskRepo, FiimaterialsSource, auto-corpus | ActiveTaskDashboard rank+render | cron simulation |
| 7 | gap-similarity, sympy subprocess, injection defense | inline-ref drawer, Ledger filter, FSRS promotion | red-team injection battery |
| 8 | cron install verify | plotly snapshot | full UX-Playbook pass |

## Acceptance criteria (overall)

- [ ] All 8 phases ship + each phase's 4 gates pass.
- [ ] Tutor surface usable on phone + desktop (per Phase 1+2).
- [ ] Tasks self-populate from feeds + auto-attach corpus (per Phase 6).
- [ ] Knowledge state gates inline references (per Phase 7).
- [ ] Backlog file documents leftover MED/LOW findings; HIGH findings all resolved before phase advancement.
- [ ] Live deploy serves all surfaces; CI green; ≥800 tests across 3 toolchains.
- [ ] User confirms tutor + life-OS still both viable end-to-end.
