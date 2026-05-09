# OpenRouter Fallback Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the tutor chat surface survive OpenRouter pulling or rate-limiting the configured model, by injecting a server-side `models: [primary, ...fallbacks]` field plus client-side single-retry on transient 429s.

**Architecture:** Single-file change inside `src/main/kotlin/jarvis/OpenRouterChatLlm.kt` — adds two private helpers (`putModelField`, `postWithRetry`) used by both `complete()` and `completeWithTools()`. Env-var-driven (`JARVIS_OPENROUTER_FALLBACK_MODELS`) so behavior is identical to today when unset. New tests in `src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt` mirror the existing `OpenRouterVisionLlmTest` HttpServer mocking pattern.

**Tech Stack:** Kotlin (Ktor client + kotlinx.serialization-json), JUnit 5 (`com.sun.net.httpserver.HttpServer` mocking, `kotlin.test` assertions, `kotlinx.coroutines.runBlocking`).

---

## File Structure

- `src/main/kotlin/jarvis/OpenRouterChatLlm.kt` — modify in place. Add `fallbackModels` field + `parseFallbackModels()` helper, `putModelField` extension function, `postWithRetry` suspend helper. Refactor `postChat()` and `completeWithTools()` payload-build sites + HTTP-call sites to use them.
- `src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt` — create. HttpServer mock per-test handler (some return 429 first then 200 to exercise retry; some capture multiple request bodies to assert no-retry behavior).
- `.env.example` — add documentation for `JARVIS_LLM_TUTOR`, `JARVIS_OPENROUTER_MODEL`, `JARVIS_OPENROUTER_FALLBACK_MODELS` (currently undocumented).

---

### Task 1: Test scaffold + first failing test

**Files:**
- Create: `src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt`

- [ ] **Step 1: Write the failing test (`completeUsesPlainModelFieldWhenNoFallback`)**

```kotlin
package jarvis

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterChatLlmTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    /** Captured request bodies, in order received. */
    private val capturedBodies = mutableListOf<String>()
    private val handlerRef = AtomicReference<HttpHandler?>(null)
    private val callCount = AtomicInteger(0)

    @BeforeEach
    fun setup() {
        capturedBodies.clear()
        callCount.set(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            callCount.incrementAndGet()
            val body = exchange.requestBody.bufferedReader().readText()
            capturedBodies += body
            val handler = handlerRef.get()
                ?: error("test forgot to set handlerRef")
            handler.handle(exchange)
        }
        server.executor = null
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterEach
    fun teardown() {
        server.stop(0)
        handlerRef.set(null)
    }

    private fun reply200(model: String, content: String = "ok"): HttpHandler = HttpHandler { exchange ->
        val body = """{"model":"$model","choices":[{"index":0,"message":{"role":"assistant","content":"$content"},"finish_reason":"stop"}]}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    }

    @Test
    fun completeUsesPlainModelFieldWhenNoFallback() = runBlocking {
        handlerRef.set(reply200(model = "primary/model"))
        val llm = OpenRouterChatLlm(
            apiKey = "sk-test",
            defaultModel = "primary/model",
            baseUrl = baseUrl,
            fallbackModels = emptyList(),
        )
        llm.use {
            val (_, model) = it.complete(
                listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10,
            )
            assertEquals("primary/model", model)
        }
        assertEquals(1, callCount.get())
        val body = Json.parseToJsonElement(capturedBodies[0]).jsonObject
        assertEquals("primary/model", body["model"]?.jsonPrimitive?.contentOrNull,
            "single-model mode must send `model:` field")
        assertNull(body["models"], "no fallback configured ⇒ no `models:` array")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests jarvis.OpenRouterChatLlmTest.completeUsesPlainModelFieldWhenNoFallback`
Expected: FAIL — compilation error: `OpenRouterChatLlm` constructor doesn't accept `fallbackModels` parameter yet.

