# Council review — 1778693954

**Problem:** Naivety-enforcement strategy for a "student stand-in" LLM-driven agent on jarvis-tutor. Agent uses the live site via Playwright DOM as a fresh-eyes beginner with NO prior knowledge of courses/tasks, learns as it goes, produces feedback (pedagogy / UX / regression, mode-parametrized). Naivety must be enforced because the underlying LLM has subject knowledge baked in.

**Proposed approach:** Pick among (A) persona prompt + page-only input feed, (B) bounded per-session knowledge ledger, (C) deliberately weaker `:free` model, (D) combine A+B+C.

**Project context:**
- Domain: jarvis-tutor — Romanian uni-student web tutor for Alex (single user, FII Iași AI bachelor, finals Jun 1-21 2026, PS HW due 2026-05-21).
- Stack: Kotlin/Ktor backend, React frontend, Playwright dogfood gates, OpenRouter `:free` LLM chain (Qwen / Llama / GPT-OSS).
- Constraints: NO paid APIs; recurring `:free` quota cascades (overnight refill); single VPS 7.8GB RAM; single user; mobile first-class.
- Prior decisions: LLM-as-judge drill grader shipped; pedagogy guardrails shipped (predict-before-attempt, chip-scoping, drill-self-paste guard); sidekick prefetch scoped to `_extras/$subject/`.

