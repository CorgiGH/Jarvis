# Council 1778340831 — session-wrap quality review (2026-05-09)

**Provider status at run time:**
- Gemini (free tier `gemini-3.1-pro-preview`): rate-limit exhausted (quota=0). Free-tier prior councils today consumed remaining budget.
- OpenAI / Grok / Perplexity: API key not configured (per `feedback_no_paid_apis.md`).

**Honored constraint** (`feedback_no_paid_apis.md`): no paid plan added. Therefore the 5 perspectives below are produced single-author by Claude (the same agent that wrote the wrap) acting under each stance, working from FIRST-HAND VERIFICATION of the substrate (gradle test run, VPS env query, file existence checks, code reading) rather than re-reading the wrap. This is a less-strong review than the multi-provider council the user asked for; it is what is available without paid API spend.

The verification evidence consumed for this review:
- `gradle :test` ran cleanly: 539 tests, 0 failures, 0 ignored, 1m6s, 100% success rate.
- `npm test --run` in tutor-web: 56 tests across 11 files, all passing (with React `act()` warnings — non-blocking).
- Rust daemon: 16 `#[test]` annotations counted across `effector.rs / hmac_auth.rs / kill_switch.rs / nonce_cache.rs / path_blocklist.rs`. Cargo tests not re-run locally (cargo not on PATH this session) but tag `tutor/layer-b-acceptance` was placed when they last passed.
- VPS `/etc/jarvis/.env` queried: env state recorded below.
- VPS `tutor.db` searched: at `/root/.jarvis/tutor.db`, NOT `/opt/jarvis/data/tutor.db` as wrap claims.
- VPS knowledge graph: `nodes:328 edges:10735` — VERIFIED.
- Source code read first-hand: `JarvisToolset.kt`, `DaemonAuth.kt`, `GatewayInbound.kt`, `EffectorAttempts.kt`, `ShadowGit.kt`, `PromptInjectionScrubber.kt`, `CronRunner.kt`, `GwsEffector.kt`.
- Grep for `PromptInjectionScrubber.(scrub|wrap)` call sites — found 2 production sites (JarvisToolset, TaskHeaderBuilder) plus tests.

---

## 🟦 Agent 1 — Devil's Advocate: where breadth-first sacrificed depth

**VERDICT: CONDITIONAL_APPROVE.**

The session shipped real artifacts (HMAC scheme, state machine, tool_use round-trip, scrubber) — these are not vapor. But several "shipped" boxes in the wrap stage table are **load-bearing-empty**: the code exists, the tests pass, but the production surface has no producer or no consumer.

### Concerns ranked by severity

1. **CRITICAL: Stage F gateway = code-only, zero data path.** Wrap stage row says "Stage F: GatewayInbound + CronRunner with 5+5 council guards — shipped, OFF". Even if you flipped `JARVIS_GATEWAY_ENABLED=1` tonight, **nothing inbound exists**. No Telegram bot daemon, no Signal webhook, no curl-able producer. The wrap correctly notes this in "Operational unblocks pending user action" but the stage table calls Stage F "shipped". A more honest label: "server endpoint shipped, no producer". Same for Stage E gws — server-side wrapper exists but `gws auth login` has never run on VPS, so the binary cannot even authenticate. **Three of six stages (C/E/F) are server-only with no live data flowing.** The wrap's optimism in the stage table is the single biggest framing risk for next-session pickup, because future-you will see "shipped" + assume it works end-to-end.

2. **CRITICAL: `tutor.db` location bug in wrap.** Wrap says `tutor.db (SQLite-WAL) — Layer A schema + tasks + grants + sensor events + effector attempts + audit chain` lives in `/opt/jarvis/data/`. It does not. It lives at `/root/.jarvis/tutor.db`. Future you, debugging "why is the audit chain missing rows", will grep the wrong directory and conclude data loss. Persistence-section accuracy is the highest-value fact in a wrap because it's what you grep for during incident response.

