I have the six gatherer reports. Synthesizing now into the council brief.

# CONTEXT BRIEF: Is jarvis-kotlin's teaching-generation pipeline architecturally correct to auto-produce genuinely good lessons for ANY topic?

**For:** Strategic council, 2026-06-15. **This brief is your entire context.** It synthesizes 6 gatherer reports against the repo, spec, plans, councils, and read-only DB. Cited paths are absolute.

---

## 0. TWO BUILDS — DO NOT CONFLATE

There are two distinct artifacts. Conflating them is the single easiest way to misjudge this question. The distinction is carried through every section below as **[LEGACY-live]** / **[CURRENT-only]** / **[both]** / **[neither]**.

- **(a) LEGACY / LIVE** — the bundle DEPLOYED on the VPS that the learner (Alex) actually opens **today**. It is the **SESSION-66 era** bundle (`9c09364`, bundle mtime 2026-06-05). **None of this session's 140-commit window is live.** Zero viz instances are deployed here (Plan 4b never shipped to it). A learner today sees: 4 PA KCs as 5-beat lessons (no figures, because the viz-instance route has 0 rows in the live DB), the queue, FSRS tracking, code-practice grading (0 real problems seeded), one manually-seeded POO exam.
- **(b) CURRENT / IN-PROGRESS** — `main` @ `9c48d1d`, **140 commits ahead of LIVE**, plus uncommitted work. Includes Plans V + 4b + 6 complete and CI-green, 7 hand-authored viz instances wired, the EC1–EC5 spec additions. **This is what the council is judging** — the architecture and forward path. But **it is NOT deployed**: per the project's own feature-shipped rule (bundle-green ≠ shipped; open the URL), nothing in this window is "shipped" until VPS deploy (Plan-6 Task 15, PM-gated) passes a live probe.

**The one-line gut check for the council:** the learner today sees roughly one subject's worth of figure-less hand-authored lessons. Everything sophisticated discussed below is CURRENT-only or spec-only.

Spec: `C:/Users/User/jarvis-kotlin/docs/superpowers/specs/2026-06-11-one-pass-digestion-teaching-engine-design.md`. Plan index: `C:/Users/User/jarvis-kotlin/docs/superpowers/plans/2026-06-11-one-pass-plan-index.md`. Ledger: `C:/Users/User/jarvis-kotlin/docs/superpowers/plans/2026-06-13-remaining-work-to-100.md`.

---

## 1. WHAT THE TUTOR IS + THE LEARNER

**The goal:** a tutor that takes ANY university lecture/lab/exam file and auto-produces lessons that (a) build intuition first for a learner who doesn't get it yet, (b) fill the prerequisite gaps the learner lacks, (c) use visuals only when they actually aid, (d) teach with real pedagogy, (e) reach genuine technical depth, all up to spec on UI/UX. It is the **tutor vertical** of a larger life-OS; only the tutor is being built.

**The learner — Alex (hard constraints, `alex-learner-profile.md`):** first-year AI bachelor, UAIC Iași; subjects ALO, PA, PS, POO, **SORC** (SO+RC = one subject, two domains); finals June 2026. **Unmedicated ADHD** → un-skippable gates, session caps, never guilt-trip, never "go sleep." **Programming very low**, trapped in an "ask-AI-then-read-backwards" loop the tutor exists to break (→ productive-failure ATTEMPT-before-answer; never write his code for him). **Math foundations shaky.** **Visual / 3Blue1Brown learner** — real visualizations only, despises "fancy text in colored boxes." **No walls of text** — one concept at a time. **Romanian for all learning content, English for meta** (hard line, chronically violated).

**The load-bearing constraint — NO-ORACLE-INVERSION (`feedback_no_oracle_inversion.md`):** *Alex verifies NOTHING about content, ever. The only thing he ever hands over is a raw FILE.* He cannot vet content or name a subject's pedagogy. The system must DERIVE each subject's shape from the material, not from Alex. This is **why** the spec puts all truth-checking on machines and reserves only an *experience* approve/reject for the human — and it is exactly why the recent failure (§6) is so dangerous: the one human who could catch a bad lesson is definitionally unable to.

---

## 2. THE INTENDED PIPELINE (spec)

One single pipeline, applied identically to every artifact — *"exactly one path from raw material to the learner; no side doors"* (spec §1.1). Seven stages:

**Upload → Classify → Digest → Verify → Serve → Track → Revise.**

- **Upload:** drop a file (PDF/HTML/scan/code-archive/text) or URL. Nothing else asked.
- **Extraction-legibility gate FIRST, before classify** (§2.3): garble poisons everything downstream, so it's checked first → auto-retry alt extractor/OCR → else park as a gap-record asking for a readable FILE (files, never judgments).
- **Classify** by content (never filename) into one of 9 artifact kinds (§2.2), tagged subject + exam component.
- **Digest** (§3): decompose into **knowledge components (KCs)**, **per-beat teaching content** for each KC, problems with parameter slots, rubrics, grade-model facts, dates. All generated **in Romanian** (§8, RO-only generation, not translate-after).
- **Verify** — machine-only gate chain in order (§9.2): (1) extraction → (2) trust-net faithful-check (admission gate) → (3) semantic figure gates (trace-match + invariant) → (4) rendered Playwright gates (no-clip, legibility/contrast, interaction, language) → (5) Impeccable design-lint → (6) visual baselines → (7) grader eval harness. **"No human verifies truth."**
- **Serve:** 5-beat lessons + drills + code practice + mock exams, via the priority queue.
- **Track:** every interaction writes unified FSRS + EWMA mastery; exam dates shape queue priority.
- **Revise:** forgetting → compressed re-lesson with fresh parameter-slot values.

**Then the ONE human step (§1.4):** after all machine gates go green, the pipeline STOPS, presents the rendered real lesson, and the user **approves or rejects with a one-line note — on EXPERIENCE, never truth.** Reject #1 → one regeneration folding the note; reject #2 → routes to design review, never a third regen. Approved shape becomes the **reference pattern** for the next artifact of the same kind×subject, so quality converges.

**Intuition-first / prereq-fill / visuals-when-they-aid (the model):**
- **Intuition-first** is structurally enforced by the fixed, never-reordered **5-beat deck** (§4.2): **① PREDICT ② ATTEMPT ③ REVEAL ④ NAME ⑤ CHECK** — predict + attempt precede the formal NAME. Per `concept_type` the engine picks the beat variant (e.g. definition-taxonomy = classify-an-example-first; numerical = skeleton-dominant arithmetic trace, geometry = 1-screen anchor only). The arc is I-do → we-do → you-do (§6.1).
- **Prereq-fill:** R-PREREQ edges on the KC graph gate availability; **EC2 cold-start completeness gate** (§15.2, INV-EC2.1) requires every servable KC to have a prerequisite path to an entry KC, or the missing rung is made explicit as an "assumed-knowledge stub" flagged `neverificat`. **EC1 prereq-peek** serves a compressed prereq reveal on demand.
- **Visuals only when they aid:** a figure is never free-form — it must be `(family_id, instance_data)` (§5.1) and is gated by semantic correctness, not decoration (*"a figure that animates something the algorithm doesn't do cannot pass"* §5.4). SVG-only is locked (§5.6) partly on pedagogy grounds (scroll/mouse decoration opposes the ADHD-paced sequential form). Coverage is conditional (INV-EC2.2): figure where the concept_type calls for one, else a logged `figure-deferred`.

**The faithfulness gate (trust-net):** all content must pass faithful-check before serving; a KC with `verification_status != faithful` is 404'd. Truth = machines; experience = the one human checkpoint; *"neither party does the other's job."*

---

## 3. WHAT'S ACTUALLY BUILT vs GAP (reality, not doc-claims)

The crucial finding across all gatherers: **the spec describes an auto-generation pipeline that does not exist. The system today is a hand-authored content hub with a sophisticated serving + tracking layer.**

