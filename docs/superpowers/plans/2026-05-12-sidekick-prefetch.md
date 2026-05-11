# Sidekick Pre-Fetch Retrieval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Server-side pre-fetch corpus material before the sidekick LLM call when the user selects text in a tutor workspace, so chip-flow replies always include `_extras/<subject>/` citation pills regardless of whether the LLM tool-calls `search_archival`.

**Architecture:** A new `SelectionQueryBuilder` decides whether a selection is a usable retrieval query (length-gate, anchor-text fallback, sanitize `</retrieved_context>` literals, reject pure-LaTeX). The sidekick handler in `TutorRoutes.kt` runs `HybridRetriever.search` synchronously under a try/catch, normalizes hit paths to forward-slash, and threads the hits into `SidekickContext.systemContext` which embeds them as a `<retrieved_context source="prefetched_corpus" trust="indexed_data">` block with 200-char-capped snippets. The `search_archival` tool stays in the LLM palette so the model can broaden; `CitationExtractor` unions pre-fetched + LLM-fetched hits for path verification.

**Tech Stack:** Kotlin 2.0.21, Ktor 3.0.1, Exposed 0.55.0, JUnit 5 + kotlin.test, kotlinx.serialization, Playwright (MJS gate runner), gradle :installDist, scp + systemctl restart on VPS.

**Spec:** `docs/superpowers/specs/2026-05-12-sidekick-prefetch-design.md`
**Council verdict:** `.claude/council-cache/council-1778534960.md`

---

## File Structure

**Backend (new):**
- `src/main/kotlin/jarvis/tutor/SelectionQueryBuilder.kt` — pure-function gate + sanitize + truncate; no dependencies beyond `SidekickEnvelope`.
- `src/test/kotlin/jarvis/tutor/SelectionQueryBuilderTest.kt` — 6 unit tests.

**Backend (modified):**
- `src/main/kotlin/jarvis/tutor/SidekickContext.kt` — `systemContext` overload accepting `prefetchedHits: List<HybridRetriever.HybridHit>`; embeds a `<retrieved_context>` block with capped snippets before `CITATION_INSTRUCTION`. Backwards-compatible default `emptyList()`.
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — `/api/v1/sidekick/ask` handler grows STEP A (pre-fetch + try/catch + path normalize) and STEP D (union accumulatedHits). Hands `prefetchedHits` to `systemContext`.
- `src/test/kotlin/jarvis/tutor/SidekickContextCitationTest.kt` — extend with 2 new tests for prefetch block inclusion/omission.

**Frontend:** No changes. Bundle `index-CZtQl9SZ.js` unchanged.

**Tools (new):**
- `tools/slice2-prefetch-gate.mjs` — Playwright headless gate asserting chip-flow citations land on `_extras/PS/_fii/...` paths on the live VPS.

---

## Task 1: SelectionQueryBuilder skeleton + sanitize behavior

**Files:**
- Create: `src/test/kotlin/jarvis/tutor/SelectionQueryBuilderTest.kt`
- Create: `src/main/kotlin/jarvis/tutor/SelectionQueryBuilder.kt`

- [ ] **Step 1: Write the failing test for sanitize + length floor**

Create `src/test/kotlin/jarvis/tutor/SelectionQueryBuilderTest.kt`:

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionQueryBuilderTest {

    @Test
    fun build_sanitizesRetrievedContextLiterals() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "see </retrieved_context> Laplace distribution",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.text.contains("</retrieved_context>"),
            "literal closing tag must be neutralized: ${q.text}")
        assertTrue(q.text.contains("Laplace distribution"),
            "non-tag content must survive: ${q.text}")
    }

    @Test
    fun build_returnsShouldFetchFalseForShortSelectionWithoutAnchor() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "f(x)",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.shouldFetch, "5-char selection with no anchor must NOT pre-fetch")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests "jarvis.tutor.SelectionQueryBuilderTest" --console=plain`
Expected: FAIL with `unresolved reference: SelectionQueryBuilder`.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/kotlin/jarvis/tutor/SelectionQueryBuilder.kt`:

