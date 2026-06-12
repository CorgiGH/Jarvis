---
# DESIGN.md — the single machine-readable brand source for jarvis-kotlin's tutor UI.
# Convention: google-labs-code/design.md (Apache-2.0). YAML below is the source of
# truth; it is mirrored 1:1 into the Tailwind v4 `@theme` block at tutor-web/src/index.css.
# Every grounded-ui-design agent prompt MUST open with: "Read DESIGN.md. Use only named
# tokens by their {path}. Arbitrary values (bg-[#...], pl-[17px], rounded-*) are FORBIDDEN."
# A reference like {colors.accent} is valid ONLY if the token exists below. Invent nothing.
scope: tutor-web/            # this brand governs the React SPA; the Kotlin backend has no UI

# ─────────────────────────────────────────────────────────────────────────────
# COLOR — two legal surfaces, one accent. There is no third surface and no second hue.
# Names match tutor-web/src/index.css EXACTLY (renaming churns 11 shipped gates — don't).
# ─────────────────────────────────────────────────────────────────────────────
colors:
  # base primitives (literal values; the ONLY place a hex lives)
  base:
    ink: "#000000"        # true black
    paper: "#ffffff"      # true white
    yellow-300: "#fde047" # THE accent
    yellow-400: "#facc15" # accent pressed/hover + focus ring
    yellow-500: "#eab308" # left-rule on cards
    yellow-50:  "#fefce8" # faint accent wash (rare)
    zinc-50:    "#fafafa" # muted page backdrop (pdf canvas, showcase)
  # semantic — components reference THESE, never base, never a raw hex
  page-bg: "{colors.base.paper}"        # LIGHT surface (default working surface)
  page-fg: "{colors.base.ink}"
  panel-dark-bg: "{colors.base.ink}"    # DARK surface (hero / terminal / code / cinematic)
  panel-dark-fg: "{colors.base.yellow-300}"
  accent: "{colors.base.yellow-300}"    # the focus color; ONE element at a time (ADHD rule)
  accent-hover: "{colors.base.yellow-400}"
  accent-rule: "{colors.base.yellow-500}"
  accent-soft: "{colors.base.yellow-50}"
  border-strong: "{colors.base.ink}"    # hard black dividers/borders, 2–4px
  border-thin: "rgba(0,0,0,0.2)"        # faint hairline (locked/inactive)
  ring-focus: "{colors.base.yellow-400}"
  surface-muted: "{colors.base.zinc-50}"
  # status (semantic, inline cards/badges only — NOT decoration)
  danger-bg: "#dc2626"
  danger-fg: "#ffffff"
  danger-text: "#991b1b"   # 7.66:1 on white, AAA
  info-bg: "#bfdbfe"
  disabled-bg: "#d1d5db"
  disabled-fg: "#6b7280"
  overlay-fg: "#ffffff"

# ─────────────────────────────────────────────────────────────────────────────
# TYPE — monospaced is the brand voice. No Inter/Roboto/Helvetica anywhere.
# ─────────────────────────────────────────────────────────────────────────────
type:
  font-display: 'ui-monospace, "SF Mono", "JetBrains Mono", Menlo, Consolas, monospace'
  font-body: '{type.font-display}'   # mono everywhere today; prose-readability is an OPEN calibration item, not a license to add Inter
  # scale (px tokens already in index.css :root)
  sm: "12px"
  body: "clamp(14px, calc(13.5px + 0.1vw), 16px)"
  lg: "18px"
  h2: "20px"
  display: "clamp(28px, 5vw, 40px)"   # door/hero headline
  display-xl: "clamp(36px, 6vw, 68px)" # cinematic dark hero
  tracking-tight: "-0.01em"
  tracking-wide: "0.05em"
  tracking-widest: "0.15em"   # uppercase labels/chrome — the brand's signature
  tracking-mega: "0.3em"      # cinematic kickers

