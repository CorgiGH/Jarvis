package jarvis.web

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import jarvis.Activity
import jarvis.ActivityEntry
import jarvis.CHAT_SYSTEM_PROMPT
import jarvis.ChatMessage
import jarvis.ChatTools
import jarvis.ChatTurnWriter
import jarvis.Conversations
import jarvis.CoreMemory
import jarvis.Llm
import jarvis.LlmFactory
import jarvis.MemoryWiki
import jarvis.ProactiveLoop
import jarvis.buildChatContext
import jarvis.resolveOpenRouterKey
import jarvis.subsystem.SubsystemInput
import jarvis.subsystem.Subsystems
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.exitProcess

private const val DEFAULT_PORT = 8080
private const val AUTH_COOKIE = "jarvis_auth"
private const val COOKIE_MAX_AGE_SECONDS = 30 * 24 * 3600 // 30 days

internal suspend fun runWeb() {
    val authToken = System.getenv("JARVIS_AUTH_TOKEN")?.trim().orEmpty()
    if (authToken.isEmpty()) {
        System.err.println(
            "ERROR: JARVIS_AUTH_TOKEN not set. Generate a strong random string (e.g. " +
                "'openssl rand -hex 32' or any 32+ random chars), put it in your .env or env, then re-run.",
        )
        exitProcess(1)
    }

    val apiKey = resolveOpenRouterKey()  // optional now; only required if JARVIS_LLM=openrouter
    val port = System.getenv("JARVIS_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val client: Llm = LlmFactory.create(apiKey)
    // Letta-style: chat history is derived from conversations.jsonl on every
    // turn (Conversations.recentAsChatMessages). The mutex serializes the LLM
    // call + ChatTurnWriter.append so concurrent /chat requests do not double-
    // invoke the model and do not race on the JSONL append-and-read.
    val historyLock = Mutex()

    println("Jarvis web on http://localhost:$port (Ctrl+C to stop)")
    println("Auth required. First visit: http://localhost:$port/login")
    // Scheduled-block reminders. Default-OFF behind REMINDER_LOOP_ENABLED env.
    jarvis.BlockReminder.start()

    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }

        intercept(ApplicationCallPipeline.Plugins) {
            val path = call.request.path()
            // Public routes — no auth: login form, basic liveness, tutor SPA
            // bundle (token-gated client-side after load), Layer A health,
            // and the /auth/setup magic-link exchange (gated by its own
            // raw-token check, not the JARVIS_AUTH_TOKEN bearer).
            if (path == "/login" || path == "/healthz" || path == "/api/v1/health") return@intercept
            if (path == "/tutor" || path.startsWith("/tutor/")) return@intercept
            if (path.startsWith("/auth/")) return@intercept
            // Layer B sensor + effector + grants routes use the tutor
            // session cookie + CSRF token (set by /auth/setup), NOT the
            // legacy bearer JARVIS_AUTH_TOKEN. Hand off to the route's
            // own auth.
            if (path.startsWith("/api/v1/sensor/") ||
                path.startsWith("/api/v1/effector/") ||
                path.startsWith("/api/v1/grants") ||
                path.startsWith("/api/v1/tasks") ||
                path.startsWith("/api/v1/me/") ||
                path == "/api/v1/tutor/auto-session" ||
                path == "/api/v1/gateway/inbound" ||
                // Gate 2: tutor SPA routes — self-authenticate via jarvis_session
                path.startsWith("/api/v1/last-task") ||
                path.startsWith("/api/v1/fsrs/") ||
                path.startsWith("/api/v1/drill/") ||
                path.startsWith("/api/v1/sidekick/") ||
                path.startsWith("/api/v1/gap") ||
                path.startsWith("/api/v1/daemon/health")) return@intercept

            val header = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
            val cookieToken = call.request.cookies[AUTH_COOKIE]
            if (header == authToken || cookieToken == authToken) return@intercept

            if (path.startsWith("/api/") || path == "/apk") {
                call.respondText(
                    "401 missing or invalid auth token",
                    ContentType.Text.Plain,
                    HttpStatusCode.Unauthorized,
                )
            } else {
                call.respondRedirect("/login")
            }
            finish()
        }

        routing {
            get("/healthz") {
                call.respondText("ok", ContentType.Text.Plain)
            }

            get("/login") {
                call.respondText(LOGIN_HTML, ContentType.Text.Html)
            }

            post("/login") {
                val params = call.receiveParameters()
                val provided = params["token"]?.trim().orEmpty()
                if (provided == authToken) {
                    call.response.cookies.append(
                        Cookie(
                            name = AUTH_COOKIE,
                            value = authToken,
                            path = "/",
                            maxAge = COOKIE_MAX_AGE_SECONDS,
                            httpOnly = true,
                        ),
                    )
                    call.respondRedirect("/")
                } else {
                    call.respondText(
                        LOGIN_HTML.replace("<!--ERR-->", "<p style='color:#bf616a'>invalid token</p>"),
                        ContentType.Text.Html,
                        HttpStatusCode.Unauthorized,
                    )
                }
            }

            get("/") {
                call.respondText(INDEX_HTML, ContentType.Text.Html)
            }

            post("/chat") {
                val params = call.receiveParameters()
                val msg = params["msg"]?.trim().orEmpty()
                if (msg.isEmpty()) {
                    call.respondText("", ContentType.Text.Html)
                    return@post
                }
                val (reply, model) = historyLock.withLock {
                    val sysPrompt = CHAT_SYSTEM_PROMPT + CoreMemory.preamble() +
                        "\n\n# Context\n" + buildChatContext()
                    val messages = listOf(ChatMessage("system", sysPrompt)) +
                        Conversations.recentAsChatMessages() +
                        ChatMessage("user", msg)
                    val (text, modelName) = try {
                        ChatTools.runTurn(client, messages)
                    } catch (e: Exception) {
                        return@withLock "[error] ${e.message}" to "n/a"
                    }
                    appendChatTurn(msg, text, modelName)
                    text to modelName
                }
                call.respondText(turnHtml(msg, reply, model), ContentType.Text.Html)
            }

            post("/reflect") {
                try {
                    jarvis.runReflect()
                    call.respondText(
                        """<div class="turn jarvis"><b>reflection</b> saved to wiki — see /wiki</div>""",
                        ContentType.Text.Html,
                    )
                } catch (e: Exception) {
                    call.respondText(
                        """<div class="turn jarvis"><b>reflect failed:</b> ${escape(e.message ?: "unknown")}</div>""",
                        ContentType.Text.Html,
                    )
                }
            }

            post("/sub") {
                val params = call.receiveParameters()
                val cmd = params["cmd"]?.trim().orEmpty()
                if (cmd.isEmpty()) {
                    val list = Subsystems.all.joinToString("<br>") {
                        "<code>${it.name}</code> — ${escape(it.description)}"
                    }
                    call.respondText(
                        """<div class="turn jarvis"><b>subsystems:</b><br>$list</div>""",
                        ContentType.Text.Html,
                    )
                    return@post
                }
                val tokens = cmd.split(" ", limit = 2)
                val sub = Subsystems.get(tokens[0]) ?: run {
                    call.respondText(
                        """<div class="turn jarvis">unknown subsystem: ${escape(tokens[0])}</div>""",
                        ContentType.Text.Html,
                    )
                    return@post
                }
                val query = tokens.getOrNull(1)?.trim()
                val activity = Activity.loadEntries()
                val wiki = MemoryWiki.recent()
                val output = try {
                    sub.run(
                        client,
                        SubsystemInput(
                            activity = activity,
                            wiki = wiki,
                            recentChat = Conversations.recentAsChatMessages(),
                            userQuery = query,
                        ),
                    )
                } catch (e: Exception) {
                    call.respondText(
                        """<div class="turn jarvis"><b>sub:${sub.name} failed:</b> ${escape(e.message ?: "")}</div>""",
                        ContentType.Text.Html,
                    )
                    return@post
                }
                output.wikiEntry?.let { MemoryWiki.append(it, output.text) }
                call.respondText(
                    """<div class="turn user">[sub ${escape(sub.name)}${query?.let { " " + escape(it) }.orEmpty()}]</div>
                       |<div class="turn jarvis"><pre>${escape(output.text)}</pre></div>
                    """.trimMargin(),
                    ContentType.Text.Html,
                )
            }

            post("/api/chat") {
                val req = call.receive<ApiChatRequest>()
                val msg = req.msg.trim()
                if (msg.isEmpty()) {
                    call.respond(ApiChatResponse("(empty message)", "n/a"))
                    return@post
                }
                val tutorCtxOuter = call.application.attributes
                    .getOrNull(jarvis.tutor.TutorContextKey)
                val sidOuter = call.request.cookies["jarvis_session"]
                // Phase 7.4 implicit-gap detection. If the user msg matches
                // "i don't (get|know|understand) X" / "what (is|does) X" /
                // "how do X", auto-POST a gap before LLM round-trip so the
                // ledger captures the question. Best-effort — failure
                // doesn't block the chat reply.
                jarvis.tutor.ImplicitGapDetector.detect(msg)?.let { topic ->
                    val tid = req.taskId?.trim().orEmpty()
                    val tutorCtx = tutorCtxOuter
                    if (tutorCtx != null) try {
                        val uid = sidOuter?.let {
                            jarvis.tutor.SessionRepo(tutorCtx.db).findUserId(it)
                        }
                        if (uid != null) {
                            val gap = jarvis.tutor.KnowledgeGap(
                                id = jarvis.tutor.TutorTypes.ulid(),
                                userId = uid,
                                taskId = tid.takeIf { it.isNotEmpty() },
                                topic = topic.take(256),
                                language = null,
                                type = jarvis.tutor.GapType.CONCEPT,
                                trigger = jarvis.tutor.GapTrigger.EXPLICIT_ASK,
                                filledAt = java.time.Instant.now(),
                                source = jarvis.tutor.GapSource.LLM_GROUNDED,
                                content = msg.take(512),
                                exampleCode = null,
                                sourceCitation = null,
                                resolvedBy = null,
                                reusedCount = 0,
                                fsrsCardId = null,
                            )
                            jarvis.tutor.KnowledgeGapRepo(tutorCtx.db, tutorCtx.ledgerDir)
                                .upsertByTriple(gap, taskId = tid.takeIf { it.isNotEmpty() }, content = msg.take(512))
                        }
                    } catch (e: Exception) {
                        System.err.println("[implicit-gap] WARN ${e.message?.take(160)}")
                    }
                }
                val (reply, model) = historyLock.withLock {
                    // Tutor task-context V0: when the request carries a
                    // taskId AND the TutorContext is installed, inject
                    // a ≤300-tok header (cached 30s on state_version)
                    // so the LLM knows what task / subject / deadline /
                    // weak-concept landscape it's helping with. The
                    // SYSTEM_INJECTION_PREAMBLE tells the model that
                    // retrieved_context is DATA only, not instructions.
                    val taskHeader = run {
                        val tid = req.taskId?.trim().orEmpty()
                        val tutorCtx = tutorCtxOuter
                        if (tid.isEmpty() || tutorCtx == null) "" else {
                            try {
                                val uid = sidOuter?.let {
                                    jarvis.tutor.SessionRepo(tutorCtx.db).findUserId(it)
                                } ?: "anonymous"
                                "\n\n" + jarvis.tutor.TaskHeaderBuilder.SYSTEM_INJECTION_PREAMBLE +
                                    "\n\n" + jarvis.tutor.TaskHeaderBuilder.build(
                                        db = tutorCtx.db,
                                        userId = uid,
                                        taskId = tid,
                                        subjectHint = req.subject,
                                    )
                            } catch (e: Exception) {
                                System.err.println("[task-header] WARN ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                                ""
                            }
                        }
                    }
                    val sysPrompt = CHAT_SYSTEM_PROMPT + CoreMemory.preamble() +
                        "\n\n# Context\n" + buildChatContext() + taskHeader
                    // Tutor surface (taskId + TutorContext present + env
                    // toggle on) routes through OpenRouter free-tier with
                    // proper tool_use. Otherwise: legacy ChatTools.runTurn
                    // (RelayLlm/claude-max + marker-pattern). Council
                    // 2026-05-09 ruling: tool_use is the architecture for
                    // tutor; marker-pattern stays for the non-tutor chat
                    // path so users without OPENROUTER_API_KEY are
                    // unaffected.
                    val tutorSurface = req.taskId?.isNotBlank() == true &&
                        tutorCtxOuter != null &&
                        (System.getenv("JARVIS_LLM_TUTOR")?.lowercase() == "openrouter") &&
                        !resolveOpenRouterKey().isNullOrBlank()
                    // F7 — context-pressure aware. Drops oldest turns when
                    // chat array exceeds a char budget so we don't slam the
                    // provider with a saturated context window. Built once
                    // so both the tutor-surface fallback and the legacy
                    // path can reuse the same shape.
                    val legacyMessages = listOf(ChatMessage("system", sysPrompt)) +
                        Conversations.recentAsChatMessagesWithBudget() +
                        ChatMessage("user", msg)
                    suspend fun runLegacy(): Pair<String, String> = try {
                        ChatTools.runTurn(client, legacyMessages)
                    } catch (e: Exception) {
                        "[error] ${e.message}" to "n/a"
                    }
                    val (text, modelName) = if (tutorSurface) {
                        try {
                            jarvis.tutor.JarvisToolset().use { ts ->
                                val r = ts.chat(systemPrompt = sysPrompt, userText = msg)
                                r.text to "${r.model} (${r.toolRounds} tool rounds)"
                            }
                        } catch (e: Exception) {
                            // Tutor surface threw — could be 429/empty/other.
                            // Degrade gracefully to legacy chat path so the user
                            // gets SOMETHING instead of an error string or empty
                            // turn. Tag the model so observability surfaces it.
                            System.err.println(
                                "[tool-chat] degrading to legacy: ${e.javaClass.simpleName}: ${e.message?.take(200)}",
                            )
                            val (legacyText, legacyModel) = runLegacy()
                            legacyText to "$legacyModel (tool-chat-fallback)"
                        }
                    } else {
                        runLegacy()
                    }
                    appendChatTurn(msg, text, modelName)
                    text to modelName
                }
                call.respond(ApiChatResponse(reply, model))
            }

            // Phase 3.1 (council 1778184643): polling endpoint for the Android
            // APK. Read-only — `since` is an ISO-8601 instant; we return rows
            // strictly newer than that, oldest-first, capped at `limit`. PII
            // scanner mirrors the buildChatContext fix: any signal whose
            // snippet trips identifier-shape regex is dropped (with WARN) so
            // a phone notification can never leak matricol/email/phone.
            get("/api/signals") {
                val sinceParam = call.request.queryParameters["since"].orEmpty().trim()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    ?.coerceIn(1, 50) ?: 10
                val sinceInstant = if (sinceParam.isEmpty()) null else
                    runCatching { java.time.Instant.parse(sinceParam) }.getOrNull()
                val all = jarvis.Signals.readAll()
                val filtered = all.asSequence()
                    .filter { sinceInstant == null ||
                        runCatching { java.time.Instant.parse(it.ts) }.getOrNull()
                            ?.let { ts -> ts > sinceInstant } ?: false }
                    .filter { sig ->
                        val findings = jarvis.CoreMemory.scanTextForPii(sig.snippet)
                        if (findings.isNotEmpty()) {
                            System.err.println(
                                "[/api/signals] WARN dropping ${sig.id} containing " +
                                    "${findings.joinToString(",") { it.kind }}",
                            )
                            false
                        } else true
                    }
                    .take(limit)
                    .toList()
                call.respond(ApiSignalsResponse(filtered))
            }

            // R3 (deep-research recommendation #3 / Mem0 feedback API parity):
            // append-only feedback ledger for ProactiveSignal user actions.
            // Whitelist enforced server-side; bad action → 400.
            // S3 — render today's study plan directly (bypass chat tool path
            // since the LLM sometimes hallucinates the response without
            // actually invoking [[plan: today]]). Pure deterministic output
            // from Schedule × KnowledgeState × ConceptCatalog.
            // study_now — works without schedule. Picks weakest concept,
            // suggests 25-min Pomodoro. Council retro 2026-05-08 fix for
            // "what should I do at 02:33 with empty schedule".
            get("/api/study_now") {
                val subject = call.request.queryParameters["subject"]
                val stats = jarvis.KnowledgeState.stats()
                val catalog = jarvis.ConceptCatalog.all()
                val weak = stats
                    .filter { subject == null || it.subject.equals(subject, true) }
                    .filter { it.confidence < 0.5f }
                    .sortedBy { it.confidence }.firstOrNull()
                val msg = if (weak != null) {
                    "POMODORO 25min: ${weak.subject} / ${weak.concept} (conf=${"%.2f".format(weak.confidence)}, stale ${weak.staleDays}d)"
                } else {
                    val seen = stats.map { it.concept to it.subject }.toSet()
                    val untouched = catalog
                        .filter { subject == null || it.subject.equals(subject, true) }
                        .firstOrNull { (it.name to it.subject) !in seen }
                    if (untouched != null) {
                        "POMODORO 25min: ${untouched.subject} / ${untouched.name} (untouched)"
                    } else {
                        "(no concepts available — populate concept catalog first)"
                    }
                }
                call.respondText(msg, ContentType.Text.Plain)
            }

            // Mockup-gap closer: per-subject confidence rollup for the
            // sidebar % strip. Reads KnowledgeState.stats(), groups by
            // subject, returns avg + max confidence + concept count so
            // the UI can render "PA 43%" / "POO 2%" style indicators.
            // Confidence values get capped soft at 10 by stats(); we
            // normalize to a 0..1 range before returning so the frontend
            // stays decoupled from the soft-cap constant.
            get("/api/v1/subject-confidence") {
                val all = jarvis.KnowledgeState.stats()
                val grouped = all.groupBy { it.subject.ifBlank { "OTHER" } }
                val rows = grouped.map { (subject, stats) ->
                    val confs = stats.map { it.confidence }
                    ApiSubjectConfidenceRow(
                        subject = subject,
                        concepts = stats.size,
                        avgConfidence = (confs.average().toFloat() / 10f).coerceIn(0f, 1f),
                        maxConfidence = ((confs.maxOrNull() ?: 0f) / 10f).coerceIn(0f, 1f),
                    )
                }.sortedByDescending { it.maxConfidence }
                call.respond(ApiSubjectConfidenceResponse(rows))
            }

            // Phase 7.1 deferral closer: ConceptInline confidence query.
            // Frontend gates inline-link affordance behind threshold so well-
            // known concepts render plain (no underline). Match policy:
            // case-insensitive name; max confidence across subjects (rare
            // for a name to span subjects, but takes the strongest signal).
            get("/api/v1/concept-confidence") {
                val name = call.request.queryParameters["name"]?.trim().orEmpty()
                if (name.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "name required")
                    return@get
                }
                val all = jarvis.KnowledgeState.stats()
                val match = all
                    .filter { it.concept.equals(name, ignoreCase = true) }
                    .maxByOrNull { it.confidence }
                val conf = match?.confidence ?: 0f
                val threshold = conceptLinkThreshold()
                call.respond(
                    ApiConceptConfidenceResponse(
                        concept = name,
                        confidence = conf,
                        staleDays = match?.staleDays ?: Long.MAX_VALUE,
                        matched = match != null,
                        linked = conf < threshold,
                    ),
                )
            }

            // Daily allocator — single best next block.
            get("/api/next_block") {
                val now = java.time.Instant.now()
                val zone = java.time.ZoneId.of("Europe/Bucharest")
                val schedule = jarvis.Schedule.load()
                val assignments = jarvis.Assignments.current(now, zone)
                val stats = jarvis.KnowledgeState.stats(now)
                val catalog = jarvis.ConceptCatalog.all()
                val activity = jarvis.Activity.loadEntries(hours = 1)
                val stress = jarvis.StressProxy.current(activity, now, zone)
                val nb = jarvis.Allocator.suggest(
                    schedule, assignments, stats, catalog, stress, now, zone,
                )
                call.respond(nb)
            }

            get("/api/plan") {
                val now = java.time.Instant.now()
                // VPS systemDefault is UTC; user lives in Europe/Bucharest.
                // Hardcode the user-locale TZ so dates / hours align with the
                // schedule.json the user authored from their own clock.
                val zone = java.time.ZoneId.of("Europe/Bucharest")
                val schedule = jarvis.Schedule.load()
                val stats = jarvis.KnowledgeState.stats(now)
                val catalog = jarvis.ConceptCatalog.all()
                val items = jarvis.StudyPlanner.today(schedule, stats, catalog, now, zone)
                val rendered = jarvis.StudyPlanner.render(items, schedule, now, zone)
                call.respondText(rendered, ContentType.Text.Plain)
            }

            // F4 — cached user-state. /api/state returns the last cached
            // ctx-model output. Stale cache → trigger background refresh +
            // return what we have (caller doesn't block).
            get("/api/state") {
                val cached = jarvis.StateCache.current()
                if (jarvis.StateCache.isStale()) {
                    jarvis.StateCache.maybeRefresh(client)
                }
                if (cached == null) {
                    call.respond(
                        io.ktor.http.HttpStatusCode.NoContent
                    )
                } else {
                    call.respond(cached)
                }
            }

            // R6 (deep-research recommendation #6): live focus-session
            // detection. APK polls this every 15 min and surfaces a quiet
            // ongoing notification when active=true.
            get("/api/focus") {
                val activity = jarvis.Activity.loadEntries(hours = 4)
                val session = jarvis.FocusDetector.current(activity, java.time.Instant.now())
                call.respond(session)
            }

            // Phone-logger device-test diagnostic. APK debug screen calls
            // this to confirm the server is actually receiving phone:* rows
            // (i.e. PhoneActivityWorker → /api/activity round-trip works
            // end-to-end). Returns recent phone activity stats over the
            // last 24 h. No auth-sensitive payload — package names only.
            get("/api/_phone_health") {
                val rows = jarvis.Activity.loadEntries(hours = 24)
                    .filter { e -> e.title?.startsWith("phone:") == true }
                val byPkg = rows.groupingBy { it.process ?: "?" }.eachCount()
                    .entries.sortedByDescending { it.value }.take(10)
                    .map { jarvis.web.PhoneHealthBucket(it.key, it.value) }
                val last = rows.maxByOrNull { it.ts }
                call.respond(
                    jarvis.web.PhoneHealthResponse(
                        count24h = rows.size,
                        lastTs = last?.ts,
                        lastPackage = last?.process,
                        topPackages = byPkg,
                    ),
                )
            }

            post("/api/signals/ack") {
                val req = call.receive<ApiAckRequest>()
                if (req.signalId.isBlank() || req.action !in jarvis.Feedback.ALLOWED_ACTIONS) {
                    call.respond(
                        io.ktor.http.HttpStatusCode.BadRequest,
                        "signalId required + action must be one of " +
                            jarvis.Feedback.ALLOWED_ACTIONS.joinToString(",")
                    )
                    return@post
                }
                jarvis.Feedback.append(
                    jarvis.FeedbackEntry(
                        signalId = req.signalId,
                        ts = java.time.Instant.now().toString(),
                        action = req.action,
                    )
                )
                call.respond(io.ktor.http.HttpStatusCode.NoContent)
            }

            post("/api/activity") {
                val entry = call.receive<ActivityEntry>()
                Activity.append(entry)
                val scored = Activity.loadEntries(hours = 1).lastOrNull() ?: entry
                // S4 — bump KnowledgeState confidence if active window matches a
                // catalog concept. Cheap synchronous lookup + append-only write.
                try { jarvis.ActiveDoc.touchOnActivity(scored) } catch (_: Exception) {}
                // Phase 2.1 — proactive signal emission hook (default-OFF).
                ProactiveLoop.consider(scored, client)
                call.respond(io.ktor.http.HttpStatusCode.NoContent)
            }

            post("/api/wiki") {
                val req = call.receive<ApiWikiRequest>()
                if (req.section.isBlank() || req.content.isBlank()) {
                    call.respond(
                        io.ktor.http.HttpStatusCode.BadRequest,
                        "section + content required",
                    )
                    return@post
                }
                MemoryWiki.append(req.section, req.content)
                call.respond(io.ktor.http.HttpStatusCode.NoContent)
            }

            post("/api/sub") {
                val params = call.receiveParameters()
                val cmd = params["cmd"]?.trim().orEmpty()
                if (cmd.isEmpty()) {
                    val list = Subsystems.all.joinToString("\n") { "  ${it.name} - ${it.description}" }
                    call.respond(ApiSubResponse("Available subsystems:\n$list", "n/a"))
                    return@post
                }
                val tokens = cmd.split(" ", limit = 2)
                val sub = Subsystems.get(tokens[0]) ?: run {
                    call.respond(ApiSubResponse("unknown subsystem: ${tokens[0]}", "n/a"))
                    return@post
                }
                val query = tokens.getOrNull(1)?.trim()
                val activity = Activity.loadEntries()
                val wiki = MemoryWiki.recent()
                val output = try {
                    sub.run(
                        client,
                        SubsystemInput(
                            activity = activity,
                            wiki = wiki,
                            recentChat = Conversations.recentAsChatMessages(),
                            userQuery = query,
                        ),
                    )
                } catch (e: Exception) {
                    call.respond(ApiSubResponse("[sub:${sub.name} failed] ${e.message}", "n/a"))
                    return@post
                }
                output.wikiEntry?.let { MemoryWiki.append(it, output.text) }
                val modelTag = output.wikiEntry?.let {
                    Regex("""\(([^)]+)\)""").find(it)?.groupValues?.getOrNull(1)
                } ?: "unknown"
                call.respond(ApiSubResponse(output.text, modelTag))
            }

            get("/apk") {
                val apkPath = java.nio.file.Path.of(
                    "android/build/outputs/apk/debug/android-debug.apk",
                ).toAbsolutePath()
                if (!apkPath.toFile().exists()) {
                    call.respondText(
                        "APK not built yet. From the project root run: gradle :android:assembleDebug",
                        ContentType.Text.Plain,
                    )
                    return@get
                }
                val bytes = apkPath.toFile().readBytes()
                call.response.headers.append(
                    "Content-Disposition",
                    "attachment; filename=jarvis.apk",
                )
                call.respondBytes(bytes, ContentType("application", "vnd.android.package-archive"))
            }

            get("/wiki") {
                call.respondText(
                    "<!doctype html><meta charset=utf-8><pre style='font-family:monospace;background:#1e1e1e;color:#d4d4d4;padding:1em'>${escape(MemoryWiki.readAll())}</pre>",
                    ContentType.Text.Html,
                )
            }

            get("/activity") {
                call.respondText(
                    "<!doctype html><meta charset=utf-8><pre style='font-family:monospace;background:#1e1e1e;color:#d4d4d4;padding:1em'>${escape(Activity.loadRecent(hours = 168L))}</pre>",
                    ContentType.Text.Html,
                )
            }
        }

        // Layer A — tutor SPA bundle + /api/v1/health. Mounts a separate
        // routing block; Ktor merges multiple routing blocks into the same
        // engine, so this composes with the routes above.
        //
        // Bootstrap order matters: installTutorContext() must run BEFORE
        // installTutorRoutes() so /auth/setup can read TutorContextKey off
        // application.attributes. Without this, /auth/setup 500s in prod
        // (the gap Task 21 flagged and Task 25 closes).
        installTutorContext(
            dbPath = jarvis.Config.tutorDbPath,
            ledgerDir = java.nio.file.Path.of(jarvis.Config.tutorLedgerDir),
        )
        installTutorRoutes()
    }.start(wait = true)
}