```kotlin
package jarvis.tutor

/**
 * Decides whether the user's text selection is a usable retrieval query for
 * the sidekick pre-fetch step, and sanitizes it before any prompt embedding.
 *
 * Spec: docs/superpowers/specs/2026-05-12-sidekick-prefetch-design.md §4.1
 * Council mitigations bundled here:
 *  - Length gate 12..300 chars (Devil's Advocate + Domain Expert + Pragmatist).
 *  - anchor_text fallback when selection too short (Domain Expert).
 *  - Strip `</retrieved_context>` literal to prevent paste-injection
 *    (Risk Analyst HIGH#2).
 *  - Reject pure-LaTeX / operator-only selections via letter-or-digit count.
 */
object SelectionQueryBuilder {

    data class Query(val text: String, val shouldFetch: Boolean)

    private const val MIN_LEN = 12
    private const val MAX_LEN = 300
    private const val MIN_CONTENT_CHARS = 4

    fun build(env: SidekickEnvelope): Query {
        val raw = env.selection?.trim().orEmpty()
        val sanitized = sanitize(raw)

        val candidate = if (sanitized.length < MIN_LEN) {
            env.anchorText?.trim()?.let { sanitize(it) }.orEmpty()
        } else sanitized

        val truncated = candidate.take(MAX_LEN)
        val ok = truncated.length in MIN_LEN..MAX_LEN && hasContentChars(truncated)
        return Query(text = truncated, shouldFetch = ok)
    }

    private fun sanitize(s: String): String =
        s.replace("<retrieved_context", "&lt;retrieved_context", ignoreCase = true)
            .replace("</retrieved_context>", "&lt;/retrieved_context&gt;", ignoreCase = true)
            .replace("```", "` ``")

    private fun hasContentChars(s: String): Boolean =
        s.count { it.isLetterOrDigit() } >= MIN_CONTENT_CHARS
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests "jarvis.tutor.SelectionQueryBuilderTest" --console=plain`
Expected: PASS — 2 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/SelectionQueryBuilder.kt src/test/kotlin/jarvis/tutor/SelectionQueryBuilderTest.kt
git commit -m "feat(sidekick): SelectionQueryBuilder with sanitize + length-floor gate"
```

---

## Task 2: SelectionQueryBuilder anchor fallback + max-length cap + content-char gate

**Files:**
- Modify: `src/test/kotlin/jarvis/tutor/SelectionQueryBuilderTest.kt`

- [ ] **Step 1: Add the failing tests**

Append to `src/test/kotlin/jarvis/tutor/SelectionQueryBuilderTest.kt` inside the existing class:

```kotlin
    @Test
    fun build_fallsBackToAnchorTextWhenSelectionTooShort() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            anchorText = "Laplace distribution has its probability density function defined by f(x).",
            selection = "f(x)",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertTrue(q.shouldFetch, "anchor fallback must kick in for short selection")
        assertTrue(q.text.contains("Laplace distribution"),
            "anchor content must appear in query: ${q.text}")
    }

    @Test
    fun build_rejectsPureLatexLikeSelection() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = "\\frac{1}{2b} e^{-|x-\\mu|/b}",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.shouldFetch,
            "selection with <4 letter-or-digit chars must NOT pre-fetch: text=${q.text}")
    }

    @Test
    fun build_truncatesAtMax300Chars() {
        val long = "Laplace ".repeat(50)
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            selection = long,
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertEquals(300, q.text.length, "text must be truncated to MAX_LEN")
        assertTrue(q.shouldFetch, "truncated long selection still fetches")
    }

    @Test
    fun build_returnsShouldFetchFalseWhenSelectionAndAnchorBothMissing() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            userQuestion = "explain"
        )
        val q = SelectionQueryBuilder.build(env)
        assertFalse(q.shouldFetch, "no selection + no anchor must NOT pre-fetch")
        assertEquals("", q.text)
    }
```

- [ ] **Step 2: Run tests to verify they pass without further code changes**

Run: `gradle :test --tests "jarvis.tutor.SelectionQueryBuilderTest" --console=plain`
Expected: PASS — 6 tests, 0 failures. (The Task-1 implementation already covers these — these tests pin the behavior.)

If any test fails (e.g. `hasContentChars` is incorrect), revise `SelectionQueryBuilder.kt` until all 6 pass before proceeding.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/jarvis/tutor/SelectionQueryBuilderTest.kt
git commit -m "test(sidekick): anchor fallback + max-len + latex-reject + empty cases"
```