| Capability | Built? | Runs (CI)? | LIVE? | Verdict |
|---|---|---|---|---|
| **Digestion** (PDF/URL → KCs) | ❌ | ❌ | ❌ | **SPEC-ONLY [neither]** |
| **Beat GENERATION** | ❌ | ❌ | ❌ | **SPEC-ONLY [neither]** |
| 5-beat lesson SERVING | ✅ | ✅ | ✅ | WORKS **[both]** — beats hand-authored in YAML |
| **Figure-data GENERATION** | ❌ | ❌ | ❌ | **SPEC-ONLY [neither]** — instances hand-authored |
| Figure families (5) + serving | ✅ | ✅ | partial | WORKS **[CURRENT]**; 0 instances LIVE |
| Faithfulness gate (trust-net) | ✅ | ✅ | ✅ | WORKS **[both]** — 8 DB rows, 4 faithful |
| Queue / Azi + FSRS tracking | ✅ | ✅ | ✅ | WORKS **[both]** — no auto-trigger |
| Code-practice grading | ✅ | ✅ | ✅ | WORKS **[both]** — 0 real problems |
| Forgetting → re-lesson w/ fresh params | ❌ | ❌ | ❌ | **SPEC-ONLY [neither]** |
| Exam digestion / parsing | ❌ | ❌ | ❌ | **SPEC-ONLY [neither]** |
| **Pedagogical-quality gate** | ❌ | ❌ | ❌ | **DOES NOT EXIST [neither]** |

Point-by-point, marked:

- **Digestion run-state — [neither].** No code ingests a PDF/URL and produces KCs. What exists: `ContentRepo.kt` (reads hand-authored YAML), `ContentValidator.kt` (validates authored content), `ContentCli.kt`/`ContentReconcile.kt` (admin tools). Every KC in `content/{subject}/kcs/` is hand-written YAML. **Total real KCs authored: 5** — 4 PA (`pa-kc-001..004`) + 1 ALO (`alo-kc-gauss-elim`). **0 PS, SO, RC, POO.** The spec's §2 (classify) and §3 (decompose) are pure spec, zero code. **This is ~50% of the spec, and it is the half that makes "auto-produce for ANY topic" true.**

- **Lesson-beat generation — generated vs hand-authored: HAND-AUTHORED [both].** Beats are hand-written in YAML (`pa-kc-001.yaml:77-133`: predict/attempt/reveal/name/check all hand-written). The *serving* mechanism is built and works (`GET /api/v1/lesson/{kcId}`, `POST .../beat`, `BeatSelector.planFor()`, the 5 React beat components in `BeatOrchestrator.tsx`). The validator `KcBeats.isCompleteFor` only *validates* authored beats — it never *produces* them.

- **Figure-data generation — generated vs hand-authored: HAND-AUTHORED [CURRENT-only for instances].** 5 families built and wired in `familyRegistry.ts`. **7 viz instances exist, all hand-authored YAML** (`content/{subject}/viz/*.yaml`), e.g. `viz-pa-selectsort-001.yaml` = 18 hand-built steps of `data_json`. **This hand-authoring is the direct cause of the §6 failure: never-sorts and dead-frame figures are latent by construction.**

- **Guidance core (queue / tracking) — [both].** Queue reading + FSRS + phase tracking built and live. But **no auto-interventions**: §7.3 forgetting→re-lesson and §7.4 exam-proximity re-weighting have NO code, no cron. The live DB has 828 FSRS cards (~89% English, zero kc_id backfill — legacy residue).

- **The pedagogical-quality gate — DOES ONE EXIST? NO [neither].** This is the heart of the council's question. **There is no autonomous machine gate that judges "does this lesson actually teach."** By design (§1.1, §9.4(e)): the machine gates cover *truth and rendering only* — faithfulness, trace-correctness, render-correctness, legibility, language, no-clip. The spec is emphatic that lint/baseline gates are "geometry/CSS only" and *"citing gates 5–6 as closing the one-pass loop is explicitly false."* **The only judge of whether a lesson teaches well is the human experience checkpoint (§1.4)** — and that human is Alex, who by the no-oracle-inversion constraint cannot reliably judge pedagogy. The spec's fallback is §10.3: *"a defect the user catches is a missing gate, and the gate gets built"* — i.e. pedagogy gates are meant to *accrete from caught failures over time*, not exist up front.

---

## 4. THE PEDAGOGY MODEL (locked) + where the pipeline does/doesn't enforce it

**Locked decisions (treat as premises):**
- **5-beat gated deck** (council-1781052957): ① un-skippable PREDICT (commitment gate) → ② ATTEMPT before any answer (productive failure) → ③ ANIMATED reveal FUSED with explanation (figure stays live with callouts; not a static card — the Khan "watch-then-forget" bug) → ④ NAME → ⑤ CHECK. Next disabled until each gate clears; reading dwell-floor so a wall of text can't be skipped in 200ms.
- **Engine picks the beats** per concept_type × mastery (closed plan set FULL/STANDARD/MASTERED-REVISIT/RE-LESSON). HOW-to-teach is the tutor's job, not Alex's.
- **Numerical methods** (council-1781097621): arithmetic trace is the vehicle, ACTIVE + learner-paced + skeleton-dominant; geometry = 1-screen anchor only, never interleaved.
- **I-do/we-do/you-do**; misconception fired → targeted remediation drill re-queued ahead of new material; grader stack ordered by trust with **LLM last and never alone** (INV-6.3).

