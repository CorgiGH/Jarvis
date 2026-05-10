# Tutor Drill Workspace · Slice 1 Design (2026-05-10)

> **Status:** spec under user review.
> **Authoring path:** brainstorm-driven; 19 specialist agents reviewed `tutor-tema-a-live-demo.html`; consensus issues compiled at `2026-05-10-full-site-audit-findings.md` + this doc resolves them.
> **For implementer:** see writing-plans handoff.

## Goal (one sentence)

Pivot the tutor workspace into a chat-first **drill stack** that grounds every card in the actual homework PDF, decomposes Tema A into per-problem mini-stacks, surfaces inline help at the paragraph level, inverts the order to put DRILL before EXPLANATION, replaces the regex grader with an LLM grader, and finally exposes the FSRS review surface that has been ghost-running in the DB since Phase 7.

## What's in scope (Slice 1)

1. **PDF ingest pipeline** — server-side extraction of Tema A's problem statements with page anchors.
2. **Multi-problem schema + UI** — `problemRefs: List<ContentRef>`, two-tier progress (outer N/N problems, inner 4/4 cards), per-problem mini-stack, COMPILE & SUBMIT final card, `?problem=N` URL.
3. **Inline help** — text-selection `✨ ASK` floating chip + per-paragraph `?` gutter affordance + structured context envelope sent to LLM.
4. **Productive-failure inversion** — order becomes DRILL → WORKED → DEFINITION → CHECK (Kapur 2008/2014). Drill renders first; WORKED + DEFINITION unlock as the explanation after attempt.
5. **LLM grader** — `POST /api/v1/drill/grade` replaces the regex check. Parses misconception (mean vs median vs mode), returns elaborated feedback.
6. **FSRS review surface** — `/tutor/review` route, flip card, AGAIN/HARD/GOOD/EASY with intervals, real card synth from completed drills, forecast counts.
7. **Full motion language** — all 18 v5.1 animations + Bret-Victor-grade concept illustrations: drag-μ-on-the-line direct manipulation, sumplot marker tracks curve via RAF, slope-counter votes viz, Σ-decomposition stacked bar, plus Plotly frame morphs for distribution comparisons.
8. **Tema A as the live test case** — Slice 1 ships against the real `_extras/PS/ps_hw/Tema_A.pdf`. Other tasks fall back to the legacy single-problem path until Slice 2 ports them.
9. **Daemon PC-boot autostart** — Windows Task Scheduler installer for `jarvis-daemon` Rust binary + reverse SSH tunnel. Survives PC reboot/sleep. User runs the installer once with admin perms.
10. **Google OAuth 2.0 + REST replacement for `GwsEffector`** — replace the broken `gws` subprocess approach with a direct Kotlin OAuth client + Calendar/Drive/Gmail REST clients. Wires the existing `calendar_create_event` / `drive_search` / `gmail_create_draft` LLM tools to actually work. User does one-time setup: create OAuth client in Google Cloud Console, download credentials JSON, run consent flow once.

## What's out of scope (Slice 2, deferred)

- a11y pass (focus-visible, ARIA roles, keyboard activation, drawer focus trap, ARIA live for state changes)
- Mobile responsive (`@media`, slide-over rail, 44px touch targets, Plotly mobile-safe config)
- Cross-subject viz plugins (mermaid, code-runner WASM, sorting bars, recursion tree, Gantt)
- Subject-alias fix (`SO` ↔ `SO&RC`) + material_paths backfill cron
- Performance (lazy Plotly + uPlot migration, persistent SVG nodes vs innerHTML rewrite, restyle vs react)
- Visual hierarchy refactor (palette to 3 colors, border tier, letter-spacing tier, emoji purge)
- Onboarding tooltips (FSRS, PRIOR GAP %, jargon glossary)
- Romanian academic register pass (`verifică`, `salvează`, `marchează rezolvată`)
- Streak gamification opt-out
- Devil's-advocate-driven scope cuts (rail-as-drawer, drill-as-pure-chat) — re-evaluated after Slice 1 dogfood

