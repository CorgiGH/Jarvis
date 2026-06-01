# Viz Score Backlog — Decision Memo (2026-06-01)

**Source:** per-component scores from the playbook §0 scoring pass (20 components).
**Mount reality (verified against repo, not memory):**
- `tutor-web/src/components/viz/vizRegistry.ts` wires exactly ONE id: `"recursion-tree" → RecursionTree`.
- `content/viz-ids.yaml` `viz_ids:` lists exactly `recursion-tree` (the Kotlin `checkVizReferences` validator + `vizRegistry.test.ts` enforce the two stay equal).
- All 20 scored components exist as files under `tutor-web/src/components/viz/`. **19 are demo-only ghosts** (reachable only via `VizDemoPage.tsx`, never through the student-facing registry route). `ArrayStepper` is new-this-session, living in a throwaway harness.
- **"Wiring" a ghost = one `vizRegistry.ts` entry + one matching `content/viz-ids.yaml` id, added together** (the test fails if they drift).

> **Global caveat on all taste numbers:** every `tasteEst` below is `LOW CONFIDENCE — from code only`. Layout-tangle, legibility, and cramped-viewBox risks are *inferred from constants and SVG math*, not seen. The calibrated floor in §3 is **provisional** until the §6 visual render pass.

---

## 1. RANKED BACKLOG TABLE (best → worst)

Ranking key: correctness first (a wrong viz outranks every cosmetic win in the negative direction), then codeQuality, then taste, with structure (predict/primitives) as tiebreaker.

| # | component | correctness | codeQual | tasteEst (low-conf) | predict-reveal? | sharedPrims? | keep/delete | topFix (one-liner) |
|---|-----------|-------------|----------|---------------------|-----------------|--------------|-------------|--------------------|
| 1 | **TcpHandshake** | looks-correct | 4 | 4 | ✗ | ✓ | keep-needs-work | Wire 2 gates (F1 open, F7 flood defense) |
| 2 | **SchedulerGantt** | looks-correct | 4 | 4 | ✗ | ✓ | keep-needs-work | Gate "which job runs next?" per phase |
| 3 | **ProcessFSM** | looks-correct | 4 | 4 | ✗ | ✓ | keep-needs-work | Add 3–4 state-transition gates; add missing WAITING→RUNNING edge |
| 4 | **RaceMutex** | looks-correct | 4 | 4 | ✗ | ✓ | **keep-and-wire** | Gate F2 "what will counter be?"; delete dead INK marker |
| 5 | **NPGadget** | looks-correct | 4 | 4 | ✗ | ✓ | **keep-and-wire** | Gate F5 "are the 3 nodes pairwise connected?" |
| 6 | **TcpCwnd** | **suspect** | 4 | 4 | ✗ | ✓ | keep-needs-work | **Add the FAST_RECOVERY phase the title promises but never shows** |
| 7 | **CppVTable** | looks-correct | 3.5 | 3.5 | ✗ | ✓ | keep-needs-work | Add gates F4/F9; fix DrawPath markerEnd drawOn + delete void arrowAngleDeg |
| 8 | **BayesTree** | looks-correct | 3 | 4 | ✗ (abstractPole ✓) | ✓ | keep-needs-work | Add ≥2 prior/posterior gates; collapse double buildFrames() call |
| 9 | **Tls0RttReplay** | looks-correct | 4 | 3 | ✗ | ✓ | keep-needs-work | Add 2 gates; fix 10-message slot-shrink legibility |
| 10 | **OsiEncap** | looks-correct | 4 | 3 | ✗ | ✓ | keep-needs-work | Wire fragmentation gate; render the modeled-but-undrawn reassembly buffer |
| 11 | **DPWastedWork** | looks-correct | 4 | 3 | ✗ | ✓ | keep-needs-work | Gate "how many times is fib(2) computed?"; fix depth-bucket tree layout |
| 12 | **MatrixTransform** | looks-correct | 4 | 3 | ✗ | ✓ | keep-needs-work | Bake default gates; fix "M(t)·t" mislabel |
| 13 | **RecursionTree** ⚓WIRED | **suspect** | 4 | 3 | ✗ | ✓ | keep-needs-work | **Replace flat depth-bucket layout w/ Reingold-Tilford (tree actively misleads)** |
| 14 | **PageTableWalk** | **suspect** | 4 | 3 | ✗ | ✓ | keep-needs-work | **Flush stale TLB entry on Phase-4 COW break (real bug)** + TLB-fill on fault retry |
| 15 | **ArrayStepper** 🆕 | looks-correct | 3 | 3 | ✗ | ✓ | keep-needs-work | Distinct compare-vs-swap encoding; kill dead INK ternary (line 100) |
| 16 | **SlopeCounter** | looks-correct | 4 | 3 | ✗ | ✗ | **keep-and-wire** | TweenText the diff value when μ slides (chip readout, gates N/A by design) |
| 17 | **NumLineDirect** | **suspect** | 3.5 | 2.5 | ✗ | ✗ | keep-needs-work | **Fix μ-label desync during drag** (circle imperative, label trails on re-render) |
| 18 | **SumPlotTracker** | looks-correct | 4 | 3 | ✗ | ✗ | keep-needs-work | RAF bypasses reduced-motion → use motion animate(); memoize buildCurve |
| 19 | **CompareFrames** | looks-correct | 3 | 2 | ✗ | ✗ | keep-needs-work | Wrap in shell w/ outlier-reveal gate; replace raw CSS `all 600ms` transition |
| 20 | **SigmaStackedBar** | looks-correct | 4 | 2 | ✗ | ✗ | keep-needs-work | Wrap in shell w/ per-deviation reveal arc; all-INK block reads as undifferentiated |