**Where the pipeline enforces it:** the 5-beat form is enforced by INV-4.1 (beat-plan legality) and the BeatOrchestrator gating — these are real and built [CURRENT, CI-green]. The grader trust-order is spec'd as INV-6.x. So the lesson *form* is locked AND machine-checkable.

**Where it does NOT — the single largest pedagogy gap: the DEPTH artifact.**
- Alex's load-bearing requirement (`feedback_lesson_layered_depth.md`, 2026-06-14): intuition is the front door, but a student **MUST reach real technical depth.**
- **council-1781433195 ruled the cheap fix WRONG (3 REJECT / 2 CONDITIONAL):** "detail = a second render-skin of the same figure + an O(n²) line" is *the same picture at two zoom levels, not two kinds of knowledge.* Genuine depth — loop **invariant** + why it holds, complexity **DERIVATION** (not the answer), correctness argument, edge cases, actual code, tradeoffs — is **non-figural, prose/proof/code by nature, and the locked figure-family architecture structurally has no slot for it.** Depth must be a **separate, concept-keyed, first-class artifact with its OWN authoring budget and its OWN correctness gate**, flagged `neverificat` until gated (the Risk Analyst: depth inherits NONE of the figure's verification → false mastery, catastrophic before an exam).
- **Enforcement status: ABSENT.** A full grep of the 739-line binding spec (incl. the same-day §15 EC1–EC5 amendment) returns **zero matches for `depth`, `intuition`, `drill-to-detail`, `invariant`, `complexity derivation`.** §15 added EC1–EC5 (live-help, cold-start, recovery, goal-layer, provider-health) but **NOT** a depth artifact, depth concept_type, or depth gate. The NAME beat (④) is one fused sentence, not the separate gated proof/code/derivation artifact the council mandated. **The locked pedagogy demands a depth artifact with its own gate; the spec does not yet name, schema, or gate it; the figure architecture cannot host it; only the visual layer is built (the 2026-06-14 selectsort demos prove the VISUAL layer only).**

---

## 5. THE UI/UX + VISUAL STATE

**Two surfaces, a deliberate polish gap:**
- **Production lesson surface (`BeatOrchestrator.tsx`) [both]:** correctly gated, monospaced brutalist (JetBrains Mono self-hosted), hard-offset shadows, reduced-motion respected, accessible, responsive. **NOT premium-polished:** square pips, no masthead/trust-badge, content in a `max-w-3xl` centered box, functional chrome.
- **The premium bar — Lectie demos (`/tutor/lectie-selectsort`, `-mergesort`) [CURRENT, demo-only]:** mount the REAL families inside a full-viewport cinematic shell mirroring `lectie.html` — masthead brand pill, trust badge, elegant bar-pips, dark floating-figure stage, restyled footer. These are **showpieces proving the families *can* read premium**; production is functional-first.

**The visual-generation approach — constrained SVG families:**
- 4–5 families (`SequenceArrayFamily`, `SortMergeFamily`, `GraphTreeFamily`, `ChartDistributionFamily`, `MatrixGridFamily`), each a typed `*Data`/`*State` with a hand-rolled parser-oracle (cardinality preserved, pointer bounds, phase enum). Correctness stamps in the SVG (`data-cell-index`, `data-pointer-index`); a **trace-match harness** runs an oracle from the seed `values` and asserts rendered frames == oracle frames.
- **Three render skins** (white / dark / 3B1B "bars") share ONE geometry contract, so invariants hold across skins. Motion is tuned (SWAP_EASE, 420ms slides), `reducedMotion="user"` zeros durations.
- **"Measured callout" no-clip gate** (INV-4.4/5.3/8.4): callout chip measured in the render loop so it never clips; asserted in Playwright (the `viz_no_clip_gate` "talked-about-1000-times" cardinal sin, machine-checked).

**Visual issues / gaps:**
1. **Polish split** demo-vs-production (deliberate, but real).
2. **Figure height clamp:** `LessonFigureShell` caps figure at `min(42vh, 320px)` ≈ 272px on Alex's 648px content height — a multi-row figure can feel cramped (verify at HIS res, viewport screenshots not fullPage, per `reference_alex_screen_resolution.md`).
3. **Pips inconsistency** between surfaces. 4. **Dwell-floor heuristic** can feel artificial. 5. **Dark-surface 1px hairline contrast** faint.
- **The dark reskin (SESSION-68) [CURRENT-only]** is first-pass; theme system + 6 alt palettes exist via `DoorWarm`/`ThemePicker` (demo-only). Plan W (warm 2nd theme) ratified, sequenced after Plan V.

**Critically (links to §6): the visual *correctness* gates verify GEOMETRY — none verifies the figure makes pedagogical sense or shows visible progress.**

---

## 6. THE FAILURE HISTORY + THE 2 PRIOR COUNCILS (build ON these)

**The recurring failure (this session, not yet in BRIDGE-LOG):** a full session was burned trying to make the production lesson "look good"; one root error re-fired in five shapes (`council-1781471050.md:3`, `council-1781475889.md:3`):
1. **Never-sorts figure shipped** — mergesort (`pa-kc-002`) bound to `family_id: graph-tree`, a static divide-tree where "merge" is an instant text relabel (`"9 3"→"3 9"`); it shows structure and **never actually sorts.** `FigureBindingNonVacuityTest.kt:72` **pins this wrong binding as correct.**
2. **Dead frames** — a follow-up "sort-merge" figure had frames 2/3/4 do nothing, yet rendered cleanly.
3. **Hand-authored, not generated** — per-step JSON hand-authored per instance → never-sorts and dead-frames latent by construction.
4. **CSS-reskin worse than the existing hand demo.**
5. **Repeated false "verified"** — subagent self-certified a dead-frame figure "READY"; PM claimed "verified every frame" after checking **3 of 30.**

**Through-line:** no part of the pipeline can JUDGE whether a figure teaches the concept. Every gate verifies geometry (trace-match / no-clip / invariants); **none verifies pedagogical fit or visible progress** — so an all-gates-green figure teaching the wrong mental model passed everything, and the one human who could catch it (Alex) can't.

**Council 1781471050 — figure ARCHITECTURE (confidence 9/10):**
- **REJECTED** CSS-reskin + reference-replication (a reference is a fixture, not a target).
- **KEPT (sound):** the constrained-family / render-once architecture — `SequenceArrayFamily.tsx` proves a family can be premium + animated + oracle-correct.
- **ROOT CAUSE:** a *process* concept bound to a *structure* family; every gate checks geometry, none checks fit.
- **CORRECT PATH:** (1) **re-bind, don't reskin** — mergesort → animated seq-array-class merge, tokens physically converge into one sorted row, last frame = sorted; (2) **add a TIME axis** — a stepped family whose typed data is an ordered state sequence, so "show it happen" is the default; (3) **build the concept→family FITNESS GATE** (the load-bearing missing gate) keyed off concept_type: assert the family expresses the concept's defining dynamic + the final frame reaches the taught end-state, **fail-CLOSED → HALT + "needs new family" backlog**, never silent fallback; (4) families own the premium look (zero per-lesson CSS); (5) families = **extensible registry**, not a closed 4.

**Council 1781475889 — visual-production METHOD (confidence 9/10):**
- **REJECTED** the method on two unanimous structural flaws.
  1. **Keyframes HAND-AUTHORED, not generated by running the algorithm.** Fix: figure = deterministic function of the real algorithm — `run(algorithm, input) → typed state/event stream → keyframes → existing scrubber shell`. Then dead-frames/never-sorts are **structurally impossible** (the VisuAlgo/Galles/Manim standard).
  2. **Verification = sampling + self-certification — inadmissible.** Author writes both states and oracle (tautology); 3-of-30 spot-check ⇒ ~90% unverified; across N figures P(zero defects)→0. Fix: **ship authority comes ONLY from green CI gates computed from RENDERED frames** — (a) every-frame-advances (perceptual-hash dead-frame detector; legit holds = explicit `hold:true`), (b) final == sorted(input), (c) monotone-progress (inversions non-increasing), (d) no-regression vs incumbent (perceptual-diff). **No agent or PM "GOOD/verified" carries ship authority; PM eyeball is additive, never a substitute.**
- **KEEP** per-family SVG renderer + framer-motion scrubber shell. **RETIRE** hand-authored per-step JSON + the delegate-to-Opus-then-spot-check loop → replace with **generate → gate → eyeball-once.**

**Settled premises (do NOT re-litigate):** constrained-family/render-once architecture is sound; the figure IS the execution trace (generated, never hand-authored); self-certification is inadmissible; the named CI gates are mandated; re-bind mergesort off graph-tree; add a time/stepped family; registry is extensible. **Open space:** how concept_type→family fitness is concretely encoded; the typed-event schema for the stepped family; how the instrumented Kotlin trace crosses to the React renderer (contract/serialization); perceptual-hash thresholds + the explicit-hold mechanism; how no-regression baselines are stored; how "needs new family" surfaces as backlog vs hard HALT.

---

## 7. THE OPEN QUESTION FOR THE COUNCIL

**Is the jarvis-kotlin teaching-generation pipeline architecturally correct to reliably AUTO-PRODUCE genuinely good lessons for ANY topic** — lessons that build intuition first, fill prerequisite gaps, use visuals only when they aid, teach with real pedagogy, reach genuine technical depth, and are fully up to spec on UI/UX? **What's covered, what's the gap, what's the correct path?**

The two prior councils settled the **figure sub-problem** (generate-from-algorithm + machine gates from rendered frames + a concept→family fitness gate + extensible registry). This council must zoom OUT to the **whole teaching-generation pipeline** and resolve the gaps those councils' scope did not reach. Specific sub-questions:

1. **Is the architecture right end-to-end?** The figure layer now has a generate-then-gate model. Does that same model extend to the rest — does the spec's "one pipeline, no side doors" hold when **the entire digestion/generation half (§2–3) does not exist** and **everything served today is hand-authored**? Is the right path to build digestion to feed the proven serving layer, or does the serving layer's correctness depend on assumptions only generation can satisfy?

2. **What is genuinely covered vs a gap?** Covered (built + CI-green, CURRENT): 5-beat serving, faithfulness gate, FSRS/queue tracking, code-grading, 5 figure families with geometry gates. **Gaps:** (a) the **entire digestion/generation pipeline** (~50% of spec, the part that makes "ANY topic" real); (b) the **DEPTH artifact** — a ratified Alex requirement the spec doesn't name/schema/gate and the figure architecture can't host; (c) the **pedagogical-quality gate** — there is NO machine gate on "does this lesson teach," only a human checkpoint the learner can't reliably fill; (d) **EC1–EC5** experience layer (spec'd, unbuilt); (e) **5 of 6 subjects** have zero content; (f) the **deploy gap** — nothing in the 140-commit window is live.

