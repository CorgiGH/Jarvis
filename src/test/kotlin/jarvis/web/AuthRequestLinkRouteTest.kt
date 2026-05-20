package jarvis.web

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import jarvis.tutor.MagicLinkTokensTable
import jarvis.tutor.TutorContextKey
import jarvis.tutor.TutorDb
import jarvis.tutor.UsersTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthRequestLinkRouteTest {
    @Test fun `request-link issues a token and calls the mailer, always 200`() = testApplication {
        val dbDir = Files.createTempDirectory("reqlink")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable) }
        val fake = FakeMailer()
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = fake))
            installTutorRoutes()
        }
        val r = client.post("/auth/request-link") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"student@example.com","lang":"ro"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("ok"))
        assertEquals(1, fake.sent.size)
        assertEquals("student@example.com", fake.sent.single().first)
        val link = fake.sent.single().second
        assertTrue(link.contains("/auth/verify?token="), "link must point at /auth/verify: $link")
        assertFalse(link.contains("/tutor/"), "link must NOT have a /tutor prefix: $link")
    }

    @Test fun `a malformed email is rejected with 400`() = testApplication {
        val dbDir = Files.createTempDirectory("reqlink2")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, MagicLinkTokensTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.post("/auth/request-link") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"not-an-email","lang":"ro"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}
