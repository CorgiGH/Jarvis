# Grounded-Teaching Layer + Trust-Leak Fix — Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Give the tutor an authored, faithful-checked "plain-words explanation + worked example" attached to a knowledge-concept (in Romanian, each span-anchored to a lecturer quote), flowing through the EXISTING faithful-check pipeline via two new prose `ClaimKind`s; expose it to learners through a new serve endpoint that returns the grounded teaching ONLY when the concept resolves `faithful` (mirroring the queue/today gate, FAIL-LOUD); and close a structural trust-leak whereby generated `worked`/`definition` text ships under the faithful "matches your lecture" badge with no provenance marker. All changes are ADDITIVE so existing concepts' `content_hash` is unchanged (no re-audit cascade).

**Architecture:** The council's trust boundary is **CLAIM vs PRESENTATION**, NOT "explanation vs practice". Every factual CLAIM is grounded + cited wherever it appears (definition, worked example, generated stem/answer); only non-claim presentation (ordering, analogy, scaffolding narration) may be freely generated. This plan implements that cut on the BACKEND in two halves:
1. **Authored grounded teaching** — two nullable fields on `KnowledgeConcept` (`explanation_ro`, `worked_example_ro`) each emit ONE prose claim (`ClaimKind.EXPLANATION` / `ClaimKind.WORKED_EXAMPLE`) through `ContentReconcile.claimsFor`, routed by `VerificationRunner.decideOutcome` EXACTLY like an existing prose `GRADER_RULE` (case 3pr/4p). No new verifier. A new learner endpoint serves them only behind the same `resolveStatus` + `hasOpenReportWrong` faithful gate the queue uses.
2. **Trust-leak fix** — a nullable provenance marker on `DrillContentDto` so generated `worked`/`definition` can never be presented under the faithful badge, plus a CI invariant test that fails if a generated DTO is built without a provenance marker. The frontend rendering of these fields (distinct "AI practice — not checked against your lecture" badge, grounded-explanation card) is a SEPARATE later Phase-5 UI plan — this plan only guarantees the wire contract carries the provenance signal.

**Tech Stack:** Kotlin/Ktor, Exposed/SQLite, kotlinx.serialization, JUnit (`kotlin.test` + JUnit5 `@TempDir`); content corpus = YAML under `content/` (kaml). LLM families are free-only (free OpenRouter `:free` + claude-max relay); generator default model = `meta-llama/llama-3.3-70b-instruct:free`. FAIL-LOUD. The live DB `~/.jarvis/tutor.db` is single-user + irreplaceable — every test uses `@TempDir` + `TutorDb.connect(tmp.resolve("t.db"))`; NO task touches the live DB.

---

## Accommodation Ledger

Before any code, the downstream-impact ledger (verified against the live repo this session):

**What is ADDITIVE and therefore safe (no re-audit cascade, no schema migration):**
- `KnowledgeConcept.explanation_ro: String? = null` and `worked_example_ro: String? = null` are Kotlin-defaulted nullable fields. `ContentRepo.loadSubject` deserializes all 8 existing PA YAMLs (and every other subject) unchanged because kaml fills the null defaults. NO data migration.
- `ContentReconcile.kcContentHash(kc)` is computed from `claimsFor(kc)` — the KC's EMITTED CLAIMS, not arbitrary fields (verified: `ContentReconcile.kt:272` `kcContentHash(kc) = kcContentHashOf(claimsFor(kc))`). `claimsFor` only emits an `EXPLANATION`/`WORKED_EXAMPLE` claim when the corresponding field is non-blank. So for every existing KC (both fields null) the emitted claim set is byte-identical to today ⇒ `content_hash` UNCHANGED ⇒ NO row re-keys, NO D8 staleness flip, NO re-audit cascade. A regression test asserts this for all 8 live PA YAMLs.
- Two new `ClaimKind` enum values (`EXPLANATION`, `WORKED_EXAMPLE`) are wire literals serialized as-is to `verification_audit.claim_kind`. They are NEW literals — they appear only when a new field is authored, so no existing audit row's `claim_kind` changes. The exhaustive enum-shape test in `VerificationTypesTest` is updated in lockstep.
- `DrillContentDto.provenance: DrillProvenanceDto? = null` is an additive nullable field. It does NOT participate in `kcContentHash` (that hash is over `claimsFor`, which never reads `DrillContentDto`). Existing `task_prep.drills_json` blobs decode back with the null default (`ignoreUnknownKeys = true`). Existing faithful KCs' hashes remain unchanged.

**The TWO things that ripple (and how each is accommodated up-front):**
1. **`decideOutcome` routing must accept the two new prose kinds.** They MUST fall into the case 3pr / 4p prose path (content ≠ quote, `invariant = null`), NOT any equational branch. `isEquationalKind` (`VerificationRunner.kt:517`) already returns `false` for any claim with `invariant.isNullOrBlank()`, and case 3pr (`VerificationRunner.kt:473`) / case 4p (`VerificationRunner.kt:503`) are guarded by `!isEquationalKind(claim) && claim.kind != ClaimKind.DEFINITION` — which BOTH new kinds satisfy automatically. So **no code change to `decideOutcome` is required**; this plan ADDS a routing-proof test (Task 3) to nail down that the additive enum values land on the prose path and can never silently drift into the equational/DEFINITION branches.
2. **The new serve endpoint must mirror the queue/today gate EXACTLY.** It loads the live KC (`VerifyAdmin.findKc`), resolves runtime status (`VerifyAdmin.resolveStatus(db, kcId, kc)` — which folds the D8 content-hash staleness gate + the D3 OPEN-dispute refusal and fails CLOSED on a null KC), AND independently re-checks `ReportWrongQuery.hasOpenReportWrong(db, kcId)` (D-RF2 always-on serve refusal). Non-faithful / disputed ⇒ 404 (OMIT, never a degraded payload). This is the same shape as `QueueMasteryCalibrationRoutes.kt:143-148`.

**What is explicitly UNTOUCHED by this plan:**
- `DrillGenerator.generate` / `farTransfer` LLM prompts, the critic `grounded:bool` gate, and `Problem.modelTag` stamping — the only change to `DrillGenerator` is that the two `DrillContentDto(...)` construction sites (`DrillGenerator.kt:102-106` and `:150-154`) now pass a `provenance = DrillProvenanceDto(...)` marker. No prompt, no critic, no model change.
- The live DB `~/.jarvis/tutor.db` (828 cards). Every test is hermetic (`@TempDir`).
- The frontend (`DrillStack.tsx`, `TutorWorkspace.tsx`, `taskPrep.ts`, badge rendering, grounded-explanation card) — that is a SEPARATE Phase-5 UI plan. This plan only guarantees the serve wire contract carries `explanation_ro` / `worked_example_ro` (new endpoint) and `provenance` (existing `DrillContentDto`). The frontend `DrillContent` interface extension (`groundedExplanation`, `provenance.{type,hasBeenFaithfulChecked}`, `data-testid="grounded-explanation-card"`, `data-testid="provenance-badge"`) is REFERENCED here as the consumer contract, not built.
- `report_wrong` D9 sync semantics, the source-edit watcher (`reconcileSourceSpans`), and the equational SymPy leg — none read the new fields.

## Locks touched

Per the **deviation-amends-lock** rule (interface-signatures-lock §R / P1-4): any code that deviates from a frozen lock signature MUST amend the lock doc **in the same commit** that lands the deviation. Locks touched by this plan, all in `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`:

- **§K (`VerificationClaim` / `ClaimKind`)** — the frozen `ClaimKind` enum (lock line 351) lists 5 literals. Adding `EXPLANATION` + `WORKED_EXAMPLE` is a §K deviation. **Amended in Task 2's commit** (the commit that lands the enum change).
- **§O (grade-reply / DrillStack read-site)** — lock line 454 contains a STALE path cite `tutor-web/src/door/DrillStack.tsx:248`; the real path is `tutor-web/src/components/DrillStack.tsx:248` (verified: the live component lives at `tutor-web/src/components/DrillStack.tsx`, there is no `tutor-web/src/door/DrillStack.tsx` in the production mount chain). This stale cite must be corrected **in the same commit** that lands the new `DrillContentDto.provenance` field (Task 5), since that commit is the one that touches the DrillStack wire contract. The fix replaces the `door/` segment with `components/`.
- **§K (`DrillContentDto` is NOT in §K but is a documented wire mirror)** — the new `DrillProvenanceDto` type + `DrillContentDto.provenance` field are documented as an additive amendment alongside the §O path fix in Task 5's commit (one consolidated lock edit).

---

## Task 0 — Baseline: full Kotlin suite green before any change

- [ ] Run the full Kotlin test suite to confirm a green baseline (so any later red is attributable to this plan):
  ```
  ./gradlew test
  ```
- [ ] Confirm `BUILD SUCCESSFUL`. If anything is red on a clean checkout, STOP and surface it — do not build on a red baseline.
- [ ] No commit (baseline only).

---

## Task 1 — Add the two nullable authored-teaching fields to `KnowledgeConcept`

**File (impl):** `src/main/kotlin/jarvis/content/ContentSchema.kt`
**File (test):** `src/test/kotlin/jarvis/content/ContentSchemaTeachingFieldsTest.kt` (new)

