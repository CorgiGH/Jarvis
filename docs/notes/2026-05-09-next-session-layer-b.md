# Next session — Layer B start

**Status:** Layer A of jarvis-tutor SHIPPED 2026-05-09. 26 tasks landed across one autonomous run. 64 tests green. Tag `tutor/layer-a-acceptance` placed. Branch `feat/jarvis-tutor-layer-a` merged into `main` (no-ff, merge commit `8864ba4`). Pushed to GitHub remote `https://github.com/CorgiGH/Jarvis` — `main` + `feat/jarvis-tutor-layer-a` + tag all on origin.

This note is the entry point for whoever picks up Layer B next session.

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
2. **Spawn fresh constructive R3-style council** on Layer B specifically (not the full spec):
   - Devil's Advocate: design bugs in the daemon HMAC + read-only auto-trigger
   - Domain Expert: prior art for screenshot-as-sensor + clipboard-as-effector (Continue.dev, Aider, Vimium, Tauri)
   - Pragmatist: build order — daemon first or vision sensor first? Where's the spine?
   - Risk Analyst: shadow-git ordering bug (must be SYNCHRONOUS pre-commit), kill-switch resilience, prompt-injection defense layer ordering
   - First Principles: load-bearing decisions vs cosmetic ones (e.g. is keyboard injection truly v1 or can clipboard hold the line for longer?)
3. **Write Layer B plan** at `docs/superpowers/plans/YYYY-MM-DD-jarvis-tutor-layer-b.md` — bite-sized TDD tasks, mirroring Layer A plan structure.
4. **Execute via subagent-driven-development skill.** Pre-build gate equivalent for Layer B is daemon-HMAC fuzzer (replay-attack surface) + shadow-git ordering test (every effector pre-commit MUST land before extension applies edit).

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

## Layer A council transcripts

- R1 (build/no-build): `.claude/council-cache/council-1778274789.md` — 5/5 REJECT, dismissed by user
- R2 (constructive design): `.claude/council-cache/council-1778275450-r2.md` — 5/5 CONDITIONAL with concrete improvements
- R3 (Plan A vs Option 2 distribution): inline in conversation history — 2/3 PLAN_A, 1/3 NEITHER (meta). Plan A confirmed. React Router baked into Layer A per Devil's Advocate flag.

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
