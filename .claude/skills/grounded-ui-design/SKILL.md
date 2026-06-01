---
name: grounded-ui-design
description: Produce genuinely-good, spec-grounded UI for ONE surface at a time via a multi-agent loop that SEPARATES correctness (a mechanical gate) from taste (a strong-lead proposing real alternatives that YOU pick). Use when designing/redesigning any tutor UI surface (the concept door, the drill, the teaching loop, a dashboard, etc.). Hands you 2-3 real, mounted, ranked options; never auto-picks; never averages agents into mush. Triggers: "design the X surface", "redesign X", "/grounded-ui-design X".
---

# Grounded UI Design

A repeatable system that designs ONE UI surface at a time and hands the user 2-3 **real, mounted, ranked** options to pick from. Validated by council `wf_e06c3501-271` (2026-05-31), which reworked a naive "agents agree → grounded" loop that produces grounded-but-forgettable mush.

## The two functions this system separates (the load-bearing idea)

- **Correctness** = no hallucinated primitive · no dropped existing component/token/viz · no spec-prohibited feature. This is a **GATE** — binary, mechanically checked against the repo, NOT agent opinion.
- **Taste** = the one confident layout that's actually good. This **dies under averaging**. So a single opinionated **design-lead** proposes *deliberately divergent* options; advisors critique on the record; **the user picks.** Nothing is averaged into consensus.

If you ever find the loop "converging because the agents agreed," STOP — that is the failure mode. Agreement among same-model agents is cheap and produces point-of-view-free layouts.

## When to use

One surface per run (door, drill, teaching loop, dashboard…). NOT the whole UI at once. Each pick locks; the next run is grounded in already-locked surfaces. The whole UI emerges surface by surface.

## Run modes

- **FULL** (①→⑥): a new surface or a real redesign.
- **LIGHT** (deterministic pack + mechanical verifier + render only, skipping the LLM proposal/judge): small tweaks. Cheaper; diffs against the prior run.

Every run persists to a hashed folder `.superpowers/ui-runs/<surface>-<gitsha>-<ts>/` (`pack.json`, `proposals/`, `conflicts.json`, `verifier-report.json`, `critic-report.json`, screenshots, `ranking.md`) so the next tweak DIFFS instead of blind re-rolling.

---

## REVISION 2026-06-01 — hard lessons (these OVERRIDE the stages below where they conflict)

Council `wf_a167bcef`/`wacbibi2d` + a live door run exposed the loop as **oblivious: it reasoned about UI as structured text (JSON `element→primitive` maps + ASCII) and never looked at a pixel until the optional last step.** Findings: `docs/superpowers/findings/2026-06-01-grounded-ui-design-skill-failures.md`. The fixes, in priority order:

1. **RENDER-FIRST, ASCII BANNED.** The render (stage ⑤) is **mandatory and the FIRST user-facing artifact** — the user picks from REAL screenshots, never from ASCII/JSON. ASCII wireframes are an internal scratch only; showing one to the user is the #1 failure (a visual learner cannot read them). If you cannot render, you cannot ship the run.
2. **GATES RUN BEFORE TASTE-RANKING — not after.** Before ⑥ ranks anything, the mechanical verifier (`verify-proposals.mjs`) MUST pass, AND two feasibility checks must pass: **(a) route/data feasibility** — every endpoint the design calls (`/api/...`) exists in the route table or is explicitly flagged NEW; grep it, don't discover it post-rank. **(b) component-capability feasibility** — see #3. A taste-ranking over infeasible designs wastes the whole run (this session ranked 3 doors that all called a non-existent endpoint).
3. **CONTRAST GATE (added).** `verify-proposals.mjs` now computes WCAG contrast on any element that declares `fg`+`bg` tokens — two *valid* tokens can be an unreadable pair (white `--color-page-bg` on yellow `--color-accent` = 1.32:1 shipped this session). Declare `fg`/`bg` per element. A render-time DOM contrast scan (Playwright `getComputedStyle` + the same luminance math) is the belt-and-suspenders net for hand-composed surfaces.
4. **CAPABILITY MANIFEST, not just existence.** The pack records a primitive EXISTS but the proposer assumed a *locked boxed widget* (`AlgoStepperShell`: fixed max-width, white SVG, own controls sidebar) could go full-bleed-dark — physically impossible until it was actually unboxed. Before proposing a layout AROUND a primitive, READ the primitive's real constraints (dimensions, theme, own-chrome, restyleable?, fullBleed?). Record them in the pack. "It renders" ≠ "it renders the way I'm drawing it."
5. **INTERACTIVE ITERATION KEEPS THE GATES ON.** When the user says "go bolder / more visual / this but X", re-run the GATED path (verifier + contrast + render), never hand-code ungated variants. The white-on-yellow bug shipped precisely because the bolder variants were hand-coded OUTSIDE the loop with no gate. Going interactive must never drop the net.

