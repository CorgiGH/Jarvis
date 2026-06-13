# grader-golden fixtures

Golden sets for the grader-eval harness (Plan-4a Task 5, spec §9.2 / §0.9E).

Layout: `fixtures/grader-golden/{subject}/{grader-type}/{id}.json`

Shipped now (Plan 4a — deterministic leg):
- `{PA,ALO,PS}/grade-scoring/*.json` — 12 items run by `GraderGoldenHarnessTest`
  against the real `GradeScoring` object.

Shipped (Plan 6 Task 5 — execution leg, INV-6.2):
- `{PA,PS,ALO}/execution-grader/*.json` — known-good / known-bad-mutant PAIRS run by
  `ExecutionGraderSandboxTest` against the REAL `ExecutionGrader` subprocess sandbox.
  Coverage: one good + one mutant per runner R / Python / C++ / Alk (INV-6.2 verbatim),
  derived from the Task-2 real-corpus problems (PA→alk, PS→r, PS-computation→python,
  ALO→cpp). POO has no corpus problem yet → no POO execution fixtures (named pending).
  STRICT on PC + CI (a missing toolchain is RED, not skip); `JARVIS_EXEC_GRADER_OPTIONAL=1`
  downgrades to skip-with-log on the VPS only.

Shipped (Plan 6 Task 6 — LLM-judge recorded-replay leg, R-6-Q5, INV-6.4):
- `{PA,ALO,PS}/llm-judge/*.json` — 6 items (≥2 per subject: 1 clean parse + 1 adversarial
  fenced/preamble-wrapped) run by `GraderGoldenHarnessTest`'s `llmJudgeGoldenCases` method.
  These are RECORDED-REPLAY fixtures: each captures a raw LLM output (recorded once at
  implementation time against the relay / OpenRouter :free), then replays it through the
  REAL `DrillGrader.parseGradeJson` + `GradeScoring` in CI. Zero live LLM calls in CI.
  No-paid-APIs compliant (R-6-Q5 ratified). The golden pins parser+scorer behavior, not
  model behavior — a model output change only affects what is recorded, not the replay.

INV-6.4 (structural satisfaction): both golden legs (grade-scoring + llm-judge) plus
Plan 6 Task 5's execution-grader suite run inside `:check`, which CI runs on every merge.
This means "goldens green per grader per subject before any grader change merges" is
structurally enforced — it cannot be bypassed by a green CI run that skips the golden sets.

Each golden is one JSON object:
`{ "grader": "...", "subject": "...", "id": "...", "input": {...}, "expected": {...} }`

For `grade-scoring`, `input.rubric` is a `Map<String, Boolean>` and `expected` is
`{ "score": Double, "correct": Boolean }`. The harness compares score with epsilon 1e-9.

For `execution-grader`, the shape is (§0.9-E):
`{ "grader": "execution", "subject": "...", "id": "...", "language": "r|python|cpp|alk",
   "input": { "source": "<full program>", "stdin": null, "expected_stdout": "<exact>" },
   "expected": { "pass": Boolean }, "corpus_note": "<provenance — names the source problem>" }`
`ExecutionGraderSandboxTest` runs the program in the sandbox and asserts the trimmed stdout
matches `expected_stdout` exactly (pass) or not (the known-bad mutant). Where the fixture's
`language` differs from the source problem's exam language (Python/C++ here), the `corpus_note`
says so — the runner is under test, not the routing (routing truth is Task 3's).

For `llm-judge`, the shape is (§0.9-E):
`{ "grader": "llm-judge", "subject": "...", "id": "...",
   "input": { "raw_llm_output": "<verbatim recorded model text>" },
   "expected": { "parsed": Boolean, "score": Double, "correct": Boolean,
                 "misconception": String?, "confident": Boolean } }`
`GraderGoldenHarnessTest.llmJudgeGoldenCases` replays `raw_llm_output` through
`DrillGrader.parseGradeJson` then runs `GradeScoring.scoreFromRubric` / `correctFromRubric` /
`isConfident` and asserts each expected field with 1e-9 epsilon on score.
Adversarial fixtures exercise the brace-extract path (preamble + fenced JSON) to ensure
the two-pass parse is covered by the replay suite.