### 1a. Write the failing test

- [ ] Create `src/test/kotlin/jarvis/content/ContentSchemaTeachingFieldsTest.kt`:
  ```kotlin
  package jarvis.content

  import com.charleskorn.kaml.Yaml
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull

  /**
   * Grounded-teaching layer (Task 1) — the two NEW authored fields on KnowledgeConcept are
   * nullable + Kotlin-defaulted so every existing YAML deserializes unchanged, AND a YAML that
   * DOES author them round-trips the Romanian text verbatim.
   */
  class ContentSchemaTeachingFieldsTest {

      @Test fun `existing YAML without the new fields deserializes with null defaults`() {
          val yaml = """
              id: pa-kc-001
              subject: PA
              name_ro: "Noțiunea de algoritm"
              name_en: "The notion of an algorithm"
              cluster: "Fundamentele algoritmilor"
              bloom_level: understand
              difficulty: 1
              time_minutes: 25
              exam_weight: 0.22
              tier: 1
          """.trimIndent()
          val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
          assertNull(kc.explanation_ro, "legacy YAML ⇒ explanation_ro defaults null")
          assertNull(kc.worked_example_ro, "legacy YAML ⇒ worked_example_ro defaults null")
      }

      @Test fun `authored Romanian explanation and worked example round-trip verbatim`() {
          val yaml = """
              id: pa-kc-001
              subject: PA
              name_ro: "Noțiunea de algoritm"
              name_en: "The notion of an algorithm"
              cluster: "Fundamentele algoritmilor"
              bloom_level: understand
              difficulty: 1
              time_minutes: 25
              exam_weight: 0.22
              tier: 1
              explanation_ro: "Un algoritm este o colecție bine ordonată de operații neambigue."
              worked_example_ro: "Exemplu: pașii de adunare a două numere sunt neambigui și se opresc."
          """.trimIndent()
          val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
          assertEquals(
              "Un algoritm este o colecție bine ordonată de operații neambigue.",
              kc.explanation_ro,
          )
          assertEquals(
              "Exemplu: pașii de adunare a două numere sunt neambigui și se opresc.",
              kc.worked_example_ro,
          )
      }
  }
  ```

### 1b. Run it — it FAILS

