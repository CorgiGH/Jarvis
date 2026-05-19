package jarvis

import jarvis.tutor.FsrsCardRepo
import jarvis.tutor.FsrsCardsTable
import jarvis.tutor.FsrsSource
import jarvis.tutor.FsrsState
import jarvis.tutor.SessionsTable
import jarvis.tutor.TutorCard
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorTypes
import jarvis.tutor.User
import jarvis.tutor.UserRepo
import jarvis.tutor.UserScope
import jarvis.tutor.UsersTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readText
import kotlin.streams.toList
import kotlin.system.exitProcess

@Serializable
internal data class CardPair(val front: String, val back: String)

@Serializable
internal data class CardsReply(val cards: List<CardPair> = emptyList())

/**
 * Council 1779189544 reframe — Session 1: seed FSRS cards from Alex's
 * scraped corpus so the daily-retrieval loop closes for the 13-day finals
 * window. One-shot CLI: walk each corpus dir, call claude-CLI per .md to
 * generate Q/A pairs, insert as MANUAL FsrsCards with dueAt=now so they
 * surface immediately in /api/v1/fsrs/due → /review.
 *
 * Auth: claude CLI inherits the user's OAuth session (NOT ANTHROPIC_API_KEY).
 * Per "no paid LLM API spend" rule, do NOT pass --bare (which forces API key)
 * and do not set ANTHROPIC_API_KEY in env. The CLI uses Alex's existing
 * Claude subscription.
 */
internal fun runSeedFsrs(args: List<String>) {
    var userId = "owner"
    val corpusDirs = mutableListOf<Path>()
    var subjectOverride: String? = null
    var cardsPerDoc = 8
    var limitDocs = Int.MAX_VALUE
    var dryRun = false
    var model = "haiku"

    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--user" -> userId = args[++i]
            "--corpus" -> { corpusDirs.add(Path.of(args[++i])) }
            "--subject" -> subjectOverride = args[++i]
            "--cards-per-doc" -> cardsPerDoc = args[++i].toInt()
            "--limit" -> limitDocs = args[++i].toInt()
            "--dry-run" -> dryRun = true
            "--model" -> model = args[++i]
            "-h", "--help" -> { printSeedUsage(); return }
            else -> {
                System.err.println("seed-fsrs: unknown arg '$a'")
                printSeedUsage()
                exitProcess(2)
            }
        }
        i++
    }
    if (corpusDirs.isEmpty()) {
        printSeedUsage()
        exitProcess(2)
    }

    val db = TutorDb.connect(Config.tutorDbPath)
    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(UsersTable, SessionsTable, FsrsCardsTable)
    }
    val users = UserRepo(db)
    if (users.findById(userId) == null) {
        val now = Instant.now()
        users.insert(User(userId, userId, UserScope.OWNER, now, now))
        println("[seed-fsrs] created user '$userId'")
    }

    val repo = FsrsCardRepo(db)
    val docs = collectMarkdownFiles(corpusDirs).take(limitDocs)
    if (docs.isEmpty()) {
        System.err.println("seed-fsrs: no .md files under ${corpusDirs.joinToString(", ")}")
        exitProcess(2)
    }
    println(
        "[seed-fsrs] user=$userId  ${docs.size} docs across ${corpusDirs.size} corpus dirs, " +
            "cards-per-doc≤$cardsPerDoc, model=$model${if (dryRun) "  DRY-RUN" else ""}",
    )

    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val nowTs = Instant.now()
    var totalCards = 0
    var failedDocs = 0
    for ((idx, mdPath) in docs.withIndex()) {
        val subj = subjectOverride ?: deriveSubject(mdPath)
        val raw = try {
            mdPath.readText()
        } catch (e: Exception) {
            System.err.println("[${idx + 1}/${docs.size}] read failed: $mdPath — ${e.message?.take(120)}")
            failedDocs++
            continue
        }
        if (raw.isBlank()) {
            println("[${idx + 1}/${docs.size}] $subj/${mdPath.fileName} SKIP (empty)")
            continue
        }
        // Strip NUL bytes — they sneak in from PDF-extracted markdown and
        // ProcessBuilder rejects them in command args ("invalid null character").
        val content = raw.filter { it.code != 0 }.take(8000)
        val prompt = buildCardGenPrompt(content, subj, cardsPerDoc)
        val cards = runClaudeCardGen(prompt, model, json)
        if (cards == null || cards.isEmpty()) {
            failedDocs++
            println("[${idx + 1}/${docs.size}] $subj/${mdPath.fileName} FAILED (no parseable cards)")
            continue
        }
        if (dryRun) {
            println("[${idx + 1}/${docs.size}] $subj/${mdPath.fileName} → ${cards.size} cards (dry-run)")
            cards.forEach {
                println("    Q: ${it.front.take(120)}")
                println("    A: ${it.back.take(120)}")
            }
            totalCards += cards.size
            continue
        }
        var inserted = 0
        val sourceRef = "${subj}:${mdPath.fileName}".take(32)
        for (card in cards) {
            try {
                repo.insert(
                    TutorCard(
                        id = TutorTypes.ulid(),
                        userId = userId,
                        source = FsrsSource.MANUAL,
                        sourceRef = sourceRef,
                        front = card.front.trim(),
                        back = card.back.trim(),
                        state = FsrsState(
                            difficulty = 5.0,
                            stability = 0.5,
                            retrievability = 1.0,
                            dueAt = nowTs,
                            lastReviewedAt = nowTs,
                            lapses = 0,
                        ),
                    ),
                )
                inserted++
            } catch (e: Exception) {
                System.err.println("  insert failed: ${e.message?.take(120)}")
            }
        }
        totalCards += inserted
        println("[${idx + 1}/${docs.size}] $subj/${mdPath.fileName} → $inserted cards inserted")
    }
    println("[seed-fsrs] DONE  total cards: $totalCards  failed-docs: $failedDocs")
}

