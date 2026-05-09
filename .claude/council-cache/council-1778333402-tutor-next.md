# Council review — 1778333402

**Problem:** Pick the next 1-3 capabilities to ship for jarvis-kotlin tutor from a 7-capability researcher menu (tree-sitter extractor, Graphify-shape graph tools, SKILL.md packaging, multi-channel daemon gateway, daemon cron skill-runner, page-per-concept wiki, gws CLI effector).

**Proposed approach:** Constructive 5-perspective council to evaluate which capabilities to ship now, next week, and defer — given the user's unmet need ("Jarvis should know my materials and answer where X shows up without me pointing"), the existing stack (491 backend tests, 55 frontend tests, 16 daemon tests, TaskHeaderBuilder V0 shipped, JarvisToolset.search_archival not wired into chat, marker-pattern `[[search: query]]` hack in ChatTools.runTurn), and the constraints (RelayLlm default, OpenRouter switch = $1-3/day, daemon + reverse-SSH already fragile, seminar/lecture content is prose PDFs not code).

**Project context:**
- Domain: Personal AI tutor stack (jarvis-kotlin) — Kotlin/Ktor backend on VPS, Vite/React frontend at /tutor/, Rust axum daemon at 127.0.0.1:7331 with HMAC + enigo keystroke injection + reverse SSH tunnel.
- Stack: Kotlin/Ktor (491 tests), Vite/React 19/Tailwind v4 (55 tests), Rust axum (16 tests), SQLite-WAL, Exposed ORM.
- Existing primitives: TaskHeaderBuilder (≤300 tok always-on header, 30s LRU), JarvisToolset.search_archival (HybridRetriever-backed, NOT wired into chat), ScreenshotExtractor (vision LLM), SourceClassifier, ShadowGit, EffectorAttempt state machine, AuditLine hash chain, kill switch via ~/.jarvis/KILL.
- Constraints: deadline framing forbidden (user dismissed twice), authorization settled, focus only on capability + ordering correctness.
- Prior decisions: ruflo IGNORE; Graphify ADAPT (subprocess fragility flagged); OpenClaw ADAPT selectively; gws CLI optional; Karpathy LLM Wiki = knowledge architecture not tool-use alternative.

**Timestamp:** 2026-05-09T (epoch 1778333402)

---

## 🔴 Devil's Advocate

STANCE: REJECT (the menu as a whole) / CONDITIONAL (on items 1–2 narrowly scoped)

REASONING: Five of the seven capabilities are speculative infrastructure for problems that haven't proven themselves yet, and the two that look like real value (1+2) are framed around a tool — Graphify — whose central trick is wrong for the user's actual content. Tree-sitter parses code grammars; it does not parse Romanian-language seminar PDFs about formal language theory or operating-systems concepts. You'd burn a week building a "deterministic-first" extractor that has nothing deterministic to bite on, then fall back to LLM-on-prose for ~95% of nodes — at which point you've reinvented retrieval augmentation with extra ceremony. The marker pattern + search_archival is already the unwired version of the right answer; ship that path and stop dressing it up as a graph.

KEY CONCERNS (ranked):
1. CRITICAL — Tree-sitter on prose is a category error. Capability 1 collapses to "another LLM chunker with extra config."
2. CRITICAL — Capabilities 4 + 7 (chat gateway + gws CLI) blow open the threat model. `/gateway/inbound` forwarding Telegram to `/api/chat` makes any compromised messenger account an injection vector into the LLM chain that already has effector dispatch wired.
3. HIGH — Capability 5 (background skill-runner cron in daemon) violates the kill-switch invariant. Cron-fired LLM activity the user didn't initiate, runs against the same effector dispatch path.
4. HIGH — Capability 6 (wiki/<concept-slug>.md) is a state-explosion trap without a curator. Within a month: `formal-languages.md`, `formal-language.md`, `Formal Languages.md`, `chomsky-hierarchy.md`, `chomsky.md`, all overlapping. Karpathy's gist works because Karpathy is the merge function. The tutor has no merge function.
5. MEDIUM — The marker pattern is the prompt-injection hole already in production. Any string the model is told to echo can fabricate a `[[search: ...]]` marker the parser executes. Migrate the channel before adding 4 more graph tools through it.

KEY CONCERN: Building infrastructure for a corpus that hasn't been ingested. The unmet need is an *ingestion-and-indexing* problem, not a *graph-modeling* one — and the menu confuses those.

## 📚 Domain Expert

STANCE: CONDITIONAL

