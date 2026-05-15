# Council review — 1778839098

**Problem:** Post-Claude-CLI-provider decision, which model band does Surface Y's persona run on?

**Proposed approach (the menu):**
- (a) Y persona defaults to Claude-CLI + behavioral-competence tripwire as sole leakage gate
- (b) Y persona stays on `:free` (gpt-oss-120b); Claude-CLI provider built but powers OTHER tools (X-trace-grader upgrade, Z-visual-critic upgrade, sub-personas)
- (c) Y on curated mid-tier `:free` (likely gpt-oss-120b); tripwire as belt-and-suspenders

**Project context:**
- Domain: jarvis-kotlin tutor + Surface X/Y/Z eval suite. Single-user (Alex). FII Iași AI bachelor's, finals Jun 1-21 2026.
- Stack: Kotlin backend (VPS, not in this repo), React/TS frontend, Node ESM tooling, Playwright, OpenRouter `:free`, Claude CLI v2.1.141 (subscription pool — SHARED with Alex's coding sessions).
- Constraints: single-maintainer. `:free` OR claude-max-via-CLI. Lands on `main`.
- Prior decisions:
  - council-1778785261: WRONG APPROACH on `:free` model-roulette. Decouple X-fixture seeding (deterministic non-LLM) from Y friction-discovery (stochastic). Seeder shipped: `tools/seed-tutor-events.mjs` (Spec A v2).
  - council-1778787266: WRONG APPROACH on (a)/(b)/(c) "what should Y optimize for". Y is friction-discovery ONLY; drill-completion DEMOTED to incidental; behavioral-competence tripwire mandatory. ONE open sub-question handed to spec: which model band for Y?
  - Spec line 786: naivety-leakage mitigations = MathVC schema-gate + confusion-tuples + weak `:free` model (the leg deleted by CLI swap).

**Timestamp:** 2026-05-15T09:58:18Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT

REASONING: The framing is rotten — all three options treat "which model band" as the load-bearing question, but the actual load-bearing artifact is the behavioral-competence tripwire itself, and the menu hand-waves it into existence as if it's a one-liner. It isn't. A tripwire that flags "zero-confusion / first-try-flawless / zero-`ask_sidekick`" runs has to be calibrated against ground truth — and Alex has exactly three runs to calibrate from, two of which (deepseek incoherent, minimax killed by config bug) carry zero signal on what authentic naive friction actually looks like. You will tune the tripwire on n=1 (gpt-oss-120b), then claim it generalizes. That is overfitting masquerading as a mitigation, and it sits on the critical path for every option in the menu — including (b), because the spec line 786 risk table already DEMOTED the weak-`:free`-model leg to "incidental" once the CLI provider exists; the tripwire is what's actually doing the work in all three branches, and nobody has shown it can do that work. Worse: option (b)'s claimed virtue ("restores the lost mitigation leg") is a lie of convenience — gpt-oss-120b on a controller-resolved `submit` macro is no longer the same weak-`:free` model that originally earned that mitigation credit; the controller deleted the very capability floor that made the leg load-bearing. You're citing a mitigation that the seeder rewrite already neutered.

KEY CONCERN: The behavioral-competence tripwire is treated as a settled primitive across all three options, but it has never been calibrated against more than one authentic-naive run (gpt-oss-120b), and the controller-resolved `submit` macro has already eroded the "weak `:free` model" mitigation that (b) claims to restore. Pick a model band only AFTER you've defined: (i) the tripwire's concrete thresholds, (ii) the calibration corpus that justifies them, and (iii) what behavior in a SINGLE run trips it vs. what requires N-run aggregation — otherwise you're shipping a leakage gate that fails open and won't know it until finals week.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL — pick (b), with (c) as the explicit fallback if gpt-oss-120b:free degrades

REASONING: The literature is unambiguous on this class of problem. Milička et al. 2024 (PLoS One, "Large language models are able to downplay their cognitive abilities to fit the persona they simulate") empirically demonstrated that frontier models — Claude included — exhibit hyper-accuracy leakage when prompted to simulate weaker users, and persona-prompting alone does NOT close the gap. This is the canonical "persona-prompting ceiling" anti-pattern: you cannot prompt a Claude-tier model into being a naive Romanian undergrad with Math Analysis gaps. Sierra's TAU-bench (Yao et al. 2024) solves the adjacent problem (LLM-as-user) by pairing a scripted skeleton with LLM judgment for terminal actions — exactly what your `submit`-as-controller-resolved-macro now provides. With that wall demolished, you no longer need the model to be capable of terminal action selection; you need it to be incapable of NOT looking like a student, which is the opposite axis. Option (a) leans on a single tripwire to detect leakage that Milička shows is statistically pervasive across hundreds of turns — discount-usability-testing wisdom (Nielsen, 5-tester heuristic) says detection-after-the-fact is strictly worse than substrate-level prevention. Option (b) preserves the substrate-level mitigation (weak model) that your own Spec line 786 risk table treats as load-bearing alongside MathVC's schema-gate and Generative Students' confusion-tuples — a defense-in-depth stack the council already ratified.

KEY CONCERN: The "decouple seeder from persona" lesson (council-1778785261) is the same lesson in a different costume — deterministic concerns get deterministic tools, stochastic concerns get stochastic tools, and you do NOT promote a tool across that boundary because it's "available." Claude-CLI is now available; that is not a reason to put it on the friction-discovery leg. Spend Claude-CLI on X-trace-grader and Z-visual-critic where judgment quality is the bottleneck and naivety-simulation is not required. Locking (b) to gpt-oss-120b:free specifically (not generic `:free` roulette — that was the failure mode the first council rejected) is the operational form of this answer; if gpt-oss-120b:free deprecates or degrades, fall to (c) with an explicit next-pick from the same competence band, never up-band to Claude.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL — pick (b)
REASONING: Option (a) costs you a new `claude-cli.mjs` adapter (subprocess piping, stdout parsing, error/timeout handling, token accounting that Claude CLI doesn't natively emit the same way OpenRouter does, plus a behavioral tripwire) — call it 200-400 LOC of new surface area plus a permanent debugging tax every time `claude --print` changes its output shape across CLI versions. Worse, it pipes 50-150 calls/run into the SAME subscription pool Alex codes finals-study from; one Y misfire during a 10-min cap = throttled coding session = real human cost on a finals deadline. Option (b) is the ~1-line change at `surface-y.mjs:66` plus the tripwire you're building anyway, with full quota isolation via `OPENROUTER_API_KEY_STANDIN` (already restored + live-verified) — that's literally 95% of the value at 5% of the cost. Option (c) is (b) wearing a tie; "curated mid-tier" without a concrete second model on the shortlist is just (b) plus a doc you'll never re-read. Build the Claude-CLI adapter LATER for X/Z where call volume is 1-5/run and the subscription-pool blast radius is bounded.
KEY CONCERN: Burning Alex's coding subscription pool to discover friction in a tutor UI is a cost-benefit inversion — the tool eats the user's primary work surface to evaluate a secondary one.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: Strip the framing: Y's irreducible job is to be a fresh-eyes user who fails where Alex can't see failure. From scratch, the question isn't "which model band?" — it's "what is the minimum capability profile that fails like a beginner without failing in ways no beginner would fail?" That's a band with a ceiling AND a floor: too weak (deepseek) produces incoherent noise that masquerades as friction but isn't; too strong (Claude-CLI-default) produces competent navigation that masks real friction with model-prior compensation. The menu collapses a 2D problem (floor + ceiling) onto a 1D axis (which tier?) and option (a) silently violates the ceiling. Worse: the framing treats "capability floor lifted by controller macro" as license to climb arbitrarily high — but lifting the terminal-action floor doesn't lift the cognitive-naivety ceiling, which is the actual binding constraint for Y's signal.
KEY CONCERN: A capable model with a tripwire is not equivalent to a naive model — the tripwire detects gross competence leakage (knows the answer cold) but cannot detect the subtler failure mode where Claude-CLI's prior smooths over genuinely confusing UI by inferring intent the way no real beginner would. Y's value IS the friction that a smart model would route around. Pick the weakest model that clears the floor (gpt-oss-120b, i.e. option b) — climbing higher destroys the signal Y exists to produce, regardless of tripwire coverage.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: REJECT
REASONING: **CRITICAL: Option (a) inverts the failure mode from "Y misses friction" to "Y poisons the entire ledger."** Claude CLI's behavioral hyper-competence isn't just a leakage risk — it's a fixture-corruption risk. Schema-gate (`surface-y-gate.mjs`) is a substring scan over CONCEPT aliases; it cannot fire when the failure is "zero hesitation, zero `ask_sidekick`, first-try-flawless R." Behavioral tripwire (zero-confusion + first-try-flawless + zero-sidekick) sounds protective but is itself a heuristic on three signals against a model that is structurally optimized to produce all three — false-negative rate on a single-maintainer single-user system with no ground-truth oracle is unbounded. Worst case: Y reports "smooth!" for 3 consecutive runs, Alex ships UI changes on that signal, real-Alex hits the same UI June 5 mid-finals and the friction is invisible because the fixture said it wasn't there. **HIGH: Option (a) also collides with subscription pool during Jun 1-21.** 150 calls/run on the shared subscription pool during finals = silent throttling of Alex's own coding sessions at the exact moment subscription cost is most real. The constraint section names this explicitly. **MEDIUM: Option (b) `:free` daily-quota cascade is real but recoverable** — Y dies mid-run, partial draft, you re-run tomorrow; no fixture poisoning, no finals-session throttle. Option (c) curated mid-tier `:free` + tripwire dominates (a) on both critical risks and dominates (b) on phantom-friction (deepseek-incoherent failure mode) — tripwire becomes belt-and-suspenders on a model that's already weak enough to leak naivety, not a sole defense against a model engineered to hide it.
KEY CONCERN: Claude CLI on Y is a **fixture-poisoning vector with no detector** — the only behavioral-leakage signal is a 3-flag heuristic running against a model trained to suppress all 3 flags, on a single-user system where Alex IS the ground truth and won't notice the corruption until he hits the un-surfaced friction during finals.

---

## Sanity Check

SANITY Devil's Advocate: PASS
NOTE: clean. Stayed in REJECT lane. Concrete claims tool-verifiable (spec line 786 weak-model demotion language; n=1 calibration corpus from 3 runs minus 2 invalid). New angle — the tripwire itself is critical-path across all options, never previously surfaced — is in-scope (finding the weakest assumption) and not a strawman.

SANITY Domain Expert: PASS
NOTE: clean. Named real patterns specifically (Milička 2024 PLoS One, Yao 2024 TAU-bench, Nielsen 5-tester, MathVC, Generative Students). CONDITIONAL has explicit condition + explicit graceful-degrade path (b → c, never to Claude). Concrete comparison to the council-1778785261 "decouple seeder from persona" rule.

SANITY Pragmatist: PASS
NOTE: clean. Concrete LOC estimate (200-400) and ~1-line counter-cost. CONDITIONAL with named pick (b). Stayed in cost/maintenance lane. Explicit "build Claude-CLI for X/Z later" relocation of the provider scope. The subscription-pool-during-finals point is in-lane (human cost), not a Risk Analyst overreach.

SANITY First Principles: PASS
NOTE: clean. Stripped the menu to the actual 2D problem (floor + ceiling) and rejected the 1D collapse — in-lane for the role. From-scratch answer (weakest clearing floor) compared to proposal. Did not just rephrase the user's framing.

SANITY Risk Analyst: PASS
NOTE: clean. Ranked CRITICAL/HIGH/MEDIUM. Top risk (fixture poisoning) named with concrete failure scenario (3 green runs → ship → finals friction). Pool-collision risk tool-verified (constraint section explicitly names finals throttling). Caught the false-negative-rate-unbounded property of the tripwire heuristic.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: WRONG APPROACH

CORE FINDING:
The (a)/(b)/(c) menu is the wrong shape — it collapses a 2D problem (competence floor + competence ceiling) onto a 1D axis (tier). Unanimous: (a) is REJECTED — 5 of 5 agents flag behavioral hyper-competence leakage with no detector capable of catching it (schema-gate is concept-only; the proposed behavioral tripwire is a 3-flag heuristic against a model engineered to suppress all 3 — false-negative rate unbounded). 4 of 5 converge on (b) as the operational answer with (c) as the graceful-degrade path. Devil's Advocate alone raises the deeper sequencing issue: the tripwire is critical-path across ALL options, and it has never been calibrated — it must be spec'd (thresholds + corpus + single-run-vs-N-run policy) BEFORE shipping Y on any model.

AGENT CONSENSUS: 3 REJECT, 2 CONDITIONAL — 0 flagged. Unanimous on rejecting (a). 4 of 5 land on (b)+(c) hybrid; DA dissents on sequencing (tripwire-first) but does not endorse (a).

KEY ISSUES:
- **Behavioral hyper-competence leakage is unmitigated under (a).** Risk Analyst ranks this CRITICAL — fixture-poisoning vector with no detector. Domain Expert grounds it in Milička 2024: persona-prompting alone cannot close the gap on frontier models. First Principles strips it to "lifting the terminal-action floor doesn't lift the cognitive-naivety ceiling."
- **Subscription-pool collision during finals (Jun 1-21) is concrete, not theoretical.** Pragmatist + Risk Analyst independently flag it. 150 Y-calls/run on the shared pool = silent throttling of Alex's actual coding work at the exact moment that cost matters most. (a) and (c) both incur this if anything ever runs Claude on Y; (b) escapes it entirely via `OPENROUTER_API_KEY_STANDIN` quota isolation.
- **The behavioral tripwire is undefined.** Devil's Advocate's critique is load-bearing: thresholds, calibration corpus, and aggregation policy (single-run vs. N-run) are not specified anywhere. Spec B must define them as a precondition section, not punt them to "tune during execution."
- **Claude-CLI provider scope shifts.** All 5 agents agree the CLI provider should still be built — but for X-trace-grader, Z-visual-critic, sub-personas, and other capability-wanted tools — NOT for Y's persona. This relocates the provider's primary user; Spec B must reflect that.

RECOMMENDED PATH (replaces the menu):
1. **Y persona stays on `:free`. Pin gpt-oss-120b:free as primary** (it already showed authentic naive-student behavior across all prior runs and surfaced friction). NO generic `:free` roulette — that was the failure mode council-1778785261 rejected.
2. **Curated `:free` fallback list as graceful degrade** — if gpt-oss-120b:free deprecates / 429s / regresses, fall to a named next-pick in the same competence band (e.g. mid-tier `:free` candidates). NEVER up-band to Claude-CLI. Document the fallback list in Spec B.
3. **Define the behavioral tripwire BEFORE shipping** — in Spec B's design doc, an explicit precondition section: (i) concrete thresholds for zero-`ask_sidekick`, first-try-flawless-code, zero-confusion-step signals; (ii) calibration corpus (the 1-2 valid prior runs + future runs explicitly counted); (iii) single-run-vs-N-run aggregation policy. Ship the tripwire as a transcript-postprocessor, NOT as a live gate (it's diagnostic, not blocking).
4. **Claude-CLI provider's scope is X-trace-grader + Z-visual-critic + sub-personas** (where competence is wanted), NOT Surface Y. Build it as `tools/lib/claude-cli.mjs` mirroring the `openrouter.mjs` API. Y's `surface-y.mjs:66` default model stays a `:free` pin.
5. **Subscription-pool budget convention**: any CLI-using tool gets a hard token/duration cap per invocation, and never runs on the same flow that's actively driving Alex's coding session (e.g. don't run X+Z+seeder+Y back-to-back; sequence them with a quota-aware gate).

CONFIDENCE: 9
What would change it: the tripwire-design problem (DA's critique) is genuinely unsolved at brief time. The recommended path defers thresholds + corpus to Spec B's design section — if Spec B can't produce concrete thresholds, the council's confidence falls to ~6 and the conclusion becomes "ship Y without a behavioral tripwire and treat the spec line 786 weak-`:free`-model leg as the sole behavioral-leakage mitigation," which is still consistent with (b) but loses the new defense layer.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
