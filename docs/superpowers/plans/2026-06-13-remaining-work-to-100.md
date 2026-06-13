# Remaining Work to 100% Spec — jarvis tutor

> **This is the single handoff ledger.** A fresh session reads `BRIDGE-HEAD.md` first, then this doc, to get the WHOLE remaining picture in one place — no thread lost across sessions. Authored 2026-06-13 (SESSION-68) at `main @ 09f5add`. Binding spec = `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md`; roadmap = `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md`.

## What "100%" means (spec §10.2)
A real user **drops a file/URL → it digests into faithful content → teaches (lesson) → practices → tracks**, across the real corpus, with every gate alive. The proof is the §10.2 **11-artifact proof run** + bulk-upload of the 140 inventoried sources.

## DONE + CI-green (run `09f5add`, all 7 jobs)
Plan 1 (trust-net ON) · Plan 2 (knowledge schema) · Plan 3 (lesson engine + viz family **1 of 6**) · Plan 4a (gate tooling) · Plan 4b (rendered gates 3+4) · Plan 6 (practice + 4-leg grader chain). Trust-net LIVE: 4 faithful KCs (`pa-kc-001..004`) serving.

---

## A. Build plans (sequence: V → 5 → 7; W after V)

| Plan | What | State | Doc |
|---|---|---|---|
| **V** | **Viz families 2–6** (sequence/array · matrix/grid · chart/distribution · timeline/protocol · state-machine/code-trace) + migrate the ~24 hand-coded primitives into the family system. Only family 1 (graph/tree) is built. Frontend-only; reuses 4b's per-family harness. **Load-bearing — Plan 5 can only emit figures for families that exist; this is the sparse-figure fix.** | **NEXT, recon'd** | `build-review/2026-06-13-planV-viz-families-recon.md` |
| **5** | Digestion/upload pipeline — upload door (file/URL) → 9-kind classifier → per-kind extraction → dedup/provenance → gap-ledger → generate beat content + viz instances → checkpoint review. The "drop a PDF, get a lesson" engine half. | pending | spec §1.4-1.5,§2 |
| **7** | Tracking/timeline — unified FSRS+EWMA state, beat telemetry, forgetting→re-lesson, misconception→re-queue, exam-aware queue priority, readiness dashboard, ADHD session shape, placement rebuild + the §10.2 **proof run**. | pending | spec §7,§10 |
| **W** | Warm second theme — switchable app-wide skin; every surface gated in BOTH skins, baselines double. **AFTER V** (so dual-skin baselines cover all 6 families at once; never run V ∥ W — viz file intersection). | ratified | spec-external; `DoorWarm.tsx`/`warmVars`/`palettes.ts` exist |

## B. Gate hardening / loose threads (tracked, unslotted — fold into the plan that touches the area)
- **Impeccable → blocking.** Built in Plan 4a, running in CI **fail-open** (`|| true`). Locked sequence = calibrate→subset→pin→fail-open→**then blocking**; stuck at fail-open. Promotion to a real blocking gate needs a re-calibrate against more true-positives + flip the `|| true`. **Fold into Plan V** (V is the big viz/design push impeccable lints) or a dedicated gate-hardening pass. Files: `tools/impeccable-rules.json`, `tools/impeccable-filter.mjs`, `build-review/impeccable-calibration-2026-06-12.json`.
- **cross-language schema-hash CI test** — python `db-backup.py` vs Kotlin `MigrationBackupGate.liveSchemaHash`; now more load-bearing (4b duplicated the §0.9E RO-heuristic constants TS↔Kotlin). → Plan 5.
- **relay retry/backoff** — `RelayLlm`/`FreeLlmApiLlm` got `RetryingLlm` in Plan 6; the VerificationRunner relay path (pa-kc-006 false-negative class) still bare. → verify path.
- **StateCache flake** (`stateCacheConcurrentPersistNeverTearsJson`) — pre-existing concurrency race, ~1-in-2 under `--rerun-tasks`; standing carve-out, name-don't-chase. A real fix (isolate the cache per test) is unslotted.
- **Linux visual-baseline regeneration** — baselines were captured on Windows; CI runs Linux. Unslotted (no visual job blocks on them yet).
- **deploy.sh SPA smoke broken** — greps `/` for `<div id="root">` but `/` 302s to `/login`; replace with healthz + an authenticated probe. → fold into Task 15 / deploy.
- **~24 tsc baseline errors** — pre-existing; CI runs vitest not tsc.

## C. Per-plan re-carries (small, named — not lost)
- **REQ-1** — queue actually moves a concept along the predict→practice arc as mastery rises (spec §6.1/§7.4). → Plan 7.
- **R-MULTISELECT drills** — the "bifați toate" multi-select variant on the DRILL side (Plan 6 shipped only the mock-exam grid half). → Plan 7 or a practice follow-up.
- **ALO-proof + POO problem seeds** — Plan 6 seeded PA/ALO/PS; ALO-proof archetype + POO have no locatable corpus problem yet (honest pending). → arrives with content (Plan 5/7).
- **CodeMirror editor** — code-practice uses a plain textarea (R-6-Q9 deferral); real syntax editor is a named follow-up. → practice polish.
- **lessonStrings / chromeStrings / practiceStrings consolidation** — 3 RO strings files exist by design this cycle; revisit/merge. → tidy.
- **FSRS re-seed wart** · **pa-kc-006 re-audit** (pending relay re-auth) · **Resend key unset** (magic-link via log) — small ops items.
- Resolved already (don't re-carry): BeatOrchestrator error boundary (done in 4b), numeric-ATTEMPT tolerance single-source (done in 6), `tutor-shell-api-contract` red (resolved `5fbfaaf`), rtk (rejected w/ evidence).

## D. Ops / "make it real" (non-build, but required for a usable site)
- **Deploy — Task 15 (PM-gated).** Nothing from SESSION-68 is live; the VPS serves the SESSION-66 bundle. VPS deploy + toolchain provisioning probe + production seed behind the INV-3.1 backup gate + live interaction smoke. **No feature-shipped claim for ANY 4b/6 surface until its live probe passes.** Do this after each plan lands (or batch).
- **Content (the 140 sources).** ALO 25 · PS 59 · SORC 18 · PA 4 · POO 32. The site is nearly empty until these ingest — and ingestion needs Plan 5 (digest) + the Plan 7 proof run. The CP-3 `LanguageCheckTable` live migration also fires when content flows.
- **Trust-net flip — DONE** (corrects the long-standing stale "never run" note): 4 PA KCs are faithful + serving on PC+VPS.

---

## Recommended sequence (one line)
**Plan V** (+ fold impeccable→blocking) → deploy → **Plan 5** → **Plan 7** (incl. the proof run + content) → **Plan W** after V whenever. Two-lane contract applies if two non-intersecting plans run parallel.

## How a new session uses this
1. Read `BRIDGE-HEAD.md` (current state + the V→5→7 pointer).
2. Read this ledger (the full remaining map).
3. For the active plan, open its recon/plan doc (Plan V → the recon above) and execute just-in-time per the build workflow.