6. **THE SELF-SEEING LOOP IS NOW WIRED (2026-06-01 foundation).** The council-flagged "vision-in-the-loop multimodal critic" is no longer deferred — it is stage ⑤'s core (below). The agent runs `self-see.mjs --emit-probe` → pastes the diagnostic into `mcp__playwright__browser_evaluate` (cheap, deterministic: contrast on the COMPUTED DOM, overflow incl. KaTeX, viewBox squash, clipping, ink-density) → spends a Claude **vision** call (multi-frame screenshot) only when the probe flags an anomaly or to score taste → iterates under a forced-optimization gate. Vision judges PIXELS, not JSON.
7. **`DESIGN.md` (repo root) IS THE BRAND CONTRACT.** Every proposing/critiquing/judging agent opens by reading it. Tokens come from it (mirrored into `index.css @theme`); the taste verdict is scored against its **two anchors** (Score-1 generic-SaaS ↔ Score-5 Swiss-brutalist-terminal). Arbitrary values are forbidden because DESIGN.md says so and the verifier enforces it.

---

## The loop

### ① GROUNDING PACK — deterministic script, ZERO LLM
Run `build-pack.mjs` (sibling file) → a frozen, git-SHA-stamped `pack.json`. It is NOT summarized from agent recall (agent-assembled grounding is already wrong in this repo: 24 viz `.tsx` on disk vs **1** registered). It emits:
- **Primitives**: `git ls-files tutor-web/src/components/**` minus `*.test.*`; prop signatures grepped from `export function` / `interface`.
- **Tokens**: parse `tutor-web/src/index.css` `:root` (the ~54 `--color-*` / `--type-*` decls) + `viz/theme.ts`. Raw hex anywhere downstream = FAIL.
- **Viz inventory** reconciled across three sources, divergence kept as a FACT: on-disk `.tsx`, `vizRegistry.ts` keys, `content/viz-ids.yaml` ids. A viz is **USABLE** only if on-disk AND registry-wired; the rest go in a **BUILT-BUT-UNREACHABLE** bucket (proposers may *request wiring*, must not assume it renders).
- **Self-check**: `registry-key-count == yaml-id-count == claimed-usable-count`; fail loud on mismatch.
- **PROHIBITED denylist**: read from a checked-in file `prohibited-ui.txt` (`streak|xp|badge|leaderboard|gamif|…`) reflecting the CURRENT north-star — NOT old specs (the 2026-05-10 spec actually KEPT a streak; the 2026-05-17 redesign banned it).
- **Learner profile** (baked in, never re-typed): ADHD (one primary action visible, minimize cognitive load), visual learner (viz foregrounded), predict-then-reveal, low programming confidence — from `~/.claude/projects/.../memory/alex-learner-profile.md` + spec.
- **Omitted-on-purpose**: every repo primitive/token NOT carried into the run, listed explicitly → omission is a logged decision, not a silent gap.

Agents **cite the pack by hash**; they never regenerate it.

