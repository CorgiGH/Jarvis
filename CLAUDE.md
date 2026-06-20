# CLAUDE.md — jarvis-kotlin

Personal AI tutor (the "one-pass digestion teaching engine"). Kotlin/Ktor backend + React/TS frontend.
**Session start: read the memory `BRIDGE-HEAD.md` first** (canonical live state), then `MEMORY.md`. End: `/wrap`.

## Commands
- Backend tests: `./gradlew --no-daemon :test` (full) or `:test --tests "jarvis.content.*"` (targeted).
  ⚠ Use `:test`, NOT `test` — bare `test` hits the `:android` module and rejects `--tests`.
- Backend full check (CI parity): `./gradlew --no-daemon :check`.
- Frontend (in `tutor-web/`): `npx vitest run <path>` · `npm run dev` (vite :5173) · `npm run e2e` (playwright).
- Viz gates: `node tutor-web/tools/figure-noclip-gate.mjs` (needs vite :5173 up).

## Architecture
- `src/main/kotlin/jarvis/` — Ktor backend (content loader, verify/grader, routes). `src/test/kotlin/` — JUnit.
- `tutor-web/` — React + TS + Vite frontend (separate npm root). `tutor-web/tools/*.mjs` — CI viz gates.
- `content/<SUBJECT>/{kcs,viz,misconceptions}/*.yaml` — the knowledge-concept corpus (loaded by ContentRepo).
- `.github/workflows/test.yml` — CI (gradle :check, vitest, frame-conjunction + figure-noclip gates, gate-registry).
- `docs/superpowers/` — specs + the LOCKED master plan; `docs/CONVENTIONS.md` — file-org standard.

## Gotchas
- **NEVER `git add -A` / `git clean` on main** — untracked door/demo + `tutor-dist/` bundle/png are NOT gitignored; a sweep deletes or commits the wrong thing. Stage explicit pathspecs.
- Run test suites **foreground + streamed to a log**; never pipe a long run through `| tail` (buffers, blinds you).
- Viz/figures gated at **1536×648 AND ×730** viewport, zero clip/overlap.
- `~/.jarvis/tutor.db` = irreplaceable single-user SR history; off-box dump before any live-DB mutation.