**Timestamp:** 2026-05-13T17:39:14Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: All four approaches share a fatal premise: that an LLM-driven Playwright bot constitutes a credible proxy for Alex's confusion. It does not. Alex's friction is metacognitive — eyes drifting, re-reading the same paragraph, abandoning a drill because the chair is uncomfortable, panic-scrolling to sidekick because finals are in 19 days. A stand-in driven by a stateless `:free`-tier completion will produce *grammatically plausible confusion* that perfectly mimics what an LLM thinks a confused student looks like — which is exactly what your pedagogy guardrails were already trained against by the same model family. You will be grading the site using the same statistical distribution you tuned it to please. Worse, the feedback artifact will *feel* rigorous (transcripts! ledger entries! friction traces!) and you will trust it, replacing the one signal that matters — your own daily dogfood as Alex — with a synthetic confidence-laundering loop. Persona leakage (A), ledger gaming (B), weak-model incoherence (C), and combinatorial debugging hell (D) are downstream symptoms; the disease is "automating the only honest QA signal you have."
KEY CONCERN: The stand-in's failure mode is not detectable from inside the stand-in. A naive student who is secretly fluent in probability will write critique that reads like a naive student's critique — and you have no oracle to distinguish "real UX friction" from "LLM performing friction." You are building a measurement instrument whose calibration error is invisible to itself, on a deadline where the actual measurement (Alex using the site) is free and already happening.
PICKED: NONE-RETHINK

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The class of problem here is well-established: LLM-as-synthetic-user for usability testing. Real examples include Synthetic Users (syntheticusers.com), UserTesting's AI personas, and Maze AI — all of which converged on the persona-prompt approach (A) but found that "prior knowledge leakage" is the killer failure mode, exactly as you've identified. The academic standard is MathDial (Macina et al., EMNLP 2023) and Bridge (Wang et al., 2024) where LLM-as-student is grounded in EXPLICIT confusion-state ledgers per turn — this is approach (B), and it's load-bearing in their evals because Karpinska & Iyyer (2023, "Large Language Models Effectively Leverage Document-level Context") showed naive persona prompts fail the moment the underlying model recognizes domain vocabulary. Anthropic's persona-based red-teaming work (Perez et al., 2022 "Discovering Language Model Behaviors with Model-Written Evaluations") explicitly notes that persona alone produces "shallow compliance" — the model performs naivety in tone but reasons from full knowledge. Approach (C) is what CHI 2024's "Simulating Students with LLMs" paper (Markel et al.) tried and rejected: weaker models produce noisier signal but still leak knowledge, AND introduce confounds (you can't tell if the bug is tutor-side or stand-in-side). The honest answer from practice: (D) is the right composition, but (B) is the load-bearing piece — (A) and (C) without (B) have published failure modes.
KEY CONCERN: The ledger in (B) must be enforced by a SEPARATE verifier LLM call ("did this response use only facts in the ledger?") — self-policing personas don't work. This is the Constitutional AI pattern (Bai et al., 2022) applied to ignorance instead of harmlessness. Skip the verifier and you've built (A) with extra steps.
PICKED: D

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: Approach A is 80% of the value at 10% of the cost — a persona prompt plus DOM-only feed reuses your existing Playwright gates (`slice1-5-playwright-gate.mjs`, `slice2-playwright-gate.mjs`), adds maybe 200 LOC, and ships in a single evening. With PS HW due in 8 days and finals 9 days out, every extra hour spent on (B)'s ledger machinery or (D)'s triple-stack is an hour stolen from PS revision and the parallel slices already in flight. (B) sounds defensible but in 3 months it becomes the thing that breaks when you rename a route — you'll be debugging the ledger instead of using the tool. (C) is a trap: a weaker `:free` model produces incoherent feedback you can't act on, so the "real ignorance" is worse than simulated ignorance. Ship A now, only add (B)'s ledger if A actually drifts into knowledge-leakage on the first 3 dogfood runs — evidence-driven, not speculative.
KEY CONCERN: Time arbitrage — 8 days to PS HW + parallel slices in flight means (B/D) cost is paid in PS exam score, not just LOC.
PICKED: A

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The framing is solving the wrong problem. "Simulated naivety" is not what Alex actually needs — what he needs is **signal about whether the tutor site teaches**. A faux-naive LLM cannot give that signal because (a) the underlying model's priors leak no matter how hard you prompt-jail it, and (b) even if naivety were perfectly enforced, the agent's failure modes (token-level pattern matching, hallucinated confidence, no working memory across sessions, no cognitive load, no boredom, no emotional state) are categorically different from a real beginner's failure modes. You'd be measuring "can an LLM with amnesia navigate the UI" — which is mostly a UX-affordance test, not a pedagogy test. The four candidates A/B/C/D all double down on the wrong abstraction. From scratch, I'd build a **trace-replay harness + invariant checker**: capture real Alex sessions (already happening via dogfood gates), define falsifiable pedagogy invariants (e.g., "after a wrong answer, the next prompt must reference the specific error", "drill difficulty must increase only after 2 consecutive correct", "sidekick must never reveal the answer before 2 hint cycles"), and have an LLM act as a **rubric-grader over recorded traces**, not as a fake student. Naivety isn't enforced — it's irrelevant, because the LLM is judging the tutor's behavior, not pretending to be confused by it. For UX regression specifically, Playwright + visual-diff already does this better than any LLM agent. The only thing a stand-in adds beyond a rubric-grader is "discovering unknown unknowns" — and for that, the right tool is a fuzzer (random valid-input agent that just tries to break things), not a fake beginner.
KEY CONCERN: The whole project is downstream of a category error — Alex wants to know "is my site teaching?" and the proposed answer is "let's build a fake learner," when the direct answer is "define what teaching means, then check the traces against it." Building the stand-in burns weeks; the rubric-grader ships in a weekend and gives sharper signal.
PICKED: E:Trace-replay rubric-grader (LLM judges recorded Alex sessions against falsifiable pedagogy invariants) + Playwright fuzzer for UX regression — skip the fake-student abstraction entirely.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Top risk is **quota cascade collapse** — a Playwright-driven LLM stand-in firing one prompt per DOM step at 30+ steps/session, layered on top of the already-stressed drill grader + sidekick chain, will burn the OpenRouter `:free` daily quota mid-session. When it does, the grader and sidekick start returning 429s or empty completions during Alex's actual finals study — the stand-in agent literally breaks the surface it was built to test, and Alex gets ghost regressions ("drill grader broken!") that are really just rate-limit fallout. Approach (D) is worst here because it stacks persona-call + ledger-update-call + grade-self-call per step. Second risk is **fake-bug pollution**: a weak `:free` model (C) or jailbroken persona (A) writes confident-sounding findings like "the sidekick gave wrong answer on Laplace transform Q3" — Alex reads the markdown pre-finals, spends 40 minutes re-checking a sidekick path that was actually correct, and loses trust in the dogfood signal. The ledger (B) drifts by step 15 because Playwright DOM snapshots aren't deterministic (drawer animations, async paints) so "seen" entries get logged for content the agent never actually rendered, producing naivety violations that look like real student confusion. Combining (D) compounds: persona leak → ledger logs leaked fact as "seen" → weak grader rubber-stamps it → findings.md asserts Alex's site taught something it didn't.
KEY CONCERN: Quota cascade — stand-in agent's per-step LLM calls starve the live drill grader + sidekick mid-Alex-session, breaking the production surface during finals prep. Hard cap stand-in to a separate OpenRouter key OR an offline-only nightly window when Alex isn't using the site; never share the live quota pool. Plus findings.md must be write-only to a quarantined path (`docs/standin-findings/DRAFT-*.md`), zero auto-commit, human-review gate before any of it influences design decisions.
PICKED: D-with-conditions (separate quota key + offline window + quarantined findings path + ledger backed by server-side event log, not DOM scrape)

