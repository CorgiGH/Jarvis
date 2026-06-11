# Plan 1: Trust-Net ON Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the built-and-sealed trust-net ON in production: hardened backup tooling + restore drill → live-DB migrations (kc_verification_status +3 columns, report_wrong +resolved_by/resolved_at) → `verifyContent` over the real PA corpus producing ≥1 FAITHFUL row → D9 PC→VPS verdict sync → CI loud-red on missing verifier deps.

**Architecture:** The trust-net code (VerificationRunner, VerifyAdmin serve gate, VerificationGate) is already built and sealed on `main`; only the live DB schema is 3 columns behind it and the audit has never run, so the faithful-gated queue is empty for every user. This plan adds the missing operational layer: an INV-3.1 backup-refusal gate inside `TutorMigration.migrate`, a hardened `tools/db-backup.py` (argparse + manifest + restore drill), a D9 export/import pair over the frozen audit-column allowlist, and machine-checked invariants — then runs the gated migration and the real audit. Every step that touches `~/.jarvis/tutor.db` (828 cards, irreplaceable) is rehearsed on a copy first and gated on a same-day verified off-box dump.

**Tech Stack:** Kotlin/Ktor + Exposed/SQLite, Python (backup tool), Gradle tasks, GitHub Actions CI

**Binding spec:** `docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md` — §3.6 (migration order), §9.2 gate 2 (turn-on sequence + flip-guard scoping), §10.1 (build order), §12 (locked decisions D-RF2/D-RF3/backup-first), INV-3.1, INV-3.3, INV-9.3. On conflict with anything older, the spec wins — except `docs/superpowers/plans/2026-06-02-interface-signatures-lock.md`, which is canonical-on-conflict for frozen signatures (§13). Everything in this plan is additive against that lock.

---

## Section 0 — Verified ground truth (read before any task)

Everything below was re-verified against the working tree + the live DB (read-only) on 2026-06-11. Do **not** trust older plan docs where they disagree.

### 0.1 The live DB (`~/.jarvis/tutor.db`, read-only probe)

```
fsrs_cards: 828 rows                       ← INV-3.3: all 828 must survive every step
kc_verification_status: 0 rows, 4 columns  ← [kc_id, status, last_audit_run_id, updated_at]
report_wrong: 0 rows, 7 columns            ← [id, user_id, kc_id, card_id, grade_attempt_raw, reported_at, resolution]
verification_audit: 0 rows (22 columns, schema already current)
```

Live DDL (exact, from `sqlite_master`):

```sql
CREATE TABLE kc_verification_status (kc_id VARCHAR(64) NOT NULL PRIMARY KEY, status VARCHAR(16) DEFAULT 'unverified' NOT NULL, last_audit_run_id VARCHAR(64) NULL, updated_at TEXT NOT NULL)
CREATE TABLE report_wrong (id VARCHAR(26) NOT NULL PRIMARY KEY, user_id VARCHAR(26) NOT NULL, kc_id VARCHAR(64) NOT NULL, card_id VARCHAR(26) NULL, grade_attempt_raw TEXT NOT NULL, reported_at TEXT NOT NULL, resolution VARCHAR(24) NULL, CONSTRAINT fk_report_wrong_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT)
```

### 0.2 The 3 missing columns — what the code already writes

`src/main/kotlin/jarvis/tutor/Phase1Tables.kt:117-184`, `KcVerificationStatusTable`. The code defines **7** columns; the live DB has **4**. The missing three (quoted from the source):

```kotlin
val contentHash = varchar("content_hash", 16).nullable()        // D8 staleness fingerprint + D9 upsert key
val lectureGrounded = bool("lecture_grounded").nullable()       // B5r-2 (D-R5/D-R6) grounded badge signal
val sourceSpanHash = varchar("source_span_hash", 16).nullable() // D1 source-of-record co-factor
```

So the audit speculation "content_hash/source_span_hash/+1" resolves to: **the +1 is `lecture_grounded`**. All three are nullable + additive; `SchemaUtils.createMissingTablesAndColumns` ALTER-adds them with no backfill (NULL ⇒ fail-closed at serve, which is the correct honest default).

### 0.3 report_wrong D-RF3 — ALREADY IN CODE, only the live DB is behind

The spec's §3.6 step 3 says report_wrong "gains" `resolved_by`+`resolved_at`. **In code it already gained them** — `Phase1Tables.kt:88-101` `ReportWrongTable` (quoted):

```kotlin
val resolvedBy = varchar("resolved_by", 26).nullable()
val resolvedAt = timestamp("resolved_at").nullable()
```

and `VerificationRunner` already stamps them via `ReportWrongQuery.closeOpenReports(..., resolvedBy = ownerId, resolvedAt = now)` (`VerificationRunner.kt:206-211`). **No new schema code is needed** — this plan only pins the live-shape delta with a test (Task 5) and runs the migration (Task 7). The table is empty live (0 rows), so the D-RF3 "add while empty" window is still open.

### 0.4 How this repo migrates schema (the pattern you MUST follow)

`src/main/kotlin/jarvis/tutor/Migration.kt` — `TutorMigration.migrate(db, backupHook, failAfter, v2HashFlipEnabled)`:

- ONE place for all DDL: `SchemaUtils.createMissingTablesAndColumns(*ALL_TABLES)` inside one `transaction(db)` + explicit `WHERE col IS NULL`-guarded backfills. Idempotent (M-IDEMP).
- SQLite ALTER is NOT transactional; a mid-migration failure fires the M-PARTIAL `backupHook` and throws `MigrationException`.
- Already has the DELTA-4 flip fence: when env `JARVIS_V2_HASH_FLIP` ∈ {1,true,yes,on}, `assertNoLiveContentHashRowsBeforeV2Flip()` aborts if any non-null `content_hash` row exists (`Migration.kt:136-138, 231-239`). **Per spec §9.2 gate 2 this guard is flip-time only** — we set the env var for exactly one boot (Task 7) and never bake it in; re-audits over a populated table stay runnable, keyed by `audit_run_id`.
- It does **NOT** currently enforce backup-first — the KDoc (`Migration.kt:24-27`) pushes that to operator convention ("The operator runs that BEFORE boot"). **INV-3.1 requires the runner itself to refuse.** Task 3 adds that gate.
- Test seams are constructor/function parameters with production defaults (`backupHook`, `failAfter`, `v2HashFlipEnabled`) — the new gate follows the same idiom.
- Entry points that call `migrate`: server boot (`TutorRoutes.kt:2981` via `installTutorContext`), `jarvis migrate [path]` operator subcommand (`Main.kt:36-54,76`), `VerifyContentCli.kt:96`, `ContentReconcileCli.kt:54`, and ~10 test files.

### 0.5 tools/db-backup.py — the confirmed flag-parsing hole

`tools/db-backup.py:45` (current):

```python
out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("./backups")
```

