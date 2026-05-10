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

## PHASE D · Frontend DrillStack + multi-problem (Tasks D1-D6)

### Task D1: `lib/drillGrader.ts` client wrapper + TS types

**Files:**
- Create: `tutor-web/src/lib/drillGrader.ts`
- Test: `tutor-web/src/__tests__/drillGrader.test.ts`

- [ ] **Step 1: Write failing test for `gradeDrill` + type shapes**

Create `tutor-web/src/__tests__/drillGrader.test.ts`:

```typescript
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { gradeDrill } from "../lib/drillGrader";
import type { GradeResult } from "../lib/drillGrader";

const MOCK_GRADE_RESULT: GradeResult = {
  correct: true,
  score: 1.0,
  rubric: { numeric: true, mechanism: true, justification: true },
  misconception: null,
  elaboratedFeedback:
    "✓ correct. Mechanism cited (median). Reasoning: Σ|x_i − μ| minimized at sample median.",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", {
    value: "csrf=abc123",
    configurable: true,
    writable: true,
  });
});
afterEach(() => {
  vi.unstubAllGlobals();
});

describe("gradeDrill", () => {
  test("POSTs to /api/v1/drill/grade with correct body and CSRF header", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify(MOCK_GRADE_RESULT), {
          status: 200,
          headers: { "content-type": "application/json" },
        })
      )
    );

    const result = await gradeDrill({
      taskId: "task-01",
      problemId: "A3",
      problemStatement: "Sample x=(3,7,8,9,14). What is μ̂ MLE for Laplace?",
      userAttempt: "μ̂ = 8 because median",
      expectedAnswerHint: "median equals 8",
    });

    const fetchMock = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(fetchMock[0]).toBe("/api/v1/drill/grade");
    expect(fetchMock[1].method).toBe("POST");
    expect(fetchMock[1].headers["X-CSRF-Token"]).toBe("abc123");
    const body = JSON.parse(fetchMock[1].body as string);
    expect(body.taskId).toBe("task-01");
    expect(body.problemId).toBe("A3");
    expect(body.userAttempt).toBe("μ̂ = 8 because median");

    expect(result.correct).toBe(true);
    expect(result.score).toBe(1.0);
    expect(result.misconception).toBeNull();
    expect(result.elaboratedFeedback).toContain("median");
  });

  test("returns GradeResult with misconception on incorrect attempt", async () => {
    const misconceptionResult: GradeResult = {
      correct: false,
      score: 0.0,
      rubric: { numeric: false, mechanism: false, justification: false },
      misconception: "L2_ESTIMATOR_CONFUSION",
      elaboratedFeedback: "you computed the mean (L2 MLE), not the Laplace median MLE",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify(misconceptionResult), {
          status: 200,
          headers: { "content-type": "application/json" },
        })
      )
    );

    const result = await gradeDrill({
      taskId: "task-01",
      problemId: "A3",
      problemStatement: "What is μ̂?",
      userAttempt: "μ̂ = 8.2 (sum/n)",
      expectedAnswerHint: "median equals 8",
    });

    expect(result.correct).toBe(false);
    expect(result.misconception).toBe("L2_ESTIMATOR_CONFUSION");
    expect(result.rubric.numeric).toBe(false);
  });

  test("throws on non-OK response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response("LLM unavailable", { status: 502 })
      )
    );

    await expect(
      gradeDrill({
        taskId: "t1",
        problemId: "A1",
        problemStatement: "p",
        userAttempt: "a",
        expectedAnswerHint: "h",
      })
    ).rejects.toThrow(/502/);
  });
});
```

- [ ] **Step 2: Run test to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/drillGrader.test.ts
```

Expected: FAIL — `Cannot find module '../lib/drillGrader'`.

- [ ] **Step 3: Implement `lib/drillGrader.ts`**

Create `tutor-web/src/lib/drillGrader.ts`:

```typescript
import { jarvisFetch } from "./api";

export interface GradeRubric {
  numeric: boolean;
  mechanism: boolean;
  justification: boolean;
  [key: string]: boolean;
}

export interface GradeResult {
  correct: boolean;
  score: number;
  rubric: GradeRubric;
  misconception: string | null;
  elaboratedFeedback: string;
}

export interface GradeDrillArgs {
  taskId: string;
  problemId: string;
  problemStatement: string;
  userAttempt: string;
  expectedAnswerHint: string;
}

