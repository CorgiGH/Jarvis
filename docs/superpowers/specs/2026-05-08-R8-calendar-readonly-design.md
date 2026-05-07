# R8 — Read-only Google Calendar pull (deferred — user OAuth required)

## Context

Roadmap Phase 6.1 + deep-research recommendation #8. Khoj-style read-only minimal-scope OAuth. Adds a `[[calendar]]` chat tool returning today's + tomorrow's events.

## Status: SHIP CODE STUB ONLY

The OAuth grant flow is interactive and on the autonomous-agent CAN'T list (per `docs/notes/2026-05-08-autonomous-resume.md`: "modify /opt/jarvis/.env beyond an existing key" + "OAuth browser flows" both forbidden). Therefore:

- **Ship:** the chat tool, its parser, env-vars, fallback "(calendar not configured)" message.
- **Don't ship:** OAuth client setup, refresh-token plumbing, gcalcli install on VPS.
- **User completes** by running `gcalcli init` on VPS once + sets `JARVIS_CALENDAR_ENABLED=true`.

## Approaches

- **(a) gcalcli subprocess (Khoj-pattern).** ✓ Picked. CLI does OAuth + caches refresh token. jarvis just shells out to `gcalcli agenda` and parses stdout. Minimal jarvis-side code.
- **(b) Direct Google Calendar API via Ktor client + service account.** Requires GCP project setup; service account can't read personal calendar.
- **(c) CalDAV on Google.** Deprecated for personal accounts since 2014.

## Design

**New chat tool:** `[[calendar: <range>]]` where range is `today` (default), `tomorrow`, `week`, or a free-form `gcalcli agenda` argument like `2026-05-21` for explicit date.

**ChatTools.executeCalendar(args):**
1. Read env `JARVIS_CALENDAR_ENABLED`. If not "true", return "(calendar not configured — see docs/superpowers/specs/2026-05-08-R8-calendar-readonly-design.md)".
2. Spawn `gcalcli agenda <range>` via ProcessBuilder. 5s timeout.
3. Capture stdout + return cleaned (≤2KB).
4. On any error, return "(calendar fetch failed: <kind>)".

**No persistent cache** of calendar events. Per-call subprocess spawn. Acceptable at low call frequency (chat-driven only).

**System-prompt hint:** add brief one-liner to `CHAT_SYSTEM_PROMPT` describing the tool, gated by env. Skip for v1 — ship the tool, let the user invoke explicitly.

## Activation steps (user runs once)

```bash
ssh root@46.247.109.91
apt install gcalcli
gcalcli init   # browser-based OAuth grant — user must run interactively
echo 'JARVIS_CALENDAR_ENABLED=true' >> /opt/jarvis/.env
systemctl restart jarvis
```

## Edge cases

- gcalcli not installed → ProcessBuilder.start() throws IOException → "(calendar fetch failed: IOException)".
- Token expired (gcalcli's own refresh fails) → stderr leaks "Authentication failed" → caller sees error string.
- Range argument with shell metacharacters → escape via array-mode ProcessBuilder (no shell expansion).

## Acceptance criteria

- 4 tests on the parser/wiring layer (env-disabled returns hint, unknown range falls through to gcalcli, output truncation).
- Smoke (post-user-activation): `[[calendar: today]]` returns calendar text or graceful failure.

## LOC estimate

~50 main + ~30 tests.
