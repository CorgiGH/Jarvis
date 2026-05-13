# Student stand-in — 3-surface dogfood design

> Spec for an automated dogfood system that complements Alex's manual study-session dogfood. Three complementary LLM-driven surfaces critique the live tutor site from different lenses: a pedagogy invariant judge (X), a layperson visual critic (Z), and a beginner-persona student stand-in (Y). Council-shaped over 3 rounds of adversarial review; cites verified literature (MathVC, EduAgent, Milička 2024 PLoS One, Constitutional AI).

## North star

Augment — not replace — Alex's daily dogfood with three independent, low-cost-per-run signals about whether the tutor site:
1. **Honors its own pedagogy contracts** (Surface X — invariant trace-grader).
2. **Looks legible to a non-designer** (Surface Z — novice-eyes visual critic).
3. **Could plausibly be navigated by a fresh-eyes beginner** (Surface Y — student stand-in).

Each surface emits findings to a quarantined drafts path with a provenance stamp. Findings are reviewed by Alex before influencing any design decision. The system NEVER auto-commits, NEVER blocks deploys, NEVER shares OpenRouter quota with Alex's live grader+sidekick.

## Council provenance

Three rounds of `claude-council-lite` shaped this design:

| Round | Question | Verdict | Cache |
|-------|----------|---------|-------|
| 1 | Naivety-enforcement strategy for the stand-in | FLAWED → split into X (no naivety) + Y (constrained-D) + Z (added during user clarification) | `.claude/council-cache/council-1778693954-student-standin-naivety.md` |
| 2 | Trigger model for the 3 surfaces | FLAWED → surface-differentiated (X manual + advisory deploy-gate, Z manual, Y manual-only); provenance stamp + quota-verification non-negotiable | `.claude/council-cache/council-1778694612-standin-trigger-model.md` |
| 3 | Recording architecture for Surface X | FLAWED → A-respec'd: envelope-capture + ground-truth fixture set + RA hardening | `.claude/council-cache/council-1778694955-standin-recording-arch.md` |
| 4 | Full-spec review | FLAWED → 6 inline correctness fixes applied (DOM-fingerprint normalization, judge-side provenance, fixture-gate stochastic stability, Y/Z race flag, R-code redaction, INV-09/10 recast to INFO). No scope cuts (build-everything preference durable). | `.claude/council-cache/council-1778696411-standin-full-spec-review.md` |

A specialized literature researcher ran in parallel with Round 1 and verified 6 papers on LLM-as-naive-student. Strongest cite: `MathVC` (arXiv:2404.06711) symbolic-schema gate; `Generative Students` confusion-tuple primitive; `Milička et al. 2024 PLoS One` measurement of GPT-4 "hyper-accuracy" persona leakage.

## Anchoring constraints

- **Single user.** Alex only. No multi-tenancy, no auth.
- **OpenRouter `:free` chain only.** No paid APIs. Recurring `:free` quota cascades documented (overnight refill).
- **Tiny VPS.** 7.8 GB RAM total; jarvis JVM at -Xmx512m.
- **Pre-finals window.** PS HW 2026-05-21 (8 days). Finals Jun 1 – Jun 21 2026. Scarce attention. False alerts are the dominant failure mode to avoid.
- **Live tutor surface is load-bearing.** Alex studies daily on corgflix.duckdns.org/tutor/. The stand-in MUST NOT degrade live grader / sidekick / drill UX during study hours.

## Architecture overview

Three surfaces wired around one shared infrastructure layer. Sequenced X → Z → Y; each ship-gates the next.

```
┌────────────────────────────────────────────────────────────────────┐
│                 SHARED INFRA (cross-cutting)                       │
│                                                                    │
│  tools/lib/provenance.mjs           tools/findings-stale-check.mjs │
│    ↳ getStamp() →                     ↳ flags [STALE] when stamp   │
│      {git_head, bundle_hash,            doesn't match current      │
│       live_dom_fingerprint,                                        │
│       ts_utc, surface_version}                                     │
│                                                                    │
│  docs/notes/2026-05-13-openrouter-quota-isolation.md               │
│    ↳ BLOCKING prereq: verify :free quota isolation                 │
│      between separate API keys on same OR account                  │
│                                                                    │
│  docs/standin-findings/                                            │
│    DRAFT-X-<sess>-<ts>.md   ← Surface X output                     │
│    DRAFT-Z-<sess>-<ts>.md   ← Surface Z output                     │
│    DRAFT-Y-<task>-<ts>.md   ← Surface Y output                     │
│    golden/<bootstrap>.md    ← FP ground-truth fixture set          │
│    schemas/<task>.yaml      ← Y concept schemas                    │
│    screenshots/             ← Z screenshots (gitignored)           │
└────────────────────────────────────────────────────────────────────┘
                                  │
       ┌──────────────────────────┼──────────────────────────┐
       │                          │                          │
       ▼                          ▼                          ▼
   SURFACE X                  SURFACE Z                  SURFACE Y
 trace-grader              novice-visual              student stand-in
 (invariants)              (layperson eye)            (beginner persona)

 Manual CLI +              Manual CLI;                Manual CLI ONLY.
 advisory deploy           standalone +               Hard cap
 gate (optional).          piggyback modes.           ≤50 LLM calls /
                                                      session.

 1 LLM call per            1 LLM call per             Bounded by cap +
 invariant per             screenshot.                MathVC schema-gate
 session.                                             regenerate loop.

 Ship gate:                Ship gate:                 Ship gate:
 ≥80% agreement            X shipped +                X+Z shipped +
 on 15-25 golden           quota verdict              separate API key
 fixture traces.           permits.                   OR offline window.
       │                          │                          │
       └──────────────────────────┴──────────────────────────┘
                                  │
                                  ▼
                ┌─────────────────────────────────────┐
                │   BACKEND ENVELOPE-CAPTURE LAYER    │
                │                                     │
                │  TutorEventLog.kt                   │
                │   ↳ separate appender + lock        │
                │   ↳ async bounded-queue writer      │
                │   ↳ /opt/jarvis/data/private/       │
                │   ↳ tutor_events.YYYY-MM-DD.jsonl   │
                │   ↳ 14-day rotation                 │
                │   ↳ gitignored                      │
                └─────────────────────────────────────┘
```

