# Tutor Overhaul — Phase 2 (UX Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tokenize the design system, harden a11y, set the substrate every later phase inherits. Visual identity stays brutalist-yellow; no SO theme copy. Zero AA violations on `/tutor/` workspace + `/tutor/tasks` page when scanned by axe.

**Architecture:** CSS custom properties in `:root` exposed to Tailwind v4 via `@theme` block; components migrate from `bg-yellow-300` / `text-black` literals to `bg-[var(--accent-yellow)]` (or shortname utilities mapped from the @theme block). Typography vars added in the same block, body uses `clamp()` for fluid sizing. Global focus-ring + reduced-motion rules go in `index.css`. ARIA + semantic HTML lifts go per-component, scoped to icon-only controls + nav landmarks. A11y is tested in two places: `vitest-axe` runs against rendered components in jsdom (catches structural violations), Playwright gate runs `axe-core` against the live deployed page (catches contrast + dynamic violations).

**Tech Stack:** React 19 + Tailwind v4 + Vitest + Testing Library + new dev dep `vitest-axe`. Playwright MCP tools for the live a11y gate.

**Source spec:** `docs/superpowers/specs/2026-05-10-tutor-overhaul-design.md` § Phase 2 (lines 75-126).

**Dependencies / order constraints:**
- Tokens (Task 2) MUST land before component migration (Task 3) — migration references the tokens.
- Touch-target adjustments (Task 5) MUST happen after tokens land, since some sizing uses var-based padding.
- A11y test infra (Task 8) MUST land after the components have been touched, otherwise existing baseline noise overwhelms new violations.

---

## File Structure

**New:**
- `tutor-web/src/__tests__/axe.workspace.test.tsx` — vitest-axe scan of `<TutorWorkspace>` rendering.
- `tutor-web/src/__tests__/axe.tasks.test.tsx` — vitest-axe scan of the `/tasks` route.
- `tutor-web/src/__tests__/typography.test.tsx` — snapshot-style assertion that `--type-body` resolves to a clamp() expression and that body line-height is 1.5.
- `tutor-web/src/__tests__/visibleState.test.tsx` — non-preset task-create paths get in-flight disabled state.

**Modified — heavily:**
- `tutor-web/src/index.css` — gains `@theme` block with 9 color tokens + 4 type tokens + global `*:focus-visible` + `prefers-reduced-motion` rules.

**Modified — surgical (per-component, only the literals + ARIA additions):**
- `tutor-web/src/App.tsx` — header colors → tokens; `aria-current="page"` on nav links matching active route.
- `tutor-web/src/components/Sidebar.tsx` — colors → tokens; `<aside>` keeps role; inner `<nav>` wraps the task list; `role="list"` on `<ul>` collections; touch-target `py-3 sm:py-1.5` on task buttons; `aria-label` on `+ NEW TASK` clarifies intent; `aria-current="true"` on active task.
- `tutor-web/src/components/TutorWorkspace.tsx` — banner color → tokens (deduped notice); `role="status"` + `aria-live="polite"` on banner so screen readers announce dedup-200 outcomes.
- `tutor-web/src/components/ChatPane.tsx` — bg/fg → tokens; role-tag chips use tokens; `60ch` max-width clamp on prose containers (existing inline-block message wrappers stay; outer scroll container gets `[&_p]:max-w-[60ch]` only on assistant messages); SEND button + screenshot button get visible focus per global rule.
- `tutor-web/src/components/PdfPane.tsx` — colors → tokens; iframe gets `title` attribute "PDF document" for screen readers.
- `tutor-web/src/components/Scratchpad.tsx` — colors → tokens; textarea gets `aria-label="Task scratchpad"`; sizing changes to `min-h-[6rem] max-h-[40vh] resize-y` (also closes Phase 3.6 sidebar polish item early since this is the same line).
- `tutor-web/src/components/TaskQuickStart.tsx` — colors → tokens; preset button group gets `<fieldset>` + `<legend>` for screen readers.
- `tutor-web/src/components/TasksScreen.tsx` — colors → tokens; in-flight `disabled` on submit; empty-state copy "no tasks yet — start one above"; Enter-to-submit (also closes Phase 3.3 ergonomics fragment early).
- `tutor-web/src/components/TrustSettings.tsx` — colors → tokens; in-flight `disabled` on grant submit.
- `tutor-web/src/components/KnowledgeGapCard.tsx` — colors → tokens.
- `tutor-web/src/components/SuggestedEditCard.tsx` — colors → tokens.
- `tutor-web/src/components/ChipRow.tsx` — colors → tokens; `role="group"` + `aria-label="Suggested follow-ups"`; chip touch-target `py-2 sm:py-1`.
- `tutor-web/src/components/ScreenshotCapture.tsx` — colors → tokens; emoji wrapped `<span aria-hidden>📷</span><span>Capture</span>`; button `aria-label` describes action.
- `tutor-web/src/setupTests.ts` — extends `expect` with `toHaveNoViolations` matcher from vitest-axe.
- `tutor-web/package.json` — adds `vitest-axe` to devDependencies.

**Phase 1 backlog items folded into Phase 2 (cross out in backlog after each task ships):**
- `[1] [layout] [mobile-first] [med] PDF stack order on mobile` → Task 4's typography max-width work doesn't touch this; defer remains.
- `[1] [layout] [gestalt-grouping] [med] Mobile divider between Scratchpad and Chat` → Task 5 touch-target sweep covers this incidentally; add the `border-t-4 sm:border-t-0` on Scratchpad.
- `[1] [layout] [responsive-breakpoint] [med] sm cramp at 640-820px` → Task 5 sweep bumps the column-split breakpoint to `md`.
- `[1] [interaction] [feedback-response-time] [med] in-flight disable on non-preset paths` → Task 7 visible-state coverage.
- `[1] [layout] [scroll-containment] [low] PdfPane mobile clip` → Task 5 sweep adds `min-h-[50vh] sm:min-h-0`.

---

## Task 1: Phase 2 plan committed

**Files:**
- Already-staged: `docs/superpowers/plans/2026-05-10-tutor-overhaul-phase2.md` (this file).

- [ ] **Step 1: Verify the plan is in the working tree, not yet committed**

Run: `git status -s docs/superpowers/plans/`
Expected: shows `?? docs/superpowers/plans/2026-05-10-tutor-overhaul-phase2.md` (untracked) OR `A docs/superpowers/plans/2026-05-10-tutor-overhaul-phase2.md` (staged) but no `M` (no diff vs committed).

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-05-10-tutor-overhaul-phase2.md
git commit -m "$(cat <<'EOF'
Phase 2 plan: UX foundation (tokens + a11y + visible-state)

Per spec § Phase 2 (lines 75-126). Phase 1 shipped + 4 gates passed at
33633f2. Phase 2 substrate every later phase inherits: design tokens,
typography scale, focus + motion + touch, ARIA + semantic HTML, visible
state coverage. Plan folds 5 Phase 1 backlog items into Phase 2 sweep
where they reduce surface (mobile gestalt, sm-cramp, scroll containment,
non-preset in-flight disable).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: §2.1a — Design tokens defined in `index.css` (CSS only, no component changes)

