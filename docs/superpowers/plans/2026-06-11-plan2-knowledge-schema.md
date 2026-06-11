# Plan 2: Knowledge Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement spec §3 — `concept_type`, per-beat language-keyed teaching fields, `pedagogical_instance`/viz-instance content classes, the problem bank, the grade-model registry + exam-schedule seeding, the 828-card `kc_id` backfill mechanism, and the INV-3.2…3.5 invariants — additively, against the live 828-card DB, behind the INV-3.1 backup gate.

**Architecture:** KC teaching content stays in git-tracked YAML (`content/{subject}/kcs/*.yaml`) deserialized into `KnowledgeConcept` — beat fields and `concept_type` are content-schema additions (Kotlin + YAML), NOT DB columns. The DB gains exactly 7 new additive tables (problem bank ×3, grade registry ×2, glossary, exam schedule); zero ALTERs to existing tables. None of the new KC fields feed `kcContentHashOf` claims in this plan, so the 4 live faithful badges are untouched (verification-coverage extension for beat content is gate-chain work, spec §9.2, Plans 4/5).

**Tech Stack:** Kotlin + Exposed + kotlinx-serialization (kaml for YAML), SQLite, JUnit 5, python (db-backup.py), GitHub Actions CI.

---

## Section 0 — Verified ground truth (read before any task; recon 2026-06-11, workflow wf_d6a6f8b7)

### 0.1 Where KC content lives

KC content = per-KC YAML at `content/{subject}/kcs/{id}.yaml`, deserialized to `KnowledgeConcept` (`src/main/kotlin/jarvis/content/ContentSchema.kt:38-102`) via kotlinx-serialization. **There is NO KC table in SQLite** — the DB stores only the trust verdict (`kc_verification_status`, 8 rows: pa-kc-001..006 + 2 fixtures; `{faithful:4, uncertain:4}` after the post-wrap clean re-audit). **Live content_hash literals (read-only probe 2026-06-11, PC and VPS byte-identical — the hash-stability oracle for Tasks 4/11/12):** pa-kc-001 `ca05c671` · 002 `56156563` · 003 `d5363220` · 004 `817aaf44` · 005 `1b67adce` · 006 `6af8f795` · fixtures both `1a004870`. (The stale rehearsal copy `build-review/tmp/pathA-tutor.db` holds DIFFERENT values — never use it as the hash oracle.) Subjects registered in `content/subjects.yaml`: PA, PS, POO, ALO, SO-RC. Only PA has KC files (8). `users.lang` (varchar 8, default `'ro'`, `Users.kt:32`) is the per-user `learner_language` — §3.7 is already satisfied; do not build a new setting.

### 0.2 Current KnowledgeConcept fields (verbatim, the additive base)

```kotlin
// ContentSchema.kt:38-102 — existing fields, all FROZEN-in-place (additive only)
id, subject, name_ro, name_en, cluster, bloom_level, difficulty, time_minutes,
exam_weight, tier, grounding_tier="standard", source: List<SourceRef>, viz_id?,
requires_visual=false, version=1, verification_status="unverified", invariant?,
invariant_statement?, grader_rules: List<String>, stem_template?, phase_plan?,
far_transfer_stem?, self_explanation_prompt?, worked_example_first=false,
explanation_ro?, worked_example_ro?
```

### 0.3 Content-hash contract (LOAD-BEARING — why this plan causes no re-audit cascade)

`content_hash` = `ContentReconcile.kcContentHashOf(claimsFor(kc))` (`ContentReconcile.kt:301-321`, v2 formula FROZEN per lock §I.1). `claimsFor()` (`ContentReconcile.kt:71-140`) emits claims ONLY from: `source[*].quote`, `invariant`, `invariant_statement`, `grader_rules`, `explanation_ro`, `worked_example_ro`. **New fields added to KnowledgeConcept do NOT change any KC's content_hash unless `claimsFor()` is extended — this plan does NOT extend it.** Therefore: adding `concept_type` + `beats` to the 8 PA YAML files leaves all hashes stable; the 4 faithful badges stay lit. Re-admission semantics for beat content (per-field verification, pedagogical_instance method-consistency, concept_type in the faithful-check prompt — spec §3.1/§3.3/§9.2 g2) land with the gate-chain/digestion plans; every task here must keep `claimsFor()` untouched and Task 4/9 asserts hash stability machine-checked.

### 0.4 Migration system (the pattern to copy)

No versioned ledger. `TutorMigration.migrate` (`Migration.kt:94-206`) runs `SchemaUtils.createMissingTablesAndColumns(*ALL_TABLES)` (`ALL_TABLES` at `Migration.kt:42-73`) + idempotent `WHERE col IS NULL` backfills. Adding tables = define `object XTable : Table(...)` + append to `ALL_TABLES`. **INV-3.1 gate** (`MigrationBackupGate.kt`): pending DDL + `fsrs_cards >= 800` ⇒ REFUSES without a same-day `*.manifest.json` in `JARVIS_BACKUP_DIR` with `integrity=PASS`, `restore_drill=PASS`, `fsrs_cards==live`, `schema_hash==liveSchemaHash`. 7 new tables = pending DDL ⇒ the gate FIRES on both live DBs. Backup tool: `python3 tools/db-backup.py <out_dir> [--db PATH]` (manifest sidecar auto-written). Env: `JARVIS_TUTOR_DB` (all CLIs) but `jarvis migrate` reads `JARVIS_DB`/positional — always pass the explicit path (JVM ignores `$HOME`; the 871-vs-0 VPS incident).

### 0.5 Live DB facts (read-only probe 2026-06-11)

- `fsrs_cards`: 828 rows, ALL `source='MANUAL'`, ALL `kc_id=NULL`. `source_ref` pattern `SUBJECT:filename.md`; prefixes: ALO 175, PA 53, POO 78, PS 134, RC 190, SO 198 (RC+SO = subject SO-RC; `subjects.yaml` id is `SO-RC` — backfill logic must map both prefixes).
- **Backfill reality:** PA cards reference `lecture11_*.md` / `lecture9_10_np.md` / `seminar9/10_*.md`; the 6 production PA KCs are all sourced from `pa-lecture-01` only. **Zero confident source-doc overlap exists today** ⇒ honest backfill result = 0 links, all 828 stay NULL (spec §3.6 step 4: "no forced guesses"). INV-3.3's "≥1 card non-null per subject that has KCs" is therefore implemented in its satisfiable form — asserted per subject **where a KC's source doc matches ≥1 card source_ref** (see Task 8/9; deviation documented in §0.8).
- `exam_dates`: 0 rows. Wire + table shape FROZEN (lock §R): `{subject, start_at}`, **one row per (user, subject), PUT upserts** (`SessionPlacementExamRoutes.kt:395-409` — the upsert block).
- `kc_verification_status`: 7 cols (post Plan 1), 8 rows. D9 `VerdictRow` allowlist FROZEN at exactly those 7 fields (`TrustSync.kt:37-45`); new columns would be silently un-synced — this plan adds NONE.
- `users`: owner user exists (single user); seeders must look up its real id at runtime, never hardcode.

### 0.6 Trust/serve seams this plan must not break

- Serve gate ALLOW = `status==faithful && !hasOpenReportWrong` + always-on hash-parity check (`TrustRoutes.kt:223-238`, `VerificationGate.kt:29-31` — signature FROZEN lock §I.2).
- `ApiLessonReply` FROZEN (lock §NEW-L): `prediction_options` must remain `emptyList()` and `definition_ro` null **until a KC options source exists** — beats ARE that source, but populating the endpoint is Plan 3 (BeatOrchestrator); this plan does not touch the lesson route.
- `trustInvariants` gradle task (`build.gradle.kts:224-231`, `TrustInvariantsCli.kt`) = owner-run, read-only, against the REAL DB; checks INV-3.3 / SCHEMA / FLIP / PARITY. Plan 2 extends it (Task 9). Pattern for new checks: append a block to `check()` adding to `failures`.
- CI jobs (`.github/workflows/test.yml`): backend (`gradle :check`), frontend, daemon, backup-tooling, verifier-deps-loud-red. Frontend job red = pre-existing `tutor-shell-api-contract.spec.ts` e2e only — not yours to fix here, do not claim it.

### 0.7 INV-3.5 status

**Does not exist anywhere today** (zero matches for any lock-diff check in CI/gradle/tests). Existing patterns to model it on: `LiveShapeColumnsMigrationTest.kt:40-98` (exact column-set pins), `TrustSyncTest.kt:49-68` (D9 allowlist pin), `MigrationBackupGateTest.kt:53-70` (schema-hash pin). Task 10 creates `SignatureLockPinTest` + runs in `:check` (backend CI job).

### 0.8 Locked decisions + documented deviations (binding for every task)

1. **exam-schedule lock-route:** spec §3.5 says "seed exam_dates with the 12 IA12 rows", but 12 rows include 2–3 per subject, violating lock §R's frozen "one row per (user, subject)". Lock is canonical-on-conflict (spec §13). Resolution: new additive table `exam_schedule_rows` carries all 12 verbatim sweep rows (type/room/precision/source); `exam_dates` additionally gets the 4 primary exam dates (ALO 03.06, SORC 06.06, POO 09.06, PA 10.06) via its existing one-row-per-subject upsert semantics. INV-3.4's count-12 assertion targets `exam_schedule_rows`.
2. **kc_id backfill honesty:** mapping = card `source_ref` filename ⇄ KC `source[*].doc` confident match only (exact doc-stem match after normalization); zero matches today is the EXPECTED, correct result. No similarity scoring, no LLM guessing.
3. **No `claimsFor()` / content-hash / D9 / VerificationGate changes.** Any task whose diff touches `ContentReconcile.claimsFor`, `kcContentHashOf`, `TrustSync.VerdictRow`, or `VerificationGate.gate` is out of bounds — stop and escalate.
4. **`concept_type` verification deferral:** type/content-mismatch flagging in the faithful-check prompt (spec §3.1) is gate-2 coverage extension = Plans 4/5. Here: enum + YAML backfill + load-time validation + invariant only.
5. **Beat-completeness serve-gating:** `KcBeats.isCompleteFor(conceptType, lang)` ships now (Task 2) and the trustInvariants check asserts zero beat-served KCs lack complete beats — vacuously green today (beat serving starts in Plan 3), loud-red the moment Plan 3 serves an incomplete KC.
6. **Romanian-only generation now** (§3.7): schema language-keyed (`Map<String,…>`), pipeline writes only `"ro"` — no speculative multi-language work.
7. **Standing constraints:** never `git add -A` / `git clean` on main (door files untracked); live DB mutations ONLY behind the INV-3.1 gate with a fresh off-box dump; no paid APIs; all learner-facing content RO, code/identifiers EN.

---

## Section 0.9 — Canonical types & DDL (SINGLE SOURCE OF TRUTH — every task copies from here verbatim; divergence = bug)

### A. ConceptType enum — new file `src/main/kotlin/jarvis/content/ConceptType.kt`

```kotlin
package jarvis.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spec §3.1 — drives beat-variant selection (§4.2-4.3) and viz-family selection (§5.2).
 * Wire/YAML literals are the spec's hyphenated strings, FROZEN.
 */
@Serializable
enum class ConceptType {
    @SerialName("procedure") PROCEDURE,
    @SerialName("proof") PROOF,
    @SerialName("definition-taxonomy") DEFINITION_TAXONOMY,
    @SerialName("code-trace") CODE_TRACE,
    @SerialName("timing") TIMING,
    @SerialName("probabilistic") PROBABILISTIC,
    @SerialName("comparison") COMPARISON,
    @SerialName("formula-application") FORMULA_APPLICATION;

    companion object {
        /** YAML literal -> enum; null for unknown (validator turns unknown into a load error). */
        fun fromWire(s: String): ConceptType? = entries.firstOrNull { wireOf(it) == s }
        fun wireOf(t: ConceptType): String = when (t) {
            PROCEDURE -> "procedure"; PROOF -> "proof"
            DEFINITION_TAXONOMY -> "definition-taxonomy"; CODE_TRACE -> "code-trace"
            TIMING -> "timing"; PROBABILISTIC -> "probabilistic"
            COMPARISON -> "comparison"; FORMULA_APPLICATION -> "formula-application"
        }
    }
}
```

### B. Beat field classes — new file `src/main/kotlin/jarvis/content/KcBeats.kt`

Spec §3.2 minimum fields per beat, §4.3 numerical skeleton variant. All teaching strings are in the language of the enclosing map key (RO today). Field names EN (code), content RO (data).

```kotlin
package jarvis.content

import kotlinx.serialization.Serializable

/** One language version of the 5-beat teaching content (spec §3.2). Keyed by language in KnowledgeConcept.beats. */
@Serializable
data class KcBeats(
    val predict: BeatPredict? = null,
    val attempt: BeatAttempt? = null,
    val reveal: BeatReveal? = null,
    val name: BeatName? = null,
    val check: BeatCheck? = null,
) {
    /**
     * INV-3.2 structural minimum: beats ①②③⑤ present + each beat's own minimum fields,
     * with the §4.3 numerical-variant requirement for skeleton concept types.
     * Beat ④ (name) is optional at the schema level (STANDARD plan omits it, spec §4.5).
     */
    fun isCompleteFor(conceptType: ConceptType): Boolean {
        val p = predict ?: return false
        val a = attempt ?: return false
        val r = reveal ?: return false
        val c = check ?: return false
        if (p.prompt.isBlank() || p.options.size !in 3..4 || p.options.any { it.callback.isBlank() }) return false
        if (p.options.none { it.correct }) return false
        val numerical = conceptType in setOf(
            ConceptType.PROCEDURE, ConceptType.FORMULA_APPLICATION, ConceptType.PROBABILISTIC,
        )
        if (a.statement.isBlank() || a.feedback_correct.isBlank()) return false
        if (numerical) {
            if (a.skeleton_rows.isEmpty() || a.trace_steps.isEmpty()) return false
        } else {
            if (a.choices.isEmpty() || a.choices.any { it.feedback.isBlank() }) return false
        }
        if (r.steps.isEmpty() || r.steps.any { it.text.isBlank() || it.callout.isBlank() }) return false
        if (c.item_stem.isBlank() || !c.hasGradingData()) return false
        return true
    }
}

@Serializable
data class PredictOption(
    val text: String,
    /** Echoed at reveal: "you predicted X — here is where that holds/breaks" (§3.2 ①). */
    val callback: String,
    val correct: Boolean = false,
)

@Serializable
data class BeatPredict(
    val prompt: String,
    val options: List<PredictOption>, // 3-4
)

@Serializable
data class AttemptChoice(
    val text: String,
    val correct: Boolean,
    /** Both-path feedback — wrong-path text TEACHES, never just "incorrect" (§3.2 ②). */
    val feedback: String,
)

/** §4.3: one named skeleton row; row count is INSTANCE DATA, never a hardcoded 4. */
@Serializable
data class SkeletonRow(
    val label: String,
    /** The formula-line revealed for this row; null for structural rows. */
    val formula: String? = null,
    /** The sign/decision step gets its own named row (§4.3) — mark it. */
    val is_decision_row: Boolean = false,
)

@Serializable
data class TraceStep(
    val row_index: Int,
    val value: String,
    val callout: String? = null,
)

@Serializable
data class BeatAttempt(
    val statement: String,
    /** Choice variant (non-numerical types). */
    val choices: List<AttemptChoice> = emptyList(),
    /** Numerical variant (§4.3): the skeleton + per-click trace. */
    val skeleton_rows: List<SkeletonRow> = emptyList(),
    val trace_steps: List<TraceStep> = emptyList(),
    /** Free-input schema for the numerical variant (JSON-schema-ish string); null for choice variant. */
    val input_schema: String? = null,
    val feedback_correct: String,
)

@Serializable
data class RevealStep(
    val text: String,
    /** Explanation FUSED to the step — a detached paragraph is a contract violation (§3.2 ③, §5.3). */
    val callout: String,
)

/** §3.2 ③ figure binding: family ID + reference to a typed viz instance (content row, see VizInstance). */
@Serializable
data class FigureBinding(
    val family_id: String,
    val instance_id: String,
)

@Serializable
data class BeatReveal(
    val steps: List<RevealStep>,
    val figure: FigureBinding? = null,
)

@Serializable
data class BeatName(
    val definition: String,
    val invariant_statement: String,
    val why_matters: String,
)

@Serializable
data class BeatCheck(
    /** Different-instance item — same concept, different surface values (§3.2 ⑤). */
    val item_stem: String,
    val choices: List<AttemptChoice> = emptyList(),
    val numeric_answer: String? = null,
    val numeric_tolerance: Double? = null,
) {
    fun hasGradingData(): Boolean =
        choices.any { it.correct } || numeric_answer != null
}
```

### C. KnowledgeConcept additions — modify `src/main/kotlin/jarvis/content/ContentSchema.kt` (ADDITIVE: two new fields at the end of the data class, nothing else moves)

```kotlin
    // ===== Plan 2 (spec §3.1/§3.2) — ADDITIVE; neither field feeds claimsFor()/content_hash =====
    /** Spec §3.1 — wire literal, validated against ConceptType.fromWire at load. Required for every real KC (INV-3.2). */
    val concept_type: String? = null,
    /** Spec §3.2/§3.7 — language-keyed beat content; key = language code ("ro"). Empty for pre-digestion KCs. */
    val beats: Map<String, KcBeats> = emptyMap(),
```

### D. PedagogicalInstance + VizInstance — new file `src/main/kotlin/jarvis/content/VizInstance.kt`

Content rows live at `content/{subject}/viz/{id}.yaml` (instances are content; content requires a source-traceable method — spec §3.3/§5.5). Plan 2 ships schema + loader only; rendering/trace-match = Plan 3.

```kotlin
package jarvis.content

import kotlinx.serialization.Serializable

/**
 * Spec §3.3 — a generated example (fresh numbers / new tiny graph). EXEMPT from verbatim grounding;
 * verified by method-consistency + family trace-match (gate work, Plans 3-5). The schema carries the
 * hooks those verifiers need: the method anchor and the reference execution.
 */
@Serializable
data class PedagogicalInstance(
    /** KC whose source-taught method this instance must be solved by (method-consistency anchor). */
    val method_kc_id: String,
    /**
     * Family-typed instance payload (nodes/edges/cells/labels/step-deltas/callouts — §5.3) as a JSON
     * string. STORED AS STRING, not JsonElement: kaml cannot bind kotlinx JsonElement (its serializer
     * is Json-format-only); instances are machine-authored (digestion), so string-JSON costs nothing.
     * The loader MUST parse-validate it at load time — malformed JSON = load error naming the instance.
     * Typed per-family payload schemas land with the family registry (Plan 3).
     */
    val data_json: String = "{}",
) {
    /** Parsed view for consumers/validators; throws on malformed JSON. */
    fun dataElement(): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.Json.parseToJsonElement(data_json)
}

/** Spec §5.3/§5.5 — one typed figure instance: (family_id, instance_data), nothing else. */
@Serializable
data class VizInstance(
    val id: String,
    val subject: String,
    val family_id: String, // one of the ~6 families (§5.2); registry lands in Plan 3
    val language: String = "ro",
    val instance: PedagogicalInstance,
)
```

### E. The 7 new DB tables — new file `src/main/kotlin/jarvis/tutor/Plan2Tables.kt` (+ register ALL in `Migration.kt` ALL_TABLES)

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** Spec §3.4 — problems are first-class rows, not KC appendages. */
object ProblemsTable : Table("problems") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    /** The problem family, e.g. "Dijkstra on weighted digraph, find shortest path + predecessor table". */
    val archetype = varchar("archetype", 256)
    /** Language-keyed statement: JSON map lang -> statement text. */
    val statementJson = text("statement_json")
    /** Re-rollable values (graph weights, array contents, lambda...) — JSON; null = fixed instance. */
    val parameterSlotsJson = text("parameter_slots_json").nullable()
    /** Whether the source contains a worked solution (R-SOLPRESENT). */
    val solutionPresent = bool("solution_present").default(false)
    val solutionJson = text("solution_json").nullable()
    /** R-LANG: the exam-language constraint id: "alk" | "r" | "cpp" | "bash" | "posix-c" | null. */
    val examLanguage = varchar("exam_language", 32).nullable()
    /** R-LANG details, e.g. banned-STL/banned-function list for POO — JSON; null = none. */
    val examLanguageConstraintsJson = text("exam_language_constraints_json").nullable()
    /** R-DATAFILES: CSV/txt deps the problem names — JSON list of {name, bundled: bool}; null = none. */
    val dataFilesJson = text("data_files_json").nullable()
    val sourceDoc = varchar("source_doc", 256).nullable()
    val sourcePage = integer("source_page").nullable()
    /** Provenance tier (§2.5); null until digestion stamps it. */
    val provenance = varchar("provenance", 24).nullable()
    /** §6.2.4 — honest label for items not derived from a real past paper. */
    val syntheticTag = bool("synthetic_tag").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, subject, archetype) }
}

/** Spec §3.4 rubric rows — G1..Gn, AP/WP, all-or-nothing, penalty rules. Mirrors real grading-doc structure. */
object ProblemRubricItemsTable : Table("problem_rubric_items") {
    val id = varchar("id", 64)
    val problemId = varchar("problem_id", 64).references(ProblemsTable.id)
    /** Rubric row label, "G1".."Gn". */
    val label = varchar("label", 16)
    val points = double("points")
    /** "AP" (answer-points) | "WP" (working-points). */
    val kind = varchar("kind", 4)
    val allOrNothing = bool("all_or_nothing").default(false)
    /** e.g. wrong sign penalized once, not per-line — JSON; null = none. */
    val penaltyRulesJson = text("penalty_rules_json").nullable()
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
    init { index(false, problemId) }
}

/** Spec §3.4 — many-to-many; mastery writes fan out across links. */
object ProblemKcLinksTable : Table("problem_kc_links") {
    val problemId = varchar("problem_id", 64).references(ProblemsTable.id)
    val kcId = varchar("kc_id", 64)
    override val primaryKey = PrimaryKey(problemId, kcId)
}

/** Spec §3.5 R-GRADEMODEL — one verified row per subject per model variant; seeded ONLY from the 2026-06-11 sweep. */
object GradingModelsTable : Table("grading_models") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    /** Variant discriminator, e.g. "live-page" vs "fisa-2024-25-ro" vs "fisa-2025-26-en"; null = sole model. */
    val variant = varchar("variant", 64).nullable()
    /** The model the dashboard/scheduler consumes; at most one true per subject. */
    val isPrimary = bool("is_primary").default(false)
    /** Verbatim formula from the source, e.g. "Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris". */
    val formula = text("formula")
    val maxTotal = double("max_total").nullable()
    /** Pass conditions — JSON, e.g. {"all":[{"component":"exam","min":3,"scale":10},{"total_min":360}]}. */
    val passRuleJson = text("pass_rule_json")
    /** Curve description — JSON; null = unknown (a gap, not a guess). */
    val curveJson = text("curve_json").nullable()
    /** "official-site" | "corpus-evidence" (spec §3.5 evidence tier). */
    val evidenceTier = varchar("evidence_tier", 24)
    val sourceUrl = varchar("source_url", 512)
    /** Errata/gap annotations carried from the sweep — JSON list of strings. */
    val notesJson = text("notes_json").nullable()
    /** Anchor into the sweep doc, e.g. "verified-grade-models-exam-schedule.md#alo". */
    val sweepRef = varchar("sweep_ref", 256)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("grading_models_subject_variant", subject, variant) }
}

/** Spec §3.5 — per-component rows under a grading model. */
object GradeComponentsTable : Table("grade_components") {
    val id = varchar("id", 64)
    val modelId = varchar("model_id", 64).references(GradingModelsTable.id)
    val name = varchar("name", 128)
    val maxPoints = double("max_points").nullable()
    val weight = double("weight").nullable()
    /** Per-component minimum gate — JSON; null = none stated. */
    val minGateJson = text("min_gate_json").nullable()
    /** "yes" | "no" | "unknown" — never guess; unknown is honest (G1/G9). */
    val reexamPolicy = varchar("reexam_policy", 12).nullable()
    val evidenceTier = varchar("evidence_tier", 24)
    val sourceUrl = varchar("source_url", 512).nullable()
    /** Free detail (schedule week, duration, format facts) — JSON. */
    val detailJson = text("detail_json").nullable()
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
    init { index(false, modelId) }
}

/** Spec §3.5 — per-subject EN<->RO term pairs; language validator exemption list (§8.2). */
object GlossaryTermsTable : Table("glossary_terms") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    val termEn = varchar("term_en", 256)
    val termRo = varchar("term_ro", 256)
    val sourceDoc = varchar("source_doc", 256).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("glossary_subject_term_en", subject, termEn) }
}

/**
 * Spec §3.5 exam_dates seeding, LOCK-ROUTED (Section 0.8 #1): the 12 verbatim IA12 sweep rows live HERE;
 * the frozen exam_dates table keeps its one-row-per-(user,subject) contract untouched.
 */
object ExamScheduleRowsTable : Table("exam_schedule_rows") {
    val id = varchar("id", 64)
    /** Tutor subject code (ALO/SORC/POO/PA) or the raw discipline name when unmapped (EF, Pedagogie 1, Reverse Engineering — G13: NEVER map RE to SORC). */
    val subject = varchar("subject", 64)
    val rawDiscipline = varchar("raw_discipline", 128)
    /** "examen" | "restanta" | "test-practic" | "examen-facultativ". */
    val examType = varchar("exam_type", 24)
    val startAt = timestamp("start_at")
    val endAt = timestamp("end_at").nullable()
    val room = varchar("room", 128).nullable()
    /** "exact" | "vague" — dates may be vague ("session week"); scheduler consumes both (§3.5/§7.4). */
    val datePrecision = varchar("date_precision", 12)
    /** Trace to the verified sweep (INV-3.4). */
    val sourceRef = varchar("source_ref", 256)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, subject, startAt) }
}
```

### F. Seed data constants (verbatim from `docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md` — the ONLY permitted source; the taxonomy doc is NEVER read by seeders)

**Grade models (8 rows):**

| id | subject | variant | primary | tier | source URL |
|---|---|---|---|---|---|
| gm-alo | ALO | null | true | official-site | https://edu.info.uaic.ro/algebra-liniara/ |
| gm-ps-live | PS | live-page | true | official-site | https://edu.info.uaic.ro/probabilitati-si-statistica/ |
| gm-ps-fisa | PS | fisa | false | official-site | (fisa PDF URL, §0.9F notes) |
| gm-poo | POO | null | true | official-site | https://gdt050579.github.io/poo_course_fii/administrative.html |
| gm-pa-2425 | PA | fisa-2024-25-ro | false | official-site | (fisa 2024-25 RO PDF URL) |
| gm-pa-2526 | PA | fisa-2025-26-en | false | official-site | (fisa 2025-26 EN PDF URL) |
| gm-sorc | SORC | null | true | official-site | https://profs.info.uaic.ro/georgiana.calancea/so-rc-evaluation.html |
| (components carry corpus-evidence tier rows where flagged: PA per-test "35 pts"/Alk/retake = corpus-evidence; SORC T.SO = corpus-evidence) | | | | | |

Key facts the seeder encodes (Task 7 carries the full literal Kotlin seed list — copy values EXACTLY from the sweep doc):
- **ALO:** formula verbatim `Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris`; maxTotal 800; pass = exam ≥3/10 AND total ≥360; Gaussian curve over passers (10%/20%/25%/25%/15%/5% → 10/9/8/7/6/5); components: lab Teme T1–T5 = 50/60/60/60/70 (early-submission +≤5/temă, late −50%), seminar test wk7 max 100 (10×nota), exam max 400 (40×nota); reexamPolicy unknown (G1).
- **PS (live-page, PRIMARY):** Verificare, NO re-exam on any component; seminars 60 (6 × 10-pt 15-min mini-tests) + labs 60 (Teme A–D 20 + class exercises 20 + final-week 20-min stats test 20); gates BOTH buckets ≥30/60; curve unknown (G6). **PS (fisa, non-primary):** Test teoretic 50 + Temă lab 17 + Test 17 + Verificare practică 16, all "reexaminare: Nu"; mapping to live buckets unpublished (G4 — store both, never reconcile by guessing).
- **POO:** T1 (wk8) 30 + T2 (wk14/15) 30 + final 30 + lab activity ≤10; pass = total ≥45 ONLY (E13/E14: no ≥20-combined gate, no attendance gate — EXCLUDED); ECTS-percentile scale noted in curveJson.
- **PA:** 2024-25 RO = teste scrise 70% + seminar 30% (10% prezență + 20% activitate); 2025-26 EN = 65% continuous (course block 32.5 + seminar block 32.5) + 35% final written; pass ≥45/100 both; BOTH stored, neither primary, notesJson flags `unreconciled-G11`; "Alk mandatory" / "2×35 pts" / retake-format = corpus-evidence component rows (E10–E12).
- **SORC:** `NF = 0.1*SO1 + 0.3*SO2 + 0.1*SO3 + 0.1*RC1 + 0.4*RC2`; pass = RC2 ≥5 AND NF ≥5; RC2 retakeable; SO1/SO2/SO3 = gap G7 (components stored with detailJson `{"definition":"unknown","gap":"G7"}`, NEVER guessed); T.SO claims = corpus-evidence (E9).

**Exam schedule (12 rows, all datePrecision="exact", sourceRef=`docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule`):**

| # | subject | rawDiscipline | examType | start (Europe/Bucharest) | end | room |
|---|---|---|---|---|---|---|
| 1 | ALO | Algebră liniară și optimizare | examen | 2026-06-03T16:00 | 18:00 | C2 |
| 2 | SORC | Sisteme de operare și rețele de calculatoare | examen | 2026-06-06T11:00 | 13:00 | C2 |
| 3 | POO | Programare orientată-obiect | examen | 2026-06-09T08:00 | 10:00 | C2, C210, C309, C412, C413 |
| 4 | Educație fizică | Educație fizică | examen | 2026-06-09T16:00 | 18:00 | Teren Sport |
| 5 | PA | Proiectarea algoritmilor | examen | 2026-06-10T12:00 | 14:00 | C112, C2, C308, C309 |
| 6 | POO | Programare orientată-obiect | test-practic | 2026-06-16T08:00 | 12:00 | C210, C308, C309, C401, C403, C405, C409, C411, C412, C413 |
| 7 | Pedagogie 1 | Pedagogie 1 | examen-facultativ | 2026-06-18T09:00 | 11:00 | C2 |
| 8 | SORC | Sisteme de operare și rețele de calculatoare | restanta | 2026-06-20T10:00 | 12:00 | C2 |
| 9 | ALO | Algebră liniară și optimizare | restanta | 2026-06-22T12:00 | 14:00 | C2 |
| 10 | Reverse Engineering | Reverse Engineering | restanta | 2026-06-22T19:00 | 20:00 | C309 |
| 11 | PA | Proiectarea algoritmilor | restanta | 2026-06-23T10:00 | 12:00 | C112, C2, C308, C309 |
| 12 | POO | Programare orientată-obiect | restanta | 2026-06-25T18:00 | 20:00 | C2, C308, C309 |

Row 10's subject stays the raw string `Reverse Engineering` (G13 — identity unconfirmed, NEVER seeded as SORC/RC). PS: zero rows, correct (Verificare). `exam_dates` additionally gets 4 rows via its frozen upsert: (ALO, 2026-06-03T16:00), (SORC, 2026-06-06T11:00), (POO, 2026-06-09T08:00), (PA, 2026-06-10T12:00) — timestamps stored UTC (Europe/Bucharest is UTC+3 in June: 16:00 EEST = 13:00Z).

**concept_type backfill (Task 4) — the 8 PA KCs.** Dual-agent independent classification with agreement requirement; the plan PRE-REGISTERS the expected assignments so reviewers diff against something (disagreement with these = escalate to PM, do not silently pick):

| KC | expected concept_type | basis |
|---|---|---|
| pa-kc-001 Noțiunea de algoritm | definition-taxonomy | definitional KC, bloom=understand |
| pa-kc-002 Problemă/model/pereche | definition-taxonomy | definitional |
| pa-kc-003 Moduri de descriere | comparison | contrasts description modes |
| pa-kc-004 Necesitatea formalizării | definition-taxonomy | conceptual/definitional |
| pa-kc-005 Dimensiunea valorilor | formula-application | bloom=apply, numeric invariant `1+1+1=3`, grounding strict |
| pa-kc-006 Costul de timp | formula-application | bloom=apply, numeric invariant `t+t+t=3*t` |
| pa-kc-fixture-recursion | code-trace | recursion-tree viz fixture |
| pa-kc-fixture-compute | procedure | compute fixture |

---

## Task list (Tasks 0–13 below; full TDD steps per task)

> **Provenance note (2026-06-12):** the sweep doc `docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md` was given explicit HTML anchors (`#alo` `#ps` `#poo` `#pa` `#sorc` `#ia12-schedule`) so every `sweepRef`/`sourceRef` fragment used by the Task 7 seeder RESOLVES. The modified sweep doc must be committed with Task 7 (it is currently a working-tree modification).

