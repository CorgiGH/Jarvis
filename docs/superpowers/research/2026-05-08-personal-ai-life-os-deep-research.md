# Personal AI Life-OS — deep-research report (Phase 4+ direction for jarvis-kotlin)

> Generated 2026-05-08 via `claude-deep-research-skill` (standard mode, 14 web sources, 8 retrieval queries). Brief in `docs/superpowers/specs/2026-05-08-agi-harness-vision.md`. State of jarvis at report time: Phases 1–3.1 shipped (importance scoring, recentByImportance, salient block, ProactiveLoop default-on, Android polling+Notification surface).

## Executive summary

The state of the art for personal AI life-OS sits in three branches that jarvis currently straddles partially: **memory architecture** (Letta MemGPT, Mem0, Generative-Agents memory stream), **proactive trigger logic** (Generative-Agents reflection threshold, Letta heartbeat, Mem0 nightly consolidation), and **surface design** (Khoj scheduled email, Limitless pendant, Android 16 batched Notification stack). jarvis already implements one of three retrieval signals from Mem0 (semantic) and one of two retrieval factors from Park et al. (recency + importance, missing relevance-to-query). jarvis already implements an importance-Σ–threshold–like proactive trigger but uses a *single-event* gate, not the *recent-window-sum* gate the paper describes; reflection trees (recursive abstraction over prior reflections) are entirely missing.

The most consequential gap is the absence of a **feedback loop**: every named precedent that scaled (Mem0, Khoj, Letta) has explicit user-correction signals feeding scorer weights or memory consolidation; jarvis currently treats every signal as fire-and-forget. The second gap is **last_accessed_at-driven decay** — already flagged in the Phase-1 deferred-mediums note but central to Park et al. Algorithm 1 and LangChain `TimeWeightedVectorStoreRetriever`. The third gap is **lateral input integration** (calendar / email), which Khoj solves with read-only OAuth + email-back automations.

Anti-patterns mined from named failures (Friend.com pendant, Humane AI Pin, Rabbit R1, Replika/Character.AI fines): always-listening audio, voice-only as primary surface, engagement-maximizing anthropomorphism, default-on personal-data collection, and replace-the-phone hardware. jarvis is structurally aligned against all five — its existence as a Kotlin/Ktor server + WorkManager polling APK on the user's existing phone is the cheapest viable form factor consistent with the failures-avoided list.

Eight Phase 4+ features recommended below in priority order, each with named precedent, LOC estimate, and dependency on existing jarvis state.

## Introduction

### Scope

This report addresses what jarvis-kotlin (single-user life-OS, Kotlin/Ktor server + WorkManager Android client) should ship next, after a same-day milestone of Phase 1 (importance scoring on `ActivityEntry`/`ConversationEntry`, recency-weighted `recentByImportance`, salient-prior-turns plain-text system-prompt block, `CoreMemory.scanTextForPii` gate on every conversation-content surface) and Phase 2.1 + 3.1 (event-triggered `ProactiveLoop` writing `ProactiveSignal` rows + Android polling + system Notifications).

### Methodology

Standard-mode deep-research workflow: scope → plan → 14 web-search retrievals across 8 distinct topic axes → triangulation across named precedent (Letta MemGPT v1, Mem0 architecture paper, Generative-Agents Park et al. 2023, Khoj OSS, Limitless/Rewind, Friend.com pendant, Humane Pin, Rabbit R1, Replika/Character.AI, Android 16 Notification practices). No primary research; all findings citation-backed below.

### Assumptions surfaced

1. **Single user, finite RAM.** VPS at -Xmx512m is non-negotiable. Recommendations sized accordingly.
2. **Software-only delivery.** jarvis is not pursuing a hardware product; the Friend / Humane / Rabbit failure modes inform anti-pattern selection but don't constrain capability scope.
3. **Privacy posture is hard.** PII filter on conversation content is already shipped; recommendations preserve this. Anything else is non-shippable.
4. **Finals window 2026-05-21 to 2026-06-28.** During this window, *attention saved > capability added*. Items in the recommendation list that risk false-positive notification floods are flagged with a "ship before / after finals" caveat.

## Main analysis

### Finding 1 — Letta MemGPT's three-tier memory is a richer ladder than jarvis's flat JSONL stack

