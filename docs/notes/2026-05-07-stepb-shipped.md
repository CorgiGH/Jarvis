# Jarvis-Kotlin session wrap — 2026-05-07 (Step B SHIPPED)

Third session of the day. Step B Letta-split refactor + VectorStore wiring
landed on `main` and deployed to corgflix.duckdns.org. 6 commits, 4 councils.

## What landed today

| Commit | Phase | What |
|---|---|---|
| `f927f11` | Step B blocker | Process-wide write-mutex on `wiki.md` + `activity.jsonl`. Race-tested at 1000 concurrent appends. |
| `97b099e` | Step B.1 | `Conversations.kt` + `conversations.jsonl`. Schema `{role, content, ts, model, msg_id, kind="chat", v=1}`. NO FIFO truncation. 5 tests. |
| `83e2558` | Step B.3+B.4 | `core_memory.md` empty + `buildChatContext` rewired to `Conversations.recent()`. Dual-write to `wiki.md` kept for legacy `/wiki` page. |
| `b7959ee` | Step B.5 | `WikiToJsonlMigrator` CLI (gradle `migrateWiki`, NOT used in deploy — user said wiki content useless). |
| `e87425c` | Step B hardening | `ChatTurnWriter` atomic dual-write (one critical section, no orphan-turn under SIGTERM/OOM). Migrator dedup-by-msgId. CoreMemory privacy scanner (matricol/email/phone regex with WARN log). v-version guard on read. `seq` field on schema. ChatTurnWriter→recent integration test. |
| `4e7bdab` | VectorStore wiring | `EmbeddingsPipeline.indexTurnAsync` called from `ChatTurnWriter.append`. Lazy-init from `OPENROUTER_API_KEY`. SupervisorJob+Dispatchers.IO scope so embed latency does NOT block `/api/chat`. Failures log + swallow. |

## Council transcripts

- `council-1778104395.md` — pre-impl on Letta-split design. Verdict: FLAWED. Ruled FTS5-vs-embeddings DEFERRED entirely (use grep-as-tool when needed).
- `council-1778105576.md` — post-impl on shipped diff. Verdict: FLAWED. Demanded 6 hardening fixes before VPS deploy.
- `council-1778155110.md` — deploy strategy. Verdict: APPROVED with named follow-up. Skip migration (user said wiki useless), `installDist` + `scp`, `mv-old` rollback, copy-paste first deploy.

## Operational state at session end (2026-05-07 ~12:30 UTC)

- VPS web at `https://corgflix.duckdns.org/`. systemd `jarvis.service` active.
  Memory ~57 MB, JVM at -Xmx512m, host RAM 350 Mi free + 2.7 Gi available.
- LLM provider: `JARVIS_LLM=copilot` (Copilot CLI subprocess).
- VPS state at `/opt/jarvis/data/`:
  - `conversations.jsonl` (8 rows from smoke chats, schema v=1, kind=chat, seq=0/1, paired msg_ids)
  - `embeddings.json` ~19.5 KB after first turn through new EmbeddingsPipeline (was 0 before this session — VectorStore was starved on VPS)
  - `activity.jsonl` 196 lines + still growing every 5 min from PC logger
  - `core_memory.md` does NOT exist on VPS yet (preamble = empty no-op until user creates one)
  - `wiki.md` 596 bytes, `wiki.md.bak.1778090717` 7.3 MB (rollback artifact, keep)
  - `archival/` 316 .md (3.2 MB, untouched)
- Rollback chain on VPS:
  - `/opt/jarvis/jarvis-kotlin/` — current (commit 4e7bdab)
  - `/opt/jarvis/jarvis-kotlin-prev/` — Step B w/o VectorStore wiring
  - `/opt/jarvis/jarvis-kotlin-pre-stepb/` — pre-Step-B production
- VPS `.env` now also carries `OPENROUTER_API_KEY` (was missing on first deploy; pipeline correctly logged the WARN).

## Deploy artifact

`tools/deploy.sh` codified from the session's commands. Usage:
```
bash tools/deploy.sh           # build+test+installDist+scp+restart+verify
bash tools/deploy.sh rollback  # swap current ↔ -prev, restart
```

## Outstanding follow-ups

1. **48h cleanup** after 2026-05-09 12:30 UTC if no rollback needed:
   ```
   ssh root@46.247.109.91 "rm -rf /opt/jarvis/jarvis-kotlin-pre-stepb /opt/jarvis/jarvis-kotlin-prev"
   ```
2. **Drop wiki.md write from chat path** — currently dual-write keeps `wiki.md`
   fed for the `/wiki` HTML page only (chat recency lives in `conversations.jsonl`,
   embeddings live via `EmbeddingsPipeline`). Subsystems still read `MemoryWiki.recent()`
   as their context input — rewiring those to read from `Conversations.recent()` is the
   prerequisite. Bigger refactor than just deleting the wiki.md write.
3. **core_memory.md on VPS is empty** — by design (council 1778104395 HARD RULE: no
   identifiers in pinned context). User can `ssh root@46.247.109.91 "vim /opt/jarvis/data/core_memory.md"`
   to add preferences. Privacy scanner will WARN on identifier-shaped paste.
4. **Wiki.md.bak.1778090717 retention** — council 1778104395 said keep until
   `conversations.jsonl` has 7 live days. Today is day 0. Earliest delete: 2026-05-14.

## Quick verify on resume

```
ssh root@46.247.109.91 "systemctl is-active jarvis; wc -l /opt/jarvis/data/conversations.jsonl /opt/jarvis/data/activity.jsonl; ls -la /opt/jarvis/data/embeddings.json /opt/jarvis/data/core_memory.md 2>&1"
curl -s https://corgflix.duckdns.org/healthz   # expect: ok
git -C C:\Users\User\jarvis-kotlin log --oneline -10
```

## Honest-mirror flags

- THREE consecutive multi-hour jarvis sessions over 2026-05-06 → 07. Finals
  window priority lock has been rescinded for these. The 2026-05-04 council
  ("meta-planning sidetrack vector") + `feedback_ship_and_watch.md` apply
  doubly: pause to actually USE jarvis from phone/web before adding more.
- Memory hard-rule: do NOT auto-resume jarvis-kotlin in a fresh chat unless
  user explicitly opens with it. Default to study work.
