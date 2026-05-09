package jarvis

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2026-05-09 fix: BlockReminder didn't gate on QuietHours, so Pomodoro
 * pulses + start-block reminders fired at 7am during sleep — burning a
 * notification slot + writing a wake-the-user-up signal.
 */
class BlockReminderQuietTest {

    private val now = Instant.parse("2026-05-09T05:00:00Z") // 08:00 Bucharest

    private fun schedule(): ScheduleFile = ScheduleFile(
        blocks = listOf(
            ScheduleBlock(
                date = "2026-05-09",
                start = "08:03",
                end = "10:00",
                kind = "study",
                subject = "PA",
                topic = "Greedy",
            ),
        ),
    )

    @Test
    fun startReminderSuppressedWhenSleeping(@TempDir tmp: Path) {
        val file = tmp.resolve("signals.jsonl")
        // Block starts 3 min from now (within the 5-min lookahead window).
        // Presence reads as SLEEPING (no recent activity) → no reminder.
        val id = BlockReminder.tickOnce(
            file, schedule(), now,
            nudgeAllowed = { false },
        )
        assertNull(id, "sleeping/quiet must suppress start-block reminder")
        assertTrue(Signals.readAllFrom(file).isEmpty(), "no signal written")
    }

    @Test
    fun startReminderFiresWhenAwake(@TempDir tmp: Path) {
        val file = tmp.resolve("signals.jsonl")
        val id = BlockReminder.tickOnce(
            file, schedule(), now,
            nudgeAllowed = { true },
        )
        assertNotNull(id, "awake + non-quiet: start-block reminder fires")
        assertTrue(Signals.readAllFrom(file).size == 1)
    }
}
