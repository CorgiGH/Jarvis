# Viz-Foundation Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the brutalist-compliant viz foundation (deps + AlgoStepperShell + retrofit 4 existing viz + CompareFrames reimpl over @visx/shape) on branch `viz-foundation-demo`, visible only at `/tutor/viz-demo` developer gallery. No production drill-page integration this slice.

**Architecture:** Linear D→C→B→E execution. (D) install motion + d3 5 + @visx 5 + mafs. (C) extract `AlgoStepperShell.tsx` shared utility with generic `Frame<S>` interface, scrub/step/keyboard/play/predict-gate/share-link/voice plumbing. (B) retrofit 4 existing viz components (`NumLineDirect`, `SumPlotTracker`, `SlopeCounter`, `SigmaStackedBar`) to `theme.ts` brutalist colors + remove rounded pills/gradients. (E) reimpl `CompareFrames` over `@visx/shape` without touching plotly deps (`ChatPane` still uses plotly — deferred). All 7 components mounted in expanded `VizDemoPage` gallery.

**Tech Stack:** React 19, TypeScript 5.7, Vite 5, vitest 2.1, @testing-library/react 16, JetBrains Mono, motion@12 (new), d3@^3 sub-modules (new), @visx@^3.12 sub-packages (new), mafs@^0.21 (new), `tutor-web/src/components/viz/theme.ts` (existing).

**Branch:** `viz-foundation-demo` (already created from `main` at `09c46c4`; first commit `1393e57`). Stays on this branch; NO merge to main until separate decision.

**Spec:** `docs/superpowers/specs/2026-05-18-viz-foundation-slice-design.md` (committed `ceda3c3` + `1393e57`).

---

## File Structure

**Create:**
- `tutor-web/src/components/viz/AlgoStepperShell.tsx` — shared utility (≤350 LOC target)
- `tutor-web/src/components/viz/AlgoStepperShellSmoke.tsx` — counter primitive for gallery
- `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx` — unit test (all 6 behaviour groups)

**Modify:**
- `tutor-web/package.json` — add deps (Task 1)
- `tutor-web/src/components/viz/NumLineDirect.tsx` — theme retrofit (Task 9)
- `tutor-web/src/components/viz/SumPlotTracker.tsx` — theme retrofit (Task 10)
- `tutor-web/src/components/viz/SlopeCounter.tsx` — theme retrofit (Task 11)
- `tutor-web/src/components/viz/SigmaStackedBar.tsx` — theme retrofit (Task 12)
- `tutor-web/src/components/viz/CompareFrames.tsx` — full reimpl over @visx/shape (Task 13)
- `tutor-web/src/components/viz/VizDemoPage.tsx` — expand to multi-tile gallery (Tasks 8, 9, 10, 11, 12, 13)
- `tutor-web/src/__tests__/viz/CompareFrames.test.tsx` — update assertions if over-specified to plotly (Task 13)

**No change:** `tutor-web/src/components/viz/MatrixTransform.tsx`, `tutor-web/src/components/PlotlyEmbed.tsx`, `tutor-web/src/components/ChatPane.tsx`, all backend.

---

## Task 1: Install viz library-matrix deps (D)

**Files:**
- Modify: `tutor-web/package.json`
- Modify: `tutor-web/package-lock.json` (auto)

**Why first:** Task 13 (CompareFrames reimpl over `@visx/shape`) requires `@visx/shape` installed. Other tasks (Shell, retrofits) don't strictly need them but installing once upfront is cheaper than partial-install state.

**No new tests** — install is mechanical. Verification is `tsc --noEmit` clean + `npm run build` clean + existing test suite still green.

- [ ] **Step 1: Add deps to package.json**

Add to `tutor-web/package.json` `dependencies` (preserve sort order — alphabetical):

```json
{
  "dependencies": {
    "@visx/group": "^3.12.0",
    "@visx/hierarchy": "^3.12.0",
    "@visx/network": "^3.12.0",
    "@visx/scale": "^3.12.0",
    "@visx/shape": "^3.12.0",
    "d3-array": "^3.2.4",
    "d3-format": "^3.1.0",
    "d3-hierarchy": "^3.1.2",
    "d3-scale": "^4.0.2",
    "d3-shape": "^3.2.0",
    "katex": "^0.16.11",
    "mafs": "^0.21.0",
    "motion": "^12.0.0",
    "pdfjs-dist": "^5.7.284",
    "plotly.js-dist-min": "^3.5.1",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-pdf": "^10.4.1",
    "react-plotly.js": "^2.6.0",
    "react-router-dom": "^7.1.0"
  }
}
```

Also add to `devDependencies` (alphabetical):

```json
"@types/d3-array": "^3.2.1",
"@types/d3-format": "^3.0.4",
"@types/d3-hierarchy": "^3.1.7",
"@types/d3-scale": "^4.0.8",
"@types/d3-shape": "^3.1.6"
```

- [ ] **Step 2: Install**

```bash
cd tutor-web && npm install
```

Expected: `added N packages` exit 0. If `mafs@^0.21` peer-dep warns about React 19 mismatch, retry with `npm install --legacy-peer-deps` and note in commit message.

- [ ] **Step 3: Typecheck**

```bash
cd tutor-web && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 4: Build**

```bash
cd tutor-web && npm run build
```

Expected: build succeeds. Bundle hash will change (new deps in import map even if unused) — that's fine, NOT pushing to prod.

- [ ] **Step 5: Existing tests still green**

```bash
cd tutor-web && npm run test -- --run
```

Expected: all existing tests pass (count = whatever was passing before; deps install should not affect tests).

- [ ] **Step 6: Commit**

```bash
git add tutor-web/package.json tutor-web/package-lock.json
git commit -m "feat(viz-deps): install motion + d3 + visx + mafs per slice spec"
```

---

## Task 2: AlgoStepperShell skeleton — types + initial render (C.1)

**Files:**
- Create: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Create: `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx`

**Scope:** Type exports (`Frame<S>`, `PredictionGate`, `AlgoStepperShellProps<S>`), component skeleton that renders title/desc/SVG container + the current frame via `renderFrame`. No interaction yet (scrubber/keyboard/play come in Task 3).

- [ ] **Step 1: Write failing test — initial render shows frame 0**

Create `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx`:

```tsx
import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { AlgoStepperShell, type Frame } from "../../components/viz/AlgoStepperShell";

type CounterState = { n: number };

const counterFrames: Frame<CounterState>[] = [
  { state: { n: 0 }, aria: "count is 0" },
  { state: { n: 1 }, aria: "count is 1" },
  { state: { n: 2 }, aria: "count is 2" },
];

const renderCounter = (f: Frame<CounterState>) => (
  <text data-testid="counter-readout" x={240} y={180}>
    {f.state.n}
  </text>
);

describe("AlgoStepperShell — initial render", () => {
  test("renders title, desc, and frame 0 via renderFrame", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo counter"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    expect(screen.getByText("Counter")).toBeInTheDocument();
    expect(screen.getByText("Demo counter")).toBeInTheDocument();
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("renderFrame is called with frame at current index", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo counter"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const readout = screen.getByTestId("counter-readout");
    expect(readout).toHaveTextContent("0");
  });

  test("materializes generator function into frame array on mount", () => {
    function* gen(): Generator<Frame<CounterState>> {
      yield { state: { n: 10 }, aria: "ten" };
      yield { state: { n: 20 }, aria: "twenty" };
    }
    render(
      <AlgoStepperShell
        title="Gen counter"
        desc="Generator demo"
        frames={gen}
        renderFrame={renderCounter}
      />
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("10");
  });
});
```

- [ ] **Step 2: Run the test — verify it fails**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: FAIL with "Cannot find module" or similar (component doesn't exist yet).

- [ ] **Step 3: Implement AlgoStepperShell skeleton**

Create `tutor-web/src/components/viz/AlgoStepperShell.tsx`:

```tsx
import { useMemo, useState, type ReactNode } from "react";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export type Frame<S> = {
  state: S;
  aria: string;
  meta?: Record<string, unknown>;
};

export interface PredictionGate {
  question: string;
  answers: { label: string; isCorrect: boolean }[];
  onAnswered?: (correct: boolean) => void;
}

export interface AlgoStepperShellProps<S> {
  title: string;
  desc: string;
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  renderFrame: (frame: Frame<S>, idx: number) => ReactNode;
  predictionGate?: PredictionGate;
  voiceMap?: Record<number, string>;
  autoplayMsPerFrame?: number;
  onShare?: (hashState: string) => void;
  testIdPrefix?: string;
}

const titleIdFor = (prefix: string) => `${prefix}-title`;
const descIdFor = (prefix: string) => `${prefix}-desc`;

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") {
      return Array.from(frames());
    }
    return frames;
  }, [frames]);

  const [idx] = useState(0);
  const currentFrame = materializedFrames[idx] ?? materializedFrames[0];
  const titleId = titleIdFor(testIdPrefix);
  const descId = descIdFor(testIdPrefix);

  return (
    <div
      role="group"
      aria-labelledby={titleId}
      data-testid={`${testIdPrefix}-root`}
      style={{
        background: PAPER,
        border: `2px solid ${INK}`,
        padding: 20,
        display: "grid",
        gridTemplateColumns: "1fr 260px",
        gap: 24,
        fontFamily: FONT_FAMILY,
        color: INK,
        maxWidth: 1100,
      }}
    >
      <svg
        viewBox="0 0 480 360"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-labelledby={`${titleId} ${descId}`}
        tabIndex={0}
        style={{
          background: "#fff",
          outline: "none",
          width: "100%",
          height: "auto",
          border: `1px solid ${INK}`,
        }}
      >
        <title id={titleId}>{title}</title>
        <desc id={descId}>{desc}</desc>
        {currentFrame && renderFrame(currentFrame, idx)}
      </svg>
      <div
        data-testid={`${testIdPrefix}-controls`}
        style={{ display: "flex", flexDirection: "column", gap: 14 }}
      >
        {/* controls land in next task */}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run the test — verify pass**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 3 tests PASS.

- [ ] **Step 5: Typecheck**

```bash
cd tutor-web && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx
git commit -m "feat(viz): AlgoStepperShell skeleton — types + initial render"
```

---

## Task 3: AlgoStepperShell — scrubber + step + keyboard (C.2)

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Modify: `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx`

**Scope:** Add scrubber (`<input type=range>`), explicit step controls, and keyboard handler (←/→/J/K to step, no auto-play yet).

- [ ] **Step 1: Append failing tests for scrubber + step + keyboard**

Append to `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx`:

```tsx
import userEvent from "@testing-library/user-event";

describe("AlgoStepperShell — stepping", () => {
  test("scrubber changes frame", async () => {
    const user = userEvent.setup();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const scrubber = screen.getByTestId("stepper-scrubber") as HTMLInputElement;
    expect(scrubber.value).toBe("0");
    await user.clear(scrubber);
    scrubber.value = "2";
    scrubber.dispatchEvent(new Event("input", { bubbles: true }));
    scrubber.dispatchEvent(new Event("change", { bubbles: true }));
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
  });

  test("ArrowRight steps forward; ArrowLeft steps back", async () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    svg.focus();
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true })
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("J/K keys step (vim-style)", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "k", bubbles: true })
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "j", bubbles: true })
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("step controls clamp at boundaries (no negative idx, no idx > last)", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true })
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
    for (let i = 0; i < 10; i++) {
      svg.dispatchEvent(
        new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
      );
    }
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
  });
});
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 4 new tests FAIL (no scrubber, no keyboard handler).

- [ ] **Step 3: Implement scrubber + step + keyboard**

Replace `AlgoStepperShell.tsx` body with the augmented version. Specifically, in the component function (after `const [idx, setIdx] = useState(0);`):

```tsx
import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type ReactNode } from "react";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

