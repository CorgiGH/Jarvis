# Artifact-Type Taxonomy — One-Pass Digestion Pipeline

_English, jarvis-meta. Synthesized from per-subject characterizations of PA, ALO, PS, POO, SO, RC (UAIC FII / IA Year 1). Input = non-lecture course artifacts; lectures are the concept SOURCE, not characterization targets._

DB claims below verified against source 2026-06-11: `tasks` = `src/main/kotlin/jarvis/tutor/Tasks.kt:69`, `task_prep` = `src/main/kotlin/jarvis/tutor/TaskPrep.kt:14`, `exam_dates` = `src/main/kotlin/jarvis/tutor/Phase1Tables.kt:104`, `mock_exams` = `src/main/kotlin/jarvis/tutor/MockExamTable.kt:18`, `fsrs_cards` = `src/main/kotlin/jarvis/tutor/FsrsCards.kt:38`.

---

## 1. Cross-subject artifact-kind table

Eight kinds emerged, not six — the input vocabulary (lecture/lab/seminar/homework/past-exam/grading-doc) needs three additions discovered in the corpora: **reference/cheat-sheet**, **student-notes**, and **grade-ledger** (all currently filed under "other").

| Kind | What it contains (merged) | Grading role (merged) | Surface it must map to | Extraction fields (union) | Outliers / variations |
|---|---|---|---|---|---|
| **lecture** | Slide decks / notes; concept exposition, definitions, command/API examples, open "predict the output" blanks, demo cross-refs to labs. | Never directly graded (PS fisa: curs pondere 0). Source of everything else. | 5-beat lesson nodes (the existing engine core). SO slides have PREDICT prompts pre-baked ("Efectul acestei comenzi: ?"). | section_title, concept_definitions, code/command examples, open_questions_in_slides, demo_cross_references, prerequisite_lecture_number, topic_cluster | SO splits into two disjoint domains: POSIX-practical slides on hand vs OS-theory MCQ domain with **no lecture corpus identified** — domain tagging required. |
| **lab** | Two flavors: (a) worked-example + proposed-exercise sheets (PS, SO, RC: theory blurb → solved example → numbered unsolved exercises, hints/Indicatie, in-lab vs take-home tiers); (b) implementation specs (POO: class interface given, student implements everything; file-organisation + banned-API constraints). | Ranges from holistic participation points (SO: 10p/half-semester discretionary) through continuous-eval feed (PS: part of 100-pt component) to hard graded deliverables (POO: GitHub-submitted, T1/T2 lab exams 30 pts each, ≥20 combined to pass). RC: unknown. | **Code-practice session** (net-new surface): stem + hint scaffold + skeleton-fill or free code + attempt-gated reveal of reference solution; plus **deliverable tracker** for the POO flavor (rubric self-assessment checklist). Decomposable sub-questions can also feed the existing FSRS drill queue. | problem_stem, worked_solution_code, hint_text, solution_sketch, tier (in_lab/homework/self_study), lab_number, topic_cluster, prerequisite_lecture_refs, constraints (banned STL/string.h, required API), grading_rubric (G1..Gn), data_file_dependencies, solution_present, solution_url | Worked examples are NOT solutions to the exercises (RC, PS) — pipeline must never conflate them. RC labs 5+8 missing; RC lab file numbering off-by-one vs PDF titles. PS lab solutions live in separate year-archive .R files. |
| **seminar** | Numbered pen-and-paper problem sets, **no solutions** (PA: proof tasks — reductions, NP membership, invariants; ALO: multi-step numeric linear-algebra computations; PS: probability formula-application, but no standalone sheets for current year — only TA solution .R files). | PA: live participation, 30/100 pts (20 contribution + 10 attendance), no submitted deliverable. ALO: one graded seminar test week 7, 12.5%. PS: folded into the lab component. | Three drill sub-modes, all pen-and-paper (no code): **structured proof drill** (PA: named sub-steps — decision formulation → ND algorithm / NP membership → reduction → both-directions iff — each scored separately); **step-trace numeric drill** (ALO: check each intermediate matrix/scalar state, not just the final answer); **formula-application drill** (PS: PREDICT formula → work numbers → reveal derivation → name theorem → numeric check). | problem_stem, problem_type/method_tag, sub_parts, expected_proof_structure (named sub-steps), hint_present, solution_present (false across the board), prerequisite_lecture_ref, difficulty_tier/rank, is_proof_or_compute, seminar_number | POO, SO, RC have **no seminar kind at all**. PS current-year seminar sheets are a corpus gap (only old solutions exist). Reusable proof scaffolds (PA exchange-argument template) are a cross-artifact dependency on the reference kind. |
| **homework** | Programming-assignment specs: algorithm description (often full pseudocode — itself reusable lesson material), input/output spec, numeric verification criteria (residual norms, error-tolerance ± with confidence), explicit per-sub-problem point weights, mandatory submission format (single .R script / Python pair-file). | ALO: 5 temas = 300 pts = 37.5% of grade, early-submission bonus. PS: 4 parts (A–D) = 17 pts of the continuous component, TA-graded, no re-exam. | **Deliverable tracker** (net-new): deadline + point-at-stake per sub-problem + submission status; paired with **skeleton-fill prep drills** generated from the spec's own pseudocode (practice the archetype before writing the real submission — the app prepares, it does NOT grade the submission). | tema/part number, sub_problem_id, point_weight, problem_stem, algorithm/archetype_tag, parameter_slots, verification_criterion, distribution_names, N_simulation_param, data_file_dependency, deadline (often ABSENT from files — website-only), submission_format, prerequisite_lecture_ref, year | **PA, POO, SO, RC have zero homework.** PS archetypes recur year-to-year with parameter swaps → extract archetype + parameter slots, not just stems. ALO deadlines/point-values are not in the corpus (course-website scrape flagged). |
| **past-exam** | Three structural families: (a) **multi-part written paradigm paper** (PA: 3 questions × a/b/c/d sub-parts with printed point brackets, solutions sometimes embedded in colored text); (b) **MCQ code-trace / theory grids** (POO course exam: Google Form, 1-pt items, "what does this program print?"; SO theory: 18-item forms mixing radio 0.5p + checkbox multi-select 1p); (c) **practical coding test** (SO TP1/TP2, POO lab T1/T2: build-from-spec under time pressure, granular rubric, all-or-nothing sub-items, score-halving penalty rules). | Dominant grade weight everywhere it exists: PA 70/100, POO 30 (course MCQ) + 60 (lab tests), SO T.SO 30/60 with a 12p minimum gate. Embedded solutions/baremuri are the authoritative answer keys. | (a) → **mock-exam mode** (net-new): timed, paper conditions, sub-questions in forced order (each builds on the prior), rubric-based per-sub-part scoring with common-error checklist. (b) → existing FSRS predict-reveal cards fit perfectly (one item = one card; barem explanation = the reveal beat); checkbox items need a **multi-select variant** or true/false decomposition. (c) → **timed rubric self-assessment** (cannot auto-grade; value = format familiarity + rubric awareness). | exam_year, session (regular/retake), question_number, paradigm/topic_tag, sub_question_label + pts + stem + expected_answer, solution_present, answer_options + correct_index, explanation/barem text, difficulty_tag ([UP]/[DE]), rubric rows (criterion, points, AP/WP tag, penalty_condition), time_limit, permitted_materials, alk/code_required, invariant_required, complexity_required | **ALO, PS, RC have zero past papers** (see §2). PA answer-key papers ("cu-rasp") must be split: solutions extracted as answer-key content, not stems. PA unexpanded exam zips (2018→2024) = pending ingestion. POO 2023-24 papers exist only as PNG scans (OCR pass needed). |
| **grading-doc** | Weight-authority records: fisa disciplinei / evaluation pages / course grading docs with explicit component points, minimum thresholds, blocking gates, re-exam policy, curve method, evaluation type (Examen vs Verificare); plus per-test rubrics (baremuri) attached to papers. | IS the grading contract. Not learnable content. | **Grade-model registry + progress dashboard** (net-new): register as authority record feeding component weights, gates, and readiness scores; rubric rows feed the mock-exam scorer and deliverable-tracker checklists. Skip non-pedagogical sections (PA "Learn & Earn" hall of fame). | component_names + max_points + weights, minimum_thresholds, blocking_gates (e.g. "failing continuous eval blocks exam"), reexam_allowed per component, curve_method, evaluation_type (E/V), pass_condition (compound), bonus_rules, exam_week_numbers, ects, topic lists w/ week numbers | RC has **no grading doc at all** — full unknown. ALO's "Foaie de referință"/"Ghid" were filed as grading-doc but are really reference kind (re-filed below). SO splits into three sub-types: per-test barem, course structure doc, instructor grade roster (the roster is grade-ledger kind). |
| **reference / cheat-sheet** (new kind) | Professor- or student-authored study aids: PA Fișa Supraviețuire (canonical examinable-algorithm inventory + exchange-argument proof template), ALO Foaie de referință (method-selection table + formula cards, **permitted in exam first 30 min**) + Ghid rezolvare (9 exercise types × 6 sections incl. "Greșeli frecvente"), RC cheatsheet (predict/reveal Q&A already structured). | Not graded, but defines the de-facto examinable inventory; ALO's is a permitted exam material (surface constraint). | **Structured reference index**: map sections → drill families; method-selection table → PREDICT-phase decision scaffold; proof templates → fill-in-the-blanks frames (not free-form writing); "greșeli frecvente" → targeted wrong-answer distractors + scorer checklist items; cheatsheet Q&A pairs → flashcard seeds. | section/exercise_type_name, algorithm_name, formula_set, dp_state/base/recurrence, complexity, proof_template_type, worked_example_steps + de_ce annotations, common_errors, when_to_use, verification_key, linked_exam_questions, qa_pairs | Authorship varies (student vs professor) — trust-net provenance tagging matters. ALO's exam-permitted status is unique. |
| **student-notes** (new kind) | Dense student-authored lecture reconstructions (PA NP_Complete_Course_Notes.md: definitions, full gadget constructions, worked NP-completeness proofs, P-vs-NP pairs table, exam-strategy error list). | Not graded. Authoritative coverage map for what lectures contain. | Ingestible as lecture-equivalent content for the 5-beat template + high-value recognition drills (P vs NP-complete classification); error lists → rubric checklists. | section_title, concept_name, formal_definition_present, proof_type, reduction_source/target, gadget_names, p_vs_np_pairs, common_errors | Only PA has one in this corpus. Lower provenance trust than professor material — must pass the faithful/verification gate. |
| **grade-ledger** (new kind) | Raw score rosters: PA partial-test ledger (per-sub-question scores per student), SO grade overview (lab-activity distribution). | Reveals real point structure + item difficulty; never user-facing. | **No teaching surface.** Analytics input only: per-sub-question mean/zero-rate → initial FSRS difficulty per drill item; cross-reference column headers to reconstruct missing rubrics. | sub_question_code, max_pts, mean_score, zero_score_count, full_score_count | PA ledger lets you back-calculate a rubric for a paper whose stems are missing. |