/**
 * Council 1778105576 (A): atomic dual-write delegated to ChatTurnWriter so
 * user+assistant rows land under one critical section. Avoids orphan-user-turn
 * under SIGTERM/OOM/ENOSPC. Wiki write is best-effort and does not corrupt
 * chat recency on failure. Replaces the previous 3-synchronized-block sequence
 * (Conversations user, Conversations assistant, MemoryWiki) that had no
 * rollback path between blocks 1 and 2.
 *
 * Dual-write to wiki.md is steady-state — NOT a 7-day kill-switch. Council
 * 1778105576 found WebMain never feeds VectorStore today, so dropping the wiki
 * write would not change semantic behavior on VPS. Re-evaluate when the
 * embedding pipeline is intentionally rewired.
 */
private fun appendChatTurn(userMsg: String, assistantReply: String, model: String) {
    ChatTurnWriter.append(userMsg, assistantReply, model)
}

@Serializable
private data class ApiChatRequest(
    val msg: String,
    /** Tutor task-context V0: when present, server pulls the task
     *  header for this id and injects it into the system prompt. */
    val taskId: String? = null,
    /** Optional subject override for header lookup. When null, the
     *  task row's subject is used. */
    val subject: String? = null,
)

@Serializable
private data class ApiChatResponse(val reply: String, val model: String)

