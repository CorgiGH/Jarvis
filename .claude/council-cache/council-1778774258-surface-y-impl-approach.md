# Council review — 1778774258

**Problem:** Which of three implementations realizes the prior council's "principled persona/harness split" for Surface Y. Surface Y (synthetic naive-student agent — LLM persona driving the live tutor UI via Playwright) reliably navigates, targets the correct R-code textarea, and types real R code — then loops `action:type` instead of `action:click` on CHECK ANSWER; loop-detection halts it at 3. KEY FACT: navigation AND field-targeting already work reliably — ONLY the `type`→`click` submit transition fails.

**Proposed approaches:** A — Controller auto-submit (no vocab change; controller detects ready-to-submit state and clicks behind the LLM's back; smallest diff; the "hack"). B — Semantic `submit` action (add ONE semantic action to the persona vocab; LLM emits it when answer ready; controller executes deterministically; keep click/type/navigate for everything else; medium diff; CONTROLLER RECOMMENDATION). C — Full semantic vocab restructure (LLM emits only semantic intents; controller owns ALL DOM mechanics including field-targeting; biggest diff; rebuilds nav + field-targeting which currently work).

**Project context:** Domain — "jarvis-kotlin", a personal AI-tutor web app for one user (Alex, AI bachelor's, finals Jun 2026) + synthetic-QA tooling layer Surface X/Y/Z. Stack — Kotlin backend, React/TS frontend, Node ESM `.mjs` tooling, Playwright, OpenRouter `:free` LLMs; tool is `tools/surface-y.mjs` + `surface-y-persona.mjs` + `surface-y-gate.mjs`; 57 passing Node tests. Constraints — single-user; HARD no-paid-API (`:free` only, ~50/day quota, verification re-runs scarce); build-everything mode; direct-to-`main`. Prior decisions — an earlier council delivered WRONG APPROACH on the old strategic framing, recommended path = instrument the stuck step (DONE) + Alex manual click-through + "fix Surface Y as architecture"; a clarifying question settled that the finding doc marks each step persona-chosen vs controller-executed regardless of approach.

**Timestamp:** 2026-05-14T15:57:38Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: B is built on a diagnostic fantasy: that the model loops `type` because the vocab lacks a "submit" concept. It doesn't. The model already typed correct R code, already wrote "ready to submit" in its observation, and `click` on the CHECK ANSWER button was available the entire time — it had every token it needed to emit the right action and chose `type` anyway. Adding a `submit` verb just renames the exact semantic action-selection decision the model already fails; you will get `type` looping until loop-detection halts, except now with one more vocab entry for a weak `:free` model to fumble on every other step. Worse, B's whole justification leans on "nav + field-targeting already work reliably" — but that's an unaudited inference from a handful of runs of a stochastic LLM; the `type`→`click` failure IS field-stage behavior misfiring, which is evidence the model's DOM-action selection is globally shaky, not locally sound. The honest read is that A/B/C are all symptom patches on an under-powered model, and B is the worst kind because it looks principled while changing nothing about the failure mechanism.
KEY CONCERN: B does not touch the actual failure — the model's inability to select the submit action is semantic, and `submit` is still a semantic action the same model must choose; renaming the verb cannot fix a choice the model already had the means to make correctly and didn't.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: Approach B is the established pattern, not an anti-pattern — every production LLM browser agent (browser-use, WebVoyager, the ReAct-style tool harnesses, MCP servers) ships exactly this mixed granularity: low-level primitives (`click`/`type`/`navigate`) plus higher-level semantic/composite actions (`extract_content`, `done`, `ask_human`) in one action space. The action-space-design literature treats abstraction level as per-action ergonomics, not a global purity invariant; the "done/submit" terminal action is the single most universal semantic action in the field precisely because terminal-state transitions are where pure-primitive agents reliably stall (your `type`→`click` loop is the textbook failure — the model has no primitive that means "I am finished," so it repeats the last content-producing one). B is also the correct application of the prior council's principled-split mandate: scope the deterministic controller macro-action to the proven failure point. Approach A reproduces the loop invisibly and breaks the persona's own transcript honesty (its `observation` says "ready to submit" but never gets to act on it), and C is a rewrite of `navigate` + field-targeting that the KEY FACT says already work reliably — that violates "don't rebuild working subsystems," and "controller owns field-targeting" is genuinely harder than it looks because affordance-scanning for *presence* is far easier than disambiguating *which* of N textareas is the answer field without the LLM's semantic read.
KEY CONCERN: Define `submit`'s ready-state contract tightly — it must target the answer-bearing CHECK ANSWER control via a stable selector/affordance-id (not "click whatever looks like submit"), or B silently degenerates into Approach A's opaque guessing and you lose the determinism the split was supposed to buy.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The bug is a single `type`→`click` transition that only manifests under a live run, and `:free` quota makes verification runs the scarce resource — so the right approach minimizes re-runs-to-confirm, not lines of code. Approach A is ~1 function but it's verify-fragile: an opaque controller heuristic guessing "ready-to-submit" can mis-fire silently, and you'll burn quota runs chasing false greens; it also leaves the exact persona blind spot (no submit intent) unfixed. Approach C rebuilds nav and field-targeting that ALREADY WORK — that's the worst trade here: large blast radius, real risk the 57 tests become a multi-hour fix, and a single user feels zero benefit. Approach B is the proportionate fix: one explicit `submit` action makes the persona's intent legible, the controller executes it deterministically (no heuristic to mis-guess), the diff is bounded to additive vocab + prompt + one execute branch + tests, and it's the cleanest thing to hand off.
KEY CONCERN: B is only worth its medium diff if it lands in ONE verification run — gate the work on first updating `surface-y-persona.mjs` so the LLM reliably emits `submit` at the right moment (prompt the model into it deterministically), since a second failed live run costs more than the entire code diff.

## 🧱 First Principles

AGENT: First Principles
STANCE: CONDITIONAL
REASONING: Strip the framing: Surface Y exists to find UX friction a naive first-year hits. A real beginner with hands and eyes does NOT loop on "type then click a separate button" — they see the button and click it. So the `type`→`click` loop is a HARNESS artifact, not a real finding, and any of A/B/C are legitimate plumbing fixes. But the deeper error is letting a "naive student" persona choose low-level `click`/`type`/CSS-selector actions at all — a real student thinks in intents ("submit my answer", "go to the next problem"), not DOM mechanics. The loop isn't the LLM failing; it's the action vocabulary mismatching the persona, and the observation/affordance layer feeding it a representation where `click submit` isn't salient. That said, C's full rebuild of working nav + field-targeting is reckless waste — the right move is B's direction (semantic actions) but B under-commits by adding only ONE semantic verb while leaving the rest of the low-level vocab in place to re-bite later.
KEY CONCERN: B is correct in direction but scoped to a single symptom — `submit` alone fixes this loop while leaving the same intent/primitive mismatch everywhere else; commit to a small semantic verb set (submit, next, navigate-to-step) rather than one bolt-on action, but do NOT rebuild the nav/targeting that already works.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Approach A is the worst: submitting behind the LLM's back means Surface Y's transcript records `type` actions but never the submit, so the findings doc Alex reviews silently fabricates a "completed" run — a quality-eval tool that lies about what the persona did is worse than one that visibly halts at loop-detection 3, and on the live site a mis-fired heuristic click is unattributable to any persona action. C is HIGH risk: it rebuilds nav + field-targeting that already work, putting the 57 passing tests and the only-proven-working parts of the pipeline in regression scope to fix a localized `type`→`click` gap — disproportionate, and quota-scarce re-runs make a regression expensive to even detect. B is the right shape because the LLM still explicitly emits `submit`, so the transcript stays truthful and the diff is contained to the one broken transition; its real risk (MEDIUM, becomes HIGH if unmitigated) is the controller's "find the enabled submit-class button" heuristic mis-firing on a page with two enabled buttons or a destructive look-alike, which on the live corgflix site is a wrong auto-click blast radius. Mitigation makes B safe: scope the submit selector to the drill's actual CHECK ANSWER element (exact testid/text, not a class heuristic), assert exactly one match before clicking, hard-fail visibly if zero or multiple — a loud failure is recoverable under quota scarcity, a silent wrong click is not.
KEY CONCERN: B's submit-button resolution must be an exact, single-match selector that hard-fails loudly on ambiguity — never a permissive submit-class heuristic that can silently click the wrong live-site button.

---

## Sanity Check

SANITY Devil's Advocate: PASS
NOTE: clean. The "renaming the verb ≠ fixing the mechanism" critique is substantive and well-argued; the challenge to the "already works" assumption is fair. Its core diagnostic claim competes with Domain Expert's — that is a real disagreement for the judge, not a reasoning fault.

SANITY Domain Expert: PASS
NOTE: clean. Names concrete prior art (browser-use, WebVoyager, ReAct, MCP, the `done` terminal action, action-space-design literature); directly and credibly rebuts the Devil's Advocate mechanism claim. CONDITIONAL with a specified contract.

SANITY Pragmatist: PASS
NOTE: clean. Reasoning grounded in the quota-scarce-verification constraint; CONDITIONAL with a specific, actionable gate (persona-prompt work first).

SANITY First Principles: PASS
NOTE: clean. Properly strips the framing (harness-artifact vs real finding). Its "small verb set" expansion is a minority position the other three conditionals implicitly push back on — substantive divergence, not a fault.

SANITY Risk Analyst: PASS
NOTE: clean. Ranks by severity; concrete failure modes; CONDITIONAL with a hard guard. Its selector-contract condition converges almost exactly with the Domain Expert's.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: APPROVED

CORE FINDING:
Approach B is right, and the council is near-unanimous on the negatives: every clean agent rejects A (submits behind the LLM's back → the findings doc silently fabricates a "completed" run; a QA tool that lies is worse than one that visibly halts) and every clean agent rejects C (rebuilds navigation + field-targeting that already work → disproportionate blast radius on the 57 tests for a localized one-transition bug). The one REJECT — Devil's Advocate — argues B merely renames the failing decision. That is the crux, and it is credibly rebutted: Domain Expert, citing browser-use / WebVoyager / ReAct / MCP, identifies the `type`→`click` loop as the textbook pure-primitive failure mode — the model repeats the last content-producing action because it has no terminal "I am finished" primitive. Adding `submit` is not a rename; it supplies the missing affordance. But Devil's Advocate's concern is not dismissed — it is the exact thing the next live run must rule out, and there is a clear escalation path if it isn't.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL, 0 APPROVE — 0 agents flagged. Unanimous against A and C; 4 of 5 endorse B's direction; the lone REJECT is credibly rebutted by named prior art.

KEY ISSUES:
- **Selector contract is mandatory, not optional.** Domain Expert and Risk Analyst independently converge: `submit` must resolve to the drill's actual CHECK ANSWER control via an exact, single-match selector (testid/text), assert exactly one match, and hard-fail loudly on zero-or-multiple. A permissive "submit-class" heuristic silently degrades B into A and risks a wrong auto-click on the live site Alex studies on.
- **Persona-prompt work gates the code.** Pragmatist: B only pays off if it lands in ONE verification run (quota is scarce). Update `surface-y-persona.mjs` so the model reliably emits `submit` at the right moment BEFORE spending a live run — don't just add the action and hope.
- **The Devil's Advocate failure mode is the acceptance test.** If, given the `submit` action + updated prompt + the already-shipped instrumentation, the model STILL loops `type` — then B was insufficient and the diagnosis was wrong. That single live run is the real confidence-mover.
- **Resist scope creep.** First Principles wants a broader semantic verb set (submit/next/navigate-to-step). The majority disagrees: scope to the one proven failure point now; `navigate` already works. A broader semantic vocab is a YAGNI-until-needed follow-up, not part of this change.

RECOMMENDED PATH:
Implement Approach B with these as mandatory, not aspirational:
1. Add the single semantic `submit` action to the persona vocabulary — nothing else added.
2. Controller executes `submit` via an exact single-match selector for CHECK ANSWER; assert exactly one match; hard-fail loudly (visible error step in the transcript) on zero or multiple. Never a class heuristic.
3. Update `surface-y-persona.mjs` first — the prompt must reliably elicit `submit` when the answer field is filled. Treat this as the gating sub-task.
4. Keep the transcript truthful: `submit` is persona-CHOSEN, controller-EXECUTED — record both, consistent with the "mark each step" decision already made.
5. Leave `click`/`type`/`navigate` and field-targeting untouched.
6. The next live run is the acceptance test for the Devil's Advocate concern. If `submit` still loops, escalate — do not keep prompt-tuning past the model-capability floor.

CONFIDENCE: 7
What would change it: the next live run's result. Everything except "will the model actually emit `submit`" is high-confidence (A and C are clearly out; the selector contract is clearly required). The one genuine unknown is the Devil's Advocate mechanism question, and it is empirically settleable on the very next run thanks to the already-shipped instrumentation — which is why this is APPROVED rather than held.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
