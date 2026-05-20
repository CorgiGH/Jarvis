# Gate 1: Viz Foundation Compliance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adopt the `viz-foundation-demo` branch as the redesign's visualization foundation and bring it to full spec §6.4 V1-V20 compliance.

**Architecture:** Per the 2026-05-20 audit (`docs/superpowers/research/2026-05-20-viz-foundation-demo-audit.md`), the branch ships 15 working primitives + a shared `AlgoStepperShell` + all 6 ⭐ world-firsts, but carries: a branch-wide V2 autoplay violation, a broken V3 prediction-gate contract (single gate, not a per-frame map), a V12 yellow-as-category defect in 6 files, and ARIA gaps in 4 standalone viz. This plan merges the branch, then applies the audit's **Tier 1 (T1-T5)**, **Tier 2 (T6-T11)**, **Tier 3 (T12-T15)**, and **Tier 5 hygiene** as TDD tasks. The audit's **Tier 4** (prediction-gate content authoring, BayesTree draggable prior, RC-3 bufferbloat/AQM build-out) is intentionally OUT of this plan — it is pedagogy-content depth, gated on T2 landing, and ships as a separate follow-on plan so the gate questions can be authored with subject review.

**Tech Stack:** React 19 + TypeScript + Vite; `motion@12`; `vitest` + `@testing-library/react`; `d3` / `@visx` / `mafs`; Playwright headless for the brutalist rule-sniff gate.

**Execution branch:** work continues directly on `viz-foundation-demo` (it IS the foundation — no separate merge target). Task 1 merges current `main` into it. At Gate-1 acceptance the branch merges to `main`.

---

## File Structure

**Created:**
- `tutor-web/src/components/viz/HatchDefs.tsx` — shared SVG `<defs>` exporting `hatch-light` + `hatch-dense` `<pattern>` elements. One responsibility: own the two magnitude/category hatch fills so no viz hand-rolls its own.
- One `*.test.tsx` per task (paths given per task).

**Modified:**
- `tutor-web/src/components/viz/theme.ts` — add `HATCH_LIGHT` / `HATCH_DENSE` tokens (T1).
- `tutor-web/src/components/viz/AlgoStepperShell.tsx` — V3 `predictionGates` map (T2), V2 autoplay removal (T3), V17 Home/End keys (T4), `initialStep`/`onStep` contract (T5), mount `<HatchDefs/>` (T1).
- `tutor-web/src/components/viz/SchedulerGantt.tsx` (T6), `SigmaStackedBar.tsx` (T7), `OsiEncap.tsx` (T8), `TcpHandshake.tsx` (T9), `TcpCwnd.tsx` (T10), `Tls0RttReplay.tsx` (T11) — V12 palette fixes.
- `tutor-web/src/components/viz/NumLineDirect.tsx` (T12), `SumPlotTracker.tsx` (T13), `CompareFrames.tsx` (T14), `SlopeCounter.tsx` (T15) — ARIA fixes.
- `tutor-web/src/components/viz/BayesTree.tsx`, `CppVTable.tsx`, `MatrixTransform.tsx`, `ProcessFSM.tsx` — dead-code hygiene (T16).

**Decomposition note:** every viz component is one file with one responsibility (one roster viz). The Shell is the one shared file all frame-based viz depend on — its tasks (T2-T5) land before any Tier 2/3 task so the contract is stable when components are touched.

---

## Task 1: Adopt the branch — merge `main`, establish green baseline

**Files:**
- No file edits authored here — this is a git merge + verification task.

- [ ] **Step 1: Check out the foundation branch**

Run: `git checkout viz-foundation-demo`
Expected: `Switched to branch 'viz-foundation-demo'`. HEAD = `0b32947`.

- [ ] **Step 2: Merge current `main` into it**

Run: `git merge main`
`main` carries the FSRS commits (`5a9ccfa`..`da072e3`) that the branch does not have. Expected conflicts and resolutions:
- `src/main/resources/tutor-dist/**` (built bundle artifacts) — take **either** side; the bundle is rebuilt in the Final task. `git checkout --theirs src/main/resources/tutor-dist && git add src/main/resources/tutor-dist`.
- `tutor-web/package.json` / `tutor-web/package-lock.json` — if conflicted, keep the **union** of dependencies (branch adds motion/d3/visx/mafs; main may have changed unrelated deps). Hand-merge, do not drop either side's deps.
- Any `*.kt` conflict — keep both sides' changes; the branch did not touch backend, so conflicts here are unexpected — if one appears, stop and inspect.

- [ ] **Step 3: Install + verify the merge**

