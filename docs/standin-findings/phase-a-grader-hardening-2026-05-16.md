# Phase A — Grader Hardening Acceptance (2026-05-16)

Council 1778881174 verdict-driven hardening of `DrillGrader` + `TutorEventLog` + Surface X.
Plan: `docs/superpowers/plans/2026-05-16-grader-tripwire-reseed.md`.

## Commit chain (origin/main)

| Task | SHA | Subject |
|------|-----|---------|
| (plan + transcripts) | `059458c` | docs(plan+council): grader hardening + Y tripwire refinement + X-fixture re-seed |
| A.1 | `355f8d6` | feat(envelope): add llm_output_raw_truncated field for grader parse_error forensics |
| A.2 | `615cf4c` | refactor(grader): DrillGrader.grade returns GradeAttempt carrying raw output for forensics |
| A.3 | `da802a1` | feat(grader): capture raw LLM output (truncated 1500) in tutor event envelope on parse_error |
| A.4 | `220ff38` | feat(surface-x): filter parse_error/error events from calibration corpus (status_in=['ok'] default) |
| A.5 | `fc490c9` | feat(grader): request response_format=json_object via OpenRouter to coax JSON-only output |
| A.6 | `fd7a58b` | fix(grader): bump maxTokens to 1200 for code-grading path to prevent JSON truncation |
| A.7 | `50bd785` | feat(grader): parseGradeJson balanced-brace fallback for preamble + trailing chatter |

All 8 commits pushed to `origin/main` (`c05de23..50bd785`).

## Test counts

### Backend (`gradle :test --rerun-tasks`)

- **Baseline (pre-Phase-A):** 702 PASS (per plan)
- **Final (this run):** **714 PASS / 0 fail / 0 error / 0 skipped**
- Delta: +12 tests (covers A.2/A.3/A.5/A.6/A.7 new tests)
- Build time: 2m 31s clean compile + full test run
- Aggregated from `build/test-results/test/*.xml`:
  `tests=714 failures=0 errors=0 skipped=0`

### Tools (`npm run test:tools --prefix tools`)