### ② DESIGN-LEAD proposes N divergent theses (taste enters here)
ONE opinionated lead agent authors **2 (up to 3) deliberately divergent FULL proposals**, each committing to a different stated thesis (e.g. "single-focus zen drill column" vs "split-rail reference+drill" vs "dense command-deck"). Each carries an explicit `element → primitive/token` map citing the pack. Variance is manufactured on purpose.
Two **advisor** lenses CRITIQUE (they do NOT co-author), with asymmetric objectives so disagreement is structural:
- **UX-interaction** (absorbs the learning-experience lens): cognitive load, clicks-to-retrieval, predict-then-reveal rhythm, ADHD one-primary-action.
- **UI-visual**: hierarchy, brutalist-yellow application, to-scale density.

### ③ RECONCILE — capped, NO consensus objective
The lead addresses each advisor note. Every conflict terminates as exactly one tag (never "they agreed"):
- **RESOLVED-BY-EVIDENCE** (cite the pack line / spec goal / learner axis that decides it),
- **TRADE-OFF-ACCEPTED** (chosen option names what it sacrifices and why),
- **ESCALATE-TO-ALEX** (genuine taste call — do not fake-resolve).
Hard cap **2 rounds**; then the lead force-decides + records rationale. Learner-profile tie-breaker: ADHD → cognitive-load > hierarchy.

### ④ VERIFIER (deterministic) + ONE decorrelated critic
- **Mechanical verifier (code, no LLM)**: every cited element exists in `pack.json`; every reused primitive's props match the real signature **AND the proposal names the data/URL it's fed** (catches correct-component/wrong-prop — the `pdfUrl`-dropped-at-underscore class); every color/space resolves to a token (no raw hex); PROHIBITED regex = 0 hits; viz parity holds; **every USABLE primitive appears USED or REJECTED-with-reason** (unaccounted = auto-FAIL).
- **One judgment critic, DECORRELATED**: a *different model family* (or at minimum a fresh-context persona with NO access to the lead's/advisors' reasoning traces — only the final artifact + the mechanical pack). Its sole job: "what did everyone assume was fine." This is the independent vet that catches the streak-class miss. **Fail loud if an intended cross-family agent silently degrades to the host model** (the gws / Gemini-fallback lesson).
- Critic→③ bounce capped at **1 return**; a second failure ESCALATES to Alex with the failing assertion attached.

### ⑤ RENDER REAL + SELF-SEE — mount actual components, then LOOK (vision-in-the-loop)
Render by **mounting the actual `tutor-web` components** with the real `index.css` tokens on a throwaway Vite route (Storybook-style) — **NOT a hand-rolled HTML/CSS twin** (that re-ships the ghost-component gap at the mockup layer — the trap a hand-written mockup fell into this session). The dev server runs on `:5173`; drive it with **Playwright MCP** (`mcp__playwright__browser_*`) — one toolchain, no headless second runtime.

Per candidate, run this sub-loop (collapses N blind round-trips to 1–2 seen ones):

