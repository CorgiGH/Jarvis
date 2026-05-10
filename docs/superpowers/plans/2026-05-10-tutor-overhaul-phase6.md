# Tutor Overhaul — Phase 6 (Task Autonomy) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** Tasks ARE what the system knows is due, not what the user typed. Substrate for feed-driven detection (`TaskDetector` aggregator over multiple sources) + auto-corpus attachment (`tasks.material_paths` populated at INSERT via HybridRetriever) + active-task dashboard (`ActiveTaskDashboard` replaces `TaskQuickStart` with composite-ranked list).

**Architecture:** Kotlin `TaskDetector` interface aggregates multiple `TaskSource` implementations. New `DetectedTaskRepo` tracks `(sourceId, externalId)` → `taskId` so re-runs upsert. `tasks` table gains `material_paths TEXT NULL` column populated at task creation by `HybridRetriever.search(title, k=5)`. Manual trigger route `POST /api/v1/task-detect/run` so the user / cron can fire it; LLM-mediated cron-via-SKILL.md deferred (existing CronRunner uses tool_use round-trips, doesn't fit a pure-Kotlin batch job — backlog item). Frontend `ActiveTaskDashboard` lists tasks ranked by `(deadline_urgency × 0.5) + (weight × 0.2) + (readiness × 0.3)`; replaces `TaskQuickStart` as default landing.

**Tech Stack:** Same as Phase 5 backend (Kotlin + Ktor + Exposed). No new deps.

**Source spec:** `docs/superpowers/specs/2026-05-10-tutor-overhaul-design.md` § Phase 6 (lines 230-291).

**Scope cuts (deferred to Phase 8 / backlog):**
- 6.4 cron-via-SKILL.md wiring — existing CronRunner is LLM-tool-driven; pure-Kotlin batch jobs need a new path. Ship the manual-trigger route now; cron later.
- 6.6 schedule.json integration — read-side, doesn't need Phase 6 surface change.

---

## File Structure

**Created (backend):**
- `src/main/kotlin/jarvis/tutor/taskdetect/TaskDetector.kt` — interface + `DetectedTask` data class + `TaskDetectorAggregator`.
- `src/main/kotlin/jarvis/tutor/taskdetect/DetectedTaskRepo.kt` — table + repo.
- `src/main/kotlin/jarvis/tutor/taskdetect/ManualSource.kt` — reads existing `TasksTable` rows, surfaces them as `DetectedTask` (preserves manual entry as a source).
- `src/main/kotlin/jarvis/tutor/taskdetect/FiimaterialsSource.kt` — walks `_extras/<subject>/extracted/<sha8>/meta.json`, surfaces them as low-priority "reading" tasks.
- `src/test/kotlin/jarvis/tutor/taskdetect/DetectedTaskRepoTest.kt`
- `src/test/kotlin/jarvis/tutor/taskdetect/FiimaterialsSourceTest.kt`
- `src/test/kotlin/jarvis/tutor/taskdetect/TaskDetectorAggregatorTest.kt`

**Created (frontend):**
- `tutor-web/src/components/ActiveTaskDashboard.tsx` — replaces `TaskQuickStart` as default landing.
- `tutor-web/src/lib/taskRanking.ts` — pure scoring functions (testable in isolation).
- `tutor-web/src/__tests__/taskRanking.test.ts`
- `tutor-web/src/__tests__/ActiveTaskDashboard.test.tsx`
- `tutor-web/src/__tests__/referenceMaterials.test.tsx` — TutorWorkspace renders Reference materials when `materialPaths` non-empty.

**Modified (backend):**
- `src/main/kotlin/jarvis/tutor/Tasks.kt` — add `materialPaths: List<String>` field + `material_paths` TEXT NULL column.
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — POST `/api/v1/tasks` populates `materialPaths` via HybridRetriever; GET `/api/v1/tasks/{id}` returns it; new `POST /api/v1/task-detect/run` calls aggregator.

**Modified (frontend):**
- `tutor-web/src/App.tsx` — `<TaskQuickStart />` → `<ActiveTaskDashboard />`. (`TaskQuickStart` stays in source for "+ Manual entry" modal.)
- `tutor-web/src/components/TutorWorkspace.tsx` — render Reference materials rail above PdfPane when `materialPaths` array non-empty.

---

## Task 1: Phase 6 plan committed

Commit only.

```bash
git add docs/superpowers/plans/2026-05-10-tutor-overhaul-phase6.md
git commit -m "Phase 6 plan: Task autonomy (detector + auto-corpus + dashboard)

Per spec § Phase 6. Phase 5 shipped + gates passed at 7ea02ad.
Phase 6: TaskDetector aggregator over Manual + Fiimaterials sources,
DetectedTaskRepo for upsert dedup, tasks.material_paths auto-attached
via HybridRetriever at INSERT, ActiveTaskDashboard composite-ranked
landing replacing TaskQuickStart. Cron-via-SKILL.md + assignments.jsonl
integration deferred (backlog).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: §6.1 — `TaskDetector` interface + aggregator + `DetectedTaskRepo`

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/taskdetect/TaskDetector.kt`
- Create: `src/main/kotlin/jarvis/tutor/taskdetect/DetectedTaskRepo.kt`
- Create: `src/test/kotlin/jarvis/tutor/taskdetect/DetectedTaskRepoTest.kt`

### `TaskDetector.kt`

```kotlin
package jarvis.tutor.taskdetect

import java.time.Instant

/**
 * Phase 6 task source. Each source produces a list of [DetectedTask]s
 * the aggregator dedups + upserts into TasksTable + DetectedTaskMappingTable.
 */
interface TaskDetector {
    val sourceId: String
    suspend fun discover(): List<DetectedTask>
}

data class DetectedTask(
    val sourceId: String,
    val externalId: String,
    val subject: String,
    val title: String,
    val deadline: Instant,
    val problemPath: String? = null,
    val sourceUrl: String? = null,
    val rawMetadata: Map<String, String> = emptyMap(),
)

/**
 * Aggregates multiple [TaskDetector] instances; calls discover() on each;
 * dedupes detected tasks by (sourceId, externalId); returns a flat list.
 * Per-source failures are logged + skipped (one bad source doesn't kill
 * the cycle).
 */
class TaskDetectorAggregator(private val sources: List<TaskDetector>) {
    suspend fun discoverAll(): List<DetectedTask> {
        val seen = mutableSetOf<Pair<String, String>>()
        val out = mutableListOf<DetectedTask>()
        for (src in sources) {
            val tasks = try {
                src.discover()
            } catch (e: Exception) {
                System.err.println("[detector] source ${src.sourceId} failed: ${e.message?.take(160)}")
                continue
            }
            for (t in tasks) {
                val key = t.sourceId to t.externalId
                if (key in seen) continue
                seen += key
                out += t
            }
        }
        return out
    }
}
```

### `DetectedTaskRepo.kt`

```kotlin
package jarvis.tutor.taskdetect

import jarvis.tutor.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

object DetectedTaskMappingTable : Table("detected_task_mapping") {
    val sourceId = varchar("source_id", 64)
    val externalId = varchar("external_id", 256)
    val taskId = varchar("task_id", 26)
    val firstSeenAt = timestamp("first_seen_at")
    val lastSeenAt = timestamp("last_seen_at")
    val userId = varchar("user_id", 26).references(UsersTable.id)
    override val primaryKey = PrimaryKey(sourceId, externalId)
}

class DetectedTaskRepo(private val db: Database) {
    fun findExisting(sourceId: String, externalId: String): String? = transaction(db) {
        DetectedTaskMappingTable.selectAll()
            .where { (DetectedTaskMappingTable.sourceId eq sourceId) and (DetectedTaskMappingTable.externalId eq externalId) }
            .singleOrNull()
            ?.get(DetectedTaskMappingTable.taskId)
    }

    fun upsertMapping(sourceId: String, externalId: String, taskId: String, userId: String,
                      now: Instant = Instant.now()): String = transaction(db) {
        val existing = DetectedTaskMappingTable.selectAll()
            .where { (DetectedTaskMappingTable.sourceId eq sourceId) and (DetectedTaskMappingTable.externalId eq externalId) }
            .singleOrNull()
        if (existing != null) {
            DetectedTaskMappingTable.update({
                (DetectedTaskMappingTable.sourceId eq sourceId) and (DetectedTaskMappingTable.externalId eq externalId)
            }) {
                it[lastSeenAt] = now
            }
            return@transaction existing[DetectedTaskMappingTable.taskId]
        }
        DetectedTaskMappingTable.insert {
            it[DetectedTaskMappingTable.sourceId] = sourceId
            it[DetectedTaskMappingTable.externalId] = externalId
            it[DetectedTaskMappingTable.taskId] = taskId
            it[DetectedTaskMappingTable.userId] = userId
            it[firstSeenAt] = now
            it[lastSeenAt] = now
        }
        taskId
    }
}
```

Add `DetectedTaskMappingTable` to `installTutorContext`'s `SchemaUtils.createMissingTablesAndColumns(...)` list.

### Test

```kotlin
package jarvis.tutor.taskdetect

import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DetectedTaskRepoTest {
    private fun freshRepo(tmp: Path): Pair<DetectedTaskRepo, String> {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) { SchemaUtils.createMissingTablesAndColumns(UsersTable, DetectedTaskMappingTable) }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return DetectedTaskRepo(db) to userId
    }

    @Test
    fun `findExisting returns null when no mapping`(@TempDir tmp: Path) {
        val (repo, _) = freshRepo(tmp)
        assertNull(repo.findExisting("src1", "ext1"))
    }

    @Test
    fun `upsertMapping inserts then returns same taskId on re-upsert`(@TempDir tmp: Path) {
        val (repo, userId) = freshRepo(tmp)
        val taskId = TutorTypes.ulid()
        val a = repo.upsertMapping("src1", "ext1", taskId, userId)
        assertEquals(taskId, a)
        // Re-upsert with a DIFFERENT taskId should still return the original
        // (mapping is idempotent on (source, external)).
        val b = repo.upsertMapping("src1", "ext1", "OTHER-ID", userId)
        assertEquals(taskId, b)
        assertEquals(taskId, repo.findExisting("src1", "ext1"))
    }

    @Test
    fun `aggregator dedups across sources by (sourceId, externalId)`() = runBlocking {
        val src = object : TaskDetector {
            override val sourceId = "src-test"
            override suspend fun discover(): List<DetectedTask> = listOf(
                DetectedTask("src-test", "e1", "PA", "Tema A", Instant.now()),
                DetectedTask("src-test", "e1", "PA", "Tema A duplicate", Instant.now()),
                DetectedTask("src-test", "e2", "PS", "Tema B", Instant.now()),
            )
        }
        val agg = TaskDetectorAggregator(listOf(src))
        val out = agg.discoverAll()
        assertEquals(2, out.size)
        assertEquals(setOf("e1", "e2"), out.map { it.externalId }.toSet())
    }
}
```

Backend tests 566 → 569.

---

## Task 3: §6.2 — `ManualSource` + `FiimaterialsSource`

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/taskdetect/ManualSource.kt`
- Create: `src/main/kotlin/jarvis/tutor/taskdetect/FiimaterialsSource.kt`
- Create: `src/test/kotlin/jarvis/tutor/taskdetect/FiimaterialsSourceTest.kt`

### `ManualSource.kt`

```kotlin
package jarvis.tutor.taskdetect

import jarvis.tutor.TaskRepo
import org.jetbrains.exposed.sql.Database

/**
 * Surfaces existing TasksTable rows back through the detector pipeline
 * so the aggregator preserves manual-entry tasks. ExternalId = task.id.
 */
class ManualSource(
    private val db: Database,
    private val userId: String,
) : TaskDetector {
    override val sourceId = "manual"
    override suspend fun discover(): List<DetectedTask> {
        val tasks = TaskRepo(db).listForUser(userId)
        return tasks.map { t ->
            DetectedTask(
                sourceId = sourceId,
                externalId = t.id,
                subject = t.subject,
                title = t.title,
                deadline = t.deadline,
                problemPath = t.problemRef.path.takeIf { it.isNotBlank() },
                sourceUrl = null,
                rawMetadata = mapOf("status" to t.status.name),
            )
        }
    }
}
```

### `FiimaterialsSource.kt`

```kotlin
package jarvis.tutor.taskdetect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.streams.toList

/**
 * Reads the Phase 5 crawler's output (_extras/<subject>/extracted/<sha8>/meta.json)
 * and surfaces fetched PDFs as low-priority "reading" tasks. Deadlines
 * are placeholders (now + 14 days) — the real "due" signal comes from
 * other sources; this one just makes the corpus visible in the dashboard.
 */
class FiimaterialsSource(private val extrasRoot: Path) : TaskDetector {
    override val sourceId = "fiimaterials"

    @Serializable
    private data class Meta(
        val sourceUrl: String? = null,
        val fetchedAt: String? = null,
        val sha256: String? = null,
        val subject: String? = null,
        val kind: String? = null,
    )

    override suspend fun discover(): List<DetectedTask> {
        if (!extrasRoot.exists()) return emptyList()
        val now = Instant.now()
        val out = mutableListOf<DetectedTask>()
        val json = Json { ignoreUnknownKeys = true }
        Files.walk(extrasRoot).use { stream ->
            for (p in stream.toList()) {
                if (p.name != "meta.json") continue
                val txt = try { Files.readString(p) } catch (_: Exception) { continue }
                val meta = try { json.decodeFromString(Meta.serializer(), txt) } catch (_: Exception) { continue }
                val sha = meta.sha256 ?: continue
                val subject = meta.subject ?: "OTHER"
                val pdfFile = Files.list(p.parent).use { stream2 ->
                    stream2.filter { it.name != "meta.json" && it.name.endsWith(".pdf") }
                        .findFirst().orElse(null)
                } ?: continue
                val title = pdfFile.name.removeSuffix(".pdf").replace('_', ' ').take(256)
                out += DetectedTask(
                    sourceId = sourceId,
                    externalId = sha,
                    subject = subject,
                    title = title,
                    deadline = now.plus(14, ChronoUnit.DAYS),
                    problemPath = pdfFile.toString(),
                    sourceUrl = meta.sourceUrl,
                    rawMetadata = mapOf(
                        "kind" to (meta.kind ?: "other"),
                        "sha256" to sha,
                    ),
                )
            }
        }
        return out
    }
}
```

### Test

```kotlin
package jarvis.tutor.taskdetect

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiimaterialsSourceTest {
    @Test
    fun `discover returns empty when root missing`() = runBlocking {
        val src = FiimaterialsSource(java.nio.file.Path.of("/nonexistent/path/abc"))
        assertTrue(src.discover().isEmpty())
    }

    @Test
    fun `discover walks meta.json sidecars + maps to DetectedTask`(@TempDir tmp: Path) = runBlocking {
        val pa = tmp.resolve("PA").resolve("extracted").resolve("abcd1234")
        Files.createDirectories(pa)
        Files.writeString(pa.resolve("Subiect_partial_2021.pdf"), "%PDF-fake")
        Files.writeString(pa.resolve("meta.json"), """
            {"sourceUrl":"https://x/y.pdf","fetchedAt":"2026-05-10T00:00:00Z",
             "sha256":"abcd1234","subject":"PA","kind":"exam"}
        """.trimIndent())

        val src = FiimaterialsSource(tmp)
        val tasks = src.discover()
        assertEquals(1, tasks.size)
        val t = tasks[0]
        assertEquals("fiimaterials", t.sourceId)
        assertEquals("abcd1234", t.externalId)
        assertEquals("PA", t.subject)
        assertEquals("Subiect partial 2021", t.title)
        assertEquals("exam", t.rawMetadata["kind"])
    }
}
```

Backend tests 569 → 571.

---

## Task 4: §6.3a — `tasks.material_paths` column + auto-attach at INSERT + GET surfaces it

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/Tasks.kt` — add `materialPaths` field + column.
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` — populate at POST; surface at GET.

### Tasks.kt

Add to `TasksTable`:
```kotlin
val materialPaths = text("material_paths").nullable()  // JSON array of relative paths
```

Add to `Task` data class:
```kotlin
val materialPaths: List<String> = emptyList(),
```

Extend `insert(t: Task)`:
```kotlin
it[materialPaths] = if (t.materialPaths.isEmpty()) null
    else TutorTypes.tutorJson.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer()), t.materialPaths)