// ... types unchanged ...

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") return Array.from(frames());
    return frames;
  }, [frames]);

  const lastIdx = Math.max(0, materializedFrames.length - 1);
  const [idx, setIdx] = useState(0);

  const stepBy = useCallback(
    (delta: number) => {
      setIdx((prev) => Math.max(0, Math.min(lastIdx, prev + delta)));
    },
    [lastIdx]
  );

  const setIdxClamped = useCallback(
    (next: number) => {
      setIdx(Math.max(0, Math.min(lastIdx, next)));
    },
    [lastIdx]
  );

  const onKey = useCallback(
    (e: ReactKeyboardEvent<SVGSVGElement>) => {
      const k = e.key.toLowerCase();
      if (e.key === "ArrowRight" || k === "k") {
        e.preventDefault();
        stepBy(1);
      } else if (e.key === "ArrowLeft" || k === "j") {
        e.preventDefault();
        stepBy(-1);
      }
    },
    [stepBy]
  );

  const currentFrame = materializedFrames[idx] ?? materializedFrames[0];
  const titleId = `${testIdPrefix}-title`;
  const descId = `${testIdPrefix}-desc`;

  return (
    <div
      role="group"
      aria-labelledby={titleId}
      data-testid={`${testIdPrefix}-root`}
      style={{
        background: PAPER,
        border: `2px solid ${INK}`,
        padding: 20,
        display: "grid",
        gridTemplateColumns: "1fr 260px",
        gap: 24,
        fontFamily: FONT_FAMILY,
        color: INK,
        maxWidth: 1100,
      }}
    >
      <svg
        viewBox="0 0 480 360"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-labelledby={`${titleId} ${descId}`}
        tabIndex={0}
        onKeyDown={onKey}
        style={{
          background: "#fff",
          outline: "none",
          width: "100%",
          height: "auto",
          border: `1px solid ${INK}`,
        }}
      >
        <title id={titleId}>{title}</title>
        <desc id={descId}>{desc}</desc>
        {currentFrame && renderFrame(currentFrame, idx)}
      </svg>
      <div
        data-testid={`${testIdPrefix}-controls`}
        style={{ display: "flex", flexDirection: "column", gap: 14 }}
      >
        <div>
          <div
            style={{
              fontSize: 11,
              letterSpacing: "0.08em",
              fontWeight: 700,
              textTransform: "uppercase",
              marginBottom: 4,
            }}
          >
            Frame
          </div>
          <div style={{ fontSize: 18, fontWeight: 700 }}>
            {idx + 1} / {materializedFrames.length}
          </div>
          <input
            type="range"
            min={0}
            max={lastIdx}
            step={1}
            value={idx}
            onChange={(e) => setIdxClamped(Number(e.target.value))}
            aria-label="Frame index"
            data-testid={`${testIdPrefix}-scrubber`}
            style={{ width: "100%", marginTop: 6, accentColor: INK }}
          />
        </div>
        <div style={{ display: "flex", gap: 4 }}>
          <button
            onClick={() => stepBy(-1)}
            data-testid={`${testIdPrefix}-step-back`}
            style={brutalistBtn(false)}
          >
            ◀ back
          </button>
          <button
            onClick={() => stepBy(1)}
            data-testid={`${testIdPrefix}-step-fwd`}
            style={brutalistBtn(false)}
          >
            fwd ▶
          </button>
        </div>
      </div>
    </div>
  );
}

function brutalistBtn(active: boolean): React.CSSProperties {
  return {
    background: active ? INK : PAPER,
    color: active ? ACCENT : INK,
    border: `2px solid ${INK}`,
    padding: "6px 10px",
    fontFamily: FONT_FAMILY,
    fontSize: 11,
    fontWeight: 700,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    cursor: "pointer",
  };
}
```

- [ ] **Step 4: Run tests — verify all 7 pass**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 7 tests PASS (3 from Task 2 + 4 new).

- [ ] **Step 5: Typecheck**

```bash
cd tutor-web && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx
git commit -m "feat(viz): AlgoStepperShell scrubber + step + keyboard (←→/JK)"
```

---

## Task 4: AlgoStepperShell — play/pause + reduced-motion + R (C.3)

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Modify: `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx`

**Scope:** Add play/pause button + Space toggle + R reset + reduced-motion gate (when `prefers-reduced-motion: reduce`, play steps once-per-click instead of auto-tick).

- [ ] **Step 1: Append failing tests**

```tsx
describe("AlgoStepperShell — play/pause/reset", () => {
  test("play advances frames over time", async () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        autoplayMsPerFrame={100}
      />
    );
    const playBtn = screen.getByTestId("stepper-play");
    playBtn.click();
    vi.advanceTimersByTime(105);
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    vi.advanceTimersByTime(105);
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
    vi.useRealTimers();
  });

  test("R resets to frame 0 + pauses", async () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "r", bubbles: true })
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("0");
  });

  test("Space toggles play/pause", () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        autoplayMsPerFrame={100}
      />
    );
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: " ", bubbles: true })
    );
    vi.advanceTimersByTime(105);
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: " ", bubbles: true })
    );
    vi.advanceTimersByTime(500);
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    vi.useRealTimers();
  });

  test("reduced-motion disables auto-tick (play steps once per click)", () => {
    const matchMediaSpy = vi
      .spyOn(window, "matchMedia")
      .mockImplementation((q) => ({
        matches: q === "(prefers-reduced-motion: reduce)",
        media: q,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
        onchange: null,
      }));
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        autoplayMsPerFrame={100}
      />
    );
    screen.getByTestId("stepper-play").click();
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    vi.advanceTimersByTime(500);
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("1");
    vi.useRealTimers();
    matchMediaSpy.mockRestore();
  });
});
```

Add `import { vi } from "vitest";` at the top of the test file if not already present.

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 4 new tests FAIL (no play button, no R reset, no Space toggle, no reduced-motion branch).

- [ ] **Step 3: Implement play + R + Space + reduced-motion**

In `AlgoStepperShell.tsx`, add play/pause state, R reset, Space toggle, reduced-motion read. Diff against Task 3 body:

```tsx
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type KeyboardEvent as ReactKeyboardEvent, type ReactNode } from "react";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

// ... types unchanged ...

const DEFAULT_AUTOPLAY_MS = 800;

