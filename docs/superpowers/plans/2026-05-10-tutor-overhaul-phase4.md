# Tutor Overhaul — Phase 4 (Layer B §4 Close + Cross-Device Sync) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Server-side gap persistence (gaps survive reload; cross-task reuse). SHOW DOCS gap-card action wired to HybridRetriever + KnowledgeGraphQuery. PDF text-selection "I don't know this" tooltip emits a `<gap>` envelope. Cross-device session sync via server cookie.

**Architecture:** Existing `knowledge_gaps` table extended with the missing Phase-4 columns (taskId, trigger, content, exampleCode, sourceCitation, resolvedBy, createdAt, updatedAt). `KnowledgeGapRepo` gains `findById`, `listForUser`, `listForTask`, `markResolved`, `incrementReused`. Three new routes: POST `/api/v1/gap` (idempotent on `(user_id, task_id, topic)`), POST `/api/v1/gap/{id}/search-docs`, GET `/api/v1/gaps?taskId=`. The existing POST `/api/v1/gap/{id}/status` (Phase 3.2 draft) stays — it appends to the audit log AND now also calls `markResolved`. Frontend ChatPane POSTs gaps on parse + GETs historical on mount + merges. `<embed>` PdfPane → `react-pdf` (pdf.js + accessible TextLayer); `selectionchange` listener triggers floating tooltip → click emits `<gap>` envelope into chat. Cross-device: `localStorage.lastTaskId` syncs to server cookie `jarvis_last_task` on each task-route hit; App reads cookie first, localStorage fallback.

**Tech Stack:** Kotlin + Ktor + Exposed (backend; same). React 19 + Vitest + new dep `react-pdf` (peer: `pdfjs-dist`).

**Source spec:** `docs/superpowers/specs/2026-05-10-tutor-overhaul-design.md` § Phase 4 (lines 167-198).

**Out of Phase-3 backlog folded:**
- `[3] [interaction] [feedback-response-time] [med] scratchpad save indicator` — separate concern; Phase 4 doesn't address. Stays.
- `[3] [interaction] [state-visibility] [med] scratchpad mount race` — Phase 4.4 cross-device logic touches App, not scratchpad. Stays.
- `[3] [error-prevention] [error-recovery] [med] SuggestedEditCard retry path` — orthogonal. Stays.

---

## File Structure

**Created:**
- `src/test/kotlin/jarvis/tutor/GapRepoTest.kt`
- `src/test/kotlin/jarvis/tutor/GapsRouteTest.kt`
- `tutor-web/src/__tests__/gapPersist.test.tsx` — ChatPane GET on mount + POST on parse.
- `tutor-web/src/__tests__/showDocs.test.tsx` — KnowledgeGapCard SHOW DOCS click renders results.
- `tutor-web/src/__tests__/crossDeviceSync.test.tsx` — server cookie wins over localStorage.

**Modified — backend:**
- `src/main/kotlin/jarvis/tutor/KnowledgeGaps.kt` — extend table (8 new columns); extend Repo with the 5 new methods.
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` — 3 new routes (POST /gap, POST /gap/{id}/search-docs, GET /gaps); extend POST /gap/{id}/status to also call `markResolved`; cookie-write hook on existing GET `/api/v1/tasks/{id}` access (or via a new sync endpoint — see Task 9).

**Modified — frontend:**
- `tutor-web/package.json` — add `react-pdf` + `pdfjs-dist`.
- `tutor-web/src/components/PdfPane.tsx` — `<embed>` → `react-pdf` Document/Page; selection listener → floating tooltip; click emits `<gap>` envelope through a callback.
- `tutor-web/src/components/ChatPane.tsx` — POST `/api/v1/gap` on each parsed gap envelope; GET `/api/v1/gaps?taskId=` on mount + merge above live messages; expose a `useGapEnvelopeEmitter` callback so PdfPane can inject gaps without going through fetch directly.
- `tutor-web/src/components/KnowledgeGapCard.tsx` — SHOW DOCS button (collapsed by default); on expand, POST `/api/v1/gap/{id}/search-docs` + render results.
- `tutor-web/src/App.tsx` — read `jarvis_last_task` cookie on mount; prefer it over localStorage; on each `taskId` change, write cookie via `document.cookie = ...`.

---

## Task 1: Phase 4 plan committed

- [ ] **Step 1: Commit**

```bash
git add docs/superpowers/plans/2026-05-10-tutor-overhaul-phase4.md
git commit -m "$(cat <<'EOF'
Phase 4 plan: Layer B §4 close + cross-device sync

