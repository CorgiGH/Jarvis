# Council review — 1778772227

**Problem:** Surface Y (synthetic naive-student stand-in: LLM-persona-driven Playwright agent driving the real tutor UI) reliably navigates to the drill, writes real R code into the correct textarea, observes "ready to submit" — then loops `action:type` instead of `action:click` on CHECK ANSWER. Loop-detection halts it at 3 identical actions. 6 operational re-runs all end this way. Believed root cause: model-capability floor on `openai/gpt-oss-120b:free` for the multi-step type→click-submit transition; explicit ACTION RULES prompting did not fix it; UI mechanism Playwright-verified sound (`page.fill` enables the button). Consequence: ZERO real `tutor_events` produced, which blocks fattening the Surface X golden fixture.

**Proposed approach:** Pick one of — A: try a stronger free model (one ~6-call re-run; quota-blocked until 00:00 UTC reset). B: accept Surface Y V1 as-is, "completes drill end-to-end" → V2 ticket. C: controller-side auto-submit hack — `surface-y.mjs` detects field-filled + submit-enabled and forces the click (no quota, doable now, hacky).

**Project context:** Domain — "jarvis-kotlin", a personal AI-tutor web app for one user (Alex, AI bachelor's, finals Jun 2026) plus a synthetic-QA tooling layer (Surface X/Y/Z). Stack — Kotlin backend, React/TS frontend (Vite), Node `.mjs` tooling, Playwright, OpenRouter `:free` LLMs. Constraints — single-user; HARD no-paid-API (`:free` only, ~50 calls/day shared quota, ~84 already burned today); build-everything mode; direct-to-`main`. Prior decisions — 8-phase tutor overhaul COMPLETE; student-standin plan FULLY EXECUTED (Phases 0–4.1); Surface X + Z shipped & live-verified; Surface Y shipped but operationally stuck.

**Timestamp:** 2026-05-14T15:23:47Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: The A/B/C framing is built on an unexamined assumption: that "model-capability floor" is the real diagnosis. You never verified it. The agent types REAL R code into the CORRECT textarea and its own observation says "ready to submit" — that is not a model too weak to plan a two-step transition; a model that incapable would fail far earlier and more randomly. The far more likely cause is that the controller's observation/affordance payload doesn't expose the CHECK ANSWER button as a clearly clickable, uniquely-selectored action at the moment the field is full — so the model picks the only affordance it's confident about (the textarea it already succeeded with) and re-types. Note your own recent commit history is five straight `fix(standin): Surface Y affordances...` commits — you have been patching the affordance layer for days, which is itself evidence the harness, not the model, is the variable. Every option fails under this: A burns scarce quota proving nothing if a stronger model hits the same blind affordance; B ships a tool whose single most important finding ("submit affordance is invisible to a naive actor") gets misfiled as a model limitation and lost; C is the worst — it hard-codes the controller to paper over the exact UX-friction signal Surface Y exists to detect, corrupting the validity of every future run and still producing synthetic events that aren't real naive-student behavior, poisoning the Surface X golden fixture you're trying to fatten.
KEY CONCERN: The "model-capability floor" root cause is unproven and probably wrong; the loop is most likely a controller-side affordance-exposure bug, which means A/B/C are all treating a symptom — instrument the actual action payload the model receives at the stuck step before spending quota, shipping, or hacking.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The A/B/C framing is wrong because it conflates two distinct agent roles that the established literature keeps separate: the "agent as scripted user" (deterministic harness drives navigation/submission, LLM only makes the judgment calls a human tester would) versus the "agent as fuzzer" (free-roaming exploration to surface unknown-unknowns). Surface Y's actual job — surface UX friction a first-year hits — does not require the LLM to emit low-level `action:click` tool calls at all; that is harness plumbing, and hand-rolling a raw observation-action loop over Playwright without constrained decoding or a tool-use grammar is the reinvented wheel here. This is exactly why frameworks like browser-use, WebVoyager, and the ReAct pattern as actually deployed pin deterministic "macro" actions (submit, navigate) and reserve the model for semantic decisions — the model-capability floor you hit with gpt-oss-120b is a known failure mode of asking a weak model to do step-transition bookkeeping it should never have owned. So C is not a "hack": detecting field-filled-plus-button-enabled and clicking is the correct deterministic-harness boundary, and it's the only option that both unblocks the X-fixture work now and fixes the design error; A just buys a stronger model to paper over the architecture, and B ships a known-broken loop as "V1."
KEY CONCERN: C must be implemented as a principled harness/persona split — deterministic macro-actions (submit/navigate) versus LLM semantic decisions (what to answer, what friction to report) — not a one-off "if filled then click" patch, or you'll re-hit the same boundary confusion at the next multi-step transition.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The A/B/C framing is half wrong because it treats these as mutually exclusive when the cheapest correct move is C+B together: write the auto-submit fallback in `surface-y.mjs` now, ship Surface Y today, and unblock the X-fixture work immediately — no 8.75h wait, no quota gamble. Option A burns ~6 of tomorrow's scarce calls on a coin-flip ("stronger free model" is unverified hope) and even if it works you'd still want the controller fallback for run-to-run reliability, so A is at best a later nice-to-have, not the answer. The "controller does the persona's job" objection is overblown: Surface Y's product purpose is surfacing UX friction *up to* the answer, and a deterministic submit step is normal test-harness scaffolding, not a persona cheat — label it clearly and it costs nothing to maintain. Pure B is the trap: it feels clean but leaves the only downstream dependency (real `tutor_events` for the golden fixture) permanently blocked, which means you didn't actually finish anything.
KEY CONCERN: The auto-submit fallback must be scoped tightly — fire only on the confirmed "field non-empty + CHECK ANSWER enabled" state and log that the controller (not the persona) submitted, so the friction data stays honest and the hack can't silently mask a future real navigation bug.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The A/B/C framing collapses three distinct goals into one and then debates implementation trivia. Strip it down: there are two real deliverables — (1) real gradeable `tutor_events` to fatten the X golden fixture, and (2) UX-friction findings a naive student would hit. These have different right instruments. For goal (1), the fastest, zero-quota, zero-hack path is the user clicking through the drill once himself — that produces authentic, correctly-formed events in minutes and unblocks X immediately; no LLM persona is needed or even appropriate, since the event payload doesn't care who generated it. For goal (2), Surface Y has ALREADY succeeded — it navigated, wrote real R code, and self-reported "ready to submit"; the submit-loop failure is itself a finding (the friction is real but it's the agent's, not the student's, so it's noise) and chasing "completes the drill" is gold-plating an instrument whose job is observation, not completion.
KEY CONCERN: Option C is the worst of all — a controller that force-clicks submit destroys Surface Y's validity as a friction detector (you can no longer trust that a "completed" run reflects what a real student could do) while adding maintenance debt, all to avoid a 5-minute manual click-through that the user can do right now.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Option C is the only CRITICAL-severity failure: it silently corrupts data integrity. A controller-forced CHECK ANSWER click means the captured tutor_events no longer represent naive-student behavior — they are synthetic-controller artifacts wearing a "real event" label. Blast radius is wide and the failure is invisible: the Surface X golden fixture is treated as ground truth by downstream tooling, so every judge calibrated against it inherits the contamination, and nothing flags it because the events look real. That is the worst failure class — silent, propagating, and discovered late. By contrast B's risk (V2 ticket rots, X fixture stays starved) is MEDIUM and fully visible/recoverable, and A's model-lottery risk (one-time pass non-reproducible) is HIGH but self-evident the moment a second run fails — no hidden corruption.
KEY CONCERN: Option C poisons the "golden" fixture with controller-assisted events that are indistinguishable from real ones — reject C outright; do A (gated on the 00:00 UTC reset) and ship B's V1 as the honest interim state, but never let C's events touch the X fixture.

---

## Sanity Check

SANITY Devil's Advocate: PASS
NOTE: clean. Diagnosis-challenge is argued, not asserted; commit-history evidence is concrete; explicitly hedges its own counter-theory ("instrument before spending"). One stray non-English token, no reasoning impact.

SANITY Domain Expert: PASS
NOTE: clean. Names concrete prior art (ReAct, browser-use, WebVoyager, constrained decoding / tool-use grammars); CONDITIONAL with a specified condition.

SANITY Pragmatist: PASS
NOTE: clean. Proposes a C+B blend — in-scope because the invocation explicitly asks "or is the framing wrong?"; CONDITIONAL with a specified, testable condition.

SANITY First Principles: PASS
NOTE: clean. Properly strips the framing, separates the two real goals, offers a from-scratch alternative (manual click-through). REJECT with clear reasoning.

SANITY Risk Analyst: PASS
NOTE: clean. Ranks by severity as required; focuses on the top risk; CONDITIONAL with a hard guard condition. Its "C is CRITICAL" directly contradicts Domain Expert's "C is correct" — that is a substantive disagreement for the judge, not a sanity fault.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: WRONG APPROACH

CORE FINDING:
A/B/C is a false trichotomy resting on an unverified diagnosis. No clean agent endorsed the framing as-given — two REJECT it outright, three rewrite it. The two specific cracks: (1) "model-capability floor" was never instrumented — an agent that types correct R code into the correct box and self-reports "ready to submit" does not look like a model too weak to plan two steps; it looks like the affordance payload not presenting CHECK ANSWER as the obvious next action (Devil's Advocate). (2) The framing conflates two separable goals — "produce real `tutor_events`" and "Surface Y autonomously completes a drill" — and lets the first stay blocked on the second (First Principles). The Domain-Expert-vs-Risk-Analyst clash over C is not a real contradiction: C-as-harness-architecture (deterministic submit/navigate macro-actions) is sound; C-as-untagged-event-source into the golden fixture is the CRITICAL data-integrity failure. They reconcile via tagging.

AGENT CONSENSUS: 2 REJECT, 3 CONDITIONAL, 0 APPROVE — 0 agents flagged. Effectively unanimous that the framing is wrong.

KEY ISSUES:
- **Unproven root cause.** "Model-capability floor" was asserted, not instrumented. The cheap, zero-quota first move is logging the exact affordance-list/payload the model receives at the stuck step — settle whether CHECK ANSWER is even presented as a clean clickable before spending anything. (Devil's Advocate)
- **Architecture error, not a model error.** Surface Y hand-rolls a raw observation-action loop where the LLM emits low-level click/type. Established practice (browser-use, WebVoyager, ReAct-as-deployed) pins deterministic *macro-actions* for plumbing (submit, navigate) and reserves the LLM for semantic decisions. "C the hack" is actually the correct boundary — but only done as a principled persona/harness split, not a one-off `if`. (Domain Expert, Pragmatist)
- **Two goals are tangled.** Real `tutor_events` for the X fixture do not require Surface Y to finish autonomously — the user clicking the drill once himself produces authentic events in minutes, zero quota, and aligns with the existing project norm that Alex validates his own drill answers. UX-friction findings are a separate deliverable Surface Y has *already* produced. (First Principles)
- **Data-integrity guard is non-negotiable.** Any controller-submitted event must be tagged so it can never silently enter the golden fixture as naive-student behavior. CRITICAL if skipped — contamination is silent, propagating, discovered late. (Risk Analyst, Pragmatist)

RECOMMENDED PATH:
Drop A/B/C. Do this instead, in order:
1. **Instrument the stuck step now (zero quota).** Log the verbatim affordance-list/payload the persona receives at the loop point. This either confirms or kills the "model-capability floor" diagnosis — and the instrument-first pattern already paid off once (it exposed the placeholder-echo bug last segment).
2. **Unblock the X fixture independently of Surface Y (zero quota).** Have Alex click through the drill once by hand to produce real, correctly-formed `tutor_events`. This decouples the downstream work from the agent-loop debugging entirely.
3. **Fix Surface Y as architecture, not as a hack.** Make submit/navigate deterministic controller macro-actions; keep the LLM for semantic decisions only. This is "C done right" — and it MUST tag controller-submitted events so they are excluded from (or clearly marked in) the golden fixture.
4. **Demote A to optional-later.** Even if a stronger free model completes the drill once, it is a model-lottery result and you would still want the deterministic submit for reproducibility. Not worth burning the quota-reset wait on as the primary play.
B is partly right — Surface Y's friction findings so far are valid V1 output — but "completes drill end-to-end → vague V2 ticket" misnames the remaining work. The remaining work is the harness split in step 3, scoped concretely.

CONFIDENCE: 7
What would raise it: the result of step 1. If instrumentation shows CHECK ANSWER *is* cleanly presented and the model still won't click it, the "capability floor" reading gains weight and step 3 becomes the whole answer. If it shows the affordance is muddy at that step, step 3 may be a small affordance fix rather than a full macro-action refactor. Either way the recommended path holds — it front-loads the cheap diagnostic and decouples the downstream unblock — which is why this is not lower.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