---

## 2. Per-subject exam-format summary

### Format KNOWN (evidence-backed)

- **PA** — "Test scris": 2 × 35-pt closed-book 1-hour papers. Invariant structure across 2015/2017/2018: 3 questions = Greedy + DP + NP-completeness, each with 4 ordered sub-parts (a: formalize input/output 2-3p; b: base case / counterexample 2-3p; c: recurrence / greedy-correctness / reduction 3-4p; d: Alk implementation / optimal substructure 2-3p), partial credit at 0.5p. **Alk pseudocode is mandatory** (`choose` for nondeterminism). Retake = single 70-pt full-syllabus paper. Residual unknowns (minor): exact paradigm split between test 1 and test 2; per-year point normalization (27-36 visible vs 35 stated).
- **POO** — Course exam: Google Form MCQ, ~10-15 items × 1 pt = 30 pts, dominant type "Ce se întâmplă la execuția următorului program?" (mental C++ trace → output / compile error / runtime error), barem provides step-by-step execution traces. Lab exams T1/T2: separate in-lab coding sessions, 30 pts each, G1..Gn rubric with AP/WP tags. Format stable through 2023-24. Residual unknowns: per-year item count, stated time limit.
- **SO** — T.SO (week 8, 30 pts, 12-pt gate): (1) practical coding test — multi-part build-from-spec (1 C program + 3 bash scripts in 2024), granular all-or-nothing rubric, **score halved** on structural-constraint violation, 4 simultaneous variants; (2) theoretical MCQ — 18-item Google Form grids, radio 0.5p + "bifați TOATE" checkbox 1p, classical OS-theory topics. Residual unknowns: exact practical/MCQ point split within the 30p; the OS-theory MCQ domain has no matching lecture corpus on hand.
- **ALO (structure known, item bank missing)** — authoritative from alo_c01 grading slides: written exam, 3-4 exercises, 1 hour, first 30 min printed-documentation-only, scored 1-10, ≥3/10 hard gate, 50% of grade, Gaussian curve over passers. The 9 likely exercise types are documented in the Ghid rezolvare + Foaie method-selection table. **But zero actual past papers exist in the corpus** — see gaps below.

