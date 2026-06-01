# Council (deep, --agents) — visual/animation architecture + grounded-ui-design rework — 2026-06-01

**Brief:** Q1 fix the oblivious UI-design skill · Q2 is "static React/SVG primitive for everything" the wrong foundation (a skill-rework / b primitive redesign / c +backend / d whole redesign) · Q3 does animation-first fit the current backend or force a redesign. Hard rule: no deadline/scope-nag.
**Members:** 🟦 Gemini (decorrelated external — ran on `gemini-2.5-flash`; Pro tier = zero free quota, see caveat) · ⚔️ Claude motion-architecture maximalist · 🛡️ Claude backend-feasibility skeptic.

---

## Votes on Q2

| Member | Verdict | One line |
|---|---|---|
| ⚔️ Maximalist | **(b)** parametric primitive redesign + **thin additive** backend field (NOT a rewrite) | LLM emits a validated declarative `vizSpec` driving parametric primitives; persisted + cited like a drill |
| 🛡️ Skeptic | **(a)** skill-rework only | registry is cosmetic (zero grade/citation coupling); grow it by hand + unbox the shell; leave backend alone |
| 🟦 Gemini | **(c)** backend revision too | human-authored declarative templates in `content/`; Claude fills only data payloads |

## Q1 — consensus (all three): YES to the skill rework

- Render-FIRST + mandatory (ban ASCII as a deliverable) — **all three call this the root-cause fix.**
- Vision-in-the-loop multimodal critic that LOOKS at the screenshot.
- Gates-as-code BEFORE taste-ranking: WCAG contrast · density/empty-space · component-capability feasibility · backend/route feasibility.
- Per-primitive capability manifest in the pack (dims, theme, own-chrome, restyleable?, full-bleed?).
- Interactive react→refine→re-render loop with gates kept ON.

Skeptic's prioritization (the others rubber-stamp without sequencing): **minimum = render-first + contrast + route-feasibility + manifest** (all mechanical/ordering, no model). **Vision-in-loop critic = the expensive one; add as a force-multiplier AFTER render-first proves out, not a prerequisite.**

## The real consensus underneath the split

1. **The boxed `AlgoStepperShell` is the villain — unbox it.** ALL THREE: it hardcodes `max-width:1100px`, white SVG, `1fr 260px` grid, black-on-white (`AlgoStepperShell.tsx:218-225,261`). Split **transport** (frames/scrubber/a11y/prediction-gates/voice — keep) from **chrome** (size/theme/controls-placement/full-bleed → injectable props). ~30 lines. This alone makes "viz IS the page" / dark / cinematic possible.
2. **Primitives are zero-prop / hardcoded-data** (`recursion-tree` literally `buildFibTrace(5)`). The flexibility ceiling is "1 wired component," not the architecture. Make them **parametric** (data via props); grow into a small library (~5-6 shapes).
3. **No opaque blobs** (Lottie/MP4/pre-rendered): maximalist + skeptic both reject — can't be span-cited, can't be regenerated on retraction (roadmap §9). Declarative/inspectable only. (Gemini leaned Lottie — discount that specific pick; keep its template+data shape.)
4. **(b) and (c) are nearly the SAME architecture** — "declarative templates/primitives + Claude fills data, validated+cited+persisted." They only disagree whether that's an "additive field" or a "backend revision." The genuine dissent is the skeptic's **(a): don't build the generation pipeline at all yet.**

## The skeptic's hardest point (line-cited, hard to refute)

The registry has **ZERO coupling to grade or citation**: grade keys off `serverProblem.kcIds` (`TutorRoutes.kt:2016-2024`), `vizId` is nowhere in the grade/citation path, serve just ships a frozen `drillsJson` blob (`:935-940`). So the visual can change **freely without touching the backend**. And generated-motion *adds* a new provenance problem — **what does a citation span mean for a tween?** — that collides with the `emit()` chokepoint (roadmap §3.8/§8), with D1 (any adaptive motion = live relay on serve), and with the 871-card live-DB migration hazard (a new persisted column during the keystone migration window). It competes with the 6 open blockers for the same ContentValidator critical path, for **zero grade benefit**.

Both maximalist (self-objection) and skeptic (collision #1) independently flag the SAME gating unknown: **citation-semantics of a generated viz payload is undefined.** That cross-confirmation is the strongest signal in the council.

## Blind-spot cross-reference

- 🟦 Gemini rubber-stamped every gate with no prioritization/cost-flag → 🛡️ skeptic supplies exactly that (defer the multimodal critic; minimum mechanical set first).
- 🟦 Gemini's Lottie/blob templates → ⚔️ + 🛡️ both say blobs break citation + retraction. Keep the *shape* (template + Claude data), drop the *blob format*.
- ⚔️ open scene-graph DSL → "balloons into a worse Lottie, unbounded interpreter, vision-critic becomes load-bearing at RUNTIME" — 🛡️ independently confirms; → keep any vocab **closed + small**.
- 871-card migration interaction with any new persisted field — only 🛡️ caught it (collision #4).

## SYNTHESIS — recommended path (staged; defer the fork)

**NOW — all-agreed, zero backend change, zero invariant risk:**
1. **Rework the skill** (Q1): render-first mandatory + contrast gate + route-feasibility gate + capability manifest (skeptic's minimum). Layer the vision-in-loop critic + gated iterate loop next.
2. **Unbox `AlgoStepperShell`** — transport vs chrome; layout/theme/full-bleed become props. (This retroactively proves the earlier "full-bleed is impossible" conclusion WRONG — it was a styling lock, ~30 lines, not an architecture limit.)
3. **Parametric primitives + grow the library** (~5-6 motion-rich shapes) using the `motion`/`visx`/`d3`/`mafs` already in `package.json`. Delivers the free-flowing/dark/animated visuals the user wants.

**DEFER the generated-motion / backend question behind TWO gates:**
- (G1) prove parametric primitives + unboxed shell are genuinely insufficient for the real corpus, AND
- (G2) define what a citation `SourceRef`+span means on a generated viz payload such that `emit()` can hard-block an un-cited animation.
- If/when both cross → go **bounded-(b)**: ONE additive, validated, cited, persisted typed-`params` field (= maximalist's bounded position = Gemini's template/payload split). **Never** a runtime motion compiler; **never** opaque video/Lottie.

**Net verdict:** **(a) now → (bounded-b) later, gated.** Not (c)/(d). The user's flexibility is delivered by unbox+parametric+grow-library (frontend only); the backend stays untouched until the citation-semantics unknown is solved.

## Caveat — provider/cost
🟦 Gemini Pro tiers (`gemini-3.1-pro-preview`, `gemini-3-pro-preview`, `gemini-2.5-pro`) return `limit:0` (zero free quota) on this key — unusable without paid billing. The answer ran on `gemini-2.5-flash` (free). The council's configured default points at a Pro model with no free quota → misconfig; point `GEMINI_MODEL` default at Flash, or the council silently degrades. (No-paid-APIs line held: Flash is free.)