function readReducedMotion(): boolean {
  if (typeof window === "undefined") return false;
  if (typeof window.matchMedia !== "function") return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    autoplayMsPerFrame = DEFAULT_AUTOPLAY_MS,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") return Array.from(frames());
    return frames;
  }, [frames]);

  const lastIdx = Math.max(0, materializedFrames.length - 1);
  const [idx, setIdx] = useState(0);
  const [playing, setPlaying] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reducedMotion = readReducedMotion();

  const stepBy = useCallback(
    (delta: number) => {
      setIdx((prev) => Math.max(0, Math.min(lastIdx, prev + delta)));
    },
    [lastIdx]
  );

  const setIdxClamped = useCallback(
    (next: number) => setIdx(Math.max(0, Math.min(lastIdx, next))),
    [lastIdx]
  );

  const reset = useCallback(() => {
    setIdx(0);
    setPlaying(false);
  }, []);

  const togglePlay = useCallback(() => {
    if (reducedMotion) {
      // single-step on play press, no auto-tick
      stepBy(1);
      return;
    }
    setPlaying((p) => !p);
  }, [reducedMotion, stepBy]);

  useEffect(() => {
    if (!playing || reducedMotion) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }
    intervalRef.current = setInterval(() => {
      setIdx((prev) => {
        const next = prev + 1;
        if (next > lastIdx) {
          setPlaying(false);
          return lastIdx;
        }
        return next;
      });
    }, autoplayMsPerFrame);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [playing, reducedMotion, autoplayMsPerFrame, lastIdx]);

  const onKey = useCallback(
    (e: ReactKeyboardEvent<SVGSVGElement>) => {
      const k = e.key.toLowerCase();
      if (e.key === "ArrowRight" || k === "k") {
        e.preventDefault();
        stepBy(1);
      } else if (e.key === "ArrowLeft" || k === "j") {
        e.preventDefault();
        stepBy(-1);
      } else if (e.key === " ") {
        e.preventDefault();
        togglePlay();
      } else if (k === "r") {
        e.preventDefault();
        reset();
      }
    },
    [stepBy, togglePlay, reset]
  );

  const currentFrame = materializedFrames[idx] ?? materializedFrames[0];
  const titleId = `${testIdPrefix}-title`;
  const descId = `${testIdPrefix}-desc`;

  return (
    <div
      role="group"
      aria-labelledby={titleId}
      data-testid={`${testIdPrefix}-root`}
      style={{
        background: PAPER,
        border: `2px solid ${INK}`,
        padding: 20,
        display: "grid",
        gridTemplateColumns: "1fr 260px",
        gap: 24,
        fontFamily: FONT_FAMILY,
        color: INK,
        maxWidth: 1100,
      }}
    >
      <svg
        viewBox="0 0 480 360"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-labelledby={`${titleId} ${descId}`}
        tabIndex={0}
        onKeyDown={onKey}
        style={{
          background: "#fff",
          outline: "none",
          width: "100%",
          height: "auto",
          border: `1px solid ${INK}`,
        }}
      >
        <title id={titleId}>{title}</title>
        <desc id={descId}>{desc}</desc>
        {currentFrame && renderFrame(currentFrame, idx)}
      </svg>
      <div
        data-testid={`${testIdPrefix}-controls`}
        style={{ display: "flex", flexDirection: "column", gap: 14 }}
      >
        <div>
          <div
            style={{
              fontSize: 11,
              letterSpacing: "0.08em",
              fontWeight: 700,
              textTransform: "uppercase",
              marginBottom: 4,
            }}
          >
            Frame
          </div>
          <div style={{ fontSize: 18, fontWeight: 700 }}>
            {idx + 1} / {materializedFrames.length}
          </div>
          <input
            type="range"
            min={0}
            max={lastIdx}
            step={1}
            value={idx}
            onChange={(e) => setIdxClamped(Number(e.target.value))}
            aria-label="Frame index"
            data-testid={`${testIdPrefix}-scrubber`}
            style={{ width: "100%", marginTop: 6, accentColor: INK }}
          />
        </div>
        <div style={{ display: "flex", gap: 4 }}>
          <button
            onClick={togglePlay}
            data-testid={`${testIdPrefix}-play`}
            style={brutalistBtn(playing)}
          >
            {playing ? "⏸ pause" : "▶ play"}
          </button>
          <button
            onClick={() => stepBy(-1)}
            data-testid={`${testIdPrefix}-step-back`}
            style={brutalistBtn(false)}
          >
            ◀
          </button>
          <button
            onClick={() => stepBy(1)}
            data-testid={`${testIdPrefix}-step-fwd`}
            style={brutalistBtn(false)}
          >
            ▶
          </button>
          <button
            onClick={reset}
            data-testid={`${testIdPrefix}-reset`}
            style={brutalistBtn(false)}
          >
            reset
          </button>
        </div>
        {reducedMotion && (
          <div
            data-testid={`${testIdPrefix}-reduced-motion-hint`}
            style={{ fontSize: 10, opacity: 0.6, lineHeight: 1.4 }}
          >
            Reduced-motion on · play steps once per click.
          </div>
        )}
      </div>
    </div>
  );
}

function brutalistBtn(active: boolean): CSSProperties {
  return {
    background: active ? INK : PAPER,
    color: active ? ACCENT : INK,
    border: `2px solid ${INK}`,
    padding: "6px 10px",
    fontFamily: FONT_FAMILY,
    fontSize: 11,
    fontWeight: 700,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    cursor: "pointer",
  };
}
```

- [ ] **Step 4: Run tests — verify all 11 pass**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 11 tests PASS.

- [ ] **Step 5: Typecheck + commit**

```bash
cd tutor-web && npx tsc --noEmit
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx
git commit -m "feat(viz): AlgoStepperShell play/pause + Space + R + reduced-motion"
```

---

## Task 5: AlgoStepperShell — predict-gate + share-link + ARIA live (C.4)

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Modify: `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx`

**Scope:** Predict-gate locks scrubber+play until answered; unlocks on answer; calls `onAnswered(correct)`. URL hash encode/decode (idx → `#stepper-idx-N`, restored on mount). ARIA live region updates with `frame.aria`.

- [ ] **Step 1: Append failing tests**

```tsx
describe("AlgoStepperShell — predict-gate", () => {
  test("locks scrubber + play until answered, then unlocks", () => {
    const onAnswered = vi.fn();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        predictionGate={{
          question: "Pick the right one",
          answers: [
            { label: "wrong", isCorrect: false },
            { label: "right", isCorrect: true },
          ],
          onAnswered,
        }}
      />
    );
    const scrubber = screen.getByTestId("stepper-scrubber") as HTMLInputElement;
    expect(scrubber.disabled).toBe(true);
    expect((screen.getByTestId("stepper-play") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByTestId("predict-gate")).toBeInTheDocument();
    screen.getByText("right").click();
    expect(onAnswered).toHaveBeenCalledWith(true);
    expect(scrubber.disabled).toBe(false);
    expect(screen.queryByTestId("predict-gate")).toBeNull();
  });

  test("incorrect answer still unlocks but reports isCorrect=false", () => {
    const onAnswered = vi.fn();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        predictionGate={{
          question: "Pick",
          answers: [
            { label: "wrong", isCorrect: false },
            { label: "right", isCorrect: true },
          ],
          onAnswered,
        }}
      />
    );
    screen.getByText("wrong").click();
    expect(onAnswered).toHaveBeenCalledWith(false);
    expect(screen.queryByTestId("predict-gate")).toBeNull();
  });
});

describe("AlgoStepperShell — share-link", () => {
  beforeEach(() => {
    window.location.hash = "";
  });

  test("on mount, reads idx from hash if present", () => {
    window.location.hash = "#stepper-idx-2";
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    expect(screen.getByTestId("counter-readout")).toHaveTextContent("2");
  });

  test("on step, writes idx to hash", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    expect(window.location.hash).toBe("#stepper-idx-1");
  });

  test("share button calls onShare with current hash payload", () => {
    const onShare = vi.fn();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        onShare={onShare}
      />
    );
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    screen.getByTestId("stepper-share").click();
    expect(onShare).toHaveBeenCalledWith("stepper-idx-1");
  });
});

describe("AlgoStepperShell — ARIA live", () => {
  test("live region updates with frame.aria", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
      />
    );
    const live = screen.getByTestId("stepper-live");
    expect(live).toHaveTextContent("count is 0");
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    expect(live).toHaveTextContent("count is 1");
  });
});
```

Make sure `beforeEach` is imported from vitest: `import { beforeEach, describe, expect, test, vi } from "vitest";`.

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 6 new tests FAIL.

- [ ] **Step 3: Implement predict-gate + share-link + ARIA live**

Modify `AlgoStepperShell.tsx` — add state, effects, and JSX. The full body becomes:

```tsx
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from "react";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export type Frame<S> = {
  state: S;
  aria: string;
  meta?: Record<string, unknown>;
};

export interface PredictionGate {
  question: string;
  answers: { label: string; isCorrect: boolean }[];
  onAnswered?: (correct: boolean) => void;
}

export interface AlgoStepperShellProps<S> {
  title: string;
  desc: string;
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  renderFrame: (frame: Frame<S>, idx: number) => ReactNode;
  predictionGate?: PredictionGate;
  voiceMap?: Record<number, string>;
  autoplayMsPerFrame?: number;
  onShare?: (hashState: string) => void;
  testIdPrefix?: string;
}

const DEFAULT_AUTOPLAY_MS = 800;

function readReducedMotion(): boolean {
  if (typeof window === "undefined") return false;
  if (typeof window.matchMedia !== "function") return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function parseHashIdx(prefix: string): number | null {
  if (typeof window === "undefined") return null;
  const m = window.location.hash.match(
    new RegExp(`#${prefix}-idx-(\\d+)`)
  );
  if (!m) return null;
  return Number(m[1]);
}

function writeHashIdx(prefix: string, idx: number): void {
  if (typeof window === "undefined") return;
  window.location.hash = `${prefix}-idx-${idx}`;
}

