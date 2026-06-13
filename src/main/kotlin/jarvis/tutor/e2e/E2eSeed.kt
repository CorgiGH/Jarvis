package jarvis.tutor.e2e

import jarvis.content.ConceptType
import jarvis.content.ContentCli
import jarvis.content.ContentRepo
import jarvis.content.ContentReconcile
import jarvis.content.KnowledgeConcept
import jarvis.tutor.AI_LITERACY_VERSION
import jarvis.tutor.AiLiteracyRepo
import jarvis.tutor.CardStatus
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.FsrsSource
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.LanguageCheckTable
import jarvis.tutor.SessionRepo
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.VerificationStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

/**
 * Plan 4b Task 9 (§0.9J / R-4b-Q2 option (a)) — the real-backend CI seeder.
 *
 * Builds a fresh SQLite DB seeded from the REAL `content/` corpus via the REAL admission path
 * ([ContentValidator.validate] → [ContentReconcile.reconcile]), promotes every beat-complete KC to
 * `faithful` with the REAL recomputed hashes (the [jarvis.tutor.DrillGradeAtomicTest] `seedFaithful`
 * pattern — this stamps what [jarvis.tutor.verify.VerificationRunner] would, deterministically, with
 * ZERO LLM calls), and seeds one user + session so the boot-up Ktor server serves the faithful-gated
 * lesson route over the real corpus. The companion `lesson-gates.spec.ts` drives the rendered gates
 * 4a–4d (INV-4.4 / INV-5.3 / INV-8.4) against this DB.
 *
 * NO LLM anywhere (the faithful stamp is deterministic). NO live-DB mutation — it writes a throwaway
 * file under `build/e2e/`. The seed manifest's `kcIds` / `figureKcIds` are DERIVED from the corpus,
 * never hardcoded — when Plan 5/6 admits a 5th beat-complete KC or a 2nd figure binding the gate's
 * coverage grows with zero seeder edits (the same no-silent-under-coverage standard Task 2 sets).
 */
object E2eSeed {

    /** The fixed session id the spec injects as the `jarvis_session` cookie. */
    const val SID_LABEL: String = "e2e-lesson-gates"

    /** Far-future session TTL so the seeded session never expires mid-CI-run (10 years). */
    private const val SESSION_TTL_SECONDS: Long = 10L * 365 * 24 * 3600

    /** The result of one seed run — returned to tests; serialized to `seed.json` for the spec. */
    data class SeedResult(
        /** The RAW session id (the cookie value) — store sha256(sid) is what the DB holds. */
        val sid: String,
        /** Every beat-complete KC promoted to faithful (derived; resolves to pa-kc-001..004 today). */
        val kcIds: List<String>,
        /** The subset of [kcIds] whose `ro` beats bind a `reveal.figure` (today ["pa-kc-002"]). */
        val figureKcIds: List<String>,
        /** The seeded user id (OWNER scope). */
        val userId: String,
    )

    /**
     * A KC is "beat-complete" iff its `concept_type` parses AND its `ro` beats are structurally
     * complete for that type — the EXACT gate the lesson serve route applies
     * (QueueMasteryCalibrationRoutes.kt:484-487). The seeder promotes exactly this set to faithful,
     * so the served set == the promoted set (no over- or under-seeding).
     */
    fun isBeatComplete(kc: KnowledgeConcept): Boolean {
        val ct = kc.concept_type?.let { ConceptType.fromWire(it) } ?: return false
        val roBeats = kc.beats["ro"] ?: return false
        return roBeats.isCompleteFor(ct)
    }

    /** True iff the KC's `ro` reveal binds a figure (drives `figureKcIds`). */
    fun bindsFigure(kc: KnowledgeConcept): Boolean =
        kc.beats["ro"]?.reveal?.figure != null