REASONING: The well-trod pattern for "answer questions about my course content with citations to my specific files" is RAG over a chunked corpus with hybrid retrieval (BM25 + dense), not graph construction. NotebookLM, Mem.ai, Cursor `@docs`, Continue.dev `@codebase`, and Claude Projects all converge on: ingest → chunk → embed → hybrid retrieve → cite-by-source-span. Early Obsidian-AI / Logseq-AI plugins tried graph-as-primary and failed because graph traversal returns paths, not passages, and the LLM needs passages with provenance. Capability 2 is GraphRAG (Microsoft, Edge et al. 2024), real win on multi-hop/thematic queries — overkill for the dominant single-hop "where is X defined" question class here. The SOTA primitive is already 90% built (`HybridRetriever` + `search_archival`); wiring it into chat via real tool_use is the highest-leverage move and it's hidden in the preamble (the OpenRouter switch) rather than listed as one of the seven.

KEY CONCERNS (ranked):
1. CRITICAL — The menu omits the prior-art SOTA move that's already 90% built. Wire search_archival into chat as a real tool (not the marker hack), via OpenRouter tool_use, citations as `{file_path, line_range, snippet}`. Cost is $1–3/day; the menu treats this as a constraint when it's the answer.
2. HIGH — Capability 2 (Graphify-shape graph tools) is GraphRAG, premature here. Microsoft GraphRAG paper shows graph helps on global/thematic queries, hurts latency on local queries that dominate tutor use. Ship hybrid retrieval first.
3. HIGH — Capability 3 (SKILL.md / AGENTS.md) duplicates Anthropic's own skill system. You're on Claude Code with first-class skills. Write tutor flows as `.claude/skills/<name>/SKILL.md` — same format, native runtime, no parallel loader.
4. MEDIUM — Capability 6 (page-per-concept wiki) is the Anki/Obsidian pattern; well understood, requires curation. Ship as opt-in scratch surface, not as backbone.
5. MEDIUM — Multi-channel gateway (capability 4) is a chat-platform problem (n8n/Pipedream/OpenClaw), not a tutor problem. Building it inside the tutor stack couples two unrelated lifecycles.

KEY CONCERN: The menu skipped past "wire the retriever you already have into chat with proper tool_use" — which is what every comparable system shipped first.

## ⚙️ Pragmatist

STANCE: CONDITIONAL

