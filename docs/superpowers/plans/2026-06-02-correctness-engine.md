# Phase 2 — Correctness & Trust Engine (the trust-net) — TDD build plan

**Status:** build-time detail plan for master-impl-plan-v2 Area B. Written at GO (SESSION-54, 2026-06-04).
**Canonical contracts:** `interface-signatures-lock.md` §I, §I2, §J, §K, §L, §P, §Q (FROZEN — canonical-on-conflict).
**Already shipped (Phase 1 — do NOT rebuild):** `VerificationStatus` enum + `AuditOutcome` + `VerificationStatus_.transition` (`VerificationStatus.kt`); tables `verification_audit`, `kc_verification_status`, `report_wrong`, `exam_dates` (`Phase1Tables.kt`).

## Goal (LOCKED)
Every KC claim clears an OFFLINE audit — re-locate against the LIVE source PDF → two LLM families agree → ≥1 non-LLM leg passes → span↔claim round-trip — BEFORE entering SR. Certifies "faithful to your source," NEVER "correct." FAIL-LOUD: never-ran ≠ disagreed; resolve all legs BEFORE any DB write.

## Council refinements folded in (council 1780568136)
- **R0 — source corrected:** the real PA source-of-record is **`Curs 1 PA.pdf`** ("Algorithmic Language", Ciobâcă/Lucanu, PA 2019/2020) at `tmp-secondbrain-scrape/_fii/_gdrive/PA_Y1/Curs/curs_2020-2021/Curs 1 PA.pdf` — NOT `lecture01_comppb.pdf` (a different Feb-2026 lecture the master plan mis-named). 14/15 KC quotes already locate in it verbatim (49 form-feeds). Fix the filename in master-plan + data-model-lock + BRIDGE-HEAD/active-constraints.
- **R1 — hardened acceptance:** "≥1 KC FAITHFUL" alone is gameable by the DEGRADED/paraphrase path. Acceptance MUST require: (a) ≥1 KC FAITHFUL with `page_anchor_status=LIVE` against the real extraction; (b) the net correctly REJECTS a deliberately-corrupted KC (mutated quote ⇒ `failed`/`uncertain`, NEVER `faithful`); (c) proven on the real multi-page lecture, not a stand-in.
- **R2 — anti-contamination:** gate the proof lecture's groundedness independently (validateContent green on real extraction) BEFORE the verifier is declared proven; don't co-tune checker + its only gold lecture.

---

## Batch 0 — Source-of-record fix (task 0). Gate: `validateContent` green + form-feed test.
- Re-extract `Curs 1 PA.pdf` → `content/PA/_sources/pa-lecture-01.md` as RAW `pdftotext` output (form-feeds preserved, CRLF→LF per `SourceOfRecord.extract`).
- Reconcile the 1 non-verbatim quote `pa-kc-006` #3 ("The time cost of a computation is the sum…") → verbatim PDF text ("A computation (an execution) is a sequence of execution steps") — system-derived, NOT via Alex.
- Fix the mis-named PDF in: master-impl-plan-v2 (lines 161/233), data-model-lock, BRIDGE-HEAD §6 + active-constraints resume-recipe (flag for /wrap; do not hand-edit the auto:resume block — note it for regeneration).
- **Tests:** `SourceOfRecordExtractTest` — `_sources/pa-lecture-01.md` has ≥1 form-feed; `./gradlew validateContent` → 15/15 quotes locate (0 errors).

## Batch 1 — Leaf types + pure components (no LLM/DB deps). Gate: `:check`.
- §K types: `VerificationClaim`, `ClaimKind`, `NonLlmLegKind`, `NonLlmResult`, `fun interface NonLlmLeg`.
- §L: `LegFamily { RELAY, OPENROUTER, NONLLM }`.
- §J: `LiveSourceLocator.locate(rawSource, quote): LocateResult` + `LocateResult` + `PageAnchorStatus`. Builds folded-index→raw-offset map. Tests: pa-kc-005 `"\n  "` quote → LIVE span, `SourceOfRecord.slice` round-trips; whitespace-variant → DEGRADED/fuzzyDistance; absent → `span=null`; 0-form-feed source → `page=null`, NONE.
- §I2: `VerificationGate.gate(kc, status, hasOpenReportWrong): GateDecision` (pure). Tests: `faithful`+no-report → ALLOW; each of `{unverified,pending,uncertain,failed}` → DENY; `faithful`+open-report → DENY; attempt-count irrelevant.
- `ExtractionConfidence.score(rawText): Double` (P2-RULE1) + a `GARBLED_EXTRACTION` reason. Tests: mojibake/high-replacement-char fixture → below-threshold (error); clean fixture → pass; empty → ERROR (M-CSCHEMA).
- §Q: `CitedClaim` + `CitationGuard.attach(claim): CitedClaim`. Tests: `source==null` → THROWS; resolved → `CitedClaim{doc,span|page,quote}`.
- §P: `HonestFloor { FAITHFUL_TO_SOURCE, UNVERIFIED }` + derive. Tests: `faithful` → FAITHFUL_TO_SOURCE; every other → UNVERIFIED.