## Surface X — trace-replay rubric-grader

**Job:** LLM-as-judge reads recorded sessions, checks each invariant, emits PASS / FAIL / N-A per invariant with cited evidence.

### Inputs

- Session window: `(session_id, from_ts, to_ts)` OR `(task_id, last N events)`.
- Source: `/opt/jarvis/data/private/tutor_events.YYYY-MM-DD.jsonl` (real Alex sessions) by default. `--from-fixture <path>` for golden-trace calibration replay.

### Invariant catalog (V1)

Sourced via the hybrid method (derive from existing shipped guardrails + hand-author for new design intent; LLM-propose-curate expansion deferred to V2).

**Derived from shipped guardrails:**

- `INV-01`: PREDICT textarea filled before R-CODE textarea accepts input (Queue D predict-before-attempt, commit `d611166`).
- `INV-02`: Wrong drill grader response cites specific failed rubric_chip by name.
- `INV-03`: Sidekick refuses when selection Jaccard ≥ 0.7 with drill statement (drill-self-paste guard, `d611166`).
- `INV-04`: Chip-ask never fires inside open active DRILL card (`data-card-type="DRILL"` AND `data-state="open"`).
- `INV-05`: Sidekick reply cites ≥ 1 corpus path when corpus-eligible material invoked (Slice 2.5, commit `dc15613`).
- `INV-06`: Locked drill cards (WORKED / DEFINITION / CHECK) cannot open until active drill answered or skipped.
- `INV-07`: PDF stepper preserves drill state across A1 → A2 navigation.

**Hand-authored (new design intent):**

