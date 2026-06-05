# Phase-2 trust-net false-faithful fix-plan (post MF-1/2/3)

**Judge verdict (2026-06-05):** **FLAWED** — fix the plan, then build.

The fix-SET targets the right four holes and fails in the SAFE direction (false-negative, never false-positive). Three of the four legs (D2, D3, D4) are sound in shape and ship roughly as proposed. But the **flagship HIGH fix (D1) is mechanically broken as written** and its dismissed parenthetical alternative is architecturally impossible here. The set cannot be APPROVED until D1's mechanism is corrected and two cross-cutting hazards (D9 re-key + normalization drift) are pinned as explicit tasks, not footnotes.

This is `FLAWED`, not `WRONG_APPROACH`: the diagnosis is correct, the remedy class is correct (Merkle source-hashing + dispute state-machine + vacuous-truth guard + complete cache-key), only the D1 implementation and the migration accounting are wrong.

---

## Core finding

Every confirmed defect shares ONE root cause stated at two layers:

1. **The D8 `content_hash` under-covers the verdict's input set.** `kcContentHashOf` (ContentReconcile.kt:285-300) folds `kind|content|doc|page|spanStart|spanEnd` — it omits (a) the `_sources/{doc}.md` bytes the round-trip actually reads, and (b) the raw `VerificationClaim.invariant` SymPy actually checks. The staleness key is blind to two of its real read-dependencies.
2. **An invalidation EVENT (OPEN report_wrong) is an input NO gate consults** — neither the serve path (`servedHonestFloor` / `resolveStatus`) nor the re-audit writer (`finalizeKc`) reads `ReportWrongTable`. The one component that does (`VerificationGate.gate`) has zero production callers.

D1+D2 are the same bug (incomplete cache-key) at two fields. D3 is a separate bug (missing event consultation + missing dispute state-machine). D4 is a third (a check that cannot fail certifies nothing). The fix must name the invariant — "the badge lights ONLY if every input the `faithful` verdict was a function of is provably unchanged at serve, AND no open dispute exists, else fail-dark" — so the next missing input isn't another whack-a-mole.

### The one decisive correction (D1 mechanism)

The proposal says "fold a fingerprint of the source-span bytes into `content_hash`." That is **architecturally impossible without detecting nothing**, for a verified, load-bearing reason:

- The serve path runs on the **VPS, which is serve-only (D7)**. D9 syncs only audit columns, hash-keyed; it **never** syncs `_sources/{doc}.md`. The VPS has no source bytes.
- `servedHonestFloor` recomputes `currentHash = ContentReconcile.kcContentHash(kc)` from the **YAML-loaded `kc` only** (TrustRoutes.kt:267). The frozen identity `kcContentHash(kc) == kcContentHashOf(claimsFor(kc))` (ContentReconcile.kt:263-272, explicitly documented as load-bearing for audit==serve agreement) means the serve side has nothing but YAML to hash.
- Therefore a source-byte hash **cannot ride the shared `kcContentHash`**: at serve the VPS would have no source bytes to recompute it from, and a *stored* value folded in would be self-referential (serve recomputes from the same frozen stored value → `currentHash == rowHash` ALWAYS → detects exactly nothing).

D1 MUST be a **separate, PC-computed, synced column** (`source_span_hash`) compared independently at serve. And the parenthetical "re-run the round-trip at serve" alternative is **dropped** — it pulls `_sources` reads onto the VPS serve path, breaks D7, and risks a serve-time throw degrading toward a silent pass (violates FAIL-LOUD).

Contrast with D2: the `invariant` field travels in the YAML, so D2 *can* ride the shared `kcContentHash` (both audit and serve recompute it from YAML identically). **D2 rides the shared hash; D1 cannot.** The proposal conflates them as "fold into content_hash" — that conflation is the flaw.

---

## Council consensus

5/5 agents returned **CONDITIONAL**; 5/5 sanity notes **PASS** (no agent discounted). There is unanimous, mechanically-grounded agreement on:

- **D1 is the load-bearing defect in the set.** As written it does not close H1 at serve time (Devil's Advocate, First Principles, Pragmatist, Risk Analyst, Domain Expert).
- **Reject the "re-run round-trip at serve" alternative** on D7 grounds (all five).
- **D1 must hash the round-trip's `fold('\s+'→space)`-normalized located slice, NOT raw source bytes** — else whitespace/CRLF/re-OCR churn (exactly the class D-R10 hand-corrected on pa-kc-002/004) flips the hash and drops faithful KCs ⇒ the forbidden pa-kc-001..004 false-negative regression (Domain Expert + Risk Analyst, both verified against SpanClaimRoundTrip.kt:52).
- **D1 + D2 change what the fingerprint covers ⇒ they re-key every stored hash ⇒ D9 PC→VPS surgical upsert breaks unless re-keyed; force a one-time re-audit + re-sync, and version the hash so old rows fail CLOSED** (Domain Expert "v2:" prefix; Risk Analyst, Pragmatist, First Principles all concur). Blast radius is ~0 RIGHT NOW because the live trust-net tables are empty / pre-migration — so pay this cost now.
- **D3's serve-refusal must ship WITH the report-resolution half** (OPEN→REVERIFIED_FAITHFUL/RETRACTED), or it is a permanent DoS-on-truth: one frivolous report darkens a correct badge forever with no exit (Domain Expert, Risk Analyst).
- **D2 and D4 are small, localized, and ship roughly as proposed** (Pragmatist, First Principles).

Divergence (resolved here): Pragmatist wants the FULL REVERIFY lifecycle deferred and D3 closed by a serve-refusal + a one-line `finalizeKc` relight-guard; Domain Expert + Risk Analyst insist a terminal closing transition must exist or D3 deadlocks. **Resolution: build the minimal-but-COMPLETE lifecycle** — serve-refusal + finalizeKc relight-guard + ONE owner-driven closing transition (a re-audit that re-grounds writes `REVERIFIED_FAITHFUL` and only THEN may relight; an owner accept-the-report writes `RETRACTED`). That is the smallest state-machine with no permanent trap. The full multi-actor moderation UI stays deferred; the *exit edge* does not.

---

## Hard acceptance gate (applies to EVERY defect)

**pa-kc-001..004 MUST stay `faithful` / "matches your lecture" after every change.** A fix that drops them is a false-negative regression and is unacceptable. A regression test asserting all four stay faithful runs after each defect lands and is the final green gate for the whole change.

Secondary locked constraints honored throughout: FAIL-LOUD (never-ran ⇒ DISAGREED; infra-fail THROWS); audit OFFLINE/PC-side, VPS serve-only (D7); D9 = surgical hash-keyed upsert of audit cols only, skip OPEN report_wrong, never whole `tutor.db`; badge wording ALWAYS "matches your lecture", NEVER "verified correct"; per-claim-kind routing D-R17 unchanged; NO paid APIs; per-phase TDD (failing test first).

---

## Per-defect fix-plan

### D2 — INVARIANT omitted from hash (HIGH; ship first, smallest blast radius)

**Defect.** `kcContentHashOf` omits `c.invariant`. When `invariant_statement` is authored, `claimsFor` puts the NL restatement in `content` and the raw equation lives only in the unhashed `invariant` field (ContentReconcile.kt:82-83). Editing `1+1+1=3 → 1+1+1=4` changes nothing the hash sees ⇒ no stale ⇒ badge served over an equation SymPy would now REFUTE.

**Chosen fix.** Add `c.invariant ?: ""` to the canonical `listOf(...)` inside `kcContentHashOf` (ContentReconcile.kt:290-297). One field, one place. The audit-side entry point (`kcContentHashOf(claimsFor(kc))`) and the serve-side recompute (`kcContentHash(kc)`) both flow through this single function, so audit==serve agreement is preserved automatically — `invariant` rides the shared hash because it is a YAML field present on both sides.

**Why.** Completes the cache-key for the EQUATIONAL input. The fingerprint must cover every input the verdict depended on; the SymPy input is one of them. Smallest, lowest-risk diff that closes a HIGH.

**Ordering / dependencies.** Ships in the SAME content_hash redefinition as D1 (see Build Order) so there is exactly ONE re-key, ONE re-audit, ONE re-sync. Do NOT land D2 alone then D1 later — that is two re-keys and two re-syncs.

**Pitfalls.**
- **Normalization drift:** none — `invariant` is hashed as authored YAML text on both sides; no fold needed. (Do not "normalize" the equation before hashing — that would desync audit vs serve.)
- **D9-sync:** changes the hash → re-keys every row. Covered by the shared versioned-prefix + re-audit task (Build Order step 0).
- **pa-kc false-negative:** pa-kc-001..004 are pure DEFINITIONs with no `invariant` ⇒ `c.invariant ?: ""` adds the empty string for them ⇒ their hash is unaffected by the *value* but DOES change due to the version prefix (intended; they get re-audited and re-hashed in the same pass and stay faithful).

**First failing TDD test.**
`ContentReconcileTest`: build a KC with `invariant_statement` set and `invariant = "1 + 1 + 1 = 3"`; capture `H1 = kcContentHash(kc)`. Mutate ONLY `invariant` to `"1 + 1 + 1 = 4"` (leave `invariant_statement`, quotes, spans untouched); capture `H2`. **Assert `H1 != H2`.** (Red today: equal. Green after the fold-in.)

---

### D1 — source-of-record blindness (HIGH; the load-bearing fix; corrected mechanism)

**Defect.** `content_hash` fingerprints the YAML quote+span+doc-FILENAME but never the bytes of `content/<subj>/_sources/{doc}.md`. Edit / re-OCR the source after an audit (YAML unchanged) ⇒ `rowHash == currentHash` ⇒ badge served over a quote that no longer relocates in the live source. The round-trip — the sole leg reading `_sources` — runs only at offline audit, never at serve.

**Chosen fix (CORRECTED — this is the council's binding amendment).** A **separate stored column `source_span_hash`**, computed PC-side at audit time, synced by D9 as an audit column, compared independently at the serve gate. Specifically:

1. **Compute (PC-side, audit time, in `SpanClaimRoundTrip` / `VerificationRunner`):** for each span-bearing claim, after the round-trip locates the slice, compute `sha256(fold(located-slice))` where `fold` is the **round-trip's OWN `fold(s) = s.replace(Regex("\\s+"), " ").trim()`** (SpanClaimRoundTrip.kt:52). Aggregate per-KC (sorted by claimId, like `kcContentHashOf`) into one `source_span_hash`. Hash the **round-trip's normalized located slice**, NOT raw source bytes and NOT the whole file.
2. **Store + sync:** add `source_span_hash` to `kc_verification_status`; D9 surgical upsert carries it as an audit column (it is a PC-computed audit artifact, exactly what D9 is for).
3. **Serve gate (`servedHonestFloor`, TrustRoutes.kt:266-272):** the VPS already loads the live `_sources/{doc}.md`? **No** — confirm it does NOT. The serve gate compares the **stored** `source_span_hash` against the value re-derived from... nothing the VPS has. **Therefore the serve gate compares the stored `source_span_hash` only as a presence/version check, and the actual source-edit detection happens PC-side at re-audit / reconcile.** The serve-time guard is: a NULL/absent `source_span_hash` fails CLOSED. The DETECTION of a source edit is a **PC-side reconcile/watcher step** (step 4) that re-pends or nulls the row when `_sources` bytes change.
4. **Detection trigger (PC-side):** extend reconcile (or a pre-audit check) so that when the live `_sources/{doc}.md` no longer yields `fold(located-slice)` matching the stored `source_span_hash`, the KC's `content_hash` and `source_span_hash` are NULLed (fail-closed) and the KC is re-pended for audit. This is the watcher leg Devil's Advocate mandated — a passive serve-side hash column alone cannot fire, because the VPS has no source bytes.

> **Why not the simpler "fold source hash into content_hash"?** Verified impossible: serve recomputes `kcContentHash` from YAML only (no source bytes on the VPS), and a stored value folded in is self-referential. See Core Finding.
> **Why not "re-run the round-trip at serve"?** Verified forbidden: pulls `_sources` reads onto the VPS serve path (breaks D7) and risks a serve-time throw → silent pass (breaks FAIL-LOUD). Dropped.

**Why this mechanism.** It closes H1 at the only place that has the source bytes (PC-side), travels the result as an audit column (D9-legal), and fails CLOSED at serve when the column is absent/stale. It hashes the round-trip's normalized located slice so it tolerates exactly the churn the round-trip tolerates (no false-negative on whitespace/re-OCR).

**Ordering / dependencies.** Lands in the SAME content_hash/schema redefinition pass as D2. Requires the new column migration + the re-audit + re-sync (Build Order step 0). The PC-side detection step (4) depends on the column existing.

**Pitfalls.**
- **Normalization drift (CRITICAL):** hashing **raw** source bytes would false-negative on the `\s+`/CRLF/`\n`-indent churn D-R10 already hand-corrected on pa-kc-002/004 ⇒ pa-kc-001..004 flap ⇒ forbidden regression. MUST hash `fold(located-slice)` via the round-trip's shared `fold()`. This is the single most important constraint on D1.
- **DoS-on-truth / mass false-negative:** fingerprint ONLY the cited span WINDOW (the located slice), NEVER the whole file — whole-file hashing stales every co-located KC on any unrelated edit = a false-negative storm.
- **D9-sync:** `source_span_hash` is a new audit column; D9 must carry it. It is computed PC-side, so D7 is honored. Re-key once (step 0).
- **Self-referential trap:** do NOT fold the stored hash back into `kcContentHash` (the serve recompute would make it always-match). Keep it a separate column with PC-side detection.

**First failing TDD test.**
`SpanClaimRoundTrip`/`VerificationRunner` integration: audit a DEFINITION KC against a temp `_sources/x.md` ⇒ row gets `source_span_hash = S`. Then (a) edit `_sources/x.md` so the cited quote no longer relocates, run the PC-side reconcile/detection ⇒ **assert `content_hash`/`source_span_hash` NULLed and KC re-pended** (and `servedHonestFloor` → UNVERIFIED). And (b) a whitespace-only edit (`"foo bar"` → `"foo  bar"`) ⇒ **assert `source_span_hash` UNCHANGED** (fold tolerates it) and the KC stays faithful. Test (b) is the pa-kc anti-regression guard; it is RED if you hash raw bytes.

---

### D3 — report_wrong re-audit relight (HIGH; the REVERIFY lifecycle gap)

**Defect.** After report_wrong (MF-2 correctly darkens the badge: status→pending, `lectureGrounded=false`, `contentHash=null`, OPEN row inserted), an owner re-audit on unchanged content runs `finalizeKc`, which **unconditionally** rewrites `lectureGrounded=true` + fresh `contentHash` (VerificationRunner.kt:152-189 — no `ReportWrongTable` read). The serve path never consults OPEN report_wrong either. The dispute is silently overridden; the badge re-lights over disputed content. `VerificationGate.gate` (the one component that checks `hasOpenReportWrong`) has zero production callers.

**Chosen fix (minimal-but-COMPLETE state-machine — no permanent trap).** Three coordinated parts:

1. **Serve refusal (kills the live risk cheaply).** In `servedHonestFloor` (and `resolveStatus`), within the existing transaction, query `ReportWrongTable` for any OPEN row for the kc; if present, return `UNVERIFIED` (fail-closed). This is the ~10%-cost move that closes the serve-side hole immediately.
2. **Re-audit relight-guard.** In `finalizeKc`, before writing `grounded=true` + `contentHash`, query for an OPEN report_wrong; if present, do NOT set `lectureGrounded=true`/`contentHash` — leave them cleared (the dispute outranks the re-audit). One query + one conditional.
3. **The terminal closing edge (prevents the DoS-on-truth deadlock).** Define exactly ONE writer of each closing transition so OPEN is escapable:
   - **Owner re-audit that re-grounds** the disputed KC writes `resolution = REVERIFIED_FAITHFUL` on the OPEN row (closing it) and ONLY THEN is `finalizeKc` permitted to relight. (The re-audit is the resolution event; it is owner-triggered, PC-side, D7-legal.)
   - **Owner accepts the report** (content genuinely wrong) writes `resolution = RETRACTED`, KC stays dark / gets corrected.

   The full multi-actor moderation UI stays deferred; the *exit edge* does not. Without (3), "refuse while OPEN" with no writer of OPEN→closed is a permanent trap — one frivolous report darkens a correct badge forever (the DoS-on-truth the Risk Analyst + Domain Expert flagged).

Optionally wire `VerificationGate.gate(kc, status, hasOpenReportWrong)` (already built + tested, zero callers) at the serve/SR-entry seam so the chokepoint is reused rather than re-implemented; the caller does the `ReportWrongTable` lookup (per the gate's own contract, VerificationGate.kt:14-15).

**Why.** Two read-paths (serve + re-audit) must consult the invalidation event, and the dispute must have a defined exit or it deadlocks. This is the canonical moderation state-machine: report → {REVERIFIED_FAITHFUL | RETRACTED} terminal edges, owner-driven.

**Ordering / dependencies.** Independent of the content_hash re-key (D1/D2) — can land before or after, no shared migration. Recommended SECOND (after D2's quick win, before/parallel to D1's larger change) because it closes a HIGH with no re-sync cost.

**Pitfalls.**
- **DoS-on-truth (CRITICAL):** ship parts (1)+(2)+(3) together. (1)+(2) alone with no closing edge = permanent trap. The whole point of (3) is the exit.
- **Re-audit ordering:** the closing-transition write (REVERIFIED_FAITHFUL) and the relight must be atomic with `finalizeKc`'s grounded write — same transaction — or a crash between them re-opens the relight hole.
- **FAIL-LOUD:** the OPEN-report query in `servedHonestFloor` must fail closed on any error (treat a query throw as "assume OPEN" → UNVERIFIED), never as "assume clear → serve".
- **Serve cost:** the OPEN-report query is one indexed lookup inside the txn already open; acceptable.

**First failing TDD test.**
`TrustRoutesTest`/`VerificationRunnerTest`: seed a faithful+grounded KC (prod reality). POST report_wrong ⇒ assert `servedHonestFloor` → UNVERIFIED (MF-2, already green). THEN run a re-audit (`finalizeKc`) on unchanged content. **Assert `servedHonestFloor` STILL → UNVERIFIED while the report row is OPEN** (red today: relights to FAITHFUL_TO_SOURCE). Then drive the owner-re-audit closing transition ⇒ assert the OPEN row becomes `REVERIFIED_FAITHFUL` AND the badge relights (proves the exit edge exists, no deadlock).

---

### D4 — tautological equation (LOW; defense-in-depth; self-inflicted, not adversarial)

**Defect.** A trivially-true authored equation (`t = t`, `0 = 0`) passes `splitEquation` (rejects `<=,>=,!=,==` but NOT `lhs==rhs` identity, NonLlmLegs.kt:73-82) ⇒ `simplify(t-t)==0` ⇒ `ran=true, pass=true`. The "SymPy ran && passed" condition is satisfied vacuously — a check that cannot fail certifies nothing. `bothSupported` is still required, which blunts weaponization (LOW).

**Chosen fix.** Reject vacuous invariants at **author/audit time** (PC-side), in `ContentValidator` (and/or `directiveEquation` emit): reject when `lhs` and `rhs` are syntactically identical after trim, OR — stronger — when `simplify(lhs - rhs) == 0` holds for a syntactically-trivial identity / the two sides share no contentful free symbols distinguishing them. Belt-and-suspenders: a trivially-true invariant could also floor to `uncertain`/NONE rather than count as a SymPy pass. Localize entirely to author/audit; **zero serve/sync/hash ripple.**

**Why.** "A check that cannot fail is not a check." Reject the vacuous invariant before it can certify. Same class as a tautology guard in a theorem prover.

**Ordering / dependencies.** Fully independent; lowest priority (self-inflicted authoring error, not an adversarial serve path; `bothSupported` already mitigates). Lands last. No re-key, no re-sync, no serve change.

**Pitfalls.**
- **Don't over-reject legitimate identities:** a real lecture invariant like `|n|_unif = 1` is NOT `lhs==rhs`-identical; the syntactic-identity check is safe. The `simplify`-based check must be careful not to reject meaningful equalities that happen to simplify to 0 over the SAME symbols (e.g. `2x = x + x` is meaningful) — prefer the conservative **syntactic-identity** reject (lhs.trim() == rhs.trim()) as the primary guard; treat the broader `simplify`-trivial reject as a flagged warning, not a hard fail, to avoid false-rejecting authored content.
- **PC-side only:** keep it in the audit/validate path; do NOT add a serve-time check (D7).
- **pa-kc false-negative:** pa-kc-001..004 carry no tautological invariants; unaffected.

**First failing TDD test.**
`ContentValidatorTest` (or `NonLlmLegsTest`): author a KC with `invariant = "t = t"` ⇒ **assert validateContent REJECTS it** (red today: accepted). Companion: `invariant = "|n|_unif = 1"` ⇒ assert ACCEPTED (guards against over-rejection).

---

## Prioritized build order

**Step 0 — Migration scaffolding (BEFORE any hash change lands in served code).**
Bump a hash **schema-version prefix** (e.g. `"v2:"`) into the canonical string of `kcContentHashOf` so every legacy row deterministically MISMATCHES and fails CLOSED (this also closes the separate legacy stale-grounded defect M1 for free). Add the `source_span_hash` column. Plan the one-time **re-audit + D9 re-sync** as an explicit task. Blast radius is ~0 now (live trust-net tables empty / pre-migration) — pay it here, once.

1. **D2** (one field into `kcContentHashOf`) — smallest diff, closes a HIGH, rides the shared hash. Lands in the SAME content_hash redefinition as D1.
2. **D1** (separate PC-computed `source_span_hash` over `fold(located-slice)` + PC-side detection that re-pends/nulls on source edit) — the load-bearing fix; folded into the same re-key/re-audit/re-sync as D2 so there is exactly ONE re-key, ONE re-audit, ONE re-sync.
3. **D3** (serve OPEN-report refusal + finalizeKc relight-guard + ONE owner-driven closing transition) — closes a HIGH with no re-sync cost; ship all three parts together (no DoS-on-truth deadlock). Independent of the re-key, can run parallel to/after D1+D2.
4. **D4** (reject tautological invariants at author/audit time) — defense-in-depth, fully isolated, lands last.

**Final green gate (whole change):** the pa-kc-001..004 regression test asserts all four remain `faithful` / "matches your lecture" after every defect lands. Each defect is RED-test-first per locked TDD. Re-run the full suite after Step 0's re-audit to confirm the four survive the version-prefix re-key.

---

### Verified ground-truth anchors (re-checked against `main` this session)

- `kcContentHashOf` folds `kind|content|doc|page|spanStart|spanEnd`, omits source bytes + `c.invariant` — ContentReconcile.kt:285-300.
- Frozen identity `kcContentHash(kc) == kcContentHashOf(claimsFor(kc))` is load-bearing for audit==serve — ContentReconcile.kt:263-272.
- Serve recomputes `currentHash` from the YAML `kc` only — TrustRoutes.kt:267; gate at 266-272.
- Round-trip `fold(s) = s.replace(Regex("\\s+"), " ").trim()` — SpanClaimRoundTrip.kt:52; pass = `fold(slice) == fold(quote)` at :43.
- `finalizeKc` writes `lectureGrounded`/`contentHash` unconditionally, no `ReportWrongTable` read — VerificationRunner.kt:152-189.
- `VerificationGate.gate(kc, status, hasOpenReportWrong)` exists, caller does the report lookup, zero production callers — VerificationGate.kt:24-31.
- `splitEquation` rejects `<=,>=,!=,==` but not `lhs==rhs` identity — NonLlmLegs.kt:73-82.
- Live trust-net tables empty / pre-`c5e4f9b` 4-col schema ⇒ blast radius ~0 now — audit report `report` field + active-constraints.md:16-17.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
