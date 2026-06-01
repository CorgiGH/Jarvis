# grounded-ui-design — failure ledger (the skill is oblivious) — 2026-06-01

Honest audit of what went wrong designing the concept **door** this session. Split into **skill defects** (the system) and **operator misuse** (me). Input for a council on reworking the skill.

## The one root cause

**The system reasons about UI in the wrong representation.** Every agent (lead, advisors, reconcile, decorrelated critic, judge) reasons over a *structured-text abstraction* of the UI — `element→primitive` JSON maps and ASCII wireframes — and **never looks at a rendered pixel** until the optional last step ⑤ (which I skipped). Taste, hierarchy, density, contrast, "does this look good," "is this empty on desktop" — all of that lives in PIXELS. A loop that judges taste on JSON is structurally blind. It is "oblivious" because it never sees what it made.

## Skill defects (the system, not me)

1. **Render is last + optional.** ⑤ render-real is the final stage and easy to defer. So the FIRST thing the user sees can be ASCII. For a visual learner who can't read ASCII, the skill delivered nothing usable. Render must be FIRST and MANDATORY; the user should never see text mockups.
2. **No vision-in-the-loop.** The judge/critic are text-only. Nothing multimodal ever *looks* at the screenshot. So no agent can say "this is 60% empty space," "white-on-yellow is unreadable," "the accent is scattered." Verdicts are guesses about an abstraction.
3. **No readability gate.** Neither the verifier nor the render gate checked WCAG contrast. Two *valid* tokens can be an unreadable pair (white `--color-page-bg` on yellow `--color-accent` = 1.32:1). It shipped. (Added contrast to the verifier AFTER the fact — the skill shipped without it.)
4. **Pack records existence, not capability.** The pack says `viz.usable = [recursion-tree]` but NOT *how it renders*: it's a locked bordered widget (`AlgoStepperShell`, fixed `max-width:1100px`, white SVG, a 260px controls sidebar, black-on-white). The "viz IS the page / full-bleed-dark" direction was **physically impossible** with the real component — discovered only by hand-reading the source. Proposals can assume impossible compositions because the pack has no capability/constraint manifest per primitive.
5. **Feasibility checked AFTER ranking.** The decorrelated critic caught (correctly) that all 3 doors call `/api/v1/learn/*` endpoints that don't exist and route to an unregistered `/learn/:id/drill` — but as a *post-hoc shared cap*, after the judge already ranked. Feasibility (routes/data exist or are flagged) must gate BEFORE taste-ranking.
6. **Divergence axis too narrow.** "Manufactured divergence" produced threshold / map / cold-open — three *sparse text layouts* that felt same-y and underwhelming. None led with a real visual, despite the pack baking in "visual learner, viz foregrounded." The divergence was cosmetic. Axes must be forced to be genuinely different AND tied to the profile (e.g. one MUST be visual-led).
7. **No iteration loop.** The skill is a single fan-out terminating at a pick (or nothing). It has no path for "none of these — go bolder / more visual / this but X." Real design is react→refine→re-render. The moment we went interactive, we left the skill entirely.
8. **Empty-on-desktop never surfaced.** Doors were mostly blank mid-screen on desktop (sparse + `min-h-screen`). The judge ranked without noting it; I caught it by eye. A density/whitespace check on the real render would have flagged it.
9. **"Decorrelated" critic isn't.** It's the same model family (Claude) with fresh context — not a real decorrelation. The streak-class miss it's meant to catch can still pass if the blind spot is model-wide.

## Operator misuse (me)

- **Showed ASCII to a visual learner.** Deferred ⑤ "to save effort," then handed wireframes. Wrong audience, wrong artifact.
- **Went off-script under momentum.** D4–D11 (the bolder set + full-bleed) were hand-coded with ZERO skill gates — no verifier, no critic, no render check. That's exactly where the white-on-yellow bug shipped. A good system makes the gated path the only path; I proved the gates are bypassable.
- **Built before reading the component.** Proposed "viz is the page" before checking whether the real viz *can* be the page. It can't.

## Candidate directions for the council to adjudicate (do NOT pre-decide)

- **A. Render-first, always.** First user-facing artifact = real screenshots. ASCII banned as a deliverable.
- **B. Vision-in-the-loop.** A multimodal judge/critic that LOOKS at each screenshot and critiques pixels (contrast, density, hierarchy, taste). Verdicts on pixels, not JSON.
- **C. Mechanical gates as code, pre-taste:** WCAG contrast · empty-space/density · component-capability feasibility · backend/route feasibility. All must pass before anything is ranked.
- **D. Capability manifest in the pack:** per primitive — dimensions, theme, own-chrome, restyleable?, composes-into-what — so proposals can't assume impossible layouts.
- **E. Interactive refine loop:** a first-class react→refine→re-render cycle with the user, the FULL net (gates + render) applied to every iteration including live tweaks — so going interactive never drops the net.
- **F. Forced profile-tied divergence axes:** e.g. one option MUST be visual/viz-led, one text-first, one cinematic — derived from the learner profile, not the lead's whim.
- **G. Real decorrelation:** an actually different model family for the independent critic (or a structurally adversarial persona), fail-loud on degradation.
- **H. The pick is a checkpoint, not the terminus.**