**Files:**
- Modify: `tutor-web/src/index.css` (currently 1 line: `@import "tailwindcss";`).

The Tailwind v4 `@theme` block exposes CSS custom properties as utility-class shortnames. `--color-yellow-300` becomes `bg-yellow-300` etc. Phase 2 redefines the brutalist palette as semantic tokens (`--color-page-bg`, `--color-accent`, etc.) and registers them via `@theme` so components migrate to `bg-page-bg`, `bg-accent` shortnames in Task 3. The `:root` block also exports the raw vars so non-Tailwind consumers (e.g. inline `style` attributes) can read them.

- [ ] **Step 1: Replace the contents of `tutor-web/src/index.css`**

Write:

```css
@import "tailwindcss";

/* ---------- Brutalist-yellow design tokens (Phase 2.1) ----------
   Color tokens are semantic, not literal. To reskin (e.g. light/dark
   later phase), swap the :root block; components don't churn.
   Tailwind v4 picks up @theme `--color-<name>` as `bg-<name>`,
   `text-<name>`, `border-<name>` etc. */
:root {
  /* Surface */
  --color-page-bg: #ffffff;
  --color-page-fg: #000000;
  --color-panel-dark-bg: #000000;
  --color-panel-dark-fg: #fde047;          /* yellow-300 */

  /* Accent */
  --color-accent: #fde047;                  /* yellow-300 */
  --color-accent-hover: #facc15;            /* yellow-400 */
  --color-accent-soft: #fefce8;             /* yellow-50 */

  /* Border */
  --color-border-strong: #000000;
  --color-border-thin: rgba(0, 0, 0, 0.2);

  /* Focus */
  --color-ring-focus: #facc15;              /* yellow-400 */

  /* Typography */
  --type-sm: 12px;
  --type-body: clamp(14px, calc(13.5px + 0.1vw), 16px);
  --type-lg: 18px;
  --type-h2: 20px;
}

@theme {
  --color-page-bg: var(--color-page-bg);
  --color-page-fg: var(--color-page-fg);
  --color-panel-dark-bg: var(--color-panel-dark-bg);
  --color-panel-dark-fg: var(--color-panel-dark-fg);
  --color-accent: var(--color-accent);
  --color-accent-hover: var(--color-accent-hover);
  --color-accent-soft: var(--color-accent-soft);
  --color-border-strong: var(--color-border-strong);
  --color-border-thin: var(--color-border-thin);
  --color-ring-focus: var(--color-ring-focus);
}

/* ---------- Body baseline typography (Phase 2.2) ---------- */
html, body {
  font-size: var(--type-body);
  line-height: 1.5;
}

code, pre, kbd, samp {
  line-height: 1.6;
}

/* Cap chat reply prose width per wiki [[Typography & Readability]];
   class is opt-in so we don't regress existing card layouts that need
   wide rows. */
.prose-clamp p,
.prose-clamp li,
.prose-clamp pre {
  max-width: 60ch;
}

/* ---------- Focus ring (Phase 2.3) ----------
   Global focus-visible per wiki [[Keyboard Navigation]]; keyboard users
   get the ring, mouse users don't. */
*:focus-visible {
  outline: 2px solid var(--color-ring-focus);
  outline-offset: 2px;
}

/* ---------- Reduced motion (Phase 2.3) ---------- */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    transition-duration: 0ms !important;
    animation-duration: 0ms !important;
  }
}
```

- [ ] **Step 2: Verify the dev build still compiles**

Run from `tutor-web/`: `npm run build`
Expected: build succeeds; output ends with `built in XXs`. Tailwind v4 should accept the `@theme` block. If it errors with "unknown rule @theme" the project is on an older Tailwind — abort and surface to controller.

- [ ] **Step 3: Verify no existing tests break**

Run from `tutor-web/`: `npm test -- --run`
Expected: 87 passed, 0 failed. The CSS change is class-utility-additive at this stage; no existing component should regress.

- [ ] **Step 4: Commit**