Per spec § Phase 4. Phase 3 shipped + 4 gates passed at 8d6e952.
Phase 4: extend knowledge_gaps + 3 new routes + ChatPane gap-persist
loop + KnowledgeGapCard SHOW DOCS + PdfPane react-pdf swap with
selection tooltip + cross-device server cookie sync.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: §4.1a Backend — extend knowledge_gaps table + repo

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/KnowledgeGaps.kt`.
- Create: `src/test/kotlin/jarvis/tutor/GapRepoTest.kt`.

The existing `KnowledgeGapsTable` is missing 8 of the spec-required columns. Add them. The existing `KnowledgeGapRepo` lacks 5 of the spec-required methods. Add them.

- [ ] **Step 1: Extend `KnowledgeGapsTable` columns**

Add to the table object:

```kotlin
val taskId = varchar("task_id", 26).nullable()
val trigger = varchar("trigger", 32)            // GapTrigger.name; default to "EXPLICIT_ASK" via app code
val content = text("content")                    // gap.content payload
val exampleCode = text("example_code").nullable()
val sourceCitation = text("source_citation").nullable()
val resolvedBy = varchar("resolved_by", 32).nullable()  // GapResolved.name when set
val createdAt = timestamp("created_at")
val updatedAt = timestamp("updated_at")
```

Existing `filledAt` stays; `createdAt` is the spec-named insert time; on insert: `createdAt = filledAt = now`. Spec says "created_at, updated_at"; we keep `filledAt` for backward compat (existing code reads it).

- [ ] **Step 2: Extend `append()` to write the new columns**

Replace the existing `append` body inside the `transaction(db) { KnowledgeGapsTable.insert { ... } ... }` block:

```kotlin
fun append(g: KnowledgeGap, taskId: String? = null, trigger: GapTrigger = g.trigger,
           content: String = g.content, exampleCode: String? = g.exampleCode,
           sourceCitation: String? = g.sourceCitation,
           now: Instant = Instant.now()): String = transaction(db) {
    KnowledgeGapsTable.insert {
        it[id] = g.id
        it[userId] = g.userId
        it[KnowledgeGapsTable.taskId] = taskId
        it[topic] = g.topic
        it[language] = g.language
        it[type] = g.type.name
        it[KnowledgeGapsTable.trigger] = trigger.name
        it[filledAt] = g.filledAt
        it[KnowledgeGapsTable.content] = content
        it[KnowledgeGapsTable.exampleCode] = exampleCode
        it[KnowledgeGapsTable.sourceCitation] = sourceCitation
        it[resolvedBy] = g.resolvedBy?.name
        it[reusedCount] = g.reusedCount
        it[fsrsCardId] = g.fsrsCardId
        it[createdAt] = now
        it[updatedAt] = now
    }
    // The JSONL ledger write is intentionally kept (existing audit chain).
    val file = ledgerDir.resolve("knowledge_gaps_${g.userId}.jsonl")
    Files.createDirectories(ledgerDir)
    val line = TutorTypes.tutorJson.encodeToString(KnowledgeGap.serializer(), g)
    Files.write(file, (line + "\n").toByteArray(),
        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    g.id
}
```

- [ ] **Step 3: Add the 5 new repo methods**

Append to `KnowledgeGapRepo`:

```kotlin
private fun rowToGap(row: org.jetbrains.exposed.sql.ResultRow): KnowledgeGap = KnowledgeGap(
    id = row[KnowledgeGapsTable.id],
    userId = row[KnowledgeGapsTable.userId],
    taskId = row[KnowledgeGapsTable.taskId],
    topic = row[KnowledgeGapsTable.topic],
    language = row[KnowledgeGapsTable.language],
    type = GapType.valueOf(row[KnowledgeGapsTable.type]),
    trigger = GapTrigger.valueOf(row[KnowledgeGapsTable.trigger]),
    filledAt = row[KnowledgeGapsTable.filledAt],
    source = GapSource.LLM_GROUNDED,  // not stored in table; default for legacy reads
    content = row[KnowledgeGapsTable.content],
    exampleCode = row[KnowledgeGapsTable.exampleCode],
    sourceCitation = row[KnowledgeGapsTable.sourceCitation],
    resolvedBy = row[KnowledgeGapsTable.resolvedBy]?.let { GapResolved.valueOf(it) },
    reusedCount = row[KnowledgeGapsTable.reusedCount],
    fsrsCardId = row[KnowledgeGapsTable.fsrsCardId],
)

fun findById(id: String): KnowledgeGap? = transaction(db) {
    KnowledgeGapsTable.selectAll().where { KnowledgeGapsTable.id eq id }
        .singleOrNull()?.let(::rowToGap)
}

fun listForUser(userId: String, limit: Int = 200): List<KnowledgeGap> = transaction(db) {
    KnowledgeGapsTable.selectAll()
        .where { KnowledgeGapsTable.userId eq userId }
        .orderBy(KnowledgeGapsTable.createdAt, SortOrder.DESC)
        .limit(limit)
        .map(::rowToGap)
}

fun listForTask(userId: String, taskId: String): List<KnowledgeGap> = transaction(db) {
    KnowledgeGapsTable.selectAll()
        .where { (KnowledgeGapsTable.userId eq userId) and (KnowledgeGapsTable.taskId eq taskId) }
        .orderBy(KnowledgeGapsTable.createdAt, SortOrder.ASC)
        .map(::rowToGap)
}

/** Idempotent on (userId, taskId, topic). Hit increments reusedCount + returns existing id. */
fun upsertByTriple(g: KnowledgeGap, taskId: String?, content: String,
                   exampleCode: String? = null, sourceCitation: String? = null,
                   now: Instant = Instant.now()): String = transaction(db) {
    val existing = KnowledgeGapsTable.selectAll().where {
        (KnowledgeGapsTable.userId eq g.userId) and
        (if (taskId == null) KnowledgeGapsTable.taskId.isNull()
         else KnowledgeGapsTable.taskId eq taskId) and
        (KnowledgeGapsTable.topic eq g.topic)
    }.firstOrNull()
    if (existing != null) {
        val id = existing[KnowledgeGapsTable.id]
        KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
            it[reusedCount] = existing[KnowledgeGapsTable.reusedCount] + 1
            it[updatedAt] = now
        }
        return@transaction id
    }
    append(g.copy(taskId = taskId), taskId = taskId, content = content,
           exampleCode = exampleCode, sourceCitation = sourceCitation, now = now)
}

fun markResolved(id: String, by: GapResolved, now: Instant = Instant.now()): Boolean = transaction(db) {
    val n = KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
        it[resolvedBy] = by.name
        it[updatedAt] = now
    }
    n > 0
}

fun incrementReused(id: String, now: Instant = Instant.now()): Boolean = transaction(db) {
    val cur = KnowledgeGapsTable.selectAll()
        .where { KnowledgeGapsTable.id eq id }
        .firstOrNull()?.get(KnowledgeGapsTable.reusedCount) ?: return@transaction false
    KnowledgeGapsTable.update({ KnowledgeGapsTable.id eq id }) {
        it[reusedCount] = cur + 1
        it[updatedAt] = now
    }
    true
}
```

Required additional imports in `KnowledgeGaps.kt`:
```kotlin
import org.jetbrains.exposed.sql.update  // already present per Phase 3
```

The `KnowledgeGap` data class needs `taskId: String?` field. It's already in the data class (line 30 of current source) — verify before claiming this step done.

- [ ] **Step 4: Write `GapRepoTest.kt`**

Create `src/test/kotlin/jarvis/tutor/GapRepoTest.kt`:

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
import kotlin.test.assertTrue

class GapRepoTest {

    private fun freshCtx(tmp: Path): Pair<KnowledgeGapRepo, String> {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, KnowledgeGapsTable)
        }
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        return KnowledgeGapRepo(db, tmp) to userId
    }

    private fun mkGap(userId: String, topic: String, taskId: String? = null) = KnowledgeGap(
        id = TutorTypes.ulid(), userId = userId, taskId = taskId, topic = topic,
        language = "kotlin", type = GapType.CONCEPT, trigger = GapTrigger.EXPLICIT_ASK,
        filledAt = Instant.now(), source = GapSource.LLM_GROUNDED,
        content = "explanation of $topic", exampleCode = "val x = 1",
        sourceCitation = null, resolvedBy = null, reusedCount = 0, fsrsCardId = null,
    )

    @Test
    fun `upsertByTriple inserts on miss + bumps reusedCount on hit`(@TempDir tmp: Path) {
        val (repo, userId) = freshCtx(tmp)
        val g = mkGap(userId, "closures", taskId = "T1")
        val id1 = repo.upsertByTriple(g, taskId = "T1", content = g.content)
        assertNotNull(repo.findById(id1))
        // Same triple → same id, reusedCount bumped to 1.
        val id2 = repo.upsertByTriple(mkGap(userId, "closures", taskId = "T1"), taskId = "T1", content = "x")
        assertEquals(id1, id2)
        assertEquals(1, repo.findById(id1)?.reusedCount)
    }

    @Test
    fun `markResolved + incrementReused update timestamps + values`(@TempDir tmp: Path) {
        val (repo, userId) = freshCtx(tmp)
        val id = repo.upsertByTriple(mkGap(userId, "lambdas", "T2"), taskId = "T2", content = "x")
        assertTrue(repo.markResolved(id, GapResolved.USER_TYPED))
        assertEquals(GapResolved.USER_TYPED, repo.findById(id)?.resolvedBy)
        assertTrue(repo.incrementReused(id))
        assertEquals(1, repo.findById(id)?.reusedCount)
    }

    @Test
    fun `listForTask scopes to single task + ascending createdAt`(@TempDir tmp: Path) {
        val (repo, userId) = freshCtx(tmp)
        repo.upsertByTriple(mkGap(userId, "a", "T3"), taskId = "T3", content = "x")
        Thread.sleep(5)
        repo.upsertByTriple(mkGap(userId, "b", "T3"), taskId = "T3", content = "x")
        repo.upsertByTriple(mkGap(userId, "c", "T4"), taskId = "T4", content = "x")
        val t3 = repo.listForTask(userId, "T3")
        assertEquals(2, t3.size)
        assertEquals(listOf("a", "b"), t3.map { it.topic })
    }
}
```

