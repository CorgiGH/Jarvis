package jarvis

import kotlinx.serialization.json.Json

private const val INTERVAL_SECONDS = 300L

internal fun runLogger(once: Boolean) {
    val pretty = Json { prettyPrint = true; encodeDefaults = true }
    if (once) {
        val entry = ActivityCapture.snapshot()
        println(pretty.encodeToString(ActivityEntry.serializer(), entry))
        return
    }
    println("Activity logger started. Interval: ${INTERVAL_SECONDS}s. Output: ${Config.activityFile}")
    while (true) {
        try {
            ActivityCapture.snapshot()
        } catch (e: Exception) {
            System.err.println("[activity_logger] error: ${e.javaClass.simpleName}: ${e.message}")
        }
        try {
            Thread.sleep(INTERVAL_SECONDS * 1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            break
        }
    }
}