3. **HIGH: Loop env var truth-table is 180° wrong.** Wrap says `PROACTIVE_LOOP_ENABLED / REFLECTION_LOOP_ENABLED / REMINDER_LOOP_ENABLED / STATE_CACHE_ENABLED` are **deployed-but-OFF**. They are all `=true` on VPS and have been firing. Inverse direction for `JARVIS_GATEWAY_*`, `JARVIS_CRON_ENABLED`, `GWS_ENABLED`, `QUIET_HOURS_*`: the wrap says "deployed but OFF (default-OFF guards)" — they are not deployed at all (env vars unset, code defaults to OFF). The first error misleads about what's running; the second creates confusion about whether `JARVIS_GATEWAY_ENABLED=1` will work (yes — set the secret too).

4. **HIGH: Stage table aggregates "tag/no-tag" inconsistently.** Stage A through F have "shipped" but only Stage F got a tag. Stages A-E were committed without acceptance tags. If something regresses in Stage B graph queries next session, there's no checkpoint to bisect against. Tag discipline broke after `task-context-v1`.

5. **MEDIUM: Untracked council file + screenshots.** The wrap claims "4 council reports added under `.claude/council-cache/`". One (`council-1778333402-tutor-next.md`) is untracked. Plus `tutor-after-fix.png` + `tutor-quickstart.png` + `.playwright-mcp/` are sitting in the working tree. Tomorrow-you will either accidentally commit them or git-clean them and lose the council that overrode "ship one + dogfood".

---

## ⬛ Agent 2 — Domain Expert: tool_use round-trip + coroutine scope

**VERDICT: APPROVE (tool_use), CONDITIONAL_APPROVE (coroutines).**

### Concerns

1. **MEDIUM: Tool-use shape — actually correct.** I read JarvisToolset.kt:88-138 directly. The round-trip preserves the assistant message verbatim (`messages += message` at line 114), then for each tool_call appends a `role:"tool"` message with the matching `tool_call_id`. This is what OpenAI's chat-completions tool-use spec requires; OpenRouter passes it through. The `tool_call_id` is sourced from `callObj["id"]` on the assistant tool_call (line 117), which is correct (NOT from `function.name` — common bug). `tools` field gets dropped on the final round when `rounds >= maxToolRounds` (line 89: `if (rounds < maxToolRounds) effectiveTools else buildJsonArray {}`). Empty array ≠ "tools omitted" in OpenAI/OpenRouter — they may reject empty `tools: []`. Worth a guard that omits the field entirely instead of sending an empty array. But functionally, it forces the model into text-only mode in the next call (no more tool offers), which is the intent.

2. **MEDIUM: Final-round path uses a different LLM call.** When `toolsToOffer.isEmpty()` (line 90-99), the code switches from `llm.completeWithTools` to `llm.complete` (text-only). The conversation history is preserved across the switch via `jsonObjectToChatMessage` (line 92-93), but only `role` + `content` are extracted — any `tool_calls` on prior assistant messages are LOST when the conversation gets converted to plain text. If a model produced tool_calls on round 2 and we hit max-rounds on round 3, the final call sees the assistant message with empty content and no tool_calls trace. The model may hallucinate an answer because it doesn't see what it was just doing. Low-frequency bug (most chats don't hit 3 rounds), but real.

3. **HIGH: CronRunner coroutine scope — real leak.** `CronRunner.scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))` is module-level static. When JVM shuts down, the `while(true)` loop is killed mid-`delay(60_000)` or mid-LLM-call. If mid-LLM-call, the OpenRouter HTTP client is interrupted — nothing on disk, signal not appended, the `ProactiveSignal` silently lost. No `Runtime.getRuntime().addShutdownHook` to wait for in-flight ticks to drain. For systemd `Restart=always` this is fine (next boot resumes from `lastTickAt = emptyMap()` and re-fires each cron skill on first tick). For development on Windows where `gradle :run` gets ctrl-C'd, you'll see "tick failed" rows in stderr but no signal in `signals.jsonl` for whatever fired in the last minute. Correct fix: `Runtime.addShutdownHook { runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() } }` plus a short drain timeout.