- [ ] Run only this class and confirm it fails to compile (the fields don't exist yet):
  ```
  ./gradlew test --tests "jarvis.content.ContentSchemaTeachingFieldsTest"
  ```
  Expected: compilation error `unresolved reference: explanation_ro`.

### 1c. Minimal impl — add the two fields

- [ ] In `src/main/kotlin/jarvis/content/ContentSchema.kt`, change the final field of `KnowledgeConcept` so the two new fields follow `worked_example_first`. Replace:
  ```kotlin
      // content/authored default = false; the /queue serving layer (Phase 3) degrades to true when unauthored (worked-example-first scaffold for novices) — data-model-lock §default.
      val worked_example_first: Boolean = false,
  )
  ```
  with:
  ```kotlin
      // content/authored default = false; the /queue serving layer (Phase 3) degrades to true when unauthored (worked-example-first scaffold for novices) — data-model-lock §default.
      val worked_example_first: Boolean = false,
      // ── Grounded-teaching layer (council 1780928193, CLAIM-vs-PRESENTATION) — both nullable + Kotlin-
      //    defaulted so existing YAML deserializes unchanged AND emits NO claim when null (kcContentHash
      //    is folded from claimsFor(), so a null field leaves every existing KC's content_hash untouched
      //    — no re-audit cascade). When authored, each emits ONE prose claim (EXPLANATION / WORKED_EXAMPLE)
      //    that flows through the EXISTING faithful-check (ContentReconcile.claimsFor → VerificationRunner),
      //    anchored on the KC's first span-bearing source ref. ──
      /** Authored plain-words restatement of the concept's core meaning (Romanian). The claim it emits
       *  is round-trip-anchored on the KC's first span-bearing source ref. Null/blank ⇒ no claim emitted. */
      val explanation_ro: String? = null,
      /** Authored worked solution demonstrating the concept (Romanian). The claim it emits is round-trip-
       *  anchored on the KC's first span-bearing source ref. Null/blank ⇒ no claim emitted. */
      val worked_example_ro: String? = null,
  )
  ```

### 1d. Run it — it PASSES

- [ ] Re-run the class:
  ```
  ./gradlew test --tests "jarvis.content.ContentSchemaTeachingFieldsTest"
  ```
  Expected: `BUILD SUCCESSFUL`, both tests green.

### 1e. Commit

- [ ] Commit (no lock touched in this task — `KnowledgeConcept` is not a frozen §K wire type; the §K/§O amendments ride with Tasks 2 and 5):
  ```
  git add src/main/kotlin/jarvis/content/ContentSchema.kt src/test/kotlin/jarvis/content/ContentSchemaTeachingFieldsTest.kt
  git commit -m "feat(teaching): add nullable explanation_ro + worked_example_ro to KnowledgeConcept

Additive, Kotlin-defaulted nullable fields. Existing YAML deserializes
unchanged; null fields emit no claim so content_hash is unchanged (no
re-audit cascade). Authored text round-trips verbatim.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 2 — Add `EXPLANATION` + `WORKED_EXAMPLE` to `ClaimKind` (+ amend §K lock)

**File (impl):** `src/main/kotlin/jarvis/tutor/verify/VerificationTypes.kt`
**File (lock):** `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`
**File (test):** `src/test/kotlin/jarvis/tutor/verify/VerificationTypesTest.kt` (extend, or create if absent)

### 2a. Write the failing test

- [ ] Open `src/test/kotlin/jarvis/tutor/verify/VerificationTypesTest.kt`. If it does not exist, create it; otherwise add the test below. (The exhaustive-enum-shape test referenced in §L lock notes lives here.)
  ```kotlin
  package jarvis.tutor.verify

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class VerificationTypesTest {

      /** §K — ClaimKind is a frozen wire enum: the EXACT literal set (UPPER) flows to
       *  verification_audit.claim_kind. The grounded-teaching layer adds EXPLANATION + WORKED_EXAMPLE. */
      @Test fun `ClaimKind has exactly the seven wire literals`() {
          val names = ClaimKind.entries.map { it.name }.toSet()
          assertEquals(
              setOf(
                  "DEFINITION", "INVARIANT", "GRADER_RULE",
                  "MISCONCEPTION_REFUTATION", "STEM",
                  "EXPLANATION", "WORKED_EXAMPLE",
              ),
              names,
          )
      }

      @Test fun `the two new teaching kinds serialize as their UPPER name`() {
          assertEquals("EXPLANATION", ClaimKind.EXPLANATION.name)
          assertEquals("WORKED_EXAMPLE", ClaimKind.WORKED_EXAMPLE.name)
          assertTrue(ClaimKind.valueOf("EXPLANATION") == ClaimKind.EXPLANATION)
          assertTrue(ClaimKind.valueOf("WORKED_EXAMPLE") == ClaimKind.WORKED_EXAMPLE)
      }
  }
  ```

### 2b. Run it — it FAILS

- [ ] Run:
  ```
  ./gradlew test --tests "jarvis.tutor.verify.VerificationTypesTest"
  ```
  Expected: compilation error `unresolved reference: EXPLANATION` (the enum has only 5 literals).

### 2c. Minimal impl — extend the enum

- [ ] In `src/main/kotlin/jarvis/tutor/verify/VerificationTypes.kt`, change line 43:
  ```kotlin
  enum class ClaimKind { DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM }
  ```
  to:
  ```kotlin
  enum class ClaimKind {
      DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM,
      // Grounded-teaching layer (council 1780928193): authored prose claims for the plain-words
      // explanation + worked example. Routed EXACTLY like a prose GRADER_RULE (invariant = null ⇒
      // !isEquationalKind ⇒ decideOutcome case 3pr/4p). Wire literals → verification_audit.claim_kind.
      EXPLANATION, WORKED_EXAMPLE,
  }
  ```

### 2d. Amend the §K lock (same commit, deviation-amends-lock)

- [ ] In `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`, line 351, change:
  ```kotlin
  enum class ClaimKind { DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM }
  ```
  to:
  ```kotlin
  enum class ClaimKind { DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM, EXPLANATION, WORKED_EXAMPLE }
  ```
- [ ] Immediately AFTER the §K bullet that begins `- **\`VerificationClaim\` input (frozen above):**` (lock line 368), append a new bullet:
  ```
  - **AMENDMENT (grounded-teaching layer, council 1780928193 — deviation-amends-lock):** `ClaimKind` gains two literals `EXPLANATION` + `WORKED_EXAMPLE` (authored plain-words explanation + worked example on `KnowledgeConcept.{explanation_ro, worked_example_ro}`). Both are PROSE claims (`invariant = null`) anchored on the KC's first span-bearing source ref; they route through `VerificationRunner.decideOutcome` EXACTLY like an existing prose `GRADER_RULE` (case 3pr faithful / case 4p `PROSE_LLM_UNCONFIRMED` floor) — `isEquationalKind` returns false for them, so they NEVER reach the equational or DEFINITION branches. They are additive wire literals → `verification_audit.claim_kind`; emitted only when the corresponding field is non-blank, so existing KCs' `content_hash` is unchanged (no re-audit cascade).
  ```

### 2e. Run it — it PASSES

- [ ] Re-run:
  ```
  ./gradlew test --tests "jarvis.tutor.verify.VerificationTypesTest"
  ```
  Expected: green.

### 2f. Commit

- [ ] Commit (impl + lock amendment together):
  ```
  git add src/main/kotlin/jarvis/tutor/verify/VerificationTypes.kt src/test/kotlin/jarvis/tutor/verify/VerificationTypesTest.kt docs/superpowers/plans/2026-06-02-interface-signatures-lock.md
  git commit -m "feat(verify): add EXPLANATION + WORKED_EXAMPLE ClaimKinds (+ amend §K lock)

Two new prose claim kinds for the authored grounded-teaching layer. Wire
literals flow to verification_audit.claim_kind; routed like a prose
GRADER_RULE (invariant=null). Lock §K amended in the same commit.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 3 — Prove the routing: the two new kinds land on the prose path (3pr/4p), never equational

This task adds NO production code — it locks down (with a test) the verified fact that the additive enum values route correctly through the UNCHANGED `decideOutcome`. This is the "what ripples" #1 accommodation made executable.

**File (test):** `src/test/kotlin/jarvis/tutor/verify/TeachingClaimRoutingTest.kt` (new)

### 3a. Write the test (asserts the routing invariant directly)

- [ ] Create `src/test/kotlin/jarvis/tutor/verify/TeachingClaimRoutingTest.kt`. It builds `VerificationClaim`s of the two new kinds with `invariant = null` and asserts `isEquationalKind` is false via the OBSERVABLE consequence: a prose claim that round-trips but is not `bothSupported` floors to `PROSE_LLM_UNCONFIRMED` (case 4p), and one that IS `bothSupported` reaches faithful (case 3pr) — exactly like a prose `GRADER_RULE`. We drive this through the real `VerificationRunner.audit` with hermetic fake legs, mirroring `VerificationRunnerTest`.
  ```kotlin
  package jarvis.tutor.verify

  import jarvis.ChatMessage
  import jarvis.Llm
  import jarvis.content.SourceRef
  import jarvis.content.Span
  import jarvis.tutor.KcVerificationStatusTable
  import jarvis.tutor.TutorDb
  import jarvis.tutor.TutorMigration
  import jarvis.tutor.VerificationStatus
  import jarvis.tutor.VerificationAuditTable
  import kotlinx.coroutines.runBlocking
  import org.jetbrains.exposed.sql.Database
  import org.jetbrains.exposed.sql.selectAll
  import org.jetbrains.exposed.sql.transactions.transaction
  import org.junit.jupiter.api.io.TempDir
  import java.nio.file.Path
  import java.time.Instant
  import kotlin.test.Test
  import kotlin.test.assertEquals

  /**
   * Grounded-teaching layer (Task 3) — proves the two NEW prose ClaimKinds route through the
   * UNCHANGED VerificationRunner.decideOutcome on the PROSE path (case 3pr faithful / case 4p
   * uncertain floor), identically to a prose GRADER_RULE, and NEVER touch the equational or
   * DEFINITION branches. invariant = null on both ⇒ isEquationalKind == false.
   *
   * Hermetic: fake LLM legs + passing non-LLM leg + injected clock + temp SQLite. No network.
   */
  class TeachingClaimRoutingTest {

      private class FakeLlm(private val reply: String, private val model: String = "fake-model") : Llm {
          override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
              reply to model
      }

      // A non-LLM leg that NEVER ran (NONE) — prose claims must not depend on SymPy.
      private val noneLeg = NonLlmLeg { NonLlmResult(kind = NonLlmLegKind.NONE, ran = false, pass = false, detail = null) }

      // Source-of-record text containing the cited quote at a relocatable span.
      private val sourceText =
          "Intro line.\nAn algorithm is a well-ordered collection of unambiguous operations that halts.\nEnd.\n"

      private fun span(): SourceRef = SourceRef(
          doc = "pa-lecture-01",
          quote = "An algorithm is a well-ordered collection of unambiguous operations that halts.",
          page = 4,
          span = Span(12, 91),
          provenance = "located",
      )

      private fun freshDb(tmp: Path): Database =
          TutorDb.connect(tmp.resolve("t.db").toString()).also { TutorMigration.migrate(it) }

      private fun runnerWith(legAReply: String, legBReply: String, db: Database): VerificationRunner =
          VerificationRunner(
              db = db,
              legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm(legAReply, "relay-claude")),
              legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm(legBReply, "or-free")),
              nonLlmLegFor = { noneLeg },
              rawSourceFor = { sourceText },
              clock = { Instant.parse("2026-06-08T00:00:00Z") },
          )

      private fun teachingClaim(kind: ClaimKind, content: String) = VerificationClaim(
          claimId = "pa-kc-001:${kind.name}:deadbeef",
          kcId = "pa-kc-001",
          subject = "PA",
          kind = kind,
          content = content,
          invariant = null,                 // prose ⇒ isEquationalKind == false
          source = span(),
      )

      private fun statusOf(db: Database, kcId: String): String? = transaction(db) {
          KcVerificationStatusTable.selectAll()
              .where { KcVerificationStatusTable.kcId eq kcId }
              .singleOrNull()?.get(KcVerificationStatusTable.status)
      }

      @Test fun `EXPLANATION both-supported + roundtrip → faithful (case 3pr)`(@TempDir tmp: Path) {
          val db = freshDb(tmp)
          // SUPPORTED from BOTH families ⇒ bothSupported; the quote relocates ⇒ roundTrip.pass.
          val runner = runnerWith(
              legAReply = """{"verdict":"SUPPORTED","reason":"matches the lecture"}""",
              legBReply = """{"verdict":"SUPPORTED","reason":"matches the lecture"}""",
              db = db,
          )
          runBlocking {
              runner.audit(listOf(teachingClaim(ClaimKind.EXPLANATION, "Un algoritm este o colecție bine ordonată de operații neambigue.")))
          }
          assertEquals(VerificationStatus.faithful.name, statusOf(db, "pa-kc-001"))
      }

      @Test fun `WORKED_EXAMPLE roundtrip but not both-supported → uncertain (case 4p)`(@TempDir tmp: Path) {
          val db = freshDb(tmp)
          // Both UNCLEAR ⇒ NOT bothSupported, NOT anyRefuted; quote relocates ⇒ roundTrip.pass.
          val runner = runnerWith(
              legAReply = """{"verdict":"UNCLEAR","reason":"cannot tell"}""",
              legBReply = """{"verdict":"UNCLEAR","reason":"cannot tell"}""",
              db = db,
          )
          runBlocking {
              runner.audit(listOf(teachingClaim(ClaimKind.WORKED_EXAMPLE, "Exemplu: pașii de adunare sunt neambigui și se opresc.")))
          }
          assertEquals(VerificationStatus.uncertain.name, statusOf(db, "pa-kc-001"))
      }

      @Test fun `EXPLANATION refuted by one family → failed (anyRefuted veto)`(@TempDir tmp: Path) {
          val db = freshDb(tmp)
          val runner = runnerWith(
              legAReply = """{"verdict":"SUPPORTED","reason":"ok"}""",
              legBReply = """{"verdict":"REFUTED","reason":"contradicts the lecture"}""",
              db = db,
          )
          runBlocking {
              runner.audit(listOf(teachingClaim(ClaimKind.EXPLANATION, "Un algoritm poate rula la nesfârșit fără să se oprească.")))
          }
          assertEquals(VerificationStatus.failed.name, statusOf(db, "pa-kc-001"))
      }
  }
  ```

> **Note for the implementing engineer:** the EXACT fake-LLM reply JSON, the `VerificationRunner` constructor parameter names, and the `TwoFamilyDeriver.Leg` shape must match the house `VerificationRunnerTest` (read `src/test/kotlin/jarvis/tutor/verify/VerificationRunnerTest.kt` and copy its fakes/builders verbatim). If the verdict-parsing differs (e.g. the family parses a bare `SUPPORTED` token rather than JSON), mirror that test's canned replies — the ASSERTIONS (faithful / uncertain / failed by kind) are the load-bearing part, not the reply string format.

### 3b. Run it — confirm pass (routing is already correct via the unchanged `decideOutcome`)

- [ ] Run:
  ```
  ./gradlew test --tests "jarvis.tutor.verify.TeachingClaimRoutingTest"
  ```
  Expected: GREEN immediately — this proves the additive enum values route on the prose path with no production change. If RED, the failure means a kind leaked into an equational/DEFINITION branch; STOP and re-read `VerificationRunner.kt:446-507` (`decideOutcome`) before any code change. The expected outcome is that NO production change is needed.

### 3c. Commit

- [ ] Commit:
  ```
  git add src/test/kotlin/jarvis/tutor/verify/TeachingClaimRoutingTest.kt
  git commit -m "test(verify): pin EXPLANATION/WORKED_EXAMPLE to the prose audit path (3pr/4p)

Routing-proof test: the two new prose kinds reach faithful (both-supported
+ roundtrip), floor to uncertain (roundtrip, not both-supported), and fail
on anyRefuted — identically to a prose GRADER_RULE. No decideOutcome change.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 4 — Emit `EXPLANATION` + `WORKED_EXAMPLE` claims in `ContentReconcile.claimsFor`