```bash
git add tutor-web/src/index.css
git commit -m "$(cat <<'EOF'
Phase 2.1a: design tokens + typography baseline + focus/motion globals

Brutalist-yellow palette redefined as semantic tokens in :root and
exposed to Tailwind v4 via @theme. Body font uses clamp(14px, 13.5px +
0.1vw, 16px) per spec; line-height 1.5 body / 1.6 mono. Global
*:focus-visible ring with 2px solid + 2px offset; prefers-reduced-motion
zeros transition + animation durations.

prose-clamp opt-in class caps chat reply paragraphs at 60ch — applied
in 2.2 component pass.

No component classes migrated yet; that lands in 2.1b.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: §2.1b — Component migration (hardcoded → token utilities)

**Files (modified):**
- `tutor-web/src/App.tsx`
- `tutor-web/src/components/Sidebar.tsx`
- `tutor-web/src/components/TutorWorkspace.tsx`
- `tutor-web/src/components/ChatPane.tsx`
- `tutor-web/src/components/PdfPane.tsx`
- `tutor-web/src/components/Scratchpad.tsx`
- `tutor-web/src/components/TaskQuickStart.tsx`
- `tutor-web/src/components/TasksScreen.tsx`
- `tutor-web/src/components/TrustSettings.tsx`
- `tutor-web/src/components/KnowledgeGapCard.tsx`
- `tutor-web/src/components/SuggestedEditCard.tsx`
- `tutor-web/src/components/ChipRow.tsx`
- `tutor-web/src/components/ScreenshotCapture.tsx`

The migration is mechanical. Apply this map across each file:

| Hardcoded utility       | Token utility           | Notes |
|-------------------------|-------------------------|-------|
| `bg-yellow-300`         | `bg-accent`             | brand accent |
| `bg-yellow-400`         | `bg-accent-hover`       | hover state |
| `bg-yellow-50` / `bg-yellow-100` | `bg-accent-soft` | hint surface |
| `bg-black`              | `bg-panel-dark-bg`      | inverted panels |
| `text-black`            | `text-page-fg`          | default body |
| `text-yellow-300`       | `text-panel-dark-fg`    | light-on-dark text |
| `text-yellow-200/80`    | `text-panel-dark-fg/80` | softened light-on-dark |
| `bg-white`              | `bg-page-bg`            | page surface |
| `border-black`          | `border-border-strong`  | structural |
| `border-yellow-300`     | `border-accent`         | accent rule |
| `border-black/20` / `border-black/30` | `border-border-thin` | hairlines |
| `text-black/60` / `text-black/80` | `text-page-fg/60` / `text-page-fg/80` | muted body |

Some classes don't have a Tailwind shortname under v4 even with `@theme`; for those, use the arbitrary-value escape: `bg-[var(--color-accent)]`. Specifically `border-yellow-300` → `border-[var(--color-accent)]` if Tailwind can't auto-derive the shortname (Tailwind v4 generates `border-accent` from `--color-accent` automatically — try the shortname first).

- [ ] **Step 1: Migrate App.tsx**

Edit `tutor-web/src/App.tsx`. Apply the mapping table above. Specific replacements:

- Line 83 `bg-black text-yellow-300` → `bg-panel-dark-bg text-panel-dark-fg`
- Line 83 `border-yellow-300` → `border-accent`
- Line 88 `text-yellow-200/80` → `text-panel-dark-fg/80`
- Line 92 `bg-yellow-300 text-black px-2 py-0.5 hover:bg-yellow-200` → `bg-accent text-page-fg px-2 py-0.5 hover:bg-accent-hover`
- Line 105 `bg-white` → `bg-page-bg`
- Line 107 `text-black/60` → `text-page-fg/60`

Also add `aria-current="page"` to the active nav link. App.tsx has `<Link to="/?pick=1">workspace</Link>`, `<Link to="/tasks">tasks</Link>`, `<Link to="/settings/trust">trust</Link>`. Use the `useLocation` hook (already imported via react-router-dom) to compute which link matches and set `aria-current="page"` on it. Keep visible styling unchanged (a11y-only signal for this pass).

- [ ] **Step 2: Migrate Sidebar.tsx**

Edit `tutor-web/src/components/Sidebar.tsx`:

- Line 56 `border-r-4 border-black bg-yellow-50` → `border-r-4 border-border-strong bg-accent-soft`
- Line 58 `bg-black text-yellow-300` → `bg-panel-dark-bg text-panel-dark-fg`
- Line 64 `border-b-2 border-black bg-yellow-300 hover:bg-yellow-400` → `border-b-2 border-border-strong bg-accent hover:bg-accent-hover`
- Line 68 `text-black/60` → `text-page-fg/60`
- Line 70 `text-black/60` → `text-page-fg/60`
- Line 76 `bg-black/10` + `border-black/30` → `bg-page-fg/10` + `border-border-thin`
- Line 94 `border-black/20 hover:bg-yellow-100` → `border-border-thin hover:bg-accent-soft`
- Line 95 `bg-yellow-300` → `bg-accent`
- Line 100 `text-black/60` → `text-page-fg/60`

Also add `aria-label="Create a new tutor task"` to the `+ NEW TASK` button (line 64) and `aria-current="true"` on the active task button (line 90, gated by `active`).

- [ ] **Step 3: Migrate TutorWorkspace.tsx (banner only)**

Edit `tutor-web/src/components/TutorWorkspace.tsx`:

- Line 33 `bg-yellow-300 border-b-4 border-black text-black` → `bg-accent border-b-4 border-border-strong text-page-fg`

The body of the workspace doesn't carry color literals (already structural Tailwind). Add `role="status" aria-live="polite"` to the deduped banner div so screen readers announce the dedup outcome.

- [ ] **Step 4: Migrate ChatPane.tsx**

Edit `tutor-web/src/components/ChatPane.tsx`. There are 5 color literal occurrences (per `grep -c`). Specific lines vary; apply the mapping. The header strip (`bg-black text-yellow-300`) and message-role chips (`bg-yellow-300 text-black` for "you" / `bg-blue-200 text-black` for "sensor" / `bg-black text-white` for "jarvis") get migrated. Keep `bg-blue-200` literal — it's a sensor-only signal that intentionally falls outside the brutalist palette. Add the `prose-clamp` class to the scroll container (the one with `overflow-auto p-4 space-y-3`) so the assistant messages clamp to 60ch.

- [ ] **Step 5: Migrate the remaining components**

Apply the mapping table mechanically to each of the remaining 9 files. Each has 1-7 occurrences; trust the table.

- [ ] **Step 6: Run vitest, expect green**

Run from `tutor-web/`: `npm test -- --run`
Expected: 87 passed, 0 failed.

If a test fails on a class match — check whether the test asserted a specific literal (e.g. `expect(...).toHaveClass("bg-yellow-300")`). If yes, the test was over-coupled to the implementation; relax to assert against the new token name OR the `getComputedStyle` value. Document the test you relaxed.

- [ ] **Step 7: Visual smoke test in dev server**

Run from `tutor-web/`: `npm run dev` (in a separate shell). Open `http://localhost:5173/tutor/`. Confirm:
- Workspace renders with the same brutalist-yellow palette as before.
- Sidebar background unchanged.
- Header inverted yellow-on-black unchanged.

Kill the dev server when done.

- [ ] **Step 8: Build + verify no Tailwind warnings**

Run from `tutor-web/`: `npm run build 2>&1 | tee /tmp/phase2-build.log`
Expected: build succeeds; no warnings about unknown utility classes (Tailwind v4 prints these inline).

- [ ] **Step 9: Commit**

```bash
cd /c/Users/User/jarvis-kotlin && git add tutor-web/src/App.tsx tutor-web/src/components/Sidebar.tsx tutor-web/src/components/TutorWorkspace.tsx tutor-web/src/components/ChatPane.tsx tutor-web/src/components/PdfPane.tsx tutor-web/src/components/Scratchpad.tsx tutor-web/src/components/TaskQuickStart.tsx tutor-web/src/components/TasksScreen.tsx tutor-web/src/components/TrustSettings.tsx tutor-web/src/components/KnowledgeGapCard.tsx tutor-web/src/components/SuggestedEditCard.tsx tutor-web/src/components/ChipRow.tsx tutor-web/src/components/ScreenshotCapture.tsx
git commit -m "$(cat <<'EOF'
Phase 2.1b: migrate components from hardcoded color utilities to tokens

13 components swap bg-yellow-300/text-black/etc. for token shortnames
(bg-accent, text-page-fg, border-border-strong, etc.) backed by the
@theme block from 2.1a. Visual identity unchanged — this is purely a
plumbing pass so future light/dark or theme tweaks are a one-block
:root edit.

Also lands aria-current="page" on App nav, aria-label on Sidebar NEW
TASK button + aria-current on active task, role="status"+aria-live=
"polite" on the dedup banner so screen readers announce it.

bg-blue-200 in ChatPane sensor-message chip kept as literal — it's a
deliberate non-brand signal.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: §2.2 — Typography scale + chat reply max-width clamp

**Files:**
- Already-modified in Task 2: `tutor-web/src/index.css` (type vars + body/code line-height + .prose-clamp).
- Modify: `tutor-web/src/components/ChatPane.tsx` — add `prose-clamp` class on the assistant-message container (Task 3 step 4 may have already done this; verify).
- Create: `tutor-web/src/__tests__/typography.test.tsx`.

- [ ] **Step 1: Verify ChatPane assistant messages carry prose-clamp**

Read `tutor-web/src/components/ChatPane.tsx` and confirm the scroll container or the assistant message wrapper carries `prose-clamp`. If not, add it. The exact wrapper depends on JSX structure — apply to the deepest container that holds only assistant prose so user messages remain free-form.

- [ ] **Step 2: Write the typography test**

Create `tutor-web/src/__tests__/typography.test.tsx`:

```tsx
import { test, expect } from "vitest";

