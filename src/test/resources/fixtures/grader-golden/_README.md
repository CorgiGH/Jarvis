# grader-golden fixtures

Golden sets for the grader-eval harness (Plan-4a Task 5, spec §9.2 / §0.9E).

Layout: `fixtures/grader-golden/{subject}/{grader-type}/{id}.json`

Shipped now (Plan 4a — deterministic leg):
- `{PA,ALO,PS}/grade-scoring/*.json` — 12 items run by `GraderGoldenHarnessTest`
  against the real `GradeScoring` object.

Reserved for Plan 6 (NOT populated here — dir convention only):
- `{subject}/llm-judge/*.json` — LLM-judge golden sets (relay-graded leg)
- `{subject}/execution-grader/*.json` — execution/SymPy-grader golden sets

Each golden is one JSON object:
`{ "grader": "...", "subject": "...", "id": "...", "input": {...}, "expected": {...} }`
For `grade-scoring`, `input.rubric` is a `Map<String, Boolean>` and `expected` is
`{ "score": Double, "correct": Boolean }`. The harness compares score with epsilon 1e-9.
