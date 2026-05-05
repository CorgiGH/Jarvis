# Jarvis-Kotlin session wrap — 2026-05-06

Single-session full build of the life-OS harness. This file is the
restart point for the next session.

## Repo

`C:\Users\User\jarvis-kotlin` on `main` branch. Local-only (no remote
pushed). Activity log + wiki + embeddings store at `~/.life-os/`.

## What's built (commit-by-commit)

| Commit | Phase | What |
|---|---|---|
| 38679c2 | 1 | JVM chat REPL with honest-mirror persona |
| a0eb6a6 | 2 | Activity logger via JNA Win32 |
| 89d19ea | — | Logger Startup-folder auto-launch |
| 6f9093e | 3 | Daily reflection job |
| 0dec8f0 | B | Subsystems framework + judgment / dots / teach |
| b833fce | A | 6 coding tools + agentic Coder subsystem |
| 2ad3305 | E | Daily reflection scheduled at 08:00 |
| 432c9db | — | Refined prompts + timing & ctx-model subsystems |
| fdadf04 | F (web) | Ktor server + HTMX UI |
| b9fa2ec | — | Semantic memory (OpenRouter embeddings + cosine search) |
| e1ff4a4 | 5 | Android Compose UI + APK build + over-LAN sideload |
| 39dc34f | G | Voice IO on Android (RecognizerIntent + TTS) |
| b5b57d6 | — | Persist backend URL + TTS toggle via DataStore |
| cc2777c | step 0 | LLM provider abstraction (claude-max default + openrouter + anthropic stub) |
| e01ed12 | F (auth) | Bearer token auth on all web endpoints |

## Operational state at session end

- **Activity logger:** auto-starts on logon via Startup-folder shortcut
  pointing at `tools/start_logger_hidden.vbs`. Captures every 5 min,
  writes `~/.life-os/activity.jsonl`.
- **Daily reflection:** Windows scheduled task `JarvisDailyReflection`
  registered for 08:00 local. Runs `bin/jarvis-kotlin.bat reflect`
  via `tools/start_reflect_hidden.vbs`.
- **Web server:** not auto-started; `gradle runWeb` on demand. Refuses
  to boot without `JARVIS_AUTH_TOKEN`.
- **APK:** debug-signed at `android/build/outputs/apk/debug/android-debug.apk`,
  17.32 MB. Latest build is post-auth, requires token in app field.
- **Provider:** default `claude-max` (subprocess `claude --print`,
  charges Max plan, NOT Anthropic API). Verified end-to-end with the
  Timing subsystem on 2026-05-06.

## Subsystems (6 total)

`judgment` (honest reality-check), `dots` (cross-domain pattern finder),
`teach` (spaced-rep quiz), `timing` (surface-now/defer/skip), `ctx-model`
(energy/stress/life-season inference), `coder` (agentic 30-iter loop
with read/write/edit/grep/glob/bash tools).

## Sequence resumption

Order locked at session end:

1. **Step 0 LLM abstraction** — DONE (cc2777c).
2. **F: auth** — DONE (e01ed12).
3. **VPS prep + deploy** — NEXT. Blocked on info gathering (see below).
4. **Migrate `~/.life-os` data** — scp from PC to VPS.
5. **Reroute logger** — change `activity_logger` to POST to VPS over HTTPS
   instead of writing local file. Backend gets new `/api/activity` route
   with bearer auth.
6. **APK update** — backend URL default points at `https://corgflix.duckdns.org`.
7. **Populate** — ingester walks `Desktop/Second brain` and
   `Desktop/SO/os-study-guide`, creates wiki entries tagged with source,
   bulk embeds.
8. **B: prompt refinement** — driven by real data after population.
9. **D: ADB-over-WiFi** — fast APK redeploy, ~10 min one-time pairing.
10. **E: new subsystems** — user picks (calendar / todo / journal / finance / etc.).

## Required info before Step 3 (VPS deploy)

User needs to provide on next session start:

- ByteHosting VPS public IPv4
- SSH username (root or sudo user)
- OS + version: `ssh user@vps cat /etc/os-release` (PRETTY_NAME)
- Confirm `corgflix.duckdns.org` is the user's at duckdns.org and
  whether it currently points at this VPS or needs re-pointing
- Free RAM after MC `-Xmx4G` reduction — `free -h`. Need ≥1.5 GB
  headroom for jarvis JVM
- Port 443 free? `sudo ss -tlnp | grep ':443'`

Once provided, generate `tools/vps-install.sh` (idempotent installer:
JDK 21 + Caddy auto-HTTPS + Claude Code CLI + systemd unit +
firewall) + scp/start commands.

## Pending decisions

- Logger transport security: bearer token on every POST, OR mTLS
  client cert. Bearer is simpler and matches existing endpoint auth;
  pick that unless threat model changes.
- Whether to keep PC web server for LAN-only fallback when VPS is
  down. Probably yes — adds zero overhead, useful during outages.
- iOS client: CMP iOS module is theoretically possible, but no Apple
  Developer account and no iOS device in scope. Defer indefinitely.

## What was overridden in this session (for honesty)

- 2026-05-04 priority-lock council verdict (PS HW + finals window) —
  rescinded explicitly by user for this session.
- Brainstorming skill HARD-GATE — overridden by explicit "stop bs and
  just get to work" instruction.
- Council verdict against full Kotlin harness (council-1778011370) —
  overridden by explicit "full kotlin life-os harness" pick.

User rescinded each gate consciously. Outcome: 15 commits, full
working life-OS, real shipped artifacts. The "council was wrong"
result here does NOT generalize — most council pushbacks land
correctly. See feedback notes.

## Quick resume commands

```powershell
# verify state
git -C C:\Users\User\jarvis-kotlin log --oneline -15
Get-ScheduledTask -TaskName "JarvisDailyReflection" | Select State
Get-Process | Where { $_.ProcessName -eq "java" } | Select Id, StartTime
Get-Content C:\Users\User\.life-os\activity.jsonl -Tail 3

# bring up local web (test before next migration step)
$env:JARVIS_AUTH_TOKEN = "<paste from .env>"
gradle -p C:\Users\User\jarvis-kotlin runWeb
```