```

Extend `toTask()`:
```kotlin
materialPaths = this[TasksTable.materialPaths]?.let { json ->
    runCatching {
        TutorTypes.tutorJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer()), json)
    }.getOrDefault(emptyList())
} ?: emptyList(),
```

### TutorRoutes.kt

In `POST /api/v1/tasks` block, after computing `subjectTrim` + `titleTrim` but before constructing `Task`:

```kotlin
val materialPaths: List<String> = try {
    kotlinx.coroutines.runBlocking {
        jarvis.HybridRetriever.search(titleTrim, k = 5, semanticEmbed = null)
    }.map { it.id }.distinct().take(5)
} catch (e: Exception) { emptyList() }
```

Pass `materialPaths = materialPaths` into the `Task(...)` constructor.

Add a GET route to surface a single task's view:

```kotlin
get("/api/v1/tasks/{id}") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
    val taskId = call.parameters["id"]
        ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@get }
    val task = TaskRepo(ctx.db).findById(taskId)
    if (task == null || task.userId != userId) {
        call.respond(HttpStatusCode.NotFound, "task not found"); return@get
    }
    call.respond(HttpStatusCode.OK, ApiTaskDetailView(
        id = task.id, subject = task.subject, title = task.title,
        deadline = task.deadline.toString(), status = task.status.name,
        materialPaths = task.materialPaths,
    ))
}
```

Add type:
```kotlin
@Serializable
private data class ApiTaskDetailView(
    val id: String, val subject: String, val title: String,
    val deadline: String, val status: String,
    val materialPaths: List<String>,
)
```

### Test

Append to `TasksRouteIdempotentTest.kt` (the closest existing tasks-routes test):

```kotlin
@Test
fun `POST tasks populates materialPaths via HybridRetriever (empty when no corpus)`(@TempDir tmp: Path) =
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
            {"subject":"PA","title":"Tema 99","deadline":"2026-05-21T00:00:00Z",
             "repo":"user","problemPath":"x.pdf","rubricPath":""}
        """.trimIndent()
        val r = client.post("/api/v1/tasks") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, r.status)
        // Extract id then GET it.
        val id = Regex("\"id\":\"([^\"]+)\"").find(r.bodyAsText())!!.groupValues[1]
        val r2 = client.get("/api/v1/tasks/$id") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r2.status, r2.bodyAsText())
        // materialPaths field present (may be empty array under test corpus).
        assertTrue(r2.bodyAsText().contains("\"materialPaths\""))
    }
```

Backend tests 571 → 572.

---

## Task 5: §6.3b — Frontend Reference materials rail

**Files:**
- Modify: `tutor-web/src/components/TutorWorkspace.tsx`
- Create: `tutor-web/src/__tests__/referenceMaterials.test.tsx`

In `TutorWorkspace.tsx`, fetch the task detail (the new `GET /api/v1/tasks/{id}`) on `taskId` mount; if `materialPaths.length > 0`, render a small rail above the PDF column.

Add state:
```tsx
const [materialPaths, setMaterialPaths] = useState<string[]>([]);
useEffect(() => {
  let cancelled = false;
  jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}`)
    .then(r => r.ok ? r.json() : null)
    .then((d: { materialPaths?: string[] } | null) => {
      if (!cancelled && d?.materialPaths) setMaterialPaths(d.materialPaths);
    })
    .catch(() => {});
  return () => { cancelled = true; };
}, [taskId]);
```

Render between the deduped-banner and the two-col container:
```tsx
{materialPaths.length > 0 && (
  <div data-testid="reference-materials"
       className="border-b-4 border-border-strong bg-accent-soft px-4 py-2 font-mono text-xs">
    <div className="font-bold tracking-widest mb-1">REFERENCE MATERIALS ({materialPaths.length})</div>
    <ul role="list" className="space-y-0.5">
      {materialPaths.map((p, i) => (
        <li key={`${p}-${i}`} className="text-page-fg/80 truncate">{p}</li>
      ))}
    </ul>
  </div>
)}
```

### Test

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("reference materials rail renders when GET /tasks/{id} returns paths", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks/T1")) {
      return new Response(JSON.stringify({
        id: "T1", subject: "PA", title: "Tema 5",
        deadline: new Date().toISOString(), status: "ACTIVE",
        materialPaths: ["_extras/PA/study_guide/laplace.pdf", "_extras/PA/seminars/sem3.pdf"],
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>);
  const rail = await waitFor(() => screen.getByTestId("reference-materials"));
  expect(rail.textContent).toMatch(/REFERENCE MATERIALS \(2\)/);
  expect(rail.textContent).toMatch(/laplace\.pdf/);
  expect(rail.textContent).toMatch(/sem3\.pdf/);
});

test("reference materials rail absent when paths empty", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.endsWith("/api/v1/tasks/T2")) {
      return new Response(JSON.stringify({ materialPaths: [] }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T2" /></MemoryRouter>);
  // Allow effect microtask to flush.
  await new Promise(r => setTimeout(r, 0));
  expect(screen.queryByTestId("reference-materials")).toBeNull();
});
```

Frontend tests 106 → 108.

---

## Task 6: §6.5 — `ActiveTaskDashboard` + composite ranking

**Files:**
- Create: `tutor-web/src/lib/taskRanking.ts`
- Create: `tutor-web/src/components/ActiveTaskDashboard.tsx`
- Create: `tutor-web/src/__tests__/taskRanking.test.ts`
- Create: `tutor-web/src/__tests__/ActiveTaskDashboard.test.tsx`
- Modify: `tutor-web/src/App.tsx` — render `<ActiveTaskDashboard />` when `showQuickStart` instead of `<TaskQuickStart />`. The "+ Manual entry" path inside `ActiveTaskDashboard` opens `<TaskQuickStart />` as a fallback. Keep TaskQuickStart in the codebase.

### `taskRanking.ts`

```ts
export interface RankableTask {
  id: string;
  subject: string;
  title: string;
  deadline: string;  // ISO
  status?: string;
}

const KIND_WEIGHT: Record<string, number> = {
  exam: 1.0, partial: 1.0, final: 1.0,
  tema: 0.8, hw: 0.8, homework: 0.8,
  lab: 0.5, laborator: 0.5,
  seminar: 0.3, sem: 0.3,
};

export function deadlineUrgency(deadlineIso: string, now: Date = new Date()): number {
  const days = (new Date(deadlineIso).getTime() - now.getTime()) / 86400000;
  if (days < 0) return 1;
  if (days >= 14) return 0;
  return 1 - days / 14;
}

export function kindWeight(title: string): number {
  const t = title.toLowerCase();
  for (const [k, w] of Object.entries(KIND_WEIGHT)) {
    if (t.includes(k)) return w;
  }
  return 0.4;
}

/**
 * Composite rank score per spec § 6.5:
 *   (deadline_urgency × 0.5) + (weight × 0.2) + (readiness × 0.3)
 * Higher = more urgent / important. readiness defaults to 0.5 when no
 * KnowledgeRepo signal is available (assume mid-confidence).
 */
export function rankTask(task: RankableTask, readiness: number = 0.5, now: Date = new Date()): number {
  const u = deadlineUrgency(task.deadline, now);
  const w = kindWeight(task.title);
  return u * 0.5 + w * 0.2 + readiness * 0.3;
}
```

### `taskRanking.test.ts`

```ts
import { test, expect } from "vitest";
import { deadlineUrgency, kindWeight, rankTask } from "../lib/taskRanking";

test("deadlineUrgency saturates at 0 (>=14d) and 1 (<=0d)", () => {
  const now = new Date("2026-05-10T00:00:00Z");
  expect(deadlineUrgency("2026-05-31T00:00:00Z", now)).toBe(0);
  expect(deadlineUrgency("2026-05-09T00:00:00Z", now)).toBe(1);
  const seven = deadlineUrgency("2026-05-17T00:00:00Z", now);
  expect(seven).toBeGreaterThan(0.4);
  expect(seven).toBeLessThan(0.6);
});

test("kindWeight matches keyword in title", () => {
  expect(kindWeight("Tema 5")).toBe(0.8);
  expect(kindWeight("Partial 2021")).toBe(1.0);
  expect(kindWeight("Lab 3")).toBe(0.5);
  expect(kindWeight("Seminar 2")).toBe(0.3);
  expect(kindWeight("Random thing")).toBe(0.4);
});

test("rankTask composite roughly matches spec weights", () => {
  const now = new Date("2026-05-10T00:00:00Z");
  // Tema 5 due in 7 days, readiness 0.5 → ~0.5*0.5 + 0.8*0.2 + 0.5*0.3 = 0.56
  const score = rankTask({ id: "1", subject: "PA", title: "Tema 5", deadline: "2026-05-17T00:00:00Z" }, 0.5, now);
  expect(score).toBeGreaterThan(0.5);
  expect(score).toBeLessThan(0.7);
});
```

### `ActiveTaskDashboard.tsx`

```tsx
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { jarvisFetch } from "../lib/api";
import { rankTask, type RankableTask } from "../lib/taskRanking";
import { TaskQuickStart } from "./TaskQuickStart";

interface TaskView extends RankableTask {}

/**
 * Phase 6.5: replaces TaskQuickStart as the default "no-task-pinned"
 * landing. Renders the user's tasks ranked by the spec composite score.
 * "+ Manual entry" expands the original TaskQuickStart inline as a
 * fallback for adding a task that isn't surfaced by any detector.
 */
export function ActiveTaskDashboard() {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [showManual, setShowManual] = useState(false);

  useEffect(() => {
    jarvisFetch("/api/v1/tasks")
      .then(r => r.ok ? r.json() : { tasks: [] })
      .then((data: { tasks: TaskView[] }) => setTasks(data.tasks ?? []))
      .catch(() => setTasks([]))
      .finally(() => setLoaded(true));
  }, []);

  const ranked = [...tasks].sort((a, b) => rankTask(b) - rankTask(a));

  return (
    <div data-testid="active-task-dashboard" className="p-6 font-mono text-sm">
      <div className="text-xs font-bold tracking-widest mb-2">ACTIVE TASKS · ranked by urgency × weight × readiness</div>
      {!loaded ? (
        <div className="text-page-fg/60">loading…</div>
      ) : ranked.length === 0 ? (
        <div data-testid="active-task-empty" className="text-page-fg/60 mb-4">
          No active tasks yet. Trigger detection or add one manually below.
        </div>
      ) : (
        <ul role="list" className="space-y-1 mb-4">
          {ranked.map(t => {
            const days = Math.round((new Date(t.deadline).getTime() - Date.now()) / 86400000);
            const dueTag = days < 0 ? `OVERDUE ${-days}d` : days === 0 ? "TODAY" : `${days}d`;
            return (
              <li key={t.id} data-testid="active-task-row" data-task-id={t.id}>
                <button
                  onClick={() => navigate(`/?taskId=${t.id}`)}
                  className="w-full text-left border border-border-strong p-2 hover:bg-accent-soft"
                >
                  <div className="text-xs font-bold tracking-widest">
                    {t.subject} · {dueTag}
                  </div>
                  <div className="text-sm">{t.title}</div>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <div className="flex gap-2 mb-4">
        <button
          data-testid="active-task-detect-btn"
          onClick={async () => {
            try {
              await jarvisFetch("/api/v1/task-detect/run", { method: "POST" });
              const r = await jarvisFetch("/api/v1/tasks");
              if (r.ok) {
                const data: { tasks: TaskView[] } = await r.json();
                setTasks(data.tasks ?? []);
              }
            } catch (_) {}
          }}
          className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-3 py-2 sm:py-1"
        >
          RUN DETECTION
        </button>
        <button
          data-testid="active-task-manual-btn"
          onClick={() => setShowManual(s => !s)}
          className="text-xs font-bold tracking-widest bg-page-bg text-page-fg border-2 border-border-strong px-3 py-2 sm:py-1"
        >
          {showManual ? "− Hide manual entry" : "+ Manual entry"}
        </button>
      </div>
      {showManual && <TaskQuickStart />}
    </div>
  );
}
```

### `ActiveTaskDashboard.test.tsx`

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ActiveTaskDashboard } from "../components/ActiveTaskDashboard";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("renders empty state when no tasks", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({ tasks: [] }), { status: 200 }),
  ));
  render(<MemoryRouter><ActiveTaskDashboard /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-empty")).toBeInTheDocument());
});

test("renders ranked task list", async () => {
  // Two tasks: nearer-deadline + higher-weight one should rank first.
  const future = (d: number) => new Date(Date.now() + d * 86400000).toISOString();
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({
      tasks: [
        { id: "T1", subject: "PA", title: "Seminar 1", deadline: future(13), status: "ACTIVE" },
        { id: "T2", subject: "PS", title: "Partial 2021", deadline: future(2),  status: "ACTIVE" },
      ],
    }), { status: 200 }),
  ));
  render(<MemoryRouter><ActiveTaskDashboard /></MemoryRouter>);
  await waitFor(() => expect(screen.getAllByTestId("active-task-row").length).toBe(2));
  const rows = screen.getAllByTestId("active-task-row");
  // Partial 2021 should rank above Seminar 1.
  expect(rows[0].getAttribute("data-task-id")).toBe("T2");
  expect(rows[1].getAttribute("data-task-id")).toBe("T1");
});

test("manual entry toggle reveals TaskQuickStart", async () => {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify({ tasks: [] }), { status: 200 }),
  ));
  render(<MemoryRouter><ActiveTaskDashboard /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-empty")).toBeInTheDocument());
  fireEvent.click(screen.getByTestId("active-task-manual-btn"));
  await waitFor(() => expect(screen.getByTestId("task-quickstart")).toBeInTheDocument());
});
```

### App.tsx

Replace `<TaskQuickStart />` with `<ActiveTaskDashboard />` in the JSX:

```tsx
import { ActiveTaskDashboard } from "./components/ActiveTaskDashboard";
// ...
{showQuickStart ? <ActiveTaskDashboard /> : <TutorWorkspace ... />}
```

Frontend tests 108 + 3 (taskRanking) + 3 (dashboard) = 114.

---

## Task 7: §6.4 — `POST /api/v1/task-detect/run` route (manual trigger)

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`

```kotlin
post("/api/v1/task-detect/run") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val sources: List<jarvis.tutor.taskdetect.TaskDetector> = listOf(
            jarvis.tutor.taskdetect.ManualSource(ctx.db, userId),
            jarvis.tutor.taskdetect.FiimaterialsSource(ctx.ledgerDir.resolve("archival/_extras")),
        )
        val agg = jarvis.tutor.taskdetect.TaskDetectorAggregator(sources)
        val detected = kotlinx.coroutines.runBlocking { agg.discoverAll() }
        val repo = jarvis.tutor.taskdetect.DetectedTaskRepo(ctx.db)
        val taskRepo = TaskRepo(ctx.db)
        var inserted = 0; var existing = 0
        for (d in detected) {
            val mappedId = repo.findExisting(d.sourceId, d.externalId)
            if (mappedId != null) {
                repo.upsertMapping(d.sourceId, d.externalId, mappedId, userId)
                existing++
                continue
            }
            // Create new Task row + mapping.
            val newId = jarvis.tutor.TutorTypes.ulid()
            val now = Instant.now()
            taskRepo.insert(jarvis.tutor.Task(
                id = newId, userId = userId,
                subject = d.subject.take(32), title = d.title.take(256),
                deadline = d.deadline,
                problemRef = jarvis.tutor.ContentRef(repo = "detected", path = d.problemPath ?: "", sha = "pending"),
                conceptRefs = emptyList(),
                rubricRef = jarvis.tutor.ContentRef(repo = "detected", path = d.problemPath ?: "", sha = "pending"),
                scratchpad = null, submission = null, grade = null,
                cardRefs = emptyList(),
                status = jarvis.tutor.TaskStatus.ACTIVE,
                createdAt = now, updatedAt = now,
            ))
            repo.upsertMapping(d.sourceId, d.externalId, newId, userId)
            inserted++
        }
        call.respond(HttpStatusCode.OK, ApiTaskDetectReply(inserted = inserted, existing = existing, total = detected.size))
    }
}
```

Type:
```kotlin
@Serializable
private data class ApiTaskDetectReply(val inserted: Int, val existing: Int, val total: Int)
```

Test (append to existing tasks-routes test):

```kotlin
@Test
fun `POST task-detect run returns counts`(@TempDir tmp: Path) = testApplication {
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
    val r = client.post("/api/v1/task-detect/run") {
        cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
    }
    assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
    assertTrue(r.bodyAsText().contains("\"inserted\""))
    assertTrue(r.bodyAsText().contains("\"existing\""))
}
```

Backend tests 572 → 573.

---

## Task 8: Code Gate

```bash
git push origin main
```
Wait for CI green.

---

## Task 9: Live Gate

```bash
cd tutor-web && npm run build
cd /c/Users/User/jarvis-kotlin
git add src/main/resources/tutor-dist/
git commit -m "Phase 6: rebuild frontend bundle (ActiveTaskDashboard + Reference materials)"
git push origin main
& "C:\Program Files\Git\bin\bash.exe" tools/deploy.sh
curl -sS https://corgflix.duckdns.org/healthz   # ok
```

**Migration risk repeat:** the new `tasks.material_paths` + `detected_task_mapping` tables are added. Both safe — `material_paths` is `nullable()`; `DetectedTaskMappingTable` is brand new (no existing rows). `createMissingTablesAndColumns` handles both.

---

## Task 10: Playwright Gate

Spawn Playwright agent, scenarios:
1. ActiveTaskDashboard renders — load `/tutor/?pick=1`, see ACTIVE TASKS heading + RUN DETECTION button + + Manual entry button.
2. RUN DETECTION POSTs `/api/v1/task-detect/run` → 200 with counts; tasks list refreshes.
3. Reference materials rail — open a task that has materialPaths populated; verify rail renders.
4. Composite ranking — create two tasks (one near deadline, one far); verify order.

---

## Task 11: UX-Playbook Gate

Recognition Over Recall + Hick's Law + Goal Gradient + Zeigarnik columns. Audit only ActiveTaskDashboard + Reference materials surfaces.

---

## Task 12: Final review

DoD:
- Backend tests 573 (566 + 3 DetectedTaskRepo + 2 FiimaterialsSource + 1 material_paths + 1 task-detect/run = +7).
- Frontend tests 114 (106 + 2 referenceMaterials + 3 taskRanking + 3 ActiveTaskDashboard).
- Daemon untouched (16).
- CI green; live healthz ok; bundle hash matches.
- Playwright gate scenarios pass.
- UX-Playbook 0 HIGH; MED/LOW in backlog.

---

## Out of scope (Phase 6 explicitly defers)

- Cron-driven re-scrape via SKILL.md — existing CronRunner uses LLM tool_use round-trips; pure-Kotlin batch-job cron is its own phase. Manual-trigger route + future scheduled-task hook are sufficient for Phase 6.
- `assignments.jsonl` / `schedule.json` integration — read-side, no Phase-6 surface change.
- Real per-faculty `CourseScraper` impls (UaicFiiPaScraper etc) — wait for user-provided URLs.
- Knowledge-aware concept popups, FSRS gating — Phase 7.
- Plotly inline / cron probe install / final audit — Phase 8.
