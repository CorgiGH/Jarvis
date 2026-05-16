# Phase A — Real-User Drill Grade Smoke (2026-05-16)

Final end-to-end acceptance for the Phase A grader hardening chain (see
`phase-a-grader-hardening-2026-05-16.md`). Prior acceptance proved the new
code path via Phase C re-seed (`X-Standin-Run: 1` synthetic events). This
doc closes the loop with a real authenticated Alex-typed answer.

## Setup

- HEAD `91a3793` on `origin/main` (post Y-corpus-growth bump).
- Live `/tutor/` at https://corgflix.duckdns.org/tutor/, bundle `index-B-Xy35Ve.js`.
- Backend running the Phase A grader chain (response_format=json_object,
  maxTokens=1200 for code path, balanced-brace fallback parser, GradeAttempt
  carrying raw output).
- User logged in, picked task `01KR6K07T6PATPRR5KH1JXYF8E` (PS Tema A),
  typed `bomboclat` (9 chars, deliberately nonsense canary), clicked
  CHECK ANSWER.

## Event captured

VPS `/opt/jarvis/data/private/tutor_events.2026-05-16.jsonl` last line:

```
{"event_type":"drill_grade",
 "event_id":"eb02d2a128f14983bbf66f3350974678",
 "ts_utc":"2026-05-16T20:38:00.606633778Z",
 "task_id":"01KR6K07T6PATPRR5KH1JXYF8E",
 "session_id":"c7bfe72756cc4158aaad21626b266bdff5321c1088538ac02477c5e436bb70f7",
 "prompt_template_id":"drill-grader-v3",
 "llm_input_redacted":{"rcode_sha256":"a6c63a5c…","preview_head":"bomboclat","preview_tail":"bomboclat","length_chars":9},
 "llm_output_full":"{\"correct\":false,\"score\":0.0,\"rubric\":{\"uses_rlaplace_or_inverse_cdf_sampler\":false,\"n_equals_10000\":false,\"iterates_over_b_in_half_one_two_four\":false,\"plots_histogram_AND_theoretical_pdf_overlay\":false},\"misconception\":\"OTHER\",\"elaboratedFeedback\":\"The student's response doesn't contain any R code related to the problem…\"}",
 "model_resolved":"z-ai/glm-4.5-air:free",
 "latency_ms":24456,
 "llm_output_raw_truncated":null,
 "status":"ok",
 "is_synthetic":false}
```

## Verdict

PASS — Phase A grader chain validated on real authenticated user input.

| Acceptance check | Observed | Expected | Result |
|------------------|----------|----------|--------|
| `status` | `ok` | `ok` OR `parse_error` (both acceptable) | PASS |
| `is_synthetic` | `false` | `false` (real user, not seeder) | PASS |
| `llm_output_raw_truncated` field present | yes (`null`) | present, populated only on `parse_error` | PASS |
| `rubric` populated | 4 snake_case chips all `false` | populated object | PASS |
| `correct` semantically right | `false` for "bomboclat" | `false` (no R code, no Laplace, no plot) | PASS |
| `elaboratedFeedback` contextual | names VGAM/inverse CDF, n=10000, b iter, histogram | references task-specific criteria | PASS |
| `model_resolved` non-null | `z-ai/glm-4.5-air:free` | non-null on ok path | PASS |
| `latency_ms` reasonable | 24456 (24.5s) | <60s | PASS |

## Phase A end-to-end status

- Synthetic-event acceptance (Phase C re-seed): PASS — `phase-a-grader-hardening-2026-05-16.md`.
- Calibration verification (Trace 9+10 promotion): PASS — `calibration-verify-2026-05-16.md`.
- Real-user smoke (this doc): PASS.

Closes hot-work item #1 from the 2026-05-16T19:12 BRIDGE wrap.

## Notes

- `tokens_in`/`tokens_out` null on this event. Carry from prior session, not
  introduced by Phase A. Out of scope.
- `latency_ms: 24456` is on the slower side for a 9-char input; the glm-4.5-air
  free-band model's elaborated_feedback path is the likely time sink. Not a
  Phase A regression.
- No parse_error path exercised by this smoke (input was nonsense but the
  grader handled it cleanly). The `llm_output_raw_truncated` forensics field
  remains validated by unit tests (DrillGraderTest A.3) and by the Phase C
  re-seed's synthetic events — the live path is wired but the live grader
  hasn't faulted today. That is itself good news.
