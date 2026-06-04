# Council review — 1780439180 (plan re-audit, round 2)

**Context:** After council 1780438414 found 2 gaps, a close-then-reaudit workflow (wf_953f495f) authored the fixes (signatures-lock doc `2026-06-02-interface-signatures-lock.md`; master §6.1 dropped-decision reconciliation; B5 tag fix) then ran a 6-dim re-audit + 5-persona council + judge.

**VERDICT: CONDITIONAL** (unanimous across all 6 council agents + 6 re-audits; confidence 9).

**CLOSURE WORKED: PARTIAL.** The B5 tag + §2.3 freeze-pointer landed; the signatures-lock froze the 4 Phase-3→5 seams (QueueItem, Phase, PrereqGraph, MasterySparkline=**band**). BUT §6.1 reconciled D2/D1/calibration in PROSE without turning them into executable phase TASKS (the "ghost-task" pattern — Alex's CLAUDE.md cardinal failure: all phases green, keystone never ships); missed two more dropped roadmap rules; and introduced a dangling §7 cross-ref.

**CORE FINDING:** Phase 1 (data model) is unambiguously build-ready + code-grounded (KcMastery.kt:60 record() self-transacts; the 4 seams frozen; nothing blocks Phase 1 START). The PROGRAM-level plan is NOT freeze-ready: roadmap-LOCKED decisions reconciled in prose but never tasked, plus VerificationClaim has no field shape before Phase 2.

**REMAINING GAPS (8) — all block Phase 2+, NOT Phase 1 start:**
1. #8 emit() per-call citation chokepoint (roadmap §3.8 keystone "no un-cited claim reaches the learner") has ZERO task; VerificationGate.gate() @134 is an SR-entry gate, different site. → name a Phase-2 task w/ write-site before Phase 2.
2. #1 extraction-confidence-SCORE gate (roadmap §3.1 ingest step-0) has no task; line-78 ContentValidator is binary presence, not a garbled-but-present score. → ingest step-0 task.
3. D1-generator "WIRE NOW" reconciled in §6.1 prose but no Area-C task row (Area-C names only DrillGenParser + Problem.modelTag). → add task w/ read/write sites.
4. VerificationClaim is a frozen PARAM type (lock-doc:262) with NO field shape anywhere; ≥4 consumers + verification_audit.claim_id/kind constrain it. → freeze its shape BEFORE Phase 2 (the one un-frozen type in the anti-refactor core).
5. VerificationGate.gate(kc): ALLOW|DENY return enum + kc param not in lock doc → pin in Phase-2 freeze pass.
6. Dangling §7 cross-ref (§6.1 defers to "§7 breadth" but no §7 exists; plan ends ~263). → rename to "post-Phase-6 breadth" / add §7 stub.
7. Spine-vs-master contradiction: spine §4.2/§6 read async open-ended grading; master froze SYNC-200 (H13). → fix spine or add freeze-pointer.
8. model_tag contradiction: CHANGE-8 defaults modelTag null; D1/roadmap require relay-derived → stays null = silent #8 violation. → tie writer to the D1 task.

**NEEDS ALEX (5 decisions):**
1. D2 semantic embedder PARKED to post-skeleton — reverses a locked decision; confirm parking is OK.
2. Calibration gold-set gate DEMOTED from skeleton-acceptance (roadmap §12) to a breadth gate — sharpest; needs explicit sign-off.
3. D1-generator (Claude-on-generator) WIRE-NOW Phase 3 vs park — confirm in skeleton scope.
4. MasterySparkline = band (not series) — self-locked, vetoable; 1-line ack before Phase-5 freezes 6 sites.
5. Brand §2.1 brutalist default — one-time yes/no gating Phase-4 T5 merge.

Pattern: converging (round 1 = 2 structural gaps; round 2 = prose→task + 2 missed rules + type-shapes + doc contradictions = more concrete). 1–2 more close→audit cycles should reach clean.