### Format UNKNOWN → "ask user to upload" gaps (do NOT guess; do NOT synthesize mock papers and present them as real)

| Subject | What is unknown | Ask Alex to upload |
|---|---|---|
| **ALO** | Actual exam papers; point distribution per exercise; whether proofs appear; per-tema deadlines/points | the `exercitii/` folder (cited as the exam-problem source), `ALO_consultatii_2026`, any past ALO paper, course-website tema table |
| **PS** | No sit-down exam exists (Verificare, not Examen) — but the **Test teoretic (50 pts, single biggest item)** format is entirely unknown: question count, open-book?, duration, structure. Also "Test" (17p practical) format. | any sample/past Test teoretic paper, any lab practical test paper, current-year seminar sheets |
| **RC** | Everything: no past exams, no grading doc, no syllabus, no seminar/homework evidence, lab weights unknown | RC fișa disciplinei / grading announcement, any past RC exam paper, labs 5 and 8 |

Pipeline rule: each UNKNOWN row becomes a first-class **corpus-gap record** surfaced to the user as an upload request; synthetic items generated meanwhile must be visibly tagged `synthetic — no real paper exists` (PS precedent: "without real past papers, any mock exam is synthetic").

---

## 3. New pipeline requirements discovered

Things the lesson-centric (5-beat + FSRS flashcard) design has **no concept for**. Named only; no fix-plans.

