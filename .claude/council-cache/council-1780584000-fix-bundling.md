# Council — phase2-fix-bundling (SESSION-54, workflow wflova705)

**Date:** 2026-06-04. 4 reviewers (arch-deps · refuter · phase-boundary · critical-path) + judge. Reviewed Claude's proposed 4-bundle re-sequencing of the Phase-2 audit fixes + next-steps.

**Judge: ADJUST (4/4 ADJUST, conf 0.83).** Directionally right (F1-first correct) but file-locality grouping split the ONE honesty invariant across 3 bundles, mis-ordered 2 seams, and pulled Phase-3 admission wiring into Phase-2.

**Locked 6-bundle build order (folded into correctness-engine.md "PHASE-2 REMAINING"):**
1. **Verdict honesty** — F1 (both halves: agree polarity + decideOutcome case-3, demand SUPPORTED==SUPPORTED) + F4-serve (kill YAML-seed faithful fallback). Acceptance: REFUTED+REFUTED and zero-legs KC both serve `unverified`.
2. **Topology guard** — `/admin/verify` in-handler → CLI/proxy. BEFORE any D6 model code.
3. **Staleness gate** — D8 content_hash (edits frozen lock; coordinate with F3) THEN serve/read enforce. D8 before F2.
4. **Emit safety** — F3 (replace frozen 1-arg attach) + F5 (per-claim verdicts, reads Bundle-1 rows) + F4-author + F6.
5. **Engine swap** — D6 (NLI leg + LegFamily slot + adapter; confirm SUPPORTED/REFUTED/UNCLEAR contract first) + VerifyContentCli env-gate reconcile + F2 at existing write-sites only.
6. **Topology tail + sync** — D7 back half + D9 (surgical content-hash-keyed PC→VPS upsert, OPEN-report_wrong carve-out).
7. **Acceptance re-run (last)** — offline `verifyContent` CLI (NOT /admin/verify); off-box dump first; prove ≥1 faithful @ page_anchor=LIVE + a corrupted KC rejected.

**Phase-2/3 line:** Phase 2 = bundles 1–7, F2 gating only existing write-sites. **Phase 3 = the SR-admission read-gate on `/drill/grade`** (trust-blind today, TutorRoutes.kt:1924 — net-new, deferred). The "pull /drill/grade admission into Bundle 2" idea = the scope-creep cut.

**Hard must-build-before edges:** F1-agree + F1-decideOutcome same bundle · F4-serve with F1 · D7 route-extraction before any D6 model code · D8 before F2 · F3+D8 = ONE coordinated frozen-lock edit · F5 reads Bundle-1's audit rows · confirm D6 adapter contract before Bundle-1 internals · Bundle-7 acceptance via the offline CLI.
