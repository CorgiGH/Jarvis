# Plan 4a: Gate-Chain Tooling (Ungated Half) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. **LANE B — executes in the dedicated worktree `../jarvis-kotlin-lane-b` (branch `lane-b/plan4a`), NEVER in the main working tree** (Plan 3 is executing there). PM merges to main after the full-suite gate.

**Goal:** Spec §9.2 gates 5–6 + §9.4 adopt-list, the dependency-free half: self-hosted fonts, DESIGN.md auto-generation + drift gate, Impeccable calibrate→subset→pin→fail-open, env-locked visual baselines (fixed shell + viz-demo ONLY) + INV-9.5 scope gate, and the grader-eval harness skeleton (deterministic leg).

**Architecture:** Everything lands as NEW files or edits to files Plan 3 never touches (recon-verified zero intersection). The ONE shared file (`.github/workflows/test.yml`, Impeccable fail-open step) is delivered as a patch file the PM applies at merge — Lane B never edits it directly.

**Tech Stack:** woff2 self-hosting (OFL-licensed), node script for DESIGN.md gen, `impeccable@2.3.2` (npm-verified 2026-06-12), @playwright/test separate visual config, JUnit 5 parameterized golden tests.

---

## Section 0 — Verified ground truth (recon 2026-06-12, wf_fef32715)

1. **Fonts:** zero `<link>`/`@font-face` in the app; JetBrains Mono appears in font stacks (`DoorBrutalist.tsx:28`, `viz/theme.ts:13`, `ThemePicker.tsx:69`) but is NEVER fetched — device-installed or fallback. Fraunces/Nunito are runtime-CDN-injected ONLY by `DoorWarm.tsx:150-159` (demo component). `tutor-web/public/` has 5 git-tracked files, no `fonts/`; Vite `base:'/tutor/'` ⇒ fonts serve at `/tutor/fonts/…`. **NEVER `git add -A`** — public/ holds many untracked demo artifacts.
2. **Impeccable VERIFIED:** npm package `impeccable`, latest 2.3.2 (= council cite), CLI binary `impeccable`, repo github.com/pbakaus/impeccable, Apache-2.0. Locked amendment sequence (council-1781132987 (a), active-constraints:26): **calibrate → applicable-subset → version-pin `impeccable@2.3.2` → fail-open until BOTH → only then blocking.** First action MUST be the calibrate run (also verifies the CLI surface at runtime). DESIGN.md auto-generated from index.css, never hand-maintained.
3. **DESIGN.md exists** (repo root, git-tracked, YAML+MD) but the index.css mirror is MANUAL — no script. `:root` tokens at `index.css:23-78`, `@theme` at `:80-108`. Known gaps: `--type-display*`/`--tracking-*` live only in DESIGN.md; motion tokens not in `@theme`. Known drift: `viz/theme.ts` hardcodes INK `#0a0a0a`/PAPER `#f5f5f0` vs DESIGN.md `#000000`/`#ffffff` — **do NOT "fix" theme.ts here** (changes viz rendering = needs Plan-4b rendered gates); record as carried follow-up.
4. **Baselines:** new `tutor-web/playwright.visual.config.ts` + `tutor-web/e2e/visual/` dir (separate from `e2e/` — structural scope enforcement: lesson specs can never grow baselines). Env-lock in config: `workers:1`, `deviceScaleFactor:1`, headless, pinned snapshot path. The 2 permitted pages: `/tutor/` (AppShell fixed shell) + `/tutor/viz-demo` (VizDemoPage). INV-9.5 = a vitest/script asserting the snapshot dir contains baselines from ONLY those two spec files.
5. **Graders today:** drill stack = `DrillGrader` (LLM) + `GradeScoring` (deterministic override); SymPy leg is admission-side. Only golden-like test: `P3HonestyGraderSpotCheckTest`. Harness skeleton scope = the DETERMINISTIC leg (`GradeScoring`) with golden sets at `src/test/resources/fixtures/grader-golden/{subject}/{grader-type}/{id}.json`, JUnit 5 parameterized, runs in `:check`. LLM-judge + execution-grader golden sets = Plan 6 (stubs/dir convention only here).
6. **Two-lane contract (Alex-ratified, index header):** Lane B file set must have zero intersection with Plan 3 Tasks 8–11 files (recon-confirmed for all deliverables except test.yml → patch-file mechanism). Worktree setup: `git worktree add ../jarvis-kotlin-lane-b -b lane-b/plan4a` from current main HEAD. All commits on `lane-b/plan4a`. No live-DB access of any kind in this plan. The frontend `npm` work runs inside the worktree's own `tutor-web` (own node_modules via `npm ci` — never share the main tree's).
7. **Licensing/no-paid:** JetBrains Mono (OFL), Fraunces (OFL), Nunito (OFL) — woff2 files fetched once from official sources (google-webfonts-helper or upstream repos), committed. No paid services.

## Section 0.9 — Canonical contracts

