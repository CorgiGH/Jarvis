# E1 — Trustworthy Grader + Per-KC Mastery Signal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a drill grade trustworthy (deterministic where checkable, defer-don't-record when not) and record it into a new explicit per-KC mastery signal — so personalization has an honest foundation.

**Architecture:** Insert a deterministic scoring layer between `DrillGrader.grade` (the LLM call) and the HTTP reply: recompute `score` from rubric booleans server-side (never trust the LLM's self-reported float), gate on internal-consistency confidence (LOW → defer, don't record), and add an optional canonical-answer exact-match that overrides the LLM. Add a new `kc_mastery` table updated by an explicit EWMA rule, written only by confident grades that name their target KC(s). Reuse the existing grade route, DrillGrader, content corpus, and SQLite/Exposed persistence.

**Tech Stack:** Kotlin 2.0.21 / JDK 21, Ktor 3.0.1 (server-test-host for HTTP tests), Exposed (SQLite WAL at `~/.jarvis/tutor.db`), kotlinx.serialization, JUnit 5 (`kotlin.test`).

---

## Context the engineer needs (verified against the code 2026-05-29)

- **Grade route:** `POST /api/v1/drill/grade` at `src/main/kotlin/jarvis/web/TutorRoutes.kt:1748`. Handler deserializes `ApiDrillGradeRequest` (`TutorRoutes.kt:2283`), calls `DrillGrader.grade(...)` (`src/main/kotlin/jarvis/tutor/DrillGrader.kt:156`), maps the result to `ApiDrillGradeReply` (`TutorRoutes.kt:2303`, mapping block `1804-1831`), responds at `1833`, then fire-and-forget logs a `TutorEvent` (`1839-1888`). **It writes NOTHING to the DB and never touches FSRS.**
- **The trust hole:** `GradeResult.score` (`DrillGrader.kt:14`) is the LLM's own self-reported float; `tryParse` only validates it as `doubleOrNull`. The route maps it straight through. The rubric booleans (also LLM-emitted) are not used to derive the score.
- **Grade types (do not redefine — import/reuse):**
  - `GradeResult(correct: Boolean, rubric: Map<String, Boolean>, score: Double, misconception: String?, elaboratedFeedback: String)` — `DrillGrader.kt:14`
  - `GradeAttempt(parsed: GradeResult?, rawOutput: String, modelResolved: String)` — `DrillGrader.kt:36`
  - `DrillGrader.grade(problemStatement, userAttempt, expectedHint, llm, language?, referenceSolution?, rubricItems?, prediction?, giveUp): GradeAttempt` — `DrillGrader.kt:156`
- **Persistence:** SQLite at `~/.jarvis/tutor.db`; tables declared as Exposed `object`s (pattern: `FsrsCardsTable` in `src/main/kotlin/jarvis/tutor/FsrsCards.kt:28`). Schema is created at startup via `SchemaUtils.create(...)` in `TutorRoutes.kt:2358`. `UsersTable.id` (`varchar(26)`) is the FK target used by existing tables (e.g. `TasksTable` in `Tasks.kt:69` uses `.references(UsersTable.id)`).
- **No mastery store exists** (grep `mastery` = 0 matches). This plan creates the first one.
- **KC ids** live in `content/PA/kcs/pa-kc-00{1..6}.yaml` (e.g. `pa-kc-001`); `KnowledgeConcept` (`src/main/kotlin/jarvis/content/ContentSchema.kt:28`) carries no rubric/answer fields — practice/grading concerns stay in the grade layer.
- **Test harness:** NO gradle wrapper in this repo — use the installed gradle: `gradle :test --tests "jarvis.tutor.XxxTest"` (the `:` root qualifier is REQUIRED, else the `:android` subproject swallows `--tests`). Gradle binary: `/c/Tools/gradle-8.10/bin/gradle`. Pure-unit pattern: `DrillGraderTest.kt` (inject a fake `Llm`). HTTP-integration pattern: `TutorRoutesTest.kt` (`testApplication {}`, `@TempDir` SQLite, `installFreshTutor(tmp)`, `seedSession(ctx)`).