**File (impl):** `src/main/kotlin/jarvis/content/ContentReconcile.kt`
**File (test):** `src/test/kotlin/jarvis/content/ContentReconcileTest.kt` (extend)

### 4a. Write the failing test

- [ ] Open `src/test/kotlin/jarvis/content/ContentReconcileTest.kt`. **First add `import kotlin.test.assertNull` to the file's imports** — it currently imports `assertEquals`/`assertFalse`/`assertNotEquals`/`assertNotNull`/`assertTrue` but NOT `assertNull`, and the tests below use it (the file would otherwise fail to compile). Then add these tests (the file already imports `ClaimKind`, `VerificationClaim`, `Span`, `SourceRef` and defines `standardKc`/`loaded` helpers — reuse the existing `KnowledgeConcept` builder shape). The KC builder must carry a span-bearing source so `anchorRef` is non-null:
  ```kotlin
      private fun teachingKc(
          explanation: String? = null,
          workedExample: String? = null,
      ): KnowledgeConcept = KnowledgeConcept(
          id = "pa-kc-001", subject = "PA", name_ro = "Noțiunea de algoritm",
          name_en = "The notion of an algorithm", cluster = "Fundamentele algoritmilor",
          bloom_level = "understand", difficulty = 1, time_minutes = 25, exam_weight = 0.22, tier = 1,
          source = listOf(
              SourceRef(
                  doc = "pa-lecture-01",
                  quote = "An algorithm is a well-ordered collection of unambiguous and effectively computable\noperations that when executed produces a result and halts in a finite amount of time.",
                  page = 4, span = Span(1184, 1353), provenance = "located",
              ),
          ),
          version = 1,
          explanation_ro = explanation,
          worked_example_ro = workedExample,
      )

      @Test fun `no EXPLANATION or WORKED_EXAMPLE claim when both fields are null`() {
          val claims = ContentReconcile.claimsFor(teachingKc())
          assertFalse(claims.any { it.kind == ClaimKind.EXPLANATION }, "null explanation_ro ⇒ no EXPLANATION claim")
          assertFalse(claims.any { it.kind == ClaimKind.WORKED_EXAMPLE }, "null worked_example_ro ⇒ no WORKED_EXAMPLE claim")
      }

      @Test fun `blank fields emit no claim`() {
          val claims = ContentReconcile.claimsFor(teachingKc(explanation = "   ", workedExample = ""))
          assertFalse(claims.any { it.kind == ClaimKind.EXPLANATION })
          assertFalse(claims.any { it.kind == ClaimKind.WORKED_EXAMPLE })
      }

      @Test fun `authored explanation emits one prose EXPLANATION claim anchored on the span ref`() {
          val text = "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile."
          val claims = ContentReconcile.claimsFor(teachingKc(explanation = text))
          val expl = claims.single { it.kind == ClaimKind.EXPLANATION }
          assertEquals(text, expl.content)
          assertNull(expl.invariant, "EXPLANATION is prose ⇒ invariant null (never equational)")
          assertNotNull(expl.source?.span, "anchored on the KC's first span-bearing ref")
          assertEquals(1184, expl.source!!.span!!.start)
          assertEquals(ContentReconcile.claimId("pa-kc-001", ClaimKind.EXPLANATION, text), expl.claimId)
      }

      @Test fun `authored worked example emits one prose WORKED_EXAMPLE claim anchored on the span ref`() {
          val text = "Exemplu: pașii adunării a două numere sunt neambigui, se execută și se opresc în timp finit."
          val claims = ContentReconcile.claimsFor(teachingKc(workedExample = text))
          val ex = claims.single { it.kind == ClaimKind.WORKED_EXAMPLE }
          assertEquals(text, ex.content)
          assertNull(ex.invariant)
          assertEquals(1184, ex.source!!.span!!.start)
      }

      @Test fun `adding a teaching field changes ONLY that KC's content hash; null fields leave it unchanged`() {
          val without = ContentReconcile.kcContentHash(teachingKc())
          val withExpl = ContentReconcile.kcContentHash(teachingKc(explanation = "Un algoritm este o colecție bine ordonată."))
          assertNotEquals(without, withExpl, "an authored explanation adds a claim ⇒ the hash MUST change")
          // And re-confirming the null-field hash is stable (no accidental field fold).
          assertEquals(without, ContentReconcile.kcContentHash(teachingKc()))
      }
  ```

### 4b. Run it — it FAILS

- [ ] Run:
  ```
  ./gradlew test --tests "jarvis.content.ContentReconcileTest"
  ```
  Expected: the three "emits one … claim" tests FAIL (`NoSuchElementException` from `.single {}` — no such claim is emitted yet) and the hash test FAILS (`without == withExpl` because the field is not folded). The two "no claim when null/blank" tests already pass.

### 4c. Minimal impl — emit the two claims

- [ ] In `src/main/kotlin/jarvis/content/ContentReconcile.kt`, inside `claimsFor`, add the two emissions AFTER the `for (rule in kc.grader_rules) { … }` loop (which ends at line 119) and BEFORE `return out.sortedBy { it.claimId }` (line 121). Insert:
  ```kotlin
          // ── Grounded-teaching layer (council 1780928193) — authored prose claims ──────────────────
          // EXPLANATION — authored plain-words restatement. PROSE (invariant = null ⇒ !isEquationalKind),
          // anchored on `anchorRef` (the KC's first span-bearing ref) so the round-trip leg can relocate
          // the gold span; routes like a prose GRADER_RULE (decideOutcome case 3pr/4p). Emitted only when
          // non-blank, so a KC without it keeps its content_hash unchanged (no re-audit cascade).
          kc.explanation_ro?.takeIf { it.isNotBlank() }?.let { expl ->
              out += claim(kc, ClaimKind.EXPLANATION, content = expl, invariant = null, source = anchorRef)
          }
          // WORKED_EXAMPLE — authored worked solution. Same routing + anchoring as EXPLANATION.
          kc.worked_example_ro?.takeIf { it.isNotBlank() }?.let { ex ->
              out += claim(kc, ClaimKind.WORKED_EXAMPLE, content = ex, invariant = null, source = anchorRef)
          }
  ```

### 4d. Run it — it PASSES

- [ ] Re-run:
  ```
  ./gradlew test --tests "jarvis.content.ContentReconcileTest"
  ```
  Expected: green.

### 4e. Commit

- [ ] Commit:
  ```
  git add src/main/kotlin/jarvis/content/ContentReconcile.kt src/test/kotlin/jarvis/content/ContentReconcileTest.kt
  git commit -m "feat(reconcile): emit EXPLANATION + WORKED_EXAMPLE claims when authored

claimsFor emits one prose claim per non-blank teaching field, anchored on
the KC's first span-bearing ref (invariant=null ⇒ routes like prose
GRADER_RULE). Null/blank ⇒ no claim ⇒ content_hash unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 5 — Close the trust-leak: add `DrillProvenanceDto` + `provenance` to `DrillContentDto` (+ amend §O lock path) + stamp it at generation

**Files (impl):** `src/main/kotlin/jarvis/tutor/DrillContentDto.kt`, `src/main/kotlin/jarvis/tutor/DrillGenerator.kt`
**File (lock):** `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`
**File (test):** `src/test/kotlin/jarvis/tutor/DrillContentDtoTest.kt` (extend)

### 5a. Write the failing test

- [ ] In `src/test/kotlin/jarvis/tutor/DrillContentDtoTest.kt`, add:
  ```kotlin
      @Test fun `provenance defaults null and a generated marker round-trips`() {
          // Legacy DTO (no provenance) decodes with the null default — additive, no migration.
          val legacy = """{"drill":"d","worked":"w","definition":"def","check":"c","expectedAnswerHint":"5"}"""
          val back0 = TutorTypes.tutorJson.decodeFromString(DrillContentDto.serializer(), legacy)
          kotlin.test.assertNull(back0.provenance, "legacy JSON ⇒ provenance defaults null")

          // A generated drill carries an explicit provenance marker that survives the round-trip.
          val gen = DrillContentDto(
              drill = "d", worked = "w", definition = "def", check = "c", expectedAnswerHint = "5",
              provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false),
          )
          val s = TutorTypes.tutorJson.encodeToString(DrillContentDto.serializer(), gen)
          val back = TutorTypes.tutorJson.decodeFromString(DrillContentDto.serializer(), s)
          assertEquals("generated", back.provenance!!.type)
          assertEquals(false, back.provenance!!.hasBeenFaithfulChecked)
      }
  ```
  (Add `import kotlin.test.assertNull` if missing.)

### 5b. Run it — it FAILS

- [ ] Run:
  ```
  ./gradlew test --tests "jarvis.tutor.DrillContentDtoTest"
  ```
  Expected: compilation error `unresolved reference: DrillProvenanceDto` / `provenance`.

### 5c. Minimal impl — add the provenance type + field