- [ ] **Step 5: Run + commit**

```bash
gradle :test --tests "jarvis.tutor.GapRepoTest" -i
```
Expected: 3 PASS.

```bash
gradle :test
```
Expected: 555 + 3 = 558 backend.

Existing `KnowledgeGapRepoTest` (if any) may break because `findByTopic` is unchanged but the JSONL→DB coupling assumes existing tables — re-check by running. Fix in place if a single existing test relies on a removed shape.

```bash
git add src/main/kotlin/jarvis/tutor/KnowledgeGaps.kt src/test/kotlin/jarvis/tutor/GapRepoTest.kt
git commit -m "$(cat <<'EOF'
Phase 4.1a: extend knowledge_gaps table + GapRepo (5 new methods)

Adds 8 columns to knowledge_gaps (taskId nullable, trigger, content,
exampleCode nullable, sourceCitation nullable, resolvedBy nullable,
createdAt, updatedAt). createMissingTablesAndColumns handles the ALTER
on existing DBs.

5 new repo methods: findById, listForUser, listForTask, upsertByTriple
(idempotent on (userId, taskId, topic) — bumps reusedCount on hit),
markResolved, incrementReused. Existing append() + findByTopic +
bumpReuse stay untouched.

3 new tests in GapRepoTest. Backend tests 555 → 558.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: §4.1b Backend — POST /gap + GET /gaps + extend POST /gap/{id}/status

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`.
- Create: `src/test/kotlin/jarvis/tutor/GapsRouteTest.kt`.

3 routes:
1. `POST /api/v1/gap` — body `{ topic, language?, type, trigger, content, exampleCode?, sourceCitation?, taskId? }` → `upsertByTriple`. Returns 201 + `{ id, reusedCount }`.
2. `GET /api/v1/gaps?taskId=` — list gaps for the session user, filtered by taskId if param present.
3. The existing `POST /api/v1/gap/{id}/status` (Phase 3.2) currently only writes to `tutor_card_action_log`. Extend it to ALSO call `KnowledgeGapRepo.markResolved(id, GapResolved.valueOf(req.status))` if the status string is one of the GapResolved values. If it's some other string (legacy / non-spec), only log to audit.

- [ ] **Step 1: Add the routes**

Inside `installTutorRoutes`'s `routing { }` block, add (use the same csrfProtect + session pattern as existing routes):

```kotlin
post("/api/v1/gap") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val req = try {
            sensorJson.decodeFromString(ApiCreateGapRequest.serializer(), call.receiveText())
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
            return@csrfProtect
        }
        if (req.topic.isBlank() || req.topic.length > 256) {
            call.respond(HttpStatusCode.BadRequest, "topic 1-256 chars")
            return@csrfProtect
        }
        val typeEnum = try { jarvis.tutor.GapType.valueOf(req.type) } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "type must be one of GapType"); return@csrfProtect
        }
        val triggerEnum = try { jarvis.tutor.GapTrigger.valueOf(req.trigger) } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "trigger must be one of GapTrigger"); return@csrfProtect
        }
        val now = Instant.now()
        val gap = jarvis.tutor.KnowledgeGap(
            id = jarvis.tutor.TutorTypes.ulid(), userId = userId, taskId = req.taskId,
            topic = req.topic.take(256), language = req.language, type = typeEnum,
            trigger = triggerEnum, filledAt = now, source = jarvis.tutor.GapSource.LLM_GROUNDED,
            content = req.content, exampleCode = req.exampleCode, sourceCitation = req.sourceCitation,
            resolvedBy = null, reusedCount = 0, fsrsCardId = null,
        )
        val gapRepo = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir)
        val id = gapRepo.upsertByTriple(gap, taskId = req.taskId, content = req.content,
            exampleCode = req.exampleCode, sourceCitation = req.sourceCitation, now = now)
        val saved = gapRepo.findById(id)
        call.respond(HttpStatusCode.Created, ApiGapView(
            id = id,
            taskId = saved?.taskId,
            topic = saved?.topic ?: req.topic,
            language = saved?.language,
            type = saved?.type?.name ?: req.type,
            trigger = saved?.trigger?.name ?: req.trigger,
            content = saved?.content ?: req.content,
            exampleCode = saved?.exampleCode,
            sourceCitation = saved?.sourceCitation,
            resolvedBy = saved?.resolvedBy?.name,
            reusedCount = saved?.reusedCount ?: 0,
        ))
    }
}

get("/api/v1/gaps") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
    val taskId = call.request.queryParameters["taskId"]
    val repo = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir)
    val gaps = if (taskId != null) repo.listForTask(userId, taskId) else repo.listForUser(userId)
    call.respond(HttpStatusCode.OK, ApiGapsList(gaps.map { g ->
        ApiGapView(
            id = g.id, taskId = g.taskId, topic = g.topic, language = g.language,
            type = g.type.name, trigger = g.trigger.name,
            content = g.content, exampleCode = g.exampleCode, sourceCitation = g.sourceCitation,
            resolvedBy = g.resolvedBy?.name, reusedCount = g.reusedCount,
        )
    }))
}
```

- [ ] **Step 2: Extend the existing POST /gap/{id}/status to call markResolved**

Inside the existing `post("/api/v1/gap/{id}/status") { ... }` block, after the `CardActionLogRepo(ctx.db).insert(...)` line, before the `call.respond(...)`, add:

```kotlin
// Phase 4: status → KnowledgeGap.resolvedBy if it parses as one.
val resolved = try { jarvis.tutor.GapResolved.valueOf(req.status) } catch (e: Exception) { null }
if (resolved != null) {
    jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir).markResolved(cardId, resolved)
}
```

(Existing `gap.id` from frontend is the same id used at insert time, so `cardId` matches `KnowledgeGap.id`.)

- [ ] **Step 3: Add request/reply types**

Near other `private data class Api...` types:

```kotlin
@Serializable
private data class ApiCreateGapRequest(
    val topic: String,
    val language: String? = null,
    val type: String,        // GapType.name
    val trigger: String,     // GapTrigger.name
    val content: String,
    val exampleCode: String? = null,
    val sourceCitation: String? = null,
    val taskId: String? = null,
)

@Serializable
private data class ApiGapView(
    val id: String,
    val taskId: String?,
    val topic: String,
    val language: String?,
    val type: String,
    val trigger: String,
    val content: String,
    val exampleCode: String?,
    val sourceCitation: String?,
    val resolvedBy: String?,
    val reusedCount: Int,
)

@Serializable
private data class ApiGapsList(val gaps: List<ApiGapView>)
```

- [ ] **Step 4: Write `GapsRouteTest.kt`**

