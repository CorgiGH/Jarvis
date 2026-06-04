# Council review — 1780449926

**Problem:** Are we truly ready to set off building, and to let Claude run the build autonomously for hours unattended?
**Proposed approach:** Start Phase 1 (data model) now via agents/TDD, unattended — card cull (871→849) + Changes 1-10 migrations + explicit backfills + recordIn/upsert txn-split + me/delete cascade + Problem widen + idempotency CI + partial-failure guards + backup-precondition gate over the LIVE SQLite DB — then continue down the dependency chain (Phases 2-6) in one multi-hour unattended run.
**Project context:** jarvis-kotlin tutor; Kotlin/Ktor+Exposed/SQLite single-user (~871 cards @ ~/.jarvis/tutor.db); React+TS+Vite; two-family LLM verification (claude-max relay + OpenRouter :free) + SymPy; NO paid APIs; plan-then-build; PM-delegation; trust-net keystone; badge never "verified correct". Phase 0 DONE (CI green, main @ b3656d2). Plan "freeze-ready" after a 4-round audit.
**Timestamp:** 2026-06-03T~04:25Z

---

## 🔴 Devil's Advocate
AGENT: Devil's Advocate
STANCE: REJECT
REASONING: "Freeze-ready" is a paper claim that collapses on contact with real state. The Phase-4 foundation the plan calls "~70% built / DONE on door-compare" does not compile from that branch: door-compare's ThemePicker.tsx, theme/applyTheme.ts, and theme/ThemeProvider.tsx all import from "../door/palettes", but tutor-web/src/door/palettes.ts (and figures.tsx, concept.ts, DoorBrutalist, DoorWarm) are tracked on NO branch — git log --all shows they were never committed anywhere; they exist only as untracked working-tree files, and the working-tree ThemePicker.tsx is a different, shorter file (81 lines) than door-compare's (344). The plan's hard-gate T5 ("commit untracked door/; merge door-compare→main") is wired against a fiction: merging the branch alone yields broken imports, and "commit untracked door/" is silently load-bearing yet treated as a footnote — the exact ghost-component class this plan's §1 exists to prevent, now at the foundation. Letting Claude run unattended for hours down a chain whose Phase-4 keystone provably can't build means the run hard-stops at T5 (hours wasted) or an agent improvises palette source and every Phase-5/6 surface inherits an unreviewed theme base. Compounding: Phase 1 mutates the live SQLite (no row-level rollback for a non-transactional SQLite ALTER) under an uncommitted plan, unattended.
KEY CONCERN: The Phase-4 theme foundation is NOT built where the plan says — door-compare's theme files import door/palettes.ts/figures.tsx/concept.ts committed on no branch, living only as untracked unmatched working-tree files. T5 cannot succeed as written; "commit untracked door/" is the actual keystone and is under-specified. NOT freeze-ready for an autonomous all-phases run. NO-GO until that source is committed, the 81-vs-344 ThemePicker fork reconciled, and tsc proves door-compare+door/ compiles.

## 📚 Domain Expert
AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The migration design is textbook expand/contract / parallel-change (Ambler "Refactoring Databases"): every column lands NULLABLE, then explicit post-ALTER backfill UPDATE … WHERE col IS NULL, then reads narrow — the additive backward-compatible pattern gh-ost/pt-online-schema-change/Stripe/GitHub enforce, and the v1→v2 correction (Exposed .default() is client-side, not DDL DEFAULT) is exactly the gotcha that bites teams trusting ORM defaults — verified it's a real bare createMissingTablesAndColumns with no SQL default. The fail-closed MIN_EXPECTED_CARDS=800 precondition + verified gzip dump + restore-to-:memory: integrity check is a legitimate backup-before-irreversible-DDL gate (SQLite ALTER is non-transactional, no online tool for embedded SQLite, so backup-and-restore IS the correct recovery primitive), and verifyContent correctly stays OFF check (H6). The divergence is the OPERATING MODE, not the design: mature shops run irreversible production-DDL with a human at the deploy gate and an expand→backfill→verify→contract cadence with a soak — they do NOT chain a destructive 871→849 DELETE, 10 migrations over the live single-copy DB, AND multi-hour downstream feature work into one unattended run with no checkpoint at the irreversible boundary.
KEY CONCERN: Letting the irreversible Phase-1 data-DDL/DELETE over the live single-copy ~/.jarvis/tutor.db run inside the same unattended session as Phases 2-6. Cut: gate Phase 1 as its own run that HALTS after the post-migration verify (idempotency CI green + 849-invariant + due()/queue survival test + fresh off-box dump), with a human or a hard automated assertion gate that aborts the whole run on any Phase-1 invariant miss, before the chain continues. Secondary: the "real PA lecture-1 PDF" is NOT in content/PA/_sources/ (only the 0-form-feed paraphrase); the real PDF sits unextracted in tmp-secondbrain-scrape/ — Phase 2's page-anchor leg runs DEGRADED-only until pdftotext extraction lands.