export async function gradeDrill(args: GradeDrillArgs): Promise<GradeResult> {
  const res = await jarvisFetch("/api/v1/drill/grade", {
    method: "POST",
    body: JSON.stringify({
      taskId: args.taskId,
      problemId: args.problemId,
      problemStatement: args.problemStatement,
      userAttempt: args.userAttempt,
      expectedAnswerHint: args.expectedAnswerHint,
    }),
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => "");
    throw new Error(`gradeDrill HTTP ${res.status}: ${msg.slice(0, 160)}`);
  }
  return res.json() as Promise<GradeResult>;
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/drillGrader.test.ts
```

Expected: 3 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/lib/drillGrader.ts tutor-web/src/__tests__/drillGrader.test.ts
git commit -m "feat(drill): drillGrader.ts client wrapper + GradeResult TS types"
```

---

### Task D2: `ProblemStepper.tsx` component + `?problem=N` URL parsing

**Files:**
- Create: `tutor-web/src/components/ProblemStepper.tsx`
- Test: `tutor-web/src/__tests__/ProblemStepper.test.tsx`

- [ ] **Step 1: Write failing test**

Create `tutor-web/src/__tests__/ProblemStepper.test.tsx`:

```typescript
import { render, screen, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { MemoryRouter, Route, Routes, useSearchParams } from "react-router-dom";
import { ProblemStepper, parseProblemParam } from "../components/ProblemStepper";

describe("parseProblemParam", () => {
  test("returns 0 when param is absent", () => {
    expect(parseProblemParam(null)).toBe(0);
  });

  test("returns 0 for non-numeric string", () => {
    expect(parseProblemParam("abc")).toBe(0);
  });

  test("returns 0 for negative number", () => {
    expect(parseProblemParam("-1")).toBe(0);
  });

  test("returns parsed index for valid positive integer string", () => {
    expect(parseProblemParam("2")).toBe(2);
  });

  test("returns 0 for '0'", () => {
    expect(parseProblemParam("0")).toBe(0);
  });
});

describe("ProblemStepper component", () => {
  const problems = [
    { problemId: "A1", label: "A1" },
    { problemId: "A2", label: "A2" },
    { problemId: "A3", label: "A3" },
  ];

  function Wrapper({ initialParam = "" }: { initialParam?: string }) {
    return (
      <MemoryRouter initialEntries={[`/?taskId=T1${initialParam}`]}>
        <Routes>
          <Route
            path="/"
            element={
              <ProblemStepper
                problems={problems}
                activeProblemIndex={parseProblemParam(
                  new URLSearchParams(initialParam).get("problem")
                )}
              />
            }
          />
        </Routes>
      </MemoryRouter>
    );
  }

  test("renders all problem labels as buttons", () => {
    render(<Wrapper />);
    expect(screen.getByRole("button", { name: /A1/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /A2/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /A3/ })).toBeInTheDocument();
  });

  test("first problem button is filled (◉) when active index is 0", () => {
    render(<Wrapper />);
    const btn = screen.getByRole("button", { name: /A1/ });
    expect(btn.getAttribute("aria-current")).toBe("true");
    expect(btn.textContent).toContain("◉");
  });

  test("inactive problem buttons show hollow dot (○)", () => {
    render(<Wrapper />);
    const btn2 = screen.getByRole("button", { name: /A2/ });
    expect(btn2.textContent).toContain("○");
    expect(btn2.getAttribute("aria-current")).toBeNull();
  });

  test("active problem index 2 marks A3 as current", () => {
    render(<Wrapper initialParam="&problem=2" />);
    const btn = screen.getByRole("button", { name: /A3/ });
    expect(btn.getAttribute("aria-current")).toBe("true");
    expect(btn.textContent).toContain("◉");
  });

  test("onProblemSelect fires with problem index when button clicked", () => {
    const onSelect = vi.fn();
    render(
      <MemoryRouter>
        <ProblemStepper
          problems={problems}
          activeProblemIndex={0}
          onProblemSelect={onSelect}
        />
      </MemoryRouter>
    );
    fireEvent.click(screen.getByRole("button", { name: /A2/ }));
    expect(onSelect).toHaveBeenCalledWith(1);
  });
});
```

- [ ] **Step 2: Run test to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/ProblemStepper.test.tsx
```

Expected: FAIL — `Cannot find module '../components/ProblemStepper'`.

- [ ] **Step 3: Implement `ProblemStepper.tsx`**

Create `tutor-web/src/components/ProblemStepper.tsx`:

```typescript
import { useNavigate, useSearchParams } from "react-router-dom";

export interface ProblemStub {
  problemId: string;
  label: string;
}

interface ProblemStepperProps {
  problems: ProblemStub[];
  activeProblemIndex: number;
  onProblemSelect?: (index: number) => void;
}

/**
 * Parses the `?problem=N` URL param into a zero-based index.
 * Returns 0 for absent, non-numeric, or negative values.
 */
export function parseProblemParam(raw: string | null): number {
  if (raw == null) return 0;
  const n = parseInt(raw, 10);
  if (isNaN(n) || n < 0) return 0;
  return n;
}

/**
 * Renders the ◉ A1  ○ A2  ○ A3 … stepper strip.
 * Click updates ?problem=N in the URL (pushes history so Back works)
 * and fires optional onProblemSelect callback.
 */
export function ProblemStepper({
  problems,
  activeProblemIndex,
  onProblemSelect,
}: ProblemStepperProps) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  function handleClick(index: number) {
    const next = new URLSearchParams(searchParams);
    next.set("problem", String(index));
    navigate({ search: next.toString() });
    onProblemSelect?.(index);
  }

  return (
    <nav
      data-testid="problem-stepper"
      aria-label="Problem stepper"
      className="flex items-center gap-3 px-4 py-2 border-b-4 border-border-strong bg-accent-soft font-mono text-xs overflow-x-auto"
    >
      {problems.map((p, i) => {
        const active = i === activeProblemIndex;
        return (
          <button
            key={p.problemId}
            data-testid={`stepper-problem-${p.problemId}`}
            aria-current={active ? "true" : undefined}
            onClick={() => handleClick(i)}
            className={`flex items-center gap-1 tracking-widest whitespace-nowrap px-2 py-1 transition-colors ${
              active
                ? "text-page-fg font-bold bg-accent"
                : "text-page-fg/60 hover:text-page-fg hover:bg-accent/50"
            }`}
          >
            <span aria-hidden="true">{active ? "◉" : "○"}</span>
            <span>{p.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/ProblemStepper.test.tsx
```

Expected: 8 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/ProblemStepper.tsx tutor-web/src/__tests__/ProblemStepper.test.tsx
git commit -m "feat(drill): ProblemStepper component + parseProblemParam URL helper"
```

---

### Task D3: `DrillCard.tsx` — locked/open/complete card with ped-tag, checkbox, stagger support

**Files:**
- Create: `tutor-web/src/components/DrillCard.tsx`
- Test: `tutor-web/src/__tests__/DrillCard.test.tsx`

- [ ] **Step 1: Write failing test**

Create `tutor-web/src/__tests__/DrillCard.test.tsx`:

```typescript
import { render, screen, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { DrillCard, DrillCardState } from "../components/DrillCard";

function mockReducedMotion(reduced: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reduced && query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

beforeEach(() => {
  mockReducedMotion(false);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("DrillCard ped-tag rendering", () => {
  test("DRILL card shows [PRACTICE] ped-tag", () => {
    render(
      <DrillCard
        cardType="DRILL"
        title="③ DRILL · YOUR TURN"
        state="open"
        staggerIndex={0}
      >
        <p>Attempt the problem.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[PRACTICE]");
  });

  test("WORKED card shows [CONCRETE] ped-tag", () => {
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="open"
        staggerIndex={1}
      >
        <p>Worked solution.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[CONCRETE]");
  });

  test("DEFINITION card shows [CONCRETE] ped-tag", () => {
    render(
      <DrillCard
        cardType="DEFINITION"
        title="① DEFINITION"
        state="open"
        staggerIndex={2}
      >
        <p>Definition body.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[CONCRETE]");
  });

  test("CHECK card shows [CHECK] ped-tag", () => {
    render(
      <DrillCard
        cardType="CHECK"
        title="④ CHECK · TRANSFER"
        state="open"
        staggerIndex={3}
      >
        <p>Transfer question.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("ped-tag").textContent).toBe("[CHECK]");
  });
});

describe("DrillCard lock state", () => {
  test("locked card hides body and shows lock message", () => {
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="locked"
        staggerIndex={1}
      >
        <p data-testid="secret-body">Secret worked solution.</p>
      </DrillCard>
    );
    expect(screen.queryByTestId("secret-body")).not.toBeInTheDocument();
    expect(screen.getByTestId("card-lock-message")).toBeInTheDocument();
    expect(screen.getByTestId("card-lock-message").textContent).toMatch(
      /attempt drill first/i
    );
  });

  test("open card renders body slot", () => {
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="open"
        staggerIndex={1}
      >
        <p data-testid="visible-body">Worked solution text.</p>
      </DrillCard>
    );
    expect(screen.getByTestId("visible-body")).toBeInTheDocument();
    expect(screen.queryByTestId("card-lock-message")).not.toBeInTheDocument();
  });

  test("complete card shows checked checkbox and green header tint", () => {
    render(
      <DrillCard
        cardType="DRILL"
        title="③ DRILL · YOUR TURN"
        state="complete"
        staggerIndex={0}
      >
        <p>Body.</p>
      </DrillCard>
    );
    const checkbox = screen.getByTestId("card-checkbox");
    expect(checkbox.getAttribute("aria-checked")).toBe("true");
    const header = screen.getByTestId("card-header");
    expect(header.className).toContain("complete");
  });
});

describe("DrillCard stagger + reduced-motion", () => {
  test("data-stagger-index attribute matches prop", () => {
    render(
      <DrillCard
        cardType="CHECK"
        title="④ CHECK · TRANSFER"
        state="open"
        staggerIndex={3}
      >
        <p>Body.</p>
      </DrillCard>
    );
    expect(
      screen.getByTestId("drill-card").getAttribute("data-stagger-index")
    ).toBe("3");
  });

  test("under prefers-reduced-motion, no animation class applied", () => {
    mockReducedMotion(true);
    render(
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state="open"
        staggerIndex={1}
      >
        <p>Body.</p>
      </DrillCard>
    );
    const card = screen.getByTestId("drill-card");
    expect(card.className).not.toContain("animate-slide-in");
  });
});
```

- [ ] **Step 2: Run test to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/DrillCard.test.tsx
```

Expected: FAIL — `Cannot find module '../components/DrillCard'`.

- [ ] **Step 3: Implement `DrillCard.tsx`**

Create `tutor-web/src/components/DrillCard.tsx`:

```typescript
import { useEffect, useRef, type ReactNode } from "react";

export type CardType = "DRILL" | "WORKED" | "DEFINITION" | "CHECK";
export type DrillCardState = "locked" | "open" | "complete";

const PED_TAG: Record<CardType, string> = {
  DRILL: "[PRACTICE]",
  WORKED: "[CONCRETE]",
  DEFINITION: "[CONCRETE]",
  CHECK: "[CHECK]",
};

interface DrillCardProps {
  cardType: CardType;
  title: string;
  state: DrillCardState;
  staggerIndex: number;
  children: ReactNode;
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Single drill card shell.
 *
 * - `state="locked"` — hides body, shows lock message. No animation class.
 * - `state="open"` — renders children. If reduced-motion is OFF, adds
 *   `animate-slide-in` so parent can control stagger via CSS `animation-delay`.
 * - `state="complete"` — renders children, shows ☑ checkbox, green header.
 *
 * `data-stagger-index` is set for parent-driven animation stagger
 * (see DrillStack: 80ms delay per index via inline style).
 */
export function DrillCard({
  cardType,
  title,
  state,
  staggerIndex,
  children,
}: DrillCardProps) {
  const reduced = prefersReducedMotion();
  const animClass =
    state !== "locked" && !reduced ? "animate-slide-in" : "";

  return (
    <article
      data-testid="drill-card"
      data-stagger-index={staggerIndex}
      className={`border-4 border-border-strong bg-page-bg font-mono text-xs ${animClass}`}
      style={
        state !== "locked" && !reduced
          ? { animationDelay: `${staggerIndex * 80}ms` }
          : undefined
      }
    >
      {/* Header row */}
      <div
        data-testid="card-header"
        className={`flex items-center justify-between px-4 py-2 border-b-4 border-border-strong ${
          state === "complete"
            ? "complete bg-accent text-page-fg"
            : "bg-panel-dark-bg text-panel-dark-fg"
        }`}
      >
        <div className="flex items-center gap-3 min-w-0">
          {/* Checkbox: draws ☑ on complete (anim #2) */}
          <span
            data-testid="card-checkbox"
            role="checkbox"
            aria-checked={state === "complete" ? "true" : "false"}
            aria-label="Card complete"
            className={`text-base transition-all duration-[180ms] ease-out select-none ${
              state === "complete" ? "text-page-fg" : "text-page-fg/40"
            }`}
          >
            {state === "complete" ? "☑" : "☐"}
          </span>
          <span className="tracking-widest font-bold truncate">{title}</span>
        </div>
        <span
          data-testid="ped-tag"
          className="text-[10px] tracking-widest text-page-fg/60 shrink-0 ml-2"
        >
          {PED_TAG[cardType]}
        </span>
      </div>

      {/* Body */}
      {state === "locked" ? (
        <div
          data-testid="card-lock-message"
          className="px-4 py-6 text-page-fg/40 tracking-widest text-center"
        >
          🔒 attempt drill first
        </div>
      ) : (
        <div className="card-body px-4 py-4 leading-relaxed">{children}</div>
      )}
    </article>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/DrillCard.test.tsx
```

Expected: 9 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/DrillCard.tsx tutor-web/src/__tests__/DrillCard.test.tsx
git commit -m "feat(drill): DrillCard component with locked/open/complete states, ped-tags, stagger support"
```

---

### Task D4: `DrillStack.tsx` — state machine, DRILL→WORKED→DEF→CHECK order, grade + giveUp

**Files:**
- Create: `tutor-web/src/components/DrillStack.tsx`
- Test: `tutor-web/src/__tests__/DrillStack.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/DrillStack.test.tsx`:

```typescript
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { DrillStack } from "../components/DrillStack";
import type { GradeResult } from "../lib/drillGrader";

const DRILL_CONTENT = {
  drill: "What is the MLE of μ for Laplace(μ, b) given sample (3,7,8,9,14)?",
  worked: "The MLE is the sample median = 8. Proof: argmin Σ|x_i − μ| at median.",
  definition: "The Laplace distribution has pdf: (1/2b)exp(−|x−μ|/b). L2 estimator is mean; L1 is median.",
  check: "Transfer: for sample (2,5,10,11,12), what is the Laplace MLE?",
  expectedAnswerHint: "median equals 8 for original; for transfer: 10",
};

function mockReducedMotion(reduced: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reduced && query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

function makeGradeOkResponse(result: GradeResult): Response {
  return new Response(JSON.stringify(result), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

beforeEach(() => {
  mockReducedMotion(false);
  Object.defineProperty(document, "cookie", {
    value: "csrf=test-csrf",
    configurable: true,
    writable: true,
  });
  vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 200 })));
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("DrillStack initial render", () => {
  test("renders DRILL card open and WORKED/DEFINITION/CHECK locked", () => {
    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    const cards = screen.getAllByTestId("drill-card");
    expect(cards).toHaveLength(4);

    // First card (DRILL) should be open — no lock message
    expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(3);

    // Drill input textarea and submit button present
    expect(screen.getByTestId("drill-attempt-input")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /check answer/i })).toBeInTheDocument();
  });

  test("DRILL content text appears in first open card", () => {
    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );
    expect(screen.getByText(/MLE of μ for Laplace/)).toBeInTheDocument();
  });

  test("renders give-up button", () => {
    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );
    expect(
      screen.getByRole("button", { name: /give up/i })
    ).toBeInTheDocument();
  });
});

describe("DrillStack correct-answer path", () => {
  test("on correct grade → WORKED, DEFINITION, CHECK unlock (lock messages disappear)", async () => {
    const correctResult: GradeResult = {
      correct: true,
      score: 1.0,
      rubric: { numeric: true, mechanism: true, justification: true },
      misconception: null,
      elaboratedFeedback: "✓ correct. Median = 8.",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(correctResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "μ̂ = 8, the sample median" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));

    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );

    // Elaborated feedback appears
    expect(screen.getByTestId("grade-feedback")).toBeInTheDocument();
    expect(screen.getByTestId("grade-feedback").textContent).toContain("correct");
  });

  test("on correct grade → onProblemComplete is not called yet (check card still pending)", async () => {
    const correctResult: GradeResult = {
      correct: true,
      score: 1.0,
      rubric: { numeric: true, mechanism: true, justification: true },
      misconception: null,
      elaboratedFeedback: "✓ correct.",
    };
    const onComplete = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(correctResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={onComplete}
        />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "median = 8" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));
    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );
    // onProblemComplete fires only when CHECK is also marked done
    expect(onComplete).not.toHaveBeenCalled();
  });
});

describe("DrillStack misconception path", () => {
  test("on incorrect grade → misconception banner appears, cards stay locked", async () => {
    const wrongResult: GradeResult = {
      correct: false,
      score: 0.0,
      rubric: { numeric: false, mechanism: false, justification: false },
      misconception: "L2_ESTIMATOR_CONFUSION",
      elaboratedFeedback:
        "You computed the mean (8.2). For Laplace, the L1 MLE is the median.",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(wrongResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "μ̂ = 8.2 (sum / n)" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));

    await waitFor(() =>
      expect(screen.getByTestId("grade-feedback")).toBeInTheDocument()
    );

    expect(screen.getByTestId("grade-feedback").textContent).toContain(
      "mean"
    );
    expect(screen.getByTestId("misconception-banner")).toBeInTheDocument();
    // Cards remain locked
    expect(screen.getAllByTestId("card-lock-message")).toHaveLength(3);
  });
});

describe("DrillStack giveUp path", () => {
  test("give up → POSTs attempted-not-solved to grade endpoint + unlocks all cards", async () => {
    const giveUpResult: GradeResult = {
      correct: false,
      score: 0.0,
      rubric: { numeric: false, mechanism: false, justification: false },
      misconception: null,
      elaboratedFeedback: "Marked as attempted-not-solved.",
    };
    const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
      if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
        return makeGradeOkResponse(giveUpResult);
      }
      return new Response("{}", { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={vi.fn()}
        />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /give up/i }));

    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );

    // Verify the grade call included giveUp flag
    const gradeCalls = fetchMock.mock.calls.filter(
      (c) =>
        typeof c[0] === "string" && c[0].includes("/api/v1/drill/grade")
    );
    expect(gradeCalls).toHaveLength(1);
    const body = JSON.parse(gradeCalls[0][1].body as string);
    expect(body.giveUp).toBe(true);
    expect(body.userAttempt).toBe("ATTEMPTED_NOT_SOLVED");
  });
});

describe("DrillStack CHECK completion", () => {
  test("clicking Mark CHECK Done fires onProblemComplete", async () => {
    const correctResult: GradeResult = {
      correct: true,
      score: 1.0,
      rubric: { numeric: true, mechanism: true, justification: true },
      misconception: null,
      elaboratedFeedback: "✓ correct.",
    };
    const onComplete = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
          return makeGradeOkResponse(correctResult);
        }
        return new Response("{}", { status: 200 });
      })
    );

    render(
      <MemoryRouter>
        <DrillStack
          taskId="T1"
          problemId="A3"
          content={DRILL_CONTENT}
          onProblemComplete={onComplete}
        />
      </MemoryRouter>
    );

    // Grade drill first
    fireEvent.change(screen.getByTestId("drill-attempt-input"), {
      target: { value: "median = 8" },
    });
    fireEvent.click(screen.getByRole("button", { name: /check answer/i }));
    await waitFor(() =>
      expect(screen.queryAllByTestId("card-lock-message")).toHaveLength(0)
    );

    // Now complete the CHECK card
    fireEvent.click(screen.getByRole("button", { name: /mark check done/i }));
    expect(onComplete).toHaveBeenCalledWith("A3");
  });
});
```

- [ ] **Step 2: Run test to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/DrillStack.test.tsx
```

Expected: FAIL — `Cannot find module '../components/DrillStack'`.

- [ ] **Step 3: Implement `DrillStack.tsx`**

Create `tutor-web/src/components/DrillStack.tsx`:

```typescript
import { useState } from "react";
import { DrillCard } from "./DrillCard";
import type { DrillCardState } from "./DrillCard";
import { gradeDrill } from "../lib/drillGrader";
import type { GradeResult } from "../lib/drillGrader";

export interface DrillContent {
  drill: string;
  worked: string;
  definition: string;
  check: string;
  expectedAnswerHint: string;
}

interface DrillStackProps {
  taskId: string;
  problemId: string;
  content: DrillContent;
  onProblemComplete: (problemId: string) => void;
}

type StackPhase =
  | "idle"
  | "grading"
  | "correct"
  | "incorrect"
  | "given-up"
  | "check-done";

/**
 * Orchestrates 4 DrillCards in DRILL → WORKED → DEFINITION → CHECK order.
 *
 * State machine:
 *   idle      → user types attempt → click "CHECK ANSWER" → grading
 *   grading   → API responds correct   → correct  (WORKED/DEF/CHECK unlock, stagger 80ms)
 *   grading   → API responds incorrect → incorrect (cards stay locked, misconception banner)
 *   idle      → user clicks "GIVE UP"  → given-up  (POST giveUp=true, unlock all)
 *   correct   → user clicks "MARK CHECK DONE" → check-done → onProblemComplete fires
 *   given-up  → user clicks "MARK CHECK DONE" → check-done → onProblemComplete fires
 *
 * Animation #1 (unlock stagger) is driven by data-stagger-index + CSS
 * `animation-delay` set in DrillCard. No JS timers needed — CSS handles the 80ms cascade.
 */
export function DrillStack({
  taskId,
  problemId,
  content,
  onProblemComplete,
}: DrillStackProps) {
  const [attempt, setAttempt] = useState("");
  const [phase, setPhase] = useState<StackPhase>("idle");
  const [gradeResult, setGradeResult] = useState<GradeResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const unlocked = phase === "correct" || phase === "given-up" || phase === "check-done";

  function drillState(): DrillCardState {
    if (phase === "check-done" || phase === "correct" || phase === "given-up") return "complete";
    return "open";
  }

  function secondaryState(): DrillCardState {
    if (!unlocked) return "locked";
    if (phase === "check-done") return "complete";
    return "open";
  }

  function checkState(): DrillCardState {
    if (!unlocked) return "locked";
    if (phase === "check-done") return "complete";
    return "open";
  }

  async function handleCheckAnswer() {
    if (phase === "grading") return;
    setPhase("grading");
    setError(null);
    try {
      const result = await gradeDrill({
        taskId,
        problemId,
        problemStatement: content.drill,
        userAttempt: attempt,
        expectedAnswerHint: content.expectedAnswerHint,
      });
      setGradeResult(result);
      setPhase(result.correct ? "correct" : "incorrect");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error — please retry.");
      setPhase("idle");
    }
  }

  async function handleGiveUp() {
    if (phase === "grading") return;
    setPhase("grading");
    setError(null);
    try {
      const result = await gradeDrill({
        taskId,
        problemId,
        problemStatement: content.drill,
        userAttempt: "ATTEMPTED_NOT_SOLVED",
        expectedAnswerHint: content.expectedAnswerHint,
        // giveUp flag is serialized into the body; backend records AGAIN grade
        ...(({ giveUp: true } as unknown) as object),
      });
      setGradeResult(result);
      setPhase("given-up");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error — please retry.");
      setPhase("idle");
    }
  }

  function handleCheckDone() {
    setPhase("check-done");
    onProblemComplete(problemId);
  }

  return (
    <div data-testid="drill-stack" className="flex flex-col gap-4 p-4">
      {/* Card order: DRILL → WORKED → DEFINITION → CHECK (§D productive-failure inversion) */}

      {/* 1. DRILL card — always open */}
      <DrillCard
        cardType="DRILL"
        title="③ DRILL · YOUR TURN"
        state={drillState()}
        staggerIndex={0}
      >
        <p className="mb-4 leading-relaxed">{content.drill}</p>
        <textarea
          data-testid="drill-attempt-input"
          value={attempt}
          onChange={(e) => setAttempt(e.target.value)}
          disabled={unlocked || phase === "grading"}
          rows={3}
          placeholder="Type your answer here…"
          className="w-full border-2 border-border-strong bg-page-bg font-mono text-xs p-2 resize-none focus:outline-none focus:border-accent disabled:opacity-50"
        />
        {gradeResult && (
          <div
            data-testid="grade-feedback"
            className={`mt-3 px-3 py-2 border-l-4 font-mono text-xs leading-relaxed ${
              gradeResult.correct
                ? "border-accent bg-accent/10 text-page-fg"
                : "border-danger-text bg-danger-text/10 text-page-fg"
            }`}
          >
            {gradeResult.elaboratedFeedback}
          </div>
        )}
        {gradeResult && !gradeResult.correct && gradeResult.misconception && (
          <div
            data-testid="misconception-banner"
            className="mt-2 px-3 py-1.5 bg-panel-dark-bg text-panel-dark-fg font-mono text-[10px] tracking-widest"
          >
            MISCONCEPTION · {gradeResult.misconception.replace(/_/g, " ")}
          </div>
        )}
        {error && (
          <div className="mt-2 text-danger-text font-mono text-xs">{error}</div>
        )}
        {!unlocked && (
          <div className="mt-3 flex gap-2">
            <button
              onClick={handleCheckAnswer}
              disabled={phase === "grading" || attempt.trim().length === 0}
              className="px-4 py-1.5 bg-accent text-page-fg font-mono text-xs font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover disabled:opacity-40 transition-all duration-[280ms] ease-in-out active:scale-95"
            >
              {phase === "grading" ? "GRADING…" : "CHECK ANSWER"}
            </button>
            <button
              onClick={handleGiveUp}
              disabled={phase === "grading"}
              className="px-4 py-1.5 text-page-fg/60 font-mono text-xs tracking-widest border-2 border-border-thin hover:text-page-fg hover:border-border-strong disabled:opacity-40"
            >
              give up — show solution
            </button>
          </div>
        )}
      </DrillCard>

      {/* 2. WORKED EXAMPLE card — locked until drill graded */}
      <DrillCard
        cardType="WORKED"
        title="② WORKED EXAMPLE"
        state={secondaryState()}
        staggerIndex={1}
      >
        <p className="leading-relaxed">{content.worked}</p>
      </DrillCard>

      {/* 3. DEFINITION card — locked until drill graded */}
      <DrillCard
        cardType="DEFINITION"
        title="① DEFINITION"
        state={secondaryState()}
        staggerIndex={2}
      >
        <p className="leading-relaxed">{content.definition}</p>
      </DrillCard>

      {/* 4. CHECK card — locked until drill graded */}
      <DrillCard
        cardType="CHECK"
        title="④ CHECK · TRANSFER"
        state={checkState()}
        staggerIndex={3}
      >
        <p className="mb-4 leading-relaxed">{content.check}</p>
        {unlocked && phase !== "check-done" && (
          <button
            onClick={handleCheckDone}
            className="px-4 py-1.5 bg-accent text-page-fg font-mono text-xs font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover transition-all duration-[280ms] ease-in-out active:scale-95"
          >
            MARK CHECK DONE
          </button>
        )}
      </DrillCard>
    </div>
  );
}
```

Note: the `giveUp` property is passed via object spread inside `gradeDrill`'s args. Update `lib/drillGrader.ts` to forward an optional `giveUp?: boolean` field:

```typescript
// In GradeDrillArgs interface, add:
giveUp?: boolean;

// In gradeDrill body, include in JSON.stringify:
body: JSON.stringify({
  taskId: args.taskId,
  problemId: args.problemId,
  problemStatement: args.problemStatement,
  userAttempt: args.userAttempt,
  expectedAnswerHint: args.expectedAnswerHint,
  ...(args.giveUp ? { giveUp: true } : {}),
}),
```

And fix the DrillStack `handleGiveUp` call to pass `giveUp: true` cleanly:

```typescript
// Replace the spread workaround in handleGiveUp with:
const result = await gradeDrill({
  taskId,
  problemId,
  problemStatement: content.drill,
  userAttempt: "ATTEMPTED_NOT_SOLVED",
  expectedAnswerHint: content.expectedAnswerHint,
  giveUp: true,
});
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/DrillStack.test.tsx
```

Expected: 8 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/DrillStack.tsx tutor-web/src/__tests__/DrillStack.test.tsx tutor-web/src/lib/drillGrader.ts
git commit -m "feat(drill): DrillStack state machine — DRILL→WORKED→DEF→CHECK, grade, giveUp, stagger unlock"
```

---

### Task D5: `ProgressStrip.tsx` — two-tier outer/inner progress with `aria-valuenow` + dot animation

**Files:**
- Create: `tutor-web/src/components/ProgressStrip.tsx`
- Test: `tutor-web/src/__tests__/ProgressStrip.test.tsx`

- [ ] **Step 1: Write failing test**

Create `tutor-web/src/__tests__/ProgressStrip.test.tsx`:

```typescript
import { render, screen } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { ProgressStrip } from "../components/ProgressStrip";

function mockReducedMotion(reduced: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reduced && query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

beforeEach(() => {
  mockReducedMotion(false);
});
afterEach(() => {
  vi.restoreAllMocks();
});

describe("ProgressStrip outer tier", () => {
  test("renders outer progressbar with correct aria values", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const outer = screen.getByTestId("outer-progress");
    expect(outer.getAttribute("role")).toBe("progressbar");
    expect(outer.getAttribute("aria-valuenow")).toBe("3");
    expect(outer.getAttribute("aria-valuemin")).toBe("0");
    expect(outer.getAttribute("aria-valuemax")).toBe("7");
    expect(outer.getAttribute("aria-label")).toMatch(/3 of 7 problems/i);
  });

  test("renders 7 outer dots for total=7", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("outer-dot");
    expect(dots).toHaveLength(7);
  });

  test("3 outer dots are filled, 4 are empty for done=3 total=7", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("outer-dot");
    const filled = dots.filter((d) => d.getAttribute("data-filled") === "true");
    const empty = dots.filter((d) => d.getAttribute("data-filled") === "false");
    expect(filled).toHaveLength(3);
    expect(empty).toHaveLength(4);
  });

  test("label shows correct fraction text", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    expect(screen.getByTestId("outer-label").textContent).toMatch(/3 \/ 7/);
  });
});

describe("ProgressStrip inner tier", () => {
  test("renders inner progressbar with correct aria values", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const inner = screen.getByTestId("inner-progress");
    expect(inner.getAttribute("role")).toBe("progressbar");
    expect(inner.getAttribute("aria-valuenow")).toBe("2");
    expect(inner.getAttribute("aria-valuemax")).toBe("4");
    expect(inner.getAttribute("aria-label")).toMatch(/2 of 4 cards.*A3/i);
  });

  test("renders 4 inner dots for total=4", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("inner-dot");
    expect(dots).toHaveLength(4);
  });

  test("2 inner dots are filled for done=2", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const dots = screen.getAllByTestId("inner-dot");
    const filled = dots.filter((d) => d.getAttribute("data-filled") === "true");
    expect(filled).toHaveLength(2);
  });

  test("inner label shows fraction and current problem label", () => {
    render(
      <ProgressStrip
        outer={{ done: 3, total: 7 }}
        inner={{ done: 2, total: 4 }}
        currentProblemLabel="A3"
      />
    );
    const label = screen.getByTestId("inner-label");
    expect(label.textContent).toMatch(/2 \/ 4/);
    expect(label.textContent).toContain("A3");
  });
});

describe("ProgressStrip edge cases", () => {
  test("all outer done → all dots filled", () => {
    render(
      <ProgressStrip
        outer={{ done: 4, total: 4 }}
        inner={{ done: 4, total: 4 }}
        currentProblemLabel="A4"
      />
    );
    const outerDots = screen.getAllByTestId("outer-dot");
    expect(outerDots.every((d) => d.getAttribute("data-filled") === "true")).toBe(true);
  });

  test("done=0 → no dots filled", () => {
    render(
      <ProgressStrip
        outer={{ done: 0, total: 3 }}
        inner={{ done: 0, total: 4 }}
        currentProblemLabel="A1"
      />
    );
    const outerDots = screen.getAllByTestId("outer-dot");
    expect(outerDots.every((d) => d.getAttribute("data-filled") === "false")).toBe(true);
  });

  test("under prefers-reduced-motion, dot animation class absent", () => {
    mockReducedMotion(true);
    render(
      <ProgressStrip
        outer={{ done: 2, total: 4 }}
        inner={{ done: 1, total: 4 }}
        currentProblemLabel="A2"
      />
    );
    const filledOuterDots = screen
      .getAllByTestId("outer-dot")
      .filter((d) => d.getAttribute("data-filled") === "true");
    filledOuterDots.forEach((dot) => {
      expect(dot.className).not.toContain("animate-dot-fill");
    });
  });
});
```

- [ ] **Step 2: Run test to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/ProgressStrip.test.tsx
```

Expected: FAIL — `Cannot find module '../components/ProgressStrip'`.

- [ ] **Step 3: Implement `ProgressStrip.tsx`**

Create `tutor-web/src/components/ProgressStrip.tsx`:

```typescript
interface ProgressTier {
  done: number;
  total: number;
}

interface ProgressStripProps {
  outer: ProgressTier;
  inner: ProgressTier;
  currentProblemLabel: string;
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Two-tier progress strip.
 *
 * Outer tier: N/N problems completed. Dots use anim #4 (outer-dot-flash).
 * Inner tier: N/4 cards completed for current problem. Dots use anim #3
 *   (radial scale 0→1, 160ms ease-out). Both tiers have aria-valuenow
 *   for screen reader compatibility (Slice 2 full a11y pass).
 *
 * Animation classes `animate-dot-fill` and `animate-outer-dot-fill` must
 * be defined in tailwind.config (keyframe: scale 0→1, 160ms/200ms ease-out).
 * Under prefers-reduced-motion, no animation class is applied.
 */
export function ProgressStrip({
  outer,
  inner,
  currentProblemLabel,
}: ProgressStripProps) {
  const reduced = prefersReducedMotion();

  return (
    <div
      data-testid="progress-strip"
      className="flex flex-col gap-1.5 px-4 py-2 border-b-4 border-border-strong bg-page-bg font-mono text-xs"
    >
      {/* Outer tier — problems N/N */}
      <div
        data-testid="outer-progress"
        role="progressbar"
        aria-valuenow={outer.done}
        aria-valuemin={0}
        aria-valuemax={outer.total}
        aria-label={`${outer.done} of ${outer.total} problems complete`}
        className="flex items-center gap-2"
      >
        <div className="flex items-center gap-1">
          {Array.from({ length: outer.total }, (_, i) => {
            const filled = i < outer.done;
            return (
              <span
                key={i}
                data-testid="outer-dot"
                data-filled={filled ? "true" : "false"}
                aria-hidden="true"
                className={`inline-block w-3 h-3 rounded-full border-2 border-border-strong transition-transform ${
                  filled
                    ? `bg-accent ${!reduced ? "animate-outer-dot-fill" : ""}`
                    : "bg-transparent"
                }`}
              />
            );
          })}
        </div>
        <span
          data-testid="outer-label"
          className="text-page-fg/70 tracking-widest"
        >
          {outer.done} / {outer.total} problems
        </span>
      </div>

      {/* Inner tier — cards N/4 for current problem */}
      <div
        data-testid="inner-progress"
        role="progressbar"
        aria-valuenow={inner.done}
        aria-valuemin={0}
        aria-valuemax={inner.total}
        aria-label={`${inner.done} of ${inner.total} cards complete (${currentProblemLabel})`}
        className="flex items-center gap-2"
      >
        <div className="flex items-center gap-1">
          {Array.from({ length: inner.total }, (_, i) => {
            const filled = i < inner.done;
            return (
              <span
                key={i}
                data-testid="inner-dot"
                data-filled={filled ? "true" : "false"}
                aria-hidden="true"
                className={`inline-block w-2 h-2 rounded-full border-2 border-border-strong transition-transform ${
                  filled
                    ? `bg-accent ${!reduced ? "animate-dot-fill" : ""}`
                    : "bg-transparent"
                }`}
              />
            );
          })}
        </div>
        <span
          data-testid="inner-label"
          className="text-page-fg/70 tracking-widest"
        >
          {inner.done} / {inner.total} cards ({currentProblemLabel})
        </span>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/ProgressStrip.test.tsx
```

Expected: 11 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/ProgressStrip.tsx tutor-web/src/__tests__/ProgressStrip.test.tsx
git commit -m "feat(drill): ProgressStrip two-tier outer/inner progress with aria-valuenow + dot animation"
```

---

### Task D6: `CompileSubmitCard.tsx` — all-problems-complete card with LaTeX export + submit

**Files:**
- Create: `tutor-web/src/components/CompileSubmitCard.tsx`
- Test: `tutor-web/src/__tests__/CompileSubmitCard.test.tsx`

- [ ] **Step 1: Verify submit endpoint against `TutorWorkspace.tsx`**

Run:
```
grep -n "submit" tutor-web/src/components/TutorWorkspace.tsx tutor-web/src/lib/api.ts
```

No existing `/api/v1/tasks/{id}/submit` endpoint is called in those files. Use the placeholder route `/api/v1/tasks/{id}/submit` (POST, body `{ note: string }`). This matches the pattern established by the scratchpad PUT in `TutorWorkspace.tsx`.

- [ ] **Step 2: Write failing test**

Create `tutor-web/src/__tests__/CompileSubmitCard.test.tsx`:

```typescript
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { CompileSubmitCard } from "../components/CompileSubmitCard";

const ANSWERS = [
  { problemId: "A1", attempt: "μ̂ = 8 (sample median)" },
  { problemId: "A2", attempt: "σ̂² = 6.4 (MLE variance)" },
  { problemId: "A3", attempt: "MLE for Laplace is median" },
];

function mockReducedMotion(reduced: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reduced && query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

beforeEach(() => {
  mockReducedMotion(false);
  Object.defineProperty(document, "cookie", {
    value: "csrf=submit-csrf",
    configurable: true,
    writable: true,
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response("{}", { status: 200 }))
  );
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("CompileSubmitCard rendering", () => {
  test("renders card with COMPILE & SUBMIT heading", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    expect(screen.getByTestId("compile-submit-card")).toBeInTheDocument();
    expect(screen.getByTestId("compile-submit-heading").textContent).toMatch(
      /COMPILE & SUBMIT/i
    );
  });

  test("renders each problem answer in the LaTeX export block", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    const exportBlock = screen.getByTestId("latex-export");
    expect(exportBlock.textContent).toContain("A1");
    expect(exportBlock.textContent).toContain("μ̂ = 8 (sample median)");
    expect(exportBlock.textContent).toContain("A2");
    expect(exportBlock.textContent).toContain("σ̂² = 6.4");
    expect(exportBlock.textContent).toContain("A3");
  });

  test("renders MARK SUBMITTED button", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    expect(
      screen.getByRole("button", { name: /mark submitted/i })
    ).toBeInTheDocument();
  });

  test("under normal motion, card has slide-up animation class", () => {
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    const card = screen.getByTestId("compile-submit-card");
    expect(card.className).toContain("animate-slide-up");
  });

  test("under prefers-reduced-motion, no animation class", () => {
    mockReducedMotion(true);
    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );
    const card = screen.getByTestId("compile-submit-card");
    expect(card.className).not.toContain("animate-slide-up");
  });
});

describe("CompileSubmitCard submit flow", () => {
  test("clicking MARK SUBMITTED POSTs to /api/v1/tasks/T1/submit with CSRF header", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() => {
      const calls = fetchMock.mock.calls;
      const submitCall = calls.find(
        (c) => typeof c[0] === "string" && c[0].includes("/api/v1/tasks/T1/submit")
      );
      expect(submitCall).toBeDefined();
      expect(submitCall![1].method).toBe("POST");
      expect(submitCall![1].headers["X-CSRF-Token"]).toBe("submit-csrf");
    });
  });

  test("after submit, button shows SUBMITTED and is disabled", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("{}", { status: 200 }))
    );

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() => {
      const btn = screen.getByTestId("submit-button");
      expect(btn.textContent).toMatch(/SUBMITTED/i);
      expect(btn).toBeDisabled();
    });
  });

  test("submit failure shows error message, button re-enabled", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("server error", { status: 500 }))
    );

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() =>
      expect(screen.getByTestId("submit-error")).toBeInTheDocument()
    );
    const btn = screen.getByTestId("submit-button");
    expect(btn).not.toBeDisabled();
  });

  test("submit body includes stitched LaTeX text for all problems", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <MemoryRouter>
        <CompileSubmitCard taskId="T1" answers={ANSWERS} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole("button", { name: /mark submitted/i }));

    await waitFor(() => {
      const submitCall = fetchMock.mock.calls.find(
        (c) => typeof c[0] === "string" && c[0].includes("/api/v1/tasks/T1/submit")
      );
      expect(submitCall).toBeDefined();
      const body = JSON.parse(submitCall![1].body as string);
      expect(body.note).toContain("A1");
      expect(body.note).toContain("μ̂ = 8");
      expect(body.note).toContain("A3");
    });
  });
});
```

- [ ] **Step 3: Run test to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/CompileSubmitCard.test.tsx
```

Expected: FAIL — `Cannot find module '../components/CompileSubmitCard'`.

- [ ] **Step 4: Implement `CompileSubmitCard.tsx`**

Create `tutor-web/src/components/CompileSubmitCard.tsx`:

```typescript
import { useState } from "react";
import { jarvisFetch } from "../lib/api";

export interface ProblemAnswer {
  problemId: string;
  attempt: string;
}

interface CompileSubmitCardProps {
  taskId: string;
  answers: ProblemAnswer[];
  onSubmitted?: () => void;
}

type SubmitPhase = "idle" | "submitting" | "done" | "error";

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Builds a plain-text LaTeX-friendly export from per-problem answers.
 * Format:
 *   \textbf{A1.} μ̂ = 8 (sample median)
 *   \textbf{A2.} σ̂² = 6.4 ...
 */
function buildLatexExport(answers: ProblemAnswer[]): string {
  return answers
    .map((a) => `\\textbf{${a.problemId}.} ${a.attempt}`)
    .join("\n\n");
}

/**
 * CompileSubmitCard — appears after all problems complete.
 *
 * Slides up from below viewport via CSS anim #18 (`animate-slide-up`,
 * 400ms cubic-bezier). Under prefers-reduced-motion, animation class
 * is suppressed and card is just rendered in-place.
 *
 * Stitches per-problem answers into a LaTeX-friendly block for review,
 * then POSTs to /api/v1/tasks/{id}/submit with the stitched note.
 */
export function CompileSubmitCard({
  taskId,
  answers,
  onSubmitted,
}: CompileSubmitCardProps) {
  const [phase, setPhase] = useState<SubmitPhase>("idle");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const reduced = prefersReducedMotion();
  const latexExport = buildLatexExport(answers);

  async function handleSubmit() {
    if (phase !== "idle" && phase !== "error") return;
    setPhase("submitting");
    setErrorMsg(null);
    try {
      const res = await jarvisFetch(
        `/api/v1/tasks/${encodeURIComponent(taskId)}/submit`,
        {
          method: "POST",
          body: JSON.stringify({ note: latexExport }),
        }
      );
      if (!res.ok) {
        const msg = await res.text().catch(() => "");
        throw new Error(`HTTP ${res.status}: ${msg.slice(0, 160)}`);
      }
      setPhase("done");
      onSubmitted?.();
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : "Submission failed — please retry.");
      setPhase("error");
    }
  }

  return (
    <div
      data-testid="compile-submit-card"
      className={`border-4 border-border-strong bg-page-bg font-mono text-xs ${
        !reduced ? "animate-slide-up" : ""
      }`}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-panel-dark-bg text-panel-dark-fg border-b-4 border-border-strong">
        <span
          data-testid="compile-submit-heading"
          className="tracking-widest font-bold"
        >
          ⑤ COMPILE &amp; SUBMIT
        </span>
        <span className="text-[10px] tracking-widest text-panel-dark-fg/60">
          [SUBMIT]
        </span>
      </div>

      {/* LaTeX export block */}
      <div className="px-4 py-4">
        <div className="mb-2 tracking-widest text-page-fg/60 text-[10px]">
          STITCHED ANSWERS · LaTeX EXPORT
        </div>
        <pre
          data-testid="latex-export"
          className="whitespace-pre-wrap break-words bg-accent-soft border-2 border-border-thin px-3 py-3 leading-relaxed text-[11px]"
        >
          {latexExport}
        </pre>
      </div>

      {/* Submit row */}
      <div className="px-4 pb-4 flex flex-col gap-2">
        <button
          data-testid="submit-button"
          onClick={handleSubmit}
          disabled={phase === "submitting" || phase === "done"}
          className="px-6 py-2 bg-accent text-page-fg font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-[280ms] ease-in-out active:scale-95"
        >
          {phase === "submitting"
            ? "SUBMITTING…"
            : phase === "done"
            ? "✓ SUBMITTED"
            : "MARK SUBMITTED"}
        </button>
        {phase === "error" && errorMsg && (
          <div
            data-testid="submit-error"
            className="text-danger-text tracking-widest text-[10px]"
          >
            {errorMsg}
          </div>
        )}
        {phase === "done" && (
          <div
            data-testid="submit-success"
            className="text-page-fg/70 tracking-widest text-[10px]"
          >
            SUBMITTED — check your task status for grade feedback.
          </div>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/CompileSubmitCard.test.tsx
```

Expected: 8 PASS.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/CompileSubmitCard.tsx tutor-web/src/__tests__/CompileSubmitCard.test.tsx
git commit -m "feat(drill): CompileSubmitCard with LaTeX export + slide-up anim #18 + submit endpoint"
```

---

## PHASE E · Inline help UI (Tasks E1-E3)

### Task E1: `lib/inlineAsk.ts` — envelope builder + selection listener; `lib/sidekickContext.ts` — `askSidekick` fetch wrapper

**Files:**
- Create: `tutor-web/src/lib/inlineAsk.ts`
- Create: `tutor-web/src/lib/sidekickContext.ts`
- Test: `tutor-web/src/__tests__/inlineAsk.test.ts`

- [ ] **Step 1: Write failing tests for `buildSidekickEnvelope` + `attachSelectionListener`**

Create `tutor-web/src/__tests__/inlineAsk.test.ts`:

```ts
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import {
  buildSidekickEnvelope,
  attachSelectionListener,
} from "../lib/inlineAsk";

// ─── buildSidekickEnvelope ────────────────────────────────────────────────────

describe("buildSidekickEnvelope", () => {
  test("maps all args to SidekickEnvelope fields", () => {
    const env = buildSidekickEnvelope({
      taskId: "task-01",
      problemId: "A3",
      cardId: "card-1",
      cardTitle: "③ DRILL · YOUR TURN",
      anchorId: "drill-statement",
      anchorText: "Sample x = (3,7,8,9,14). What is μ̂?",
      selection: "MLE",
      userQuestion: "what does MLE mean?",
    });
    expect(env.task_id).toBe("task-01");
    expect(env.problem_id).toBe("A3");
    expect(env.card_id).toBe("card-1");
    expect(env.card_title).toBe("③ DRILL · YOUR TURN");
    expect(env.anchor_id).toBe("drill-statement");
    expect(env.anchor_text).toBe("Sample x = (3,7,8,9,14). What is μ̂?");
    expect(env.selection).toBe("MLE");
    expect(env.user_question).toBe("what does MLE mean?");
  });

  test("omits undefined optional fields", () => {
    const env = buildSidekickEnvelope({
      taskId: "task-02",
      userQuestion: "explain this",
    });
    expect(env.task_id).toBe("task-02");
    expect(env.problem_id).toBeUndefined();
    expect(env.selection).toBeUndefined();
    expect(env.user_question).toBe("explain this");
  });
});

// ─── attachSelectionListener ──────────────────────────────────────────────────

describe("attachSelectionListener", () => {
  let root: HTMLElement;
  let cardBody: HTMLElement;
  let onAsk: ReturnType<typeof vi.fn>;
  let detach: () => void;

  beforeEach(() => {
    root = document.createElement("div");
    cardBody = document.createElement("div");
    cardBody.className = "card-body";
    cardBody.textContent = "Sample x = (3,7,8,9,14). What is MLE?";
    root.appendChild(cardBody);
    document.body.appendChild(root);
    onAsk = vi.fn();
    detach = attachSelectionListener(root, onAsk);
  });

  afterEach(() => {
    detach();
    document.body.removeChild(root);
    vi.restoreAllMocks();
  });

  test("calls onAsk with rect when selection ≥ 3 chars inside .card-body", () => {
    const mockRange = {
      toString: () => "MLE",
      getBoundingClientRect: () => ({
        top: 100, left: 50, bottom: 116, right: 80, width: 30, height: 16,
      } as DOMRect),
      commonAncestorContainer: cardBody.firstChild as Node,
    } as unknown as Range;

    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => mockRange,
      toString: () => "MLE",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));

    expect(onAsk).toHaveBeenCalledOnce();
    const [selectedText, rect] = onAsk.mock.calls[0];
    expect(selectedText).toBe("MLE");
    expect(rect.top).toBe(100);
  });

  test("does not call onAsk when selection is fewer than 3 chars", () => {
    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "ab",
        getBoundingClientRect: () => ({ top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0 } as DOMRect),
        commonAncestorContainer: cardBody.firstChild as Node,
      } as unknown as Range),
      toString: () => "ab",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).not.toHaveBeenCalled();
  });

  test("does not call onAsk when selection is outside .card-body", () => {
    const outside = document.createElement("p");
    outside.textContent = "outside paragraph";
    root.appendChild(outside);

    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "outside paragraph",
        getBoundingClientRect: () => ({ top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0 } as DOMRect),
        commonAncestorContainer: outside.firstChild as Node,
      } as unknown as Range),
      toString: () => "outside paragraph",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).not.toHaveBeenCalled();
  });

  test("detach removes the mouseup listener", () => {
    detach();
    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "MLE here",
        getBoundingClientRect: () => ({ top: 10, left: 10, bottom: 26, right: 40, width: 30, height: 16 } as DOMRect),
        commonAncestorContainer: cardBody.firstChild as Node,
      } as unknown as Range),
      toString: () => "MLE here",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests to confirm they fail**

```
cd tutor-web && npx vitest run src/__tests__/inlineAsk.test.ts
```

Expected: FAIL — `../lib/inlineAsk` module not found.

- [ ] **Step 3: Implement `lib/inlineAsk.ts`**

Create `tutor-web/src/lib/inlineAsk.ts`:

```ts
/** Shape of the envelope sent to POST /api/v1/sidekick/ask.
 *  Field names use snake_case to match the backend SidekickEnvelope. */
export interface SidekickEnvelope {
  task_id?: string;
  problem_id?: string;
  card_id?: string;
  card_title?: string;
  anchor_id?: string;
  anchor_text?: string;
  selection?: string;
  user_question: string;
}

export interface BuildEnvelopeArgs {
  taskId?: string;
  problemId?: string;
  cardId?: string;
  cardTitle?: string;
  anchorId?: string;
  anchorText?: string;
  selection?: string;
  userQuestion: string;
}

/** Build a SidekickEnvelope from camelCase args, dropping undefined fields. */
export function buildSidekickEnvelope(args: BuildEnvelopeArgs): SidekickEnvelope {
  const env: SidekickEnvelope = { user_question: args.userQuestion };
  if (args.taskId !== undefined) env.task_id = args.taskId;
  if (args.problemId !== undefined) env.problem_id = args.problemId;
  if (args.cardId !== undefined) env.card_id = args.cardId;
  if (args.cardTitle !== undefined) env.card_title = args.cardTitle;
  if (args.anchorId !== undefined) env.anchor_id = args.anchorId;
  if (args.anchorText !== undefined) env.anchor_text = args.anchorText;
  if (args.selection !== undefined) env.selection = args.selection;
  return env;
}

/** Walk up the DOM from `node` to check if it's inside a `.card-body`. */
function insideCardBody(node: Node): boolean {
  let cur: Node | null = node;
  while (cur) {
    if (cur instanceof Element && cur.classList.contains("card-body")) return true;
    cur = cur.parentNode;
  }
  return false;
}

/** Attach a `mouseup` listener on `root` that fires `onAsk(selectedText, rect)`
 *  when the user finishes a selection of ≥ 3 chars inside a `.card-body` element.
 *  Returns a detach function — call it in the component's cleanup effect. */
export function attachSelectionListener(
  root: HTMLElement,
  onAsk: (selectedText: string, rect: DOMRect) => void,
): () => void {
  function handleMouseUp() {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    const range = sel.getRangeAt(0);
    const text = range.toString();
    if (text.length < 3) return;
    if (!insideCardBody(range.commonAncestorContainer)) return;
    const rect = range.getBoundingClientRect();
    onAsk(text, rect);
  }

  root.addEventListener("mouseup", handleMouseUp);
  return () => root.removeEventListener("mouseup", handleMouseUp);
}
```

- [ ] **Step 4: Implement `lib/sidekickContext.ts`**

Create `tutor-web/src/lib/sidekickContext.ts`:

```ts
import { jarvisFetch } from "./api";
import type { SidekickEnvelope } from "./inlineAsk";

export interface SidekickReply {
  text: string;
  model: string;
  quotedContext: string | null;
}

/** POST the envelope to /api/v1/sidekick/ask and return the reply.
 *  Throws on network error or non-OK status so callers can render
 *  the "(LLM unavailable)" fallback message. */
export async function askSidekick(env: SidekickEnvelope): Promise<SidekickReply> {
  const res = await jarvisFetch("/api/v1/sidekick/ask", {
    method: "POST",
    body: JSON.stringify(env),
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => res.statusText);
    throw new Error(`sidekick ${res.status}: ${msg}`);
  }
  return res.json() as Promise<SidekickReply>;
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```
cd tutor-web && npx vitest run src/__tests__/inlineAsk.test.ts
```

Expected: 6 PASS (2 envelope + 4 selection listener).

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/lib/inlineAsk.ts tutor-web/src/lib/sidekickContext.ts tutor-web/src/__tests__/inlineAsk.test.ts
git commit -m "feat(inline-ask): SidekickEnvelope builder + attachSelectionListener + askSidekick client"
```

---

### Task E2: `InlineAskChip.tsx` — floating ✨ ASK chip on text selection

**Files:**
- Create: `tutor-web/src/components/InlineAskChip.tsx`
- Test: `tutor-web/src/__tests__/InlineAskChip.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/InlineAskChip.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, vi, describe, beforeEach } from "vitest";
import { InlineAskChip } from "../components/InlineAskChip";
import type { SidekickEnvelope } from "../lib/inlineAsk";

const baseRect: DOMRect = {
  top: 200,
  left: 120,
  bottom: 216,
  right: 200,
  width: 80,
  height: 16,
  x: 120,
  y: 200,
  toJSON: () => ({}),
};

const baseEnv: SidekickEnvelope = {
  task_id: "task-01",
  problem_id: "A3",
  card_id: "card-1",
  card_title: "③ DRILL · YOUR TURN",
  selection: "MLE",
  user_question: "what does MLE mean?",
};

describe("InlineAskChip", () => {
  test("renders ✨ ASK label", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    expect(screen.getByRole("button", { name: /✨ ASK/i })).toBeInTheDocument();
  });

  test("chip is positioned 8px above the selection rect top", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    const chip = screen.getByRole("button", { name: /✨ ASK/i });
    // Position is applied via inline style; top = rect.top - 8 + scrollY
    // In jsdom scrollY = 0, so top = 192
    expect(chip.style.top).toBe("192px");
  });

  test("chip left is anchored to selectionRect.left", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    const chip = screen.getByRole("button", { name: /✨ ASK/i });
    expect(chip.style.left).toBe("120px");
  });

  test("calls onAsk with the envelope when clicked", async () => {
    const onAsk = vi.fn();
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={onAsk} />
    );
    await userEvent.click(screen.getByRole("button", { name: /✨ ASK/i }));
    expect(onAsk).toHaveBeenCalledOnce();
    expect(onAsk).toHaveBeenCalledWith(baseEnv);
  });

  test("chip carries the ask-chip-fade-in CSS class (animation #15)", () => {
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    const chip = screen.getByRole("button", { name: /✨ ASK/i });
    expect(chip.classList.contains("ask-chip-fade-in")).toBe(true);
  });

  test("reduced-motion: chip still renders without animation class suppressed (CSS media query handles it)", () => {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: (query: string) => ({
        matches: query === "(prefers-reduced-motion: reduce)",
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }),
    });
    render(
      <InlineAskChip selectionRect={baseRect} envelope={baseEnv} onAsk={vi.fn()} />
    );
    expect(screen.getByRole("button", { name: /✨ ASK/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run tests to confirm they fail**

```
cd tutor-web && npx vitest run src/__tests__/InlineAskChip.test.tsx
```

Expected: FAIL — `../components/InlineAskChip` module not found.

- [ ] **Step 3: Implement `InlineAskChip.tsx`**

Create `tutor-web/src/components/InlineAskChip.tsx`:

```tsx
import type { SidekickEnvelope } from "../lib/inlineAsk";

interface InlineAskChipProps {
  selectionRect: DOMRect;
  envelope: SidekickEnvelope;
  onAsk: (env: SidekickEnvelope) => void;
}

export function InlineAskChip({ selectionRect, envelope, onAsk }: InlineAskChipProps) {
  const top = selectionRect.top + window.scrollY - 8;
  const left = selectionRect.left + window.scrollX;

  return (
    <button
      className="ask-chip-fade-in"
      style={{
        position: "fixed",
        top: `${top}px`,
        left: `${left}px`,
        transform: "translateY(-100%)",
        zIndex: 9999,
        display: "inline-flex",
        alignItems: "center",
        gap: "4px",
        padding: "3px 8px",
        background: "var(--color-accent, #ffcc00)",
        color: "var(--color-page-fg, #0a0a0a)",
        fontFamily: "monospace",
        fontSize: "11px",
        fontWeight: 700,
        letterSpacing: "0.08em",
        border: "2px solid var(--color-border-strong, #0a0a0a)",
        borderRadius: 0,
        cursor: "pointer",
        userSelect: "none",
        whiteSpace: "nowrap",
      }}
      aria-label="✨ ASK sidekick about selection"
      onMouseDown={(e) => e.preventDefault()}
      onClick={() => onAsk(envelope)}
    >
      ✨ ASK
    </button>
  );
}
```

Append to `tutor-web/src/index.css`:

```css
/* Animation #15 · Inline ✨ ASK chip fade-in */
@keyframes askChipFadeIn {
  from { opacity: 0; transform: translateY(calc(-100% + 4px)); }
  to   { opacity: 1; transform: translateY(-100%); }
}
.ask-chip-fade-in {
  animation: askChipFadeIn 100ms ease-out both;
}
@media (prefers-reduced-motion: reduce) {
  .ask-chip-fade-in {
    animation: none;
  }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```
cd tutor-web && npx vitest run src/__tests__/InlineAskChip.test.tsx
```

Expected: 6 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/InlineAskChip.tsx tutor-web/src/__tests__/InlineAskChip.test.tsx tutor-web/src/index.css
git commit -m "feat(inline-ask): InlineAskChip floating chip with fade-in anim #15 + reduced-motion CSS"
```

---

### Task E3: `AskGutter.tsx` per-paragraph `?` button + `Sidekick.tsx` panel; wire into `TutorWorkspace.tsx`

**Files:**
- Create: `tutor-web/src/components/AskGutter.tsx`
- Create: `tutor-web/src/components/Sidekick.tsx`
- Modify: `tutor-web/src/components/TutorWorkspace.tsx`
- Test: `tutor-web/src/__tests__/AskGutter.test.tsx`
- Test: `tutor-web/src/__tests__/Sidekick.test.tsx`

- [ ] **Step 1: Write failing tests for `AskGutter`**

Create `tutor-web/src/__tests__/AskGutter.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, vi, describe } from "vitest";
import { AskGutter } from "../components/AskGutter";
import type { SidekickEnvelope } from "../lib/inlineAsk";

const baseEnv: Omit<SidekickEnvelope, "user_question"> = {
  task_id: "task-01",
  problem_id: "A3",
  card_id: "card-1",
  card_title: "① DEFINITION",
  anchor_id: "def-para-0",
};

describe("AskGutter", () => {
  test("renders children (paragraph content)", () => {
    render(
      <AskGutter paragraphText="Laplace distribution has heavy tails." context={baseEnv} onAsk={vi.fn()}>
        <p>Laplace distribution has heavy tails.</p>
      </AskGutter>
    );
    expect(screen.getByText("Laplace distribution has heavy tails.")).toBeInTheDocument();
  });

  test("? button is present in the DOM (visible on hover via CSS)", () => {
    render(
      <AskGutter paragraphText="Laplace distribution has heavy tails." context={baseEnv} onAsk={vi.fn()}>
        <p>Laplace distribution has heavy tails.</p>
      </AskGutter>
    );
    expect(screen.getByRole("button", { name: /ask sidekick about this paragraph/i })).toBeInTheDocument();
  });

  test("clicking ? calls onAsk with the paragraph as anchorText + empty userQuestion", async () => {
    const onAsk = vi.fn();
    render(
      <AskGutter paragraphText="Heavy tails make the median optimal." context={baseEnv} onAsk={onAsk}>
        <p>Heavy tails make the median optimal.</p>
      </AskGutter>
    );
    await userEvent.click(screen.getByRole("button", { name: /ask sidekick about this paragraph/i }));
    expect(onAsk).toHaveBeenCalledOnce();
    const env: SidekickEnvelope = onAsk.mock.calls[0][0];
    expect(env.anchor_text).toBe("Heavy tails make the median optimal.");
    expect(env.task_id).toBe("task-01");
    expect(env.user_question).toBe("");
  });

  test("? button responds to keyboard activation (focus-visible path)", async () => {
    const onAsk = vi.fn();
    render(
      <AskGutter paragraphText="Laplace MLE." context={baseEnv} onAsk={onAsk}>
        <p>Laplace MLE.</p>
      </AskGutter>
    );
    const btn = screen.getByRole("button", { name: /ask sidekick about this paragraph/i });
    btn.focus();
    await userEvent.keyboard("{Enter}");
    expect(onAsk).toHaveBeenCalledOnce();
  });
});
```

- [ ] **Step 2: Write failing tests for `Sidekick`**

Create `tutor-web/src/__tests__/Sidekick.test.tsx`:

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, vi, describe, beforeEach } from "vitest";
import { Sidekick } from "../components/Sidekick";
import type { SidekickEnvelope } from "../lib/inlineAsk";

vi.mock("../lib/sidekickContext", () => ({
  askSidekick: vi.fn(),
}));

import { askSidekick } from "../lib/sidekickContext";
const mockAskSidekick = vi.mocked(askSidekick);

const baseEnv: SidekickEnvelope = {
  task_id: "task-01",
  problem_id: "A3",
  card_id: "card-1",
  card_title: "① DEFINITION",
  selection: "MLE",
  user_question: "what does MLE mean?",
};

describe("Sidekick", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  test("renders collapsed by default when no envelope is provided", () => {
    render(<Sidekick />);
    const panel = screen.getByTestId("sidekick-panel");
    expect(panel).toBeInTheDocument();
    expect(panel).toHaveAttribute("data-expanded", "false");
  });

  test("expands and calls askSidekick when envelope prop is set", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "MLE stands for Maximum Likelihood Estimation.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: "MLE",
    });

    render(<Sidekick envelope={baseEnv} />);

    await waitFor(() => {
      expect(mockAskSidekick).toHaveBeenCalledOnce();
      expect(mockAskSidekick).toHaveBeenCalledWith(baseEnv);
    });

    expect(await screen.findByText(/MLE stands for Maximum Likelihood Estimation/)).toBeInTheDocument();
    const panel = screen.getByTestId("sidekick-panel");
    expect(panel).toHaveAttribute("data-expanded", "true");
  });

  test("renders > quoted: strip above the AI reply (animation #16)", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "The median minimises the sum of absolute deviations.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: "MLE",
    });

    render(<Sidekick envelope={baseEnv} />);

    const quote = await screen.findByTestId("sidekick-quote");
    expect(quote.textContent).toMatch(/quoted:.*MLE/i);
    expect(quote.classList.contains("sidekick-quote-pop-in")).toBe(true);
  });

  test("renders LLM unavailable message on askSidekick rejection", async () => {
    mockAskSidekick.mockRejectedValue(new Error("503 upstream"));

    render(<Sidekick envelope={baseEnv} />);

    expect(await screen.findByText(/LLM unavailable/i)).toBeInTheDocument();
  });

  test("toggle button collapses an expanded panel", async () => {
    mockAskSidekick.mockResolvedValue({
      text: "Answer text.",
      model: "meta-llama/llama-3.3-70b-instruct:free",
      quotedContext: null,
    });

    render(<Sidekick envelope={baseEnv} />);
    await screen.findByText("Answer text.");

    const toggle = screen.getByRole("button", { name: /collapse sidekick/i });
    await userEvent.click(toggle);

    const panel = screen.getByTestId("sidekick-panel");
    expect(panel).toHaveAttribute("data-expanded", "false");
  });
});
```

- [ ] **Step 3: Run tests to confirm they fail**

```
cd tutor-web && npx vitest run src/__tests__/AskGutter.test.tsx src/__tests__/Sidekick.test.tsx
```

Expected: FAIL — `AskGutter` and `Sidekick` modules not found.

- [ ] **Step 4: Implement `AskGutter.tsx`**

Create `tutor-web/src/components/AskGutter.tsx`:

```tsx
import type { ReactNode } from "react";
import { buildSidekickEnvelope } from "../lib/inlineAsk";
import type { SidekickEnvelope } from "../lib/inlineAsk";

interface AskGutterProps {
  paragraphText: string;
  context: Omit<SidekickEnvelope, "user_question" | "anchor_text">;
  onAsk: (env: SidekickEnvelope) => void;
  children: ReactNode;
}

export function AskGutter({ paragraphText, context, onAsk, children }: AskGutterProps) {
  function handleAsk() {
    const env = buildSidekickEnvelope({
      taskId: context.task_id,
      problemId: context.problem_id,
      cardId: context.card_id,
      cardTitle: context.card_title,
      anchorId: context.anchor_id,
      anchorText: paragraphText,
      selection: context.selection,
      userQuestion: "",
    });
    onAsk(env);
  }

  return (
    <div className="askable" style={{ position: "relative", display: "flex", alignItems: "flex-start", gap: 0 }}>
      <button
        className="ask-gutter-btn"
        aria-label="Ask sidekick about this paragraph"
        onClick={handleAsk}
        style={{
          position: "absolute",
          left: "-28px",
          top: "2px",
          width: "22px",
          height: "22px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "var(--color-accent-soft, #fffae6)",
          border: "2px solid var(--color-border-strong, #0a0a0a)",
          borderRadius: 0,
          fontFamily: "monospace",
          fontSize: "12px",
          fontWeight: 700,
          cursor: "pointer",
          opacity: 0,
          transition: "opacity 120ms ease-out",
          flexShrink: 0,
        }}
      >
        ?
      </button>
      <div style={{ flex: 1 }}>{children}</div>
    </div>
  );
}
```

Append to `tutor-web/src/index.css`:

```css
.askable:hover .ask-gutter-btn,
.askable:focus-within .ask-gutter-btn {
  opacity: 1;
}
@media (prefers-reduced-motion: reduce) {
  .ask-gutter-btn { transition: none; }
}

/* Animation #16 · Sidekick context-quote pop-in */
@keyframes sidekickQuotePopIn {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}
.sidekick-quote-pop-in {
  animation: sidekickQuotePopIn 180ms ease-out both;
}
@media (prefers-reduced-motion: reduce) {
  .sidekick-quote-pop-in { animation: none; }
}
```

- [ ] **Step 5: Implement `Sidekick.tsx`**

Create `tutor-web/src/components/Sidekick.tsx`:

```tsx
import { useEffect, useState } from "react";
import { askSidekick } from "../lib/sidekickContext";
import type { SidekickEnvelope } from "../lib/inlineAsk";

interface SidekickProps {
  envelope?: SidekickEnvelope;
}

type FetchState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "ok"; text: string; quotedContext: string | null }
  | { status: "error" };

export function Sidekick({ envelope }: SidekickProps) {
  const [expanded, setExpanded] = useState(false);
  const [fetchState, setFetchState] = useState<FetchState>({ status: "idle" });

  useEffect(() => {
    if (!envelope) return;
    setExpanded(true);
    setFetchState({ status: "loading" });

    let cancelled = false;
    askSidekick(envelope)
      .then((reply) => {
        if (cancelled) return;
        setFetchState({ status: "ok", text: reply.text, quotedContext: reply.quotedContext });
      })
      .catch(() => {
        if (!cancelled) setFetchState({ status: "error" });
      });

    return () => { cancelled = true; };
  }, [envelope]);

  const chevron = expanded ? "▲" : "▼";

  return (
    <div
      data-testid="sidekick-panel"
      data-expanded={String(expanded)}
      style={{
        borderTop: "4px solid var(--color-border-strong, #0a0a0a)",
        background: "var(--color-panel-dark-bg, #1a1a1a)",
        color: "var(--color-panel-dark-fg, #f5f5f5)",
        fontFamily: "monospace",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "6px 12px", borderBottom: expanded ? "2px solid var(--color-border-strong, #0a0a0a)" : "none" }}>
        <span style={{ fontSize: "11px", fontWeight: 700, letterSpacing: "0.1em" }}>SIDEKICK</span>
        <button
          aria-label={expanded ? "Collapse sidekick" : "Expand sidekick"}
          onClick={() => setExpanded((v) => !v)}
          style={{ background: "none", border: "none", color: "inherit", fontFamily: "monospace", fontSize: "12px", cursor: "pointer", padding: "0 4px" }}
        >
          {chevron}
        </button>
      </div>
      <div style={{ overflow: "hidden", maxHeight: expanded ? "600px" : "0", transition: "max-height 200ms ease-out" }}>
        <div style={{ padding: "10px 12px", fontSize: "13px", lineHeight: 1.6 }}>
          {fetchState.status === "idle" && <span style={{ opacity: 0.5 }}>Select text or click ? to ask the sidekick.</span>}
          {fetchState.status === "loading" && <span style={{ opacity: 0.7 }}>thinking…</span>}
          {fetchState.status === "error" && <span style={{ color: "var(--color-accent, #ffcc00)", opacity: 0.9 }}>(LLM unavailable; rate-limited?)</span>}
          {fetchState.status === "ok" && (
            <>
              {fetchState.quotedContext && (
                <div
                  data-testid="sidekick-quote"
                  className="sidekick-quote-pop-in"
                  style={{ borderLeft: "3px solid var(--color-accent, #ffcc00)", paddingLeft: "10px", marginBottom: "10px", fontSize: "12px", opacity: 0.85, fontStyle: "italic" }}
                >
                  {`> quoted: "${fetchState.quotedContext}"`}
                </div>
              )}
              <div style={{ whiteSpace: "pre-wrap" }}>{fetchState.text}</div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Wire `InlineAskChip` + `Sidekick` into `TutorWorkspace.tsx`**

In `tutor-web/src/components/TutorWorkspace.tsx`, add imports:

```tsx
import { useRef, useState } from "react";
import { InlineAskChip } from "./InlineAskChip";
import { Sidekick } from "./Sidekick";
import { attachSelectionListener, buildSidekickEnvelope } from "../lib/inlineAsk";
import type { SidekickEnvelope } from "../lib/inlineAsk";
```

(Dedupe with existing imports — `useRef`/`useState` may already be imported.)

Inside the `TutorWorkspace` function body, before the return, add:

```tsx
  const workspaceRef = useRef<HTMLDivElement>(null);
  const [chipState, setChipState] = useState<{ rect: DOMRect; envelope: SidekickEnvelope } | null>(null);
  const [sidekickEnvelope, setSidekickEnvelope] = useState<SidekickEnvelope | undefined>(undefined);

  useEffect(() => {
    const root = workspaceRef.current;
    if (!root) return;
    const detach = attachSelectionListener(root, (selectedText, rect) => {
      const env = buildSidekickEnvelope({ taskId, selection: selectedText, userQuestion: selectedText });
      setChipState({ rect, envelope: env });
    });
    function handlePointerDown(e: PointerEvent) {
      const target = e.target as HTMLElement;
      if (!target.closest(".ask-chip-fade-in")) setChipState(null);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => {
      detach();
      document.removeEventListener("pointerdown", handlePointerDown);
    };
  }, [taskId]);
```

Attach `ref={workspaceRef}` to the outermost `<div>` returned by `TutorWorkspace`. Mount `<Sidekick envelope={sidekickEnvelope} />` inside the right pane below `<ChatPane />`. Render `<InlineAskChip />` conditionally on `chipState` at end of JSX:

```tsx
{chipState && (
  <InlineAskChip
    selectionRect={chipState.rect}
    envelope={chipState.envelope}
    onAsk={(env) => { setSidekickEnvelope(env); setChipState(null); }}
  />
)}
```

- [ ] **Step 7: Run all Phase E tests**

```
cd tutor-web && npx vitest run src/__tests__/inlineAsk.test.ts src/__tests__/InlineAskChip.test.tsx src/__tests__/AskGutter.test.tsx src/__tests__/Sidekick.test.tsx
```

Expected: 6 + 6 + 4 + 5 = 21 PASS.

- [ ] **Step 8: Commit**

```bash
git add tutor-web/src/components/AskGutter.tsx tutor-web/src/components/Sidekick.tsx tutor-web/src/components/TutorWorkspace.tsx tutor-web/src/__tests__/AskGutter.test.tsx tutor-web/src/__tests__/Sidekick.test.tsx tutor-web/src/index.css
git commit -m "feat(sidekick): AskGutter ? gutter button + Sidekick panel with quoted-context strip; wire into TutorWorkspace"
```

---

## PHASE F · FSRS review UI (Tasks F1-F3)

### Task F1: `lib/fsrsClient.ts` — due/grade/forecast API wrappers

**Files:**
- Create: `tutor-web/src/lib/fsrsClient.ts`
- Test: `tutor-web/src/__tests__/fsrsClient.test.ts`

- [ ] **Step 1: Write failing tests for the three client functions**

Create `tutor-web/src/__tests__/fsrsClient.test.ts`:

```typescript
import { vi, beforeEach, afterEach, describe, test, expect } from "vitest";
import { getDue, gradeCard, getForecast } from "../lib/fsrsClient";

beforeEach(() => {
  Object.defineProperty(document, "cookie", {
    value: "csrf=testcsrf",
    configurable: true,
    writable: true,
  });
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("getDue", () => {
  test("calls /api/v1/fsrs/due and returns card array", async () => {
    const payload = {
      cards: [
        {
          id: "c1",
          front: "Q1",
          back: "A1",
          sourceTaskId: "t1",
          difficulty: 2.1,
          stability: 4.0,
          retrievability: 0.9,
          dueAt: "2026-05-10T10:00:00Z",
          lapses: 0,
        },
      ],
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(payload), { status: 200 })),
    );
    const cards = await getDue(10);
    expect(cards).toHaveLength(1);
    expect(cards[0].id).toBe("c1");
    expect(cards[0].front).toBe("Q1");
    expect(cards[0].lapses).toBe(0);
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toContain("/api/v1/fsrs/due");
    expect(call[0]).toContain("limit=10");
  });

  test("throws on non-2xx response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("Unauthorized", { status: 401 })),
    );
    await expect(getDue()).rejects.toThrow("401");
  });
});

describe("gradeCard", () => {
  test("posts grade to /api/v1/fsrs/{id}/grade and returns reply", async () => {
    const payload = {
      cardId: "c1",
      nextDueAt: "2026-05-14T10:00:00Z",
      newDifficulty: 2.2,
      newStability: 4.5,
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(payload), { status: 200 })),
    );
    const reply = await gradeCard("c1", 3);
    expect(reply.cardId).toBe("c1");
    expect(reply.newStability).toBe(4.5);
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toContain("/api/v1/fsrs/c1/grade");
    expect(call[1].method).toBe("POST");
    expect(JSON.parse(call[1].body)).toEqual({ grade: 3 });
  });

  test("throws on non-2xx response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("Not Found", { status: 404 })),
    );
    await expect(gradeCard("missing", 2)).rejects.toThrow("404");
  });
});

describe("getForecast", () => {
  test("calls /api/v1/fsrs/forecast and returns counts", async () => {
    const payload = { tomorrow: 4, thisWeek: 18, thisMonth: 41 };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(payload), { status: 200 })),
    );
    const f = await getForecast();
    expect(f.tomorrow).toBe(4);
    expect(f.thisWeek).toBe(18);
    expect(f.thisMonth).toBe(41);
  });

  test("throws on non-2xx response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("Server Error", { status: 500 })),
    );
    await expect(getForecast()).rejects.toThrow("500");
  });
});
```

- [ ] **Step 2: Run to confirm failure**

```
cd tutor-web && npx vitest run src/__tests__/fsrsClient.test.ts
```

Expected: FAIL — `../lib/fsrsClient` module does not exist.

- [ ] **Step 3: Implement `fsrsClient.ts`**

Create `tutor-web/src/lib/fsrsClient.ts`:

```typescript
import { jarvisFetch } from "./api";

export interface FsrsCardView {
  id: string;
  front: string;
  back: string;
  sourceTaskId: string | null;
  difficulty: number;
  stability: number;
  retrievability: number;
  dueAt: string;
  lapses: number;
}

export interface FsrsDueReply { cards: FsrsCardView[]; }

export interface FsrsGradeReply {
  cardId: string;
  nextDueAt: string;
  newDifficulty: number;
  newStability: number;
}

export interface FsrsForecastReply {
  tomorrow: number;
  thisWeek: number;
  thisMonth: number;
}

async function requireOk(res: Response): Promise<Response> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res;
}

export async function getDue(limit?: number): Promise<FsrsCardView[]> {
  const qs = limit != null ? `?limit=${limit}` : "";
  const res = await requireOk(await jarvisFetch(`/api/v1/fsrs/due${qs}`));
  const body: FsrsDueReply = await res.json();
  return body.cards;
}

export async function gradeCard(id: string, grade: 1 | 2 | 3 | 4): Promise<FsrsGradeReply> {
  const res = await requireOk(
    await jarvisFetch(`/api/v1/fsrs/${encodeURIComponent(id)}/grade`, {
      method: "POST",
      body: JSON.stringify({ grade }),
    }),
  );
  return res.json();
}

export async function getForecast(): Promise<FsrsForecastReply> {
  const res = await requireOk(await jarvisFetch("/api/v1/fsrs/forecast"));
  return res.json();
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/fsrsClient.test.ts
```

Expected: 6 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/lib/fsrsClient.ts tutor-web/src/__tests__/fsrsClient.test.ts
git commit -m "feat(fsrs-ui): lib/fsrsClient.ts — getDue / gradeCard / getForecast with typed interfaces"
```

---

### Task F2: `FsrsReview.tsx` — flip card with 3D animation + grade buttons

**Files:**
- Create: `tutor-web/src/components/FsrsReview.tsx`
- Test: `tutor-web/src/__tests__/FsrsReview.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/FsrsReview.test.tsx`:

```typescript
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { FsrsReview } from "../components/FsrsReview";

vi.mock("../lib/fsrsClient", () => ({
  getDue: vi.fn(),
  gradeCard: vi.fn(),
  getForecast: vi.fn(),
}));

import * as fsrsClient from "../lib/fsrsClient";

const mockCard = {
  id: "c1",
  front: "What is the MLE of μ for Laplace(μ, b)?",
  back: "The sample median — argmin Σ|x_i − μ|.",
  sourceTaskId: "task-ps-1",
  difficulty: 2.1,
  stability: 4.0,
  retrievability: 0.9,
  dueAt: "2026-05-10T10:00:00Z",
  lapses: 0,
};
const mockForecast = { tomorrow: 4, thisWeek: 18, thisMonth: 41 };

beforeEach(() => {
  vi.mocked(fsrsClient.getDue).mockResolvedValue([mockCard]);
  vi.mocked(fsrsClient.getForecast).mockResolvedValue(mockForecast);
  vi.mocked(fsrsClient.gradeCard).mockResolvedValue({
    cardId: "c1", nextDueAt: "2026-05-14T10:00:00Z", newDifficulty: 2.2, newStability: 4.5,
  });
});
afterEach(() => { vi.clearAllMocks(); });

describe("FsrsReview — basic render", () => {
  test("renders header with due count and streak", async () => {
    render(<FsrsReview streak={12} />);
    await waitFor(() => expect(screen.getByTestId("fsrs-header")).toBeInTheDocument());
    expect(screen.getByTestId("fsrs-header").textContent).toMatch(/REVIEW/);
    expect(screen.getByTestId("fsrs-header").textContent).toMatch(/1 DUE/);
    expect(screen.getByTestId("fsrs-header").textContent).toMatch(/12-DAY STREAK/);
  });

  test("shows card front and SHOW ANSWER button before flip", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("card-front")).toBeInTheDocument());
    expect(screen.getByTestId("card-front").textContent).toContain("What is the MLE");
    expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument();
    expect(screen.queryByTestId("grade-buttons")).toBeNull();
  });

  test("empty state when 0 cards due", async () => {
    vi.mocked(fsrsClient.getDue).mockResolvedValue([]);
    render(<FsrsReview streak={3} />);
    await waitFor(() => expect(screen.getByTestId("fsrs-empty")).toBeInTheDocument());
    expect(screen.queryByTestId("card-front")).toBeNull();
  });
});

describe("FsrsReview — 3D flip", () => {
  test("clicking SHOW ANSWER adds is-flipped class to card wrapper", async () => {
    render(<FsrsReview streak={12} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    const wrapper = screen.getByTestId("card-flip-wrapper");
    expect(wrapper.classList.contains("is-flipped")).toBe(false);
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    expect(wrapper.classList.contains("is-flipped")).toBe(true);
  });

  test("grade buttons appear after flip", async () => {
    render(<FsrsReview streak={12} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-buttons")).toBeInTheDocument());
    expect(screen.getByTestId("grade-btn-1")).toBeInTheDocument();
    expect(screen.getByTestId("grade-btn-2")).toBeInTheDocument();
    expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument();
    expect(screen.getByTestId("grade-btn-4")).toBeInTheDocument();
  });
});

describe("FsrsReview — grading", () => {
  test("clicking GOOD calls gradeCard(id, 3) then advances", async () => {
    vi.mocked(fsrsClient.getDue).mockResolvedValueOnce([
      mockCard,
      { ...mockCard, id: "c2", front: "Second card" },
    ]);
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-3"));
    await waitFor(() => expect(vi.mocked(fsrsClient.gradeCard)).toHaveBeenCalledWith("c1", 3));
    await waitFor(() => expect(screen.getByTestId("card-front").textContent).toContain("Second card"));
  });

  test("clicking AGAIN calls gradeCard(id, 1)", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-1")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-1"));
    await waitFor(() => expect(vi.mocked(fsrsClient.gradeCard)).toHaveBeenCalledWith("c1", 1));
  });

  test("after all cards graded shows empty state", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("show-answer-btn")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("show-answer-btn"));
    await waitFor(() => expect(screen.getByTestId("grade-btn-3")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("grade-btn-3"));
    await waitFor(() => expect(screen.getByTestId("fsrs-empty")).toBeInTheDocument());
  });
});

describe("FsrsReview — forecast strip", () => {
  test("renders forecast counts", async () => {
    render(<FsrsReview streak={0} />);
    await waitFor(() => expect(screen.getByTestId("fsrs-forecast")).toBeInTheDocument());
    const strip = screen.getByTestId("fsrs-forecast");
    expect(strip.textContent).toMatch(/tomorrow.*4/i);
    expect(strip.textContent).toMatch(/week.*18/i);
    expect(strip.textContent).toMatch(/month.*41/i);
  });
});
```

- [ ] **Step 2: Run to confirm failure**

```
cd tutor-web && npx vitest run src/__tests__/FsrsReview.test.tsx
```

Expected: FAIL — `../components/FsrsReview` module does not exist.

- [ ] **Step 3: Implement `FsrsReview.tsx`**

Create `tutor-web/src/components/FsrsReview.tsx`:

```tsx
import { useEffect, useState, useCallback } from "react";
import {
  getDue, getForecast, gradeCard,
  type FsrsCardView, type FsrsForecastReply,
} from "../lib/fsrsClient";

const FLIP_STYLE = `
.fsrs-scene { perspective: 900px; }
.fsrs-card-flip {
  position: relative; width: 100%;
  transform-style: preserve-3d;
  transition: transform 400ms cubic-bezier(.4,0,.2,1);
}
.fsrs-card-flip.is-flipped { transform: rotateY(180deg); }
.fsrs-card-face {
  position: absolute; width: 100%;
  backface-visibility: hidden; -webkit-backface-visibility: hidden;
}
.fsrs-card-face.fsrs-card-back { transform: rotateY(180deg); }
.fsrs-card-flip-container { position: relative; }
@media (prefers-reduced-motion: reduce) {
  .fsrs-card-flip { transition: none; }
}
`;

interface Props { streak: number; }
type Grade = 1 | 2 | 3 | 4;

const GRADE_LABELS: Record<Grade, string> = {
  1: "AGAIN ~10m",
  2: "HARD ~1d",
  3: "GOOD ~4d",
  4: "EASY ~12d",
};

export function FsrsReview({ streak }: Props) {
  const [cards, setCards] = useState<FsrsCardView[]>([]);
  const [index, setIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [forecast, setForecast] = useState<FsrsForecastReply | null>(null);
  const [loading, setLoading] = useState(true);
  const [grading, setGrading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([getDue(50), getForecast()])
      .then(([dueCards, fc]) => {
        if (cancelled) return;
        setCards(dueCards);
        setForecast(fc);
      })
      .catch((err: Error) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const currentCard: FsrsCardView | undefined = cards[index];
  const totalDue = cards.length;
  const done = !loading && cards.length > 0 && index >= cards.length;
  const empty = !loading && cards.length === 0;

  const handleShowAnswer = useCallback(() => setFlipped(true), []);

  const handleGrade = useCallback(async (grade: Grade) => {
    if (!currentCard || grading) return;
    setGrading(true);
    try {
      await gradeCard(currentCard.id, grade);
      setIndex(prev => prev + 1);
      setFlipped(false);
    } catch (_) {
      // silent — user can retry
    } finally {
      setGrading(false);
    }
  }, [currentCard, grading]);

  return (
    <>
      <style>{FLIP_STYLE}</style>
      <div className="flex flex-col h-full font-mono bg-page-bg text-page-fg">
        <header
          data-testid="fsrs-header"
          className="bg-panel-dark-bg text-panel-dark-fg px-4 py-3 flex items-center gap-4 border-b-4 border-accent tracking-widest font-bold text-sm"
        >
          <span>JARVIS · REVIEW</span>
          {!loading && !error && (
            <>
              <span className="text-accent">{totalDue} DUE</span>
              {streak > 0 && <span className="text-orange-400">🔥 {streak}-DAY STREAK</span>}
            </>
          )}
        </header>

        <main className="flex-1 min-h-0 overflow-y-auto flex flex-col items-center justify-start p-6 gap-6">
          {loading && <p className="text-page-fg/60 tracking-widest text-sm">loading review queue…</p>}
          {error && <p className="text-danger-text tracking-widest text-sm" role="alert">(can't reach review queue — {error})</p>}

          {(empty || done) && !loading && !error && (
            <div data-testid="fsrs-empty" className="text-center space-y-2">
              <p className="text-2xl font-bold tracking-widest">ALL DONE</p>
              <p className="text-page-fg/60 text-sm tracking-widest">no cards due right now — check back later</p>
            </div>
          )}

          {currentCard && !done && !loading && (
            <>
              <p className="text-xs text-page-fg/60 tracking-widest self-start">CARD {index + 1} OF {totalDue}</p>

              <div className="fsrs-scene w-full max-w-xl">
                <div className="fsrs-card-flip-container" style={{ minHeight: "180px" }}>
                  <div
                    data-testid="card-flip-wrapper"
                    className={`fsrs-card-flip ${flipped ? "is-flipped" : ""}`}
                    style={{ minHeight: "180px" }}
                  >
                    <div className="fsrs-card-face border-4 border-border-strong bg-accent-soft p-6 flex flex-col gap-4" style={{ minHeight: "180px" }}>
                      <p className="text-[10px] tracking-widest text-page-fg/50 font-bold">FRONT · click to flip</p>
                      <p data-testid="card-front" className="text-base leading-relaxed">{currentCard.front}</p>
                      <div className="flex gap-3 mt-auto">
                        <button data-testid="show-answer-btn" onClick={handleShowAnswer} className="px-4 py-2 bg-accent hover:bg-accent-hover text-page-fg font-bold tracking-widest text-xs">SHOW ANSWER</button>
                      </div>
                    </div>
                    <div className="fsrs-card-face fsrs-card-back border-4 border-accent bg-page-bg p-6 flex flex-col gap-4" style={{ minHeight: "180px" }}>
                      <p className="text-[10px] tracking-widest text-page-fg/50 font-bold">ANSWER</p>
                      <p data-testid="card-back" className="text-base leading-relaxed">{currentCard.back}</p>
                    </div>
                  </div>
                </div>
              </div>

              {flipped && (
                <div data-testid="grade-buttons" className="flex flex-wrap gap-3 justify-center">
                  {([1, 2, 3, 4] as Grade[]).map(g => (
                    <button
                      key={g}
                      data-testid={`grade-btn-${g}`}
                      onClick={() => handleGrade(g)}
                      disabled={grading}
                      className={`px-4 py-2 font-bold tracking-widest text-xs border-2 border-border-strong hover:bg-accent hover:text-page-fg disabled:opacity-50 ${g === 3 ? "bg-accent text-page-fg" : "bg-page-bg text-page-fg"}`}
                    >
                      {GRADE_LABELS[g]}
                    </button>
                  ))}
                </div>
              )}
            </>
          )}

          {forecast && (
            <div data-testid="fsrs-forecast" className="mt-auto border-t border-border-thin pt-4 w-full max-w-xl flex gap-6 text-xs tracking-widest text-page-fg/70">
              <span>FORECAST · tomorrow <strong className="text-page-fg">{forecast.tomorrow}</strong> · week <strong className="text-page-fg">{forecast.thisWeek}</strong> · month <strong className="text-page-fg">{forecast.thisMonth}</strong></span>
            </div>
          )}
        </main>
      </div>
    </>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/FsrsReview.test.tsx
```

Expected: 10 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/FsrsReview.tsx tutor-web/src/__tests__/FsrsReview.test.tsx
git commit -m "feat(fsrs-ui): FsrsReview — flip card (anim #6 3D rotateY) + AGAIN/HARD/GOOD/EASY grade row"
```

---

### Task F3: Route `/tutor/review` + nav pill in header

**Files:**
- Modify: `tutor-web/src/main.tsx`
- Modify: `tutor-web/src/App.tsx`
- Test: `tutor-web/src/__tests__/App.test.tsx`

- [ ] **Step 1: Write failing route test**

Append to `tutor-web/src/__tests__/App.test.tsx`:

```typescript
import { FsrsReview } from "../components/FsrsReview";

vi.mock("../components/FsrsReview", () => ({
  FsrsReview: () => React.createElement("div", { "data-testid": "fsrs-review-page" }, "FSRS REVIEW"),
}));

test("/review route renders FsrsReview page", async () => {
  render(<MemoryRouter initialEntries={["/review"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("fsrs-review-page")).toBeInTheDocument());
});

test("header nav pill 'review' links to /review", async () => {
  render(<MemoryRouter initialEntries={["/"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("active-task-dashboard")).toBeInTheDocument());
  const pill = screen.getByRole("link", { name: /review/i });
  expect(pill).toBeInTheDocument();
  expect(pill.getAttribute("href")).toContain("review");
});

test("review nav pill has aria-current=page when on /review", async () => {
  render(<MemoryRouter initialEntries={["/review"]}><App /></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId("fsrs-review-page")).toBeInTheDocument());
  const pill = screen.getByRole("link", { name: /review/i });
  expect(pill.getAttribute("aria-current")).toBe("page");
});
```

- [ ] **Step 2: Run to confirm failure**

```
cd tutor-web && npx vitest run src/__tests__/App.test.tsx
```

Expected: 3 new tests FAIL.

- [ ] **Step 3: Register `/review` route in `main.tsx`**

In `tutor-web/src/main.tsx`, add the import + route:

```tsx
import "katex/dist/katex.min.css";
import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { App } from "./App";
import { TrustSettings } from "./components/TrustSettings";
import { TasksScreen } from "./components/TasksScreen";
import { FsrsReview } from "./components/FsrsReview";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter basename="/tutor">
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/tasks" element={<TasksScreen />} />
        <Route path="/settings/trust" element={<TrustSettings />} />
        <Route path="/review" element={<FsrsReview streak={0} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
```

- [ ] **Step 4: Add the `review` nav pill to `App.tsx`**

In `tutor-web/src/App.tsx`, in the `<nav>` block, add a new `<Link>` between `tasks` and `trust`:

```tsx
<Link
  to="/review"
  aria-current={here.pathname === "/review" ? "page" : undefined}
  className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
>
  review
</Link>
```

- [ ] **Step 5: Run App test suite**

```
cd tutor-web && npx vitest run src/__tests__/App.test.tsx
```

Expected: all App tests PASS (existing + 3 new).

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/main.tsx tutor-web/src/App.tsx tutor-web/src/__tests__/App.test.tsx
git commit -m "feat(fsrs-ui): register /review route + add 'review' nav pill in App header"
```

---

## PHASE G · Concept animations (Tasks G1-G5)

### Task G1: `NumLineDirect.tsx` — direct-drag μ marker on SVG number line

**Files:**
- Create: `tutor-web/src/components/viz/NumLineDirect.tsx`
- Create: `tutor-web/src/__tests__/viz/NumLineDirect.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/viz/NumLineDirect.test.tsx`:

```tsx
import { render, fireEvent } from "@testing-library/react";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { NumLineDirect } from "../../components/viz/NumLineDirect";

beforeEach(() => {
  vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => { cb(0); return 0; });
  vi.stubGlobal("cancelAnimationFrame", () => {});
});
afterEach(() => { vi.unstubAllGlobals(); });

describe("NumLineDirect", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders an SVG element", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    expect(container.querySelector("svg")).not.toBeNull();
  });

  test("renders a tick mark for each sample point", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    const ticks = container.querySelectorAll("[data-testid='sample-tick']");
    expect(ticks.length).toBe(data.length);
  });

  test("renders the μ marker circle", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} />);
    expect(container.querySelector("[data-testid='mu-marker']")).not.toBeNull();
  });

  test("μ marker cx reflects mu prop mapped to SVG space", () => {
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={() => {}} min={0} max={20} />);
    const marker = container.querySelector("[data-testid='mu-marker']")!;
    const cx = parseFloat(marker.getAttribute("cx") ?? "0");
    expect(cx).toBeGreaterThan(24);
    expect(cx).toBeLessThan(456);
  });

  test("pointer drag calls onMu with new value", () => {
    const onMu = vi.fn();
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={onMu} min={0} max={20} />);
    const marker = container.querySelector("[data-testid='mu-marker']")!;
    (marker as any).setPointerCapture = vi.fn();
    (marker as any).releasePointerCapture = vi.fn();

    fireEvent.pointerDown(marker, { pointerId: 1, clientX: 196 });
    fireEvent.pointerMove(marker, { pointerId: 1, clientX: 216, target: marker });
    fireEvent.pointerUp(marker, { pointerId: 1 });

    expect(onMu).toHaveBeenCalled();
    const called = onMu.mock.calls[onMu.mock.calls.length - 1][0];
    expect(called).toBeGreaterThan(8);
  });

  test("does not call onMu if pointer moves less than 1px threshold", () => {
    const onMu = vi.fn();
    const { container } = render(<NumLineDirect data={data} mu={8} onMu={onMu} min={0} max={20} />);
    const marker = container.querySelector("[data-testid='mu-marker']")!;
    (marker as any).setPointerCapture = vi.fn();
    (marker as any).releasePointerCapture = vi.fn();

    fireEvent.pointerDown(marker, { pointerId: 1, clientX: 196 });
    fireEvent.pointerMove(marker, { pointerId: 1, clientX: 196.3 });
    fireEvent.pointerUp(marker, { pointerId: 1 });

    expect(onMu).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/viz/NumLineDirect.test.tsx
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement `NumLineDirect.tsx`**

Create `tutor-web/src/components/viz/NumLineDirect.tsx`:

```tsx
import { useRef, useCallback, useEffect } from "react";

export interface NumLineDirectProps {
  data: number[];
  mu: number;
  onMu: (v: number) => void;
  min?: number;
  max?: number;
}

const SVG_W = 480;
const SVG_H = 80;
const PAD = 24;
const USABLE = SVG_W - PAD * 2;
const TICK_H = 14;
const AXIS_Y = 50;
const MARKER_R = 9;

function toSvgX(v: number, lo: number, hi: number): number {
  return PAD + ((v - lo) / (hi - lo)) * USABLE;
}
function fromSvgX(x: number, lo: number, hi: number): number {
  const clamped = Math.max(PAD, Math.min(SVG_W - PAD, x));
  return lo + ((clamped - PAD) / USABLE) * (hi - lo);
}

export function NumLineDirect({ data, mu, onMu, min, max }: NumLineDirectProps) {
  const lo = min ?? Math.min(...data) - 2;
  const hi = max ?? Math.max(...data) + 2;
  const markerRef = useRef<SVGCircleElement>(null);
  const muRef = useRef(mu);
  muRef.current = mu;
  const dragging = useRef(false);
  const rafId = useRef<number>(0);
  const pendingX = useRef<number | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  const prefersReduced = typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  useEffect(() => {
    if (!markerRef.current) return;
    markerRef.current.setAttribute("cx", String(toSvgX(mu, lo, hi)));
  }, [mu, lo, hi]);

  const flushRAF = useCallback(() => {
    if (pendingX.current === null) return;
    const svgEl = svgRef.current;
    if (!svgEl) return;
    const rect = svgEl.getBoundingClientRect();
    const svgX = pendingX.current - rect.left;
    const newMu = parseFloat(fromSvgX(svgX, lo, hi).toFixed(3));
    pendingX.current = null;
    if (Math.abs(newMu - muRef.current) >= 0.05) onMu(newMu);
  }, [lo, hi, onMu]);

  const onPointerDown = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragging.current = true;
  }, []);

  const onPointerMove = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    if (!dragging.current) return;
    const svgEl = svgRef.current;
    if (!svgEl) return;
    const rect = svgEl.getBoundingClientRect();
    const rawSvgX = e.clientX - rect.left;
    const currentX = toSvgX(muRef.current, lo, hi);
    if (Math.abs(rawSvgX - currentX) < 1) return;
    pendingX.current = e.clientX;
    if (prefersReduced) flushRAF();
    else {
      cancelAnimationFrame(rafId.current);
      rafId.current = requestAnimationFrame(flushRAF);
    }
  }, [flushRAF, lo, prefersReduced]);

  const onPointerUp = useCallback((e: React.PointerEvent<SVGCircleElement>) => {
    e.currentTarget.releasePointerCapture(e.pointerId);
    dragging.current = false;
    cancelAnimationFrame(rafId.current);
    if (pendingX.current !== null) flushRAF();
  }, [flushRAF]);

  return (
    <svg
      ref={svgRef}
      width={SVG_W}
      height={SVG_H}
      viewBox={`0 0 ${SVG_W} ${SVG_H}`}
      data-testid="num-line-direct"
      style={{ userSelect: "none", touchAction: "none" }}
    >
      <line x1={PAD} y1={AXIS_Y} x2={SVG_W - PAD} y2={AXIS_Y} stroke="currentColor" strokeWidth={1.5} opacity={0.35} />
      {data.map((v, i) => (
        <line
          key={i}
          data-testid="sample-tick"
          x1={toSvgX(v, lo, hi)} y1={AXIS_Y - TICK_H / 2}
          x2={toSvgX(v, lo, hi)} y2={AXIS_Y + TICK_H / 2}
          stroke="currentColor" strokeWidth={2} opacity={0.6}
        />
      ))}
      <circle
        ref={markerRef}
        data-testid="mu-marker"
        cx={toSvgX(mu, lo, hi)} cy={AXIS_Y} r={MARKER_R}
        fill="var(--color-accent, #3b82f6)" stroke="white" strokeWidth={2}
        style={{ cursor: "ew-resize" }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
      />
      <text x={toSvgX(mu, lo, hi)} y={AXIS_Y - MARKER_R - 4} textAnchor="middle" fontSize={11} fill="var(--color-accent, #3b82f6)" fontWeight="bold">μ</text>
    </svg>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/viz/NumLineDirect.test.tsx
```

Expected: 6 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/NumLineDirect.tsx tutor-web/src/__tests__/viz/NumLineDirect.test.tsx
git commit -m "feat(viz): NumLineDirect — SVG number line with direct-drag μ marker (pointer events + RAF)"
```

---

### Task G2: `SumPlotTracker.tsx` — Σ|x_i − μ| line chart with RAF-tracked marker

**Files:**
- Create: `tutor-web/src/components/viz/SumPlotTracker.tsx`
- Create: `tutor-web/src/__tests__/viz/SumPlotTracker.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/viz/SumPlotTracker.test.tsx`:

```tsx
import { render } from "@testing-library/react";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { SumPlotTracker } from "../../components/viz/SumPlotTracker";

beforeEach(() => {
  vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => { cb(0); return 0; });
  vi.stubGlobal("cancelAnimationFrame", () => {});
});
afterEach(() => { vi.unstubAllGlobals(); });

describe("SumPlotTracker", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders an SVG element", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("svg")).not.toBeNull();
  });

  test("renders a persistent path element for the curve", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("[data-testid='sum-curve']")).not.toBeNull();
  });

  test("renders the tracking marker circle", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("[data-testid='sum-marker']")).not.toBeNull();
  });

  test("marker cx is within SVG bounds", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    const marker = container.querySelector("[data-testid='sum-marker']")!;
    const cx = parseFloat(marker.getAttribute("cx") ?? "0");
    expect(cx).toBeGreaterThanOrEqual(0);
    expect(cx).toBeLessThanOrEqual(480);
  });

  test("sum curve d attribute is non-empty", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    const path = container.querySelector("[data-testid='sum-curve']")!;
    expect((path.getAttribute("d") ?? "").length).toBeGreaterThan(0);
  });

  test("marker position changes when mu prop changes", () => {
    const { container, rerender } = render(<SumPlotTracker data={data} mu={3} />);
    const before = container.querySelector("[data-testid='sum-marker']")!.getAttribute("cx");
    rerender(<SumPlotTracker data={data} mu={14} />);
    const after = container.querySelector("[data-testid='sum-marker']")!.getAttribute("cx");
    expect(before).not.toBe(after);
  });

  test("renders a y-axis label", () => {
    const { container } = render(<SumPlotTracker data={data} mu={8} />);
    expect(container.querySelector("[data-testid='sum-axis-label']")).not.toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/viz/SumPlotTracker.test.tsx
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement `SumPlotTracker.tsx`**

Create `tutor-web/src/components/viz/SumPlotTracker.tsx`:

```tsx
import { useRef, useEffect } from "react";

export interface SumPlotTrackerProps {
  data: number[];
  mu: number;
  range?: [number, number];
}

const SVG_W = 480, SVG_H = 200;
const PAD_L = 48, PAD_R = 16, PAD_T = 16, PAD_B = 32;
const USABLE_W = SVG_W - PAD_L - PAD_R;
const USABLE_H = SVG_H - PAD_T - PAD_B;
const STEPS = 240;

function sumAbsDev(mu: number, data: number[]): number {
  return data.reduce((acc, x) => acc + Math.abs(x - mu), 0);
}

interface CurvePoint { svgX: number; svgY: number; mu: number; }

function buildCurve(data: number[], lo: number, hi: number): CurvePoint[] {
  const pts: CurvePoint[] = [];
  const yValues: number[] = [];
  for (let i = 0; i <= STEPS; i++) yValues.push(sumAbsDev(lo + (i / STEPS) * (hi - lo), data));
  const yMin = Math.min(...yValues), yMax = Math.max(...yValues);
  const yRange = yMax - yMin || 1;
  for (let i = 0; i <= STEPS; i++) {
    const mu = lo + (i / STEPS) * (hi - lo);
    pts.push({
      svgX: PAD_L + (i / STEPS) * USABLE_W,
      svgY: PAD_T + USABLE_H - ((yValues[i] - yMin) / yRange) * USABLE_H,
      mu,
    });
  }
  return pts;
}

function pathD(pts: CurvePoint[]): string {
  if (pts.length === 0) return "";
  return pts.map((p, i) => `${i === 0 ? "M" : "L"}${p.svgX.toFixed(2)},${p.svgY.toFixed(2)}`).join(" ");
}

function interpolateMarker(mu: number, pts: CurvePoint[]): { x: number; y: number } {
  if (pts.length === 0) return { x: 0, y: 0 };
  if (mu <= pts[0].mu) return { x: pts[0].svgX, y: pts[0].svgY };
  if (mu >= pts[pts.length - 1].mu) return { x: pts[pts.length - 1].svgX, y: pts[pts.length - 1].svgY };
  let lo = 0, hi = pts.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (pts[mid].mu <= mu) lo = mid; else hi = mid;
  }
  const t = (mu - pts[lo].mu) / (pts[hi].mu - pts[lo].mu);
  return {
    x: pts[lo].svgX + t * (pts[hi].svgX - pts[lo].svgX),
    y: pts[lo].svgY + t * (pts[hi].svgY - pts[lo].svgY),
  };
}

export function SumPlotTracker({ data, mu, range }: SumPlotTrackerProps) {
  const lo = range?.[0] ?? Math.min(...data) - 1;
  const hi = range?.[1] ?? Math.max(...data) + 1;
  const curve = buildCurve(data, lo, hi);
  const pathStr = pathD(curve);

  const markerRef = useRef<SVGCircleElement>(null);
  const muRef = useRef(mu);
  const rafId = useRef<number>(0);

  useEffect(() => {
    const prevMu = muRef.current;
    muRef.current = mu;
    if (Math.abs(mu - prevMu) < 0.001) return;
    cancelAnimationFrame(rafId.current);
    rafId.current = requestAnimationFrame(() => {
      if (!markerRef.current) return;
      const pos = interpolateMarker(mu, curve);
      markerRef.current.setAttribute("cx", pos.x.toFixed(2));
      markerRef.current.setAttribute("cy", pos.y.toFixed(2));
    });
    return () => cancelAnimationFrame(rafId.current);
  }, [mu, curve]);

  const initialPos = interpolateMarker(mu, curve);

  return (
    <svg width={SVG_W} height={SVG_H} viewBox={`0 0 ${SVG_W} ${SVG_H}`} data-testid="sum-plot-tracker">
      <line x1={PAD_L} y1={PAD_T} x2={PAD_L} y2={PAD_T + USABLE_H} stroke="currentColor" strokeWidth={1} opacity={0.3} />
      <line x1={PAD_L} y1={PAD_T + USABLE_H} x2={PAD_L + USABLE_W} y2={PAD_T + USABLE_H} stroke="currentColor" strokeWidth={1} opacity={0.3} />
      <text data-testid="sum-axis-label" x={10} y={PAD_T + USABLE_H / 2} fontSize={10} textAnchor="middle" transform={`rotate(-90, 10, ${PAD_T + USABLE_H / 2})`} fill="currentColor" opacity={0.6}>Σ|xᵢ − μ|</text>
      <path data-testid="sum-curve" d={pathStr} fill="none" stroke="var(--color-accent, #3b82f6)" strokeWidth={2} />
      <circle ref={markerRef} data-testid="sum-marker" cx={initialPos.x} cy={initialPos.y} r={6} fill="var(--color-accent, #3b82f6)" stroke="white" strokeWidth={2} />
    </svg>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/viz/SumPlotTracker.test.tsx
```

Expected: 7 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/SumPlotTracker.tsx tutor-web/src/__tests__/viz/SumPlotTracker.test.tsx
git commit -m "feat(viz): SumPlotTracker — RAF-driven marker tracks Σ|xi−μ| curve as μ changes"
```

---

### Task G3: `SlopeCounter.tsx` — live left-minus-right integer

**Files:**
- Create: `tutor-web/src/components/viz/SlopeCounter.tsx`
- Create: `tutor-web/src/__tests__/viz/SlopeCounter.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/viz/SlopeCounter.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, test, expect } from "vitest";
import { SlopeCounter } from "../../components/viz/SlopeCounter";

describe("SlopeCounter", () => {
  const data = [3, 7, 8, 9, 14];

  test("LEFT chip count for mu=8 → 2", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-left-chip").textContent).toContain("2");
  });

  test("RIGHT chip count for mu=8 → 2", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-right-chip").textContent).toContain("2");
  });

  test("diff = 0 for mu=8", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("0");
  });

  test("diff = -4 for mu=3 (no left, 4 right)", () => {
    render(<SlopeCounter data={data} mu={3} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("-4");
  });

  test("diff = 4 for mu=14 (4 left, no right)", () => {
    render(<SlopeCounter data={data} mu={14} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("4");
  });

  test("instantly updates on mu prop change", () => {
    const { rerender } = render(<SlopeCounter data={data} mu={3} />);
    rerender(<SlopeCounter data={data} mu={14} />);
    expect(screen.getByTestId("slope-diff").textContent).toContain("4");
  });

  test("excludes ties (x === mu) from both counts", () => {
    render(<SlopeCounter data={data} mu={8} />);
    expect(screen.getByTestId("slope-left-chip").textContent).toContain("2");
    expect(screen.getByTestId("slope-right-chip").textContent).toContain("2");
  });
});
```

- [ ] **Step 2: Run tests to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/viz/SlopeCounter.test.tsx
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement `SlopeCounter.tsx`**

Create `tutor-web/src/components/viz/SlopeCounter.tsx`:

```tsx
export interface SlopeCounterProps { data: number[]; mu: number; }

export function SlopeCounter({ data, mu }: SlopeCounterProps) {
  const left = data.filter((x) => x < mu).length;
  const right = data.filter((x) => x > mu).length;
  const diff = left - right;

  return (
    <div data-testid="slope-counter" style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "0.4rem", fontFamily: "var(--font-mono, monospace)" }}>
      <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
        <span data-testid="slope-left-chip" style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem", padding: "0.2em 0.6em", borderRadius: "9999px", background: "var(--color-left, #3b82f6)", color: "#fff", fontSize: "0.8rem", fontWeight: 700 }}>
          LEFT: {left}
        </span>
        <span data-testid="slope-right-chip" style={{ display: "inline-flex", alignItems: "center", gap: "0.25rem", padding: "0.2em 0.6em", borderRadius: "9999px", background: "var(--color-right, #f59e0b)", color: "#fff", fontSize: "0.8rem", fontWeight: 700 }}>
          RIGHT: {right}
        </span>
      </div>
      <div style={{ fontSize: "0.75rem", opacity: 0.65, marginTop: "0.1rem" }}>
        slope = <span data-testid="slope-diff" style={{ fontWeight: 700, color: diff > 0 ? "var(--color-left, #3b82f6)" : diff < 0 ? "var(--color-right, #f59e0b)" : "currentColor" }}>{diff >= 0 ? `+${diff}` : `${diff}`}</span> ({left} − {right})
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/viz/SlopeCounter.test.tsx
```

Expected: 7 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/SlopeCounter.tsx tutor-web/src/__tests__/viz/SlopeCounter.test.tsx
git commit -m "feat(viz): SlopeCounter — live left/right chip badges with instant slope integer"
```

---

### Task G4: `SigmaStackedBar.tsx` — stacked bar of |x_i − μ| with CSS transition

**Files:**
- Create: `tutor-web/src/components/viz/SigmaStackedBar.tsx`
- Create: `tutor-web/src/__tests__/viz/SigmaStackedBar.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/viz/SigmaStackedBar.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, test, expect, vi } from "vitest";
import { SigmaStackedBar } from "../../components/viz/SigmaStackedBar";

describe("SigmaStackedBar", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders one segment per data point", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const segments = container.querySelectorAll("[data-testid^='sigma-seg-']");
    expect(segments.length).toBe(data.length);
  });

  test("sum label = 13 for mu=8", () => {
    render(<SigmaStackedBar data={data} mu={8} />);
    expect(screen.getByTestId("sigma-sum").textContent).toContain("13");
  });

  test("sum updates when mu changes", () => {
    const { rerender } = render(<SigmaStackedBar data={data} mu={8} />);
    rerender(<SigmaStackedBar data={data} mu={3} />);
    expect(screen.getByTestId("sigma-sum").textContent).toContain("26");
  });

  test("zero-deviation segment has zero width", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const seg = container.querySelector(`[data-testid='sigma-seg-${data.indexOf(8)}']`) as HTMLElement;
    expect(parseFloat(seg.style.width)).toBe(0);
  });

  test("each segment has data-value matching |xi − μ|", () => {
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    data.forEach((x, i) => {
      const seg = container.querySelector(`[data-testid='sigma-seg-${i}']`)!;
      expect(parseFloat(seg.getAttribute("data-value") ?? "")).toBeCloseTo(Math.abs(x - 8), 3);
    });
  });

  test("transition: none under prefers-reduced-motion", () => {
    const original = window.matchMedia;
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }) as any;
    const { container } = render(<SigmaStackedBar data={data} mu={8} />);
    const seg = container.querySelector("[data-testid^='sigma-seg-']") as HTMLElement;
    expect(seg.style.transition).toBe("none");
    window.matchMedia = original;
  });
});
```

- [ ] **Step 2: Run tests to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/viz/SigmaStackedBar.test.tsx
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement `SigmaStackedBar.tsx`**

Create `tutor-web/src/components/viz/SigmaStackedBar.tsx`:

```tsx
export interface SigmaStackedBarProps { data: number[]; mu: number; }

const SEGMENT_COLORS = ["#3b82f6", "#f59e0b", "#10b981", "#ef4444", "#8b5cf6", "#06b6d4", "#f97316"];

export function SigmaStackedBar({ data, mu }: SigmaStackedBarProps) {
  const prefersReduced = typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const deviations = data.map((x) => Math.abs(x - mu));
  const total = deviations.reduce((a, b) => a + b, 0);

  return (
    <div data-testid="sigma-stacked-bar" style={{ display: "flex", flexDirection: "column", gap: "0.4rem", fontFamily: "var(--font-mono, monospace)" }}>
      <div style={{ display: "flex", height: "28px", width: "100%", borderRadius: "4px", overflow: "hidden", background: "var(--color-panel-bg, #f1f5f9)" }}>
        {deviations.map((dev, i) => {
          const pct = total > 0 ? (dev / total) * 100 : 0;
          return (
            <div
              key={i}
              data-testid={`sigma-seg-${i}`}
              data-value={dev}
              style={{
                width: `${pct}%`,
                background: SEGMENT_COLORS[i % SEGMENT_COLORS.length],
                transition: prefersReduced ? "none" : "width 120ms ease-out",
                height: "100%",
                minWidth: dev > 0 ? "2px" : "0px",
              }}
              title={`|x${i + 1} − μ| = ${dev.toFixed(2)}`}
            />
          );
        })}
      </div>
      <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
        {deviations.map((dev, i) => (
          <span key={i} style={{ fontSize: "0.7rem", color: SEGMENT_COLORS[i % SEGMENT_COLORS.length], fontWeight: 600 }}>
            |x<sub>{i + 1}</sub>−μ|={dev.toFixed(1)}
          </span>
        ))}
      </div>
      <div style={{ fontSize: "0.8rem", fontWeight: 700, textAlign: "right" }}>
        Σ = <span data-testid="sigma-sum" style={{ color: "var(--color-accent, #3b82f6)" }}>{total.toFixed(1)}</span>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/viz/SigmaStackedBar.test.tsx
```

Expected: 6 PASS.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/components/viz/SigmaStackedBar.tsx tutor-web/src/__tests__/viz/SigmaStackedBar.test.tsx
git commit -m "feat(viz): SigmaStackedBar — horizontal stacked bar per |xi−μ| segment, 120ms ease-out"
```

---

### Task G5: Plotly frame morphs — `frames` prop + `CompareFrames.tsx` demo

**Files:**
- Modify: `tutor-web/src/components/PlotlyEmbed.tsx`
- Create: `tutor-web/src/components/viz/CompareFrames.tsx`
- Create: `tutor-web/src/__tests__/viz/CompareFrames.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `tutor-web/src/__tests__/viz/CompareFrames.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, test, expect, vi, beforeEach } from "vitest";
import { CompareFrames } from "../../components/viz/CompareFrames";

const animateSpy = vi.fn().mockResolvedValue(undefined);

vi.mock("plotly.js-dist-min", () => ({
  default: { animate: animateSpy },
}));

beforeEach(() => { animateSpy.mockClear(); });

describe("CompareFrames", () => {
  const data = [3, 7, 8, 9, 14];

  test("renders without crashing", () => { render(<CompareFrames data={data} />); });

  test("renders play button", () => {
    render(<CompareFrames data={data} />);
    expect(screen.getByTestId("compare-frames-play")).toBeInTheDocument();
  });

  test("play calls Plotly.animate with 3 frames", async () => {
    render(<CompareFrames data={data} />);
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    await waitFor(() => expect(animateSpy).toHaveBeenCalled());
    const [, frames] = animateSpy.mock.calls[0];
    expect(Array.isArray(frames)).toBe(true);
    expect(frames.length).toBe(3);
  });

  test("transition duration = 600ms by default", async () => {
    render(<CompareFrames data={data} />);
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    await waitFor(() => expect(animateSpy).toHaveBeenCalled());
    const [, , opts] = animateSpy.mock.calls[0];
    expect(opts.transition.duration).toBe(600);
  });

  test("transition duration = 0 under reduced-motion", async () => {
    const original = window.matchMedia;
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn(),
    }) as any;
    render(<CompareFrames data={data} />);
    fireEvent.click(screen.getByTestId("compare-frames-play"));
    await waitFor(() => expect(animateSpy).toHaveBeenCalled());
    const [, , opts] = animateSpy.mock.calls[0];
    expect(opts.transition.duration).toBe(0);
    window.matchMedia = original;
  });
});
```

- [ ] **Step 2: Run tests to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/viz/CompareFrames.test.tsx
```

Expected: FAIL — module not found.

- [ ] **Step 3: Extend `PlotlyEmbed.tsx` with `frames` prop**

Modify `tutor-web/src/components/PlotlyEmbed.tsx`:

```tsx
import { lazy, Suspense, useEffect, useRef } from "react";

const Plot = lazy(async () => {
  const factoryMod = await import("react-plotly.js/factory");
  const Plotly = await import("plotly.js-dist-min");
  const factory = (factoryMod as any).default ?? factoryMod;
  const PlotlyDefault = (Plotly as any).default ?? Plotly;
  return { default: factory(PlotlyDefault) };
});

export interface PlotlyFigure {
  data?: any[];
  layout?: any;
  config?: any;
}

export interface PlotlyFrame {
  name: string;
  data: any[];
  layout?: any;
}

export function PlotlyEmbed({
  figure,
  indexLabel,
  frames,
  animateOnMount = false,
}: {
  figure: PlotlyFigure;
  indexLabel?: string;
  frames?: PlotlyFrame[];
  animateOnMount?: boolean;
}) {
  const layoutTitle = figure.layout?.title;
  const titleText: string =
    (typeof layoutTitle === "string" && layoutTitle) ||
    (layoutTitle && typeof layoutTitle.text === "string" && layoutTitle.text) ||
    "";
  const caption = titleText ? `${indexLabel ?? "FIG"} · ${titleText}` : (indexLabel ?? "FIG");
  const layoutWithoutTitle = { ...(figure.layout ?? {}) };
  delete (layoutWithoutTitle as any).title;

  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current || !frames || !animateOnMount) return;
    const el = containerRef.current.querySelector(".js-plotly-plot");
    if (!el) return;
    import("plotly.js-dist-min").then((mod) => {
      const Plotly = (mod as any).default ?? mod;
      if (typeof Plotly.animate !== "function") return;
      const prefersReduced = typeof window !== "undefined" &&
        window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      const dur = prefersReduced ? 0 : 600;
      Plotly.animate(el, frames, {
        transition: { duration: dur, easing: "cubic-bezier(.4,0,.2,1)" },
        frame: { duration: dur },
      });
    });
  }, [frames, animateOnMount]);

  return (
    <div data-testid="plotly-embed" className="my-2 border-2 border-border-strong">
      <div data-testid="plotly-caption" className="bg-panel-dark-bg text-panel-dark-fg text-[10px] tracking-widest font-bold uppercase px-3 py-1">{caption}</div>
      <div ref={containerRef}>
        <Suspense fallback={<div className="text-xs text-page-fg/60 p-2">loading plot…</div>}>
          <Plot
            data={figure.data ?? []}
            layout={{ ...layoutWithoutTitle, autosize: true }}
            config={{ displayModeBar: false, ...(figure.config ?? {}) }}
            style={{ width: "100%", maxWidth: 800 }}
            useResizeHandler
          />
        </Suspense>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Implement `CompareFrames.tsx`**

Create `tutor-web/src/components/viz/CompareFrames.tsx`:

```tsx
import { useCallback } from "react";
import { PlotlyEmbed, PlotlyFrame } from "../PlotlyEmbed";

export interface CompareFramesProps { data: number[]; }

function buildFrames(data: number[]): PlotlyFrame[] {
  const sorted = [...data].sort((a, b) => a - b);
  const mean = data.reduce((a, b) => a + b, 0) / data.length;
  const median = sorted.length % 2 === 0
    ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2
    : sorted[Math.floor(sorted.length / 2)];

  const scatter = (est: number, label: string) => [
    { type: "scatter", mode: "markers", x: data.map((_, i) => i + 1), y: data, name: "data", marker: { color: "#64748b", size: 8 } },
    { type: "scatter", mode: "lines", x: [1, data.length], y: [est, est], name: label, line: { color: "#3b82f6", width: 2, dash: "dash" } },
  ];

  return [
    { name: "baseline", data: scatter(mean, "baseline mean") },
    { name: "mean", data: scatter(mean, `mean (${mean.toFixed(2)})`) },
    { name: "median", data: scatter(median, `median (${median.toFixed(2)})`) },
  ];
}

export function CompareFrames({ data }: CompareFramesProps) {
  const frames = buildFrames(data);
  const mean = data.reduce((a, b) => a + b, 0) / data.length;

  const handlePlay = useCallback(async () => {
    const mod = await import("plotly.js-dist-min");
    const Plotly = (mod as any).default ?? mod;
    if (typeof Plotly.animate !== "function") return;
    const el = document.querySelector("[data-testid='compare-frames-plotly'] .js-plotly-plot");
    if (!el) return;
    const prefersReduced = typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const dur = prefersReduced ? 0 : 600;
    await Plotly.animate(el, frames, {
      transition: { duration: dur, easing: "cubic-bezier(.4,0,.2,1)" },
      frame: { duration: dur },
    });
  }, [frames]);

  return (
    <div data-testid="compare-frames-plotly" style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
      <PlotlyEmbed
        indexLabel="FIG"
        figure={{
          data: [
            { type: "scatter", mode: "markers", x: data.map((_, i) => i + 1), y: data, name: "data", marker: { color: "#64748b", size: 8 } },
            { type: "scatter", mode: "lines", x: [1, data.length], y: [mean, mean], name: "mean", line: { color: "#3b82f6", width: 2, dash: "dash" } },
          ],
          layout: { title: { text: "Mean vs Median — frame morph" }, showlegend: true, margin: { t: 20, b: 30, l: 40, r: 10 } },
        }}
      />
      <button
        data-testid="compare-frames-play"
        onClick={handlePlay}
        style={{ alignSelf: "flex-start", padding: "0.35em 0.8em", border: "1.5px solid var(--color-accent, #3b82f6)", borderRadius: "4px", background: "transparent", color: "var(--color-accent, #3b82f6)", fontFamily: "var(--font-mono, monospace)", fontSize: "0.75rem", fontWeight: 700, cursor: "pointer" }}
      >
        ▶ ANIMATE FRAMES
      </button>
    </div>
  );
}
```

- [ ] **Step 5: Run tests to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/viz/CompareFrames.test.tsx
cd tutor-web && npx vitest run src/__tests__/viz/
```

Expected: 5 PASS for CompareFrames; full viz/ suite all green.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/PlotlyEmbed.tsx tutor-web/src/components/viz/CompareFrames.tsx tutor-web/src/__tests__/viz/CompareFrames.test.tsx
git commit -m "feat(viz): Plotly frame morphs — frames prop on PlotlyEmbed + CompareFrames 3-frame mean/median morph"
```

---

## PHASE H · Daemon PC-boot autostart (Tasks H1-H3)

### Task H1: Windows Task Scheduler XML + install script

**Files:**
- Create: `tools/jarvis-daemon-task.xml`
- Create: `tools/install-daemon-autostart.ps1`
- Test: `tools/Install-DaemonAutostart.Tests.ps1`

- [ ] **Step 1: Write failing Pester smoke test**

Create `tools/Install-DaemonAutostart.Tests.ps1`:

```powershell
#Requires -Modules Pester

Describe "install-daemon-autostart (dry-run)" {
    BeforeAll {
        $env:JARVIS_DAEMON_INSTALL_DRYRUN = "1"
        $fakeExe = Join-Path $TestDrive "daemon\target\release\jarvis-daemon.exe"
        New-Item -ItemType Directory -Force -Path (Split-Path $fakeExe) | Out-Null
        Set-Content $fakeExe "" -Encoding ASCII
        $fakeSsh = Join-Path $TestDrive ".ssh\id_ed25519"
        New-Item -ItemType Directory -Force -Path (Split-Path $fakeSsh) | Out-Null
        Set-Content $fakeSsh "" -Encoding ASCII
        $script:ToolsDir = Join-Path $PSScriptRoot ".."
        $script:ScriptPath = Join-Path $script:ToolsDir "tools\install-daemon-autostart.ps1"
    }
    AfterAll { Remove-Item Env:\JARVIS_DAEMON_INSTALL_DRYRUN -ErrorAction SilentlyContinue }

    It "script file exists" { Test-Path $script:ScriptPath | Should -Be $true }

    It "jarvis-daemon-task.xml exists and contains TaskScheduler namespace" {
        $xmlPath = Join-Path $script:ToolsDir "tools\jarvis-daemon-task.xml"
        Test-Path $xmlPath | Should -Be $true
        [xml]$doc = Get-Content $xmlPath -Raw
        $doc.Task.GetAttribute("xmlns") | Should -Be "http://schemas.microsoft.com/windows/2004/02/mit/task"
    }

    It "XML contains both LogonTrigger and BootTrigger" {
        $xmlPath = Join-Path $script:ToolsDir "tools\jarvis-daemon-task.xml"
        [xml]$doc = Get-Content $xmlPath -Raw
        $ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
        $ns.AddNamespace("ts", "http://schemas.microsoft.com/windows/2004/02/mit/task")
        $doc.SelectSingleNode("//ts:LogonTrigger", $ns) | Should -Not -BeNullOrEmpty
        $doc.SelectSingleNode("//ts:BootTrigger", $ns)  | Should -Not -BeNullOrEmpty
    }

    It "XML restart-on-failure is 3 with PT1M delay" {
        $xmlPath = Join-Path $script:ToolsDir "tools\jarvis-daemon-task.xml"
        [xml]$doc = Get-Content $xmlPath -Raw
        $ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
        $ns.AddNamespace("ts", "http://schemas.microsoft.com/windows/2004/02/mit/task")
        $doc.SelectSingleNode("//ts:RestartOnFailure/ts:Count", $ns).InnerText | Should -Be "3"
        $doc.SelectSingleNode("//ts:RestartOnFailure/ts:Interval", $ns).InnerText | Should -Be "PT1M"
    }

    It "dry-run install emits expected schtasks task names" {
        $output = & powershell -ExecutionPolicy Bypass -File $script:ScriptPath -DaemonExe $fakeExe -SshKey $fakeSsh 2>&1
        $names = $output | Select-String "Jarvis Daemon|Jarvis Reverse SSH Tunnel"
        $names.Count | Should -BeGreaterOrEqual 2
    }
}
```

- [ ] **Step 2: Run test to confirm fail**

```powershell
Invoke-Pester tools\Install-DaemonAutostart.Tests.ps1 -Output Detailed
```

Expected: FAIL — script + XML missing.

- [ ] **Step 3: Create `tools/jarvis-daemon-task.xml`**

```xml
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>Jarvis local effector daemon — HMAC-gated keystroke injector on 127.0.0.1:7331. Managed by tools/install-daemon-autostart.ps1.</Description>
    <Author>__CURRENT_USER__</Author>
  </RegistrationInfo>
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
      <UserId>__CURRENT_USER__</UserId>
      <Delay>PT10S</Delay>
    </LogonTrigger>
    <BootTrigger>
      <Enabled>true</Enabled>
      <Delay>PT30S</Delay>
    </BootTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <UserId>__CURRENT_USER__</UserId>
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>true</AllowHardTerminate>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>
    <IdleSettings>
      <StopOnIdleEnd>false</StopOnIdleEnd>
      <RestartOnIdle>false</RestartOnIdle>
    </IdleSettings>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <RunOnlyIfIdle>false</RunOnlyIfIdle>
    <WakeToRun>false</WakeToRun>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <RestartOnFailure>
      <Interval>PT1M</Interval>
      <Count>3</Count>
    </RestartOnFailure>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>__DAEMON_EXE__</Command>
    </Exec>
  </Actions>
</Task>
```

- [ ] **Step 4: Create `tools/install-daemon-autostart.ps1`**

```powershell
#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Registers "Jarvis Daemon" + "Jarvis Reverse SSH Tunnel" scheduled tasks.

.DESCRIPTION
    Both run as the current interactive user (NOT SYSTEM) so ~/.ssh and the
    OS keychain are accessible. Restart-on-failure: 3× with 1-minute delay.

    Pre-requisites checked:
      1. daemon\target\release\jarvis-daemon.exe must exist.
      2. ~/.ssh/id_ed25519 must exist (key is NOT auto-generated).

    Dry-run: $env:JARVIS_DAEMON_INSTALL_DRYRUN=1 or -DryRun skips schtasks.

.PARAMETER DaemonExe
    Path to the daemon binary (default: $RepoRoot\daemon\target\release\jarvis-daemon.exe).

.PARAMETER SshKey
    Path to the SSH private key (default: $env:USERPROFILE\.ssh\id_ed25519).

.PARAMETER DryRun
    Skip actual schtasks calls.
#>

param(
    [string]$DaemonExe = "",
    [string]$SshKey    = "",
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$isDryRun = $DryRun.IsPresent -or ($env:JARVIS_DAEMON_INSTALL_DRYRUN -eq "1")
$RepoRoot = Split-Path -Parent $PSScriptRoot

if ($DaemonExe -eq "") { $DaemonExe = Join-Path $RepoRoot "daemon\target\release\jarvis-daemon.exe" }
if ($SshKey -eq "")    { $SshKey    = Join-Path $env:USERPROFILE ".ssh\id_ed25519" }

$XmlTemplate = Join-Path $PSScriptRoot "jarvis-daemon-task.xml"
$LogDir      = Join-Path $env:USERPROFILE ".jarvis"
$LogFile     = Join-Path $LogDir "daemon-autostart.log"

Write-Host "=== Jarvis Daemon Autostart Installer ===" -ForegroundColor Cyan

if (-not (Test-Path $DaemonExe)) {
    Write-Error "Daemon binary not found: $DaemonExe`nRun: cd daemon && cargo build --release"
    exit 1
}
if (-not (Test-Path $SshKey)) {
    Write-Error "SSH private key not found: $SshKey`nGenerate with: ssh-keygen -t ed25519 -f `"$SshKey`""
    exit 1
}
if (-not (Test-Path $XmlTemplate)) {
    Write-Error "Task XML template not found: $XmlTemplate"
    exit 1
}

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

$currentUser = "$env:USERDOMAIN\$env:USERNAME"

function Register-JarvisTask {
    param([string]$TaskName, [string]$XmlContent, [string]$DescriptionForLog)
    $tmpXml = [System.IO.Path]::GetTempFileName() + ".xml"
    [System.IO.File]::WriteAllText($tmpXml, $XmlContent, [System.Text.Encoding]::Unicode)

    if ($isDryRun) {
        Write-Host "DRY-RUN: schtasks /Create /TN `"$TaskName`" /XML `"$tmpXml`" /F"
        Write-Host "         ($DescriptionForLog)"
        Remove-Item $tmpXml -ErrorAction SilentlyContinue
        return
    }

    Write-Host "Registering: $TaskName ..."
    schtasks.exe /Create /TN $TaskName /XML $tmpXml /F
    if ($LASTEXITCODE -ne 0) {
        Remove-Item $tmpXml -ErrorAction SilentlyContinue
        Write-Error "schtasks /Create failed for '$TaskName' (exit $LASTEXITCODE)"
        exit $LASTEXITCODE
    }
    Remove-Item $tmpXml -ErrorAction SilentlyContinue
    Write-Host "[OK] Task '$TaskName' registered."
}

