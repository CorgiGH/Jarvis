# Vision — AGI-shaped life-OS harness on phone + PC

> Source: 2026-05-07 user directive at end of Step B session, after 16-commit ChatTools + Letta-split shipping cycle.

## North star

A harness for an LLM that is **smarter than the user**, runs always-on across the user's phone and PC, monitors what the user does in real time, and has *smart logic that ranks importance of things* — so the system surfaces what matters at the moment it matters, instead of waiting to be asked.

This is not a chatbot. The chat surface is one of N input/output channels. The system's primary loop is:

```
observe (activity log + chat + active windows + calendar)
  → score (importance / urgency / surprise / load-bearing-ness)
    → act (surface notification, pin to core_memory, trigger reflection,
            invoke a subsystem, write a wiki note, do nothing)
```

The user's role is to *receive surfaced signals* and *correct misjudgements*; the system's role is to *not waste the user's attention*.

## Anchoring constraints

- **Single user.** No multi-tenancy, no auth scaling, no shared state.
- **Tiny VPS.** 7.8 GB RAM total, 4.2 GB consumed by MC server, jarvis JVM at -Xmx512m. Room for one always-on JVM + some local index, not much more.
- **Provider-agnostic.** Llm interface abstracts Anthropic / OpenRouter / Copilot CLI / Claude Max. Any new feature must work on at minimum the Copilot CLI subprocess (plain text in/out, no structured tool API).
- **Privacy.** Identifiers (matricol, finals dates, medical-adjacent) NEVER pinned to system prompt. Privacy scanner already enforces this on `core_memory.md`. Provider-side prompt logging is opaque; treat every chat turn as published.
- **Finals window 2026-05-21 to 2026-06-28.** User has scarce attention. The harness must save attention, not consume it. False alerts are the dominant failure mode to avoid.

## What's already built (2026-05-07 snapshot)

Already-shipped infrastructure that the AGI harness builds on:

| Layer | Component | Status |
|---|---|---|
| Storage | `conversations.jsonl` (chat), `activity.jsonl` (window samples), `archival/` (316 .md notes), `wiki.md` (notes + reflections + pins), `embeddings.json` (vector store), `core_memory.md` (pinned context) | Live on VPS |
| Concurrency | Process-wide `synchronized(LOCK)` mutex on every appender; `ChatTurnWriter` atomic dual-row write | Race-tested |
| Read tools | search / read / recall / wiki / activity / stats / time | Live |
| Write tools | remember (append-only to wiki) | Live |
| Meta tools | sub (invoke any subsystem) | Live |
| Subsystems | judgment / dots / teaching / timing / ctx-model / coder / search | Live |
| Privacy | matricol/email/phone regex scanner on core_memory at preamble time | WARN-loud |
| Provider | Copilot CLI subprocess via `JARVIS_LLM=copilot` | Active on VPS |
| Surface | HTMX web UI, Android APK over HTTPS w/ bearer auth, CLI | Live |
| Logger | PC-side activity logger, POSTs every 5 min, falls back to local file | Active |
| Reflection | PC cron at 08:00 local | Active (PC-only) |

## What's missing (the gap to the north star)

The current system is **reactive**: it waits for the user to chat, then reads context and replies. None of the core loop *observes-and-decides* unprompted. Specifically:

1. **No importance scoring.** Activity events and chat turns are equal weight in `recent(N)` retrieval. A 10-minute deep-work block looks identical to a 10-minute YouTube break.
2. **No proactive surface.** Nothing runs on a clock (other than the activity logger, which only ingests, never decides). Daily reflection writes to wiki but never pushes to the user.
3. **No notification path.** Even if the system *did* decide something matters, there's no channel to tell the user — no FCM, no APNS, no WebSocket fan-out to the phone APK.
4. **No cross-correlator.** Subsystems run on demand. There's no "every 30 min, look at activity + recent chat + sleep cues, decide if anything needs surfacing".
5. **No model of the user's current state.** `ctx-model` subsystem exists but is invoked manually. Nothing keeps a rolling cached "what is the user up to right now" inference.
6. **No write-back from chat to importance.** When the user says "this is important", the system doesn't pin or weight it.
7. **No learning.** The system doesn't get better at scoring importance over time. Every misjudgement is forgotten.

## Roadmap (concrete targets)

The roadmap below is ordered by leverage-per-LOC and dependency. Each item is sized for one ~2-hour session. Do them in order; they unlock each other.

### Phase 1 — observe with weights