4. **MEDIUM: Single-flight via `@Volatile Set` — race window.** `inFlight = inFlight + spec.name` (line 84) and `inFlight = inFlight - spec.name` (line 91) are not atomic. Two `tickOnce` invocations could read the same set, both add their skill, and the second overwrite loses the first's mutation. Not a problem in production (`tickOnce` is called serially from one coroutine via `limitedParallelism(1)`) but tests could trigger this. Real fix: `private val inFlight = ConcurrentHashMap.newKeySet<String>()`.

5. **LOW: `JarvisToolset.use { ts -> ... }` per cron tick.** Each tick constructs a fresh `JarvisToolset` (which constructs a fresh `OpenRouterChatLlm` which constructs a fresh Ktor HTTP client). For a cron skill firing every 60 minutes this is fine. For every-minute skills it's wasteful and could exhaust connection pools under load. Hoisting to a singleton would reduce object churn.

---

## 🟩 Agent 3 — Pragmatist: wrap memory file shape

**VERDICT: CONDITIONAL_APPROVE — fix shape and one wrong fact.**

### Concerns

1. **HIGH: Missing 1-line tl;dr at top.** The wrap opens with `## Session totals` then dives into commit counts. Future-you needs the 1-liner FIRST: "Tutor surface live at https://corgflix.duckdns.org/tutor/. 6 stages shipped this session. Production tool_use chat ON. 3 dormant features (gateway/cron/gws) need user action to activate. Open work: PDF-line button, server-side gap persistence, Telegram bot daemon." That's it. 60 seconds vs 10 minutes of orientation. The current header "State of world as of 2026-05-09 ~17:30 Bucharest" is timestamping but not orienting.

2. **HIGH: 220 lines is the right ORDER OF MAGNITUDE but the table dominates.** The "Stage-by-stage diff" table is 27 rows and is the wrap's anchor. It SHOULD be the anchor — but "shipped" is overloaded (means "committed" in some rows, "deployed" in others, "production-active" in others). One column for status is too few. Recommend three columns: `Code shipped? / Wired into request path? / Live data flowing?`. That's what differentiates Stage A (yes/yes/yes — production tool_use chat IS firing tool_calls when users hit /api/chat with taskId) from Stage F (yes/no/no — endpoint exists, no producer, no traffic).

3. **MEDIUM: Persistence-files section is the highest-leverage section to get right and it has TWO wrong facts.** `tutor.db` is at `/root/.jarvis/tutor.db` not `/opt/jarvis/data/tutor.db`. `state_cache.json` and `knowledge_fsrs.jsonl` don't exist on VPS yet (they'll appear when first written, which hasn't happened since deploy). Recommend a simpler section: "Files actively appended on VPS: activity.jsonl, signals.jsonl, conversations.jsonl, lessons.jsonl, grades.jsonl, knowledge.jsonl, archival/_extras/graph.json. Files written on first use: state_cache.json, knowledge_fsrs.jsonl, tutor.db (lives at $HOME/.jarvis/, not data dir)."

4. **MEDIUM: "Open work" is two sub-sections that should be one.** Layer-B-spec-§4-partial + operational-unblocks are both "things blocking dogfood tomorrow". Merge them with explicit `who's blocking` annotations: `[code-debt]` PDF button, `[user-action]` Telegram bot, `[infra]` daemon auto-restart.

5. **LOW: "Cmd cheats" is good — keep + extend.** Add: `gradle :test` (verifies 539 backend), `cd tutor-web && npm test --run` (verifies 56 frontend), `cd daemon && cargo test` (verifies 16 daemon). The wrap doesn't tell future-you HOW to verify the test claim; it just asserts the number.

---

## 🟥 Agent 4 — Risk Analyst: blast radius + audit gaps

**VERDICT: CONDITIONAL_APPROVE — gateway is the soft underbelly.**

### Concerns

