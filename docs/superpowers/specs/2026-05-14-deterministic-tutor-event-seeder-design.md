# Deterministic tutor-event seeder — design

**Date:** 2026-05-14
**Status:** Spec — pending implementation
**Councils:**
- `.claude/council-cache/council-1778785261.md` (verdict: WRONG APPROACH on the `:free`-model-roulette menu → decouple fixture-seeding from the persona; seed the X-fixture deterministically).
- `.claude/council-cache/council-1778787266.md` (verdict: WRONG APPROACH on the "what should Y optimize for" framing → Y is friction-discovery; X-fixture seeding stays on a deterministic non-LLM path; Y is never the event source).

## Problem

Surface X's golden fixture (`docs/standin-findings/golden/2026-05-13-bootstrap-traces.md`) needs real `drill_grade` events to draw traces from. The only producer of `drill_grade` events to date has been Surface Y's LLM persona — which has never completed a drill across ~10 runs, so the X-fixture has been blocked the whole time.

Both councils this session ruled that this coupling is the actual bug: producing a `drill_grade` event is a deterministic data-generation task and must not depend on a stochastic LLM agent. The fix is a small, deterministic, non-LLM seeder that produces real `drill_grade` events directly.

## Why this is its own spec

This seeder shares no code and no dependency with the larger Claude-CLI-provider + Surface-Y-naivety-hardening work (tracked separately as `2026-05-14-surface-claude-cli-provider-design.md`). It is the load-bearing downstream unblock and is small enough to ship fast on its own. Decoupled per the brainstorming decomposition step.

## What was investigated

The `drill_grade` event is produced by `POST /api/v1/drill/grade` (`TutorRoutes.kt:1580`). Findings that shape the design:

- **It is a real HTTP endpoint.** No need to drive the drill UI via Playwright — a direct POST reaches it.
- **CSRF is a double-submit cookie** (`Csrf.kt:8-16`): the handler only checks `header("X-CSRF-Token") == cookie("csrf")`, both non-blank. There is no server-side token store. A first-party tool controls both sides, so CSRF is trivially satisfiable by self-issuing a matching pair — it is **not** a blocker.
- **Auth** requires a valid `jarvis_session` cookie (`TutorRoutes.kt:1585`); an invalid/missing one returns `401 invalid session`.
- **The endpoint grades server-side** via `DrillGrader.grade()` through the server's own `OpenRouterChatLlm` (`TutorRoutes.kt:1606-1620`). The seeder therefore controls *what is submitted*, not the grade. The resulting event carries a *real* grader verdict — which is exactly what the X-fixture wants.
- **`X-Standin-Run: 1`** sets `isSynthetic = true` on the appended event (`TutorRoutes.kt:1602`, `1686`). Synthetic events are partitioned: `event-log-reader.mjs`'s `filterEvents` excludes them unless `include_synthetic: true` is passed. They reach the X-fixture only via deliberate hand-curation — confirmed safe by council-1778785261.
- **The event lands** via `TutorEventLog.GLOBAL.append(evt)` → `tutor_events.<date>.jsonl` in the server's private dir (`TutorEventLog.kt:76`). Append happens after the HTTP response and is wrapped in `runCatching` — a serialization failure is swallowed and never breaks the response.
- **Even on server-side LLM failure** the endpoint still responds `200` with an `UNGRADED`/`error`-status body and **still appends a `drill_grade` event** (with `status = "error"`). So an event lands regardless of the server's OpenRouter quota state.
- **`ApiDrillGradeRequest`** (`TutorRoutes.kt:1930-1940`) fields: `taskId`, `problemId`, `problemStatement`, `userAttempt`, `expectedAnswerHint` (all required); `language`, `referenceSolution`, `rubricItems`, `prediction` (optional).
- **No GET endpoint serves the drill request fields.** `problemStatement` / `expectedAnswerHint` / `rubricItems` / `referenceSolution` appear only as request *inputs*, never in any response body. So fetching the payload dynamically is not cheap — the seeder hardcodes it (decision (i); the dynamic-fetch alternative (ii) is explicitly deferred).

## Approach

