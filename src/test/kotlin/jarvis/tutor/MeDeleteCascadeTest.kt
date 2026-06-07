package jarvis.tutor

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import jarvis.web.FakeMailer
import jarvis.web.ME_DELETE_CASCADE_TABLES
import jarvis.web.ME_DELETE_RETAINED_USER_FK_TABLES
import jarvis.web.installTutorRoutes
import jarvis.web.testTutorContext
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * P0-1 (GDPR Art. 17 erasure) — the /me/delete cascade must purge EVERY user-scoped table.
 *
 * Under PRAGMA foreign_keys=ON (TutorDb.connect enables it) a single missing user-FK child table
 * makes the final UsersTable.deleteWhere FK-throw and roll the whole erasure back → /me/delete 500s
 * for any affected user (e.g. anyone who graded a drill → kc_mastery row; anyone with a provider
 * config). The council found KcMasteryTable + ProviderConfigTable both missing.
 *
 * This test does TWO things:
 *  1. CLASS-KILLER (fix-claim discipline) — reflects over the PRODUCTION table set
 *     [jarvis.tutor.ALL_TABLES], finds every table whose column foreign-keys UsersTable.id, and
 *     asserts each is covered by the erasure (in [ME_DELETE_CASCADE_TABLES], the retained
 *     allowlist, or is UsersTable itself). So the NEXT user-FK table cannot be silently missed.
 *  2. FUNCTIONAL — drives /me/delete over the FULL production schema with ≥1 seeded row in every
 *     user-FK child table (including kc_mastery + user_provider_config), asserts a 200 (no FK
 *     throw) and zero leftover rows.
 */
class MeDeleteCascadeTest {

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** The single column on [table] that foreign-keys UsersTable.id, or null if none. */
    private fun userFkColumn(table: Table): Column<*>? =
        table.columns.firstOrNull { col ->
            col.foreignKey?.targetTable === UsersTable && col.foreignKey?.target?.any { it === UsersTable.id } == true
        }

    /** Every PRODUCTION table that foreign-keys UsersTable.id (excluding UsersTable itself). */
    private fun userFkTables(): List<Table> =
        ALL_TABLES.filter { it !== UsersTable && userFkColumn(it) != null }

    // ── (1) CLASS-KILLER: erasure covers every user-FK table in the production schema ─────────────
    @Test
    fun `every user-FK table in the production schema is covered by the me-delete cascade`() {
        val covered: Set<Table> =
            (ME_DELETE_CASCADE_TABLES + ME_DELETE_RETAINED_USER_FK_TABLES + UsersTable).toSet()

        val missing = userFkTables().filter { it !in covered }

        if (missing.isNotEmpty()) {
            fail(
                "P0-1 GDPR-erasure gap: ${missing.size} table(s) foreign-key UsersTable.id but are " +
                    "NOT in the /me/delete cascade (ME_DELETE_CASCADE_TABLES) nor the retained " +
                    "allowlist (ME_DELETE_RETAINED_USER_FK_TABLES): " +
                    "${missing.map { it.tableName }}. Under PRAGMA foreign_keys=ON the erasure will " +
                    "FK-throw and roll back → /me/delete 500. Add each to the cascade (or, with a " +
                    "written GDPR 17(3) basis, to the retained allowlist).",
            )
        }

        // Specifically pin the two the council found, so a regression names them.
        assertTrue(
            KcMasteryTable in covered,
            "kc_mastery (user-FK) must be in the erasure cascade — every grader has a row",
        )
        assertTrue(
            ProviderConfigTable in covered,
            "user_provider_config (user-FK) must be in the erasure cascade",
        )
    }

    // ── (1b) every CASCADE entry is a real user-scoped table the route can delete by user_id ──────
    //    The route resolves the predicate column by the literal name "user_id" — assert each entry
    //    has exactly one such column (a missing/renamed one would make the route throw at runtime).
    //    NOTE: NOT every cascade entry has a DB-level FK — consent_log / user_preferences /
    //    ai_literacy_confirmation are user-scoped but FK-less by design (they cannot cause the P0-1
    //    FK-throw, but GDPR still requires erasing them), so this asserts user_id presence, not a FK.
    @Test
    fun `every cascade entry is a user-scoped table with exactly one user_id column`() {
        for (table in ME_DELETE_CASCADE_TABLES) {
            val userIdCols = table.columns.filter { it.name == "user_id" }
            assertEquals(
                1, userIdCols.size,
                "${table.tableName} is in the erasure cascade but does not have exactly one " +
                    "'user_id' column (the route deletes by that exact name)",
            )
        }
    }

    // ── (2) FUNCTIONAL: erasure over the FULL production schema, seeding the P0-1 tables ───────────
    //    Reproduces the exact council bug: under PRAGMA foreign_keys=ON a kc_mastery (and/or
    //    user_provider_config) row that the cascade does NOT delete makes the final
    //    UsersTable.deleteWhere FK-throw → the whole erasure rolls back → 500. Seeded over the WHOLE
    //    production schema (SchemaUtils.create(*ALL_TABLES)) so FK enforcement is the real thing, not
    //    a hand-curated subset. The Phase-1 tables stay covered by the original cascade test path
    //    below + the static reflection invariant above.
    @Test
    fun `me-delete purges kc_mastery + user_provider_config without an FK throw (P0-1)`() = testApplication {
        val dbDir = Files.createTempDirectory("me-delete-cascade-p0-1")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())

