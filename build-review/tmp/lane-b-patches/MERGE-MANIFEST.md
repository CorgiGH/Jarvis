# Lane B (plan4a) — MERGE MANIFEST (for the PM)

> Lane B = the dependency-free half of spec §9.2 gates 5–6 + §9.4 adopt-list, built in the
> dedicated worktree `../jarvis-kotlin-lane-b` on branch `lane-b/plan4a`, isolated from Plan 3
> (which executed in the main tree). **Lane B did NOT merge.** This manifest is the PM's merge recipe.

## Branch
- Branch: `lane-b/plan4a`
- Base: `main` (worktree created from main HEAD at Plan-4a Task 0; merge-base = b3aa963066be1f8d65e3deab4f0c95a53997f812)
- Worktree: `../jarvis-kotlin-lane-b`

## Commits (git log --oneline main..lane-b/plan4a)
```
42a3b3b fix(plan4a): regenerate impeccable CI patch with proper CRLF-aware unified diff (git apply --check passes)
9892ceb feat(plan4a): grader-golden harness (deterministic leg) + 12 goldens + seeded-red proof
3a26958 fix(plan4a): self-contained themeRefHarness (zero src/door/* imports) + regenerate DARK baseline
f1df996 (review fix) Task 4: restore import-based themeRefHarness per plan canonical block; regenerate baselines
ace9255 (review fix) Task 4: remove unauthorized ./door/* files; inline logic into themeRefHarness.tsx
483cf39 feat(plan4a): env-locked visual baselines (shell + 2 theme reference pages) + INV-9.5 scope gate
c348989 feat(tooling): Impeccable calibrate + post-process subset filter + fail-open CI patch file (Plan 4a §0.9C; PM applies patch at merge)
5b1e1e7 test(design): add multi-block :root coverage — enforce ALL-blocks blockBody() contract (review fix)
9e2ce1a feat(design): autogen DESIGN.md token table from index.css + drift gate (Plan 4a §0.9B)
cc36118 feat(fonts): self-host JetBrains Mono/Fraunces/Nunito woff2 (gwfh static); drop DoorWarm CDN injection (Plan 4a §0.9A)
```

## Changed files (git diff --name-only main...lane-b/plan4a)
```
DESIGN.md
build-review/impeccable-calibration-2026-06-12.json
build-review/tmp/lane-b-patches/test-yml-impeccable.patch
build.gradle.kts
src/test/kotlin/jarvis/tutor/GraderGoldenHarnessTest.kt
src/test/resources/fixtures/grader-golden/ALO/grade-scoring/alo-all-pass-2.json
src/test/resources/fixtures/grader-golden/ALO/grade-scoring/alo-half-4of8.json
src/test/resources/fixtures/grader-golden/ALO/grade-scoring/alo-one-short.json
src/test/resources/fixtures/grader-golden/ALO/grade-scoring/alo-single-fail.json
src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-all-false.json
src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-all-pass.json
src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-empty-rubric.json
src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-single-pass.json
src/test/resources/fixtures/grader-golden/PA/grade-scoring/pa-typical-3of5.json
src/test/resources/fixtures/grader-golden/PS/grade-scoring/ps-all-or-nothing-one-false.json
src/test/resources/fixtures/grader-golden/PS/grade-scoring/ps-single-fail.json
src/test/resources/fixtures/grader-golden/PS/grade-scoring/ps-typical-2of3.json
src/test/resources/fixtures/grader-golden/_README.md
tools/generate-design-md.mjs
tools/generate-design-md.test.mjs
tools/impeccable-filter.mjs
tools/impeccable-rules.json
tutor-web/e2e/visual/__screenshots__/shell.visual.spec.ts/shell.png
tutor-web/e2e/visual/__screenshots__/theme-dark.visual.spec.ts/theme-dark.png
tutor-web/e2e/visual/__screenshots__/theme-light.visual.spec.ts/theme-light.png
tutor-web/e2e/visual/shell.visual.spec.ts
tutor-web/e2e/visual/theme-dark.visual.spec.ts
tutor-web/e2e/visual/theme-light.visual.spec.ts
tutor-web/package.json
tutor-web/playwright.visual.config.ts
tutor-web/public/fonts/Fraunces-400.woff2
tutor-web/public/fonts/Fraunces-600.woff2
tutor-web/public/fonts/Fraunces-600italic.woff2
tutor-web/public/fonts/Fraunces-900.woff2
tutor-web/public/fonts/JetBrainsMono-400.woff2
tutor-web/public/fonts/JetBrainsMono-700.woff2
tutor-web/public/fonts/Nunito-400.woff2
tutor-web/public/fonts/Nunito-600.woff2
tutor-web/public/fonts/Nunito-700.woff2
tutor-web/public/fonts/Nunito-800.woff2
tutor-web/public/fonts/Nunito-900.woff2
tutor-web/src/__tests__/baselineScope.test.ts
tutor-web/src/__tests__/designMdSync.test.ts
tutor-web/src/__tests__/fontLoading.test.ts
tutor-web/src/door/DoorWarm.tsx
tutor-web/src/index.css
tutor-web/src/themeRefHarness.tsx
tutor-web/theme-ref.html
```

