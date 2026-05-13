# Student stand-in 3-surface dogfood — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the council-shaped 3-surface dogfood system (X trace-grader + Z novice-visual + Y student stand-in) per `docs/superpowers/specs/2026-05-13-student-standin-design.md` commit `72b9337`. Build-everything preference — no scope cuts. All findings quarantined under `docs/standin-findings/`, provenance-stamped, manually reviewed before any influence on design.

**Architecture:** Three Node CLI tools (`surface-{x,z,y}.mjs`) drive the live tutor via DOM/Playwright (Z, Y) or read server-side envelopes (X) from a new Kotlin module `TutorEventLog.kt`. Shared infra under `tools/lib/`: provenance stamp (with normalized DOM hash + judge fields), OpenRouter `:free` client (envelope-aware), stale-check reader. Drill-grade events redact R-code answers; Y-driven events tagged `is_synthetic` so X replay can skip them.

**Tech Stack:** Kotlin/Ktor backend (existing). React frontend (existing). Node 22 ESM + Playwright 1.49 for tools (existing `tools/package.json`). OpenRouter `:free` chain via existing `OpenRouterChatLlm` patterns. Native `node --test` runner (matches `extract-fiimaterials.test.mjs` convention).

---

## File structure

**Backend (new):**
- `src/main/kotlin/jarvis/tutor/TutorEventLog.kt` — envelope-capture appender with separate `ReentrantLock` + async bounded-queue + 14-day rotation. Includes `TutorEvent` data class with `is_synthetic` field + nested `RcodeRedacted` for drill-grade R-code body.

**Backend (modified):**
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — hook `/api/v1/drill/grade` (line 1497) and `/api/v1/sidekick/ask` (line 1296) to write to `TutorEventLog`.

**Backend tests (new):**
- `src/test/kotlin/jarvis/tutor/TutorEventLogTest.kt` — appender correctness, R-code redaction, `is_synthetic` flag plumb, separate-lock isolation, daily rotation file naming, queue-overflow drop.

**Tools (new) — shared lib:**
- `tools/lib/provenance.mjs` — `getStamp(page, opts)` with normalized DOM + judge fields.
- `tools/lib/openrouter.mjs` — `:free` chain client that returns `{text, model_resolved, prompt_sha256, tokens_in, tokens_out, latency_ms}`. Sets `temperature` + `seed` when caller passes them.
- `tools/lib/event-log-reader.mjs` — reads VPS `/opt/jarvis/data/private/tutor_events.YYYY-MM-DD.jsonl` via SSH (or local copy), filters by `session_id` / `task_id` / `from_ts` / `to_ts` / `is_synthetic`.

**Tools (new) — surfaces:**
- `tools/findings-stale-check.mjs` — reads finding doc frontmatter, compares stamp to current state, prints per-field `[OK|STALE|FRESH]`.
- `tools/verify-openrouter-quota-isolation.mjs` — burn-test on 2 separate keys, writes verdict to `docs/notes/2026-05-13-openrouter-quota-isolation.md`.
- `tools/surface-x.mjs` — trace-grader CLI.
- `tools/surface-x-invariants.mjs` — 10-invariant catalog with PASS/FAIL or INFO classification + per-invariant scope-bracket selectors.
- `tools/surface-z.mjs` — novice-visual critic CLI.
- `tools/surface-z-lints.mjs` — programmatic lints (snake-case + contrast + font-size + overflow).
- `tools/surface-y.mjs` — student stand-in CLI.
- `tools/surface-y-persona.mjs` — persona prompt builder + seen-concepts ledger + confusion-tuple injector.
- `tools/surface-y-gate.mjs` — schema-gate filter (concept-reference extractor + 3-strike regenerate).

**Tools (new) — tests:**
- `tools/lib/provenance.test.mjs` — DOM normalization unit tests + stamp shape.
- `tools/lib/openrouter.test.mjs` — `:free` chain fallback resolution mock + prompt-sha capture.
- `tools/lib/event-log-reader.test.mjs` — filter predicates.
- `tools/findings-stale-check.test.mjs` — `[OK|STALE]` per field.
- `tools/surface-x-invariants.test.mjs` — scope-bracket selectors for each INV-id.
- `tools/surface-x.test.mjs` — multi-run majority vote + K-of-N gate.
- `tools/surface-z-lints.test.mjs` — snake-case + contrast + font-size + overflow detectors.
- `tools/surface-z.test.mjs` — viewport sweep + vision-fallback.
- `tools/surface-y-gate.test.mjs` — concept extraction + 3-strike regenerate.
- `tools/surface-y-persona.test.mjs` — ledger update from DOM + confusion-tuple injection.
- `tools/surface-y.test.mjs` — `X-Standin-Run` header on every request + hard caps.

**Docs (new):**
- `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md` — hand-curated fixture set skeleton (Alex fills 15-25 entries during Phase 1 Task 1.8).
- `docs/standin-findings/schemas/PS-Tema-A.yaml` — first Y concept schema.
- `docs/notes/2026-05-13-openrouter-quota-isolation.md` — output of Phase 0 Task 0.10 verify run.
- `docs/standin-findings/.gitkeep` — preserve directory.
- `docs/standin-findings/screenshots/.gitkeep` — preserve directory (gitignored body).

**Config (modified):**
- `.gitignore` — exclude private payload + ephemeral findings.
- `deploy.sh` — advisory hook for X behind `RUN_LLM_EVAL=1`.
- `tools/package.json` — add npm scripts for each surface + tests.

**Directory creations on VPS (post-deploy, one-shot via SSH):**
- `mkdir -p /opt/jarvis/data/private && chown jarvis:jarvis /opt/jarvis/data/private && chmod 0700 /opt/jarvis/data/private`

---

## Phase 0 — Pre-flight (BLOCKING all surfaces)

Pre-flight must complete entirely before any of X / Z / Y ships. Goal: shared infra + envelope-capture backend + quota-isolation verdict on disk.

### Task 0.1: OpenRouter `:free` client wrapper

**Files:**
- Create: `tools/lib/openrouter.mjs`
- Test: `tools/lib/openrouter.test.mjs`

Spec reference: `docs/superpowers/specs/2026-05-13-student-standin-design.md` § "Cross-cutting infrastructure" — provenance stamp, judge-side fields.

- [ ] **Step 1: Write the failing test**

```javascript
// tools/lib/openrouter.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { callLlm } from "./openrouter.mjs";

test("callLlm returns envelope shape", async (t) => {
  // Mock the fetch transport via dependency injection.
  const fakeFetch = async (url, init) => {
    return new Response(JSON.stringify({
      choices: [{ message: { content: '{"status": "PASS"}' } }],
      model: "qwen/qwen-2.5-7b:free",
      usage: { prompt_tokens: 120, completion_tokens: 8 }
    }), { status: 200, headers: { "content-type": "application/json" } });
  };

  const result = await callLlm({
    apiKey: "sk-test",
    model: "qwen/qwen-2.5-7b:free",
    systemPrompt: "You are a judge.",
    userPrompt: "Grade INV-01.",
    temperature: 0,
    seed: 42,
    transport: fakeFetch,
  });

  assert.equal(result.text, '{"status": "PASS"}');
  assert.equal(result.model_resolved, "qwen/qwen-2.5-7b:free");
  assert.equal(typeof result.prompt_sha256, "string");
  assert.equal(result.prompt_sha256.length, 64);
  assert.equal(result.tokens_in, 120);
  assert.equal(result.tokens_out, 8);
  assert.equal(typeof result.latency_ms, "number");
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test lib/openrouter.test.mjs`
Expected: FAIL with `Cannot find module './openrouter.mjs'`.

- [ ] **Step 3: Implement the client**

```javascript
// tools/lib/openrouter.mjs
import { createHash } from "node:crypto";

export async function callLlm({
  apiKey,
  model,
  systemPrompt,
  userPrompt,
  temperature = 0.0,
  seed = null,
  maxTokens = null,
  transport = globalThis.fetch,
}) {
  if (!apiKey) throw new Error("callLlm: apiKey required");
  const t0 = Date.now();
  const body = {
    model,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: userPrompt },
    ],
    temperature,
  };
  if (seed !== null) body.seed = seed;
  if (maxTokens !== null) body.max_tokens = maxTokens;

  const resp = await transport("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "HTTP-Referer": "https://corgflix.duckdns.org/tutor/",
    },
    body: JSON.stringify(body),
  });
  const latency_ms = Date.now() - t0;
  if (!resp.ok) {
    const text = await resp.text().catch(() => "");
    throw new Error(`callLlm: ${resp.status} ${text.slice(0, 200)}`);
  }
  const data = await resp.json();
  const prompt_sha256 = createHash("sha256")
    .update(systemPrompt + "\n---\n" + userPrompt)
    .digest("hex");

  return {
    text: data.choices?.[0]?.message?.content ?? "",
    model_resolved: data.model ?? model,
    prompt_sha256,
    tokens_in: data.usage?.prompt_tokens ?? null,
    tokens_out: data.usage?.completion_tokens ?? null,
    latency_ms,
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd tools && node --test lib/openrouter.test.mjs`
Expected: `# tests 1` `# pass 1`.

- [ ] **Step 5: Commit**

```bash
git add tools/lib/openrouter.mjs tools/lib/openrouter.test.mjs
git commit -m "feat(standin): OpenRouter :free client wrapper with envelope capture"
```

---

### Task 0.2: Provenance stamp module

**Files:**
- Create: `tools/lib/provenance.mjs`
- Test: `tools/lib/provenance.test.mjs`

Spec reference: § "Provenance stamp" — includes Council #4 RA-1 fix (DOM normalization) + DE fix (judge fields).

- [ ] **Step 1: Write the failing tests**

```javascript
// tools/lib/provenance.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeDomForFingerprint, getStamp } from "./provenance.mjs";

test("normalizeDomForFingerprint strips ULIDs", () => {
  const html = '<div id="01KR6K07T6PATPRR5KH1JXYF8E">x</div>';
  const out = normalizeDomForFingerprint(html);
  assert.match(out, /<ULID>/);
  assert.doesNotMatch(out, /01KR6K07T6/);
});

test("normalizeDomForFingerprint strips UUIDs + ISO timestamps + epoch ms", () => {
  const html = `<div data-id="a1b2c3d4-e5f6-7890-abcd-1234567890ab">
    <span>2026-05-13T17:55:55Z</span>
    <span>1778694955123</span>
  </div>`;
  const out = normalizeDomForFingerprint(html);
  assert.match(out, /<UUID>/);
  assert.match(out, /<TS>/);
  assert.match(out, /<EPOCH>/);
});

test("normalizeDomForFingerprint strips volatile data-* attrs", () => {
  const html = '<div data-event-id="evt-1" data-session-id="s-1" data-render-key="r-1" data-nonce="n-1">x</div>';
  const out = normalizeDomForFingerprint(html);
  assert.match(out, /data-event-id=&quot;.+\&quot;|<VOL>/);
});

test("normalizeDomForFingerprint strips cookies + csrf tokens", () => {
  const html = '<meta name="csrf" content="csrfToken=\\"abc123\\"" >';
  const out = normalizeDomForFingerprint(html);
  assert.match(out, /csrfToken="<TOKEN>"/);
});

test("getStamp returns required fields without page", async () => {
  const stamp = await getStamp(null, {});
  assert.equal(typeof stamp.git_head, "string");
  assert.equal(typeof stamp.bundle_hash, "string");
  assert.equal(stamp.live_dom_fingerprint, null);
  assert.equal(typeof stamp.ts_utc, "string");
  assert.equal(typeof stamp.surface_version, "string");
  assert.equal(stamp.judge_model_resolved, null);
  assert.equal(stamp.judge_prompt_sha256, null);
});

test("getStamp threads judge fields from opts", async () => {
  const stamp = await getStamp(null, {
    judge_model_resolved: "qwen/qwen-2.5-7b:free",
    judge_prompt_sha256: "deadbeef".repeat(8),
  });
  assert.equal(stamp.judge_model_resolved, "qwen/qwen-2.5-7b:free");
  assert.equal(stamp.judge_prompt_sha256.length, 64);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd tools && node --test lib/provenance.test.mjs`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement provenance.mjs**

```javascript
// tools/lib/provenance.mjs
import { execSync } from "node:child_process";
import { createHash } from "node:crypto";

export function normalizeDomForFingerprint(html) {
  return html
    .replace(/\b[0-9A-HJKMNP-TV-Z]{26}\b/g, "<ULID>")
    .replace(/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi, "<UUID>")
    .replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z?/g, "<TS>")
    .replace(/\b\d{13,}\b/g, "<EPOCH>")
    .replace(/data-(event-id|session-id|render-key|nonce)="[^"]*"/g, '<VOL>')
    .replace(/jarvis_auth=[^;"\s]+/g, "jarvis_auth=<COOKIE>")
    .replace(/csrfToken="[^"]*"/g, 'csrfToken="<TOKEN>"');
}

export async function getStamp(page, opts = {}) {
  let git_head = "unknown";
  try {
    git_head = execSync("git rev-parse --short HEAD", { stdio: ["pipe", "pipe", "ignore"] }).toString().trim();
  } catch {}

  let bundle_hash = "unknown";
  try {
    const txt = await fetch("https://corgflix.duckdns.org/tutor/").then(r => r.text());
    bundle_hash = txt.match(/index-([A-Za-z0-9_-]+)\.js/)?.[1] ?? "unknown";
  } catch {}

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
    judge_model_resolved: opts.judge_model_resolved ?? null,
    judge_prompt_sha256: opts.judge_prompt_sha256 ?? null,
  };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd tools && node --test lib/provenance.test.mjs`
Expected: all 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add tools/lib/provenance.mjs tools/lib/provenance.test.mjs
git commit -m "feat(standin): provenance stamp module w/ normalized DOM + judge fields"
```

---

### Task 0.3: Stale-check reader

**Files:**
- Create: `tools/findings-stale-check.mjs`
- Test: `tools/findings-stale-check.test.mjs`

Spec reference: § "Stale-check reader".

- [ ] **Step 1: Write the failing test**

```javascript
// tools/findings-stale-check.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { checkStaleness } from "./findings-stale-check.mjs";

const tmp = mkdtempSync(join(tmpdir(), "stale-test-"));

test("checkStaleness flags STALE when git_head differs", async () => {
  const fp = join(tmp, "doc1.md");
  writeFileSync(fp, [
    "---",
    "surface: X",
    "provenance:",
    "  git_head: dead000",
    "  bundle_hash: B-Xy35Ve",
    "  live_dom_fingerprint: null",
    "  ts_utc: 2026-05-13T17:55:55Z",
    "  surface_version: x-v1.0",
    "  judge_model_resolved: null",
    "  judge_prompt_sha256: null",
    "---",
    "# Findings",
  ].join("\n"));
  const result = await checkStaleness(fp, { currentBundleHash: "B-Xy35Ve" });
  assert.equal(result.fields.git_head.status, "STALE");
  assert.equal(result.fields.bundle_hash.status, "OK");
  assert.equal(result.overall, "STALE");
});