- [ ] **Step 3: Add the `fallbackModels` constructor parameter (and helper) to `OpenRouterChatLlm.kt`**

Edit `src/main/kotlin/jarvis/OpenRouterChatLlm.kt`. Update the class header to add the new parameter:

```kotlin
class OpenRouterChatLlm(
    private val apiKey: String = resolveOpenRouterKey()
        ?: error("OPENROUTER_API_KEY required for OpenRouterChatLlm"),
    private val defaultModel: String = System.getenv("JARVIS_OPENROUTER_MODEL")
        ?: "meta-llama/llama-3.3-70b-instruct:free",
    private val baseUrl: String = Config.OPENROUTER_BASE_URL,
    private val fallbackModels: List<String> = parseFallbackModels(),
) : Llm {
```

Add the parser as a top-level private function in the same file (above the class):

```kotlin
private fun parseFallbackModels(): List<String> =
    System.getenv("JARVIS_OPENROUTER_FALLBACK_MODELS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
```

Add the model-field helper as a private extension function inside the class (anywhere among the other private members):

```kotlin
private fun kotlinx.serialization.json.JsonObjectBuilder.putModelField(modelOverride: String?) {
    when {
        modelOverride != null -> put("model", modelOverride)
        fallbackModels.isEmpty() -> put("model", defaultModel)
        else -> put("models", buildJsonArray {
            add(JsonPrimitive(defaultModel))
            fallbackModels.forEach { add(JsonPrimitive(it)) }
        })
    }
}
```

Update `complete()`'s payload builder to use it (replace `put("model", defaultModel)` with `putModelField(null)`):

```kotlin
override suspend fun complete(
    messages: List<ChatMessage>,
    maxTokens: Int,
): Pair<String, String> {
    val payload = buildJsonObject {
        putModelField(null)
        put("max_tokens", maxTokens)
        put("messages", buildJsonArray {
            for (m in messages) {
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
        })
    }
    return postChat(payload)
}
```

Update `completeWithTools()`'s payload builder similarly (replace `put("model", modelOverride ?: defaultModel)` with `putModelField(modelOverride)`):

```kotlin
suspend fun completeWithTools(
    messages: JsonArray,
    tools: JsonArray,
    maxTokens: Int = 1500,
    toolChoice: String = "auto",
    modelOverride: String? = null,
): JsonObject {
    val payload = buildJsonObject {
        putModelField(modelOverride)
        put("max_tokens", maxTokens)
        put("messages", messages)
        put("tools", tools)
        put("tool_choice", JsonPrimitive(toolChoice))
    }
    // … rest unchanged
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests jarvis.OpenRouterChatLlmTest.completeUsesPlainModelFieldWhenNoFallback`
Expected: PASS.

- [ ] **Step 5: Do not commit yet** — bundle with the next tests for one cohesive feature commit.

---

### Task 2: Fallback chain + response model propagation

**Files:**
- Modify: `src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt` — add two more tests.

- [ ] **Step 1: Write the failing tests**

Append to the test class:

```kotlin
@Test
fun completeUsesModelsArrayWhenFallbackSet() = runBlocking {
    handlerRef.set(reply200(model = "fallback-1"))
    val llm = OpenRouterChatLlm(
        apiKey = "sk-test",
        defaultModel = "primary/model",
        baseUrl = baseUrl,
        fallbackModels = listOf("fallback-1", "fallback-2"),
    )
    llm.use {
        it.complete(listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10)
    }
    val body = Json.parseToJsonElement(capturedBodies[0]).jsonObject
    assertNull(body["model"], "fallback configured ⇒ no plain `model:` field")
    val models = body["models"]?.jsonArray ?: error("expected `models:` array")
    assertEquals(3, models.size)
    assertEquals("primary/model", models[0].jsonPrimitive.content)
    assertEquals("fallback-1", models[1].jsonPrimitive.content)
    assertEquals("fallback-2", models[2].jsonPrimitive.content)
}

@Test
fun responseModelFieldPropagatesToCaller() = runBlocking {
    // OR routed past primary (404 at edge) and served from fallback-1.
    handlerRef.set(reply200(model = "fallback-1", content = "served by fallback"))
    val llm = OpenRouterChatLlm(
        apiKey = "sk-test",
        defaultModel = "primary/model",
        baseUrl = baseUrl,
        fallbackModels = listOf("fallback-1"),
    )
    llm.use {
        val (text, model) = it.complete(
            listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10,
        )
        assertEquals("served by fallback", text)
        assertEquals("fallback-1", model,
            "model tag must reflect the OR-edge-routed model, not the configured primary")
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests jarvis.OpenRouterChatLlmTest`
Expected: All three tests PASS — the helper from Task 1 already supports the chain shape, and `postChat()` already returns `parsed.model`.

