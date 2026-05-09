# Tutor Overhaul — Phase 1 (Critical Bugs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make tutor usable again. Three production-blocking bugs go away: long replies don't scroll, double-clicking a subject preset spawns one task (not three), mobile collapses cleanly to a single column.

**Architecture:** Minimal-surface fixes only — no new abstractions, no refactors. Backend gets one pre-INSERT lookup in `POST /api/v1/tasks`; frontend swaps the workspace's outer `grid` for a `flex` container so children inherit the parent's bounded height (CSS grid was breaking `min-h-0` propagation, eating the chat scroll). Phase 1 is the only place we ship a band-aid for task duplication; Phase 6 replaces it with feed-driven detection.

**Tech Stack:** Kotlin 21 + Ktor + Exposed (backend); React 19 + Tailwind v4 + Vitest + Testing Library (frontend); JUnit 5 + Ktor `testApplication` (backend tests).

**Source spec:** `docs/superpowers/specs/2026-05-10-tutor-overhaul-design.md` § Phase 1 (lines 49-72).

---

## File Structure

**Created:**
- `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` — gate-output destination for MED/LOW findings across all 8 phases.
- `src/test/kotlin/jarvis/tutor/TasksRouteIdempotentTest.kt` — backend route test for POST `/api/v1/tasks` idempotency (subject+title dedup per user).
- `tutor-web/src/__tests__/TutorWorkspace.scroll.test.tsx` — frontend test asserting the chat column scrolls internally when content overflows.

**Modified:**
- `src/main/kotlin/jarvis/web/TutorRoutes.kt:561-605` — `POST /api/v1/tasks`: before `TaskRepo.insert`, query `listForUser(userId)` + match by `subject + title`. Match → respond `200 OK` with existing `ApiTaskView`. Miss → existing `INSERT` + `201 Created` path unchanged.
- `tutor-web/src/components/TutorWorkspace.tsx:32` — replace `<div className="grid grid-cols-2 h-full min-h-0 flex-1">` with `<div className="flex h-full min-h-0 flex-1 flex-col sm:flex-row">`. Child columns get `flex-1 min-w-0 min-h-0 sm:w-1/2`.
- `tutor-web/src/components/ChatPane.tsx:89` — add `min-w-0` to the chat-pane root so flex sibling can shrink.
- `tutor-web/src/components/ChatPane.tsx:101` — replace `overflow-y-auto overflow-x-hidden` with `overflow-auto`.

**Untouched:** `TaskQuickStart.tsx` — both `200` and `201` are `r.ok` per spec, so the navigate path keeps working without changes.

---

## Task 1: Create the Phase 1 backlog file

**Files:**
- Create: `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md`

The backlog is the durable destination for MED/LOW UX-Playbook findings across every phase. Phase 1 is when it springs into existence per spec § Backlog file (line 387).

- [ ] **Step 1: Write the backlog scaffold**

Create `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` with this content:

```markdown
# Tutor Overhaul — Backlog

Created: 2026-05-10 (Phase 1).

Source-of-truth doc for MED/LOW findings produced by each phase's UX-Playbook gate. HIGH severity findings block phase advancement and are fixed in-place — they should never appear here. Phase 8's final audit rolls everything up; entries can be deleted as they ship.

Format per entry:
- `[phase] [category] [principle] [severity med|low] [finding] — [recommended action]`

## Phase 1 — Critical bugs

(populated when Phase 1 UX-Playbook gate runs)

## Phase 2 — UX foundation

(empty)

## Phase 3 — Hygiene

(empty)

## Phase 4 — Layer B §4 close

(empty)

## Phase 5 — Corpus expansion

(empty)

## Phase 6 — Task autonomy

(empty)

## Phase 7 — Layer C

(empty)

## Phase 8 — Layer D + ops + final audit

(empty)
```

- [ ] **Step 2: Commit the backlog scaffold**