## What's permanently out of scope

- Telegram bot producer (user-blocked, awaiting token)

## Architecture

### A. PDF ingest pipeline

**Trigger:** task INSERT (`POST /api/v1/tasks` or `POST /api/v1/task-detect/run`). Async after 200 OK so user isn't blocked.

**Producer:** new `jarvis.tutor.PdfProblemExtractor`:
1. Resolve `task.problemRef.path` under `archivalDir`.
2. PDF text layer extraction via `pdfbox` (already on classpath via `tutor-web/react-pdf`'s server-side equivalent — verify; else add `org.apache.pdfbox:pdfbox:2.0.x`).
3. LLM call (free-tier OpenRouter chain) with prompt: "Identify each numbered problem in this homework. Return JSON `[{problem_id, page, statement, equation_refs[], data_givens[]}]`."
4. Persist to new `task_prep` table:
   ```
   task_prep(task_id PK, generated_at, version, problems_json TEXT, drills_json TEXT, rail_json TEXT)
   ```
   Cached. Invalidated on `StateVersion.bump()` + manual `/api/v1/task/{id}/reprep`.

**On open:** `GET /api/v1/tasks/{id}` joins `task_prep`. If miss → render skeleton + fire LLM async + websocket-push when ready (out of scope; for Slice 1 use polling: skeleton with `aria-busy` and SWR-style refetch every 2s).

### B. Multi-problem schema + UI

**Schema change** (`Tasks.kt`):
- `problemRef: ContentRef` → `problemRefs: List<ContentRef>` (JSON column, nullable for legacy single-problem tasks).
- New `progress: Map<String, ProblemProgress>` field. `ProblemProgress = { problemId: String, cards: Map<Int, CardState>, completedAt: Instant? }`.

**Migration:** Slice 1 doesn't drop the singular `problemRef`; it adds `problemRefs` nullable. Existing rows stay valid.

**UI:**
```
┌─ JARVIS · TUTOR · DAY 1 · PS · 12d ──────────────┐
├──────────────────────────────────────────────────┤
│ STEPPER: ◉ A1  ○ A2  ○ A3  ○ A4 …                │ ← problem stepper
├──────────────────────────────────────────────────┤
│ OUTER: ●●●○○○○  3 / 7 problems                    │
│ INNER: ●●○○      2 / 4 cards (A3)                 │
├──────────────────────────────────────────────────┤
│ ③ DRILL · YOUR TURN     [unlocks WORKED + DEF]    │
│ ② WORKED EXAMPLE        [locked until drill OK]   │
│ ① DEFINITION            [locked until drill OK]   │
│ ④ CHECK · TRANSFER       [locked until drill OK]   │
│ SIDEKICK  (collapsible, context-aware)             │
└──────────────────────────────────────────────────┘
       ▼ after problem N completes ▼
┌─ ⑤ COMPILE & SUBMIT ─────────────────────────────┐
│ Stitched answers per problem · LaTeX export ·    │
│ "MARK SUBMITTED"                                 │
└──────────────────────────────────────────────────┘
```

**URL:** `/tutor/?taskId=A&problem=3`. Stepper click pushes history. Refresh restores. Bare `?taskId=A` defaults to lowest incomplete problem.

**Resource rail:** per-problem section auto-expands; task-wide section pinned.

### C. Inline help

**Two affordances, both surfacing zero scroll:**

1. **Text-selection floating chip** — `mouseup` listener; if selection ≥ 3 chars inside `.card-body`, render `✨ ASK` chip 8px above selection. Click → opens sidekick + pre-fills with selection-as-context.

2. **Per-paragraph hover gutter** — every `<p>` in card body wrapped in `.askable`; left margin shows `?` button on hover. Click → opens sidekick with paragraph-as-context.

**Context envelope** sent to backend `POST /api/v1/sidekick/ask`:
```json
{
  "task_id": "01...",
  "problem_id": "A3",
  "card_id": "card-1",
  "card_title": "③ DRILL · YOUR TURN",
  "anchor_id": "drill-statement",
  "anchor_text": "Sample x = (3,7,8,9,14). What is μ̂?",
  "selection": "MLE",
  "user_question": "what does MLE mean?"
}
```

Sidekick renders `> quoted: "MLE"` above the AI reply so user sees what AI is answering about.

### D. Productive-failure inversion

Default render order in DOM: DRILL → WORKED → DEFINITION → CHECK.

Cards 1-3 (WORKED, DEFINITION, CHECK) start in `locked` state with body hidden, header showing `🔒 attempt drill first`.

When drill grader returns "correct" → unlock all three with staggered slide-in (240ms each, 80ms stagger).

If user clicks `give up — show solution` → mark drill `attempted-not-solved`, unlock the three, but FSRS card synthesized at grade `AGAIN` (1) instead of grade `GOOD` (3). Different FSRS payload, no false-positive.

**Hint progression:** 3 levels, one per click. H1 = conceptual nudge ("what does the sum represent?"), H2 = directional ("try μ=8 vs μ=9, which sum is smaller?"), H3 = method dump (current single-hint behavior). Track in `ProblemProgress.hintsUsed` for FSRS difficulty modifier.

### E. LLM grader

`POST /api/v1/drill/grade`:
```json
{
  "task_id": "01...",
  "problem_id": "A3",
  "user_attempt": "μ̂ = 8 because median of (3,7,8,9,14)",
  "expected_answer_hint": "median equals 8; mechanism: argmin Σ|x_i - μ| at sample median"
}
```

Returns:
```json
{
  "correct": true,
  "rubric": { "numeric": true, "mechanism": true, "justification": true },
  "score": 1.0,
  "misconception": null,
  "elaborated_feedback": "✓ correct. Mechanism cited (median). Reasoning: Σ|x_i − μ| is minimized at the sample median because each term is V-shaped piecewise linear; sum of V-shapes has its kink-driven minimum at the median order statistic."
}
```

Misconception parser (server-side prompt):
- numeric is mean (8.2) → `"L2 estimator confusion"`: "you computed the mean (the L2 / Normal MLE), but Laplace's |·| is non-differentiable at μ = x_i, so argmin ≠ stationary point"
- numeric is midrange ((3+14)/2 = 8.5) → `"minimax confusion"`: "that's the minimax estimator, not MLE"
- numeric is mode → `"mode confusion"`: "mode = median for symmetric Laplace, but only when sample is symmetric"

Free-tier model OK (existing chain `meta-llama/llama-3.3-70b-instruct:free` → `z-ai/glm-4.5-air:free` → `openai/gpt-oss-120b:free`). Grader runs at most 1 LLM call per CHECK click; cached per (problemId, userAttempt) hash.

### F. FSRS review surface

**Routes (new):**
- `GET /api/v1/fsrs/due?limit=N` — returns due cards with `front, back, dueAt, currentDifficulty, currentStability, sourceTaskId`.
- `POST /api/v1/fsrs/{cardId}/grade` — body `{ grade: 1|2|3|4 }` → calls existing `KnowledgeFsrs.recordReview` + bumps `state_version`.
- `GET /api/v1/fsrs/forecast` — returns `{ tomorrow, week, month }` counts.

**UI (`/tutor/review`):**
```
┌─ JARVIS · REVIEW · 7 DUE · 🔥 12-DAY STREAK ──┐
│ CARD 1 of 7 · PS · Laplace MLE                │
│ ┌──────────────────────────────────────────┐  │
│ │ FRONT · click to flip                    │  │
│ │ What is the MLE of μ for Laplace(μ, b)?  │  │
│ │       [SHOW ANSWER]   [skip]             │  │
│ └──────────────────────────────────────────┘  │
│   on flip ↓                                   │
│ [AGAIN ~10m] [HARD ~1d] [GOOD ~4d] [EASY ~12d]│
│ FORECAST · tomorrow 4 · week 18 · month 41    │
└───────────────────────────────────────────────┘
```

**Card synth flow:** when CHECK card completes, server-side `GapPromotion.promote` extends to also mint cards from the WORKED EXAMPLE and DEFINITION snippets (one cloze per material claim). Currently 1 card per gap; new: 1-3 cards per problem.

**Streak:** kept (per user-preference history) but accompanied by "this week: 4/7 days" recovery framing per ADHD agent's note. Single emoji `🔥` retained.

### G. Full motion language

All v5.1 site animations preserved + new concept-illustration animations:

| # | Trigger | What moves | Duration / easing | Class |
|---|---|---|---|---|
| 1 | Drill correct | Cards 1, 2, 4 unlock with staggered slide+fade (80ms apart) | 240ms cubic-bezier(.2,.8,.2,1) | load-bearing |
| 2 | Card complete | Checkbox draws ☑, header → green | 180ms ease-out | load-bearing |
| 3 | Inner progress dot fills | Scale 0→1 radial | 160ms ease-out | load-bearing |
| 4 | Outer problem dot fills | Same as #3 + outer-strip flash | 200ms | load-bearing |
| 5 | Button press | scale + accent ring | 280ms ease-in-out | load-bearing-lite |
| 6 | FSRS card flip | 3D rotateY 0→180 | 400ms cubic-bezier(.4,0,.2,1) | load-bearing |
| 7 | Resource rail item → drawer | bg highlight + drawer slide-in | 220ms ease-out | load-bearing |
| 8 | Sidekick collapse / expand | max-height + chevron rotate | 200ms ease-out | decorative |
| 9 | Task complete (all problems done) | Title strike L→R + confetti burst | 520ms | load-bearing |
| 10 | Number line μ direct-drag | μ marker cx tracks pointer (not slider) | 16ms / frame (RAF) | load-bearing pedagogy |
| 11 | Sumplot marker tracks curve | cx, cy interpolated along piecewise curve via RAF | 16ms / frame | load-bearing pedagogy |
| 12 | Slope-counter "votes" | `points-left − points-right` integer animates as μ sweeps | live, no easing | load-bearing pedagogy |
| 13 | Σ-decomposition stacked bar | Each `\|x_i − μ\|` segment grows/shrinks as μ moves | 120ms ease-out per segment | load-bearing pedagogy |
| 14 | Plotly frame morphs (compare) | `Plotly.animate` between frames showing mean/median trajectory | 600ms cubic-bezier | load-bearing pedagogy |
| 15 | Inline ✨ ASK chip | Fade-in above selection | 100ms ease-out | load-bearing |
| 16 | Sidekick context-quote pop-in | `> quoted:` strip fades in with answer | 180ms | load-bearing |
| 17 | Hint reveal (3 tiers) | Each hint slides in below drill, prior hints dim | 200ms | load-bearing |
| 18 | Compile & Submit unlock | Bottom card slides up from below viewport | 400ms cubic-bezier | load-bearing |
| 19 | Sorting algo viz (PA) | Bar swap with translateX over 300ms ease-in-out | 300ms | load-bearing pedagogy (Slice 2 generalizes) |
| 20 | Graph traversal viz (PA) | Edge draws with stroke-dasharray reveal, 220ms per edge | 220ms / step | load-bearing pedagogy (Slice 2) |
| 21 | Recursion tree expand (PA) | Child nodes pop-in with stagger | 160ms / level | load-bearing pedagogy (Slice 2) |

Slice 1 ships #1–#18 (Tema A specific). #19–#21 specced now, built in Slice 2 cross-subject pass.

**Reduced-motion override:** complete coverage. Plotly `transition: { duration: 0 }`, RAF gated on `matchMedia('(prefers-reduced-motion: reduce)').matches`, `setTimeout(..., 800)` collapse skipped.

### H. Demo grading regex bug + giveUp() fix

Fixed in `E` above: replace regex with LLM grader. `giveUp()` flow grades as AGAIN, not GOOD.

### I. Sidekick is real

Replace placeholder `'(in the real app, the LLM answers here ...)'` with actual call to existing `JarvisToolset.chat` via `/api/v1/sidekick/ask` route. Free-tier OpenRouter chain + 5-layer prompt-injection scrubber already in place.

### K. Google OAuth 2.0 + REST replacement

**Problem:** `GwsEffector.kt` shells out to a `gws` binary that doesn't exist (`@googleworkspace/cli` is not a real npm package; verified by `gws auth login` returning "Command 'gws' not found" on VPS 2026-05-10). The existing `calendar_create_event` / `drive_search` / `gmail_create_draft` tools in `JarvisToolset.openAiToolDefs` will silently fail every invocation in Slice 1's drill workspace.

**Replacement:** new `jarvis.tutor.GoogleApiClient` Kotlin module using direct OAuth 2.0 + Google REST APIs:

```
jarvis.tutor.GoogleApiClient
├── OAuth2Token (data class: accessToken, refreshToken, expiresAt)
├── TokenStore (loads/persists from /opt/jarvis/data/google-token.json)
├── refreshIfExpired(token, clientCreds): OAuth2Token
├── calendar.events.insert(summary, startIso, endIso, calendarId="primary")
├── drive.files.list(query, pageSize)
├── gmail.users.drafts.create(to, subject, body, userId="me")
└── REST helper using java.net.http.HttpClient (already in classpath, no new deps)
```

OAuth scopes (minimal):
- `https://www.googleapis.com/auth/calendar.events` (Calendar event read+write)
- `https://www.googleapis.com/auth/drive.readonly` (Drive read; downgraded from full)
- `https://www.googleapis.com/auth/gmail.compose` (Gmail draft only — never send)

**One-time setup (user action, browser-required):**
1. Open https://console.cloud.google.com/apis/credentials
2. Create new project "Jarvis Personal Tutor" (or reuse existing)
3. Enable APIs: Calendar API + Drive API + Gmail API
4. Create OAuth client ID → application type: "Desktop app" → name: "jarvis-personal"
5. Download `client_secrets.json`
6. Add OAuth consent screen with the user's gmail (publishing status: Testing — only the user's own email is in test-users list)
7. SCP the JSON to VPS: `scp client_secrets.json root@46.247.109.91:/opt/jarvis/data/`
8. Run new `jarvis google-auth-bootstrap` CLI subcommand on user's PC (browser-capable):
   - Reads `client_secrets.json`
   - Spins up local `http://localhost:9999/callback` listener
   - Opens browser to OAuth consent
   - Receives auth code, exchanges for refresh + access token
   - Prints token JSON to stdout
   - User SCPs `google-token.json` to VPS at `/opt/jarvis/data/google-token.json`