export function AlgoStepperShell<S>(props: AlgoStepperShellProps<S>) {
  const {
    title,
    desc,
    frames,
    renderFrame,
    predictionGate,
    autoplayMsPerFrame = DEFAULT_AUTOPLAY_MS,
    onShare,
    testIdPrefix = "stepper",
  } = props;

  const materializedFrames = useMemo<Frame<S>[]>(() => {
    if (typeof frames === "function") return Array.from(frames());
    return frames;
  }, [frames]);

  const lastIdx = Math.max(0, materializedFrames.length - 1);
  const initialIdx = useMemo(() => {
    const fromHash = parseHashIdx(testIdPrefix);
    if (fromHash === null) return 0;
    return Math.max(0, Math.min(lastIdx, fromHash));
  }, [testIdPrefix, lastIdx]);

  const [idx, setIdx] = useState(initialIdx);
  const [playing, setPlaying] = useState(false);
  const [predictionLocked, setPredictionLocked] = useState(!!predictionGate);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reducedMotion = readReducedMotion();

  const stepBy = useCallback(
    (delta: number) => {
      if (predictionLocked) return;
      setIdx((prev) => Math.max(0, Math.min(lastIdx, prev + delta)));
    },
    [lastIdx, predictionLocked]
  );

  const setIdxClamped = useCallback(
    (next: number) => {
      if (predictionLocked) return;
      setIdx(Math.max(0, Math.min(lastIdx, next)));
    },
    [lastIdx, predictionLocked]
  );

  const reset = useCallback(() => {
    setIdx(0);
    setPlaying(false);
  }, []);

  const togglePlay = useCallback(() => {
    if (predictionLocked) return;
    if (reducedMotion) {
      setIdx((prev) => Math.max(0, Math.min(lastIdx, prev + 1)));
      return;
    }
    setPlaying((p) => !p);
  }, [reducedMotion, predictionLocked, lastIdx]);

  useEffect(() => {
    if (!playing || reducedMotion) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }
    intervalRef.current = setInterval(() => {
      setIdx((prev) => {
        const next = prev + 1;
        if (next > lastIdx) {
          setPlaying(false);
          return lastIdx;
        }
        return next;
      });
    }, autoplayMsPerFrame);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [playing, reducedMotion, autoplayMsPerFrame, lastIdx]);

  useEffect(() => {
    writeHashIdx(testIdPrefix, idx);
  }, [idx, testIdPrefix]);

  const onKey = useCallback(
    (e: ReactKeyboardEvent<SVGSVGElement>) => {
      if (predictionLocked) return;
      const k = e.key.toLowerCase();
      if (e.key === "ArrowRight" || k === "k") {
        e.preventDefault();
        stepBy(1);
      } else if (e.key === "ArrowLeft" || k === "j") {
        e.preventDefault();
        stepBy(-1);
      } else if (e.key === " ") {
        e.preventDefault();
        togglePlay();
      } else if (k === "r") {
        e.preventDefault();
        reset();
      }
    },
    [stepBy, togglePlay, reset, predictionLocked]
  );

  const onPredict = useCallback(
    (isCorrect: boolean) => {
      setPredictionLocked(false);
      predictionGate?.onAnswered?.(isCorrect);
    },
    [predictionGate]
  );

  const onShareClick = useCallback(() => {
    onShare?.(`${testIdPrefix}-idx-${idx}`);
  }, [onShare, testIdPrefix, idx]);

  const currentFrame = materializedFrames[idx] ?? materializedFrames[0];
  const titleId = `${testIdPrefix}-title`;
  const descId = `${testIdPrefix}-desc`;

  return (
    <div
      role="group"
      aria-labelledby={titleId}
      data-testid={`${testIdPrefix}-root`}
      style={{
        background: PAPER,
        border: `2px solid ${INK}`,
        padding: 20,
        display: "grid",
        gridTemplateColumns: "1fr 260px",
        gap: 24,
        fontFamily: FONT_FAMILY,
        color: INK,
        maxWidth: 1100,
      }}
    >
      <svg
        viewBox="0 0 480 360"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-labelledby={`${titleId} ${descId}`}
        tabIndex={0}
        onKeyDown={onKey}
        style={{
          background: "#fff",
          outline: "none",
          width: "100%",
          height: "auto",
          border: `1px solid ${INK}`,
        }}
      >
        <title id={titleId}>{title}</title>
        <desc id={descId}>{desc}</desc>
        {currentFrame && renderFrame(currentFrame, idx)}
      </svg>
      <div
        data-testid={`${testIdPrefix}-controls`}
        style={{ display: "flex", flexDirection: "column", gap: 14 }}
      >
        <div>
          <div
            style={{
              fontSize: 11,
              letterSpacing: "0.08em",
              fontWeight: 700,
              textTransform: "uppercase",
              marginBottom: 4,
            }}
          >
            Frame
          </div>
          <div style={{ fontSize: 18, fontWeight: 700 }}>
            {idx + 1} / {materializedFrames.length}
          </div>
          <input
            type="range"
            min={0}
            max={lastIdx}
            step={1}
            value={idx}
            disabled={predictionLocked}
            onChange={(e) => setIdxClamped(Number(e.target.value))}
            aria-label="Frame index"
            data-testid={`${testIdPrefix}-scrubber`}
            style={{ width: "100%", marginTop: 6, accentColor: INK }}
          />
        </div>
        <div style={{ display: "flex", gap: 4 }}>
          <button
            onClick={togglePlay}
            disabled={predictionLocked}
            data-testid={`${testIdPrefix}-play`}
            style={brutalistBtn(playing)}
          >
            {playing ? "⏸ pause" : "▶ play"}
          </button>
          <button
            onClick={() => stepBy(-1)}
            disabled={predictionLocked}
            data-testid={`${testIdPrefix}-step-back`}
            style={brutalistBtn(false)}
          >
            ◀
          </button>
          <button
            onClick={() => stepBy(1)}
            disabled={predictionLocked}
            data-testid={`${testIdPrefix}-step-fwd`}
            style={brutalistBtn(false)}
          >
            ▶
          </button>
          <button
            onClick={reset}
            data-testid={`${testIdPrefix}-reset`}
            style={brutalistBtn(false)}
          >
            reset
          </button>
        </div>
        <button
          onClick={onShareClick}
          data-testid={`${testIdPrefix}-share`}
          style={brutalistBtn(false)}
        >
          🔗 share
        </button>
        {reducedMotion && (
          <div
            data-testid={`${testIdPrefix}-reduced-motion-hint`}
            style={{ fontSize: 10, opacity: 0.6, lineHeight: 1.4 }}
          >
            Reduced-motion on · play steps once per click.
          </div>
        )}
        {predictionGate && predictionLocked && (
          <div
            data-testid="predict-gate"
            style={{ border: `2px solid ${INK}`, padding: 10, background: PAPER }}
          >
            <div
              style={{
                fontSize: 11,
                letterSpacing: "0.08em",
                fontWeight: 700,
                textTransform: "uppercase",
                marginBottom: 6,
              }}
            >
              ⚡ Predict
            </div>
            <div style={{ fontSize: 11, lineHeight: 1.5 }}>
              {predictionGate.question}
            </div>
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 4,
                marginTop: 8,
              }}
            >
              {predictionGate.answers.map((a, i) => (
                <button
                  key={i}
                  onClick={() => onPredict(a.isCorrect)}
                  style={{
                    background: PAPER,
                    color: INK,
                    border: `1px solid ${INK}`,
                    padding: "4px 8px",
                    fontFamily: FONT_FAMILY,
                    fontSize: 10,
                    letterSpacing: "0.06em",
                    textTransform: "uppercase",
                    cursor: "pointer",
                  }}
                >
                  {a.label}
                </button>
              ))}
            </div>
          </div>
        )}
        <div
          aria-live="polite"
          role="status"
          data-testid={`${testIdPrefix}-live`}
          style={{ position: "absolute", left: -9999 }}
        >
          {currentFrame?.aria ?? ""}
        </div>
      </div>
    </div>
  );
}

function brutalistBtn(active: boolean): CSSProperties {
  return {
    background: active ? INK : PAPER,
    color: active ? ACCENT : INK,
    border: `2px solid ${INK}`,
    padding: "6px 10px",
    fontFamily: FONT_FAMILY,
    fontSize: 11,
    fontWeight: 700,
    letterSpacing: "0.06em",
    textTransform: "uppercase",
    cursor: "pointer",
  };
}
```

- [ ] **Step 4: Run tests — verify all 17 pass**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 17 tests PASS.

- [ ] **Step 5: Typecheck + commit**

```bash
cd tutor-web && npx tsc --noEmit
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx
git commit -m "feat(viz): AlgoStepperShell predict-gate + share-link + ARIA live"
```

---

## Task 6: AlgoStepperShell — voice toggle + playback (C.5)

**Files:**
- Modify: `tutor-web/src/components/viz/AlgoStepperShell.tsx`
- Modify: `tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx`

**Scope:** Voice toggle button. When voice ON and `voiceMap[idx]` defined, play that mp3 (queue if already playing). Debounce 200ms during rapid scrubbing.

- [ ] **Step 1: Append failing tests**

```tsx
describe("AlgoStepperShell — voice", () => {
  let originalAudio: typeof Audio;
  const audioInstances: Array<{ src: string | null; played: boolean; paused: boolean }> = [];

  beforeEach(() => {
    audioInstances.length = 0;
    originalAudio = window.Audio;
    window.Audio = class FakeAudio {
      _src: string | null = null;
      played = false;
      paused = true;
      onended: (() => void) | null = null;
      currentTime = 0;
      constructor() {
        const inst = { src: null as string | null, played: false, paused: true };
        audioInstances.push(inst);
        Object.defineProperty(this, "src", {
          get: () => inst.src,
          set: (v: string) => { inst.src = v; },
        });
        Object.defineProperty(this, "paused", { get: () => inst.paused });
        this.play = () => {
          inst.played = true;
          inst.paused = false;
          this.played = true;
          this.paused = false;
          return Promise.resolve();
        };
        this.pause = () => {
          inst.paused = true;
          this.paused = true;
        };
      }
      play: () => Promise<void>;
      pause: () => void;
    } as unknown as typeof Audio;
  });

  afterEach(() => {
    window.Audio = originalAudio;
  });

  test("voice off by default", () => {
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        voiceMap={{ 0: "/a.mp3", 1: "/b.mp3" }}
      />
    );
    const voiceBtn = screen.getByTestId("stepper-voice") as HTMLButtonElement;
    expect(voiceBtn.textContent?.toLowerCase()).toContain("off");
  });

  test("voice on + frame change triggers Audio.play with voiceMap url", async () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        voiceMap={{ 0: "/a.mp3", 1: "/b.mp3" }}
      />
    );
    screen.getByTestId("stepper-voice").click();
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    vi.advanceTimersByTime(250);
    expect(audioInstances.some((a) => a.src === "/b.mp3" && a.played)).toBe(true);
    vi.useRealTimers();
  });

  test("voiceMap miss is a no-op", () => {
    vi.useFakeTimers();
    render(
      <AlgoStepperShell
        title="Counter"
        desc="Demo"
        frames={counterFrames}
        renderFrame={renderCounter}
        voiceMap={{ 0: "/a.mp3" }}
      />
    );
    screen.getByTestId("stepper-voice").click();
    const svg = screen.getByRole("img");
    svg.dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true })
    );
    vi.advanceTimersByTime(250);
    expect(audioInstances.some((a) => a.src === "/b.mp3")).toBe(false);
    vi.useRealTimers();
  });
});
```

Add `afterEach` to imports if not present.

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 3 new tests FAIL.

- [ ] **Step 3: Implement voice toggle + playback + debounce**

In `AlgoStepperShell.tsx`, add voice state + audio ref + debounce-effect. Insert after the existing `useEffect` blocks (around the `useEffect` for hash writing):

```tsx
// After existing useEffects:

const [voiceOn, setVoiceOn] = useState(false);
const audioRef = useRef<HTMLAudioElement | null>(null);
const pendingNextSrcRef = useRef<string | null>(null);
const voiceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

const playSrc = useCallback((src: string) => {
  if (!src) return;
  let a = audioRef.current;
  if (!a) {
    a = new Audio();
    a.onended = () => {
      if (pendingNextSrcRef.current) {
        const next = pendingNextSrcRef.current;
        pendingNextSrcRef.current = null;
        a!.src = next;
        a!.play().catch(() => {});
      }
    };
    audioRef.current = a;
  }
  if (a.paused) {
    a.src = src;
    a.play().catch(() => {});
  } else {
    pendingNextSrcRef.current = src;
  }
}, []);

useEffect(() => {
  if (!voiceOn) {
    if (voiceTimerRef.current) clearTimeout(voiceTimerRef.current);
    return;
  }
  const src = props.voiceMap?.[idx];
  if (!src) return;
  if (voiceTimerRef.current) clearTimeout(voiceTimerRef.current);
  voiceTimerRef.current = setTimeout(() => playSrc(src), 200);
  return () => {
    if (voiceTimerRef.current) clearTimeout(voiceTimerRef.current);
  };
}, [voiceOn, idx, props.voiceMap, playSrc]);