No argparse. `python tools/db-backup.py --help` therefore executes a **live backup into a directory literally named `--help`** (final-audit limitation #12 — confirmed). Existing protections that MUST be preserved: the `MIN_EXPECTED_CARDS` fail-closed floor (default 800; refuses below, **before** writing any dump), `JARVIS_DB` env resolution, restore-integrity check, exit 0/1 contract, and the stdout strings asserted by `tools/test_db_backup.py` (`"integrity: PASS"`, `"backed up N fsrs_cards"`). NOTE: the file's comment says "the live corpus is ~871" — stale; live is 828. Fix the comment while editing.

### 0.6 verifyContent / reconcileContent — entry points and env deps

`build.gradle.kts:189-217` registers both as `JavaExec` tasks (group `verification`, deliberately NOT on `check`):

- `gradle verifyContent "-PverifyArgs=content PA"` → `jarvis.tutor.verify.VerifyContentCliKt`. args[0]=content dir, args[1]=subject filter. FAIL-LOUD pre-flight (`VerifyContentCli.missingFamilyEnv`, H4): exits 2 **before opening any DB** unless ALL of `JARVIS_RELAY_URL`, `JARVIS_RELAY_TOKEN` (family A = RELAY) and `JARVIS_PYTHON3` (family B = local NLI, DeBERTa `MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli` via a python with torch+transformers) are set non-blank. Abort message: `"verifyContent ABORTED (FAIL-LOUD, H4): … Missing env: <names>"`. This existing fail-loud is exactly what the INV-9.3 CI job (Task 11) asserts.
- `gradle reconcileContent` → `jarvis.content.ContentReconcileCliKt`. No env preconditions, no LLM. Validates the corpus, runs the D1 source-edit watcher (`ContentReconcile.reconcileSourceSpans`), then UNVERIFIED→PENDING (`ContentReconcile.reconcile`). Prints `[reconcileContent] reconciled N claim(s); promoted M KC(s) to pending.`
- DB resolution for both: `JARVIS_TUTOR_DB` env/property, else `jarvis.Config.tutorDbPath` = `~/.jarvis/tutor.db` (`Config.kt:38`).
- Relay facts (PC): port **9999**, bearer token persisted at `~/.jarvis-relay-token` (`tools/install-pc-relay.ps1:26`).

### 0.7 The real PA corpus

`content/subjects.yaml` declares subject id **`PA`** (plus PS, POO, ALO, SO-RC). `content/PA/kcs/` holds `pa-kc-001..006.yaml` + `pa-kc-fixture-compute.yaml` + `pa-kc-fixture-recursion.yaml`; sources in `content/PA/_sources/pa-lecture-01.md`. PA is the subject with a SymPy non-LLM leg (`VerifyContentCli.liveRunner`: `nonLlmLegFor = PA ⇒ SymPy, else NONE`).

### 0.8 D9 — confirmed UNBUILT; the frozen contract it must satisfy

Grep proof: `D9` appears only in comments (`Migration.kt:133`, `Phase1Tables.kt:165`, `ReportWrongQuery.kt:23-24`) and plan docs. No export/import code exists. The frozen contract (`interface-signatures-lock.md` §I.1:250, FROZEN RF1):

> **Audit-column allowlist (FROZEN, RF1):** the set D9 surgically upserts PC→VPS is exactly `{status, content_hash, lecture_grounded, source_span_hash, last_audit_run_id}`. D9 NEVER syncs `report_wrong` rows and NEVER the whole `tutor.db`. These five plus the PK `kc_id` + bookkeeping `updated_at` are the table.

Tasks 9–10 build exactly that: an export command producing a verdict dump JSON + an import command applied on the VPS, both idempotent, rows carrying `last_audit_run_id` (re-audit keyed), with a monotonic `updated_at` guard so an older dump never overwrites a newer verdict.

### 0.9 VPS topology (for Tasks 7 & 11)

- VPS `root@46.247.109.91`; app dist `/opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin` (systemd unit `jarvis`, `ExecStart=… web`); env at `/opt/jarvis/.env` (preserved across deploys); deploy via `bash tools/deploy.sh` (builds, scps dist + `content/` corpus, restarts, smoke-checks `https://corgflix.duckdns.org/healthz`).
- The Main binary already has a `migrate` subcommand (`Main.kt:76`) — server boot also migrates. After Task 3 lands, **any boot with pending DDL over a ≥800-card DB demands a same-day verified manifest** (read from `JARVIS_BACKUP_DIR`, else `./backups`) — the VPS runbook in Task 11 accounts for this.

### 0.10 Hard constraints (binding, repeated from the spec — violations = stop work)

1. **Backup-first (INV-3.1):** no mutating step on any ≥800-card DB without a same-day verified off-box dump. CI tests the refusal path.
2. **All 828 cards survive (INV-3.3):** count equality with the backup after every mutating step.
3. **Additive only** vs `interface-signatures-lock.md` — no frozen signature modified; everything here is new files, new defaulted params, new columns already frozen in the lock.
4. **NO manual content spot-checks by the user, ever** (no-oracle-inversion). Machine parity checks only: the PARITY invariant (stored `content_hash` == recompute from `content/`) + a full machine re-audit replace the old plan's forbidden "spot-check 3 rows against PDFs manually".
5. **No paid APIs:** RELAY (claude-max relay) + local NLI only; never OpenAI/paid OpenRouter.
6. **The live DB is IRREPLACEABLE:** every mutating task operates on a COPY until its explicitly gated apply step.
7. Repo hygiene: **never `git add -A`** (untracked demo files live in the tree), never `git clean`, never branch-switch on main, never merge the stale `door-compare` branch. Commit named files only.
8. Run gradle directly — never pipe it through `| tail`/`| head`.

---

## Task 0 — Preconditions probe (read-only, no commit)

- [ ] From repo root `C:\Users\User\jarvis-kotlin`, probe the live DB read-only:

```powershell
python -c "import sqlite3,pathlib; db=(pathlib.Path.home()/'.jarvis'/'tutor.db'); con=sqlite3.connect(db.as_uri()+'?mode=ro',uri=True); print('cards:', con.execute('SELECT COUNT(*) FROM fsrs_cards').fetchone()[0]); print('kvs cols:', [c[1] for c in con.execute('PRAGMA table_info(kc_verification_status)')]); print('rw cols:', [c[1] for c in con.execute('PRAGMA table_info(report_wrong)')]); print('kvs rows:', con.execute('SELECT COUNT(*) FROM kc_verification_status').fetchone()[0])"
```

Expected output (exactly this state — if it differs, STOP and re-assess against Section 0):

```
cards: 828
kvs cols: ['kc_id', 'status', 'last_audit_run_id', 'updated_at']
rw cols: ['id', 'user_id', 'kc_id', 'card_id', 'grade_attempt_raw', 'reported_at', 'resolution']
kvs rows: 0
```

- [ ] Confirm clean-enough git state: `git status --short` — expect the known untracked demo files; do not touch them.
- [ ] Confirm the baseline suite is green before changing anything: `gradle --no-daemon :test` → `BUILD SUCCESSFUL`. If it is not green, stop and report — do not build on red.

---

## Task 1 — Harden `tools/db-backup.py` with argparse (`--help` must never execute a backup)

**Files:** `tools/test_db_backup_hardening.py` (new), `tools/db-backup.py` (modified).

- [ ] **Write the failing tests.** Create `tools/test_db_backup_hardening.py`:

```python
#!/usr/bin/env python3
"""Tests for the HARDENED tools/db-backup.py.

Final-audit limitation #12: the tool had NO flag parsing — `--help` executed a
live backup into a directory literally named `--help`. These tests pin the fix
(argparse) and, from Task 2 on, the manifest sidecar + restore drill
(backup -> restore to scratch -> row-count + schema-hash equality vs live).

Convention: stdlib unittest, subprocess-driven, no external deps — matches
tools/test_db_backup.py, which MUST keep passing unchanged (its stdout/exit
contract is the regression net for the old behavior).
"""

from __future__ import annotations

import glob
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRIPT = HERE / "db-backup.py"


def make_db(db_path: Path, n_cards: int) -> None:
    conn = sqlite3.connect(db_path)
    try:
        conn.execute(
            "CREATE TABLE fsrs_cards (id INTEGER PRIMARY KEY, due TEXT, stability REAL)"
        )
        conn.executemany(
            "INSERT INTO fsrs_cards (id, due, stability) VALUES (?, ?, ?)",
            [(i, "2026-06-11", float(i)) for i in range(n_cards)],
        )
        conn.commit()
    finally:
        conn.close()


def run_tool(args: list[str], db: Path | None, cwd: Path, min_cards: str | None = "10"):
    env = dict(os.environ)
    if db is not None:
        env["JARVIS_DB"] = str(db)
    else:
        env.pop("JARVIS_DB", None)
    if min_cards is not None:
        env["MIN_EXPECTED_CARDS"] = min_cards
    else:
        env.pop("MIN_EXPECTED_CARDS", None)
    return subprocess.run(
        [sys.executable, str(SCRIPT), *args],
        env=env, capture_output=True, text=True, cwd=cwd,
    )


class FlagSafetyTests(unittest.TestCase):
    def test_help_exits_0_and_never_backs_up(self):
        """--help prints usage, exits 0, creates NO '--help' dir and NO dump."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)

            proc = run_tool(["--help"], db, cwd=tmp)

            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertIn("usage", proc.stdout.lower())
            self.assertFalse(
                (tmp / "--help").exists(),
                "a directory named '--help' was created — flag parsing is broken "
                "(final-audit limitation #12)",
            )
            self.assertEqual(
                glob.glob(str(tmp / "**" / "*.sql.gz"), recursive=True), [],
                "--help executed a live backup",
            )

    def test_unknown_flag_exits_2_without_backing_up(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)

            proc = run_tool(["--bogus"], db, cwd=tmp)

            self.assertEqual(proc.returncode, 2, proc.stdout + proc.stderr)
            self.assertEqual(glob.glob(str(tmp / "**" / "*.sql.gz"), recursive=True), [])

    def test_db_flag_overrides_env(self):
        """--db points at the real source even when JARVIS_DB points elsewhere."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            real = tmp / "real.db"
            make_db(real, 50)
            decoy = tmp / "decoy.db"
            make_db(decoy, 5)  # below floor 10 — would refuse if (wrongly) used
            out = tmp / "backups"

            proc = run_tool(["--db", str(real), str(out)], decoy, cwd=tmp)

            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertIn("backed up 50 fsrs_cards", proc.stdout)
            self.assertEqual(len(glob.glob(str(out / "*.sql.gz"))), 1)

    def test_positional_output_dir_still_works(self):
        """The legacy invocation `db-backup.py <outdir>` is unchanged."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)
            out = tmp / "backups"

            proc = run_tool([str(out)], db, cwd=tmp)

            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertEqual(len(glob.glob(str(out / "*.sql.gz"))), 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
```

- [ ] **Run it — expect RED** (`--help` today creates the dir and backs up):

```powershell
python tools/test_db_backup_hardening.py
```

Expected: `FAILED (failures=2)` or similar — `test_help_exits_0_and_never_backs_up` and `test_unknown_flag_exits_2_without_backing_up` fail; the other two may pass.

- [ ] **Implement.** Replace `tools/db-backup.py` with (complete file; Task 2 extends it):

```python
#!/usr/bin/env python3
"""
tools/db-backup.py — off-box SQLite backup tool for jarvis tutor.db
Gap-ledger must-resolve #3: schema-deploy precondition. Plan-1 hardening:
argparse flag parsing (final-audit limitation #12: `--help` used to execute a
live backup into a directory named `--help`).

Usage:
    python3 tools/db-backup.py [output_dir] [--db PATH] [--min-cards N]

    output_dir defaults to ./backups (created if absent).
    DB path: --db flag, else $JARVIS_DB env var, else ~/.jarvis/tutor.db.
    Floor: --min-cards, else $MIN_EXPECTED_CARDS, else 800.

Output: backups/jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz
Exit codes: 0 = success, 1 = any failure, 2 = bad CLI usage (argparse).
"""

import argparse
import gzip
import os
import sqlite3
import sys
from datetime import datetime
from pathlib import Path

# Fail-closed schema-ALTER precondition (M-DB). The backup MUST refuse if the
# source corpus looks truncated, so the first ALTER can never run over a
# culled/wrong-path DB. Default 800; the live corpus is 828 (verified
# 2026-06-11). Overridable via --min-cards or the MIN_EXPECTED_CARDS env var.
DEFAULT_MIN_EXPECTED_CARDS = 800


def parse_args(argv=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="db-backup.py",
        description=(
            "Off-box SQLite backup for the jarvis tutor DB. Refuses (exit 1) when "
            "fsrs_cards < MIN_EXPECTED_CARDS — the fail-closed M-DB floor that "
            "protects every schema ALTER."
        ),
    )
    p.add_argument(
        "output_dir", nargs="?", default="./backups",
        help="directory the .sql.gz dump lands in (default: ./backups)",
    )
    p.add_argument(
        "--db", default=None,
        help="source DB path (default: $JARVIS_DB, else ~/.jarvis/tutor.db)",
    )
    p.add_argument(
        "--min-cards", type=int, default=None,
        help="override the fail-closed floor (default: $MIN_EXPECTED_CARDS, else 800)",
    )
    return p.parse_args(argv)


def resolve_floor(args: argparse.Namespace) -> int | None:
    """--min-cards > $MIN_EXPECTED_CARDS > default. None means: error already printed."""
    if args.min_cards is not None:
        return args.min_cards
    try:
        return int(os.environ.get("MIN_EXPECTED_CARDS", DEFAULT_MIN_EXPECTED_CARDS))
    except ValueError:
        print(
            f"ERROR: MIN_EXPECTED_CARDS env var is not an integer: "
            f"{os.environ.get('MIN_EXPECTED_CARDS')!r}",
            file=sys.stderr,
        )
        return None


def open_ro(db_path: Path) -> sqlite3.Connection:
    return sqlite3.connect(db_path.as_uri() + "?mode=ro", uri=True)


def cmd_backup(db_path: Path, out_dir: Path, min_expected_cards: int) -> int:
    try:
        src = open_ro(db_path)
    except sqlite3.OperationalError as exc:
        print(f"ERROR: cannot open DB {db_path}: {exc}", file=sys.stderr)
        return 1

    try:
        src_count = src.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
    except sqlite3.OperationalError as exc:
        print(f"ERROR: cannot read fsrs_cards: {exc}", file=sys.stderr)
        src.close()
        return 1

    # --- fail-closed floor: refuse BEFORE writing any dump (M-DB) ---
    if src_count < min_expected_cards:
        print(
            f"ERROR: fsrs_cards={src_count} < MIN_EXPECTED_CARDS={min_expected_cards}"
            f" — refusing backup (corpus looks truncated; do NOT migrate)",
            file=sys.stderr,
        )
        src.close()
        return 1

    out_dir.mkdir(parents=True, exist_ok=True)

    # --- dump to gzip ---
    ts = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_path = out_dir / f"jarvis-tutor-db-{ts}.sql.gz"
    try:
        with gzip.open(out_path, "wt", encoding="utf-8") as gz:
            for line in src.iterdump():
                gz.write(line + "\n")
    except Exception as exc:
        print(f"ERROR: dump failed: {exc}", file=sys.stderr)
        src.close()
        return 1
    src.close()
    dump_size = out_path.stat().st_size

    # --- integrity check: restore to :memory: and compare card count ---
    # (Task 2 replaces this with the full restore drill to a scratch FILE
    #  + schema-hash equality + the manifest sidecar.)
    integrity_ok = False
    try:
        with gzip.open(out_path, "rt", encoding="utf-8") as gz:
            sql_text = gz.read()
        mem = sqlite3.connect(":memory:")
        mem.executescript(sql_text)
        restored_count = mem.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
        mem.close()
        if restored_count == src_count:
            integrity_ok = True
            integrity_label = "OK"
        else:
            integrity_label = f"FAIL (source={src_count}, restored={restored_count})"
    except Exception as exc:
        integrity_label = f"FAIL ({exc})"

    status = "PASS" if integrity_ok else "FAIL"
    print(f"[db-backup] integrity: {status}")
    print(
        f"[db-backup] backed up {src_count} fsrs_cards"
        f" -> {out_path}"
        f" ({dump_size:,} bytes);"
        f" integrity: {integrity_label}"
    )
    return 0 if integrity_ok else 1


def main(argv=None) -> int:
    args = parse_args(argv)
    floor = resolve_floor(args)
    if floor is None:
        return 1
    db_path = Path(args.db) if args.db else Path(
        os.environ.get("JARVIS_DB", Path.home() / ".jarvis" / "tutor.db")
    )
    if not db_path.exists():
        print(f"ERROR: DB not found at {db_path}", file=sys.stderr)
        return 1
    return cmd_backup(db_path, Path(args.output_dir), floor)


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Run both suites — expect GREEN:**

```powershell
python tools/test_db_backup_hardening.py
python tools/test_db_backup.py
```

Expected: both end `OK` (4 tests + 5 tests).

- [ ] **Commit:**

```powershell
git add tools/db-backup.py tools/test_db_backup_hardening.py
git commit -m "fix(backup): argparse hardening — --help no longer executes a live backup (audit limitation #12)"
```

---

## Task 2 — Restore drill + manifest sidecar in `tools/db-backup.py`

The spec (§3.6 step 1): "A restore drill (backup → restore to scratch → row-count + schema-hash equality) is part of step 1, not optional." The manifest sidecar is what the Task-3 Kotlin gate reads.

**Files:** `tools/test_db_backup_hardening.py` (extended), `tools/db-backup.py` (extended).

- [ ] **Add the failing tests.** Append to `tools/test_db_backup_hardening.py` (before the `if __name__ == "__main__":` block):

```python
class ManifestAndDrillTests(unittest.TestCase):
    def _backup(self, tmp: Path, n: int = 50):
        db = tmp / "live.db"
        make_db(db, n)
        out = tmp / "backups"
        proc = run_tool([str(out)], db, cwd=tmp)
        self.assertEqual(proc.returncode, 0, proc.stderr)
        dumps = glob.glob(str(out / "*.sql.gz"))
        self.assertEqual(len(dumps), 1)
        return db, Path(dumps[0])

    def test_manifest_sidecar_written_with_schema_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            mp = Path(str(dump) + ".manifest.json")
            self.assertTrue(mp.exists(), "manifest sidecar missing")
            m = json.loads(mp.read_text(encoding="utf-8"))
            self.assertEqual(m["fsrs_cards"], 50)
            self.assertEqual(m["integrity"], "PASS")
            self.assertEqual(m["restore_drill"], "PASS")
            self.assertEqual(m["dump_file"], dump.name)
            self.assertIn("created_date", m)
            # schema_hash matches an independent recompute over the live DB —
            # the EXACT algorithm MigrationBackupGate.liveSchemaHash (Kotlin) mirrors.
            conn = sqlite3.connect(db)
            rows = conn.execute(
                "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL "
                "AND name NOT LIKE 'sqlite_%'"
            ).fetchall()
            conn.close()
            expect = hashlib.sha256(
                "\n".join(sorted(r[0].strip() for r in rows)).encode("utf-8")
            ).hexdigest()
            self.assertEqual(m["schema_hash"], expect)

    def test_restore_drill_runs_inside_every_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db = tmp / "live.db"
            make_db(db, 50)
            proc = run_tool([str(tmp / "backups")], db, cwd=tmp)
            self.assertEqual(proc.returncode, 0)
            self.assertIn("restore drill: PASS", proc.stdout)

    def test_restore_drill_flag_passes_on_fresh_dump(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            proc = run_tool(["--restore-drill", str(dump)], db, cwd=tmp)
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertIn("restore drill: PASS", proc.stdout)

    def test_restore_drill_fails_on_row_count_drift(self):
        """Live gains a row AFTER the dump -> the dump no longer covers the data."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            conn = sqlite3.connect(db)
            conn.execute("INSERT INTO fsrs_cards (id, due, stability) VALUES (999, 'x', 0.0)")
            conn.commit()
            conn.close()

            proc = run_tool(["--restore-drill", str(dump)], db, cwd=tmp)

            self.assertEqual(proc.returncode, 1)
            self.assertIn("restore drill: FAIL", proc.stdout)
            m = json.loads(Path(str(dump) + ".manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(m["restore_drill"], "FAIL", "manifest must be downgraded")

    def test_restore_drill_fails_on_schema_drift(self):
        """Live schema changes AFTER the dump -> schema-hash mismatch."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            db, dump = self._backup(tmp)
            conn = sqlite3.connect(db)
            conn.execute("ALTER TABLE fsrs_cards ADD COLUMN kc_id TEXT")
            conn.commit()
            conn.close()

            proc = run_tool(["--restore-drill", str(dump)], db, cwd=tmp)

            self.assertEqual(proc.returncode, 1)
            self.assertIn("schema-hash mismatch", proc.stdout)
```

- [ ] **Run — expect RED** (`KeyError`/missing manifest, unknown `--restore-drill` flag → exit 2):

```powershell
python tools/test_db_backup_hardening.py
```

- [ ] **Implement.** In `tools/db-backup.py`: add imports `hashlib`, `json`, `tempfile`, and `timezone` (`from datetime import datetime, timezone`); add the new functions below; add the `--restore-drill` argument; replace `cmd_backup`'s `:memory:` integrity block with the drill + manifest; route `--restore-drill` in `main`.

New functions (add after `open_ro`):

```python
MANIFEST_VERSION = 1


def schema_hash(conn: sqlite3.Connection) -> str:
    """sha256 hex over the sorted, stripped CREATE statements of all user objects.

    MUST stay byte-identical with MigrationBackupGate.liveSchemaHash (Kotlin):
    SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE
    'sqlite_%'; strip each; sort; join with '\\n'; sha256 hex digest.
    """
    import hashlib as _hashlib
    rows = conn.execute(
        "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'"
    ).fetchall()
    canon = "\n".join(sorted(r[0].strip() for r in rows))
    return _hashlib.sha256(canon.encode("utf-8")).hexdigest()


def manifest_path_for(dump_path: Path) -> Path:
    return dump_path.with_name(dump_path.name + ".manifest.json")


def run_restore_drill(db_path: Path, dump_path: Path) -> tuple[bool, str]:
    """Restore the dump into a scratch FILE db; compare row-count + schema-hash vs live."""
    import tempfile as _tempfile
    src = open_ro(db_path)
    try:
        live_cards = src.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
        live_schema = schema_hash(src)
    finally:
        src.close()

    try:
        with gzip.open(dump_path, "rt", encoding="utf-8") as gz:
            sql_text = gz.read()
        fd, scratch = _tempfile.mkstemp(prefix="restore-drill-", suffix=".db")
        os.close(fd)
        restored = sqlite3.connect(scratch)
        restored.executescript(sql_text)
    except Exception as exc:
        return False, f"restore failed: {exc}"

    try:
        r_cards = restored.execute("SELECT COUNT(*) FROM fsrs_cards").fetchone()[0]
        r_schema = schema_hash(restored)
    except sqlite3.OperationalError as exc:
        restored.close()
        return False, f"restored DB unreadable: {exc}"
    restored.close()
    try:
        os.unlink(scratch)
    except OSError:
        pass

    if r_cards != live_cards:
        return False, f"row-count mismatch (live={live_cards}, restored={r_cards})"
    if r_schema != live_schema:
        return False, (
            f"schema-hash mismatch (live={live_schema[:12]}…, restored={r_schema[:12]}…)"
        )
    return True, f"cards {r_cards}=={live_cards}, schema_hash match"


def write_manifest(dump_path: Path, db_path: Path, cards: int,
                   s_hash: str, drill_status: str) -> None:
    now = datetime.now(timezone.utc)
    manifest = {
        "tool": "db-backup.py",
        "manifest_version": MANIFEST_VERSION,
        "created_date": datetime.now().strftime("%Y-%m-%d"),  # LOCAL date — gate compares LocalDate.now()
        "created_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "db_path": str(db_path),
        "dump_file": dump_path.name,
        "fsrs_cards": cards,
        "schema_hash": s_hash,
        "integrity": drill_status,
        "restore_drill": drill_status,
    }
    import json as _json
    manifest_path_for(dump_path).write_text(
        _json.dumps(manifest, indent=2), encoding="utf-8"
    )


def cmd_restore_drill(db_path: Path, dump_path: Path) -> int:
    if not dump_path.exists():
        print(f"ERROR: dump not found at {dump_path}", file=sys.stderr)
        return 1
    ok, detail = run_restore_drill(db_path, dump_path)
    label = "PASS" if ok else "FAIL"
    print(f"[db-backup] restore drill: {label} ({detail})")
    mp = manifest_path_for(dump_path)
    if mp.exists():
        import json as _json
        m = _json.loads(mp.read_text(encoding="utf-8"))
        m["restore_drill"] = label
        m["drilled_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        mp.write_text(_json.dumps(m, indent=2), encoding="utf-8")
    return 0 if ok else 1
```

In `parse_args`, add:

```python
    p.add_argument(
        "--restore-drill", metavar="DUMP_SQL_GZ", default=None,
        help="verify an EXISTING dump against the live DB (restore to scratch, "
             "row-count + schema-hash equality), update its manifest, exit 0/1",
    )
```

In `cmd_backup`, replace everything from `# --- integrity check: restore to :memory: …` down to (and including) the final `return 0 if integrity_ok else 1` with:

```python
    # --- restore DRILL (spec §3.6 step 1): restore to a scratch FILE,
    #     row-count + schema-hash equality vs live; then write the manifest
    #     sidecar the migration runner's INV-3.1 gate reads. ---
    src2 = open_ro(db_path)
    src_schema = schema_hash(src2)
    src2.close()
    drill_ok, drill_detail = run_restore_drill(db_path, out_path)
    status = "PASS" if drill_ok else "FAIL"
    integrity_label = "OK" if drill_ok else f"FAIL ({drill_detail})"

    write_manifest(out_path, db_path, src_count, src_schema, status)

    print(f"[db-backup] integrity: {status}")
    print(f"[db-backup] restore drill: {status} ({drill_detail})")
    print(
        f"[db-backup] backed up {src_count} fsrs_cards"
        f" -> {out_path}"
        f" ({dump_size:,} bytes);"
        f" integrity: {integrity_label}"
    )
    return 0 if drill_ok else 1
```

In `main`, after the `db_path.exists()` check, add:

```python
    if args.restore_drill:
        return cmd_restore_drill(db_path, Path(args.restore_drill))
```

- [ ] **Run — expect GREEN (both files):**

```powershell
python tools/test_db_backup_hardening.py
python tools/test_db_backup.py
```

Expected: `OK` ×2 (10 tests + 5 tests). The old suite still passes because `"integrity: PASS"` and `"backed up N fsrs_cards"` strings and the exit contract are preserved.

- [ ] **Commit:**

```powershell
git add tools/db-backup.py tools/test_db_backup_hardening.py
git commit -m "feat(backup): restore drill (scratch restore, row-count + schema-hash vs live) + manifest sidecar"
```

---

## Task 3 — INV-3.1: backup-refusal gate inside `TutorMigration.migrate`

The runner itself must refuse any schema-mutating run over a protected corpus without a same-day verified dump. Scoping (so server boots, CLIs, and the ~10 existing migration tests keep working): the gate fires **only when** (a) this run has pending DDL (`SchemaUtils.statementsRequiredToActualizeScheme` non-empty) **and** (b) the DB holds a protected corpus (`fsrs_cards` count ≥ floor, default 800 / `MIN_EXPECTED_CARDS` — the same M-DB floor db-backup.py uses). Fresh test DBs (0 cards) and routine no-DDL boots stay un-gated; small fixtures (<800 rows) in existing tests stay un-gated; the live DB (828) is gated.

**Files:** `src/test/kotlin/jarvis/tutor/MigrationBackupGateTest.kt` (new), `src/main/kotlin/jarvis/tutor/MigrationBackupGate.kt` (new), `src/main/kotlin/jarvis/tutor/Migration.kt` (modified).

- [ ] **Write the failing tests.** Create `src/test/kotlin/jarvis/tutor/MigrationBackupGateTest.kt`:

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * INV-3.1 (spec §3.8) — the migration runner REFUSES any schema-mutating run over a protected
 * corpus (fsrs_cards >= floor) unless a same-day VERIFIED off-box dump manifest exists
 * (integrity PASS + restore_drill PASS + row-count + schema-hash equality with THIS DB).
 * Fresh/empty DBs and no-op (zero pending DDL) runs are NOT gated.
 */
class MigrationBackupGateTest {

    private fun tempDb(tmp: Path, name: String = "gate.db"): Database =
        TutorDb.connect(tmp.resolve(name).toString())

    /** Old-shape DB with N protected rows: a legacy fsrs_cards only — migrate() has pending DDL. */
    private fun seedOldShape(db: Database, cards: Int) {
        transaction(db) {
            exec(
                "CREATE TABLE IF NOT EXISTS fsrs_cards (" +
                    "id VARCHAR(26) PRIMARY KEY, user_id VARCHAR(26), source VARCHAR(24), " +
                    "source_ref VARCHAR(32), front TEXT, back TEXT, difficulty DOUBLE, " +
                    "stability DOUBLE, retrievability DOUBLE, due_at TEXT, last_reviewed_at TEXT, lapses INT)",
            )
            repeat(cards) { i ->
                exec(
                    "INSERT INTO fsrs_cards (id, user_id, source, source_ref, front, back, difficulty, " +
                        "stability, retrievability, due_at, last_reviewed_at, lapses) VALUES (" +
                        "'gate-$i','u','MANUAL','r','f','b',5.0,0.5,1.0," +
                        "'2026-06-11 00:00:00.000000','2026-06-11 00:00:00.000000',0)",
                )
            }
        }
    }

    private fun gate(dir: Path, floor: Long = 5, today: LocalDate = LocalDate.now()) =
        MigrationBackupGate(backupDir = dir, minExpectedCards = floor, today = { today })

    private fun schemaHashOf(db: Database): String =
        transaction(db) { MigrationBackupGate.liveSchemaHash(this) }

    private fun writeManifest(
        dir: Path,
        cards: Long,
        schemaHash: String,
        date: String = LocalDate.now().toString(),
        drill: String = "PASS",
        integrity: String = "PASS",
    ) {
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("jarvis-tutor-db-test.sql.gz.manifest.json"),
            """{"tool":"db-backup.py","manifest_version":1,"created_date":"$date",""" +
                """"created_at":"${date}T00:00:00Z","db_path":"t","dump_file":"jarvis-tutor-db-test.sql.gz",""" +
                """"fsrs_cards":$cards,"schema_hash":"$schemaHash","integrity":"$integrity","restore_drill":"$drill"}""",
        )
    }

    private fun tableExists(db: Database, name: String): Boolean = transaction(db) {
        var found = false
        exec("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'") { rs -> found = rs.next() }
        found
    }

    @Test
    fun `refuses pending DDL over a protected corpus without a manifest`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, cards = 10)

        val e = assertFailsWith<MigrationException> {
            TutorMigration.migrate(db, backupGate = gate(tmp.resolve("backups-empty")))
        }

        assertIs<BackupGateRefusal>(e.cause)
        assertTrue(e.message!!.contains("INV-3.1"), "refusal must name the invariant: ${e.message}")
        // refusal fired BEFORE any DDL: the new tables were never created…
        assertFalse(tableExists(db, "kc_verification_status"))
        // …and the protected rows are intact.
        assertEquals(10L, transaction(db) { MigrationBackupGate.liveFsrsCardCount(this) })
    }

    @Test
    fun `refuses a stale (yesterday) manifest`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db), date = LocalDate.now().minusDays(1).toString())

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("not today"), e.cause!!.message)
    }

    @Test
    fun `refuses a manifest whose card count does not cover the live data`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, cards = 9, schemaHash = schemaHashOf(db))

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("fsrs_cards"), e.cause!!.message)
    }

    @Test
    fun `refuses a failed restore drill`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db), drill = "FAIL")

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("restore_drill"), e.cause!!.message)
    }

    @Test
    fun `refuses a schema-hash mismatch (dump predates a schema change)`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHash = "deadbeef")

        val e = assertFailsWith<MigrationException> { TutorMigration.migrate(db, backupGate = gate(dir)) }
        assertTrue(e.cause!!.message!!.contains("schema_hash"), e.cause!!.message)
    }

    @Test
    fun `valid same-day verified manifest lets the migration proceed and data survives`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db))

        assertEquals(MigrationResult.Success, TutorMigration.migrate(db, backupGate = gate(dir)))

        assertEquals(10L, transaction(db) { MigrationBackupGate.liveFsrsCardCount(this) })
        assertTrue(tableExists(db, "kc_verification_status"))
    }

    @Test
    fun `fresh empty DB is not gated (nothing to protect)`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        // no manifest anywhere; the gate WOULD refuse if consulted
        assertEquals(
            MigrationResult.Success,
            TutorMigration.migrate(db, backupGate = gate(tmp.resolve("backups-empty"))),
        )
    }

    @Test
    fun `no pending DDL means no gate (routine re-boot needs no fresh dump)`(@TempDir tmp: Path) {
        val db = tempDb(tmp)
        seedOldShape(db, 10)
        val dir = tmp.resolve("backups")
        writeManifest(dir, 10, schemaHashOf(db))
        TutorMigration.migrate(db, backupGate = gate(dir)) // first run migrates with the manifest

        // second run: schema is current -> zero pending DDL -> a refusing gate must NOT fire
        assertEquals(
            MigrationResult.Success,
            TutorMigration.migrate(db, backupGate = gate(tmp.resolve("backups-empty"))),
        )
    }
}
```

- [ ] **Run — expect RED** (compile failure: `MigrationBackupGate` / `BackupGateRefusal` unresolved):

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.MigrationBackupGateTest"
```

