# Council review — 1778960917

**Problem:** Surface Y adversarial-leaked corpus is at n=0; tripwire threshold tuning needs n≥3. Durable rule: adversarial-leaked must be hand-authored OFFLINE. Binding council 1778839098: Claude-CLI on Y persona BANNED (fixture-poisoning, no detector).

**Proposed approach:** Use Playwright MCP (browser-control from a Claude conversation) to drive live `/tutor/`. Capture interaction SHAPES only — click order, DOM, network envelopes, timing, screenshots. Claude (me) writes the persona transcript PROSE BY HAND from the captured shapes. Claim: this is "hand-authored OFFLINE" because prose-authoring is offline; MCP only captures factual shapes a real session would produce.

**Project context:** jarvis-kotlin tutor, single-user, Surface Y on `:free` OpenRouter band only, tripwire AND-gates on `findings_count==0`, corpus n=2 authentic-naive today. Tripwire reads 4 features: submit-step-index, confusion-event-count, sidekick-ask-count, findings-count.

**Timestamp:** 2026-05-16T19:48:37Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: The proposal smuggles the exact vector the ban exists to prevent, just relocated. The ban targets "capability shapes leaking into the corpus" — and the click sequence IS a capability shape. Deciding which `data-testid` to click, in what order, and when to submit IS problem-decomposition; that's the same Claude-reasoning surface that 1778839098 quarantined, now wearing a Playwright costume. Calling the prose "the persona" and the clicks "just shape" is a definitional sleight-of-hand: the tripwire fires on `min_steps_for_zero_confusion_flag` and `submit_step_index_max_for_flawless_flag` — BOTH of which are click-sequence metrics, not prose metrics. You are proposing to hand-author the exact signal the tripwire is calibrated against, then feed it back as ground truth for tuning that same tripwire. That is corpus circularity with a capability-leaked source. The "but a real naive Alex would structurally produce the same clicks" claim is unfalsifiable at n=2 authentic — you have no distribution to compare against, which is precisely why corpus growth is the gating bottleneck in the first place.

KEY CONCERN: The tripwire's two load-bearing thresholds (`min_steps_for_zero_confusion_flag: 6`, `submit_step_index_max_for_flawless_flag: 3`) are CLICK-SEQUENCE features, not prose features. If a human (Claude) authors the click sequence, the human's capability — knowing when to submit, knowing which step is "the answer step" — is encoded directly into the feature the tripwire measures. The prose layer is decorative; the poison is in the clicks. This is the ban, one layer up, with no detector — same as before.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: REJECT
REASONING: This proposal collapses two distinct phases (capability-probing and fixture-authoring) into a single Claude session, which is a known anti-pattern. The established convention in adversarial-fixture work — TruthfulQA (Lin, Hilton, Evans 2021), BIG-Bench Hard adversarial subsets, and Anthropic's own HH-RLHF red-team corpus (Ganguli et al. 2022) — explicitly separates the "human red-teamer who interacts with the system" from the "fixture that captures the failure mode," and crucially keeps the *target model lineage out of the authoring loop entirely*. The Playwright-MCP-then-write-prose pattern violates this: Claude has just observed `/tutor/`'s exact DOM, network envelopes, click timing, and screenshots immediately before writing prose meant to look like a naive `:free`-band student. That is textbook **evaluator-model contamination** in Bowman et al.'s "Measuring Progress on Scalable Oversight" framing (2022) and the **author-evaluator coupling** risk Perez et al. flagged in "Discovering Language Model Behaviors with Model-Written Evaluations" (2022) — where model-authored eval items systematically encoded the author-model's own priors and were ~15-20% easier for sibling models to pass. The "shapes-only" firewall is illusory: capability leakage in this corpus isn't *what URL was clicked*, it's *the texture of reasoning* — exactly the channel that gets contaminated when the same model that just parsed `rail_json.payload.path` then writes "naive" prose about it 30 seconds later. The HELM (Liang et al. 2022) and EleutherAI lm-eval-harness conventions both require fixture provenance be **temporally and procedurally separated** from any model-in-the-loop interaction with the target surface; the SWE-bench authoring protocol (Jimenez et al. 2023) goes further and requires human-only repo interaction during issue selection. The standard for adversarial-leaked negatives specifically — see ANLI's three-round human-and-model-loop protocol (Nie et al. 2020) — uses a *different* model family as the probe than the one being evaluated, never the evaluator's own lineage. Your council 1778839098 ruling already encoded this intuition ("Claude-CLI as Y persona is BANNED, no automated detector"); the Playwright-MCP variant is the same vector with a thinner disguise — the contamination surface is "Claude's working memory at prose-authoring time," not "whose API key signs the request."

