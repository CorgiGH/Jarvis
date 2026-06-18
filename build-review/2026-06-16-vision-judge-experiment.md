# UNIT 2 part-b — vision-judge experiment (bare frames, N=3, 2026-06-16)

Measures whether the figure-overseer's AI vision leg is a REAL pixel-judge or a label-reader.
Result on this case: REAL. Independent (same free claude-max, fresh context per call, a rubric
it did NOT author) → flagged the bad frame 3/3, passed the good frame 3/3, with pixel-grounded
reasoning. This is a DEMONSTRATION on one A/B pair, not a benchmarked precision/recall.

## Setup
- Provider: `claude --print` (free Claude Max subscription, ANTHROPIC_API_KEY unset) — the same path UNIT 2 part-a wired into `ClaudeMaxLlm`.
- Inputs: two BARE, single, un-captioned mergesort DIVIDE frames (callout band cropped out), captured from the BARS skin at 1536×730. They contain the SAME numbers/bars (5 2 8 1 9 3); the ONLY visual difference is the divide grouping.
  - `good_d2.png` (4531 B, fixed renderer): bars split into groups with yellow group-bracket underlines + gaps — `[5] [2 8] [1] [9 3]`.
  - `bad_d2.png` (3923 B, defect renderer — divide branch forced to ignore st.runs): one solid ungapped row, no brackets. Frozen: bad_d1/d2/d3 are byte-identical (md5 6bd3879206333609b1e1faffc7abf0a5).
- The defect was applied to a working-tree copy of SortMergeFamily.tsx, captured, then `git checkout`-reverted (tree clean — verified by PM).
- Rubric (fed as text alongside the image; the judge did NOT author it): "a CORRECT divide frame must be VISIBLY SPLIT into separate groups (gaps / group-bracket underlines); a BROKEN frame shows all bars in one solid ungapped row." Output contract: `VERDICT: PASS|FAIL` + `CHECK_1`/`CHECK_2`; any CHECK FAIL ⇒ VERDICT FAIL.
- N=3 fresh-context calls per frame.

## Raw verdicts (verbatim, run by the PM)
KNOWN-GOOD `good_d2.png`:
- RUN 1: VERDICT: PASS / CHECK_1 PASS / CHECK_2 PASS
- RUN 2: VERDICT: PASS / CHECK_1 PASS — "bars split into 4 groups (5 | 2,8 | 1 | 9,3) by yellow brackets with gaps" / CHECK_2 PASS
- RUN 3: VERDICT: PASS / CHECK_1 PASS / CHECK_2 PASS

KNOWN-BAD `bad_d2.png`:
- RUN 1: VERDICT: FAIL / CHECK_1 FAIL / CHECK_2 FAIL
- RUN 2: VERDICT: FAIL / CHECK_1 FAIL / CHECK_2 FAIL
- RUN 3: VERDICT: FAIL / CHECK_1 FAIL / CHECK_2 FAIL

## Reading
- Perfect discrimination on this pair: good PASS 3/3, bad FAIL 3/3.
- Pixel-grounded, NOT label-reading: the frames carry no PASS/FAIL/answer caption, and the only signal is the bracket/gap structure — which run 2 named exactly (the 4-group partition). A text-only/label path could not separate them (identical numbers).
- Council pass-bar (1781565177) MET: bad ⇒ FAIL ≥2/3 naming pixel-level; good ⇒ PASS 3/3.

## Honest caveats (measure-then-decide, council-binding)
- This is N=3 on ONE bad/good pair = a demonstration, not a labeled precision/recall benchmark. The vision leg stays ADVISORY / FAIL-OPEN / SHADOW until benchmarked on a corpus.
- The DETERMINISTIC floor remains authoritative: the trace-match harness (CI) + the frame-conjunction pixel-gate (UNIT 1, hard-blocking) are the hard blockers. "An AI looked at it" is never the only thing between a wrong figure and the learner.
- Next to graduate the leg from shadow: a labeled bench (many bad/good frames across families) measuring precision/recall, + the rubric generalized per family.

## Artifacts
Frames: `.claude/temp/judge-frames/{good_d1,good_d2,good_d3,bad_d1,bad_d2,bad_d3}.png` (scratch). Capture tool: `tutor-web/tools/__fixtures__/capture-one.mjs` (untracked). Wiring shipped: commit `f5182ba` (UNIT 2 part-a).
