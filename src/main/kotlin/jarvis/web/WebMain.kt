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
import jarvis.Llm
import jarvis.LlmFactory
import jarvis.MemoryWiki
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
    val history = mutableListOf<ChatMessage>()
    val historyLock = Mutex()

    println("Jarvis web on http://localhost:$port (Ctrl+C to stop)")
    println("Auth required. First visit: http://localhost:$port/login")

    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }

        intercept(ApplicationCallPipeline.Plugins) {
            val path = call.request.path()
            if (path == "/login" || path == "/healthz") return@intercept

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
                    history.add(ChatMessage("user", msg))
                    val sysPrompt = CHAT_SYSTEM_PROMPT + "\n\n# Context\n" + buildChatContext()
                    val messages = listOf(ChatMessage("system", sysPrompt)) + history
                    val (text, modelName) = try {
                        client.complete(messages)
                    } catch (e: Exception) {
                        history.removeLast()
                        return@withLock "[error] ${e.message}" to "n/a"
                    }
                    history.add(ChatMessage("assistant", text))
                    MemoryWiki.append("conversation ($modelName)", "**user:** $msg\n\n**jarvis:** $text")
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
                    sub.run(client, SubsystemInput(activity, wiki, query))
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
                val (reply, model) = historyLock.withLock {
                    history.add(ChatMessage("user", msg))
                    val sysPrompt = CHAT_SYSTEM_PROMPT + "\n\n# Context\n" + buildChatContext()
                    val messages = listOf(ChatMessage("system", sysPrompt)) + history
                    val (text, modelName) = try {
                        client.complete(messages)
                    } catch (e: Exception) {
                        history.removeLast()
                        return@withLock "[error] ${e.message}" to "n/a"
                    }
                    history.add(ChatMessage("assistant", text))
                    MemoryWiki.append("conversation ($modelName)", "**user:** $msg\n\n**jarvis:** $text")
                    text to modelName
                }
                call.respond(ApiChatResponse(reply, model))
            }

            post("/api/activity") {
                val entry = call.receive<ActivityEntry>()
                Activity.append(entry)
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
                    sub.run(client, SubsystemInput(activity, wiki, query))
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
    }.start(wait = true)
}

@Serializable
private data class ApiChatRequest(val msg: String)

@Serializable
private data class ApiChatResponse(val reply: String, val model: String)

@Serializable
private data class ApiSubResponse(val text: String, val model: String)

@Serializable
private data class ApiWikiRequest(val section: String, val content: String)

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