Expected: `> Task :compileTestKotlin FAILED` with `Unresolved reference: MigrationBackupGate`.

- [ ] **Implement.** Create `src/main/kotlin/jarvis/tutor/MigrationBackupGate.kt`:

```kotlin
package jarvis.tutor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Transaction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/**
 * INV-3.1 (Plan-1 / spec §3.8) — the BACKUP-FIRST refusal gate for [TutorMigration.migrate].
 *
 * The migration runner refuses to run any SCHEMA-MUTATING step over a PROTECTED corpus
 * (fsrs_cards >= [minExpectedCards], the same M-DB floor `tools/db-backup.py` enforces) unless a
 * SAME-DAY VERIFIED dump manifest exists: `tools/db-backup.py` writes a `*.manifest.json` sidecar
 * next to every dump (Plan-1 Task 2) carrying integrity + restore-drill verdicts, the dumped
 * row count, and a schema hash; this gate demands integrity==PASS, restore_drill==PASS,
 * created_date == today, fsrs_cards == the live count, and schema_hash == the live schema hash
 * (so the dump provably covers THIS data at THIS schema).
 *
 * Scoping (deliberate): fresh DBs (0 protected rows — tests, first boot) and no-DDL boots are NOT
 * gated; the gate keys on data-at-risk, not on a default-off flag (the D-RF2 anti-pattern).
 */
@Serializable
data class BackupManifest(
    val tool: String = "db-backup.py",
    val manifest_version: Int = 1,
    val created_date: String,
    val created_at: String = "",
    val db_path: String = "",
    val dump_file: String = "",
    val fsrs_cards: Long,
    val schema_hash: String,
    val integrity: String,
    val restore_drill: String,
)

/** Thrown by the gate BEFORE any DDL has run. Wrapped into [MigrationException] by the runner. */
class BackupGateRefusal(message: String) : RuntimeException(message)

class MigrationBackupGate(
    private val backupDir: Path = defaultBackupDir(),
    val minExpectedCards: Long = defaultFloor(),
    private val today: () -> LocalDate = LocalDate::now,
) {

    /** Refuses (throws [BackupGateRefusal]) unless the newest manifest verifies against the live DB. */
    fun assertSafeToMutate(liveCards: Long, liveSchemaHash: String) {
        val m = newestManifest() ?: refuse(
            "no backup manifest (*.manifest.json) in $backupDir — run " +
                "`python tools/db-backup.py <off-box dir>` first (M-DB / same-day verified dump)",
        )
        val todayStr = today().toString()
        if (m.created_date != todayStr) {
            refuse("newest backup manifest is dated ${m.created_date}, not today ($todayStr) — take a fresh dump")
        }
        if (m.integrity != "PASS") refuse("newest backup manifest integrity=${m.integrity} (must be PASS)")
        if (m.restore_drill != "PASS") refuse("newest backup manifest restore_drill=${m.restore_drill} (must be PASS)")
        if (m.fsrs_cards != liveCards) {
            refuse("backup covers ${m.fsrs_cards} fsrs_cards but the live DB has $liveCards — the dump is not a backup of THIS data")
        }
        if (m.schema_hash != liveSchemaHash) {
            refuse("backup manifest schema_hash != live schema hash — the dump predates a schema change; take a fresh dump")
        }
    }

    private fun refuse(why: String): Nothing =
        throw BackupGateRefusal("migration REFUSED (INV-3.1 backup gate): $why")

    internal fun newestManifest(): BackupManifest? {
        if (!Files.isDirectory(backupDir)) return null
        val json = Json { ignoreUnknownKeys = true }
        return backupDir.listDirectoryEntries("*.manifest.json")
            .mapNotNull { p -> runCatching { json.decodeFromString<BackupManifest>(p.readText()) }.getOrNull() }
            .maxByOrNull { it.created_at }
    }

    companion object {
        fun defaultBackupDir(): Path =
            Path.of(System.getenv("JARVIS_BACKUP_DIR")?.takeIf { it.isNotBlank() } ?: "backups")

        fun defaultFloor(): Long =
            System.getenv("MIN_EXPECTED_CARDS")?.trim()?.toLongOrNull() ?: 800L

        /**
         * MUST stay byte-identical with tools/db-backup.py `schema_hash()`:
         * SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%';
         * strip each; sort; join with '\n'; sha256 hex digest.
         */
        fun liveSchemaHash(tx: Transaction): String {
            val sqls = ArrayList<String>()
            tx.exec("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'") { rs ->
                while (rs.next()) sqls.add(rs.getString(1).trim())
            }
            val canon = sqls.sorted().joinToString("\n")
            val digest = MessageDigest.getInstance("SHA-256").digest(canon.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** fsrs_cards row count; 0 when the table does not exist (fresh DB — nothing to protect). */
        fun liveFsrsCardCount(tx: Transaction): Long {
            var n = 0L
            try {
                tx.exec("SELECT COUNT(*) FROM fsrs_cards") { rs -> if (rs.next()) n = rs.getLong(1) }
            } catch (_: Exception) {
                return 0L
            }
            return n
        }
    }
}
```