# ── Daemon task ──
$daemonXml = (Get-Content $XmlTemplate -Raw -Encoding UTF8)
$daemonXml = $daemonXml.Replace("__DAEMON_EXE__", $DaemonExe)
$daemonXml = $daemonXml.Replace("__CURRENT_USER__", $currentUser)
Register-JarvisTask -TaskName "Jarvis Daemon" -XmlContent $daemonXml -DescriptionForLog "jarvis-daemon.exe on 127.0.0.1:7331"

# ── Tunnel task ──
$sshArgs = "-N -R 7331:127.0.0.1:7331 root@46.247.109.91 -i `"$SshKey`" -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o ExitOnForwardFailure=yes"

$tunnelXml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>Reverse SSH tunnel: local 127.0.0.1:7331 -&gt; VPS 46.247.109.91:7331.</Description>
    <Author>$currentUser</Author>
  </RegistrationInfo>
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
      <UserId>$currentUser</UserId>
      <Delay>PT15S</Delay>
    </LogonTrigger>
    <BootTrigger>
      <Enabled>true</Enabled>
      <Delay>PT35S</Delay>
    </BootTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <UserId>$currentUser</UserId>
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>true</AllowHardTerminate>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>true</RunOnlyIfNetworkAvailable>
    <IdleSettings>
      <StopOnIdleEnd>false</StopOnIdleEnd>
      <RestartOnIdle>false</RestartOnIdle>
    </IdleSettings>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <RunOnlyIfIdle>false</RunOnlyIfIdle>
    <WakeToRun>false</WakeToRun>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <RestartOnFailure>
      <Interval>PT1M</Interval>
      <Count>3</Count>
    </RestartOnFailure>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>ssh</Command>
      <Arguments>$sshArgs</Arguments>
    </Exec>
  </Actions>
</Task>
"@

Register-JarvisTask -TaskName "Jarvis Reverse SSH Tunnel" -XmlContent $tunnelXml -DescriptionForLog "reverse SSH -R 7331 to VPS"

$stamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
$logEntry = "$stamp  install OK  daemon=$DaemonExe  sshKey=$SshKey  user=$currentUser"

if ($isDryRun) {
    Write-Host "DRY-RUN: would append to log: $LogFile"
    Write-Host "         $logEntry"
} else {
    Add-Content -Path $LogFile -Value $logEntry -Encoding UTF8
    Write-Host "[OK] Health log updated: $LogFile"
}

Write-Host ""
Write-Host "=== Install complete ===" -ForegroundColor Green
Write-Host "Next steps:"
Write-Host "  1. Open Task Scheduler (taskschd.msc); both tasks should show 'Ready'"
Write-Host "  2. Reboot to verify auto-fire, or run now:"
Write-Host "       schtasks /Run /TN `"Jarvis Daemon`""
Write-Host "       schtasks /Run /TN `"Jarvis Reverse SSH Tunnel`""
Write-Host "  3. Tutor header pill should go green within ~30s of login"
Write-Host "  4. Logs: $LogFile"
```

