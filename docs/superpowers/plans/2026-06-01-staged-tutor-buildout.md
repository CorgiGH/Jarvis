# Staged Tutor Buildout — Implementation Plan

> ⛔ **SUPERSEDED — folded into `2026-06-02-master-impl-plan-v2.md`; its Stage 0 = DONE (Phase 0 shipped SESSION-52). Do NOT build from this file.**

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the north-star tutor on a trustworthy base — fix the viz that teaches falsehoods + build the verification-net + clean baseline FIRST (Stage 0), then a backend walking-skeleton on one computational KC end-to-end (Stage 1), then breadth (Stage 2+).

**Architecture:** Reordered per council `1780333403`: foundation (verification-net + DB backup + clean git + viz correctness) GATES surface/feature work — it does not trail it. Stage 0 is fully specified below. Stage 1 (backend skeleton) is outlined and earns its own detailed plan once Stage 0 lands and the two must-resolves (real PDF + computational-KC choice) are decided. Stage 2+ is breadth, sketched.

**Tech Stack:** Kotlin + Ktor + Exposed/SQLite backend; React 19 + Tailwind v4 + Vite frontend (`tutor-web/`); vitest + vitest-axe + Playwright (MCP + config) for tests; `d3-hierarchy` + `motion` + `@visx/*` for viz; Claude via the relay (D1); GitHub Actions CI.

**Inputs fused (do NOT re-derive — cite):**
- Roadmap (WHAT): `docs/superpowers/specs/2026-05-31-north-star-roadmap-design.md` (10 rules, keystone §4, slice-1 §6).
- Gap-ledger: `docs/superpowers/findings/2026-05-31-build-health-audit-gap-ledger.md` (B1–B6, Phase 0–5, 4 must-resolves).
- Design foundation (this session): `DESIGN.md` + `.claude/skills/grounded-ui-design/` (loop + `self-see.mjs`) + locked door pick `.superpowers/ui-runs/door-441d42b/PICK.md` (p1 THRESHOLD).
- Viz-quality (Stage 0): `docs/superpowers/research/2026-06-01-viz-ui-excellence-playbook.md` + `docs/superpowers/findings/2026-06-01-viz-score-backlog.md` + `…viz-mount-audit.md` + `.superpowers/ui-runs/viz-coverage-verdict.md`.

**Hard rules (apply to every task):** no time/effort estimates (dependency order only); no paid APIs; render-before-claim (live-URL verified, not just bundle-green); all file:line claims re-verified against HEAD before editing (they were verified at HEAD `441d42b`).

---

## Staging overview