- [ ] **Wire the gate into `Migration.kt`** (3 edits):

Edit A — add the parameter to `migrate` (directly after the `v2HashFlipEnabled` parameter, before the closing paren of the parameter list):

```kotlin
        /**
         * INV-3.1 (Plan-1, spec §3.8) — the BACKUP-FIRST refusal gate. Consulted ONLY when this run
         * has pending schema DDL AND the DB holds a protected corpus (fsrs_cards >= the gate's
         * floor, default 800 / MIN_EXPECTED_CARDS). Refuses (throws, NO DDL executed) unless a
         * same-day VERIFIED off-box dump manifest exists. Tests inject a gate pointed at a temp
         * manifest dir; production callers leave the default (reads JARVIS_BACKUP_DIR, else ./backups).
         */
        backupGate: MigrationBackupGate = MigrationBackupGate(),
```

Edit B — at the top of the `transaction(db) {` block, BEFORE `SchemaUtils.createMissingTablesAndColumns(*ALL_TABLES)`:

```kotlin
                // INV-3.1 — BACKUP-FIRST: compute the pending DDL; if this boot would MUTATE the
                // schema of a protected corpus, demand the same-day verified dump BEFORE any ALTER.
                val pendingDdl = SchemaUtils.statementsRequiredToActualizeScheme(*ALL_TABLES, withLogs = false)
                if (pendingDdl.isNotEmpty()) {
                    val liveCards = MigrationBackupGate.liveFsrsCardCount(this)
                    if (liveCards >= backupGate.minExpectedCards) {
                        backupGate.assertSafeToMutate(liveCards, MigrationBackupGate.liveSchemaHash(this))
                    }
                }
```

