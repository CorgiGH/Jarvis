# `viz-foundation-demo` branch audit — spec-compliance evidence

**Date:** 2026-05-20
**Branch audited:** `viz-foundation-demo` (68 commits) — worktree at `C:\Users\User\jarvis-viz-audit\`
**Audited against:** `docs/superpowers/specs/2026-05-17-jarvis-full-tutor-redesign-design.md` §6.3 / §6.4 / §6.5
**Viz dir:** `tutor-web/src/components/viz/` (24 files)
**Scope:** static spec-compliance audit. No tests/builds/tsc run. Single file written: this report.

---

## TL;DR

The branch is **far stronger than the spec assumed**. The spec said the viz layer was a "clean slate, NOT YET BUILT." In reality the branch has a spec-grade shared foundation (`theme.ts`, `AlgoStepperShell.tsx`, `motion-helpers.tsx`) and **15 fully-built interactive viz primitives**, including **all 6 ⭐ world-firsts**. Color discipline is essentially perfect — a codebase-wide grep found **zero** blue `#3b82f6`, **zero** amber `#f59e0b`, **zero** Tailwind blue/amber classes. The only non-theme literal anywhere is pure white `#fff`.

But two systemic gaps block a clean "adopt as-is":

1. **V2 violation, branch-wide.** `AlgoStepperShell` ships a working autoplay timer with a play/pause button. The spec is explicit: "V2 Manual advance only (no autoplay)." Every one of the 14 Shell-based components inherits this. This is a one-file fix but it touches the contract.
2. **V3 violation, branch-wide.** The Shell supports exactly **one** `predictionGate` for the whole component. Spec V3 wants "a prediction gate every 3-5 frames." No built component even passes the single gate it could (only the `MatrixTransform` demo wires one). Prediction-gating is effectively **unbuilt** despite the plumbing being half-there.

**Recommendation: (b) adopt-with-rework.** The rework is concentrated in `AlgoStepperShell.tsx` (the V2 + V3 + AlgoVizProps contract fixes) and is small relative to the ~10,000 LOC of correct, on-palette primitive code it would be insane to rebuild. Full per-component rework list in §8.

---

## 1. File inventory

All paths under `tutor-web/src/components/viz/`. LOC from `wc -l`.

| File | LOC | Renders |
|---|---|---|
| `theme.ts` | 18 | Brutalist token constants (INK/PAPER/ACCENT/strokes/fonts). |
| `motion-helpers.tsx` | 200 | `motion`-based SVG animation wrappers: `TweenText`, `FadeText`, `DrawLine`, `DrawPath`, `PopIn`; re-exports `motion` + `AnimatePresence`. |
| `AlgoStepperShell.tsx` | 467 | Reusable scrub/predict/ARIA/keyboard/share shell. All 14 frame-based viz wrap it. |
| `AlgoStepperShellSmoke.tsx` | 73 | Smoke harness — an 8-frame counter that subclasses the Shell. Not a roster viz. |
| `NumLineDirect.tsx` | 116 | PS basis — draggable μ marker on a number line with sample ticks. |
| `SumPlotTracker.tsx` | 95 | PS basis — Σ\|xᵢ−μ\| curve with a marker tracking μ. |
| `SlopeCounter.tsx` | 62 | PS basis — left/right sample-count chips + slope readout (HTML, not SVG). |
| `SigmaStackedBar.tsx` | 82 | PS basis — stacked absolute-deviation bar (HTML divs, not SVG). |
| `CompareFrames.tsx` | 154 | PS basis — mean-vs-median estimator scatter (visx + hand SVG). |
| `MatrixTransform.tsx` | 288 | ALO-3 — eigenvector grid-warp; rays + eigenvectors animate t=0→1. |
| `RecursionTree.tsx` | 458 | PA-1 — fib(5) call-stack cards + recursion tree side-by-side. |
| `DPWastedWork.tsx` | 476 | PA-3 ⭐ — DP table vs naïve recursion tree with duplicate-subtree shading. |
| `NPGadget.tsx` | 469 | PA-7 ⭐ — 3-SAT → CLIQUE bidirectional-iff reduction animator. |
| `BayesTree.tsx` | 798 | PS-2 ⭐ — Bayes prob-tree + joint-probability area square + posterior. |
| `CppVTable.tsx` | 1043 | POO-1 ⭐ + POO-4 ⭐ — vtable virtual dispatch (phase 1) + shared_ptr ref-count cycle leak (phase 2). |
| `ProcessFSM.tsx` | 767 | SO-1 — process lifecycle FSM with explicit ZOMBIE state + process table. |
| `SchedulerGantt.tsx` | 1204 | SO-2 — FCFS/SJF/SRTF/RR/MLFQ Gantt comparison + MLFQ queues + summary bars. |
| `RaceMutex.tsx` | 991 | SO-3 — two-thread race condition (lost update) then mutex serialization. |
| `PageTableWalk.tsx` | 676 | SO-4 ⭐-adjacent — VA→TLB→page-table→phys walk; TLB miss/hit, page fault, COW-after-fork. |
| `OsiEncap.tsx` | 989 | RC-1 — OSI 7-layer encapsulation + MTU fragmentation + reassembly. |
| `TcpHandshake.tsx` | 1189 | RC-2 — TCP 3-way handshake + SYN-flood attack + SYN-cookies defense. |
| `TcpCwnd.tsx` | 536 | RC-3 ⭐ — TCP congestion window (Reno) cwnd-over-time + AIMD. |
| `Tls0RttReplay.tsx` | 515 | RC-6 ⭐ — TLS 1.3 0-RTT resumption + replay attack + mitigations. |
| `VizDemoPage.tsx` | 255 | Demo gallery page mounting all 20 primitives in `<section>` tiles. |

Total ~12,000 LOC; ~10,500 of it production-relevant primitive/shell/helper code.

---

## 2. Base-vocabulary coverage (spec §6.4 — 12 base components)

The spec's 12 "base component vocabulary" is a set of *reusable primitives*. The branch did **not** build them as named generic primitives; it built **concrete roster viz** that internally contain equivalent structures. Verdict per base component:

| Base component | Status | Evidence |
|---|---|---|
| AlgoStepper | **EXISTS (as shell)** | `AlgoStepperShell.tsx` — the reusable scrub shell. Named `AlgoStepperShell`, not `AlgoStepper`. |
| ProcessTree | **MISSING** | No `{id,ppid,cmd}[]` process-tree primitive. `ProcessFSM.tsx` is a state-machine, not a pid hierarchy tree. |
| FilesystemTree | **MISSING** | No filesystem/inode tree. Spec roster SO-6 also missing. |
| PermissionsGrid | **MISSING** | No ugo×rwx grid. Spec roster SO-7 also missing. |
| BayesGrid | **PARTIAL** | `BayesTree.tsx` ships the Bayes *area square* (joint-probability 100% square) which is the BayesGrid concept, fused into the tree viz. Not a standalone `{prior,sensitivity,specificity}` primitive. |
| MatrixTransform | **EXISTS** | `MatrixTransform.tsx` — named exactly, ALO-3. |
| DPTable | **PARTIAL** | `DPWastedWork.tsx` contains a DP table with dependency arrows, fused into the wasted-work viz. Not standalone. |
| RecursionTree | **EXISTS** | `RecursionTree.tsx` — named exactly, PA-1. |
| UMLClass | **MISSING** | No UML class diagram primitive. (No POO-2/3/5/6/7 either.) |
| PacketFlow | **PARTIAL** | `Tls0RttReplay.tsx` + `TcpHandshake.tsx` both implement swimlane packet flow inline. No extracted `PacketFlow` primitive. |
| Sparkline | **MISSING** | No Tufte sparkline primitive — and R9 names sparklines the *primary* data viz. Notable gap. |
| SmallMultiples | **MISSING** | No small-multiples grid. |

**Summary: 3 of 12 exist as named primitives (AlgoStepper-as-Shell, MatrixTransform, RecursionTree); 4 exist fused-into-roster-viz (BayesGrid, DPTable, PacketFlow ×2); 5 fully missing (ProcessTree, FilesystemTree, PermissionsGrid, UMLClass, Sparkline, SmallMultiples).** The branch optimized for shipping impressive roster viz over building the extractable primitive library the spec's library matrix prescribes.

---

## 3. 36-viz roster coverage (spec §6.4)

The spec roster is numbered 1-44 in the doc body but labelled PA-1..8, PS-1..7, POO-1..7, ALO-1..8, SO-1..8, RC-1..6 (44 labels; spec calls it "36-viz" — the count is loose).

| Roster entry | Status | File |
|---|---|---|
| PA-1 ⭐ Recursion stack+tree+code | **PRESENT** | `RecursionTree.tsx` |
| PA-2 Greedy exchange-argument duel | MISSING | — |
| **PA-3 ⭐ DP wasted-work overlay** | **PRESENT** | `DPWastedWork.tsx` |
| PA-4 ⭐ Graph traversal DFS-tree morph | MISSING | — |
| PA-5 Dijkstra-fails-on-negative | MISSING | — |
| PA-6 MST cut-property | MISSING | — |
| **PA-7 ⭐ NP-completeness reduction gadget** | **PRESENT** | `NPGadget.tsx` |
| PA-8 Complexity wall-clock translator | MISSING | — |
| PS-1 Sample-space grid | MISSING | — |
| PS-2 ⭐ Bayes tree + area-overlay | **PRESENT** | `BayesTree.tsx` |
| PS-3 PDF↔CDF sweep | MISSING | — |
| PS-4 CLT + Cauchy counterexample | MISSING | — |
| PS-5 ⭐ Hypothesis testing dual-distribution | MISSING | — |
| PS-6 MLE likelihood landscape | MISSING | — |
| PS-7 Markov chain mixing | MISSING | — |
| **POO-1 ⭐ C++ vtable dispatch arrow path** | **PRESENT** | `CppVTable.tsx` (phase 1) |
| POO-2 Polymorphism trace chain | MISSING | — |
| POO-3 Memory layout | MISSING | — |
| **POO-4 ⭐ Smart-pointer ref-count + cycle** | **PRESENT** | `CppVTable.tsx` (phase 2) |
| POO-5 Templates instantiation | MISSING | — |
| POO-6 Inheritance-vs-composition | MISSING | — |
| POO-7 Exception stack unwinding | MISSING | — |
| ALO-1 Vector spaces | MISSING | — |
| ALO-2 ⭐ Linear transformation grid-warp | MISSING (partly subsumed by ALO-3) | — |
| ALO-3 ⭐ Eigenvalue stretch | **PRESENT** | `MatrixTransform.tsx` |
| ALO-4 Determinant signed-volume | MISSING | — |
| ALO-5 Induction dominoes | MISSING | — |
| ALO-6 Eulerian + planarity | MISSING | — |
| ALO-7 Pascal's triangle | MISSING | — |
| ALO-8 Number theory clock + GCD | MISSING | — |
| SO-1 Process FSM with zombie | **PRESENT** | `ProcessFSM.tsx` |
| SO-2 Scheduler Gantt | **PRESENT** | `SchedulerGantt.tsx` |
| SO-3 Threads vs processes / race + mutex | **PRESENT** | `RaceMutex.tsx` |
| SO-4 ⭐ Page table walk + TLB + fault + COW | **PRESENT** | `PageTableWalk.tsx` |
| SO-5 Memory layout (OS perspective) | MISSING | — |
| SO-6 Filesystem inode + links | MISSING | — |
| SO-7 Permissions ugo×rwx grid | MISSING | — |
| SO-8 IPC (pipes/shm/signals) | MISSING | — |
| RC-1 OSI encapsulation + MTU frag | **PRESENT** | `OsiEncap.tsx` |
| RC-2 TCP 3-way handshake + SYN flood | **PRESENT** | `TcpHandshake.tsx` |
| **RC-3 ⭐ TCP cwnd + bufferbloat** | **PRESENT (partial)** | `TcpCwnd.tsx` |
| RC-4 BGP route propagation + hijack | MISSING | — |
| RC-5 DNS recursive + DNSSEC + Kaminsky | MISSING | — |
| **RC-6 ⭐ TLS 1.3 + 0-RTT replay** | **PRESENT** | `Tls0RttReplay.tsx` |