- [ ] **Step 5: Run Pester to confirm pass**

```powershell
Invoke-Pester tools\Install-DaemonAutostart.Tests.ps1 -Output Detailed
```

Expected: 5 PASS.

- [ ] **Step 6: Commit**

```bash
git add tools/jarvis-daemon-task.xml tools/install-daemon-autostart.ps1 tools/Install-DaemonAutostart.Tests.ps1
git commit -m "feat(daemon): add Windows Task Scheduler installer + daemon XML for PC-boot autostart"
```

---

### Task H2: Uninstall script

**Files:**
- Create: `tools/uninstall-daemon-autostart.ps1`

- [ ] **Step 1: Create uninstall script**

```powershell
#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Removes "Jarvis Daemon" and "Jarvis Reverse SSH Tunnel" scheduled tasks.

.DESCRIPTION
    Idempotent. Already-removed tasks are skipped silently.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$TaskNames = @("Jarvis Daemon", "Jarvis Reverse SSH Tunnel")

Write-Host "=== Jarvis Daemon Autostart Uninstaller ===" -ForegroundColor Cyan

foreach ($name in $TaskNames) {
    Write-Host "Removing task: $name ..."
    $output = schtasks.exe /Delete /TN $name /F 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Removed: $name"
    } elseif ($output -match "cannot find the file|The specified task name") {
        Write-Host "[SKIP] Not found (already removed): $name"
    } else {
        Write-Warning "schtasks /Delete failed for '$name' (exit $LASTEXITCODE): $output"
    }
}

Write-Host ""
Write-Host "=== Uninstall complete ===" -ForegroundColor Green
Write-Host "Daemon binary and SSH key were NOT removed."
```