---

## Sanity Check

SANITY Devil's Advocate: PASS
NOTE: Clean — concrete, committed REJECT with a sharp single argument (calibration-invisible measurement instrument). No circular reasoning, no strawman, on-persona.

SANITY Domain Expert: PASS
NOTE: Cites real papers/products by name (MathDial Macina EMNLP 2023, Perez 2022 Discovering LM Behaviors, Bai 2022 Constitutional AI, Markel CHI 2024). Karpinska & Iyyer 2023 application is slightly loose but defensible. Verifier-LLM recommendation is concrete + actionable.

SANITY Pragmatist: PASS
NOTE: Clean. Time-cost reasoning is grounded in concrete deadlines (PS HW 8 days, finals 9 days). Evidence-driven escalation rule is on-persona.

SANITY First Principles: PASS
NOTE: Actively rejects framing as instructed. Names a concrete alternative (trace-replay rubric-grader + invariant checker + fuzzer). Argues from first principles about LLM failure modes vs human failure modes. On-persona.

SANITY Risk Analyst: PASS
NOTE: Two concrete top risks, each with concrete failure modes + concrete mitigations. Quota-cascade analysis is grounded in the project's documented `:free` chain constraint. Doesn't stray into other agents' territory.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The naivety-enforcement question is the wrong first question. The council surfaces three converging signals: (1) the documented LLM-as-naive-student failure mode is **knowledge leakage that cannot be detected from inside the agent** (Devil's Advocate + Milička hyper-accuracy evidence cited by the parallel researcher); (2) persona-only (A) is published-broken on strong models, ledger-only (B) is published-broken without an external verifier, weak-model-only (C) introduces confounds, and combine-all (D) compounds LLM-call cost on a quota-constrained surface that Alex's live study depends on; (3) the alternative framing — judge recorded traces against pedagogy invariants, plus a UX-focused fuzzer — gives sharper signal at lower risk. The student-stand-in concept is NOT WRONG-APPROACH outright (FP's verdict is a near-call), but the four proposed candidates are not safe to ship as-is. The fix is to **reshape the design** before choosing a naivety knob.