useEffect(() => {
  if (!voiceOn && audioRef.current) {
    audioRef.current.pause();
    audioRef.current.currentTime = 0;
    pendingNextSrcRef.current = null;
  }
  return () => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }
  };
}, [voiceOn]);
```

Then in the controls JSX (after the share button), add voice toggle:

```tsx
<button
  onClick={() => setVoiceOn((v) => !v)}
  data-testid={`${testIdPrefix}-voice`}
  style={{ ...brutalistBtn(voiceOn) }}
>
  {voiceOn ? "🔊 voice on" : "🔇 voice off"}
</button>
```

NOTE: `props.voiceMap` rather than destructured `voiceMap` keeps the effect closure stable across renders.

- [ ] **Step 4: Run tests — verify all 20 pass**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/AlgoStepperShell.test.tsx
```

Expected: 20 tests PASS.

- [ ] **Step 5: Typecheck + commit**

```bash
cd tutor-web && npx tsc --noEmit
git add tutor-web/src/components/viz/AlgoStepperShell.tsx tutor-web/src/__tests__/viz/AlgoStepperShell.test.tsx
git commit -m "feat(viz): AlgoStepperShell voice toggle + debounced playback"
```

---

## Task 7: AlgoStepperShellSmoke + VizDemoPage gallery refactor (C.6)

**Files:**
- Create: `tutor-web/src/components/viz/AlgoStepperShellSmoke.tsx`
- Modify: `tutor-web/src/components/viz/VizDemoPage.tsx`

**Scope:** Build smoke component (counter primitive subclassing Shell). Refactor VizDemoPage into a multi-tile gallery with wrapper `<section data-testid="viz-demo-*">` per tile. Wrap existing MatrixTransform with `viz-demo-matrix` testid. Add smoke tile with `viz-demo-algo-stepper-smoke` testid.

- [ ] **Step 1: Create smoke component**

Create `tutor-web/src/components/viz/AlgoStepperShellSmoke.tsx`:

```tsx
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK } from "./theme";

type CounterState = { n: number };

const frames: Frame<CounterState>[] = Array.from({ length: 8 }, (_, i) => ({
  state: { n: i },
  aria: `Counter at ${i}`,
}));

function renderCounter(f: Frame<CounterState>): React.ReactNode {
  return (
    <>
      <text
        x={240}
        y={150}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
        opacity={0.6}
      >
        COUNTER
      </text>
      <text
        x={240}
        y={210}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={84}
        fontWeight={900}
        fill={INK}
      >
        {f.state.n}
      </text>
      <rect
        x={140}
        y={250}
        width={(f.state.n / 7) * 200}
        height={12}
        fill={ACCENT}
        stroke={INK}
        strokeWidth={1}
      />
      <rect
        x={140}
        y={250}
        width={200}
        height={12}
        fill="none"
        stroke={INK}
        strokeWidth={1}
      />
    </>
  );
}

export function AlgoStepperShellSmoke() {
  return (
    <AlgoStepperShell
      title="AlgoStepperShell smoke — counter"
      desc="Subclass demo: counter 0..7 with brutalist progress bar"
      frames={frames}
      renderFrame={renderCounter}
      testIdPrefix="stepper-smoke"
    />
  );
}
```

- [ ] **Step 2: Refactor VizDemoPage**

Replace `tutor-web/src/components/viz/VizDemoPage.tsx` with:

```tsx
import { AlgoStepperShellSmoke } from "./AlgoStepperShellSmoke";
import { MatrixTransform } from "./MatrixTransform";
import { FONT_FAMILY, INK, PAPER } from "./theme";

const tileStyle: React.CSSProperties = {
  marginBottom: 48,
};

const headingStyle: React.CSSProperties = {
  margin: "0 0 8px",
  fontSize: 14,
  fontWeight: 900,
  letterSpacing: "0.06em",
  textTransform: "uppercase",
};

const subheadingStyle: React.CSSProperties = {
  margin: "0 0 16px",
  fontSize: 11,
  opacity: 0.7,
  letterSpacing: "0.04em",
};

export function VizDemoPage() {
  return (
    <div
      data-testid="viz-demo-page"
      style={{
        background: PAPER,
        minHeight: "100vh",
        padding: 32,
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <header style={{ marginBottom: 32 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900 }}>
          ⭐ VIZ DEMO GALLERY
        </h1>
        <p style={{ margin: "4px 0 0", fontSize: 12, opacity: 0.7 }}>
          Brutalist mono · viz-foundation-demo branch · NOT a production drill flow
        </p>
      </header>

      <section data-testid="viz-demo-matrix" style={tileStyle}>
        <h2 style={headingStyle}>ALO-3 · Eigenvector demo (MatrixTransform)</h2>
        <p style={subheadingStyle}>
          Standalone primitive (not yet on Shell). Scrubber + prediction gate + voice cache.
        </p>
        <MatrixTransform
          target={[
            [2, 1],
            [1, 2],
          ]}
          eigenvectors={[
            { v: [1 / Math.sqrt(2), 1 / Math.sqrt(2)], lambda: 3 },
            { v: [1 / Math.sqrt(2), -1 / Math.sqrt(2)], lambda: 1 },
          ]}
          w={[0.8, 0.3]}
          testRayCount={8}
          predictionGate={{
            question:
              "Which colored ray stays in the same direction after applying M?",
            answers: [
              { label: "gray test ray", isCorrect: false },
              { label: "yellow eigenvector", isCorrect: true },
              { label: "black w (non-eig)", isCorrect: false },
            ],
          }}
          liveRegionId="viz-live"
        />
      </section>

      <section data-testid="viz-demo-algo-stepper-smoke" style={tileStyle}>
        <h2 style={headingStyle}>AlgoStepperShell smoke — counter</h2>
        <p style={subheadingStyle}>
          Subclass demo · 8 frames · scrubber + keyboard + Space + R + share + voice (no audio map)
        </p>
        <AlgoStepperShellSmoke />
      </section>
    </div>
  );
}
```

- [ ] **Step 3: Start dev server + manual eyeball**

```bash
cd tutor-web && npm run dev
```

Open `http://localhost:5174/tutor/viz-demo`. Verify:
- Page renders with brutalist header
- `viz-demo-matrix` section shows MatrixTransform working as before
- `viz-demo-algo-stepper-smoke` section shows counter, scrubber moves, ←/→/Space/R work, share button works
- No console errors

Stop the dev server (Ctrl+C) after eyeball.

- [ ] **Step 4: Run all tests**

```bash
cd tutor-web && npm run test -- --run
```

Expected: all tests pass (existing + 20 new from AlgoStepperShell).

- [ ] **Step 5: Typecheck + build**

```bash
cd tutor-web && npx tsc --noEmit && npm run build
```