If `responseModelFieldPropagatesToCaller` fails, check that `postChat()`'s return value is `choice.message.content to (parsed.model.ifBlank { defaultModel })` — the `ifBlank` fallback is intentional (some OR responses omit the model field on tool-only replies); the mock returns the field non-blank so the actually-used model wins.

- [ ] **Step 3: Do not commit yet** — bundle with retry tests next.

---

### Task 3: 429 single-retry helper

**Files:**
- Modify: `src/main/kotlin/jarvis/OpenRouterChatLlm.kt` — add `postWithRetry` helper, refactor both call sites.
- Modify: `src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt` — add two retry tests.

- [ ] **Step 1: Write the failing tests**

Append to the test class:

```kotlin
@Test
fun retriesOnceOn429WithShortRetryAfter() = runBlocking {
    val responseN = AtomicInteger(0)
    handlerRef.set(HttpHandler { exchange ->
        if (responseN.incrementAndGet() == 1) {
            exchange.responseHeaders.add("Retry-After", "1")
            exchange.responseHeaders.add("Content-Type", "application/json")
            val body = """{"error":{"message":"rate limited","code":429}}"""
            exchange.sendResponseHeaders(429, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        } else {
            val body = """{"model":"primary/model","choices":[{"index":0,"message":{"role":"assistant","content":"recovered"},"finish_reason":"stop"}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
    })
    val llm = OpenRouterChatLlm(
        apiKey = "sk-test",
        defaultModel = "primary/model",
        baseUrl = baseUrl,
        fallbackModels = emptyList(),
    )
    val started = System.currentTimeMillis()
    val (text, _) = llm.use {
        it.complete(listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10)
    }
    val elapsed = System.currentTimeMillis() - started
    assertEquals("recovered", text)
    assertEquals(2, callCount.get(), "must retry exactly once on 429 + Retry-After ≤ 5")
    assertTrue(elapsed >= 1000, "must honor Retry-After by sleeping ≥ 1000ms (got ${elapsed}ms)")
}

@Test
fun propagatesOn429WithLongRetryAfter() = runBlocking {
    handlerRef.set(HttpHandler { exchange ->
        exchange.responseHeaders.add("Retry-After", "30")
        exchange.responseHeaders.add("Content-Type", "application/json")
        val body = """{"error":{"message":"rate limited","code":429}}"""
        exchange.sendResponseHeaders(429, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    })
    val llm = OpenRouterChatLlm(
        apiKey = "sk-test",
        defaultModel = "primary/model",
        baseUrl = baseUrl,
        fallbackModels = emptyList(),
    )
    val ex = assertFails {
        llm.use {
            it.complete(listOf(ChatMessage(role = "user", content = "ping")), maxTokens = 10)
        }
    }
    assertEquals(1, callCount.get(), "must NOT retry when Retry-After > 5 (would block too long)")
    assertTrue(ex.message?.contains("429") == true, "thrown error must mention the 429: '${ex.message}'")
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests jarvis.OpenRouterChatLlmTest`
Expected: Both retry tests FAIL — current code throws on the first 429, no retry path.

