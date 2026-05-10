# Tutor Workspace Wiring · Slice 1.5 Design (2026-05-11)

> **Status:** spec approved by user 2026-05-11; pending plan + SDD execution.
> **Origin:** post-mortem at `docs/notes/2026-05-11-slice1-postmortem.md` identified ghost-component anti-pattern. Slice 1 (44 tasks, tag `slice1-tutor-drill-workspace` @ `b058fd8`) shipped `DrillStack` / `ProblemStepper` / `DrillCard` / `ProgressStrip` / `CompileSubmitCard` as bundled-but-never-mounted components. `TutorWorkspace.tsx` was never modified to render them.
> **For implementer:** see writing-plans handoff at bottom.

## Goal (one sentence)

Replace `TutorWorkspace.tsx`'s JSX so the Slice 1 drill-stack components actually paint at `https://corgflix.duckdns.org/tutor/?taskId=<id>`, add the missing `POST /api/v1/tasks/{id}/submit` route that Slice 1 D6 left unbacked, build the `ResourceRail` component that surfaces PDF + Scratchpad + concept refs as drawers, and bake post-mortem workflow fixes (paired build+mount tasks; Playwright visual gate; spec-level `data-testid` acceptance) into the plan + final review.

## What's in scope (Slice 1.5)

1. **Workspace render swap.** `TutorWorkspace.tsx` `return` JSX rewritten to mount `<ProblemStepper>` + `<ProgressStrip>` + `<DrillStack>` + `<ResourceRail>` + `<CompileSubmitCard>`. `<PdfPane>` and `<Scratchpad>` removed from main JSX and remounted as drawer contents opened from the rail. `<ChatPane>` removed (deferred to Slice 2 backlog). `<Sidebar>` removed (mockup v5_1 has none; nav goes through existing header pills).
2. **`ResourceRail` component (new).** 320px right-side column that renders items from `task_prep.rail_json` and opens drawers on click. Drawer contents per item type: PDF view (existing `PdfPane`), Scratchpad workbench (existing `Scratchpad`), Concept drawer (existing `ConceptDrawer`), Prior-gap card (existing `KnowledgeGapCard`), FSRS-due pill navigates to `/tutor/review`.
3. **Bootstrap flow.** On mount, `GET /api/v1/tasks/{id}` joined with `task_prep`. On miss → POST `/api/v1/task/{id}/reprep` async + render skeleton + poll every 2 s until `task_prep` populated. Existing Phase B5 route already covers the reprep side; this fix wires the frontend polling.
4. **Submit endpoint (new backend route).** `POST /api/v1/tasks/{id}/submit` sets `TaskStatus.SUBMITTED`. D6's `CompileSubmitCard` already POSTs to this URL; route doesn't exist on the server, so the submit button is broken in production. This fix builds the route + Kotlin test before the wire-up.
5. **Visual acceptance gate.** Spec lists 7 `data-testid` selectors that Phase J final review must assert visible via Playwright against the live URL. Bundle-hash-matches + tests-green is no longer sufficient.
6. **Plan-author + SDD-reviewer workflow fixes** baked into the new plan and into the SDD final-review prompt: paired build+mount tasks, self-review grep for component names across new+modified files, Playwright visual gate.

## What's out of scope (deferred)

- **Mobile responsive.** Slice 1.5 ships desktop-only (≥ 768 px viewport). `@media` breakpoints for stack/stepper/rail collapse stay in Slice 2 backlog. Visible regression on phones is acceptable for this fix only.
- **Header task switcher dropdown** (mockup v5_1 specs a Cmd+K palette + task-switch chip in header). Not built. Without `<Sidebar>` and without a switcher, the only way to navigate between tasks in Slice 1.5 is the existing `/tutor/tasks` route via the `tasks` nav pill. If this proves painful in dogfood, add to Slice 2.
- **`<ChatPane>` free-chat surface.** Slice 1 spec already deferred this to Slice 2 (spec line 419). Slice 1.5 deletes the component from `TutorWorkspace.tsx`; Slice 2 will revive it as a separate `/tutor/` (no taskId) route per mockup v5.
- **Animations beyond what Slice 1 components already use.** ResourceRail's drawer-open uses animation #7 (already specced) but no new animations are added in Slice 1.5.
- **`task_prep` invalidation on `StateVersion.bump()`.** Slice 1 spec §A mentions this as the cache invalidation strategy; not built. Manual `/reprep` button in the rail covers Slice 1.5 needs.