## Batch 2 — Verification legs (LLM families + non-LLM). Gate: `:check` (legs tested with fakes).
- `NonLlmLeg` impls: `SymPyLeg` (PA — shells to python/sympy on an INVARIANT/GRADER_RULE claim) ; `NoneLeg` (other subjects → `kind=NONE, ran=false` → UNCERTAIN floor). Tests: a true invariant → pass; a false one → !pass; NONE → ran=false.
- `TwoFamilyDeriver` — family A `RelayLlm` (RELAY) + family B `OpenRouterChatLlm :free` (OPENROUTER) independently re-derive the claim; agreement + family-collapse detect (both legs resolve same `LegFamily` ⇒ FAMILY_COLLAPSE). Tests (fake Llm): agree → agree=true; disagree → false; same configured family → collapse.
- `SpanClaimRoundTrip` — 3rd leg, DIFFERENT family: re-locate the span via `LiveSourceLocator`, read back `SourceOfRecord.slice`, confirm it supports the claim. Tests: faithful span → pass; mutated quote → fail.

## Batch 3 — Runner + persistence (FAIL-LOUD, offline batch). Gate: `:check`.
- `VerificationRunner.audit(claims, …)` — per claim: try/catch each leg (thrown → record as such, never crash mid-txn); compute `AuditOutcome`; `VerificationStatus_.transition`; RESOLVE all legs BEFORE any write; then write `verification_audit` (one row/claim/run) + upsert `kc_verification_status` (B8). Distinguishes never-ran (family unprovisioned/NONE) from disagreed (H5). No `faithful` without BOTH non-LLM-pass AND families-agree.
- Tests: all-agree+nonllm-pass+roundtrip → `faithful`; thrown LLM leg → `uncertain`/`failed` (NEVER silent faithful); NONLLM NONE → `uncertain` floor; family collapse → `uncertain`; re-audit idempotent on `audit_run_id`.

## Batch 4 — curate-tutor Stage-9 reconcile + author pa-kc-005/006. Gate: `:check` + validateContent.
- Stage-9 reconcile (after `validateContent`): set `verification_status=pending` + content-hash `claim_id`s (M-CLAIM) + emit `VerificationClaim`s; idempotent; NEVER regress `faithful→pending` (H10).
- Author `pa-kc-005`/`pa-kc-006`: `invariant` + `grader_rules` + vision-confirmed span from the real PDF (system-derived; no oracle inversion, H8). These are the FAITHFUL-target KCs.
- Tests: reconcile twice → stable claim_ids, no regress; authored KCs validate strict (invariant≠null, grader_rules non-empty, span present).

## Batch 5 — Routes + Gradle task. Gate: `:check`.
- `GET /api/v1/verify/{kcId}/status` — reads `kc_verification_status` (B8); builds `claims: List<CitedClaim>` via `CitationGuard.attach`; `honest_floor` derived (§P); `badge_text` pinned ("matches your lecture" / "unverified"), NEVER "verified correct".
- `POST /api/v1/admin/verify/{kcId}` — owner-only, offline, runs `VerificationRunner.audit`.
- `POST /api/v1/fsrs/{id}/report-wrong` — writes `report_wrong` (resolution=OPEN), pauses the card; gate flips KC via REPORT_WRONG → pending.
- `verifyContent` Gradle task — owner/manual offline ONLY, **NOT** `dependsOn(check)` (H6); aborts if a family env var is missing (FAIL-LOUD).

