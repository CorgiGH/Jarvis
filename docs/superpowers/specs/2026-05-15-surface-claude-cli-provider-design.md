# Surface Claude-CLI provider + Surface Y naivety-hardening — design (Spec B)

**Date:** 2026-05-15
**Status:** Spec — pending implementation
**Councils (binding):**
- `.claude/council-cache/council-1778785261.md` — decouple X-fixture seeding from the stochastic persona; seed deterministically. (Closed by Spec A v2.)
- `.claude/council-cache/council-1778787266.md` — Surface Y is friction-discovery ONLY; drill-completion is incidental, not a success metric; behavioral-competence tripwire mandatory.
- `.claude/council-cache/council-1778839098-y-competence-band.md` — WRONG APPROACH on the (a)/(b)/(c) model-band menu. **Unanimous REJECT on Claude-CLI-for-Y.** Convergent on (b)+(c) hybrid: gpt-oss-120b:free pinned primary + named `:free` fallback list. **Behavioral tripwire is critical-path; must be spec'd here, not punted to execution.**

## Why this spec exists

Two motivating problems are solved together because they share scope:

1. **The Claude CLI is available** (`/c/Users/User/.local/bin/claude`, v2.1.141) and rides the user's existing Claude Code subscription pool — it is NOT paid-API spend per the `:free` rule, but it IS a real resource (Alex shares this pool with his finals-study coding sessions). The codebase has no provider for it. Surface X (trace-grader) + Surface Z (visual critic) currently route through OpenRouter `:free`; both could benefit from Claude's stronger judgment on bounded call counts. Other tools (sub-personas, brainstorm-rerun-on-CLI, future review tooling) want it too. A single, reusable `tools/lib/claude-cli.mjs` mirrors the existing `tools/lib/openrouter.mjs` API.

2. **Surface Y's naivety-leakage mitigation stack lost a load-bearing leg** when the v1 student-standin spec admitted (line 786, risk table) that "weak `:free` model" was one of three required mitigations against behavioral hyper-competence — and council-1778787266 made the behavioral-competence tripwire mandatory to replace it. The latest council (`1778839098`) added one critical constraint: **the tripwire must be defined (thresholds, calibration corpus, aggregation policy) before Y ships on any model.** If the tripwire is hand-waved, every band choice becomes a fail-open gate that won't fire until finals.

The shared scope is "what becomes of Y's persona substrate now that we have CLI access?" The two pieces resolve together: the CLI provider is built (for X/Z/sub-personas), and Y is *explicitly excluded* from it.

## Verified facts (2026-05-15)

The recon already done this session, captured here to avoid future ghost-component citations:

- **Claude CLI present:** `which claude` → `/c/Users/User/.local/bin/claude`; `claude --version` → `2.1.141 (Claude Code)`. Invocation from inside Node child process supported (`spawn("claude", ["--print", ...], {stdio:["pipe","pipe","pipe"]})`).
- **OpenRouter `:free` `gpt-oss-120b` is live and usable as Surface Y's pin:** live probe to `openrouter.ai/api/v1/chat/completions` with `OPENROUTER_API_KEY_STANDIN` (`.env`, gitignored) responded `HTTP 200` with `provider:"OpenInference"` and model `openai/gpt-oss-120b:free`.
- **`tools/lib/openrouter.mjs` is the reference pattern.** Signature: `callLlm({apiKey, model, systemPrompt, userPrompt, temperature, seed, maxTokens, transport, maxRetries, baseDelayMs, sleep})` → `{text, model_resolved, prompt_sha256, tokens_in, tokens_out, latency_ms}`. The CLI provider mirrors this — same input shape, same output shape, no semantic delta at the call site.
- **Surface Y `surface-y.mjs:66` already pins `openai/gpt-oss-120b:free` as default.** No code change needed for the model pin itself — only the addition of a fallback list and the tripwire postprocessor.
- **The 3 prior Y runs (gpt-oss-120b authentic-naive, minimax config-timeout, deepseek incoherent)** are the closest thing to a calibration corpus we have. Only the gpt-oss-120b run carries authentic-naive signal; the other two are invalid (minimax timeout = config bug, deepseek = below floor).
- **Surface Y burns `(maxRegens+1) * maxCallsPerSession` = up to 150 calls per run** (defaults: `maxRegens=2`, `maxCallsPerSession=50`). Quota isolation via `OPENROUTER_API_KEY_STANDIN` (separate from prod `OPENROUTER_API_KEY`).

## Unverified — flagged for first-run confirmation

