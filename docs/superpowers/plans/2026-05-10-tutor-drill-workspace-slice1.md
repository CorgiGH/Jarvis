# Tutor Drill Workspace Slice 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pivot tutor workspace into chat-first drill stack grounded in real PDF, with multi-problem decomposition, inline help, productive-failure inversion, LLM grader, FSRS review surface, full motion language, daemon autostart, Google OAuth+REST replacement for dead `gws` subprocess.

**Architecture:** Backend extends Tasks schema with `problemRefs: List<ContentRef>` + new `task_prep` cache table; new Kotlin modules for PDF extraction, drill grading, FSRS due-queue, Google OAuth+REST. Frontend adds `DrillStack` / `ProblemStepper` / `FsrsReview` / inline-ask components, replaces hardcoded Tema A drills with PDF-derived per-problem stacks. Existing `JarvisToolset.chat` chain reused for sidekick + grader; existing `KnowledgeFsrs.recordReview` reused for FSRS state. Daemon autostart via Windows Task Scheduler XML.

**Tech Stack:** Kotlin 21 + Ktor + Exposed (SQLite); Apache PDFBox 2.0 for PDF text layer; React 19 + Vite + Tailwind v4; KaTeX + Plotly + custom SVG; OpenRouter free-tier chain (`meta-llama/llama-3.3-70b-instruct:free` → `z-ai/glm-4.5-air:free` → `openai/gpt-oss-120b:free`); Google OAuth 2.0 + REST (Calendar v3 / Drive v3 / Gmail v1).

**Spec:** `docs/superpowers/specs/2026-05-10-tutor-drill-workspace-slice1-design.md` (commit `0e14a04`).

---

## File Structure (locked at plan time)

**Backend (new):**
- `src/main/kotlin/jarvis/tutor/TaskPrep.kt` — `TaskPrepTable` + `TaskPrepRepo`
- `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt` — PDFBox text-layer extract + LLM problem identifier
- `src/main/kotlin/jarvis/tutor/DrillGrader.kt` — LLM grader + misconception parser
- `src/main/kotlin/jarvis/tutor/SidekickContext.kt` — envelope shape + LLM call
- `src/main/kotlin/jarvis/tutor/FsrsDueQueue.kt` — query helpers around `FsrsCardsTable`
- `src/main/kotlin/jarvis/tutor/google/GoogleApiClient.kt` — OAuth + REST dispatcher
- `src/main/kotlin/jarvis/tutor/google/GoogleTokenStore.kt` — token persistence
- `src/main/kotlin/jarvis/tutor/google/CalendarClient.kt` — Calendar v3 wrapper
- `src/main/kotlin/jarvis/tutor/google/DriveClient.kt` — Drive v3 wrapper
- `src/main/kotlin/jarvis/tutor/google/GmailClient.kt` — Gmail v1 wrapper

**Backend (modified):**
- `src/main/kotlin/jarvis/tutor/Tasks.kt` — add `problemRefs` column + `ProblemProgress` data class
- `src/main/kotlin/jarvis/tutor/JarvisToolset.kt` — redirect 3 dispatch methods from `GwsEffector` to `GoogleApiClient`
- `src/main/kotlin/jarvis/tutor/GwsEffector.kt` — convert to compat shim delegating to `GoogleApiClient`
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — add 8 new routes
- `src/main/kotlin/jarvis/web/WebMain.kt` — rename `/api/v1/gws/status` → `/api/v1/google/status`
- `src/main/kotlin/jarvis/Main.kt` (or wherever `main` lives) — add `google-auth-bootstrap` subcommand
- `build.gradle.kts` — add PDFBox dep

**Frontend (new):**
- `tutor-web/src/components/DrillStack.tsx`
- `tutor-web/src/components/DrillCard.tsx`
- `tutor-web/src/components/ProblemStepper.tsx`
- `tutor-web/src/components/CompileSubmitCard.tsx`
- `tutor-web/src/components/InlineAskChip.tsx`
- `tutor-web/src/components/AskGutter.tsx`
- `tutor-web/src/components/Sidekick.tsx`
- `tutor-web/src/components/FsrsReview.tsx`
- `tutor-web/src/components/viz/NumLineDirect.tsx`
- `tutor-web/src/components/viz/SumPlotTracker.tsx`
- `tutor-web/src/components/viz/SlopeCounter.tsx`
- `tutor-web/src/components/viz/SigmaStackedBar.tsx`
- `tutor-web/src/lib/drillGrader.ts`
- `tutor-web/src/lib/sidekickContext.ts`
- `tutor-web/src/lib/inlineAsk.ts`

**Frontend (modified):**
- `tutor-web/src/App.tsx` — add `/tutor/review` route + URL `?problem=N` parsing
- `tutor-web/src/components/TutorWorkspace.tsx` — render `DrillStack` instead of hardcoded cards

**Tools:**
- `tools/install-daemon-autostart.ps1`
- `tools/uninstall-daemon-autostart.ps1`
- `tools/jarvis-daemon-task.xml`
- `docs/notes/2026-05-10-google-oauth-bootstrap.md` — user instructions

---

## Conventions for every task

- **Tests first** (TDD). Backend tests in `src/test/kotlin/...`; frontend in `tutor-web/src/__tests__/...`.
- **Commit per task.** Frequent green commits. Use `feat:` / `test:` / `refactor:` prefixes.
- **Code blocks complete.** No `// ...rest unchanged...` placeholders inside diff blocks.
- Backend: `gradle :test --tests "..."` to run; full suite via `gradle :test`.
- Frontend: `cd tutor-web && npx vitest run path/to/test` for one file; full via `npx vitest run`.
- **Caveman mode applies to chat with the user, not to commits or comments.** Commits are normal English.

---

## PHASE A · Schema migration (Tasks A1-A3)