- **Stage 0 — foundation (this doc, full detail).** Clean git baseline → off-box DB backup → verification-net → pedagogical-correctness gate (the detector) → fix the 4 viz correctness bugs (verified by the gate) → optionally wire the 3 ready keepers. Nothing in Stage 1 starts until Stage 0 is green.
- **Stage 1 — backend walking skeleton (outline; gated).** One computational KC end-to-end on the live app, Phase-0 blockers (B1–B6) leading, keystone citation handle first. Blocked on must-resolves #1 (computational KC) + #2 (real PDF). Earns a detailed plan after Stage 0.
- **Stage 2+ — breadth (sketch).** More KCs → subjects → misconception mining → fișă ingest; retraction (#9) is the first breadth task.

**Deferred behind Stage 0 + Stage 1 (council reorder):** the remaining UI surfaces (drill, teaching loop — old recipe step 2e). The picked door (p1 THRESHOLD) is the entry to Stage-1's serve step; it is NOT built as a surface in Stage 0.

---

## File-structure map (Stage 0)

**Create:**
- `.gitignore` additions — ignore ephemera (`.tmp-*/`, `tutor-web/test-results/`, root `door*.png`, `*-db-backup-*.sql.gz`).
- `tools/db-backup.sh` — off-box `sqlite3 .dump` of the live DB + card-count verify (no-paid; sqlite3 CLI).
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` — pinned Gradle 8.10 wrapper (CI + local determinism).
- `tutor-web/src/components/viz/__tests__/trace-match.ts` — shared pedagogical-correctness harness (reference-trace + spatial assertions; playbook §3.4/§3.6).
- `tutor-web/src/components/viz/__tests__/{recursionTree,pageTableWalk,tcpCwnd,numLineDirect}.trace.test.tsx` — one correctness test per fixed viz.
- `tutor-web/e2e/door-real-backend.spec.ts` — one un-stubbed E2E asserting a real route paints (replaces the all-stubbed smoke as the gating net).

**Modify:**
- `tutor-web/src/components/viz/RecursionTree.tsx:318-339` — depth-bucket layout → `d3-hierarchy` `tree()` (Reingold-Tilford).
- `tutor-web/src/components/viz/PageTableWalk.tsx:303-312` — flush stale TLB on COW break.
- `tutor-web/src/components/viz/TcpCwnd.tsx:40-44` — add the FAST_RECOVERY phase.
- `tutor-web/src/components/viz/NumLineDirect.tsx:47,123` — sync label x with marker cx.
- `tutor-web/playwright.config.ts:1-20` — add `use: { reducedMotion: 'reduce' }`.
- `.github/workflows/test.yml` — run `gradle check` (incl. validateContent) + `npm run e2e`.
- `tools/deploy.sh:47` — build the SPA (`cd tutor-web && npm ci && npm run build`) + deepen smoke (SPA-paints assertion).
- `tutor-web/src/components/viz/vizRegistry.ts` + `content/viz-ids.yaml` — (optional 0.F) wire RaceMutex/NPGadget/SlopeCounter.

**Branch off main (NOT committed to main — branching-scope: demos → feature branch):** `tutor-web/src/DoorMockups.tsx` (now holds the real p1/p2/p3 demo components — still a throwaway harness) + its `tutor-web/src/main.tsx` `/door-mockups` route + the root `door*.png` / `p{1,2,3}-*.png` renders. Decision record (`PICK.md`, `ranking.md`, proposals, `RENDER-HANDOFF.md`) IS committed to main.

---

## STAGE 0

### Task 0.A — Clean git baseline

**Files:**
- Modify: `.gitignore`
- Branch: `door-demo-harness` for the demo route + `DoorMockups.tsx` + door PNGs

- [ ] **Step 1: Confirm HEAD + inspect the working tree**

Run: `git rev-parse HEAD && git status --short`
Expected: HEAD = `441d42b...`; ~55 dirty/untracked files.

- [ ] **Step 2: Add ephemera to `.gitignore`**

Append to `.gitignore`:
```
# ephemeral work + demo render artifacts (Stage-0 baseline 2026-06-01)
.tmp-*/
.tmp-*.sql
tutor-web/test-results/
tutor-web/public/audio/
/door*.png
/p[1-9]*-*.png
*-db-backup-*.sql.gz
```

- [ ] **Step 3: Move the door DEMO harness onto a feature branch (do NOT ship a dev-only mockup route on main)**

The `/door-mockups` route + `DoorMockups.tsx` are a throwaway demo (now holding the real p1/p2/p3 components). Per branching-scope they leave main. `main.tsx`'s ONLY change is that route, so revert it on main and carry it on the branch.
```bash
git stash push -- tutor-web/src/main.tsx tutor-web/src/DoorMockups.tsx
git switch -c door-demo-harness
git stash pop
git add tutor-web/src/main.tsx tutor-web/src/DoorMockups.tsx
git commit -m "demo(door): real p1/p2/p3 THRESHOLD/MAP/COLD-OPEN render harness (throwaway, off main)"
git switch main
```
Expected: on `main`, `git status` no longer lists `main.tsx`/`DoorMockups.tsx`.

- [ ] **Step 4: Stage production work + docs (the genuine main changes)**

```bash
git add .gitignore \
        tools/pc-relay-server.py \
        tutor-web/src/components/viz/AlgoStepperShell.tsx \
        tutor-web/src/components/viz/RecursionTree.tsx \
        tutor-web/src/index.css \
        .claude/active-constraints.md \
        .claude/skills/grounded-ui-design/ \
        .claude/council-cache/ \
        docs/superpowers/ \
        DESIGN.md \
        src/test/kotlin/jarvis/tutor/E3RealRelayProofTest.kt \
        .superpowers/ui-runs/door-441d42b/PICK.md \
        .superpowers/ui-runs/door-441d42b/ranking.md \
        .superpowers/ui-runs/door-441d42b/RENDER-HANDOFF.md \
        .superpowers/ui-runs/door-441d42b/proposals/ \
        .superpowers/ui-runs/door-441d42b/pack.json \
        .superpowers/ui-runs/door-441d42b/conflicts.json \
        .superpowers/ui-runs/door-441d42b/verifier-report.json
git rm .claude/commands/sanity.md .claude/commands/wrap.md
```

- [ ] **Step 5: Verify nothing demo/ephemeral is staged**

Run: `git diff --cached --name-only | grep -Ei 'DoorMockups|door[0-9].*\.png|\.tmp-|test-results' || echo CLEAN`
Expected: `CLEAN`.

- [ ] **Step 6: Commit the baseline**

```bash
git commit -m "chore(stage0): clean baseline — design foundation (DESIGN.md), motion tokens, ShellLayout unbox, decision trail

- DESIGN.md brand contract + index.css motion/shadow tokens (playbook §5)
- AlgoStepperShell ShellLayout (fullBleed/controls/canvasBg) + RecursionTree parametric props
- grounded-ui-design rework + self-see.mjs (vision-in-loop)
- door pick recorded (p1 THRESHOLD) + council cache + roadmap/gap-ledger/playbook
- remove deprecated commands sanity.md/wrap.md

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git status
```
Expected: working tree clean except gitignored ephemera.

---

### Task 0.B — Off-box DB backup (HARD precondition of any schema-touching deploy)

Gap-ledger must-resolve #3: a `sqlite3 .dump` off-box is the one place a mistake = unrecoverable loss over the live 871-card DB. Build the backup tool BEFORE Stage 1 ever ALTERs a table.

**Files:**
- Create: `tools/db-backup.sh`

- [ ] **Step 1: Confirm sqlite3 availability + DB path**

Run: `where.exe sqlite3 2>$null; Test-Path "$env:USERPROFILE\.jarvis\tutor.db"`
Expected: a sqlite3 path; `True`. If sqlite3 missing, install (`winget install SQLite.SQLite` / `apt-get install sqlite3` on VPS) — it is free, no paid-API conflict.
DB path confirmed at `Config.kt:38` → `jdbc:sqlite:${home}/.jarvis/tutor.db`.

- [ ] **Step 2: Write `tools/db-backup.sh`**

```bash
#!/usr/bin/env bash
# Off-box backup of the live tutor SQLite DB. HARD precondition of any
# schema-touching deploy (gap-ledger must-resolve #3). No paid services.
set -euo pipefail
DB="${JARVIS_DB:-$HOME/.jarvis/tutor.db}"
OUT_DIR="${1:-./backups}"
mkdir -p "$OUT_DIR"
[ -f "$DB" ] || { echo "FATAL: DB not found at $DB" >&2; exit 1; }
TS="$(sqlite3 "$DB" "SELECT strftime('%Y%m%d-%H%M%S','now');")"
COUNT="$(sqlite3 "$DB" "SELECT COUNT(*) FROM fsrs_cards;")"
OUT="$OUT_DIR/jarvis-tutor-db-$TS.sql.gz"
sqlite3 "$DB" ".dump" | gzip > "$OUT"
echo "backed up $COUNT fsrs_cards → $OUT ($(du -h "$OUT" | cut -f1))"
# integrity: dump must be re-importable
gzip -dc "$OUT" | sqlite3 ":memory:" "SELECT 1;" >/dev/null && echo "integrity: dump re-imports OK"
```

- [ ] **Step 3: Run it; verify the card count matches the live DB**

Run: `bash tools/db-backup.sh ./backups`
Expected: `backed up <N> fsrs_cards → ./backups/jarvis-tutor-db-*.sql.gz` (N should match the documented live count — verify against the VPS DB too) + `integrity: dump re-imports OK`.

- [ ] **Step 4: Document the VPS backup invocation (not run from CI; run before each schema deploy)**

Append to `tools/deploy.sh` header comment (no execution change yet):
```bash
# SCHEMA-DEPLOY PRECONDITION (gap-ledger must-resolve #3):
#   ssh root@46.247.109.91 'bash -s' < tools/db-backup.sh /opt/jarvis/backups
# Run this and confirm the card count BEFORE any deploy that ALTERs a table.
```

- [ ] **Step 5: Commit**

```bash
git add tools/db-backup.sh tools/deploy.sh
git commit -m "feat(stage0): off-box DB backup tool (gap-ledger must-resolve #3, schema-deploy precondition)"
```

---

### Task 0.C — Verification-net (GATING, gap-ledger Phase 5)

The net must catch regressions before they reach the live URL. Built before any feature so Stage 1 lands on green rails.

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}`
- Modify: `tutor-web/playwright.config.ts`, `.github/workflows/test.yml`, `tools/deploy.sh`
- Create: `tutor-web/e2e/door-real-backend.spec.ts`

- [ ] **Step 1: Commit a pinned Gradle wrapper (CI uses an injected 8.10 today; pin it)**

Run: `gradle wrapper --gradle-version 8.10` (uses the CI-pinned version), then:
Run: `git status --short gradle* gradlew*`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` appear.

- [ ] **Step 2: Verify the wrapper builds**

Run: `./gradlew --version`
Expected: `Gradle 8.10`.

- [ ] **Step 3: Add `reducedMotion` to Playwright config**

Modify `tutor-web/playwright.config.ts` — add to the `use` block:
```ts
use: {
  baseURL: 'http://localhost:5173',
  reducedMotion: 'reduce', // a11y + deterministic screenshots (playbook §2.4)
},
```

- [ ] **Step 4: Write one un-stubbed E2E (real backend, not the all-mocked smoke)**

Create `tutor-web/e2e/door-real-backend.spec.ts`:
```ts
import { test, expect } from '@playwright/test';

// Gating net: hit a REAL registered route with NO API stubs and assert it paints
// with zero 4xx/5xx (CLAUDE.md interaction-smoke). Replaces the all-stubbed smoke
// as the slice-acceptance gate. Requires the dev server + backend reachable.
test('a real route paints with zero 4xx/5xx', async ({ page }) => {
  const bad: string[] = [];
  page.on('response', r => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });
  await page.goto('/');                       // real app shell, no route stubs
  await expect(page.locator('body')).toBeVisible();
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx during first paint:\n${bad.join('\n')}`).toEqual([]);
});
```

- [ ] **Step 5: Run the E2E locally to confirm it executes**

Run: `cd tutor-web; npm run e2e -- door-real-backend`
Expected: PASS if the backend is up; a clear network-failure list if not (the test's job).

- [ ] **Step 6: Make CI run the full gate (`gradle check` + Playwright)**

Modify `.github/workflows/test.yml` — backend job: change `:test` to `check` (pulls in `validateContent`, already wired at `build.gradle.kts:170-181`):
```yaml
      - run: ./gradlew --no-daemon check
```
Frontend job — add after `npm test`:
```yaml
      - run: npx playwright install --with-deps chromium
      - run: npm run e2e
```

- [ ] **Step 7: Make `deploy.sh` build the SPA + deepen the smoke**

Modify `tools/deploy.sh` near `:47` (before the backend build), add:
```bash
echo "==> building tutor-web SPA"
( cd tutor-web && npm ci && npm run build )   # ships the real bundle, not a stale one
```
And in the smoke section (`:116`), after the `/healthz` check add:
```bash
echo "==> smoke: SPA index paints"
curl -fsS "https://corgflix.duckdns.org/" | grep -q '<div id="root"' \
  || { echo "SMOKE FAIL: SPA index did not serve"; exit 1; }
```

- [ ] **Step 8: Commit**

```bash
git add gradlew gradlew.bat gradle/wrapper tutor-web/playwright.config.ts \
        tutor-web/e2e/door-real-backend.spec.ts .github/workflows/test.yml tools/deploy.sh
git commit -m "test(stage0): verification-net — pinned gradle wrapper, CI runs check+e2e, real-backend smoke, deploy builds SPA (gap-ledger Phase 5)"
```

---

### Task 0.D — Pedagogical-correctness gate (the detector; playbook §3.6)

Build the detector BEFORE fixing, so each fix in 0.E is verified by a test that fails on the bug and passes on the fix. A viz that teaches a falsehood is the worst bug and has zero detector today. The gate = a tiny reference implementation per algorithm emitting the canonical trace, asserted against the viz's own frame sequence (trace-match) + spatial invariants (MoVer-style, playbook §3.4).

**Files:**
- Create: `tutor-web/src/components/viz/__tests__/trace-match.ts`

- [ ] **Step 1: Write the shared harness**

Create `tutor-web/src/components/viz/__tests__/trace-match.ts`:
```ts
// Pedagogical-correctness harness (playbook §3.4 + §3.6): assert a viz's frames
// match a reference trace AND obey spatial invariants. A viz must export its
// frame builder for this to work (see each fix task).

/** Assert two ordered traces are element-wise deep-equal; pinpoint the first divergence. */
export function assertTraceMatches<T>(actual: T[], reference: T[], label: string): void {
  if (actual.length !== reference.length)
    throw new Error(`${label}: trace length ${actual.length} ≠ reference ${reference.length}`);
  for (let i = 0; i < reference.length; i++) {
    const a = JSON.stringify(actual[i]), r = JSON.stringify(reference[i]);
    if (a !== r) throw new Error(`${label}: frame ${i} diverges\n  actual:    ${a}\n  reference: ${r}`);
  }
}

/** No two laid-out nodes at the same depth overlap horizontally (min gap = 2*r). */
export function assertNoSiblingOverlap(
  nodes: { id: string; x: number; y: number; depth: number }[], r: number, label: string,
): void {
  const byDepth = new Map<number, { id: string; x: number }[]>();
  for (const n of nodes) (byDepth.get(n.depth) ?? byDepth.set(n.depth, []).get(n.depth)!).push(n);
  for (const [depth, row] of byDepth) {
    const sorted = [...row].sort((a, b) => a.x - b.x);
    for (let i = 1; i < sorted.length; i++)
      if (sorted[i].x - sorted[i - 1].x < 2 * r)
        throw new Error(`${label}: nodes ${sorted[i - 1].id},${sorted[i].id} overlap at depth ${depth} (gap ${(sorted[i].x - sorted[i - 1].x).toFixed(1)} < ${2 * r})`);
  }
}
```

- [ ] **Step 2: Verify it imports cleanly (no test yet — used by 0.E)**

Run: `cd tutor-web; npx tsc --noEmit`
Expected: no new type errors from `trace-match.ts`.

- [ ] **Step 3: Commit**

```bash
git add tutor-web/src/components/viz/__tests__/trace-match.ts
git commit -m "test(stage0): pedagogical-correctness harness — trace-match + spatial-invariant assertions (playbook §3.6)"
```

---

### Task 0.E — Fix the 4 viz correctness bugs (TDD, verified by 0.D)

> Order: RecursionTree FIRST — it is the ONLY registry-wired, student-reachable viz (backlog line 47).

#### 0.E.1 — RecursionTree: depth-bucket layout → d3-hierarchy Reingold-Tilford

**Files:**
- Modify: `tutor-web/src/components/viz/RecursionTree.tsx:318-339` (+ export the frame/layout fn for the test)
- Test: `tutor-web/src/components/viz/__tests__/recursionTree.trace.test.tsx`

- [ ] **Step 1: Write the failing spatial test**

Create `tutor-web/src/components/viz/__tests__/recursionTree.trace.test.tsx`:
```tsx
import { describe, it, expect } from 'vitest';
import { layoutTreeNodes, buildRecursionFrames } from '../RecursionTree';
import { assertNoSiblingOverlap } from './trace-match';

describe('RecursionTree layout correctness', () => {
  it('fib(5) full tree has zero sibling overlap', () => {
    const frames = buildRecursionFrames(5);
    const finalTree = frames[frames.length - 1].tree;     // fully expanded
    const laid = layoutTreeNodes(finalTree);              // [{id,x,y,depth}]
    expect(() => assertNoSiblingOverlap(laid, 16 /* node r */, 'fib(5)')).not.toThrow();
  });
  it('every non-root node sits below its parent (x within parent subtree span)', () => {
    const frames = buildRecursionFrames(5);
    const laid = layoutTreeNodes(frames[frames.length - 1].tree);
    const byId = new Map(laid.map(n => [n.id, n]));
    for (const n of frames[frames.length - 1].tree) {
      if (n.parentId == null) continue;
      const child = byId.get(n.id)!, parent = byId.get(n.parentId)!;
      expect(child.y).toBeGreaterThan(parent.y);          // strictly deeper
    }
  });
});
```

- [ ] **Step 2: Run it — expect FAIL (layoutTreeNodes not exported / overlap present)**

Run: `cd tutor-web; npx vitest run src/components/viz/__tests__/recursionTree.trace.test.tsx`
Expected: FAIL — either `layoutTreeNodes is not a function` or an overlap assertion error on the current depth-bucket layout.

- [ ] **Step 3: Replace the layout (RecursionTree.tsx:318-339) with d3-hierarchy**

Add import at top of `RecursionTree.tsx`:
```ts
import { hierarchy, tree as d3tree } from 'd3-hierarchy';
```
Replace the depth-bucket block (`:318-339`) and export the layout fn:
```ts
// Reingold-Tilford via d3-hierarchy (installed, was unused). Replaces the
// depth-bucket DFS layout that ignored subtree structure → sibling overlap.
export function layoutTreeNodes(
  treeNodes: TreeNode[],
): { id: string; x: number; y: number; depth: number }[] {
  if (treeNodes.length === 0) return [];
  const byId = new Map(treeNodes.map(n => [n.id, n]));
  const root = treeNodes.find(n => n.parentId == null);
  if (!root) return [];
  const built = hierarchy(root, n => n.childIds.map(id => byId.get(id)!).filter(Boolean));
  const usableW = TREE_X_END - TREE_X;          // 470 - 160 = 310
  const maxDepth = Math.max(...treeNodes.map(n => n.depth), 1);
  const usableH = maxDepth * TREE_LEVEL_GAP;
  d3tree<TreeNode>().size([usableW, usableH])(built);
  return built.descendants().map(d => ({
    id: d.data.id,
    x: TREE_X + d.x,
    y: TREE_Y_TOP + d.y,
    depth: d.depth,
  }));
}
```
Then, where `positions` was built (the old `:331-339`), call:
```ts
const positions = new Map<string, { x: number; y: number }>(
  layoutTreeNodes(tree).map(n => [n.id, { x: n.x, y: n.y }]),
);
```
Ensure `buildRecursionFrames` (the frame builder, currently internal) is `export`ed so the test can drive it; if it has another name, export under that name and update the test import.

- [ ] **Step 4: Run the test — expect PASS**

Run: `cd tutor-web; npx vitest run src/components/viz/__tests__/recursionTree.trace.test.tsx`
Expected: PASS (no overlap; children strictly below parents).

- [ ] **Step 5: Visually confirm on the live viz (render-before-claim)**

Run the dev server; with Playwright MCP navigate to `/viz-demo#recursion-tree-idx-0`, advance the frame slider to full expansion, screenshot, and confirm by eye: no crossed edges, siblings grouped under parents.
Expected: a clean fib(5) tree.

- [ ] **Step 6: Commit**

```bash
git add tutor-web/src/components/viz/RecursionTree.tsx tutor-web/src/components/viz/__tests__/recursionTree.trace.test.tsx
git commit -m "fix(viz): RecursionTree depth-bucket layout → d3-hierarchy Reingold-Tilford (the only wired viz; subtrees no longer overlap)"
```

#### 0.E.2 — PageTableWalk: flush stale TLB on COW break

**Files:**
- Modify: `tutor-web/src/components/viz/PageTableWalk.tsx:303-312`
- Test: `tutor-web/src/components/viz/__tests__/pageTableWalk.trace.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect } from 'vitest';
import { buildPageTableFrames } from '../PageTableWalk';
describe('PageTableWalk TLB coherence', () => {
  it('no stale TLB entry for vpn=3 after the Phase-4 COW break', () => {
    const frames = buildPageTableFrames();
    const afterCow = frames.filter(f => f.phase >= 4);
    for (const f of afterCow)
      expect(f.tlb.find(e => e.vpn === 3 && e.pfn === 2), `stale vpn3→pfn2 in phase ${f.phase}`).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run — expect FAIL** (`buildPageTableFrames` export + stale entry present).
Run: `cd tutor-web; npx vitest run src/components/viz/__tests__/pageTableWalk.trace.test.tsx` → FAIL.

- [ ] **Step 3: Fix** — in `PageTableWalk.tsx` after the COW remap (`pt[3].pfn = 5` at `:303`, before the `push({phase:4,…})` at `:312`), invalidate the stale entry:
```ts
tlb = tlb.filter((e) => e.vpn !== 3); // TLB shootdown: PTE[3] remapped, old mapping must not survive
```
Export `buildPageTableFrames` if not already.

- [ ] **Step 4: Run — expect PASS.** Same command → PASS.

- [ ] **Step 5: Commit**
```bash
git add tutor-web/src/components/viz/PageTableWalk.tsx tutor-web/src/components/viz/__tests__/pageTableWalk.trace.test.tsx
git commit -m "fix(viz): PageTableWalk flush stale TLB entry on COW break (was teaching that a stale mapping silently vanishes)"
```

#### 0.E.3 — TcpCwnd: add the FAST_RECOVERY phase the title promises

**Files:**
- Modify: `tutor-web/src/components/viz/TcpCwnd.tsx:40-44`
- Test: `tutor-web/src/components/viz/__tests__/tcpCwnd.trace.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect } from 'vitest';
import { buildCwndFrames } from '../TcpCwnd';
describe('TcpCwnd Reno fast-recovery', () => {
  it('enters FAST_RECOVERY on loss before returning to CONG_AVOIDANCE', () => {
    const frames = buildCwndFrames();
    const modes = frames.map(f => f.mode);
    const lossIdx = frames.findIndex(f => /PACKET LOSS/.test(f.event ?? ''));
    expect(lossIdx).toBeGreaterThan(-1);
    expect(modes[lossIdx]).toBe('FAST_RECOVERY');                 // not CONG_AVOIDANCE
    expect(modes.slice(lossIdx).includes('CONG_AVOIDANCE')).toBe(true); // exits later
  });
});
```

- [ ] **Step 2: Run — expect FAIL** (mode is `CONG_AVOIDANCE` at loss).

- [ ] **Step 3: Fix** — in `TcpCwnd.tsx:43` change `mode = "CONG_AVOIDANCE"` → `mode = "FAST_RECOVERY"`, and in the per-RTT loop add the recovery behavior + exit:
```ts
} else if (mode === "FAST_RECOVERY") {
  cwnd += 1;                         // inflate per dup-ACK
  if (rtt >= lossRtt + RECOVERY_WINDOW) { mode = "CONG_AVOIDANCE"; cwnd = ssthresh; }
}
```
Define `RECOVERY_WINDOW` (e.g. 2) and capture `lossRtt` at the loss frame. Export `buildCwndFrames` if needed.

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Commit**
```bash
git add tutor-web/src/components/viz/TcpCwnd.tsx tutor-web/src/components/viz/__tests__/tcpCwnd.trace.test.tsx
git commit -m "fix(viz): TcpCwnd actually run FAST_RECOVERY (title/summary promised a phase the state machine skipped)"
```

#### 0.E.4 — NumLineDirect: sync the μ label with the marker

**Files:**
- Modify: `tutor-web/src/components/viz/NumLineDirect.tsx:47,123`
- Test: `tutor-web/src/components/viz/__tests__/numLineDirect.trace.test.tsx`

- [ ] **Step 1: Write the failing test** (render + drag, assert label x === marker cx)

```tsx
import { describe, it, expect } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { NumLineDirect } from '../NumLineDirect';
describe('NumLineDirect label/marker sync', () => {
  it('μ label x tracks the marker cx after a value change', () => {
    const { container } = render(<NumLineDirect />);
    const marker = container.querySelector('circle[data-testid="mu-marker"]')!;
    const label = container.querySelector('text[data-testid="mu-label"]')!;
    // simulate a mu change via the component's slider/drag handle
    const slider = container.querySelector('input[type="range"]')!;
    fireEvent.change(slider, { target: { value: '7' } });
    expect(label.getAttribute('x')).toBe(marker.getAttribute('cx'));
  });
});
```
(Add `data-testid="mu-marker"`/`"mu-label"` to the circle/text in the fix.)

- [ ] **Step 2: Run — expect FAIL** (label x stale vs marker cx).

- [ ] **Step 3: Fix** — simplest correct path: drop the imperative circle update; render both `cx` and label `x` from React state so they update together. In `NumLineDirect.tsx`, remove the `useEffect`+`setAttribute` at `:47`; make the circle `cx={toSvgX(mu, lo, hi)}` and keep the text `x={toSvgX(mu, lo, hi)}` (`:123`). Add the two `data-testid`s.

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Commit**
```bash
git add tutor-web/src/components/viz/NumLineDirect.tsx tutor-web/src/components/viz/__tests__/numLineDirect.trace.test.tsx
git commit -m "fix(viz): NumLineDirect sync μ label with marker (drop imperative path; both from state)"
```

- [ ] **Step 6 (gate): run the full frontend suite + a11y**

Run: `cd tutor-web; npm test`
Expected: all pass (93 existing + 4 new trace tests + 5 axe).

---

### Task 0.F — (optional) Wire the 3 ready-to-ship ghost viz

Backlog §4-A: RaceMutex, NPGadget, SlopeCounter are correct + taste-4. Wiring is value-add coverage, last in Stage 0 (after correctness). Skip if scope is tight; it is NOT a Stage-1 prerequisite.

**Files:**
- Modify: `tutor-web/src/components/viz/vizRegistry.ts`, `content/viz-ids.yaml`

- [ ] **Step 1: Confirm each renders in the demo gallery** (`/viz-demo`) without error (Playwright MCP screenshot each).
- [ ] **Step 2: Add all three to BOTH files together** (backlog line 80 — registry + yaml in one commit, never one without the other):
```ts
// vizRegistry.ts
"race-mutex": RaceMutex,
"np-gadget": NPGadget,
"slope-counter": SlopeCounter,
```
```yaml
# content/viz-ids.yaml
- race-mutex
- np-gadget
- slope-counter
```
- [ ] **Step 3:** Run `npm test` (the registry/yaml parity self-check must pass) → PASS.
- [ ] **Step 4: Commit** `feat(viz): wire RaceMutex/NPGadget/SlopeCounter (registry + viz-ids together)`.

---

### Stage 0 EXIT GATE (all must hold before Stage 1)

- [ ] `git status` clean on `main`; demo harness on `door-demo-harness`.
- [ ] `tools/db-backup.sh` produces a verified, re-importable dump with the correct card count, off-box.
- [ ] `./gradlew check` green (incl. validateContent); `cd tutor-web && npm test && npm run e2e` green; CI runs both.
- [ ] All 4 viz correctness tests pass; RecursionTree visually confirmed non-overlapping on the live `/viz-demo`.
- [ ] `tools/deploy.sh` builds the SPA + the deepened smoke asserts the index paints.

---

## STAGE 1 — backend walking skeleton (OUTLINE; earns a detailed plan after Stage 0 + the 2 must-resolves)

**Gated decisions (gap-ledger must-resolves — resolve BEFORE Stage-1 detail):**
- [ ] **#1 Computational KC (NEEDS VERIFY/CHOICE).** `pa-kc-001` is definitional = wrong. Verify `pa-kc-005` or `pa-kc-006` has a canonical answer that `GradeScoring.answerMatches` grades sync AND a real source span; else author one. *Decides grading/oracle/UX.*
- [ ] **#2 Real PDF (NEEDS ALEX).** Obtain `Curs 1 PA.pdf`; re-extract raw via pdftotext into `_sources` (current file is a hand-typed paraphrase, 0 form-feeds); fix `SourceOfRecord` page/offset contract.
- [ ] **#3 Schema migration + backfill** over the live 871-card DB — nullable cols vs defaulted-backfill; a NEW dedicated claim→card column (NOT reuse the occupied `sourceRef varchar(32)` — too narrow, FsrsCards.kt:31). Run `tools/db-backup.sh` (0.B) as the precondition.
- [ ] **#4 Truth-oracle independence under D1** — scope slice-1 oracle to compute-verify (#3a) + flag all definitional claims as unverified; defer textbook-KB (#3b) to breadth.

**Phase-0 blockers lead (gap-ledger B1–B6), keystone first:**
1. **B2 keystone (§4) — FIRST.** Add `SourceRef[]` + `model_tag` to `Problem`/`DrillContentDto`; thread the full SourceRef through generation (stop flattening `sources = kc.source.map{it.quote}` at `TutorRoutes.kt:1459`); one `emit()` chokepoint that HARD-BLOCKS un-cited/cheap-model BEFORE `call.respond` (today built after, `:2045→:2051`); `model_tag` from the relay's returned model (kill hardcoded `criticUsed="relay/claude"` `:1520`).
2. **B1 grade→FSRS join.** On confident grade, upsert an `FsrsCard` (RUBRIC_CRITERION) in the SAME txn as the mastery write (`TutorRoutes.kt:2024-2031`).
3. **B3/B4 content indexer.** New producer indexing `content/**/_sources` + KC text into VectorStore; repoint every tutor `HybridRetriever.search` callsite off `Config.archivalDir` onto `content/`; flip the 5 `semanticEmbed=null` callsites (navigation only — type-barred from verification, M2).
4. **B5 auth allowlist.** Reconcile `WebMain.kt:91-106` plural `/api/v1/tasks` to the real singular mutating routes; add the interceptor test.
5. **B6 generation SPOF.** Fallback/retry wrapper on the critic relay; a real-model (or cassette) generation test measuring the false-reject rate (F1).

**The skeleton hops (roadmap §6):** ingest (extraction-gate #1 first) → keystone handle → wire navigation (D2 local multilingual embedder, Phase-1 swap: 1536→384-dim store wipe + dim guard) → generate via relay (pre-gen+persist, accept-gate requires a ContentValidator-confirmed span) → serve with forced-retrieval server gate (#4, no reveal before attempt; unaided flag) → grade deterministic + truth-oracle re-verify + `emit()` chokepoint → mastery + FSRS card (B1 handle) → grade calibration on a PA gold set (#10) → **verify on the live app** (the picked **p1 THRESHOLD door** is the entry; data-testid + zero-4xx/5xx + click-through gates per CLAUDE.md; BEGIN must land on a real registered `/learn/:id/drill` route — p1's named #1 risk is a door to nowhere).

---

## STAGE 2+ — breadth (SKETCH)

More PA KCs over the same pipe → other subjects (ALO/POO/PS/SO shells; PS needs vision/MinerU) → misconception mining (#5) per subject → fișă-disciplinei ingest (coverage #6 + textbook KB #3b) → **retraction (#9) is the FIRST breadth task** (only has work once >1 claim is taught + the keystone handle exists): `ClaimRegistry` + `RetractionSweep`, un-reinforce/quarantine FSRS cards seeded by a retracted claim. Remaining UI surfaces (drill, teaching loop) via the grounded-ui-design loop, grounded in this Stage-1 reality.

---

## Self-review notes (writing-plans checklist)

- **Spec coverage:** Stage 0 covers every gap-ledger Phase-5 net item + the 4 viz bugs + DB backup (#3) + clean baseline. Roadmap §6 hops + B1–B6 mapped in the Stage-1 outline (detail deferred by design, gated on must-resolves). ✓
- **Placeholders:** Stage 0 tasks carry real code/commands. Stage 1/2+ are explicitly OUTLINES (not bite-sized) — by scope-check decision, not by omission. ✓
- **Type consistency:** `layoutTreeNodes`/`buildRecursionFrames`/`buildPageTableFrames`/`buildCwndFrames` are the export names the tests import; each fix task exports them. Confirm the real internal names when editing (grounding noted frame builders are currently internal). ✓
- **Build+mount pairing:** no NEW student-facing components in Stage 0 (viz fixes are in-place edits to already-wired/ghost components; 0.F wires existing ones via registry+yaml together). The door is NOT mounted in Stage 0 (deferred). N/A by design. ✓
- **data-testid:** added `mu-marker`/`mu-label` for the NumLineDirect test; the live-URL data-testid gates live in Stage 1's acceptance (door entry). ✓
- **Verify-before-edit:** every file:line was verified at HEAD `441d42b`; re-verify at execution time (the rule).
