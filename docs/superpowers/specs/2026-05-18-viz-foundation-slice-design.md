# Viz-foundation slice — design

**Date:** 2026-05-18
**Parent spec:** `docs/superpowers/specs/2026-05-17-jarvis-full-tutor-redesign-design.md` §6.4 (V1-V20 + 36-viz roster + library matrix) + §11.1 (extended deliverables 18-25) + §14 ship-gate Phase 1 "Foundations"
**Slice scope:** Production-grade viz foundation, surfaced at developer gallery `/tutor/viz-demo` only. Per-subject drill mounting is a downstream slice.

## 1. Why this slice exists

MatrixTransform shipped 2026-05-17 as a 617-LOC standalone primitive with duplicated plumbing (scrub + keyboard + ARIA + voice + predict-gate). Parent spec mandates `AlgoStepperShell.tsx` shared utility before more primitives ship (parent §6.4 line 492, §11.1 deliverable 20, §14 Phase 1). Existing 5 viz components (`NumLineDirect`, `SumPlotTracker`, `SlopeCounter`, `SigmaStackedBar`, `CompareFrames`) are brutalist-noncompliant (blue `#3b82f6` + amber `#f59e0b` + rounded pills) — parent §6.4 line 349 mandates retrofit "before more primitives ship." Library-matrix deps (motion + d3 5 + visx 5 + mafs) not yet in `package.json` (parent §11.1 deliverables 21-24). Plotly still installed (~1MB bundle hit) — parent §11.1 deliverable 25 mandates deprecation in favor of `@visx/shape` (~10KB).

## 2. Out of scope (deferred)

- Per-subject drill mounting of any viz primitive — separate later slice
- New primitives (PA-1, PA-3, SO-4, PS-2, etc.) — separate later slices; this slice ships Shell so they have a foundation to subclass
- MatrixTransform retrofit onto Shell — `MatrixTransform.tsx` keeps its current standalone plumbing this slice; retrofit deferred to whichever later slice has reason to touch it
- **Plotly removal from `package.json`** — gated on `ChatPane` + `PlotlyEmbed` migration (8 plotly-dependent files including chat envelope rendering). `<plotly>` chat-envelope feature still functional this slice; this slice only reimpls `CompareFrames` (E path A). Full plotly removal is its own future slice.
- Parent spec Phase 1 items NOT in this slice: §13 empty-state / §14 error / §17 bilingual toggle / brutalist rule-sniff Playwright / RLS + cross-tenant CI test. Those land in separate slices.

## 3. Deliverables

### D — Install library-matrix deps

**`tutor-web/package.json` adds (`dependencies`):**

```
"motion": "^12",
"d3-scale": "^4",
"d3-shape": "^3",
"d3-hierarchy": "^3",
"d3-array": "^3",
"d3-format": "^3",
"@visx/hierarchy": "^3.12",
"@visx/network": "^3.12",
"@visx/shape": "^3.12",
"@visx/scale": "^3.12",
"@visx/group": "^3.12",
"mafs": "^0.21"
```

NOTE: parent spec line 870 explicit: `motion` package, NOT deprecated `motion-one`.

**Acceptance:** `npm install` exits 0; `npm run build` + `tsc --noEmit` clean; lockfile committed. Plotly stays installed until E.

### C — AlgoStepperShell.tsx (new — `tutor-web/src/components/viz/AlgoStepperShell.tsx`)

**Responsibility (per parent §6.4 line 492 + §11.1 line 869):**

Shared utility every interactive viz subclasses. Owns the universal 6 interaction verbs (parent §6.4 line 518) — scrub / play / step / predict-gate / reduced-motion / share-link — plus ARIA narration, keyboard parity, optional voice-over.

**TypeScript interface:**

```ts
import type { ReactNode } from "react";

export type Frame<S> = {
  state: S;          // primitive-specific state at this frame
  aria: string;      // narration string for aria-live and screen readers
  meta?: Record<string, unknown>;  // optional per-frame metadata for renderFrame
};

export interface PredictionGate {
  question: string;
  answers: { label: string; isCorrect: boolean }[];
  onAnswered?: (correct: boolean) => void;
}

export interface AlgoStepperShellProps<S> {
  /** SVG <title> contents — short label. */
  title: string;
  /** SVG <desc> contents — full description for screen readers. */
  desc: string;
  /** Frame array OR generator function. Generator is materialized once on mount. */
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  /** Renders the visual SVG/HTML for a single frame. Pure function of frame + idx. */
  renderFrame: (frame: Frame<S>, idx: number) => ReactNode;
  /** Optional predict-then-reveal gate. Locks scrubber + play until answered. */
  predictionGate?: PredictionGate;
  /** Optional voice-over: map frame-idx → audio URL (mp3). Played on threshold reach. */
  voiceMap?: Record<number, string>;
  /** Autoplay frame duration in ms. Default 800. Disabled if prefers-reduced-motion. */
  autoplayMsPerFrame?: number;
  /** Optional callback when user shares — receives URL hash payload (already encoded). */
  onShare?: (hashState: string) => void;
  /** Optional data-testid prefix for the shell root (default "stepper"). */
  testIdPrefix?: string;
}
```