## Batch 6 — End-to-end acceptance (HARDENED, R1). Gate: real-relay proof + reject-proof + `:check`; off-box DB dump first.
- Take a fresh off-box DB dump (done this session: `jarvis-db-backups/…-033740`; re-dump if mutating live).
- Run `admin/verify` (offline batch, real relay + openrouter) on the authored pa-kc-005.
- **ACCEPTANCE (all must hold):**
  1. ≥1 KC reaches `faithful` with `page_anchor_status=LIVE` against the real `Curs 1 PA.pdf` extraction.
  2. A deliberately-corrupted KC (mutated quote / wrong invariant) is REJECTED (`failed`/`uncertain`), NEVER `faithful` — proves the net can say "not faithful".
  3. Every claim on `/verify/{kcId}/status` carries a resolved `SourceRef` (CitationGuard holds).
- Record the proof in `docs/superpowers/findings/`.

---
**Execution:** sequential TDD batches, each `./gradlew :check`-gated before commit (the proven Phase-1 cadence). PM-delegation: a build subagent per batch; Claude gates live-DB writes + the Batch-6 acceptance. Relay must be AWAKE for Batch 2/6 real-LLM tests (confirmed healthy this session).

---

## DESIGN UPDATE — SESSION-54 (2 councils + Alex; approved 2026-06-04)

Batch-6 acceptance hit a real wall: family B = `OpenRouterChatLlm :free` is 429-throttled (the parked FreeLLMAPI problem) AND the SymPy leg can't run under this Windows box's WindowsApps Store python via `ProcessBuilder` (empty output). Two councils (`1780568136` factcheck-two-family-vs-persona, `1780583533` factcheck-pc-side-audit) + Alex reshaped the design. All four changes below supersede the corresponding earlier text.

- **D6 — Family B becomes a LOCAL NLI/entailment model, not a free chatbot.** Council `1780568136` (CLAUDE_PARTLY_RIGHT, conf 0.86): same-model-different-persona = false independence (correlated errors / self-preference — Huang 2310.01798, Tyen 2311.08516, Wataoka 2410.21819); a weaker-but-DIFFERENT model is fine on the narrow source-faithfulness/NLI task (MiniCheck 2404.10774: a 770M model ≈ GPT-4 at ~400× less cost). **Better than a 2nd chatty free LLM: a frozen NLI checker (MiniCheck-class / DeBERTa-v3-NLI, ~0.4–0.8 GB).** Genuinely architecture-independent (uncorrelated with any generative LLM), deterministic, zero-API, low-variance — AND it kills the OpenRouter-429 blocker entirely (no 2nd API call). Family B in the audit = this NLI model; the OpenRouter leg is demoted to optional/fallback. **Add a coverage monitor** so a noisy 2nd leg doesn't silently bury correct cards under "unverified" (council's R2 — the user's legitimate worry; don't wave it away).

- **D7 — The audit runs OWNER-SIDE on the PC; the VPS is SERVE-ONLY.** Council `1780583533` (GOOD_WITH_CONDITIONS, 3/4). The audit is already an offline owner/manual batch (`VerifyContentCli`), leg-A (the relay) already runs PC-side, and the PC is definitionally ON at audit time (Alex adds lectures from it). The VPS has only ~1.9 GB free next to a 4 GB-pinned JVM — loading even a 0.6 GB model there risks OOM-killing the live box, and audit-on-VPS still calls the PC relay over Tailscale anyway → buys nothing. So the NLI model + SymPy + relay all run on the PC; the VPS never loads the model.

- **D8 — CONTENT-HASH GATE (real bug fix; do regardless of topology).** Councils verified: `kc_verification_status` is keyed on `kc_id` ALONE (`Phase1Tables.kt:108`) with NO content hash — the hash lives only in `verification_audit.claim_id`. So if a lecture is edited after an audit, the live gate/badge still reads `faithful` and the badge LIES ("matches your lecture" vs text never checked). **Fix:** add `content_hash` (sha256_8 of the audited claim text+span, + the git SHA) to `kc_verification_status`. Enforce in TWO places: (a) **serve** — `/verify/{kcId}/status` shows `faithful`/"matches your lecture" only if `hash(current serving content) == row.content_hash`, else falls to `HonestFloor.UNVERIFIED`; (b) **sync** — push a verdict only when the VPS row's `content_hash` matches the audited hash. Makes staleness / partial-sync / PC-off all fail CLOSED to honest "unverified", never fail-stale to a lying "faithful".