AGENT CONSENSUS: 2 REJECT (Devil's Advocate, First Principles), 3 CONDITIONAL (Domain Expert → D, Pragmatist → A, Risk Analyst → D-with-conditions). 0 clean APPROVE. 0 flagged.

KEY ISSUES:
1. **Quota cascade collapse (CRITICAL — Risk Analyst).** Stand-in fires 30+ LLM calls per session on the SAME `:free` chain that Alex's drill grader and sidekick already share. Mid-session burn → Alex sees 429s in his actual finals study → ghost regressions → trust collapse in the whole dogfood signal. Must be isolated: separate quota key OR offline-only window; no shared pool with live study surface.
2. **Persona-only naivety is published-broken (HIGH — Domain Expert + researcher's Milička 2024 + Perez 2022).** Strong models exhibit "hyper-accuracy" leakage; persona produces "shallow compliance" in tone while reasoning from full knowledge. Without an external verifier (Constitutional-AI-style applied to ignorance), (A) is shipping a known-broken pattern.
3. **The user may be measuring the wrong thing (HIGH — First Principles + Devil's Advocate).** A faux-naive LLM cannot model Alex's metacognitive friction. Trace-replay against falsifiable pedagogy invariants gives sharper signal at lower cost. The stand-in should be ONE of several signals, not THE pedagogy oracle.
4. **Findings pollution risk (HIGH — Risk Analyst).** Fake bugs in `findings.md` consume Alex's finite pre-finals attention. Quarantined drafts path + human-review gate is non-negotiable.

RECOMMENDED PATH:
Do NOT ship any of (A), (B), (C), (D) as the single naivety strategy. Reshape the design as follows:

1. **Split the work into two complementary surfaces, not one.**
   - **Surface X — Trace-replay rubric-grader** (First Principles' E). Define 5–10 falsifiable pedagogy invariants. LLM grades recorded Alex sessions + dogfood-gate runs against them. Naivety is irrelevant here — the LLM is a judge, not a learner. Ships fastest, gives the cleanest signal, zero leakage failure mode.
   - **Surface Y — Student stand-in (the user's original concept)** — still build it, but as a SECONDARY signal scoped to "discover unknown unknowns" (UX dead-ends, broken affordances on a fresh task) rather than as a pedagogy oracle.

2. **For Surface Y's naivety, pick (D) combine — but constrained:**
   - **Symbolic-schema gate (MathVC pattern from researcher)** instead of bare ledger. Each PDF/drill carries an explicit concept-schema; a controller filters LLM output that references concepts absent from the schema and forces regeneration. This is the verifier the Domain Expert called for, materialized as a hard gate instead of a self-policing prompt.
   - **Weaker free-tier model** (cost-aligned + Milička evidence that smaller models are MORE faithful to novice personas because they have less expertise to suppress).
   - **Confusion-tuple primitive** (from `Generative Students` via researcher): encode `confused_between(A, B)` pairs so wrong answers are systematic misconceptions rather than random noise.

3. **Non-negotiable mitigations from Risk Analyst** (these gate ANY ship of Surface Y):
   - **Separate OpenRouter API key** dedicated to stand-in, OR offline-only nightly window (e.g. 03:00–06:00 local) when Alex isn't using the live site. Never share the live quota pool.
   - **Quarantined findings path** (`docs/standin-findings/DRAFT-*.md`), write-only, zero auto-commit, explicit human-review gate before any finding influences design.
   - **Hard cap on per-session LLM calls** (e.g. ≤ 50). Surfaces cascade risk loudly if it spikes.
   - **Ledger backed by server-side event log** (route hits + drill submits + sidekick asks), not by DOM scrape — sidesteps drawer/animation/async-paint drift.

4. **Sequence:** Ship Surface X first (1 evening, zero leakage risk, immediate signal). Use its findings to inform Surface Y's invariant catalog before building it. Surface Y stays a Slice 2 effort with the council's mitigations baked into the spec.

CONFIDENCE: 8
[Would climb to 9 if the user confirms (a) acceptance of the two-surface split, (b) willingness to provision a separate OpenRouter key or commit to the offline-window approach, and (c) authoring of an initial pedagogy-invariant catalog before building either surface. Would drop to 6 if the user insists on shipping (A) alone as a single quick win — that path has named published failure modes.]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1778693954-student-standin-naivety.md