REASONING: Of the seven, only one is single-engineer-shippable in 3–5 commits with dogfood-by-tomorrow latency, and the others all have at least one of {new dependency / new fragility / new threat-model surface / requires a corpus you haven't ingested}. The one that's small AND high-value AND closes the user's stated unmet need is: wire `search_archival` into chat via OpenRouter tool_use with file-cited results. Everything else can wait until you've used real tool_use for a week and learned what the model actually needs.

KEY CONCERNS (ranked):
1. CRITICAL — The "switch to OpenRouter = $1-3/day" framing is treating a feature toggle as a blocker. That's a working-day coffee. Pay the toggle.
2. HIGH — Capabilities 1+2 are 2 weeks minimum to ship correctly even in pragmatist form (skip tree-sitter, use LLM-only chunker, graph as sqlite tables): ingestion CLI + chunker + embedder + node/edge tables + 4 tool handlers + chat wiring + tests.
3. HIGH — Capabilities 4, 5, 7 add daemon surface area while the daemon's existing fragility (reverse-SSH tunnel, kill-switch, restart story) is unsolved.
4. MEDIUM — Capability 3 (SKILL.md packaging) is non-functional infrastructure — refactor wearing capability clothing. Defer until ≥3 skill-shaped flows exist.

SMALLEST VIABLE NEXT SHIP — numbered (target: ≤5 commits, dogfood by tomorrow):
1. Add `OpenRouterChatLlm` adapter in chain. Same interface as `RelayLlm`; tool_use enabled; reads `OPENROUTER_API_KEY` from env. ~1 commit.
2. Wire `search_archival` as a real tool in `JarvisToolset` for chat path — JSON-schema params `{query: string, top_k: int = 5}`, returns `{results: [{file_path, line_range, snippet, score}]}`. Behind per-task `llm_provider: "openrouter"|"relay"` flag in TaskHeader for A/B. ~1 commit.
3. Augment `TaskHeaderBuilder` to inject one-line "available tools" hint when `llm_provider == openrouter`. Stay within 300-tok budget. ~1 commit.
4. Ingestion smoke-CLI: `./gradlew ingestCorpus --path C:\Users\User\jarvis-kotlin\docs\subjects` — chunks markdown by H2/H3, embeds, writes to existing `HybridRetriever` index. No graph, no wiki, no curator. ~1 commit.
5. Acceptance test: `tutor/openrouter-search-acceptance` — token → `/tutor` → user asks "where in PA seminars do we cover Master theorem" → model emits proper tool_call → `search_archival` returns ≥1 result with `file_path` and `line_range` → reply cites file path verbatim → audit chain logs the tool_call → green. ~1 commit.

Total: 5 commits, no new languages, no new processes, no new ports, no new threat surface. Closes user's stated unmet need with a measurable acceptance test by tomorrow.

KEY CONCERN: The menu over-indexes on net-new capabilities and under-indexes on finishing the half-built ones.

## 🧱 First Principles

STANCE: WRONG-APPROACH-FOR-MENU / CONDITIONAL on the underlying goal

REASONING: The actual goal stripped of framing is: *the LLM should be able to read the user's existing materials and cite specific spans of them in its replies, without the user pointing.* Everything else in the menu is decoration on or premature optimization of that single primitive. The 7-capability menu is a researcher's "what could we build" enumeration — it's derived from the set of repos evaluated this session, not from the goal. If you took the goal and reasoned upward with no prior art, you would build exactly one thing: a tool the model can call that takes a natural-language query and returns ranked passages from the user's materials with file paths and line ranges. That is `search_archival` wired into chat via real tool_use. Everything else (graph, wiki, skills, gateway, cron, gws) is auxiliary scaffolding around or beyond that primitive.

KEY CONCERNS (ranked):
1. CRITICAL — The menu confuses "what could we build with these new tools" with "what would close the unmet need." The unmet need is closed by one tool, not seven.
2. HIGH — Capability 3 (SKILL.md) confuses packaging with capability. Convention can only emerge after you have ≥3 skills. You have zero.
3. HIGH — Capability 4 (gateway) confuses access surface with usefulness. The user already has the most direct possible access surface (their own browser at `/tutor`). Telegram makes it reachable from a couch — that's a feature for after the model is actually useful, not before.
4. MEDIUM — Capability 5 (cron) confuses initiative with intelligence. A daily auto-summary feels intelligent but is mostly noise unless the model has something to say. Ship retrieval first; let the user pull. If pull works, then consider push.
5. MEDIUM — The OpenRouter cost framing ($1-3/day) is the load-bearing self-deception. It's described as a constraint when it's actually the unlock. Pay the toggle.

KEY CONCERN: The user's underlying need is best served by one well-designed primitive, not seven capabilities. The menu obscures this because it was generated by surveying tools, not by reasoning from the goal.

## ⚠️ Risk Analyst

STANCE: CONDITIONAL

REASONING: The stack already has two known fragilities — daemon process liveness and reverse-SSH tunnel — and none of the proposed mitigations touch those. Five of the seven capabilities (1, 4, 5, 6, 7) actively add new failure modes on top. The ranking of failure-mode severity is roughly: kill-switch evasion > daemon-resident long-running processes > Python subprocess lifecycle > marker-pattern injection > corpus duplication. The proposed menu adds risk in approximately the inverse order of urgency.

KEY CONCERNS (ranked):
1. CRITICAL — Background cron in daemon (capability 5) breaks the kill-switch contract. Cron-fired LLM calls without user click; if daemon misses the kill-file check on a cron tick, unattended LLM spend + unattended effector attempts. Mitigation: any cron must be a system-level cron (Windows Task Scheduler / launchd) calling a foreground CLI, not a daemon-resident loop, and must check `KILL` before loading any LLM credential.
2. CRITICAL — Multi-channel gateway (capability 4) breaks the trust boundary all current security assumes. EffectorValidator, SourceClassifier, grant rate-limit, PromptInjectionScrubber assume the prompt source is the user's own browser session over the user's own VPS. Telegram inbound = unverifiable sender routed through HMAC that authenticates the daemon-to-server hop, not the human-to-daemon hop. Mitigation: do not ship a gateway. If you must, gate behind per-channel READ_ONLY lock that disables effector dispatch when source_channel != "browser", enforced at the route layer not the LLM-prompt layer.
3. HIGH — Python subprocess for Graphify (capability 1) introduces a third process with its own restart story. Already have VPS ktor + Rust daemon + reverse-SSH tunnel. Add Python interpreter + venv + Graphify package + tree-sitter binary parsers per language on Windows. First Windows update touching Python and the tutor is dark. Mitigation: do not vendor Graphify. Port the 4 graph queries directly to Kotlin against existing SQLite — <500 lines.
4. HIGH — gws CLI (capability 7) adds OAuth refresh-token lifecycle owned by a third process you do not maintain. Token refresh failure modes (revoked grant, expired refresh, scope drift) are silent until they aren't, blast radius is high (Gmail draft of LLM reply). Mitigation: if shipped, restrict to read-only scopes day one (Drive read for syllabus ingest only, no Gmail, no Calendar write).
5. MEDIUM — Wiki page-per-concept (capability 6) without de-dup grows monotonically. Within 30 days expect ≥2x page count vs concept count. Mitigation: don't ship without write-time canonicalizer (slug normalizer + alias table) and a daily de-dup job — which is itself capability 5, which is itself a fragility, etc.

KEY CONCERN: The single highest risk in the menu is autonomous-LLM-activity (cron + multi-channel gateway) being added before the kill-switch and trust-boundary invariants are explicitly re-proven for the new sources. Ship neither 4 nor 5 until a written threat model covers them.

---

## Sanity Check

SANITY [Devil's Advocate]: PASS. Concrete file paths, named threat vectors, ranked. No diplomacy. Stayed in lane. The "rm -rf" hypothetical in concern #5 is illustrative not literal; reasoning still holds (any echoed string can fabricate a marker).

SANITY [Domain Expert]: PASS. Named real systems (NotebookLM, Cursor `@docs`, Continue.dev `@codebase`, Claude Projects, Microsoft GraphRAG with Edge et al. 2024 paper, Anthropic Skills). Compared menu items to specific prior art. Clean.

SANITY [Pragmatist]: PASS. Numbered shippable tasks with file/commit-level granularity. CONDITIONAL stated with explicit conditions ("flip the toggle"). No hedging. Clean.

SANITY [First Principles]: PASS. Actively rejected the "pick from menu" framing and named the goal underneath. Compared from-scratch answer (retrieval-with-citation) to proposed approach (7-capability menu) and named the divergence. Clean.

SANITY [Risk Analyst]: PASS. Ranks risks CRITICAL/HIGH/MEDIUM as required. Names specific mitigations per risk. Top-2 focus preserved. Clean.

All five clean. Judge weights all five fully.

---

## Synthesis

### Top 3 must-fix concerns (cross-agent consensus)

1. **The OpenRouter "cost constraint" is the actual unlock, not the actual constraint.** Pragmatist + First Principles + Domain Expert independently flagged. The marker pattern is a hack invented to avoid the $1–3/day toggle; flipping it dissolves the hack and unlocks real tool_use, which is the architecture every comparable system uses (Cursor, Continue, Claude Projects, NotebookLM).
2. **Capabilities 4 + 5 break the trust + kill-switch invariants the current stack relies on.** Devil's Advocate + Risk Analyst converge. Cannot ship until written threat models re-prove the invariants for non-browser sources and non-user-initiated activity. Defer indefinitely, not "next week."
3. **Tree-sitter on prose PDFs is a category error; Graphify-shape graph tools are GraphRAG, premature for single-hop queries.** Devil's Advocate + Domain Expert + First Principles converge. Capabilities 1 + 2 should not ship until measured-failure evidence exists that hybrid retrieval misses on multi-hop or thematic queries.

### Recommended ship order

**Tomorrow (1 capability — the only one that survives all 5 lenses):**
Wire `search_archival` into chat via OpenRouter tool_use with file-path + line-range citations. Pragmatist's 5 numbered tasks above are the ship plan.

**Next week (2 — only after ≥3 days of dogfooding the above):**
- Capability 1' (modified): LLM-only corpus chunker + embedder, no tree-sitter, no graph. Expand `search_archival` reach by ingesting full `docs/subjects/` and `docs/superpowers/` trees into existing `HybridRetriever` index.
- Capability 3' (modified): Promote 1–2 tutor flows that have repeated ≥3 times during dogfooding into native Claude Code skills under `.claude/skills/`. Don't reinvent skill packaging.

**Defer indefinitely (4 — explicit "do not schedule"):**
- Capability 2 (graph tools) — until measured retrieval failure on thematic queries.
- Capability 4 (multi-channel gateway) — until written threat model exists.
- Capability 5 (daemon cron) — until kill-switch + watchdog spec exists for the daemon.
- Capability 6 (page-per-concept wiki) — until a curator (LLM or human) is in place.
- Capability 7 (gws CLI) — if ever shipped, scope to Drive-read-only on day one.

### Load-bearing First Principles insight

> The user's unmet need is closed by exactly one tool — retrieval-with-citation that the model invokes itself — and the menu obscures this by surveying repos instead of reasoning from the goal. Build the one tool. Use it for a week. Let the next capability be the one whose absence you actually felt, not the one that came pre-packaged in someone else's framework.

---

## Judge

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: WRONG APPROACH

CORE FINDING:
The 7-capability menu is the wrong frame. Four of seven capabilities (4, 5, 6, 7)
add risk before fixing existing fragilities; two (1, 2) build graph infrastructure
for a content type tree-sitter doesn't parse and a query class hybrid retrieval
already handles. The single capability that closes the user's stated unmet need —
"Jarvis should know where in my materials X shows up" — isn't on the menu at all:
it's wiring search_archival into chat via real tool_use, which costs $1-3/day and
~5 commits.

AGENT CONSENSUS: 1 REJECT (the menu), 4 CONDITIONAL — 0 agents flagged. All five
clean agents converged on "wire search_archival via OpenRouter tool_use first" as
the actual next ship; three (Pragmatist, Domain Expert, First Principles) called
out the OpenRouter cost framing as the load-bearing self-deception in the menu.

KEY ISSUES:
- The OpenRouter $1-3/day cost is being treated as a constraint when it's the
  unlock for proper tool_use. The marker-pattern hack exists because of it.
  Flip the toggle.
- Capabilities 4 (multi-channel gateway) and 5 (daemon cron) break the trust-
  boundary and kill-switch invariants the current stack assumes. Both must be
  deferred indefinitely until written threat models exist for non-browser sources
  and non-user-initiated LLM activity.
- Capability 1 (tree-sitter on seminar PDFs) is a category error — tree-sitter
  parses code grammars, not Romanian-language prose. The "deterministic-first"
  promise collapses to LLM-on-prose for ~95% of nodes.

RECOMMENDED PATH:
Ship by tomorrow (Pragmatist's 5 numbered tasks): (1) OpenRouterChatLlm adapter,
(2) search_archival as real JarvisToolset tool with JSON-schema params and
{file_path, line_range, snippet} return shape, (3) TaskHeaderBuilder injects
"available tools" hint when llm_provider==openrouter, (4) ./gradlew ingestCorpus
smoke-CLI for docs/subjects, (5) tutor/openrouter-search-acceptance test. This is
~5 commits, no new languages/processes/ports/threat surface, and produces a
green acceptance test that closes the unmet need.

Next week (only after ≥3 days of dogfooding the above): expand corpus ingestion
(LLM-only chunker, no tree-sitter, no graph), and promote 1-2 repeated tutor
flows into native Claude Code skills under .claude/skills/ (not a parallel
SKILL.md runtime).

Defer indefinitely with explicit "do not schedule": capabilities 2, 4, 5, 6, 7.
Revisit only when measured-failure evidence justifies them — don't pre-build.

CONFIDENCE: 9
The one piece of information that would change this rating is whether the user
has thematic / multi-hop questions where single-passage retrieval has empirically
failed. If yes, GraphRAG-style capability 2 moves up. Until then, build the
primitive, measure, decide.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

[COUNCIL CONTEXT SAVED]
Domain: Personal AI tutor stack (jarvis-kotlin) — Kotlin/Ktor backend on VPS, Vite/React frontend at /tutor/, Rust axum daemon at 127.0.0.1:7331 with HMAC + enigo + reverse SSH tunnel.
Stack: Kotlin/Ktor (491 tests), Vite/React 19/Tailwind v4 (55 tests), Rust axum (16 tests), SQLite-WAL, Exposed ORM. RelayLlm default, OpenRouter wired only for vision sensor + experimental JarvisToolset.
Constraints: deadline framing forbidden (user dismissed twice); authorization is settled; focus only on capability + ordering correctness; daemon + reverse-SSH already known fragile; seminar/lecture content is prose PDFs not code.
Prior decisions: ruflo IGNORE; Graphify subprocess flagged as fragility; OpenClaw selectively-borrow-patterns; gws CLI optional; Karpathy LLM Wiki = knowledge architecture not tool-use alternative. TaskHeaderBuilder V0 shipped. JarvisToolset.search_archival exists but NOT wired into chat. Marker pattern `[[search: query]]` parsed in ChatTools.runTurn (22 tools defined, 11 invoked over 30 days).
Council verdict (this session): WRONG APPROACH on the 7-capability menu. Recommended next ship = wire search_archival into chat via OpenRouter tool_use (5 commits, dogfood tomorrow). Defer indefinitely: capabilities 2, 4, 5, 6, 7.
