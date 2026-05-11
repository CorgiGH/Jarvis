package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertTrue

class SidekickContextCitationTest {
    @Test
    fun systemContext_instructs_llm_to_cite_source_filenames() {
        val env = SidekickEnvelope(
            taskId = "01ABCD",
            userQuestion = "What does ps_c4 say about Laplace?",
        )
        val ctx = SidekickContext.systemContext(env)
        // Must instruct LLM to cite when retrieval used
        assertTrue(ctx.contains("(src:"), "system context should describe citation format: $ctx")
        assertTrue(ctx.contains("filename") || ctx.contains("source"),
            "system context should mention source filenames: $ctx")
    }
}