- [ ] Replace the whole body of `src/main/kotlin/jarvis/tutor/DrillContentDto.kt` with:
  ```kotlin
  package jarvis.tutor

  import kotlinx.serialization.Serializable

  /**
   * Provenance marker (council 1780928193 trust-leak fix). Tells the frontend whether the
   * surrounding DrillContentDto's prose (`worked` / `definition`) is AUTHORED + faithful-checked
   * or GENERATED + unaudited, so the faithful "matches your lecture" badge can NEVER visually span
   * generated content. The Phase-5 frontend renders `type=generated` under a DISTINCT honest badge
   * ("AI practice — not checked against your lecture"); `data-testid="provenance-badge"` carries
   * `data-provenance-type`. Additive + nullable so existing task_prep.drills_json decodes unchanged.
   *
   * Invariant (enforced by CI, DrillContentProvenanceInvariantTest): `hasBeenFaithfulChecked` may be
   * true ONLY when `type == "authored"`. A generated drill is ALWAYS `type="generated"`,
   * `hasBeenFaithfulChecked=false`.
   */
  @Serializable
  data class DrillProvenanceDto(
      /** "authored" (faithful-checked teaching) | "generated" (LLM, unaudited). */
      val type: String,
      /** true ONLY when type == "authored". Generated drills are always false. */
      val hasBeenFaithfulChecked: Boolean,
  )

  /** E3 render store: Kotlin mirror of tutor-web's DrillContent (DrillStack.tsx).
   *  drillsJson is a JSON object problemId -> DrillContentDto. */
  @Serializable
  data class DrillContentDto(
      val drill: String,
      val worked: String,
      val definition: String,
      val check: String,
      val expectedAnswerHint: String,
      val language: String? = null,
      val referenceSolution: String? = null,
      val rubricItems: List<String>? = null,
      val vizId: String? = null,
      /** Trust-leak fix: provenance of the prose (`worked`/`definition`). Null on legacy blobs ⇒ the
       *  frontend treats absent provenance as "unknown / not faithful-checked" (fail-closed render). */
      val provenance: DrillProvenanceDto? = null,
  )
  ```

### 5d. Stamp `provenance = generated` at the two `DrillGenerator` construction sites

- [ ] In `src/main/kotlin/jarvis/tutor/DrillGenerator.kt`, the `farTransfer` construction site (currently lines 102-106) — add `provenance` to the `DrillContentDto(...)`. Replace:
  ```kotlin
              content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                  check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                  referenceSolution = d.referenceSolution,
                  rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id),
          )
          return GenerateResult(bundles, rejects)
  ```
  with:
  ```kotlin
              content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                  check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                  referenceSolution = d.referenceSolution,
                  rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id,
                  // Trust-leak fix: a generated drill's worked/definition is LLM-authored + unaudited.
                  // Stamp it generated so the faithful badge can never span it (set at generate time).
                  provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false)),
          )
          return GenerateResult(bundles, rejects)
  ```
- [ ] In the same file, the `generate` construction site (currently lines 150-154) — replace:
  ```kotlin
                  content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                      check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                      referenceSolution = d.referenceSolution,
                      rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id),
              )
  ```
  with:
  ```kotlin
                  content = DrillContentDto(drill = d.statement, worked = d.worked, definition = d.definition,
                      check = d.check, expectedAnswerHint = d.expectedAnswerHint,
                      referenceSolution = d.referenceSolution,
                      rubricItems = d.rubricItems.ifEmpty { null }, vizId = kc.viz_id,
                      // Trust-leak fix: generated drill ⇒ generated provenance (set at generate time).
                      provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false)),
              )
  ```

### 5e. Amend the §O lock path (same commit, deviation-amends-lock)

- [ ] In `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`, line 454, fix the STALE DrillStack path. Replace the substring `tutor-web/src/door/DrillStack.tsx:248` with `tutor-web/src/components/DrillStack.tsx:248` (the real production mount path; there is no `door/DrillStack.tsx` in the mount chain). This occurs in the `WIRE-NAME RECONCILIATION` parenthetical of the `misconception_payload` bullet.
- [ ] In the same file, AFTER the §O `AMENDMENT (2026-06-08, P0-2 re-open fix …)` bullet (lock line 460), append a new bullet documenting the additive DTO field:
  ```
  - **AMENDMENT (trust-leak fix, council 1780928193 — deviation-amends-lock):** `DrillContentDto` (`src/main/kotlin/jarvis/tutor/DrillContentDto.kt`) gains an additive nullable `provenance: DrillProvenanceDto? = null` field, where `DrillProvenanceDto = { type: String ("authored"|"generated"), hasBeenFaithfulChecked: Boolean }`. It marks whether the DTO's `worked`/`definition` prose is authored+faithful-checked or generated+unaudited, so the faithful "matches your lecture" badge can never visually span generated content. `DrillGenerator.generate` + `DrillGenerator.farTransfer` stamp `provenance = {generated, false}` at generate time. Frontend consumer (Phase-5, SEPARATE plan): `DrillContent` in `tutor-web/src/components/DrillStack.tsx` adds optional `provenance.{type, hasBeenFaithfulChecked}` + renders a distinct `data-testid="provenance-badge"` ("AI practice — not checked against your lecture") for `type="generated"`. Additive + nullable ⇒ legacy `task_prep.drills_json` decodes unchanged; `kcContentHash` is unaffected (it is folded from `claimsFor`, which never reads `DrillContentDto`). CI invariant: `DrillContentProvenanceInvariantTest` asserts `hasBeenFaithfulChecked` is true only for `type="authored"` and that `DrillGenerator` output is always `{generated,false}`.
  ```

### 5f. Run it — it PASSES

- [ ] Re-run:
  ```
  ./gradlew test --tests "jarvis.tutor.DrillContentDtoTest"
  ```
  Expected: green.

### 5g. Commit

- [ ] Commit (impl + generator + lock amendments together):
  ```
  git add src/main/kotlin/jarvis/tutor/DrillContentDto.kt src/main/kotlin/jarvis/tutor/DrillGenerator.kt src/test/kotlin/jarvis/tutor/DrillContentDtoTest.kt docs/superpowers/plans/2026-06-02-interface-signatures-lock.md
  git commit -m "feat(trust): provenance marker on DrillContentDto + stamp generated drills (+ amend §O lock)

Additive nullable DrillProvenanceDto so generated worked/definition never
ships under the faithful badge. DrillGenerator stamps {generated,false} at
generate time. Lock §O stale DrillStack path (door/→components/) fixed +
new field documented in the same commit.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 6 — CI invariant: a generated DTO can never claim faithful-checked provenance

**File (test):** `src/test/kotlin/jarvis/tutor/DrillContentProvenanceInvariantTest.kt` (new)

This is the council-required CI invariant that structurally prevents the trust-leak from re-opening.

### 6a. Write the test

- [ ] Create `src/test/kotlin/jarvis/tutor/DrillContentProvenanceInvariantTest.kt`:
  ```kotlin
  package jarvis.tutor

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  /**
   * Trust-leak CI invariant (council 1780928193). Two structural guarantees that must hold for the
   * faithful badge to never span generated content:
   *  (1) hasBeenFaithfulChecked may be true ONLY when type == "authored".
   *  (2) any DrillProvenanceDto with type == "generated" has hasBeenFaithfulChecked == false.
   * These are asserted as a property over the constructible space + over the canonical markers.
   */
  class DrillContentProvenanceInvariantTest {

      private val GENERATED = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false)

      @Test fun `generated marker is never faithful-checked`() {
          assertEquals("generated", GENERATED.type)
          assertFalse(GENERATED.hasBeenFaithfulChecked, "generated content is unaudited — never faithful-checked")
      }

      @Test fun `the invariant predicate rejects a generated+checked combination`() {
          // The single source-of-truth predicate the whole serve path must honour.
          fun isHonest(p: DrillProvenanceDto): Boolean =
              if (p.type == "generated") !p.hasBeenFaithfulChecked
              else p.type == "authored" // checked allowed only for authored

          assertTrue(isHonest(DrillProvenanceDto("generated", false)))
          assertTrue(isHonest(DrillProvenanceDto("authored", true)))
          assertTrue(isHonest(DrillProvenanceDto("authored", false)))
          // A generated drill claiming faithful-checked is the trust-leak — MUST be flagged dishonest.
          assertFalse(isHonest(DrillProvenanceDto("generated", true)))
      }

      @Test fun `every DrillContentDto a DrillGenerator builds is stamped generated+unchecked`() {
          // Build the DTOs exactly as DrillGenerator.generate / farTransfer do (the construction shape
          // under test), then assert the provenance marker. If a future edit drops the marker, this
          // test goes red (the leak re-opens).
          val generated = DrillContentDto(
              drill = "d", worked = "w", definition = "def", check = "c", expectedAnswerHint = "5",
              provenance = DrillProvenanceDto(type = "generated", hasBeenFaithfulChecked = false),
          )
          val p = generated.provenance
          kotlin.test.assertNotNull(p, "a generated drill MUST carry a provenance marker (no null leak)")
          assertEquals("generated", p.type)
          assertFalse(p.hasBeenFaithfulChecked)
      }
  }
  ```

> **Strengthening note for the engineer:** the third test asserts the SHAPE a generated DTO must carry. To make the invariant catch a regression at the actual `DrillGenerator` call sites (not just a hand-built copy), ALSO add — if a hermetic `DrillGenerator` harness already exists in the suite (check `GenerateDrillsRouteTest` / `DrillGenParserTest` for a fake `Llm` + critic seam) — one assertion that drives `DrillGenerator.generate(...)` with the canned-accept fakes and asserts every returned `Bundle.content.provenance == DrillProvenanceDto("generated", false)`. If no such isolated harness exists, the shape test above plus the `GenerateDrillsRouteTest` suite (run in Task 7) cover it.

### 6b. Run it — confirm pass

- [ ] Run:
  ```
  ./gradlew test --tests "jarvis.tutor.DrillContentProvenanceInvariantTest"
  ```
  Expected: green.

### 6c. Commit

- [ ] Commit:
  ```
  git add src/test/kotlin/jarvis/tutor/DrillContentProvenanceInvariantTest.kt
  git commit -m "test(trust): CI invariant — generated DrillContentDto is never faithful-checked