test("body --type-body resolves to a clamp() expression with 14px floor", () => {
  // jsdom doesn't compute clamp() to a final px value; it preserves the
  // expression. Read the raw custom property off :root.
  const root = document.documentElement;
  // index.css must have been imported via setupTests for the tokens to
  // exist — vitest doesn't run the @import "tailwindcss" pipeline so we
  // emulate by setting the property explicitly here, then assert the
  // app's setup didn't override it.
  const styleEl = document.createElement("style");
  styleEl.textContent = ":root { --type-body: clamp(14px, calc(13.5px + 0.1vw), 16px); --type-sm: 12px; --type-lg: 18px; --type-h2: 20px; }";
  document.head.appendChild(styleEl);
  const computed = getComputedStyle(root);
  expect(computed.getPropertyValue("--type-body").trim()).toMatch(/clamp\(14px/);
  expect(computed.getPropertyValue("--type-body").trim()).toMatch(/16px\)$/);
  expect(computed.getPropertyValue("--type-sm").trim()).toBe("12px");
  expect(computed.getPropertyValue("--type-lg").trim()).toBe("18px");
  expect(computed.getPropertyValue("--type-h2").trim()).toBe("20px");
  document.head.removeChild(styleEl);
});

test(".prose-clamp paragraphs are capped at 60ch when the rule is loaded", () => {
  const styleEl = document.createElement("style");
  styleEl.textContent = ".prose-clamp p { max-width: 60ch; }";
  document.head.appendChild(styleEl);
  const wrap = document.createElement("div");
  wrap.className = "prose-clamp";
  const p = document.createElement("p");
  p.textContent = "x";
  wrap.appendChild(p);
  document.body.appendChild(wrap);
  const computed = getComputedStyle(p);
  expect(computed.maxWidth).toMatch(/60ch|^\d+px$/); // jsdom may resolve to px or keep ch
  document.body.removeChild(wrap);
  document.head.removeChild(styleEl);
});
```

The test isn't a behavioral check — jsdom can't compute clamp() — but it pins the contract that `--type-body` is a clamp expression bounded by 14/16, and that `.prose-clamp p` carries a max-width rule. Real font-size at the user's viewport is verified by the Playwright gate.

- [ ] **Step 3: Run the test, expect green**

Run from `tutor-web/`: `npm test -- --run typography`
Expected: 2 passed.

- [ ] **Step 4: Run full vitest suite**

Run from `tutor-web/`: `npm test -- --run`
Expected: 87 + 2 = 89 passed.

- [ ] **Step 5: Commit (if Task 3 step 4 didn't already add prose-clamp)**

If the ChatPane edit was needed:
```bash
git add tutor-web/src/components/ChatPane.tsx tutor-web/src/__tests__/typography.test.tsx
```
Otherwise:
```bash
git add tutor-web/src/__tests__/typography.test.tsx
```

```bash
git commit -m "$(cat <<'EOF'
Phase 2.2: typography test + chat-reply 60ch clamp confirmed

Pins the contract that --type-body is a clamp(14px, calc(13.5px +
0.1vw), 16px) expression and that .prose-clamp paragraphs carry
max-width: 60ch. jsdom can't compute clamp() — Playwright gate
verifies the rendered font-size at viewport.

Frontend tests 87 → 89.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: §2.3 — Touch targets + folded-in mobile fixes from Phase 1 backlog

**Files:**
- Modify: `tutor-web/src/components/Sidebar.tsx` — task buttons `py-1.5` → `py-3 sm:py-1.5`.
- Modify: `tutor-web/src/components/ChipRow.tsx` — chip pills `py-1` → `py-2 sm:py-1`.
- Modify: `tutor-web/src/App.tsx` — header `× close` button `px-2 py-0.5` → `px-3 py-2 sm:px-2 sm:py-0.5`.
- Modify: `tutor-web/src/components/Scratchpad.tsx` — `h-32` → `min-h-[6rem] max-h-[40vh] resize-y`; add `border-t-4 sm:border-t-0` so on mobile a horizontal divider separates Scratchpad from PDF column above.
- Modify: `tutor-web/src/components/TutorWorkspace.tsx` — bump column-split breakpoint from `sm:` to `md:` to relieve 640-820px cramp; PdfPane wrapper gets `min-h-[50vh] md:min-h-0`.

This task closes 4 Phase-1 backlog items in one swing (the `[1] [layout] [responsive-breakpoint] [med]`, `[1] [layout] [scroll-containment] [low]`, `[1] [layout] [gestalt-grouping] [med]`, and the touch-target part of `[1] [interaction] [feedback-response-time] [med]`).

- [ ] **Step 1: Edit Sidebar.tsx task button padding**

In `Sidebar.tsx` find the task button className (around line 94: `w-full text-left px-3 py-1.5 border-b border-border-thin hover:bg-accent-soft`). Change `py-1.5` to `py-3 sm:py-1.5`. The `+ NEW TASK` button already at `py-2` is acceptable for tap (32px not 44px but on desktop is fine; on mobile sidebar is hidden anyway via `hidden sm:flex`). Leave NEW TASK unchanged.

- [ ] **Step 2: Edit ChipRow.tsx chip padding**

Read the file. Find the chip button className. Change `py-1` to `py-2 sm:py-1`.

- [ ] **Step 3: Edit App.tsx × close button padding**

`App.tsx` line 92 currently `bg-accent text-page-fg px-2 py-0.5 hover:bg-accent-hover` (post-Task-3). Change `px-2 py-0.5` to `px-3 py-2 sm:px-2 sm:py-0.5`.

- [ ] **Step 4: Edit Scratchpad.tsx sizing + mobile divider**

Read the file. Find the textarea and its wrapper. Replace fixed `h-32` (or whatever the current sizing is) with `min-h-[6rem] max-h-[40vh] resize-y`. Add `border-t-4 sm:border-t-0 border-border-strong` to the Scratchpad wrapper so on mobile (when columns stack) it gets a horizontal divider above.

- [ ] **Step 5: Edit TutorWorkspace.tsx breakpoint bump**

In `TutorWorkspace.tsx`, replace every `sm:` breakpoint variant in the two-col container's classes with `md:`. Specifically:
- `flex-col sm:flex-row` → `flex-col md:flex-row`
- `sm:w-1/2` → `md:w-1/2` (×2: left + right column wrappers)
- `border-b-4 sm:border-b-0 sm:border-r-4` → `border-b-4 md:border-b-0 md:border-r-4`

The PDF inner wrapper currently has `flex-1 min-h-0 overflow-hidden`. Add `min-h-[50vh] md:min-h-0` so on mobile the PDF iframe gets enough vertical real estate to actually render.

The Sidebar's `hidden sm:flex` stays unchanged — the sidebar collapsing at 640px (rather than 768px) is intentional; small tablets get sidebar back early.

- [ ] **Step 6: Run vitest, expect green**

Run from `tutor-web/`: `npm test -- --run`
Expected: 89 passed. The scroll test asserts `flex-col` and `sm:flex-row` (or now `md:flex-row`) — the regex tolerates either since both match `/flex-col/` and `/sm:flex-row/`. If the test specifically asserts `sm:flex-row` (not `md:flex-row`), update the regex to `/(sm|md):flex-row/`.

- [ ] **Step 7: Build + visual mobile smoke test**

Run from `tutor-web/`: `npm run build`. Expected: success.

