# Slice-1.5 Audit — Design (2026-05-17)

> Triggered by snake_case rubric chip leak (`uses_rlaplace_or_inverse_cdf_sampler` showing
> verbatim on https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E despite
> Surface Z owning this bug class). Surface Z's default page list
> (`/tutor/`, `/tutor/review`) never reached drilling state where rubric chips render,
> so the lint never fired. This audit closes the meta-bug — "nobody enumerated the states"
> — and the actual reported bug, plus any HIGH/MED found in the sweep.

## Goals

- Fix the reported snake_case leak permanently (single `formatEnum()` utility used
  everywhere a server enum/key reaches user text).
- Audit every reachable workspace state in the Slice-1.5 architecture for the same bug
  class + adjacent bug classes (axe AA violations, console errors, 4xx/5xx responses,
  raw HTTP error envelopes, placeholder text, dead interactive affordances).
- Land an explicit state matrix in this spec so future audits can re-run identically
  and CI can later gate on it.

## Non-goals

- Mobile app audit (Android client is out of scope).
- Backend route audit (last done 2026-05-10; not relevant to the snake_case bug class).
- Performance / load testing.
- New features. This is bug-removal only.

## Three phases

### Phase A — state-space enumeration

The Slice-1.5 workspace state-space is large but most cells are unreachable, trivially
equivalent, or low-value. The full cross-product would be:

| Dimension | Values | Cardinality |
|-----------|--------|-------------|
| Route | `/`, `/?pick=1`, `/?taskId={id}`, `/tasks`, `/review`, `/settings/trust` | 6 |
| Task variant | empty-drills (POO C1, PA Tema 5, SO Linux); code-grading-drill (PS Tema A) | 2 |
| Drill state | unattempted; typed-not-submitted; graded-correct; graded-wrong; gave-up | 5 |
| ResourceRail drawer | closed; PDF; SCRATCHPAD; CONCEPT; PRIOR_GAP | 5 |
| LEDGER drawer | closed; open-all; open-filter-open; open-filter-resolved | 4 |
| ConceptDrawer | closed; open | 2 |
| Header banner | none; deduped-notice; missing-pinned-task | 3 |
| Viewport | mobile 375, desktop 1280 | 2 |

Curated to **~30 representative states**. Each cell in the matrix below is a row in
the audit. Cells listed in `S-XX` order; the audit tool consumes this table verbatim.

#### State matrix

