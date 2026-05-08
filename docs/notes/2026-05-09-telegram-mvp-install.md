# Telegram MVP install — forcing-function push

The autonomous agent shipped `tools/send-daily-push.py` as the
**push-mode** counterpart to the pull-mode chat bot. Council
round-3 First Principles flagged this as the load-bearing missing
piece: every other study-companion feature is conditional on the
user remembering to open the app. The daily Telegram push removes
that condition.

Status as of 2026-05-09 morning:
- `tools/send-daily-push.py` written, 15/15 unit tests green.
- Script staged on VPS at `/opt/jarvis/tools/send-daily-push.py`
  (chmod +x, owned by root).
- Dry-run on real VPS data prints a coherent message
  (POO 8% weakest, Lab eval 2 surfaces).
- Cron line **NOT installed yet** — waits until the env keys land
  and a manual smoke-test confirms a Telegram message lands on
  the user's phone.

The remaining steps are user-side because the autonomous-agent
operating manual forbids modifying `/opt/jarvis/.env` beyond
appending a key the user already has locally. Token + chat-id are
new keys the user must obtain.

## Step 1 — create the bot via @BotFather (~5 min on phone)

1. Open Telegram on the phone.
2. Search `@BotFather`, start a chat.
3. Send `/newbot`.
4. Name: anything (e.g. `Jarvis Daily`).
5. Username: must end in `bot` (e.g. `victor_jarvis_bot`). Save the
   bot's `t.me/<username>` link — you'll need to message it once
   so it can talk to you.
6. BotFather replies with `Use this token to access the HTTP API: <TOKEN>`.
   Copy the token. **Treat it like a password** — anyone with it
   can post to your bot.

## Step 2 — discover your numeric chat id

1. From your phone, open the bot's chat link from step 1.
2. Send any message (e.g. `/start`). The bot won't reply — that's
   fine, it has no handler. We only need Telegram to register that
   you've contacted it.
3. From any machine with curl:
   ```bash
   curl -s "https://api.telegram.org/bot<TOKEN>/getUpdates" | python3 -m json.tool
   ```
4. Look for `"chat":{"id":<NUMBER>,...,"first_name":"..."}`. That
   number is your chat id. Save it.

## Step 3 — append env keys on VPS

```bash
ssh root@46.247.109.91
echo 'TELEGRAM_BOT_TOKEN=<paste-token-here>' >> /opt/jarvis/.env
echo 'TELEGRAM_CHAT_ID=<paste-chat-id-here>' >> /opt/jarvis/.env
exit
```

The bot binary doesn't load these — the cron job loads them from
its own shell, so a `systemctl restart jarvis` is **not** needed.

## Step 4 — smoke-test once

```bash
ssh root@46.247.109.91 'set -a; . /opt/jarvis/.env; set +a; /opt/jarvis/tools/send-daily-push.py'
```

Expected: phone receives a Telegram message in <5s naming your
weakest subject and its next concrete action. Console prints
`sent ts=YYYY-MM-DD status=200 subject=<X>`.

If you get `telegram HTTP 401`, the token is wrong. If you get
`telegram HTTP 400 Bad Request: chat not found`, the chat id is
wrong (or you never sent a message to the bot).

## Step 5 — install the cron entry

```bash
ssh root@46.247.109.91
crontab -l > /tmp/crontab.bak
(crontab -l; echo '0 9 * * * set -a; . /opt/jarvis/.env; set +a; /opt/jarvis/tools/send-daily-push.py >> /var/log/jarvis-push.log 2>&1') | crontab -
crontab -l | tail -3
```

`0 9 * * *` = 09:00 daily, server's local TZ. The VPS is on UTC,
so this fires at 09:00 UTC = 12:00 Bucharest. If you'd rather the
ping land at 09:00 Bucharest local, use `0 6 * * *` instead
(server time = local − 3h during summer, − 2h during winter).

## Step 6 — verify the next morning

The morning after install, check `/var/log/jarvis-push.log` —
should have a single `sent ts=...` line and no errors. The phone
should have a Telegram notification.

## Rollback / disable

- One-shot disable: `ssh root@46.247.109.91 'crontab -l | grep -v send-daily-push.py | crontab -'`
- Re-enable: re-run step 5.
- Permanent removal: also delete `/opt/jarvis/tools/send-daily-push.py`
  and the two env lines.

## Variables of behavior

- `JARVIS_PUSH_DRY_RUN=1` — prints the message instead of POSTing.
  Useful for testing format changes without spamming yourself.
- `JARVIS_GRADES_FILE` / `JARVIS_ASSIGNMENTS_FILE` — override
  paths (used by tests; production defaults are correct).

## Known limits — v1

- One subject per ping. If POO (8%) and ALO (24%) both need
  attention, only POO surfaces until POO catches up. v2 could
  surface top-2 weakest if the gap to next subject is small.
- "Next action" picks the closest-due assignment for the weakest
  subject. If the weakest has no open assignments, the message
  falls back to a `[[study_now: SUBJECT]]` prompt. v2 could fall
  back to nearest-due across all subjects.
- No acknowledgment loop — the bot can't tell if you read the
  message or acted on it. v2 adds inline-keyboard buttons
  (Done / Snooze / Wrong-priority) feeding `feedback.jsonl`.
- Hard-coded 09:00. Quiet-hours config lives in the proactive
  loop's R7 settings screen but doesn't extend here yet.
