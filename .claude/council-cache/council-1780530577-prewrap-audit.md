# Council review — 1780530577

**Problem:** Session retrospective + PRE-WRAP SAFETY AUDIT. Did the session go well; is it safe to /wrap-and-stop; what must the wrap capture/fix; and is the recurring council-loop-on-wrap a sign the wrap SKILL should be fixed?
**Proposed approach:** "Session went well; run /wrap now and stop."
**Project context:** Kotlin/Ktor + Exposed/SQLite single-user tutor; memory repo (BRIDGE-HEAD = current state, active-constraints.md = injected every turn) is the handoff; /wrap rewrites them; Alex low-experience, relies on accurate resume; tired of council-looping wraps.
**Timestamp:** 2026-06-04T~05:49Z

---

## 🔴 Devil's Advocate — REJECT
Verified the resume-poison is real AND survives the proposed wrap. active-constraints.md (injected every turn) reads "PLANNING DONE… NEXT = Phase 1: cull + Changes 1-10 + recordIn split + cascades" — but those commits ALREADY landed (82b5809 atop f025ec2/cbff4e6/8122752/b099e93). The /wrap skill (~/.claude/commands/wrap.md) rewrites BRIDGE-HEAD (Step 2) + commits ONLY the memory repo (Step 5); it has ZERO references to active-constraints.md (grep: 0 matches), and that file lives in the PROJECT repo, not <MEM> — so a naive /wrap leaves "build Phase 1 next" injected verbatim into next session, which re-builds done work + re-litigates the locked decisions before BRIDGE-HEAD is even read. Loss hazard: door source + 11 council files + ~12 plan docs committed nowhere; off-repo snapshot covers door/+main.tsx ONLY, not the council/plan docs.
KEY CONCERN: /wrap does NOT rewrite active-constraints.md — the every-turn "Phase 1 is NEXT" line is stale resume-POISON the wrap won't fix; the wrap MUST be paired with a manual edit of active-constraints.md (retire the done Phase-1 recipe, bump main→82b5809, mark Phase 1 done+hardened) + a commit-or-snapshot of the uncommitted council/plan/door files BEFORE stopping.

## 📚 Domain Expert — CONDITIONAL
The SESSION met a real Definition of Done — built (6 TDD batches b3656d2→82b5809), independently reviewed (council 1780525901), hardened on findings (82b5809, council 1780526710), green, and ACTUALLY pushed (verified origin/main...HEAD = 0/0). Sound hygiene; "stop now" is correct, work is at a clean integration point. But "wrap and stop" is safe only if the wrap captures what a mature logbook always captures, and the current BRIDGE-HEAD is the SESSION-52 snapshot (main @ b3656d2) — the whole Phase-1 delta is unrecorded until /wrap runs. Handoff-completeness needs five non-obvious items: the locked council decisions, the deliberate no-phase-regression / "871→828 is the product call not a bug" rationale, the off-box backup state, the flaky/baseline test carve-out, and the ALO Desktop-artifact location. The skill's SCHEMA has slots for all five (Open items / Live external state / Pointers / BRIDGE-LOG) — so APPROVE-with-conditions: run /wrap, verify those five present.
KEY CONCERN: The recurring council-loop IS the signal — root cause = a MISSING CHECKLIST in the wrap SKILL. The skill is a generic procedure (probe→rewrite→append→commit→self-verify) with a generic content check ("every section non-empty"); it has NO domain "definition-of-done capture" checklist forcing the five high-value-easy-to-omit classes. Each session a human/council catches one about to drop — that IS the loop. The leverage fix: bake those five (or a project wrap-probe.md, which the skill supports at Step 1) into the skill as a mandatory pre-commit checklist — do it once and the wrap stops needing a council.

