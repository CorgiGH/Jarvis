# Push-not-pull metacognitive surface — Design (2026-05-17)

> Carry-over from council `1778988899-sidebar-dead-code` First Principles agent
> (sanity-check PASS): the KnowledgeLedger is **bursty, not ambient** — relevant
> at two moments only (end-of-drill: "did I just repeat a known gap?" and
> start-of-session: "what's still unresolved?"). Surfacing it via a header
> button alone (Option C shipped) leaves the user-initiated burden in place.
> A push surface — auto-injecting the most recent unresolved gap into the
> drill-result panel — closes the metacognitive loop without requiring the user
> to remember to click anything.

## Goals

- **Close the metacog loop without user-initiated navigation.** When a user
  completes (or gives up on) a drill, the next eye-line below the grade
  feedback should answer "have I bumped into a similar concept before, and is
  it still unresolved?" — surfaced automatically, dismissible per-session.
- **Reuse the existing gap data path.** `/api/v1/gaps` already returns
  `{topic, taskId, type, reusedCount, resolvedBy}`. No new backend route.
- **Earn UI footprint via signal, not speculation.** A telemetry counter on
  surface-shown + click + dismiss lets the next /wrap window decide if this
  earned the space or churned in noise.

## Non-goals

- **No new prediction model.** "Did I miss a similar concept" is a string-match
  + recency heuristic, not an embedding-distance model. Slice 3 may upgrade.
- **No new gap types.** This consumes existing rows; doesn't write any.
- **No notifications outside the drill-result panel.** Out of scope for this
  spec: home-screen recap, daily email, push notifications. The drill-result
  panel is the trigger surface because that's where the brain is in
  recall-mode.
- **No replacement for the LEDGER button.** This is additive — LEDGER stays
  reachable via header for whole-history browse. Push-surface is for
  current-drill context.

## The trigger

After a `gradeDrill` call returns and `gradeResult` is set in
`DrillStack.tsx`, render a sibling panel BELOW `grade-feedback` and the
rubric grade chip strip. The panel only renders when:

1. `gradeResult` is non-null AND
2. `gradeResult.correct === false` OR `giveUp === true` (push-surface only
   fires on a miss; correct answers don't need the metacog reminder), AND
3. There exists at least one unresolved gap from `/api/v1/gaps` matching
   ANY of the following heuristics against `gradeResult.misconception`,
   `content.drill` (problem statement), or current task subject:
    - **Direct misconception match** — gap `topic` contains a substring of
      the misconception code (e.g. `MINIMAX_CONFUSION` → look for "minimax"
      in any open-gap topic).
    - **Recent-on-this-task** — gap `taskId === taskId` AND `resolvedBy ==
      null` AND created within the last 14 days.
    - **Recent-on-this-subject** — gap `taskId` joins to a task whose
      subject matches the current task's subject AND open AND last 7 days.
4. Heuristic priority: direct misconception > recent-on-this-task > recent-on-subject.
   First match wins. Only ONE gap surfaces per drill-result panel — no list.

## The surface

```
┌─────────────────────────────────────────────────────────────┐
│  GRADE FEEDBACK (existing)                                  │
│  …                                                          │
├─────────────────────────────────────────────────────────────┤
│  ⚡ FAMILIAR GAP                                            │
│  you flagged "L2 estimator confusion" 3 days ago on        │
│  Tema 5 — and it's still unresolved.                       │
│                                                             │
│  [ open in ledger ]  [ mark resolved ]  [ snooze 1h ]      │
└─────────────────────────────────────────────────────────────┘
```

Brutalist style: same `border-l-4 border-accent-rule` + accent-soft
background as the existing feedback strip. `data-testid="related-gap-push"`
for audit reach. ARIA: `role="region" aria-label="Related historical gap"`.

Button surface:
- `[open in ledger]` → opens KnowledgeLedger (reuses `setLedgerOpen(true)`
  from App.tsx, lifted via context OR a custom event) filtered to this gap.
- `[mark resolved]` → POST `/api/v1/gap/{id}/status` body `{resolvedBy:
  "USER_MARKED_DONE"}` (route already exists at `TutorRoutes.kt:1257`).
- `[snooze 1h]` → localStorage `jarvis.push.snooze_until.{gapId}` = now+1h;
  surface won't reappear for this gap until then. Pure client-side.

## Snooze + dismiss state

- **Session snooze** (`jarvis.push.snooze_until.{gapId}`): per-gap, expires
  in 1h. Bumped each time the user clicks `[snooze]`. No backend persistence.
- **Resolved**: gap disappears from `/api/v1/gaps` open results; surface
  won't re-emerge.
- **Auto-fade after 30s**: panel collapses to a 1-line strip
  `⚡ FAMILIAR GAP — 1 unresolved (open ledger)` after 30s of no
  interaction. Re-expands on hover/focus. Doesn't auto-dismiss entirely.

## Telemetry

Five events via `recordTelemetry()` (shipped 2026-05-17 in hot-work #4):
- `push.gap.shown` — surface rendered. Payload: `{gapId, taskId, misconception}`
- `push.gap.opened` — user clicked `[open in ledger]`
- `push.gap.resolved` — user clicked `[mark resolved]`
- `push.gap.snoozed` — user clicked `[snooze 1h]`
- `push.gap.faded` — auto-fade fired (30s without click)

After 2 weeks, ratio `(opened + resolved) / shown` decides if the surface
earns its footprint. Target: ≥ 30% engagement OR ≥ 1 explicit `resolved`
per week. Below either threshold → re-spec or revert.

## Implementation plan (outline — full plan in companion doc when greenlit)

### Backend

No new routes. Existing `/api/v1/gaps` (line 1237 in TutorRoutes.kt) +
`/api/v1/gap/{id}/status` (line 1257) cover the path.

### Frontend

1. **New component**: `tutor-web/src/components/RelatedGapPush.tsx` —
   takes `taskId`, `misconception`, `subject` props; fetches gaps; chooses
   a gap via the priority heuristic; renders the surface with the 3
   buttons. Fades after 30s.
2. **Mount in DrillStack.tsx** — below `grade-feedback` (~line 220), only
   when `gradeResult && !gradeResult.correct`.
3. **Lift `setLedgerOpen`** — from App.tsx state into a `LedgerContext`
   provider, so RelatedGapPush can trigger the drawer from anywhere.
4. **Add 2-3 vitest cases** — render with mocked `/api/v1/gaps`, click
   each of the 3 buttons, assert correct telemetry + API calls.
5. **Audit spec** — new row S-31: "drill-result with related gap visible"
   — reach `S-10 → wait related-gap-push` → required selectors.

### Risk

- **Empty-task drills (POO C1, PA Tema 5, SO Linux)**: `gradeResult ===
  null` because there are no drills to grade. Surface never fires.
  Acceptable — task setup is the bottleneck there, not metacog.
- **Cold-start no-gaps user**: `/api/v1/gaps` returns `{gaps: []}`. No
  gap matches; surface doesn't render. Correct behavior.
- **Match heuristic false-positive**: surface fires on an unrelated gap
  whose topic happens to substring-match the misconception code. Telemetry
  catches this if `(opened + resolved) / shown` falls below 30% — re-tune
  matcher then.

## Decision: spec → plan → ship gating

This spec lands as a brainstorm doc. Plan doc + impl wait for:
1. User greenlight (the carry-over earned a "separate spec" status, not an
   immediate ship).
2. ≥ 1 real telemetry signal from `ledger.opened` showing the LEDGER itself
   gets used user-initiated (post-2026-05-17 deploy). If `ledger.opened`
   stays at 0 by 2026-05-31, Option B (delete LEDGER entirely) supersedes
   this spec — no point adding push-surface for a feature about to be
   removed.

## Open questions for the council (if convened)

1. **Heuristic vs. embedding**: misconception-string match is brittle. Is a
   small embedding-distance model worth Slice 3?
2. **One-gap-at-a-time vs. carousel**: when 3 historical gaps match, do we
   show top-1 or a carousel? Top-1 minimizes cognitive load but loses
   coverage.
3. **Resolved-gap auto-dismiss timing**: 30s fade — too fast? too slow?
   No data yet; instrument + tune via telemetry.
4. **Cross-subject leakage**: a PS gap surfaced during an SO drill — is
   that recall-helpful (concepts cross subjects) or distracting? Default:
   same-subject only, opt-in to cross-subject later if telemetry shows
   demand.

## Carry-over from this spec to next session

After user reviews this brainstorm, options are:
- **A**: convene a council with FleetView/Spawn (5 agents) to validate the
  heuristic + UI; output a numbered plan doc.
- **B**: greenlight + write plan inline (skip council since the design is
  modest scope, low blast radius).
- **C**: defer until `ledger.opened` telemetry signal arrives (recommended
  default — don't build for a feature that may be deleted).
