# DELTA downstream-ledger — council re-freeze decisions vs the original fix-ledger

**For:** `build-review/2026-06-05-trustnet-refreeze-decisions.md` (D-RF1/2/3 + blocker ordering), checked against the original `build-review/2026-06-05-trustnet-fix-downstream-ledger.md`.
**Produced:** 2026-06-05, SESSION-55. Scope: ONLY what is NEW beyond what the original ledger already accounted for. Ground-truth re-verified against `main` this session (anchors at end).

---

## VERDICT (2 lines)

1. **NET: REDUCES downstream burden.** Four of the five council deltas are zero-new-ripple (they either confirm a value the original ledger already specified, name a helper the ledger already mandated, or harden footnote-tasks the ledger already listed). The deltas REMOVE one ripple Claude was about to add (full-sha256 mismatch) and SHRINK three future false-red / drift surfaces.
2. **Exactly ONE real NEW_RIPPLE: DELTA-3** (`resolved_by` + `resolved_at` on `report_wrong`) — a genuine schema growth the original ledger explicitly assumed away ("only UPDATE resolution in place → no new table, no cascade change"). It is additive, NULL-default, D9-invisible, and self-contained — but it does move a frozen seam (CHANGE-7) and create one new write-contract obligation that did not exist in the ledger.

---

## Per-delta

### DELTA-1 — `source_span_hash` width = sha256_8/varchar(16), mirroring `content_hash` — **REDUCES**

- **New vs ledger?** No. The original ledger Area 1a (line 22) ALREADY specified `varchar(16)` = sha256_8, "mirroring `contentHash`; if full sha256 is ever wanted, pin it in the lock at the same time." The council's move OVERTURNS *Claude's* later full-sha256 recommendation back to the ledger's own value.
- **Net effect:** REMOVES a ripple Claude was about to add (a 256-bit secondary hash behind a 64-bit primary/key — an internally-inconsistent two-strength gate that invites a future "fix"). It restores ONE consistent 64-bit tier.
- **Touches the lock** (§I.1 + CHANGE-10) — but only inside the re-freeze window the ledger §2.1/§2.6 already opened. The D9 payload is unchanged (`source_span_hash` was already a synced VALUE in the widened allowlist; 16-char vs 64-char is the same column slot, same upsert, same wire field). D9's KEY stays `content_hash` (ContentReconcile.kt:299-308). Serve gate only presence/version-checks the stored value (VPS has no `_sources` bytes, D7), so width is opaque to it. No index change, no migration-order change (still ALTER-ADDed nullable on the 0-row table in the same Step-0 pass).
- **(1) New frozen seam moved beyond ledger?** No.
- **(2) New forward contract?** One narrow constraint, NOT new in spirit: decide BOTH hash widths together (never ship one 64-bit + one 256-bit trust hash). The ledger already coupled them ("pin it in the lock at the same time").
- **(3) New build/CI step?** No.

### DELTA-2 — one pure helper `hasOpenReportWrong(db, kcId): Boolean` (fail-closed-on-throw) — **NEUTRAL**

- **New vs ledger?** No new dependency. The ledger Area-3 row ("Phase-3 queue/today filter") + §2.7 ALREADY mandated that serve, the gate caller, AND the Phase-3 queue/today filter consult the SAME OPEN-report signal. DELTA-2 CONVERTS that already-required shared INPUT into one named, fail-closed-on-throw unit so the predicate is not copy-pasted at 3 sites (3 chances to drift fail-closed → fail-open).
- **Net effect:** SHRINKS a future drift surface. Does NOT widen the frozen admission-only `VerificationGate.gate` signature (the council UPHELD option (b), keeping the gate pure + admission-only — §I2:265-268). The helper is a separate pure unit; it reads `ReportWrongTable` (user-scoped, NOT an audit col, NOT in the D9 set), so D9 still skips OPEN report_wrong unchanged.
- **(1) New frozen seam moved?** No.
- **(2) New forward contract?** One, but pre-existing: Phase-3 queue/today MUST call this helper. The ledger already said "must consult the SAME OPEN-report signal"; naming the helper makes it a concrete reusable seam, REDUCING drift risk rather than adding a dependency.
- **(3) New build/CI step?** No. No schema, no D9 payload, no build-order change.

### DELTA-3 — add `resolved_by` + `resolved_at` to `report_wrong` NOW — **NEW_RIPPLE** (the only one)

- **New vs ledger?** **YES — this is the one genuine move beyond the ledger.** The ledger's me/delete-cascade note (line 117) explicitly assumed NO schema growth: report_wrong resolution "only UPDATE resolution in place → no new table, no cascade change." DELTA-3 adds real columns. Verified live: `ReportWrongTable` = 7 cols `{id, user_id, kc_id, card_id, grade_attempt_raw, reported_at, resolution}` (Phase1Tables.kt:83-92), no resolver/timestamp; CHANGE-7 freezes the same set (data-model-lock.md:408).
- **Net effect:** adds a SMALL new forward ripple to PREVENT a larger un-backfillable one. Two new downstream obligations:
  - **(a) D3's closing-edge writer** (the REVERIFIED_FAITHFUL/RETRACTED transition in `finalizeKc` / the owner admin-verify path) MUST now ALSO stamp `resolved_by` = own-id (single-owner) + `resolved_at` — a write-contract the original D3 fix-plan/ledger did NOT enumerate. It is a one-line addition to an already-planned single-owner write, but it IS new.
  - **(b)** the future multi-user read-model becomes a DEPENDENT of these columns — which is precisely the intended benefit: it turns the future owner→roles generalization from "we have no provenance" archaeology into a pure additive read-model change.
