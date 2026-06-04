# Council review — 1780412110 — Master Implementation Plan

**Problem:** Is the master implementation plan's set of LOCKED contracts (data model, API, interfaces, verification state machine) + build order correct and complete enough to prevent the mid-build refactoring it exists to prevent?
**Artifact:** `docs/superpowers/plans/2026-06-02-master-impl-plan.md`
**Ground rules (OUT OF BOUNDS):** timing/deadlines, scope/defer, content-not-authored-yet, UI-displacement, sequencing-vs-engine. Judge contract correctness only.
**Timestamp:** 2026-06-02

All 5 agents read the live repo + cited line numbers.

## 🔴 Devil's Advocate — REJECT
Multi-KC cardinality contradiction. The live grade route is one-problem-to-MANY-KCs: `Problem.kcIds: List<String>` (`PdfProblemExtractor.kt:25`), recorded in a loop `serverProblem.kcIds.forEach { kcId -> repo.record(...) }` (`TutorRoutes.kt:2024-2027`). But the plan freezes everything on a SCALAR `kc_id`: the `attempts` table (CHANGE 5), the extended `drill/grade` req/resp, the B1 single-card upsert, scalar `phase`/`next_phase_action`/`kc_quarantined`. A multi-KC problem can't be represented → forces re-keying `attempts`, re-shaping the grade envelope to arrays, rewriting B1 to "upsert the set" — exactly the churn the plan exists to prevent. **Lock the per-attempt→per-KC relationship as one-to-many (array fields or a defined per-KC fan-out) BEFORE freezing CHANGE 5 / grade extension / B1.**