## Scope & deferrals (deliberate)

- **In scope:** deterministic score recomputation, internal-consistency confidence + defer, optional canonical-answer exact-match, a new `kc_mastery` table + explicit EWMA rule, and wiring `drill/grade` → mastery for confident grades.
- **Deferred (NOT this plan):** code-execution sandbox (PA attempts are proofs/pseudocode → text path; no runnable-code need yet); prereq-gated mastery unlocking (needs the edges graph — store the signal now, gate later); connecting drill outcomes to FSRS *card* scheduling (drills aren't cards yet); the UTC/clock open item (FSRS paths verified UTC-clean — see note).
- **UTC note:** all FSRS due-date timestamps use `java.time.Instant` (UTC) end-to-end; the only `LocalDate.now()` is event-log file rotation (`TutorEventLog.kt:76,84`), isolated from scheduling. The BRIDGE-HEAD "+3h skew" is not reproducible in current code — verify against prod data before spending effort; do not add a fix task speculatively.

---

## File Structure

- **Create** `src/main/kotlin/jarvis/tutor/GradeScoring.kt` — pure, LLM-independent scoring functions (score recompute, confidence, answer normalize/match). One responsibility: deterministic scoring decisions. No I/O.
- **Create** `src/main/kotlin/jarvis/tutor/KcMastery.kt` — `KcMasteryTable` + `KcMastery` data class (with the explicit rule constants) + `KcMasteryRepo`. One responsibility: per-KC mastery persistence + update rule.
- **Modify** `src/main/kotlin/jarvis/web/TutorRoutes.kt` — extend `ApiDrillGradeRequest` (+ `canonicalAnswer`, `conceptIds`) and `ApiDrillGradeReply` (+ `confidence`, `recorded`, `answerMatch`); apply the deterministic layer in the handler; register `KcMasteryTable` in the startup `SchemaUtils.create`.
- **Create tests** `src/test/kotlin/jarvis/tutor/GradeScoringTest.kt`, `src/test/kotlin/jarvis/tutor/KcMasteryTest.kt`, `src/test/kotlin/jarvis/tutor/DrillGradeMasteryRouteTest.kt`.

---

### Task 1: Deterministic score recomputation (`GradeScoring`)

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/GradeScoring.kt`
- Test: `src/test/kotlin/jarvis/tutor/GradeScoringTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradeScoringTest {
    @Test fun `score is fraction of rubric items passed`() {
        assertEquals(0.5, GradeScoring.scoreFromRubric(mapOf("a" to true, "b" to false)), 1e-9)
        assertEquals(1.0, GradeScoring.scoreFromRubric(mapOf("a" to true, "b" to true)), 1e-9)
    }

    @Test fun `empty rubric scores zero, not crash`() {
        assertEquals(0.0, GradeScoring.scoreFromRubric(emptyMap()), 1e-9)
    }

    @Test fun `correct iff every rubric item passed`() {
        assertTrue(GradeScoring.correctFromRubric(mapOf("a" to true, "b" to true)))
        assertFalse(GradeScoring.correctFromRubric(mapOf("a" to true, "b" to false)))
        assertFalse(GradeScoring.correctFromRubric(emptyMap()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests "jarvis.tutor.GradeScoringTest"`
Expected: FAIL — `GradeScoring` is unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package jarvis.tutor

/**
 * Deterministic, LLM-independent scoring layer (E1 trustworthy-grader).
 * The LLM produces rubric booleans + prose; THIS object decides the score,
 * correctness, and confidence. The LLM's self-reported `score` float is never trusted.
 */
object GradeScoring {

    /** Score = fraction of rubric items the grader marked passed. */
    fun scoreFromRubric(rubric: Map<String, Boolean>): Double {
        if (rubric.isEmpty()) return 0.0
        return rubric.values.count { it }.toDouble() / rubric.size
    }

    /** Correct iff the rubric is non-empty and every item passed. */
    fun correctFromRubric(rubric: Map<String, Boolean>): Boolean =
        rubric.isNotEmpty() && rubric.values.all { it }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests "jarvis.tutor.GradeScoringTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/GradeScoring.kt src/test/kotlin/jarvis/tutor/GradeScoringTest.kt
git commit -m "feat(e1): deterministic score recompute from rubric booleans"
```

---

### Task 2: Internal-consistency confidence (defer-don't-record)

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/GradeScoring.kt`
- Test: `src/test/kotlin/jarvis/tutor/GradeScoringTest.kt`

Rule: a grade is **confident** only when the LLM's own `correct` verdict agrees with its own rubric booleans. If the LLM says "correct" but a rubric item is false (or says "wrong" but every item passed), the grade is internally incoherent → LOW confidence → caller must defer (not record into mastery).

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test fun `confident when llm correct flag agrees with its rubric`() {
        val coherentPass = GradeResult(true, mapOf("a" to true, "b" to true), 1.0, null, "ok")
        val coherentFail = GradeResult(false, mapOf("a" to true, "b" to false), 0.5, "m", "fb")
        assertTrue(GradeScoring.isConfident(coherentPass))
        assertTrue(GradeScoring.isConfident(coherentFail))
    }

    @Test fun `not confident when llm correct flag contradicts its rubric`() {
        val saysCorrectButItemFalse = GradeResult(true, mapOf("a" to true, "b" to false), 0.9, null, "fb")
        val saysWrongButAllPass = GradeResult(false, mapOf("a" to true, "b" to true), 0.2, "m", "fb")
        assertFalse(GradeScoring.isConfident(saysCorrectButItemFalse))
        assertFalse(GradeScoring.isConfident(saysWrongButAllPass))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests "jarvis.tutor.GradeScoringTest"`
Expected: FAIL — `isConfident` is unresolved.

- [ ] **Step 3: Write minimal implementation**

Add to `GradeScoring` (object body):

```kotlin
    /**
     * LOW confidence when the LLM's own correctness verdict is internally inconsistent
     * with the rubric booleans it emitted. Such grades MUST be deferred, never recorded.
     */
    fun isConfident(llm: GradeResult): Boolean =
        llm.correct == correctFromRubric(llm.rubric)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests "jarvis.tutor.GradeScoringTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/GradeScoring.kt src/test/kotlin/jarvis/tutor/GradeScoringTest.kt
git commit -m "feat(e1): internal-consistency confidence gate (defer-dont-record)"
```

---

### Task 3: Canonical-answer exact-match (deterministic override)

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/GradeScoring.kt`
- Test: `src/test/kotlin/jarvis/tutor/GradeScoringTest.kt`

When the caller supplies a canonical final answer, the server decides the match deterministically — the LLM does not get a vote on the final answer. Normalization is whitespace/case/trailing-punctuation insensitive; numeric answers compare as doubles.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test fun `answer match is case whitespace and trailing-punctuation insensitive`() {
        assertTrue(GradeScoring.answerMatches("O(n log n)", "  o(n  log n).  "))
        assertFalse(GradeScoring.answerMatches("O(n log n)", "O(n^2)"))
    }

    @Test fun `numeric answers compare as numbers`() {
        assertTrue(GradeScoring.answerMatches("42", "42.0"))
        assertTrue(GradeScoring.answerMatches("0.5", " .5 ")) // normalized then numeric
        assertFalse(GradeScoring.answerMatches("42", "43"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests "jarvis.tutor.GradeScoringTest"`
Expected: FAIL — `answerMatches` unresolved.

- [ ] **Step 3: Write minimal implementation**

Add to `GradeScoring`:

```kotlin
    /** Normalize a free-text answer for exact comparison. */
    fun normalizeAnswer(s: String): String =
        s.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', ',', ';', ':', ' ')

    /** Deterministic match of a student answer against a canonical answer (string or numeric). */
    fun answerMatches(canonical: String, attempt: String): Boolean {
        val c = normalizeAnswer(canonical)
        val a = normalizeAnswer(attempt)
        if (c == a) return true
        val cn = c.toDoubleOrNull()
        val an = a.toDoubleOrNull()
        return cn != null && an != null && kotlin.math.abs(cn - an) < 1e-9
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests "jarvis.tutor.GradeScoringTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/GradeScoring.kt src/test/kotlin/jarvis/tutor/GradeScoringTest.kt
git commit -m "feat(e1): deterministic canonical-answer exact-match"
```

---

### Task 4: Per-KC mastery store (`kc_mastery` table + EWMA rule)

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/KcMastery.kt`
- Test: `src/test/kotlin/jarvis/tutor/KcMasteryTest.kt`

Explicit, tunable rule (not opaque knowledge-tracing): EWMA of graded scores, `mastered` when the EWMA is sustained high over enough observations. Only confident grades reach here (Task 5 enforces).

- [ ] **Step 1: Write the failing test**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class KcMasteryTest {
    private fun freshDb(tmp: Path): Database {
        val db = Database.connect("jdbc:sqlite:${tmp.resolve("m.db")}", "org.sqlite.JDBC")
        transaction(db) { SchemaUtils.create(KcMasteryTable) }
        return db
    }

    @Test fun `first observation seeds ewma to the score`(@TempDir tmp: Path) {
        val repo = KcMasteryRepo(freshDb(tmp))
        assertNull(repo.get("u1", "pa-kc-001"))
        val m = repo.record("u1", "pa-kc-001", 1.0)
        assertEquals(1.0, m.ewmaScore, 1e-9)
        assertEquals(1, m.observations)
        assertFalse(m.mastered) // observations < MIN_OBSERVATIONS
    }

    @Test fun `ewma blends prior and new with alpha`(@TempDir tmp: Path) {
        val repo = KcMasteryRepo(freshDb(tmp))
        repo.record("u1", "pa-kc-001", 1.0)        // ewma 1.0
        val m = repo.record("u1", "pa-kc-001", 0.0) // 0.4*0 + 0.6*1.0 = 0.6
        assertEquals(0.6, m.ewmaScore, 1e-9)
        assertEquals(2, m.observations)
    }

    @Test fun `mastered after sustained high ewma over enough observations`(@TempDir tmp: Path) {
        val repo = KcMasteryRepo(freshDb(tmp))
        repo.record("u1", "pa-kc-001", 1.0)
        repo.record("u1", "pa-kc-001", 1.0)
        val m = repo.record("u1", "pa-kc-001", 1.0)
        assertTrue(m.mastered) // ewma 1.0 >= 0.8 and observations 3 >= 3
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests "jarvis.tutor.KcMasteryTest"`
Expected: FAIL — `KcMasteryTable` / `KcMastery` / `KcMasteryRepo` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/** Per-KC mastery signal. PK = (userId, kcId). */
object KcMasteryTable : Table("kc_mastery") {
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val kcId = varchar("kc_id", 64)
    val ewmaScore = double("ewma_score")
    val observations = integer("observations")
    val lastGradedAt = timestamp("last_graded_at")
    override val primaryKey = PrimaryKey(userId, kcId)
}

data class KcMastery(
    val userId: String,
    val kcId: String,
    val ewmaScore: Double,
    val observations: Int,
    val lastGradedAt: Instant,
) {
    /** Explicit mastery rule: sustained high EWMA over enough observations. */
    val mastered: Boolean get() = ewmaScore >= MASTERY_THRESHOLD && observations >= MIN_OBSERVATIONS

    companion object {
        const val EWMA_ALPHA = 0.4         // weight on the newest score
        const val MASTERY_THRESHOLD = 0.8  // EWMA needed to count as mastered
        const val MIN_OBSERVATIONS = 3     // minimum graded attempts before mastery can latch
    }
}

class KcMasteryRepo(private val db: Database) {

    fun get(userId: String, kcId: String): KcMastery? = transaction(db) {
        KcMasteryTable.selectAll()
            .where { (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kcId) }
            .map {
                KcMastery(
                    it[KcMasteryTable.userId], it[KcMasteryTable.kcId],
                    it[KcMasteryTable.ewmaScore], it[KcMasteryTable.observations],
                    it[KcMasteryTable.lastGradedAt],
                )
            }
            .singleOrNull()
    }

    /** Apply one graded score via the EWMA rule; upsert and return the updated mastery. */
    fun record(userId: String, kcId: String, score: Double, now: Instant = Instant.now()): KcMastery =
        transaction(db) {
            val existing = get(userId, kcId)
            val newEwma =
                if (existing == null) score
                else KcMastery.EWMA_ALPHA * score + (1 - KcMastery.EWMA_ALPHA) * existing.ewmaScore
            val newObs = (existing?.observations ?: 0) + 1
            if (existing == null) {
                KcMasteryTable.insert {
                    it[KcMasteryTable.userId] = userId
                    it[KcMasteryTable.kcId] = kcId
                    it[ewmaScore] = newEwma
                    it[observations] = newObs
                    it[lastGradedAt] = now
                }
            } else {
                KcMasteryTable.update({ (KcMasteryTable.userId eq userId) and (KcMasteryTable.kcId eq kcId) }) {
                    it[ewmaScore] = newEwma
                    it[observations] = newObs
                    it[lastGradedAt] = now
                }
            }
            KcMastery(userId, kcId, newEwma, newObs, now)
        }
}
```

> Note: existing FSRS code uses `timestamp(...)` from Exposed for `Instant` columns (`FsrsCards.kt:28`). Match that project's import (`org.jetbrains.exposed.sql.javatime.timestamp`) — if `FsrsCards.kt` imports a different path, copy that exact import instead.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :test --tests "jarvis.tutor.KcMasteryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/jarvis/tutor/KcMastery.kt src/test/kotlin/jarvis/tutor/KcMasteryTest.kt
git commit -m "feat(e1): per-KC mastery store with explicit EWMA rule"
```

---

### Task 5: Wire `drill/grade` → deterministic layer + mastery

**Files:**
- Modify: `src/main/kotlin/jarvis/web/TutorRoutes.kt` (request/reply types `2283`/`2303`; handler mapping block `1804-1833`; schema bootstrap `2358`)
- Test: `src/test/kotlin/jarvis/tutor/DrillGradeMasteryRouteTest.kt`

The route now: recompute score from rubric (Task 1), override `correct` deterministically when `canonicalAnswer` is present (Task 3), compute confidence (Task 2), and — only when confident and `conceptIds` are supplied — record each KC's mastery (Task 4). Deferred grades record nothing.

- [ ] **Step 1: Write the failing integration test**

```kotlin
package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * NOTE on harness reuse: this test mirrors TutorRoutesTest.kt's
 * `POST drill grade writes a redacted TutorEvent` (lines 106-161). Copy that test's
 * `installFreshTutor(tmp)` + `seedSession(ctx)` helpers verbatim (or call them if shared).
 * It also needs a fake grader: the real handler instantiates OpenRouterChatLlm() directly,
 * so this test asserts the DETERMINISTIC + MASTERY layer, injecting rubric outcomes via the
 * request body where the handler's deterministic path is exercised. If the handler cannot be
 * fed a fake LLM, gate the LLM call behind a test-injectable seam first (see Step 3 note).
 */
class DrillGradeMasteryRouteTest {

    @Test fun `confident grade records KC mastery`(@TempDir tmp: Path) = testApplication {
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", "test-csrf"); header("X-CSRF-Token", "test-csrf")
            contentType(ContentType.Application.Json)
            setBody(
                """{"taskId":"t1","problemId":"p1","problemStatement":"complexity of mergesort?",
                   "userAttempt":"O(n log n)","expectedAnswerHint":"linearithmic",
                   "canonicalAnswer":"O(n log n)","conceptIds":["pa-kc-001"]}"""
            )
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val m = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNotNull(m, "confident grade must record mastery")
        assertEquals(1, m.observations)
    }

    @Test fun `deferred (incoherent) grade records nothing`(@TempDir tmp: Path) = testApplication {
        // Drive an internally-incoherent grade so confidence is LOW; assert no mastery row.
        // (Use the same setup; supply inputs that make the grader's correct flag disagree with its rubric,
        //  OR inject a fake grader returning GradeResult(correct=true, rubric={"a":false}, ...).)
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, _) = seedSession(ctx!!)
        // ... post a request whose graded result is incoherent (see Step 3 seam) ...
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :test --tests "jarvis.tutor.DrillGradeMasteryRouteTest"`
Expected: FAIL — new request fields (`canonicalAnswer`, `conceptIds`) and the mastery write don't exist yet.

- [ ] **Step 3: Extend the request/reply types**

In `TutorRoutes.kt:2283`, add two nullable fields to `ApiDrillGradeRequest` (keep existing fields):

```kotlin
    val canonicalAnswer: String? = null,
    val conceptIds: List<String>? = null,
```

In `TutorRoutes.kt:2303`, add to `ApiDrillGradeReply` (keep existing fields):

```kotlin
    val confidence: String,   // "HIGH" | "LOW"
    val recorded: Boolean,    // true iff this grade updated mastery
    val answerMatch: Boolean? = null, // present iff canonicalAnswer was supplied
```

> **Test seam note:** the handler instantiates `OpenRouterChatLlm()` directly (`TutorRoutes.kt:1778`), so a test can't inject grader output. Before wiring, extract the LLM construction into a swappable property on the route module (e.g. a `var drillGraderLlmFactory: () -> Llm = { OpenRouterChatLlm() }` near `installTutorRoutes`), and have the handler call `drillGraderLlmFactory()`. The integration tests override it to return a fake `Llm` (pattern: `DrillGraderTest.kt:89`). This seam is a prerequisite for Step 1's assertions — add it in this step.

- [ ] **Step 4: Apply the deterministic layer + mastery write in the handler**

Replace the success branch of the mapping block (`TutorRoutes.kt:1804-1831`, the `else` where `attempt.parsed != null`) with logic that uses `GradeScoring` and writes mastery. Concretely, after obtaining `val g: GradeResult = attempt.parsed!!`:

```kotlin
// Deterministic correctness: rubric-derived, with canonical-answer override when provided.
val answerMatch: Boolean? = req.canonicalAnswer?.let { GradeScoring.answerMatches(it, req.userAttempt) }
val rubricCorrect = GradeScoring.correctFromRubric(g.rubric)
val deterministicCorrect = answerMatch ?: rubricCorrect
val deterministicScore = GradeScoring.scoreFromRubric(g.rubric)

// Confidence: incoherent grade, OR canonical answer disagrees with the rubric verdict.
val coherent = GradeScoring.isConfident(g)
val answerAgrees = answerMatch == null || answerMatch == rubricCorrect
val confident = coherent && answerAgrees

// Record mastery ONLY for confident grades that name their KC(s).
var recorded = false
if (confident && !req.conceptIds.isNullOrEmpty()) {
    val repo = KcMasteryRepo(ctx.db) // `ctx` = the TutorContext already in scope in this handler
    req.conceptIds.forEach { kcId -> repo.record(userId, kcId, deterministicScore) }
    recorded = true
}

val reply = ApiDrillGradeReply(
    correct = deterministicCorrect,
    score = deterministicScore,
    rubric = g.rubric,
    misconception = g.misconception,
    elaboratedFeedback = g.elaboratedFeedback,
    confidence = if (confident) "HIGH" else "LOW",
    recorded = recorded,
    answerMatch = answerMatch,
)
```

> Confirm the in-scope names while editing: the handler already resolves `userId` (`TutorRoutes.kt:1753-1755`) and has access to the `TutorContext` (the same `ctx`/`db` used elsewhere in this file for repos — grep the handler for how `FsrsCardRepo`/`KnowledgeGaps` obtain their `Database`, and reuse that exact accessor). Also update the `error` / `parse_error` branches to send `confidence = "LOW"`, `recorded = false`, `answerMatch = null` so the reply type is always satisfied.

- [ ] **Step 5: Register the new table at startup**

In `TutorRoutes.kt:2358`, add `KcMasteryTable` to the `SchemaUtils.create(...)` argument list (alongside the existing tables).

```kotlin
SchemaUtils.create(/* ...existing tables..., */ KcMasteryTable)
```

- [ ] **Step 6: Typecheck + run the new tests**

Run: `gradle :test --tests "jarvis.tutor.DrillGradeMasteryRouteTest"`
Expected: PASS (both tests).

- [ ] **Step 7: Run the full suite (catch cross-cutting regressions)**

Run: `gradle :test`
Expected: PASS. If `TutorRoutesTest`'s drill-grade test breaks because the reply gained fields, update its assertions to expect the new `confidence`/`recorded` fields (the client uses `ignoreUnknownKeys = true`, so deserialization is safe; only explicit field assertions need updating).

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/jarvis/web/TutorRoutes.kt src/test/kotlin/jarvis/tutor/DrillGradeMasteryRouteTest.kt
git commit -m "feat(e1): drill/grade applies deterministic scoring + records confident KC mastery"
```

---

## Self-Review (completed against the design + maps)

1. **Spec coverage** — design E1 asks for: deterministic-where-checkable (Tasks 1, 3 ✓), LLM-only-for-prose (Task 5 keeps `elaboratedFeedback`/`misconception` from the LLM, score/correct now deterministic ✓), low-confidence→defer-don't-record (Tasks 2, 5 ✓), explicit per-KC mastery rule (Task 4 ✓), and the grade→mastery link that didn't exist (Task 5 ✓). Deferred items (sandbox, prereq-gating, FSRS-card link, UTC) are listed with rationale.
2. **Placeholder scan** — Task 5 Steps 1 & 4 intentionally carry guidance prose ("see seam", "grep how repos obtain db") because the exact in-scope `Database` accessor and the fake-LLM seam must be read from the file at edit time; every code step shows real code. The Step-1 "deferred grade" test body is sketched, not complete — the executor must finalize it once the Step-3 seam exists. Flagged here so it isn't mistaken for done.
3. **Type consistency** — `GradeResult` reused as-is (not redefined); `GradeScoring` function names (`scoreFromRubric`, `correctFromRubric`, `isConfident`, `answerMatches`, `normalizeAnswer`) are stable across Tasks 1-5; `KcMastery`/`KcMasteryRepo.record` signature consistent between Task 4 and Task 5.
4. **Build+mount pairing** — N/A (no new frontend components this slice; backend-only).
5. **Component-reuse contract** — N/A (no React component remount).
6. **`data-testid` grep** — N/A (no visual-acceptance section).

**Two executor-facing caveats** (carried, not hidden): (a) Task 5 requires introducing a test seam for the grader LLM before its integration tests can assert — that's Step 3. (b) The "deferred grade" test (Task 5 Step 1, second test) needs finalizing against that seam. Both are called out in-line.

---

## Execution Handoff
See the chat message after this file is saved for the two execution options.