- [ ] **Step 3: Add the `postWithRetry` helper to `OpenRouterChatLlm.kt`**

Add as a private suspend function inside the class:

```kotlin
private suspend fun postWithRetry(
    payload: JsonObject,
    titleHeader: String,
): io.ktor.client.statement.HttpResponse {
    suspend fun once() = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        header("HTTP-Referer", "https://github.com/CorgiGH/Jarvis")
        header("X-Title", titleHeader)
        contentType(ContentType.Application.Json)
        setBody(payload)
    }

    val first = once()
    if (first.status.value != 429) return first

    val retryAfterSec = first.headers["Retry-After"]?.toIntOrNull() ?: return first
    if (retryAfterSec <= 0 || retryAfterSec > 5) return first

    System.err.println("openrouter: 429 with Retry-After ${retryAfterSec}s — sleeping then retrying once")
    kotlinx.coroutines.delay(retryAfterSec.toLong() * 1000)
    return once()
}
```

Refactor `postChat()` to use it (replace its inline `client.post(...)` with `postWithRetry(payload, "jarvis-kotlin")`):

```kotlin
private suspend fun postChat(payload: JsonObject): Pair<String, String> {
    val resp = postWithRetry(payload, titleHeader = "jarvis-kotlin")
    if (!resp.status.isSuccess()) {
        error("openrouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}")
    }
    val parsed: ChatResponse = resp.body()
    val choice = parsed.choices.firstOrNull() ?: error("openrouter returned no choices")
    return choice.message.content to (parsed.model.ifBlank { defaultModel })
}
```

Refactor `completeWithTools()` to use it (replace its inline `client.post(...)` with `postWithRetry(payload, "jarvis-kotlin tutor")`):

```kotlin
suspend fun completeWithTools(
    messages: JsonArray,
    tools: JsonArray,
    maxTokens: Int = 1500,
    toolChoice: String = "auto",
    modelOverride: String? = null,
): JsonObject {
    val payload = buildJsonObject {
        putModelField(modelOverride)
        put("max_tokens", maxTokens)
        put("messages", messages)
        put("tools", tools)
        put("tool_choice", JsonPrimitive(toolChoice))
    }
    val resp = postWithRetry(payload, titleHeader = "jarvis-kotlin tutor")
    if (!resp.status.isSuccess()) {
        error("openrouter HTTP ${resp.status.value}: ${resp.bodyAsText().take(400)}")
    }
    val parsed = resp.body<JsonObject>()
    val choices = parsed["choices"] as? JsonArray ?: error("no choices in reply")
    val firstChoice = choices.firstOrNull() as? JsonObject ?: error("empty choices")
    return firstChoice["message"] as? JsonObject ?: error("no message in choice")
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests jarvis.OpenRouterChatLlmTest`
Expected: All five tests PASS.

- [ ] **Step 5: Do not commit yet** — one more test to wrap.

---

### Task 4: completeWithTools modelOverride still pins

**Files:**
- Modify: `src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt` — add the override test.

- [ ] **Step 1: Write the test**

```kotlin
@Test
fun completeWithToolsHonorsModelOverride() = runBlocking {
    handlerRef.set(HttpHandler { exchange ->
        val body = """{"model":"specific/pinned","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    })
    val llm = OpenRouterChatLlm(
        apiKey = "sk-test",
        defaultModel = "primary/model",
        baseUrl = baseUrl,
        fallbackModels = listOf("fallback-1", "fallback-2"),
    )
    llm.use {
        val msgArr = kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.buildJsonObject {
                put("role", "user"); put("content", "ping")
            })
        }
        it.completeWithTools(
            messages = msgArr,
            tools = kotlinx.serialization.json.buildJsonArray { },
            modelOverride = "specific/pinned",
        )
    }
    val body = Json.parseToJsonElement(capturedBodies[0]).jsonObject
    assertEquals("specific/pinned", body["model"]?.jsonPrimitive?.contentOrNull,
        "modelOverride must pin to a single model and ignore the fallback chain")
    assertNull(body["models"], "modelOverride present ⇒ no `models:` array")
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew test --tests jarvis.OpenRouterChatLlmTest`
Expected: All six tests PASS — `putModelField` checks `modelOverride != null` first.