    /**
     * Seed a fresh e2e DB and write [outJson]. Returns the [SeedResult].
     *
     * Steps (§0.9J):
     *  1. fresh DB file (delete if exists) + [TutorMigration.migrate] + `SchemaUtils.create(LanguageCheckTable)`
     *     (the migration patch is PM-merge-only at CP-3 — the seeder creates the table itself so it is
     *     self-contained pre-CP-3).
     *  2. [ContentCli.reconcile] — the REAL admission path (validateContent aborts non-zero on a
     *     malformed corpus) + claims + pending + language-check records.
     *  3. promote every beat-complete KC to `faithful` with the REAL `kcContentHash(kc)` + REAL
     *     `sourceSpanHashOf(claimsFor(kc), repo::sourceText)` (deterministic, zero LLM).
     *  4. seed one OWNER user + session (sid label [SID_LABEL]) + AI-literacy confirmation + one ACTIVE
     *     FSRS card per promoted KC so the oggi handoff target has data.
     *  5. write [outJson] `{ "sid", "kcIds", "figureKcIds" }` (DERIVED sets; abort if either is empty).
     *
     * @throws IllegalStateException if the corpus fails validation, or if the promoted set or the
     *   figure-binding set is empty (the anti-vacuity gate — the figure gates must never go vacuous).
     */
    fun seed(dbPath: String, contentDir: Path, outJson: Path, clock: () -> Instant = Instant::now): SeedResult {
        val now = clock()

        // (1) fresh DB file + migrate + the language_check table (PM-merge-only migration patch).
        val dbFile = Path.of(dbPath)
        dbFile.parent?.createDirectories()
        // Delete a stale DB + its WAL/SHM sidecars so the run is hermetic.
        dbFile.deleteIfExists()
        Path.of("$dbPath-wal").deleteIfExists()
        Path.of("$dbPath-shm").deleteIfExists()
        val db = TutorDb.connect(dbPath)
        TutorMigration.migrate(db)
        transaction(db) { SchemaUtils.create(LanguageCheckTable) }

        // (2) REAL admission path: validate (abort non-zero on a malformed corpus) → reconcile
        //     (claims + pending + language-check records). This is exactly the curate-tutor Stage-9
        //     reconcile owners run — the seed enters trust the same way real content does.
        ContentCli.reconcile(contentDir, db, clock)

        // (3) promote every beat-complete KC to faithful with REAL hashes (deterministic, zero LLM).
        val repo = ContentRepo(contentDir)
        val manifest = repo.loadManifest()
        val promoted = mutableListOf<String>()
        val figureKcs = mutableListOf<String>()
        for (entry in manifest.subjects) {
            val loaded = repo.loadSubject(entry.id)
            for (kc in loaded.kcs) {
                if (!isBeatComplete(kc)) continue
                promoteFaithful(db, repo, kc, now)
                promoted += kc.id
                if (bindsFigure(kc)) figureKcs += kc.id
            }
        }
        check(promoted.isNotEmpty()) {
            "E2eSeed: NO beat-complete KC found in the corpus — the lesson-gates proof would be vacuous " +
                "(every figure/no-clip/legibility/RO gate would assert over an empty served set). Abort."
        }
        check(figureKcs.isNotEmpty()) {
            "E2eSeed: NO figure-binding KC found among the ${promoted.size} promoted KCs — the figure " +
                "gate (INV-4.4 'every animated', R-4b-Q1 anti-vacuity) would be vacuous. Abort " +
                "(Task 5's FigureBindingNonVacuityTest pins the same invariant Kotlin-side)."
        }

        // (4) one OWNER user + a session (fixed sid label) + AI-literacy confirmation + one ACTIVE
        //     FSRS card per promoted KC so the oggi handoff target (queue/today) has data.
        val userId = TutorTypes.ulid()
        UserRepo(db).insert(
            User(userId, "e2e", UserScope.OWNER, now, now, email = "e2e@local", lang = "ro"),
        )
        AiLiteracyRepo(db).confirm(userId, AI_LITERACY_VERSION, "ro")
        val sid = SessionRepo(db).create(userId, SESSION_TTL_SECONDS)
        for (kcId in promoted) seedActiveCard(db, userId, kcId, now)

        // (5) write the seed manifest (derived sets; both proven non-empty above).
        outJson.parent?.createDirectories()
        outJson.writeText(buildSeedJson(sid, promoted, figureKcs))

        return SeedResult(sid = sid, kcIds = promoted, figureKcIds = figureKcs, userId = userId)
    }

