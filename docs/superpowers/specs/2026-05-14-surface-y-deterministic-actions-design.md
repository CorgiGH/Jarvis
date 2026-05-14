# Surface Y deterministic `submit` action — design

> Refactor Surface Y's submit interaction so the answer-submission step is a deterministic controller macro-action triggered by a semantic LLM intent, instead of a low-level `click` the persona must reason out. Scoped precisely to the one proven failure point. Council-shaped (two `claude-council-lite` rounds).

## Problem

Surface Y is the synthetic naive-student stand-in: an LLM persona drives the live tutor UI via Playwright. It reliably navigates, targets the correct R-code textarea, and types real R code into it — then loops `action:type` instead of `action:click` on the CHECK ANSWER button. Loop-detection halts it at 3 identical actions. Six operational re-runs all end this way; the persona's own `observation` field even says "ready to submit." Consequence: Surface Y has never reached a gradeable endpoint, so it produces zero real `tutor_events`.

**Key fact:** the persona's navigation AND field-targeting already work reliably. ONLY the `type`→`click` submit transition fails. The fix must hit that boundary — not rebuild what works.

## Council provenance

| Round | Question | Verdict | Cache |
|-------|----------|---------|-------|
| 1 | Strategic: A (stronger model) / B (accept V1) / C (controller hack)? | WRONG APPROACH — false trichotomy on an unverified diagnosis. Path: instrument the stuck step, decouple the downstream unblock, fix Surface Y as architecture. | `.claude/council-cache/council-1778772227-surface-y-abc-decision.md` |
| 2 | Implementation: which realization of the principled split? | APPROVED — Approach B (semantic `submit` action), with a mandatory selector contract + persona-prompt gating sub-task. | `.claude/council-cache/council-1778774258-surface-y-impl-approach.md` |

Round 2 named the `type`→`click` loop as the textbook pure-primitive agent failure (browser-use / WebVoyager / ReAct / MCP all ship a terminal `done`-style semantic action for exactly this reason): the model repeats the last content-producing action because it has no primitive meaning "I am finished." Adding `submit` supplies the missing affordance — it is not a rename of the failing decision.

## The change

Add one semantic action — `submit` — to the persona's action vocabulary. The LLM emits `{action:"submit"}` when it judges its answer ready. The controller executes `submit` deterministically: resolve the drill's CHECK ANSWER control via an exact single-match selector, assert exactly one match, click. Everything else is untouched: `click`, `type`, `navigate`, `ask_sidekick`, `give_up`, the affordance scan, and field-targeting all stay as-is.

This is the prior council's "principled persona/harness split" scoped exactly to the proven failure point. The persona keeps the *semantic* decision (is my answer ready?); the controller takes the *mechanical* decision (which button, where, click).

## Action contract

The persona's JSON reply gains `submit` as an allowed `action` value:

```
"action": "click" | "type" | "navigate" | "ask_sidekick" | "submit" | "give_up"
```

`submit` carries no `target` and no `payload` — it is a pure intent. The controller owns the mechanics.

## Selector resolution + hard-fail contract (mandatory)

The controller resolves CHECK ANSWER to an **exact, single-match selector** — a `data-testid` if the drill exposes one, else an exact-text match. It MUST:

1. Resolve the selector against the live drill DOM.
2. Assert `count() === 1` before clicking.
3. On zero or multiple matches: push a visible `error` step into the transcript and `break` the run loop — a loud, recoverable failure.

A permissive "submit-class" or "looks-like-submit" heuristic is explicitly forbidden: it silently degrades this approach into the rejected controller-auto-submit hack and risks a wrong auto-click on the live tutor site Alex studies on.

The exact selector value is resolved in the implementation plan's first task (load the live drill DOM, identify CHECK ANSWER's stable handle). The spec requires the *contract* above; the plan pins the *value*.

## Persona-prompt gating sub-task

`surface-y-persona.mjs` `buildPersonaPrompt` must be updated **before** any live verification run:

- Add `submit` to the action enum in the JSON reply schema.
- Add an ACTION RULE: *"When your answer field shows `[current value: ...]` and you believe the answer is ready, emit `{action:"submit"}` — do NOT keep typing into a filled field."*

This is the gating sub-task: a live verification run is quota-scarce (`:free` ~50 calls/day), so the prompt must reliably elicit `submit` before a run is spent. Adding the action without the prompt work would waste the run.

## Transcript + event marking

The `submit` step is recorded as **persona-CHOSEN** (the `{action:"submit"}` came from the LLM) and **controller-EXECUTED** (the controller performed the click mechanics). Both facts appear in the transcript entry, consistent with the per-step executor-marking decision made this session. No backend change: Surface-Y-driven `tutor_events` remain `is_synthetic:true` via the existing `X-Standin-Run:1` header — the existing `is_synthetic` design already keeps them out of real-user grading by default.

## Components touched

| File | Change |
|------|--------|
| `tools/surface-y-persona.mjs` | `buildPersonaPrompt`: add `submit` to the action enum + the ACTION RULE above. (Gating sub-task.) |
| `tools/surface-y.mjs` | Action-execution block: add a `submit` branch — resolve selector, assert single match, click, or loud-fail. Transcript entry marks persona-chosen + controller-executed. |
| `tools/surface-y.test.mjs` | Add: `submit` executes a click on the resolved selector; `submit` hard-fails loudly on ambiguous/missing selector. Existing loop-detection + `runStandin` tests must stay green. A `submit`-loop is still caught by existing `(action,target)` loop-detection (3× `submit` → break) — covered by a test. |

## Acceptance criteria

This is a CLI-tool change, not a paint-mounted UI surface — acceptance is anchored to behavior, not `data-testid` selectors.

| What must be true after the change | How to verify |
|------------------------------------|---------------|
| `submit` is in the persona action enum + ACTION RULE present | Read `surface-y-persona.mjs`; `buildPersonaPrompt` output contains `submit` + the rule. |
| `submit` branch resolves an exact single-match selector, hard-fails loudly otherwise | `npm run test:tools` — new `submit` tests pass; existing 57 stay green. |
| Live run: persona emits `submit` and the drill completes | Next quota-gated Surface Y run shows a `submit` transcript step AND a `drill_grade` event reaches the backend `tutor_events` log. |
| Devil's Advocate failure mode ruled out | If the live run still loops `type` despite the `submit` action + updated prompt, the approach was insufficient — escalate, do not prompt-tune past the model-capability floor. |

The live run is the real acceptance test. It is empirically settleable on the next run thanks to the affordance-payload instrumentation already shipped (uncommitted) earlier this session.

## Out of scope

- **Broader semantic verb set.** First Principles (council round 2) suggested `submit` + `next` + `navigate-to-step`. Deferred — YAGNI until a second multi-step transition actually bites. `navigate` already works.
- **Groq fallback wiring.** A Groq `:free` key was tested this session and works (serves `openai/gpt-oss-120b`); wiring it as an OpenRouter fallback is a separate, user-deferred effort.
- **Any change to `click` / `type` / `navigate` / `ask_sidekick` / `give_up` or the affordance scan / field-targeting.** They work — leave them.
- **Controller auto-submit (council Approach A)** and **full semantic vocab restructure (Approach C)** — both rejected by council round 2.

## Risks + mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| Controller's submit-selector heuristic mis-fires on a page with multiple enabled buttons → wrong auto-click on the live tutor | HIGH (if unmitigated) | Exact single-match selector + `count()===1` assertion + loud hard-fail on ambiguity. No class heuristic. |
| The model still won't emit `submit` (Devil's Advocate's concern: `submit` is still a semantic action the same model must choose) | MEDIUM | Persona-prompt gating sub-task elicits it deterministically; the next live run is the acceptance test; clear escalation path if it fails. |
| Adding a 4th-ish action confuses the weak `:free` model on other steps | LOW | Single additive action, clearly scoped in the ACTION RULE; existing actions unchanged. |
| Quota-scarce verification — a second failed live run costs more than the diff | MEDIUM | Persona-prompt work gates the code; land it in one run. |
