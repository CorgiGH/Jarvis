# Deterministic tutor-event seeder — design (v2, auth-corrected)

**Date:** 2026-05-15
**Status:** Spec — pending implementation
**Supersedes:** `2026-05-14-deterministic-tutor-event-seeder-design.md` (v1 — auth model was wrong; see below)
**Councils (still binding, unchanged):**
- `.claude/council-cache/council-1778785261.md` — decouple X-fixture seeding from the stochastic persona; seed deterministically.
- `.claude/council-cache/council-1778787266.md` — Surface Y is friction-discovery only; the seeder is the event source, not Y.

## Why v2 exists

v1 shipped (commits `d85a44d`..`0ba7497`, 77 `test:tools` tests green) but **the seeder never actually authenticated** — every live run returned `401`. Root-caused 2026-05-15: v1's "What was investigated" section rested on three memory claims that were never verified against a running server, and all three were wrong:

1. **"the endpoint reads `jarvis_session`"** — wrong. `POST /api/v1/drill/grade` requires **three** cookies: `jarvis_auth` (the outer auth-token interceptor), `jarvis_session`, and `csrf`. Missing `jarvis_auth` → `401 missing or invalid auth token`; missing `jarvis_session` → `401 invalid session`.
2. **"CSRF is a double-submit cookie, any constant works"** — wrong. A self-issued matching `csrf` cookie + `X-CSRF-Token` pair returns `401 invalid session`. The `csrf` is server-validated and tied to the minted session, not a free double-submit.
3. **`TutorRoutes.kt:1585` / `Csrf.kt:8-16`** — unverifiable. There are **zero `.kt` files in this repo**; the backend source lives elsewhere (VPS or another repo). v1's line-number citations were written from memory.

v1's "Noted aside" even saw the `jarvis_auth` vs `jarvis_session` discrepancy and concluded the *Surface tools* were buggy for using `jarvis_auth` — exactly backwards. The Surface tools were right.

**This is a memory-verification-rule failure.** v2 is built only on facts verified end-to-end against the live server this session (curl truth-table + Playwright request capture).

## Verified facts (2026-05-15, against the live server)

The complete, working auth flow — every step confirmed:

```
jarvis_auth   ($JARVIS_AUTH_COOKIE  ||  tools/AUTH_TOKEN.txt — gitignored, present, 48 chars)
  │
  ├─ GET /api/v1/tutor/auto-session     Cookie: jarvis_auth=<token>
  │     → 200   Set-Cookie: jarvis_session=<64hex>; Max-Age=1209600; Secure; HttpOnly; SameSite=Strict
  │            Set-Cookie: csrf=<32hex>;          Max-Age=1209600; Secure;           SameSite=Strict
  │            body: {"ok":true,"userId":"owner","csrf":"<32hex>"}
  │
  └─ POST /api/v1/drill/grade
        Cookie:        jarvis_auth=<token>; jarvis_session=<minted>; csrf=<minted>
        X-CSRF-Token:  <minted csrf>
        X-Standin-Run: 1
        Content-Type:  application/json
        body:          ApiDrillGradeRequest JSON
        → 200 + a drill_grade event appended to the server's tutor_events log
```

Truth-table evidence (all against the real `POST /api/v1/drill/grade`, body `{}` to isolate auth from body-validation):

| credentials sent | result | meaning |
|---|---|---|
| `jarvis_session` + self-issued csrf, no `jarvis_auth` (= v1 seeder) | `401 missing or invalid auth token` | `jarvis_auth` is the outer gate |
| `jarvis_auth` + real `csrf` + `jarvis_session` + `X-CSRF-Token` | `400 malformed: Fields [...] required` | **auth + CSRF passed** — server reached body parsing |
| `jarvis_auth` + real `csrf`, no `jarvis_session` | `401 invalid session` | `jarvis_session` also required |
| `jarvis_auth` + self-issued csrf, no `jarvis_session` | `401 invalid session` | csrf is not a free double-submit |
| freshly-minted `jarvis_session`+`csrf` (from auto-session) + `jarvis_auth` + `X-CSRF-Token` | `400 malformed` | **the mint→grade chain works end-to-end** |

