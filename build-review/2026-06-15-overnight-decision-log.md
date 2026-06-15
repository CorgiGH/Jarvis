# Overnight autonomous session — decision log (2026-06-15 → 16)

PM (Alex) asleep. Claude works the **oracle-first foundation** of the locked master plan
(`docs/superpowers/plans/2026-06-15-end-to-end-master-plan-to-100-v2.md`). Every decision (mine + council's)
is appended here with grounding. Machine gates carry ship authority — NOT my "verified".

## Standing parameters (Alex-ratified, this session start)

- **Git:** commit to LOCAL `main` by explicit path only (reversible). **NO push to origin. NO VPS deploy.** Never `git add -A` / `git clean` on main.
- **VPS:** "i think so, test it" → reachability self-verified below. May use for testing only; **never deploy to the live site**.
- **Council:** local **council-lite** (adversarial Claude subagents) — respects no-paid-APIs. Full multi-provider council NOT used.
- **Scope:** Phase 0 (gate substrate) + A0 (commit+green viz WIP) + Phase A (generate-from-algorithm figures) + **attempt lesson generation** (folded into the SPIKE: measure relay-model capability vs the oracles; author depth/CHECK banks for the ~7 real KCs). No site-visible breadth shipped (oracle-first/deploy-last; "don't update the site").
- **Discipline:** suites FOREGROUND from `tutor-web` cwd (frontend) / repo root (gradle), explicit timeout, stream-to-log, NEVER `| tail`/`Select-Object -Last`; never 2 vitest at once. I re-run the gate myself before any commit — a subagent "green" is inadmissible.

## Live-state probes (session start)

- **VPS SSH = REACHABLE.** `root@46.247.109.91` → host `panel`, up 31 days, idle load. Usable for testing; **never deploy**.
- **Relay `:9999` LISTENING** (pid 13488) — auth not yet probed. Dev Ktor backend `:8080` (pid 45884, SANDBOX db) + vite `:5173` (pid 4464) still up from SESSION-73.
- Git: `main @ 759eeb4`, 5 ahead of origin, 0 behind (matches BRIDGE-HEAD).

## Phase-0 recon (workflow w8upm0b8f)

All 8 foundation defects still LIVE (no code ran since SESSION-73). Board in workflow output; recommended fix order 1→4→3 then registry #6, #2 parallel, #8 register-as-debt, #5 PM decision.

## PROCESS NOTE — PM-delegation correction

I started **hand-editing** the 3 trivial fixes myself (fail-open flip, relay-retry ×2, StateCache isolation ×2). Alex stopped me: *"you are PM not a worker."* Correct. Switched to full workflow-delegation: workflows build + run gates foreground→logs; I review machine evidence + gate commits; council for decisions. The 3 applied edits are KEPT (correct, reverting = theater) and **gated** by workflow `w6po39rko`, not redone by hand.

## Decisions

| # | Decision | Made by | Grounding | Status |
|---|----------|---------|-----------|--------|
| D0 | Overnight scope + parameters above | Alex (4-question audit) | this session | LOCKED |
| D1 | VPS reachable → may use for test, never deploy | probe | SSH OK to `panel` | LOCKED |
| D2 | Keep the 3 hand-applied fixes; gate (don't redo) them via workflow | Claude (PM) | reverting correct work = wasted tokens | LOCKED |
| D3 | **#1 impeccable blocking-flip REVERTED** — premature | gate evidence | local `detect`=EXIT 1, filter kept **25 of 37** real findings in committed prod components (ChatPane/DrillStack/Sidekick/beat comps). Flip would red CI. Becomes a **calibration work-item** (triage 25 → fix or de-scope antipattern), not a one-liner. | test.yml back to fail-open |
| D4 | **recon MISDIAGNOSED the StateCache flake** | my own re-run | recon called it a test-isolation leak; the REAL ~1-in-2 red is `IntegrationHarnessTest.stateCacheConcurrentPersistNeverTearsJson:160` — `StateCache.persist` does a **non-atomic** `Files.writeString(TRUNCATE_EXISTING)`, so K concurrent writers interleave → corrupt/empty file → `assertNotNull` fails. My loop: **2/3 red** (agent falsely claimed 4/4 — inadmissible-green lesson, live again). Fix = **atomic temp+rename** (production change to StateCache.kt). The test-isolation edit is kept as hygiene but is NOT the flake fix. | fix delegated |

### Gate status (Phase-0 fixes, this run)
- **#2 schema-parity** (SchemaHashParityTest.kt + test_schema_hash_parity.py): GREEN both runtimes (pending my final consolidated re-gate).
- **#3 relay-retry** (VerifyContentCli + TrustRoutes): GREEN (TrustRoutesTest + verify.* pass in every run; only the concurrent-persist test reds).
- **#4 StateCache:** test-isolation done; **atomic-persist fix IN PROGRESS** (the real flake).
- **#7 no-clip leg seeds** (seeded-clip-legs.spec.ts): GREEN (leg1+leg3 RED+clean, 4/4).
- **Deferred (NOT Phase-0 fixes / not mine):** matrix-grid `mg-root`/`mgg-root` strict-mode double-mount on /tutor/viz-demo (uncommitted viz WIP → A0/Phase-A scope); drill-4b legibility self-test resolved-instead-of-rejected locally (pre-existing, committed, ?env/chromium-contrast — re-check in CI).

## Pending decisions (queued for council-lite)

- **D-A — figure generate-from-algorithm serialization contract** (Phase A; the "net-new design" the plan names): shape of the Kotlin→React typed state/event stream → keyframes. Council-lite with full context before any build.
- **D-B — defect #5 visual baselines:** enforce on Linux CI (regen + {platform} suffix + wire e2e:visual) vs keep documented fail-open (current posture, plan4a:1504). Council-lite.