## Architecture

### A. Component swap in `TutorWorkspace.tsx`

Current `return` JSX (live in production):

```
<div ref={workspaceRef}>
  <DaemonHealthPill header strip>
  <dedupedNotice />
  <materialPaths list />
  <flex>
    <Sidebar />
    <flex>
      <flex-col>
        <PdfPane />
        <Scratchpad />
      </flex-col>
      <flex-col>
        <ChatPane />
        <Sidekick />
      </flex-col>
    </flex>
  </flex>
  <StatusBar />
  {chipState && <InlineAskChip />}
</div>
```

Target `return` JSX (Slice 1.5):

```
<div ref={workspaceRef}>
  <header data-testid="tutor-header">
    <JARVIS · TUTOR title>
    <DaemonHealthPill />
  </header>
  <dedupedNotice />  /* kept for backwards compat */
  <ProblemStepper data-testid="problem-stepper" problems={...} activeProblemIndex={...} />
  <ProgressStrip data-testid="progress-strip" outer={...} inner={...} />
  <flex>
    <main className="flex-1">
      <DrillStack data-testid="drill-stack"
                  taskId={taskId}
                  problemId={currentProblem.id}
                  content={drillContent}
                  onProblemComplete={...} />
      <Sidekick envelope={sidekickEnvelope} />
      {allProblemsDone && (
        <CompileSubmitCard taskId={taskId}
                           answers={collectedAnswers}
                           onSubmitted={...} />
      )}
    </main>
    <ResourceRail data-testid="resource-rail"
                  taskId={taskId}
                  items={parsedRailItems} />
  </flex>
  <StatusBar />
  {chipState && <InlineAskChip />}
</div>
```

Removed: `<PdfPane>`, `<Scratchpad>`, `<ChatPane>`, `<Sidebar>` from main JSX. They survive only as drawer contents or as separate routes.

Backwards-compat retained:
- `attachSelectionListener` + `InlineAskChip` wiring (already in TutorWorkspace, E3) keeps text-selection ✨ ASK working inside drill card bodies.
- `Sidekick` panel mount stays, takes envelope from selection chip click or rail-item-click.
- Existing `dedupedNotice` rendering kept for users opening duplicated tasks.

### B. Layout (per spec §B ASCII + mockup v5_1)

```
┌─ HEADER · JARVIS · TUTOR · DaemonHealthPill ─────────────────┐
├──────────────────────────────────────────────────────────────┤
│ STEPPER: ◉ A1  ○ A2  ○ A3  ◉ A4                              │
├──────────────────────────────────────────────────────────────┤
│ OUTER: ●●○○○○ 2/6 problems   INNER: ●●○○ 2/4 cards (A2)      │
├──────────────────────────────────┬───────────────────────────┤
│ ③ DRILL · YOUR TURN              │ RESOURCE RAIL (320 px R)  │
│ ② WORKED EXAMPLE [locked]        │ · PDF · Tema_A.pdf p.4 →  │
│ ① DEFINITION [locked]            │ · SCRATCHPAD →            │
│ ④ CHECK · TRANSFER [locked]      │ · CONCEPT · Laplace MLE → │
│ SIDEKICK (collapsible) ▼         │ · PRIOR GAP · 73% →       │
│                                  │ FSRS · 4 DUE →            │
├──────────────────────────────────┴───────────────────────────┤
│ ⑤ COMPILE & SUBMIT (slides up after last problem complete)   │
└──────────────────────────────────────────────────────────────┘
```

Container widths: main column flex-1 (consumes available space); rail is fixed 320 px right column. Below 768 px viewport: degraded layout (rail wraps below or off-screen; explicitly out of scope for Slice 1.5).