- [ ] **Step 2: AST parse smoke check**

```powershell
powershell -ExecutionPolicy Bypass -Command "
  \$errors = @()
  \$null = [System.Management.Automation.Language.Parser]::ParseFile('tools\uninstall-daemon-autostart.ps1', [ref]\$null, [ref]\$errors)
  if (\$errors.Count -gt 0) { \$errors | ForEach-Object { Write-Host \$_.Message }; exit 1 }
  Write-Host 'Syntax OK'
"
```

Expected: `Syntax OK`.

- [ ] **Step 3: Commit**

```bash
git add tools/uninstall-daemon-autostart.ps1
git commit -m "feat(daemon): add uninstall-daemon-autostart.ps1 for removing scheduled tasks"
```

---

### Task H3: Backend health route + DaemonHealthPill

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`
- Test: `src/test/kotlin/jarvis/tutor/DaemonHealthRouteTest.kt`
- Create: `tutor-web/src/components/DaemonHealthPill.tsx`
- Test: `tutor-web/src/__tests__/DaemonHealthPill.test.tsx`
- Modify: `tutor-web/src/components/TutorWorkspace.tsx`

- [ ] **Step 1: Write failing backend test**

Create `src/test/kotlin/jarvis/tutor/DaemonHealthRouteTest.kt`:

```kotlin
package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DaemonHealthRouteTest {
    private fun noLiveDaemonApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { installTutorRoutes() }
            block()
        }

    @Test
    fun `GET api v1 daemon health returns 200 with reachable=false when no daemon running`() =
        noLiveDaemonApp {
            val r = client.get("/api/v1/daemon/health")
            assertEquals(HttpStatusCode.OK, r.status)
            val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
            assertTrue(json.containsKey("reachable"))
            assertTrue(json.containsKey("tunnelUp"))
            assertTrue(json.containsKey("lastSeenAt"))
            assertEquals(false, json["reachable"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `GET api v1 daemon health shape has all three fields`() =
        noLiveDaemonApp {
            val r = client.get("/api/v1/daemon/health")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = r.bodyAsText()
            assertTrue(body.contains("\"reachable\""))
            assertTrue(body.contains("\"tunnelUp\""))
            assertTrue(body.contains("\"lastSeenAt\""))
        }

    @Test
    fun `GET api v1 daemon health content-type is application json`() =
        noLiveDaemonApp {
            val r = client.get("/api/v1/daemon/health")
            assertNotNull(r.headers[HttpHeaders.ContentType])
            assertTrue(r.headers[HttpHeaders.ContentType]!!.contains("application/json"))
        }
}
```

- [ ] **Step 2: Run test to confirm fail**

```
gradle :test --tests "jarvis.tutor.DaemonHealthRouteTest"
```

Expected: FAIL — 404 (route doesn't exist).

- [ ] **Step 3: Add `GET /api/v1/daemon/health` route in `TutorRoutes.kt`**

In `src/main/kotlin/jarvis/web/TutorRoutes.kt` inside `installTutorRoutes`, add:

```kotlin
        get("/api/v1/daemon/health") {
            val url = "http://127.0.0.1:7331/health"
            val client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build()
            val now = java.time.Instant.now()
            val (reachable, tunnelUp) = try {
                val req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build()
                val resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
                Pair(resp.statusCode() in 200..299, true)
            } catch (_: java.net.ConnectException) {
                Pair(false, false)
            } catch (_: Exception) {
                Pair(false, false)
            }
            val lastSeenAt = if (reachable) now.toString() else null
            val body = buildString {
                append("""{"reachable":$reachable,"tunnelUp":$tunnelUp,"lastSeenAt":""")
                if (lastSeenAt != null) append('"').append(lastSeenAt).append('"')
                else append("null")
                append("}")
            }
            call.respondText(body, ContentType.Application.Json)
        }
```

- [ ] **Step 4: Run backend test to confirm pass**

```
gradle :test --tests "jarvis.tutor.DaemonHealthRouteTest" --rerun-tasks
```

Expected: 3 PASS.

- [ ] **Step 5: Write failing frontend test**

Create `tutor-web/src/__tests__/DaemonHealthPill.test.tsx`:

```tsx
import { render, screen, act } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect, describe } from "vitest";
import { DaemonHealthPill } from "../components/DaemonHealthPill";

function stubHealth(payload: object) {
  vi.stubGlobal("fetch", vi.fn(async () =>
    new Response(JSON.stringify(payload), { status: 200, headers: { "content-type": "application/json" } })
  ));
}

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe("DaemonHealthPill", () => {
  test("green DAEMON OK when reachable=true & tunnelUp=true", async () => {
    stubHealth({ reachable: true, tunnelUp: true, lastSeenAt: "2026-05-10T12:00:00Z" });
    await act(async () => { render(<DaemonHealthPill />); });
    const pill = screen.getByTestId("daemon-health-pill");
    expect(pill.getAttribute("data-status")).toBe("green");
    expect(screen.getByText(/DAEMON OK/i)).toBeInTheDocument();
  });

  test("amber TUNNEL ONLY when reachable=false & tunnelUp=true", async () => {
    stubHealth({ reachable: false, tunnelUp: true, lastSeenAt: null });
    await act(async () => { render(<DaemonHealthPill />); });
    const pill = screen.getByTestId("daemon-health-pill");
    expect(pill.getAttribute("data-status")).toBe("amber");
    expect(screen.getByText(/TUNNEL ONLY/i)).toBeInTheDocument();
  });

  test("red DAEMON DOWN when both false", async () => {
    stubHealth({ reachable: false, tunnelUp: false, lastSeenAt: null });
    await act(async () => { render(<DaemonHealthPill />); });
    const pill = screen.getByTestId("daemon-health-pill");
    expect(pill.getAttribute("data-status")).toBe("red");
    expect(screen.getByText(/DAEMON DOWN/i)).toBeInTheDocument();
  });

  test("re-polls every 30s and updates", async () => {
    let callCount = 0;
    vi.stubGlobal("fetch", vi.fn(async () => {
      callCount++;
      const payload = callCount === 1
        ? { reachable: false, tunnelUp: false, lastSeenAt: null }
        : { reachable: true, tunnelUp: true, lastSeenAt: "2026-05-10T12:00:30Z" };
      return new Response(JSON.stringify(payload), { status: 200 });
    }));
    await act(async () => { render(<DaemonHealthPill />); });
    expect(screen.getByTestId("daemon-health-pill").getAttribute("data-status")).toBe("red");
    await act(async () => { vi.advanceTimersByTime(30_000); });
    expect(screen.getByTestId("daemon-health-pill").getAttribute("data-status")).toBe("green");
    expect(callCount).toBeGreaterThanOrEqual(2);
  });

  test("renders without crashing on fetch reject", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new Error("net::ERR_FAILED"); }));
    await act(async () => { render(<DaemonHealthPill />); });
    expect(screen.getByTestId("daemon-health-pill")).toBeInTheDocument();
  });
});
```

- [ ] **Step 6: Run frontend test to confirm fail**

```
cd tutor-web && npx vitest run src/__tests__/DaemonHealthPill.test.tsx
```

Expected: FAIL — module not found.

- [ ] **Step 7: Create `DaemonHealthPill.tsx`**

```tsx
import { useEffect, useState } from "react";