KEY CONCERN: The proposal smuggles the banned pattern back in under a "shapes vs. prose" distinction that does not survive the contamination literature — Claude observing the live surface immediately before authoring the "naive" fixture is precisely what Perez et al. and Bowman et al. identified as the leakage channel. Standard practice: probe the surface in one session/tool/operator, hand the *artifacts* (screenshots, HAR files, DOM dumps) to a clean-context author (human-only, or a non-lineage model) who never saw the live system, and have them write the prose blind. If you want AI assistance in the authoring step, use a non-Anthropic model family (e.g. a local Llama or a Mistral `:free`) as the prose-drafter so the evaluator's lineage never touches the authoring context.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: REJECT
REASONING: The MCP-driven recording proposal is solving a problem you don't have. The tripwire input is a JSON transcript array — `[{action, target, payload, observation}, ...]` — already represented inline in `tools/surface-y-tripwire.test.mjs` as ~3-6 line literals. The "shapes" you'd capture from Playwright (clicks, DOM, network, timing) are not what the tripwire reads; it reads `action` strings and `observation` text. You already have 11 authentic-naive DRAFT-Y traces sitting in `docs/standin-findings/` showing the exact transcript schema, plus the existing `HYPER_COMPETENT` fixture in the test file showing the adversarial-leaked shape verbatim. Option (4) replay-shaping — fork an authentic-naive trace, delete the `ask_sidekick`/confusion observations, collapse to `submit` at step 2-3 — gets you n=3 in 15 minutes of Claude time, zero Alex time, and the fixtures are guaranteed schema-valid because they were born from real runs.

The MCP path adds: browser session setup, auth dance with `jarvis_auth`/`jarvis_session`/`csrf` cookies, network capture plumbing, then a *manual translation step* from captured shapes back into transcript JSON — and the captured shapes are descriptively irrelevant to what the tripwire actually evaluates. You're paying browser-automation complexity for fidelity in dimensions the consumer ignores.

YAGNI calculus on Alex-time: option (1) pure desk-authoring is ~20 min Claude session, 2 min Alex review per fixture. Option (4) replay-shaping is ~15 min Claude session, 2 min Alex review. Option (3) MCP-driven is multi-hour Claude session + non-zero Alex time when MCP hits auth/timing weirdness on the live VPS + the same 2 min review at the end. The MCP option is strictly dominated unless you ALSO need realistic DOM/timing capture for a downstream consumer — which the tripwire is not.

KEY CONCERN: Adversarial-leaked fixtures are TEST DATA for `surface-y-tripwire.test.mjs`, not artifacts that need shape-realism. They need to (a) match the transcript schema and (b) exhibit the "no confusion + no sidekick + early clean submit + zero findings" pattern. Both are trivially hand-authorable. Use option (4) — fork 3 of the existing DRAFT-Y traces, surgically remove confusion/sidekick events, collapse to early submit, drop them in as `ADVERSARIAL_LEAKED_1/2/3` constants alongside `HYPER_COMPETENT`. Ship today. Revisit MCP-recording only when you have a consumer that actually needs DOM/network fidelity (e.g. a UI regression harness), and even then prefer option (2) Alex-driven recording with a tiny `page.on('response')` logger — that's 30 min of Alex time ONCE and produces a reusable recorder, vs. MCP which produces a one-shot transcript per session.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: Strip the framing: the tripwire reads four integers (submit-step-index, confusion-event-count, sidekick-ask-count, findings-count). Those four integers are the entire signal surface. Everything else — Playwright runs, DOM captures, network shapes, hand-authored prose — is decorative wrapping around four numbers that a human can type in five seconds. The proposal spends massive tool-call budget driving a browser to capture shapes that the tripwire could not care less about (DOM, network, click sequences), then hand-authors prose that the tripwire never reads. That's not "shapes via MCP, prose by hand" — that's "irrelevant artifacts via MCP, irrelevant artifacts by hand." Option (c) is closer to right, but even mutation-synthesis is overkill: with n=8 target and four-integer signal space, the correct artifact is a hand-typed table of 8 rows × 4 columns where the author deliberately places each row on the leaked/naive side of each threshold. The prose envelope, if needed at all for downstream X-handoff readability, gets stubbed last and cheaply.