- **`claude --print` exit-code semantics under quota throttle.** The Anthropic subscription rate-limit pool, when exhausted, may surface as a non-zero exit, a stderr-only error, or a 429-equivalent JSON envelope on stdout. The provider's error mapping must handle all three; first-run live probe will pick the actual shape.
- **`claude --print` token accounting.** OpenRouter returns `usage.prompt_tokens` + `usage.completion_tokens` in the JSON response. Claude CLI's `--print` may or may not emit usage in the same shape. Provider returns `null` for `tokens_in/out` if unavailable; this is acceptable per the existing OpenRouter return contract (`tokens_in/out` already `?? null`).
- **The behavioral tripwire's calibration corpus.** Defined here as the gpt-oss-120b authentic-naive run; first live Y run under Spec B's pinned config grows the corpus to n=2. Threshold values below are the spec's starting point — they may need adjustment after the first 2-3 runs.

## Scope (what's IN)

1. **`tools/lib/claude-cli.mjs`** — subprocess wrapper for `claude --print`, API-compatible with `tools/lib/openrouter.mjs`. Lives in `tools/lib/` (a peer to `openrouter.mjs`, not inline per `seed-tutor-events.mjs`'s pattern) because it has ≥2 consumers (Surface X + Surface Z) from day one.
2. **Surface X (`tools/surface-x.mjs`) opt-in CLI provider routing.** A new `--provider=openrouter|claude-cli` flag (default `openrouter`) selects the provider at runtime. Default unchanged so the existing Spec A flow is not disturbed.
3. **Surface Z (`tools/surface-z.mjs`) opt-in CLI provider routing.** Same flag shape as X.
4. **Surface Y `:free` fallback list.** A new `--fallback-models=<csv>` flag (default empty) on `surface-y.mjs`. When provided, the OpenRouter call wrapper attempts the primary; on a non-retryable failure (daily-quota 429 OR HTTP 5xx after retries) it advances to the next model in the list. NEVER includes any `claude-*` model — the fallback path is locked to the `:free` band per the council verdict.
5. **`tools/surface-y-tripwire.mjs`** — transcript postprocessor. Pure function: `flagSuspectRun(transcript) → { suspect, signals, thresholds, rationale }`. Emits to DRAFT-Y frontmatter (`tripwire_status: clean|suspect`) AND to a dedicated `## Behavioral-competence tripwire` section in the finding doc.
6. **Provenance stamping** — `tools/lib/provenance.mjs` `getStamp()` gains a `provider_name` field (defaults `null`; X/Z/Y populate when known) so DRAFT files record which provider was used.

## Scope (what's OUT — anti-features)

- **Claude-CLI as Surface Y's persona provider.** Council unanimous REJECT. Not buildable as an opt-in toggle either — the comment guarding `surface-y.mjs:62-65` ("Standin key only — NO fallback to OPENROUTER_API_KEY") is reinforced; the Y persona's provider is hard-wired to OpenRouter, full stop.
- **Up-banding fallbacks beyond `:free`.** The fallback list is enforced as a `:free`-band-only allow-list (each entry must end in `:free`).
- **Live blocking tripwire.** The tripwire is diagnostic — it emits a flag in the finding doc, never aborts a run, never gates downstream consumption.
- **Auto-calibration of tripwire thresholds.** Thresholds are spec-defined (below). First-pass values; future tuning is a documented manual step.
- **Migration of X/Z away from OpenRouter as default.** Default provider stays OpenRouter for cost predictability + zero subscription-pool collision with Alex's coding.
- **`claude-cli` model selection beyond CLI defaults.** The provider accepts `model` as a pass-through to the CLI's `--model` flag where supported; no client-side model registry.
- **Subprocess pool / parallelism.** First version is strictly sequential — one `claude --print` invocation at a time per process — so blast radius on subscription-pool collision is bounded.
- **Token budget enforcement inside the provider.** Caller is responsible for capping its own call count. Provider exposes `latency_ms` but does not refuse to call.

## The behavioral-competence tripwire (precondition section per council `1778839098`)

The single most important clarification this spec must make. Council was explicit: define this before the band pick, not after. The model-band decision (gpt-oss-120b:free pinned) is *contingent* on this tripwire being live.

### Signals (computed from a finished Y transcript)

```
ask_sidekick_count       = transcript.filter(t => t.action === "ask_sidekick").length
confusion_step_count     = transcript.filter(t => {
                              const obs = (t.observation || "").toLowerCase();
                              return obs.includes("confused") || obs.includes("don't know")
                                  || obs.includes("unclear") || obs.includes("[leak?]")
                                  || obs.includes("stuck") || t.action === "give_up"
                                  || t.action === "ask_sidekick";
                           }).length
first_action_was_submit  = transcript[0]?.action === "submit"
submit_before_step_4     = transcript.findIndex(t => t.action === "submit") in [0,1,2,3]
                            && transcript.slice(0,4).every(t => t.action !== "ask_sidekick"
                                                            && t.action !== "give_up"
                                                            && !t.error)
zero_payload_iterations  = transcript.filter(t => t.action === "type"
                              && (t.payload?.length || 0) < 10).length
                              < transcript.filter(t => t.action === "type").length
```

### Thresholds (v1 — calibrated against gpt-oss-120b's authentic-naive run)

A run is **`suspect`** if ANY of:
- `ask_sidekick_count === 0` AND `confusion_step_count === 0` AND transcript length ≥ 6
- `submit_before_step_4 === true`
- `zero_payload_iterations === false` AND `confusion_step_count === 0`
  (the persona typed clean, full-length attempts every time with no hesitation)

Otherwise: **`clean`**.

Rationale:
- gpt-oss-120b's prior run: never invoked `ask_sidekick` (the persona was instructed to use it; a real Claude-tier model in the same prompt frame WOULD use it less, but a model that uses it ZERO across 6+ steps is suspect). It did show confusion (typed real R, stalled). Threshold = 0 catches the silent-glide failure.
- A first-try `submit` at step ≤3 with zero hesitation/ask_sidekick is the canonical "Claude already knew the answer" trace.
- A model that types complete, polished, full-length attempts every iteration with no half-formed scratch is producing answers, not exploring.

### Calibration corpus (v1)

- **n=1 authentic-naive:** the gpt-oss-120b prior run transcript (from prior session's draft, archived).
- **n=0 hyper-competent reference:** synthetic, hand-authored — a fixture in `tools/surface-y-tripwire.test.mjs` that mocks what a Claude-CLI Y run WOULD produce (zero `ask_sidekick`, submit at step 2, full R answer first try). Used to verify the tripwire FIRES on its target failure mode.

Future Y runs under Spec B grow the corpus: each `clean` run is added as a positive sample, each `suspect` run is reviewed.

### Aggregation policy

- **Single-run:** soft flag in the DRAFT-Y frontmatter (`tripwire_status: suspect`) and a `## Behavioral-competence tripwire` section that lists the signal values + rationale.
- **N-run:** out of scope for v1. If 2 consecutive Y runs trip `suspect`, that is a manual review trigger — documented but not automated.

### Acceptance for the tripwire

- Unit test (fixture `tools/surface-y-tripwire.test.mjs`) shows: authentic-naive transcript → `clean`; synthetic hyper-competent transcript → `suspect`.
- Live Y run under the pinned model emits a `tripwire_status` line in DRAFT-Y.md frontmatter AND a populated `## Behavioral-competence tripwire` section.

## Component layout

```
tools/
  lib/
    claude-cli.mjs              NEW — callLlm({...}) → {text, model_resolved, ...}
    openrouter.mjs              unchanged
    provenance.mjs              MODIFY — getStamp() gains provider_name field
  surface-x.mjs                 MODIFY — --provider flag routes to lib/claude-cli or lib/openrouter
  surface-x.test.mjs            MODIFY — add tests for --provider flag
  surface-z.mjs                 MODIFY — --provider flag (same shape as X)
  surface-z.test.mjs            MODIFY — add tests
  surface-y.mjs                 MODIFY — add --fallback-models CSV flag; route to fallback list
                                         when primary fails non-retryably; call tripwire at end
  surface-y.test.mjs            MODIFY — add fallback-routing tests
  surface-y-tripwire.mjs        NEW — flagSuspectRun(transcript) → { suspect, signals, ... }
  surface-y-tripwire.test.mjs   NEW — fixtures for authentic-naive + synthetic hyper-competent
```

## Data flow

```
Surface X / Z (capability-wanted tools)
  --provider=claude-cli   → lib/claude-cli.mjs.callLlm  → spawn("claude", ["--print", ...])
                                                          → parse stdout → {text, ...}
  --provider=openrouter (default) → lib/openrouter.mjs.callLlm (unchanged)

Surface Y (naivety-required tool)
  apiKey=process.env.OPENROUTER_API_KEY_STANDIN  (UNCHANGED — never lib/claude-cli)
  model="openai/gpt-oss-120b:free"  (pinned default — UNCHANGED)
  fallback list (NEW):
    [ "openai/gpt-oss-120b:free" (primary)
    , "<:free-mid-tier-1>"
    , "<:free-mid-tier-2>"
    ]
  On primary 429-daily / 5xx-after-retries → advance to next model in list.
  After run completes → flagSuspectRun(transcript)
                        → DRAFT-Y frontmatter: tripwire_status: clean|suspect
                        → DRAFT-Y body: ## Behavioral-competence tripwire section
```

## Env + CLI contract

```
ANTHROPIC_API_KEY        IGNORED — Claude CLI uses its own subscription pool
CLAUDE_CLI_BIN           optional — override path to claude binary (default: "claude" in PATH)
CLAUDE_CLI_TIMEOUT_MS    optional — per-invocation timeout (default: 120000)

surface-x.mjs:
  --provider=openrouter|claude-cli   default openrouter
  (existing flags unchanged)

surface-z.mjs:
  --provider=openrouter|claude-cli   default openrouter
  (existing flags unchanged)

surface-y.mjs:
  --fallback-models=<csv>            default empty (no fallback)
                                     each entry must end in ":free"
                                     example: openai/gpt-oss-120b:free,deepseek/deepseek-chat-v3.1:free
  (provider is NEVER selectable — always OpenRouter)
```

## Risks + mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Behavioral hyper-competence leakage if Y ever runs on Claude-CLI | CRITICAL | The provider is hard-wired to OpenRouter inside `surface-y.mjs`. No `--provider` flag on Y. The CLI provider is structurally inaccessible to Y. Council `1778839098` verdict bakes this in. |
| Subscription-pool collision during finals (Jun 1-21) | HIGH | (a) Default provider on X+Z is `openrouter` — opting into `claude-cli` is a per-run flag, never the default. (b) Provider is strictly sequential (no parallel children). (c) `CLAUDE_CLI_TIMEOUT_MS` cap (120s default). |
| `claude --print` output format drifts across CLI versions | MEDIUM | Provider records `cli_version` in returned envelope. `model_resolved` is parsed from `--model` echo + `cli_version` fingerprint; if parse fails, `model_resolved = "claude-cli@${cli_version}"`. |
| Tripwire calibration overfit on n=1 | HIGH | Documented explicitly in this spec. v1 thresholds are conservative-leaning (require ≥6 steps before flagging on the zero-confusion path; require a clean step-0..3 submit chain). First 2-3 Y runs grow the corpus. Single-run `suspect` is a SOFT flag, not blocking. |
| Tripwire fails open on a subtle Claude leak the heuristic doesn't catch | HIGH | The tripwire is belt-and-suspenders, not the sole defense. The substrate-level mitigation (Y locked to `:free`) is the load-bearing leg. The tripwire catches the gross failures + serves as a regression signal. |
| Fallback list silently up-bands to Claude | CRITICAL | `surface-y.mjs` parses `--fallback-models` and rejects (loud exit) any entry not ending in `:free`. Unit-tested. |
| `claude --print` returns malformed JSON-mode output | LOW | Provider uses non-JSON `--print` plain-text mode (OpenRouter already returns plain text in the choices[].message.content shape; the provider returns the same shape). No JSON-mode dependency. |

## Success metrics (v1)

- **CLI provider:** `tools/lib/claude-cli.mjs` exists; `callLlm` returns the same return-shape as `openrouter.mjs`; unit tests pass; one live invocation against the real CLI succeeds (recorded in this spec's acceptance run).
- **X+Z opt-in:** running `node tools/surface-x.mjs --provider=claude-cli --task=<id>` succeeds end-to-end on a live task (or fails loudly with a clear error if the CLI is unavailable). Default-no-flag invocation produces identical results to pre-spec.
- **Y fallback list:** running `node tools/surface-y.mjs --task=<id> --schema=<p> --fallback-models=<csv-with-non-free-entry>` exits non-zero with a clear "fallback list contains non-`:free` model" error. Valid `:free`-only list is accepted.
- **Y tripwire:** every DRAFT-Y-*.md emitted from a Y run includes `tripwire_status: clean|suspect` in frontmatter AND a populated `## Behavioral-competence tripwire` section. The synthetic hyper-competent fixture transcript trips `suspect`.

## Acceptance gates (feature-shipped verification)

- `npm run test:tools` → all tests green (existing 84/84 plus the new tripwire/provider/fallback tests).
- Live X invocation with `--provider=claude-cli` against a real task → DRAFT-X.md emitted, frontmatter records `provider_name: claude-cli`.
- Live Y invocation under default config → DRAFT-Y.md emitted, frontmatter records `tripwire_status` AND the body has the tripwire section. (If `tripwire_status: suspect`, that's a real finding to surface, NOT a test failure.)

## V2 backlog (deferred)

- N-run tripwire aggregation (consecutive `suspect` → manual review trigger).
- Tripwire threshold tuning UX (a CLI to re-run the postprocessor against an archived transcript with adjusted thresholds).
- Subprocess pool for the CLI provider (parallel runs).
- Native `usage` parsing from `claude --print` (when CLI emits it in a stable shape).
- `claude --print --output-format=json` mode if the CLI ever adds one.
- Migrating Surface Z's vision-piggyback to a Claude CLI multimodal path.
- Auto-fallback model resolution from OpenRouter's `:free` listings.

## Open questions for v1 implementation

None blocking. The two `Unverified` items above (CLI exit-code shape under quota, CLI usage emission) get resolved by the first live invocation during execution; the implementation handles both possibilities defensively.