- **D9 — PC→VPS verdict SYNC = surgical one-way upsert (NEW component).** NEVER copy `tutor.db` PC→VPS (the VPS holds irreplaceable FSRS/attempt/session/report_wrong state that exists nowhere on the PC). The sync is a content-hash-keyed per-KC UPSERT of **audit-owned columns ONLY** (`kc_verification_status.{status, content_hash, last_audit_run_id}` + `verification_audit` rows), over Tailscale with bearer auth. **Carve-out:** skip any `kc_id` with an OPEN `report_wrong` on the VPS (never overwrite/close it, never un-pause a card the learner flagged). VPS is read-only for content + verdict columns; content edits originate on the PC → git deploy. `verifyContent` stays FAIL-LOUD if anyone runs it on the VPS without the model.

**Net effect on the batches:** Batch 2's family-B leg gains an `NliLeg` (local model) as the primary independent checker; Batch 1/3 add the `content_hash` column + serve/sync gate; a new **Batch 7 = PC→VPS verdict sync** (surgical upsert + report_wrong carve-out). Batch-6 acceptance re-runs on the PC with the NLI model (unblocks the live FAITHFUL that OpenRouter+Store-python blocked). Council transcripts: `.claude/council-cache/council-1780568136-*`, `council-1780583533-*`.

---

## PHASE-2 REMAINING — QUALITY AUDIT + LOCKED 6-BUNDLE ORDER (SESSION-54)

A quality audit (`council-1780580000-phase2-audit`, 27 raised / 26 confirmed, judge SOUND_WITH_FIXES conf 0.9) found the engine matches the frozen seams field-for-field + the runner is FAIL-LOUD/resolve-before-write, but with **1 live CRITICAL + over-claim footguns**. A sequencing council (`council-1780584000-fix-bundling`, judge ADJUST conf 0.83) tightened Claude's 4-bundle proposal into the **locked 6-bundle order below**. This supersedes the "Batch 6/7" sketch above for the REMAINING Phase-2 work.