# ─────────────────────────────────────────────────────────────────────────────
# SHAPE — radius is LOCKED at zero. This is non-negotiable and lint-enforced.
# ─────────────────────────────────────────────────────────────────────────────
radius:
  none: "0"   # ALL corners. `rounded-*` is a lint error, not a review note.
border-width:
  hair: "1px"   # inactive/locked
  rule: "2px"   # dividers, default card border
  bold: "4px"   # primary CTA, emphasized frame
space:          # 4px base grid; arbitrary spacing (pl-[17px]) is forbidden
  grid: "4px"

# ─────────────────────────────────────────────────────────────────────────────
# ELEVATION — brutalist HARD offset only (0 blur, solid border-strong).
# Soft/blurred drop-shadows are SaaS and BANNED.
# ─────────────────────────────────────────────────────────────────────────────
elevation:
  flat: "none"
  hard: "8px 8px 0 0 {colors.border-strong}"   # the only legal shadow

# ─────────────────────────────────────────────────────────────────────────────
# MOTION — purposeful only. Numbers from the viz-excellence playbook §5.
# NEW tokens (added to @theme this session). ADHD rule overrides everything below.
# ─────────────────────────────────────────────────────────────────────────────
motion:
  duration-fast: "200ms"   # hover/click; below 200ms imperceptible
  duration-base: "300ms"   # state changes
  duration-slow: "450ms"   # panels/large moves; above 500ms breaks flow-of-thought
  ease-out: "cubic-bezier(0,0,0.2,1)"        # entering
  ease-in: "cubic-bezier(0.4,0,1,1)"         # exiting (use a SHORTER duration)
  ease-standard: "cubic-bezier(0.4,0,0.2,1)" # point-to-point
  ease-landing: "cubic-bezier(0.22,1,0.36,1)" # the shipped slide-up "satisfying landing"
  stagger: "70ms"          # secondary elements; 60–80ms band
  delay-children: "200ms"
---

# The brand in one line

**The best designs look like a Swiss International Style poster remixed for a terminal — NOT a SaaS landing page.** Monospaced, one yellow accent on black-or-white, hard black borders as structure, asymmetric grid, zero radius, zero gradient, zero soft shadow. If it could be a Stripe/Linear/Vercel marketing page, it is wrong.

This is a tutor for one ADHD AI-bachelor student who learns visually. The brand exists to (a) keep attention on the ONE thing that matters right now and (b) never look like generic AI output, because generic = untrustworthy = unread.

# Two surfaces, never a third

The same brutalist system renders on exactly two surfaces. Pick by job, never mix within a frame.

| Surface | Tokens | Use for |
|---|---|---|
| **LIGHT** (default) | `page-bg` white · `page-fg` black ink · `accent` yellow · `border-strong` black | sustained reading, drills, dashboards, the working surface |
| **DARK** | `panel-dark-bg` black · `panel-dark-fg` yellow · borders in `panel-dark-fg` | hero / concept-door / terminal / code / cinematic moments |

Both are brutalist-yellow. The accent and the mono voice are constant across both. Dark is for *moments*, light is for *work* — long-form reading stays on light.

# The taste gate — two anchors (calibrated, averaged 3× @ temp 0.7)

A render is scored against exactly these two poles. Floor is calibrated from the best shipped components (see `findings/2026-06-01-viz-score-backlog.md`), not a magic number.

- **Score 1 (reject):** Generic AI output. Inter/Roboto font, blue/purple/teal palette, `rounded-lg` cards, three equal columns, gradient backgrounds, soft drop-shadows, centered hero with a subtitle. Looks like every SaaS landing page. *Could be any product.*
- **Score 5 (target):** Swiss brutalist. Monospaced everywhere, a single `accent` highlight on a `page-bg`/`panel-dark-bg` field, full-width hard black borders as dividers, asymmetric grid, zero shadows (or one hard 0-blur offset), zero gradients, uppercase `tracking-widest` labels. A 1960s Swiss poster running in a terminal. *Could only be this tutor.*

# Do

