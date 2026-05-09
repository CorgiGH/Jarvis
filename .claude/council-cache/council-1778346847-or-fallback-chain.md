# Council review — 1778346847

**Problem:** Tutor chat surface uses `OpenRouterChatLlm` with a single configured free-tier model. When OpenRouter pulls or rate-caps that model (gemini-2.0-flash-exp:free was already pulled mid-session), both `complete()` and `completeWithTools()` throw on `error("openrouter HTTP $status")`. Tutor degrades to legacy `ChatTools.runTurn` path (no tools) until a human edits .env + restarts.

**Proposed approach:** Add `JARVIS_OPENROUTER_FALLBACK_MODELS` env var (CSV). Inside `OpenRouterChatLlm`, parse on construction into `fallbackModels: List<String>`. Both `postChat()` and `completeWithTools()` wrap HTTP send in a loop over `[primary] + fallbacks`. On 404/410/429, log + try next. Other errors propagate. Returned `model` reflects actually-used model. ~30 LOC, single file. Three new tests via existing HttpServer mocking pattern.

**Project context:**
- jarvis-kotlin: solo-dev personal AI tutor + life-OS, Kotlin/JVM Ktor backend on VPS, Vite/React frontend at /tutor/.
- Free-tier OpenRouter only (user feedback rule: no paid APIs).
- Default model: meta-llama/llama-3.3-70b-instruct:free.
- Existing `FallbackLlm` is INTER-provider (Llm interface). New work is INTRA-provider (model-level inside OpenRouterChatLlm).
- Caller `WebMain.kt` already short-circuits to legacy chat on exception — degradation, not full outage.
- `completeWithTools()` runs up to 3 tool-use rounds per user message.