- **A. Fonts:** `tutor-web/public/fonts/{JetBrainsMono,Fraunces,Nunito}-{weights}.woff2`; `@font-face` blocks appended to `index.css` (font-display: swap, `url('/tutor/fonts/…')`); JetBrains Mono weights 400/700; Fraunces 400/600/900 + 600italic (variable file acceptable); Nunito 400/600/700/800/900 (variable file acceptable). `DoorWarm.tsx` CDN injection REPLACED by the local faces (delete the runtime `<link>` block). Headless renders stop silently falling back to system fonts (the council's stated reason).
- **B. DESIGN.md gen:** `tools/generate-design-md.mjs` — parses `index.css` `:root` + `@theme`, regenerates the token section of DESIGN.md between `<!-- AUTOGEN:tokens BEGIN/END -->` markers (add markers; hand-written prose outside markers survives). CI-shaped drift test: a vitest (`tutor-web/src/__tests__/designMdSync.test.ts` or node script in `:check`-adjacent npm test) that regenerates in-memory and diffs against the committed DESIGN.md section — drift = red.
- **C. Impeccable (PM-amended 2026-06-12 after live CLI verification):** the real `impeccable@2.3.2 detect` CLI exposes ONLY `--json`/`--gpt`/`--gemini` — there is NO `--config` flag and NO tool-native rule-subset mechanism (reviewer ran `--help` against the actual binary). The locked "applicable-subset" leg is therefore implemented as a **post-process filter**: calibrate output committed at `build-review/impeccable-calibration-2026-06-12.json`; the subset = a committed rule list at `tools/impeccable-rules.json` (`{"enabled":["<antipattern-id>", …]}` — the finding key in detect's JSON is `antipattern`) applied by `tools/impeccable-filter.mjs` (reads detect JSON on stdin, drops findings whose antipattern id is not enabled, exits per filtered count); enabled = only antipatterns with ≥1 true-positive hit in calibration, each disabled id listed with a reason. CI patch file `build-review/tmp/lane-b-patches/test-yml-impeccable.patch` adds the fail-open pipeline (`npx impeccable@2.3.2 detect --json tutor-web/src/ | node tools/impeccable-filter.mjs || true`) — **PM applies at merge**. This satisfies the locked calibrate→subset→pin→fail-open sequence; the subset mechanism (script vs config flag) was never specified by the council and required no re-litigation.
- **D. Baselines:** `playwright.visual.config.ts` (workers:1, deviceScaleFactor:1, snapshotPathTemplate under `e2e/visual/__screenshots__/`), specs `e2e/visual/shell.visual.spec.ts` + `e2e/visual/viz-demo.visual.spec.ts` (toHaveScreenshot, fullPage, maxDiffPixelRatio 0); baselines GENERATED and committed in this plan (the human-recommit owner = the PM, per council (b)); INV-9.5 scope test `e2e/visual/baselineScope.test.ts`-style script asserting every file under `__screenshots__` traces to exactly those 2 specs; npm script `e2e:visual`.
- **E. Grader harness:** `GraderGoldenHarness` JUnit 5 parameterized test over `fixtures/grader-golden/**/*.json` (shape: `{grader:"grade-scoring", subject, input:{rubric…}, expected:{score, correct}}`); ≥12 golden items for GradeScoring spanning PA/ALO/PS shapes incl. edge cases (empty rubric, all-false, all-or-nothing); runs inside `:check`; a seeded-violation test proves the harness reds on a changed expectation (fix-claim discipline).


> **PM RULING (2026-06-12, Task-4 second stop):** `themeRefHarness.tsx` MUST be **SELF-CONTAINED** — a token-true LIGHT/DARK brutalist specimen built from `index.css` tokens only, with **ZERO imports from `src/door/DoorBrutalist|concept|figures`** (those are deliberately-untracked working-tree demos per the standing door rule; importing them makes the committed tree unresolvable on clean checkout — the verifier PROVED 2/3 baselines die when they're absent). The earlier canonical block prescribing DoorBrutalist imports is RETRACTED. The two theme PNGs must be regenerated from the self-contained harness, and the committed-state proof (move untracked door files aside → `npm run e2e:visual` 3/3 → restore) is a REQUIRED step. Also: the snapshotPathTemplate carries no `{platform}` token — baseline filenames have NO -win32 suffix; the manifest note should say so.

## Tasks

| # | Task |
|---|---|
| 0 | Worktree setup + delta-recon probe (lane-b/plan4a from current main HEAD; verify zero-intersection list against Plan 3's ACTUAL landed diffs) |
| 1 | Self-hosted fonts (§0.9A) + DoorWarm CDN removal + font-loading vitest |
| 2 | DESIGN.md autogen + markers + drift test (§0.9B) |
| 3 | Impeccable calibrate (REAL run) → subset config → pinned fail-open CI patch file (§0.9C) |
| 4 | Visual baseline machinery + 2 baselines + INV-9.5 scope gate (§0.9D) |
| 5 | Grader-eval harness skeleton + 12 golden items + seeded-red proof (§0.9E) |
| 6 | Lane full-suite gate in the worktree + merge-prep manifest (file list, CI patch, follow-ups) for the PM merge |

## Task 0 — Worktree setup + delta-recon probe (lane-b/plan4a from current main HEAD; verify zero-intersection against Plan 3's ACTUAL landed diffs)

Creates the dedicated Lane B worktree and proves — against Plan 3's **real landed commits**, not the recon's static claim — that Plan 4a's deliverable file set does not collide with Plan 3 work already on `main`. This is the runtime check the recon could not do (the recon predates the Plan-3 commits that landed since). The task ends with `npm ci` in the worktree's own `tutor-web` so every later frontend command runs against an isolated `node_modules`, never the main tree's. **This task makes no source edits and no Lane B commit** — it sets up the lane and gates on the intersection result.

> **Operator note (Lane B is structural, not optional):** Plan 3 is executing in the MAIN working tree `C:\Users\User\jarvis-kotlin` concurrently. NEVER run any Plan 4a command in the main tree — every command in Plans 4a Tasks 0–6 either `cd`s into `..\jarvis-kotlin-lane-b` or passes `-C ..\jarvis-kotlin-lane-b` to git. The worktree shares the same `.git` object store but has its own checked-out files, its own branch (`lane-b/plan4a`), and its own `tutor-web/node_modules`. The PM merges `lane-b/plan4a` to `main` after the full-suite gate (Task 6), applying the test.yml patch file by hand at that point.

**Files:**
- Create (worktree, not committed by this task): the worktree dir `..\jarvis-kotlin-lane-b` on branch `lane-b/plan4a`
- Read-only: Plan 3's landed file list (`git log --name-only efb50f5..HEAD`)
- No edits to any tracked file in this task

- [ ] **Step 1: Confirm the main tree is on `main` at the recon's branch-point HEAD.** From the repo root `C:\Users\User\jarvis-kotlin`:

```powershell
git -C C:\Users\User\jarvis-kotlin rev-parse --abbrev-ref HEAD
git -C C:\Users\User\jarvis-kotlin rev-parse HEAD
git -C C:\Users\User\jarvis-kotlin cat-file -t efb50f5
```

Expected: branch `main`; a 40-char HEAD SHA; `efb50f5` resolves to type `commit` (it is the Plan-3 branch-point used as the recon delta base). **STOP if** the branch is not `main` or `efb50f5` does not resolve — the worktree must be created from current `main` HEAD, and the delta-recon base must exist, before anything else.

- [ ] **Step 2: Create the Lane B worktree from current main HEAD.** Native git worktree — no copy, shares the object store:

```powershell
git -C C:\Users\User\jarvis-kotlin worktree add ..\jarvis-kotlin-lane-b -b lane-b/plan4a
```

Expected output ends with `Preparing worktree (new branch 'lane-b/plan4a')` and `HEAD is now at <sha> docs(plan): two-lane mode ratified …` (or whatever the current main HEAD subject is). **STOP if** git reports `fatal: '..\jarvis-kotlin-lane-b' already exists` (a prior run left it — `git -C C:\Users\User\jarvis-kotlin worktree list` to inspect; only reuse it if it is already on `lane-b/plan4a` and clean) or `fatal: a branch named 'lane-b/plan4a' already exists` (delete the stale branch only after confirming it has no unmerged commits).

- [ ] **Step 3: Verify the worktree is registered and on the right branch.**

```powershell
git -C C:\Users\User\jarvis-kotlin worktree list
git -C ..\jarvis-kotlin-lane-b rev-parse --abbrev-ref HEAD
```

Expected: `worktree list` shows two lines — the main tree on `[main]` and `…/jarvis-kotlin-lane-b` on `[lane-b/plan4a]`; the second command prints `lane-b/plan4a`. **STOP if** the worktree is not listed or is on the wrong branch.

> **Recon-prose staleness (finding #3):** the recon's stated `main` HEAD (the `9825005`/`abdd2d1` SHAs referenced in older memory) is STALE — live HEAD is later, and some Plan-3 work is ALREADY landed at the branch-point (`git log efb50f5..HEAD` shows `VizDemoPage.tsx`, `main.tsx`, `AlgoStepperShell.tsx`, the `lesson/` components, `GraphTreeFamily`, `familyRegistry`, etc.). Do NOT trust any hardcoded HEAD SHA in this plan — Task 0 parameterizes the branch-cut off whatever `main` HEAD is live (Step 2). Plan 3 may also land MORE commits to shared-ish files (`main.tsx`, `package.json`/`-lock`) AFTER this worktree is cut; the branch-cut intersection probe below cannot see those. That post-cut risk is re-checked at MERGE time in Task 6 Step 5 (the second zero-intersection re-assertion against the THEN-current `main`).

- [ ] **Step 4: Capture Plan 3's ACTUAL landed file set.** Plan 3 commits are `efb50f5..HEAD` on `main`. List the files they touched (this is the live delta the static recon could not see):

```powershell
git -C C:\Users\User\jarvis-kotlin log --name-only --pretty=format: efb50f5..HEAD | Sort-Object -Unique | Where-Object { $_ -ne '' }
```

Expected: a sorted file list. As of the recon snapshot it is exactly these 38 paths (it may have GROWN if Plan 3 landed more since — that is fine; the intersection check below is what matters):

```
content/PA/kcs/pa-kc-001.yaml
content/PA/kcs/pa-kc-002.yaml
content/PA/kcs/pa-kc-003.yaml
content/PA/kcs/pa-kc-004.yaml
content/PA/viz/viz-pa-mergesort-001.yaml
docs/superpowers/plans/2026-06-02-interface-signatures-lock.md
docs/superpowers/plans/2026-06-11-one-pass-plan-index.md
src/main/kotlin/jarvis/content/ContentValidator.kt
src/main/kotlin/jarvis/tutor/lesson/BeatSelector.kt
src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt
src/test/kotlin/jarvis/content/AuthoredBeatsCompletenessTest.kt
src/test/kotlin/jarvis/content/FigureBindingValidationTest.kt
src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt
src/test/kotlin/jarvis/tutor/lesson/BeatSelectorTest.kt
src/test/kotlin/jarvis/web/LessonBeatGradeRouteTest.kt
src/test/kotlin/jarvis/web/LessonServeRouteTest.kt
tutor-web/package-lock.json
tutor-web/package.json
tutor-web/src/components/lesson/AttemptBeat.tsx
tutor-web/src/components/lesson/BeatOrchestrator.test.tsx
tutor-web/src/components/lesson/BeatOrchestrator.tsx
tutor-web/src/components/lesson/CheckBeat.tsx
tutor-web/src/components/lesson/NameBeat.tsx
tutor-web/src/components/lesson/PredictBeat.tsx
tutor-web/src/components/lesson/RevealBeat.tsx
tutor-web/src/components/viz/AlgoStepperShell.tsx
tutor-web/src/components/viz/VizDemoPage.tsx
tutor-web/src/components/viz/families/GraphTreeFamily.tsx
tutor-web/src/components/viz/families/__tests__/GraphTreeFamily.test.tsx
tutor-web/src/components/viz/families/__tests__/mergesortTrace.ts
tutor-web/src/components/viz/families/familyRegistry.ts
tutor-web/src/lib/beatGrade.ts
tutor-web/src/lib/dwell.test.ts
tutor-web/src/lib/dwell.ts
tutor-web/src/lib/lesson.ts
tutor-web/src/lib/lessonStrings.ts
tutor-web/src/main.tsx
tutor-web/src/vite-raw.d.ts
tutor-web/vite.config.ts
```

- [ ] **Step 5: Run the intersection probe — Plan 4a's full deliverable list × Plan 3's landed list.** Plan 4a's complete deliverable file set (Tasks 1–6, from §0.9) is the right-hand operand. Run this script from the repo root:

```powershell
$plan3 = git -C C:\Users\User\jarvis-kotlin log --name-only --pretty=format: efb50f5..HEAD |
  Sort-Object -Unique | Where-Object { $_ -ne '' }

# Plan 4a's full deliverable list (NEW or MODIFIED files across all 6 tasks, §0.9 A–E + Task 0/6).
$plan4a = @(
  'tutor-web/src/index.css',                                   # Task 1 (modify)
  'tutor-web/src/door/DoorWarm.tsx',                           # Task 1 (modify)
  'tutor-web/src/__tests__/fontLoading.test.ts',              # Task 1 (new)
  'tutor-web/public/fonts/',                                   # Task 1 (new dir, woff2 files)
  'tools/generate-design-md.mjs',                              # Task 2 (new)
  'tools/generate-design-md.test.mjs',                         # Task 2 (new)
  'DESIGN.md',                                                 # Task 2 (modify: markers + autogen block)
  'tutor-web/src/__tests__/designMdSync.test.ts',             # Task 2 (new)
  'build-review/impeccable-calibration-2026-06-12.json',       # Task 3 (new)
  'tools/impeccable-rules.json',                               # Task 3 (new — the post-process subset allowlist)
  'tools/impeccable-filter.mjs',                               # Task 3 (new — the stdin filter script)
  'build-review/tmp/lane-b-patches/test-yml-impeccable.patch', # Task 3 (new; PM applies)
  'tutor-web/theme-ref.html',                                  # Task 4 (new — theme-ref harness HTML entry)
  'tutor-web/src/themeRefHarness.tsx',                         # Task 4 (new — theme-ref harness entry)
  'tutor-web/playwright.visual.config.ts',                     # Task 4 (new)
  'tutor-web/e2e/visual/shell.visual.spec.ts',                # Task 4 (new)
  'tutor-web/e2e/visual/theme-dark.visual.spec.ts',           # Task 4 (new — DARK theme reference page)
  'tutor-web/e2e/visual/theme-light.visual.spec.ts',          # Task 4 (new — LIGHT theme reference page)
  'tutor-web/src/__tests__/baselineScope.test.ts',            # Task 4 (new — INV-9.5 vitest; under src/ so npm test collects it)
  'build.gradle.kts',                                          # Task 5 (modify — PM-ratified DIRECT edit; Plan 3 8-11 don't touch it)
  'src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt'    # Task 5 (new)
)

# package.json / package-lock.json are SHARED BASELINE, not a conflict (PM-ratified
# exclusion from the zero-intersection STOP): Plan 3 already landed its edits there via
# its landed Tasks 3–7 — Lane B branches FROM that state and adds only ADDITIVE script
# keys. They are intentionally EXCLUDED from $plan4a so they are not flagged; Lane B's
# additive `scripts` keys are PM-merge-resolved (the PM re-runs npm ci and rebases the
# lockfile at merge). Task 2 adds a `design:check` npm script; Task 4 adds `e2e:visual`
# + `e2e:visual:update`; all are additive package.json edits, no key collision with Plan 3.
$collision = $plan3 | Where-Object { $plan4a -contains $_ }
if ($collision) {
  Write-Host "COLLISION — Plan 4a deliverables already touched by Plan 3:"
  $collision | ForEach-Object { Write-Host "  $_" }
} else {
  Write-Host "OK — zero intersection (besides the shared package.json/-lock baseline)."
}
```

Expected output (exactly): `OK — zero intersection (besides the shared package.json/-lock baseline).` **STOP if** any collision is printed: a Plan 4a deliverable was modified by a Plan 3 commit since the recon, which breaks the two-lane zero-intersection contract (Section 0 #6). Do NOT edit a colliding file in Lane B — report the colliding path(s) to the PM verbatim; the lane assignment must be re-cut before any further Plan 4a work. (A growth of `$plan3` that does NOT collide is benign — proceed.)

- [ ] **Step 6: Install the worktree's own frontend deps.** Every Plan 4a frontend command runs in the worktree's `tutor-web` against its OWN `node_modules` — never the main tree's. From the repo root:

```powershell
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web ci
```

Expected: npm installs from the inherited `package-lock.json` and prints `added <N> packages` with no error. **STOP if** `npm ci` fails (e.g. lockfile out of sync) — the lane cannot build the frontend gates without a clean install; report the error verbatim. (Note: the worktree's `tutor-web/node_modules/` is git-ignored, exactly like the main tree's.)

- [ ] **Step 7: Confirm the worktree builds the baseline frontend before any Plan 4a edit.** Sanity gate so later red is attributable to Plan 4a, not an inherited break:

```powershell
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test
```

Expected: `vitest run` completes; all inherited suites pass. (The pre-existing `tutor-shell-api-contract.spec.ts` Playwright e2e red noted in Plan 2 §0.6 is an `npm run e2e` concern, NOT `npm test` — `npm test` is vitest-only and must be green.) **STOP and report if** vitest is red on the inherited baseline — do not build Plan 4a on a red lane.

> No commit in Task 0 — the worktree + branch are the deliverable, and they are created by `git worktree add`, not by a commit. Tasks 1–6 each commit to `lane-b/plan4a` in the worktree.

---

## Task 1 — Self-hosted fonts (§0.9A) + DoorWarm CDN removal + font-loading vitest

Replaces the runtime Google-Fonts CDN injection in `DoorWarm.tsx` with self-hosted woff2 faces served from `tutor-web/public/fonts/` (Vite `base:'/tutor/'` ⇒ served at `/tutor/fonts/…`). Adds `@font-face` blocks to `index.css` (`font-display: swap`), commits the woff2 files, and deletes the `DoorWarm` `<link>`-to-`fonts.googleapis.com` effect. A vitest asserts (a) `index.css` declares the expected `@font-face` families and (b) `DoorWarm.tsx` no longer references `fonts.googleapis`. The council's stated reason (§0 #1): headless Playwright renders silently fall back to system fonts when fonts come from a CDN, breaking the visual baselines Task 4 commits. **Self-hosted faces are deterministic offline; no GDPR transfer to Google; exam-day-safe.** All three families are OFL-licensed (§0 #7) — woff2 fetched once from official sources and committed. (No learner-facing strings here — chrome/tooling only.)

**Files (all in the worktree `..\jarvis-kotlin-lane-b`):**
- Create: `tutor-web/public/fonts/JetBrainsMono-400.woff2`, `JetBrainsMono-700.woff2`
- Create: `tutor-web/public/fonts/Fraunces-400.woff2`, `Fraunces-600.woff2`, `Fraunces-900.woff2`, `Fraunces-600italic.woff2`
- Create: `tutor-web/public/fonts/Nunito-400.woff2`, `Nunito-600.woff2`, `Nunito-700.woff2`, `Nunito-800.woff2`, `Nunito-900.woff2`
- Modify: `tutor-web/src/index.css` (append `@font-face` block, after the existing `@theme`)
- Modify: `tutor-web/src/door/DoorWarm.tsx` (delete the `useEffect` CDN-link injection; refresh the stale-then-accurate header comment, finding #8)
- Create: `tutor-web/src/__tests__/fontLoading.test.ts`

> **Source policy (finding #6, PM-ratified):** `gwfh` (`https://gwfh.mranftl.com`) is the PRIMARY source for ALL THREE families — its download API shape is uniform across families (`?download=zip&subsets=latin&variants=<list>&formats=woff2`), so one extraction loop covers everything and there are no per-family path guesses. We pull **static instances** (one woff2 per weight) — uniform, deterministic, no variable-axis ambiguity; the variable files are optional and NOT used here. Upstream GitHub releases are the FALLBACK per family (only if gwfh is unreachable). The Step-2 wOF2-magic STOP gate still guards every downloaded file (an HTML error page never reaches a commit).

- [ ] **Step 1: Download the woff2 static instances from gwfh (PRIMARY for all three) into the worktree's `public/fonts/`.** Create the dir, then pull ONE zip per family from the uniform gwfh download API (finding #6 — gwfh is primary for ALL THREE because its API shape is identical across families; static instances, one woff2 per weight). Each zip contains the per-weight static woff2 named `<family>-<subset>-<variant>.woff2` (e.g. `fraunces-latin-600italic.woff2`); the extract loop renames them to this task's flat `<Family>-<variant>.woff2` scheme. From the repo root:

```powershell
New-Item -ItemType Directory -Force ..\jarvis-kotlin-lane-b\tutor-web\public\fonts | Out-Null
$FONTS = "..\jarvis-kotlin-lane-b\tutor-web\public\fonts"

# Uniform gwfh download (PRIMARY for all three families — same API shape: ?download=zip&subsets=latin&variants=<list>&formats=woff2).
# id        = gwfh font id ; out = output basename ; variants = the weights/styles this task self-hosts.
$families = @(
  @{ id = 'jetbrains-mono'; out = 'JetBrainsMono'; variants = '400,700' }
  @{ id = 'fraunces';       out = 'Fraunces';      variants = '400,600,900,600italic' }
  @{ id = 'nunito';         out = 'Nunito';        variants = '400,600,700,800,900' }
)
foreach ($fam in $families) {
  $zip = Join-Path $FONTS "_$($fam.out).zip"
  $dir = Join-Path $FONTS "_$($fam.out)"
  curl.exe -fL "https://gwfh.mranftl.com/api/fonts/$($fam.id)?download=zip&subsets=latin&variants=$($fam.variants)&formats=woff2" -o $zip
  Expand-Archive $zip -DestinationPath $dir -Force
  # gwfh names files "<id>-<subset>-<variant>.woff2"; rename each to "<out>-<variant>.woff2".
  foreach ($v in ($fam.variants -split ',')) {
    $src = Get-ChildItem $dir -Recurse -Filter "*$v.woff2" |
           Where-Object { $_.BaseName -match "(^|-)$([regex]::Escape($v))$" } | Select-Object -First 1
    if (-not $src) { throw "gwfh zip for $($fam.out) is missing the $v weight — fall back to upstream GitHub for this family (see fallback note)." }
    Copy-Item $src.FullName (Join-Path $FONTS "$($fam.out)-$v.woff2") -Force
  }
  Remove-Item $zip -Force
  Remove-Item $dir -Recurse -Force
}
```

> **Per-family GitHub FALLBACK (only if gwfh is unreachable — finding #6):** each family ships official woff2/variable upstream; if gwfh 4xx/5xx or the zip lacks a weight, pull from upstream and (for a variable file) switch that family's `@font-face` in Step 3 to a `font-weight` RANGE + `format('woff2-variations')`:
> - **JetBrains Mono (OFL):** `https://github.com/JetBrains/JetBrainsMono/releases/latest` → `fonts/webfonts/JetBrainsMono-Regular.woff2` + `-Bold.woff2` (static).
> - **Fraunces (OFL):** `https://github.com/undercasetype/Fraunces` → `fonts/variable/Fraunces[opsz,wght].woff2` + `Fraunces-Italic[opsz,wght].woff2` (variable — use the variable `@font-face` form).
> - **Nunito (OFL):** `https://github.com/googlefonts/nunito` → `fonts/variable/Nunito[wght].woff2` (variable — note: upstream commits the TTF variable; if no woff2 is committed, prefer gwfh, or convert the TTF to woff2 before committing).

- [ ] **Step 2: Verify all 11 woff2 files exist and are real font binaries (non-empty, woff2 magic `wOF2`).** (5 produced JetBrains/Fraunces/Nunito static instances become 2 + 4 + 5 = 11 files.)

```powershell
Get-ChildItem ..\jarvis-kotlin-lane-b\tutor-web\public\fonts\*.woff2 | Select-Object Name, Length
$expected = @(
  'JetBrainsMono-400','JetBrainsMono-700',
  'Fraunces-400','Fraunces-600','Fraunces-900','Fraunces-600italic',
  'Nunito-400','Nunito-600','Nunito-700','Nunito-800','Nunito-900'
)
foreach ($f in $expected) {
  $p = "..\jarvis-kotlin-lane-b\tutor-web\public\fonts\$f.woff2"
  if (-not (Test-Path $p)) { Write-Host "$f : MISSING"; continue }
  $bytes = [System.IO.File]::ReadAllBytes($p)[0..3]
  $magic = -join ($bytes | ForEach-Object { [char]$_ })
  Write-Host "$f : magic='$magic', $((Get-Item $p).Length) bytes"
}
```

Expected: 11 files, each with `Length` > 8000 and `magic='wOF2'`. **STOP if** any file is missing, zero-length, an HTML error page (magic would be `<!do`/`<htm`), or the magic is not `wOF2` — the gwfh download for that family failed; switch to that family's documented GitHub fallback (Step 1 note) before continuing. Do not commit a non-font placeholder.

- [ ] **Step 3: Append the `@font-face` block to `index.css`.** Insert it immediately AFTER the closing `}` of the `@theme { … }` block (currently ending at `index.css:108`) and BEFORE the `/* ---------- Body baseline typography */` comment. These are gwfh STATIC instances (finding #6) — every face declares a SINGLE `font-weight` and `format('woff2')` (not `woff2-variations`). `font-display: swap` on every face. Paths are absolute under the Vite base (`/tutor/fonts/…`). (If a family fell back to its upstream VARIABLE file in Step 1, replace that family's static faces with one variable face: a `font-weight` RANGE + `format('woff2-variations')` — see the Step-1 fallback note.)

```css
/* ---------- Self-hosted fonts (Plan 4a §0.9A — replaces DoorWarm CDN injection) ----------
   woff2 committed under tutor-web/public/fonts/ (Vite base '/tutor/' ⇒ served at /tutor/fonts/).
   gwfh static instances (one woff2 per weight); font-display: swap so first paint never blocks.
   Headless Playwright renders are now font-deterministic (no CDN race, no silent system-font
   fallback) — the reason the visual baselines (Plan 4a Task 4) can be committed. All three
   families are OFL-licensed. */
@font-face {
  font-family: 'JetBrains Mono';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('/tutor/fonts/JetBrainsMono-400.woff2') format('woff2');
}
@font-face {
  font-family: 'JetBrains Mono';
  font-style: normal;
  font-weight: 700;
  font-display: swap;
  src: url('/tutor/fonts/JetBrainsMono-700.woff2') format('woff2');
}
@font-face {
  font-family: 'Fraunces';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('/tutor/fonts/Fraunces-400.woff2') format('woff2');
}
@font-face {
  font-family: 'Fraunces';
  font-style: normal;
  font-weight: 600;
  font-display: swap;
  src: url('/tutor/fonts/Fraunces-600.woff2') format('woff2');
}
@font-face {
  font-family: 'Fraunces';
  font-style: normal;
  font-weight: 900;
  font-display: swap;
  src: url('/tutor/fonts/Fraunces-900.woff2') format('woff2');
}
@font-face {
  font-family: 'Fraunces';
  font-style: italic;
  font-weight: 600;
  font-display: swap;
  src: url('/tutor/fonts/Fraunces-600italic.woff2') format('woff2');
}
@font-face {
  font-family: 'Nunito';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('/tutor/fonts/Nunito-400.woff2') format('woff2');
}
@font-face {
  font-family: 'Nunito';
  font-style: normal;
  font-weight: 600;
  font-display: swap;
  src: url('/tutor/fonts/Nunito-600.woff2') format('woff2');
}
@font-face {
  font-family: 'Nunito';
  font-style: normal;
  font-weight: 700;
  font-display: swap;
  src: url('/tutor/fonts/Nunito-700.woff2') format('woff2');
}
@font-face {
  font-family: 'Nunito';
  font-style: normal;
  font-weight: 800;
  font-display: swap;
  src: url('/tutor/fonts/Nunito-800.woff2') format('woff2');
}
@font-face {
  font-family: 'Nunito';
  font-style: normal;
  font-weight: 900;
  font-display: swap;
  src: url('/tutor/fonts/Nunito-900.woff2') format('woff2');
}
```

> Edit anchor: in `..\jarvis-kotlin-lane-b\tutor-web\src\index.css`, the `@theme { … }` block closes at the line `}` directly above `/* ---------- Body baseline typography (Phase 2.2) ---------- */`. Place the block between those two.

- [ ] **Step 4: Delete the CDN injection from `DoorWarm.tsx`.** Remove the entire `useEffect` that creates the `door-warm-fonts` `<link>` to `fonts.googleapis.com` (currently `DoorWarm.tsx:150-159`). The faces now load globally from `index.css`, so `DoorWarm` needs no per-mount injection. Also drop the now-unused `useEffect` import if it is no longer referenced anywhere else in the file.

Replace this block:

```tsx
  useEffect(() => {
    const id = "door-warm-fonts";
    if (document.getElementById(id)) return;
    const link = document.createElement("link");
    link.id = id;
    link.rel = "stylesheet";
    link.href =
      "https://fonts.googleapis.com/css2?family=Fraunces:ital,opsz,wght@0,9..144,400;0,9..144,600;0,9..144,900;1,9..144,600&family=Nunito:wght@400;600;700;800;900&display=swap";
    document.head.appendChild(link);
  }, []);

  const pips = Array.from({ length: concept.progress.total }, (_, i) => i);
```

with just:

```tsx
  // Fonts (Fraunces/Nunito) are self-hosted via index.css @font-face (Plan 4a §0.9A).
  // The former runtime CDN <link> injection is removed — see council reason in index.css.
  const pips = Array.from({ length: concept.progress.total }, (_, i) => i);
```

Then fix the import line `import { useEffect, type CSSProperties, type ReactNode } from "react";` to drop `useEffect`:

```tsx
import { type CSSProperties, type ReactNode } from "react";
```

> Confirm `useEffect` is not used elsewhere in the file before removing it from the import: `Select-String -Path ..\jarvis-kotlin-lane-b\tutor-web\src\door\DoorWarm.tsx -Pattern 'useEffect'` should return ONLY the import line after Step 4's body edit — if it returns another usage, leave the import and only delete the CDN `useEffect`.

Finally, refresh the now-stale-then-accurate header comment (finding #8 — DoorWarm.tsx:11-13 claimed "the fonts are loaded locally" while the code was actually fetching them from a CDN; after this task removes the CDN injection the claim becomes true, so make the wording reflect the real mechanism). Find:

```tsx
// NOTE: this skin INTENTIONALLY violates DESIGN.md (serif, multi-hue, gradient,
// radius, soft shadow). All styles are scoped under `.door-warm` and the fonts are
// loaded locally so nothing here leaks into the real brutalist brand tokens.
```

Replace with:

```tsx
// NOTE: this skin INTENTIONALLY violates DESIGN.md (serif, multi-hue, gradient,
// radius, soft shadow). All styles are scoped under `.door-warm`. Fraunces/Nunito
// are self-hosted globally via index.css @font-face (Plan 4a §0.9A) — no runtime CDN
// <link> — so nothing here leaks into the real brutalist brand tokens.
```

- [ ] **Step 5: Write the font-loading vitest** (grep-style assertions over the two source files — no rendering needed). Create `..\jarvis-kotlin-lane-b\tutor-web\src\__tests__\fontLoading.test.ts`:

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, it, expect } from "vitest";

/**
 * Plan 4a Task 1 — fonts are self-hosted, not CDN-injected (§0.9A).
 * (a) index.css declares the three @font-face families served from /tutor/fonts/.
 * (b) DoorWarm no longer injects a fonts.googleapis stylesheet at runtime.
 * Reading the source text (not the rendered DOM) keeps this deterministic and CI-cheap.
 */
const ROOT = resolve(__dirname, "..", "..");           // tutor-web/
const css = readFileSync(resolve(ROOT, "src/index.css"), "utf8");
const doorWarm = readFileSync(resolve(ROOT, "src/door/DoorWarm.tsx"), "utf8");

describe("self-hosted fonts (Plan 4a §0.9A)", () => {
  it("index.css declares @font-face for JetBrains Mono, Fraunces, and Nunito", () => {
    for (const family of ["JetBrains Mono", "Fraunces", "Nunito"]) {
      const re = new RegExp(
        `@font-face\\s*{[^}]*font-family:\\s*'${family}'`,
        "s",
      );
      expect(re.test(css), `missing @font-face for ${family}`).toBe(true);
    }
  });

  it("every @font-face serves woff2 from the /tutor/fonts/ base with font-display: swap", () => {
    const faces = css.match(/@font-face\s*{[^}]*}/gs) ?? [];
    const self = faces.filter((f) => /url\('\/tutor\/fonts\//.test(f));
    expect(self.length).toBeGreaterThanOrEqual(5);
    for (const f of self) {
      expect(/font-display:\s*swap/.test(f), `font-display: swap missing in: ${f}`).toBe(true);
      expect(/\.woff2'\)\s*format\('woff2/.test(f), `not a woff2 src in: ${f}`).toBe(true);
    }
  });

  it("DoorWarm no longer injects a fonts.googleapis stylesheet", () => {
    expect(doorWarm.includes("fonts.googleapis")).toBe(false);
    expect(doorWarm.includes("door-warm-fonts")).toBe(false);
  });
});
```

- [ ] **Step 6: Run the new test — expect GREEN.** From the repo root:

```powershell
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test -- src/__tests__/fontLoading.test.ts
```

Expected: vitest reports the `fontLoading.test.ts` file with 3 passing tests, `Test Files 1 passed`. **STOP if** any assertion fails — most likely the `@font-face` block was placed wrong (Step 3) or `DoorWarm.tsx` still contains `fonts.googleapis`/`door-warm-fonts` (Step 4).

- [ ] **Step 7: Run the full frontend vitest suite** (the import-graph change in `DoorWarm.tsx` plus the global `@font-face` could affect any DoorWarm-rendering test; never trust a partial run — review-workflow rule):

```powershell
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test
```

Expected: all vitest files pass (the inherited baseline from Task 0 Step 7, plus the new `fontLoading.test.ts`). **STOP and report if** any previously-green DoorWarm test now reds — a likely cause is the dropped `useEffect` import breaking a render; re-check Step 4.

- [ ] **Step 8: Commit to `lane-b/plan4a` (explicit paths only — never `git add -A`; the worktree's `public/` holds many untracked demo artifacts, Section 0 #1).**

```powershell
git -C ..\jarvis-kotlin-lane-b add `
  tutor-web/public/fonts/JetBrainsMono-400.woff2 `
  tutor-web/public/fonts/JetBrainsMono-700.woff2 `
  tutor-web/public/fonts/Fraunces-400.woff2 `
  tutor-web/public/fonts/Fraunces-600.woff2 `
  tutor-web/public/fonts/Fraunces-900.woff2 `
  tutor-web/public/fonts/Fraunces-600italic.woff2 `
  tutor-web/public/fonts/Nunito-400.woff2 `
  tutor-web/public/fonts/Nunito-600.woff2 `
  tutor-web/public/fonts/Nunito-700.woff2 `
  tutor-web/public/fonts/Nunito-800.woff2 `
  tutor-web/public/fonts/Nunito-900.woff2 `
  tutor-web/src/index.css `
  tutor-web/src/door/DoorWarm.tsx `
  tutor-web/src/__tests__/fontLoading.test.ts
git -C ..\jarvis-kotlin-lane-b commit -m "feat(fonts): self-host JetBrains Mono/Fraunces/Nunito woff2 (gwfh static); drop DoorWarm CDN injection (Plan 4a §0.9A)"
```

> **STOP before committing if** `git -C ..\jarvis-kotlin-lane-b status --short tutor-web/public/fonts/` shows any `.woff2` you did NOT intend (e.g. a leftover `_<Family>` extraction artifact) — add only the 11 named faces. Confirm the staged set is exactly those 14 paths (11 fonts + index.css + DoorWarm.tsx + fontLoading.test.ts) with `git -C ..\jarvis-kotlin-lane-b diff --cached --name-only`.

---

## Task 2 — DESIGN.md autogen + markers + drift test (§0.9B)

Adds an **ADDITIVE machine-generated token mirror** to DESIGN.md (PM-ratified scope, finding #5): `tools/generate-design-md.mjs` parses `index.css`'s `:root` and `@theme` custom properties and emits a token table into DESIGN.md between `<!-- AUTOGEN:tokens BEGIN -->` / `<!-- AUTOGEN:tokens END -->` markers (hand-written prose AND the existing YAML front-matter both survive untouched). A drift test regenerates the section in-memory and string-compares it against the committed file — drift = red, exactly the CI shape the council wanted. An npm script wires it for local use.

> **Scope clarification (finding #5 — do NOT overclaim):** this gate closes drift between the new AUTOGEN table and `index.css` ONLY. It does NOT regenerate or gate the DESIGN.md **YAML front-matter** (DESIGN.md:1-98 + the line-164 "Source of truth: the YAML front-matter … Mirrored into index.css @theme" rule) — by PM ruling the YAML front-matter stays the human source of truth and is left untouched (additive markers section only). The YAML ⇄ `index.css` drift is therefore NOT closed by this task; it is a CARRIED FOLLOW-UP (recorded in the Task 6 manifest). Also note: `index.css`'s `:root` has no `--type-display*`/`--tracking-*` (those live only in the DESIGN.md YAML, with `--tracking-mega` partly in `@theme`), so the generated table is the `index.css`-subset of tokens — sufficient for the splice, not the full DESIGN.md token set. §0.9B's earlier "replaces the manually-maintained mirror" phrasing is SUPERSEDED by this additive scope.

**The known `viz/theme.ts` INK/PAPER drift (`#0a0a0a`/`#f5f5f0` vs DESIGN.md `#000000`/`#ffffff`) is NOT fixed here** — fixing it changes viz rendering and needs Plan-4b rendered gates; record it as a carried follow-up only (Section 0 #3). (No learner-facing strings — tooling only.)

**Files (all in the worktree `..\jarvis-kotlin-lane-b`):**
- Create: `tools/generate-design-md.mjs`
- Create: `tools/generate-design-md.test.mjs`
- Modify: `DESIGN.md` (insert the AUTOGEN markers + the generated block)
- Create: `tutor-web/src/__tests__/designMdSync.test.ts`
- Modify: `tutor-web/package.json` (additive `design:check` script)

- [ ] **Step 1: Write the generator `tools/generate-design-md.mjs`.** It (1) reads `tutor-web/src/index.css`, (2) regex-parses every `--name: value;` declaration inside the `:root { … }` block and inside the `@theme { … }` block, (3) renders a deterministic Markdown table (sorted by name within each section), and (4) when given `--write`, splices that table between the AUTOGEN markers in `DESIGN.md`; otherwise it prints the rendered block to stdout (the mode the drift test uses). Create `..\jarvis-kotlin-lane-b\tools\generate-design-md.mjs`:

```js
#!/usr/bin/env node
/**
 * Plan 4a §0.9B — generate the DESIGN.md token table from tutor-web/src/index.css.
 *
 * Source of truth for the TABLE is index.css (:root + @theme custom properties).
 * Hand-written prose in DESIGN.md OUTSIDE the AUTOGEN markers is never touched.
 *
 * Usage:
 *   node tools/generate-design-md.mjs            # print the generated block to stdout
 *   node tools/generate-design-md.mjs --write    # splice it into DESIGN.md between the markers
 *
 * The drift test (designMdSync.test.ts) calls the stdout mode and string-compares
 * against the committed DESIGN.md block — any divergence reds the frontend job.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(HERE, "..");
const CSS_PATH = resolve(REPO_ROOT, "tutor-web", "src", "index.css");
const DESIGN_PATH = resolve(REPO_ROOT, "DESIGN.md");

const BEGIN = "<!-- AUTOGEN:tokens BEGIN -->";
const END = "<!-- AUTOGEN:tokens END -->";

/** Extract the body of the FIRST top-level `<selector> { ... }` block whose header matches `headerRe`. */
function blockBody(css, headerRe) {
  const m = css.match(headerRe);
  if (!m) return null;
  const start = css.indexOf("{", m.index);
  if (start === -1) return null;
  // brace-match to find the matching close (the token blocks contain no nested braces today,
  // but match defensively so a future nested rule does not silently truncate the table).
  let depth = 0;
  for (let i = start; i < css.length; i++) {
    if (css[i] === "{") depth++;
    else if (css[i] === "}") {
      depth--;
      if (depth === 0) return css.slice(start + 1, i);
    }
  }
  return null;
}

/** Parse `--name: value;` declarations (ignores comments and blank lines). Returns [{name, value}] sorted by name. */
function parseTokens(body) {
  if (body == null) return [];
  // strip /* ... */ comments first so a `;` inside a comment can't fake a declaration
  const clean = body.replace(/\/\*[\s\S]*?\*\//g, "");
  const out = [];
  const re = /(--[A-Za-z0-9-]+)\s*:\s*([^;]+);/g;
  let m;
  while ((m = re.exec(clean)) !== null) {
    out.push({ name: m[1].trim(), value: m[2].replace(/\s+/g, " ").trim() });
  }
  out.sort((a, b) => a.name.localeCompare(b.name, "en"));
  return out;
}

function renderTable(title, tokens) {
  const lines = [`### ${title}`, "", "| Token | Value |", "|---|---|"];
  for (const t of tokens) {
    // escape pipe + backtick the value so a value containing `|` (none today) can't break the table
    const value = t.value.replace(/\|/g, "\\|");
    lines.push(`| \`${t.name}\` | \`${value}\` |`);
  }
  return lines.join("\n");
}

export function generateBlock(css) {
  const root = parseTokens(blockBody(css, /:root\s*{/));
  const theme = parseTokens(blockBody(css, /@theme\s*{/));
  return [
    "_Auto-generated from `tutor-web/src/index.css` by `tools/generate-design-md.mjs`. Do not edit by hand — run `npm run design:check`._",
    "",
    renderTable(":root custom properties", root),
    "",
    renderTable("@theme utilities", theme),
  ].join("\n");
}

export function spliceIntoDesign(designText, block) {
  const b = designText.indexOf(BEGIN);
  const e = designText.indexOf(END);
  if (b === -1 || e === -1 || e < b) {
    throw new Error(
      `DESIGN.md is missing the AUTOGEN markers (${BEGIN} / ${END}); add them once before --write.`,
    );
  }
  const before = designText.slice(0, b + BEGIN.length);
  const after = designText.slice(e);
  return `${before}\n${block}\n${after}`;
}

function main() {
  const css = readFileSync(CSS_PATH, "utf8");
  const block = generateBlock(css);
  if (process.argv.includes("--write")) {
    const design = readFileSync(DESIGN_PATH, "utf8");
    writeFileSync(DESIGN_PATH, spliceIntoDesign(design, block));
    process.stderr.write("DESIGN.md token block updated.\n");
  } else {
    process.stdout.write(block);
  }
}

// Run main only when invoked directly (not when imported by the test).
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
```

- [ ] **Step 2: Insert the AUTOGEN markers into DESIGN.md and generate the block once.** DESIGN.md today (read Section 0 #3) keeps tokens in the YAML front-matter and prose; the autogen table is an ADDITIVE machine-readable mirror appended after the prose. Add the marker pair near the end of DESIGN.md (after the `# How this file is used` section), then run the generator in `--write` mode to fill it.

First, append the marker block to `..\jarvis-kotlin-lane-b\DESIGN.md` (after the final `- **References:** …` bullet of `# How this file is used`):

```markdown

# Token mirror (machine-generated)

The table below is generated from `tutor-web/src/index.css` and must not be hand-edited. It is the drift-gated mirror of the live CSS custom properties; the YAML front-matter above remains the human source of truth for prose/rationale. The CI frontend job reds if this block drifts from `index.css` (Plan 4a §0.9B).

<!-- AUTOGEN:tokens BEGIN -->
<!-- AUTOGEN:tokens END -->
```

Then run the generator to populate it. From the repo root:

```powershell
node ..\jarvis-kotlin-lane-b\tools\generate-design-md.mjs --write
```

Expected: stderr prints `DESIGN.md token block updated.` and the file now has a populated table between the markers. Inspect it:

```powershell
Select-String -Path ..\jarvis-kotlin-lane-b\DESIGN.md -Pattern '--color-accent|--ease-standard|--shadow-hard' -Context 0,0
```

Expected: rows like `` | `--color-accent` | `#fde047` | ``, `` | `--ease-standard` | … | ``, `` | `--shadow-hard` | … | ``. **STOP if** the block is empty (markers missing or in the wrong order) or the table omits known tokens (the `:root`/`@theme` regex did not match — re-check Step 1).

- [ ] **Step 3: Write the node unit test for the generator** (`tools/generate-design-md.test.mjs`, node's built-in test runner — matches the existing `tools/*.test.mjs` convention). Create `..\jarvis-kotlin-lane-b\tools\generate-design-md.test.mjs`:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateBlock, spliceIntoDesign } from "./generate-design-md.mjs";

test("generateBlock parses :root + @theme tokens into a sorted table", () => {
  const css = `
:root {
  --color-b: #222;
  /* a comment with a ; semicolon inside should be ignored */
  --color-a: #111;
}
@theme {
  --ease-x: var(--ease-x);
}
`;
  const block = generateBlock(css);
  // sorted: --color-a before --color-b
  assert.ok(block.indexOf("--color-a") < block.indexOf("--color-b"));
  assert.ok(block.includes("| `--color-a` | `#111` |"));
  assert.ok(block.includes("| `--ease-x` | `var(--ease-x)` |"));
  assert.ok(!block.includes("a comment"));
});

test("spliceIntoDesign replaces only the marker region", () => {
  const design = "PROSE\n<!-- AUTOGEN:tokens BEGIN -->\nOLD\n<!-- AUTOGEN:tokens END -->\nTAIL";
  const out = spliceIntoDesign(design, "NEW");
  assert.ok(out.startsWith("PROSE\n<!-- AUTOGEN:tokens BEGIN -->"));
  assert.ok(out.endsWith("<!-- AUTOGEN:tokens END -->\nTAIL"));
  assert.ok(out.includes("NEW"));
  assert.ok(!out.includes("OLD"));
});

test("spliceIntoDesign throws when markers are absent", () => {
  assert.throws(() => spliceIntoDesign("no markers here", "X"), /AUTOGEN markers/);
});
```

- [ ] **Step 4: Write the vitest drift gate** (lives under `tutor-web/src/__tests__/` so the frontend CI job runs it). It imports the generator's `generateBlock`, regenerates from the live `index.css`, and asserts the committed DESIGN.md block matches byte-for-byte. Create `..\jarvis-kotlin-lane-b\tutor-web\src\__tests__\designMdSync.test.ts`:

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, it, expect } from "vitest";
// generator lives at <repo>/tools; this test lives at <repo>/tutor-web/src/__tests__
import { generateBlock } from "../../../tools/generate-design-md.mjs";

const REPO_ROOT = resolve(__dirname, "..", "..", "..");
const css = readFileSync(resolve(REPO_ROOT, "tutor-web/src/index.css"), "utf8");
const design = readFileSync(resolve(REPO_ROOT, "DESIGN.md"), "utf8");

const BEGIN = "<!-- AUTOGEN:tokens BEGIN -->";
const END = "<!-- AUTOGEN:tokens END -->";

function committedBlock(text: string): string {
  const b = text.indexOf(BEGIN);
  const e = text.indexOf(END);
  if (b === -1 || e === -1) throw new Error("DESIGN.md AUTOGEN markers missing");
  return text.slice(b + BEGIN.length, e).replace(/^\n/, "").replace(/\n$/, "");
}

describe("DESIGN.md token block stays in sync with index.css (Plan 4a §0.9B)", () => {
  it("regenerated block equals the committed block (run `npm run design:check` to fix drift)", () => {
    const regenerated = generateBlock(css);
    expect(committedBlock(design)).toBe(regenerated);
  });
});
```

- [ ] **Step 5: Add the `design:check` npm script** (additive — package.json is shared baseline, Task 0 Step 5 documents this). In `..\jarvis-kotlin-lane-b\tutor-web\package.json`, add to the `scripts` object (the script `cd`s up to repo root because the generator lives in `../tools`):

```json
    "design:check": "node ../tools/generate-design-md.mjs --write"
```

> Insert it after the existing `"e2e": "playwright test"` line (add a trailing comma to that line). Resulting `scripts` block:
> ```json
>   "scripts": {
>     "dev": "vite",
>     "build": "vite build",
>     "preview": "vite preview",
>     "test": "vitest run",
>     "e2e": "playwright test",
>     "design:check": "node ../tools/generate-design-md.mjs --write"
>   },
> ```

- [ ] **Step 6: Run the generator unit test (node) — expect GREEN.** From the repo root:

```powershell
node --test ..\jarvis-kotlin-lane-b\tools\generate-design-md.test.mjs
```

Expected: `# pass 3`, `# fail 0`. **STOP if** any test fails — the parser or splicer logic is wrong (Step 1).

- [ ] **Step 7: Run the vitest drift gate — expect GREEN** (it must already be in sync because Step 2 wrote the block from the same generator):

```powershell
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test -- src/__tests__/designMdSync.test.ts
```

Expected: 1 passing test. **STOP if** it reds — the committed block (Step 2) and the regenerated block disagree; re-run `node ..\jarvis-kotlin-lane-b\tools\generate-design-md.mjs --write` and re-stage DESIGN.md, then re-run.

- [ ] **Step 8: Prove the gate REDS on drift (fix-claim discipline — the test must actually catch drift).** Temporarily add a token to `index.css`, confirm the drift test fails, then revert. From the repo root:

```powershell
Add-Content ..\jarvis-kotlin-lane-b\tutor-web\src\index.css "`n:root { --plan4a-drift-probe: 1px; }"
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test -- src/__tests__/designMdSync.test.ts
```

Expected: the drift test FAILS (the regenerated block now contains `--plan4a-drift-probe`, the committed block does not). **STOP if it stays green** — the gate is not actually comparing; fix Step 4 before proceeding. Then REVERT the probe (restore the clean file) and confirm green again:

```powershell
git -C ..\jarvis-kotlin-lane-b checkout -- tutor-web/src/index.css
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test -- src/__tests__/designMdSync.test.ts
```

Expected: clean `index.css` restored; drift test passes again. (If `index.css` had staged Task-1 edits, the `checkout --` reverts to the last commit which INCLUDES Task 1's `@font-face` block — Task 1 committed it — so this is safe. Confirm the `@font-face` block is still present after the revert.)

- [ ] **Step 9: Run the full frontend vitest suite** (never trust a partial run — review-workflow rule):

```powershell
npm --prefix ..\jarvis-kotlin-lane-b\tutor-web test
```

Expected: all vitest files pass, including `designMdSync.test.ts` and Task 1's `fontLoading.test.ts`. **STOP and report if** any red.

- [ ] **Step 10: Commit to `lane-b/plan4a` (explicit paths only).**

```powershell
git -C ..\jarvis-kotlin-lane-b add `
  tools/generate-design-md.mjs `
  tools/generate-design-md.test.mjs `
  DESIGN.md `
  tutor-web/src/__tests__/designMdSync.test.ts `
  tutor-web/package.json
git -C ..\jarvis-kotlin-lane-b commit -m "feat(design): autogen DESIGN.md token table from index.css + drift gate (Plan 4a §0.9B)"
```

> **Carried follow-up (record in the Task 6 merge-prep manifest, do NOT fix here):** `viz/theme.ts` hardcodes INK `#0a0a0a` / PAPER `#f5f5f0` vs DESIGN.md `#000000` / `#ffffff` (Section 0 #3). The drift gate added here covers DESIGN.md ⇄ `index.css` only — it does NOT cover `theme.ts`, by design. Fixing `theme.ts` changes viz rendering and belongs to Plan 4b's rendered gates.

---

## Task 3 — Impeccable calibrate (REAL run) → post-process subset FILTER → pinned fail-open CI patch file (§0.9C)

Runs the **real** Impeccable detector against the repo (the council-locked sequence: calibrate → applicable-subset → version-pin `impeccable@2.3.2` → fail-open until both are done → only THEN blocking). **§0.9C was PM-amended 2026-06-12 after live CLI verification (finding #1, BLOCKER):** the real `impeccable@2.3.2 detect` CLI exposes ONLY `--json` / `--gpt` / `--gemini` — there is **NO `--config` flag and NO tool-native rule-subset/disable mechanism**. The "applicable-subset" leg is therefore implemented as a **POST-PROCESS FILTER**, not a config file: the detector runs unfiltered (`detect --json src/`), its JSON is piped through a committed allow-list (`tools/impeccable-rules.json`) applied by a small node script (`tools/impeccable-filter.mjs`) that drops findings whose antipattern id is not enabled. **The finding key in detect's JSON is `antipattern`** (e.g. `"overused-font"`), NOT a generic `rule-id` — the allow-list and the filter both key on `antipattern`. There is **no `impeccable.config.json`** anywhere in this task; the binary would silently ignore one.

Step 1 is the live calibrate run AND the surface guard — it verifies the WHOLE pipeline (`detect --json | node tools/impeccable-filter.mjs`) runs before any subset work; **if `detect`/`--json` is absent or the pipe errors, STOP and report verbatim** rather than guess. Step 2 catalogues the hits into a findings table keyed by `antipattern`. Step 3 writes the allow-list `tools/impeccable-rules.json` (enable ONLY antipatterns with ≥1 true-positive hit, plus DESIGN.md hard-locks; every disabled antipattern listed with a reason) and the filter script `tools/impeccable-filter.mjs`. Step 4 produces the CI patch FILE adding the fail-open PIPELINE step — **the patch is NOT applied to `test.yml` here; the PM applies it at merge** (test.yml is the one shared file the two-lane contract routes through a patch file, Section 0 #6). (No learner-facing strings — tooling only.)

**Files (all in the worktree `..\jarvis-kotlin-lane-b`):**
- Create: `build-review/impeccable-calibration-2026-06-12.json` (committed UNFILTERED calibrate output)
- Create: `tools/impeccable-rules.json` (the applicable-subset allow-list — `{"enabled":["<antipattern-id>", …], "disabled":{…}}`)
- Create: `tools/impeccable-filter.mjs` (reads detect JSON on stdin, drops findings whose `antipattern` is not enabled, exits per filtered count)
- Create: `build-review/tmp/lane-b-patches/test-yml-impeccable.patch` (PM-applied CI patch — the fail-open `detect … | node tools/impeccable-filter.mjs || true` pipeline)
- A findings table (Step 2) goes into the Task 6 merge-prep manifest / plan-index follow-ups — not its own source file

- [ ] **Step 1: REAL calibrate run — verify the CLI surface AND the whole pipeline before any subset work (calibrate-first STOP guard, finding #1).** The subset is a post-process filter, so the surface the executor depends on is BOTH `detect --json` (the producer) AND the ability to pipe its JSON into a node script. This step proves the entire `detect --json | filter` shape works before Steps 2–4 build on it — there is NO `--config` flag to check (it does not exist; do not look for one). First confirm the package + binary at the pinned version (no global install — `npx` pulls `impeccable@2.3.2`). From the repo root, into the worktree:

```powershell
npx --yes impeccable@2.3.2 --help
```

Expected: the help text lists a `detect` command and a `--json` flag, and lists ONLY `--json` / `--gpt` / `--gemini` as detect flags (NO `--config`, confirming the post-process-filter design). **STOP and report the help output VERBATIM if** there is no `detect` subcommand, or `--json` is not a flag of `detect`, or the package fails to resolve at `2.3.2`, OR a `--config`/rule-subset flag unexpectedly DOES exist (that would change the design back — report it; do not improvise). Do not improvise an alternate invocation; the PM decides any corrected sequence.

If the surface matches, run the calibrate detection and commit its UNFILTERED JSON (this is the evidence base — the filter is NOT applied to the committed calibration; we keep the full hit set so the enable/disable decisions in Step 3 are auditable):

```powershell
npx --yes impeccable@2.3.2 detect --json ..\jarvis-kotlin-lane-b\tutor-web\src\ > ..\jarvis-kotlin-lane-b\build-review\impeccable-calibration-2026-06-12.json
```

Expected: a non-empty JSON file. Inspect that it is valid JSON and SEE THE FINDING SHAPE — specifically confirm the per-finding rule key is `antipattern` (the key the allow-list + filter depend on; the council's "rule-id" was a generic paraphrase):

```powershell
$cal = Get-Content ..\jarvis-kotlin-lane-b\build-review\impeccable-calibration-2026-06-12.json -Raw | ConvertFrom-Json
Write-Host "valid JSON, $((Get-Item ..\jarvis-kotlin-lane-b\build-review\impeccable-calibration-2026-06-12.json).Length) bytes"
# Surface the actual finding key set so Step 2/3 key on the REAL field (expected: 'antipattern').
($cal | ConvertTo-Json -Depth 8) -split "`n" | Select-Object -First 40
```

Expected: no exception from `ConvertFrom-Json`; a byte count printed; the finding objects carry an `antipattern` field (e.g. `"antipattern": "overused-font"`). **STOP and report if** (a) the output is not valid JSON (the CLI may emit text+JSON mixed — capture the raw output verbatim and report; the `--json` contract is part of what this step verifies), (b) the file is empty (the detector found nothing AND emitted nothing — note whether it printed to stderr instead), or (c) the finding key is NOT `antipattern` (report the real key name verbatim; the allow-list + filter in Step 3 must key on whatever the binary actually emits — substitute the real key everywhere `antipattern` appears below).

- [ ] **Step 2: Catalogue the hits into a findings table (executor fills from the real calibration JSON).** Read the committed JSON and, for EACH distinct `antipattern` id that produced a finding, record one row. This table is the evidence base for the Step-3 enable/disable decision and goes into the Task 6 merge-prep manifest. Fill this template (one row per antipattern that fired; the executor reads the actual `antipattern` ids/messages from the JSON — do NOT invent ids):

```markdown
### Impeccable calibration findings (tutor-web/src/, impeccable@2.3.2, 2026-06-12)

| Antipattern id | # hits | Sample file:line | True positive? (real DESIGN.md violation) | Notes |
|---|---|---|---|---|
| <antipattern-from-json> | <n> | <path:line> | yes / no (false positive) | <one line — e.g. "flags the intentional DoorWarm radius which is a scoped demo violation, §0 #1"> |
```

> Classification rule for "True positive?": a hit is a TRUE POSITIVE only if it flags a real violation of the locked DESIGN.md system in PRODUCTION chrome. Hits inside the intentionally-DESIGN.md-violating demo (`DoorWarm.tsx` — serif/multi-hue/gradient/radius are deliberate and scoped under `.door-warm`, Section 0 #1) are FALSE POSITIVES for our gate. The post-process filter drops them by path-scoping the detect target and/or by leaving their antipattern out of the allow-list — note each as such with the reason.

- [ ] **Step 3a: Write the applicable-subset allow-list `tools/impeccable-rules.json` (executor derives from the Step-2 calibration).** **Decision rule:** enable ONLY antipatterns with >0 TRUE-POSITIVE hits OR antipatterns that protect a locked invariant we want enforced going forward even at zero current hits (radius-lock, arbitrary-color, gradient/blur-shadow bans — DESIGN.md hard locks). Disable every antipattern that produced ONLY false positives (demo-scoped) or is irrelevant to this repo's stack, and list each disabled antipattern WITH its reason. The file is a plain JSON allow-list consumed by `tools/impeccable-filter.mjs` (Step 3b) — NOT a config the binary reads (the binary has no config surface). Create `..\jarvis-kotlin-lane-b\tools\impeccable-rules.json` using the REAL `antipattern` ids from the calibration (this is the SHAPE — the executor substitutes the actual ids discovered in Steps 1–2):

```json
{
  "$comment": "Plan 4a §0.9C applicable-subset, applied as a POST-PROCESS FILTER (impeccable has no --config / rule-subset flag). 'enabled' = antipattern ids with >0 true-positive hits in build-review/impeccable-calibration-2026-06-12.json OR a DESIGN.md hard-lock we enforce forward. 'disabled' carries a reason per dropped antipattern. The key is the detect JSON finding field 'antipattern'. Version pinned to impeccable@2.3.2 in the CI command (council-1781132987 amendment).",
  "enabled": [
    "<enabled-antipattern-1>",
    "<enabled-antipattern-2>"
  ],
  "disabled": {
    "<disabled-antipattern-1>": "false-positive only: fires on the intentionally-DESIGN.md-violating DoorWarm demo (scoped under .door-warm, Section 0 #1)",
    "<disabled-antipattern-2>": "not applicable to this stack: <one-line reason, e.g. flags inline-style usage the SVG viz components require>"
  }
}
```

> **Executor notes:**
> - The demo doors (`DoorWarm.tsx`, `DoorBrutalist.tsx`) are intentional brand demos (Section 0 #1). The filter has no path-exclude of its own; keep their false-positive antipatterns OUT of `enabled` (and document them in `disabled`). If a whole-file exclude is cleaner, narrow the detect TARGET in the CI command (Step 4) to the production chrome dirs instead of `src/` — path-scope the producer, not a config the binary ignores.
> - `enabled` is the ONLY thing the filter honours; `disabled` is documentation (the reason ledger). An antipattern absent from `enabled` is dropped regardless of whether it appears in `disabled`.
> - There is **no `impeccable.config.json`** and **no `--config` flag** — do not create one; the binary would ignore it (finding #1).

- [ ] **Step 3b: Write the post-process filter `tools/impeccable-filter.mjs` (the subset mechanism).** It reads detect's JSON on stdin, loads the allow-list, drops every finding whose `antipattern` is not in `enabled`, prints the filtered report to stdout, prints a one-line summary to stderr, and exits 0 (kept = 0) or 1 (kept > 0) so a future un-fail-open flip can gate on the exit code. It is robust to the two shapes detect may emit (a top-level array of findings, or an object with a `findings`/`results`/`issues` array). Create `..\jarvis-kotlin-lane-b\tools\impeccable-filter.mjs`:

```js
#!/usr/bin/env node
/**
 * Plan 4a §0.9C — Impeccable applicable-subset, applied as a POST-PROCESS FILTER.
 *
 * impeccable@2.3.2 `detect` has NO --config / rule-subset flag (verified live, finding #1),
 * so the locked "applicable-subset" leg is implemented here: pipe `detect --json` through this
 * script, which keeps only findings whose `antipattern` id is in tools/impeccable-rules.json.
 *
 * Usage (the CI pipeline):
 *   npx --yes impeccable@2.3.2 detect --json src/ | node tools/impeccable-filter.mjs
 *
 * stdout: the filtered report, same shape as the input (array in -> array out; object in ->
 *         object out with the findings array replaced by the kept subset).
 * stderr: a one-line summary ("impeccable-filter: kept K of N findings (subset of M enabled)").
 * exit  : 0 when zero findings survive the subset, 1 when >=1 survives. The CI step appends
 *         `|| true` (fail-open) so this exit code does not fail the build until the PM unlock;
 *         the code is meaningful now so flipping to blocking later is just dropping `|| true`.
 *
 * The detect finding key is `antipattern` (e.g. "overused-font"). If the real binary emits a
 * different key, change FINDING_KEY below (and the allow-list) to match — see Task 3 Step 1 (c).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const FINDING_KEY = "antipattern";
const HERE = dirname(fileURLToPath(import.meta.url));
const RULES_PATH = resolve(HERE, "impeccable-rules.json");

/** Read all of stdin to a string (synchronous, fd 0). Empty stdin -> "". */
function readStdin() {
  try {
    return readFileSync(0, "utf8");
  } catch {
    return "";
  }
}

function loadEnabled() {
  const rules = JSON.parse(readFileSync(RULES_PATH, "utf8"));
  if (!Array.isArray(rules.enabled)) {
    throw new Error(`tools/impeccable-rules.json must have an "enabled" array`);
  }
  return new Set(rules.enabled);
}

/** Pull the findings array out of whatever top-level shape detect emitted. */
function extractFindings(parsed) {
  if (Array.isArray(parsed)) return { findings: parsed, container: null, key: null };
  for (const key of ["findings", "results", "issues", "violations"]) {
    if (parsed && Array.isArray(parsed[key])) {
      return { findings: parsed[key], container: parsed, key };
    }
  }
  // Unknown object shape with no recognizable array -> treat as zero findings (fail-open safe).
  return { findings: [], container: parsed, key: null };
}

function main() {
  const raw = readStdin().trim();
  const enabled = loadEnabled();

  // No JSON on stdin (detector printed nothing / wrote to stderr): emit an empty array, exit 0.
  if (raw === "") {
    process.stdout.write("[]\n");
    process.stderr.write(
      `impeccable-filter: no JSON on stdin; kept 0 of 0 findings (subset of ${enabled.size} enabled)\n`,
    );
    process.exit(0);
  }

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    // Mixed text+JSON or malformed: do not crash the fail-open CI step — report and pass through empty.
    process.stderr.write(`impeccable-filter: stdin was not valid JSON (${e.message}); kept 0\n`);
    process.stdout.write("[]\n");
    process.exit(0);
  }

  const { findings, container, key } = extractFindings(parsed);
  const total = findings.length;
  const kept = findings.filter((f) => enabled.has(f && f[FINDING_KEY]));

  let out;
  if (container && key) {
    out = { ...container, [key]: kept };
  } else {
    out = kept;
  }
  process.stdout.write(JSON.stringify(out, null, 2) + "\n");
  process.stderr.write(
    `impeccable-filter: kept ${kept.length} of ${total} findings (subset of ${enabled.size} enabled)\n`,
  );
  process.exit(kept.length > 0 ? 1 : 0);
}

main();
```

- [ ] **Step 3c: Validate the full pipeline locally (the real subset mechanism, end to end).** Run the detector and pipe it through the filter; confirm it parses, filters, and prints a kept/total summary without error:

```powershell
npx --yes impeccable@2.3.2 detect --json ..\jarvis-kotlin-lane-b\tutor-web\src\ | node ..\jarvis-kotlin-lane-b\tools\impeccable-filter.mjs | ConvertFrom-Json | Out-Null
Write-Host "pipeline OK — detect --json | impeccable-filter.mjs produced valid filtered JSON"
```

Expected: the filter's stderr prints `impeccable-filter: kept K of N findings (subset of M enabled)`; stdout is valid JSON (no `ConvertFrom-Json` exception). **STOP and report if** the pipe errors (e.g. `impeccable-rules.json` not found beside the script, or the `enabled` array is missing) — fix `tools/impeccable-rules.json` / the script path before committing. (Note: the calibration JSON committed in Step 1 stays the UNFILTERED full hit set — do NOT overwrite it with filtered output.)

- [ ] **Step 4: Write the CI patch FILE (NOT applied to test.yml — PM applies at merge).** The fail-open step runs the pinned detector PIPED THROUGH THE FILTER in the frontend job and NEVER fails the build yet (`|| true`) — fail-open is the locked stance until calibrate + subset are both landed and reviewed (Section 0 #2). The run-line has **no `--config`** (it does not exist, finding #1); the subset is the `| node ../tools/impeccable-filter.mjs` stage. Create the patch dir and the patch file. Author the patch as a unified diff that ADDS one step to the `frontend` job in `.github/workflows/test.yml`, immediately after the existing `vitest run` step (line 67) and before `Install Playwright browsers` (line 68). Create `..\jarvis-kotlin-lane-b\build-review\tmp\lane-b-patches\test-yml-impeccable.patch`:

```diff
--- a/.github/workflows/test.yml
+++ b/.github/workflows/test.yml
@@ -64,6 +64,11 @@ jobs:
       - name: npm ci
         run: npm ci
       - name: vitest run
         run: npm test
+      # Plan 4a §0.9C — Impeccable design-quality gate, FAIL-OPEN (council-1781132987 amendment).
+      # Pinned impeccable@2.3.2; applicable-subset applied as a POST-PROCESS FILTER (no --config
+      # flag exists). The `|| true` keeps it non-blocking until calibrate+subset are landed AND
+      # reviewed; flip to blocking in a follow-up once validated against a few green PRs.
+      - name: Impeccable detect (fail-open)
+        run: npx --yes impeccable@2.3.2 detect --json src/ | node ../tools/impeccable-filter.mjs || true
       - name: Install Playwright browsers (chromium only)
         run: npx playwright install --with-deps chromium
       - name: Playwright e2e
         run: npm run e2e
```

> **Executor notes on the patch:**
> - The frontend job has `working-directory: tutor-web` (test.yml:54-56), so the step runs from `tutor-web/`; hence the detect target `src/` (relative to `tutor-web/`) and the filter path `../tools/impeccable-filter.mjs` (repo-root `tools/`). The allow-list `tools/impeccable-rules.json` is resolved by the SCRIPT relative to its own location, so it needs no path in the run-line.
> - There is **no `--config` flag** (finding #1) — if Step 1's `--help` ever showed one, that is a design change to report, not to wire here.
> - **The EXECUTABLE run-line must be IDENTICAL everywhere it appears as a command** (this patch, Step 5's `Select-String` check, and the Task 6 MERGE-MANIFEST "CI patch" section) — finding #7. The canonical executable string is exactly: `npx --yes impeccable@2.3.2 detect --json src/ | node ../tools/impeccable-filter.mjs || true`. Do NOT paraphrase `src/` or drop the filter stage. (The core plan's §0.9C *summary* describes the SAME pipeline with repo-root-relative paths — `tutor-web/src/` and `tools/impeccable-filter.mjs` — because it speaks from repo root; the CI step runs with `working-directory: tutor-web`, so the executable form is the working-directory-relative one here. Same command, two vantage points — not a contradiction; the manifest pins the executable form.)
> - The hunk header line numbers (`@@ -64,6 +64,11 @@`) match `test.yml` as captured in Section 0 (the `vitest run` step at lines 66-67, `Install Playwright browsers` at line 68). Verify they still match the CURRENT `..\jarvis-kotlin-lane-b\.github\workflows\test.yml` before finalizing — if Plan 3 (or anyone) changed the frontend job since the recon, regenerate the hunk against the live file so the PM's `git apply` succeeds.
> - Do NOT run `git apply` on this patch in Lane B. It is delivered as a file for the PM to apply at merge. Verify it is at least well-formed without applying:
>
> ```powershell
> git -C ..\jarvis-kotlin-lane-b apply --check --recount build-review/tmp/lane-b-patches/test-yml-impeccable.patch
> Write-Host "patch applies cleanly (check-only; NOT applied)"
> ```
>
> Expected: no output from `--check` (exit 0) and the confirmation line. **STOP and report if** `--check` errors — the hunk does not match the live `test.yml`; regenerate the diff against the current file. (If `--check` passes, the working tree is unchanged — `git apply --check` does not modify files. Confirm with `git -C ..\jarvis-kotlin-lane-b status --short .github/` showing NO change to `test.yml`.)

- [ ] **Step 5: Confirm the four deliverable files exist, the pipeline run-line is the canonical string, and `test.yml` is UNTOUCHED.**

```powershell
Get-ChildItem `
  ..\jarvis-kotlin-lane-b\build-review\impeccable-calibration-2026-06-12.json,`
  ..\jarvis-kotlin-lane-b\tools\impeccable-rules.json,`
  ..\jarvis-kotlin-lane-b\tools\impeccable-filter.mjs,`
  ..\jarvis-kotlin-lane-b\build-review\tmp\lane-b-patches\test-yml-impeccable.patch | Select-Object Name, Length
# The patch must carry the canonical run-line verbatim (finding #7) and NO --config.
Select-String -Path ..\jarvis-kotlin-lane-b\build-review\tmp\lane-b-patches\test-yml-impeccable.patch -Pattern 'detect --json src/ \| node \.\./tools/impeccable-filter\.mjs \|\| true'
if (Select-String -Path ..\jarvis-kotlin-lane-b\build-review\tmp\lane-b-patches\test-yml-impeccable.patch -Pattern '--config' -Quiet) { Write-Host 'BUG: --config present in patch — remove it (finding #1)' }
git -C ..\jarvis-kotlin-lane-b status --short .github/workflows/test.yml
```

Expected: four non-empty files listed; the `Select-String` finds the canonical run-line; no `--config` warning; the `git status` line for `test.yml` prints NOTHING (the file is unmodified — the two-lane contract is intact). **STOP if** `test.yml` shows as modified — Lane B must never edit it directly; revert it (`git -C ..\jarvis-kotlin-lane-b checkout -- .github/workflows/test.yml`) and re-deliver the change via the patch file only.

- [ ] **Step 6: Commit to `lane-b/plan4a` (explicit paths only — note `build-review/` and `build-review/tmp/` hold many untracked artifacts; add ONLY these four paths).**

```powershell
git -C ..\jarvis-kotlin-lane-b add `
  build-review/impeccable-calibration-2026-06-12.json `
  tools/impeccable-rules.json `
  tools/impeccable-filter.mjs `
  build-review/tmp/lane-b-patches/test-yml-impeccable.patch
git -C ..\jarvis-kotlin-lane-b commit -m "feat(tooling): Impeccable calibrate + post-process subset filter + fail-open CI patch file (Plan 4a §0.9C; PM applies patch at merge)"
```

> **STOP before committing if** `git -C ..\jarvis-kotlin-lane-b diff --cached --name-only` shows anything other than those exact four paths — `build-review/` is full of untracked audit artifacts that must not be swept in. In particular, confirm NO `impeccable.config.json` is staged (it must not exist — finding #1).

## Task 4 — Visual baseline machinery + 3 baselines (shell + 2 theme reference pages) + INV-9.5 scope gate (§0.9D)

Adds an env-locked, separate Playwright config that snapshots **only the three spec-mandated surfaces** — the fixed shell + the TWO theme reference pages — plus the INV-9.5 scope gate that asserts the snapshot dir contains baselines from ONLY those three spec files (structural enforcement: lesson specs can never grow baselines). **This realigns to the spec (finding #2, BLOCKER):** spec §9.2 gate 6 (line 494), §9.4 amendment (b) (line 508), INV-9.5 (line 520), and active-constraints line 26 ALL say identically *"the fixed shell + 2 theme reference pages ONLY"* = **3 baselines**. The prior fragment shipped *shell + viz-demo* (2 baselines) and `viz-demo` is the viz GALLERY, not a theme reference page — and its INV-9.5 gate hard-coded `ALLOWED = ["shell.visual","viz-demo.visual"]`, which would REJECT the spec-required theme pages and ADMIT a page the spec never lists. That is corrected here.

**The 2 theme reference pages = the DESIGN.md "Two surfaces, never a third" (DESIGN.md:106-115): the brutalist DARK surface and the brutalist LIGHT surface.** Neither is routed in `main.tsx` (a Plan-3 file Lane B must not touch — Task 0), and `DoorWarm.tsx` is explicitly the *intentionally-DESIGN.md-violating* demo (Task 3 §0.9C), so it is NOT a theme reference page. Lane B therefore serves the two reference surfaces from its OWN Vite multi-page HTML harness under `tutor-web/` (zero-intersection, no `main.tsx` edit): a single harness entry renders the REAL theme tokens from `index.css` applied to a representative specimen sheet, selecting DARK vs LIGHT by query param. The DARK specimen mounts the real `DoorBrutalist` component (the canonical DARK surface, `data-testid="door-brutalist"`); the LIGHT specimen renders the same brutalist primitives on the `page-bg`/`page-fg`/`accent` LIGHT tokens. Both are deterministic: SVG figures only, **no emoji**, self-hosted fonts (Task 1), no async/RAF settle. (Why not viz-demo, finding #4: its gallery header uses emoji that render from the OS emoji font — non-deterministic across OSes regardless of font self-hosting — and it mounts ~22 RAF/d3-measured SVG tiles, the weakest possible target for a 0-tolerance full-page baseline. It is gated STRUCTURALLY by Plan-3 Task 9's family no-clip spec, never by an exact-pixel baseline.)

The shell page is rendered against the worktree's own Vite dev server on 5173 with **no Kotlin backend** — its spec stubs the first-paint `/api` contract the same way `e2e/tutor-shell-api-contract.spec.ts` does (REUSE that stub list verbatim). The two theme reference pages are static harness HTML (no `<App/>`, no auth, no `/api`) so they need **zero** stubs. Baselines are generated and committed in this task; per council (b) the human re-commit owner at merge is the PM, but Lane B generates the first set so the gate is real. Env-lock (`workers:1`, `deviceScaleFactor:1`, headless, `reducedMotion:'reduce'`, pinned `snapshotPathTemplate`) makes the PNGs reproducible across machines on the same OS.

**Recon facts this task is built on (verified 2026-06-12):**
- `tutor-web/playwright.config.ts` uses `testDir:"./e2e"`, `baseURL:"http://localhost:5173"`, `webServer.command:"npm run dev"`, `reuseExistingServer:true`. The new visual config is a SEPARATE file with `testDir:"./e2e/visual"` so the lesson specs in `e2e/` can never contribute a baseline.
- The shell mounts under `<BrowserRouter basename="/tutor">` (`src/main.tsx:73`); `/tutor/` routes to `<App/>`. Its first-paint `/api` calls (auto-session, me/export, last-task, fsrs/forecast, tasks) MUST be stubbed or `<App/>` redirects to `/tutor/login`. Shell paint anchors that exist today: `data-testid="app-shell"` (`AppShell.tsx:18`), the brand text `JARVIS · TUTOR` (`TutorWorkspace.tsx:151`), `data-testid="header-ledger-btn"` (`App.tsx:318`).
- The two DESIGN.md surfaces (DESIGN.md:106-115): **DARK** = `panel-dark-bg` (black) field + `panel-dark-fg`/`accent` (yellow) — rendered by the real `DoorBrutalist` component (`tutor-web/src/door/DoorBrutalist.tsx`, root `data-testid="door-brutalist"`, uses `bg-panel-dark-bg text-overlay-fg`, pure SVG `Figure`, no emoji). **LIGHT** = `page-bg` (white) + `page-fg` (black ink) + `accent` (yellow fill) — "long-form reading stays on light" (DESIGN.md:115). Both are driven entirely by `index.css` tokens, so a token change reflows the baseline (the whole point).
- `main.tsx` has NO `/door` / `/door-compare` / theme route, and Lane B cannot add one (`main.tsx` is a Plan-3 deliverable). Hence the Lane-B-owned static HTML harness under `tutor-web/` (Vite serves root-level `*.html` as additional entry points; the dev server resolves `http://localhost:5173/<name>.html`).
- No `tutor-web/playwright.visual.config.ts`, no `tutor-web/e2e/visual/`, and no `tutor-web/theme-ref.html` / `tutor-web/src/themeRefHarness.tsx` exist yet (zero-intersection confirmed; none are Plan-3 files).

**Files (ALL in the worktree `../jarvis-kotlin-lane-b`):**
- Create: `tutor-web/theme-ref.html` (Lane-B-owned Vite HTML entry for the 2 theme reference surfaces)
- Create: `tutor-web/src/themeRefHarness.tsx` (the harness entry — mounts DARK or LIGHT by `?surface=`)
- Create: `tutor-web/playwright.visual.config.ts`
- Create: `tutor-web/e2e/visual/shell.visual.spec.ts`
- Create: `tutor-web/e2e/visual/theme-dark.visual.spec.ts`
- Create: `tutor-web/e2e/visual/theme-light.visual.spec.ts`
- Create: `tutor-web/src/__tests__/baselineScope.test.ts` (INV-9.5 scope gate, a vitest — MUST live under `src/` so `npm test`'s vitest `include:["src/**/*.{test,spec}.{ts,tsx}"]` collects it; `vite.config.ts` is a Plan-3 file Lane B cannot edit to widen the glob)
- Create (generated, committed): `tutor-web/e2e/visual/__screenshots__/**` (the 3 PNG baselines)
- Modify: `tutor-web/package.json` (add `e2e:visual` + `e2e:visual:update` scripts)

> All `npm` commands below run inside the worktree's own `tutor-web` (`../jarvis-kotlin-lane-b/tutor-web`) and assume Task 0 already ran `npm ci` there (own `node_modules`, never the main tree's). If a step fails with "Cannot find module", run `npm --prefix ../jarvis-kotlin-lane-b/tutor-web ci` first.

- [ ] **Step 1: Write the Lane-B-owned theme-reference harness HTML entry.** Vite serves root-level `*.html` in the project as additional entry points, so `tutor-web/theme-ref.html` is reachable at `http://localhost:5173/theme-ref.html` on the worktree's own dev server — no `main.tsx` route, no Plan-3 file touched. Create `../jarvis-kotlin-lane-b/tutor-web/theme-ref.html`:

```html
<!doctype html>
<html lang="ro">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Theme reference (Plan 4a visual baseline)</title>
  </head>
  <body>
    <div id="theme-ref-root"></div>
    <script type="module" src="/src/themeRefHarness.tsx"></script>
  </body>
</html>
```

- [ ] **Step 2: Write the harness entry that mounts the two REAL theme reference surfaces.** It imports `index.css` (so the real `@theme` tokens + Task-1 `@font-face` apply), reads `?surface=dark|light`, and renders the DARK surface via the real `DoorBrutalist` component or the LIGHT brutalist specimen — both driven only by `index.css` tokens (no hardcoded hex), deterministic (SVG figure, no emoji, no async). Create `../jarvis-kotlin-lane-b/tutor-web/src/themeRefHarness.tsx`:

```tsx
import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { DoorBrutalist } from "./door/DoorBrutalist";
import { getConcept } from "./door/concept";

/**
 * Plan-4a Task 4 (§0.9D, spec §9.2 g6 / INV-9.5) — the two THEME REFERENCE PAGES.
 *
 * DESIGN.md "Two surfaces, never a third" (DESIGN.md:106-115): the brutalist DARK surface
 * and the brutalist LIGHT surface. These are the spec's "2 theme reference pages" baselined
 * alongside the fixed shell (3 baselines total). They are NOT routed in main.tsx (a Plan-3
 * file Lane B must not edit), so Lane B owns this standalone Vite harness entry. Everything
 * is driven by the real index.css tokens — a token change reflows the baseline, which is the
 * drift signal we want. Deterministic on purpose: SVG figures only, NO emoji, self-hosted
 * fonts (Task 1), no async/RAF — so a 0-tolerance full-page screenshot is reproducible.
 *
 * ?surface=dark  -> the real DoorBrutalist (canonical DARK surface; data-testid door-brutalist)
 * ?surface=light -> the brutalist LIGHT specimen on page-bg/page-fg/accent tokens
 */
const surface = new URLSearchParams(window.location.search).get("surface") === "light" ? "light" : "dark";

// A fixed concept so the rendered content never varies run-to-run.
const concept = getConcept("mergesort");

/** The LIGHT brutalist reference: the same design system on the white work surface. */
function ThemeRefLight() {
  return (
    <div
      data-testid="theme-ref-light"
      className="min-h-screen w-full bg-page-bg font-mono text-page-fg"
      style={{ display: "flex", flexDirection: "column" }}
    >
      <main style={{ flex: "1 0 auto", width: "100%", maxWidth: 1280, margin: "0 auto", padding: "0 clamp(28px,6vw,96px)" }}>
        <header
          className="border-b-2 border-border-strong"
          style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 24, padding: "clamp(22px,4vh,44px) 0 16px" }}
        >
          <span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>JARVIS · TUTOR</span>
          <span style={{ fontSize: 12, letterSpacing: "0.15em", opacity: 0.7 }}>LIGHT SURFACE — REFERENCE</span>
        </header>

        <section style={{ padding: "clamp(36px,7vh,88px) 0", display: "grid", gap: "clamp(28px,5vh,52px)" }}>
          <p style={{ fontSize: 12, letterSpacing: "0.26em", textTransform: "uppercase", opacity: 0.7 }}>
            <span className="text-accent" style={{ fontWeight: 700, background: "var(--color-accent)", color: "var(--color-page-fg)", padding: "2px 8px" }}>
              {concept.subject}
            </span>{" "}
            / {concept.track} / {concept.familiarity}
          </p>
          <h1 style={{ margin: 0, lineHeight: 1.0, letterSpacing: "-0.02em", textTransform: "uppercase", fontWeight: 700, fontSize: "clamp(44px,7vw,104px)" }}>
            <span style={{ display: "block" }}>{concept.titleTop}</span>
            <span style={{ display: "block", background: "var(--color-accent)", padding: "0 12px", width: "fit-content" }}>{concept.titleAccent}</span>
          </h1>
          {/* equation in a hard-bordered mono plate — the LIGHT elevation token */}
          <div className="border-2 border-border-strong" style={{ display: "inline-block", width: "fit-content", padding: "12px 18px", fontSize: "clamp(15px,1.5vw,20px)", boxShadow: "var(--shadow-hard)" }}>
            {concept.equation}
          </div>
          <p style={{ maxWidth: "48ch", fontSize: "clamp(14px,1.2vw,17px)", lineHeight: 1.6, opacity: 0.85 }}>{concept.gistLead}</p>
          <div style={{ display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
            <button
              type="button"
              data-testid="theme-ref-light-begin"
              className="bg-accent text-page-fg"
              style={{ border: "none", cursor: "pointer", fontFamily: "inherit", fontWeight: 700, letterSpacing: "0.22em", textTransform: "uppercase", fontSize: "clamp(14px,1.1vw,16px)", padding: "16px 36px", boxShadow: "var(--shadow-hard)" }}
            >
              Begin →
            </button>
            <span style={{ fontSize: 12.5, letterSpacing: "0.04em", opacity: 0.6 }}>
              uppercase <span className="border border-border-strong" style={{ padding: "3px 8px", fontSize: 11 }}>tracking-widest</span> labels
            </span>
          </div>
        </section>
      </main>
    </div>
  );
}

createRoot(document.getElementById("theme-ref-root")!).render(
  <StrictMode>
    {surface === "light" ? (
      <ThemeRefLight />
    ) : (
      <div data-testid="theme-ref-dark">
        <DoorBrutalist concept={concept} brandMark={<span style={{ letterSpacing: "0.3em", fontWeight: 700, fontSize: 13 }}>JARVIS</span>} />
      </div>
    )}
  </StrictMode>,
);
```

> **Determinism note:** `getConcept("mergesort")` returns a fixed `DoorConcept` (the door demo data model, `src/door/concept.ts`); its `Figure` is a deterministic SVG (merge spec) — no emoji, no RAF, no network. The shell spec at Step 4 is the only baseline needing `/api` stubs; both theme-ref baselines are pure static render.

- [ ] **Step 3: Write the separate env-locked visual config.** Create `../jarvis-kotlin-lane-b/tutor-web/playwright.visual.config.ts`:

```ts
import { defineConfig } from "@playwright/test";

/**
 * Plan-4a Task 4 (spec §9.2 g5/g6, §0.9D) — env-locked visual-baseline config, SEPARATE from
 * playwright.config.ts. testDir is e2e/visual ONLY, so the lesson specs under e2e/ can NEVER grow
 * a baseline (structural scope enforcement; INV-9.5 double-checks it). Env-lock = deterministic PNGs:
 *   workers:1            — no parallel render races
 *   deviceScaleFactor:1  — 1 physical px = 1 CSS px (HiDPI machines would otherwise 2x the PNG)
 *   headless + reduced motion — no animation frame in the capture
 *   snapshotPathTemplate — every baseline lands under e2e/visual/__screenshots__/<spec>/<name>
 *
 * The three permitted surfaces (and ONLY these three): /tutor/ (AppShell fixed shell) +
 * /theme-ref.html?surface=dark + /theme-ref.html?surface=light (the 2 DESIGN.md theme reference
 * pages — "Two surfaces, never a third"). All served by the worktree's own `npm run dev` (Vite)
 * with NO Kotlin backend — shell.visual stubs the first-paint /api contract; the theme-ref pages
 * are static harness HTML and need no stub.
 */
export default defineConfig({
  testDir: "./e2e/visual",
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  reporter: "list",
  snapshotPathTemplate: "{testDir}/__screenshots__/{testFilePath}/{arg}{ext}",
  use: {
    baseURL: "http://localhost:5173",
    locale: "ro-RO",
    timezoneId: "Europe/Bucharest",
    reducedMotion: "reduce",
    deviceScaleFactor: 1,
    headless: true,
    viewport: { width: 1280, height: 900 },
  },
  expect: {
    // Exact match — drift in the fixed shell / gallery is a real signal, not noise.
    toHaveScreenshot: { maxDiffPixelRatio: 0, animations: "disabled" },
  },
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
```

- [ ] **Step 4: Write the shell visual spec (stubbed, no backend).** Create `../jarvis-kotlin-lane-b/tutor-web/e2e/visual/shell.visual.spec.ts`. The `/api` stub list is REUSED verbatim from `e2e/tutor-shell-api-contract.spec.ts` (the proven first-paint contract; without it `<App/>` redirects to `/tutor/login`):

```ts
import { test, expect } from "@playwright/test";

/**
 * Plan-4a Task 4 (§0.9D) — visual baseline of the AppShell FIXED shell at /tutor/.
 *
 * CI runs NO Kotlin backend (the frontend job is npm ci -> vitest -> playwright, no :8080), so the
 * first-paint /api contract is stubbed at the Playwright layer — the SAME stub set proven by
 * e2e/tutor-shell-api-contract.spec.ts. Without these, ensureTutorSession() 401s and <App/> redirects
 * to /tutor/login (a different, non-shell paint). The shot is fullPage with maxDiffPixelRatio 0
 * (config). Only the fixed shell is in frame on a no-data dashboard: header brand, nav, ledger button.
 */
test("AppShell fixed shell at /tutor/ matches the baseline", async ({ page }) => {
  // ── First-paint /api stubs (verbatim from tutor-shell-api-contract.spec.ts) ──
  await page.route("**/api/v1/tutor/auto-session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );
  await page.route("**/api/v1/me/export", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }),
    }),
  );
  await page.route("**/api/v1/last-task", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ taskId: null }) }),
  );
  await page.route("**/api/v1/fsrs/forecast", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ dueNow: 0 }) }),
  );
  await page.route("**/api/v1/tasks", (route) => {
    if (route.request().method() === "GET") {
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ tasks: [] }) });
    } else {
      route.continue();
    }
  });

  await page.goto("/tutor/");

  // Anchor: the shell actually mounted (not a /login redirect) before we snapshot.
  await expect(page.getByTestId("app-shell")).toBeVisible({ timeout: 10_000 });
  // PM AMENDMENT 2026-06-12: "JARVIS · TUTOR" lives in TutorWorkspace, which only renders with an
  // active task — the no-task stubs paint ActiveTaskDashboard. app-shell + header-ledger-btn suffice.
  await expect(page.getByTestId("header-ledger-btn")).toBeVisible();

  await expect(page).toHaveScreenshot("shell.png", { fullPage: true });
});
```

- [ ] **Step 5: Write the two theme-reference visual specs (no stubs — static harness HTML).** The harness pages mount no `<App/>` and fire no `/api`, so no stubs are needed. Create `../jarvis-kotlin-lane-b/tutor-web/e2e/visual/theme-dark.visual.spec.ts`:

```ts
import { test, expect } from "@playwright/test";

/**
 * Plan-4a Task 4 (§0.9D, spec §9.2 g6 / INV-9.5) — visual baseline of the DARK theme reference page.
 *
 * DESIGN.md "Two surfaces, never a third": the brutalist DARK surface. Served by the Lane-B-owned
 * harness (theme-ref.html?surface=dark), which mounts the REAL DoorBrutalist component driven by
 * index.css tokens — a token change reflows this baseline. No /api, no emoji, SVG figure, self-hosted
 * fonts (Task 1) → a 0-tolerance full-page shot is deterministic on the reference OS.
 */
test("DARK theme reference page matches the baseline", async ({ page }) => {
  await page.goto("/tutor/theme-ref.html?surface=dark"); // PM AMENDMENT: Vite base "/tutor/"
  await expect(page.getByTestId("theme-ref-dark")).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId("door-brutalist")).toBeVisible();
  await expect(page).toHaveScreenshot("theme-dark.png", { fullPage: true });
});
```

Create `../jarvis-kotlin-lane-b/tutor-web/e2e/visual/theme-light.visual.spec.ts`:

```ts
import { test, expect } from "@playwright/test";

/**
 * Plan-4a Task 4 (§0.9D, spec §9.2 g6 / INV-9.5) — visual baseline of the LIGHT theme reference page.
 *
 * DESIGN.md "Two surfaces, never a third": the brutalist LIGHT work surface (page-bg/page-fg/accent;
 * "long-form reading stays on light", DESIGN.md:115). Served by the Lane-B-owned harness
 * (theme-ref.html?surface=light), driven entirely by index.css tokens. No /api, no emoji, deterministic.
 */
test("LIGHT theme reference page matches the baseline", async ({ page }) => {
  await page.goto("/tutor/theme-ref.html?surface=light"); // PM AMENDMENT: Vite base "/tutor/"
  await expect(page.getByTestId("theme-ref-light")).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId("theme-ref-light-begin")).toBeVisible();
  await expect(page).toHaveScreenshot("theme-light.png", { fullPage: true });
});
```

- [ ] **Step 6: Add the npm scripts.** Edit `../jarvis-kotlin-lane-b/tutor-web/package.json` — add `e2e:visual` and `e2e:visual:update` after the existing `e2e` script.

Find:

```json
    "test": "vitest run",
    "e2e": "playwright test"
```

Replace with:

```json
    "test": "vitest run",
    "e2e": "playwright test",
    "e2e:visual": "playwright test -c playwright.visual.config.ts",
    "e2e:visual:update": "playwright test -c playwright.visual.config.ts --update-snapshots"
```

- [ ] **Step 7: Generate the three baselines (this writes the committed PNGs).** From the worktree's `tutor-web`:

```powershell
npm --prefix ../jarvis-kotlin-lane-b/tutor-web run e2e:visual:update
```

Expected: Playwright starts the dev server on 5173, runs the 3 specs, and (because `--update-snapshots`) PASSES while WRITING the baselines:

```
Running 3 tests using 1 worker
  3 passed
```

Three PNGs now exist under `../jarvis-kotlin-lane-b/tutor-web/e2e/visual/__screenshots__/` (one per spec dir, named `shell.png` / `theme-dark.png` / `theme-light.png` with a platform suffix Playwright appends, e.g. `shell-chromium-linux.png` on CI vs `…-win32.png` locally — see the platform-suffix note at the end of this task). Confirm they were written:

```powershell
Get-ChildItem -Recurse ../jarvis-kotlin-lane-b/tutor-web/e2e/visual/__screenshots__ -Filter *.png | Select-Object -ExpandProperty FullName
```

Expected: exactly three `.png` paths, under `shell.visual.spec.ts/`, `theme-dark.visual.spec.ts/`, and `theme-light.visual.spec.ts/` directories.

- [ ] **Step 8: Confirm the baselines are stable (re-run WITHOUT update — expect GREEN).** A baseline that only passes the run that wrote it is worthless:

```powershell
npm --prefix ../jarvis-kotlin-lane-b/tutor-web run e2e:visual
```

Expected: `3 passed` (the just-written baselines match a fresh render — env-lock makes this deterministic).

- [ ] **Step 9: Write the INV-9.5 scope gate (vitest).** Create `../jarvis-kotlin-lane-b/tutor-web/src/__tests__/baselineScope.test.ts` — it lives under `src/` (NOT `e2e/visual/`) because `npm test`'s vitest `include` is `src/**/*.{test,spec}.{ts,tsx}` ONLY and `vite.config.ts` is a Plan-3 file Lane B cannot edit to widen it. It globs the `e2e/visual/__screenshots__` dir (resolved relative to this file) and asserts every baseline path traces to exactly the THREE permitted spec files (`shell.visual`, `theme-dark.visual`, or `theme-light.visual`) — no lesson/drill/gallery spec can ever smuggle in a baseline:

```ts
import { describe, it, expect } from "vitest";
import { readdirSync, statSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Plan-4a Task 4 (§0.9D, spec INV-9.5 line 520) — INV-9.5 baseline-scope gate.
 *
 * Structural enforcement of the spec's "fixed shell + 2 theme reference pages ONLY" rule:
 * every PNG under e2e/visual/__screenshots__ MUST live under a directory whose name contains
 * "shell.visual", "theme-dark.visual", or "theme-light.visual" (Playwright's snapshotPathTemplate
 * nests baselines under {testFilePath}). Any other baseline — a lesson/drill/gallery spec quietly
 * growing a snapshot — REDS this gate. Runs in `npm test` (the frontend vitest job), so it gates on
 * every CI run, JVM-free.
 */
// This test lives at tutor-web/src/__tests__/; the baselines live at tutor-web/e2e/visual/__screenshots__.
const HERE = dirname(fileURLToPath(import.meta.url));
const SCREENSHOTS = join(HERE, "..", "..", "e2e", "visual", "__screenshots__");
const ALLOWED = ["shell.visual", "theme-dark.visual", "theme-light.visual"];

function allPngPaths(dir: string): string[] {
  if (!existsSync(dir)) return [];
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) out.push(...allPngPaths(full));
    else if (name.endsWith(".png")) out.push(full);
  }
  return out;
}

describe("INV-9.5 visual-baseline scope", () => {
  it("every baseline PNG traces to exactly the three permitted spec files", () => {
    const pngs = allPngPaths(SCREENSHOTS);
    // The three baselines are generated in Task 4; if the dir is empty here the generation step was
    // skipped — that is itself a scope failure (the gate must guard real baselines, not nothing).
    expect(pngs.length, `no baselines found under ${SCREENSHOTS}; run e2e:visual:update`).toBeGreaterThan(0);
    const stray = pngs.filter((p) => !ALLOWED.some((a) => p.replace(/\\/g, "/").includes(a)));
    expect(stray, `baselines outside the permitted shell + 2 theme-ref specs:\n${stray.join("\n")}`).toEqual([]);
  });

  it("all three permitted baselines are present (shell + theme-dark + theme-light)", () => {
    const pngs = allPngPaths(SCREENSHOTS).map((p) => p.replace(/\\/g, "/"));
    expect(pngs.some((p) => p.includes("shell.visual")), "shell baseline missing").toBe(true);
    expect(pngs.some((p) => p.includes("theme-dark.visual")), "theme-dark baseline missing").toBe(true);
    expect(pngs.some((p) => p.includes("theme-light.visual")), "theme-light baseline missing").toBe(true);
  });
});
```

- [ ] **Step 10: Run the scope gate — expect GREEN:**

```powershell
npm --prefix ../jarvis-kotlin-lane-b/tutor-web test -- baselineScope
```

Expected: the `baselineScope.test.ts` suite passes (2 tests). (`npm test` is `vitest run`; the trailing arg filters to this file.)

- [ ] **Step 11: Prove INV-9.5 REDS on a planted stray baseline (seeded-violation, in a TEMP COPY — never in the real tree).** Copy the worktree's `__screenshots__` to a temp dir, plant a fake LESSON baseline, point the gate at the copy, confirm RED, discard the copy:

```powershell
$tmp = Join-Path $env:TEMP ("inv95-redtest-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force $tmp | Out-Null
Copy-Item -Recurse ../jarvis-kotlin-lane-b/tutor-web/e2e/visual/__screenshots__/* $tmp
# Plant a stray baseline that the gate must reject (a lesson spec growing a snapshot).
New-Item -ItemType Directory -Force (Join-Path $tmp "lesson.visual.spec.ts") | Out-Null
$realPng = Get-ChildItem -Recurse $tmp -Filter *.png | Select-Object -First 1
Copy-Item $realPng.FullName (Join-Path $tmp "lesson.visual.spec.ts\lesson.png")
# Re-run the SAME filter logic the gate uses, against the temp copy:
$allowed = @("shell.visual","theme-dark.visual","theme-light.visual")
$stray = Get-ChildItem -Recurse $tmp -Filter *.png | Where-Object {
  $p = $_.FullName.Replace("\","/"); -not ($allowed | Where-Object { $p.Contains($_) })
}
if ($stray) { Write-Host "[RED as expected] stray baseline(s):"; $stray | ForEach-Object { Write-Host "  $($_.FullName)" } }
else { Write-Host "[BUG] gate did NOT detect the planted stray — INV-9.5 is vacuous" }
Remove-Item -Recurse -Force $tmp
```

Expected: `[RED as expected] stray baseline(s):` followed by the planted `lesson.visual.spec.ts/lesson.png` path. **STOP if** it prints `[BUG]` — the scope filter does not actually reject strays. (This mirrors the vitest's filter exactly; the real `__screenshots__` is never mutated — only the temp copy is, and it is removed.)

- [ ] **Step 12: Commit (explicit paths — NEVER `git add -A`; the worktree's `tutor-web/public/` holds untracked demo artifacts).** From anywhere, target the worktree with `-C`:

```powershell
git -C ../jarvis-kotlin-lane-b add `
  tutor-web/theme-ref.html `
  tutor-web/src/themeRefHarness.tsx `
  tutor-web/playwright.visual.config.ts `
  tutor-web/e2e/visual/shell.visual.spec.ts `
  tutor-web/e2e/visual/theme-dark.visual.spec.ts `
  tutor-web/e2e/visual/theme-light.visual.spec.ts `
  tutor-web/src/__tests__/baselineScope.test.ts `
  tutor-web/e2e/visual/__screenshots__ `
  tutor-web/package.json
git -C ../jarvis-kotlin-lane-b commit -m @'
feat(plan4a): env-locked visual baselines (shell + 2 theme reference pages) + INV-9.5 scope gate

- theme-ref.html + themeRefHarness.tsx: Lane-B-owned Vite harness serving the 2
  DESIGN.md theme reference surfaces (DARK = real DoorBrutalist; LIGHT = brutalist
  specimen on page-bg/page-fg/accent), driven by index.css tokens — no main.tsx edit
- playwright.visual.config.ts: separate testDir e2e/visual, workers:1,
  deviceScaleFactor:1, headless, maxDiffPixelRatio:0, pinned snapshotPathTemplate
- shell.visual.spec.ts: /tutor/ AppShell fixed shell, /api first-paint stubs
  reused verbatim from tutor-shell-api-contract.spec.ts (CI has no backend)
- theme-dark/theme-light.visual.spec.ts: the 2 theme reference pages (static harness,
  no stubs); viz-demo dropped (emoji + RAF gallery = non-deterministic; structurally
  gated by Plan-3 Task 9 family no-clip, never an exact-pixel baseline)
- baselineScope.test.ts: INV-9.5 — every __screenshots__ PNG must trace to the
  shell + 2 theme-ref specs ONLY (red on a planted lesson baseline; proven in a temp copy)
- npm scripts e2e:visual + e2e:visual:update; the 3 generated PNG baselines committed

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

Expected: one commit on `lane-b/plan4a` listing the 8 source/config files plus the 3 PNGs.

> **Platform-suffix note for the PM merge (carried into the manifest):** Playwright appends a platform suffix to baseline filenames (`-darwin`/`-linux`/`-win32`). The 3 baselines generated on Windows (Lane B's likely host) will NOT match CI's `ubuntu-latest` render — the CI visual run would fail "missing snapshot for linux". The merge recipe (Task 6) therefore notes that the PM regenerates the 3 baselines on a Linux host (or the CI matrix is pinned to one OS) before turning the visual run blocking. Until then the `e2e:visual` script is NOT added to `test.yml` (this plan adds no CI wiring for it — same fail-open posture as Impeccable; the npm script exists for local/PM use and the INV-9.5 vitest gate, which IS in `npm test`, runs cross-platform because it inspects paths, not pixels).

---

## Task 5 — Grader-eval harness skeleton + 12 golden items + seeded-red proof (§0.9E)

Builds the deterministic-leg grader-eval harness: a JUnit 5 `@ParameterizedTest` that loads golden JSON fixtures from the classpath (`fixtures/grader-golden/{subject}/grade-scoring/{id}.json`), runs each through the REAL `GradeScoring` object, and asserts the computed `score` + `correct` match the golden `expected`. Scope is the DETERMINISTIC leg ONLY (`GradeScoring`) — LLM-judge and execution-grader golden sets are Plan 6 (this task ships only the dir convention for them). At least 12 golden items for GradeScoring spanning PA/ALO/PS rubric shapes including the edge cases the real scorer has branches for. A seeded-violation step proves the harness reds when an expected value is flipped (fix-claim discipline). Runs inside `:check` automatically (`src/test` placement + `useJUnitPlatform()` already on).

**Recon facts this task is built on (verified 2026-06-12 against the REAL code):**
- `GradeScoring` (`src/main/kotlin/jarvis/tutor/GradeScoring.kt`) — the deterministic leg. Exact contract:
  - `scoreFromRubric(rubric: Map<String, Boolean>): Double` → empty rubric returns `0.0`; otherwise `count{true} / size`.
  - `correctFromRubric(rubric: Map<String, Boolean>): Boolean` → `isNotEmpty() && all { it }`.
  - (also `isConfident(GradeResult)`, `normalizeAnswer`, `answerMatches` — out of scope for the rubric harness; the golden shape models the rubric leg per §0.9E.)
- `GradeResult` lives in `src/main/kotlin/jarvis/tutor/DrillGrader.kt:14` (`correct, rubric, score, misconception, elaboratedFeedback`).
- There is NO all-or-nothing class in production — `GradeScoring` has no `allOrNothing` method. The §0.9E "all-or-nothing" edge case is therefore modelled as a rubric where the only way to be `correct` is for EVERY item to pass (which is exactly `correctFromRubric`'s contract) — a golden item with one `false` proves a single failed criterion → `correct:false` even at high partial score. (Do NOT invent an all-or-nothing scorer; the harness tests the real object.)
- The test classpath has ONLY `kotlin("test")` (`build.gradle.kts:64`) — JUnit Jupiter `@ParameterizedTest` / `@MethodSource` need `junit-jupiter-params` + `junit-jupiter-api`, which are NOT declared. This task ADDS them as `testImplementation` (the platform runner is already active via `useJUnitPlatform()`). No production-dependency change.
- **`build.gradle.kts` is edited DIRECTLY in the lane (PM-ratified, NOT a patch file) — verified zero-intersection with Plan 3.** Plan 3 Tasks 8–11 do not touch `build.gradle.kts`: their Files blocks are TS/TSX/YAML + Kotlin `src/` + `tools/deploy.sh` only — Task 8 (`docs/superpowers/plans/2026-06-12-plan3-lesson-engine.md:3772-3779`: `familyRegistry.ts`/`GraphTreeFamily.tsx`/its tests/`mergesortTrace.ts`/`viz-pa-mergesort-001.yaml`/`AlgoStepperShell.tsx`/`VizDemoPage.tsx`), Task 9 (`:4450-4455`: `assertNoClip.ts`/`lesson-beats.spec.ts`/`pa-kc-001-beats.json`/`family-no-clip.spec.ts`/`importBan.test.ts`), Task 10 (`:4905-4911`: `OggiScreen.tsx`/`main.tsx`/`BeatOrchestrator.tsx`/`LessonScreen.tsx`+test/`new-surfaces-smoke.spec.ts`), Task 11 (`:5183-5184`: `tools/deploy.sh`). A repo-wide grep of the Plan-3 plan for `build.gradle.kts` / `junit-jupiter` returns ZERO matches (verified 2026-06-12). So the direct edit collides with nothing in Lane B's branch-point or in Plan 3's later tasks; it is delivered as a normal lane commit, not the test.yml-style patch file. (If a future re-recon finds Plan 3 DID start touching `build.gradle.kts`, convert this to a patch-file deliverable like the `test.yml` Impeccable step.)
- The existing golden-like test `P3HonestyGraderSpotCheckTest` reads checked-in fixtures from `src/test/resources/fixtures/…` via `getResourceAsStream("/fixtures/…")` — the same classpath-resource convention this harness uses.

**Golden JSON shape (§0.9E, frozen for this harness):**

```json
{ "grader": "grade-scoring", "subject": "PA", "id": "pa-typical-3of5",
  "input": { "rubric": { "G1": true, "G2": true, "G3": true, "G4": false, "G5": false } },
  "expected": { "score": 0.6, "correct": false } }
```

**Files (ALL in the worktree `../jarvis-kotlin-lane-b`):**
- Modify: `build.gradle.kts` (add `junit-jupiter-params` + `junit-jupiter-api` testImplementation)
- Create: `src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt`
- Create (12 golden items): `src/test/resources/fixtures/grader-golden/{PA,ALO,PS}/grade-scoring/*.json`
- Create (dir-convention stubs for Plan 6, content-free): `src/test/resources/fixtures/grader-golden/_README.md`

- [ ] **Step 1: Add the JUnit 5 params dependency.** In `../jarvis-kotlin-lane-b/build.gradle.kts`, the test deps block ends with the ktor test deps.

Find:

```kotlin
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.0.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.0.1")
}
```

Replace with:

```kotlin
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.0.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    // Plan-4a Task 5 — JUnit 5 parameterized API for the grader-golden harness.
    // (kotlin("test") brings the platform runner via useJUnitPlatform(); these add @ParameterizedTest.)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
}
```

- [ ] **Step 2: Write the 12 golden fixtures.** Create the following files under `../jarvis-kotlin-lane-b/src/test/resources/fixtures/grader-golden/`. Each is a single JSON object in the frozen shape. The set spans typical, empty-rubric, all-false, single-item, and the all-or-nothing semantic (one false → not-correct) across PA/ALO/PS:

`PA/grade-scoring/pa-typical-3of5.json`:
```json
{ "grader": "grade-scoring", "subject": "PA", "id": "pa-typical-3of5", "input": { "rubric": { "G1": true, "G2": true, "G3": true, "G4": false, "G5": false } }, "expected": { "score": 0.6, "correct": false } }
```

`PA/grade-scoring/pa-all-pass.json`:
```json
{ "grader": "grade-scoring", "subject": "PA", "id": "pa-all-pass", "input": { "rubric": { "G1": true, "G2": true, "G3": true } }, "expected": { "score": 1.0, "correct": true } }
```

`PA/grade-scoring/pa-empty-rubric.json`:
```json
{ "grader": "grade-scoring", "subject": "PA", "id": "pa-empty-rubric", "input": { "rubric": {} }, "expected": { "score": 0.0, "correct": false } }
```

`PA/grade-scoring/pa-all-false.json`:
```json
{ "grader": "grade-scoring", "subject": "PA", "id": "pa-all-false", "input": { "rubric": { "G1": false, "G2": false, "G3": false, "G4": false } }, "expected": { "score": 0.0, "correct": false } }
```

`PA/grade-scoring/pa-single-pass.json`:
```json
{ "grader": "grade-scoring", "subject": "PA", "id": "pa-single-pass", "input": { "rubric": { "G1": true } }, "expected": { "score": 1.0, "correct": true } }
```

`ALO/grade-scoring/alo-single-fail.json`:
```json
{ "grader": "grade-scoring", "subject": "ALO", "id": "alo-single-fail", "input": { "rubric": { "G1": false } }, "expected": { "score": 0.0, "correct": false } }
```

`ALO/grade-scoring/alo-half-4of8.json`:
```json
{ "grader": "grade-scoring", "subject": "ALO", "id": "alo-half-4of8", "input": { "rubric": { "G1": true, "G2": true, "G3": true, "G4": true, "G5": false, "G6": false, "G7": false, "G8": false } }, "expected": { "score": 0.5, "correct": false } }
```

`ALO/grade-scoring/alo-one-short.json`:
```json
{ "grader": "grade-scoring", "subject": "ALO", "id": "alo-one-short", "input": { "rubric": { "G1": true, "G2": true, "G3": true, "G4": true, "G5": false } }, "expected": { "score": 0.8, "correct": false } }
```

`ALO/grade-scoring/alo-all-pass-2.json`:
```json
{ "grader": "grade-scoring", "subject": "ALO", "id": "alo-all-pass-2", "input": { "rubric": { "G1": true, "G2": true } }, "expected": { "score": 1.0, "correct": true } }
```

`PS/grade-scoring/ps-typical-2of3.json`:
```json
{ "grader": "grade-scoring", "subject": "PS", "id": "ps-typical-2of3", "input": { "rubric": { "G1": true, "G2": true, "G3": false } }, "expected": { "score": 0.6666666666666666, "correct": false } }
```

`PS/grade-scoring/ps-all-or-nothing-one-false.json`:
```json
{ "grader": "grade-scoring", "subject": "PS", "id": "ps-all-or-nothing-one-false", "input": { "rubric": { "G1": true, "G2": true, "G3": true, "G4": true, "G5": true, "G6": false } }, "expected": { "score": 0.8333333333333334, "correct": false } }
```

`PS/grade-scoring/ps-single-fail.json`:
```json
{ "grader": "grade-scoring", "subject": "PS", "id": "ps-single-fail", "input": { "rubric": { "G1": false } }, "expected": { "score": 0.0, "correct": false } }
```

> The two non-terminating decimals (`ps-typical-2of3` = 2/3, `ps-all-or-nothing-one-false` = 5/6) are written as the EXACT `Double` Kotlin produces (`2.0/3 == 0.6666666666666666`, `5.0/6 == 0.8333333333333334`). The harness parses these as `Double` and compares with a tiny epsilon (Step 3), so the literal must be the closest IEEE-754 double — these are. The `ps-all-or-nothing-one-false` item is the §0.9E all-or-nothing edge case: a high partial score (0.83) but `correct:false` because one criterion failed (the real `correctFromRubric` all-pass semantic; no separate all-or-nothing scorer exists).

- [ ] **Step 3: Write the harness test.** Create `../jarvis-kotlin-lane-b/src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt`:

```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import kotlin.test.assertEquals

/**
 * Plan-4a Task 5 (spec §9.2, §0.9E) — the grader-eval harness skeleton, DETERMINISTIC LEG ONLY.
 *
 * Loads golden JSON fixtures from src/test/resources/fixtures/grader-golden/{subject}/grade-scoring/,
 * runs each rubric through the REAL GradeScoring object (the LLM-independent leg that DECIDES the score
 * and correctness — the LLM's self-reported float is never trusted), and asserts score+correct match
 * the golden expected. JUnit 5 @ParameterizedTest over classpath resources; runs inside `:check`.
 *
 * SCOPE: only grader == "grade-scoring". LLM-judge + execution-grader golden sets are Plan 6; their
 * dir convention is reserved (see fixtures/grader-golden/_README.md) but no items ship here.
 *
 * The 12 golden items span PA/ALO/PS rubric shapes incl. the edge cases the real scorer branches on:
 * empty rubric (→ 0.0 / not-correct), all-false, single-item, and the all-or-nothing semantic
 * (one false ⇒ not-correct even at a high partial score — GradeScoring.correctFromRubric's all-pass rule).
 */
class GraderGoldenHarnessTest {

    @Serializable data class GoldenInput(val rubric: Map<String, Boolean>)
    @Serializable data class GoldenExpected(val score: Double, val correct: Boolean)
    @Serializable data class Golden(
        val grader: String,
        val subject: String,
        val id: String,
        val input: GoldenInput,
        val expected: GoldenExpected,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("graderGoldenCases")
    fun `GradeScoring matches the golden expected score and correctness`(name: String, g: Golden) {
        assertEquals("grade-scoring", g.grader, "this harness only runs grade-scoring goldens: ${g.id}")
        val actualScore = GradeScoring.scoreFromRubric(g.input.rubric)
        val actualCorrect = GradeScoring.correctFromRubric(g.input.rubric)
        assertEquals(g.expected.score, actualScore, 1e-9, "score mismatch for golden '${g.id}'")
        assertEquals(g.expected.correct, actualCorrect, "correct mismatch for golden '${g.id}'")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Resolve the fixtures dir off the compiled-test classpath (works in `gradle :check`). */
        private fun goldenRoot(): File {
            val url = requireNotNull(
                GraderGoldenHarnessTest::class.java.getResource("/fixtures/grader-golden"),
            ) { "fixtures/grader-golden missing from the test classpath — check src/test/resources" }
            // Gradle :test copies resources to build/resources/test (a file: URL); guard the
            // directory-walk against a future JAR-packaged classpath (jar: URL → File(uri) throws).
            // finding #9: explicit, actionable failure instead of a NoSuchFileException.
            require(url.protocol == "file") {
                "grader-golden fixtures must resolve to a file: URL for directory walking (got '${url.protocol}': $url). " +
                    "If resources moved into a JAR, switch to getResourceAsStream over a known id list."
            }
            return File(url.toURI())
        }

        @JvmStatic
        fun graderGoldenCases(): List<org.junit.jupiter.params.provider.Arguments> {
            val files = goldenRoot()
                .walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.parentFile.name == "grade-scoring" }
                .sortedBy { it.path }
                .toList()
            // Guard: the 12 goldens must actually be discovered (a glob/path regression would make this
            // suite silently 0-case green — fix-claim discipline: the harness must run real items).
            require(files.size >= 12) {
                "expected >= 12 grade-scoring golden fixtures, found ${files.size}: ${files.map { it.name }}"
            }
            return files.map { f ->
                val g = json.decodeFromString(Golden.serializer(), f.readText())
                org.junit.jupiter.params.provider.Arguments.of(g.id, g)
            }
        }
    }
}
```

- [ ] **Step 4: Write the reserved-dir README (Plan 6 convention, content-free).** Create `../jarvis-kotlin-lane-b/src/test/resources/fixtures/grader-golden/_README.md`:

```markdown
# grader-golden fixtures

Golden sets for the grader-eval harness (Plan-4a Task 5, spec §9.2 / §0.9E).

Layout: `fixtures/grader-golden/{subject}/{grader-type}/{id}.json`

Shipped now (Plan 4a — deterministic leg):
- `{PA,ALO,PS}/grade-scoring/*.json` — 12 items run by `GraderGoldenHarnessTest`
  against the real `GradeScoring` object.

Reserved for Plan 6 (NOT populated here — dir convention only):
- `{subject}/llm-judge/*.json` — LLM-judge golden sets (relay-graded leg)
- `{subject}/execution-grader/*.json` — execution/SymPy-grader golden sets

Each golden is one JSON object:
`{ "grader": "...", "subject": "...", "id": "...", "input": {...}, "expected": {...} }`
For `grade-scoring`, `input.rubric` is a `Map<String, Boolean>` and `expected` is
`{ "score": Double, "correct": Boolean }`. The harness compares score with epsilon 1e-9.
```

- [ ] **Step 5: Run the harness — expect GREEN (12 parameterized cases):**

```powershell
gradle --no-daemon -p ../jarvis-kotlin-lane-b :test --tests "jarvis.tutor.GraderGoldenHarnessTest"
```

Expected: `BUILD SUCCESSFUL`. The HTML report (`../jarvis-kotlin-lane-b/build/reports/tests/test/`) shows the parameterized method with 12 named invocations (`pa-typical-3of5`, `pa-all-pass`, …, `ps-single-fail`), all passed.

- [ ] **Step 6: Prove the harness REDS on a flipped expectation (seeded-violation, then REVERT).** Temporarily corrupt ONE golden's expected `correct`, run, confirm RED, restore. Use `pa-all-pass` (true → flip to false):

```powershell
# Flip pa-all-pass expected.correct from true to false (seeded violation).
(Get-Content ../jarvis-kotlin-lane-b/src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-all-pass.json) `
  -replace '"correct": true', '"correct": false' `
  | Set-Content ../jarvis-kotlin-lane-b/src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-all-pass.json
gradle --no-daemon -p ../jarvis-kotlin-lane-b :test --tests "jarvis.tutor.GraderGoldenHarnessTest"
```

Expected: `BUILD FAILED` — the `pa-all-pass` invocation fails with `correct mismatch for golden 'pa-all-pass'` (real scorer returns `true`, flipped golden expects `false`). **STOP and investigate if it stays GREEN** — a green here means the harness is not actually asserting against the goldens (fix-claim discipline: the gate must be able to fail).

Now REVERT the seeded violation:

```powershell
(Get-Content ../jarvis-kotlin-lane-b/src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-all-pass.json) `
  -replace '"correct": false', '"correct": true' `
  | Set-Content ../jarvis-kotlin-lane-b/src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-all-pass.json
```

Confirm the revert restored the exact original (no diff against the staged/committed-elsewhere content):

```powershell
git -C ../jarvis-kotlin-lane-b diff --stat -- src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-all-pass.json
```

Expected: empty output if already committed, OR (if not yet committed) the file shows only the original content — re-run the harness (Step 5) to confirm GREEN again before committing.

- [ ] **Step 7: Re-run the harness GREEN after revert** (never commit a tree you last saw red):

```powershell
gradle --no-daemon -p ../jarvis-kotlin-lane-b :test --tests "jarvis.tutor.GraderGoldenHarnessTest"
```

Expected: `BUILD SUCCESSFUL`, 12 cases passed.

- [ ] **Step 8: Confirm it runs inside `:check` automatically** (src/test placement + `useJUnitPlatform()` — no extra wiring needed; verify the harness is part of the full backend gate, but do NOT pipe through `| tail`):

```powershell
gradle --no-daemon -p ../jarvis-kotlin-lane-b :check
```

Expected: `BUILD SUCCESSFUL`. (The harness ran as part of `:test` which `:check` depends on; the report dir lists `GraderGoldenHarnessTest`.) **STOP and report** if `:check` is red — capture the failing test name; do not claim green on a partial read.

- [ ] **Step 9: Commit (explicit paths):**

```powershell
git -C ../jarvis-kotlin-lane-b add `
  build.gradle.kts `
  src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt `
  src/test/resources/fixtures/grader-golden
git -C ../jarvis-kotlin-lane-b commit -m @'
feat(plan4a): grader-golden harness (deterministic leg) + 12 goldens + seeded-red proof

- GraderGoldenHarnessTest: JUnit5 @ParameterizedTest over
  fixtures/grader-golden/{subject}/grade-scoring/*.json, asserts the REAL
  GradeScoring.scoreFromRubric/correctFromRubric vs each golden expected
- 12 goldens across PA/ALO/PS: typical, empty-rubric, all-false, single-item,
  all-or-nothing (one-false ⇒ not-correct at high partial score)
- >=12-case discovery guard (no silent 0-case green); seeded-red proof verified
  (flip pa-all-pass.correct ⇒ BUILD FAILED, reverted)
- build.gradle.kts: junit-jupiter-api/params testImplementation (@ParameterizedTest)
- runs inside :check automatically (src/test); _README reserves llm-judge /
  execution-grader dirs for Plan 6

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

Expected: one commit on `lane-b/plan4a` listing `build.gradle.kts`, the harness test, the 12 golden JSONs, and `_README.md`.

---

## Task 6 — Lane full-suite gate in the worktree + merge-prep manifest for the PM

Runs the COMPLETE gate inside the worktree (`gradle :check` + `npm test` + `npm run e2e:visual`, all from `../jarvis-kotlin-lane-b`) to prove the whole `lane-b/plan4a` branch is green end-to-end, then writes `build-review/tmp/lane-b-patches/MERGE-MANIFEST.md` — the single document the PM uses to merge Lane B into `main`. The manifest lists the branch, every commit, every changed file (`git diff --name-only main...lane-b/plan4a`), a pointer to the `test.yml` Impeccable patch file (Task 3), the carried follow-ups, and the explicit PM merge recipe. **This task does NOT merge** — it ends at "PM merges" (no self-merge; the two-lane contract reserves the merge for the PM after the gate).

**Files (ALL in / targeting the worktree `../jarvis-kotlin-lane-b`):**
- Create: `../jarvis-kotlin-lane-b/build-review/tmp/lane-b-patches/MERGE-MANIFEST.md`
- (Read-only) the Task 3 patch file `build-review/tmp/lane-b-patches/test-yml-impeccable.patch` (must already exist on the branch)

- [ ] **Step 1: Confirm the branch and that the working tree is clean** (every prior task committed; no stray edits). From the worktree:

```powershell
git -C ../jarvis-kotlin-lane-b rev-parse --abbrev-ref HEAD
git -C ../jarvis-kotlin-lane-b status --short
```

Expected: branch `lane-b/plan4a`. `git status --short` shows ONLY known-untracked artifacts (NOT any modified `src/` / `tutor-web/` source — those are committed). **STOP if** any tracked source file is modified/staged — a prior task left work uncommitted; finish it first. Do NOT `git add -A` / `git clean` (the worktree's `tutor-web/public/` carries untracked demo files, same as main).

- [ ] **Step 2: Run the FULL backend gate in the worktree** (never pipe through `| tail` — review-workflow rule):

```powershell
gradle --no-daemon -p ../jarvis-kotlin-lane-b :check
```

Expected: `BUILD SUCCESSFUL`. This includes `validateContent`, the new `GraderGoldenHarnessTest`, and every pre-existing backend test. **STOP and report the failing test name** if red.

- [ ] **Step 3: Run the frontend vitest suite in the worktree** (this is what CI's frontend job runs; includes the INV-9.5 `baselineScope.test.ts`):

```powershell
npm --prefix ../jarvis-kotlin-lane-b/tutor-web test
```

Expected: vitest passes (all suites, including `baselineScope.test.ts` and the existing `designMdSync` test from Task 2). **STOP and report** if red. (If "Cannot find module", run `npm --prefix ../jarvis-kotlin-lane-b/tutor-web ci` first — the worktree has its own `node_modules`.)

- [ ] **Step 4: Run the visual baselines in the worktree** (re-verify the 3 committed baselines still match a fresh render — env-lock makes this deterministic on the SAME OS the baselines were generated on):

```powershell
npm --prefix ../jarvis-kotlin-lane-b/tutor-web run e2e:visual
```

Expected: `3 passed` (shell + theme-dark + theme-light). **STOP and report** if a baseline drifts. (If the worktree host differs from the baseline-generation host, the platform suffix will mismatch — see the Task 4 platform-suffix note; record this in the manifest follow-ups rather than regenerating here.)

> Note: the standard e2e suite (`npm run e2e`) has a KNOWN pre-existing red (`tutor-shell-api-contract.spec.ts`, per Plan-2 §0.6 / Plan-4a §0) that is NOT Lane B's to fix. Run it ONLY if you want the full picture; the lane gate that matters for THIS plan's deliverables is `:check` + `npm test` + `e2e:visual`. Do not claim the pre-existing e2e red as a Lane B failure.

- [ ] **Step 5: Capture the branch's commit/changed-file list AND re-assert zero-intersection against Plan-3's POST-CUT commits (finding #3).** Task 0's intersection probe only saw Plan-3 work landed AT branch-cut; Plan 3 may have landed MORE commits to shared-ish files (`main.tsx`, `package.json`/`-lock`) between cut and now. This MERGE-time re-check is the second half of the two-lane contract. From the worktree:

```powershell
# (a) commit + changed-file lists for the manifest
git -C ../jarvis-kotlin-lane-b log --oneline main..lane-b/plan4a
git -C ../jarvis-kotlin-lane-b diff --name-only main...lane-b/plan4a

# (b) MERGE-time zero-intersection re-check: Lane B's files vs everything Plan 3 changed on
#     main SINCE the merge-base (this catches Plan-3 commits that landed AFTER the worktree cut).
$base    = git -C ../jarvis-kotlin-lane-b merge-base main lane-b/plan4a
$laneB   = git -C ../jarvis-kotlin-lane-b diff --name-only "$base" lane-b/plan4a
$plan3New = git -C ../jarvis-kotlin-lane-b diff --name-only "$base" main   # Plan-3 commits landed on main since the cut
# package.json / package-lock.json are the PM-merge-resolved shared baseline (Task 0) — exclude them.
$shared  = @('tutor-web/package.json','tutor-web/package-lock.json')
$collision = $laneB | Where-Object { $plan3New -contains $_ -and $shared -notcontains $_ }
if ($collision) {
  Write-Host "MERGE-TIME COLLISION — Lane B files also changed by Plan 3 since the branch-cut:"
  $collision | ForEach-Object { Write-Host "  $_" }
} else {
  Write-Host "OK — zero post-cut intersection (besides the PM-resolved package.json/-lock baseline)."
}
```

Expected: commit list = Tasks 1–5's commits (fonts, DESIGN.md autogen, Impeccable calibrate+filter+patch, visual baselines, grader harness); changed-file list = every file those commits touched; and the re-check prints `OK — zero post-cut intersection …`. **STOP if** `test.yml` appears in the changed-file list (Lane B must never edit it directly — it ships as a patch file) OR the re-check prints a `MERGE-TIME COLLISION` (a Plan-3 commit landed on a Lane-B file after the cut — report the path(s) to the PM verbatim; the conflicting file must be PM-merge-resolved before merge, exactly as Task 0's branch-cut probe demands). Save the outputs to paste into Step 7.

- [ ] **Step 6: Confirm the Impeccable CI patch file exists** (Task 3 produced it; the manifest points the PM at it):

```powershell
Get-Item ../jarvis-kotlin-lane-b/build-review/tmp/lane-b-patches/test-yml-impeccable.patch | Select-Object FullName, Length
git -C ../jarvis-kotlin-lane-b apply --check --3way build-review/tmp/lane-b-patches/test-yml-impeccable.patch
```

Expected: the patch file exists (non-zero length) and `git apply --check` reports nothing (the patch applies cleanly against the worktree's `.github/workflows/test.yml`). **STOP if** `git apply --check` errors — the patch is stale against the current `test.yml` and the PM cannot apply it; flag it in the manifest as needing a refresh before merge.

- [ ] **Step 7: Write the merge manifest.** Create `../jarvis-kotlin-lane-b/build-review/tmp/lane-b-patches/MERGE-MANIFEST.md`. Fill the `<from Step 5>` placeholders with the ACTUAL captured output (do not leave them literal):

```markdown
# Lane B (plan4a) — MERGE MANIFEST (for the PM)

> Lane B = the dependency-free half of spec §9.2 gates 5–6 + §9.4 adopt-list, built in the
> dedicated worktree `../jarvis-kotlin-lane-b` on branch `lane-b/plan4a`, isolated from Plan 3
> (which executed in the main tree). **Lane B did NOT merge.** This manifest is the PM's merge recipe.

## Branch
- Branch: `lane-b/plan4a`
- Base: `main` (worktree created from main HEAD at Plan-4a Task 0)
- Worktree: `../jarvis-kotlin-lane-b`

## Commits (git log --oneline main..lane-b/plan4a)
```
<from Step 5: the commit list, newest first>
```

## Changed files (git diff --name-only main...lane-b/plan4a)
```
<from Step 5: the full changed-file list>
```
**Zero-intersection check:** none of the above is a Plan-3 Tasks 8–11 file (re-verified at MERGE time
against Plan-3's post-cut commits, Step 5(b)). `build.gradle.kts` IS in this list — it is a PM-ratified
DIRECT lane edit (Plan 3 8–11 verifiably do not touch it, Task 5 recon). `.github/workflows/test.yml`
is NOT in this list (it ships as a patch — see below).

## Lane gate result (run in the worktree, this task)
- `gradle --no-daemon -p ../jarvis-kotlin-lane-b :check` → BUILD SUCCESSFUL (incl. GraderGoldenHarnessTest)
- `npm --prefix ../jarvis-kotlin-lane-b/tutor-web test` → vitest GREEN (incl. INV-9.5 baselineScope, designMdSync)
- `npm --prefix ../jarvis-kotlin-lane-b/tutor-web run e2e:visual` → 3 passed (shell + theme-dark + theme-light baselines)
- (`npm run e2e` standard suite has a PRE-EXISTING red `tutor-shell-api-contract.spec.ts` — NOT Lane B's; Plan-2 §0.6)

## CI patch the PM must apply (the ONE shared file)
- File: `build-review/tmp/lane-b-patches/test-yml-impeccable.patch`
- Adds the Impeccable fail-open step to the frontend job. Canonical run-line (identical in the patch,
  §0.9C, and here — finding #7), no `--config` (the flag does not exist; subset = post-process filter):
  `npx --yes impeccable@2.3.2 detect --json src/ | node ../tools/impeccable-filter.mjs || true`
  (frontend job `working-directory: tutor-web`, so `src/` = tutor-web/src/, `../tools/` = repo-root tools/;
  fail-open `|| true` until the unlock below).
- Subset deliverables on the branch: `tools/impeccable-rules.json` (the `{"enabled":[…],"disabled":{…}}`
  antipattern allow-list) + `tools/impeccable-filter.mjs` (the stdin filter) + the UNFILTERED calibrate
  output `build-review/impeccable-calibration-2026-06-12.json`. There is NO `impeccable.config.json`.
- Verified `git apply --check` clean against current `test.yml` at manifest time.

## Carried follow-ups (NOT done in Lane B — explicit hand-off)
1. **theme.ts ⇄ DESIGN.md drift (recon §0 #3):** `tutor-web/src/components/viz/theme.ts` hardcodes
   INK `#0a0a0a` / PAPER `#f5f5f0` vs DESIGN.md `#000000` / `#ffffff`. NOT fixed here — changing theme.ts
   alters viz rendering (needs Plan-4b rendered gates + a fresh visual baseline). Carry as a Plan-4b item.
2. **Impeccable promote-to-blocking unlock:** the fail-open step becomes BLOCKING only when BOTH are true:
   (a) the applicable-subset is CALIBRATED (the post-process allow-list `tools/impeccable-rules.json`,
   derived from the real calibrate run committed UNFILTERED at `build-review/impeccable-calibration-2026-06-12.json`
   and applied by `tools/impeccable-filter.mjs`), AND (b) the version is PINNED `impeccable@2.3.2` in the
   CI command. Both ship in Lane B (Task 3). To flip to blocking, drop the `|| true` (the filter already
   exits 1 when ≥1 enabled-antipattern finding survives) — a deliberate PM step AFTER a green run on real
   PRs; do NOT remove `|| true` at merge. **Note: there is NO `impeccable.config.json` / `--config` flag**
   (the real CLI has neither — finding #1); the subset is the `| node ../tools/impeccable-filter.mjs` stage.
3. **DESIGN.md YAML front-matter drift (finding #5):** the Task-2 autogen gate closes drift only between
   the new AUTOGEN table and `index.css`. The DESIGN.md **YAML front-matter** (DESIGN.md:1-98, the
   line-164 "Source of truth: the YAML front-matter … Mirrored into index.css @theme" rule) is left as the
   human source of truth (PM ruling — additive only) and is NOT regenerated or gated. The YAML ⇄ index.css
   drift remains a hand-maintained mirror; closing it (regenerate YAML token values from index.css and gate
   that) is a separate follow-up.
4. **Visual baseline platform suffix:** the 3 baselines generated on Lane B's host carry an OS suffix
   (`-win32` / `-linux` / `-darwin`). If they were generated on a different OS than CI's `ubuntu-latest`,
   the CI visual run would fail "missing snapshot for linux". This plan adds NO `test.yml` wiring for the
   visual run (same fail-open posture as Impeccable); the INV-9.5 vitest IS in `npm test` and is
   cross-platform (path inspection, not pixels). Before the PM turns the visual run blocking, regenerate
   the 3 baselines (shell + theme-dark + theme-light) on a Linux host (or pin the visual job to one OS) and
   re-commit them.
5. **Self-hosted fonts (Task 1) — DoorWarm:** Fraunces/Nunito woff2 (gwfh static instances) were
   self-hosted and DoorWarm's CDN `<link>` injection removed. Fraunces/Nunito are needed at runtime ONLY if
   `DoorWarm.tsx` (a demo component) is RETAINED; if the door demos are dropped, those two font families can
   be removed from `public/fonts/` (JetBrains Mono stays — it is in the production viz/theme stacks AND the
   theme-ref DARK/LIGHT baselines). Decide at merge.

## PM merge recipe (run from the MAIN repo `C:\Users\User\jarvis-kotlin`)
> Do these in order. The full gate runs AGAIN on the merged main tree — a green lane gate is necessary,
> not sufficient (cross-lane interactions surface only post-merge).

1. Merge the branch (no fast-forward, preserve the lane history):
   `git -C C:\Users\User\jarvis-kotlin merge --no-ff lane-b/plan4a`
2. Apply the Impeccable CI patch (the one shared file Lane B never edited directly):
   `git -C C:\Users\User\jarvis-kotlin apply --3way build-review/tmp/lane-b-patches/test-yml-impeccable.patch`
   then stage + commit it: `git -C C:\Users\User\jarvis-kotlin add .github/workflows/test.yml && git -C C:\Users\User\jarvis-kotlin commit -m "ci(plan4a): impeccable fail-open frontend step (Lane B patch)"`
3. Run the FULL gate on merged main (never pipe through | tail):
   - `gradle --no-daemon :check` → BUILD SUCCESSFUL
   - `npm --prefix tutor-web test` → vitest GREEN
   - `npm --prefix tutor-web run e2e:visual` → 3 passed (regenerate the 3 baselines on this host first if
     the OS differs from where Lane B generated them — follow-up #4)
4. If all green, push: `git -C C:\Users\User\jarvis-kotlin push origin main`
5. Do NOT remove the Impeccable `|| true` fail-open yet (follow-up #2 unlock conditions).

**PM merges. Lane B stops here.**
```

- [ ] **Step 8: Commit the manifest (explicit path):**

```powershell
git -C ../jarvis-kotlin-lane-b add build-review/tmp/lane-b-patches/MERGE-MANIFEST.md
git -C ../jarvis-kotlin-lane-b commit -m @'
docs(plan4a): Lane B merge manifest — commits, changed files, CI patch, follow-ups, PM recipe

Lane gate green in the worktree (:check + npm test + e2e:visual = 3 baselines).
Manifest hands the merge to the PM: branch/commit/changed-file lists (+ MERGE-time
re-check vs Plan-3 post-cut commits), the test-yml Impeccable patch pointer (subset
= post-process filter, no --config), carried follow-ups (theme.ts drift; Impeccable
blocking-unlock; DESIGN.md YAML front-matter drift; visual baseline platform suffix;
Fraunces/Nunito only if DoorWarm retained), and the no-ff merge + patch-apply +
full-gate + push recipe. NO self-merge — PM merges.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

Expected: one commit on `lane-b/plan4a` adding `MERGE-MANIFEST.md`. This is Lane B's final commit; the branch is ready for the PM merge.

- [ ] **Step 9: Final lane-state report (no commit) — confirm the branch is merge-ready:**

```powershell
git -C ../jarvis-kotlin-lane-b log --oneline main..lane-b/plan4a
git -C ../jarvis-kotlin-lane-b status --short
```

Expected: the commit list now includes the manifest commit; `git status --short` shows only known-untracked artifacts. Report to the PM: "Lane B green (`:check` + `npm test` + `e2e:visual`), manifest at `build-review/tmp/lane-b-patches/MERGE-MANIFEST.md`, ready to merge." **Lane B does not merge — the PM does.**

