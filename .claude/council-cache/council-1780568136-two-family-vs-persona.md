# Council — 1780568136 — fact-check: two model families vs same-model-different-persona

**Date:** 2026-06-04. **Run:** workflow `factcheck-two-family-vs-persona`. 4 reviewers (ensemble-theory · llm-judge-research · refuter · systems-pragmatic) + judge, web-grounded.

**Question:** Is Claude's claim correct — that for source-FAITHFULNESS verification, two DIFFERENT model families beat the SAME model with a different persona, and a weaker-but-different model still adds value?

**Verdict: CLAUDE_PARTLY_RIGHT (conf 0.86).** All 4 reviewers = PARTLY.

**Where right:**
- Same-model persona/prompt swap = FALSE independence (shared weights → shared blind spots; self-correction limits Huang 2310.01798 / Tyen 2311.08516; LLM-judge self-preference Wataoka 2410.21819). A DIFFERENT family is the correct standard mitigation. "family-collapse → UNCERTAIN" is a sound guardrail.
- A weaker-but-different model adds real value on the NARROW faithfulness/NLI task — MiniCheck (2404.10774): 770M ≈ GPT-4 at ~400× less cost. So "free model = useless" is empirically wrong here.
- Fail-safe asymmetry: under the unanimous-AND gate, a weak/confused 2nd model can only veto → "unverified", never wrongly-trust.

**Where overstated / missed:**
1. "Different family = real independence" too clean — frontier models co-err ~60% even across providers (vs ~33% chance), worse with shared/distilled data; free OpenRouter models are often distillations → maybe not decorrelated. False-accept protection real but not airtight.
2. "Failure mode always safe" understates the COVERAGE cost — every weak-model false-negative buries a correct card under "unverified"; for a solo learner under-coverage is the dominant real cost (the user's legitimate worry).
3. **Missed the better option:** a frozen NLI/entailment model (MiniCheck-class / DeBERTa-NLI) as judge B — truly architecture-independent, zero-API, deterministic, low-variance — strictly better than a chatty free LLM. Pair with a coverage monitor.

**Adopted:** D6 in `2026-06-02-correctness-engine.md` (family B = local NLI model; coverage monitor).
