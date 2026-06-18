# Jarvis Tutor — Deep State-of-Project Audit (2026-06-10)

_10-agent workflow (8 dimension auditors + completeness critic + Opus synthesis) over real code + live renders. HEAD `9825005` on `main`. #1 finding independently verified against the live DB (read-only copy)._

## Headline numbers
- **Overall maturity vs the ideal ("best learning app that teaches even when stuck"): ~34%**
- **Master plan actually built + working: ~55%** — but the built 55% does not connect into one working learning loop.
- One-liner: **excellent engine, nearly empty car.** Backend strong + tested (224 KT tests); frontend has all the rooms but almost none of the hallways; the content tank is ~1–8% full.

## Maturity by dimension
| Dimension | % to ideal | One line |
|---|---|---|
| Plan progress / completeness | 58 | Phases 1–3 backend solid; ~12/30 remaining tasks shipped; end-to-end path broken at several seams. |
| Teaching (core pedagogy) | 38 | Loop architecture right (productive-failure, FSRS, prereq DAG); content ~1% authored, key mechanisms never fire. |
| Help-when-stuck | 38 | Ladder/ribbon/grounded-card exist; HintControlRow never built; grounded card never fires while still wrong. |
| Content readiness & trust | 22 | Rigorous trust-net, **zero fuel**: 0 faithful KCs, DB schema 3 cols behind code, 4/5 subjects empty. |
| Visuals / viz | 22 | 25 components, **only 1 wired**; viz never in lesson cards; 24/25 unreachable. |
| UX / flow & cold-start | 28 | All rooms, no hallways: legacy task-manager home, Begin no-op, no start affordance. |
| UI / design system | 27 | Real token foundation + door exemplar; every other surface = kicker + one line + blank; black-on-black consent. |
| Functional integrity / risk | 42 | Backend auth solid; frontend blockers (invisible consent, dead exam stub) + no error boundary + no DB backup. |

## THE biggest blocker (verified)
**The trust-net was built but never turned on, so 0 KCs are faithful — and the queue serves faithful-only, so every user gets an empty queue + "Ai terminat tot pentru azi."**
Verified on the live DB (read-only copy, original untouched): `kc_verification_status` = `[kc_id, status, last_audit_run_id, updated_at]` — missing `content_hash`/`source_span_hash`/`lecture_grounded`; **0 rows, 0 faithful**; 828 fsrs_cards intact.
The trust-net is the architectural centerpiece justifying all other complexity, yet it has never produced a single production faithful row. Until ≥1 KC is FAITHFUL end-to-end (migrate DB → run verify → build PC→VPS sync), no learner can complete a single loop. Everything else is downstream.

## Top priorities (severity-ranked, deduped)
1. **[blocker] Turn on the trust-net** — back up tutor.db → boot to migrate the 3 missing columns → reconcile + verifyContent to stamp ≥1 FAITHFUL PA KC → build D9 (PC→VPS verdict sync, co-blocker: verify on PC alone is a no-op for the live site).
2. **[blocker] Wire the dead nav seams** — OggiScreen Begin/CTRL+ENTER → `/lesson/{kcId}`; redirect `/` to OggiScreen (demote ActiveTaskDashboard to /tasks); ExamRoute fetch real questions; SubjectCard onClick. Verify each by clicking on the live URL.
3. **[blocker] Author the content corpus** — 4/5 subjects are `.gitkeep`; PA has 6 Lecture-1 KCs, 1 with explanation_ro, 0 stem_templates. curate-tutor → spans, misconceptions, explanation_ro, worked_example_ro, prediction_options, viz_id, RO stem_template; verify each FAITHFUL.
4. **[blocker] Fix black-on-black consent + GDPR** — body text #000 on #000; AiLiteracyGate swallows the confirm-POST error and proceeds anyway → consent row may never be written.
5. **[high] Romanian-output gate on the LLM layer** — DrillGenerator + DrillGrader prompts have ZERO Romanian instruction + lead with name_en → generated drills AND every wrong-answer feedback reach a RO learner in English. Add RO instruction + CI heuristic (the G1 systemic guardrail).
6. **[high] Build HintControlRow + fix stuck-help discovery** — HintControlRow does not exist; the only way to reach help is accidentally clicking "give up"; grounded explanation never fires on the incorrect path; render self_explanation_prompt after a wrong grade.
7. **[high] Wire viz into student routes + teaching cards** — resolve G4 first, then register the 6 highest-load viz + mount RoutedViz in lesson WORKED/DEFINITION cards.
8. **[high] React error boundary + degraded/empty-state stack** — zero ErrorBoundary in the tree (one throw = blank screen); FatalErrorPage/DegradedBanner/InlineErrorCard/EmptyState built but never mounted; distinguish empty-queue states.
9. **[high] Collapse nav to spec's 5 items** — hide on auth routes, unify to RO, define the undefined `bg-panel-bg` token (Tailwind v4 silently emits nothing).
10. **[medium] Routine DB backup + atomic migration** — 828 cards, only an emergency dump to a relative path; SQLite ALTER non-transactional → silent total-loss risk.
11. **[medium] Placement tests subject knowledge, not tutor internals** — current 8 Qs probe EWMA/FSRS (internal mechanics), misclassify a CS student, never write mastery priors; add "De ce contează?" primer.

