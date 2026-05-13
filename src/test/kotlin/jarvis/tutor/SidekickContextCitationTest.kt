package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun systemContext_includesPrefetchBlockWhenHitsPresent() {
        val env = SidekickEnvelope(taskId = "01A", selection = "Laplace", userQuestion = "q")
        val hits = listOf(
            jarvis.HybridRetriever.HybridHit(
                source = "lexical",
                id = "_extras/PS/_fii/edu/files/Tema_A_en.md",
                snippet = "Laplace distribution has its probability density function defined by f(x).",
                score = 0.95,
            )
        )
        val ctx = SidekickContext.systemContext(env, prefetchedHits = hits)
        assertTrue(ctx.contains("source=\"prefetched_corpus\""),
            "system context must announce prefetched_corpus block: $ctx")
        assertTrue(ctx.contains("_extras/PS/_fii/edu/files/Tema_A_en.md"),
            "path must be visible to LLM so it can cite it: $ctx")
        assertTrue(ctx.contains("Laplace distribution"),
            "snippet content must be visible to LLM: $ctx")
    }

    @Test
    fun systemContext_omitsPrefetchBlockWhenHitsEmpty() {
        val env = SidekickEnvelope(taskId = "01A", selection = "Laplace", userQuestion = "q")
        val ctx = SidekickContext.systemContext(env, prefetchedHits = emptyList())
        assertFalse(ctx.contains("source=\"prefetched_corpus\""),
            "no prefetched block when hits list empty: $ctx")
    }

    @Test
    fun systemContext_truncatesSnippetAt200Chars() {
        val longSnippet = "x".repeat(500)
        val env = SidekickEnvelope(taskId = "01A", selection = "q", userQuestion = "q")
        val hits = listOf(
            jarvis.HybridRetriever.HybridHit(
                source = "lexical",
                id = "_extras/PS/foo.md",
                snippet = longSnippet,
                score = 0.5,
            )
        )
        val ctx = SidekickContext.systemContext(env, prefetchedHits = hits)
        // 200-char snippet plus path + score header; full 500-char run must NOT appear.
        assertFalse(ctx.contains("x".repeat(201)),
            "snippet must be capped at 200 chars per spec §4.3")
    }
}
