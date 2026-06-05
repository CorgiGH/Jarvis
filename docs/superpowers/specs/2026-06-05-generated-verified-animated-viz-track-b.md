# Generated · Verified · Animated explainer per Knowledge-Concept — Track-B spec candidate

**Status:** DEFERRED Track-B / Phase-3 spec candidate. Logged 2026-06-05 at Alex's request. NOT scheduled — this is a *capability log*, gated on backlog-scoring (§5) before it becomes a build task.
**Author:** Claude · **Date:** 2026-06-05 (SESSION-55).
**Scope:** the TUTOR vertical (jarvis-kotlin). Backend = Kotlin; viz layer = `tutor-web/` React 19 + `motion ^12` + SVG.

---

## 1. The capability (one sentence)

**Claude GENERATES a fresh, ANIMATED explainer for each knowledge-concept (KC), and that explainer is VERIFIED for pedagogical correctness before the student ever sees it.**

Three independent properties compose here. Two are already planned and partly built; one is the genuine gap. The point of this doc is to state *precisely* which is which, with citations, so this never gets re-litigated as "all new" or quietly bundled into a phase that hasn't scoped it.

```
GENERATED-per-concept   →  THE GAP (deferred Track B; gated on backlog-scoring)
VERIFIED-pedagogically  →  ALREADY PLANNED  (viz playbook §3.6 — the correctness gate)
ANIMATED                →  ALREADY PLANNED  (Motion library + AlgoStepperShell, playbook §5/§6)
```

---

## 2. What is ALREADY planned (do not re-spec these)

### 2.1 VERIFIED — the pedagogical-correctness gate is already designed

`docs/superpowers/research/2026-06-01-viz-ui-excellence-playbook.md` **§3.6** ("THE PEDAGOGICAL-CORRECTNESS GATE — the most important gate in this document; net-new vs. the old playbook") already specifies a two-layer correctness oracle that runs *before* a viz is trusted:

1. **Trace-match against a reference implementation** — write a tiny plain-TS reference implementation of the algorithm, emit its canonical step trace, and assert the viz's step sequence matches that reference trace (not merely that the animation is internally self-consistent). "A BFS viz whose frontier dequeues in DFS order fails here even though every MoVer spatial assertion passes." (§3.6, layer 1.)
2. **Content review against the source** — feed the viz's claimed teaching point + the source KC/PDF excerpt to Claude and ask "does the visualization's claim match the source, and is anything pedagogically misleading?" — catching wrong intuition / mislabeled axis / a correct algorithm explained with a false *reason*. (§3.6, layer 2.)

The playbook flags this gate as **non-negotiable, no aesthetic substitute, apply BACKWARD first** (§3.6 closing; §7 step 4: "Fail → fix and restart at step 4"). It exists *specifically* because the user "cannot vet subject content himself" (`alex-learner-profile` / `user_subject_knowledge_gap`), so a polished-but-wrong viz is the worst failure mode and currently has zero detector.

**Conclusion:** the VERIFIED leg of this capability is already a planned gate. Track B does not invent verification — it *reuses* §3.6 as the admission gate on each generated explainer.

### 2.2 ANIMATED — the motion toolchain and shell are already chosen + partly built

The same playbook **§5** ("Steal-these exemplars + motion principles") and **§6** ("Motion toolchain for THIS stack — use what's installed") lock the animation substrate to the **`motion` library (Motion, formerly Framer Motion), `motion ^12`, already installed AND already abstracted in `tutor-web/src/components/viz/motion-helpers.tsx`** (which already exports `DrawLine`, `DrawPath` (`pathLength 0→1`), `TweenText`, `FadeText`, `PopIn`). §6 explicitly *rejects* adding a second runtime (GSAP/Rive/Lottie) for this stack. §5 names the ES6 generator-per-step + RAF-clock architecture as the animation spine and the natural attach-point for the §3.6 reference-trace check.

