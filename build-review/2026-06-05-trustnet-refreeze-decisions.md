# Trust-net re-freeze — FINAL judge decisions (D-RF1/2/3 + blocker ordering)

**Date:** 2026-06-05, SESSION-55. **Judge:** adversarial council synthesis (Devil's Advocate · Risk Analyst · Domain Expert · Pragmatist) over Claude's recommendations.
**Inputs:** `build-review/2026-06-05-trustnet-fix-plan.md` · `build-review/2026-06-05-trustnet-fix-downstream-ledger.md` · `interface-signatures-lock.md` §I.1/§I2 · `data-model-lock.md` CHANGE-7/10. Ground truth re-verified against `main` + live `~/.jarvis/tutor.db` this session.

**Headline:** Approve the re-freeze and all four moves — but OVERTURN Claude's one substantive engineering call (full-sha256 `source_span_hash`) and ADD two cheap, empty-table, do-it-now schema fixes Claude waved into prose. The re-freeze is the one moment these are free.

---

## D-RF1 — Re-freeze the trust-store (6 frozen seams) + pin `source_span_hash` width

- **Claude's rec:** Approve the re-freeze now (blast radius ~0: trust tables empty, D9 unbuilt, live DB pre-D8). Pin `source_span_hash` = **FULL sha256** (not truncated) — "trust gate, collision-resistance matters, storage trivial."
- **Council verdict:** **REFINE** (4/4 reviewers REFINE; unanimous that the re-freeze itself is correct, unanimous that the FULL-sha256 width pin is wrong as stated).
- **Final answer for Alex:**
  1. **APPROVE the re-freeze now.** Verified: live `kc_verification_status` = 0 rows (all 3 trust tables empty), `content_hash` absent from the live 4-col table, D9 unbuilt. The v2 re-key + ONE re-audit while empty is correct, and the cost is genuinely ~0 today and unbounded once D9 ships + rows exist. Do it now.
  2. **OVERTURN the width pin — set `source_span_hash` = `varchar(16)` / sha256_8, MIRRORING `content_hash`.** Verified: the PRIMARY trust fingerprint `content_hash` is sha256_8 (ContentReconcile.kt:299-308; lock §I.1:232) and is ALSO the D9 upsert key. Pinning the SECONDARY co-factor to 256-bit while the load-bearing primary stays 64-bit ships an internally-inconsistent two-hash gate at two strengths — a smell that invites the next reader to "fix" the mismatch, and it hardens the weak leg's defense while leaving the binding leg untouched. The threat here is self-inflicted source churn / re-OCR (D1/D4 note), NOT adversarial preimage forgery, so 64-bit is sufficient for a single-user pre-launch corpus. **Decide width for BOTH hashes together:** mirror at `varchar(16)`, OR — only if Alex genuinely wants a 256-bit trust tier — widen BOTH `content_hash` and `source_span_hash` to full sha256 in this same re-freeze. Never ship one 64-bit and one 256-bit trust hash.
  3. **ADD two re-freeze tasks Claude omitted (both near-zero-cost on empty tables):**
     - **Pre-flip hard gate:** assert "0 live `content_hash` rows" as a BUILD-TIME ABORT immediately before the v2 flip lands (not a footnote), and freeze/skip audit runs during the Step-0 window. The ~0-blast-radius premise is point-in-time; nothing currently fences the window between "verified empty today" and "flip lands."
     - **Re-audit env gate:** gate the ONE re-audit on "audit env confirmed live" (JARVIS_PYTHON3/NLI present, per commits 7a70682/41dc6c4) BEFORE flipping. FAIL-LOUD turns a never-ran audit into DISAGREED, so an infra hiccup at re-audit time reds the final green gate (pa-kc-001..004) for an infra reason that LOOKS like a logic regression.
  4. **Disambiguate the D2 term in the lock + rewrite the KDoc.** Verified: ContentReconcile.kt:268-272 (B5r-3/D-R7) explicitly states `invariant_statement` is ALREADY folded transitively via `claimsFor` content, and that adding it as a SEPARATE term was deliberately avoided. D2 adds the RAW `VerificationClaim.invariant` field (the SymPy equation) — a DIFFERENT field, so it is not pure double-counting — but the re-freeze MUST rewrite that KDoc (else the code self-contradicts its own load-bearing comment) and a build-time check must confirm the raw `invariant` carries something `claimsFor` does not already put in `content`, to avoid double-hashing.
- **Rationale:** Re-freeze is right and free now; but a 256-bit secondary hash behind a 64-bit primary gate is incoherent and buys nothing on 0 rows — the norm is internal consistency at a chosen tier, not "full hash everywhere," and the pre-flip + env gates are the cheap fences that protect the whole change's green.

---

## D-RF2 — VerificationGate consultation class for the D3 serve refusal

- **Claude's rec:** Option (b) — keep VerificationGate ADMISSION-ONLY; give D3's serve refusal a SEPARATE always-on inline ReportWrong lookup. Routing a safety refusal through a default-OFF toggleable gate risks silently disabling it (FAIL-LOUD violation) and avoids widening a frozen caller-set.
- **Council verdict:** **UPHOLD** (4/4 UPHOLD).
- **Final answer for Alex:** Adopt option (b). Verified: VerificationGate is pure (the caller does the ReportWrong lookup either way — §I2:265), its frozen caller-set is admission-only {SR-entry, cold-start, GapPromotion.promote, B1 upsert} (§I2:268), its ALLOW rule is `status==faithful` (does NOT match the serve badge predicate `faithful OR lecture_grounded` at servedHonestFloor), and F2 ships default-OFF. Routing the D3 OPEN-dispute serve refusal through that gate (option a) wires a SAFETY interlock behind a feature flag — the textbook fail-safe/feature-flag anti-pattern. Option (b) keeps the refusal always-on, fails-closed on query throw (assume OPEN → UNVERIFIED), and leaves the frozen seam untouched. **One REFINE-grade addition (do it):** extract ONE pure helper `hasOpenReportWrong(db, kcId): Boolean` with the fail-closed-on-throw contract baked in, and have the gate's caller, `servedHonestFloor`, AND the future Phase-3 queue/today filter all call it. This de-dups the shared INPUT (the predicate will live in 3 call sites) without coupling the two different OUTPUT predicates — closing the only real risk: drift of the fail-closed semantics across copy-pasted txn blocks.
- **Rationale:** Both the safety lens (don't put a safety refusal behind a toggle) and the cost lens (inline is ~3 lines inside an already-open txn; reuse adds the lookup PLUS a gate call and re-freezes a frozen caller-set) agree on (b); the named helper prevents 3 chances to fail-open.

---

## D-RF3 — Dispute "owner" = single-user admin/verify owner (not a new moderation actor)

- **Claude's rec:** Confirm single-owner now (multi-user is locked-deferred; don't build moderation actors). Closing edge (REVERIFIED_FAITHFUL/RETRACTED) is owner-only. Record the "owner = single admin" assumption in prose so the future multi-user phase generalizes owner → roles.
- **Council verdict:** **REFINE** (3 UPHOLD + 1 REFINE; the operational decision is unanimous, but Devil's Advocate names a genuine un-backfillable schema gap that the empty-table moment makes free to close — the judge folds it in).
- **Final answer for Alex:**
  1. **CONFIRM single-owner now.** Building a multi-role moderation actor now would violate the locked-deferred multi-user decision and add speculative generality (YAGNI) against the plan-fully-then-build + no-build-unless-Alex-opens-the-door constraints. The state machine (report → {REVERIFIED_FAITHFUL | RETRACTED}) is actor-agnostic; multi-user later = adding an authz layer on the SAME edges, not a redesign.
  2. **RECORD the single-owner assumption in the lock** (§I.2/§2.5), not just a review doc that gets archived — the "record it" clause is load-bearing, not optional (a silent single-owner where the role boundary is never written down IS the anti-pattern).
  3. **ADD `resolved_by` + `resolved_at` columns to `report_wrong` NOW, NULL-defaulted.** Verified: the frozen `report_wrong` schema (data-model-lock:408; Phase1Tables.kt:83-90) is `id · user_id · kc_id · card_id · grade_attempt_raw · reported_at · resolution` — `user_id` is the REPORTER, and the RESOLUTION carries NO resolver/timestamp. "Just generalize owner→roles later" is false comfort: a future multi-user corpus of historical resolutions would have NO record of who resolved them or when — un-attributable retroactively, the one thing you cannot backfill. RF1 is ALREADY re-opening report_wrong-adjacent contracts and the table is empty (0 rows), so adding the columns now (single-owner writes its own id) is additive, fail-safe, and turns the future generalization into a pure read-model change instead of a "we have no provenance" archaeology problem. Recording the assumption in PROSE ≠ making it cheap to undo; record it in the SCHEMA while the schema is on the operating table.
- **Rationale:** The operational decision is unarguable (single-owner, no moderation actor), but provenance is the un-backfillable cost — and the empty table + already-open re-freeze is the one near-zero-cost moment to fix it.

---

## BLOCKER ORDERING — flip hash to v2 + add source_span_hash + fold invariant → ONE re-audit while empty → build D9/B6 after

- **Claude's rec:** Agrees with the ledger's ordering.
- **Council verdict:** **UPHOLD** (4/4 UPHOLD; sequence is forced by a hard data-dependency, not preference).
- **Final answer for Alex:** Adopt the sequence exactly. Verified: `content_hash` is SIMULTANEOUSLY the D8 staleness fingerprint AND the D9 surgical-upsert match KEY (LOCKED "keyed on content_hash", active-constraints.md:24). The v2 prefix + invariant term re-key EVERY `content_hash`; a D9 shipped BEFORE the flip would upsert on the old keyspace and silently match ZERO rows after the flip — a no-op orphan, the worst failure mode for a trust-sync (looks healthy, syncs nothing). So: flip formula + add `source_span_hash` column + fold the invariant term → ONE re-audit while all three trust tables are empty (0 rows verified) → THEN author D9/B6 against the widened keyspace + audit-col allowlist `{status, content_hash, lecture_grounded, source_span_hash, last_audit_run_id}`. **Two refinements (already cross-referenced from RF1):** (a) make the "0 live content_hash rows at flip time" assertion a HARD build-time abort + fence out audit runs during the Step-0 window — the empty-table premise is point-in-time and nothing currently protects it from a stray audit run; (b) keep the "ONE re-audit while empty" framed as the next NORMAL audit run under v2, not a heavyweight migration ceremony (re-keying 0 rows is vacuous; "re-sync" is a literal no-op since D9 is unbuilt). The one genuinely urgent, do-it-now item the ceremony framing under-weights: the lock docs are CANONICAL-ON-CONFLICT and ALREADY lie about live reality (live Kotlin table = 6 cols, lock freezes 4; `lecture_grounded` live + serve-read but absent from both lock docs) — close that doc drift now regardless of D1–D4.
- **Rationale:** Never ship a consumer keyed on a value whose keyspace you're about to re-key; the ordering is the only safe one, and the cheap pre-flip assertion + the lock-doc amendment are the parts that must be tasks, not footnotes.

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