```bash
git add docs/superpowers/plans/2026-05-10-tutor-overhaul-phase1.md docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md
git commit -m "$(cat <<'EOF'
Phase 1 plan + backlog scaffold

Per spec §Backlog file: backlog created at Phase 1 start, populated by
each phase's UX-Playbook gate. MED/LOW findings live here; HIGH block
advancement.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Backend — POST /api/v1/tasks idempotency by (subject, title) per user

**Files:**
- Create: `src/test/kotlin/jarvis/tutor/TasksRouteIdempotentTest.kt`
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt:561-605`

The bug: clicking the same QuickStart preset twice (because of latency or impatience) creates two `Task` rows with identical `subject` + `title`. Phase 1 band-aid: pre-INSERT lookup in the route. Phase 6 replaces this with `TaskDetector` deduping at registration time.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/jarvis/tutor/TasksRouteIdempotentTest.kt`:

```kotlin
package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TasksRouteIdempotentTest {

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seedSession(ctx: TutorContext): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        return userId to sid
    }

    @Test
    fun `second POST with same subject+title returns 200 with existing id`(@TempDir tmp: Path) =
        testApplication {
            var ctx: TutorContext? = null
            application {
                installFreshTutor(tmp)
                ctx = attributes[TutorContextKey]
            }
            startApplication()
            val (_, sid) = seedSession(ctx!!)
            val csrf = "test-csrf-12345"

            val client = createClient {
                install(HttpCookies)
                install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val payload = """
                {"subject":"PA","title":"Tema 5","deadline":"2026-05-21T00:00:00Z",
                 "repo":"user","problemPath":"tema5.pdf","rubricPath":""}
            """.trimIndent()

            val r1 = client.post("/api/v1/tasks") {
                cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
                contentType(ContentType.Application.Json); setBody(payload)
            }
            assertEquals(HttpStatusCode.Created, r1.status, "first POST must create: ${r1.bodyAsText()}")
            val firstBody = r1.bodyAsText()
            val firstId = Regex("\"id\":\"([^\"]+)\"").find(firstBody)?.groupValues?.get(1)
                ?: error("no id in first response: $firstBody")

            val r2 = client.post("/api/v1/tasks") {
                cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
                contentType(ContentType.Application.Json); setBody(payload)
            }
            assertEquals(HttpStatusCode.OK, r2.status, "second POST must dedup: ${r2.bodyAsText()}")
            assertTrue(
                r2.bodyAsText().contains("\"id\":\"$firstId\""),
                "second POST must echo first task's id; got: ${r2.bodyAsText()}",
            )
        }

    @Test
    fun `different subjects same title remain distinct`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (_, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"

        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val pa = """
            {"subject":"PA","title":"Tema 5","deadline":"2026-05-21T00:00:00Z",
             "repo":"user","problemPath":"pa.pdf","rubricPath":""}
        """.trimIndent()
        val ps = """
            {"subject":"PS","title":"Tema 5","deadline":"2026-05-21T00:00:00Z",
             "repo":"user","problemPath":"ps.pdf","rubricPath":""}
        """.trimIndent()

        val r1 = client.post("/api/v1/tasks") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(pa)
        }
        val r2 = client.post("/api/v1/tasks") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(ps)
        }

        assertEquals(HttpStatusCode.Created, r1.status)
        assertEquals(HttpStatusCode.Created, r2.status, "different subject must NOT dedup: ${r2.bodyAsText()}")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "jarvis.tutor.TasksRouteIdempotentTest" -i`

Expected: `second POST with same subject+title returns 200 with existing id` FAILS — second POST currently returns `201 Created` with a NEW id (no dedup logic in the route yet). The "different subjects" test will pass even without changes.

- [ ] **Step 3: Add the pre-INSERT lookup**

Edit `src/main/kotlin/jarvis/web/TutorRoutes.kt` — replace the body of `post("/api/v1/tasks") { ... }` (currently lines 561-605) so that, after request parsing + validation but before `TaskRepo.insert`, the handler looks for an existing match.

The current block ends with:
```kotlin
val id = jarvis.tutor.TutorTypes.ulid()
val now = Instant.now()
TaskRepo(ctx.db).insert(Task(
    id = id, userId = userId,
    subject = req.subject.trim().take(32),
    title = req.title.trim().take(256),
    deadline = deadline,
    problemRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.problemPath, sha = "pending"),
    conceptRefs = emptyList(),
    rubricRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.rubricPath.ifBlank { req.problemPath }, sha = "pending"),
    scratchpad = null, submission = null, grade = null,
    cardRefs = emptyList(),
    status = TaskStatus.ACTIVE,
    createdAt = now, updatedAt = now,
))
jarvis.tutor.StateVersion.bump()
call.respond(HttpStatusCode.Created, ApiTaskView(
    id = id, subject = req.subject, title = req.title,
    deadline = deadline.toString(), status = TaskStatus.ACTIVE.name,
))
```

Replace with:
```kotlin
val subjectTrim = req.subject.trim().take(32)
val titleTrim = req.title.trim().take(256)
// Phase-1 idempotency band-aid: spec §1.3. Replaced by TaskDetector
// dedup in Phase 6. Match key is (subject, title) per user — same
// subject+title from this user is interpreted as a re-click of an
// in-flight POST, not a deliberate second task.
val existing = TaskRepo(ctx.db).listForUser(userId)
    .firstOrNull { it.subject == subjectTrim && it.title == titleTrim }