**Zero-intersection check (MERGE-time):** none of the above is a Plan-3 Tasks 8–11 file
(re-verified at MERGE time against Plan-3's post-cut commits on main, 2026-06-12 Task 6).
`build.gradle.kts` IS in this list — it is a PM-ratified DIRECT lane edit (Plan 3 8–11
verifiably do not touch it, Task 5 recon). `.github/workflows/test.yml` is NOT in this
list (it ships as a patch — see below). Zero post-cut intersection confirmed.

**PM RULING (2026-06-12) applied:** `themeRefHarness.tsx` is SELF-CONTAINED — zero imports
from `src/door/DoorBrutalist|concept|figures`. Committed-state proof: moved those 3
untracked files aside → `npm run e2e:visual` → 3/3 passed → files restored. Commit 3a26958.

## Lane gate result (run in the worktree, 2026-06-12)
- `gradle --no-daemon -p ../jarvis-kotlin-lane-b :check` → BUILD SUCCESSFUL (incl. GraderGoldenHarnessTest 12 cases, validateContent 0 errors)
- `npm --prefix ../jarvis-kotlin-lane-b/tutor-web test` → vitest GREEN (171 files, 876 tests incl. INV-9.5 baselineScope, designMdSync, fontLoading)
- `npm --prefix ../jarvis-kotlin-lane-b/tutor-web run e2e:visual` → 3 passed (shell + theme-dark + theme-light baselines)
- (`npm run e2e` standard suite has a PRE-EXISTING red `tutor-shell-api-contract.spec.ts` — NOT Lane B's; Plan-2 §0.6)

## CI patch the PM must apply (the ONE shared file)
- File: `build-review/tmp/lane-b-patches/test-yml-impeccable.patch`
- Adds the Impeccable fail-open step to the frontend job. Canonical run-line (identical in
  the patch, §0.9C, and here — finding #7), no `--config` (the flag does not exist; subset
  = post-process filter):
  `npx --yes impeccable@2.3.2 detect --json src/ | node ../tools/impeccable-filter.mjs || true`
  (frontend job `working-directory: tutor-web`, so `src/` = tutor-web/src/, `../tools/` = repo-root
  tools/; fail-open `|| true` until the unlock below).
- Subset deliverables on the branch: `tools/impeccable-rules.json` (the `{"enabled":[…],"disabled":{…}}`
  antipattern allow-list) + `tools/impeccable-filter.mjs` (the stdin filter) + the UNFILTERED calibrate
  output `build-review/impeccable-calibration-2026-06-12.json`. There is NO `impeccable.config.json`.
- Verified `git apply --check --recount` clean against current `test.yml` at manifest time.
- Note: patch uses git's full-diff format (with index/hash header); PM applies with `git apply --3way`.

## Carried follow-ups (NOT done in Lane B — explicit hand-off)
1. **theme.ts ⇄ DESIGN.md drift (recon §0 #3):** `tutor-web/src/components/viz/theme.ts` hardcodes
   INK `#0a0a0a` / PAPER `#f5f5f0` vs DESIGN.md `#000000` / `#ffffff`. NOT fixed here — changing theme.ts
   alters viz rendering (needs Plan-4b rendered gates + a fresh visual baseline). Carry as a Plan-4b item.
2. **Impeccable promote-to-blocking unlock:** the fail-open step becomes BLOCKING only when BOTH are true:
   (a) the applicable-subset is CALIBRATED (the post-process allow-list `tools/impeccable-rules.json`,
   derived from the real calibrate run committed UNFILTERED at `build-review/impeccable-calibration-2026-06-12.json`
   and applied by `tools/impeccable-filter.mjs`), AND (b) the version is PINNED `impeccable@2.3.2` in the
   CI command. Both ship in Lane B (Task 3). To flip to blocking, drop the `|| true` (the filter already
   exits 1 when >=1 enabled-antipattern finding survives) — a deliberate PM step AFTER a green run on real
   PRs; do NOT remove `|| true` at merge. **Note: there is NO `impeccable.config.json` / `--config` flag**
   (the real CLI has neither — finding #1); the subset is the `| node ../tools/impeccable-filter.mjs` stage.
3. **DESIGN.md YAML front-matter drift (finding #5):** the Task-2 autogen gate closes drift only between
   the new AUTOGEN table and `index.css`. The DESIGN.md **YAML front-matter** (DESIGN.md:1-98, the
   line-164 "Source of truth: the YAML front-matter … Mirrored into index.css @theme" rule) is left as the
   human source of truth (PM ruling — additive only) and is NOT regenerated or gated. The YAML ⇄ index.css
   drift remains a hand-maintained mirror; closing it is a separate follow-up.
4. **Visual baseline platform suffix:** the 3 baselines generated on Lane B's host (Windows) carry NO
   platform suffix (snapshotPathTemplate has no `{platform}` token — PM RULING 2026-06-12). However,
   Playwright's PIXEL rendering may differ slightly between Windows and Linux. If CI runs on ubuntu-latest
   and the baselines were generated on Windows, the visual run MAY fail with pixel diffs. This plan adds NO
   `test.yml` wiring for the visual run (same fail-open posture as Impeccable); the INV-9.5 vitest IS in
   `npm test` and is cross-platform (path inspection, not pixels). Before the PM turns the visual run
   blocking, regenerate the 3 baselines (shell + theme-dark + theme-light) on the CI OS (Linux) and
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
   - `npm --prefix tutor-web run e2e:visual` → 3 passed (regenerate the 3 baselines on the CI OS
     (Linux) first if cross-platform pixels differ — follow-up #4)
4. If all green, push: `git -C C:\Users\User\jarvis-kotlin push origin main`
5. Do NOT remove the Impeccable `|| true` fail-open yet (follow-up #2 unlock conditions).

**PM merges. Lane B stops here.**