```kotlin
package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.get
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

class GapsRouteTest {
    private fun Application.freshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seed(ctx: TutorContext): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        return userId to sid
    }

    @Test
    fun `POST gap returns 201 + GET gaps lists it for taskId`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seed(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r1 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"topic":"closures","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"explain","taskId":"T1"}""")
        }
        assertEquals(HttpStatusCode.Created, r1.status, r1.bodyAsText())
        val r2 = client.get("/api/v1/gaps?taskId=T1") { cookie("jarvis_session", sid) }
        assertEquals(HttpStatusCode.OK, r2.status)
        assertTrue(r2.bodyAsText().contains("\"topic\":\"closures\""))
    }

    @Test
    fun `POST gap twice with same triple returns same id (reusedCount bumps)`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seed(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val payload = """{"topic":"laplace","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"x","taskId":"T2"}"""
        val r1 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(payload)
        }
        val r2 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody(payload)
        }
        val id1 = Regex("\"id\":\"([^\"]+)\"").find(r1.bodyAsText())?.groupValues?.get(1)
        val id2 = Regex("\"id\":\"([^\"]+)\"").find(r2.bodyAsText())?.groupValues?.get(1)
        assertEquals(id1, id2)
        // r2 body has reusedCount 1.
        assertTrue(r2.bodyAsText().contains("\"reusedCount\":1"))
    }

    @Test
    fun `POST gap status with GapResolved value updates resolvedBy`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (_, sid) = seed(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val r1 = client.post("/api/v1/gap") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"topic":"derivative","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"x","taskId":"T3"}""")
        }
        val gapId = Regex("\"id\":\"([^\"]+)\"").find(r1.bodyAsText())!!.groupValues[1]
        val r2 = client.post("/api/v1/gap/$gapId/status") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json); setBody("""{"status":"USER_MARKED_DONE"}""")
        }
        assertEquals(HttpStatusCode.OK, r2.status)
        val r3 = client.get("/api/v1/gaps?taskId=T3") { cookie("jarvis_session", sid) }
        assertTrue(r3.bodyAsText().contains("\"resolvedBy\":\"USER_MARKED_DONE\""))
    }
}
```

- [ ] **Step 5: Run + commit**

```bash
gradle :test --tests "jarvis.tutor.GapsRouteTest" -i
gradle :test
```
Expected: 558 + 3 = 561 backend.

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/GapsRouteTest.kt
git commit -m "$(cat <<'EOF'
Phase 4.1b: POST /api/v1/gap + GET /api/v1/gaps + extend status route

POST /api/v1/gap idempotent on (user, taskId, topic) via
KnowledgeGapRepo.upsertByTriple; returns 201 with ApiGapView. GET
/api/v1/gaps?taskId=... lists gaps for the session user, scoped to a
task when query param present.

Extended POST /api/v1/gap/{id}/status: still writes to
tutor_card_action_log (3.2 behavior preserved); additionally calls
KnowledgeGapRepo.markResolved when status parses as GapResolved.

3 new tests in GapsRouteTest. Backend tests 558 → 561.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: §4.1c Frontend — ChatPane POST gap on parse + GET on mount

**Files:**
- Modify: `tutor-web/src/components/ChatPane.tsx`.
- Create: `tutor-web/src/__tests__/gapPersist.test.tsx`.

ChatPane runs `parseKnowledgeGaps` on every assistant reply. Each parsed gap should be POSTed; the response's reusedCount is reflected on the rendered card.

On task mount, GET historical gaps + render them as a "previously-flagged gaps" list above the live conversation.

- [ ] **Step 1: Edit `ChatPane.tsx`**

Add at top:
```tsx
import { useEffect, useRef, useState } from "react";
```

After the `parseKnowledgeGaps` call in the `send()` function, fire a fire-and-forget POST per gap:

```tsx
gapParsed.gaps.forEach(async (g) => {
  try {
    await jarvisFetch("/api/v1/gap", {
      method: "POST",
      body: JSON.stringify({
        topic: g.topic,
        language: g.language,
        type: g.type,
        trigger: g.trigger,
        content: g.content,
        exampleCode: g.exampleCode,
        sourceCitation: g.sourceCitation,
        taskId,
      }),
    });
  } catch (_) { /* best-effort persist */ }
});
```

Add a `historicalGaps` state + GET on mount:

```tsx
const [historicalGaps, setHistoricalGaps] = useState<KnowledgeGap[]>([]);
useEffect(() => {
  let cancelled = false;
  jarvisFetch(`/api/v1/gaps?taskId=${encodeURIComponent(taskId)}`)
    .then(r => r.ok ? r.json() : { gaps: [] })
    .then((data: { gaps: any[] }) => {
      if (cancelled) return;
      setHistoricalGaps((data.gaps ?? []).map(g => ({
        id: g.id, topic: g.topic, language: g.language, type: g.type,
        trigger: g.trigger, content: g.content, exampleCode: g.exampleCode,
        sourceCitation: g.sourceCitation,
      })));
    })
    .catch(() => {});
  return () => { cancelled = true; };
}, [taskId]);
```

Render historical above messages: above the `{messages.map(...)}` block, add:

```tsx
{historicalGaps.length > 0 && (
  <div data-testid="historical-gaps" className="mb-2">
    <div className="text-xs font-bold tracking-widest text-page-fg/60 mb-1">PREVIOUSLY FLAGGED ({historicalGaps.length})</div>
    {historicalGaps.map(g => (
      <KnowledgeGapCard key={g.id} gap={g}
        onInsertScratchpad={onScratchpadInsert ? gg => {
          const text = gg.exampleCode ? `// ${gg.topic}\n${gg.exampleCode}` : `// ${gg.topic}\n${gg.content}`;
          onScratchpadInsert(text);
        } : undefined} />
    ))}
  </div>
)}
```

- [ ] **Step 2: Write `gapPersist.test.tsx`**

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { ChatPane } from "../components/ChatPane";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("ChatPane GETs historical gaps on mount", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/gaps?taskId=T1")) {
      return new Response(JSON.stringify({
        gaps: [{ id: "g-old-1", topic: "closures", language: "kotlin", type: "CONCEPT",
                 trigger: "EXPLICIT_ASK", content: "explain closures",
                 exampleCode: null, sourceCitation: null, resolvedBy: null, reusedCount: 0 }],
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<ChatPane taskId="T1" />);
  await waitFor(() => expect(screen.getByTestId("historical-gaps")).toBeInTheDocument());
  expect(screen.getByText(/closures/)).toBeInTheDocument();
});

test("ChatPane POSTs gaps parsed from assistant reply", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/gaps")) {
      return new Response(JSON.stringify({ gaps: [] }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/chat")) {
      return new Response(JSON.stringify({
        reply: `intro: <gap>{"id":"g1","topic":"laplace","language":"math","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"explain laplace","exampleCode":null}</gap>`,
      }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/gap") && init?.method === "POST") {
      return new Response(JSON.stringify({ id: "g-new", reusedCount: 0 }), { status: 201 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<ChatPane taskId="T2" />);
  fireEvent.change(screen.getByPlaceholderText(/message/i), { target: { value: "explain laplace" } });
  fireEvent.click(screen.getByRole("button", { name: /send/i }));
  await waitFor(() => {
    const calls = (globalThis.fetch as any).mock.calls.filter((c: any) =>
      typeof c[0] === "string" && c[0] === "/api/v1/gap" && c[1]?.method === "POST");
    expect(calls.length).toBeGreaterThanOrEqual(1);
    const body = JSON.parse(calls[0][1].body);
    expect(body.topic).toBe("laplace");
    expect(body.taskId).toBe("T2");
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
cd tutor-web && npm test -- --run gapPersist
npm test -- --run
```
Expected: 99 + 2 = 101 frontend.

```bash
git add tutor-web/src/components/ChatPane.tsx tutor-web/src/__tests__/gapPersist.test.tsx
git commit -m "$(cat <<'EOF'
Phase 4.1c: ChatPane POSTs gaps on parse + GETs historical on mount

Each parsed <gap> envelope from an assistant reply fire-and-forgets a
POST /api/v1/gap with the active taskId. The server's
upsertByTriple handles dedup. On task mount the chat-pane GETs
/api/v1/gaps?taskId= and renders a "PREVIOUSLY FLAGGED" section above
the live conversation so reload survives.

Frontend tests 99 → 101.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: §4.2 Backend — POST /api/v1/gap/{id}/search-docs

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt`.
- Existing test class extension `GapsRouteTest`.

- [ ] **Step 1: Add the route**

```kotlin
post("/api/v1/gap/{id}/search-docs") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val gapId = call.parameters["id"]
            ?: run { call.respond(HttpStatusCode.BadRequest, "id required"); return@csrfProtect }
        val repo = jarvis.tutor.KnowledgeGapRepo(ctx.db, ctx.ledgerDir)
        val gap = repo.findById(gapId)
        if (gap == null || gap.userId != userId) {
            call.respond(HttpStatusCode.NotFound, "gap not found"); return@csrfProtect
        }
        // Hybrid retrieval — lexical + semantic. semanticEmbed=null → lexical-only at this scale.
        val hits = try {
            kotlinx.coroutines.runBlocking {
                jarvis.HybridRetriever.search(gap.topic, k = 3, semanticEmbed = null)
            }
        } catch (e: Exception) {
            emptyList()
        }
        // KnowledgeGraph hits use the in-process graph if loaded.
        val graphResults = try {
            val graph = ctx.knowledgeGraph
            if (graph != null) jarvis.KnowledgeGraphQuery.query(graph, gap.topic, 3) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val merged = (hits.map {
            ApiSearchDocResult(filename = it.path, snippet = it.snippet, lineRef = null)
        } + graphResults.map {
            ApiSearchDocResult(filename = it.nodeId, snippet = it.summary ?: "", lineRef = null)
        }).distinctBy { it.filename to it.snippet }.take(6)
        call.respond(HttpStatusCode.OK, ApiSearchDocsReply(results = merged))
    }
}
```

(If `ctx.knowledgeGraph` doesn't exist on TutorContext, replace that block with `emptyList()`. Many of these adapters are already wired in JarvisToolset.kt; the goal of the spec is to surface them via this route — exact wiring may need a small adapter. If `HybridRetriever.search` requires a different signature than this, mirror the pattern from `JarvisToolset.kt:539`.)

- [ ] **Step 2: Add types**

```kotlin
@Serializable
private data class ApiSearchDocResult(val filename: String, val snippet: String, val lineRef: String?)

@Serializable
private data class ApiSearchDocsReply(val results: List<ApiSearchDocResult>)
```

- [ ] **Step 3: Add test**

Append to `GapsRouteTest.kt` (or a new file `SearchDocsRouteTest.kt`):

```kotlin
@Test
fun `POST gap search-docs returns empty results when no archival corpus`(@TempDir tmp: Path) = testApplication {
    var ctx: TutorContext? = null
    application { freshTutor(tmp); ctx = attributes[TutorContextKey] }
    startApplication()
    val (_, sid) = seed(ctx!!)
    val csrf = "test-csrf-12345"
    val client = createClient {
        install(HttpCookies)
        install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val r1 = client.post("/api/v1/gap") {
        cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
        contentType(ContentType.Application.Json)
        setBody("""{"topic":"riemann","type":"CONCEPT","trigger":"EXPLICIT_ASK","content":"x","taskId":"T9"}""")
    }
    val gapId = Regex("\"id\":\"([^\"]+)\"").find(r1.bodyAsText())!!.groupValues[1]
    val r2 = client.post("/api/v1/gap/$gapId/search-docs") {
        cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
    }
    assertEquals(HttpStatusCode.OK, r2.status, r2.bodyAsText())
    assertTrue(r2.bodyAsText().contains("\"results\""))
}
```

- [ ] **Step 4: Run + commit**

```bash
gradle :test
```
Expected: 561 + 1 = 562 backend.

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/GapsRouteTest.kt
git commit -m "$(cat <<'EOF'
Phase 4.2 backend: POST /api/v1/gap/{id}/search-docs

Calls HybridRetriever.search(topic, k=3) + KnowledgeGraphQuery.query if
the context graph is loaded; merges + dedupes to up to 6 results
(filename + snippet). Returns 404 if gap missing / cross-user.
Catches retriever exceptions to empty list — Phase 4 surface, doesn't
need to fail closed when the corpus is empty in test harnesses.

Backend tests 561 → 562.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: §4.2 Frontend — KnowledgeGapCard SHOW DOCS button

**Files:**
- Modify: `tutor-web/src/components/KnowledgeGapCard.tsx`.
- Create: `tutor-web/src/__tests__/showDocs.test.tsx`.

- [ ] **Step 1: Add SHOW DOCS button + collapsible results**

In `KnowledgeGapCard.tsx`, add state + handler:

```tsx
const [docsOpen, setDocsOpen] = useState(false);
const [docs, setDocs] = useState<{ filename: string; snippet: string; lineRef: string | null }[] | null>(null);
const [docsError, setDocsError] = useState<string | null>(null);

async function loadDocs() {
  if (docs != null) { setDocsOpen(o => !o); return; }
  setDocsError(null);
  try {
    const r = await jarvisFetch(`/api/v1/gap/${encodeURIComponent(gap.id)}/search-docs`, { method: "POST" });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const data = await r.json();
    setDocs(data.results ?? []);
    setDocsOpen(true);
  } catch (e) {
    setDocsError((e as Error).message);
  }
}
```

Add the button in the action row (alongside INSERT / RESOLVE / DISMISS):

```tsx
<button onClick={loadDocs} data-testid="knowledge-gap-show-docs"
        className="text-xs tracking-widest bg-page-bg text-page-fg/80 px-3 py-1 border border-border-thin">
  {docsOpen ? "HIDE DOCS" : "SHOW DOCS"}
</button>
```

Render results when expanded (after the action row, inside the card):

```tsx
{docsOpen && docs && (
  <div data-testid="knowledge-gap-docs" className="mt-2 border-t border-border-thin pt-2 space-y-1">
    {docs.length === 0 ? (
      <div className="text-xs text-page-fg/60">no docs found</div>
    ) : (
      docs.map((d, i) => (
        <div key={`${d.filename}-${i}`} className="text-xs">
          <div className="font-bold">{d.filename}{d.lineRef ? ` :${d.lineRef}` : ""}</div>
          <div className="text-page-fg/70 whitespace-pre-wrap">{d.snippet}</div>
        </div>
      ))
    )}
  </div>
)}
{docsError && (
  <div className="text-xs text-danger-text mt-1">docs load failed: {docsError}</div>
)}
```

- [ ] **Step 2: Write test**

`tutor-web/src/__tests__/showDocs.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { KnowledgeGapCard } from "../components/KnowledgeGapCard";
import type { KnowledgeGap } from "../lib/knowledgeGap";

const gap: KnowledgeGap = {
  id: "g-1", topic: "laplace", language: "math",
  type: "CONCEPT", trigger: "EXPLICIT_ASK",
  content: "explain laplace transform",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("SHOW DOCS POSTs + renders inline results", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/gap/g-1/search-docs") && init?.method === "POST") {
      return new Response(JSON.stringify({
        results: [
          { filename: "PA/study_guide/laplace.pdf", snippet: "Laplace transform definition...", lineRef: null },
          { filename: "PS/Tema_A.pdf", snippet: "Apply the transform...", lineRef: null },
        ],
      }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<KnowledgeGapCard gap={gap} />);
  fireEvent.click(screen.getByTestId("knowledge-gap-show-docs"));
  await waitFor(() => expect(screen.getByTestId("knowledge-gap-docs")).toBeInTheDocument());
  expect(screen.getByText(/laplace.pdf/)).toBeInTheDocument();
  expect(screen.getByText(/Tema_A.pdf/)).toBeInTheDocument();
});

test("SHOW DOCS toggles HIDE DOCS on second click without re-fetch", async () => {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify({ results: [] }), { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
  render(<KnowledgeGapCard gap={gap} />);
  const btn = screen.getByTestId("knowledge-gap-show-docs");
  fireEvent.click(btn);
  await waitFor(() => expect(btn.textContent).toMatch(/HIDE DOCS/));
  fireEvent.click(btn);
  expect(btn.textContent).toMatch(/SHOW DOCS/);
  expect(fetchMock.mock.calls.length).toBe(1);
});
```

- [ ] **Step 3: Run + commit**

```bash
npm test -- --run showDocs
npm test -- --run
```
Expected: 101 + 2 = 103 frontend.

```bash
git add tutor-web/src/components/KnowledgeGapCard.tsx tutor-web/src/__tests__/showDocs.test.tsx
git commit -m "$(cat <<'EOF'
Phase 4.2 frontend: KnowledgeGapCard SHOW DOCS collapsible

Toggles a results section under the gap card. First click POSTs
/api/v1/gap/{id}/search-docs (lazy load); subsequent clicks toggle
visibility without re-fetching. Empty results show "no docs found".
Per [[Progressive Disclosure]] — collapsed by default.

Frontend tests 101 → 103.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: §4.4 Cross-device server cookie

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` — set `jarvis_last_task` cookie on each GET `/api/v1/tasks/{id}/scratchpad` (the route a workspace mount fires) AND a new GET `/api/v1/last-task` for the App to read on first paint.
- Modify: `tutor-web/src/App.tsx` — fetch `/api/v1/last-task` once (in addition to localStorage); prefer it on cold mount.
- Create: `tutor-web/src/__tests__/crossDeviceSync.test.tsx`.

`document.cookie` is JS-readable for non-httpOnly cookies. To survive across devices we need a server cookie. Set it httpOnly so JS can't fake it; backend route reads it.

- [ ] **Step 1: Backend — add `GET /api/v1/last-task` + set cookie on workspace activity**

```kotlin
get("/api/v1/last-task") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@get }
    val sid = call.request.cookies["jarvis_session"]
    val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
        ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@get }
    val taskId = call.request.cookies["jarvis_last_task"]
    // Validate the cookie still maps to a real task for this user.
    val valid = taskId?.let { TaskRepo(ctx.db).findById(it)?.takeIf { t -> t.userId == userId } }
    call.respond(HttpStatusCode.OK, ApiLastTaskReply(taskId = valid?.id))
}

post("/api/v1/last-task") {
    val ctx = application.attributes.getOrNull(TutorContextKey)
        ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@post }
    call.csrfProtect {
        val sid = call.request.cookies["jarvis_session"]
        val userId = sid?.let { SessionRepo(ctx.db).findUserId(it) }
            ?: run { call.respond(HttpStatusCode.Unauthorized, "invalid session"); return@csrfProtect }
        val req = try {
            sensorJson.decodeFromString(ApiLastTaskReply.serializer(), call.receiveText())
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "malformed: ${e.message?.take(160)}")
            return@csrfProtect
        }
        val taskId = req.taskId ?: ""
        val valid = if (taskId.isNotBlank())
            TaskRepo(ctx.db).findById(taskId)?.takeIf { t -> t.userId == userId } else null
        if (valid == null) {
            call.respond(HttpStatusCode.BadRequest, "taskId not found"); return@csrfProtect
        }
        call.response.cookies.append(
            io.ktor.http.Cookie(
                name = "jarvis_last_task",
                value = valid.id,
                httpOnly = true,
                secure = true,
                extensions = mapOf("SameSite" to "Strict"),
                path = "/",
                maxAge = 60 * 60 * 24 * 30,
            ),
        )
        call.respond(HttpStatusCode.OK, ApiLastTaskReply(taskId = valid.id))
    }
}
```

Add `@Serializable private data class ApiLastTaskReply(val taskId: String?)` near other types.

- [ ] **Step 2: Frontend — App reads cookie on mount + POSTs on taskId change**

In `App.tsx`, after the `ensureTutorSession` effect, add:

```tsx
const [serverLastTask, setServerLastTask] = useState<string | null>(null);
const [serverLastTaskLoaded, setServerLastTaskLoaded] = useState(false);
useEffect(() => {
  jarvisFetch("/api/v1/last-task")
    .then(r => r.ok ? r.json() : null)
    .then((d: { taskId?: string } | null) => {
      setServerLastTask(d?.taskId ?? null);
      setServerLastTaskLoaded(true);
    })
    .catch(() => setServerLastTaskLoaded(true));
}, []);
```

Adjust the cold-start restore logic to prefer server cookie:

```tsx
// existing block:
useEffect(() => {
  if (restoredOnce.current) {
    if (explicitTaskId) {
      try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
    }
    return;
  }
  if (!serverLastTaskLoaded) return;  // wait for /last-task before deciding
  restoredOnce.current = true;
  if (explicitTaskId) {
    try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
    return;
  }
  if (pickMode) return;
  // Server cookie wins if present.
  if (serverLastTask) {
    setParams({ taskId: serverLastTask }, { replace: true });
    return;
  }
  try {
    const last = localStorage.getItem(LAST_TASK_KEY);
    if (last && last !== "TEST-TASK-A") {
      setParams({ taskId: last }, { replace: true });
    }
  } catch (_) {}
}, [explicitTaskId, pickMode, setParams, serverLastTask, serverLastTaskLoaded]);
```

POST cookie when explicit task id changes:

```tsx
useEffect(() => {
  if (!explicitTaskId || explicitTaskId === "TEST-TASK-A") return;
  jarvisFetch("/api/v1/last-task", {
    method: "POST",
    body: JSON.stringify({ taskId: explicitTaskId }),
  }).catch(() => {});
}, [explicitTaskId]);
```

- [ ] **Step 3: Test**

`tutor-web/src/__tests__/crossDeviceSync.test.tsx`:

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { App } from "../App";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  localStorage.clear();
});
afterEach(() => { vi.unstubAllGlobals(); });

test("App prefers server jarvis_last_task cookie over localStorage on cold mount", async () => {
  localStorage.setItem("jarvis.lastTaskId", "TASK_LOCAL");
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/last-task")) {
      return new Response(JSON.stringify({ taskId: "TASK_SERVER" }), { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/tutor/auto-session")) {
      return new Response("{}", { status: 200 });
    }
    if (typeof url === "string" && url.includes("/api/v1/tasks")) {
      return new Response(JSON.stringify({ tasks: [{ id: "TASK_SERVER" }] }), { status: 200 });
    }
    return new Response("{}", { status: 200 });
  }));
  render(<MemoryRouter initialEntries={["/"]}><App /></MemoryRouter>);
  await waitFor(() => {
    // App should have navigated to ?taskId=TASK_SERVER (server cookie wins).
    expect(window.location.search).toContain("TASK_SERVER");
  });
});
```

