# Grader hardening + Y tripwire refinement + X-fixture re-seed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land council-mandated fixes for the flaky `:free` grader (Council 1778881174-drillgrader-fix), refine the Surface Y behavioral tripwire (Council 1778881175-y-tripwire-design), then re-seed Trace 9+10 X fixtures with healthy-grader output.

**Architecture:** Three sequential phases on `main`. Phase A hardens the Kotlin grader path (envelope raw-output capture FIRST as load-bearing observability, then consumer-side filter, then substrate flag + token-cap fix, then parser robustness). Phase B refines the Node tripwire (AND-gate findings_count==0, frontmatter confidence band, light persona prompt with external exemplars). Phase C re-runs the Spec A v2 seeder against the post-Phase-A grader and promotes real-rubric labels.

**Tech Stack:** Kotlin 1.x + Ktor + kotlinx.serialization (backend), Node 20+ (tools layer), OpenRouter `:free`-band LLM provider, gradle (Kotlin build), `node --test` (tools test runner).

**Source authorities (verbatim verdicts — read before starting):**
- `.claude/council-cache/council-1778881174-drillgrader-fix.md` — Phase A.
- `.claude/council-cache/council-1778881175-y-tripwire-design.md` — Phase B.

**File map:**

Phase A (Kotlin server + Node tools):
- Modify `src/main/kotlin/jarvis/tutor/TutorEventLog.kt` — add `llm_output_raw_truncated: String? = null` field to `TutorEvent` (~line 18-45 data class).
- Modify `src/main/kotlin/jarvis/tutor/DrillGrader.kt` — `grade()` returns a `GradeAttempt` carrying both `parsed: GradeResult?` and `rawOutput: String`; `parseGradeJson()` gains regex-balanced-brace fallback; `complete()` call passes `responseFormat = "json_object"`; bump `maxTokens` to 1200 for code-grading path.
- Modify `src/main/kotlin/jarvis/OpenRouterChatLlm.kt` — `complete()` gains `responseFormat: String? = null` param that injects `response_format: {type: ...}` into payload (line 88-105).
- Modify `src/main/kotlin/jarvis/web/TutorRoutes.kt` — drill/grade handler (1606-1648) captures `attempt.rawOutput`, stores truncated 1500-char copy in envelope on `parse_error` path.
- Modify `src/test/kotlin/jarvis/tutor/DrillGraderTest.kt` — new tests for preamble extract + trailing-chatter extract.
- Modify `tools/lib/event-log-reader.mjs` — `filterEvents()` gains `status_in?: string[]` param.
- Modify `tools/lib/event-log-reader.test.mjs` — new tests for status filter.
- Modify `tools/surface-x.mjs` — pass `status_in: ["ok"]` from CLI for calibration (line 302-308).

Phase B (Node tools):
- Modify `tools/surface-y-tripwire.mjs` — `flagSuspectRun(transcript, findings_count, thresholds)` AND-gates suspect on `findings_count === 0`.
- Modify `tools/surface-y-tripwire.test.mjs` — tests for new AND-gate.
- Modify `tools/surface-y.mjs` — counts findings (transcript filter at line 358) BEFORE calling tripwire; passes count; emits `tripwire_confidence_band` frontmatter field.
- Modify `tools/surface-y-persona.mjs` — append 1-2 external authentic-naive few-shot exemplars (hand-authored, NOT from calibration corpus).

Phase C (documentation + ops):
- Modify `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md` — replace CANDIDATE Trace 9+10 with real-rubric-grounded labels after re-seed.

**Test commands:**
- Kotlin: `./gradlew test --tests "jarvis.tutor.DrillGraderTest"` (single class); `./gradlew test` (full backend, 702 tests).
- Node tools: `npm run test:tools --prefix tools` (113 → goal 119+ with new tests).

---

## Phase A — DrillGrader hardening (Council 1)

### Task A.1: Add `llm_output_raw_truncated` field to TutorEvent envelope

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/TutorEventLog.kt:18-45`

- [ ] **Step 1: Read current `TutorEvent` data class**

Confirm field set + ordering before adding. Today's class ends with `is_synthetic: Boolean = false` at line 44. Add new field BEFORE that, after `latency_ms`.

- [ ] **Step 2: Add new field with default null**

Add line in `TutorEvent` data class:

```kotlin
val llm_output_raw_truncated: String? = null,
```

Place after `val latency_ms: Long?,` and before `val status: String,`.

- [ ] **Step 3: Compile check (no tests yet — purely additive schema change with default)**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/TutorEventLog.kt
git commit -m "feat(envelope): add llm_output_raw_truncated field for grader parse_error forensics"
```

---

### Task A.2: Refactor `DrillGrader.grade()` to return raw output alongside parsed result

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/DrillGrader.kt:13-124`
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt:1606-1648` (caller signature update)

- [ ] **Step 1: Write failing test for new `GradeAttempt` return type**

Append to `src/test/kotlin/jarvis/tutor/DrillGraderTest.kt`:

```kotlin
@Test
fun `grade returns GradeAttempt carrying raw output on success`() = kotlinx.coroutines.runBlocking {
    val fakeLlm = object : jarvis.OpenRouterChatLlm() {
        override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int): Pair<String, String> {
            return """{"correct":true,"rubric":{"numeric":true,"mechanism":true,"justification":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}""" to "fake/model"
        }
    }
    val attempt = DrillGrader.grade(
        problemStatement = "p", userAttempt = "a", expectedHint = "h",
        llm = fakeLlm,
    )
    assertNotNull(attempt.parsed)
    assertTrue(attempt.rawOutput.contains("\"correct\":true"))
    assertEquals("fake/model", attempt.modelResolved)
}

@Test
fun `grade returns GradeAttempt carrying raw output even on parse fail`() = kotlinx.coroutines.runBlocking {
    val fakeLlm = object : jarvis.OpenRouterChatLlm() {
        override suspend fun complete(messages: List<jarvis.ChatMessage>, maxTokens: Int): Pair<String, String> {
            return "Sure here is your grade I think it is good" to "fake/model"
        }
    }
    val attempt = DrillGrader.grade(
        problemStatement = "p", userAttempt = "a", expectedHint = "h",
        llm = fakeLlm,
    )
    assertNull(attempt.parsed)
    assertEquals("Sure here is your grade I think it is good", attempt.rawOutput)
}
```