(If this Exposed version's `statementsRequiredToActualizeScheme` has no `withLogs` parameter, call it without that argument.)

Edit C — at the top of the existing `catch (e: Throwable) {` block, BEFORE the `extractFailingColumn` line:

```kotlin
            // INV-3.1 refusal: NOTHING was mutated (the gate throws before any DDL). Do NOT fire
            // the M-PARTIAL recovery backup — there is nothing to recover. Rethrow with the
            // refusal intact so callers/tests can read the reason.
            if (e is BackupGateRefusal) {
                System.err.println("[TutorMigration] ${e.message}")
                throw MigrationException(null, e)
            }
```

- [ ] **Run the new tests — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.MigrationBackupGateTest"
```

Expected: `BUILD SUCCESSFUL`, 8 tests passed.

- [ ] **Run the FULL suite** (the gate touches every migrate call site; per the review-workflow rule, never trust a partial run):

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`. The ~10 existing migration-test files keep passing because their fixtures hold < 800 cards (the floor self-skips them). If any test fails on the gate, that test seeds ≥800 rows — inject `backupGate = MigrationBackupGate(minExpectedCards = Long.MAX_VALUE)` is **forbidden** (it would be a bypass); instead point the test at a temp manifest dir via the `writeManifest` helper pattern above.

- [ ] **Commit:**

```powershell
git add src/main/kotlin/jarvis/tutor/MigrationBackupGate.kt src/main/kotlin/jarvis/tutor/Migration.kt src/test/kotlin/jarvis/tutor/MigrationBackupGateTest.kt
git commit -m "feat(trust-net): INV-3.1 backup-refusal gate in TutorMigration (same-day verified dump or refuse)"
```

---

## Task 4 — Pin the kc_verification_status +3-column migration against the exact live shape

The migration mechanism already exists (`createMissingTablesAndColumns` ALTER-adds the 3 nullable columns). What is missing is a test that pins the EXACT live 4-column shape → 7-column result, so the live apply (Task 7) is rehearsed in CI forever.

**Files:** `src/test/kotlin/jarvis/tutor/LiveShapeColumnsMigrationTest.kt` (new).

- [ ] **Write the test** (it should pass immediately — it pins behavior; if it fails, the migration is broken and must be fixed before anything touches the live DB):

```kotlin
package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-1 Tasks 4+5 — pins the EXACT live-DB column deltas the trust-net flip migration closes.
 * Live DDL captured READ-ONLY from ~/.jarvis/tutor.db on 2026-06-11:
 *   kc_verification_status = [kc_id, status, last_audit_run_id, updated_at]            (4 cols, 0 rows)
 *   report_wrong           = [id, user_id, kc_id, card_id, grade_attempt_raw,
 *                             reported_at, resolution]                                  (7 cols, 0 rows)
 * Code (Phase1Tables.kt) additionally defines content_hash/lecture_grounded/source_span_hash
 * (KcVerificationStatusTable) and resolved_by/resolved_at (ReportWrongTable, D-RF3).
 *
 * Replica technique: full-migrate a fresh DB, then DROP the new columns (sqlite-jdbc 3.45.3.0
 * bundles SQLite >= 3.35, which supports ALTER TABLE … DROP COLUMN) — yielding the exact live
 * shape — then re-migrate and assert the deltas.
 */
class LiveShapeColumnsMigrationTest {

    private fun liveShapeDb(tmp: Path, drops: Map<String, List<String>>): Database {
        val db = TutorDb.connect(tmp.resolve("live-shape.db").toString())
        TutorMigration.migrate(db) // fresh DB: the INV-3.1 gate self-skips (0 protected rows)
        transaction(db) {
            for ((table, cols) in drops) for (c in cols) exec("ALTER TABLE $table DROP COLUMN $c")
        }
        return db
    }

    private fun columnsOf(db: Database, table: String): List<String> = transaction(db) {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        cols
    }

    @Test
    fun `kc_verification_status gains exactly content_hash lecture_grounded source_span_hash`(@TempDir tmp: Path) {
        val db = liveShapeDb(
            tmp,
            mapOf("kc_verification_status" to listOf("content_hash", "lecture_grounded", "source_span_hash")),
        )
        assertEquals(
            listOf("kc_id", "status", "last_audit_run_id", "updated_at"),
            columnsOf(db, "kc_verification_status"),
            "replica must match the live 4-column shape before re-migration",
        )

        TutorMigration.migrate(db)

        assertEquals(
            setOf(
                "kc_id", "status", "last_audit_run_id", "updated_at",
                "content_hash", "lecture_grounded", "source_span_hash",
            ),
            columnsOf(db, "kc_verification_status").toSet(),
        )

        // idempotent (M-IDEMP): a second run is a no-op
        TutorMigration.migrate(db)
        assertEquals(7, columnsOf(db, "kc_verification_status").size)
    }
}
```

- [ ] **Run — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.LiveShapeColumnsMigrationTest"
```

Expected: `BUILD SUCCESSFUL`, 1 test passed. (If the DROP COLUMN statements fail, the bundled SQLite is older than expected — STOP and report; do not work around with a hand-built table.)

- [ ] **Commit:**

```powershell
git add src/test/kotlin/jarvis/tutor/LiveShapeColumnsMigrationTest.kt
git commit -m "test(trust-net): pin live 4-col kc_verification_status -> 7-col migration (the +3 trust columns)"
```

---

## Task 5 — Pin the report_wrong D-RF3 migration (+resolved_by/resolved_at) against the live shape

Section 0.3: the columns are ALREADY in code; this task pins the live 7-column → 9-column delta while the table is still empty (the D-RF3 un-backfillable-provenance window).

**Files:** `src/test/kotlin/jarvis/tutor/LiveShapeColumnsMigrationTest.kt` (extended).

- [ ] **Add the test** to `LiveShapeColumnsMigrationTest`:

```kotlin
    @Test
    fun `report_wrong gains exactly resolved_by and resolved_at, both nullable (D-RF3)`(@TempDir tmp: Path) {
        val db = liveShapeDb(tmp, mapOf("report_wrong" to listOf("resolved_by", "resolved_at")))
        assertEquals(
            listOf("id", "user_id", "kc_id", "card_id", "grade_attempt_raw", "reported_at", "resolution"),
            columnsOf(db, "report_wrong"),
            "replica must match the live 7-column shape before re-migration",
        )

        TutorMigration.migrate(db)

        assertEquals(
            setOf(
                "id", "user_id", "kc_id", "card_id", "grade_attempt_raw",
                "reported_at", "resolution", "resolved_by", "resolved_at",
            ),
            columnsOf(db, "report_wrong").toSet(),
        )

        // Both NULLable (PRAGMA notnull == 0): additive, no backfill needed on the empty live
        // table; a pre-existing OPEN row would read NULL = not-yet-resolved (Phase1Tables KDoc).
        transaction(db) {
            exec("PRAGMA table_info(report_wrong)") { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    if (name == "resolved_by" || name == "resolved_at") {
                        assertEquals(0, rs.getInt("notnull"), "$name must be nullable")
                    }
                }
            }
        }
    }
```

- [ ] **Run — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.LiveShapeColumnsMigrationTest"
```

Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Commit:**

```powershell
git add src/test/kotlin/jarvis/tutor/LiveShapeColumnsMigrationTest.kt
git commit -m "test(trust-net): pin report_wrong D-RF3 resolved_by/resolved_at live-shape migration"
```

---

## Task 6 — `trustInvariants` gradle task (INV-3.3 + schema + zero-faithful-now-nonzero + machine content-hash parity)

Built BEFORE the live apply so Tasks 7–8 can use it as their acceptance gate. The PARITY check is the machine replacement for the forbidden "manually spot-check 3 rows against the PDFs": for every faithful row, the stored `content_hash` must equal `ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))` recomputed from the live `content/` corpus — the same audit-side == serve-side identity the lock freezes (§I.1).

**Files:** `src/test/kotlin/jarvis/tutor/verify/TrustInvariantsTest.kt` (new), `src/main/kotlin/jarvis/tutor/verify/TrustInvariantsCli.kt` (new), `build.gradle.kts` (modified).

- [ ] **Write the failing tests.** Create `src/test/kotlin/jarvis/tutor/verify/TrustInvariantsTest.kt`:

```kotlin
package jarvis.tutor.verify

import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-1 Task 6 — the `gradle trustInvariants` core: INV-3.3 / SCHEMA / FLIP / PARITY.
 * Uses the REAL in-repo corpus (content/, gradle test workingDir = project root) so the PARITY
 * leg exercises the exact ContentRepo + kcContentHashOf path the serve gate uses.
 */
class TrustInvariantsTest {

    private val contentDir: Path = Path.of("content")

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("inv.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private fun insertVerdict(db: Database, kcId: String, hash: String?) {
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[status] = "faithful"
                it[lastAuditRunId] = "run-test-1"
                it[contentHash] = hash
                it[lectureGrounded] = true
                it[sourceSpanHash] = "0123456789abcdef"
                it[updatedAt] = Instant.now()
            }
        }
    }

    private fun realHashOf(kcId: String): String {
        val repo = ContentRepo(contentDir)
        val kc = repo.loadManifest().subjects
            .flatMap { repo.loadSubject(it.id).kcs }
            .single { it.id == kcId }
        return ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
    }

    @Test
    fun `all green when a faithful row carries the recomputed corpus hash`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", realHashOf("pa-kc-001"))

        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)

        assertEquals(emptyList(), failures)
    }

    @Test
    fun `zero faithful rows fails the FLIP invariant`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "FLIP" }, "$failures")
    }

    @Test
    fun `tampered content_hash fails PARITY (machine parity, no human spot-check)`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", "ffffffffffffffff")
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 0)
        assertTrue(failures.any { it.check == "PARITY" && it.detail.contains("pa-kc-001") }, "$failures")
    }

    @Test
    fun `card floor violation surfaces INV-3-3`(@TempDir tmp: Path) {
        val db = freshDb(tmp)
        insertVerdict(db, "pa-kc-001", realHashOf("pa-kc-001"))
        val failures = TrustInvariantsCli.check(db, contentDir, backupDir = null, floor = 800)
        assertTrue(failures.any { it.check == "INV-3.3" }, "$failures")
    }
}
```

- [ ] **Run — expect RED** (compile failure: `TrustInvariantsCli` unresolved):

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustInvariantsTest"
```

- [ ] **Implement.** Create `src/main/kotlin/jarvis/tutor/verify/TrustInvariantsCli.kt`:

```kotlin
package jarvis.tutor.verify

import jarvis.content.ContentReconcile
import jarvis.content.ContentRepo
import jarvis.tutor.MigrationBackupGate
import jarvis.tutor.TutorDb
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Plan-1 final gate — machine-checked "trust-net is ON" invariants over the REAL DB + the REAL
 * content corpus (spec §3.8 / §9.2 gate 2). Operator-run, PC-side: `gradle trustInvariants`.
 * READ-ONLY — never mutates the DB. Checks:
 *
 *   INV-3.3  fsrs_cards >= floor AND == the newest backup manifest's count (when a backup dir
 *            is supplied via JARVIS_BACKUP_DIR) — all 828 cards survived.
 *   SCHEMA   kc_verification_status carries the 7 frozen columns (lock §I.1); report_wrong
 *            carries resolved_by/resolved_at (D-RF3).
 *   FLIP     >= 1 row WHERE status='faithful' — the zero-faithful-rows-now-nonzero assertion.
 *   PARITY   every faithful row's content_hash == ContentReconcile.kcContentHashOf(claimsFor(kc))
 *            recomputed from content/ — the MACHINE parity check that replaces any manual PDF
 *            spot-check (no-oracle-inversion: the user never verifies content).
 */
object TrustInvariantsCli {

    data class Failure(val check: String, val detail: String)

    fun check(db: Database, contentDir: Path, backupDir: Path?, floor: Long): List<Failure> {
        val failures = ArrayList<Failure>()

        // ── INV-3.3: survival vs floor + the backup manifest ─────────────────────────────
        val cards = transaction(db) { MigrationBackupGate.liveFsrsCardCount(this) }
        if (cards < floor) failures += Failure("INV-3.3", "fsrs_cards=$cards < floor=$floor")
        if (backupDir != null) {
            val manifest = MigrationBackupGate(backupDir = backupDir, minExpectedCards = floor).newestManifest()
            when {
                manifest == null ->
                    failures += Failure("INV-3.3", "no *.manifest.json in $backupDir")
                manifest.fsrs_cards != cards ->
                    failures += Failure("INV-3.3", "fsrs_cards=$cards != backup manifest's ${manifest.fsrs_cards}")
            }
        }

        // ── SCHEMA: the frozen column sets ───────────────────────────────────────────────
        val kvs = transaction(db) { columnsOf("kc_verification_status") }
        val expectedKvs = setOf(
            "kc_id", "status", "last_audit_run_id", "content_hash",
            "lecture_grounded", "source_span_hash", "updated_at",
        )
        if (kvs.toSet() != expectedKvs) {
            failures += Failure("SCHEMA", "kc_verification_status=$kvs expected=$expectedKvs")
        }
        val rw = transaction(db) { columnsOf("report_wrong") }
        for (c in listOf("resolved_by", "resolved_at")) {
            if (c !in rw) failures += Failure("SCHEMA", "report_wrong missing $c (D-RF3)")
        }

        // ── FLIP: zero-faithful-rows-now-nonzero ─────────────────────────────────────────
        // Fail-soft on a PRE-MIGRATION schema (no content_hash column yet): the query throwing
        // means the trust columns are absent — report it as a FLIP failure, never crash.
        val faithfulRows: List<Pair<String, String?>> = runCatching {
            transaction(db) {
                val rows = ArrayList<Pair<String, String?>>()
                exec("SELECT kc_id, content_hash FROM kc_verification_status WHERE status='faithful'") { rs ->
                    while (rs.next()) rows.add(rs.getString(1) to rs.getString(2))
                }
                rows
            }
        }.getOrElse {
            failures += Failure("FLIP", "kc_verification_status not queryable with trust columns (pre-migration schema?): ${it.message?.take(120)}")
            emptyList()
        }
        if (faithfulRows.isEmpty() && failures.none { it.check == "FLIP" }) {
            failures += Failure("FLIP", "0 faithful rows in kc_verification_status — the trust-net is still OFF")
        }

        // ── PARITY: stored content_hash == recompute from the live corpus ────────────────
        if (faithfulRows.isNotEmpty()) {
            val repo = ContentRepo(contentDir)
            val kcsById = repo.loadManifest().subjects
                .flatMap { repo.loadSubject(it.id).kcs }
                .associateBy { it.id }
            for ((kcId, stored) in faithfulRows) {
                val kc = kcsById[kcId]
                when {
                    kc == null ->
                        failures += Failure("PARITY", "$kcId is faithful in the DB but absent from $contentDir")
                    stored == null ->
                        failures += Failure("PARITY", "$kcId is faithful with NULL content_hash (fails closed at serve — re-audit it)")
                    else -> {
                        val recomputed = ContentReconcile.kcContentHashOf(ContentReconcile.claimsFor(kc))
                        if (recomputed != stored) {
                            failures += Failure(
                                "PARITY",
                                "$kcId stored=$stored recomputed=$recomputed (content edited after the audit?)",
                            )
                        }
                    }
                }
            }
        }
        return failures
    }

    private fun org.jetbrains.exposed.sql.Transaction.columnsOf(table: String): List<String> {
        val cols = ArrayList<String>()
        exec("PRAGMA table_info($table)") { rs -> while (rs.next()) cols.add(rs.getString("name")) }
        return cols
    }
}