### C. `ResourceRail` component (new)

```tsx
// tutor-web/src/components/ResourceRail.tsx
export interface RailItem {
  type: 'PDF' | 'SCRATCHPAD' | 'CONCEPT' | 'PRIOR_GAP' | 'FSRS_DUE';
  label: string;
  action: 'OPEN_DRAWER' | 'NAVIGATE';
  payload: Record<string, unknown>;
}

interface ResourceRailProps {
  taskId: string;
  items: RailItem[];
}

export function ResourceRail({ taskId, items }: ResourceRailProps) {
  const [openDrawer, setOpenDrawer] = useState<RailItem | null>(null);
  const navigate = useNavigate();

  function handleClick(item: RailItem) {
    if (item.action === 'NAVIGATE') {
      const route = (item.payload.route as string) || '/';
      navigate(route);
      return;
    }
    setOpenDrawer(item);
  }

  return (
    <>
      <aside data-testid="resource-rail"
             className="w-[320px] border-l-4 border-border-strong bg-page-bg flex flex-col">
        {items.map((item, i) => (
          <button
            key={`${item.type}-${i}`}
            data-testid={`rail-item-${item.type}`}
            onClick={() => handleClick(item)}
            className="text-left px-3 py-2 border-b border-border-thin hover:bg-accent-soft"
          >
            <span className="text-[10px] tracking-widest text-page-fg/60">{item.type}</span>
            <span className="block text-sm">{item.label}</span>
          </button>
        ))}
      </aside>
      {openDrawer && (
        <RailDrawer item={openDrawer} taskId={taskId} onClose={() => setOpenDrawer(null)} />
      )}
    </>
  );
}
```

Drawer content dispatch (one component per type, all existing except the dispatcher):

| RailItem.type | Drawer content | Source |
|---|---|---|
| `PDF` | `<PdfPane url={payload.path} ... />` | existing `PdfPane.tsx` |
| `SCRATCHPAD` | `<Scratchpad value={scratch} onChange={...} />` | existing `Scratchpad.tsx` |
| `CONCEPT` | `<ConceptDrawer conceptId={payload.conceptId} />` | existing `ConceptDrawer.tsx` |
| `PRIOR_GAP` | `<KnowledgeGapCard gap={...} />` | existing `KnowledgeGapCard.tsx` |
| `FSRS_DUE` | NAVIGATE action → router goes to `/tutor/review` (no drawer) | existing route F3 |