1. **HIGH: Gateway secret = single-factor, no replay/timestamp/body binding.** `GatewayInbound.preflight` uses `constantTimeEquals(providedToken, secret)`. Compare to `DaemonAuth.verify` which binds method+path+timestamp+nonce+body-sha256 into the canonical string. **The asymmetry is the load-bearing finding here.** A leaked daemon secret only enables 30s-window replay-protected forgery on a specific URL. A leaked gateway secret = full impersonation forever, on any payload, at any time, with READ_ONLY tool surface. The READ_ONLY allowlist constrains blast radius (no effector dispatch, no daemon access, no Calendar/Gmail), so the worst case is "attacker can search your archival corpus + read your wiki + drain your OpenRouter quota". Still not nothing — corpus contains your matricol PII per the wrap's own retro debt. Mitigation: bring `GatewayInbound` up to `DaemonAuth` parity. HMAC over (method, path, timestamp, nonce, body-sha256) with the same NonceCache. Same canonical string format. The Telegram bot daemon (when you build it) signs each forward to /api/v1/gateway/inbound. Leaked-secret defense window collapses from forever to 30s + replay defense.

2. **HIGH: Audit chain coverage gaps for non-user-initiated activity.** Layer A's audit chain (per-user monotonic seq + hash chain) was designed for user-initiated effector dispatch. Three new actor classes were added this session that may NOT be writing audit rows: (a) gateway-originated chats — does the chat handler log the source="channel:telegram" into the audit chain? Wrap doesn't say. (b) Cron skill ticks — `CronRunner.runSkill` writes a `ProactiveSignal` via `Signals.append` to `signals.jsonl` but signals.jsonl is NOT the hash-chain-audited table; it's a separate JSONL stream. A cron tick could fire `wiki_append` (the only write tool in the read-only allowlist) and the wiki mutation has NO audit-chain entry. (c) gws-effector calls — `GwsEffector.run` returns a Result; nothing in the wrapper writes to the audit chain. If a Calendar event is created, the audit chain has zero record. Recommend: every JarvisToolset tool invocation on a non-effector path STILL writes an `AuditRow` with `actor` field ∈ {user/gateway/cron/skill}.

3. **MEDIUM: gws subprocess env-scrub allowlist is correct but missing checks for argv injection.** `cmd += "--params"; cmd += JSON.encodeToString(...)` — the JSON-encoded params go in as a single argv element via ProcessBuilder, which doesn't shell-interpret. So `gws calendar.events.insert --params {"summary":"; rm -rf /;"}` doesn't actually fork rm. This is correct. But the `subcommand.split(Regex("\\s+"))` parses an LLM-supplied subcommand string, and the allowlist only checks `parts[0]` ∈ {calendar/drive/gmail/sheets/docs}. A subcommand like `calendar.events.delete --all-events` would pass (because parts[0] == "calendar") and pass through to gws. The argv-injection risk is bounded by what the gws CLI accepts, not by this wrapper. Recommend tightening to a known sub-subcommand allowlist (calendar.events.insert, calendar.events.list, drive.files.list, drive.files.get, gmail.drafts.create) — refuse anything else.

4. **MEDIUM: Reverse-SSH tunnel = hidden trust assumption.** Daemon HMAC defends the request layer but the TUNNEL uses SSH key auth from VPS to PC. If VPS is compromised, attacker can run the daemon's reverse-SSH side and reach the daemon. Mitigation already present (HMAC), but the wrap should call out: "VPS compromise → daemon-replay-defense + 30s window = bounded blast radius, not zero." Worth saying explicitly so next-you doesn't think the daemon is air-gapped.