Run: `npm --prefix tutor-web install`
Run: `npm --prefix tutor-web run test -- --run`
Expected: green baseline — vitest passes (~348 tests per the branch's last wrap; exact count may shift with the merge). Record the exact pass count; it is the regression baseline for every later task.

- [ ] **Step 4: Commit the merge**

Run:
```bash
git add -A
git commit -m "chore(viz): merge main into viz-foundation-demo — adopt branch as Gate 1 base"
```

---

## Task 2 (T1): theme.ts hatch tokens + shared `HatchDefs` component

**Files:**
- Modify: `tutor-web/src/components/viz/theme.ts`
- Create: `tutor-web/src/components/viz/HatchDefs.tsx`
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx` (mount `<HatchDefs/>`)
- Test: `tutor-web/src/components/viz/__tests__/HatchDefs.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// tutor-web/src/components/viz/__tests__/HatchDefs.test.tsx
import { render } from "@testing-library/react";
import { HatchDefs } from "../HatchDefs";
import { HATCH_LIGHT, HATCH_DENSE } from "../theme";

test("theme exports hatch fill tokens", () => {
  expect(HATCH_LIGHT).toBe("url(#hatch-light)");
  expect(HATCH_DENSE).toBe("url(#hatch-dense)");
});

test("HatchDefs renders both pattern elements", () => {
  const { container } = render(
    <svg>
      <HatchDefs />
    </svg>
  );
  expect(container.querySelector("pattern#hatch-light")).not.toBeNull();
  expect(container.querySelector("pattern#hatch-dense")).not.toBeNull();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix tutor-web run test -- --run HatchDefs`
Expected: FAIL — `HATCH_LIGHT`/`HATCH_DENSE` not exported; `../HatchDefs` module not found.

- [ ] **Step 3: Add the theme tokens**

Append to `tutor-web/src/components/viz/theme.ts`:
```ts
// Hatch fills — V6 "hatching density for magnitude/category" (NOT opacity).
// Reference via fill={HATCH_LIGHT}; the consuming SVG must render <HatchDefs/>.
export const HATCH_LIGHT = "url(#hatch-light)";
export const HATCH_DENSE = "url(#hatch-dense)";
```

- [ ] **Step 4: Create `HatchDefs.tsx`**

```tsx
// tutor-web/src/components/viz/HatchDefs.tsx
import { INK } from "./theme";

// Shared SVG <defs> for the two brutalist hatch patterns.
// Render once inside any <svg> that uses HATCH_LIGHT / HATCH_DENSE fills.
export function HatchDefs() {
  return (
    <defs>
      <pattern
        id="hatch-light"
        patternUnits="userSpaceOnUse"
        width={6}
        height={6}
        patternTransform="rotate(45)"
      >
        <line x1={0} y1={0} x2={0} y2={6} stroke={INK} strokeWidth={1} />
      </pattern>
      <pattern
        id="hatch-dense"
        patternUnits="userSpaceOnUse"
        width={3}
        height={3}
        patternTransform="rotate(45)"
      >
        <line x1={0} y1={0} x2={0} y2={3} stroke={INK} strokeWidth={1} />
      </pattern>
    </defs>
  );
}
```

- [ ] **Step 5: Mount `<HatchDefs/>` in the Shell's SVG**

In `AlgoStepperShell.tsx`: add `import { HatchDefs } from "./HatchDefs";` to the theme-import region. Inside the `<svg>` element, immediately after `<desc id={descId}>{desc}</desc>` and before `{currentFrame && renderFrame(...)}`, insert:
```tsx
            <HatchDefs />
```
This makes the hatch patterns available to every Shell-based viz (Tier 2 consumers).

- [ ] **Step 6: Run test to verify it passes**

Run: `npm --prefix tutor-web run test -- --run HatchDefs`
Expected: PASS (both tests).

- [ ] **Step 7: Commit**

```bash
git add tutor-web/src/components/viz/theme.ts tutor-web/src/components/viz/HatchDefs.tsx tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/components/viz/__tests__/HatchDefs.test.tsx
git commit -m "feat(viz): add HATCH_LIGHT/HATCH_DENSE tokens + HatchDefs (audit T1)"
```

---

## Task 3 (T2): AlgoStepperShell — V3 `predictionGates` per-frame map

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Test: existing AlgoStepperShell test file (locate via `Glob tutor-web/**/AlgoStepperShell*.test.tsx`); add cases there.

**Context:** the Shell today exposes `predictionGate?: PredictionGate` (singular) and one boolean `predictionLocked`. Spec V3 + the `AlgoVizProps` contract require `predictionGates?: Map<number, PredictionGate>` — a gate keyed by frame index. A gate at frame N must be reachable but block advance to N+1 until answered.

- [ ] **Step 1: Write the failing test**

Add to the AlgoStepperShell test file:
```tsx
import { render, screen, fireEvent } from "@testing-library/react";
import { AlgoStepperShell, type PredictionGate } from "../AlgoStepperShell";

function gateFixture(): Map<number, PredictionGate> {
  return new Map([
    [2, { question: "next?", answers: [{ label: "A", isCorrect: true }, { label: "B", isCorrect: false }] }],
  ]);
}

test("predictionGates: gate at frame 2 blocks advance past frame 2 until answered", () => {
  const frames = Array.from({ length: 6 }, (_, i) => ({ state: i, aria: `f${i}` }));
  render(
    <AlgoStepperShell
      title="t" desc="d" frames={frames}
      renderFrame={(f) => <text>{String(f.state)}</text>}
      predictionGates={gateFixture()}
      testIdPrefix="gt"
    />
  );
  // step to frame 2 (the gated frame is reachable)
  fireEvent.click(screen.getByTestId("gt-step-fwd"));
  fireEvent.click(screen.getByTestId("gt-step-fwd"));
  expect(screen.getByTestId("gt-frame-counter").textContent).toContain("3 / 6");
  // forward is now blocked — predict gate visible, step-fwd disabled
  expect(screen.getByTestId("predict-gate")).toBeInTheDocument();
  expect(screen.getByTestId("gt-step-fwd")).toBeDisabled();
  // answer the gate -> advance unlocks
  fireEvent.click(screen.getByText("A"));
  expect(screen.queryByTestId("predict-gate")).toBeNull();
  fireEvent.click(screen.getByTestId("gt-step-fwd"));
  expect(screen.getByTestId("gt-frame-counter").textContent).toContain("4 / 6");
});
```
Note: this test references `data-testid="gt-frame-counter"` — Step 3 adds that testid to the existing frame-count `<div>` (it currently has none).

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: FAIL — `predictionGates` prop not accepted; no `*-frame-counter` testid.

- [ ] **Step 3: Rework the Shell for the gates map**

In `AlgoStepperShell.tsx` apply these edits:

(a) Props interface — replace `predictionGate?: PredictionGate;` with:
```ts
  predictionGates?: Map<number, PredictionGate>;
```

(b) Replace the `predictionLocked` state line with answered-gate tracking:
```ts
  const [answeredGates, setAnsweredGates] = useState<Set<number>>(new Set());
```

(c) After `lastIdx` is computed, add the reachable-frame ceiling:
```ts
  const maxReachable = useMemo(() => {
    if (!props.predictionGates) return lastIdx;
    const gateFrames = [...props.predictionGates.keys()].sort((a, b) => a - b);
    for (const g of gateFrames) {
      if (!answeredGates.has(g)) return Math.min(g, lastIdx);
    }
    return lastIdx;
  }, [props.predictionGates, answeredGates, lastIdx]);

  const gateLocked =
    !!props.predictionGates?.has(idx) && !answeredGates.has(idx);
  const activeGate = props.predictionGates?.get(idx);
```

(d) `stepBy` — clamp to `maxReachable` instead of `lastIdx`, drop the old `predictionLocked` guard:
```ts
  const stepBy = useCallback(
    (delta: number) => {
      setIdx((prev) => Math.max(0, Math.min(maxReachable, prev + delta)));
    },
    [maxReachable]
  );
```

(e) `onPredict` — record the answered gate by frame index:
```ts
  const onPredict = useCallback(
    (isCorrect: boolean) => {
      setAnsweredGates((prev) => new Set(prev).add(idx));
      activeGate?.onAnswered?.(isCorrect);
    },
    [idx, activeGate]
  );
```

(f) Scrubber `<input type="range">` — `max={maxReachable}`, `disabled={gateLocked}`, and clamp `onChange` to `maxReachable`.

(g) The frame-count `<div>` (`{idx + 1} / {materializedFrames.length}`) — add `data-testid={`${testIdPrefix}-frame-counter`}`.

(h) step-fwd button `disabled` — change to `disabled={idx >= maxReachable}`. step-back button keeps `disabled={idx === 0}`.

(i) Predict-gate UI block — change the render condition from `props.predictionGate && predictionLocked` to `gateLocked && activeGate`, and read `activeGate.question` / `activeGate.answers` instead of `props.predictionGate.*`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: PASS, including the new gate test. Pre-existing Shell tests that referenced the old `predictionGate` prop will fail — update them in the same step to the `predictionGates` map shape (this is part of the contract change, not separate work).

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/components/viz/__tests__/
git commit -m "feat(viz): AlgoStepperShell predictionGates per-frame map — V3 (audit T2)"
```

---

## Task 4 (T3): AlgoStepperShell — remove autoplay (V2: manual advance only)

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Test: AlgoStepperShell test file.

**Context:** spec V2 = "Manual advance only (no autoplay)." The Shell ships `DEFAULT_AUTOPLAY_MS`, a `setInterval` frame-advancer, `playing` state, `togglePlay`, a `▶ play/⏸ pause` button, and `autoplayMsPerFrame`. All removed. The `Space` key is repurposed to step forward one frame (manual advance — spec §6.5 verb 1 lists "Space advance").

- [ ] **Step 1: Write the failing test**

```tsx
test("V2: no play button; Space advances exactly one frame", () => {
  const frames = Array.from({ length: 5 }, (_, i) => ({ state: i, aria: `f${i}` }));
  render(
    <AlgoStepperShell title="t" desc="d" frames={frames}
      renderFrame={(f) => <text>{String(f.state)}</text>} testIdPrefix="ap" />
  );
  expect(screen.queryByTestId("ap-play")).toBeNull();
  const svg = screen.getByRole("img");
  fireEvent.keyDown(svg, { key: " " });
  expect(screen.getByTestId("ap-frame-counter").textContent).toContain("2 / 5");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: FAIL — `ap-play` button still rendered.

- [ ] **Step 3: Strip autoplay**

In `AlgoStepperShell.tsx`, delete:
- `const DEFAULT_AUTOPLAY_MS = 1400;`
- `autoplayMsPerFrame?: number;` from `AlgoStepperShellProps`
- `autoplayMsPerFrame = DEFAULT_AUTOPLAY_MS` from the props destructure
- `const [playing, setPlaying] = useState(false);`
- `const intervalRef = useRef<...>(null);`
- the entire `togglePlay` `useCallback`
- the entire autoplay `useEffect` (the `setInterval` block)
- the `<button ... data-testid={`${testIdPrefix}-play`}>` element
- `setPlaying(false);` inside `reset` (just `setIdx(0)`)

Change the `Space` branch of `onKey` from `togglePlay()` to `stepBy(1)`. Keep the `reducedMotion` read + the reduced-motion CSS block + `<MotionConfig reducedMotion="user">` — those stay (they govern per-frame entry animation, not autoplay).

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: PASS. Update any pre-existing test referencing `*-play` or `togglePlay`.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/components/viz/__tests__/
git commit -m "fix(viz): remove AlgoStepperShell autoplay — V2 manual-advance-only (audit T3)"
```

---

## Task 5 (T4): AlgoStepperShell — Home/End keys (V17 keyboard parity)

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Test: AlgoStepperShell test file.

- [ ] **Step 1: Write the failing test**

```tsx
test("V17: Home jumps to first frame, End jumps to last reachable frame", () => {
  const frames = Array.from({ length: 7 }, (_, i) => ({ state: i, aria: `f${i}` }));
  render(
    <AlgoStepperShell title="t" desc="d" frames={frames}
      renderFrame={(f) => <text>{String(f.state)}</text>} testIdPrefix="he" />
  );
  const svg = screen.getByRole("img");
  fireEvent.keyDown(svg, { key: "End" });
  expect(screen.getByTestId("he-frame-counter").textContent).toContain("7 / 7");
  fireEvent.keyDown(svg, { key: "Home" });
  expect(screen.getByTestId("he-frame-counter").textContent).toContain("1 / 7");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: FAIL — Home/End unhandled, frame unchanged.

- [ ] **Step 3: Handle Home/End in `onKey`**

In the `onKey` handler, add before the closing brace:
```ts
    } else if (e.key === "Home") {
      e.preventDefault();
      setIdx(0);
    } else if (e.key === "End") {
      e.preventDefault();
      setIdx(maxReachable);
    }
```
`End` goes to `maxReachable` (not `lastIdx`) so it cannot jump past an unanswered gate.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/components/viz/__tests__/
git commit -m "feat(viz): AlgoStepperShell Home/End keys — V17 keyboard parity (audit T4)"
```

---

## Task 6 (T5): AlgoStepperShell — `initialStep` + `onStep` contract props

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Test: AlgoStepperShell test file.

**Context:** the `AlgoVizProps` contract (spec §6.4) requires `initialStep?: number` and `onStep?: (idx: number) => void`. The Shell has neither — initial index comes only from the URL hash, and the parent is never notified of frame changes (blocks the tutor "deep-link / observe viz state" integration). `liveRegionId` from the spec contract is intentionally NOT added — the Shell owns its live region internally (id `${prefix}-live`), which the audit judged better; the spec §6.4 `AlgoVizProps` block should be amended to drop `liveRegionId` (tracked, spec is untracked working-doc).

- [ ] **Step 1: Write the failing test**

```tsx
test("contract: initialStep seeds the frame, onStep fires on change", () => {
  const seen: number[] = [];
  const frames = Array.from({ length: 6 }, (_, i) => ({ state: i, aria: `f${i}` }));
  render(
    <AlgoStepperShell title="t" desc="d" frames={frames}
      renderFrame={(f) => <text>{String(f.state)}</text>}
      initialStep={3} onStep={(i) => seen.push(i)} testIdPrefix="cn" />
  );
  expect(screen.getByTestId("cn-frame-counter").textContent).toContain("4 / 6");
  fireEvent.click(screen.getByTestId("cn-step-fwd"));
  expect(seen).toContain(4);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: FAIL — `initialStep`/`onStep` not accepted; frame starts at 1/6.

- [ ] **Step 3: Add the contract props**

In `AlgoStepperShellProps<S>` add:
```ts
  initialStep?: number;
  onStep?: (idx: number) => void;
```
Change `initialIdx` so the URL hash wins, else `initialStep`, else 0:
```ts
  const initialIdx = useMemo(() => {
    const fromHash = parseHashIdx(testIdPrefix);
    const seed = fromHash ?? props.initialStep ?? 0;
    return Math.max(0, Math.min(lastIdx, seed));
  }, [testIdPrefix, lastIdx, props.initialStep]);
```
In the existing `useEffect` that calls `writeHashIdx(testIdPrefix, idx)`, add `props.onStep?.(idx);` so the parent is notified on every frame change.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix tutor-web run test -- --run AlgoStepperShell`
Expected: PASS. Run the full Shell suite + a typecheck (`npm --prefix tutor-web run build` or `npx tsc --noEmit`) — the Shell contract is now stable for all Tier 2/3 consumers.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/components/viz/__tests__/
git commit -m "feat(viz): AlgoStepperShell initialStep/onStep — AlgoVizProps contract (audit T5)"
```

---

## Tasks 7-12 (T6-T11): V12 palette fixes — yellow is focus, not category

**Shared context for all six tasks.** Spec V12: "Yellow = focus not category." V6: "hatching density for magnitude." Each of the six files encodes a *category* (job, layer, message-kind, mode) with `ACCENT` or with `opacity` cycling. The fix pattern is identical: category → `INK` fill + a `HATCH_LIGHT`/`HATCH_DENSE` pattern (from Task 2); `ACCENT` reserved strictly for the single current-focus element of the frame. Each task: read the cited region, write a test asserting the focus-only invariant, apply the fix, verify, commit.

**Test pattern (adapt the testid + frame per component).** For a Shell-based viz, render it, step to a frame with multiple categories visible, and assert at most one `fill={ACCENT}` element exists. Concretely:
```tsx
// count ACCENT-filled marks in the rendered SVG
const accentMarks = container.querySelectorAll('[fill="#facc15"]');
expect(accentMarks.length).toBeLessThanOrEqual(1); // focus only
```

### Task 7 (T6): `SchedulerGantt.tsx` — job categories via hatch

- [ ] **Step 1** — Read `SchedulerGantt.tsx` around the `jobOpacity()` function (audit cites `:520`) and its call sites.
- [ ] **Step 2** — Write failing test `tutor-web/src/components/viz/__tests__/SchedulerGantt.palette.test.tsx`: render `SchedulerGantt`, step to a frame showing ≥2 distinct jobs, assert ≤1 `[fill="#facc15"]` element and that distinct jobs use distinct `fill` values drawn from `{INK, HATCH_LIGHT, HATCH_DENSE}` (plus one dot/cross variant if a 4th job exists — add a `hatch-cross` pattern to `HatchDefs` if needed; if added, update the HatchDefs test).
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — Replace `jobOpacity()` (4-tier opacity) with a `jobFill(jobId)` returning `INK` / `HATCH_LIGHT` / `HATCH_DENSE` / 4th pattern per job. `ACCENT` only on the segment under the current-time tick. Ensure the consuming `<svg>` is Shell-based (HatchDefs already mounted by Task 2) — if `SchedulerGantt` renders its own root `<svg>` outside the Shell, add `<HatchDefs/>` inside it.
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): SchedulerGantt job categories via hatch not opacity — V12 (audit T6)"`

### Task 8 (T7): `SigmaStackedBar.tsx` — ink segments + SVG rebuild

- [ ] **Step 1** — Read `SigmaStackedBar.tsx` (82 LOC; pure HTML `<div>` bar, `segmentFor()` cycles INK/ACCENT by `i%4`, audit `:5-11`).
- [ ] **Step 2** — Write failing test `__tests__/SigmaStackedBar.test.tsx`: render it, assert the bar renders as `<svg>` (V7/V18) with real `<rect>` segments, all segments `fill={INK}` except an optionally-focused one, and `<title>`/`<desc>` present.
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — Rebuild the bar as `<svg>` with `<rect>` segments (V7/V18). Magnitude is encoded by segment width already — drop the opacity cycle, all segments `INK`. `ACCENT` only for a hovered/focused segment. Add `role="img"` + `<title>`/`<desc>`.
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): SigmaStackedBar SVG rebuild + ink segments — V7/V12 (audit T7)"`

### Task 9 (T8): `OsiEncap.tsx` — packet fill ink, accent for active only

- [ ] **Step 1** — Read `OsiEncap.tsx` around `PacketGlyph` (audit `:641` `fill={ACCENT}` unconditional) and the active-layer-row fill (`:692`).
- [ ] **Step 2** — Write failing test `__tests__/OsiEncap.palette.test.tsx`: render, step to a fragmentation frame, assert ≤1 `[fill="#facc15"]` element.
- [ ] **Step 3** — Run test: FAIL (yellow packets + yellow row).
- [ ] **Step 4** — `PacketGlyph` fill → `INK` (or `HATCH_LIGHT` keyed by header count). Reserve `ACCENT` for the packet on the active layer only. Do not also paint the active layer *row* `ACCENT` — pick one focus mark (the audit notes yellow-on-yellow loses focus).
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): OsiEncap packet fill ink, accent=focus only — V12 (audit T8)"`