3. **Is there ANY gate on "does this lesson actually teach"?** Today: **no.** The §1.4 human checkpoint + the §10.3 "caught defect → new gate" accretion is the only mechanism, and it depends on a learner who cannot vet content. The figure councils just proved that the equivalent geometry gates let a wrong-mental-model figure pass. **Does the pedagogy/depth layer need its own machine ESTIMATOR-class gates (per P1: ESTIMATOR + `neverificat` flag, never fake-green) the way the figure layer now does — and what would they concretely assert?**

4. **What is the correct path to a tutor that auto-produces lessons up to spec on UI + UX + teaching + intuition + completeness?** Sequencing context: the ledger orders **V → 5 → 7** (figures, then digestion, then tracking/proof-run), W after V; governing principles are **P1 NO-SPEC-CUT** (never silently drop; PROVEN-by-gate or ESTIMATOR+`neverificat`, never fake-green) and **P2 EASY⇒GATED** (typed input → renderer/template verified once → auto-gates ride for free; no bespoke un-gated content). The council should rule whether this sequence and these gates are sufficient, or whether the depth artifact and a pedagogical-quality estimator must be promoted to first-class, schema'd, gated pipeline stages before "auto-produce good lessons for any topic" is architecturally honest.

**Bottom line for the council:** the SERVING + tracking + figure-correctness architecture is sound and partly built; the GENERATION half is absent; and there is no machine gate — and a structurally-blind human checkpoint — on the two things the council's question centers on: *does this lesson teach, and does it reach real depth.* The figure councils proved geometry-green ≠ teaches-right for one artifact type; the open question is whether that lesson generalizes to a required pedagogy/depth gate across the whole pipeline, and what the correct end-to-end path is.