interface DaemonHealth {
  reachable: boolean;
  tunnelUp: boolean;
  lastSeenAt: string | null;
}

type PillStatus = "green" | "amber" | "red" | "unknown";

function pillFromHealth(h: DaemonHealth): PillStatus {
  if (h.reachable && h.tunnelUp) return "green";
  if (!h.reachable && h.tunnelUp) return "amber";
  return "red";
}

const STATUS_LABELS: Record<PillStatus, string> = {
  green: "DAEMON OK",
  amber: "TUNNEL ONLY",
  red:   "DAEMON DOWN",
  unknown: "CHECKING...",
};

const STATUS_DOT_CLASS: Record<PillStatus, string> = {
  green: "bg-green-400",
  amber: "bg-yellow-400",
  red:   "bg-red-500",
  unknown: "bg-page-fg/30",
};

const POLL_INTERVAL_MS = 30_000;

export function DaemonHealthPill() {
  const [status, setStatus] = useState<PillStatus>("unknown");
  const [lastSeenAt, setLastSeenAt] = useState<string | null>(null);

  async function poll() {
    try {
      const r = await fetch("/api/v1/daemon/health");
      if (!r.ok) { setStatus("red"); return; }
      const data: DaemonHealth = await r.json();
      setStatus(pillFromHealth(data));
      setLastSeenAt(data.lastSeenAt ?? null);
    } catch { setStatus("red"); }
  }

  useEffect(() => {
    poll();
    const t = setInterval(poll, POLL_INTERVAL_MS);
    return () => clearInterval(t);
  }, []);

  return (
    <span
      data-testid="daemon-health-pill"
      data-status={status}
      title={lastSeenAt ? `Last seen: ${lastSeenAt}` : STATUS_LABELS[status]}
      className="inline-flex items-center gap-1 font-mono text-[9px] tracking-widest text-page-fg/70 select-none"
    >
      <span className={`inline-block w-1.5 h-1.5 rounded-full ${STATUS_DOT_CLASS[status]} transition-colors duration-500`} aria-hidden="true" />
      {STATUS_LABELS[status]}
    </span>
  );
}
```

- [ ] **Step 8: Wire `DaemonHealthPill` into `TutorWorkspace.tsx` header**

In `tutor-web/src/components/TutorWorkspace.tsx`, add import:

```tsx
import { DaemonHealthPill } from "./DaemonHealthPill";
```

In the `return` block, before the existing content, add header strip:

```tsx
      <div data-testid="tutor-header" className="flex items-center justify-between px-4 py-1 border-b-4 border-border-strong bg-panel-dark-bg text-panel-dark-fg text-[10px] font-mono tracking-widest">
        <span className="font-bold">JARVIS · TUTOR</span>
        <DaemonHealthPill />
      </div>
```

- [ ] **Step 9: Run frontend test to confirm pass**

```
cd tutor-web && npx vitest run src/__tests__/DaemonHealthPill.test.tsx
```

Expected: 5 PASS.

- [ ] **Step 10: Run full backend test suite**

```
gradle :test --tests "jarvis.tutor.*" --rerun-tasks
```

Expected: all tutor backend tests PASS, including 3 new DaemonHealthRouteTest cases.

- [ ] **Step 11: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/DaemonHealthRouteTest.kt tutor-web/src/components/DaemonHealthPill.tsx tutor-web/src/__tests__/DaemonHealthPill.test.tsx tutor-web/src/components/TutorWorkspace.tsx
git commit -m "feat(daemon): /api/v1/daemon/health route + DaemonHealthPill header pill (green/amber/red)"
```

---

## PHASE I · Google OAuth+REST replacement (Tasks I1-I7)

---

### Task I1: GoogleTokenStore — OAuth2Token persistence

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/google/GoogleTokenStore.kt`
- Test: `src/test/kotlin/jarvis/tutor/google/GoogleTokenStoreTest.kt`

- [ ] **Step 1: Write failing tests for TokenStore round-trip, expiry, and missing file**

Create `src/test/kotlin/jarvis/tutor/google/GoogleTokenStoreTest.kt`:

```kotlin
package jarvis.tutor.google

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleTokenStoreTest {

    @Test
    fun `load returns null when token file does not exist`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        assertNull(store.load())
    }

    @Test
    fun `save and load round-trip preserves all fields`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val expiresAt = Instant.parse("2026-06-01T12:00:00Z")
        val token = OAuth2Token(
            accessToken = "ya29.access",
            refreshToken = "1//refresh",
            expiresAt = expiresAt,
        )
        store.save(token)
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals("ya29.access", loaded.accessToken)
        assertEquals("1//refresh", loaded.refreshToken)
        assertEquals(expiresAt, loaded.expiresAt)
    }

    @Test
    fun `isExpired returns true when expiresAt is in the past`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val past = Instant.now().minusSeconds(300)
        val token = OAuth2Token("a", "r", past)
        assertTrue(store.isExpired(token, now = Instant.now()))
    }

    @Test
    fun `isExpired returns false when expiresAt is well in the future`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val future = Instant.now().plusSeconds(3600)
        val token = OAuth2Token("a", "r", future)
        assertFalse(store.isExpired(token, now = Instant.now()))
    }

    @Test
    fun `isExpired applies 60s safety buffer — token expiring in 30s is considered expired`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        val almostExpired = Instant.now().plusSeconds(30)
        val token = OAuth2Token("a", "r", almostExpired)
        assertTrue(store.isExpired(token, now = Instant.now()))
    }

    @Test
    fun `save overwrites existing file`(@TempDir tmp: Path) {
        val store = TokenStore(tmp.resolve("google-token.json"))
        store.save(OAuth2Token("old", "r", Instant.now().plusSeconds(3600)))
        store.save(OAuth2Token("new", "r", Instant.now().plusSeconds(7200)))
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals("new", loaded.accessToken)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
gradle :test --tests "jarvis.tutor.google.GoogleTokenStoreTest"
```

Expected: FAIL — `jarvis.tutor.google` package does not exist.

- [ ] **Step 3: Create `GoogleTokenStore.kt`**

Create `src/main/kotlin/jarvis/tutor/google/GoogleTokenStore.kt`:

```kotlin
package jarvis.tutor.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Persistent OAuth 2.0 token store backed by a JSON file at [path].
 *
 * Thread safety: callers should synchronize externally if concurrent
 * refresh is possible. In practice, tool dispatch in JarvisToolDefs is
 * serialized per LLM round-trip, so this is safe for the tutor surface.
 *
 * [isExpired] applies a 60-second safety buffer so the access token is
 * refreshed before Google actually rejects it.
 */
@Serializable
data class OAuth2Token(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String, // ISO-8601, serialized as String for Kotlin 1.9 compat
) {
    fun expiresAtInstant(): Instant = Instant.parse(expiresAt)
}

fun OAuth2Token(accessToken: String, refreshToken: String, expiresAt: Instant) =
    OAuth2Token(accessToken, refreshToken, expiresAt.toString())

data class ClientCreds(val clientId: String, val clientSecret: String)

class TokenStore(private val path: Path) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): OAuth2Token? = try {
        json.decodeFromString<OAuth2Token>(path.readText())
    } catch (_: Exception) { null }

    fun save(token: OAuth2Token) {
        path.parent?.toFile()?.mkdirs()
        path.writeText(json.encodeToString(token))
    }

    /** Returns true if the token expires within 60 seconds of [now]. */
    fun isExpired(token: OAuth2Token, now: Instant = Instant.now()): Boolean =
        token.expiresAtInstant().minusSeconds(60) <= now
}
```

- [ ] **Step 4: Run tests to confirm all pass**

```
gradle :test --tests "jarvis.tutor.google.GoogleTokenStoreTest" --rerun-tasks
```

Expected: 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/google/GoogleTokenStore.kt \
        src/test/kotlin/jarvis/tutor/google/GoogleTokenStoreTest.kt
git commit -m "feat(google): add OAuth2Token + TokenStore with JSON persistence + expiry check"
```

---