## ⚙️ Pragmatist — CONDITIONAL
"Wrap now and stop" is right on direction but wrong if it means wrap-AS-IS, because the wrap IS the must-do: both resume files are stale (active-constraints + BRIDGE-HEAD say "Phase 1 next / main @ b3656d2"; reality Phase-1-done / @ 82b5809), and active-constraints is injected every turn — a wrap that follows its own Step 2 rewrites BRIDGE-HEAD for free, but the skill never NAMES active-constraints.md (governed only by that file's header convention, easy to skip). Do NOT council-loop the wrap; the skill already has the right procedure-checklist (probe SHA, full-rewrite, retire resolved, self-verify SHA). The one cheap leveraged skill edit (do it in the same wrap): add three explicit Step-2 lines — "rewrite active-constraints.md RESUME RECIPE + LOCKED decisions to match BRIDGE-HEAD," "note off-repo artifacts (Desktop ALO)," "carry flaky-test/baseline counts forward."
KEY CONCERN: Under-capturing is the real risk, not over-processing — if this wrap doesn't actually rewrite the every-turn-injected active-constraints.md (+ verify HEAD SHA), next session resumes on false instructions ("build Phase 1" when shipped) for a low-experience operator who trusts the resume.

## 🧱 First Principles — CONDITIONAL
Raw goal = "a fresh, memory-less session resumes correctly + loses nothing." The proposed wrap is right in intent but the CURRENT resume files actively mislead: both BRIDGE-HEAD + the every-turn-injected active-constraints freeze reality at b3656d2 / "Phase-1-is-NEXT," while reality is 82b5809 with 7 pushed council-hardened commits (verified git ls-remote = 82b5809, work safe). A fresh Alex — who can't reconstruct this — would be told to build Phase 1 from scratch, redoing finished+pushed work + possibly clobbering the migrations. So /wrap must run AND must overwrite the stale Git-state/Hot-work/RESUME-RECIPE lines + fold in the new truths (council-1780526710 decisions incl. no-phase-regression, the new untracked docs, off-box backup, ALO artifacts' Desktop-only location, flaky/baseline carryover).
KEY CONCERN: The resume files are stale by a full phase; a CEREMONIAL wrap that doesn't actually correct Git-state + RESUME-RECIPE hands next session a map to rebuild shipped work. "Run /wrap" only counts if it demonstrably rewrites those stale lines. The recurring "must council the wrap" proves the skill has no encoded definition-of-a-complete-wrap — the durable fix is writing that checklist into the skill ONCE.

## ⚠️ Risk Analyst — CONDITIONAL
"Wrap and stop" is safe ONLY because the proposed /wrap is itself the fix. Dominant risk = resume-poison (CRITICAL if wrap skipped/half-done, neutralized if thorough): active-constraints.md (every turn) + BRIDGE-HEAD both describe b3656d2 / "Phase 1 NEXT, 871 cards, cull-not-run" vs reality 82b5809 / all Phase-1 commits landed+hardened, cull executed (live DB 828, integrity ok). A next session would try to rebuild Phase 1 + re-run migrations/cull on the live DB — BUT recoverability is GOOD: the cull SQL is idempotent (CardCullTest), the backup guard is fail-closed at MIN_EXPECTED_CARDS=800, 3 .sql.gz backups exist → a re-run is a no-op against the current DB, not data loss. Second risk: uncommitted door source (HIGH, fully recoverable) — verified the off-repo jarvis-door-snapshot/ is BYTE-IDENTICAL + current (diff exit 0), so a net exists provided wrap restates the "worktree-only, never clean/checkout" landmine. ALO Desktop artifacts MEDIUM/orphaned (content/ALO/ tracked dirs are empty .gitkeeps; real guides only on Desktop, path-referenced from memory) — degraded-not-lost.
KEY CONCERN: The wrap MUST actually rewrite active-constraints.md + BRIDGE-HEAD to reality (main @ 82b5809, Phase-1 DONE+hardened, 828 cards, cull executed, mark Phase-1 + open items resolved:) — a no-op/partial wrap leaves a stale-Phase-1 instruction injected every turn, the one outcome that re-litigates locked decisions + re-touches the live DB. Stopping is fine; stopping WITHOUT a verified-correct wrap is not.