- [ ] **Step 3: Do not commit yet** — wrap with .env.example update + full suite next.

---

### Task 5: Document the new env var

**Files:**
- Modify: `.env.example`

- [ ] **Step 1: Append the new section to `.env.example`**

Add at the end of the file:

```
# OPTIONAL. Tutor-surface chat path. When set to "openrouter", the tutor
# (https://corgflix.duckdns.org/tutor/) sends chat through OpenRouterChatLlm
# with tool_use enabled (search_archival, query_graph, wiki_*, etc.). When
# unset, the tutor uses the legacy ChatTools path (no tools). Requires
# OPENROUTER_API_KEY to also be set.
# JARVIS_LLM_TUTOR=openrouter

# OPTIONAL. Override the OpenRouter chat model used by both the legacy
# `JARVIS_LLM=openrouter` provider AND the tutor surface. Defaults to
# meta-llama/llama-3.3-70b-instruct:free. Free-tier models are subject to
# upstream provider rate limits (Venice, Together, etc.) and OpenRouter
# may pull them at any time — see JARVIS_OPENROUTER_FALLBACK_MODELS below.
# JARVIS_OPENROUTER_MODEL=meta-llama/llama-3.3-70b-instruct:free

# OPTIONAL. Comma-separated OpenRouter model IDs used as a server-side
# fallback chain when the primary is pulled or returns 404/410 at the
# OpenRouter edge. The chat-completions request body sends
# `models: [primary, ...fallbacks]` so OpenRouter routes around dead
# models in a single round-trip. Transient 429s (rate-limit) are NOT
# routed past — they trigger a single client-side retry honoring the
# Retry-After header (only if ≤ 5s); longer waits propagate so the
# caller's degraded chat path serves the turn. Validate model IDs at
# https://openrouter.ai/models before adding — an invalid ID rejects
# the entire request with HTTP 400.
# JARVIS_OPENROUTER_FALLBACK_MODELS=mistralai/mistral-small-3.1-24b-instruct:free,qwen/qwen-2.5-72b-instruct:free
```

---

### Task 6: Full backend suite + commit

**Files:**
- All of the above.

- [ ] **Step 1: Run the full backend test suite**

