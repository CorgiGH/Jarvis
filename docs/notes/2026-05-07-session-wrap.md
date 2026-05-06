# Jarvis-Kotlin session wrap — 2026-05-07

Second session. VPS deploy + ingest mistake + council-driven revert.
Restart point for next session.

## What landed today (commit-by-commit)

| Commit | Phase | What |
|---|---|---|
| `2e485e2` | step 0 | `JARVIS_DATA_DIR` env override so VPS state lives under `/opt/jarvis/data` instead of `/root/.life-os` |
| `eb529b1` | step 1 | `tools/vps-install.sh` for Ubuntu 22.04 with existing nginx + JDK 21 |
| `cc2777c` | step 0 | LLM provider abstraction: `Llm` interface + `ClaudeMaxLlm` + `OpenRouterLlm` + `AnthropicLlm` stub + `LlmFactory` |
| `e01ed12` | F | Bearer-token auth on all web endpoints, `JARVIS_AUTH_TOKEN` required at boot |
| `2eabcfd` | step 0' | Replace OpenRouter chat path with GitHub Copilot CLI provider; OpenRouter retained only for embeddings |
| `13a85d3` | step 4 | Reroute PC activity logger to `POST /api/activity` (env-controlled, with local-file fallback on POST failure) |
| `589100c` | council fallout | Revert wiki.md ingest + isolate archival corpus to `/opt/jarvis/data/archival/{second-brain,study-guide}/` |

## Operational state at session end (2026-05-07)

- **VPS:** `corgflix.duckdns.org` → 46.247.109.91 (Ubuntu 22.04, JDK 21,
  Node 20, MC server eating 4.2 GB / 7.8 GB RAM, jarvis JVM at -Xmx512m).
- **TLS:** Let's Encrypt cert via `certbot --nginx`, auto-renews. Reverse
  proxy in `/etc/nginx/sites-enabled/jarvis` → 127.0.0.1:8080.
- **Service:** `systemctl is-active jarvis` → active. EnvironmentFile
  is `/opt/jarvis/.env` (mode 600). HOME=/root so `~/.claude` and
  COPILOT auth state stay where the CLIs put them.
- **Provider:** `JARVIS_LLM=copilot` (placeholder until `claude login`
  binds the Max plan on VPS). Copilot is authed via
  `COPILOT_GITHUB_TOKEN` (fine-grained PAT with "Copilot Requests"
  permission) so it runs headless without touching the keychain.
- **State dir:** `/opt/jarvis/data/` — `wiki.md` (13 lines, two real
  conversation entries), `activity.jsonl` (live; growing every 5 min
  from the PC logger), `archival/` (316 .md / 3.2 MB; isolated from
  chat retrieval), `wiki.md.bak.1778090717` (7.3 MB pre-revert backup;
  **DO NOT DELETE** until Step B mutex commit lands AND a regression
  test confirms N concurrent /api/wiki POSTs do not corrupt the
  `^## ` boundary; "looks fine" does not detect byte interleaving).
- **PC logger:** running via Startup-folder VBS shortcut. POSTs to
  `https://corgflix.duckdns.org/api/activity` every 5 min. Falls back
  to local `~/.life-os/activity.jsonl` if VPS unreachable. `.env` at
  `C:\Users\User\jarvis-kotlin\.env` carries `JARVIS_BACKEND_URL`,
  `JARVIS_AUTH_TOKEN`, `OPENROUTER_API_KEY`.
- **Android APK:** at `/opt/jarvis/data/archival/...` no — APK is at
  `/opt/jarvis/android/build/outputs/apk/debug/android-debug.apk`.
  Phone is pointed at `https://corgflix.duckdns.org` with the auth
  token. Voice + persisted prefs version (post-`b5b57d6`) is
  installed.
- **Auth token:** value in `C:\Users\User\jarvis-kotlin\tools\AUTH_TOKEN.txt`
  on PC (gitignored). Same value in `/opt/jarvis/.env`
  `JARVIS_AUTH_TOKEN=` on VPS. Phone app's auth-token field stores
  the same.
- **Passwordless SSH:** ed25519 key now in `~/.ssh/authorized_keys` on
  VPS, no more password prompts.