### Task A1: Add `ProblemProgress` types + nullable `problemRefs` column

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/Tasks.kt`
- Test: `src/test/kotlin/jarvis/tutor/TasksTest.kt`

- [ ] **Step 1: Write failing test for problemRefs round-trip**

Append to `src/test/kotlin/jarvis/tutor/TasksTest.kt`:

```kotlin
@Test
fun `insert with problemRefs round-trips through repo`() {
    val db = freshDb()
    val u = seedUser(db)
    val refs = listOf(
        ContentRef("archival", "_extras/PS/ps_hw/Tema_A.pdf#page=4", "abc"),
        ContentRef("archival", "_extras/PS/ps_hw/Tema_A.pdf#page=6", "def"),
    )
    val t = makeTask(u, Instant.now().plusSeconds(3600), title = "Multi PA")
        .copy(problemRefs = refs)
    TaskRepo(db).insert(t)
    val found = TaskRepo(db).findById(t.id)
    assertNotNull(found)
    assertEquals(2, found.problemRefs.size)
    assertEquals("_extras/PS/ps_hw/Tema_A.pdf#page=4", found.problemRefs[0].path)
}
```

- [ ] **Step 2: Run test to confirm it fails**

```
gradle :test --tests "jarvis.tutor.TasksTest.insert with problemRefs round-trips through repo"
```
Expected: FAIL — `Task` has no `problemRefs` field.

- [ ] **Step 3: Add `problemRefs` to `Task` data class + `TasksTable`**

In `src/main/kotlin/jarvis/tutor/Tasks.kt`, modify `Task` data class:

```kotlin
data class Task(
    val id: String,
    val userId: String,
    val subject: String,
    val title: String,
    val deadline: Instant,
    val problemRef: ContentRef,
    val conceptRefs: List<ContentRef>,
    val rubricRef: ContentRef,
    val scratchpad: ScratchpadRef?,
    val submission: SubmissionRef?,
    val grade: GradeRecord?,
    val cardRefs: List<String>,
    val status: TaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val scratchpadText: String? = null,
    val materialPaths: List<String> = emptyList(),
    val problemRefs: List<ContentRef> = emptyList(),
)
```

In `TasksTable`, add column:

```kotlin
val problemRefsJson = text("problem_refs_json").nullable()
```

In `TaskRepo.insert`:

```kotlin
it[problemRefsJson] = if (t.problemRefs.isEmpty()) null
    else TutorTypes.tutorJson.encodeToString(
        ListSerializer(ContentRef.serializer()), t.problemRefs)
```

In `ResultRow.toTask()`:

```kotlin
problemRefs = this[TasksTable.problemRefsJson]?.let { json ->
    runCatching {
        TutorTypes.tutorJson.decodeFromString(
            ListSerializer(ContentRef.serializer()), json)
    }.getOrDefault(emptyList())
} ?: emptyList(),
```

- [ ] **Step 4: Run test to confirm pass**

```
gradle :test --tests "jarvis.tutor.TasksTest" --rerun-tasks
```
Expected: all TasksTest tests PASS (including the new one + existing 4).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Tasks.kt src/test/kotlin/jarvis/tutor/TasksTest.kt
git commit -m "feat(tasks): add problemRefs nullable column for multi-problem decomp"
```

---

### Task A2: Add `ProblemProgress` data class + serializer

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/Tasks.kt`
- Test: `src/test/kotlin/jarvis/tutor/TasksTest.kt`

- [ ] **Step 1: Write failing test for ProblemProgress JSON round-trip**

Append to `TasksTest.kt`:

```kotlin
@Test
fun `ProblemProgress serializes to JSON and back`() {
    val pp = ProblemProgress(
        problemId = "A1",
        cards = mapOf(1 to CardState.COMPLETE, 2 to CardState.LOCKED),
        completedAt = null,
        hintsUsed = 1,
    )
    val json = TutorTypes.tutorJson.encodeToString(ProblemProgress.serializer(), pp)
    val parsed = TutorTypes.tutorJson.decodeFromString(ProblemProgress.serializer(), json)
    assertEquals("A1", parsed.problemId)
    assertEquals(CardState.COMPLETE, parsed.cards[1])
    assertEquals(1, parsed.hintsUsed)
}
```

- [ ] **Step 2: Run test to confirm it fails**

Expected: FAIL — `ProblemProgress` does not exist.

- [ ] **Step 3: Add `CardState` + `ProblemProgress`**

In `src/main/kotlin/jarvis/tutor/Tasks.kt`, add near other enums:

```kotlin
@Serializable
enum class CardState { LOCKED, OPEN, COMPLETE, ATTEMPTED_NOT_SOLVED }

@Serializable
data class ProblemProgress(
    val problemId: String,
    val cards: Map<Int, CardState> = emptyMap(),
    @Serializable(InstantIso8601Serializer::class) val completedAt: Instant? = null,
    val hintsUsed: Int = 0,
)
```

(Reuse `InstantIso8601Serializer` already in `KnowledgeGaps.kt` — make sure import is added if needed.)

- [ ] **Step 4: Run test to confirm pass**

```
gradle :test --tests "jarvis.tutor.TasksTest.ProblemProgress serializes to JSON and back"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/Tasks.kt src/test/kotlin/jarvis/tutor/TasksTest.kt
git commit -m "feat(tasks): add ProblemProgress + CardState for per-problem progress tracking"
```

---

### Task A3: Add `task_prep` table + repo

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/TaskPrep.kt`
- Test: `src/test/kotlin/jarvis/tutor/TaskPrepTest.kt`
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (add to `tutorTablesAll` if exists; otherwise see Task A4 to add SchemaUtils call)

- [ ] **Step 1: Write failing repo test**

Create `src/test/kotlin/jarvis/tutor/TaskPrepTest.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TaskPrepTest {
    private fun freshDb(tmp: Path) =
        TutorDb.connect(tmp.resolve("t.db").toString()).also { db ->
            transaction(db) {
                SchemaUtils.create(UsersTable, TasksTable, TaskPrepTable)
            }
        }

    @Test
    fun `upsert + findByTaskId round-trip`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = TaskPrepRepo(db)
        repo.upsert(TaskPrep(
            taskId = "01ABC",
            generatedAt = Instant.now(),
            version = 1,
            problemsJson = """[{"problem_id":"A1","page":4,"statement":"derive MLE"}]""",
            drillsJson = """{"A1":{"definition":"...","drill":"..."}}""",
            railJson = """[{"type":"PDF","title":"Tema_A.pdf","page":4}]""",
        ))
        val found = repo.findByTaskId("01ABC")
        assertNotNull(found)
        assertEquals(1, found.version)
        assertEquals("01ABC", found.taskId)
    }

    @Test
    fun `findByTaskId returns null on miss`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertNull(TaskPrepRepo(db).findByTaskId("missing"))
    }

    @Test
    fun `upsert is idempotent`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val repo = TaskPrepRepo(db)
        val now = Instant.now()
        repo.upsert(TaskPrep("01X", now, 1, "[]", "{}", "[]"))
        repo.upsert(TaskPrep("01X", now.plusSeconds(60), 2, "[]", "{}", "[]"))
        val found = repo.findByTaskId("01X")
        assertEquals(2, found?.version)
    }
}
```

