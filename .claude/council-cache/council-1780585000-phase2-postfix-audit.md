# Council — phase2-postfix-audit (SESSION-54, workflow wayrajnng)

**Date:** 2026-06-04. 4 dimensions (regression · remaining-holes · missing-coverage · plan-coherence) → each finding verified against committed code → judge split broken-vs-missing. **23 raised / 23 confirmed.**

**Judge: HAS_LIVE_HOLES (conf 0.9) — trust-net HONEST-SAFE but was FUNCTIONALLY BROKEN.**

Honest-safe: F1 genuinely fixed (decideOutcome gates on bothSupported), every remaining defect fails CLOSED → the LOCKED no-false-faithful invariant HOLDS, no live false-faithful path. But broken the other way: a multi-claim poisoning bug made the locked "≥1 KC faithful" acceptance UNREACHABLE.

**BROKEN → FIXED this session (commit `c8e8d9d`):**
- CRITICAL multi-claim KC poisoning (one bug, 3 faces): runner conflated a claim's OWN verdict with a rolling SHARED kc_verification_status row (each claim read+mutated the row the next read; sticky transition table); + PA DEFINITION claims auto-routed to `failed` (SymPyLeg kind=SYMPY/ran=false → decideOutcome else → failed). pa-kc-005/006 (the only faithful targets) dragged to failed regardless of order; VerifyContentCli flattens all claims into one audit() = exactly the poisoning batch. Untested (only multi-claim test used 2 DIFFERENT KCs). FIX: per-claim verdict = transition(pending,outcome) (order-independent) → verification_audit.status; KC = aggregateKc conjunction; non-equational claims → NONE/uncertain floor not failed. + 4 regression tests.

**MISSING → folded into B5-B7** (see correctness-engine.md "POST-FIX AUDIT" section): D6 NLI adapter contract (UNCLEAR band load-bearing) + coverage monitor spec; F2 gate wiring at the 3 existing write-sites (GapPromotion kc-less = explicit ALLOW); report_wrong REVERIFY lifecycle (HIGH — today a report is a permanent trap); D9 provenance hygiene (stale content_hash/last_audit_run_id on report-wrong flip); content_hash source-SHA gap; pa-kc-005/006 tautological invariants → author real ones; F6-LOW residue (fuzzy_distance/page); DEGRADED-anchor faithful policy test.

**Key honest note:** on current PA content a KC aggregates to `uncertain` — real `faithful` requires the D6 NLI leg (the independent check for citation/rule claims). The acceptance (Bundle 7) genuinely depends on Bundle 5.
