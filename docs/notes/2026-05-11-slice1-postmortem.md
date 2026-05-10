# Slice 1 Post-Mortem · Ghost Components in Production (2026-05-11)

## TL;DR

44-task Slice 1 plan shipped green via SDD. All component tests pass. Bundle deployed. **Live workspace at `https://corgflix.duckdns.org/tutor/?taskId=…` still renders the OLD layout** because no task in the plan modified `tutor-web/src/components/TutorWorkspace.tsx`'s return block to import + mount the new components.

DrillStack / ProblemStepper / DrillCard / ProgressStrip / CompileSubmitCard exist in the bundle, pass their unit tests, and are NEVER paint-mounted in production. Ghost feature ship — identical anti-pattern to the FSRS-cards-in-DB-no-UI gap that the 19-agent audit flagged two sessions ago.

## What actually shipped vs intended

| Surface | Spec §B intended | Live now (2026-05-11) |
|---|---|---|
| Workspace render path | ProblemStepper + ProgressStrip + DrillStack + Sidekick + CompileSubmitCard | PdfPane + Scratchpad + ChatPane + Sidekick + InlineAskChip |
| `/tutor/review` route | FsrsReview flip card + grade row + forecast | ✅ Live + working |
| Header `DaemonHealthPill` | Status pill | ✅ Mounted, shows red (autostart not installed yet) |
| Inline help (text-select ✨ ASK + per-paragraph `?` gutter) | Wired into card paragraphs | ⚠️ Wired into ChatPane only; not into the never-rendered drill cards |

## Root cause chain

1. **Plan-author omission (the controller, prior session).** Spec §B described the layout swap in an ASCII mockup. When extracting spec → tasks, the controller gave each new component its own task (D1 stepper, D2 drill card, D3 stack, etc.). The final "Task DN: open `TutorWorkspace.tsx`, replace the return block, import the new components, delete `<PdfPane>` / `<Scratchpad>` / `<ChatPane>` from the JSX" step was implied by a file-structure preamble bullet — never enumerated as a checkbox task in the plan body. SDD followed plan tasks verbatim. Plan said build X. Plan did not say mount X. SDD did not mount X.

2. **Self-review checklist box-ticked.** Plan self-review asked "every spec section maps to at least one task". Spec §B mapped to D1-D6 (build the components). Pass. The implicit "and put them on the screen" sub-requirement was not a separate spec bullet, so self-review didn't catch it.

3. **TDD asserted the wrong layer.** Component tests use React Testing Library to render `<DrillStack />` in isolation. Tests pass on the component contract. Production paint goes through `<TutorWorkspace>` which imports none of the new components. There is no possible component-level test failure that catches "the workspace doesn't render this".

4. **SDD per-task review scope.** Spec compliance reviewer for each Phase D task checked "component file exists with expected shape + tests green". No reviewer task said "open the live URL, screenshot, assert `[data-testid="drill-stack"]` visible". The whole-branch final review ran the full test suite + checked commits — bundle built, tests green, ship.

5. **Bundle rebuild is a false signal.** `npm run build` includes every imported file. The new components ARE imported somewhere (the route map for `/tutor/review`, the test files), so they end up in `index-Bk26zsCv.js`. Bundle hash matching deployment ≠ feature rendering on the workspace page.

6. **No visual gate at the end.** Phase J3 was "deploy + verify bundle hash + tag". Verified bundle hash. Did not verify visual state of a real task URL.

## Same anti-pattern, second time

The 19-agent audit on 2026-05-10 identified ghost features as a top concern:

> "FSRS cards exist in DB, NO UI. Material_paths only at INSERT, never backfilled. Subject % subject-key mismatch (`SO` tasks vs `SO&RC` knowledge.jsonl) means rollup never matches."

Slice 1 spec §F shipped the FSRS review surface, closing that specific ghost. **Then the spec → plan → SDD pipeline shipped 5 fresh ghost components** doing the exact same thing the audit was set up to prevent.

## Fix going forward — additions to the spec → plan → SDD workflow

These items belong in the user-global `C:\Users\User\.claude\CLAUDE.md` and/or the `superpowers:writing-plans` skill, not just in this slice's backlog:

### Plan structure (writing-plans skill)

- Every "create new component" task MUST have a paired or following "wire it" task that names the exact file:line of the mount site and shows the JSX diff that mounts it. Two checkboxes minimum per component: build + mount.
- File-structure preamble's "Modified" bullets are not load-bearing for SDD — they're documentation. Every modification must have its own task.

### Self-review (writing-plans skill)

- Add a check: "Every component file listed under 'Frontend (new)' must appear in a separate task as an import or JSX element in some file under 'Frontend (modified)'." Grep the plan for the component name across both sections. If only "new" mentions it, integration task missing.

### Final review (subagent-driven-development skill)

