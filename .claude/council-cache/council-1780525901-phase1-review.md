# Council review — 1780525901

**Problem:** Is shipped Phase-1 (data model, main @ cca9f7b, CI green, :check 958, live DB 871→828) genuinely SOUND + fully reviewed + are the cross-phase SEAMS wired to match the signatures-lock so Phase 2/3/5 build unchanged? What to do next?
**Proposed approach:** "Phase 1 done + green + live-migrated; proceed straight to Phase 2 (correctness/trust-net)."
**Project context:** Kotlin/Ktor + Exposed/SQLite single-user tutor; keystone = Phase-2 verification trust-net; plan-everything-then-build with a frozen signatures-lock as the anti-redesign contract; PM-delegation; Phase 1 built TDD via agents, gradle-gated per batch, cull+migration proven on a restored copy before live.
**Timestamp:** 2026-06-04T~03:51Z

---

## 🔴 Devil's Advocate — REJECT
PhaseModel.transition (signatures-lock §A) is internally contradictory and its `current` param is dead: KDoc + "no-regression" test comments (PhaseModel.kt:18-21, PhaseModelTest Group-6) claim a `max(computed,current)` floor, but the impl (PhaseModel.kt:54-64) ignores `current` and returns a pure threshold recompute — tests green only because each Group-6 body asserts the ignore-current value while comments claim no-regression. So `recordIn` threads `existing.phase` into a discarded param and phase can silently REGRESS. Compounding: `entry_phase` (CHANGE-2, the §4.1 floor ScaffoldPlanner §F consumes) has ZERO writers in src — neither the backfill (writes only `phase`) nor recordIn sets it. Phase 3 builds the scaffold/phase-gate against a frozen seam delivering neither the documented no-regression floor nor any entry_phase data.
KEY CONCERN: `current`-param + entry_phase phase-floor frozen in signature but unbuilt in behavior — a signatures-lock breach hiding behind green tests (tests written to impl behavior, not contract). Secondary: H11 still emits span/vision separately from invariant/grader_rules (not the one aggregated report §78 mandates; latent — all 6 real KCs are grounding_tier=standard).
[SANITY NOTE: the entry_phase "dead column" claim is OVER-STRONG — entry_phase's WRITER is the Phase-3 `/placement/submit` route (master-plan §2.2), correctly out of Phase-1 scope; NULL-now = treat-as-intro is the documented contract. The PhaseModel `current`/no-regression finding is real + corroborated by First Principles.]

## 📚 Domain Expert — APPROVE
Textbook expand-phase migration (Flyway/Liquibase expand-contract): every CHANGE-1/2 col nullable-at-DDL + explicit post-ALTER `UPDATE … WHERE col IS NULL` in the same boot txn, compensating for Exposed `.default()` being client-side-only (Migration.kt:107-121). Idempotency real + tested (MigrationIdempotencyTest seeds genuine legacy shape, migrates twice). Verified live DB actually migrated (828, 0 NULL status, 6 tables, phase+entry_phase present) — clears feature-shipped/render-before-claim bar. Seams match signatures-lock exactly (§A sole phase writer, §G recordIn+record, §H upsertRubricCriterion + the (userId,source,kcId) UNIQUE index correctly NULL-permissive mirroring TasksTable.uniqueIndex, §I VerificationStatus_.transition) — no contract drift. 43-id cull vs "22→849" estimate is a documented re-audit honoring the 800 floor, not a divergence.
KEY CONCERN: CHANGE-2 phase backfill uses string-interpolated SQL with hand-rolled `'`-escaping (Migration.kt:174-178) instead of Exposed bound-param `exec(sql,args)`/.update{} that the sibling backfills + rest of the codebase use. Exploit surface ~nil (app-gen ULIDs, one-shot owner boot), so CONDITIONAL-grade — fold "parameterize + delete dead MigrationResult.Failure" into the Phase-2 opener.

## ⚙️ Pragmatist — CONDITIONAL
Cheap-to-maintain case, not compounding: migration real-wired into the boot path, plan unusually disciplined, cull/migration proven on a restored copy. Known smells low-blast-radius (dead Failure harmless; string-interp SQL only over app-ULIDs + IS NULL-guarded; on-box hook best-effort never masks the real error). A FULL formal code-review before Phase 2 is NOT worth it — green + proven-migration + wired surface clear the bar. Ordered next: (1) ~30-min smell-sweep commit — delete dead MigrationResult.Failure, RECONCILE worked_example_first default-vs-plan (real spec divergence, not a nit), comment the NULL-distinct uniqueIndex assumption; (2) THEN extract PA PDF + start Phase 2 (it's relay-dependent — close the foundation before you're blocked on a sleeping relay).
KEY CONCERN: worked_example_first=false silently disagrees with v2 plan's "server decision, degrade to true if endpoint absent" (master-plan:220) — the one smell that can quietly produce wrong pedagogy downstream; reconcile NOW.

## 🧱 First Principles — CONDITIONAL
For a CONTRACT layer "tests green" is the wrong success criterion; the right one is "Phase 2/3 build against these seams unchanged" — verified directly vs the frozen lock + live DB. The seams that matter (txn-less recordIn/upsertRubricCriterion §G/§H, VerificationStatus no-faithful-without-both-legs §2.4, new-table shapes feeding /calibration + the atomic grade-txn, the live 828-card status=ACTIVE corpus) all match → proceeding is correct in substance. The single thing treated as "done" that isn't: PhaseModel.transition ignores its frozen `current` param (no-regression rule its own test header claims is unimplemented + untested — green only because no case asserts computed<current→current). Signature still frozen-correct ⇒ a one-task fix, not a Phase-2 hold.
KEY CONCERN: PhaseModel.transition dead `current` param + unenforced no-regression — add the missing computed<current test, DECIDE the demotion rule explicitly, then implement no-regression OR delete the param from the contract — before Phase 3 consumes phase logic.

## ⚠️ Risk Analyst — CONDITIONAL
Live DB verified healthy (828, all ACTIVE, 6 tables + index, PRAGMA integrity_check=ok, foreign_key_check clean); re-run is a no-op (createMissingTablesAndColumns adds-only, backfills WHERE col IS NULL-guarded). Two LATENT risks. HIGH: the (user_id,source,kc_id) UNIQUE index — all 828 live cards are (owner,MANUAL,NULL) and coexist ONLY because SQLite treats each NULL as distinct; tests seed ONE card so the 828-share-NULL reality is never exercised — any Phase 2/3 code that backfills kc_id onto MANUAL cards (or an index rebuild under different NULL semantics) mass-collides. HIGH (data-safety): the M-PARTIAL hook is mislabeled "off-box" but shells db-backup.py to local ./backups on the same disk as a half-ALTERed DB; the only true net is the operator's manual pre-boot off-box dump (no code gate, runbook-only).
KEY CONCERN: Add a CI test seeding ~100 (owner,MANUAL,NULL) cards → migrate twice → assert zero row loss + no unique-violation (locks the NULL-distinct assumption the whole corpus depends on before Phase 2 writes kc_id logic). Cheaper second fix: redirect/relabel the M-PARTIAL backup off-box (or fix the misleading "off-box" wording, Migration.kt:84).

---

## Sanity Check
SANITY Devil's Advocate: PASS (one sub-claim flagged) — the PhaseModel `current`/no-regression finding is real + corroborated by First Principles. The `entry_phase` "dead column" claim is over-strong: entry_phase's writer is the Phase-3 `/placement/submit` route (out of Phase-1 scope), so NULL-now is the documented contract, not a defect. REJECT is over-strong for "proceed" (4 others say fixable-in-place) but the core finding stands.
SANITY Domain Expert: PASS — grounded; correctly verified seams vs lock + live DB; smell triage sound.
SANITY Pragmatist: PASS — grounded; the worked_example_first spec-divergence catch is real.
SANITY First Principles: PASS — grounded; corroborates the PhaseModel finding independently; correct that "tests green" is the wrong bar for a contract layer.
SANITY Risk Analyst: PASS — grounded; verified live integrity; both HIGH risks concrete + currently latent.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED (fixable; the freeze HELD — no redesign-forcing drift)

CORE FINDING:
The big win first: the cross-phase SEAMS match the frozen signatures-lock (independently verified by Domain Expert AND First Principles against the lock + live DB), and the live DB is genuinely migrated + healthy — so Phase 2/3 can build on these contracts WITHOUT a redesign. That's exactly what the freeze was for, and it held. BUT "green :check" hid real defects in a contract layer: PhaseModel.transition silently ignores its frozen `current` param so the no-regression rule its own KDoc/tests claim is unimplemented + untested (a behavior gap Phase 3's phase-gate consumes), and the (user,source,kc_id) UNIQUE index's NULL-distinct assumption — which the entire 828-card live corpus depends on — is never exercised by a test. Neither forces a seam redesign; both are one-task fixes. So Phase 1 is SOUND IN SUBSTANCE but NOT clean, and "proceed straight to Phase 2" should be "do a small hardening pass first."

