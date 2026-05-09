package jarvis.tutor

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

@Serializable
data class DiagSummary(val errors: Int, val warnings: Int)

@Serializable
sealed class SensorPayload {
    @Serializable
    data class TextDocSnapshot(
        val uri: String, val version: Int, val lang: String,
        val selection: Range?, val viewport: Range?, val diagSummary: DiagSummary?,
    ) : SensorPayload()

    @Serializable
    data class ConsoleEvent(
        val lang: String, val command: String,
        val outputDigest: String, val errorMessage: String?,
    ) : SensorPayload()

    @Serializable
    data class WindowFocus(val app: String, val title: String, val processName: String) : SensorPayload()

    @Serializable
    data class ClipboardEvent(val digest: String) : SensorPayload()

    @Serializable
    data class ScreenshotMeta(
        val capturedAt: String, val focusedRegion: String?, val ocrSummary: String,
    ) : SensorPayload()
}

data class SensorEvent(
    val v: Int = 1,
    val sensorId: String,
    val sensorVersion: String,
    val eventSeq: Long,
    val ts: Instant,
    val source: String,
    val taskId: String?,
    val payload: SensorPayload,
)

object SensorEventsTable : Table("sensor_events") {
    val sensorId = varchar("sensor_id", 64)
    val seq = long("seq")
    val sensorVersion = varchar("sensor_version", 32)
    val ts = timestamp("ts")
    val sourceCol = varchar("source", 32)
    val userId = varchar("user_id", 26).references(UsersTable.id)
    val taskId = varchar("task_id", 26).nullable()
    val payloadJson = text("payload_json")
    override val primaryKey = PrimaryKey(sensorId, seq)
}

class SensorRepo(private val db: Database) {
    fun append(
        userId: String, sensorId: String, sensorVersion: String,
        taskId: String?, payload: SensorPayload,
    ): SensorEvent = transaction(db) {
        val lastSeq = SensorEventsTable
            .selectAll()
            .where { SensorEventsTable.sensorId eq sensorId }
            .maxByOrNull { it[SensorEventsTable.seq] }
            ?.let { it[SensorEventsTable.seq] } ?: 0L
        val nextSeq = lastSeq + 1
        val now = Instant.now()
        val src = sensorId.substringBefore('-')
        SensorEventsTable.insert {
            it[SensorEventsTable.sensorId] = sensorId
            it[seq] = nextSeq
            it[SensorEventsTable.sensorVersion] = sensorVersion
            it[ts] = now
            it[sourceCol] = src
            it[SensorEventsTable.userId] = userId
            it[SensorEventsTable.taskId] = taskId
            it[payloadJson] = TutorTypes.tutorJson.encodeToString(SensorPayload.serializer(), payload)
        }
        SensorEvent(1, sensorId, sensorVersion, nextSeq, now, src, taskId, payload)
    }
}