### Task 10 (T9): `TcpHandshake.tsx` — message arrows not yellow-by-kind

- [ ] **Step 1** — Read `TcpHandshake.tsx` around `MessageArrow` (audit `:642-646`) and spoofed-backlog fill (`:1061`).
- [ ] **Step 2** — Write failing test `__tests__/TcpHandshake.palette.test.tsx`: step to a SYN-flood frame, assert ≤1 `[fill="#facc15"]` / `[stroke="#facc15"]` element.
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — `MessageArrow` must not draw SYN-ACK / spoofed messages `ACCENT` by kind. Encode message kind with stroke-dash pattern or a text label; reserve `ACCENT` for the single in-flight (focused) message. Spoofed backlog slot → `INK` + `HATCH_DENSE`, not `ACCENT`.
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): TcpHandshake message kind via dash not yellow — V12 (audit T9)"`

### Task 11 (T10): `TcpCwnd.tsx` — cwnd line single color, mode via hatch band

- [ ] **Step 1** — Read `TcpCwnd.tsx` around `modeColor()` (audit `:112-115`).
- [ ] **Step 2** — Write failing test `__tests__/TcpCwnd.palette.test.tsx`: render, assert the cwnd trajectory path is a single `stroke={INK}` (not switching INK↔ACCENT by mode), and ≤1 `[fill="#facc15"]` element.
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — `modeColor()` removed. The cwnd trajectory line is one `INK` stroke end-to-end. Mode regions (slow-start vs congestion-avoidance) marked with a background `HATCH_LIGHT` band or an axis annotation, not line color. `ACCENT` only on the current cwnd dot. (RC-3's missing CUBIC/BBR/bufferbloat scope is **NOT** in this task — that is Tier 4, separate plan.)
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): TcpCwnd single-color cwnd line, mode via hatch — V12 (audit T10)"`