| # | Task | Files (primary) |
|---|---|---|
| 0 | Preconditions probe (read-only, no commit) | — |
| 1 | ConceptType enum + KnowledgeConcept.concept_type + load validation | ConceptType.kt, ContentSchema.kt, ContentValidator.kt |
| 2 | Beat field classes + KnowledgeConcept.beats + completeness validator | KcBeats.kt, ContentSchema.kt |
| 3 | PedagogicalInstance + VizInstance schema + content loader | VizInstance.kt, ContentRepo.kt |
| 4 | concept_type YAML backfill (8 PA KCs) + hash-stability proof + validator tightening | content/PA/kcs/*.yaml |
| 5 | Problem-bank tables (problems, rubric items, kc links) + migration tests | Plan2Tables.kt, Migration.kt |
| 6 | Registry + glossary + schedule tables + migration tests | Plan2Tables.kt, Migration.kt |
| 7 | Seeders (grade models, schedule rows, exam_dates primaries) + idempotency tests | Plan2Seed.kt |
| 8 | kc_id backfill mechanism (confident-match only) + tests | KcIdBackfill.kt |
| 9 | trustInvariants extensions: INV-3.2 / INV-3.3-satisfiable / INV-3.4 + new-table SCHEMA pins | TrustInvariantsCli.kt |
| 10 | INV-3.5 SignatureLockPinTest + CI | SignatureLockPinTest.kt, test.yml |
| 11 | LIVE PC apply (rehearsal on copy → gated real apply → seed → invariants) | — (operational) |
| 12 | VPS apply (backup → deploy → migrate → seed → invariants) | — (operational) |
| 13 | Whole-suite gate + plan-index/docs update | plan index |

## Task 0 — Preconditions probe (read-only, no commit)

Verify the working tree, the live DB, the corpus, and the baseline suite are in the exact state Plan 2 builds on **before** touching anything. Every check below is a command + the expected output + a STOP instruction. This task makes no edits and no commit.

> **Operator note (verified 2026-06-11 read-only):** Plan 1 (the trust-net ON migration) is ALREADY applied to the live DB. `kc_verification_status` therefore has **8 rows + 7 columns**, NOT the "0 rows + 4 columns" some older memory/plan-1-Section-0 text describes. Plan 2 is additive on top of that state. The 6 production verdicts are `{pa-kc-001..004 faithful, pa-kc-005..006 uncertain}` and the 2 fixtures are `uncertain` — `{faithful:4, uncertain:4}`. These 6 production `content_hash` values (`ca05c671 / 56156563 / d5363220 / 817aaf44 / 1b67adce / 6af8f795`) are the hash-stability ground truth Task 4 proves against.

- [ ] **Step 1: Confirm the branch + clean-enough git state.** From repo root `C:\Users\User\jarvis-kotlin`:

```powershell
git rev-parse --abbrev-ref HEAD
git status --short
```

Expected: branch `main`. `git status --short` shows ONLY the known untracked demo/working-tree files (door demos under `src/main/resources/tutor-dist/`, `build-review/` artifacts, `.claude/council-cache/*`, plan docs, `*.json` probe files). There must be **no staged changes** and no modifications to `src/main/kotlin/jarvis/content/*.kt`, `src/test/kotlin/jarvis/content/*.kt`, or any `content/PA/kcs/*.yaml`. **STOP if** any source file under `src/` or any `content/` YAML is already modified/staged — a dirty tree means a prior task did not finish; re-assess before continuing. Do **not** `git add -A`, `git clean`, switch branches, or merge `door-compare` (the untracked door files must stay untouched — Section 0.8 #7).

- [ ] **Step 2: Probe the live DB read-only.** Confirm the 828 cards + the post-Plan-1 verdict shape:

```powershell
python -c "import sqlite3,pathlib; db=(pathlib.Path.home()/'.jarvis'/'tutor.db').resolve(); con=sqlite3.connect(db.as_uri()+'?mode=ro',uri=True); print('cards:', con.execute('SELECT COUNT(*) FROM fsrs_cards').fetchone()[0]); print('kvs rows:', con.execute('SELECT COUNT(*) FROM kc_verification_status').fetchone()[0]); print('kvs cols:', [c[1] for c in con.execute('PRAGMA table_info(kc_verification_status)')])"
```

Expected output (exactly):

```
cards: 828
kvs rows: 8
kvs cols: ['kc_id', 'status', 'last_audit_run_id', 'updated_at', 'content_hash', 'lecture_grounded', 'source_span_hash']
```

**STOP if** `cards != 828` (the irreplaceable corpus changed — do not migrate over it), or `kvs rows != 8`, or the 7 columns differ **as a SET**. (The PRAGMA ORDER above shows `updated_at` at index 3 because the live DB gained the last 3 columns via historical ALTERs; the `KcVerificationStatusTable` declaration order — pinned by Task 10's `SignatureLockPinTest` — puts `content_hash` at index 3. Both are correct; compare sets, not order, or you will raise a false alarm.) A different column SET means Plan 1's state drifted; re-verify against Plan 1 / Section 0 before any Plan 2 work.

- [ ] **Step 3: Confirm the 6 production verdict hashes are the live ground truth** (Task 4 asserts hash stability against exactly these):

```powershell
python -c "import sqlite3,pathlib; db=(pathlib.Path.home()/'.jarvis'/'tutor.db').resolve(); con=sqlite3.connect(db.as_uri()+'?mode=ro',uri=True); [print(r) for r in con.execute(\"SELECT kc_id,status,content_hash FROM kc_verification_status WHERE kc_id LIKE 'pa-kc-00%' ORDER BY kc_id\")]"
```

Expected output (exactly):

```
('pa-kc-001', 'faithful', 'ca05c671')
('pa-kc-002', 'faithful', '56156563')
('pa-kc-003', 'faithful', 'd5363220')
('pa-kc-004', 'faithful', '817aaf44')
('pa-kc-005', 'uncertain', '1b67adce')
('pa-kc-006', 'uncertain', '6af8f795')
```

**STOP if** any hash differs — Task 4's hash-stability proof is keyed to these exact 6 values; a mismatch means the corpus or audit drifted and the Task-4 proof would assert the wrong baseline.

- [ ] **Step 4: Confirm the PA corpus has exactly 8 KC YAML files** (6 production + 2 fixtures):

```powershell
Get-ChildItem content\PA\kcs\*.yaml | Select-Object -ExpandProperty Name
```

Expected (8 files):

```
pa-kc-001.yaml
pa-kc-002.yaml
pa-kc-003.yaml
pa-kc-004.yaml
pa-kc-005.yaml
pa-kc-006.yaml
pa-kc-fixture-compute.yaml
pa-kc-fixture-recursion.yaml
```

**STOP if** the count is not 8 — Task 4 backfills exactly these 8 files; a different set means the corpus changed.

- [ ] **Step 5: Confirm no `viz/` content directory exists for any subject** (Task 3's loader must tolerate the empty case, which is every subject today):

```powershell
Get-ChildItem -Path content -Directory -Recurse -Filter viz -ErrorAction SilentlyContinue
```

Expected: no output (zero `viz/` directories). **STOP if** any subject already has a `content/{subject}/viz/` directory — Task 3 introduces that directory shape; a pre-existing one means another task already ran.

- [ ] **Step 6: Confirm the baseline backend suite is green before changing anything.** Run gradle directly (never pipe through `| tail`/`| head` — review-workflow rule):

```powershell
gradle --no-daemon :check
```

Expected: `BUILD SUCCESSFUL`. **STOP and report if it is not green** — do not build on red. (Note: `:check` includes `validateContent`, which loads the real corpus through `ContentValidator.validate`; Tasks 1 and 4 extend that exact path, so a clean baseline here is the regression net.)

---

## Task 1 — `ConceptType` enum + `KnowledgeConcept.concept_type` + load-time validation

Adds the `concept_type` enum (spec §3.1, drives beat-variant + viz-family selection) and the additive nullable `concept_type` field on `KnowledgeConcept`. Adds a load-time validator: when `concept_type` is non-null it MUST be a valid wire literal (`ConceptType.fromWire` non-null), else a `concept_type_enum` error naming the KC id, the bad literal, and the 8 valid literals. **Null stays ALLOWED in this task** (Task 4 tightens it to required after the YAML backfill). Neither new field feeds `claimsFor()` / `content_hash` (Section 0.3) — do not touch `ContentReconcile`.

**Files:**
- Create: `src/main/kotlin/jarvis/content/ConceptType.kt`
- Create: `src/test/kotlin/jarvis/content/ConceptTypeTest.kt`
- Modify: `src/main/kotlin/jarvis/content/ContentSchema.kt` (one additive field)
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt` (one new check + wire into `validate`)
- Test: `src/test/kotlin/jarvis/content/ConceptTypeValidationTest.kt` (new)

- [ ] **Step 1: Write the failing enum + round-trip tests.** Create `src/test/kotlin/jarvis/content/ConceptTypeTest.kt`:

```kotlin
package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Plan-2 Task 1 — the ConceptType wire mapping (spec §3.1) and the additive nullable
 * KnowledgeConcept.concept_type field. The 8 wire literals are the spec's hyphenated
 * strings, FROZEN; fromWire is total (null for anything not in the set).
 */
class ConceptTypeTest {

    @Test fun `every enum constant round-trips through its wire literal`() {
        for (t in ConceptType.entries) {
            val wire = ConceptType.wireOf(t)
            assertEquals(t, ConceptType.fromWire(wire), "wireOf/fromWire must round-trip for $t")
        }
    }

    @Test fun `the 8 wire literals are exactly the spec set`() {
        assertEquals(
            setOf(
                "procedure", "proof", "definition-taxonomy", "code-trace",
                "timing", "probabilistic", "comparison", "formula-application",
            ),
            ConceptType.entries.map { ConceptType.wireOf(it) }.toSet(),
        )
    }

    @Test fun `fromWire returns null for an unknown literal`() {
        assertNull(ConceptType.fromWire("procedural"))   // close but wrong
        assertNull(ConceptType.fromWire("PROCEDURE"))    // case-sensitive
        assertNull(ConceptType.fromWire(""))
    }

    @Test fun `a KC YAML with concept_type deserializes the wire literal verbatim`() {
        val yaml = """
            id: pa-kc-005
            subject: PA
            name_ro: "Dimensiunea valorilor"
            name_en: "Value size"
            cluster: "Eficiența algoritmilor"
            bloom_level: apply
            difficulty: 3
            time_minutes: 30
            exam_weight: 0.13
            tier: 3
            concept_type: formula-application
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        assertEquals("formula-application", kc.concept_type)
        assertEquals(ConceptType.FORMULA_APPLICATION, ConceptType.fromWire(kc.concept_type!!))
    }

    @Test fun `a KC YAML without concept_type defaults to null`() {
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
        assertNull(kc.concept_type, "legacy YAML ⇒ concept_type defaults null")
    }
}
```

- [ ] **Step 2: Run — expect RED** (compile failure: `ConceptType` unresolved, and `kc.concept_type` unresolved):

```powershell
gradle --no-daemon :compileTestKotlin
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: ConceptType` and `Unresolved reference: concept_type`.

- [ ] **Step 3: Create the enum** — `src/main/kotlin/jarvis/content/ConceptType.kt` (VERBATIM from plan core §0.9A):

```kotlin
package jarvis.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spec §3.1 — drives beat-variant selection (§4.2-4.3) and viz-family selection (§5.2).
 * Wire/YAML literals are the spec's hyphenated strings, FROZEN.
 */
@Serializable
enum class ConceptType {
    @SerialName("procedure") PROCEDURE,
    @SerialName("proof") PROOF,
    @SerialName("definition-taxonomy") DEFINITION_TAXONOMY,
    @SerialName("code-trace") CODE_TRACE,
    @SerialName("timing") TIMING,
    @SerialName("probabilistic") PROBABILISTIC,
    @SerialName("comparison") COMPARISON,
    @SerialName("formula-application") FORMULA_APPLICATION;

    companion object {
        /** YAML literal -> enum; null for unknown (validator turns unknown into a load error). */
        fun fromWire(s: String): ConceptType? = entries.firstOrNull { wireOf(it) == s }
        fun wireOf(t: ConceptType): String = when (t) {
            PROCEDURE -> "procedure"; PROOF -> "proof"
            DEFINITION_TAXONOMY -> "definition-taxonomy"; CODE_TRACE -> "code-trace"
            TIMING -> "timing"; PROBABILISTIC -> "probabilistic"
            COMPARISON -> "comparison"; FORMULA_APPLICATION -> "formula-application"
        }
    }
}
```

- [ ] **Step 4: Add the `concept_type` field to `KnowledgeConcept`.** In `src/main/kotlin/jarvis/content/ContentSchema.kt`, the data class ends at the `worked_example_ro: String? = null,` line followed by `)`. Insert the additive field block immediately after `worked_example_ro` (VERBATIM from plan core §0.9C — `beats` is added in Task 2, so add ONLY `concept_type` here):

Find:

```kotlin
    val worked_example_ro: String? = null,
)
```

Replace with:

```kotlin
    val worked_example_ro: String? = null,
    // ===== Plan 2 (spec §3.1) — ADDITIVE; this field does NOT feed claimsFor()/content_hash =====
    /** Spec §3.1 — wire literal, validated against ConceptType.fromWire at load. Required for every real KC (INV-3.2). */
    val concept_type: String? = null,
)
```

- [ ] **Step 5: Write the failing validation test.** Create `src/test/kotlin/jarvis/content/ConceptTypeValidationTest.kt`:

```kotlin
package jarvis.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-2 Task 1 — load-time validation of concept_type. A non-null concept_type MUST be a valid
 * wire literal (ConceptType.fromWire non-null), else a `concept_type_enum` error. Null is ALLOWED
 * in this task (Task 4 tightens it to required-for-all after the YAML backfill).
 */
class ConceptTypeValidationTest {

    private fun kc(id: String, conceptType: String?) = KnowledgeConcept(
        id = id, subject = "PA", name_ro = "ro-$id", name_en = "en-$id",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = 0.0, tier = 1, source = emptyList(), version = 1,
        concept_type = conceptType,
    )

    private fun loaded(vararg kcs: KnowledgeConcept) =
        LoadedSubject("PA", kcs = kcs.toList(), edges = emptyList(), misconceptions = emptyList())

    @Test fun `a valid wire literal passes`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", "definition-taxonomy")))
        assertTrue(issues.isEmpty(), "$issues")
    }

    @Test fun `null concept_type passes in this task`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", null)))
        assertTrue(issues.isEmpty(), "null is allowed until Task 4 tightens it: $issues")
    }

    @Test fun `an invalid literal is an error naming the KC, the bad value, and the 8 valid literals`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", "procedural")))
        assertEquals(1, issues.size)
        val it = issues.single()
        assertEquals("error", it.severity)
        assertEquals("concept_type_enum", it.rule)
        assertTrue(it.detail.contains("k1"), "names the KC id: ${it.detail}")
        assertTrue(it.detail.contains("procedural"), "names the bad literal: ${it.detail}")
        for (valid in listOf(
            "procedure", "proof", "definition-taxonomy", "code-trace",
            "timing", "probabilistic", "comparison", "formula-application",
        )) {
            assertTrue(it.detail.contains(valid), "lists valid literal '$valid': ${it.detail}")
        }
    }

    @Test fun `the check runs inside validate() over the full subject`() {
        val report = ContentValidator.validate(listOf(loaded(kc("k1", "not-a-type")))) { null }
        assertTrue(
            report.issues.any { it.rule == "concept_type_enum" && it.detail.contains("k1") },
            "validate() must surface the concept_type_enum error: ${report.issues}",
        )
    }
}
```

- [ ] **Step 6: Run — expect RED** (compile failure: `checkConceptTypeEnum` unresolved):

```powershell
gradle --no-daemon :compileTestKotlin
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: checkConceptTypeEnum`.

- [ ] **Step 7: Implement the validator check.** In `src/main/kotlin/jarvis/content/ContentValidator.kt`:

7a. Wire it into the `validate` loop. Find the block of `issues +=` calls inside `for (sub in subjects)` and add the new line after `checkVerificationStatusEnum`:

Find:

```kotlin
            issues += checkVerificationStatusEnum(sub)
            issues += checkTautologicalInvariants(sub)
```

Replace with:

```kotlin
            issues += checkVerificationStatusEnum(sub)
            issues += checkConceptTypeEnum(sub)
            issues += checkTautologicalInvariants(sub)
```

7b. Add the check function. Insert it immediately after the `checkVerificationStatusEnum` function (i.e. directly before the `val AUTHORED_VERIFICATION_STATUSES` declaration):

```kotlin
    /**
     * Plan-2 Task 1 (spec §3.1) — concept_type load-time validation.
     *
     * When a KC's [KnowledgeConcept.concept_type] is non-null it MUST be a valid wire literal
     * (ConceptType.fromWire non-null). Null is ALLOWED here; Task 4 tightens it to required-for-all
     * after the YAML backfill. Localized author/audit check — ZERO serve/sync/hash ripple (concept_type
     * does NOT feed ContentReconcile.claimsFor / kcContentHashOf, plan core §0.3).
     */
    fun checkConceptTypeEnum(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val validLiterals = ConceptType.entries.joinToString(", ") { ConceptType.wireOf(it) }
        for (kc in sub.kcs) {
            val ct = kc.concept_type ?: continue   // null allowed in Task 1; required in Task 4
            if (ConceptType.fromWire(ct) == null) {
                issues += ValidationIssue(
                    severity = "error",
                    rule = "concept_type_enum",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': concept_type='$ct' is not a valid literal " +
                        "(must be one of: $validLiterals)",
                )
            }
        }
        return issues
    }
```

- [ ] **Step 8: Run the new tests — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.content.ConceptTypeTest" --tests "jarvis.content.ConceptTypeValidationTest"
```

Expected: `BUILD SUCCESSFUL`, all tests passed (5 + 4).

- [ ] **Step 9: Run the full backend suite** (the new field + new check touch the corpus-load path that `:check` exercises via `validateContent`; never trust a partial run — review-workflow rule):

```powershell
gradle --no-daemon :check
```

Expected: `BUILD SUCCESSFUL`. (The real PA corpus still validates: all 8 KCs have `concept_type` null today, so `checkConceptTypeEnum` emits nothing — Task 4 backfills them.)

- [ ] **Step 10: Commit** (explicit paths only — never `git add -A`):

```powershell
git add src/main/kotlin/jarvis/content/ConceptType.kt src/main/kotlin/jarvis/content/ContentSchema.kt src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ConceptTypeTest.kt src/test/kotlin/jarvis/content/ConceptTypeValidationTest.kt
git commit -m "feat(content): ConceptType enum + KnowledgeConcept.concept_type + load-time validation (spec 3.1, additive)"
```

---

## Task 2 — Beat field classes + `KnowledgeConcept.beats` + completeness validator

Adds the per-beat teaching-content classes (spec §3.2, §4.3 numerical skeleton variant) and the language-keyed `beats` field on `KnowledgeConcept`. The `KcBeats.isCompleteFor(conceptType)` method (INV-3.2 structural minimum) ships now; it is exercised by unit tests only in this task (beat *serving* starts in Plan 3, locked-decision §0.8 #5). Neither new field feeds `claimsFor()` / `content_hash` — do not touch `ContentReconcile`.

**Files:**
- Create: `src/main/kotlin/jarvis/content/KcBeats.kt`
- Create: `src/test/kotlin/jarvis/content/KcBeatsTest.kt`
- Modify: `src/main/kotlin/jarvis/content/ContentSchema.kt` (one additive field)

- [ ] **Step 1: Write the failing completeness + round-trip tests.** Create `src/test/kotlin/jarvis/content/KcBeatsTest.kt`. This fixture authors a small, realistic Romanian `definition-taxonomy` example for the round-trip and choice-variant cases, and a numerical `formula-application` example for the skeleton cases:

```kotlin
package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-2 Task 2 — KcBeats.isCompleteFor (INV-3.2 structural minimum, spec §3.2/§4.3) and the
 * additive language-keyed KnowledgeConcept.beats field. Beat ④ (name) is OPTIONAL at the schema
 * level (STANDARD plan omits it, §0.8 #5 / §4.5). Numerical concept types (PROCEDURE,
 * FORMULA_APPLICATION, PROBABILISTIC) require the skeleton+trace variant; others require the
 * choice variant.
 */
class KcBeatsTest {

    // A complete CHOICE-variant beats map (for a non-numerical type, e.g. DEFINITION_TAXONOMY).
    private fun completeChoiceBeats() = KcBeats(
        predict = BeatPredict(
            prompt = "Care dintre afirmații descrie corect un algoritm?",
            options = listOf(
                PredictOption("O secvență de operații neambigue care se termină", "Corect — definiția din curs.", correct = true),
                PredictOption("Orice listă de pași, chiar dacă nu se termină", "Greșit — algoritmul trebuie să se oprească în timp finit."),
                PredictOption("Doar o formulă matematică", "Greșit — un algoritm nu este o singură formulă."),
            ),
        ),
        attempt = BeatAttempt(
            statement = "Alege definiția care respectă toate condițiile din curs.",
            choices = listOf(
                AttemptChoice("Operații neambigue, efectiv calculabile, care se termină", true, "Da — toate cele trei condiții sunt prezente."),
                AttemptChoice("O listă de pași care poate continua la nesfârșit", false, "Nu — lipsește terminarea în timp finit."),
            ),
            feedback_correct = "Exact — definiția cere terminare, neambiguitate și calculabilitate.",
        ),
        reveal = BeatReveal(
            steps = listOf(
                RevealStep("Fiecare pas este neambiguu.", "Neambiguu = un singur sens de interpretare."),
                RevealStep("Execuția se oprește în timp finit.", "Finit = după un număr mărginit de pași."),
            ),
        ),
        name = BeatName(
            definition = "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile.",
            invariant_statement = "Execuția produce un rezultat și se oprește în timp finit.",
            why_matters = "Fără terminare nu putem garanta un rezultat — de aici nevoia condiției.",
        ),
        check = BeatCheck(
            item_stem = "Este o rețetă de bucătărie un algoritm? Justifică folosind cele trei condiții.",
            choices = listOf(
                AttemptChoice("Da, dacă pașii sunt neambigui și se termină", true, "Corect."),
                AttemptChoice("Nu, niciodată", false, "Greșit — depinde de condiții."),
            ),
        ),
    )

    // A complete NUMERICAL-variant beats map (skeleton + trace), for FORMULA_APPLICATION.
    private fun completeNumericalBeats() = KcBeats(
        predict = BeatPredict(
            prompt = "Care este dimensiunea uniformă a unui vector cu 3 elemente, fiecare de mărime 1?",
            options = listOf(
                PredictOption("3", "Corect — 1 + 1 + 1 = 3.", correct = true),
                PredictOption("1", "Greșit — mărimea vectorului este suma mărimilor elementelor."),
                PredictOption("Depinde de valori", "Greșit — la măsura uniformă, fiecare element are mărime 1."),
            ),
        ),
        attempt = BeatAttempt(
            statement = "Completează tabelul de mărimi pentru vectorul [a0, a1, a2].",
            skeleton_rows = listOf(
                SkeletonRow("|a0|_unif", formula = "1"),
                SkeletonRow("|a1|_unif", formula = "1"),
                SkeletonRow("|a2|_unif", formula = "1"),
                SkeletonRow("|a|_unif = sumă", formula = "1 + 1 + 1", is_decision_row = true),
            ),
            trace_steps = listOf(
                TraceStep(row_index = 0, value = "1"),
                TraceStep(row_index = 1, value = "1"),
                TraceStep(row_index = 2, value = "1"),
                TraceStep(row_index = 3, value = "3", callout = "Suma mărimilor = 3."),
            ),
            input_schema = """{"type":"number","unit":"unități de mărime"}""",
            feedback_correct = "Exact — la măsura uniformă mărimea totală este 1 + 1 + 1 = 3.",
        ),
        reveal = BeatReveal(
            steps = listOf(
                RevealStep("Fiecare element are mărime uniformă 1.", "Regula |n|_unif = 1 din curs."),
                RevealStep("Mărimea vectorului este suma mărimilor.", "|a|_d = Σ |a_i|_d."),
            ),
        ),
        check = BeatCheck(
            item_stem = "Care este mărimea uniformă a unui vector cu 5 elemente, fiecare de mărime 1?",
            numeric_answer = "5",
            numeric_tolerance = 0.0,
        ),
    )

    @Test fun `complete numerical beats pass for FORMULA_APPLICATION`() {
        assertTrue(completeNumericalBeats().isCompleteFor(ConceptType.FORMULA_APPLICATION))
    }

    @Test fun `numerical type with empty skeleton_rows fails`() {
        val b = completeNumericalBeats().let {
            it.copy(attempt = it.attempt!!.copy(skeleton_rows = emptyList()))
        }
        assertFalse(b.isCompleteFor(ConceptType.FORMULA_APPLICATION))
    }

    @Test fun `complete choice beats pass for DEFINITION_TAXONOMY`() {
        assertTrue(completeChoiceBeats().isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `choice-variant DEFINITION_TAXONOMY with empty choices fails`() {
        val b = completeChoiceBeats().let {
            it.copy(attempt = it.attempt!!.copy(choices = emptyList()))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `predict with only 2 options fails`() {
        val b = completeChoiceBeats().let {
            it.copy(predict = it.predict!!.copy(options = it.predict!!.options.take(2)))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `predict with no correct option fails`() {
        val b = completeChoiceBeats().let {
            it.copy(predict = it.predict!!.copy(options = it.predict!!.options.map { o -> o.copy(correct = false) }))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `check without grading data fails`() {
        val b = completeChoiceBeats().let {
            it.copy(check = it.check!!.copy(choices = emptyList(), numeric_answer = null))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY), "no correct choice and no numeric answer = no grading data")
    }

    @Test fun `beat 4 (name) absent is still complete (STANDARD plan legal)`() {
        val b = completeChoiceBeats().copy(name = null)
        assertTrue(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY), "beat name is optional at the schema level")
    }

    @Test fun `a KC YAML with a full ro beats map round-trips`() {
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
            concept_type: definition-taxonomy
            beats:
              ro:
                predict:
                  prompt: "Care afirmație descrie corect un algoritm?"
                  options:
                    - text: "O secvență de operații neambigue care se termină"
                      callback: "Corect — definiția din curs."
                      correct: true
                    - text: "Orice listă de pași, chiar dacă nu se termină"
                      callback: "Greșit — algoritmul trebuie să se oprească."
                    - text: "Doar o formulă matematică"
                      callback: "Greșit — nu este o singură formulă."
                attempt:
                  statement: "Alege definiția care respectă toate condițiile."
                  choices:
                    - text: "Operații neambigue care se termină"
                      correct: true
                      feedback: "Da — toate condițiile sunt prezente."
                    - text: "Pași care pot continua la nesfârșit"
                      correct: false
                      feedback: "Nu — lipsește terminarea."
                  feedback_correct: "Exact — terminare, neambiguitate, calculabilitate."
                reveal:
                  steps:
                    - text: "Fiecare pas este neambiguu."
                      callout: "Un singur sens de interpretare."
                    - text: "Execuția se oprește în timp finit."
                      callout: "După un număr mărginit de pași."
                name:
                  definition: "Un algoritm este o colecție bine ordonată de operații neambigue."
                  invariant_statement: "Execuția produce un rezultat și se oprește în timp finit."
                  why_matters: "Fără terminare nu putem garanta un rezultat."
                check:
                  item_stem: "Este o rețetă un algoritm? Justifică."
                  choices:
                    - text: "Da, dacă pașii sunt neambigui și se termină"
                      correct: true
                      feedback: "Corect."
                    - text: "Nu, niciodată"
                      correct: false
                      feedback: "Greșit."
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        val ro = kc.beats["ro"]
        assertEquals("Care afirmație descrie corect un algoritm?", ro?.predict?.prompt)
        assertEquals(3, ro?.predict?.options?.size)
        assertTrue(ro!!.predict!!.options.first().correct)
        assertEquals("Corect — definiția din curs.", ro.predict!!.options.first().callback)
        assertEquals(2, ro.attempt?.choices?.size)
        assertEquals("Un algoritm este o colecție bine ordonată de operații neambigue.", ro.name?.definition)
        assertTrue(ro.isCompleteFor(ConceptType.DEFINITION_TAXONOMY), "the authored ro beats are structurally complete")
    }
}
```

- [ ] **Step 2: Run — expect RED** (compile failure: `KcBeats` / `BeatPredict` / etc. unresolved, and `kc.beats` unresolved):

```powershell
gradle --no-daemon :compileTestKotlin
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: KcBeats` and `Unresolved reference: beats`.

- [ ] **Step 3: Create the beat classes** — `src/main/kotlin/jarvis/content/KcBeats.kt` (VERBATIM from plan core §0.9B):

```kotlin
package jarvis.content

import kotlinx.serialization.Serializable

/** One language version of the 5-beat teaching content (spec §3.2). Keyed by language in KnowledgeConcept.beats. */
@Serializable
data class KcBeats(
    val predict: BeatPredict? = null,
    val attempt: BeatAttempt? = null,
    val reveal: BeatReveal? = null,
    val name: BeatName? = null,
    val check: BeatCheck? = null,
) {
    /**
     * INV-3.2 structural minimum: beats ①②③⑤ present + each beat's own minimum fields,
     * with the §4.3 numerical-variant requirement for skeleton concept types.
     * Beat ④ (name) is optional at the schema level (STANDARD plan omits it, spec §4.5).
     */
    fun isCompleteFor(conceptType: ConceptType): Boolean {
        val p = predict ?: return false
        val a = attempt ?: return false
        val r = reveal ?: return false
        val c = check ?: return false
        if (p.prompt.isBlank() || p.options.size !in 3..4 || p.options.any { it.callback.isBlank() }) return false
        if (p.options.none { it.correct }) return false
        val numerical = conceptType in setOf(
            ConceptType.PROCEDURE, ConceptType.FORMULA_APPLICATION, ConceptType.PROBABILISTIC,
        )
        if (a.statement.isBlank() || a.feedback_correct.isBlank()) return false
        if (numerical) {
            if (a.skeleton_rows.isEmpty() || a.trace_steps.isEmpty()) return false
        } else {
            if (a.choices.isEmpty() || a.choices.any { it.feedback.isBlank() }) return false
        }
        if (r.steps.isEmpty() || r.steps.any { it.text.isBlank() || it.callout.isBlank() }) return false
        if (c.item_stem.isBlank() || !c.hasGradingData()) return false
        return true
    }
}

@Serializable
data class PredictOption(
    val text: String,
    /** Echoed at reveal: "you predicted X — here is where that holds/breaks" (§3.2 ①). */
    val callback: String,
    val correct: Boolean = false,
)

@Serializable
data class BeatPredict(
    val prompt: String,
    val options: List<PredictOption>, // 3-4
)

@Serializable
data class AttemptChoice(
    val text: String,
    val correct: Boolean,
    /** Both-path feedback — wrong-path text TEACHES, never just "incorrect" (§3.2 ②). */
    val feedback: String,
)

/** §4.3: one named skeleton row; row count is INSTANCE DATA, never a hardcoded 4. */
@Serializable
data class SkeletonRow(
    val label: String,
    /** The formula-line revealed for this row; null for structural rows. */
    val formula: String? = null,
    /** The sign/decision step gets its own named row (§4.3) — mark it. */
    val is_decision_row: Boolean = false,
)

@Serializable
data class TraceStep(
    val row_index: Int,
    val value: String,
    val callout: String? = null,
)

@Serializable
data class BeatAttempt(
    val statement: String,
    /** Choice variant (non-numerical types). */
    val choices: List<AttemptChoice> = emptyList(),
    /** Numerical variant (§4.3): the skeleton + per-click trace. */
    val skeleton_rows: List<SkeletonRow> = emptyList(),
    val trace_steps: List<TraceStep> = emptyList(),
    /** Free-input schema for the numerical variant (JSON-schema-ish string); null for choice variant. */
    val input_schema: String? = null,
    val feedback_correct: String,
)

@Serializable
data class RevealStep(
    val text: String,
    /** Explanation FUSED to the step — a detached paragraph is a contract violation (§3.2 ③, §5.3). */
    val callout: String,
)

/** §3.2 ③ figure binding: family ID + reference to a typed viz instance (content row, see VizInstance). */
@Serializable
data class FigureBinding(
    val family_id: String,
    val instance_id: String,
)

@Serializable
data class BeatReveal(
    val steps: List<RevealStep>,
    val figure: FigureBinding? = null,
)

@Serializable
data class BeatName(
    val definition: String,
    val invariant_statement: String,
    val why_matters: String,
)

@Serializable
data class BeatCheck(
    /** Different-instance item — same concept, different surface values (§3.2 ⑤). */
    val item_stem: String,
    val choices: List<AttemptChoice> = emptyList(),
    val numeric_answer: String? = null,
    val numeric_tolerance: Double? = null,
) {
    fun hasGradingData(): Boolean =
        choices.any { it.correct } || numeric_answer != null
}
```

- [ ] **Step 4: Add the `beats` field to `KnowledgeConcept`.** In `src/main/kotlin/jarvis/content/ContentSchema.kt`, the `concept_type` field added in Task 1 is now the last field. Add `beats` immediately after it (VERBATIM from plan core §0.9C):

Find:

```kotlin
    /** Spec §3.1 — wire literal, validated against ConceptType.fromWire at load. Required for every real KC (INV-3.2). */
    val concept_type: String? = null,
)
```

Replace with:

```kotlin
    /** Spec §3.1 — wire literal, validated against ConceptType.fromWire at load. Required for every real KC (INV-3.2). */
    val concept_type: String? = null,
    /** Spec §3.2/§3.7 — language-keyed beat content; key = language code ("ro"). Empty for pre-digestion KCs. */
    val beats: Map<String, KcBeats> = emptyMap(),
)
```

- [ ] **Step 5: Run the new tests — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.content.KcBeatsTest"
```

Expected: `BUILD SUCCESSFUL`, all tests passed (9). **If the YAML round-trip test fails to deserialize the nested `beats` map**, do NOT change the frozen §0.9B types — STOP and escalate to PM (kaml nested-map deserialization is the suspect, not the schema).

- [ ] **Step 6: Run the full backend suite** (never trust a partial run — review-workflow rule):

```powershell
gradle --no-daemon :check
```

Expected: `BUILD SUCCESSFUL`. (The real corpus is unaffected: all 8 KCs have `beats` empty today; `beats` does not feed validation, claims, or hashes.)

- [ ] **Step 7: Commit** (explicit paths only):

```powershell
git add src/main/kotlin/jarvis/content/KcBeats.kt src/main/kotlin/jarvis/content/ContentSchema.kt src/test/kotlin/jarvis/content/KcBeatsTest.kt
git commit -m "feat(content): KcBeats beat field classes + KnowledgeConcept.beats + isCompleteFor (spec 3.2/4.3, additive)"
```

---

## Task 3 — `PedagogicalInstance` + `VizInstance` schema + content loader

Adds the viz-instance content classes (spec §3.3/§5.3/§5.5) and a loader that discovers `content/{subject}/viz/*.yaml` the same way KC YAMLs load (mirroring `ContentRepo.loadSubject`). In Plan 2: schema + loader only (rendering/trace-match = Plan 3). Unknown `family_id` is ALLOWED at load (the family registry lands in Plan 3), but instance `id` must be unique per subject (load error on a dup). An empty/absent `viz/` dir is fine — that is every subject today (Task 0 Step 5). Does not touch `ContentReconcile` / `claimsFor`.

**Files:**
- Create: `src/main/kotlin/jarvis/content/VizInstance.kt`
- Create: `src/test/kotlin/jarvis/content/VizInstanceLoaderTest.kt`
- Modify: `src/main/kotlin/jarvis/content/ContentRepo.kt` (add `loadVizInstances`)

- [ ] **Step 1: Write the failing loader tests.** Create `src/test/kotlin/jarvis/content/VizInstanceLoaderTest.kt`:

```kotlin
package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Plan-2 Task 3 — VizInstance content (spec §3.3/§5.3/§5.5) + the content/{subject}/viz/*.yaml
 * loader. Mirrors the KC loader: one YAML per instance, sorted by id. Unknown family_id is ALLOWED
 * (registry = Plan 3); a duplicate id within a subject is a load ERROR. Empty/absent viz/ = fine.
 */
class VizInstanceLoaderTest {

    private fun writeSubjectsManifest(root: Path) {
        root.resolve("subjects.yaml").writeText(
            """
            version: 1
            subjects:
              - id: PA
                name_ro: "Proiectarea Algoritmilor"
                name_en: "Algorithm Design"
            """.trimIndent(),
        )
    }

    private fun writeViz(root: Path, fileName: String, body: String) {
        val dir = root.resolve("PA").resolve("viz")
        dir.createDirectories()
        dir.resolve(fileName).writeText(body.trimIndent())
    }

    @Test fun `a viz instance YAML round-trips`() {
        val yaml = """
            id: viz-pa-recursion-001
            subject: PA
            family_id: recursion-tree
            language: ro
            instance:
              method_kc_id: pa-kc-fixture-recursion
              data_json: '{"nodes": ["f(3)", "f(2)", "f(1)"], "depth": 3}'
        """.trimIndent()
        val v = Yaml.default.decodeFromString(VizInstance.serializer(), yaml)
        assertEquals("viz-pa-recursion-001", v.id)
        assertEquals("PA", v.subject)
        assertEquals("recursion-tree", v.family_id)
        assertEquals("ro", v.language)
        assertEquals("pa-kc-fixture-recursion", v.instance.method_kc_id)
        val parsed = v.instance.dataElement() as kotlinx.serialization.json.JsonObject
        assertTrue(parsed.containsKey("nodes"))
        assertEquals("3", parsed["depth"].toString())
    }

    @Test fun `loadVizInstances reads every viz yaml for a subject, sorted by id`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "b.yaml", """
            id: viz-pa-002
            subject: PA
            family_id: recursion-tree
            instance:
              method_kc_id: pa-kc-001
        """)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-001
            subject: PA
            family_id: number-line
            instance:
              method_kc_id: pa-kc-005
        """)
        val instances = ContentRepo(tmp).loadVizInstances("PA")
        assertEquals(listOf("viz-pa-001", "viz-pa-002"), instances.map { it.id })
    }

    @Test fun `an unknown family_id is allowed at load (registry lands in Plan 3)`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-001
            subject: PA
            family_id: a-family-that-does-not-exist-yet
            instance:
              method_kc_id: pa-kc-001
        """)
        val instances = ContentRepo(tmp).loadVizInstances("PA")
        assertEquals(1, instances.size)
        assertEquals("a-family-that-does-not-exist-yet", instances.single().family_id)
    }

    @Test fun `a duplicate instance id within a subject is a load error`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-dup
            subject: PA
            family_id: recursion-tree
            instance:
              method_kc_id: pa-kc-001
        """)
        writeViz(tmp, "b.yaml", """
            id: viz-pa-dup
            subject: PA
            family_id: number-line
            instance:
              method_kc_id: pa-kc-005
        """)
        val e = assertFailsWith<IllegalStateException> { ContentRepo(tmp).loadVizInstances("PA") }
        assertTrue(e.message!!.contains("viz-pa-dup"), "the error names the duplicate id: ${e.message}")
        assertTrue(e.message!!.contains("PA"), "the error names the subject: ${e.message}")
    }

    @Test fun `an absent viz dir yields an empty list (every subject today)`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        tmp.resolve("PA").createDirectories()   // subject dir exists, no viz/ subdir
        assertEquals(emptyList(), ContentRepo(tmp).loadVizInstances("PA"))
    }

    @Test fun `malformed data_json is a load error naming the instance`(@TempDir tmp: Path) {
        writeSubjectsManifest(tmp)
        writeViz(tmp, "a.yaml", """
            id: viz-pa-bad-json
            subject: PA
            family_id: recursion-tree
            instance:
              method_kc_id: pa-kc-001
              data_json: '{"nodes": [unclosed'
        """)
        val e = assertFailsWith<IllegalStateException> { ContentRepo(tmp).loadVizInstances("PA") }
        assertTrue(e.message!!.contains("viz-pa-bad-json"), "the error names the instance: ${e.message}")
        assertTrue(e.message!!.contains("data_json"), "the error names the field: ${e.message}")
    }
}
```

- [ ] **Step 2: Run — expect RED** (compile failure: `VizInstance` / `loadVizInstances` unresolved):

```powershell
gradle --no-daemon :compileTestKotlin
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: VizInstance` and `Unresolved reference: loadVizInstances`.

- [ ] **Step 3: Create the content classes** — `src/main/kotlin/jarvis/content/VizInstance.kt` (VERBATIM from plan core §0.9D):

```kotlin
package jarvis.content