9. Set `GWS_ENABLED=1` in `/opt/jarvis/.env`, restart service

**JarvisToolset wiring:** `dispatchCalendarCreate` / `dispatchDriveSearch` / `dispatchGmailDraft` (currently call `GwsEffector.run`) get redirected to `GoogleApiClient` methods. Tool descriptions in `openAiToolDefs` stay identical so the LLM doesn't need to relearn anything.

**`GwsEffector.kt` fate:** kept temporarily as a compat shim that delegates to `GoogleApiClient` so any other caller (none today) doesn't break. New code uses `GoogleApiClient` directly. `GwsEffector` deleted in a follow-up commit.

**Health surface:** existing `GET /api/v1/gws/status` route (Phase 7 deferral closer) renamed to `/api/v1/google/status`; reports `enabled`, `tokenPresent`, `tokenExpiresAt`, `tokenRefreshable`. Tutor settings page surfaces this so user knows when re-consent is needed (refresh tokens never expire unless user revokes, but token file could be deleted by ops).

**Failure modes:**
- Missing `google-token.json` → return structured error "GoogleApi disabled — run google-auth-bootstrap on your PC + scp the token to VPS"
- 401 on access token → auto-refresh via stored refresh_token + retry once; if refresh also fails, surface as "consent expired, re-run bootstrap"
- 429 rate limit → exponential backoff (3 tries, 1s/4s/16s)
- All errors include the upstream Google error code in the LLM-visible message so debugging is fast