- Cite a named token for every color/space/radius/duration. `bg-accent`, `border-border-strong`, `text-page-fg`.
- Uppercase + `tracking-widest` for labels, kickers, chrome, button text (`B E G I N`).
- Structure with hard borders (`border-rule` / `border-bold`) and the 4px grid. Let borders do the work shadows would do elsewhere.
- Asymmetric, deliberate layout. A door may be 60% empty *if the emptiness is composed* (poster negative space), not if it is accidental sparse-text-on-min-h-screen.
- One `accent` element in focus at a time. Everything inactive is monochrome and still.
- Hard 0-blur offset shadow (`elevation.hard`) when a card must lift — never a blur.
- Animate only where it carries meaning (state change, the load-bearing transition). Name the purpose or cut it.
- Respect `prefers-reduced-motion` (reduce, not eliminate) + an in-UI motion toggle.

# Don't

- ❌ No arbitrary values: `bg-[#FACC15]`, `pl-[17px]`, `text-[19px]`. Lint error.
- ❌ No `rounded-*`. Radius is 0. Always.
- ❌ No gradients, no blurred drop-shadows, no glass/blur, no neumorphism.
- ❌ No Inter/Roboto/Helvetica/system-sans for chrome. Mono is the voice.
- ❌ No second accent hue. Yellow is the only color that means "look here." Status colors (danger/info) are semantic, not decoration.
- ❌ No idle/ambient/entrance-pulse animation. No animating keyboard-repeated actions. Toxic for ADHD attention.
- ❌ No white-on-yellow or yellow-on-white text (`accent` on `page-bg` ≈ 1.3:1 — fails WCAG hard). Yellow is a FILL behind black ink, or a fg ON the dark surface. Contrast is a mechanical gate, not a judgment call.
- ❌ No centered-hero-with-subtitle SaaS template.

# Three consistency locks (agent self-checks before claiming done)

1. **Color lock** — only the semantic tokens above appear. Any raw hex outside `colors.base` = fail.
2. **Shape lock** — `radius.none` everywhere. Any `rounded-*` = fail.
3. **Theme lock** — exactly one surface per frame (LIGHT or DARK), no theme toggle, no mid-frame mix.

Pre-flight = an ESLint scan + a regex for arbitrary colors + an axe `color-contrast` pass on the rendered DOM. Mechanical, ~2s, runs before any taste scoring.

# Motion, precisely (ADHD-first)

- `accent` is the current-focus highlight **only**, one element at a time. Everything else monochrome + motionless.
- Durations from `motion.*`. Never a flat 300ms everywhere; pick by job (fast=interaction, base=state, slow=panel). Exiting elements use `ease-in` + a *shorter* duration.
- Stagger secondary elements `motion.stagger` (70ms). **Exception:** two panels starting at the *identical* time is the explicit claim "these are the same event" (tree rearrange + array swap) — flush them together.
- Step control (play/pause/**step-back**/speed) beats easing polish. A pausable plain stepper teaches better than a beautiful autoplayer that can't stop.
- `prefers-reduced-motion`: static by default, motion only inside `@media (prefers-reduced-motion: no-preference)`; reduced fallback = instant opacity/color (not vestibular). Plus a manual localStorage toggle — an ADHD user may want motion off regardless of OS.

# How this file is used

- **Source of truth:** the YAML front-matter. Mirrored into `tutor-web/src/index.css` `@theme` (keep them identical; PR that edits one edits both).
- **Every UI agent prompt** opens by reading this file and is forbidden from arbitrary values.
- **The taste gate** (`grounded-ui-design`) scores renders against the two anchors above.
- **References:** brand rationale + the full quality method → `docs/superpowers/research/2026-06-01-viz-ui-excellence-playbook.md`; the scored backlog + calibrated floor → `docs/superpowers/findings/2026-06-01-viz-score-backlog.md`.

# Token mirror (machine-generated)