**Count: 14 of 44 roster entries built** (PA-1, PA-3, PA-7, PS-2, POO-1, POO-4, ALO-3, SO-1, SO-2, SO-3, SO-4, RC-1, RC-2, RC-3, RC-6 — note CppVTable covers two entries so 15 labels across 14 files).

### The 6 ⭐ world-firsts — all 6 PRESENT

| World-first | File | Notes |
|---|---|---|
| PA-3 DP wasted-work overlay | `DPWastedWork.tsx` | DP table + naïve tree side-by-side, duplicate subtrees shaded by call count via `dupShade()`. Matches spec intent. |
| PA-7 NP reduction gadget | `NPGadget.tsx` | 3-SAT→CLIQUE; frames 3-6 do forward direction, 7-9 reverse — bidirectional iff is present. |
| POO-1 C++ vtable dispatch | `CppVTable.tsx` phase 1 | Full `a→object→vptr→vtable→slot→fn` arrow path animated across frames 0-6. |
| POO-4 smart-ptr ref-count cycle | `CppVTable.tsx` phase 2 | `use_count` counters, cycle creation, leak badge, weak_ptr fix — frames 7-14. |
| RC-3 TCP cwnd | `TcpCwnd.tsx` | cwnd-over-time + AIMD + slow-start/cong-avoidance. **Partial:** see §3-note below. |
| RC-6 TLS 1.3 0-RTT replay | `Tls0RttReplay.tsx` | 1-RTT → ticket → 0-RTT → replay → 3 mitigations. Matches spec. |

**RC-3 scope shortfall (not a world-first blocker, but flag it):** the spec demands "Tahoe/Reno/CUBIC/BBR variants + **bufferbloat** (AQM = CoDel + FQ-CoDel flat-latency contrast)." `TcpCwnd.tsx` ships **Reno only** (`TcpCwnd.tsx:14` `type Mode` has 3 modes; `:87` mentions CUBIC only in a closing text label). There is **no bufferbloat panel, no AQM, no CoDel/FQ-CoDel**. The "world-first at this fidelity" claim in §6.8 rests on the bufferbloat/AQM contrast — that part is unbuilt.

---

## 4. V1-V20 compliance — per-component

### Branch-wide findings (apply to every Shell-based component)

These are inherited from `AlgoStepperShell.tsx` and `motion-helpers.tsx`, so they are scored once here and not repeated 14×:

- **V2 VIOLATION (autoplay present).** `AlgoStepperShell.tsx:39` `DEFAULT_AUTOPLAY_MS = 1400`; `:124-133` a `setInterval` advances frames automatically; `:332-339` a `▶ play / ⏸ pause` button. Spec V2: "Manual advance only (no autoplay)." Autoplay is opt-in (defaults to off — `playing` starts `false` at `:85`) and is disabled under reduced-motion, but the *capability and the play button exist*. Strict V2 reading = violation. Every frame-based component inherits it.
- **V3 VIOLATION (single gate, no cadence).** `AlgoStepperShell.tsx:27-37` `AlgoStepperShellProps` exposes `predictionGate?: PredictionGate` — **singular**. Spec V3 wants "a prediction gate every 3-5 frames" and the AlgoVizProps contract specifies `predictionGates?: Map<number, PredictionGate>` (a *map*, keyed by frame index). The Shell cannot place a gate at frame N. The single gate it does support only blocks advance from the *first* frame (`:86` `predictionLocked` initialized once). No built component passes even that single gate (see per-component table).
- **V8 PASS.** `motion-helpers.tsx:2-8` and `AlgoStepperShell.tsx:12` import from `motion/react` — correct `motion@12.x` package. **Zero** imports of deprecated `motion-one` anywhere (grep confirmed).
- **V6/V12 PASS (palette).** Codebase-wide grep for hex literals found **only** `theme.ts` constants + `#fff`/`#ffffff` (pure white). **Zero** `#3b82f6`, **zero** `#f59e0b`, **zero** other accent colors. Grep for Tailwind `blue/amber/sky/indigo/cyan/orange` classes and `rgb(`/`hsl(`: **zero matches**. White-as-fill is used heavily for "empty/unfilled" cells (e.g. `RecursionTree.tsx:235`). The spec theme has only INK/PAPER/ACCENT — strictly, `#fff` is a 4th value. Magnitude differs from paper `#f5f5f0` only slightly; it reads as "unfilled" vs PAPER "inert." Minor: arguably should be PAPER or a hatch. Not a blue/amber-class violation.
- **V5/V19 PARTIAL.** Every component supplies per-frame `aria` strings (the `Frame.aria` field) and the Shell renders them in an `aria-live="polite"` region (`AlgoStepperShell.tsx:439-446`) — V5 satisfied. Every component supplies SVG `<title>`+`<desc>` via the Shell's `title`/`desc` props (`:290-291`) — V19 long-description satisfied at the diagram level. **Gap:** the live region is positioned `left: -9999` (`:443`) — old-school off-screen, works for SR but not the modern `sr-only` clip pattern the spec's ARIA recipe shows (`data-testid="viz-narrator"`). The Shell's region testid is `${prefix}-live`, not `viz-narrator`. Cosmetic.
- **V16 PASS.** `AlgoStepperShell.tsx:41-45` `readReducedMotion()`; `:254-258` a `@media (prefers-reduced-motion: reduce)` CSS block kills the fade animation; `:266` `<MotionConfig reducedMotion="user">` makes every `motion` child honor it. Solid.
- **V7/V18 PASS.** Everything renders as hand-authored `<svg>` with real `<text>` elements — no `<canvas>`, no `<foreignObject>` text, no HTML-overlay labels inside the SVG viz. (Exceptions: `SlopeCounter.tsx` and `SigmaStackedBar.tsx` are pure HTML/CSS components — see their rows.)
- **V17 PARTIAL.** `AlgoStepperShell.tsx:206-222` `onKey` handles `ArrowRight/K` (fwd), `ArrowLeft/J` (back), `Space` (play), `R` (reset). Spec §6.5 verb 1 also requires `Home`/`End` (first/last frame) — **not handled**. So keyboard parity is incomplete. Applies branch-wide.
- **V20 PARTIAL.** Reduced-motion mode degrades animation to instant snaps, and a static frame still renders — so there *is* a static fallback per figure. But it is the same interactive SVG with motion off, not a distinct "static fallback" artifact. Acceptable under a generous V20 reading.