- `INV-08`: Drill rubric_chip text rendered human-readable, NOT raw snake_case. Motivating example: `uses_rlaplace_or_inverse_cdf_sampler` MUST NOT appear verbatim in user-visible UI.
- `INV-09` (INFO-only, never-gate): Sidekick latency observed. Surface a `latency_p95_ms` field per session; flag with `status=INFO` when > 8000 ms with corpus-eligible request. Does NOT count as a PASS/FAIL invariant — `:free` chain 429 fallback cascade makes hard latency gates self-defeating (Council #4 DA + P fix). Useful for long-term trend visibility.
- `INV-10` (INFO-only, never-gate): Grader latency observed. Surface `latency_p95_ms` per session; `status=INFO` when > 12000 ms. Same rationale as INV-09. Track over time to spot drift after model fallback rotations or backend changes; never use as a ship-blocking signal.

Invariants with `status=INFO` are included in findings docs but never counted as FAIL for any aggregation (success-metrics, deploy-gate output, calibration agreement).

### Grader prompt structure

```
SYSTEM: You are an invariant judge. Given a session trace and one
invariant statement, return strict JSON:
  {
    "status": "PASS" | "FAIL" | "N_A",
    "evidence": {"event_ids": [...], "excerpt": "..."},
    "reason": "<one sentence>"
  }

INVARIANT: <INV-id + statement>

SESSION EVENTS (jsonl excerpt bracketed to invariant scope):
<event_log_subset>

Reply: <JSON only>
```

One LLM call per (session × invariant). For 10 invariants × 1 session = ~10 calls.

### Output schema

```yaml
---
surface: X
session_id: <uuid>
provenance:
  git_head: <sha>
  bundle_hash: <hash>
  live_dom_fingerprint: <hash>
  ts_utc: <iso>
  surface_version: x-v1.0
fixture_set: docs/standin-findings/golden/2026-05-13-bootstrap-traces.md
invariants_run: 10
---

# Surface X findings — session <sid>

| Invariant | Status | Evidence (event_id) | Reason |
|-----------|--------|---------------------|--------|
| INV-01 | PASS | evt-42 | predict typed at 14:02:11 before R input |
| INV-08 | FAIL | evt-118 | rubric_chip rendered as "uses_rlaplace_..." |
| ... | | | |

## Failures (full evidence)

### INV-08 — FAIL
- event_id: evt-118
- excerpt: "rubric_chips: [uses_rlaplace_or_inverse_cdf_sampler, ...]"
- reason: <judge text>
```

### Ground-truth fixture set (FP ship gate)

Surface X is unfalsifiable without a labeled gold set. First deliverable BEFORE any production verdict:

1. Alex hand-curates 15-25 trace excerpts from recent `tutor_events.jsonl`.
2. For each (trace, invariant) pair, Alex manually labels PASS / FAIL / N-A with one-sentence reason.
3. Stored at `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md`.
4. **Ship gate** (Council #4 RA-3 fix — stochastic-judge stability):
   - LLM-judge calibration calls run with `temperature = 0.0` AND a fixed seed (`seed = 42`).
   - For each (trace, invariant), judge runs 3 times; majority vote determines the judge verdict for that pair.
   - Ship gate is `≥ K of N exact-match` against Alex's labels, where `K = ceil(0.80 * N_excluding_INFO)`. For 15 fixture pairs excluding INFO-only invariants, K = 12. Statistical floor avoids the ±0.16 CI fail-flip of a point-estimate ≥80% gate at N=25.
   - Below threshold → fix rubric / prompt before any production run.

### CLI

```bash
# Grade real session
node tools/surface-x.mjs --session <session_id> --invariants all

# Grade specific task window
node tools/surface-x.mjs --task <task_id> --from <iso> --to <iso>

# Calibration run against golden fixture
node tools/surface-x.mjs --from-fixture docs/standin-findings/golden/2026-05-13-bootstrap-traces.md --calibrate

# LLM-propose new invariants (V2; gated)
node tools/surface-x.mjs --propose-invariants --since <iso>
```

### Trigger

Manual CLI default. Optional opt-in `RUN_LLM_EVAL=1` flag on `deploy.sh` runs X on the freshly-deployed bundle's smoke trace; **ADVISORY only — never blocks deploy** (Risk Analyst: blocking on flaky LLM judge trains Alex to disable the gate).

## Surface Z — novice-eyes visual critic

**Job:** Layperson persona looks at page screenshots, critiques readability / contrast / hierarchy / typography. Complements `review-site` skill (expert panel) with a non-designer lens.

### Run modes

**Mode A — Standalone screenshot sweep:**

- Hits configurable page list (default: tutor home, task workspace, drill mid-attempt, sidekick mid-stream, review page, mobile-viewport sweeps).
- For each page: navigate, wait for paint, `page.screenshot({fullPage: true})`.
- One LLM call per screenshot with layperson persona.

**Mode B — Piggyback inside Y's Playwright session:**

- Shares Y's Playwright context.
- Y triggers screenshot after each navigation / click that changes layout.
- **MUST** `await page.waitForLoadState('networkidle', { timeout: 5000 })` before each screenshot (Council #4 RA-2 fix). Without this, screenshots can capture partial sidekick stream / mid-render drawer animations and produce phantom readability bugs.
- Same layperson LLM call plus context "Y just attempted X here".
- Output: per-step entries woven into Z findings doc (NOT Y's doc).

### Programmatic visual lints

Cheap rule-based checks run BEFORE the LLM call, seeding context with concrete defects so the LLM extends rather than invents:

- `LINT-snake-case`: scan visible text DOM for `\b[a-z]+_[a-z_]+\b` outside `<code>` / `<pre>` tags. **Catches the rubric-chip motivating example directly.**
- `LINT-contrast`: compute WCAG AA ratio on text nodes; flag < 4.5:1.
- `LINT-font-size`: scan computed font-size on body text; flag < 14 px on mobile viewport.
- `LINT-overflow`: scan for horizontal scrollbars on viewport widths {375, 768, 1280}.

### Layperson persona prompt

```
SYSTEM: You are a non-designer Romanian uni student opening this
study site for the first time on your laptop. You don't know what
any of the technical content is. You're judging ONLY whether things
look right to your eyes:
  - Is it obvious what's a button vs decoration?
  - Is text legible? Contrast OK? Font readable?
  - Does code-font ever leak into non-code text (e.g. snake_case
    where Title Case belongs)?
  - Is hierarchy clear (headings vs body vs labels)?
  - Mobile crampy? Desktop overly stretched?
  - Anything just plain ugly or jarring?

Reply STRICT JSON:
{
  "severity": "blocking" | "readability" | "cosmetic" | "none",
  "observations": [
    {"what": "...", "where": "<region desc>", "why_it_hurts": "..."}
  ],
  "one_liner": "<single sentence overall impression>"
}

DO NOT comment on content correctness. DO NOT use design jargon.
You are NOT a designer. You are a user.

AUTO-LINTS (treat as hints, extend with your own eye):
<lint_output>

DOM TEXT EXCERPT (text-only fallback if vision model unavailable):
<dom_text>
```

### Output schema

```yaml
---
surface: Z
mode: standalone | piggyback
session_id: <uuid>
provenance: { git_head, bundle_hash, live_dom_fingerprint, ts_utc, surface_version: z-v1.0 }
pages_visited: 7
---

# Surface Z findings — <ts>

| Page | Severity | One-liner |
|------|----------|-----------|
| /tutor/?taskId=01KR6... drill-open | readability | rubric chips render as code-keys, hard to read |
| /tutor/ home | none | clean |
| ... | | |

## Detailed observations

### /tutor/?taskId=...&drill-open (readability)
**Auto-lints:** 4 snake_case strings outside code blocks at `.rubric-chip[data-key='uses_rlaplace...']`.
**Layperson observations:**
- what: "rubric labels look like programmer code"
  where: "below CHECK ANSWER button"
  why_it_hurts: "I can't read 'uses_rlaplace_or_inverse_cdf_sampler' fast; eyes slide off"

**Screenshot:** `docs/standin-findings/screenshots/Z-<sessionid>-<page>.png` (gitignored)
```

### CLI

```bash
# Standalone sweep — fixed page list
node tools/surface-z.mjs --mode standalone

# Standalone w/ custom page list
node tools/surface-z.mjs --mode standalone --pages '/tutor/,/tutor/review,/tutor/?taskId=<id>'

# Mobile-viewport sweep
node tools/surface-z.mjs --mode standalone --viewports 375,768,1280
```

Piggyback mode has no separate CLI; Y wires Z into its Playwright context.

### Model selection

Vision-capable `:free` model on OpenRouter chain. Candidates verified in pre-flight:

- `qwen/qwen2.5-vl-32b-instruct:free`
- `meta-llama/llama-3.2-11b-vision-instruct:free`

Text-only fallback if no vision model resolves: layperson receives auto-lint output + DOM-text excerpt.

### Trigger

Manual CLI default. **No V1 cron** (Risk Analyst quota + Devil's Advocate stamp-drift).

## Surface Y — student stand-in

**Job:** Beginner persona drives the tutor site via Playwright DOM. Naivety enforced by external schema-gate (MathVC pattern). Discovers unknown-unknowns — UX dead-ends, broken affordances, missing scaffolding for a fresh-eyes user.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                  SURFACE Y RUN LOOP                             │
│                                                                 │
│  ┌──────────────┐                                               │
│  │ Init session │ ← --task <id> --schema <path> --model <name>  │
│  │  - new Playwright context                                    │
│  │  - load concept-schema for task                              │
│  │  - empty seen-concepts ledger                                │
│  │  - empty confusion-tuples list                               │
│  └──────────────┘                                               │
│         │                                                       │
│         ▼                                                       │
│  ┌──────────────┐  ┌──────────────────────────────────────────┐ │
│  │ Loop step    │→ │ Z piggyback: screenshot + LLM critique   │ │
│  │ (≤50 total)  │  └──────────────────────────────────────────┘ │
│  └──────────────┘                                               │
│         │                                                       │
│         ▼                                                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  1. Read current DOM (only what user sees)               │   │
│  │  2. Update seen-concepts ledger from new page content    │   │
│  │     via NER pass on visible text + schema match          │   │
│  │  3. Build LLM input:                                     │   │
│  │     - persona prompt                                     │   │
│  │     - current DOM excerpt                                │   │
│  │     - seen-concepts ledger (current state)               │   │
│  │     - active confusion-tuples                            │   │
│  │     - last 5 session events                              │   │
│  │  4. Call weak free-tier model                            │   │
│  │  5. Schema-gate filter:                                  │   │
│  │     - parse LLM output for concept references            │   │
│  │     - reject if references concept ∉ schema ∪ ledger     │   │
│  │     - regenerate up to 2x with violation hint            │   │
│  │     - fail-loud after 3rd attempt → log violation        │   │
│  │  6. Execute action (click / type / nav)                  │   │
│  │  7. Log event to Y session transcript                    │   │
│  │  8. If "done" condition met or cap hit: break            │   │
│  └──────────────────────────────────────────────────────────┘   │
│         │                                                       │
│         ▼                                                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Emit findings (provenance-stamped)                       │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Concept schema (per task)

Hand-authored per task in V1. Format (YAML):

```yaml
# docs/standin-findings/schemas/PS-Tema-A.yaml
task_id: 01KR6K07T6PATPRR5KH1JXYF8E
subject: PS
title: "Tema A — Laplace distribution"
concepts:
  - id: laplace_distribution
    aliases: ["Laplace", "distribuția Laplace", "double-exponential"]
    introduced_in: ["A1.pdf:page=2", "_extras/PS/_fii/.../probability8.md"]
  - id: inverse_cdf_sampling
    aliases: ["inverse-CDF", "rlaplace", "quantile transform"]
    introduced_in: ["A1.pdf:page=4"]
  - id: histogram_overlay
    introduced_in: []
    generic: true  # standard R skill, always allowed
confusion_tuples:
  - between: [laplace_distribution, normal_distribution]
    why: "Both symmetric around mean; novice confuses kurtosis"
  - between: [inverse_cdf_sampling, rejection_sampling]
    why: "Both are sampling methods; both use uniform R.V.s"
```

V2 deferred: PDF-parse-based schema bootstrap.

### Persona prompt (weak free-tier model)

```
SYSTEM: You are Alex, a first-year FII Iași AI student. You have
NEVER seen this course material before this session. You know basic
high-school math (algebra, sums, derivatives) and some R syntax
(variables, vectors, mean()). You do NOT know any of the following
unless this session's history shows you've read about them:
  <enumerate schema.concepts EXCLUDING ledger.seen>

You're sitting in front of the tutor site trying to do this task
for the first time. If a concept name appears unfamiliar, stay
confused — say so, ask sidekick, or stare at it. NEVER reason from
knowledge you haven't been shown this session.

If you find yourself "remembering" something off-ledger, that's
the LLM substrate leaking. Mark with [LEAK?] and explain.

SESSION HISTORY (last 5 events):
  <event log excerpt>

SEEN-CONCEPTS LEDGER:
  <ids of concepts you've encountered this session via DOM>

ACTIVE CONFUSION-TUPLES (you systematically mix these up):
  <list>

CURRENT DOM:
  <excerpt of visible page content>

Decide ONE next action. Reply STRICT JSON:
{
  "thinking": "<beginner's internal monologue, 2-3 sentences>",
  "action": "click" | "type" | "navigate" | "ask_sidekick" | "give_up",
  "target": "<CSS selector or text label>",
  "payload": "<typed text if action=type/ask_sidekick>",
  "observation": "<what I'm confused about or noticed>"
}
```

### Schema-gate filter (external, MathVC pattern)

```typescript
function filterResponse(llmJson, schema, ledger):
  refs = extractConceptReferences(
    llmJson.thinking + llmJson.payload + llmJson.observation
  )
  for ref in refs:
    if ref not in schema.concepts:
      if not isGeneric(ref):  // basic R / basic math: allowed
        if ref not in ledger:
          return VIOLATION(ref)
  return OK
```

3-strike regenerate with violation hint: `"You referenced 'rejection sampling' — you haven't seen this concept. Stay confused."` Fail after 3rd attempt → log to violations list + skip step.

### Confusion-tuple injection (Generative Students primitive)

Per ledger update, sample one active confusion-tuple. Inject into persona:
```
You frequently confuse <A> with <B> because <why>.
```
Forces wrong answers to be SYSTEMATIC misconceptions, not random.

### Hard caps (Risk Analyst mitigations)

- `MAX_CALLS_PER_SESSION = 50`. Counter increments each LLM call (gate + persona + sidekick-reply parsing). At cap → emit findings + exit.
- `MAX_DURATION_MIN = 10`. Wallclock guard against Playwright hangs.
- `MAX_REGENERATE_PER_STEP = 2`. Bounds gate loop.
- API key: env `OPENROUTER_API_KEY_STANDIN`, distinct from `OPENROUTER_API_KEY` used by live grader+sidekick. **Verified isolated by quota pre-flight gate.**
- Health probe: between LLM calls, ping `/api/v1/health`; abort run if 429 returned.
- Every Y-driven HTTP request to tutor backend MUST carry header `X-Standin-Run: 1`. Backend tags resulting `tutor_events.jsonl` entries with `is_synthetic: true` so Surface X replay skips Y-induced events when grading real-user invariants (Council #4 RA-2 fix).

### Output schema

```yaml
---
surface: Y
session_id: <uuid>
task_id: <id>
schema_path: docs/standin-findings/schemas/PS-Tema-A.yaml
provenance: { git_head, bundle_hash, live_dom_fingerprint, ts_utc, surface_version: y-v1.0 }
model_resolved: <weak-tier model name>
calls_used: 37
duration_min: 6
gate_violations: 4
---

# Surface Y findings — task <id>, session <sid>

## Discovered unknown-unknowns
- <fresh-eyes observation 1>
- <observation 2>

## Schema-gate violations (naivety leakage)
- Step 18: persona referenced "rejection sampling" not in ledger; 2 regenerates; on 3rd referenced it again → logged as leak
- ...

## Session transcript
| Step | Action | Target | Observation |
|------|--------|--------|-------------|
| 1 | navigate | /tutor/?taskId=... | "first time seeing this; lots of side panels" |
| 2 | click | data-testid=problem-A1 | "starting with A1, looks like first problem" |
| 3 | ask_sidekick | "what is rlaplace?" | "PDF mentions it but no def given" |
| ... | | | |

## Sidekick interactions
- Step 3 query: "what is rlaplace?"
  reply excerpt: "..."
  cited paths: [_extras/PS/...]
  beginner reaction: "<persona observation>"
```

### CLI

```bash
# Single task run, schema explicit
node tools/surface-y.mjs --task 01KR6K07T6PATPRR5KH1JXYF8E --schema docs/standin-findings/schemas/PS-Tema-A.yaml

# Override model
node tools/surface-y.mjs --task <id> --schema <path> --model qwen-2-7b:free

# Disable Z piggyback (default ON)
node tools/surface-y.mjs --task <id> --schema <path> --no-piggyback-z
```

### Trigger

**Manual CLI ONLY.** No scheduled trigger in V1. Human-in-the-loop triage gate.

## Cross-cutting infrastructure

### Provenance stamp

```javascript
// tools/lib/provenance.mjs
import { execSync } from "node:child_process";
import { createHash } from "node:crypto";

// Strip per-render volatile content from DOM before hashing so the
// fingerprint reflects STRUCTURE not session-state. Without this,
// every run reads [STALE: DOM changed] because ULIDs/timestamps/cookies
// bake into every render. (Council #4 RA-1 fix.)
function normalizeDomForFingerprint(html) {
  return html
    .replace(/\b[0-9A-HJKMNP-TV-Z]{26}\b/g, "<ULID>")           // ULID
    .replace(/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi, "<UUID>")
    .replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z?/g, "<TS>")  // ISO
    .replace(/\b\d{13,}\b/g, "<EPOCH>")                          // epoch ms
    .replace(/data-(event-id|session-id|render-key|nonce)="[^"]*"/g, '$&="<VOL>"')
    .replace(/jarvis_auth=[^;"\s]+/g, "jarvis_auth=<COOKIE>")
    .replace(/csrfToken="[^"]*"/g, 'csrfToken="<TOKEN>"');
}

export async function getStamp(page, opts = {}) {
  const git_head = execSync("git rev-parse --short HEAD").toString().trim();
  const bundle_hash = await fetch("https://corgflix.duckdns.org/tutor/")
    .then(r => r.text())
    .then(t => t.match(/index-([A-Za-z0-9_-]+)\.js/)?.[1] ?? "unknown");
  const live_dom_fingerprint = page
    ? createHash("sha256")
        .update(normalizeDomForFingerprint(await page.content()))
        .digest("hex")
        .slice(0, 16)
    : null;
  return {
    git_head,
    bundle_hash,
    live_dom_fingerprint,
    ts_utc: new Date().toISOString(),
    surface_version: process.env.SURFACE_VERSION ?? "unknown",
    // Judge-side provenance (Council #4 DE fix — MLflow/W&B standard fields).
    // Findings produced under different judge models or prompt versions must
    // not silently conflate.
    judge_model_resolved: opts.judge_model_resolved ?? null,
    judge_prompt_sha256: opts.judge_prompt_sha256 ?? null
  };
}
```

All surfaces emit `getStamp()` result as YAML frontmatter of their findings doc. Surface CLIs pass `judge_model_resolved` (resolved post-`:free` fallback from the OpenRouter completion's `model` field in the response) and `judge_prompt_sha256` (sha256 of the system+user prompt template) to `getStamp()`.

### Stale-check reader

```bash
node tools/findings-stale-check.mjs docs/standin-findings/DRAFT-X-<sid>-<ts>.md
```

Compares stamp against current state. Output:

```
git_head: 4a7fe94 → CURRENT dc15613  [STALE: HEAD advanced 1 commit]
bundle_hash: B-Xy35Ve → CURRENT B-Xy35Ve  [OK]
live_dom_fingerprint: abc123 → CURRENT def456  [STALE: DOM changed]
ts_utc: 2026-05-13T17:55Z (1h ago)  [FRESH]
surface_version: x-v1.0  [OK]
Overall: [STALE] — verify before acting on findings
```

### Quota-isolation verification (BLOCKING prereq)

```bash
node tools/verify-openrouter-quota-isolation.mjs
```

Procedure:
1. Generate 2 OpenRouter API keys on Alex's account.
2. Key A: burn 100 free-tier model calls in tight loop.
3. Key B: try same model immediately after.
4. If Key B → 429: account-level quota IS shared. Verdict: SHARED. Automation forbidden during study hours. Y stays manual-only and is run only after Alex's last study session of the day.
5. If Key B → 200: account-level quota IS isolated per key. Verdict: ISOLATED. Automation OK with per-key rate limits.
6. Verdict written to `docs/notes/2026-05-13-openrouter-quota-isolation.md`.

**Until this doc exists with a verdict line, none of the surfaces ship beyond local prototype.**

### TutorEventLog.kt — backend envelope-capture

```kotlin
// src/main/kotlin/jarvis/tutor/TutorEventLog.kt
package jarvis.tutor

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock

@Serializable
data class TutorEvent(
    val event_type: String,           // "drill_grade" | "sidekick_ask" | "page_nav"
    val event_id: String,             // ULID
    val ts_utc: String,
    val task_id: String?,
    val session_id: String,           // cookie-derived hash
    val prompt_template_id: String?,
    val system_prompt_sha256: String?,
    val retrieved_context_summary: List<String>?,
    val llm_input_full: String?,      // For sidekick_ask: full prompt OK. For drill_grade: see redaction note.
    val llm_input_redacted: RcodeRedacted? = null,  // Council #4 RA-secondary fix: when event_type=drill_grade, R-code answer body is hashed + truncated; raw body never stored
    val llm_output_full: String?,
    val model_resolved: String?,      // post-`:free` fallback resolution
    val tokens_in: Int?,
    val tokens_out: Int?,
    val latency_ms: Long?,
    val status: String,
    val is_synthetic: Boolean = false  // Council #4 RA-2 fix: marks Y-driven synthetic events; Surface X replay skips these when grading real-user invariants
)

@Serializable
data class RcodeRedacted(
    val rcode_sha256: String,         // full hash of submitted R-code body
    val preview_head: String,         // first 40 chars
    val preview_tail: String,         // last 40 chars
    val length_chars: Int
)

object TutorEventLog {
    private val LOCK = ReentrantLock()  // SEPARATE from existing LogAppender
    private val queue = Channel<TutorEvent>(capacity = 1024)
    private val privateDir = File("/opt/jarvis/data/private").also {
        it.mkdirs()
        // chmod 0700
    }

    init {
        kotlinx.coroutines.GlobalScope.launch {
            for (evt in queue) writeOne(evt)
        }
    }

    fun append(evt: TutorEvent) {
        if (!queue.trySend(evt).isSuccess) {
            System.err.println("[tutor-event-log] queue saturated, dropping ${evt.event_id}")
        }
    }

    private fun writeOne(evt: TutorEvent) {
        LOCK.lock()
        try {
            val today = LocalDate.now().toString()
            val file = File(privateDir, "tutor_events.$today.jsonl")
            file.appendText(jsonEncode(evt) + "\n")
        } finally {
            LOCK.unlock()
        }
    }

    fun rotateAndRetain() {
        // Delete files older than 14 days. Called by daily cron OR systemd-tmpfiles.
    }
}
```

Hook points in `TutorRoutes.kt`:

- `/api/v1/drill/grade` → `TutorEventLog.append(TutorEvent(event_type="drill_grade", llm_input_full=null, llm_input_redacted=RcodeRedacted(hash, head40, tail40, len), is_synthetic=request.header("X-Standin-Run") == "1", ...))`. R-code answer body NEVER stored raw — hash + 40-char head + 40-char tail + length only. Closes the credential-grade backup-boundary leak path (Council #4 RA-secondary fix).
- `/api/v1/sidekick/ask` → `TutorEventLog.append(TutorEvent(event_type="sidekick_ask", llm_input_full=fullText, is_synthetic=request.header("X-Standin-Run") == "1", ...))`. Sidekick selections + questions are not credential-grade (already part of the tutor's chat surface); keep raw.

`is_synthetic` set TRUE when the inbound request carries the header `X-Standin-Run: 1`. Surface Y MUST set this header on every Playwright-driven request. Surface X's replay logic filters synthetic events out by default when grading real-user invariants; explicit `--include-synthetic` flag opt-in for grading Y's own runs.

V2 deferred: SPA nav events via beacon endpoint.

### File / directory layout

```
jarvis-kotlin/
├── src/main/kotlin/jarvis/tutor/
│   └── TutorEventLog.kt                          # NEW
│
├── tools/
│   ├── lib/
│   │   └── provenance.mjs                        # NEW
│   ├── surface-x.mjs                             # NEW
│   ├── surface-z.mjs                             # NEW
│   ├── surface-y.mjs                             # NEW
│   ├── findings-stale-check.mjs                  # NEW
│   └── verify-openrouter-quota-isolation.mjs     # NEW
│
├── docs/
│   ├── superpowers/specs/
│   │   └── 2026-05-13-student-standin-design.md  # this spec
│   ├── standin-findings/                         # NEW
│   │   ├── DRAFT-X-<sess>-<ts>.md
│   │   ├── DRAFT-Z-<sess>-<ts>.md
│   │   ├── DRAFT-Y-<task>-<ts>.md
│   │   ├── golden/
│   │   │   └── 2026-05-13-bootstrap-traces.md    # FP ground-truth
│   │   ├── schemas/
│   │   │   └── PS-Tema-A.yaml                    # Y concept schema
│   │   └── screenshots/                          # gitignored
│   └── notes/
│       └── 2026-05-13-openrouter-quota-isolation.md  # verification verdict
│
└── /opt/jarvis/data/private/                     # VPS only, gitignored
    ├── tutor_events.YYYY-MM-DD.jsonl
    └── (14-day retention)
```

### .gitignore additions

```gitignore
# Student stand-in surfaces (sensitive payload + ephemeral)
/opt/jarvis/data/private/
docs/standin-findings/screenshots/
docs/standin-findings/DRAFT-*.md
```

Findings docs commit only AFTER human-review gate. Quarantined drafts stay local.

### Ship-gate matrix

| Surface | Prerequisite | First-ship gate |
|---------|-------------|-----------------|
| Pre-flight | — | quota-isolation verdict + provenance + stale-check + TutorEventLog deployed |
| X | Pre-flight done | LLM-judge ≥ 80% agreement on 15-25 golden fixture |
| Z | X shipped | quota verdict permits Playwright LLM runs |
| Y | X+Z shipped | separate OR API key (or offline window if SHARED verdict) enforced |

## Risks + mitigations (consolidated from 3 councils)

| Risk | Severity | Source | Mitigation |
|------|----------|--------|------------|
| Quota-cascade collapse — surfaces share live grader+sidekick quota | CRITICAL | RA round 1 | Pre-flight quota verification BLOCKING. Separate `OPENROUTER_API_KEY_STANDIN`. Manual-only Y. `/api/v1/health` probe between LLM calls. |
| Findings become zombie advice when site changes underneath | CRITICAL | DA round 2 | Provenance stamp on every finding. `findings-stale-check.mjs` flags `[STALE]` at read. |
| Trace-grader unfalsifiable without labeled ground truth | CRITICAL | FP round 3 | 15-25 hand-curated golden fixtures. ≥ 80% LLM-judge-vs-Alex agreement gate. |
| PII / answer-key leakage in event log | CRITICAL | RA round 3 | `/opt/jarvis/data/private/` (0700, gitignored). Payload allowlist. Separate appender+lock. 14-day rotation. |
| Persona naivety leakage — model performs naivety, reasons from full knowledge | HIGH | DE round 1, Milička 2024 PLoS One | MathVC external schema-gate (3-strike regenerate + log violation). Confusion-tuples from Generative Students. Weak `:free` model. |
| Fake-bug pollution wastes pre-finals attention | HIGH | RA round 1 | Quarantined `DRAFT-*.md`. Zero auto-commit. Explicit human-review gate. |
| LOCK contention on existing activity-log appender | MEDIUM | RA round 3 | Separate `ReentrantLock` for TutorEventLog. Async bounded-queue writer. |
| OpenRouter `:free` model fallback grades different model per replay | MEDIUM | DA round 3 | Envelope captures `model_resolved`. Findings cite which model produced the response. Replay grades the captured envelope, not a re-run. |
| Layperson Z critiques on cosmetic taste, not real bugs | MEDIUM | implicit | Programmatic lint pass primes LLM (snake_case / contrast / overflow); LLM extends rather than invents. |
| DOM fingerprint stale-thrash from ULIDs / timestamps / session cookies in `page.content()` | CRITICAL | RA round 4 | Normalize DOM before hashing (strip ULIDs, UUIDs, ISO timestamps, epoch ms, volatile `data-*` attrs, cookie values). `normalizeDomForFingerprint()` in `tools/lib/provenance.mjs`. |
| Judge-side provenance missing — findings under different judge models silently conflated | HIGH | DE round 4 | Add `judge_model_resolved` + `judge_prompt_sha256` to `getStamp()`. MLflow/W&B standard. |
| Stochastic LLM-judge fail-flips ≥80%-agreement gate at N=25 (±0.16 CI) | HIGH | RA round 4 | Judge calls run at `temperature=0` + fixed seed. 3-run majority vote per (trace, invariant). Gate recast as `≥K of N exact-match` (K = ceil(0.80 × N)). |
| Y → real sidekick API call → X replay misattributes Y's synthetic as real user invariant failure | HIGH | RA round 4 | `X-Standin-Run: 1` header on every Y request. `is_synthetic` field on every `tutor_events.jsonl` entry. X replay default skips synthetic; opt-in `--include-synthetic` for grading Y's own runs. |
| Y persona action mid-Z screenshot captures partial render → phantom readability flags | HIGH | RA round 4 | `await page.waitForLoadState('networkidle', { timeout: 5000 })` before each Z piggyback screenshot. |
| R-code answer in `llm_input_full` survives 14-day rotation via VPS backup snapshot → credential-grade leak | HIGH | RA round 4 | Drill_grade events store `RcodeRedacted{hash, head40, tail40, length}` instead of raw R-code body. Other event types keep `llm_input_full` raw. |
| INV-09 / INV-10 latency invariants FAIL every run on `:free` chain fallback cascade → trains Alex to ignore X | CRITICAL | DA + P round 4 | Recast as `status=INFO` informational only. Never count as PASS/FAIL. Surface `latency_p95_ms` field per session for trend visibility. |

## Success metrics (V1)

- **X:** catches ≥ 1 invariant regression Alex hadn't already noticed within first 5 production runs.
- **Z:** snake_case-chip bug (motivating example, `INV-08`) surfaced + queued for fix.
- **Y:** ≥ 1 unknown-unknown discovered per run (UX dead-end, broken affordance, missing scaffolding on a fresh task).
- **All:** zero spurious 429 cascades on live grader / sidekick during stand-in runs (validated via `/api/v1/health` probe logs).

## Visual-presence acceptance criteria

This spec describes CLI-driven features, not paint-mounted UI surfaces. No live URL `data-testid` selectors apply directly. Acceptance anchored to artifact paths instead:

| What user MUST see after V1 ship | How to verify |
|----------------------------------|---------------|
| `tools/lib/provenance.mjs` returns valid stamp object | `node -e "import('./tools/lib/provenance.mjs').then(m=>m.getStamp().then(console.log))"` returns object with all 5 fields populated. |
| `/opt/jarvis/data/private/tutor_events.YYYY-MM-DD.jsonl` exists post-drill-submit | `ssh root@VPS "ls -la /opt/jarvis/data/private/"` shows today's file. `tail -1` shows valid JSON envelope. |
| `docs/standin-findings/DRAFT-X-<sid>-<ts>.md` created after first X run, has provenance frontmatter | `node tools/surface-x.mjs --task <id> && ls docs/standin-findings/DRAFT-X-*.md`. Frontmatter contains all 5 provenance fields. |
| `node tools/findings-stale-check.mjs <path>` outputs status per field | Sample run shows `[OK]` / `[STALE]` / `[FRESH]` per field. |
| `docs/notes/2026-05-13-openrouter-quota-isolation.md` written with verdict | File exists, contains "VERDICT: SHARED" or "VERDICT: ISOLATED". |
| `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md` has 15-25 fixture entries | `grep -c '^### Trace' docs/standin-findings/golden/2026-05-13-bootstrap-traces.md` ≥ 15. |
| X calibration run reaches ≥ 80% agreement on fixtures | `node tools/surface-x.mjs --from-fixture <path> --calibrate` outputs `agreement_pct: ≥0.80`. Below = ship-blocked. |

## Anti-features (NOT in V1)

- Nightly cron of any surface.
- Auto-commit of findings.
- Combined-report rollup (3 separate files only).
- Synthetic-from-production replay (DE V2 pattern).
- Per-call token truncation (quality concern).
- Dev-UI "Dogfood this page" button.
- Event-driven triggers (FP P5: X on attempt-submit, Z on stuck, Y on button).
- Vision-model commitment for Z (text-fallback OK if no `:free` vision resolves).
- Schema auto-bootstrap (Y schemas hand-authored in V1).
- LLM-propose-invariants (hybrid invariant source defers this to expansion phase).
- Combined findings index / rollup.

## V2 backlog (deferred)

- Synthetic-from-production replay — promote real envelope traces into regression fixtures (DE Langfuse pattern).
- Nightly cron (gated on quota-isolation verdict permitting).
- Event-driven triggers (FP P5).
- Combined-report rollup with stale-flagged index.
- LLM-propose-curate invariant expansion (`--propose-invariants` flag).
- Schema auto-generation from PDF parse.
- Vision-capable `:free` model resolution for Z piggyback.

## Dependencies + sequence

1. **Pre-flight (BLOCKING all surfaces):**
   - `tools/verify-openrouter-quota-isolation.mjs` written + run. Verdict in `docs/notes/2026-05-13-openrouter-quota-isolation.md`.
   - `tools/lib/provenance.mjs` written.
   - `tools/findings-stale-check.mjs` written.
   - `TutorEventLog.kt` shipped + deployed to VPS, hook points wired in `TutorRoutes.kt`.

2. **Surface X (first ship):**
   - Hand-curate 15-25 golden fixture traces.
   - `tools/surface-x.mjs` written.
   - Calibration run: ≥ 80% agreement.
   - Optional: `deploy.sh` advisory hook on `RUN_LLM_EVAL=1`.

3. **Surface Z (second ship):**
   - `tools/surface-z.mjs` written (standalone mode first).
   - Programmatic lints implemented.
   - Vision-model resolution verified or text-fallback wired.

4. **Surface Y (third ship):**
   - First schema authored (`schemas/PS-Tema-A.yaml`).
   - `tools/surface-y.mjs` written.
   - Z piggyback wired (default ON).
   - Separate `OPENROUTER_API_KEY_STANDIN` provisioned (or offline-window respected).

## Council triggers (must convene BEFORE proceeding)

Per the council pattern, convene `claude-council-lite` if:

- Y schema-gate filter design needs to change (touches naivety contract).
- Any surface moves from manual-only to automated trigger.
- Adding a new invariant catalog source beyond hybrid (derive + hand + LLM-curate).
- TutorEventLog payload schema changes (PII surface mutation).

## Out of scope

- Multi-user / multi-tenant. Single-Alex only.
- Running surfaces against Anthropic / OpenAI paid APIs. `:free` chain only.
- Replacing Alex's manual dogfood. This complements; it does not substitute.
- Grading Alex's correctness on drills. Surface X grades the SITE's pedagogy contracts, NOT Alex's answers.
- Auto-fixing bugs surfaced by findings. All findings are human-reviewed.
- Cross-task or cross-subject analyses in V1. Per-task runs only.
