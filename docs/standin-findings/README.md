# Stand-in findings

Quarantined drafts from the 3-surface dogfood system (Surface X trace-grader, Surface Z novice-visual, Surface Y student stand-in). Every finding here is **human-review-only** until Alex promotes it. Nothing in `DRAFT-*.md` is auto-committed.

## Layout

- `golden/` — hand-curated ground-truth fixture set for Surface X calibration.
- `schemas/` — concept schemas for Surface Y per task (e.g. `PS-Tema-A.yaml`).
- `screenshots/` — Surface Z screenshots (gitignored; regenerated each run).
- `DRAFT-X-<sess>-<ts>.md` — Surface X findings (gitignored).
- `DRAFT-Z-<sess>-<ts>.md` — Surface Z findings (gitignored).
- `DRAFT-Y-<task>-<ts>.md` — Surface Y findings (gitignored).

## Pre-flight (one-shot, BLOCKING all surfaces)

Before any surface ships beyond local prototype:

1. Provision a second OpenRouter API key. Visit `https://openrouter.ai/settings/keys`, create a key labeled `standin`.
2. Set both env vars:
   ```bash
   export OPENROUTER_API_KEY=<live-key>          # already used by jarvis grader + sidekick
   export OPENROUTER_API_KEY_STANDIN=<new-key>   # dedicated to stand-in surfaces
   ```
3. Run the quota-isolation verifier:
   ```bash
   node tools/verify-openrouter-quota-isolation.mjs
   ```
4. Verdict lands in `docs/notes/2026-05-13-openrouter-quota-isolation.md`. Read it.
5. If verdict is `SHARED`, Surface Y is gated to outside-study-hours runs only (recommend 03:00-06:00 local).
6. Add `OPENROUTER_API_KEY_STANDIN` to the VPS env at `/opt/jarvis/.env` only if you want the deploy-time advisory hook to use it; for laptop-driven CLI runs the local export is sufficient.

## Surface CLIs

```bash
# Surface X — grade real sessions against pedagogy invariants
node tools/surface-x.mjs --task <task_id> --invariants all

# Surface X — calibration run against golden fixture (ship gate)
node tools/surface-x.mjs --calibrate --from-fixture docs/standin-findings/golden/2026-05-13-bootstrap-traces.md

# Surface Z — novice-eyes visual sweep (standalone)
node tools/surface-z.mjs --mode standalone

# Surface Y — student stand-in on a task
node tools/surface-y.mjs --task <task_id> --schema docs/standin-findings/schemas/PS-Tema-A.yaml
```

## Stale-check before acting on any finding

```bash
node tools/findings-stale-check.mjs docs/standin-findings/DRAFT-X-*.md
```

Per-field `[OK|STALE|FRESH]` output. If `Overall: [STALE]`, the finding was generated against an older site state — re-run the surface before acting.