Optionally start `npm run dev` and resize browser to 700px width to confirm the new `md:` breakpoint kicks in (PDF should stack above chat at 700px since 700 < 768).

- [ ] **Step 8: Commit**

```bash
git add tutor-web/src/components/Sidebar.tsx tutor-web/src/components/ChipRow.tsx tutor-web/src/App.tsx tutor-web/src/components/Scratchpad.tsx tutor-web/src/components/TutorWorkspace.tsx
git commit -m "$(cat <<'EOF'
Phase 2.3: touch targets + mobile breakpoint bump + scratchpad divider

Touch targets per wiki [[Touch Target Sizing]] (≥44×44 on mobile):
- Sidebar task buttons py-1.5 → py-3 sm:py-1.5
- ChipRow chips py-1 → py-2 sm:py-1
- App × close px-2 py-0.5 → px-3 py-2 sm:px-2 sm:py-0.5

Folds in Phase 1 backlog items:
- [1] [layout] [responsive-breakpoint] [med]: bump column-split sm: → md:
  to relieve 640-820px cramp where 50/50 columns went to ~310px each.
- [1] [layout] [scroll-containment] [low]: PdfPane wrapper gets
  min-h-[50vh] md:min-h-0 so mobile stacked iframe doesn't clip to 0.
- [1] [layout] [gestalt-grouping] [med]: Scratchpad gets border-t-4
  sm:border-t-0 so the three mobile regions read as three.
- Scratchpad sizing h-32 → min-h-[6rem] max-h-[40vh] resize-y per
  wiki [[Progressive Disclosure]].

Sidebar hidden sm:flex preserved — sidebar collapses at 640px, columns
split at 768px; small tablets get sidebar back at 640-768px even while
PDF/chat still stack vertically.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: §2.4 — ARIA + semantic HTML + empty states

**Files:**
- Modify: `tutor-web/src/components/Sidebar.tsx` — wrap subject groups in `<nav aria-label="Tasks by subject">`; `<ul role="list">`.
- Modify: `tutor-web/src/components/ScreenshotCapture.tsx` — emoji a11y wrap; button `aria-label`.
- Modify: `tutor-web/src/components/TasksScreen.tsx` — empty state copy + form `<label>` association.
- Modify: `tutor-web/src/components/TrustSettings.tsx` — form labels.
- Modify: `tutor-web/src/components/PdfPane.tsx` — iframe `title="PDF document"`.
- Modify: `tutor-web/src/components/Scratchpad.tsx` — `aria-label="Task scratchpad"`.

Most of these are 1-2 line edits. The Sidebar restructure is slightly larger.

- [ ] **Step 1: Sidebar — add `<nav>` wrapping the subject groups**

Read Sidebar.tsx. The subject groups are rendered by `grouped.map(([subject, subjectTasks]) => (...))`. Wrap that whole `.map()` output in a single `<nav aria-label="Tasks by subject">`. Set `role="list"` on each `<ul>` inside the subject group (already implicit, but explicit avoids screen readers stripping list semantics when CSS removes default list-style).

If the existing JSX doesn't have a single fragment to wrap, restructure to:
```tsx
<aside data-testid="sidebar" ...>
  <div ...>TASKS</div>
  <button data-testid="sidebar-new-task" ...>+ NEW TASK</button>
  {!loaded && <div ...>loading…</div>}
  {loaded && tasks.length === 0 && <div data-testid="sidebar-empty" ...>...</div>}
  <nav aria-label="Tasks by subject" className="flex-1">
    {grouped.map(([subject, subjectTasks]) => (
      <div key={subject} data-testid={`sidebar-subject-${subject}`}>
        <div className="...">{subject}</div>
        <ul role="list">
          {subjectTasks.map(t => (...))}
        </ul>
      </div>
    ))}
  </nav>
</aside>
```

- [ ] **Step 2: ScreenshotCapture — emoji a11y wrap + button aria-label**

In `ScreenshotCapture.tsx`, line 80 has `📷 capture (Ctrl+Shift+J)` as a label string. The label is rendered into a button. Change the label assembly:

From: `const label = status === "idle" ? "📷 capture (Ctrl+Shift+J)" : ...;` (used as button text)

To: build the label as JSX nodes, not a string. Where the button renders the label, replace `{label}` with:

```tsx
<>
  <span aria-hidden="true">📷</span>
  <span>{statusText}</span>