### J. Daemon PC-boot autostart

**Problem:** the Rust `jarvis-daemon` (HMAC-gated effector at 127.0.0.1:7331) + reverse SSH tunnel both die on PC reboot/sleep. Effector tools then fail with BadGateway. Currently manual `cargo run` after every boot.

**Producer:** new `tools/install-daemon-autostart.ps1` PowerShell installer (admin-elevated):
1. Verify `cargo build --release` artifact at `daemon\target\release\jarvis-daemon.exe`.
2. Generate `tools/jarvis-daemon-task.xml` Windows Task Scheduler XML — triggers on user logon + system boot, runs as current user (not SYSTEM, so it inherits `~/.ssh/`), restart-on-failure 3× w/ 1-min delay.
3. `schtasks.exe /Create /TN "Jarvis Daemon" /XML jarvis-daemon-task.xml /F`.
4. Second task: `Jarvis Reverse SSH Tunnel` running `ssh -N -R 7331:127.0.0.1:7331 root@46.247.109.91 -i ~/.ssh/id_ed25519` with same triggers + restart policy.
5. Health probe: writes `~/.jarvis/daemon-autostart.log` on each fire so user can `tail` if it fails silently.

**Frontend surface:** new GET `/api/v1/daemon/health` polls the daemon via the tunnel; status pill in Tutor header turns green when reachable, amber when "tunnel up but daemon unresponsive", red when both dead. Single user, single PC — no multi-tenancy needed.