### Task 12 (T11): `Tls0RttReplay.tsx` — message highlight not yellow-by-kind

- [ ] **Step 1** — Read `Tls0RttReplay.tsx` around the `fillColor` message field (audit `:34-39`) and `MessageRow` (`:143`).
- [ ] **Step 2** — Write failing test `__tests__/Tls0RttReplay.palette.test.tsx`: step to the replay frame, assert ≤1 `[stroke="#facc15"]` element.
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — Drop `fillColor: "accent"` as a per-message data field. Replay/danger messages: mark with a `⚠` glyph or `HATCH_DENSE`, not `ACCENT`. `ACCENT` only for the current-frame message. (Lowest-severity of the six — yellow here doubles as a legit danger cue; if review prefers keeping a danger color, document the exception in a code comment citing this task.)
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): Tls0RttReplay replay marker via glyph not yellow — V12 (audit T11)"`

---

## Tasks 13-16 (T12-T15): ARIA fixes — standalone (non-Shell) viz

**Shared context.** Four components do not use the Shell and so do not inherit its `role="img"` + `<title>`/`<desc>` + `aria-live` region. Each task adds the missing ARIA.

### Task 13 (T12): `NumLineDirect.tsx`

- [ ] **Step 1** — Read `NumLineDirect.tsx` around the root `<svg>` (audit `:85`).
- [ ] **Step 2** — Write failing test `__tests__/NumLineDirect.test.tsx`: render, assert the `<svg>` has `role="img"`, an `aria-labelledby` pointing at present `<title>`+`<desc>` ids, and an `aria-live="polite"` region exists that updates when μ is dragged.
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — Add `role="img"`, `<title id>`/`<desc id>` + `aria-labelledby`, and an off-screen `aria-live="polite"` `<div>` (mirror the Shell's pattern: `position:absolute; left:-9999`) narrating μ on each drag.
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): NumLineDirect role/title/desc/aria-live — V19/V5 (audit T12)"`

