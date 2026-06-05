# Downstream / Accommodation Ledger — Phase-2 trust-net fix-plan (D1–D4)

**For:** `build-review/2026-06-05-trustnet-fix-plan.md` (council verdict FLAWED → fix-then-build).
**Produced:** 2026-06-05, SESSION-55. Per [[feedback_account_for_accommodations]] — account up-front for everything downstream of these contract moves before any code lands.
**Ground-truth re-verified against `main` + live `~/.jarvis/tutor.db` this session** (anchors at the end).

---

## VERDICT (3 lines)

1. **NOT fully contained — the fixes are sound and fail-safe, but D1+D2+D3 collectively MOVE SIX FROZEN seams** in `interface-signatures-lock.md` (canonical-on-conflict) and `data-model-lock.md`, so they REQUIRE an explicit Alex re-freeze, not just code.
2. **Scope does NOT expand into new phases** — every impact lands inside Phase-2's already-owned surface (trust-net audit + serve gate + content_hash); the future-phase touches (D9/B6, Phase-3 grade txn, Phase-5 TrustBadge) are *forward spec-constraints on still-unbuilt work*, not refactors of shipped code. Blast radius is **~0 right now**: live trust tables verified 0 rows, D9 verified unbuilt, live DB is pre-D8 4-col shape.
3. **One BLOCKER class must be sequenced, not just noted:** D1+D2 re-key EVERY `content_hash`; D9 is LOCKED to key its upsert ON `content_hash`. The plan's "do the v2 re-key + ONE re-audit while tables are empty, build D9 after" ordering is the only safe path — encode it as a hard gate, not a footnote.

---

## Area 1 — Data model & D9 sync

### 1a. New `source_span_hash` column (D1) — additive, safe, but widens the audit-column set everywhere

**What moves.** `KcVerificationStatusTable` (Phase1Tables.kt:108–150) gains a 7th column:
`val sourceSpanHash = varchar("source_span_hash", 16).nullable()` (width pinned to `varchar(16)` = `sha256_8`, mirroring `contentHash`; if full sha256 is ever wanted, pin it in the lock at the same time).

**What relies on it.**
- The serve gate (`servedHonestFloor`/`resolveStatus`, TrustRoutes.kt:204–273) — a NULL `source_span_hash` must fail CLOSED → UNVERIFIED, exactly like the existing `content_hash` NULL path. The serve side can ONLY presence/version-check it (the VPS has no `_sources` bytes — **D7**); it CANNOT recompute it.
- The audit writer `finalizeKc` (VerificationRunner.kt:157–190) — must compute + write it in the SAME upsert/txn as `status`/`content_hash`/`lecture_grounded`.
- D9 PC→VPS sync (UNBUILT) — must carry it as a synced VALUE.

**Must be accommodated.**
- **Migration is purely additive + idempotent + needs NO backfill.** VERIFIED: the live `kc_verification_status` is the BARE pre-D8 4-column shape (kc_id, status, last_audit_run_id, updated_at) — it is missing `content_hash` AND `lecture_grounded` too. At next boot `createMissingTablesAndColumns` (via `TutorMigration.migrate`) will ALTER-ADD all three (content_hash, lecture_grounded, source_span_hash) in one pass; each NULL on the 0 existing rows = fail-closed = correct. No separate migration task — just add the column to the table object; confirm it lands via the existing ALL_TABLES path. Disjoint from the 828-card `fsrs_cards` corpus (keyed on kc_id) → no card-DB ordering hazard.
- **Detection is PC-side, never serve-side.** The actual source-edit DETECTION is a PC reconcile/watcher leg (D1 step 4) that NULLs `content_hash`+`source_span_hash` and re-pends. Do NOT add any `_sources` read to the serve path (breaks D7; a serve-time throw risks degrading to a silent pass, breaking FAIL-LOUD).
- **Hash basis is the round-trip's OWN `fold`, not raw bytes.** `source_span_hash = sha256(fold(located-slice))` where `fold(s) = s.replace(Regex("\\s+")," ").trim()` (SpanClaimRoundTrip.kt:52), aggregated per-KC sorted by claimId. Hashing raw bytes flips on whitespace/CRLF/re-OCR churn → drops pa-kc-002/004 (the forbidden false-negative). Thread the already-round-tripped `slice` (SpanClaimRoundTrip.kt:43) out to the runner.

