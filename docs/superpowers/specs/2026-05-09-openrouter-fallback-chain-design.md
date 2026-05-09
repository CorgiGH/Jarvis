# OpenRouter fallback chain design

**Date:** 2026-05-09
**Status:** Spec — pending implementation
**Council:** `.claude/council-cache/council-1778346847-or-fallback-chain.md` (verdict: WRONG APPROACH on initial proposal → revised per recommended path; see Probe Findings).

## Problem

Tutor chat surface uses `OpenRouterChatLlm` with a single configured free-tier model. When OpenRouter pulls or rate-caps that model, both `complete()` (text-only `Llm` interface) and `completeWithTools()` (tool_use round-trip used by `JarvisToolset` for Stages A–D) throw on `error("openrouter HTTP $status: $body")`.

Caller `WebMain.kt:288–292` short-circuits to legacy `ChatTools.runTurn` on any throw — degradation, not full outage — but the user loses tool access mid-conversation with no signal.

**Live priority:** the 2026-05-09 probe (see Probe Findings) showed `meta-llama/llama-3.3-70b-instruct:free` is currently 429-rate-limited upstream by Venice provider with `Retry-After: 11s`. Tutor is degraded right now until quota refreshes.

## Probe findings (2026-05-09)

Three curl probes against `POST openrouter.ai/api/v1/chat/completions` from VPS using existing `OPENROUTER_API_KEY`:

1. **`models: ["fake/nonexistent-model:free", "meta-llama/llama-3.3-70b-instruct:free"]`** → HTTP 400 `"fake/nonexistent-model:free is not a valid model ID"`. **Finding:** OR validates every entry in `models:` array before routing. Invalid model IDs in fallback list reject the entire request.

2. **`models: ["meta-llama/llama-3.3-70b-instruct:free", "mistralai/mistral-small-3.1-24b-instruct:free"]`** → HTTP 429 from llama (Venice upstream rate-limit, `Retry-After: 11s`). Mistral never tried. **Finding:** OR's server-side `models:` array does NOT failover on 429. The first listed model's failure is propagated to the client even when alternates are listed.

