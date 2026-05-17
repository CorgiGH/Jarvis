---
date: 2026-05-17
session: continue-from-bridge
status: 2 of 3 audit-flagged backend issues triaged; 1 production fix shipped, 1 hand-off, 1 false-positive
---

# Backend bug recon — 2026-05-17

Followup to the BRIDGE 2026-05-17T05:30 hot-work item #1. Audit-2026-05-17 flagged three HIGH `first-paint-http-error` findings on backend endpoints. Triaged each against the live VPS.

## Probe environment

- Live URL: `https://corgflix.duckdns.org`
- Auth: full chain (`jarvis_auth` + minted `jarvis_session` + `csrf` + `X-CSRF-Token`) via `/api/v1/tutor/auto-session` mint pattern from `tools/seed-tutor-events.mjs:93`
- Logs: `/var/log/jarvis.log` on VPS

## S-04 — POST `/api/v1/task-detect/run` → HTTP 500

**Root cause #1 (FIXED in production):** `AccessDeniedException` on `/opt/jarvis/data/archival/study-guide/docs/superpowers/specs` from a **background coroutine loop**, not the request handler itself. `ProactiveLoop.considerSync(ProactiveLoop.kt:114)` → `ActiveDoc.detectDrift` → `ConceptCatalog.all(KnowledgeState.kt:207)` walks the archival tree, hits permission boundary, crashes worker. Loop runs every drift-detect tick.

15 directories under `/opt/jarvis/data/archival/` had owner `jarvis` but mode `drw-r--r--` (missing execute bit on owner). Directories without execute are unenterable even by owner. Fix shipped:

```
chmod u+rx,go+rx <dir>...
```

Affected dirs (all under `/opt/jarvis/data/archival/`):
- `study-guide/docs/superpowers`, `study-guide/wiki/raw`, `study-guide/wiki/raw/notes`
- `study-guide/proxy`, `study-guide/src` (+5 nested `.curate` subdirs)
- `study-guide/.claude`, `study-guide/scripts`
- `second-brain/.claude`

Verification: `sudo -u jarvis ls /opt/jarvis/data/archival/study-guide/docs/superpowers/specs` now returns dir listing instead of `Permission denied`.

**Root cause #2 (NEEDS BACKEND REPO):** After clearing root cause #1 and restarting jarvis (+ installing `slf4j-simple-2.0.16.jar` to get logging — previously SLF4J was NOP and silently ate exceptions), the 500 persists. Real exception now visible:

```
ERROR io.ktor.server.Application - Unhandled: POST - /api/v1/task-detect/run
org.jetbrains.exposed.exceptions.ExposedSQLException:
  org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_UNIQUE]
  UNIQUE constraint failed: tasks.user_id, tasks.subject, tasks.title
SQL: INSERT INTO tasks (...) VALUES (...)
  at jarvis.tutor.TaskRepo.insert(Tasks.kt:99-100)
  at jarvis.web.TutorRoutesKt$installTutorRoutes$1$27$1.invokeSuspend(TutorRoutes.kt:1107)
  at jarvis.tutor.CsrfKt.csrfProtect(Csrf.kt:15)
```

Detector finds tasks already known to the DB. `TaskRepo.insert` does plain INSERT instead of UPSERT (INSERT OR IGNORE / ON CONFLICT). Every re-detection of a known task crashes the handler. Existing tasks in DB block re-detection entirely.

Fix locations (backend repo, not in jarvis-kotlin/ working tree):
- `jarvis.tutor.TaskRepo.insert(Tasks.kt:99-100)` — switch to `insertIgnore` or `upsert` with conflict clause on `(user_id, subject, title)`
- OR `jarvis.web.TutorRoutesKt$installTutorRoutes$1$27$1` at `TutorRoutes.kt:1107` — wrap insert in existence check or try/catch on the UNIQUE constraint specifically

Recommended: backend-side `insertIgnore` with a `existing++` counter so the response shape `{inserted, existing, total}` still works.

UI side (`tutor-web/src/components/ActiveTaskDashboard.tsx:50,155`) already handles errors gracefully — shows `detection failed: HTTP 500` in red. No frontend change needed once backend is fixed.

## S-12 — GET `/api/v1/tasks/01KR6TZ9NCA982XHCFM1VYK761/prep` → HTTP 404 (POO C1)

**FALSE POSITIVE.** Endpoint returns **HTTP 200** when probed with full auth:

```json
{
  "taskId": "01KR6TZ9NCA982XHCFM1VYK761",
  "generatedAt": "2026-05-17T02:12:55.277Z",
  "version": 1,
  "problemsJson": "[]",
  "drillsJson": "{}",
  "railJson": "[{\"type\":\"PDF\",\"label\":\"poo_c1.pdf p.1\",...}]"
}
```

POO C1 has empty problems/drills (per BRIDGE `hallucination triggers`: "PS Tema A is the ONLY live task with rubricItems populated; other 3 have empty drills"). That's expected behavior, not a 404.

The audit ran against this endpoint and got 404 — which means the audit tool's cookie injection didn't fully cover this endpoint. Hand-off to **Task #2 (audit tool refinements)** — fix cookie/session handling for prep endpoint probes.

## S-29 / S-30 / S-07 — GET `/api/v1/tasks/PS-Tema-A/prep` → HTTP 404

**Spec bug, not backend bug.** State matrix in `docs/superpowers/specs/2026-05-17-slice15-audit-design.md` uses the literal placeholder `PS-Tema-A` as a task ID in spec rows S-29, S-30, S-07. Real ID is `01KR6K07T6PATPRR5KH1JXYF8E`. Already flagged in BRIDGE hot-work #4 "S-29 spec uses literal PS-Tema-A". Fix in **Task #2**.

## Side effects of this recon

1. **`chmod u+rx,go+rx` on 15 archival dirs** — production, owner-only-fix, no security regression (perms were unintentionally broken; now restored to standard 755-style for owner).
2. **Installed `slf4j-simple-2.0.16.jar` at `/opt/jarvis/jarvis-kotlin/lib/`** and patched `/opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin` to include it on classpath. Previously SLF4J was NOP — Ktor's StatusPages exceptions vanished silently. Now `[main] INFO io.ktor.server.Application`, `[Exposed]` queries, and `Unhandled:` exceptions all log to `/var/log/jarvis.log`. Will be wiped on next jar redeploy; document in backend-repo build script or accept the boot patch.
3. **`systemctl restart jarvis`** twice during recon (perm fix application + slf4j install). Both clean; jarvis came up healthy within 6s each time.

## Recommended next actions

| Action | Where | Owner |
|---|---|---|
| Switch `TaskRepo.insert` to upsert / insertIgnore | backend repo `Tasks.kt:99-100` | user |
| Bake `chmod u+rx` into archival sync / curation flow so this doesn't recur | tools repo OR ops doc | user |
| Bake `slf4j-simple` into the jarvis-kotlin build (gradle dep) | backend repo `build.gradle.kts` | user |
| Fix audit-spec S-12 / S-29 / S-30 / S-07 task IDs + cookie handling | this repo Task #2 | next |