| ID | Route | Reach (Playwright sequence) | Required `[data-testid]` selectors visible | Bug-class lint expectations |
|----|-------|----------------------------|---------------------------------------------|-----------------------------|
| S-01 | `/` (cold) | `goto /` | `active-task-dashboard`, `active-task-row` (≥1), `active-task-detect-btn`, `active-task-manual-btn` | no snake_case in any `active-task-row` |
| S-02 | `/?pick=1` | `goto /?pick=1` | same as S-01 | same |
| S-03 | `/` empty-tasks state | force fetch to return `{tasks:[]}` via cookie clear OR mock | `active-task-empty` | no raw HTTP error in body |
| S-04 | `/` with detect-result | S-01 → click `active-task-detect-btn`, wait for `active-task-detect-result` | `active-task-detect-result` carries "· last run" | no snake_case in result text |
| S-05 | `/` after manual-toggle | S-01 → click `active-task-manual-btn` | `active-task-manual-btn` `aria-expanded="true"`, `task-quickstart-error` absent | no duplicate task lists (P6 [6] progressive-disclosure tracking) |
| S-06 | `/tasks` | `goto /tasks` | `tasks-screen`, `task-create-form`, `tasks-list` (≥0), `task-subject-PS`, `task-subject-PA`, `task-subject-POO`, `task-subject-ALO`, `task-subject-SO` | no snake_case in `task-row` |
| S-07 | `/?taskId=PS-Tema-A` (drill un-attempted) | `goto /?taskId=01KR6K07T6PATPRR5KH1JXYF8E` | `tutor-header`, `problem-stepper`, `progress-strip`, `drill-stack`, `drill-rubric`, `resource-rail` | **NO snake_case in `drill-rubric` li text** (the reported-bug cell) |
| S-08 | S-07 + typed | S-07 → fill `drill-attempt-input` "test" | `drill-stack` still visible, no autoclose | no console error |
| S-09 | S-07 → wrong-answer graded | S-07 → fill input "bomboclat" → click CHECK ANSWER | `grade-feedback`, `rubric-grade`, `misconception-banner` | **NO snake_case in `rubric-grade` text**, **NO SCREAMING_SNAKE in `misconception-banner` text** |
| S-10 | S-07 → correct-answer graded | S-07 → fill input with reference solution → CHECK ANSWER | `grade-feedback`, `rubric-grade` all ✓ | same as S-09 |
| S-11 | S-07 → gave-up | S-07 → click GIVE UP | `grade-feedback` with give-up content | same |
| S-12 | `/?taskId=POO-C1` (empty drills) | `goto /?taskId=01KR6TZ9NCA982XHCFM1VYK761` | `tutor-header`, `problem-stepper`, `progress-strip`. Drill-stack may be empty. | no raw "{}" or "undefined" in UI |
| S-13 | `/?taskId=PA-Tema5` (empty drills) | `goto /?taskId=01KR6RRRCZNAXX10SQCEPPR4FG` | same as S-12 | same |
| S-14 | `/?taskId=SO-Linux` (empty drills) | `goto /?taskId=01KR7K0ASW5ZVK81PQ367WK4FC` | same as S-12 | same |
| S-15 | S-07 + SCRATCHPAD drawer open | S-07 → click `rail-item-SCRATCHPAD` | `rail-drawer`, `scratchpad`, `scratchpad-input`, `scratchpad-counter` | no snake_case anywhere; counter shows "0 / 50000" |
| S-16 | S-15 + scratchpad typed | S-15 → fill `scratchpad-input` "abc" | `scratchpad-status` shows "saving…" then "saved" within 2s | save-error path NOT triggered |
| S-17 | S-15 + scratchpad cleared (clobber-race check) | S-15 → fill empty | hydration ref preserves clear; no re-fetch overwrites | no extra GET /scratchpad |
| S-18 | S-07 + PDF drawer open | S-07 → click `rail-item-PDF` | `rail-drawer`, `pdf-pane` (or error branch with `pdf-upload-button`) | PDF loads OR upload affordance visible |
| S-19 | S-07 + CONCEPT drawer open | S-07 → click `rail-item-CONCEPT` (if rail has one) | `concept-drawer`, `concept-drawer-backdrop`, `concept-drawer-heading` | Escape closes; backdrop click closes; close auto-focused |
| S-20 | S-07 + PRIOR_GAP drawer open | S-07 → click `rail-item-PRIOR_GAP` (if rail has one) | `rail-drawer`, `knowledge-gap-card` (via PriorGapAdapter) | gap loaded OR "loading gap…"/error visible |
| S-21 | S-07 + sidebar LEDGER open | S-07 → click `sidebar-ledger-btn` | `knowledge-ledger`, `knowledge-ledger-backdrop`, `ledger-heading`, `ledger-filter-all` `aria-pressed="true"` | NO snake_case in `ledger-row` text |
| S-22 | S-21 + filter=open | S-21 → click `ledger-filter-open` | `ledger-filter-open` `aria-pressed="true"`; rows filtered | NO `g.type` raw enum visible |
| S-23 | S-21 + filter=resolved | S-21 → click `ledger-filter-resolved` | same shape, filter pressed | NO `g.resolvedBy` enum like "USER_MARKED_DONE" raw |
| S-24 | S-21 + row click → navigate + close | S-21 → click `ledger-row-open` (if any row has taskId) | ledger dismissed, URL changed to `/?taskId=...` | no console error during transition |
| S-25 | S-21 + Escape closes | S-21 → press Escape | ledger dismissed | onClose fired |
| S-26 | S-21 + backdrop closes | S-21 → click `knowledge-ledger-backdrop` | ledger dismissed | onClose fired |
| S-27 | `/review` | `goto /review` | `fsrs-review` (or whatever the page roots on) | no console error |
| S-28 | `/settings/trust` | `goto /settings/trust` | `trust-grants-list` (or empty state), `trust-create-btn` | no snake_case in any grant row |
| S-29 | header at <420px (mobile) | viewport 375 then `goto /?taskId=PS-Tema-A` | header `flex-wrap` reflows; nav visible | all action buttons ≥32px tall |
| S-30 | missing-pinned-task banner | clear cookies + set `?taskId=DOES-NOT-EXIST` | `missing-pinned-task` banner visible | banner text human-readable |

