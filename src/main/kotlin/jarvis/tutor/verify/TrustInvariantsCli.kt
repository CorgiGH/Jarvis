package jarvis.tutor.verify

import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.MigrationBackupGate
import jarvis.tutor.TutorDb
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Plan-1 final gate — machine-checked "trust-net is ON" invariants over the REAL DB + the REAL
 * content corpus (spec §3.8 / §9.2 gate 2). Operator-run, PC-side: `gradle trustInvariants`.
 * READ-ONLY — never mutates the DB. Checks:
 *
 *   INV-3.3  fsrs_cards >= floor AND == the newest backup manifest's count (when a backup dir
 *            is supplied via JARVIS_BACKUP_DIR) — all 828 cards survived.
 *   SCHEMA   kc_verification_status carries the 7 frozen columns (lock §I.1); report_wrong
 *            carries resolved_by/resolved_at (D-RF3).
 *   FLIP     >= 1 row WHERE status='faithful' — the zero-faithful-rows-now-nonzero assertion.
 *   PARITY   every faithful row's content_hash == ContentReconcile.kcContentHashOf(claimsFor(kc))
 *            recomputed from content/ — the MACHINE parity check that replaces any manual PDF
 *            spot-check (no-oracle-inversion: the user never verifies content).
 */
object TrustInvariantsCli {

    data class Failure(val check: String, val detail: String)

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
            val ro = kc.beats["ro"]
            if (ro == null) {
                incomplete += "${kc.id}(no ro beats)"
                continue
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

    fun check(db: Database, contentDir: Path, backupDir: Path?, floor: Long): List<Failure> {
        val failures = ArrayList<Failure>()

        // ── INV-3.3: survival vs floor + the backup manifest ─────────────────────────────
        val cards = transaction(db) { MigrationBackupGate.liveFsrsCardCount(this) }
        if (cards < floor) failures += Failure("INV-3.3", "fsrs_cards=$cards < floor=$floor")
        if (backupDir != null) {
            val manifest = MigrationBackupGate(backupDir = backupDir, minExpectedCards = floor).newestManifest()
            when {
                manifest == null ->
                    failures += Failure("INV-3.3", "no *.manifest.json in $backupDir")
                manifest.fsrs_cards != cards ->
                    failures += Failure("INV-3.3", "fsrs_cards=$cards != backup manifest's ${manifest.fsrs_cards}")
            }
        }

        // ── SCHEMA: the frozen column sets ───────────────────────────────────────────────
        val kvs = transaction(db) { columnsOf("kc_verification_status") }
        val expectedKvs = setOf(
            "kc_id", "status", "last_audit_run_id", "content_hash",
            "lecture_grounded", "source_span_hash", "updated_at",
        )
        if (kvs.toSet() != expectedKvs) {
            failures += Failure("SCHEMA", "kc_verification_status=$kvs expected=$expectedKvs")
        }
        val rw = transaction(db) { columnsOf("report_wrong") }
        for (c in listOf("resolved_by", "resolved_at")) {
            if (c !in rw) failures += Failure("SCHEMA", "report_wrong missing $c (D-RF3)")
        }

        // ── FLIP: zero-faithful-rows-now-nonzero ─────────────────────────────────────────
        // Fail-soft on a PRE-MIGRATION schema (no content_hash column yet): the query throwing
        // means the trust columns are absent — report it as a FLIP failure, never crash.
        val faithfulRows: List<Pair<String, String?>> = runCatching {
            transaction(db) {
                val rows = ArrayList<Pair<String, String?>>()
                exec("SELECT kc_id, content_hash FROM kc_verification_status WHERE status='faithful'") { rs ->
                    while (rs.next()) rows.add(rs.getString(1) to rs.getString(2))
                }
                rows
            }
        }.getOrElse {
            failures += Failure("FLIP", "kc_verification_status not queryable with trust columns (pre-migration schema?): ${it.message?.take(120)}")
            emptyList()
        }
        if (faithfulRows.isEmpty() && failures.none { it.check == "FLIP" }) {
            failures += Failure("FLIP", "0 faithful rows in kc_verification_status — the trust-net is still OFF")
        }

        // ── PARITY: stored content_hash == recompute from the live corpus ────────────────
        if (faithfulRows.isNotEmpty()) {
            val repo = ContentRepo(contentDir)
            val kcsById = repo.loadManifest().subjects
                .flatMap { repo.loadSubject(it.id).kcs }
                .associateBy { it.id }
            for ((kcId, stored) in faithfulRows) {
                val kc = kcsById[kcId]
                when {
                    kc == null ->
                        failures += Failure("PARITY", "$kcId is faithful in the DB but absent from $contentDir")
                    stored == null ->
                        failures += Failure("PARITY", "$kcId is faithful with NULL content_hash (fails closed at serve — re-audit it)")
                    else -> {
                        val recomputed = ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
                        if (recomputed != stored) {
                            failures += Failure(
                                "PARITY",
                                "$kcId stored=$stored recomputed=$recomputed (content edited after the audit?)",
                            )
                        }
                    }
                }
            }
        }
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
                val kcStems = subjectKcs.flatMap { kcRow ->
                    kcRow.source.flatMap { jarvis.tutor.KcIdBackfill.kcDocStems(kcRow.subject, it.doc) }
                }.toSet()
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

        return failures
    }

    private fun org.jetbrains.exposed.sql.Transaction.columnsOf(table: String): List<String> {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        return cols
    }
}

/** Gradle `trustInvariants` main. Read-only. Exit 0 = ALL PASS; exit 1 = failures on stderr. */
fun main(args: Array<String>) {
    val contentDir = Path.of(
        args.getOrNull(0)
            ?: System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )
    val dbPath = System.getenv("JARVIS_TUTOR_DB")
        ?: System.getProperty("JARVIS_TUTOR_DB")
        ?: jarvis.Config.tutorDbPath
    val backupDir = System.getenv("JARVIS_BACKUP_DIR")?.takeIf { it.isNotBlank() }?.let(Path::of)
    val floor = MigrationBackupGate.defaultFloor()

    val failures = TrustInvariantsCli.check(TutorDb.connect(dbPath), contentDir, backupDir, floor)
    if (failures.isEmpty()) {
        println("[trustInvariants] ALL PASS (db=$dbPath, content=$contentDir, floor=$floor)")
        return
    }
    for (f in failures) System.err.println("[trustInvariants] FAIL ${f.check}: ${f.detail}")
    exitProcess(1)
}