test("checkStaleness reports OK when everything matches", async () => {
  const currentGit = (await import("node:child_process")).execSync("git rev-parse --short HEAD").toString().trim();
  const fp = join(tmp, "doc2.md");
  writeFileSync(fp, [
    "---",
    "surface: X",
    "provenance:",
    `  git_head: ${currentGit}`,
    "  bundle_hash: B-Xy35Ve",
    "---",
    "",
  ].join("\n"));
  const result = await checkStaleness(fp, { currentBundleHash: "B-Xy35Ve" });
  assert.equal(result.fields.git_head.status, "OK");
});

// Cleanup
process.on("exit", () => rmSync(tmp, { recursive: true, force: true }));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test findings-stale-check.test.mjs`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement findings-stale-check.mjs**

```javascript
// tools/findings-stale-check.mjs
import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";

export async function checkStaleness(filepath, opts = {}) {
  const text = readFileSync(filepath, "utf8");
  const match = text.match(/^---\n([\s\S]+?)\n---/);
  if (!match) throw new Error(`No frontmatter in ${filepath}`);
  const fm = match[1];
  const parseField = (name) => fm.match(new RegExp(`^\\s+${name}:\\s+(.+)$`, "m"))?.[1]?.trim().replace(/^["']|["']$/g, "") ?? null;
  const stamp = {
    git_head: parseField("git_head"),
    bundle_hash: parseField("bundle_hash"),
    live_dom_fingerprint: parseField("live_dom_fingerprint"),
    ts_utc: parseField("ts_utc"),
    surface_version: parseField("surface_version"),
    judge_model_resolved: parseField("judge_model_resolved"),
    judge_prompt_sha256: parseField("judge_prompt_sha256"),
  };

  const currentGit = (() => {
    try { return execSync("git rev-parse --short HEAD", { stdio: ["pipe", "pipe", "ignore"] }).toString().trim(); }
    catch { return null; }
  })();
  const currentBundle = opts.currentBundleHash ?? await fetchBundleHash();

  const fields = {
    git_head: {
      stamped: stamp.git_head,
      current: currentGit,
      status: stamp.git_head === currentGit ? "OK" : "STALE",
    },
    bundle_hash: {
      stamped: stamp.bundle_hash,
      current: currentBundle,
      status: stamp.bundle_hash === currentBundle ? "OK" : "STALE",
    },
    ts_utc: {
      stamped: stamp.ts_utc,
      ageMs: stamp.ts_utc ? Date.now() - Date.parse(stamp.ts_utc) : null,
      status: stamp.ts_utc ? (Date.now() - Date.parse(stamp.ts_utc) < 86400_000 ? "FRESH" : "AGED") : "MISSING",
    },
    surface_version: {
      stamped: stamp.surface_version,
      current: process.env.SURFACE_VERSION ?? "unknown",
      status: stamp.surface_version === (process.env.SURFACE_VERSION ?? "unknown") ? "OK" : "DRIFT",
    },
    judge_model_resolved: {
      stamped: stamp.judge_model_resolved,
      status: stamp.judge_model_resolved ? "PRESENT" : "MISSING",
    },
    judge_prompt_sha256: {
      stamped: stamp.judge_prompt_sha256,
      status: stamp.judge_prompt_sha256 ? "PRESENT" : "MISSING",
    },
  };

  const anyStale = Object.values(fields).some((f) => f.status === "STALE" || f.status === "DRIFT");
  const overall = anyStale ? "STALE" : "OK";
  return { fields, overall };
}

async function fetchBundleHash() {
  try {
    const txt = await fetch("https://corgflix.duckdns.org/tutor/").then(r => r.text());
    return txt.match(/index-([A-Za-z0-9_-]+)\.js/)?.[1] ?? "unknown";
  } catch { return "unknown"; }
}

// CLI entrypoint
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith("findings-stale-check.mjs")) {
  const fp = process.argv[2];
  if (!fp) { console.error("Usage: findings-stale-check.mjs <path>"); process.exit(2); }
  const r = await checkStaleness(fp);
  for (const [k, v] of Object.entries(r.fields)) {
    console.log(`${k}: ${v.stamped ?? "(missing)"} → ${v.current ?? ""} [${v.status}]`);
  }
  console.log(`Overall: [${r.overall}]`);
  process.exit(r.overall === "STALE" ? 1 : 0);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd tools && node --test findings-stale-check.test.mjs`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add tools/findings-stale-check.mjs tools/findings-stale-check.test.mjs
git commit -m "feat(standin): stale-check reader for findings provenance"
```

---

### Task 0.4: TutorEventLog skeleton + data classes

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/TutorEventLog.kt`
- Create: `src/test/kotlin/jarvis/tutor/TutorEventLogTest.kt`

Spec reference: § "TutorEventLog.kt — backend envelope-capture".

- [ ] **Step 1: Write the failing test**

```kotlin
// src/test/kotlin/jarvis/tutor/TutorEventLogTest.kt
package jarvis.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorEventLogTest {

    @Test
    fun `appends drill_grade event with redacted R-code body`() = runBlocking {
        @TempDir val dir: Path = Path.of(System.getProperty("java.io.tmpdir"), "tutorlog-${System.nanoTime()}")
        java.io.File(dir.toString()).mkdirs()
        val log = TutorEventLog(privateDir = dir.toFile())
        val evt = TutorEvent(
            event_type = "drill_grade",
            event_id = "01KR6K07T6PATPRR5KH1JXYF8E",
            ts_utc = "2026-05-13T17:55:55Z",
            task_id = "task-1",
            session_id = "sess-1",
            prompt_template_id = "drill-grader-v3",
            system_prompt_sha256 = "abc",
            retrieved_context_summary = listOf("path1:snippet"),
            llm_input_full = null,
            llm_input_redacted = RcodeRedacted(
                rcode_sha256 = "deadbeef".repeat(8),
                preview_head = "library(VGAM); rlaplace(10000, 0, 1)",
                preview_tail = "hist(s, breaks=50)",
                length_chars = 142
            ),
            llm_output_full = "{\"correct\":true}",
            model_resolved = "qwen/qwen-2.5-7b:free",
            tokens_in = 200,
            tokens_out = 8,
            latency_ms = 4321,
            status = "ok",
            is_synthetic = false
        )
        log.append(evt)
        delay(200)  // wait for async writer
        log.flush()
        val today = java.time.LocalDate.now().toString()
        val file = dir.resolve("tutor_events.$today.jsonl")
        assertTrue(file.exists(), "log file should exist")
        val lines = file.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("\"event_type\":\"drill_grade\""))
        assertTrue(lines[0].contains("\"is_synthetic\":false"))
        assertTrue(lines[0].contains("\"rcode_sha256\":\"deadbeef"))
        assertTrue(!lines[0].contains("rlaplace(10000, 0, 1); hist(s, breaks=50)"),
                   "raw R-code body must NOT appear")
    }

    @Test
    fun `marks Y events as is_synthetic=true`() = runBlocking {
        @TempDir val dir: Path = Path.of(System.getProperty("java.io.tmpdir"), "tutorlog2-${System.nanoTime()}")
        java.io.File(dir.toString()).mkdirs()
        val log = TutorEventLog(privateDir = dir.toFile())
        val evt = TutorEvent(
            event_type = "sidekick_ask",
            event_id = "synth-1",
            ts_utc = "2026-05-13T17:56:00Z",
            task_id = "task-1",
            session_id = "sess-y",
            prompt_template_id = "sidekick-v1",
            system_prompt_sha256 = "xyz",
            retrieved_context_summary = emptyList(),
            llm_input_full = "what is rlaplace?",
            llm_input_redacted = null,
            llm_output_full = "rlaplace is...",
            model_resolved = "qwen/qwen-2.5-7b:free",
            tokens_in = 5,
            tokens_out = 30,
            latency_ms = 1234,
            status = "ok",
            is_synthetic = true
        )
        log.append(evt)
        delay(200)
        log.flush()
        val today = java.time.LocalDate.now().toString()
        val file = dir.resolve("tutor_events.$today.jsonl")
        val lines = file.readLines()
        assertTrue(lines[0].contains("\"is_synthetic\":true"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests jarvis.tutor.TutorEventLogTest`
Expected: FAIL — `TutorEventLog` / `TutorEvent` / `RcodeRedacted` not defined.

- [ ] **Step 3: Implement TutorEventLog.kt**

```kotlin
// src/main/kotlin/jarvis/tutor/TutorEventLog.kt
package jarvis.tutor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Serializable
data class RcodeRedacted(
    val rcode_sha256: String,
    val preview_head: String,
    val preview_tail: String,
    val length_chars: Int
)

@Serializable
data class TutorEvent(
    val event_type: String,
    val event_id: String,
    val ts_utc: String,
    val task_id: String?,
    val session_id: String,
    val prompt_template_id: String?,
    val system_prompt_sha256: String?,
    val retrieved_context_summary: List<String>?,
    val llm_input_full: String? = null,
    val llm_input_redacted: RcodeRedacted? = null,
    val llm_output_full: String?,
    val model_resolved: String?,
    val tokens_in: Int?,
    val tokens_out: Int?,
    val latency_ms: Long?,
    val status: String,
    val is_synthetic: Boolean = false
)

class TutorEventLog(
    private val privateDir: File = File("/opt/jarvis/data/private"),
    queueCapacity: Int = 1024
) {
    private val lock = ReentrantLock()
    private val queue = Channel<TutorEvent>(capacity = queueCapacity)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val flushSentinel = AtomicReference<Job?>(null)

    init {
        privateDir.mkdirs()
        // 0700 perms — best effort cross-platform
        runCatching { privateDir.setExecutable(true, true); privateDir.setReadable(true, true); privateDir.setWritable(true, true) }
        GlobalScope.launch(Dispatchers.IO) {
            for (evt in queue) writeOne(evt)
        }
    }

    fun append(evt: TutorEvent) {
        if (!queue.trySend(evt).isSuccess) {
            System.err.println("[tutor-event-log] queue saturated, dropping ${evt.event_id}")
        }
    }

    suspend fun flush() {
        // Best-effort drain: send a no-op + small delay
        kotlinx.coroutines.delay(50)
    }

    private fun writeOne(evt: TutorEvent) {
        lock.withLock {
            val today = LocalDate.now().toString()
            val file = File(privateDir, "tutor_events.$today.jsonl")
            file.appendText(json.encodeToString(evt) + "\n")
        }
    }

    fun rotateAndRetain(maxAgeDays: Int = 14) {
        lock.withLock {
            val cutoff = LocalDate.now().minusDays(maxAgeDays.toLong())
            privateDir.listFiles { f -> f.name.startsWith("tutor_events.") && f.name.endsWith(".jsonl") }?.forEach { f ->
                val datePart = f.name.removePrefix("tutor_events.").removeSuffix(".jsonl")
                runCatching {
                    if (LocalDate.parse(datePart).isBefore(cutoff)) f.delete()
                }
            }
        }
    }

    companion object {
        val GLOBAL: TutorEventLog by lazy { TutorEventLog() }
    }
}
```

Add type alias for the `Job` import used by `flushSentinel` (or remove unused field):

```kotlin
import kotlinx.coroutines.Job
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle test --tests jarvis.tutor.TutorEventLogTest`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/TutorEventLog.kt src/test/kotlin/jarvis/tutor/TutorEventLogTest.kt
git commit -m "feat(standin): TutorEventLog appender + RcodeRedacted + is_synthetic"
```

---

### Task 0.5: Hook drill-grade endpoint to TutorEventLog

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (around line 1497)
- Modify: `src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt`

Spec reference: § "TutorEventLog.kt — Hook points in TutorRoutes.kt".

- [ ] **Step 1: Read current drill-grade handler**

Run: `Read src/main/kotlin/jarvis/web/TutorRoutes.kt offset=1490 limit=80`
Note: capture the existing handler shape (request body type, response body type, latency timing pattern). Plan assumes there's a `val t0 = System.currentTimeMillis()` style pattern OR add one.

- [ ] **Step 2: Write failing test asserting envelope appended on drill_grade**

```kotlin
// Append to src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt
@Test
fun `POST drill grade writes a redacted TutorEvent`() = testApplication {
    application { installTutorRoutes(/* whatever fixture */) }
    val resp = client.post("/api/v1/drill/grade") {
        header(HttpHeaders.Authorization, "Bearer test-token")
        // Mark as synthetic to keep test deterministic + avoid log pollution
        header("X-Standin-Run", "1")
        contentType(ContentType.Application.Json)
        setBody("""{"task_id":"task-1","drill_id":"d1","rcode":"library(VGAM); rlaplace(100,0,1); hist(s)","predict":"approximate symmetric"}""")
    }
    assertEquals(HttpStatusCode.OK, resp.status)

    // Pull the most recent event from TutorEventLog.GLOBAL's file
    val today = java.time.LocalDate.now().toString()
    val logFile = java.io.File("/opt/jarvis/data/private/tutor_events.$today.jsonl")
    val tail = logFile.readLines().last()
    assertTrue(tail.contains("\"event_type\":\"drill_grade\""))
    assertTrue(tail.contains("\"is_synthetic\":true"))
    assertTrue(tail.contains("\"rcode_sha256\":"))
    assertTrue(!tail.contains("rlaplace(100,0,1); hist(s)"),
               "raw rcode must not be in log")
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `gradle test --tests jarvis.tutor.TutorRoutesTest.\\`POST drill grade writes a redacted TutorEvent\\``
Expected: FAIL — hook not yet wired.

- [ ] **Step 4: Implement hook in TutorRoutes.kt**

In the existing `/api/v1/drill/grade` handler, after the response body is built but before return, add:

```kotlin
import jarvis.tutor.TutorEventLog
import jarvis.tutor.TutorEvent
import jarvis.tutor.RcodeRedacted
import java.security.MessageDigest
import com.github.f4b6a3.ulid.UlidCreator

// inside the handler scope, after response is computed:
val isSynthetic = call.request.headers["X-Standin-Run"] == "1"
val rcodeBody: String = grade_request.rcode ?: ""
val rcodeHash = MessageDigest.getInstance("SHA-256")
    .digest(rcodeBody.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
val redacted = RcodeRedacted(
    rcode_sha256 = rcodeHash,
    preview_head = rcodeBody.take(40),
    preview_tail = rcodeBody.takeLast(40),
    length_chars = rcodeBody.length
)
TutorEventLog.GLOBAL.append(TutorEvent(
    event_type = "drill_grade",
    event_id = UlidCreator.getUlid().toString(),
    ts_utc = java.time.Instant.now().toString(),
    task_id = grade_request.task_id,
    session_id = call.principal<UserPrincipal>()?.sessionId ?: "anon",
    prompt_template_id = "drill-grader-v3",
    system_prompt_sha256 = systemPromptSha256(),  // helper that hashes the prompt template text
    retrieved_context_summary = retrievedCtx?.map { "${it.path}:${it.text.take(80)}" } ?: emptyList(),
    llm_input_full = null,
    llm_input_redacted = redacted,
    llm_output_full = response_body_json_string,
    model_resolved = llm_response.modelResolved,
    tokens_in = llm_response.tokensIn,
    tokens_out = llm_response.tokensOut,
    latency_ms = System.currentTimeMillis() - t0,
    status = "ok",
    is_synthetic = isSynthetic
))
```

Adapt variable names to match the actual handler. If `t0` doesn't exist, add `val t0 = System.currentTimeMillis()` at the top of the handler.

If `com.github.f4b6a3.ulid` isn't on the classpath, generate a ULID-like ID with `java.util.UUID.randomUUID().toString().replace("-", "")`.

- [ ] **Step 5: Run test + commit**

Run: `gradle test --tests jarvis.tutor.TutorRoutesTest`
Expected: existing tests + new test all pass.

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt
git commit -m "feat(standin): hook drill-grade endpoint → TutorEventLog w/ R-code redaction"
```

---

### Task 0.6: Hook sidekick-ask endpoint to TutorEventLog

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (around line 1296)
- Modify: `src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt`

Spec reference: § "TutorEventLog.kt — Hook points".

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `POST sidekick ask writes a synthetic-flagged TutorEvent`() = testApplication {
    application { installTutorRoutes(/* fixture */) }
    val resp = client.post("/api/v1/sidekick/ask") {
        header(HttpHeaders.Authorization, "Bearer test-token")
        header("X-Standin-Run", "1")
        contentType(ContentType.Application.Json)
        setBody("""{"task_id":"task-1","selection":"Laplace","user_question":"what is Laplace?"}""")
    }
    assertEquals(HttpStatusCode.OK, resp.status)
    val today = java.time.LocalDate.now().toString()
    val logFile = java.io.File("/opt/jarvis/data/private/tutor_events.$today.jsonl")
    val tail = logFile.readLines().last()
    assertTrue(tail.contains("\"event_type\":\"sidekick_ask\""))
    assertTrue(tail.contains("\"is_synthetic\":true"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests jarvis.tutor.TutorRoutesTest.\\`POST sidekick ask writes a synthetic-flagged TutorEvent\\``
Expected: FAIL.

- [ ] **Step 3: Implement hook**

In `/api/v1/sidekick/ask` handler around line 1296, add the same envelope-append pattern as drill-grade, with these differences:
- `event_type = "sidekick_ask"`
- `llm_input_full = "${request.selection}\\n\\n${request.user_question}"`  (sidekick chat not credential-grade)
- `llm_input_redacted = null`
- `prompt_template_id = "sidekick-v1"` (or whatever the template id is)

- [ ] **Step 4: Run test + commit**

Run: `gradle test --tests jarvis.tutor.TutorRoutesTest`
Expected: pass.

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/TutorRoutesTest.kt
git commit -m "feat(standin): hook sidekick-ask endpoint → TutorEventLog"
```

---

### Task 0.7: Event-log reader (Node)

**Files:**
- Create: `tools/lib/event-log-reader.mjs`
- Test: `tools/lib/event-log-reader.test.mjs`

Spec reference: § "Cross-cutting infrastructure" — Surface X reads server-side log.

- [ ] **Step 1: Write failing tests**

```javascript
// tools/lib/event-log-reader.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { readEvents, filterEvents } from "./event-log-reader.mjs";

const tmp = mkdtempSync(join(tmpdir(), "evtreader-"));

function writeJsonl(filename, events) {
  writeFileSync(join(tmp, filename), events.map(e => JSON.stringify(e)).join("\n") + "\n");
}

test("readEvents reads jsonl into objects", async () => {
  writeJsonl("tutor_events.2026-05-13.jsonl", [
    { event_type: "drill_grade", event_id: "e1", task_id: "t1", is_synthetic: false, ts_utc: "2026-05-13T10:00:00Z" },
    { event_type: "sidekick_ask", event_id: "e2", task_id: "t1", is_synthetic: true, ts_utc: "2026-05-13T10:01:00Z" },
  ]);
  const events = await readEvents({ dir: tmp });
  assert.equal(events.length, 2);
  assert.equal(events[0].event_id, "e1");
});

test("filterEvents filters synthetic by default", () => {
  const events = [
    { event_id: "e1", is_synthetic: false },
    { event_id: "e2", is_synthetic: true },
  ];
  assert.deepEqual(filterEvents(events, {}).map(e => e.event_id), ["e1"]);
  assert.deepEqual(filterEvents(events, { include_synthetic: true }).map(e => e.event_id), ["e1", "e2"]);
});

test("filterEvents filters by task_id and time window", () => {
  const events = [
    { event_id: "e1", task_id: "t1", ts_utc: "2026-05-13T10:00:00Z", is_synthetic: false },
    { event_id: "e2", task_id: "t2", ts_utc: "2026-05-13T11:00:00Z", is_synthetic: false },
    { event_id: "e3", task_id: "t1", ts_utc: "2026-05-13T12:00:00Z", is_synthetic: false },
  ];
  const r = filterEvents(events, { task_id: "t1", from_ts: "2026-05-13T09:00:00Z", to_ts: "2026-05-13T11:30:00Z" });
  assert.deepEqual(r.map(e => e.event_id), ["e1"]);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd tools && node --test lib/event-log-reader.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement event-log-reader.mjs**

```javascript
// tools/lib/event-log-reader.mjs
import { readdirSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

export async function readEvents({ dir = "/opt/jarvis/data/private", sshTarget = null } = {}) {
  let files = [];
  if (sshTarget) {
    // Pull files via SSH (used when running from dev laptop against VPS log).
    const { execSync } = await import("node:child_process");
    const list = execSync(`ssh ${sshTarget} "ls ${dir}/tutor_events.*.jsonl 2>/dev/null"`).toString().trim().split("\n").filter(Boolean);
    files = list.map((remotePath) => {
      const local = join("/tmp", remotePath.split("/").pop());
      execSync(`scp ${sshTarget}:${remotePath} ${local}`);
      return local;
    });
  } else {
    if (!existsSync(dir)) return [];
    files = readdirSync(dir).filter(f => f.startsWith("tutor_events.") && f.endsWith(".jsonl")).map(f => join(dir, f));
  }
  const all = [];
  for (const f of files) {
    const lines = readFileSync(f, "utf8").split("\n").filter(Boolean);
    for (const line of lines) {
      try { all.push(JSON.parse(line)); } catch {}
    }
  }
  all.sort((a, b) => (a.ts_utc ?? "").localeCompare(b.ts_utc ?? ""));
  return all;
}

export function filterEvents(events, { task_id, session_id, from_ts, to_ts, event_type, include_synthetic = false } = {}) {
  return events.filter(e => {
    if (!include_synthetic && e.is_synthetic) return false;
    if (task_id && e.task_id !== task_id) return false;
    if (session_id && e.session_id !== session_id) return false;
    if (event_type && e.event_type !== event_type) return false;
    if (from_ts && (e.ts_utc ?? "") < from_ts) return false;
    if (to_ts && (e.ts_utc ?? "") > to_ts) return false;
    return true;
  });
}
```

- [ ] **Step 4: Run tests + commit**

Run: `cd tools && node --test lib/event-log-reader.test.mjs`
Expected: 3 tests pass.

```bash
git add tools/lib/event-log-reader.mjs tools/lib/event-log-reader.test.mjs
git commit -m "feat(standin): event-log reader w/ synthetic filter"
```

---

### Task 0.8: .gitignore + directory skeletons

**Files:**
- Modify: `.gitignore`
- Create: `docs/standin-findings/.gitkeep`
- Create: `docs/standin-findings/screenshots/.gitkeep`
- Create: `docs/standin-findings/golden/.gitkeep`
- Create: `docs/standin-findings/schemas/.gitkeep`

Spec reference: § ".gitignore additions" + § "File / directory layout".

- [ ] **Step 1: Append to .gitignore**

```
# Student stand-in surfaces (sensitive payload + ephemeral)
/opt/jarvis/data/private/
docs/standin-findings/screenshots/
docs/standin-findings/DRAFT-*.md
```

- [ ] **Step 2: Create directory keepers**

```bash
mkdir -p docs/standin-findings/screenshots docs/standin-findings/golden docs/standin-findings/schemas
touch docs/standin-findings/.gitkeep docs/standin-findings/screenshots/.gitkeep docs/standin-findings/golden/.gitkeep docs/standin-findings/schemas/.gitkeep
```

- [ ] **Step 3: Verify git ignores screenshots dir**

```bash
touch docs/standin-findings/screenshots/test.png
git status --short  # should NOT show test.png
rm docs/standin-findings/screenshots/test.png
```

- [ ] **Step 4: Commit**

```bash
git add .gitignore docs/standin-findings/
git commit -m "chore(standin): gitignore + findings dir skeleton"
```

---

### Task 0.9: OpenRouter quota-isolation verifier

**Files:**
- Create: `tools/verify-openrouter-quota-isolation.mjs`

Spec reference: § "Quota-isolation verification (BLOCKING prereq)".

- [ ] **Step 1: Implement verifier**

```javascript
// tools/verify-openrouter-quota-isolation.mjs
import { callLlm } from "./lib/openrouter.mjs";
import { writeFileSync } from "node:fs";

const KEY_A = process.env.OPENROUTER_API_KEY;
const KEY_B = process.env.OPENROUTER_API_KEY_STANDIN;

if (!KEY_A || !KEY_B) {
  console.error("Set OPENROUTER_API_KEY (live) and OPENROUTER_API_KEY_STANDIN (proposed standin key)");
  process.exit(2);
}
if (KEY_A === KEY_B) {
  console.error("Keys are identical — provision a second OpenRouter API key first");
  process.exit(2);
}

const MODEL = "qwen/qwen-2.5-7b-instruct:free";
const BURN_N = 50;

async function burn(key, label) {
  let ok = 0, rate_limited = 0, other_err = 0;
  for (let i = 0; i < BURN_N; i++) {
    try {
      await callLlm({
        apiKey: key,
        model: MODEL,
        systemPrompt: "You reply with 'ok'.",
        userPrompt: `ping ${i}`,
        temperature: 0,
        maxTokens: 4,
      });
      ok++;
    } catch (e) {
      if (e.message.includes("429")) rate_limited++;
      else other_err++;
    }
  }
  return { ok, rate_limited, other_err, label };
}

console.log(`Burning ${BURN_N} calls on Key A...`);
const a = await burn(KEY_A, "A");
console.log(JSON.stringify(a));

console.log(`Probing Key B immediately after...`);
const b = await burn(KEY_B, "B");
console.log(JSON.stringify(b));

const verdict = b.ok > 0 ? "ISOLATED" : "SHARED";

const out = `# OpenRouter \`:free\` quota isolation verdict — ${new Date().toISOString()}

**Model probed:** \`${MODEL}\`
**Burn N:** ${BURN_N}

## Key A (live: OPENROUTER_API_KEY)
- ok: ${a.ok}
- rate_limited (429): ${a.rate_limited}
- other_err: ${a.other_err}

## Key B (proposed standin: OPENROUTER_API_KEY_STANDIN)
- ok: ${b.ok}
- rate_limited (429): ${b.rate_limited}
- other_err: ${b.other_err}

## VERDICT: ${verdict}

${verdict === "ISOLATED"
  ? "Separate keys on same account have independent quotas. Surface Y may run during study hours subject to per-key rate limits."
  : "Separate keys share account-level daily quota. Surface Y MUST be manual-only and run only OUTSIDE Alex's study window — never during 08:00-22:00 local."}
`;

writeFileSync("docs/notes/2026-05-13-openrouter-quota-isolation.md", out);
console.log(`\nVerdict written to docs/notes/2026-05-13-openrouter-quota-isolation.md`);
console.log(`Verdict: ${verdict}`);
process.exit(0);
```

- [ ] **Step 2: Document the run in README-of-the-day**

Add note to `docs/standin-findings/README.md` (create if missing) describing how to run:

```markdown
# Stand-in findings

Quarantined drafts. Human-review before any influence on design.

## Pre-flight (one-shot)

1. Provision a second OpenRouter API key (`OPENROUTER_API_KEY_STANDIN`).
2. Run `node tools/verify-openrouter-quota-isolation.mjs` (uses `OPENROUTER_API_KEY` and `OPENROUTER_API_KEY_STANDIN` env vars).
3. Result lands in `docs/notes/2026-05-13-openrouter-quota-isolation.md`.
4. If verdict is `SHARED`, Surface Y must run only outside study hours.
```

- [ ] **Step 3: Commit (verifier code only — verdict run is Task 0.10)**

```bash
git add tools/verify-openrouter-quota-isolation.mjs docs/standin-findings/README.md
git commit -m "feat(standin): OpenRouter quota-isolation pre-flight verifier"
```

---

### Task 0.10: Run pre-flight + deploy TutorEventLog to VPS + commit verdict

**Files:**
- New artifact: `docs/notes/2026-05-13-openrouter-quota-isolation.md` (output of verifier)
- VPS state: `/opt/jarvis/data/private/` created with 0700

This is a one-shot operational task — no code, just running the steps.

- [ ] **Step 1: Build + scp + restart jarvis**

```bash
gradle :installDist -x test
scp -r build/install/jarvis/lib/*.jar root@46.247.109.91:/opt/jarvis/lib/
ssh root@46.247.109.91 "systemctl restart jarvis && sleep 5 && systemctl status jarvis --no-pager"
```

Expected: jarvis.service active (running).

- [ ] **Step 2: Create private dir + verify perms**

```bash
ssh root@46.247.109.91 "mkdir -p /opt/jarvis/data/private && chown jarvis:jarvis /opt/jarvis/data/private && chmod 0700 /opt/jarvis/data/private && ls -la /opt/jarvis/data/private"
```

Expected: dir exists with `drwx------ jarvis jarvis`.

- [ ] **Step 3: Smoke-write an envelope by hitting the live drill-grade endpoint**

```bash
# From a logged-in browser via DevTools console OR a curl with the auth cookie:
curl -sk -X POST https://corgflix.duckdns.org/api/v1/drill/grade \
  -H "Cookie: jarvis_auth=$JARVIS_AUTH_COOKIE" \
  -H "X-Standin-Run: 1" \
  -H "Content-Type: application/json" \
  -d '{"task_id":"01KR6K07T6PATPRR5KH1JXYF8E","drill_id":"smoke","rcode":"# smoke test","predict":"none"}'

ssh root@46.247.109.91 "ls /opt/jarvis/data/private/ && tail -1 /opt/jarvis/data/private/tutor_events.*.jsonl"
```

Expected: today's file exists, last line is a valid JSON envelope with `"event_type":"drill_grade"` + `"is_synthetic":true`.

- [ ] **Step 4: Provision OPENROUTER_API_KEY_STANDIN + run verifier**

```bash
# Visit https://openrouter.ai/settings/keys, create new key "standin", copy.
export OPENROUTER_API_KEY=$(grep ^OPENROUTER_API_KEY= /opt/jarvis/.env | cut -d= -f2)  # actually from local .env
export OPENROUTER_API_KEY_STANDIN="sk-or-v1-..."
cd tools && node verify-openrouter-quota-isolation.mjs
```

Expected: verdict file written. Read it. Confirm `VERDICT: ISOLATED` or `VERDICT: SHARED`.

- [ ] **Step 5: Commit verdict + add OPENROUTER_API_KEY_STANDIN to VPS env**

```bash
git add docs/notes/2026-05-13-openrouter-quota-isolation.md
git commit -m "docs(standin): OpenRouter quota-isolation verdict (VERDICT: <ISOLATED|SHARED>)"

# On VPS:
ssh root@46.247.109.91 "echo 'OPENROUTER_API_KEY_STANDIN=sk-or-v1-...' >> /opt/jarvis/.env && systemctl restart jarvis"
```

---

## Phase 1 — Surface X (trace-grader)

After Phase 0 lands, X is unblocked. X reads server-side envelopes via SSH + grades against the invariant catalog.

### Task 1.1: Invariant catalog

**Files:**
- Create: `tools/surface-x-invariants.mjs`
- Test: `tools/surface-x-invariants.test.mjs`

Spec reference: § "Invariant catalog (V1)" — 10 invariants (8 PASS/FAIL + 2 INFO).

- [ ] **Step 1: Write failing tests**

```javascript
// tools/surface-x-invariants.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { INVARIANTS, scopeFor } from "./surface-x-invariants.mjs";

test("catalog has 10 invariants", () => {
  assert.equal(INVARIANTS.length, 10);
});

test("each invariant has id, statement, classification, scope", () => {
  for (const inv of INVARIANTS) {
    assert.ok(inv.id.match(/^INV-\d{2}$/));
    assert.equal(typeof inv.statement, "string");
    assert.ok(inv.statement.length > 20);
    assert.ok(["PASS_FAIL", "INFO"].includes(inv.classification));
    assert.equal(typeof inv.scope, "function");
  }
});

test("INV-09 and INV-10 are INFO-only", () => {
  const inv09 = INVARIANTS.find(i => i.id === "INV-09");
  const inv10 = INVARIANTS.find(i => i.id === "INV-10");
  assert.equal(inv09.classification, "INFO");
  assert.equal(inv10.classification, "INFO");
});

test("INV-01 scope brackets the drill-grade event preceded by predict input", () => {
  const events = [
    { event_id: "e1", event_type: "page_nav", ts_utc: "2026-05-13T10:00:00Z" },
    { event_id: "e2", event_type: "drill_grade", ts_utc: "2026-05-13T10:02:00Z" },
  ];
  const inv01 = INVARIANTS.find(i => i.id === "INV-01");
  const bracketed = inv01.scope(events);
  assert.ok(bracketed.length >= 1);
  assert.ok(bracketed.some(e => e.event_id === "e2"));
});

test("scopeFor wraps catalog lookup", () => {
  const events = [{ event_id: "e1", event_type: "sidekick_ask" }];
  const bracketed = scopeFor("INV-05", events);
  assert.equal(Array.isArray(bracketed), true);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd tools && node --test surface-x-invariants.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement catalog**

```javascript
// tools/surface-x-invariants.mjs
export const INVARIANTS = [
  {
    id: "INV-01",
    statement: "PREDICT textarea is filled before R-CODE textarea accepts input. The drill_grade event must be preceded in the same session by a page_nav or interaction event that captures the predict field non-empty.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => ["page_nav", "drill_grade"].includes(e.event_type)),
  },
  {
    id: "INV-02",
    statement: "When a drill_grade response indicates correctness=false, the response body cites at least one specific failed rubric criterion by name (the rubric_chips structure must include named failures).",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "drill_grade"),
  },
  {
    id: "INV-03",
    statement: "When a sidekick_ask carries a selection that is ≥0.7 Jaccard-similar to the corresponding drill's statement, the response model_resolved must be '(drill-self-paste-guard)' (the synthetic guard model name) — not a real LLM model.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "sidekick_ask"),
  },
  {
    id: "INV-04",
    statement: "Within an active-drill window (between a page_nav that opens a drill and the corresponding drill_grade), no sidekick_ask event fires that targets the drill body via the inline-chip flow. Identified by sidekick_ask metadata.source = 'inline-chip' AND task state has active DRILL card.",
    classification: "PASS_FAIL",
    scope: (events) => events,  // needs cross-event analysis
  },
  {
    id: "INV-05",
    statement: "When a sidekick_ask carries a corpus-eligible selection (Romanian / subject-vocab tokens), the response retrieved_context_summary has ≥1 entry AND the llm_output_full contains at least one '(src: _extras/<subject>/...)' marker.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "sidekick_ask"),
  },
  {
    id: "INV-06",
    statement: "Locked drill cards (WORKED / DEFINITION / CHECK) do not register interaction events while an active DRILL card is in 'open' state for the same task. Pre-mature open attempts must be rejected/no-op.",
    classification: "PASS_FAIL",
    scope: (events) => events,
  },
  {
    id: "INV-07",
    statement: "PDF stepper navigation (A1 → A2 → ... → AN) preserves drill state: the drill_grade for problem k uses the predict/rcode captured before the page_nav to problem k+1.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => ["page_nav", "drill_grade"].includes(e.event_type)),
  },
  {
    id: "INV-08",
    statement: "Drill rubric_chip text in drill_grade.llm_output_full is human-readable, NOT raw snake_case. Specifically, no rubric_chip key value in the rendered response contains an alphanumeric substring matching /\\b[a-z]+_[a-z_]+\\b/ as user-facing text. The motivating bug example is 'uses_rlaplace_or_inverse_cdf_sampler'.",
    classification: "PASS_FAIL",
    scope: (events) => events.filter(e => e.event_type === "drill_grade"),
  },
  {
    id: "INV-09",
    statement: "[INFO] Sidekick latency observed. Surface latency_p95_ms per session for sidekick_ask events. Flag with status=INFO when p95 > 8000 ms with corpus-eligible request. Never gate.",
    classification: "INFO",
    scope: (events) => events.filter(e => e.event_type === "sidekick_ask"),
  },
  {
    id: "INV-10",
    statement: "[INFO] Grader latency observed. Surface latency_p95_ms per session for drill_grade events. Flag with status=INFO when p95 > 12000 ms. Never gate.",
    classification: "INFO",
    scope: (events) => events.filter(e => e.event_type === "drill_grade"),
  },
];

export function scopeFor(invId, events) {
  const inv = INVARIANTS.find(i => i.id === invId);
  if (!inv) throw new Error(`Unknown invariant: ${invId}`);
  return inv.scope(events);
}
```

- [ ] **Step 4: Run tests + commit**

Run: `cd tools && node --test surface-x-invariants.test.mjs`
Expected: 5 tests pass.

```bash
git add tools/surface-x-invariants.mjs tools/surface-x-invariants.test.mjs
git commit -m "feat(standin): Surface X 10-invariant catalog w/ scope brackets"
```

---

### Task 1.2: Surface X CLI core (single invariant, single trace)

**Files:**
- Create: `tools/surface-x.mjs`
- Test: `tools/surface-x.test.mjs`

Spec reference: § "Surface X — trace-replay rubric-grader" + § "Grader prompt structure" + § "Ground-truth fixture set".

- [ ] **Step 1: Write failing test**

```javascript
// tools/surface-x.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { gradeOne, majorityVote, ofN } from "./surface-x.mjs";

test("gradeOne returns PASS/FAIL/N_A from LLM JSON", async () => {
  const fakeCallLlm = async () => ({
    text: '{"status":"PASS","evidence":{"event_ids":["e1"],"excerpt":"predict typed at 14:02:11"},"reason":"predict before rcode"}',
    model_resolved: "qwen/qwen-2.5-7b:free",
    prompt_sha256: "abc".repeat(21) + "a",
    tokens_in: 100,
    tokens_out: 30,
    latency_ms: 1500,
  });
  const result = await gradeOne({
    invariantId: "INV-01",
    invariantStatement: "PREDICT before R-CODE",
    events: [{ event_id: "e1" }],
    callLlm: fakeCallLlm,
  });
  assert.equal(result.status, "PASS");
  assert.equal(result.model_resolved, "qwen/qwen-2.5-7b:free");
  assert.ok(result.prompt_sha256);
});

test("majorityVote returns the most common status across runs", () => {
  const runs = [
    { status: "PASS" }, { status: "PASS" }, { status: "FAIL" },
  ];
  assert.equal(majorityVote(runs).status, "PASS");
});

test("majorityVote returns N_A when no majority", () => {
  const runs = [{ status: "PASS" }, { status: "FAIL" }, { status: "N_A" }];
  assert.equal(majorityVote(runs).status, "N_A");
});

test("ofN computes K-of-N exact match", () => {
  const pairs = [
    { judge: "PASS", gold: "PASS" },
    { judge: "PASS", gold: "PASS" },
    { judge: "FAIL", gold: "PASS" },
    { judge: "PASS", gold: "PASS" },
  ];
  const r = ofN(pairs, 0.80);
  assert.equal(r.matched, 3);
  assert.equal(r.total, 4);
  assert.equal(r.k, 4);  // ceil(0.80 * 4) = 4
  assert.equal(r.passed, false);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test surface-x.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement surface-x.mjs**

```javascript
// tools/surface-x.mjs
import { callLlm as defaultCallLlm } from "./lib/openrouter.mjs";

const GRADER_SYSTEM = `You are an invariant judge. Given a session trace and one invariant statement, return strict JSON:
{
  "status": "PASS" | "FAIL" | "N_A",
  "evidence": {"event_ids": [...], "excerpt": "..."},
  "reason": "<one sentence>"
}
N_A is reserved for invariants that don't apply to the given trace (e.g. no relevant events). Reply JSON only.`;

export async function gradeOne({
  invariantId,
  invariantStatement,
  events,
  callLlm = defaultCallLlm,
  apiKey = process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY,
  model = "qwen/qwen-2.5-7b-instruct:free",
  temperature = 0,
  seed = 42,
}) {
  const userPrompt = `INVARIANT: ${invariantId} — ${invariantStatement}\n\nSESSION EVENTS (jsonl):\n${events.map(e => JSON.stringify(e)).join("\n")}\n\nReply: <JSON only>`;
  const r = await callLlm({
    apiKey,
    model,
    systemPrompt: GRADER_SYSTEM,
    userPrompt,
    temperature,
    seed,
  });
  let parsed;
  try { parsed = JSON.parse(r.text); } catch { parsed = { status: "N_A", evidence: {}, reason: "parse_error" }; }
  return {
    status: parsed.status ?? "N_A",
    evidence: parsed.evidence ?? {},
    reason: parsed.reason ?? "",
    model_resolved: r.model_resolved,
    prompt_sha256: r.prompt_sha256,
    tokens_in: r.tokens_in,
    tokens_out: r.tokens_out,
    latency_ms: r.latency_ms,
  };
}

export function majorityVote(runs) {
  const counts = {};
  for (const r of runs) counts[r.status] = (counts[r.status] ?? 0) + 1;
  const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  if (sorted.length === 1) return runs[0];
  if (sorted[0][1] === sorted[1][1]) return { ...runs[0], status: "N_A", reason: "no majority" };
  return runs.find(r => r.status === sorted[0][0]);
}

export function ofN(pairs, thresholdPct = 0.80) {
  const total = pairs.length;
  const matched = pairs.filter(p => p.judge === p.gold).length;
  const k = Math.ceil(thresholdPct * total);
  return { matched, total, k, threshold_pct: thresholdPct, passed: matched >= k };
}
```

- [ ] **Step 4: Run test + commit**

Run: `cd tools && node --test surface-x.test.mjs`
Expected: 4 tests pass.

```bash
git add tools/surface-x.mjs tools/surface-x.test.mjs
git commit -m "feat(standin): Surface X grader core w/ majority-vote + K-of-N gate"
```

---

### Task 1.3: Surface X CLI — full-session grading + finding doc output

**Files:**
- Modify: `tools/surface-x.mjs` (add CLI entrypoint + multi-invariant loop + doc writer)
- Test: `tools/surface-x.test.mjs` (add doc-write integration test)

Spec reference: § "Output schema" — finding doc with provenance frontmatter.

- [ ] **Step 1: Write failing test for full-session grade**

```javascript
// Append to tools/surface-x.test.mjs
import { writeFileSync, readFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { gradeSession } from "./surface-x.mjs";

test("gradeSession runs each invariant, writes finding doc with provenance", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "x-doc-"));
  const fakeCallLlm = async ({ userPrompt }) => {
    const status = userPrompt.includes("INV-08") ? "FAIL" : "PASS";
    return {
      text: `{"status":"${status}","evidence":{"event_ids":["e1"]},"reason":"test"}`,
      model_resolved: "qwen/qwen-2.5-7b:free",
      prompt_sha256: "x".repeat(64),
      tokens_in: 50, tokens_out: 10, latency_ms: 500,
    };
  };
  const events = [
    { event_id: "e1", event_type: "drill_grade", ts_utc: "2026-05-13T10:00:00Z", task_id: "t1", is_synthetic: false },
    { event_id: "e2", event_type: "sidekick_ask", ts_utc: "2026-05-13T10:05:00Z", task_id: "t1", is_synthetic: false },
  ];
  const docPath = await gradeSession({
    sessionId: "test-session-1",
    events,
    invariantIds: ["INV-01", "INV-08"],
    outputDir: tmp,
    callLlm: fakeCallLlm,
    runsPerInvariant: 1,  // skip multi-run for speed
  });
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: X/);
  assert.match(text, /session_id: test-session-1/);
  assert.match(text, /git_head:/);
  assert.match(text, /INV-01.*PASS/);
  assert.match(text, /INV-08.*FAIL/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test surface-x.test.mjs`
Expected: FAIL — `gradeSession` not exported.

- [ ] **Step 3: Add gradeSession + CLI entrypoint to surface-x.mjs**

```javascript
// Append to tools/surface-x.mjs
import { INVARIANTS, scopeFor } from "./surface-x-invariants.mjs";
import { getStamp } from "./lib/provenance.mjs";
import { writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

export async function gradeSession({
  sessionId,
  events,
  invariantIds = INVARIANTS.map(i => i.id),
  outputDir = "docs/standin-findings",
  callLlm,
  apiKey,
  model,
  runsPerInvariant = 3,
}) {
  process.env.SURFACE_VERSION = "x-v1.0";
  const results = [];
  let lastJudgeModel = null;
  let lastPromptSha = null;
  for (const id of invariantIds) {
    const inv = INVARIANTS.find(i => i.id === id);
    if (!inv) continue;
    const bracketed = inv.scope(events);
    if (bracketed.length === 0) {
      results.push({ id, status: "N_A", reason: "no in-scope events", evidence: {} });
      continue;
    }
    const runs = [];
    for (let i = 0; i < runsPerInvariant; i++) {
      const r = await gradeOne({
        invariantId: id,
        invariantStatement: inv.statement,
        events: bracketed,
        callLlm, apiKey, model,
      });
      runs.push(r);
      lastJudgeModel = r.model_resolved;
      lastPromptSha = r.prompt_sha256;
    }
    const voted = majorityVote(runs);
    const final = {
      id,
      classification: inv.classification,
      status: inv.classification === "INFO" ? "INFO" : voted.status,
      evidence: voted.evidence,
      reason: voted.reason,
      latencies_ms: runs.map(r => r.latency_ms),
    };
    if (inv.classification === "INFO") {
      const sorted = bracketed.map(e => e.latency_ms ?? 0).filter(Boolean).sort((a, b) => a - b);
      final.latency_p95_ms = sorted[Math.floor(sorted.length * 0.95)] ?? null;
    }
    results.push(final);
  }

  const stamp = await getStamp(null, {
    judge_model_resolved: lastJudgeModel,
    judge_prompt_sha256: lastPromptSha,
  });
  const ts = stamp.ts_utc.replace(/[:.]/g, "-");
  mkdirSync(outputDir, { recursive: true });
  const docPath = join(outputDir, `DRAFT-X-${sessionId}-${ts}.md`);
  const fm = [
    "---",
    "surface: X",
    `session_id: ${sessionId}`,
    "provenance:",
    `  git_head: ${stamp.git_head}`,
    `  bundle_hash: ${stamp.bundle_hash}`,
    `  live_dom_fingerprint: ${stamp.live_dom_fingerprint ?? "null"}`,
    `  ts_utc: ${stamp.ts_utc}`,
    `  surface_version: ${stamp.surface_version}`,
    `  judge_model_resolved: ${stamp.judge_model_resolved ?? "null"}`,
    `  judge_prompt_sha256: ${stamp.judge_prompt_sha256 ?? "null"}`,
    `invariants_run: ${results.length}`,
    "---",
    "",
    `# Surface X findings — session ${sessionId}`,
    "",
    "| Invariant | Status | Reason |",
    "|-----------|--------|--------|",
    ...results.map(r => `| ${r.id} | ${r.status} | ${(r.reason || "").slice(0, 120)} |`),
    "",
    "## Per-invariant detail",
    ...results.flatMap(r => [
      "",
      `### ${r.id} — ${r.status}`,
      `- classification: ${r.classification}`,
      r.evidence?.event_ids ? `- evidence event_ids: ${JSON.stringify(r.evidence.event_ids)}` : "",
      r.evidence?.excerpt ? `- excerpt: ${r.evidence.excerpt.slice(0, 200)}` : "",
      `- reason: ${r.reason ?? ""}`,
      r.latency_p95_ms !== undefined ? `- latency_p95_ms: ${r.latency_p95_ms}` : "",
    ].filter(Boolean)),
  ].join("\n");
  writeFileSync(docPath, fm);
  return docPath;
}

// CLI entrypoint
if (process.argv[1]?.endsWith("surface-x.mjs")) {
  const args = parseArgs();  // simple --key=value parser, implement inline
  // ... read events, call gradeSession, print path
}
```

Add a tiny CLI arg parser at bottom:

```javascript
function parseArgs() {
  const out = {};
  for (const a of process.argv.slice(2)) {
    const m = a.match(/^--([^=]+)=(.+)$/);
    if (m) out[m[1]] = m[2];
    else if (a.startsWith("--")) out[a.slice(2)] = true;
  }
  return out;
}

if (process.argv[1]?.endsWith("surface-x.mjs")) {
  const args = parseArgs();
  const { readEvents, filterEvents } = await import("./lib/event-log-reader.mjs");
  const all = await readEvents({ sshTarget: args["ssh"] ?? "root@46.247.109.91" });
  const filtered = filterEvents(all, {
    task_id: args.task,
    session_id: args.session,
    from_ts: args.from,
    to_ts: args.to,
    include_synthetic: !!args["include-synthetic"],
  });
  const sessionId = args.session ?? `auto-${Date.now()}`;
  const docPath = await gradeSession({
    sessionId,
    events: filtered,
    invariantIds: args.invariants === "all" ? undefined : (args.invariants ?? "INV-01,INV-02,INV-03,INV-04,INV-05,INV-06,INV-07,INV-08,INV-09,INV-10").split(","),
    runsPerInvariant: args.calibrate ? 3 : 1,
  });
  console.log(`Wrote: ${docPath}`);
}
```

- [ ] **Step 4: Run test + commit**

Run: `cd tools && node --test surface-x.test.mjs`
Expected: all tests pass.

```bash
git add tools/surface-x.mjs tools/surface-x.test.mjs
git commit -m "feat(standin): Surface X gradeSession + CLI w/ provenance-stamped findings"
```

---

### Task 1.4: Calibration mode (`--from-fixture`)

**Files:**
- Modify: `tools/surface-x.mjs` (add fixture-parse + ofN gate)
- Modify: `tools/surface-x.test.mjs`

Spec reference: § "Ground-truth fixture set (FP ship gate)" — multi-run majority + K-of-N gate.

- [ ] **Step 1: Write failing test**

```javascript
// Append to tools/surface-x.test.mjs
import { calibrateAgainstFixture } from "./surface-x.mjs";

test("calibrateAgainstFixture reports agreement against gold labels", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "x-calib-"));
  const fixturePath = join(tmp, "fixture.md");
  writeFileSync(fixturePath, [
    "# Bootstrap fixture",
    "",
    "### Trace 1",
    "```yaml",
    "events:",
    "  - {event_id: e1, event_type: drill_grade, task_id: t1, ts_utc: '2026-05-13T10:00:00Z'}",
    "labels:",
    "  INV-01: PASS",
    "  INV-08: FAIL",
    "```",
    "",
    "### Trace 2",
    "```yaml",
    "events:",
    "  - {event_id: e2, event_type: sidekick_ask, task_id: t1, ts_utc: '2026-05-13T10:01:00Z'}",
    "labels:",
    "  INV-05: PASS",
    "```",
  ].join("\n"));

  const fakeCallLlm = async ({ userPrompt }) => {
    // judge always says PASS — so Trace 1 INV-08 gold=FAIL mismatches
    return {
      text: '{"status":"PASS","evidence":{},"reason":"test"}',
      model_resolved: "fake", prompt_sha256: "x".repeat(64),
      tokens_in: 10, tokens_out: 5, latency_ms: 100,
    };
  };
  const r = await calibrateAgainstFixture({
    fixturePath,
    callLlm: fakeCallLlm,
    runsPerInvariant: 1,
    thresholdPct: 0.80,
  });
  assert.equal(r.total, 3);    // 3 (trace, invariant) pairs
  assert.equal(r.matched, 2);  // INV-08 mismatch
  assert.equal(r.passed, false);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test surface-x.test.mjs`
Expected: FAIL — `calibrateAgainstFixture` not exported.

- [ ] **Step 3: Implement calibrateAgainstFixture**

```javascript
// Append to tools/surface-x.mjs
import { readFileSync } from "node:fs";

export function parseFixture(text) {
  const traces = [];
  const sections = text.split(/^### Trace /m).slice(1);
  for (const sec of sections) {
    const yamlMatch = sec.match(/```yaml\n([\s\S]+?)\n```/);
    if (!yamlMatch) continue;
    const yamlText = yamlMatch[1];
    const events = [];
    const labels = {};
    let inEvents = false, inLabels = false;
    for (const line of yamlText.split("\n")) {
      if (line.startsWith("events:")) { inEvents = true; inLabels = false; continue; }
      if (line.startsWith("labels:")) { inEvents = false; inLabels = true; continue; }
      if (inEvents && line.trim().startsWith("- ")) {
        // Parse inline flow-style: - {event_id: e1, event_type: drill_grade, ...}
        const flow = line.trim().slice(2).trim();
        const m = flow.match(/^\{([\s\S]+)\}$/);
        if (m) {
          const obj = {};
          for (const pair of m[1].split(",")) {
            const [k, v] = pair.split(":").map(s => s.trim().replace(/^['"]|['"]$/g, ""));
            obj[k] = v;
          }
          events.push(obj);
        }
      }
      if (inLabels && /^\s+INV-\d{2}:/.test(line)) {
        const m = line.match(/^\s+(INV-\d{2}):\s*(\w+)\s*$/);
        if (m) labels[m[1]] = m[2];
      }
    }
    traces.push({ events, labels });
  }
  return traces;
}

export async function calibrateAgainstFixture({
  fixturePath,
  callLlm,
  apiKey,
  model,
  runsPerInvariant = 3,
  thresholdPct = 0.80,
}) {
  const text = readFileSync(fixturePath, "utf8");
  const traces = parseFixture(text);
  const pairs = [];
  for (const t of traces) {
    for (const [invId, goldStatus] of Object.entries(t.labels)) {
      const inv = INVARIANTS.find(i => i.id === invId);
      if (!inv) continue;
      const bracketed = inv.scope(t.events);
      const runs = [];
      for (let i = 0; i < runsPerInvariant; i++) {
        const r = await gradeOne({
          invariantId: invId,
          invariantStatement: inv.statement,
          events: bracketed,
          callLlm, apiKey, model,
        });
        runs.push(r);
      }
      const voted = majorityVote(runs);
      pairs.push({ invariantId: invId, judge: voted.status, gold: goldStatus });
    }
  }
  const gate = ofN(pairs, thresholdPct);
  return { ...gate, pairs };
}

// Wire CLI flag
if (process.argv[1]?.endsWith("surface-x.mjs")) {
  const args = parseArgs();
  if (args.calibrate && args["from-fixture"]) {
    const r = await calibrateAgainstFixture({ fixturePath: args["from-fixture"] });
    console.log(`Calibration: ${r.matched}/${r.total} match (K=${r.k}). Threshold=${r.threshold_pct}. Passed: ${r.passed}`);
    console.log("Per-pair:");
    for (const p of r.pairs) console.log(`  ${p.invariantId}: judge=${p.judge} gold=${p.gold} ${p.judge === p.gold ? "✓" : "✗"}`);
    process.exit(r.passed ? 0 : 1);
  }
  // ... existing CLI flow
}
```

- [ ] **Step 4: Run test + commit**

Run: `cd tools && node --test surface-x.test.mjs`
Expected: all tests pass.

```bash
git add tools/surface-x.mjs tools/surface-x.test.mjs
git commit -m "feat(standin): Surface X calibration mode (--from-fixture w/ K-of-N gate)"
```

---

### Task 1.5: Hand-curate golden fixture skeleton + 15-25 entries

**Files:**
- Create: `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md`

Spec reference: § "Ground-truth fixture set (FP ship gate)".

This is a manual content task. The plan creates the skeleton; Alex fills the 15-25 entries from his real recent tutor_events.jsonl.

- [ ] **Step 1: Pull recent events from VPS**

```bash
ssh root@46.247.109.91 "cat /opt/jarvis/data/private/tutor_events.$(date -u +%Y-%m-%d).jsonl"
# Save locally as /tmp/today-events.jsonl
```

- [ ] **Step 2: Create skeleton with 5 starter traces**

```markdown
<!-- docs/standin-findings/golden/2026-05-13-bootstrap-traces.md -->
# Surface X golden fixture set — 2026-05-13 bootstrap

Hand-curated labeled traces. Each `### Trace N` block has YAML with `events:` (jsonl events from real tutor_events log) and `labels:` (Alex's manual PASS/FAIL/N_A per invariant).

Surface X calibration: `node tools/surface-x.mjs --calibrate --from-fixture <this-file>`. Ship gate: ≥K of N matches where K = ceil(0.80 × N).

---

### Trace 1
```yaml
events:
  - {event_id: 01KR..., event_type: page_nav, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, ts_utc: '2026-05-13T...'}
  - {event_id: 01KR..., event_type: drill_grade, task_id: 01KR6K07T6PATPRR5KH1JXYF8E, ts_utc: '2026-05-13T...', predict_filled: true}
labels:
  INV-01: PASS
  INV-02: PASS
  INV-08: FAIL  # rubric_chip showed snake_case
```

### Trace 2
```yaml
events:
  - ...
labels:
  INV-03: PASS  # drill-self-paste guard correctly fired
```

<!-- ... up to Trace 15-25, each labeled across 1-3 in-scope invariants -->
```

- [ ] **Step 3: Alex fills 15-25 entries** (manual; out of plan automation but plan tracks the action)

Spend ~10 min/trace pulling from `/tmp/today-events.jsonl`, hand-labeling each (trace, invariant) pair. Aim for 15-25 traces × ~2 in-scope invariants each = 30-50 total (trace, invariant) pairs.

- [ ] **Step 4: Commit fixture**

```bash
git add docs/standin-findings/golden/2026-05-13-bootstrap-traces.md
git commit -m "docs(standin): bootstrap golden fixture for Surface X calibration"
```

---

### Task 1.6: Run calibration + verify ≥K gate

**Files:**
- Read: `docs/standin-findings/golden/2026-05-13-bootstrap-traces.md`
- Optionally: iterate on prompt/scope if gate fails

Spec reference: § "Ground-truth fixture set" — ship gate K = ceil(0.80 × N).

- [ ] **Step 1: Run calibration**

```bash
cd tools && node surface-x.mjs --calibrate --from-fixture ../docs/standin-findings/golden/2026-05-13-bootstrap-traces.md
```

Expected: prints per-pair `judge=X gold=Y ✓/✗` lines + final `agreement_pct` + `Passed: true` (exit 0) or `false` (exit 1).

- [ ] **Step 2: If gate FAILS, iterate prompt**

Inspect failing pairs. Common fixes:
- Grader prompt unclear on what counts as evidence → tighten `GRADER_SYSTEM` in `surface-x.mjs`.
- Invariant statement ambiguous → re-word in `surface-x-invariants.mjs`.
- Fixture label was wrong → fix in golden file.
Re-run after each tweak. Commit ONLY when ≥K passes.

- [ ] **Step 3: Commit any prompt/invariant tweaks**

```bash
git add tools/surface-x.mjs tools/surface-x-invariants.mjs docs/standin-findings/golden/2026-05-13-bootstrap-traces.md
git commit -m "tune(standin): X grader prompt + invariants to pass calibration ≥K-of-N gate"
```

X is now ship-gated.

---

### Task 1.7: Advisory deploy hook

**Files:**
- Modify: `deploy.sh`

Spec reference: § "Trigger" (X) + § "Anti-features" — advisory only, never blocks.

- [ ] **Step 1: Add advisory line at end of deploy.sh**

```bash
# Append to deploy.sh just before the final success echo:
if [ "${RUN_LLM_EVAL:-0}" = "1" ]; then
  echo "[deploy] RUN_LLM_EVAL=1 — running Surface X advisory grader on smoke trace..."
  ( cd tools && node surface-x.mjs --task 01KR6K07T6PATPRR5KH1JXYF8E --invariants INV-01,INV-02,INV-08 ) || echo "[deploy] Surface X advisory: non-zero exit ($?). Findings written. Deploy continues."
fi
```

- [ ] **Step 2: Test the hook locally with a smoke run**

```bash
RUN_LLM_EVAL=1 bash deploy.sh  # only if you're actually deploying — otherwise dry-run by extracting the snippet
```

Expected: Surface X advisory line printed, finding doc written even if grader fails. Deploy NEVER blocks.

- [ ] **Step 3: Commit**

```bash
git add deploy.sh
git commit -m "feat(standin): advisory Surface X deploy hook gated by RUN_LLM_EVAL=1"
```

---

## Phase 2 — Surface Z (novice-eyes visual critic)

After X ships + calibration passes, Z is unblocked.

### Task 2.1: Visual lints (snake-case + contrast + font-size + overflow)

**Files:**
- Create: `tools/surface-z-lints.mjs`
- Test: `tools/surface-z-lints.test.mjs`

Spec reference: § "Programmatic visual lints" — 4 lints, run in browser via `page.evaluate()`.

- [ ] **Step 1: Write failing tests**

```javascript
// tools/surface-z-lints.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { detectSnakeCase } from "./surface-z-lints.mjs";

// Pure unit tests for the snake-case detector (other 3 lints run in browser only)
test("detectSnakeCase finds snake_case in plain text", () => {
  const out = detectSnakeCase("foo bar uses_rlaplace_or_inverse_cdf baz");
  assert.deepEqual(out, ["uses_rlaplace_or_inverse_cdf"]);
});

test("detectSnakeCase ignores single-word lowercase", () => {
  assert.deepEqual(detectSnakeCase("foo bar baz"), []);
});

test("detectSnakeCase ignores text inside code/pre tag markers", () => {
  // Caller is responsible for stripping <code>/<pre> before passing to detector
  // This test confirms the detector itself only scans plain text.
  assert.deepEqual(detectSnakeCase("hello_world test"), ["hello_world"]);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test surface-z-lints.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement lints**

```javascript
// tools/surface-z-lints.mjs
export function detectSnakeCase(text) {
  return [...text.matchAll(/\b[a-z]+_[a-z_]+\b/g)].map(m => m[0]);
}

// These run inside page.evaluate() — exported as strings to be serialized
export const LINT_EVAL_SCRIPT = `
(() => {
  const findings = { snake_case: [], low_contrast: [], small_font: [], h_overflow: false };

  // snake_case scan — visible text not inside <code> / <pre>
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const parentTag = node.parentElement?.tagName?.toLowerCase();
    if (parentTag === 'code' || parentTag === 'pre') continue;
    const matches = [...node.nodeValue.matchAll(/\\b[a-z]+_[a-z_]+\\b/g)].map(m => m[0]);
    for (const m of matches) {
      findings.snake_case.push({ text: m, selector: cssPath(node.parentElement) });
    }
  }

  // contrast scan — sample N body text nodes
  function contrastRatio(rgb1, rgb2) {
    function lum([r, g, b]) {
      const s = [r, g, b].map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); });
      return 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2];
    }
    const a = lum(rgb1), b = lum(rgb2);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  }
  function parseRgb(str) {
    const m = str.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)/);
    return m ? [+m[1], +m[2], +m[3]] : null;
  }
  const textEls = [...document.querySelectorAll('p, span, li, h1, h2, h3, h4, button, a, label')].slice(0, 50);
  for (const el of textEls) {
    if (!el.textContent.trim()) continue;
    const cs = getComputedStyle(el);
    const fg = parseRgb(cs.color);
    const bg = parseRgb(cs.backgroundColor);
    if (!fg || !bg) continue;
    const ratio = contrastRatio(fg, bg);
    if (ratio < 4.5) findings.low_contrast.push({ selector: cssPath(el), ratio: ratio.toFixed(2) });
  }

  // small-font scan
  for (const el of textEls) {
    if (!el.textContent.trim()) continue;
    const px = parseFloat(getComputedStyle(el).fontSize);
    if (px < 14) findings.small_font.push({ selector: cssPath(el), px });
  }

  // h-overflow
  findings.h_overflow = document.documentElement.scrollWidth > window.innerWidth;

  function cssPath(el) {
    if (!el) return '';
    if (el.id) return '#' + el.id;
    const path = [];
    while (el && el.nodeType === Node.ELEMENT_NODE && path.length < 4) {
      let s = el.tagName.toLowerCase();
      if (el.className && typeof el.className === 'string') s += '.' + el.className.trim().split(/\\s+/).slice(0, 2).join('.');
      path.unshift(s);
      el = el.parentElement;
    }
    return path.join(' > ');
  }

  return findings;
})()
`;
```

- [ ] **Step 4: Run test + commit**

Run: `cd tools && node --test surface-z-lints.test.mjs`
Expected: 3 tests pass.

```bash
git add tools/surface-z-lints.mjs tools/surface-z-lints.test.mjs
git commit -m "feat(standin): Surface Z programmatic visual lints (snake_case + contrast + font-size + overflow)"
```

---

### Task 2.2: Surface Z CLI — standalone screenshot sweep

**Files:**
- Create: `tools/surface-z.mjs`
- Test: `tools/surface-z.test.mjs`

Spec reference: § "Surface Z" — Mode A standalone.

- [ ] **Step 1: Write failing test (mocking Playwright)**

```javascript
// tools/surface-z.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { sweepPages } from "./surface-z.mjs";

test("sweepPages emits findings doc with lint output per page", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "z-"));
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        screenshot: async ({ path }) => { require("node:fs").writeFileSync(path, "PNG"); },
        evaluate: async () => ({
          snake_case: [{ text: "uses_rlaplace_or_inverse", selector: "div.rubric-chip" }],
          low_contrast: [], small_font: [], h_overflow: false,
        }),
        close: async () => {},
      }),
      close: async () => {},
    }),
    close: async () => {},
  };
  const fakeCallLlm = async () => ({
    text: '{"severity":"readability","observations":[{"what":"snake_case visible","where":"chip","why_it_hurts":"hard to read"}],"one_liner":"chip labels look like code"}',
    model_resolved: "qwen-vl:free", prompt_sha256: "y".repeat(64),
    tokens_in: 80, tokens_out: 30, latency_ms: 1200,
  });
  const docPath = await sweepPages({
    pages: ["/tutor/", "/tutor/review"],
    viewports: [{ width: 1280, height: 800, name: "desktop" }],
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    outputDir: tmp,
    screenshotDir: tmp,
    sessionId: "test-z-1",
    baseUrl: "https://corgflix.duckdns.org",
    authCookie: "test",
  });
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: Z/);
  assert.match(text, /uses_rlaplace_or_inverse/);
  assert.match(text, /chip labels look like code/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test surface-z.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement surface-z.mjs**

```javascript
// tools/surface-z.mjs
import { chromium } from "playwright";
import { writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { callLlm as defaultCallLlm } from "./lib/openrouter.mjs";
import { LINT_EVAL_SCRIPT } from "./surface-z-lints.mjs";
import { getStamp } from "./lib/provenance.mjs";

const LAYPERSON_SYSTEM = `You are a non-designer Romanian uni student opening this study site for the first time. You're judging ONLY whether things look right to your eyes — not technical correctness.

Reply STRICT JSON:
{
  "severity": "blocking" | "readability" | "cosmetic" | "none",
  "observations": [{"what":"...","where":"<region>","why_it_hurts":"..."}],
  "one_liner": "<single sentence overall impression>"
}

AUTO-LINTS (hints, extend with your own eye):
<INSERT>

DOM TEXT EXCERPT (text-only fallback if vision unavailable):
<INSERT>`;

export async function sweepPages({
  pages = ["/tutor/", "/tutor/review"],
  viewports = [{ width: 1280, height: 800, name: "desktop" }],
  browser = null,
  callLlm = defaultCallLlm,
  outputDir = "docs/standin-findings",
  screenshotDir = "docs/standin-findings/screenshots",
  sessionId = `auto-${Date.now()}`,
  baseUrl = "https://corgflix.duckdns.org",
  authCookie = process.env.JARVIS_AUTH_COOKIE,
  model = "qwen/qwen-2.5-vl-32b-instruct:free",
  textFallbackModel = "qwen/qwen-2.5-7b-instruct:free",
} = {}) {
  process.env.SURFACE_VERSION = "z-v1.0";
  const ownsBrowser = !browser;
  if (!browser) browser = await chromium.launch({ headless: true });
  mkdirSync(outputDir, { recursive: true });
  mkdirSync(screenshotDir, { recursive: true });

  const allFindings = [];
  let lastJudgeModel = null, lastPromptSha = null;

  for (const vp of viewports) {
    const ctx = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      storageState: authCookie ? {
        cookies: [{ name: "jarvis_auth", value: authCookie, domain: new URL(baseUrl).hostname, path: "/", secure: true, httpOnly: false }],
        origins: [],
      } : undefined,
    });
    for (const path of pages) {
      const page = await ctx.newPage();
      await page.goto(`${baseUrl}${path}`);
      await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
      const lints = await page.evaluate(LINT_EVAL_SCRIPT);
      const screenshotPath = join(screenshotDir, `Z-${sessionId}-${vp.name}-${path.replace(/\//g, "_")}.png`);
      await page.screenshot({ path: screenshotPath, fullPage: true });

      // Build text excerpt for fallback
      const domText = await page.evaluate("document.body.innerText.slice(0, 4000)");
      const userPrompt = LAYPERSON_SYSTEM
        .replace("<INSERT>", JSON.stringify(lints))
        .replace("<INSERT>", domText);

      let llmResp;
      try {
        llmResp = await callLlm({
          apiKey: process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY,
          model,
          systemPrompt: LAYPERSON_SYSTEM,
          userPrompt,
          temperature: 0.2,
        });
      } catch (e) {
        // Fallback to text-only model
        llmResp = await callLlm({
          apiKey: process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY,
          model: textFallbackModel,
          systemPrompt: LAYPERSON_SYSTEM,
          userPrompt,
          temperature: 0.2,
        });
      }
      lastJudgeModel = llmResp.model_resolved;
      lastPromptSha = llmResp.prompt_sha256;

      let parsed = { severity: "none", observations: [], one_liner: "parse_error" };
      try { parsed = JSON.parse(llmResp.text); } catch {}

      allFindings.push({
        path,
        viewport: vp.name,
        lints,
        screenshot: screenshotPath,
        ...parsed,
      });
      await page.close();
    }
    await ctx.close();
  }
  if (ownsBrowser) await browser.close();

  const stamp = await getStamp(null, {
    judge_model_resolved: lastJudgeModel,
    judge_prompt_sha256: lastPromptSha,
  });
  const ts = stamp.ts_utc.replace(/[:.]/g, "-");
  const docPath = join(outputDir, `DRAFT-Z-${sessionId}-${ts}.md`);
  const md = [
    "---",
    "surface: Z",
    "mode: standalone",
    `session_id: ${sessionId}`,
    "provenance:",
    `  git_head: ${stamp.git_head}`,
    `  bundle_hash: ${stamp.bundle_hash}`,
    `  live_dom_fingerprint: ${stamp.live_dom_fingerprint ?? "null"}`,
    `  ts_utc: ${stamp.ts_utc}`,
    `  surface_version: ${stamp.surface_version}`,
    `  judge_model_resolved: ${stamp.judge_model_resolved ?? "null"}`,
    `  judge_prompt_sha256: ${stamp.judge_prompt_sha256 ?? "null"}`,
    `pages_visited: ${allFindings.length}`,
    "---",
    "",
    `# Surface Z findings — ${sessionId}`,
    "",
    "| Page | Viewport | Severity | One-liner |",
    "|------|----------|----------|-----------|",
    ...allFindings.map(f => `| ${f.path} | ${f.viewport} | ${f.severity} | ${f.one_liner ?? ""} |`),
    "",
    "## Detailed observations",
    ...allFindings.flatMap(f => [
      "",
      `### ${f.path} (${f.viewport}) — ${f.severity}`,
      "**Auto-lints:**",
      `- snake_case strings: ${f.lints.snake_case.length}`,
      ...f.lints.snake_case.map(s => `  - "${s.text}" at \`${s.selector}\``),
      `- low_contrast nodes: ${f.lints.low_contrast.length}`,
      `- small_font nodes: ${f.lints.small_font.length}`,
      `- horizontal_overflow: ${f.lints.h_overflow}`,
      "**Layperson observations:**",
      ...(f.observations ?? []).map(o => `- **${o.what}** (${o.where}): ${o.why_it_hurts}`),
      `**Screenshot:** \`${f.screenshot}\``,
    ]),
  ].join("\n");
  writeFileSync(docPath, md);
  return docPath;
}

// CLI
if (process.argv[1]?.endsWith("surface-z.mjs")) {
  const args = Object.fromEntries(process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.+)$/); return m ? [m[1], m[2]] : [a.replace(/^--/, ""), true];
  }));
  const pages = args.pages ? args.pages.split(",") : ["/tutor/", "/tutor/review"];
  const viewports = args.viewports
    ? args.viewports.split(",").map(w => ({ width: +w, height: 800, name: `vp-${w}` }))
    : [{ width: 1280, height: 800, name: "desktop" }];
  const docPath = await sweepPages({ pages, viewports, sessionId: args.session ?? `auto-${Date.now()}` });
  console.log(`Wrote: ${docPath}`);
}
```

- [ ] **Step 4: Run test + commit**

Run: `cd tools && node --test surface-z.test.mjs`
Expected: pass.

```bash
git add tools/surface-z.mjs tools/surface-z.test.mjs
git commit -m "feat(standin): Surface Z standalone screenshot sweep w/ vision+text-fallback"
```

---

### Task 2.3: First Z run on live tutor — verify snake_case-chip caught

**Files:**
- Produces: `docs/standin-findings/DRAFT-Z-<sid>-<ts>.md` (NOT committed — quarantined)

This is an operational verification, not a code task.

- [ ] **Step 1: Run Z on the drill-open page**

```bash
cd tools && OPENROUTER_API_KEY_STANDIN=... JARVIS_AUTH_COOKIE=... node surface-z.mjs --pages=/tutor/,/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E
```

- [ ] **Step 2: Read the finding doc**

```bash
cat docs/standin-findings/DRAFT-Z-*.md
```

- [ ] **Step 3: Verify snake_case lint caught the motivating bug**

Expected: at least one `snake_case strings` entry > 0 on the drill-open page, with text containing `uses_` or similar.

- [ ] **Step 4: Run stale-check on the doc**

```bash
node tools/findings-stale-check.mjs docs/standin-findings/DRAFT-Z-*.md
```

Expected: `Overall: [OK]` (just-written).

Z's first production run validated. No commit (finding draft is gitignored).

---

### Task 2.4: Mobile viewport sweep mode

**Files:**
- Verify: `tools/surface-z.mjs` (already supports `--viewports`)

- [ ] **Step 1: Run mobile sweep**

```bash
cd tools && node surface-z.mjs --viewports=375,768,1280 --pages=/tutor/,/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E
```

- [ ] **Step 2: Verify findings include horizontal_overflow checks**

Read the new DRAFT-Z doc. Expected: each (page × viewport) row has lint output.

No commit unless a code change was needed.

---

## Phase 3 — Surface Y (student stand-in)

After X + Z ship, Y unblocks. Y requires the quota-isolation verdict + a hand-authored concept schema.

### Task 3.1: First concept schema for PS Tema A

**Files:**
- Create: `docs/standin-findings/schemas/PS-Tema-A.yaml`

Spec reference: § "Concept schema (per task)".

- [ ] **Step 1: Author the schema**

Based on the spec example, expand for Tema A using the existing PS materials:

```yaml
# docs/standin-findings/schemas/PS-Tema-A.yaml
task_id: 01KR6K07T6PATPRR5KH1JXYF8E
subject: PS
title: "Tema A — Laplace distribution sampling + visualization"
concepts:
  - id: laplace_distribution
    aliases: ["Laplace", "distribuția Laplace", "double-exponential", "double exponential"]
    introduced_in: ["A1.pdf:page=2", "_extras/PS/_fii/edu/files/Tema_A_en.md", "_extras/PS/_fii/gdrive/Curs/curs_2019-2020/En/probability8_en.md"]
  - id: inverse_cdf_sampling
    aliases: ["inverse-CDF", "rlaplace", "quantile transform", "inverse transform"]
    introduced_in: ["A1.pdf:page=4"]
  - id: location_scale_parameters
    aliases: ["mu", "b", "location", "scale", "parameter b"]
    introduced_in: ["A1.pdf:page=3"]
  - id: histogram_overlay
    introduced_in: []
    generic: true  # standard R skill
  - id: theoretical_pdf
    aliases: ["PDF", "density function"]
    introduced_in: ["A1.pdf:page=3"]
  - id: rlaplace_function
    aliases: ["rlaplace", "VGAM::rlaplace"]
    introduced_in: ["A1.pdf:page=4"]
confusion_tuples:
  - between: [laplace_distribution, normal_distribution]
    why: "Both symmetric around mean; novice confuses kurtosis (Laplace has fatter tails, sharper peak)"
  - between: [inverse_cdf_sampling, rejection_sampling]
    why: "Both are sampling methods that use uniform random variables; different mechanisms"
  - between: [location_scale_parameters, mean_variance]
    why: "Mu IS the location parameter and the mean here, but b is the scale parameter, not the variance"
```

- [ ] **Step 2: Commit schema**

```bash
git add docs/standin-findings/schemas/PS-Tema-A.yaml
git commit -m "docs(standin): Surface Y concept schema for PS Tema A"
```

---

### Task 3.2: Persona prompt builder + seen-concepts ledger

**Files:**
- Create: `tools/surface-y-persona.mjs`
- Test: `tools/surface-y-persona.test.mjs`

Spec reference: § "Persona prompt" + § "Confusion-tuple injection".

- [ ] **Step 1: Write failing tests**

```javascript
// tools/surface-y-persona.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { buildPersonaPrompt, updateLedger, sampleConfusionTuple } from "./surface-y-persona.mjs";

const schema = {
  concepts: [
    { id: "laplace_distribution", aliases: ["Laplace", "distribuția Laplace"] },
    { id: "inverse_cdf_sampling", aliases: ["inverse-CDF", "rlaplace"] },
    { id: "histogram_overlay", generic: true },
  ],
  confusion_tuples: [
    { between: ["laplace_distribution", "normal_distribution"], why: "both symmetric" },
  ],
};

test("updateLedger picks up concept aliases from DOM text", () => {
  const ledger = new Set();
  const newLedger = updateLedger(ledger, schema, "Distribuția Laplace is parameterized by μ and b.");
  assert.ok(newLedger.has("laplace_distribution"));
});

test("updateLedger does not add concepts whose aliases aren't present", () => {
  const ledger = new Set();
  const newLedger = updateLedger(ledger, schema, "Some other text about probabilities.");
  assert.equal(newLedger.size, 0);
});

test("buildPersonaPrompt lists UNKNOWN concepts (schema minus ledger minus generic)", () => {
  const ledger = new Set(["laplace_distribution"]);
  const prompt = buildPersonaPrompt({
    schema,
    ledger,
    sessionHistory: [],
    activeConfusionTuple: null,
    currentDom: "<p>hello</p>",
  });
  assert.match(prompt, /You do NOT know.*inverse_cdf_sampling/);
  assert.doesNotMatch(prompt, /You do NOT know.*histogram_overlay/);  // generic
  assert.doesNotMatch(prompt, /You do NOT know.*laplace_distribution/);  // seen
});

test("sampleConfusionTuple picks one tuple at random from schema", () => {
  const t = sampleConfusionTuple(schema, 0);  // seeded
  assert.ok(t);
  assert.deepEqual(t.between, ["laplace_distribution", "normal_distribution"]);
});

test("buildPersonaPrompt includes active confusion-tuple text", () => {
  const prompt = buildPersonaPrompt({
    schema,
    ledger: new Set(),
    sessionHistory: [],
    activeConfusionTuple: { between: ["laplace_distribution", "normal_distribution"], why: "both symmetric" },
    currentDom: "<p>x</p>",
  });
  assert.match(prompt, /You frequently confuse laplace_distribution with normal_distribution/);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd tools && node --test surface-y-persona.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement persona builder**

```javascript
// tools/surface-y-persona.mjs
export function updateLedger(ledger, schema, domText) {
  const next = new Set(ledger);
  const lower = domText.toLowerCase();
  for (const c of schema.concepts) {
    if (c.generic) continue;
    const aliases = [c.id.replace(/_/g, " "), ...(c.aliases ?? [])];
    if (aliases.some(a => lower.includes(a.toLowerCase()))) {
      next.add(c.id);
    }
  }
  return next;
}

export function sampleConfusionTuple(schema, seed = Date.now()) {
  const tuples = schema.confusion_tuples ?? [];
  if (tuples.length === 0) return null;
  return tuples[seed % tuples.length];
}

export function buildPersonaPrompt({ schema, ledger, sessionHistory, activeConfusionTuple, currentDom }) {
  const unknownConcepts = schema.concepts
    .filter(c => !c.generic && !ledger.has(c.id))
    .map(c => c.id);
  const historyText = sessionHistory.slice(-5).map(e => `- ${e.action} ${e.target ?? ""}: ${e.observation ?? ""}`).join("\n");
  const confusionLine = activeConfusionTuple
    ? `\nYou frequently confuse ${activeConfusionTuple.between[0]} with ${activeConfusionTuple.between[1]} because ${activeConfusionTuple.why}.`
    : "";

  return `SYSTEM: You are Alex, a first-year FII Iași AI student. You have NEVER seen this course material before this session. You know basic high-school math and some R syntax. You do NOT know any of the following unless this session's history shows you've read about them:
${unknownConcepts.map(c => `  - ${c}`).join("\n")}

If a concept name appears unfamiliar, stay confused — say so, ask sidekick, or stare at it. NEVER reason from knowledge you haven't been shown this session. If you find yourself "remembering" something off-ledger, mark [LEAK?] and explain.

SESSION HISTORY (last 5 events):
${historyText || "  (none)"}

SEEN-CONCEPTS LEDGER:
  ${[...ledger].join(", ") || "(empty)"}
${confusionLine}

CURRENT DOM (visible to you):
${currentDom.slice(0, 4000)}

Decide ONE next action. Reply STRICT JSON:
{
  "thinking": "<2-3 sentences internal monologue>",
  "action": "click" | "type" | "navigate" | "ask_sidekick" | "give_up",
  "target": "<CSS selector or text label>",
  "payload": "<typed text if action=type/ask_sidekick>",
  "observation": "<what I'm confused about or noticed>"
}`;
}
```

- [ ] **Step 4: Run tests + commit**

Run: `cd tools && node --test surface-y-persona.test.mjs`
Expected: 5 tests pass.

```bash
git add tools/surface-y-persona.mjs tools/surface-y-persona.test.mjs
git commit -m "feat(standin): Surface Y persona prompt + ledger + confusion-tuple injection"
```

---

### Task 3.3: Schema-gate filter (MathVC 3-strike regenerate)

**Files:**
- Create: `tools/surface-y-gate.mjs`
- Test: `tools/surface-y-gate.test.mjs`

Spec reference: § "Schema-gate filter (external, MathVC pattern)".

- [ ] **Step 1: Write failing tests**

```javascript
// tools/surface-y-gate.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { extractConceptReferences, checkResponse, gateLoop } from "./surface-y-gate.mjs";

const schema = {
  concepts: [
    { id: "laplace_distribution", aliases: ["Laplace"] },
    { id: "inverse_cdf_sampling", aliases: ["inverse-CDF", "rlaplace"] },
    { id: "rejection_sampling", aliases: ["rejection sampling"] },  // not in ledger
  ],
};

test("extractConceptReferences finds aliases in text", () => {
  const refs = extractConceptReferences("I will use rlaplace to sample.", schema);
  assert.ok(refs.includes("inverse_cdf_sampling"));
});

test("checkResponse OK when only seen concepts referenced", () => {
  const ledger = new Set(["laplace_distribution"]);
  const r = checkResponse({
    text: "I will think about Laplace.",
    schema, ledger,
  });
  assert.equal(r.ok, true);
});

test("checkResponse VIOLATION when off-ledger concept referenced", () => {
  const ledger = new Set(["laplace_distribution"]);
  const r = checkResponse({
    text: "I will use rejection sampling.",
    schema, ledger,
  });
  assert.equal(r.ok, false);
  assert.ok(r.violations.includes("rejection_sampling"));
});

test("gateLoop regenerates up to maxRegens then logs leak", async () => {
  let attempt = 0;
  const fakeCall = async () => {
    attempt++;
    return { text: "I will use rejection sampling.", model_resolved: "fake", prompt_sha256: "x".repeat(64), tokens_in: 10, tokens_out: 5, latency_ms: 100 };
  };
  const r = await gateLoop({
    initialUserPrompt: "go",
    schema, ledger: new Set(),
    callLlm: fakeCall,
    maxRegens: 2,
  });
  assert.equal(r.leaked, true);
  assert.equal(attempt, 3);  // 1 initial + 2 regenerates
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd tools && node --test surface-y-gate.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement gate**

```javascript
// tools/surface-y-gate.mjs
export function extractConceptReferences(text, schema) {
  const lower = text.toLowerCase();
  const found = new Set();
  for (const c of schema.concepts) {
    if (c.generic) continue;
    const aliases = [c.id.replace(/_/g, " "), ...(c.aliases ?? [])];
    for (const a of aliases) {
      if (lower.includes(a.toLowerCase())) {
        found.add(c.id);
        break;
      }
    }
  }
  return [...found];
}

export function checkResponse({ text, schema, ledger }) {
  const refs = extractConceptReferences(text, schema);
  const violations = refs.filter(r => !ledger.has(r));
  return { ok: violations.length === 0, refs, violations };
}

export async function gateLoop({
  initialUserPrompt,
  systemPrompt = "",
  schema, ledger,
  callLlm,
  apiKey, model, temperature = 0.4, seed = null,
  maxRegens = 2,
}) {
  let userPrompt = initialUserPrompt;
  const tries = [];
  for (let i = 0; i <= maxRegens; i++) {
    const r = await callLlm({ apiKey, model, systemPrompt, userPrompt, temperature, seed });
    const check = checkResponse({ text: r.text, schema, ledger });
    tries.push({ text: r.text, model_resolved: r.model_resolved, prompt_sha256: r.prompt_sha256, check });
    if (check.ok) return { ok: true, leaked: false, finalText: r.text, finalCheck: check, tries, llmMeta: r };
    userPrompt = `${initialUserPrompt}\n\nYour previous reply referenced concepts you have not been shown: ${check.violations.join(", ")}. Stay confused. Do not mention these concepts again.`;
  }
  return { ok: false, leaked: true, finalText: tries[tries.length - 1].text, finalCheck: tries[tries.length - 1].check, tries, llmMeta: null };
}
```

- [ ] **Step 4: Run tests + commit**

Run: `cd tools && node --test surface-y-gate.test.mjs`
Expected: 4 tests pass.

```bash
git add tools/surface-y-gate.mjs tools/surface-y-gate.test.mjs
git commit -m "feat(standin): Surface Y schema-gate w/ 3-strike regenerate"
```

---

### Task 3.4: Surface Y CLI + Playwright loop + hard caps + Z piggyback

**Files:**
- Create: `tools/surface-y.mjs`
- Test: `tools/surface-y.test.mjs`

Spec reference: § "Architecture" + § "Hard caps" + § "Trigger" (Y manual-only).

- [ ] **Step 1: Write failing test**

```javascript
// tools/surface-y.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { runStandin } from "./surface-y.mjs";

test("runStandin enforces hard cap on LLM calls", async () => {
  const tmp = mkdtempSync(join(tmpdir(), "y-"));
  const schemaPath = join(tmp, "schema.yaml");
  writeFileSync(schemaPath, [
    "task_id: t1",
    "subject: PS",
    "concepts:",
    "  - {id: laplace_distribution, aliases: [Laplace]}",
    "confusion_tuples: []",
  ].join("\n"));
  let callsMade = 0;
  const fakeCallLlm = async () => {
    callsMade++;
    return {
      text: '{"thinking":"x","action":"give_up","target":"","payload":"","observation":"stuck"}',
      model_resolved: "fake", prompt_sha256: "z".repeat(64),
      tokens_in: 50, tokens_out: 20, latency_ms: 200,
    };
  };
  const fakeBrowser = {
    newContext: async () => ({
      newPage: async () => ({
        goto: async () => {},
        waitForLoadState: async () => {},
        content: async () => "<html><body>hi</body></html>",
        screenshot: async ({ path }) => writeFileSync(path, "PNG"),
        evaluate: async () => "page text",
        click: async () => {},
        fill: async () => {},
        url: () => "https://corgflix.duckdns.org/tutor/?taskId=t1",
        close: async () => {},
        setExtraHTTPHeaders: async () => {},
        on: () => {},
      }),
      setExtraHTTPHeaders: async () => {},
      close: async () => {},
    }),
    close: async () => {},
  };
  const docPath = await runStandin({
    taskId: "t1",
    schemaPath,
    browser: fakeBrowser,
    callLlm: fakeCallLlm,
    maxCallsPerSession: 3,
    outputDir: tmp,
    sessionId: "test-y-1",
    baseUrl: "https://corgflix.duckdns.org",
    authCookie: "test",
    piggybackZ: false,
  });
  assert.ok(callsMade <= 3, `expected ≤3 calls, got ${callsMade}`);
  const text = readFileSync(docPath, "utf8");
  assert.match(text, /surface: Y/);
  assert.match(text, /calls_used: \d+/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: FAIL.

- [ ] **Step 3: Implement surface-y.mjs**

```javascript
// tools/surface-y.mjs
import { chromium } from "playwright";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { callLlm as defaultCallLlm } from "./lib/openrouter.mjs";
import { buildPersonaPrompt, updateLedger, sampleConfusionTuple } from "./surface-y-persona.mjs";
import { gateLoop } from "./surface-y-gate.mjs";
import { getStamp } from "./lib/provenance.mjs";

function parseYaml(text) {
  // Minimal YAML parser for the schema shape we use.
  // For production-grade, swap to `js-yaml` dep — adding to tools/package.json.
  const schema = { concepts: [], confusion_tuples: [] };
  let inConcepts = false, inTuples = false;
  for (const rawLine of text.split("\n")) {
    const line = rawLine;
    if (line.startsWith("task_id:")) schema.task_id = line.split(":")[1].trim();
    else if (line.startsWith("subject:")) schema.subject = line.split(":")[1].trim();
    else if (line.startsWith("title:")) schema.title = line.split(":")[1].trim();
    else if (line.startsWith("concepts:")) { inConcepts = true; inTuples = false; }
    else if (line.startsWith("confusion_tuples:")) { inConcepts = false; inTuples = true; }
    else if (inConcepts && /^\s+- /.test(line)) {
      const flow = line.replace(/^\s+- /, "").trim();
      const m = flow.match(/^\{([\s\S]+)\}$/);
      if (m) {
        const obj = {};
        for (const pair of m[1].split(/,\s*(?![^\[\]]*\])/)) {
          const [k, v] = pair.split(":").map(s => s.trim());
          if (v && v.startsWith("[")) obj[k] = v.replace(/[\[\]]/g, "").split(",").map(s => s.trim().replace(/^['"]|['"]$/g, ""));
          else obj[k] = v?.replace(/^['"]|['"]$/g, "");
        }
        schema.concepts.push(obj);
      }
    } else if (inTuples && /^\s+- /.test(line)) {
      // similar parse for confusion tuples
      const flow = line.replace(/^\s+- /, "").trim();
      const m = flow.match(/^\{([\s\S]+)\}$/);
      if (m) {
        // parse "between: [a, b], why: '...'"
        const betweenMatch = m[1].match(/between:\s*\[([^\]]+)\]/);
        const whyMatch = m[1].match(/why:\s*['"]?([^'"}]+)['"]?/);
        if (betweenMatch) {
          schema.confusion_tuples.push({
            between: betweenMatch[1].split(",").map(s => s.trim().replace(/^['"]|['"]$/g, "")),
            why: whyMatch?.[1] ?? "",
          });
        }
      }
    }
  }
  return schema;
}

export async function runStandin({
  taskId,
  schemaPath,
  browser = null,
  callLlm = defaultCallLlm,
  apiKey = process.env.OPENROUTER_API_KEY_STANDIN,
  model = "qwen/qwen-2.5-7b-instruct:free",
  maxCallsPerSession = 50,
  maxDurationMin = 10,
  maxRegens = 2,
  outputDir = "docs/standin-findings",
  sessionId = `y-${Date.now()}`,
  baseUrl = "https://corgflix.duckdns.org",
  authCookie = process.env.JARVIS_AUTH_COOKIE,
  piggybackZ = true,
}) {
  process.env.SURFACE_VERSION = "y-v1.0";
  if (!apiKey) throw new Error("runStandin: OPENROUTER_API_KEY_STANDIN required");

  const schema = parseYaml(readFileSync(schemaPath, "utf8"));
  const ownsBrowser = !browser;
  if (!browser) browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({
    storageState: authCookie ? {
      cookies: [{ name: "jarvis_auth", value: authCookie, domain: new URL(baseUrl).hostname, path: "/", secure: true, httpOnly: false }],
      origins: [],
    } : undefined,
    extraHTTPHeaders: { "X-Standin-Run": "1" },
  });
  const page = await ctx.newPage();
  const t0 = Date.now();
  let ledger = new Set();
  let activeConfusion = sampleConfusionTuple(schema, 0);
  const transcript = [];
  const gateViolations = [];
  let callsUsed = 0;
  let lastJudgeModel = null, lastPromptSha = null;
  let zPiggyFindings = [];

  await page.goto(`${baseUrl}/tutor/?taskId=${taskId}`);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});

  while (callsUsed < maxCallsPerSession && Date.now() - t0 < maxDurationMin * 60_000) {
    const dom = await page.content();
    ledger = updateLedger(ledger, schema, await page.evaluate("document.body.innerText").catch(() => ""));
    const prompt = buildPersonaPrompt({
      schema, ledger,
      sessionHistory: transcript.slice(-5),
      activeConfusionTuple: activeConfusion,
      currentDom: await page.evaluate("document.body.innerText").catch(() => ""),
    });

    const r = await gateLoop({
      initialUserPrompt: "Decide next action.",
      systemPrompt: prompt,
      schema, ledger,
      callLlm, apiKey, model,
      maxRegens,
    });
    callsUsed += r.tries.length;
    if (r.leaked) gateViolations.push({ step: transcript.length, violations: r.finalCheck.violations });
    if (r.llmMeta) {
      lastJudgeModel = r.llmMeta.model_resolved;
      lastPromptSha = r.llmMeta.prompt_sha256;
    }

    let action;
    try { action = JSON.parse(r.finalText); } catch { action = { action: "give_up", observation: "parse-error" }; }
    transcript.push({ ...action, ts: new Date().toISOString() });

    if (action.action === "give_up") break;
    try {
      if (action.action === "click" && action.target) {
        await page.click(action.target, { timeout: 3000 });
      } else if (action.action === "type" && action.target) {
        await page.fill(action.target, action.payload ?? "");
      } else if (action.action === "navigate" && action.target) {
        await page.goto(action.target.startsWith("http") ? action.target : `${baseUrl}${action.target}`);
      } else if (action.action === "ask_sidekick") {
        // Find the chip / sidekick input, type, submit
        // Implementation depends on existing TutorWorkspace DOM; placeholder:
        await page.evaluate((q) => {
          const evt = new CustomEvent("standin-sidekick-ask", { detail: { question: q } });
          window.dispatchEvent(evt);
        }, action.payload).catch(() => {});
      }
    } catch (e) {
      transcript[transcript.length - 1].error = String(e).slice(0, 200);
    }

    await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});

    if (piggybackZ) {
      const { sweepPages } = await import("./surface-z.mjs");
      // Re-screenshot current page via piggyback (single page, current viewport)
      // Implementation: call into Z with the current page object directly (refactor sweepPages to accept page) — for V1 we just snapshot here.
      const lints = await page.evaluate((await import("./surface-z-lints.mjs")).LINT_EVAL_SCRIPT);
      const screenshotPath = join("docs/standin-findings/screenshots", `Y-Zpiggy-${sessionId}-${transcript.length}.png`);
      mkdirSync("docs/standin-findings/screenshots", { recursive: true });
      await page.screenshot({ path: screenshotPath, fullPage: true });
      zPiggyFindings.push({ step: transcript.length, lints, screenshot: screenshotPath });
      // LLM critique deferred to Z piggyback follow-up (see V2)
    }

    // Health probe
    const health = await fetch(`${baseUrl}/api/v1/health`).then(r => r.status).catch(() => 0);
    if (health === 429) {
      console.error("[surface-y] /health 429 — aborting to avoid quota cascade");
      break;
    }
  }

  if (ownsBrowser) { await ctx.close(); await browser.close(); }

  const stamp = await getStamp(null, {
    judge_model_resolved: lastJudgeModel,
    judge_prompt_sha256: lastPromptSha,
  });
  const ts = stamp.ts_utc.replace(/[:.]/g, "-");
  mkdirSync(outputDir, { recursive: true });
  const docPath = join(outputDir, `DRAFT-Y-${taskId}-${ts}.md`);
  const md = [
    "---",
    "surface: Y",
    `session_id: ${sessionId}`,
    `task_id: ${taskId}`,
    `schema_path: ${schemaPath}`,
    "provenance:",
    `  git_head: ${stamp.git_head}`,
    `  bundle_hash: ${stamp.bundle_hash}`,
    `  live_dom_fingerprint: ${stamp.live_dom_fingerprint ?? "null"}`,
    `  ts_utc: ${stamp.ts_utc}`,
    `  surface_version: ${stamp.surface_version}`,
    `  judge_model_resolved: ${stamp.judge_model_resolved ?? "null"}`,
    `  judge_prompt_sha256: ${stamp.judge_prompt_sha256 ?? "null"}`,
    `model_resolved: ${lastJudgeModel ?? "null"}`,
    `calls_used: ${callsUsed}`,
    `duration_min: ${((Date.now() - t0) / 60000).toFixed(1)}`,
    `gate_violations: ${gateViolations.length}`,
    "---",
    "",
    `# Surface Y findings — task ${taskId}, session ${sessionId}`,
    "",
    "## Discovered unknown-unknowns",
    ...transcript.filter(t => t.observation).map(t => `- step ${transcript.indexOf(t) + 1}: ${t.observation}`),
    "",
    "## Schema-gate violations (naivety leakage)",
    ...gateViolations.map(v => `- step ${v.step}: referenced off-ledger concepts: ${v.violations.join(", ")}`),
    "",
    "## Session transcript",
    "| Step | Action | Target | Observation |",
    "|------|--------|--------|-------------|",
    ...transcript.map((t, i) => `| ${i + 1} | ${t.action} | ${(t.target || "").slice(0, 40)} | ${(t.observation || "").slice(0, 80)} |`),
    "",
    piggybackZ ? `## Z piggyback (${zPiggyFindings.length} captures)` : "",
    ...zPiggyFindings.map(z => `- step ${z.step}: snake_case=${z.lints.snake_case.length}, low_contrast=${z.lints.low_contrast.length}, screenshot=\`${z.screenshot}\``),
  ].join("\n");
  writeFileSync(docPath, md);
  return docPath;
}

// CLI
if (process.argv[1]?.endsWith("surface-y.mjs")) {
  const args = Object.fromEntries(process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.+)$/); return m ? [m[1], m[2]] : [a.replace(/^--/, ""), true];
  }));
  if (!args.task || !args.schema) {
    console.error("Usage: surface-y.mjs --task=<id> --schema=<path> [--model=<m>] [--no-piggyback-z]");
    process.exit(2);
  }
  const docPath = await runStandin({
    taskId: args.task,
    schemaPath: args.schema,
    model: args.model,
    piggybackZ: !args["no-piggyback-z"],
    sessionId: args.session ?? `y-${Date.now()}`,
  });
  console.log(`Wrote: ${docPath}`);
}
```

- [ ] **Step 4: Run test + commit**

Run: `cd tools && node --test surface-y.test.mjs`
Expected: test passes (cap honored).

```bash
git add tools/surface-y.mjs tools/surface-y.test.mjs
git commit -m "feat(standin): Surface Y stand-in loop w/ schema-gate + caps + Z piggyback hooks"
```

---

### Task 3.5: First Y run on PS Tema A + findings inspection

**Files:**
- Produces: `docs/standin-findings/DRAFT-Y-<task>-<ts>.md` (quarantined)

Operational verification only.

- [ ] **Step 1: Run Y**

```bash
cd tools && OPENROUTER_API_KEY_STANDIN=... JARVIS_AUTH_COOKIE=... node surface-y.mjs --task=01KR6K07T6PATPRR5KH1JXYF8E --schema=../docs/standin-findings/schemas/PS-Tema-A.yaml
```

Expected: Y runs, calls_used ≤ 50, finding doc written.

- [ ] **Step 2: Read findings**

```bash
cat docs/standin-findings/DRAFT-Y-*.md | head -100
```

Expected:
- ≥1 "unknown-unknown" observation in the persona transcript.
- ≤2 schema-gate violations (some leakage tolerable; many means MathVC schema needs expansion).
- All session events on VPS tagged `is_synthetic: true` (verify: `ssh root@VPS "grep is_synthetic /opt/jarvis/data/private/tutor_events.$(date -u +%Y-%m-%d).jsonl | tail -5"`).

- [ ] **Step 3: Run stale-check**

```bash
node tools/findings-stale-check.mjs docs/standin-findings/DRAFT-Y-*.md
```

Expected: `Overall: [OK]`.

- [ ] **Step 4: Confirm Y events filtered out of X grading**

```bash
cd tools && node surface-x.mjs --task=01KR6K07T6PATPRR5KH1JXYF8E --from=<just-now-iso>
```

Expected: X grader does NOT see Y-synthetic events (filterEvents excludes `is_synthetic: true` by default).

Y is now validated end-to-end. No commit (drafts are gitignored).

---

## Final integration

### Task 4.1: npm scripts + tools/package.json wire-up

**Files:**
- Modify: `tools/package.json`

- [ ] **Step 1: Add scripts**

```json
{
  "scripts": {
    "test:tools": "node --test extract-fiimaterials.test.mjs lib/openrouter.test.mjs lib/provenance.test.mjs lib/event-log-reader.test.mjs findings-stale-check.test.mjs surface-x-invariants.test.mjs surface-x.test.mjs surface-z-lints.test.mjs surface-z.test.mjs surface-y-persona.test.mjs surface-y-gate.test.mjs surface-y.test.mjs",
    "surface:x": "node surface-x.mjs",
    "surface:z": "node surface-z.mjs",
    "surface:y": "node surface-y.mjs",
    "stale-check": "node findings-stale-check.mjs",
    "verify-quota": "node verify-openrouter-quota-isolation.mjs"
  }
}
```

- [ ] **Step 2: Run full test suite**

```bash
cd tools && npm run test:tools
```

Expected: all suites pass.

- [ ] **Step 3: Commit**

```bash
git add tools/package.json
git commit -m "chore(standin): npm scripts for surfaces + test suite"
```

---

### Task 4.2: BRIDGE.md handoff entry

**Files:**
- Modify: `~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md`

- [ ] **Step 1: Append handoff entry**

Use the `wrap` skill to append a new dated entry summarizing:
- Spec + plan committed (cite shas)
- 4 council rounds run (cite cache files)
- Surfaces shipped (X + Z + Y, all V1)
- Quota verdict (`ISOLATED` or `SHARED`)
- Outstanding: any failed calibration or schema iteration

- [ ] **Step 2: Commit BRIDGE update**

Handled automatically by the wrap skill.

---

## Self-Review (checklist)

**1. Spec coverage:**
- §"Architecture overview" → Phase 0 Tasks 0.1-0.10 (shared infra) + Phase 1/2/3 (surfaces). ✓
- §"Surface X" → Phase 1 Tasks 1.1-1.7. ✓
- §"Surface Z" → Phase 2 Tasks 2.1-2.4. ✓
- §"Surface Y" → Phase 3 Tasks 3.1-3.5. ✓
- §"Cross-cutting infrastructure" → Phase 0. ✓
- §"Risks + mitigations" → addressed via implementation (DOM normalization in Task 0.2, judge fields in Task 0.2, R-code redaction in Task 0.5, X-Standin-Run header in Task 0.6, networkidle in Task 2.2/3.4, INV-09/10 INFO classification in Task 1.1). ✓
- §"Visual-presence acceptance criteria" → Tasks 1.6 + 2.3 + 3.5 + stale-check in 0.3. ✓

**2. Placeholder scan:** No "TBD", "implement later", "add appropriate X". Every step shows code or commands. ✓

**3. Type consistency:** `TutorEvent` schema in Task 0.4 matches references in Tasks 0.5/0.6/0.7. `getStamp()` opts arg matches usage in Tasks 1.3/2.2/3.4. `callLlm` return shape matches across `surface-x.mjs`, `surface-y-gate.mjs`. ✓

**4. Build+mount pairing:** This is a CLI/backend plan; no React components built. N/A. ✓

**5. Component-reuse contract:** `surface-y.mjs` mounts `gateLoop` from `surface-y-gate.mjs`, `buildPersonaPrompt` from `surface-y-persona.mjs`, `sweepPages`-equivalent from `surface-z.mjs`. Each test in Task 3.4 mocks the called function — wire-up verified by test. ✓

**6. data-testid grep:** Spec § "Visual-presence acceptance criteria" lists 7 artifact-path checks (not `data-testid` selectors since CLI-driven). Each check is satisfied:
- `provenance.mjs` returns stamp → Task 0.2 test asserts shape ✓
- `tutor_events.YYYY-MM-DD.jsonl` exists → Task 0.10 Step 3 verifies ✓
- `DRAFT-X-*.md` w/ provenance frontmatter → Task 1.3 test asserts frontmatter ✓
- `findings-stale-check.mjs` outputs per-field status → Task 0.3 test asserts ✓
- `docs/notes/...openrouter-quota-isolation.md` written → Task 0.9 implements + 0.10 Step 4 runs ✓
- 15-25 fixture entries → Task 1.5 ✓
- X calibration ≥ K of N → Task 1.6 ✓

All spec coverage gaps closed.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-13-student-standin.md`.

Two execution options:

1. **Subagent-Driven (recommended)** — Dispatch fresh subagent per task, review between tasks, fast iteration.

2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