KEY CONCERN: The proposal confuses "the drill produces transcripts" with "fixtures must come from the drill." The tripwire is a four-integer classifier; fixtures are four-integer rows. Anything that doesn't directly set those four integers to known adversarial values is ceremony. Playwright MCP is the wrong tool not because it's online vs. offline, but because it answers a question (what does a real run look like in DOM/network detail?) that the tripwire never asks. Build the 4×n integer matrix by hand, label each row with which threshold it violates and why, wrap in minimal JSONL — done in one Edit call, not a browser session.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: REJECT
REASONING: This proposal violates the binding council 1778839098 decision through a definitional sleight-of-hand. Concerns (3) and (2) are both CRITICAL and neither is manageable with mitigations short of abandoning the approach.

**CRITICAL #1 — Capability leakage via shape-authoring (concern 3).** The firewall in council 1778839098 banned Claude-CLI on Y because there is "no automated detector for capability leakage in prose." The proposal narrows "prose" to mean "the English narration" and exempts everything upstream. This is wrong. A persona transcript's load-bearing signal is not the adjectives — it's the decision graph: *which* testid to click, *when* to invoke the sidekick, *whether* to re-read the problem before submitting, *how long* the user dwells, *what* they type into the answer box, *when* they give up. Every one of those decisions is the persona. Claude-driving-MCP is making 100% of those decisions. The prose written afterward is downstream stenography of capability-leaked behavior that Claude already injected at click time. The tripwire then trains against "Claude's notion of what an authentic-naive user does" — same poison, laundered through a browser. Blast radius: the entire calibration corpus for Surface Y becomes a mirror of Claude's own model of naivety, which is exactly what 1778839098 prohibited. Recovery requires invalidating every fixture authored this way and retuning the tripwire — costly but not catastrophic. Detection is the catastrophic part: there is no automated check that distinguishes "shape captured from real human" vs "shape captured from Claude-puppeting-browser."

**CRITICAL #2 — Production event-log pollution (concern 2).** The proposal hits live `POST /api/v1/drill/grade` without `X-Standin-Run: 1`. Every grade writes a `drill_grade` event with `is_synthetic: false` to the same event log Surface X reads for calibration. Surface X filters `status_in=["ok"]` but does not filter `is_synthetic` by default — meaning Claude-puppet events become real signal in X's calibration window. This is a cross-surface contamination: Y's fixture generation poisons X's calibration. Blast radius is bigger than the Y corpus. Recovery requires either backfilling `is_synthetic=true` on all MCP-origin events (requires identifying them post-hoc — only possible if session-id-tagged), or excluding the time window in X's queries (requires X to know which window was contaminated). Both are operationally painful and depend on perfect logging hygiene during the capture session.

Concerns (1) auth-burn and (5) recoverability are MEDIUM — manageable with named mitigations (single long-lived dev session, fixture quarantine flag). They do not gate the decision.

Concern (4) drift is downstream of #1 and resolves if #1 resolves.

**Why no CONDITIONAL.** A CONDITIONAL stance would require mitigations for both criticals. For #1, the only honest mitigation is "don't have Claude make the decisions" — which means either (a) record a real human session with screen-recorder + network-tap (then MCP is not the tool; passive capture is) or (b) hand-script the click sequence offline from the actual user's described behavior, then replay via MCP purely as a network-fetch harness. Both of those are different proposals. For #2, the only honest mitigation is to use the `X-Standin-Run: 1` synthetic-flag path or seed-tutor-events path — which the proposal explicitly does NOT use. Adding that flag is also a different proposal.