**Uninstall:** `tools/uninstall-daemon-autostart.ps1` runs `schtasks /Delete /TN "Jarvis Daemon"` + tunnel task.

**Caveat:** SSH key must already exist at `~/.ssh/id_ed25519` and be in VPS authorized_keys. If not present, installer fails fast with a clear error pointing user to setup. Not auto-generating keys (that requires another interactive trust step).

**User actions required (one-time, admin):**
1. `powershell -ExecutionPolicy Bypass -File tools\install-daemon-autostart.ps1`
2. Confirm in Task Scheduler GUI that both tasks show "Ready"
3. Reboot to verify auto-fire
4. Tutor header pill should go green within ~30s of login

## Data flow

```
User opens /tutor/?taskId=A
  ↓
GET /api/v1/tasks/A → joins task + task_prep
  ↓
If task_prep miss → fire async LLM extraction → return skeleton
                  → poll every 2s until ready
  ↓
Render: stepper from problemRefs, drill stack from drills_json, rail from rail_json
  ↓
User selects text in DEFINITION → ✨ ASK chip → click → sidekick opens with envelope
  ↓
POST /api/v1/sidekick/ask → JarvisToolset.chat with envelope as system context
  ↓
Reply renders with > quoted: "..." prefix
  ↓
User attempts drill → POST /api/v1/drill/grade → LLM rubric
  ↓
If correct → cards 1, 2, 4 unlock animated (anim #1)
           → progress dot fills (anim #3)
           → if last card of problem → outer dot fills (anim #4) + scroll-snap to next
           → on last problem → COMPILE & SUBMIT unlocks (anim #18)
  ↓
User clicks SAVE TO FSRS → POST /api/v1/fsrs/synth → 1-3 cards minted from problem
  ↓
Later: user opens /tutor/review → GET /api/v1/fsrs/due → flip card → grade
```