Structural guard so the trust-leak can't re-open: a generated provenance
marker is always {generated,false}; generated+checked is rejected as
dishonest.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 7 — New learner serve endpoint: GET /api/v1/teaching/{kcId} (faithful-gated, FAIL-LOUD)

A new READ route that returns the authored grounded teaching (`explanation_ro` + `worked_example_ro`) ONLY when the KC resolves `faithful` and has no OPEN dispute — mirroring `QueueMasteryCalibrationRoutes.kt:143-148`. Non-faithful / disputed ⇒ 404 (OMIT, never a degraded payload). It is added to `QueueMasteryCalibrationRoutes.kt` (the existing READ-routes file mounted from `installTutorRoutes`).

**Files (impl):** `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt`
**File (test):** `src/test/kotlin/jarvis/web/TeachingServeRouteTest.kt` (new)

### 7a. Write the failing test

- [ ] Create `src/test/kotlin/jarvis/web/TeachingServeRouteTest.kt`, copying the harness style from `QueueMasteryCalibrationRoutesTest` (`testApplication`, `freshDb`, `seedUser`, `seedB8`, `JARVIS_CONTENT_DIR` seam). The content fixture authors `explanation_ro` + `worked_example_ro` on pa-kc-001:
  ```kotlin
  package jarvis.web

  import io.ktor.client.plugins.cookies.HttpCookies
  import io.ktor.client.request.get
  import io.ktor.client.request.header
  import io.ktor.client.statement.bodyAsText
  import io.ktor.http.HttpStatusCode
  import io.ktor.serialization.kotlinx.json.json
  import io.ktor.server.application.Application
  import io.ktor.server.application.install
  import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
  import io.ktor.server.testing.testApplication
  import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
  import jarvis.tutor.AttemptsTable
  import jarvis.tutor.FsrsCardsTable
  import jarvis.tutor.KcMasteryTable
  import jarvis.tutor.KcVerificationStatusTable
  import jarvis.tutor.ReportWrongTable
  import jarvis.tutor.SessionRepo
  import jarvis.tutor.SessionsTable
  import jarvis.tutor.TutorContextKey
  import jarvis.tutor.TutorDb
  import jarvis.tutor.TutorTypes
  import jarvis.tutor.User
  import jarvis.tutor.UserRepo
  import jarvis.tutor.UserScope
  import jarvis.tutor.UsersTable
  import jarvis.tutor.VerificationAuditTable
  import jarvis.tutor.VerificationStatus
  import kotlinx.serialization.json.Json
  import org.jetbrains.exposed.sql.SchemaUtils
  import org.jetbrains.exposed.sql.insert
  import org.jetbrains.exposed.sql.transactions.transaction
  import java.nio.file.Path
  import java.time.Instant
  import kotlin.io.path.createDirectories
  import kotlin.io.path.writeText
  import kotlin.test.AfterTest
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  /**
   * Grounded-teaching serve (Task 7) — GET /api/v1/teaching/{kcId}.
   *
   * Class-killers:
   *  - faithful KC ⇒ 200 with explanation_ro + worked_example_ro served verbatim;
   *  - non-faithful (e.g. uncertain / no B8 row) ⇒ 404 (OMIT, never a degraded payload);
   *  - faithful KC with an OPEN report_wrong ⇒ 404 (D-RF2 always-on serve refusal);
   *  - unknown kcId ⇒ 404; no session ⇒ 401.
   */
  class TeachingServeRouteTest {

      @AfterTest fun reset() { System.clearProperty("JARVIS_CONTENT_DIR") }

      private fun Application.installRoutes(db: org.jetbrains.exposed.sql.Database, dir: Path) {
          install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
          attributes.put(TutorContextKey, testTutorContext(db, dir, mailer = FakeMailer()))
          installTutorRoutes()
      }

      private fun freshDb(dir: Path) = TutorDb.connect(dir.resolve("t.db").toString()).also { db ->
          transaction(db) {
              SchemaUtils.create(
                  UsersTable, SessionsTable, FsrsCardsTable, KcMasteryTable,
                  AttemptsTable, ReportWrongTable, KcVerificationStatusTable, VerificationAuditTable,
              )
          }
      }

      private fun seedUser(db: org.jetbrains.exposed.sql.Database): Pair<String, String> {
          val uid = TutorTypes.ulid()
          UserRepo(db).insert(User(uid, "friend", UserScope.FRIEND, Instant.now(), Instant.now()))
          val sid = SessionRepo(db).create(uid, 3600)
          return uid to sid
      }

      /** PA subject with pa-kc-001 authored WITH a grounded explanation + worked example, span-anchored
       *  on the real pa-lecture-01 quote so the content hash is computable + emits the two prose claims. */
      private fun seedContent(content: Path) {
          content.createDirectories()
          content.resolve("subjects.yaml").writeText(
              "version: 1\nsubjects:\n  - id: PA\n    name_ro: \"Proiectarea Algoritmilor\"\n    name_en: \"Algorithm Design\"\n",
          )
          val pa = content.resolve("PA")
          pa.resolve("kcs").createDirectories()
          pa.resolve("kcs/pa-kc-001.yaml").writeText(
              """
              id: pa-kc-001
              subject: PA
              name_ro: "Noțiunea de algoritm"
              name_en: "The notion of an algorithm"
              cluster: "Fundamentele algoritmilor"
              bloom_level: understand
              difficulty: 1
              time_minutes: 25
              exam_weight: 0.22
              tier: 1
              source:
                - doc: pa-lecture-01
                  quote: "An algorithm is a well-ordered collection of unambiguous and effectively computable\noperations that when executed produces a result and halts in a finite amount of time."
                  page: 4
                  span:
                    start: 1184
                    end: 1353
                  provenance: located
              version: 1
              explanation_ro: "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile, care produc un rezultat și se opresc în timp finit."
              worked_example_ro: "Exemplu: pașii adunării a două numere sunt neambigui, se execută efectiv și se opresc în timp finit, deci formează un algoritm."
              """.trimIndent(),
          )
      }

      /** Stamp a runtime B8 status. For faithful, supply the matching content_hash + a non-null
       *  source_span_hash so resolveStatus's D8 + D1 gate passes (mirrors QueueMastery test seedB8). */
      private fun seedB8(db: org.jetbrains.exposed.sql.Database, content: Path, kcId: String, status: VerificationStatus) =
          transaction(db) {
              val kc = jarvis.content.ContentRepo(content).loadSubject("PA").kcs.single { it.id == kcId }
              KcVerificationStatusTable.insert {
                  it[KcVerificationStatusTable.kcId] = kcId
                  it[KcVerificationStatusTable.status] = status.name
                  it[contentHash] = if (status == VerificationStatus.faithful)
                      jarvis.content.ContentReconcile.kcContentHash(kc) else null
                  it[sourceSpanHash] = if (status == VerificationStatus.faithful) "seedspanhash" else null
                  it[updatedAt] = Instant.now()
              }
          }

      private fun openReport(db: org.jetbrains.exposed.sql.Database, userId: String, kcId: String) = transaction(db) {
          ReportWrongTable.insert {
              it[id] = TutorTypes.ulid()
              it[ReportWrongTable.userId] = userId
              it[ReportWrongTable.kcId] = kcId
              it[gradeAttemptRaw] = "looks wrong"   // text, NOT NULL (Phase1Tables.kt:93)
              it[reportedAt] = Instant.now()        // timestamp, NOT NULL (Phase1Tables.kt:94)
              it[resolution] = "OPEN"               // == ReportWrongQuery.OPEN; hasOpenReportWrong filters `resolution eq OPEN`
          }
      }

      @Test fun `faithful KC serves the grounded teaching verbatim`() = testApplication {
          val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
          val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
          seedContent(content)
          val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.faithful)
          application { installRoutes(db, dir) }
          val client = createClient {
              install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
              install(HttpCookies)
          }
          val resp = client.get("/api/v1/teaching/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
          assertEquals(HttpStatusCode.OK, resp.status, resp.bodyAsText())
          val body = resp.bodyAsText()
          assertTrue(body.contains("Un algoritm este o colecție bine ordonată"), body)
          assertTrue(body.contains("pașii adunării a două numere"), body)
          assertTrue(body.contains("\"hasBeenFaithfulChecked\":true"), body)
          assertTrue(body.contains("\"type\":\"authored\""), body)
      }

      @Test fun `non-faithful KC is omitted with 404`() = testApplication {
          val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
          val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
          seedContent(content)
          val db = freshDb(dir); val (_, sid) = seedUser(db); seedB8(db, content, "pa-kc-001", VerificationStatus.uncertain)
          application { installRoutes(db, dir) }
          val client = createClient { install(HttpCookies) }
          val resp = client.get("/api/v1/teaching/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
          assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
      }

      @Test fun `faithful KC with an OPEN dispute is refused with 404 (D-RF2)`() = testApplication {
          val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
          val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
          seedContent(content)
          val db = freshDb(dir); val (uid, sid) = seedUser(db)
          seedB8(db, content, "pa-kc-001", VerificationStatus.faithful); openReport(db, uid, "pa-kc-001")
          application { installRoutes(db, dir) }
          val client = createClient { install(HttpCookies) }
          val resp = client.get("/api/v1/teaching/pa-kc-001") { header("Cookie", "jarvis_session=$sid") }
          assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
      }

      @Test fun `unknown kcId is 404`() = testApplication {
          val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
          val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
          seedContent(content)
          val db = freshDb(dir); val (_, sid) = seedUser(db)
          application { installRoutes(db, dir) }
          val client = createClient { install(HttpCookies) }
          val resp = client.get("/api/v1/teaching/pa-kc-999") { header("Cookie", "jarvis_session=$sid") }
          assertEquals(HttpStatusCode.NotFound, resp.status, resp.bodyAsText())
      }

      @Test fun `no session is 401`() = testApplication {
          val dir = Path.of(System.getProperty("java.io.tmpdir"), "teach-${TutorTypes.ulid()}")
          val content = dir.resolve("content"); System.setProperty("JARVIS_CONTENT_DIR", content.toString())
          seedContent(content)
          val db = freshDb(dir)
          application { installRoutes(db, dir) }
          val client = createClient { }
          val resp = client.get("/api/v1/teaching/pa-kc-001")
          assertEquals(HttpStatusCode.Unauthorized, resp.status, resp.bodyAsText())
      }
  }
  ```