### Task 14 (T13): `SumPlotTracker.tsx`

- [ ] **Step 1** — Read `SumPlotTracker.tsx` around the `<svg>` (audit `:87`).
- [ ] **Step 2** — Write failing test `__tests__/SumPlotTracker.test.tsx`: assert `role="img"` + `<title>`/`<desc>` + `aria-labelledby`.
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — Add `role="img"` + `<title id>`/`<desc id>` + `aria-labelledby`. Display-only, so no `aria-live` needed.
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): SumPlotTracker role/title/desc — V19 (audit T13)"`

### Task 15 (T14): `CompareFrames.tsx` — ARIA + stale testid

- [ ] **Step 1** — Read `CompareFrames.tsx` (audit `:52` stale `data-testid="compare-frames-plotly"`, `:98` svg).
- [ ] **Step 2** — Write failing test `__tests__/CompareFrames.test.tsx`: assert the `<svg>` has a `<title>`/`<desc>` id pair, and that `data-testid="compare-frames"` exists (NOT `compare-frames-plotly`).
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — Add `<title id>`/`<desc id>` + `aria-labelledby`. Rename `data-testid="compare-frames-plotly"` → `compare-frames` (no Plotly here — it is a visx component). Grep the repo for `compare-frames-plotly` and update any test/selector referencing the old id.
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): CompareFrames title/desc + drop stale plotly testid — V19 (audit T14)"`