## Error handling

- PDF extraction fails → fall back to legacy single-problem render. Surface "couldn't read your PDF; using generic Laplace template" toast.
- LLM grader 5xx / timeout → fall back to permissive heuristic (any digit + median/mean keyword), tag attempt as `ungraded`. Don't auto-pass.
- Sidekick LLM 5xx → render `(LLM unavailable; rate-limited?)` instead of fake reply.
- FSRS due fetch fails → show `(can't reach review queue)` instead of empty state to distinguish from "0 due".

## Testing

Backend (Kotlin):
- `PdfProblemExtractor` golden-file test on a fixture homework PDF
- `task_prep` table migration test (existing rows + new column)
- `/api/v1/drill/grade` LLM-mocked: tests for misconception parsing branches
- `/api/v1/fsrs/due` returns due cards, `/api/v1/fsrs/{id}/grade` updates state
- Existing `GapPromotionTest` extended to mint multiple cards per problem
- `/api/v1/daemon/health` returns reachable/tunnel-only/unreachable based on probe
- `GoogleApiClient.refreshIfExpired` handles expired access token + refresh
- `/api/v1/google/status` returns enabled/tokenPresent/expiresAt/refreshable
- `GoogleApiClient` integration tests (mocked HTTP server returning canned Google responses for each endpoint shape)