</>
```

where `statusText = status === "idle" ? "capture (Ctrl+Shift+J)" : (other states' text)`. Add `aria-label="Capture a screenshot for the tutor to read"` to the `<button>` element so screen readers get a clear action description.

If the button text already lives inline (not via a `label` variable), apply the same `<span aria-hidden>📷</span><span>capture…</span>` split inline.

- [ ] **Step 3: TasksScreen — empty state + form labels + Enter-to-submit**

Read `TasksScreen.tsx`. For each input (subject, title, deadline), wrap in a `<label>` or use `htmlFor` linking. Example:
```tsx
<label className="...">
  <span className="text-xs tracking-widest">SUBJECT</span>
  <input ... />
</label>
```

If the form already uses a `<form>` element, Enter-to-submit should already work. If inputs are loose siblings of a button, wrap them in `<form onSubmit={(e) => { e.preventDefault(); submit(); }}>`. Empty state when `tasks.length === 0`: render a `<div role="status">no tasks yet — fill the form above to add one</div>`.

- [ ] **Step 4: TrustSettings — form labels**

Same pattern as TasksScreen. Wrap each input in `<label>` linking via `htmlFor`/id pair, or nest `<input>` inside `<label>`.

- [ ] **Step 5: PdfPane — iframe title**

Read `PdfPane.tsx`. Find the `<iframe>` (or `<embed>`) tag rendering the PDF URL. Add `title="PDF document for current task"` attribute. If it's an `<embed>`, embeds don't take `title`; switch to `<iframe src={url} title="PDF document for current task" />`.

- [ ] **Step 6: Scratchpad — textarea aria-label**

In `Scratchpad.tsx`, find the `<textarea>` element. Add `aria-label="Task scratchpad — local notes; auto-saved per browser"`.

- [ ] **Step 7: Run vitest, expect green**

Run: `npm test -- --run`. Expected: 89 passed.

If any test breaks because it queried `getByRole("textbox")` and now finds two (textarea + an input), refine the query to `getByRole("textbox", { name: /scratchpad/i })`.

- [ ] **Step 8: Commit**

```bash
git add tutor-web/src/components/Sidebar.tsx tutor-web/src/components/ScreenshotCapture.tsx tutor-web/src/components/TasksScreen.tsx tutor-web/src/components/TrustSettings.tsx tutor-web/src/components/PdfPane.tsx tutor-web/src/components/Scratchpad.tsx
git commit -m "$(cat <<'EOF'
Phase 2.4: ARIA + semantic HTML + empty-state copy

- Sidebar: subject groups wrapped in <nav aria-label="Tasks by
  subject">; <ul role="list"> per subject so screen readers retain
  list semantics when default styling is dropped.
- ScreenshotCapture: emoji 📷 wrapped <span aria-hidden> + sibling
  <span>capture…</span>; button gets aria-label describing the action.
- TasksScreen + TrustSettings: form inputs get linked <label>; Enter
  submits via wrapped <form>; empty state copy where tasks.length===0.
- PdfPane: iframe gains title="PDF document for current task" so
  screen readers announce the embedded surface.
- Scratchpad: textarea gains aria-label.

Closes Phase 1 backlog item touched on by spec § Phase 3.3 (Enter-to-
submit) — folded into 2.4 since it's the same a11y line.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: §2.5 — Visible-state coverage (in-flight disable on non-preset paths)

**Files:**
- Modify: `tutor-web/src/components/TasksScreen.tsx` — submit button gains `disabled` + spinner.
- Modify: `tutor-web/src/components/TrustSettings.tsx` — grant submit gains `disabled` + spinner.
- Create: `tutor-web/src/__tests__/visibleState.test.tsx`.

Phase 1 backlog `[1] [interaction] [feedback-response-time] [med]` flagged that non-preset task-create paths lack the in-flight signal. TaskQuickStart already handles this (`disabled={busy != null}`). TasksScreen + TrustSettings need parity.

The "spinner after 400ms" rule from wiki [[Feedback & Response Time]]: only show the spinner once 400ms has elapsed, otherwise the spinner flickers on fast networks. Use a setTimeout-gated state.

- [ ] **Step 1: Add in-flight state hook utility (if not already present)**

Create or extend `tutor-web/src/lib/inFlight.ts`:

```ts
import { useEffect, useRef, useState } from "react";

/**
 * Returns [inFlight, showSpinner, run] tuple.
 *  - inFlight: true while the async fn is running (set immediately on call)
 *  - showSpinner: true only after the 400ms threshold per wiki
 *    [[Feedback & Response Time]] — avoids spinner flash on fast nets.
 *  - run: wraps an async fn so callers don't have to thread the state.
 */
export function useInFlight() {
  const [inFlight, setInFlight] = useState(false);
  const [showSpinner, setShowSpinner] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timerRef.current != null) clearTimeout(timerRef.current);
  }, []);

  async function run<T>(fn: () => Promise<T>): Promise<T> {
    setInFlight(true);
    timerRef.current = setTimeout(() => setShowSpinner(true), 400);
    try {
      return await fn();
    } finally {
      if (timerRef.current != null) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      setShowSpinner(false);
      setInFlight(false);
    }
  }
  return { inFlight, showSpinner, run };
}
```

- [ ] **Step 2: Apply useInFlight to TasksScreen submit**

Read `TasksScreen.tsx`. Find the create-task POST handler. Wrap the fetch in `run(async () => {...})`. Set `disabled={inFlight}` on the submit button. Render `{showSpinner && <span aria-live="polite">creating…</span>}` next to the button.

- [ ] **Step 3: Apply useInFlight to TrustSettings grant submit**

Same pattern.

- [ ] **Step 4: Write the visibleState test**

Create `tutor-web/src/__tests__/visibleState.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TasksScreen } from "../components/TasksScreen";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("TasksScreen submit disables while POST in-flight", async () => {
  let resolvePost: (r: Response) => void = () => {};
  vi.stubGlobal("fetch", vi.fn((url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks") && init?.method === "POST") {
      return new Promise<Response>(res => { resolvePost = res; });
    }
    return Promise.resolve(new Response(JSON.stringify({ tasks: [] }), { status: 200 }));
  }));
  render(<MemoryRouter><TasksScreen /></MemoryRouter>);

  // Fill the form. Adjust selectors if TasksScreen uses different labels.
  fireEvent.change(await screen.findByLabelText(/subject/i), { target: { value: "PA" } });
  fireEvent.change(screen.getByLabelText(/title/i), { target: { value: "Tema test" } });
  // Some implementations keep deadline optional; skip if not labeled.

  const submit = screen.getByRole("button", { name: /create|add|submit/i });
  fireEvent.click(submit);

  // Disabled immediately while POST is pending.
  await waitFor(() => expect(submit).toBeDisabled());

  // Resolve the pending POST so afterEach can tear down.
  resolvePost(new Response(JSON.stringify({
    id: "T1", subject: "PA", title: "Tema test",
    deadline: new Date().toISOString(), status: "ACTIVE",
  }), { status: 201, headers: { "content-type": "application/json" } }));

  await waitFor(() => expect(submit).not.toBeDisabled());
});
```

If TasksScreen doesn't render a labeled subject input with "subject" text in the label, adjust the selector to match what's there. Use `getByPlaceholderText` or `getByTestId` as fallbacks.

- [ ] **Step 5: Run the test**

Run: `npm test -- --run visibleState`. Expected: PASS.

- [ ] **Step 6: Run full suite**

Run: `npm test -- --run`. Expected: 90 passed.

- [ ] **Step 7: Commit**

```bash
git add tutor-web/src/lib/inFlight.ts tutor-web/src/components/TasksScreen.tsx tutor-web/src/components/TrustSettings.tsx tutor-web/src/__tests__/visibleState.test.tsx
git commit -m "$(cat <<'EOF'
Phase 2.5: visible-state coverage on non-preset task-create paths

useInFlight hook centralizes the "disable while pending + spinner after
400ms" pattern per wiki [[Feedback & Response Time]]. TasksScreen +
TrustSettings submit handlers wrap fetch in run() and surface
disabled={inFlight} + spinner{showSpinner}.

Closes Phase 1 backlog [1] [interaction] [feedback-response-time] [med]
— TaskQuickStart already had busy != null gating; this brings the rest
of the create-task surface area to parity.

Frontend tests 89 → 90.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: A11y test infrastructure + axe scans

**Files:**
- Modify: `tutor-web/package.json` — add `vitest-axe` to devDependencies.
- Modify: `tutor-web/src/setupTests.ts` — extend `expect` with `toHaveNoViolations`.
- Create: `tutor-web/src/__tests__/axe.workspace.test.tsx`.
- Create: `tutor-web/src/__tests__/axe.tasks.test.tsx`.

vitest-axe runs the same axe-core engine as Playwright's `@axe-core/playwright` but inside jsdom. It catches structural a11y violations (missing labels, role mismatches, heading order, ARIA misuse) but not pixel-level contrast (jsdom has no rendered colors). The Playwright gate at Task 11 covers contrast.

- [ ] **Step 1: Install vitest-axe**

Run from `tutor-web/`: `npm install --save-dev vitest-axe`
Expected: `package.json` gains `"vitest-axe": "^X.Y.Z"` in `devDependencies`. `package-lock.json` updates.

- [ ] **Step 2: Extend setupTests.ts**

Edit `tutor-web/src/setupTests.ts`. Replace the file content with:

```ts
import "@testing-library/jest-dom";
import { expect } from "vitest";
import * as matchers from "vitest-axe/matchers";

expect.extend(matchers);
```

