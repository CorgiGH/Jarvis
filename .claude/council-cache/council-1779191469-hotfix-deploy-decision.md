# Council review — 1779191469

**Problem:** Hotfix `5a9ccfa` for S-24 (ledger taskId) + S-30 (auto-session 401) is committed locally on `hotfix-s24-s30` (1 commit ahead of main). How to land?

**Proposed approach — 3 candidates:**
- (1) ff-merge into main + `git push origin main` → live after VPS pull
- (2) Push hotfix branch + open PR (manual merge gate)
- (3) Defer push; sit on hotfix; pivot to FSRS bulk-seed work first

**Project context:** Jarvis = solo-dev personal tutor (Alex, FII Iași, 13 days to finals). Kotlin/Ktor + React 19 frontend. Live at corgflix.duckdns.org/tutor/. Multi-user planned but single-user effectively. VPS pulls from main (auto-pull-on-push vs. manual SSH UNKNOWN). Bundle (`index-DHlN0mCh.js`) committed into repo + served by Ktor static. Pre-existing 723/724 backend test pass (1 flake unrelated). App.test.tsx pre-existing broken (react-router-dom ESM/CJS). No E2E on live prod with hotfix applied. `viz-foundation-demo` branch 68 commits ahead of main, not merged. Prior council (this session) reframed strategic direction: FSRS bulk-seed loop is Session 1; user picked hotfix-first.

**Timestamp:** 2026-05-19T11:51:09Z

---

## 🔴 Devil's Advocate

[full output — see chat transcript]

STANCE: REJECT
KEY CONCERN: Removing CSRF-cookie early-exit in `ensureTutorSession` converts once-per-cold-start into once-per-mount with no rate limiting, no test coverage, no session-table write-amplification measurement, and the deploy plan has no canary/staging/post-deploy Playwright smoke + unknown VPS pull cadence.

## 📚 Domain Expert

STANCE: APPROVE
KEY CONCERN: The deploy mechanism is "unknown" (auto pull-on-push vs. manual SSH + restart) — this is the actual risk, not the diff. Industry parallel: GitHub 2018 unicorn-day + Knight Capital 2012 — "we thought the pipeline did X, it did Y." Before push, verify VPS pull path + confirm Ktor static-resource cache invalidates on the new bundle hash. Push after confirming pull-and-restart takes <60s and you can watch the live URL paint the new bundle hash.

## ⚙️ Pragmatist

STANCE: CONDITIONAL
KEY CONCERN: Unknown VPS deploy semantics is the actual blast-radius variable. Simpler alternative: SSH the VPS and check for cron/systemd auto-pull. If manual-pull, Option 1 fine (Alex controls timing). If auto-pull, do Option 2 + babysit /tutor/ for 15min post-merge.

## 🧱 First Principles

STANCE: REJECT
KEY CONCERN: The decision frame conflated "council said hotfix first" with "council said deploy hotfix first." Landing the commit on main is the council-order discharge; pushing+deploying is a separate decision that should be ordered by what surface Alex actually touches in the next 13 days — and that's the FSRS loop, which the hotfix does NOT gate. Topology investigation: App.tsx:55-58 fires ensureTutorSession on cold mount of EVERY route including /review. App.tsx:71 fires /api/v1/last-task after sessionReady. S-30 = cold-mount 401 console noise on /review (FsrsReview renders independently and doesn't depend on /last-task). S-24 lives in KnowledgeLedger.tsx, a side-drawer Alex won't touch during 13-day cram. Recommendation: ff-merge 5a9ccfa onto main LOCALLY (no push), build FSRS on top of fixed-main base, deploy combined slice once with one Playwright interaction-smoke pass covering both hotfix surfaces AND FSRS loop.

## ⚠️ Risk Analyst

STANCE: CONDITIONAL
KEY CONCERN: Bundle-shipped ≠ feature-shipped (Slice 1 + 1.5 lessons in CLAUDE.md). Zero E2E on live prod with new bundle means S-30's auto-session removal could silently break session-bootstrap in a way 723 backend tests + 115 viz tests cannot catch — App.test.tsx is pre-existing broken (react-router ESM issue), so frontend mount-path is effectively untested. Mitigation: pre-push Playwright against LIVE URL post-VPS-pull asserting cold-mount /auto-session 200s, /api/v1/gaps returns OK with null-taskId rows rendering non-clickable, zero 4xx/5xx in first paint. Rolls option ranking: Option 1 HIGH (test gap), Option 2 HIGH (PR-graveyard for solo dev), Option 3 CRITICAL (FSRS branches off stale main → next deploy ships FSRS without S-24/S-30 fix, doubling blast radius).

---

## Sanity Check