- **Contained:** does NOT reach D9. report_wrong is keyed on `id`, user-scoped, NOT an audit col; D9 is LOCKED to surgical hash-keyed upsert of AUDIT cols keyed on `content_hash`, skipping OPEN report_wrong — so neither OPEN nor RESOLVED rows ever sync. The audit-col allowlist `{status, content_hash, lecture_grounded, source_span_hash, last_audit_run_id}` is UNCHANGED. Resolution provenance stays PC-local, correctly out of the VPS serve store.
- **Build-order:** additive nullable cols on the 0-row table fold into the SAME `createMissingTablesAndColumns` pass as `source_span_hash` (no new migration task, no new ALTER ordering hazard; disjoint from the content_hash re-key). me/delete unaffected (in-place UPDATE; NULL-default cols → no new cascade/FK; data-model-lock Task 13 already covers report_wrong).
- **(1) New frozen seam moved beyond ledger?** **YES — CHANGE-7 (data-model-lock.md:408) + the `ReportWrongTable` object (Phase1Tables.kt:83-92).** This is the one seam the original ledger did NOT plan to move. Folds into the same re-freeze edit.
- **(2) New forward contract?** YES — the D3 closing-edge writer's new write-obligation (stamp resolved_by/resolved_at) + the future multi-user read-model dependency on these columns.
- **(3) New build/CI step?** No new task — folds into the existing Step-0 migration pass + the already-planned D3 closing-edge write.

### DELTA-4 — Step-0 flip hardening: hard build-time abort + audit-window fence + re-audit env-gate — **REDUCES**

- **New vs ledger?** Partly. The "0 live content_hash rows at flip" assertion was ALREADY a ledger mitigation (Area 1b line 47, "Hard gate (add as an explicit task)"). The council PROMOTES it from footnote/assert to a HARD build-time ABORT. Genuinely new: the audit-window FENCE over the Step-0 window, and the ENV-GATE on the ONE re-audit. Verified `Migration.kt` currently has a try/catch guard but NO content_hash-row assertion and NO audit-window fence — so (a) and (b) are real new build steps.
- **Net effect:** REDUCES future risk. These are GATES that fence the ledger's existing "re-key while empty" BLOCKER from a point-in-time hole; they do NOT add a downstream CONSUMER or move a frozen seam.
  - (a) hard abort REDUCES the worst D9 failure (a stray row re-keyed under the wrong formula → silent no-op orphan match).
  - (b) audit-window fence closes a new race (an audit between "verified empty" and "flip lands" writing v1 rows that orphan).
  - (c) env-gate REUSES the EXISTING NLI/JARVIS_PYTHON3 contract (NliEntailmentLlm.kt:16, VerifyContentCli.kt:126, commits 7a70682/41dc6c4 already merged) — no new dependency. It prevents FAIL-LOUD turning a never-ran re-audit into a DISAGREED that reds the pa-kc-001..004 final gate for an infra reason.
- All sit INSIDE the already-LOCKED ordering (flip → re-audit → build D9), not after it — they add pre-conditions to the flip step, NOT a new migration ORDER. None touch the D9 payload, the audit-col allowlist, or the schema.
- **(1) New frozen seam moved?** No.
- **(2) New forward contract that an unbuilt phase must call?** No — self-contains within the Step-0 migration window.
- **(3) New build/CI step?** **YES — three:** the build-time abort (home: `Migration.kt`), the audit-window fence (runbook/CI), the re-audit env-gate (home: audit runner / `VerifyContentCli` env-gate). They must exist as TASKS, not footnotes.

### DELTA-5 — record single-owner in the LOCK + fix existing lock-doc drift — **REDUCES**