---

## 2. CORRECTNESS RED FLAGS — HIGHEST PRIORITY (a wrong viz is the worst bug)

**Four `suspect` verdicts. A viz that teaches the wrong thing is worse than a viz that doesn't exist. Fix these before any cosmetic/pedagogy work.**

1. 🚨 **RecursionTree — THE ONLY WIRED, STUDENT-REACHABLE COMPONENT — is suspect.**
   The trace logic is correct, but the **tree layout is wrong**: nodes are placed left-to-right by DFS creation order, not by structural tree position. For the default fib(5), child subtrees visually overlap/interleave with sibling subtrees at depth 3–4; edges cross subtree boundaries. A student stepping through sees what looks like a wrong call order and concludes the (correct) trace is broken. **This is the single most urgent fix in the entire backlog** — it is the only thing a real student can currently reach. Fix: Reingold-Tilford / post-order x-assignment.

2. 🚨 **PageTableWalk — real TLB-coherence bug.** After the Phase-4 COW break, the stale TLB entry `vpn=3→pfn=2` is **never flushed** — the trace teaches that a stale mapping silently disappears, which is false and a concept students already miss. (Secondary: no TLB-fill after Phase-3 fault retry.) Fix: filter the stale entry out and push an eviction frame.

3. 🚨 **TcpCwnd — claim/visual mismatch.** Title, desc, and summary frame all promise **fast recovery**; `buildFrames()` defines the `FAST_RECOVERY` mode but never enters it — on loss it jumps straight to CONG_AVOIDANCE. The summary's "fast recovery" line is a lie; the viz teaches an incomplete Reno model. Fix: actually run the FAST_RECOVERY phase.

4. ⚠️ **NumLineDirect — visual desync (not a math error).** During drag the circle moves imperatively (`setAttribute`) but the μ label `<text x>` only updates on React re-render, so the label trails and snaps. Coordinate math is correct; the *displayed* value lags the marker. Fix: second ref on the text node, or drop the imperative path entirely.

**Note:** #1 (RecursionTree) and #2/#3 are *structural-honesty* bugs (the picture asserts something false). #4 is a transient render artifact. All four block "keep" until fixed.

---

## 3. CALIBRATED TASTE THRESHOLD (provisional — code-estimated)

**Top 3 anchors (highest tasteEst, code-only):** TcpHandshake (4), SchedulerGantt (4), ProcessFSM (4) — also RaceMutex/NPGadget/TcpCwnd/BayesTree sit at 4, but the three named are the cleanest "looks-correct + codeQual 4 + taste 4" triples.

**Gate floor: tasteEst ≥ 3 (provisional).**
Rationale: set the floor *just below the worst keeper among the strong cohort*. The keepers cluster at taste 3–4; the only components dropping to **2** are `SigmaStackedBar` and `CompareFrames` — both because of a genuine *visual-information* failure (all-INK undifferentiated block / bare unlabeled chart), not just a missing gate. A taste floor of **3** keeps every component whose layout carries real information and flags exactly the two whose static render conveys almost nothing. `NumLineDirect` (2.5) sits right on the boundary — its correctness bug is the real blocker, so it's gated by §2 regardless.

**Caveat (load-bearing):** taste here is inferred from code (layout constants, SVG math, encoding choices), not pixels. The floor of 3 is **provisional and will shift after the §6 visual pass** — several "4"s carry explicit legibility/cramped-viewBox risks (Tls0RttReplay 10-message shrink to ~25px slots, CppVTable 8–9px type, TcpHandshake backlog clipping past x=480) that could demote them once rendered.

---

## 4. KEEP-WIRE vs REWORK vs DELETE

No outright deletes recommended from this score set — every component is either correct-and-wireable or correct-with-a-fixable-gap. Each subject has at most one viz per concept, so there is **no redundancy to prune** yet. Distribution:

**A. KEEP-AND-WIRE NOW (correct + high quality, just needs a registry entry + gates) — 3:**
- **RaceMutex** — score `keep-and-wire`. Correct, codeQual 4, taste 4. Wire + add the one F2 gate.
- **NPGadget** — score `keep-and-wire`. Correct, codeQual 4, taste 4. Wire + one gate at F5.
- **SlopeCounter** — score `keep-and-wire`. Correct, codeQual 4, has its own test suite. It's a numeric chip (gates N/A by design); wire it and TweenText the diff.

