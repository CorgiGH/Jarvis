package jarvis

import jarvis.win32.Win32
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.io.path.createDirectories

object ActivityCapture {
    private val json = Json { encodeDefaults = true }

    fun snapshot(): ActivityEntry {
        Config.stateDir.createDirectories()
        val w = Win32.activeWindow()
        val entry = ActivityEntry(
            ts = Instant.now().toString(),
            title = w.title,
            process = w.processName,
            pid = w.pid,
        )
        val line = json.encodeToString(ActivityEntry.serializer(), entry) + "\n"
        Files.writeString(
            Config.activityFile,
            line,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
        return entry
    }
}
