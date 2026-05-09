# Next session — Layer B start

**Status:** Layer A of jarvis-tutor SHIPPED 2026-05-09. 26 tasks landed across one autonomous run. 64 tests green. Tag `tutor/layer-a-acceptance` placed. Branch `feat/jarvis-tutor-layer-a` merged into `main` (no-ff, merge commit `8864ba4`). Pushed to GitHub remote `https://github.com/CorgiGH/Jarvis` — `main` + `feat/jarvis-tutor-layer-a` + tag all on origin.

This note is the entry point for whoever picks up Layer B next session.

## Council history (must read before convening Layer B council)

User dismissed council R1 (5/5 REJECT), accepted council R2 (5/5 CONDITIONAL with concrete fixes — all incorporated). Council R3 (2/3 PLAN_A, 1/3 NEITHER) ran mid-Layer-A on plan distribution, picked Plan A + baked React Router into Layer A as adjustment.

**Implication for Layer B council:** the Layer B council is a CONSTRUCTIVE review of Layer B specifically. Do NOT re-open the Layer A "should this exist at all" question — that has been settled and dismissed by the user twice. The user has demonstrated willingness to override unanimous reject when framing is wrong; matching this in advance saves a round.

Durable transcripts on disk:
- R1 (build/no-build): `.claude/council-cache/council-1778274789.md` — 5/5 REJECT, dismissed
- R2 (constructive design): `.claude/council-cache/council-1778275450-r2.md` — 5/5 CONDITIONAL, all fixes incorporated
- R3 (Plan A vs Option 2 + Router timing): `.claude/council-cache/council-1778288445-r3.md` — Plan A + Router-in-Layer-A
- Wrap review (this note): `.claude/council-cache/council-r3-wrap-review.md` (added at session end)

## Scope fence + stop conditions (HARD)

**Authorization scope:** user said verbatim 2026-05-09 "next session starts work on layer B". Authorization is for Layer B SPECIFICALLY. When Layer B ships (tag `tutor/layer-b-acceptance` placed), authorization expires and the finals lock resumes. NO Layer C, NO parallel projects (Effortless-Paper, *arr, study guide), NO "while I'm here" refactors. If Layer B doesn't fit in the granted session(s), pause and surface trade-off explicitly to user — do NOT silently extend.

**Stop conditions during Layer B (any one triggers immediate pause):**
- User signals PS HW pressure ("panicking on PS", references to Tema A, asks about probability, etc.) → STOP, surface trade-off, hand control back
- Any council on a Layer B sub-task returns 5/5 REJECT with new arguments not seen in R1 → STOP, surface to user
- Layer B doesn't ship within ~2 sessions → STOP, surface progress, ask user whether to continue or pivot
- Vision-LLM provider chain proves incompatible (see prerequisites below) → STOP, decide pivot OR provider work BEFORE continuing

**Borrowed time framing:** PS HW deadline 2026-05-21 is 12 days out from session start. Layer B work is borrowed against PS HW prep time. The user dismissed this concern explicitly twice via councils; resume protocol honors that decision but stays vigilant for the user reversing themselves under PS pressure.

## What landed in Layer A

**Foundations only.** Schemas + contracts + auth correct day 1. Minimal usable workspace. No sensors. No effectors. No grade flow. No FSRS scheduling. No drift integration.

### Schemas (all multi-tenant via `userId`, all SQLite-WAL backed unless noted)

- `User` (`UsersTable`) — owner/friend, name, timestamps
- `Token` (`TokensTable`) — SHA256-hashed bearer tokens, issued/revoked
- `Session` (`SessionsTable`) — opaque sid, 14-day TTL, server-allocated, never raw token
- `Task` (`TasksTable`) — ULID, subject, deadline, problemRef/conceptRefs/rubricRef triples (repo+path+sha), scratchpad/submission/grade/cardRefs slots, status enum
- `SensorEvent` (`SensorEventsTable`) — LSP-shaped, sealed `SensorPayload` variants (TextDocSnapshot/ConsoleEvent/WindowFocus/ClipboardEvent/ScreenshotMeta), monotonic seq per sensor
- `TrustGrant` (`TrustGrantsTable`) — per-grant scope+ops+TTL+maxCalls, no blanket trust-class toggle
- `AuditLine` (`AuditLinesTable`) — monotonic seq per user, hash-chained via SHA256 of `prevHash + canonical-line`
- `KnowledgeGap` (`knowledge_gaps_<userId>.jsonl` JSONL ledger + `KnowledgeGapsTable` SQLite index) — append-only ledger, indexed for fast lookup, reuseCount + fsrsCardId pre-allocated
- `FsrsCard` (`FsrsCardsTable`) — TutorCard with FsrsState (difficulty/stability/retrievability/dueAt/lastReviewedAt/lapses), source enum + sourceRef
- `UserProviderConfig` (`ProviderConfigTable`) — primary provider + relay endpoint + encrypted apiKey ref + fallback list

