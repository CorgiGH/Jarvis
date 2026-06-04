# Stage-0 Exit Gate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive the `main` branch to a fully-green, pushed, CI-passing, backup-protected state — the clean foundation Phase-1 data-model work (cull + migrations + backfill) builds on. This gate ships **no new features**: it fixes the failing frontend specs, **proves CI green on a pushed SCRATCH branch BEFORE touching `main`** (local green is the pre-condition, scratch-branch CI green is the gate — see council note below), repairs the vacuous e2e gate so it actually exercises the app, and arms the off-box backup precondition (proven-failable) that guards the very first schema ALTER.

**Why Stage-0 is THE gate before Phase 1:** Phase 1 is the keystone — it ALTERs the live 871-card SQLite DB (cull → `ADD COLUMN` → explicit backfill UPDATE). Exposed's `.default()` is client-side only, `createMissingTablesAndColumns` emits no SQL `DEFAULT`, and SQLite ALTER is **not transactional** — a botched migration over an un-backed-up corpus is unrecoverable (master-impl-plan-v2 §2.1, M-PARTIAL). So Phase 1 must NOT start until: (1) the tree is green so a migration regression is detectable against a known-good baseline, (2) `main` == `origin/main` so the keystone commits land on a synced, CI-verified base, and (3) `tools/db-backup.py` enforces `MIN_EXPECTED_CARDS=800` so the schema is never ALTERed before a verified off-box dump (M-DB). This is the B5 deliverable named in the v2 master plan's Phase-0 row.

**Source contract:** `docs/superpowers/plans/2026-06-02-master-impl-plan-v2.md` — Phase-0 row (~line 155), migration-discipline ¶ (~line 42), contracts **B5 / M-DB / M-IDEMP / M-PARTIAL**.

> **Council review (`council-1780431232-phase0b-execute-gate`, verdict FLAWED — folded in):** an adversarial council ruled the *phase* correct but the gate's **definition of "green" invalid two ways**: (1) the e2e gate `door-real-backend.spec.ts` does `goto('/')` which lands OFF the `/tutor` basename → renders a routeless near-empty page, never mounts `App`, never fires `/api`, yet asserts "zero 4xx/5xx" → a **vacuous false-green** that passes with NO backend (a gate never seen red is indistinguishable from no gate); and (2) **local-green is the wrong exit criterion** — `:check`+`validateContent` has never run on a pushed commit and the green-making workflow itself lives inside the 10 unpushed commits, so the **FIRST push is the real test**. Per the verdict, the **exit criterion is tightened from "all green locally" → "CI green on a pushed SCRATCH branch AND every gate demonstrated capable of failing once."** The two structural fixes — make the e2e gate REAL (Task 2 Step 2) and push a scratch branch before `main` (new Task 4) — are folded into the tasks below; the .npmrc fix, the 9-spec fixture fix, and the backup floor are kept and strengthened (the backup floor must be *proven-failable*, Task 3).

---

## Ground Truth (discovered, verified — not parroted)