if (existing != null) {
    call.respond(HttpStatusCode.OK, ApiTaskView(
        id = existing.id, subject = existing.subject, title = existing.title,
        deadline = existing.deadline.toString(), status = existing.status.name,
    ))
    return@csrfProtect
}
val id = jarvis.tutor.TutorTypes.ulid()
val now = Instant.now()
TaskRepo(ctx.db).insert(Task(
    id = id, userId = userId,
    subject = subjectTrim,
    title = titleTrim,
    deadline = deadline,
    problemRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.problemPath, sha = "pending"),
    conceptRefs = emptyList(),
    rubricRef = ContentRef(repo = req.repo.ifBlank { "user" }, path = req.rubricPath.ifBlank { req.problemPath }, sha = "pending"),
    scratchpad = null, submission = null, grade = null,
    cardRefs = emptyList(),
    status = TaskStatus.ACTIVE,
    createdAt = now, updatedAt = now,
))
jarvis.tutor.StateVersion.bump()
call.respond(HttpStatusCode.Created, ApiTaskView(
    id = id, subject = subjectTrim, title = titleTrim,
    deadline = deadline.toString(), status = TaskStatus.ACTIVE.name,
))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "jarvis.tutor.TasksRouteIdempotentTest" -i`

Expected: both tests PASS. Second POST returns `200 OK` with the first task's id; different-subject POST still returns `201 Created`.

- [ ] **Step 5: Run full backend test suite**

Run: `gradle :test`

Expected: 548 + 2 = **550 passed**, 0 failed (skipped count unchanged at 1).

If a different test fails: read the failure, fix in place. Do not advance with backend regressions.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/TasksRouteIdempotentTest.kt
git commit -m "$(cat <<'EOF'
Phase 1.3: POST /api/v1/tasks dedup by (subject,title) per user

Band-aid for QuickStart double-click → triple task. Pre-INSERT lookup
matches subject+title for the session user; hit returns 200 with the
existing task view, miss falls through to the existing 201 INSERT path.
Phase 6 replaces this with TaskDetector dedup at registration time.

TasksRouteIdempotentTest covers same-payload idempotency + cross-subject
non-dedup. Backend tests 548 → 550.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Frontend — scroll fix + mobile reflow

**Files:**
- Create: `tutor-web/src/__tests__/TutorWorkspace.scroll.test.tsx`
- Modify: `tutor-web/src/components/TutorWorkspace.tsx:32-40`
- Modify: `tutor-web/src/components/ChatPane.tsx:89,101`

The bug: `<div className="grid grid-cols-2 h-full min-h-0 flex-1">` propagates `min-h-0` weirdly to grid children — chat column's inner `flex-1 min-h-0 overflow-y-auto` ends up taller than the viewport, so wide KaTeX or long Laplace replies push the page rather than scrolling internally. Switching the parent to flex (with explicit `flex-col sm:flex-row`) gives the children a real bounded height, and `overflow-auto` in `ChatPane`'s scroll container (combined with `min-w-0` on its root) lets long lines scroll horizontally instead of stretching the column.

The mobile reflow lands as a free side-effect of the same change: `flex-col sm:flex-row` stacks PDF on top of chat below the `sm` breakpoint. The sidebar is already `hidden sm:flex` so mobile = single column.

- [ ] **Step 1: Write the failing test**

Create `tutor-web/src/__tests__/TutorWorkspace.scroll.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/chat")) {
      // 200-line reply forces overflow regardless of viewport size.
      const longReply = Array.from({ length: 200 }, (_, i) => `line ${i}: laplace transform of f(t) = ...`).join("\n");
      return new Response(JSON.stringify({ reply: longReply }), {
        status: 200, headers: { "content-type": "application/json" },
      });
    }
    return new Response("{}", { status: 200 });
  }));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("workspace outer container is flex (not grid) so flex children get bounded height", () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  // The two-column container is the parent of the chat-pane testid.
  const chat = screen.getByTestId("chat-pane");
  const twoColParent = chat.parentElement!;
  expect(twoColParent.className).toMatch(/\bflex\b/);
  expect(twoColParent.className).not.toMatch(/\bgrid\b/);
  // Mobile-stacks-desktop-row contract: flex-col sm:flex-row.
  expect(twoColParent.className).toMatch(/flex-col/);
  expect(twoColParent.className).toMatch(/sm:flex-row/);
});

