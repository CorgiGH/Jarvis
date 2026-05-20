package jarvis.web

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossTenantIsolationTest {

    @Test fun `user A cannot see user B's fsrs cards or gaps`() = testApplication {
        // ── 1. Setup: isolated temp DB ─────────────────────────────────────────
        val dbDir = Files.createTempDirectory("crosstenant")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) {
            SchemaUtils.create(
                UsersTable, SessionsTable,
                MagicLinkTokensTable, AiLiteracyConfirmationTable,
                ConsentLogTable, UserPreferencesTable,
                TasksTable,
                FsrsCardsTable, KnowledgeGapsTable,
            )
        }

        // ── 2. Seed two users ──────────────────────────────────────────────────
        val uidA = TutorTypes.ulid()
        val uidB = TutorTypes.ulid()
        val now = Instant.now()
        UserRepo(db).insert(User(uidA, "UserA", UserScope.FRIEND, now, now, email = "a@example.io"))
        UserRepo(db).insert(User(uidB, "UserB", UserScope.FRIEND, now, now, email = "b@example.io"))

        // ── 3. Seed FSRS cards: one for A, one for B ──────────────────────────
        // Cards are due in the past (dueAt <= now) so they appear in /api/v1/fsrs/due.
        // The marker "B-ONLY-KC" appears in user B's card front + back so it
        // will show up verbatim in the JSON body if the isolation is broken.
        val cardState = FsrsState(
            difficulty = 0.3,
            stability = 1.0,
            retrievability = 0.9,
            dueAt = now.minusSeconds(3600),   // due 1 hour ago
            lastReviewedAt = now.minusSeconds(86400),
            lapses = 0,
        )
        val cardRepo = FsrsCardRepo(db)
        cardRepo.insert(TutorCard(
            id = TutorTypes.ulid(),
            userId = uidA,
            source = FsrsSource.MANUAL,
            sourceRef = "A-ONLY-KC",
            front = "A-ONLY-KC question",
            back = "A-ONLY-KC answer",
            state = cardState,
        ))
        cardRepo.insert(TutorCard(
            id = TutorTypes.ulid(),
            userId = uidB,
            source = FsrsSource.MANUAL,
            sourceRef = "B-ONLY-KC",
            front = "B-ONLY-KC question",
            back = "B-ONLY-KC answer",
            state = cardState,
        ))

        // ── 4. Seed knowledge gaps: one for A, one for B ──────────────────────
        // The marker "B-ONLY-KC" appears in user B's gap topic + content so it
        // will show up verbatim in the JSON body if the isolation is broken.
        val gapRepo = KnowledgeGapRepo(db, dbDir)
        gapRepo.append(KnowledgeGap(
            id = TutorTypes.ulid(),
            userId = uidA,
            taskId = null,
            topic = "A-ONLY-KC topic",
            language = "kotlin",
            type = GapType.CONCEPT,
            trigger = GapTrigger.EXPLICIT_ASK,
            filledAt = now,
            source = GapSource.LLM_GROUNDED,
            content = "A-ONLY-KC content",
            exampleCode = null,
            sourceCitation = null,
            resolvedBy = null,
            reusedCount = 0,
            fsrsCardId = null,
        ))
        gapRepo.append(KnowledgeGap(
            id = TutorTypes.ulid(),
            userId = uidB,
            taskId = null,
            topic = "B-ONLY-KC topic",
            language = "kotlin",
            type = GapType.CONCEPT,
            trigger = GapTrigger.EXPLICIT_ASK,
            filledAt = now,
            source = GapSource.LLM_GROUNDED,
            content = "B-ONLY-KC content",
            exampleCode = null,
            sourceCitation = null,
            resolvedBy = null,
            reusedCount = 0,
            fsrsCardId = null,
        ))

        // ── 5. Create a session for user A only ───────────────────────────────
        val sidA = SessionRepo(db).create(uidA, 3600)

        // ── 6. Wire up the application ────────────────────────────────────────
        // ContentNegotiation must be installed here (it lives in WebMain.kt in
        // production); testApplication only runs the blocks we configure.
        application {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }

        // ── 7. Assert: /api/v1/fsrs/due scoped to A — must not contain B's data
        val dueResp = client.get("/api/v1/fsrs/due") {
            header("Cookie", "jarvis_session=$sidA")
        }
        assertEquals(HttpStatusCode.OK, dueResp.status, "/api/v1/fsrs/due should return 200 for valid session")
        val dueBody = dueResp.bodyAsText()
        // A's own card must be present (proves the endpoint is not trivially empty)
        assertTrue(dueBody.contains("A-ONLY-KC"), "A's own card must appear in /fsrs/due — got: $dueBody")
        // B's card must NOT appear — this is the core isolation invariant
        assertFalse(dueBody.contains("B-ONLY-KC"), "B's card MUST NOT appear in A's /fsrs/due — LEAK DETECTED: $dueBody")

        // ── 8. Assert: /api/v1/gaps scoped to A — must not contain B's data
        val gapsResp = client.get("/api/v1/gaps") {
            header("Cookie", "jarvis_session=$sidA")
        }
        assertEquals(HttpStatusCode.OK, gapsResp.status, "/api/v1/gaps should return 200 for valid session")
        val gapsBody = gapsResp.bodyAsText()
        // A's own gap must be present (proves the endpoint is not trivially empty)
        assertTrue(gapsBody.contains("A-ONLY-KC"), "A's own gap must appear in /gaps — got: $gapsBody")
        // B's gap must NOT appear — this is the core isolation invariant
        assertFalse(gapsBody.contains("B-ONLY-KC"), "B's gap MUST NOT appear in A's /gaps — LEAK DETECTED: $gapsBody")
    }
}