import kotlinx.serialization.Serializable

/**
 * Spec §3.3 — a generated example (fresh numbers / new tiny graph). EXEMPT from verbatim grounding;
 * verified by method-consistency + family trace-match (gate work, Plans 3-5). The schema carries the
 * hooks those verifiers need: the method anchor and the reference execution.
 */
@Serializable
data class PedagogicalInstance(
    /** KC whose source-taught method this instance must be solved by (method-consistency anchor). */
    val method_kc_id: String,
    /**
     * Family-typed instance payload (nodes/edges/cells/labels/step-deltas/callouts — §5.3) as a JSON
     * string. STORED AS STRING, not JsonElement: kaml cannot bind kotlinx JsonElement (its serializer
     * is Json-format-only); instances are machine-authored (digestion), so string-JSON costs nothing.
     * The loader MUST parse-validate it at load time — malformed JSON = load error naming the instance.
     * Typed per-family payload schemas land with the family registry (Plan 3).
     */
    val data_json: String = "{}",
) {
    /** Parsed view for consumers/validators; throws on malformed JSON. */
    fun dataElement(): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.Json.parseToJsonElement(data_json)
}

/** Spec §5.3/§5.5 — one typed figure instance: (family_id, instance_data), nothing else. */
@Serializable
data class VizInstance(
    val id: String,
    val subject: String,
    val family_id: String, // one of the ~6 families (§5.2); registry lands in Plan 3
    val language: String = "ro",
    val instance: PedagogicalInstance,
)
```

- [ ] **Step 4: Add the loader to `ContentRepo`.** In `src/main/kotlin/jarvis/content/ContentRepo.kt`, add the `loadVizInstances` method. Insert it immediately after `loadVizIds()` (and before `sourceText`):

```kotlin
    /**
     * Plan-2 Task 3 — load content/{subject}/viz/*.yaml into typed [VizInstance] rows, mirroring the
     * KC loader (one YAML per instance, sorted by id). An absent/empty viz/ dir yields an empty list
     * (every subject today). Unknown family_id is ALLOWED here (the family registry lands in Plan 3);
     * a duplicate instance id WITHIN the subject is a load error (instance ids must be unique).
     */
    fun loadVizInstances(subject: String): List<VizInstance> {
        val dir = root.resolve(subject).resolve("viz")
        val instances = yamlFilesIn(dir)
            .map { Yaml.default.decodeFromString(VizInstance.serializer(), it.readText()) }
            .sortedBy { it.id }
        val firstDup = instances.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }
        check(firstDup == null) {
            "duplicate viz instance id '${firstDup!!.key}' in subject '$subject' " +
                "(${firstDup.value} files share it) — instance ids must be unique per subject"
        }
        // Parse-validate data_json at load (§0.9D contract): malformed JSON = load error naming the instance.
        for (inst in instances) {
            runCatching { inst.instance.dataElement() }.getOrElse {
                error("viz instance '${inst.id}' (subject '$subject') has malformed data_json: ${it.message}")
            }
        }
        return instances
    }
```

- [ ] **Step 5: Run the new tests — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.content.VizInstanceLoaderTest"
```

Expected: `BUILD SUCCESSFUL`, all tests passed (6). (`data_json` is a plain string field — kaml-safe by construction; the earlier `Map<String, JsonElement>` shape was rejected at plan time because kaml cannot bind kotlinx `JsonElement`, whose serializer is Json-format-only.)

- [ ] **Step 6: Run the full backend suite** (never trust a partial run):

```powershell
gradle --no-daemon :check
```

Expected: `BUILD SUCCESSFUL`. (No subject has a `viz/` dir today, so the live corpus load is unchanged.)

- [ ] **Step 7: Commit** (explicit paths only):

```powershell
git add src/main/kotlin/jarvis/content/VizInstance.kt src/main/kotlin/jarvis/content/ContentRepo.kt src/test/kotlin/jarvis/content/VizInstanceLoaderTest.kt
git commit -m "feat(content): VizInstance/PedagogicalInstance schema + content/{subject}/viz loader (spec 3.3/5.5, additive)"
```

---

## Task 4 — `concept_type` YAML backfill (8 PA KCs) + hash-stability proof + validator tightening

Backfills `concept_type` onto all 8 PA KC YAML files using a **dual-agent independent classification protocol** with an agreement requirement diffed against the pre-registered table in plan core §0.9F. Then tightens `ContentValidator` to REQUIRE `concept_type` non-null for ALL KCs (fixtures included — they are real YAML files and §0.9F assigns them types). A machine-checked hash-stability proof asserts the 6 production KCs' `kcContentHashOf(claimsFor(kc))` is IDENTICAL to the 6 live `content_hash` ground-truth values both before and after the edit — proving the 4 faithful badges provably survive (Section 0.3).

> **Why dual-agent, not just "edit the files":** the §0.9F table is the *expected* classification; the protocol exists so the executor does not blindly trust it. Two independent subagents classify from the YAML content, and the result must agree with **each other AND** the §0.9F table. Any three-way disagreement = STOP, escalate to PM (no-oracle-inversion: the executor never silently picks a type).

**Files:**
- Modify: `content/PA/kcs/pa-kc-001.yaml` … `pa-kc-006.yaml`, `pa-kc-fixture-compute.yaml`, `pa-kc-fixture-recursion.yaml` (one line each)
- Modify: `src/main/kotlin/jarvis/content/ContentValidator.kt` (tighten `checkConceptTypeEnum` to require non-null)
- Create: `src/test/kotlin/jarvis/content/ConceptTypeHashStabilityTest.kt`
- Modify: `src/test/kotlin/jarvis/content/ConceptTypeValidationTest.kt` (update the "null passes" expectation to "null fails")

- [ ] **Step 1: Capture the BEFORE hashes (machine, no commit).** Before any YAML edit, write a throwaway proof harness so the hash-stability test in Step 6 has a verified baseline. First run this read-only Kotlin-free check that the live ground-truth hashes still recompute from the CURRENT YAML (this is the pre-edit invariant — the badges are lit on these exact hashes). Create `src/test/kotlin/jarvis/content/ConceptTypeHashStabilityTest.kt` now (it is written to pass on the CURRENT corpus first, then keep passing after the edit):

```kotlin
package jarvis.content

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Path

/**
 * Plan-2 Task 4 — HASH-STABILITY PROOF. concept_type + an (empty) beats map are ADDITIVE fields that
 * do NOT feed ContentReconcile.claimsFor (plan core §0.3), so adding them must leave every production
 * KC's content_hash byte-identical. This test pins kcContentHashOf(claimsFor(kc)) for pa-kc-001..006
 * to the 6 LIVE ground-truth values (read read-only from ~/.jarvis/tutor.db on 2026-06-11). It passes
 * on the pre-edit corpus AND must still pass after Step 4 adds concept_type to the YAML — proving the
 * 4 faithful badges (pa-kc-001..004) provably survive.
 */
class ConceptTypeHashStabilityTest {

    private val contentDir: Path = Path.of("content")

    /** The 6 production verdict content_hash values, live on 2026-06-11 (plan core §0.1 ground truth). */
    private val liveHashes = mapOf(
        "pa-kc-001" to "ca05c671",
        "pa-kc-002" to "56156563",
        "pa-kc-003" to "d5363220",
        "pa-kc-004" to "817aaf44",
        "pa-kc-005" to "1b67adce",
        "pa-kc-006" to "6af8f795",
    )

    @Test fun `production KC content hashes equal the live ground truth (badges survive)`() {
        val repo = ContentRepo(contentDir)
        val kcsById = repo.loadManifest().subjects
            .flatMap { repo.loadSubject(it.id).kcs }
            .associateBy { it.id }
        for ((kcId, expected) in liveHashes) {
            val kc = kcsById[kcId] ?: error("$kcId missing from $contentDir")
            val recomputed = ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
            assertEquals(
                expected, recomputed,
                "$kcId content_hash drifted from the live audited value — concept_type/beats must " +
                    "NOT feed claimsFor; if this fails after editing YAML, the hash contract broke",
            )
        }
    }
}
```

- [ ] **Step 2: Run the proof on the CURRENT (pre-edit) corpus — expect GREEN.** This confirms the 6 live hashes recompute from the YAML as it stands today (the baseline the edit must preserve):

```powershell
gradle --no-daemon :test --tests "jarvis.content.ConceptTypeHashStabilityTest"
```

Expected: `BUILD SUCCESSFUL`, 1 test passed. **STOP if it fails** — then the live audited hashes do not match the current YAML even before your edit, which means the corpus already drifted; escalate to PM rather than editing further.

- [ ] **Step 3: Dual-agent classification protocol (no file edits yet).**

3a. Dispatch **two independent classification subagents** (Sonnet is sufficient; token-frugality rule). Give each EXACTLY this prompt, substituting the KC YAML body. Run them with no shared context (separate agent invocations) so their judgments are independent:

> You are classifying ONE knowledge-concept YAML into exactly one of these 8 `concept_type` literals: `procedure`, `proof`, `definition-taxonomy`, `code-trace`, `timing`, `probabilistic`, `comparison`, `formula-application`. Read the KC's `name_ro`/`name_en`, `bloom_level`, `cluster`, `invariant` (if any), and `source[*].quote`. Decide the single best literal. Output ONLY a JSON object: `{"kc_id":"<id>","concept_type":"<literal>","one_line_basis":"<why>"}`. Do not output anything else.
>
> KC YAML:
> ```
> <paste the full content of the KC file>
> ```

Run each agent over all 8 KC files (`pa-kc-001.yaml` … `pa-kc-006.yaml`, `pa-kc-fixture-compute.yaml`, `pa-kc-fixture-recursion.yaml`). Collect agent-A's 8 outputs and agent-B's 8 outputs.

3b. Diff the THREE columns per KC: agent-A literal, agent-B literal, and the §0.9F pre-registered literal below. They must all three agree per KC.

| KC | §0.9F expected `concept_type` |
|---|---|
| pa-kc-001 | `definition-taxonomy` |
| pa-kc-002 | `definition-taxonomy` |
| pa-kc-003 | `comparison` |
| pa-kc-004 | `definition-taxonomy` |
| pa-kc-005 | `formula-application` |
| pa-kc-006 | `formula-application` |
| pa-kc-fixture-recursion | `code-trace` |
| pa-kc-fixture-compute | `procedure` |

3c. **Agreement gate.** If, for every one of the 8 KCs, agent-A == agent-B == §0.9F literal: proceed to Step 4. **If ANY KC has a three-way disagreement** (A≠B, or A≠§0.9F, or B≠§0.9F): STOP. Do not edit any YAML. Escalate to PM with a table of the disagreements (kc_id, agent-A, agent-B, §0.9F, each agent's one_line_basis). The PM ratifies the type — the executor never silently picks (no-false-attribution / no-oracle-inversion).

- [ ] **Step 4: Edit the 8 YAML files — add one `concept_type` line each** (only after Step 3c agreement). Add the line on its own line at top level (not nested), anywhere among the scalar fields; placing it directly after the `tier:` line keeps the files readable. Use the §0.9F literal (now ratified by the dual-agent agreement). The 8 edits:

  - `content/PA/kcs/pa-kc-001.yaml` → add `concept_type: definition-taxonomy`
  - `content/PA/kcs/pa-kc-002.yaml` → add `concept_type: definition-taxonomy`
  - `content/PA/kcs/pa-kc-003.yaml` → add `concept_type: comparison`
  - `content/PA/kcs/pa-kc-004.yaml` → add `concept_type: definition-taxonomy`
  - `content/PA/kcs/pa-kc-005.yaml` → add `concept_type: formula-application`
  - `content/PA/kcs/pa-kc-006.yaml` → add `concept_type: formula-application`
  - `content/PA/kcs/pa-kc-fixture-recursion.yaml` → add `concept_type: code-trace`
  - `content/PA/kcs/pa-kc-fixture-compute.yaml` → add `concept_type: procedure`

For each file, insert the line immediately after the existing `tier:` line. Concretely (example for pa-kc-001):

Find:
```yaml
exam_weight: 0.22
tier: 1
```
Replace with:
```yaml
exam_weight: 0.22
tier: 1
concept_type: definition-taxonomy
```

(For pa-kc-005 / pa-kc-006 the line after `tier:` is `grounding_tier: strict` — insert `concept_type:` after the `tier:` line, before `grounding_tier:`. Both orderings parse identically; just keep it at top level.)

- [ ] **Step 5: Re-run the hash-stability proof — expect GREEN (UNCHANGED hashes).** This is the load-bearing assertion: adding `concept_type` (and the defaulted empty `beats`) left every production hash byte-identical:

```powershell
gradle --no-daemon :test --tests "jarvis.content.ConceptTypeHashStabilityTest"
```

Expected: `BUILD SUCCESSFUL`, 1 test passed (same 6 hashes as Step 2). **STOP and escalate if any hash changed** — that would mean `claimsFor()` somehow folded `concept_type`, contradicting Section 0.3; revert the YAML and investigate before proceeding.

- [ ] **Step 6: Tighten the validator to REQUIRE `concept_type` for ALL KCs.** Decide the requirement scope from what the validator can know: fixtures are real YAML files the validator loads, and §0.9F assigns them types, so the requirement covers ALL KCs (no fixture exemption). In `src/main/kotlin/jarvis/content/ContentValidator.kt`, replace the `checkConceptTypeEnum` body from Task 1 so a NULL is now an error:

Find:

```kotlin
        for (kc in sub.kcs) {
            val ct = kc.concept_type ?: continue   // null allowed in Task 1; required in Task 4
            if (ConceptType.fromWire(ct) == null) {
                issues += ValidationIssue(
                    severity = "error",
                    rule = "concept_type_enum",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': concept_type='$ct' is not a valid literal " +
                        "(must be one of: $validLiterals)",
                )
            }
        }
```

Replace with:

```kotlin
        for (kc in sub.kcs) {
            val ct = kc.concept_type
            when {
                ct == null -> issues += ValidationIssue(
                    severity = "error",
                    rule = "concept_type_enum",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': concept_type is required (INV-3.2) " +
                        "— must be one of: $validLiterals",
                )
                ConceptType.fromWire(ct) == null -> issues += ValidationIssue(
                    severity = "error",
                    rule = "concept_type_enum",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': concept_type='$ct' is not a valid literal " +
                        "(must be one of: $validLiterals)",
                )
            }
        }
```

- [ ] **Step 7: Update the Task-1 validation test** whose expectation was "null passes". In `src/test/kotlin/jarvis/content/ConceptTypeValidationTest.kt`, replace the now-obsolete test:

Find:

```kotlin
    @Test fun `null concept_type passes in this task`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", null)))
        assertTrue(issues.isEmpty(), "null is allowed until Task 4 tightens it: $issues")
    }
```

Replace with:

```kotlin
    @Test fun `null concept_type is now an error (Task 4 tightening, INV-3.2)`() {
        val issues = ContentValidator.checkConceptTypeEnum(loaded(kc("k1", null)))
        assertEquals(1, issues.size)
        val it = issues.single()
        assertEquals("concept_type_enum", it.rule)
        assertTrue(it.detail.contains("k1") && it.detail.contains("required"), it.detail)
    }
```

- [ ] **Step 8: Run the content validation tests — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.content.ConceptTypeValidationTest" --tests "jarvis.content.ConceptTypeHashStabilityTest"
```

Expected: `BUILD SUCCESSFUL`, all tests passed.

- [ ] **Step 9: Run the full backend suite — expect GREEN.** This is the real acceptance: `:check` runs `validateContent` over the LIVE corpus, which now requires `concept_type` on every KC. Because Step 4 backfilled all 8 PA KCs, the corpus validates; if any file was missed, this goes red naming the KC:

```powershell
gradle --no-daemon :check
```

Expected: `BUILD SUCCESSFUL`. **STOP and fix if** a `concept_type_enum` error names a KC — that file's backfill line is missing or mistyped; correct it (re-run Step 3 agreement if the type itself is in doubt).

- [ ] **Step 10: Commit** (explicit YAML + source + test paths only — NEVER `git add -A`; the untracked door files must stay untracked):

```powershell
git add content/PA/kcs/pa-kc-001.yaml content/PA/kcs/pa-kc-002.yaml content/PA/kcs/pa-kc-003.yaml content/PA/kcs/pa-kc-004.yaml content/PA/kcs/pa-kc-005.yaml content/PA/kcs/pa-kc-006.yaml content/PA/kcs/pa-kc-fixture-compute.yaml content/PA/kcs/pa-kc-fixture-recursion.yaml src/main/kotlin/jarvis/content/ContentValidator.kt src/test/kotlin/jarvis/content/ConceptTypeValidationTest.kt src/test/kotlin/jarvis/content/ConceptTypeHashStabilityTest.kt
git commit -m "feat(content): backfill concept_type on 8 PA KCs + require it (INV-3.2); hash-stability proof keeps the 4 faithful badges lit"
```

## Task 5 — Problem-bank tables (problems, rubric items, kc links) + migration tests

Spec §3.4: problems are first-class rows. This task creates `src/main/kotlin/jarvis/tutor/Plan2Tables.kt` holding the three problem-bank tables EXACTLY as core §0.9E, registers them in `Migration.kt` `ALL_TABLES`, and pins their exact column sets + idempotence + existing-table-unchanged in a migration test copied from the `LiveShapeColumnsMigrationTest` pattern (`src/test/kotlin/jarvis/tutor/LiveShapeColumnsMigrationTest.kt:23-65`). `Plan2Tables.kt` is a NEW file (verified absent: `Glob src/main/kotlin/jarvis/tutor/Plan2Tables.kt` → no match); Task 6 appends the other 4 tables to the SAME file.

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Plan2Tables.kt`
- Modify: `src/main/kotlin/jarvis/tutor/Migration.kt`
- Create: `src/test/kotlin/jarvis/tutor/Plan2ProblemTablesMigrationTest.kt`

- [ ] **Step 1: Write the failing migration test.** Create `src/test/kotlin/jarvis/tutor/Plan2ProblemTablesMigrationTest.kt`. It full-migrates a fresh temp SQLite DB (the INV-3.1 backup gate self-skips on a 0-card DB, exactly like `LiveShapeColumnsMigrationTest.kt:27`) and asserts the EXACT column set of each new problem-bank table, idempotence (second `migrate()` is a no-op), and that three pre-existing tables keep their frozen column counts (`kc_verification_status` = 7, `exam_dates` = 5, `fsrs_cards` = 15). Column lists below are copied verbatim from the `Plan2Tables.kt` definitions in Step 3.

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-2 Task 5 — pins the EXACT shapes of the three problem-bank tables (spec §3.4) the
 * additive migration creates, their idempotence, and that NO existing table shifts. Mirrors
 * LiveShapeColumnsMigrationTest's fresh-DB technique: a fresh temp SQLite DB has 0 fsrs_cards,
 * so the INV-3.1 backup gate self-skips (nothing to protect) and migrate() runs unguarded.
 */
class Plan2ProblemTablesMigrationTest {

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("plan2-problems.db").toString())
        TutorMigration.migrate(db) // 0 protected rows -> gate self-skips
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `problems table has the exact spec-3-4 column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "subject", "archetype", "statement_json", "parameter_slots_json",
                "solution_present", "solution_json", "exam_language",
                "exam_language_constraints_json", "data_files_json", "source_doc", "source_page",
                "provenance", "synthetic_tag", "created_at", "updated_at",
            ),
            columnsOf(db, "problems").toSet(),
        )
    }

    @Test
    fun `problem_rubric_items table has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "problem_id", "label", "points", "kind",
                "all_or_nothing", "penalty_rules_json", "position",
            ),
            columnsOf(db, "problem_rubric_items").toSet(),
        )
    }

    @Test
    fun `problem_kc_links table is the bare many-to-many join`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf("problem_id", "kc_id"),
            columnsOf(db, "problem_kc_links").toSet(),
        )
    }

    @Test
    fun `migration is idempotent for the new problem tables`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val before = listOf("problems", "problem_rubric_items", "problem_kc_links")
            .associateWith { columnsOf(db, it).size }
        TutorMigration.migrate(db) // second run = no-op
        val after = listOf("problems", "problem_rubric_items", "problem_kc_links")
            .associateWith { columnsOf(db, it).size }
        assertEquals(before, after)
    }

    @Test
    fun `existing tables keep their frozen column counts`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        // kc_verification_status: 7 cols (Phase1Tables.kt KcVerificationStatusTable, post Plan-1)
        assertEquals(7, columnsOf(db, "kc_verification_status").size, columnsOf(db, "kc_verification_status").toString())
        // exam_dates: 5 cols (Phase1Tables.kt:104 — id, user_id, subject, start_at, created_at)
        assertEquals(5, columnsOf(db, "exam_dates").size, columnsOf(db, "exam_dates").toString())
        // fsrs_cards: 15 cols (FROZEN live shape — must never shift under an additive plan)
        assertEquals(15, columnsOf(db, "fsrs_cards").size, columnsOf(db, "fsrs_cards").toString())
    }
}
```

- [ ] **Step 2: Run the test — expect RED (compile failure).** The `problems`/`problem_rubric_items`/`problem_kc_links` tables do not exist yet, so `migrate()` never creates them and the column-set assertions fail; before that, the whole module compiles fine (the test references no new symbols), so this is an assertion RED, not a compile RED.

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.Plan2ProblemTablesMigrationTest"
```

