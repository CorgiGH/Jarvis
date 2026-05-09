package jarvis.tutor

/**
 * Tutor Layer B0 — server-side read-only-mode classifier.
 *
 * Council R3 (2026-05-09 layer-b transcript) flagged a hole: the spec
 * said "non-allowlisted sensor source → effectors auto-disabled w/
 * red badge" but didn't say WHO classifies the source. Trusting the
 * client to self-report sensor source = trivially bypassed (a
 * malicious browser extension just lies). Classification MUST run
 * server-side off the screenshot extraction.
 *
 * Heuristics (in order):
 *  1. file_path matches a code-editor file extension AND looks like a
 *     local file path (not a URL) → ALLOWED.
 *  2. file_path matches a known browser/web pattern → READ_ONLY.
 *  3. raw OCR mentions a non-allowlisted browser host → READ_ONLY.
 *  4. file_path null AND error null AND console null → UNKNOWN
 *     (treat as READ_ONLY for safety; user may tab to a non-code app).
 *
 * The blocklist is hardcoded — sites where pasting suggested edits
 * tends to be wrong (Q&A sites, social, video). No allowlist of code
 * extensions exhaustively, just a heuristic; fail-closed when unsure.
 */
object SourceClassifier {

    enum class Mode { ALLOWED, READ_ONLY }

    data class Classification(val mode: Mode, val reason: String)

    /** File extensions where APPLY-edit is plausibly safe — code, config,
     *  notebook, markup. NOT exhaustive; this is a fail-OPEN check that
     *  is gated by the path-shape check below. */
    private val CODE_EXTENSIONS = setOf(
        "kt", "kts", "java", "py", "rs", "go", "c", "cpp", "cc", "h", "hpp",
        "js", "ts", "tsx", "jsx", "vue", "svelte", "rb", "php", "scala",
        "swift", "m", "mm", "cs", "fs", "ml", "hs", "elm", "ex", "exs",
        "r", "jl", "lua", "pl", "sh", "bash", "zsh", "fish", "ps1",
        "html", "css", "scss", "sass", "less", "md", "markdown", "rst",
        "yaml", "yml", "toml", "json", "xml", "ini", "env", "conf",
        "ipynb", "tex", "sql",
    )

    /** Hosts commonly visited during studying that should not receive
     *  APPLY actions. Read-only mode prevents a "fix this" button from
     *  pasting code into a forum textarea. */
    private val READ_ONLY_HOSTS = listOf(
        "stackoverflow.com", "stackexchange.com", "github.com",
        "reddit.com", "twitter.com", "x.com", "youtube.com",
        "facebook.com", "instagram.com", "tiktok.com", "discord.com",
        "chat.openai.com", "claude.ai", "gemini.google.com",
    )

    fun classify(extraction: ScreenshotExtraction): Classification {
        val filePath = extraction.filePath?.trim().orEmpty()
        val raw = extraction.rawReply.lowercase()

        // (2) Browser/web pattern wins early (and beats "looks like file
        // path" because URLs may end in .html which is in CODE_EXTENSIONS).
        if (filePath.isNotEmpty()) {
            val pathLower = filePath.lowercase()
            if (pathLower.startsWith("http://") || pathLower.startsWith("https://")) {
                val host = pathLower.removePrefix("https://").removePrefix("http://").substringBefore('/')
                return Classification(Mode.READ_ONLY, "browser tab: $host")
            }
            for (host in READ_ONLY_HOSTS) {
                if (pathLower.contains(host)) {
                    return Classification(Mode.READ_ONLY, "browser tab: $host")
                }
            }
            // (1) Looks like a local file path with a code-ish extension.
            val ext = filePath.substringAfterLast('.', "").lowercase()
            if (ext in CODE_EXTENSIONS && !filePath.contains(" - ")) {
                // The " - " filter excludes window-title strings like
                // "Stack Overflow - Mozilla Firefox" which technically
                // end with `firefox` but aren't file paths.
                return Classification(Mode.ALLOWED, "code file: .$ext")
            }
        }

        // (3) raw OCR mentions read-only host even when file_path is null.
        for (host in READ_ONLY_HOSTS) {
            if (raw.contains(host)) {
                return Classification(Mode.READ_ONLY, "raw OCR matched: $host")
            }
        }

        // (4) Default fail-closed: no recognizable code-editor signal.
        return Classification(Mode.READ_ONLY, "no code-editor signal — defaulting to read-only")
    }
}