`RailDrawer` is a thin wrapper that fixed-positions itself on the right, slides in from off-screen (animation #7 from spec §G, 220 ms ease-out), provides a close button + Esc-key handler, and renders the appropriate inner component by switching on `item.type`.

### D. `rail_json` schema (explicit)

`task_prep.rail_json` is a TEXT column storing a JSON array of `RailItem`s. Server populates during `/reprep`. Backend writes it; frontend reads + parses + passes to `ResourceRail`.

```ts
type RailItem = {
  type: 'PDF' | 'SCRATCHPAD' | 'CONCEPT' | 'PRIOR_GAP' | 'FSRS_DUE';
  label: string;        // visible text, e.g. "Tema_A.pdf p.4"
  action: 'OPEN_DRAWER' | 'NAVIGATE';
  payload: {
    // PDF:        { path: string, page?: number }
    // SCRATCHPAD: {} (uses task's existing scratchpad endpoint)
    // CONCEPT:    { conceptId: string }
    // PRIOR_GAP:  { gapId: string, confidence: number }
    // FSRS_DUE:   { count: number, route: '/tutor/review' }
    [k: string]: unknown;
  };
};
```

Server-side population during `/reprep` (extension to existing Phase B5 route): build a fixed item list per task:

```kotlin
// Build rail items from task + concept/gap signals + FSRS state
val railItems: List<Map<String, Any?>> = buildList {
    // PDF entry (always)
    add(mapOf(
        "type" to "PDF",
        "label" to "${task.problemRef.path.substringAfterLast('/')} p.1",
        "action" to "OPEN_DRAWER",
        "payload" to mapOf("path" to task.problemRef.path)
    ))
    // Scratchpad entry (always)
    add(mapOf(
        "type" to "SCRATCHPAD",
        "label" to "draft answers",
        "action" to "OPEN_DRAWER",
        "payload" to emptyMap<String, Any?>()
    ))
    // Concept refs (one per task.conceptRefs entry)
    task.conceptRefs.forEach { c ->
        add(mapOf("type" to "CONCEPT", "label" to c.path.substringAfterLast('/'),
                  "action" to "OPEN_DRAWER",
                  "payload" to mapOf("conceptId" to c.hash)))
    }
    // FSRS due pill
    val due = FsrsDueQueue.due(ctx.db, userId, java.time.Instant.now(), 50).size
    if (due > 0) add(mapOf(
        "type" to "FSRS_DUE", "label" to "$due cards due",
        "action" to "NAVIGATE",
        "payload" to mapOf("count" to due, "route" to "/tutor/review")
    ))
}
val railJson = TutorTypes.tutorJson.encodeToString(JsonArraySerializer, railItems.map { ... toJsonElement })
```

Concrete serializer for the rail payload is handled by `kotlinx.serialization.json.JsonObject` round-trip; plan task implements the precise encoding.

Prior-gap items omitted in Slice 1.5 first cut — `KnowledgeGapCard` exists but binding gap signals into the rail is more involved than Slice 1.5 should take. Plan defers `PRIOR_GAP` rail items to Slice 2 (rail will simply omit them). Frontend dispatch still handles the type for forward compat.

### E. Scratchpad keeps existing backend

`GET /api/v1/tasks/{id}/scratchpad` and `PUT /api/v1/tasks/{id}/scratchpad` already exist in `TutorRoutes.kt` (lines 618 and 633). The component move from main JSX → drawer changes nothing on the backend. Scratchpad component already calls the existing endpoints in its own `useEffect` hooks; pulling it out of `TutorWorkspace.tsx` and mounting it inside `RailDrawer` works as-is.

Plan adds a verification step: "open SCRATCHPAD drawer → type text → close drawer → reopen → confirm text persisted (component-level test)".

### F. Bootstrap / data flow

```
mount TutorWorkspace(taskId)
  ↓
fetch GET /api/v1/tasks/{id}                            (existing route)
  ↓
fetch GET /api/v1/tasks/{id}/prep                       (NEW route — joins task_prep)
  ↓
prep present?
  ├─ NO  → setTaskPrepStatus("missing")
  │       POST /api/v1/task/{id}/reprep                 (B5 route, async LLM)
  │       render <DrillStack> skeleton + <ProblemStepper> skeleton
  │       setInterval(2s) → fetch GET /api/v1/tasks/{id}/prep until present
  │       on present → setTaskPrepStatus("loaded"); clearInterval
  ├─ YES → setTaskPrepStatus("loaded")
  ↓
parse prep.problems_json → List<Problem>
parse prep.drills_json → Map<problemId, DrillContent>
parse prep.rail_json → List<RailItem>
  ↓
URL ?problem=N → setActiveProblemIndex(parseProblemParam(N))   (D2 helper)
  ↓
render <ProblemStepper problems={parsed.problems} />
render <ProgressStrip outer={...} inner={perProblem[active].cardsDone}/>
render <DrillStack content={parsed.drills[parsed.problems[active].id]} />
render <ResourceRail items={parsed.rail} />
  ↓
on Drill submit → gradeDrill (D1) → DrillStack state machine (D4)
on CHECK complete → updateProgress; if last problem → render <CompileSubmitCard>
on CompileSubmit "MARK SUBMITTED" → POST /api/v1/tasks/{id}/submit  (NEW route below)
```

New backend route `GET /api/v1/tasks/{id}/prep` returns the joined `task_prep` row (or 404 if absent). Cheaper than calling reprep just to check. Plan task adds it alongside the submit route.

### G. Submit endpoint (new backend route)

```kotlin
// In TutorRoutes.kt installTutorRoutes()
@Serializable
private data class ApiTaskSubmitRequest(val note: String? = null)

@Serializable
private data class ApiTaskSubmitReply(
    val taskId: String,
    val status: String,
    val submittedAt: String,
)

post("/api/v1/tasks/{id}/submit") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
            ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
        val task = TaskRepo(ctx.db).findById(taskId)
            ?: run { call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect }
        if (task.userId != userId) {
            call.respond(HttpStatusCode.Forbidden, "not your task"); return@csrfProtect
        }
        val now = java.time.Instant.now()
        val req = try {
            sensorJson.decodeFromString(ApiTaskSubmitRequest.serializer(), call.receiveText())
        } catch (_: Exception) { ApiTaskSubmitRequest() }
        // Persist status transition
        TaskRepo(ctx.db).updateStatus(taskId, jarvis.tutor.TaskStatus.SUBMITTED, now)
        // Optional: persist req.note into task.submission if SubmissionRef structure supports it.
        // For Slice 1.5: ignore note for now (acceptance criterion is status flip, not note storage).
        call.respond(HttpStatusCode.OK, ApiTaskSubmitReply(
            taskId = taskId,
            status = jarvis.tutor.TaskStatus.SUBMITTED.name,
            submittedAt = now.toString(),
        ))
    }
}
```

If `TaskRepo` does not currently expose `updateStatus(taskId, status, at)`, plan adds the method first as a TaskRepo extension. Backend test asserts: 200 response + status field flips to SUBMITTED in DB + 401 unauth + 403 cross-user + 404 missing.

Plan sequencing: backend submit route + test SHIPS BEFORE frontend wires `CompileSubmitCard` to it. D6's component already targets this URL; the route just needs to exist.

## Visual acceptance criteria (postmortem fix — load-bearing)

Final review of Slice 1.5 runs Playwright headless against `https://corgflix.duckdns.org/tutor/?taskId=<REAL_ID>` and asserts these seven selectors paint on first load. Bundle hash matching + tests green is NOT sufficient.

| # | Selector | Expected on initial paint |
|---|---|---|
| 1 | `[data-testid="problem-stepper"]` | Visible, contains ≥ 1 `<button>` child |
| 2 | `[data-testid="progress-strip"]` | Visible, contains 2 `[role="progressbar"]` (outer + inner) |
| 3 | `[data-testid="drill-stack"]` | Visible, wraps the 4 `[data-testid="drill-card"]` |
| 4 | `[data-testid="drill-card"][data-state="open"]` | At least 1 (the DRILL card) |
| 5 | `[data-testid="drill-card"][data-state="locked"]` | At least 1 (one of WORKED/DEF/CHECK) |
| 6 | `[data-testid="resource-rail"]` | Visible, contains ≥ 2 `[data-testid^="rail-item-"]` (at minimum PDF + SCRATCHPAD) |
| 7 | `[data-testid="sidekick-panel"]` | Present (collapsed default acceptable, just must exist in DOM) |

Acceptance criterion sentence (must appear in Slice 1.5 plan's J-equivalent task):

> "Feature shipped means: visible at live URL via Playwright assertions on the seven `data-testid` selectors above. Bundle hash matching + green test suite is NOT sufficient. The 2026-05-11 Slice 1 lesson — five components in bundle, ghosts in paint."

If any selector fails → final review reports `failed`, deploy is rolled back via `bash tools/deploy.sh rollback`, fix dispatched to a follow-up implementer subagent.

## Plan-author + SDD-reviewer workflow fixes (postmortem fix — bake into the new plan)

These rules govern the Slice 1.5 plan itself and the SDD execution. They also belong in the user-global `C:\Users\User\.claude\CLAUDE.md` and the `superpowers:writing-plans` skill for permanent application.

### Plan structure rules

1. **Paired build+mount tasks.** Every "create new component" task MUST have a paired or following "mount it" task that names the exact file (`tutor-web/src/components/TutorWorkspace.tsx`) and shows the JSX diff inserting the new component. Two checkboxes minimum per component: build + mount. No "implicit mounting" via file-structure preamble bullets.
2. **No file-structure-only modifications.** The "Frontend (modified)" bullet list at the top of plan files is documentation, not load-bearing. Every modification must have its own task. If a file appears in "Modified" but no task touches it, plan self-review must catch this.

### Plan self-review additions

3. **Component-mount grep.** Self-review must grep the plan body for every component name listed under "Frontend (new)". The component name must appear in a task body under "Frontend (modified)" as an import or JSX element. If only "new" mentions it → integration task missing → fix before plan handoff.
4. **`data-testid` grep.** Self-review must grep the plan body for each `data-testid` selector named in the spec's Visual Acceptance section. Each selector must appear in at least one task's code block (as a `data-testid="..."` attribute set on the component being built or mounted). If a spec selector has no corresponding plan task → fix before handoff.

### SDD reviewer fixes

5. **Final whole-branch reviewer = Playwright visual gate.** After all tasks complete, the SDD final reviewer subagent prompt explicitly includes: "Run Playwright headless against `<live URL>` or local dev server. Assert the seven `data-testid` selectors from the spec's Visual Acceptance section are visible on first paint. Component shipped + tests green is NOT enough. Open the URL. Confirm the user sees it." Reviewer must report selector-by-selector PASS/FAIL.

### Trust-but-verify rule extension (CLAUDE.md global)

6. Extend existing trust-but-verify rule:

> Before claiming a feature is shipped: open its user-facing surface (URL or CLI command) and confirm the user sees the feature. Bundle hash + tests green ≠ feature shipped. The 2026-05-11 Slice 1 lesson — 5 components in bundle, ghost in paint.

This extension belongs in user-global `C:\Users\User\.claude\CLAUDE.md`. Plan adds a task to append the extension.

## Testing

Backend:
- `POST /api/v1/tasks/{id}/submit` integration test: auth-required, owner-required, status-flips-to-SUBMITTED, idempotent (re-submitting already-SUBMITTED task is no-op success).
- `GET /api/v1/tasks/{id}/prep` integration test: returns 404 on miss, 200 with task_prep shape on hit, joins task data correctly.
- `/reprep` route extension test: populates `rail_json` with at least PDF + SCRATCHPAD items.

Frontend (Vitest + Testing Library):
- `ResourceRail` renders one button per item, dispatches by type, opens drawer on `OPEN_DRAWER`, navigates on `NAVIGATE`.
- `TutorWorkspace.tsx` integration test (new): on mount, fetches prep; if 404, fires reprep + polls; if 200, renders stack. Mock fetch to drive both paths.
- `CompileSubmitCard` POST to `/api/v1/tasks/{id}/submit` test (extend existing D6 test): success + 4xx error states.

Playwright (final review, headless against live URL):
- Seven selector assertions per Visual Acceptance section.
- Single dummy session cookie or anonymous-mode bypass for the auth-gated routes (Slice 1.5 plan task #J resolves this — likely a `JARVIS_PLAYWRIGHT_BYPASS` env var or a public-read flag on `/api/v1/tasks/{id}/prep`).

Test count target after Slice 1.5: existing 661 backend + ~6 new = ~667 backend; existing 268 frontend + ~10 new = ~278 frontend.

## Migration

1. No schema changes (Slice 1 already added `task_prep` table). Existing `task_prep` rows that were never populated with `rail_json` content (i.e. the `rail_json` column is empty string `[]`) → frontend gracefully renders an empty rail. User clicks a "refresh task prep" button (in the header? in the rail?) to trigger reprep, which now populates rail correctly. Plan task adds the refresh button.
2. Bundle rebuild + redeploy: `cd tutor-web && npm run build && bash tools/deploy.sh`. Bundle hash drifts; BRIDGE.md captures new hash.
3. Live `task_prep` rows authored by Slice 1's B5 (Tema A onwards) had `rail_json = "[]"`. After Slice 1.5 deploys, those rows still render with empty rails until manually reprep'd. Acceptable for a single-user solo project; user reprep's tasks they're actively working on.

## Sequencing

Plan task order (writing-plans implements this):

1. **Backend route: `POST /api/v1/tasks/{id}/submit`** — Kotlin route + tests. Build first so frontend mount step has a target.
2. **Backend route: `GET /api/v1/tasks/{id}/prep`** — Kotlin route returning joined task + task_prep. Build before frontend mount uses it.
3. **Backend `/reprep` extension** — populate `rail_json` with PDF + Scratchpad + Concept items. Test asserts non-empty rail.
4. **Frontend `ResourceRail` component (build)** — new component + tests + `RailItem` TS interface. Tests cover dispatch + drawer + navigate.
5. **Frontend `ResourceRail` (mount in `TutorWorkspace.tsx`)** — paired with #4, separate task per workflow-fix rule #1. JSX diff shown.
6. **Frontend `TutorWorkspace.tsx` rewire (build+mount in one task is acceptable here since this is the integration point)** — replace `return` JSX with the new layout. Mount `ProblemStepper`, `ProgressStrip`, `DrillStack`, `Sidekick` (already mounted, verify), `ResourceRail`, `CompileSubmitCard`. Remove `PdfPane`, `Scratchpad`, `ChatPane`, `Sidebar` from JSX. Add prep-fetch + skeleton + poll logic. Tests cover both bootstrap paths (prep present, prep missing → poll).
7. **CompileSubmitCard endpoint wiring** — already POSTs to `/api/v1/tasks/{id}/submit` per D6. Verify the existing D6 component now succeeds against the real route added in #1. Test against mocked-success path.
8. **Trust-but-verify extension** — append the feature-shipped-means-visible clause to `C:\Users\User\.claude\CLAUDE.md`.
9. **Bundle rebuild + deploy** — `npm run build`, `bash tools/deploy.sh`, smoke `curl` for `index-XXXXXXXX.js` drift.
10. **Final review (Playwright visual gate)** — assert seven selectors against live URL. Report PASS/FAIL per selector. On FAIL, roll back + dispatch fix subagent.
11. **BRIDGE.md wrap** — new entry with new bundle hash, test counts, deploy timestamp.

Estimated 4-6 hours via writing-plans → SDD pipeline.

## Self-review (per skill)

- **Placeholder scan:** no `TBD` / `TODO` / `implement later` in this doc.
- **Internal consistency:**
   - Component swap §A enumerates removed + added; matches §B layout ASCII.
   - RailItem schema §D matches `ResourceRail` props in §C.
   - Submit route §G matches plan task #1 + frontend wire in plan task #7.
   - Visual Acceptance §H selectors match `ResourceRail` `data-testid` attribute in §C; match `DrillStack` / `DrillCard` / `ProblemStepper` / `ProgressStrip` `data-testid` attributes already shipped in Slice 1.
- **Scope check:** 11 plan tasks, ~4-6 hours. Single coherent push. Mobile + task switcher + free-chat deferred.
- **Ambiguity check:**
   - "`PRIOR_GAP` rail item deferred to Slice 2" — explicitly out of scope for this fix.
   - Submit-button-on-already-SUBMITTED task: idempotent success.
   - Empty `rail_json` legacy rows: render empty rail, no error.
   - Reduced-motion handling for drawer slide-in: existing animation #7 CSS already supports `prefers-reduced-motion: reduce`; ResourceRail inherits.

## Backlog (Slice 2)

Lifted from Slice 1 backlog plus Slice 1.5 deferred:

- Mobile responsive (`@media`, slide-over rail, 44 px touch targets).
- Header task switcher dropdown + Cmd+K palette (mockup v5_1 spec).
- Free-chat surface (`<ChatPane>` revived as `/tutor/` route per mockup v5).
- `PRIOR_GAP` rail items (bind `KnowledgeGapCard` into rail dispatch).
- `task_prep` invalidation on `StateVersion.bump()` (replace manual refresh button with reactive cache).
- a11y pass (focus-visible, drawer focus trap, ARIA dialog on rail drawer, Esc to close).
- Visual hierarchy refactor + Romanian academic register pass.
- Streak gamification opt-out.
- Onboarding tooltips for first-time drill workspace open.