NOTE: `OpenRouterChatLlm` may not be open-for-extension in current code — if test fails on `cannot inherit final class`, mark the class `open` in `OpenRouterChatLlm.kt`. This is the minimal viable doubling pattern; switching to a sealed interface for `Llm` is out of scope for this task.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "jarvis.tutor.DrillGraderTest.grade returns GradeAttempt carrying raw output on success"`
Expected: FAIL with "unresolved reference: GradeAttempt" or similar.

- [ ] **Step 3: Add `GradeAttempt` data class + refactor `grade()` to return it**

In `DrillGrader.kt`, add after `data class GradeResult(...)` block (around line 21):

```kotlin
@Serializable
data class GradeAttempt(
    val parsed: GradeResult?,
    val rawOutput: String,
    val modelResolved: String,
)
```

Change `grade()` signature from `suspend fun grade(...): GradeResult?` to `suspend fun grade(...): GradeAttempt`.

Change function body's tail (currently lines 112-124) from:

```kotlin
val (raw, _) = llm.complete(
    listOf(
        ChatMessage("system", systemPrompt),
        ChatMessage("user", userMsg),
    ),
    maxTokens = 600,
)
val parsed = parseGradeJson(raw.trim())
if (parsed == null) {
    System.err.println("[drill grader] parse-fail lang=$language raw=${raw.take(600).replace('\n', ' ')}")
}
return parsed
```

to:

```kotlin
val (raw, modelResolved) = llm.complete(
    listOf(
        ChatMessage("system", systemPrompt),
        ChatMessage("user", userMsg),
    ),
    maxTokens = 600,
)
val parsed = parseGradeJson(raw.trim())
if (parsed == null) {
    System.err.println("[drill grader] parse-fail lang=$language raw=${raw.take(600).replace('\n', ' ')}")
}
return GradeAttempt(parsed = parsed, rawOutput = raw, modelResolved = modelResolved)
```

- [ ] **Step 4: Update `TutorRoutes.kt:1606-1648` caller for new return type**

Replace the `try { ... result = jarvis.tutor.DrillGrader.grade(...) ... if (result == null) ... }` block (1606-1648) with this exact body — note we now ALSO capture `rawOutput` so envelope build below can use it:

```kotlin
val attempt: jarvis.tutor.DrillGrader.GradeAttempt? = try {
    jarvis.OpenRouterChatLlm().use { llm ->
        kotlinx.coroutines.runBlocking {
            jarvis.tutor.DrillGrader.grade(
                problemStatement = req.problemStatement,
                userAttempt = req.userAttempt,
                expectedHint = req.expectedAnswerHint,
                llm = llm,
                language = req.language,
                referenceSolution = req.referenceSolution,
                rubricItems = req.rubricItems,
                prediction = req.prediction,
            )
        }
    }
} catch (e: Exception) { null }

val (reply, status) = when {
    attempt == null -> {
        ApiDrillGradeReply(
            correct = false, score = 0.0, rubric = emptyMap(),
            misconception = "UNGRADED",
            elaboratedFeedback = "LLM unavailable. Please re-attempt or ask sidekick.",
        ) to "error"
    }
    attempt.parsed == null -> {
        ApiDrillGradeReply(
            correct = false, score = 0.0, rubric = emptyMap(),
            misconception = "OTHER",
            elaboratedFeedback = "LLM grader returned malformed output; please re-attempt or ask sidekick.",
        ) to "parse_error"
    }
    else -> {
        val r = attempt.parsed!!
        ApiDrillGradeReply(
            correct = r.correct,
            score = r.score,
            rubric = r.rubric,
            misconception = r.misconception,
            elaboratedFeedback = r.elaboratedFeedback,
        ) to "ok"
    }
}
```

- [ ] **Step 5: Run targeted DrillGrader tests to verify new contract**

Run: `./gradlew test --tests "jarvis.tutor.DrillGraderTest"`
Expected: PASS — both happy-path and parse-fail tests now pass; existing parseGradeJson-only tests still pass because parser API unchanged.

- [ ] **Step 6: Run TutorRoutes tests to verify caller-side refactor**

Run: `./gradlew test --tests "*TutorRoutes*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/DrillGrader.kt \
        src/test/kotlin/jarvis/tutor/DrillGraderTest.kt \
        src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "refactor(grader): DrillGrader.grade returns GradeAttempt carrying raw output for forensics"
```

---

### Task A.3: Plumb raw output truncation into TutorEvent envelope on parse_error

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt:1650+ envelope-build block`

- [ ] **Step 1: Locate the envelope-build block**

Find the `runCatching { ... TutorEventLog.GLOBAL.append(...) }` block after `call.respond(...)` (around lines 1652+). Read it to confirm field set passed to `TutorEvent(...)`.

- [ ] **Step 2: Write failing test for envelope field**

Append to `src/test/kotlin/jarvis/tutor/DrillGraderTest.kt` (or `TutorRoutesTest.kt` if it exists):

```kotlin
@Test
fun `parse_error envelope carries llm_output_raw_truncated`() {
    // Construct a TutorEvent with the new field, encode, decode, assert round-trip.
    val evt = TutorEvent(
        event_type = "drill_grade",
        event_id = "test1",
        ts_utc = "2026-05-16T00:00:00Z",
        task_id = null,
        session_id = "s1",
        prompt_template_id = null,
        system_prompt_sha256 = null,
        retrieved_context_summary = null,
        llm_output_full = "{...rendered reply...}",
        model_resolved = "fake/model",
        tokens_in = null,
        tokens_out = null,
        latency_ms = null,
        llm_output_raw_truncated = "Sure here is your grade",
        status = "parse_error",
    )
    val json = kotlinx.serialization.json.Json { encodeDefaults = true }
    val encoded = json.encodeToString(TutorEvent.serializer(), evt)
    assertTrue(encoded.contains("\"llm_output_raw_truncated\":\"Sure here is your grade\""))
    val decoded = json.decodeFromString(TutorEvent.serializer(), encoded)
    assertEquals("Sure here is your grade", decoded.llm_output_raw_truncated)
}
```

- [ ] **Step 3: Run test to verify it passes (schema change from A.1 already in place)**

Run: `./gradlew test --tests "jarvis.tutor.DrillGraderTest.parse_error envelope carries llm_output_raw_truncated"`
Expected: PASS (this validates the A.1 schema change end-to-end).

- [ ] **Step 4: Update envelope-build call in TutorRoutes to populate the new field**

In the `runCatching { ... TutorEvent(...) ... }` block, add to the constructor call:

```kotlin
llm_output_raw_truncated = if (status == "parse_error" && attempt?.rawOutput != null)
    attempt.rawOutput.take(1500)
else null,
```

Place this argument near the other LLM-related fields (next to `llm_output_full`).

- [ ] **Step 5: Run the integration test that exercises /api/v1/drill/grade**

Run: `./gradlew test --tests "*TutorRoutes*drill*"` (if exists) — or the broader `*TutorRoutes*`.
Expected: PASS. If no integration test exists for the parse_error path, add one wrapping a fake LLM that returns garbage (modeled after Step 1's fake).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/DrillGraderTest.kt
git commit -m "feat(grader): capture raw LLM output (truncated 1500) in tutor event envelope on parse_error"
```

---

### Task A.4: Add `status_in` filter to `filterEvents` and wire Surface X calibration to it

**Files:**
- Modify: `tools/lib/event-log-reader.mjs:29-39`
- Modify: `tools/lib/event-log-reader.test.mjs` (or co-located test)
- Modify: `tools/surface-x.mjs:302-308`

- [ ] **Step 1: Write failing test for `status_in` filter**

Append to `tools/lib/event-log-reader.test.mjs`:

```javascript
import { test } from "node:test";
import assert from "node:assert/strict";
import { filterEvents } from "./event-log-reader.mjs";

test("filterEvents supports status_in", () => {
  const events = [
    { event_id: "1", status: "ok", task_id: "T1" },
    { event_id: "2", status: "parse_error", task_id: "T1" },
    { event_id: "3", status: "ok", task_id: "T2" },
  ];
  const okOnly = filterEvents(events, { status_in: ["ok"] });
  assert.equal(okOnly.length, 2);
  assert.deepEqual(okOnly.map(e => e.event_id), ["1", "3"]);

  const errorOnly = filterEvents(events, { status_in: ["parse_error", "error"] });
  assert.equal(errorOnly.length, 1);
  assert.equal(errorOnly[0].event_id, "2");

  // Backward-compat: omitting status_in includes all
  assert.equal(filterEvents(events, {}).length, 3);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tools/lib/event-log-reader.test.mjs`
Expected: FAIL with "okOnly.length expected 2, got 3" (filter not implemented yet).

- [ ] **Step 3: Implement filter**

In `tools/lib/event-log-reader.mjs`, modify `filterEvents` signature + body to add the `status_in` filter:

```javascript
export function filterEvents(events, { task_id, session_id, from_ts, to_ts, event_type, include_synthetic = false, status_in = null } = {}) {
  return events.filter(e => {
    if (!include_synthetic && e.is_synthetic) return false;
    if (task_id && e.task_id !== task_id) return false;
    if (session_id && e.session_id !== session_id) return false;
    if (event_type && e.event_type !== event_type) return false;
    if (from_ts && (e.ts_utc ?? "") < from_ts) return false;
    if (to_ts && (e.ts_utc ?? "") > to_ts) return false;
    if (status_in && !status_in.includes(e.status)) return false;
    return true;
  });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tools/lib/event-log-reader.test.mjs`
Expected: PASS.

- [ ] **Step 5: Wire `tools/surface-x.mjs` to filter `status: "ok"` in calibration mode**

At line 302-308, change the `filterEvents` call to:

```javascript
const filtered = filterEvents(all, {
  task_id: args.task,
  session_id: args.session,
  from_ts: args.from,
  to_ts: args.to,
  include_synthetic: !!args["include-synthetic"],
  status_in: args["all-statuses"] ? null : ["ok"],
});
```

Rationale: calibration corpus integrity requires excluding parse_error/error events by default. `--all-statuses` CLI flag re-enables the old behavior for ad-hoc investigations.

- [ ] **Step 6: Run all tools tests**

Run: `npm run test:tools --prefix tools`
Expected: 113+1=114 PASS (or whatever current baseline+1).

- [ ] **Step 7: Commit**

```bash
git add tools/lib/event-log-reader.mjs tools/lib/event-log-reader.test.mjs tools/surface-x.mjs
git commit -m "feat(surface-x): filter parse_error/error events from calibration corpus (status_in=['ok'] default)"
```

---

### Task A.5: Add `responseFormat` param to `OpenRouterChatLlm.complete` and wire grader to request `json_object`

**Files:**
- Modify: `src/main/kotlin/jarvis/OpenRouterChatLlm.kt:88-105`
- Modify: `src/main/kotlin/jarvis/tutor/DrillGrader.kt:112` (grader call)

- [ ] **Step 1: Read existing `complete()` to confirm shape**

Read lines 88-105. Confirm `payload` is built via `buildJsonObject { ... }` and includes `model`, `max_tokens`, `messages`.

- [ ] **Step 2: Write failing test that asserts `response_format` is included in payload when param passed**

If `OpenRouterChatLlmTest.kt` does not exist, create it at `src/test/kotlin/jarvis/OpenRouterChatLlmTest.kt`. Otherwise add to it:

```kotlin
package jarvis

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenRouterChatLlmResponseFormatTest {
    @Test
    fun `complete payload includes response_format when responseFormat=json_object`() {
        // Use reflection or a payload-capturing fake (depending on what's already in place).
        // Minimal: introduce a `buildCompletePayload` test-visible helper in OpenRouterChatLlm
        // that returns the JsonObject sent to /chat/completions, then assert on it.
        val payload = OpenRouterChatLlm.buildCompletePayload(
            messages = listOf(ChatMessage("user", "hi")),
            maxTokens = 100,
            defaultModel = "openai/gpt-oss-120b:free",
            fallbackModels = emptyList(),
            modelOverride = null,
            responseFormat = "json_object",
        )
        val rf = payload["response_format"] as? JsonObject
        assertTrue(rf != null && rf["type"] == JsonPrimitive("json_object"))
    }

    @Test
    fun `complete payload omits response_format when null`() {
        val payload = OpenRouterChatLlm.buildCompletePayload(
            messages = listOf(ChatMessage("user", "hi")),
            maxTokens = 100,
            defaultModel = "openai/gpt-oss-120b:free",
            fallbackModels = emptyList(),
            modelOverride = null,
            responseFormat = null,
        )
        assertTrue("response_format" !in payload)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "jarvis.OpenRouterChatLlmResponseFormatTest"`
Expected: FAIL — `buildCompletePayload` doesn't exist as a `companion object` member.

- [ ] **Step 4: Extract payload-build into a testable companion + add `responseFormat` param**

In `OpenRouterChatLlm.kt`, before `complete()` (around line 87), add a companion object:

```kotlin
companion object {
    fun buildCompletePayload(
        messages: List<ChatMessage>,
        maxTokens: Int,
        defaultModel: String,
        fallbackModels: List<String>,
        modelOverride: String?,
        responseFormat: String? = null,
    ) = kotlinx.serialization.json.buildJsonObject {
        when {
            modelOverride != null -> put("model", modelOverride)
            fallbackModels.isEmpty() -> put("model", defaultModel)
            else -> put("models", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(defaultModel))
                fallbackModels.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        }
        put("max_tokens", maxTokens)
        put("messages", kotlinx.serialization.json.buildJsonArray {
            for (m in messages) {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
        })
        if (responseFormat != null) {
            put("response_format", kotlinx.serialization.json.buildJsonObject {
                put("type", responseFormat)
            })
        }
    }
}
```

Update the instance `complete()` (line 88-105) to delegate:

```kotlin
override suspend fun complete(
    messages: List<ChatMessage>,
    maxTokens: Int,
    responseFormat: String? = null,
): Pair<String, String> {
    val payload = buildCompletePayload(
        messages = messages,
        maxTokens = maxTokens,
        defaultModel = defaultModel,
        fallbackModels = fallbackModels,
        modelOverride = null,
        responseFormat = responseFormat,
    )
    return postChat(payload)
}
```

ALSO update the `Llm` interface in `src/main/kotlin/jarvis/Llm.kt` if `complete()` is declared there — add the `responseFormat: String? = null` default param (verify with grep before touching).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "jarvis.OpenRouterChatLlmResponseFormatTest"`
Expected: PASS.

- [ ] **Step 6: Wire DrillGrader to request `json_object`**

In `src/main/kotlin/jarvis/tutor/DrillGrader.kt:112-118`, change:

```kotlin
val (raw, _) = llm.complete(
    listOf(
        ChatMessage("system", systemPrompt),
        ChatMessage("user", userMsg),
    ),
    maxTokens = 600,
)
```

to:

```kotlin
val (raw, modelResolved) = llm.complete(
    listOf(
        ChatMessage("system", systemPrompt),
        ChatMessage("user", userMsg),
    ),
    maxTokens = 600,
    responseFormat = "json_object",
)
```

(Note: this conflicts with the change made in Task A.2 Step 3 — keep A.2's destructuring of `modelResolved` and just add the `responseFormat` arg.)

- [ ] **Step 7: Run all backend tests**

Run: `./gradlew test`
Expected: 702+2=704 PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/jarvis/OpenRouterChatLlm.kt \
        src/main/kotlin/jarvis/Llm.kt \
        src/main/kotlin/jarvis/tutor/DrillGrader.kt \
        src/test/kotlin/jarvis/OpenRouterChatLlmResponseFormatTest.kt
git commit -m "feat(grader): request response_format=json_object via OpenRouter to coax JSON-only output"
```

---

### Task A.6: Bump `maxTokens` cap for code-grading path (root-cause mitigation)

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/DrillGrader.kt` — split `grade()` `maxTokens` by `isCode`.

- [ ] **Step 1: Write failing test asserting code-grading uses higher cap**

Append to `DrillGraderTest.kt`:

```kotlin
@Test
fun `grade uses maxTokens=1200 for code grading path`() = kotlinx.coroutines.runBlocking {
    var observedMaxTokens = -1
    val capturingLlm = object : jarvis.OpenRouterChatLlm() {
        override suspend fun complete(
            messages: List<jarvis.ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> {
            observedMaxTokens = maxTokens
            return """{"correct":true,"rubric":{"foo":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}""" to "fake/model"
        }
    }
    DrillGrader.grade(
        problemStatement = "p", userAttempt = "a", expectedHint = "h",
        llm = capturingLlm, language = "r", referenceSolution = "x", rubricItems = listOf("foo"),
    )
    assertEquals(1200, observedMaxTokens)
}

@Test
fun `grade keeps maxTokens=600 for text grading path`() = kotlinx.coroutines.runBlocking {
    var observedMaxTokens = -1
    val capturingLlm = object : jarvis.OpenRouterChatLlm() {
        override suspend fun complete(
            messages: List<jarvis.ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> {
            observedMaxTokens = maxTokens
            return """{"correct":true,"rubric":{"numeric":true,"mechanism":true,"justification":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}""" to "fake/model"
        }
    }
    DrillGrader.grade(
        problemStatement = "p", userAttempt = "a", expectedHint = "h",
        llm = capturingLlm, language = null,
    )
    assertEquals(600, observedMaxTokens)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "jarvis.tutor.DrillGraderTest.grade uses maxTokens=1200 for code grading path"`
Expected: FAIL (current code uses `maxTokens = 600` unconditionally).

- [ ] **Step 3: Implement split**

In `DrillGrader.kt:96-118` `grade()` body, replace the `val (raw, modelResolved) = llm.complete(...)` block with:

```kotlin
val effectiveMaxTokens = if (isCode) 1200 else 600
val (raw, modelResolved) = llm.complete(
    listOf(
        ChatMessage("system", systemPrompt),
        ChatMessage("user", userMsg),
    ),
    maxTokens = effectiveMaxTokens,
    responseFormat = "json_object",
)
```

- [ ] **Step 4: Run tests to verify both pass**

Run: `./gradlew test --tests "jarvis.tutor.DrillGraderTest"`
Expected: PASS for both new tests; existing tests still PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/DrillGrader.kt src/test/kotlin/jarvis/tutor/DrillGraderTest.kt
git commit -m "fix(grader): bump maxTokens to 1200 for code-grading path to prevent JSON truncation"
```

---

### Task A.7: Add aggressive JSON-extract fallback to `parseGradeJson`

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/DrillGrader.kt:67-84`

- [ ] **Step 1: Write failing tests for preamble + trailing-chatter extraction**

Append to `DrillGraderTest.kt`:

```kotlin
@Test
fun `parseGradeJson extracts JSON from preamble chatter`() {
    val raw = """Sure! Here is your grade in JSON format:
{"correct":true,"rubric":{"numeric":true,"mechanism":true,"justification":true},"score":1.0,"misconception":null,"elaborated_feedback":"ok"}
Hope that helps!"""
    val r = DrillGrader.parseGradeJson(raw)
    assertNotNull(r)
    assertTrue(r.correct)
    assertEquals(1.0, r.score, 0.001)
}

@Test
fun `parseGradeJson falls back to first balanced brace block`() {
    val raw = """{"meta":"this should be skipped"}
{"correct":false,"rubric":{"foo":false},"score":0.0,"misconception":"OTHER","elaborated_feedback":"nope"}"""
    val r = DrillGrader.parseGradeJson(raw)
    // First balanced block is the meta — has no `correct` key → parseGradeJson should return null.
    // This documents that aggressive extract is bounded: type-check still rejects garbage blocks.
    assertNull(r)
}

@Test
fun `parseGradeJson rejects extracted block missing required fields`() {
    val raw = """Here you go: {"correct":true} done."""
    // Block extracted but missing rubric/score/elaborated_feedback → null.
    assertNull(DrillGrader.parseGradeJson(raw))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "jarvis.tutor.DrillGraderTest.parseGradeJson extracts JSON from preamble chatter"`
Expected: FAIL (current strip-fence-only path returns null on preamble).

- [ ] **Step 3: Implement balanced-brace extraction fallback**

Replace `parseGradeJson` (lines 67-84) with:

```kotlin
fun parseGradeJson(raw: String): GradeResult? {
    // Attempt 1: existing strip-fence path.
    val direct = tryParse(raw.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    if (direct != null) return direct

    // Attempt 2: extract first balanced `{...}` block.
    val extracted = extractFirstBalancedBraceBlock(raw) ?: return null
    return tryParse(extracted)
}

private fun extractFirstBalancedBraceBlock(s: String): String? {
    val start = s.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escape = false
    for (i in start until s.length) {
        val c = s[i]
        if (escape) { escape = false; continue }
        if (c == '\\' && inString) { escape = true; continue }
        if (c == '"') { inString = !inString; continue }
        if (inString) continue
        when (c) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return s.substring(start, i + 1)
            }
        }
    }
    return null
}

private fun tryParse(cleaned: String): GradeResult? {
    return try {
        val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(cleaned) as? JsonObject
            ?: return null
        val correct = (obj["correct"] as? JsonPrimitive)?.boolean ?: return null
        val rubricObj = obj["rubric"] as? JsonObject ?: return null
        val rubric = rubricObj.mapValues {
            (it.value as? JsonPrimitive)?.boolean ?: return null
        }
        val score = (obj["score"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val misconception = (obj["misconception"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() && it != "null" }
        val feedback = (obj["elaborated_feedback"] as? JsonPrimitive)?.contentOrNull ?: return null
        GradeResult(correct, rubric, score, misconception, feedback)
    } catch (_: Exception) { null }
}
```

- [ ] **Step 4: Run targeted tests**

Run: `./gradlew test --tests "jarvis.tutor.DrillGraderTest"`
Expected: all new + existing parser tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/DrillGrader.kt src/test/kotlin/jarvis/tutor/DrillGraderTest.kt
git commit -m "feat(grader): parseGradeJson balanced-brace fallback for preamble + trailing chatter"
```

---

### Task A.8: Phase A integration check + push

**Files:** none (verification + push only).

- [ ] **Step 1: Run full backend test suite**

Run: `./gradlew test`
Expected: ≥702 PASS (baseline) + new tests from A.2/A.3/A.5/A.6/A.7 = roughly 711+ PASS.

- [ ] **Step 2: Run full tools test suite**

Run: `npm run test:tools --prefix tools`
Expected: 113 + 1 (new event-log-reader status_in test) = 114 PASS.

- [ ] **Step 3: Verify git log + push**

```bash
git log --oneline origin/main..HEAD
# Expect ~7 commits: A.1, A.2, A.3, A.4, A.5, A.6, A.7
git push origin main
```

- [ ] **Step 4: Deploy to VPS + smoke-test grader live (production verification)**

Run the existing deploy script (verify it exists):

```bash
ls tools/deploy.sh && bash tools/deploy.sh
```

Then trigger ONE real drill grade via the live `/tutor/` page (Alex's normal workflow — answer a drill question), then SSH to VPS and inspect the newest envelope:

```bash
ssh root@46.247.109.91 "tail -1 /opt/jarvis/data/private/tutor_events.\$(date -u +%Y-%m-%d).jsonl | python3 -m json.tool | grep -E 'status|llm_output_raw_truncated|model_resolved'"
```

Expected: `status: "ok"` with populated `rubric`. If `status: "parse_error"`, the new `llm_output_raw_truncated` field will contain the actual model output for diagnosis — that itself is acceptance evidence that A.1+A.3 shipped correctly.

- [ ] **Step 5: Document Phase A acceptance in the standin-findings folder**

Create `docs/standin-findings/phase-a-grader-hardening-2026-05-16.md` with:
- Commit hashes
- Test count delta
- Live-grade envelope sample (status + new field populated)
- Council 1 verdict compliance: items addressed (Layer 3 ✓, response_format ✓, maxTokens bump ✓, regex extract ✓, X filter ✓); items deferred (Layer 2 blind retry — dropped pending observability data from Layer 3)

Commit:

```bash
git add docs/standin-findings/phase-a-grader-hardening-2026-05-16.md
git commit -m "docs(grader): Phase A acceptance — council 1778881174 verdict shipped"
git push origin main
```

---

## Phase B — Y tripwire refinement (Council 2)

### Task B.1: AND-gate `findings_count == 0` to tripwire suspect condition

**Files:**
- Modify: `tools/surface-y-tripwire.mjs`
- Modify: `tools/surface-y-tripwire.test.mjs`

- [ ] **Step 1: Write failing test for AND-gate**

Append to `tools/surface-y-tripwire.test.mjs`:

```javascript
test("flagSuspectRun does NOT flag suspect when competent transcript surfaces findings", () => {
  // Long competent run (would trip Path A: 6+ steps, zero confusion)
  // BUT persona surfaced 3 friction findings — high-signal, NOT invalid.
  const transcript = [
    { action: "type", observation: "tried solution X" },
    { action: "type", observation: "result is wrong somehow" },
    { action: "type", observation: "Y also wrong" },
    { action: "type", observation: "found UX issue: button unclear" },
    { action: "type", observation: "found UX issue: tooltip missing" },
    { action: "submit", observation: "found UX issue: error message vague" },
  ];
  const findingsCount = 3;
  const r = flagSuspectRun(transcript, findingsCount);
  assert.equal(r.suspect, false, "competent + nonzero findings = high-signal, NOT suspect");
});

test("flagSuspectRun flags suspect when competent transcript surfaces ZERO findings", () => {
  const transcript = [
    { action: "type", observation: "tried solution" },
    { action: "type", observation: "" },
    { action: "type", observation: "" },
    { action: "type", observation: "" },
    { action: "type", observation: "" },
    { action: "submit", observation: "" },
  ];
  const findingsCount = 0;
  const r = flagSuspectRun(transcript, findingsCount);
  assert.equal(r.suspect, true, "competent + zero findings = invalid friction signal");
});

test("flagSuspectRun ignores findings_count for naive transcripts", () => {
  // Short run with confusion — naive path, doesn't trip the heuristic regardless of findings.
  const transcript = [
    { action: "ask_sidekick", observation: "confused about which formula" },
    { action: "type", observation: "let me try X" },
    { action: "submit", observation: "guess" },
  ];
  const r = flagSuspectRun(transcript, 0);
  assert.equal(r.suspect, false, "naive run is never suspect regardless of findings_count");
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `node --test tools/surface-y-tripwire.test.mjs`
Expected: FAIL — `flagSuspectRun` currently ignores findings_count entirely; first test fails because competent-shape currently flags suspect.

- [ ] **Step 3: Implement AND-gate**

In `tools/surface-y-tripwire.mjs`, change `flagSuspectRun` signature + body:

```javascript
export function flagSuspectRun(transcript, findingsCount = 0, thresholds = DEFAULT_THRESHOLDS) {
  const signals = computeSignals(transcript);
  const rationale = [];

  // Path A: zero-confusion AND zero-ask_sidekick across a long run.
  // AND-gated on findings_count == 0: a competent persona that nevertheless
  // surfaces friction findings is high-signal, not invalid.
  if (
    transcript.length >= thresholds.min_steps_for_zero_confusion_flag &&
    signals.ask_sidekick_count === 0 &&
    signals.confusion_step_count === 0 &&
    findingsCount === 0
  ) {
    rationale.push(
      `Run ran ${transcript.length} steps with zero ask_sidekick AND zero confusion-keyword observations AND zero findings — ` +
      `a real naive student would have used at least one, OR a competent persona that found UX friction would be high-signal.`,
    );
  }

  // Path B: first submit at step <= N with no prior friction.
  // Same AND-gate: clean fast submit IS suspect only if no findings surfaced.
  const submitIdx = transcript.findIndex(t => t.action === "submit");
  if (
    submitIdx !== -1 &&
    submitIdx <= thresholds.submit_step_index_max_for_flawless_flag &&
    transcript.slice(0, submitIdx + 1).every(t =>
      t.action !== "ask_sidekick" && t.action !== "give_up" && !t.error
    ) &&
    findingsCount === 0
  ) {
    rationale.push(
      `submit at step ${submitIdx + 1} with no prior ask_sidekick, give_up, or error, AND zero findings — ` +
      `clean first-try answer with nothing surfaced.`,
    );
  }

  return {
    suspect: rationale.length > 0,
    signals,
    thresholds,
    findings_count: findingsCount,
    rationale,
  };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `node --test tools/surface-y-tripwire.test.mjs`
Expected: all new + existing tripwire tests PASS.

- [ ] **Step 5: Update existing tripwire tests that don't pass `findingsCount`**

Run: `node --test tools/surface-y-tripwire.test.mjs` again — if any pre-existing test now flakes (because default `findingsCount=0` changes its behavior), update those tests to explicitly pass `findingsCount` matching the scenario. Look for tests that expect `suspect: true` on competent-shape — those now also need `findingsCount: 0` explicitly. Tests that expect `suspect: false` are unaffected.

- [ ] **Step 6: Commit**

```bash
git add tools/surface-y-tripwire.mjs tools/surface-y-tripwire.test.mjs
git commit -m "feat(surface-y): tripwire AND-gates suspect on findings_count==0 (council 1778881175 first-principles refinement)"
```

---

### Task B.2: Pass `findingsCount` from `surface-y.mjs` to tripwire + emit `tripwire_confidence_band` field

**Files:**
- Modify: `tools/surface-y.mjs` — count findings, pass to tripwire, add frontmatter field
- Modify: `tools/surface-y.test.mjs` — assert new frontmatter field

- [ ] **Step 1: Locate the findings filter + tripwire call in surface-y.mjs**

Line 358 has the filter that defines findings:

```javascript
...transcript.filter(t => t.observation && t.action !== "error" && t.action !== "stuck").map(t => `- step ${transcript.indexOf(t) + 1}: ${t.observation}`)
```

Lift this filter into a `const findings = ...` before the frontmatter-build block (around line 340).

Grep for the existing call to `flagSuspectRun` to find where tripwire is invoked. Update that call site to pass `findings.length`.

- [ ] **Step 2: Write failing test for `tripwire_confidence_band` frontmatter field**

In `tools/surface-y.test.mjs`, add (adapt to the existing test harness — likely uses a fake LLM):

```javascript
test("DRAFT-Y frontmatter includes tripwire_confidence_band", async () => {
  // Existing fake-LLM scaffolding — run a minimal Y session, capture the emitted markdown.
  const { stdout, draftPath } = await runYWithFakes({ /* existing fixture */ });
  const draft = readFileSync(draftPath, "utf8");
  assert.match(draft, /^tripwire_confidence_band:\s*thin_corpus_n\d+\s*$/m,
    "frontmatter must declare current corpus-size-based confidence band");
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `node --test tools/surface-y.test.mjs`
Expected: FAIL — frontmatter doesn't include the new field.

- [ ] **Step 4: Implement findings extraction + new frontmatter field**

Around line 340 (where frontmatter is built), insert before the frontmatter array:

```javascript
const findings = transcript.filter(t => t.observation && t.action !== "error" && t.action !== "stuck");
const findingsCount = findings.length;
const tripwire = flagSuspectRun(transcript, findingsCount);

// Confidence-band is a string declaring the calibration-corpus size that
// the tripwire's thresholds were tuned against. Hardcoded for now; bump
// when corpus grows. Per Council 1778881175 (Devil's Advocate concern:
// "name what guarantee means before chasing it").
const tripwireCorpusN = 2;
const tripwireConfidenceBand = `thin_corpus_n${tripwireCorpusN}`;
```

(If `flagSuspectRun` is currently called elsewhere with no `findingsCount`, replace that call site with the one above.)

In the frontmatter array (line 340+), add the field after `tripwire_status`:

```javascript
`tripwire_status: ${tripwire.suspect ? "suspect" : "clean"}`,
`tripwire_findings_count: ${findingsCount}`,
`tripwire_confidence_band: ${tripwireConfidenceBand}`,
```

And in the "Discovered unknown-unknowns" section (line 358), replace the filter-inline with the lifted `findings` variable:

```javascript
"## Discovered unknown-unknowns",
...findings.map(t => `- step ${transcript.indexOf(t) + 1}: ${t.observation}`),
```

- [ ] **Step 5: Run test to verify it passes**

Run: `node --test tools/surface-y.test.mjs`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add tools/surface-y.mjs tools/surface-y.test.mjs
git commit -m "feat(surface-y): pass findings_count to tripwire + emit tripwire_confidence_band frontmatter"
```

---

### Task B.3: Append 1-2 external authentic-naive few-shot exemplars to persona prompt

**Files:**
- Modify: `tools/surface-y-persona.mjs`

- [ ] **Step 1: Read current persona prompt**

Open `tools/surface-y-persona.mjs`. Locate the system-prompt string. Capture its current shape so the append is non-destructive.

- [ ] **Step 2: Write failing test that asserts the persona prompt includes a "naive student exemplar" block**

In `tools/surface-y-persona.test.mjs`, add:

```javascript
test("persona system prompt includes authentic-naive few-shot exemplars", () => {
  const { SYSTEM_PROMPT } = await import("./surface-y-persona.mjs");
  assert.match(SYSTEM_PROMPT, /AUTHENTIC-NAIVE EXEMPLAR/i, "must include hand-authored exemplar block");
  assert.match(SYSTEM_PROMPT, /ask_sidekick/i, "exemplar must demonstrate sidekick use");
  assert.match(SYSTEM_PROMPT, /(don't know|confused|stuck)/i, "exemplar must demonstrate confusion expression");
});
```

(Adjust the import path / export name if the persona prompt isn't currently an export — if it's inlined, add `export const SYSTEM_PROMPT = ...` and refactor the caller to use it.)

- [ ] **Step 3: Run test to verify it fails**

Run: `node --test tools/surface-y-persona.test.mjs`
Expected: FAIL.

- [ ] **Step 4: Append 1-2 hand-authored exemplars to the persona prompt**

Append this block at the END of the existing persona prompt (before any closing template literal):

```
AUTHENTIC-NAIVE EXEMPLAR (study this — mimic the SHAPE, never the literal text):

Step 1 (read problem) — observation: "ok so it's asking for the median... I think? not sure what 'sample size 10000' part is for"
Step 2 (try answer) — action: type "median"; observation: "hmm seems wrong, why would it want sample size if it's just median"
Step 3 (stuck) — action: ask_sidekick; payload: "i don't know what to do with sample size, can you hint"

ANOTHER AUTHENTIC-NAIVE EXEMPLAR:

Step 1 (read problem) — observation: "rlaplace? haven't seen that one. is it like rnorm?"
Step 2 (try code) — action: type "rlaplace(10000, 0, 1)"; observation: "running... wait does that even exist in base R"
Step 3 (confusion) — observation: "got an error saying could not find function — i think i need a package?"
Step 4 (ask) — action: ask_sidekick; payload: "do i need to install something for rlaplace"

These exemplars are hand-authored, NOT lifted from the calibration corpus. They demonstrate the SHAPE of authentic-naive transcripts: hedged language, observable confusion, sidekick use when stuck. Do NOT copy the text verbatim — these are SHAPE references only.
```

- [ ] **Step 5: Run test to verify it passes**

Run: `node --test tools/surface-y-persona.test.mjs`
Expected: PASS.

- [ ] **Step 6: Run full tools test suite to confirm no regression**

Run: `npm run test:tools --prefix tools`
Expected: 114 + tripwire deltas + persona delta = ~117+ PASS.

- [ ] **Step 7: Commit**

```bash
git add tools/surface-y-persona.mjs tools/surface-y-persona.test.mjs
git commit -m "feat(surface-y-persona): append 2 hand-authored authentic-naive exemplars (external, non-circular)"
```

---

### Task B.4: Push Phase B + document acceptance

**Files:**
- Create: `docs/standin-findings/phase-b-tripwire-refinement-2026-05-16.md`

- [ ] **Step 1: Verify all tools tests still pass**

Run: `npm run test:tools --prefix tools`
Expected: full green.

- [ ] **Step 2: Write Phase B acceptance doc**

Create `docs/standin-findings/phase-b-tripwire-refinement-2026-05-16.md`:

```markdown
# Phase B acceptance — Y tripwire refinement (council 1778881175)

**Date:** 2026-05-16
**Commits:** (fill from `git log --oneline`)

## Council 1778881175 verdict compliance

- ✓ B.1 — `flagSuspectRun` AND-gates suspect on `findings_count == 0` (First Principles 2x2 refinement).
- ✓ B.2 — DRAFT-Y frontmatter includes `tripwire_confidence_band: thin_corpus_n2` (Devil's Advocate's "name the guarantee").
- ✓ B.3 — Persona prompt appended with 2 hand-authored exemplars (NOT from n=2 calibration corpus — avoids the circular-calibration risk Risk Analyst flagged).
- DEFERRED — (b) LLM-judge tripwire — dropped indefinitely per judge verdict (silent-skip regression risk).
- DEFERRED — (c) schema-constrained generation — dropped indefinitely (naturalness destruction + `:free` model support gap).
- DEFERRED — corpus growth to n≥5 authentic + n≥3 adversarial — documented as durable carry-over; happens organically.

## Test deltas

- `tools/surface-y-tripwire.test.mjs`: +3 tests (AND-gate behaviors).
- `tools/surface-y.test.mjs`: +1 test (tripwire_confidence_band frontmatter).
- `tools/surface-y-persona.test.mjs`: +1 test (exemplar presence).

## Live-run acceptance

Run one Y session: `node tools/surface-y.mjs --task=01KR6K07T6PATPRR5KH1JXYF8E`

Inspect the emitted DRAFT-Y-*.md:
- Frontmatter must include `tripwire_status`, `tripwire_findings_count`, `tripwire_confidence_band`.
- `## Behavioral-competence tripwire` body section must list findings_count alongside signals.

## Carry-overs

- Corpus growth: log future Y runs as authentic-naive samples in this folder. Threshold re-tuning gated on n≥5 authentic + n≥3 adversarial-leaked.
- Adversarial-leaked fixture authoring: TODO — splice Claude-tier persona transcripts offline (do NOT run Claude-CLI live on Y per binding council 1778839098).
```

- [ ] **Step 3: Commit + push**

```bash
git add docs/standin-findings/phase-b-tripwire-refinement-2026-05-16.md
git commit -m "docs(surface-y): Phase B acceptance — council 1778881175 verdict shipped"
git push origin main
```

---

## Phase C — Re-seed X fixtures (Trace 9+10) with healthy-grader output

### Task C.1: Re-run Spec A v2 seeder against post-Phase-A grader

**Files:** ops only (no code changes).

- [ ] **Step 1: Confirm Phase A deploy is live**

Run on local machine:

```bash
curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1
```

Compare against the bundle hash printed by `tools/deploy.sh` at end of Phase A. Equal → Phase A is live.

- [ ] **Step 2: Run the seeder**

```bash
node tools/seed-tutor-events.mjs --task=01KR6K07T6PATPRR5KH1JXYF8E
```

Wait for completion. Capture STDOUT (it logs the 2 event IDs produced).

- [ ] **Step 3: Inspect the produced envelopes**

```bash
ssh root@46.247.109.91 "tail -2 /opt/jarvis/data/private/tutor_events.\$(date -u +%Y-%m-%d).jsonl | python3 -m json.tool"
```

Verify each event's `status`:
- Both `status: "ok"` with populated `rubric` → proceed to C.2 (promote).
- One or both `status: "parse_error"` → check `llm_output_raw_truncated` field for the raw model output. If raw output now reveals a fixable issue (e.g. preamble that regex extract should have caught), re-run Phase A's regex test against the raw text. Otherwise: log to acceptance doc and try again later (grader is intrinsically flaky on the `:free` band — this is documented in the Phase A acceptance doc).

- [ ] **Step 4 (only if Step 3 produced two `status: "ok"` events): Capture the new envelope fields locally**

```bash
ssh root@46.247.109.91 "tail -2 /opt/jarvis/data/private/tutor_events.\$(date -u +%Y-%m-%d).jsonl" > /tmp/trace-9-10-real.jsonl
```

Inspect with `cat /tmp/trace-9-10-real.jsonl | python3 -m json.tool`. Record:
- `event_id`
- `task_id`
- `rubric` (the actual rubric chip names + booleans)
- `misconception`
- `elaboratedFeedback`
- `status`
- `model_resolved`

### Task C.2: Promote Trace 9+10 with real-rubric-grounded INV labels

**Files:**
- Modify: `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md`

- [ ] **Step 1: Open the bootstrap-traces doc**

Read the CANDIDATE Trace 9 + Trace 10 sections (around line 162-190 per prior grep).

- [ ] **Step 2: Replace CANDIDATE labels with real-rubric labels**

For each trace:
- Update the `rubric_chip_text` field with the actual rubric keys from the new envelope.
- Update INV-02 verdict: PASS if rubric has named criteria, FAIL if any chip is unnamed/empty.
- Update INV-08 verdict: FAIL if any chip is in `snake_case`, PASS if all chips are plain English, N_A if no chips.
- Remove the "CANDIDATE" prefix from the section heading.
- Update the closing notes block — replace "weak fixture" caveat with a brief note that these are now real-rubric-grounded.

- [ ] **Step 3: Update the trailing notes section**

In the closing notes section of `bootstrap-traces.md` (around line 188+), strike the "weak fixtures" caveat and replace with:

```markdown
1. **Trace 9+10 are real-rubric-grounded** (re-seeded 2026-05-16 after Phase A grader hardening). Both events landed `status: "ok"` with populated rubric chips. Labels are now mechanically derived from real rubric content, not parse-error fallback shape.
```

- [ ] **Step 4: Run `surface-x` calibration against the new fixtures to verify they grade cleanly**

```bash
node tools/surface-x.mjs --calibrate --threshold=0.80
```

Expected: calibration result reflects the new fixtures landing within K-of-N agreement. Compare against pre-re-seed numbers (10/11 with Claude judge per BRIDGE.md). New traces should not regress.

- [ ] **Step 5: Commit + push**

```bash
git add docs/standin-findings/golden/2026-05-13-bootstrap-traces.md
git commit -m "docs(x-fixture): promote Trace 9+10 from CANDIDATE to real-rubric-grounded (post Phase A re-seed)"
git push origin main
```

---

## Self-Review

**1. Spec coverage:** Council 1 verdict items addressed — Layer 3 (A.1+A.3), Surface X filter (A.4), `response_format` (A.5), maxTokens bump (A.6), Layer 1 regex extract (A.7), Layer 2 dropped per judge. Council 2 verdict items addressed — findings_count gate (B.1), confidence_band frontmatter (B.2), external exemplars (B.3), corpus-growth as durable carry-over (B.4 doc). Re-seed C.1+C.2 closes the deferred carry-over from BRIDGE 2026-05-15T21:09.

**2. Placeholder scan:** No "TBD" / "fill in details" / "similar to Task N" patterns. Every code step has the actual code. Every test step has the test body.

**3. Type consistency:** `GradeAttempt(parsed, rawOutput, modelResolved)` referenced in A.2, A.3, A.6 — same field names throughout. `flagSuspectRun(transcript, findingsCount, thresholds)` signature consistent in B.1 / B.2. `tripwire_confidence_band` field name consistent in B.2 / B.4.

**4. Build+mount pairing:** N/A — this plan has no new frontend components. Phase A modifies backend Kotlin + Node tools; Phase B modifies Node tools; Phase C modifies docs. The 2026-05-11 ghost-component lesson doesn't apply.

**5. Component-reuse contract:** N/A — no "mount existing component X in new site Y" tasks in this plan.

**6. `data-testid` grep:** N/A — no visual acceptance section in source verdicts. Phase A acceptance is verified via SSH-inspecting the production envelope (Task A.8 Step 4); Phase B via DRAFT-Y frontmatter scan (B.4 doc).

---

## Execution recommendation

Phase A is the biggest block (~7 commits, Kotlin server + Node tools, requires deploy + live smoke). Phase B is small (~3 commits, Node-only). Phase C is ops + 1 doc commit, gated on A being live.

Recommended execution: **subagent-driven** for Phase A (heterogeneous file set, each subagent gets a clean task slice + review checkpoint); **inline** for Phase B (small + locally coherent); **inline** for Phase C (gates on live deploy, needs interactive ssh inspection).