@Serializable
private data class ApiSubResponse(val text: String, val model: String)

@Serializable
private data class ApiWikiRequest(val section: String, val content: String)

@Serializable
private data class ApiSignalsResponse(val signals: List<jarvis.ProactiveSignal>)

@Serializable
private data class ApiAckRequest(val signalId: String, val action: String)

@Serializable
private data class ApiSubjectConfidenceRow(
    val subject: String,
    val concepts: Int,
    val avgConfidence: Float,
    val maxConfidence: Float,
)

@Serializable
private data class ApiSubjectConfidenceResponse(val subjects: List<ApiSubjectConfidenceRow>)

@Serializable
private data class ApiConceptConfidenceResponse(
    val concept: String,
    val confidence: Float,
    val staleDays: Long,
    val matched: Boolean,
    /** Server-computed gate: false → frontend should skip inline-link
     *  affordance and render plain text. Driven by env
     *  JARVIS_CONCEPT_LINK_CONFIDENCE_THRESHOLD (default 0.7). */
    val linked: Boolean,
)

private fun conceptLinkThreshold(): Float =
    System.getenv("JARVIS_CONCEPT_LINK_CONFIDENCE_THRESHOLD")?.toFloatOrNull() ?: 0.7f

@Serializable
data class PhoneHealthBucket(val pkg: String, val count: Int)

