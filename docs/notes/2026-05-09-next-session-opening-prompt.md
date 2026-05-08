# Next session opening prompt — paste this verbatim

This file exists so the next Claude Code session has a clean,
unambiguous starting brief. The previous session (2026-05-08, 14
commits, 4 council review rounds → APPROVED) wrapped with a
gate-zero contract: APK install + Claude restart + chromium install
must precede any feature work.

User state on resume:
- Chromium for Playwright: ✅ installed (per user 2026-05-08 evening)
- New APK install: ⏳ pending user side-load from
  `https://corgflix.duckdns.org/apk`
- Claude Code restart: ⏳ pending (this file is being read because
  it happened — Playwright MCP tools should now appear at session
  start)

## Paste this on session open

```
Read docs/notes/2026-05-08-autonomous-resume.md end-to-end before any
action. We are picking up from yesterday's autonomous session that
shipped 14 commits across LLM relay + study-companion + grade sync
infrastructure. The session-end "Gate-zero" section names two
preconditions: (1) APK install on phone and (2) Claude session
restart for Playwright MCP. Chromium is already installed on PC.

Before any feature work, verify three things:
1. ssh root@46.247.109.91 'systemctl is-active jarvis' returns "active"
2. ssh root@46.247.109.91 'tail -1 /opt/jarvis/data/conversations.jsonl
   | grep -o "\"model\":\"[^\"]*\"" || echo no-conversations'
3. Playwright MCP tools (browser_navigate, browser_click, etc.) are
   in your tool list. If not, the MCP didn't load — check claude mcp
   list.

Then ask me whether the APK is installed on phone yet. If not, that's
the gate-zero blocker — Foreground Service shipped in commit 5aad26e
is write-only until APK side-load.

Once APK confirmed installed:

- FIRST commit of this session per resume-note byte-spec: forcing-
  function MVP. Telegram bot via @BotFather + 20-line VPS cron at
  09:00 daily POSTing weakest-subject's next concrete action to
  api.telegram.org/bot<TOKEN>/sendMessage. Env vars
  TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID in /opt/jarvis/.env. No
  per-day log, no acknowledgment loop in v1. This is the structural
  REJECT-fix from council round 2 (First Principles agent's "bot is
  pull-mode, not forcing function"). Without it the entire session's
  scaffolding is conditional on user remembering to open chat.

- After Telegram MVP ships + smokes (one daily push observed on
  user's phone): use Playwright MCP to scrape the SO restricted page
  at https://edu.info.uaic.ro/sisteme-de-operare/SO/index.html with
  Basic auth so2026 / i+a=IA. Save HTML + extracted text to
  /opt/jarvis/data/archival/_extras/SO/restricted-content.html via
  scp. The IP-allowlist on edu.info.uaic.ro means VPS curl returns
  401; the user's PC has the right IP, so Playwright running on PC
  works.

- Then: tighten CHAT_SYSTEM_PROMPT in Prompts.kt to auto-emit
  [[plan: today]] + [[assignments]] when user asks anything
  time-bound — current behavior is LLM-judgment-dependent and may
  miss surfacing the PS HW May 21 deadline aggressively enough.

- Then: POO sentinels for Lab eval 2 (30pt week 14/15) + final
  exam (30pt). Currently sync writes per-lab activity but ignores
  60 of 100 POO max points.

- Then: ALO column-rename detection in
  tools/sync-grades-from-sheets.py — header-row hash compared to
  expected; mismatch writes failure to grades-sync-status.json.

User context:
- Matricol 31091001031ROSL251002, group IA2/IA12.
- 5 active subjects: POO (AI1201, E), SO+RC (AI1202, E), ALO
  (AI1203, E), PS (AI1204, V — no June exam), PA (AI1205, E).
- PS HW deadline 2026-05-21 (NO restanță). 13 days as of
  yesterday.
- Finals June 1-21. Current standing per [[grades]] last sync:
  POO 7%, ALO 24% vs pass 44%, PS 35%, PA 39%, SO+RC 50%.

Memory + core_memory.md already pin this. The bot can self-serve
this context via [[grades]], [[plan: today]], [[catchup: 7]],
[[grades_sync_status]], [[assignments]].
```

## What "good" looks like at end of next session

- One Telegram message visible on user's phone, from @<botname>,
  fired by VPS cron at 09:00 (user's local TZ Europe/Bucharest),
  naming the weakest subject's next concrete action.
- SO restricted page content sitting at
  /opt/jarvis/data/archival/_extras/SO/restricted-content.html and
  reachable via [[search]] / [[recall]].
- One smoke turn through phone APK that triggers
  [[grades_sync_status]] + [[plan: today]] in the LLM's reply.

If those three observable signals don't ship, the gate-zero contract
was followed but the session's net delivery is a deferral, not a
delivery.
