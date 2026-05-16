# Council review — 1778881175

**Problem:** Surface Y persona must be authentically-naive (real-student-tier weak `:free` model). 3-leg defense in place: (L1) `:free` band lock + (L2) persona prompt + (L3) heuristic post-hoc tripwire `tools/surface-y-tripwire.mjs` with 2 magic thresholds (≥6 steps + zero confusion → suspect; submit at step ≤3 + flawless → suspect). Calibration corpus n=2 authentic-naive runs. User asks: "is there a way to almost guarantee results? prompt engineering or similar?"

**Proposed alternatives:**
- (a) Strengthen persona prompt — few-shot authentic-naive transcripts + failure-mode instructions
- (b) Replace heuristic tripwire with LLM-judge tripwire — verdict on transcript naivety
- (c) Schema-constrained generation — force `confusion_signal` field per step
- (d) Status quo + stack (a) over time
- (e) Other

**Project context:**
- Jarvis personal tutor app, single-user (Alex)
- Stack: Node tools at `tools/`, OpenRouter `:free`-band LLM
- Constraints: NO paid LLM API spend; `:free` band OR Claude-CLI subprocess
- Prior BINDING decisions: Council 1778787266 (tripwire mandatory); Council 1778839098 (Y persona band `:free` only; NEVER Claude-CLI for Y; tripwire critical-path); Spec B shipped