**Audit must-fixes (verified in code):**
- **F1 (CRITICAL, only LIVE false-faithful path):** `TwoFamilyDeriver.agree` (TwoFamilyDeriver.kt:41) ignores verdict POLARITY — both families REFUTED ⇒ agree=true ⇒ `VerificationRunner.decideOutcome` case-3 (VerificationRunner.kt:210) ⇒ `faithful`. Certifies the exact claims it should reject. Untested. (pa-kc-005's tautological `1+1+1=3` invariant makes it concretely exploitable.)
- **F2 (HIGH):** `VerificationGate.gate` has ZERO production callers — net LABELS but ENFORCES nothing. (Wire at EXISTING Phase-2 write-sites only; the `/drill/grade` SR-admission read-gate is Phase-3.)
- **F3 (HIGH):** `CitationGuard.attach(claim)` 1-arg (CitationGuard.kt:50) mints `faithful` from span-presence — the FROZEN §Q is the unsafe form; Phase-3 grade-serve is its future caller. Fix now.
- **F4 (MED):** `faithful` authorable as a YAML seed + served via `resolveStatus` fallback with zero legs run.
- **F5 (MED):** serve stamps KC-level status onto every claim (TrustRoutes.kt:221) instead of per-claim verdicts.
- **F6 (MED):** report-wrong on a null-kcId card writes an orphan row keyed on `''` (TrustRoutes.kt:310). + LOW: audit row hardcodes `fuzzy_distance=0`/authored page; GRADER_RULE prose gets the KC invariant (SymPyLeg falsely passes).

**LOCKED BUILD ORDER (council-final; build top→bottom):**
1. **VERDICT HONESTY** — F1 (fix BOTH `TwoFamilyDeriver.agree` to demand SUPPORTED==SUPPORTED AND `decideOutcome` case-3 to assert agreed-verdict==SUPPORTED) **+ F4-serve** (kill the `resolveStatus` YAML-seed `faithful` fallback → no B8 row = `unverified`). Ship together so "no false-faithful served status" is TRUE the moment it lands. Acceptance: a REFUTED+REFUTED claim AND a zero-legs KC both serve `unverified`.
2. **TOPOLOGY GUARD** — convert `POST /admin/verify` (TrustRoutes.kt:241-286) from in-handler `runBlocking{runner.audit}` to CLI-only/proxy. MUST precede any D6 model code (else a request-thread model-load on the 1.9 GB VPS).
3. **STALENESS GATE** — D8 `content_hash` (add column to `kc_verification_status` + define KC-level hash; **edits the FROZEN signatures-lock — coordinate ONE frozen-file edit with F3**) THEN serve/read enforcement (`hash(current)!=row ⇒ HonestFloor.UNVERIFIED`). **D8 before F2.**
4. **EMIT SAFETY** — F3 (replace the frozen 1-arg `attach` with the audited-status form) + F5 (per-claim verdicts from `verification_audit`; **reads Bundle-1's runner-written rows**) + F4-author (drop `faithful` from `ContentValidator.AUTHORED_VERIFICATION_STATUSES`) + F6. One pass (all in TrustRoutes.kt / ContentValidator.kt).
5. **ENGINE SWAP** — D6 (NLI family-B leg + `LegFamily` slot §L + Llm-typed Leg adapter; **confirm the NLI adapter's SUPPORTED/REFUTED/UNCLEAR contract BEFORE Bundle-1 internals**) + reconcile the `VerifyContentCli` FAIL-LOUD env-gate in ONE edit (OPENROUTER→optional + VPS-loud) + F2 wired at cold-start-seed / GapPromotion.promote (kc-less carve-out) / B1 upsert ONLY.
6. **TOPOLOGY TAIL + SYNC** — D7 back half (PC-side confirmed, VPS serve-only) + D9 (surgical content-hash-keyed PC→VPS upsert; OPEN-report_wrong carve-out; never whole `tutor.db`).
7. **ACCEPTANCE RE-RUN (LAST)** — via the OFFLINE `verifyContent` CLI (NOT `/admin/verify`); off-box DB dump first; prove (a) ≥1 KC `faithful` with `page_anchor_status=LIVE`, (b) a corrupted KC REJECTED.

**Phase-2 / Phase-3 line:** Phase 2 = bundles 1–6 + the acceptance, with F2 gating only EXISTING write-sites. **Phase 3 = the SR-admission READ-gate on `/drill/grade`** (trust-blind today, TutorRoutes.kt:1924 — net-new, stays deferred). Do NOT pull it into Bundle 3 (the scope-creep the council cut).

**Must-build-before (hard edges):** F1-agree + F1-decideOutcome same bundle · F4-serve with F1 · D7 route-extraction before any D6 model code · D8 before F2 · F3+D8 = ONE coordinated frozen-lock edit · F5 reads Bundle-1's audit rows · confirm D6 adapter contract before Bundle-1 internals · Bundle-7 acceptance via the offline CLI, not the admin route.

Audit + sequencing transcripts: `.claude/council-cache/council-1780580000-phase2-audit.md`, `council-1780584000-fix-bundling.md`.

---

## POST-FIX AUDIT (SESSION-54, workflow `wayrajnng`) — STATE + MISSING ITEMS

23 raised / 23 confirmed. Judge: **trust-net is HONEST-SAFE but was FUNCTIONALLY BROKEN** — no false-faithful path exists (F1 holds; every defect fails CLOSED), but a multi-claim poisoning bug made the locked "≥1 KC faithful" acceptance UNREACHABLE.

**FIXED this session (commit `c8e8d9d`):** the multi-claim KC poisoning (CRITICAL) — per-claim verdict now order-independent (`transition(pending,outcome)` per claim), KC = `aggregateKc` conjunction, DEFINITION/non-equational/prose-GRADER_RULE claims floor to NONE/uncertain instead of `failed`. Tests cover the same-KC multi-claim + shuffle-order + one-disagreeing-claim + honest-uncertain cases. **On current PA content a KC honestly aggregates to `uncertain` — real `faithful` needs the D6 NLI leg (Bundle 5).**

**MISSING — fold into the bundles (build-later, NOT live bugs):**
- **Bundle 5 (engine swap) — write 2 contracts BEFORE wiring:** (a) D6 NLI adapter output contract — raw type + probability→`SUPPORTED|REFUTED|UNCLEAR` thresholds, with a load-bearing UNCLEAR band (low-confidence entailment must be UNCLEAR, never silently SUPPORTED, or it re-opens a false-faithful seam); (b) coverage monitor spec (council R2) — metric = % audited KCs stuck `uncertain` because a leg never ran, by subject + blocking-leg; sourced from the `verification_audit` boolean cols (no schema change) + the `VerifyContentCli` byStatus summary; explicit fail-loud threshold.
- **Bundle 5 (gate wiring, F2):** `VerificationGate.gate` has ZERO production callers. Wire at cold-start seed (`FsrsSeedMain`) / `GapPromotion.promote` (kc-less path = EXPLICIT documented ALLOW, a gap card has no KC to deny) / B1 `upsertRubricCriterion`; supply `hasOpenReportWrong` from a real OPEN-row query; per-site "non-faithful KC produces no card" test. (Phase 3 owns the `/drill/grade` SR-admission read-gate.)
- **Bundle 5/6 — `report_wrong` REVERIFY lifecycle (HIGH):** today `resolution` is write-OPEN-only; `REVERIFIED_FAITHFUL`/`RETRACTED` appear in zero code; nothing un-pauses a report-paused card or closes a report → a learner report is a permanent trap + permanent gate-DENY. Spec + build (owner re-audit returning faithful closes the report + un-pauses), or explicitly defer in writing.
- **Bundle 6 (D9 sync) provenance hygiene:** report-wrong's faithful→pending flip leaves `content_hash` + `last_audit_run_id` STALE (`TrustRoutes.kt` sets only status+updatedAt) — NULL them on report-wrong (fail-closed); `last_audit_run_id` is currently write-only/dead (D9 reads it).
- **Contract/content reconciliation (low, fold into the D8/lock pass):** D8 `content_hash` omits the source-file/git-SHA — editing `_sources/{doc}.md` without touching the KC YAML leaves a faithful badge stale-true; reconcile correctness-engine "+ git SHA" vs the FROZEN lock (which dropped it). Annotate signatures-lock §Q write-site #2 (`/drill/grade` emit) as Phase-3-activated. **pa-kc-005/006 invariants are SymPy tautologies (`1+1+1=3`, `t+t+t=3*t`) — author real, non-tautological invariants** (the tautology contributes no independent signal; the NLI leg is the real check). F6-LOW residue: audit row hardcodes `fuzzy_distance=0` + authored page — thread the real `LocateResult` through. A `faithful`-on-DEGRADED-anchor policy test (the acceptance demands LIVE).

Post-fix audit transcript: `.claude/council-cache/council-1780585000-phase2-postfix-audit.md`.

---

## B5-RESHAPE — per-claim-kind `faithful` + badge decoupled from `faithful` (SESSION-54, Alex GO 2026-06-04)

**Why this section supersedes "B5 = swap family-B + wire gate" as the immediate next move.** A spike proved the local NLI model runs + judges prose correctly + the JVM→py3.12 bridge works (`council-1780598020`). A corpus DRY-RUN (`tools/nli_dryrun.py`, all 6 real PA KCs) then measured **0/6 faithful-eligible** and a **structural** cause, confirmed by two grounded councils (`council-1780600422-coa-post-spike`, `council-1780601847-claim-model-reshape`, 6/6 each):

- `aggregateKc` (VerificationRunner.kt:118-122) = `faithful` IFF **every** claim faithful. Every KC emits ≥1 **DEFINITION** claim (ContentReconcile.claimsFor, one per source ref, `content = ref.quote`). A DEFINITION has no equation ⇒ `SymPyLeg` returns NONE/`ran=false` (NonLlmLegs.kt:46-48) ⇒ `decideOutcome` case-4 `NONLLM_LEG_NONE` ⇒ uncertain, **never faithful**. ⟹ **No KC can EVER reach `faithful`** under the current model. Structural, not calibration. D6 (the NLI leg the plan banked on) does **not** fix it (dry-run: NLI correctly returns UNCLEAR on the bare equation `1+1+1=3` and on the literal `sympy: …` grader-rule string; self-entailment on DEFINITION `content==quote` is zero-signal).

**LOCKED-invariant re-scope (Alex blessed 2026-06-04 — was `MUST_REVISIT`).** signatures-lock.md:224 §2.5 "no `faithful` without a non-LLM-leg pass AND families-agree" is **structurally unsatisfiable for prose**. RE-SCOPE the "non-LLM leg" per claim kind: **SymPyLeg for equational claims; SpanClaimRoundTrip (live re-locate) for prose/DEFINITION claims** (the round-trip IS the deterministic non-LLM check for prose). This is an amendment of a frozen decision, recorded with sign-off — NOT a silent widen.

**Chosen design = council option A (per-claim-kind routing) + badge-decouple. The reshape:**
1. **`decideOutcome` keyed by `ClaimKind`:**
   - **DEFINITION / non-equational prose** → faithful iff `roundTrip.pass && !anyThrew` (round-trip = the prose non-LLM leg). The LLM/NLI self-vote on `content==quote` is **NOT** counted (zero independent signal); if an NLI vote is kept it must run on an INDEPENDENT restatement, never the verbatim quote.
   - **INVARIANT / GRADER_RULE (equational)** → faithful iff `bothSupported (on an NL RESTATEMENT of the invariant, not the bare equation) && sympy.ran && sympy.pass && roundTrip.pass && !anyThrew`.
   - The `sympy: …` grader-rule string is a SymPy directive — **strip it from the NLI/LLM path entirely**; route only the equation to SymPy.
2. **Badge decoupled from `faithful`.** `badgeTextFor` / `HonestFloor` "matches your lecture" is driven by **`SpanClaimRoundTrip.pass` against the LIVE source** (a served "lecture-grounded" signal), NOT by the `faithful` status. `faithful` stays the rarer strong "machine-verified" tier. **Build decision (confirm at TDD):** prefer DERIVING the badge from the stored round-trip result over adding a new `VerificationStatus` enum value (the enum is frozen + widely consumed; deriving keeps the frozen surface intact). A KC may light "matches your lecture" at status `uncertain`.
3. **Self-entailment guard (mandatory):** a test asserting `NLI(premise==hypothesis)` can NEVER alone produce faithful/grounded; the machine anchor for a DEFINITION is round-trip against the LIVE source (re-located, never trusted-as-authored) — guards the false-faithful regression.

**ACCOMMODATION LEDGER (downstream of this change — Alex's standing ask, [[feedback_account_for_accommodations]]):**
| Consumer | Change needed | Built? |
|---|---|---|
| `TrustRoutes.badgeTextFor` (TrustRoutes.kt:143-146) | badge from round-trip result, not `faithful` | BUILT — small edit |
| `HonestFloor` (HonestFloor.kt:29) | add/feed a "lecture-grounded" served signal | BUILT — small edit |
| Phase-3 SR-admission gate + `/queue` (VerificationGate; signatures-lock:76,254,267) | admit on the "lecture-grounded" tier, NOT only `faithful` (else admits ~nothing) | **NOT built — plan update; the catch that justified measuring first** |
| `VerificationStatus` enum (VerificationStatus.kt:17) | likely NO new value (derive badge) — confirm at TDD | frozen — avoid touching |
| B7 acceptance (this doc step 7) | "≥1 KC lecture-grounded LIVE" + "≥1 genuine machine-checkable KC faithful (if any authored)" + corrupted-quote REJECTED | plan update |
| signatures-lock.md:224 §2.5 | amend invariant text to the per-kind re-scope + sign-off note | frozen doc — amend |
| Content hygiene (follow-up, not the unblock) | add `span` to pa-kc-001..004; author real non-tautological invariants + NL restatements for 005/006; fix `sympy:` rule routing | content task |

**Build order (TDD, per-step :check green):** (B5r-1) `decideOutcome` per-kind rule + self-entailment guard test → (B5r-2) badge/HonestFloor decouple to round-trip → (B5r-3) NL-restatement field for equational claims + `sympy:`-string strip → (B5r-4) content hygiene (spans + real invariants) → (B5r-5) re-run `nli_dryrun` + the offline acceptance CLI: prove ≥1 KC lights "matches your lecture" LIVE and a mutated quote is REJECTED. Gate F2 stays default-OFF until (B5r-5) shows a non-trivial grounded-rate. D6 (NLI leg as family-B) still lands — but as the strong-tier signal for equational claims, NOT the prose unblock.

Reshape councils: `.claude/council-cache/council-1780600422-coa-post-spike.md`, `council-1780601847-claim-model-reshape.md`. Dry-run harness: `tools/nli_dryrun.py` (+ `tools/nli_spike.py`, `tools/pb_probe.jsh`).