private fun printSeedUsage() {
    System.err.println(
        """
        Usage: jarvis seed-fsrs --corpus <dir> [--corpus <dir> ...] [opts]

          --user <id>           Target user (default: owner)
          --corpus <dir>        Walk dir recursively for *.md (repeatable, required)
          --subject <code>      Override derived subject (PA/PS/POO/ALO/SO/RC)
          --cards-per-doc <n>   Upper bound per doc (default: 8)
          --limit <n>           Cap total docs processed (useful for sanity passes)
          --model <alias>       Claude CLI model alias (default: haiku)
          --dry-run             Generate + print, do not insert
        """.trimIndent(),
    )
}

private fun collectMarkdownFiles(dirs: List<Path>): List<Path> = dirs.flatMap { dir ->
    if (!Files.isDirectory(dir)) {
        System.err.println("seed-fsrs: not a directory, skipping: $dir")
        return@flatMap emptyList()
    }
    Files.walk(dir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.toList()
    }
}.sorted()

private val KNOWN_SUBJECTS = setOf("PA", "PS", "POO", "ALO", "SO", "RC")

private fun deriveSubject(mdPath: Path): String {
    // Path segments commonly look like ".../tmp-md/_fii/PA/algo.md" — pick the
    // first segment that matches a known subject code. Fallback "UNK".
    return mdPath.toString()
        .split('\\', '/')
        .firstOrNull { it in KNOWN_SUBJECTS }
        ?: "UNK"
}

private fun buildCardGenPrompt(content: String, subject: String, maxCards: Int): String =
    """Generate up to $maxCards FSRS-style flashcards from this $subject (Romanian university) course material.
Each card tests a single concept via active retrieval. front = a precise question. back = the concise answer (≤200 chars).
Output STRICT JSON, no prose, no markdown fences, exactly this shape:
{"cards":[{"front":"...","back":"..."}]}

Material:
$content
"""

/** Invokes the claude CLI in `--print --output-format json` mode and parses the
 *  inner result text as a [CardsReply]. Returns null on any failure. The CLI
 *  uses Alex's OAuth session — no ANTHROPIC_API_KEY required. */
internal fun runClaudeCardGen(prompt: String, model: String, json: Json): List<CardPair>? {
    return try {
        val pb = ProcessBuilder(
            "claude",
            "--print",
            "--output-format", "json",
            "--model", model,
            "--no-session-persistence",
            prompt,
        ).redirectErrorStream(false)
            // Otherwise the CLI waits ~3s for piped stdin before proceeding.
            .redirectInput(ProcessBuilder.Redirect.from(java.io.File(if (isWindows()) "NUL" else "/dev/null")))
        val process = pb.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            System.err.println("  claude exit=$exit stderr=${stderr.take(200)}")
            return null
        }
        extractCards(stdout, json)
    } catch (e: Exception) {
        System.err.println("  claude invoke failed: ${e.message?.take(160)}")
        null
    }
}

/** Public for test: takes the raw stdout of `claude --print --output-format json`,
 *  unwraps the outer envelope, strips any markdown fences the model added, and
 *  parses the inner JSON as [CardsReply].
 *
 *  Two shapes are accepted because the claude CLI's `json` output mode is
 *  actually an event stream serialized as a top-level array — the model's
 *  reply lives in the FINAL element with `type:"result"`. Legacy single-object
 *  envelopes (e.g. older versions / mocks) still work for back-compat. */
internal fun extractCards(claudeStdout: String, json: Json): List<CardPair>? {
    val root = runCatching { json.parseToJsonElement(claudeStdout) }.getOrNull() ?: return null
    val resultText = when (root) {
        is JsonArray -> root.lastOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "result" }
            ?.let { innerText((it as JsonObject)) }
        is JsonObject -> innerText(root)
        else -> null
    } ?: return null
    val cleaned = resultText.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    return runCatching { json.decodeFromString(CardsReply.serializer(), cleaned).cards }.getOrNull()
}

private fun innerText(obj: JsonObject): String? {
    // claude --output-format json shape: { "type":"result", "result":"...text...", ... }
    // Defensive — accept "result" / "text" / "content".
    obj["result"]?.jsonPrimitive?.contentOrNull?.let { return it }
    obj["text"]?.jsonPrimitive?.contentOrNull?.let { return it }
    obj["content"]?.jsonPrimitive?.contentOrNull?.let { return it }
    return null
}

private fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.startsWith("windows") == true