**Timestamp:** 2026-05-15T21:39:35Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: All four options are rearranging deck chairs on a calibration corpus of n=2. You cannot tune thresholds, validate few-shot examples, train an LLM judge, or even defend "status quo" when your ground-truth set is two transcripts — that's not calibration, it's an anecdote with a p-value of "vibes." Worse, the entire defense-in-depth narrative assumes you can recognize "authentic naive" when you see it, but the only humans who've ever labeled this dataset are you (Alex) and Claude — and Claude is exactly the competence band you're trying to detect leakage *from*. You're using the contaminant as the assay. Option (a) makes leakage harder to detect by smoothing the surface; (b) imports Claude-tier judgment into the loop you swore would never touch Claude-CLI for Y (council 1778839098 — read it again); (c) destroys the naturalism that IS the signal Y is built to measure; (d) freezes a tripwire whose thresholds (6, 3) were picked from two data points and have no false-positive/false-negative numbers attached.
KEY CONCERN: Calibration corpus n=2 is the load-bearing flaw under every option. Before any prompt engineering or judge upgrade ships, you need ≥20 authentic-naive runs AND ≥5 deliberately-adversarial "competence-leaked" runs (force Claude-tier persona, or splice in transcripts from a stronger model) to compute the tripwire's actual TPR/FPR. Without that, "almost guarantee results" is theater — you don't know what your current guarantee even is.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: Stack (a)+(c) and run (b) as a *gated, periodic auditor* — not the primary tripwire. Few-shot authentic-naive exemplars (a) is the standard prompt-engineering move for persona fidelity (cf. Anthropic's persona-vector / steering work, Park et al. "Generative Agents", AutoGen/CrewAI persona patterns), and schema-constrained `confusion_signal` per step (c) is exactly the Outlines/Guidance/JSON-mode pattern that turns implicit behavior into auditable structure — this is how Faceless / Synthetic Users research-bots enforce protocol adherence. LLM-as-judge (b) is well-established (Zheng et al. "MT-Bench / LLM-as-a-Judge", Constitutional AI critique, G-Eval, Gemini-Judge), BUT a `:free`-band judge auditing a `:free`-band persona is the classic self-judge weakness — judges drift toward verbosity/competence and miss the very naivety you want to certify (Milička's small-model novice-faithfulness finding goes the other way: weak models *are* authentic, the failure mode is the *judge* misreading them). The known-bad path is (b) alone replacing heuristics — that's reinventing PromptFoo/DeepEval assertions while removing the cheap deterministic guardrail. Keep the heuristic tripwire (it's a regex-tier smoke alarm, not a verdict), layer (a)+(c) for substrate authenticity, and add (b) as a *batch* offline auditor against held-out transcripts — never the runtime gate.
KEY CONCERN: "Near-guaranteed" is the wrong frame — persona-fidelity is a distribution property, not a per-run guarantee; commit to defense-in-depth (substrate lock + few-shot + schema + heuristic + periodic LLM audit) and stop chasing a single silver bullet, because every team that tried (b)-alone ended up with a judge that rubber-stamps competence-drift.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: With n=2 calibration corpus, you cannot meaningfully evaluate any of these alternatives — you'd be tuning to noise. Option (a) is the only proportionate move right now: ~30-60 lines of prompt text, zero new failure modes, zero new processes, testable against the existing corpus in minutes. Option (b) adds a whole new LLM call, judge prompt design burden, `:free` quota pressure, and another component to debug when transcripts go weird — wildly disproportionate to "tripwire fires occasionally on n=2 runs." Option (c) is a structural rewrite that fights the entire point of authentic-naive transcripts; the naturalness loss alone disqualifies it. Option (d) — status quo plus (a) — is the honest answer: heuristic stays diagnostic-only (cheap, debuggable, you already understand its failure modes), persona gets strengthened, and you let the corpus grow to n≥10 before deciding if the heuristic actually needs replacing.
KEY CONCERN: Picking (b) or (c) on n=2 evidence means inheriting 3 months of maintenance cost (judge prompt drift, schema migrations, new debugging surface) to solve a problem you haven't yet proven exists at scale — premature optimization with a real human-cost tail.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The framing hides the real problem. Y's actual job is "find UX friction in the tutor," not "perfectly simulate a naive student." Naive-fidelity is instrumentation correctness, not the deliverable — and the user has inverted the asymmetry. A persona that drifts competent and STILL finds friction is high-signal (friction survived even a smart user); a persona that drifts competent and finds nothing is the only suspect case. So you don't need to "guarantee" naive-fidelity across all runs — you only need to flag the one failure mode: competent-transcript + zero-friction-finding. That collapses the three-leg defense into one binary gate at the report layer, not the generation layer. Layering (a)+(b)+(c) on top of `:free` substrate is defense-in-depth against a problem that doesn't need depth — it needs the right single check in the right place. From scratch: drop legs 2-3 as currently scoped, keep `:free` as substrate ceiling, and add ONE post-hoc filter: "if transcript shape ≥ competent-threshold AND findings_count == 0, discard run." Everything else (few-shot, judge-LLM, schema constraints) is gold-plating that adds tokens, surfaces, and magic numbers without changing the decision the system actually makes.
KEY CONCERN: The user is optimizing the wrong variable. "Guarantee authentic naivety" is unachievable with a stochastic frozen model and also unnecessary — only the (competent ∧ null-finding) cell of the 2x2 is invalid. Build the gate there, not upstream.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Status quo (d) is the lowest-risk path because all proposed additions introduce new failure modes that exceed the failure they mitigate at n=2. (b) LLM-judge is the worst offender — quota exhaustion on `:free` band causes silent skip with no signal, which is a regression from "heuristic always fires" to "sometimes nothing fires"; cascading two stochastic `:free` calls also compounds variance the n=2 corpus cannot characterize. (a) few-shot creates a circular dependency where the calibration corpus IS the example pool, guaranteeing overfitting and transcript-mimicry that defeats the authentic-naive contract. (c) schema-force breaks naturalness AND most `:free` OpenRouter models lack grammar-level constrained decoding, so the leg silently no-ops on unsupported models. Status quo's only acknowledged weakness — n=2 threshold variance — was already flagged in spec as "may need adjustment after first 2-3 runs," meaning the risk is known, bounded, and self-correcting as corpus grows.
KEY CONCERN: (b) LLM-judge `:free` quota-exhaustion silent-skip mode would convert a diagnostic-always-on tripwire into a sometimes-silent one — a strictly worse safety posture than the current single-leg heuristic. Approve status quo + commitment to re-tune thresholds after n=5 runs; reject (b) outright; defer (a)/(c) until corpus reaches n≥5 to break the circular-calibration dependency.

---

## Sanity Check