Frontend (Vitest):
- `DrillStack` renders DRILL first, others locked
- LLM grader correct path → 3 staggered unlocks fire
- LLM grader misconception → elaborated feedback appears under drill
- `giveUp()` grades as AGAIN
- Inline-help: text selection → chip appears → click → sidekick opens with quote
- FSRS review: flip → grade → next card
- Direct-drag μ marker on number line
- Σ-decomposition stacked bar updates live with slider
- Multi-problem stepper: navigation + URL sync

axe (Slice 2 generalizes): single new axe test for `DrillStack` and `FsrsReview` with reduced-motion mocked.

Daemon (Rust): unchanged.

Test count target after Slice 1: 599 backend + ~25 new = ~624 backend; 140 frontend + ~12 new = ~152 frontend. Daemon flat at 16. Total ~792.

## Migration

1. New `task_prep` table — `SchemaUtils.createMissingTablesAndColumns` adds it. Nullable, no data loss.
2. `Tasks.problemRefs` column added nullable. Existing rows stay singular until reprep'd.
3. Live VPS: `bash tools/deploy.sh` after merge. First task open triggers async PDF extract + populates `task_prep` row. Polling renders skeleton-then-real on completion.
4. Old `/tutor/?taskId=X` URLs without `?problem=N` continue to work — bare URL routes to lowest-incomplete problem.

## Sequencing within Slice 1

Implementation order (also the writing-plans task order):

1. Schema migration: `task_prep` table + `Tasks.problemRefs` nullable column.
2. `PdfProblemExtractor` + cache + `/api/v1/task/{id}/reprep` route. Test against real `Tema_A.pdf`.
3. `/api/v1/sidekick/ask` route + envelope shape. Wire `JarvisToolset.chat` properly. Replace placeholder.
4. `/api/v1/drill/grade` route + misconception parser. Mock + integration test.
5. `/api/v1/fsrs/due` + `/api/v1/fsrs/{id}/grade` + `/api/v1/fsrs/forecast` routes. Reuse `KnowledgeFsrs.recordReview`.
6. Frontend: new `DrillStack` component (replaces hardcoded cards). Multi-problem stepper. URL sync.
7. Frontend: inline help — text selection chip + per-paragraph gutter. Sidekick context-aware.
8. Frontend: invert order. Locked-by-default. Stagger unlock animation.
9. Frontend: real LLM grader call. Misconception → elaborated feedback strip.
10. Frontend: `/tutor/review` route. Flip card + 4 grade buttons + forecast.
11. Frontend: concept animations — direct-drag μ, sumplot RAF, slope-counter, Σ-stacked-bar, Plotly frame morphs.
12. Daemon autostart: `tools/install-daemon-autostart.ps1` + `tools/jarvis-daemon-task.xml` + `tools/uninstall-daemon-autostart.ps1` + `GET /api/v1/daemon/health` route + Tutor header status pill.
13. Google OAuth+REST: `GoogleApiClient.kt` + `TokenStore` + 3 REST clients (calendar/drive/gmail) + `jarvis google-auth-bootstrap` CLI subcommand + redirect 3 dispatch methods in `JarvisToolset` + rename `/api/v1/gws/status` → `/api/v1/google/status`.
14. Wire end-to-end against Tema A. Manual dogfood pass. Bugfix.
15. Backend tests. Frontend tests. axe.
16. Deploy. Verify bundle hash. Backlog Slice 2 items.