Expected: `Plan2ProblemTablesMigrationTest > problems table has the exact spec-3-4 column set FAILED` with `expected: <[...]> but was: <[]>` (PRAGMA table_info returns no rows for a non-existent table). At least the first three tests fail.

- [ ] **Step 3: Implement — create `src/main/kotlin/jarvis/tutor/Plan2Tables.kt`** with the three problem-bank tables copied VERBATIM from core §0.9E (do not rename a single column; the test in Step 1 was written from these exact `varchar`/`text`/`bool`/`integer`/`double` names):

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Plan-2 additive DB tables (spec §3.4/§3.5). NONE of these ALTERs any existing table; all are
 * brand-new tables registered in TutorMigration ALL_TABLES, created by
 * SchemaUtils.createMissingTablesAndColumns. Task 5 adds the three problem-bank tables; Task 6
 * appends the registry/glossary/schedule tables below.
 *
 * House rules (mirrors Phase1Tables.kt): enum-typed columns are VARCHAR (stored literal == enum
 * name), JSON payloads are `text`, every value here is on a fresh table so a client-side
 * `.default()` at INSERT is sufficient (no ALTER backfill needed).
 */

/** Spec §3.4 — problems are first-class rows, not KC appendages. */
object ProblemsTable : Table("problems") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    /** The problem family, e.g. "Dijkstra on weighted digraph, find shortest path + predecessor table". */
    val archetype = varchar("archetype", 256)
    /** Language-keyed statement: JSON map lang -> statement text. */
    val statementJson = text("statement_json")
    /** Re-rollable values (graph weights, array contents, lambda...) — JSON; null = fixed instance. */
    val parameterSlotsJson = text("parameter_slots_json").nullable()
    /** Whether the source contains a worked solution (R-SOLPRESENT). */
    val solutionPresent = bool("solution_present").default(false)
    val solutionJson = text("solution_json").nullable()
    /** R-LANG: the exam-language constraint id: "alk" | "r" | "cpp" | "bash" | "posix-c" | null. */
    val examLanguage = varchar("exam_language", 32).nullable()
    /** R-LANG details, e.g. banned-STL/banned-function list for POO — JSON; null = none. */
    val examLanguageConstraintsJson = text("exam_language_constraints_json").nullable()
    /** R-DATAFILES: CSV/txt deps the problem names — JSON list of {name, bundled: bool}; null = none. */
    val dataFilesJson = text("data_files_json").nullable()
    val sourceDoc = varchar("source_doc", 256).nullable()
    val sourcePage = integer("source_page").nullable()
    /** Provenance tier (§2.5); null until digestion stamps it. */
    val provenance = varchar("provenance", 24).nullable()
    /** §6.2.4 — honest label for items not derived from a real past paper. */
    val syntheticTag = bool("synthetic_tag").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, subject, archetype) }
}

/** Spec §3.4 rubric rows — G1..Gn, AP/WP, all-or-nothing, penalty rules. Mirrors real grading-doc structure. */
object ProblemRubricItemsTable : Table("problem_rubric_items") {
    val id = varchar("id", 64)
    val problemId = varchar("problem_id", 64).references(ProblemsTable.id)
    /** Rubric row label, "G1".."Gn". */
    val label = varchar("label", 16)
    val points = double("points")
    /** "AP" (answer-points) | "WP" (working-points). */
    val kind = varchar("kind", 4)
    val allOrNothing = bool("all_or_nothing").default(false)
    /** e.g. wrong sign penalized once, not per-line — JSON; null = none. */
    val penaltyRulesJson = text("penalty_rules_json").nullable()
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
    init { index(false, problemId) }
}

/** Spec §3.4 — many-to-many; mastery writes fan out across links. */
object ProblemKcLinksTable : Table("problem_kc_links") {
    val problemId = varchar("problem_id", 64).references(ProblemsTable.id)
    val kcId = varchar("kc_id", 64)
    override val primaryKey = PrimaryKey(problemId, kcId)
}
```

- [ ] **Step 4: Register the three tables in `Migration.kt` `ALL_TABLES`.** Open `src/main/kotlin/jarvis/tutor/Migration.kt`. `ALL_TABLES` ends at `GraderProviderSettingTable,` (line 72) followed by `)` (line 73). Apply this exact edit — replace:

```kotlin
    // Grader provider selector (per-user setting; user-scoped FK to UsersTable.id).
    GraderProviderSettingTable,
)
```

with:

```kotlin
    // Grader provider selector (per-user setting; user-scoped FK to UsersTable.id).
    GraderProviderSettingTable,
    // Plan-2 tables (spec §3.4/§3.5) — problem bank ×3 (Task 5); registry/glossary/schedule (Task 6).
    ProblemsTable,
    ProblemRubricItemsTable,
    ProblemKcLinksTable,
)
```

(Task 6 inserts its four tables between `ProblemKcLinksTable,` and the closing `)`.)

- [ ] **Step 5: Run the test — expect GREEN.**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.Plan2ProblemTablesMigrationTest"
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 6: Run the FULL suite** (new tables enter every migrate() call site; per the review-workflow rule, never trust a partial run):

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`. Every existing migration test stays green — the three new tables are brand-new (zero ALTER of any existing table), and `createMissingTablesAndColumns` only creates what is missing.

- [ ] **Step 7: Commit** (named files only — never `git add -A`; untracked door demo files on `main` must stay untracked):

```powershell
git add src/main/kotlin/jarvis/tutor/Plan2Tables.kt src/main/kotlin/jarvis/tutor/Migration.kt src/test/kotlin/jarvis/tutor/Plan2ProblemTablesMigrationTest.kt
git commit -m "feat(schema): Plan-2 problem-bank tables (problems, rubric items, kc links) + migration pins"
```

---

## Task 6 — Registry + glossary + schedule tables + migration tests

Spec §3.5: the grade-model registry (`grading_models` + `grade_components`), the per-subject EN↔RO `glossary_terms`, and the lock-routed `exam_schedule_rows` (the 12 IA12 rows live here; `exam_dates` keeps its frozen one-row-per-(user,subject) contract — core §0.8 #1). This task APPENDS the four tables to the existing `src/main/kotlin/jarvis/tutor/Plan2Tables.kt` (created in Task 5), registers them in `ALL_TABLES`, and pins their exact shapes + idempotence.

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/Plan2Tables.kt`
- Modify: `src/main/kotlin/jarvis/tutor/Migration.kt`
- Create: `src/test/kotlin/jarvis/tutor/Plan2MetaTablesMigrationTest.kt`

- [ ] **Step 1: Write the failing migration test.** Create `src/test/kotlin/jarvis/tutor/Plan2MetaTablesMigrationTest.kt`, same fresh-DB technique as Task 5. Column lists copied verbatim from the `Plan2Tables.kt` additions in Step 2.

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-2 Task 6 — pins the exact shapes of the course-meta tables (spec §3.5): grading_models,
 * grade_components, glossary_terms, exam_schedule_rows. Same fresh-DB / 0-card / gate-self-skip
 * technique as Plan2ProblemTablesMigrationTest.
 */
class Plan2MetaTablesMigrationTest {

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("plan2-meta.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `grading_models has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "subject", "variant", "is_primary", "formula", "max_total",
                "pass_rule_json", "curve_json", "evidence_tier", "source_url",
                "notes_json", "sweep_ref", "created_at",
            ),
            columnsOf(db, "grading_models").toSet(),
        )
    }

    @Test
    fun `grade_components has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "model_id", "name", "max_points", "weight", "min_gate_json",
                "reexam_policy", "evidence_tier", "source_url", "detail_json", "position",
            ),
            columnsOf(db, "grade_components").toSet(),
        )
    }

    @Test
    fun `glossary_terms has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf("id", "subject", "term_en", "term_ro", "source_doc", "created_at"),
            columnsOf(db, "glossary_terms").toSet(),
        )
    }

    @Test
    fun `exam_schedule_rows has the exact column set`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        assertEquals(
            setOf(
                "id", "subject", "raw_discipline", "exam_type", "start_at", "end_at",
                "room", "date_precision", "source_ref", "created_at",
            ),
            columnsOf(db, "exam_schedule_rows").toSet(),
        )
    }

    @Test
    fun `migration is idempotent for the new meta tables`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val tables = listOf("grading_models", "grade_components", "glossary_terms", "exam_schedule_rows")
        val before = tables.associateWith { columnsOf(db, it).size }
        TutorMigration.migrate(db) // second run = no-op
        assertEquals(before, tables.associateWith { columnsOf(db, it).size })
    }

    @Test
    fun `exam_dates stays the frozen one-row-per-subject 5-column shape`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        // Lock §R: exam_dates is NOT widened by Plan-2; the 12 schedule rows live in
        // exam_schedule_rows instead (core §0.8 #1).
        assertEquals(
            setOf("id", "user_id", "subject", "start_at", "created_at"),
            columnsOf(db, "exam_dates").toSet(),
        )
    }
}
```

- [ ] **Step 2: Run the test — expect RED.** The four meta tables do not exist yet.

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.Plan2MetaTablesMigrationTest"
```

Expected: the four column-set tests FAIL with `expected: <[...]> but was: <[]>`.

- [ ] **Step 3: Implement — append the four tables to `src/main/kotlin/jarvis/tutor/Plan2Tables.kt`**, after `ProblemKcLinksTable` (the last object Task 5 added). Copy VERBATIM from core §0.9E:

```kotlin
/** Spec §3.5 R-GRADEMODEL — one verified row per subject per model variant; seeded ONLY from the 2026-06-11 sweep. */
object GradingModelsTable : Table("grading_models") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    /** Variant discriminator, e.g. "live-page" vs "fisa-2024-25-ro" vs "fisa-2025-26-en"; null = sole model. */
    val variant = varchar("variant", 64).nullable()
    /** The model the dashboard/scheduler consumes; at most one true per subject. */
    val isPrimary = bool("is_primary").default(false)
    /** Verbatim formula from the source, e.g. "Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris". */
    val formula = text("formula")
    val maxTotal = double("max_total").nullable()
    /** Pass conditions — JSON, e.g. {"all":[{"component":"exam","min":3,"scale":10},{"total_min":360}]}. */
    val passRuleJson = text("pass_rule_json")
    /** Curve description — JSON; null = unknown (a gap, not a guess). */
    val curveJson = text("curve_json").nullable()
    /** "official-site" | "corpus-evidence" (spec §3.5 evidence tier). */
    val evidenceTier = varchar("evidence_tier", 24)
    val sourceUrl = varchar("source_url", 512)
    /** Errata/gap annotations carried from the sweep — JSON list of strings. */
    val notesJson = text("notes_json").nullable()
    /** Anchor into the sweep doc, e.g. "verified-grade-models-exam-schedule.md#alo". */
    val sweepRef = varchar("sweep_ref", 256)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("grading_models_subject_variant", subject, variant) }
}

/** Spec §3.5 — per-component rows under a grading model. */
object GradeComponentsTable : Table("grade_components") {
    val id = varchar("id", 64)
    val modelId = varchar("model_id", 64).references(GradingModelsTable.id)
    val name = varchar("name", 128)
    val maxPoints = double("max_points").nullable()
    val weight = double("weight").nullable()
    /** Per-component minimum gate — JSON; null = none stated. */
    val minGateJson = text("min_gate_json").nullable()
    /** "yes" | "no" | "unknown" — never guess; unknown is honest (G1/G9). */
    val reexamPolicy = varchar("reexam_policy", 12).nullable()
    val evidenceTier = varchar("evidence_tier", 24)
    val sourceUrl = varchar("source_url", 512).nullable()
    /** Free detail (schedule week, duration, format facts) — JSON. */
    val detailJson = text("detail_json").nullable()
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
    init { index(false, modelId) }
}

/** Spec §3.5 — per-subject EN<->RO term pairs; language validator exemption list (§8.2). */
object GlossaryTermsTable : Table("glossary_terms") {
    val id = varchar("id", 64)
    val subject = varchar("subject", 64)
    val termEn = varchar("term_en", 256)
    val termRo = varchar("term_ro", 256)
    val sourceDoc = varchar("source_doc", 256).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("glossary_subject_term_en", subject, termEn) }
}

/**
 * Spec §3.5 exam_dates seeding, LOCK-ROUTED (core §0.8 #1): the 12 verbatim IA12 sweep rows live HERE;
 * the frozen exam_dates table keeps its one-row-per-(user,subject) contract untouched.
 */
object ExamScheduleRowsTable : Table("exam_schedule_rows") {
    val id = varchar("id", 64)
    /** Tutor subject code (ALO/SORC/POO/PA) or the raw discipline name when unmapped (EF, Pedagogie 1, Reverse Engineering — G13: NEVER map RE to SORC). */
    val subject = varchar("subject", 64)
    val rawDiscipline = varchar("raw_discipline", 128)
    /** "examen" | "restanta" | "test-practic" | "examen-facultativ". */
    val examType = varchar("exam_type", 24)
    val startAt = timestamp("start_at")
    val endAt = timestamp("end_at").nullable()
    val room = varchar("room", 128).nullable()
    /** "exact" | "vague" — dates may be vague ("session week"); scheduler consumes both (§3.5/§7.4). */
    val datePrecision = varchar("date_precision", 12)
    /** Trace to the verified sweep (INV-3.4). */
    val sourceRef = varchar("source_ref", 256)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, subject, startAt) }
}
```

- [ ] **Step 4: Register the four tables in `Migration.kt` `ALL_TABLES`.** After Task 5, the tail of `ALL_TABLES` reads:

```kotlin
    // Plan-2 tables (spec §3.4/§3.5) — problem bank ×3 (Task 5); registry/glossary/schedule (Task 6).
    ProblemsTable,
    ProblemRubricItemsTable,
    ProblemKcLinksTable,
)
```

Replace it with:

```kotlin
    // Plan-2 tables (spec §3.4/§3.5) — problem bank ×3 (Task 5); registry/glossary/schedule (Task 6).
    ProblemsTable,
    ProblemRubricItemsTable,
    ProblemKcLinksTable,
    GradingModelsTable,
    GradeComponentsTable,
    GlossaryTermsTable,
    ExamScheduleRowsTable,
)
```

- [ ] **Step 5: Run the test — expect GREEN.**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.Plan2MetaTablesMigrationTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 6: Run the FULL suite.**

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit** (named files only):

```powershell
git add src/main/kotlin/jarvis/tutor/Plan2Tables.kt src/main/kotlin/jarvis/tutor/Migration.kt src/test/kotlin/jarvis/tutor/Plan2MetaTablesMigrationTest.kt
git commit -m "feat(schema): Plan-2 grade-model registry + glossary + exam-schedule tables + migration pins"
```

---

## Task 7 — Seeder: grade models, schedule rows, exam_dates primaries + idempotency tests

Spec §3.5 / INV-3.4. This task creates `src/main/kotlin/jarvis/tutor/Plan2Seed.kt` exposing `seedKnowledgeMeta(db: Database, now: Instant): SeedReport`, wires a `jarvis seed-knowledge-meta [--db PATH]` CLI subcommand into `Main.kt` exactly like the existing `trust-export`/`trust-import` subcommands, and seeds VERBATIM from the sweep doc `docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md` (the ONLY permitted source; the taxonomy doc is NEVER read). The seeder is idempotent (stable ids + upsert-by-id), so re-running it produces identical row counts.

**SCOPE FENCE (binding):** This task touches NONE of `ContentReconcile.claimsFor` / `kcContentHashOf` / `TrustSync.VerdictRow` / `VerificationGate.gate`. It only INSERTs/UPDATEs the six new Plan-2 tables + the existing `exam_dates` (via the frozen one-row-per-(user,subject) upsert). If implementation seems to require touching any frozen signature, STOP and escalate to the PM — do not work around it.

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/Plan2Seed.kt`
- Modify: `src/main/kotlin/jarvis/Main.kt`
- Create: `src/test/kotlin/jarvis/tutor/Plan2SeedTest.kt`

### Seed-data ledger (every value below is copied from the sweep doc; cite the anchor)

`sweepRef` constant for all schedule rows = `docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule`.
`sweepRef` for grade models = `docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#<subject-anchor>` (anchors: `alo`, `ps`, `poo`, `pa`, `sorc`).

**8 grading_models rows** (ids stable; `evidenceTier="official-site"` for all 8 model rows; corpus-evidence lives on COMPONENT rows only):

| id | subject | variant | isPrimary | formula (verbatim) | maxTotal | sourceUrl |
|---|---|---|---|---|---|---|
| gm-alo | ALO | null | true | `Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris` | 800.0 | https://edu.info.uaic.ro/algebra-liniara/ |
| gm-ps-live | PS | live-page | true | `Verificare mixtă: seminarii 60 + laboratoare 60 (total 100)` | 100.0 | https://edu.info.uaic.ro/probabilitati-si-statistica/ |
| gm-ps-fisa | PS | fisa | false | `Verificare mixtă (fisa §10): Test teoretic 50 + Temă de laborator 17 + Test 17 + Verificare practică 16` | 100.0 | https://edu.info.uaic.ro/probabilitati-si-statistica/files/IA_sem_2_Fisa%20disciplinei_Probabilitati%20si%20statistica.pdf |
| gm-poo | POO | null | true | `Total = T1 (30) + T2 (30) + Final (30) + Lab activity (≤10) = 100` | 100.0 | https://gdt050579.github.io/poo_course_fii/administrative.html |
| gm-pa-2425 | PA | fisa-2024-25-ro | false | `Teste scrise (curs) 70% + Seminar 30% (10% prezență + 20% activitate)` | 100.0 | https://edu.info.uaic.ro/fise-discipline/2024-2025/Proiectarea-algoritmilor-Licenta-Informatica.pdf |
| gm-pa-2526 | PA | fisa-2025-26-en | false | `65% continuous (Practical 32.5 + Theoretical 32.5) + 35% final written assessment` | 100.0 | https://edu.info.uaic.ro/fise-discipline/2025--2026/2025-Licen%C8%9B%C4%83-Informatic%C4%83(%C3%AEn%20limba%20englez%C4%83)/InfoEng_sem_2_Fisa%20disciplinei_Proiectarea%20Algoritmilor.pdf |
| gm-sorc | SORC | null | true | `NF = 0.1*SO1 + 0.3*SO2 + 0.1*SO3 + 0.1*RC1 + 0.4*RC2` | null | https://profs.info.uaic.ro/georgiana.calancea/so-rc-evaluation.html |

Per-model JSON fields (verbatim facts from the sweep — encode as raw JSON strings, do NOT invent keys beyond what the sweep states):
- **gm-alo:** `passRuleJson` = `{"all":[{"component":"exam","min":3,"scale":10},{"total_min":360,"scale":800}]}` ; `curveJson` = `{"type":"gaussian-over-passers","bands":[{"pct":10,"grade":10},{"pct":20,"grade":9},{"pct":25,"grade":8},{"pct":25,"grade":7},{"pct":15,"grade":6},{"pct":5,"grade":5}]}` ; `notesJson` = `["reexam-policy-unknown-G1"]`.
- **gm-ps-live:** `passRuleJson` = `{"all":[{"bucket":"seminars","min":30,"scale":60},{"bucket":"labs","min":30,"scale":60}]}` ; `curveJson` = `null` ; `notesJson` = `["no-reexam-any-component","curve-unknown-G6"]`.
- **gm-ps-fisa:** `passRuleJson` = `{"note":"fisa 4-component split unreconciled with live 60+60 bucketing (G4); live page is the user-facing contract"}` ; `curveJson` = `null` ; `notesJson` = `["unreconciled-G4","all-components-reexam-Nu"]`.
- **gm-poo:** `passRuleJson` = `{"all":[{"total_min":45,"scale":100}]}` ; `curveJson` = `{"type":"ects-percentile","note":"established from the distribution of promoted scores"}` ; `notesJson` = `["E13-no-20pt-gate-excluded","E14-no-attendance-gate-excluded"]`.
- **gm-pa-2425:** `passRuleJson` = `{"all":[{"total_min":45,"scale":100}]}` ; `curveJson` = `null` ; `notesJson` = `["unreconciled-G11"]`.
- **gm-pa-2526:** `passRuleJson` = `{"all":[{"total_min":45,"scale":100}]}` ; `curveJson` = `null` ; `notesJson` = `["unreconciled-G11"]`.
- **gm-sorc:** `passRuleJson` = `{"all":[{"component":"RC2","min":5},{"component":"NF","min":5}]}` ; `curveJson` = `null` ; `notesJson` = `["RC2-retakeable","SO-component-reexam-unknown-G9"]`.

**grade_components rows** (stable ids `gc-<model>-<n>`; `evidenceTier` per row — `official-site` unless flagged corpus-evidence):

- gm-alo components (all `official-site`, `reexamPolicy="unknown"`):
  - `gc-alo-lab` name `Laborator (Teme T1–T5)` maxPoints 300.0 detailJson `{"temas":[50,60,60,60,70],"early_submission":"+≤5 pts/temă if ≥1 week early","late":"-50%","weeks":"T1 wk5, T2/T3 wk12, T4/T5 wk14"}` position 1
  - `gc-alo-seminar` name `Test seminar` maxPoints 100.0 detailJson `{"formula":"10 × nota","week":7,"duration_min":30,"covers":"seminar weeks 2–7"}` position 2
  - `gc-alo-exam` name `Test scris (exam)` maxPoints 400.0 minGateJson `{"min":3,"scale":10}` detailJson `{"formula":"40 × nota","duration_min":60,"exercises":"3–4","note":"first 30 min printed-docs-only"}` position 3
- gm-ps-live components (all `official-site`, `reexamPolicy="no"`):
  - `gc-ps-live-seminars` name `Seminarii` maxPoints 60.0 minGateJson `{"min":30,"scale":60}` detailJson `{"format":"6 × 10-pt 15-min mini-tests, one per seminar"}` position 1
  - `gc-ps-live-labs` name `Laboratoare` maxPoints 60.0 minGateJson `{"min":30,"scale":60}` detailJson `{"format":"Teme A–D 20 + class exercises 20 + final-week 20-min stats test 20"}` position 2
- gm-ps-fisa components (all `official-site`, `reexamPolicy="no"` — fisa "reexaminare: Nu"):
  - `gc-ps-fisa-teoretic` name `Test teoretic` maxPoints 50.0 position 1
  - `gc-ps-fisa-tema` name `Temă de laborator` maxPoints 17.0 position 2
  - `gc-ps-fisa-test` name `Test` maxPoints 17.0 position 3
  - `gc-ps-fisa-practica` name `Verificare practică` maxPoints 16.0 position 4
- gm-poo components (all `official-site`):
  - `gc-poo-t1` name `First lab evaluation (T1)` maxPoints 30.0 detailJson `{"week":8,"format":"in-lab C++ coding"}` reexamPolicy `yes` position 1
  - `gc-poo-t2` name `Second lab evaluation (T2)` maxPoints 30.0 detailJson `{"week":"14 or 15","format":"in-lab C++ coding"}` reexamPolicy `yes` position 2
  - `gc-poo-final` name `Final evaluation` maxPoints 30.0 detailJson `{"window":"examination period","format":"unknown-G12"}` reexamPolicy `yes` position 3
  - `gc-poo-lab` name `Lab activity` maxPoints 10.0 detailJson `{"rule":"up to 1 pt per lab, ≤10 total"}` reexamPolicy `unknown` position 4
- gm-pa-2425 components (all `official-site`):
  - `gc-pa-2425-curs` name `Teste scrise (curs)` weight 0.70 detailJson `{"partial":"wk8 Evaluare parțială — Test scris"}` position 1
  - `gc-pa-2425-seminar` name `Seminar` weight 0.30 detailJson `{"split":"10% prezență + 20% activitate"}` position 2
- gm-pa-2526 components (all `official-site`):
  - `gc-pa-2526-course` name `Course continuous (Practical + Theoretical)` maxPoints 32.5 detailJson `{"block":"65% continuous, 50% of block","reexam":"yes (both written tests)"}` reexamPolicy `yes` position 1
  - `gc-pa-2526-seminar` name `Seminar continuous (oral)` maxPoints 32.5 detailJson `{"block":"65% continuous, 50% of block","reexam":"no"}` reexamPolicy `no` position 2
  - `gc-pa-2526-final` name `Final written assessment` maxPoints 35.0 position 3
- gm-pa CORPUS-EVIDENCE components (E10/E11/E12 — attached to gm-pa-2526, `evidenceTier="corpus-evidence"`):
  - `gc-pa-corpus-alk` name `Alk pseudocode mandatory` detailJson `{"evidence":"past-paper inspection only; absent from both fise","erratum":"E11"}` position 4
  - `gc-pa-corpus-35pts` name `2 × 35-pt written tests` detailJson `{"evidence":"corpus only; '35 pt' in no fisa","erratum":"E10"}` position 5
  - `gc-pa-corpus-retake` name `Retake format` detailJson `{"evidence":"corpus only; fisa says reexam Yes but no format/points","erratum":"E12"}` position 6
- gm-sorc components:
  - `gc-sorc-so1` name `SO1` weight 0.10 evidenceTier `official-site` reexamPolicy `unknown` detailJson `{"definition":"unknown","gap":"G7"}` position 1
  - `gc-sorc-so2` name `SO2` weight 0.30 evidenceTier `official-site` reexamPolicy `unknown` detailJson `{"definition":"unknown","gap":"G7"}` position 2
  - `gc-sorc-so3` name `SO3` weight 0.10 evidenceTier `official-site` reexamPolicy `unknown` detailJson `{"definition":"unknown","gap":"G7"}` position 3
  - `gc-sorc-rc1` name `RC1` weight 0.10 evidenceTier `official-site` reexamPolicy `unknown` detailJson `{"what":"practical lab marks incl. code-review wk11–14 + other lab activities"}` position 4
  - `gc-sorc-rc2` name `RC2` weight 0.40 evidenceTier `official-site` reexamPolicy `yes` minGateJson `{"min":5}` detailJson `{"what":"written exam, Computer Networks module, weeks 9–14 content"}` position 5
  - `gc-sorc-corpus-tso` name `T.SO` evidenceTier `corpus-evidence` reexamPolicy `unknown` detailJson `{"evidence":"corpus only; SO sub-site HTTP 401","claims":"week 8, 30 pts, 12-pt gate, practical C/bash + 18-item MCQ","erratum":"E9"}` position 6

