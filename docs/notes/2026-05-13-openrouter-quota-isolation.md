# OpenRouter `:free` quota isolation verdict — 2026-05-13

**Model probed:** `google/gemma-4-26b-a4b-it:free`
**Burn N (per key):** N/A — signal arrived on first call per key; burn loop not needed.

## Procedure

Provisioned a second API key (`OPENROUTER_API_KEY_STANDIN`) on the same OpenRouter account as the live `OPENROUTER_API_KEY`. Single ping per key to `:free` endpoint, plus `/api/v1/key` introspection on both.

## Key A — `OPENROUTER_API_KEY` (live grader + sidekick)
- Single ping to `google/gemma-4-26b-a4b-it:free` → HTTP 429
- Error body: `Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model requests per day`
- Response headers: `X-RateLimit-Limit: 50`, `X-RateLimit-Remaining: 0`, `X-RateLimit-Reset: 1778716800000`
- `/api/v1/key` reported `usage_daily: 0.00000822` (small, real prior usage during today's tutor session)
- `creator_user_id`: `user_3Bujes7d1ubmSCUtHBlD4cQaxul`

## Key B — `OPENROUTER_API_KEY_STANDIN` (fresh, provisioned this session)
- Single ping to `google/gemma-4-26b-a4b-it:free` → HTTP 429
- Error body identical to Key A: `free-models-per-day` exhausted
- Response headers: `X-RateLimit-Remaining: 0`
- `/api/v1/key` reported `usage_daily: 0` (key had literally never been called for chat completions)
- `creator_user_id`: `user_3Bujes7d1ubmSCUtHBlD4cQaxul` (same account as Key A)

## VERDICT: SHARED

Both keys returned the same `free-models-per-day` 429 with `X-RateLimit-Remaining: 0`, despite Key B having `usage_daily: 0` per the key-introspection endpoint. The 50-req/day free-tier quota is enforced at the **account level**, not per-key. Provisioning additional API keys on the same account does not multiply the daily budget.

## Implications for student stand-in (Surface Y)

Separate keys SHARE account-level daily quota on the `:free` tier. Surface Y MUST be manual-only and MUST run only OUTSIDE Alex's study window (recommend 03:00-06:00 local). The standin key burning quota WILL drain the live grader+sidekick's available calls. Treat all automation as gated on a daily refill window.

Concrete operating constraints:
- 50 reqs/day across the entire account. Tutor sidekick + drill-grader + any Surface Y burn share this pool.
- Surface Y `STANDIN_HARD_CAPS` in spec must respect this, or quota will be drained before live use.
- Alternative if surface Y runs become routine: top up 10 USD on OpenRouter → 1000 reqs/day (Council recommended descope before this; user durable pref = no paid APIs).
- Alternative for Y: route Y through a non-OpenRouter LLM (claude-max subprocess, copilot-cli) and reserve `:free` for live grader+sidekick only.

## Notes on the verifier itself

First pass of `tools/verify-openrouter-quota-isolation.mjs` had three latent bugs, fixed this session:
1. Default model `qwen/qwen-2.5-7b-instruct:free` is no longer a live `:free` endpoint (404 "No endpoints found"). Default updated to `google/gemma-4-26b-a4b-it:free`.
2. Verdict logic treated any "Key B ok == 0" as SHARED, including the case where the model 404'd on both keys (zero 429s, zero OKs). Added `INDETERMINATE` verdict tier when `a.rate_limited == 0 && b.rate_limited == 0`.
3. Output path was relative to CWD. When run from `tools/`, the verdict landed in `tools/docs/notes/…` instead of `docs/notes/…`. Path now resolved against `REPO_ROOT` derived from `import.meta.url`.

## Reproduction (post-fix)

```bash
OPENROUTER_API_KEY=<live> OPENROUTER_API_KEY_STANDIN=<standin> \
  node tools/verify-openrouter-quota-isolation.mjs
```

Re-run quarterly OR when OpenRouter announces account/quota structure changes. Verdict is point-in-time; the live VPS state at run time is the authoritative answer.
