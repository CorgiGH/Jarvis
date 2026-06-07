# Phase 3 (Area C — Teaching Engine) — autonomous build log

Owner (Alex) authorized autonomous Phase-3 build 2026-06-07 ("just go do them, get work started on phase 3, I'm going out, keep track of any decisions, get as much done as possible"). Plan-first satisfied: Area C is specced in `docs/superpowers/plans/2026-06-02-master-impl-plan-v2.md:201-209` + frozen signatures in `interface-signatures-lock.md` + `data-model-lock.md`. This file = the running decision + progress log for the unattended run.

## Standing decisions (made autonomously)

- **D-AUTO-1 — per-group selective commit.** Each group commits ONLY its own Phase-3 files (explicit `git add <paths>`, NEVER `-A`/`clean`/branch-switch). Rationale: checkpoint unattended progress; the door landmine (`tutor-web/src/door/`, `main.tsx`, `palettes`) is uncommitted+unbacked-up — a stray `-A` would entangle it. Not pushed (owner didn't ask to push).
- **D-AUTO-2 — build order (dependency chain, master-plan:201-209 + 287):** G1 selection layer (ScaffoldPlanner, PrereqGraph, NextKcSelector) → G2 atomic grade (B1/B2/B3, faithful-gated, 409-if-not-ACTIVE) → G3 read routes (queue/today, mastery, calibration) → G4 session/close + placement + entry_phase + exam_dates → G5 mock-exam SYNC-200 → G6 generator P3-GEN + honesty P3-HONESTY → G7 serve wiring P3-MISC/LADDER/GHOST. PFA/IRT/Thompson + gold-set GATE = DEFERRED (decision-record only, §6.1/§7).
- **D-AUTO-3 — do not rebuild Phase-1 pieces.** `PhaseModel` (PhaseModel.kt), `kc_mastery` phase/entry_phase columns, `attempts` table, `upsertRubricCriterion` (B2) already shipped in Phase 1. Phase 3 builds logic/routes ON them.
- **D-AUTO-4 — TDD + fix-claim discipline per group.** Each task: failing test first → impl → green; class-killing assertions on the dangerous direction (atomicity, faithful-gating, 409). Full Kotlin suite green per group + independent adversarial review before commit.
- **D-AUTO-5 — frozen locks win.** Every signature taken from `interface-signatures-lock.md` (canonical-on-conflict). No wire-shape invention; serve fields paired with a named Phase-5/6 consumer (H16, no ghosts).

## Progress

| Group | Scope | Status | Commit |
|---|---|---|---|
| G1 | ScaffoldPlanner + PrereqGraph + NextKcSelector (+QueueItem/QueueMode forced by frozen sig) | DONE (suite 1189✓, review SHIP) | (this commit) |
| G2 | atomic grade (B1/B2/B3, faithful-gated, 409) | pending | — |
| G3 | queue/today + mastery + calibration | pending | — |
| G4 | session/close + placement + entry_phase + exam_dates | pending | — |
| G5 | mock-exam SYNC-200 | pending | — |
| G6 | P3-GEN generator + P3-HONESTY spot-check | pending | — |
| G7 | P3-MISC-SERVE + P3-LADDER-SERVE + P3-GHOST-FIELDS | pending | — |

## Decision/event detail (appended as the run proceeds)

### G1 — selection layer (DONE, SHIP, suite 1189/0)

New files: PrereqGraph.kt, ScaffoldPlanner.kt, NextKcSelector.kt, QueueItem.kt (+3 tests). Phase-1 types reused, not rebuilt (PhaseModel, KcMastery, phase_plan, PrereqEdge, EdgesFile).

Decisions made by the builder (all forced by frozen signatures, no wire-surface widening):
- **D-G1-1:** Created `QueueItem`+`QueueMode` now (nominally Group-3 wire types) because the FROZEN `NextKcSelector.select` returns `QueueItem?` (lock §C/§D). Built verbatim from lock §C.
- **D-G1-2:** `PrereqGraph` injected into `LockedNextKcSelector` via CONSTRUCTOR (frozen `select(...)` carries no graph param; lock §D says graph memoized in TutorContext).
- **D-G1-3:** `worked_example_first` resolved server-side = `kc.worked_example_first || phase==intro` (QueueItem field non-nullable ⇒ a value is mandatory; interprets data-model-lock "degrade to true for novices" as intro-phase).
- **D-G1-4:** interleave-cap (lock §D 3rd scoring step) uses resolved `QueueMode.name` as the same-shape key (KcCandidate has no shape field); INTERLEAVE_CAP=3, deterministic id tie-break, falls through when no differently-shaped candidate. Class-killers remain on prereq-gating + 0-KC degrade.
- **D-G1-5:** `ScaffoldPlanner` fully-mastered KC ⇒ `emptyList()` (lock §F:147); entry_phase floor trims phases rank<floor; null mastery/entry_phase ⇒ intro floor; unknown phase_plan literal dropped via runCatching.
- **D-G1-6:** `PrereqGraph` cycle-safety = iterative DFS + visited set; self-loops/cycle-backs stripped; cyclic closure degrades deterministically to "all other reachable nodes" (no hang).