Other verified facts:
- `tools/AUTH_TOKEN.txt` exists, is 48 chars, and is gitignored (`.gitignore:10`). Four existing tools (`slice2-playwright-gate.mjs`, `slice2-prefetch-gate.mjs`, `surface-y.mjs`, `surface-z.mjs`) already read `jarvis_auth` via the `process.env.JARVIS_AUTH_COOKIE || readFileSync("AUTH_TOKEN.txt")` pattern. v2 follows that pattern.
- `GET /api/v1/tutor/auto-session` (it is a **GET** — POST returns `405`) needs only `jarvis_auth`; it mints `jarvis_session` + `csrf` with a 14-day `Max-Age`. The `csrf` value is also in the JSON response body, so no Set-Cookie parsing is required for it.
- `auto-session` returns `userId:"owner"` — single-user, correct for this deployment.

## Unverified — flagged for first-run confirmation

- **`X-Standin-Run: 1` → `is_synthetic = true`.** v1 claimed this (citing the missing `TutorRoutes.kt`). It was never confirmed (no v1 run ever got a 200). v2 keeps sending `X-Standin-Run: 1`, but the **live acceptance step MUST confirm** the resulting events actually carry `is_synthetic: true` (visible via `event-log-reader.mjs` with `include_synthetic:true`, absent without). If they do not, that is a finding to escalate, not a pass.

## What survives from v1 (keep, do not rewrite)

- `tools/seed-tutor-events.fixture.json` (commit `27ceb11`) — captured from a **real** `POST /api/v1/drill/grade` body via devtools; the template + 2 attempts are correct. Untouched.
- `buildRequest(template, attempt)` — correct; merges `userAttempt` into the fixture template.
- `classifyOutcome(httpStatus, reply)` — logic is correct (401→hard auth_error, 403→hard csrf_error, 400→hard bad_request, non-200→hard http_error, 200+null→soft ungraded, 200+UNGRADED→soft ungraded, else success). Only the 401 `detail` *wording* is updated (no longer "jarvis_session cookie").
- `loadFixture()` — correct.
- The CLI skeleton — `process.argv[1]?.endsWith(...)` guard, `--key=value` parsing, `--task` guard, the `[OK]`/`[WARN]`/`[FAIL]` result loop, exit codes — correct.
- The `node --test` harness and the `test:tools` wiring (v1 Task 5) — correct.

## What v2 changes

- **`buildHeaders`** — rewritten. New signature `buildHeaders({ jarvisAuth, jarvisSession, csrf })`. Emits `Cookie: jarvis_auth=<a>; jarvis_session=<s>; csrf=<c>`, `X-CSRF-Token: <c>`, `X-Standin-Run: 1`, `Content-Type: application/json`.
- **New: `loadAuthToken()`** — `process.env.JARVIS_AUTH_COOKIE?.trim() || readFileSync(<dir>/AUTH_TOKEN.txt).trim()`. Throws a typed error if neither is available; the CLI turns that into a loud exit.
- **New: `mintSession({ jarvisAuth, baseUrl, transport })`** — `GET ${baseUrl}/api/v1/tutor/auto-session` with `Cookie: jarvis_auth=<token>`. Returns `{ jarvisSession, csrf }`: `jarvisSession` parsed from the `jarvis_session=<value>` pair in `resp.headers.getSetCookie()`; `csrf` taken from the JSON response body's `.csrf` field. Maps `401`→a hard `auth_error` ("jarvis_auth invalid/expired — refresh tools/AUTH_TOKEN.txt"); any other non-200→a hard `mint_error`.
- **`seedOne`** — signature changes from `{ ..., sessionCookie, csrfToken }` to `{ ..., jarvisAuth, jarvisSession, csrf }`. Body otherwise unchanged (build request, build headers, POST via injectable `transport`, classify).
- **`seedAll`** — signature changes to `{ template, attempts, baseUrl, jarvisAuth, transport }`. New behavior: call `mintSession` **once**, then loop `seedOne` over `attempts` reusing the minted `{ jarvisSession, csrf }`. If the mint fails, return one hard-failure result per attempt with the mint's error detail — do not POST anything.
- **CLI entrypoint** — the credential step changes: `loadAuthToken()` replaces `process.env.JARVIS_SESSION_COOKIE`. The unset-cookie guard becomes an unset-`jarvis_auth` guard with the fix instruction ("set $JARVIS_AUTH_COOKIE or create tools/AUTH_TOKEN.txt"). `--dry-run` performs the mint (a harmless GET — it does create a 14-day server-side session, but that is benign and single-user) so it can print the *real* constructed POSTs, but **masks all secret values** in its output (`jarvis_auth`, `jarvis_session`, `csrf`, `X-CSRF-Token` → `<redacted:NN-chars>`) and POSTs nothing. (This also closes the v1 Task-4 code-review note that `--dry-run` printed the raw cookie.)