Acceptance: every state above must successfully reach in Playwright AND emit zero
HIGH/MED findings post-fix. States that fail to reach are flagged for spec correction
before the audit re-runs.

### Phase B — audit tooling

#### Orchestrator

`tools/audit-slice15.mjs` — Node script + Playwright.

CLI:
```
node tools/audit-slice15.mjs \
  --base-url=https://corgflix.duckdns.org \
  --spec=docs/superpowers/specs/2026-05-17-slice15-audit-design.md \
  --output=docs/standin-findings/audit-slice15-YYYY-MM-DD.md \
  [--start-from=S-12]   # resume from given state-id
  [--only=S-07,S-09]    # subset
  [--task-id=...]       # override the default PS-Tema-A target
```

Per state row:
1. Launch Chromium (Playwright). Existing cookie + auth helpers from `tools/seed-tutor-events.mjs` reused.
2. Attach listeners:
   - `page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); })`
   - `page.on('response', r => { if (r.status() >= 400) httpErrors.push({url: r.url(), status: r.status()}); })`
   - `page.on('pageerror', e => pageErrors.push(e.message))`
3. Execute the state's `reach:` sequence.
4. Wait for each required `[data-testid]` selector — fail-loud if any missing (HIGH).
5. Capture snapshot:
   - Full-page screenshot → `docs/standin-findings/screenshots/audit-{state-id}.png`
   - DOM textContent (with `<code>`/`<pre>`/`<input value>` stripped, mirroring `LINT_EVAL_SCRIPT`)
   - axe-core report via `@axe-core/playwright`
6. Run deterministic lints on the stripped textContent:
   - `detectSnakeCase` (existing in `tools/surface-z-lints.mjs`)
   - `detectScreamingSnake` (NEW — pattern `/\b[A-Z]{2,}(_[A-Z0-9]+)+\b/g`)
   - `detectDottedModelName` (NEW — pattern `/\b[a-z]+\/[a-z0-9-]+(:free)?\b/g`)
   - `detectRawHttpError` (NEW — pattern `/\bHTTP \d{3}\b/` AND state-specific allowlist for the load-error UI which intentionally surfaces "HTTP 500")
   - `detectPlaceholder` (NEW — pattern `/\b(TODO|TBD|FIXME|XXX)\b/i`)
7. Run LLM judge (existing Z `:free` model path):
   - System prompt: "You are a UX nitpicker. Read the page text + look at the screenshot. List any issues a real user would call out as broken, confusing, or unfinished. Format as `[severity: HIGH|MED|LOW] issue · evidence`."
   - Output appended to findings doc as a subjective category.
8. Run interaction probe: for each `[data-testid]` button on the page, attempt to focus it (assert no JS error). Click-probe ONLY on a minimal allowlist (close/cancel/back buttons known idempotent) to avoid mutating state mid-audit.

#### Severity classifier (mechanical)

| Severity | Rule |
|----------|------|
| HIGH | required selector missing OR pageerror fired OR 4xx/5xx on first-paint OR axe AA violation OR raw HTTP error visible in user text (outside allowlist) |
| MED | snake_case detected OR SCREAMING_SNAKE detected OR dotted model name detected OR placeholder text OR axe AAA OR LLM judge marks "HIGH" |
| LOW | LLM judge marks "MED" or "LOW" OR cosmetic per-pixel issue |