AGENT CONSENSUS: 1 REJECT (over-strong), 1 APPROVE, 3 CONDITIONAL — 0 flagged outright; 1 sub-claim corrected (entry_phase writer is Phase-3 placement, not a Phase-1 dead column).

KEY ISSUES (priority order):
- PhaseModel.transition dead `current` param / unenforced no-regression (DA + FP): DECIDE the demotion rule, then implement no-regression OR delete `current` from the contract; add the missing computed<current test; fix the misleading KDoc. [frozen-seam behavior — fix before Phase 3]
- UNIQUE-index NULL-distinct assumption untested (Risk Analyst): add a CI test seeding ~100 (owner,MANUAL,NULL) cards → migrate twice → no row loss / no unique-violation. [the whole corpus depends on it]
- String-interpolated SQL in the phase backfill → parameterize (Domain Expert): use Exposed bound params / .update{}.
- worked_example_first=false vs plan's "degrade to true" (Pragmatist): reconcile the schema default vs the spec — decide explicitly.
- Hygiene: delete dead MigrationResult.Failure; fix the M-PARTIAL "off-box" mislabel (relabel or redirect off-disk); note/aggregate the H11 split-report (latent — all real KCs are standard tier).

RECOMMENDED PATH:
This council IS the adversarial review (don't also run a separate formal code-review — that's redundant now). Do ONE small "Phase-1 hardening" commit covering the issues above (the two HIGH ones — PhaseModel `current` decision + the NULL-distinct index test — are non-negotiable; the rest are cheap), re-run :check, push. THEN extract the PA PDF into content/PA/_sources/ and start Phase 2 — close the foundation before Phase 2 blocks you on a sleeping relay. Do NOT carry the PhaseModel/index gaps into Phase 2/3.

CONFIDENCE: 8
Would rise to 9-10 after the hardening commit lands green + the worked_example_first/no-regression product decisions are explicitly recorded. The architecture + the freeze are not in question; only a handful of contract-layer behaviors need pinning.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780525901-phase1-review.md