- **Baseline:** 113 PASS
- **Final:** **114 PASS / 0 fail / 0 skipped**
- Delta: +1 test (A.4's new `event-log-reader status_in` test)
- Duration: 2.48s

### Pre-existing test concern (NOT introduced by Phase A)

`IntegrationHarnessTest.stateCacheConcurrentPersistNeverTearsJson` — A.6 implementer flagged
this test as reproducing on pristine main during their session. In **this** integration run
the test PASSED (0.033s). Concurrent-write tests can be schedule-sensitive; we treat this
as a flake under load, not a regression. No Phase A code touches the state cache or its
concurrent-persist paths. Out of scope for Phase A.

## Live deploy verification

### Deploy run

`bash tools/deploy.sh` ran cleanly:
- `gradle :test :installDist :android:assembleDebug` → BUILD SUCCESSFUL in 7s (UP-TO-DATE from prior run)
- VPS pre-checks: `/opt` 34G free, RAM 2.4Gi avail
- Service stop/scp/start cycle clean
- Health check: `curl https://corgflix.duckdns.org/healthz` → `ok`
- `systemctl is-active jarvis` → `active`

One pre-existing log line at startup (NOT Phase A related): `AccessDeniedException:
/opt/jarvis/data/archival/study-guide/docs/superpowers/specs` — the archival watcher
trips on a permission edge for a specs directory. Pre-existing; touches no grader code.

### Live JAR symbol verification

Deployed JAR `/opt/jarvis/jarvis-kotlin/lib/jarvis-kotlin-0.1.0.jar`:
- Timestamp: `2026-05-16 13:28:39 UTC` (post-deploy)
- Size: 4408069 bytes (was 4397342 pre-deploy)

New Phase A symbols confirmed in string pool of deployed JAR:

```
jarvis/tutor/DrillGrader.class:
  json_object                         ← A.5 (response_format)
  jarvis/tutor/GradeAttempt           ← A.2 (return-type refactor)
  Ljarvis/tutor/GradeAttempt;
  Continuation<-Ljarvis/tutor/GradeAttempt;>;Ljava/lang/Object;
                                       ← A.2 (suspend signature)

jarvis/tutor/TutorEvent.class:
  status
  model_resolved
  llm_output_raw_truncated            ← A.1 (new envelope field)
```

### Frontend bundle (should be UNCHANGED — Phase A is backend-only)

- Pre-Phase-A: `index-B-Xy35Ve.js`
- Post-deploy:  `index-B-Xy35Ve.js` ✓ unchanged

### Envelope sample

No drill_grade events on 2026-05-16 yet (`tutor_events.2026-05-16.jsonl` does not exist).
Most-recent envelope is 2026-05-15 (pre-Phase-A):

```json
{
  "event_type": "drill_grade",
  "status": "parse_error",
  "task_id": "01KR6K07T6PATPRR5KH1JXYF8E",
  "model_resolved": null,
  "llm_output_raw_truncated": null
}
```

The presence of `llm_output_raw_truncated: null` in this 2026-05-15 envelope confirms the
schema migration (A.1's nullable field) is backward-compatible — old events deserialize
cleanly. Post-deploy, the next drill_grade parse_error event will populate the field with
the truncated raw LLM output (per A.3's plumbing).

**Live-fire NOT triggered** by this subagent: drill grading requires Alex to be logged
in + active on `/tutor/`. Alex (the user) will trigger one real drill grade post-merge
to validate end-to-end. The schema migration + class deploy verified above mean the
backend path is wired correctly.

## Council 1778881174 verdict compliance

### Items addressed (shipped)

- **Layer 3 — Raw output capture (load-bearing)** ✓
  - A.1 (envelope field) + A.2 (GradeAttempt return type) + A.3 (plumb to envelope)
  - All five clean agents flagged this as highest priority; shipped first.
- **Substrate-level: `response_format: {type: "json_object"}`** ✓
  - A.5. Cheap "free win" per Domain Expert. Single flag, no risk.
- **Substrate-level: `maxTokens` bump for code-grading** ✓
  - A.6. Bumped from 600 → 1200. Risk Analyst flagged 600 as likely truncation cause for
    long `elaborated_feedback` rubrics.
- **Layer 1 — Aggressive regex extract in parseGradeJson** ✓
  - A.7. Balanced-brace fallback over first top-level `{...}` block. Safe per Risk Analyst's
    type-check analysis (parseGradeJson requires typed fields → garbage blocks fall through
    to null).
- **Corpus-side: Surface X `status_in=['ok']` filter** ✓
  - A.4. Pragmatist's observation: producer-side hardening doesn't compensate for the
    consumer ingesting poisoned events. Filter at the consumer.

### Items deferred (per verdict)

- **Layer 2 — Blind N=2 retry on parse_error** — **DROPPED.**
  - Unanimously flagged broken by council: latency-pessimal (~13s mobile tax on every
    parse_error), retries same prompt on same flaky `:free` endpoint, AND destroys the
    raw-output signal Layer 3 exists to preserve (when retry succeeds, no parse_error
    event, no captured raw output, no calibration data).
  - **Decision per verdict:** wait for observability data from Layer 3 + Layer 1 +
    substrate fixes. If parse_error rate stays non-zero AND raw-output capture shows a
    repeatable failure mode that retry would actually help, revisit as a **scoped**
    retry (e.g. background calibration replays only, never live drills) or as a
    self-correcting retry (feed parse error back to model — Instructor/LangChain
    OutputFixingParser pattern). Plan: Phase B.

## Self-review

- All commits are on `origin/main` and durable.
- Backend test count delta matches plan (711+ expected, 714 actual).
- Tools test count delta matches plan exactly (114 expected, 114 actual).
- Bundle hash unchanged confirms backend-only scope.
- Live JAR contains all new symbols confirms backend code is actually deployed.
- Schema migration is backward-compatible (old `null`-valued field deserializes cleanly).
- Pre-existing concerns (`stateCacheConcurrentPersist`, archival AccessDenied) noted but
  out of scope.
- No live drill-grade fired by this autonomous subagent — Alex to validate end-to-end
  post-merge by running one drill on `/tutor/`.

## Phase A status: SHIPPED

Backend + tools all green. VPS deployed + healthy. Council verdict items 1/3/4/5 all
shipped per agent consensus. Item 2 (blind retry) deferred as the verdict explicitly
recommended.

Next phase (B): once Alex has triggered one live drill grade, inspect the new
`llm_output_raw_truncated` field on any parse_error event to confirm the observability
loop closes end-to-end. If parse_error rate is non-zero and raw output shows a
fixable pattern, iterate.