### Contracts

- `ApplyEditRequest` LSP-shaped (`{taskId, effectorId, targetUri, expectedDocVersion, edits, nonce, grantId}`) with `TextEdit { range, newText }` and `Range/Position` 0-indexed line/column
- `EffectorType { APPLY_EDIT, RUN_R, NAVIGATE, INSERT_SCRATCHPAD }`
- `Outcome { SUCCESS, REJECTED, ROLLED_BACK, STALE_DOC, PATH_DENIED }`
- `EffectorValidator` 6-rule pipeline:
  1. Hardcoded path blocklist (`.ssh/`, `.git/`, `.env*`, `*.key`, `*.pem`, `~/.aws/`, `~/.config/`, `~/.kube/`) — always wins
  2. Grant exists + active + owned by user + op in allowlist
  3. Path matches grant scope glob (`**` = any path, `*` = single segment)
  4. `expectedDocVersion` matches current
  5. Nonce not in 1000-entry replay-defense ring buffer
  6. Grant `tryConsume` (atomic, enforces `maxCalls` cap)

**Effector contract fuzzer (Task 13, the pre-build gate)** — 2100 randomized validations green: 500 valid pass, 200 stale-doc reject, 200 path-outside-scope reject, 100 replayed nonces reject, 1000 random shuffles never approve invalid.

### Auth

- `/auth/setup?t=<raw-token>` validates token via `TokenRepo.findUserIdByToken` (SHA256 hash lookup), creates 14-day session via `SessionRepo.create`, sets two cookies:
  - `jarvis_session=<sid>` — `HttpOnly; Secure; SameSite=Strict; Path=/; max-age=14d`
  - `csrf=<16-byte-hex>` — `Secure; SameSite=Strict; Path=/; max-age=14d` (NOT HttpOnly so JS can read for double-submit)
- Redirects to `/tutor/`
- CSRF middleware (`csrfProtect`) compares cookie to `X-CSRF-Token` header on mutating routes, rejects 403 on missing/mismatch

### Frontend (tutor-web)

- Vite 5.4 + React 19 + Tailwind v4 + KaTeX 0.16 + React Router 7
- `BrowserRouter basename="/tutor"` baked in from day 1 (per council R3 — avoids mid-stream Router intro at Layer B)
- Single registered route in Layer A: `/` → `<App />` → `<TutorWorkspace pdfUrl="/test-task.pdf" taskId="TEST-TASK-A" />`
- `<TutorWorkspace>` = `grid-cols-2 h-dvh` (left = `<PdfPane>`, right = `<ChatPane>`)
- `<ChatPane>` wired to existing `/api/chat` via `jarvisFetch` from `lib/api.ts` (cookie + CSRF)
- TS schema mirrors in `lib/types.ts` (Task, ContentRef, ApplyEditRequest, etc.) match Kotlin shapes
- Bundle builds into `src/main/resources/tutor-dist/` via Vite — Ktor `staticResources("/tutor", "tutor-dist")` serves it

### Backend wiring

- `Application.installTutorContext(dbPath, ledgerDir)` — creates dirs, opens `TutorDb` (WAL + synchronous=NORMAL + foreign_keys=ON via direct JDBC before Exposed connects to avoid PRAGMA-in-transaction error), runs `SchemaUtils.create` for all 10 tables, attaches `TutorContext(db, ledgerDir)` to `application.attributes[TutorContextKey]`
- `Application.installTutorRoutes()` — adds `/tutor/*` static, `/api/v1/health`, `/auth/setup`
- WebMain bootstrap calls `installTutorContext` then `installTutorRoutes` from inside the existing `embeddedServer { ... }` block
- WebMain auth-interceptor allowlist extended for `/tutor`, `/api/v1/health`, `/auth/*` so the bearer-token gate doesn't pre-empt the new routes

