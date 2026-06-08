# Foundation: App-Wide Theme System + Nav Shell — Implementation Plan

> **✅ EXECUTED 2026-06-08 (SESSION-59) — supersedes the "merge door-compare" wording (master-impl-plan-v2 T5).**
> Tasks 1–6 were BUILT on branch `door-compare`, but that branch forked **before Phase 2/3** (176 files / ~26k lines stale) — a literal `git merge door-compare→main` would have rewound the backend. So the theme system was **surgically PORTED** onto `main`: the 4 net-new files (`theme/applyTheme.ts`, `theme/ThemeProvider.tsx`, `components/AppShell.tsx`, prod `door/ThemePicker.tsx`) + their 4 tests extracted from `door-compare`, and **`door/palettes.ts` rescued from the untracked working tree** (the theme system's leaf dep — it was never committed on `door-compare`). The throwaway door demo (`DoorBrutalist/Warm/Compare`, `figures`, `concept`) was left untracked (not shipped; clean `vite build` confirms no leak). `main.tsx`/`App.tsx` reconciled per Task 5; `App.test`/`App.debug`/`crossDeviceSync` wrapped in `ThemeProvider`. **T4.5 (master-plan addition, not in the original Tasks 1–6):** manual motion toggle — `ThemeProvider.motionPreference` + `useReducedMotion()` + `prefersReducedMotionNow()` + a `MotionToggle` in `AppShell` + a global `html[data-reduce-motion]` CSS bridge in `index.css`; the 5 JS motion-helpers rewired (DrillCard/CompileSubmitCard/ProgressStrip → hook; PlotlyEmbed/NumLineDirect → imperative resolver). **Verified:** 583/583 vitest green, tsc 24 baseline (no new), production build clean, browser render confirms recolor + persist + motion toggle. The rebuilt bundle is NOT committed (deploy = separate explicit step).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote the door's theme system into an app-wide `ThemeProvider` that recolors the whole tutor via one palette picker living in a new `AppShell` masthead — the production picker is palette-only (the demo concept switcher is stripped).

**Architecture:** A React context (`ThemeProvider`) holds `{ paletteId, customPrimary }`, writes the resolved CSS custom properties to `document.documentElement` (so every `bg-accent` / `text-accent` token recolors at once), and persists the choice to `localStorage`. A new `AppShell` renders the dark brutalist masthead with the brand-circle `ThemePicker` (concept row hidden) + the existing nav, wrapping the routed content. Reuses the already-built `src/door/palettes.ts` (`PALETTES`, `brutalistVars`) verbatim — no duplication.

**Tech Stack:** React 19, react-router-dom 7, Tailwind v4 `@theme` tokens, vitest 2 + @testing-library/react + jsdom (setup in `tutor-web/src/setupTests.ts`).

**Scope boundary (explicit):** This slice ships *palette/accent theming app-wide* (the brutalist surface recolors to any of the 6 palettes) + the picker shell. It does NOT ship the *warm-skin* applied app-wide (serif/rounded re-skin of every component) — that is a separate, larger follow-on plan; the warm layout stays a door-route option. The shared `MasterySparkline` primitive ships with the LearnerQueue surface plan (it needs mastery data), not here. The correctness/trust engine is its own plan.

**Run tests:** from `tutor-web/`, `npx vitest run <path>` for one file, `npm test` for all.

---

## File Structure

- Create `tutor-web/src/theme/applyTheme.ts` — pure helpers: resolve a `ThemeChoice` to CSS vars and write/clear them on `:root`; localStorage load/save. One responsibility: the side-effect bridge between theme state and the DOM.
- Create `tutor-web/src/theme/ThemeProvider.tsx` — the React context + provider + `useTheme()` hook. Holds state, calls `applyTheme`, persists.
- Modify `tutor-web/src/door/ThemePicker.tsx` — hide the Concept row when `concepts` is empty (the production strip). Already accepts `concepts`/`conceptId`/`onConcept`.
- Create `tutor-web/src/components/AppShell.tsx` — the dark masthead (brand circle = `ThemePicker` bound to `useTheme`, concepts=[]) + nav links + `children`. Extracted from `App.tsx`'s inline `<header>`.
- Modify `tutor-web/src/main.tsx` — wrap `<BrowserRouter>` in `<ThemeProvider>`.
- Modify `tutor-web/src/App.tsx` — replace the inline `<header>` with `<AppShell>` wrapping the routed `<main>`; keep all nav props it already computes (reviewDue, ledger open, etc.).

---

## Task 1: applyTheme side-effect helpers

**Files:**
- Create: `tutor-web/src/theme/applyTheme.ts`
- Test: `tutor-web/src/__tests__/applyTheme.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// tutor-web/src/__tests__/applyTheme.test.ts
import { test, expect, beforeEach } from "vitest";
import { applyThemeToRoot, loadTheme, saveTheme } from "../theme/applyTheme";

beforeEach(() => {
  document.documentElement.removeAttribute("style");
  localStorage.clear();
});

test("applyThemeToRoot writes the palette accent onto :root", () => {
  applyThemeToRoot({ paletteId: "magenta-lime" });
  // magenta-lime primary is #FF5DA8 (from palettes.ts)
  expect(document.documentElement.style.getPropertyValue("--color-accent").toUpperCase())
    .toBe("#FF5DA8");
  expect(document.documentElement.style.getPropertyValue("--color-panel-dark-fg").toUpperCase())
    .toBe("#FF5DA8");
});

test("customPrimary overrides the palette accent", () => {
  applyThemeToRoot({ paletteId: "brand-yellow", customPrimary: "#123456" });
  expect(document.documentElement.style.getPropertyValue("--color-accent")).toBe("#123456");
});

test("save then load round-trips the choice", () => {
  saveTheme({ paletteId: "forest-gold", customPrimary: "#abcdef" });
  expect(loadTheme()).toEqual({ paletteId: "forest-gold", customPrimary: "#abcdef" });
});

test("loadTheme returns the brand-yellow default when nothing is stored", () => {
  expect(loadTheme()).toEqual({ paletteId: "brand-yellow" });
});

test("loadTheme tolerates corrupt storage", () => {
  localStorage.setItem("jarvis.theme", "{not json");
  expect(loadTheme()).toEqual({ paletteId: "brand-yellow" });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/__tests__/applyTheme.test.ts`
Expected: FAIL — `Cannot find module '../theme/applyTheme'`.

- [ ] **Step 3: Write minimal implementation**

```ts
// tutor-web/src/theme/applyTheme.ts
import { brutalistVars, type ThemeChoice } from "../door/palettes";

const KEY = "jarvis.theme";
const DEFAULT: ThemeChoice = { paletteId: "brand-yellow" };

/** Write the resolved brutalist palette vars onto :root so every
 *  `bg-accent` / `text-accent` / `border-accent` token recolors at once. */
export function applyThemeToRoot(choice: ThemeChoice): void {
  const vars = brutalistVars(choice) as Record<string, string>;
  const root = document.documentElement;
  for (const [k, v] of Object.entries(vars)) {
    if (k.startsWith("--")) root.style.setProperty(k, v);
  }
}

export function saveTheme(choice: ThemeChoice): void {
  try { localStorage.setItem(KEY, JSON.stringify(choice)); } catch (_) {}
}

export function loadTheme(): ThemeChoice {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULT };
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.paletteId === "string") return parsed;
    return { ...DEFAULT };
  } catch (_) {
    return { ...DEFAULT };
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/__tests__/applyTheme.test.ts`
Expected: PASS (5 tests). If `--color-accent` comes back lowercase, the `.toUpperCase()` in the test already normalizes it.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/theme/applyTheme.ts tutor-web/src/__tests__/applyTheme.test.ts
git commit -m "feat(theme): applyThemeToRoot + persistence helpers reusing door palettes"
```

---

## Task 2: ThemeProvider context

**Files:**
- Create: `tutor-web/src/theme/ThemeProvider.tsx`
- Test: `tutor-web/src/__tests__/ThemeProvider.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// tutor-web/src/__tests__/ThemeProvider.test.tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, beforeEach } from "vitest";
import { ThemeProvider, useTheme } from "../theme/ThemeProvider";

beforeEach(() => {
  document.documentElement.removeAttribute("style");
  localStorage.clear();
});

function Probe() {
  const { choice, setChoice } = useTheme();
  return (
    <div>
      <span data-testid="pid">{choice.paletteId}</span>
      <button onClick={() => setChoice({ paletteId: "indigo-mint" })}>indigo</button>
    </div>
  );
}

test("provider applies the default palette to :root on mount", () => {
  render(<ThemeProvider><Probe /></ThemeProvider>);
  expect(screen.getByTestId("pid").textContent).toBe("brand-yellow");
  // brand-yellow primary #FDE047
  expect(document.documentElement.style.getPropertyValue("--color-accent").toUpperCase())
    .toBe("#FDE047");
});

test("setChoice recolors :root and persists", async () => {
  render(<ThemeProvider><Probe /></ThemeProvider>);
  await userEvent.click(screen.getByText("indigo"));
  expect(screen.getByTestId("pid").textContent).toBe("indigo-mint");
  // indigo-mint primary #8B7CFF
  expect(document.documentElement.style.getPropertyValue("--color-accent").toUpperCase())
    .toBe("#8B7CFF");
  expect(JSON.parse(localStorage.getItem("jarvis.theme")!).paletteId).toBe("indigo-mint");
});

test("provider restores a persisted choice on mount", () => {
  localStorage.setItem("jarvis.theme", JSON.stringify({ paletteId: "amber-sky" }));
  render(<ThemeProvider><Probe /></ThemeProvider>);
  expect(screen.getByTestId("pid").textContent).toBe("amber-sky");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/__tests__/ThemeProvider.test.tsx`
Expected: FAIL — `Cannot find module '../theme/ThemeProvider'`.

- [ ] **Step 3: Write minimal implementation**

```tsx
// tutor-web/src/theme/ThemeProvider.tsx
import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import type { ThemeChoice } from "../door/palettes";
import { applyThemeToRoot, loadTheme, saveTheme } from "./applyTheme";

interface ThemeCtx {
  choice: ThemeChoice;
  setChoice: (c: ThemeChoice) => void;
}

const Ctx = createContext<ThemeCtx | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }): ReactNode {
  const [choice, setChoiceState] = useState<ThemeChoice>(() => loadTheme());

  useEffect(() => {
    applyThemeToRoot(choice);
    saveTheme(choice);
  }, [choice]);

  return (
    <Ctx.Provider value={{ choice, setChoice: setChoiceState }}>
      {children}
    </Ctx.Provider>
  );
}

export function useTheme(): ThemeCtx {
  const v = useContext(Ctx);
  if (!v) throw new Error("useTheme must be used inside <ThemeProvider>");
  return v;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/__tests__/ThemeProvider.test.tsx`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/theme/ThemeProvider.tsx tutor-web/src/__tests__/ThemeProvider.test.tsx
git commit -m "feat(theme): ThemeProvider context — applies + persists palette app-wide"
```

---

## Task 3: Strip the Concept row from ThemePicker in production

**Files:**
- Modify: `tutor-web/src/door/ThemePicker.tsx` (the Concept `<Row>` block)
- Test: `tutor-web/src/__tests__/ThemePicker.prod.test.tsx`

**Context — current prop signature (verified in `tutor-web/src/door/ThemePicker.tsx`):**
```tsx
export function ThemePicker({ skin, onSkin, choice, onChoice, concepts, conceptId, onConcept, size = 30 }: {
  skin: Skin; onSkin: (s: Skin) => void;
  choice: ThemeChoice; onChoice: (c: ThemeChoice) => void;
  concepts: { id: string; label: string }[]; conceptId: string; onConcept: (id: string) => void;
  size?: number;
}): ReactNode
```
The Concept `<Row label="Concept">…</Row>` currently renders unconditionally. We make it render only when `concepts.length > 0`.

- [ ] **Step 1: Write the failing test**

```tsx
// tutor-web/src/__tests__/ThemePicker.prod.test.tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect } from "vitest";
import { ThemePicker } from "../door/ThemePicker";

const noop = () => {};
const choice = { paletteId: "brand-yellow" };

test("production picker (concepts=[]) renders NO concept row", async () => {
  render(
    <ThemePicker skin="brutalist" onSkin={noop} choice={choice} onChoice={noop}
      concepts={[]} conceptId="" onConcept={noop} />
  );
  await userEvent.click(screen.getByTestId("theme-fab"));
  expect(screen.queryByText("Concept")).toBeNull();
  expect(screen.queryByTestId(/^concept-/)).toBeNull();
  // palette swatches still present
  expect(screen.getByTestId("palette-brand-yellow")).toBeTruthy();
});

test("demo picker (concepts present) still shows the concept row", async () => {
  render(
    <ThemePicker skin="brutalist" onSkin={noop} choice={choice} onChoice={noop}
      concepts={[{ id: "recurrence", label: "Recurrence" }]} conceptId="recurrence" onConcept={noop} />
  );
  await userEvent.click(screen.getByTestId("theme-fab"));
  expect(screen.getByTestId("concept-recurrence")).toBeTruthy();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/__tests__/ThemePicker.prod.test.tsx`
Expected: FAIL — first test fails because the Concept row + `concept-*` buttons render even when `concepts=[]`.

- [ ] **Step 3: Make the minimal change**

In `tutor-web/src/door/ThemePicker.tsx`, wrap the Concept `<Row label="Concept">…</Row>` block so it only renders when there are concepts. Change the opening of that block from:

```tsx
          <Row label="Concept">
```
to:
```tsx
          {concepts.length > 0 && (
          <Row label="Concept">
```
and add the closing `)}` immediately after that Row's closing `</Row>` (before `<Row label="Layout">`). The Layout and Palette rows are unchanged.

- [ ] **Step 4: Run test + typecheck**

Run: `npx vitest run src/__tests__/ThemePicker.prod.test.tsx`
Expected: PASS (2 tests).
Run: `npx tsc --noEmit`
Expected: no new errors from `ThemePicker.tsx`.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/door/ThemePicker.tsx tutor-web/src/__tests__/ThemePicker.prod.test.tsx
git commit -m "feat(theme): ThemePicker hides concept row in production (concepts=[])"
```

---

## Task 4: AppShell — masthead with brand-circle picker + nav

**Files:**
- Create: `tutor-web/src/components/AppShell.tsx`
- Test: `tutor-web/src/__tests__/AppShell.test.tsx`

AppShell renders the dark masthead: the brand circle (the production `ThemePicker`, bound to `useTheme`, `concepts={[]}`) + the brand word + a `nav` slot + a `children` content well. App.tsx passes its already-computed nav as the `nav` prop (keeps all the routing/reviewDue logic in App.tsx; AppShell is presentational).

- [ ] **Step 1: Write the failing test**

```tsx
// tutor-web/src/__tests__/AppShell.test.tsx
import { render, screen } from "@testing-library/react";
import { test, expect, beforeEach } from "vitest";
import { ThemeProvider } from "../theme/ThemeProvider";
import { AppShell } from "../components/AppShell";

beforeEach(() => {
  document.documentElement.removeAttribute("style");
  localStorage.clear();
});

test("renders the brand circle (theme fab), brand word, nav slot and children", () => {
  render(
    <ThemeProvider>
      <AppShell nav={<a href="#" data-testid="nav-probe">workspace</a>}>
        <div data-testid="content">body</div>
      </AppShell>
    </ThemeProvider>
  );
  expect(screen.getByTestId("app-shell")).toBeTruthy();
  expect(screen.getByTestId("theme-fab")).toBeTruthy();
  expect(screen.getByText("TUTOR")).toBeTruthy();
  expect(screen.getByTestId("nav-probe")).toBeTruthy();
  expect(screen.getByTestId("content")).toBeTruthy();
});

test("clicking the brand circle opens the palette picker (no concept row)", async () => {
  const { default: userEvent } = await import("@testing-library/user-event");
  render(
    <ThemeProvider>
      <AppShell nav={<span />}><span /></AppShell>
    </ThemeProvider>
  );
  await userEvent.click(screen.getByTestId("theme-fab"));
  expect(screen.getByTestId("palette-brand-yellow")).toBeTruthy();
  expect(screen.queryByText("Concept")).toBeNull();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/__tests__/AppShell.test.tsx`
Expected: FAIL — `Cannot find module '../components/AppShell'`.

- [ ] **Step 3: Write minimal implementation**

```tsx
// tutor-web/src/components/AppShell.tsx
import type { ReactNode } from "react";
import { useTheme } from "../theme/ThemeProvider";
import { ThemePicker } from "../door/ThemePicker";

/** App-wide dark masthead: brand-circle theme picker + brand word + a nav slot,
 *  wrapping the routed content. The picker is palette-only in production
 *  (concepts=[] strips the demo concept row). */
export function AppShell({
  nav,
  children,
}: {
  nav: ReactNode;
  children: ReactNode;
}): ReactNode {
  const { choice, setChoice } = useTheme();
  return (
    <div data-testid="app-shell" className="h-dvh flex flex-col">
      <header className="bg-panel-dark-bg text-panel-dark-fg px-4 py-3 flex flex-wrap items-center justify-between gap-2 border-b-4 border-accent">
        <div className="flex items-center gap-3 min-w-0">
          <ThemePicker
            skin="brutalist"
            onSkin={() => {}}
            choice={choice}
            onChoice={setChoice}
            concepts={[]}
            conceptId=""
            onConcept={() => {}}
            size={26}
          />
          <span className="text-lg font-bold tracking-widest">TUTOR</span>
        </div>
        {nav}
      </header>
      <main className="flex-1 min-h-0 overflow-hidden overflow-y-auto bg-page-bg">
        {children}
      </main>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/__tests__/AppShell.test.tsx`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/AppShell.tsx tutor-web/src/__tests__/AppShell.test.tsx
git commit -m "feat(shell): AppShell masthead with brand-circle theme picker + nav slot"
```

---

## Task 5: Mount ThemeProvider (main.tsx) + AppShell (App.tsx)

**Files:**
- Modify: `tutor-web/src/main.tsx`
- Modify: `tutor-web/src/App.tsx:233-348` (the returned `<div>`/`<header>`/`<main>` block)
- Test: `tutor-web/src/__tests__/App.test.tsx` (existing — must stay green)

- [ ] **Step 1: Wrap the router in ThemeProvider (main.tsx)**

In `tutor-web/src/main.tsx`, add the import and wrap `<BrowserRouter>`:

```tsx
import { ThemeProvider } from "./door/.."; // see exact path below
```
Concretely, add `import { ThemeProvider } from "./theme/ThemeProvider";` next to the other imports, then change:

```tsx
  <StrictMode>
    <BrowserRouter basename="/tutor">
```
to:
```tsx
  <StrictMode>
    <ThemeProvider>
    <BrowserRouter basename="/tutor">
```
and close it: change the matching
```tsx
    </BrowserRouter>
  </StrictMode>,
```
to:
```tsx
    </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
```

- [ ] **Step 2: Replace App.tsx's inline header/main with AppShell**

In `tutor-web/src/App.tsx`, add the import `import { AppShell } from "./components/AppShell";` (next to the other component imports near line 3-13).

Then replace the entire returned block (lines 233-351, from `return (` to the final `);`) with this — it keeps the brand-local controls (close button, missing-task banner, ledger) and moves the brand word + theme circle into AppShell, passing the existing `<nav>` as the `nav` prop:

```tsx
  return (
    <>
      <AppShell
        nav={
          <nav className="flex items-center gap-4 text-xs font-bold tracking-widest">
            <Link
              to={explicitTaskId && !pickMode ? `/?taskId=${explicitTaskId}` : "/?pick=1"}
              aria-current={here.pathname === "/" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              workspace
            </Link>
            <Link
              to="/tasks"
              aria-current={here.pathname === "/tasks" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              tasks
            </Link>
            <Link
              to="/review"
              aria-current={here.pathname === "/review" ? "page" : undefined}
              aria-label={reviewDue > 0
                ? `review, ${Math.min(reviewDue, REVIEW_DAILY_CAP)} cards due`
                : "review"}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              review
              {reviewDue > 0 && (
                <span aria-hidden="true" className="opacity-70 font-bold">
                  {" · "}{Math.min(reviewDue, REVIEW_DAILY_CAP)}
                </span>
              )}
            </Link>
            <Link
              to="/settings/trust"
              aria-current={here.pathname === "/settings/trust" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              trust
            </Link>
            <Link
              to="/me"
              aria-current={here.pathname === "/me" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              me
            </Link>
            <button
              data-testid="header-ledger-btn"
              onClick={() => { recordTelemetry("ledger.opened"); setLedgerOpen(true); }}
              aria-label="Open knowledge ledger"
              aria-haspopup="dialog"
              aria-expanded={ledgerOpen}
              className="hover:underline"
            >
              ledger
            </button>
            {!showQuickStart && (
              <button
                onClick={pickAnotherTask}
                data-testid="pick-another-task-btn"
                className="text-xs tracking-widest bg-accent text-page-fg px-3 py-2 sm:px-2 sm:py-0.5 hover:bg-accent-hover"
              >
                × close
              </button>
            )}
            {debug ? (
              <>
                <DaemonHealthPill />
                <span data-testid="domain-footer" className="text-[11px] tracking-widest text-panel-dark-fg/85">
                  READY · CTRL+ENTER · CORGFLIX.DUCKDNS.ORG
                </span>
              </>
            ) : (
              <span className="text-[11px] tracking-widest text-panel-dark-fg/85">READY</span>
            )}
          </nav>
        }
      >
        {missingPinnedTask && (
          <div data-testid="missing-pinned-task"
               role="status" aria-live="polite"
               className="bg-accent border-b-4 border-border-strong text-page-fg font-mono text-xs font-bold tracking-widest px-4 py-1.5">
            couldn't open last task ({taskId}) — pick another below
          </div>
        )}
        {here.pathname === "/login"
          ? <LoginPage />
          : here.pathname === "/welcome/ai-literacy"
            ? <AiLiteracyGate lang={gateLang} onConfirmed={() => { setSessionReady(true); navigate("/"); }} />
            : here.pathname === "/review"
            ? <FsrsReview streak={0} />
            : here.pathname === "/tasks"
              ? <TasksScreen />
              : here.pathname === "/settings/trust"
                ? <TrustSettings />
                : here.pathname === "/me"
                ? <SettingsMe />
                : !sessionReady
                  ? <div className="p-6 font-mono text-sm text-page-fg/80">setting up tutor session…</div>
                  : showQuickStart
                    ? <ActiveTaskDashboard />
                    : <TutorWorkspace pdfUrl={`/api/v1/tasks/${encodeURIComponent(taskId)}/pdf`} taskId={taskId} dedupedNotice={dedupedFlag} />}
      </AppShell>
      {ledgerOpen && <KnowledgeLedger onClose={() => setLedgerOpen(false)} />}
    </>
  );
```

(Note: the `taskId` chip that used to sit by the brand is dropped — `× close` moves into the nav. The brand word changes `JARVIS · TUTOR` → `TUTOR` to match the door masthead. If `App.test.tsx` asserts the old `JARVIS · TUTOR` text or the chip position, update those assertions in the next step.)

- [ ] **Step 3: Run the existing App + axe tests; fix assertions broken by the rename/move**

Run: `npx vitest run src/__tests__/App.test.tsx src/__tests__/App.debug.test.tsx src/__tests__/axe.workspace.test.tsx`
Expected: any failure is a stale assertion on `JARVIS · TUTOR` or the old chip placement. Update those assertions to the new `TUTOR` brand word + `× close` living in the nav. Re-run until green. Do NOT change behavior to satisfy a test that asserts the *old* layout — change the assertion.

- [ ] **Step 4: Typecheck + full suite**

Run: `npx tsc --noEmit` (expect no NEW errors beyond the 24 pre-existing baseline noted in BRIDGE-HEAD)
Run: `npm test` (expect green except the env-flaky `crossDeviceSync`/`App.test` cases already failing at baseline `441d42b` — confirm the set of failures did not grow)

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/main.tsx tutor-web/src/App.tsx tutor-web/src/__tests__/App.test.tsx
git commit -m "feat(shell): mount ThemeProvider + AppShell app-wide (palette theming live)"
```

---

## Task 6: Verify the recolor app-wide (real render, self-see)

**Files:** none (verification only)

- [ ] **Step 1:** Start the dev server (`npm run dev`), open `http://localhost:5173/tutor/?debug=1` (workspace or dashboard).
- [ ] **Step 2:** Click the brand circle (top-left), pick a non-yellow palette (e.g. magenta-lime).
- [ ] **Step 3:** Confirm the whole app's accent (nav `aria-current` highlight, any `bg-accent` button, the masthead bottom border) recolored — not just the circle. Reload → the choice persists.
- [ ] **Step 4:** Run the self-see contrast probe (`.claude/skills/grounded-ui-design/self-see.mjs --emit-probe` → Playwright `browser_evaluate` → `--grade`) on the dashboard at the new palette; expect `pass: true` (no contrast regressions from the swap). If a palette's accent fails contrast as a button fill (black text on it), note it; all 6 primaries are light-on-black safe but verify on the LIGHT working surface.

---

## Self-Review

**1. Spec coverage** (against the design spine §2.1 + §2.4 foundation locks): app-wide palette theming ✓ (Tasks 1-2,5), production picker palette-only / concept-row stripped ✓ (Task 3), brand-circle in the masthead is the only theming entry ✓ (Task 4), persistence ✓ (Task 1-2). Nav shell `AppShell` ✓ (Task 4-5). Deferred-by-scope (stated up top): warm-skin-app-wide, MasterySparkline, token/type config edits (already in `index.css`), correctness engine.

**2. Placeholder scan:** none — every step has full code or an exact diff.

**3. Type consistency:** `ThemeChoice` (from `door/palettes.ts`) used identically in Tasks 1,2; `applyThemeToRoot`/`loadTheme`/`saveTheme` signatures match between Task 1 def and Task 2 use; `useTheme()` returns `{choice,setChoice}` used in Task 4; `ThemePicker` props in Task 4 match its real signature (Task 3 context block).

**4. Build+mount pairing:** `applyTheme.ts` → used by `ThemeProvider` (Task 2). `ThemeProvider` → mounted in `main.tsx` (Task 5 Step 1). `AppShell` → mounted in `App.tsx` (Task 5 Step 2) as the wrapping element + imported. `ThemePicker` (modified) → mounted inside `AppShell` (Task 4 impl) AND already mounted in `DoorCompare`. No new component is left unmounted.

**5. Component-reuse contract:** Task 4 mounts the existing `ThemePicker` in the new `AppShell`. Prop signature shown verbatim (Task 3 context). Wire-up JSX names every prop explicitly (no spread). `tsc --noEmit` in Task 5 Step 4. The AppShell test (Task 4) renders AppShell mounting ThemePicker and asserts the picker opens with the palette swatch + no concept row — the reuse contract test.

**6. data-testid:** the spine has no formal Visual Acceptance testid list for this slice; `app-shell`, `theme-fab`, `palette-*` are asserted in tests.

---

## Execution Handoff

Two execution options:
1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks.
2. **Inline Execution** — batch in this session with checkpoints.