1. **R-NEW-SURFACE-PROOF — Structured proof drill**: multi-step written proofs with named, separately-scored sub-steps (PA: formulation → ND algorithm → reduction → both-directions iff). Not a card flip.
2. **R-NEW-SURFACE-STEPTRACE — Step-trace numeric drill**: per-intermediate-state checking of a hand computation (ALO: each multiplier, each row operation, each matrix state), not final-answer-only.
3. **R-NEW-SURFACE-CODE — Code-practice session**: stem + hint scaffold + skeleton-fill or free code + attempt-gated reveal of a reference solution; per-language (R, C/POSIX, bash, C++, Alk). Largest single gap — needed by PS, PA, POO, SO, RC.
4. **R-NEW-SURFACE-MOCK — Mock-exam mode**: timed paper simulation; ordered dependent sub-questions (can't skip to (d)); simultaneous question display; permitted-materials phases (ALO 30-min docs-only); retake = full-syllabus variant; rubric-based per-sub-part scoring.
5. **R-NEW-SURFACE-DELIV — Graded-deliverable tracker**: deadline-tracked submissions distinct from drill items (ALO temas, PS Teme A-D, POO GitHub labs, SO Linux install); per-sub-problem point-at-stake; early-bonus rules; submission-format constraints; app prepares but never grades.
6. **R-RUBRIC — Rubric extraction as first-class data**: G1..Gn rows, AP/WP tags, printed point brackets, all-or-nothing flags, global penalty rules (score-halving) → self-assessment checklists + mock-exam scorer.
7. **R-SOLPRESENT — solution_present flag on every problem item** + policy: worked examples ≠ exercise solutions; embedded answer keys split out as answer-key content; solution-absent sets either get synthesized solutions **visibly tagged synthetic** or are flagged as gaps — never silently invented.
8. **R-GRADEMODEL — Weight-authority registry**: grading-docs registered as authority records, not learnable content; powers a progress dashboard with component readiness + minimum-gate warnings (spec in §4).
9. **R-LANG — Exam-language/surface constraint registry**: Alk (PA), R (PS), C++ with banned-STL/banned-function rules (POO), bash/POSIX C (SO/RC) — hard constraints the code-practice surface must honor per subject.
10. **R-ARCHETYPE — Archetype + parameter-slot extraction**: recurring problem families with parameter swaps (PS homework, ALO seminar) extracted as skeleton + slots so FSRS can re-drill the same pattern with fresh numbers.
11. **R-DATAFILES — Data-file dependency tracking**: CSV/txt files named by problems (unemploy2012.csv etc.) as a first-class field; bundle or flag.
12. **R-PREREQ — Prerequisite cross-ref extraction + gating**: explicit "din cursul N" / "cursul teoretic #N" / lab-demo back-references → knowledge-graph edges that gate drill availability and link lecture↔lab nodes.
13. **R-DIFFCAL — Difficulty calibration from grade ledgers**: per-sub-question mean/zero-rate → initial FSRS difficulty; ledger column headers → rubric reconstruction for missing papers.
14. **R-GAPLEDGER — Corpus-gap ledger**: machine-readable "missing artifact → upload request" records (§2 table + missing RC labs 5/8, unexpanded PA exam zips, PS seminar sheets, ALO exercitii/, course-website-only deadlines) surfaced to the user, not guessed around.
15. **R-MULTISELECT — Multi-select MCQ support**: "bifați TOATE" checkbox items (SO) need a native multi-select drill variant or principled true/false decomposition.
16. **R-ERRORS — Common-error corpora as pedagogy data**: "greșeli frecvente" / Feedback Simulare error lists → wrong-answer distractors + scorer checklist items per topic.
17. **R-DOMAINSPLIT — Per-subject knowledge-domain tagging**: one subject can test disjoint domains (SO: POSIX-practical vs OS-theory, the latter with no lecture corpus on hand) — every drill item tags which exam component + domain it feeds.
18. **R-DEDUP — Duplicate-location reconciliation**: identical artifacts in multiple paths (PA seminars in labs/ and _fii/_gdrive/) and off-by-one file naming (RC) → content-hash dedup + canonical-path resolution before extraction.
19. **R-PROVENANCE — Author-trust tagging**: professor vs student vs tutor-generated artifacts get different trust levels; student-authored material routes through the existing verification/faithful gate before becoming teach-content.

Existing-engine fits worth naming (not new): POO MCQ + barem and SO theory MCQ map 1:1 onto current FSRS predict-reveal cards; SO lecture slides have PREDICT prompts pre-baked; ALO Ghid's 6-section structure almost matches the 5-beat template.

## 4. Grading-model digestion spec sketch

What the system must extract + store per subject to "understand how the user gets graded".

### Per-subject record: `grading_model`

```
subject_id
evaluation_type            E (sit-down exam) | V (verificare/continuous only)   -- PS is V
total_points               e.g. 100 (PA, POO-ish), 1000 (ALO), 60+40 (SO)
pass_condition             compound expression, verbatim + parsed
                           e.g. ALO: exam>=3/10 AND total>=360
                                POO: lab_tests>=20 AND course_exam>=10 AND attendance>=10
                                SO:  T.SO>=12/30 AND linux_install>0 AND T.RC>=50%
                                PS:  continuous_eval pass REQUIRED (blocks final)
curve_method               none | distribution-over-passers (PA) | gaussian-over-passers (ALO)
authority_source           file path + page of the grading-doc (re-verifiable)
gaps[]                     unknown weights/deadlines → corpus-gap ledger entries
```

### Per-component record: `grade_component` (1..n per subject)

```
component_name             e.g. "Test scris 1", "Tema de laborator", "Nota_Lab1", "Seminar attendance"
max_points / weight        explicit points + derived % of final
minimum_threshold          nullable (PA none per-component; ALO exam 3/10; SO T.SO 12/30)
blocks_pass                bool (PS continuous eval blocks exam)
reexam_allowed             bool (PS: all four sub-items "Nu"; SO: T.SO not re-evaluated)
delivery_mode              in-session-written | in-lab-practical | take-home-submission |
                           live-participation | attendance | install-task
schedule                   exact date | week-number (POO T1 wk8, ALO seminar-test wk7) |
                           exam-session | vague/unknown (→ gap ledger; deadline_precision field)
bonus_rules                e.g. ALO early-submission +5/tema
feeds_surface              which prep surface trains it (mock-exam | code-practice | proof-drill |
                           deliverable-tracker | none-for-attendance)
evidence_source            file + locator
```

### Where this lives in the current DB (verified 2026-06-11)

- **`exam_dates`** (`Phase1Tables.kt:104` — id, user_id, subject, start_at): holds the *schedule* leg of exam-session components only. No weight/threshold columns — schedule pointer, not the model.
- **`tasks`** (`Tasks.kt:69` — subject, title, **deadline**, problem_ref_json, **rubric_ref_json**, submission_json, **grade_json**, card_refs_json, status): natural home for deliverable-tracker instances (one row per tema/part/lab-exam-prep). `rubric_ref_json` + `grade_json` already give rubric linkage + point-at-stake a slot; per-sub-problem breakdown fits inside `grade_json`/`rubric_ref_json` payloads.
- **`task_prep`** (`TaskPrep.kt:14` — task_id, problems_json, drills_json, rail_json): home for the generated prep bundle per deliverable (skeleton drills, practice problems) — archetype/parameter-slot output lands here.
- **`mock_exams`** (`MockExamTable.kt:18`): existing seat for mock-exam sessions; needs the §3 mock-mode semantics (ordered sub-parts, rubric scoring, materials phases) layered on.
- **Genuinely homeless today** (named as requirements, no schema proposal here): the `grading_model` / `grade_component` records themselves (weights, gates, curve, pass-condition — no current table owns these); rubric rows as queryable data (only a JSON ref on tasks); the corpus-gap ledger (R-GAPLEDGER); exam-language constraints registry (R-LANG); data-file dependency registry (R-DATAFILES); grade-ledger-derived difficulty calibration inputs (R-DIFFCAL).
- FSRS difficulty seeding from ledgers targets **`fsrs_cards`** (`FsrsCards.kt:38`) initial-difficulty parameters at card-creation time.

Dashboard implication: readiness per `grade_component` (drill performance on the items tagged as feeding it) × component weight, with minimum-gate warnings — the 70/30 (PA), 50/37.5/12.5 (ALO), 50/17/17/16 (PS), 30/30/30/18 (POO), 30/10/10/10+40 (SO) splits all come straight from `grade_component` rows.