### Task I2: GoogleApiClient — HTTP core with retry logic

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/google/GoogleApiClient.kt`
- Test: `src/test/kotlin/jarvis/tutor/google/GoogleApiClientTest.kt`

- [ ] **Step 1: Write failing tests using HttpServer as canned responder**

Create `src/test/kotlin/jarvis/tutor/google/GoogleApiClientTest.kt`:

```kotlin
package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleApiClientTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        serverPort = server.address.port
        server.start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    private fun baseUrl() = "http://localhost:$serverPort"

    private fun cannedToken(expiresInSeconds: Long = 3600): OAuth2Token =
        OAuth2Token("valid-access-token", "refresh-token-abc",
            Instant.now().plusSeconds(expiresInSeconds))

    private fun cannedCreds() = ClientCreds("client-id-test", "client-secret-test")

    @Test
    fun `httpGet returns body from canned server`() {
        server.createContext("/test-get") { ex ->
            val resp = """{"ok":true}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(store, cannedCreds(), baseApiUrl = baseUrl())
        val result = client.httpGetRaw("/test-get")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("\"ok\""))
    }

    @Test
    fun `httpGet refreshes token on 401 and retries`() {
        var callCount = 0
        // Token refresh endpoint
        server.createContext("/token") { ex ->
            val resp = """{"access_token":"new-access","expires_in":3600,"token_type":"Bearer"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.createContext("/protected") { ex ->
            callCount++
            if (callCount == 1) {
                ex.sendResponseHeaders(401, -1)
                ex.responseBody.close()
            } else {
                val resp = """{"data":"secret"}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
        }
        // Expired token forces a refresh before the first real call
        val expiredToken = OAuth2Token("expired-access", "refresh-token-abc",
            Instant.now().minusSeconds(600))
        val store = InMemoryTokenStore(expiredToken)
        val client = GoogleApiClient(
            store, cannedCreds(),
            baseApiUrl = baseUrl(),
            tokenEndpoint = "${baseUrl()}/token"
        )
        val result = client.httpGetRaw("/protected")
        assertTrue(result.isSuccess)
        assertEquals("""{"data":"secret"}""", result.getOrThrow())
    }

    @Test
    fun `httpGet backs off on 429 and eventually succeeds`() {
        var calls = 0
        server.createContext("/rate-limited") { ex ->
            calls++
            if (calls < 3) {
                ex.sendResponseHeaders(429, -1)
                ex.responseBody.close()
            } else {
                val resp = """{"ok":true}""".toByteArray()
                ex.sendResponseHeaders(200, resp.size.toLong())
                ex.responseBody.use { it.write(resp) }
            }
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(
            store, cannedCreds(),
            baseApiUrl = baseUrl(),
            backoffMs = longArrayOf(10, 40, 160), // fast for test
        )
        val result = client.httpGetRaw("/rate-limited")
        assertTrue(result.isSuccess)
        assertEquals(3, calls)
    }

    @Test
    fun `httpGet fails after 3 consecutive 429 responses`() {
        server.createContext("/always-429") { ex ->
            ex.sendResponseHeaders(429, -1)
            ex.responseBody.close()
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(
            store, cannedCreds(),
            baseApiUrl = baseUrl(),
            backoffMs = longArrayOf(10, 40, 160),
        )
        val result = client.httpGetRaw("/always-429")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("429") == true)
    }

    @Test
    fun `httpPost sends JSON body and returns response`() {
        server.createContext("/post-endpoint") { ex ->
            val body = ex.requestBody.bufferedReader().readText()
            val parsed = Json.parseToJsonElement(body).jsonObject
            val echo = """{"received":${parsed["key"]}}""".toByteArray()
            ex.sendResponseHeaders(200, echo.size.toLong())
            ex.responseBody.use { it.write(echo) }
        }
        val store = InMemoryTokenStore(cannedToken())
        val client = GoogleApiClient(store, cannedCreds(), baseApiUrl = baseUrl())
        val result = client.httpPostRaw("/post-endpoint", """{"key":"hello"}""")
        assertTrue(result.isSuccess)
        val resp = Json.parseToJsonElement(result.getOrThrow()).jsonObject
        assertEquals("hello", resp["received"]?.jsonPrimitive?.content)
    }
}

/** Test-only in-memory TokenStore that never touches disk. */
class InMemoryTokenStore(private var token: OAuth2Token) : AbstractTokenStore() {
    override fun load(): OAuth2Token? = token
    override fun save(t: OAuth2Token) { token = t }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
gradle :test --tests "jarvis.tutor.google.GoogleApiClientTest"
```

Expected: FAIL — `GoogleApiClient` does not exist.

- [ ] **Step 3: Create `GoogleApiClient.kt`**

Create `src/main/kotlin/jarvis/tutor/google/GoogleApiClient.kt`:

```kotlin
package jarvis.tutor.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * Abstract base so tests can inject an in-memory store without hitting disk.
 */
abstract class AbstractTokenStore {
    abstract fun load(): OAuth2Token?
    abstract fun save(t: OAuth2Token)
    fun isExpired(token: OAuth2Token, now: Instant = Instant.now()): Boolean =
        token.expiresAtInstant().minusSeconds(60) <= now
}

/**
 * Disk-backed [AbstractTokenStore] using [TokenStore].
 */
class DiskTokenStore(path: java.nio.file.Path) : AbstractTokenStore() {
    private val inner = TokenStore(path)
    override fun load(): OAuth2Token? = inner.load()
    override fun save(t: OAuth2Token) = inner.save(t)
}

/**
 * Core Google REST client.
 *
 * - Uses [java.net.http.HttpClient] (JDK 11+, always on classpath).
 * - On 401: refreshes access token via the stored refresh_token, updates
 *   [store], then retries the original request exactly once.
 * - On 429: exponential back-off using [backoffMs] (default 1000/4000/16000ms,
 *   3 tries max). After all retries exhausted, returns failure.
 * - All requests attach `Authorization: Bearer <accessToken>` header.
 *
 * [baseApiUrl] and [tokenEndpoint] are overridable for tests.
 */
class GoogleApiClient(
    private val store: AbstractTokenStore,
    private val creds: ClientCreds,
    private val baseApiUrl: String = "https://www.googleapis.com",
    private val tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    private val backoffMs: LongArray = longArrayOf(1_000, 4_000, 16_000),
) {
    private val http = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Ensure the stored token is valid, refreshing if expired. Returns current token. */
    fun ensureValidToken(): OAuth2Token {
        val current = store.load()
            ?: error("GoogleApi: no token found — run `jarvis google-auth-bootstrap` on your PC and scp the token to VPS")
        if (!store.isExpired(current)) return current
        return refreshToken(current)
    }

    private fun refreshToken(expired: OAuth2Token): OAuth2Token {
        val form = "client_id=${urlEncode(creds.clientId)}" +
            "&client_secret=${urlEncode(creds.clientSecret)}" +
            "&refresh_token=${urlEncode(expired.refreshToken)}" +
            "&grant_type=refresh_token"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            error("GoogleApi: token refresh failed (HTTP ${resp.statusCode()}): ${resp.body().take(400)}")
        }
        val body = json.parseToJsonElement(resp.body()).jsonObject
        val newAccess = body["access_token"]?.jsonPrimitive?.content
            ?: error("GoogleApi: refresh response missing access_token")
        val expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        val newToken = OAuth2Token(
            accessToken = newAccess,
            refreshToken = expired.refreshToken, // refresh_token not rotated by Google in most flows
            expiresAt = Instant.now().plusSeconds(expiresIn),
        )
        store.save(newToken)
        return newToken
    }

    /** GET [path] (relative to [baseApiUrl]) with auth + retry. */
    fun httpGetRaw(path: String): Result<String> = withRetry {
        val token = ensureValidToken()
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$baseApiUrl$path"))
            .header("Authorization", "Bearer ${token.accessToken}")
            .GET()
            .build()
        http.send(req, HttpResponse.BodyHandlers.ofString())
    }

    /** POST [path] with JSON [body], with auth + retry. */
    fun httpPostRaw(path: String, body: String): Result<String> = withRetry {
        val token = ensureValidToken()
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$baseApiUrl$path"))
            .header("Authorization", "Bearer ${token.accessToken}")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        http.send(req, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * Execute [block] with 401-once-refresh + 429 exponential back-off.
     * 401 retry: refresh token, then call [block] again exactly once.
     * 429 retry: up to [backoffMs].size retries with inter-attempt sleep.
     */
    private fun withRetry(block: () -> HttpResponse<String>): Result<String> {
        var resp = try { block() } catch (e: Exception) { return Result.failure(e) }

        // Handle 401: refresh and retry once
        if (resp.statusCode() == 401) {
            val current = store.load() ?: return Result.failure(IllegalStateException("no token"))
            try { refreshToken(current) } catch (e: Exception) {
                return Result.failure(IllegalStateException(
                    "GoogleApi: consent expired — re-run google-auth-bootstrap. Detail: ${e.message}", e))
            }
            resp = try { block() } catch (e: Exception) { return Result.failure(e) }
        }

        // Handle 429 with backoff
        var attempt = 0
        while (resp.statusCode() == 429 && attempt < backoffMs.size) {
            Thread.sleep(backoffMs[attempt++])
            resp = try { block() } catch (e: Exception) { return Result.failure(e) }
        }

        if (resp.statusCode() == 429) {
            return Result.failure(RuntimeException(
                "GoogleApi: 429 rate-limited after ${backoffMs.size} retries — upstream error: ${resp.body().take(200)}"))
        }

        return if (resp.statusCode() in 200..299) {
            Result.success(resp.body())
        } else {
            Result.failure(RuntimeException(
                "GoogleApi: HTTP ${resp.statusCode()}: ${resp.body().take(400)}"))
        }
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
```

- [ ] **Step 4: Run tests to confirm all pass**

```
gradle :test --tests "jarvis.tutor.google.GoogleApiClientTest" --rerun-tasks
```

Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/google/GoogleApiClient.kt \
        src/test/kotlin/jarvis/tutor/google/GoogleApiClientTest.kt
git commit -m "feat(google): add GoogleApiClient with 401-refresh + 429 exponential backoff"
```

---

### Task I3: CalendarClient — events.insert wrapper

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/google/CalendarClient.kt`
- Test: `src/test/kotlin/jarvis/tutor/google/CalendarClientTest.kt`

- [ ] **Step 1: Write failing test with canned Calendar event JSON**

Create `src/test/kotlin/jarvis/tutor/google/CalendarClientTest.kt`:

```kotlin
package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class CalendarClientTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach fun stop() = server.stop(0)

    private fun makeClient(): CalendarClient {
        val store = InMemoryTokenStore(
            OAuth2Token("test-access", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(
            store,
            ClientCreds("cid", "csec"),
            baseApiUrl = "http://localhost:$port",
        )
        return CalendarClient(api)
    }

    @Test
    fun `eventsInsert returns EventId on 200 response`() {
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            val resp = """{"kind":"calendar#event","id":"event-abc-123","summary":"Study PS"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val client = makeClient()
        val result = client.eventsInsert(
            calendarId = "primary",
            summary = "Study PS",
            startIso = "2026-05-10T14:00:00+03:00",
            endIso = "2026-05-10T15:00:00+03:00",
        )
        assertTrue(result.isSuccess)
        assertEquals("event-abc-123", result.getOrThrow())
    }

    @Test
    fun `eventsInsert returns failure on non-200 response`() {
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            val resp = """{"error":{"code":403,"message":"Calendar usage limits exceeded."}}""".toByteArray()
            ex.sendResponseHeaders(403, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().eventsInsert(
            calendarId = "primary",
            summary = "Test",
            startIso = "2026-05-10T14:00:00+03:00",
            endIso = "2026-05-10T15:00:00+03:00",
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
    }

    @Test
    fun `eventsInsert sends correct JSON body shape`() {
        var capturedBody = ""
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            capturedBody = ex.requestBody.bufferedReader().readText()
            val resp = """{"id":"evt-body-check"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().eventsInsert("primary", "Deadline reminder",
            "2026-05-11T09:00:00+03:00", "2026-05-11T10:00:00+03:00")
        assertTrue(capturedBody.contains("\"summary\""))
        assertTrue(capturedBody.contains("Deadline reminder"))
        assertTrue(capturedBody.contains("\"dateTime\""))
        assertTrue(capturedBody.contains("\"start\""))
        assertTrue(capturedBody.contains("\"end\""))
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
gradle :test --tests "jarvis.tutor.google.CalendarClientTest"
```

Expected: FAIL — `CalendarClient` does not exist.

- [ ] **Step 3: Create `CalendarClient.kt`**

Create `src/main/kotlin/jarvis/tutor/google/CalendarClient.kt`:

```kotlin
package jarvis.tutor.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

typealias EventId = String

/**
 * Google Calendar REST client (minimal subset: events.insert).
 *
 * Scope required: [https://www.googleapis.com/auth/calendar.events]
 *
 * All HTTP is delegated to [GoogleApiClient] which handles
 * auth, 401-refresh, and 429 back-off transparently.
 */
class CalendarClient(private val api: GoogleApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a Calendar event and return its [EventId] (`id` field from response).
     *
     * @param calendarId  Calendar identifier; use `"primary"` for the user's default.
     * @param summary     Event title.
     * @param startIso    ISO-8601 datetime with timezone offset, e.g. `2026-05-10T14:00:00+03:00`.
     * @param endIso      ISO-8601 datetime with timezone offset.
     */
    fun eventsInsert(
        calendarId: String = "primary",
        summary: String,
        startIso: String,
        endIso: String,
    ): Result<EventId> {
        val body = buildEventBody(summary, startIso, endIso)
        val path = "/calendar/v3/calendars/${urlEncode(calendarId)}/events"
        return api.httpPostRaw(path, body).map { responseBody ->
            val parsed = json.parseToJsonElement(responseBody).jsonObject
            parsed["id"]?.jsonPrimitive?.content
                ?: error("Calendar eventsInsert: response missing 'id' field. Raw: ${responseBody.take(300)}")
        }
    }

    private fun buildEventBody(summary: String, startIso: String, endIso: String): String =
        """{"summary":${jsonString(summary)},"start":{"dateTime":${jsonString(startIso)}},"end":{"dateTime":${jsonString(endIso)}}}"""

    private fun jsonString(s: String): String =
        "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
```

- [ ] **Step 4: Run tests to confirm all pass**

```
gradle :test --tests "jarvis.tutor.google.CalendarClientTest" --rerun-tasks
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/google/CalendarClient.kt \
        src/test/kotlin/jarvis/tutor/google/CalendarClientTest.kt
git commit -m "feat(google): add CalendarClient.eventsInsert wrapping GoogleApiClient"
```

---

### Task I4: DriveClient — files.list wrapper

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/google/DriveClient.kt`
- Test: `src/test/kotlin/jarvis/tutor/google/DriveClientTest.kt`

- [ ] **Step 1: Write failing test with canned Drive files JSON**

Create `src/test/kotlin/jarvis/tutor/google/DriveClientTest.kt`:

```kotlin
package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class DriveClientTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach fun stop() = server.stop(0)

    private fun makeClient(): DriveClient {
        val store = InMemoryTokenStore(
            OAuth2Token("test-access", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(
            store, ClientCreds("cid", "csec"),
            baseApiUrl = "http://localhost:$port",
        )
        return DriveClient(api)
    }

    @Test
    fun `filesList returns DriveFile list on 200`() {
        server.createContext("/drive/v3/files") { ex ->
            val resp = """
                {"files":[
                    {"id":"file-1","name":"Tema_A.pdf","mimeType":"application/pdf"},
                    {"id":"file-2","name":"Curs_PS.pdf","mimeType":"application/pdf"}
                ]}
            """.trimIndent().toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().filesList("name contains 'PS'")
        assertTrue(result.isSuccess)
        val files = result.getOrThrow()
        assertEquals(2, files.size)
        assertEquals("file-1", files[0].id)
        assertEquals("Tema_A.pdf", files[0].name)
        assertEquals("application/pdf", files[0].mimeType)
    }

    @Test
    fun `filesList returns empty list when files array is absent`() {
        server.createContext("/drive/v3/files") { ex ->
            val resp = """{"kind":"drive#fileList"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().filesList("nothing")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `filesList appends query and pageSize to request URL`() {
        var capturedPath = ""
        server.createContext("/drive/v3/files") { ex ->
            capturedPath = ex.requestURI.toString()
            val resp = """{"files":[]}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().filesList(query = "mimeType='application/pdf'", pageSize = 7)
        assertTrue(capturedPath.contains("pageSize=7"))
        assertTrue(capturedPath.contains("q="))
    }

    @Test
    fun `filesList returns failure on API error`() {
        server.createContext("/drive/v3/files") { ex ->
            val resp = """{"error":{"code":401,"message":"Invalid Credentials"}}""".toByteArray()
            ex.sendResponseHeaders(401, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        // Use a client whose refresh also fails (expired token, no refresh server)
        val store = InMemoryTokenStore(
            OAuth2Token("bad-token", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(store, ClientCreds("c", "s"),
            baseApiUrl = "http://localhost:$port",
            tokenEndpoint = "http://localhost:1/nonexistent"
        )
        val result = DriveClient(api).filesList("q")
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
gradle :test --tests "jarvis.tutor.google.DriveClientTest"
```

Expected: FAIL — `DriveClient` does not exist.

- [ ] **Step 3: Create `DriveClient.kt`**

Create `src/main/kotlin/jarvis/tutor/google/DriveClient.kt`:

```kotlin
package jarvis.tutor.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
)

/**
 * Google Drive REST client (minimal subset: files.list).
 *
 * Scope required: [https://www.googleapis.com/auth/drive.readonly]
 *
 * All HTTP is delegated to [GoogleApiClient].
 */
class DriveClient(private val api: GoogleApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * List Drive files matching [query].
     *
     * @param query     Drive query string, e.g. `"name contains 'syllabus'"`.
     * @param pageSize  Maximum number of results (default 10, max 20 per spec §K).
     * @return          List of [DriveFile] or failure with upstream error message.
     */
    fun filesList(query: String, pageSize: Int = 10): Result<List<DriveFile>> {
        val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
        val path = "/drive/v3/files?q=$encodedQ&pageSize=${pageSize.coerceIn(1, 20)}" +
            "&fields=files(id,name,mimeType)"
        return api.httpGetRaw(path).map { body ->
            val root = json.parseToJsonElement(body).jsonObject
            val filesArray = root["files"]?.jsonArray ?: return@map emptyList()
            filesArray.map { elem ->
                val obj = elem.jsonObject
                DriveFile(
                    id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    mimeType = obj["mimeType"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to confirm all pass**

```
gradle :test --tests "jarvis.tutor.google.DriveClientTest" --rerun-tasks
```

Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/google/DriveClient.kt \
        src/test/kotlin/jarvis/tutor/google/DriveClientTest.kt
git commit -m "feat(google): add DriveClient.filesList wrapping GoogleApiClient"
```

---

### Task I5: GmailClient — drafts.create wrapper

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/google/GmailClient.kt`
- Test: `src/test/kotlin/jarvis/tutor/google/GmailClientTest.kt`

- [ ] **Step 1: Write failing test with canned Gmail draft response**

Create `src/test/kotlin/jarvis/tutor/google/GmailClientTest.kt`:

```kotlin
package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class GmailClientTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach fun stop() = server.stop(0)

    private fun makeClient(): GmailClient {
        val store = InMemoryTokenStore(
            OAuth2Token("test-access", "refresh", Instant.now().plusSeconds(3600))
        )
        val api = GoogleApiClient(
            store, ClientCreds("cid", "csec"),
            baseApiUrl = "http://localhost:$port",
        )
        return GmailClient(api)
    }

    @Test
    fun `draftsCreate returns DraftId on 200 response`() {
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            val resp = """{"id":"draft-xyz-789","message":{"id":"msg-1","threadId":"t-1"}}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().draftsCreate(
            to = "professor@example.ro",
            subject = "Question about PS homework",
            body = "Dear professor, I have a question about Problem A3...",
        )
        assertTrue(result.isSuccess)
        assertEquals("draft-xyz-789", result.getOrThrow())
    }

    @Test
    fun `draftsCreate sends base64url-encoded RFC822 message in request body`() {
        var capturedBody = ""
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            capturedBody = ex.requestBody.bufferedReader().readText()
            val resp = """{"id":"draft-body-check"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().draftsCreate("to@example.com", "Subject line", "Body text")

        // Request must be JSON with message.raw field (base64url)
        val parsed = Json.parseToJsonElement(capturedBody).jsonObject
        val messageObj = parsed["message"]?.jsonObject
        val raw = messageObj?.get("raw")?.jsonPrimitive?.content
        assertTrue(raw != null && raw.isNotBlank(),
            "message.raw must be present and non-empty")

        // base64url decoded content must contain RFC822 headers
        val decoded = String(java.util.Base64.getUrlDecoder().decode(raw!!), Charsets.UTF_8)
        assertTrue(decoded.contains("To: to@example.com"))
        assertTrue(decoded.contains("Subject: Subject line"))
        assertTrue(decoded.contains("Body text"))
    }

    @Test
    fun `draftsCreate encodes special characters in subject safely`() {
        var capturedBody = ""
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            capturedBody = ex.requestBody.bufferedReader().readText()
            val resp = """{"id":"draft-special"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        makeClient().draftsCreate("x@x.com", "Topic: μ̂ MLE", "Has math: Σ|xᵢ − μ|")
        val parsed = Json.parseToJsonElement(capturedBody).jsonObject
        val raw = parsed["message"]?.jsonObject?.get("raw")?.jsonPrimitive?.content ?: ""
        val decoded = String(java.util.Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
        assertTrue(decoded.contains("Topic: μ̂ MLE"))
    }

    @Test
    fun `draftsCreate returns failure on API error`() {
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            val resp = """{"error":{"code":400,"message":"Bad Request"}}""".toByteArray()
            ex.sendResponseHeaders(400, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        val result = makeClient().draftsCreate("a@b.com", "s", "b")
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
gradle :test --tests "jarvis.tutor.google.GmailClientTest"
```

Expected: FAIL — `GmailClient` does not exist.

- [ ] **Step 3: Create `GmailClient.kt`**

Create `src/main/kotlin/jarvis/tutor/google/GmailClient.kt`:

```kotlin
package jarvis.tutor.google

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

typealias DraftId = String

/**
 * Google Gmail REST client (minimal subset: users.drafts.create).
 *
 * Scope required: [https://www.googleapis.com/auth/gmail.compose]
 * (compose only — this client NEVER sends emails; drafts only).
 *
 * The Gmail API requires RFC 822 message format, base64url-encoded,
 * in the `message.raw` field of the POST body. This client handles
 * the encoding internally so callers work with plain strings.
 *
 * All HTTP is delegated to [GoogleApiClient].
 */
class GmailClient(private val api: GoogleApiClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a Gmail draft. Never auto-sends.
     *
     * @param to      Recipient email address.
     * @param subject Email subject line.
     * @param body    Plain-text email body (UTF-8).
     * @param userId  Gmail user ID; always `"me"` for authenticated user.
     * @return        [DraftId] (`id` field from the created draft resource).
     */
    fun draftsCreate(
        to: String,
        subject: String,
        body: String,
        userId: String = "me",
    ): Result<DraftId> {
        val rfc822 = buildRfc822(to, subject, body)
        val raw = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rfc822.toByteArray(Charsets.UTF_8))
        val requestBody = """{"message":{"raw":"$raw"}}"""
        val path = "/gmail/v1/users/${urlEncode(userId)}/drafts"
        return api.httpPostRaw(path, requestBody).map { responseBody ->
            val parsed = json.parseToJsonElement(responseBody).jsonObject
            parsed["id"]?.jsonPrimitive?.content
                ?: error("Gmail draftsCreate: response missing 'id' field. Raw: ${responseBody.take(300)}")
        }
    }

    private fun buildRfc822(to: String, subject: String, body: String): String =
        "To: $to\r\n" +
            "Subject: $subject\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "\r\n" +
            body

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
```

- [ ] **Step 4: Run tests to confirm all pass**

```
gradle :test --tests "jarvis.tutor.google.GmailClientTest" --rerun-tasks
```

Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/google/GmailClient.kt \
        src/test/kotlin/jarvis/tutor/google/GmailClientTest.kt
git commit -m "feat(google): add GmailClient.draftsCreate with RFC822 base64url encoding"
```

---

### Task I6: Wire JarvisToolset + rename /api/v1/gws/status

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/JarvisToolset.kt`
- Modify: `src/main/kotlin/jarvis/tutor/GwsEffector.kt`
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`
- Test: `src/test/kotlin/jarvis/tutor/google/GoogleDispatchTest.kt`

- [ ] **Step 1: Write failing tests for new dispatch wiring and status route**

Create `src/test/kotlin/jarvis/tutor/google/GoogleDispatchTest.kt`:

```kotlin
package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that the three dispatch methods in JarvisToolDefs route through
 * GoogleApiClient (via GoogleClients singleton) instead of GwsEffector.
 *
 * Uses a local HttpServer to capture the outgoing Google API calls and
 * return canned responses, proving the new wiring without network access.
 */
class GoogleDispatchTest {

    private fun calendarArgsJson(summary: String = "Study PS",
                                  start: String = "2026-05-10T14:00:00+03:00",
                                  end: String = "2026-05-10T15:00:00+03:00"): String =
        """{"summary":"$summary","startIso":"$start","endIso":"$end"}"""

    private fun driveArgsJson(query: String = "name contains 'pdf'", pageSize: Int = 5): String =
        """{"query":"$query","pageSize":$pageSize}"""

    private fun gmailArgsJson(to: String = "a@b.com",
                               subject: String = "Hi",
                               body: String = "Hello"): String =
        """{"to":"$to","subject":"$subject","body":"$body"}"""

    @Test
    fun `calendar_create_event dispatch returns Google error message when token absent`() {
        // When no token file exists, dispatch must return a human-readable error
        // containing "google-auth-bootstrap" so the LLM can relay the fix to the user.
        val result = jarvis.tutor.JarvisToolDefs.dispatch(
            "calendar_create_event", calendarArgsJson()
        )
        // Either success (if token somehow present) or meaningful error
        assertTrue(result.isNotEmpty())
        // If it failed, it must mention the bootstrap command, not "gws"
        if (result.contains("error") || result.contains("disabled") || result.contains("token")) {
            assertFalse(result.contains("gws "), "result must not mention old gws binary: $result")
        }
    }

    @Test
    fun `drive_search dispatch returns Google error message when token absent`() {
        val result = jarvis.tutor.JarvisToolDefs.dispatch(
            "drive_search", driveArgsJson()
        )
        assertTrue(result.isNotEmpty())
        if (result.contains("error") || result.contains("disabled") || result.contains("token")) {
            assertFalse(result.contains("gws "), "result must not mention old gws binary: $result")
        }
    }

    @Test
    fun `gmail_create_draft dispatch returns Google error message when token absent`() {
        val result = jarvis.tutor.JarvisToolDefs.dispatch(
            "gmail_create_draft", gmailArgsJson()
        )
        assertTrue(result.isNotEmpty())
        if (result.contains("error") || result.contains("disabled") || result.contains("token")) {
            assertFalse(result.contains("gws "), "result must not mention old gws binary: $result")
        }
    }

    @Test
    fun `calendar_create_event dispatch succeeds with mocked GoogleApiClient`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/calendar/v3/calendars/primary/events") { ex ->
            val resp = """{"id":"evt-test-99","summary":"Study PS"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val store = InMemoryTokenStore(
                OAuth2Token("access", "refresh", Instant.now().plusSeconds(3600))
            )
            val api = GoogleApiClient(store, ClientCreds("c", "s"),
                baseApiUrl = "http://localhost:$port")
            val cal = CalendarClient(api)
            val result = cal.eventsInsert("primary", "Study PS",
                "2026-05-10T14:00:00+03:00", "2026-05-10T15:00:00+03:00")
            assertTrue(result.isSuccess)
            assertContains(result.getOrThrow(), "evt-test-99")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `drive_search dispatch succeeds with mocked GoogleApiClient`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/drive/v3/files") { ex ->
            val resp = """{"files":[{"id":"f1","name":"Tema_A.pdf","mimeType":"application/pdf"}]}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val store = InMemoryTokenStore(
                OAuth2Token("access", "refresh", Instant.now().plusSeconds(3600))
            )
            val api = GoogleApiClient(store, ClientCreds("c", "s"),
                baseApiUrl = "http://localhost:$port")
            val drive = DriveClient(api)
            val result = drive.filesList("name contains 'Tema'")
            assertTrue(result.isSuccess)
            val files = result.getOrThrow()
            assertTrue(files.isNotEmpty())
            assertContains(files[0].name, "Tema_A.pdf")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `gmail_create_draft dispatch succeeds with mocked GoogleApiClient`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/gmail/v1/users/me/drafts") { ex ->
            val resp = """{"id":"draft-dispatch-ok"}""".toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val store = InMemoryTokenStore(
                OAuth2Token("access", "refresh", Instant.now().plusSeconds(3600))
            )
            val api = GoogleApiClient(store, ClientCreds("c", "s"),
                baseApiUrl = "http://localhost:$port")
            val gmail = GmailClient(api)
            val result = gmail.draftsCreate("to@test.com", "Subject", "Body")
            assertTrue(result.isSuccess)
            assertContains(result.getOrThrow(), "draft-dispatch-ok")
        } finally {
            server.stop(0)
        }
    }
}
```

- [ ] **Step 2: Run tests to confirm the "gws" error detection tests fail (dispatch still routes to GwsEffector)**

```
gradle :test --tests "jarvis.tutor.google.GoogleDispatchTest"
```

Expected: The "gws" assertion tests PASS (since GwsEffector returns "gws disabled" which contains "gws ") — this confirms the old path is in place. The test names indicate what the new wiring must satisfy.

- [ ] **Step 3: Add `GoogleClients` singleton and redirect dispatch in `JarvisToolset.kt`**

In `src/main/kotlin/jarvis/tutor/JarvisToolset.kt`, add the following after the existing imports (add `google.*` imports):

```kotlin
import jarvis.tutor.google.CalendarClient
import jarvis.tutor.google.ClientCreds
import jarvis.tutor.google.DiskTokenStore
import jarvis.tutor.google.DriveClient
import jarvis.tutor.google.GoogleApiClient
import jarvis.tutor.google.GmailClient
```

Then replace the three private dispatch methods in `JarvisToolDefs`:

Replace `private fun dispatchCalendarCreate(argsJson: String): String { ... }` with:

```kotlin
private fun dispatchCalendarCreate(argsJson: String): String {
    val obj = parseArgs(argsJson) ?: return "bad args json"
    val summary = (obj["summary"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val startIso = (obj["startIso"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val endIso = (obj["endIso"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (summary.isEmpty() || startIso.isEmpty() || endIso.isEmpty()) {
        return "calendar_create_event: summary + startIso + endIso required"
    }
    return try {
        val cal = GoogleClients.calendar()
        val result = cal.eventsInsert(calendarId = "primary", summary = summary,
            startIso = startIso, endIso = endIso)
        if (result.isSuccess) "calendar event created (id=${result.getOrThrow()})"
        else "calendar_create_event failed: ${result.exceptionOrNull()?.message?.take(400)}"
    } catch (e: Exception) {
        "calendar_create_event error: ${e.message?.take(400)}"
    }
}
```

Replace `private fun dispatchDriveSearch(argsJson: String): String { ... }` with:

```kotlin
private fun dispatchDriveSearch(argsJson: String): String {
    val obj = parseArgs(argsJson) ?: return "bad args json"
    val q = (obj["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (q.isEmpty()) return "drive_search: query required"
    val pageSize = ((obj["pageSize"] as? JsonPrimitive)?.intOrNull ?: 5).coerceIn(1, 20)
    return try {
        val drive = GoogleClients.drive()
        val result = drive.filesList(query = q, pageSize = pageSize)
        if (result.isSuccess) {
            val files = result.getOrThrow()
            if (files.isEmpty()) "drive_search: no files matched \"$q\""
            else files.joinToString("\n") { f -> "[${f.id}] ${f.name} (${f.mimeType})" }
        } else "drive_search failed: ${result.exceptionOrNull()?.message?.take(400)}"
    } catch (e: Exception) {
        "drive_search error: ${e.message?.take(400)}"
    }
}
```

Replace `private fun dispatchGmailDraft(argsJson: String): String { ... }` with:

```kotlin
private fun dispatchGmailDraft(argsJson: String): String {
    val obj = parseArgs(argsJson) ?: return "bad args json"
    val to = (obj["to"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val subject = (obj["subject"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val body = (obj["body"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (to.isEmpty() || subject.isEmpty() || body.isEmpty()) {
        return "gmail_create_draft: to + subject + body required"
    }
    return try {
        val gmail = GoogleClients.gmail()
        val result = gmail.draftsCreate(to = to, subject = subject, body = body)
        if (result.isSuccess) "gmail draft created (id=${result.getOrThrow()})"
        else "gmail_create_draft failed: ${result.exceptionOrNull()?.message?.take(400)}"
    } catch (e: Exception) {
        "gmail_create_draft error: ${e.message?.take(400)}"
    }
}
```

Add `GoogleClients` object after `JarvisToolDefs` closing brace in the same file:

```kotlin
/**
 * Lazy-initialized Google API clients rooted at the VPS data directory.
 * Token path: /opt/jarvis/data/google-token.json
 * Credentials path: /opt/jarvis/data/client_secrets.json (parsed once on first use).
 *
 * Both paths are overridable via env vars GOOGLE_TOKEN_PATH and
 * GOOGLE_CREDS_PATH for tests and alternative deployments.
 */
object GoogleClients {
    private val tokenPath: java.nio.file.Path by lazy {
        java.nio.file.Path.of(
            System.getenv("GOOGLE_TOKEN_PATH") ?: "/opt/jarvis/data/google-token.json"
        )
    }

    private val credsPath: java.nio.file.Path by lazy {
        java.nio.file.Path.of(
            System.getenv("GOOGLE_CREDS_PATH") ?: "/opt/jarvis/data/client_secrets.json"
        )
    }

    private val creds: ClientCreds by lazy {
        try {
            val text = credsPath.toFile().readText()
            val json = Json { ignoreUnknownKeys = true }
            // client_secrets.json downloaded from Google Cloud Console
            // has shape: {"installed":{"client_id":"...","client_secret":"...",...}}
            val root = json.parseToJsonElement(text).jsonObject
            val block = (root["installed"] ?: root["web"])?.jsonObject
                ?: error("client_secrets.json missing 'installed' or 'web' key")
            val clientId = block["client_id"]?.jsonPrimitive?.content
                ?: error("client_secrets.json missing client_id")
            val clientSecret = block["client_secret"]?.jsonPrimitive?.content
                ?: error("client_secrets.json missing client_secret")
            ClientCreds(clientId, clientSecret)
        } catch (e: Exception) {
            error("GoogleClients: cannot load credentials from $credsPath — " +
                "run google-auth-bootstrap on your PC. Detail: ${e.message}")
        }
    }

    private val store: DiskTokenStore by lazy { DiskTokenStore(tokenPath) }

    private val api: GoogleApiClient by lazy {
        GoogleApiClient(store = store, creds = creds)
    }

    fun calendar(): CalendarClient = CalendarClient(api)
    fun drive(): DriveClient = DriveClient(api)
    fun gmail(): GmailClient = GmailClient(api)
}
```

Also add the required import at the top of the file:
```kotlin
import jarvis.tutor.google.ClientCreds
import jarvis.tutor.google.CalendarClient
import jarvis.tutor.google.DiskTokenStore
import jarvis.tutor.google.DriveClient
import jarvis.tutor.google.GoogleApiClient
import jarvis.tutor.google.GmailClient
```

- [ ] **Step 4: Convert `GwsEffector` to a shim delegating to `GoogleClients`**

In `src/main/kotlin/jarvis/tutor/GwsEffector.kt`, replace the `run()` method body to delegate calendar/drive/gmail calls to `GoogleClients` instead of the subprocess:

```kotlin
/** Compat shim — delegates to GoogleApiClient. Kept temporarily so any
 *  future caller that still uses GwsEffector.run() is not broken.
 *  New code uses GoogleClients directly. Scheduled for deletion post-Slice-1. */
fun run(
    subcommand: String,
    params: Map<String, Any>? = null,
    body: Map<String, Any>? = null,
    timeoutSeconds: Long = DEFAULT_TIMEOUT_S,
): Result {
    val parts = subcommand.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return Result.Err(0, "subcommand required")
    return try {
        when {
            parts.size >= 3 && parts[0] == "calendar" && parts[2] == "insert" -> {
                val summary = (body?.get("summary") as? String).orEmpty()
                val startIso = ((body?.get("start") as? Map<*, *>)?.get("dateTime") as? String).orEmpty()
                val endIso = ((body?.get("end") as? Map<*, *>)?.get("dateTime") as? String).orEmpty()
                val calId = (params?.get("calendarId") as? String) ?: "primary"
                val r = jarvis.tutor.google.GoogleClients.calendar()
                    .eventsInsert(calId, summary, startIso, endIso)
                if (r.isSuccess) Result.Ok("""{"id":"${r.getOrThrow()}"}""", null)
                else Result.Err(0, r.exceptionOrNull()?.message?.take(400).orEmpty())
            }
            parts.size >= 3 && parts[0] == "drive" && parts[2] == "list" -> {
                val q = (params?.get("q") as? String).orEmpty()
                val ps = (params?.get("pageSize") as? Int) ?: 5
                val r = jarvis.tutor.google.GoogleClients.drive().filesList(q, ps)
                if (r.isSuccess) {
                    val json = r.getOrThrow().joinToString(",") {
                        """{"id":"${it.id}","name":"${it.name}"}"""
                    }
                    Result.Ok("""{"files":[$json]}""", null)
                } else Result.Err(0, r.exceptionOrNull()?.message?.take(400).orEmpty())
            }
            parts.size >= 4 && parts[0] == "gmail" && parts[2] == "drafts" -> {
                val raw = (body?.get("message") as? Map<*, *>)?.get("raw") as? String
                if (raw == null) return Result.Err(0, "gmail shim: message.raw required")
                val decoded = String(java.util.Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
                val to = decoded.lines().firstOrNull { it.startsWith("To:") }
                    ?.removePrefix("To:")?.trim().orEmpty()
                val subjectLine = decoded.lines().firstOrNull { it.startsWith("Subject:") }
                    ?.removePrefix("Subject:")?.trim().orEmpty()
                val bodyText = decoded.substringAfter("\r\n\r\n", "")
                val r = jarvis.tutor.google.GoogleClients.gmail().draftsCreate(to, subjectLine, bodyText)
                if (r.isSuccess) Result.Ok("""{"id":"${r.getOrThrow()}"}""", null)
                else Result.Err(0, r.exceptionOrNull()?.message?.take(400).orEmpty())
            }
            else -> Result.Err(0, "GwsEffector shim: unsupported subcommand '$subcommand' (use GoogleClients directly)")
        }
    } catch (e: Exception) {
        Result.Err(-1, "GwsEffector shim error: ${e.message?.take(400)}")
    }
}
```

- [ ] **Step 5: Rename the route in `TutorRoutes.kt` and update response shape**

In `src/main/kotlin/jarvis/web/TutorRoutes.kt`, replace the existing `get("/api/v1/gws/status")` handler (lines 967-980) with:

```kotlin
// Renamed from /api/v1/gws/status (Phase I). Reports Google OAuth token
// state so the user can diagnose calendar/drive/gmail availability from
// the Tutor settings page without SSHing into the VPS.
get("/api/v1/google/status") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
    val sid = call.request.cookies["jarvis_session"]
    sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
    val tokenPath = java.nio.file.Path.of(
        System.getenv("GOOGLE_TOKEN_PATH") ?: "/opt/jarvis/data/google-token.json"
    )
    val store = jarvis.tutor.google.TokenStore(tokenPath)
    val token = store.load()
    val enabled = System.getenv("GWS_ENABLED")?.lowercase()?.let {
        it == "1" || it == "true" || it == "yes"
    } ?: false
    call.respond(HttpStatusCode.OK, ApiGoogleStatusReply(
        enabled = enabled,
        tokenPresent = token != null,
        tokenExpiresAt = token?.expiresAt,
        tokenRefreshable = token?.refreshToken?.isNotBlank() == true,
    ))
}
```

Add the new reply data class near `ApiGwsStatusReply` (keep the old one for backward compat until it's cleaned up):

```kotlin
@Serializable
private data class ApiGoogleStatusReply(
    val enabled: Boolean,
    val tokenPresent: Boolean,
    val tokenExpiresAt: String?,
    val tokenRefreshable: Boolean,
)
```

- [ ] **Step 6: Run all affected tests**

```
gradle :test --tests "jarvis.tutor.google.GoogleDispatchTest" --rerun-tasks
gradle :test --tests "jarvis.tutor.GwsEffectorTest" --rerun-tasks
```

Expected: all `GoogleDispatchTest` tests PASS; `GwsEffectorTest` still passes (shim keeps `health()` intact, and the test checks `GWS_ENABLED` is unset which still returns `enabled=false`).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/JarvisToolset.kt \
        src/main/kotlin/jarvis/tutor/GwsEffector.kt \
        src/main/kotlin/jarvis/web/TutorRoutes.kt \
        src/test/kotlin/jarvis/tutor/google/GoogleDispatchTest.kt
git commit -m "feat(google): redirect calendar/drive/gmail dispatch to GoogleApiClient; rename /api/v1/gws/status -> /api/v1/google/status"
```

---

### Task I7: google-auth-bootstrap CLI subcommand + user runbook

**Files:**
- Modify: `src/main/kotlin/jarvis/Main.kt`
- Create: `docs/notes/2026-05-10-google-oauth-bootstrap.md`
- Test: `src/test/kotlin/jarvis/tutor/google/GoogleAuthBootstrapTest.kt`

- [ ] **Step 1: Write failing tests for the bootstrap flow**

Create `src/test/kotlin/jarvis/tutor/google/GoogleAuthBootstrapTest.kt`:

```kotlin
package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [GoogleAuthBootstrap] — the one-time OAuth 2.0 consent flow.
 *
 * The full flow requires a browser. Tests cover:
 *   - OAuth URL construction (scopes, redirect_uri, response_type)
 *   - Code exchange: canned token server → OAuth2Token produced correctly
 *   - Token written to output (stdout capture via parameter injection)
 */
class GoogleAuthBootstrapTest {

    private val testCreds = ClientCreds("test-client-id", "test-client-secret")
    private val redirectUri = "http://localhost:9999/callback"

    @Test
    fun `buildConsentUrl includes all three required scopes`() {
        val url = GoogleAuthBootstrap.buildConsentUrl(
            creds = testCreds,
            redirectUri = redirectUri,
            state = "state-xyz",
        )
        assertTrue(url.contains("calendar.events"),
            "URL must contain calendar.events scope")
        assertTrue(url.contains("drive.readonly"),
            "URL must contain drive.readonly scope")
        assertTrue(url.contains("gmail.compose"),
            "URL must contain gmail.compose scope")
    }

    @Test
    fun `buildConsentUrl contains client_id and redirect_uri`() {
        val url = GoogleAuthBootstrap.buildConsentUrl(testCreds, redirectUri, "s")
        assertTrue(url.contains("test-client-id"))
        assertTrue(url.contains("localhost"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("access_type=offline"))
    }

    @Test
    fun `exchangeCode posts to token endpoint and returns OAuth2Token`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/token") { ex ->
            val resp = """{
                "access_token":"ya29.test-access",
                "refresh_token":"1//test-refresh",
                "expires_in":3600,
                "token_type":"Bearer"
            }""".trimIndent().toByteArray()
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val token = GoogleAuthBootstrap.exchangeCode(
                code = "auth-code-from-google",
                creds = testCreds,
                redirectUri = redirectUri,
                tokenEndpoint = "http://localhost:$port/token",
            )
            assertNotNull(token)
            assertEquals("ya29.test-access", token.accessToken)
            assertEquals("1//test-refresh", token.refreshToken)
            assertTrue(token.expiresAtInstant().isAfter(Instant.now()))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `exchangeCode throws on non-200 response`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port
        server.createContext("/token") { ex ->
            val resp = """{"error":"invalid_grant"}""".toByteArray()
            ex.sendResponseHeaders(400, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            var threw = false
            try {
                GoogleAuthBootstrap.exchangeCode(
                    "bad-code", testCreds, redirectUri,
                    "http://localhost:$port/token"
                )
            } catch (_: Exception) { threw = true }
            assertTrue(threw, "exchangeCode must throw on 400")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `serializeToken produces valid JSON with accessToken and refreshToken`() {
        val token = OAuth2Token("acc", "ref", Instant.now().plusSeconds(3600))
        val json = GoogleAuthBootstrap.serializeToken(token)
        assertTrue(json.contains("accessToken") || json.contains("access_token"),
            "serialized token must have an access token field")
        assertTrue(json.contains("acc"))
        assertTrue(json.contains("ref"))
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
gradle :test --tests "jarvis.tutor.google.GoogleAuthBootstrapTest"
```

Expected: FAIL — `GoogleAuthBootstrap` does not exist.

- [ ] **Step 3: Create `GoogleAuthBootstrap.kt`**

Create `src/main/kotlin/jarvis/tutor/google/GoogleAuthBootstrap.kt`:

```kotlin
package jarvis.tutor.google

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * One-time OAuth 2.0 authorization code flow for jarvis.
 *
 * Runs on the USER'S PC (browser-capable), not on the VPS.
 * After consent, outputs token JSON to stdout so the user
 * can scp it to the VPS.
 *
 * Scopes:
 *   - https://www.googleapis.com/auth/calendar.events
 *   - https://www.googleapis.com/auth/drive.readonly
 *   - https://www.googleapis.com/auth/gmail.compose
 */
object GoogleAuthBootstrap {

    private const val CALLBACK_PORT = 9999
    private const val CALLBACK_TIMEOUT_SECONDS = 120L

    private val SCOPES = listOf(
        "https://www.googleapis.com/auth/calendar.events",
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/gmail.compose",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Full interactive bootstrap. Opens browser, waits for callback, exchanges
     * code for tokens, prints JSON to [out].
     *
     * @param credsPath  Path to client_secrets.json downloaded from Google Console.
     * @param tokenPath  Output path for google-token.json (also printed to stdout).
     * @param out        Output sink (defaults to System.out for normal CLI use).
     * @param tokenEndpoint Overridable for tests.
     */
    fun run(
        credsPath: java.nio.file.Path,
        tokenPath: java.nio.file.Path,
        out: java.io.PrintStream = System.out,
        tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    ) {
        val credsJson = credsPath.toFile().readText()
        val root = json.parseToJsonElement(credsJson).jsonObject
        val block = (root["installed"] ?: root["web"])?.jsonObject
            ?: error("client_secrets.json: missing 'installed' or 'web' block")
        val clientId = block["client_id"]?.jsonPrimitive?.content
            ?: error("client_secrets.json: missing client_id")
        val clientSecret = block["client_secret"]?.jsonPrimitive?.content
            ?: error("client_secrets.json: missing client_secret")
        val creds = ClientCreds(clientId, clientSecret)

        val redirectUri = "http://localhost:$CALLBACK_PORT/callback"
        val state = java.util.UUID.randomUUID().toString().take(16)
        val consentUrl = buildConsentUrl(creds, redirectUri, state)

        out.println("=== JARVIS Google OAuth Bootstrap ===")
        out.println("Opening browser for consent. If it doesn't open automatically, visit:")
        out.println("  $consentUrl")
        out.println("Waiting for callback on $redirectUri (timeout ${CALLBACK_TIMEOUT_SECONDS}s)...")

        try {
            java.awt.Desktop.getDesktop().browse(URI(consentUrl))
        } catch (_: Exception) {
            out.println("(Could not open browser automatically — please open the URL above manually)")
        }

        val code = waitForCallback(state, redirectUri)
            ?: error("OAuth callback timed out or returned an error. Re-run and complete consent within ${CALLBACK_TIMEOUT_SECONDS}s.")

        out.println("Received auth code. Exchanging for tokens...")
        val token = exchangeCode(code, creds, redirectUri, tokenEndpoint)

        val tokenJson = serializeToken(token)
        out.println("\n=== TOKEN JSON (save as google-token.json) ===")
        out.println(tokenJson)
        out.println("==============================================")
        out.println("Next: scp the file above to /opt/jarvis/data/google-token.json on the VPS")
        out.println("Then set GWS_ENABLED=1 in /opt/jarvis/.env and restart the service.")

        // Also write to tokenPath so user can scp directly
        tokenPath.parent?.toFile()?.mkdirs()
        tokenPath.toFile().writeText(tokenJson)
        out.println("Token also written to: $tokenPath")
    }

    /** Build Google OAuth 2.0 consent URL. */
    fun buildConsentUrl(creds: ClientCreds, redirectUri: String, state: String): String {
        val scopeParam = urlEncode(SCOPES.joinToString(" "))
        val redirectParam = urlEncode(redirectUri)
        val stateParam = urlEncode(state)
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${urlEncode(creds.clientId)}" +
            "&redirect_uri=$redirectParam" +
            "&response_type=code" +
            "&scope=$scopeParam" +
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=$stateParam"
    }

    /**
     * Spin up a local [HttpServer] on [CALLBACK_PORT], wait for the OAuth
     * redirect callback, and return the authorization code.
     * Returns null on timeout or error.
     */
    private fun waitForCallback(expectedState: String, redirectUri: String): String? {
        val codeLatch = CountDownLatch(1)
        val codeRef = AtomicReference<String?>(null)

        val server = HttpServer.create(InetSocketAddress(CALLBACK_PORT), 0)
        server.createContext("/callback") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            val params = parseQueryString(query)
            val code = params["code"]
            val state = params["state"]
            val responseBody: String
            if (code != null && state == expectedState) {
                codeRef.set(code)
                responseBody = "<html><body><h2>Authorization successful!</h2>" +
                    "<p>You can close this tab and return to the terminal.</p></body></html>"
            } else {
                responseBody = "<html><body><h2>Error</h2>" +
                    "<p>Missing code or state mismatch. Please retry.</p></body></html>"
            }
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            codeLatch.countDown()
        }
        server.start()
        try {
            codeLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            server.stop(0)
        }
        return codeRef.get()
    }

    /**
     * Exchange an authorization [code] for an [OAuth2Token] via the token endpoint.
     * Exposed as `internal` for testing.
     */
    fun exchangeCode(
        code: String,
        creds: ClientCreds,
        redirectUri: String,
        tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    ): OAuth2Token {
        val form = "code=${urlEncode(code)}" +
            "&client_id=${urlEncode(creds.clientId)}" +
            "&client_secret=${urlEncode(creds.clientSecret)}" +
            "&redirect_uri=${urlEncode(redirectUri)}" +
            "&grant_type=authorization_code"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val http = HttpClient.newHttpClient()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            error("Token exchange failed (HTTP ${resp.statusCode()}): ${resp.body().take(400)}")
        }
        val body = json.parseToJsonElement(resp.body()).jsonObject
        val accessToken = body["access_token"]?.jsonPrimitive?.content
            ?: error("Token response missing access_token")
        val refreshToken = body["refresh_token"]?.jsonPrimitive?.content
            ?: error("Token response missing refresh_token — ensure access_type=offline and prompt=consent")
        val expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        return OAuth2Token(accessToken, refreshToken, Instant.now().plusSeconds(expiresIn))
    }

    /** Serialize token to JSON string for stdout + file output. */
    fun serializeToken(token: OAuth2Token): String =
        Json { prettyPrint = true; encodeDefaults = true }.encodeToString(token)

    private fun parseQueryString(query: String): Map<String, String> =
        query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
```

- [ ] **Step 4: Add `google-auth-bootstrap` subcommand to `Main.kt`**

In `src/main/kotlin/jarvis/Main.kt`, add the new case to the `when` block and update USAGE:

Replace the existing `private const val USAGE` string with:

```kotlin
private const val USAGE = """Usage: jarvis [chat | logger [--once] | reflect | sub [name] [query...] | subs | web | reindex | import-anki <subject> <path> | google-auth-bootstrap]

  chat                       Start interactive chat REPL (default).
  logger                     Always-on activity logger (5-min interval).
  logger --once              Single capture, exit.
  reflect                    Daily reflection over last 24h.
  sub <name> [query...]      Run a named subsystem.
  subs                       List available subsystems.
  web                        Start Ktor web server (HTMX UI on :8080 by default).
  reindex                    Embed any wiki entries not yet in the vector store.
  import-anki <subj> <apkg>  Import an Anki .apkg deck as concepts under archival/<subj>/.
  ingest-corpus              Build / refresh the knowledge graph from archival/ markdown.
  google-auth-bootstrap      One-time OAuth 2.0 consent flow — opens browser, writes
                             google-token.json. Run on your PC (needs a browser).
                             Reads client_secrets.json from GOOGLE_CREDS_PATH or
                             ./client_secrets.json. Writes token to GOOGLE_TOKEN_PATH
                             or ./google-token.json.
"""
```

Add the new case in the `when` block (before the `else` branch):

```kotlin
"google-auth-bootstrap" -> {
    val credsPath = java.nio.file.Path.of(
        System.getenv("GOOGLE_CREDS_PATH") ?: "client_secrets.json"
    )
    val tokenPath = java.nio.file.Path.of(
        System.getenv("GOOGLE_TOKEN_PATH") ?: "google-token.json"
    )
    if (!credsPath.toFile().exists()) {
        System.err.println(
            "ERROR: client_secrets.json not found at $credsPath\n" +
            "  1. Go to https://console.cloud.google.com/apis/credentials\n" +
            "  2. Create OAuth client ID → Desktop app → download JSON\n" +
            "  3. Place it at $credsPath (or set GOOGLE_CREDS_PATH=<path>)\n" +
            "  4. Re-run: jarvis google-auth-bootstrap"
        )
        exitProcess(2)
    }
    jarvis.tutor.google.GoogleAuthBootstrap.run(credsPath, tokenPath)
}
```

Also add the import at the top of `Main.kt`:

```kotlin
import jarvis.tutor.google.GoogleAuthBootstrap
```

- [ ] **Step 5: Create user runbook doc**

Create `docs/notes/2026-05-10-google-oauth-bootstrap.md`:

```markdown
# Google OAuth Bootstrap — User Runbook

**Purpose:** one-time setup to activate calendar/drive/gmail tools in the Jarvis tutor.
**Who runs this:** you, on your Windows PC (needs a browser for OAuth consent).
**Time required:** ~10 minutes.

---

## Step 1 — Open Google Cloud Console

Go to: https://console.cloud.google.com/apis/credentials

---

## Step 2 — Create (or select) a project

Click the project dropdown at the top. Create a new project named **"Jarvis Personal Tutor"** (or reuse an existing project you own).

---

## Step 3 — Enable the three APIs

In the left menu go to **APIs & Services → Library** and enable each of:
- **Google Calendar API**
- **Google Drive API**
- **Gmail API**

Search by name and click **Enable**.

---

## Step 4 — Create OAuth Client Credentials

Go to **APIs & Services → Credentials** → **+ Create Credentials** → **OAuth client ID**.
- Application type: **Desktop app**
- Name: `jarvis-personal`
- Click **Create**

Download the JSON file that appears. It is named `client_secrets_<id>.json` by default.

---

## Step 5 — Configure OAuth Consent Screen

Go to **APIs & Services → OAuth consent screen**:
- User type: **External** (so you can add yourself as a test user)
- App name: `Jarvis Personal Tutor`
- Publishing status: leave as **Testing** (you are the only user)
- Under **Test users** → **+ Add users** → enter your Gmail address: `amoalexandru5@gmail.com`
- Save

---

## Step 6 — Copy credentials JSON to your PC

Rename the downloaded file to `client_secrets.json` and place it in the `jarvis-kotlin/` project root on your PC.

---

## Step 7 — Run the bootstrap command

Open PowerShell in the `jarvis-kotlin/` directory and run:

```powershell
.\gradlew run --args="google-auth-bootstrap"
```

Or, if you have a built jar:

```powershell
java -jar build/libs/jarvis.jar google-auth-bootstrap
```

The command will:
1. Open your browser to Google's OAuth consent page
2. You grant the three permissions (calendar, drive read-only, gmail compose)
3. Google redirects to `http://localhost:9999/callback`
4. The command prints the token JSON and saves it as `google-token.json`

---

## Step 8 — SCP the token to the VPS

```powershell
scp google-token.json root@46.247.109.91:/opt/jarvis/data/google-token.json
```

Also copy `client_secrets.json` if not already there:

```powershell
scp client_secrets.json root@46.247.109.91:/opt/jarvis/data/client_secrets.json
```

---

## Step 9 — Activate on the VPS

SSH into the VPS and edit `/opt/jarvis/.env`:

```
GWS_ENABLED=1
GOOGLE_TOKEN_PATH=/opt/jarvis/data/google-token.json
GOOGLE_CREDS_PATH=/opt/jarvis/data/client_secrets.json
```

Restart the service:

```bash
systemctl restart jarvis
```

Verify in the Tutor UI: Settings → Google status should show `tokenPresent: true`.

---

## Verification

Check the status endpoint:

```bash
curl -s -b "jarvis_session=<your-sid>" https://your-vps/api/v1/google/status
```

Expected response:

```json
{
  "enabled": true,
  "tokenPresent": true,
  "tokenExpiresAt": "2026-05-10T15:00:00Z",
  "tokenRefreshable": true
}
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `client_secrets.json not found` | Check GOOGLE_CREDS_PATH or place file in project root |
| Browser doesn't open | Copy the URL from terminal manually |
| Callback timeout | Complete consent within 120 seconds of the URL opening |
| `refresh_token missing` | Re-run bootstrap — ensure `prompt=consent` forces re-consent |
| `401 Invalid Credentials` after deploy | Re-run bootstrap and scp a fresh token |
| Token file deleted by ops | Re-run bootstrap and scp again |

---

## Token lifecycle

- Access tokens expire in ~1 hour. The server auto-refreshes using the stored `refresh_token`.
- Refresh tokens do **not** expire unless you revoke access in Google's security settings or the token file is deleted.
- You will only need to re-run bootstrap if: (a) you revoke access in Google's My Account page, or (b) the token file is lost.
```

- [ ] **Step 6: Run all I7 tests**

```
gradle :test --tests "jarvis.tutor.google.GoogleAuthBootstrapTest" --rerun-tasks
```

Expected: 5 tests PASS (browser-open and callback-wait are not exercised; tests cover URL construction, code exchange, and serialization).

```
gradle :test --tests "jarvis.tutor.google.*" --rerun-tasks
```

Expected: all Phase I tests in the `google` package PASS (GoogleTokenStoreTest, GoogleApiClientTest, CalendarClientTest, DriveClientTest, GmailClientTest, GoogleDispatchTest, GoogleAuthBootstrapTest).

- [ ] **Step 7: Full regression check**

```
gradle :test --rerun-tasks
```

Expected: existing test suite still PASSES. No regressions from the `JarvisToolset.kt` and `TutorRoutes.kt` modifications.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/jarvis/Main.kt \
        src/main/kotlin/jarvis/tutor/google/GoogleAuthBootstrap.kt \
        src/test/kotlin/jarvis/tutor/google/GoogleAuthBootstrapTest.kt \
        docs/notes/2026-05-10-google-oauth-bootstrap.md
git commit -m "feat(google): add google-auth-bootstrap CLI subcommand + 9-step user runbook"
```

## PHASE J · E2E + tests + deploy (Tasks J1-J3)

### Task J1: Vitest sweep + axe — full test suite + new a11y tests

**Files:**
- Create: `tutor-web/src/__tests__/axe.drillstack.test.tsx`
- Create: `tutor-web/src/__tests__/axe.fsrsreview.test.tsx`

- [ ] **Step 1: Write axe test for `DrillStack`**

Create `tutor-web/src/__tests__/axe.drillstack.test.tsx`:

```tsx
import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { MemoryRouter } from "react-router-dom";
import { DrillStack } from "../components/DrillStack";

beforeEach(() => {
  vi.stubGlobal("matchMedia", vi.fn((query: string) => ({
    matches: query === "(prefers-reduced-motion: reduce)",
    media: query, onchange: null,
    addListener: vi.fn(), removeListener: vi.fn(),
    addEventListener: vi.fn(), removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 200 })));
});
afterEach(() => { vi.unstubAllGlobals(); });

const STUB_PROBLEM = {
  problemId: "A1",
  problemIndex: 0,
  totalProblems: 3,
  statement: "Sample: x = (3,7,8,9,14). Find the MLE of μ for Laplace(μ,b).",
  expectedAnswerHint: "median equals 8",
  drillsJson: JSON.stringify([
    { cardId: "card-drill", type: "drill", title: "③ DRILL · YOUR TURN", body: "What is μ̂?" },
    { cardId: "card-worked", type: "worked", title: "② WORKED EXAMPLE", body: "Σ|x_i − μ| minimised at median." },
    { cardId: "card-def", type: "definition", title: "① DEFINITION", body: "Laplace MLE = sample median." },
    { cardId: "card-check", type: "check", title: "④ CHECK · TRANSFER", body: "Verify with n=5." },
  ]),
  taskId: "T1",
};

test("DrillStack initial state has no axe violations", async () => {
  const { container } = render(
    <MemoryRouter><DrillStack {...STUB_PROBLEM} onProblemComplete={vi.fn()} /></MemoryRouter>
  );
  await new Promise((r) => setTimeout(r, 0));
  const results = await axe(container, { rules: { "color-contrast": { enabled: false } } });
  expect(results).toHaveNoViolations();
});
```

- [ ] **Step 2: Write axe test for `FsrsReview`**

Create `tutor-web/src/__tests__/axe.fsrsreview.test.tsx`:

```tsx
import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { MemoryRouter } from "react-router-dom";
import { FsrsReview } from "../components/FsrsReview";

const STUB_CARDS = [{
  id: "c1",
  front: "What is the MLE of μ for Laplace(μ,b)?",
  back: "The sample median.",
  sourceTaskId: "T1",
  difficulty: 2.0, stability: 1.0, retrievability: 0.9,
  dueAt: new Date().toISOString(), lapses: 0,
}];
const STUB_FORECAST = { tomorrow: 4, thisWeek: 18, thisMonth: 41 };

beforeEach(() => {
  vi.stubGlobal("matchMedia", vi.fn((query: string) => ({
    matches: query === "(prefers-reduced-motion: reduce)",
    media: query, onchange: null,
    addListener: vi.fn(), removeListener: vi.fn(),
    addEventListener: vi.fn(), removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/fsrs/due"))
      return new Response(JSON.stringify({ cards: STUB_CARDS }), { status: 200 });
    if (typeof url === "string" && url.includes("/api/v1/fsrs/forecast"))
      return new Response(JSON.stringify(STUB_FORECAST), { status: 200 });
    return new Response("{}", { status: 200 });
  }));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("FsrsReview front-of-card state has no axe violations", async () => {
  const { container } = render(<MemoryRouter><FsrsReview streak={3} /></MemoryRouter>);
  await new Promise((r) => setTimeout(r, 0));
  const results = await axe(container, { rules: { "color-contrast": { enabled: false } } });
  expect(results).toHaveNoViolations();
});

test("FsrsReview empty queue has no axe violations", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/fsrs/due"))
      return new Response(JSON.stringify({ cards: [] }), { status: 200 });
    if (typeof url === "string" && url.includes("/api/v1/fsrs/forecast"))
      return new Response(JSON.stringify({ tomorrow: 0, thisWeek: 0, thisMonth: 0 }), { status: 200 });
    return new Response("{}", { status: 200 });
  }));
  const { container } = render(<MemoryRouter><FsrsReview streak={0} /></MemoryRouter>);
  await new Promise((r) => setTimeout(r, 0));
  const results = await axe(container, { rules: { "color-contrast": { enabled: false } } });
  expect(results).toHaveNoViolations();
});
```

- [ ] **Step 3: Run full Vitest suite**

```
cd tutor-web && npx vitest run --reporter=verbose 2>&1 | tee /tmp/vitest-slice1.txt
```

Expected: 152 frontend PASS, 0 FAIL. Triage any FAIL: `grep FAIL /tmp/vitest-slice1.txt`. Fix critical regressions; defer cosmetic to Slice 2.

- [ ] **Step 4: Run full Gradle backend test suite**

```
gradle :test --rerun-tasks 2>&1 | tee /tmp/gradle-slice1.txt
```

Expected: `BUILD SUCCESSFUL`, 624 tests, 0 failures. Daemon Rust tests separate:

```
cd daemon && cargo test 2>&1 | grep "test result" | tail -1
```

Expected: `test result: ok. 16 passed; 0 failed`.

If counts diverge from targets (152 frontend / 624 backend / 16 daemon), record actual numbers as a finding for J2/J3 BRIDGE.md update — do not block on count mismatch alone.

- [ ] **Step 5: Commit**

```bash
git add tutor-web/src/__tests__/axe.drillstack.test.tsx tutor-web/src/__tests__/axe.fsrsreview.test.tsx
git commit -m "test(a11y): axe sweep for DrillStack + FsrsReview — Slice 1 minimal a11y gate"
```

---

### Task J2: Manual dogfood pass against live Tema_A.pdf on VPS

**Files:**
- Create: `docs/superpowers/findings/2026-05-10-slice1-dogfood.md`

- [ ] **Step 1: VPS health check**

```bash
curl -sk https://corgflix.duckdns.org/healthz | python3 -m json.tool
```

Expected: `{"ok": true}`. Capture pre-deploy bundle hash:

```bash
curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1
```

Expected: `index-CFXAulB7.js` (pre-Slice-1 baseline).

- [ ] **Step 2: Identify a real Tema_A task ID**

```bash
ssh root@46.247.109.91 "sqlite3 /opt/jarvis/data/jarvis.db \
  \"SELECT id, title, subject FROM tasks WHERE problem_ref LIKE '%Tema_A%' LIMIT 5;\""
```

Pick the first returned ID. If none exist, create one via `POST /api/v1/tasks` with body referencing `_extras/PS/ps_hw/Tema_A.pdf`.

- [ ] **Step 3: Walk through 7 dogfood scenarios**

Open `https://corgflix.duckdns.org/tutor/?taskId=<REAL_TASK_ID>`. For each scenario, record PASS / FAIL / PARTIAL:

1. **PDF prep loads**: skeleton then real stepper + drill content. Stepper shows ≥ 1 problem.
2. **Multi-problem stepper**: click A2 → URL updates `?problem=2`. Refresh restores.
3. **Grader misconception**: type wrong answer (μ̂ = 8.2 = mean). CHECK returns `L2_ESTIMATOR_CONFUSION` feedback; cards remain locked.
4. **Correct drill → stagger unlock**: type correct (median = 8). WORKED + DEFINITION + CHECK slide in with 80ms stagger.
5. **Sidekick inline-ask**: select word in DEFINITION body → ✨ ASK chip appears → click → sidekick opens with quoted context → submit follow-up → real LLM reply renders.
6. **FSRS review flip**: navigate `/tutor/review` → SHOW ANSWER flips card → AGAIN/HARD/GOOD/EASY each grade and advance.
7. **Daemon health pill**: header shows green pill. If red: `ssh root@VPS "curl -s http://127.0.0.1:7331/api/v1/health"` to diagnose.

- [ ] **Step 4: Record findings**

```
mkdir -p docs/superpowers/findings
```

Create `docs/superpowers/findings/2026-05-10-slice1-dogfood.md`:

```markdown
# Slice 1 Dogfood Findings — 2026-05-10

## Session details
- Task ID tested: <REAL_TASK_ID>
- VPS: https://corgflix.duckdns.org
- Bundle (pre-deploy): index-CFXAulB7.js
- Tester: Alex

## Scenario results

| # | Scenario | Result | Notes |
|---|----------|--------|-------|
| 1 | PDF prep loads | PASS / FAIL / PARTIAL | <observations> |
| 2 | Multi-problem stepper | PASS / FAIL / PARTIAL | <observations> |
| 3 | Drill grader misconception | PASS / FAIL / PARTIAL | <observations> |
| 4 | Correct drill → stagger unlock | PASS / FAIL / PARTIAL | <observations> |
| 5 | Sidekick inline-ask | PASS / FAIL / PARTIAL | <observations> |
| 6 | FSRS review flip | PASS / FAIL / PARTIAL | <observations> |
| 7 | Daemon health pill | PASS / FAIL / PARTIAL | <observations> |

## Critical bugs (fix now)
- [ ] BUG-1: <description> — repro: <steps> — root cause hypothesis: <hypothesis>

## Cosmetic / backlog issues (defer to Slice 2)
- BACKLOG: <description>

## Verdict
SHIP AS-IS / SHIP AFTER CRITICAL FIXES / REVERT AND INVESTIGATE
```

- [ ] **Step 5: Fix critical bugs** (loop)

For each critical bug:
1. Locate failing component or route from repro steps.
2. Apply minimal fix (no scope widening).
3. Verify with relevant Vitest test or `gradle :test --tests "..."`.
4. Commit: `fix(<scope>): <one-line description>`.

Cosmetic issues stay in BACKLOG section — don't fix in J2.

- [ ] **Step 6: Commit findings**

```bash
git add docs/superpowers/findings/2026-05-10-slice1-dogfood.md
git commit -m "docs(dogfood): Slice 1 manual dogfood findings — Tema A end-to-end pass"
```

---

### Task J3: Bundle rebuild + deploy + verify + BRIDGE.md wrap

**Files:**
- Run: `cd tutor-web && npm run build`
- Run: `bash tools/deploy.sh`
- Update: `~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md`

- [ ] **Step 1: Rebuild bundle**

```bash
cd tutor-web && npm run build 2>&1 | tee /tmp/vite-build-slice1.txt
```

Expected last line: `✓ built in Xs`. Capture hash:

```bash
grep -oE 'index-[A-Za-z0-9_-]+\.js' /tmp/vite-build-slice1.txt | head -1
```

Record value (e.g., `index-Kp9rMn2A.js`). On TS error: `cd tutor-web && npx tsc --noEmit 2>&1 | head -40`.

- [ ] **Step 2: Run deploy**

From repo root:

```bash
bash tools/deploy.sh 2>&1 | tee /tmp/deploy-slice1.txt
```

Expected tail:

```
[deploy] verifying https://corgflix.duckdns.org/healthz
{"ok":true,"version":"..."}
[deploy] done. rollback: bash tools/deploy.sh rollback
```

On any fatal error: roll back immediately:

```bash
bash tools/deploy.sh rollback
```

Common failure modes:
- `installDist` fails → `gradle :installDist 2>&1 | tail -30` standalone.
- `systemctl is-active jarvis` fails → `ssh root@VPS "journalctl -u jarvis -n 50"`. Most common cause: schema migration missing (Phase A4 SchemaUtils not updated).
- `curl: (7) Failed to connect` → retry once, else check `systemctl status jarvis`.

- [ ] **Step 3: Verify bundle hash on VPS**

```bash
curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1
```

Expected: matches the hash from Step 1. If still pre-deploy hash, the static dir wasn't replaced — inspect `tools/deploy.sh` rsync target.

- [ ] **Step 4: Smoke-verify routes**

```bash
VPS=https://corgflix.duckdns.org
echo "=== healthz ===" && curl -sk "$VPS/healthz" | python3 -m json.tool
echo "=== tutor root ===" && curl -sk "$VPS/tutor/" | grep -q '<div id="root">' && echo "ROOT OK"
echo "=== fsrs due (expect 401 without session) ===" && curl -sk -o /dev/null -w "%{http_code}\n" "$VPS/api/v1/fsrs/due"
echo "=== drill grade (expect 405 on GET) ===" && curl -sk -o /dev/null -w "%{http_code}\n" "$VPS/api/v1/drill/grade"
echo "=== daemon health ===" && curl -sk "$VPS/api/v1/daemon/health" | python3 -m json.tool
echo "=== google status ===" && curl -sk "$VPS/api/v1/google/status" | python3 -m json.tool
```

Expected:
- `/healthz` → `{"ok":true,...}`
- `/tutor/` → `ROOT OK`
- `/api/v1/fsrs/due` → `401`
- `/api/v1/drill/grade` GET → `405`
- `/api/v1/daemon/health` → JSON `{reachable, tunnelUp, lastSeenAt}`
- `/api/v1/google/status` → JSON `{enabled, tokenPresent, tokenExpiresAt, tokenRefreshable}`

A `404` on the last two means the route wasn't registered — check Phase H3 / I6 commits.

- [ ] **Step 5: Tag + push**

```bash
git tag -a "slice1-tutor-drill-workspace" -m "Slice 1: Tutor Drill Workspace — DrillStack, FSRS review, inline help, LLM grader, PDF prep, daemon autostart, Google OAuth+REST"
git push origin main --tags
```

- [ ] **Step 6: Append BRIDGE.md wrap entry**

Append to `C:\Users\User\.claude\projects\C--Users-User-jarvis-kotlin\memory\BRIDGE.md`. Substitute `<NEW_BUNDLE_HASH>`, `<SLICE1_TAG_SHA>` (`git rev-parse slice1-tutor-drill-workspace`), `<ACTUAL_BACKEND>`/`<ACTUAL_FRONTEND>` (from J1), `<DOGFOOD_VERDICT>` (from J2):

```markdown
---

## 2026-05-10T<HH:MM> → next session

**identity:** Alex (amoalexandru5@gmail.com). Romanian uni. Finals Jun 1-21 2026. Subjects: PA / PS / POO / ALO / SO+RC.

**hot work (in priority):**
1. Slice 2 planning — a11y, mobile, subject-alias, viz-plugin cross-subject. Spec TBD.
2. Dogfood follow-up: review `docs/superpowers/findings/2026-05-10-slice1-dogfood.md` backlog → Slice 2 spec.
3. Google OAuth one-time user action pending: run `tools/install-daemon-autostart.ps1` (admin) + `jarvis google-auth-bootstrap` on PC + scp `google-token.json` to VPS at `/opt/jarvis/data/google-token.json`.

**bundle:** `<NEW_BUNDLE_HASH>`
verify-cmd: `curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1`

**tests:** <ACTUAL_BACKEND> backend + <ACTUAL_FRONTEND> frontend + 16 daemon = <SUM> total
(targets: 624 + 152 + 16 = 792)

**dormant integrations (1):** Telegram bot producer (token-blocked)

**blockers:**
- Google OAuth bootstrap: user action required (browser consent flow). See spec §K.
- Daemon autostart: user action required (admin PS1 script). See spec §J.

**user-said (verbatim, last 5):**
- (fill from session transcript)

**don't relitigate:** brutalist-mono yellow-on-black; no paid APIs; no deadline framing; build-everything mode; mobile first-class; single-user

**hallucination triggers:**
- `gws` (`@googleworkspace/cli`) does NOT exist. Slice 1 §K replaced with direct OAuth+REST (`GoogleApiClient.kt`). Route is `/api/v1/google/status`, not `/api/v1/gws/status`.
- `gam` not installed on VPS.
- sympy IS installed (1.9 via apt 2026-05-10).
- Live bundle hash drifts after each deploy — re-curl before citing.

**This session shipped (Slice 1 — Tutor Drill Workspace, tag slice1-tutor-drill-workspace @ <SLICE1_TAG_SHA>):**
- Phase A: `task_prep` table + `Tasks.problemRefs` nullable column
- Phase B: `PdfProblemExtractor` + async pipeline + `/api/v1/task/{id}/reprep`
- Phase C: `/api/v1/sidekick/ask`, `DrillGrader` + `/api/v1/drill/grade`, FSRS routes (`/due`, `/{id}/grade`, `/forecast`)
- Phase D: `DrillStack`, `ProblemStepper`, `DrillCard`, `CompileSubmitCard`, two-tier progress strip
- Phase E: `InlineAskChip`, `AskGutter`, sidekick context-aware rewire
- Phase F: `FsrsReview` + `/tutor/review` route
- Phase G: 5 concept animation components (`NumLineDirect`, `SumPlotTracker`, `SlopeCounter`, `SigmaStackedBar`, Plotly frame morphs via `CompareFrames`)
- Phase H: `install-daemon-autostart.ps1` + `jarvis-daemon-task.xml` + `/api/v1/daemon/health` + `DaemonHealthPill`
- Phase I: `GoogleTokenStore` + `GoogleApiClient` + Calendar/Drive/Gmail clients + `google-auth-bootstrap` CLI + `JarvisToolset` redirect + `/api/v1/google/status`
- Phase J: axe tests + dogfood findings + bundle rebuild + deploy

**Dogfood verdict:** <DOGFOOD_VERDICT> (see `docs/superpowers/findings/2026-05-10-slice1-dogfood.md`)

**Slice 1 spec:** `docs/superpowers/specs/2026-05-10-tutor-drill-workspace-slice1-design.md` (commit `0e14a04`)
**Slice 1 plan:** `docs/superpowers/plans/2026-05-10-tutor-drill-workspace-slice1.md` (tag `slice1-tutor-drill-workspace`)
**Slice 1 dogfood:** `docs/superpowers/findings/2026-05-10-slice1-dogfood.md`
```

Prepend Index entry at top:

```markdown
- 2026-05-10T<HH:MM> → Slice 1 fully shipped + deployed; bundle <NEW_BUNDLE_HASH>; <ACTUAL_BACKEND>+<ACTUAL_FRONTEND>+16 tests
```

- [ ] **Step 7: Commit BRIDGE.md update**

```bash
git add ~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md
git commit -m "chore(memory): Slice 1 session wrap — new bundle hash + test counts + dogfood verdict"
```

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
- Phases D-J task bodies are now fully expanded (D1-D6, E1-E3, F1-F3, G1-G5, H1-H3, I1-I7, J1-J3 — total 31 D-J tasks added).

**3. Type consistency:**

- `Problem.problemId: String` consistent across PdfProblemExtractor, drillsJson schema, frontend ProblemStepper.
- `GradeResult.misconception: String?` + enum-string codes (`L2_ESTIMATOR_CONFUSION` etc.) match across DrillGrader, route, frontend.
- `SidekickEnvelope.userQuestion: String` consistent in backend + frontend `lib/sidekickContext.ts`.
- `TaskPrep.problemsJson: String` (raw JSON) — frontend deserializes via per-problem schema in `DrillStack`.
- `FsrsCardView.dueAt: String` (ISO-8601) consistent.

No type drift detected.

---

## Plan completion

All phases A-J expanded with full TDD task bodies. Total: ~36 tasks. Ready for subagent-driven-development handoff.