## ⚙️ Pragmatist
AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The plan is genuinely strong — the most thorough freeze docs audited (concrete return types, factually corrected migration mechanism, M-PARTIAL/M-IDEMP recovery, enumerated gate exemptions). Phase 1 in isolation is a reasonable unattended target: bounded SQL+DDL+backfill over a single SQLite file with a tested backup precondition, idempotency CI, and a restore path. But three facts make a FULL all-phases run reckless: (1) tools/db-backup.py is NOT off-box — it writes ./backups/ on the same disk as the DB, contradicting the plan's "off-box dump" precondition, so the one safety net is one rm -rf/disk-fault away from useless; (2) the door landmine is live — on-disk untracked door/ThemePicker.tsx is a different file from the real 11KB one on door-compare, and ~30 untracked artifacts sit in the tree, so any agent git clean -fdx or naive git merge door-compare destroys work that exists nowhere else; (3) Phase 2 depends on the relay (claude-max, home PC) + OpenRouter :free — both can sleep/429 mid-run; the plan handles it semantically (throw⇒UNCERTAIN) but an unattended agent hitting a relay stall burns hours or silently degrades the corpus to UNCERTAIN with nobody watching. Per-phase TDD plans (data-model-lock.md etc.) do NOT exist yet — an "all phases now" run builds 2-6 against unwritten plans.
KEY CONCERN: A single unattended run down the whole chain is NOT go. GO only for Phase 1, with: (a) BEFORE any agent touches the tree, defuse the landmine — commit/stash the real door state + untracked artifacts so no clean/merge destroys them (human pre-step); (b) make the backup actually off-box (scp to VPS / separate target) before trusting it as the ALTER precondition; (c) scope the unattended run to Phase 1, stop at a green checkpoint; (d) forbid git clean -fdx and git merge door-compare in the run's allowed commands. Phase 1 alone is ~90% of the de-risking value at a fraction of the blast radius.

## 🧱 First Principles
AGENT: First Principles
STANCE: CONDITIONAL
REASONING: The raw goal is ONE trustworthy walking-skeleton concept end-to-end, and the only part where "trustworthy" is machine-checkable is the TDD-gated backend (Phases 1-3, ending at ≥1 KC FAITHFUL); Phases 4-6 are a visual rebrand + UI for a documented visual learner under a render-before-claim rule, so autonomizing them is autonomizing exactly the judgment a human eye is the oracle for — and a green Playwright data-testid gate is precisely the ghost-component/selectors-painted-but-wrong trap CLAUDE.md was burned by twice. The plan itself is freeze-ready as a CONTRACT (signatures locked, 43 fixes folded, fail-loud designed in); the unbounded multi-hour run across the whole chain is what's being treated as ready when the raw constraints (live DB present + backed up off-box, relay provably up, visual learner must SEE) say it is not. [NOTE: this agent also claimed the root ./tutor.db has zero tables — that is the empty repo-root decoy; the real corpus is at ~/.jarvis/tutor.db = 871 cards.]
KEY CONCERN: GO, but bound the autonomous run to Phases 1-3 ONLY (machine-checkable, TDD-gated), gated on two hard preconditions verified BEFORE launch — (a) the real 871-card DB mounted + a verified off-box backup asserting ≥800 cards; (b) the relay confirmed live so Phase 2's two-family FAITHFUL gate can actually be reached rather than silently collapsing to UNCERTAIN. Stop at the Phase-3 exit; Phase 4 (theme/shell merge) and Phases 5/6 (UI) require a human eye on real renders and must NOT be in unattended scope.