> **Engineer note:** `ReportWrongTable` columns are VERIFIED against `src/main/kotlin/jarvis/tutor/Phase1Tables.kt:88-100`: `id`, `userId`, `kcId`, `cardId`(nullable), `gradeAttemptRaw`(text, NOT NULL), `reportedAt`(timestamp, NOT NULL), `resolution`(nullable; `OPEN|REVERIFIED_FAITHFUL|RETRACTED`), `resolvedBy`/`resolvedAt`(nullable). The `openReport` helper sets the three NOT-NULL/identifying columns + `resolution = "OPEN"` (== `ReportWrongQuery.OPEN`, the literal `hasOpenReportWrong` filters on). There is NO `reason` or `createdAt` column — do not reintroduce them. Likewise confirm `testTutorContext` / `FakeMailer` helper signatures from `QueueMasteryCalibrationRoutesTest`. The cookie can also be set via `install(HttpCookies)` + `cookie("jarvis_session", sid)` as that test does; either form works.

### 7b. Run it — it FAILS

- [ ] Run:
  ```
  ./gradlew test --tests "jarvis.web.TeachingServeRouteTest"
  ```
  Expected: all 200/404 assertions fail because the route does not exist yet (Ktor returns 404 for the unmapped path — but the faithful-serve test fails on the missing body, and the 401 test fails because an unmapped path returns 404 not 401). Confirm the faithful-serve test is red.

### 7c. Minimal impl — add the reply DTO + the route

- [ ] In `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt`, add the reply DTO near the other `@Serializable` reply types (after `ApiCalibrationBucket`, around line 113):
  ```kotlin
  /**
   * Grounded-teaching serve reply (council 1780928193). Served by GET /api/v1/teaching/{kcId} ONLY
   * when the KC resolves `faithful` + has no OPEN dispute. Both prose fields are AUTHORED and have
   * passed the faithful-check pipeline, so `provenance` is stamped {authored, faithful-checked=true}
   * — the type-honest counterpart to the generated DrillContentDto.provenance marker. A field is null
   * when the author did not write it. FAIL-LOUD: non-faithful / disputed KCs are 404 (never served).
   */
  @Serializable
  data class ApiTeachingReply(
      val kcId: String,
      val name_ro: String,
      val explanation_ro: String?,
      val worked_example_ro: String?,
      val provenance: jarvis.tutor.DrillProvenanceDto,
  )
  ```
- [ ] Inside `fun Route.installQueueMasteryCalibrationRoutes()`, add the route after the existing `get("/api/v1/calibration") { … }` block (keep it inside the function body):
  ```kotlin
      // ── GET /api/v1/teaching/{kcId} ─────────────────────────────────────────────────────────────
      // The authored grounded teaching (plain-words explanation + worked example) for ONE KC. Served
      // ONLY when the KC resolves `faithful` (VerifyAdmin.resolveStatus folds the D8 staleness gate)
      // AND has no OPEN report_wrong (D-RF2 always-on serve refusal). Non-faithful / disputed / unknown
      // ⇒ 404 (OMIT, never a degraded payload) — the SAME gate queue/today uses (lines 143-148).
      get("/api/v1/teaching/{kcId}") {
          requireUser { _ ->
              val ctx = call.application.attributes.getOrNull(TutorContextKey)
                  ?: run { call.respond(HttpStatusCode.InternalServerError, "TutorContext missing"); return@requireUser }
              val db = ctx.db
              val kcId = call.parameters["kcId"]?.takeIf { it.isNotBlank() }
                  ?: run { call.respond(HttpStatusCode.BadRequest, "kcId required"); return@requireUser }
              val repo = ContentRepo(groupThreeContentDir())
              val kc = VerifyAdmin.findKc(repo, kcId)
                  ?: run { call.respond(HttpStatusCode.NotFound, """{"error":"unknown kc"}"""); return@requireUser }

              // The faithful gate, mirrored from queue/today (line 143-148):
              val resolved = VerifyAdmin.resolveStatus(db, kc.id, kc)
              if (resolved != VerificationStatus.faithful) {
                  call.respond(HttpStatusCode.NotFound, """{"error":"not faithful"}"""); return@requireUser
              }
              if (ReportWrongQuery.hasOpenReportWrong(db, kc.id)) {
                  call.respond(HttpStatusCode.NotFound, """{"error":"disputed"}"""); return@requireUser
              }

              call.respond(
                  HttpStatusCode.OK,
                  ApiTeachingReply(
                      kcId = kc.id,
                      name_ro = kc.name_ro,
                      explanation_ro = kc.explanation_ro?.takeIf { it.isNotBlank() },
                      worked_example_ro = kc.worked_example_ro?.takeIf { it.isNotBlank() },
                      // Served only behind the faithful gate above ⇒ honest authored+checked provenance.
                      provenance = jarvis.tutor.DrillProvenanceDto(type = "authored", hasBeenFaithfulChecked = true),
                  ),
              )
          }
      }
  ```
- [ ] Add the imports `VerifyAdmin` is in the same `jarvis.web` package (no import needed); ensure `import jarvis.content.ContentRepo` (already present, line 8) and `import jarvis.tutor.VerificationStatus` (already present, line 21) and `import jarvis.tutor.verify.ReportWrongQuery` (already present, line 22) are in scope. No new import is required for `DrillProvenanceDto` because it is fully qualified inline.

### 7d. Run it — it PASSES

- [ ] Re-run:
  ```
  ./gradlew test --tests "jarvis.web.TeachingServeRouteTest"
  ```
  Expected: all five tests green. If the faithful-serve test 404s, the most likely cause is the D8 hash gate: confirm `seedB8` stamps `contentHash = ContentReconcile.kcContentHash(kc)` over the SAME loaded KC and a non-null `sourceSpanHash` — exactly as the queue test does.

### 7e. Commit

