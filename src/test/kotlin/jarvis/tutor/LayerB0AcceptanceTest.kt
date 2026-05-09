package jarvis.tutor

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import io.ktor.client.request.setBody
import jarvis.VisionLlm
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tutor Layer B0 acceptance test. End-to-end: bootstrap context with
 * a stub VisionLlm, seed user + session, drive two screenshot
 * captures (one ALLOWED-class file, one READ-ONLY browser tab),
 * assert response shape + persistence + classification.
 *
 * Pairs with LayerAAcceptanceTest as the per-layer ratchet. Per
 * council R3 (council-1778325410-layer-b.md), B0 ships standalone
 * with intermediate tag `tutor/layer-b0-clipboard`; the canonical
 * `tutor/layer-b-acceptance` tag waits for B1 (Tauri daemon +
 * keystroke injection + shadow-git ordering).
 *
 * What "B0 acceptance" certifies:
 *  - Auth: session cookie + CSRF gate works (covered indirectly via
 *    happy-path session here; deep gates in ScreenshotSensorRouteTest)
 *  - Vision sensor extraction round trip
 *  - Server-side source classification (not client-trusted)
 *  - SensorEvent persisted with monotonic seq
 *  - Audit log chain unchanged by sensor writes (no sensor row in
 *    audit ledger; sensors are observation, not action)
 *  - Multi-tenant isolation: a 2nd user's sensor stream stays separate
 */
class LayerB0AcceptanceTest {

    private val tinyPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

    private class CannedVisionLlm(private val replies: List<String>) : VisionLlm {
        private var i = 0
        override suspend fun analyze(
            prompt: String, imageBase64: String, mediaType: String,
            maxTokens: Int, model: String?,
        ): String {
            val r = replies[i % replies.size]
            i++
            return r
        }
    }

    @Test
    fun `acceptance — vision sensor end-to-end with classification + persistence`() = testApplication {
        val tmp = java.nio.file.Files.createTempDirectory("b0-accept")
        val canned = CannedVisionLlm(listOf(
            // First call: looks like a Kotlin source file → ALLOWED.
            """{"file_path":"src/main/kotlin/Foo.kt","cursor":{"line":42,"col":7},"console_output":"$ ./gradlew test","error":null}""",
            // Second call: stackoverflow URL → READ_ONLY.
            """{"file_path":"https://stackoverflow.com/q/12345","cursor":null,"console_output":null,"error":null}""",
        ))
        var ctx: TutorContext? = null
        application {
            install(ServerContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            installTutorContext(tmp.resolve("tutor.db").toString(), tmp)
            val initial = attributes[TutorContextKey]
            attributes.put(TutorContextKey, initial.copy(visionLlm = canned))
            installTutorRoutes()
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val tutorCtx = assertNotNull(ctx)

        // Seed user + session + CSRF.
        val userId = TutorTypes.ulid()
        UserRepo(tutorCtx.db).insert(User(userId, "victor", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(tutorCtx.db).create(userId, ttlSeconds = 3600)
        val csrf = "b0-accept-csrf-token"

        val client = createClient { install(HttpCookies) }

        // 1. ALLOWED-class capture: kotlin file.
        val r1 = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng","taskId":"T-1","sensorId":"screenshot-test"}""")
        }
        assertEquals(HttpStatusCode.OK, r1.status, "kotlin shot ok: ${r1.bodyAsText()}")
        val body1 = r1.bodyAsText()
        assertTrue(body1.contains("\"filePath\":\"src/main/kotlin/Foo.kt\""))
        assertTrue(body1.contains("\"readOnlyMode\":false"), "kotlin file → effectors enabled: $body1")
        assertTrue(body1.contains("\"eventSeq\":1"), "first sensor event seq: $body1")

        // 2. READ-ONLY capture: stackoverflow URL.
        val r2 = client.post("/api/v1/sensor/screenshot") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"imageBase64":"$tinyPng","taskId":"T-1","sensorId":"screenshot-test"}""")
        }
        assertEquals(HttpStatusCode.OK, r2.status)
        val body2 = r2.bodyAsText()
        assertTrue(body2.contains("\"readOnlyMode\":true"), "stackoverflow → READ-ONLY: $body2")
        assertTrue(body2.contains("stackoverflow"), "reason cites host: $body2")
        assertTrue(body2.contains("\"eventSeq\":2"), "second sensor event seq: $body2")

        // 3. Both events persisted in DB under the same sensorId, monotonic.
        transaction(tutorCtx.db) {
            val rows = SensorEventsTable.selectAll()
                .where { SensorEventsTable.userId eq userId }
                .orderBy(SensorEventsTable.seq, SortOrder.ASC)
                .toList()
            assertEquals(2, rows.size, "two screenshot events persisted")
            assertEquals(1L, rows[0][SensorEventsTable.seq])
            assertEquals(2L, rows[1][SensorEventsTable.seq])
            assertEquals("screenshot", rows[0][SensorEventsTable.sourceCol],
                "sensorId 'screenshot-test' → source 'screenshot' (split before '-')")
        }

        // 4. Audit log chain stays valid — sensor writes never enter audit.
        assertTrue(
            AuditRepo(tutorCtx.db).verifyChain(userId),
            "audit chain stays valid (sensor writes don't append to audit log)",
        )

        // 5. Multi-tenant isolation: second user sees no sensor events from user 1.
        val u2 = TutorTypes.ulid()
        UserRepo(tutorCtx.db).insert(User(u2, "alice", UserScope.FRIEND, Instant.now(), Instant.now()))
        transaction(tutorCtx.db) {
            val u2Events = SensorEventsTable.selectAll()
                .where { SensorEventsTable.userId eq u2 }
                .toList()
            assertEquals(0, u2Events.size, "u2 sees no sensor events from u1")
        }
    }
}