A single Node ESM script, `tools/seed-tutor-events.mjs`, that issues direct authenticated `POST /api/v1/drill/grade` requests with hardcoded payloads.

The seeder is fully deterministic and uses no LLM in its own process. Each request the server grades server-side; the seeder's determinism is over *what is submitted*, not the grade returned.

### Component: `tools/seed-tutor-events.mjs`

- A hardcoded `ATTEMPTS` array. V1 ships **2 entries** for task `01KR6K07T6PATPRR5KH1JXYF8E` (the PS-Tema-A Laplace-sampling drill): one **known-good** R answer (a correct `rlaplace`/inverse-CDF histogram script) and one **known-bad** R answer (a deliberately wrong attempt — e.g. wrong distribution or missing the b-loop). The array shape makes adding more attempts trivial without restructuring.
- The shared `ApiDrillGradeRequest` template fields for that task (`problemId`, `problemStatement`, `expectedAnswerHint`, `referenceSolution`, `rubricItems`, `prediction`) are hardcoded. They are obtained **once during implementation** by capturing a real `POST /api/v1/drill/grade` request body from browser devtools while doing the drill on the live tutor; the seeder swaps only `userAttempt` per array entry.
- For each attempt: build the request, POST it, record the HTTP status + parsed reply.
- A `callOnce(attempt)` function does the single POST; the CLI loops it over `ATTEMPTS`.

### Request construction

Each POST sends:
- **Headers:** `Content-Type: application/json`, `X-Standin-Run: 1`, `X-CSRF-Token: <token>`, `Cookie: jarvis_session=<session>; csrf=<token>`.
- **`<token>`** is a fixed non-blank string the seeder self-issues (any constant works — the server only checks header == cookie).
- **`<session>`** comes from the `JARVIS_SESSION_COOKIE` env var (see Env contract). The event's `session_id` is derived server-side from this cookie value (`TutorRoutes.kt:1603`), so the seeder does not set `session_id` directly.
- **Body:** the `ApiDrillGradeRequest` JSON for the attempt.

## Env + CLI contract

```
JARVIS_SESSION_COOKIE   required — a valid jarvis_session cookie value (copy from browser devtools)
--base-url=<url>        optional — default https://corgflix.duckdns.org (matches the other Surface tools)
--task=<id>             optional — default 01KR6K07T6PATPRR5KH1JXYF8E (the only task with hardcoded payloads in V1; passing another id is a loud error until its payloads are added)
--dry-run               optional — build + print the requests, do not POST
```

- Unset `JARVIS_SESSION_COOKIE` → loud exit before any request.
- The script follows the existing `tools/` CLI conventions (the `process.argv[1]?.endsWith(...)` guard + `--key=value` parsing seen in `surface-z.mjs` / `surface-y.mjs`).

## Data flow

```
seed-tutor-events.mjs
  → POST /api/v1/drill/grade  (×N attempts, X-Standin-Run:1)
    → server: DrillGrader.grade() via OpenRouterChatLlm  (server-side grade)
    → server: TutorEventLog.GLOBAL.append(drill_grade event, is_synthetic=true)
      → tutor_events.<date>.jsonl   (VPS, /opt/jarvis/data/private, 0700)
        → event-log-reader.mjs readEvents({sshTarget}) + filterEvents({include_synthetic:true})
          → candidate traces for the hand-curated Surface X golden fixture
```

The event log lives on the VPS, so any read-back (acceptance check included) goes through `readEvents({ sshTarget: "root@46.247.109.91" })` — the same SSH target `surface-x.mjs:269` uses — or runs on the VPS directly.

The seeder's responsibility ends at "real `drill_grade` events exist in the log, readable with `include_synthetic:true`." Turning those events into golden-fixture traces is a separate, manual Surface X curation step — out of scope here.

## Error handling

