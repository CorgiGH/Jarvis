# Trustworthy-AI-output research — merged synthesis (2026-06-18, SESSION-81)

Two verified web scours (every name fetched-and-confirmed before counting):
- **Concrete tools** (task `ws962h6dg`): 38 raw → **30 verified / 28 free**; 6 rejected (capability-unconfirmed/hallucinated incl. a fake "Moodle MCP Server"). Full raw output: `…/tasks/ws962h6dg.output`.
- **Broad playbook** (task `ww9nxlx58`): 33 raw → **22 verified**; 11 rejected as "desc-unconfirmed" (NB: some — promptfoo, fast-check — are real tools whose *specific claim* the cited URL didn't prove; held back conservatively, not proven fake). Full raw output: `…/tasks/ww9nxlx58.output`.

## The one principle the whole field converges on
**Never let an AI be the final judge of an AI's correctness.** Anchor "is it right?" to something that cannot rationalize — a program, a math engine, a test, a formal proof, a rule. This is exactly what council-1781781668 ruled and what Alex's instinct said. The LLM-judge is reserved ONLY for what has no deterministic oracle, and even there with independence tricks + human spot-check.

## Three shapes of the principle (simplest → hardest), mapped to our legs

### 1. Compute/check it with a real engine (deterministic oracle) — for COMPUTABLE correctness
The cuts-vs-depth class dies here. Verified, free, installable now:
- **mcp-solver** (szeider) — https://github.com/szeider/mcp-solver — MIT MCP over Z3/MiniZinc/PySAT/Clingo; SAT-2025 paper. Encode `depth = ⌈log₂n⌉` as a constraint; check the correct option is *uniquely* entailed and no other option satisfies → catches ambiguity, not just wrong answers. **Best fit.**
- **sympy-mcp** (sdiehl) — https://github.com/sdiehl/sympy-mcp — Apache-2.0 MCP wrapping SymPy CAS (34 tools: solve/simplify/calculus/ODE). ⚠️ uses `eval`/`parse_expr` — sandbox it.
- **SymPy** directly (no MCP) — simplest for a Kotlin backend that shells out to a small Python verifier service.
- **lean-lsp-mcp** (oOo0oOo) — https://github.com/oOo0oOo/lean-lsp-mcp — Lean 4 proof MCP, actively maintained (v0.27, 2026). Strongest guarantee (closed proof) but heavy (needs Lean toolchain) — **overkill now**, note for later.
- **Property-Based Testing** = the general engineering form: declare an invariant, the framework auto-generates hundreds of cases; the verifier is the *property function*, not an LLM. **jqwik** (https://jqwik.net) = JVM/**Kotlin** → drops into the backend; Hypothesis (Python) / fast-check (TS) are the analogues.

### 2. Generate-many, keep-only-what-passes (correct-by-construction / verifiable rewards) — GENERAL quality-without-QA
- **RLVR best-of-N** — https://github.com/opendilab/awesome-RLVR — inference-time mode needs NO training: sample N AI outputs, run each through the deterministic verifier from shape #1, return the first that passes, discard the rest. The AI proposes, the calculator disposes. Works for ANY vertical, not just teaching — this is the new lever we hadn't named.
- **Correct-by-construction authoring** (PrairieLearn model, https://prairielearn.org — open-source) — the question's answer is *computed from the source/params in code*, so it can't drift from the prompt. Maps to our corpus-grounded authoring: compute the numeric answer from the KC, generate options around it.

### 3. The honest hard part — TASTE / pedagogy / clarity (no deterministic oracle)
No calculator exists for "is this a good explanation / is this ambiguous-in-spirit." Best available, all partial:
- **Chain-of-Verification (CoVe)** — arXiv 2309.11495 — draft → ask independent verification sub-questions (answered in isolation) → revise. Independence breaks the rationalization loop *somewhat*; degrades on deep reasoning; **NOT a substitute for an oracle**.
- **Process Reward Models** ("Let's Verify Step by Step", arXiv 2305.20050; PRM800K open) — score each reasoning step, not just the final answer → errors become locatable; open PRM models exist (Qwen2.5-Math-PRM-7B, math-shepherd-mistral-7b-prm via Ollama, free).
- **Honest residue:** a small human spot-check stays here. The field has no magic for taste yet.

## Recommendation — adopt first (solo-dev, free)
1. **mcp-solver (or plain SymPy service)** = the deterministic answer/ambiguity oracle. Kills the whole cuts-vs-depth bug class. Wire into the authoring step.
2. **Best-of-N over that oracle** = generate K candidate questions, keep only those whose stated answer the oracle confirms is unique-in-options. Quality without QA.
3. **jqwik invariants** in the Kotlin backend for the trace/FSRS/schema legs that ARE computable.
LLM-judge stays ONLY for taste/pedagogy, with CoVe-style independence + a thin human spot-check — never as the correctness gate.

This is also the big-Jarvis answer: every future vertical gets correctness from a non-LLM oracle + best-of-N, LLM-judge only for the genuinely subjective residue.
