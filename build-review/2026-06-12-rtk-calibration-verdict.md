# rtk calibration verdict — REJECTED (2026-06-12, SESSION-67)

Decision: Alex ratified **drop rtk**; adopted instead the 2 quality-guaranteed token levers (lean agent prompts + honest per-task model routing). This doc is the evidence record.

## What was tested

rtk v0.42.4 (`rtk-ai/rtk`, real, 61.8k★, Apache-2.0) — installed from checksum-verified release zip, calibrated via a 10-agent workflow: recon (config/hook mechanics) → 3 fidelity agents (raw vs rtk paired runs on the REAL repo) → 6 adversarial verifiers re-running the "safe"/high-compression claims.

## Fidelity matrix (real repo outputs)

| pair | reduction | safe? | detail |
|---|---|---|---|
| git status (154 dirty) | 2.1% | yes (verified: all 155 paths byte-preserved) | savings trivial |
| git log --oneline -30 | 0.8% | yes | 2 subject tails truncated, SHAs intact |
| git show --stat | 0% | yes (byte-identical) | — |
| git diff | 16-25% | **REFUTED** | silently drops `deleted file mode`, `\ No newline`, blob SHAs → breaks patch tooling (we PM-apply patches, Plan 4a) |
| grep (rtk grep) | 1.2% | **NO** | elides path segments (`src/components`→`...`) → follow-up Edit/Read = file-not-found; 80-char line truncation |
| ls | 98.7% | **NO** | non-recursive: hides 324/332 files; `rtk tree` hard-broken on Windows ("Too many parameters", tree.com exclusion-list bug) |
| read (cat) | 0% default | yes | passthrough; `-l aggressive` (90.8%) destroys Edit old_string constructability |
| tsc --noEmit | **-3.8% (longer)** | yes | all 28 errors preserved + adds summary |
| vitest (1 file) | 89.8% | **NO** | failures collapse to counts; stack traces/file:line gone — confirms test carve-out |
| npm run build | **-33.9% (longer)** | yes | bundle hashes preserved; stderr reordered after success line |

Pattern: **where rtk is safe, savings ≈ 0; where savings are big, it deletes load-bearing facts.**

## Structural mismatch (the deeper reason)

ccusage profile June 1-12 (12 days, 3.50B tokens, $3,196 nominal):

| bucket | tokens | share |
|---|---|---|
| cache read | 3.34B | **95.3%** |
| cache create | 136M | 3.9% |
| output | 16.5M | 0.5% |
| fresh input | 11.2M | **0.3%** |

rtk compresses Bash-tool stdout = a slice of the 0.3% bucket. Our burn = cache reads (prefix re-read per turn), driven by turn count × context size; subagent transcripts = 72% of this project's volume (502MB vs 194MB main loop). The 60-90% marketing numbers come from harnesses that `cat` files and dump raw test logs through the shell; our harness uses Read/Grep/Glob tools + structured outputs, which rtk never touches.

Windows warts (secondary): `rtk init -g` writes `rtk hook claude` assuming PATH; `rtk tree` broken; stderr/stdout reordering; stderr warning noise in grep.

## Adopted instead (Alex-ratified 2026-06-12)

1. **Lean agent prompts** — strip verifiable redundancy only; never under-inform.
2. **Honest per-task model routing on workflow agents** — Sonnet/Haiku where the task verifiably doesn't need Opus-class.

Rejected as long-run detriments: static-context compression (delayed nuance loss; /wrap//dream regenerate), thinner review panels (the adversarial pass caught a false "safe" git-diff claim in THIS calibration).

rtk binary removed from `~\tools\rtk` (re-install trivial if ever revisited: checksum-verified release zip).