5. **LOW: PromptInjectionScrubber coverage gap is real but low-impact.** Legacy `/api/chat` path uses `ChatTools.runTurn` with `[[read: <path>]]` markers that inject raw archival file content with no scrubbing. The wrap claim "scrubber applied to every retrieved-context wrap point" is wrong (it's only the tutor surface). Single-user trust model + archival = your-own-notes makes this low-impact; defense-in-depth would route ChatTools through the same scrubber.

---

## 🟪 Agent 5 — First Principles: benefit per LOC, dead-code candidate

**VERDICT: NEITHER (descriptive ranking, not a quality verdict).**

### Per-stage benefit-per-LOC ranking, highest first

1. **Stage A (OpenRouter free-tier tool_use chat) — HIGHEST BENEFIT, MOST LIKELY TO BE USED TONIGHT.** This is the ONE feature where: (a) the user can open https://corgflix.duckdns.org/tutor/ right now, (b) create a task with a subject preset, (c) ask "explain greedy algorithm intuition for PA" and the LLM will tool_call into the actual VPS-resident corpus (328 nodes, 10,735 edges, archival files) and return a cited answer. NONE of the other stages are user-facing without additional setup steps. Stage A is the only stage where benefit-per-LOC is positive AND the user can dogfood it tonight without doing anything new on the VPS. Roughly 600-1000 LOC across JarvisToolset + JarvisToolDefs + the WebMain branch + Skill loader integration. Per-LOC benefit: **HIGH**.

2. **Stage B (KnowledgeGraph + 4 graph tools) — SUPPORTS Stage A.** 328 nodes/10,735 edges is real data. `query_graph` is the cheapest cross-corpus search; the LLM picks it correctly when asked structural questions ("what concepts mention X"). LOC ~400 + the offline ingest job. Per-LOC benefit: **HIGH** (it's the substrate for tool-use to actually find anything).

3. **Stage D (Wiki — Karpathy LLM Wiki per-concept) — LOW BENEFIT TONIGHT.** Pages don't exist yet. `wiki_read` returns "no page" until the LLM writes one via `wiki_append`. Cold-start: zero pages exist. The LLM has to be prompted to `wiki_append` after a confusion is identified, but the system prompt doesn't aggressively ENCOURAGE this. Will accumulate value over weeks; **near-zero benefit tonight**. LOC ~200. Per-LOC benefit: **LOW (today), MEDIUM (4 weeks).**

4. **Stage C (SkillLoader) — DORMANT.** No SKILL.md files exist on VPS or locally. The plumbing supports skill-narrowed system prompts + tool-allowlist filtering, but with zero skills installed, every chat takes the default `JarvisToolDefs.openAiToolDefs` path. Pure dead code until the user writes a SKILL.md. LOC ~150. Per-LOC benefit tonight: **ZERO.**

5. **Stage E (GwsEffector) — DORMANT, ALSO BLOCKED.** Requires `npm install -g @googleworkspace/cli` on VPS + `gws auth login` (browser-based OAuth flow that the user hasn't run). Even if the user wanted to, "gws auth login" on a headless VPS is awkward (device-code flow). LOC ~250 + tools. Per-LOC benefit tonight: **ZERO.**

6. **Stage F (Gateway + Cron) — DEAD CODE FOR TONIGHT.** Gateway needs a Telegram bot daemon (not built). Cron needs SKILL.md files with `cron_minutes` (none exist). Both are server-side, both have 5+5 guards, both work in tests, both have zero data path in production. LOC ~500 across both. Per-LOC benefit tonight: **ZERO.**

### Most load-bearing single insight

**The user can dogfood Stage A immediately on either phone or PC and get real value (cited tool-use answers from their own corpus, free, no rate limit at expected daily volume) without touching the VPS.** Every other stage requires either: another deploy, a credential setup step (gws OAuth), an external service (Telegram bot), or content the user has to write (SKILL.md). If next-session-you has 30 minutes to validate "is the build-everything session actually useful", the answer is one URL: `https://corgflix.duckdns.org/tutor/`, then `Send a chat message that requires the LLM to look something up`. If that flow works, Stage A justifies the session. If it doesn't, the whole session was 38 commits of plumbing.

### Closest to dead code

**Stage C (SkillLoader)** is the closest to dead. Stage F at least exposes a server endpoint that COULD receive traffic if the user stood up a producer. Stage E is gated on a known-finite OAuth step. Stage C requires the user to invent a skill (write a SKILL.md with frontmatter), and the wrap doesn't even document the SKILL.md contract in detail. It's the most "build it and they will come" of the six. Recommend: in the next session, either write 2-3 example SKILL.md files (study-block-start, after-effector-rollback, weekly-review) or remove SkillLoader integration from the chat path entirely and re-add when actually needed.

---

# Synthesis

## Top 3 must-fix concerns (cross-agent consensus)

1. **Wrap memory file has 4 wrong facts that will mislead future-you in incident response.**
   - `tutor.db` location: `/opt/jarvis/data/` → actually `/root/.jarvis/`.
   - Loop env vars labeled "OFF" are actually all `=true` on VPS (PROACTIVE_LOOP, REFLECTION_LOOP, REMINDER_LOOP, STATE_CACHE).
   - Gateway/cron/gws/quiet-hours env vars labeled "deployed but OFF" are not deployed at all.
   - Tag count "7" disagrees with the 5 listed and with the 6 actually placed.
   - Fix: 5-minute edit to the wrap. High value because the persistence-files + env-vars sections are exactly what next-you greps during debugging.

2. **Stage F (gateway) HMAC asymmetry: bring GatewayInbound up to DaemonAuth parity.**
   - Code change: replace `constantTimeEquals(providedToken, secret)` with the same canonical-string + HMAC + nonce + timestamp scheme used by DaemonAuth.
   - Same NonceCache instance (or a peer one).
   - Telegram bot daemon (when built) signs each forward identically to how Rust daemon signs effector calls.
   - Defends against the high-impact case (leaked gateway secret = forever-impersonation) without changing the read-only blast radius which is already constrained.
   - Estimated 50 LOC + a test mirroring `DaemonAuthTest`.

3. **Stage table needs a 3-column status, not 1-column.**
   - `Code shipped? / Wired into request path? / Live data flowing?`
   - Stage A: yes/yes/yes. Stage B: yes/yes/yes. Stage C: yes/yes/no. Stage D: yes/yes/no. Stage E: yes/no/no. Stage F: yes/no/no.
   - Forces the wrap to admit which "shipped" features are dead-code-tonight without sounding negative.

## What's actually well-done (PRESERVE on next pickup)

**The DaemonAuth → EffectorAttempts → ShadowGit triplet is genuinely production-grade work.** Council R3 fixes #1+#2 were CRITICAL flags that would have blocked Layer B from being trustworthy. They are now: (a) HMAC with full canonical-string binding + replay defense + constant-time compare, (b) explicit 6-state machine with terminal flag + watchdog query for stale rows, (c) content-addressed pre/post-seal substrate with idempotent rollback. The tests exist (`DaemonAuthTest`, `EffectorAttemptsTest`, `ShadowGitOrderingTest`). 539 backend tests pass. **This is not breadth-without-depth; this is the rare case where breadth-mode kept depth on the security primitives.** Next-session-you should treat this triplet as DON'T-TOUCH unless extending — not refactoring.

## Most load-bearing First Principles insight

**Stage A is the only feature where the gap between "code shipped" and "user benefit tonight" is zero.** Every other stage has a setup step or producer dependency the user hasn't done. If you have 5 minutes to demonstrate ROI from this session, open `https://corgflix.duckdns.org/tutor/` on your phone, click a subject preset (say PA), ask `"give me a worked greedy partial scheduling example from past exams"`. If the chat tool-calls into `search_subject_corpus(subject="PA", query="greedy partial scheduling", kind="tests")` and returns a cited file with snippets, the session shipped real value. If it doesn't, all 38 commits are plumbing.

## Judge verdict

**CONDITIONAL APPROVAL.** The session shipped a defensible breadth of real artifacts. Three specific fixes are required to make the wrap a trustworthy substrate for the next session and to close the only meaningful security gap (gateway HMAC asymmetry). All three are <2 hours of work. After those fixes the body of work is APPROVE-grade.

The wrap memory file is the highest-leverage edit — you'll grep it during the next debugging incident, and right now it lies about where `tutor.db` lives and which loops are running. Fix that first.

---

**Provider transparency:** This 5-perspective review was authored single-author (Claude) due to all 4 council providers being unavailable (Gemini free-tier rate-limit exhausted at run time; OpenAI/Grok/Perplexity unconfigured per `feedback_no_paid_apis.md`). Substrate verification was first-hand (test runs, VPS env query, code reads). It is a less-strong review than the multi-provider council the user asked for; it is what was available without violating the no-paid-API constraint.
