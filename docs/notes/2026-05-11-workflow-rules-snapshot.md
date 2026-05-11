# Workflow Rules Snapshot · 2026-05-11

User-global rules ratified after Slice 1 ghost-component + Slice 1.5 PDF-404 lessons. Live in `C:\Users\User\.claude\CLAUDE.md` (user-global, outside repo) and in two superpowers SKILL.md files at `C:\Users\User\.claude\plugins\cache\claude-plugins-official\superpowers\5.1.0\skills\{subagent-driven-development,writing-plans}\SKILL.md` (plugin cache, also outside repo). This file is a snapshot in the repo for audit trail + so the team can grep for the rule text without leaving the project.

## Memory verification rule (load-bearing, pre-2026-05-11)

Before acting on a memory claim that names: specific filepath / external binary / npm-pip-cargo package / HTTP route / commit SHA / bundle hash / test count / live URL: re-verify against current state via grep / curl / `which` / `ls` / `git ls-files` / `git rev-parse HEAD`. If the claim doesn't hold, update the memory file BEFORE acting. Memory = intent + history. Reality = repo + VPS. Trust reality.

## Spec-first clarification rule (load-bearing)

When in brainstorm / spec-writing / plan-writing mode AND a spec or plan already exists for the topic (under `docs/superpowers/specs/` or `docs/superpowers/plans/`):

Before drafting ANY clarifying question, grep the existing artifacts. For each candidate question, log one line:
- `Spec-check: <question>` → `spec'd at <path>:<line>: "<quoted phrase>"` (skip the question)
- `Spec-check: <question>` → `no match in <paths>; asking user` (proceed with AskUserQuestion)

Only call AskUserQuestion on items confirmed as genuine gaps.

If the user asks "is this in the spec?" — that phrase is a self-audit failure signal. Apologize tersely. Audit remaining questions against spec/plan before resuming.

Cost of one bad question = trust + tokens. Cost of one grep = ~200 tokens. Always grep first.

## Feature-shipped verification rule (post 2026-05-11 Slice 1 ghost-component lesson)

Before claiming a feature is shipped: open its user-facing surface (URL or CLI command) and confirm the user sees the feature. Bundle hash + tests green ≠ feature shipped.

The 2026-05-11 Slice 1 lesson: 5 components in bundle (`DrillStack` / `ProblemStepper` / `DrillCard` / `ProgressStrip` / `CompileSubmitCard`), all built + tested green + bundled + deployed, none mounted in `TutorWorkspace.tsx`. Live URL showed the OLD layout.

Enforcement:
- spec → plan: every "create new component" task is paired with or followed by a "mount it" task naming exact mount-site file + JSX diff
- plan self-review: grep every component file listed under "Frontend (new)" against bodies of "Frontend (modified)" tasks. Missing integration → fix before handoff
- spec-level visual acceptance: spec sections describing UI layouts MUST list `data-testid` selectors that must be visible on first paint
- SDD final review: Playwright headless against live URL asserting each spec'd selector paints. Component-shipped + tests-green is not enough.

## Interaction-smoke gate (post 2026-05-11 Slice 1.5 PDF-404 lesson)

Selectors-painted ≠ selectors-work. Slice 1.5 visual gate passed (rail rendered with PDF item) but clicking the PDF item opened a drawer with "HTTP 404 No PDF attached" — rail item carried `/static/<path>` instead of auth-gated `/api/v1/tasks/{id}/pdf`. `TutorWorkspace.tsx:28` destructured `pdfUrl: _pdfUrl` (underscore-dead-prop) → right URL was on the chain, dropped on the floor.

Final-review Playwright gate MUST extend from "selectors visible" to:

1. All N spec'd `[data-testid]` selectors visible on first paint
2. ZERO 4xx/5xx network responses during first paint (capture via `page.on('response', …)`)
3. Click every spec-listed interactive element (rail items, drawer triggers, primary buttons, nav pills)
4. After each click: assert no on-screen text matches `/404|HTTP \d{3}|not found|error/i` AND no new 4xx/5xx network responses fired

If any of (2), (4) fail: NOT shipped. Same severity as failing (1).

## Component-reuse contract rule (post 2026-05-11 Slice 1.5)

When a plan task body says "mount existing component X in new site Y" (e.g. drawer reuses `PdfPane`, sidebar reuses `Scratchpad`):

