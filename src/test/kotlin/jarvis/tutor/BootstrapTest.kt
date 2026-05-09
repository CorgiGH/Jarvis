package jarvis.tutor

import io.ktor.server.testing.*
import jarvis.web.installTutorContext
import jarvis.web.installTutorRoutes
import kotlin.test.Test
import kotlin.test.assertNotNull

class BootstrapTest {
    @Test
    fun `installTutorContext attaches TutorContext to application`() = testApplication {
        val tmp = java.nio.file.Files.createTempDirectory("boot")
        // ApplicationTestBuilder.application is internal in ktor 3.0.1; capture
        // the live Application receiver from inside the application{} lambda
        // so we can read attributes after the test engine has booted.
        var captured: jarvis.tutor.TutorContext? = null
        application {
            installTutorContext(dbPath = tmp.resolve("tutor.db").toString(), ledgerDir = tmp)
            installTutorRoutes()
            captured = attributes[TutorContextKey]
        }
        startApplication()
        assertNotNull(captured)
    }
}