    /**
     * Stamp a `kc_verification_status` row that [jarvis.web.VerifyAdmin.resolveStatus] serves as
     * FAITHFUL: status=faithful, REAL [ContentReconcile.kcContentHash] (matches the live content), REAL
     * [ContentReconcile.sourceSpanHashOf] (present → the D1 source-span leg passes), and (by virtue of
     * the fresh DB) no OPEN report_wrong. Identical to DrillGradeAtomicTest.seedFaithful / the
     * LessonServeRouteTest seedB8 faithful path — what the real VerificationRunner would write, minus
     * the LLM legs (deterministic).
     */
    private fun promoteFaithful(db: Database, repo: ContentRepo, kc: KnowledgeConcept, now: Instant) {
        val contentHash = ContentReconcile.kcContentHash(kc)
        val spanHash = ContentReconcile.sourceSpanHashOf(ContentReconcile.claimsFor(kc)) { subject, doc ->
            repo.sourceText(subject, doc)
        } ?: error(
            "E2eSeed: KC ${kc.id} produced a NULL source_span_hash — its quote no longer relocates in " +
                "_sources (faithful requires a present span hash). Fix the corpus before seeding.",
        )
        transaction(db) {
            // Stage-9 reconcile already INSERTed a `pending` row for every KC (PK = kc_id), so
            // promote by UPDATE — a raw INSERT would collide on the PK. Mirrors VerificationRunner
            // .finalizeKc (insert-if-absent / update-if-present); after reconcile the row always
            // exists, but the existence branch keeps the helper robust if reconcile is ever skipped.
            val exists = KcVerificationStatusTable.selectAll()
                .where { KcVerificationStatusTable.kcId eq kc.id }
                .any()
            if (exists) {
                KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kc.id }) {
                    it[status] = VerificationStatus.faithful.name
                    it[KcVerificationStatusTable.contentHash] = contentHash
                    it[sourceSpanHash] = spanHash
                    it[updatedAt] = now
                }
            } else {
                KcVerificationStatusTable.insert {
                    it[kcId] = kc.id
                    it[status] = VerificationStatus.faithful.name
                    it[KcVerificationStatusTable.contentHash] = contentHash
                    it[sourceSpanHash] = spanHash
                    it[updatedAt] = now
                }
            }
        }
    }

    /** Seed one ACTIVE RUBRIC_CRITERION FSRS card for (user, kc) so queue/today has data to surface. */
    private fun seedActiveCard(db: Database, userId: String, kcId: String, now: Instant) {
        transaction(db) {
            FsrsCardsTable.insert {
                it[id] = TutorTypes.ulid()
                it[FsrsCardsTable.userId] = userId
                it[sourceType] = FsrsSource.RUBRIC_CRITERION.name
                it[sourceRef] = kcId.take(32)
                it[front] = "e2e"
                it[back] = "e2e"
                it[difficulty] = 5.0
                it[stability] = 1.0
                it[retrievability] = 1.0
                it[dueAt] = now
                it[lastReviewedAt] = now
                it[lapses] = 0
                it[FsrsCardsTable.kcId] = kcId
                it[status] = CardStatus.ACTIVE.name
            }
        }
    }

    /** Minimal hand-rolled JSON (no serializer dependency on the wire): `{ sid, kcIds, figureKcIds }`. */
    private fun buildSeedJson(sid: String, kcIds: List<String>, figureKcIds: List<String>): String {
        fun arr(xs: List<String>) = xs.joinToString(",") { "\"${jsonEscape(it)}\"" }
        return """{"sid":"${jsonEscape(sid)}","kcIds":[${arr(kcIds)}],"figureKcIds":[${arr(figureKcIds)}]}"""
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}

/**
 * Thin CLI entry — `gradle :seedE2eDb` (JavaExec mainClass `jarvis.tutor.e2e.E2eSeedKt`,
 * build-gradle-seed-e2e.patch, PM-merge-only). Writes `build/e2e/tutor.db` + `build/e2e/seed.json`
 * (overridable via env). Aborts non-zero on a malformed corpus or an empty promoted/figure set.
 */
fun main() {
    val dbPath = System.getenv("E2E_SEED_DB") ?: "build/e2e/tutor.db"
    val contentDir = Path.of(System.getenv("E2E_SEED_CONTENT") ?: "content")
    val outJson = Path.of(System.getenv("E2E_SEED_JSON") ?: "build/e2e/seed.json")
    if (!Files.isDirectory(contentDir)) {
        System.err.println("[E2eSeed] content dir not found: $contentDir")
        kotlin.system.exitProcess(2)
    }
    val result = E2eSeed.seed(dbPath, contentDir, outJson)
    println(
        "[E2eSeed] OK — db=$dbPath json=$outJson\n" +
            "  user=${result.userId} sid=${result.sid.take(8)}…\n" +
            "  promoted (faithful, beat-complete): ${result.kcIds}\n" +
            "  figure-bearing: ${result.figureKcIds}",
    )
}