        // Build the WHOLE production schema (so the cascade runs against reality, not a curated subset).
        transaction(db) { SchemaUtils.create(*ALL_TABLES) }

        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "alice", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)

        // The two tables the council found missing — each is a user-FK child, so each on its own is
        // enough to FK-block the user-row delete before the fix.
        transaction(db) {
            KcMasteryTable.insert {
                it[userId] = uid; it[kcId] = "pa-kc-001"; it[ewmaScore] = 0.5; it[observations] = 1
                it[lastGradedAt] = Instant.now()
            }
            ProviderConfigTable.insert {
                it[userId] = uid; it[primaryProvider] = ProviderType.CLAUDE_CLI_RELAY.name
                it[relayEndpoint] = null; it[apiKeyEncryptedRef] = null; it[fallbackJson] = "[]"
            }
        }
        transaction(db) {
            assertEquals(1L, KcMasteryTable.selectAll().where { KcMasteryTable.userId eq uid }.count())
            assertEquals(1L, ProviderConfigTable.selectAll().where { ProviderConfigTable.userId eq uid }.count())
        }

        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }

        val r = client.post("/api/v1/me/delete") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status, "erasure must not FK-throw / 500 (P0-1)")
        assertNull(UserRepo(db).findById(uid), "the user row itself must be gone")

        transaction(db) {
            assertEquals(
                0L, KcMasteryTable.selectAll().where { KcMasteryTable.userId eq uid }.count(),
                "kc_mastery must be empty after me/delete",
            )
            assertEquals(
                0L, ProviderConfigTable.selectAll().where { ProviderConfigTable.userId eq uid }.count(),
                "user_provider_config must be empty after me/delete",
            )
        }
    }

    // ── (3) FUNCTIONAL (B6): the Phase-1 + G5 user-scoped tables still purge, driven off ALL_TABLES ─
    @Test
    fun `delete removes rows in all phase-1 + G5 tables and throws no FK error`() = testApplication {
        val dbDir = Files.createTempDirectory("me-delete-cascade-phase1")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())

        // Re-driven off the PRODUCTION table set (not a hand-curated subset).
        transaction(db) { SchemaUtils.create(*ALL_TABLES) }

        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "carol", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)

        transaction(db) {
            SessionSummariesTable.insert {
                it[id] = TutorTypes.ulid(); it[userId] = uid; it[startedAt] = Instant.now()
                it[closedAt] = Instant.now(); it[cardsReviewed] = 5; it[masteryDeltaJson] = "{}"
                it[narrative] = null
            }
            AttemptsTable.insert {
                it[id] = TutorTypes.ulid(); it[userId] = uid; it[kcId] = "pa-kc-001"
                it[taskId] = TutorTypes.ulid(); it[problemId] = "A1"; it[phase] = "practice"
                it[studentConfidence] = null; it[correct] = true; it[score] = 1.0
                it[scaffoldLevel] = 0; it[isFarTransfer] = false; it[selfExplanation] = null
                it[recorded] = true; it[gradedAt] = Instant.now()
            }
            ReportWrongTable.insert {
                it[id] = TutorTypes.ulid(); it[userId] = uid; it[kcId] = "pa-kc-001"; it[cardId] = null
                it[gradeAttemptRaw] = """{"score":0.5}"""; it[reportedAt] = Instant.now()
                it[resolution] = null
            }
            ExamDatesTable.insert {
                it[id] = TutorTypes.ulid(); it[userId] = uid; it[subject] = "PA"
                it[startAt] = Instant.now().plusSeconds(3600); it[createdAt] = Instant.now()
            }
            MockExamsTable.insert {
                it[id] = TutorTypes.ulid(); it[userId] = uid; it[subject] = "PA"
                it[questionsJson] = "[]"; it[createdAt] = Instant.now()
            }
        }

        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }

        val r = client.post("/api/v1/me/delete") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertNull(UserRepo(db).findById(uid))

        transaction(db) {
            assertEquals(0L, SessionSummariesTable.selectAll().where { SessionSummariesTable.userId eq uid }.count())
            assertEquals(0L, AttemptsTable.selectAll().where { AttemptsTable.userId eq uid }.count())
            assertEquals(0L, ReportWrongTable.selectAll().where { ReportWrongTable.userId eq uid }.count())
            assertEquals(0L, ExamDatesTable.selectAll().where { ExamDatesTable.userId eq uid }.count())
            assertEquals(0L, MockExamsTable.selectAll().where { MockExamsTable.userId eq uid }.count())
        }
    }

    // ── regression guard: erasure is safe even when the user has no rows in the new tables ─────────
    @Test
    fun `delete with no rows in new tables still succeeds`() = testApplication {
        val dbDir = Files.createTempDirectory("me-delete-empty-new")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())

        transaction(db) { SchemaUtils.create(*ALL_TABLES) }

        val uid = TutorTypes.ulid()
        UserRepo(db).insert(User(uid, "bob", UserScope.FRIEND, Instant.now(), Instant.now()))
        val sid = SessionRepo(db).create(uid, 3600)

        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }

        val r = client.post("/api/v1/me/delete") {
            header("Cookie", "jarvis_session=$sid; csrf=c")
            header("X-CSRF-Token", "c")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertNull(UserRepo(db).findById(uid))
    }
}