## Council 1778078829 (2026-05-07)

Asked: is the proposed retrieval fix (drop `WIKI_RECENT_ENTRIES` from
30 to 5 + filter sections starting with `[SOURCE:`) the right next
step after the 988-file ingest dominated `recent()`?

Verdict: **WRONG APPROACH.** All five clean agents (Devil's Advocate,
Domain Expert, Pragmatist, First Principles, Risk Analyst) converged
independently on schema-not-retrieval. The single `wiki.md` was being
used as four different abstractions at once (chat memory, append log,
searchable corpus, knowledge base) and the ingest exposed it. The
correct move is the Letta/MemGPT/Khoj pattern — separate conversation
memory from archival knowledge.

Today executed step 1 of the verdict: revert ingest, isolate archival.
Step 2 (Letta-split refactor) intentionally deferred to next session.

Transcript at `.claude/council-cache/council-1778078829.md`.

## Sequence resumption

Locked at session end:

1. **Step 0 LLM abstraction** — DONE (cc2777c)
2. **F: auth** — DONE (e01ed12)
3. **VPS deploy** — DONE (eb529b1, 2e485e2, certbot, systemd, copilot CLI)
4. **Reroute logger** — DONE (13a85d3)
5. **APK update** — DONE (deployed, working over HTTPS)
6. **Populate** — REVERTED per council; archival isolated (589100c). Re-do as part of step B below.
7. **B: Letta-split refactor** — NEXT. ~2-4 hours. Outline below.
8. **D: ADB-over-WiFi** — pending, ~10 min one-time pairing.
9. **E: new subsystems** — user picks.

## Step B (next session) — Letta/Khoj-style storage split

> **BLOCKER — DO BEFORE ANY OTHER STEP B WORK:** add a process-wide
> write-mutex around every appender for `wiki.md` and `activity.jsonl`
> *before* migrating either file. Council 1778078829 rated this **HIGH**;
> council 1778102666 confirmed and elevated it. Right now four endpoints
> (`POST /api/chat`, `/api/sub`, `/api/wiki`, plus `MemoryWiki.append` from
> the chat handler) all hit `Files.writeString(..., APPEND)` on `wiki.md`
> with no lock; `/api/activity` does the same on `activity.jsonl`.
> Concurrent POSTs can interleave bytes mid-section and silently corrupt
> the `^## ` regex split — recovery would require restoring from
> `wiki.md.bak.1778090717`, which is the ONLY surviving copy of the
> pre-revert content. Do NOT touch the schema (move conversations out,
> ingest archival, etc.) until the mutex commit is in and a smoke test
> confirms N concurrent POSTs do not corrupt the section boundary.

The actual fix the council prescribed. Outline so resume is concrete:

1. **`conversations.jsonl`** at `${JARVIS_DATA_DIR}/conversations.jsonl`
   - Append-only, one JSON message per line (role / content / ts /
     model / msg_id).
   - FIFO truncate at e.g. 1000 entries (or 5 MB) so retrieval is bounded.
   - Replaces wiki.md as the source of `recent(N)` for chat context.
   - Migration: walk existing `wiki.md` "## [...] conversation (...)"
     sections one-time and emit JSONL rows; archive the old wiki.md.
2. **`archival/`** indexed
   - Pick: SQLite FTS5 (no extra deps, fast lex search, no embeddings
     cost) **OR** OpenAI `text-embedding-3-small` via OpenRouter
     (~$0.20 to embed 316 files; cosine similarity at chat time).
   - Recommend FTS5 for v1: zero $ cost, no extra service, works on
     316 files; embeddings can layer on top later.
   - Indexer scans `archival/`, populates SQLite at
     `${JARVIS_DATA_DIR}/archival.db`, watches mtimes for incremental
     updates.
   - Retrieval surface: a new `/sub search <query>` subsystem (or a
     `/api/search` endpoint) — NEVER auto-injected into chat context.
     Chat draws on archival only when the user explicitly asks or a
     subsystem decides it's relevant.
3. **`core_memory.md`** at `${JARVIS_DATA_DIR}/core_memory.md`
   - Small (≤4 KB) mutable file. Always prepended to chat system prompt.
   - Pinned context: the finals window, identity facts, current
     priorities, anti-sidetrack rules. Same shape as Letta's core_memory.