**Timestamp:** 2026-05-09T20:34:07Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: The fallback list is a static env var that has to be edited and restarted exactly when the original model dies — which is the same operational toil this is supposed to eliminate, just with one extra layer. OpenRouter's `:free` tier models churn constantly (that's literally why gemini-2.0-flash-exp vanished), so any list you commit today is a stale list tomorrow; the second model can be just as gone as the first when 429s cascade across the free tier during a rate-limit storm. Worse, you're treating 429 the same as 404 in the fallback loop — but 429 is a back-off signal, not a "this model is dead" signal. Burning through your entire fallback list on a transient rate-limit spike will hammer all your free-tier quotas simultaneously and leave you with zero working models for the cooldown window, which is strictly worse than failing fast on one model and letting the legacy-chat fallback take the turn. You're also silently swapping the model mid-`completeWithTools` round-trip — if model A returned a tool_use block and model B has different tool-calling semantics or rejects the assistant message format, you'll get a confusing mid-conversation failure that's harder to debug than a clean 404.
KEY CONCERN: Conflating "model gone" (404/410, retry next) with "rate limited" (429, back off and fail) means a single free-tier rate-limit spike will torch every fallback in your list in one turn, leaving you globally degraded for the cooldown window — the opposite of the resilience you're buying. Drop 429 from the fallback trigger set, or add a per-model cooldown cache, before this ships.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: The proposal reinvents a small slice of what LiteLLM Router (BerriAI/litellm) and OpenRouter's own native model routing already solve — both expose ordered `models: [...]` fallback lists with status-code-driven failover (429/5xx/timeout) as a first-class feature. OpenRouter specifically supports a `models` array in the chat-completions request body that does server-side fallback in one round trip; using it would be ~5 LOC and zero new failure modes vs. ~30 LOC of client-side loop logic that you now own forever. That said, client-side failover is a legitimate, established pattern (see Polly's `FallbackPolicy`, Resilience4j `Retry` + fallback, AWS SDK's adaptive retry mode, OpenAI Python SDK's `max_retries`) — the standard exists because you sometimes want failover across *providers*, not just models on one provider, and you want local control over which errors trigger it. The proposal's status-code triage (404/410/429 → fallback, others → propagate) matches industry practice; the concern is that 429 in a single-loop-no-backoff design is a known anti-pattern (every LLM client lib eventually adds exponential backoff + jitter because hammering a rate-limited endpoint with the *next* model in the list often just trips its rate limit too).
KEY CONCERN: Evaluate OpenRouter's native `models: [primary, ...fallbacks]` request-body field first — if it covers your failure modes (404 model-pulled + 429 rate-cap), it eliminates the entire client-side code path and the test surface. If you still need client-side (e.g., to log per-model attempts, or because OpenRouter's server-side routing has gaps for `:free` tier), proceed — but treat 429 differently from 404/410 (404/410 = permanent, retry-next-model immediately; 429 = transient, needs at least a short backoff or risk cascading rate-limits across your fallback list).

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: The implementation is genuinely small (~30 LOC, one file, three tests in an established pattern) and it directly addresses a real recurring failure mode — OpenRouter pulled `gemini-2.0-flash-exp:free` mid-session, exactly the scenario this fixes. For a solo dev who already has a working manual .env-edit escape hatch, the question is whether automating it pays for itself; given that free-tier models churn frequently and `WebMain.kt` already silently degrades to a worse legacy path (so the user doesn't even notice the regression immediately), automation here meaningfully reduces "surprise quality drops" between manual fixes. The hidden cost is the fallback list itself — it's now a thing that has to be curated, and a stale fallback model is just as broken as a stale primary, so without discipline it becomes a maintenance multiplier rather than a divider. Returning the actually-used model in the response field is the right call and worth keeping non-negotiable, because without it debugging "why does the tutor feel dumber today" becomes guesswork.
KEY CONCERN: The fallback list is only useful if it's kept fresh — bake the maintenance ritual in now (commit-message convention documenting which models were verified working, plus a one-line log on each fallback hit so stale entries surface in normal use) or this becomes a quietly-rotting config that masks outages instead of routing around them.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: The actual goal is "stay alive when upstream provider has model availability issues," not "iterate a Kotlin list." OpenRouter ships server-side fallback as a first-class feature: pass `models: [primary, ...fallbacks]` in the chat-completions body and OR routes to the first available model in a single HTTP request — no client loop, no per-attempt latency stacking, no duplicated retry logic across `postChat()` and `completeWithTools()`, and tool-call semantics are preserved by the edge. Building a client-side loop reinvents what the upstream already does better, doubles the surface area (two call sites to keep in sync), and means every fallback hop pays a fresh TLS+HTTP round-trip from the VPS. From scratch with no prior art, the answer is: declare your fallback list in the request body and let the routing layer that already knows provider health do the routing.
KEY CONCERN: The user appears unaware that OpenRouter's `models: [...]` field exists — adopting it is ~5 LOC in the request payload builder plus a CSV-parsed env var, strictly less code than the proposed loop, with better latency and a single source of truth for fallback behavior.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: The mid-tool-loop swap is the real failure mode. `completeWithTools()` runs up to 3 rounds; if model A returns a `tool_calls` response in round 1, the tools execute, and then the primary 404s on round 2, you fall through to model B with a conversation history shaped by A's tool-call dialect (assistant message with `tool_calls` field, followed by `role:tool` results). Some OpenRouter free models reject or mishandle that schema — they'll either error on the assistant turn or hallucinate text instead of continuing the tool loop, and now the user sees garbage mid-task instead of clean degradation. Quota-cliff waste (always hitting primary first after a 429) is real but minor — one wasted request per turn at free-tier latency is fine. The 404/410/429-only fall-through gate is correct; expanding it is where you'd get hurt.
KEY CONCERN: HIGH — mid-loop model swap with tool-call history. Mitigation: scope fallback to round 1 only (if `messages` contains any `role:tool` or assistant `tool_calls`, do not switch models — fail to caller and let `WebMain` short-circuit to legacy). Pin the round-1 winner for the rest of the `completeWithTools()` invocation. Add a test that asserts a primary-fails-after-tool-calls scenario throws rather than silently swapping mid-loop.

---

## Sanity Check

SANITY Devil's Advocate: PASS — clean. Names mechanism (rate-limit storm cascade), commits to REJECT, no scope drift.
SANITY Domain Expert: PASS — clean. Names patterns by spec (LiteLLM Router, OR `models:` field, Polly, Resilience4j). Concrete LOC comparison.
SANITY Pragmatist: PASS — clean. Stays in human-cost lane, proposes concrete maintenance ritual.
SANITY First Principles: PASS — clean. Strips framing to "stay alive", names server-side alternative with mechanism.
SANITY Risk Analyst: PASS — clean. Ranks HIGH, names mitigation (pin round-1 winner, scope fallback to round 1).

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: WRONG APPROACH

CORE FINDING:
OpenRouter's chat-completions API accepts a `models: [primary, ...fallbacks]` array in the request body and performs server-side model routing in a single HTTP round-trip. The proposed client-side loop reinvents that feature with strictly worse properties: doubles the call-site surface (postChat + completeWithTools), eats per-attempt latency, owns the 404/429 triage logic forever, and — most damningly — risks mid-tool-loop model swaps where model B inherits a `tool_calls` history shaped by model A's dialect. Three independent agents (Devil's Advocate, Domain Expert, First Principles) converged on this without prompting.

AGENT CONSENSUS: 2 REJECT, 3 CONDITIONAL, 0 APPROVE — 0 flagged. Three agents independently identified OR's native `models:` field as the correct primitive.

KEY ISSUES:
- **Wrong layer (Domain Expert + First Principles).** OR's server-side `models:` array is ~5 LOC of payload change, eliminates the client-side loop, and lets OR's edge router (which actually knows model health) make routing decisions. No client-side per-attempt TLS round-trip stacking.
- **429 ≠ 404 (Devil's Advocate + Domain Expert).** Treating rate-limits identically to permanent removal will cascade through the fallback list during a rate-limit storm, hammering every free-tier quota simultaneously and leaving you globally degraded for the cooldown window. If client-side is kept, 429 must trigger backoff, not next-model.
- **Mid-tool-loop swap is HIGH risk (Risk Analyst).** `completeWithTools()` runs up to 3 rounds. If model A returns tool_calls in round 1 and model B serves round 2, B inherits a conversation history with A's assistant `tool_calls` field + `role:tool` results. Free-tier models vary in tool-call dialect support → silent garbage output mid-task.

RECOMMENDED PATH:
Replace the proposed client-side loop with OR's native server-side routing:

1. **Env var stays the same.** `JARVIS_OPENROUTER_FALLBACK_MODELS` (CSV). User-facing contract unchanged.
2. **Inject into request body.** Both `postChat()` and `completeWithTools()` add `"models": [defaultModel, ...fallbackModels]` to the JSON payload when fallback list is non-empty. OpenRouter's edge handles routing on 404/429/timeout. Single HTTP round-trip per call.
3. **Read actual model from response.** Already done — `parsed.model` field reflects the model OR actually routed to. Surface it in the returned tag.
4. **Tests.** HttpServer mock asserts the `models` array is present in the request body when env var is set, AND that the response's model field is propagated to caller. One test for env-unset (no `models` field), one for env-set + 200 (assert array shape), one for env-set + verifying response model wins.
5. **No client-side retry loop, no triage code, no mid-loop swap risk** — OR's edge owns all of it. ~5–10 LOC change instead of ~30, with strictly better failure properties.

Risks remaining:
- Server-side `models:` field semantics on `:free` tier specifically — confirm via OR docs or one curl probe before committing. If OR documents it as paid-tier-only, fall back to the client-side approach BUT with three required modifications: (a) drop 429 from trigger set (Devil's Advocate's veto), (b) pin model after first successful round of `completeWithTools()` (Risk Analyst's HIGH mitigation), (c) log fallback hits to stderr so stale entries surface in normal use (Pragmatist).
- Pragmatist's "list rot" concern applies to either approach — bake commit-message convention now ("verified <model> + <fallback> respond 200 on YYYY-MM-DD").

CONFIDENCE: 8
What would change this rating: a 30-second curl probe against `https://openrouter.ai/api/v1/chat/completions` with `"models": ["nonexistent/model:free", "meta-llama/llama-3.3-70b-instruct:free"]` to confirm server-side routing actually engages on free-tier. If it doesn't, downgrade to the client-side approach with the three Devil's-Advocate/Risk-Analyst/Pragmatist mitigations and re-run the council on the revised design.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