## Roadmap to the ideal (ordered)
- **A — Turn on the engine** (make ONE loop real): backup → migrate → verify first FAITHFUL KC → build D9. _Acceptance: a new user's queue returns ≥1 KC on the live URL._
- **B — Connect the hallways** (make the loop clickable): redirect `/`→oggi; Begin→lesson; ExamRoute real questions; SubjectCard onClick; error boundary; mount empty/error primitives. _Acceptance: Playwright drives login→oggi→lesson→drill→grade, zero 4xx/5xx, no dead clicks._
- **C — Fix the front door**: consent contrast + GDPR write; auto-trigger onboarding/placement; 5-item RO nav hidden on auth routes; define tokens. _Acceptance: a cold beginner reaches the first lesson, no invisible text, no dead nav._
- **D — Romanian gate everywhere LLMs generate** + CI heuristic that fails English strings.
- **E — Fill the tank**: author KC corpora for all 5 subjects, each verified FAITHFUL. _Acceptance: every finals subject has ≥1 fully-faithful lesson from /materie._
- **F — Make help-when-stuck genuinely teach**: HintControlRow; grounded explanation on the wrong path; pre-attempt misconception (Posner); self-explanation prompt; Renkl backward-fading; calibration→hint policy.
- **G — Make visuals teach**: resolve G4 (Track A vs B); wire 6 viz into lesson cards; predict-gates; source-correctness review.
- **H — Harden for trust + durability**: ESLint + token/a11y rules; keyboard-only drill path; mobile pass; screenshot baselines in CI; nightly backups; CI guard for the NLI faithful path.

## Cross-cutting risks the critic surfaced (not in the 8 dimensions)
- **The faithful gate is untestable in CI** — needs JARVIS_PYTHON3 + DeBERTa-v3 + relay; CI has none; D9 unbuilt; 0 rows. The centerpiece has never produced a production faithful row and silently fails-open/UNCERTAIN with no alarm.
- **Generated content is English** across drills, grader feedback, misconception codes, lesson done-state — no RO gate anywhere. Directly violates the hard language-split rule.
- **No error boundary + no empty-corpus guard** = blank screens / "done for today" to a never-started user / dead subject clicks combine into a silent-fail front door.
- **Single-user SQLite + no routine backup + non-atomic migration + irreplaceable 828-card DB** = silent total-loss triangle.
- **Missed dimensions:** keyboard-only path through the drill loop (ADHD-relevant), mobile/responsive (phones realistic for RO students), a11y tests may silently no-op (8 TS errors on `toHaveNoViolations`).

## Honest bottom line
You built an excellent engine and a nearly empty car. The backend (Phases 1–3) is genuinely strong, tested, council-hardened — **do not rebuild it.** But measured against your bar, the app today **teaches no one**: 0 faithful KCs → empty queue for every real user; 4/5 subjects empty; the frontend's core seams unwired. ~55% of the plan is built but doesn't connect into one loop. The remaining work is **concentrated and tractable — integration wiring, content, and turning on the trust-net, not new architecture.** The foundation is sound; the gap is finishing. Be disciplined that "shipped" = a real user reaches it on a live URL — the recurring failure here is built-but-unmounted components passing tests while the user sees nothing.
