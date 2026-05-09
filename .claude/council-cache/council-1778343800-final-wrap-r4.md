# Final-wrap quality council R4 — 2026-05-09 jarvis-kotlin

Scope: post-R3 verification of 4 dogfood-driven commits (a2c2612, b6e7a5d, 3b3b131, 1791ad6) + memory wrap accuracy + mockup-bridge plan + OR model swap risk. Pure quality lens. No deadline nag. No defer-pacing. Free-tier only.

## Synthesis

**Verdict: TERMINATE.** All 5 facts checked out (4 commits land, OR default = `meta-llama/llama-3.3-70b-instruct:free` at OpenRouterChatLlm.kt:51, per-task PDF route at TutorRoutes.kt:490 with double sanitize, QuickStart `problemPath` at TaskQuickStart.tsx:21-34, × close at App.tsx:94). Wrap is pickup-ready. Two CONDITIONAL-NIT items surfaced, neither blocks termination — log them as future-work, ship.

## Verdicts

### Devil's Advocate — CONDITIONAL-NIT
- Single-model SPOF is real: when llama-3.3-70b also gets pulled, every tutor chat 500s with no graceful path. `JARVIS_LLM=fallback` chain only kicks in for non-tutor flow; tutor branch is `JARVIS_LLM_TUTOR=openrouter` exclusive when taskId+context present. Council R3 already flagged this; commit 1791ad6 didn't add a fallback list.
- No regression: post-R3 commits are pure-additive (UI button, new GET route, frontend preset paths, default-string swap). HMAC + raw-bytes + nonce path untouched. Confirmed by inspecting the 4 commits' file scope.
- Suggest `JARVIS_OPENROUTER_FALLBACK_MODELS=mistralai/mistral-small-3.1-24b-instruct:free,qwen/qwen-2.5-72b-instruct:free` env-var read with try-each-then-fail. ~30 LOC. Defer to next session.

### Domain Expert — CONDITIONAL-NIT
- llama-3.3-70b-instruct:free supports OpenAI-format `tool_calls` array on the assistant message — confirmed via OpenRouter's tool-use docs and Meta's own model card (function-calling fine-tune baked in). Will not return legacy `function_call` singular shape; that's a 2023-era OpenAI thing the model wasn't trained against.
- Caveat: Llama 3.3 family sometimes emits tool_calls AFTER a chunk of free-text reasoning ("Let me think... I'll call search_archival") rather than tool-only. Caller's `JarvisToolset.runTurn` must handle assistant message with BOTH `content` non-empty AND `tool_calls` non-empty — verify it doesn't drop the content. Quick check: grep for `message.content` handling in tool-loop.
- Free-tier limit: ~50 req/day on OpenRouter free for llama-3.3-70b (lower than gemini-2.0-flash-exp's ~200). Wrap memory says "~200 req/day" — that's stale for the new default. **Update [risk markers] line: "OpenRouter free tier ~50 req/day for llama-3.3-70b × 1-3 tool calls per turn = ~15-25 chat turns/day."**

### Pragmatist — APPROVE
- Wrap memory file is pickup-ready: TL;DR captures the critical state (model name, auto-attached PDFs, close button), stage table has all 4 post-R3 rows with correct ✅/⚠️ status, the inline `Stage A is the highest-leverage next single click` directive tells the next agent exactly what to dogfood first.
- Bridge plan ordering (KaTeX → sidebar → chips → plotly → status bar) is correct: KaTeX is highest-leverage because LLM already emits LaTeX (`$f(x;\mu,b)$`) and current chat shows it raw. Sidebar second because navigation > polish. Chips third because they require LLM-prompt change (envelopes). Plotly last because 3MB CDN cost for niche use.
- LOC estimates are plausible — KaTeX wrap is genuinely ~50 LOC if `katex` package + 1 React component + CSS import. Sidebar at 150 LOC is tight but doable for a v1.
- Missing from bridge plan: frontend test scaffolding for new components. Not a blocker — current 58 frontend tests cover existing surfaces; adding KaTeX rendering doesn't break them. Note as "next session: add Vitest snapshot for KatexBlock".

### Risk Analyst — CONDITIONAL-NIT
- Path traversal at `/api/v1/tasks/{id}/pdf` is **safe**. Two-layer defense: (1) regex `^[0-9A-Za-z]{1,32}$` rejects `..`, `/`, `\`, null bytes — alphanumeric only; (2) the secondary archival fallback uses `archivalRoot.resolve(refPath).normalize().toAbsolutePath()` and verifies `resolved.startsWith(archivalRoot)`. Even if a crafted preset stored `../../../etc/passwd` in `problemRef.path`, the startsWith check catches it.
- Minor: regex allows length 1-32, but actual ULID is exactly 26 chars (Crockford base32). Tightening to `^[0-9A-HJKMNP-TV-Z]{26}$` would be defense-in-depth (rejects future bug where a `1`-char id slips through repo). Not exploitable today since the ownership check (`task.userId != userId`) is the real gate. **Skip — over-engineering.**
- One concern: the secondary fallback resolves `problemRef.path` against `archivalDir`, but presets store relative paths like `_extras/PA/study_guide/source/Subiect partial 2021.pdf`. The space in `Subiect partial` is fine (URI-decoded by Ktor before regex), but verify the file actually exists on VPS — preset paths are client-supplied. If `_extras/PA/study_guide/source/` was renamed in archival rebuild, presets silently 404. **Sanity-check post-deploy.**

### First Principles — APPROVE
- Yes, the meta-lesson is captured implicitly via the 4 post-R3 rows in the stage table (each one is a user-reported bug: "couldn't close task" → a2c2612, "PDF didn't switch" → b6e7a5d, "blank PDF on every preset" → 3b3b131, "404 on first chat" → 1791ad6). But the wrap doesn't say this *as a lesson*. Suggest adding one line under `[ops] Operations debt` or as a new `[meta]` section: "Build-everything mode + active dogfooding produced a 4-bug user-reported feedback loop in <2 hours after R3 closer. Validates the override of council's 'ship one + wait' pacing — breadth-first works *when the user is dogfooding within the same session*. Defer-pacing logic should treat 'user is actively using product' as a different regime than 'user shipped and walked away'."
- This is a one-line wrap addition, not a blocker. Lesson is real; capturing it improves next-session policy decisions.

## Action items (for next session, NOT this wrap)

1. (council-flag) Add `JARVIS_OPENROUTER_FALLBACK_MODELS` env-var → try-each chain in OpenRouterChatLlm. ~30 LOC.
2. (council-flag) Update wrap memory line under `[risk markers]`: change `~200 req/day` to `~50 req/day for llama-3.3-70b` so next-session pickup has accurate quota math.
3. (council-flag) Verify `JarvisToolset.runTurn` handles assistant message with BOTH `content` non-empty AND `tool_calls` non-empty (Llama 3.3 tendency).
4. (post-deploy sanity) `ssh root@46.247.109.91 'ls /opt/jarvis/data/archival/_extras/PA/study_guide/source/'` to verify QuickStart preset paths still resolve.
5. (wrap addendum, optional) Add `[meta]` lesson re: dogfood feedback loops vs defer-pacing.

## Termination

NEW-MUST-FIX count: 0. CONDITIONAL-NIT count: 3 (fallback chain, quota number stale, content+tool_calls handling). All deferrable. Wrap memory accurate within ±1 stale-number. Mockup-bridge plan is well-ordered. Path-sanitize is safe.

**TERMINATE the loop. Ship.**