**Frozen seam broken?** YES (see Area 2.1/2.2) — both lock docs freeze the column set WITHOUT this column.

### 1b. `content_hash` FORMULA change (D1+D2) → v2 prefix → re-key — the D9 BLOCKER

**What moves.** Two edits inside the single `kcContentHashOf` (ContentReconcile.kt:285–300):
- D2: fold `c.invariant ?: ""` into the `listOf(...)`.
- Step-0: prepend a `"v2:"` schema-version prefix.
Both ride through the ONE function that both audit-side (`kcContentHashOf(claimsFor(kc))`) and serve-side (`kcContentHash(kc)`, :274) flow through → the FROZEN identity `kcContentHash(kc) == kcContentHashOf(claimsFor(kc))` is preserved automatically. This is the safe leg of the formula change.

**What relies on it.** `content_hash` is BOTH the D8 staleness fingerprint AND the **D9 surgical-upsert match KEY** (LOCKED "keyed on content_hash", active-constraints.md:24). The v2 prefix + invariant term change EVERY `content_hash` value.

**Must be accommodated — BLOCKER ordering.**
- A D9 upsert keyed on the OLD hash would match ZERO VPS rows after the flip (silent no-op, not an error → orphan rows). MITIGATION IS ALREADY CORRECT IN THE PLAN and re-verified here: live `kc_verification_status` / `verification_audit` / `report_wrong` all = **0 rows**, AND D9 is unbuilt (no `syncAudit`/`upsertAudit`/`surgical` impl in any `.kt`). So there is nothing to re-key on the VPS NOW.
- **Hard gate (add as an explicit task):** assert "no live `content_hash` rows exist at formula-flip time" before flipping. The hazard exists ONLY if D9 ships BEFORE the formula change — hence the LOCKED order: flip formula + add column + ONE re-audit while tables empty → build D9 after, with the new column + new keyspace baked in.
- **D9's column allowlist must be AUTHORED (not refactored) to the widened set** `{status, content_hash, lecture_grounded, source_span_hash, last_audit_run_id}` from the start. Current D9 carve-out names only `content_hash` + `last_audit_run_id`. The sync KEY stays `content_hash`; `source_span_hash` is a synced VALUE. Skip-OPEN-`report_wrong` stays honored (D3 darkens locally; the OPEN dispute never syncs — `ReportWrongTable` is user-scoped, not an audit col, and is NOT in the D9 set).

**Frozen seam broken?** The formula text is frozen (Area 2.2) → YES for the lock; the D9 contract SHAPE (surgical, hash-keyed, skip-OPEN, never whole tutor.db) does NOT change — only its keyspace + column-set, which are still-unbuilt spec.

### 1c. ONE re-audit + ONE re-sync (Build-Order step 0)

**What moves.** After Step-0 (v2 prefix + source_span_hash column + D2 invariant fold), exactly ONE re-audit re-hashes all KCs under the v2 formula and stamps fresh `content_hash` + `source_span_hash`. This also subsumes the already-known Phase-2 leftover "re-audit the live corpus (legacy stale-grounded rows)" (active-constraints.md:17) and closes the legacy stale-grounded defect M1 for free (v2 prefix ⇒ every legacy row deterministically mismatches ⇒ fails closed).

**Must be accommodated.** Do NOT land D2 alone then D1 later (= two re-keys / two re-syncs). The re-audit must re-confirm pa-kc-001..004 stay faithful under v2 (final green gate); they are pure DEFINITIONs (no invariant), so `c.invariant ?: ""` is empty for them — only the v2 prefix moves their hash, which the re-audit refreshes. Because D9 is unbuilt, the "re-sync" is currently a NO-OP, but the re-audit itself is mandatory and must precede any first D9 sync.

**Frozen seam broken?** No (operational task), but it is a BLOCKER gate on the green of the whole change.

---

## Area 2 — Frozen contracts / lock docs

> These are the moves that REQUIRE an Alex re-freeze decision. `interface-signatures-lock.md` is canonical-on-conflict; if the lock text and the code diverge, the lock wins — so the lock MUST be amended in the SAME edit that lands each change, or the lock becomes a lie about the live contract.

### 2.1 §I.1 column set — one-hash → two-hash scheme  **[BREAKS FROZEN — BLOCKER]**