/** Gradle `trustInvariants` main. Read-only. Exit 0 = ALL PASS; exit 1 = failures on stderr. */
fun main(args: Array<String>) {
    val contentDir = Path.of(
        args.getOrNull(0)
            ?: System.getProperty("JARVIS_CONTENT_DIR")
            ?: System.getenv("JARVIS_CONTENT_DIR")
            ?: "content",
    )
    val dbPath = System.getenv("JARVIS_TUTOR_DB")
        ?: System.getProperty("JARVIS_TUTOR_DB")
        ?: jarvis.Config.tutorDbPath
    val backupDir = System.getenv("JARVIS_BACKUP_DIR")?.takeIf { it.isNotBlank() }?.let(Path::of)
    val floor = MigrationBackupGate.defaultFloor()

    val failures = TrustInvariantsCli.check(TutorDb.connect(dbPath), contentDir, backupDir, floor)
    if (failures.isEmpty()) {
        println("[trustInvariants] ALL PASS (db=$dbPath, content=$contentDir, floor=$floor)")
        return
    }
    for (f in failures) System.err.println("[trustInvariants] FAIL ${f.check}: ${f.detail}")
    exitProcess(1)
}
```

- [ ] **Register the gradle task.** In `build.gradle.kts`, directly after the `reconcileContent` task registration (line ~217), add:

```kotlin
// Plan-1 (trust-net ON): machine-checked invariants over the REAL DB + corpus. READ-ONLY.
// INV-3.3 (828 cards survive, vs the newest backup manifest when JARVIS_BACKUP_DIR is set),
// frozen schema columns, >=1 faithful row, and content-hash PARITY (stored == recomputed from
// content/ — the machine replacement for manual spot-checks). Owner/manual; NOT part of check
// (it reads the runtime DB).
tasks.register<JavaExec>("trustInvariants") {
    group = "verification"
    description = "Trust-net ON invariants (INV-3.3, schema, >=1 faithful, content-hash parity) " +
        "against the REAL DB (JARVIS_TUTOR_DB) + content/ corpus. Read-only; NOT part of check."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.tutor.verify.TrustInvariantsCliKt")
    args = (project.findProperty("invariantsArgs") as String?)?.split(" ")?.filter { it.isNotEmpty() } ?: listOf()
    jvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
```

- [ ] **Run — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustInvariantsTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Sanity-run the task against the live DB (read-only) — expect the honest CURRENT failures:**

```powershell
gradle --no-daemon trustInvariants
```

Expected (this is the "before" picture — the task exits 1):

```
[trustInvariants] FAIL SCHEMA: kc_verification_status=[kc_id, status, last_audit_run_id, updated_at] expected=[...7 columns...]
[trustInvariants] FAIL SCHEMA: report_wrong missing resolved_by (D-RF3)
[trustInvariants] FAIL SCHEMA: report_wrong missing resolved_at (D-RF3)
[trustInvariants] FAIL FLIP: kc_verification_status not queryable with trust columns (pre-migration schema?): ...no such column: content_hash...
```

- [ ] **Commit:**

```powershell
git add src/main/kotlin/jarvis/tutor/verify/TrustInvariantsCli.kt src/test/kotlin/jarvis/tutor/verify/TrustInvariantsTest.kt build.gradle.kts
git commit -m "feat(trust-net): trustInvariants gradle task — INV-3.3 + schema + faithful-nonzero + content-hash parity"
```

---

## Task 7 — Apply the migrations to the LIVE PC DB (gated; rehearsal on a copy FIRST)

This is the first task that mutates real data. Sequence: rehearse everything on a copy → take the real off-box backup → run the gated migration with the one-time DELTA-4 flip fence → verify. The live DB default path is `~/.jarvis/tutor.db`; note `jarvis migrate` reads the **`JARVIS_DB`** env var (not `JARVIS_TUTOR_DB`) — this plan always passes the explicit path argument instead, to remove that trap.

### 7a — Rehearsal on a copy

- [ ] Stop any local process holding the DB (the local web server if running). Do **NOT** stop the relay on :9999 — Task 8 needs it:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Confirm:$false }
```

- [ ] Make the rehearsal copy:

```powershell
$reh = "$env:USERPROFILE\trustnet-rehearsal"
New-Item -ItemType Directory -Force $reh | Out-Null
Copy-Item "$env:USERPROFILE\.jarvis\tutor.db" "$reh\tutor-copy.db"
```

- [ ] **Prove the refusal path on real data (INV-3.1 live proof):** migrate the copy with NO backup taken:

```powershell
$env:JARVIS_BACKUP_DIR = "$reh\backups"   # empty — no manifest yet
gradle --no-daemon run --args="migrate $env:USERPROFILE\trustnet-rehearsal\tutor-copy.db"
```

Expected: **BUILD FAILED** (non-zero) with stderr containing:

```
[TutorMigration] migration REFUSED (INV-3.1 backup gate): no backup manifest (*.manifest.json) in ...
[migrate] FAILURE at column=null: ...
```

- [ ] Back up the copy (drill included), then migrate it with the flip fence on:

```powershell
python tools/db-backup.py --db "$env:USERPROFILE\trustnet-rehearsal\tutor-copy.db" "$reh\backups"
```

Expected:

```
[db-backup] integrity: PASS
[db-backup] restore drill: PASS (cards 828==828, schema_hash match)
[db-backup] backed up 828 fsrs_cards -> ...\jarvis-tutor-db-YYYYMMDD-HHMMSS.sql.gz (N,NNN,NNN bytes); integrity: OK
```

```powershell
$env:JARVIS_V2_HASH_FLIP = "1"    # DELTA-4: one-time flip fence (asserts 0 live content_hash rows)
gradle --no-daemon run --args="migrate $env:USERPROFILE\trustnet-rehearsal\tutor-copy.db"
Remove-Item Env:\JARVIS_V2_HASH_FLIP
```

Expected: `[migrate] SUCCESS — fsrs_cards before=828 after=828`

- [ ] Verify the copy's schema + survival:

```powershell
python -c "import sqlite3,sys; con=sqlite3.connect(sys.argv[1]); print([c[1] for c in con.execute('PRAGMA table_info(kc_verification_status)')]); print([c[1] for c in con.execute('PRAGMA table_info(report_wrong)')]); print('cards:', con.execute('SELECT COUNT(*) FROM fsrs_cards').fetchone()[0])" "$env:USERPROFILE\trustnet-rehearsal\tutor-copy.db"
```

Expected (column ORDER may differ — the SET is what matters):

```
['kc_id', 'status', 'last_audit_run_id', 'updated_at', 'content_hash', 'lecture_grounded', 'source_span_hash']
['id', 'user_id', 'kc_id', 'card_id', 'grade_attempt_raw', 'reported_at', 'resolution', 'resolved_by', 'resolved_at']
cards: 828
```

This rehearsal also proves the **python↔Kotlin schema-hash parity** end-to-end: the manifest was written by python and consumed green by the Kotlin gate.

### 7b — The real apply

- [ ] Take the real backup + restore drill, then copy it OFF-BOX (the VPS is the off-box target):

```powershell
$bk = "$env:USERPROFILE\jarvis-backups\$(Get-Date -Format yyyy-MM-dd)"
New-Item -ItemType Directory -Force $bk | Out-Null
python tools/db-backup.py $bk
# expected: integrity: PASS / restore drill: PASS (cards 828==828, schema_hash match) / backed up 828 fsrs_cards ...

ssh root@46.247.109.91 "mkdir -p /opt/jarvis/backups/pc"
Get-ChildItem $bk | ForEach-Object { scp $_.FullName root@46.247.109.91:/opt/jarvis/backups/pc/ }
ssh root@46.247.109.91 "ls -la /opt/jarvis/backups/pc/"   # confirm dump + manifest landed
```

- [ ] Run the gated migration on the LIVE DB (same day as the backup; flip fence on for this one boot):

```powershell
$env:JARVIS_BACKUP_DIR = $bk
$env:JARVIS_V2_HASH_FLIP = "1"
gradle --no-daemon run --args="migrate $env:USERPROFILE\.jarvis\tutor.db"
Remove-Item Env:\JARVIS_V2_HASH_FLIP
```

Expected: `[migrate] SUCCESS — fsrs_cards before=828 after=828`

- [ ] Verify the live DB exactly as in 7a (same python one-liner, path `$env:USERPROFILE\.jarvis\tutor.db`). Expected: the 7-column + 9-column sets and `cards: 828`.

- [ ] Run the invariants (FLIP is EXPECTED to still fail — the audit has not run yet; everything else must pass):

```powershell
gradle --no-daemon trustInvariants
```

Expected: exactly one failure line remains:

```
[trustInvariants] FAIL FLIP: 0 faithful rows in kc_verification_status — the trust-net is still OFF
```

If any SCHEMA or INV-3.3 line appears: STOP. Restore from the dump (`gunzip` the `.sql.gz`, replay into a fresh file, swap in) and re-assess.

No commit (this task changes no repo files).

---

## Task 8 — Run reconcile + verifyContent over the REAL PA corpus → ≥1 FAITHFUL row, machine-verified

### 8a — Preflight the verifier deps (the H4 families)

- [ ] Provision the env (PC, PowerShell):

```powershell
$env:JARVIS_RELAY_URL = "http://127.0.0.1:9999"
$env:JARVIS_RELAY_TOKEN = (Get-Content "$env:USERPROFILE\.jarvis-relay-token" -Raw).Trim()
# JARVIS_PYTHON3 = a python with torch + transformers + pyyaml (the interpreter that runs
# tools/nli_dryrun.py / nli_spike.py). Verify, then export:
python -c "import torch, transformers, yaml; print('NLI deps OK')"
$env:JARVIS_PYTHON3 = (Get-Command python).Source
```

If the import check fails, install into that interpreter: `pip install torch transformers pyyaml sentencepiece` (local model `MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli` downloads to the HF cache on first use, ~700 MB — free, local, no paid API).

- [ ] Relay health check:

```powershell
curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $env:JARVIS_RELAY_TOKEN" http://127.0.0.1:9999/healthz
```

Expected: `200`. If not, re-run `tools/install-pc-relay.ps1` and retry.

- [ ] **Negative proof (the fail-loud is alive):** in a FRESH PowerShell window with none of the three env vars set, run `gradle --no-daemon verifyContent` — expected: BUILD FAILED, stderr contains `verifyContent ABORTED (FAIL-LOUD, H4): … Missing env: JARVIS_RELAY_URL, JARVIS_RELAY_TOKEN, JARVIS_PYTHON3`. Close that window; continue in the provisioned one.

### 8b — Reconcile, audit, assert

- [ ] Stage-9 reconcile (no LLM; validates corpus, runs the D1 watcher, sets UNVERIFIED→PENDING):

```powershell
gradle --no-daemon reconcileContent
```

Expected output shape: `[reconcileContent] reconciled N claim(s); promoted M KC(s) to pending.` (N, M > 0; covers all subjects in `content/`).

- [ ] The audit — PA only (the subject with the SymPy non-LLM leg):

```powershell
gradle --no-daemon verifyContent "-PverifyArgs=content PA"
```

Expected output shape (claim count depends on the corpus; per-claim lines list every verdict):

```
verifyContent: audited N claims -> {faithful=F, uncertain=U}
  pa-kc-001 <claimId> : pending -> faithful (ALL_AGREE_ROUNDTRIP_NONLLM_PASS)
  ...
```

Acceptance: **F ≥ 1 at the claim level AND ≥1 KC aggregates to faithful** (next step asserts it in the DB). `uncertain` rows are legal (EQUATIONAL/PROSE_LLM_UNCONFIRMED floors); any `failed` row means the corpus contradicts its source — park it, do NOT edit content to force green, and do NOT ask the user to judge it (no-oracle-inversion; a failed KC simply stays unserved).

- [ ] Assert the flip in the DB:

```powershell
python -c "import sqlite3,pathlib; con=sqlite3.connect((pathlib.Path.home()/'.jarvis'/'tutor.db').as_uri()+'?mode=ro',uri=True); print(dict(con.execute('SELECT status, COUNT(*) FROM kc_verification_status GROUP BY status').fetchall()))"
```

Expected shape: `{'faithful': >=1, 'pending': ..., 'uncertain': ...}`.

- [ ] **Machine parity (replaces any manual spot-check):**

```powershell
$env:JARVIS_BACKUP_DIR = $bk    # same dir as Task 7b, same day
gradle --no-daemon trustInvariants
```

Expected: `[trustInvariants] ALL PASS (db=..., content=content, floor=800)` — INV-3.3 (828==manifest), SCHEMA, FLIP (≥1 faithful), PARITY (every faithful row's hash recomputes identically from `content/`).

- [ ] **Machine re-check of the audited rows (stability re-audit; the spec's re-audits-stay-runnable guarantee):** run the audit a second time with a fresh `audit_run_id` (automatic — one ULID per invocation) and assert the verdict distribution is stable and the run was recorded separately:

```powershell
gradle --no-daemon verifyContent "-PverifyArgs=content PA"
python -c "import sqlite3,pathlib; con=sqlite3.connect((pathlib.Path.home()/'.jarvis'/'tutor.db').as_uri()+'?mode=ro',uri=True); runs=con.execute('SELECT audit_run_id, COUNT(*) FROM verification_audit GROUP BY audit_run_id').fetchall(); print('runs:', runs)"
```

Expected: the second run's `{faithful=…, uncertain=…}` summary equals the first run's, and `runs:` shows **2 distinct audit_run_id values with equal claim counts**. This works because `JARVIS_V2_HASH_FLIP` is unset now — the DELTA-4 zero-row guard was flip-time only (spec §9.2 gate 2 scoping note), so re-audits over the populated table run normally. If the distributions differ, the audit is flaky — investigate the leg logs before proceeding; do not hand-pick the greener run.

No commit (no repo files changed; the DB is runtime state).

---

## Task 9 — D9 export: `TrustSync.export` + `jarvis trust-export`

**Files:** `src/test/kotlin/jarvis/tutor/verify/TrustSyncTest.kt` (new), `src/main/kotlin/jarvis/tutor/verify/TrustSync.kt` (new), `src/main/kotlin/jarvis/Main.kt` (modified).

- [ ] **Write the failing tests.** Create `src/test/kotlin/jarvis/tutor/verify/TrustSyncTest.kt`:

```kotlin
package jarvis.tutor.verify

import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D9 — the PC→VPS trust-verdict sync, against the FROZEN audit-column allowlist
 * (interface-signatures-lock §I.1): export carries EXACTLY {kc_id, status, last_audit_run_id,
 * content_hash, lecture_grounded, source_span_hash, updated_at}; import is idempotent (same
 * dump twice = 0 applied) and monotonic (an older dump never overwrites a newer verdict);
 * report_wrong is NEVER touched; never the whole tutor.db.
 */
class TrustSyncTest {

    private fun db(tmp: Path, name: String): Database {
        val d = TutorDb.connect(tmp.resolve(name).toString())
        TutorMigration.migrate(d)
        return d
    }

    private fun seedVerdict(db: Database, kcId: String, status: String, runId: String, at: Instant) {
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[KcVerificationStatusTable.status] = status
                it[lastAuditRunId] = runId
                it[contentHash] = "00aa11bb22cc33dd"
                it[lectureGrounded] = true
                it[sourceSpanHash] = "44ee55ff66aa77bb"
                it[updatedAt] = at
            }
        }
    }

    @Test
    fun `export carries exactly the frozen allowlist columns`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))

        val dump = TrustSync.export(pc)

        assertEquals(1, dump.rows.size)
        val out = tmp.resolve("dump.json")
        TrustSync.writeDump(dump, out)
        val text = Files.readString(out)
        for (key in listOf(
            "kc_id", "status", "last_audit_run_id",
            "content_hash", "lecture_grounded", "source_span_hash", "updated_at",
        )) {
            assertTrue(key in text, "dump must carry $key")
        }
        assertFalse("report_wrong" in text, "D9 NEVER syncs report_wrong")
        assertFalse("grade_attempt_raw" in text)
    }

    @Test
    fun `export with a run filter carries only that audit run's rows`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        seedVerdict(pc, "pa-kc-002", "faithful", "run-2", Instant.parse("2026-06-11T11:00:00Z"))

        val dump = TrustSync.export(pc, auditRunId = "run-2")

        assertEquals(listOf("pa-kc-002"), dump.rows.map { it.kc_id })
    }
}
```

- [ ] **Run — expect RED** (compile failure: `TrustSync` unresolved):

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustSyncTest"
```

- [ ] **Implement.** Create `src/main/kotlin/jarvis/tutor/verify/TrustSync.kt` (export half + CLI shells; `importDump` lands in Task 10 — write the whole file now, it is one coherent unit):

```kotlin
package jarvis.tutor.verify

import jarvis.tutor.KcVerificationStatusTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * D9 — the PC→VPS trust-verdict sync (spec §9.2 gate 2 step 4; FROZEN allowlist,
 * interface-signatures-lock §I.1):
 *
 *   "the set D9 surgically upserts PC→VPS is exactly {status, content_hash, lecture_grounded,
 *    source_span_hash, last_audit_run_id}. D9 NEVER syncs report_wrong rows and NEVER the whole
 *    tutor.db. These five plus the PK kc_id + bookkeeping updated_at are the table."
 *
 * Export runs PC-side after a verifyContent batch (`jarvis trust-export`); import runs on the
 * VPS via the dist binary (`jarvis trust-import <file>`). Both idempotent:
 *  - re-importing the same dump applies 0 changes (skip when existing.updated_at >= incoming);
 *  - an OLDER dump never overwrites a NEWER verdict (same monotonic guard);
 *  - rows carry last_audit_run_id, so every applied verdict is keyed to the audit run that
 *    produced it (re-audits produce a new run id + later updated_at and flow through).
 * The serve gate (VerifyAdmin.resolveStatus) independently re-checks content_hash against the
 * VPS's own content/ at read time, so a verdict synced over stale content fails CLOSED to
 * unverified — the import cannot create a lying badge.
 */
object TrustSync {

    @Serializable
    data class VerdictRow(
        val kc_id: String,
        val status: String,
        val last_audit_run_id: String?,
        val content_hash: String?,
        val lecture_grounded: Boolean?,
        val source_span_hash: String?,
        val updated_at: String, // ISO-8601 Instant
    )

    @Serializable
    data class VerdictDump(
        val version: Int = 1,
        val exported_at: String,
        val rows: List<VerdictRow>,
    )

    data class ImportReport(val inserted: Int, val updated: Int, val skipped: Int) {
        val applied: Int get() = inserted + updated
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** All verdict rows (or only those of [auditRunId]), kc_id-sorted for a deterministic dump. */
    fun export(db: Database, auditRunId: String? = null, clock: () -> Instant = Instant::now): VerdictDump =
        transaction(db) {
            val rows = KcVerificationStatusTable.selectAll()
                .map {
                    VerdictRow(
                        kc_id = it[KcVerificationStatusTable.kcId],
                        status = it[KcVerificationStatusTable.status],
                        last_audit_run_id = it[KcVerificationStatusTable.lastAuditRunId],
                        content_hash = it[KcVerificationStatusTable.contentHash],
                        lecture_grounded = it[KcVerificationStatusTable.lectureGrounded],
                        source_span_hash = it[KcVerificationStatusTable.sourceSpanHash],
                        updated_at = it[KcVerificationStatusTable.updatedAt].toString(),
                    )
                }
                .filter { auditRunId == null || it.last_audit_run_id == auditRunId }
                .sortedBy { it.kc_id }
            VerdictDump(exported_at = clock().toString(), rows = rows)
        }

    /**
     * Surgical upsert of [dump] into kc_verification_status. Touches NOTHING else (no
     * report_wrong, no other table). Idempotent + monotonic: a row is skipped when the existing
     * row's updated_at >= the incoming row's (same dump re-applied, or a stale dump).
     */
    fun importDump(db: Database, dump: VerdictDump): ImportReport {
        var inserted = 0
        var updated = 0
        var skipped = 0
        transaction(db) {
            for (row in dump.rows) {
                val incoming = Instant.parse(row.updated_at)
                val existing = KcVerificationStatusTable.selectAll()
                    .where { KcVerificationStatusTable.kcId eq row.kc_id }
                    .singleOrNull()
                when {
                    existing == null -> {
                        KcVerificationStatusTable.insert {
                            it[kcId] = row.kc_id
                            it[status] = row.status
                            it[lastAuditRunId] = row.last_audit_run_id
                            it[contentHash] = row.content_hash
                            it[lectureGrounded] = row.lecture_grounded
                            it[sourceSpanHash] = row.source_span_hash
                            it[updatedAt] = incoming
                        }
                        inserted++
                    }
                    existing[KcVerificationStatusTable.updatedAt] >= incoming -> skipped++
                    else -> {
                        KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq row.kc_id }) {
                            it[status] = row.status
                            it[lastAuditRunId] = row.last_audit_run_id
                            it[contentHash] = row.content_hash
                            it[lectureGrounded] = row.lecture_grounded
                            it[sourceSpanHash] = row.source_span_hash
                            it[updatedAt] = incoming
                        }
                        updated++
                    }
                }
            }
        }
        return ImportReport(inserted, updated, skipped)
    }

    fun writeDump(dump: VerdictDump, out: Path) {
        Files.writeString(out, json.encodeToString(dump))
    }

    fun readDump(path: Path): VerdictDump = json.decodeFromString(Files.readString(path))
}

/** Thin process shells for the Main.kt subcommands (mirrors ContentReconcileCli's split). */
object TrustSyncCli {

    private fun connectDb(): Database {
        val dbPath = System.getenv("JARVIS_TUTOR_DB")
            ?: System.getProperty("JARVIS_TUTOR_DB")
            ?: jarvis.Config.tutorDbPath
        return jarvis.tutor.TutorDb.connect(dbPath)
    }

    /** `jarvis trust-export [out.json] [--run <auditRunId>]` — PC-side. */
    fun runExport(args: List<String>) {
        val runIdx = args.indexOf("--run")
        val auditRunId = if (runIdx >= 0) args.getOrNull(runIdx + 1) else null
        val out = Path.of(
            args.firstOrNull { !it.startsWith("--") && it != auditRunId } ?: "trust-verdicts.json",
        )
        val db = connectDb()
        jarvis.tutor.TutorMigration.migrate(db)
        val dump = TrustSync.export(db, auditRunId)
        TrustSync.writeDump(dump, out)
        println(
            "[trust-export] wrote ${dump.rows.size} verdict row(s) -> $out" +
                (auditRunId?.let { " (run=$it)" } ?: ""),
        )
    }

    /** `jarvis trust-import <in.json>` — VPS-side (dist binary). */
    fun runImport(args: List<String>) {
        val path = args.firstOrNull() ?: run {
            System.err.println("Usage: jarvis trust-import <verdicts.json>")
            kotlin.system.exitProcess(2)
        }
        val db = connectDb()
        jarvis.tutor.TutorMigration.migrate(db)
        val report = TrustSync.importDump(db, TrustSync.readDump(Path.of(path)))
        println(
            "[trust-import] inserted=${report.inserted} updated=${report.updated} " +
                "skipped=${report.skipped} (rows=${report.inserted + report.updated + report.skipped})",
        )
    }
}
```

- [ ] **Wire the subcommands into `src/main/kotlin/jarvis/Main.kt`.** In the `when (args.firstOrNull())` block (after the `"migrate"` case at line ~76), add:

```kotlin
        "trust-export" -> jarvis.tutor.verify.TrustSyncCli.runExport(args.drop(1))
        "trust-import" -> jarvis.tutor.verify.TrustSyncCli.runImport(args.drop(1))
```

and add to the `USAGE` string (after the `ingest-corpus` line):

```
  trust-export [out.json] [--run <id>]
                             D9 PC-side: dump kc_verification_status verdict rows
                             (frozen allowlist) to JSON for VPS import.
  trust-import <in.json>     D9 VPS-side: surgical, idempotent upsert of a verdict
                             dump into kc_verification_status. Never report_wrong.
```

- [ ] **Run — expect GREEN:**

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustSyncTest"
```

Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Commit:**

```powershell
git add src/main/kotlin/jarvis/tutor/verify/TrustSync.kt src/main/kotlin/jarvis/Main.kt src/test/kotlin/jarvis/tutor/verify/TrustSyncTest.kt
git commit -m "feat(trust-net): D9 trust-export — verdict dump over the frozen audit-column allowlist"
```

---

## Task 10 — D9 import: idempotent, monotonic, report_wrong-untouchable

**Files:** `src/test/kotlin/jarvis/tutor/verify/TrustSyncTest.kt` (extended). (`TrustSync.importDump` was written in Task 9 — these tests prove its contract; if any fails, fix `importDump`, not the test.)

- [ ] **Add the failing/contract tests** to `TrustSyncTest`:

```kotlin
    @Test
    fun `import inserts then re-import is a no-op (idempotent)`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        seedVerdict(pc, "pa-kc-002", "uncertain", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        val dump = TrustSync.export(pc)

        val first = TrustSync.importDump(vps, dump)
        assertEquals(2, first.inserted)
        assertEquals(0, first.updated)
        assertEquals(0, first.skipped)

        val second = TrustSync.importDump(vps, dump)
        assertEquals(0, second.inserted)
        assertEquals(0, second.updated)
        assertEquals(2, second.skipped)

        val statuses = transaction(vps) {
            KcVerificationStatusTable.selectAll()
                .associate { it[KcVerificationStatusTable.kcId] to it[KcVerificationStatusTable.status] }
        }
        assertEquals(mapOf("pa-kc-001" to "faithful", "pa-kc-002" to "uncertain"), statuses)
    }

    @Test
    fun `an older dump never overwrites a newer verdict`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        val oldDump = TrustSync.export(pc)
        // the VPS later holds a NEWER verdict (run-2)
        seedVerdict(vps, "pa-kc-001", "failed", "run-2", Instant.parse("2026-06-12T09:00:00Z"))

        val report = TrustSync.importDump(vps, oldDump)

        assertEquals(0, report.applied)
        assertEquals(1, report.skipped)
        val status = transaction(vps) {
            KcVerificationStatusTable.selectAll().single()[KcVerificationStatusTable.status]
        }
        assertEquals("failed", status, "a stale dump must not clobber the newer verdict")
    }

    @Test
    fun `a newer re-audit run updates the same kc (re-audits stay runnable, keyed by audit_run_id)`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "uncertain", "run-1", Instant.parse("2026-06-11T10:00:00Z"))
        TrustSync.importDump(vps, TrustSync.export(pc))

        // PC re-audits: same KC, NEW run id, new verdict, later updated_at
        transaction(pc) {
            KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq "pa-kc-001" }) {
                it[status] = "faithful"
                it[lastAuditRunId] = "run-2"
                it[updatedAt] = Instant.parse("2026-06-12T10:00:00Z")
            }
        }

        val report = TrustSync.importDump(vps, TrustSync.export(pc))

        assertEquals(1, report.updated)
        val row = transaction(vps) { KcVerificationStatusTable.selectAll().single() }
        assertEquals("faithful", row[KcVerificationStatusTable.status])
        assertEquals("run-2", row[KcVerificationStatusTable.lastAuditRunId])
    }

    @Test
    fun `import never touches report_wrong`(@TempDir tmp: Path) {
        val pc = db(tmp, "pc.db")
        val vps = db(tmp, "vps.db")
        seedVerdict(pc, "pa-kc-001", "faithful", "run-1", Instant.parse("2026-06-11T10:00:00Z"))

        fun reportWrongCount(): Long = transaction(vps) {
            var n = -1L
            exec("SELECT COUNT(*) FROM report_wrong") { rs -> if (rs.next()) n = rs.getLong(1) }
            n
        }
        val before = reportWrongCount()
        assertEquals(0L, before)

        TrustSync.importDump(vps, TrustSync.export(pc))

        assertEquals(before, reportWrongCount(), "D9 must not write report_wrong (frozen allowlist)")
    }