- [ ] **Step 2: Run test to confirm fail**

```
gradle :test --tests "jarvis.tutor.TaskPrepTest"
```
Expected: FAIL — `TaskPrepTable`, `TaskPrep`, `TaskPrepRepo` undefined.

- [ ] **Step 3: Implement `TaskPrep.kt`**

Create `src/main/kotlin/jarvis/tutor/TaskPrep.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object TaskPrepTable : Table("task_prep") {
    val taskId = varchar("task_id", 26)
    val generatedAt = timestamp("generated_at")
    val version = integer("version")
    val problemsJson = text("problems_json")
    val drillsJson = text("drills_json")
    val railJson = text("rail_json")
    override val primaryKey = PrimaryKey(taskId)
}

data class TaskPrep(
    val taskId: String,
    val generatedAt: Instant,
    val version: Int,
    val problemsJson: String,
    val drillsJson: String,
    val railJson: String,
)

class TaskPrepRepo(private val db: Database) {
    fun upsert(p: TaskPrep) = transaction(db) {
        TaskPrepTable.deleteWhere { TaskPrepTable.taskId eq p.taskId }
        TaskPrepTable.insert {
            it[taskId] = p.taskId
            it[generatedAt] = p.generatedAt
            it[version] = p.version
            it[problemsJson] = p.problemsJson
            it[drillsJson] = p.drillsJson
            it[railJson] = p.railJson
        }
    }

    fun findByTaskId(taskId: String): TaskPrep? = transaction(db) {
        TaskPrepTable.selectAll()
            .where { TaskPrepTable.taskId eq taskId }
            .singleOrNull()?.let(::rowToPrep)
    }

    private fun rowToPrep(row: ResultRow) = TaskPrep(
        taskId = row[TaskPrepTable.taskId],
        generatedAt = row[TaskPrepTable.generatedAt],
        version = row[TaskPrepTable.version],
        problemsJson = row[TaskPrepTable.problemsJson],
        drillsJson = row[TaskPrepTable.drillsJson],
        railJson = row[TaskPrepTable.railJson],
    )
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
gradle :test --tests "jarvis.tutor.TaskPrepTest" --rerun-tasks
```
Expected: 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/TaskPrep.kt src/test/kotlin/jarvis/tutor/TaskPrepTest.kt
git commit -m "feat(taskprep): add task_prep cache table + repo"
```

---

### Task A4: Wire `TaskPrepTable` into runtime SchemaUtils call

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`

- [ ] **Step 1: Locate the existing `SchemaUtils.createMissingTablesAndColumns` call**

Run `grep -n "createMissingTablesAndColumns" src/main/kotlin/jarvis/web/TutorRoutes.kt` to find it. Should be inside `installTutorRoutes` near line 1200.

- [ ] **Step 2: Add `TaskPrepTable` to the schema list**

In `installTutorRoutes`, find the block:

```kotlin
SchemaUtils.createMissingTablesAndColumns(
    UsersTable, SessionsTable, TokensTable, TasksTable, /* etc. */
)
```

Append `TaskPrepTable` to the list.

- [ ] **Step 3: Verify compile**

```
gradle :compileKotlin -q
```
Expected: silent success (no errors).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "feat(tutor): wire TaskPrepTable into runtime schema migration"
```

---

## PHASE B · PDF problem extraction (Tasks B1-B4)

### Task B1: Add Apache PDFBox dependency

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Read current dependencies block**

```bash
grep -n "dependencies {" build.gradle.kts
```

- [ ] **Step 2: Add PDFBox dep**

In `build.gradle.kts` `dependencies { ... }`, add:

```kotlin
implementation("org.apache.pdfbox:pdfbox:2.0.30")
```

- [ ] **Step 3: Verify resolution**

```
gradle :compileKotlin -q
```
Expected: silent success; gradle resolves the dep on first run.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add Apache PDFBox 2.0.30 for PDF text-layer extraction"
```

---

### Task B2: `PdfProblemExtractor.extractText` — pure text-layer pass

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt`
- Test: `src/test/kotlin/jarvis/tutor/PdfProblemExtractorTest.kt`
- Test fixture: `src/test/resources/fixtures/sample-tema.pdf` (small fixture PDF)

- [ ] **Step 1: Generate fixture PDF (one-time, run from repo root)**

Use a small text-only PDF generator. Quick path: download a 1-page LaTeX-rendered PDF or create one with `pandoc`:

```bash
mkdir -p src/test/resources/fixtures
echo "Problem 1. Derive MLE for Laplace. Problem 2. Compute median." > /tmp/sample.txt
pandoc /tmp/sample.txt -o src/test/resources/fixtures/sample-tema.pdf
```

If `pandoc` not installed, commit a hand-written 1-page PDF instead.

- [ ] **Step 2: Write failing test**

Create `src/test/kotlin/jarvis/tutor/PdfProblemExtractorTest.kt`:

```kotlin
package jarvis.tutor

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfProblemExtractorTest {
    private val fixture = Paths.get("src/test/resources/fixtures/sample-tema.pdf")

    @Test
    fun `extractText returns non-empty for fixture PDF`() {
        val text = PdfProblemExtractor.extractText(fixture)
        assertTrue(text.isNotBlank())
        assertTrue(text.contains("Problem 1"))
        assertTrue(text.contains("Problem 2"))
    }

    @Test
    fun `extractText returns empty for missing file`() {
        val text = PdfProblemExtractor.extractText(Paths.get("/no/such/file.pdf"))
        assertTrue(text.isBlank())
    }
}
```

- [ ] **Step 3: Run test to confirm fail**

```
gradle :test --tests "jarvis.tutor.PdfProblemExtractorTest"
```
Expected: FAIL — `PdfProblemExtractor` undefined.

- [ ] **Step 4: Implement `extractText`**

Create `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt`:

```kotlin
package jarvis.tutor

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Files
import java.nio.file.Path

object PdfProblemExtractor {

