# Council review — 1778694612

**Problem:** Trigger model for the 3-surface student-stand-in design (X trace-grader, Z novice-visual, Y stand-in). How should each surface be fired?

**Proposed approaches:**
- P1 Manual CLI per surface
- P2 Nightly cron during offline window 03:00–06:00
- P3 On-deploy gate for X+Z, manual Y
- P4 Mix manual CLI + dev-UI "Dogfood this page" button

**Project context:** jarvis-tutor solo dev (Alex). PS HW due 2026-05-21 (8 days). OpenRouter `:free` chain only (quota cascades documented). 3-surface design X→Z→Y in flight. Quota cascade is documented CRITICAL risk; Y must not share live grader+sidekick quota.

**Timestamp:** 2026-05-13T17:50:12Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: All four proposals fixate on *when* to fire surfaces and skip the load-bearing question: *what live mount-state are they grading?* Per the post-2026-05-11 ghost-component + PDF-404 lessons, "feature shipped" is not bundle-green + tests-green — it's user-visible + interaction-clean on the live URL. P1 fires on Alex's whim (he'll skip Z the day before HW), P2 grades a 03:00 snapshot that may not match the 09:00 deploy Alex actually studies on (stale findings = worse than no findings, because Alex will trust them), P3 runs X+Z on-deploy but a deploy gate that fails-loud during a 21:00 study session is a self-inflicted Surface Y-style quota-cascade outage on Alex's primary tool, and P4's "Dogfood this page" button burns live quota mid-study — the exact cascade the Y design budget separates. None of them tie the trigger to a *git-HEAD + bundle-hash + live-paint* fingerprint, so findings cannot be invalidated when the surface changes underneath them — repeating the ghost-component class of bug at the meta-review layer.
KEY CONCERN: Findings without a (git-HEAD, bundle-hash, live-DOM-fingerprint) provenance stamp become zombie advice — Alex will act on a Surface Z critique of a layout that no longer exists, or a Surface Y confusion-tuple from a drill that's since been rewritten, and the "review council" becomes a source of drift instead of a guard against it. Trigger policy is downstream of provenance; you're solving the wrong problem first.
PICKED: NONE-RETHINK

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: Established LLM-eval-harness practice splits into two tiers: (a) cheap deterministic gates on every PR (RAGAS/DeepEval/promptfoo running in GitHub Actions, OpenAI's `evals` repo's CI smoke, Anthropic's internal "regression evals" on model rollouts), and (b) expensive stochastic suites on a schedule (LangSmith scheduled experiments, Anthropic's nightly "behavioral eval" sweeps, DeepMind's periodic SAFE/HELM-style runs). The 3-surface design here maps cleanly: trace-grader X is deterministic-ish and cheap → belongs on the deploy gate like Playwright/Cypress E2E in Vercel/Netlify preview-deploy hooks. Novice-visual Z is medium-cost and benefits from nightly batching like RAGAS's `evaluate()` scheduled jobs or LangSmith's cron-triggered datasets. Student-stand-in Y is the Duolingo/Khan-Academy-style user-simulation tier — these teams (per Duolingo's 2023 ML blog on Birdbrain + KA's Khanmigo eval posts) run user-sim agents in batched offline jobs, NOT on every commit, because token cost and variance demand human triage. P3's on-deploy gate for X+Z collapses two tiers into one and will fire X+Z on every dogfood push — that's the LangSmith anti-pattern flagged in their own docs. P2 nightly-only loses the fast feedback loop X gives you. P4 is closest to industry practice: X=deploy-gate, Z=cron, Y=manual.
KEY CONCERN: Quota cascade collision with live grader+sidekick is the load-bearing constraint and none of P1-P4 spec a token-budget guardrail. Industry pattern (OpenAI evals, LangSmith) is per-job token ceilings + circuit-breaker that aborts the eval if live-traffic latency p95 degrades.
PICKED: P4 (respec'd: X=deploy-gate, Z=cron, Y=manual)

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: P2 nightly cron is a new failure surface (cron config + log rotation + silent-fail debugging) on top of an already-thin ops budget — 8 days to PS HW means every new background process is a tax Alex can't afford to debug at 2am. P1 is zero-implementation but zero-discipline: in build-everything mode with parallel slices flying, "I'll run it manually" decays to "I forgot for 3 days" by Thursday. P3 piggybacks on `deploy.sh` which Alex already touches and already debugs — marginal cost is ~10 lines of shell, marginal debug surface is "same place I'm already looking when deploys break." P4's UI button adds frontend wiring during finals crunch — wrong direction. P3 gates X+Z to a workflow that already runs, and keeps Y manual because Y is the one with judgement calls that benefit from human-in-the-loop.
KEY CONCERN: Adding any background daemon (cron) before finals creates a silent-failure debug surface Alex won't notice until it's already broken something downstream — he doesn't have the attention budget to monitor it.
PICKED: P3

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The framing assumes triggers must be scheduled, automated, or button-pushed — but Alex is one user studying alone with a free-tier quota budget and three different surfaces serving three different cognitive jobs. The real question isn't "when do we run these?" — it's "what event in Alex's study session creates the demand for each surface?" X (trace-grader) needs to fire when Alex submits an attempt. Z (novice-visual) needs to fire when Alex hits confusion on a concept. Y (stand-in) needs to fire when Alex wants a sanity check on his own reasoning. These are three different user-intent events, not three instances of the same trigger problem. Forcing them onto a unified schedule wastes quota on surfaces Alex isn't using; forcing them all to manual CLI adds friction that kills the in-flow study loop.
KEY CONCERN: The proposals conflate "when does the code run" (deploy/CI question) with "when does the surface activate for Alex" (UX question) — council is being asked to pick a CI strategy when the actual job is event-driven surface activation tied to Alex's study actions.
PICKED: P5 — Event-driven per-surface (X on attempt-submit, Z on concept-stuck or dwell-time threshold, Y on explicit "check my reasoning" button)

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: P2 has the highest tail risk: a 03:00 cron firing Y on the same OpenRouter ACCOUNT that backs Alex's drill grader will silently drain the daily quota before he wakes up — "isolated quota" via separate keys doesn't help if the upstream account-level reset is shared. P3 multiplies LLM burn by deploy count (8x/day typical) and couples deploy SUCCESS to a non-deterministic judge — flaky CI shape that will train Alex to ignore failures or disable the gate. P1 rots silently. P4's dev UI button is the cheapest mistake surface: one stray click in prod, or shipping the panel un-gated, burns quota or leaks internal trigger. The only safe configuration is P1 manual CLI as the default + opt-in `RUN_LLM_EVAL=1` env flag on cheap surfaces, non-blocking, with Y kept strictly manual and rate-limited to one run per session.
KEY CONCERN: P2 nightly cron silently exhausting shared-account OpenRouter quota before Alex's 09:00 study session — failure is invisible until the drill grader 429s mid-drill, no human in the loop to notice the cron drained him.
PICKED: P1

---

## Sanity Check

SANITY Devil's Advocate: PASS — Surfaces a real cross-cutting concern (provenance stamp) that the framing missed. Concrete reference to ghost-component + PDF-404 lessons.

SANITY Domain Expert: PASS — Named real frameworks (RAGAS, DeepEval, promptfoo, OpenAI evals, LangSmith, Anthropic regression evals, DeepMind SAFE/HELM, Cypress, Vercel preview-deploy hooks, Duolingo Birdbrain, KA Khanmigo). Two-tier industry pattern is well-established.

SANITY Pragmatist: PASS — Concrete cost reasoning grounded in deploy.sh reuse. On-persona, CONDITIONAL with explicit constraint.

SANITY First Principles: PASS — Actively rejects framing, proposes P5 (event-driven) tied to user intent. On-persona.

SANITY Risk Analyst: PASS — Two concrete risks with named failure modes (P2 account-level quota shared reset, P3 flaky CI gate training Alex to ignore). Mitigation specific (RUN_LLM_EVAL=1 env flag).

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
No single proposed trigger ships as-is; the council split 5 ways with 2 REJECT + 3 CONDITIONAL each picking a different option. Two cross-cutting concerns dominate: (1) Devil's Advocate's PROVENANCE STAMP is non-negotiable regardless of trigger — findings without `(git-HEAD, bundle-hash, live-DOM-fingerprint, timestamp)` become zombie advice and replicate the ghost-component lesson at the meta-review layer. (2) Risk Analyst's ACCOUNT-LEVEL QUOTA risk must be verified before ANY automated trigger — separate OpenRouter API keys may NOT isolate `:free` quota if the upstream reset clock is shared on the account, in which case automated runs silently drain Alex's live study tools. The right path differentiates per surface (DE + FP both said so), reuses existing infra where possible (P), defaults to safe (RA), and adds provenance stamps everywhere (DA).

AGENT CONSENSUS: 2 REJECT (DA → NONE-RETHINK, FP → P5), 3 CONDITIONAL (DE → P4-respec'd, P → P3, RA → P1). Zero clean APPROVE. Zero flagged.

KEY ISSUES:
1. **Provenance stamp on findings (CRITICAL — DA).** Every finding written to `docs/standin-findings/DRAFT-*.md` MUST carry `(git_head, bundle_hash, live_dom_fingerprint, timestamp_utc, surface_version)`. Stale findings auto-flagged when any of these don't match current state at read-time. Non-negotiable.
2. **Account-level OpenRouter quota verification (CRITICAL — RA).** Alex must verify whether separate API keys on the same OpenRouter account share a daily `:free` quota ceiling. If YES, automated triggers are forbidden during study hours. If NO, automated runs are bounded by per-key limits and safer.
3. **Surface-differentiated trigger (HIGH — DE + FP convergence).** Industry pattern (DE) + first-principles user-intent (FP) both say: X cheap+frequent → deploy-or-event trigger; Z medium → manual+optional event; Y heavy+stochastic → manual only with human triage.
4. **Deploy-gate non-blocking (HIGH — RA + DE).** If X (or X+Z) gates `deploy.sh`, it MUST be advisory not blocking. Flaky LLM judges blocking deploys train Alex to disable the gate.

RECOMMENDED PATH:
**Surface-differentiated trigger with provenance stamps and a quota-verification prerequisite.**

Per-surface plan:
- **X (trace-grader):** Manual CLI default (`node tools/surface-x.mjs --session <recording-id>`). Optional `RUN_LLM_EVAL=1` env flag on `deploy.sh` runs X on the deploy's freshly-recorded smoke trace; advisory output (never blocks deploy). Future event-driven hook (FP angle): fire X on drill-attempt-submit if Alex flips a `--watch` flag.
- **Z (novice-visual):** Manual CLI default (`node tools/surface-z.mjs --mode static|piggyback`). NO cron in V1 — RA's account-quota risk + DA's provenance-stamp drift make cron unsafe before verification. Piggyback variant fires inside Y's session automatically.
- **Y (stand-in):** Manual CLI ONLY. Explicit `node tools/surface-y.mjs --task <id> --schema <path>`. Hard ceiling ≤50 LLM calls/session, separate OpenRouter API key, runs outside study hours preferred but not enforced (user discretion). Findings to quarantined `docs/standin-findings/DRAFT-Y-*.md` — human-review gate before any influence on design.

Cross-cutting requirements:
- **Provenance stamp module:** New `tools/lib/provenance.mjs` exports `getStamp()` returning `{git_head, bundle_hash, live_dom_fingerprint, timestamp_utc, surface_version}`. ALL surface findings prepend this stamp as YAML frontmatter. Reader tool `tools/findings-stale-check.mjs` flags `[STALE]` if any field doesn't match current state.
- **Quota verification prerequisite (BLOCKING ship of any surface):** Alex (or council-suggested research dispatch) verifies OpenRouter account-level free-tier reset behavior. Document in `docs/notes/2026-05-13-openrouter-quota-isolation.md`. Until verified, all surfaces are manual-only.
- **Token-budget circuit-breaker (DE recommendation):** Each surface caps per-run token spend; aborts if live-traffic p95 latency degrades during run (probe `/api/v1/health` between LLM calls).

Confidence: 8
[Would climb to 9 if quota-verification check returns "separate keys = separate quotas" and provenance-stamp module is sketched as part of spec. Would drop to 6 if Alex insists on cron-or-deploy-gate without provenance + quota verification.]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1778694612-standin-trigger-model.md