> Wiring each = add `"<id>": Component` to `vizRegistry.ts` AND the matching `- <id>` to `content/viz-ids.yaml`, together (or `vizRegistry.test.ts` fails).

**B. KEEP, FIX CORRECTNESS FIRST, then wire — 4 (the §2 red flags):**
- **RecursionTree** (already wired — fix in place, do NOT ship more student traffic to the tangled-tree bug until layout is fixed).
- **PageTableWalk**, **TcpCwnd**, **NumLineDirect** — fix the suspect bug, then they join the wire queue.

**C. KEEP, REWORK (correct but pedagogy/encoding gap) — 13:** everyone else marked `keep-needs-work`. These are good components missing predict-reveal and/or carrying a cosmetic/encoding fix. Wire opportunistically as their gaps close. The two weakest by taste — **SigmaStackedBar** and **CompareFrames** — need a structural rework (wrap in `AlgoStepperShell` with a real frame arc) before they earn a wire, not just a polish.

**D. DELETE — 0.** Nothing is redundant or unsalvageable. Revisit only if two components later cover the same concept-id.

---

## 5. STRUCTURE GAPS (systemic refactors — playbook §1, §5)

Counts across all 20:

- **predict-then-reveal: 0 / 20 wire any prediction gate.** This is the single biggest *systemic* gap. `AlgoStepperShell` ships a fully working `predictionGates` / `gateLocked` / `onPredict` machinery; **every** shell-based component passes `undefined`, so the predict-reveal loop — the explicit pedagogical differentiator from a GIF, and the top item in Alex's learner profile — is inert everywhere. **Recommend a single systemic pass that adds a `predictionGates` Map to every shell-mounted viz**, since the infra cost is zero and it's the highest-leverage change repeated 15+ times in the topFixes.
- **abstract pole: 4 / 20 have one** (BayesTree, CppVTable, NPGadget, TcpHandshake). 16 lack a concrete→abstract "zoom out to the principle" frame. Less urgent than gates, but the concrete-only ones never crystallize the lesson.
- **shared-primitive reuse: 15 / 20 use shared primitives; 5 do not** — `CompareFrames`, `NumLineDirect`, `SigmaStackedBar`, `SlopeCounter`, `SumPlotTracker`. Of these, `SlopeCounter` (chip) and `NumLineDirect` (controlled widget) are *justified* non-users by their own header comments. The unjustified three (`CompareFrames`, `SigmaStackedBar`, `SumPlotTracker`) hand-roll animation/transition and as a result **bypass `prefers-reduced-motion`** (CompareFrames raw `all 600ms` CSS; SumPlotTracker raw RAF outside MotionConfig). That's an accessibility regression, not just a style nit — fold it into the shell-wrap rework.

Systemic takeaways for the playbook:
1. **One gate pass** (add `predictionGates` to all shell viz) — biggest ROI, near-zero infra.
2. **One reduced-motion pass** (route the 3 unjustified hand-rolled animators through motion primitives).
3. **Layout-faithfulness pass** for the two tree viz (RecursionTree + DPWastedWork share the same flawed depth-bucket layout — fix once, apply twice).

---

## 6. THE NEXT STEP — the visual-taste render pass (what code can't replace)

Every taste number above is `LOW CONFIDENCE — from code only`. The score pass **cannot** confirm: does the RecursionTree actually look tangled at fib(5)? Do Tls0RttReplay's 10 stacked messages collapse to illegible 25px slots? Is SigmaStackedBar really an undifferentiated black block? Is CppVTable's 8px type readable? These are the exact claims the §3 floor rests on.

**Scoped render pass (concrete):**
1. Run the viz demo surface locally (`VizDemoPage.tsx` is the only host that mounts all 20 — confirm its dev route, then `npm run dev` under `tutor-web/`).
2. Playwright-navigate to each component's demo, step through every frame, and `browser_take_screenshot` at the default input and at the largest input each supports (RecursionTree fib(5) AND fib(7) to expose the tangle; Tls0RttReplay at the 10-message Phase-3 frame; SigmaStackedBar at n=5+).
3. Per the interaction-smoke gate (global CLAUDE.md): assert ZERO 4xx/5xx during paint and no on-screen `/404|error/i` after stepping.
4. Score taste from the pixels, re-rank, and **re-fix the §3 floor** — promote/demote the "4"s carrying legibility risk, confirm or clear the 2-scores.
5. Only then wire the §4-A keepers (RaceMutex, NPGadget, SlopeCounter) and the post-fix red-flag survivors.

The four §2 correctness bugs do NOT need to wait for the render pass — they are confirmable from trace/state logic and should be fixed immediately, **RecursionTree first** (it is the only thing a real student can currently reach).
