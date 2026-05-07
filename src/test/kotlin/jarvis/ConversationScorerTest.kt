package jarvis

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationScorerTest {

    @Test
    fun pinMarkerBoostsScore() {
        val plain = ConversationScorer.score("how do I sort a list")
        val pinned = ConversationScorer.score(
            "remember this: sealed classes can't be subclassed across modules",
        )
        assertTrue(pinned > plain, "pinned ($pinned) should beat plain ($plain)")
        assertTrue(pinned >= 0.7f, "pin marker should land ≥0.7, got $pinned")
    }

    @Test
    fun emotionWordBoostsScore() {
        val neutral = ConversationScorer.score("the function returns a list of strings")
        val stuck = ConversationScorer.score("I am completely stuck on this null pointer error")
        assertTrue(stuck > neutral, "stuck ($stuck) > neutral ($neutral)")
    }

    @Test
    fun shortQuestionPenalized() {
        val q = ConversationScorer.score("what is a coroutine?")
        val statement = ConversationScorer.score("a coroutine is a suspendable computation.")
        assertTrue(q < statement, "short question ($q) should score below statement ($statement)")
    }

    @Test
    fun pinMarkerBeatsQuestionPenalty() {
        // Both signals present; pin marker dominates so the question penalty
        // does NOT apply.
        val s = ConversationScorer.score("important: should I use Result or Either?")
        // base 0.4 + pin 0.3 = 0.7; question penalty suppressed by strongSignal
        assertEquals(0.7f, s, absoluteTolerance = 1e-4f,
            message = "pin should suppress short-question penalty")
    }

    @Test
    fun codeBlockSlightlyPenalized() {
        val codeOnly = ConversationScorer.score(
            "```\nfun add(a: Int, b: Int) = a + b\n```",
        )
        val mixed = ConversationScorer.score("here is the function definition.")
        assertTrue(codeOnly < mixed, "code-dominant ($codeOnly) < prose ($mixed)")
    }

    @Test
    fun emotionSuppressesCodePenalty() {
        val s = ConversationScorer.score(
            "stuck on this for an hour:\n```\nval x = listOf(1).first()\n```",
        )
        // base 0.4 + emotion 0.2 = 0.6, code penalty suppressed by strongSignal
        assertEquals(0.6f, s, absoluteTolerance = 1e-4f,
            message = "emotion should suppress code penalty")
    }

    @Test
    fun plainTaskTurnLandsAtBase() {
        val s = ConversationScorer.score("here is the function you asked for.")
        assertEquals(0.4f, s, absoluteTolerance = 1e-4f,
            message = "plain task turn → base 0.4")
    }

    @Test
    fun scoreClamped() {
        // Stack every positive signal — must still be ≤ 1.0.
        val maxish = ConversationScorer.score(
            "remember this: I am stuck and frustrated about the broken decision",
        )
        assertTrue(maxish in 0f..1f, "must clamp to [0,1], got $maxish")
        // Empty + only penalties — must still be ≥ 0.
        val empty = ConversationScorer.score("")
        assertTrue(empty in 0f..1f, "empty must be in [0,1], got $empty")
    }

    @Test
    fun caseInsensitive() {
        val a = ConversationScorer.score("REMEMBER THIS is critical")
        val b = ConversationScorer.score("remember this is critical")
        assertEquals(a, b, "scoring must be case-insensitive")
    }

    @Test
    fun longQuestionNotPenalized() {
        // Long question → not isShortQuestion. Stays at base.
        val long = "I want to understand the lifecycle of Kotlin coroutine " +
            "cancellation across nested scopes when one scope is a supervisor " +
            "scope and the parent gets cancelled mid-flight while children are " +
            "still running long-blocking IO operations and one of them throws."
        require(long.length > 200) { "test fixture must be > 200 chars" }
        val s = ConversationScorer.score(long + "?")
        // No emotion or pin; long → no question penalty → 0.4 base.
        assertEquals(0.4f, s, absoluteTolerance = 1e-4f,
            message = "long question should not be penalized")
    }

    @Test
    fun scoreFromEntry() {
        val entry = ConversationEntry(
            role = "user", content = "remember this: deploys need rollback first",
            ts = "2026-05-08T00:00:00Z", msgId = "m1",
        )
        val s = ConversationScorer.score(entry)
        assertEquals(0.7f, s, absoluteTolerance = 1e-4f,
            message = "entry overload should match content overload")
    }
}