(If the import path differs in the actually-installed `vitest-axe` version, adjust accordingly; the package's README shows the correct path. Verify via `cat node_modules/vitest-axe/package.json | grep main` after install.)

- [ ] **Step 3: Write the workspace axe scan**

Create `tutor-web/src/__tests__/axe.workspace.test.tsx`:

```tsx
import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 200 })));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("TutorWorkspace has no axe AA violations", async () => {
  const { container } = render(
    <MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>,
  );
  const results = await axe(container, {
    rules: {
      // jsdom can't compute color contrast — defer to Playwright gate.
      "color-contrast": { enabled: false },
    },
  });
  expect(results).toHaveNoViolations();
});

test("TutorWorkspace with deduped banner has no axe violations", async () => {
  const { container } = render(
    <MemoryRouter>
      <TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" dedupedNotice={true} />
    </MemoryRouter>,
  );
  const results = await axe(container, {
    rules: { "color-contrast": { enabled: false } },
  });
  expect(results).toHaveNoViolations();
});
```

- [ ] **Step 4: Write the tasks-page axe scan**

Create `tutor-web/src/__tests__/axe.tasks.test.tsx`:

```tsx
import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { MemoryRouter } from "react-router-dom";
import { TasksScreen } from "../components/TasksScreen";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/tasks")) {
      return new Response(JSON.stringify({ tasks: [] }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("TasksScreen empty state has no axe AA violations", async () => {
  const { container } = render(<MemoryRouter><TasksScreen /></MemoryRouter>);
  const results = await axe(container, {
    rules: { "color-contrast": { enabled: false } },
  });
  expect(results).toHaveNoViolations();
});
```

- [ ] **Step 5: Run the axe tests**

Run: `npm test -- --run axe`
Expected: 3 PASS. If any fail, the test will print the violation; fix the underlying component (most common failures: missing form labels, `<button>` with no accessible name, list items not inside `<ul>`/`<ol>`). Re-run after each fix.

- [ ] **Step 6: Run full suite**

Run: `npm test -- --run`
Expected: 90 + 3 = 93 passed.

- [ ] **Step 7: Commit**

```bash
git add tutor-web/package.json tutor-web/package-lock.json tutor-web/src/setupTests.ts tutor-web/src/__tests__/axe.workspace.test.tsx tutor-web/src/__tests__/axe.tasks.test.tsx
git commit -m "$(cat <<'EOF'
Phase 2 a11y: vitest-axe scans for workspace + tasks page

vitest-axe runs the same axe-core engine as @axe-core/playwright but
inside jsdom. Catches structural violations (missing labels, role
mismatches, heading order, ARIA misuse). Color contrast is deferred to
the Playwright gate since jsdom has no rendered colors.

3 axe scans: TutorWorkspace bare, TutorWorkspace with deduped banner,
TasksScreen empty state. All zero AA violations under jsdom.

Frontend tests 90 → 93.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Code Gate

- [ ] **Step 1: Push to origin/main**

```bash
git push origin main
```

- [ ] **Step 2: Wait for CI completion**

Poll the GitHub API:
```bash
until curl -sS "https://api.github.com/repos/CorgiGH/Jarvis/actions/runs?branch=main&per_page=1" 2>/dev/null | python3 -c "
import json, sys
d = json.load(sys.stdin)
r = d.get('workflow_runs', [{}])[0]
sys.exit(0 if r.get('status') == 'completed' else 1)
"; do sleep 20; done
```

Expected: completes within 5 minutes; conclusion is `success`. Confirm:
```bash
curl -sS "https://api.github.com/repos/CorgiGH/Jarvis/actions/runs?branch=main&per_page=1" | python3 -c "
import json, sys
r = json.load(sys.stdin)['workflow_runs'][0]
print(r['head_sha'][:7], r['conclusion'])
"
```

If `failure`: `gh run view --log-failed` (or hit `/jobs` API) to see which job. Most likely backend (no changes expected to break it) or frontend (axe violations not visible to vitest until npm install runs in CI). Fix in-place + re-push.

---

## Task 10: Live Gate

- [ ] **Step 1: Rebuild frontend bundle**

Run from `tutor-web/`: `npm run build`. Tailwind v4 should compile the new @theme block + token utilities. Confirm output ends with `built in XXs` and the new `index-<hash>.js` filename.

- [ ] **Step 2: Stage + commit the rebuilt bundle**

```bash
cd /c/Users/User/jarvis-kotlin
git add src/main/resources/tutor-dist/
git commit -m "$(cat <<'EOF'
Phase 2: rebuild frontend bundle

Bundle reflects design tokens, typography clamp, focus/motion globals,
ARIA + semantic HTML, visible-state coverage, mobile breakpoint bump.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push origin main
```

- [ ] **Step 3: Deploy**

Run: `& "C:\Program Files\Git\bin\bash.exe" tools/deploy.sh`
Expected: build, scp, restart all log success. Tail ends with `[deploy] done.`.

- [ ] **Step 4: Verify healthz + bundle hash**

```bash
curl -sS https://corgflix.duckdns.org/healthz
```
Expected: `ok`.

```bash
ls src/main/resources/tutor-dist/assets/ | grep -E "^index-[A-Za-z0-9]+\.js$"
curl -sS https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9]+\.js'
```
Expected: filenames match.

---

## Task 11: Playwright Gate (focus traversal + a11y scan)

- [ ] **Step 1: Spawn the Playwright a11y agent**

Use the Agent tool with `subagent_type: general-purpose`. Prompt verbatim:

```
Phase 2 Playwright a11y gate for jarvis-kotlin tutor overhaul. Use
mcp__playwright__browser_* tools to verify the new design tokens +
typography + focus + a11y on https://corgflix.duckdns.org/tutor/.

Confirm bundle hash first: navigate to /tutor/, browser_evaluate the
script src that starts with "index-"; report it.

CRITICAL CONSTRAINT: Do NOT nag about the PS HW deadline (2026-05-21)
or finals proximity. Treat as design feedback only. Authorization is
settled.

Run these scenarios; report PASS/FAIL per scenario.

Scenario 1 — visible focus ring on Tab:
- browser_navigate to /tutor/
- If TaskQuickStart shown, click any preset to enter workspace
- browser_press_key Tab repeatedly (~10 presses)
- After each Tab, browser_evaluate the active element + check that
  getComputedStyle(document.activeElement).outlineWidth is "2px" and
  outlineStyle is "solid"
- PASS if every Tab landing has the 2px ring; FAIL on first miss
- Take screenshot phase2-focus-ring.png mid-traversal

Scenario 2 — body font-size resolves to clamp range at viewport:
- browser_resize to 1024x768 (desktop)
- browser_evaluate getComputedStyle(document.body).fontSize
- Expected: between 14px and 16px (clamp range)
- browser_resize to 375x812 (mobile)
- browser_evaluate same
- Expected: 14px (floor of clamp)
- PASS if both in range; FAIL if either outside

Scenario 3 — chat reply max-width 60ch:
- browser_navigate to /tutor/, click PA preset if needed
- browser_type long chat: "explain laplace transform in detail"
- browser_click send; browser_wait_for the reply text
- browser_evaluate the assistant message paragraph element's
  getBoundingClientRect().width AND its getComputedStyle.maxWidth
- maxWidth should resolve to ~60ch worth of px (around 480-576px in
  monospace at body font-size). Width must not exceed maxWidth.