SANITY Devil's Advocate: PASS — clean.
SANITY Domain Expert: PASS — clean. Real precedents named.
SANITY Pragmatist: PASS — clean.
SANITY First Principles: PASS — clean. Topology investigated, framing reframed.
SANITY Risk Analyst: PASS — clean. Severity ranks + concrete mitigation.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The 3-option framing missed Option 4: **ff-merge `5a9ccfa` onto main locally + do NOT push + build FSRS bulk-seed on top of fixed-main base + deploy combined slice once.** First Principles' topology investigation overrides the integration-majority: S-30 is cosmetic console noise on /review (FsrsReview renders independently) and S-24 is in a side-drawer Alex won't touch during finals cram. Neither hotfix gates the FSRS loop. Three of five agents (Devil + Pragmatist + Risk Analyst) flag the same load-bearing risk: VPS pull mechanism is unknown + no E2E on live prod with new bundle. Pushing now means deploying a fix without verifying the deploy path, against the CLAUDE.md load-bearing rule "bundle-shipped ≠ feature-shipped." Risk Analyst CORRECTLY ranks Option 3 (defer push and let FSRS branch off stale main) as CRITICAL, but Option 4 (defer push BUT ff-merge locally) sidesteps that: the FSRS work branches off fixed-main, so the eventual combined deploy ships both with one Playwright smoke gate.

AGENT CONSENSUS: 2 REJECT (Devil + First Principles), 2 CONDITIONAL (Pragmatist + Risk), 1 APPROVE (Domain Expert). Effective: Option 4 satisfies the topology argument + the no-double-deploy concern + the unknown-VPS-cadence risk simultaneously. Domain Expert's APPROVE collapses to "Option 4 is also fine" when VPS pipeline is unknown.

KEY ISSUES:
1. **Topology says hotfix doesn't gate FSRS.** S-30 = console noise on /review (FsrsReview is independent of /last-task). S-24 = side-drawer Alex won't touch during cram. Deploying it standalone is premature.
2. **Unknown VPS pull cadence is the dominant risk.** All 3 critical-stance agents flag it. Until verified (auto-pull-on-push vs. manual SSH), pushing means an unbounded blast-radius window.
3. **Zero E2E + pre-existing broken App.test.tsx = frontend mount-path effectively untested.** S-30 fix removed an auth-state-skip path; a Playwright smoke on live prod is the only verification that satisfies the CLAUDE.md feature-shipped rule.
4. **Bundle delta unexplained.** ~1.03MB vs prior ~1.00MB — diff with rollup-visualizer or simple grep to confirm only App.tsx delta + sourcemap landed; if anything else snuck in (transitive dep version drift, sourcemap for stale file), would be invisible to tests.

RECOMMENDED PATH (Option 4 — "land local, no push, bundle into next deploy"):

1. **Now (~5min):** `git checkout main && git merge --ff-only hotfix-s24-s30 && git branch -d hotfix-s24-s30`. Main now has hotfix at HEAD. Do NOT push.
2. **Next session — Session 1 (~3-4hr):** Build FSRS bulk-seed card-gen tool per prior council. Branch off the now-fixed main. Wire REVIEW DUE tile. Rebuild bundle ONCE (includes both hotfix + FSRS surface).
3. **Pre-push verification (mandatory before main push):**
   - SSH the VPS, confirm pull mechanism (cron / systemd timer / webhook / manual).
   - Run bundle diff or rollup-visualizer to confirm only intended changes shipped.
   - Run Playwright headless against live URL POST-pull: (a) cold-mount `/tutor/` → /auto-session 200s + session cookie sets, (b) /api/v1/gaps returns 200 with null-taskId rows non-clickable, (c) /tutor/review cold-mount → REVIEW DUE renders + first card visible, (d) zero 4xx/5xx in network log during first paint.
   - Pre-stage `git revert <combined-commit-sha> && git push` in clipboard.
4. **Then push.** Watch live URL paint new bundle hash in DevTools Network for ≥3min.

If push cadence reveals cron-on-push (auto-deploy), this plan stays the same — but the post-push babysit window is the same; the combined deploy still beats two deploys with two rollback narratives.

If user wants to push hotfix NOW anyway (Alex's call): step 1 = `git push origin main`, step 2 = verify VPS pull resolved, step 3 = Playwright smoke against live, step 4 = continue Session 1 FSRS work on top of pushed-main. This is Option 1 with the Risk Analyst's mitigations bolted on. Still acceptable; just less efficient than Option 4.

CONFIDENCE: 7

What would shift:
- → 9: VPS pull mechanism turns out to be manual SSH (then Option 4 is trivially correct; no time pressure on push).
- → 5: VPS pulls every 5min on cron and Alex won't be at the keyboard for 30+min post-push (then session-storm regression from removed early-exit could go undetected; need Playwright smoke FIRST regardless of option).
- → 9: Alex confirms they don't use the ledger drawer or hit /last-task surfaces this week (then hotfix is purely defensive code-hygiene, can wait until any planned deploy).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