4. **Write-mutex** on `MemoryWiki.append()` and the new
   `Conversations.append()` and any `/api/wiki` / `/api/activity`
   handler. Today the `/api/wiki` route has no lock; concurrent POSTs
   could interleave bytes mid-section and silently corrupt the regex
   split. Activity.jsonl was single-writer (PC logger only) so it was
   fine — but now it's also reachable via `/api/activity` POST. Add
   a process-wide `Mutex` for both files.
5. **Regression check:** add a smoke test (or a one-shot `gradle run
   --args="check"`) that asserts `recent(N)` returns only entries
   typed as conversation/reflection.
6. **Don't re-ingest into wiki.md.** The archival corpus is already
   on disk under `/opt/jarvis/data/archival/`. The indexer reads
   from there directly.

### Open design choices for next session

- **FTS5 vs embeddings (DEFERRED to Step B kickoff, not pre-decided):**
  FTS5 is the leading candidate — zero $ cost, no API dependency,
  works on 316 files in-process via SQLite — but the actual call should
  be made fresh next session against the council-1778102666 ADR
  separation rule (Status:Proposed ≠ Decision). Embeddings remain a
  viable alternative; ~$0.20 to embed the corpus and a second arm can
  layer over FTS5 anyway. Do NOT lock in FTS5 just because this note
  recommends it.
- **conversations.jsonl size cap:** 1000 entries seems fine for v1;
  reflection gets to skim the tail when it summarizes.
- **core_memory.md seed:** import the relevant fields from
  `~/.claude/projects/C--Users-User/memory/MEMORY.md` (finals window,
  identity, language preference, sidetrack pattern, etc.) on first
  run. Confirm with user before taking memory content automatically.

## Tools / scripts on disk

- `tools/vps-install.sh` — idempotent Ubuntu 22.04 installer
  (claude CLI, certbot, systemd unit, nginx vhost). Updated 2026-05-07
  to drop the HOME override and document that data lives under
  `/opt/jarvis/data` via `JARVIS_DATA_DIR`.
- `tools/start_logger_hidden.vbs` — Startup-folder shortcut target
  for the always-on PC activity logger.
- `tools/install_scheduled_task.ps1` — scheduled-task registration
  helper (admin path; we used Register-ScheduledTask cmdlet directly
  instead because schtasks denied this user).
- `tools/start_reflect_hidden.vbs` — daily reflection launcher.
- `tools/reflect_if_stale.ps1` — fallback for environments where
  Register-ScheduledTask is denied.
- `tools/populate.ps1` — DEPRECATED 2026-05-07 (council fallout). Now
  also excludes node_modules / build / dist / etc., but the script
  itself shouldn't be the primary ingest path going forward.
- `tools/stage_archival.ps1` — bundles .md tree (with the same
  excludes) into a zip for scp/unzip onto the VPS archival/ dir.
- `tools/AUTH_TOKEN.txt` — gitignored. Token value.
- `tools/vps.env` — gitignored. Latest VPS .env shape used during
  deploy.

## Quick resume checklist

> **Meta-cue:** memory's 2026-05-04 priority lock (PS HW 2026-05-21,
> finals June 1-21) is real. If study work is not done, Step B waits.
> The 2026-05-04 council verdict explicitly named meta-planning sessions
> as a sidetrack vector, and council 1778102666 flagged the wrap-ritual
> itself as a potential instance. Honor the lock unless explicitly
> rescinded.

```powershell
# verify state
$VPS = "root@46.247.109.91"
ssh $VPS "systemctl is-active jarvis; wc -l /opt/jarvis/data/wiki.md; wc -l /opt/jarvis/data/activity.jsonl; ls /opt/jarvis/data/archival; ls -la /opt/jarvis/data/wiki.md.bak.* 2>/dev/null"
curl.exe -s https://corgflix.duckdns.org/healthz   # expect: ok
git -C C:\Users\User\jarvis-kotlin log --oneline -10
git -C C:\Users\User\jarvis-kotlin log --oneline 2eabcfd..HEAD | wc -l   # day-2 commit count, self-correcting
```
