# Council review — 1779229188

**Problem:** 871 FSRS flashcards exist only in the local dev SQLite DB; they must reach the VPS prod SQLite DB (`/opt/jarvis/data/tutor.db`), which has 0 flashcards but already holds live sessions/tasks/gaps.

**Proposed approach:** Dump the `fsrs_cards` rows from local SQLite → scp → import into the VPS `tutor.db`, instead of re-running the `seed-fsrs` claude-CLI generator on the VPS (~$2 quota, ~40min).

**Project context:** Kotlin/Ktor + Exposed/SQLite + React/Vite. Manual `tools/deploy.sh` deploy; VPS state dir `/opt/jarvis/data/` preserved across deploys. Single user. No paid LLM API spend. FSRS-6 shipped; `seed-fsrs` CLI generates cards via claude CLI.

**Timestamp:** 2026-05-20T (epoch 1779229188)

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: CONDITIONAL
REASONING: The plan's fatal assumption is "the rows are the rows" — that a SQL dump faithfully round-trips through two SQLite type representations. A naive `.dump`/`INSERT` re-import either preserves the local encoding (fine) or — if regenerated via any tool that reformats values — silently shifts all six timestamp columns on 871 cards, including the 50 hand-graded ones whose `dueAt`/`lastReviewedAt`/`stability` are the only irreplaceable data. Success cannot be verified by "871 rows landed"; only by reading cards back through `FsrsCardRepo.toCard()` on the VPS. Second, the plan ignores deploy ordering: if the `fsrs_cards` table doesn't exist yet on prod, or an old schema exists with different column widths, the import aborts or mismatches.
KEY CONCERN: The dump must round-trip through the same Exposed `timestamp()` codec on both ends, or graded FSRS state silently corrupts. Mandatory gates: (1) deploy the pending app FIRST so prod schema matches; (2) back up `/opt/jarvis/data/tutor.db` before import; (3) verify the `users` row `id="owner"` exists on prod; (4) acceptance is NOT row count — hit `/api/v1/fsrs/due` and confirm 821 due, spot-check 3 of the 50 graded cards' `dueAt`/`stability`.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: Dumping one table and importing it is the textbook pattern for partial SQLite-to-SQLite transfer — re-running a non-deterministic LLM generator to reproduce data you already have is the anti-pattern (wastes quota, produces *different* 871 cards, loses the 50 graded states). The correct mechanism is `sqlite3 local.db ".mode insert fsrs_cards"` (or `.dump fsrs_cards` with the `CREATE TABLE`/`CREATE INDEX` lines stripped), giving pure `INSERT INTO fsrs_cards VALUES(...)`; wrap `BEGIN; ... COMMIT;` and use `INSERT OR IGNORE` for idempotency. This is a write into a live preserved DB, so the discipline is: stop the `jarvis` service first (SQLite single-writer), `cp tutor.db tutor.db.bak`, run in one transaction, verify `count(*)`, restart.
KEY CONCERN: Confirm the `due_at`/`last_reviewed_at` storage encoding is byte-identical between local and the possibly-older VPS app version before import; deploy the pending app version first so both DBs are schema- and serializer-identical.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: Moving 871 self-contained rows beats burning $2 of weekly claude quota + 40min to regenerate identical data — proportionate. `fsrs_cards` is a leaf table (only outbound FK is `user_id`→`users`, nothing FKs into it), so a scoped row copy can't corrupt sessions/tasks/gaps. But "dump the table, scp the SQL, import" is the careless path: a plain `.dump fsrs_cards` emits `CREATE TABLE` (or worse `DROP/CREATE`) that can nuke the live table, and a bare import gives no rollback.
KEY CONCERN: A naive `.dump` includes DDL and can drop/recreate the live table — export rows-only (`.mode insert`) and never let schema statements near the prod DB. Conditions: (1) export rows only, no schema; (2) verify `users` row `id='owner'` on VPS first; (3) `cp tutor.db tutor.db.bak-$(date +%s)` with service stopped; (4) import in one `BEGIN;...COMMIT;`, post-verify `count(*)`==871, else restore `.bak`.

## 🧱 First Principles

