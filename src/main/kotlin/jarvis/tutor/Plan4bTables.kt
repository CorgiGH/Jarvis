package jarvis.tutor

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Plan 4b Task 6 — new DB table: [LanguageCheckTable].
 *
 * Language-check admission record: one row per (KC, field, language) checked by [LanguageGate]
 * during [ContentReconcile.reconcile]. Written at ADMISSION/SYNC time (never at serve time —
 * R-4b-Q6). Allows INV-8.1's query: "served fields lacking a language_check record → 0 rows"
 * (every served field passes admission ⇒ every served RO field has a record).
 *
 * Registration in [TutorMigration] ALL_TABLES is a PM-MERGE-ONLY patch (migration-language-check.patch
 * at CP-3, applied behind the INV-3.1 backup gate on BOTH DBs). Tests create it directly via
 * [org.jetbrains.exposed.sql.SchemaUtils.create] (the LessonServeRouteTest freshDb pattern).
 *
 * Honest note: the LIVE DB has 0 `kc_verification_status` rows (trust-net never run in production)
 * → INV-8.1 is vacuously 0-row on live until the trust-net flip. The CI proof runs on the seeded DB.
 */
object LanguageCheckTable : Table("language_check") {
    /** The KC id (or misconception id) whose field was checked. */
    val kcId = varchar("kc_id", 64)
    /** The field path, e.g. "name_ro", "beats.ro.predict.prompt". */
    val field = varchar("field", 256)
    /** Language code checked, e.g. "ro". */
    val lang = varchar("lang", 8)
    /** "pass" | "fail" */
    val status = varchar("status", 8)
    /** Detailed violation message from LanguageGate; null when status="pass". */
    val detail = text("detail").nullable()
    /** [LanguageGate.VERSION] at the time of the check; allows re-checking on gate version bump. */
    val validatorVersion = varchar("validator_version", 16)
    val checkedAt = timestamp("checked_at")
    override val primaryKey = PrimaryKey(kcId, field, lang)
}
