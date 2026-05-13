package jarvis.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorEventLogTest {

    @Test
    fun `appends drill_grade event with redacted R-code body`() = runBlocking {
        val dir: Path = Path.of(System.getProperty("java.io.tmpdir"), "tutorlog-${System.nanoTime()}")
        java.io.File(dir.toString()).mkdirs()
        val log = TutorEventLog(privateDir = dir.toFile())
        val evt = TutorEvent(
            event_type = "drill_grade",
            event_id = "01KR6K07T6PATPRR5KH1JXYF8E",
            ts_utc = "2026-05-13T17:55:55Z",
            task_id = "task-1",
            session_id = "sess-1",
            prompt_template_id = "drill-grader-v3",
            system_prompt_sha256 = "abc",
            retrieved_context_summary = listOf("path1:snippet"),
            llm_input_full = null,
            llm_input_redacted = RcodeRedacted(
                rcode_sha256 = "deadbeef".repeat(8),
                preview_head = "library(VGAM); rlaplace(10000, 0, 1)",
                preview_tail = "hist(s, breaks=50)",
                length_chars = 142
            ),
            llm_output_full = "{\"correct\":true}",
            model_resolved = "qwen/qwen-2.5-7b:free",
            tokens_in = 200,
            tokens_out = 8,
            latency_ms = 4321,
            status = "ok",
            is_synthetic = false
        )
        log.append(evt)
        delay(200)  // wait for async writer
        log.flush()
        val today = java.time.LocalDate.now().toString()
        val file = dir.resolve("tutor_events.$today.jsonl")
        assertTrue(file.exists(), "log file should exist")
        val lines = file.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("\"event_type\":\"drill_grade\""))
        assertTrue(lines[0].contains("\"is_synthetic\":false"))
        assertTrue(lines[0].contains("\"rcode_sha256\":\"deadbeef"))
        assertTrue(!lines[0].contains("rlaplace(10000, 0, 1); hist(s, breaks=50)"),
                   "raw R-code body must NOT appear")
    }

    @Test
    fun `marks Y events as is_synthetic=true`() = runBlocking {
        val dir: Path = Path.of(System.getProperty("java.io.tmpdir"), "tutorlog2-${System.nanoTime()}")
        java.io.File(dir.toString()).mkdirs()
        val log = TutorEventLog(privateDir = dir.toFile())
        val evt = TutorEvent(
            event_type = "sidekick_ask",
            event_id = "synth-1",
            ts_utc = "2026-05-13T17:56:00Z",
            task_id = "task-1",
            session_id = "sess-y",
            prompt_template_id = "sidekick-v1",
            system_prompt_sha256 = "xyz",
            retrieved_context_summary = emptyList(),
            llm_input_full = "what is rlaplace?",
            llm_input_redacted = null,
            llm_output_full = "rlaplace is...",
            model_resolved = "qwen/qwen-2.5-7b:free",
            tokens_in = 5,
            tokens_out = 30,
            latency_ms = 1234,
            status = "ok",
            is_synthetic = true
        )
        log.append(evt)
        delay(200)
        log.flush()
        val today = java.time.LocalDate.now().toString()
        val file = dir.resolve("tutor_events.$today.jsonl")
        val lines = file.readLines()
        assertTrue(lines[0].contains("\"is_synthetic\":true"))
    }
}