Required sub-steps in the task body:
1. `grep`/`Read` X's prop signature — show in task body verbatim
2. Show Y's wire-up JSX with prop values explicitly named (no `...spread`)
3. Run `tsc --noEmit` on the diff before commit — catches prop-type mismatches
4. Add a unit test asserting Y renders X without runtime errors, mocking X's primary external dep. The mock asserts the URL/payload shape Y passes IS what X expects.

Closes "wired up but with wrong shape" gap.

## Underscore-dead-prop rule (post 2026-05-11 Slice 1.5)

Destructured component props with `_` prefix in production code = workflow smell. `_` = "I'm ignoring this on purpose" = the parent's contract is stale OR the new layout dropped a responsibility without removing the prop from the parent's pass-down.

Action: every `_propName` either becomes used or gets removed from parent's pass-down + component's prop interface. Lint rule when ESLint exists. Manual check until then.

The 2026-05-11 lesson: `TutorWorkspace.tsx:28` destructured `pdfUrl: _pdfUrl` because the new layout moved PDF rendering into `ResourceRail`'s drawer. The parent (`App.tsx:193`) still passed `pdfUrl={\`/api/v1/tasks/${taskId}/pdf\`}`. `ResourceRail` built its own URL from `rail_json.payload.path` — wrong URL. The right `pdfUrl` was on the prop chain, dropped at the underscore.

## Plan self-review checks (writing-plans skill, post 2026-05-11)

Self-review section now requires:

**1. Spec coverage:** Skim each spec section/requirement. Point to a task that implements it.

**2. Placeholder scan:** Search plan for "TBD" / "TODO" / "implement later" / "Add appropriate error handling" / "Similar to Task N" without code. Fix.

**3. Type consistency:** Types, signatures, property names match across tasks.

**4. Build+mount pairing (post 2026-05-11):** Every "create new component" task has paired or following "mount it" task naming exact mount-site file + JSX diff. Grep plan for every file listed under "Frontend (new)" — must appear in body of a "Frontend (modified)" task as import or JSX element. File-structure preamble bullets DON'T count.

**5. Component-reuse contract (post 2026-05-11):** Tasks saying "mount existing X in new site Y" require the 4 sub-steps (prop signature paste, JSX with explicit prop values, tsc check, URL-shape test).

**6. `data-testid` grep (post 2026-05-11):** If spec has "Visual Acceptance" section listing `[data-testid=...]` selectors, grep plan body for each. Each selector must appear in at least one task's code block.

## SDD final-review interaction-smoke gate (subagent-driven-development skill, post 2026-05-11)

Final whole-branch reviewer prompt MUST include verbatim:

```
Run Playwright headless against <live URL or local dev server>. Assertions, in order:

1. All N data-testid selectors from the spec's Visual Acceptance section are visible on first paint.
2. ZERO 4xx/5xx network responses fired during first paint. Capture with page.on('response', r => {...}); fail if any URL the page requested returned 4xx/5xx.
3. Click every spec-listed interactive element (rail items, drawer triggers, primary buttons, nav pills).
4. After each click: assert no on-screen text matches /404|HTTP \d{3}|not found|error/i. Assert no new 4xx/5xx network responses fired during/after the click.
5. Report selector-by-selector AND click-by-click PASS/FAIL.

If ANY of (1)-(4) fail, the slice is NOT shipped. Selectors painted but content broken = same severity as components not painted.
```

## Component-reuse contract check (SDD skill, post 2026-05-11)

When dispatching implementer for a task that says "mount existing component X in new site Y", the implementer prompt MUST include:

1. Implementer must grep/Read X's prop signature and paste it before writing wire-up
2. Wire-up JSX must name prop values explicitly (no `...spread`)
3. Run `tsc --noEmit` before commit
4. Write a unit test that renders Y mounting X with a mock for X's primary external dep; the mock asserts URL/payload shape Y passes IS what X expects

If task body doesn't include these sub-steps, controller adds them before dispatching.

---

## Lessons → rules table

| Lesson | Rule |
|---|---|
| `gws` hallucinated for 5 commits (2026-05-10) | Memory verification rule |
| Spec-already-answered question asked (2026-05-11 brainstorm) | Spec-first clarification rule |
| Slice 1 ghost components (2026-05-11) | Feature-shipped verification + build+mount pairing |
| Slice 1.5 PDF 404 in drawer (2026-05-11) | Interaction-smoke gate + Component-reuse contract + Underscore-dead-prop |