---

## Task 3: SidekickContext.systemContext accepts prefetchedHits

**Files:**
- Modify: `src/test/kotlin/jarvis/tutor/SidekickContextCitationTest.kt`
- Modify: `src/main/kotlin/jarvis/tutor/SidekickContext.kt`

- [ ] **Step 1: Write the failing tests**

Append to `src/test/kotlin/jarvis/tutor/SidekickContextCitationTest.kt` inside the existing class:

```kotlin
    @Test
    fun systemContext_includesPrefetchBlockWhenHitsPresent() {
        val env = SidekickEnvelope(taskId = "01A", selection = "Laplace", userQuestion = "q")
        val hits = listOf(
            jarvis.HybridRetriever.HybridHit(
                source = "lexical",
                id = "_extras/PS/_fii/edu/files/Tema_A_en.md",
                snippet = "Laplace distribution has its probability density function defined by f(x).",
                score = 0.95,
            )
        )
        val ctx = SidekickContext.systemContext(env, prefetchedHits = hits)
        assertTrue(ctx.contains("prefetched_corpus"),
            "system context must announce prefetched_corpus block: $ctx")
        assertTrue(ctx.contains("_extras/PS/_fii/edu/files/Tema_A_en.md"),
            "path must be visible to LLM so it can cite it: $ctx")
        assertTrue(ctx.contains("Laplace distribution"),
            "snippet content must be visible to LLM: $ctx")
    }

    @Test
    fun systemContext_omitsPrefetchBlockWhenHitsEmpty() {
        val env = SidekickEnvelope(taskId = "01A", selection = "Laplace", userQuestion = "q")
        val ctx = SidekickContext.systemContext(env, prefetchedHits = emptyList())
        assertFalse(ctx.contains("prefetched_corpus"),
            "no prefetched block when hits list empty: $ctx")
    }

    @Test
    fun systemContext_truncatesSnippetAt200Chars() {
        val longSnippet = "x".repeat(500)
        val env = SidekickEnvelope(taskId = "01A", selection = "q", userQuestion = "q")
        val hits = listOf(
            jarvis.HybridRetriever.HybridHit(
                source = "lexical",
                id = "_extras/PS/foo.md",
                snippet = longSnippet,
                score = 0.5,
            )
        )
        val ctx = SidekickContext.systemContext(env, prefetchedHits = hits)
        // 200-char snippet plus path + score header; full 500-char run must NOT appear.
        assertFalse(ctx.contains("x".repeat(201)),
            "snippet must be capped at 200 chars per spec §4.3")
    }
```

Also add the import at the top of the file if not already present:

```kotlin
import kotlin.test.assertFalse
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle :test --tests "jarvis.tutor.SidekickContextCitationTest" --console=plain`
Expected: FAIL — `systemContext` does not accept `prefetchedHits` parameter (compile error or "too many arguments").

- [ ] **Step 3: Extend SidekickContext.systemContext**

Open `src/main/kotlin/jarvis/tutor/SidekickContext.kt`. Replace the existing `fun systemContext(env: SidekickEnvelope): String { ... }` with:

```kotlin
    private const val PREFETCH_SNIPPET_CAP = 200

    /**
     * Build the sidekick system prompt for [env]. When [prefetchedHits] is
     * non-empty, embed them as a <retrieved_context source="prefetched_corpus"
     * trust="indexed_data"> block BEFORE the citation instruction — the LLM
     * sees them as already-fetched corpus material and can cite their paths
     * directly via (src: <path>) without invoking search_archival.
     *
     * Snippets are capped at PREFETCH_SNIPPET_CAP chars to keep the system
     * prompt budget bounded (~600 tokens at k=3 vs. ~1800 uncapped). Per
     * council Pragmatist KEY CONCERN, this gates token blow-up on long
     * Tema A sessions.
     */
    fun systemContext(
        env: SidekickEnvelope,
        prefetchedHits: List<jarvis.HybridRetriever.HybridHit> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append("# Sidekick context\n")
        env.taskId?.let { sb.append("task: ").append(it).append('\n') }
        env.problemId?.let { sb.append("problem: ").append(it).append('\n') }
        env.cardTitle?.let { sb.append("card: ").append(it).append('\n') }
        env.anchorText?.let {
            sb.append("paragraph the user is asking about:\n")
            sb.append(it.take(800)).append('\n')
        }
        env.selection?.let {
            sb.append("specific selection inside that paragraph:\n  \"")
            sb.append(it.take(200)).append("\"\n")
        }
        if (prefetchedHits.isNotEmpty()) {
            val body = buildString {
                for (h in prefetchedHits) {
                    append("[prefetched score=")
                    append("%.3f".format(h.score))
                    append("] ")
                    append(h.id)
                    append('\n')
                    append(h.snippet.replace('\n', ' ').take(PREFETCH_SNIPPET_CAP))
                    append("\n\n")
                }
            }.trimEnd()
            sb.append(
                PromptInjectionScrubber.wrap(
                    source = "prefetched_corpus",
                    trust = "indexed_data",
                    content = body,
                )
            )
            sb.append('\n')
        }
        sb.append(CITATION_INSTRUCTION)
        return PromptInjectionScrubber.wrap(
            source = "sidekick_context",
            trust = "user_anchor",
            content = sb.toString(),
        )
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :test --tests "jarvis.tutor.SidekickContextCitationTest" --console=plain`
Expected: PASS — 4 tests (1 original + 3 new), 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/SidekickContext.kt src/test/kotlin/jarvis/tutor/SidekickContextCitationTest.kt
git commit -m "feat(sidekick): systemContext embeds prefetched_corpus block (200-char snippet cap)"
```

---

## Task 4: Wire pre-fetch into sidekick handler with try/catch + path normalize + union

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt:1296-1339`

This task has no separate test file — the integration is exercised end-to-end by the Playwright gate in Task 5. The unit tests in Tasks 1-3 cover the components in isolation. Per spec §6 the load-bearing invariant is "pre-fetch failure must degrade silently to empty hits, never 500"; the try/catch below enforces that.

- [ ] **Step 1: Read current handler body**

Run: `sed -n '1296,1339p' src/main/kotlin/jarvis/web/TutorRoutes.kt`

You should see the handler that starts with `post("/api/v1/sidekick/ask") {` and ends after `call.respond(HttpStatusCode.OK, ApiSidekickReply(...))`. Confirm line numbers match this plan; if the file has drifted, locate the block by `post("/api/v1/sidekick/ask")` and apply the edit at that location.

- [ ] **Step 2: Replace the handler body to add pre-fetch + normalize + union**

Find this exact existing block:

```kotlin
                val systemContext = jarvis.tutor.SidekickContext.systemContext(env)
                var text: String
                var model: String
                var retrievalHits: List<jarvis.HybridRetriever.HybridHit> = emptyList()
                try {
                    jarvis.tutor.JarvisToolset().use { ts ->
                        val r = kotlinx.coroutines.runBlocking {
                            ts.chat(systemPrompt = systemContext, userText = env.userQuestion)
                        }
                        text = r.text
                        model = r.model
                        retrievalHits = r.hits
                    }
                } catch (e: Exception) {
                    // Graceful degraded reply: 200 with an "unavailable" body so
                    // the frontend renders the existing "(LLM unavailable)" branch
                    // and the interaction-smoke gate doesn't flag transient
                    // upstream OpenRouter failures as a wiring bug. Slice 1 spec
                    // §I: "Sidekick LLM 5xx → render `(LLM unavailable; rate-limited?)`".
                    text = "(LLM unavailable; rate-limited? ${e.message?.take(120) ?: ""})"
                    model = "(none)"
                }
                val quoted = env.selection ?: env.anchorText?.take(160)
                val citations = jarvis.tutor.CitationExtractor.extract(text, retrievalHits)
                call.respond(HttpStatusCode.OK, ApiSidekickReply(text = text, model = model, quotedContext = quoted, citations = citations))
```

Replace it with:

```kotlin
                // Spec §3 STEP A — pre-fetch corpus material when selection is
                // a usable query. Mirrors RAG pre-retrieval pattern; closes
                // GAP-1 (chip-flow never triggered search_archival on its own).
                val selectionQuery = jarvis.tutor.SelectionQueryBuilder.build(env)
                val prefetchedHits: List<jarvis.HybridRetriever.HybridHit> = if (selectionQuery.shouldFetch) {
                    val subject = env.taskId
                        ?.let { jarvis.tutor.TaskRepo(ctx.db).findById(it)?.subject }
                        ?.takeIf { it.isNotBlank() }
                    try {
                        val key = jarvis.resolveOpenRouterKey()
                        val embedFn: (suspend (String) -> kotlin.FloatArray)? = if (!key.isNullOrBlank()) {
                            { q -> jarvis.embeddings.EmbeddingsClient(key).use { c -> c.embed(q) } }
                        } else null
                        val raw = kotlinx.coroutines.runBlocking {
                            jarvis.HybridRetriever.search(selectionQuery.text, k = 3, semanticEmbed = embedFn)
                        }
                        // Spec §4.4 — normalize OS-native separators to '/' at the
                        // pre-fetch boundary so the union with LLM-fetched hits and
                        // subsequent CitationExtractor regex match share one canonical
                        // form (Risk Analyst MEDIUM, also matches /reprep convention).
                        val normalized = raw.map { it.copy(id = it.id.replace('\\', '/')) }
                        if (!subject.isNullOrBlank()) {
                            normalized.filter { it.id.startsWith("_extras/$subject/", ignoreCase = true) }
                        } else normalized
                    } catch (e: Exception) {
                        // Spec §6 critical invariant — pre-fetch failure must
                        // degrade to empty hits, NEVER 500. Mirror the LLM
                        // exception handler's graceful pattern.
                        System.err.println("[sidekick prefetch] ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                        emptyList()
                    }
                } else emptyList()

                val systemContext = jarvis.tutor.SidekickContext.systemContext(env, prefetchedHits = prefetchedHits)
                var text: String
                var model: String
                var llmHits: List<jarvis.HybridRetriever.HybridHit> = emptyList()
                try {
                    jarvis.tutor.JarvisToolset().use { ts ->
                        val r = kotlinx.coroutines.runBlocking {
                            ts.chat(systemPrompt = systemContext, userText = env.userQuestion)
                        }
                        text = r.text
                        model = r.model
                        llmHits = r.hits
                    }
                } catch (e: Exception) {
                    text = "(LLM unavailable; rate-limited? ${e.message?.take(120) ?: ""})"
                    model = "(none)"
                }
                // Spec §3 STEP D — union pre-fetched ∪ LLM-fetched, dedupe by id.
                val unionedHits = (prefetchedHits + llmHits).distinctBy { it.id }
                val quoted = env.selection ?: env.anchorText?.take(160)
                val citations = jarvis.tutor.CitationExtractor.extract(text, unionedHits)
                call.respond(HttpStatusCode.OK, ApiSidekickReply(text = text, model = model, quotedContext = quoted, citations = citations))
```

- [ ] **Step 3: Compile to verify no type errors**

Run: `gradle :compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL with `:compileKotlin` task. If you see `Unresolved reference: SelectionQueryBuilder`, Task 1 wasn't merged — go fix it first.

- [ ] **Step 4: Run the full sidekick-adjacent test suite**

Run: `gradle :test --tests "jarvis.tutor.SidekickContextCitationTest" --tests "jarvis.tutor.SelectionQueryBuilderTest" --tests "jarvis.tutor.CitationExtractorTest" --tests "jarvis.tutor.JarvisToolDefsTest" --console=plain`
Expected: PASS — all tests green. (No regression in CitationExtractor since `unionedHits` is just a deduped list.)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "feat(sidekick): pre-fetch corpus on selection before LLM, union with tool hits"
```

---

## Task 5: Playwright interaction-smoke gate for chip-flow citations

**Files:**
- Create: `tools/slice2-prefetch-gate.mjs`

The gate runs against the live VPS bundle `index-CZtQl9SZ.js` and exercises the chip-flow end-to-end. Per the 2026-05-11 selectors-painted-≠-selectors-work rule, this is the load-bearing acceptance check.

- [ ] **Step 1: Inspect the existing Slice 2 gate for auth + selectors patterns**