### Task 16 (T15): `SlopeCounter.tsx`

- [ ] **Step 1** — Read `SlopeCounter.tsx` (62 LOC; pure HTML readout chip).
- [ ] **Step 2** — Write failing test `__tests__/SlopeCounter.test.tsx`: assert the component exposes its slope readout to assistive tech — either `role="img"`+`aria-label` (if kept as HTML) or `role="status"` on the live readout.
- [ ] **Step 3** — Run test: FAIL.
- [ ] **Step 4** — `SlopeCounter` is a small numeric readout chip, not a diagram. Reclassify it as a UI widget: keep it HTML (acceptable — V7 "SVG over canvas" governs *diagrams*, and this is a readout), but add `role="status"` + `aria-label` on the slope value so screen readers announce it. Add a one-line code comment stating it is intentionally a non-`<svg>` readout widget.
- [ ] **Step 5** — Run test: PASS.
- [ ] **Step 6** — `git commit -m "fix(viz): SlopeCounter aria-status on slope readout (audit T15)"`

---

## Task 17 (Tier 5): Dead-code hygiene

**Files:**
- Modify: `BayesTree.tsx`, `CppVTable.tsx`, `MatrixTransform.tsx`, `ProcessFSM.tsx`

- [ ] **Step 1: Typecheck baseline**