### Per-component table

Verdicts: **SPEC-READY** = compliant modulo the branch-wide V2/V3 shell fixes; **NEEDS-REWORK** = component-specific violation beyond the shell; **PARTIAL-STUB** = incomplete vs its roster spec.

| Component | Component-specific violations found | Verdict |
|---|---|---|
| `theme.ts` | None. Adds 4 extra constants beyond spec (see §5) — additive, not a violation. | SPEC-READY |
| `motion-helpers.tsx` | `TweenText` (`:25-49`) and `DrawLine`/`DrawPath` interpolate values smoothly. That is fine for *intra-frame* entry animation, but `TweenText` tweening a numeric *between frame snapshots* edges toward V1 ("discrete frames not interpolation"). Used pervasively (BayesTree, TcpCwnd, CppVTable). Borderline — frames are still discrete; only the in-between paint animates. | SPEC-READY |
| `AlgoStepperShell.tsx` | V2 autoplay (`:124`,`:332`); V3 single gate not Map (`:33`); no Home/End keys (`:206`); AlgoVizProps contract mismatch (see §6). | NEEDS-REWORK |
| `AlgoStepperShellSmoke.tsx` | Test harness, not a roster viz. No prediction gate (it's a smoke). Fine as-is. | SPEC-READY (n/a) |
| `NumLineDirect.tsx` | Not Shell-based — standalone. `onPointerMove` drag continuously updates μ — this is the V3 "Manipulate parameters" verb done well (Tangle-style). No frames so V1/V2/V3-gate n/a. ARIA: SVG has `data-testid` but **no `role="img"`, no `<title>`/`<desc>`** (`:85-92`) → V19 fail. No `aria-live` narration of μ changes → V5 fail. | NEEDS-REWORK |
| `SumPlotTracker.tsx` | Standalone, display-only. SVG at `:87` has **no `role`, no `<title>`/`<desc>`, no aria-live** → V5/V19 fail. `interpolateMarker` (`:44`) is fine. | NEEDS-REWORK |
| `SlopeCounter.tsx` | **Pure HTML `<div>`/`<span>`, not SVG** → V7/V18 fail by construction (though spec V7 is "SVG over canvas" — HTML text is arguably worse than neither). No `role`, no aria-live. It is a tiny readout chip; could stay HTML if reclassified as a non-viz UI widget. | NEEDS-REWORK |
| `SigmaStackedBar.tsx` | **Pure HTML `<div>` bar, not SVG** → V7/V18 fail. Uses `title=` attributes for segment tooltips — not SR-grade. `:9` cycles INK/ACCENT by `i % 4` with opacity 0.55 alternation — this is "color/opacity as **category**" which contradicts **V12** ("yellow = focus not category") and **V6** ("hatching density for magnitude" — should be hatch, not opacity-cycling). Clear violation. | NEEDS-REWORK |
| `CompareFrames.tsx` | `testid="compare-frames-plotly"` (`:52`) — stale name, no Plotly here (it's visx). `:102` `style={{transition:"all 600ms cubic-bezier..."}}` — CSS transition tween between estimator modes, mild V1 smell. SVG has `role="img"` + `aria-label` but **no `<title>`/`<desc>` id pair, no aria-live** → V19 partial. Mode switch via `▶ animate frames` button is a 3-state toggle, not a gated stepper. | NEEDS-REWORK |
| `MatrixTransform.tsx` | Shell-based. The **only** component that actually wires a `predictionGate` (`VizDemoPage.tsx:82-90`). `:26` declares a `liveRegionId?` prop marked `@deprecated` and ignored — dead prop (the §`Underscore-dead-prop` smell from CLAUDE.md, here a deprecated-prop variant). 21 frames at t-steps of 0.05 — `lerpMat` interpolates the matrix, but each frame is discrete so V1 holds. | SPEC-READY |
| `RecursionTree.tsx` | Shell-based. No prediction gate passed. `useNodePositionMVs` (`:135`) animates node x/y between frames via `motionValue`+`animate` — discrete frames preserved, motion is entry-only. Clean. | SPEC-READY |
| `DPWastedWork.tsx` ⭐ | Shell-based. No prediction gate — a PA-3 world-first viz with zero predict-then-reveal is a pedagogy miss vs V3. Duplicate shading via `dupShade()` opacity ramp — this is **V6-correct** (opacity ramp = magnitude of waste, legitimate use). | SPEC-READY |
| `NPGadget.tsx` ⭐ | Shell-based. No prediction gate. 12 frames, bidirectional. `FooterMessage` 2-line wrap. Clean. | SPEC-READY |
| `BayesTree.tsx` ⭐ | Shell-based. No prediction gate (PS-2 is a prime predict-then-reveal candidate — "guess the posterior"). `pctStr` defined then `void`-ed (`:104`,`:786`) — dead code. Heavy `TweenText` use across frames — numbers tween between snapshots, borderline V1 but frames discrete. Spec PS-2 wants **draggable prior**; here prior only changes at fixed frames 7/8 — the "Manipulate" verb is **not** implemented (no drag). | NEEDS-REWORK |
| `CppVTable.tsx` ⭐⭐ | Shell-based, covers POO-1 + POO-4. No prediction gate. `_SVG_W`/`_SVG_H` (`:359-362`) dead aliases, `void`-ed. `FRAME_COUNT` exported as literal `15` (`:352`) while `FRAMES` has 15 entries — coincidentally correct but brittle (`buildFrames()` length not used). Otherwise the arrow-routing is elaborate and on-palette. | SPEC-READY |
| `ProcessFSM.tsx` | Shell-based. No prediction gate. `:343-365` defines `fsm-arrow-accent` marker whose path `fill={INK}` — identical to the ink marker (accent marker is not actually yellow). Minor: an "accent" asset that isn't accent-colored. Uses a real SVG `<pattern>` hatch for ZOMBIE (`:366-375`) — good V6. Clean otherwise. | SPEC-READY |
| `SchedulerGantt.tsx` | Shell-based. No prediction gate. `jobOpacity()` (`:520`) maps J1/J2/J3/J4 to **distinct opacity tiers** — opacity used to **distinguish 4 categories** of job. This is the **V12 violation** ("yellow = focus not category") and **V6** ("hatching density for magnitude," not category) — jobs are categories, not magnitudes. Should be 4 hatch patterns, not 4 opacities. Real violation. | NEEDS-REWORK |
| `RaceMutex.tsx` | Shell-based. No prediction gate (SO-3 race is a textbook predict-the-bug moment). `ThreadPane` blocked-status indicator uses `opacity:[1,0.45,1]` `repeat:Infinity` (`:643-650`) — an **infinite pulsing animation**. That is a soft V2 issue (continuous motion, not frame-gated) and an accessibility concern; it is *not* gated by reduced-motion at the component level (the Shell's `MotionConfig` should catch it, but an infinite loop is exactly what V2's spirit forbids). Flag. | NEEDS-REWORK |
| `PageTableWalk.tsx` | Shell-based. No prediction gate. 20 frames across 4 phases. Cell fills animate INK/ACCENT/PAPER on highlight — palette-clean. No component-specific violation. | SPEC-READY |
| `OsiEncap.tsx` | Shell-based. No prediction gate. Packet glyphs all `fill={ACCENT}` (`:641`) — every packet is yellow regardless of focus. That makes **yellow the packet category color**, not the focus color → **V12 violation**. The active *layer row* also turns ACCENT (`:692`), so on a fragment frame you have yellow packets on a yellow row — focus is lost. Flag. | NEEDS-REWORK |
| `TcpHandshake.tsx` | Shell-based. No prediction gate. `MessageArrow` (`:642-646`): SYN-ACK and any spoofed message are drawn ACCENT *unconditionally* — yellow encodes message-kind/spoofed-ness (a **category**), not focus → **V12 violation**. Spoofed backlog slots also fill ACCENT (`:1061`). Flag. | NEEDS-REWORK |
| `TcpCwnd.tsx` ⭐ | Shell-based. No prediction gate. `modeColor()` (`:112-115`): the cwnd trajectory line is ACCENT during SLOW_START, INK during CONG_AVOIDANCE — yellow encodes the **mode category** → **V12 violation**. Also the RC-3 **scope shortfall**: Reno-only, no CUBIC/BBR, no bufferbloat/AQM (see §3). | NEEDS-REWORK + PARTIAL-STUB |
| `Tls0RttReplay.tsx` ⭐ | Shell-based. No prediction gate. `MessageRow` strokes replay messages ACCENT (`:143`) and `fillColor:"accent"` is a per-message data field (`:34-39`) — yellow encodes "this is a replay/PSK message" (**category**) → **V12 violation**, though here it doubles as danger-highlight so it is the least-bad instance. Flag. | NEEDS-REWORK |
| `VizDemoPage.tsx` | Gallery harness. Not a viz. On-palette. `MatrixTransform` here is the only call site passing a `predictionGate`. Fine as a demo page. | SPEC-READY (n/a) |

**Tally:** 8 SPEC-READY (modulo shell fixes), 13 NEEDS-REWORK, 1 PARTIAL-STUB (TcpCwnd is both). The dominant component-specific defect is **V12 — yellow used as a category color** in the 5 network/OS viz that have a natural "type" axis (OsiEncap, TcpHandshake, TcpCwnd, Tls0RttReplay) plus SchedulerGantt and SigmaStackedBar using opacity as a category axis. This is a *consistent, mechanical* defect, not 13 different bugs.

---

## 5. `theme.ts` check (spec §6.4 "Brutalist-theme constants")

`theme.ts` is 18 lines. Spec requires 8 exports. Actual:

| Spec-required export | Spec value | `theme.ts` actual | Match? |
|---|---|---|---|
| `INK` | `'#0a0a0a'` | `"#0a0a0a"` (`:5`) | ✅ exact |
| `PAPER` | `'#f5f5f0'` | `"#f5f5f0"` (`:6`) | ✅ exact |
| `ACCENT` | `'#facc15'` | `"#facc15"` (`:7`) | ✅ exact |
| `STROKE_DEFAULT` | `1` | `1` (`:9`) | ✅ exact |
| `STROKE_FOCUS` | `2` | `2` (`:10`) | ✅ exact |
| `HATCH_LIGHT` | `'url(#hatch-light)'` | **MISSING** | ❌ not exported |
| `HATCH_DENSE` | `'url(#hatch-dense)'` | **MISSING** | ❌ not exported |

**Deviations:**
1. **`HATCH_LIGHT` and `HATCH_DENSE` are absent.** This is consequential, not cosmetic — V6 says "hatching density for magnitude," and the absence of shared hatch tokens is *why* components reached for opacity-as-magnitude / opacity-as-category instead (SchedulerGantt, SigmaStackedBar, DPWastedWork). `ProcessFSM.tsx:366` had to hand-roll its own `<pattern id="fsm-zombie-hatch">` locally. Adding the two hatch `<defs>` + tokens is a prerequisite for fixing the V6/V12 defects cleanly.
2. **Extra exports (additive, not violations):** `STROKE_THICK=4` (`:11`), `FONT_FAMILY` (`:13`), `FONT_SIZE_TINY/LABEL/BODY/VALUE` (`:15-18`). These are reasonable and used widely; the spec didn't forbid extra tokens. `FONT_FAMILY` correctly resolves JetBrains Mono (R1/V13).

**Verdict:** 5/7 exact match. The 2 missing hatch tokens are the only real gap, and they are load-bearing for the §4 V6/V12 rework.

---

## 6. `AlgoStepperShell.tsx` check

### §6.5 — the 6 interaction verbs

| Verb | Spec requirement | Shell implementation | Status |
|---|---|---|---|
| 1. Scrub | timeline + ←/→/J/K/Space/Home/End + speed slider | `<input type="range">` scrubber (`:314-329`); keys ←/→/J/K/Space/R (`:206-222`); **NO Home/End**; **NO speed slider** (autoplay speed is a fixed `autoplayMsPerFrame` prop, not a UI slider). | **PARTIAL** |
| 2. Predict-then-reveal | gate blocks advance until prediction submitted | `predictionGate` prop + `predictionLocked` state + `predict-gate` UI (`:390-438`); blocks all controls while locked. Works — but **only one gate, only at the start** (cannot gate frame N). | **PARTIAL** |
| 3. Manipulate parameters | every numeric input draggable in-place (Tangle-style) | **ABSENT from the Shell.** The Shell has no parameter-drag concept. Individual components (`NumLineDirect`) implement drag themselves, outside the Shell. | **MISSING** |
| 4. Counterfactual fork | "what if I'd picked the other?" ghost-branch | **ABSENT.** No fork/branch mechanism anywhere in the Shell. (`NPGadget`/`RaceMutex` show counterfactuals as scripted frames, not interactive forks.) | **MISSING** |
| 5. Layer toggle | "show me the proof" overlay, off by default | **ABSENT.** No layer-toggle control. | **MISSING** |
| 6. Save state / share link | URL hash encodes state; tutor can deep-link | `parseHashIdx`/`writeHashIdx` (`:47-59`); `#${prefix}-idx-${N}` written on every step (`:139-141`); `🔗 share` button + `onShare` callback (`:366-373`,`:202-204`). | **PRESENT** |

**Shell verbs: 2 of 6 fully present (Scrub-partial→ counts as partial, Save-state present), 1 partial (Predict), 3 missing (Manipulate, Counterfactual, Layer-toggle).** The Shell implements the *linear playback* half of the interaction grammar well and the *exploratory* half (verbs 3-5) not at all. For a "Gate 1 foundation" those three are the expensive, differentiating verbs.

### AlgoVizProps contract match (spec §6.4)

Spec contract:
```ts
interface AlgoVizProps<Frame> {
  frames: Generator<Frame>;            // OR Frame[] precomputed
  initialStep?: number;
  onStep?: (idx: number) => void;
  predictionGates?: Map<number, PredictionGate>;
  liveRegionId: string;
}
```

Shell's actual `AlgoStepperShellProps<S>` (`AlgoStepperShell.tsx:27-37`):
```ts
interface AlgoStepperShellProps<S> {
  title: string;                       // not in spec contract
  desc: string;                        // not in spec contract
  frames: Frame<S>[] | (() => Generator<Frame<S>>);
  renderFrame: (frame, idx) => ReactNode;   // not in spec contract
  predictionGate?: PredictionGate;     // SINGULAR — spec wants Map
  voiceMap?: Record<number,string>;    // not in spec contract
  autoplayMsPerFrame?: number;         // not in spec; enables V2-violating autoplay
  onShare?: (hashState) => void;
  testIdPrefix?: string;
}
```

**Deviations from the AlgoVizProps contract:**

1. `frames` — ✅ matches (accepts `Frame[]` or generator-thunk). Note the spec types it `Generator<Frame>` and the Shell types it `() => Generator<Frame<S>>` (a thunk, not a live generator) — minor, the thunk form is actually safer for React.
2. `initialStep?` — ❌ **MISSING.** The Shell only takes initial index from the URL hash (`:78-82`), there is no `initialStep` prop. Spec contract requires it.
3. `onStep?` — ❌ **MISSING.** No per-step callback. The Shell never notifies the parent of frame changes. This blocks the tutor "deep-link / observe viz state" integration.
4. `predictionGates?: Map<number, PredictionGate>` — ❌ **MISMATCH.** Shell has singular `predictionGate?: PredictionGate`. This is the V3 root cause.
5. `liveRegionId` — ❌ **MISSING / inverted.** Spec makes the parent pass a `liveRegionId` (the parent owns the live region). The Shell instead *owns* the live region internally (`:439-446`, id `${prefix}-live`) and does not accept the prop. `MatrixTransform.tsx:26` even declares a `liveRegionId?` prop and marks it `@deprecated`/ignored. Functionally the Shell's approach is fine (arguably better), but it does not match the contract — a caller written to the spec contract would not compile.
6. Shell *adds* `title`, `desc`, `renderFrame`, `voiceMap`, `autoplayMsPerFrame`, `testIdPrefix` — none in the spec contract. `renderFrame` is necessary and good (the spec's contract is under-specified — it never says how a frame paints). `voiceMap` is a sensible extension. `autoplayMsPerFrame` is the V2-violating one.

**Verdict:** the Shell is a *capable superset-and-subset* of the spec contract — it adds the rendering hook the spec forgot, but it is missing `initialStep`, `onStep`, the `predictionGates` *map*, and the `liveRegionId` prop. A component written against the literal spec `AlgoVizProps` would not drop into this Shell unmodified. The contract needs a deliberate reconciliation pass (see §8).

---

## 7. Dependency check (`tutor-web/package.json`)

| Spec requirement | package.json | Status |
|---|---|---|
| `motion@12.x` (NOT `motion-one`) | `"motion": "^12.0.0"` (`:25`) | ✅ present, correct package. No `motion-one` anywhere. |
| `d3-scale` | `"d3-scale": "^4.0.2"` (`:21`) | ✅ |
| `d3-shape` | `"d3-shape": "^3.2.0"` (`:20`) | ✅ |
| `d3-hierarchy` | `"d3-hierarchy": "^3.1.2"` (`:19`) | ✅ |
| `d3-array` | `"d3-array": "^3.2.4"` (`:18`) | ✅ |
| `d3-format` | `"d3-format": "^3.1.0"` (`:18`) | ✅ |
| `@visx/hierarchy@3.12.x` | `"@visx/hierarchy": "^3.12.0"` (`:14`) | ✅ |
| `@visx/network@3.12.x` | `"@visx/network": "^3.12.0"` (`:15`) | ✅ |
| `@visx/shape@3.12.x` | `"@visx/shape": "^3.12.0"` (`:16`) | ✅ |
| `@visx/scale@3.12.x` | `"@visx/scale": "^3.12.0"` (`:16`) | ✅ |
| `@visx/group@3.12.x` | `"@visx/group": "^3.12.0"` (`:13`) | ✅ |
| `mafs@0.21.x` | `"mafs": "^0.21.0"` (`:24`) | ✅ |

**All 12 spec-required deps present at the spec-pinned versions.** The dependency manifest is a clean match.

### Plotly status

`plotly.js-dist-min` **is still a dependency** — `package.json:27` `"plotly.js-dist-min": "^3.5.1"`, plus `:31` `"react-plotly.js": "^2.6.0"`. Spec §6.4 performance table marks Plotly **"(deprecated) → DEPRECATE → @visx/shape."**

Files still importing Plotly (grep across `tutor-web/src`):
- `src/components/PlotlyEmbed.tsx` — lazy-`import()`s `plotly.js-dist-min` (`:8`,`:60`) + `react-plotly.js/factory` (`:7`).
- `src/components/ChatPane.tsx` — imports `PlotlyEmbed` + `parsePlotly` (`:13-14`).
- `src/lib/plotlyParse.ts` — parses ```plotly fenced blocks.
- `src/setupTests.ts:56-60` — mocks both Plotly packages for jsdom.
- Tests: `__tests__/PlotlyEmbed.test.tsx`, `__tests__/plotlyParse.test.ts`, `__tests__/ChatPaneEnvelopes.test.tsx`.

**Important nuance:** Plotly is **not** used by any file in the `viz/` directory. It lives entirely in the **chat pane** path (`ChatPane` renders ```plotly fenced blocks the LLM emits). `CompareFrames.tsx:52` has a *stale* `data-testid="compare-frames-plotly"` but the component itself uses visx, not Plotly — a naming leftover, not a real dependency. So: Plotly is correctly *absent from the viz layer*, but the spec's "DEPRECATE Plotly" directive is **not done** — it is still wired into the chat-prose rendering path. That is out of scope for the viz-layer Gate-1 decision but should be tracked as separate debt.

---

## 8. Merge-strategy recommendation

### Recommendation: **(b) adopt-with-rework**

Rebuilding (option d) would discard ~10,500 LOC of palette-clean, structurally-sound, all-6-world-firsts-present viz code to fix what is fundamentally **one shell-level contract bug + one mechanical palette defect repeated across 6 files.** Cherry-picking foundation only (option c) throws away the 14 working roster viz for no reason — the spec wanted those viz built anyway. Adopting whole (option a) ships a documented V2 violation, a non-existent V3, and an AlgoVizProps contract that won't accept spec-written components.

The branch becomes the Gate-1 foundation **after the rework below.** The rework is small and concentrated.

### Tier 1 — `theme.ts` + `AlgoStepperShell.tsx` (do first, unblocks everything)

| # | File | Change |
|---|---|---|
| T1 | `theme.ts` | Add `HATCH_LIGHT='url(#hatch-light)'` + `HATCH_DENSE='url(#hatch-dense)'` exports. Add a shared `<defs>` component (or document the two `<pattern>` ids the Shell must inject) so every viz can reference real hatch fills. Prereq for T2-group and the V12 fixes. |
| T2 | `AlgoStepperShell.tsx` | **V3 fix:** change `predictionGate?: PredictionGate` → `predictionGates?: Map<number, PredictionGate>`. Track per-frame locks: when `idx` reaches a gated frame, lock advance until that frame's gate is answered. |
| T3 | `AlgoStepperShell.tsx` | **V2 fix:** remove the autoplay `setInterval` (`:124-133`), the `▶ play/⏸ pause` button (`:332-339`), and `autoplayMsPerFrame`. Replace the "speed slider" verb-1 requirement with manual-step-only (V2). If a play affordance is wanted, gate it behind reduced-motion-off AND make it advance one frame per tick with no auto-loop — but cleanest is delete it. |
| T4 | `AlgoStepperShell.tsx` | **V17 fix:** add `Home` (→ frame 0) and `End` (→ last frame) to `onKey` (`:206`). |
| T5 | `AlgoStepperShell.tsx` | **AlgoVizProps contract fix:** add `initialStep?: number` (seed `idx`, URL hash overrides it) and `onStep?: (idx:number)=>void` (call it in the `idx` effect). Decide `liveRegionId`: either accept the prop and honor it, or formally amend the spec contract to "Shell owns the live region" and delete the dead `liveRegionId` prop from `MatrixTransform.tsx:26`. Recommend amending the spec — the Shell's approach is better. |

### Tier 2 — V12 palette fixes (mechanical, one pattern, 6 files)

The defect everywhere: **yellow (or opacity) used to encode a category**, when spec V12 says yellow = focus only. Fix = swap category encoding to ink + hatch patterns (from T1), reserve ACCENT strictly for the current-focus element.

| # | File:line | Change |
|---|---|---|
| T6 | `SchedulerGantt.tsx:520` `jobOpacity()` | Replace the 4-tier opacity job encoding with 4 hatch patterns (J1 solid ink, J2 dense hatch, J3 light hatch, J4 dot/cross). Yellow only on the segment under the current-time tick. |
| T7 | `SigmaStackedBar.tsx:5-11` `segmentFor()` | Stop cycling INK/ACCENT by `i%4`. Segment magnitude is already encoded by width; make all segments ink, drop the opacity cycle. ACCENT only for a focused/hovered segment. Also: it is pure HTML — rebuild as `<svg>` (V7/V18) or formally reclassify as a non-viz readout. |
| T8 | `OsiEncap.tsx:641` `PacketGlyph` | Packet fill should be ink (or hatch by header-count), not unconditional ACCENT. ACCENT reserved for the packet on the active layer only — and don't also paint the active *row* ACCENT (`:692`) or focus collides. |
| T9 | `TcpHandshake.tsx:642-646`, `:1061` | `MessageArrow` must not draw SYN-ACK/spoofed messages ACCENT by kind. Encode kind with stroke-dash or label; reserve ACCENT for the in-flight (focused) message. Spoofed backlog slot: ink + hatch, not ACCENT. |
| T10 | `TcpCwnd.tsx:112-115` `modeColor()` | The cwnd line must not switch ACCENT↔INK by mode. Draw the whole trajectory ink; mark *mode regions* with a hatch band or axis annotation; ACCENT only on the current cwnd dot. |
| T11 | `Tls0RttReplay.tsx:34-39`, `:143` | Drop `fillColor:"accent"` as a per-message data field. Replay/danger highlighting: use a hatch or a `⚠` glyph; ACCENT only for the current-frame message. (Lowest priority — here yellow doubles as legit danger-cue.) |

### Tier 3 — per-component ARIA / SVG gaps (standalone non-Shell viz)

| # | File | Change |
|---|---|---|
| T12 | `NumLineDirect.tsx:85` | Add `role="img"` + `<title>`/`<desc>` id pair (V19); add an `aria-live` region narrating μ on drag (V5). |
| T13 | `SumPlotTracker.tsx:87` | Add `role="img"` + `<title>`/`<desc>` (V19). It's display-only so aria-live is optional. |
| T14 | `CompareFrames.tsx:52,98` | Add `<title>`/`<desc>` id pair; rename stale `data-testid="compare-frames-plotly"` → `compare-frames` (no Plotly here). |
| T15 | `SlopeCounter.tsx` | Reclassify as a non-viz UI chip (acceptable as HTML) OR rebuild as SVG. Add `role`/`aria` either way. |

### Tier 4 — pedagogy completeness (V3 content + RC-3 scope)

These are *content* gaps, not code-correctness bugs — schedule after Tier 1-3 but before claiming the redesign's pedagogy bar is met.

| # | Scope | Change |
|---|---|---|
| T16 | All 14 Shell viz | Once T2 lands (`predictionGates` map), actually **author** prediction gates — spec V3 wants one every 3-5 frames. Currently only `MatrixTransform` passes even one. Highest-value targets: `BayesTree` (guess the posterior), `RaceMutex` (guess the lost-update bug), `DPWastedWork` (guess the call count), `TcpCwnd` (guess cwnd after loss). |
| T17 | `BayesTree.tsx` | Spec PS-2 wants a **draggable prior** ("Manipulate" verb). Currently the prior only changes at scripted frames 7/8. Add a draggable prior input. |
| T18 | `TcpCwnd.tsx` | RC-3 PARTIAL-STUB: ships Reno only. Spec wants Tahoe/Reno/CUBIC/BBR **and** the bufferbloat / AQM (CoDel + FQ-CoDel) flat-latency contrast — the latter is what makes RC-3 a "world-first." Build the bufferbloat panel or the §6.8 positioning claim is unsupported. |

### Tier 5 — hygiene (cheap, do opportunistically)

- Remove dead code: `BayesTree.tsx` `pctStr` (`:104`,`:786`); `CppVTable.tsx` `_SVG_W`/`_SVG_H` (`:359-362`); `MatrixTransform.tsx` deprecated `liveRegionId` (`:26`).
- `ProcessFSM.tsx:355-365`: the `fsm-arrow-accent` marker has `fill={INK}` — either make it actually ACCENT or delete the duplicate marker.
- `CppVTable.tsx:352`: `FRAME_COUNT` is hardcoded `15` instead of `FRAMES.length` — make it derived.
- Plotly deprecation (`PlotlyEmbed.tsx`, `ChatPane.tsx`, `plotlyParse.ts`): out of scope for the viz Gate-1 decision, but the spec's "DEPRECATE Plotly → @visx/shape" directive is untouched. Track as separate chat-pane debt.

### Effort shape

Tier 1 is ~1 focused session on a single file (`AlgoStepperShell.tsx`) plus a 2-line `theme.ts` add. Tier 2 is the same mechanical edit applied to 6 files. Tier 3 is small ARIA additions to 4 standalone files. Tiers 4-5 are content/hygiene that can land incrementally. **Nothing here is a rebuild.** The branch's 14 roster viz, all 6 world-firsts, the palette discipline, the dependency manifest, the reduced-motion handling, and the share-link plumbing are all keep-as-is. That is the case for **adopt-with-rework**, not rebuild.

---

## Appendix — what the spec got wrong about the branch

The spec §6.4 said: *"All 12 are spec'd but NOT YET BUILT in `tutor-web/src/components/viz/` — clean slate. Existing 5 components (`NumLineDirect.tsx`, `SumPlotTracker.tsx`, `SlopeCounter.tsx`, `SigmaStackedBar.tsx`, `CompareFrames.tsx`) are brutalist-noncompliant (use blue `#3b82f6` + amber `#f59e0b` + rounded pills)."*

Reality on the branch as audited:
- Not a clean slate — 15 primitives + a full shared shell are built.
- The "existing 5" are **not** brutalist-noncompliant. Grep found **zero** `#3b82f6`, **zero** `#f59e0b`, **zero** rounded pills (`SlopeCounter.tsx:11` explicitly sets `borderRadius: 0`). They already import from `theme.ts`. Either the spec described a pre-retrofit state that the branch has since fixed, or the spec's claim was stale when written. Their actual remaining defects are ARIA gaps (T12-T15) and SigmaStackedBar's opacity-as-category (T7) — not the colors the spec named.

The merge decision should be made against the branch as it actually is, not against the spec's stale "clean slate" assumption.