Run: `sed -n '1,80p' tools/slice2-playwright-gate.mjs`

Confirm the auth-cookie + CSRF flow + headless-Chromium launch shape. The new gate reuses the same envelope.

- [ ] **Step 2: Write the new gate**

Create `tools/slice2-prefetch-gate.mjs`:

```javascript
// Slice 2.5 chip-flow citation gate (sidekick pre-fetch).
//
// Verifies: chip click on a "distributia Laplace" selection produces a
// sidekick reply with at least one [data-testid="citation-pill"] whose
// path starts under "_extras/PS/". Then clicks the pill and asserts the
// [data-testid="concept-drawer"] appears with the resource content,
// without any new 4xx/5xx network response.
//
// Run: cd tools && npx playwright test slice2-prefetch-gate.mjs
//   or: node slice2-prefetch-gate.mjs   (uses bundled chromium)
//
// Env:
//   JARVIS_AUTH_COOKIE  — value of jarvis_auth cookie for the live VPS.
//                         Read from process.env or AUTH_TOKEN.txt (sibling).
//   JARVIS_TUTOR_URL    — defaults to https://corgflix.duckdns.org

import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = process.env.JARVIS_TUTOR_URL ?? "https://corgflix.duckdns.org";
const TASK_ID = "01KR6K07T6PATPRR5KH1JXYF8E";

function loadAuthCookie() {
    if (process.env.JARVIS_AUTH_COOKIE) return process.env.JARVIS_AUTH_COOKIE;
    const here = path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, "$1"));
    const file = path.join(here, "AUTH_TOKEN.txt");
    if (fs.existsSync(file)) return fs.readFileSync(file, "utf8").trim();
    throw new Error("JARVIS_AUTH_COOKIE not set and tools/AUTH_TOKEN.txt missing");
}

async function main() {
    const auth = loadAuthCookie();
    const browser = await chromium.launch({ headless: true });
    const ctx = await browser.newContext();
    await ctx.addCookies([{
        name: "jarvis_auth", value: auth,
        domain: "corgflix.duckdns.org", path: "/",
        httpOnly: false, secure: true, sameSite: "Lax",
    }]);
    const page = await ctx.newPage();

    const networkErrors = [];
    page.on("response", (resp) => {
        const s = resp.status();
        if (s >= 400 && s < 600) networkErrors.push(`${s} ${resp.url()}`);
    });

    await page.goto(`${BASE}/tutor/?taskId=${TASK_ID}`, { waitUntil: "domcontentloaded" });
    await page.waitForSelector('[data-testid="inline-ask-chip"], article', { timeout: 15000 });
    // Trigger selection on the Laplace drill span via DOM evaluation.
    await page.evaluate(() => {
        const span = Array.from(document.querySelectorAll("span"))
            .find((el) => el.children.length === 0 && el.textContent?.includes("Laplace") && el.textContent.length < 400);
        if (!span) throw new Error("no Laplace selectable span found in workspace");
        const r = document.createRange();
        r.selectNodeContents(span);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(r);
        span.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    });
    await page.waitForSelector('[data-testid="inline-ask-chip"]', { state: "attached", timeout: 5000 });
    await page.click('[data-testid="inline-ask-chip"]');

    // Wait for sidekick reply (LLM round-trip + render).
    await page.waitForSelector('[data-testid="sidekick-reply"]', { timeout: 45000 });

    // Wait for citation pills to attach. If pre-fetch worked, at least 1 pill.
    await page.waitForSelector('[data-testid="citation-pill"]', { timeout: 10000 });
    const pillTitles = await page.$$eval('[data-testid="citation-pill"]', (els) =>
        els.map((e) => e.getAttribute("title") || e.textContent || "")
    );
    if (pillTitles.length < 1) throw new Error(`expected ≥1 citation pill, got ${pillTitles.length}`);
    const fiiHit = pillTitles.find((t) => /_extras\/PS\//.test(t));
    if (!fiiHit) throw new Error(`no _extras/PS/ pill found among: ${JSON.stringify(pillTitles)}`);
    console.log(`[gate] pill ok: ${fiiHit}`);

    // Click the matching pill → assert concept-drawer paints.
    const pillIndex = pillTitles.findIndex((t) => /_extras\/PS\//.test(t));
    await page.$$eval('[data-testid="citation-pill"]', (els, i) => els[i].click(), pillIndex);
    await page.waitForSelector('[data-testid="concept-drawer"]', { timeout: 10000 });
    const drawerText = await page.$eval('[data-testid="concept-drawer"]', (e) => e.textContent || "");
    if (/404|HTTP \d{3}|not found|error/i.test(drawerText)) {
        throw new Error(`drawer shows error text: ${drawerText.slice(0, 200)}`);
    }
    console.log(`[gate] drawer ok (${drawerText.length} chars)`);

    if (networkErrors.length > 0) {
        throw new Error(`network 4xx/5xx during flow:\n  ${networkErrors.join("\n  ")}`);
    }

    console.log("[gate] PASS — chip-flow citations + drawer + zero network errors");
    await browser.close();
}

main().catch((e) => {
    console.error("[gate] FAIL:", e.message);
    process.exit(1);
});
```

