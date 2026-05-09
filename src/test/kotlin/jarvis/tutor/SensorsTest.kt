package jarvis.tutor

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SensorsTest {
    private fun freshDb() = TutorDb.connect(
        Files.createTempDirectory("sens-test").resolve("t.db").toString()
    ).also { db -> transaction(db) { SchemaUtils.create(UsersTable, SensorEventsTable) } }

    @Test
    fun `TextDocSnapshot payload round-trips`() {
        val payload: SensorPayload = SensorPayload.TextDocSnapshot(
            uri = "file:///c/x.R", version = 17, lang = "r",
            selection = Range(Position(3, 5), Position(3, 12)),
            viewport = null, diagSummary = DiagSummary(errors = 1, warnings = 0)
        )
        val json = TutorTypes.tutorJson.encodeToString(SensorPayload.serializer(), payload)
        val back = TutorTypes.tutorJson.decodeFromString(SensorPayload.serializer(), json)
        assertIs<SensorPayload.TextDocSnapshot>(back)
        assertEquals(17, back.version)
    }

    @Test
    fun `append assigns monotonic eventSeq per source`() {
        val db = freshDb()
        val u = TutorTypes.ulid()
        UserRepo(db).insert(User(u, "v", UserScope.OWNER, Instant.now(), Instant.now()))
        val repo = SensorRepo(db)
        val a = repo.append(u, "vscode-1", "0.1", null,
            SensorPayload.WindowFocus("VSCode", "x.R", "Code.exe"))
        val b = repo.append(u, "vscode-1", "0.1", null,
            SensorPayload.WindowFocus("VSCode", "x.R", "Code.exe"))
        val c = repo.append(u, "rstudio-1", "0.1", null,
            SensorPayload.WindowFocus("RStudio", "y.R", "rstudio.exe"))
        assertEquals(1L, a.eventSeq)
        assertEquals(2L, b.eventSeq)
        assertEquals(1L, c.eventSeq)
    }
}