test("chat pane root carries min-w-0 so flex sibling can shrink", () => {
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  const chat = screen.getByTestId("chat-pane");
  expect(chat.className).toMatch(/\bmin-w-0\b/);
});

test("chat scroll container uses overflow-auto for KaTeX horizontal overflow", async () => {
  const { container } = render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  fireEvent.change(screen.getByPlaceholderText(/message/i), { target: { value: "explain laplace" } });
  fireEvent.click(screen.getByRole("button", { name: /send/i }));
  await waitFor(() => expect(screen.getAllByText(/^line 0:/).length).toBeGreaterThan(0));

  // The scroll container is the flex-1 child of chat-pane that holds the messages.
  const chat = screen.getByTestId("chat-pane");
  const scrollContainer = Array.from(chat.querySelectorAll("div")).find(d =>
    /\boverflow-auto\b/.test(d.className) && /\bflex-1\b/.test(d.className),
  );
  expect(scrollContainer, "expected an overflow-auto + flex-1 scroll container under chat-pane").toBeDefined();
  // Negative-assert the old broken classes don't reappear.
  expect(scrollContainer!.className).not.toMatch(/overflow-y-auto/);
  expect(scrollContainer!.className).not.toMatch(/overflow-x-hidden/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `tutor-web/`): `npm test -- --run TutorWorkspace.scroll`

Expected: all three test cases FAIL — current `TutorWorkspace.tsx:32` has `grid grid-cols-2`, current `ChatPane.tsx:89` has no `min-w-0`, current `ChatPane.tsx:101` uses `overflow-y-auto overflow-x-hidden`.

- [ ] **Step 3: Edit TutorWorkspace.tsx — swap grid for flex**

Edit `tutor-web/src/components/TutorWorkspace.tsx:32-40`. Replace:

```tsx
      <div className="grid grid-cols-2 h-full min-h-0 flex-1">
        <div className="flex flex-col h-full min-h-0 border-r-4 border-black">
          <div className="flex-1 min-h-0 overflow-hidden">
            <PdfPane url={pdfUrl} />
          </div>
          <Scratchpad value={scratch} onChange={setScratch} />
        </div>
        <ChatPane taskId={taskId} onScratchpadInsert={appendToScratchpad} />
      </div>
```

with:

```tsx
      <div className="flex h-full min-h-0 flex-1 flex-col sm:flex-row">
        <div className="flex flex-col h-full min-h-0 flex-1 min-w-0 sm:w-1/2 border-r-4 border-black">
          <div className="flex-1 min-h-0 overflow-hidden">
            <PdfPane url={pdfUrl} />
          </div>
          <Scratchpad value={scratch} onChange={setScratch} />
        </div>
        <div className="flex-1 min-w-0 min-h-0 sm:w-1/2 h-full">
          <ChatPane taskId={taskId} onScratchpadInsert={appendToScratchpad} />
        </div>
      </div>
```

The chat column now sits inside its own `flex-1 min-w-0 min-h-0 sm:w-1/2 h-full` wrapper because the spec wants every child column carrying that class set; wrapping ChatPane keeps ChatPane's own internals untouched apart from the changes in step 4.

- [ ] **Step 4: Edit ChatPane.tsx — root min-w-0 + scroll container overflow-auto**

Edit `tutor-web/src/components/ChatPane.tsx:89` — change root `<div>`'s class:

From:
```tsx
    <div data-testid="chat-pane" className="h-full flex flex-col bg-white font-mono">
```

To:
```tsx
    <div data-testid="chat-pane" className="h-full flex flex-col bg-white font-mono min-w-0">
```

Edit `tutor-web/src/components/ChatPane.tsx:101` — change scroll container's class:

From:
```tsx
      <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden p-4 space-y-3">
```

To:
```tsx
      <div className="flex-1 min-h-0 overflow-auto p-4 space-y-3">
```

- [ ] **Step 5: Run scroll test to verify it passes**

Run (from `tutor-web/`): `npm test -- --run TutorWorkspace.scroll`

Expected: all three tests PASS.

- [ ] **Step 6: Run full frontend test suite**

Run (from `tutor-web/`): `npm test -- --run`

Expected: 81 + 3 = **84 passed**, 0 failed. The existing `TutorWorkspace.test.tsx` cases ("renders left PDF pane, right chat pane, and scratchpad", INSERT → SCRATCHPAD, send button) MUST still pass — they assert testids that we haven't touched.

If "renders left PDF pane..." fails: the test queries `getByTestId("pdf-pane")` and `getByTestId("chat-pane")`; a wrapper div around `<ChatPane>` doesn't change either testid since they live on inner elements. Re-confirm by reading the failure.

- [ ] **Step 7: Commit**

```bash
git add tutor-web/src/components/TutorWorkspace.tsx tutor-web/src/components/ChatPane.tsx tutor-web/src/__tests__/TutorWorkspace.scroll.test.tsx
git commit -m "$(cat <<'EOF'
Phase 1.1+1.2: workspace scroll + mobile reflow

TutorWorkspace's outer container goes from grid grid-cols-2 to
flex flex-col sm:flex-row. CSS grid was eating min-h-0 propagation,
so long replies pushed the page instead of scrolling within the chat
column.

Side-effect closes 1.2 (mobile reflow): below sm breakpoint the
flex-col stack puts PDF above chat; sidebar stays hidden sm:flex so
mobile = single column.

ChatPane root gets min-w-0 (lets the flex sibling shrink) and the
inner scroll container goes from overflow-y-auto+overflow-x-hidden
to overflow-auto so wide KaTeX scrolls horizontally instead of
stretching the column.

3 new tests in TutorWorkspace.scroll.test.tsx assert the contract
(flex-not-grid; min-w-0 on chat root; overflow-auto on scroll
container). Frontend tests 81 → 84.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Code Gate (Gate 1 of 4)

Per spec § Per-phase gate model.

- [ ] **Step 1: Push to remote and confirm CI green**

Run:
```bash
git push origin main
```

Then check CI:
```bash
gh run list --branch main --limit 3
```

Wait for the most recent run to complete. Pass criteria:
- `backend` job (gradle :test on temurin 21): green
- `frontend` job (vitest): green
- `daemon` job (cargo test on linux w/ libxdo+libdbus): green

If any job fails: read its logs (`gh run view <id> --log-failed`), fix in-place, push, re-run gate. Don't advance.

---

## Task 5: Live Gate (Gate 2 of 4)

- [ ] **Step 1: Deploy via tools/deploy.sh**

Run:
```bash
& "C:\Program Files\Git\bin\bash.exe" tools/deploy.sh
```

Expected: tail-of-log output ends without `ERROR` lines; the script's own `echo "[deploy] stop service, rotate -prev, scp dist, restart"` flow completes.

- [ ] **Step 2: Verify health endpoint**

Run:
```bash
curl https://corgflix.duckdns.org/healthz
```

Expected: `ok` (single word, no newline).

- [ ] **Step 3: Verify bundle hash matches local build**

Compute local bundle hash:
```bash
sha256sum tutor-web/dist/assets/index-*.js | head -1
```

Fetch deployed bundle hash:
```bash
curl -sS https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[a-z0-9]+\.js' | head -1
```

Expected: the local hash filename's prefix (`index-<hash>`) matches the one referenced by the deployed `/tutor/` HTML.

If mismatch: deploy script may have re-served the previous bundle; re-run `tools/deploy.sh` and re-verify before advancing.

---

## Task 6: Playwright Gate (Gate 3 of 4)

Per spec § Phase 1 Gates: "Playwright agent specifically reproduces today's failure modes (long laplace reply scroll, 3× preset click) and confirms fix."

- [ ] **Step 1: Spawn the Playwright test agent**

Use the Agent tool with subagent_type: `general-purpose` and the Playwright MCP browser tools. Prompt it with this checklist (copy verbatim):

```
You are the Phase 1 Playwright gate. Use the playwright MCP tools to drive
https://corgflix.duckdns.org/tutor/ and re-run THE EXACT failure modes Phase 1
claimed to fix. Report PASS/FAIL per scenario in structured form.

Scenarios:

1. SCROLL — long laplace reply scrolls inside the chat column, not the page.
   - browser_navigate to https://corgflix.duckdns.org/tutor/
   - If a QuickStart preset row is shown, click "PA" to create a task, then
     wait for workspace to load.
   - browser_type into the chat input: "give me a 200-line worked example of
     applying the laplace transform to f(t) = t^2 e^(-3t), with steps".
   - browser_click the send button.
   - browser_wait_for the reply (text containing "laplace" or "$").
   - browser_evaluate: check that document.documentElement.scrollHeight ===
     document.documentElement.clientHeight (page itself does NOT overflow),
     AND that the chat scroll container's scrollHeight > clientHeight (inner
     scroll is engaged). PASS if both hold.

2. DUP TASK — clicking PA preset 3× creates exactly one task.
   - browser_navigate to https://corgflix.duckdns.org/tutor/tasks (force
     fresh task list view).
   - Count rows currently visible.
   - browser_navigate back to /tutor/, click PA preset 3× in <500ms each.
   - browser_navigate to /tutor/tasks again, count rows.
   - PASS if exactly 1 new row appeared. FAIL if 2 or 3.