- **New vs ledger?** Mostly no. The original ledger ALREADY named both drift fixes as MUST-do-at-re-freeze: §2.5 ("§I.1 must FIRST be reconciled to reality — `lecture_grounded`") and §2.6 ("data-model-lock CHANGE-10 — enumerate ALL audit columns"). Verified the drift is live: §I.1 (interface-signatures-lock.md:226-242) freezes only `content_hash`, omits `lecture_grounded` (live at Phase1Tables.kt:147, read by `servedHonestFloor`) AND `source_span_hash`; CHANGE-10 (data-model-lock.md:410) freezes 4 cols but the live Kotlin table = 6. The ONE genuinely-escalated bit: record the single-owner assumption IN THE LOCK (§I.2/§2.5), not just the archived review memo.
- **Net effect:** REDUCES. Today the lock is canonical-on-conflict AND wrong — an un-reconciled lock would make the next-phase author a stale migration against a 4-col freeze (live = 6) and omit `lecture_grounded` from the trust contract entirely. Truth-telling breaks no live reader; the served badge predicate (grounded OR faithful at servedHonestFloor) finally matches the documented gate.
- The single-owner-in-lock addition is zero code/schema/D9/build-order ripple (prose in the frozen doc); it pins a FORWARD constraint on still-unbuilt, locked-DEFERRED multi-user work, forcing that phase to revisit owner→roles at the documented seam. The schema-of-record reconciliation (CHANGE-10 → 6+ cols incl `source_span_hash`; §I.1 += `lecture_grounded`) lands in the SAME re-freeze edit as the code, per the ledger's "lock amended in the same edit or it becomes a lie" rule.
- **(1) New frozen seam moved beyond ledger?** No — it RECONCILES seams to live reality the ledger already flagged; the single-owner prose is a new forward constraint, not a moved live seam.
- **(2) New forward contract?** One: the future multi-user phase MUST revisit owner→roles at the §I.2/§2.5 seam (forecloses the silent-single-owner anti-pattern). Constrains DEFERRED work only.
- **(3) New build/CI step?** No.

---

## Roll-up of the three explicit call-outs

**(1) NEW frozen seams moved beyond the original ledger:**
- **ONE: DELTA-3** — `report_wrong` CHANGE-7 (data-model-lock.md:408) + the `ReportWrongTable` object (Phase1Tables.kt:83-92) gain `resolved_by` + `resolved_at`. (DELTA-1 and DELTA-5 only move seams the ledger §2.1/§2.5/§2.6 already opened; not new.)

**(2) NEW forward-contracts a future phase must honor:**
- **DELTA-3:** D3 closing-edge writer must stamp `resolved_by`/`resolved_at`; the future multi-user read-model depends on these cols. (New.)
- DELTA-1: decide both hash widths together (pre-existing in spirit).
- DELTA-2: Phase-3 queue/today must call the named helper (pre-existing "must consult"; now a concrete seam).
- DELTA-5: future multi-user phase must revisit owner→roles at the §I.2/§2.5 seam (constrains DEFERRED work only).

**(3) NEW build/CI steps:**
- **DELTA-4 — THREE:** (a) hard build-time abort "0 live content_hash rows" before the v2 flip (`Migration.kt`); (b) audit-window fence over the Step-0 window; (c) re-audit env-gate on JARVIS_PYTHON3/NLI present (audit runner / `VerifyContentCli`). All inside the existing LOCKED ordering — no migration-order change.
- (DELTA-3's columns fold into the existing Step-0 `createMissingTablesAndColumns` pass — not a new task.)

---

## GO decision — fold-in-safe?

**Fold-in-safe. This does NOT change the parked GO.** The council's net effect REDUCES downstream burden; the single NEW_RIPPLE (DELTA-3) is additive, NULL-default, D9-invisible, me/delete-neutral, and lands in the SAME re-freeze edit + SAME Step-0 migration pass already on the table — it adds one frozen-seam line (CHANGE-7) and one writer obligation, both inside Phase-2's already-owned re-freeze. The three new DELTA-4 CI steps are guards on the existing flip, not new phases. Every delta either confirms a ledger value, names a ledger-mandated helper, hardens a ledger footnote, or reconciles a lock the ledger already flagged as a lie. The re-freeze decision set Alex is already being asked to approve (D-RF1/2/3 + blocker ordering) absorbs all five deltas with NO new locked-constraint violation: FAIL-LOUD honored (DELTA-4 protects it), D7 honored (DELTA-1/3 never sync to VPS), D9 keyed-on-content_hash + skip-OPEN-report_wrong unchanged (DELTA-3 stays out of the audit set), multi-user stays DEFERRED (DELTA-3/5 only capture provenance + pin the seam), pa-kc-001..004 protected (DELTA-4 (c) prevents an infra false-red).

---

## Verified ground-truth anchors (re-checked this session)

- `ReportWrongTable` = 7 cols, NO `resolved_by`/`resolved_at` — Phase1Tables.kt:83-92. CHANGE-7 freezes the same 7-field set — data-model-lock.md:408.
- `KcVerificationStatusTable` = 6 cols (incl `content_hash`:124, `lecture_grounded`:147) — Phase1Tables.kt:108-150. CHANGE-10 freezes only 4 — data-model-lock.md:410. §I.1 freezes only `content_hash`, omits `lecture_grounded` + `source_span_hash` — interface-signatures-lock.md:226-242.
- `VerificationGate.gate(kc, status, hasOpenReportWrong)` is pure; caller does the lookup; admission-only caller-set {SR-entry, cold-start, GapPromotion.promote, B1 upsert} — interface-signatures-lock.md §I2:257-268.
- `content_hash` = sha256_8/varchar(16), is the D9 upsert KEY — ContentReconcile.kt:299-308 (per ledger); §I.1:232.
- env-gate contract pre-exists — NliEntailmentLlm.kt:16, VerifyContentCli.kt:126, commits 7a70682/41dc6c4.
- Ledger me/delete note assumed NO report_wrong schema growth ("only UPDATE resolution in place") — trustnet-fix-downstream-ledger.md:117.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