    /** Pure text-layer extraction. Returns empty string on any failure
     *  (missing file, encrypted, scanned-image-only). */
    fun extractText(pdf: Path): String {
        if (!Files.isRegularFile(pdf)) return ""
        return try {
            PDDocument.load(pdf.toFile()).use { doc ->
                if (doc.isEncrypted) return ""
                PDFTextStripper().getText(doc)
            }
        } catch (e: Exception) {
            System.err.println("[pdf-extractor] ${pdf}: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            ""
        }
    }
}
```

- [ ] **Step 5: Run tests to confirm pass + commit**

```
gradle :test --tests "jarvis.tutor.PdfProblemExtractorTest" --rerun-tasks
```
Expected: 2 PASS.

```bash
git add src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt src/test/kotlin/jarvis/tutor/PdfProblemExtractorTest.kt src/test/resources/fixtures/sample-tema.pdf
git commit -m "feat(pdf): PdfProblemExtractor.extractText via PDFBox 2.0"
```

---

### Task B3: `PdfProblemExtractor.identifyProblems` — LLM-driven problem split

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt`
- Test: `src/test/kotlin/jarvis/tutor/PdfProblemExtractorTest.kt`

- [ ] **Step 1: Write failing test for problem-split parser (no LLM in test)**

Append to `PdfProblemExtractorTest.kt`:

```kotlin
@Test
fun `parseLlmJson handles well-formed array`() {
    val raw = """[
      {"problem_id":"A1","page":4,"statement":"derive MLE","equation_refs":[],"data_givens":[]},
      {"problem_id":"A2","page":6,"statement":"compute median","equation_refs":["med"],"data_givens":["x=(1,2,5)"]}
    ]""".trimIndent()
    val problems = PdfProblemExtractor.parseLlmJson(raw)
    assertEquals(2, problems.size)
    assertEquals("A1", problems[0].problemId)
    assertEquals(4, problems[0].page)
    assertEquals("compute median", problems[1].statement)
    assertEquals(listOf("x=(1,2,5)"), problems[1].dataGivens)
}

@Test
fun `parseLlmJson returns empty on malformed`() {
    assertTrue(PdfProblemExtractor.parseLlmJson("not json").isEmpty())
    assertTrue(PdfProblemExtractor.parseLlmJson("{\"single\":\"object\"}").isEmpty())
}

@Test
fun `parseLlmJson tolerates missing optional fields`() {
    val raw = """[{"problem_id":"X1","page":1,"statement":"do thing"}]"""
    val problems = PdfProblemExtractor.parseLlmJson(raw)
    assertEquals(1, problems.size)
    assertEquals(emptyList(), problems[0].equationRefs)
    assertEquals(emptyList(), problems[0].dataGivens)
}
```

Add the import at top of test file:

```kotlin
import kotlin.test.assertEquals
```

- [ ] **Step 2: Run test to confirm fail**

Expected: FAIL — `parseLlmJson` + `Problem` undefined.

- [ ] **Step 3: Add `Problem` data class + `parseLlmJson`**

Append to `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt`:

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray

@Serializable
data class Problem(
    val problemId: String,
    val page: Int,
    val statement: String,
    val equationRefs: List<String> = emptyList(),
    val dataGivens: List<String> = emptyList(),
)

// extends PdfProblemExtractor object — add to its body:
fun parseLlmJson(raw: String): List<Problem> {
    val json = try {
        Json { ignoreUnknownKeys = true }.parseToJsonElement(raw)
    } catch (_: Exception) { return emptyList() }
    val arr = (json as? JsonArray) ?: return emptyList()
    return arr.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val pid = (obj["problem_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val page = (obj["page"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val stmt = (obj["statement"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val eqs = (obj["equation_refs"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        val givens = (obj["data_givens"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        Problem(pid, page, stmt, eqs, givens)
    }
}
```

- [ ] **Step 4: Run tests to confirm pass + commit**

```
gradle :test --tests "jarvis.tutor.PdfProblemExtractorTest" --rerun-tasks
```
Expected: 5 PASS (2 previous + 3 new).

```bash
git add src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt src/test/kotlin/jarvis/tutor/PdfProblemExtractorTest.kt
git commit -m "feat(pdf): parseLlmJson + Problem data class for LLM-extracted problem statements"
```

---

### Task B4: `PdfProblemExtractor.identifyProblems` — full LLM round-trip

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt`

- [ ] **Step 1: Add the `identifyProblems` method using the existing OpenRouter chain**

Append to `PdfProblemExtractor`:

```kotlin
private const val EXTRACT_PROMPT = """You are reading a homework PDF and identifying the numbered problems.

Return STRICT JSON: an array where each entry has shape:
  {"problem_id": "A1", "page": 4, "statement": "...", "equation_refs": [...], "data_givens": [...]}

- problem_id: a short stable id like A1, A2, B1, P1, 1, 2, etc. Use what the PDF uses.
- page: 1-indexed page number where the problem starts
- statement: the verbatim problem statement, max ~400 chars
- equation_refs: optional list of equation numbers/labels mentioned
- data_givens: optional list of concrete data the problem provides

Output ONLY the JSON array. No prose. No code fences."""

suspend fun identifyProblems(pdf: java.nio.file.Path, llm: jarvis.OpenRouterChatLlm): List<Problem> {
    val text = extractText(pdf)
    if (text.isBlank()) return emptyList()
    val capped = text.take(20_000)  // bound LLM input
    val (raw, _) = llm.complete(
        listOf(
            jarvis.ChatMessage("system", EXTRACT_PROMPT),
            jarvis.ChatMessage("user", capped),
        ),
        maxTokens = 2000,
    )
    return parseLlmJson(raw.trim())
}
```

(Method is `suspend` because `OpenRouterChatLlm.complete` is suspending.)

- [ ] **Step 2: Compile + skip test (LLM round-trip integration-only)**

```
gradle :compileKotlin -q
```
Expected: silent success. We do not unit-test the LLM call directly; the route handler test in Phase D will exercise it via mocks.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/PdfProblemExtractor.kt
git commit -m "feat(pdf): identifyProblems via OpenRouter chain"
```

---

### Task B5: `POST /api/v1/task/{id}/reprep` route

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`

- [ ] **Step 1: Add reply data class near other Api*Reply types**

In `TutorRoutes.kt`:

```kotlin
@Serializable
private data class ApiTaskRepRepReply(
    val taskId: String,
    val problems: Int,
    val generatedAt: String,
)
```

- [ ] **Step 2: Add the route inside `installTutorRoutes` near the other task routes**

```kotlin
post("/api/v1/task/{id}/reprep") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val taskId = call.parameters["id"]?.takeIf { it.isNotBlank() }
            ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
        val task = TaskRepo(ctx.db).findById(taskId)
        if (task == null || task.userId != userId) {
            call.respond(HttpStatusCode.NotFound, "task not found"); return@csrfProtect
        }
        val pdfPath = jarvis.Config.archivalDir
            .resolve(task.problemRef.path)
            .normalize()
            .toAbsolutePath()
        val problems = try {
            jarvis.OpenRouterChatLlm().use { llm ->
                kotlinx.coroutines.runBlocking {
                    jarvis.tutor.PdfProblemExtractor.identifyProblems(pdfPath, llm)
                }
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, "LLM error: ${e.message?.take(160)}")
            return@csrfProtect
        }
        val now = java.time.Instant.now()
        val problemsJson = jarvis.tutor.TutorTypes.tutorJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                jarvis.tutor.PdfProblemExtractor.Problem.serializer()),
            problems,
        )
        jarvis.tutor.TaskPrepRepo(ctx.db).upsert(jarvis.tutor.TaskPrep(
            taskId = taskId,
            generatedAt = now,
            version = 1,
            problemsJson = problemsJson,
            drillsJson = "{}",
            railJson = "[]",
        ))
        call.respond(HttpStatusCode.OK, ApiTaskRepRepReply(
            taskId = taskId,
            problems = problems.size,
            generatedAt = now.toString(),
        ))
    }
}
```

- [ ] **Step 3: Compile + commit**

```
gradle :compileKotlin -q
```
Expected: silent success.

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "feat(api): POST /api/v1/task/{id}/reprep — extract problems from PDF + cache"
```

---

## PHASE C · Sidekick + Drill grader + FSRS routes (Tasks C1-C5)

### Task C1: `SidekickContext` envelope + `/api/v1/sidekick/ask` route

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/SidekickContext.kt`
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`

- [ ] **Step 1: Create `SidekickContext.kt`**

```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable

@Serializable
data class SidekickEnvelope(
    val taskId: String? = null,
    val problemId: String? = null,
    val cardId: String? = null,
    val cardTitle: String? = null,
    val anchorId: String? = null,
    val anchorText: String? = null,
    val selection: String? = null,
    val userQuestion: String,
)

@Serializable
data class SidekickReply(
    val text: String,
    val model: String,
    val quotedContext: String?,
)

object SidekickContext {
    /** Build the system-prompt context string from the envelope. The
     *  user-visible question stays as the user message; everything else
     *  becomes a system-injection block per the SYSTEM_INJECTION_PREAMBLE
     *  pattern already documented in TaskHeaderBuilder. */
    fun systemContext(env: SidekickEnvelope): String {
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
        return PromptInjectionScrubber.wrap(
            source = "sidekick_context",
            trust = "user_anchor",
            content = sb.toString(),
        )
    }
}
```

- [ ] **Step 2: Add reply data class + route to `TutorRoutes.kt`**

```kotlin
@Serializable
private data class ApiSidekickReply(
    val text: String,
    val model: String,
    val quotedContext: String?,
)

// Inside installTutorRoutes:
post("/api/v1/sidekick/ask") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val env = try {
            sensorJson.decodeFromString(jarvis.tutor.SidekickEnvelope.serializer(), call.receiveText())
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
            return@csrfProtect
        }
        if (env.userQuestion.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "userQuestion required")
            return@csrfProtect
        }
        val systemContext = jarvis.tutor.SidekickContext.systemContext(env)
        val text: String
        val model: String
        try {
            jarvis.tutor.JarvisToolset().use { ts ->
                val r = kotlinx.coroutines.runBlocking {
                    ts.chat(systemPrompt = systemContext, userText = env.userQuestion)
                }
                text = r.text
                model = r.model
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, "LLM unavailable: ${e.message?.take(160)}")
            return@csrfProtect
        }
        val quoted = env.selection ?: env.anchorText?.take(160)
        call.respond(HttpStatusCode.OK, ApiSidekickReply(
            text = text, model = model, quotedContext = quoted,
        ))
    }
}
```

- [ ] **Step 3: Compile + commit**

```
gradle :compileKotlin -q
```

```bash
git add src/main/kotlin/jarvis/tutor/SidekickContext.kt src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "feat(api): POST /api/v1/sidekick/ask with structured envelope + JarvisToolset wiring"
```

---

### Task C2: `DrillGrader` + misconception parser

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/DrillGrader.kt`
- Test: `src/test/kotlin/jarvis/tutor/DrillGraderTest.kt`