Lock §I.1 (interface-signatures-lock.md:226–242) freezes the D8 fingerprint as a SINGLE column with a SINGLE serve-gate predicate (`content_hash: varchar(16) NULLABLE`, line 232). D1 widens this to a TWO-hash scheme. The lock must:
- add `source_span_hash` to the frozen column list on `kc_verification_status`,
- name its `fold()` basis (SpanClaimRoundTrip.kt:52) and the PC-side detection seam (reconcile/watcher nulls content_hash + source_span_hash + re-pends),
- state explicitly that detection is NOT a serve-side recompute (VPS has no `_sources` — D7).
Additive to the table (0 live rows) → migration cheap; but the FROZEN one-hash description moves.

### 2.2 §I.1 canonical formula text — v1 → v2  **[BREAKS FROZEN — BLOCKER]**

Lock §I.1 line 236/240 freezes the canonicalization as `kind|content|doc|page|span` and marks it "**The hash (FROZEN)**". Two corrections needed at re-freeze:
- **Pre-existing doc drift (note it):** the live code already folds `kind|content|doc|page|spanStart|spanEnd` (6 terms — span split into start/end, ContentReconcile.kt:290–297), not the lock's `…|span`. The lock text was already mildly stale; fold this correction in at the same edit.
- **The actual move:** add the raw `invariant` term AND the `v2:` prefix. This changes every stored hash's VALUE = the definition of moving a frozen contract. Amend the lock to the v2 formula with an explicit note that v1 hashes deterministically mismatch and fail closed. Re-prove the FROZEN identity `kcContentHash(kc) == kcContentHashOf(claimsFor(kc))` holds post-change (it does — invariant travels in YAML on both sides; the change is inside the shared function).
- Every doc quoting the old canonicalization (`correctness-engine.md`, `b5-reshape-decisions.md`, the D8 KDoc on Phase1Tables.kt:113–123) must be updated.

### 2.3 §I.1 serve-gate predicate — add OPEN-report fail-closed term (D3)  **[BREAKS FROZEN — BLOCKER]**

Lock §I.1 line 242 freezes: `resolveStatus` returns `faithful` ONLY when `row.content_hash != null && kc != null && row.content_hash == kcContentHash(kc)`; gate applies ONLY to faithful. VERIFIED: live `resolveStatus` (TrustRoutes.kt:204–227) and `servedHonestFloor` (:248–273) match this exactly — neither reads `ReportWrongTable`. D3 adds a THIRD fail-closed input: an OPEN `report_wrong` for the kc forces UNVERIFIED regardless of hash match. The lock §I.1 serve-gate paragraph + the §N wire-shape row for `/verify/{kcId}/status` must both record it. **FAIL-LOUD:** a query throw ⇒ "assume OPEN" ⇒ UNVERIFIED.

### 2.4 §I.1 writer contract — finalizeKc relight-guard (D3)  **[MOVES, doc-only]**

Lock §I.1 line 241 says the runner "stamps content_hash alongside status" UNCONDITIONALLY. VERIFIED: `finalizeKc` (VerificationRunner.kt:157–190) writes `contentHash`+`lectureGrounded` with NO `ReportWrongTable` read. D3 makes it a conditional state-machine edge: (a) skip `grounded=true`/`contentHash` while an OPEN row exists; (b) write `REVERIFIED_FAITHFUL` atomically before relight. The `report_wrong.resolution` enum `{OPEN|REVERIFIED_FAITHFUL|RETRACTED}` is ALREADY frozen (data-model-lock:408) → NO enum/schema change. But the runner's behavioral contract gains a real exit edge `REPORT_WRONG→pending→{REVERIFIED_FAITHFUL|RETRACTED}`; the §2.5 state-machine + the §I.1 writer line must document it. Also extend the writer line from "stamps content_hash" to "stamps content_hash AND source_span_hash" (D1).

### 2.5 §I.1 must FIRST be reconciled to reality (`lecture_grounded`)  **[BREAKS FROZEN — MUST]**

Pre-existing post-lock drift that the re-freeze must clean up FIRST: `lecture_grounded` (Phase1Tables.kt:147) is LIVE in code and read by `servedHonestFloor`, but is absent from BOTH lock docs — §I.1 and data-model-lock CHANGE-10 freeze `kc_verification_status` WITHOUT it. The served badge is keyed off `servedHonestFloor` (grounded OR faithful), not raw `resolveStatus` faithful (lock §P/§I.1 still describe the old faithful-only gate). D1's `source_span_hash` and D3's OPEN-report term both interact with the grounded path (report_wrong already CLEARS `lecture_grounded`). The re-freeze MUST fold `lecture_grounded` into §I.1 at the same time, or the lock stays a lie about the live trust contract.