### Config additions (`Config.kt`)

- `tutorDbPath` — `JARVIS_TUTOR_DB` env or `~/.jarvis/tutor.db`
- `tutorLedgerDir` — `JARVIS_TUTOR_LEDGER_DIR` env or `~/.jarvis/tutor-ledgers`

## Verification harness

- `gradle -p C:/Users/User/jarvis-kotlin :test --tests "jarvis.tutor.*"` — 23 test classes, **64 pass / 0 fail / 0 error / 0 skip**
- `cd tutor-web && npm test` — 6 frontend tests pass (api.ts + App + TutorWorkspace)
- `tools/smoke-tutor.sh` — curl `/api/v1/health` + `/tutor/` against running app
- `LayerAAcceptanceTest` — full end-to-end: seed user → issue token → `/auth/setup` (302 + cookies) → `/tutor/` (200 + root div) → `/api/v1/health` ok → audit chain valid → validator rejects `/etc/passwd` → multi-tenant isolation

## Dev-loop commands (Layer B will need these)

- **Backend dev:** `gradle -p C:/Users/User/jarvis-kotlin :run` — starts Ktor on port from `Config.port` (default 8080 unless `JARVIS_PORT` env set; check `Config.kt`)
- **Frontend dev (hot reload):** `cd C:/Users/User/jarvis-kotlin/tutor-web && npm run dev` — Vite on `:5173`, proxies `/api` to backend (configured in `vite.config.ts` — proxy target = `http://localhost:7331` currently; UPDATE if backend port differs)
- **Bundle for prod:** `cd C:/Users/User/jarvis-kotlin/tutor-web && npm run build` — emits to `src/main/resources/tutor-dist/`, picked up by Ktor `staticResources` on next backend start
- **Full smoke (backend running):** `bash tools/smoke-tutor.sh` (use `HOST=http://localhost:8080 bash tools/smoke-tutor.sh` if port differs)

## Layer B prerequisites (CHECK BEFORE STARTING IMPL)

These are the load-bearing assumptions Layer B is built on. If any fails, surface to user BEFORE writing the plan:

1. **Vision-LLM provider chain compatibility** — Layer B Task 1 is the screenshot sensor. It posts an image to `/api/v1/sensor/screenshot` expecting structured extraction (`{file_path, cursor, console_output, error}`). Memory says `JARVIS_LLM=fallback` (relay → claude-max-relay → copilot CLI). Verify:
   - Does the PC-side Tailscale relay forward Anthropic `image` content blocks intact? Check `RelayLlm.kt` + relay server impl.
   - If relay is text-only: pivot options = (a) bring back OpenRouter for vision-only chat path, (b) require `claude` CLI on VPS (currently NOT installed per memory), (c) skip vision sensor for Layer B and rely on editor extensions in Layer D.
   - This is a Council R3-style decision point — convene if relay isn't vision-capable.

2. **Tauri 2.x + Rust toolchain** — Layer B Task 2 (daemon) needs `rustup` + Visual Studio C++ Build Tools on Windows (~3GB download + user prompts). User environment has neither installed. Confirm with user before scaffold task. Pivot if user prefers: clipboard-only effector v0 ships without daemon and gives 95% of value safely.

3. **Code-signing certificates for daemon** — explicit `CAN'T-without-user`. Sideload-only distribution is fine for v0; only matters if user later wants to share daemon with friends.

4. **Browser screen-capture permission** — `getDisplayMedia` requires user gesture + per-session permission grant. Test in Chromium first; Firefox + Safari may differ. Plan should include a "permissions denied" graceful path.

5. **Backend port reconciliation** — `vite.config.ts` proxies `/api` to `http://localhost:7331`. Memory + production config use Ktor on `:8080`. These must agree. Check + fix in Task 0 of Layer B plan if mismatched.

## VPS deploy rule (HARD)