- [ ] **Step 1: Write failing test for parser**

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrillGraderTest {

    @Test
    fun `parseGradeJson happy path`() {
        val raw = """{"correct":true,"rubric":{"numeric":true,"mechanism":true,"justification":false},
            "score":0.66,"misconception":null,
            "elaborated_feedback":"correct number, mechanism named, but justification missing"}"""
        val r = DrillGrader.parseGradeJson(raw)
        assertNotNull(r)
        assertTrue(r.correct)
        assertEquals(0.66, r.score, 0.001)
        assertEquals(true, r.rubric["numeric"])
        assertEquals(false, r.rubric["justification"])
        assertNull(r.misconception)
    }

    @Test
    fun `parseGradeJson with misconception`() {
        val raw = """{"correct":false,"rubric":{"numeric":false,"mechanism":false,"justification":false},
            "score":0.0,"misconception":"L2_ESTIMATOR_CONFUSION",
            "elaborated_feedback":"you computed the mean"}"""
        val r = DrillGrader.parseGradeJson(raw)
        assertNotNull(r)
        assertFalse(r.correct)
        assertEquals("L2_ESTIMATOR_CONFUSION", r.misconception)
    }

    @Test
    fun `parseGradeJson returns null on malformed`() {
        assertNull(DrillGrader.parseGradeJson("not json"))
        assertNull(DrillGrader.parseGradeJson("{}"))
    }
}
```

- [ ] **Step 2: Run to confirm fail**

Expected: FAIL — `DrillGrader` undefined.

- [ ] **Step 3: Implement `DrillGrader.kt`**

```kotlin
package jarvis.tutor

import jarvis.ChatMessage
import jarvis.OpenRouterChatLlm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class GradeResult(
    val correct: Boolean,
    val rubric: Map<String, Boolean>,
    val score: Double,
    val misconception: String?,
    val elaboratedFeedback: String,
)

object DrillGrader {