**12 exam_schedule_rows** — all `datePrecision="exact"`, `sourceRef="docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"`. Stable ids `esr-01`..`esr-12`. **UTC conversion (Europe/Bucharest June = EEST = UTC+3; store UTC `Instant`s):**

| id | subject | rawDiscipline | examType | local start (EEST) | startAt (UTC) | local end | endAt (UTC) | room |
|---|---|---|---|---|---|---|---|---|
| esr-01 | ALO | Algebră liniară și optimizare | examen | 2026-06-03 16:00 | `2026-06-03T13:00:00Z` | 18:00 | `2026-06-03T15:00:00Z` | C2 |
| esr-02 | SORC | Sisteme de operare și rețele de calculatoare | examen | 2026-06-06 11:00 | `2026-06-06T08:00:00Z` | 13:00 | `2026-06-06T10:00:00Z` | C2 |
| esr-03 | POO | Programare orientată-obiect | examen | 2026-06-09 08:00 | `2026-06-09T05:00:00Z` | 10:00 | `2026-06-09T07:00:00Z` | C2, C210, C309, C412, C413 |
| esr-04 | Educație fizică | Educație fizică | examen | 2026-06-09 16:00 | `2026-06-09T13:00:00Z` | 18:00 | `2026-06-09T15:00:00Z` | Teren Sport |
| esr-05 | PA | Proiectarea algoritmilor | examen | 2026-06-10 12:00 | `2026-06-10T09:00:00Z` | 14:00 | `2026-06-10T11:00:00Z` | C112, C2, C308, C309 |
| esr-06 | POO | Programare orientată-obiect | test-practic | 2026-06-16 08:00 | `2026-06-16T05:00:00Z` | 12:00 | `2026-06-16T09:00:00Z` | C210, C308, C309, C401, C403, C405, C409, C411, C412, C413 |
| esr-07 | Pedagogie 1 | Pedagogie 1 | examen-facultativ | 2026-06-18 09:00 | `2026-06-18T06:00:00Z` | 11:00 | `2026-06-18T08:00:00Z` | C2 |
| esr-08 | SORC | Sisteme de operare și rețele de calculatoare | restanta | 2026-06-20 10:00 | `2026-06-20T07:00:00Z` | 12:00 | `2026-06-20T09:00:00Z` | C2 |
| esr-09 | ALO | Algebră liniară și optimizare | restanta | 2026-06-22 12:00 | `2026-06-22T09:00:00Z` | 14:00 | `2026-06-22T11:00:00Z` | C2 |
| esr-10 | Reverse Engineering | Reverse Engineering | restanta | 2026-06-22 19:00 | `2026-06-22T16:00:00Z` | 20:00 | `2026-06-22T17:00:00Z` | C309 |
| esr-11 | PA | Proiectarea algoritmilor | restanta | 2026-06-23 10:00 | `2026-06-23T07:00:00Z` | 12:00 | `2026-06-23T09:00:00Z` | C112, C2, C308, C309 |
| esr-12 | POO | Programare orientată-obiect | restanta | 2026-06-25 18:00 | `2026-06-25T15:00:00Z` | 20:00 | `2026-06-25T17:00:00Z` | C2, C308, C309 |

Row 10's `subject` stays the raw string `Reverse Engineering` (G13 — NEVER seeded as SORC/RC). PS has zero rows (Verificare). `exam_dates` additionally gets 4 PRIMARY rows via its frozen one-row-per-(user,subject) upsert — `(ALO, 2026-06-03T13:00:00Z)`, `(SORC, 2026-06-06T08:00:00Z)`, `(POO, 2026-06-09T05:00:00Z)`, `(PA, 2026-06-10T09:00:00Z)`.

- [ ] **Step 1: Write the failing idempotency + INV-3.4-shape test.** Create `src/test/kotlin/jarvis/tutor/Plan2SeedTest.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-2 Task 7 — seedKnowledgeMeta idempotency + INV-3.4 shape (spec §3.5).
 * A migrated temp DB with exactly one user; seed twice; assert identical counts + the INV-3.4
 * invariants (every grading_models row has a non-empty source_url; 12 exam_schedule_rows; every
 * schedule row sourceRef == the sweep anchor; at most one primary per subject; row esr-10's
 * subject is the raw 'Reverse Engineering', NEVER SORC).
 */
class Plan2SeedTest {

    private fun migratedDbWithUser(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("seed.db").toString())
        TutorMigration.migrate(db)
        transaction(db) {
            // UsersTable required non-null cols (Users.kt:25-33): id, name, scope, created_at,
            // last_seen_at; email is nullable, lang defaults 'ro'. Set every required column.
            UsersTable.insert {
                it[id] = "01HSEEDUSER000000000000000" // 26 chars — UsersTable.id is varchar(26)
                it[name] = "Alex"
                it[scope] = "OWNER"
                it[createdAt] = Instant.now()
                it[lastSeenAt] = Instant.now()
                it[email] = "alex@example.com"
            }
        }
        return db
    }

    private fun count(db: Database, table: org.jetbrains.exposed.sql.Table): Long =
        transaction(db) { table.selectAll().count() }

    @Test
    fun `seeding twice yields identical row counts (idempotent upsert-by-id)`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        val now = Instant.parse("2026-06-11T10:00:00Z")

        val first = Plan2Seed.seedKnowledgeMeta(db, now)
        val countsAfterFirst = listOf(
            GradingModelsTable, GradeComponentsTable, ExamScheduleRowsTable, ExamDatesTable,
        ).associateWith { count(db, it) }

        val second = Plan2Seed.seedKnowledgeMeta(db, now)
        val countsAfterSecond = listOf(
            GradingModelsTable, GradeComponentsTable, ExamScheduleRowsTable, ExamDatesTable,
        ).associateWith { count(db, it) }

        assertEquals(countsAfterFirst, countsAfterSecond, "re-seed must not duplicate rows")
        assertEquals(8L, countsAfterFirst[GradingModelsTable])
        assertEquals(12L, countsAfterFirst[ExamScheduleRowsTable])
        assertEquals(4L, countsAfterFirst[ExamDatesTable])
        // first run inserts everything; second inserts nothing (all updated)
        assertEquals(0, second.inserted, "second run must insert 0")
        assertTrue(first.inserted > 0, "first run must insert > 0")
    }

    @Test
    fun `INV-3-4 every grading_models row has a non-empty source_url`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            GradingModelsTable.selectAll().forEach {
                assertTrue(
                    it[GradingModelsTable.sourceUrl].isNotBlank(),
                    "grading_models ${it[GradingModelsTable.id]} has a blank source_url",
                )
            }
        }
    }

    @Test
    fun `INV-3-4 exactly 12 schedule rows all tracing the sweep anchor`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        val anchor = "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"
        transaction(db) {
            val rows = ExamScheduleRowsTable.selectAll().toList()
            assertEquals(12, rows.size)
            rows.forEach { assertEquals(anchor, it[ExamScheduleRowsTable.sourceRef]) }
        }
    }

    @Test
    fun `at most one primary grading model per subject`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            GradingModelsTable.selectAll()
                .filter { it[GradingModelsTable.isPrimary] }
                .groupBy { it[GradingModelsTable.subject] }
                .forEach { (subject, rows) ->
                    assertEquals(1, rows.size, "$subject has ${rows.size} primary models")
                }
        }
    }

    @Test
    fun `Romanian diacritics round-trip byte-identical through SQLite`(@TempDir tmp: Path) {
        // A JDBC/encoding regression would corrupt user-facing RO content while every
        // count-based test stays green — assert exact equality including ă/ț/ș/î.
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            val aloFormula = GradingModelsTable.selectAll()
                .first { it[GradingModelsTable.id] == "gm-alo" }[GradingModelsTable.formula]
            assertEquals(
                "Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris",
                aloFormula,
            )
            val esr01 = ExamScheduleRowsTable.selectAll()
                .first { it[ExamScheduleRowsTable.id] == "esr-01" }
            assertEquals("Algebră liniară și optimizare", esr01[ExamScheduleRowsTable.rawDiscipline])
            val esr04 = ExamScheduleRowsTable.selectAll()
                .first { it[ExamScheduleRowsTable.id] == "esr-04" }
            assertEquals("Educație fizică", esr04[ExamScheduleRowsTable.rawDiscipline])
        }
    }

    @Test
    fun `row esr-10 subject is the raw Reverse Engineering, never SORC (G13)`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            val row = ExamScheduleRowsTable.selectAll()
                .first { it[ExamScheduleRowsTable.id] == "esr-10" }
            assertEquals("Reverse Engineering", row[ExamScheduleRowsTable.subject])
        }
    }

    @Test
    fun `exam_dates primaries are the 4 stored-UTC subject dates`(@TempDir tmp: Path) {
        val db = migratedDbWithUser(tmp)
        Plan2Seed.seedKnowledgeMeta(db, Instant.parse("2026-06-11T10:00:00Z"))
        transaction(db) {
            val bySubject = ExamDatesTable.selectAll()
                .associate { it[ExamDatesTable.subject] to it[ExamDatesTable.startAt] }
            assertEquals(setOf("ALO", "SORC", "POO", "PA"), bySubject.keys)
            assertEquals(Instant.parse("2026-06-03T13:00:00Z"), bySubject["ALO"])
            assertEquals(Instant.parse("2026-06-06T08:00:00Z"), bySubject["SORC"])
            assertEquals(Instant.parse("2026-06-09T05:00:00Z"), bySubject["POO"])
            assertEquals(Instant.parse("2026-06-10T09:00:00Z"), bySubject["PA"])
        }
    }
}
```

NOTE: the `UsersTable.insert` block sets every required non-null `UsersTable` column (`Users.kt:25-33`): `id` (varchar 26), `name`, `scope`, `created_at`, `last_seen_at`; `email` is nullable and `lang` defaults to `'ro'`. This was verified against `Users.kt` directly — do NOT drop any of the five required columns (a partial insert throws `NOT NULL constraint failed` and masks the real test).

- [ ] **Step 2: Run the test — expect RED (compile failure: `Plan2Seed` unresolved).**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.Plan2SeedTest"
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: Plan2Seed`.

- [ ] **Step 3: Implement — create `src/main/kotlin/jarvis/tutor/Plan2Seed.kt`.** Full file (every literal copied from the ledger above):

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Plan-2 Task 7 — course-meta seeder (spec §3.5 / INV-3.4). Seeds the grade-model registry, its
 * per-component rows, the 12 IA12 exam_schedule_rows, and the 4 primary exam_dates rows. Every
 * value is copied VERBATIM from the ONLY permitted source:
 *   docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md
 * The taxonomy doc is NEVER read here (it carries 15 errata the sweep corrects).
 *
 * Idempotent: stable ids (gm-*, gc-*, esr-*) + upsert-by-id (UPDATE if present, else INSERT). A
 * re-run produces identical row counts. exam_dates reuses the route's one-row-per-(user,subject)
 * semantics (SessionPlacementExamRoutes.kt:395-409): look up the sole user at runtime.
 *
 * SCOPE: touches NONE of claimsFor / kcContentHashOf / TrustSync.VerdictRow / VerificationGate.
 */
object Plan2Seed {

    private const val SCHED_REF =
        "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"

    private fun gmRef(anchor: String) =
        "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#$anchor"

    /** Counts of rows newly inserted vs updated vs (n/a) skipped across all seeded tables. */
    data class SeedReport(val inserted: Int, val updated: Int, val skipped: Int) {
        val applied: Int get() = inserted + updated
    }

    private data class Model(
        val id: String, val subject: String, val variant: String?, val isPrimary: Boolean,
        val formula: String, val maxTotal: Double?, val passRuleJson: String,
        val curveJson: String?, val sourceUrl: String, val notesJson: String?, val sweepRef: String,
    )

    private data class Component(
        val id: String, val modelId: String, val name: String, val maxPoints: Double?,
        val weight: Double?, val minGateJson: String?, val reexamPolicy: String?,
        val evidenceTier: String, val sourceUrl: String?, val detailJson: String?, val position: Int,
    )

    private data class Schedule(
        val id: String, val subject: String, val rawDiscipline: String, val examType: String,
        val startAt: Instant, val endAt: Instant?, val room: String?,
    )

    // ── Grade models (8) — evidenceTier="official-site" for every MODEL row (corpus tier on components) ──
    private val MODELS = listOf(
        Model(
            "gm-alo", "ALO", null, true,
            "Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris", 800.0,
            """{"all":[{"component":"exam","min":3,"scale":10},{"total_min":360,"scale":800}]}""",
            """{"type":"gaussian-over-passers","bands":[{"pct":10,"grade":10},{"pct":20,"grade":9},{"pct":25,"grade":8},{"pct":25,"grade":7},{"pct":15,"grade":6},{"pct":5,"grade":5}]}""",
            "https://edu.info.uaic.ro/algebra-liniara/", """["reexam-policy-unknown-G1"]""", gmRef("alo"),
        ),
        Model(
            "gm-ps-live", "PS", "live-page", true,
            "Verificare mixtă: seminarii 60 + laboratoare 60 (total 100)", 100.0,
            """{"all":[{"bucket":"seminars","min":30,"scale":60},{"bucket":"labs","min":30,"scale":60}]}""",
            null, "https://edu.info.uaic.ro/probabilitati-si-statistica/",
            """["no-reexam-any-component","curve-unknown-G6"]""", gmRef("ps"),
        ),
        Model(
            "gm-ps-fisa", "PS", "fisa", false,
            "Verificare mixtă (fisa §10): Test teoretic 50 + Temă de laborator 17 + Test 17 + Verificare practică 16",
            100.0,
            """{"note":"fisa 4-component split unreconciled with live 60+60 bucketing (G4); live page is the user-facing contract"}""",
            null,
            "https://edu.info.uaic.ro/probabilitati-si-statistica/files/IA_sem_2_Fisa%20disciplinei_Probabilitati%20si%20statistica.pdf",
            """["unreconciled-G4","all-components-reexam-Nu"]""", gmRef("ps"),
        ),
        Model(
            "gm-poo", "POO", null, true,
            "Total = T1 (30) + T2 (30) + Final (30) + Lab activity (≤10) = 100", 100.0,
            """{"all":[{"total_min":45,"scale":100}]}""",
            """{"type":"ects-percentile","note":"established from the distribution of promoted scores"}""",
            "https://gdt050579.github.io/poo_course_fii/administrative.html",
            """["E13-no-20pt-gate-excluded","E14-no-attendance-gate-excluded"]""", gmRef("poo"),
        ),
        Model(
            "gm-pa-2425", "PA", "fisa-2024-25-ro", false,
            "Teste scrise (curs) 70% + Seminar 30% (10% prezență + 20% activitate)", 100.0,
            """{"all":[{"total_min":45,"scale":100}]}""", null,
            "https://edu.info.uaic.ro/fise-discipline/2024-2025/Proiectarea-algoritmilor-Licenta-Informatica.pdf",
            """["unreconciled-G11"]""", gmRef("pa"),
        ),
        Model(
            "gm-pa-2526", "PA", "fisa-2025-26-en", false,
            "65% continuous (Practical 32.5 + Theoretical 32.5) + 35% final written assessment", 100.0,
            """{"all":[{"total_min":45,"scale":100}]}""", null,
            "https://edu.info.uaic.ro/fise-discipline/2025--2026/2025-Licen%C8%9B%C4%83-Informatic%C4%83(%C3%AEn%20limba%20englez%C4%83)/InfoEng_sem_2_Fisa%20disciplinei_Proiectarea%20Algoritmilor.pdf",
            """["unreconciled-G11"]""", gmRef("pa"),
        ),
        Model(
            "gm-sorc", "SORC", null, true,
            "NF = 0.1*SO1 + 0.3*SO2 + 0.1*SO3 + 0.1*RC1 + 0.4*RC2", null,
            """{"all":[{"component":"RC2","min":5},{"component":"NF","min":5}]}""", null,
            "https://profs.info.uaic.ro/georgiana.calancea/so-rc-evaluation.html",
            """["RC2-retakeable","SO-component-reexam-unknown-G9"]""", gmRef("sorc"),
        ),
    )

    private const val ALO_URL = "https://edu.info.uaic.ro/algebra-liniara/"
    private const val PS_LIVE_URL = "https://edu.info.uaic.ro/probabilitati-si-statistica/"
    private const val POO_URL = "https://gdt050579.github.io/poo_course_fii/administrative.html"
    private const val SORC_URL = "https://profs.info.uaic.ro/georgiana.calancea/so-rc-evaluation.html"

    // ── Grade components ──
    private val COMPONENTS = listOf(
        // gm-alo
        Component("gc-alo-lab", "gm-alo", "Laborator (Teme T1–T5)", 300.0, null, null, "unknown",
            "official-site", ALO_URL,
            """{"temas":[50,60,60,60,70],"early_submission":"+≤5 pts/temă if ≥1 week early","late":"-50%","weeks":"T1 wk5, T2/T3 wk12, T4/T5 wk14"}""", 1),
        Component("gc-alo-seminar", "gm-alo", "Test seminar", 100.0, null, null, "unknown",
            "official-site", ALO_URL,
            """{"formula":"10 × nota","week":7,"duration_min":30,"covers":"seminar weeks 2–7"}""", 2),
        Component("gc-alo-exam", "gm-alo", "Test scris (exam)", 400.0, null, """{"min":3,"scale":10}""", "unknown",
            "official-site", ALO_URL,
            """{"formula":"40 × nota","duration_min":60,"exercises":"3–4","note":"first 30 min printed-docs-only"}""", 3),
        // gm-ps-live
        Component("gc-ps-live-seminars", "gm-ps-live", "Seminarii", 60.0, null, """{"min":30,"scale":60}""", "no",
            "official-site", PS_LIVE_URL, """{"format":"6 × 10-pt 15-min mini-tests, one per seminar"}""", 1),
        Component("gc-ps-live-labs", "gm-ps-live", "Laboratoare", 60.0, null, """{"min":30,"scale":60}""", "no",
            "official-site", PS_LIVE_URL,
            """{"format":"Teme A–D 20 + class exercises 20 + final-week 20-min stats test 20"}""", 2),
        // gm-ps-fisa
        Component("gc-ps-fisa-teoretic", "gm-ps-fisa", "Test teoretic", 50.0, null, null, "no", "official-site", null, null, 1),
        Component("gc-ps-fisa-tema", "gm-ps-fisa", "Temă de laborator", 17.0, null, null, "no", "official-site", null, null, 2),
        Component("gc-ps-fisa-test", "gm-ps-fisa", "Test", 17.0, null, null, "no", "official-site", null, null, 3),
        Component("gc-ps-fisa-practica", "gm-ps-fisa", "Verificare practică", 16.0, null, null, "no", "official-site", null, null, 4),
        // gm-poo
        Component("gc-poo-t1", "gm-poo", "First lab evaluation (T1)", 30.0, null, null, "yes", "official-site", POO_URL,
            """{"week":8,"format":"in-lab C++ coding"}""", 1),
        Component("gc-poo-t2", "gm-poo", "Second lab evaluation (T2)", 30.0, null, null, "yes", "official-site", POO_URL,
            """{"week":"14 or 15","format":"in-lab C++ coding"}""", 2),
        Component("gc-poo-final", "gm-poo", "Final evaluation", 30.0, null, null, "yes", "official-site", POO_URL,
            """{"window":"examination period","format":"unknown-G12"}""", 3),
        Component("gc-poo-lab", "gm-poo", "Lab activity", 10.0, null, null, "unknown", "official-site", POO_URL,
            """{"rule":"up to 1 pt per lab, ≤10 total"}""", 4),
        // gm-pa-2425
        Component("gc-pa-2425-curs", "gm-pa-2425", "Teste scrise (curs)", null, 0.70, null, null, "official-site", null,
            """{"partial":"wk8 Evaluare parțială — Test scris"}""", 1),
        Component("gc-pa-2425-seminar", "gm-pa-2425", "Seminar", null, 0.30, null, null, "official-site", null,
            """{"split":"10% prezență + 20% activitate"}""", 2),
        // gm-pa-2526
        Component("gc-pa-2526-course", "gm-pa-2526", "Course continuous (Practical + Theoretical)", 32.5, null, null, "yes",
            "official-site", null, """{"block":"65% continuous, 50% of block","reexam":"yes (both written tests)"}""", 1),
        Component("gc-pa-2526-seminar", "gm-pa-2526", "Seminar continuous (oral)", 32.5, null, null, "no",
            "official-site", null, """{"block":"65% continuous, 50% of block","reexam":"no"}""", 2),
        Component("gc-pa-2526-final", "gm-pa-2526", "Final written assessment", 35.0, null, null, null,
            "official-site", null, null, 3),
        // gm-pa CORPUS-EVIDENCE (E10/E11/E12) — attached to gm-pa-2526
        Component("gc-pa-corpus-alk", "gm-pa-2526", "Alk pseudocode mandatory", null, null, null, null,
            "corpus-evidence", null, """{"evidence":"past-paper inspection only; absent from both fise","erratum":"E11"}""", 4),
        Component("gc-pa-corpus-35pts", "gm-pa-2526", "2 × 35-pt written tests", null, null, null, null,
            "corpus-evidence", null, """{"evidence":"corpus only; '35 pt' in no fisa","erratum":"E10"}""", 5),
        Component("gc-pa-corpus-retake", "gm-pa-2526", "Retake format", null, null, null, null,
            "corpus-evidence", null, """{"evidence":"corpus only; fisa says reexam Yes but no format/points","erratum":"E12"}""", 6),
        // gm-sorc — SO1/SO2/SO3 = gap G7 (definition unknown, NEVER guessed)
        Component("gc-sorc-so1", "gm-sorc", "SO1", null, 0.10, null, "unknown", "official-site", SORC_URL,
            """{"definition":"unknown","gap":"G7"}""", 1),
        Component("gc-sorc-so2", "gm-sorc", "SO2", null, 0.30, null, "unknown", "official-site", SORC_URL,
            """{"definition":"unknown","gap":"G7"}""", 2),
        Component("gc-sorc-so3", "gm-sorc", "SO3", null, 0.10, null, "unknown", "official-site", SORC_URL,
            """{"definition":"unknown","gap":"G7"}""", 3),
        Component("gc-sorc-rc1", "gm-sorc", "RC1", null, 0.10, null, "unknown", "official-site", SORC_URL,
            """{"what":"practical lab marks incl. code-review wk11–14 + other lab activities"}""", 4),
        Component("gc-sorc-rc2", "gm-sorc", "RC2", null, 0.40, """{"min":5}""", "yes", "official-site", SORC_URL,
            """{"what":"written exam, Computer Networks module, weeks 9–14 content"}""", 5),
        Component("gc-sorc-corpus-tso", "gm-sorc", "T.SO", null, null, null, "unknown", "corpus-evidence", SORC_URL,
            """{"evidence":"corpus only; SO sub-site HTTP 401","claims":"week 8, 30 pts, 12-pt gate, practical C/bash + 18-item MCQ","erratum":"E9"}""", 6),
    )

    // ── 12 exam_schedule_rows (datePrecision="exact"; UTC = Europe/Bucharest June EEST minus 3h) ──
    private val SCHEDULE = listOf(
        Schedule("esr-01", "ALO", "Algebră liniară și optimizare", "examen",
            Instant.parse("2026-06-03T13:00:00Z"), Instant.parse("2026-06-03T15:00:00Z"), "C2"),
        Schedule("esr-02", "SORC", "Sisteme de operare și rețele de calculatoare", "examen",
            Instant.parse("2026-06-06T08:00:00Z"), Instant.parse("2026-06-06T10:00:00Z"), "C2"),
        Schedule("esr-03", "POO", "Programare orientată-obiect", "examen",
            Instant.parse("2026-06-09T05:00:00Z"), Instant.parse("2026-06-09T07:00:00Z"), "C2, C210, C309, C412, C413"),
        Schedule("esr-04", "Educație fizică", "Educație fizică", "examen",
            Instant.parse("2026-06-09T13:00:00Z"), Instant.parse("2026-06-09T15:00:00Z"), "Teren Sport"),
        Schedule("esr-05", "PA", "Proiectarea algoritmilor", "examen",
            Instant.parse("2026-06-10T09:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), "C112, C2, C308, C309"),
        Schedule("esr-06", "POO", "Programare orientată-obiect", "test-practic",
            Instant.parse("2026-06-16T05:00:00Z"), Instant.parse("2026-06-16T09:00:00Z"),
            "C210, C308, C309, C401, C403, C405, C409, C411, C412, C413"),
        Schedule("esr-07", "Pedagogie 1", "Pedagogie 1", "examen-facultativ",
            Instant.parse("2026-06-18T06:00:00Z"), Instant.parse("2026-06-18T08:00:00Z"), "C2"),
        Schedule("esr-08", "SORC", "Sisteme de operare și rețele de calculatoare", "restanta",
            Instant.parse("2026-06-20T07:00:00Z"), Instant.parse("2026-06-20T09:00:00Z"), "C2"),
        Schedule("esr-09", "ALO", "Algebră liniară și optimizare", "restanta",
            Instant.parse("2026-06-22T09:00:00Z"), Instant.parse("2026-06-22T11:00:00Z"), "C2"),
        Schedule("esr-10", "Reverse Engineering", "Reverse Engineering", "restanta",
            Instant.parse("2026-06-22T16:00:00Z"), Instant.parse("2026-06-22T17:00:00Z"), "C309"),
        Schedule("esr-11", "PA", "Proiectarea algoritmilor", "restanta",
            Instant.parse("2026-06-23T07:00:00Z"), Instant.parse("2026-06-23T09:00:00Z"), "C112, C2, C308, C309"),
        Schedule("esr-12", "POO", "Programare orientată-obiect", "restanta",
            Instant.parse("2026-06-25T15:00:00Z"), Instant.parse("2026-06-25T17:00:00Z"), "C2, C308, C309"),
    )

    // ── exam_dates primaries: (subject, startAt UTC) one row per (user, subject) ──
    private val PRIMARY_EXAM_DATES = listOf(
        "ALO" to Instant.parse("2026-06-03T13:00:00Z"),
        "SORC" to Instant.parse("2026-06-06T08:00:00Z"),
        "POO" to Instant.parse("2026-06-09T05:00:00Z"),
        "PA" to Instant.parse("2026-06-10T09:00:00Z"),
    )

    /**
     * Seed (or re-seed) all course-meta tables. Idempotent: upsert-by-id for the registry/schedule,
     * one-row-per-(user,subject) upsert for exam_dates. Runs inside ONE transaction.
     */
    fun seedKnowledgeMeta(db: Database, now: Instant): SeedReport {
        var inserted = 0
        var updated = 0
        transaction(db) {
            // sole user (exam_dates is user-scoped); abort if not exactly one
            val userIds = UsersTable.selectAll().map { it[UsersTable.id] }
            require(userIds.size == 1) {
                "seedKnowledgeMeta expects exactly ONE user, found ${userIds.size} — refusing (exam_dates is user-scoped)"
            }
            val userId = userIds.single()

            // ── grade models ──
            for (m in MODELS) {
                val exists = GradingModelsTable.selectAll()
                    .where { GradingModelsTable.id eq m.id }.any()
                if (exists) {
                    GradingModelsTable.update({ GradingModelsTable.id eq m.id }) { row ->
                        row[subject] = m.subject; row[variant] = m.variant; row[isPrimary] = m.isPrimary
                        row[formula] = m.formula; row[maxTotal] = m.maxTotal; row[passRuleJson] = m.passRuleJson
                        row[curveJson] = m.curveJson; row[evidenceTier] = "official-site"; row[sourceUrl] = m.sourceUrl
                        row[notesJson] = m.notesJson; row[sweepRef] = m.sweepRef
                    }
                    updated++
                } else {
                    GradingModelsTable.insert { row ->
                        row[id] = m.id; row[subject] = m.subject; row[variant] = m.variant
                        row[isPrimary] = m.isPrimary; row[formula] = m.formula; row[maxTotal] = m.maxTotal
                        row[passRuleJson] = m.passRuleJson; row[curveJson] = m.curveJson
                        row[evidenceTier] = "official-site"; row[sourceUrl] = m.sourceUrl
                        row[notesJson] = m.notesJson; row[sweepRef] = m.sweepRef; row[createdAt] = now
                    }
                    inserted++
                }
            }

            // ── grade components ──
            for (c in COMPONENTS) {
                val exists = GradeComponentsTable.selectAll()
                    .where { GradeComponentsTable.id eq c.id }.any()
                if (exists) {
                    GradeComponentsTable.update({ GradeComponentsTable.id eq c.id }) { row ->
                        row[modelId] = c.modelId; row[name] = c.name; row[maxPoints] = c.maxPoints
                        row[weight] = c.weight; row[minGateJson] = c.minGateJson; row[reexamPolicy] = c.reexamPolicy
                        row[evidenceTier] = c.evidenceTier; row[sourceUrl] = c.sourceUrl
                        row[detailJson] = c.detailJson; row[position] = c.position
                    }
                    updated++
                } else {
                    GradeComponentsTable.insert { row ->
                        row[id] = c.id; row[modelId] = c.modelId; row[name] = c.name
                        row[maxPoints] = c.maxPoints; row[weight] = c.weight; row[minGateJson] = c.minGateJson
                        row[reexamPolicy] = c.reexamPolicy; row[evidenceTier] = c.evidenceTier
                        row[sourceUrl] = c.sourceUrl; row[detailJson] = c.detailJson; row[position] = c.position
                    }
                    inserted++
                }
            }

            // ── exam_schedule_rows (12) ──
            for (s in SCHEDULE) {
                val exists = ExamScheduleRowsTable.selectAll()
                    .where { ExamScheduleRowsTable.id eq s.id }.any()
                if (exists) {
                    ExamScheduleRowsTable.update({ ExamScheduleRowsTable.id eq s.id }) { row ->
                        row[subject] = s.subject; row[rawDiscipline] = s.rawDiscipline; row[examType] = s.examType
                        row[startAt] = s.startAt; row[endAt] = s.endAt; row[room] = s.room
                        row[datePrecision] = "exact"; row[sourceRef] = SCHED_REF
                    }
                    updated++
                } else {
                    ExamScheduleRowsTable.insert { row ->
                        row[id] = s.id; row[subject] = s.subject; row[rawDiscipline] = s.rawDiscipline
                        row[examType] = s.examType; row[startAt] = s.startAt; row[endAt] = s.endAt
                        row[room] = s.room; row[datePrecision] = "exact"; row[sourceRef] = SCHED_REF
                        row[createdAt] = now
                    }
                    inserted++
                }
            }

            // ── exam_dates: 4 primaries, frozen one-row-per-(user,subject) upsert ──
            for ((subjectCode, startInstant) in PRIMARY_EXAM_DATES) {
                val existingId = ExamDatesTable.selectAll()
                    .where { (ExamDatesTable.userId eq userId) and (ExamDatesTable.subject eq subjectCode) }
                    .map { it[ExamDatesTable.id] }
                    .firstOrNull()
                if (existingId == null) {
                    ExamDatesTable.insert { row ->
                        row[id] = TutorTypes.ulid()
                        row[ExamDatesTable.userId] = userId
                        row[subject] = subjectCode
                        row[startAt] = startInstant
                        row[createdAt] = now
                    }
                    inserted++
                } else {
                    ExamDatesTable.update({ ExamDatesTable.id eq existingId }) { row ->
                        row[startAt] = startInstant
                    }
                    updated++
                }
            }
        }
        return SeedReport(inserted = inserted, updated = updated, skipped = 0)
    }
}
```