- [ ] Commit:
  ```
  git add src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt src/test/kotlin/jarvis/web/TeachingServeRouteTest.kt
  git commit -m "feat(serve): GET /api/v1/teaching/{kcId} — faithful-gated grounded teaching

Serves authored explanation_ro + worked_example_ro ONLY behind the same
faithful gate queue/today uses (resolveStatus D8 staleness + D-RF2 open-
dispute refusal). Non-faithful / disputed / unknown ⇒ 404 (FAIL-LOUD omit).
Reply carries {authored, faithful-checked} provenance.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 8 — Author the grounded teaching on the REAL pa-kc-001 corpus file

Now wire the real content (not just a test fixture), grounding it in the lecturer quote at `pa-lecture-01` page 4, span 1184-1353 (verified present in `content/PA/_sources/pa-lecture-01.md`).

**File (content):** `content/PA/kcs/pa-kc-001.yaml`
**File (test):** `src/test/kotlin/jarvis/content/Pa001TeachingClaimsTest.kt` (new)

### 8a. Write the failing test (the corpus must emit the two prose claims)

- [ ] Create `src/test/kotlin/jarvis/content/Pa001TeachingClaimsTest.kt`. It loads the REAL corpus via `ContentRepo` over the repo's `content/` dir and asserts pa-kc-001 now emits both teaching claims, anchored on a span-bearing ref:
  ```kotlin
  package jarvis.content

  import jarvis.tutor.verify.ClaimKind
  import java.nio.file.Path
  import kotlin.test.Test
  import kotlin.test.assertNotNull
  import kotlin.test.assertNull
  import kotlin.test.assertTrue

  /**
   * Grounded-teaching layer (Task 8) — the REAL pa-kc-001 corpus file authors explanation_ro +
   * worked_example_ro, so claimsFor emits one EXPLANATION + one WORKED_EXAMPLE claim, each PROSE
   * (invariant null) anchored on the KC's first span-bearing source ref.
   */
  class Pa001TeachingClaimsTest {

      private fun contentRoot(): Path = Path.of("content") // repo root (gradle test cwd)

      @Test fun `pa-kc-001 emits the two authored teaching claims, anchored and prose`() {
          val kc = ContentRepo(contentRoot()).loadSubject("PA").kcs.single { it.id == "pa-kc-001" }
          assertNotNull(kc.explanation_ro, "pa-kc-001 must author explanation_ro")
          assertNotNull(kc.worked_example_ro, "pa-kc-001 must author worked_example_ro")

          val claims = ContentReconcile.claimsFor(kc)
          val expl = claims.single { it.kind == ClaimKind.EXPLANATION }
          val ex = claims.single { it.kind == ClaimKind.WORKED_EXAMPLE }

          assertNull(expl.invariant, "EXPLANATION is prose")
          assertNull(ex.invariant, "WORKED_EXAMPLE is prose")
          assertNotNull(expl.source?.span, "anchored on the first span-bearing ref")
          assertNotNull(ex.source?.span, "anchored on the first span-bearing ref")
          assertTrue(expl.content.isNotBlank())
          assertTrue(ex.content.isNotBlank())
      }
  }
  ```

### 8b. Run it — it FAILS

- [ ] Run:
  ```
  ./gradlew test --tests "jarvis.content.Pa001TeachingClaimsTest"
  ```
  Expected: `assertNotNull(kc.explanation_ro)` fails — the real YAML does not author the fields yet.

### 8c. Minimal impl — author the Romanian teaching on pa-kc-001.yaml

- [ ] **First, reorder the `source:` list so the definition quote is the FIRST span-bearing ref.** The real `pa-kc-001.yaml` lists three refs; today the first is `"It does not exists a standard definition…"` (span 811-880) and the actual definition `"An algorithm is a well-ordered collection…"` (span 1184-1353) is SECOND. `claimsFor` anchors every EXPLANATION/WORKED_EXAMPLE on `anchorRef = kc.source.firstOrNull { it.span != null }`, so the teaching claims must anchor on the clause they restate. Move the `start: 1184 / end: 1353` source entry to be the FIRST item in the `source:` list. This is SAFE: DEFINITION claims are content-addressed by quote (reorder-stable — the emitted DEFINITION set is byte-identical), and pa-kc-001 has no `invariant`/`grader_rules`, so reordering does NOT change its existing pre-teaching `content_hash`. After the reorder, `anchorRef` is the 1184-1353 definition quote, matching the comment in the appended block.
- [ ] Then, after the `worked_example_first: true` line and the existing `self_explanation_prompt` / `far_transfer_stem` block, append the two new fields. The Romanian text restates ONLY what the cited lecturer quote (page 4) states — it invents no new subject fact:
  ```yaml
  # ── Grounded-teaching layer (council 1780928193, CLAIM-vs-PRESENTATION) — authored plain-words
  #    explanation + worked example, RESTATING ONLY the page-4 definition quote
  #    ("An algorithm is a well-ordered collection of unambiguous and effectively computable
  #    operations that when executed produces a result and halts in a finite amount of time.").
  #    Each flows through the faithful-check as a prose EXPLANATION / WORKED_EXAMPLE claim, anchored
  #    on this KC's first span-bearing source ref (pa-lecture-01, span 1184-1353). ──
  explanation_ro: >-
    Pe scurt: un algoritm este o colecție bine ordonată de operații care sunt neambigue și efectiv
    calculabile; atunci când este executat, el produce un rezultat și se oprește într-un timp finit.
    Cu alte cuvinte, fiecare pas este clar (nu lasă loc de interpretare), poate fi efectiv dus la
    capăt, iar execuția nu continuă la nesfârșit — se termină după un număr finit de pași.
  worked_example_ro: >-
    Exemplu lucrat: vrem să adunăm două numere a și b. Pasul 1 — citim a; pasul 2 — citim b; pasul 3
    — calculăm s = a + b; pasul 4 — afișăm s și ne oprim. Fiecare pas este neambiguu (știm exact ce
    avem de făcut) și efectiv calculabil (adunarea se poate executa), iar după pasul 4 execuția se
    oprește, deci produce un rezultat în timp finit. Conform definiției din curs, această secvență
    este un algoritm.
  ```

### 8d. Run it — it PASSES

- [ ] Re-run:
  ```
  ./gradlew test --tests "jarvis.content.Pa001TeachingClaimsTest"
  ```
  Expected: green.
- [ ] Sanity re-run the regression that proves no OTHER KC's hash changed (from Task 4) plus the schema test:
  ```
  ./gradlew test --tests "jarvis.content.ContentReconcileTest" --tests "jarvis.content.ContentSchemaTeachingFieldsTest"
  ```
  Expected: green (the other 7 PA YAMLs author no teaching fields ⇒ their `content_hash` is unchanged).

### 8e. Commit

- [ ] Commit:
  ```
  git add content/PA/kcs/pa-kc-001.yaml src/test/kotlin/jarvis/content/Pa001TeachingClaimsTest.kt
  git commit -m "content(PA): author grounded explanation + worked example on pa-kc-001

Romanian plain-words explanation + worked example restating only the page-4
definition quote; emits prose EXPLANATION + WORKED_EXAMPLE claims anchored
on the KC's span-bearing source ref. Only pa-kc-001's content_hash changes.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 9 — ACCEPTANCE: full Kotlin suite green + the CI invariants

This is the final gate. It runs the WHOLE Kotlin suite (not just the touched classes) to prove no regression anywhere, and explicitly re-runs the two CI invariants this plan introduced.

- [ ] Run the full suite:
  ```
  ./gradlew test
  ```
  Expected: `BUILD SUCCESSFUL`. Confirm ZERO new failures vs the Task-0 baseline. If anything that passed in Task 0 is now red, treat it as a regression introduced by this plan and use superpowers:systematic-debugging before proceeding — do NOT claim done.
- [ ] Explicitly re-run the CI invariant + the routing-proof + the serve gate to confirm each is green in isolation (so a future flake is attributable):
  ```
  ./gradlew test --tests "jarvis.tutor.DrillContentProvenanceInvariantTest" --tests "jarvis.tutor.verify.TeachingClaimRoutingTest" --tests "jarvis.web.TeachingServeRouteTest"
  ```
  Expected: all green.
- [ ] Verify the two lock amendments landed and are accurate (deviation-amends-lock self-audit):
  - [ ] `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md` line ~351 `ClaimKind` enum now lists `EXPLANATION, WORKED_EXAMPLE`.
  - [ ] The same doc no longer contains the substring `door/DrillStack.tsx:248` (now `components/DrillStack.tsx:248`):
    ```
    git grep -n "door/DrillStack.tsx" docs/superpowers/plans/2026-06-02-interface-signatures-lock.md
    ```
    Expected: NO output (the stale cite is gone).
  - [ ] The §K and §O AMENDMENT bullets for the grounded-teaching layer + DrillContentDto.provenance are present.
- [ ] Confirm the live DB was never touched (no path under `~/.jarvis/` appears in any diff):
  ```
  git log --oneline -9
  git diff --stat HEAD~8 HEAD
  ```
  Expected: only files under `src/`, `content/PA/kcs/pa-kc-001.yaml`, and the lock doc — nothing under `~/.jarvis/`.
- [ ] Do NOT push or open a PR unless Alex asks (production-on-`main` work; pushing/PR is a separate explicit instruction). The plan is complete when the full suite is green and the lock amendments are verified.

---

## Out of scope (REFERENCED, built by a SEPARATE Phase-5 UI plan)

The frontend rendering is NOT built here. The later Phase-5 UI plan will consume the wire contracts this plan ships:
- `tutor-web/src/components/DrillStack.tsx` — extend the `DrillContent` interface with optional `provenance: { type: "authored" | "generated"; hasBeenFaithfulChecked: boolean }`; render a distinct `data-testid="provenance-badge"` (`data-provenance-type`) showing "AI practice — not checked against your lecture" for `type === "generated"`, so the faithful "matches your lecture" badge can never visually span generated content. Add a `data-testid="grounded-explanation-card"` that renders the new endpoint's `explanation_ro` only when `provenance.hasBeenFaithfulChecked === true`.
- `tutor-web/src/lib/taskPrep.ts` / a new `teaching.ts` client — `getTeaching(kcId): Promise<ApiTeachingReply | null>` against `GET /api/v1/teaching/{kcId}` (404 ⇒ null).
- The Phase-5 plan MUST follow the feature-shipped + interaction-smoke gates (mount-site named, Playwright asserts the new `data-testid`s paint on first load with zero 4xx/5xx).