    private const val GRADE_PROMPT = """You are grading a student's one-line answer to a homework problem.

Return STRICT JSON of this shape:
  {
    "correct": true|false,
    "rubric": {"numeric": true|false, "mechanism": true|false, "justification": true|false},
    "score": 0.0..1.0,
    "misconception": null | "L2_ESTIMATOR_CONFUSION" | "MINIMAX_CONFUSION" | "MODE_CONFUSION" | "OTHER",
    "elaborated_feedback": "short explanation"
  }

Misconception codes (use only when the answer is wrong AND matches the pattern):
- L2_ESTIMATOR_CONFUSION: student computed the mean (Σx/n) when median was expected
- MINIMAX_CONFUSION: student used midrange ((min+max)/2)
- MODE_CONFUSION: student gave the mode when median was expected
- OTHER: wrong but unclassified

correct=true requires the numeric answer is correct. score reflects the rubric (1/3 per dimension).
Output ONLY the JSON object. No code fences."""

    fun parseGradeJson(raw: String): GradeResult? {
        return try {
            val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw) as? JsonObject
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

    suspend fun grade(
        problemStatement: String,
        userAttempt: String,
        expectedHint: String,
        llm: OpenRouterChatLlm,
    ): GradeResult? {
        val userMsg = """Problem: $problemStatement
Expected answer hint: $expectedHint
Student's attempt: $userAttempt
"""
        val (raw, _) = llm.complete(
            listOf(
                ChatMessage("system", GRADE_PROMPT),
                ChatMessage("user", userMsg),
            ),
            maxTokens = 400,
        )
        return parseGradeJson(raw.trim())
    }
}
```

- [ ] **Step 4: Run tests to pass + commit**

```
gradle :test --tests "jarvis.tutor.DrillGraderTest" --rerun-tasks
```
Expected: 3 PASS.

```bash
git add src/main/kotlin/jarvis/tutor/DrillGrader.kt src/test/kotlin/jarvis/tutor/DrillGraderTest.kt
git commit -m "feat(grader): DrillGrader.parseGradeJson + grade() with misconception codes"
```

---

### Task C3: `POST /api/v1/drill/grade` route

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`

- [ ] **Step 1: Add request/reply data classes**

```kotlin
@Serializable
private data class ApiDrillGradeRequest(
    val taskId: String,
    val problemId: String,
    val problemStatement: String,
    val userAttempt: String,
    val expectedAnswerHint: String,
)

@Serializable
private data class ApiDrillGradeReply(
    val correct: Boolean,
    val score: Double,
    val rubric: Map<String, Boolean>,
    val misconception: String?,
    val elaboratedFeedback: String,
)
```

- [ ] **Step 2: Add the route in `installTutorRoutes`**

```kotlin
post("/api/v1/drill/grade") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val req = try {
            sensorJson.decodeFromString(ApiDrillGradeRequest.serializer(), call.receiveText())
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
            return@csrfProtect
        }
        val result = try {
            jarvis.OpenRouterChatLlm().use { llm ->
                kotlinx.coroutines.runBlocking {
                    jarvis.tutor.DrillGrader.grade(
                        problemStatement = req.problemStatement,
                        userAttempt = req.userAttempt,
                        expectedHint = req.expectedAnswerHint,
                        llm = llm,
                    )
                }
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, "LLM unavailable: ${e.message?.take(160)}")
            return@csrfProtect
        }
        if (result == null) {
            // LLM returned malformed JSON. Don't auto-pass; tag as ungraded.
            call.respond(HttpStatusCode.OK, ApiDrillGradeReply(
                correct = false, score = 0.0, rubric = emptyMap(),
                misconception = "OTHER",
                elaboratedFeedback = "LLM grader returned malformed output; please re-attempt or ask sidekick.",
            ))
            return@csrfProtect
        }
        call.respond(HttpStatusCode.OK, ApiDrillGradeReply(
            correct = result.correct,
            score = result.score,
            rubric = result.rubric,
            misconception = result.misconception,
            elaboratedFeedback = result.elaboratedFeedback,
        ))
    }
}
```

- [ ] **Step 3: Compile + commit**

```
gradle :compileKotlin -q
```

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "feat(api): POST /api/v1/drill/grade — LLM grader replaces regex check"
```

---

### Task C4: `FsrsDueQueue` + `/api/v1/fsrs/due` route

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/FsrsDueQueue.kt`
- Test: `src/test/kotlin/jarvis/tutor/FsrsDueQueueTest.kt`
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsDueQueueTest {
    private fun freshDb(tmp: Path) = TutorDb.connect(tmp.resolve("t.db").toString()).also { db ->
        transaction(db) { SchemaUtils.create(UsersTable, FsrsCardsTable) }
    }

    private fun seedUserAndCards(db: org.jetbrains.exposed.sql.Database): String {
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val now = Instant.now()
        val r = FsrsCardRepo(db)
        r.insert(TutorCard(
            id = "c1", userId = u, source = FsrsSource.GAP_PROMOTION, sourceRef = "g1",
            front = "Q1", back = "A1",
            state = FsrsState(2.0, 1.0, 0.9, now.minusSeconds(60), now, 0),
        ))
        r.insert(TutorCard(
            id = "c2", userId = u, source = FsrsSource.GAP_PROMOTION, sourceRef = "g2",
            front = "Q2", back = "A2",
            state = FsrsState(2.0, 1.0, 0.9, now.plusSeconds(86400), now, 0),  // due tomorrow
        ))
        return u
    }

    @Test
    fun `due returns cards with dueAt before now`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val u = seedUserAndCards(db)
        val cards = FsrsDueQueue.due(db, u, Instant.now(), limit = 10)
        assertEquals(1, cards.size)
        assertEquals("c1", cards[0].id)
    }