3. MOBILE REFLOW — at 375px viewport the workspace stacks single-column.
   - browser_resize to 375x812.
   - browser_navigate to https://corgflix.duckdns.org/tutor/ + click any
     preset.
   - browser_evaluate: getComputedStyle of the workspace's outer two-col
     container; confirm flex-direction is "column" (not "row").
   - browser_take_screenshot mobile-stacked.png.
   - PASS if PDF appears above chat with no horizontal page scroll
     (document.documentElement.scrollWidth === clientWidth).

Output structure (one block per scenario):
[scenario] [pass|fail] [evidence: e.g. scrollHeight values, row count, screenshot path]

If any scenario fails: report the exact tutor surface state (URL, viewport,
DOM snippet of the failing element) and stop. Don't try to fix — just
report.
```

- [ ] **Step 2: Read the agent's report**

If all three scenarios PASS: advance to Gate 4.

If any FAILS: do not advance. Add a note in `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` under Phase 1 with the failure detail, fix in-place (re-run Tasks 2 or 3 as needed), then re-run this Gate.

---

## Task 7: UX-Playbook Gate (Gate 4 of 4)

Per spec § Per-phase gate model: spawn an audit agent. Inputs: SO `wiki/architecture/UX Playbook.md` + `Design Principles.md` (cross-domain arbiter) + the list of files changed in Phase 1. Audit only the surfaces touched.

- [ ] **Step 1: Spawn the UX-Playbook audit agent**

Use the Agent tool with `subagent_type: general-purpose`. Prompt:

```
You are the UX-Playbook gate for jarvis-kotlin Phase 1. Read these inputs:

1. C:\Users\User\Desktop\SO\os-study-guide\wiki\architecture\UX Playbook.md
2. C:\Users\User\Desktop\SO\os-study-guide\wiki\architecture\Design Principles.md

Then audit the surfaces touched in Phase 1:
- C:\Users\User\jarvis-kotlin\tutor-web\src\components\TutorWorkspace.tsx
- C:\Users\User\jarvis-kotlin\tutor-web\src\components\ChatPane.tsx
- The user-visible behavior of POST /api/v1/tasks (the dedup is invisible
  to a happy-path user; only audit if it surfaces an error or status
  inconsistency in the UI).

Audit ONLY these surfaces — do NOT score broader tutor screens (sidebar,
tasks page, settings). Phase 2 will tokenize the design system; Phase 1's
job is purely to fix the 3 critical bugs without making any visible
regression.

CRITICAL CONSTRAINT: Do NOT nag about the PS HW deadline (2026-05-21) or
finals proximity. The user has dismissed deadline framing multiple times
via prior councils. Treat this as design feedback only. Authorization for
this work is granted by the user; that question is settled. If your verdict
relitigates "should you be doing this instead of studying" the verdict
will be discarded — focus all energy on whether the design is correct.

Output format (one row per finding):
[category] [principle] [pass|fail|n/a] [severity high|med|low] [finding] [recommended action]

