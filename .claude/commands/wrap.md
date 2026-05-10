---
description: Append session handoff entry to BRIDGE.md. Run before context exhaustion or at session end.
---

Append a new dated block to ~/.claude/projects/C--Users-User-jarvis-kotlin/memory/BRIDGE.md with the structure below. Update the index line at the top of the file too. Do NOT delete any prior entries — BRIDGE.md is append-only.

Required fields (gather facts via tool calls before writing — do not hallucinate):

- **identity** — Alex (amoalexandru5@gmail.com). Confirm from memory/user_identity.md.
- **hot work (in priority)** — top 1-3 active streams with file paths.
- **bundle** — current live bundle hash. Verify via:
  `curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1`
  Include the verify-cmd literally so the next session can re-run it.
- **tests** — backend / frontend / daemon / node count totals. Best-effort; note if you didn't actually run them this session.
- **dormant integrations** — list anything user-blocked.
- **blockers** — for Claude (next session) and for user.
- **user-said (verbatim, last 3-5)** — quote the user verbatim. Do not paraphrase.
- **don't relitigate** — locked-in rules from feedback memory files.
- **hallucination triggers** — known wrong facts that have crept into earlier sessions; warn the next session off them.
- **active spec / plan paths** — paths to the in-flight spec and plan docs with commit SHAs.

Format mirror Section C of docs/superpowers/specs/2026-05-10-memory-sanity-check-design.md. Append after the last `---` separator at the bottom of BRIDGE.md, with an updated `## YYYY-MM-DDTHH:MM → next session` heading.

After writing, also prepend a 1-line entry to the `## Index (newest first)` section at the top of BRIDGE.md.