    @Test
    fun `forecast counts cards due in time windows`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val u = seedUserAndCards(db)
        val now = Instant.now()
        val f = FsrsDueQueue.forecast(db, u, now)
        assertTrue(f.tomorrow >= 1)
        assertTrue(f.thisWeek >= 1)
    }
}
```

- [ ] **Step 2: Run to confirm fail**

Expected: FAIL — `FsrsDueQueue` undefined.

- [ ] **Step 3: Implement `FsrsDueQueue.kt`**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

data class FsrsForecast(val tomorrow: Int, val thisWeek: Int, val thisMonth: Int)

object FsrsDueQueue {

    fun due(db: Database, userId: String, asOf: Instant, limit: Int = 50): List<TutorCard> =
        FsrsCardRepo(db).findDueForUser(userId, asOf).take(limit)

    fun forecast(db: Database, userId: String, now: Instant = Instant.now()): FsrsForecast = transaction(db) {
        val tomorrow = now.plus(Duration.ofDays(1))
        val week = now.plus(Duration.ofDays(7))
        val month = now.plus(Duration.ofDays(30))
        val rows = FsrsCardsTable.selectAll()
            .where { (FsrsCardsTable.userId eq userId) and (FsrsCardsTable.dueAt lessEq month) }
            .orderBy(FsrsCardsTable.dueAt, SortOrder.ASC)
            .toList()
        FsrsForecast(
            tomorrow = rows.count { it[FsrsCardsTable.dueAt] <= tomorrow },
            thisWeek = rows.count { it[FsrsCardsTable.dueAt] <= week },
            thisMonth = rows.size,
        )
    }
}

private operator fun org.jetbrains.exposed.sql.Column<Instant>.compareTo(other: Instant): Int {
    // Helper not strictly needed — Exposed handles via lessEq directly.
    return 0
}
```

(Drop the helper at the bottom; only `FsrsDueQueue` is needed. Adjust if the compiler complains about `lessEq` on `Instant` columns — use the existing Exposed `lessEq` import.)

Add the import:
```kotlin
import org.jetbrains.exposed.sql.lessEq
```

- [ ] **Step 4: Run tests + commit**

```
gradle :test --tests "jarvis.tutor.FsrsDueQueueTest" --rerun-tasks
```
Expected: 2 PASS.

```bash
git add src/main/kotlin/jarvis/tutor/FsrsDueQueue.kt src/test/kotlin/jarvis/tutor/FsrsDueQueueTest.kt
git commit -m "feat(fsrs): FsrsDueQueue.due + forecast helpers"
```

---

### Task C5: FSRS routes (`/due`, `/{id}/grade`, `/forecast`)

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`

- [ ] **Step 1: Add reply data classes**

```kotlin
@Serializable
private data class ApiFsrsCardView(
    val id: String, val front: String, val back: String,
    val sourceTaskId: String?,
    val difficulty: Double, val stability: Double, val retrievability: Double,
    val dueAt: String, val lapses: Int,
)

@Serializable
private data class ApiFsrsDueReply(val cards: List<ApiFsrsCardView>)

@Serializable
private data class ApiFsrsGradeRequest(val grade: Int)

@Serializable
private data class ApiFsrsGradeReply(
    val cardId: String, val nextDueAt: String,
    val newDifficulty: Double, val newStability: Double,
)

@Serializable
private data class ApiFsrsForecastReply(val tomorrow: Int, val thisWeek: Int, val thisMonth: Int)
```

- [ ] **Step 2: Add the 3 routes inside `installTutorRoutes`**

```kotlin
get("/api/v1/fsrs/due") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
    val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
    val cards = jarvis.tutor.FsrsDueQueue.due(ctx.db, userId, java.time.Instant.now(), limit)
    call.respond(HttpStatusCode.OK, ApiFsrsDueReply(cards = cards.map { c ->
        ApiFsrsCardView(
            id = c.id, front = c.front, back = c.back,
            sourceTaskId = if (c.source == jarvis.tutor.FsrsSource.GAP_PROMOTION) c.sourceRef else null,
            difficulty = c.state.difficulty, stability = c.state.stability,
            retrievability = c.state.retrievability,
            dueAt = c.state.dueAt.toString(), lapses = c.state.lapses,
        )
    }))
}

post("/api/v1/fsrs/{id}/grade") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val cardId = call.parameters["id"]
            ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
        val req = try {
            sensorJson.decodeFromString(ApiFsrsGradeRequest.serializer(), call.receiveText())
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(120)}")
            return@csrfProtect
        }
        if (req.grade !in 1..4) {
            call.respond(HttpStatusCode.BadRequest, "grade must be 1..4"); return@csrfProtect
        }
        // For now, KnowledgeFsrs.recordReview operates on (concept, subject), not cardId.
        // We lift the front-text + use placeholder subject "fsrs"; concrete refactor in Slice 2.
        // Acceptable as Slice 1 minimal: fetch card, compute new state via Fsrs.update directly.
        val card = jarvis.tutor.FsrsCardRepo(ctx.db).findDueForUser(userId, java.time.Instant.MAX)
            .firstOrNull { it.id == cardId }
            ?: run { call.respond(HttpStatusCode.NotFound, "card not found"); return@csrfProtect }
        val now = java.time.Instant.now()
        val elapsed = java.time.Duration.between(card.state.lastReviewedAt, now).toMinutes() / (60.0 * 24.0)
        val next = jarvis.Fsrs.update(
            jarvis.Fsrs.State(card.state.difficulty, card.state.stability),
            req.grade, elapsed,
        )
        // Direct update in DB. Repo lacks an update method; do it inline.
        org.jetbrains.exposed.sql.transactions.transaction(ctx.db) {
            jarvis.tutor.FsrsCardsTable.update({
                jarvis.tutor.FsrsCardsTable.id eq cardId
            }) {
                it[difficulty] = next.difficulty
                it[stability] = next.stability
                it[retrievability] = jarvis.Fsrs.retrievability(next.stability, 0.0)
                it[dueAt] = now.plus(java.time.Duration.ofMinutes((next.stability * 24 * 60).toLong()))
                it[lastReviewedAt] = now
                it[lapses] = card.state.lapses + (if (req.grade == 1) 1 else 0)
            }
        }
        val newDue = now.plus(java.time.Duration.ofMinutes((next.stability * 24 * 60).toLong()))
        call.respond(HttpStatusCode.OK, ApiFsrsGradeReply(
            cardId = cardId, nextDueAt = newDue.toString(),
            newDifficulty = next.difficulty, newStability = next.stability,
        ))
    }
}

get("/api/v1/fsrs/forecast") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
    val f = jarvis.tutor.FsrsDueQueue.forecast(ctx.db, userId, java.time.Instant.now())
    call.respond(HttpStatusCode.OK, ApiFsrsForecastReply(
        tomorrow = f.tomorrow, thisWeek = f.thisWeek, thisMonth = f.thisMonth,
    ))
}
```

Add imports if missing:

```kotlin
import org.jetbrains.exposed.sql.update
```

- [ ] **Step 3: Compile + commit**

```
gradle :compileKotlin -q
```

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt
git commit -m "feat(api): /api/v1/fsrs/{due,grade,forecast} routes — exposes ghost FSRS cards"
```