Estimated 12-17 hours for one Claude Code session running the writing-plans → subagent-driven-development pipeline.

## Self-review (per skill)

- Placeholder scan: zero `TBD` / `TODO` in this doc; all sections concrete.
- Internal consistency: drill order DRILL → WORKED → DEF → CHECK consistent across §B, §D, §G. URL `?problem=N` consistent §B + data flow.
- Scope check: 12-step implementation order, ~8-12h. Slice 2 explicitly deferred to backlog. Single coherent push.
- Ambiguity check: PDF extraction fallback specified (§Error handling). Grader misconception list bounded (3 named cases). FSRS card synth count ranged (1-3 cards/problem).

## Backlog (Slice 2)

Lifted directly from the 19 specialist reports. Will be its own spec doc:

- a11y: `:focus-visible` global rule, `<div onclick>` → `<button>`, drawer focus trap + Esc + ARIA dialog, ARIA live on state changes
- mobile: `@media (max-width: 760px)` → stack rail as slide-over drawer, 44px touch targets, range-input thumb sizing for `pointer: coarse`
- subject-key alias: `subjectAlias.kt` mapping `SO` ↔ `SO&RC`; subject-confidence rollup uses normalized key
- material_paths backfill: gradle `:backfillMaterials` task running `HybridRetriever.search` per pre-existing task
- viz-plugin refactor: `<viz type="...">` dispatcher with renderers for `mermaid | code-runner-wasm | sorting-bars | recursion-tree | gantt | ladder-diagram`
- typography: `.card-body p { margin: 0 0 .9em }` + `.katex-display { margin: 1.4em 0 1.6em }` + display math wrapped in own `<p>`
- visual hierarchy: 3-color palette discipline, 2-tier border, 2-tier letter-spacing, drop emoji from chrome
- performance: lazy Plotly via IntersectionObserver, OR migrate to uPlot, restyle vs react, persistent SVG nodes vs innerHTML rewrite, prerender KaTeX server-side
- onboarding: 90s coachmark on first launch covering `FSRS DUE`, `PRIOR GAP %`, drill stack flow
- Romanian register: `verifică`/`salvează`/`marchează rezolvată` for academic verbs, keep tech terms English
- streak gamification opt-out toggle in settings
- Plotly Boeing → uPlot or featherweight alt
- nav pills onclick wiring + free-chat surface + settings cog
- inline-help context envelope schema documented for LLM-side
- prefers-reduced-motion plumbing complete (Plotly + RAF + setTimeout)
- focus-mode default on (ADHD-safer) — toggleable

## Sequence vs the Devil's Advocate pushback

The skeptic agent argued: drill stack might be over-engineered, rail is theater, animations are anti-pedagogy, FSRS is premature. User chose `Slice 1 end-to-end (recommended)` over `Devil's advocate first — reconsider drill-stack`. Position acknowledged, deferred to post-Slice-1 dogfood. If Tema A end-to-end via this design feels worse than a pure chat, we revisit drill-stack scope before Slice 2.
