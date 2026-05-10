---
description: Deep memory audit — cross-checks every concrete claim against current state via fresh sub-agent. Token cost ~5-10k. Use when /verify-report.md flagged ≥2 [STALE] entries or you smell drift.
---

Dispatch a fresh general-purpose sub-agent with the prompt below. Wait for the report. Present it to me. Do NOT modify memory inline — let me choose the fixes.

```
You are a memory auditor for the jarvis-kotlin project.

Read every file under the project's memory directory. Resolve the directory from environment variable CLAUDE_MEMORY_DIR if set, else default to:
~/.claude/projects/C--Users-User-jarvis-kotlin/memory/

Skip MEMORY.md (it's an index, no frontmatter) and BRIDGE.md (handoff doc).

For each remaining .md file, identify every factual claim that names a specific:
- filepath
- external binary name (gws, gam, gcloud, npm CLIs, etc)
- npm/pip/cargo package name
- HTTP route path
- commit SHA
- bundle hash
- test count
- live URL

For each such claim, verify it against current state. Use:
- `grep -r` against C:/Users/User/jarvis-kotlin
- `git -C C:/Users/User/jarvis-kotlin log --oneline <sha>` for SHA refs
- `curl -sk https://corgflix.duckdns.org/<path>` for live state
- `which <binary>` or `ssh root@46.247.109.91 'which <binary>'` for binaries
- `ls C:/Users/User/jarvis-kotlin/<path>` for files

Build a delta report grouped by memory file. Each entry tagged:
- [OK]            — claim verified
- [STALE: ...]    — claim was true but drifted (e.g. SHA exists but isn't HEAD anymore)
- [HALLUCINATED: ...] — claim was never true (e.g. gws binary)

Include the verify command + its actual output for every entry.

End with a section "Recommended rewrites" listing each memory file that needs updating + the corrected text for each stale/hallucinated claim.

Stay under 1500 words.
```

When the agent returns, summarize the top 3 most actionable fixes for me. Wait for my approval before any rewrites.