NOTE on imports: the seeder uses `GradingModelsTable.id eq m.id` etc. The `eq` infix comes from `org.jetbrains.exposed.sql.SqlExpressionBuilder.eq`; `and` from `org.jetbrains.exposed.sql.and`. If the build flags either `eq`/`and` import as unused or unresolvable (Exposed version drift), match the import style already used in `SessionPlacementExamRoutes.kt` (which uses `eq`/`and` the same way) — open that file's import block and copy it verbatim. Do NOT change the `where { ... }` predicate logic.

- [ ] **Step 4: Run the seeder test — expect GREEN.**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.Plan2SeedTest"
```

Expected: `BUILD SUCCESSFUL`, 7 tests passed (incl. the Romanian-diacritics round-trip test).

- [ ] **Step 5: Wire the CLI subcommand into `Main.kt`** (exactly like `trust-export`/`trust-import` at `Main.kt:82-83`). Three edits:

Edit A — add a `runSeedKnowledgeMeta` helper above `fun main` (after `runMigrate`, mirroring its DB-path resolution + before/after reporting). The path precedence matches the seeder's runtime needs: `--db` flag, else `$JARVIS_TUTOR_DB`, else `jarvis.Config.tutorDbPath` (the same precedence `TrustSyncCli.connectDb` uses, `TrustSync.kt:136-141`):

```kotlin
/**
 * Plan-2 operator tool: seed the course-meta tables (grade models, components, exam schedule,
 * exam_dates primaries) from the 2026-06-11 verified sweep. Idempotent (upsert-by-id). Applies
 * TutorMigration first so the Plan-2 tables exist. DB path: `--db PATH`, else $JARVIS_TUTOR_DB,
 * else ~/.jarvis/tutor.db. Prints the SeedReport. NOTE on the live DB: migrate() over the 828-card
 * corpus fires the INV-3.1 backup gate (pending Plan-2 DDL) — run `python tools/db-backup.py` first.
 */
private fun runSeedKnowledgeMeta(args: List<String>) {
    val dbIdx = args.indexOf("--db")
    val dbPath = (if (dbIdx >= 0) args.getOrNull(dbIdx + 1) else null)
        ?: System.getenv("JARVIS_TUTOR_DB")
        ?: System.getProperty("JARVIS_TUTOR_DB")
        ?: jarvis.Config.tutorDbPath
    System.err.println("[seed-knowledge-meta] target DB = $dbPath")
    val db = jarvis.tutor.TutorDb.connect(dbPath)
    try {
        jarvis.tutor.TutorMigration.migrate(db)
    } catch (e: jarvis.tutor.MigrationException) {
        System.err.println("[seed-knowledge-meta] migration FAILED: ${e.message}")
        exitProcess(1)
    }
    val report = jarvis.tutor.Plan2Seed.seedKnowledgeMeta(db, java.time.Instant.now())
    println(
        "[seed-knowledge-meta] inserted=${report.inserted} updated=${report.updated} " +
            "skipped=${report.skipped} (applied=${report.applied})",
    )
}
```

Edit B — add the dispatch case to the `when (args.firstOrNull())` block, directly after the `"trust-import"` case (`Main.kt:83`):

```kotlin
        "seed-knowledge-meta" -> runSeedKnowledgeMeta(args.drop(1))
```

Edit C — add the usage line to the `USAGE` string. Insert it after the `trust-import` line (`Main.kt:22-23`). ALSO append `| seed-knowledge-meta` to the bracketed subcommand list on the FIRST `Usage: jarvis [...]` header line (`Main.kt:7`) so the summary line stays complete:

```
  seed-knowledge-meta [--db PATH]
                             Plan-2: seed grade-model registry + exam schedule + exam_dates
                             primaries from the 2026-06-11 verified sweep (idempotent).
```

- [ ] **Step 6: Confirm the CLI compiles + dispatches** (read-only — do NOT run against the live DB, which would fire the INV-3.1 gate). Run the subcommand against a fresh throwaway DB to prove wiring end-to-end:

```powershell
gradle --no-daemon installDist
$env:JARVIS_TUTOR_DB = "$env:TEMP\plan2-seed-smoke.db"
& build\install\jarvis-kotlin\bin\jarvis-kotlin seed-knowledge-meta
```

Expected stderr `[seed-knowledge-meta] target DB = ...plan2-seed-smoke.db` then — because the throwaway DB has zero users — the seeder's `require(userIds.size == 1)` throws `IllegalArgumentException: seedKnowledgeMeta expects exactly ONE user, found 0`. That is the CORRECT wired behavior (it proves dispatch + migrate ran; the user precondition fires before any seed). Clean up: `Remove-Item $env:TEMP\plan2-seed-smoke.db*`.

- [ ] **Step 7: Run the FULL suite.**

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit** (named files only — never `git add -A`):

```powershell
git add src/main/kotlin/jarvis/tutor/Plan2Seed.kt src/main/kotlin/jarvis/Main.kt src/test/kotlin/jarvis/tutor/Plan2SeedTest.kt
git commit -m "feat(seed): course-meta seeder (grade models + 12 exam-schedule rows + 4 exam_dates) + seed-knowledge-meta CLI"
```

## Task 8 — `kc_id` backfill mechanism (confident-match only) + CLI subcommand

The 828 live cards all carry `kc_id = NULL` (§0.5). This task adds a **pure** matcher
(`KcIdBackfill.computeLinks`) that links a card to a KC ONLY when their normalized
source-doc stems are EXACTLY equal (§0.8 #2 — no fuzzy/similarity scoring, no LLM), an
`applyLinks` writer that NEVER overwrites a non-null `kc_id`, and a `jarvis backfill-kc-ids`
CLI subcommand. The **honest production result today is 0 links** (PA cards reference
`lecture11_*` / `seminar9_10_*`; the 6 PA KCs are all sourced from `pa-lecture-01` — zero
overlap, §0.5). A 0-link run is SUCCESS, not failure — the CLI prints `0` and exits 0.

**Files:**
- Create: `src/main/kotlin/jarvis/tutor/KcIdBackfill.kt`
- Modify: `src/main/kotlin/jarvis/Main.kt` (new `backfill-kc-ids` subcommand + USAGE line)
- Test: `src/test/kotlin/jarvis/tutor/KcIdBackfillTest.kt` (new)

- [ ] **Step 1: Write the failing tests.** Create `src/test/kotlin/jarvis/tutor/KcIdBackfillTest.kt`:

```kotlin
package jarvis.tutor

import jarvis.content.KnowledgeConcept
import jarvis.content.SourceRef
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-2 Task 8 (spec §3.6 step 4 / §0.8 #2) — kc_id backfill is CONFIDENT-MATCH ONLY:
 * a link exists iff a card's normalized source-doc stem == a KC's normalized source[*].doc
 * stem. No fuzzy/similarity scoring. The production reality is 0 links (PA cards =
 * lecture11/seminar9_10; PA KCs = pa-lecture-01) — a 0-link run is the CORRECT result.
 * applyLinks NEVER overwrites a non-null kc_id.
 */
class KcIdBackfillTest {

    private fun kc(id: String, subject: String, docs: List<String>): KnowledgeConcept =
        KnowledgeConcept(
            id = id, subject = subject, name_ro = id, name_en = id, cluster = "c",
            bloom_level = "understand", difficulty = 1, time_minutes = 1, exam_weight = 0.0,
            tier = 1, source = docs.map { SourceRef(doc = it, quote = "q") },
        )

    private fun card(id: String, sourceRef: String) = KcIdBackfill.CardRef(id = id, sourceRef = sourceRef)

    @Test
    fun `exact normalized stem match links the card`() {
        // card source_ref "PA:lecture-01.md" normalizes to "pa lecture 01" == KC doc "pa-lecture-01"
        val links = KcIdBackfill.computeLinks(
            cards = listOf(card("c1", "PA:lecture-01.md")),
            kcs = listOf(kc("pa-kc-001", "PA", listOf("pa-lecture-01"))),
        )
        assertEquals(listOf(KcIdBackfill.Link(cardId = "c1", kcId = "pa-kc-001", subject = "PA")), links)
    }

    @Test
    fun `near miss does NOT link (lecture11 vs pa-lecture-01)`() {
        // This is the live PA reality: lecture11 normalizes to "lecture11", KC doc to "pa lecture 01".
        val links = KcIdBackfill.computeLinks(
            cards = listOf(card("c1", "PA:lecture11_en.md")),
            kcs = listOf(kc("pa-kc-001", "PA", listOf("pa-lecture-01"))),
        )
        assertEquals(emptyList(), links)
    }

    @Test
    fun `SO and RC prefixes both map to subject SO-RC for KC lookup`() {
        val kcs = listOf(kc("sorc-kc-1", "SO-RC", listOf("so-rc-lecture-03")))
        val soLink = KcIdBackfill.computeLinks(listOf(card("c-so", "SO:so-rc-lecture-03.md")), kcs)
        val rcLink = KcIdBackfill.computeLinks(listOf(card("c-rc", "RC:so-rc-lecture-03.md")), kcs)
        assertEquals(listOf(KcIdBackfill.Link("c-so", "sorc-kc-1", "SO-RC")), soLink)
        assertEquals(listOf(KcIdBackfill.Link("c-rc", "sorc-kc-1", "SO-RC")), rcLink)
    }

    @Test
    fun `a card only links to a KC of the SAME subject`() {
        // PA card stem matches a POO KC's doc stem by coincidence -> still NO link (subject differs).
        val links = KcIdBackfill.computeLinks(
            cards = listOf(card("c1", "PA:shared-doc.md")),
            kcs = listOf(kc("poo-kc-1", "POO", listOf("shared-doc"))),
        )
        assertEquals(emptyList(), links)
    }

    @Test
    fun `applyLinks sets kc_id and never overwrites a non-null kc_id`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertCard(db, id = "c-null", kcId = null)
        insertCard(db, id = "c-set", kcId = "already-linked")

        val applied = KcIdBackfill.applyLinks(
            db,
            listOf(
                KcIdBackfill.Link("c-null", "pa-kc-001", "PA"),
                KcIdBackfill.Link("c-set", "pa-kc-999", "PA"), // must NOT overwrite "already-linked"
            ),
        )

        assertEquals(1, applied, "only the null-kc_id card is written")
        assertEquals("pa-kc-001", kcIdOf(db, "c-null"))
        assertEquals("already-linked", kcIdOf(db, "c-set"))
    }

    @Test
    fun `production reality (lecture11 vs pa-lecture-01) yields zero links`() {
        val links = KcIdBackfill.computeLinks(
            cards = listOf(
                card("c1", "PA:lecture11_en.md"),
                card("c2", "PA:seminar9_10_np.md"),
                card("c3", "PA:lecture9_10_np.md"),
            ),
            kcs = listOf(
                kc("pa-kc-001", "PA", listOf("pa-lecture-01")),
                kc("pa-kc-006", "PA", listOf("pa-lecture-01")),
            ),
        )
        assertTrue(links.isEmpty(), "today's corpus has ZERO confident overlap (the honest 0-link result)")
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────
    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("backfill.db").toString())
        TutorMigration.migrate(db) // fresh DB: the INV-3.1 gate self-skips (0 protected rows)
        return db
    }

    private fun insertCard(db: Database, id: String, kcId: String?) {
        transaction(db) {
            // a real owner user is required by the FK; insert one if absent.
            // DSL insert against the REAL users columns (Users.kt:25-32: id, name, scope,
            // created_at, last_seen_at, email, lang — there is NO display_name column);
            // guarded for idempotency across multiple insertCard calls in one test.
            val ownerExists = UsersTable.selectAll()
                .where { UsersTable.id eq "owner" }.count() > 0
            if (!ownerExists) {
                UsersTable.insert {
                    it[UsersTable.id] = "owner"
                    it[name] = "owner"
                    it[scope] = "OWNER"
                    it[createdAt] = Instant.parse("2026-06-11T00:00:00Z")
                    it[lastSeenAt] = Instant.parse("2026-06-11T00:00:00Z")
                    it[email] = "o@x"
                    // lang keeps its default 'ro'
                }
            }
            FsrsCardsTable.insert {
                it[FsrsCardsTable.id] = id
                it[userId] = "owner"
                it[sourceType] = "MANUAL"
                it[sourceRef] = "PA:lecture11_en.md"
                it[front] = "f"; it[back] = "b"
                it[difficulty] = 5.0; it[stability] = 0.5; it[retrievability] = 1.0
                it[dueAt] = Instant.now(); it[lastReviewedAt] = Instant.now(); it[lapses] = 0
                it[FsrsCardsTable.kcId] = kcId
                it[status] = "ACTIVE"
            }
        }
    }

    private fun kcIdOf(db: Database, cardId: String): String? = transaction(db) {
        FsrsCardsTable.selectAll().where { FsrsCardsTable.id eq cardId }.single()[FsrsCardsTable.kcId]
    }
}
```

- [ ] **Step 2: Run the tests — expect RED** (compile failure: `KcIdBackfill` unresolved):

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.KcIdBackfillTest"
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: KcIdBackfill`.

- [ ] **Step 3: Implement `KcIdBackfill.kt`.** Create `src/main/kotlin/jarvis/tutor/KcIdBackfill.kt`:

```kotlin
package jarvis.tutor

import jarvis.content.KnowledgeConcept
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Plan-2 Task 8 (spec §3.6 step 4) — the fsrs_cards.kc_id backfill: link an existing card to a KC
 * ONLY when their source documents provably match. The match is EXACT normalized-stem equality
 * (§0.8 #2) — no fuzzy/similarity scoring, no LLM guessing. Cards that cannot be confidently linked
 * stay NULL-linked and keep scheduling independently ("no forced guesses").
 *
 * Production reality (verified 2026-06-11): 0 links — PA cards reference lecture11_*/seminar9_10_*,
 * the 6 PA KCs are all sourced from `pa-lecture-01`; zero confident overlap. A 0-link run is the
 * CORRECT result, not a failure.
 */
object KcIdBackfill {

    /** A card's identity + its raw `source_ref` (pattern `SUBJECT:filename.md`). */
    data class CardRef(val id: String, val sourceRef: String)

    /** One confident link: this card belongs to this KC (of this subject). */
    data class Link(val cardId: String, val kcId: String, val subject: String)

    /**
     * Map a card-source prefix to the tutor subject id used for KC lookup. `SO:` and `RC:` BOTH map
     * to the `SO-RC` subject (subjects.yaml id; §0.5). Every other prefix maps to itself.
     */
    fun subjectOfPrefix(prefix: String): String = when (prefix) {
        "SO", "RC" -> "SO-RC"
        else -> prefix
    }

    /**
     * Normalize a source-doc identifier to its comparable stem:
     *   strip a `SUBJECT:` prefix (everything up to and including the first ':'),
     *   strip a file extension (everything from the last '.'),
     *   lowercase,
     *   collapse every run of [-_ ] into a single space, then trim.
     *
     * Examples (§0.8 #2):
     *   "PA:lecture11_en.md" -> "lecture11 en"
     *   "pa-lecture-01"      -> "pa lecture 01"
     */
    fun normalizeDocStem(raw: String): String {
        val afterPrefix = raw.substringAfter(':', raw) // no ':' -> unchanged
        val noExt = afterPrefix.substringBeforeLast('.', afterPrefix)
        return noExt.lowercase()
            .replace(Regex("[-_ ]+"), " ")
            .trim()
    }

    /** The `SUBJECT` prefix of a card `source_ref` ("PA:lecture11_en.md" -> "PA"); "" if no ':'. */
    private fun prefixOf(sourceRef: String): String =
        if (':' in sourceRef) sourceRef.substringBefore(':') else ""

    /**
     * Pure. A link exists iff (a) the card's prefix maps to the KC's subject AND (b) the card's
     * normalized source-doc stem equals one of the KC's normalized `source[*].doc` stems.
     * Deterministic order: input-card order, then KC order. First matching KC per card wins (a card
     * gets at most one link).
     */
    fun computeLinks(cards: List<CardRef>, kcs: List<KnowledgeConcept>): List<Link> {
        // index normalized KC doc stems by subject -> stem -> kcId (first KC wins for a stem)
        data class Key(val subject: String, val stem: String)
        val bySubjectStem = LinkedHashMap<Key, String>()
        for (kc in kcs) {
            for (ref in kc.source) {
                val key = Key(kc.subject, normalizeDocStem(ref.doc))
                bySubjectStem.putIfAbsent(key, kc.id)
            }
        }
        val links = ArrayList<Link>()
        for (card in cards) {
            val subject = subjectOfPrefix(prefixOf(card.sourceRef))
            val stem = normalizeDocStem(card.sourceRef)
            val kcId = bySubjectStem[Key(subject, stem)] ?: continue
            links += Link(cardId = card.id, kcId = kcId, subject = subject)
        }
        return links
    }

    /**
     * Apply links to fsrs_cards: `UPDATE fsrs_cards SET kc_id=? WHERE id=? AND kc_id IS NULL`.
     * NEVER overwrites a non-null kc_id (the RUBRIC_CRITERION migration backfill already stamped
     * some; this is additive). Returns the count actually written. Idempotent: a second run re-finds
     * no NULL rows for the already-linked ids and writes 0.
     */
    fun applyLinks(db: Database, links: List<Link>): Int = transaction(db) {
        var applied = 0
        for (link in links) {
            // Exposed DSL update: returns the affected-row count directly, no string-built SQL,
            // no connection-scoped changes() readback. WHERE kc_id IS NULL = never-overwrite.
            applied += FsrsCardsTable.update(
                { (FsrsCardsTable.id eq link.cardId) and FsrsCardsTable.kcId.isNull() },
            ) { it[kcId] = link.kcId }
        }
        applied
    }
}
```

Required imports for `applyLinks`: `org.jetbrains.exposed.sql.update`, `org.jetbrains.exposed.sql.and`, `org.jetbrains.exposed.sql.SqlExpressionBuilder.eq`.

> **Implementer note:** the DSL `update` form above is MANDATORY — do not substitute raw
> `exec("UPDATE ...")` (Exposed's `exec(sql, transform)` treats the statement as a query; an UPDATE
> yields no ResultSet and the affected-count readback breaks). Do NOT change the `WHERE kc_id IS NULL`
> never-overwrite semantics. This task NEVER touches `ContentReconcile`, `kcContentHashOf`,
> `TrustSync.VerdictRow`, or `VerificationGate` — if a change seems to need any of those, STOP and
> escalate to the PM.

- [ ] **Step 4: Run the tests — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.KcIdBackfillTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 5: Wire the `backfill-kc-ids` CLI subcommand into `Main.kt`.**

Edit A — add a CLI runner function. Insert it directly after `runMigrate` (after its closing
`}` at `Main.kt:59`):

```kotlin
/**
 * Plan-2 Task 8 — `jarvis backfill-kc-ids [--db PATH] [--dry-run]`. Confident-match-only kc_id
 * backfill: links each card to a KC iff their normalized source-doc stems are EXACTLY equal
 * (KcIdBackfill). Prints per-subject link counts + the total. `--dry-run` computes + prints but
 * writes NOTHING. The honest result on today's corpus is 0 links (PA cards = lecture11/seminar;
 * PA KCs = pa-lecture-01) — a 0 total is SUCCESS, exit 0.
 */
private fun runBackfillKcIds(args: List<String>) {
    val dryRun = "--dry-run" in args
    val dbIdx = args.indexOf("--db")
    val dbPath = (if (dbIdx >= 0) args.getOrNull(dbIdx + 1) else null)
        ?: System.getenv("JARVIS_TUTOR_DB")
        ?: System.getProperty("JARVIS_TUTOR_DB")
        ?: jarvis.Config.tutorDbPath
    val contentDir = java.nio.file.Path.of(
        System.getenv("JARVIS_CONTENT_DIR")?.takeIf { it.isNotBlank() } ?: "content",
    )
    System.err.println("[backfill-kc-ids] db=$dbPath content=$contentDir dryRun=$dryRun")

    val db = jarvis.tutor.TutorDb.connect(dbPath)
    jarvis.tutor.TutorMigration.migrate(db)

    // read all cards (id, source_ref) read-only
    val cards = org.jetbrains.exposed.sql.transactions.transaction(db) {
        val out = ArrayList<jarvis.tutor.KcIdBackfill.CardRef>()
        exec("SELECT id, source_ref FROM fsrs_cards") { rs ->
            while (rs.next()) out.add(jarvis.tutor.KcIdBackfill.CardRef(rs.getString(1), rs.getString(2)))
        }
        out
    }

    // load every KC from content/
    val repo = jarvis.content.ContentRepo(contentDir)
    val kcs = repo.loadManifest().subjects.flatMap { repo.loadSubject(it.id).kcs }

    val links = jarvis.tutor.KcIdBackfill.computeLinks(cards, kcs)
    val perSubject = links.groupingBy { it.subject }.eachCount().toSortedMap()

    val applied = if (dryRun) 0 else jarvis.tutor.KcIdBackfill.applyLinks(db, links)

    println("[backfill-kc-ids] candidate links: ${links.size} (cards=${cards.size}, kcs=${kcs.size})")
    if (perSubject.isEmpty()) {
        println("[backfill-kc-ids] per-subject: (none — 0 confident source-doc overlaps, the honest result)")
    } else {
        for ((subject, n) in perSubject) println("[backfill-kc-ids]   $subject: $n")
    }
    println(
        if (dryRun) "[backfill-kc-ids] DRY-RUN — wrote 0 rows (total candidate links=${links.size})"
        else "[backfill-kc-ids] applied $applied link(s) (skipped ${links.size - applied} already-linked)",
    )
}
```

Edit B — route the subcommand. In the `when (args.firstOrNull())` block, add this case right
after the `"migrate" -> runMigrate(args.getOrNull(1))` line (`Main.kt:81`):

```kotlin
        "backfill-kc-ids" -> runBackfillKcIds(args.drop(1))
```

Edit C — document it in `USAGE` (and append `| backfill-kc-ids` to the bracketed subcommand list on the FIRST `Usage: jarvis [...]` header line, `Main.kt:7`). In the `USAGE` string, add this block right after the
`trust-import <in.json>` lines (after `Main.kt:23`):

```kotlin
  backfill-kc-ids [--db PATH] [--dry-run]
                             Plan-2: link fsrs_cards to KCs by EXACT normalized source-doc
                             stem (confident-match only; no fuzzy/LLM). Prints per-subject
                             link counts + total. 0 links is the honest result today.
```

- [ ] **Step 6: Build to confirm Main.kt compiles, then dry-run against a temp copy of the live DB.**
First a compile check, then a real dry-run on a COPY (never the live DB directly for the rehearsal):

```powershell
gradle --no-daemon :compileKotlin
Copy-Item "$env:USERPROFILE\.jarvis\tutor.db" "$env:TEMP\backfill-rehearsal.db" -Force
$env:JARVIS_TUTOR_DB = "$env:TEMP\backfill-rehearsal.db"
gradle --no-daemon run --args="backfill-kc-ids --dry-run"
```

Expected: `BUILD SUCCESSFUL`; the run prints
`[backfill-kc-ids] candidate links: 0 (cards=828, kcs=8)` and
`[backfill-kc-ids] per-subject: (none — 0 confident source-doc overlaps, the honest result)` and
`[backfill-kc-ids] DRY-RUN — wrote 0 rows (total candidate links=0)`. (The real apply runs in the
gated Task 11/12 operational steps, NOT here.) A non-zero candidate count here means a source-doc
overlap appeared since recon — STOP and report it (do not apply silently).

- [ ] **Step 7: Run the full backend test suite — expect GREEN (no regression):**

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit** (explicit paths only — NEVER `git add -A`):

```powershell
git add src/main/kotlin/jarvis/tutor/KcIdBackfill.kt src/test/kotlin/jarvis/tutor/KcIdBackfillTest.kt src/main/kotlin/jarvis/Main.kt
git commit -m "feat(backfill): kc_id confident-match backfill (exact normalized stem) + backfill-kc-ids CLI"
```

---

## Task 9 — `trustInvariants` extensions: INV-3.2 / INV-3.3-satisfiable / INV-3.4 + new-table SCHEMA pins

Extend `TrustInvariantsCli.check()` (the existing `failures: ArrayList<Failure>` accumulator pattern,
`TrustInvariantsCli.kt:30-104`) with five new check blocks. These run owner-side over the REAL DB +
the REAL content corpus (`gradle trustInvariants`); they are READ-ONLY. The existing INV-3.3 /
SCHEMA / FLIP / PARITY blocks stay untouched. Each new failure CLASS gets a unit test that seeds the
DB/corpus into BOTH green and red — a check that cannot be shown red does not exist (fix-claim
discipline).

**Files:**
- Modify: `src/main/kotlin/jarvis/tutor/verify/TrustInvariantsCli.kt`
- Test: `src/test/kotlin/jarvis/tutor/verify/TrustInvariantsPlan2Test.kt` (new)

> **Anchor reality (read before editing):** `check()` already loads the corpus for PARITY via
> `ContentRepo(contentDir)` and reads columns via `Transaction.columnsOf(table)`
> (`TrustInvariantsCli.kt:107-111`). The new blocks REUSE those. `KnowledgeConcept` gained
> `concept_type: String?` (Task 1) and `beats: Map<String, KcBeats>` (Task 2); `ConceptType.fromWire`
> and `KcBeats.isCompleteFor(conceptType)` exist (§0.5 A/B). The 7 Plan-2 tables (Task 5/6) are in
> `ALL_TABLES`. This task NEVER touches `claimsFor` / `kcContentHashOf` / `VerdictRow` /
> `VerificationGate` — if it seems to, STOP and escalate.

- [ ] **Step 1: Write the failing tests.** Create
`src/test/kotlin/jarvis/tutor/verify/TrustInvariantsPlan2Test.kt`:

```kotlin
package jarvis.tutor.verify

import jarvis.content.ConceptType
import jarvis.content.ContentRepo
import jarvis.content.KcBeats
import jarvis.content.KnowledgeConcept
import jarvis.tutor.ExamScheduleRowsTable
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.GradingModelsTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plan-2 Task 9 — the new trustInvariants legs: INV-3.2a (concept_type), INV-3.2b (beat
 * completeness for AUTHORED beats only), INV-3.3-link (satisfiable form), INV-3.4 (grade-model
 * + schedule provenance), and the SCHEMA pins for the 7 new tables. Each failure class is shown
 * BOTH green and red. Uses the REAL in-repo corpus (content/) for the green case (gradle test
 * workingDir = project root) so it exercises the exact ContentRepo path the serve gate uses.
 */
class TrustInvariantsPlan2Test {

    private val contentDir: Path = Path.of("content")

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("inv2.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    // Minimal valid grade-model + 12 schedule rows so INV-3.4 is GREEN unless a test breaks one.
    private fun seedGradeModels(db: Database, sourceUrl: String = "https://edu.info.uaic.ro/x") {
        transaction(db) {
            GradingModelsTable.insert {
                it[id] = "gm-x"; it[subject] = "ALO"; it[variant] = null; it[isPrimary] = true
                it[formula] = "F"; it[maxTotal] = 800.0; it[passRuleJson] = "{}"
                it[evidenceTier] = "official-site"; it[sourceUrl] = sourceUrl
                it[sweepRef] = "verified-grade-models-exam-schedule.md#alo"
                it[createdAt] = Instant.now()
            }
        }
    }

    private val sweepAnchor =
        "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"

    private fun seedSchedule(db: Database, count: Int = 12, sourceRef: String = sweepAnchor) {
        transaction(db) {
            repeat(count) { i ->
                ExamScheduleRowsTable.insert {
                    it[id] = "es-$i"; it[subject] = "ALO"; it[rawDiscipline] = "d"
                    it[examType] = "examen"; it[startAt] = Instant.now(); it[datePrecision] = "exact"
                    it[ExamScheduleRowsTable.sourceRef] = sourceRef; it[createdAt] = Instant.now()
                }
            }
        }
    }

    @Test
    fun `INV-3-2a green on the real corpus once concept_type is backfilled`(@TempDir tmp: Path) {
        // Task 4 backfills concept_type onto all 8 PA KCs; this asserts the real corpus passes.
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.none { it.check == "INV-3.2a" }, "$failures")
    }

    @Test
    fun `INV-3-2a red when a KC has a null concept_type`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val kcs = listOf(kc("k1", conceptType = null))
        val failures = TrustInvariantsCli.checkKcs(kcs)
        assertTrue(failures.any { it.check == "INV-3.2a" && it.detail.contains("k1") }, "$failures")
    }

    @Test
    fun `INV-3-2a red when a KC has an unknown concept_type literal`(@TempDir tmp: Path) {
        val failures = TrustInvariantsCli.checkKcs(listOf(kc("k1", conceptType = "not-a-type")))
        assertTrue(failures.any { it.check == "INV-3.2a" && it.detail.contains("k1") }, "$failures")
    }

    @Test
    fun `INV-3-2b red when an AUTHORED ro beat is incomplete`() {
        // beats non-empty but ro incomplete (predict missing) -> fail
        val k = kc("k1", conceptType = "procedure").copy(beats = mapOf("ro" to KcBeats()))
        val failures = TrustInvariantsCli.checkKcs(listOf(k))
        assertTrue(failures.any { it.check == "INV-3.2b" && it.detail.contains("k1") }, "$failures")
    }

    @Test
    fun `INV-3-2b GREEN when beats are EMPTY (beat-serving not live in Plan 2)`() {
        val k = kc("k1", conceptType = "procedure") // beats default emptyMap()
        val failures = TrustInvariantsCli.checkKcs(listOf(k))
        assertTrue(failures.none { it.check == "INV-3.2b" }, "empty beats must NOT fail in Plan 2: $failures")
    }

    @Test
    fun `INV-3-4 red when a grade model has a blank source_url`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db, sourceUrl = ""); seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("source_url") }, "$failures")
    }

    @Test
    fun `INV-3-4 red when the schedule row count is not 12`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db, count = 11)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("12") }, "$failures")
    }

    @Test
    fun `INV-3-4 red when a schedule row sourceRef is not the sweep anchor`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db)
        seedSchedule(db, count = 12, sourceRef = "some-other-doc.md")
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("sourceRef") }, "$failures")
    }

    @Test
    fun `INV-3-4 red when two grade models are is_primary for one subject`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db)
        transaction(db) {
            GradingModelsTable.insert {
                it[id] = "gm-x2"; it[subject] = "ALO"; it[variant] = "v2"; it[isPrimary] = true
                it[formula] = "F2"; it[passRuleJson] = "{}"; it[evidenceTier] = "official-site"
                it[sourceUrl] = "https://edu.info.uaic.ro/x"
                it[sweepRef] = "verified-grade-models-exam-schedule.md#alo"; it[createdAt] = Instant.now()
            }
        }
        seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.4" && it.detail.contains("is_primary") }, "$failures")
    }

    @Test
    fun `SCHEMA pins green for the 7 new tables on a fully-migrated DB`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.none { it.check == "SCHEMA" }, "$failures")
    }

    // ── INV-3.3-link: both green and red (fix-claim discipline — a check that cannot be shown
    //    red does not exist). Needs a TEMP corpus whose KC source doc OVERLAPS a card source_ref
    //    (the real content/ has zero overlap, so it can only ever show the vacuous-green path).
    private fun writeOverlapCorpus(tmp: Path): Path {
        val root = tmp.resolve("corpus"); root.createDirectories()
        root.resolve("subjects.yaml").writeText(
            """
            version: 1
            subjects:
              - id: PA
                name_ro: "Proiectarea Algoritmilor"
                name_en: "Algorithm Design"
            """.trimIndent(),
        )
        val kcs = root.resolve("PA").resolve("kcs"); kcs.createDirectories()
        // KC doc 'lecture_x' and card ref 'PA:lecture-x.md' both normalize to stem "lecture x"
        kcs.resolve("pa-kc-t1.yaml").writeText(
            """
            id: pa-kc-t1
            subject: PA
            name_ro: "KC de test"
            name_en: "Test KC"
            cluster: "c"
            bloom_level: understand
            difficulty: 1
            time_minutes: 1
            exam_weight: 0.0
            tier: 1
            concept_type: definition-taxonomy
            source:
              - doc: lecture_x
                quote: "citat de test"
            """.trimIndent(),
        )
        return root
    }

    private fun insertCardWithUser(db: Database, cardId: String, sourceRef: String, kcId: String?) {
        transaction(db) {
            if (UsersTable.selectAll().where { UsersTable.id eq "owner" }.count() == 0L) {
                UsersTable.insert {
                    it[UsersTable.id] = "owner"; it[name] = "owner"; it[scope] = "OWNER"
                    it[createdAt] = Instant.now(); it[lastSeenAt] = Instant.now(); it[email] = "o@x"
                }
            }
            FsrsCardsTable.insert {
                it[FsrsCardsTable.id] = cardId
                it[userId] = "owner"; it[sourceType] = "MANUAL"
                it[FsrsCardsTable.sourceRef] = sourceRef
                it[front] = "f"; it[back] = "b"
                it[difficulty] = 5.0; it[stability] = 0.5; it[retrievability] = 1.0
                it[dueAt] = Instant.now(); it[lastReviewedAt] = Instant.now(); it[lapses] = 0
                it[FsrsCardsTable.kcId] = kcId; it[status] = "ACTIVE"
            }
        }
    }

    @Test
    fun `INV-3-3-link red when a subject HAS source-doc overlap but zero linked cards`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val corpus = writeOverlapCorpus(tmp)
        insertCardWithUser(db, "card-1", "PA:lecture-x.md", kcId = null) // overlap exists, link missing
        val failures = TrustInvariantsCli.check(db, corpus, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "INV-3.3-link" && it.detail.contains("PA") }, "$failures")
    }

    @Test
    fun `INV-3-3-link green when the overlapping card IS linked`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        seedGradeModels(db); seedSchedule(db)
        val corpus = writeOverlapCorpus(tmp)
        insertCardWithUser(db, "card-1", "PA:lecture-x.md", kcId = "pa-kc-t1")
        val failures = TrustInvariantsCli.check(db, corpus, backupDir = null, floor = 0)
        assertTrue(failures.none { it.check == "INV-3.3-link" }, "$failures")
    }

    private fun kc(id: String, conceptType: String?): KnowledgeConcept =
        KnowledgeConcept(
            id = id, subject = "PA", name_ro = id, name_en = id, cluster = "c",
            bloom_level = "understand", difficulty = 1, time_minutes = 1, exam_weight = 0.0,
            tier = 1, concept_type = conceptType,
        )
}
```

> **Note on the test seam `checkKcs`:** the per-KC INV-3.2 legs (a + b) are extracted into a pure
> helper `TrustInvariantsCli.checkKcs(kcs: List<KnowledgeConcept>): List<Failure>` so they are
> unit-testable WITHOUT a DB; `check()` calls it after loading the corpus. The DB-backed legs
> (INV-3.3-link, INV-3.4, SCHEMA) are tested through the full `check()` entry point.

- [ ] **Step 2: Run the tests — expect RED** (compile failure: `checkKcs` unresolved + the new
checks don't exist yet so the red-case assertions can't pass):

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustInvariantsPlan2Test"
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: checkKcs`.

- [ ] **Step 3: Implement the new check blocks.** In `TrustInvariantsCli.kt`:

Edit A — add the pure per-KC helper. Insert it inside `object TrustInvariantsCli`, directly after
the `data class Failure(...)` line (`TrustInvariantsCli.kt:28`):

```kotlin
    /**
     * Pure, DB-free per-KC trust legs (INV-3.2a + INV-3.2b). Extracted so they unit-test without a DB.
     * INV-3.2a: every KC has a non-null concept_type that is a valid ConceptType wire literal.
     * INV-3.2b: for every KC with NON-EMPTY beats, beats["ro"] (the sole user's language) passes
     *           isCompleteFor(conceptType). KCs with EMPTY beats are NOT failures in Plan 2 — beat
     *           serving starts in Plan 3, so completeness is enforced only for AUTHORED beats.
     */
    fun checkKcs(kcs: List<jarvis.content.KnowledgeConcept>): List<Failure> {
        val failures = ArrayList<Failure>()

        // INV-3.2a — concept_type present + a valid wire literal.
        val badType = kcs.filter { it.concept_type == null || jarvis.content.ConceptType.fromWire(it.concept_type!!) == null }
        if (badType.isNotEmpty()) {
            failures += Failure(
                "INV-3.2a",
                "KC(s) with missing/invalid concept_type: " +
                    badType.joinToString(", ") { "${it.id}=${it.concept_type ?: "<null>"}" },
            )
        }

        // INV-3.2b — beat-completeness for AUTHORED beats only (beat-serving not live; Plan 3).
        val incomplete = ArrayList<String>()
        for (kc in kcs) {
            if (kc.beats.isEmpty()) continue // EMPTY beats are allowed (pre-digestion KC)
            val type = jarvis.content.ConceptType.fromWire(kc.concept_type ?: "") ?: continue // 3.2a already flagged it
            val ro = kc.beats["ro"] ?: run {
                incomplete += "${kc.id}(no ro beats)"; continue
            }
            if (!ro.isCompleteFor(type)) incomplete += kc.id
        }
        if (incomplete.isNotEmpty()) {
            failures += Failure(
                "INV-3.2b",
                "authored ro beats incomplete (beat-serving not live; completeness enforced for " +
                    "authored beats only): ${incomplete.joinToString(", ")}",
            )
        }
        return failures
    }
```

Edit B — call the per-KC legs + add the INV-3.3-link / INV-3.4 / new-table SCHEMA blocks. Insert
this block in `check()` immediately BEFORE the final `return failures` line
(`TrustInvariantsCli.kt:104`). Note `check()` already builds `kcsById` inside the PARITY block, but
only when `faithfulRows` is non-empty; load the corpus independently here so the new legs always run:

```kotlin
        // ── Plan-2 legs (INV-3.2 / INV-3.3-link / INV-3.4) ───────────────────────────────
        // Load the FULL corpus (the PARITY block only loads it when faithfulRows is non-empty).
        val repo2 = ContentRepo(contentDir)
        val allKcs = repo2.loadManifest().subjects.flatMap { repo2.loadSubject(it.id).kcs }

        // INV-3.2a + INV-3.2b (pure, per-KC)
        failures += checkKcs(allKcs)

        // INV-3.3-link (satisfiable form, §0.8 #2): for each subject, IF any KC source-doc stem
        // matches any card source_ref stem, THEN >= 1 card of that subject must have a non-null
        // kc_id. Subjects with zero source-doc overlap are vacuously satisfied (the honest 0-link
        // reality today). Uses the SAME normalization as KcIdBackfill (single source of truth).
        run {
            data class CardRow(val sourceRef: String, val kcId: String?)
            val cards = ArrayList<CardRow>()
            runCatching {
                transaction(db) {
                    exec("SELECT source_ref, kc_id FROM fsrs_cards") { rs ->
                        while (rs.next()) cards.add(CardRow(rs.getString(1), rs.getString(2)))
                    }
                }
            }
            // group cards by their mapped subject; compute the normalized stem set per subject
            val cardsBySubject = cards.groupBy {
                jarvis.tutor.KcIdBackfill.subjectOfPrefix(
                    if (':' in it.sourceRef) it.sourceRef.substringBefore(':') else "",
                )
            }
            val kcsBySubject = allKcs.groupBy { it.subject }
            for ((subject, subjectKcs) in kcsBySubject) {
                val subjectCards = cardsBySubject[subject].orEmpty()
                if (subjectCards.isEmpty()) continue
                val kcStems = subjectKcs.flatMap { it.source }
                    .map { jarvis.tutor.KcIdBackfill.normalizeDocStem(it.doc) }.toSet()
                val anyOverlap = subjectCards.any {
                    jarvis.tutor.KcIdBackfill.normalizeDocStem(it.sourceRef) in kcStems
                }
                if (anyOverlap && subjectCards.none { it.kcId != null }) {
                    failures += Failure(
                        "INV-3.3-link",
                        "$subject has a KC/card source-doc overlap but NO card with a non-null kc_id " +
                            "(run `jarvis backfill-kc-ids`)",
                    )
                }
            }
        }

        // INV-3.4: grade-model + exam-schedule provenance (§3.5 / §0.8 #1).
        run {
            data class Gm(val id: String, val subject: String, val sourceUrl: String, val isPrimary: Boolean)
            val gms = ArrayList<Gm>()
            runCatching {
                transaction(db) {
                    exec("SELECT id, subject, source_url, is_primary FROM grading_models") { rs ->
                        while (rs.next()) {
                            gms.add(Gm(rs.getString(1), rs.getString(2), rs.getString(3) ?: "", rs.getBoolean(4)))
                        }
                    }
                }
            }.onFailure {
                failures += Failure("INV-3.4", "grading_models not queryable: ${it.message?.take(120)}")
            }
            // every grade model has a non-empty source_url
            for (gm in gms) {
                if (gm.sourceUrl.isBlank()) failures += Failure("INV-3.4", "grading_models ${gm.id} has a blank source_url")
            }
            // at most one is_primary per subject
            gms.filter { it.isPrimary }.groupBy { it.subject }.forEach { (subject, primaries) ->
                if (primaries.size > 1) {
                    failures += Failure(
                        "INV-3.4",
                        "subject $subject has ${primaries.size} is_primary grade models (max 1): " +
                            primaries.joinToString(", ") { it.id },
                    )
                }
            }
            // exam_schedule_rows: count == 12 AND every sourceRef == the frozen sweep anchor
            val sweepAnchor =
                "docs/superpowers/findings/2026-06-11-verified-grade-models-exam-schedule.md#ia12-schedule"
            val refs = ArrayList<String>()
            runCatching {
                transaction(db) {
                    exec("SELECT source_ref FROM exam_schedule_rows") { rs ->
                        while (rs.next()) refs.add(rs.getString(1))
                    }
                }
            }.onFailure {
                failures += Failure("INV-3.4", "exam_schedule_rows not queryable: ${it.message?.take(120)}")
            }
            if (refs.size != 12) failures += Failure("INV-3.4", "exam_schedule_rows count=${refs.size} expected 12")
            val offAnchor = refs.filter { it != sweepAnchor }
            if (offAnchor.isNotEmpty()) {
                failures += Failure(
                    "INV-3.4",
                    "${offAnchor.size} exam_schedule_rows sourceRef != the sweep anchor ($sweepAnchor)",
                )
            }
        }

        // SCHEMA pins for the 7 new Plan-2 tables (same PRAGMA table_info pattern as expectedKvs).
        run {
            val newTableCols: Map<String, Set<String>> = mapOf(
                "problems" to setOf(
                    "id", "subject", "archetype", "statement_json", "parameter_slots_json",
                    "solution_present", "solution_json", "exam_language", "exam_language_constraints_json",
                    "data_files_json", "source_doc", "source_page", "provenance", "synthetic_tag",
                    "created_at", "updated_at",
                ),
                "problem_rubric_items" to setOf(
                    "id", "problem_id", "label", "points", "kind", "all_or_nothing",
                    "penalty_rules_json", "position",
                ),
                "problem_kc_links" to setOf("problem_id", "kc_id"),
                "grading_models" to setOf(
                    "id", "subject", "variant", "is_primary", "formula", "max_total", "pass_rule_json",
                    "curve_json", "evidence_tier", "source_url", "notes_json", "sweep_ref", "created_at",
                ),
                "grade_components" to setOf(
                    "id", "model_id", "name", "max_points", "weight", "min_gate_json", "reexam_policy",
                    "evidence_tier", "source_url", "detail_json", "position",
                ),
                "glossary_terms" to setOf("id", "subject", "term_en", "term_ro", "source_doc", "created_at"),
                "exam_schedule_rows" to setOf(
                    "id", "subject", "raw_discipline", "exam_type", "start_at", "end_at", "room",
                    "date_precision", "source_ref", "created_at",
                ),
            )
            for ((table, expected) in newTableCols) {
                val actual = transaction(db) { columnsOf(table) }.toSet()
                if (actual != expected) {
                    failures += Failure("SCHEMA", "$table=$actual expected=$expected")
                }
            }
        }
```

> **Implementer guard:** the SCHEMA column-set literals above MUST be copied VERBATIM from the
> `Plan2Tables.kt` table definitions (§0.5 E). If a column name differs, fix the assertion to match
> the canonical DDL — do NOT invent a different field name. This block NEVER reads or recomputes
> `content_hash` / `claimsFor` / a `VerdictRow` — it is pure schema introspection.

- [ ] **Step 4: Run the new tests — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustInvariantsPlan2Test"
```

Expected: `BUILD SUCCESSFUL`, all tests passed.

- [ ] **Step 5: Run the EXISTING `TrustInvariantsTest` — expect GREEN (no regression to the
original 4 legs):**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustInvariantsTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed. (Those tests pass `floor=0` and seed faithful rows;
they do NOT seed grade models or schedule rows, so the new INV-3.4 legs WILL surface failures —
**check the test expectations**: `TrustInvariantsTest` asserts on specific check names with
`failures.any { it.check == "X" }`, not `assertEquals(emptyList(), failures)`. The one exception is
`all green when a faithful row carries the recomputed corpus hash` which asserts
`assertEquals(emptyList(), failures)`. That test WILL now break because INV-3.4 fires on an
un-seeded grade-model/schedule. **Fix it in this step**: update that test to seed a minimal valid
grade model + 12 sweep-anchored schedule rows before asserting empty — mirror the
`seedGradeModels`/`seedSchedule` helpers from `TrustInvariantsPlan2Test`. Show the diff in the
commit. Do NOT weaken the assertion to `failures.none { it.check == "PARITY" }`; keep it a full
green by giving it valid Plan-2 seed data.)

- [ ] **Step 6: Run the full backend suite — expect GREEN:**

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit** (explicit paths only):

```powershell
git add src/main/kotlin/jarvis/tutor/verify/TrustInvariantsCli.kt src/test/kotlin/jarvis/tutor/verify/TrustInvariantsPlan2Test.kt src/test/kotlin/jarvis/tutor/verify/TrustInvariantsTest.kt
git commit -m "feat(invariants): INV-3.2/3.3-link/3.4 + 7-table SCHEMA pins in trustInvariants"
```

---

## Task 10 — INV-3.5 `SignatureLockPinTest` (runs in `:check` — closes the spec §3.8 INV-3.5 gap)

INV-3.5 **does not exist anywhere today** (§0.7). The spec §3.8 demands a CI diff against the
interface-signatures-lock showing no modified/removed frozen signature. This task creates
`SignatureLockPinTest` in `src/test/` so it runs inside `gradle :check` (the backend CI job) — turning
INV-3.5 into a real, always-on CI check. Each pin is its own `@Test` method citing the lock section
in KDoc. Wire shapes are pinned via `serialDescriptor(...).elementNames`; table shapes via Exposed
`Table.columns`; enum literals via the `@SerialName` wire values. NEW Plan-2 tables must NOT collide
with any frozen table name (disjointness assertion).

**Files:**
- Create: `src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt`
- Modify: `.github/workflows/test.yml` (one acknowledgment line on the backend job — see Step 5)

> **Reality the pins encode (verified 2026-06-11):**
> - `exam_dates` columns = {id, user_id, subject, start_at, created_at} (`Phase1Tables.kt:104-111`; lock §R `ApiExamDate {subject, start_at}` + the table).
> - `fsrs_cards` = 15 columns (`FsrsCards.kt:38-65`): id, user_id, source, source_ref, front, back, difficulty, stability, retrievability, due_at, last_reviewed_at, lapses, kc_id, status, paused_at.
> - `kc_verification_status` = 7 columns (`Phase1Tables.kt:117-184`; lock §I.1): kc_id, status, last_audit_run_id, content_hash, lecture_grounded, source_span_hash, updated_at.
> - `VerificationStatus` = {unverified, pending, faithful, uncertain, failed} (lock §I).
> - `Phase` = {intro, practice, retrieval, mastered} (lock §A).
> - `ClaimKind` = {DEFINITION, INVARIANT, GRADER_RULE, MISCONCEPTION_REFUTATION, STEM, EXPLANATION, WORKED_EXAMPLE} (lock §K + the §K AMENDMENT — 7 literals).
> - `ApiLessonReply` fields in order (lock §NEW-L): kcId, kc_name_ro, kc_name_en, concrete_question_ro, echo_source_ro, prediction_options, term_ro, definition_ro, explanation_ro, worked_example_ro, provenance.
> - `TrustSync.VerdictRow` fields (lock §I.1 allowlist; cross-checked by TrustSyncTest dump contents): kc_id, status, last_audit_run_id, content_hash, lecture_grounded, source_span_hash, updated_at.
> - `QueueItem` fields (lock §C): kc_id, kc_name_ro, kc_name_en, subject, phase, mastery_ewma, fsrs_card_id, verification_status, worked_example_first, mode.

- [ ] **Step 1: Confirm the exact identifiers exist before writing the test** (cheap grep — the test
imports these; a wrong package/name = compile RED for the wrong reason):

```powershell
gradle --no-daemon :compileKotlin
```

