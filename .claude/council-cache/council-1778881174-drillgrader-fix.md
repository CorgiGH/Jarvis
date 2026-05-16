# Council review — 1778881174

**Problem:** Server-side `drill-grader-v3` LLM (`:free` OpenRouter model) intermittently emits non-JSON. `DrillGrader.parseGradeJson()` returns null. Caller `TutorRoutes.kt:1606-1648` emits hardcoded fallback `"LLM grader returned malformed output"` with `status: parse_error`, empty rubric. These events poison downstream Surface X calibration corpus (invariants land mechanically). Same task ID was `status: ok` 10h prior → flaky not broken.

**Proposed approach:**
1. Aggressive JSON extract — regex-balanced-brace `{...}` fallback in `parseGradeJson()` if current strip+parse fails.
2. N=2 retry on parse_error at caller before fallback.
3. Capture raw LLM output (~1500-char truncated) in tutor event envelope when status=parse_error (currently stderr-only).

**Project context:**
- Jarvis personal tutor app, single-user (Alex), FII Iași AI bachelor's finals
- Stack: Kotlin/Ktor backend, OpenRouter `:free`-band LLM
- Constraints: NO paid LLM API spend; `:free` band OR Claude-CLI subprocess; stays on `main`; mobile first-class
- Prior decisions: Spec B shipped; grader prompt already enforces JSON-only + parser already strips ```json fences

**Timestamp:** 2026-05-15T21:39:34Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: Layer 2 is the killer — N=2 retry adds ~13s of synchronous latency on every parse_error, and the symptom you're treating IS already a transient stochastic failure of a `:free` model under unknown rate-limit/load pressure. You're solving "the model emitted preamble" by hitting the same flaky endpoint twice in a row, which (a) doubles your exposure to whatever 429/queue/cold-start condition triggered the smell in the first place, and (b) silently degrades the user-facing drill loop by 13s every time the smell fires, on mobile, for a student who came back to the app to grade one problem. Worse, retry-on-parse-error masks the very signal Layer 3 is trying to capture: if the retry succeeds, you never log the raw bad output, so the calibration corpus loses the exact "what does this `:free` model emit when it misbehaves" data you need to tune Layer 1's regex extractor. Layer 1 + Layer 3 together are coherent and cheap; Layer 2 actively fights both of them and ships a worse UX in exchange for nothing measurable.
KEY CONCERN: N=2 retry is latency-pessimal AND data-destructive — it eats 13s on the user's critical path AND erases the raw-output evidence that Layer 3 exists to preserve. Drop Layer 2, or gate it behind "Layer 1 regex extract failed AND log raw before retry" so the corpus still wins when the retry rescues the call.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The proposed three-part approach matches established practice for unreliable JSON-emitting LLMs, but it's missing the canonical first move. The standard pattern for "model emits JSON-ish text with noise" is the Instructor/Outlines/LangChain `OutputFixingParser` stack: (1) strict parse, (2) lenient extract (your "aggressive JSON extract" — this is exactly what `json-repair` / `jsonrepair` npm / Instructor's `mode=MD_JSON` do — regex for first balanced `{...}` is correct and battle-tested), (3) retry with the parse error fed back to the model as a correction prompt ("your previous output was not valid JSON: <snippet>; emit valid JSON only"), then (4) capture raw output for forensics. Your N=2 retry is a blind retry — established practice (Instructor's `max_retries`, LangChain's `RetryOutputParser`) feeds the malformed output + error back into the next call so the model self-corrects; a blind retry on a stochastic `:free` model wastes a request ~50% of the time when the model has a persistent prompt-misread. The raw-capture-in-envelope move is correct and matches the "structured logging for LLM failure modes" pattern (Langfuse, Helicone, Braintrust all do this) — keep it. Also worth noting: OpenRouter does pass `response_format: {type: "json_object"}` through to providers that support it (OpenAI-compatible models, some Mistral, some Gemini on the `:free` band) — not a guarantee but adding it costs nothing and helps on the models that honor it; this is a free win you're leaving on the table.
KEY CONCERN: Blind retry (step 2) diverges from the standard "feedback retry" pattern (Instructor/LangChain `OutputFixingParser`) — pass the malformed raw output + parse error back into the retry's user message so the model can self-correct, otherwise you're just rerolling dice on the same prompt.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: Layer 1 is a no-brainer — 10 lines of regex extraction is cheap insurance against the most common LLM JSON failure mode, and `DrillGrader.parseGradeJson()` is already the right home. Layer 3 is also worth it because without raw output capture you're literally debugging blind on a single-user corpus; stderr-only is fine for a daemon but useless for "why did this drill calibration sample fail two days ago" forensics on a personal tutor. Layer 2 (N=2 retry) is the one that worries me — a blanket 13s latency tax on every parse_error during a real drill is user-hostile UX (Alex IS the user, watching the spinner), and you're retrying the same prompt against the same `:free` model that just emitted garbage; the conditional probability of success on retry isn't independent. Two events in a row earlier today is exactly the kind of correlated failure pattern that says "retry won't help, escalate or accept." The corpus thinning is the real problem, not user-blocking grade attempts.
KEY CONCERN: Gate Layer 2 behind a flag (default OFF), or scope it to background calibration replays only (not live drills) — keep the live-grade path lean and use the new raw-output capture from Layer 3 to drive offline re-grading of parse_error events when the corpus needs topping up. Shipping all three as proposed adds latency + complexity to the user-facing critical path to fix an offline data-quality problem.

## 🧱 First Principles

AGENT: First Principles
STANCE: CONDITIONAL
REASONING: The framing hides that two goals are being solved with one event shape: student UX (graceful degradation) and corpus calibration (clean shape contract). The current hardcoded fallback serves UX but poisons the corpus by manufacturing parse_error events with empty rubrics that look like real grader failures. The proposed three-part fix reduces parse-error frequency but does nothing about corpus poisoning — the actual downstream harm. The right primary fix is for Surface X to filter on `status != "ok"` and exclude parse_error events from the calibration set entirely; the parser/retry/raw-capture work is secondary hardening. Of the three proposed pieces, capturing raw output in the envelope is by far the most load-bearing (makes failures debuggable + lets you decide the next prompt change with data, not stderr archaeology); retry is fine; regex extract is dominated by switching to OpenRouter JSON-mode if the chosen `:free` model supports it.
KEY CONCERN: The proposal treats this as a parser-reliability problem when it's a corpus-contract problem. Surface X is consuming events it should be filtering out, and no amount of parser hardening fixes that — a `:free` model will eventually emit garbage, and when it does, those events must not leak into calibration. Fix the consumer filter first, then harden the producer.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Items 1 and 3 are low-risk and net-positive — regex balanced-brace extract over the FIRST top-level `{...}` is safe because `parseGradeJson` already requires specific typed fields (`correct: Boolean`, `rubric: JsonObject` of booleans, `score: Double`, `elaborated_feedback: String`); a garbage block missing any of those returns null and falls through, so "silent corruption" requires the model to emit a block that happens to type-check against all five fields — vanishingly unlikely for a malformed `:free` response. Raw-text capture closes a real observability gap (today's envelope's `llm_output_full` is the rendered reply, NOT the raw LLM text — TutorRoutes.kt:1657-1660 — so parse_error envelopes are currently useless for debugging) and PII risk is bounded because the model echoes only what the student already typed, which the same envelope already fingerprints via `RcodeRedacted` (TutorRoutes.kt:1663-1668); a separate `llm_output_raw_truncated` field with same redaction posture is consistent. The actual blocker is item 2: a single blind retry doubles the ~13s mobile wait to ~26s on every parse failure — that is the failure mode the student feels.
KEY CONCERN: HIGH — the N=2 retry strategy needs to be (a) ordered AFTER aggressive extract (so extract catches the easy cases without paying retry latency), (b) gated by a "is this a recoverable parse error" check (e.g., empty raw string = network/timeout, don't retry the parser; non-empty raw with no `{` at all = model refused, don't retry; only retry when raw HAS `{` but extract still fails), and (c) accompanied by a `retry_count` field in the envelope so "retry succeeded" is not invisible. Without (a)+(b), every parse_error pays 26s on mobile for no observability win; without (c), you lose the signal that the grader is flaky and can't tune the prompt later. Secondary: drop `maxTokens=600` to a higher cap on the retry attempt only (600-token truncation is itself a probable cause of unbalanced JSON for code-grading rubrics with long `elaborated_feedback`) — retrying with the same cap will just re-truncate at the same boundary.

---

## Sanity Check

SANITY [Devil's Advocate]: PASS
NOTE: clean. REJECT with specific caveat ordering layers 1+3 above layer 2.

SANITY [Domain Expert]: PASS
NOTE: clean. Named patterns concretely (Instructor, Outlines, LangChain OutputFixingParser, jsonrepair, Langfuse/Helicone/Braintrust, OpenRouter `response_format`).

SANITY [Pragmatist]: PASS
NOTE: clean. Concrete numbers (10 lines, 13s latency, mobile, correlated failures).

SANITY [First Principles]: PASS
NOTE: clean. Correctly rejected framing (parser-reliability vs corpus-contract). Identified existing `status: "parse_error"` envelope field that Surface X is not yet filtering on.

SANITY [Risk Analyst]: PASS
NOTE: clean. Read live code to verify claims. Discovered `llm_output_full` envelope field is rendered reply not raw text (confirms observability gap) AND that `maxTokens=600` cap likely contributes to truncation failures.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
Layer 3 (raw-output capture) is the load-bearing fix — every clean agent ranks it highest. Layer 1 (regex extract) is fine but dominated by OpenRouter's `response_format: {type: "json_object"}` mode on supporting models (free substrate-level lever none in the proposal mentioned). Layer 2 (blind N=2 retry) is unanimously broken: latency-pessimal on mobile, retries the same prompt on the same flaky `:free` endpoint, AND erases the raw-output signal Layer 3 exists to preserve. Independently: the proposal misses the corpus-side fix entirely — Surface X should filter `status != "ok"` events out of the calibration set; no producer-side hardening compensates for consuming poisoned events.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL, 0 APPROVE — 0 agents flagged.

KEY ISSUES:
1. **Layer 2 as designed is broken.** Blind retry doubles mobile wait to ~26s, retries the same prompt against the same model, and (per Devil's Advocate) overwrites Layer 3's raw-capture signal when retry rescues the call. Must be redesigned or dropped.
2. **Corpus-poisoning is the ACTUAL downstream harm.** First Principles flagged that even with all 3 layers, when a `:free` model emits garbage the parse_error envelope is still consumed by Surface X. The one-line fix is a consumer-side filter on `status != "ok"` in `surface-x.mjs`. No producer fix replaces this.
3. **Cheaper substrate-level levers are missing.** Domain Expert: OpenRouter `response_format: {type: "json_object"}` is free for supporting `:free` models. Risk Analyst: `maxTokens=600` cap likely truncates long `elaborated_feedback` JSON. Both should be tried before the regex-extract path becomes load-bearing.

RECOMMENDED PATH:
**Ship Layer 3 FIRST** (raw output capture in tutor event envelope). It's the load-bearing observability fix that unblocks all subsequent decisions.

Then in priority order:
- **Add Surface X consumer-side filter** (`status != "ok"` excluded from calibration corpus). One-line fix in `surface-x.mjs`. Independent of any producer-side change.
- **Try OpenRouter `response_format: {type: "json_object"}`** in the grader call. Free if the chosen `:free` model honors it. Single-flag addition.
- **Investigate `maxTokens=600` cap** as probable root cause of truncated JSON. Bump for long elaborated_feedback (especially code-grading path with long rubric).
- **Ship Layer 1 (aggressive regex extract)** in `parseGradeJson()`. Safe per Risk Analyst's type-check analysis. Catches preamble + trailing chatter that escapes substrate-level fixes.

DROP or REDESIGN Layer 2:
- If kept: redesign as Instructor-style **feedback retry** (Domain Expert) — pass parse error + raw output back to LLM as correction prompt. NOT blind retry.
- Alternative: scope to **background calibration replays only** (Pragmatist) — never on live drill grading. Reads raw-output captures from Layer 3 to re-grade parse_error events offline.
- Gate by **recoverability check** (Risk Analyst): only retry when raw HAS `{` but extract failed. Skip retry on empty raw / no-brace responses.
- Always add **`retry_count` field** to envelope.

CONFIDENCE: 8/10
[Would rise to 9-10 with raw-output data from Layer 3 to confirm which failure mode dominates: preamble (Layer 1 catches), truncation (cap bump fixes), missing-keys (nothing catches — need prompt iteration), or refusal (retry/extract both useless). Layer 3 must ship first.]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
