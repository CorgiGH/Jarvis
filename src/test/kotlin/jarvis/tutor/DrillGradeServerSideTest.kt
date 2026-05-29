package jarvis.tutor

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.cookie
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2 server-side grading contract: the grade route must resolve Problem from
 * the persisted TaskPrep (server-side) and use Problem.kcIds / Problem.canonicalAnswer
 * — client-supplied conceptIds and canonicalAnswer are never trusted.
 */
class DrillGradeServerSideTest {
    private class FakeGraderLlm(private val json: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            json to "fake-grader-model"
    }

    @AfterEach fun resetSeam() { drillGraderLlmFactory = { jarvis.OpenRouterChatLlm() } }

    private fun Application.installFreshTutor(tmp: Path) {
        install(ServerContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
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

    private val COHERENT = """{"correct":true,"rubric":{"numeric":true,"mechanism":true},""" +
        """"score":1.0,"misconception":null,"elaborated_feedback":"ok"}"""

    @Test
    fun `mastery records on persisted Problem kcIds, ignoring client conceptIds`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        TaskPrepRepo(ctx!!.db).upsert(TaskPrep(
            taskId = "task-1", generatedAt = Instant.now(), version = 1,
            problemsJson = Json.encodeToString(ListSerializer(Problem.serializer()),
                listOf(Problem(problemId = "d1", page = 1, statement = "6*7?",
                    kcIds = listOf("pa-kc-002"), canonicalAnswer = "42"))),
            drillsJson = "{}", railJson = "[]",
        ))
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"42","problemStatement":"6*7?","expectedAnswerHint":"42","conceptIds":["WRONG-KC"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"answerMatch\":true"), "server canonical used; body=${resp.bodyAsText()}")
        assertNotNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-002"), "records on persisted Problem.kcIds")
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "WRONG-KC"), "client conceptIds NOT trusted")
    }

    @Test
    fun `confident grade with no persisted kcIds records nothing and recorded is false`(@TempDir tmp: Path) = testApplication {
        drillGraderLlmFactory = { FakeGraderLlm(COHERENT) }
        var ctx: TutorContext? = null
        application { installFreshTutor(tmp); ctx = attributes[TutorContextKey] }
        startApplication()
        val (userId, sid) = seedSession(ctx!!)
        val csrf = "test-csrf-12345"
        val client = createClient {
            install(HttpCookies); install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val resp = client.post("/api/v1/drill/grade") {
            cookie("jarvis_session", sid); cookie("csrf", csrf); header("X-CSRF-Token", csrf)
            contentType(ContentType.Application.Json)
            setBody("""{"taskId":"task-1","problemId":"d1","userAttempt":"x","problemStatement":"p","expectedAnswerHint":"h","conceptIds":["pa-kc-001"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"recorded\":false"), "no server kcIds → not recorded; body=${resp.bodyAsText()}")
        assertNull(KcMasteryRepo(ctx!!.db).get(userId, "pa-kc-001"), "client conceptIds must not record")
    }
}
