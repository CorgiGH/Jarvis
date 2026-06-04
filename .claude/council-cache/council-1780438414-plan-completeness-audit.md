# Council review — 1780438414

**Problem:** The recorded state claims "PLANNING IS DONE + council-hardened, freeze-ready" (`master-impl-plan-v2.md`). Alex suspects it is NOT actually done — especially the design — and wants it audited before any building (Phase 1 = data model is the gated next build step).

**Method:** Workflow `plan-completeness-audit-council` (wf_4cbe6d0c) — 5 read-only audits of the real docs → 5-persona council → judge.

**Timestamp:** 2026-06-02T22:13:34Z

---

## Audit findings (condensed)

- **DESIGN — PARTIAL.** Only the door (1 of 29 surfaces) is genuinely designed + rendered. The other 28 are a strong TEXT spine (layout-gist + component names + focal claim). The spine itself marks **15 surfaces `self-see: YES`** = visual/taste must be render-decided at build time — incl. the keystone drill loop. **No rendered design exists for any surface but the door.** Open decisions: brand §2.1 (gates all), Subject-Map columns, voice-unlock contradiction, glossary list.
- **BACKEND — Phase 1 DONE/freeze-ready** (every column/type/backfill/cull/FK concrete, code-verified, all 43 ledger issues folded). **Phase 2 PARTIAL** (shape locked; blocked on the real PA PDF + pa-kc-005/006 strict-tier authoring for its ≥1-FAITHFUL acceptance). **Phase 3 PARTIAL** (API wire shapes locked; algorithm bodies + §2.3 internal interface field-shapes prose-only).
- **COVERAGE — faithful on the verification-net** (10 safety rules + 5 guardrails all have homes), BUT **silently dropped a LOCKED decision: D2 local embedder + VectorStore/HybridRetriever semantic-leg navigation** (zero in v2, absent from the Deferred table). Also under-specified: D1 Claude-on-generator, calibration GOLD-SET gate (only `/calibration` display shipped), fișă-disciplinei ingest.
- **GAPS — all 43 ledger issues truly folded (0 unfolded).** Genuine opens = 9: blocking-later (PA PDF, KC authoring, brand §2.1, MasterySparkline prop, ProgressStrip R3) + non-blocking (6 per-phase TDD docs unwritten, voice, Subject-Map, far_transfer wiring). Nothing blocks Phase 1 START.
- **READINESS — Phase 1 freeze-ready; the §2.3 cross-phase Kotlin signatures (PhaseModel/NextKcSelector/QueueItem/Phase/PrereqGraph) are prose arg-lists with NO return types/field shapes — verified zero exist in code.** These feed Phase 5's 6 MasterySparkline consumers → wrong shape = 6-site codemod = the "redesign 5000×" cascade. Also: `stage0-exit-gate.md` already exists yet v2 line 155 says "write — B5".

## Council (5 personas)

- **Devil's Advocate — CONDITIONAL.** Most damaging incompleteness = §2.3 internal interfaces are unfrozen return-type/field contracts masquerading as locked seams; plus the dropped D2 means the plan isn't internally complete on its own locked scope. Build Phase 1, but don't let it EXIT without (a) a real §2.3 signatures file + (b) D2/D1-gen/calibration-gate in an explicit Deferred-or-Now decision.
- **Domain Expert — CONDITIONAL.** "Master plan now + per-phase TDD at build time" is a sound established pattern (Parnas information-hiding / stable-interface-deferred-implementation / Last Responsible Moment) — IF the frozen layer is the cascade-forcing one. Schema is correctly frozen; two seam-classes aren't (§2.3 signatures; dropped D2). Deferring test bodies/algorithm internals is legit; deferring cross-phase data/signature shapes is not.
- **Pragmatist — CONDITIONAL.** Do NOT block on rendering 28 surfaces or writing 6 TDD plans — that's over-planning that buys nothing (Phase 1 schema is independent). The ONE must-finish-before-Phase-1-exits = promote §2.3 to a frozen-signatures file. Rendering the 15 self-see surfaces now would be the throwaway-mockup proliferation already banned.
- **First Principles — CONDITIONAL.** "Planning is done" conflates internals (fine to defer) with seam signatures (not). Real readiness blocker = interface-signature freeze, NOT design and NOT backend bodies. Design-taste deferral only burns far-downstream frontend rework; Phase 1 doesn't touch it.
- **Risk Analyst — CONDITIONAL.** Phase 1 freeze-ready (all 5 dims confirm). Highest-cascade risk = §2.3 signatures; biggest UNOWNED gap = D2 silently dropped from a locked decision with no Deferred entry. The dangerous illusion is "Phase 1 clean ⇒ rest clean" — it's the inter-phase contracts, not the schema, that carry the rework risk.

---

## Judge

VERDICT: **CONDITIONAL** (5 CONDITIONAL, 0 APPROVE, 0 REJECT — unanimous; all five converge on the identical §2.3-signature-freeze + D2-park precondition). CONFIDENCE: 9.

CORE FINDING: "Planning is DONE" is overstated, but the gap is narrow and Phase-1-safe — don't over-correct to "stop everything." Phase 1 (data model) is genuinely frozen + code-verified; build it. The real rework risk Alex fears lives in exactly two unfrozen seams: (1) §2.3 cross-phase Kotlin interfaces are prose with no Phase/QueueItem/PrereqGraph field shapes (verified zero exist in code), feeding Phase 5's 6 MasterySparkline consumers; (2) a LOCKED decision — D2 local embedder + VectorStore/HybridRetriever navigation — was silently dropped (absent from v2 + the Deferred table). Deferring per-phase TDD test bodies is legitimate and NOT the problem.

MUST FINISH (ordered):
1. Phase 1 START is safe now — schema independent of every open item.
2. GATE Phase 1's MERGE/EXIT on ONE new artifact: §2.3 promoted from prose to a real frozen-signatures file (return types + Phase/QueueItem/PrereqGraph fields + MasterySparkline prop + LiveSourceLocator/NonLlmLeg types).
3. Same pass: explicit Deferred-table entry (or "wire now") for D2 embedder + D1 Claude-on-generator + calibration gold-set GATE.
4. Fix stale "write — B5" tag (stage0-exit-gate.md exists → "execute B5").
5. The 4 small taste/decisions resolved in-place at their surface (brand §2.1 before Phase-4 T5; ProgressStrip R3 before 0c/0d/0e; far_transfer/self_explanation wiring) — NOT up front; do NOT render the 15 self-see surfaces before their components exist.
6. PA PDF + pa-kc-005/006 authoring = Phase-2 inputs, not Phase-1 blockers.

DESIGN STATUS: NOT done as rendered design (1/29 surfaces); the 28-surface text spine routes 15 self-see surfaces to render-at-build-time — legitimate just-in-time, does NOT block Phase 1.
BACKEND STATUS: Phase 1 freeze-ready (build now); Phase 2 build-ready-in-shape (gated on PDF + authored KCs for acceptance); Phase 3 wire-contracts locked but §2.3 field-shapes prose-only (freeze before Phase 1 exits).

Output saved to: .claude/council-cache/council-1780438414-plan-completeness-audit.md