- [ ] **Step 3: Smoke-run the gate against the live VPS**

Run from the project root:

```bash
JARVIS_AUTH_COOKIE=$(cat tools/AUTH_TOKEN.txt) node tools/slice2-prefetch-gate.mjs
```

If `AUTH_TOKEN.txt` is absent, ask the user to provide the live `jarvis_auth` cookie value.

Expected: stdout ends with `[gate] PASS — chip-flow citations + drawer + zero network errors`.

If FAIL with "no citation pill" — pre-fetch isn't ingesting; check VPS log for `[sidekick prefetch]` stderr lines.
If FAIL with "no _extras/PS/ pill" — subject filter regressed; re-verify Task 4 normalize step.
If FAIL with "drawer shows error text" — pill click wired to wrong path; verify CitationPill onClick handler forwards the full `_extras/...` id.

- [ ] **Step 4: Commit**

```bash
git add tools/slice2-prefetch-gate.mjs
git commit -m "test(slice2.5): Playwright gate for chip-flow citation pills + drawer"
```

---

## Task 6: Backend deploy, full-suite test, live dogfood, final wrap-up commit

**Files:**
- No edits; build + ship + verify.

- [ ] **Step 1: Run the full backend test suite locally**

Run: `gradle :test --console=plain 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL` with all 685+ tests passing (was 681 pre-Task-1, +4 from this slice).

- [ ] **Step 2: Build the install distribution**

Run: `gradle :installDist -x test --console=plain`
Expected: `BUILD SUCCESSFUL`; `build/install/jarvis-kotlin/lib/jarvis-kotlin-0.1.0.jar` exists.

- [ ] **Step 3: Deploy the jar to VPS and restart**

Run:

```bash
scp -q build/install/jarvis-kotlin/lib/jarvis-kotlin-0.1.0.jar root@46.247.109.91:/opt/jarvis/jarvis-kotlin/lib/jarvis-kotlin-0.1.0.jar
ssh root@46.247.109.91 "systemctl restart jarvis && sleep 4 && systemctl is-active jarvis && curl -s http://localhost:8080/healthz"
```

Expected output:
```
active
ok
```

- [ ] **Step 4: Run the Playwright gate against the freshly-deployed backend**

Run: `JARVIS_AUTH_COOKIE=$(cat tools/AUTH_TOKEN.txt) node tools/slice2-prefetch-gate.mjs`

Expected: `[gate] PASS — ...`. If FAIL, capture the failure mode in `docs/superpowers/findings/2026-05-12-slice2-dogfood.md` under a new "Post-fix dogfood" section before unwinding.

- [ ] **Step 5: Curl the sidekick endpoint with a realistic envelope to double-check**

This is a redundant sanity check the gate already covers, but it produces a copy-pasteable confirmation in the commit message.

```bash
ssh root@46.247.109.91 "curl -sk -X POST https://corgflix.duckdns.org/api/v1/sidekick/ask \
  -H 'Content-Type: application/json' \
  -H 'Cookie: jarvis_auth=$(cat /opt/jarvis/AUTH_TOKEN.txt 2>/dev/null || echo MISSING); jarvis_session=$(curl -sk -c - -X POST https://corgflix.duckdns.org/api/v1/tutor/auto-session -H \"Cookie: jarvis_auth=...\" | grep jarvis_session | awk '{print \$7}')' \
  --data '{\"task_id\":\"01KR6K07T6PATPRR5KH1JXYF8E\",\"selection\":\"distributia Laplace\",\"user_question\":\"explain\"}'"
```