```

- [ ] **Run — expect GREEN** (if any of these fail, fix `TrustSync.importDump` until green; do not weaken the asserts):

```powershell
gradle --no-daemon :test --tests "jarvis.tutor.verify.TrustSyncTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Run the FULL suite** (Main.kt changed in Task 9; prove nothing else broke):

```powershell
gradle --no-daemon :test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Commit:**

```powershell
git add src/test/kotlin/jarvis/tutor/verify/TrustSyncTest.kt
git commit -m "test(trust-net): D9 import contract — idempotent, monotonic, report_wrong untouched"
```

---

## Task 11 — Run the D9 sync for real: VPS backup → deploy → export → import → serve check

The VPS DB is a SEPARATE SQLite at the path `/opt/jarvis/.env`'s `JARVIS_TUTOR_DB` points to (default `~/.jarvis/tutor.db` of the service user). After deploy, the service's boot-time migrate has pending DDL there too — the INV-3.1 gate applies, so the VPS backup happens FIRST, same day.

- [ ] **Back up the VPS DB with the hardened tool** (the dump is then pulled to the PC — that is the VPS's off-box copy):

```powershell
scp tools/db-backup.py root@46.247.109.91:/opt/jarvis/db-backup.py
# NOTE: single-quoted in PowerShell so $ reaches the REMOTE bash untouched.
ssh root@46.247.109.91 'set -a; . /opt/jarvis/.env; set +a; mkdir -p /opt/jarvis/backups; python3 /opt/jarvis/db-backup.py --db "${JARVIS_TUTOR_DB:-/root/.jarvis/tutor.db}" /opt/jarvis/backups'
```

Expected: the same three-line PASS output as Task 7 (the VPS card count may differ from 828 — that DB has its own history; whatever it is, the manifest must say PASS). Pull the off-box copy:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\jarvis-backups\vps" | Out-Null
scp "root@46.247.109.91:/opt/jarvis/backups/*.sql.gz*" "$env:USERPROFILE\jarvis-backups\vps\"
```

