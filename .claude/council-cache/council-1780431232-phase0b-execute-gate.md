# Council review — 1780431232

**Problem:** Phase 0a (authoring the Stage-0 exit-gate plan) is done. Is executing Phase 0b NOW the right move, or does the approach need a rethink — given new findings (CI already red on main, `:check`/validateContent never CI-run, e2e hits a non-existent backend) that were NOT known when the v2 plan was hardened?

**Proposed approach (Phase 0b):** Execute the Stage-0 exit gate via agents — (1) commit `tutor-web/.npmrc legacy-peer-deps=true`; (2) fix 9 failing frontend specs (ai-literacy fixture gap, no prod change); (3) add `MIN_EXPECTED_CARDS=800` guard to `tools/db-backup.py` + off-box backup; (4) `./gradlew :check` + `npm test` + e2e all green; (5) push the 10-ahead `main` to origin (gated on Alex's ok). Pre-flight before Phase 1 (data-model ALTER + card cull).

**Project context:** jarvis-kotlin — personal AI tutor (Kotlin/Ktor + React + android), solo dev, one vertical of a planned life-OS. Council-hardened build bible `master-impl-plan-v2.md`; locked chain Phase 0→6. Constraints: no time estimates; plan-first-then-build (build only on Alex's go); PM-delegation; finals = parallel track; no paid APIs; 10-ahead main unpushed; one production DB (~/.jarvis/tutor.db, 871 cards).

**Timestamp:** 2026-06-02T20:13:52Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: The gate's entire premise — "go green, then push" — is built on the assumption that local-green proves CI-green, and the new findings demolish that assumption three separate ways: the React 19/@visx ERESOLVE conflict means CI dies at `npm ci` regardless of the 9-spec fix, `./gradlew :check` runs validateContent that has NEVER executed in this CI, and the e2e spec asserts zero 4xx/5xx against a backend that demonstrably does not exist in the frontend CI job. You are about to run an exit gate whose pass criterion ("all green") cannot be satisfied by the listed steps, and worse, you don't even understand WHY the e2e currently passes — which means you're flying blind on whether it's passing for a real reason or a false one (silent proxy failure, cached result, skipped test). Pushing a 10-commit `main` to a red CI on the strength of a local green you can't reconcile with CI reality is not a pre-flight check; it's laundering unproven state through a gate ceremony that makes everyone FEEL safe while the actual unknowns ship unexamined. The plan was hardened before anyone knew CI was red — it is answering a question ("are local tests clean?") that is no longer the real risk ("why is CI red, and what does `:check` surface on first real run?").
KEY CONCERN: The gate validates the wrong thing — it proves local-green, but the unexamined e2e-passes-without-a-backend mystery means you cannot even trust that local green is real, so executing 0b now ceremonially blesses state you fundamentally do not understand instead of first answering "why does CI fail and why does a backend-dependent test pass with no backend."

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: Steps 1-3 and 5 match the established pre-flight-gate pattern exactly and I'd APPROVE them outright: `.npmrc legacy-peer-deps=true` is the standard React-19-on-stale-peer-ranges fix (verified: no .npmrc exists, @visx wants React 16-18, project is React 19 — same class of fix as `--legacy-peer-deps`/npm `overrides`, and Vite/SWC ecosystems lean on it constantly), a backup-row-count guard (`MIN_EXPECTED_CARDS`) before a destructive DB op is textbook backup-verification (a backup you haven't restore-tested or row-counted is Schrödinger's backup), and pushing the 10-ahead branch is just "green the gate, then push" — correct ordering. The plan's defect is that step 4 ("get :check + npm test + e2e all green") is written as if green is a fixed target, but two of the three findings prove the gate's *definition of green is unproven*: `gradle :check`+validateContent has never run in this CI (the green-making workflow is itself inside the unpushed 10), and `door-real-backend.spec.ts` is structurally server-less — `playwright.config.ts` only boots `npm run dev`, nothing starts Kotlin on :8080, so an "assert zero 4xx/5xx" test is passing precisely because a proxy `ECONNREFUSED` isn't an HTTP response object it can see — a classic false-green / vacuous-assertion smell. So: execute 0b now, but the gate is not "tests pass," it's "tests pass *and each gate was demonstrated capable of failing*" — run validateContent and the real-backend e2e locally against a started backend (or stub the proxy and rename the test honestly) before trusting either as a gate.
KEY CONCERN: `door-real-backend.spec.ts` is a vacuous green — it asserts "no backend errors" while no backend exists, so it certifies nothing; gating the push on it (and on a never-run `:check`) means pushing on a gate you've never seen go red, which is indistinguishable from having no gate.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The five tasks are the right pre-flight work and individually cheap, but the plan was authored against a CI state that no longer exists — green-locally is no longer the exit condition, green-after-push is. Three findings (CI already red, `:check` never CI-run, e2e hits a server-less backend) mean the first push is the real test and will likely surface failures the local checklist can't see, so gating the push on "all green locally" gives false confidence. The fix is small: reorder so the `.npmrc` + spec fixes land, then validate against CI on a throwaway branch BEFORE pushing `main`, and resolve the e2e mystery (it either has hidden stubs, or it's silently skipping — find out which before trusting it as a gate). Don't rethink the phase; tighten the exit criterion to "CI green on a pushed branch" and run the diagnostics that the plan predates.
KEY CONCERN: The exit gate validates locally but the actual unknowns (heavier `:check`, ERESOLVE, server-less e2e) only resolve in CI — push a scratch branch to surface them before touching `main`, or Phase 0b "passes" while `main` stays red.

## 🧱 First Principles

AGENT: First Principles
STANCE: CONDITIONAL
REASONING: Stripped of framing, the goal of Stage-0 is not "make the listed five fixes" — it is to PROVE a recoverable, reproducible, CI-green baseline exists before mutating the data model, so build work can't sit on quicksand. The plan was authored against an assumed-mostly-green CI; reality (verified) is that CI is red for a reason the plan only half-addresses (`.npmrc` fixes `npm ci`, but the e2e gate `door-real-backend.spec.ts` does `goto('/')` and asserts zero 4xx with NO backend on `:8080` in CI and a `/tutor/`-based SPA, so its pass/fail is structurally undefined server-less), and the backend `:check`+`validateContent` path has never proven green on a pushed run. So executing the five steps as written produces a baseline that LOOKS green by checklist but whose green-ness on the most important axes (backend correctness gate, real-backend e2e) is unestablished — which defeats the gate's entire purpose. The right move is to execute Phase 0b but FIRST repair the gate's own validity (make the e2e self-sufficient — stub or spin a backend, or scope it out of the CI frontend job — and confirm `:check` actually runs and passes), then push.
KEY CONCERN: The e2e gate is not a valid gate as currently wired — `door-real-backend.spec.ts` asserts zero 4xx/5xx while hitting `/api` routes that proxy to a `localhost:8080` backend that does not exist in CI or locally; until that is made self-sufficient or explicitly scoped, "all green" is a false signal and pushing on it ships the exact shaky foundation Stage-0 was meant to prevent.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Top risk is **HIGH (not CRITICAL — recoverable, but the gate self-certifies green while being hollow)**: step (3)'s `MIN_EXPECTED_CARDS=800` guard does NOT exist in `tools/db-backup.py` (verified: no such constant, no `src_count <` check); its only safety is restored==source equality, so a culled/wrong-path DB backs up "0 cards, integrity OK" and EXITS 0 — the one production DB (871 cards) gets a green-looking backup that proves nothing before Phase 1 culls cards. Second **HIGH**: the gate is partly theatrical — CI already runs `gradle :check` AND server-less `npm run e2e` on push (test.yml:29,71), but `door-real-backend.spec.ts` does `page.goto('/')` against a `basename="/tutor"` router (main.tsx:15), so it paints a routeless near-empty page, asserts zero 4xx, and passes WITHOUT ever mounting `App` or touching `/api` — it cannot catch the backend-contract regressions it claims to gate. The `.npmrc` + 9-spec fixes are low-risk and correct; the sequencing (verify DB → push) is sound. Approve executing 0b, but only after two corrections, else the gate gives false confidence right before a destructive schema migration.
KEY CONCERN: The DB-backup guard the plan relies on (step 3, `MIN_EXPECTED_CARDS=800`) is not implemented — build it AND make the e2e gate actually exercise `/tutor` so it hits a real `/api` route, otherwise Phase 0b ships a green checkmark over an unverified backup and an inert smoke test, immediately before Phase 1 alters the schema and culls the only copy of 871 real cards.

---

## Sanity Check

SANITY [Devil's Advocate]: PASS
NOTE: clean. Attacks the load-bearing assumption (local-green ⇒ CI-green) and the e2e mystery; stays in lane; commits to REJECT.

SANITY [Domain Expert]: PASS
NOTE: clean. Names concrete patterns (.npmrc legacy-peer-deps, npm overrides, backup row-count verification, vacuous-assertion/false-green) and verified them against the actual repo files.

SANITY [Pragmatist]: PASS
NOTE: clean. Concrete, specified condition (scratch-branch CI validation before main; resolve the e2e mystery); human-cost framing; stays in lane.

SANITY [First Principles]: PASS
NOTE: clean. Strips the checklist framing to the gate's real purpose (prove a recoverable, CI-green baseline) and rules the e2e gate structurally invalid.

SANITY [Risk Analyst]: PASS
NOTE: clean. Ranked HIGH/HIGH, top-2 focus, verified against main.tsx:15 basename + test.yml; the db-backup guard is genuinely unbuilt (plan Task 3 = add it).

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The Stage-0 PHASE is right and steps 1, 2, 3, 5 are textbook-correct — but the gate's DEFINITION OF "GREEN" is flawed two ways, both independently verified against the code. (1) The e2e gate is a vacuous false-green: `door-real-backend.spec.ts` does `goto('/')` against a `basename="/tutor"` router (main.tsx:15), so it paints a routeless near-empty page and asserts "zero 4xx" without ever mounting `App` or hitting `/api` — it certifies nothing and has never been seen to fail. (2) "Local green ≠ CI green": `:check`/validateContent has never run on a pushed commit and the green-making workflow itself lives inside the 10 unpushed commits, so the FIRST push to `main` is the real test — pushing into a known-red CI to "discover" failures is backwards. Don't proceed as-is; the fix is small and clear.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL — 0 agents flagged. (The lone REJECT and the four CONDITIONALs all point at the SAME two defects; the disagreement is only "rethink" vs "tighten," not on substance.)

KEY ISSUES:
- **The e2e gate is structurally inert** — `goto('/')` outside the `/tutor` basename → no `App` mount, no `/api` call, no backend needed; asserting zero 4xx over a near-empty page is a hollow checkmark. A test that can't fail is not a gate. (Devil's Advocate, Domain Expert, First Principles, Risk Analyst — all verified against main.tsx/playwright.config/test.yml.)
- **Local-green is the wrong exit criterion** — the real unknowns (ERESOLVE-fixed `npm ci`, never-CI-run `:check`+validateContent) only resolve on a real push; gating on local green ships unproven state. Validate on a throwaway/scratch branch BEFORE touching `main`. (Pragmatist, Devil's Advocate.)
- **The `MIN_EXPECTED_CARDS=800` backup guard is genuinely unbuilt** (plan Task 3 adds it) — it MUST be real and effective before Phase 1's destructive cull of the only copy of 871 cards. (Risk Analyst.)

RECOMMENDED PATH (fixes, not a rethink):
1. Keep & execute steps 1 (.npmrc), 2 (9 specs), 3 (build the REAL card-count guard + off-box backup) — all correct.
2. **Repair the gate's validity BEFORE relying on it:** fix `door-real-backend.spec.ts` to navigate under `/tutor` so it actually mounts `App` + hits `/api` against a started/stubbed backend — OR rename/scope it honestly so it isn't a false gate. Confirm each gate can be made to FAIL once.
3. **Push a scratch branch to origin first** (not `main`) to surface what CI really does with the new workflow (ERESOLVE, `:check`+validateContent, e2e). Read the real result.
4. Redefine the exit criterion to **"CI green on a pushed scratch branch AND every gate demonstrated capable of failing,"** then fast-forward `main` + push with Alex's ok.
5. Fold steps 2-4 back into `2026-06-02-stage0-exit-gate.md` before executing.

CONFIDENCE: 8
What would raise it: confirming exactly how `door-real-backend.spec.ts` currently resolves in CI (true pass vs silently-skipped vs vacuous) — the agents proved it CAN'T validate the backend, but the precise current-pass mechanism is inferred, not run. That doesn't change the verdict (the gate is invalid either way), only the diagnosis detail.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780431232-phase0b-execute-gate.md
