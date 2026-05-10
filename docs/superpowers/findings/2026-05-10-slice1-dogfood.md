# Slice 1 Dogfood Findings — 2026-05-10

> **STATUS: TEMPLATE — awaiting manual dogfood pass by Alex.**
> Fill in each TODO cell as you walk through the scenarios on the live VPS.
> Do NOT edit the section headers or table structure; just replace the TODO values.

---

## Session details

| Field | Value |
|-------|-------|
| Task ID tested | TODO — run `ssh root@46.247.109.91 "sqlite3 /opt/jarvis/data/jarvis.db \"SELECT id, title FROM tasks WHERE problem_ref LIKE '%Tema_A%' LIMIT 1;\""` and paste the result |
| VPS URL | https://corgflix.duckdns.org |
| Bundle hash (pre-deploy) | TODO — run `curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1` |
| Tester | Alex |
| Date | 2026-05-10 |

---

## Scenario results

<!-- Replace each "TODO" in the Result column with: PASS / FAIL / PARTIAL -->
<!-- Add any observations in the Notes column — leave blank if none -->

| # | Scenario | Result | Notes |
|---|----------|--------|-------|
| 1 | PDF prep loads (skeleton → real stepper + drill content; stepper shows ≥ 1 problem) | TODO | |
| 2 | Multi-problem stepper (click A2 → URL updates `?problem=2`; refresh restores state) | TODO | |
| 3 | Drill grader misconception (wrong answer → `L2_ESTIMATOR_CONFUSION` feedback; cards locked) | TODO | |
| 4 | Correct drill → stagger unlock (correct answer → WORKED + DEFINITION + CHECK slide in with 80 ms stagger) | TODO | |
| 5 | Sidekick inline-ask (select word in DEFINITION → ✨ ASK chip → click → sidekick opens with quoted context → real LLM reply renders) | TODO | |
| 6 | FSRS review flip (navigate `/tutor/review` → SHOW ANSWER flips card → AGAIN/HARD/GOOD/EASY each grade and advance) | TODO | |
| 7 | Daemon health pill (header shows green pill; if red: `ssh root@VPS "curl -s http://127.0.0.1:7331/api/v1/health"` to diagnose) | TODO | |

---

## Critical bugs (fix now, before shipping Slice 1)

<!-- Add one bullet per blocking bug found during the dogfood pass. -->
<!-- Format: - [ ] BUG-N: <description> — repro: <steps> — root cause hypothesis: <hypothesis> -->
<!-- If none, replace this section with "None found." -->

- [ ] BUG-1: TODO

---

## Cosmetic / backlog issues (defer to Slice 2)

<!-- Add one bullet per non-blocking issue. -->
<!-- Format: - BACKLOG: <description> -->
<!-- If none, replace this section with "None found." -->

- BACKLOG: TODO

---

## Verdict

<!-- Replace with exactly one of: -->
<!--   SHIP AS-IS                   — all 7 PASS, zero critical bugs -->
<!--   SHIP AFTER CRITICAL FIXES    — critical bugs found; fix loop completed -->
<!--   REVERT AND INVESTIGATE        — cannot safely ship; needs deeper work -->

**TODO**

---

## How to run the dogfood pass

1. **VPS health check**
   ```bash
   curl -sk https://corgflix.duckdns.org/healthz | python3 -m json.tool
   ```
   Expected: `{"ok": true}`

2. **Capture bundle hash** (record in Session details above)
   ```bash
   curl -sk https://corgflix.duckdns.org/tutor/ | grep -oE 'index-[A-Za-z0-9_-]+\.js' | head -1
   ```

3. **Find a real Tema_A task ID** (record in Session details above)
   ```bash
   ssh root@46.247.109.91 "sqlite3 /opt/jarvis/data/jarvis.db \
     \"SELECT id, title, subject FROM tasks WHERE problem_ref LIKE '%Tema_A%' LIMIT 5;\""
   ```
   If no rows return, create one via `POST /api/v1/tasks` referencing `_extras/PS/ps_hw/Tema_A.pdf`.

4. **Open the tutor page** with the real task ID
   ```
   https://corgflix.duckdns.org/tutor/?taskId=<REAL_TASK_ID>
   ```

5. Walk through scenarios 1–7 in order. Fill in the Results table above as you go.

6. After the pass, commit this file:
   ```bash
   git add docs/superpowers/findings/2026-05-10-slice1-dogfood.md
   git commit -m "docs(dogfood): Slice 1 manual dogfood findings — Tema A end-to-end pass"
   ```