## 📚 Domain Expert — APPROVE
The migration strategy is textbook expand-contract: every new column nullable-or-DB-defaulted (so `createMissingTablesAndColumns`, which only ADDs, succeeds on the hot 871-row DB), enums-as-VARCHAR avoids the unportable native-pg-enum trap, 3-value `status` VARCHAR over a boolean is right (booleans can't expand to QUARANTINED|PAUSED without a 2nd migration). Matches the codebase's own convention (`prediction`/`giveUp`/`Problem.kcIds` are already additive-nullable). The interface seams (`PhaseModel`/`NextKcSelector`/`VerificationGate`/`ScaffoldPlanner`) are strategy-pattern ports at the right boundary — the PFA/Thompson swap is caller-free; `PhaseModel` as SOLE writer of phase inside `record()`'s txn is the correct single-writer invariant. jsonl→`attempts` orphans no reader (mastery already lives in `kc_mastery`; jsonl is an audit event log). Keystone-first order is sound because the engine seams are co-frozen in §2.3.
KEY CONCERN: the B1 atomic txn inherits the existing `record()` read-then-write (TOCTOU, documented single-user-safe); the Phase-3 detailed plan must state every B1 write routes through ONE `transaction(db){}` block + an upsert (not insert+catch) so it can't half-commit. Contract is correct; nail the txn boundary at build time.

## ⚙️ Pragmatist — CONDITIONAL
Seams are mostly right + repo-grounded (kc_mastery PK `(userId,kcId)` with `record()` as txn boundary; `source_ref` genuinely `varchar(32)` occupied so "add kc_id" is correct; schema reg at `TutorRoutes.kt:2603`; `DrillStack.StackPhase` lacks rung/confidence; `ProgressStrip` uses `rounded-full` R3 violation). Three hidden TBDs: (b) "curate-tutor emits verification_status=pending" — curate-tutor is a SKILL.md PROSE procedure, NOT wired to write verification_status/claim_id/verification_audit; the UNVERIFIED→PENDING edge has no code event that fires it. (c) §2.2 specifies req/resp but NOT the auth envelope (`jarvis_session` cookie / `csrfProtect` / `aiLiteracyGate` / owner-scope) every existing route enforces. (d) `MasterySparkline` frozen against `/api/v1/mastery` which returns a SCALAR `ewma_score` per KC — no time series, no `mastery_history` table; a "sparkline" implies a trend, so ≥1 of the 6 consumers wants history the frozen shape can't supply → codemod across 6 sites.
KEY CONCERN: the YAML-KC `verification_status` ↔ runtime DB-card reconciliation seam. KCs load from disk (`ContentRepo`, no DB); `fsrs_cards`/`kc_mastery` are SQLite; `GapPromotion.promote()` writes cards with no `kc_id`. The plan names `VerificationGate.gate(kc)` as the chokepoint but never defines WHO loads the corpus, how a `fsrs_cards.kc_id` soft-ref resolves to its YAML KC's live `verification_status`, what invalidates that on REPORT-WRONG, or how the gate applies to GAP_PROMOTION cards with no kc_id. Define the single resolver before Phase 2/3.

## 🧱 First Principles — CONDITIONAL
Category error: the plan freezes the CONTAINERS (columns, envelopes, interface signatures) but leaves the CONTENT of the container — the KC schema, the unit of knowledge every layer reads — designed as "add nullable fields so the 6 PA KCs deserialize unchanged," NOT derived from the domain. Verified: today's `KnowledgeConcept` holds only metadata + source quotes; CHANGE 3 adds the entire teaching+verification payload (`invariant`, `grader_rules`, `stem_template`, `phase_plan`, `far_transfer_stem`, `self_explanation_prompt`, `verification_status`) at once, all optional, validated only for the single `strict` computational case. A flat `invariant: String` + `grader_rules: List<String>` is shaped by SymPy-checkable algebra; a PS proof-obligation or SO concurrency-invariant changes the field's MEANING (not presence) → cascades through `NonLlmLeg`/`TwoFamilyDeriver`/`VerificationGate`/`DrillGenerator`. Freezing fsrs_cards/endpoints is genuinely correct; the mistake is treating the KC schema as solved when it's the maximally-depended-upon, least-domain-derived contract.
KEY CONCERN: derive CHANGE 3's `invariant`/`grader_rules`/`stem_template`/`phase_plan` shape from at least one NON-PA subject's real unit of knowledge (a PS proof or SO systems KC) before locking — confirm the same fields hold a proof-obligation + a systems-invariant without re-typing, OR type them subject-discriminated (sealed/variant) now. Else "added fields" is deferred churn wearing a frozen label.

## ⚠️ Risk Analyst — CONDITIONAL
Migration is the SAFE part: `createMissingTablesAndColumns` (Exposed 0.55) only ADDs (never ALTERs/drops on SQLite) so the 871 rows + count invariant hold; the source_ref→kc_id backfill mirrors the proven `MigrateConceptRefs.kt` UPDATE. **CRITICAL = VerificationGate bypass:** `POST /api/v1/gap/{id}/promote` (`TutorRoutes.kt:1528`) → `GapPromotion.promote(db, ledgerDir, gapId)` → `cardRepo.insert(...)` directly, from a `KnowledgeGap` that has NO kc and gets `kc_id=NULL` (CHANGE 1). So `VerificationGate.gate(kc)` has no `kc` to gate against — unverified gap content reaches the SR corpus through a side door the gate's signature can't intercept. **HIGH:** the B1 cross-check leg runs a free LLM that can 429 mid-grade; the stated fail-safe (non-faithful ⇒ skip writes) holds ONLY if a 429/relay failure is MAPPED to non-faithful/uncertain, not thrown inside the open txn.
KEY CONCERN: the gap-promotion bypass. Mitigation (must be in the Phase-1/2 detailed plan): either (a) gate `/gap/{id}/promote` to create cards with `status='QUARANTINED'` (excluded by CHANGE 1's `WHERE status='ACTIVE'` filter) until cleared, OR (b) explicitly carve gap-promotion out of §2.3 as user-self-authored / intentionally-ungated and document it. Pick one; leaving §2.3 as-is ships a gate with a hole its own signature created.

---

## Sanity Check
SANITY Devil's Advocate: PASS — cited `PdfProblemExtractor.kt:25` + `TutorRoutes.kt:2024-2027`; the one-to-many cardinality contradiction is real + decisive.
SANITY Domain Expert: PASS — additive-migration / strategy-pattern / event-log reasoning correct + repo-verified; APPROVE with a build-time txn-boundary note.
SANITY Pragmatist: PASS — three TBDs + the YAML↔DB resolver seam all concrete + line-cited.
SANITY First Principles: PASS — KC-schema-not-domain-derived is a genuine deep refactor source, verified against the current `KnowledgeConcept`.
SANITY Risk Analyst: PASS — the gap-promote bypass cited at `TutorRoutes.kt:1528`; gate signature genuinely can't cover a kc-less path.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The plan's architecture is sound — the migration discipline, interface seams, and keystone-first sequencing are textbook-correct (Domain Expert APPROVE, repo-verified). But it is not yet safe to freeze: four contracts are wrong or undefined in ways that WOULD force the exact mid-build refactor the plan exists to prevent. This is the planning-first process working — these were caught on paper, not after building against them.

AGENT CONSENSUS: 1 REJECT, 3 CONDITIONAL, 1 APPROVE — 0 flagged; all 5 repo-grounded.

KEY ISSUES (the 4 contract gaps to fix before freezing):
1. **Multi-KC cardinality (REJECT, must-fix).** The live grade path is one-problem-to-many-KCs (`kcIds: List`), but `attempts` / the grade envelope / B1 are frozen on scalar `kc_id`. Lock the per-attempt→per-KC relationship one-to-many now.
2. **VerificationGate bypass (CRITICAL).** `/gap/{id}/promote` inserts FSRS cards from a kc-less `KnowledgeGap`; `gate(kc)` can't intercept it. Decide: promote-as-QUARANTINED, or explicitly carve gap-promotion out of the gate.
3. **KC schema not domain-derived (deep).** The unit-of-knowledge fields (`invariant`/`grader_rules`/`stem_template`/`phase_plan`) are shaped to PA-computational only. Derive from a non-PA subject (proof/systems) or type them subject-discriminated before locking CHANGE 3.
4. **YAML-KC ↔ DB reconciliation + 3 TBDs.** Define the single resolver (corpus load, `fsrs_cards.kc_id`→live `verification_status`, REPORT-WRONG invalidation); wire curate-tutor to actually emit `pending` (it's prose today); specify endpoint auth; resolve `MasterySparkline` scalar-vs-trend (add history or rename).

RECOMMENDED PATH:
Fold these 4 into the master plan, then it's freeze-ready:
- §2.1/§2.2: make `attempts` + grade envelope per-KC one-to-many (array or fan-out with a per-KC `phase`/`verification_status`/`recorded` vector).
- §2.3: define `VerificationGate` to cover the gap path (promote→QUARANTINED) OR carve it out explicitly.
- CHANGE 3: type the KC teaching/verification fields subject-discriminated (or validate the shape against one PS/SO unit) before freezing.
- Add a "runtime resolver" contract (KC-corpus load + kc_id→verification_status lookup + invalidation) + endpoint auth envelope + a `mastery_history` source (or rename MasterySparkline to a single-value pip).
- Build-time note (Domain Expert): B1 = one `transaction{}` + upsert; map LLM 429 → uncertain, never thrown in the open txn.

CONFIDENCE: 8
All findings are line-cited against the live repo. What would lower the need: the KC-schema item (#3) is partly a judgment call on how different PS/SO units really are — confirmable only when a non-PA subject is modeled, so subject-discriminated typing now is the safe hedge.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780412110-master-impl-plan.md