## ⚠️ Risk Analyst
AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: The migration tooling is sound — verified live: 871 cards, db-backup.py gates at 800 and passes integrity (memory-restore round-trip OK). But two off-tooling failure modes dominate. CRITICAL: the door source (tutor-web/src/door/, 7 files) is committed nowhere while main.tsx already imports it — any clean-tree op an unattended agent reaches for (git clean -dx, branch switch, stash) silently and irrecoverably deletes hours of hand-built UI that Phase 4 T5 depends on. HIGH: the "off-box" backup writes to ./backups/ inside the repo on the same disk (gitignored), so the irreplaceable artifact (871 cards of real SR history) and its only safety net share a single failure domain and a single git clean -dx — and the Phase-1 agent will be AUTHORING the non-transactional ALTER/backfill/M-PARTIAL guards in the same unwatched pass that runs them, making that backup the sole recovery path for self-introduced migration bugs. [NOTE: this agent claimed door/ is gitignored; direct git check-ignore says door/ is NOT ignored — only backups/ is. The clean/switch blast radius still holds; the "git add won't stage it" sub-claim does not.]
KEY CONCERN: Do NOT set off unattended until (a) the door tree is preserved in git/off-disk so a clean-tree op can't vaporize it; and (b) a backup copy is written to a genuinely separate location off this disk/repo and verified. Until both hold, an unattended multi-hour run mutating the live DB on a dirty tree is one routine git/disk event away from losing either the door work or the irreplaceable card corpus.

---

## Sanity Check
SANITY Devil's Advocate: PASS — grounded; door-compile fact verified (palettes.ts on no branch, ThemePicker 81 vs 344). Note: REJECT is scoped to the all-phases unattended run; Phase 1 alone does not touch door/, so the finding bounds scope rather than blocking Phase 1.
SANITY Domain Expert: PASS — clean; correct on expand/contract, the .default()-isn't-DDL gotcha, and PA-PDF-not-extracted.
SANITY Pragmatist: PASS — grounded; on-box backup + door landmine + relay-stall all real. (door/ "not gitignored" per direct check, but its untracked+cleanable point holds.)
SANITY First Principles: PASS (one sub-claim flagged) — the "root tutor.db zero tables ⇒ corpus absent" is a path error (empty ./tutor.db decoy; real DB ~/.jarvis = 871). Core stance (bound to 1-3, relay precondition) holds.
SANITY Risk Analyst: PASS (one sub-claim flagged) — "door/ is gitignored" is false (only backups/ is); clean/switch blast radius + on-box backup core findings hold.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The PLAN is sound and Phase 1 is a legitimate unattended target — but the RUN AS PROPOSED (all phases, unattended, multi-hour) is not safe, for reasons outside the plan text: (1) the door UI source is committed on no branch (palettes.ts exists in no git history; door-compare won't compile standalone) and a routine git clean/switch destroys it; (2) the only DB backup is on-box inside the repo, sharing a failure domain with the irreplaceable live DB; (3) the relay-dependent + visual phases (2 via relay, 4-6 via human eye) cannot be safely autonomized. These are fixable pre-flight; none invalidate the architecture.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL — 0 flagged outright; 2 minor sub-claims noted (FP path-decoy, RA gitignore detail).

KEY ISSUES:
- Door source preservation (CRITICAL): tutor-web/src/door/ (7 files incl. palettes.ts) + modified main.tsx are committed nowhere; must be preserved off-disk/in-git before any tree-mutating run, and git clean -fdx / merge door-compare / branch-switch must be forbidden during the run.
- Off-box backup (HIGH): ./backups/ is on the same disk inside the repo (and gitignored ⇒ killed by git clean -fdx). A verified copy must land off-disk before the irreversible Phase-1 ALTER.
- Scope bound (HIGH): bound the unattended run to the machine-checkable backend (Phase 1, extendable to 2-3 only with a relay-live gate + per-phase TDD plan + abort-on-red between phases). STOP before Phase 4 — the visual rebrand/UI need a human eye on real renders (render-before-claim).

RECOMMENDED PATH:
GO, bounded. Pre-flight (before any agent touches the tree): (1) preserve door/ + main.tsx off-repo and/or in git; (2) write a fresh DB dump and copy it off-disk (VPS or separate drive), verified; (3) hard-prohibit git clean -fdx / branch-switch / merge door-compare for the run. Then run Phase 1 via TDD with a hard automated assertion gate that ABORTS the whole run on any invariant miss (849-count, due()/queue survival, idempotency CI). Continue into Phases 2-3 ONLY IF Phase 1 is fully green AND the relay is confirmed live (else stop after Phase 1, having written the 2-3 TDD plans). STOP before Phase 4 regardless; leave a report for Alex with renders pending.

CONFIDENCE: 8
Would rise to 9-10 with: a confirmed off-disk backup landing, and a confirmed relay-liveness check at Phase-2 entry. The architecture/plan is not in question; only the run's safety envelope is.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780449926-readiness-autonomous-build.md