| Condition | Behavior |
|-----------|----------|
| `JARVIS_SESSION_COOKIE` unset | Loud exit before any request, with the fix instruction. |
| HTTP 401 (`invalid session`) | Loud per-attempt failure: the session cookie is stale/invalid — re-copy from devtools. Non-zero exit. |
| HTTP 403 (`CSRF check failed`) | Loud failure. Should be unreachable (the seeder self-issues a matching pair); if it fires, the CSRF contract changed — surface it. Non-zero exit. |
| HTTP 400 (`malformed`) | Loud failure: the hardcoded payload no longer matches `ApiDrillGradeRequest`. Non-zero exit. |
| HTTP 200, reply `misconception: "UNGRADED"` / `status` error | The server's OpenRouter was unavailable. A `drill_grade` event **still lands** (with `status: "error"`) — a valid event shape, just less rich. Report it as a soft warning; do not fail the run. |
| HTTP 200, normal reply | Success. Report the `correct` / `score` from the reply. |

The seeder reports a per-attempt summary line and exits non-zero if any attempt hard-failed (401/403/400/network), zero otherwise (soft `UNGRADED` warnings do not fail the run).

## Testing

- **Unit test** (`tools/seed-tutor-events.test.mjs`, `node --test`): inject a mock transport (mirrors `tools/lib/openrouter.test.mjs`'s `fetch` mock). Assert: the request URL, the `X-Standin-Run` / `X-CSRF-Token` headers, the `Cookie` header carries both `jarvis_session` and a matching `csrf`, and the body is well-formed `ApiDrillGradeRequest` JSON for each `ATTEMPTS` entry. Assert the 401 / 403 / 400 / `UNGRADED` / success branches each map to the right exit behavior.
- **Wire** `seed-tutor-events.test.mjs` into `package.json`'s `test:tools` script.
- **Real acceptance** (manual, behavior-anchored — same precedent as `2026-05-14-surface-y-deterministic-actions-design.md`: a CLI tool, acceptance is behavior not selectors): run the seeder once against the live tutor, then confirm via `event-log-reader.mjs` `readEvents({ sshTarget: "root@46.247.109.91" })` + `filterEvents({ task_id: "01KR6K07T6PATPRR5KH1JXYF8E", event_type: "drill_grade", include_synthetic: true })` that **2 new `drill_grade` events** are present, one per attempt.

## Out of scope

- **Dynamic payload fetch (option (ii)).** No GET endpoint serves the request fields; reconstructing them would mean replicating the frontend's request assembly across 2+ endpoints. Deferred — revisit only if the seeder must span many tasks.
- **Multi-task seeding.** V1 is one task, 2 attempts. The `ATTEMPTS` array shape makes extension trivial later.
- **Golden-fixture curation.** The seeder makes events *exist*; selecting + hand-labelling them into `bootstrap-traces.md` is Surface X's manual job.
- **The Claude-CLI provider + Surface Y naivety-hardening.** Separate spec (`2026-05-14-surface-claude-cli-provider-design.md`).

## Acceptance criteria

- [ ] `tools/seed-tutor-events.mjs` exists; `JARVIS_SESSION_COOKIE` unset → loud exit before any request.
- [ ] `--dry-run` prints the 2 constructed requests (headers + body) and POSTs nothing.
- [ ] A live run against the tutor produces exactly 2 new `drill_grade` events for task `01KR6K07T6PATPRR5KH1JXYF8E`, both `is_synthetic: true`, visible via `event-log-reader.mjs` `readEvents({ sshTarget })` with `include_synthetic: true` and absent without it.
- [ ] 401 / 403 / 400 each produce a loud failure + non-zero exit; a soft `UNGRADED` reply is a warning, not a failure.
- [ ] `seed-tutor-events.test.mjs` passes and is wired into `package.json` `test:tools`; the full `test:tools` suite stays green.

## Noted aside (out of scope — flag for the Surface-CLI-provider spec)

The Surface tools (`surface-y.mjs:88`, `surface-z.mjs:60`) set a Playwright cookie named `jarvis_auth`, but `POST /api/v1/drill/grade` and the other authenticated routes read `jarvis_session` (`TutorRoutes.kt:1585`). This looks like a latent bug in the Surface tools — and could explain why Surface Y's runs never produced authenticated events even when the persona reached a gradeable action. The seeder sidesteps it by using `jarvis_session` directly. The discrepancy should be investigated as part of `2026-05-14-surface-claude-cli-provider-design.md`.