Run: `npx --prefix tutor-web tsc --noEmit` (or `npm --prefix tutor-web run build`). Record current error count — this task must not raise it.

- [ ] **Step 2: Remove the dead code**

- `BayesTree.tsx` — remove `pctStr` (declared `:104`, `void`-ed `:786`).
- `CppVTable.tsx` — remove `_SVG_W`/`_SVG_H` dead aliases (`:359-362`); change `FRAME_COUNT` (`:352`) from the literal `15` to `FRAMES.length` (derived, not brittle).
- `MatrixTransform.tsx` — remove the `@deprecated` `liveRegionId?` prop (`:26`) from its props interface and any reference (the Shell owns the live region per Task 6; this dead prop is the underscore-dead-prop smell from CLAUDE.md).
- `ProcessFSM.tsx` — the `fsm-arrow-accent` marker (`:355-365`) has `fill={INK}`, identical to the ink marker. Either give it `fill={ACCENT}` and use it for the focused edge, or delete the duplicate marker and its references. Prefer: make it actually `ACCENT` and route the current-transition edge through it (small V12-positive win).

- [ ] **Step 3: Verify**

Run: `npx --prefix tutor-web tsc --noEmit`
Expected: error count ≤ Step 1 baseline (dead-code removal should not add errors; `MatrixTransform` prop removal must not break its call site in `VizDemoPage.tsx` — if `VizDemoPage` passes `liveRegionId`, remove that prop there too).
Run: `npm --prefix tutor-web run test -- --run` — full suite still green.

- [ ] **Step 4: Commit**

```bash
git add tutor-web/src/components/viz/
git commit -m "chore(viz): dead-code hygiene — pctStr, _SVG_W/H, deprecated liveRegionId (audit T5)"
```

---

## Task 18: Final — rebuild bundle, full suite, demo-gallery render gate

**Files:**
- Modify: `src/main/resources/tutor-dist/**` (rebuilt bundle artifacts)

- [ ] **Step 1: Full test suite**

Run: `npm --prefix tutor-web run test -- --run`
Expected: green, pass count ≥ the Task 1 baseline (new tests added across T1-T16 raise it; zero regressions).

- [ ] **Step 2: Typecheck**

Run: `npx --prefix tutor-web tsc --noEmit`
Expected: zero errors introduced beyond the Task 17 baseline.