SANITY [Devil's Advocate]: PASS
NOTE: clean. Cited binding council 1778839098 specifically. n=2 corpus point is well-supported. "Contaminant as the assay" reasoning is sound.

SANITY [Domain Expert]: PASS
NOTE: clean. Many named patterns (persona-vector, Generative Agents, AutoGen/CrewAI, Outlines/Guidance, MT-Bench, Constitutional AI, G-Eval, PromptFoo/DeepEval, Faceless, Milička). Concrete prior-art comparison.

SANITY [Pragmatist]: PASS
NOTE: clean. Numbers concrete (30-60 lines, n=2 noise floor, n≥10 threshold). Maintenance-tail reasoning aligned with persona.

SANITY [First Principles]: PASS
NOTE: clean. Reframes from naive-fidelity-detection to friction-survival 2x2. Insight is novel and load-bearing — refines current heuristic by AND-gating with findings_count==0, not actually conflicting with binding council 1778787266 (current tripwire is post-hoc at finding-doc layer, which is what First Principles recommends).

SANITY [Risk Analyst]: PASS
NOTE: clean. Each option's specific failure mode named with severity. Silent-skip regression risk for (b) is concrete.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The user is asking the wrong question. "Can prompt engineering near-guarantee naive persona fidelity?" — no, persona-fidelity is a stochastic distribution property; there is no per-run guarantee with a frozen `:free` model (4 of 5 clean agents converge on this). The actual leverage is two-fold: (1) grow the calibration corpus before investing in any leakage-detection alternative — at n=2 you cannot tell whether the current heuristic has good thresholds or whether any proposed alternative is better; (2) sharpen the *decision* the tripwire drives, not the *detection*. First Principles' reframing is the load-bearing insight: a competent-persona run that finds friction is still useful signal; only a competent-persona run with zero findings is invalid. Refine the existing tripwire by AND-gating with `findings_count == 0` rather than adding LLM-judge or schema-constraint machinery.

AGENT CONSENSUS: 2 REJECT, 3 CONDITIONAL, 0 APPROVE — 0 agents flagged.

KEY ISSUES:
1. **Calibration corpus n=2 is the load-bearing blocker.** Cannot evaluate ANY of (a)/(b)/(c)/(d) vs current heuristic without ≥10 authentic-naive runs + ≥5 adversarial-leaked runs to compute TPR/FPR. Devil's Advocate, Pragmatist, Risk Analyst all converge.
2. **(b) LLM-judge is strictly worse than current heuristic.** Three failure modes: `:free` quota exhaustion → silent skip (regression from always-on heuristic); self-judge weakness (judge drifts toward competence-favored verdicts per Milička / MT-Bench literature — Domain Expert); cascading two stochastic `:free` calls compounds variance the corpus can't characterize (Risk Analyst).
3. **(c) Schema-constrained generation breaks naturalism + silently no-ops on most `:free` models.** Most `:free` OpenRouter models lack grammar-level constrained decoding. Forced `confusion_signal` token = false-positive clean signal.
4. **(a) Has corpus-circularity risk.** Using n=2 corpus as the few-shot example pool guarantees overfitting + transcript-mimicry. Risk Analyst's concern.
5. **First Principles' 2x2 refinement is the right cheap win.** Current heuristic flags suspect-shaped runs regardless of findings. Adding `AND findings_count == 0` gate flags only the genuinely invalid cell. Cheap, doesn't conflict with binding council 1778787266.

RECOMMENDED PATH:
1. **Refine current heuristic, don't replace it.** Add `AND findings_count == 0` to the suspect condition in `surface-y-tripwire.mjs`. Implements First Principles' 2x2 reframing without breaking the binding tripwire mandate. ~5 lines.

2. **Grow corpus BEFORE tuning thresholds or evaluating alternatives.** Plan:
   - n≥5 authentic-naive runs (incremental — let Y runs happen organically as you work)
   - n≥3 adversarial-leaked runs (deliberately force a Claude-tier persona via `--provider=claude-cli` SUSPENDED FOR Y per binding council OR splice transcripts from a non-`:free` upstream model offline as fixtures)
   - Compute TPR/FPR for current thresholds before deciding any change

3. **Light (a) stacking is acceptable IF corpus diversity is preserved.** Add 1-2 *external* authentic-naive exemplars to persona prompt (e.g., hand-authored or sourced from non-calibration transcripts), NOT the n=2 calibration runs. Avoids circularity.

4. **Defer (b) and (c) indefinitely.** (b) is regression-risk for diagnostic-always-on guarantee. (c) is naturalness-destroying + likely silent no-op.

5. **Per Devil's Advocate: name what "guarantee" means before chasing it.** Add a `tripwire_confidence_band` field to DRAFT-Y frontmatter (e.g., "calibration_corpus_size: 2; threshold_provisional: yes") so future readers understand the current guarantee is "best-effort with thin corpus."

CONFIDENCE: 8/10
[Would rise to 9-10 with TPR/FPR numbers from corpus n≥5. Current verdict is robust against most plausible new information because all 4 alternatives have agent-confirmed structural flaws independent of corpus size.]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