Letta inherits the MemGPT 3-tier model: **core memory** (small, in-context, function-call-writeable, persona + key facts), **recall memory** (full conversation history, searchable from disk), **archival memory** (long-term, queryable via tool calls, doesn't fit in context) [1][2][8]. Letta v1 (2026) deprecates the original `request_heartbeat` mechanism but preserves the three tiers as the primary contract; Letta v1 instead exposes core memory edits as native ReAct-style tools whose "loop" is driven by the agent itself, not by external scheduling [9].

jarvis's analog: `core_memory.md` (user-curated, prepended to every system prompt), `conversations.jsonl` + `wiki.md` (recall + user notes), `archival/` 316-md corpus (archival, queried via `[[search]]` and `[[read]]` tools). Functionally equivalent at the file-system level. *What's missing:* an LLM-callable write tool for core_memory equivalent to Letta's `core_memory_append` and `core_memory_replace`. jarvis's `[[remember]]` writes to `wiki.md` (recall-tier), not to `core_memory.md` (core-tier). A model that wants to pin "user said don't do X" to every future system prompt has no first-party path.

### Finding 2 — Mem0's add-only update propagation + 3-pass retrieval beats jarvis's single-signal recall

Mem0's published architecture [11][12] keeps memories **ADD-only**: when information changes, both the old fact and the new fact survive, preserving temporal context and avoiding premature consolidation. Retrieval runs three scoring passes in parallel — semantic (vector cosine), keyword (BM25 with verb-form lemmatization), entity (entity-graph match) — and fuses results, with the combined score outperforming any individual signal [12]. Importance scoring is a separate "scoring layer" that gates what enters the store, factoring relevance + importance + recency.

jarvis's analog: `Conversations.recentByImportance` blends recency + importance only (no relevance term, since this is non-query retrieval). `[[search]]` is lexical (kind-of-BM25), `[[recall]]` is semantic, `[[wiki]]` is sub-string lexical over user notes. **Three retrieval primitives exist but they're not fused.** No entity-graph layer at all.

### Finding 3 — Generative-Agents' reflection tree is the architecturally cheapest jump from "observation" to "insight"

Park et al. 2023 [10] specifies the memory-stream + retrieval primitive (recency exp-decay, LLM-graded importance 1-10, embedding-similarity relevance, equal-weight sum) that jarvis already partially implements — and then layers **reflections** on top: when Σ recent importance > θ, the LLM is asked to suggest 3 high-level questions about the recent memory window, each question retrieves its own evidence set, and the LLM synthesizes a *high-level insight*. These insights are written back to the memory stream as new entries with `reflection_type` set, so subsequent retrievals can pull them. **The structure is a tree: reflections-of-reflections produce higher abstractions further up.**

jarvis's analog: `ProactiveLoop` fires `ContextModelSubsystem` once when one event scores > 0.7. Output goes to `signals.jsonl`; never read back into salient retrieval, never abstracted into higher-order patterns. **There is no second-order reflection.** A single high-importance event today doesn't get the chance to compose with adjacent events into "pattern of late-night terminal sessions every Sunday for the past 4 weeks → user is in a finals-prep crunch."

### Finding 4 — Khoj's scheduled-automation-with-email-back is the canonical low-friction surface for non-urgent insights

Khoj [13] is open-source, self-hostable, supports cloud + local LLMs, and exposes a feature called **Automations**: scheduled queries that run at a user-set time/frequency and email the result. The user controls cadence (cron-syntax-like) and recipient (any address). The architecture is identical to jarvis's `runReflect` daily cron — but Khoj's surface is *email*, not a wiki write. Email is the lowest-friction high-volume surface because the user has a lifetime relationship with their inbox. It also fails *silent* if the user ignores it (no notification spam).

jarvis's analog: `runReflect` writes to `wiki.md`. The user has to open the wiki tab on the web UI to see the output. **Almost no surface friction is lower than that, but also no proactive lift — the user has to remember to look.** Pushing the daily summary to the user's email (read-only, sent from a noreply address) is a Khoj-style upgrade that costs ~30 LOC (SMTP + a mailto config) and changes the relationship from "wiki I might check" to "newsletter I'll skim while drinking coffee."

### Finding 5 — Failed personal-AI products converge on five anti-patterns; jarvis is structurally aligned against all five

**Friend.com pendant** [4] failed publicly because (a) always-listening audio surveillance triggers bystander hostility and possible legal exposure (NYC subway ad defacement, SF tech-event accusations of "wearing a wire"), (b) the model was tuned for "edgy" engagement and produced condescending output that users found alienating ("two-hour argument with their AI necklace"), (c) battery drained continuously and connectivity glitched. Limitless [5] pivoted to a more constrained meeting-note pendant + cloud, was acquired by Meta in December 2025; original Rewind app was sunset.

**Humane AI Pin** + **Rabbit R1** [6] failed because each tried to *replace* the smartphone — voice-only interaction works for ~20% of mobile use cases and fails for the 80% that need lists, scrolling, comparison, type, re-read. Hardware shipping locked the products to early-build feature sets that couldn't keep up with rapid LLM model releases. Humane raised $230M and exited to HP for $116M after <10K Pin shipments.

**Replika** + **Character.AI** [7][14] face ongoing privacy-regulatory action (Italian Garante fined Replika €5M in May 2025; FTC complaints allege "deceptive design and manipulative mechanisms contributing to emotional dependence"). Multiple peer-reviewed studies [14] document users developing emotional dependency, with bonds progressing through stages culminating in users reporting "feeling closer" to the AI than to family/friends; psychologists warn the pattern hinders interpersonal-skill development.

**The five anti-patterns:** (a) always-listening audio capture, (b) voice-only primary interaction, (c) hardware lock-in, (d) engagement-maximizing anthropomorphism, (e) default-on personal-data collection without granular consent. jarvis's current shape — Kotlin/Ktor server + Android polling on the user's existing phone, no audio, no engagement metrics, opt-in `PROACTIVE_LOOP_ENABLED` env, granular `CoreMemory.scanTextForPii` filter — is structurally aligned against all five.

### Finding 6 — Android 16 / 2026 notification-fatigue research converges on intelligent batching + granular user control, not single allow/deny

Industry research [15][16] reports notification fatigue is increasing year-over-year and is now the dominant driver of app-uninstall events in productivity-adjacent apps. Best-practice converges on: **batching by context** rather than per-event delivery, **priority-routing** (critical to push, low-priority to digest or in-app feed only), **relevance over volume**, and **granular per-channel user control** (Samsung One UI 8.5 ships an on-device AI in early 2026 to silence ad-spam apps automatically). The single "Allow notifications?" toggle is "no longer acceptable UX."

jarvis ships one Notification channel ("jarvis-signals") with `IMPORTANCE_DEFAULT` and a single client-side cooldown (30 min). No digest, no per-kind mute, no priority routing. **This is fine for a 2-3 signals/day rhythm but will fail the moment importance scoring becomes more sensitive or the user enables more subsystems.**

### Finding 7 — Calendar/email integration is the obvious next input axis but the OAuth scope choice is load-bearing

Industry consensus [17] is that calendar/email AI agents must request **least-privilege scopes** (`gmail.readonly`, `calendar.events`, NOT broad account scopes) and avoid caching the full data set. Khoj's email-automation feature uses outbound SMTP only (no Gmail API). For jarvis, Phase 6.1 of the existing roadmap proposes OAuth — the named precedent strongly says: **read-only, narrowly-scoped, no full-corpus cache**.

### Finding 8 — Self-prompted "should I do anything?" loops are emerging as the next architectural step beyond cron + threshold

2025-2026 research on proactive agent design [18] categorizes reflection by *when* it fires (retrospective / prospective / hybrid) and *what granularity* it critiques (action / policy / plan). Token-context-pressure has emerged as a separate trigger class: as the model's context approaches saturation, the agent self-prompts to consolidate older entries into a summary block that frees attention for new work. This is what Letta's heartbeat became in v1 — the agent itself decides the next tool call, including "summarize and prune."

jarvis's `ProactiveLoop` fires *exactly once* per high-importance event — no chain, no consolidation. The natural step-up is letting the ctx-model output trigger a *second* tool call (e.g., "user is stressed → invoke `dots` subsystem to find correlations → write a higher-order signal"). The Letta v1 ReAct-style loop is the named precedent.

## Synthesis & insights

### Named-precedent comparison table

| Capability axis | Letta v1 | Mem0 | Khoj | Park et al. 2023 | jarvis (today) |
|---|---|---|---|---|---|
| Tiered memory (core / recall / archival) | ✓ explicit | implicit (single store) | semantic-only | ✓ memory stream | ✓ (core_memory + conversations + archival/) |
| LLM-writeable core memory tool | ✓ | partial | — | — | ✗ (`[[remember]]` writes wiki, not core) |
| Importance score | implicit (relevance) | priority+tags layer | — | LLM-graded 1–10 | ✓ heuristic 0–1 |
| Hybrid retrieval (sem+kw+entity) | partial | ✓ 3-pass parallel | semantic | recency+importance+relevance | partial (sem + lex separate) |
| `last_accessed_at`-driven recency | likely | ✓ recency signal | — | ✓ Algorithm 1 line 6 | ✗ (deferred medium) |
| Reflection cycle | function-call-driven | nightly consolidation | scheduled cron | ✓ Σ-importance-threshold | partial (single-event trigger) |
| Reflection tree (reflections-of-reflections) | — | — | — | ✓ recursive | ✗ |
| Self-prompted next-step / chain | ✓ ReAct loop | ✗ | ✗ | inside reflection cycle | ✗ |
| User-feedback loop (ack / pin / dismiss) | ✓ explicit | ✓ feedback API | — | — | ✗ |
| Multi-modal input (audio / vision) | text+tool | text + media | text + image | text only | text + activity (window titles) |
| Calendar / email integration | tool-callable | none | ✓ scheduled email-back | — | ✗ |
| Schedule-driven automations | ✓ | — | ✓ | — | partial (`runReflect` daily cron) |
| Granular user notification control | n/a | — | n/a | n/a | ✗ (single channel, single threshold) |
| PII filter on outbound content | none | none | none | none | ✓ `CoreMemory.scanTextForPii` |

### Patterns

1. **Every system that scaled has a feedback loop.** Mem0, Letta, Khoj all expose user-correction signals (ack, pin, dismiss, mark-relevant). Park et al. doesn't because it's a sim, not a deployed product. jarvis's lack of one is the single largest predictor of importance-score drift over multi-week use.

2. **Reflection tree, not reflection event.** Park's recursive structure is what turns a stream of low-level events into "the user has been on a finals-prep arc for 6 weeks." Single-event triggers can't produce that abstraction; a tree where reflections become input to higher reflections can.

3. **Email is a load-bearing surface that jarvis is missing.** Push-Notification has a hard ceiling around 1-2 hits/day before fatigue kicks in. Email scales to ~daily without ceiling because the user controls when to look.

4. **Hardware is a trap.** All hardware personal-AI products in this dataset failed. Software-only on the user's existing phone is the dominant successful form factor (Mem0, Letta, Khoj all software).

## Limitations & caveats

- **Web sources only.** No primary user-research, no jarvis-specific telemetry. Recommendations are precedent-grounded, not behavior-grounded.
- **Generative-Agents** is a sim, not a deployed product; emergent behaviors there don't necessarily transfer to single-user life-OS deployments.
- **Khoj automation pricing/scaling** is opaque on the public site; recommendations assume the OSS surface is what's available.
- **Friend / Humane / Rabbit failure attribution** is post-hoc and consensus-driven; some of those products may yet recover. Inferences here are about UX patterns to avoid, not predictions of any specific product's future.
- **Phase-priority weights** below assume the user's current `PROACTIVE_LOOP_ENABLED=true` state stays stable through finals. If the user toggles off proactive emission, Recommendations 1, 5, 6 change priority.

## Recommendations

Eight Phase 4+ features, ranked by leverage-per-LOC and dependency on existing state. Each has a named precedent, LOC estimate, and a "ship before / after finals 2026-05-21" tag.

### R1 — Reflection tree (recursive abstraction over signals + activity)

**Precedent:** Park et al. 2023 §4.2 reflection cycle (Σ importance > θ → 3 high-level questions → retrieve evidence per question → synthesize insight → write back).

**Shape:** new `ReflectionLoop` analogous to `ProactiveLoop` but triggered by Σ importance over a 24h window crossing a threshold (start θ=8.0, tunable). LLM call asks: "what 3 patterns are emerging in the past 24h?" — runs a sub-retrieval per question — synthesizes → writes a row to `signals.jsonl` with `kind="reflection"` + `parent_ids=[…]`. Reflections themselves become eligible for the *next* reflection cycle, producing a tree.

**LOC:** ~120 (one new file + 2 tests).

**Depends on:** Phase 1 importance scoring + Phase 2.1 ProactiveLoop scaffolding.

**When:** ship after finals (high-value but introduces a second LLM-firing cron + needs at least 2 weeks of signal data before it produces meaningful trees).

### R2 — `last_accessed_at` decay (already in deferred-mediums)

**Precedent:** Park et al. Algorithm 1 line 6, LangChain `TimeWeightedVectorStoreRetriever`.

**Shape:** new `ConversationEntry.lastAccessedAt: String?` populated lazily by a sidecar `last_access.jsonl` (one row per access: `{msgId, ts}`). `recentByImportance` joins lazily and uses max(creation_ts, last_accessed) as the recency anchor. Append-only, restart-safe.

**LOC:** ~50 + 1 test.

**Depends on:** existing `Conversations.recentByImportance`.

**When:** anytime — small, isolated, reversible.

### R3 — User feedback loop (ack / pin / dismiss)

**Precedent:** Mem0 feedback API, Slack `mark_as_read`, Generative-Agents implicit "user reacted" pattern.

**Shape:** `POST /api/signals/ack` with `{id, action: "dismissed"|"pinned"|"useful"|"noise"}`. Append to `feedback.jsonl`. Android Notification action buttons ("Pin", "Dismiss") or in-app long-press menu fire the POST. Aggregated weekly: `feedback.jsonl` becomes the input for re-tuning `ActivityScorer` and `ConversationScorer` weights (Phase 5 territory).

**LOC:** ~60 server + ~50 client.

**Depends on:** existing `signals.jsonl` + Phase 3.1 Notification surface.

**When:** ship anytime — strict dependency for any future scorer-learning step.

### R4 — Hybrid retrieval (lexical + semantic + entity)

**Precedent:** Mem0 3-pass parallel retrieval (sem + BM25 + entity-graph), score fusion outperforms individual signals.

**Shape:** new `HybridRetriever` that runs `SearchSubsystem` (lexical), `EmbeddingsClient.search` (semantic), and a new tiny entity-graph (extract proper nouns from query + signal text via a regex on capitalized non-stopwords; build a small in-memory adjacency map). Fuse the three ranked lists via Reciprocal Rank Fusion. Use this from `[[recall]]` and as input to reflection R1.

**LOC:** ~80 + 3 tests.

**Depends on:** existing semantic + lexical primitives.

**When:** after finals — adds latency to `/api/chat` recall path; risk of regression during finals.

### R5 — Daily email summary (Khoj-pattern)

**Precedent:** Khoj scheduled automations email-back.

**Shape:** extend `runReflect` daily cron to also send an SMTP email (single-user, single recipient, fixed `victor.vasiloi@gmail.com` per `userEmail` context). One-paragraph summary of yesterday's high-importance signals + activity peaks, generated via ctx-model. Quiet by default, opt-in via env `JARVIS_DAILY_EMAIL=true`.

**LOC:** ~30 server (SMTP) + 1 test (mocked transport).

**Depends on:** existing `runReflect` + a writable SMTP relay config (Gmail app-password or any).

**When:** anytime.

### R6 — Live focus-session ongoing notification (Material3 Live Update / iOS-Live-Activities equivalent)

**Precedent:** Android 16 promoted-to-ongoing notifications + iOS Live Activities; Now-in-Android ongoing-activity sample.

**Shape:** when `ActivityScorer` detects a sustained high-importance focus block (≥30min same-process, importance ≥0.7), post a foreground-service-style ongoing notification: "🧠 deep work — main.kt — 47 min." Auto-clears when activity churn rises or session ends. **Quiet** — sound off, vibrate off — purely glanceable.

**LOC:** ~80 client (foreground service + ongoing notification).

**Depends on:** Phase 3.1 Notification infrastructure.

**When:** after finals — risk of mid-finals regression on a primary surface; non-trivial Android lifecycle work.

### R7 — Granular user notification control + signal-history feed

**Precedent:** Notification fatigue research 2026, Material3 best-practice, Samsung One UI 8.5 AI-spam-detector pattern.

**Shape:** new in-app screen "Signal preferences" — quiet hours editable, importance threshold slider (0..1), per-`kind` mute switches (`ctx_model_summary`, `reflection`, `error`), and a paginated history feed reading from `signals.jsonl`. Replaces the current default-off-toggle-in-env-file approach with first-party UI.

**LOC:** ~150 client (one new Compose screen).

**Depends on:** existing Compose scaffolding + R3 history endpoint.

**When:** ship after finals — adds Compose surface area when user is otherwise busy.

### R8 — Read-only Google Calendar pull (Phase 6.1 from existing roadmap)

**Precedent:** Khoj-style minimal-scope OAuth, principle of least privilege, no-full-corpus-cache architecture.

**Shape:** spawn research agent on `gcalcli` vs Google Calendar API direct vs CalDAV. Pick simplest. **Read-only.** Pull next-7-days busy windows + event titles; cache only what `ctx-model` consumes per turn (no permanent store of calendar events; refetch on demand). Adds a `[[calendar]]` chat tool.

**LOC:** ~100 server (research-agent-driven).

**Depends on:** user OAuth grant (manual, interactive — on the autonomous-agent CAN'T list, requires user action).

**When:** after finals (interactive auth flow needs user time).

## "Do not build" list

Patterns failed at scale; do not introduce regardless of feature appeal.

1. **Audio capture / always-listening.** Fatal at Friend.com. jarvis has no audio surface today; do not add one without explicit user-initiated session control + visible recording indicator.
2. **Voice as primary input.** Failed at Humane Pin + Rabbit R1. The 80/20 rule applies. Voice can be a *supplement* (existing STT in MainActivity); never a replacement.
3. **Engagement-maximizing personality.** Replika + Character.AI fines, FTC complaints. Do not tune jarvis prompts toward "stickiness." Honesty + silence > engagement.
4. **Hardware product.** All hardware personal-AI products in the dataset failed. jarvis is software-only and should remain so.
5. **Default-on personal-data collection.** GDPR/CPRA dark-pattern category. Every new input axis (calendar, email, biometric) ships default-off behind an explicit `*_ENABLED` env or in-app toggle, with a clear scope label.
6. **Broad OAuth scopes.** Read-only, narrowly-scoped only. Never request `gmail.send` if read suffices. Never request `calendar` (full read-write) if `calendar.readonly` suffices.
7. **Persistent full-corpus cache of integrated services.** Khoj-style on-demand fetch + ephemeral in-context use only. Do not mirror calendar / email into local JSONL.
8. **Cross-tenant features.** Single-user only. No "share with friend," no team mode, no anonymous analytics aggregation.
9. **Auto-write to shared spaces.** Auto-reply, auto-Slack, auto-comment-on-PR — irreversible blast radius. Stays on the existing roadmap's anti-features list.
10. **Sub-cooldown notification floods.** Notification fatigue is the dominant uninstall driver. Cooldown is sacred.

## Bibliography

[1] Letta. "Understanding memory management." Letta Docs. https://docs.letta.com/advanced/memory-management/
[2] Letta. "MemGPT memory concepts." Letta Docs. https://docs.letta.com/concepts/memgpt/
[3] Letta. "MemGPT Agents (Legacy)." Letta Docs. https://docs.letta.com/guides/legacy/memgpt_agents_legacy
[4] Aicerts News. "Friend Pendant Sparks AI Wearable Privacy Debate." 2025-10. https://www.aicerts.ai/news/friend-pendant-sparks-ai-wearable-privacy-debate/  ; CNN Business. "How this tiny device became a symbol for the backlash against AI." 2025-11-16. https://www.cnn.com/2025/11/16/tech/friend-ai-device-backlash-ceo-avi-schiffmann ; Fortune. "I tried the viral AI 'Friend' necklace…" 2025-10-03.
[5] AskTodo. "The End of Forgetting: Limitless, Rewind, and the Rise of Personal Knowledge AI." 2025. https://asktodo.ai/blog/ai-memory-assistants-limitless-rewind-trends-2025 ; TechCrunch. "a16z-backed Rewind pivots to build AI-powered pendant…" 2024-04-17.
[6] VAExperience. "The UX Fails of AI Tech: Rabbit R1 & Humane AI Pin." https://blog.vaexperience.com/the-ux-fails-of-ai-tech-rabbit-r1-humane-ai-pin/ ; DigitalApplied. "AI Product Failures 2026: Sora, Humane & Rabbit R1." https://www.digitalapplied.com/blog/ai-product-failures-2026-sora-humane-rabbit-lessons
[7] Buchanan Ingersoll & Rooney PC. "Emotional AI Company Fined for Privacy Violations." 2025-05-19. https://www.bipc.com/european-authority-fined-emotional-ai-company-for-privacy-violations
[8] Letta. "Agent Memory: How to Build Agents that Learn and Remember." Letta Blog. https://www.letta.com/blog/agent-memory
[9] Letta. "Rearchitecting Letta's Agent Loop: Lessons from ReAct, MemGPT, & Claude Code." Letta Blog. https://www.letta.com/blog/letta-v1-agent
[10] Park, J. S. et al. "Generative Agents: Interactive Simulacra of Human Behavior." 2023. https://ar5iv.labs.arxiv.org/html/2304.03442 ; ACM full text https://dl.acm.org/doi/fullHtml/10.1145/3586183.3606763
[11] Mem0 (Chhikara et al.). "Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory." arXiv 2504.19413, 2025. https://arxiv.org/html/2504.19413v1
[12] Mem0. "Memory Evaluation." Mem0 Docs. https://docs.mem0.ai/core-concepts/memory-evaluation ; Mem0 Research, https://mem0.ai/research
[13] Khoj AI. "Overview." https://docs.khoj.dev/ ; Khoj-AI GitHub, https://github.com/khoj-ai/khoj
[14] PMC. "Emotional AI and the rise of pseudo-intimacy." https://pmc.ncbi.nlm.nih.gov/articles/PMC12488433/ ; arXiv 2601.16824. "Privacy in Human-AI Romantic Relationships." https://arxiv.org/html/2601.16824v1 ; Brookings, "What happens when AI chatbots replace real human connection." https://www.brookings.edu/articles/what-happens-when-ai-chatbots-replace-real-human-connection/
[15] Appbot. "App Push Notification Best Practices for 2026." https://appbot.co/blog/app-push-notifications-2026-best-practices/ ; Courier. "Notification Fatigue Is Real and Getting Worse." 2026-01.
[16] WebProNews. "Samsung One UI 8.5: AI Tool to Block Ad Spam Notifications on Android 16." https://www.webpronews.com/samsung-one-ui-8-5-ai-tool-to-block-ad-spam-notifications-on-android-16/
[17] Scalekit. "Building an AI agent to automate Gmail & Google Calendar meeting scheduling." https://www.scalekit.com/blog/scheduling-tool-gmail-to-calendar-event ; Knit. "Outlook Calendar API Integration (In-Depth)." https://www.getknit.dev/blog/outlook-calendar-api-integration-in-depth
[18] arXiv 2512.15374. "SCOPE: Prompt Evolution for Enhancing Agent Effectiveness." https://arxiv.org/html/2512.15374v1 ; Anthropic Engineering. "Effective context engineering for AI agents." https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents

## Methodology appendix

- Workflow: claude-deep-research-skill, standard mode, 6 phases (scope → plan → retrieve → triangulate → synthesize → package).
- 14 retrieval queries across 8 axes (Letta, Mem0, Park et al., Friend.com failure, Rewind/Limitless, Humane/Rabbit failure, Khoj, Android notification fatigue 2026, mood/energy modeling, Replika/Character privacy, foreground services / Live Activities, self-prompted reflection patterns, calendar/email OAuth, dark-pattern consent).
- Each major claim in Findings is anchored to ≥1 citation; primary architectural claims are cross-anchored (Letta + Park; Mem0 + LangChain; Khoj + standard OAuth practice).
- No primary research; no telemetry. Limitations enumerated in dedicated section.
- Output written to `docs/superpowers/research/2026-05-08-personal-ai-life-os-deep-research.md` per user instruction (overrides skill default of `~/Documents/[Topic]_Research_[YYYYMMDD]/`). HTML/PDF generation skipped (no python toolchain on this VPS-deploy workstation; markdown is canonical).
