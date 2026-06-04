# Council — phase2-audit (SESSION-54, workflow w4adhe5j3)

**Date:** 2026-06-04. 4 review dimensions (code-bugs · trust-integrity · plan-coherence · next-steps-coherence) → each finding adversarially verified against the real code → judge. **27 raised / 26 confirmed.** Severity: 2 CRITICAL, 7 HIGH, 12 MEDIUM, 5 LOW.

**Judge: BUILT QUALITY = SOUND_WITH_FIXES · NEXT STEPS = ALIGNED_WITH_ADJUSTMENTS (conf 0.9).**

**Core:** the engine matches the frozen seams (§I/§I2/§J/§K/§L/§P/§Q) field-for-field; runner is hermetic + resolve-before-write + FAIL-LOUD; round-trip deterministic; pa-kc-005/006 round-trip diacritic-exact. BUT 1 live critical + over-claim footguns ⇒ not "done".

**Confirmed must-fixes (the ones folded into correctness-engine.md's locked order):**
- **F1 CRITICAL (only LIVE false-faithful path):** `TwoFamilyDeriver.agree` (TwoFamilyDeriver.kt:41) ignores verdict polarity → both-REFUTED ⇒ agree=true ⇒ `VerificationRunner.decideOutcome` case-3 (kt:210) ⇒ `faithful`. Untested. pa-kc-005's `1+1+1=3` tautology makes it concretely exploitable.
- **F2 HIGH:** `VerificationGate.gate` has ZERO production callers — net LABELS, ENFORCES nothing (ghost-component). Wire at existing Phase-2 write-sites only; `/drill/grade` admission-gate is Phase-3.
- **F3 HIGH:** `CitationGuard.attach(claim)` 1-arg (kt:50) mints `faithful` from span-presence; the FROZEN §Q is the unsafe form; Phase-3 grade-serve is the future caller.
- **F4 MED:** `faithful` authorable as YAML seed + served via resolveStatus fallback with zero legs run.
- **F5 MED:** serve stamps KC-level status onto every claim (TrustRoutes.kt:221), not per-claim.
- **F6 MED + LOW:** report-wrong null-kcId orphan row (kt:310); audit hardcodes fuzzy_distance=0/page; GRADER_RULE prose gets the KC invariant (SymPy false-pass).

**Calibration confirmed:** the multi-claim "last-write-wins" findings DOWNGRADED to MEDIUM — the transition table is sticky (uncertain/failed never auto-clear to faithful), so a KC with ≥1 bad claim ends uncertain/failed under every ordering. Only F1 is a live false-faithful bug.

**Next-steps adjustments (D6-D9):** right direction; D8 must edit the frozen lock + cover the admission READ path; D6 collides with the OPENROUTER env gate + needs a LegFamily slot/adapter; D7's /admin/verify route is a request-path violation; spec the coverage monitor. → all folded into the locked 6-bundle order (see `council-1780584000-fix-bundling.md`).
