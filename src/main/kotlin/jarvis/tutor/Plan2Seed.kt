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