## Component layout (single file — `tools/seed-tutor-events.mjs`)

```
CONSTANTS        DEFAULT_BASE_URL, DEFAULT_TASK_ID, AUTH_TOKEN_PATH
loadFixture()           — unchanged
loadAuthToken()         — NEW: env || AUTH_TOKEN.txt; typed throw if neither
buildRequest()          — unchanged
buildHeaders({a,s,c})   — REWRITE: 3-cookie Cookie + X-CSRF-Token + X-Standin-Run
classifyOutcome()       — unchanged logic; 401 detail wording updated
mintSession({...})      — NEW: GET /auto-session → { jarvisSession, csrf }
seedOne({...})          — REWRITE signature: jarvisAuth + jarvisSession + csrf
seedAll({...})          — REWRITE: mint once, then loop seedOne
CLI entrypoint          — REWRITE credential step; --dry-run mints + masks
```

`mintSession` and `loadAuthToken` live in this file (not a `lib/`) per the chosen approach: it matches the codebase's existing per-tool-inline pattern, keeps the tool to one ~190-line file, and YAGNI — extracting a shared `lib/jarvis-auth.mjs` is a trivial later move if a second non-Playwright consumer appears.

## Data flow

```
seed-tutor-events.mjs
  loadAuthToken()                          → jarvis_auth   (env || tools/AUTH_TOKEN.txt)
  mintSession()  → GET /api/v1/tutor/auto-session   → { jarvisSession, csrf }   (once)
  seedAll → seedOne ×N:
    POST /api/v1/drill/grade  (jarvis_auth + jarvisSession + csrf + X-CSRF-Token + X-Standin-Run:1)
      → server: DrillGrader.grade()  (server-side grade — seeder controls WHAT is submitted, not the grade)
      → server: appends a drill_grade event (is_synthetic=true, per X-Standin-Run — to be confirmed)
        → tutor_events.<date>.jsonl  (VPS, /opt/jarvis/data/private)
          → event-log-reader.mjs readEvents({sshTarget}) + filterEvents({include_synthetic:true})
            → candidate traces for the hand-curated Surface X golden fixture
```

The seeder's responsibility ends at "2 real `drill_grade` events exist in the log, readable with `include_synthetic:true`." Curating them into the golden fixture is a separate manual Surface X step — out of scope.

## Env + CLI contract

```
JARVIS_AUTH_COOKIE   optional — jarvis_auth value; if unset, falls back to tools/AUTH_TOKEN.txt
                     (a loud exit only if BOTH are missing)
--base-url=<url>     optional — default https://corgflix.duckdns.org
--task=<id>          optional — default 01KR6K07T6PATPRR5KH1JXYF8E (the only task with a hardcoded
                     fixture in V1; any other id is a loud error)
--dry-run            optional — mint the session, build + print the requests with secrets MASKED,
                     POST nothing, exit 0
```

## Error handling

| Condition | Behavior |
|-----------|----------|
| `jarvis_auth` unresolved (no `$JARVIS_AUTH_COOKIE`, no `tools/AUTH_TOKEN.txt`) | Loud exit before any request, with the fix instruction. Exit 2. |
| `mintSession` → `401` | `jarvis_auth` is invalid/expired. Loud: "refresh tools/AUTH_TOKEN.txt". `seedAll` returns one hard `auth_error` per attempt; nothing is POSTed. Exit 1. |
| `mintSession` → non-200/non-401, or missing `jarvis_session` Set-Cookie / `csrf` body field | Loud hard `mint_error` — the auto-session contract changed. Nothing POSTed. Exit 1. |
| `drill/grade` → `401` | Session rejected mid-run (should not happen right after a successful mint). Loud, hard. Exit 1. |
| `drill/grade` → `403` | CSRF rejected — the csrf contract changed. Loud, hard. Exit 1. |
| `drill/grade` → `400` | The hardcoded payload no longer matches `ApiDrillGradeRequest`. Loud, hard. Exit 1. |
| `drill/grade` → `200`, reply `UNGRADED` / error status | Server OpenRouter was down. A `drill_grade` event **still lands** (`status:"error"`). Soft warning, not a failure. |
| `drill/grade` → `200`, normal reply | Success. Report `correct`/`score`. |
| network throw (mint or grade) | Hard `network_error`. Exit 1. |

The run exits non-zero if any attempt hard-failed or the mint failed; zero otherwise (soft `UNGRADED` warnings do not fail the run).

## Testing