Then verify the symbols the test imports resolve in the source tree (read, do not guess):
- `jarvis.tutor.verify.TrustSync.VerdictRow` (`src/main/kotlin/jarvis/tutor/verify/TrustSync.kt:36-45`)
- `jarvis.web.ApiLessonReply` (verified 2026-06-11 at `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt:142` — package `jarvis.web`, NOT `jarvis.tutor`; the test imports it).
- `jarvis.tutor.QueueItem` (`QueueItem.kt:15`), `jarvis.tutor.Phase` (`PhaseModel.kt:10`), `jarvis.tutor.VerificationStatus` (`VerificationStatus.kt:17`) — all in `jarvis.tutor` (the test's own package; no import).
- `jarvis.tutor.verify.ClaimKind` (`VerificationTypes.kt:43`, 7 literals incl. the §K amendment).

> If a later refactor moves any of these to a different package, fix the `import` in the test to the
> real package. Do NOT move or rename the production type to fit the test.

- [ ] **Step 2: Write the test (this IS the implementation — a pin test has no separate prod code).**
Create `src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt`:

```kotlin
package jarvis.tutor

import jarvis.tutor.verify.ClaimKind
import jarvis.tutor.verify.TrustSync
import jarvis.web.ApiLessonReply
import kotlinx.serialization.descriptors.serialDescriptor
import org.jetbrains.exposed.sql.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * INV-3.5 (spec §3.8) — the CI diff against the interface-signatures-lock. This test makes INV-3.5
 * a REAL, always-on CI check (it runs in `gradle :check`, the backend CI job) — closing the
 * "INV-3.5 does not exist anywhere" gap found in the Plan-2 recon (§0.7). Every assertion pins ONE
 * frozen signature and cites its lock section in KDoc. A renamed/removed frozen column or wire field
 * FAILS here with the exact mismatch. New Plan-2 tables are ADDITIVE: they must not collide with any
 * frozen table name (last test).
 *
 * These pins are NOT redundant with LiveShapeColumnsMigrationTest (which pins the live-DB DELTA the
 * Plan-1 flip closes) or TrustSyncTest (which pins the D9 dump CONTENTS): this pins the frozen TYPE
 * shapes themselves, so a code change that drifts from the lock is caught at the type boundary.
 */
class SignatureLockPinTest {

    private fun colNames(t: Table): List<String> = t.columns.map { it.name }

    /** lock §R — exam_dates is the frozen one-row-per-(user,subject) schedule pointer. */
    @Test
    fun `exam_dates columns are frozen`() {
        assertEquals(
            listOf("id", "user_id", "subject", "start_at", "created_at"),
            colNames(ExamDatesTable),
        )
    }

    /** master-plan CHANGE-1 — fsrs_cards is the 828-card store; 15 frozen columns. */
    @Test
    fun `fsrs_cards has exactly the 15 frozen columns`() {
        assertEquals(
            listOf(
                "id", "user_id", "source", "source_ref", "front", "back", "difficulty",
                "stability", "retrievability", "due_at", "last_reviewed_at", "lapses",
                "kc_id", "status", "paused_at",
            ),
            colNames(FsrsCardsTable),
        )
    }

    /** lock §I.1 (RF1) — the 7-column trust store + the frozen D9 audit-column allowlist. */
    @Test
    fun `kc_verification_status has exactly the 7 frozen columns`() {
        assertEquals(
            listOf(
                "kc_id", "status", "last_audit_run_id", "content_hash",
                "lecture_grounded", "source_span_hash", "updated_at",
            ),
            colNames(KcVerificationStatusTable),
        )
    }

    /** lock §I — the 5 wire literals (4 authored + runtime-only `failed`). */
    @Test
    fun `VerificationStatus enum literals are frozen`() {
        assertEquals(
            listOf("unverified", "pending", "faithful", "uncertain", "failed"),
            VerificationStatus.entries.map { it.name },
        )
    }

    /** lock §A — the 4 phase literals (wire == lowercase name). */
    @Test
    fun `Phase enum literals are frozen`() {
        assertEquals(
            listOf("intro", "practice", "retrieval", "mastered"),
            Phase.entries.map { it.name },
        )
    }

    /** lock §K + the §K AMENDMENT — the 7 frozen claim kinds (UPPER wire names). */
    @Test
    fun `ClaimKind literals are frozen (7, incl the grounded-teaching amendment)`() {
        assertEquals(
            listOf(
                "DEFINITION", "INVARIANT", "GRADER_RULE", "MISCONCEPTION_REFUTATION",
                "STEM", "EXPLANATION", "WORKED_EXAMPLE",
            ),
            ClaimKind.entries.map { it.name },
        )
    }

    /** lock §NEW-L — the lesson reply field names IN ORDER (kotlinx-serialization descriptor). */
    @Test
    fun `ApiLessonReply wire field names match the NEW-L list in order`() {
        val names = serialDescriptor<ApiLessonReply>().elementNames.toList()
        assertEquals(
            listOf(
                "kcId", "kc_name_ro", "kc_name_en", "concrete_question_ro", "echo_source_ro",
                "prediction_options", "term_ro", "definition_ro", "explanation_ro",
                "worked_example_ro", "provenance",
            ),
            names,
        )
    }

    /** lock §I.1 — the D9 VerdictRow type (TrustSyncTest pins the DUMP; this pins the TYPE). */
    @Test
    fun `TrustSync VerdictRow field names are the 7 frozen allowlist keys`() {
        val names = serialDescriptor<TrustSync.VerdictRow>().elementNames.toList()
        assertEquals(
            listOf(
                "kc_id", "status", "last_audit_run_id", "content_hash",
                "lecture_grounded", "source_span_hash", "updated_at",
            ),
            names,
        )
    }

    /** lock §C — the /queue/today element field names. */
    @Test
    fun `QueueItem wire field names match the lock §C list`() {
        val names = serialDescriptor<QueueItem>().elementNames.toList()
        assertEquals(
            listOf(
                "kc_id", "kc_name_ro", "kc_name_en", "subject", "phase", "mastery_ewma",
                "fsrs_card_id", "verification_status", "worked_example_first", "mode",
            ),
            names,
        )
    }

    /** INV-3.5 additivity — NEW Plan-2 tables must not collide with any frozen table name. */
    @Test
    fun `Plan-2 tables are disjoint from the frozen table names`() {
        val frozen = setOf(
            "exam_dates", "fsrs_cards", "kc_verification_status", "report_wrong",
            "kc_mastery", "attempts", "verification_audit", "users",
        )
        val plan2 = setOf(
            ProblemsTable, ProblemRubricItemsTable, ProblemKcLinksTable,
            GradingModelsTable, GradeComponentsTable, GlossaryTermsTable, ExamScheduleRowsTable,
        ).map { it.tableName }.toSet()
        val collisions = plan2.intersect(frozen)
        assertTrue(collisions.isEmpty(), "Plan-2 tables collide with frozen tables: $collisions")
        assertEquals(7, plan2.size, "expected exactly the 7 new Plan-2 tables")
    }
}
```

- [ ] **Step 3: Run the test — expect GREEN** (it pins the CURRENT frozen shapes, which already
hold). If any pin is RED, the lock and code disagree TODAY — STOP and reconcile per the lock's
DURABLE RULE (amend the lock in the same commit, or escalate if a trust guarantee weakens):

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.SignatureLockPinTest"
```

Expected: `BUILD SUCCESSFUL`, 10 tests passed.

> **Documented failure mode:** if a later change renames e.g. `fsrs_cards.kc_id` to `concept_id`,
> the `fsrs_cards has exactly the 15 frozen columns` test fails with
> `expected:<[…, kc_id, …]> but was:<[…, concept_id, …]>` — the exact column drift, pointing at the
> frozen-signature violation. That is INV-3.5 doing its job.

- [ ] **Step 4: Confirm it runs inside `:check`** (so INV-3.5 is a real CI gate, not a manual task):

```powershell
gradle --no-daemon :check --tests "jarvis.tutor.SignatureLockPinTest"
```

Expected: `BUILD SUCCESSFUL` — the test is part of `:test`, which `:check` depends on. (No new gradle
task or CI job is needed; the backend CI job already runs `gradle :check`.)

- [ ] **Step 5: Acknowledge INV-3.5 coverage in the backend CI job name.** Edit
`.github/workflows/test.yml`. Change the backend job's `Gradle check` step name (line 28) so the
INV-3.5 coverage is visible in the CI log:

Replace:
```yaml
      - name: Gradle check (root project only — android subproject skipped; includes validateContent)
```
with:
```yaml
      - name: Gradle check (root project only; includes validateContent + INV-3.5 SignatureLockPinTest)
```

(No new job — INV-3.5 runs inside the existing `:check`. This one-line rename is the explicit
acknowledgment the task requires.)

- [ ] **Step 6: Run the full backend suite — expect GREEN:**

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit** (explicit paths only — NEVER `git add -A`):

```powershell
git add src/test/kotlin/jarvis/tutor/SignatureLockPinTest.kt .github/workflows/test.yml
git commit -m "test(inv-3.5): SignatureLockPinTest pins frozen tables/enums/wire shapes in :check"
```

## Task 11 — Apply the Plan-2 migrations + seeders to the LIVE PC DB (rehearsal on a copy FIRST, then gated real apply)

This is the first Plan-2 task that mutates real data. It adds the 7 new tables (Task 5/6), seeds the grade-model registry + exam schedule + exam_dates primaries (Task 7), and runs the honest kc_id backfill (Task 8) against the live 828-card DB. Sequence is identical in spirit to Plan-1 Task 7: **rehearse everything on a throwaway copy → take the real off-box backup → run the gated migration → seed → backfill → verify with read-only asserts**.

Hard facts (verified read-only 2026-06-11):
- Live PC DB: `C:\Users\User\.jarvis\tutor.db` = `$env:USERPROFILE\.jarvis\tutor.db`, **828 `fsrs_cards`, ALL `kc_id` NULL**, 8 rows in `kc_verification_status` ({faithful:4, uncertain:4}).
- 7 new tables expected post-migrate: `problems`, `problem_rubric_items`, `problem_kc_links`, `grading_models`, `grade_components`, `glossary_terms`, `exam_schedule_rows` (plan core §0.5 E; the `exam_dates` table already exists from Phase 1).
- `jarvis migrate` reads the **positional path first** (`Main.kt:42-44`), then `JARVIS_DB`. This plan ALWAYS passes the explicit path argument — never relies on `$HOME` resolution.
- The seeder + backfill CLIs (`seed-knowledge-meta`, `backfill-kc-ids`) are wired into `Main.kt` by Plan-2 Task 7 / Task 8 and read `JARVIS_TUTOR_DB` or a `--db <path>` flag. This task passes `--db` explicitly. **If either subcommand is missing (Task 7/8 not yet merged), STOP — do not hand-run seed SQL; the CLIs carry the idempotency + honest-match logic.**
- Honest backfill result today = **0 links, all 828 cards stay `kc_id` NULL** (plan core §0.5/§0.8 #2 — zero confident source-doc overlap exists). A non-zero link count here is a RED flag (a forced guess crept in): STOP and escalate.
- No `claimsFor()` / content-hash / D9 / VerificationGate code is touched by this task — the 4 faithful badges must stay lit with **unchanged** `content_hash` (asserted at the end of 11b).

### 11a — Rehearsal on a copy

- [ ] **Step 1: Stop any local process holding the live DB.** Do NOT stop the relay on :9999 (unrelated to this plan but standing infra):

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Confirm:$false }
```

Expected: no output (nothing was listening) or the process is stopped. Either is fine.

- [ ] **Step 2: Make the rehearsal copy of the live DB:**

```powershell
$reh = "$env:USERPROFILE\plan2-rehearsal"
New-Item -ItemType Directory -Force $reh | Out-Null
Copy-Item "$env:USERPROFILE\.jarvis\tutor.db" "$reh\tutor-copy.db" -Force
python -c "import sqlite3,sys; print('copy cards:', sqlite3.connect(sys.argv[1]).execute('SELECT COUNT(*) FROM fsrs_cards').fetchone()[0])" "$reh\tutor-copy.db"
```

Expected: `copy cards: 828`

- [ ] **Step 3: Prove the INV-3.1 refusal path on the copy (no backup taken yet).** Point `JARVIS_BACKUP_DIR` at an EMPTY dir and migrate the copy — the gate must REFUSE because 7 new tables = pending DDL over an ≥800-card DB with no same-day manifest:

```powershell
$env:JARVIS_BACKUP_DIR = "$reh\backups"   # does not exist yet — no manifest
gradle --no-daemon run --args="migrate $env:USERPROFILE\plan2-rehearsal\tutor-copy.db"
```

Expected: **BUILD FAILED** (non-zero exit), stderr contains:

```
[TutorMigration] migration REFUSED (INV-3.1 backup gate): no backup manifest (*.manifest.json) in ...
[migrate] FAILURE at column=null: ...
```

If migration SUCCEEDS here, the gate is not firing on the new tables — STOP and escalate (the additive DDL is not registering as pending; Task 5/6 wiring is broken).

- [ ] **Step 4: Back up the COPY into a temp backup dir (drill included):**

```powershell
python tools/db-backup.py --db "$env:USERPROFILE\plan2-rehearsal\tutor-copy.db" "$reh\backups"
```

Expected (three PASS lines):

```
[db-backup] integrity: PASS
[db-backup] restore drill: PASS (cards 828==828, schema_hash match)
[db-backup] backed up 828 fsrs_cards -> ...\jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz (N,NNN,NNN bytes); integrity: OK
```

- [ ] **Step 5: Migrate the copy (gate now passes — same-day manifest present):**

```powershell
$env:JARVIS_BACKUP_DIR = "$reh\backups"
gradle --no-daemon run --args="migrate $env:USERPROFILE\plan2-rehearsal\tutor-copy.db"
```

Expected: `[migrate] SUCCESS — fsrs_cards before=828 after=828`

- [ ] **Step 6: Seed knowledge-meta on the copy (grade models + schedule rows + exam_dates primaries):**

```powershell
gradle --no-daemon run --args="seed-knowledge-meta --db $env:USERPROFILE\plan2-rehearsal\tutor-copy.db"
```

Expected (shape; exact counts from Task 7's seeder): a success line reporting `grading_models=8 grade_components=N grading rows; exam_schedule_rows=12; exam_dates primaries=4; glossary=...`. Non-zero exit = STOP.

- [ ] **Step 7: Backfill kc_id on the copy in DRY-RUN mode (no writes, honest match report):**

```powershell
gradle --no-daemon run --args="backfill-kc-ids --dry-run --db $env:USERPROFILE\plan2-rehearsal\tutor-copy.db"
```

Expected: a report ending `[backfill-kc-ids] DRY-RUN: 0 card(s) would link (0 confident source-doc matches); 828 stay NULL`. **A non-zero would-link count = STOP and escalate** (a guess crept in; §0.8 #2 forbids similarity scoring).

- [ ] **Step 8: Assert the copy's schema + survival (read-only).** This single python block proves: 7 new tables exist, seed row counts, all 828 cards survive with `kc_id` still NULL, and `kc_verification_status` is untouched (8 rows, content_hash values unchanged):

```powershell
python - "$env:USERPROFILE\plan2-rehearsal\tutor-copy.db" << 'PY'
import sqlite3, sys
con = sqlite3.connect("file:" + sys.argv[1].replace("\\","/") + "?mode=ro", uri=True)
tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
need = {"problems","problem_rubric_items","problem_kc_links","grading_models","grade_components","glossary_terms","exam_schedule_rows"}
print("new tables present:", need.issubset(tables), sorted(need - tables) or "(all 7 present)")
print("grading_models:", con.execute("SELECT COUNT(*) FROM grading_models").fetchone()[0])
print("exam_schedule_rows:", con.execute("SELECT COUNT(*) FROM exam_schedule_rows").fetchone()[0])
print("exam_dates:", con.execute("SELECT COUNT(*) FROM exam_dates").fetchone()[0])
print("fsrs_cards:", con.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0])
print("fsrs_cards kc_id NOT NULL:", con.execute("SELECT COUNT(*) FROM fsrs_cards WHERE kc_id IS NOT NULL").fetchone()[0])
print("kvs rows:", con.execute("SELECT COUNT(*) FROM kc_verification_status").fetchone()[0])
print("kvs faithful hashes:", con.execute("SELECT kc_id||':'||content_hash FROM kc_verification_status WHERE status='faithful' ORDER BY kc_id").fetchall())
PY
```

Expected EXACTLY:

```
new tables present: True (all 7 present)
grading_models: 8
exam_schedule_rows: 12
exam_dates: 4
fsrs_cards: 828
fsrs_cards kc_id NOT NULL: 0
kvs rows: 8
kvs faithful hashes: [('pa-kc-001:ca05c671',), ('pa-kc-002:56156563',), ('pa-kc-003:d5363220',), ('pa-kc-004:817aaf44',)]
```

If `fsrs_cards kc_id NOT NULL` is anything but 0, or any faithful hash differs, STOP — the rehearsal caught a regression before it touched real data. (The 4 faithful hashes `ca05c671 / 56156563 / d5363220 / 817aaf44` were read from the live DB 2026-06-11; they are the canonical pre/post-migrate parity values — plan core §0.1 records only the count {faithful:4}, these literal hashes are the verified runtime values.)

### 11b — The real apply

- [ ] **Step 1: Take the real off-box backup of the LIVE DB + push it off-box to the VPS.** The dump goes to a dated PC dir AND a copy lands on the VPS at `/opt/jarvis/backups/pc/`:

```powershell
$bk = "$env:USERPROFILE\jarvis-backups\$(Get-Date -Format yyyy-MM-dd)"
New-Item -ItemType Directory -Force $bk | Out-Null
python tools/db-backup.py $bk
```

Expected (three PASS lines, cards 828):

```
[db-backup] integrity: PASS
[db-backup] restore drill: PASS (cards 828==828, schema_hash match)
[db-backup] backed up 828 fsrs_cards -> ...\jarvis-backups\2026-06-12\jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz (N,NNN,NNN bytes); integrity: OK
```

Then copy the dump + manifest off-box to the VPS:

```powershell
ssh root@46.247.109.91 "mkdir -p /opt/jarvis/backups/pc"
Get-ChildItem $bk | ForEach-Object { scp $_.FullName root@46.247.109.91:/opt/jarvis/backups/pc/ }
ssh root@46.247.109.91 "ls -la /opt/jarvis/backups/pc/"   # confirm the dated dump + .manifest.json landed
```

Expected: the new `.sql.gz` and `.sql.gz.manifest.json` appear in the VPS listing.

- [ ] **Step 2: Run the gated migration on the LIVE DB (same day as the backup).** The INV-3.1 gate reads `$bk` and must find the fresh same-day manifest with `integrity=PASS`, `fsrs_cards==828`, `schema_hash==live`:

```powershell
$env:JARVIS_BACKUP_DIR = $bk
gradle --no-daemon run --args="migrate $env:USERPROFILE\.jarvis\tutor.db"
```

Expected: `[migrate] SUCCESS — fsrs_cards before=828 after=828`

**What refusal looks like and what to do:** if instead you see `[TutorMigration] migration REFUSED (INV-3.1 backup gate): ...` (no manifest / stale date / card-count or schema-hash mismatch), the migration did NOT run — **refusal = STOP. Fix the backup (re-run Step 1 so a fresh same-day manifest exists in `$bk`), then retry Step 2. NEVER set any bypass env var, NEVER point `JARVIS_BACKUP_DIR` at a stale manifest, NEVER edit the gate.** The gate refusing is the system working.

- [ ] **Step 3: Seed knowledge-meta on the LIVE DB:**

```powershell
gradle --no-daemon run --args="seed-knowledge-meta --db $env:USERPROFILE\.jarvis\tutor.db"
```

Expected: the same success shape as 11a Step 6 (`grading_models=8 ... exam_schedule_rows=12; exam_dates primaries=4`). The seeder is idempotent (Task 7) — re-running is a no-op; a non-zero exit = STOP.

- [ ] **Step 4: Run the REAL kc_id backfill on the LIVE DB (not dry-run):**

```powershell
gradle --no-daemon run --args="backfill-kc-ids --db $env:USERPROFILE\.jarvis\tutor.db"
```

Expected: `[backfill-kc-ids] linked 0 card(s) (0 confident source-doc matches); 828 stay NULL` — success exit 0. **A non-zero linked count = STOP and escalate** (§0.8 #2: zero overlap is the correct, honest result today).

- [ ] **Step 5: Run trustInvariants over the REAL DB (all blocks must PASS, including the new INV-3.2/INV-3.4 blocks from Task 9):**

```powershell
$env:JARVIS_TUTOR_DB = "$env:USERPROFILE\.jarvis\tutor.db"
$env:JARVIS_BACKUP_DIR = $bk
# args[0] is the CONTENT DIR (TrustInvariantsCli.kt:116-124); the DB path comes ONLY from
# JARVIS_TUTOR_DB. NEVER pass the .db path positionally — it would be read as the content dir.
gradle --no-daemon trustInvariants "-PinvariantsArgs=content"
```

Expected: `[trustInvariants] ALL PASS (db=..., content=content, floor=800)` — including:
- INV-3.3 (828 == backup manifest, no card lost its history);
- SCHEMA pins for the 7 new tables (Task 9);
- INV-3.2 (every KC has a valid `concept_type`; zero beat-served KCs lack complete beats — vacuously green, no KC is beat-served until Plan 3);
- INV-3.4 (`exam_schedule_rows` count == 12, every `grading_models` row has a non-empty `source_url`, every `exam_dates` row traces to the sweep);
- FLIP (≥1 faithful — still 4) and PARITY (each faithful hash recomputes identically from `content/`).

If ANY line FAILs: STOP. Do NOT proceed to the VPS. If a SCHEMA / INV-3.2 / INV-3.4 line fails, the seed/migrate is wrong — restore from `$bk` (`gunzip` the `.sql.gz`, replay into a fresh file, swap in) and re-assess. If only PARITY fails on a faithful row, the content corpus diverged — escalate (do NOT edit content to force green).

- [ ] **Step 6: Same read-only assert set as 11a Step 8, against the LIVE DB** (substitute path `$env:USERPROFILE\.jarvis\tutor.db`). Expected output identical to 11a Step 8 EXACTLY:

```
new tables present: True (all 7 present)
grading_models: 8
exam_schedule_rows: 12
exam_dates: 4
fsrs_cards: 828
fsrs_cards kc_id NOT NULL: 0
kvs rows: 8
kvs faithful hashes: [('pa-kc-001:ca05c671',), ('pa-kc-002:56156563',), ('pa-kc-003:d5363220',), ('pa-kc-004:817aaf44',)]
```

- [ ] **Step 7: Badge sanity — the 4 faithful rows are still faithful with UNCHANGED content_hash** (machine check; this is the proof Plan 2 caused no re-audit cascade — §0.3):

```powershell
python -c "import sqlite3,pathlib; con=sqlite3.connect((pathlib.Path.home()/'.jarvis'/'tutor.db').as_uri()+'?mode=ro',uri=True); [print(r) for r in con.execute('SELECT kc_id,status,content_hash FROM kc_verification_status ORDER BY kc_id')]"
```

Expected EXACTLY (8 rows, the 4 faithful hashes byte-identical to the pre-migrate values):

```
('pa-kc-001', 'faithful', 'ca05c671')
('pa-kc-002', 'faithful', '56156563')
('pa-kc-003', 'faithful', 'd5363220')
('pa-kc-004', 'faithful', '817aaf44')
('pa-kc-005', 'uncertain', '1b67adce')
('pa-kc-006', 'uncertain', '6af8f795')
('pa-kc-fixture-compute', 'uncertain', '1a004870')
('pa-kc-fixture-recursion', 'uncertain', '1a004870')
```

Any faithful row flipped to a non-faithful status, or any `content_hash` changed = a content-hash cascade fired (a Plan-2 field leaked into `claimsFor()`) — STOP and escalate; this violates the §0.3 invariant the whole plan rests on.

No repo commit (this task changes runtime state only — the DB is not git-tracked). The rehearsal copy + temp backups under `$env:USERPROFILE\plan2-rehearsal` can be deleted afterward; the dated `$bk` dump is KEPT (it is the recovery point).

---

## Task 12 — Apply the Plan-2 migrations + seeders to the LIVE VPS DB (backup → deploy → migrate → seed → invariants → badge spot-check)

The VPS runs the tutor as a systemd service (`User=jarvis`, `WorkingDirectory=/opt/jarvis`, `Environment=HOME=/opt/jarvis`, `ExecStart=…/jarvis-kotlin web`). On `web` boot the service runs `TutorMigration.migrate` (`TutorRoutes.kt:2981`) against `Config.tutorDbPath`, which with `HOME=/opt/jarvis` and **no `JARVIS_TUTOR_DB` set** resolves to `/opt/jarvis/.jarvis/tutor.db` — the REAL service DB (**871 cards**, verified 2026-06-11). The 7-new-tables DDL is pending there too, so the INV-3.1 gate fires on the boot-migrate; `JARVIS_BACKUP_DIR=/opt/jarvis/backups` is already in `/opt/jarvis/.env` (verified), so a same-day manifest must exist there BEFORE the deploy-restart.

**Critical DB-path rule (the 871-vs-0 incident):** when you run any CLI over `ssh root@46.247.109.91` WITHOUT the service's HOME, `user.home`=`/root` → it would target `/root/.jarvis/tutor.db` — a **junk byproduct DB (286 KB, root-owned)**, NEVER the service DB. So every CLI invocation below passes the explicit path `/opt/jarvis/.jarvis/tutor.db` (and every backup uses `--db /opt/jarvis/.jarvis/tutor.db`). NEVER target `/root/.jarvis/tutor.db`.

**PowerShell ssh quoting idiom (from Plan-1 Task 11):** single-quote the PowerShell string so `$` reaches the REMOTE bash untouched; the remote `set -a; . /opt/jarvis/.env; set +a` sources the env on the VPS side.

- [ ] **Step 1: Ship the hardened backup tool + back up the VPS DB (explicit `--db`), then pull the dump to the PC (the VPS's off-box copy):**

```powershell
scp tools/db-backup.py root@46.247.109.91:/opt/jarvis/db-backup.py
ssh root@46.247.109.91 'mkdir -p /opt/jarvis/backups; python3 /opt/jarvis/db-backup.py --db /opt/jarvis/.jarvis/tutor.db /opt/jarvis/backups'
```

Expected (three PASS lines, **871** cards — the VPS has its own history):

```
[db-backup] integrity: PASS
[db-backup] restore drill: PASS (cards 871==871, schema_hash match)
[db-backup] backed up 871 fsrs_cards -> /opt/jarvis/backups/jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz (N,NNN,NNN bytes); integrity: OK
```

Pull the off-box copy to the PC (both directions are now off-box-covered):

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\jarvis-backups\vps" | Out-Null
scp "root@46.247.109.91:/opt/jarvis/backups/*.sql.gz*" "$env:USERPROFILE\jarvis-backups\vps\"
```

Expected: the new `.sql.gz` + `.sql.gz.manifest.json` land under `...\jarvis-backups\vps\`.

- [ ] **Step 2: Deploy the new jar + corpus (boot-migrate runs the schema).** Use the existing deploy recipe verbatim — it builds (`gradle :test :installDist`), scps the dist + the `content/` corpus, restarts the service (which migrates on boot), and smoke-checks healthz:

```powershell
bash tools/deploy.sh
```

Expected: deploy completes; the final lines show `active` from `systemctl is-active jarvis` and a healthz body. The boot-migrate over the 871-card DB needs the same-day manifest in `/opt/jarvis/backups` (taken in Step 1) — if the service fails to start with `[TutorMigration] migration REFUSED (INV-3.1 backup gate)` in `/var/log/jarvis.log`, the manifest is missing/stale: re-run Step 1 (same day), then `ssh root@46.247.109.91 "systemctl restart jarvis && sleep 4 && systemctl is-active jarvis"`. **Refusal = STOP + fix the backup; never bypass.**

- [ ] **Step 3: Run an explicit idempotent migrate against the REAL service DB** (belt-and-braces — proves the correct DB was migrated, not the `/root` junk DB; boot-migrate already ran, so this is a no-op confirming success):

```powershell
ssh root@46.247.109.91 'set -a; . /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin migrate /opt/jarvis/.jarvis/tutor.db'
```

Expected: `[migrate] SUCCESS — fsrs_cards before=871 after=871`. If `before=0` appears, the wrong DB was targeted (the `/root` junk DB) — STOP, do not seed, re-check the explicit path.

- [ ] **Step 4: Seed knowledge-meta on the REAL VPS DB (explicit `--db`):**

```powershell
ssh root@46.247.109.91 'set -a; . /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin seed-knowledge-meta --db /opt/jarvis/.jarvis/tutor.db'
```

Expected: the success shape `grading_models=8 ... exam_schedule_rows=12; exam_dates primaries=4`. Idempotent — safe to re-run; non-zero exit = STOP.

- [ ] **Step 5: Remote read-only asserts (tables present, seed counts, 871 cards survive, faithful badges unchanged).** The query uses no string-literal SQL with quotes that would fight the ssh layer — it reads via a heredoc-free python `-c` over the explicit path:

```powershell
ssh root@46.247.109.91 'python3 -c "import sqlite3; con=sqlite3.connect(\"file:/opt/jarvis/.jarvis/tutor.db?mode=ro\",uri=True); t={r[0] for r in con.execute(\"SELECT name FROM sqlite_master WHERE type=\x27table\x27\")}; need={\"problems\",\"problem_rubric_items\",\"problem_kc_links\",\"grading_models\",\"grade_components\",\"glossary_terms\",\"exam_schedule_rows\"}; print(\"new tables present:\", need.issubset(t)); print(\"grading_models:\", con.execute(\"SELECT COUNT(*) FROM grading_models\").fetchone()[0]); print(\"exam_schedule_rows:\", con.execute(\"SELECT COUNT(*) FROM exam_schedule_rows\").fetchone()[0]); print(\"exam_dates:\", con.execute(\"SELECT COUNT(*) FROM exam_dates\").fetchone()[0]); print(\"fsrs_cards:\", con.execute(\"SELECT COUNT(*) FROM fsrs_cards\").fetchone()[0]); print(\"faithful:\", con.execute(\"SELECT COUNT(*) FROM kc_verification_status WHERE status=\x27faithful\x27\").fetchone()[0]); print(\"faithful hashes:\", con.execute(\"SELECT kc_id||\x27:\x27||content_hash FROM kc_verification_status WHERE status=\x27faithful\x27 ORDER BY kc_id\").fetchall())"'
```

Expected EXACTLY:

```
new tables present: True
grading_models: 8
exam_schedule_rows: 12
exam_dates: 4
fsrs_cards: 871
faithful: 4
faithful hashes: [('pa-kc-001:ca05c671',), ('pa-kc-002:56156563',), ('pa-kc-003:d5363220',), ('pa-kc-004:817aaf44',)]
```

(If the PowerShell→ssh quoting fights you, `ssh root@46.247.109.91` interactively and run the python block directly on the VPS — the assertion is what matters, not the wrapper. The faithful set + hashes must be byte-identical to the PC's: `ca05c671 / 56156563 / d5363220 / 817aaf44`.) Any deviation (missing table, count ≠ 8/12/4, cards ≠ 871, a flipped/changed faithful row) = STOP and escalate.

- [ ] **Step 6: healthz 200 confirmation** (the service is serving after the boot-migrate):

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -m 10 https://corgflix.duckdns.org/healthz
```

Expected: `200`. Anything else: check `ssh root@46.247.109.91 "tail -30 /var/log/jarvis.log"` for the boot-migrate refusal or a startup error before claiming done.

- [ ] **Step 7: D9 NO-OP check (this plan does not touch the trust sync).** Plan 2 added ZERO columns to `kc_verification_status` and ZERO changes to `TrustSync.VerdictRow` (§0.5, §0.8 #3). Confirm the verdict table is byte-for-byte what Plan 1 left — the faithful set is unchanged and no new column appeared on the synced table:

```powershell
ssh root@46.247.109.91 'python3 -c "import sqlite3; con=sqlite3.connect(\"file:/opt/jarvis/.jarvis/tutor.db?mode=ro\",uri=True); print(\"kvs cols:\", [c[1] for c in con.execute(\"PRAGMA table_info(kc_verification_status)\")]); print(\"kvs rows:\", con.execute(\"SELECT COUNT(*) FROM kc_verification_status\").fetchone()[0])"'
```

Expected EXACTLY (the 7 frozen Plan-1 columns, 8 rows — NO Plan-2 column added):

```
kvs cols: ['kc_id', 'status', 'last_audit_run_id', 'updated_at', 'content_hash', 'lecture_grounded', 'source_span_hash']
kvs rows: 8
```

A column count other than 7 = a Plan-2 change leaked into the frozen verdict table — STOP and escalate (this would silently un-sync via the D9 allowlist).

- [ ] **Step 8: Badge spot-check for the PM (machine check, NOT content judgement).** Curl the verify-status endpoint for `pa-kc-001` and confirm it reads `faithful` — this is the end-to-end proof that the synced verdict + the VPS's own content-hash recompute still agree after the schema add. **This is a machine assertion; never ask Alex to read or judge the lesson content (no-oracle-inversion).**

```powershell
# the REAL route is GET /api/v1/verify/{kcId}/status (TrustRoutes.kt:396) — NOT /tutor/verify-status/
curl.exe -s -m 10 https://corgflix.duckdns.org/api/v1/verify/pa-kc-001/status
```

Expected: a JSON body whose status field is `faithful` (the reply also carries `honest_floor` and `badge_text` — `badge_text` must be the RO "corespunde cursului" class of string, NEVER "corect"). If the route is auth-gated and returns 401, fall back to the direct DB read: `ssh root@46.247.109.91 'python3 -c "import sqlite3; print(sqlite3.connect(\"file:/opt/jarvis/.jarvis/tutor.db?mode=ro\",uri=True).execute(\"SELECT status FROM kc_verification_status WHERE kc_id=\x27pa-kc-001\x27\").fetchone())"'` and expect `('faithful',)`. A non-faithful result = the content corpus on the VPS diverged from the synced hash (the deploy ships `content/`, so a stale corpus is the usual cause) — diff the VPS `content/PA` against the PC's and escalate; do NOT edit content to force the badge.

No repo commit (runtime ops). Record nothing in memory files — `/wrap` handles session state.

---

## Task 13 — Whole-suite final gate + plan-index/docs update

Plan-2's per-task commits are already on `main` (Tasks 1–10). This task runs the full review-workflow gate (backend + frontend + python suites, run in full, never piped through `tail`), updates the plan index to mark Plan 2 DONE, and commits + pushes the docs update — verifying `0/0 vs origin` after the push.

**Files:**
- Modify: `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md`

- [ ] **Step 1: Full backend suite — run in full, review the whole output (NEVER pipe through `tail`/`head`; the review-workflow rule):**

```powershell
gradle --no-daemon :check
```

Expected: `BUILD SUCCESSFUL`. This runs every backend test including the new Plan-2 tests (ConceptType, KcBeats completeness, VizInstance loader, migration tests for the 7 tables, seeder idempotency, kc_id backfill, and the INV-3.5 `SignatureLockPinTest` from Task 10). If anything is RED, STOP and fix — do not mark the plan done.

- [ ] **Step 2: Frontend vitest:**

```powershell
cd tutor-web ; npm test ; cd ..
```

Expected: vitest GREEN (all unit/component specs pass). **The `tutor-shell-api-contract.spec.ts` e2e is RED PRE-EXISTING** (it needs a served backend / mock in CI — plan core §0.6, carried in the plan-index follow-up list). That red is NOT introduced by Plan 2: do NOT claim it green, do NOT attempt to fix it in this task. If vitest's NON-e2e specs go red, that IS yours — STOP and fix.

- [ ] **Step 3: Python backup-tooling suites (run both, full output):**

```powershell
python tools/test_db_backup.py
python tools/test_db_backup_hardening.py
```

Expected: each prints `OK` (the unittest pass line). A failure here means the backup tool regressed — STOP and fix before claiming done.

- [ ] **Step 4: Update the plan index — flip the Plan 2 row to DONE and append any newly-discovered carried follow-ups.** Read the current row first:

The Plan 2 table row currently reads:

```
| 2 | Knowledge schema — concept_type, per-beat language-keyed teaching fields, pedagogical_instance class, problem bank (archetype/slots/rubrics), grade-model registry + exam_dates seeding, 828-card kc_id backfill | §3 | next | just-in-time |
```

Replace the `Status` and `Detailed plan doc` cells (apply this exact edit to `docs/superpowers/plans/2026-06-11-one-pass-plan-index.md`):

- old: `| §3 | next | just-in-time |`
- new: `| §3 | **DONE 2026-06-11** (7 new tables live on PC+VPS; 8 grade-model + 12 schedule + 4 exam_dates rows seeded; 828/871 cards intact, 0 kc_id links — honest zero-overlap; 4 faithful badges unchanged) | `2026-06-11-plan2-knowledge-schema.md` |`

Then append any follow-ups DISCOVERED DURING EXECUTION to the existing "Carried follow-ups" line (do NOT delete the existing entries; append after the last `·`-separated item). Known Plan-2 carry candidates to fold in if they held true during execution:

- `kc_id backfill is a 0-link no-op today (zero source-doc overlap, §0.8 #2) — re-run the honest backfill after the digestion pipeline (Plan 5) ingests real PA lecture docs that share source stems with the 828 cards`
- `concept_type / beat-content verification (type-mismatch in the faithful-check prompt, per-beat re-admission, pedagogical_instance method-consistency) is deferred to Plans 4/5 (§0.8 #4) — claimsFor() stays unextended until then`

If execution surfaced a NEW ambiguity not in that list, append it verbatim too (one `·`-separated item) rather than silently dropping it.

- [ ] **Step 5: Verify the edit rendered (read-only sanity):**

```powershell
Select-String -Path docs/superpowers/plans/2026-06-11-one-pass-plan-index.md -Pattern 'DONE 2026-06-11.*7 new tables'
```

Expected: one match line showing the updated Plan 2 row. (Per house tooling rules, this is the one place a `Select-String` is used to confirm the doc edit landed — the Edit tool already errors if the old string was not found.)

- [ ] **Step 6: Commit the docs update (explicit path only — NEVER `git add -A`; untracked door files on `main` must not be swept):**

```powershell
git add docs/superpowers/plans/2026-06-11-one-pass-plan-index.md
git commit -m @'
docs(plan): Plan 2 (knowledge schema) DONE — 7 tables live PC+VPS, registry+schedule seeded, honest 0-link kc_id backfill

Plan 2 applied to both live DBs behind the INV-3.1 gate: 7 additive
tables + 8 grade-model / 12 schedule / 4 exam_dates rows; 828/871 cards
intact, 0 kc_id links (zero source-doc overlap today — honest result);
4 faithful badges unchanged (claimsFor() untouched). Index flipped to
DONE + carried follow-ups appended.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

- [ ] **Step 7: Push and verify `0/0 vs origin`:**

```powershell
git push origin main
git status
git log -1 --oneline
```

Expected: `git status` shows `Your branch is up to date with 'origin/main'.` (0 ahead / 0 behind), and `git log -1` shows the docs(plan) commit at HEAD. If `git status` shows "ahead by 1", the push did not land — re-run `git push origin main` and re-check before claiming the plan done.

**Done means:** `:check` green, frontend vitest green (the one pre-existing e2e red is named + not yours), both python suites `OK`, the plan index marks Plan 2 DONE with the verified live-state summary, and `main` is `0/0` against `origin`.

