# Council review — 1778694955

**Problem:** Recording architecture for Surface X (trace-replay rubric-grader) in the student-stand-in design.

**Proposed approaches:**
- A: Server-side tutor_events.jsonl logger (real sessions, ~80 LOC backend).
- B: Playwright re-run synthetic sessions only (no backend touch).
- C: Hybrid A+B (~150 LOC).

**Project context:** jarvis-tutor solo dev (Alex). VPS at 7.8GB RAM, jarvis JVM at -Xmx512m. Existing append-only jsonl logs (activity, conversations, signals, feedback, last_access, knowledge, grades) at /opt/jarvis/data/. No drill-attempt or sidekick log yet. Playwright dogfood gates exist (slice1-5, slice2, slice2-prefetch). OpenRouter `:free` chain with model fallback.

**Timestamp:** 2026-05-13T17:55:55Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: All three options ship a grader that cannot grade. "Trace-replay rubric-grader" presumes the trace contains the artifact being graded — but a JSONL line like `{event:"drill_submit", drill_id:42, answer:"x=3"}` is a pointer, not the work product. The rubric (was the chain-of-thought sound? did the sidekick hallucinate? was the cited PDF page actually relevant?) needs the full LLM prompt, the full LLM response, the resolved PDF context, and the user's intermediate scratchpad state — none of which live in TutorRoutes.kt's request handlers as a single capturable object today. Option B is worse: Playwright replays Alex's clicks but not Alex's *cognition*, so it grades a simulation of a user who doesn't exist. Option C compounds A's incompleteness with B's irrelevance and pretends a `--source` flag resolves the contradiction.
KEY CONCERN: The proposals confuse "log of events" with "transcript of reasoning." Surface X needs the full LLM-call envelope (system prompt + retrieved context + model output + token usage + which `:free` model handled it after fallback) joined to the user-visible state at that moment. Worse: OpenRouter `:free` chains fall through models silently, so the same drill replayed tomorrow grades a different model's output than what Alex actually saw. The recording must be a sealed envelope per LLM turn, not an event stream.
PICKED: NONE-RETHINK

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The established pattern in LLM-eval tooling is bifurcated — Langfuse, LangSmith, and Helicone capture production traces (analogous to A) while OpenAI's evals repo, promptfoo, deepeval, and RAGAS operate on curated/synthetic datasets (analogous to B), and mature shops run both: Langfuse-style trace ingestion for drift detection feeds promptfoo/RAGAS-style regression suites. On the product-analytics side, Posthog, Snowplow, and Mixpanel pair event streams with FullStory/Datadog/Sentry session replay precisely because aggregate events lose the "why" without the trace — exactly the gap a trace-grader Surface X must close. For a solo-dev pre-finals tool, B-only mirrors the promptfoo/evals-repo mistake of grading on toy fixtures that never see real failure modes, while A-only mirrors raw Snowplow logs without replay — you get counts but can't reconstruct a drill attempt end-to-end. C (Langfuse + promptfoo pattern) is industry-standard but only if the synthetic half is treated as regression fixtures derived FROM real logs, not hand-authored in isolation.
KEY CONCERN: Synthetic Playwright sessions must be seeded from real `tutor_events.jsonl` traces (a la Langfuse "dataset from production traces"), otherwise B-half degenerates into the deepeval/promptfoo anti-pattern of evaluating on distributions the model never sees in production.
PICKED: C

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: A is the cheapest path to real signal — ~80 LOC of synchronized JSONL append matches the existing LogAppender pattern Alex already maintains, so the marginal debug surface is near-zero. B alone gives synthetic-only data that won't catch the things Alex actually does wrong in real sessions, which is the whole point of recording. C's extra ~70 LOC buys you a Playwright harness Alex already has working for slice1-5/slice2 gates — re-running those scenarios against the new logger is a 10-minute ad-hoc task, not a permanent ~70 LOC of tooling to maintain through 9-37 days of finals churn. The 3-month hidden cost of C is two code paths to keep in sync when the event schema evolves; the cost of A is one append helper that mirrors patterns Alex debugs in his sleep.
KEY CONCERN: C double-counts existing infrastructure — Playwright re-runs are already a solved workflow, so paying ~70 extra LOC to formalize them as "synthetic recording" is maintenance burden disguised as completeness.
PICKED: A

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The framing assumes the grader needs *traces* (timestamped event sequences from a recording mechanism), but a trace-grader fundamentally consumes one thing: **a sequence of (state, action, outcome) tuples it can score against a rubric**. The recording architecture (server log vs Playwright vs hybrid) is downstream plumbing — it answers "how do we capture tuples" not "what tuples does grading need." All three proposals conflate the input *substrate* with the input *schema*, which is why they feel interchangeable and none of them feel right. The actual blocker for a solo dev pre-finals isn't "where do traces come from" — it's "do I have a labeled rubric + ~20 golden traces with known-correct grades to validate the grader itself works." Without that, you're building a grader that grades nothing measurable.
KEY CONCERN: No proposal addresses **ground truth**. A trace-grader with no labeled gold set is unfalsifiable — you cannot tell if it's working, regressing, or hallucinating. A, B, and C all produce *more unlabeled traces*, which is the opposite of what's scarce.
PICKED: D — Hand-curate 15-25 labeled golden traces (Alex's own recent sessions, manually scored against the rubric) as the fixture set. Treat real-session capture and Playwright synthesis as later additions for breadth, not as the bootstrap input.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Option A's headline risk is not disk fill — 130KB/week baseline plus ~1 event/sec during study sessions is maybe 5-10MB/month, trivial on a 7.8GB VPS even over a year. The real CRITICAL risks are (1) PII / answer-key leakage: drill submissions contain Alex's wrong answers, full prompts, and OpenRouter completions — if tutor_events.jsonl captures payload bodies it becomes a credential-grade artifact that must never leave the VPS or land in a git repo, and (2) LOCK contention amplification: the existing process-wide mutex already serializes activity log appends; adding page-nav events (10-50× the write rate) risks tail-latency spikes on drill submit under the same lock, which Alex will perceive as the tutor "freezing" mid-session. JSONL mid-line corruption is HIGH but mitigable with `O_APPEND` + line-buffered writes + a startup truncate-to-last-newline repair. Option B's drift risk is real but BOUNDED. Option C inherits A's two CRITICAL risks without reducing either.
KEY CONCERN: Answer-key / wrong-answer PII in tutor_events.jsonl becoming an exfiltration target — once it exists, every future feature (export, debug bundle, support-share) is one careless `scp` away from a leak. Must be designed with payload-redaction at write-time, not read-time.
PICKED: A (CONDITIONAL on: separate log file with its own appender + lock, payload-field allowlist not blocklist, async queue with bounded backpressure so drill-submit latency is decoupled from log-write, daily rotation + 14-day retention cap, and explicit `.gitignore` + VPS-only path under `/opt/jarvis/data/private/`)

---

## Sanity Check

SANITY Devil's Advocate: PASS — Identifies OpenRouter `:free` model-fallback as a concrete reproducibility risk; the "envelope vs event" distinction is load-bearing and missed by the original framing.

SANITY Domain Expert: PASS — Named real systems (Langfuse, LangSmith, Helicone, OpenAI evals, promptfoo, deepeval, RAGAS, Posthog, Snowplow, Mixpanel, FullStory, Datadog, Sentry). Industry-standard bifurcation is verifiable.

SANITY Pragmatist: PASS — Concrete LOC + maintenance reasoning, references existing slice1-5/slice2 gates.

SANITY First Principles: PASS — Surfaces ground-truth as the actual scarce resource. Concrete proposal (15-25 labeled golden traces).

SANITY Risk Analyst: PASS — Two CRITICAL risks with concrete mitigations (separate appender, payload-field allowlist, async queue, daily rotation, gitignore + private path).

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
None of A / B / C ships as proposed. The council converges around A-respec'd as the recording substrate, but with TWO load-bearing additions that the original proposal missed: (1) Devil's Advocate's ENVELOPE-CAPTURE requirement — Surface X needs the full LLM-call envelope per turn (system prompt + retrieved context + model output + token usage + which `:free` fallback model resolved) so the grader has the work product, not just a pointer; this also closes the OpenRouter model-fallback reproducibility hole. (2) First Principles' GROUND-TRUTH requirement — without 15-25 labeled golden traces with known-correct grades, the rubric-grader is unfalsifiable and you cannot tell working from regressing from hallucinating. C is rejected because synthetic re-runs add maintenance burden over Playwright dogfood gates Alex already has, and DE's pattern-correct synthetic-from-production-only stays a V2 expansion. The shape is: A-respec'd + envelope-capture + ground-truth fixture set + RA's hardening conditions.

AGENT CONSENSUS: 2 REJECT (DA → NONE-RETHINK on envelope grounds, FP → D on ground-truth grounds), 3 CONDITIONAL (DE → C, P → A, RA → A-with-conditions). Zero flagged. Pragmatist + Risk Analyst converge on A; DE wanted C but conceded the synthetic-half must derive from production traces (which is V2 only); the two REJECTs identified missing requirements rather than rejecting the input source itself.

KEY ISSUES:
1. **Envelope-capture (CRITICAL — DA).** Server-side log must capture the full LLM-call envelope per turn: system prompt template id + resolved system prompt + retrieved corpus context + full LLM input + full LLM output + token counts + WHICH model resolved after `:free` fallback. Event pointer alone is insufficient.
2. **PII / answer-key leakage (CRITICAL — RA).** Log path MUST be `/opt/jarvis/data/private/tutor_events.jsonl`, gitignored, separate appender + lock, payload-field allowlist not blocklist, daily rotation + 14-day retention. Never auto-export.
3. **LOCK contention (HIGH — RA).** Use separate lock from existing activity-log appender; consider async bounded-queue path so drill-submit latency is decoupled from log-write latency.
4. **Ground-truth bootstrap (CRITICAL — FP).** Before the LLM-judge grades a single new trace, Alex hand-curates 15-25 of his own recent sessions with manual rubric grades. These form the validation fixture set; the LLM-judge is calibrated against them BEFORE going live. Without this, Surface X is unfalsifiable.

RECOMMENDED PATH:
**Approach A-respec'd with envelope-capture + ground-truth bootstrap + RA hardening:**

1. **New backend module:** `TutorEventLog.kt` — separate appender + ReentrantLock from existing LogAppender. Path `/opt/jarvis/data/private/tutor_events.jsonl`. Async bounded-queue writer (drop with WARN if queue saturates rather than block drill-submit). `O_APPEND` + line-buffered + startup truncate-to-last-newline repair.

2. **Envelope-capture hooks** on drill-grade and sidekick-ask endpoints. Each turn writes ONE jsonl line with allowlisted fields: `{event_type, event_id, timestamp_utc, task_id, session_id (cookie-derived), prompt_template_id, system_prompt_sha256 (full text gitignored, hash referenced), retrieved_context_summary (snippet IDs + first 80 chars), llm_input_full, llm_output_full, model_resolved (after :free fallback), tokens_in, tokens_out, latency_ms, status (ok/429/error)}`. Drill submissions: redact raw R-code answer body if matches a known-secret pattern; otherwise include (Alex's wrong answers are pedagogy gold).

3. **Privacy:** path under `/opt/jarvis/data/private/` (new subdir, jarvis:jarvis 0700). `.gitignore` adds `/opt/jarvis/data/private/`. Daily rotation `tutor_events.YYYY-MM-DD.jsonl`, 14-day retention via systemd-tmpfiles or a cron. Never ship in debug bundles.

4. **Ground-truth fixture set** (FP's blocker): Alex hand-curates 15-25 recent drill+sidekick traces, labels each against the rubric (e.g. "predict-before-attempt invariant: PASS", "rubric-chip-readability invariant: FAIL — snake_case visible"). Stored at `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md`. Surface X's first deliverable is "the LLM-judge agrees with Alex's manual grade on ≥80% of the 15-25 fixture set." Below that threshold, the rubric or the prompt is broken; do not ship Surface X.

5. **Synthetic Playwright recording deferred to V2** — Playwright dogfood gates stay as existing smoke checks. If/when Surface X has stable real-trace signal, V2 can add a synthetic-from-production pattern (DE's Langfuse-style) where production traces become the dataset for regression replays. Not in V1.

6. **Surface X output:** `docs/standin-findings/DRAFT-X-<sessionid>-<timestamp>.md` with provenance stamp + per-invariant PASS/FAIL/N-A + cited evidence (event_id pointers, NOT full PII payloads). Stale-check via `tools/findings-stale-check.mjs`.

Confidence: 9
[Would climb to 10 with: (a) explicit Kotlin module sketch in the spec showing TutorEventLog appender boundary, (b) the 15-25 golden fixture set already started by Alex. Would drop to 7 if Alex skips ground-truth and ships X straight against new unlabeled traces.]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1778694955-standin-recording-arch.md