(If the VPS DB holds fewer than 800 cards the boot gate will self-skip there; the backup is still taken — belt and braces.)

- [ ] **Point the VPS service at the manifest dir** so the boot-time gate can verify it:

```powershell
ssh root@46.247.109.91 "grep -q '^JARVIS_BACKUP_DIR=' /opt/jarvis/.env || echo 'JARVIS_BACKUP_DIR=/opt/jarvis/backups' >> /opt/jarvis/.env; grep '^JARVIS_BACKUP_DIR=' /opt/jarvis/.env"
```

Expected: `JARVIS_BACKUP_DIR=/opt/jarvis/backups`

- [ ] **Deploy** (same day as the VPS backup; deploy.sh builds, runs `gradle :test`, ships the dist + the `content/` corpus — the corpus copy is what makes the serve-side content-hash recompute match):

```powershell
bash tools/deploy.sh
```

Expected: deploy completes, `systemctl is-active jarvis` prints `active`, healthz responds. If the service fails to start with the INV-3.1 refusal in `/var/log/jarvis.log`, the manifest is missing/stale — redo the two steps above on the same day.

- [ ] **Export on the PC, ship, import on the VPS:**

```powershell
gradle --no-daemon run --args="trust-export trust-verdicts.json"
# expected: [trust-export] wrote N verdict row(s) -> trust-verdicts.json   (N = PA KC count incl. fixtures)
scp trust-verdicts.json root@46.247.109.91:/tmp/trust-verdicts.json
ssh root@46.247.109.91 "set -a; . /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin trust-import /tmp/trust-verdicts.json"
```

Expected: `[trust-import] inserted=N updated=0 skipped=0 (rows=N)`

- [ ] **Idempotency proof on the real VPS** — run the import again:

```powershell
ssh root@46.247.109.91 "set -a; . /opt/jarvis/.env; set +a; /opt/jarvis/jarvis-kotlin/bin/jarvis-kotlin trust-import /tmp/trust-verdicts.json"
```

Expected: `[trust-import] inserted=0 updated=0 skipped=N (rows=N)`

- [ ] **Assert the verdicts landed (machine check)** — the query avoids string literals entirely so no quoting layer fights another; expect a `faithful` group with count ≥ 1:

```powershell
ssh root@46.247.109.91 'set -a; . /opt/jarvis/.env; set +a; DB="${JARVIS_TUTOR_DB:-/root/.jarvis/tutor.db}"; python3 -c "import sqlite3,sys; print(sqlite3.connect(sys.argv[1]).execute(\"SELECT status, COUNT(*) FROM kc_verification_status GROUP BY status\").fetchall())" "$DB"'
```

Expected shape: `[('faithful', N>=1), ('pending', ...), ('uncertain', ...)]`. (If the PowerShell→ssh quoting still fights you, `ssh root@46.247.109.91` interactively and run the python line directly on the VPS — the assertion is what matters, not the wrapper.)

- [ ] **Serve-side confirmation (render before claiming done):** open `https://corgflix.duckdns.org` in a browser, log in, open a PA lesson/queue surface for a faithful KC and confirm the trust badge shows **"corespunde cursului"** (never "corect" — locked copy, §12). The badge lights only if the VPS's `content/` recompute matches the synced `content_hash` — this click IS the end-to-end D9 proof. If the badge stays "unverified", diff the VPS `content/PA` against the PC's (the deploy ships it; a stale corpus is the usual cause).

No repo commit (runtime ops). Record nothing in memory files — `/wrap` handles session state.

---

## Task 12 — CI: verifier-dep loud-red (INV-9.3) + backup-tooling job; final whole-suite gate

**Files:** `.github/workflows/test.yml` (modified).

- [ ] **Add two jobs** to `.github/workflows/test.yml` (append under `jobs:`, sibling to `backend`/`frontend`/`daemon`). The INV-3.1 refusal-path tests already run in CI inside the existing `backend` job (`gradle :check` runs `MigrationBackupGateTest`); these jobs add the python suite and the INV-9.3 seeded drill (hiding a verifier dep IS the seeded violation — the run must go RED, never silently UNCERTAIN):

```yaml
  backup-tooling:
    name: backup tooling (db-backup.py unittest)
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - name: db-backup floor tests (M-DB fail-closed)
        run: python tools/test_db_backup.py
      - name: db-backup hardening tests (argparse + manifest + restore drill)
        run: python tools/test_db_backup_hardening.py

  verifier-deps-loud-red:
    name: verifier deps loud-red (INV-9.3 seeded drill)
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.10"
      - name: ALL verifier deps hidden -> verifyContent aborts RED before touching any DB
        env:
          JARVIS_TUTOR_DB: /tmp/loudred-all.db
        run: |
          set +e
          gradle --no-daemon verifyContent > loudred-all.log 2>&1
          code=$?
          set -e
          echo "gradle exit=$code"
          tail -40 loudred-all.log
          test "$code" -ne 0
          grep -q "verifyContent ABORTED (FAIL-LOUD, H4)" loudred-all.log
          grep -q "JARVIS_RELAY_URL" loudred-all.log
          grep -q "JARVIS_PYTHON3" loudred-all.log
          test ! -e /tmp/loudred-all.db
      - name: ONE hidden dep (JARVIS_PYTHON3) -> still RED, named in the abort
        env:
          JARVIS_TUTOR_DB: /tmp/loudred-one.db
          JARVIS_RELAY_URL: http://127.0.0.1:1
          JARVIS_RELAY_TOKEN: seeded-drill-dummy
        run: |
          set +e
          gradle --no-daemon verifyContent > loudred-one.log 2>&1
          code=$?
          set -e
          echo "gradle exit=$code"
          tail -40 loudred-one.log
          test "$code" -ne 0
          grep -q "JARVIS_PYTHON3" loudred-one.log
          test ! -e /tmp/loudred-one.db
```

(Why `test ! -e`: `VerifyContentCli.run` exits 2 **before** `TutorDb.connect` — the scratch DB file must therefore never exist. That is the "RED, not silent-UNCERTAIN, and no DB was touched" assertion in one line.)

- [ ] **Validate the yaml locally:**

```powershell
python -c "import yaml; yaml.safe_load(open('.github/workflows/test.yml', encoding='utf-8')); print('yaml OK')"
```

Expected: `yaml OK`

- [ ] **Final whole-build gate** (the review-workflow rule — full suite, run yourself, no piping):

```powershell
gradle --no-daemon :check
python tools/test_db_backup.py
python tools/test_db_backup_hardening.py
gradle --no-daemon trustInvariants
```

Expected: `BUILD SUCCESSFUL`, `OK`, `OK`, and `[trustInvariants] ALL PASS …`.

- [ ] **Commit, then push so CI actually runs the new jobs** (this is production work on main; CI green is part of "shipped" — watch the run):

```powershell
git add .github/workflows/test.yml
git commit -m "ci(trust-net): INV-9.3 verifier-dep loud-red drill + db-backup python suite"
git push origin main
gh run watch
```

Expected: all five jobs green (backend, frontend, daemon, backup-tooling, verifier-deps-loud-red). A red `verifier-deps-loud-red` job here would itself be the INV-9.3 drill failing — fix before claiming the plan done.

---

## Done means

1. `tools/db-backup.py --help` prints usage and backs up nothing; every backup writes a verified manifest + passes the scratch-restore drill (CI: `backup-tooling`).
2. `TutorMigration.migrate` refuses schema mutation over a ≥800-card DB without a same-day verified dump — proven by `MigrationBackupGateTest` in CI **and** by the live refusal run in Task 7a (INV-3.1).
3. Live `~/.jarvis/tutor.db`: `kc_verification_status` has 7 columns, `report_wrong` has 9, `fsrs_cards` = 828 == backup manifest (INV-3.3).
4. `kc_verification_status` holds ≥1 `faithful` row produced by `verifyContent` over the real PA corpus; `gradle trustInvariants` = ALL PASS including content-hash PARITY; a second full re-audit reproduced the verdicts (machine re-check — no human spot-checks anywhere).
5. The VPS holds the same verdict rows via `trust-export`/`trust-import` (idempotent re-import = 0 applied), and the live badge renders "corespunde cursului" on a faithful KC.
6. CI is loud-red when a verifier dependency is hidden (INV-9.3 seeded drill) and on the backup/refusal suites.