Severity rules per spec § Per-phase gate model:
- HIGH: blocks Phase 2 advancement. Fix in-place, then re-run this gate.
- MED/LOW: appended to docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md
  under "## Phase 1 — Critical bugs", deferred to a later phase.

End with a summary line: TOTAL: X high, Y med, Z low.
```

- [ ] **Step 2: Triage findings**

For each finding the agent returns:

- **HIGH**: do NOT advance. Fix in-place (might require revisiting Task 3). Re-run Tasks 4-7 (gates 1-4) until clean.
- **MED / LOW**: append to `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` under `## Phase 1 — Critical bugs`. Format per backlog spec: `[phase] [category] [principle] [severity] [finding] — [recommended action]`.

- [ ] **Step 3: Commit the populated backlog (if MED/LOW found)**

If the backlog file changed:
```bash
git add docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md
git commit -m "$(cat <<'EOF'
Phase 1 backlog: append UX-Playbook MED/LOW findings

Per spec § Backlog file: HIGH findings were fixed in-place; MED/LOW
deferred here for the next phase that touches the same surface.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push origin main
```

If no MED/LOW: skip this step; nothing to commit.

---

## Phase 1 Definition of Done

Tick all before declaring Phase 1 complete and moving to Phase 2 spec → plan → execute:

- [ ] Backend: `gradle :test` reports 550 passed, 0 failed locally.
- [ ] Frontend: `npm test -- --run` from `tutor-web/` reports 84 passed, 0 failed.
- [ ] Daemon: untouched in Phase 1, but `cargo test` from `daemon/` still reports 16 passed.
- [ ] CI green on `main` for the most recent push.
- [ ] `curl https://corgflix.duckdns.org/healthz` returns `ok`.
- [ ] Playwright agent reports PASS on all 3 scenarios (scroll, dup task, mobile reflow).
- [ ] UX-Playbook agent reports zero HIGH findings; any MED/LOW are in the backlog file.
- [ ] Backlog file `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` exists and has been committed (Task 1 minimum; populated if Task 7 produced findings).
- [ ] No new commits since last gate run (the gate-test artifacts are committed before declaring done).

---

## Out of scope (do NOT do this in Phase 1)

These will tempt the engineer; spec is explicit they belong elsewhere:

- **Design tokens / typography / focus rings / motion / a11y**: Phase 2.
- **TaskDetector abstraction / replacing the band-aid dedup with feed-driven detection**: Phase 6.
- **Server-side scratchpad persistence** (touching `Scratchpad`): Phase 3.
- **Sidebar + KaTeX + chips visual polish**: those are already shipped (commits 780fe2c / 612bf1e / 39cb59f); Phase 2 tokenizes them, doesn't redo them.
- **Knowledge gap server-persistence**: Phase 4.
- **Inline reference popups / Knowledge Ledger drawer**: Phase 7.

If you find yourself reaching for any of the above, stop — that's a Phase-N task pretending to be a Phase-1 task. Note the temptation in the backlog file and move on.