| Fact | Verified value | How verified |
|---|---|---|
| Branch / HEAD | `main`, HEAD `a2dd257`, **ahead of `origin/main` by 10** (unpushed) | `git rev-list --count origin/main..main` → `10`; `git status -sb` |
| 4 Stage-0 viz fixes | **DONE on `main`** — `6192b35` RecursionTree, `666067d` PageTableWalk, `b070d1a` TcpCwnd, `2854fdb` NumLineDirect | `git log --oneline`; `git branch --contains <sha>` → `main` for first & last |
| Failing frontend specs | **9 tests across 2 files in the FULL run** (full suite: 9 failed / 558 passed / 567 total) | `npm test` in `tutor-web` |
| — file 1 (full run) | `src/__tests__/App.test.tsx` — **7 fail** in the full run (out of 9 tests in the file) | `npm test` (full-suite FAIL list shows 7 App.test rows) |
| — file 1 (isolation) | `src/__tests__/App.test.tsx` — **8 fail** in isolation (the 8th, `review nav pill has aria-current=page when on /review`, FLAKES: fails alone, passes in the full run) | `npx vitest run src/__tests__/App.test.tsx` → 8 failed / 1 passed |
| — file 2 | `src/__tests__/crossDeviceSync.test.tsx` — **2 of 2 fail, rock-solid in BOTH isolation and the full run** (NOT the flaker) | `npx vitest run …/crossDeviceSync.test.tsx` → 2 failed; full run → 2 failed |
| Root cause | **NOT DOMMatrix/react-pdf** — `react-pdf` is already mocked in `setupTests.ts`. Real cause: the `/welcome/ai-literacy` gate (`App.tsx:97-103`) redirects because the test fetch stubs don't return `aiLiteracyConfirmed:true` from `/api/v1/me/export` | read `App.tsx:36-111`, `setupTests.ts`, both test files; observed `No routes matched location "/welcome/ai-literacy"` in stderr |
| Flaky count + source | full run = **9** (App.test 7 + crossDeviceSync 2); the two files run in isolation = **10** (App.test 8 + crossDeviceSync 2). The +1 drift is in **App.test.tsx** (`review nav pill has aria-current=page`), NOT crossDeviceSync — jsdom effect timing on the same redirect bug. The deterministic stub greens all of them. | isolation runs + full run, compared |
| **CI is RED on `main` RIGHT NOW** | last **5** push runs on `main` = **failure**; the failing job is **frontend**, dying at **`npm ci`** (ERESOLVE), BEFORE vitest ever runs | `gh run list --branch main` (5× failure); `gh run view 26670694533` → `X npm ci`, `vitest run` shown as `-` (skipped) |
| **`npm ci` peer-dep conflict** | `@visx/group@3.12.0` peer-requires react `^16‖^17‖^18`; project is `react@^19.0.0` → ERESOLVE. There is **NO `.npmrc`** anywhere (root or `tutor-web/`) and the workflow's `npm ci` (line 65) has **no `--legacy-peer-deps`** → the frontend job cannot install deps | `npm ci --dry-run` → `ERESOLVE`; `glob .npmrc` + `glob tutor-web/.npmrc` → 0 files; read workflow line 65 |
| **Backend CI job-name/command DRIFT** | last pushed run shows `backend (gradle :test)`; the workflow at **current HEAD** runs `backend (gradle check)` → `gradle --no-daemon :check` (incl. `validateContent`). `:check` has **never run in CI on this repo** — it's inside the 10 unpushed commits | `gh run view 26670694533` job name `backend (gradle :test)` vs `.github/workflows/test.yml:16,29` `gradle … :check` |
| **e2e gate is a VACUOUS false-green (council, load-bearing)** | `e2e/door-real-backend.spec.ts:7` does `page.goto('/')`; playwright `baseURL: http://localhost:5173` (playwright.config.ts:6) → it lands at `http://localhost:5173/` which is **OFF** the `/tutor` base. The SPA mounts under `<BrowserRouter basename="/tutor">` (main.tsx:15) and vite serves it at `/tutor/` (vite.config.ts:8 `base: "/tutor/"`). So `/` paints a **routeless near-empty page** — `App` never mounts, no `/api` call fires — yet the spec asserts "zero 4xx/5xx" and **passes with NO backend on :8080** (CI's frontend job starts none). It certifies NOTHING and has never been seen to fail. The "real `/api` with no stubs" framing was WRONG: nothing under `/` ever reaches `/api`. | read `e2e/door-real-backend.spec.ts:4-11`, `playwright.config.ts:6,11-15` (baseURL :5173, `npm run dev`), `main.tsx:15` (`basename="/tutor"`), `vite.config.ts:8,16-22` (`base:"/tutor/"`, proxy `/api`→:8080) |
| **e2e webServer** | `playwright.config.ts` `webServer.command: npm run dev` + `reuseExistingServer: true` → Playwright boots Vite itself (no separate "build the SPA" step). `vite.config.ts` proxies `/api`→`:8080` + `/auth`→`:8080` — but only matters once the test actually navigates under `/tutor` (see fix in Task 2 Step 2). | read `playwright.config.ts`, `vite.config.ts` |
| `tools/db-backup.py` | **EXISTS**; counts `fsrs_cards` + restore-integrity-checks to `:memory:`. **Has NO `MIN_EXPECTED_CARDS` guard** — that precondition must be ADDED | read whole file; `grep MIN_EXPECTED_CARDS` → 0 hits in `tools/` |
| Live DB | `~/.jarvis/tutor.db` exists, **871 `fsrs_cards`** → `MIN_EXPECTED_CARDS=800` PASSES today | Python `sqlite3` ro-count (see blocker below) |
| `sqlite3` CLI | **NOT on PATH** — use Python `sqlite3` (db-backup.py already does) | `command -v sqlite3` → empty |
| CI | `.github/workflows/test.yml` — push-to-`main` runs 3 jobs: **backend** `gradle :check` (incl. `validateContent`), **frontend** `npm test` + `npm run e2e` (Playwright chromium), **daemon** `cargo test` | read workflow |
| Toolchain | `gradlew` + `gradlew.bat` present; `npm test` = `vitest run`, `npm run e2e` = `playwright test` | `ls`, `package.json` |

> **Consequence (CORRECTED — plan-invalidating finding):** Greening the 9 specs is necessary but **NOT sufficient** to make the frontend CI job pass. CI on `main` is **already red** and the frontend job dies at **`npm ci`** (ERESOLVE peer conflict, react@19 vs `@visx/group` peer ^16‖^17‖^18) — **before `vitest run` ever executes**. So `npm test` green locally does nothing for CI until the dependency-resolution failure is fixed (Task 0). The original premise — "the 9 reds would fail the frontend CI job; push gated on green" — is mechanically wrong: the job never reaches vitest. **Task 0 (below) is now the first prerequisite**; E7 (CI green) is contingent on it. Also: the **backend** job at current HEAD runs `:check` (not the `:test` the last pushed run ran), so its green-ness is *unproven* and must be verified locally (Task 2 Step 3 / E3) before relying on it.

---

## Run commands

- Frontend install (the CI step that's RED): from `tutor-web/`, `npm ci` (currently ERESOLVE — see Task 0; `npm ci --dry-run` reproduces without mutating)
- Frontend one file: from `tutor-web/`, `npx vitest run src/__tests__/<file>`
- Frontend all: from `tutor-web/`, `npm test`
- Frontend e2e: from `tutor-web/`, `npm run e2e` (Playwright boots Vite via `webServer: npm run dev`). ⚠️ The current spec is VACUOUS — `goto('/')` lands off the `/tutor` base, never mounts `App`, never hits `/api` (Task 2 Step 2 repairs it to navigate under `/tutor`).
- Backend: from repo root, `gradlew.bat :check` (Unix: `./gradlew :check`) — CI runs `gradle --no-daemon :check`, incl. `validateContent`
- Backup: from repo root, `python tools/db-backup.py ./backups`

---

## Task 0 — Fix frontend dependency resolution so `npm ci` succeeds in CI ⚠️ PREREQUISITE

**Why this is Task 0:** the frontend CI job fails at **`npm ci`** (workflow line 65), BEFORE `vitest run`. Reproduced locally (`npm ci --dry-run` → `ERESOLVE`) and confirmed in the last 5 main push runs (e.g. `gh run view 26670694533` → `X npm ci`, vitest step `-`/skipped). `@visx/group@3.12.0` peer-requires react `^16‖^17‖^18`; the project pins `react@^19.0.0` (and the lockfile resolves `react@19.2.6`). There is no `.npmrc` and `npm ci` has no `--legacy-peer-deps`, so install aborts. **Until this is fixed, Tasks 1/2 (greening vitest) cannot make the frontend CI job green and E7 is unachievable.**

**Decision — pick ONE, lowest-risk first:**
- **Option A (preferred): commit a `tutor-web/.npmrc` with `legacy-peer-deps=true`.** One file, no workflow edit, applies to both `npm ci` and `npm install` locally + in CI. This is the same posture the local `node_modules` was already installed under (the tree works at runtime; only strict `ci` peer-resolution rejects it).
- **Option B: change the workflow** `npm ci` step (line 65) to `npm ci --legacy-peer-deps`. Equivalent effect but only in CI — local `npm ci` would still fail, so A is preferred for parity.
- **Option C (heaviest): replace/upgrade the offending dep.** `@visx/*` has no react-19 peer release as pinned; migrating the viz that uses `@visx/group`/`hierarchy`/`network`/`shape` off visx (e.g. to the d3 primitives already in `dependencies`) is a real code change with regression surface. Out of scope for Stage-0 (no new features); only consider if A/B are rejected.

> **Note:** A/B make `npm ci` *accept* the existing (working) tree; they do NOT silence a real runtime incompatibility — the app already builds and 558 specs pass against this exact `node_modules`. This is npm's strict-peer gate, not a broken install.

- [ ] **Step 1: Reproduce the install failure**

  Run (from `tutor-web/`): `npm ci --dry-run`
  Expected: `npm error code ERESOLVE` naming `@visx/group@3.12.0` peer `react@"^16…||^17…||^18…"` vs found `react@19.x`. This is the exact failure CI hits.

- [ ] **Step 2: Apply Option A**

  Create `tutor-web/.npmrc`:
  ```
  legacy-peer-deps=true
  ```

- [ ] **Step 3: Prove `npm ci` now succeeds**

  Run (from `tutor-web/`): `npm ci`
  Expected: clean install, exit 0 (no ERESOLVE). This is the CI step that was dying.

- [ ] **Step 4: Re-confirm the suite still runs on the freshly-installed tree**

  Run (from `tutor-web/`): `npm test`
  Expected: same baseline as before (9 failed / 558 passed) — Task 0 only fixes install, not the specs. (Tasks 1 greens the 9.)

- [ ] **Step 5: Commit**
  ```bash
  git add tutor-web/.npmrc
  git commit -m "build(stage0): tutor-web/.npmrc legacy-peer-deps=true so npm ci installs under react@19 (visx peer is ^16|^17|^18) — unblocks the frontend CI job"
  ```

**Acceptance:** `npm ci` exits 0 from a clean state in `tutor-web/`; `.npmrc` (or workflow `--legacy-peer-deps`) committed; the frontend CI job will now reach `vitest run` instead of dying at install. **E7 is contingent on this task.**

---

## Task 1 — Green the 9 failing frontend specs (literacy-gate fixture gap)

**Classification:** every one of the 9 is a **test-harness/fixture gap (env shim)**, NOT a production bug. The `/welcome/ai-literacy` first-login gate landed in commits `1365f49` → `c927d01` → `407c996`, AFTER `App.test.tsx` and `crossDeviceSync.test.tsx` were written. Their `fetch` stubs were never updated, so `/api/v1/me/export` returns `{}` → `aiLiteracyConfirmed` is `undefined` (falsy) → `App.tsx:101` navigates to `/welcome/ai-literacy`, which the test routers don't render → empty `<div />` → the asserted testid never paints. **Do NOT change `App.tsx` gate logic** — production behavior (gate unconfirmed users) is correct. Fix the fixtures.

**The exact mechanism (App.tsx):**
```
36  ensureTutorSession() -> GET /api/v1/tutor/auto-session -> returns r.status (stub: 200)
75  status !== 401 && status !== 0  ==> true
88  jarvisFetch("/api/v1/me/export") -> stub returns {} 200, r.ok == true
98  !data.aiLiteracyConfirmed  ==> true (undefined)
101 navigate("/welcome/ai-literacy", { replace: true })   // <-- the redirect that breaks every assertion
```

**Files:**
- Modify: `tutor-web/src/__tests__/App.test.tsx` (the `beforeEach` fetch stub at line 17, and the two per-test `vi.stubGlobal` overrides at ~line 54 and the other override)
- Modify: `tutor-web/src/__tests__/crossDeviceSync.test.tsx` (both per-test stubs at lines 19 and 45)

### Spec-by-spec ledger (ALL 9 App.test tests + both crossDeviceSync tests · status · fix)

`App.test.tsx` has **9 tests total**. **7 fail in the full run; 8 fail in isolation** (the extra one flakes); **1 (`/review route renders FsrsReview page`) passes in BOTH** because it renders the *mocked* `FsrsReview` at `/review` and never trips the dashboard/literacy fetch path. All 9 are listed below so the "9 tests in the file" count reconciles against the rows.

| # | File · test name | Full-run | Isolation | Class | Fix |
|---|---|---|---|---|---|
| 1 | App.test · `default route shows ActiveTaskDashboard (no real task pinned)` | ❌ fail | ❌ fail | env-shim | stub `/me/export`→`{aiLiteracyConfirmed:true}` |
| 2 | App.test · `pick=1 query param forces dashboard even with last-task in localStorage` | ❌ fail | ❌ fail | env-shim | same |
| 3 | App.test · `× close button clears last-task and returns to dashboard` | ❌ fail | ❌ fail | env-shim | same |
| 4 | App.test · `real taskId pinned in URL renders TutorWorkspace` | ❌ fail | ❌ fail | env-shim | same (this test has its own override stub — add `/me/export` there too) |
| 5 | App.test · `missing taskId falls back to dashboard even when explicit` | ❌ fail | ❌ fail | env-shim | same |
| 6 | App.test · `manual-entry path reveals TaskQuickStart presets` | ❌ fail | ❌ fail | env-shim | same |
| 7 | App.test · `header nav pill 'review' links to /review` | ❌ fail | ❌ fail | env-shim | same |
| 8* | App.test · `review nav pill has aria-current=page when on /review` | ✅ **pass** | ❌ fail | env-shim (**THE FLAKER**) | same — deterministic stub greens it in both modes |
| 9 | App.test · `/review route renders FsrsReview page` (App.test.tsx:93-96) | ✅ **pass** | ✅ **pass** | — (already green) | none — renders mocked `FsrsReview`, no literacy fetch path |
| 10 | crossDeviceSync · `App prefers server jarvis_last_task cookie over localStorage on cold mount` | ❌ fail | ❌ fail | env-shim | add `/me/export`→`{aiLiteracyConfirmed:true}` branch to that test's stub |
| 11 | crossDeviceSync · `App falls back to localStorage when server cookie absent` | ❌ fail | ❌ fail | env-shim | same |

**Counts (verified by run):** full run = **9 failed** = App.test **7** (#1–#7) + crossDeviceSync **2** (#10–#11). Isolation = **10 failed** = App.test **8** (#1–#8) + crossDeviceSync **2**.

\***The flaker is App.test #8** (`review nav pill has aria-current=page when on /review`), NOT crossDeviceSync: it fails when `App.test.tsx` runs alone (8th failure) but **passes inside the full `npm test` run** (→ 7 App.test failures), a jsdom effect-timing artifact of the same `/welcome/ai-literacy` redirect bug. **crossDeviceSync fails 2/2 rock-solid in BOTH isolation and the full run** — it does NOT flake. (Verified: `npx vitest run …/crossDeviceSync.test.tsx` → 2 failed; full `npm test` FAIL list → both crossDeviceSync rows present.) The deterministic `/me/export` stub greens #8 in both modes regardless of run order. Root cause and fix are unchanged by this correction; only the per-file counts and flake-source attribution are fixed (the prior ledger asserted 8+2 with crossDeviceSync as the flaker — both false).

- [ ] **Step 1: Reproduce red (baseline — this is also the "gate seen red once" proof for E4b)**

  Run: `npx vitest run src/__tests__/App.test.tsx src/__tests__/crossDeviceSync.test.tsx`
  Expected: FAIL — 8–10 failures; stderr shows `No routes matched location "/welcome/ai-literacy"`. Confirm this is the signature before touching anything (systematic-debugging: see the failure first). **Record the red count + signature** — this is the council-required "demonstrate the gate can fail" evidence for these specs (E4b); seeing red here, then green after Step 4, proves the fixture fix is exercising the real gate, not silently skipping.

- [ ] **Step 2: Patch the `App.test.tsx` `beforeEach` stub**

  In the `beforeEach` fetch stub (line 17), add a branch BEFORE the fallthrough `return new Response("{}", { status: 200 })`:
  ```ts
  if (typeof url === "string" && url.includes("/api/v1/me/export")) {
    return new Response(JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }),
      { status: 200, headers: { "content-type": "application/json" } });
  }
  ```
  Apply the same branch to the per-test `vi.stubGlobal` override inside `real taskId pinned in URL renders TutorWorkspace` (~line 54) and any other per-test override that replaces the stub wholesale.

- [ ] **Step 3: Patch both `crossDeviceSync.test.tsx` stubs**

  In each of the two `vi.stubGlobal("fetch", …)` stubs (lines 19, 45), add the same `/api/v1/me/export` → `{aiLiteracyConfirmed:true}` branch before the fallthrough. (The `/last-task`, `/auto-session`, `/tasks` branches already exist; this is the missing one.)

- [ ] **Step 4: Run the two files to green**

  Run: `npx vitest run src/__tests__/App.test.tsx src/__tests__/crossDeviceSync.test.tsx`
  Expected: PASS — all tests in both files (11/11 in isolation). stderr no longer shows the `/welcome/ai-literacy` "No routes matched" line.

- [ ] **Step 5: Typecheck**

  Run: `npx tsc --noEmit`
  Expected: no NEW errors introduced by the stub edits (test files are type-checked).

- [ ] **Step 6: Commit**
  ```bash
  git add tutor-web/src/__tests__/App.test.tsx tutor-web/src/__tests__/crossDeviceSync.test.tsx
  git commit -m "test(stage0): stub /me/export aiLiteracyConfirmed so the literacy gate doesn't redirect App/crossDeviceSync specs"
  ```

**Acceptance:** the 2 named files run green in isolation AND inside the full `npm test` run; `No routes matched location "/welcome/ai-literacy"` no longer appears; no `App.tsx` production code changed.

---

## Task 2 — Full local suite green (`npm ci` + `:check` + `npm test` + e2e)

The CI job mirror — prove all four CI surfaces green locally before pushing, so the push doesn't burn a red CI run. The frontend job has THREE ordered steps that can each be red — `npm ci` (Task 0), `npm test` (Step 1), `npm run e2e` (Step 2) — plus the backend `:check` (Step 3, never run in CI before). Greening `npm test` alone is NOT "the suite green".

- [ ] **Step 1: Frontend unit/component — `npm test`**

  Run (from `tutor-web/`): `npm test`
  Expected: **0 failed**, 567 passed (the 558 already-green + the 9 fixed in Task 1). If any non-Task-1 test is red, STOP — it's a regression from the 10 unpushed commits, not in scope to paper over; debug it (systematic-debugging) and fix the cause.

- [ ] **Step 2: Frontend e2e — REPAIR the vacuous gate FIRST, then run it ⚠️ COUNCIL FIX (load-bearing)**

  **The gate is structurally INVALID as written — fix it before trusting it.** `e2e/door-real-backend.spec.ts:7` does `page.goto('/')`; with playwright `baseURL: "http://localhost:5173"` (playwright.config.ts:6) that resolves to `http://localhost:5173/`, which is **OFF the `/tutor` base**. The SPA mounts under `<BrowserRouter basename="/tutor">` (main.tsx:15) and vite serves it at `/tutor/` (vite.config.ts:8 `base: "/tutor/"`). So `/` paints a **routeless near-empty page**: `App` never mounts, no `/api` request ever fires, and the test's "zero 4xx/5xx" assertion is **vacuously true with NO backend running** — it has never been capable of failing and certifies nothing. Asserting "no backend errors" while no backend is exercised is a hollow checkmark (Domain Expert / First Principles / Risk Analyst, all verified against main.tsx:15 + playwright.config.ts:6 + vite.config.ts:8).

  **What actually happens re: webServer (corrected — the SPA does NOT need a separate build):** `playwright.config.ts` declares `webServer.command: "npm run dev"` with `reuseExistingServer: true`, so Playwright boots the **Vite dev server itself** at `http://localhost:5173` (or reuses one). There is no "build the SPA first" step.

  **Sub-step 2a — make the navigation REAL.** Change the spec to navigate under the base so `App` actually mounts and the `/api` path is exercised:
  - Change `page.goto('/')` → `page.goto('/tutor/')` (the `/tutor` base) so the router resolves the `/` route element `<App />` (main.tsx:17) and first-paint `/api/*` calls (`/api/v1/tutor/auto-session`, `/api/v1/me/export`, etc. — see Task 1's App.tsx trace) actually fire through the vite proxy.
  - Re-verify the resolved URL at execution time (`page.url()` after goto) lands on the mounted SPA, not the routeless page. If `reuseExistingServer` picks up a stale Vite without the `/tutor` base, restart it.

  **Sub-step 2b — give the assertion something to assert against (a backend, or honest re-scope).** Once it navigates under `/tutor`, first paint WILL hit `/api`→`:8080`. CI's frontend job starts **no** Kotlin backend, so pick ONE, record which:
  - **(a) Start a real/stub backend for the assertion to mean anything.** Boot the Kotlin backend on :8080 (`./gradlew run` / the WebMain entrypoint) before `npm run e2e` AND wire the same boot into the CI frontend e2e step (else CI's e2e is still red/hollow); OR stub the `/api` proxy responses at the Playwright layer so the spec exercises a deterministic contract. This keeps it a true "real-route, real-API, zero-4xx/5xx" gate.
  - **(b) If a real backend in the frontend CI job is OUT OF SCOPE for Stage-0, RENAME/RE-SCOPE the spec so it is not masquerading as a backend gate.** Rename `door-real-backend.spec.ts` → a `*-frontend-paint.spec.ts` (or similar) that explicitly asserts only what it CAN without a backend — i.e. navigate `/tutor/`, assert `App`'s shell/nav paints (a real `data-testid` from the mounted shell), and assert no client-side 4xx for **statically-served** assets — and DROP the false "real backend, zero `/api` 5xx" claim from its name + comment. A test that can't reach `/api` must not pretend to gate `/api`.
  Record the choice (a or b) and WHY in the execution log. Do NOT leave it asserting "zero 4xx/5xx" over an unmounted page.

  **Sub-step 2c — DEMONSTRATE the gate can FAIL once (load-bearing, council).** A gate never seen red is indistinguishable from no gate. After the repair, prove it is capable of failing:
  - For path (a): with the backend down (or the stub forced to return a 5xx for one route), run the spec and CONFIRM it FAILS the zero-4xx/5xx assertion; then bring the backend/stub back and confirm it goes GREEN. Record both runs.
  - For path (b): temporarily break the asserted shell `data-testid` (or point goto at a known-404 path) and confirm the renamed spec FAILS; revert and confirm GREEN. Record both runs.
  This red→green demonstration is REQUIRED evidence for E4b (and E4a's "real, not vacuous") — not optional.

  **Sub-step 2d — run the repaired gate green.** Run (from `tutor-web/`): `npm run e2e`. Expected: the repaired/renamed spec PASSES for the reason it claims (App mounted under `/tutor`, and either backend-backed zero-4xx/5xx OR the honestly-scoped frontend-paint assertion), with the red-demonstration from 2c on record.

  > **Do NOT hand-wave E4a/E4b as "CI runs the same thing."** "CI substitutes for local e2e" is only valid once (i) the spec navigates under `/tutor` so it isn't vacuous, AND (ii) either the backend is provisioned in the CI e2e step OR the spec is honestly re-scoped to a backend-free assertion, AND (iii) it has been demonstrated capable of failing once. Until all three hold, E4a/E4b are unmet regardless of a green checkmark.

- [ ] **Step 3: Backend — `./gradlew :check`  ⚠️ UNPROVEN IN CI — verify locally before relying on E3/E7**

  Run (from repo root): `gradlew.bat :check` (Unix: `./gradlew :check`)
  Expected: BUILD SUCCESSFUL. `:check` includes `validateContent`.

  **Why this is NOT a rubber-stamp (job-name/command drift, verified):** the last *pushed* CI run shows the backend job as **`backend (gradle :test)`** (`gh run view 26670694533`), but the workflow at **current HEAD** (`.github/workflows/test.yml:16,29`) names the job **`backend (gradle check)`** and runs **`gradle --no-daemon :check`** (incl. `validateContent`). The workflow was changed inside the **10 unpushed commits**, so the **first push will run a newer, heavier backend step that has NEVER actually run in CI on this repo.** `:check` can surface failures `:test` never did (e.g. a `validateContent` corpus-validation failure, extra verification tasks). **Therefore the plan's confidence that backend will be green is unproven.** Run `gradlew.bat :check` locally to completion and confirm BUILD SUCCESSFUL **including `validateContent`** BEFORE trusting E3/E7. If `:check` pulls the android subproject locally, scope to root `:check` to match CI exactly. If `:check` is red, it blocks the push — diagnose it (it's a real pre-existing red hiding behind the old `:test` job), do not push over it.

- [ ] **Step 4: Record the green triple**

  Capture the three pass lines (vitest summary, playwright summary, gradle BUILD SUCCESSFUL) in the execution log. This is the local pre-push evidence (verification-before-completion: evidence before assertions).

**Acceptance:** `npm test` 0-failed · the **repaired** e2e gate green for the reason it claims (mounts `App` under `/tutor`; backend started/stubbed OR honestly re-scoped) **and demonstrated capable of failing once** · `./gradlew check` BUILD SUCCESSFUL. Local green here is the **pre-condition** to push the scratch branch (Task 4) — NOT the gate.

---

## Task 3 — Arm the backup precondition (`MIN_EXPECTED_CARDS=800`) — TDD, PROVEN-FAILABLE ⚠️ COUNCIL FIX

**Verified ABSENT (not assumed):** `tools/db-backup.py` reads `src_count` at **line 44** (`SELECT COUNT(*) FROM fsrs_cards`) and restore-integrity-checks (line 77, `restored_count == src_count`), but there is **NO `MIN_EXPECTED_CARDS` constant and NO `src_count <` floor check** anywhere in the file. The Risk Analyst flagged this HIGH: as written, a culled/wrong-path DB with 0 cards still backs up "integrity: OK" and **exits 0** — a green-looking backup that proves nothing, right before Phase 1 culls the only copy of 871 real cards. The guard is genuinely unbuilt; this task BUILDS it and **proves it can ABORT**.

The v2 plan (M-DB, line 175) requires `MIN_EXPECTED_CARDS=800` IN the script so the very first Phase-1 ALTER cannot run over a truncated/wrong DB. ADD it. This guard is the safety interlock for Phase 1 — author it now, in Stage-0, where there's no schema pressure. **Not-optional acceptance: a TDD test must demonstrate the guard ABORTS below the floor (it is not enough that it exists — it must be seen to fail-closed once).**

**Files:**
- Modify: `tools/db-backup.py`
- Test: `tools/test_db_backup.py` (new — pytest or stdlib `unittest`; no external deps, matches the repo's `tools/test_*.py` convention)

- [ ] **Step 1: Write the failing test (PROVE the abort, both sides of the floor)**

  Create `tools/test_db_backup.py` that, against a temp SQLite DB:
  - **floor PASS** — builds a fixture DB with an `fsrs_cards` table holding **801 rows** → run backup → asserts **exit 0** and a `.sql.gz` is written.
  - **floor ABORT (the load-bearing case)** — builds a fixture DB with **799 rows** → run backup → asserts **exit non-zero** AND **NO `.sql.gz` was trusted/produced as a valid backup** AND stderr contains `MIN_EXPECTED_CARDS`. This is the proof the guard **fails closed** — without it the guard "exists" but has never been seen to abort, which is indistinguishable from no guard (council: every gate must be demonstrated capable of failing once).
  - **boundary** — optionally assert **800 rows** is the inclusive/exclusive boundary you chose (e.g. `< 800` aborts → 800 passes, 799 aborts); pick one and pin it in the test so the floor semantics can't silently drift.
  - (keep the existing restore-integrity path covered: 801-row dump restores to 801.)

  Drive the script via `JARVIS_DB=<tmp>` env (the script already reads `JARVIS_DB`, line 26) + a tmp output dir, invoking `main()` or `subprocess`.

  Run: `python tools/test_db_backup.py` (or `pytest tools/test_db_backup.py`)
  Expected: FAIL — the 799-row case currently **exits 0** (verified: no `MIN_EXPECTED_CARDS` / no `src_count <` check exists yet in db-backup.py). Seeing this RED first is the demonstration the test actually exercises the guard.

- [ ] **Step 2: Add the guard to `db-backup.py`**

  Introduce a module constant `MIN_EXPECTED_CARDS = 800` and, immediately AFTER `src_count` is read (currently line ~44) and BEFORE the dump, add:
  ```python
  if src_count < MIN_EXPECTED_CARDS:
      print(f"ERROR: fsrs_cards={src_count} < MIN_EXPECTED_CARDS={MIN_EXPECTED_CARDS}"
            f" — refusing backup (corpus looks truncated; do NOT migrate)", file=sys.stderr)
      src.close()
      return 1
  ```
  Allow override via `MIN_EXPECTED_CARDS` env (optional) but default 800. Keep the existing restore-integrity check unchanged.

- [ ] **Step 3: Run test to green**

  Run: `python tools/test_db_backup.py`
  Expected: PASS — 801→exit0, 799→exit-nonzero with `MIN_EXPECTED_CARDS` in stderr.

- [ ] **Step 4: Real off-box dump against the live DB**

  Run (from repo root): `python tools/db-backup.py ./backups`
  Expected: `[db-backup] integrity: PASS` and `backed up 871 fsrs_cards` (871 ≥ 800 → guard passes). A `backups/jarvis-tutor-db-<ts>.sql.gz` lands.
  **OFF-BOX note:** the deliverable is an *off-box* dump. Producing the local `.sql.gz` is the script's job; moving it off-box (e.g. to the VPS or external storage) is an operational step done at Phase-1 execution time, not a code change here. Do NOT `scp`/push it as part of this authoring pass. Record that the local dump succeeded with the 800-floor armed.

- [ ] **Step 5: Commit**
  ```bash
  git add tools/db-backup.py tools/test_db_backup.py
  git commit -m "feat(backup): MIN_EXPECTED_CARDS=800 floor in db-backup.py (M-DB) + test — schema-ALTER precondition for Phase 1"
  ```

**Acceptance:** `db-backup.py` refuses (exit≠0) below 800; the live 871-card DB dumps green with integrity PASS; test covers both sides of the floor.

---

## Task 4 — Push a SCRATCH branch → read REAL CI → only THEN push `main` ⚠️ COUNCIL FIX (the outward actions — `main` push needs Alex's explicit OK)

> **🚩 GATE + sequencing fix (council `1780431232`).** The council ruled **local-green is the wrong exit criterion**: `:check`+`validateContent` has never run on a pushed commit and the green-making workflow itself lives inside the 10 unpushed commits, so the **FIRST push is the real test**. Pushing `main` straight into a known-red CI to "discover" failures is backwards. Therefore this task is split: **push a THROWAWAY/SCRATCH branch FIRST** (cheap, reversible, surfaces exactly what the new workflow does in real CI — ERESOLVE-fixed `npm ci`, `:check`+`validateContent`, the repaired e2e), fix whatever CI surfaces, and **only then fast-forward + push `main`** (gated on Alex's explicit ok). The scratch branch is the gate; `main` is the outward production action.

- [ ] **Step 0: Diagnose the ALREADY-RED CI — prerequisite, not a watch-and-hope**

  **Ground truth: CI on `main` is RED right now.** The last 5 push runs are all `failure`; the failing job is **frontend**, dying at **`npm ci`** (ERESOLVE) — see `gh run list --branch main` and `gh run view 26670694533`. **Do NOT push and then discover the red.** Before any push, confirm the four known causes are each closed locally:
  1. **frontend `npm ci`** — closed by **Task 0** (`.npmrc` `legacy-peer-deps=true`). Re-confirm `npm ci` exits 0 from clean.
  2. **frontend `vitest`** — closed by **Task 1** (9 specs greened). Re-confirm `npm test` 0-failed.
  3. **frontend `npm run e2e`** — the **repaired** e2e gate from Task 2 Step 2 (navigates under `/tutor`; backend started/stubbed OR honestly re-scoped; demonstrated capable of failing). The OLD vacuous spec would pass in CI for the wrong reason — that no longer counts.
  4. **backend `:check`** — never run in CI before (job-name drift, Task 2 Step 3); must be locally green incl. `validateContent`.
  Local-green on 1–4 is the PRE-condition to push the scratch branch — NOT the exit. The exit is the scratch-branch CI going green (Step 2).

- [ ] **Step 1: Push a SCRATCH branch (no Alex-gate — it does not touch `main`/production)**

  ```bash
  git switch -c stage0-ci-probe        # throwaway; name it whatever
  git push -u origin stage0-ci-probe   # this is the FIRST real test of the new workflow
  ```
  This is reversible: it pushes a non-`main` branch only. Per Alex's branching-scope ("stays on main = production; demos/probes → feature branches"), a scratch CI-probe branch does **not** require the production push-gate — only `main` does (Step 4). Do NOT `--force`; do NOT push `door-compare`.

- [ ] **Step 2: Read the REAL CI result on the scratch branch — fix whatever it surfaces**

  Run: `gh run watch` / `gh run list --branch stage0-ci-probe --limit 1`, then `gh run view <id>`.
  Expected target: all 3 jobs green on the scratch SHA — **`backend (gradle check)`** runs `gradle --no-daemon :check` (the NEW heavier job from the unpushed commits, never CI-run — see Task 2 Step 3), **`frontend (vitest)`** runs `npm ci` → `npm test` → `npx playwright install chromium` → `npm run e2e` (the repaired gate), **`daemon (cargo test)`** runs `cargo test --locked`.
  **This is the gate, not a formality:** the scratch run is the first time `:check`+`validateContent` + ERESOLVE-fixed `npm ci` + the repaired e2e run together in real CI. If ANY job is red, the local/CI environments diverged or a cause was missed — **diagnose the specific failing step, fix the cause, push again to the scratch branch, re-read.** Loop until the scratch branch is fully green. Do NOT move to Step 3 until it is.

- [ ] **Step 3: Confirm pre-`main`-push state**

  Run: `git switch main`, then `git fetch origin && git rev-list --count origin/main..main` and `git rev-list --count main..origin/main`.
  Expected: clean working tree (Tasks 0–3 committed; scratch-probe fixes, if any, also landed on `main`). **Assert nothing new landed on `origin/main`** (`main..origin/main` count = 0). Do **not** assert a hard-coded ahead-number — it drifts with how many commits Tasks 0/1/3 + any scratch-surfaced fixes produced. The exit check that matters is **post-push `origin/main == main`** (Step 5 / E6), not a brittle integer. Confirm `main`'s tip is (or fast-forwards to) the exact tree the scratch branch proved green.

- [ ] **Step 4: ⚠️ Get Alex's explicit OK, then push `main` (the ONE production action)**

  This is the only step that touches `origin/main`. Per Alex's branching-scope + "stays on main = production" stances, **get Alex's explicit go-ahead before running**:
  ```bash
  git push origin main
  ```
  (Do NOT `--force`; do NOT push any other branch — `door-compare` stays unpushed.) Because `main` now equals the scratch-proven tree, this push is expected to be a formality, not a discovery.

- [ ] **Step 5: Confirm `main` CI green + clean up the scratch branch**

  Run: `gh run watch` (on `main`); expected all 3 jobs green on the `main` SHA (same result the scratch branch already proved). Then delete the throwaway: `git push origin --delete stage0-ci-probe` and `git branch -D stage0-ci-probe`. Record the green `main` run URL.

**Acceptance:** the **scratch branch went green on all 3 CI jobs** (the real gate); each gate was demonstrated capable of failing once (Task 2 Step 2c, Task 3 Step 1); `git push origin main` then succeeds with Alex's OK; `origin/main` == local `main` (ahead-count 0); the `main` CI run for that SHA is green on all 3 jobs.

---

## EXIT CRITERIA (binary — the gate is PASSED iff ALL true)

> **Exit criterion TIGHTENED per council `1780431232` (FLAWED verdict): the gate is NOT "all green locally" — it is "CI green on a pushed SCRATCH branch AND every gate demonstrated capable of failing once."** Local-green (E0–E3, E5) is the *pre-condition* to push the scratch branch; the *gate itself* is E6 (scratch-branch CI green) + E4b (every gate proven failable). `main` push (E7) is the outward production action, gated on Alex, and is expected to be a formality once the scratch branch is green.

- [ ] **E0** — `npm ci` exits **0 from a clean state** in `tutor-web/` (the ERESOLVE peer conflict is resolved via committed `.npmrc legacy-peer-deps=true` or workflow `--legacy-peer-deps`). **Without E0 the frontend CI job dies at install and E6/E7 cannot be reached.**
- [ ] **E1** — `npm test` in `tutor-web` exits **0 failures** (the 9 specs green; full suite green).
- [ ] **E2** — the fixed specs green **in isolation AND in the full run** (incl. App.test #8, the flaker, deterministically green both ways); `No routes matched location "/welcome/ai-literacy"` gone from stderr.
- [ ] **E3** — `gradlew.bat :check` → **BUILD SUCCESSFUL** locally, **including `validateContent`** (this is the heavier `:check` that has never run in CI — its green-ness is proven here, not assumed).
- [ ] **E4a** — the e2e gate is **REAL, not vacuous**: `door-real-backend.spec.ts` (or its honest rename) navigates under **`/tutor`** so `App` actually mounts (no more `goto('/')` off-base routeless page), and EITHER it exercises a started/stubbed backend's `/api` with zero 4xx/5xx, OR it is re-scoped + renamed to an honest backend-free frontend-paint assertion. `npm run e2e` green for the reason the spec claims. (Task 2 Step 2.)
- [ ] **E4b** — **every gate demonstrated capable of FAILING once** (council, load-bearing): the repaired e2e shown red→green (Task 2 Step 2c), the `MIN_EXPECTED_CARDS` floor shown to ABORT at 799 then pass at 801 (Task 3 Step 1), and the 9-spec fix shown red-first then green (Task 1 Step 1). A gate never seen red is indistinguishable from no gate.
- [ ] **E5** — `tools/db-backup.py` contains `MIN_EXPECTED_CARDS=800`, **refuses (exit≠0, no trusted dump) below the floor** (test proves the ABORT side, not just the pass side — see E4b), and a **real dump of the live 871-card DB ran green** (integrity PASS).
- [ ] **E6** — **CI is GREEN on a pushed SCRATCH branch** (`stage0-ci-probe` or similar) on all 3 jobs — `backend (gradle check)` / `frontend (vitest, incl. npm ci + repaired e2e)` / `daemon (cargo test)`. **THIS is the gate** (replaces "local green"): the first real run of `:check`+`validateContent` + ERESOLVE-fixed `npm ci` + the repaired e2e in actual CI. No Alex-gate (non-`main` branch). (Task 4 Steps 1–2.)
- [ ] **E7** — **only after E6 green**, `git push origin main` done **with Alex's explicit OK**; after push **`origin/main` == `main`** (`git rev-list --count origin/main..main` == 0 AND `main..origin/main` == 0) and the `main` CI run for that SHA is green on all 3 jobs (the scratch branch already proved this tree; `main` is the production formality). The scratch branch is then deleted. (Exit check is repo-state equality, NOT a hard-coded ahead-count.)
- [ ] **E8** — no `App.tsx` (or other production) source changed to satisfy a test — only test fixtures + `db-backup.py` guard + the `.npmrc` (build config) + the e2e spec's navigation/scope (test infra, not product code) touched.

When E0–E8 are all checked, Stage-0 is passed and Phase 1 (`2026-06-02-data-model-lock.md`) may begin. **Note the gate flipped from local-green to scratch-branch-CI-green (E6) per the council** — do NOT declare Stage-0 passed on local green alone.

---

## NOT in this gate (boundary — these are Phase 1+)

- **No schema ALTERs** — zero `ADD COLUMN`, zero new tables, zero `createMissingTablesAndColumns` changes. (Changes 1–10 are Phase 1.)
- **No card cull** — the 871→849 cull (16 junk + 6 dupes, B1 refinement) is a **dedicated, tested, backed-up Phase-1 SQL task**. Stage-0 leaves all 871 cards untouched; it only *arms the backup that protects the cull*.
- **No backfill UPDATEs, no `PhaseModel`/`VerificationStatus`/`recordIn` split, no `upsertRubricCriterion`** — all Phase 1.
- **No `App.tsx` gate-logic change** — the literacy redirect is correct prod behavior; Stage-0 fixes only the stale test fixtures.
- **No off-box transfer automation** — the backup *script + floor* is armed here; wiring the dump into a deploy/migration runner is Phase-1 execution.
- **No `door-compare` merge** — the T5 theme-shell merge to `main` (B7) is Phase 4. `door-compare` stays unpushed.

---

## Open Questions / Risks (honest)

0. **🔴 CI on `main` is ALREADY RED (CERTAIN — not a risk, a current fact) AND local-green is NOT the exit (council).** Last 5 push runs = `failure`; the failing job is **frontend** at **`npm ci`** (ERESOLVE). Task 0 fixes it. But per council `1780431232`, the deeper issue is the **exit criterion**: `:check`+`validateContent` has never run on a pushed commit and the green-making workflow lives inside the 10 unpushed commits, so the **first push is the real test**. Task 4 therefore pushes a **SCRATCH branch first** to read what real CI does (install/vitest/repaired-e2e/`:check`), fixes what it surfaces, and only then pushes `main` (Alex-gated). `gh run view 26670694533` is the red-`main` receipt.
1. **e2e gate is a VACUOUS false-green — it never even reaches `/api` (HIGH — council-confirmed, plan-invalidating for E4).** The "real `/api`, no stubs, zero 4xx/5xx" framing was WRONG in a deeper way than "no backend": `door-real-backend.spec.ts:7` does `goto('/')` with playwright `baseURL: :5173` (playwright.config.ts:6) → it lands at `http://localhost:5173/` which is **OFF** the `/tutor` base. The SPA mounts under `<BrowserRouter basename="/tutor">` (main.tsx:15) + `base: "/tutor/"` (vite.config.ts:8), so `/` paints a **routeless near-empty page** — `App` never mounts, NO `/api` request fires — and "zero 4xx/5xx" is **vacuously true with no backend**. The test has never been capable of failing → it gates nothing. **Fix (Task 2 Step 2): navigate under `/tutor` so `App` mounts and `/api` actually fires; then either start/stub a backend for the assertion to mean something, OR rename/re-scope the spec honestly; then demonstrate it can fail once.** Until then, "CI runs the same thing" is no escape hatch — CI's green on this spec is the SAME false-green. (Verified against main.tsx:15, playwright.config.ts:6, vite.config.ts:8, door-real-backend.spec.ts:4-11.)
2. **`sqlite3` CLI absent (low — handled).** Confirmed not on PATH. `db-backup.py` and its test use Python's stdlib `sqlite3` (no CLI dependency), so this is a non-blocker for Task 3. Any ad-hoc card count must use `python -c "import sqlite3…"`, not the `sqlite3` shell.
3. **CI parity for the literacy fix (low).** The fix is pure test-fixture; CI runs the same `vitest` — but ONLY once Task 0 lets `npm ci` reach `vitest run`. Low risk of local-green/CI-red divergence at the vitest layer; the actual divergence risk is upstream at install (Task 0) and at e2e (item 1).
4. **Flake source = App.test #8, NOT crossDeviceSync (root-caused).** The 9-vs-10 drift is **App.test.tsx**'s `review nav pill has aria-current=page when on /review`: fails in isolation (8th), passes in the full run (→7), jsdom effect-timing on the same `/welcome/ai-literacy` redirect bug. **crossDeviceSync fails 2/2 rock-solid in both modes — it is NOT the flaker** (the original plan misattributed the flake to it). The deterministic `/me/export` stub greens #8 in both modes; same fix, no flake suppression.
5. **`:check` scope (low).** CI runs `gradle :check` (root only; android subproject skipped) incl. `validateContent`. If a local `gradlew.bat :check` pulls the android subproject, scope to root `:check` to match CI exactly.
6. **Backend `:check` is UNPROVEN in CI (medium — NOT low).** Job-name/command drift: the last pushed run ran `backend (gradle :test)`; current HEAD runs `backend (gradle check)` → `:check` (incl. `validateContent`), changed inside the 10 unpushed commits. **The first push runs a heavier backend step that has never executed in CI on this repo** — `:check` may surface failures `:test` didn't (validateContent, extra verification tasks). The plan's confidence backend will be green is therefore unproven; **`gradlew.bat :check` must be run locally to BUILD SUCCESSFUL before relying on E3/E7** (Task 2 Step 3). A red `:check` blocks the push — diagnose, don't push over it.
7. **Ahead-count is conditional, so the exit check is repo-state equality (minor).** Task 4 Step 3 no longer asserts a hard-coded ahead-number (it would be ≈11–13 depending on whether Tasks 0/1/3 each produce exactly one commit + any scratch-surfaced fixes — drifts if any is split or amended). The binary exit check is post-push **`origin/main == main`** (E7: `origin/main..main` == 0 AND `main..origin/main` == 0), not an integer. The actual GATE is E6 (scratch-branch CI green), not the ahead-count.

---

## Execution Handoff

Two options:
1. **Subagent-Driven (recommended)** — one subagent per task (0→1→2→3→4), review between. Task 4 pushes a scratch branch unattended, then **pauses for Alex's explicit `main`-push OK** (Step 4).
2. **Inline** — batch Tasks 0–3 in-session with the green-triple checkpoint, push the **scratch branch** to read real CI, fix what it surfaces, then halt at Task 4 Step 4 for the `main` push gate.

**Hard ordering:** **Task 0 (fix `npm ci` — PREREQUISITE for any CI-green claim)** → Task 1 (green specs, red-first) → Task 2 (local green triple + **REPAIR the vacuous e2e gate** so it mounts `App` under `/tutor`, and demonstrate it can fail) → Task 3 (backup floor + **prove it aborts** + live dump) → Task 4 (**push a SCRATCH branch → read real CI → fix → only THEN push `main`**, the `main` push gated on Alex). The scratch-branch CI run is the GATE; the `main` push is the lone outward production step.

> **One-line orientation for the executor (council `1780431232`, FLAWED):** CI on `main` is RED *now* (frontend `npm ci` ERESOLVE). **Local green is NOT the exit — CI green on a pushed scratch branch is.** Two structural fixes are load-bearing: (1) the e2e gate `door-real-backend.spec.ts` is a VACUOUS false-green — `goto('/')` lands OFF the `/tutor` basename so `App` never mounts and `/api` never fires; make it navigate under `/tutor` (+ start/stub a backend OR honestly re-scope it) and demonstrate it can fail; (2) push a SCRATCH branch first to surface what the never-CI-run `:check`+`validateContent` + repaired e2e actually do, before `main`. Beyond the 9 specs: install, the repaired e2e, `:check`, and the proven-failable backup floor.