**DO NOT deploy Layer A or any Layer B partial to corgflix.duckdns.org during the Layer B build.** Reason: Layer B includes a Tauri daemon binding `127.0.0.1` on the user's PC. Deploying half-built Layer B to VPS where there's no PC daemon = broken UX (sensor screenshots fail, effectors fail, gap-fill workflows can't write to clipboard). VPS is single-instance shared between all sessions of jarvis backend; partial deploys break the existing prod surface.

**Deploy at end of Layer B as part of acceptance testing**, not before. Existing pre-tutor build on VPS keeps working as-is during Layer B. Acceptance criteria for VPS deploy: Layer B tag placed + manual smoke against `tutor.corgflix.duckdns.org` (or wherever the new tutor surface lives — confirm with user before exposing under a new subdomain).

If user EXPLICITLY asks to dogfood Layer A on VPS during Layer B build (e.g. "let me see the workspace shell on my phone"), build a separate `feat/layer-a-deploy` branch from the `tutor/layer-a-acceptance` tag and deploy ONLY that — do NOT mix with Layer B WIP.

## Layer B scope (next)

Per spec at `docs/superpowers/specs/2026-05-09-jarvis-tutor-design.md` §4:

1. **Vision-LLM screenshot sensor** — browser `getDisplayMedia`, hotkey-triggered (`Ctrl+Shift+J`), POST to `/api/v1/sensor/screenshot`, vision LLM extracts `{file_path, cursor, console_output, error}`. Universal fallback for any app, no install.
2. **Local Tauri daemon** — cross-platform (Win/Mac/Linux) Tauri 2.x binary. Bind 127.0.0.1 only, DNS-rebinding `Host` header check, HMAC per call w/ OS-keychain secret, rate limit 10 keystrokes/sec + 100/min, hardcoded path blocklist mirrored from `EffectorValidator`, kill switch (Telegram `/jarvis stop` + `Ctrl+Alt+J` hotkey writes `~/.jarvis/KILL` + VPS-unreachable >30s fail-closed).
3. **Effectors v0+v1**:
   - v0 = clipboard write (universal, safe, user pastes Ctrl+V)
   - v1 = keyboard injection via daemon, scoped+audited, **shadow-git pre-commit blocks effector return** so every applyEdit is rollback-able via `git diff post-pre | git apply -R`
   - `~/.jarvis/shadow/<project>` is its own git repo (NOT worktree of user's project)
4. **Per-grant trust UI** — explicit always-confirm by default, trust grant rows are explicit DB rows w/ TTL (default 1h, max 8h), revoke = single-row delete. NO blanket trust-class toggle (architectural mistake per council).
5. **Read-only mode auto-trigger** — non-allowlisted sensor source (browser tab on stackoverflow.com etc.) → effectors auto-disabled w/ red `READ-ONLY MODE` badge in jarvis pane header.
6. **Knowledge gap inline card** (explicit + manual paths only this layer):
   - Explicit: chat classifier emits `[[gap: <lang>, <type>, <topic>]]` marker → frontend creates `KnowledgeGap` row + renders gap card
   - Manual flag: "I don't know this" button on any line in problem PDF or scratchpad → opens gap-fill chat scoped to clicked content
   - Card UI: yellow border, header w/ topic + source citation, body w/ markdown + code blocks, actions: `INSERT → SCRATCHPAD` (NOT user's editor — preserves generation effect), `MARK RESOLVED`, `SHOW DOCS`, `FLAG WRONG`
   - Source priority pipeline: past gap → local lesson → archival corpus → external doc → LLM grounded → LLM pure
7. **Suggested-edit cards** in chat — typed JSON `{id, type, payload, status}` rendered with APPLY/RUN/REJECT buttons (APPLY → daemon does the v1 effector path; REJECT just dismisses)

Implicit gap detection (via WATCHING) is **Layer C**, NOT Layer B. Same with cross-task reuse + Knowledge Ledger drawer + FSRS card promotion.

## Resume protocol

When you start the Layer B session:

1. **Read this note first.** Do NOT auto-read the entire spec or all prior memories — that's noise.
2. **Verify prerequisites** (see "Layer B prerequisites" section above). If any fails, surface to user BEFORE convening council.
3. **Spawn fresh constructive R3-style council** on Layer B specifically (not the full spec):
   - Devil's Advocate: design bugs in the daemon HMAC + read-only auto-trigger
   - Domain Expert: prior art for screenshot-as-sensor + clipboard-as-effector (Continue.dev, Aider, Vimium, Tauri)
   - Pragmatist: build order — daemon first or vision sensor first? Where's the spine?
   - Risk Analyst: shadow-git ordering bug (must be SYNCHRONOUS pre-commit), kill-switch resilience, prompt-injection defense layer ordering
   - First Principles: load-bearing decisions vs cosmetic ones (e.g. is keyboard injection truly v1 or can clipboard hold the line for longer?)
4. **Write Layer B plan** at `docs/superpowers/plans/YYYY-MM-DD-jarvis-tutor-layer-b.md` — bite-sized TDD tasks, mirroring Layer A plan structure.
5. **Execute via subagent-driven-development skill.** Pre-build gate equivalent for Layer B is daemon-HMAC fuzzer (replay-attack surface) + shadow-git ordering test (every effector pre-commit MUST land before extension applies edit).

## Plan sizing hint

Spec §4 has 7 numbered Layer B sub-items (vision sensor / daemon / effectors v0+v1 / per-grant trust / read-only mode / gap inline cards / suggested-edit cards). At Layer A's TDD density (~3-7 tasks per major component), Layer B plan should land around **20-50 tasks**. Calibration check after writing the plan:

- **Plan <20 tasks** → likely under-scoped, missing schema additions or test coverage. Re-check.
- **Plan 20-50 tasks** → in target range. Proceed.
- **Plan 50-80 tasks** → either Layer B is bigger than estimated OR Layer C scope smuggled in. Audit; Layer C work belongs in next layer.
- **Plan >80 tasks** → definite scope creep. Pause and resplit; cut anything not in spec §4.

## CAN'T-without-user list (preserved from Layer A)

- New outbound channels (Telegram, email, push) — must confirm
- OAuth flows + interactive auth
- Money-spend (Anthropic/OpenRouter/Tauri code-signing certificates)
- Anti-features (anything that would help user procrastinate; e.g. don't add YouTube allowlist or "study music" tooling)
- Force-push or destructive git ops on shared remote
- Friend onboarding (multi-user UI) — schema is ready but UI is deferred per spec §7

## Open follow-ups not addressed in Layer A

- 27-commit branch went directly into `main` via `--no-ff` merge. No PR review. If multi-developer collab is wanted, change protocol next time.
- `LayerAAcceptanceTest` exists but is NOT wired into a CI gate. GitHub Actions would catch regressions before merge. Defer to Layer D polish.
- VPS deploy: Layer A code is on GitHub `main` but NOT deployed to corgflix.duckdns.org. Existing jarvis service on VPS still runs the pre-tutor build. To deploy: `git pull` + gradle `:installDist` + scp + `systemctl restart jarvis.service`. Do this when Layer B is also ready, OR sooner if you want to dogfood the workspace shell against real R/PS task files.
- Test PDF in `tutor-web/public/test-task.pdf` is minimal placeholder. Real Tema A.pdf renders fine in browser `<embed>` but may want server-side proxying for cross-origin PDFs in Layer C.

## Layer A council transcripts (durable)

- R1 (build/no-build): `.claude/council-cache/council-1778274789.md` — 5/5 REJECT, dismissed by user
- R2 (constructive design): `.claude/council-cache/council-1778275450-r2.md` — 5/5 CONDITIONAL with concrete improvements (all incorporated into spec + plan)
- R3 (Plan A vs Option 2 distribution + Router timing): `.claude/council-cache/council-1778288445-r3.md` — 2/3 PLAN_A, 1/3 NEITHER (meta). Plan A confirmed. React Router baked into Layer A per Devil's Advocate flag — this IS the documented spec deviation.

**Spec deviations vs `docs/superpowers/specs/2026-05-09-jarvis-tutor-design.md`:**
- React Router lives in Layer A, not Layer B (per R3). Spec §3 + plan still describe Router as Layer B; treat that as draft-status drift, R3 transcript is the authoritative rationale.
- All other Layer A schemas + auth + workspace shell match spec §3 verbatim.

## Tag + branch state

```
$ git -C C:/Users/User/jarvis-kotlin tag -l 'tutor/*'
tutor/layer-a-acceptance

$ git -C C:/Users/User/jarvis-kotlin log main --oneline -5
8864ba4 Merge feat/jarvis-tutor-layer-a — Tutor Layer A complete (26 tasks, 64 tests green, fuzzer-validated)
175238e Tutor Layer A: acceptance test — token→setup→/tutor→/api+audit+validator green
7bc61e4 Tutor Layer A: bootstrap TutorContext (DB + schema migrate + ledger dir)
d898050 Tutor Layer A: hardcoded test PDF + smoke script + smoke test
aa3aea2 Tutor Layer A: workspace shell — split-pane PDF + chat
```

GitHub: <https://github.com/CorgiGH/Jarvis/tree/main>
