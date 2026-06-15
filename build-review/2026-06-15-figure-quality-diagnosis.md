# Figure-quality diagnosis (PM-done, not delegated) — 2026-06-15

Council-1781532469 ordered: PM personally classifies the rejected renders + reads the shell; diagnosis is NOT gate-backed so agent classification is inadmissible. Done by reading code + opening the actual render PNGs at Alex's viewport (1536×~730).

## Unknown (b) — CAN THE SHELL ANIMATE? → YES, today, NO contract change.
- `AlgoStepperShell.tsx:13` imports `motion/react` (Framer Motion); frames wrapped in `<MotionConfig reducedMotion="user">` (line 354).
- `motion-helpers.tsx` already ships `TweenText` (numeric tween), `FadeText` (cross-fade), `DrawLine` (line grows), `DrawPath` (path draws on), `PopIn` (enter/exit). Reduced-motion honored.
- **BUT** the DEFAULT cross-frame transition is a blunt CSS `algoStepperFadeIn` 350ms opacity fade on EVERY svg child (`AlgoStepperShell.tsx:338`). Figure BLINKS between keyframes — nothing slides, nothing tracks identity across a step. Motion is opt-in per family; the families barely opt in. This IS the "clean but flat, no continuity" gap (Manim's thesis = object continuity across a transition; fade-everything has none).
- Autoplay exists (reading-paced dwell, `frameDwellMs`), so stepping is there — only the inter-frame MORPH is missing.

## Unknown (a) — FAILURE MODE PER FAMILY (opened the renders)

| Family / render | Verdict | Why |
|---|---|---|
| **seq-array as BARS** — selection sort (`r-bars-start/mid`) | ✅ **THE REFERENCE (endorsed)** | bar HEIGHT = value (magnitude visible at a glance) · sorted-prefix color highlight · caret ∨ tracks the current min · plain-RO callout. Encoding MATCHES the algorithm's dynamic (find smallest → grow sorted region). |
| **seq-array as BOXES** — merge sort (`r-merge-start/end`) | ❌ wrong-encoding + concept-misfit | flat equal-size white cards = ZERO magnitude (5, 8, 1 look identical) · the defining merge dynamic (two sorted runs interleaving by comparing fronts) is ABSENT — start=scrambled boxes → end=sorted yellow boxes, nothing between shows MERGE. Same "family" as bars, but the box skin drops magnitude AND can't show the concept. |
| **matrix-grid** — DP fib / Gauss (`r-matrixgrid`) | ❌ flat + void | plain bordered table floating in a huge empty canvas; functional, no visual life. "the old stuff." |
| **dark PAGE theme** (`cmp-dark`) | ❌ rejected separately by Alex ("shit") | page reskin — disposition already = REVERT (separate from the figure question). |

**NOT broken-layout.** No clip/overlap — layout is clean. So this is the TASTE/QUALITY path, not a bug fix.

## Unknown (c) — WHAT MAKES THE ENDORSED FIGURE CLICK (the positive reference, not a written rubric)
Bar height = value · color marks the sorted prefix · a caret tracks the live comparison · one plain-language RO callout per step. The renderer's visual GRAMMAR matches selection-sort's dynamic. That match — not polish — is the difference.

## What the diagnosis DICTATES (not a guess)
The merge & matrix figures don't fail on polish; they fail because the renderer's grammar doesn't match the concept and drops magnitude, and because the state-change is never animated (blink, not morph). So the fix is NOT "style the family renderer." It is:
1. Every family renderer encodes MAGNITUDE where the concept has it (bars, not flat boxes).
2. The state-change is ANIMATED with element-tracked motion that already exists in-repo (TweenText/PopIn/layout) — a bar sliding to its sorted slot, two runs converging — not a fade.
3. The renderer's grammar MATCHES the concept's defining dynamic (merge = two sorted runs interleaving; selection = find-min + grow prefix).

## The ONE decision this surfaces for PM/spec-owner (not an executor call)
Point 3 directly tensions the LOCKED master-plan directive (§5.2 / Phase-A A3) that FOLDS sort/merge INTO the sequence-array family. The evidence: one sequence-array renderer producing both bars (good) and boxes (bad) is the coarse-granularity failure council-1781532034 finding #3 predicted. Merge needs a grammar that shows the interleave — either (i) merge gets the bars grammar + a two-run-interleave motion inside the sequence-array family, or (ii) merge/divide-conquer warrants its own family. (i) vs (ii) is a §5.2 spec-owner amendment, not an executor choice.

## Indicated next move (council step 2: flat/static + shell-CAN-animate)
Renderer redesign benchmarked against the selection-sort reference, with motion-aware checkable criteria PER family — starting on the worst (merge). Pending Alex's ruling on the §5.2 family-split fork above.
