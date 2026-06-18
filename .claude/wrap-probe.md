# wrap-probe — jarvis-kotlin (read by /wrap Step 1)

Project-specific external/off-repo state to fold into BRIDGE-HEAD "Live external state" + Open items each wrap. These are the things a repo-scoped wrap silently loses unless probed.

1. **Off-box DB backup state.** Live DB = `~/.jarvis/tutor.db` (single-user SQLite, irreplaceable SR history). A backup is OFF-BOX only if it lives off this disk. Record the newest of each: local `./backups/*.sql.gz` (ON-box, same disk — NOT a real off-box net) AND the VPS copy at `root@46.247.109.91:/root/jarvis-db-backups/` (the real off-box). Note whether a fresh off-box dump was taken this session. `tools/db-backup.py` writes the dump (fail-closed at `MIN_EXPECTED_CARDS=800`); set `JARVIS_BACKUP_DIR` to redirect it off-disk. (Council 1780531080 / data-safety: before any Phase-2 live mutation, take a verified off-box dump.)

2. **Off-repo artifacts (NOT in git — easy to orphan).** The ALO exam study-aid is on the Desktop, outside the repo + outside memory:
   - `Desktop/ALO — Foaie de referință examen.pdf` — the corrected bring-in reference sheet (the in-exam paper).
   - `Desktop/jarvis-tutor-handoff/ALO-ghid-*.html` (+ `ALO-ghid-INDEX.html`) — the interactive solving guide (9 topics + index).
   - `Desktop/ALO — Ghid rezolvare (complet).pdf` — the single combined shareable PDF.
   - `Desktop/jarvis-door-snapshot/` — the byte-identical net for the uncommitted-everywhere `tutor-web/src/door/` source (verify it still exists each wrap).
   Record existence + location so they aren't forgotten; do not delete.

3. **Known-flaky / baseline test carve-out.** `IntegrationHarnessTest.stateCacheConcurrentPersistNeverTearsJson` was a StateCache concurrency race — **FIXED SESSION-74** (`StateCache.persist` now atomic temp+rename, commit `e527386`; verified 10/10 under `--rerun-tasks`). It should NO LONGER flake; if it does now, that IS a regression. (Remaining baseline carryover: ~24 pre-existing tsc errors, ~9 env-flaky tests; CI runs vitest not tsc.)

4. **VPS deploy state.** `corgflix.duckdns.org` (`root@46.247.109.91`). push ≠ deploy — record whether THIS session deployed (changed the served bundle) or only pushed to GitHub. Default assumption: pushes do not redeploy the VPS.