Run: `./gradlew test`
Expected: 540 (existing) + 6 (new) = 546 tests pass on the JVM toolchain. (Frontend Vite tests + Rust daemon tests are untouched and don't need re-running.)

If any unrelated test fails, investigate before committing — do not skip.

- [ ] **Step 2: Stage + commit**

Run:
```bash
git add src/main/kotlin/jarvis/OpenRouterChatLlm.kt src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt .env.example docs/superpowers/specs/2026-05-09-openrouter-fallback-chain-design.md docs/superpowers/plans/2026-05-09-openrouter-fallback-chain.md .claude/council-cache/council-1778346847-or-fallback-chain.md
```

Then:
```bash
git commit -m "$(cat <<'EOF'
OpenRouter fallback chain: server-side `models:` array + 429 single-retry

Probe (2026-05-09) confirmed OR's chat-completions API honors
`models: [primary, ...fallbacks]` for server-side routing past
404/410 (model pulled) but does NOT failover on 429 (upstream
rate-limit). llama-3.3-70b-instruct:free is currently 429-rate-limited
by Venice with Retry-After ~3-11s.

This commit:
- Adds `JARVIS_OPENROUTER_FALLBACK_MODELS` (CSV) env var.
- Sends `models:` array in request body when env set; OR edge handles 404.
- Single client-side retry on 429 if Retry-After ≤ 5s (sleep + redo).
- 429 + Retry-After > 5 OR missing ⇒ propagate (caller's legacy chat path serves).
- `completeWithTools(modelOverride=...)` still pins explicit model (V2 hook).

Council R5 verdict: WRONG APPROACH on the original client-side-loop design,
revised to the OR-native server-side `models:` field. See
`.claude/council-cache/council-1778346847-or-fallback-chain.md`.

Deferred (V2): mid-tool-loop model pinning (Risk Analyst HIGH).

Verified: meta-llama/llama-3.3-70b-instruct:free + mistralai/mistral-small-3.1-24b-instruct:free
both reachable on OR free tier as of 2026-05-09 (mistral pending live verification
since llama is currently 429ed).
EOF
)"
```

- [ ] **Step 3: Push**

Run: `git push origin main`
Expected: pushes to `main` (no PR — solo single-branch project per existing flow).

---

### Task 7: Deploy + smoke-test on VPS

**Files:**
- Operational change: `/opt/jarvis/.env` on the VPS gets the new env var.
- Service restart: `systemctl restart jarvis`.

- [ ] **Step 1: Deploy via existing script**

Run: `& "C:\Program Files\Git\bin\bash.exe" tools/deploy.sh`
Expected: builds shadowJar, scps to VPS, restarts service. Tail end of output should show `Active: active (running)`.

- [ ] **Step 2: Add fallback chain to VPS .env**

Run:
```bash
ssh root@46.247.109.91 'grep -q JARVIS_OPENROUTER_FALLBACK_MODELS /opt/jarvis/.env || echo "JARVIS_OPENROUTER_FALLBACK_MODELS=mistralai/mistral-small-3.1-24b-instruct:free,qwen/qwen-2.5-72b-instruct:free" >> /opt/jarvis/.env'
```

Then restart:
```bash
ssh root@46.247.109.91 'systemctl restart jarvis'
```

- [ ] **Step 3: Health check**

Run: `curl -s https://corgflix.duckdns.org/healthz`
Expected: `ok`

- [ ] **Step 4: Probe live tutor — does the chain engage when primary 429s?**

Run a curl probe through the live `/api/chat` endpoint with a tutor task already created. Or, simpler: tail logs while sending a tutor chat from the browser.

```bash
ssh root@46.247.109.91 'tail -f /var/log/jarvis.log' &
# … in browser, open https://corgflix.duckdns.org/tutor/, click a preset, send "hi"
```

Look for either:
- A successful 200 response with `model:` in the chat reply (good — chain engaged).
- A `429` log line followed by either `sleeping then retrying once` or a graceful degradation to legacy chat (also good).

If the response is just the legacy chat (no tool use), check `/var/log/jarvis.log` for the actual failure mode and address before claiming success.

- [ ] **Step 5: Update memory state-of-world**

Edit `C:/Users/User/.claude/projects/C--Users-User-jarvis-kotlin/memory/project_jarvis_2026-05-09_session_wrap.md` — append a `### Post-wrap addendum` section noting the fallback chain shipped + which models are in the live chain + that R5-derived V2 work (mid-tool-loop pinning) is now the new top-of-list.

---

## Self-Review

**1. Spec coverage:**
- ✅ Env contract — Tasks 1, 5.
- ✅ `putModelField` helper — Task 1.
- ✅ `postWithRetry` helper — Task 3.
- ✅ All 6 tests from spec — Tasks 1–4.
- ✅ Documentation (.env.example) — Task 5.
- ✅ Acceptance criteria — Tasks 1, 2, 3, 4 + Task 6 (full suite).

**2. Placeholder scan:** No "TBD"/"TODO"/"appropriate handling" left.

**3. Type consistency:** `fallbackModels` consistently `List<String>` across helper + constructor + tests. `putModelField` always called with single `modelOverride: String?` arg. `postWithRetry` always called with `(JsonObject, String)` — title header per call site.

Plan saved.