Expected: 0 errors, build succeeds.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/viz/AlgoStepperShellSmoke.tsx tutor-web/src/components/viz/VizDemoPage.tsx
git commit -m "feat(viz): AlgoStepperShellSmoke + VizDemoPage gallery (matrix + smoke tiles)"
```

---

## Task 8: Retrofit NumLineDirect + mount in gallery (B.1)

**Files:**
- Modify: `tutor-web/src/components/viz/NumLineDirect.tsx`
- Modify: `tutor-web/src/components/viz/VizDemoPage.tsx`

**Scope:** Replace `var(--color-accent, #3b82f6)` with `ACCENT` from theme.ts. Replace `stroke="white"` (marker stroke) with `stroke={INK}`. No prop changes. Mount in gallery with `viz-demo-numline` testid.

- [ ] **Step 1: Retrofit NumLineDirect**

Replace `tutor-web/src/components/viz/NumLineDirect.tsx` body so it imports from theme.ts and uses INK/ACCENT directly:

```tsx
import { useRef, useCallback, useEffect } from "react";
import { ACCENT, INK } from "./theme";

export interface NumLineDirectProps {
  data: number[];
  mu: number;
  onMu: (v: number) => void;
  min?: number;
  max?: number;
}

const SVG_W = 480;
const SVG_H = 80;
const PAD = 24;
const USABLE = SVG_W - PAD * 2;
const TICK_H = 14;
const AXIS_Y = 50;
const MARKER_R = 9;

function toSvgX(v: number, lo: number, hi: number): number {
  return PAD + ((v - lo) / (hi - lo)) * USABLE;
}
function fromSvgX(x: number, lo: number, hi: number): number {
  const clamped = Math.max(PAD, Math.min(SVG_W - PAD, x));
  return lo + ((clamped - PAD) / USABLE) * (hi - lo);
}

export function NumLineDirect({ data, mu, onMu, min, max }: NumLineDirectProps) {
  const lo = min ?? Math.min(...data) - 2;
  const hi = max ?? Math.max(...data) + 2;
  const markerRef = useRef<SVGCircleElement>(null);
  const muRef = useRef(mu);
  muRef.current = mu;
  const dragging = useRef(false);
  const rafId = useRef<number>(0);
  const pendingX = useRef<number | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  const prefersReduced = typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  useEffect(() => {
    if (!markerRef.current) return;
    markerRef.current.setAttribute("cx", String(toSvgX(mu, lo, hi)));
  }, [mu, lo, hi]);

  const flushRAF = useCallback(() => {
    if (pendingX.current === null) return;
    const svgEl = svgRef.current;
    const rect = svgEl ? svgEl.getBoundingClientRect() : { left: 0 };
    const svgX = pendingX.current - rect.left;
    const newMu = parseFloat(fromSvgX(svgX, lo, hi).toFixed(3));
    pendingX.current = null;
    if (Math.abs(newMu - muRef.current) >= 0.05) onMu(newMu);
  }, [lo, hi, onMu]);

  const onPointerDown = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragging.current = true;
  }, []);

  const onPointerMove = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    if (!dragging.current) return;
    const svgEl = svgRef.current;
    const rect = svgEl ? svgEl.getBoundingClientRect() : { left: 0 };
    const rawSvgX = e.clientX - rect.left;
    const currentX = toSvgX(muRef.current, lo, hi);
    if (Math.abs(rawSvgX - currentX) < 1) return;
    pendingX.current = e.clientX;
    if (prefersReduced) flushRAF();
    else {
      cancelAnimationFrame(rafId.current);
      rafId.current = requestAnimationFrame(flushRAF);
    }
  }, [flushRAF, lo, prefersReduced]);

  const onPointerUp = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    e.currentTarget.releasePointerCapture(e.pointerId);
    dragging.current = false;
    cancelAnimationFrame(rafId.current);
    if (pendingX.current !== null) flushRAF();
  }, [flushRAF]);

  return (
    <svg
      ref={svgRef}
      width={SVG_W}
      height={SVG_H}
      viewBox={`0 0 ${SVG_W} ${SVG_H}`}
      data-testid="num-line-direct"
      style={{ userSelect: "none", touchAction: "none" }}
    >
      <line x1={PAD} y1={AXIS_Y} x2={SVG_W - PAD} y2={AXIS_Y} stroke={INK} strokeWidth={1.5} opacity={0.35} />
      {data.map((v, i) => (
        <line
          key={i}
          data-testid="sample-tick"
          x1={toSvgX(v, lo, hi)} y1={AXIS_Y - TICK_H / 2}
          x2={toSvgX(v, lo, hi)} y2={AXIS_Y + TICK_H / 2}
          stroke={INK} strokeWidth={2} opacity={0.6}
        />
      ))}
      <circle
        ref={markerRef}
        data-testid="mu-marker"
        cx={toSvgX(mu, lo, hi)} cy={AXIS_Y} r={MARKER_R}
        fill={ACCENT} stroke={INK} strokeWidth={2}
        style={{ cursor: "ew-resize" }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
      />
      <text x={toSvgX(mu, lo, hi)} y={AXIS_Y - MARKER_R - 4} textAnchor="middle" fontSize={11} fill={INK} fontWeight="bold">μ</text>
    </svg>
  );
}
```

- [ ] **Step 2: Mount in VizDemoPage gallery**

In `tutor-web/src/components/viz/VizDemoPage.tsx`, add a new section after the smoke tile:

```tsx
import { useState } from "react";
import { NumLineDirect } from "./NumLineDirect";

// inside VizDemoPage component:
const [numLineMu, setNumLineMu] = useState(5);

// before closing </div>, after <AlgoStepperShellSmoke/> section:
<section data-testid="viz-demo-numline" style={tileStyle}>
  <h2 style={headingStyle}>NumLineDirect (PS basis)</h2>
  <p style={subheadingStyle}>
    Drag μ marker · 7 sample ticks · retrofit theme · drag-RAF flow
  </p>
  <NumLineDirect
    data={[1, 3, 4, 5, 6, 8, 11]}
    mu={numLineMu}
    onMu={setNumLineMu}
  />
</section>
```

- [ ] **Step 3: Run existing NumLineDirect test**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/NumLineDirect.test.tsx
```

Expected: PASS (test asserts render-no-throw + marker movement; styling change shouldn't break).

If test fails because it asserted on `#3b82f6` blue or `fill="white"` literally, update the assertion to match the new INK/ACCENT values from theme.ts.

- [ ] **Step 4: Manual eyeball at /viz-demo**

Start dev server, open `http://localhost:5174/tutor/viz-demo`, verify:
- NumLineDirect tile renders in `viz-demo-numline` section
- Marker is yellow (ACCENT) with black (INK) border, not blue
- Tick marks are black, not their old color
- Drag works, μ updates

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/NumLineDirect.tsx tutor-web/src/components/viz/VizDemoPage.tsx
git commit -m "refactor(viz): retrofit NumLineDirect to brutalist theme.ts + mount in gallery"
```

---

## Task 9: Retrofit SumPlotTracker + mount in gallery (B.2)

**Files:**
- Modify: `tutor-web/src/components/viz/SumPlotTracker.tsx`
- Modify: `tutor-web/src/components/viz/VizDemoPage.tsx`

**Scope:** Replace `var(--color-accent, #3b82f6)` with `ACCENT` and `stroke="currentColor"` axes with `stroke={INK}`. Replace `stroke="white"` marker stroke with `stroke={INK}`. Mount with `viz-demo-sumplot` testid.

- [ ] **Step 1: Retrofit**

Replace the body of `SumPlotTracker.tsx` so it imports theme + uses INK/ACCENT:

```tsx
import { useRef, useEffect } from "react";
import { ACCENT, INK } from "./theme";

export interface SumPlotTrackerProps {
  data: number[];
  mu: number;
  range?: [number, number];
}

const SVG_W = 480, SVG_H = 200;
const PAD_L = 48, PAD_R = 16, PAD_T = 16, PAD_B = 32;
const USABLE_W = SVG_W - PAD_L - PAD_R;
const USABLE_H = SVG_H - PAD_T - PAD_B;
const STEPS = 240;

function sumAbsDev(mu: number, data: number[]): number {
  return data.reduce((acc, x) => acc + Math.abs(x - mu), 0);
}

interface CurvePoint { svgX: number; svgY: number; mu: number; }

function buildCurve(data: number[], lo: number, hi: number): CurvePoint[] {
  const pts: CurvePoint[] = [];
  const yValues: number[] = [];
  for (let i = 0; i <= STEPS; i++) yValues.push(sumAbsDev(lo + (i / STEPS) * (hi - lo), data));
  const yMin = Math.min(...yValues), yMax = Math.max(...yValues);
  const yRange = yMax - yMin || 1;
  for (let i = 0; i <= STEPS; i++) {
    const mu = lo + (i / STEPS) * (hi - lo);
    pts.push({
      svgX: PAD_L + (i / STEPS) * USABLE_W,
      svgY: PAD_T + USABLE_H - ((yValues[i] - yMin) / yRange) * USABLE_H,
      mu,
    });
  }
  return pts;
}

function pathD(pts: CurvePoint[]): string {
  if (pts.length === 0) return "";
  return pts.map((p, i) => `${i === 0 ? "M" : "L"}${p.svgX.toFixed(2)},${p.svgY.toFixed(2)}`).join(" ");
}

function interpolateMarker(mu: number, pts: CurvePoint[]): { x: number; y: number } {
  if (pts.length === 0) return { x: 0, y: 0 };
  if (mu <= pts[0].mu) return { x: pts[0].svgX, y: pts[0].svgY };
  if (mu >= pts[pts.length - 1].mu) return { x: pts[pts.length - 1].svgX, y: pts[pts.length - 1].svgY };
  let lo = 0, hi = pts.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (pts[mid].mu <= mu) lo = mid; else hi = mid;
  }
  const t = (mu - pts[lo].mu) / (pts[hi].mu - pts[lo].mu);
  return {
    x: pts[lo].svgX + t * (pts[hi].svgX - pts[lo].svgX),
    y: pts[lo].svgY + t * (pts[hi].svgY - pts[lo].svgY),
  };
}

export function SumPlotTracker({ data, mu, range }: SumPlotTrackerProps) {
  const lo = range?.[0] ?? Math.min(...data) - 1;
  const hi = range?.[1] ?? Math.max(...data) + 1;
  const curve = buildCurve(data, lo, hi);
  const pathStr = pathD(curve);

  const markerRef = useRef<SVGCircleElement>(null);
  const muRef = useRef(mu);
  const rafId = useRef<number>(0);

  useEffect(() => {
    const prevMu = muRef.current;
    muRef.current = mu;
    if (Math.abs(mu - prevMu) < 0.001) return;
    cancelAnimationFrame(rafId.current);
    rafId.current = requestAnimationFrame(() => {
      if (!markerRef.current) return;
      const pos = interpolateMarker(mu, curve);
      markerRef.current.setAttribute("cx", pos.x.toFixed(2));
      markerRef.current.setAttribute("cy", pos.y.toFixed(2));
    });
    return () => cancelAnimationFrame(rafId.current);
  }, [mu, curve]);

  const initialPos = interpolateMarker(mu, curve);

  return (
    <svg width={SVG_W} height={SVG_H} viewBox={`0 0 ${SVG_W} ${SVG_H}`} data-testid="sum-plot-tracker">
      <line x1={PAD_L} y1={PAD_T} x2={PAD_L} y2={PAD_T + USABLE_H} stroke={INK} strokeWidth={1} opacity={0.3} />
      <line x1={PAD_L} y1={PAD_T + USABLE_H} x2={PAD_L + USABLE_W} y2={PAD_T + USABLE_H} stroke={INK} strokeWidth={1} opacity={0.3} />
      <text data-testid="sum-axis-label" x={10} y={PAD_T + USABLE_H / 2} fontSize={10} textAnchor="middle" transform={`rotate(-90, 10, ${PAD_T + USABLE_H / 2})`} fill={INK} opacity={0.6}>Σ|xᵢ − μ|</text>
      <path data-testid="sum-curve" d={pathStr} fill="none" stroke={INK} strokeWidth={2} />
      <circle ref={markerRef} data-testid="sum-marker" cx={initialPos.x} cy={initialPos.y} r={6} fill={ACCENT} stroke={INK} strokeWidth={2} />
    </svg>
  );
}
```

- [ ] **Step 2: Mount in VizDemoPage**

In `VizDemoPage.tsx`, add import + tile after the NumLine tile:

```tsx
import { SumPlotTracker } from "./SumPlotTracker";

// after viz-demo-numline section:
<section data-testid="viz-demo-sumplot" style={tileStyle}>
  <h2 style={headingStyle}>SumPlotTracker (PS basis)</h2>
  <p style={subheadingStyle}>
    Σ|xᵢ − μ| curve · marker tracks μ · brutalist mono
  </p>
  <SumPlotTracker
    data={[1, 3, 4, 5, 6, 8, 11]}
    mu={numLineMu}
  />
</section>
```

- [ ] **Step 3: Run existing test**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/SumPlotTracker.test.tsx
```

Expected: PASS. If it asserted on blue literally, update.

- [ ] **Step 4: Eyeball**

Verify at `/viz-demo` that SumPlotTracker tile renders, curve is black (INK), marker is yellow (ACCENT) with black stroke.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/SumPlotTracker.tsx tutor-web/src/components/viz/VizDemoPage.tsx
git commit -m "refactor(viz): retrofit SumPlotTracker to brutalist + mount in gallery"
```

---

## Task 10: Retrofit SlopeCounter + mount (B.3)

**Files:**
- Modify: `tutor-web/src/components/viz/SlopeCounter.tsx`
- Modify: `tutor-web/src/components/viz/VizDemoPage.tsx`

**Scope:** Replace rounded chips (`borderRadius: "9999px"`) with `border: 2px solid INK; borderRadius: 0`. Replace blue + amber chip backgrounds with `INK`/`ACCENT` + appropriate text contrast. Replace `var(--font-mono, monospace)` with `FONT_FAMILY`.

- [ ] **Step 1: Retrofit SlopeCounter**

Replace `tutor-web/src/components/viz/SlopeCounter.tsx`:

```tsx
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export interface SlopeCounterProps { data: number[]; mu: number; }

const chipBase: React.CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: "0.25rem",
  padding: "0.25em 0.6em",
  border: `2px solid ${INK}`,
  borderRadius: 0,
  fontSize: "0.8rem",
  fontWeight: 700,
  fontFamily: FONT_FAMILY,
  letterSpacing: "0.06em",
  textTransform: "uppercase",
};

export function SlopeCounter({ data, mu }: SlopeCounterProps) {
  const left = data.filter((x) => x < mu).length;
  const right = data.filter((x) => x > mu).length;
  const diff = left - right;

  const diffColor = diff > 0 || diff < 0 ? INK : INK;

  return (
    <div
      data-testid="slope-counter"
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: "0.5rem",
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
        <span
          data-testid="slope-left-chip"
          style={{ ...chipBase, background: PAPER, color: INK }}
        >
          LEFT: {left}
        </span>
        <span
          data-testid="slope-right-chip"
          style={{ ...chipBase, background: ACCENT, color: INK }}
        >
          RIGHT: {right}
        </span>
      </div>
      <div style={{ fontSize: "0.75rem", opacity: 0.7, marginTop: "0.1rem" }}>
        slope ={" "}
        <span
          data-testid="slope-diff"
          style={{ fontWeight: 900, color: diffColor }}
        >
          {diff >= 0 ? `+${diff}` : `${diff}`}
        </span>{" "}
        ({left} − {right})
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Mount in VizDemoPage**

```tsx
import { SlopeCounter } from "./SlopeCounter";

// after viz-demo-sumplot section:
<section data-testid="viz-demo-slope" style={tileStyle}>
  <h2 style={headingStyle}>SlopeCounter (PS basis)</h2>
  <p style={subheadingStyle}>
    Left/right count chips · diff readout · brutalist hard edges
  </p>
  <SlopeCounter data={[1, 3, 4, 5, 6, 8, 11]} mu={numLineMu} />
</section>
```

- [ ] **Step 3: Test + eyeball + commit**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/SlopeCounter.test.tsx
```

If green + eyeball clean:

```bash
git add tutor-web/src/components/viz/SlopeCounter.tsx tutor-web/src/components/viz/VizDemoPage.tsx
git commit -m "refactor(viz): retrofit SlopeCounter to brutalist + mount in gallery"
```

---

## Task 11: Retrofit SigmaStackedBar + mount (B.4)

**Files:**
- Modify: `tutor-web/src/components/viz/SigmaStackedBar.tsx`
- Modify: `tutor-web/src/components/viz/VizDemoPage.tsx`

**Scope:** Replace 7-color segment palette with brutalist alternation INK + ACCENT (and a couple of derived greys via opacity for distinguishability). Replace `borderRadius: "4px"` outer with `0`. Replace `var(--color-panel-bg, #f1f5f9)` background with `PAPER`. Replace `var(--font-mono, monospace)` + `var(--color-accent, #3b82f6)` with `FONT_FAMILY` + `INK`/`ACCENT`. Add 2px INK border around outer bar.

- [ ] **Step 1: Retrofit**

Replace `tutor-web/src/components/viz/SigmaStackedBar.tsx`:

```tsx
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export interface SigmaStackedBarProps { data: number[]; mu: number; }

const segmentFor = (i: number): { fill: string; opacity: number } => {
  const cycle = i % 4;
  if (cycle === 0) return { fill: INK, opacity: 1 };
  if (cycle === 1) return { fill: ACCENT, opacity: 1 };
  if (cycle === 2) return { fill: INK, opacity: 0.55 };
  return { fill: ACCENT, opacity: 0.55 };
};

export function SigmaStackedBar({ data, mu }: SigmaStackedBarProps) {
  const prefersReduced =
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const deviations = data.map((x) => Math.abs(x - mu));
  const total = deviations.reduce((a, b) => a + b, 0);

  return (
    <div
      data-testid="sigma-stacked-bar"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <div
        style={{
          display: "flex",
          height: "28px",
          width: "100%",
          borderRadius: 0,
          overflow: "hidden",
          background: PAPER,
          border: `2px solid ${INK}`,
        }}
      >
        {deviations.map((dev, i) => {
          const pct = total > 0 ? (dev / total) * 100 : 0;
          const { fill, opacity } = segmentFor(i);
          return (
            <div
              key={i}
              data-testid={`sigma-seg-${i}`}
              data-value={dev}
              style={{
                width: `${pct}%`,
                background: fill,
                opacity,
                transition: prefersReduced ? "none" : "width 120ms ease-out",
                height: "100%",
                minWidth: dev > 0 ? "2px" : "0px",
                borderRight: i === deviations.length - 1 ? "none" : `1px solid ${INK}`,
              }}
              title={`|x${i + 1} − μ| = ${dev.toFixed(2)}`}
            />
          );
        })}
      </div>

      <div style={{ display: "flex", gap: "0.6rem", flexWrap: "wrap", fontSize: "0.7rem", fontWeight: 700 }}>
        {deviations.map((dev, i) => (
          <span
            key={i}
            style={{ color: INK, opacity: 0.8 }}
          >
            |x<sub>{i + 1}</sub>−μ|={dev.toFixed(1)}
          </span>
        ))}
      </div>

      <div style={{ fontSize: "0.8rem", fontWeight: 900, textAlign: "right" }}>
        Σ = <span data-testid="sigma-sum" style={{ color: INK, background: ACCENT, padding: "0 0.3em" }}>{total.toFixed(1)}</span>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Mount in VizDemoPage**

```tsx
import { SigmaStackedBar } from "./SigmaStackedBar";

// after viz-demo-slope:
<section data-testid="viz-demo-sigma" style={tileStyle}>
  <h2 style={headingStyle}>SigmaStackedBar (PS basis)</h2>
  <p style={subheadingStyle}>
    Stacked deviations · ink + accent + opacity alternation · hard edges
  </p>
  <SigmaStackedBar data={[1, 3, 4, 5, 6, 8, 11]} mu={numLineMu} />
</section>
```

- [ ] **Step 3: Test + eyeball + commit**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/SigmaStackedBar.test.tsx
```

If green + eyeball clean:

```bash
git add tutor-web/src/components/viz/SigmaStackedBar.tsx tutor-web/src/components/viz/VizDemoPage.tsx
git commit -m "refactor(viz): retrofit SigmaStackedBar to brutalist + mount in gallery"
```

---

## Task 12: Reimpl CompareFrames over @visx/shape + mount (E)

**Files:**
- Modify: `tutor-web/src/components/viz/CompareFrames.tsx` (full rewrite)
- Modify: `tutor-web/src/components/viz/VizDemoPage.tsx`
- Modify: `tutor-web/src/__tests__/viz/CompareFrames.test.tsx` (likely)

**Scope:** Replace plotly-based `CompareFrames` with `@visx/shape`-based scatter + line. Same `data: number[]` prop. Drop plotly import entirely from this file (plotly stays in package.json for ChatPane). New frame animation = clicking ANIMATE button morphs between "baseline" (data points + horizontal line at mean) and "median" (data points + horizontal line at median) using CSS transition on the line's `y1`/`y2`.

- [ ] **Step 1: Reimpl CompareFrames**

Replace `tutor-web/src/components/viz/CompareFrames.tsx`:

```tsx
import { useMemo, useState } from "react";
import { Group } from "@visx/group";
import { scaleLinear } from "@visx/scale";
import { Line } from "@visx/shape";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

export interface CompareFramesProps { data: number[]; }

const SVG_W = 480;
const SVG_H = 220;
const PAD_L = 48;
const PAD_R = 16;
const PAD_T = 16;
const PAD_B = 32;
const USABLE_W = SVG_W - PAD_L - PAD_R;
const USABLE_H = SVG_H - PAD_T - PAD_B;

type Mode = "baseline" | "mean" | "median";

function computeMean(data: number[]): number {
  if (data.length === 0) return 0;
  return data.reduce((a, b) => a + b, 0) / data.length;
}

function computeMedian(data: number[]): number {
  if (data.length === 0) return 0;
  const s = [...data].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 === 0 ? (s[mid - 1] + s[mid]) / 2 : s[mid];
}

export function CompareFrames({ data }: CompareFramesProps) {
  const mean = useMemo(() => computeMean(data), [data]);
  const median = useMemo(() => computeMedian(data), [data]);
  const [mode, setMode] = useState<Mode>("baseline");

  const xMax = data.length + 1;
  const yMin = Math.min(...data, mean, median) - 1;
  const yMax = Math.max(...data, mean, median) + 1;

  const xScale = scaleLinear<number>({ domain: [0, xMax], range: [PAD_L, SVG_W - PAD_R] });
  const yScale = scaleLinear<number>({ domain: [yMin, yMax], range: [SVG_H - PAD_B, PAD_T] });

  const estimate = mode === "median" ? median : mean;
  const label =
    mode === "baseline"
      ? "baseline (mean)"
      : mode === "mean"
        ? `mean ${mean.toFixed(2)}`
        : `median ${median.toFixed(2)}`;

  return (
    <div
      data-testid="compare-frames"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 8,
        fontFamily: FONT_FAMILY,
        color: INK,
      }}
    >
      <svg
        width={SVG_W}
        height={SVG_H}
        viewBox={`0 0 ${SVG_W} ${SVG_H}`}
        role="img"
        aria-label="Compare estimators — mean vs median"
        style={{ background: PAPER, border: `2px solid ${INK}` }}
      >
        <Group>
          <Line
            from={{ x: PAD_L, y: SVG_H - PAD_B }}
            to={{ x: SVG_W - PAD_R, y: SVG_H - PAD_B }}
            stroke={INK}
            strokeWidth={1}
            opacity={0.3}
          />
          <Line
            from={{ x: PAD_L, y: PAD_T }}
            to={{ x: PAD_L, y: SVG_H - PAD_B }}
            stroke={INK}
            strokeWidth={1}
            opacity={0.3}
          />
          {data.map((v, i) => (
            <circle
              key={i}
              data-testid="compare-data-point"
              cx={xScale(i + 1)}
              cy={yScale(v)}
              r={4}
              fill={INK}
              stroke={INK}
              strokeWidth={1}
            />
          ))}
          <Line
            data-testid="compare-estimate-line"
            from={{ x: PAD_L, y: yScale(estimate) }}
            to={{ x: SVG_W - PAD_R, y: yScale(estimate) }}
            stroke={ACCENT}
            strokeWidth={3}
            style={{ transition: "all 600ms cubic-bezier(0.4, 0, 0.2, 1)" }}
          />
          <text
            x={SVG_W - PAD_R - 8}
            y={yScale(estimate) - 6}
            textAnchor="end"
            fontFamily={FONT_FAMILY}
            fontSize={11}
            fontWeight={700}
            fill={INK}
          >
            {label}
          </text>
        </Group>
      </svg>
      <div style={{ display: "flex", gap: 4 }}>
        <button
          data-testid="compare-frames-play"
          onClick={() => {
            setMode((m) =>
              m === "baseline" ? "mean" : m === "mean" ? "median" : "baseline"
            );
          }}
          style={{
            background: PAPER,
            color: INK,
            border: `2px solid ${INK}`,
            padding: "0.4em 0.8em",
            fontFamily: FONT_FAMILY,
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: "0.06em",
            textTransform: "uppercase",
            cursor: "pointer",
          }}
        >
          ▶ animate frames
        </button>
        <span
          data-testid="compare-mode-readout"
          style={{
            alignSelf: "center",
            fontSize: 10,
            opacity: 0.7,
            letterSpacing: "0.04em",
          }}
        >
          mode: {mode}
        </span>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Run existing CompareFrames test**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/CompareFrames.test.tsx
```

Likely FAIL — original test probably mocked plotly, asserted on plotly DOM, or used `.js-plotly-plot` selector.

- [ ] **Step 3: Read + update the test**

Read `tutor-web/src/__tests__/viz/CompareFrames.test.tsx`. Identify plotly-specific assertions (mock of `plotly.js-dist-min`, selector `.js-plotly-plot`, spy on `Plotly.animate`).

Rewrite assertions to:
- Render the component without throw
- `getByTestId("compare-frames")` exists
- `getByTestId("compare-frames-play")` exists
- Clicking play changes `getByTestId("compare-mode-readout")` text from "mode: baseline" → "mode: mean" → "mode: median"
- `getByTestId("compare-data-point")` count equals `data.length`
- `getByTestId("compare-estimate-line")` exists

Example minimal rewrite:

```tsx
import { describe, expect, test } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CompareFrames } from "../../components/viz/CompareFrames";

describe("CompareFrames (visx impl)", () => {
  test("renders root, play button, and N data points", () => {
    render(<CompareFrames data={[1, 2, 3, 4, 5]} />);
    expect(screen.getByTestId("compare-frames")).toBeInTheDocument();
    expect(screen.getByTestId("compare-frames-play")).toBeInTheDocument();
    expect(screen.getAllByTestId("compare-data-point")).toHaveLength(5);
  });

  test("clicking play cycles baseline → mean → median → baseline", () => {
    render(<CompareFrames data={[1, 2, 3, 4, 5]} />);
    const readout = screen.getByTestId("compare-mode-readout");
    expect(readout).toHaveTextContent("mode: baseline");
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    expect(readout).toHaveTextContent("mode: mean");
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    expect(readout).toHaveTextContent("mode: median");
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    expect(readout).toHaveTextContent("mode: baseline");
  });

  test("renders an estimate line", () => {
    render(<CompareFrames data={[1, 2, 3, 4, 5]} />);
    expect(screen.getByTestId("compare-estimate-line")).toBeInTheDocument();
  });
});
```

- [ ] **Step 4: Re-run test — verify PASS**

```bash
cd tutor-web && npm run test -- --run src/__tests__/viz/CompareFrames.test.tsx
```

Expected: 3 PASS.

- [ ] **Step 5: Mount in VizDemoPage**

```tsx
import { CompareFrames } from "./CompareFrames";

// after viz-demo-sigma:
<section data-testid="viz-demo-compare-frames" style={tileStyle}>
  <h2 style={headingStyle}>CompareFrames (PS basis)</h2>
  <p style={subheadingStyle}>
    Mean vs median estimator · animate-frames button cycles modes · visx scatter + line
  </p>
  <CompareFrames data={[1, 3, 4, 5, 6, 8, 11]} />
</section>
```

- [ ] **Step 6: Eyeball at /viz-demo**

Start dev server. Open `http://localhost:5174/tutor/viz-demo`. Verify:
- All 7 tiles visible (matrix, smoke, numline, sumplot, slope, sigma, compare-frames)
- CompareFrames tile shows scatter + yellow line, no blue plotly chrome
- Clicking ANIMATE FRAMES button cycles label "baseline → mean → median → baseline"
- Line position animates smoothly (CSS transition)
- No console errors

- [ ] **Step 7: Full suite + build**

```bash
cd tutor-web && npm run test -- --run && npx tsc --noEmit && npm run build
```

Expected: all tests green, tsc 0 errors, build succeeds.

- [ ] **Step 8: Commit**

```bash
git add tutor-web/src/components/viz/CompareFrames.tsx tutor-web/src/__tests__/viz/CompareFrames.test.tsx tutor-web/src/components/viz/VizDemoPage.tsx
git commit -m "refactor(viz): reimpl CompareFrames over @visx/shape + mount in gallery"
```

---

## Task 13: Final acceptance + wrap

**Files:**
- Modify: `C:/Users/User/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md` (append wrap entry)

**Scope:** Run the gallery acceptance check from spec §8. Confirm all 7 testids painted, all tiles brutalist-compliant by eye, no console errors. Append wrap entry to BRIDGE.md.

- [ ] **Step 1: Final acceptance — eyeball at /viz-demo**

```bash
cd tutor-web && npm run dev
```

Open `http://localhost:5174/tutor/viz-demo` in browser. Check each tile:

1. `viz-demo-matrix` — MatrixTransform renders, scrubber works, predict gate works, no blue
2. `viz-demo-algo-stepper-smoke` — counter 0..7, scrubber + ←→/JK/Space/R work, share button copies hash
3. `viz-demo-numline` — μ marker is yellow with black border, drag works
4. `viz-demo-sumplot` — curve is black, marker yellow, follows shared `numLineMu` state
5. `viz-demo-slope` — chips are black-on-paper and black-on-yellow, hard rectangles
6. `viz-demo-sigma` — stacked bar alternates ink/accent with opacity, sum chip is black-on-yellow
7. `viz-demo-compare-frames` — scatter dots black, estimate line yellow, cycles on button click

Open DevTools Console — verify zero errors during page load + interactions.

- [ ] **Step 2: Final test suite**

```bash
cd tutor-web && npm run test -- --run
```

Expected: every test green; count is roughly: prior baseline (~330) + 20 AlgoStepperShell + 0-3 deltas from CompareFrames test rewrite.

- [ ] **Step 3: Final build**

```bash
cd tutor-web && npm run build
```

Expected: build succeeds. Note new bundle hash. NOT deploying — slice stays on branch `viz-foundation-demo`.

- [ ] **Step 4: Verify branch state**

```bash
git -C C:/Users/User/jarvis-kotlin log --oneline main..viz-foundation-demo
```

Expected: 12-14 commits on the branch (1 D + 5 C + 4 B + 1 E + 2 spec). All ahead of main.

- [ ] **Step 5: Append wrap entry to BRIDGE.md**

Append to `C:/Users/User/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md`:

```markdown
## 2026-05-18 viz-foundation-demo slice wrap

**branch:** `viz-foundation-demo` (NOT main; demo branch per feedback_branching_scope durable rule)

**slice path:** `docs/superpowers/specs/2026-05-18-viz-foundation-slice-design.md` + `docs/superpowers/plans/2026-05-18-viz-foundation-demo.md`

**shipped:** D install deps (motion + d3 + visx + mafs) → C AlgoStepperShell + smoke (20 vitest) → B retrofit 4 viz (NumLine/SumPlot/Slope/SigmaStacked) → E CompareFrames over @visx/shape. 13+ commits on branch. NOT merged to main.

**visible:** `/tutor/viz-demo` gallery shows 7 brutalist tiles. Local dev only (no VPS deploy this slice).

**deferred (durable carry-over):**
- Plotly removal from package.json — gated on ChatPane <plotly> envelope migration. 7 plotly-dependent files outside CompareFrames. Future "chat-plotly migration" slice.
- MatrixTransform → AlgoStepperShell retrofit — not touched this slice; standalone plumbing remains. Future slice or just-in-time when next primitive needs Shell hand-off.
- Per-subject drill-page mounting of any viz primitive — entirely separate downstream slice.

**branch decision:** Stays on branch. Future session: either merge into a future production viz integration slice OR cherry-pick if scope diverges OR delete branch + restart fresh.

**hallucination triggers:**
- AlgoStepperShell is the canonical foundation. MatrixTransform is intentionally a non-subclass (older plumbing). Do not "fix" by retrofitting unless task demands.
- Plotly is STILL in package.json. Don't claim it's removed.
- VizDemoPage is a dev gallery, NOT a user-facing surface. No drill flow uses these viz components yet.
```

Run:

```bash
git -C C:/Users/User/jarvis-kotlin status
```

Verify only memory/BRIDGE.md changed (memory dir is outside repo; this is a sanity check — BRIDGE lives in `.claude/projects/...`, not repo, so won't show in status).

- [ ] **Step 6: Slice done**

Print summary:

> Branch `viz-foundation-demo` shipped: deps installed, AlgoStepperShell + smoke, 4 brutalist retrofits, CompareFrames over @visx/shape. 7 tiles visible at localhost:5174/tutor/viz-demo. Not deployed to prod. Not merged to main.