The shell component already exists in the repo: **`tutor-web/src/components/viz/AlgoStepperShell`** (named as an established `prefers-reduced-motion` site in the playbook's grounding inventory). The viz layer is 24 hand-coded React/SVG components today.

**Conclusion:** the ANIMATED leg is already planned and the substrate is already installed + partly built. Track B does not pick an animation library — it *uses* Motion + AlgoStepperShell.

---

## 3. What is the GAP — GENERATED-per-concept

`docs/superpowers/specs/2026-05-29-e3-generate-route-design.md` **§5 ("Routing architecture")** establishes the current, *deliberately-bounded* state of viz generation and **explicitly defers the bespoke per-concept generated viz**:

> "Most existing viz components are **zero-prop static illustrations** (e.g. `RecursionTree(): ReactNode`, a fixed fib(5) trace) — a routed visual is a **concept-level animation, not a render of the specific generated problem**. That is acceptable for proving routing; stated plainly so no one expects per-problem viz." (e3 §5, "Registry".)

So what E3 ships is **routing** a pre-built, concept-level viz component by `viz_id` — NOT generating a fresh explainer per KC. The bespoke-generation track is named and deferred in the viz playbook's two-track split, gated on backlog-scoring proving it is the bottleneck:

> "**TRACK B — NET-NEW (optional, later):** a declarative spec + auto-layout system for **future** components. This is a from-scratch architecture, not a 'one new dependency.' Do not start it until Track A's baseline scoring tells you the bespoke approach is the bottleneck." (playbook §0 orientation; restated §6b/§6c.)
>
> "**When to even consider Track B:** only if §0's scored backlog shows the *bespoke* approach is the bottleneck — i.e., you're repeatedly hand-nudging x/y to stop overlaps across many new graph/tree viz. … If most future viz are number-lines, bar charts, and FSMs, Track B may never pay off." (playbook §6b.)

**Conclusion:** GENERATED-per-concept is the one un-built, un-scheduled leg. It is deferred-by-design and **gated**: it does not start until the §0 retrospective backlog-scoring (taste §4 + correctness §3.6 over the *mounted* subset of the 24 components, plus the §5 mount-audit) demonstrates the hand-coded/bespoke approach is the actual bottleneck. Until that gate fires, the move is to *consolidate the 24 toward N shared primitives* (playbook §1 Failure B), not to build a generator.

---

## 4. Proof-of-concept (tonight, 2026-06-05)

`C:/Users/User/Desktop/cursuri RC/rc-anim.html` — a **Claude-authored, self-verified animated explainer** for a single networking knowledge-concept (RC = Rețele de Calculatoare / Computer Networks). It animates, in Romanian, the message «salut» being **encapsulated down the OSI/TCP-IP stack** (App → Transport `port|nr` → Rețea `IP` → Legătură `MAC` → morph to bits), **travelling across the wire as bits** (STEP 5, glide along the FIR left→right), and **de-encapsulated back up** at the receiver (peel MAC → IP → port; ACK zips back; show «salut» + check). A 9-step `requestAnimationFrame`-driven animation with a step caption bar and progress pips; standalone single-file HTML (~709 lines).

This is an existence proof of the **GENERATED + ANIMATED + self-VERIFIED** triple at the unit of *one knowledge-concept*: Claude produced the explainer from the source concept, animated it with hand-authored keyframes/RAF (the manual analogue of the Motion path §6 prescribes), and self-checked the step trace against the real encapsulation order (the manual analogue of the §3.6 trace-match). It is a desktop artifact, NOT wired into `tutor-web/` — it demonstrates the *capability*, not the productionized Track-B path.

What it does NOT yet have (and what productionizing Track B would add): the §3.6 oracle as an automated admission gate, the Motion/AlgoStepperShell substrate (§5/§6) instead of bespoke CSS keyframes, the `viz-ids.yaml` ↔ `vizRegistry.ts` routing bridge (e3 §5), and `prefers-reduced-motion`/baseline-screenshot discipline.

---

## 5. Gate to promote this from candidate → build task (do NOT skip)

This spec candidate becomes a real Phase-3 build plan ONLY when ALL of the following hold (per the playbook's two-track discipline):

1. **§0 backlog-scoring is done** — the 24 existing viz are mount-audited (against `vizRegistry.ts`, currently 1-of-24 wired) and scored for taste (§4) + correctness (§3.6), producing the ranked backlog + calibrated threshold.
2. **The backlog shows the bespoke approach is the bottleneck** — repeated hand-nudging across many *new* graph/tree viz, not just number-lines/bar-charts/FSMs (which Track B never pays off for).
3. **The primitive-consolidation harvest is exhausted first** — the 24 are pulled toward N shared primitives (playbook §1) before any generator is built; if one-fix-propagates closes the gap, Track B stays deferred.
4. **Alex opens the door** — per `feedback_pm_delegation` / no-build-unless-Alex-opens-it; this is a plan-first artifact, building is gated on his GO.

Until then: **DEFERRED.** Logged so the capability + its citations are not lost, and so a future session does not mis-scope "generate an animated explainer per KC" as net-new or sneak it into a phase that never scoped its verification + animation substrate (both already planned, §2).

---

## 6. Citation index

| Claim | Source | Locator |
|---|---|---|
| VERIFIED viz is already a planned gate (pedagogical-correctness) | `docs/superpowers/research/2026-06-01-viz-ui-excellence-playbook.md` | §3.6 (the two-layer correctness oracle; "most important gate", "non-negotiable") |
| ANIMATED substrate already chosen + partly built | same playbook | §5 (motion principles / ES6-generator-per-step spine), §6 (`motion ^12` + `motion-helpers.tsx`), `AlgoStepperShell` in the grounding inventory |
| GENERATED-per-concept is explicitly the bounded/deferred leg | `docs/superpowers/specs/2026-05-29-e3-generate-route-design.md` | §5 "Registry" ("concept-level animation, NOT a render of the specific generated problem") |
| Track B is net-new, optional, gated on backlog-scoring proving bespoke is the bottleneck | viz playbook | §0 orientation, §6b "When to even consider Track B", §6c |
| Proof-of-concept (Claude-authored, self-verified, animated, per-concept) | filesystem | `C:/Users/User/Desktop/cursuri RC/rc-anim.html` (~709 lines, 9-step RAF animation, RC encapsulation) |

🤖 Generated with [Claude Code](https://claude.com/claude-code)
