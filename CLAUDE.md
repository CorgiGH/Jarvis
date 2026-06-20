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
- Run test suites **foreground + streamed to a log**; never pipe a long run through `| tail` (buffers, blinds you).
- Viz/figures gated at **1536×648 AND ×730** viewport, zero clip/overlap.
- `~/.jarvis/tutor.db` = irreplaceable single-user SR history; off-box dump before any live-DB mutation.

## Workflow discipline (the intent half)

The machine half (gates, CI, package/backup verification) is self-enforcing and works — keep it.
The recurring failures are in INTENT, not code. Root rule: **the verify-and-own discipline you
apply to code also applies to words and to the ask.** Three concretes (from the 2026-06-20 5-session audit):

- **Restate the target before implementing.** On any ask with an inferred target, first state one line:
  "Target = X, from your words '…'." If you fired an AskUserQuestion, act on the chosen/correct option —
  never a different one. (Cost of the miss: a full build→test→revert cycle wired into the wrong component.)
- **Verify before you ASSERT, not just before you act.** Before stating any product / capability / memory
  claim ("X is banned", "diagrams aren't in the lesson", "model is off"), grep/Read/check the live env
  first — same bar as acting on a memory claim. Garbled-memory-as-fact caused the worst user friction.
- **Own experience decisions; don't ask.** Thesis = machines verify TRUTH, you own EXPERIENCE. On
  design/aesthetic/UX calls, decide + state under `PROPOSAL-GATE`, don't AskUserQuestion. Reserve asks
  for genuine user-only calls (money, irreversible, scope). And match your self-grade bar to the
  deliverable: for a visual ask, invoke the design skills up front and apply an "is this presentable?"
  hold before showing — never hand the user a half-finished artifact as QA.