AGENT: First Principles
STANCE: CONDITIONAL
REASONING: Stripped of framing, the goal is: one student wants one coherent, current view of their study data wherever they study. The table-dump migration is a correct *mechanism* for tonight's symptom, but answers the wrong question: the real problem is "two divergent SQLite DBs for one user with no defined source of truth." Designed from scratch for a single user you do NOT keep two live databases — you have one authoritative `tutor.db` (the VPS, since it already holds live sessions/tasks/gaps and is preserved) and the local box is a throwaway sandbox.
KEY CONCERN: Direction is backwards as a long-term answer. The 50 graded cards mean local already diverged from prod in a way a one-shot copy cannot reconcile next time. Do the import tonight (harmless, fast), but only if paired with a one-time decision that the VPS `tutor.db` is henceforth the sole authority and local stops being a study target — otherwise this same merge conflict recurs forever.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Sound and recoverable IF two preconditions hold. First, the import must run against a STOPPED jarvis service: the VPS DB is WAL-mode and the live service holds open file handles; a concurrent `sqlite3` import risks a locked DB, half-applied transaction, or `-wal`/`-shm` desync that corrupts the user's only copy of sessions/tasks/gaps. Second, the raw `sqlite3` CLI defaults to `foreign_keys=OFF` (unlike the app's `TutorDb.connect` which sets `PRAGMA foreign_keys=ON`), so if the `owner` row is missing the import silently creates 871 orphan cards. The Instant.MAX precedent does NOT apply here (that was a query param, not stored data); the 871 rows carry ordinary `Instant.now()` timestamps and SQLite stores instants timezone-agnostically, so a direct row copy is safe. ULID collision is a non-issue for the first import (target empty).
KEY CONCERN: CRITICAL — importing into a live WAL-mode production DB. Mandatory: (1) `systemctl stop jarvis`, (2) `cp tutor.db tutor.db.bak-preimport`, (3) import in one `BEGIN;...COMMIT;`, (4) restart + smoke the FSRS due endpoint. Without stop+backup a botched import is unrecoverable.

---

## Sanity Check

SANITY Devil's Advocate: PASS
NOTE: Storage-type claim is imprecise — the live schema has `due_at`/`last_reviewed_at` as TEXT (ISO-8601), not numeric; a verbatim `.mode insert` copy of TEXT is lossless. But the demanded mitigations (deploy-first, backup, FK check, verify-by-readback) are all valid and echoed by clean agents.

SANITY Domain Expert: PASS
NOTE: Clean. Names concrete patterns (`.mode insert`, `.dump`, `ATTACH DATABASE`, `INSERT OR IGNORE`). Same timestamp-encoding caution is slightly overweighted given TEXT storage, but the mitigation is cheap and harmless.

SANITY Pragmatist: PASS
NOTE: Clean. CONDITIONAL with four explicit, named conditions. Correctly identifies `fsrs_cards` as a leaf table.

SANITY First Principles: PASS
NOTE: Clean. Genuinely strips the framing — challenges transfer *direction* and the two-DB split rather than rephrasing the question.

SANITY Risk Analyst: PASS
NOTE: Clean and strongest. Correctly retires the Instant.MAX red herring, correctly flags the `sqlite3` CLI `foreign_keys=OFF` default vs the app's `PRAGMA foreign_keys=ON`, ranks the WAL-mode live-import risk CRITICAL.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: APPROVED

CORE FINDING:
Row-copy is the right mechanism — re-seeding would burn quota to generate a *different* 871 cards and would destroy the 50 hand-graded scheduling states, which are irreplaceable. All five agents land CONDITIONAL, not because the approach is wrong but because a careless execution writes into a live WAL-mode production DB. The mechanism is approved; the execution must follow a fixed runbook.

AGENT CONSENSUS: 5 CONDITIONAL, 0 flagged — unanimous approve-with-mitigations.

KEY ISSUES:
- CRITICAL: the import writes into a live WAL-mode prod DB the running service holds open. Stop `jarvis` and back up `tutor.db` first, or a botched import is unrecoverable and takes live sessions/tasks/gaps with it.
- Export rows ONLY (`sqlite3 ".mode insert fsrs_cards"`), never a `.dump` that carries `CREATE`/`DROP` DDL near the prod table.
- The raw `sqlite3` CLI defaults to `foreign_keys=OFF`; verify the `users` row `id='owner'` exists on the VPS first, or the import silently creates 871 orphan cards.
- Acceptance is NOT row count — read cards back through the app: `/api/v1/fsrs/due` must show 821 due, and the 50 graded cards must keep their `dueAt`/`stability`.
- (First Principles) After import, declare the VPS `tutor.db` the sole source of truth and stop studying on local — otherwise the two DBs re-diverge and the next merge has graded-state collisions.

RECOMMENDED PATH:
Proceed with row-copy, under this runbook:
1. Deploy the pending app to the VPS FIRST (schema + serializer parity).
2. `ssh VPS` → confirm `fsrs_cards` exists and `SELECT count(*) FROM users WHERE id='owner'` returns 1.
3. `systemctl stop jarvis`.
4. `cp /opt/jarvis/data/tutor.db /opt/jarvis/data/tutor.db.bak-preimport`.
5. Local: `sqlite3 tutor.db ".mode insert fsrs_cards" "SELECT * FROM fsrs_cards"` → rows-only SQL. Wrap in `BEGIN; ... COMMIT;`. scp to VPS.
6. Import into VPS `tutor.db` (rows-only, one transaction, `INSERT OR IGNORE` for re-run safety).
7. `systemctl start jarvis`; verify `/api/v1/fsrs/due` shows 821 due + spot-check 3 graded cards.
8. On failure: restore the `.bak-preimport` copy.
9. Decide: VPS is now the study authority; local is dev-only.

CONFIDENCE: 8
What would raise it: confirmed presence of the `owner` row on the VPS and confirmation the post-deploy VPS schema matches local (both expected true, not yet verified).
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1779229188-fsrs-card-transfer.md