### 2.6 data-model-lock CHANGE-10 — enumerate ALL audit columns  **[BREAKS FROZEN — MUST]**

CHANGE-10 (data-model-lock.md:410) defines `kc_verification_status` as exactly 4 columns (kc_id, status, last_audit_run_id, updated_at). The live Kotlin table already silently drifted to 6 (content_hash + lecture_grounded, B5-RESHAPE); D1 adds a 7th. The frozen CHANGE-10 must be amended to enumerate ALL audit columns (content_hash, lecture_grounded, source_span_hash) or the next phase building against the lock authors a stale migration. This is schema-of-record drift to reconcile in the canonical frozen file, not just code.

### 2.7 §I2 VerificationGate caller-set vs the new D3 serve consultation  **[decision; doc-only either way]**

`VerificationGate.gate(kc, status, hasOpenReportWrong)` (§I2) already NAMES `hasOpenReportWrong` as its third input — but VERIFIED it has ZERO production callers; `TrustRoutes` does not call it. The frozen caller-set is exactly {SR-entry, cold-start seed, GapPromotion.promote, B1 upsert} (admission only). D3 adds an OPEN-report consultation at the SERVE seam — a NEW consultation CLASS the frozen seam never enumerated. **Decision required (Area 4):** either reuse `VerificationGate.gate` at the serve seam (widening its frozen caller-set + re-specifying "default-OFF" so it can't accidentally disable the D3 serve refusal), OR D3 uses a separate inline `ReportWrongTable` lookup and the gate stays admission-only. Must be pinned before Phase-3 wires F2, else the gate's contract is ambiguous across two consultation classes.

---

## Area 3 — Future phases & roadmap (forward spec-constraints, not refactors)

| Future surface (status) | Constraint D1–D4 imposes |
|---|---|
| **D9/B6 PC→VPS sync** (LOCKED, UNBUILT) | Author it AFTER the v2 re-key; key on `content_hash`; carry the widened audit-col set incl. `source_span_hash`; keep skip-OPEN-report_wrong + surgical + never-whole-tutor.db. (Area 1b.) |
| **PC-side reconcile / curate-tutor Stage-9** (Phase-2 Batch-4) | D1's watcher INTENTIONALLY regresses faithful→pending on a real source-byte change — a deliberate, NARROWED exception to H10 (never-regress-faithful). Write it into the Stage-9 contract: regress ONLY when source bytes actually changed, never on a no-op re-run (idempotent); parallel to MF-2's report_wrong clearing path. Otherwise the two rules collide. |
| **Real-PDF source ingestion / re-OCR** (Phase-2 open input) | This is the exact event that fires D1's watcher. Re-ingestion must capture the located slice through the SAME `fold()` (SpanClaimRoundTrip.kt:52); future PDF re-OCR + authoring of pa-kc-005/006's vision-confirmed spans must guarantee it, or new KCs flap on first re-OCR. Hard gate: pa-kc-001..004 stay faithful constrains every future source-ingestion change. |
| **Phase-3 atomic grade txn** (P3-GEN / recordIn + finalizeKc) | D3's OPEN-report query + REVERIFIED_FAITHFUL write + relight-guard must be ATOMIC with finalizeKc's grounded write (same txn; a crash between re-opens the relight hole). Phase-3's "LLM-resolve-outside-txn then ONE transaction{}" contract must accommodate this extra query + conditional. |
| **Phase-3 queue/today filter** (SHOULD) | Phase-3 pre-generates+persists drills. An OPEN report must flip the KC OUT of queue/today (queue already OMITS non-faithful), not just darken the badge — else a disputed KC keeps serving pre-generated drills with no badge. Queue-filter must consult the SAME OPEN-report signal D3 adds to serve. |
| **Phase-5 TrustBadge / report_wrong UI** (Surface 15) | TrustBadge currently degrades only on kc_id-null / non-faithful; it must ALSO reflect OPEN-report → UNVERIFIED. An owner-reachable exit path (owner CLI / admin-verify) MUST exist before D3 ships, or every report is a permanent badge-dark trap (DoS-on-truth). No learner-facing moderation UI in scope (multi-actor moderation DEFERRED). |
| **Multi-user / auth** (DEFERRED §6) | D3's "owner" = the SINGLE-user owner-only admin/verify caller, a PC-side owner-triggered re-audit (D7-legal), NOT a moderator actor (none exists). Park as a known follow-on: who may write RETRACTED vs REVERIFIED_FAITHFUL across users is a multi-user-era concern. Record the single-owner assumption so the multi-user phase revisits it. |
| **Coverage monitor** (UNBUILT Open-item) | Build it AFTER the v2 formula lands so it counts the right hash version; count `source_span_hash` presence as part of "faithful coverage" (NULL ⇒ not-yet-covered, fails closed); detect the v2: boundary so legacy-prefixed rows report as stale, not faithful. |
| **ContentValidator / authoring** (D4) | D4 adds a syntactic `lhs.trim()==rhs.trim()` reject (hard fail) + a `simplify`-trivial soft warning to the PC-side author/audit path, as an additional all-errors-together condition in the frozen ContentValidator strict rule. ZERO serve/sync/hash/schema ripple; no frozen signature moves. Do NOT over-reject meaningful identities (`|n|_unif = 1`, `2x = x+x`) — syntactic-identity is the hard fail; simplify-trivial is a flagged warning. Future pa-kc-005/006 real invariants + far_transfer stems must pass D4. Lands LAST. |

**me/delete cascade (D3):** `report_wrong` is already user-scoped + in the me/delete cascade (data-model-lock Task 13); REVERIFIED_FAITHFUL/RETRACTED only UPDATE `resolution` in place → no new table, no cascade change.

---

## (1) Lock docs to update (at re-freeze, SAME edit that lands the code)

1. **`docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` §I.1** — (a) add `source_span_hash` column + fold-basis + PC-side detection seam [2.1]; (b) rewrite canonical formula text to v2 (`invariant` term + `v2:` prefix; fix the pre-existing `span`→`spanStart|spanEnd` drift) [2.2]; (c) add OPEN-report fail-closed term to the serve-gate predicate + the §N `/verify/{kcId}/status` wire row [2.3]; (d) extend the writer line to "stamps content_hash AND source_span_hash" + the conditional relight-guard edge [2.4]; (e) fold in `lecture_grounded` to reconcile §I.1 to live reality [2.5]; re-prove the frozen `kcContentHash == kcContentHashOf(claimsFor)` identity post-change.
2. **`docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` §I2 / §2.5** — record the D3 closing-edge lifecycle `REPORT_WRONG→pending→{REVERIFIED_FAITHFUL|RETRACTED}`; resolve the VerificationGate serve-vs-admission caller-class decision [2.7].
3. **`docs/superpowers/plans/2026-06-02-data-model-lock.md` CHANGE-10** — enumerate ALL audit columns (content_hash, lecture_grounded, source_span_hash) [2.6].
4. **`docs/superpowers/plans/2026-06-02-data-model-lock.md` Task 11 (ContentValidator)** — add the D4 vacuous-invariant reject to the strict rule set (completeness; not a frozen-contract move).
5. **`docs/superpowers/plans/2026-06-02-correctness-engine.md` (§B5-RESHAPE / D8 + Stage-9)** — update the old `content_hash` canonicalization quote to v2; write the narrowed H10 exception (regress only on real source-byte change) into the Stage-9 reconcile contract.
6. **`docs/superpowers/findings/2026-06-04-b5-reshape-decisions.md`** + the D8 KDoc on `Phase1Tables.kt` — update every place quoting the old fold formula.
7. **`.claude/active-constraints.md` LOCKED-decisions (D8/D9)** — note the v2 formula + widened D9 audit-col set once Alex re-freezes.

## (2) Safe build / sequencing order (future dev not blocked)

- **Step 0 (one pass, while tables empty — the BLOCKER gate):** v2: prefix + `source_span_hash` column + D2 invariant fold → ONE re-audit (re-stamps content_hash + source_span_hash for all live KCs) → assert "no live content_hash rows existed before the flip" + pa-kc-001..004 re-reach faithful under v2. Re-sync is a NO-OP today (D9 unbuilt).
- **1. D2** (one field into `kcContentHashOf`) — rides the shared hash; lands IN the Step-0 redefinition.
- **2. D1** (separate PC-computed `source_span_hash` over `fold(located-slice)` + PC-side reconcile/watcher detection) — folded into the SAME re-key/re-audit as D2 (exactly ONE re-key, ONE re-audit, ONE re-sync).
- **3. D3** (serve OPEN-report refusal + finalizeKc relight-guard + ONE owner-driven closing transition — ship all three together, no DoS-on-truth deadlock) — independent of the re-key; can run parallel to/after D1+D2.
- **4. D4** (reject tautological invariants at author/audit time) — fully isolated, lands last.
- **After Step 0:** build D9/B6 against the widened keyspace + audit-col set; build the coverage monitor against the v2 boundary; wire F2 (default-OFF) with the §2.7 decision already pinned.
- **Final green gate (whole change):** pa-kc-001..004 regression test asserts all four stay `faithful` after every defect lands AND after the v2 re-key. Per-phase TDD: each defect RED-test-first.

## (3) Decisions Alex must make BEFORE building

- **D-RF1 — Re-freeze the trust-store lock.** Approve moving SIX frozen seams in §I.1 + CHANGE-10 ([2.1]–[2.6]): two-hash scheme, v2 formula, OPEN-report serve term, conditional writer, lecture_grounded reconciliation, full audit-column enumeration. The lock is canonical-on-conflict — this is a deliberate re-freeze, not drift. (Also pin: `source_span_hash` width = `varchar(16)` sha256_8, OR full sha256.)
- **D-RF2 — VerificationGate consultation class ([2.7]).** Reuse `VerificationGate.gate` at the serve seam (widen its frozen caller-set + re-spec default-OFF so it can't disable the D3 serve refusal), OR keep the gate admission-only and give D3 a separate inline `ReportWrongTable` lookup. Pin before Phase-3 wires F2.
- **D-RF3 — D3 owner-closing edge = single-user owner.** Confirm "owner" who writes REVERIFIED_FAITHFUL/RETRACTED is the admin/verify owner-only caller (a PC-side owner-triggered re-audit, D7-legal), NOT a new moderation actor (multi-user DEFERRED). Record the single-owner assumption for the multi-user era to revisit.

---

## Verified ground-truth anchors (re-checked against `main` + live DB this session)

- Live `~/.jarvis/tutor.db` `kc_verification_status` = **4 columns** (kc_id, status, last_audit_run_id, updated_at) — missing content_hash AND lecture_grounded. **All three trust tables = 0 rows** (kc_verification_status / verification_audit / report_wrong).
- Kotlin `KcVerificationStatusTable` = **6 columns** (adds content_hash:124, lecture_grounded:147) — Phase1Tables.kt:108–150. No `source_span_hash`.
- `data-model-lock.md:410` CHANGE-10 freezes only the 4 bare columns; `interface-signatures-lock.md:232` freezes only `content_hash`. Both omit `lecture_grounded` → triple drift.
- `kcContentHashOf` folds `kind|content|doc|page|spanStart|spanEnd` (omits source bytes + `c.invariant`) — ContentReconcile.kt:285–300. Frozen identity `kcContentHash(kc)==kcContentHashOf(claimsFor(kc))` at :274; lock §I.1:236 quotes the older `…|span`.
- Serve gate `resolveStatus` (TrustRoutes.kt:204–227) + `servedHonestFloor` (:248–273) read only status + contentHash + lectureGrounded — NEITHER reads `ReportWrongTable`.
- `finalizeKc` writes contentHash + lectureGrounded UNCONDITIONALLY, no ReportWrongTable read — VerificationRunner.kt:157–190.
- D9 sync: NO `syncAudit`/`upsertAudit`/`surgical`/`source_span_hash` impl in any `.kt` (grep) → unbuilt; forward spec-constraint.
- Round-trip `fold(s)=s.replace(Regex("\\s+")," ").trim()` — SpanClaimRoundTrip.kt:52; located `slice` at :43.
- `report_wrong.resolution` enum `{OPEN|REVERIFIED_FAITHFUL|RETRACTED}` ALREADY frozen — data-model-lock.md:408 + Phase1Tables.kt (no enum change for D3).
- `VerificationGate.gate(kc, status, hasOpenReportWrong)` exists, names hasOpenReportWrong, ZERO production callers — interface-signatures-lock.md §I2:257–262.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
