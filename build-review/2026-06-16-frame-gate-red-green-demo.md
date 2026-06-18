# UNIT 1 — frame-gate RED→GREEN trust demo (live CI, 2026-06-16)

The trust-win: the figure-overseer's deterministic floor (the frame-conjunction gate)
demonstrably catches a real "figure frozen while state moved" defect on live GitHub
Actions, and clears when the defect is fixed. The gate is now a HARD-BLOCKING CI job.

Repo: CorgiGH/Jarvis · gate job = `frame-gate (frame-conjunction figure gate, real renderer)`
in `.github/workflows/test.yml` (boots vite :5173, runs `node tutor-web/tools/frame-conjunction-gate.mjs`
over the REAL renderer; no `|| true`, no continue-on-error).

## The three runs

| # | Commit | Renderer | frame-gate | Run |
|---|--------|----------|-----------|-----|
| 1 | `01763c6` (main, UNIT 1) | FIXED (computePlacement reads st.runs) | **GREEN** 0 exempt / 58 live / 0 fail, exit 0 | actions/runs/27585267302 |
| 2 | `b609f7b` (PR #2 defect) | BROKEN (divide branch ignores st.runs) | **RED** 6 HARD FAILS, exit 1 | actions/runs/27585406595 |
| 3 | `932a2cd` (PR #2 revert) | FIXED again | **GREEN**, exit 0 | actions/runs/27585539848 |

PR #2 (`demo/frame-gate-red`) was opened ONLY for the demo and closed without merging —
the defect never touched main.

## The RED verdict (run 2, captured verbatim from the CI job log)

```
  FAIL  frame 0→1: STATE CHANGED [runs,frontOf] but FIGURE FROZEN (changed pixels=0 < MEANINGFUL_PX=80; maxΔ=0, region=760x336)
  FAIL  frame 1→2: STATE CHANGED [runs,frontOf] but FIGURE FROZEN (changed pixels=0 < MEANINGFUL_PX=80; maxΔ=0, region=760x336)
  FAIL  frame 2→3: STATE CHANGED [runs,frontOf] but FIGURE FROZEN (changed pixels=0 < MEANINGFUL_PX=80; maxΔ=0, region=760x336)
  FAIL  frame 0→1: ... region=620x347   (second skin)
  FAIL  frame 1→2: ...
  FAIL  frame 2→3: ...
  exempt holds (state unchanged)        : 0
  live pairs (state+figure both moved)  : 52
  HARD FAILS (state moved, figure frozen): 6
  [boxes @ /tutor/lectie-mergesort] frame 0→1: state changed {runs, frontOf}, figure region changedPx=0 (< 80) — FROZEN
  [boxes @ /tutor/lectie-mergesort] frame 1→2: ...  [boxes ...] frame 2→3: ...
  [bars @ /tutor/merge-compare] frame 0→1: ...  [bars ...] 1→2: ...  [bars ...] 2→3: ...
GATE RED — 6 frozen-figure-over-live-state pair(s). Exit 1.
```

RED is grounded for the EXACT documented reason — the divide frames 0→1,1→2,2→3 went
byte-frozen (changedPx=0) on BOTH skins (boxes + bars) while the typed state advanced
(`runs,frontOf` changed). Not a CI-env fluke. `live pairs` dropped 58→52 (the 6 froze).

## Permanent liveness (separate from this one-time demo)

The committed `frameConjunction.self.test.ts` (frontend vitest job, no browser) feeds REAL
PNG fixtures through the extracted `frame-conjunction-core.mjs` (decode + perceptual diff +
classifyPair): frozen=FAIL, same-size per-pixel moved=live, reflow moved=live,
state-unchanged=exempt-hold, oracle pin, seed-sync(30) — closes the INV-9.4 liveness gap
every PR with zero infra. Design ratified by grounded `council-1781567474` (option C).

Accepted-debt (recorded in the gate-coverage registry): the screenshot+callout-crop+scrubber
half of the gate is covered ONLY by this live `frame-gate` CI job, not by the unit test.