3. **`models: ["google/gemini-2.0-flash-exp:free", "meta-llama/llama-3.3-70b-instruct:free"]`** → HTTP 429 from llama (same upstream issue). Gemini was skipped (likely 404'd at edge), llama tried, llama 429'd. **Finding:** OR's `models:` DOES failover on 404 (model-pulled / model-removed) — the gemini→llama transition happened server-side. So permanent removals are handled by edge routing; transient rate-limits are not.

**Conclusion:** OR `models:` is the right primitive for the original-motivating-incident class (gemini-2.0-flash-exp:free pulled mid-session = HTTP 404 at edge). 429 (rate-limit) needs a separate client-side path.

## Approach

Two-layer defense:

1. **Server-side `models:` array** — for 404/410/permanent-removal. OR's edge router decides routing in a single HTTP round-trip. Zero client-side iteration, no per-attempt latency stacking.

2. **Client-side `Retry-After` honor** — for 429/transient rate-limit. Parse `Retry-After` header. If ≤ 5 seconds, sleep + retry same request once. If > 5 seconds OR a second 429 follows, propagate so caller's legacy chat path serves the turn.

Explicitly **not** in V1:
- Mid-tool-loop model pinning (Risk Analyst HIGH risk). Deferred to V2 — see Deferred Risks.
- Per-model 429 cooldown cache (Devil's Advocate concern about cascading rate-limits across fallbacks). Single retry per request prevents cascade adequately at single-user scale.
- Iterating the fallback chain on 429. Council-vetoed (Devil's Advocate, Domain Expert): would torch all free-tier quotas during a rate-limit storm.

## Env contract

```
JARVIS_OPENROUTER_FALLBACK_MODELS="model-id-1,model-id-2,model-id-3"
```

- Comma-separated OpenRouter model IDs.
- Whitespace trimmed per element. Empty entries dropped.
- Unset or empty string → no behavior change (chain is `[defaultModel]`, plain `model:` field used).
- Validated **lazily**: invalid IDs surface as HTTP 400 from OR on first request, not at construction (we don't pre-flight; that adds startup cost and another failure mode).

## Code changes — `OpenRouterChatLlm.kt` only

### Constructor field

```kotlin
private val fallbackModels: List<String> = parseFallbackModels()

private fun parseFallbackModels(): List<String> =
    System.getenv("JARVIS_OPENROUTER_FALLBACK_MODELS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
```

### Helper — model field selection

```kotlin
private fun JsonObjectBuilder.putModelField(modelOverride: String?) {
    if (modelOverride != null) {
        // Explicit override — caller pinning a specific model. Honor it; ignore fallback chain.
        put("model", modelOverride)
    } else if (fallbackModels.isEmpty()) {
        // No fallback configured — single-model mode (status quo).
        put("model", defaultModel)
    } else {
        // Fallback chain active — let OR edge route.
        put("models", buildJsonArray {
            add(JsonPrimitive(defaultModel))
            fallbackModels.forEach { add(JsonPrimitive(it)) }
        })
    }
}
```

### Helper — single-retry on 429

```kotlin
private suspend fun postWithRetry(url: String, payload: JsonObject): HttpResponse {
    val first = client.post(url) {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
        header("X-Title", "jarvis-kotlin")
        contentType(ContentType.Application.Json)
        setBody(payload)
    }
    if (first.status.value != 429) return first

    val retryAfterSec = first.headers["Retry-After"]?.toIntOrNull() ?: return first
    if (retryAfterSec > 5) return first  // too long — let caller's legacy fallback serve

    System.err.println("openrouter: 429 with Retry-After ${retryAfterSec}s — sleeping then retrying once")
    kotlinx.coroutines.delay(retryAfterSec.toLong() * 1000)

    return client.post(url) {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
        header("X-Title", "jarvis-kotlin")
        contentType(ContentType.Application.Json)
        setBody(payload)
    }
}
```

### `postChat()` and `completeWithTools()` updates

Both build their payloads using `putModelField()` (replacing the existing `put("model", ...)` line) and call `postWithRetry()` instead of `client.post()` directly. Response parsing unchanged — `parsed.model` already reflects the actually-used model when OR routes via `models:` array.

`completeWithTools(modelOverride: String? = null, ...)` signature is preserved; passing `modelOverride` continues to pin that model and bypass the chain (useful for future tool-loop V2 pinning).

## Tests — `OpenRouterChatLlmTest.kt` (new file)

Mirror the `OpenRouterVisionLlmTest` `HttpServer` mocking pattern. Five tests:

1. **`completeUsesPlainModelFieldWhenNoFallback`** — env unset. Capture request body, assert `model:` present, `models:` absent.
2. **`completeUsesModelsArrayWhenFallbackSet`** — env = `"model-b,model-c"`. Capture body, assert `models:` array == `[defaultModel, "model-b", "model-c"]`, `model:` absent.
3. **`responseModelFieldPropagatesToCaller`** — env set; mock server returns `{"model": "model-b", ...}`. Assert returned model tag == `"model-b"` (proves OR's edge-routing decision surfaces).
4. **`retriesOnceOn429WithShortRetryAfter`** — first response 429 + `Retry-After: 1`, second response 200. Assert two requests received, final pair returned from second.
5. **`propagatesOn429WithLongRetryAfter`** — response 429 + `Retry-After: 30`. Assert single request, throws with the OR error body.

`completeWithTools()` shares the same code paths via `putModelField()` + `postWithRetry()`, so its behavior is covered transitively. One additional test for explicit pin-via-override:

6. **`completeWithToolsHonorsModelOverride`** — env set with fallback chain. Call `completeWithTools(modelOverride = "specific/model")`. Assert request body contains `model: "specific/model"`, `models:` array absent.

Total: 6 new tests. Existing test surface untouched.

## Deferred risks (V2 candidates)

### HIGH — mid-tool-loop model swap (Risk Analyst)

`completeWithTools()` runs inside a 3-round loop in `JarvisToolset.chat`. If round 1 lands on model A and round 2 lands on model B (because A 429'd or was pulled), B inherits a conversation history with A's `assistant.tool_calls` field + `role:tool` results. Free-tier models vary in tool-call dialect support. B may emit text instead of continuing the tool loop, producing degraded mid-task output.

**V1 acceptance:** all currently-likely fallback models (llama-3.3-70b-instruct, mistral-small-3.1-24b-instruct, qwen-2.5-72b-instruct) honor OpenAI tool_calls schema. Cross-swap is generally safe today. Risk surfaces only if the fallback chain mixes a non-tool-capable model.

**V2 fix:** `JarvisToolset` captures `parsed.model` from round 1 response and passes it as `modelOverride` for rounds 2+. Pin happens at the call site, not inside `OpenRouterChatLlm`. Requires `completeWithTools()` return shape to surface the model — current return is `JsonObject` (the message); needs to wrap in `data class ToolCallResult(val message: JsonObject, val model: String)` or add an outparam.

### MEDIUM — fallback list rot (Pragmatist)

A stale fallback model is just as broken as a stale primary. Without curation, the list quietly degrades into a list of dead models that mask outages.

**V1 mitigation:** stderr log on every fallback hit (the OR response's `model:` field is already returned to caller; can be logged at the WebMain layer too). Commit-message convention: when adding/changing fallback models, include "verified <model> + <fallback> respond 200 on YYYY-MM-DD" in the body.

**V2 candidate:** weekly cron probe that posts a trivial completion to each model in the chain and logs failures.

### LOW — invalid model ID rejects whole request

Probe finding 1: an invalid ID anywhere in `models:` array → HTTP 400. A typo in `JARVIS_OPENROUTER_FALLBACK_MODELS` would break ALL tutor chat (not just fall through). 

**V1 mitigation:** documented in this spec. Operator validates IDs against `https://openrouter.ai/models` page before adding to env.

**V2 candidate:** lazy validation on first use: if a request returns 400 mentioning an invalid model ID, log + drop that ID from in-memory chain + retry without it. Keeps service alive at the cost of one wasted request.

## Out of scope

- Cross-provider failover beyond OpenRouter — covered by existing `FallbackLlm` at the `Llm` interface level (e.g. OpenRouter → ClaudeMax → Copilot). This spec is purely intra-OR-provider model fallback.
- Embeddings client (`jarvis.embeddings.EmbeddingsClient`). It also uses OR but is single-purpose and rarely model-bound.
- Vision LLM (`OpenRouterVisionLlm`). Same OR client, but vision models are less interchangeable; failover here would need a separate model-capability gate.

## Acceptance criteria

- [ ] `JARVIS_OPENROUTER_FALLBACK_MODELS` env unset → behavior identical to today (`model:` field, no retry on 429).
- [ ] `JARVIS_OPENROUTER_FALLBACK_MODELS="a,b,c"` set → request body has `models: [defaultModel, "a", "b", "c"]`.
- [ ] OR returns 429 with `Retry-After ≤ 5` → exactly 2 HTTP requests issued (sleep between), second response surfaces.
- [ ] OR returns 429 with `Retry-After > 5` or missing → exactly 1 HTTP request, throws.
- [ ] All 6 new tests + 614 existing tests pass.
- [ ] Caller (`WebMain`, `JarvisToolset`) unchanged — feature ships entirely inside `OpenRouterChatLlm.kt`.