(The auth-cookie loop above is fiddly — the Playwright gate is the authoritative check. Skip Step 5 if it gives auth grief.)

- [ ] **Step 6: Update the dogfood findings doc**

Open `docs/superpowers/findings/2026-05-12-slice2-dogfood.md`. Under "Critical bugs found + fixed" add:

```markdown
### BUG-3 (shipped as Slice 2.5): chip-flow had zero citations because LLM never tool-called

Repro pre-fix: select "distributia Laplace" on Tema A drill card, click ✨ ASK → reply renders but `[data-testid="citation-pill"]` count is 0. LLM answers from baseline knowledge, never invokes search_archival.

Fix: server-side pre-fetch in `/api/v1/sidekick/ask` handler. Spec at `docs/superpowers/specs/2026-05-12-sidekick-prefetch-design.md`. Plan at `docs/superpowers/plans/2026-05-12-sidekick-prefetch.md`. Council verdict (4 mitigations bundled) at `.claude/council-cache/council-1778534960.md`.

Post-fix dogfood: gate `tools/slice2-prefetch-gate.mjs` passes on bundle `index-CZtQl9SZ.js` (unchanged) + new backend jar. ≥1 `_extras/PS/...` pill renders; concept-drawer opens with file content; zero new 4xx/5xx during flow.
```

Then update the §"Scenario results" table:

```markdown
| 5 | CitationPill renders in Sidekick | PASS | Slice 2.5 fix shipped 2026-05-12; gate `slice2-prefetch-gate.mjs` green |
| 6 | CitationPill click → ResourceRail drawer for that path | PASS | concept-drawer paints with file content, no 404 |
```

- [ ] **Step 7: Commit + push**

```bash
git add docs/superpowers/findings/2026-05-12-slice2-dogfood.md
git commit -m "$(cat <<'EOF'
docs(slice2.5): mark Scenarios 5+6 PASS after pre-fetch ship

Playwright gate (tools/slice2-prefetch-gate.mjs) green on bundle
index-CZtQl9SZ.js + new backend jar deployed today. _extras/PS/
citation pills land on chip-flow + concept-drawer opens with file
content; zero network errors during the flow.

Closes GAP-1 from dogfood findings. Council-required mitigations
verified live:
  a. SelectionQueryBuilder gate (length, anchor fallback, sanitize,
     content-char floor)
  b. try/catch on HybridRetriever.search → empty hits + stderr
  c. 200-char snippet cap in SidekickContext block
  d. \\→/ normalize at pre-fetch boundary

First Principles "your-notes-first" UX reframe remains a backlog
item — separate brainstorm.
EOF
)"
git push origin main
```

Expected: push to `origin/main` succeeds.

---

## Spec-to-task coverage map

| Spec section | Covered by |
|--------------|------------|
| §3 Architecture STEP A pre-fetch | Task 4 |
| §3 STEP B systemContext with hits | Task 3 |
| §3 STEP C JarvisToolset.chat unchanged | (no task — verified by full suite in Task 6) |
| §3 STEP D union accumulatedHits | Task 4 |
| §3 STEP E CitationExtractor.extract | Task 4 (uses existing extractor) |
| §4.1 SelectionQueryBuilder | Tasks 1+2 |
| §4.2 TaskRepo.findById subject lookup | Task 4 inline |
| §4.3 SidekickContext snippet cap | Task 3 |
| §4.4 path normalize at boundary | Task 4 |
| §4.5 CitationExtractor (no change) | Task 4 verification |
| §6 Error handling (silent-degrade) | Task 4 try/catch |
| §7 Testing | Tasks 1, 2, 3, 5 |
| §8 Acceptance with `[data-testid]` selectors | Task 5 Playwright gate (inline-ask-chip, sidekick-reply, citation-pill, concept-drawer) |
| §9 Out of scope deferrals | (none — backlog) |