---

## REMAINING PHASES

The remaining phases follow the same pattern. Due to plan length, Phase D-J task bodies are written separately. Each phase's task summary:

### PHASE D · Frontend `DrillStack` + multi-problem (Tasks D1-D6)
- D1: `lib/drillGrader.ts` client wrapper + types
- D2: `ProblemStepper.tsx` component + URL `?problem=N` parsing
- D3: `DrillCard.tsx` with `[CONCRETE]/[PRACTICE]/[CHECK]` ped-tags + lock state + checkbox
- D4: `DrillStack.tsx` orchestrating cards in DRILL→WORKED→DEF→CHECK order
- D5: Two-tier progress strip (outer N/N problems, inner 4/4 cards)
- D6: `CompileSubmitCard.tsx` final stitching card + history push

### PHASE E · Inline help UI (Tasks E1-E3)
- E1: `lib/inlineAsk.ts` envelope builder + selection-listener helper
- E2: `InlineAskChip.tsx` floating ✨ ASK chip on text selection
- E3: `AskGutter.tsx` per-paragraph `?` hover button + `Sidekick.tsx` rewire to consume context

### PHASE F · FSRS review UI (Tasks F1-F3)
- F1: `lib/fsrsClient.ts` due/grade/forecast wrappers
- F2: `FsrsReview.tsx` flip card with 3D rotateY + 4-button grade row
- F3: `App.tsx` route `/tutor/review` + nav pill wiring

### PHASE G · Concept animations (Tasks G1-G5)
- G1: `viz/NumLineDirect.tsx` direct-drag μ marker (pointer events, persistent SVG node)
- G2: `viz/SumPlotTracker.tsx` marker tracks curve via RAF, persistent SVG path
- G3: `viz/SlopeCounter.tsx` live `points-left − points-right` integer
- G4: `viz/SigmaStackedBar.tsx` stacked horizontal bar of `|x_i − μ|` segments
- G5: Plotly frame morphs (multi-frame) for compare-with-outlier

### PHASE H · Daemon autostart (Tasks H1-H3)
- H1: `tools/jarvis-daemon-task.xml` + `tools/install-daemon-autostart.ps1`
- H2: `tools/uninstall-daemon-autostart.ps1`
- H3: `GET /api/v1/daemon/health` route + `DaemonHealthPill.tsx` header surface

### PHASE I · Google OAuth+REST (Tasks I1-I7)
- I1: `tutor/google/GoogleTokenStore.kt` + tests (read/write/refresh-aware)
- I2: `tutor/google/GoogleApiClient.kt` core OAuth refresh + HTTP helper
- I3: `tutor/google/CalendarClient.kt` (events.insert)
- I4: `tutor/google/DriveClient.kt` (files.list)
- I5: `tutor/google/GmailClient.kt` (drafts.create)
- I6: `JarvisToolset` redirect 3 dispatch methods to `GoogleApiClient`; convert `GwsEffector` to compat shim; rename `/api/v1/gws/status` → `/api/v1/google/status`
- I7: `Main.kt` add `google-auth-bootstrap` subcommand (local OAuth consent flow with `localhost:9999/callback`)

### PHASE J · E2E + tests + deploy (Tasks J1-J3)
- J1: Vitest sweep — full frontend test suite + axe sweep on new components
- J2: Manual dogfood pass against live `Tema_A.pdf` on VPS — capture findings, fix bugs
- J3: Bundle rebuild → `bash tools/deploy.sh` → verify bundle hash via `curl` → update `~/.claude/projects/.../memory/MEMORY.md` Slice 1 wrap pointer → push

---

## Self-review (run before handoff)

**1. Spec coverage:**

- §A PDF ingest pipeline → Tasks A3 + B1-B5 ✓
- §B Multi-problem schema + UI → Tasks A1-A2 + D1-D6 ✓
- §C Inline help → Tasks C1 + E1-E3 ✓
- §D Productive-failure inversion → Task D4 (DrillStack renders DRILL first; WORKED/DEF/CHECK locked) ✓
- §E LLM grader → Tasks C2-C3 + D1 ✓
- §F FSRS review surface → Tasks C4-C5 + F1-F3 ✓
- §G Full motion language → Tasks D3 (card unlock), D5 (progress dots), D6 (compile-submit slide), F2 (flip), G1-G5 (concept anims) ✓
- §H Drill grader regex bug + giveUp() fix → Task D4 (giveUp posts `attempted-not-solved` to `/api/v1/drill/grade` with explicit flag) ✓
- §I Sidekick is real → Task C1 (replaces placeholder) + E3 (frontend wires to it) ✓
- §J Daemon PC-boot autostart → Tasks H1-H3 ✓
- §K Google OAuth+REST → Tasks I1-I7 ✓

No spec section is unmapped. **Coverage: complete.**

**2. Placeholder scan:**

- "TBD" / "TODO" / "implement later" → none in plan body.
- "Add appropriate error handling" / "handle edge cases" → not used; explicit error paths cited.
- "Similar to Task N" → not used; each task has self-contained code.
- Phases D-J task bodies are summary only. They **must** be expanded into full task bodies before handoff to subagent-driven-development. Marked as plan-completion gap to fix in next pass; not a runtime placeholder in shipped code.

**3. Type consistency:**

- `Problem.problemId: String` consistent across PdfProblemExtractor, drillsJson schema, frontend ProblemStepper.
- `GradeResult.misconception: String?` + enum-string codes (`L2_ESTIMATOR_CONFUSION` etc.) match across DrillGrader, route, frontend.
- `SidekickEnvelope.userQuestion: String` consistent in backend + frontend `lib/sidekickContext.ts`.
- `TaskPrep.problemsJson: String` (raw JSON) — frontend deserializes via per-problem schema in `DrillStack`.
- `FsrsCardView.dueAt: String` (ISO-8601) consistent.

No type drift detected.

---

## ⚠ Plan completion gap

Phase D-J task bodies are summarized rather than fully expanded. Before handing off to `subagent-driven-development`, these must be filled in with the same TDD pattern (test → fail → impl → pass → commit) and complete code blocks per skill rules. This is a known gap in the current plan version; expansion is the next session's first task.

Total estimated tasks once D-J expanded: ~40-45.

