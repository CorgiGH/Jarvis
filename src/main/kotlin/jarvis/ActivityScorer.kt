package jarvis

import java.time.Duration
import java.time.Instant

/**
 * Phase 1.1 — heuristic importance scorer for [ActivityEntry] rows.
 *
 * Pure function: ([entry], [recent]) → Float in 0..1. No I/O, no mutation. The
 * caller (currently [Activity.append]) is responsible for loading `recent`.
 *
 * Score composition:
 *   base(process, title)         per-process / per-domain prior
 * + keyword_bonus(title)         signals user is hitting an error / TODO
 * + continuity_bonus(recent)     long uninterrupted focus window in same proc
 * - distraction_penalty(recent)  many distinct processes in the last 5 min
 * → coerceIn(0f, 1f)
 *
 * Heuristic only — Phase 5 may rewrite weights from labelled feedback.
 */
object ActivityScorer {

    fun score(entry: ActivityEntry, recent: List<ActivityEntry>): Float {
        val base = baseScore(entry.process, entry.title)
        val kw = keywordBonus(entry.title)
        val cont = continuityBonus(entry, recent)
        val dist = distractionPenalty(entry, recent)
        return (base + kw + cont - dist).coerceIn(0f, 1f)
    }

    private val IDE_PROCS = setOf(
        "code.exe", "code-insiders.exe",
        "idea64.exe", "idea.exe", "studio64.exe", "studio.exe",
        "rider64.exe", "webstorm64.exe", "pycharm64.exe", "clion64.exe",
        "phpstorm64.exe", "rubymine64.exe", "goland64.exe",
        "vim.exe", "nvim.exe", "emacs.exe", "sublime_text.exe",
        "devenv.exe", "xcode.exe",
    )
    private val TERMINAL_PROCS = setOf(
        "powershell.exe", "pwsh.exe", "cmd.exe",
        "wt.exe", "windowsterminal.exe",
        "bash.exe", "alacritty.exe", "wezterm.exe", "terminal.exe",
    )
    private val BROWSER_PROCS = setOf(
        "chrome.exe", "firefox.exe", "msedge.exe", "brave.exe",
        "opera.exe", "vivaldi.exe", "arc.exe",
    )
    private val COMMS_PROCS = setOf(
        "slack.exe", "teams.exe", "discord.exe",
        "outlook.exe", "thunderbird.exe", "zoom.exe",
    )
    private val ENTERTAINMENT_PROCS = setOf(
        "spotify.exe", "vlc.exe", "netflix.exe",
        "steam.exe", "epicgameslauncher.exe",
    )

    private fun baseScore(process: String?, title: String?): Float {
        val p = process?.lowercase().orEmpty()
        val t = title?.lowercase().orEmpty()
        if (p in IDE_PROCS) return 0.7f
        if (p in TERMINAL_PROCS) return 0.7f
        if (p in BROWSER_PROCS) return browserBase(t)
        if (p in COMMS_PROCS) return 0.4f
        if (p in ENTERTAINMENT_PROCS) return 0.1f
        return 0.3f
    }

    private fun browserBase(t: String): Float = when {
        t.contains("stack overflow") || t.contains("stackoverflow") -> 0.6f
        t.contains("readthedocs") || t.contains("read the docs") -> 0.6f
        t.contains("man page") -> 0.6f
        t.contains("docs.") -> 0.6f
        t.contains("mdn web docs") -> 0.6f
        t.contains("github.com") || t.contains(" - github") -> 0.5f
        t.contains("gitlab") -> 0.5f
        t.contains("youtube") -> 0.1f
        t.contains("netflix") -> 0.1f
        t.contains("twitch") -> 0.1f
        // X/Twitter: page titles vary — "Home / X", "username on X", "(@handle)".
        t.contains("twitter") || t.contains("x.com") ||
            t.endsWith(" / x") || t.contains(" / x ") ||
            t.contains(" on x ") || t.endsWith(" on x") ||
            t.contains("(@") -> 0.05f
        t.contains("reddit") -> 0.05f
        t.contains("instagram") -> 0.05f
        t.contains("tiktok") -> 0.05f
        t.contains("facebook") -> 0.05f
        else -> 0.3f
    }

    /** DM5 fix (post-impl council 1778164815): word-boundary regex so "fail"
     *  no longer hits inside "failsafe"/"failure-mode" and "bug" no longer
     *  hits inside "debug". CamelCase compound words like
     *  "NullPointerException" still match because \b sits at the
     *  letter-letter case-transition boundary in regex flavor used by
     *  java.util.regex (kotlin.text.Regex) when both characters are word
     *  characters of the SAME case class — they don't. So we add an
     *  explicit "exception" suffix-anchored alternation: any word ending
     *  in `Exception` matches. Same trick for `Error`. */
    private val KEYWORD_REGEX = Regex(
        """\b(error|exception|todo|fixme|bug|fail|crash|stacktrace|panic)\b""" +
            """|stack\s+trace""" +
            """|[A-Za-z]+(Exception|Error)\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun keywordBonus(title: String?): Float {
        if (title.isNullOrEmpty()) return 0f
        val hits = KEYWORD_REGEX.findAll(title).count()
        return (hits * 0.1f).coerceAtMost(0.2f)
    }

    /** How long has the current process been continuously active up to [entry]?
     *  Walk `recent` from newest to oldest; stop at first entry with a different
     *  process. The earliest still-same-process timestamp anchors the window. */
    private fun continuityBonus(entry: ActivityEntry, recent: List<ActivityEntry>): Float {
        val proc = entry.process ?: return 0f
        val now = parseInstantOrNull(entry.ts) ?: return 0f
        var windowStart: Instant = now
        for (e in recent.asReversed()) {
            if (e.process != proc) break
            val ts = parseInstantOrNull(e.ts) ?: break
            if (ts < windowStart) windowStart = ts
        }
        val mins = Duration.between(windowStart, now).toMinutes()
        return when {
            mins >= 30 -> 0.2f
            mins >= 10 -> 0.1f
            else -> 0f
        }
    }

    /** Penalize churn: many distinct process names in the 5 min window leading
     *  up to [entry] is the signature of distraction / context-switching. */
    private fun distractionPenalty(entry: ActivityEntry, recent: List<ActivityEntry>): Float {
        val now = parseInstantOrNull(entry.ts) ?: return 0f
        val cutoff = now.minus(Duration.ofMinutes(5))
        val distinct = recent
            .mapNotNull { e -> parseInstantOrNull(e.ts)?.let { it to e.process } }
            .filter { (ts, _) -> ts >= cutoff }
            .map { (_, p) -> p }
            .toSet()
        return if (distinct.size > 5) 0.2f else 0f
    }

    private fun parseInstantOrNull(s: String?): Instant? =
        if (s.isNullOrEmpty()) null else runCatching { Instant.parse(s) }.getOrNull()
}