The current proposal as stated is a renaming of the banned approach. Reject and ask for a revised proposal that either uses passive human-session capture (no Claude in the driver's seat) OR uses the synthetic-event path with hand-scripted click sequences derived from the real user's described behavior — not from Claude's model of it.

KEY CONCERN: Shape-authoring IS persona-authoring. Moving Claude from "writes the prose" to "drives the browser that generates the shapes the prose narrates" does not remove Claude from the capability-decision loop — it just hides Claude's fingerprints one layer deeper, in a layer that has no automated leakage detector (same gap that motivated 1778839098). The firewall fails by design.

---

## Sanity Check

SANITY Devil's Advocate: PASS
NOTE: clean. Identifies tripwire's load-bearing features as click-sequence features, supported by named thresholds in the codebase.

SANITY Domain Expert: PASS
NOTE: clean. Cites concrete patterns (TruthfulQA, BIG-Bench Hard, HH-RLHF, Perez et al., Bowman et al., HELM, lm-eval-harness, SWE-bench, ANLI) — exactly the specificity the persona requires.

SANITY Pragmatist: PASS
NOTE: clean. Verified existing infra (DRAFT-Y count, HYPER_COMPETENT fixture, tripwire signature) before ruling. Compared 4 named alternatives.

SANITY First Principles: PASS
NOTE: clean. Strips framing to "tripwire reads 4 integers" — directly aligned with what `tools/surface-y-tripwire.mjs` actually evaluates. Strong but not overgeneralized.

SANITY Risk Analyst: PASS
NOTE: clean. Two CRITICAL risks ranked with named blast radius + recovery analysis. Honest refusal of CONDITIONAL with reasoning.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: WRONG APPROACH

CORE FINDING:
The proposal is binding council 1778839098 with a Playwright disguise. The tripwire's load-bearing features (submit-step-index, confusion-event-count, sidekick-ask-count, findings-count) are CLICK-SEQUENCE features — not prose features — so "MCP captures shapes, Claude writes prose" leaves Claude in control of exactly the signal the tripwire measures. Additionally the proposal pollutes the production event log with `is_synthetic: false` Claude-puppet events that Surface X would consume as real calibration signal, creating cross-surface contamination beyond Y's corpus.

AGENT CONSENSUS: 5 REJECT, 0 CONDITIONAL, 0 APPROVE — 0 agents flagged.

KEY ISSUES:
- **Click-sequence IS persona.** The four integers the tripwire reads are decisions Claude would be making at MCP-driver time. Prose-authoring being "human-done" is moot when the upstream click decisions are the poisoned signal.
- **Cross-surface contamination via live event log.** Without `X-Standin-Run: 1`, MCP-driven grades land in the same `drill_grade` stream Surface X reads. Recovery requires session-id tagging hygiene that the proposal does not specify.
- **Tripwire input is JSON transcript arrays, not browser sessions.** Existing test file already has `HYPER_COMPETENT` as a 3-line inline literal. The signal surface is hand-typable in under 5 minutes per fixture.

RECOMMENDED PATH:
**Replay-shape from existing authentic-naive DRAFT-Y traces.** Concretely: pick 3 of the 11 existing `DRAFT-Y-*.md` files in `docs/standin-findings/`. For each, surgically delete `ask_sidekick` actions and `observation: "confused"`-style events, collapse the step sequence so `submit` lands at index ≤ 3, set the trace's derived `findings_count` to 0. Drop the resulting 3 transcript arrays into `tools/surface-y-tripwire.test.mjs` as `ADVERSARIAL_LEAKED_1/2/3` constants, mirroring the existing `HYPER_COMPETENT` fixture's shape. Write 3 tests asserting `flagSuspectRun(fixture, 0).suspect === true`. Annotate each fixture inline with the threshold it violates ("Path A — zero ask_sidekick + zero confusion across ≥6 steps" or "Path B — submit at step ≤3 with no prior friction"). Ship as one commit; zero browser sessions; zero live VPS traffic.

This is the Pragmatist's option (4) consolidated with First Principles' 4-integer-classifier framing. It is faithful to the binding council 1778839098 (Claude never drives Y's surface) and satisfies the durable rule (the fixture content is a hand-typed JSON literal, not an MCP-recorded artifact).

CONFIDENCE: 9
Convergent REJECT from 5 clean agents along distinct axes (capability vector, contamination literature, YAGNI, signal-surface, blast radius). The one rating point withheld: the existing authentic-naive corpus at n=2 is itself small enough that even the replay-shape recommendation rests on a thin base distribution. Growing authentic-naive to n≥5 via more `:free`-band Y runs remains independently needed; that work is unaffected by this verdict.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[COUNCIL CONTEXT SAVED]
Domain: jarvis-kotlin tutor — Surface Y adversarial-leaked corpus growth for tripwire calibration
Stack: Node tools (mjs), Playwright, OpenRouter :free, Kotlin backend, Vite frontend
Constraints: single-user (Alex), Surface Y on :free band only, build-everything mode, no paid LLM API spend (CLI subprocess permitted)
Prior decisions:
- council 1778787266 — Y is friction-discovery, drill-completion demoted
- council 1778839098 (BINDING) — Claude-CLI on Y persona BANNED; fixture-poisoning vector, no detector
- council 1778881175 — Y tripwire AND-gates on findings_count==0
- council 1778881174 — DrillGrader Phase A hardening (response_format=json_object, maxTokens 1200, balanced-brace fallback)
- council 1778960917 (THIS) — Playwright MCP for Y fixture shape-capture REJECTED; replay-shape from existing authentic-naive DRAFT-Y traces instead