The table below is generated from `tutor-web/src/index.css` and must not be hand-edited. It is the drift-gated mirror of the live CSS custom properties; the YAML front-matter above remains the human source of truth for prose/rationale. The CI frontend job reds if this block drifts from `index.css` (Plan 4a §0.9B).

<!-- AUTOGEN:tokens BEGIN -->
_Auto-generated from `tutor-web/src/index.css` by `tools/generate-design-md.mjs`. Do not edit by hand — run `npm run design:check`._

### :root custom properties

| Token | Value |
|---|---|
| `--color-accent` | `#fde047` |
| `--color-accent-hover` | `#facc15` |
| `--color-accent-rule` | `#eab308` |
| `--color-accent-soft` | `#fefce8` |
| `--color-border-strong` | `#000000` |
| `--color-border-thin` | `rgba(0, 0, 0, 0.2)` |
| `--color-danger-bg` | `#dc2626` |
| `--color-danger-fg` | `#ffffff` |
| `--color-danger-text` | `#991b1b` |
| `--color-disabled-bg` | `#d1d5db` |
| `--color-disabled-fg` | `#6b7280` |
| `--color-info-bg` | `#bfdbfe` |
| `--color-overlay-fg` | `#ffffff` |
| `--color-page-bg` | `#ffffff` |
| `--color-page-fg` | `#000000` |
| `--color-panel-dark-bg` | `#000000` |
| `--color-panel-dark-fg` | `#fde047` |
| `--color-ring-focus` | `#facc15` |
| `--color-surface-muted` | `#fafafa` |
| `--delay-children` | `200ms` |
| `--duration-base` | `300ms` |
| `--duration-fast` | `200ms` |
| `--duration-slow` | `450ms` |
| `--ease-in` | `cubic-bezier(0.4, 0, 1, 1)` |
| `--ease-landing` | `cubic-bezier(0.22, 1, 0.36, 1)` |
| `--ease-out` | `cubic-bezier(0, 0, 0.2, 1)` |
| `--ease-standard` | `cubic-bezier(0.4, 0, 0.2, 1)` |
| `--radius-none` | `0` |
| `--shadow-hard` | `8px 8px 0 0 var(--color-border-strong)` |
| `--stagger` | `70ms` |
| `--type-body` | `clamp(14px, calc(13.5px + 0.1vw), 16px)` |
| `--type-h2` | `20px` |
| `--type-lg` | `18px` |
| `--type-sm` | `12px` |

### @theme utilities

| Token | Value |
|---|---|
| `--color-accent` | `var(--color-accent)` |
| `--color-accent-hover` | `var(--color-accent-hover)` |
| `--color-accent-rule` | `var(--color-accent-rule)` |
| `--color-accent-soft` | `var(--color-accent-soft)` |
| `--color-border-strong` | `var(--color-border-strong)` |
| `--color-border-thin` | `var(--color-border-thin)` |
| `--color-danger-bg` | `var(--color-danger-bg)` |
| `--color-danger-fg` | `var(--color-danger-fg)` |
| `--color-danger-text` | `var(--color-danger-text)` |
| `--color-disabled-bg` | `var(--color-disabled-bg)` |
| `--color-disabled-fg` | `var(--color-disabled-fg)` |
| `--color-info-bg` | `var(--color-info-bg)` |
| `--color-overlay-fg` | `var(--color-overlay-fg)` |
| `--color-page-bg` | `var(--color-page-bg)` |
| `--color-page-fg` | `var(--color-page-fg)` |
| `--color-panel-dark-bg` | `var(--color-panel-dark-bg)` |
| `--color-panel-dark-fg` | `var(--color-panel-dark-fg)` |
| `--color-ring-focus` | `var(--color-ring-focus)` |
| `--color-surface-muted` | `var(--color-surface-muted)` |
| `--ease-landing` | `var(--ease-landing)` |
| `--ease-standard` | `var(--ease-standard)` |
| `--shadow-hard` | `var(--shadow-hard)` |
| `--tracking-mega` | `0.3em` |
<!-- AUTOGEN:tokens END -->