- [ ] **Step 3: Build the bundle**

Run: `npm --prefix tutor-web run build`
Expected: clean build. Copy/sync the built assets into `src/main/resources/tutor-dist/` per the repo's existing build step (match how prior `build(viz)` commits did it — inspect commit `0b32947`).

- [ ] **Step 4: Demo-gallery render check**

Start the dev server: `npm --prefix tutor-web run dev` (serves `/tutor/viz-demo` on `:5174`).
Using Playwright headless (or the `mcp__playwright` browser tools), open `http://localhost:5174/tutor/viz-demo` and assert:
- every viz tile's `data-testid` paints on first load (`viz-demo-*` selectors per `VizDemoPage.tsx`)
- zero console errors, zero 4xx/5xx network responses on first paint
- on a Shell-based tile: no `▶ play` button present (V2); stepping with the `*-step-fwd` button advances the frame counter
- spot-check one Tier-2 tile (e.g. `SchedulerGantt`): step to a multi-job frame, confirm at most one yellow-filled mark

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/tutor-dist tutor-web
git commit -m "build(viz): rebuild bundle — Gate 1 viz-foundation compliance"
```

---

## Self-Review

**1. Spec coverage (audit T1-T15 + Tier 5).** T1→Task 2. T2→Task 3. T3→Task 4. T4→Task 5. T5→Task 6. T6→Task 7. T7→Task 8. T8→Task 9. T9→Task 10. T10→Task 11. T11→Task 12. T12→Task 13. T13→Task 14. T14→Task 15. T15→Task 16. Tier 5 hygiene→Task 17. Adopt-the-branch→Task 1. Bundle/render gate→Task 18. **Audit Tier 4 (T16-T18: prediction-gate content authoring, BayesTree draggable prior, RC-3 bufferbloat/AQM) is explicitly deferred** to a sibling plan — it is content depth + one feature build, gated on Task 3's `predictionGates` map. **Non-viz Gate 1 deliverables from spec §14** (empty states §13, error states §14, bilingual toggle §17, brutalist ruleset doc, rule-sniff Playwright assertion, RLS + cross-tenant CI) are NOT in this plan — they touch the app shell, not the viz layer, and ship as a sibling plan "Gate 1: App Shell Foundations." Spec §14 also lists "RLS + cross-tenant CI" under Gate 1, but RLS requires Postgres which spec Gate 2 introduces — flagged as a spec ordering inconsistency; RLS belongs in Gate 2.

**2. Placeholder scan.** No "TBD"/"implement later". Rework tasks (T6-T15) for files not read in full give exact file:line from the audit + a concrete test + a concrete target pattern + an explicit Step-1 "read the cited region" — concrete, not placeholder. New code (HatchDefs, theme tokens, all Shell edits) is shown verbatim because those files were read.

**3. Type consistency.** `AlgoStepperShellProps<S>` final state after Tasks 3-6: `predictionGates?: Map<number, PredictionGate>` (Task 3, replaces singular), `autoplayMsPerFrame` removed (Task 4), `initialStep?`/`onStep?` added (Task 6). `PredictionGate` type unchanged. `maxReachable`/`gateLocked`/`activeGate` introduced in Task 3 and reused by Tasks 4-5 (Home/End uses `maxReachable`) — consistent. `data-testid={`${testIdPrefix}-frame-counter`}` added in Task 3, referenced by Task 4/5/6 tests — consistent.

**4. Build+mount pairing.** One new component: `HatchDefs.tsx` (Task 2). It is mounted in `AlgoStepperShell.tsx`'s `<svg>` in the same task (Step 5) — so every Shell-based viz gets the patterns. Standalone non-Shell viz that consume hatch (`SigmaStackedBar`, Task 8) mount `<HatchDefs/>` in their own root `<svg>` — called out in Task 8 Step 4 and Task 7 Step 4. No ghost component.

**5. Component-reuse contract.** Tasks 7-12 consume the hatch patterns + the reworked Shell. Task 2 Step 5 shows the exact `<HatchDefs/>` mount JSX. Task 3 Step 3 shows the exact new `AlgoStepperShellProps` shape consuming tasks must match. Tasks 7-16 each Step-1 "read the cited region" before editing — the prop/structure of the file being modified is established before the edit. Task 17 explicitly checks the `MatrixTransform` prop removal against its `VizDemoPage` call site.

**6. `data-testid` grep.** This plan has no spec "Visual Acceptance" testid list of its own (that lives in the App Shell sibling plan + spec §3.2). The testids it introduces — `*-frame-counter` (Task 3) and `compare-frames` rename (Task 15) — are each created and consumed within this plan. Task 18 Step 4 asserts the existing `viz-demo-*` gallery testids still paint.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-20-gate1-viz-foundation.md`.
