# Viz mount-audit (playbook §0, step 1) — 2026-06-01

Deterministic reachability audit of the 20 student-facing viz components (infra excluded: AlgoStepperShell, HatchDefs, motion-helpers, theme, VizDemoPage, AlgoStepperShellSmoke). "Mounted" = reachable by a **student**, i.e. via `vizRegistry.ts` → `RoutedViz` → the drill flow. `/viz-demo` is a dev gallery (`VizDemoPage` header: "NOT a production drill flow"); `DoorMockups` is this session's throwaway design harness. Both = GHOST re: a student.

## The finding

**1 of 20 wired. 19 ghosts.** `vizRegistry.ts` maps exactly one id (`recursion-tree` → `RecursionTree`). Every other viz renders only in the `/viz-demo` gallery (and/or my throwaway `DoorMockups`). A student in the real drill flow can reach **only the recursion tree.** This is the project's signature failure (2026-05-11 Slice-1 ghost-component) at the viz layer, quantified.

## Table

| component | registry-wired (student) | in /viz-demo | class |
|---|---|---|---|
| RecursionTree | ✅ | ✅ | **WIRED (student-reachable)** |
| ArrayStepper (new this session) | ❌ | ❌ | GHOST (only in throwaway DoorMockups) |
| BayesTree | ❌ | ✅ | GHOST (demo-only) |
| CompareFrames | ❌ | ✅ | GHOST (demo-only) |
| CppVTable | ❌ | ✅ | GHOST (demo-only) |
| DPWastedWork | ❌ | ✅ | GHOST (demo-only) |
| MatrixTransform | ❌ | ✅ | GHOST (demo-only) |
| NPGadget | ❌ | ✅ | GHOST (demo-only) |
| NumLineDirect | ❌ | ✅ | GHOST (demo-only) |
| OsiEncap | ❌ | ✅ | GHOST (demo-only) |
| PageTableWalk | ❌ | ✅ | GHOST (demo-only) |
| ProcessFSM | ❌ | ✅ | GHOST (demo-only) |
| RaceMutex | ❌ | ✅ | GHOST (demo-only) |
| SchedulerGantt | ❌ | ✅ | GHOST (demo-only) |
| SigmaStackedBar | ❌ | ✅ | GHOST (demo-only) |
| SlopeCounter | ❌ | ✅ | GHOST (demo-only) |
| SumPlotTracker | ❌ | ✅ | GHOST (demo-only) |
| TcpCwnd | ❌ | ✅ | GHOST (demo-only) |
| TcpHandshake | ❌ | ✅ | GHOST (demo-only) |
| Tls0RttReplay | ❌ | ✅ | GHOST (demo-only) |

## Implication for the backlog

Per the playbook: a gorgeous component nobody mounts is worth zero. Before any taste/correctness work, the decision per ghost is **keep-and-wire vs delete**. The wiring path is `content/viz-ids.yaml` + `vizRegistry.ts` (a registry entry + a yaml id) — these are the same files the build-pack viz parity self-check reads. But wiring is downstream of the keep/delete call, which is downstream of the taste + correctness score (next step).

## Method note
`git grep`/ripgrep of each component name across `src` excluding self + `*.test.*`, classified by importer (vizRegistry.ts → WIRED; VizDemoPage.tsx → demo-only; DoorMockups.tsx → throwaway). The `mafs`/Plotly/KaTeX/react-pdf surfaces (playbook §6c) are separate and not in this viz-component count.