- Final whole-branch reviewer must run Playwright headless against the live deployed URL (or local dev server if VPS deploy is the next step) and assert key `data-testid` selectors of the slice are visible in the paint. Visual-presence gate, not just test-suite gate.
- Reviewer prompt explicitly says: "Component shipped + tests green is NOT enough. Open the URL. Confirm the user sees it."

### Spec writing (brainstorming skill)

- Spec section that describes a UI layout in ASCII MUST include a separate "what the user SHOULD see when they open URL X" subsection with explicit `data-testid` selectors that must be visible. This becomes the visual-presence acceptance criterion the SDD final review enforces.

### Trust-but-verify rule extension (CLAUDE.md)

Existing rule covers "memory claims that name filepath / binary / package / route / SHA / bundle hash / test count / live URL". Add to the same rule:

> Before claiming a feature is shipped: open its user-facing surface (URL or CLI command) and confirm the user sees the feature. Bundle hash + tests green ≠ feature shipped. The 2026-05-11 Slice 1 lesson — 5 components in bundle, ghost in paint.

## Open question (brainstorm interrupt)

Prior session opened `/superpowers:brainstorming` for the swap fix and got mid-flight on swap-scope:

1. **Full replace** — drill stack IS the workspace; `PdfPane` + `Scratchpad` + `ChatPane` gone. PDF lives only behind a resource-rail drawer. Spec-faithful per §B.
2. **Coexist by task type** — `task.problemRefs.length > 0` → drill stack; legacy single-problem tasks keep old layout. Migration grace.
3. **Side-by-side** — `PdfPane` as left rail + `DrillStack` center + `Sidekick` bottom-right + `ChatPane` removed. Hybrid.
4. **Toggle mode** — user-picked per task via header toggle. Most flexible, most chrome.

User interrupted to ask for status. **Next session: answer this question + resume brainstorm → spec → plan → SDD pipeline with the workflow fixes above baked in.**

## Other Slice 1 deferred user actions (still pending, unrelated to the wiring gap)

- `tools\install-daemon-autostart.ps1` admin install → daemon + reverse SSH tunnel autostart. Until then, `DaemonHealthPill` red.
- `jarvis google-auth-bootstrap` on PC + `scp google-token.json` to VPS at `/opt/jarvis/data/google-token.json`. Until then, `/api/v1/google/status` reports `tokenPresent: false`; calendar/drive/gmail dispatch returns structured "disabled".
- Manual dogfood pass per `docs/superpowers/findings/2026-05-10-slice1-dogfood.md` — blocked by the wiring gap (first 4 scenarios can't be exercised through UI until the swap commit lands).

## Snapshot of current state at post-mortem write time

- HEAD: `c8b2831` (matches origin/main)
- Tag: `slice1-tutor-drill-workspace` @ `b058fd8`
- Live bundle: `index-Bk26zsCv.js`
- `/healthz` 200
- Tests: 661 backend + 268 frontend + 16 daemon + 7 node = 952 green
- Memory dir: `BRIDGE.md` canonical + `project_jarvis_overhaul_active.md` (2 verify pinned) + 3 feedback files + `user_identity.md`. Archive contains `project_jarvis_2026-05-09_session_wrap.md`. Verify report green on all 5 live files.

## 2026-05-11 update — workflow fixes shipped (3 layers)

### Layer 1: CLAUDE.md trust-but-verify extension

Appended to `C:\Users\User\.claude\CLAUDE.md` — new "Spec-first clarification rule (load-bearing)". Two clauses:
1. Brainstorm/spec/plan mode → grep existing artifacts before any clarifying question. Log "Spec-check: <q> → <result>" inline.
2. Feature-shipped claims require live URL visual confirmation, not just bundle hash + green tests.

### Layer 2: brainstorming SKILL.md grep gate

Edited `C:\Users\User\.claude\plugins\cache\claude-plugins-official\superpowers\5.1.0\skills\brainstorming\SKILL.md` — added checklist item #3 "Spec/plan grep gate" before "Ask clarifying questions" (now item #4). Full gate section appended with the 4-step procedure: list candidates → grep per question → log result → only ask user on confirmed gaps.

### Layer 3: brainstorming SKILL.md self-review extension

Added two new self-review items to the existing 4-item list:
- Item 5: clarification-questions audit (post-session retrospective; flag avoidable questions in BRIDGE.md)
- Item 6: visual-presence acceptance criterion ("what user SHOULD see at URL X" subsection with data-testid selectors enforced at SDD final review)

### Files touched (all user-global, NOT in repo)

- `C:\Users\User\.claude\CLAUDE.md` (+15 lines)
- `C:\Users\User\.claude\plugins\cache\claude-plugins-official\superpowers\5.1.0\skills\brainstorming\SKILL.md` (+30 lines across 2 edits)

Repo-tracked note only — the actual rule files live outside the jarvis-kotlin repo.