---

## Sanity Check
SANITY Devil's Advocate: PASS — verified directly (wrap.md grep = 0 active-constraints refs; orchestrator re-confirmed). REJECT is slightly strong (a convention-following wrap CAN edit it) but the core point — the skill doesn't MANDATE it, so the poison can survive — is the most important + correct finding.
SANITY Domain Expert: PASS — strongest; the missing-skill-checklist root-cause + the wrap-probe.md extension path are the leverage answer.
SANITY Pragmatist: PASS — grounded; the 3-line skill edit is concrete + correct.
SANITY First Principles: PASS — verified git ls-remote; minimal-correct-action reasoning sound.
SANITY Risk Analyst: PASS — verified the door snapshot is byte-identical + the backups exist; the re-migration risk downgrade (idempotent cull + fail-closed guard) is correct + reassuring.

---

## Judge
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED (the SESSION went well; the naive "wrap and stop" is unsafe — and the recurring loop has a one-time skill fix)

CORE FINDING:
The session genuinely went well — Phase 1 built → independently reviewed → council-corrected-hardened → green → PUSHED (verified at a clean integration point), plus the ALO track delivered. "Stop now" is the right instinct. BUT a NAIVE wrap is unsafe for one concrete reason all five agents verified: BOTH resume files are stale by a full phase, and active-constraints.md — which is INJECTED EVERY TURN next session — would tell a fresh, low-experience Alex to "build Phase 1" (already shipped) and re-run migrations on the live DB. The /wrap skill rewrites BRIDGE-HEAD but has ZERO steps that touch active-constraints.md (grep-confirmed) — it's maintained only by that file's own header convention, which is exactly the thing that gets skipped. AND the recurring need to council the wrap is itself the diagnosis: the wrap SKILL has no encoded "definition of a complete wrap," so a human/council re-discovers the same capture-gaps every session. That is the loop Alex wants gone.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL — unanimous: session good; wrap MUST actually rewrite active-constraints.md + BRIDGE-HEAD; the durable fix is a one-time skill checklist.

KEY ISSUES:
- active-constraints.md is the every-turn resume-poison the wrap skill does NOT mandate rewriting — must be fixed THIS wrap (retire the done Phase-1 recipe; main → 82b5809; Phase-1 DONE+hardened; mark open items resolved).
- The wrap must also fold in: the council-locked decisions (no-phase-regression rationale, the 5 SESSION-52 decisions still hold), the off-box backup state (local + VPS), the ALO Desktop artifact locations (orphaned outside repo+memory), the known flaky test (stateCacheConcurrent), and restate the door landmine (off-repo snapshot is byte-identical + current).
- Uncommitted-everywhere council/plan docs: low loss-risk but note in the wrap.

RECOMMENDED PATH (this is the leverage answer Alex asked for):
Do NOT council-loop the wrap. Instead, ONE-TIME: (1) fix the wrap SKILL — add a mandatory step that rewrites active-constraints.md (RESUME RECIPE + LOCKED decisions + git-state SHA) to match the new BRIDGE-HEAD and retires resolved items, plus a small capture-checklist (off-box backup, off-repo artifacts, known-flaky tests); optionally encode the project-specific items via the project's wrap-probe.md (the skill already reads it at Step 1). (2) Then run the improved /wrap (which now self-verifies HEAD == 82b5809 + rewrites BOTH files). Result: this wrap is correct AND every future wrap is good first-try with no council.

CONFIDENCE: 9
Unanimous + grounded (the active-constraints gap grep-verified twice; the door snapshot diff-verified byte-identical; git ls-remote = 82b5809). The only judgment left is global-skill-edit vs project-wrap-probe.md for the project-specific items — the active-constraints rewrite belongs in the global skill (it's a generic memory-system invariant); the off-box/ALO/flaky items can go in the project wrap-probe.md.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Output saved to: .claude/council-cache/council-1780530577-prewrap-audit.md
