package jarvis.web

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import jarvis.tutor.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoSessionGuardTest {
    @Test fun `auto-session no longer auto-logs-in an unauthenticated caller`() = testApplication {
        val dbDir = Files.createTempDirectory("autosess")
        val db = TutorDb.connect(dbDir.resolve("t.db").toString())
        transaction(db) { SchemaUtils.create(UsersTable, SessionsTable, MagicLinkTokensTable) }
        application {
            attributes.put(TutorContextKey, testTutorContext(db, dbDir, mailer = FakeMailer()))
            installTutorRoutes()
        }
        val r = client.get("/api/v1/tutor/auto-session")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
