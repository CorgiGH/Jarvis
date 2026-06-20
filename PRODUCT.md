# Product

## Register

product

## Users

One primary user today: Alex — an AI-bachelor student at UAIC (BScIA), unmedicated ADHD, a strong visual learner with low prior programming confidence, who wants out of the "ask-AI / read-backwards" loop. He uses this while studying for real university exams (PA, ALO, PS, POO, SORC), under time pressure, often distracted. The job-to-be-done: take any raw course material he hands over (PDF, slides, lecture md) and turn it into something he can actually learn from — verified, visual, paced one concept at a time. Future users are the same shape (students of any subject); the system must stay subject-agnostic.

## Product Purpose

jarvis-kotlin is the tutor vertical of a personal life-OS — and the only active one. It is a whole-system, one-pass **digestion teaching engine**: ingest any media → classify (9 kinds) → digest → machine-verify → serve 5-beat lessons, drills, and mock exams → track mastery (FSRS) → revise, for any subject now or later. Success = a student reaches real technical depth on material he could not vet himself, trusting the content because (a) it is machine-verified true and (b) it never looks like generic AI output. The load-bearing division: **machines verify TRUTH; the user approves EXPERIENCE.** The user is never asked to judge correctness — when something is missing, the system asks for a FILE, not a verdict.

## Brand Personality

A 1960s Swiss International Style poster running in a terminal — not a SaaS landing page. Precise, confident, structural, calm. Mono voice everywhere, one yellow accent on a black-or-white field, hard black borders as structure, zero radius, zero gradient, zero soft shadow. Three words: **brutalist, focused, trustworthy.** The two non-negotiable emotional goals: keep attention on the ONE thing that matters right now (ADHD-first), and never look like generic AI output — because generic reads as untrustworthy, and untrustworthy content goes unread. Two surfaces only: LIGHT for sustained work/reading, DARK for hero/terminal/cinematic moments. Full visual system is in DESIGN.md (the ratified, drift-gated source of truth) — PRODUCT.md does not restate tokens.

## Anti-references

Stripe / Linear / Vercel marketing pages. Generic AI output: Inter/Roboto/Helvetica, blue/purple/teal palettes, `rounded-lg` cards, three equal columns, gradient backgrounds, soft drop-shadows, centered hero + subtitle. The "AI slop" test is explicit: if someone could look at a surface and say "AI made that," it failed. Also a specific in-house anti-reference (the SESSION-85 reckoning): a verified-but-ugly figure — a tiny element floating in dead space — is a FAIL, not a pass. Correctness alone is not the bar; the visual must be extraordinary. The donor app in `.scratch/donor/` is diagnosis-only, never a visual baseline.

## Design Principles

1. **Attention is the scarce resource.** One accent element in focus at a time; everything inactive is monochrome and still. No idle/ambient/entrance-pulse motion — toxic for ADHD focus. The interface earns trust by being calm.
2. **Never-generic = trustworthy.** Distinctiveness is not vanity; it is the trust mechanism. Generic AI aesthetics signal unvetted content. The brutalist-terminal voice is the credibility signal.
3. **Machines verify truth, the user approves experience.** Correctness is gated by non-LLM oracles, correct-by-construction, best-of-N. The human (never the user) only judges taste, and only on a thin spot-check. Never route content-correctness to the user.
4. **Intuition before jargon, depth on demand.** Lead with the simplified/visual layer (bars, plain words); a student must always be able to reach real technical depth (invariants, derivations, edge cases) as a separate concept-keyed artifact, not a reskin.
5. **Extraordinary, not "good."** Verified + gated is the floor, not the ceiling. A figure that passes the correctness gate but looks mediocre is unfinished. Design is led by the design tooling and looked at full-size, never hand-coded by reflex.

## Accessibility & Inclusion

Contrast is a mechanical gate, not a judgment call: body text ≥ 4.5:1, large ≥ 3:1, enforced by an axe `color-contrast` pass on the rendered DOM before any taste scoring. The yellow accent is a FILL behind black ink or a fg on the dark surface only — never yellow-on-white or white-on-yellow text (fails WCAG hard). Motion respects `prefers-reduced-motion` (reduce, not eliminate) AND a manual in-UI toggle, because an ADHD user may want motion off regardless of OS setting. Step controls (play / pause / step-back / speed) over autoplay — a pausable stepper teaches better than a beautiful autoplayer that can't stop. Verify UI at Alex's real resolution: 1536×864, viewport screenshots (`fullPage:false`), zero clip/overlap.
