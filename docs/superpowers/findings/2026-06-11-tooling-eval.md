# Tooling evaluation — user-supplied list, 2026-06-11

Scope: 11 candidate tools/policies evaluated against the locked design system (brutalist #fde047/near-black + warm Fraunces/Nunito coral/teal), the one-pass lesson pipeline, the Playwright machine-quality gates, and the no-paid-APIs constraint.

## 1. Verdict table

| Name | What | Already used | Verdict | Why (one line) |
|---|---|---|---|---|
| Impeccable | Design-quality enforcement: Claude skill + PRODUCT.md/DESIGN.md contract + `npx impeccable detect` (41 deterministic anti-pattern rules, no API key) | NO | **adopt** | Deterministic CSS/contrast/anti-pattern gate that slots next to the Playwright no-clip gate and implements the DESIGN.md contract the grounded-ui-design skill already requires — without imposing its own aesthetic. |
| SkillUI (npxskillui) | CLI that reverse-engineers design tokens/SKILL.md/DESIGN.md from an existing site or repo | NO | **skip** | Extraction tool for cloning an existing aesthetic; our design language is already locked and build-pack.mjs reads index.css tokens directly — a scrape would only produce a second, conflicting DESIGN.md. |
| UI/UX Pro Max | AI skill that generates design systems from scratch (161 rules, 161 palettes, 57 font pairings, Python/CSV search engine) | NO | **skip** | Greenfield design-system generator that would propose new palettes against a council-locked system; mobile-native-heavy rules, Python runtime deps, and no audit/gate mechanism. |
| Taste Skill | 13 portable SKILL.md taste variants (incl. brutalist) with DESIGN_VARIANCE / MOTION_INTENSITY / VISUAL_DENSITY dials; anti-slop bans | NO | **adopt-later** | The brutalist-skill variant aligns strongly with the locked brutalist theme, but the top-level v2 BANS Fraunces (our locked warm display font) and defaults to GSAP vs our Framer Motion — install only the brutalist variant, scoped, after Impeccable's gate is running. |
| Google Stitch | Google Labs AI UI generator (text→multi-screen React/HTML/Tailwind) with open DESIGN.md spec + free MCP server (350 gens/mo) | NO | **adopt-later** | Free and MCP-callable, but its strength is screen scaffolding, not animated pedagogical SVG traces; revisit for G1–G4 / area-F layout scaffolding once the lesson pipeline is stable and the two themes are verified in its DESIGN.md format. |
| 21st.dev Magic MCP | Component registry + AI component generation (Magic) via web/MCP | NO | **skip** | $20/month after 5 free requests — violates the no-paid-APIs constraint outright; generic SaaS primitives, not pedagogical viz. |
| VoltAgent awesome-claude-code-subagents | 154+ MIT-licensed Claude Code subagent definitions across 10 categories | NO | **adopt-later** | Free and format-compatible, but none cover viz-gates / 5-beat lessons; the right move is authoring project-specific agents (viz-quality-gate, lesson-beat-validator) modelled on its structure once the pipeline shape locks. |
| dgreenheck/webgpu-claude-skill | Claude skill encoding Three.js WebGPU + TSL shader knowledge | NO (zero webgpu/webgl/three.js refs in production code) | **skip** | Canvas/WebGPU output is opaque to every machine gate this project runs on (DOM-query no-clip, trace-match, predict-gate); adopting it would train agents to reach for the wrong rendering layer. |
| custom-webgl-threejs-shaders policy | Standing policy decision on WebGL/Three.js/shaders/scroll/mouse-reactive effects | NO usage | **skip (= BLOCK, codified below in §2)** | The viz needs are structured-data layout problems, not rendering problems; the project's own 3D lesson (qr-3d.html) confused the learner and is being replaced by 2D. |
| Google Fonts | Hosted font CDN (fonts.googleapis.com runtime fetch) | **YES** — DoorWarm.tsx:157, 3 viz-fork-demo lesson HTMLs, 40 doors-lab HTMLs | **adopt (change mode: self-host)** | Keep the fonts, drop the CDN: runtime googleapis fetches make headless/offline Playwright renders silently fall back to system fonts, breaking visual baselines — self-host Fraunces/Nunito/JetBrains Mono woff2 under tutor-web/public/fonts/. |
| Playwright | E2E browser automation; @playwright/test suite (5 specs) + ~18 playwright-core shoot.*.mjs audit scripts | **YES** — both tiers active | **adopt (extend)** | Already the quality-gate backbone; the gaps are visual-regression baselines (toHaveScreenshot), trace/video-on-failure, the no-clip assert not yet wired into @playwright/test, and a silently-broken axe suite (8 tsc errors). |

## 2. POLICY — WebGL / Three.js / shaders / mouse-reactive / scroll-driven effects (standing architecture decision)

**Default stack for the viz auto-pipeline (the only stack agents may emit):**
- SVG, viewBox 480×360, inside `AlgoStepperShell`
- Framer Motion for step-controlled transitions
- d3-hierarchy for tree/graph layout; plain SVG geometry for everything else

**Blocked in the auto-generation pipeline:** WebGL, WebGPU, Three.js, any canvas-based figure, scroll-driven animation, mouse-reactive decoration.

**Why (three independent reasons, each sufficient):**
1. **Machine-verifiability.** Every quality gate this project runs — the Playwright no-clip gate (`getBoundingClientRect()` on real DOM nodes), trace-match correctness tests, predict-gate assertions, accessibility-tree reads — operates on the DOM/SVG tree. A canvas collapses all of that to opaque pixels; gates degrade to hand-tuned screenshot diffs that produce false passes (a clipped label inside a canvas still "passes"). An agent authoring SVG can read its own output and assert on it; an agent authoring a Three.js scene cannot.
2. **Pedagogy.** The council-locked 5-beat lesson form is sequential and learner-paced; scroll-driven and mouse-reactive decoration is directly opposed to it. The ADHD learner profile demands one concept at a time, motion only in service of the reveal. The repo's own data point: the hand-coded 3D Householder reflection (qr-3d.html / lectie-qr.html) confused the learner and is being replaced by a 2D version.
3. **Cost/footprint.** Three.js/WebGPU adds ~600 KB of JS to a bundle that has none, on a student laptop, with patchy mobile WebGPU support (2026). Shader authoring (TSL/WGSL) is expert hand-craft agents cannot reliably generate or verify.

**The only exception path:** a concept that is *intrinsically* 3D, where 2D projection destroys the insight (e.g., 3D rotation groups, quaternion viz, mesh topology). Even then: (a) exhaust SVG/orthographic-2D first; (b) requires an explicit human decision documenting why 2D was insufficient; (c) hand-authored, NOT through the auto-pipeline; (d) gated by screenshot-diff Playwright (since DOM-query gates cannot see inside a canvas); (e) prominent comment explaining the exception.

**App chrome / landing surfaces:** same skip. Both themes are high-contrast and typography-driven; their identity does not depend on 3D or shader effects, and ambient decoration violates the no-decoration constraint outside lessons too.

**Enforcement:** add the constraint verbatim to `.claude/active-constraints.md`:
> Viz auto-pipeline output format = SVG inside AlgoStepperShell. WebGL/WebGPU/Three.js/canvas-based figures are BLOCKED in the auto-generation pipeline. Any 3D figure requires an explicit human decision, documents why 2D was insufficient, is hand-authored, and uses a screenshot-diff Playwright gate rather than the DOM-query no-clip gate.

## 3. Adopt now — shortlist with concrete plug-in points

### Impeccable (new)
1. `npx impeccable skills install` → drops the skill into `.claude/skills/impeccable/`.
2. Create `DESIGN.md` at repo root (`impeccable init`), mirroring the existing CSS token contract from `tutor-web/src/index.css` (both themes).
3. Add `npx impeccable detect --json tutor-web/src/` as a step in the Playwright quality-gate workflow; assert zero failures on its JSON output. Catches contrast/spacing/animation anti-patterns before any human looks at screenshots — a deterministic peer to the no-clip gate at the CSS layer, no API key, no LLM call.

### Google Fonts → self-host (change to existing usage)
1. Download Fraunces (variable, opsz 9–144, regular/italic, weights 400/600/900) and Nunito (400/600/700/800/900) woff2 (Google Fonts ZIP or `google-fonts-helper`); ideally JetBrains Mono too.
2. Place under `tutor-web/public/fonts/` (served at `/tutor/fonts/`); add `@font-face` rules to `tutor-web/src/index.css`.
3. Remove the `<link>` injection from `DoorWarm.tsx:157` and the googleapis `<link>` tags from the doors-lab HTML files.
4. Agent-generated lesson HTML includes `<link rel="stylesheet" href="/tutor/fonts/fonts.css">` or inlines the `@font-face` block.
Result: font-deterministic headless Playwright renders (no CDN race, no silent system-font fallback), offline/exam-day safety, no GDPR transfer to Google.

### Playwright → extend (change to existing usage)
1. **Visual regression:** convert shoot flows to @playwright/test specs ending in `expect(page).toHaveScreenshot('lesson-brutalist-beat1.png', { maxDiffPixelRatio: 0.02 })`; commit baselines to `tutor-web/e2e/snapshots/`; agent-generated pages run against approved baselines.
2. **No-clip gate in the suite:** add an `assertNoOverflow(page)` helper (page.evaluate over all elements inside `.beat.on`, flag `scrollHeight > offsetHeight + 2` / bounding-box overlap); wire into `new-surfaces-smoke.spec.ts` for all 7 routes. This makes the memory-claimed "machine-checked" no-clip gate actually live in CI, not just in ad-hoc shoot.qrqa2.mjs.
3. **Trace + video on failure:** in `tutor-web/playwright.config.ts` add `use: { trace: 'retain-on-failure', video: 'retain-on-failure' }` — zero cost on green, automatic forensics when an agent-generated lesson fails.
4. **Fix the silently-broken axe suite:** add `import 'vitest-axe/extend-expect'` to `tutor-web/src/setupTests.ts` — clears the 8 tsc errors and turns the 8 existing axe tests into real enforcement.

### Adopt-later queue (in order, with their unlock conditions)
- **Taste Skill — brutalist variant only**: after Impeccable's detect gate is running and the GSAP-vs-Framer-Motion conflict is scoped. Copy only `skills/brutalist-skill/SKILL.md` → `.claude/skills/brutalist-ui/`; dials DESIGN_VARIANCE=8, MOTION_INTENSITY=4, VISUAL_DENSITY=6; never install top-level v2 (it bans Fraunces, our locked warm display font).
- **VoltAgent subagents**: after the lesson-pipeline spec is finalised — install react-specialist + ui-ux-tester as baselines, then author project-specific `viz-quality-gate.md` and `lesson-beat-validator.md` agents in their format.
- **Google Stitch**: once the lesson pipeline is stable and new surface layouts (area-F expansions, G1–G4) need scaffolding — author DESIGN.md for both themes first, add the Stitch MCP, gate output through the existing Playwright checks.

## 4. Skip — one-line reasons

- **SkillUI** — scrapes a design system we already have locked and token-anchored; would create a conflicting second DESIGN.md with no gate or enforcement value.
- **UI/UX Pro Max** — greenfield palette/font generator that would fight the council-locked design system on every UI request; mobile-heavy rules, Python deps, no audit mechanism.
- **21st.dev Magic MCP** — $20/month after 5 free requests = hard no-paid-APIs violation; generic SaaS primitives our 24 hand-coded viz components don't need.
- **dgreenheck/webgpu-claude-skill** — would train agents toward canvas/WebGPU output that every existing machine gate (DOM no-clip, trace-match, predict-gate) is blind to; the repo's own 3D lesson is the counter-example.
- **custom WebGL/Three.js/shaders/scroll/mouse-reactive (policy)** — BLOCKED as a standing constraint (see §2): SVG-in-AlgoStepperShell is the only agent-emittable viz stack; 3D is a human-decided, hand-authored, screenshot-diff-gated exception only.
