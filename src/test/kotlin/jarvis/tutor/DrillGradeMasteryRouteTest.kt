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
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.testApplication
import jarvis.ChatMessage
import jarvis.Llm
import jarvis.web.drillGraderLlmFactory
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * E1 trustworthy-grader integration: POST /api/v1/drill/grade must apply the
 * deterministic scoring layer and record per-KC mastery ONLY for confident
 * grades. A fake grader Llm is injected through [drillGraderLlmFactory] (the
 * Step-B test seam) so the deterministic + mastery-write path runs without a
 * live model.
 */
class DrillGradeMasteryRouteTest {

    companion object {
        // The drill-grade handler always appends to TutorEventLog.GLOBAL, whose
        // dir freezes on FIRST access across the JVM. If this class runs before
        // TutorRoutesTest, pin the dir so GLOBAL does not freeze to the
        // production /opt/jarvis/data/private default (which fails on Windows /
        // pollutes the box). We use a manually-created temp dir (NOT @TempDir):
        // GLOBAL's async writer coroutine outlives the test methods, and a
        // per-class @TempDir gets cleaned out from under the still-running
        // writer, raising a FileNotFoundException on a background thread that
        // surfaces as a spurious test failure. A non-auto-cleaned dir avoids
        // that race. Only set if unset so we don't fight a dir another class
        // already pinned.
        @JvmStatic
        @BeforeAll
        fun pinEventLogDir() {
            if (System.getProperty("jarvis.tutor.event_log.dir") == null) {
                val dir = Files.createTempDirectory("drill-grade-evtlog")
                System.setProperty("jarvis.tutor.event_log.dir", dir.toString())
            }
        }
    }

    /** A fake Llm that returns a fixed grader JSON body regardless of input. */
    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(
            messages: List<ChatMessage>,
            maxTokens: Int,
            responseFormat: String?,
        ): Pair<String, String> = json to "fake-grader-model"
    }

    @AfterEach
    fun resetSeam() {
        // Restore the production factory so we don't leak the fake into other
        // tests that share the same JVM (the seam is a module-level var).
        drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() }
    }

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        installTutorContext(tmp.resolve("t.db").toString(), tmp)
        installTutorRoutes()
    }

    private fun seedSession(ctx: TutorContext): Pair<String, String> {
        val userId = TutorTypes.ulid()
        UserRepo(ctx.db).insert(User(userId, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val sid = SessionRepo(ctx.db).create(userId, ttlSeconds = 3600)
        AiLiteracyRepo(ctx.db).confirm(userId, AI_LITERACY_VERSION, "ro")
        return userId to sid
    }

    private fun gradeBody(conceptIds: List<String>): String {
        val ids = conceptIds.joinToString(",") { "\"$it\"" }
        return """{
            "taskId":"task-1",
            "problemId":"d1",
            "userAttempt":"the median is 3",
            "problemStatement":"compute the median",
            "expectedAnswerHint":"3",
            "conceptIds":[$ids]
        }""".trimIndent()
    }

    @Test
    fun `confident grade records mastery`(@TempDir tmp: Path) = testApplication {
        // COHERENT GradeResult: correct=true AND every rubric item true →
        // GradeScoring.isConfident == true → grade is recorded.
        drillGraderLlmFactory = {
            FakeGraderLlm(
                """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
                    """"score":1.0,"misconception":null,"elaborated_feedback":"correct"}""",
            )
        }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(gradeBody(listOf("pa-kc-001")))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNotNull(mastery, "confident grade must record KC mastery")
        assertEquals(1, mastery.observations, "exactly one observation recorded")
    }

    @Test
    fun `deferred incoherent grade records nothing`(@TempDir tmp: Path) = testApplication {
        // INCOHERENT GradeResult: correct=true but a rubric item is false →
        // correctFromRubric == false → disagrees with llm.correct →
        // GradeScoring.isConfident == false → grade is deferred (not recorded).
        drillGraderLlmFactory = {
            FakeGraderLlm(
                """{"correct":true,"rubric":{"numeric":true,"mechanism":false},""" +
                    """"score":0.5,"misconception":"OTHER","elaborated_feedback":"partial"}""",
            )
        }

        var ctx: TutorContext? = null
        application {
            installFreshTutor(tmp)
            ctx = attributes[TutorContextKey]
        }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies)
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid)
            cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody(gradeBody(listOf("pa-kc-001")))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val mastery = KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001")
        assertNull(mastery, "incoherent (deferred) grade must NOT record KC mastery")
    }
}