#### Findings doc format

```
# Slice-1.5 Audit Findings — 2026-05-17

## Method
[brief recap; pointer to spec]

## Summary
- HIGH: N  MED: M  LOW: K
- States audited: 30/30 (or list of UNREACHABLE)
- Bundle hash audited: <hash>
- Live URL: https://corgflix.duckdns.org/tutor/

## Findings

| State | Severity | Category | Finding | Evidence | Suggested fix |
|-------|----------|----------|---------|----------|---------------|
| S-07 | MED | snake-case-leak | rubric items rendered raw | "uses_rlaplace_or_inverse_cdf_sampler" in [data-testid=drill-rubric] | apply formatEnum() at DrillStack.tsx:185 |
| ... | ... | ... | ... | ... | ... |
```

#### Tests for the audit tool

`tools/audit-slice15.test.mjs`:
- `detectScreamingSnake` — positive + negative cases
- `detectDottedModelName` — covers `z-ai/glm-4.5-air:free`, doesn't match `path/to/file.js`
- `detectRawHttpError` — positive + allowlist suppression
- `detectPlaceholder` — positive (each marker) + negative (e.g. "todoist" doesn't trigger TODO)
- `severityClassifier` — every rule has at least one test case
- spec-row parser — markdown table → state-row JSON contract

### Phase C — fixes

Sequential commits, each closing one finding or finding-group.

#### C.1 — `formatEnum()` utility

Path: `tutor-web/src/lib/formatEnum.ts`

```ts
export function formatEnum(s: string | null | undefined, opts?: { preserve?: string[] }): string {
  if (s == null || s === "") return s ?? "";
  let out = s.replace(/_/g, " ").toLowerCase();
  if (opts?.preserve) {
    for (const token of opts.preserve) {
      const re = new RegExp(`\\b${token.toLowerCase()}\\b`, "g");
      out = out.replace(re, token);
    }
  }
  return out;
}
```

Test path: `tutor-web/src/__tests__/formatEnum.test.ts` — covers:
- snake_case basic strip
- SCREAMING_SNAKE → lowercase
- mixed-case (e.g. `plots_histogram_AND_theoretical_pdf_overlay`)
- preserve-token path (`PDF`, `CDF`, `R`, `AND`, `OR`)
- `null`/`undefined`/`""` defensive returns
- already-formatted strings unchanged ("already lower case")

Apply sites (initial — Phase B audit may add more):
| Site | Change |
|------|--------|
| `DrillStack.tsx:185` | `<li key={item}>{formatEnum(item, { preserve: ["PDF","CDF","VGAM","R"] })}</li>` |
| `DrillStack.tsx:237` | `{k.replace(/_/g, " ")}` → `formatEnum(k, { preserve: ["PDF","CDF","AND","OR","R"] })` |
| `DrillStack.tsx:248` | `gradeResult.misconception.replace(/_/g, " ")` → `formatEnum(gradeResult.misconception)` |
| `KnowledgeGapCard.tsx:113` | `{resolved ?? "open"}` → `{resolved ? formatEnum(resolved) : "open"}` |
| `KnowledgeLedger.tsx:123` | `{g.type} · ... · {g.resolvedBy ?? "open"}` → `{formatEnum(g.type)} · ... · {g.resolvedBy ? formatEnum(g.resolvedBy) : "open"}` |

#### C.2..C.N — fix-on-find loop

For each HIGH then MED finding in the Phase B doc:
- One commit per fix (or per cohort if the fix is mechanically identical across N sites).
- Commit message body references the finding-id from the audit doc.
- Re-run the offending state in the audit after each commit to confirm the finding clears.

#### LOW backlog policy

All LOW findings appended to `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md`
with `[audit-2026-05-17] [low]` tag and `[deferred]` mark. Visible in future backlog
scans.

## Error handling

- **Audit tool, state-row unreachable**: mark `UNREACHABLE` in findings; do not crash. Log
  the failed step (selector waited, timeout, axe-fail reason).
- **Audit tool, Playwright crash / network drop**: write partial findings doc, exit non-zero.
  Rerun via `--start-from=S-NN`.
- **formatEnum, null/undefined**: return `""` (or pass-through `s ?? ""`). No throw.
- **Live URL audit, bundle changes mid-run**: bundle hash captured at start; re-stamp at
  end and warn if drift.

## Testing

### Audit tool

- `npm run test:tools --prefix tools` includes `audit-slice15.test.mjs` (new).
- Each new detect* function has positive + negative tests.
- Severity classifier has rule coverage tests.

### formatEnum

- `npm --prefix tutor-web run test -- --run formatEnum` (vitest) — covers all 4 rules + defensive paths.

### End-to-end

- Phase C terminal step: re-run `node tools/audit-slice15.mjs --base-url=https://corgflix.duckdns.org` against the live deploy.
- Expected: 0 HIGH, 0 MED, N LOW (catalogued).
- Manual confirmation on the bug that triggered this spec:
  visit https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E,
  verify the "rubric — must satisfy all" list shows human-readable text
  (e.g. "uses rlaplace or inverse CDF sampler" — not the snake_case raw key).

## Acceptance gate

1. Spec state matrix (Phase A) — committed.
2. Audit tool (Phase B) — `audit-slice15.mjs` + `audit-slice15.test.mjs` shipped, tests green.
3. Findings doc emitted at least once against live URL.
4. `formatEnum()` (Phase C.1) shipped + applied at the 5 initial sites + new tests green.
5. All HIGH + MED findings from the live audit closed via commits referencing their finding-id.
6. Re-run audit against live → 0 HIGH, 0 MED.
7. LOW findings catalogued in backlog with audit tag.
8. Manual smoke on PS Tema A — rubric chips human-readable.
9. Deploy to VPS via `bash tools/deploy.sh`; re-audit live URL post-deploy → still 0 HIGH / 0 MED.

## What the user SHOULD see (visual-presence acceptance per skill rule)

After acceptance, on **https://corgflix.duckdns.org/tutor/?taskId=01KR6K07T6PATPRR5KH1JXYF8E**:

- `[data-testid="drill-rubric"]` visible — list items are human-readable text
  (no `_` chars, no `UPPERCASE_SNAKE` chars except domain acronyms in the preserve list).
- `[data-testid="drill-stack"]` visible.
- `[data-testid="problem-stepper"]` visible.
- `[data-testid="progress-strip"]` visible.
- `[data-testid="resource-rail"]` visible.

After typing "bomboclat" + clicking CHECK ANSWER:
- `[data-testid="grade-feedback"]` visible with contextual prose.
- `[data-testid="rubric-grade"]` visible — every `<li>` text is human-readable.
- `[data-testid="misconception-banner"]` visible — text human-readable (e.g.
  "misconception · other" not "MISCONCEPTION · OTHER" raw).

Audit fails if any of the above contains `_` or `[A-Z]{2,}_[A-Z]` patterns.

## References

- Reported bug: `uses_rlaplace_or_inverse_cdf_sampler` on PS Tema A page (user-flagged 2026-05-17).
- Existing Surface Z infrastructure: `tools/surface-z.mjs`, `tools/surface-z-lints.mjs`.
- Prior audit template: `docs/superpowers/specs/2026-05-10-full-site-audit-findings.md`.
- Slice-1.5 architecture: `tutor-web/src/components/TutorWorkspace.tsx`, `ResourceRail.tsx`, `DrillStack.tsx`.
- Live tasks (probed 2026-05-17):
  - POO C1 `01KR6TZ9NCA982XHCFM1VYK761` — empty drills
  - PA Tema 5 `01KR6RRRCZNAXX10SQCEPPR4FG` — empty drills
  - PS Tema A `01KR6K07T6PATPRR5KH1JXYF8E` — code-grading drill, rubricItems populated (the bug-class cell)
  - SO Linux `01KR7K0ASW5ZVK81PQ367WK4FC` — empty drills