NOTE: jsdom location is harder to assert. If `MemoryRouter` doesn't expose URL changes, fall back to: assert `setParams` was called with `TASK_SERVER`. Or use `useLocation()` probe pattern from existing TaskQuickStart tests.

- [ ] **Step 4: Run + commit**

```bash
gradle :test  # 562 → 564 (add 2 last-task tests if you also write them — optional; the GET path is exercised through the frontend test)
cd tutor-web && npm test -- --run
```
Expected frontend: 103 + 1 = 104.

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt tutor-web/src/App.tsx tutor-web/src/__tests__/crossDeviceSync.test.tsx
git commit -m "$(cat <<'EOF'
Phase 4.4: cross-device session sync via jarvis_last_task cookie

GET /api/v1/last-task returns the validated cookie value; POST
/api/v1/last-task sets it (httpOnly + Strict + Secure, 30-day TTL).
App fetches /last-task on mount; cold-start logic now prefers the
server cookie over localStorage when present, falls back to
localStorage when not. Each explicit-taskId change POSTs to update
the cookie.

Frontend tests +1.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: §4.3 PdfPane react-pdf swap + selection tooltip (HEAVIEST)

**Files:**
- Modify: `tutor-web/package.json` — add `react-pdf` + `pdfjs-dist`.
- Modify: `tutor-web/src/components/PdfPane.tsx`.
- Create: `tutor-web/src/__tests__/pdfSelection.test.tsx`.