Unit tests (`tools/seed-tutor-events.test.mjs`, `node --test`, injected mock `transport`):

- The mock `transport` now dispatches on URL: a `GET .../auto-session` branch returns `{ status:200, headers:{ getSetCookie:()=>["jarvis_session=S64; Max-Age=..."] }, json:async()=>({ok:true,userId:"owner",csrf:"C32"}) }`; a `POST .../drill/grade` branch returns the graded reply.
- `loadAuthToken` — env present; env empty + file fallback; neither (typed throw).
- `mintSession` — success (parses `jarvisSession` from Set-Cookie, `csrf` from body); `401` → hard `auth_error`; non-200 → hard `mint_error`; missing Set-Cookie / missing body `csrf` → hard `mint_error`.
- `buildHeaders` — emits the 3-cookie `Cookie` string in `jarvis_auth; jarvis_session; csrf` order, `X-CSRF-Token` = the csrf, `X-Standin-Run:1`, `Content-Type`.
- `buildRequest`, `classifyOutcome` — v1 tests carry over (classifyOutcome's 401-detail-string assertion, if any, updated to the new wording).
- `seedOne` — POSTs to the grade URL with the right method/body/headers (new 3-cookie shape); network throw → hard `network_error`; `401` → hard `auth_error`.
- `seedAll` — mints once then returns one result per attempt in order; **mint-failure path** → one hard result per attempt, zero grade POSTs (assert the mock's grade branch was never called).
- Wire stays in `package.json` `test:tools` (already done in v1 Task 5).

Real acceptance (manual, behavior-anchored — a CLI tool's acceptance is behavior):
1. `cd tools && node seed-tutor-events.mjs --dry-run` → prints 2 constructed POSTs with **masked** secrets, POSTs nothing, exit 0.
2. Unset `$JARVIS_AUTH_COOKIE` **and** temporarily confirm behavior with `tools/AUTH_TOKEN.txt` present → normal run; then (conceptually) both-missing → loud exit 2. (`AUTH_TOKEN.txt` is present in this workspace, so the happy path needs no env var.)
3. `cd tools && node seed-tutor-events.mjs` → mints, POSTs 2 attempts, prints `[OK]`/`[WARN]` lines, exit 0.
4. Read back via `event-log-reader.mjs` `readEvents({ sshTarget:"root@46.247.109.91" })` + `filterEvents({ task_id:"01KR6K07T6PATPRR5KH1JXYF8E", event_type:"drill_grade", include_synthetic:true })` → **exactly 2 new events**, and the same filter with `include_synthetic:false` → those 2 **absent** (this confirms both that the events landed AND the unverified `X-Standin-Run`→`is_synthetic` mapping).

## Out of scope

- Dynamic payload fetch — no GET endpoint serves the request fields; the fixture is hardcoded (and already captured/committed). Unchanged from v1.
- Multi-task seeding — V1 is one task, 2 attempts (`known-good` + `known-bad`). The `attempts` array shape keeps extension trivial.
- Golden-fixture curation — the seeder makes events *exist*; selecting/labelling them into `bootstrap-traces.md` is Surface X's manual job.
- Migrating the 4 other auth-needing tools onto a shared auth module — they each inline their `jarvis_auth` read today; leave them.
- The Claude-CLI provider + Surface Y naivety-hardening — separate spec.

## Acceptance criteria

- [ ] `tools/seed-tutor-events.mjs` resolves `jarvis_auth` from `$JARVIS_AUTH_COOKIE` or `tools/AUTH_TOKEN.txt`; **both** missing → loud exit 2 before any request.
- [ ] `mintSession` does `GET /api/v1/tutor/auto-session` and returns `{ jarvisSession, csrf }`; a `401` produces a loud "refresh AUTH_TOKEN.txt" hard failure.
- [ ] `--dry-run` mints, prints the 2 constructed POSTs with all secret values masked, and POSTs nothing.
- [ ] A live run produces exactly 2 new `drill_grade` events for task `01KR6K07T6PATPRR5KH1JXYF8E`, both `is_synthetic:true`, visible via `event-log-reader.mjs` with `include_synthetic:true` and absent without it.
- [ ] The `X-Standin-Run:1` → `is_synthetic:true` mapping is confirmed by that read-back (it was never verified in v1).
- [ ] `401`/`403`/`400`/mint-failure each produce a loud failure + non-zero exit; a soft `UNGRADED` reply is a warning, not a failure.
- [ ] `seed-tutor-events.test.mjs` passes and the full `test:tools` suite stays green.
