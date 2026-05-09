# Wrap-review council — 1778289095

**Problem:** Validate session wrap-up artifacts (resume note, memory updates, GitHub remote, commit dd902d4 + 13ffe5e). Two rounds run.

**Round 1 wrap-review** (after first wrap-up commit `dd902d4`):
- Devil's Advocate: NEEDS_FIXES — R3 transcript only in conversation history, spec drift, scope ambiguity, off-disk feedback rule conflict, untracked .vbs, VPS deploy gap, memory bloat, session-start protocol contradiction
- Pragmatist: NEEDS_FIXES — vision-LLM provider gap, Tauri/Rust toolchain blocker, R3 verdict not durable, .vbs untracked, scattered build commands, VPS deploy ambiguity, missing dev-loop frontend command
- First Principles: NEEDS_FIXES — authorization scope expiry, off-disk backup vibes claim, council R1/R2 history buried, PS HW deadline tension unacknowledged, plan sizing missing, VPS deploy gap

R1 fixes applied in commit `13ffe5e`:
- Durable R3 transcript at `.claude/council-cache/council-1778288445-r3.md` (reconstructed)
- Council R1/R2/R3 history moved to top of resume note
- Scope fence: Layer B authorization expires at `tutor/layer-b-acceptance` tag
- Stop conditions enumerated
- Layer B prerequisites checklist
- VPS deploy hard rule (no mid-Layer-B deploy)
- Dev-loop commands documented
- Plan sizing hint (20-50 tasks)
- Off-disk backup decision tree with GitHub-remote exception
- Session-start disambiguation
- `tools/start_block_enforcer_hidden.vbs` committed
- Spec-deviation footnote at bottom of resume note

**Round 2 wrap-review** (after R1 fixes):
- Devil's Advocate: NEW_ISSUES_INTRODUCED — R3 transcript reconstruction smuggles in cleaner reasoning; stop-conditions live in TWO places with subtle divergence; resume note ballooned and buried Layer B details; tag-placement authority still ambiguous; vision-LLM verification has no reproducible test; off-disk N>5 threshold silently deleted; spec-deviation footnote is workaround not fix
- Pragmatist: FIXES_PARTIAL — port mismatch deferred not fixed (vite proxies `:7331`, backend defaults `:8080`); `Config.port` referenced in resume note but doesn't exist (port lives in `WebMain.kt:45`); `council-r3-wrap-review.md` advertised but file absent; resume note 217 lines borderline; stop conditions over-broad; plan sizing might undershoot
- First Principles: FIXES_PARTIAL — five irreducible-knowledge categories ARE answered but scattered across 3 artifacts with overlapping content; stop conditions mix checkable-at-start with judgment items; R3 transcript reconstruction acknowledged but bullets read post-hoc; spec-vs-R3 conflict resolution buried at line 199; tag-placement implicit not explicit; "tutor" disambiguation collides with PS-tutoring vocabulary

R2 fixes applied (this commit):
- **Port reconciliation FIXED** — `tutor-web/vite.config.ts` proxy target changed from `http://localhost:7331` → `http://localhost:8080` (matches backend `WebMain.kt:45` `DEFAULT_PORT = 8080`); also added `/auth` and `/tutor` to proxy list
- **Config.port reference fixed** — resume note dev-loop command now correctly cites `WebMain.kt:45` `DEFAULT_PORT` and `WebMain.kt:60` `JARVIS_PORT` env read; `Config.kt` reference removed (port doesn't live there)
- **`council-r3-wrap-review.md` reference replaced** — points to this file (`council-1778289095-wrap-review.md`) instead
- **READ-ORDER section added** to top of resume note — explicit conflict-resolution rule: resume note > R3 transcript > spec > MEMORY.md when artifacts disagree on Router/scope/stop-conditions
- **Tag-placement authority made explicit** — Claude self-tags after `LayerBAcceptanceTest` passes; authorization expires immediately on tag; pause and surface "Layer B shipped, awaiting orders"
- **Vision-LLM verification given concrete test** — curl one-liner with 1x1 PNG against relay endpoint; PASS/FAIL criteria documented; pivot options ranked (a) OpenRouter on VPS *recommended*, (b) skip vision-sensor for Layer B, (c) claude CLI on VPS deferred per CAN'T list
- **Stop conditions split** — checkable-at-start (vision-LLM / Tauri / port) vs watch-during-execution (PS HW pressure / 5/5 REJECT / >2 sessions / provider failure); "user dogfooding by typing PS-relevant questions into tutor chat" explicitly NOT a stop condition (target use case)
- **MEMORY.md stop conditions deduplicated** — replaced enumeration with pointer to resume note (single source of truth)
- **"tutor" disambiguation tightened** — bare "tutor" removed from triggers (collides with PS-tutoring vocab); now requires "jarvis tutor", "tutor app", "tutor layer" or other explicit jarvis reference
- **Off-disk backup N>5 threshold preserved orthogonally** — decision tree restructured as Step 1 (push if remote exists) + Step 2 (bundle threshold check independent of push success); >5 unpushed local-only commits triggers bundle regardless of remote
- **Spec banner added** — `docs/superpowers/specs/2026-05-09-jarvis-tutor-design.md` top-of-file amendment notes Router-in-Layer-A per R3, with read-order link to this resume note
- **R3 transcript caveat strengthened** — already had "(reconstructed)" header; resume note now adds inline note: "stances faithful, reasoning bullets are post-hoc summary, not verbatim"

**Deferred to next session (P2 — non-blocking):**
- Memory bloat refactor on `project_jarvis_kotlin.md` (250 lines, append-only) — substantial work, defer until next session has fresh-eyes reader
- Plan sizing widened slightly (20-50 → 20-65 with definite-creep at >80) — minor tweak left for resume-note proper if needed
- Resume note length still ~250 lines after R2 fixes — could split Layer A retrospective to sibling `layer-a-summary.md` but decided against fragmenting

**Output saved to:** `.claude/council-cache/council-1778289095-wrap-review.md`