This is the most complex Phase 4 item. Bundle cost ~200KB. pdfjs needs a worker URL (CDN or bundled). React-PDF v9 expects `pdfjs-dist` v4 peer.

If this task blocks (e.g. pdfjs worker issues in jsdom test), document the failure + defer the React-PDF swap to a backlog item. The selection-tooltip flow can ship later through the chat path (user types instead of selects).

- [ ] **Step 1: Install deps**

```bash
cd tutor-web && npm install --save react-pdf pdfjs-dist
```

- [ ] **Step 2: Replace PdfPane content**

Read the current `PdfPane.tsx`. Replace the iframe block with:

```tsx
import { useEffect, useRef, useState } from "react";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";
import { jarvisFetch } from "../lib/api";

// pdf.js worker (must match the bundled pdfjs-dist version).
pdfjs.GlobalWorkerOptions.workerSrc = `https://cdn.jsdelivr.net/npm/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

export interface PdfPaneProps {
  url: string;
  /** Called when user highlights text + clicks "I don't know this".
   *  Parent (TutorWorkspace → ChatPane) emits a <gap> envelope into chat. */
  onPdfSelectionGap?: (selection: { text: string; page: number }) => void;
}

export function PdfPane({ url, onPdfSelectionGap }: PdfPaneProps) {
  const [error, setError] = useState<string | null>(null);
  const [size, setSize] = useState<number | null>(null);
  const [numPages, setNumPages] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [tooltip, setTooltip] = useState<{ x: number; y: number; text: string; page: number } | null>(null);

  useEffect(() => {
    let cancelled = false;
    jarvisFetch(url)
      .then(async r => {
        if (cancelled) return;
        if (!r.ok) { setError(`HTTP ${r.status}`); return; }
        const blob = await r.blob();
        setSize(blob.size);
        if (blob.size < 200) setError("placeholder PDF (<200 bytes)");
      })
      .catch(e => { if (!cancelled) setError((e as Error).message); });
    return () => { cancelled = true; };
  }, [url]);

  useEffect(() => {
    function onSelectionChange() {
      const sel = window.getSelection();
      if (!sel || sel.rangeCount === 0 || sel.isCollapsed) {
        setTooltip(null); return;
      }
      const text = sel.toString().trim();
      if (text.length < 3) { setTooltip(null); return; }
      const range = sel.getRangeAt(0);
      // Confirm selection is inside our pdf-pane.
      if (!containerRef.current?.contains(range.commonAncestorContainer)) {
        setTooltip(null); return;
      }
      // Find the page number from the closest data-page-number ancestor.
      let node: Node | null = range.commonAncestorContainer;
      let page = 1;
      while (node && node !== containerRef.current) {
        if (node instanceof HTMLElement && node.dataset.pageNumber) {
          page = parseInt(node.dataset.pageNumber, 10) || 1;
          break;
        }
        node = node.parentNode;
      }
      const rect = range.getBoundingClientRect();
      const containerRect = containerRef.current!.getBoundingClientRect();
      setTooltip({
        x: rect.left - containerRect.left,
        y: rect.bottom - containerRect.top + 4,
        text: text.slice(0, 200),
        page,
      });
    }
    document.addEventListener("selectionchange", onSelectionChange);
    return () => document.removeEventListener("selectionchange", onSelectionChange);
  }, []);

  if (error) {
    return (
      <div data-testid="pdf-pane" className="h-full bg-surface-muted overflow-auto relative p-6 font-mono text-sm">
        <div className="text-xs font-bold tracking-widest text-page-fg/70 mb-2">PDF</div>
        <div className="text-page-fg/80 mb-2 break-all">{url}</div>
        <div className="bg-accent-soft border-l-4 border-accent-rule p-3 mb-3">
          <div className="font-bold text-xs tracking-widest mb-1">PDF NOT VIEWABLE</div>
          <div className="text-xs text-page-fg/70">{error}</div>
        </div>
      </div>
    );
  }

  return (
    <div data-testid="pdf-pane"
         tabIndex={0}
         aria-label="PDF viewer"
         ref={containerRef}
         className="h-full bg-surface-muted overflow-auto relative">
      <Document
        file={url}
        onLoadSuccess={({ numPages }) => setNumPages(numPages)}
        onLoadError={(e) => setError(e.message)}
        loading={<div className="p-6 text-xs">loading PDF…</div>}
      >
        {numPages && Array.from({ length: numPages }, (_, i) => i + 1).map(p => (
          <Page key={p} pageNumber={p} renderTextLayer={true} renderAnnotationLayer={false} />
        ))}
      </Document>
      {tooltip && (
        <button
          data-testid="pdf-selection-tooltip"
          onClick={() => {
            onPdfSelectionGap?.({ text: tooltip.text, page: tooltip.page });
            setTooltip(null);
            window.getSelection()?.removeAllRanges();
          }}
          className="absolute z-10 bg-panel-dark-bg text-panel-dark-fg text-xs font-bold tracking-widest px-2 py-1"
          style={{ left: tooltip.x, top: tooltip.y }}
        >
          🤷 I don't know this
        </button>
      )}
      {size != null && (
        <div className="absolute bottom-1 right-1 bg-page-fg/70 text-overlay-fg text-xs px-2 py-0.5 rounded">
          {(size / 1024).toFixed(1)} KB
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Wire `onPdfSelectionGap` through TutorWorkspace → ChatPane**

`TutorWorkspace.tsx`: lift a callback that emits a gap into ChatPane. Simplest: ChatPane exposes a callback ref or use the existing chat-input pre-fill pattern (set chat input to `<gap>...</gap>` and submit). But that goes through the LLM round trip.

Simpler: PdfPane → TutorWorkspace wraps its `<PdfPane ... onPdfSelectionGap={selection => emit(selection)} />`. Emit can:
1. POST `/api/v1/gap` directly (since the gap is user-initiated, no LLM round-trip needed) with `topic = selection.text`, `trigger = "EXPLICIT_ASK"`, `content = selection.text`, `taskId`.
2. Then dispatch a window event the ChatPane listens for to refresh `historicalGaps`.

Add to TutorWorkspace.tsx:

```tsx
async function emitSelectionGap(selection: { text: string; page: number }) {
  try {
    await jarvisFetch("/api/v1/gap", {
      method: "POST",
      body: JSON.stringify({
        topic: selection.text,
        type: "CONCEPT",
        trigger: "EXPLICIT_ASK",
        content: selection.text,
        sourceCitation: `pdf:page=${selection.page}`,
        taskId,
      }),
    });
    window.dispatchEvent(new CustomEvent("jarvis:gap-created", { detail: { taskId } }));
  } catch (_) {}
}
```

Pass to PdfPane: `<PdfPane url={pdfUrl} onPdfSelectionGap={emitSelectionGap} />`.

In `ChatPane.tsx`, the existing GET `/api/v1/gaps?taskId=` effect listens for the event:

```tsx
useEffect(() => {
  function onGapCreated() { /* re-fetch historicalGaps */ }
  window.addEventListener("jarvis:gap-created", onGapCreated);
  return () => window.removeEventListener("jarvis:gap-created", onGapCreated);
}, [taskId]);
```

(Inline the re-fetch logic from Task 4 step 1.)

- [ ] **Step 4: Test**

`tutor-web/src/__tests__/pdfSelection.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, beforeEach, afterEach, test, expect } from "vitest";
import { PdfPane } from "../components/PdfPane";

// react-pdf needs canvas + worker; mock at the module level so jsdom doesn't try to load pdf.js.
vi.mock("react-pdf", () => ({
  Document: ({ children, file }: any) => <div data-testid="mock-pdf-document" data-file={file}>{children}</div>,
  Page: ({ pageNumber }: any) => <div data-page-number={pageNumber}>Page {pageNumber} content for selection</div>,
  pdfjs: { GlobalWorkerOptions: {}, version: "9.0.0" },
}));

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async () => new Response(new Blob(["%PDF-fake".repeat(100)]), { status: 200 })));
});
afterEach(() => { vi.unstubAllGlobals(); });

test("PdfPane shows tooltip on text selection ≥3 chars + emits gap on click", async () => {
  const onGap = vi.fn();
  render(<PdfPane url="/sample.pdf" onPdfSelectionGap={onGap} />);
  // Force document load success state.
  // Simulate a selection by setting the selection range manually.
  const pageEl = await waitFor(() => screen.getByText(/Page 1 content/));
  const range = document.createRange();
  range.selectNode(pageEl);
  const sel = window.getSelection()!;
  sel.removeAllRanges();
  sel.addRange(range);
  document.dispatchEvent(new Event("selectionchange"));
  const tooltip = await waitFor(() => screen.getByTestId("pdf-selection-tooltip"));
  fireEvent.click(tooltip);
  expect(onGap).toHaveBeenCalledWith(expect.objectContaining({
    text: expect.stringContaining("Page 1 content"),
    page: 1,
  }));
});
```

- [ ] **Step 5: Run + commit (or DEFER to backlog if blocked)**

```bash
npm test -- --run pdfSelection
npm test -- --run
```
Expected: 104 + 1 = 105.

If react-pdf blows up with `Promise.withResolvers is not defined` or worker init errors in jsdom, the entire pdfSelection test will fail. In that case:
1. Confirm the test failure mode.
2. Document the blocker in `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` under "## Phase 4 — Layer B §4 close" as `[4] [pdf-react-swap] [med] react-pdf jsdom worker init issue — defer to Phase 8 final pass with happy-dom or playwright instead of jsdom`.
3. Roll back `PdfPane.tsx` to the iframe version (Phase 3.4 state).
4. Commit only the deps + test as marker work; ship the spec § 4.3 SHOW DOCS button + selection-tooltip flow in a future phase.

If working:

```bash
git add tutor-web/package.json tutor-web/package-lock.json tutor-web/src/components/PdfPane.tsx tutor-web/src/components/TutorWorkspace.tsx tutor-web/src/components/ChatPane.tsx tutor-web/src/__tests__/pdfSelection.test.tsx
git commit -m "$(cat <<'EOF'
Phase 4.3: react-pdf swap + selection tooltip

PdfPane uses react-pdf (pdf.js with accessible TextLayer) instead of
<iframe>. selectionchange listener watches for ≥3-char highlights
inside the pdf-pane container; floating tooltip "🤷 I don't know
this" appears near the selection. Click POSTs /api/v1/gap directly
with topic = selection text, sourceCitation = pdf:page=N, then fires
jarvis:gap-created so ChatPane re-fetches historical gaps.

Bundle adds ~200KB (react-pdf + pdfjs-dist worker via CDN). pdf.js
worker URL pinned to bundled pdfjs-dist version.

Frontend tests +1.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Code Gate

```bash
git push origin main
```

Wait for CI green.

---

## Task 10: Live Gate

```bash
cd tutor-web && npm run build
cd /c/Users/User/jarvis-kotlin
git add src/main/resources/tutor-dist/
git commit -m "Phase 4: rebuild frontend bundle"
git push origin main
& "C:\Program Files\Git\bin\bash.exe" tools/deploy.sh
curl -sS https://corgflix.duckdns.org/healthz   # ok
```

---

## Task 11: Playwright Gate

Spawn Playwright agent. Scenarios:
1. Gap envelope persists — chat with a `<gap>`-emitting message; reload; PREVIOUSLY FLAGGED list shows the gap.
2. SHOW DOCS — click the button on a gap card; results render below.
3. PDF selection → tooltip — open PDF, highlight text, tooltip appears, click → gap appears in chat.
4. Cross-device — open task on viewport A; clear localStorage; navigate `/tutor/`; should land back on the cookie-remembered task.

If 4.3 is deferred, scenario 3 maps to "no regression on iframe-PDF rendering".

---

## Task 12: UX-Playbook Gate

Spec § Phase 4 Gates: focus on **State Visibility** (gap card lifecycle) + **Direct Manipulation** (selection → tooltip → action).

---

## Task 13: Final review

Phase 4 DoD:
- Backend tests 562-564 (depending on Task 5+7 test coverage).
- Frontend tests 103-105 (depending on Task 8 outcome).
- Daemon untouched.
- CI green.
- Live healthz ok + bundle hash matches.
- Playwright PASS or documented deferrals.
- UX-Playbook 0 HIGH; MED/LOW in backlog.

---

## Out of scope (do NOT do this in Phase 4)

- TaskDetector / cron-driven re-scrape / unique index — Phase 6.
- Inline reference popups / Knowledge Ledger drawer / sympy / 5-layer prompt-injection — Phase 7.
- Plotly inline / cron probe install — Phase 8.
- Anki promotion from gaps — Phase 7 FSRS work.