@Serializable
data class PhoneHealthResponse(
    val count24h: Int,
    val lastTs: String? = null,
    val lastPackage: String? = null,
    val topPackages: List<PhoneHealthBucket> = emptyList(),
)

private fun escape(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun turnHtml(userMsg: String, reply: String, model: String): String = """
    |<div class="turn user">${escape(userMsg)}</div>
    |<div class="turn jarvis"><div class="meta">${escape(model)}</div>${escape(reply).replace("\n", "<br>")}</div>
""".trimMargin()

private val LOGIN_HTML = """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Jarvis · login</title>
  <style>
    body { font-family: ui-monospace, monospace; max-width: 360px; margin: 4em auto; padding: 1em; background: #14161a; color: #d8dee9; }
    h1 { font-size: 1.1em; opacity: 0.7; }
    input { width: 100%; box-sizing: border-box; background: #1c2026; color: #d8dee9; border: 1px solid #3b4252; padding: 0.5em; border-radius: 4px; font-family: inherit; }
    button { margin-top: 0.5em; background: #5e81ac; color: #eceff4; border: 0; padding: 0.5em 1em; cursor: pointer; border-radius: 4px; font-family: inherit; }
  </style>
</head>
<body>
  <h1>jarvis · enter token</h1>
  <!--ERR-->
  <form method="post" action="/login">
    <input type="password" name="token" placeholder="JARVIS_AUTH_TOKEN" autofocus required>
    <button type="submit">unlock</button>
  </form>
  <p style="opacity:0.5;font-size:0.8em">cookie set after success; valid 30 days.</p>
</body>
</html>
""".trimIndent()

private val INDEX_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Jarvis</title>
  <script src="https://unpkg.com/htmx.org@2.0.3" integrity="sha384-0895/pl2MU10Hqc6jd4RvrAemEf28nFUizaP3UO6QySMUTV8z4tF7Ka9KzFxkb1ti" crossorigin="anonymous"></script>
  <style>
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body {
      font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
      max-width: 800px;
      margin: 1em auto;
      padding: 1em;
      background: #14161a;
      color: #d8dee9;
    }
    h1 { font-size: 1.2em; margin: 0 0 1em; opacity: 0.7; }
    .links { font-size: 0.85em; opacity: 0.6; margin-bottom: 1em; }
    .links a { color: #88c0d0; margin-right: 1em; }
    #chat { display: flex; flex-direction: column; gap: 0.5em; min-height: 200px; margin-bottom: 1em; }
    .turn { padding: 0.6em 0.8em; border-radius: 6px; line-height: 1.4; word-break: break-word; }
    .turn.user { background: #2a2f3a; border-left: 3px solid #88c0d0; }
    .turn.jarvis { background: #1c2026; border-left: 3px solid #a3be8c; }
    .turn pre { white-space: pre-wrap; margin: 0; font-family: inherit; }
    .meta { font-size: 0.7em; opacity: 0.5; margin-bottom: 0.3em; }
    form, .row { display: flex; gap: 0.5em; margin-bottom: 0.5em; }
    textarea, input[type=text] {
      flex: 1; background: #1c2026; color: #d8dee9; border: 1px solid #3b4252;
      padding: 0.5em; font-family: inherit; font-size: 1em; border-radius: 4px;
    }
    textarea { resize: vertical; min-height: 3em; }
    button {
      background: #5e81ac; color: #eceff4; border: 0; padding: 0.5em 1em;
      cursor: pointer; font-family: inherit; border-radius: 4px;
    }
    button:hover { background: #81a1c1; }
    .htmx-indicator { display: none; }
    .htmx-request .htmx-indicator { display: inline; opacity: 0.6; }
    details { margin-bottom: 0.5em; }
    summary { cursor: pointer; opacity: 0.7; }
  </style>
</head>
<body>
  <h1>jarvis</h1>
  <div class="links">
    <a href="/tutor/" style="background:#000;color:#ffd700;padding:4px 10px;text-decoration:none;font-weight:bold;letter-spacing:.15em;border:2px solid #ffd700;">▸ TUTOR</a>
    <a href="/wiki" target="_blank">wiki</a>
    <a href="/activity" target="_blank">activity (7d)</a>
    <a href="/apk">apk</a>
  </div>
  <div id="chat"></div>

  <form hx-post="/chat" hx-target="#chat" hx-swap="beforeend"
        hx-on::before-request="this.querySelector('textarea').disabled=true"
        hx-on::after-request="this.querySelector('textarea').disabled=false; this.reset()">
    <textarea name="msg" placeholder="message…" required></textarea>
    <button type="submit">send <span class="htmx-indicator">…</span></button>
  </form>

  <details>
    <summary>tools</summary>
    <form hx-post="/sub" hx-target="#chat" hx-swap="beforeend"
          hx-on::after-request="this.reset()">
      <input type="text" name="cmd" placeholder='sub name [query] — empty lists subs'>
      <button type="submit">run sub</button>
    </form>
    <form hx-post="/reflect" hx-target="#chat" hx-swap="beforeend">
      <button type="submit">run daily reflection now</button>
    </form>
  </details>
</body>
</html>
""".trimIndent()