**Behaviour contract:**

1. **Scrubber:** `<input type="range" min=0 max={frames.length - 1} step=1 value={idx}>` with brutalist styling (theme.ts INK + ACCENT, border-radius 0). Disabled when `predictionLocked`.
2. **Play / pause / reset:** brutalist buttons. Play advances `idx` by 1 every `autoplayMsPerFrame` (default 800). Pause stops. Reset → `idx=0` + stops.
3. **Step ←/→ (and `J/K`):** Single-frame increments. ←/J = back, →/K = forward. Disabled when locked.
4. **Space:** Toggle play/pause.
5. **R:** Reset.
6. **Predict-gate:** if `predictionGate` provided, render gate UI; lock scrubber + play + step until user picks an answer; on pick, call `onAnswered(isCorrect)` + unlock. Render gate inside the controls pane.
7. **Reduced-motion:** read `window.matchMedia('(prefers-reduced-motion: reduce)').matches`; if true, autoplay disabled (play button still steps but at user-controlled cadence — clicking advances 1 frame at a time, no auto-tick).
8. **ARIA live region:** `<div aria-live="polite" role="status" class="sr-only">{frame.aria}</div>` updates on every frame change. Single shared region per shell instance.
9. **Voice-over:** if `voiceMap[idx]` set + voice toggled on, play that mp3 on frame entry (queue, don't interrupt). Voice toggle button in controls pane (default off — per `feedback_no_paid_apis` durable rule + ElevenLabs voice deferred state). When idx changes rapidly during scrub, debounce 200ms before triggering.
10. **URL hash share-link:** On every idx change (or predict-answer or scrub-end), encode state to hash like `#viz-stepper-7` (idx) — on mount, parse hash + restore idx. `onShare?` callback fires when user clicks share button (button renders full URL with the hash); copies to clipboard via `navigator.clipboard.writeText`.

**Internal layout (brutalist, matches MatrixTransform's split):**

```
<div role=group aria-labelledby={titleId} style="border:2px solid INK, padding:20, grid 1fr 260px, font JetBrains Mono, max-width 1100">
  <svg viewBox="0 0 480 360" aria-labelledby="{titleId} {descId}" tabindex=0 onKeyDown={...}>
    <title id={titleId}>{title}</title>
    <desc id={descId}>{desc}</desc>
    {renderFrame(frames[idx], idx)}
  </svg>
  <div controls-pane>
    [scrubber] [play/pause/reset] [voice toggle] [predict-gate?] [share button] [aria-live]
  </div>
</div>
```

**Generator materialization:** if `frames` is a function, call it once on first render, collect into array via `[...gen()]`. Memoize. Generators that yield infinitely or > 200 frames should be pre-truncated by caller; Shell does NOT handle infinite streams (parent §6.4 line 444 perf-ceiling: ~5000 SVG DOM nodes max).

**Pure function constraint on `renderFrame`:** must NOT call hooks, must NOT mutate frame. Return SVG / React elements only. Same frame + same idx must produce same output (enables Playwright screenshot stability).

**Acceptance:**

- File: `tutor-web/src/components/viz/AlgoStepperShell.tsx` (≤350 LOC target)
- Vitest unit file: `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx` — covers: frame stepping (idx 0→N), keyboard parity (←/→/J/K/Space/R), predict-gate lock + unlock + isCorrect propagation, share-link encode + decode round-trip, reduced-motion no-autoplay branch, voice toggle calls Audio.play, voiceMap miss is no-op, ARIA live region updates with frame.aria
- TypeScript clean (`tsc --noEmit`)
- Rendered visible at `/viz-demo` via a small `AlgoStepperShellSmoke.tsx` example (counter primitive: frame = `{ n: number }`, renderFrame draws a big number in INK; lets us eyeball Shell behaviour standalone before subclasses come online)

**MatrixTransform NOT retrofitted onto Shell this slice.** It works; touching it is deferred. Extract patterns from it (the voice-cache infra, the keyboard handler, the predict-gate UI) — reimplement inside Shell.

### B — Retrofit 4 existing components to brutalist (theme.ts swap)

Components: `NumLineDirect.tsx`, `SumPlotTracker.tsx`, `SlopeCounter.tsx`, `SigmaStackedBar.tsx`. (CompareFrames separate — full reimpl in E.)

**Per-component retrofit checklist:**

1. `import { INK, PAPER, ACCENT, FONT_FAMILY } from './theme'`
2. Replace every literal `#3b82f6` (blue) with `INK`
3. Replace every literal `#f59e0b` (amber) with `ACCENT`
4. Replace every `border-radius > 4px` with `0` (or `2px` if shape-distinguishing requires it)
5. Remove any `box-shadow` declarations
6. Remove any `linear-gradient(...)` / `radial-gradient(...)`
7. Replace any sans-serif font family with `FONT_FAMILY` (JetBrains Mono)
8. Preserve every prop in the public interface — callers unchanged
9. Preserve every existing `data-testid` — audit safety
10. Visual eyeball at `/viz-demo` (each component already mounted there or add quickly)

**Per-component commit format:** `refactor(viz): retrofit <Component> to brutalist theme.ts`

**Acceptance:**

- 4 commits, one per component
- Each existing vitest test still green (the test files in `tutor-web/src/__tests__/viz/` test render-no-throw + behaviour, not styling — retrofit shouldn't break them; if any does, the test was over-specified and gets updated)
- Each component visible at `/viz-demo` gallery (add tiles if missing)
- No new blue/amber/gradient/shadow in the diff (grep enforces)

### E — `CompareFrames.tsx` reimpl over @visx/shape (path A — trim)

**Current state:** `CompareFrames.tsx` uses `react-plotly.js` + `plotly.js-dist-min` (~1MB minified). Default chrome is blue, violates R1-R10. Parent §11.1 deliverable 25 envisioned full plotly removal but blocked by `ChatPane` + `PlotlyEmbed` still using plotly for `<plotly>` envelope rendering in chat (Phase 7-8 overhaul wire-up).

**Decision (2026-05-18):** Trim E to CompareFrames-only reimpl. **Plotly stays installed** for ChatPane envelope rendering. **Bundle savings deferred** to a future "ChatPane plotly migration" slice that will tackle rewriting `PlotlyEmbed` or replacing chat-charts.

**Target:** Reimplement `CompareFrames` over `@visx/shape` (`Line`, `Bar`, axes hand-SVG). Same prop interface, same data-testids, brutalist styling. No `package.json` changes.

**Steps:**

1. Read current `CompareFrames.tsx` + its test
2. Identify the plot type used (mean-vs-median scatter + animation)
3. Reimplement in `@visx/shape` with theme.ts colors + JetBrains Mono labels
4. Preserve prop API — callers shouldn't notice
5. Existing `CompareFrames.test.tsx` may need update if it was over-specified to Plotly DOM (likely uses `.js-plotly-plot` selector or imports plotly mock) — refactor to render-no-throw + data-testid presence assertions
6. Keep plotly imports OUT of the new `CompareFrames.tsx` — it should be plotly-free even though the package stays installed for other consumers

**Acceptance:**

- `CompareFrames.tsx` reimplemented, no plotly import in the file
- `PlotlyEmbed.tsx` + `ChatPane.tsx` + plotly deps in `package.json` unchanged
- CompareFrames vitest green (updated assertions if necessary)
- Visible at `/viz-demo`, brutalist eyeball (INK / PAPER / ACCENT only, no blue dashed line, hard edges)

**Deferred (note for future slice):** Migrate `ChatPane`'s `<plotly>` envelope rendering off plotly. Options: rewrite `PlotlyEmbed` to translate `{ data, layout }` spec → `@visx/shape` (non-trivial — plotly has many chart types); drop chat-chart feature; replace with smaller library (`uPlot` ~40KB, `@observablehq/plot`). Until this lands, plotly stays in `package.json` and the ~1MB bundle cost is paid.

## 4. Execution order (linear D → C → B → E)

| Step | Deliverable | Roughly | Reason for placement |
|------|-------------|---------|----------------------|
| 1 | D — install deps | one-shot npm install | Other steps don't need them blocking, but installing once upfront avoids partial-state |
| 2 | C — AlgoStepperShell + smoke | shell standalone | Pure new file; reads from theme.ts only; no retrofit risk |
| 3 | B — retrofit 4 components | 4 commits | Theme-only swap; can be subagent-driven (per-component independent) |
| 4 | E — CompareFrames reimpl + plotly remove | last | Largest reimpl + last because removing plotly from package.json is the highest-blast-radius step |

## 5. Out-of-band gates

Per parent spec line 60 (Slice 1 ghost-component lesson) + parent §18 visual acceptance criterion summary:

- **First-paint testids at `/tutor/viz-demo`:** every shipped component visible. Specifically: `[data-testid="viz-demo-matrix"]`, `[data-testid="viz-demo-algo-stepper-smoke"]`, `[data-testid="viz-demo-numline"]`, `[data-testid="viz-demo-sumplot"]`, `[data-testid="viz-demo-slope"]`, `[data-testid="viz-demo-sigma"]`, `[data-testid="viz-demo-compare-frames"]`.
- **Zero 4xx/5xx during first paint** (Slice 1.5 PDF-404 lesson) — `/viz-demo` is a static SPA route, no backend calls; this should be trivially satisfied.
- **Brutalist rule-sniff on `/viz-demo`:** Playwright assertion ensures no DOM element has computed-color hue in 200°-260° range AND no element has `border-radius > 4px` AND no element has `box-shadow !== 'none'` AND every font-family in allowlist `["JetBrains Mono", "JetBrains Mono Variable", "monospace"]`. Per parent §11.1 deliverable 27. (This may extend to a per-slice gate; for now, run manually before merge to keep slice-doc minimal.)

## 6. Testing strategy

- **Vitest unit:** AlgoStepperShell (frame stepping, keyboard parity, predict-gate, reduced-motion, share-link, voice). Each retrofit component's existing test re-runs green.
- **Vitest smoke:** `AlgoStepperShellSmoke.tsx` renders Shell with a counter primitive; test renders Smoke without throw + finds the scrubber + finds the readout.
- **Manual:** Open `http://localhost:5174/tutor/viz-demo` after each deliverable lands; eyeball every tile; tab through; arrow-key step; predict-gate test (where applicable).
- **No backend integration tests this slice** — pure frontend foundation, no backend touches.

## 7. Risk + mitigations

| Risk | Mitigation |
|------|-----------|
| `mafs@^0.21` peer-dep collision with react@19 | Verify on install; if mismatch, pin to mafs version compatible with React 19 or use mafs's `--legacy-peer-deps` install flag. Document in commit message if so. |
| `motion@^12` API drift from older motion-one references in research notes | Use motion v12 API explicitly: `motion.div`, `useAnimate`, etc. Verify via official docs link before first use. |
| CompareFrames test over-specified to Plotly DOM | If test grep finds `plotly` / `gl-canvas` / `plotly-graph-div` selectors, refactor test to assert prop-API + render-no-throw instead. Update commit explains rationale. |
| Retrofit accidentally changes prop API | Audit step 8 each retrofit: diff the public type signature; reject any non-additive change. |
| Plotly removal breaks a non-CompareFrames caller | Grep `from 'plotly'` / `from 'react-plotly'` across `tutor-web/src`. Currently only `CompareFrames.tsx` imports plotly per spec assumption — verify before removing from package.json. |
| MatrixTransform has duplicate plumbing after Shell ships (technical debt) | Accepted debt. Document in slice closing wrap that MatrixTransform retrofit-to-Shell is deferred. |

## 8. Visible acceptance criterion (`/tutor/viz-demo`)

After this slice ships, navigating to `http://localhost:5174/tutor/viz-demo` shows a brutalist gallery containing:

1. ALO-3 MatrixTransform (existing — unchanged this slice)
2. AlgoStepperShell counter smoke (new this slice)
3. NumLineDirect (retrofit — brutalist now)
4. SumPlotTracker (retrofit — brutalist now)
5. SlopeCounter (retrofit — brutalist now)
6. SigmaStackedBar (retrofit — brutalist now)
7. CompareFrames (reimpl over visx — brutalist now)

All on one page, all tab-navigable, all keyboard-operable for the interactive ones, all using only `INK` / `PAPER` / `ACCENT` colors + JetBrains Mono font + ≤4px border radius + hard edges + no gradients + no shadows.

## 9. Definition of done

- Single feature branch (or direct main per "stays on main branch" durable rule)
- Per-deliverable commit history (D, C, B×4, E)
- All vitest green: `npm --prefix tutor-web run test -- --run`
- `npm --prefix tutor-web run build` clean
- `/tutor/viz-demo` renders all 7 tiles, brutalist-compliant by eye
- Bundle size unchanged (or marginally larger from new deps minus modest plotly-internal cleanup — full ≥800KB drop deferred per E-trim decision)
- BRIDGE.md handoff updated with: this slice path · noted deferred ChatPane plotly migration
- No new memory files needed (existing parent spec + this slice doc cover everything; auto-memory only logs intent/exceptions per durable rules)