1. **Navigate + interaction-smoke (mechanical, global-`CLAUDE.md` gate).** `browser_navigate` at 2–3 fixed widths (e.g. 390 / 834 / 1280). Capture `browser_network_requests`: **ZERO 4xx/5xx on first paint.** Every spec'd `data-testid` paints. Click every interactive element; assert no `/404|HTTP \d{3}|error/i` text and no new 4xx/5xx. Reuse the 5 `src/__tests__/axe.*.test.tsx` as hard pass/fail.
2. **Diagnostic probe (deterministic, NO vision — token discipline).** `node self-see.mjs --emit-probe` → paste the returned JS as the `function` arg of `browser_evaluate`; save its JSON; `node self-see.mjs --grade <json>`. **Any `blocking[]` entry (contrast on the COMPUTED DOM, horizontal/KaTeX overflow, SVG viewBox squash, clipped animation) → FIX and restart ⑤ for this candidate. Do not advance, do not vision-call yet.** `warnings[]` (sparse/dense ink) must be LOOKED at in step 3, not auto-failed (a poster's negative space is legal; accidental emptiness is not).
3. **Three-frame vision critique (spend the vision call here).** `browser_take_screenshot` at t=0 / t=500ms / t=1000ms (`browser_wait_for` between; for finite anims confirm fill=forwards at rest). Feed all three to a single Claude **vision** call scoring the 4-dimension rubric — (1) the algorithm/teaching state shown matches the step description, (2) layout not broken, (3) no overlap, (4) animation reaches final state — AND the **DESIGN.md two-anchor taste rubric** (Score-1 generic-SaaS ↔ Score-5 Swiss-brutalist-terminal). The critique MUST emit per-issue JSON `{issue, element_description, approx_bbox:{x,y,w,h}%, suggested_fix}`; map bbox→selector via `browser_evaluate('document.elementFromPoint(x,y)')` so each note is actionable, not a vibe.
4. **Forced-optimization gate (anti-thrash).** Apply the fixes; re-render; **accept a revision ONLY if its score strictly exceeds the prior best.** Resample ≤10×; if it can't beat the prior best, HALT and surface to Alex with the stuck render + critique attached (never cycle between two equally-broken states each called a "fix").

A candidate advances to ⑥ only after: mechanical floor (step 1+2) green AND it clears the **calibrated taste floor** (DESIGN.md / `findings/2026-06-01-viz-score-backlog.md`). Persist all screenshots + the probe JSON + critiques to the run folder. The loop does **not** end at a screenshot file — see ⑥, and the MOUNTED-AND-VISIBLE gate (paints on the real student route) for anything that ships beyond the throwaway demo route.

### ⑥ JUDGE → ALEX (termination; this measures QUALITY)
A judge **RANKS** (never blends — no best-of-all Frankenstein) the **self-seen** rendered candidates (carrying their ⑤ vision scores) against a written rubric tied to the learner profile (ADHD: one primary action; visual: viz foregrounded not buried; predict-then-reveal as the emotional climax) **and the `DESIGN.md` two-anchor taste rubric, scored 3× @ temp 0.7 and averaged** (playbook §4.2), reusing the `review-site` skill's Visual/UX/Pedagogy agents as the rubric source. It produces a justified ranking and **hands Alex 2-3 strong, grounded, real-rendered options.** It does NOT auto-pick. **THE LOOP TERMINATES AT ALEX'S SELECTION.** The pick becomes the build spec for the implementation plan (wiring the route + server endpoints is downstream execution).

---

## Anti-patterns this system exists to prevent (all hit this repo before)
- **Consensus mush** — agents agreeing into a forgettable layout. Fixed by lead-proposes-divergent + Alex-picks; "agreed" is never a terminal state.
- **Ghost-component** — building components nobody mounts / a hand-HTML twin that diverges from production. Fixed by ⑤ mounting real components + interaction-smoke.
- **Agent-recall grounding** — a primitive/token/viz silently forgotten by every agent. Fixed by ① deterministic pack + omitted-on-purpose + the 1≠1≠24 self-check.
- **Correlated blind spots / model monoculture** — same-model critic rubber-stamping shared misses (the streak slipped past until a SEPARATE vet caught it). Fixed by the deterministic verifier + a decorrelated different-family critic + fail-loud-on-degradation.
- **Prohibited features** (streaks/XP) — fixed by the checked-in denylist regex-scan = 0.

## Model routing (per feedback_subagent_model_routing)
Verifier = no model. Advisors = cheaper model. Spend the strong model (Opus) only on the lead's taste work + the decorrelated critic + the ⑥ judge. Log model identity per agent.

## Provenance
Council `wf_e06c3501-271` (2026-05-31, NEEDS-REWORK → this loop). Builds on `~/.claude/skills/review-site/` (the Visual/UX/Pedagogy rubric source). The render harness is the repo's `tools/slice*-playwright-gate.mjs` + `tutor-web/playwright.config.ts` (NOT `review-screenshot.mjs` — that was a different app's, absent here).