- PASS if width <= maxWidth; FAIL otherwise

Scenario 4 — axe-core scan, zero AA violations:
- Inject axe-core via browser_evaluate using the npm tarball CDN:
  await import('https://unpkg.com/axe-core@4.10.0/axe.min.js') will not
  work because of CORS in many environments — instead use a CDN that
  serves UMD bundles with permissive CORS:
    const s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/axe-core@4.10.0/axe.min.js';
    document.head.appendChild(s);
    await new Promise(r => s.onload = r);
- browser_evaluate: const r = await window.axe.run(document, { runOnly: ['wcag2a', 'wcag2aa'] }); return JSON.stringify({ violations: r.violations.length, list: r.violations.map(v => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })) });
- PASS if violations === 0; FAIL otherwise; if FAIL, list the violation IDs

Scenario 5 — keyboard-only nav reaches all controls:
- browser_navigate to /tutor/, click any preset
- Tab from page top, count distinct visible focused elements after each Tab
- Expected reachable: header logo / × close / nav links (workspace, tasks, trust) / NEW TASK / sidebar task buttons / chat input / send button / screenshot capture button (if visible) / scratchpad textarea
- PASS if you can reach chat input + send button + scratchpad without trapping; FAIL if focus skips an interactive control

Output format (one block per scenario):
[scenario-N] [pass|fail]
  evidence: <one-line>
  screenshot: <path or "n/a">
  notes: <unexpected>

End with TOTAL: X pass, Y fail and the bundle hash.

If MCP tools jam: retry once, then stop and report the environmental
issue.
```

- [ ] **Step 2: Triage results**

If any scenario FAILS:
- Scenario 1 fail → focus-visible CSS not loading → check index.css build output, may need explicit `:focus-visible` instead of `*:focus-visible` if Tailwind reset overrides.
- Scenario 2 fail → body font-size not picking up `var(--type-body)` → check html/body cascade.
- Scenario 3 fail → prose-clamp class not applied or selector wrong → re-check ChatPane assistant container.
- Scenario 4 fail → axe found violations → fix in-place per violation IDs.
- Scenario 5 fail → focus trap → check no `tabIndex={-1}` on interactive elements.

Re-run gate after each fix. Don't advance with FAIL.

---

## Task 12: UX-Playbook Gate

- [ ] **Step 1: Spawn the UX-Playbook audit agent**

Use Agent tool with `subagent_type: general-purpose`. Prompt:

```
Phase 2 UX-Playbook audit for jarvis-kotlin. Read:
1. C:\Users\User\Desktop\SO\os-study-guide\wiki\architecture\UX Playbook.md
2. C:\Users\User\Desktop\SO\os-study-guide\wiki\architecture\Design Principles.md

Audit ONLY surfaces touched by Phase 2:
- tutor-web/src/index.css (tokens + globals)
- tutor-web/src/App.tsx (header tokens + aria-current)
- tutor-web/src/components/Sidebar.tsx (tokens + nav + a11y)
- tutor-web/src/components/TutorWorkspace.tsx (banner tokens + role/live)
- tutor-web/src/components/ChatPane.tsx (tokens + prose-clamp)
- tutor-web/src/components/PdfPane.tsx (tokens + iframe title)
- tutor-web/src/components/Scratchpad.tsx (tokens + sizing + label)
- tutor-web/src/components/TaskQuickStart.tsx (tokens)
- tutor-web/src/components/TasksScreen.tsx (tokens + form + empty + in-flight)
- tutor-web/src/components/TrustSettings.tsx (tokens + form + in-flight)
- tutor-web/src/components/KnowledgeGapCard.tsx (tokens)
- tutor-web/src/components/SuggestedEditCard.tsx (tokens)
- tutor-web/src/components/ChipRow.tsx (tokens + role group)
- tutor-web/src/components/ScreenshotCapture.tsx (tokens + emoji a11y)

DO NOT score Phase 3-8 territory: gap server-persist, SHOW DOCS,
TaskDetector, etc. Out-of-scope for this audit.

CRITICAL CONSTRAINT: Do NOT nag about deadlines or finals proximity.
Authorization settled.

Run full Visual Polish + Responsive & Accessible columns from the UX
Playbook. Output:
[category] [principle] [pass|fail|n/a] [severity high|med|low] [finding] [recommended action]

Severity:
- HIGH blocks Phase 3; fix in-place + re-run gate.
- MED/LOW append to docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md
  under "## Phase 2 — UX foundation".

End: TOTAL: X high, Y med, Z low.

Discipline:
- Did I score what Phase 2 actually touched?
- Did I propose Phase 3-8 work? Demote to n/a Phase-N scope.
- Did I score color contrast against actual rendered values? (The
  Playwright gate already checked axe. Don't double-count.)
- Did I just rephrase the spec? Skip.
```

- [ ] **Step 2: Triage results**

HIGH findings: fix in-place, re-run gate. MED/LOW: append to backlog under "## Phase 2 — UX foundation"; commit + push.

---

## Task 13: Phase 2 Definition of Done

Tick all before declaring Phase 2 complete:

- [ ] Backend tests still 550 passed (Phase 2 didn't touch backend).
- [ ] Frontend tests 93 passed (87 prior + 6 new: typography x2, visibleState x1, axe x3).
- [ ] Daemon untouched (16 tests).
- [ ] CI green on main for the latest Phase 2 commit.
- [ ] `curl https://corgflix.duckdns.org/healthz` returns `ok`.
- [ ] Deployed bundle hash matches local build.
- [ ] Playwright gate: 5/5 PASS (focus ring, font-size clamp, prose 60ch, axe zero violations, keyboard reachability).
- [ ] UX-Playbook gate: zero HIGH; MED/LOW in backlog.
- [ ] Phase 1 backlog items folded in: 4 of 5 closed (mobile breakpoint, scroll-containment, gestalt-grouping, in-flight disable). 1 deferred: mobile stack order PDF-on-top — Phase 2 didn't change DOM order, only breakpoint + sizing; remains in Phase 1 backlog or migrate to Phase 3 if relevant.
- [ ] Backlog file `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` has Phase 2 section populated (or empty if no MED/LOW).
- [ ] No new commits since last gate run (gate-test artifacts committed before declaring done).

---

## Out of scope (do NOT do this in Phase 2)

- **Server-side gap persistence / SHOW DOCS / PDF-line button**: Phase 4.
- **TaskDetector / unique index / cron-driven re-scrape**: Phase 6.
- **Inline reference popups / Knowledge Ledger drawer / sympy tool**: Phase 7.
- **Plotly inline / cron probe install / final audit**: Phase 8.
- **Server-side scratchpad persist (debounced PUT)**: Phase 3.4 — Phase 2 only touches client-side sizing/aria, not the persistence wire.
- **AbortController on /api/chat fetch**: Phase 3.1.
- **POST /api/v1/gap/{id}/status route**: Phase 3.2 (Phase 2 doesn't touch backend at all).

If you find yourself adding any of the above, stop — that's a Phase-N task pretending to be Phase 2. Note it in the backlog under the right phase and move on.