- **1.1 Activity importance scoring** — Add `importance: Float?` to `ActivityEntry`. Heuristic v1: process-name allowlist (IDE / terminal / Slack / browser-with-doc-domain) + window-title hints + duration-since-last-different-window. Score 0..1. Tests on synthetic logs.
- **1.2 Conversation importance** — Add `importance: Float? = null` to `ConversationEntry` (v=2 schema bump? Or keep v=1, add nullable field + reader-tolerant). Score: user-emotional-content > task-mechanical > pleasantries. Heuristic-then-LLM. Council on schema bump.
- **1.3 Importance-weighted recent()** — `Conversations.recentByImportance(n)` mixes recency + importance. Don't replace `recent()` — different surface. Adds an importance-aware variant to `buildChatContext`.

### Phase 2 — decide on a clock

- **2.1 ProactiveLoop subsystem** — Scheduled task (Ktor-side coroutine, not OS cron) running every N minutes. Reads recent activity + chat. Invokes ctx-model + dots. Writes a `ProactiveSignal` row to a new `signals.jsonl` if importance > threshold. Council: every-N-minutes vs event-triggered.
- **2.2 Quiet hours / rate limit** — Don't surface during the user's typical sleep window (inferred from activity log). Don't surface more than 1× per 60 min. Don't repeat the same signal twice.
- **2.3 Surface decision** — given a signal, pick a channel: pin to `core_memory` (low-cost, passive), append to wiki under "proactive note" (medium), push notification (high-cost, attention-stealing). Council on cost-benefit calibration.

### Phase 3 — notification path

- **3.1 Android FCM or polling** — research agent: does the existing APK use FCM, polling, or neither? If neither, simplest path is APK polls `/api/signals` every M minutes when foregrounded, otherwise FCM. **Spawn research agent before designing.**
- **3.2 Notification ack / dismiss** — phone-side dismiss feeds back to backend. The system learns: "user dismissed 5 in a row → my threshold is too low → raise it."

### Phase 4 — cross-correlate

- **4.1 Sleep inference** — derive sleep windows from activity gaps + first-window-after-gap. Add to ctx-model input.
- **4.2 Focus-block detector** — sustained low-process-churn periods. Already partially in dots subsystem; promote to a named structure.
- **4.3 Stress proxy** — high churn + late hours + scrolling apps. Already in ctx-model; tighten.

### Phase 5 — learn

- **5.1 Importance feedback loop** — every "user dismissed", "user pinned", "user said this matters" / "this doesn't" is a labelled example. Stash in `feedback.jsonl`. Periodically retrain the importance heuristic (weights). Council before any model fine-tuning — most likely answer is "regex/heuristic re-weighting, not fine-tuning".
- **5.2 Self-reflection rewire** — current `ReflectMain` is PC cron, runs 08:00, writes to wiki. Move to VPS scheduler. Make it consume signals + feedback + activity + chat, not just activity. Output a daily summary that's also embedded for `recall`.

### Phase 6 — extend the input surface

- **6.1 Calendar pull** — Google Calendar OAuth. Spawn research agent on simplest path (probably user-token via `gcalcli` or direct OAuth). Read-only.
- **6.2 Email triage** — Gmail OAuth + scoring, NOT auto-reply. Read-only.
- **6.3 Health data?** — punt unless user explicitly asks.

## Anti-features (do NOT build without explicit user OK)

- Auto-reply to messages (Slack, email, SMS) — irreversible, blast radius high.
- Auto-spend money — buying anything via API.
- Auto-publish to public channels — blog, social.
- Auto-rewrite user files outside `/opt/jarvis/data/` and `~/.life-os/`.
- Cross-user features. Single-user only.
- Provider login flows that require interactive auth (`claude login`, OAuth in browser) — these need user.
- Schema migrations that aren't reversible from the bak file.

## Council triggers (must convene BEFORE proceeding)

Per `feedback_council_pattern.md` — convene 5-agent council on:
- Schema changes that affect existing rows (e.g. v=2 bump on conversations).
- Any new write tool (counterpart to `remember` — `forget`, `pin`, `redact` etc.).
- Any new outbound channel (FCM, email, SMS).
- Any change that touches more than 3 callsites.

Convene `claude-council-lite` skill. Save transcript to `.claude/council-cache/`. Honor verdict — FLAWED means fix before shipping; WRONG APPROACH means stop and re-plan.

## Agent spawn triggers (delegate research, not implementation)

Spawn an agent (`general-purpose` or `Explore`) when:
- A feature requires understanding an external system (FCM, OAuth flow, Khoj/Letta/Mem0 internals, Compose Android API surface).
- Code spans >5 files and you're unsure of all callsites.
- You'd otherwise grep blindly.

DO NOT spawn agents to write production code on your behalf — implementation stays in the main session for review-by-diff. Agents do research + return findings.

## Verify-before-completion

Per `superpowers:verification-before-completion`: every claim of "done" needs evidence. For this project specifically:
- Test added, fails before, passes after (TDD).
- Smoke against real VPS via `tools/deploy.sh` after each ship.
- One end-to-end behavior assertion through the actual surface that ships (e.g., `/api/chat` for chat changes, journalctl for service-level changes).
