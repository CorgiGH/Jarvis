# Autonomous resume — next jarvis-kotlin session (2026-05-08+)

> Read this first. The user has explicitly authorized unsupervised work for the next session toward the north star in `docs/superpowers/specs/2026-05-08-agi-harness-vision.md`.

## Context handoff

**End-of-session state (2026-05-07):** 16 commits today — `f927f11` (mutex blocker) → `80f5aeb` (3 more tools: activity/stats/sub). Step B Letta-split + ChatTools 9-tool dispatch + Conversations-derived chat history all SHIPPED to corgflix.duckdns.org. Smoked end-to-end on VPS. Memory + index + repo wrap notes current.

**User's words (verbatim):** "wrap up this session and get ready for a lot of unsupervised work in a new session, i want you to add what you think is necessary without me, ask council for things, get a new agent for research when new features are needed, the goal is making a harness for a system that is smarter than me that can live on my phone and pc, monitor the things i do and have smart logic where it knows importance of things"

**The vision is in `docs/superpowers/specs/2026-05-08-agi-harness-vision.md`.** Read it before doing anything.

## What "unsupervised" means here (and what it doesn't)

You CAN, without asking:
- Convene `claude-council-lite` on any decision the spec lists as needing one. Honor the verdict.
- Spawn `general-purpose` or `Explore` agents for research (provider integration patterns, library surface, etc.).
- Write code, tests, commit, run `bash tools/deploy.sh` to deploy.
- Roll back via `bash tools/deploy.sh rollback` if a deploy goes sideways.
- Edit `docs/`, memory files, wrap notes.
- Add new tools to `ChatTools` if the council approves the design.
- Add new subsystems.
- Tail logs, run smoke chats via `/api/chat`.

You CAN'T, without explicit user confirmation in a new chat turn:
- Anything in the **Anti-features** list of the vision spec.
- Push to a git remote (the repo is local-only by design — adding a remote needs user OK).
- Run `claude login`, `gcalcli init`, OAuth browser flows, or anything else interactive.
- Spend money on new APIs (OpenRouter embeddings already paid; anything else needs OK).
- Modify `/opt/jarvis/.env` beyond appending a key the user already has locally.
- Delete `wiki.md.bak.1778090717` before 2026-05-14 (council retention rule).
- Delete `/opt/jarvis/jarvis-kotlin-pre-stepb` or `/opt/jarvis/jarvis-kotlin-prev` before 2026-05-09 12:30 UTC (deploy rollback window).
- Decide a feature is "shipped" without smoke-testing through the actual user surface.

## Recommended first move

Phase 1.1 in the vision: **Activity importance scoring**. Smallest, most-leveraged step toward the north star, no schema migration of existing rows, no new outbound channel, no LLM call required for v1.

1. Read `src/main/kotlin/jarvis/Activity.kt` and `ActivityCapture.kt`.
2. Sketch `importance: Float?` field on `ActivityEntry` (nullable for back-compat, no v=2 bump needed since the only readers are the loaders themselves).
3. Heuristic scorer in a new `ActivityScorer.kt`: process allowlist (IDE > terminal > docs > browser-on-doc-domain > messaging > entertainment), window-title regex weights (e.g. 'StackOverflow' adds, 'twitter.com' subtracts), duration-since-last-different-window (long focus blocks > short churn).
4. TDD: failing test on synthetic events asserting expected ordering, then implement.
5. Commit. Deploy. Verify via `[[stats]]` + `[[activity: 24]]` — the latter should show importance scores in the formatted output.

Convene a council if the heuristic design feels under-specified — but only if. Otherwise just ship and iterate.

## Loop discipline (per session)

After each commit:

1. Run `gradle :test` — must be green before deploying.
2. Run `bash tools/deploy.sh` — must show `[deploy] done` with `ok` healthcheck.
3. Smoke through `/api/chat` (or the relevant endpoint) and assert the new behavior is observable.
4. If the smoke shows misbehavior: roll back via `tools/deploy.sh rollback`, fix locally, re-deploy.
5. Update `docs/notes/2026-05-08-autonomous-resume.md` (this file) with the commit + smoke result + next action.
6. Update memory at `C:\Users\User\.claude\projects\C--Users-User\memory\project_jarvis_kotlin.md` if state of operational reality changed.

After 5 commits or 2 hours (whichever comes first):
- Convene a post-impl council on the diff. Apply CRITICAL/HIGH inline. Defer MEDIUM/LOW to a follow-up note.
- Update wrap notes.

## Stop-rules

Stop and write a "blocker" note (don't keep grinding) when:
- Council returns WRONG APPROACH and you don't have an obvious next path.
- A test fails for a reason you can't diagnose in 15 minutes.
- A deploy fails and rollback also fails.
- You discover an inconsistency between memory state and observable VPS state.
- You hit anything on the "CAN'T" list above.

The blocker note belongs at `docs/notes/2026-05-08-blocker-<topic>.md`. Memory entry too.

## Useful commands

```bash
# Local dev loop
gradle :test                                              # unit tests
gradle :installDist && bash tools/deploy.sh                # build + deploy
bash tools/deploy.sh rollback                             # revert to -prev

# VPS ops
ssh root@46.247.109.91 "systemctl is-active jarvis"
ssh root@46.247.109.91 "wc -l /opt/jarvis/data/{conversations,activity}.jsonl"
ssh root@46.247.109.91 "tail -50 /var/log/jarvis.log"
ssh root@46.247.109.91 "free -h && df -h /opt"

# Smoke
TOKEN=$(cat C:/Users/User/jarvis-kotlin/tools/AUTH_TOKEN.txt)
curl -s https://corgflix.duckdns.org/healthz
curl -s -X POST https://corgflix.duckdns.org/api/chat \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"msg":"<test prompt>"}'
```

## Outstanding tactical items (small, do anytime)

- 2026-05-09 12:30 UTC: `ssh root@46.247.109.91 "rm -rf /opt/jarvis/jarvis-kotlin-pre-stepb /opt/jarvis/jarvis-kotlin-prev"` if no rollback issues.
- 2026-05-14: re-evaluate `wiki.md.bak.1778090717` retention.
- `core_memory.md` on VPS is empty by design — user-curated. Privacy scanner is wired; user can `ssh + vim` to seed.
- `claude-max` provider login is interactive; flag for user when next opportunity arises.
