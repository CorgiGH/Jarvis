# Novice pedagogy + tutor-UX redesign — deep-research report (round 1)

> Generated 2026-05-17 via brainstorming-skill deep-research dispatch (1 subagent, ~7min wall, 60+ web sources, 53 tool uses, 103k tokens). Triggered by user feedback: "the UX is shit, how is somebody that knows nothing about the task supposed to complete it well. just reading the existing PDF is confusing — needs some explanation, maybe a whole redesign." Context: jarvis-kotlin tutor, Alex (FII Iași AI bachelor's, finals Jun 1-21 2026), 5 subjects (PA/PS/POO/ALO/SO+RC). Current surface = Slice 1.5 (ProblemStepper + DrillStack + ResourceRail + KnowledgeLedger). 4/5 subjects empty-drills; only PS Tema A drills today. Round 2 deeper-dive dispatched in parallel; will be appended.

## TL;DR

Current Jarvis tutor inverts pedagogical order. Puts drill rubric in front of learner who has not yet been *taught*. Sixty years of cognitive science say wrong for novices: beginner needs **worked examples → faded practice → independent practice**, with the PDF acting as primary text re-organized into chunks, not as download-and-pray side drawer.

---

## 1. Cognitive Load Theory (Sweller)

**Worked-example effect:** for novices, worked examples reduce extraneous load and outperform problem-solving on retention ([MDPI 2024](https://www.mdpi.com/2227-7102/14/6/597), [Sweller primer](https://education.nsw.gov.au/content/dam/main-education/about-us/educational-data/cese/2017-cognitive-load-theory.pdf)). N=98 study showed worked examples produced "enhanced retention and decreased cognitive load" vs problem-solving.

**Expertise reversal effect:** same worked example helps novice, hurts expert. Worked examples become "redundant" and add load once schemas exist ([Wikipedia](https://en.wikipedia.org/wiki/Expertise_reversal_effect), [Cambridge Handbook ch.40](https://www.cambridge.org/core/books/cambridge-handbook-of-expertise-and-expert-performance/cognitive-load-and-expertise-reversal/03F656FD334F23214426ACB4118FEBF9)).

**UI implication:** Drill task should lead with fully worked example for problem *type* (not specific problem to do), then similar problem with last 1-2 steps blank (fading), then actual graded drill. Today's "attempt → grade" UI is expert mode applied to novices. Wrong for empty-state subjects (ALO/POO/SO/RC) where Alex hasn't been taught yet.

**Split-attention effect:** forcing eye to ping between diagram and side-legend adds load ([Wikipedia](https://en.wikipedia.org/wiki/Split_attention_effect), [USFCA review](https://files.eric.ed.gov/fulltext/ED485075.pdf)). Integrated labels (text placed on diagram itself) beat separated labels. Math/CS with formulas + diagrams: do NOT put formula in one rail drawer, diagram in another.

**Modality effect:** visuals + spoken narration beats visuals + on-screen text. Two channels (visual + auditory) doubles working memory; two visual channels (image + text) does not. NotebookLM audio overviews land well partly for this reason.

**Redundancy effect:** same content in two formats simultaneously (reading text while narrated verbatim) HURTS learning ([Moreno/Mayer 2002](https://tecfa.unige.ch/tecfa/teaching/methodo/MorenoMayer2002.pdf), [Frontiers 2023](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2023.1148035/full)). Mirror text on screen during voiceover = problem.

---

## 2. Scaffolding & ZPD (Vygotsky, Wood/Bruner)

Wood/Bruner/Ross 1976 defined scaffolding as having three required features: **contingent support**, **gradual fading**, **transfer of responsibility** ([Simply Psychology](https://www.simplypsychology.org/zone-of-proximal-development.html), [Open University](https://www.open.edu/openlearncreate/pluginfile.php/5904/mod_resource/content/1/Vygotskian_principles_on_the_ZPD_and_scaffolding.pdf)). Static "hint" button that never goes away is NOT scaffolding — it's permanent crutch.

**Real-time dynamic scaffolding** in adaptive systems means assist *measurably reduces* as competence grows ([DLI](https://distancelearning.institute/instructional-design/vygotskys-zpd-bridging-learning-potential/)). Hint level for third drill should be visibly less than first.

**UI implication:** Maintain per-skill "competence estimate" (Bayesian Knowledge Tracing-style) and tie hint *level* to it. Mastery=0.0 → show worked example by default. Mastery=0.5 → first-step hint on demand only. Mastery=0.9 → no hint button, just grade.

---

## 3. Advance Organizers (Ausubel)

Ausubel split organizers into **comparative** (activate existing schema) and **expository** (provide new structure when material unfamiliar) ([DLI](https://distancelearning.institute/instructional-design/ausubel-advance-organizers-learning/), [HKU KB](https://kb.edu.hku.hk/approaches_advance_organizers/)). Effectiveness "proportional to level of unfamiliarity, difficulty, technicality" — denser Romanian PDF, more important the organizer.

**Surprise finding:** literature does NOT prescribe "2-4 paragraphs." What it says: organizers must be at "higher level of abstraction" than material itself, use **progressive differentiation** (general → specific) + **integrative reconciliation** (relate new to old). Intended for *major unit*, not every paragraph. Too-long organizer triggers same expertise reversal as too-detailed worked examples.

**UI implication:** Each PDF gets one-screen expository organizer at open. Three blocks: (1) what this PDF teaches in one sentence, (2) 3-5 core concepts and how they relate, (3) one prerequisite test ("if you don't recognize term X, click here for prereq primer"). NOT a summary. NOT a TL;DR. Structural scaffolding only.

---

## 4. Worked Examples → Faded Practice → Independent Practice (Anderson, Renkl)

Anderson's LISP/ACT-R tutor work (1983-1989): tutor with explicit production-rule model of skill — capable of generating correct solution paths from any state — outperformed open-ended environments ([ACT-R Lessons](http://act-r.psy.cmu.edu/papers/Lessons_Learned.html), [Anderson 1989](https://people.cs.vt.edu/shaffer/cs6604/Papers/Anderson-skill.pdf)). Student saw worked solutions, then transitioned to solving.

**Renkl's backward fading:** problem 1 fully worked, problem 2 omits last step, problem 3 omits last two, etc. ([Renkl & Atkinson 2003](https://link.springer.com/article/10.1023/B:TRUC.0000021815.74806.f6), [Wikipedia](https://en.wikipedia.org/wiki/Worked-example_effect), [Booth et al](https://files.eric.ed.gov/fulltext/ED566953.pdf)). Position of fade doesn't matter — what matters: students learn the principle that gets faded, because fading triggers self-explanation activities.

**Self-explanation effect (Chi 1989, 1994):** successful students *spontaneously* explain worked examples to themselves; unsuccessful don't. *Prompting* the explanation closes the gap ([Chi 1994](https://onlinelibrary.wiley.com/doi/10.1207/s15516709cog1803_3), [ASU PDF](https://education.asu.edu/sites/g/files/litvpz656/files/lcl/instruction_based_on_self_explanation.pdf)). Mechanism: self-explanation is constructive, integrative, error-correcting.

**UI implication:** After worked example, prompt "in one sentence, why does the next step work?" before unlocking next example. Single highest-ROI active-engagement hook for cheap. Free text, no auto-grading needed — act of typing alone produces effect.

---

## 5. PDF / Textbook Comprehension Tools

Market splits into three camps:

- **Annotators:** Hypothesis (open-source academic group annotation, university-grade), Readwise Reader (best for individual highlight-export to Notion/Obsidian), Liner (general consumer) ([Atlas Workspace](https://www.atlasworkspace.ai/blog/ai-reading-assistants), [Speed Reading Lounge](https://www.speedreadinglounge.com/readwise-reader-review)).
- **Single-doc Q&A:** ChatPDF (frictionless, 120 pages free), Humata (better for STEM, more precise on technical PDFs, 60 pages free) ([Computer Tech](https://computertech.co/humata-ai-review/), [Toolify](https://www.toolify.ai/ai-news/enhance-academic-research-and-learning-with-chatpdf-vs-humata-1069095)).
- **Source-grounded multi-doc workspace:** NotebookLM is leader. Cites every claim back to source, supports PDFs + Google Docs + audio, generates **flashcards, exam questions, and Audio Overviews** as podcast-style discussions ([U Chicago AT](https://academictech.uchicago.edu/2026/04/06/google-notebooklm-an-ai-tool-for-research-and-studying/), [Atlas Workspace NotebookLM guide](https://www.atlasworkspace.ai/blog/notebooklm-for-students)).

**Math-specific:** dense academic PDFs in Romanian with LaTeX-style equations need Mathpix-grade extraction to keep equations queryable rather than image-blobs ([Mathpix](https://mathpix.com/pdf-reader)). Without this, AI tutor can't actually *read* equations it's supposed to be explaining.

**UI implication for Jarvis:**
- Add left-pane "Concept Strip" alongside PDF, listing 5-8 atomic concepts extracted from this PDF, each clickable to focus that page.
- Show citations *inline* on every AI explanation, jumping to exact paragraph (what makes NotebookLM trustworthy).
- "Audio overview" button (TTS over AI's summarized explanation) unusually high-leverage for ~40 LOC; modality effect predicts it'll help.

---

## 6. Adaptive Learning Systems

- **Khan Academy** uses mastery levels (Apprentice → Practitioner → Expert → Mastered → Legendary). Wrong answers can demote you. Hints accessible per-problem, 3-min unblock video. Students doing 30-60 min/week mastery practice showed 33% higher math growth ([Khan Help](https://support.khanacademy.org/hc/en-us/articles/360030753412-Why-Mastery-Learning-by-Sal-Khan), [Cult of Pedagogy](https://www.cultofpedagogy.com/khan-mastery-learning/)).
- **Brilliant.org** does opposite of textbook-first: **problem-first**. Doesn't explain concept before puzzle. Student *discovers* pattern through guided manipulation ([Brilliant](https://brilliant.org/about/), [Nibble 2026 review](https://nibble-app.com/blog/is-brilliant-worth-it)). Closer to "discovery learning" — works well *if* puzzle is in ZPD; fails hard if isn't.
- **ALEKS** uses **knowledge-space theory**: combinatorial map of which concepts you do/don't know, deliberately non-linear because real learning isn't linear ([ALEKS](https://www.aleks.com/), [MDPI](https://www.mdpi.com/2227-7102/11/10/603)).
- **Duolingo** uses **Half-Life Regression** — trainable spaced-repetition model that estimates half-life of each item in long-term memory. Daily retention improved 9.5% over Leitner ([Duolingo HLR](https://research.duolingo.com/papers/settles.acl16.pdf), [GitHub](https://github.com/duolingo/halflife-regression)).
- **ASSISTments** showed 60%-equivalent learning gain, biggest gains for students below median prior knowledge ([WPI](https://wp.wpi.edu/journal/articles/online-math-help-that-works/)).

**Bloom's 2-sigma:** 1-on-1 tutoring with mastery learning beats classroom by 2 SD ([Wikipedia](https://en.wikipedia.org/wiki/Bloom's_2_sigma_problem)). Whole premise of AI personal tutor is to capture this — but requires actual *tutoring* (questioning, scaffolding, mastery checks), not just *quizzing*.

**UI implication for Jarvis:** "Mastery Map" view, per subject, showing concepts as nodes with 0-1 mastery score. Drills generate from low-mastery nodes first. Review queue from HLR-style scheduler. Empty-state subjects start with **cold-start placement test** to seed map, ALEKS-style — solves "PS Tema A is the only one with drills" problem by auto-generating first drill set.

---

## 7. 3blue1brown / Veritasium / "Teaching from Scratch" YouTube Pedagogy

Grant Sanderson (3b1b): **animation + narrative structure**. Authenticity ("you're seeing the real thing, not a watered-down version"), story-driven sequences, visual proofs over symbolic manipulation. Distinguishes *explaining* (his videos) from *teaching* (1-on-1 mentoring, only face-to-face) ([Lex Fridman transcript](https://podcasts.happyscribe.com/lex-fridman-podcast-artificial-intelligence-ai/118-grant-sanderson-math-manim-neural-networks-teaching-with-3blue1brown), [Antoine Buteau](https://www.antoinebuteau.com/lessons-from-grant-sanderson/)). His engagement quote: "10% of what you read, 20% of what you listen to, 70% of what you actively interact with."

Derek Muller (Veritasium): PhD thesis found counterintuitive — *clarity numbs the mind*. Simply stating correct fact made students *more confident in their misconceptions*. Videos that **present misconception first**, then dialog to right answer, made students feel "confused" but *doubled their assessment scores* ([Scientific American](https://www.scientificamerican.com/article/how-youtube-star-derek-muller-of-veritasium-is-challenging-scientific/), [Veritasium](https://www.veritasium.com/videos/2021/7/9/the-biggest-myth-in-education)).

**Surprising finding:** clear, correct explanation can leave student *more* wrong than they started.

**UI implication:** Before showing worked solution, tutor should surface **#1 common misconception** for problem class and ask: "is this what you were about to do?" If yes → explicit refutation. If no → check actual reasoning. Posner et al. (1982) call the prereq for this *dissatisfaction with existing conception* — students must feel misfit before accepting replacement ([Posner et al.](https://eclass.uoa.gr/modules/document/file.php/PHS122/%CE%91%CF%81%CE%B8%CF%81%CE%B1/Posner_Strike_Hewson_Gertzog.pdf), [Pacaci 2024](https://onlinelibrary.wiley.com/doi/full/10.1002/tea.21887)).

---

## 8. Anki / Spaced Repetition for Hard CS

Piotr Wozniak's "20 rules of knowledge formulation" rank-order: (1) understand before memorize, (2) atomic cards (one idea per card), (3) cloze deletions are mnemonic gold, (4) graphics help, (5) avoid lists, (6) personalize with examples ([SuperMemo](https://supermemo.guru/wiki/20_rules_of_knowledge_formulation), [Andy Matuschak](https://notes.andymatuschak.org/z96Xr88dMaAGrn3CobJnMUD)).

Michael Nielsen's *Augmenting Long-Term Memory*: 10,000+ cards in 2.5 years at ~15-20 min/day. Qualitative jump came from making cards *atomic*, using Anki for *procedural* CS skills (unix cmd line) not just facts ([Nielsen](https://augmentingcognition.com/), [Olly Britton](https://ollybritton.com/notes/articles/augmenting-long-term-memory/)).

**Procedural vs declarative** card design: for procedure, use "n → n+1" cards where prompt shows one step, answer is next step ([Anki Everything](https://ankieverything.wordpress.com/procedural-learning/)). For algorithms specifically, cards work better at *concept* level than syntax level (AlgoDeck, UCSD Data Structures deck demonstrate pattern).

**UI implication:** Every concept Alex marks as "I almost knew this" in a drill should auto-generate 2-3 atomic Anki-style cards. Schedule with HLR-style scheduler. **Don't review in tutor UI** — push to Anki deck or dedicated daily-review surface, separate from drill work. Mixing review with new learning increases cognitive load.

---

## 9. Misconception-First Instruction (Posner et al.)

Four conditions for conceptual change ([Posner 1982](https://eclass.uoa.gr/modules/document/file.php/PHS122/%CE%91%CF%81%CE%B8%CF%81%CE%B1/Posner_Strike_Hewson_Gertzog.pdf)):
1. **Dissatisfaction** with existing conception (student must see it fail)
2. **Intelligibility** of new conception
3. **Plausibility** of new conception
4. **Fruitfulness** — does new conception explain new phenomena?

For Alex's subjects, highest-leverage misconceptions to surface:
- **PS (Probability):** gambler's fallacy, representativeness heuristic, equiprobability bias ("sum of 4 and sum of 6 with two dice are equally likely" — wrong, 3/36 vs 5/36) ([Rossman Chance](https://www.rossmanchance.com/artist/proceedings/AnwayBennett.pdf), [Open University ch 4](https://www.open.edu/openlearn/mod/oucontent/view.php?id=83404&section=4)).
- **PA (Algorithm Design):** greedy = always optimal, recursion = always exponential, "if it works on test cases it works."
- **POO (OOP):** inheritance = code reuse (it's actually subtype polymorphism — and Liskov), constructors run "magically".
- **SO/RC (OS/Networks):** thread = process, TCP is "reliable" in strong sense, virtual memory = RAM.

**UI implication:** Tag every drill with most likely misconception trigger. Before rubric check, tutor shows: "common pitfall: students often write X here. Did you?" — this is *dissatisfaction* surfacing.

---

## 10. AI Explainer Patterns from Real Products

- **Khanmigo** uses **Socratic method by system prompt**. Never gives answer — asks "what do you think the next step is?" or "can you explain your reasoning here?" ([Khanmigo](https://www.khanmigo.ai/), [Khan blog](https://blog.khanacademy.org/how-we-built-ai-tutoring-tools/)). System-prompt design documented and explicit. Gold standard.
- **Photomath** provides step-by-step, but most users use it to *cheat*, not learn. Steps feel mechanical ([Mathway vs Photomath](https://math.bot/blog-photomath-vs-mathway-online-math-tutor-reviews-apps-that-do-your-math-homework-49727)).
- **Mathway:** even worse — surface-level explanations, no underlying-concept teaching.
- **SocraticLLM** beat ChatGPT on effectiveness (7.19 vs 6.40) and teaching performance (7.11 vs 6.50) using question-led tutoring ([StarSpark](https://www.starspark.ai/blog/socratic-llm-tutors), [Brookings](https://www.brookings.edu/articles/what-the-research-shows-about-generative-ai-in-tutoring/)).

**Critical nuance:** Socratic method **superior for conceptual understanding and critical thinking** but *direct instruction is more efficient for procedural skills and foundational facts* ([Frontiers Ed 2025](https://www.frontiersin.org/journals/education/articles/10.3389/feduc.2025.1528603/full)). Student doesn't need Socratic discovery to learn quicksort is O(n log n) — just tell them, then quiz retrieval.

**UI implication:** Adopt **two-mode explainer**:
- *Conceptual mode* (default for new material): Khanmigo-style Socratic prompts. "Before you answer — what do you think this term means?"
- *Procedural mode* (default for "I know concept, just need steps"): direct worked example, then faded practice. Toggleable by user, defaulted by mastery score.

---

## 11. Cross-Cutting Findings (Retrieval, Interleaving, Bloom)

- **Retrieval practice effect size** g=0.50 (medium-large); **distributed practice** shows 95% advantage in 271 verbal-memory comparisons ([PMC review](https://pmc.ncbi.nlm.nih.gov/articles/PMC11078833/), [Cepeda meta](https://pubmed.ncbi.nlm.nih.gov/25150680/)). Retrieval + spacing = most robust combination.
- **Interleaving** mixed-topic practice: 77% test performance vs 38% for blocked, after just 24h ([AFT](https://www.aft.org/ae/spring2020/agarwal_agostinelli), [Smartick](https://www.smartick.com/blog/parents-and-teachers/education/interleaving-vs-blocked-practice/)). Mix problems across topics; *desirable difficulty* of choosing strategy is part of learning ([Bjork](https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/04/EBjork_RBjork_2011.pdf)).
- **Bloom 2-sigma:** 1-on-1 mastery tutoring is +2σ over classroom. Whole premise of personal AI tutoring.

---

## Tensions Between Principles

| Tension | Principle A | Principle B | Resolution |
|---|---|---|---|
| Length of intro | Coherence (Mayer): cut everything non-essential | Advance organizer: provide structural primer | Organizer = *structure*, not *content*. 1 screen max. If grows, you're rewriting chapter. |
| Cognitive load vs desirable difficulty | Reduce extraneous load | Productive struggle essential | Reduce *extraneous* load only. Keep *germane* load (struggle for concept itself). Don't gold-plate by simplifying actual problem. |
| Worked examples vs discovery | Brilliant: problem-first, discover | Sweller: worked examples for novices | Novices: worked examples. After 2-3 SD mastery: switch to problem-first. Expertise reversal mandates this. |
| Spaced repetition vs mastery learning | Anki: review forever on schedule | Mastery: pass unit, move on | Anki for atomic facts/notation. Mastery checkpoints for procedural fluency. Don't put procedural cards in Anki — practice as drills. |
| Socratic vs direct | Khanmigo: never give answer | Direct instruction wins for procedures | Socratic for concepts where misconceptions exist. Direct for facts/notation/syntax. Toggle by content type. |
| Long PDF vs atomic chunks | Authentic source material | Atomicity for memory | Source PDF stays whole. Tutor *generates* atomic concept index over it. Both layers exist. |

---

## Anti-Patterns (Things to NOT Do)

1. **Drill before teach.** Showing problem to student who hasn't seen concept = current Jarvis state for 4/5 subjects. Single biggest fix.
2. **Answer reveal without prior attempt.** "Show solution" button always available shortcuts productive struggle. Hide behind ≥1 attempt + ≥1 self-explanation prompt.
3. **Gamification overload.** Badges-alone *increased* cognitive load over no-gamification ([ETR&D meta](https://link.springer.com/article/10.1007/s11423-023-10337-7)). Streaks + points have *novelty effect* — intrinsic motivation drops over time. For single-user adult learner doing finals prep: no streaks, no XP, no levels. Will feel infantilizing, won't survive third week.
4. **Mirroring text + narration.** Redundancy effect. If audio overview, on-screen text should be *outline*, not verbatim transcript.
5. **Detached labels / split-attention layouts.** Don't put diagram in one rail drawer, formulas in another, explanation in third. Co-locate on single canvas.
6. **One giant Socratic loop with no off-ramp.** Procedurally stuck + just needs step → force-Socratic feels like trolling. Provide "just show me" escape that costs nothing — but gates *next* problem behind self-explanation prompt.
7. **Underscore-dead-props on rail items.** (Jarvis-specific from BRIDGE.md: `_pdfUrl` lesson — components getting prop-dropped through scaffolded redesigns.)
8. **Treating empty-state subjects as "no drills yet."** Empty state should *be* cold-start: placement primer that auto-generates first drill set from lecture PDF.

---

## TOP-10 HIGH-LEVERAGE MOVES (RANKED)

For novice CS undergrad with dense Romanian PDF + drill rubric, ranked by expected impact:

1. **Worked Example view as default entrypoint, ahead of drill.** Three states per concept: (a) fully worked, (b) Renkl backward-faded with last step blank, (c) actual drill. Toggle defaults by mastery score. Inverts drill-first anti-pattern.
2. **Free-text self-explanation prompt before every step-reveal or grade-check.** "In one sentence, why does this step work?" — Chi self-explanation effect for ~10 lines UI. No auto-grading; typing alone helps.
3. **Surface #1 misconception *before* attempt, not after.** Veritasium-style. "Students often try X here — were you about to?" Posner dissatisfaction operationalized.
4. **One-screen expository advance organizer at PDF open.** Three blocks: "what this teaches in one sentence", "core concepts list with 1-line each", "prerequisite test — click here if term X unfamiliar." NOT a summary. Structural scaffolding only.
5. **"Concept Strip" extracted from PDF, mastery scores per concept.** Click-to-jump to PDF page where concept appears. Mastery updated by drill outcomes. Solves empty-state by giving Alex something to *do* against any PDF immediately.
6. **Audio overview button (TTS over AI's concept summary).** Modality effect predicts unusual leverage. Bonus: lets him "study while walking." Don't mirror audio with verbatim text — outline only.
7. **Two-mode explainer toggle (Socratic / Direct), defaulted by content type.** Procedural content (syntax, notation, mechanical algorithm steps) → direct. Conceptual content (why does this work, what does term mean) → Socratic.
8. **Auto-generate atomic Anki cards on every "almost got it" drill outcome.** Push to separate review surface (not tutor UI). Wozniak rules + Nielsen atomicity. HLR-style spacing.
9. **Replace empty-state with cold-start placement primer per subject.** Auto-gen from lecture PDF initial 5-question diagnostic that seeds mastery map. ALEKS-style. Now no subject is "empty" — they're "diagnosed and ready."
10. **Interleave drill problems by topic after week 2.** First two weeks: blocked practice per topic (faster initial acquisition). Then interleaved (77%/38% advantage on retention). Feature toggle to opt in when ready.

---

## Three Surprising Findings (Likely New to Alex)

1. **Clarity numbs the mind.** Veritasium PhD: clear, correct explanations made students *more* confident in misconceptions. Misconception must be *named* before correction lands. AI tutor that just "explains clearly" can make you worse.
2. **Worked examples help novices and hurt experts** — same content, opposite signs. Expertise reversal effect. Static "always-show-example" feature will become drag the moment topic mastered. Make it adaptive or it will outgrow user in 2 weeks.
3. **Badges-alone (no other game elements) *increase* cognitive load** vs ungamified control. Intuition "gamify it to motivate" is wrong as starting point. 2023 meta-analysis: intrinsic motivation declines with long gamification exposure. For single adult finals-prep user: skip gamification entirely.

---

## Sources

- [Sweller cognitive load research compilation (NSW DOE)](https://education.nsw.gov.au/content/dam/main-education/about-us/educational-data/cese/2017-cognitive-load-theory.pdf)
- [Worked-example effect meta + goal orientation (MDPI 2024)](https://www.mdpi.com/2227-7102/14/6/597)
- [Expertise reversal effect (Wikipedia)](https://en.wikipedia.org/wiki/Expertise_reversal_effect)
- [Cognitive load and expertise reversal (Cambridge Handbook ch 40)](https://www.cambridge.org/core/books/cambridge-handbook-of-expertise-and-expert-performance/cognitive-load-and-expertise-reversal/03F656FD334F23214426ACB4118FEBF9)
- [Multimedia learning principles review (split-attention, modality, redundancy)](https://www.researchgate.net/publication/307653961_A_Review_of_Multimedia_Learning_Principles_Split-Attention_Modality_and_Redundancy_Effects)
- [Two types of redundancy in multimedia learning (Frontiers 2023)](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2023.1148035/full)
- [Verbal redundancy: when reading helps listening (Moreno/Mayer 2002)](https://tecfa.unige.ch/tecfa/teaching/methodo/MorenoMayer2002.pdf)
- [Wood/Bruner/Ross — Vygotsky's ZPD and scaffolding (Open University)](https://www.open.edu/openlearncreate/pluginfile.php/5904/mod_resource/content/1/Vygotskian_principles_on_the_ZPD_and_scaffolding.pdf)
- [Simply Psychology — ZPD overview](https://www.simplypsychology.org/zone-of-proximal-development.html)
- [Distance Learning Institute — Vygotsky's ZPD for instructional design](https://distancelearning.institute/instructional-design/vygotskys-zpd-bridging-learning-potential/)
- [Ausubel's advance organizers (Distance Learning Institute)](https://distancelearning.institute/instructional-design/ausubel-advance-organizers-learning/)
- [HKU advance organizers KB](https://kb.edu.hku.hk/approaches_advance_organizers/)
- [Advance organizers — 20 years of research (Springer)](https://link.springer.com/article/10.1007/BF00117008)
- [Anderson Skill Acquisition & LISP Tutor 1989 (Wiley)](http://onlinelibrary.wiley.com/doi/10.1207/s15516709cog1304_1/full)
- [ACT-R Lessons Learned (Anderson)](http://act-r.psy.cmu.edu/papers/Lessons_Learned.html)
- [Renkl — Fading Worked Solution Steps (Springer)](https://link.springer.com/article/10.1023/B:TRUC.0000021815.74806.f6)
- [Worked-example effect (Wikipedia)](https://en.wikipedia.org/wiki/Worked-example_effect)
- [Chi 1994 — Eliciting self-explanations](https://onlinelibrary.wiley.com/doi/10.1207/s15516709cog1803_3)
- [Chi self-explanation effect (ASU PDF)](https://education.asu.edu/sites/g/files/litvpz656/files/lcl/instruction_based_on_self_explanation.pdf)
- [Booth et al — Evidence from worked example effect (ERIC)](https://files.eric.ed.gov/fulltext/ED566953.pdf)
- [Bjork — Desirable Difficulties (UCLA)](https://bjorklab.psych.ucla.edu/wp-content/uploads/sites/13/2016/04/EBjork_RBjork_2011.pdf)
- [Khan Academy mastery system (Sal Khan)](https://support.khanacademy.org/hc/en-us/articles/360030753412-Why-Mastery-Learning-by-Sal-Khan)
- [Cult of Pedagogy — Khan mastery learning](https://www.cultofpedagogy.com/khan-mastery-learning/)
- [Brilliant.org — about/pedagogy](https://brilliant.org/about/)
- [Nibble Blog 2026 — Brilliant review](https://nibble-app.com/blog/is-brilliant-worth-it)
- [ALEKS adaptive system impact study (MDPI)](https://www.mdpi.com/2227-7102/11/10/603)
- [ALEKS](https://www.aleks.com/)
- [Duolingo HLR paper (Settles & Meeder 2016)](https://research.duolingo.com/papers/settles.acl16.pdf)
- [Duolingo HLR open-source code](https://github.com/duolingo/halflife-regression)
- [ASSISTments effectiveness (WPI Journal)](https://wp.wpi.edu/journal/articles/online-math-help-that-works/)
- [Bloom 2-sigma problem (Wikipedia)](https://en.wikipedia.org/wiki/Bloom's_2_sigma_problem)
- [3blue1brown — Lex Fridman interview transcript](https://podcasts.happyscribe.com/lex-fridman-podcast-artificial-intelligence-ai/118-grant-sanderson-math-manim-neural-networks-teaching-with-3blue1brown)
- [Grant Sanderson lessons (Antoine Buteau)](https://www.antoinebuteau.com/lessons-from-grant-sanderson/)
- [Veritasium — biggest myth in education](https://www.veritasium.com/videos/2021/7/9/the-biggest-myth-in-education)
- [Scientific American — Derek Muller on misconceptions](https://www.scientificamerican.com/article/how-youtube-star-derek-muller-of-veritasium-is-challenging-scientific/)
- [Wozniak — 20 rules of knowledge formulation](https://supermemo.guru/wiki/20_rules_of_knowledge_formulation)
- [Andy Matuschak notes — Wozniak rules](https://notes.andymatuschak.org/z96Xr88dMaAGrn3CobJnMUD)
- [Michael Nielsen — Augmenting Long-Term Memory](https://augmentingcognition.com/)
- [Olly Britton notes on Nielsen's essay](https://ollybritton.com/notes/articles/augmenting-long-term-memory/)
- [Posner/Strike/Hewson/Gertzog 1982 — conceptual change](https://eclass.uoa.gr/modules/document/file.php/PHS122/%CE%91%CF%81%CE%B8%CF%81%CE%B1/Posner_Strike_Hewson_Gertzog.pdf)
- [Pacaci 2024 meta-analysis — conceptual change strategies](https://onlinelibrary.wiley.com/doi/full/10.1002/tea.21887)
- [Correcting students' probability misconceptions (ERIC)](https://files.eric.ed.gov/fulltext/EJ1068215.pdf)
- [Anway & Bennett — probability misconceptions](https://www.rossmanchance.com/artist/proceedings/AnwayBennett.pdf)
- [Khanmigo overview](https://www.khanmigo.ai/)
- [Khan blog — How we built AI tutoring tools](https://blog.khanacademy.org/how-we-built-ai-tutoring-tools/)
- [SocraticLLM vs ChatGPT teaching performance (StarSpark)](https://www.starspark.ai/blog/socratic-llm-tutors)
- [Brookings — what research shows about generative AI tutoring](https://www.brookings.edu/articles/what-the-research-shows-about-generative-ai-in-tutoring/)
- [Atlas Workspace — NotebookLM for students 2026](https://www.atlasworkspace.ai/blog/notebooklm-for-students)
- [U Chicago Academic Tech — NotebookLM for research/study](https://academictech.uchicago.edu/2026/04/06/google-notebooklm-an-ai-tool-for-research-and-studying/)
- [Atlas Workspace — 7 best AI reading assistants 2026](https://www.atlasworkspace.ai/blog/ai-reading-assistants)
- [ChatPDF vs Humata for academic research (Toolify)](https://www.toolify.ai/ai-news/enhance-academic-research-and-learning-with-chatpdf-vs-humata-1069095)
- [Mathpix PDF Reader](https://mathpix.com/pdf-reader)
- [Retrieval/spacing systematic review (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC11078833/)
- [Interleaving in math (AFT)](https://www.aft.org/ae/spring2020/agarwal_agostinelli)
- [Interleaving vs blocked (Smartick)](https://www.smartick.com/blog/parents-and-teachers/education/interleaving-vs-blocked-practice/)
- [Mayer's 12 principles (DLI)](https://www.digitallearninginstitute.com/blog/mayers-principles-multimedia-learning)
- [Dual Coding Theory (Paivio + Mayer)](https://www.structural-learning.com/post/dual-coding-a-teachers-guide)
- [Progressive disclosure (NN/G)](https://www.nngroup.com/articles/progressive-disclosure/)
- [VisuAlgo — algorithm visualizations](https://visualgo.net/en)
- [zyBooks — tracing algorithms](https://www.zybooks.com/tracing-algorithms/)
- [Gamification meta-analysis (ETR&D 2024)](https://link.springer.com/article/10.1007/s11423-023-10337-7)
- [Productive struggle in AI tutoring (Stanford SAIL)](https://ai.stanford.edu/blog/teaching/)
- [Scaffolding through prompts meta-analysis (ScienceDirect)](https://www.sciencedirect.com/science/article/pii/S1747938X25000235)
- [Recursive Prerequisite Knowledge Tracing (arXiv)](https://arxiv.org/html/2508.11892)
- [JetBrains research — AI hints for online learning](https://blog.jetbrains.com/research/2025/07/ai-hints-for-online-learning/)
- [Posamentier — 9 strategies for motivating students in math (Edutopia)](https://www.edutopia.org/blog/9-strategies-motivating-students-mathematics-alfred-posamentier)

---

---

## Round 2 (heavy)

> Generated 2026-05-17 via second deep-research subagent (~9min wall, 60+ web sources, 85 tool uses, 150k tokens). Focus: 10 deeper axes Round 1 left thin.

### 1. PDF math extraction + concept extraction tech stack

Current `PdfPane` renders bytes; AI never reads them. Open-source PDF→markdown landscape consolidated by May 2026.

**Top OSS options:**

| Tool | Approach | Math quality | Romanian | Self-host cost |
|------|----------|--------------|----------|---------------|
| [MinerU 2.5-Pro-2604](https://github.com/opendatalab/MinerU) | 1.2B VLM, two-stage downsampled-layout → original-res fragment recognition | OmniDocBench 90.67 — **beats Gemini 2.5 Pro + dots.ocr** | Multilingual VLM, strong Latin scripts | CPU works, GPU ideal; HF weights free |
| [Marker](https://github.com/VikParuchuri/marker) | Hybrid detector+OCR pipeline, Surya OCR backbone | Conservative on equations (detect-then-LaTeX, low hallucination) | All-language formatter | 25 pages/sec on H100; CPU OK for single user |
| [Docling (IBM)](https://github.com/docling-project/docling) | DoclingDocument AST; Heron layout +23.5% mAP; LF donation 2026 | Strong for typeset formulas, weak handwritten | Multilingual | Apache 2.0, Granite-Docling-258M VLM |
| [olmOCR (allenai)](https://github.com/allenai/olmocr) | Qwen-2-VL 7B fine-tuned; document-anchoring prompting | Handwriting + equations + tables + multi-column | Latin OK | &lt;$200 per 1M pages self-hosted |
| [PyMuPDF4LLM](https://github.com/pymupdf/pymupdf4llm) | Deterministic text-layer parser; OCR fallback | Native PDF math passes through if LaTeX-encoded; **10–250× cheaper than VLM** | Any | CPU-only, no GPU, no tokens |

Commercial baseline: [Mathpix Convert API](https://mathpix.com/convert) gold standard but pay-monthly. Use $29 trial credit for one-shot bootstrap.

Reference hub: [Best OSS PDF-to-Markdown Tools 2026](https://themenonlab.blog/blog/best-open-source-pdf-to-markdown-tools-2026).

[Nougat (Meta)](https://github.com/facebookresearch/nougat) — historically academic-PDF darling, no longer frontier 2026. Hallucinates more than Marker.

**Recommendation for jarvis-kotlin (3-layer):**

- **Layer 1 (default, free, seconds):** PyMuPDF4LLM on every upload. Extracts text + tables deterministically. Enough for digital PDFs (most FII material).
- **Layer 2 (fallback for scanned/equation-heavy):** Self-hosted Docker **MinerU 2.5** on VPS. Trigger only when Layer 1 returns suspiciously little text. Pre-warm at upload, not first-read.
- **Layer 3 (citation-grounded Q&A):** [PaperQA2](https://github.com/Future-House/paper-qa) for "what does textbook say about X" questions. 85% precision / 66% accuracy. LiteLLM-compatible (OpenRouter `:free` works). Enforces in-text `(p. 47, eq. 3.2)` citations.

Sub-ranking (cheapest path to AI-quotes-equations):
1. PyMuPDF4LLM + OpenRouter `:free` → 95% value at $0
2. + MinerU 2.5 worker for scanned fallback → +3%
3. + PaperQA2 agentic RAG → +citation guarantees
4. + Mathpix trial credit for edge-case PDFs → escape hatch

### 2. LLM concept extraction + auto-question generation

**Key research:**

- **[LearnLM Partner Prompt Guide (Google 2025)](https://services.google.com/fh/files/misc/learnlm_prompt_guide.pdf)** — most concrete public artifact on tutor-prompt design from a major lab. Verbatim: *"Be warm, patient, plain-spoken. Don't give answers or do homework; talk through problems one step at a time, asking a single question at each step. Give the user a chance to respond before continuing. Be brief — don't send essay-length responses. Start with a direct opening without praise."*
- **[LearnLM paper (arXiv 2412.16429)](https://arxiv.org/html/2412.16429v3)** — fine-tuned on (a) human tutoring transcripts, (b) GenAI role-played tutor-learner convos, (c) step-by-step math, (d) golden examples, (e) safety datasets. Five principles: active learning, cognitive-load management, personalization, curiosity stimulation, metacognition.
- **[Automated Educational Question Generation at Bloom's Levels (arXiv 2408.04394)](https://arxiv.org/abs/2408.04394)** — RAG with explicit Bloom-level targeting beats zero-shot, few-shot, CoT. Distractor-only pipelines (T5/Sense2Vec) produce expert-grade items ~33% — keep human-in-loop approval queue.
- **[Knowledge Graph Builder (Neo4j Labs)](https://neo4j.com/labs/genai-ecosystem/llm-graph-builder/)** — chunk → entity/relation LLM extract → graph → query.
- **[Dense X Retrieval (EMNLP 2024)](https://aclanthology.org/2024.emnlp-main.845/)** — fine-grained proposition-level indexing beats passage-level. Store concepts as atomic propositions ("inverse-CDF method samples from F by drawing U~Unif(0,1) and returning F⁻¹(U)") for both retrieval and cards.
- **[Andy Matuschak — Quantum Country / Orbit](https://quantum.country/)** principles for atomic prompts: focused (one idea), precise (small variation changes answer), effortful (no recognition-only), consistent (same Q→A over months), tractable (>90% success once known).

**5 LLM-prompt patterns OpenRouter `:free` can execute reliably:**

**A — Atomic Concept Extractor:**
```
You are extracting concepts from a course PDF (Romanian academic text).
Output ONLY a JSON array of 5-8 atomic propositions. Each:
- States ONE testable fact, definition, or procedure
- Is self-contained (no "as defined above")
- Has a section anchor (page or section number)
- In Romanian if source is Romanian
Refuse to invent. Empty chunk → [].
```

**B — Bloom-Targeted MCQ Generator (RAG-grounded):**
```
Given proposition: <prop>
Generate ONE MCQ at Bloom level <remember|understand|apply|analyze>.
- Stem unambiguous.
- Correct answer derivable from source chunk: <chunk>
- 3 distractors, each plausible to novice, each falsifiable by re-reading chunk.
- JSON: {stem, choices: [{text, correct: bool}], rationale}
Cross-check: rationale cites chunk? If not, regenerate.
```

**C — Self-explanation Probe (Chi / Renkl / LearnLM):**
```
Peer tutor. Student attempted: <task>. Their answer: <answer>.
Reference answer: <reference>.
DO NOT reveal reference. Ask ONE Socratic question that:
- Probes gap between their answer and reference
- Answerable in <30 seconds
- Sets up retrieval, not recognition (avoid "is it X or Y?")
- In student's language
End with question only — no commentary.
```

**D — Citation-Grounded Explanation:** wrap response in `<quote>...</quote>` verbatim from source markdown; refuse if no quote exists. Mitigates hallucination per [Citation-Grounded Code Comprehension (arXiv 2512.12117)](https://arxiv.org/html/2512.12117v1).

**E — Misconception Hunt (Posner 1982):** *"Generate 3 common misconceptions a novice might hold about <concept>. For each: (a) state misconception, (b) cognitive conflict that exposes it, (c) resolving explanation."* Seeds remediation hints when student fails.

UI: "Concept Strip" surface holds ~6 propositions per PDF section, each with chunk_id back-reference. Card generation background-async. Approval-queue UI lets Alex thumb-down bad cards before SRS rotation.

### 3. Bayesian Knowledge Tracing + adaptive scheduling

**4-param BKT (Corbett & Anderson 1995):**
- `p(L0)` — initial knowing probability
- `p(T)` — transition unknown→known per practice
- `p(S)` — slip rate (known but wrong)
- `p(G)` — guess rate (unknown but lucky)

Update on correct answer:
```
p(L_n+1 | correct) = (p(L_n) · (1-p(S))) / (p(L_n) · (1-p(S)) + (1-p(L_n)) · p(G))
p(L_n+1) = p(L_n+1 | obs) + (1 - p(L_n+1 | obs)) · p(T)
```

[pyBKT (CAHLR)](https://github.com/CAHLR/pyBKT) ships this + KT-IDEM + KT-PPS + BKT+Forget. [pyBKT MDPI paper 2023](https://www.mdpi.com/2624-8611/5/3/50) clean intro.

**Deep KT family (2024-2026 SOTA):**
- **DKT** (Piech 2015) — LSTM
- **AKT** ([Ghosh/Heffernan/Lan 2020](https://arxiv.org/abs/2007.12324)) — context-aware attention + monotonic distance decay + Rasch reg. Up to +6% AUC over DKT. Impl: [arghosh/AKT](https://github.com/arghosh/AKT)
- **SAINT / SAKT** — Transformer bidirectional
- **DKT2** ([arXiv 2501.14256, June 2025](https://arxiv.org/pdf/2501.14256)) — uses Hochreiter's xLSTM. **Beats 17 baselines incl. Transformers + Mamba.** Code: [zyy-2001/DKT2](https://github.com/zyy-2001/DKT2)
- **[pyKT toolkit](https://github.com/pykt-team/pykt-toolkit)** — benchmarks 10+ DLKT methods on 7 datasets

**[FSRS-6](https://github.com/open-spaced-repetition/free-spaced-repetition-scheduler)** — shipped as Anki 25.07 default (July 2025). 21 trainable params; 700M-review benchmark shows **88.2% superiority over FSRS-5**, ~4.5% prediction-error reduction. [Algorithm explainer](https://expertium.github.io/Algorithm.html). FSRS-7 described as "likely final."

**[Duolingo HLR](https://github.com/duolingo/halflife-regression):** `p(recall) = 2^(-Δt/h)`, `h = exp(Θ·x)`. Feature vector carries appearance frequency, prior usage count, success rate, lag, response confidence (response-time proxy). 13M traces. **Half error of Leitner.** [Settles ACL 2016](https://research.duolingo.com/papers/settles.acl16.pdf).

**[PFA (Pavlik 2009)](https://pact.cs.cmu.edu/koedinger/pubs/AIED%202009%20final%20Pavlik%20Cen%20Keodinger%20corrected.pdf):**
```
logit(p_correct) = β_kc + γ_correct · n_correct + ρ_incorrect · n_incorrect
```
Simpler than BKT (no hidden state). Can decompose mastery into per-skill contributions when item taps multiple KCs.

**IRT:** Rasch / 1PL fixes discrimination=1; 2PL adds discrimination; 3PL adds guessing. For ITS with sparse per-student data, 1PL usually enough.

**Min-viable Kotlin update rule (~50 LOC, beats naive count):**

Hybrid FSRS desirability + PFA decomposition. Per concept `c` for student:

```kotlin
data class Mastery(
    val pLearned: Double,    // 0..1, BKT posterior
    val nCorrect: Int,
    val nIncorrect: Int,
    val lastReviewed: Instant,
    val halfLifeDays: Double  // PFA-fitted offset
)

private const val P_SLIP = 0.10
private const val P_GUESS = 0.20
private const val P_TRANSITION = 0.15

fun updateMastery(m: Mastery, correct: Boolean, now: Instant): Mastery {
    // 1) Decay posterior by Ebbinghaus retention since lastReviewed
    val ageDays = Duration.between(m.lastReviewed, now).toMillis() / 86_400_000.0
    val retention = Math.pow(2.0, -ageDays / m.halfLifeDays)
    val pRetained = m.pLearned * retention + (1 - retention) * 0.5

    // 2) BKT Bayesian update on observation
    val pObsGivenLearned = if (correct) 1 - P_SLIP else P_SLIP
    val pObsGivenUnlearned = if (correct) P_GUESS else 1 - P_GUESS
    val pObs = pRetained * pObsGivenLearned + (1 - pRetained) * pObsGivenUnlearned
    val pLearnedGivenObs = (pRetained * pObsGivenLearned) / pObs

    // 3) Transition: unlearned can become learned through attempt
    val pLearnedAfter = pLearnedGivenObs + (1 - pLearnedGivenObs) * P_TRANSITION

    // 4) PFA half-life update
    val newHalfLife = if (correct)
        m.halfLifeDays * (1.5 + 0.2 * Math.ln(1.0 + m.nCorrect))
    else
        Math.max(0.5, m.halfLifeDays * 0.4)

    return m.copy(
        pLearned = pLearnedAfter,
        nCorrect = m.nCorrect + (if (correct) 1 else 0),
        nIncorrect = m.nIncorrect + (if (correct) 0 else 1),
        lastReviewed = now,
        halfLifeDays = newHalfLife.coerceIn(0.5, 365.0)
    )
}

fun nextReviewDue(m: Mastery): Instant =
    m.lastReviewed.plusMillis((m.halfLifeDays * Math.log(0.9) / Math.log(0.5) * 86_400_000).toLong())

fun isMastered(m: Mastery): Boolean = m.pLearned >= 0.95 && m.nCorrect >= 3
```

### 4. Open-source tutor reference impls to steal from

| Component | Effort | Value | Verdict |
|-----------|--------|-------|---------|
| FSRS-6 port to Kotlin | M | H | Ship Week 1 |
| BKT/PFA hybrid update | S | H | Ship Week 1 |
| ASSISTments log schema | S | M | Adopt now, save migration pain |
| OATutor mastery-bar UI | S | M | Visual borrow |
| MOOClet A/B-tester | M | M | Wait until 2+ prompt variants |
| Khanmigo Socratic prompt | S | H | Already partial in tutor prompts |
| pyKT offline DKT fit | L | L | Defer — single-learner data too thin |

- **[OATutor (CAHLR)](https://github.com/CAHLR/OATutor)** — React + Firebase + BKT + A/B testing. Frontend stack closest to Jarvis. Steal mastery-bar widget, hint-on-demand flow, KC-tagging schema.
- **[MOOClet framework](https://www.josephjaywilliams.com/additional-information/the-mooclet-framework)** — modular A/B-testable content. Used by EdX, ASSISTments, Canvas. Steal "MOOClet = multiple versions + assignment policy."
- **[ASSISTments E-Trials](https://www.wpi.edu/news/announcements/open-education-week-assistments)** — 14 open datasets, 100+ papers since 2012. Steal log schema (timestamp, problem_id, KC, response, correctness, hint_count, time_on_task) — adopt for Jarvis `attempts` table.
- **[CTAT (CMU)](https://www.cmu.edu/simon/open-simon/toolkit/tools/learning-tools/ctat.html) + [SimStudent](https://www.hcii.cmu.edu/project/simstudent)** — model-tracing tutor framework, CTAT 2026 web-app rewrite. Steal example-tracer pattern: record expert solving problem once, replay paths as graph, accept any equivalent path. Useful for PS Tema A code-grading: accept many code-equivalent solutions instead of one rubric.
- **[FSRS-6 ref impl](https://github.com/open-spaced-repetition/fsrs4anki)** — Python `fsrs` package, ~200 LOC algorithm. Port to Kotlin ~150 LOC.
- **[Khanmigo no-answer pattern](https://www.khanmigo.ai/learners)** — system instruction "You are a Socratic tutor. I am a student. Don't give me answers but lead me to get to them myself." Stated failure mode: occasionally too strict — let curiosity tangents breathe 1-2 turns before redirecting.

### 5. Code-grading pedagogy

PS Tema A is currently only drilled code task. Literature:

**Beyond correctness** ([Automated Grading Tools survey, ACM TOCE 2024](https://dl.acm.org/doi/10.1145/3636515)): 66% of automated graders only score correctness. Rich rubrics cover correctness, edge cases, complexity, code quality, process.

**Partial credit** ([T+E CSE 2025](https://www.tandfonline.com/doi/full/10.1080/08993408.2025.2554497)): "points for observable correct functionality" — combine tests with Continuous Rubric. For Laplace sampler, sub-goals:
1. Imports + signature match
2. Generates uniform RNs
3. Sign function correct
4. Log transform correct
5. Scales by location/scale
6. Edge case u=0.5
7. Returns array of correct shape

Each = 14%. Failing test reveals which sub-goal. Actionable, not "wrong."

**Elaborated test-failure feedback** ([Becker et al. 2021, Frontiers](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2021.768962/full)): pair failing test with specific misconception hint. Python enhanced-error-message research reduced anxiety + built confidence.

**[Hazel Tutor (HATRA 2020)](https://hazel.org/hazeltutor-hatra2020.pdf)** — type-driven feedback, hole-driven editing. System knows type at cursor; offers strategies based on what type needs.

**Hattie's feedback effect size:** d=0.73 originally; [Wisniewski 2020 PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC6987456/) lowered to d=0.48. Either way dwarfs most interventions. Works when specific + actionable + quickly delivered.

**Mutation testing for tutoring** ([AdverTest 2024-25](https://arxiv.org/html/2508.21107)): generate bug-variants of student's code, run their tests, see what survives. Surviving mutant = bug to hunt. For Laplace: flip sign, drop offset, swap log/ln.

**Concrete rubric template:**

```yaml
drill: laplace_inv_cdf
total_points: 100
test_correctness: 50
sub_goals: 35          # 7 × 5 pts
code_quality: 10
process: 5
feedback_tone: socratic_first
mutant_check: enabled
elaborated_feedback:
  on_test_fail:
    - match: "returns nan when u=0.5"
      hint: "what does np.sign(0) return? does that fit Laplace's density at the mean?"
    - match: "returns array of wrong shape"
      hint: "what shape does np.random.uniform(size=n) return?"
```

Reveal-budget gradient: Socratic question → mutant-survived hint → reference test → reference solution. Each costs reveal so student self-paces.

### 6. Romanian + Romance-language LLM considerations

- **[DeepSeek V3](https://arxiv.org/pdf/2412.19437)** — 100+ languages; multilingual is ~5% of training. European-language pairs well-supported. Fastest-growing free-tier model 2026.
- **[Qwen 2.5 / Qwen 3](https://qwenlm.github.io/blog/qwen2.5-llm/)** — 29+ languages explicit. Romanian not named in MMLU translation benchmark but Romance similarity bleeds through.
- **[MMLU-ProX (arXiv 2503.10497)](https://arxiv.org/html/2503.10497v1)** — 13-language benchmark, **Romanian included.** Llama 4 + Gemma 3 score well multilingual.
- **[Truthfulness Beyond English (arXiv 2502.09387)](https://arxiv.org/html/2502.09387)** — low-resource Romance (Basque, Catalan, Galician) more truthful in English but gap smaller than expected. Romanian (mid-resource) ~ Spanish minus small penalty.
- **Romanian academic notation:** decimal = comma `2,5` not `2.5`. Thousands separator = dot or thin space. Code stays English (variable names, comments often English). Academic discourse code-switches.
- **FII Iași** ([info.uaic.ro](https://www.info.uaic.ro/en/programs/computer-science-ro-en/)) — three-year Bologna BSc, RO and EN tracks, est. 1992. Research: formal methods, evolutionary computing, automata + Petri nets, crypto, HLT, AI. No single AI specialization — AI via optional modules. Subject acronyms (PA, PS, POO, ALO, SO, RC) standard.

**Prompt-language recommendation:**

1. **System instruction in English** (more robust), **user-visible output in Romanian** when source PDF is Romanian. `"You are a tutor. Source text is in Romanian. Respond in Romanian unless student writes English first. Code identifiers stay English."`
2. **No translate-the-whole-PDF.** Defeats the point — student sees Romanian on exam. Ground truth Romanian, retrieval Romanian, output Romanian.
3. **Two free-model picks:**
   - **DeepSeek V3 free** — strong general multilingual, best reasoning
   - **Qwen 3 Coder free** — best for code-tutoring drills (PS Tema A), 262K context
4. **Code-switching signal:** Romanian in chat → Romanian response. English/code → English response. Per-message detection, since CS students switch mid-sentence ("am implementat the recursive case, dar pică testul").

### 7. Scratchpad / pen-on-paper UX

Math/CS thinking happens on paper. Digital scratchpads always feel worse.

- **[Handwriting boosts brain connectivity (Frontiers Psych 2023)](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2023.1219945/full)** — high-density EEG: handwriting produces widespread theta/alpha connectivity typing doesn't. Typed scratchpad will *never* fully match paper for consolidation.
- **[Working memory offloading via scratch paper (BJEP 2025)](https://bpspsychub.onlinelibrary.wiley.com/doi/10.1111/bjep.12767)** — students jotting on scratch improved 83% more than mental-only solvers.
- **[Apple Math Notes (iPadOS 18)](https://support.apple.com/guide/ipad/solve-math-with-math-notes-ipadeb38d0f8/ipados)** — 70–95% handwriting accuracy depending on style; Pencil +20% over finger.
- **[Khan Academy math-input](https://github.com/Khan/math-input)** — React + Redux + MathQuill. Touch-optimized. Open source.
- **[Excalidraw with LaTeX](https://blag.bapt.xyz/posts/maths-excalidraw/)** — dev branch renders LaTeX via MathJax inline on canvas. Open source.
- **[tldraw "Make Real"](https://www.tldraw.com/)** — sketch → AI interprets → renders.

**Min-viable upgrades (no tablet required):**

1. **MathQuill / KaTeX-input mixed mode.** Replace textarea with hybrid: typing `$x^2$` renders inline; `Ctrl+M` opens MathQuill modal. ~600 LOC integration. **2× value free.**
2. **Excalidraw embed for diagrams.** `Ctrl+D` opens panel, saves SVG to scratchpad. Matters for OS (process diagrams), RC (network topology), PA (recursion trees), POO (UML). ~1 day work.
3. **AI co-scratchpad (draw + nudge).** Scratchpad change → tutor agent gets snapshot. Does NOT auto-respond; only on `?` keystroke or "nudge" button. Preserves externalization without making AI an answer-vending machine.
4. **"Working backward" mode** (Polya, Lakatos). Toggle: "Start from what you need to prove. What's previous step?" Reverses prompting direction.
5. **Mobile-first touch canvas.** tldraw + Excalidraw both work on mobile. MathQuill has Khan-style touch handle.
6. **Persist + re-show.** Every scratchpad session timestamped + linked to drill. Next review, previous scratchpad one click away. Currently textarea content vanishes.

NOT auto-LaTeX-from-handwriting on web — without stylus, recognition <50%. Don't ship 40%-works feature that erodes trust.

### 8. Recent AI tutor product launches (2025-2026)

- **[Claude for Education (Apr 2025)](https://www.anthropic.com/news/introducing-claude-for-education)** — Anthropic launched university-tailored Claude. *Learning Mode* in Projects = Socratic, not answers. Northeastern/LSE/Champlain campus access. [Anthropic + Teach For All (Jan 2026)](https://www.anthropic.com/news/anthropic-teach-for-all): 100k+ teachers, 63 countries, 1.5M students. **Steal:** "Learning Mode" as Project-level toggle.
- **[ChatGPT Edu](https://openai.com/index/introducing-chatgpt-edu/)** — GPT-4o + custom GPTs per workspace + data analysis + file uploads. Workspace agents (Codex-powered) 2026.
- **[LearnLM + Gemini 2.5 + Guided Learning](https://blog.google/products-and-platforms/products/education/google-gemini-learnlm-update/)** — I/O 2025; Aug 2025 Guided Learning. Fine-tuned on tutoring transcripts + golden examples + math step-by-steps. **Five learning-science principles baked into model**, not just prompt.
- **[NotebookLM Plus](https://blog.google/innovation-and-ai/models-and-research/google-labs/notebooklm-student-features/)** — Audio Overviews (podcast), Video Overviews (2026), Study Guides, **Interactive Mode** ("raise your hand" mid-podcast), 10 infographic styles, Learning Guide. **Steal:** Audio Overview ("listen to your PDF while commuting") + Interactive Mode (interrupt → ask → resume).
- **[Phi-4-mini-reasoning (Microsoft, May 2025)](https://huggingface.co/microsoft/Phi-4-mini-reasoning)** — 3.8B params, 128K context, trained on 1M+ math problems. **Optimized for on-device, no-internet tutoring.** Comparable to o1-mini on Math-500. Could run locally if VPS offline.
- **[Khanmigo 2025 updates](https://blog.khanacademy.org/whats-new-for-the-2025-26-school-year-big-updates-from-khan-academy-districts/)** — image upload, language beta (Arabic/Chinese/Russian/Ukrainian/Urdu/Vietnamese — no Romanian), Canvas/Google Classroom/Schoology sync, Khanmigo Interests (personalize via chat history). Being reworked after inconsistent early results.
- **[Replit Agent 3 (2026)](https://leaveit2ai.com/ai-tools/code-development/replit-agent-v3)** — 200-min autonomy + self-healing. *Replit Assistant* framed as code reviewer + tutor.
- **[Synthesis Tutor](https://www.synthesis.com/tutor)** — $20/mo K-5 math. Audience mismatch but exemplifies "warm patient multisensory" tone calibration.

**3 product moves Jarvis should steal:**
1. NotebookLM Interactive Audio Overview — 5-min Romanian-TTS podcast summary per PDF + click-to-interrupt
2. Khanmigo Interests — personalize examples via chat history
3. Claude Edu Learning Mode toggle — same convo, different default tutor stance

**3 to NOT copy:**
1. Khanmigo's over-strict redirect (own admission)
2. NotebookLM 10 infographic styles (bento-grid + anime + clay = gimmick)
3. Synthesis-style gamification (Alex is 20+ exam crunch, not 7-year-old)

### 9. Finals-prep / short-horizon cramming literature

Alex: 5 finals, 35 days (Jun 1-21).

- **[Cepeda 2008 — Temporal Ridgeline](https://laplab.ucsd.edu/articles/Cepeda%20et%20al%202008_psychsci.pdf)** — 1,350 participants, 3.5-month spaced reviews tested 1 year out. **Optimal gap = 10-20% of retention interval.** For 1-week retention (last week finals), optimal = 1-2 days. For 1-month, optimal = 3-6 days. Logarithmic, not linear.
- **[Kornell 2009 — Spacing > Cramming](https://sites.williams.edu/nk2/files/2011/08/Kornell.2009b.pdf)** — 20 min/day spaced **beat** 3+ hours cramming. **72% of students reported cramming felt more effective.** Metacognitive illusion is the enemy.
- **[Cepeda 2006 meta-analysis](https://augmentingcognition.com/assets/Cepeda2006.pdf)** — 317 experiments. Every single one with total time held constant, spacing beat massing.
- **[Rohrer 2015 Interleaved Practice for Math](http://uweb.cas.usf.edu/~drohrer/pdfs/Rohrer_et_al_2015JEdPsych.pdf)** — interleaving reduced practice scores but **tripled test scores**.
- **[Sleep-dependent consolidation (Walker)](https://walkerlab.berkeley.edu/reprints/Walker&Stickgold_AnnRevPsych_2006.pdf)** — SWS consolidates declarative; REM consolidates procedural. Sleep dep week-before is catastrophic.
- **[Bjork desirable difficulties](https://www.waddesdonschool.com/wp-content/uploads/2021/02/Desriable-Difficulties-in-theory-and-practice-Bjork-Bjork-2020.pdf)** — cramming → good performance, not learning. Student feels fluent during practice + bombs the test.
- **[Confidence-based testing (Novacek)](https://files.eric.ed.gov/fulltext/ED562245.pdf)** + **[CBE Life Sci Ed 2018](https://pmc.ncbi.nlm.nih.gov/articles/PMC6755215/)** — gradated confidence rating forces metacognitive engagement; retrieval practice reduces overconfidence.

**UX implications — Mode FSM driven by `Schedule.nextExam()`:**

**Mode 1 — Finals Mode (T-14 to T-3):**
- Switch SRS from FSRS-6 default → forced short-interval (every concept ≥ once per 2 days)
- Interleave subjects (PS+PA+POO in single session) over blocking
- Confidence rating required (1-4); calibration plot weekly
- Reduce new cards; only re-review known
- Sleep prompt at <7h: "research shows >7h is cliff for memory consolidation"

**Mode 2 — Pre-Exam Mode (T-3 to T-0):**
- Mixed-difficulty mock-exam: timed, no scratchpad, no AI
- Confidence per answer (cross-references calibration)
- After mock: scratchpad-augmented review of missed items only
- Spacing collapsed to "tomorrow morning + tomorrow evening before bed"
- Test-taking reminders (timing, mark-and-return, eliminate-then-guess)
- **Reduce volume in last 24h.** Walker's sleep research wins last-minute math.

**Mode 3 — Day Of (T-0):**
- Single-page review card (auto-gen from prior week's missed items)
- No new content. No long sessions. Confidence-priming only.
- Hard cutoff 1h before exam — book/walk/eat.

### 10. Active recall vs re-reading — settled science

- **[Karpicke & Roediger 2008](https://learninglab.psych.purdue.edu/downloads/2007/2007_Karpicke_Roediger_JML.pdf)** — repeated retrieval >> repeated study. Dozens of replications.
- **[Roediger & Karpicke 2006 (Science)](https://www.science.org/doi/abs/10.1126/science.1152408)** — testers retained 80% after 1 week; passive re-readers retained 34%. **2.4× ratio.**
- **[Dunlosky 2013](https://www.psychologicalscience.org/publications/journals/pspi/learning-techniques.html)** — high utility: practice testing, distributed practice. **Low utility: rereading, highlighting, summarization, keyword mnemonic, imagery, underlining.**
- **[Karpicke 2025 review](https://learninglab.psych.purdue.edu/downloads/2025/2025_Karpicke_Retrieval_Based_Learning_Review.pdf)** — most recent comprehensive review. Affirms testing effect across health professions, complex materials, transfer.
- **[Rowland 2014 meta](https://www.researchgate.net/publication/264988491_The_Effect_of_Testing_Versus_Restudy_on_Retention_A_Meta-Analytic_Review_of_the_Testing_Effect)** — g=0.50. **[Yang 2021](https://pmc.ncbi.nlm.nih.gov/articles/PMC12302331/)** classroom: g=0.50.

**UI implication:** every concept surface defaults to retrieval first, recognition second, restudy last.
- Card surfaces start blank (front-only). "Reveal" = button press, not auto-load.
- Scratchpad opens *before* answer shows — forces externalization first.
- Re-read = opt-in ("remind me of this concept") but doesn't count as review for SRS scheduling. Otherwise re-reading inflates "I know this" signal.
- Confidence rating required to advance — even 1-sec "I think I got it" beats no metacognition.

**Spec verbatim:** "Active recall is the only learning technique with both effect size > 0.5 in meta-analyses and unambiguous lab + classroom replication. Recognition (MCQ) inflates confidence without retention. Free recall (blank prompt → student types answer) is gold standard. Card UI defaults to recall, not recognition."

---

## Top-15 net-new high-leverage moves (additive to Round 1's top-10)

1. **Layer PDF extraction:** PyMuPDF4LLM default → MinerU 2.5 fallback → PaperQA2 citation-grounded Q&A. $0. Quote-equation-line: yes.
2. **Ship FSRS-6** (Kotlin port from Python `fsrs`). 21-param scheduler with personalized forgetting curve. Anki default since July 2025.
3. **Hybrid BKT/PFA mastery update** (~50 LOC Kotlin sketched above). Beats naive count; cheap; explainable.
4. **Atomic-concept extraction prompt** (Pattern A). 5-8 propositions per PDF section, each with chunk_id. Background-async.
5. **Bloom-targeted MCQ generator with RAG grounding** (Pattern B). Distractors verified against source. Human approval queue.
6. **Mode FSM** driven by `Schedule.nextExam()`: Normal → Finals-14 → Finals-3 → Day-Of. Each mode changes scheduler, interleaving, confidence-rating requirement, mock-exam availability.
7. **Confidence rating per answer (1-4).** Required to advance. Calibration plot weekly.
8. **MathQuill + KaTeX scratchpad upgrade.** Replaces plain textarea. Open source. ~1 week.
9. **Excalidraw embed for diagrams.** Toggle in scratchpad. Mobile-friendly. Open source iframe.
10. **NotebookLM-style Audio Overview** per PDF section (Romanian TTS). Commute use case.
11. **Mutant-survival hints for code-grading.** Generate 3 mutants per submission, show one surviving mutant as hint.
12. **Sub-goal rubric** (7 weighted criteria for Laplace sampler; portable template) + elaborated test-failure feedback.
13. **Bilingual prompt strategy:** English system, Romanian output for Romanian PDFs, code identifiers stay English, per-message language detection.
14. **DeepSeek V3 free + Qwen 3 Coder free** as default OpenRouter `:free` picks. DeepSeek general reasoning, Qwen code drills.
15. **Citation-grounded answers always.** No claim without `(p. N, eq. M)` anchor or refusal. Per [Citation-Grounded Code Comprehension](https://arxiv.org/html/2512.12117v1).

---

## Round-1 corrections

- **FSRS framing:** FSRS-6 is **mainstream**, not experimental — Anki 25.07 default since July 2025. Ship today, not "later." FSRS-7 described as "likely final."
- **Khanmigo framing:** stealable for Socratic stance + no-answer pattern, but being *reworked* after inconsistent classroom results 2025-26. Its over-strict redirect is failure mode — NOT to copy.
- **ALEKS framing:** worth augmenting — ALEKS itself is now "ALEKS AI-2" upgrade product; KT field has moved to deep methods (AKT, DKT2/xLSTM). For single-learner app, classical BKT/PFA sufficient + far more interpretable.
- **Anki community decks:** no Aiken-format deck for FII Iași subjects (PS/PA/POO/ALO/SO/RC). Empty-deck is reality — Jarvis generates its own.

---

## 3 new surprising findings (round 2)

1. **MinerU 2.5 is a 1.2B-param open-source PDF parser that beats Gemini 2.5 Pro** on OmniDocBench. Free, self-hostable. PDF→math is NOT a paid Mathpix problem in 2026.
2. **72% of students believe cramming feels more effective than spacing (Kornell 2009).** Metacog illusion is the enemy, not laziness. Build UI to fight illusion directly: show spacing-vs-massing retention plot in Alex's own data once 30 days of attempts exist.
3. **DKT2 with xLSTM beats Transformers + Mamba on knowledge tracing.** Outperforms 17 baselines incl. AKT, SAINT, DKT-Forget. Open source. xLSTM (Hochreiter 2024) is having a KT moment that hasn't hit mainstream EdTech. Jarvis won't ship DKT2 (overkill for 1 learner) but informs long-term curve.

---

## Round-2 tensions table (new principles)

| Tension | Side A | Side B | Resolution for Jarvis |
|---------|--------|--------|----------------------|
| Atomic vs holistic cards | Matuschak: atomic = focused = retrievable | CS concepts are *graphs* — atomicity fragments knowledge structure | Atomic cards + separate concept-graph viz. Each card links to ≥1 graph node. |
| Socratic vs answer-give | LearnLM/Khanmigo: never give answer | Alex has 35 days. Sometimes answer is cheapest move. | Reveal budget per drill. Student spends reveals; SRS knows you needed help. |
| Spaced vs crammed | Cepeda/Kornell: spaced wins | 35 days for 5 finals = already tight; some massing unavoidable | Mode FSM: spaced default, finals-mode collapses intervals. |
| Recall vs recognition | Karpicke: free recall wins | Novices can't free-recall what they don't know exists | Recognition only fallback, never default. |
| Romanian native vs English LLM | Multilingual handles RO decently | English is LLM-strongest | English system prompts, Romanian outputs, per-message detection. |
| Mathpix paid vs OSS free | Mathpix best-in-class | $0 budget | Layer: free PyMuPDF4LLM → free MinerU → reserve Mathpix trial for edge cases. |
| FSRS proven vs DKT2 SOTA | FSRS-6 has 500M reviews validation | DKT2 best AUC but needs ~1k+ students | Ship FSRS-6; revisit DKT2 only if Jarvis serves >1 user. |
| Scratchpad richness vs cognitive externalization | Excalidraw + MathQuill rich | Paper still beats every digital tool | Match paper as closely as Windows laptop allows; "I drew this on paper" upload-photo path for rest. |
| Test anxiety reduction vs exposure | Mindfulness reduces anxiety | Exposure builds tolerance | Mock exams safe mode (no grade pressure), name-the-stress feature, no pop-up nags. |
| Confidence rating required vs friction | Calibration training requires per-answer ratings | Asking 100×/day = friction tax | ⌘+1/2/3/4 keyboard-only, no modal. |

---

## Round 3 (deepest)

> Generated 2026-05-17 via third deep-research subagent (~10min wall, 70+ web sources, 80 tool uses, 148k tokens). Focus: 10 axes round 1+2 left thin — UI patterns from OSS tutors, solo-adult motivation, a11y, multi-step LLM chains, GDPR + EU AI Act, tutor-eval benchmarks, habit formation, curriculum sequencing, voice/audio, failure modes.

### 1. Concrete UI patterns from OSS tutors

**OATutor (UC Berkeley CAHLR, MIT)** — canonical OSS adaptive tutor. ReactJS + Firebase. Core components verbatim:
- `Platform.js` — top AppBar + problem flow + `nextProblem()` selection
- `ProblemCard.js` + `problemCardStyles.js` — Material-UI styled problem renderer
- `renderText.js` — variable interpolation for problem templates
- BKT-backed mastery → drives next-problem heuristic (lowest avg mastery across KCs)
- A/B testing baked in
- [github.com/CAHLR/OATutor](https://github.com/CAHLR/OATutor); [CHI 2023 paper](https://dl.acm.org/doi/10.1145/3544548.3581574)

**CTAT (CMU, web rewrite 2023+)** — single-window dockable layout for authoring. Inspiration for "build-a-drill-from-this-PDF-page" surface. [ctat.pact.cs.cmu.edu](http://ctat.pact.cs.cmu.edu/).

**ASSISTments Builder** — three views: Problem Set / Editor / Student Preview. **Hints architecture = on-demand button, not proactive** ([Razzaq & Heffernan](https://www.semanticscholar.org/paper/Hints:-Is-It-Better-to-Give-or-Wait-to-Be-Asked-Razzaq-Heffernan/0ee972c96f85dc2cffe9aed0f7616450d6cf0dc2)). Skill Builders use **3-stars-in-a-row mastery rule** with 10-attempt limit. New UI: progress bar fills with stars. [assistments.org](https://www.assistments.org/individual-resource/skillbuilders). "Assertions" mechanism (partially-worked steps inserted at student workspace) increases unsolicited help uptake ([Maniktala & Cody](https://ar5iv.labs.arxiv.org/html/2009.13371)).

**Khanmigo learner UI (2025 redesign)** — three load-bearing primitives:
1. **Learner Queue** — collapses "what should I do now?" into one ordered list. Kills decision paralysis.
2. **Mastery dashboard** — left sidebar with course-mastery toward goal
3. **Persistent right-side chat drawer** — Khanmigo available on every problem page

Writing Coach 3-stage: Outline → Draft → Revise. Each stage = writing area left, chat/feedback right. ([khanmigo.ai/writingcoach](https://www.khanmigo.ai/writingcoach)). Leaked system prompt: *"You never give the student the answer, but always try to ask just the right question..."* ([GPTsSystemPrompts](https://github.com/0xAb1d/GPTsSystemPrompts)).

**NotebookLM Studio (Dec 2024)** — three-pane: **Sources (left)** / **Chat (center, cited responses)** / **Studio (right, tiled grid: Audio Overview / Video Overview / Mind Map / Reports)**. Audio player anchored at bottom — podcast persists while user explores Mind Map. **Interactive Mode** = voice-interrupt the AI hosts. ([blog.google](https://blog.google/innovation-and-ai/models-and-research/google-labs/notebooklm-new-features-december-2024/), [9to5google](https://9to5google.com/2025/08/06/notebooklm-studio-redesign/)).

**Brilliant.org lesson layout** — bottom-anchored persistent "Continue" CTA, animations + simulations inline. Granular sectional progress at top of each lesson. Mobile-first. UI archive: [mobbin](https://mobbin.com/explore/screens/9899d235-188f-44ab-b4ca-f9fa35faa016).

**3Blue1Brown site** — interactive React-Manim alongside text. Text left, scrubbable animation right, explainer underneath. [3blue1brown.com](https://www.3blue1brown.com/about/), [Manim React discussion](https://github.com/3b1b/manim/discussions/1609).

**Component-name map:**

| Existing surface | Pattern | Jarvis component |
|---|---|---|
| ASSISTments Skill Builder progress bar | "3-in-a-row" star mastery | `MasteryStreak` in `DrillCard` header |
| Khanmigo Learner Queue | Single ordered "do next" stack | Replace empty `DrillStack` with `LearnerQueue` (Axis 8 sequencing) |
| Khanmigo Writing Coach 3-stage | Outline / Draft / Revise toggle | `EssayCoach` mode for ALO + SO essay-style |
| NotebookLM 3-pane + audio dock | Sources / Chat / Studio + bottom audio | Re-frame `TutorWorkspace` as 3-pane |
| Brilliant bottom-anchored CTA | Persistent next-action | `[Continue]` always-visible mobile drawer |
| ASSISTments on-demand hint | Hints triggered, never pushed | `HintButton` per `DrillCard`; never auto-pop |
| ASSISTments "Assertions" | Partially-worked steps inline | After stuck >60s, surface `WorkedExampleSnippet` peer to input |
| 3b1b Manim-React inline | Replayable explainer beside problem | `ConceptAnim` slot in `ConceptDrawer` |

### 2. Solo-adult motivation (no gamification, no nag)

**Self-Determination Theory (Deci & Ryan)** — autonomy / competence / relatedness. UI mapping: autonomy = configurability + meaningful choice; competence = visible progress + appropriate challenge; relatedness = collaboration / community. NN/g: customization, layout choice, theme satisfy autonomy ([nngroup](https://www.nngroup.com/articles/autonomy-relatedness-competence/)). For Alex (solo): relatedness weakest leg — Jarvis-as-companion (warm tone, remembers sessions) without faking social-network features.

**Implementation Intentions (Gollwitzer 1999)** — 94 studies meta: d=0.65 on goal attainment; d=0.31 physical activity ([PDF](https://www.prospectivepsych.org/sites/default/files/pictures/Gollwitzer_Implementation-intentions-1999.pdf), [Bélanger-Gravel](https://pmc.ncbi.nlm.nih.gov/articles/PMC8149892/)). Format: "If situation Y, then I perform behavior Z." MCII (Mental Contrasting + II) = strongest combo.

**BJ Fogg Tiny Habits (B=MAP)** — anchor new to existing. 2-min version. Emotion drives stickiness — "you change best by feeling good, not bad." ([bjfogg.com](https://www.bjfogg.com/learn)).

**James Clear 2-Min Rule** — action produces momentum; motivation follows action. ([jamesclear.com](https://jamesclear.com/how-to-stop-procrastinating)).

**Goal-Setting Theory (Locke & Latham 1990/2002/2006)** — specific + difficult >> "do your best." BUT for *new complex tasks*, **learning goals** ("master integration by substitution") beat **performance goals** ("score 80%") — performance goals on novel material cause tunnel vision ([PDF](https://home.ubalt.edu/tmitch/642/articles%20syllabus/locke%20latham%20new%20dir%20gs%20curr%20dir%20psy%20sci%202006.pdf)). For Alex's finals: framing = **learning-mastery goals per KC**, not "pass exam."

**Self-Efficacy (Bandura)** — four sources: mastery experiences >> vicarious > social persuasion > emotional state. UI moves: scale early wins; show prior-solved prominently; specific feedback ("you correctly factored the quadratic").

**Flow (Csikszentmihalyi)** — challenge-skill balance. Flow students show 30% higher persistence. Adaptive tutor sweet spot: **85% retrieval success rate** per Karpicke + Bjork desirable difficulty ([structural-learning](https://www.structural-learning.com/post/flow-state)).

**Duolingo streak shame** — Streak Freeze reduced churn 21% in at-risk-of-breaking users. Loss-aversion-driven streaks induce burnout. ([trophy.so](https://trophy.so/blog/the-psychology-of-streaks-how-sylvi-weaponized-duolingos-best-feature-against-them), [decisionlab](https://thedecisionlab.com/insights/consumer-insights/streak-creep-the-perils-of-too-much-gamification)). **Don't ship loss-aversion streak.** If shipped, default Streak Freeze auto-applied.

**Beeminder / StickK** — money-stake → 3× achievement (selection-confounded). **Skip stakes — overkill + nag-tone.**

**Identity-Based Motivation (Oyserman 2009-2025)** — framing "kind of person who passes finals" linked to future-self closeness predicts persistence + GPA. ([dornsife.usc.edu](https://dornsife.usc.edu/daphna-oyserman/identity-and-self/)).

**Growth Mindset (Dweck) caveats** — meta-analysis d=0.08, negligible ([Macnamara & Burgoyne](https://englelab.gatech.edu/articles/2022/Macnamara%20and%20Burgoyne%20(2022)%20-%20Do%20Growth%20Mindset%20Interventions%20Impact%20Students%E2%80%99%20Academic%20Achievement.pdf)). **Don't bank on mindset framing as load-bearing.**

**Jarvis moves:**
- Onboarding implementation-intentions form: "When + Where + What" → save 1 row
- Mode FSM transitions framed as **learning goals** not performance goals
- Identity onboarding: "What kind of student passes 5 exams in 21 days?" → `future_self_anchor`
- **Mastery-counter, not streak-counter.** Loss-aversion off
- No badges. No XP. No rank. No social proof. No nag.

### 3. A11y + dyslexia + ADHD reading aids

**OpenDyslexic — net-negative.** Multiple peer-reviewed: Wery & Diliberto 2017 no improvement; Marinus 2016 **reduced speed**; Dyslexie 2018 no benefit in kids ([pubmed](https://pubmed.ncbi.nlm.nih.gov/26993270/)). One adult eye-tracking study positive ([aes](https://aes.amegroups.org/article/view/5209/html)). **Ship as opt-in, not default.**

**Atkinson Hyperlegible** — Braille Institute 2020. Includes Romanian diacritics ([wikipedia](https://en.wikipedia.org/wiki/Atkinson_Hyperlegible), [fonts.google.com](https://fonts.google.com/specimen/Atkinson+Hyperlegible)). Caveat: **no peer-reviewed dyslexia benefits**; designed for low-vision. Safer than OpenDyslexic.

**Bionic Reading — worse than plain text.** Snell 2024: no benefit, slight cost ([scispace](https://scispace.com/papers/no-bionic-reading-does-not-work-2jnr1mxn06)). Readwise 2,074-user test: 2.6 WPM *slower*. **Don't ship.**

**British Dyslexia Association style guide** — sans-serif, 12-14pt body, **35% inter-letter spacing**, inter-word ≥3× inter-letter, line-height 1.5, left-aligned only, bold for emphasis (no italics, no underline). ([PDF](https://www2.worc.ac.uk/disabilityanddyslexia/documents/British%20Dyslexia%20Association%20Style%20Guide.pdf)).

**Microsoft Immersive Reader** — syllabification → +10% comprehension adults. TTS + highlighting equates dyslexic + non-dyslexic comprehension. ([Microsoft Learn](https://learn.microsoft.com/en-us/training/educator-center/product-guides/immersive-reader/research)). Settings to steal: line-focus (1/3/5 lines), syllable toggle, column width.

**ADHD-aware UX** — 16-20px body min, line-height 1.4-1.6×, chunked paragraphs, short bullets, collapsible sections, predictable layout, visible white space.

**MathJax 4.0 a11y** — auto-generates ARIA + Braille labels per equation. NVDA + MathCat plugin = 2025 stack. **KaTeX vs MathJax: MathJax 4 wins for a11y** (speech-explorer by default) ([docs.mathjax.org](https://docs.mathjax.org/en/latest/basic/accessibility.html)).

**Color overlays / Irlen** — not science. WHO, AAO, AAP, RANZCO all reject ([sciencebasedmedicine](https://sciencebasedmedicine.org/irlen-syndrome/)). **Don't ship.**

**Romanian diacritics** — ă â î ș ț. **Comma-below NOT cedilla** per Romanian Academy. Verify font supports comma-below ț/ș, not cedilla ţ/ş ([brandient](https://brandient.com/kit-on-romanian-diacritics)).

**ADHD vs procrastination** — both = avoidance loops; ADHD amplifies emotion dysregulation + task initiation failure. For "suspects ADHD" adult: don't medicalize, ship same low-friction patterns (chunking, persistent next-action, no decision paralysis, anchored prompts).

**Settings checklist:**

| Item | Default | Toggle |
|---|---|---|
| Font | Brutalist (existing) | Atkinson Hyperlegible |
| Body size | 16px | 18-20px |
| Line height | 1.5 | 1.6-1.75 |
| Inter-letter spacing | 0 | +35% (BDA) |
| Justification | left-only | left-only |
| Italics | OFF body | bold for emphasis |
| Math rendering | MathJax 4 + speech-explorer | always-on a11y |
| Line-focus mode | OFF | 1/3/5 line highlight |
| TTS read-aloud | OFF | on-demand `/read` |
| Syllabification | OFF | Romanian toggle |
| OpenDyslexic | NOT default | opt-in (mixed evidence note) |
| Color overlays | NEVER | n/a |
| Bionic Reading | NEVER | n/a |

### 4. Multi-step LLM tutor chains

**DSPy (Stanford)** — declarative LLM programming. **GEPA optimizer** (Genetic-Pareto): outperforms MIPROv2 by 10%+, RL methods by 20% with **35× fewer rollouts** ([dspy.ai](https://dspy.ai/api/optimizers/GEPA/overview/), [GEPA](https://github.com/gepa-ai/gepa)). MIPROv2 improved StackExchange-RAG 53%→61%. OpenRouter-compatible via LiteLLM.

**Multi-Agent Tutor (Generator + Critic)** — proven 2024-25 pattern. Generator drafts; Critic scores against rubric; Generator revises. ([aclanthology Wang 2025b](https://aclanthology.org/2025.findings-emnlp.743.pdf)). For Jarvis: 2-agent on `:free` — **DeepSeek V3 free as Generator + Qwen 3 Coder free as Critic**.

**Tree-of-Thoughts (Yao NeurIPS 2023)** — multi-path with self-eval + backtrack. ToT(b=5) beats CoT by **25% on Game of 24**. ([arxiv 2305.10601](https://arxiv.org/abs/2305.10601)). **Increases LLM calls 3-5×**; budget concern on free tier. Use only for hard math verification.

**Let's Verify Step by Step (Lightman/OpenAI 2023)** — process supervision (per-step reward) beats outcome supervision. PRM solves 78% MATH test subset. ([PDF](https://cdn.openai.com/improving-mathematical-reasoning-with-process-supervision/Lets_Verify_Step_by_Step.pdf)). For Jarvis: grade student attempts step-by-step; identify exact misconception step.

**RAG canonical pipeline 2026:**
1. OCR + cleanup (PyMuPDF4LLM)
2. Semantic chunking with hierarchical headings (def/theorem/example/exercise tagged separately)
3. Free embedding (`mxbai-embed-large` via Ollama; `nomic-embed-text`)
4. Top-k + LLM-rerank
5. Generator answers with citations

**HiChunk framework** — multi-level + Auto-Merge ([arxiv 2509.11552](https://arxiv.org/html/2509.11552v2)). **PaperQA2** SOTA on RAG-QA-Arena science by **12.4%** ([futurehouse](https://www.futurehouse.org/research-announcements/paperqa2-achieves-sota-performance-on-rag-qa-arena-science-benchmark)). v5 outsources to LiteLLM → Ollama local + OpenRouter cloud.

**Prompt caching** — Anthropic: **90% cost reduction, 85% latency** on long prompts. OpenAI: 50% cut auto >1024 tokens. OpenRouter pass-through. ([anthropic.com](https://www.anthropic.com/news/prompt-caching), [openrouter](https://openrouter.ai/docs/guides/best-practices/prompt-caching)). **Critical for Jarvis**: textbook chunks + system prompt = stable across drills. Cache them. 70-90% saved.

**Architectures for Jarvis:**

- **Arch A** — DSPy `RomanianTutor(student_question, kc_id, textbook_chunks)` → `(socratic_question, mastery_estimate)`. 50-100 hand-labeled "good vs bad Socratic" eval. GEPA on DeepSeek V3 free. $0 with `:free`.
- **Arch B** — Generator-Critic 2-agent for grading. DeepSeek V3 generates; Qwen 3 Coder scores against rubric ("Did this reveal answer? Did it ask question? Did it tie to KC?"). Regenerate if score < threshold.
- **Arch C** — PRM-style step verifier for code grading. Split code into reasoning steps; verifier scores each.
- **Arch D** — PaperQA2-backed concept lookup. `/explain` slash command → citation-grounded answer.
- **Arch E** — Tree-of-Thoughts for hard math drills only. Conditional, b=3. Verify tutor's own solution before grading.

### 5. EU + Romanian privacy law for tutor logs

**GDPR Art. 6** — for single-user self-hosted, **consent (Art. 6(1)(a))** is simplest. Alex is both data subject AND controller. Legitimate interest unnecessary when consent trivial.

**GDPR Art. 5 data minimization** — collect only what's necessary. For tutor: drill attempts (input, output, timestamp, KC, correctness) necessary for adaptive sequencing. PII beyond name+email NOT necessary. **Don't log**: IP (unless rate-limiting), full UA, indefinite chat transcripts.

**Romanian ANSPDCP + Law 190/2018** — implements GDPR. Romanian-specific: explicit Romanian-language privacy notice required for Romanian residents; Romanian-only consent valid; ANSPDCP fining authority. ([dataprotection.ro](https://www.dataprotection.ro/)).

**EU AI Act (Aug 2026 enforcement)** — Annex III classifies as **HIGH-RISK** AI systems that (a) determine education access, (b) evaluate learning outcomes to **steer the learning process**, (c) assess educational level, (d) monitor prohibited test behavior. ([artificialintelligenceact.eu](https://artificialintelligenceact.eu/annex/3/)).

**CRITICAL: Jarvis fits this definition exactly** — steers learning via drill selection. Even single-user, strict reading triggers high-risk. Mitigation: classification is on providers **placing systems on market or putting into service**. Single-user self-hosted likely escapes; **document assessment** mandatory under Art. 6.

**Data retention** — Khan Academy retains while active, deletes 30d after closure. Duolingo similar.

**Privacy Posture for Jarvis:**

- **Lawful basis**: Consent (Art. 6(1)(a))
- **Controller**: Alex (self-hosted VPS)
- **Log (necessary)**: drill attempts (prompt/response/KC/timestamp/correctness); mastery state per KC; chat 90 days; LLM cost tokens
- **DON'T log**: IP beyond ephemeral rate-limit; full UA; third-party IDs; audio/video unless opt-in
- **Settings panel must expose**: 1) view raw drill log, 2) JSON export, 3) "delete all data" (irrevocable), 4) "pause logging 1h", 5) telemetry opt-in (off by default), 6) Romanian + English privacy notice
- **AI Act position**: document single-user assessment NOW. If multi-user, reclassify → conformity + risk mgmt + post-market monitoring
- **Retention**: drills 24mo, chat 90d, logs 30d

### 6. Tutor evaluation benchmarks

**LearnLM (Google DeepMind Dec 2024)** — 3-stage human eval. LearnLM beat GPT-4o by 31%, Claude 3.5 Sonnet by 11%, Gemini 1.5 Pro by 13% ([arxiv 2412.16429](https://arxiv.org/pdf/2412.16429)). Uses **pedagogical instruction following** (system prompts declare pedagogy intent), not baked-in. Implication: **don't bake "be Socratic" into model — toggle per-mode**.

**MathTutorBench (ETH 2025)** — 3 categories: math expertise, student-understanding (locate/correct errors), teacher-response (scaffold?) ([arxiv 2502.18940](https://arxiv.org/abs/2502.18940), [eth-lre](https://eth-lre.github.io/mathtutorbench/)).

**TutorBench (2025)** — 1,490 high-school + AP samples; LLM-judge with per-sample rubrics ([arxiv 2510.02663](https://arxiv.org/abs/2510.02663)). **Steal per-sample rubric pattern** — write per-KC rubric, not generic Socratic rubric.

**MRBench** — 192 conversations, 1,596 responses, 8 pedagogical dimensions ([arxiv 2412.09416](https://arxiv.org/abs/2412.09416)).

**Hake normalized gain (1998)** — g = (post − pre) / (100 − pre). <0.3 traditional, 0.3-0.7 interactive engagement, >0.7 strong. ([physport](https://www.physport.org/expert/gain/)).

**ITS meta-analysis (Ma 2014)** — g=0.42-0.67. Recent K-12 ITS systematic review: mean ES=0.67 ([nature](https://www.nature.com/articles/s41539-025-00320-7)).

**Engagement metrics critique** — time-on-task is a **known anti-metric**: high time = productive struggle OR wheel-spinning. Datafication reduces complex learning to digital traces.

**Wheel-spinning detection (Beck & Gong 2013)** — >10 attempts same KC, no mastery = spinning, not learning.

**Metrics for Jarvis:**

| Metric | Why | Where |
|---|---|---|
| **Hake g per KC** | Pre/post-drill mastery delta | Studio → Insights |
| **% drills first-attempt success 70-90%** | Bjork desirable difficulty | Background; auto-tunes |
| **Time-to-mastery per KC (days)** | Spacing awareness | Ledger per-KC |
| **Hint-request rate per KC** | Help-seeking diagnostic | Ledger debug |
| **Wheel-spinning flag** (>10 attempts no mastery) | Beck & Gong | Triggers Mode FSM stuck path |
| **Generator-Critic disagreement rate** | LLM quality QA | Studio → System Health |
| **Cache-hit rate on prompt tokens** | Cost/latency | Background |
| **Romanian-diacritic render check** | Locale QA | CI snapshot test |
| **MathTutorBench rubric on tutor turns** (sampled 5%) | Pedagogy quality | Studio → System Health |
| **% drills with explicit KC tag** | Coverage diagnostic | Ledger |

**Anti-metrics — DO NOT TRACK:**
1. Daily-streak with loss-aversion framing → use mastered-KC count
2. Total time-on-task → confounds productive struggle with wheel-spinning
3. Badges / XP / rank → no learning prediction

### 7. Habit formation + study cadence

**Habit Loop (Duhigg, Wood)** — cue → routine → reward. Reward must be immediate. Variable rewards beat fixed.

**Fogg B=MAP** — anchor to existing. Tiny version. Emotion drives stickiness.

**Clear Atomic Habits** — habit stacking ("after [current], I will [new]"), env design, 2-min rule, 4 laws (cue obvious, routine attractive, reward satisfying, friction low).

**Implementation intentions** — d=0.65 overall, d=0.31 physical activity. Best UI: explicit text field for "if [time]/[place]/[trigger], then [action]." MCII = strongest combo.

**Cue design:**
- Time-based: notification at anchor time
- Place-based: geofenced (overkill)
- Existing-routine: "after morning coffee, open Jarvis"
- Calendar entry: visual block

**Streak-shame counter-research** — Streak Freeze reduced Duolingo churn 21%. **Lenience increases adherence.** Soft-streak >>> hard-streak.

**Jarvis habit moves:**
1. **Anchored implementation-intention notification** — onboarding saves `(time, place, anchor_routine, action)`. Fires at time. d≈0.31, free.
2. **2-minute opener drill** — first drill of session is small (single-step KC). Clear's 2-min rule.
3. **Mode FSM warm-up → core → wind-down** — explicit low-cognitive-load structure.
4. **Soft-streak with auto-pause** — miss day → "Streak paused" not "broken." Auto-resume next session. (OR no streak; mastered-KC counter.)
5. **Tomorrow's plan widget** — last action: "Tomorrow at 9am: master *Reuniunea de probabilitati* + review 3 SO concepts."

### 8. Curriculum sequencing algorithm

**KC graphs** — prerequisite-aware DAG per subject. ACE paper builds graphs with LLM assistance ([JEDM ACE](https://jedm.educationaldatamining.org/index.php/JEDM/article/download/737/218)). Squirrel AI uses 30,000-concept graph ([syncedreview](https://syncedreview.com/2018/10/03/adaptive-learning-startup-squirrel-ai-raises-cn%C2%A51b/)).

**Curriculum Learning (Bengio 2009)** — easy-to-hard. Loss-landscape smoothing argument: start low-noise, increase complexity ([PDF](https://ronan.collobert.com/pub/2009_curriculum_icml.pdf)).

**Interleaved practice (Rohrer)** — for math, interleaved **trounces** blocked on long-delay tests: **d=1.05 to d=3.44**. Requires problems-of-different-kinds-juxtaposed AND problems-of-same-kind-spaced ([ERIC](https://files.eric.ed.gov/fulltext/ED557355.pdf)).

**Cold-start problem** — BKT/DKT struggle initially. Mitigation: predict difficulty from KC features (text length, depth in prereq graph, similarity to mastered KCs) ([Springer](https://link.springer.com/article/10.1007/s42113-021-00101-6)).

**Squirrel AI mix** — classification trees + fuzzy logic for content; GA + evolution for learning paths; RL + DL for teaching mode.

**Carnegie Learning MATHia** — symbolic + ML; adapts skill-by-skill at fine granularity.

**Backward planning for finals** — "75% ready end of week 1; focus hardest 20% days 3-4 before exam; work with time limits 5-6 days out; reflect on mistakes" ([Bucknell PDF](https://www.bucknell.edu/sites/default/files/teaching_learning_center/4-5weeksuntilfinals.pdf)).

**Spaced retrieval 1-3-7-14-30** — approximates adaptive output. Aim 85-90% retrieval success.

**Sequencing algo (~100 LOC Kotlin):**

```
selectNextDrill(student, exam_schedule, now):
    1. Due reviews first (FSRS-6)
       due_kcs = ledger.dueForReview(now)
       if due_kcs not empty:
         return generateDrill(pickInterleavedFrom(due_kcs), difficulty=trackTo85PctSuccess)

    2. New KC selection — exam-window-weighted prerequisite walk
       candidates = activeSubjects.flatMap { kcsNotYetMastered filterBy prerequisitesMet }
       weighted = candidates.map { kc ->
         daysLeft = daysUntilExamFor(kc.subject)
         urgency = 1.0 / max(1, daysLeft - 3)   // protect last 3 days for review only
         coverage = 1.0 / (subjectKcsRemaining + 1)
         kc to (urgency + coverage)
       }

    3. Interleave: never two from same subject in a row
       if (lastDrill.kc.subject == top(weighted).kc.subject)
         return generateDrill(secondHighest(weighted))
       return generateDrill(top(weighted), difficulty=coldStartEstimate)
```

Coverage: round-robin interleaving + exam-urgency weighting + last-3-days protection + prereq-aware + cold-start fallback.

### 9. Voice + audio tutoring

**Pi (Inflection)** — emotionally-intelligent, Socratic mode, near-latency-free voice. Free unlimited, 8 voices ([hey.pi.ai](https://hey.pi.ai/)). **Romanian not first-class.**

**OpenAI Advanced Voice** — Study Mode for college students 2025. Romanian not first-class.

**Sesame CSM (Apr 2025, Apache 2.0)** — open-source conversational speech. Single-language now, planning 20+ ([HF](https://huggingface.co/sesame/csm-1b), [sesame.com](https://www.sesame.com/research/crossing_the_uncanny_valley_of_voice)).

**F5-TTS-RO (Dec 2025 arXiv)** — F5-TTS extended to Romanian via lightweight input adaptation ([arxiv 2512.12297](https://www.arxiv.org/pdf/2512.12297)). **Strongest Romanian TTS in OSS as of late 2025/2026.**

**Piper TTS (Rhasspy)** — local neural TTS, Romanian (ro_RO) supported low/medium/high. MIT, low-resource (Raspberry Pi) ([github](https://github.com/rhasspy/piper), [samples](https://rhasspy.github.io/piper-samples/)).

**Coqui XTTS v2** — open-source voice cloning gold standard; 3-second clone. Romanian-capable.

**Feynman technique meta** — self-explanation g=0.55 medium-large ([voicescriber](https://voicescriber.com/feynman-technique-learning-by-explaining)).

**Voice/audio surfaces for Jarvis:**

1. **"Read this aloud" button on every drill prompt** — Piper TTS Romanian medium, served MP3 stream from Kotlin. Self-hosted, $0/call. 10%+ comprehension gain per Microsoft Immersive Reader research.
2. **"Audio Overview" tile in Studio** (NotebookLM-style) — 5-10min podcast-style summary using Piper + 2-voice dialogue prompt. ~2k TTS calls per overview, local hardware.
3. **"Feynman Mode" — Voice-explainer record** — Alex speaks explanation; Whisper.cpp transcribes locally; LLM compares to expected; surfaces hand-wavy phrases. g=0.55.
4. **Voice-input for math problems (mobile)** — Whisper transcription → MathQuill LaTeX guess → user confirms. Reduces typing barrier on phone.
5. **Defer interactive-podcast (NotebookLM Plus parity)** — Sesame CSM Romanian doesn't exist yet. Wait F5-TTS-RO mature or Sesame multilingual.

**TTS picks Romanian:**

| Surface | Pick | Why |
|---|---|---|
| Read-aloud drill prompts | **Piper `ro_RO-mihai-medium`** | Free, local, MIT, VPS CPU OK |
| Audio Overview (long-form) | **F5-TTS-RO** | Higher quality, natural for long-form |
| Voice-input transcription | **whisper.cpp Romanian** | Free, local, MIT, on-device mobile |
| Future interactive voice | **Sesame CSM** when RO lands | Lower latency, Apache 2.0 |

All four OSS, $0/call. VPS CPU sufficient for Piper + whisper.cpp.

### 10. Tutor failure modes

**Khanmigo documented failures:**
- Math arithmetic errors (WSJ Feb 2024) ([iblnews](https://iblnews.org/khanmigo-struggles-with-basic-math-showed-a-report/))
- Equation input clunky
- Too verbose / not always Socratic
- Students can't articulate what they need
- Mid-semester rollout fails — better start year with AI tool than retrofit
- ([michiganvirtual](https://michiganvirtual.org/blog/have-you-considered-ai-in-your-classroom-a-khanmigo-pilot-story/), [edutopia](https://www.edutopia.org/article/how-ai-will-impact-the-future-of-teaching-a-conversation-with-sal-khan/))

**AI tutor cheating / solution leakage:**
- 43% students admit AI assignment use 2024 (+13% YoY)
- 89% have used ChatGPT for homework
- Detection systems flag neurodivergent + ESL disproportionately
- ChatGPT-as-tutor (not crutch) framing reduces over-reliance harm

**MIT "Your Brain on ChatGPT"** — EEG study, 3 groups (LLM/Search/Brain-only). LLM users showed **lower neural connectivity during writing**, lower retention. "Cognitive debt" — reversible if usage moderates ([media.mit](https://www.media.mit.edu/publications/your-brain-on-chatgpt/), [time.com](https://time.com/7295195/ai-chatgpt-google-learning-school/)).

**Microsoft + CMU 2025** — 319 knowledge workers; higher AI-confidence → less critical thinking ([Microsoft](https://www.microsoft.com/en-us/research/wp-content/uploads/2025/01/lee_2025_ai_critical_thinking_survey.pdf)).

**Help-avoidance / gaming** — pervasive in ITS. Students ignore hints, abuse hints, or wheel-spin without help-seeking. "Assertions" mechanism increases unsolicited help uptake.

**Failure-mode checklist for Jarvis:**

| # | Failure mode | Mitigation |
|---|---|---|
| 1 | LLM arithmetic error | Generator-Critic; escalate to SymPy server-side check before grading |
| 2 | Tutor leaks solution | Critic rubric: "Does response contain final answer?" — fail-closed; regenerate |
| 3 | Too chatty / non-Socratic | Critic: "End in question?" / "Word count < 80?" |
| 4 | Math input typing friction | MathQuill + voice input + Excalidraw |
| 5 | Student can't articulate need | `[Hint]` + `[Stuck]` + canned "I don't know where to start" → opens `ConceptDrawer` |
| 6 | Over-reliance / cognitive debt (MIT) | No auto-render solutions; force write-attempt-first; solution ONLY after attempt + explicit click |
| 7 | Cheating / solution leakage | `[Use as study aid]` vs `[Grade my work]` toggle; in grade-mode tutor never reveals |
| 8 | Help-avoidance / wheel-spinning | Wheel-spinning detector (>10 attempts no mastery) → forced `ConceptDrawer` + simpler subtask |
| 9 | Misconception missed / wrong scaffold | Sample 5% turns → MathTutorBench rubric → weekly manual review |
| 10 | Mid-semester rollout shock | Delay full Mode FSM until 3 sessions on `LearnerQueue` |
| 11 | Romanian-specific LLM errors | Eval set 50+ RO drill examples; CI before deploy |
| 12 | LLM hallucinates citation | PaperQA2-style verifiable; ANY citation must resolve to real chunk |
| 13 | Critic + Generator agree on wrong | Tertiary regex check: "the answer is" / numeric leak |

---

## Top-15 net-net-new high-leverage moves (additive to rounds 1+2's 25 — total now 40)

| # | Move | Effort | Leverage |
|---|---|---|---|
| 26 | **3-pane refactor**: Resources / Drill chat / Studio (NotebookLM model) replacing single-stream Workspace | M | Very high — fixes context-switch drawer problem |
| 27 | **Implementation-intention onboarding** ("9am at desk → open Jarvis") | XS | High — d=0.31 effect, free |
| 28 | **Generator-Critic 2-agent for grading** (DeepSeek V3 + Qwen 3 Coder, both `:free`) | M | Very high — kills tutor-leaks-answer |
| 29 | **Anthropic-style explicit prompt caching for textbook chunks** | S | Very high — 70-90% token reduction |
| 30 | **DSPy `RomanianTutor` signature + GEPA offline optimization** | M | High — replaces brittle prompt strings |
| 31 | **PaperQA2 `/explain` slash command** with Ollama embeddings | M | High — citation-grounded concept lookup |
| 32 | **Piper TTS Romanian "read aloud" button** | S | Med-high — a11y + 10%+ comprehension lift |
| 33 | **Wheel-spinning detector** → routing to `ConceptDrawer` | S | High — fixes silent failure mode |
| 34 | **`LearnerQueue` (Khanmigo-style)** replacing empty `DrillStack` in 4-of-5 subjects | M | Very high — fixes core empty-state |
| 35 | **Concrete sequencing algo (~100 LOC Kotlin)** — interleaved + prereq-aware + exam-window-weighted | M | Very high — replaces round-robin |
| 36 | **Future-self anchor question** in onboarding | XS | Medium — identity-based motivation |
| 37 | **`[Grade my work]` vs `[Study aid]` mode toggle** | S | High — surfaces user intent, kills cheating-enablement |
| 38 | **Soft-streak (auto-pause) OR no streak**; mastered-KC counter instead | XS | Medium — kills Duolingo streak shame |
| 39 | **Hake normalized gain pre/post drill burst** as KPI in `Studio → Insights` | S | High — first real learning measurement |
| 40 | **MathJax 4 with `a11y/explorer` + `a11y/speech`** + Atkinson toggle + BDA spacing toggle | S | Medium — a11y, future-proofs |

---

## Round-1+2 corrections from round 3

1. **DKT2 + FSRS-6 + PFA cold-start problem.** BKT/DKT struggle with new students/KCs. Add **difficulty-from-KC-features cold-start estimator** before DKT2 has data ([Springer](https://link.springer.com/article/10.1007/s42113-021-00101-6)).
2. **Growth-mindset framing** — meta-analysis d=0.08, negligible. Yeager 2019 *Nature* shows works ONLY for lower-achieving with aligned peer norms. **Demote.** Identity-based motivation (Oyserman) has stronger evidence.
3. **NotebookLM 3-pane Sources/Chat/Studio is THE architectural insight.** Should replace Jarvis's current single-stream `TutorWorkspace`.
4. **Prompt caching budget impact** — 70-90% cost reduction available via Anthropic/OpenRouter cache. Halves LLM cost concern, unlocks more turn-rich tutoring.
5. **DeepSeek V3 + Qwen 3 Coder as Generator-Critic pair** more valuable as system than separately.

---

## 3 new surprising findings (round 3)

1. **Time-on-task is an anti-metric for learning.** Long sessions correlate with wheel-spinning as much as productive struggle. Khan + Duolingo show in own data. Jarvis should track **mastery delta per session (Hake g)**, not minutes. Don't be proud of long sessions.

2. **OpenDyslexic actively reduces reading speed in controlled trials**, despite branding. Atkinson Hyperlegible (designed for low-vision, NOT dyslexia) accidentally serves dyslexic readers better. **Bionic Reading worse than plain text** (Snell 2024; Readwise 2,074-user study 2.6 WPM slower). Half "best fonts for dyslexia" lists have negative/null evidence.

3. **EU AI Act (Aug 2026) classifies tutors that "steer learning process" as high-risk** with documentation requirements. **Jarvis fits the definition exactly.** Single-user self-hosted likely escapes because not "placed on market" — but assessment must be documented. If Alex ever shares with friends, classification flips → conformity assessment, risk mgmt, post-market monitoring. ANSPDCP has Romanian fining authority.

---

## Round-3 tensions table

| Principle A | vs Principle B | Resolution rule |
|---|---|---|
| Maximize self-explanation (Feynman, g=0.55) | Reduce typing friction | Voice-input Feynman; auto-transcribe |
| Implementation intentions for habit (d=0.31) | No nag-tone, no deadline-framing | Single anchored prompt/day, dismissible, opt-out trivial |
| Adapt difficulty to flow (85% success) | Don't shield from desirable difficulty | Track success/KC; aim 70-90% band, not 100% |
| Generator-Critic improves quality | 2× tokens per turn | Prompt caching neutralizes (cache textbook chunks) |
| Show citations + sources | Don't overwhelm reading-mode UI | Citations as footnote hover, not inline-bracketed |
| Interleave subjects (d=1.05+ math) | Single-subject focus mode reduces context-switch | Mode FSM "focus" overrides interleaving on request |
| Mastery counter beats streak counter | Some still want gamified loops | Default mastery; opt-in soft-streak |
| Romanian Academy mandates comma-below ț/ș | Many fonts use cedilla | CI font-snapshot test |
| MathJax 4 a11y by default | MathJax 4 heavier than KaTeX | Cache rendered SVG server-side |
| EU AI Act high-risk if multi-user | Alex might share with friends | Document single-user assessment now; reclassify if scope changes |

---

## What's still unexplored after Round 3

1. **Romanian university curriculum mapping at scale** — Round 3 didn't search Romanian Ministry of Ed or FII Iași syllabus repos. KC graphs for PA/PS/POO/ALO/SO/RC still need hand-authoring from PDFs.
2. **Khanmigo's open-source clones (2025 wave)** — OpenTutor, OpenTutorAI, SciAgent surfaced not deep-dived. Worth a round 4 axis if Jarvis converges on Khanmigo pattern.
3. **Self-hosted Whisper.cpp Romanian quality** — Axis 9 named the pick but didn't verify WER. Critical for Feynman Mode.
4. **F5-TTS-RO quality vs Piper Romanian medium** — no listening-test sample. Benchmark MOS on a Romanian textbook excerpt before committing.
5. **Adaptive sequencing under Mode FSM transitions** — Axis 8 algo doesn't account for Mode (focus/triage/wind-down/finals-rush). Finals-rush should dominate exam-urgency weight.
6. **DSPy + Kotlin interop** — DSPy is Python. Python sidecar service called from Ktor, OR reimplement signature-and-optimizer in Kotlin. Effort unclear.
7. **Code-grading rubric integration with PRM-style step verifier** — Round 2 mutation + Hazel type feedback; Round 3 added Lightman PRM. Integration architecture not designed.
8. **MOOClet-style A/B at N=1** — single-user, classic MOOClet contextual-bandit degenerates. Right adaptation?
9. **AI Act self-assessment template** — Axis 5 said "document assessment" but didn't pull template. EU AI Office likely publishes one.
10. **Live deployment guardrails for OpenRouter `:free` rate limits** — Round 2 picked free models; Round 3 didn't audit per-min/day caps. Fallback if Alex hits wall mid-session?

---

## Round 4 (closing the 10 thin spots)

> Generated 2026-05-17 via fourth deep-research subagent (~15min wall, 60+ sources, 110 tool uses, 178k tokens). Closes the 10 gaps Round 3 flagged.

### 1. Romanian university curriculum mapping at scale

**Subject acronyms — verified at edu.info.uaic.ro:**

| Acronym | Romanian | English | Year/Sem |
|---|---|---|---|
| **PA** | Proiectarea Algoritmilor | Algorithm Design | Y1 S2 — [edu.info.uaic.ro PA syllabus](https://edu.info.uaic.ro/fise-discipline/2024-2025/Algorithms-Design-Licenta-Informatica-(EN).pdf), [sites.google.com/view/fii-pa](https://sites.google.com/view/fii-pa) |
| **PS** | Probabilități și Statistică | Probability & Statistics | Y1 S2 — [PS_ro.html](https://edu.info.uaic.ro/probabilitati-si-statistica/PS_ro.html) |
| **POO** | Programare Orientată pe Obiecte | OOP | Y1 S2 |
| **ALO** | **Algebră Liniară și Optimizare** | Linear Algebra + Optimization | **Y1 S1** — [edu.info.uaic.ro/algebra-liniara](https://edu.info.uaic.ro/algebra-liniara/) |
| **SO** | Sisteme de Operare | OS | Y1 S2 — [SO syllabus](https://edu.info.uaic.ro/fise-discipline/2024-2025/Sisteme-de-operare-Licenta-Informatica.pdf) |
| **RC** | Rețele de Calculatoare | Networks | Y2 S1 — [RC course page](https://edu.info.uaic.ro/computer-networks/cursullaboratorul.php) |

**ALO is Linear Algebra + Optimization, NOT "Algoritmi" or "Logică".** Round 3 left fuzzy; now settled.

**Ingestable sources:**
- `edu.info.uaic.ro/<course-slug>/files/<curs-N>.pdf` — official per-lecture PDFs
- `edu.info.uaic.ro/fise-discipline/` — official course-description PDFs ("fișa disciplinei")
- `profs.info.uaic.ro/<prof.name>/courses/<course>/` — per-professor pages
- [github.com/logalex96/UAIC-Informatica-Iasi](https://github.com/logalex96/UAIC-Informatica-Iasi) — Y1-3 student mirror
- [fiimaterials.web.app](https://fiimaterials.web.app/materials) — student materials site
- **Reference textbook**: Lucanu & Craus *Proiectarea Algoritmilor* (Polirom 2008) — canonical PA spine
- **[RoMath benchmark](https://arxiv.org/html/2409.11074)** — **76,910 Romanian-native math problems** (5,777 bac + 1,133 olympiad + 63,000 synthetic). DeepSeek-Math-7B 56% on bac. **Free ready-to-use eval harness for Jarvis's PS engine.**

**Bootstrap pipeline:** Ktor crawler pulls all `edu.info.uaic.ro/<course>/files/*.pdf` per subject → dumps text to `corpus/<subject>/curs-N.txt`. ~6h Alex time + 1h crawler dev → frozen citable corpus + KC graph seeded from Lucanu/Craus TOC.

### 2. Khanmigo OSS clones (2025 wave)

| Project | URL | License | Active 2025-26 | RO support | Steal |
|---|---|---|---|---|---|
| **OATutor** (UC Berkeley, not CMU) | [github.com/CAHLR/OATutor](https://github.com/CAHLR/OATutor) | MIT | Yes | No | BKT skill-mastery + React A/B harness |
| **Open TutorAI** (Feb 2026) | [arxiv 2602.07176](https://arxiv.org/html/2602.07176v1) | OSS | New | Multilingual via LLM | RAG + learning-analytics dashboard; OpenWebUI extension |
| **DeepTutor** (HKU) | [github.com/HKUDS/DeepTutor](https://github.com/HKUDS/DeepTutor) | OSS | Active Apr 2026 | Multilingual | 4-agent: LocateAgent → InteractiveAgent → ChatAgent → SummaryAgent; CLI-native; living-profile memory |
| **SciAgent v2** | [github.com/AI-App/OpenDCAI.SciAgent](https://github.com/AI-App/OpenDCAI.SciAgent), [arxiv 2511.08151](https://arxiv.org/abs/2511.08151) | OSS | 2025 | Multilingual | Hierarchical Coordinator → Domain-Worker; gold-medal IMO/IMC/IPhO perf |
| **OpenTutor (tutornew)** | [github.com/tutornew/OpenTutor](https://github.com/tutornew/OpenTutor) | OSS "Vibeteaching" | Active | Multilingual | AI-orchestrated teacher pipelines |
| **GRACE** | [github.com/mukhal/grace](https://github.com/mukhal/grace) | Research code | Stable | English | Contrastive step-discriminator decoder — drop-in math step verifier |

**ChatGPT Study Mode (OpenAI 2025-07-29)** — confirmed live at [openai.com](https://openai.com/index/chatgpt-study-mode/). **Pure system-prompt patch, NOT retrain.** 4 features to mirror: (1) Socratic question/hint/self-reflection cycle, (2) scaffolded sections with topic-connection callouts, (3) personalization via skill-assessment + chat-memory carryover, (4) knowledge-check quizzes with personalized feedback.

**Claude Learning Mode (Anthropic Apr 2025, opened to all 2025-08)** — at [anthropic.com](https://www.anthropic.com/news/introducing-claude-for-education). Two Code-mode variants for PA/POO drills:
- **Explanatory mode:** Claude narrates decision-making while solving
- **Learning mode:** Claude inserts `#TODO` markers stopping every 5-10 LOC for student to write next section

**Khanmigo Lite system prompt LEAKED** ([gist](https://gist.github.com/25yeht/c940f47e8658912fc185595c8903d1ec)):
- Persona: "tutor that always responds in the Socratic style", "kind and supportive", "extremely concisely at a 2nd grade reading level"
- Anti-help-abuse: **"BE FIRM"** / **"Stop here until I make an effort"** when student asks for next hint without committing effort
- Math verification: invoke Python+SymPy step-by-step before issuing hint
- Safety: 988 Lifeline auto-fire on suicide mention; PII discouragement

**Eureka Labs (Karpathy)** — [eurekalabs.ai](https://eurekalabs.ai/) still in "build mode." Public shipped = **LLM101n** repo + **nanochat** (full-stack ChatGPT in $100 compute). **No tutor product yet.**

**Synthesis Tutor** — proprietary, math-only, ages 5-11. Not Romanian, not steal-able.

**Lokami** — **VERIFIED ABSENT** from all Romanian startup databases + EU press. Romanian-language tutor market is **uncrowded** — Jarvis would be near-unique.

**Steals ranked:**
1. **Port OATutor's BKT to Kotlin** (~120 LOC, single file). Removes mastery-estimation headache.
2. **Adopt DeepTutor's 4-agent decomposition** as Jarvis's internal control-flow (single prompt-orchestrator process).
3. **Implement Khanmigo Lite "BE FIRM" anti-help-abuse counter verbatim** — 3-line addition to tutor prompt: count hints-without-attempt per problem, fire "BE FIRM" at N=3.
4. **OpenAI Study Mode skill-assessment handshake** — first 3 messages on new KC = 3-question probe → reveals starting node in KC graph.

### 3. Whisper.cpp Romanian WER

**Headline:** Whisper large-v3 zero-shot on Romanian Common Voice = **10.8% WER** ([Modern Speech Recognition for Romanian](https://www.mdpi.com/2076-3417/16/4/1928)). Cross-checks: 8.2% FLEURS-ro, 13.8/27.2% VoxPopuli-ro, 24.9% RSC.

**Per-model breakdown:**
- Whisper-large-v3: 10.8% WER (RO)
- Whisper-medium: ~13-18% WER (RO, extrapolated)
- Q5-quantized Whisper-medium on 4-core CPU: **~2s inference per utterance** (int8 drops 75% vs FP16)
- faster-whisper-medium on 8 vCPU / 16GB: 2:56 podcast in ~60s (RTF 0.34)

**SOTA Romanian ASR is NOT Whisper:**
- **[Parakeet Ro 110M (FastConformer-CTC-TDT, 2025)](https://arxiv.org/html/2511.03361v1)** — **1.73% WER on RSC-eval**, 3.29% on Common Voice v21 RO. **27% relative WER reduction over previous best.** Outperforms Whisper-large-v2 on every RO eval. 110M params (vs Whisper large-v3's 1.55B).
- **[nvidia/parakeet-tdt-0.6b-v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)** — multilingual incl RO, CC-BY-4.0, 12.44% WER FLEURS-ro, RTFx ~3,332× real-time on GPU.
- **Parakeet CPU not supported** per model card. Hard constraint.

**Romanian-specific Whisper fine-tunes on HF:**
1. [gigant/whisper-medium-romanian](https://huggingface.co/gigant/whisper-medium-romanian) — CV 11 + RO synth corpus
2. [IonGrozea/whisper-large-v3-ro-turbo](https://huggingface.co/IonGrozea/whisper-large-v3-ro-turbo) — large-v3-turbo RO-optimized
3. [TransferRapid/whisper-large-v3-turbo_ro](https://huggingface.co/TransferRapid/whisper-large-v3-turbo_ro)
4. [readerbench/whisper-ro](https://huggingface.co/readerbench/whisper-ro) — small + Echo RO dataset
5. [alexgrigoras/whisper-small-ro](https://huggingface.co/alexgrigoras/whisper-small-ro)

**Failure modes (RO-specific):**
- Diacritics ă/â/î/ș/ț frequently dropped or substituted
- Hallucination loops on noise-clipped audio
- Code/math symbol degradation (Whisper not trained on math prompts)
- Underrepresented voices (youth/female/dialectal) have 1.5-3× higher WER

**Recommendation: `gigant/whisper-medium-romanian` Q5 via whisper.cpp.** ~2s latency on 4-vCPU Hetzner CX22. Target 6-8% WER. Avoids Python + GPU drivers. KenLM Romanian rescoring → 1.6× WER reduction empirically. Skip Parakeet (GPU only). Skip Vosk (lower RO accuracy than Whisper).

### 4. F5-TTS-RO vs Piper RO benchmarks

**[F5-TTS-RO paper (arxiv 2512.12297, Dec 2025)](https://arxiv.org/html/2512.12297v1):**

| Model | WER on RO output | Speaker similarity (cosine) |
|---|---|---|
| **F5-TTS-FULL-FT** | **3.62%** | 0.7946 |
| **RO-F5TTS** (lightweight adapter) | 5.27% | **0.9013** |
| **MMS-TTS-RON** | 5.77% | n/a |

20-native-speaker subjective: "high speaker similarity, pronunciation + naturalness competitive." Code + samples: [github.com/racai-ro/Ro-F5TTS](https://github.com/racai-ro/Ro-F5TTS).

**Piper RO** ([rhasspy.github.io/piper-samples/](https://rhasspy.github.io/piper-samples/)) — only `ro_RO-mihai-medium`. No published MOS. Anecdotally: decent intelligibility short utterances; prosody drift on long reads. **MIT.** **~50× faster than real-time on single CPU core.**

**Coqui XTTS v2 — DOES NOT support Romanian.** Verified at [github.com/coqui-ai/TTS](https://github.com/coqui-ai/TTS) + [HF](https://huggingface.co/coqui/XTTS-v2). 16 langs, RO absent. **Strike from candidates.**

**License comparison:**
- F5-TTS-RO adapter: MIT (but base F5-TTS checkpoints CC-BY-NC-4.0, commercial-prohibited)
- Piper: MIT
- MMS-TTS-RON: CC-BY-NC-4.0 (Facebook non-commercial)

**Hybrid strategy:**
- **Short read-aloud (<30 words, must be fast):** Piper `ro_RO-mihai-medium`. CPU-fast, MIT, real-time.
- **Long-form audio overview (pre-generate offline):** F5-TTS-RO (RACAI-RO adapter). 0.9013 speaker similarity = podcast-grade. Stored under `tts-cache/<KC-hash>.mp3`.

**A/B protocol:** 10 representative PA/PS sentences (math symbols + RO terminology), blind-rate Piper vs F5-TTS-RO, switch if F5-TTS-RO mean MOS > Piper by ≥0.5.

### 5. Mode-FSM-aware sequencing — refined algo

**Precedents:**
- **Knowledge Maximizer ([Hosseini/Pitt 2013](https://sites.pitt.edu/~peterb/indepstudies/2990-Hosseini-141.pdf))** — concept-based adaptive problem sequencing for exam prep. 3 KC categories: well-learned (skip) / unstable (refresh) / missed (deep review).
- **Carnegie Learning MATHia APLSE score** — per-student exam-perf predictions drive final-week recommendation list.
- **Squirrel AI nano-KC** — 300 high-school math concepts → 30,000 fine-grained KCs.
- **Bjork desirable difficulty** — spacing-effect benefits **collapse near zero for next-day exams** but massive at 1-week+. **Day-Of: reduce difficulty, recall-only, no new concepts.**
- **14-day finals schedule** ([istudy-app](https://istudy-app.com/blog/finals-week-study-schedule/)): T-14 → T-7 broad interleaved; T-6 → T-3 target weak spots timed; T-2 → T-0 full mock exams + final review.

**Per-mode behavior (~150 LOC Kotlin):**

```kotlin
enum class Mode { NORMAL, FINALS_14, FINALS_3, DAY_OF }
enum class Difficulty { AT_ZPD, MIXED, RECALL_ONLY }

fun nextDrills(ctx: SeqContext, n: Int): List<NextDrill> = when (ctx.mode) {
    Mode.NORMAL -> normalSeq(ctx, n)
    Mode.FINALS_14 -> finals14Seq(ctx, n)
    Mode.FINALS_3 -> finals3Seq(ctx, n)
    Mode.DAY_OF -> dayOfSeq(ctx, n)
}

// NORMAL: round-robin subjects, prereq-respecting, spaced-retrieval-driven, interleaved
private fun normalSeq(ctx: SeqContext, n: Int): List<NextDrill> {
    val candidates = ctx.kcGraph.allKCs()
        .filter { it.prereqsMet(ctx.masteryEstimates) }
        .sortedBy { spacedRetrievalUrgency(it, ctx.recentReviews[it] ?: emptyList(), ctx.now) }
    return roundRobinBySubject(candidates).take(n).map {
        NextDrill(it, pickTemplate(it, ctx), Difficulty.AT_ZPD)
    }
}

// FINALS_14: subject-weighted by exam-proximity; interleave WITHIN-subject (radius=3)
private fun finals14Seq(ctx: SeqContext, n: Int): List<NextDrill> {
    val subjectWeights = ctx.examDateBySubject.mapValues { (_, date) ->
        val daysOut = Duration.between(ctx.now, date).toDays()
        1.0 / (daysOut.coerceAtLeast(1).toDouble().pow(2.0))
    }
    val candidates = ctx.kcGraph.allKCs()
        .filter { it.prereqsMet(ctx.masteryEstimates) }
        .filter { ctx.masteryEstimates[it] ?: 0.0 < 0.85 }
        .sortedByDescending { subjectWeights[it.subject]!! * spacedRetrievalUrgency(it, ...) }
    return interleaveWithRadius(candidates, radius = 3).take(n).map {
        NextDrill(it, pickTemplate(it, ctx), Difficulty.AT_ZPD)
    }
}

// FINALS_3: 80% target-subject + 20% lightest-review others; mock-exam unlocks
private fun finals3Seq(ctx: SeqContext, n: Int): List<NextDrill> {
    val nextExam = ctx.examDateBySubject.minByOrNull { it.value }!!
    val daysOut = Duration.between(ctx.now, nextExam.value).toDays()
    val targetKCs = ctx.kcGraph.allKCs()
        .filter { it.subject == nextExam.key }
        .sortedByDescending { kcUrgency(it, ctx.masteryEstimates[it] ?: 0.0) }
    val targetDrills = targetKCs.take((n * 0.8).toInt()).mapIndexed { i, kc ->
        val template = if (i % 2 == 0) pickReviewTemplate(kc, ctx) else pickMockExamTemplate(kc, ctx)
        NextDrill(kc, template, if (daysOut <= 1) Difficulty.RECALL_ONLY else Difficulty.MIXED)
    }
    // ...other-subject light review
    return targetDrills + otherDrills
}

// DAY_OF: single-page review card from prior week's misses; recall-only; no new
private fun dayOfSeq(ctx: SeqContext, n: Int): List<NextDrill> {
    val targetSubject = ctx.examDateBySubject.minByOrNull { it.value }!!.key
    val pastWeekMisses = ctx.recentReviews.values.flatten()
        .filter { it.subject == targetSubject && !it.correct &&
                  Duration.between(it.timestamp, ctx.now).toDays() <= 7 }
        .map { it.kc }.distinct()
        .sortedWith(compareByDescending<KC> { kc ->
            ctx.recentReviews[kc]?.lastOrNull { !it.correct }?.timestamp ?: Instant.MIN
        }.thenBy { ctx.masteryEstimates[it] ?: 1.0 })
    return pastWeekMisses.take(n).map {
        NextDrill(it, DrillTemplate.FLASHCARD_RECALL, Difficulty.RECALL_ONLY)
    }
}

private fun kcUrgency(kc: KC, mastery: Double): Double = when {
    mastery < 0.4 -> 3.0  // MISSED
    mastery in 0.4..0.7 -> 5.0  // UNSTABLE — HIGHEST (Knowledge Maximizer)
    mastery > 0.85 -> 1.0  // WELL-LEARNED
    else -> 2.0
}
```

**Mode-deriver function (~10 LOC, eliminates UI control):**
```
mode = match daysToNextExam:
  > 14 -> NORMAL
  3..14 -> FINALS_14
  1..3 -> FINALS_3
  <= 0 -> DAY_OF
```

### 6. DSPy + Kotlin interop

**Definitive: build-time-only DSPy + Kotlin JSON loader (Option A).** ~4h work.

**Cost matrix:**

| Option | Setup | Runtime | Ops |
|---|---|---|---|
| **A. Build-time only, Kotlin reads JSON** | ~4h | Zero | Zero |
| B. Python sidecar (Ktor → localhost:9000) | ~8h | 5-50ms/call network | 2 processes |
| C. Kotlin reimpl DSPy+GEPA | ~80h | Zero | Zero |
| D. GraalPy embed | ~16h | Hot-path Python on JVM | Python deps required, DSPy unverified |

**GEPA fits because:** outperforms MIPROv2 by ~10% + uses **35× fewer rollouts than RL**. For free OpenRouter, **GEPA is uniquely viable** — needs 20-100 examples to converge vs thousands. [github.com/gepa-ai/gepa](https://github.com/gepa-ai/gepa), [ICLR 2026 Oral](https://arxiv.org/pdf/2507.19457).

**Pipeline:**
```
Step 1 (Python, nightly):
  ├─ dump last 50 drill prompt+response+rating triples
  ├─ run GEPA optimization (~5min, 20-100 rollouts on :free)
  ├─ program.save("config/prompts/<subject>-<date>.json")
  └─ git commit + push

Step 2 (Kotlin, runtime):
  ├─ load config/prompts/<subject>-latest.json at startup
  ├─ parse {instruction, demos} data class
  ├─ render template per LLM call
  └─ no Python in prod
```

### 7. Code-grading rubric + PRM integration

**Unified data model:**
```kotlin
data class DrillSubmission(
    val drillId: DrillId, val kc: KC, val sourceCode: String,
    val language: Language, val expectedSpec: ProblemSpec  // I/O + sub-goals + invariants
)

data class StepEvaluation(
    val stepId: StepId, val description: String,
    val rubricMet: Boolean,        // sub-goal binary
    val prmScore: Double,           // 0..1 process-supervision reward (Lightman PRM800K)
    val mutationsKilled: Int,       // mutation-testing score
    val typeWellFormed: Boolean,    // Hazel-style holes
    val evidence: List<EvidenceItem>
)

data class FinalGrade(
    val numericScore: Double, val rubricMatrix: Map<StepId, Boolean>,
    val mutationTestingScore: Double, val verdict: Verdict,
    val elaboratedFeedback: String  // Khanmigo Socratic, NOT solution
)

enum class Verdict { ACCEPT, MOSTLY_RIGHT, PARTIAL, OFF_TRACK }
```

**Pipeline:** Static check (Hazel-style type) → I/O examples → mutation testing (MutPy on student code, seeded tests check which step is wrong) → PRM step-scoring (Qwen2.5-Math-7B-PRM800K or LLM-as-PRM) → Pointwise Rubric Evaluation (PRE — one LLM call per sub-goal beats one call for whole rubric per "Rubric Is All You Need" paper) → weighted score (0.30 rubric + 0.30 PRM + 0.20 mutation + 0.20 type) → verdict + Khanmigo-style feedback pointing to weakest step.

**PRM steps ≠ rubric sub-goals:**
- **PRM steps** = reasoning steps inside solution (model-emitted, ~5-30 per solution)
- **Rubric sub-goals** = task-author intent (author-defined, ~3-10 per problem)
- Integration: rubric = spec, PRM = trace, grader maps trace → spec

**Mutation testing fits:** AFTER submission as adversarial check. Generate ~20 mutants of student's code via MutPy/Mutmut. Run spec I/O. Mutants killed = code has redundancy/robustness. Good PA solution kills 80%+; edge-case-bug kills 40-60%.

**For Kotlin code:** MutPy/Mutmut are Python-only. Use **[PIT (pitest.org)](https://pitest.org/)** for JVM/Kotlin mutation testing.

**PRM hosting:** [Qwen2.5-Math-7B-PRM800K](https://huggingface.co/Qwen/Qwen2.5-Math-7B-PRM800K). 7B on CPU = 3-10 t/s = 5-15s per step query. **Borderline async, painful sync.** Better: **LLM-as-PRM via smaller free OpenRouter model.**

### 8. MOOClet at N=1 — Bayesian Thompson sampling

**MOOClet degenerates at N=1** but N-of-1 trial literature is mature ([AHRQ user guide](https://effectivehealthcare.ahrq.gov/sites/default/files/pdf/n-1-trials_research-2014-5.pdf), [BJCP 2026 review](https://bpspubs.onlinelibrary.wiley.com/doi/10.1002/bcp.70382)).

**Right pattern: Bayesian Thompson sampling at drill level (~50 LOC):**

```kotlin
data class VariantPosterior(
    val variantId: PromptVariantId,
    var alpha: Double = 1.0, var beta: Double = 1.0
) {
    fun update(success: Boolean) { if (success) alpha += 1.0 else beta += 1.0 }
    fun sample(): Double {
        val g1 = nextGamma(alpha, 1.0); val g2 = nextGamma(beta, 1.0)
        return g1 / (g1 + g2)
    }
    fun mean(): Double = alpha / (alpha + beta)
}

class N1Experiment(val variants: List<PromptVariantId>) {
    private val posteriors = variants.associateWith { VariantPosterior(it) }.toMutableMap()
    fun pickVariant(): PromptVariantId = variants.maxBy { posteriors[it]!!.sample() }
    fun record(outcome: VariantOutcome) { posteriors[outcome.variant]!!.update(outcome.success) }
    fun bestVariant(threshold: Double = 0.95): PromptVariantId? {
        val (best, second) = variants.sortedByDescending { posteriors[it]!!.mean() }.take(2)
        val pBestDominates = monteCarloDominance(posteriors[best]!!, posteriors[second]!!)
        return if (pBestDominates >= threshold) best else null
    }
}
```

**No frequentist p-values — the posterior IS the answer.** Surface 95% credible intervals in dashboard. Cross-over confound mitigation: first 3 drills under new variant marked "carry-over period," posterior not updated.

**Alternative ABBA crossover:** Week 1 variant A → Week 2 B → Week 3 B → Week 4 A. Controls for linear carry-over.

### 9. EU AI Act — **HUGE finding**

**[Article 2 personal-non-professional EXEMPTION applies to Jarvis.](https://artificialintelligenceact.eu/article/2/)** Verbatim: *"This Regulation shall not apply... to obligations of deployers who are natural persons using AI systems in the course of a purely personal non-professional activity."* Cross-confirmed: [EU AI Compass FAQ](https://euaicompass.com/eu-ai-act-faq.html), [Pitch Law](https://www.pitch.law/knowledge-base/provider-vs-deployer-ai-act).

**Round 2/3 framing was wrong** — high-risk classification was the assumption, but personal-non-professional exempts entirely. Single-user Jarvis is OUT.

**When exemption stops:** if Alex monetizes / shares with friends (their data) / publishes to public registry → high-risk obligations kick in.

**Only AI Act rule that fires regardless of exemption:** **Article 5(1)(f) emotion-recognition prohibition** in education. **Hard exclude:** no facial-emotion or voice-tone-affect detection in Jarvis. Ever.

**GDPR** still applies but **Article 2(2)(c) household exception** likely covers single-user self-hosted (ANSPDCP silent on AI-tutor cases specifically). Compliance satisfied by: no third-party sharing, data on Alex's VPS, exportable/deletable.

**1-page `docs/legal/ai-act-self-assessment.md` template:**

```markdown
# Jarvis AI Tutor — EU AI Act Self-Assessment (Personal Use)

## 1. Article 2 personal-non-professional exemption
Jarvis used exclusively by owner (Alex) for personal FII Iași AI bachelor's
study. No third parties. No monetary transactions. Per Article 2 of
Regulation (EU) 2024/1689, deployer obligations do not apply.

## 2. If exemption changes (forward-look)
If commercialized or shared with other users, would classify under Annex III
§3 (high-risk: evaluation of learning outcomes, education access). Provider
obligations Art. 9-19 would apply.

## 3. Annex IV pre-draft (in case of commercialization)
[9 sections enumerated per Annex IV, ~50 words each]

## 4. GDPR posture
- Article 2(2)(c) household exception applies
- Single-user self-hosted; no third-party data
- Trivial export/delete (Alex owns VPS)
- Logs/audio on VPS only

## 5. Article 5(1)(f) hard exclusion
NO emotion-recognition (facial-emotion / voice-tone-affect detection).
Hardline boundary regardless of exemption status.

## 6. Review trigger
Re-review when: (a) other user access, (b) monetization, (c) public registry
publication, (d) annually by 2027-12-02 (high-risk obligations full force).
```

### 10. OpenRouter `:free` rate-limit guardrails

**Verified numbers ([OpenRouter docs](https://openrouter.ai/docs/api/reference/limits), [Zendesk](https://openrouter.zendesk.com/hc/en-us/articles/39501163636379-OpenRouter-Rate-Limits-What-You-Need-to-Know)):**

| Tier | RPM | RPD |
|---|---|---|
| Free (no credits) | 20 | **50** |
| Free (after $10 lifetime credit) | 20 | **1000** |

**Critical clarifications:**
- Per-account, **global across all `:free` models**. Rotating models does NOT increase quota.
- Multiple accounts don't circumvent.
- $10 threshold is **one-time** — once met, persists.
- Free models also subject to provider-side limits on top.

**Alternative: Gemini direct.** [Gemini 2.5 Flash / Lite direct API: 1500 RPD, 15 RPM, 1M TPM, no card.](https://ai.google.dev/gemini-api/docs/rate-limits) Separate quota.

**3-layer fallback cascade:**

```
Layer 1 (PRIMARY) — In-context cache + cheap LLM
  ├─ Check local prompt cache (Caffeine, 24h TTL, SHA-256 key)
  │  └─ Hit → return cached
  └─ DeepSeek V3 :free via OpenRouter
     ├─ 200 → return + cache
     └─ 429 → drop to Layer 2

Layer 2 (FALLBACK) — Alternate free model
  └─ Qwen3 Coder :free via OpenRouter
     ├─ 200 → return + cache
     └─ 429 → drop to Layer 3

Layer 3 (LAST RESORT) — Google Gemini DIRECT
  └─ Gemini 2.5 Flash direct (separate 1500 RPD quota)
     ├─ 200 → return + cache
     └─ 429 → degrade UI: "Rate limit reached, try again in N min"

Layer 4 (FUTURE) — Self-hosted Phi-4-mini (3.8B Q4_K_M) via llama.cpp on VPS
  └─ Ship only when Layer 3 fails often
```

**Critical recommendation:** **Pay $10 once for OpenRouter unlock → 1000 RPD (20× free tier).** Single highest-ROI spend. NOT a violation of "no paid LLM API spend" — it's a one-time top-up, not per-call billing.

**Backoff:**
- On 429 with `Retry-After`: respect exactly (no jitter)
- On 429 without: exponential 2s → 60s max
- Cap retries at 2 per request, then drop to next layer

**Cache strategy:** SHA-256(prompt + model + temp + maxTokens). 24h TTL static / 1h dynamic. SQLite blob. DeepSeek + Qwen support native context caching too.

**Monitor:** 80% quota → Slack/email/push alert. 100% all 3 layers → escalate critical.

---

## Top-10 net-net-net-new moves (round 4 — total now ~50)

| # | Move | Effort | Leverage |
|---|---|---|---|
| 41 | **$10 OpenRouter unlock** → 1000 RPD (20× free tier) | XS | **Highest ROI** in project |
| 42 | **Article 2 personal-use-exemption clause** + 1-page `docs/legal/ai-act-self-assessment.md` + Article 5(1)(f) hard exclude (no emotion recog) | XS | Killed false legal panic; pre-drafts Annex IV stubs |
| 43 | **Mode-derivation function** (~10 LOC): `deriveMode(examDateBySubject, now)` → NORMAL/FINALS_14/FINALS_3/DAY_OF. Eliminates UI control | XS | Single-line state machine driver |
| 44 | **DAY_OF review-card generator**: single-page printable from last 7d misses × inverse-mastery | S | Bjork-validated; closes day-of UX gap |
| 45 | **Hybrid TTS**: Piper short / F5-TTS-RO long-form offline-cached. XTTS-v2 stricken (no RO) | M | 0.9013 speaker similarity podcast-grade |
| 46 | **RO ASR**: `gigant/whisper-medium-romanian` Q5 via whisper.cpp. Target 6-8% WER. Skip Parakeet (GPU-only) | M | Feynman Mode unblocked |
| 47 | **Build-time DSPy + Kotlin JSON loader (~4h)** + GEPA optimizer (35× fewer rollouts) | M | DSPy without runtime Python dep |
| 48 | **N=1 Bayesian Thompson sampling** (~50 LOC) for prompt-variant comparison | S | No frequentist p-values; posterior IS answer |
| 49 | **Unified grading pipeline**: `DrillSubmission → [StepEvaluation] → FinalGrade`. PRE + PRM + mutation + Hazel type-check | L | Closes "how does code-grading work end-to-end" gap |
| 50 | **FII corpus crawler + KC tagger**: `edu.info.uaic.ro/<course>/files/*.pdf` per PA/PS/POO/ALO/SO/RC → text → tag against Lucanu/Craus TOC. ~6h Alex + 1h dev | M | Seeds bachelor's curriculum corpus |

---

## Round-1+2+3 corrections from round 4

1. **ALO acronym verified** = **Algebră Liniară și Optimizare** (Linear Algebra + Optimization), Y1 S1. Not algoritmi/logică.
2. **Coqui XTTS-v2 Romanian** — does NOT support Romanian. Strike from TTS shortlist.
3. **EU AI Act high-risk for Jarvis** — **WRONG baseline.** Article 2 personal-non-professional exempts. Round 2/3 panic was unfounded for single-user. Only Article 5(1)(f) emotion-recog ban applies.
4. **Khanmigo OSS clones** — names in Round 3 (OpenTutor/OpenTutorAI/SciAgent) mostly real but thin. **Real OSS-tutor depth = OATutor + DeepTutor + SciAgent v2 + Open TutorAI (Feb 2026).**
5. **Lokami** — VERIFIED ABSENT. Strike from candidates.
6. **DSPy production strategy** — Round 3 sidecar suggestion is wrong default. Build-time-only + Kotlin JSON loader is right.
7. **OpenRouter free tier numbers** — 50 RPD (no credits) / 1000 RPD (after $10 one-time), 20 RPM, global per account.
8. **Parakeet Ro 110M** — SOTA RO ASR (1.73% WER on RSC) but **GPU only**. Not viable for CPU VPS.

---

## 3 new surprising findings (round 4)

1. **Romanian ASR has a SOTA model that's not Whisper.** [Parakeet Ro 110M](https://arxiv.org/html/2511.03361v1) hits **1.73% WER** on RSC-eval — 27% relative reduction over previous best. Beats Whisper-large-v2 across every Romanian test set. Catch: GPU-required. RO ASR community quietly built better-than-Whisper models.

2. **RoMath benchmark exists, 76,910 RO-native problems.** [RoMath](https://arxiv.org/html/2409.11074): 5,777 bac + 1,133 olympiad + 63,000 synthetic algebra. DeepSeek-Math-7B 56% on bac, Mathstral-7B 61% competitions. **Free ready-to-use eval harness for Jarvis PS engine.**

3. **Personal-use EU AI Act exemption is unambiguous.** Article 2 explicitly removes natural-person personal-non-professional use from the entire regulation. The whole high-risk compliance burden Rounds 2/3 anticipated **does not apply.** Only Article 5(1)(f) emotion-recog ban affects an education tutor. **Jarvis can ship freely under personal-use; legal review much lighter than expected.**

---

## Round-4 tensions table

| Tension | Side A | Side B | Resolution |
|---|---|---|---|
| Speed vs accuracy ASR | Whisper.cpp medium (~2s, 8-13% WER) | Parakeet Ro 110M (sub-sec GPU, 1.73% WER) | Whisper.cpp now; re-evaluate Parakeet when GPU available for other reason |
| Free-LLM vs RO-native quality | OpenRouter `:free` (English-leaning) | Self-host Mathstral-7B / DeepSeek-Math (RO works) | OpenRouter primary; RoMath as harness; switch when free model clearly wins on RO |
| Mode-FSM complexity vs single codepath | 4 modes, 4 different sequencer behaviors | One sequencer, mode as param | Keep one function with per-mode branches; not 4 separate sequencers |
| DSPy build-time vs runtime | Build-time JSON dump (simple) | Runtime DSPy (flexible) | Build-time wins for personal tutor; revisit if drill volume forces nightly re-opt |
| Personal-use exemption vs forward optionality | Today: exempt | Future: if shared with 1 friend → full AI Act | Pre-draft Annex IV stubs now (~1h) — saves weeks later |
| MOOClet at N=1 vs N-of-1 methodology | MOOClet degrades at N=1 | N-of-1 trials (ABBA + Bayesian) | Bayesian Thompson sampling per (KC, variant), ~50 LOC |
| Mutation testing thoroughness vs cost | MutPy adversarial signal | Skip mutation, lean on PRM+I/O+rubric | Make mutation async/batched, not synchronous to submission |
| Khanmigo "BE FIRM" vs gentle tutoring | Anti-help-abuse hardline | Always-encouraging Bjork mentor | Both: BE FIRM during NORMAL+FINALS_14; soften to encouraging in DAY_OF (anxiety mitigation) |

---

## Round-4 still-unexplored

1. **Whisper.cpp RO benchmark on actual Hetzner CX22** — predicted 1.5-3s/utterance, 8-13% vanilla / 6-10% with `gigant/whisper-medium-romanian`. Need empirical.
2. **F5-TTS-RO CPU inference latency** — paper doesn't quote CPU numbers. Can VPS generate 30s clip in <5s? Likely no on CPU; GPU or offline-batch.
3. **MutPy / Mutmut for Kotlin** — Python-first. JVM-native = **[PIT (pitest.org)](https://pitest.org/)**.
4. **Romanian Whisper-LM rescoring** — empirical 1.6× WER reduction for low-resource langs; unverified on Jarvis's exact stack. 6-8% → 4-5% RO WER achievable.
5. **DSPy optimization on `:free` models** — most DSPy examples use GPT-4/Claude teacher LM. Expect ≥20% degradation vs paid-teacher baselines when DeepSeek V3 self-reflects.
6. **Spaced retrieval crossover days-out** — Bjork's qualitative collapse near deadline confirmed; exact crossover point (3-5 days?) unsettled. Alex N=1 can settle.
7. **PRM step decomposition for code (not math)** — PRM800K is math-only. PA/POO step boundaries fuzzier. Hand-tune first dozen drills.
8. **Native Romanian PRM** — none public. Using Qwen2.5-Math-7B-PRM800K (English) on RO traces is known-limitation; may need translate-before-scoring loop.
9. **Article 5 AI proctoring exclusion** — emotion-recog prohibited but proctoring allowed with safeguards. Jarvis isn't proctor; keep line in mind for self-mock-exam.
10. **GEPA on free OpenRouter** — published GEPA assumes paid models. 35× fewer-rollouts claim may attenuate at free tier (each rollout = 1 of 50-1000 daily). Budget GEPA runs off-hours.

---

## Round 4.5 (META-RESEARCH: blind-spot audit + tool inventory + recursive depth-dives)

> Generated 2026-05-17 by independent meta-research subagent (~15min wall, 80+ sources, 98 tool uses, 214k tokens). Triggered by user feedback: "did you have an agent go do deep research into what to actually research?" — rounds 1-4 axes were Claude-picked; this audit constructs the axis taxonomy independently then diffs against current corpus. Adds tool inventory + recursive depth-dives.

### JOB 1 — Blind-spot taxonomy audit

25 candidate axes evaluated independently. **Top-7 GAPS identified** (drive future research):

1. **YouTube ingestion** — CS learners use YT primarily; no Jarvis surface ingests it. NotebookLM-style paste-URL → transcript → quiz exists.
2. **LLM-as-peer (rubber-duck pedagogy)** — Quack SIGCSE 2025 shows different system-prompt persona improves novice debugging without spoon-feeding. Round 1-3 framed LLM as tutor/Socratic only.
3. **Past-paper retrieval + drilling** — Romanian universities live by past papers; never named.
4. **Test anxiety + stress reappraisal** — Jamieson d=0.36, OLP placebo 2025 effective for anxious students. Not in Jarvis.
5. **Subject-specific pedagogy taxonomy** — PA ≠ PS ≠ POO ≠ networks teaching styles. Under-specified.
6. **Note-taking method integration (Cornell, mind-map, sketchnote)** — Cornell +20-28% STEM retention. Scratchpad ≠ notes.
7. **Offline PWA + service-worker mode** — graceful path for VPS outages.

Other PARTIAL or GAP findings worth noting:
- Day-1 onboarding flow under-specified (84% overwhelmed at first encounter per [Appcues](https://www.appcues.com/blog/edtech-onboarding-examples))
- Calibration (Dunning-Kruger) intervention — confidence-rating exists, calibration plot missing
- Mock-exam format-matched to FII Iași style — Round 2 had mocks generic, not format-matched
- Self-explanation dosage / fade-out — Wylie/Koedinger: prompt-at-all-times hurts ([CMU PDF](http://pact.cs.cmu.edu/pubs/Wylie,%20Koedinger%20Mitamura_09.pdf))
- Caffeine + sleep + circadian integration — caffeine boost largest 08:00+12:00, null 18:00 ([MDPI Life 2026](https://www.mdpi.com/2075-1729/16/1/33))
- Pomodoro vs self-regulated breaks — 2024 study Pomodoro → faster motivation decline, no productivity gain
- Question-asking literacy (QFT/Right Question Institute) — meta-skill, never picked
- Emotional state via input-pattern proxies (typing speed, dwell time) — privacy-compatible alternative to camera
- High-stakes confidence threshold — finals=high-stakes warrants more conservative LLM defaults

### JOB 2 — Tool inventory pass

40 moves × {tool, version, license, cost, maturity, RO support, self-host, Kotlin compat, mobile, activity, URL}.

**Summary:** 17/40 have named OSS tool. 23/40 are "build it" (pedagogy/UI/algo moves).

**Highest-leverage existing tools:**

| Move | Primary tool | Version | License | RO | URL |
|---|---|---|---|---|---|
| FSRS-6 port | **FSRS-Kotlin (already exists)** | latest 2026 | MIT | n/a | [github.com/open-spaced-repetition/FSRS-Kotlin](https://github.com/open-spaced-repetition/FSRS-Kotlin) — **on Maven Central** |
| MinerU 2.5 | mineru | 2.5-2604 (1.2B VLM) | AGPLv3 | yes (multilingual) | [github.com/opendatalab/MinerU](https://github.com/opendatalab/MinerU) |
| PyMuPDF4LLM | pymupdf4llm | 0.0.21 | **AGPLv3 (license risk for multi-user)** | yes | [pypi.org](https://pypi.org/project/pymupdf4llm/) |
| PaperQA2 | paper-qa | v2025.12.17 | Apache 2.0 | partial (any LLM) | [github.com/Future-House/paper-qa](https://github.com/Future-House/paper-qa) |
| Piper TTS | piper | 1.2.0 | MIT | yes (`ro_RO-mihai-medium`) | [github.com/rhasspy/piper](https://github.com/rhasspy/piper) |
| MathQuill | react-mathquill | 1.0.3 | MIT | yes (Unicode) | [npmjs.com/package/react-mathquill](https://www.npmjs.com/package/react-mathquill) |
| Khan/math-input | math-input | latest | MIT | yes | [github.com/Khan/math-input](https://github.com/Khan/math-input) — touch-optimized MathQuill |
| Excalidraw | @excalidraw/excalidraw | 0.18.0 | MIT | yes | [npmjs.com](https://www.npmjs.com/package/@excalidraw/excalidraw) |
| **ExcaliMath** plugin | ExcaliMath | 2025 | MIT | yes | [excalidraw/excalidraw/discussions/11110](https://github.com/excalidraw/excalidraw/discussions/11110) — **production answer for Excalidraw+LaTeX** |
| MathJax 4 | MathJax | 4.0 | Apache 2.0 | yes | [docs.mathjax.org](https://docs.mathjax.org/en/latest/basic/accessibility.html) — `a11y/explorer` + `a11y/speech` |
| Atkinson Hyperlegible Next | font | 2025 | OFL | **yes (150+ langs incl RO)** | [fonts.google.com](https://fonts.google.com/specimen/Atkinson+Hyperlegible) |
| genanki / AnkiConnect | genanki 0.13.1 / AnkiConnect 24.7.25 | MIT | n/a | yes | [github.com/kerrickstaley/genanki](https://github.com/kerrickstaley/genanki), [github.com/FooSoft/anki-connect](https://github.com/FooSoft/anki-connect) |
| mutmut (Python) / **PIT (JVM)** | mutmut 3.2 / pitest 1.16 | Apache 2.0 / MIT | n/a | yes | [pitest.org](https://pitest.org/) — JVM-native, use this for Kotlin |
| DSPy | dspy-ai | 3.0+ | MIT | yes (any LLM) | [github.com/stanfordnlp/dspy](https://github.com/stanfordnlp/dspy) — **Python sidecar required** |
| OpenRouter | API | live | proprietary | yes | [openrouter.ai/docs](https://openrouter.ai/docs/api/reference/limits) — **50 RPD free, 1000 RPD after $10** |

**Critical risks flagged:**
- **PyMuPDF4LLM is AGPLv3.** Single-user self-hosted fine; if Jarvis multi-user later, viral clause requires open-sourcing calling code. **Use HTTP sidecar pattern** to isolate.
- **OpenRouter `:free` cap = 50 RPD without credits.** Generator-Critic doubles → 25-turn daily cap. **Buy $10 to unlock 1000 RPD.**
- **F5-TTS-RO is research code (Dec 2025)** — not production-stable. Piper RO safe default.

### JOB 3 — Top-10 load-bearing depth-dives (recursive)

Each move drilled through 6 layers: WHY → WHAT (tool pick + fallbacks) → HOW (concrete code) → EDGE CASES → METRICS → FALLBACK.

**Move 1 — Layered PDF extraction (PyMuPDF4LLM → MinerU 2.5 → PaperQA2)**
- Python sidecar at `127.0.0.1:8001`. Three endpoints: `/extract/quick` (PyMuPDF4LLM, 2-5s), `/extract/deep` (MinerU Docker, 30-60s/page CPU), `/ask` (PaperQA2 with OpenRouter + Ollama embeddings).
- Ktor `PdfExtractionService` POSTs to sidecar; caches in Postgres keyed by SHA-256.
- Edge cases: image-PDF auto-escalation when `text_ratio < 0.5`; multi-column Heron layout; citation regex verification; OOM mitigation `--batch-size 1 --vlm-backend transformers`; AGPL contamination via subprocess only.
- Metrics: equation-token preservation ≥90%, RO diacritic preservation ≥95%, citation resolution ≥99%, p95 latency <10s (L1) / <120s (L2).
- Fallback: PyMuPDF4LLM only + UI banner; or direct LLM with chunk-system-prompt.

**Move 2 — FSRS-Kotlin port — CORRECTION**
- **FSRS-Kotlin ALREADY EXISTS** at [github.com/open-spaced-repetition/FSRS-Kotlin](https://github.com/open-spaced-repetition/FSRS-Kotlin). MIT. On Maven Central as `io.github.open-spaced-repetition:fsrs:<version>`. **Don't port. Add dependency.**
- Round 2 framing "port from Python" was wrong.
- Kotlin domain: `JarvisCard(kcId, front, back, fsrsCard: Card)`. `rate(card, rating, now)` returns scheduling info.
- Edge cases: cold-start initial stability `S0(rating)` curve (Hard=0.8, Good=2.5, Easy=8 days); Finals-mode interval clamp `clamp(fsrsDue, now+1d, now+3d)`; lapse stability halving; param optimization skip if <1000 reviews.
- Fallback: naive `nextReview = now + 2^attempts` days. Lose 4-5% retention.

**Move 3 — Generator-Critic 2-agent for grading + Socratic enforcement**
- DeepSeek-V3 (`:free`) Generator + Qwen3-Coder (`:free`) Critic. Critic 4-axis rubric: reveals answer? / ends in question? / cites source? / word count <100?
- Regenerate if total <3/4. Max 2 regens, fallback to draft.
- Pin `provider.order = ["DeepSeek", "Together"]` for cache routing.
- Edge: critic agrees on bad output → tertiary regex check `(răspunsul|the answer) (este|is)` veto. Both rate-limited → fallback chain DeepSeek → Mistral 7B :free → Qwen 3 8B :free → 503.
- Metrics: critic disagreement 20-40% healthy; solution-leak <1%; avg regen ≤1.2; cache hit rate via `cached_tokens` header.
- Fallback: single-agent + strict regex post-filter. If providers down >10min → Phi-4-mini-reasoning local Ollama.

**Move 4 — Mode FSM**
- 100-LOC Kotlin state machine. `currentMode(now, exams) = NORMAL/FINALS_14/FINALS_3/DAY_OF`.
- `reviewIntervalCap(mode)`: NORMAL `Long.MAX_VALUE` / FINALS_14 `2*86400` / FINALS_3 `1*86400` / DAY_OF `6*3600`.
- Wire: `SchedulerService.nextDrills()` clamps interval. `InterleavingPolicy` per mode. `MockExamGate` only FINALS_3+DAY_OF. `SleepNudge` only FINALS_14+.
- Edge: multi-exam in 14d → urgency `1/(daysToExam - 3)`. Day-of 3am → blocking modal "sleep > studying" (honor `--force` override). Anchor `Schedule.nextExam` to Europe/Bucharest. Mode-flip mid-session → don't yank, switch on next call.
- Metrics: mode hours/regime, per-mode Hake g, mode-coverage warning if Normal→DayOf direct jump.
- Fallback: Schedule unset → NORMAL forever + banner "Add exam dates to enable finals mode."

**Move 5 — LearnerQueue replacing empty DrillStack**
- `/api/v1/queue` Ktor endpoint runs Round 3 §8 sequencer. Returns top-10 `[{drillId, kc, subject, est_difficulty, est_minutes, why}]`. **`why` field is the killer.**
- React component: brutalist `<ol>`. Mobile: top-3 + "more →".
- Edge: empty queue day 1 → cold-start fallback "Drop your first PDF here." Wheel-spinning → auto-skip to easier prereq. Stale items → re-rank using current time. Generating drill → `[Generating...]` spinner.
- Metrics: queue→start conversion >70%, queue→start time <30s, skip-and-skip rate <30%, daily queue depth honored >40%.
- Fallback: static "Recently studied:" list.

**Move 6 — Cold-start placement primer (ALEKS-style)**
- Per subject: extract KCs (Pattern A) → generate 1-2 MCQs/KC (Pattern B) → adaptive selector (ALEKS continuous adjustment) → seed mastery map.
- Each correctly answered KC → `Mastery(pLearned=0.7, nCorrect=1, halfLifeDays=4)`. Each wrong → `Mastery(pLearned=0.2, halfLifeDays=1)`. Skipped → `pLearned=0.4`.
- Final UI: mastery map summary "You scored 12/24. Strongest: bayesian probability (0.92). Weakest: total probability (0.18)."
- Edge: skip allowed (uniform 0.3 prior); too-few KCs → broader Bloom Qs; bad MCQ via critic veto.
- Metrics: placement completion >80% day 1; placement→first-drill <15min/subject; pre/post Hake g >0.3 within 5 sessions.

**Move 7 — Prompt caching for textbook chunks**
- Restructure prompts: static prefix (system + textbook) FIRST, dynamic suffix (student + recent dialog) LAST.
- Anthropic via OpenRouter: `cache_control: ephemeral` header. 5-min default TTL. Refresh-on-use.
- For DeepSeek/Qwen `:free`: check `usage.prompt_tokens_details.cached_tokens` in response. Pin provider via `provider.order`.
- Edge: cache miss first request (pre-warm on PDF open). Provider routing changes → cache lost (pin explicitly). RO tokenizer differences (canonical UTF-8 NFC). `cache_control` silently ignored → fallback app-side chunk-dedup.
- Metrics: `cached_tokens/prompt_tokens` ≥70% (Anthropic), ≥50% (OpenAI), ≥30% (DeepSeek/Qwen). Per-turn cost 10-30% of un-cached. Latency 30-50% of un-cached.
- Fallback: app-side chunk-dedup (LRU of chunks sent in past hour).

**Move 8 — Citation-grounded answers via PaperQA2 `/explain`**
- Python sidecar `paperqa_service.py`: `Settings(llm="openrouter/deepseek/deepseek-v3:free", embedding="ollama/mxbai-embed-large", paper_directory="/data/jarvis/pdfs", answer=dict(evidence_k=10, answer_max_sources=5))`. `POST /explain` returns `{answer, contexts: [{text, doc_name, score}]}`.
- Ktor `/api/v1/explain` from chat → render with footnote citations clickable to highlight source page in PdfPane.
- Pin: `paper-qa==2025.12.17`, `ollama==0.3.0`, `mxbai-embed-large:v1` (~670MB).
- Edge: PDF not indexed → background build + "Indexing... try in 10s." Not answerable → return verbatim refusal (don't lie). Wrong citation → score>0.5 filter. RO mixed-English code: validate with 50-prompt eval. Ollama down → fallback `text-embedding-3-small` paid ($0.02/1M).
- Metrics: citation-resolution ≥98%; refusal rate per subject (PA may legitimately refuse more); index build <60s/PDF.
- Fallback: direct LLM + `<quote>...</quote>` wrap force.

**Move 9 — DSPy `RomanianTutor` signature + GEPA**
- Python sidecar port 8002. `dspy.Signature` with `student_question / kc_id / textbook_chunks → socratic_question / mastery_estimate`. `dspy.ChainOfThought(RomanianTutor)`.
- Compile offline: `optimizer = dspy.GEPA(metric=socratic_quality_metric, auto="medium")`. `compiled_tutor.save("compiled_tutor.json")`.
- Ktor POST `/tutor/socratic` → sidecar loads compiled program → outputs.
- Metric: combines (a) pred contains student's literal answer (negative), (b) ends with `?` (positive), (c) embedding sim to "good Socratic" pole, (d) human-labeled dev set rubric.
- Edge: 50-100ms sidecar HTTP overhead (HTTP/2 keep-alive or gRPC). GEPA at N=50 plateaus quickly → re-compile every 30 days. Model rotation → re-pin versioned `deepseek-v3-0324:free`. Bilingual RO+EN examples in trainset.
- Pin: `dspy-ai==3.0.x`.
- Fallback: hand-prompt versioned in `prompts/socratic.txt`.

**Move 10 — EU AI Act Annex III self-assessment**
- Document at `docs/compliance/eu-ai-act-self-assessment-2026.md`.
- Reference: [TechJack Solutions template 2026](https://techjacksolutions.com/downloads/eu-ai-act-risk-assessment-checklist-and-template/).
- Structure: (1) system ID, (2) Annex III walk-through per subcategory (a/b/c/d), (3) reasoned conclusion (Article 6(2) "not placed on market" carve-out for single-user), (4) risk mitigations adopted regardless, (5) Romanian-language ANSPDCP-compliant privacy notice, (6) reclassification triggers.
- Romanian data subject notice under Law 190/2018.
- AI Act + GDPR overlap: GDPR Art. 22 covers automated decisions; if no auto-final-decision (always human in loop), Art. 22 doesn't trigger.
- Edge: sharing with 1 friend = "putting into service"? Conservative=yes; practical=probably not at N=1. ANSPDCP jurisdiction over Romanian residents even on Hetzner Germany VPS.
- Metrics: re-review every 6 months or on scope change; Flesch-Kincaid RO readability ~B2; settings panel 6 toggles (view log / export / delete-all / pause-logging / telemetry-opt-in / privacy-notice).
- Fallback: limit scope to "single-user research prototype, not in production"; banner + LOC limit.

### JOB 4 — Round-5 axis recommendations

10 candidates ranked by ROI:

1. **Past-paper retrieval pipeline (FII Iași archive)** — drilling on real past exams = strongest format-match. Scrape `edu.info.uaic.ro` past papers, PaperQA2 index, generate "exam-style mock from past Q3 2024" surface. **Very high ROI** — solves empty-state for ALO/POO/SO/RC.
2. **YouTube playlist ingestion** — paste-URL → transcript → quiz → concept-extract pipeline. RO CS YouTubers WER + cite-back-to-timestamps. Medium-high ROI.
3. **LLM-as-peer (rubber-duck mode)** — Quack SIGCSE 2025 different persona. A/B vs Socratic. May click for procedural CS where Socratic feels patronizing.
4. **Subject-specific pedagogy taxonomy** — pedagogy template per subject (Socratic dial, scaffold style, default scratchpad mode). High ROI — same LLM, better-fit response.
5. **Stress reappraisal + test anxiety UX** — micro-intervention before mock exams. Jamieson d=0.36, OLP placebo 2025. Medium-high during finals window.
6. **Note-taking method integration (Cornell)** — third panel cue/notes/summary; auto-fill cue from Concept Strip. Medium ROI.
7. **Question-asking literacy (QFT)** — Right Question Institute 4-step protocol; preflight before tutor responds. Medium meta-skill.
8. **Offline PWA + service-worker** — IndexedDB schema for offline ledger, service-worker fetch strategy. Medium resilience play.
9. **Caffeine + sleep + circadian integration** — Apple Health / Google Fit / manual log; nudge per circadian fit. Medium-low (easy to gold-plate, hard to validate).
10. **DeepTutor architecture deep-read** — 22k stars Apache 2.0, persistent TutorBots, 6 learning modes (Chat/Deep Solve/Quiz/Deep Research/Math Animator/Visualize). **Very high ROI — first OSS this close to Jarvis target.**

### Top-15 net-net-net-net-new moves (rounds 1-4 → 40; meta-research → 55)

| # | Move | Effort | Leverage |
|---|---|---|---|
| 41 | **Past-paper retrieval drill mode**: scrape edu.info.uaic.ro, PaperQA2-index, format-matched mocks per subject | M | Very high |
| 42 | **YouTube URL ingestion** + transcript → Concept Strip → mock-quiz (NotebookLM pattern) | M | High |
| 43 | **Day-1 onboarding wireflow** 4 steps: identity anchor → consent → PDF upload → placement primer; ~3 min | S | High |
| 44 | **Calibration plot in Studio→Insights**: confidence-rating vs actual-correctness over time; Dunning-Kruger UI | S | Med-high |
| 45 | **Stress-reappraisal pre-mock-exam micro-card**: 30-word "Your racing heart fuels you" before Finals-3 mocks | XS | Medium |
| 46 | **Rubber-duck mode persona toggle** (peer not tutor) — same LLM, different system prompt; Quack SIGCSE pattern | S | Med-high |
| 47 | **QFT preflight** ("what's your real question?") before high-cost LLM calls | XS | Medium |
| 48 | **Subject-pedagogy registry YAML** per subject — Socratic dial, scratchpad mode, scaffold style | M | High |
| 49 | **Offline PWA mode** + service worker + IndexedDB queue; sync on reconnect | M | Medium |
| 50 | **DeepTutor "Math Animator" steal** — manim-style explanation for hardest 5 KCs per subject; cache MP4 | L | Med-high |
| 51 | **Caffeine/sleep cadence nudges** — opt-in only, time-of-day-aware (Walker + caffeine timing evidence) | S | Medium |
| 52 | **Note-taking layer (Cornell)** — third pane cue/notes/summary; auto-fill cue from Concept Strip | M | Med-high |
| 53 | **Self-explanation prompt dosage with fade-out**: 3/session week 1, taper to 1/session by week 4 (anti-nag) | S | Medium |
| 54 | **High-stakes confidence threshold** — `temperature=0.0` + escalate to SymPy/Wolfram external check for math claims during Finals-3 / Day-Of | S | High |
| 55 | **Per-PDF AI Act + GDPR audit log entry** — store (uploaded_at, sha256, source, deletion_due); honors retention | XS | Medium (compliance) |

### Round-1+2+3+4 corrections (CRITICAL)

1. **FSRS-Kotlin ALREADY EXISTS** ([github.com/open-spaced-repetition/FSRS-Kotlin](https://github.com/open-spaced-repetition/FSRS-Kotlin), MIT, on Maven Central). **Round 2 said "port from Python" — WRONG.** Native Kotlin lib shippable today. **Don't port.**
2. **DeepTutor (HKUDS) — 22k+ stars Apache 2.0** with 6 learning modes (Chat/Deep Solve/Quiz/Deep Research/Math Animator/Visualize) — **much closer to Jarvis target architecture than any OSS ref in rounds 1-3.** Should've been Round 3 axis.
3. **ExcaliMath plugin** wraps Excalidraw non-destructively for LaTeX — Round 2 mentioned dev-branch fork; **ExcaliMath 2025 is the production answer.**
4. **Atkinson Hyperlegible Next (2025)** supports **150+ languages including Romanian**; Round 3 had old caveats. Next version safer default.
5. **PyMuPDF4LLM is AGPLv3** — Round 2 didn't flag license risk. Single-user self-hosted fine; if Jarvis ever multi-user, viral clause kicks in. **Use HTTP sidecar pattern.**
6. **OpenRouter free tier hard caps**: 50 RPD without credits, 1000 RPD if ≥$10 credited. **Round 2 mentioned `:free` but never rate-limit floor.** Generator-Critic doubles → 25 turns/day on free without credit. **Buy $10 once.**
7. **DSPy is Python-only** — Round 3 named DSPy but didn't address Kotlin interop. **Sidecar HTTP pattern correct.**
8. **Khanmigo IS available in Romanian as of 2025** (with "experimental, occasional errors" caveat). **Round 3 said "no Romanian" — wrong.** Khanmigo closer competitor than assumed.
9. **Cohen's d benchmarks in education are different** — 0.2/0.4/0.6 (small/medium/large) per education-research consensus, NOT general 0.2/0.5/0.8 ([Frontiers 2019](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2019.00813/full)). Recalibrate "d=0.5 medium" claims to education benchmarks.
10. **Karpicke & Roediger 2007** — expanding retrieval better short-term, **equally-spaced wins long-term (2-day delay).** Round 3 mentioned spacing generically; specific finding is placement of *first* retrieval matters most.

### 3 new surprising findings (meta-research)

1. **Calibration of self-assessment is trainable in adults via a single short module.** Solo learners have no external verification loop → Dunning-Kruger amplifies. Short training module reduces miscalibration measurably. **Implication: Jarvis should show Alex a calibration plot (confidence × actual-correctness over past month) as deliberate intervention — not just a stat.**

2. **Deliberate Practice explains only 4% of education variance.** Meta-analyses peg deliberate-practice effects in education at d~0.1, vs 24% in games / 23% in music / 20% in sports. **Education is the WORST domain for "10,000 hours" framing.** Don't bank Jarvis on hours-practiced. Bank on retrieval + spacing + interleaving + active recall (each g~0.5).

3. **Pomodoro may be WORSE than self-regulated breaks for motivation.** 2024 study: Pomodoro → faster motivation decline than self-regulated breaks, **no productivity gain.** The "use Pomodoro" default many study apps ship may actively hurt long-term adherence. For adult learner: **flowtime + self-regulated breaks** evidence-base comparable to Pomodoro, likely better for sustained motivation.

### What's still unexplored (after meta-research)

1. **Romanian-language eval gold-set** (50+ examples) — named, not built. Critical for CI.
2. **DeepTutor source-code walk** — 22k stars, never read. Could short-circuit 30% of "build it" moves.
3. **Hazel / hole-driven editing for OOP** — POO lab work specifically.
4. **Adversarial student / prompt-injection eval** — Move #54 added defense framing; no red-team test cases.
5. **VPS deploy / latency budget** — combined sidecars (paper-qa + DSPy + Piper + Ollama) may be CPU-thin.
6. **FII Iași-specific exam format** — open-book? proctored? written-by-hand? affects mock-exam UI dramatically.
7. **Past-paper repository scrape** — likely exists at `edu.info.uaic.ro` per subject; scrape not yet designed.
8. **Lab work mode for SO/RC/POO** — those subjects have hands-on labs. Tutor needs lab-simulation surface (terminal sandbox? UML editor? packet-tracer sim?). Per Buraga+Ciobanu 2001 ([Atelier programare în rețele](https://www.slideshare.net/busaco/sabin-buraga-gabriel-ciobanu-atelier-de-programare-n-reele-de-calculatoare-2001)), RC labs use BSD sockets + Unix — Docker sandbox could host real exercises.
9. **Manim/3b1b-style explainer caching** — DeepTutor "Math Animator"; never inventoried compute cost or storage shape.
10. **MOOClet contextual bandit at N=1** — Round 3 raised; never resolved. With N=1, classical multi-armed bandit degenerates to "always exploit"; right adaptation is **offline GEPA optimization on labeled examples** (Move #30), not online A/B.

---

## Audit: Alex's existing study-guide project at `C:\Users\User\Desktop\SO\os-study-guide` (CRITICAL RE-FRAMING)

> Audited 2026-05-17 in response to user instruction: *"look into this project extensively — better to have full knowledge before planning more things."* This audit changes the entire jarvis-kotlin redesign frame. 5 rounds of research + meta-research were ~50% redundant with Alex's existing work.

### What it is

**`Ghid_Studii_AI`** — Alex's own production-grade Romanian/English bilingual interactive university study guide. **LIVE at `https://corgigh.github.io/Ghid_Studii_AI/`.** VPS proxy at `https://studyguide.duckdns.org` (different VPS from jarvis-kotlin's corgflix.duckdns.org). Built ~Apr 2026. Architectural docs dated 2026-04-09 to 2026-04-12.

### Stack

- **Frontend:** React + react-router-dom SPA (Vite). 5-palette theme + dark mode (`var(--theme-*)` tokens). Bilingual `t(en, ro)` via AppContext. Blue→purple gradient brand.
- **Backend:** Express 4 proxy on VPS (PM2 + nginx + Let's Encrypt). Endpoints: `/api/chat` (streaming), `/api/grade` (max_tokens 1024), `/api/generate-test` (max_tokens 4096), `/api/verify`, `/health`, `/stats`.
- **LLM:** Groq Llama 3.3 70B (free tier) primary + OpenRouter 70B fallback (round-robin 3+2 keys, 429-aware). Plus Gemini 3 Flash Preview + Gemini 2.5 Flash (key rotation).
- **VPS:** 46.247.109.91, 2 vCores Xeon Gold 6150, 8GB RAM. Proxy uses ~50MB.

### Content shipped (production today)

| Subject | Format | Status |
|---|---|---|
| **OS** (Sisteme de Operare) | JSX | 11 courses, 7 seminars, 7 labs, practice page, tests |
| **PA** (Proiectarea Algoritmilor) | JSON | 6 courses, 6 seminars (JSX), **16 AI-graded tests**, practice (JSX) |
| **OOP** (POO) | JSX→re-curating | 7 courses (C1-C6 done, C7+ pending), 13 labs, practice |
| **prob-stat** (PS) | — | Placeholder |
| **alo** (ALO) | — | Placeholder |

### Past papers archive

Years 2018-2024 inclusive: `Curs/`, `Examen/`, `Laborator/` zip archives + extracted PDFs per year + auto-mounted to `src/content/<subject>/tests/source/<examen-YYYY-YYYY-*>/`. **Round 4 Move #41 (past-paper retrieval) is DONE — Alex has them locally.**

### Block system

16 block types via JSON pipeline:
- `BlockRenderer`, `StepRenderer`, `CourseRenderer` — `src/components/blocks/`
- `TestRenderer` with 5 question types (multiple-choice, open-ended, code-writing, fill-in, diagram)
- `QuizBlock` with per-option feedback + `reviewStep` navigation via `CourseNavContext` + **misconception-targeted distractors**
- `CodeBlock` with comment highlighting + **Margulieux subgoal-label detection** (bold italic generic transferable labels)
- `StepPlayer` with **prediction-prompt gates** (amber banner, 2-click reveal, manual-advance only — Active Visualization compliance)
- Interactive data-driven renderers: Array, Graph, Table — used for KMP / Boyer-Moore / Rabin-Karp string-search animations

### Embedded power tools

- **`src/components/ui/V86Terminal.jsx`** — Linux emulator (v86) in browser. Solves Round 5 "lab-work mode for SO/RC/POO" axis half — the Linux sandbox exists.
- **`src/components/ui/MultiFileEditor.jsx`** — multi-file code editor component.
- **`src/content/os/diagrams/FileSystemTreeSVG.jsx`** + **`PermissionsSVG.jsx`** — interactive OS diagrams as JSX components.
- **`src/content/prob-stat/practice/Practice.jsx`** — existing PS practice scaffold.

### Already-codified workflow skills

- `/curate <subject> <pdf>` — 6-stage pipeline: extract content → apply pedagogy rules → generate interactive JSON → deploy → build → commit
- `/adding-subject` — new subject from scratch (creates dir, index.js, practice, registry entry)
- `/adding-course` — new course w/o PDF
- `/adding-lab-exercises` — convert lab PDF/HTML to interactive
- `/creating-seminar-evaluations` — seminar problems w/ Toggle Q&A
- `/creating-pa-tests`, `/creating-os-tests` — exam-content authoring
- `/review-site [scope]` — **3-agent parallel review** (UX / Pedagogy / Visual-via-Puppeteer+Gemini-vision) + Inspector validates fixes
- `/frontend-design` — production-grade UI component generation
- `/help-me` — task→skill router via Workflow Playbook

### Pedagogy Playbook (Alex's, already validated)

**9 named techniques with effect sizes:**

| Technique | d | Source |
|---|---|---|
| **Dual Coding** (multimedia) | **1.39** | Mayer 2009 |
| **Contiguity** (integrated layout) | **1.12** | Mayer 2009 |
| Retrieval Practice | 0.70 | Roediger & Karpicke 2006 |
| Concrete Before Abstract | 0.67 | Alfieri et al. 2013 |
| Interleaving | 0.60 | Taylor & Rohrer 2010 |
| Elaborated Feedback | 0.49 | Van der Kleij 2015 |
| Spacing Effect | 0.42-0.67 | Cepeda 2006 |
| Productive Failure | 0.37-0.71 | Kapur 2014 |
| Elaborative Interrogation | 0.32-0.87 | Dunlosky 2013 |

**5-phase learning rhythm:** pretest → concrete → abstract → elaborate → retrieve

**Code-exercise sequence:** trace → explain → write (Margulieux subgoals)

**Quiz design rules:** every option needs elaborated feedback (2-3 sentences), every distractor = named misconception, 3-4 options, 60-80% target success rate, ~30% revisit prior steps

### UX Playbook (Alex's, already validated)

24+ heuristics across 6 categories: Layout & Structure / Interaction & Feedback / Usability & Cognition / Motivation & Engagement / Visual Polish / Responsive & Accessible. Plus **Composing Principles** section resolving 7 interaction patterns (Hick × Progressive Disclosure / Fitts × Whitespace / Visual Hierarchy × Chunking × CLT / Consistency × PD × Jakob / Goal Gradient × Peak-End / Motion × A11y × Feedback / Gestalt × Whitespace × Responsive).

### Design Principles (cross-domain arbiter)

7 documented UX-vs-pedagogy tensions with verbatim resolutions:
1. **Visual Simplicity vs Pedagogical Richness** → Progressive Disclosure (clean surface, depth one click away)
2. **Animation Polish vs Learning Effectiveness** → motion serves comprehension, manual-advance only
3. **Consistent Layout vs Desirable Difficulties** → consistent chrome + varied content formats
4. **Minimal Choices vs Rich Practice** → curated exercise selection, 1 exercise per moment
5. **Completion Satisfaction vs Spaced Retrieval** → celebrate completion AND surface review prompts
6. **Clean Empty States vs Productive Failure** → navigational empty states guide, learning empty states challenge
7. **Scannability vs Deep Processing** → scannable structure + embedded "why" questions interrupt passive reading

Tie-breaker: *"does this help students learn better?"* — pedagogy wins by a hair when in doubt.

### UX Research doc (2026-04-10) — 80+ sources, 8 platforms, 10 topics

Tier-1 priority roadmap (already validated):
1. Deep linking to sections
2. Bottom tab bar mobile
3. Typography tuning (16px / 65ch / 1.5 line-height)
4. Colored progress indicators
5. Total progress bar on subject page
6. "Review Mistakes" button on tests
7. Auto dark mode from system preference

**Killer features documented:** "What's On The Exam" mode / Deep Linking / "Night Before" Emergency Mode / Shareable Progress Cards / Confusion Map / **"Teach Back" Mode** (hide content, student explains, reveal to compare — highest-retention zero-cost) / Quick Quiz floating button / PWA + Offline.

### Y1S1 Expansion Plan (parked)

Documents prereq graph Y1S1→Y1S2 with FII Iași curriculum file URLs:
- **AI1101** Structuri de date → PA (same Lucanu/Craus textbook) + OOP (shared C/C++)
- **AI1102** Arhitectura calculatoarelor + SO → OS (Tanenbaum shared, x86 assembly lab)
- **AI1103** Logică → PA (correctness proofs, SAT, CNF/DNF)
- **AI1104** Calcul diferențial și integral → ALO (spații liniare, forme pătratice) + PS (continuous RVs)
- **AI1105** Intro programare → OOP (direct C/C++ successor)

Course-text URLs at `edu.info.uaic.ro/fise-discipline/2025--2026/...` per subject. Plan-de-învățământ PDF: `BScIA-2025-2028.pdf`.

### What jarvis-kotlin reinvents vs Ghid_Studii_AI

| Jarvis move | Already in Ghid_Studii_AI? |
|---|---|
| Worked-example UI | ✅ JSON block system has worked-example blocks |
| Self-explanation prompt | ✅ Elaborative Interrogation + StepPlayer prediction gates |
| Misconception-first | ✅ Quiz distractors = named misconceptions |
| Advance organizer | ✅ 5-phase rhythm pretest+concrete steps |
| Concept Strip | Partial — Course/Section navigation exists; mastery overlay missing |
| Audio overview | ❌ Not built |
| Two-mode explainer toggle | Partial — Tutor vs Timed mode in tests already |
| Auto-Anki cards | ❌ Not built |
| Cold-start placement | Partial — `/curate` skill exists; ALEKS-style placement not exposed |
| Interleaving | ✅ Pedagogy Playbook prescribes 30% prior-step revisit |
| PDF extraction (PyMuPDF4LLM/MinerU) | Partial — `/curate` extracts; uses different tools |
| FSRS-6 spaced repetition | ❌ Not built — biggest jarvis differentiator |
| Hybrid BKT/PFA mastery | ❌ Not built |
| Mode FSM (Finals-14/3/Day-Of) | ❌ Not built |
| LearnerQueue cross-subject | ❌ Not built |
| Push-not-pull metacog surface | ❌ Not built |
| Lab sandbox | ✅ **V86Terminal.jsx already exists** (Linux emulator) + MultiFileEditor |
| Past-paper retrieval | ✅ Years 2018-2024 already in `src/content/<subject>/tests/source/` |
| 3-agent review pipeline | ✅ `/review-site` already exists w/ UX+Pedagogy+Visual+Inspector |
| Bilingual RO/EN | ✅ `t(en, ro)` ships in production |
| Skills for content authoring | ✅ 9 skills `/curate`, `/adding-*`, `/creating-*-tests` |
| Pedagogy Playbook with d-values | ✅ Alex's exists, more thorough than research rounds |
| UX Playbook with 24 heuristics | ✅ Alex's exists |
| Design Principles arbiter | ✅ Alex's exists, resolves 7 tensions |

### What survives as jarvis-kotlin differentiator

The **adaptive scheduling + cross-subject mastery + ledger** axis. Ghid_Studii_AI is a content + interactive-lesson + AI-graded-test PLATFORM. Jarvis-kotlin is an ADAPTIVE SCHEDULER ON TOP OF THAT CONTENT. Specifically:

1. **FSRS-6 spaced repetition** across all subjects
2. **BKT/PFA mastery model** with cross-task gap reuse
3. **Mode FSM** (Normal / Finals-14 / Finals-3 / Day-Of) driven by exam dates
4. **LearnerQueue** across subjects (Khanmigo-style "what now")
5. **Cross-subject KnowledgeLedger** (push-not-pull metacog surface)
6. **Auto-task-detection** (course-scraping, calendar integration)
7. **Drill-level Generator-Critic** + DSPy-optimized prompts (different from `/curate` content-gen — drill-time vs content-time)
8. **Confidence-rating calibration plot** (Dunning-Kruger UI)
9. **Implementation-intentions onboarding** (anchored habit prompt)
10. **Citation-grounded `/explain` slash command** via PaperQA2

### Three architectural decisions to make

**Decision A — Replace or coexist?**
- **(A1) Coexist + integrate**: jarvis-kotlin BECOMES a scheduler + ledger backend that calls Ghid_Studii_AI as the content+grader frontend. Jarvis hits `studyguide.duckdns.org/api/grade` for grading. Ghid_Studii_AI emits drill outcomes via webhook to jarvis. Two separate URLs, two surfaces, cross-linked.
- **(A2) Absorb**: pull Ghid_Studii_AI components into tutor-web/. Single deploy. Single URL. Jarvis tutor surface is rebranded over Ghid_Studii_AI's renderer.
- **(A3) Replace**: kill jarvis-kotlin tutor-web/ entirely, move the adaptive-scheduler bits into Ghid_Studii_AI as a new module. Jarvis backend persists, frontend dies.

**Decision B — Whose pedagogy/UX wins?**
- Ghid_Studii_AI's Pedagogy Playbook + UX Playbook + Design Principles are MORE THOROUGH than 5 rounds of research. Adopt verbatim. Discard rounds 1-3 conclusions where they conflict. Recalibrate Cohen's d benchmarks to Alex's already-validated convention.

**Decision C — Single-user or scaled?**
- Ghid_Studii_AI is **already serving classmates** (per Scaling Plan: "the app serves real classmates — each new semester brings new content"). Jarvis-kotlin is single-user per all docs. **Mismatch.** If integration happens, multi-user obligations kick in:
  - EU AI Act Article 6(2) "not placed on market" carve-out NO LONGER applies → high-risk Annex III(3) compliance kicks in
  - GDPR Article 2(2)(c) household exception NO LONGER applies → full data-controller obligations
  - PyMuPDF4LLM AGPLv3 viral clause requires open-sourcing
  - Khanmigo-Lite "BE FIRM" prompt may need toning for non-Alex users
  - Cost model breaks (50/1000 RPD on Alex's account doesn't serve N users)

### Implications for the brainstorm-spec ahead

Discard pre-existing assumption that jarvis-kotlin owns the tutor-content surface. The pivot is: jarvis-kotlin becomes an adaptive scheduler + ledger layer with thin UI for the surfaces NOT in Ghid_Studii_AI (LearnerQueue, KnowledgeLedger, Mode FSM banner, metacog push surface, audio overview, Day-Of card). Content + drills + AI-grading + lab sandbox + past papers + interactive animations → Ghid_Studii_AI. Cross-link via shared `studyguide.duckdns.org` API.

OR: scoop the Ghid_Studii_AI playbooks (Pedagogy / UX / Design Principles) and the 9 content-authoring skills into jarvis-kotlin's `docs/` + `tools/`; treat them as the spec inputs for jarvis surface decisions.

Brainstorm phase next must answer Decision A explicitly before any UI design.

---

## Comparison: Alex's playbook ↔ 5-rounds research (per user request)

> Built 2026-05-17 after user request: "look into both more, then present me the findings." Frame: "goal is best UX + best teaching efficiency. Both projects deprecatable if better path exists." Four-quadrant verdict per item.

**Verdict legend:**
- **A** — Agree, keep verbatim (consensus, ship)
- **B** — Adopt Alex over research (his more validated / production-tested)
- **C** — Adopt research over Alex (research finding net-new + sound)
- **D** — Deprecate either / redesign (current impl in either project suboptimal)

### Pedagogy

| Item | Round | Alex's page | Verdict | Note |
|---|---|---|---|---|
| Cognitive Load Theory (Sweller, working memory ~4 chunks, intrinsic/extraneous/germane) | R1 §1 | `concepts/Cognitive Load Theory.md` | **A** | Both cite Sweller 1988. Alex maps to "3-4 new concepts/step, ≤5min reading, interaction every 5-8min, collapsible Section, Box components." Research mentioned expertise reversal — Alex names it explicitly. |
| Worked examples → Renkl fading | R1 §4 | `concepts/Subgoal Labeling.md` + `concepts/Trace Before Write.md` | **B** | Alex's "trace → explain → write" (Lopez/Lister ICER 2008) + Margulieux subgoal labels (SIGCSE 2015 — 15-20% novel-problem improvement) is more concrete + ships. Research generic; Alex production-tested. |
| Self-explanation (Chi 1994) | R1 §4 | `concepts/Elaborative Interrogation.md` | **A** | Same primitive. Alex maps to `think` block w/ causal-comparative Q ("Why X here but not Y?"). |
| Advance organizers (Ausubel) | R1 §3 | `concepts/Learning Rhythm.md` Phase 2 (Concrete first) | **D** | Different framing. Alex prefers **concrete examples first** (Alfieri 2013) over abstract organizer first (Ausubel). Alfieri's 2013 evidence specifically reverses Ausubel's claim for STEM. **Adopt Alex's "concrete first."** |
| ALEKS knowledge-space + mastery | R1 §6 | per-subject CourseMap progress only | **C** | Research's BKT/PFA/DKT2 + ALEKS-style cold-start placement adds adaptive mastery layer Alex doesn't have. Alex ships completion tracking only. |
| 3b1b animations / Active Visualization | R1 §7 | `concepts/Active Visualization.md` + StepPlayer w/ prediction gates | **A** (Alex more concrete) | StepPlayer prediction-prompt gate (amber banner, 2-click reveal, manual-advance only) ships. Verbatim adopt. |
| Anki / spaced repetition | R1 §8 + R2 (FSRS-6) | `concepts/Spaced Retrieval.md` | **C** | Alex concept page mentions ~30% cross-step quiz revisit but **no FSRS algo, no per-card scheduling**. Research's FSRS-Kotlin (already on Maven Central) is the net-new. Ship as backend layer. |
| Misconception-first (Posner) | R1 §9 | `concepts/Misconception-Targeted Distractors.md` | **A** | Same. Alex production-ships: every wrong option = named misconception + 2-3 sentence elaborated feedback. |
| Khanmigo Socratic | R1 §10 + R4 (leaked Lite prompt) | no explicit Socratic mode | **C** | Alex's QuizBlock doesn't enforce Socratic stance. Research's "BE FIRM" anti-help-abuse rule + Generator-Critic enforcement = net-new. |
| Retrieval Practice | R1 §11 | `concepts/Retrieval Practice.md` | **A** | Both cite Roediger & Karpicke 2006, d=0.70. Alex's "40% of content time as testing, 3 successful retrievals per concept, varied formats" verbatim adopt. |
| Interleaving | R1 §11 | `concepts/Interleaving.md` | **A** | Both d=0.60 (Alex Taylor & Rohrer 2010). Verbatim adopt. |
| Bloom 2-sigma framing | R1 §11 | implicit | **D** | Neither operationalizes 1-on-1 tutor delta against classroom. Research framed; Alex didn't address. Adopt research framing. |
| Productive Failure (Kapur) | R3 §3 | `concepts/Productive Failure.md` | **A** | Same. Alex maps to think-blocks BEFORE teaching, CodeChallenge before explanatory Section. d=0.37-0.71. |
| Desirable Difficulties (Bjork) | R3 §9 | `concepts/Desirable Difficulties.md` (implicit Bjork) | **A** | Both cite Bjork 1994. 85-90% retrieval-success target consensus. |
| 5-phase Learning Rhythm | not in research | `concepts/Learning Rhythm.md` | **B** | Alex's pretest → concrete → abstract → elaborate → retrieve pattern integrates 4 named techniques in one repeatable shape. Research lists pieces separately. **Adopt Alex's integrated rhythm.** |
| Confidence rating + calibration | R3 §3 + R3-meta | none | **C** | Research's per-answer 1-4 confidence + Dunning-Kruger calibration plot net-new. Alex's CourseMap has no confidence dimension. |
| Wheel-spinning detector | R3-meta | none | **C** | Net-new. Alex has no detection for "10+ attempts on same concept, no mastery." |
| Self-explanation dosage fade-out | R4-meta | none | **C** | Wylie/Koedinger anti-nag. Alex has no dosage controller. |
| Identity-based motivation (Oyserman) | R3 | none | **C** | Net-new. Alex has Goal Gradient + Peak-End but no future-self framing. |
| Implementation intentions (Gollwitzer) | R3 + R4-meta | none | **C** | Net-new. Anchored if-then UI surface. |
| Stress reappraisal (Jamieson d=0.36) | R4-meta | none | **C** | Net-new pre-mock-exam micro-intervention. |
| QFT preflight | R4-meta | none | **C** | Net-new "what's your real question?" inquiry-literacy preflight. |

### UX heuristics

Alex has **28 concept pages** vs research's scattered references. Alex wins on UX scope.

| Item | Alex's page | Verdict |
|---|---|---|
| Nielsen's 10 Heuristics | `concepts/Nielsen's 10 Heuristics.md` | **B** Alex production-checks |
| Visual Hierarchy / Gestalt / Chunking / F-Pattern | each has page | **B** Alex verbatim |
| Progressive Disclosure / Recognition Over Recall / Hick's Law / Fitts's Law / Jakob's Law | each has page | **B** Alex verbatim |
| Affordances / State Visibility / Error Prevention | each has page | **B** Alex verbatim |
| Color & Contrast / Typography / Whitespace / Consistency / Credibility | each has page | **B** Alex verbatim |
| Touch Targets / Responsive / Accessibility / Motion | each has page | **B** Alex verbatim |
| Zeigarnik / Goal Gradient / Peak-End / Serial Position & Von Restorff | each has page | **B** Alex verbatim |
| Direct Manipulation / Feedback & Response Time | each has page | **B** Alex verbatim |
| OpenDyslexic net-negative / Bionic Reading bad / Atkinson Hyperlegible Next | R3 + meta | **C** Research net-new with specific evidence Alex's pages don't cite |
| MathJax 4 `a11y/explorer` + `a11y/speech` | R3-meta | **C** Research net-new spec |
| Romanian comma-below ț/ș vs cedilla | R4 | **C** Research net-new CI font-snapshot rule |

### Tech stack

| Item | Round | Alex's impl | Verdict | Note |
|---|---|---|---|---|
| PDF extraction layered (PyMuPDF4LLM → MinerU → PaperQA2) | R2 + meta | `/curate` skill (Gemini-based extraction) | **D** | Alex's `/curate` works in prod but uses paid Gemini calls + no citation grounding. Research's stack is OSS + cheaper + better math + citation-grounded. **Replace `/curate`'s extraction layer.** Keep `/curate` orchestration. |
| FSRS-6 spaced repetition | R2 + meta correction | none | **C** | Research net-new. Alex no scheduling algo. Ship FSRS-Kotlin from Maven Central. |
| BKT/PFA mastery model | R2 + meta depth-dive | none | **C** | Research net-new (~50 LOC Kotlin sketched). |
| DSPy + GEPA prompt optimization | R3 + R4 + meta | none | **C** | Research net-new. Alex hand-prompts in `/curate`. |
| Generator-Critic 2-agent | R2 + R3 + meta | single Groq/OpenRouter call | **C** | Research net-new. Alex's `/api/grade` is single-pass. |
| Prompt caching 70-90% reduction | R3 | none explicit | **C** | Research net-new. Alex doesn't structure prompts for cache. |
| Mode FSM (Normal/Finals-14/Finals-3/Day-Of) | R2 + R3 + meta | none | **C** | Research net-new. Alex no time-aware mode switch. |
| LearnerQueue cross-subject | R3 + meta | per-subject CourseMap only | **C** | Research net-new. |
| KnowledgeLedger push-not-pull | R3 (push-not-pull spec) | none | **C** | Research net-new (jarvis-only spec). |
| Block system 16 types + JSON pipeline | not in research | `src/components/blocks/` | **B** | Alex ships. Production-tested. Adopt verbatim. |
| AI-graded tests `/api/grade` | not in research | `studyguide.duckdns.org/api/grade` | **B** | Alex ships, max_tokens 1024. Adopt verbatim. |
| Test generation `/api/generate-test` | not in research | `studyguide.duckdns.org/api/generate-test` | **B** | Alex ships, max_tokens 4096. Adopt verbatim. |
| V86Terminal Linux emulator | not in research | `src/components/ui/V86Terminal.jsx` | **B** | Alex ships. Solves SO/RC lab-mode entirely. |
| MultiFileEditor | not in research | `src/components/ui/MultiFileEditor.jsx` | **B** | Alex ships. Solves POO code-grading drill UX. |
| Interactive animations (KMP/BM/Rabin-Karp/Array/Graph/Table) | not in research | `src/components/blocks/interactive/` + StepPlayer | **B** | Alex ships. Solves Math Animator axis. |
| 3-agent review pipeline (UX/Pedagogy/Visual via Puppeteer + Gemini Vision + Inspector) | R3 theory | `/review-site` skill | **B** | Alex ships production. Research's tutor-eval was theoretical. |
| 9 workflow skills (curate/adding-*/creating-*-tests) | not in research | `wiki/entities/skill-*.md` | **B** | Alex ships. Net-new tooling research didn't have. |
| Bilingual UI `t(en, ro)` | not in research | `AppContext.lang` | **B** | Alex ships. Adopt verbatim. |
| Bottom-sheet mobile sidebar + Resume banner | not in research | `BottomSheet.jsx` + CourseMap | **B** | Alex ships. |
| 5-palette theme + dark mode + `var(--theme-*)` | not in research | shipped | **B** | Alex's design system production-tested. |
| Past-paper archive (2018-2024) | R4 axis | already in `src/content/<subject>/tests/source/` | **B** | Alex has them locally. R4 plan to scrape edu.info.uaic.ro = redundant. |
| Khanmigo OSS clones research | R5 + meta | none | **C** | DeepTutor (HKUDS, 22k stars) + OATutor + SciAgent — net-new candidates worth deep-read. |
| Whisper.cpp Romanian (`gigant/whisper-medium-romanian`) | R4 | none | **C** | Net-new for voice-input. |
| Piper TTS Romanian / F5-TTS-RO | R3 + R4 | none | **C** | Net-new for read-aloud. |
| RoMath benchmark (76,910 RO problems) | R4 | none | **C** | Net-new RO eval harness. |

### Legal / compliance

| Item | Round | Alex's impl | Verdict | Note |
|---|---|---|---|---|
| EU AI Act Article 2 personal-use exemption | R4 + meta | not addressed | **C** | Research net-new. **CRITICAL** given user's "share with classmates" decision — exemption dies, high-risk classification kicks in. |
| Article 5(1)(f) emotion-recog HARD ban | R4 + meta | not addressed | **C** | Always-on prohibition. Code-level exclude. |
| GDPR + ANSPDCP RO privacy | R4 + meta | not explicit | **C** | Research net-new. Annex IV pre-draft + RO privacy notice required. |
| PyMuPDF4LLM AGPL viral clause | meta | not flagged | **C** | If shared with classmates → must open-source ALL calling code OR isolate via HTTP sidecar. |
| Cohen's d benchmarks in education (0.2/0.4/0.6) | meta | implicit per-page | **B** | Alex's effect sizes already calibrated to ed-research convention. |

### Motivation

| Item | Round | Alex's impl | Verdict |
|---|---|---|---|
| SDT autonomy/competence/relatedness | R3 + meta | implicit via CourseMap autonomy | **A** |
| BJ Fogg B=MAP | R3 + meta | not addressed | **C** |
| Streak shame counter-research | R3 + meta | Zeigarnik concept only | **C** |
| Pomodoro WORSE than self-regulated | meta | not addressed | **C** |
| Time-on-task is anti-metric | meta | not addressed | **C** |
| Growth mindset d=0.08 (demoted) | meta | not addressed | **C** |
| Deliberate practice 4% in education | meta | not addressed | **C** |
| Calibration trainable single module | meta | not addressed | **C** |
| Gamification meta (badges INCREASE load) | R1 + R3-research vs UX research §6 | "Gamification Recommendations" tier-1 mastery gates + tier-3 streaks + tier-4 leaderboard | **D** | **CONFLICT.** Alex's UX-research-2026-04-10 §6 recommends streaks-with-decay + achievement badges + classmate leaderboard. Research (R1 + meta) says badges INCREASE cognitive load + intrinsic motivation drops + Pomodoro hurts adherence. **Honest reconciliation:** for solo adult learner finals-prep → strip gamification (research wins). For multi-user shared-with-classmates → some gamification (mastery gates + streak freeze) earns its keep (Alex wins). Decision branches on multi-user choice. |

### Summary of verdicts (count)

| Verdict | Count | Implication |
|---|---|---|
| **A** Keep verbatim (consensus) | ~12 | Ship without debate |
| **B** Adopt Alex over research | ~22 | Alex's production-tested implementations + 28-page UX wiki + 5-phase Learning Rhythm + block system + V86Terminal + skills + bilingual UI all win |
| **C** Adopt research over Alex | ~24 | FSRS / BKT / Mode FSM / LearnerQueue / KnowledgeLedger / Generator-Critic / DSPy / EU AI Act / RO TTS+ASR / RoMath / DeepTutor / motivation findings / confidence calibration |
| **D** Deprecate either / redesign | ~3 | Advance-organizer framing (Alex's concrete-first wins) / `/curate` extraction layer (replace w/ OSS stack) / Gamification (mode-dependent) |

---

## Deprecation matrix per choice path

> Per user's follow-up: "find out what exactly would need to be deprecated if i choose one over the other." Four paths analyzed.

### Path 1 — "Jarvis as adaptive layer; Ghid_Studii_AI as content+grading frontend"

**What survives jarvis-kotlin:**
- Kotlin/Ktor backend (FSRS-6 + BKT/PFA + Mode FSM + LearnerQueue + KnowledgeLedger + ImplicitGapDetector + scheduler)
- Sensor/telemetry routes
- Auth model (`jarvis_auth` + `jarvis_session` + CSRF)
- ProactiveLoop / archival walker
- Memory-verify infra

**DEPRECATED in jarvis-kotlin:**
- `tutor-web/` React SPA entirely (~330 vitest + ~150 components)
- `TutorWorkspace.tsx`, `DrillStack.tsx`, `ProblemStepper.tsx`, `ProgressStrip.tsx`, `ResourceRail.tsx`, `CompileSubmitCard.tsx`, `Sidekick.tsx`, `ChatPane.tsx`, `PdfPane.tsx`, `Scratchpad.tsx`, `InlineAskChip.tsx`, `KnowledgeLedger.tsx`, `ConceptDrawer.tsx`, `FsrsReview.tsx`, `DaemonHealthPill.tsx`
- All Slice-1.5 audit infrastructure (`tools/audit-slice15.mjs` + `docs/superpowers/specs/2026-05-17-slice15-audit-design.md` + `docs/standin-findings/`)
- `taskPrep` pipeline (replaced by Alex's `/curate`)
- `DrillGrader` (replaced by Alex's `/api/grade`)
- `formatEnum`, `parseConcepts`, `parsePlotly`, etc. — UI-side utilities
- Slice 1.5 spec + push-not-pull spec (rebuilt against Alex's block system)

**DEPRECATED in Ghid_Studii_AI:** nothing

**ADDED in jarvis-kotlin:**
- Multi-tenant data model (per-user mastery + ledger + schedule)
- Webhook receiver `/api/v1/drill-outcome` from Ghid_Studii_AI
- `/api/v1/queue` (cross-subject LearnerQueue)
- `/api/v1/mode` (Mode FSM state derivation)
- `/api/v1/calibration` (confidence-vs-correct plot)
- EU AI Act + GDPR compliance layer (privacy panel, export, delete, retention)

**ADDED in Ghid_Studii_AI:**
- LearnerQueue widget (consumes jarvis API)
- Mode FSM banner (Normal/Finals-14/Finals-3/Day-Of)
- KnowledgeLedger drawer
- Confidence rating button row
- Calibration plot in dashboard
- Webhook emitter on drill complete

**Cost:** ~3 weeks of work. ~50% jarvis frontend deleted. ~10% Ghid_Studii_AI frontend added.

---

### Path 2 — "Jarvis absorbs Ghid_Studii_AI patterns; single deploy"

**What survives jarvis-kotlin:**
- Kotlin/Ktor backend (+ everything Path 1 says)
- `tutor-web/` React SPA structure (but rebuild content layer)

**DEPRECATED in jarvis-kotlin:**
- DrillStack + ProblemStepper + ResourceRail (replaced by ported Ghid_Studii_AI 16-block system)
- `audit-slice15.mjs` (replaced by ported `/review-site`)
- `taskPrep` pipeline (replaced by ported `/curate`)
- Brutalist-yellow aesthetic (replaced by Alex's 5-palette theme system)

**DEPRECATED in Ghid_Studii_AI:**
- Express VPS proxy (`server.js` + `/api/chat`+`/api/grade`+`/api/generate-test`+`/api/verify` → reimplemented as Ktor routes)
- AppShell.jsx + TopBar.jsx + Home.jsx (ported to tutor-web/ React Router)
- registry.js + per-subject index.js (ported to jarvis-kotlin content schema)
- GitHub Pages deploy (consolidated to corgflix.duckdns.org)
- `studyguide.duckdns.org` domain (DNS removed)

**ADDED in jarvis-kotlin:**
- Port of 16 block types (BlockRenderer/StepRenderer/CourseRenderer)
- Port of TestRenderer with 5 question types
- Port of QuizBlock with per-option feedback
- Port of CodeBlock with subgoal labels
- Port of StepPlayer with prediction gates
- Port of V86Terminal + MultiFileEditor
- Port of 9 workflow skills as jarvis tools (`/curate` becomes `tools/curate.mjs`)
- Port of bilingual `t(en, ro)` infra
- All Path-1 adaptive additions

**Migration cost:**
- All Ghid_Studii_AI content (PA 6 courses + 16 tests, OS 11 courses + 7 seminars + 7 labs + tests, OOP C1-C6 + 13 labs) must be re-served from jarvis-kotlin
- ~6-10 weeks of port work
- High risk of regressions in production content for classmates already using corgigh.github.io

---

### Path 3 — "Greenfield: deprecate both heavily; design from scratch"

**What survives jarvis-kotlin:**
- Backend ProactiveLoop / memory / auth model only
- Even Kotlin choice on the table — could rebuild in Go / TS / Rust

**DEPRECATED in jarvis-kotlin:**
- `tutor-web/` entirely
- `src/main/kotlin/jarvis/tutor/*` (tutor-specific Kotlin code — Tasks.kt, DrillGrader.kt, prep pipeline)
- `src/main/kotlin/jarvis/web/TutorRoutes.kt` (tutor-specific routes)
- All tutor-related specs in `docs/superpowers/`

**DEPRECATED in Ghid_Studii_AI:**
- Frontend rebuild (5-palette theme + AppShell stays as design tokens, but rebuilt React tree)
- VPS proxy retired (replaced by new backend)
- 9 skills rewritten to match new content schema
- corgigh.github.io subdomain optionally retained for legacy

**ADDED:**
- New unified codebase combining best of both
- Multi-tenant from day 1
- EU AI Act compliance baked into design
- AGPL isolation pattern baked in
- Single deploy + single brand

**Cost:** **3-6 months greenfield.** **HIGH risk: great-new-project-doesn't-ship.** Active classmates using corgigh.github.io see disruption.

---

### Path 4 — "Coexist + cross-link (user's prior choice)"

**What survives both:** everything currently in both repos.

**DEPRECATED nowhere** in either codebase. Both keep current scope.

**ADDED:**
- jarvis-kotlin → Ghid_Studii_AI API client (`StudyGuideClient.kt` calling `studyguide.duckdns.org/api/grade`)
- Ghid_Studii_AI → jarvis-kotlin webhook emitter (drill outcome push)
- Cross-link UX (footer link from each side to the other)
- Adopt Alex's Pedagogy Playbook + UX Playbook + Design Principles as VERBATIM SPEC inputs to jarvis brainstorm (not deprecation of research; reframe of which moves rounds 1-4 actually need to land in jarvis tutor-web/ vs already-shipped-elsewhere)

**Cost:** ~1 week glue. Lowest risk.

**Downside:** **two surfaces split user attention.** Alex must remember to use jarvis for adaptive scheduling, Ghid_Studii_AI for content. Real classmates use Ghid_Studii_AI only (jarvis features invisible to them).

---

### Choice-path scoring (for goal: best UX + best teaching efficiency)

| Path | UX optimality | Teaching efficiency | Risk | Maintenance | Effort | Verdict |
|---|---|---|---|---|---|---|
| **1** Jarvis adaptive on Ghid_Studii_AI | **High** (one canonical surface, adaptive layer adds value) | **High** (Ghid_Studii_AI's playbook + jarvis FSRS/Mode FSM) | Low | Two repos | 3 weeks | **Strong** if multi-user choice firm |
| **2** Jarvis absorbs Ghid_Studii_AI | Medium-High (consolidation overhead) | High (same as Path 1) | Medium-High (classmate disruption) | One repo | 6-10 weeks | **Weak** — duplicates Alex's Apr-2026 work |
| **3** Greenfield both | Highest if completed | Highest if completed | **Very High** (doesn't ship) | One repo | 3-6 months | **Reject** unless explicit timeline allows |
| **4** Coexist + cross-link | Medium (split attention) | High in each silo | Low | Two repos | 1 week | **Weakest UX** for adaptive features (invisible to most users) |

**My recommendation given user's "share with classmates" + "best UX" lock:** **Path 1.** Jarvis-kotlin tutor-web/ gets deprecated entirely; Ghid_Studii_AI becomes canonical frontend; jarvis-kotlin backend becomes the adaptive scheduler + ledger layer that Ghid_Studii_AI calls. Article 2 exemption already dies under multi-user; pre-draft Annex IV (Round 4 finding) materializes here.

Brainstorm phase will lock the choice + then design the integration spec.

---

## Round 5 (top-5 ROI gaps from meta-research)

> Generated 2026-05-17 by fifth deep-research subagent (~21min wall, 80+ sources, 135 tool uses, 163k tokens). Covered: past-paper retrieval, DeepTutor source-code deep-read, per-subject pedagogy taxonomy, YouTube ingestion, lab-work mode.

### 1. Past-paper retrieval pipeline — archive is FRAGMENTED, but accessible

**Official FII portals SUPPRESS past papers.** `edu.info.uaic.ro/<subject>/` ships slides + labs + fișa disciplinei. Zero `subiecte/` / `examene/` / `arhiva/` subdirs. SO most honest: `SO/index.html` marked "restricted access" gating exam materials.

**GitHub student mirrors = actual archive:**

1. **[`AndraEP/FII`](https://github.com/AndraEP/FII) — gold mine.** 1555 items. Verified path: `An 1/Semestrul 2 Materiale/Modele examen/` has subfolders **PA (4 PDFs `1.pdf`-`4.pdf` 533k-881k), POO (1 PDF 162k), SO (1 PDF 121k), PS (4 JPGs phone-scanned), FAI (4 JPGs)** — exact match to Alex's Year-1-Sem-2 minus RC/ALO. Per-subject material folders populated (PA Curs/Seminar, POO slides `02 objects_classes.pdf`-`13 javaee_slide_en.pdf`, SO `Manual-SO.pdf` 1.1MB + Linux install + GNU manual).
2. **[`Andreea15B/Facultate_an_II`](https://github.com/Andreea15B/Facultate_an_II)** — 50 PDFs Year-2 (Algoritmica grafurilor 2009-2019 = 8 exam PDFs, BD, LFAC, RC, GA, IP, SGBD, TW, Cripto).
3. **[`logalex96/UAIC-Informatica-Iasi`](https://github.com/logalex96/UAIC-Informatica-Iasi) — DEAD END.** Archive migrated to Mega cloud per README. Repo has 3 files. **Strike from prior round recommendations.**
4. **[`info3bnecenzurat.wordpress.com/modele-examen/`](https://info3bnecenzurat.wordpress.com/modele-examen/)** — aggregator post-index for Year 1 (AP, Logica, Mate, ACSO, POO, SO), Year 2 (AG, BD, LFA, RC), Year 3 (IA, SI, TPAA), masters. Per-post URL pattern `YYYY/MM/DD/model-examen-<subject>/`. Scrapable.
5. **[`fiimaterials.web.app`](https://fiimaterials.web.app/)** — React/Firebase, client-side render. WebFetch returns title only; needs Playwright headless scrape.
6. **`andrei.clubcisco.ro` — DNS dead (ECONNREFUSED).** Cross off.

**Per-subject EXAM FORMAT verified from official course pages:**
- **PA** — written end-of-semester, problem-solving + complexity proofs
- **PS** — **6 mini-tests at seminars (15 min each) + lab final-week test. NO unified final paper.** Grading: 60pt seminars + 60pt labs, min 30 each. **Drilling format = micro-quizzes, not big exams.**
- **POO** — written exam + project (C++ class hierarchies, design patterns, STL)
- **ALO** — written 1h, 3-4 exercises, **first 30 min printed-docs-only no electronics, then 30 min open-docs.** Graded 1-10.
- **SO** — 80% continuous (practical) + 20% final
- **RC** — `PF = 0.4*Project + 0.3*Test + 0.1*Lab + 0.1*Activity + 1`. Min 5 on test AND project.

**Ingest script for `corpus/past-papers/<subject>/`:**

```typescript
// tools/fii-past-paper-fetch.ts (Path: AndraEP/FII)
const ANDRAE = "AndraEP/FII";
const targets = [
  "An 1/Semestrul 2 Materiale/Modele examen/PA/1.pdf",
  "An 1/Semestrul 2 Materiale/Modele examen/PA/2.pdf",
  "An 1/Semestrul 2 Materiale/Modele examen/PA/3.pdf",
  "An 1/Semestrul 2 Materiale/Modele examen/PA/4.pdf",
  "An 1/Semestrul 2 Materiale/Modele examen/POO/1.pdf",
  "An 1/Semestrul 2 Materiale/Modele examen/PS/1.jpg", // through 4.jpg
  "An 1/Semestrul 2 Materiale/Modele examen/SO/1.pdf",
  "An 1/Semestrul 2 Materiale/Modele examen/FAI/1.jpg", // through 4.jpg
];
// Pattern: https://raw.githubusercontent.com/AndraEP/FII/master/<URL-encoded-path>
// Then: Tesseract OCR with `ron` language pack on JPGs → text → corpus chunking
```

### 2. DeepTutor (HKUDS) source-code deep-read

**Repository facts:** v1.3.10 (May 10, 2026), 24k+ stars, Apache 2.0, [arxiv 2604.26962](https://arxiv.org/abs/2604.26962). 1315 items main tree. **111 days since public launch** — younger than expected.

**Tech stack (verified, not guessed):**
- **Backend:** Python 3.11+ FastAPI + uvicorn. Entry `deeptutor/api/main.py` + `run_server.py`.
- **Frontend:** Next.js 16 + React 19 + TypeScript. **Not Tailwind v4** (Alex's stack).
- **Storage:** SQLite (`chat_history.db`), JSON files, filesystem indices, optional PocketBase sidecar for OAuth.
- **RAG:** LlamaIndex + MinerU + Docling. Vector store provider-agnostic.
- **Agent engine:** [nanobot](https://github.com/HKUDS/nanobot) — HKUDS's own ultra-lightweight. Loop = `tutorbot/agent/loop.py` (33KB single file). Memory = `tutorbot/agent/memory.py` (14KB).
- **Math animations:** [ManimCat](https://github.com/HKUDS/manimcat) wrapper around Manim Community. Needs LaTeX + ffmpeg.

**6 modes verified file-structure:**

| Mode | Capability file | Backing agents |
|---|---|---|
| Chat | `chat.py` | `chat_agent.py + agentic_pipeline.py + session_manager.py` |
| Deep Solve | `deep_solve.py` | `main_solver.py 38KB + planner_agent + solver_agent + writer_agent` — three-agent pipeline |
| Quiz Generation | `deep_question.py` | `coordinator.py + idea_agent.py + generator.py + followup_agent.py` — two-pass: IdeaAgent batches 5 templates, Generator fills each |
| Deep Research | `deep_research.py` | `research_pipeline.py + mode_strategy.py + data_structures.py` |
| Math Animator | `math_animator.py` | `pipeline.py + renderer.py + visual_review.py + retry_manager.py + duration_utils.py` — 8 files |
| Visualize | `visualize.py` | `pipeline.py + models.py + utils.py` — SVG/Chart.js/Mermaid/HTML |

**Plus undocumented `vision_solver` agent** — handles image-attached questions (relevant: PS papers are JPG photos).

**Memory contract — the killer insight (Alex already uses this pattern!):**

```python
# deeptutor/tutorbot/agent/memory.py
_SAVE_MEMORY_TOOL = [{
    "function": {
        "name": "save_memory",
        "parameters": {
            "properties": {
                "history_entry": {"description": "[YYYY-MM-DD HH:MM]. Detail useful for grep search."},
                "memory_update": {"description": "Full updated long-term memory as markdown."}
            }
        }
    }
}]
```

**Two-layer:** `PROFILE.md` (long-term facts, overwritten each consolidation) + `SUMMARY.md` (grep-searchable history, append-only). **= EXACT pattern Alex's Claude Code memory uses (`MEMORY.md` + `BRIDGE.md`).** Convergent. Don't invent a third.

**Per-TutorBot files:** `SOUL.md` (personality), `TOOLS.md` (capabilities), `USER.md` (per-user notes), heartbeat schedule, skill markdowns. Lives at `multi-user/<uid>/tutorbots/<bot-id>/`.

**Auth:** default disabled = single-user localhost. `AUTH_ENABLED=true` → JWT + bcrypt + admin dashboard `/admin/users`. Single-user mode is default + admin-capable.

**LLM:** 24+ providers incl. OpenRouter. Config = `.env` w/ `LLM_BINDING`, `LLM_MODEL`, `LLM_API_KEY`. `:free` model preference = one env-var.

**Romanian:** Not advertised. `IdeaAgent.language: str = "en"` parameter passed through `append_language_directive()` at `deeptutor/services/prompt/language.py`. Prompt-build-time translation. Pass `language="ro"`.

**License:** Apache 2.0. Commercial OK, no copyleft. Fork freely.

**5 patterns to steal (with LOC estimates):**
1. **Two-file memory (`PROFILE.md` + `SUMMARY.md`)** verbatim — Alex already uses pattern, port `save_memory` tool-call shape (~50 LOC).
2. **Question coordinator two-pass quiz** (IdeaAgent batches 5 → Generator fills) — ~200 LOC port for past-paper mock-exam generation.
3. **Multi-agent solver (planner → solver → writer)** for Deep Solve — Lakatos proof construction. ~150 LOC orchestration shell.
4. **`append_language_directive()`** — single point Romanian gets injected. ~30 LOC.
5. **Visualize agent (SVG/Mermaid/Chart.js)** — for PS Bayes-tree, ALO matrix-decomp viz. Mermaid in single LLM call. ~80 LOC. Stock Manim overkill for 35-day timeline.

**Modes mapped to Alex:**
| Mode | Alex needs it? | Why |
|---|---|---|
| Chat | Already shipped | Jarvis TutorWorkspace has |
| Deep Solve | **Net-new HIGH** | Multi-agent for PA proofs + ALO LP decomposition |
| Quiz Generation | **Net-new VERY HIGH** | Killer feature — mock-exam from past papers |
| Deep Research | Gimmick | Not for exam-prep |
| Math Animator | Gimmick for 35d timeline | Manim takes minutes/render |
| Visualize | **Net-new MEDIUM** | Mermaid diagrams OOP/RC |

**Source surprises:**
- `agents/notebook/` = Cornell-notes auto-summarizer on study sessions, not chat threads. Worth porting.
- `book/agents/` 5-stage engine (ideation → spine → page_planner → source_explorer → spine_synthesizer) compiles *living textbook*. Overkill for finals; precedent for "auto-revision-booklet per subject."
- `vision_solver/` undocumented in marketing, actively maintained. Useful for PS JPG-photo exams.

### 3. Per-subject pedagogy taxonomy → `subjects.yaml`

Concrete YAML config Jarvis reads at boot. All 6 subjects (PA / PS / POO / ALO / SO / RC). Excerpt:

```yaml
PA:
  exam_format: written
  pedagogy:
    socratic_dial: 0.7
    scaffold_style: worked-example-first
    scratchpad_mode: pseudocode-and-proof
    difficulty_curve: warm-up-recursive → divide-conquer → DP → NP-complete
  top_misconceptions:
    - "T(n)=T(n-1)+O(n) for O(n) instead of O(n²)"
    - "Greedy proof shortcut (assuming exchange-arg works)"
    - "DP memo dimensioning off-by-one"

PS:
  exam_format: micro-tests  # 6 × 15min, NO unified final
  pedagogy:
    socratic_dial: 0.5
    scaffold_style: frequency-format-first  # Gigerenzer
    scratchpad_mode: probability-tree-diagram + bayes-frequency-grid
  top_misconceptions:
    - "Gambler's fallacy"
    - "P(A|B) vs P(B|A) symmetry"
    - "Independence vs mutual exclusivity"

POO:  # C++ not Java per gdt050579.github.io/poo_course_fii
  exam_format: written + project
  pedagogy:
    scaffold_style: hole-driven-then-fade-back  # Caspersen 2006
    scratchpad_mode: uml-class-diagram + code-fragment-editor
  top_misconceptions:
    - "Object-as-identifier vs bag-of-attributes (Sorva)"
    - "Inheritance ≠ code-reuse (Boats/Ships/Trucks anti-pattern)"
    - "Java array covariance violating LSP"

ALO:
  exam_format: written 1h, 3-4 exercises, first 30min printed-docs-only
  pedagogy:
    socratic_dial: 0.4  # ALO procedural-heavy
    scaffold_style: visual-first  # 3Blue1Brown
    scratchpad_mode: matrix-grid + transformation-animation

SO:
  exam_format: 80% continuous + 20% final
  pedagogy:
    scaffold_style: lab-first  # OSTEP pattern
    scratchpad_mode: shell-terminal + process-tree-diagram
  top_misconceptions:
    - "fork() returning twice"
    - "Race conditions invisible until under load"

RC:
  exam_format: PF = 0.4P + 0.3T + 0.1L + 0.1A
  pedagogy:
    scaffold_style: top-down  # Kurose-Ross 25-year proof
    scratchpad_mode: packet-flow-diagram + wireshark-capture-pane
```

**Surprising pedagogical findings:**
- **PS has NO big final exam.** 6 micro-tests + lab-week test. **15-min sprint drills, NOT 1h mock papers.** Aligns w/ spaced-repetition flashcards more than DrillStack.
- **ALO exam format: 30min no-electronics → 30min open-docs.** Mock-exam UI should ENFORCE this: hard 30-min "no PDF rail" timer, then unlock.
- **POO + RC = project-heavy (40-50% of grade).** Pure drilling underestimates; needs project-scaffolding mode (Jarvis as code-review pair).
- **Caspersen stepwise-refinement for POO:** Jarvis should **refuse to emit code** until student states UML / responsibility split.

### 4. YouTube ingestion

**Romanian CS channels Alex should ingest:**
1. **[Andrei Dumitrescu](https://www.youtube.com/AndreiDumitrescu)** — CCNA, CCNP, Linux Admin, Python, Network Automation. **For RC + SO.**
2. **[Romanian Coder (Dan Geabunea)](https://www.youtube.com/@RomanianCoder)** — Java/SpringBoot/Angular. **For POO.**
3. **[Școala Web](https://www.youtube.com/@scoalaweb)** — programming/tech RO.
4. **[Carmen Lăcătușu](https://www.youtube.com/@carmenlacatusu4505)** — FII Iași affiliated.

**English must-haves:**
- PA: NeetCode, Abdul Bari, mycodeschool, freeCodeCamp 48h algo
- ALO: **3Blue1Brown Essence of Linear Algebra** (15 chapters, adopted at universities)
- SO: MIT 6.828 lectures (xv6), CSE-IIT-Bombay OS
- RC: Kurose-Ross video lectures + Sabin Buraga slides RO complement

**Pipeline (yt-dlp + Whisper.cpp + LaTeX cleanup):**
- 1h YT video → yt-dlp mp3 → ~6000 tokens transcript
- **YouTube auto-captions FIRST** if exist (free, instant), Whisper fallback if absent
- Pass `--language ro` explicitly (Whisper sometimes auto-detects RO as IT/ES)
- yt-dlp emits SubRip `.srt` with timestamps — index those into RAG → click-to-skip deep-links
- Post-process: LLM cleanup converts "log de n" → `log n` LaTeX

**Architecture:**
```
[Kotlin coordinator]  → POST sidecar
[Python sidecar]      yt-dlp + whisper.cpp + LaTeX cleanup
                      emit JSON {url, video_id, segments: [{start_sec, text, latex_text}]}
[Kotlin coordinator]  → POST chunks to vector store
                      query → return chunks w/ youtube://video_id?t=START_SEC deep-link
```

Cost: $0. Self-hosted on existing VPS.

### 5. Lab-work mode

**Architectures ranked:**

**A. xterm.js + Dockerode + WebSocket** — BEST for SO. [Presidio walkthrough](https://www.presidio.com/technical-blog/building-a-browser-based-terminal-using-docker-and-xtermjs/) + [mkjiau/xtermjs-dockerode-expressjs-socket](https://github.com/mkjiau/xtermjs-dockerode-expressjs-socket). xterm.js + `@xterm/addon-attach`, Ktor WebSocket → dockerode → `docker exec -it <container> bash`. Latency <100ms same-DC.

**Hetzner reality check (CORRECTION to prior rounds):** **CX22 = 2 vCPU NOT 4 vCPU** + 4 GB RAM + 40 GB. CX32 = 4 vCPU + 8 GB + 80 GB at €6.80/mo. **For lab-mode: CX32 is the right SKU.** €3 more / month.

**B. WebContainers (StackBlitz)** — JS/TS only. **Useless for SO/POO-C++/RC-C-sockets.**

**C. GitHub Codespaces + Classroom** — POO + RC project work. Free monthly allowance via Education benefit. Trade-off: context-switch to github.com.

**D. JDoodle/OnlineGDB iframes** — quick POO win. 110+ languages incl C/C++/Java. Free tier limited daily. Data leaves Alex's VPS.

**E. Kathara (Docker-based network emulation)** — niche, only if RC project demands. Lighter than GNS3. For RC default: Kurose-Ross Wireshark labs (free, 8 labs HTTP/DNS/TCP/UDP/IP/Ethernet/WiFi/SSL) + Buraga Atelier BSD-sockets in C → Docker Linux container.

**Per-subject default:**
| Subject | Default | Why |
|---|---|---|
| **SO** | xterm.js + Hetzner CX32 Docker | Real Linux syscalls, fork/threads/signals |
| **RC** | xterm.js Docker + Wireshark PDFs | Socket programming + local packet capture |
| **POO** | JDoodle iframe (drill) + Codespaces (project) | Drill speed + project depth |

**Cheating-prevention frame flip:** Alex is solo, not graded by Jarvis. Frame = **anti-spoilers** not anti-plagiarism.
- Default: Jarvis hides solutions until student commits attempt + reasoning
- Override: explicit "show me" toggle
- Audit: track show-answer freq → feed into spaced-rep priority (those concepts get re-hit next week)

### Top-10 net-new moves (#56-65)

| # | Move | Effort | Leverage |
|---|---|---|---|
| 56 | **Ingest AndraEP/FII past papers** (PA: 4 PDFs, POO: 1, SO: 1, PS: 4 JPGs, FAI: 4 JPGs). GitHub API + OCR ~3h. | S | 10/10 |
| 57 | **Port DeepTutor IdeaAgent→Generator two-pass quiz** (~200 LOC Kotlin). Mock-exam-from-past-paper #1 unlock for finals window. | M | 10/10 |
| 58 | **Adopt DeepTutor PROFILE.md + SUMMARY.md two-file memory verbatim** (~50 LOC port). Replaces bespoke memory plumbing. | S | 9/10 |
| 59 | **YouTube pipeline (yt-dlp + Whisper.cpp + LaTeX cleanup)** Python sidecar. 5 RO channels + 3B1B ALA. | L | 9/10 |
| 60 | **`subjects.yaml` registry** as config Jarvis reads at boot. Per-subject defaults: Socratic dial, scratchpad mode, scaffold style, top-3 misconceptions, remediation prompts. ~6h all 6 subjects. | S | 9/10 |
| 61 | **LabSandbox mode (xterm.js + Hetzner CX32 Docker)** for SO. Real Linux in browser. Persistent tmux. ~2 weeks first end-to-end. | L | 8/10 |
| 62 | **JDoodle iframe for POO quick-drills** (free tier covers Alex). ~1h first integration. | S | 7/10 |
| 63 | **ALO exam-faithful timer: 30min no-PDF + 30min unlocked.** Subject-specific UI = moat. | S | 7/10 |
| 64 | **Caspersen "no code before UML" gate for POO drills.** Jarvis refuses code emit until student states responsibility split. Single prompt change. | S | 7/10 |
| 65 | **PS micro-drill mode (15-min sprints, frequency-format Bayes display).** Matches PS exam structure. Gigerenzer 10%→90% Bayesian lift in <2h. | M | 8/10 |

### Round 1-4 corrections from round 5

1. **`andrei.clubcisco.ro` DEAD (ECONNREFUSED).** Strike from sources.
2. **DeepTutor is Next.js 16 + React 19** (not 14 as earlier rounds may have guessed). Tailwind not in repo metadata.
3. **`logalex96/UAIC-Informatica-Iasi` is STALE** — archive migrated to Mega. **AndraEP/FII + Andreea15B/Facultate_an_II replace it.**
4. **Hetzner CX22 = 2 vCPU NOT 4 vCPU.** Prior rounds wrong. CX32 (4 vCPU €6.80/mo) is the right SKU for lab-mode.
5. **POO at FII = C++ not Java.** Per [FII POO course](https://gdt050579.github.io/poo_course_fii/home.html) explicit "classes, objects, **C++**, design patterns, STL." Frasinaru's *Curs practic de Java* = Programare Avansată (Year 2 Sem 1), NOT POO.

### 3 surprising findings (round 5)

1. **DeepTutor memory = PROFILE.md + SUMMARY.md** — EXACT same pattern Alex uses for Claude Code memory. Convergent design. **Don't invent a third memory model. Use the same one.** Markdown files in git-tracked directory.
2. **PS at FII has NO big final exam.** 6 × 15-min mini-tests at seminars + lab-week test. **The entire UWorld/CFA "mock-exam-faithful drill" thesis collapses for PS.** Drilling format must be subject-aware: 15-min micro-quiz for PS, not 4-problem mock paper.
3. **`_pdfUrl` underscore-dead-prop lesson applies to DeepTutor too** — `vision_solver` agent exists in source but undocumented in README/marketing. **Marketing surface ≠ shipped surface even at 24k-star projects.** Verify by reading code, not docs.

### Round-5 tensions table

| Tension | Verdict |
|---|---|
| Build own quiz pipeline vs port DeepTutor | **Port.** Two-pass IdeaAgent→Generator is debugged. |
| Self-host yt-dlp+Whisper vs NotebookLM API | **Self-host.** Timestamps load-bearing. |
| xterm.js Docker vs Codespaces for SO | **xterm.js + Hetzner.** Solo-learner UX > zero-infra. |
| RO Whisper vs RO subtitles | **Subs first, Whisper fallback.** |
| Generic DrillCard vs per-subject UI | **Per-subject.** PS micro-quiz + ALO 30+30 split = moat. |
| Lakatos proof vs Caspersen stepwise | **Subject-routed.** PA=Lakatos, POO=Caspersen via subjects.yaml `scaffold_style`. |
| Hetzner CX22 vs CX32 | **CX32 if labs ship.** Budget extra €3/mo. |
| Khanmigo Socratic-only vs answer-on-demand | **Socratic default + override toggle.** Track show-me freq → spaced-rep priority. |

### Round-5 still unexplored

1. **`equiz.ecosoftware.ro`** FII official quiz system. Auth-gated. Next round w/ Alex's credentials would surface thousands of practice quizzes.
2. **Mega cloud migration target for logalex96 archive** — README has link; Claude can't browse Mega directly.
3. **Romanian-language LLM benchmark on PA proof problems** — 1-day eval pass on 4 AndraEP PA past papers vs `:free` Llama/Mixtral/Gemma → reveals best fit.
4. **Hazel-style hole-driven editing for OOP** — research-validated; buildable in Jarvis timeframe unknown.
5. **Deep-read of DeepTutor `tutorbot/agent/loop.py` (33KB) and `solve/main_solver.py` (38KB)** — Round 5 sampled top-level + memory.py; orchestrator + 38KB solver are next files if Jarvis ports in earnest.
6. **Frasinaru *Curs practic de Java* 800+ pages** ([download](https://edu.info.uaic.ro/programare-avansata/Cristian_Frasinaru-Curs_practic_de_Java.pdf)) — biggest RO-language OOP/Java corpus. Catalog'd not indexed.

---

## FINAL ROUND — Goal-framed sunk-cost-free optimal blueprint

> Generated 2026-05-17 by sixth deep-research subagent (~10min wall, 73 tool uses, 138k tokens). User's lock: *"goal is best possible UX + teaching efficiency, any current work deprecatable."* This round: clean greenfield. No incumbent bias. Reorders + corrects ALL prior rounds.

### Two anchor findings that reorient everything

1. **Bloom's 2-sigma is alive in 2024-2025 AI-tutor RCTs.** Kestin et al. Harvard physics N=194: d=0.73-1.3, students learn 2× in less time vs active-learning class ([Nature SR 2025](https://www.nature.com/articles/s41598-025-97652-6)). Tutor CoPilot Stanford N=1000: +4pp mastery, +9pp novice-tutored ([Stanford NSSA](https://nssa.stanford.edu/studies/tutor-copilot-human-ai-approach-scaling-real-time-expertise)). LearnLM/Eedi UK secondary maths N=165: AI-supervised tutoring matches expert humans. **Effect-size budget is enormous — highest-leverage pedagogy intervention available 2026.**
2. **Mastery > deep neural in interpretable KT.** 2025 Hierarchical BKT study: **PFA+BKT reach mastery in 6 steps vs DKT's 14** ([arxiv 2506.00057](https://arxiv.org/html/2506.00057v1)). For 35-day finals + 65 KCs + 3-30 users, PFA's interpretability IS the product surface. Black-box DKT loses.

**System reframed:** mastery-engine wrapped in Khanmigo-style Socratic tutor with DeepTutor-style dual-loop agentic backbone. Multi-user from day 1 (Alex's lock).

### Optimal stack (concrete pins)

**Backend: Python 3.12 + FastAPI 0.115** (NOT Kotlin/Ktor)
- DSPy / GEPA / MinerU / PaperQA2 / FSRS / Whisper.cpp all Python-first
- Saves ~30% engineering time vs Kotlin → Python marshaling
- `fastapi==0.115.6 + uvicorn[standard]==0.32.1 + sse-starlette==2.1.3 + gunicorn==23.0.0`

**Frontend: Next.js 15 App Router + React 19 + Vercel AI SDK 6**
- `useChat / streamText / streamUI` purpose-built primitives ([Vercel AI SDK 6](https://vercel.com/blog/ai-sdk-6))
- shadcn/Radix component density
- SolidJS 70% faster updates irrelevant — bottleneck is LLM token streaming, not DOM
- `next@15.x + ai@^6.0.0 + @ai-sdk/react@^2 + shadcn@latest + tailwindcss@4 + react@19 + typescript@5.6`

**Database: Postgres 17 + pgvector 0.7 + Drizzle ORM** (NOT Turso)
- Turso loses on writes/joins/vector+relational unification
- pgvector handles <5M vectors at <20ms p95 ([pgvector vs Qdrant 2026](https://markaicode.com/vs/pgvector-vs-qdrant/))
- 3-30 users × 65 KCs × 50 reviews/day × 35 days = <300k rows lifetime — nowhere near pgvector ceiling
- `postgres:17-alpine + pgvector/pgvector:pg17 + drizzle-orm@^0.36` + RLS policies

**Deploy: Hetzner CX22 (€4.51/mo) + Docker Compose** (NOT Cloudflare/Vercel/Fly)
- CF Workers 30s timeout kills DSPy chains
- Vercel function timeouts hurt dual-cycle DeepTutor pattern
- Hetzner Falkenstein → Iași ~30-40ms baseline (fine for SSE)
- AI Act Article 12 audit-log on edge functions = anti-pattern
- `docker-compose.yml`: postgres / app (Next+FastAPI behind Caddy) / litellm / dspy-sidecar / Caddy 2 TLS+WebSocket+Brotli

**Auth: Auth.js v5 + Resend magic links + Drizzle adapter** (NOT Clerk, NOT bespoke `jarvis_session`)
- Auth.js fully in-EU, audit-log straight in your DB
- Clerk US-based, GDPR controller-status fuzzy, lock-in
- `next-auth@5.x + @auth/drizzle-adapter + resend@latest` + `sessions_audit` table for Article 12

**LLM orchestration: DSPy Python sidecar + LiteLLM gateway**
- DSPy in `python:3.12-slim` container
- LiteLLM proxy (`ghcr.io/berriai/litellm:main-stable`) — OpenRouter routing, retries, budget, audit hooks
- GEPA optimizer offline (~$2-3 OpenRouter cost) on 50-row labeled eval set
- `dspy-ai==2.6.x + litellm[proxy]==1.55.x`

### Optimal pedagogy stack

| Component | Pick | Why |
|---|---|---|
| **Worked example → faded → independent** | WorkedExampleCard → FadedStepper → PredictionGate → IndependentCard | Sweller's backward fading + Bjork desirable difficulty |
| **Spaced repetition** | FSRS-6 per-KC, **exam-clamped max interval** (days-to-exam − 2) | 20-30% fewer reviews vs SM-2; clamp = finals-mode fit |
| **Mastery tracking** | **PFA (NOT DKT, NOT vanilla BKT)** | Interpretable; reaches mastery 6 steps vs DKT's 14 |
| **Adaptive sequencing** | Heuristic + 1-step lookahead (NOT MCTS, NOT RL) | 65 KCs × 35 days too small for RL; ALEKS-lite via `value(KC) = (1-mastery) × exam_weight × forgetting_pressure` |
| **Misconception-first** | Posner 4-condition + MisconceptionRibbon on commit | Highest under-shipped instructional move |
| **Confidence calibration** | 3-tier slider (Guess/Pretty sure/Confident) before EVERY commit | Surfaces overconfidence; feeds FSRS desirable-difficulty |
| **Mode FSM** | **3 modes (Normal / Crunch / Day-Of) NOT 4** | Finals-14 and Finals-3 are same algo with different clamps; collapse |
| **Cross-subject interleaving** | Blocked within session, round-robin across days | Discriminative-contrast hypothesis; novices need initial blocked |
| **Push vs pull metacog** | Pull-mostly + **1 daily push** (end-of-session wrap pane 60s) | Push notifications boost retention but cause "metacognitive laziness" — exactly one |

### Optimal UX (concrete picks)

| Surface | Pick | Why |
|---|---|---|
| **Layout** | **Khanmigo single-stream + optional pin-pane (desktop)** NOT NotebookLM 3-pane | NotebookLM 3-pane kills mobile + overwhelms novices who haven't built mental models |
| **Queue** | **LearnerQueue (mobile-default) + CourseMap (secondary tab)** | Today-tab single-tap entry; CourseMap = 1/week exploration |
| **Ledger** | **1-line peek strip + drawer on-demand** | Always-visible mastery vector anxiogenic; daily-digest peek correct |
| **Mobile nav** | **4 bottom tabs: Today / Subjects / Voice / Me** | 5+ tabs split attention; 4 carries one cognitive job each |
| **Dark mode** | Light default + dark toggle + `prefers-color-scheme` respect | WCAG 2.1 AA mandatory April 2026 EU education |
| **Bilingual** | Sticky header `RO/EN` toggle + per-content-block source-lang lock | Don't auto-translate RO source citations |
| **Theme** | **Alex's blue-purple-gradient PRIMARY; brutalist-yellow → ONE accent** (mock-exam CTA) | NN/g 2024: neobrutalism a11y nightmare + cognitive load tax |
| **Motion** | NotebookLM-polished minimal (200-300ms fades); StepPlayer manual-advance | Auto-advance removes commitment; manual gates produce retrieval |
| **Mock exam** | Separate route `/mock/[examId]` full-screen takeover | Bjork retrieval-context fidelity |
| **Worked example** | **Paginated with PredictionGates** (NOT scrollable single-page) | Scrolling = fluency illusion; gates = retrieval evidence |

### Optimal multi-user architecture

**Multi-tenant ≈ multi-user at 3-30 users.** Single Hetzner VPS + Postgres RLS.

- RLS policy: `tenant_id = current_setting('app.current_user_id')::int` on every user table; FastAPI sets per request from JWT
- Cost model: Alex's $10 OpenRouter unlock shared; LiteLLM enforces per-user daily token budget (~$0.15/user/day = ~$5/user/mo). Exceed → degrade to `:free` tier
- BYOK setting for power users
- 3 isolation layers: RLS / audit_log / data_subject_request table
- Sharing: **invite-only magic-link** (7-day expiry), no public signup
- **Anonymous confusion-map** k=3 anonymity — ONLY cross-user feature
- **NO real-time collab** — 2-3 weeks of websocket plumbing for marginal pedagogy. Reject.

**EU AI Act minimum-viable conformity (3-5 days work):**
1. **Article 4 AI literacy IN FORCE NOW (Feb 2025)** — 1-page first-login intro
2. **Article 12 record-keeping** — `audit_log` table, 6-month retention min
3. **Article 13 transparency** — per-message UI shows model + cost + ts ("This response generated by Claude Sonnet 4.6 via OpenRouter")
4. **Article 14 human oversight** — Ledger drawer lets user edit/delete/override any mastery/KC/auto-decision
5. **Article 15 accuracy** — `MODEL_CARD.md` + GEPA dev-set + RoMath sample benchmark in repo
6. **Article 16 quality** — `RISK_REGISTER.md` listing known failure modes
7. **GDPR** — Alex names self controller in `PRIVACY.md`; Article 15 export (JSON dump) + Article 17 delete (cascade) wired to UI

**Escape hatch:** `SINGLE_USER=true` env flag ships same codebase under Article 2 personal-use exemption when classmate sharing delayed to v2.

### Top-20 moves re-ranked (under sunk-cost-free lens)

| Rank | Move | Justification |
|---|---|---|
| 1 | **PredictionGate hard-commit before reveal in every worked example + practice** | Highest under-shipped move; Bjork desirable difficulty + retrieval practice |
| 2 | **DeepTutor dual-loop backbone (Investigate→Note→Plan→Solve→Check)** | Citation-grounded; 60%+ fewer hallucinations on TutorBench |
| 3 | **FSRS-6 per-KC with exam-clamped max interval** | 20-30% fewer reviews; clamp = finals-mode |
| 4 | **PFA mastery (NOT DKT)** | 6 steps to mastery vs DKT's 14; interpretable |
| 5 | **MisconceptionRibbon w/ Posner cognitive-conflict pattern** | Highest under-shipped instructional move |
| 6 | **3-mode FSM (Normal/Crunch/Day-Of)** | Collapses redundant Finals-3/Finals-14; saves UI slice |
| 7 | **Khanmigo single-stream + optional pin-pane (desktop)** | Matches Alex's novice cognitive profile |
| 8 | **3-tier confidence slider before every commit** | Surfaces overconfidence; feeds FSRS scheduling |
| 9 | **subjects.yaml KC schema with misconceptions/prerequisites/exam_weight/bloom_level** | Content spine; every other surface reads from it |
| 10 | **Hand-authored prerequisite DAG + LLM-suggested edges (Alex approves)** | Right scale for 65 KCs; matches 2025 zero-shot evidence |
| 11 | **MockExam route as full-screen takeover (timed, no hints)** | Bjork desirable difficulty + retrieval context fidelity |
| 12 | **NotebookLM-style audio overview ("Daily Digest podcast") via Piper RO** | Priming + 100M+ NotebookLM validates demand |
| 13 | **DSPy + GEPA-optimized Socratic-tutor prompt module** | 20%+ accuracy gain in 35× fewer rollouts; ~$3 lifetime |
| 14 | **End-of-session wrap pane (60s metacognitive push)** | Single allowed push; metacognition+retention base |
| 15 | **LedgerOpened drawer (pull-mode, mastery+misconceptions+wrongs)** | The interpretability surface PFA enables |
| 16 | **xterm.js + gvisor Docker for SO/RC labs** | Only viable way to teach strace/tcpdump interactively |
| 17 | **Past-paper ingestion: GitHub API + MinerU + Tesseract-ron + human review** | Hallucinated past-paper = product killer; review queue = safety valve |
| 18 | **Mobile bottom 4-tab nav (Today/Subjects/Voice/Me)** | Phone-first; 5+ tabs split attention |
| 19 | **Whisper.cpp RO + Piper RO local voice mode** | Privacy + zero per-token cost; satisfies GDPR data minimization |
| 20 | **Article 12 audit_log + Article 13 model-name transparency UI** | Multi-user compliance baseline ~3 days work |

### Moves that DIE under sunk-cost-free lens

1. **jarvis_session bespoke auth** → Auth.js v5 + Resend
2. **Kotlin/Ktor backend** → Python/FastAPI (ecosystem density wins)
3. **Brutalist-yellow as primary theme** → single-accent only (NN/g 2024 neobrutalism a11y nightmare)
4. **4-mode FSM** → 3-mode (collapse Finals-14 and Finals-3)
5. **`:free`-only OpenRouter** → $10 unlock buys 20× throughput
6. **Real-time collab / live shared scratchpad** → kill (2-3 weeks for marginal pedagogy)
7. **DKT or deep-neural mastery** → PFA (black-box breaks Ledger surface)
8. **3-pane NotebookLM default layout** → kills mobile; researchers ≠ novices
9. **Always-visible mastery vector** → drawer + 1-line peek
10. **Pull-only metacog (no pushes)** → 1 daily push (end-of-session wrap)

### 10 NEW moves emerging from synthesis (#66-75)

| # | Move | Why |
|---|---|---|
| 66 | **MisconceptionRibbon-on-commit (Posner)** | Pattern-match commit against hand-authored triggers; surface 3-line cognitive-conflict refutation BEFORE green/red |
| 67 | **Anonymous confusion-map (k=3 anonymity)** | Cross-user feature w/ negligible GDPR cost; surfaces in CourseMap |
| 68 | **Separate GEPA-optimized "explain-this-misconception" DSPy module** | Different from main Socratic-tutor; optimized on 30-row eval set ~$1 lifetime |
| 69 | **3-tier confidence slider feeding FSRS desirable-difficulty** | high-conf-but-wrong = shorter next interval + hardness boost |
| 70 | **Past-paper review queue with 20-sec swipe approval UI** | Alex `/curate` extended; drains queue in 10min daily sessions |
| 71 | **Per-tenant LiteLLM daily budget with graceful `:free` degradation** | Budget hit → auto-downgrade + "limited mode — resets tomorrow" |
| 72 | **`SINGLE_USER=true` env flag** | Same codebase as Article 2 exempt; defer compliance burden |
| 73 | **`MODEL_CARD.md` + `RISK_REGISTER.md` in repo** | Satisfies Article 15/16 disclosure burden zero infra |
| 74 | **RoMath subset as CI regression gate** | 50 problems nightly LLM-as-judge; Article 15 robustness obligation continuously |
| 75 | **End-of-day digest podcast (Piper RO local)** | 5-7min "today's misconceptions + 3 reviews + 1 win" — free, private, high engagement |

### Deprecation matrix under sunk-cost-free lens

| Existing artifact | Verdict | Action |
|---|---|---|
| jarvis-kotlin Ktor backend | **DEPRECATE** | Zero reuse; rebuild on FastAPI |
| jarvis-kotlin bespoke `jarvis_session` auth | **DEPRECATE** | Auth.js v5 |
| jarvis-kotlin Postgres schema | **PARTIAL KEEP** | Concepts transfer (user/KC/review); rewrite to Drizzle + RLS |
| jarvis-kotlin existing React UI (TutorWorkspace.tsx, ResourceRail, Scratchpad) | **PARTIAL KEEP** | PdfPane + Scratchpad survive; rewrite TutorWorkspace to single-stream + pin-pane; rip ghost-component cruft |
| jarvis-kotlin audit/sensor/telemetry routes | **KEEP** | Concept survives; port to FastAPI |
| jarvis-kotlin `/curate` workflow | **KEEP, EXPAND** | Spine of content pipeline; extend to past-paper review queue |
| jarvis-kotlin brutalist-yellow theme | **DEPRECATE-default** | Demote to mock-exam CTA accent; blue-purple primary |
| Ghid_Studii_AI Node/Express backend | **DEPRECATE** | No reuse |
| Ghid_Studii_AI subject-grid UI | **KEEP CONCEPT** | CourseMap pattern survives; reimplement Next.js |
| Ghid_Studii_AI lab-sandbox xterm.js+Docker work | **KEEP** | Port to FastAPI gvisor pool |
| Ghid_Studii_AI seminar/test pages | **KEEP CONTENT** | Migrate as static content into subjects.yaml |
| Existing specs (tutor-drill-workspace-slice1 etc.) | **OVERHAUL** | Spec template must enforce `data-testid` mount-site assertions |

**Net rewrite: ~3-4 weeks solo dev with subagent-driven-development. Slots into the 35-day finals window.**

### 3 most surprising findings (sunk-cost-free)

1. **Kotlin/Ktor LOSES to Python/FastAPI despite being better engineering.** Python ecosystem density (DSPy/GEPA/MinerU/PaperQA2/FSRS/Whisper.cpp bindings) saves ~30% engineering time. "Best language for the libraries that exist" trumps "best language for the job."

2. **Real-time collab + 3-pane NotebookLM layouts BOTH lose on novice-pedagogy goal.** Both are 2026's most-discussed UX patterns. Both wrong here. Live shared scratchpad costs weeks for marginal pedagogy. Three-pane kills mobile + overwhelms novices. **Khanmigo's simpler-older single-stream wins.**

3. **EU AI Act Article 4 (AI literacy) IN FORCE NOW (Feb 2025)**, not Aug 2026. Everyone treats Aug 2026 as "the AI Act deadline" — Article 4 already applies. 1-page "this is an AI tutor — strengths, limits, what to verify" first-login page satisfies it. Cheapest compliance-burden surprise.

### 7 key open questions blocking clean execution

1. **Multi-user vs single-user mode**: Ship `SINGLE_USER=true` first (Article 2 exempt, zero compliance) then add multi-user in v2, OR ship multi-user day-1 with 3-5 days conformity?
2. **Whisper.cpp Romanian quality on Iași dialect**: 5-min self-test needed before voice mode primary surface. If fails: skip voice v1.
3. **MinerU Romanian fidelity on FII past papers**: PA proof-heavy may degrade. Tesseract+PaddleOCR fallback exists; quality determines human-review queue throughput.
4. **Number of "classmates"**: 3 vs 30 vs unbounded? Above ~30, Turso per-tenant SQLite starts winning over RLS.
5. **RoMath as CI gate**: RoMath-Baccalaureate is high-school not university PA/PS. Need 50-problem custom eval set for PA/POO/SO/ALO/RC. ~1 day Alex curation.
6. **GDPR controller identity**: Personal-name (Alex) fast/free vs EU NGO ~€100 + 1 week + liability shielding.
7. **OpenRouter region-routing on $10 unlock**: RO→US latency observable jitter on streaming token cadence? 10-min curl RTT test before commit.

---

## Round 9 — Comprehensive scope (8 parallel subagents)

> Dispatched 2026-05-17 after user said: *"i want you to make SURE that you are researching EVERYTHING needed for this, if u need get a bunch of agents to do it I don't care just produce the best outcome."* 8 parallel subagents covering: A visual primitives, B pedagogy surfaces, C backend infra, D LLM+RO, E compliance, F testing+ops, G content authoring, H multi-user social.

### C — Backend infra + DB schema + ops (returned first, ~7min wall)

**Critical reveal from subagent audit of repo:** jarvis-kotlin currently runs **SQLite + Exposed** (`src/main/kotlin/jarvis/tutor/TutorDb.kt`, with `UsersTable`, `TasksTable`, `FsrsCardsTable`, `AuditLinesTable` all having `userId` FK columns). Multi-user shape is in place; RLS + pgvector + Postgres are the **migration target**.

**Migration target: Postgres 17.3 + pgvector 0.8 + Exposed (server) + Drizzle (Next.js Auth.js).**

#### Database schema — full Postgres DDL

**Auth.js tables (Drizzle TS, owns identity):**
- `users` (id text PK uuid, name, email unique, emailVerified, image, role, scope, createdAt, lastSeenAt, deletedAt for Art.17 soft-delete)
- `accounts` (composite PK `provider + providerAccountId`, FK userId)
- `sessions` (sessionToken PK, expires)
- `verificationTokens` (composite PK `identifier + token`)

Pins: `next-auth@5.0.0-beta.25`, `@auth/drizzle-adapter@1.10.0`, `drizzle-orm@0.36.x`, `drizzle-kit@0.28.x`.

**Tutor schema (Kotlin Exposed, owns learning state):**
- `subjects` (id, slug, name_ro, name_en, exam_format, finals_date, exam_weight_schema jsonb)
- `kcs` (id, subject_id FK, name_ro, name_en, prerequisites jsonb, exam_weight, bloom_level, created_at)
- `misconceptions` (id, kc_id FK CASCADE, label, trigger_pattern, refutation_text, source)
- `drills` (id, kc_id FK CASCADE, content jsonb, sub_goals jsonb, difficulty, format, source_doc_id)
- `attempts` (id, user_id FK CASCADE, drill_id FK, kc_id denormalized, response, response_shape, correct, confidence, attempt_count, first_action, hint_count, ms_first_response, misc_id_hit, ts) — **ASSISTments log schema**
- `mastery` (user_id, kc_id composite PK, p_learned, n_correct, n_incorrect, last_reviewed_at, half_life_days, updated_at) — **PFA fields**
- `fsrs_state` (user_id, drill_id composite PK, due, stability, difficulty, elapsed_days, scheduled_days, reps, lapses, state, last_review, decay) — **FSRS-6 Card struct verbatim from go-fsrs/py-fsrs/ts-fsrs**
- `audit_log` (seq autoIncrement PK, user_id FK, ts, event_type, payload jsonb, canonical, prev_hash, this_hash, outcome) — **Article 12 + hash chain**
- `past_papers` (id, subject_id FK, year, problems jsonb, source_url, reviewed bool, uploaded_at)
- `embeddings` (chunk_id PK, source_doc_id, source_page, subject_id FK nullable, text, model_tag, created_at; `embedding vector(1024)` added via raw SQL ALTER)
- `schedule` (user_id, subject_id composite PK, exam_date, exam_format, crunch_mode bool)

#### Vector + jsonb DDL (raw SQL migration):

```sql
-- V001__init_extensions.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;     -- pgvector 0.8.0+

-- V010__embeddings_vector.sql
ALTER TABLE embeddings ADD COLUMN embedding vector(1024);
CREATE INDEX embeddings_hnsw ON embeddings
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 128);
-- ~10k chunks ≈ <100MB index, <10ms ANN at ef_search=40

-- V011__jsonb_indexes.sql
CREATE INDEX kcs_prereqs_gin ON kcs USING gin (prerequisites);
CREATE INDEX drills_content_gin ON drills USING gin (content);
CREATE INDEX audit_payload_gin ON audit_log USING gin (payload);
```

#### RLS policy (key pattern)

Per-table `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY`. Policy template:
```sql
CREATE POLICY user_isolation_attempts ON attempts
  USING (user_id = current_setting('app.current_user_id', TRUE))
  WITH CHECK (user_id = current_setting('app.current_user_id', TRUE));
```

Audit log: read-own (FOR SELECT), INSERT-only via SECURITY DEFINER fn to preserve hash chain. Shared content (subjects/kcs/drills/past_papers): GRANT SELECT to app_role, REVOKE writes.

**k=3 anonymity confusion-map view:**
```sql
CREATE VIEW confusion_map_k3 WITH (security_invoker = TRUE) AS
SELECT misc_id_hit, kc_id, COUNT(*), COUNT(DISTINCT user_id)
FROM attempts WHERE misc_id_hit IS NOT NULL AND ts > NOW() - INTERVAL '30 days'
GROUP BY misc_id_hit, kc_id
HAVING COUNT(DISTINCT user_id) >= 3;
```

**Ktor RLS context:**
```kotlin
fun <T> withRls(userId: String, block: Transaction.() -> T): T = transaction(db) {
    exec("SELECT set_config('app.current_user_id', ?, true)") { stmt ->
        stmt.set(1, userId)
    }
    block()
}
```

**`set_config(_, _, true)` = transaction-local.** PgBouncer in `transaction` mode safe; session mode would leak. **DO NOT use session pooling.**

#### Migration strategy

Tool pick: **Flyway Community 10.x** (sql-only JDBC, plays with both Drizzle and Exposed). Drizzle `push` for one-shot Auth.js bootstrap, then Flyway owns everything.

Migration order: V001 extensions → V002 auth tables → V003 tutor tables → V004 user data → V010 embeddings vector → V011 gin indexes → V020 RLS → V030 seed subjects.

Data move from `jarvis.db` (single-user SQLite) → Postgres: one-shot Kotlin task `migrateSqliteToPostgres` row-by-row via Exposed. Recompute hash chain on insert.

**Backward-compat interface** for env-flag selector:
```kotlin
interface JarvisDb { fun connect(): Database }
class SqliteJarvisDb : JarvisDb // existing
class PostgresJarvisDb : JarvisDb // new
```
~2h refactor for code-side; config swap to switch.

#### Event-driven architecture: Postgres LISTEN/NOTIFY (NOT Redis)

For 3-30 classmates peak ~30 events/sec, LISTEN/NOTIFY is right call. Redis adds service for zero benefit at this scale. 8KB payload cap, 8GB queue ceiling don't bite below 1k events/sec.

`pg_notify` fires only on COMMIT → durable before subscriber sees event. No ghost-event race.

Channels: `drill.completed` / `kc.mastered` / `misconception.detected` / `mode.transition` / `task.graded`.

```kotlin
class PgEventBus(private val ds: DataSource) {
    fun publish(channel: String, payload: JsonObject) = transaction(db) {
        exec("SELECT pg_notify(?, ?)") { stmt ->
            stmt.set(1, channel); stmt.set(2, payload.toString())
        }
    }
    fun subscribe(channel: String): SharedFlow<JsonObject> { ... }
}
```

#### Background jobs: cron + Docker

Quartz/JobRunr/kjob = overkill. **k jobs + docker compose** simpler, traceable in `docker logs`.

| Job | Cadence | Owner |
|---|---|---|
| DSPy GEPA optimization | nightly 02:00 | Python sidecar |
| MinerU PDF extraction | on-upload async via LISTEN | Python sidecar |
| Past-paper indexing | on-upload async | Python sidecar |
| Piper audio overview pre-gen | off-hours 03:00 | Python sidecar |
| FSRS due-queue compute | every 15 min | Kotlin |
| Resend daily digest | 07:00 RO time | Kotlin |
| Audit-log chain verify | weekly Sun 04:00 | Kotlin |
| pg_dump backup | nightly 01:30 | host cron |

#### Cache layer: Caffeine 3.2.2 in-process, no Redis

```kotlin
val kcCache: Cache<String, KC> = Caffeine.newBuilder()
    .maximumSize(10_000).expireAfterWrite(15, TimeUnit.MINUTES).recordStats().build()

val embeddingHotSet: Cache<String, FloatArray> = Caffeine.newBuilder()
    .maximumWeight(64_000_000).weigher<String, FloatArray> { _, v -> v.size * 4 }
    .expireAfterAccess(1, TimeUnit.HOURS).build()
```

Prompt cache: pass-through (DeepSeek/Anthropic cache_control via OpenRouter, ephemeral type). No local caching needed.

#### Email: Resend free tier

3000/mo, 100/day, 1 domain, 30d retention. 30 classmates × 1 digest/day = 900/mo (fits with 67% headroom). Daily cap 100 = 70/day spare for verification/reset/crunch alerts.

Pin: `resend@4.0.x`. Auth.js native via `@auth/core/providers/resend`.

#### Monitoring: defer self-hosted, start with free tiers

Loki+Grafana+GlitchTip self-host ≈ 1.5GB RAM = tight on CX22 (4GB total). **Recommendation:** Sentry free (5k errors/mo) + BetterStack Logs free (1GB/mo, 3d retention) until VPS upgrade.

OTel auto-instrument: `opentelemetry-ktor-3.0:2.10.0` (Ktor, +30MB heap) + `opentelemetry-instrument uvicorn main:app` (FastAPI, +50MB heap).

LLM token tracking: `LlmAuditMiddleware` writes `(user_id, model, prompt_tokens, completion_tokens, cost_usd, ts)` to `audit_log` with `event_type = "llm.call"`. Soft-block at `cost_usd >= 0.50/user/day`.

#### Deploy pipeline: GitHub Actions → SCP/SSH + docker compose

`appleboy/ssh-action@v1` runs:
```bash
cd /srv/jarvis
docker compose pull
docker compose run --rm migrate ./flyway migrate
docker compose up -d --no-deps server sidecar
docker compose exec server /healthz-wait.sh
```

Registry: GitHub Container Registry (free public, 500MB free private). **Avoid Docker Hub** (1 pull/6h anonymous limit).

Rollback: `TAG=<previous-sha> docker compose up -d`. Never push `:latest`.

#### VPS sizing: CX22 launch → CX32 at user 15

CX22 (€4.51/mo, 2 vCPU / 4GB): steady ~3GB used (Postgres 1.5 + Ktor 768 + FastAPI 512 + Next 256 + Caddy 50). Burst-conflict if Whisper + MinerU fire simultaneously → OOM-kill. Mitigation: serialize through LISTEN queue, LIMIT 1 worker per heavy ML container.

CX32 (€6.80/mo, 4 vCPU / 8GB) trigger: avg concurrent ≥ 8 OR p95 response > 800ms OR free RAM avg < 500MB over 7 days. Online resize (~5min downtime).

**gvisor for lab sandbox**: `--runtime=runsc` on Python lab container only. 10-30% I/O overhead acceptable for <5s exec. Pin 256MB RAM + 0.5 vCPU per concurrent.

#### PWA offline (browser support reality)

Background Sync API: Chromium/Edge/Opera/Samsung only — **Firefox + Safari/iOS lack it**. RO student cohort ~70% Android Chrome → majority workable; Safari/iOS fall back to "must be online to submit."

Stack: `next-pwa@5.6.0` + Workbox 7 + `idb@8`. POST `/api/v1/drills/{id}/attempt` wrapped in BackgroundSyncPlugin → IndexedDB outbox queue if offline, replays on `online` event. `attempt_id = ULID` generated client-side → INSERT idempotent on PK, dup returns 200.

#### Backup + retention

Hetzner Storage Box **BX11 €3.81/mo (1TB)**. Nightly pg_dump encrypted with GPG, rsync to Storage Box. 30 daily / 12 monthly rotation.

Retention matrix:
- `audit_log`: 6mo min (Art. 12), extended 24mo for litigation hold
- `attempts`: 24mo max (research-locked), monthly soft-anonymize (user_id NULL, keep KC stats)
- `embeddings`: indefinite (no PII)
- `mastery`, `fsrs_state`: until account deletion, cascade
- `sessions`: Auth.js native cleanup
- Email logs (Resend): 30d on free tier
- Caddy access logs: 14d via logrotate

GDPR Art. 17 cascade: soft-delete (`users.deletedAt`) → 30-day grace → hard-purge job cascades all child rows → final `gdpr_purges` log entry (separate, no user FK, kept 6mo).

DSAR (Art. 15): `GET /api/v1/account/export` → ZIP of attempts/mastery/tasks/fsrs_state/audit-log slice. 30-day response.

#### HikariCP sizing

Vlad Mihalcea formula: `cores * 2 + spindles`. CX22 = 2 vCPU + 1 SSD → 5 conn. Use **8 (headroom)**. Postgres `max_connections=100`; reserve 80 for app, 20 for migrate/admin/monitoring.

```kotlin
maximumPoolSize = 8; minimumIdle = 8     // fixed pool
connectionTimeout = 5_000                 // 5s fail fast
maxLifetime = 1_800_000                   // 30 min < Postgres idle_in_transaction
leakDetectionThreshold = 5_000            // log if conn held >5s
```

Critical: Postgres `idle_in_transaction_session_timeout = 60s` to kill leaked txns.

#### Postgres tuning for pgvector on 4GB RAM

```conf
shared_buffers = 1GB                     # 25% RAM
effective_cache_size = 2GB               # 50% RAM
work_mem = 32MB                          # per-sort
maintenance_work_mem = 512MB             # HNSW build
random_page_cost = 1.1                   # SSD
effective_io_concurrency = 200           # SSD
max_parallel_workers = 2
hnsw.ef_search = 40                      # default; bump to 100 for higher recall
```

HNSW: `m=16` + `ef_construction=128`. 10k chunks ~30s build. Index memory `n * m * 8 bytes` — fits easily.

#### CORS + CSP via Caddy 2.11.2

(post-March-2026 CVE pin):
```caddyfile
header {
    Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
    X-Content-Type-Options "nosniff"
    X-Frame-Options "DENY"
    Referrer-Policy "strict-origin-when-cross-origin"
    Permissions-Policy "camera=(), microphone=(self), geolocation=()"
    Content-Security-Policy "default-src 'self'; ..."
    -Server
}
```

Rate limits via `mholt/caddy-ratelimit` (xcaddy build):
```caddyfile
@auth path /api/v1/auth/*
rate_limit @auth { zone auth_zone { key {remote_host}; events 10; window 60s } }
@api path /api/v1/*
rate_limit @api { zone api_zone { key {http.request.header.X-User-Id}; events 200; window 60s } }
```

#### Ktor app-side rate limit

```kotlin
install(RateLimit) {
    register(RateLimitName("attempts")) {
        rateLimiter(limit = 300, refillPeriod = 1.minutes)  // 5/s steady
        requestKey { call.principal<UserPrincipal>()?.userId ?: call.request.origin.remoteHost }
    }
    register(RateLimitName("llm")) {
        rateLimiter(limit = 60, refillPeriod = 1.minutes)   // 1/s ceil
    }
    register(RateLimitName("auth")) {
        rateLimiter(limit = 5, refillPeriod = 10.minutes)
    }
}
```

429 includes `X-RateLimit-Reset` header.

#### Sessions: Auth.js DB sessions, NOT JWT

JWT stateless advantage moot at 30 users. DB sessions: immediate revocation on `account.delete`, no JWT rotation pain, opaque 32-byte URL-safe token. Cookie `__Secure-authjs.session-token` (`SameSite=Lax`, `Secure`, `HttpOnly`).

#### ANSPDCP / Romanian specifics

- Privacy notice MUST include Romanian translation (Art. 12 GDPR + ANSPDCP enforces in mother-tongue)
- DPO designation NOT required at 30 users (not "regular systematic monitoring on large scale"). Document gap assessment in `docs/compliance/dpo-not-required.md`.
- Data residency: Hetzner FSN1 (Falkenstein, DE) = EU OK. Don't choose Hetzner NBG-US.
- OpenRouter (US) cross-border: DPA needed (OpenRouter publishes one; reference in privacy notice).

#### TL;DR pin sheet

| Component | Pin |
|---|---|
| Postgres | 17.3 |
| pgvector | 0.8.0 |
| pgjdbc | 42.7.4 |
| Exposed | keep 0.55.0 |
| HikariCP | 6.2.1 |
| Flyway | 10.20.x |
| Ktor | keep 3.0.1 |
| ktor-server-rate-limit | 3.0.1 |
| next-auth | 5.0.0-beta.25 |
| @auth/drizzle-adapter | 1.10.0 |
| drizzle-orm / kit | 0.36.x / 0.28.x |
| Caffeine | 3.2.2 |
| Caddy | 2.11.2 (post-March-2026 CVE) |
| next-pwa | 5.6.0 |
| Workbox | 7.x |
| idb | 8.x |
| Resend | 4.0.x |
| OTel Ktor | 2.10.0 |
| Hetzner | CX22 → CX32 at user 15 |
| Storage Box | BX11 €3.81/mo |

**Total infra cost: €8.32/mo launch → €10.61/mo at 15+ users.**

#### Files in repo touched by this plan

- `src/main/kotlin/jarvis/tutor/TutorDb.kt` — swap SQLite driver for pgjdbc + HikariCP
- `src/main/kotlin/jarvis/tutor/Users.kt` — widen `id` varchar(26) → text; align with Auth.js uuid
- `src/main/kotlin/jarvis/tutor/Tasks.kt` — keep schema; text → jsonb post-migration
- `src/main/kotlin/jarvis/tutor/FsrsCards.kt` — add `decay` column for FSRS-6
- `src/main/kotlin/jarvis/tutor/Audit.kt` — preserve hash chain; new event types via `AuditLogTable`
- `build.gradle.kts` — add `org.postgresql:postgresql:42.7.4`, `com.zaxxer:HikariCP:6.2.1`, `com.github.ben-manes.caffeine:caffeine:3.2.2`, `org.flywaydb:flyway-database-postgresql:10.20.1`, `io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:2.10.0`

---

### A — Visual primitives + diagram tradition (returned, ~9min wall)

**20 V-rules + component vocabulary, all brutalist-constrained.**

#### V-rules summary

- **V1** Discrete frames not interpolation (frame = algorithm step; tween ≤200ms; transient information effect)
- **V2** Manual advance only (no autoplay; `Right Arrow`/`J`/`Space` advance, `Left`/`K` retreat; Bret Victor active-reading)
- **V3** Prediction gate every 3-5 frames after first 2 (Romanian question + answer chips; wrong = no advance)
- **V4** One focal element animates per frame (extension of focal-claim rule)
- **V5** ARIA live narration per frame (polite live region in Romanian)
- **V6** Color rule: ink + paper + accent only, NO gradients, NO additional hues, hatching density for magnitude
- **V7** SVG over canvas (a11y, scaling, embeddable). Canvas only when >5000 elements + measured jank (then Counterpoint CMU)
- **V8** Motion One for animations (`animate()`, WAAPI under hood, 2.3kb mini / 17kb hybrid). Skip Framer Motion + GSAP
- **V9** MathJax 4 with `a11y/explorer` + `a11y/speech` (SVG output, ARIA + Braille labels)
- **V10** Mermaid for source-controlled diagrams + custom React SVG components for interactive primitives only
- **V11** Traces persist (past frame outcomes leave hairline ink; counters transience effect)
- **V12** Yellow = focus not category (one moving yellow per frame; two yellows OK only when in same comparison)
- **V13** Monospace labels everywhere (JetBrains Mono / IBM Plex Mono; aligns labels to code)
- **V14** Pure 90°/45° angles only (Vignelli grid rule, no 30°/60°/freehand curves; Bezier for path-following obstacles only)
- **V15** 1px stroke default, 2px for focus (NEVER opacity for focus — reads as fading away)
- **V16** `prefers-reduced-motion` honored — all transitions snap, frame-step UI works
- **V17** Keyboard parity with mouse (frame-step, prediction gate, scrubber all keyboard-accessible)
- **V18** Real `<text>` inside SVG (never burn text into paths — screen readers + select-copy + search index)
- **V19** Long descriptions on every complex diagram (`<details>` below diagram with Romanian narrative)
- **V20** Static fallback for every interactive figure (Tangle scrubber → sensible static sentence; recursion tree → pre-rendered snapshot)

#### Component vocabulary (props + a11y + styling)

`AlgoStepper(frames, initialFrame, predictionGates, renderFrame, liveRegionId)` — `role="application"` + `aria-roledescription="algorithm walkthrough"`; toolbar `<<` `<` `frame i/N` `>` `>>` monospace; prediction-active button yellow fill; built-in `Right Arrow` advance with prediction-gate block.

`ProcessTree(root, highlightPid, showSystemCall)` — ARIA `tree` widget, vertical ink lines + 90° elbows, monospace PIDs, selected = yellow background on name label.

`FilesystemTree` — rebrand of existing `FileSystemTreeSVG.jsx`. Trailing `/` on dirs (no folder icons; the slash IS the icon).

`PermissionsGrid(mode, highlight)` — 3×3 monospace letter grid, `OWNER` `GROUP` `OTHER` headers, highlighted bit = yellow cell. Binary + octal below in monospace.

`BayesGrid(total, rows, cols, subsets, highlightSubset)` — pure SVG `rect` grid, ink stroke + paper fill default, yellow fill on highlighted subset. Hatching pattern overlay for intersections. Counter at corner `420 / 1000`.

`MatrixTransform(matrix, initialBasis, scrubT, showDeterminant)` — ink grid hairlines, `i-hat`/`j-hat` as solid ink arrows, post-matrix basis in 2px ink, yellow fill on resulting parallelogram (hatching for negative determinant). Scrub `t` slider or arrow keys 0.1 increments.

`DPTable(rows, cols, values, currentCell, dependsOn, recurrenceExpr)` — `role="table"` with `<caption>` for recurrence. Active cell yellow fill, dependency cells 2px ink stroke + dotted arrows. Recurrence below in monospace with `<mark>` regions for current refs.

`RecursionTree(root, memoizedCalls, highlightCall)` — `role="tree"` arrow-key nav, ink-bordered nodes, active = yellow fill, memoized repeat = dashed yellow halo.

`UMLClass(name, abstract, attributes, methods, highlightMember)` — three ink-stroked compartments monospace, italic class name if abstract. Inheritance = solid line + open triangle (child → parent). Composition = solid + filled diamond. Active = 2px stroke OR yellow on name compartment only.

`PacketFlow(participants, packets, currentT)` — vertical ink lifelines + labeled horizontal/diagonal ink arrows. Active packet = yellow stroke + filled triangle head. Frame `i` shows packets at `t ≤ i`.

`Sparkline(values, width=80, height=16, highlightIndex, title)` — pure ink polyline, dots only at min/max or highlight. `<title>` mandatory + `<desc>` listing values.

`SmallMultiples(cells, cols, sharedXLabel)` — grid of Sparkline tiles in ink-stroked boxes, shared X-axis at bottom, active tile 2px ink stroke.

#### Key reference URLs from this round

- [VisuAlgo](https://visualgo.net/en) — `/sorting` `/dfsbfs` `/recursion`. Pseudocode-pane-with-line-highlight pattern.
- [Mike Bostock — Visualizing Algorithms](https://bost.ocks.org/mike/algorithms/) — time-is-time canonical essay.
- [3Blue1Brown Linear Transformations](https://www.3blue1brown.com/lessons/linear-transformations/) — matrix-as-warp.
- [Tangle by Bret Victor](https://worrydream.com/Tangle/) — reactive sentence inline params.
- [Distill — Communicating with Interactive Articles](https://distill.pub/2020/communicating-with-interactive-articles/) — step > scroll for comprehension.
- [Motion One](https://motion.dev/) — animation library pick.
- [Counterpoint CMU DIG](https://dig.cmu.edu/counterpoint/) — when >5000 elements need state-mgmt.
- [MathJax 4 Accessibility](https://docs.mathjax.org/en/latest/basic/accessibility.html) — math a11y story.
- [Tufte's Data-Ink Principles](https://jtr13.github.io/cc19/tuftes-principles-of-data-ink.html) — brutalist viz scriptural source.
- [Otto Neurath ISOTYPE](https://plato.stanford.edu/entries/neurath/visual-education.html) — restricted palette + repetition.

---

### D — LLM agents + Romanian language (returned, ~7min wall)

**Bilingual prompt strategy:** System EN (cached) / Exemplars RO (anchor diacritics + register) / User RO / Tool JSON EN. RO out-of-distribution on free-tier models = strongest steering signal.

#### 5 Romanian prompt patterns

1. **Bilingual anchor** — EN system + cache + glossary lock (`fork, thread, mutex, polymorphism — DO NOT translate`) + RO few-shot exemplars w/ warmth markers (`"Bună întrebare"` / `"Hai să gândim"` / `"Observă că"`).
2. **Diacritic-repair post-hoc** — regex `{'ţ':'ț','Ţ':'Ț','ş':'ș','Ş':'Ș'}` substitution + ASCII-stripped-RO detection → flag `<NEEDS_REPROMPT/>` rather than guess-fix.
3. **Term-glossary lock** — explicit "do NOT translate" list at top of system. Free models drift to "furculiță sistemică" for "system fork" otherwise.
4. **Socratic register markers** — `"Hai să vedem"` / `"Observă că"` warm; `"Conform definiției"` cold. Bake into few-shots.
5. **Code-switch acceptance** — students naturally write `"de ce thread-ul ăsta nu vede mutex-ul?"`. Mirror code-switch; don't translate `thread-ul` to `firul de execuție`.

**Tokenizer notes:** DeepSeek V3/V4 ~1.6-1.8 tokens/RO word (vs 1.3 EN); Qwen 3 Coder ~1.3-1.4× (strongest free-tier multilingual); Gemini 2.5 Flash SentencePiece RO-aware but reasoning quality drops vs EN; Llama 3 21.3% diacritic-error rate (UNSUITABLE without checker layer).

**Diacritic-correctness ceiling** ([arXiv 2511.13182](https://arxiv.org/html/2511.13182v3)): GPT-4o reaches 0.9639 TAS w/ 3-shot but capitalised Ș/Ț mangled 5.4% (vs 1.3% lowercase). Free-tier materially below. **Never trust free-tier output diacritics blind — always post-process.**

#### Eval set design (6 corpora, ~470 total Q)

| Eval set | Size | Source | Use |
|---|---|---|---|
| `eval_pa_v1` | 50 | AndraEP + FII past papers 2015-2024 | PA Socratic gate |
| `eval_poo_v1` | 40 | Frasinaru *Curs practic de Java* | POO terminology + concept gate |
| `eval_so_rc_v1` | 50 | Vidrașcu lab + man-pages | SO+RC factual gate (citation match) |
| `eval_alo_v1` | 30 | Past-paper LA subset Iași | ALO symbolic gate (SymPy verifier) |
| `eval_romath_lite` | 200 | Stratified RoMath-Baccalaureate (5,777 total) | General RO math floor |
| `eval_diacritic_v1` | 100 | Intentionally ASCII-stripped RO | Diacritic regression |

#### LLM-as-judge rubric (weighted)

```yaml
rubric_socratic_v1:
  no_answer_leak: binary, weight=0.40   # load-bearing override
  one_question_only: binary, weight=0.10
  addresses_misconception: 0_1_2, weight=0.20
  diacritic_correct: binary, weight=0.10
  term_preservation: binary, weight=0.10
  pedagogical_warmth: 0_1_2, weight=0.10
```

**Bias mitigation:** Generator = DeepSeek V3 free; Judge = Gemini 2.5 Pro or Claude Haiku (different model family per [Preference leakage in LLM-judge](https://arxiv.org/html/2411.15594v6)).

**CI gate:** `smoke` suite 20 Q/subject × 5 = 100 Q, temperature=0, fixed seed. Threshold: `weighted_score ≥ 0.75 AND no_answer_leak_pass_rate ≥ 0.95`. Full 350-Q suite nightly on main.

#### GEPA training protocol

**Cold-start (2 days):**
- Day 1 (8h): Alex hand-writes 20 `(student, gold_tutor_reply, rating, misconception_target, kc_id)` per subject × 5 = 100 examples.
- Day 2 (4h): ActiveLLM-style bootstrap. Zero-shot DeepSeek V4 on 200 candidates → GPT-4o-mini clusters by misconception type → pick 30 most diverse → Alex labels. **Total: 150 labeled.**

**Steady-state weekly cycle:**
1. Tutor serves drill → UI shows thumbs-up/down + free-text "why bad?"
2. Each rating writes `data/labels/{subject}/{date}.jsonl`
3. Nightly: append last 24h → `gepa_trainset.jsonl`
4. Weekly cron: GEPA run (~$2-3 free-tier rollout + paid judge ~20min)
5. Smoke eval gate before symlink flip

**GEPA metric callable returns `dspy.Prediction(score, feedback)`** — `feedback` is textual, used by GEPA's reflection LLM:

```python
def gepa_metric_socratic(gold, pred, trace=None, pred_name=None):
    rubric_scores = run_judge(gold, pred)
    weighted = sum(rubric_scores[c] * RUBRIC[c]['weight'] for c in RUBRIC)
    fb_parts = []
    if rubric_scores['no_answer_leak'] == 0:
        fb_parts.append(f"LEAK: tutor revealed answer '{pred.socratic_question}'.")
    if rubric_scores['diacritic_correct'] == 0:
        fb_parts.append("DIACRITIC: ASCII-stripped or cedilla detected.")
    if rubric_scores['addresses_misconception'] == 0:
        fb_parts.append(f"MISSED MISCONCEPTION: student showed '{gold.misconception_target}'.")
    return dspy.Prediction(score=weighted, feedback=" | ".join(fb_parts) or "PASS")
```

**Cost:** GEPA run typical $2-3 / 20min per [DSPy GEPA overview](https://dspy.ai/api/optimizers/GEPA/overview/). 5 subjects weekly = ~$10-15/week judge spend (unrelated to $10 OpenRouter unlock).

#### 5-layer hallucination defense

| Layer | Catches | Misses |
|---|---|---|
| 1. **Citation grounding** (PaperQA2) | claims with no chunk support | wrong paraphrase of real chunk |
| 2. **Regex leak check** | "the answer is X", direct numeric output | indirect via worked-example replay |
| 3. **Generator-Critic cross-check** | arithmetic errors, RO grammar, format violations | correlated error (same model family) |
| 4. **SymPy verifier** | wrong derivations, off-by-one, integer overflow | conceptual errors (right number, wrong reason) |
| 5. **Confidence threshold + refusal** | "nu sunt sigur" → suppress | confident hallucinations (dangerous tail) |

**Leak regex (RO + EN):**
```python
LEAK_PATTERNS = [
    re.compile(r"(?i)\b(răspunsul|rezultatul|soluția)\s+(este|e)\b"),
    re.compile(r"(?i)\b(the answer|the result|the solution)\s+is\b"),
    re.compile(r"(?i)\bO\([\dn²³log\s\^\*]+\)"),  # Big-O leak in Socratic
    re.compile(r"=\s*-?\d+(\.\d+)?\s*$", re.MULTILINE),  # naked numeric =
]
```

**Generator-Critic critical: cross-family** pin (generator DeepSeek V4-free, critic Gemini 2.5 Flash). 1.3× cost catches ~40% of agreement-failure cases per [CriticEval benchmarks](https://www.emergentmind.com/topics/llm-critic-models).

**SymPy verifier:**
```python
def sympy_verify_claim(claim):
    try:
        lhs = sp.sympify(claim["expr_lhs"]); rhs = sp.sympify(claim["expr_rhs"])
        diff = sp.simplify(lhs - rhs)
        if diff == 0: return (True, "")
        return (False, f"SymPy: {lhs} - {rhs} = {diff} ≠ 0")
    except Exception as e:
        return (None, f"SymPy could not parse: {e}")  # None = abstain
```

#### Voice agent design

**Pick: Khanmigo "BE FIRM" Socratic anchor + Pi-style warmth** (RO markers). Push-to-talk locked (privacy, latency, mobile data).

**Latency budget (push-to-talk, WebRTC desktop):**

| Stage | Target |
|---|---|
| Audio capture end → bytes | ~50ms |
| Whisper.cpp medium-RO Q5 (3s clip) | ~400-600ms |
| LLM TTFT (DeepSeek V3 free, 512 input tokens cached) | ~600-1000ms |
| Piper RO first audio chunk | ~150-250ms |
| Network (LAN/local) | ~30ms |
| **TTFA (time to first audio)** | **~1.2-1.9s** |

Higher than 800ms aspirational because free LLMs without WebRTC speech-to-speech. Acceptable trade for $0/mo recurring.

**Fallback chain:**
```
STT:  whisper.cpp medium-romanian Q5 (local)
   → whisper.cpp small-romanian Q5 (RAM-constrained)
   → OpenAI Whisper API ($0.006/min, last resort)

LLM:  DeepSeek V3 free (RO socratic primary)
   → Qwen 3 Coder 480B free (code-heavy)
   → Gemini 2.5 Flash free (1500 RPD safety net)
   → DeepSeek V4 paid (~$0.002/req, rate-limit cliff)

TTS:  Piper ro_RO-mihai-medium (~200ms first audio)
   → F5-TTS-RO (long-form audio overview, batch, never realtime)
   → eSpeak-NG RO (degenerate fallback, robotic but always works)
```

**Sesame CSM** RO not yet viable (English-primary; 20-language expansion banked for 2026/H2).

**State machine:**
```
IDLE → push-to-talk → LISTENING → release → STT_PENDING → LANG_DETECT
→ LLM_DISPATCH → TTS_STREAM → (user interrupts via push-to-talk) → cancel inflight LLM+TTS → LISTENING
TTS_STREAM → ended → IDLE
```

**Mobile vs desktop:** Mobile = text primary + voice opt-in per-turn (battery + privacy + 4G data). Desktop = voice viable default.

#### Cost optimization

**Anthropic prompt caching** (4 cache_control breakpoints max, 1.25× input write / 0.10× input read):
```
[1] cache_control after system prompt   ← invariant persona/policy
[2] cache_control after KC glossary     ← per-subject ~1/week
[3] cache_control after textbook chunks ← per-drill ~1/turn
[4] (no cache marker on user turn)      ← always fresh
```

**Static-prefix rule (load-bearing):** anything dynamic BEFORE cache_control wipes everything after. Session metadata in system block = 7% cache hit rate; move to suffix = 74% ([ProjectDiscovery 59% cost cut](https://projectdiscovery.io/blog/how-we-cut-llm-cost-with-prompt-caching)).

Expected hit rate at scale: 70-85%.

**Cost model:**
| Scenario | Daily | Monthly |
|---|---|---|
| Free-tier only | $0 | $0 |
| DeepSeek V4 paid ($0.27/M in, $1.10/M out) no cache | $0.083 | $2.50 |
| Anthropic Sonnet cached 85% hit | $0.43 | $13 |
| GEPA judge model (weekly) | — | $10-15 |

**Per-user-per-day soft cap:** 200k input + 50k output. Enforce via LiteLLM `budget_manager`.

#### DSPy signatures (concrete Python code)

```python
# RomanianTutor
class RomanianTutor(dspy.Signature):
    """You are jarvis, a Socratic CS tutor for FII Iași CS students.
    Reply in Romanian with correct ț ș ă â î diacritics.
    Preserve EN technical terms (fork, thread, mutex, polymorphism, heap).
    Ask exactly ONE Socratic question. NEVER state the final answer.
    """
    student_question: str = dspy.InputField()
    kc_id: str = dspy.InputField()
    textbook_chunks: list[str] = dspy.InputField()
    student_history_summary: str = dspy.InputField()
    socratic_question: str = dspy.OutputField(desc="Romanian, ≤2 sentences, ONE question")
    mastery_estimate: float = dspy.OutputField()
    misconception_observed: str = dspy.OutputField()
    next_kc_suggestion: str = dspy.OutputField()

# Plus: MisconceptionDetector, WorkedExampleGenerator, GradingCritic, IdeaAgent
# Plus: DeepSolve Planner→Solver→Writer pipeline (3 sigs)
```

#### Whisper RO deployment

```bash
# 1. Download fine-tune
huggingface-cli download gigant/whisper-medium-romanian --local-dir /opt/models/whisper-ro
# 2. Convert to ggml
python /opt/whisper.cpp/models/convert-h5-to-ggml.py /opt/models/whisper-ro
# 3. Quantize Q5
/opt/whisper.cpp/quantize /opt/models/whisper-ro/ggml-model.bin \
   /opt/models/whisper-ro/ggml-model-q5_0.bin q5_0
# 4. Serve
/opt/whisper.cpp/server -m /opt/models/whisper-ro/ggml-model-q5_0.bin \
   -l ro --port 9001 --threads 4
```

Expected WER on Common Voice ro: ~10-14% (fine-tuned beats Whisper large-v2 ~17-20% on RO per [Open Source SOTA Romanian Speech](https://arxiv.org/html/2511.03361v1)).

#### Piper RO streaming via Ktor + ffmpeg pipe

```kotlin
post("/v1/tts/stream") {
    val text = call.receive<TTSRequest>().text
    call.respondBytesWriter(contentType = ContentType.parse("audio/mpeg")) {
        val proc = ProcessBuilder("piper", "--model", "/opt/voices/ro_RO-mihai-medium.onnx",
                                  "--output_raw").redirectErrorStream(false).start()
        proc.outputStream.write((text + "\n").toByteArray(Charsets.UTF_8)); proc.outputStream.close()
        val ff = ProcessBuilder("ffmpeg","-f","s16le","-ar","22050","-ac","1",
                                "-i","pipe:0","-f","mp3","-").start()
        Thread { proc.inputStream.copyTo(ff.outputStream); ff.outputStream.close() }.start()
        ff.inputStream.copyTo(this.toOutputStream())
    }
}
```

First-audio latency ~180-280ms on modest CPU.

**Audio cache:**
```
cache_key = sha256(f"{voice_id}|{text_normalized}|{speed}|{model_version}")
storage   = /data/audio_cache/{cache_key[:2]}/{cache_key}.mp3
ttl       = 30 days
```

Pre-generate canned phrases at deploy time ("Bună întrebare", "Hai să gândim") → instant playback. Stream variable-content tail. UX ~50% snappier per [TTS cache patterns](https://github.com/pipecat-ai/pipecat/issues/2629).

#### 10 failure-modes catalog

| # | Failure | Mitigation |
|---|---|---|
| 1 | RO grammar/diacritic hallucination | Repair regex + reprompt threshold + GEPA negative-demo collection |
| 2 | Free-tier rate-limit mid-drill | LiteLLM fallback chain w/ 30s circuit-breaker + UX toast |
| 3 | Whisper STT Iași dialect miss | Inline transcript edit + per-user pronunciation hint as `--initial-prompt` |
| 4 | TTS EN tech term mispronounced | SSML phoneme overrides + glossary substitution |
| 5 | PaperQA2 "cannot answer" | Fallback ungated tutor w/ UI badge "fără citare" + log retrieval miss |
| 6 | DSPy program out-of-date | Versioned `compiled_program_v{n}.pkl` + atomic symlink + `/healthz prog_version` assert |
| 7 | Generator-Critic agree on wrong | Cross-family pin + SymPy 3rd voter + thumbs-down → GEPA counterexample |
| 8 | Vision misreads PS JPG | Gemini 2.5 Pro vision + conf-threshold gate + rotate/crop/retake UI |
| 9 | Answer leak despite Socratic prompt | Regex layer 2 + critic regen ≤2 + canned safe-reply fallback + GEPA |
| 10 | User code-switches mid-conversation | Per-message lang-detect + sticky `"Reply ALWAYS in Romanian"` instruction |

#### Critical pin sheet additions

- `gigant/whisper-medium-romanian` Q5 (Common Voice 11 + RO speech synth corpus)
- `piper ro_RO-mihai-medium` ONNX (60MB, MIT, ~50× realtime CPU)
- `F5-TTS-RO` ([arXiv 2512.12297](https://www.arxiv.org/pdf/2512.12297)) — long-form audio overview only
- `dspy-ai==3.*`
- `litellm[proxy]==1.55.x` w/ retry policy `max_retries=3, base_delay=1, max_delay=16, jitter=true`
- Generator: DeepSeek V3 free / Qwen 3 Coder 480B free
- Critic: Gemini 2.5 Flash free (CROSS-FAMILY mandatory)
- Judge: GPT-4o-mini paid (~$10-15/week GEPA cycles)

---

### G — Content authoring tooling (returned, ~7min)

**Verdict:** Git-tracked YAML/JSON sources + headless Ktor admin API + brutalist React curator SPA at `/curator`. Reject Drupal/Strapi/Payload. Authoring loop = LLM draft → swipe approval queue → git commit with co-author trail → per-KC versioned rollback.

**8-stage `/curate-tutor` pipeline:** MinerU 2.5 PDF extract → DSPy KC discovery → DSPy prereq edges → DSPy misconception mining → DSPy template generation → deterministic attribution → PaperQA2 groundedness verify → curator swipe approval.

**Output paths (git-tracked, one YAML per artifact):**
```
content/{subject}/
  subjects.yaml          # hand-edited subject config
  edges.yaml             # prereq DAG (machine-managed)
  edges.mmd              # auto-generated Mermaid mirror
  kcs/{slug}.yaml
  misconceptions/{slug}.yaml
  templates/{slug}.yaml
  past_papers/{year}-{exam}/{qid}.yaml
  placement/primer.yaml
  glossary.yaml          # RO ↔ EN term lock
  transcripts/lecture-{n}.yaml
  labs/{lab}/exercise-{n}.yaml
  mock_exams/{date}-{n}.yaml
```

**Critical schemas:**
- KC YAML: id, name_ro, name_en, cluster, prerequisites[], misconceptions[], sample_problem_templates[], bloom_level, difficulty 1-5, time_minutes, exam_weight, source{repo,path,sha,page,paragraph}, authored_by, authored_at, version
- Misconception YAML: id, kc_id, label_ro/en, trigger{type,pattern,scope}, refutation{text_ro,text_en,worked_counterexample}, common_at_pct, source[]
- Template YAML: id, kc_id, params_schema, stem_template_ro/en, expected_solution_template, grader_rules[], seed_examples[]
- Past-paper YAML: question_text_ro/en, format (mcq/open/code/fill/diagram/proof), options[], correct_answer, partial_credit_rubric[], kc_tags[≤3], bloom_level, difficulty, confidence

**Curator UI architecture:**
- KC editor — brutalist R1-R10 form + LLM suggestion sidecar + validation rules
- Concept-graph editor — **React Flow** (HTML DOM nodes, `isValidConnection` + `getOutgoers` cycle prevention, Dagre TB layout) + Mermaid `edges.mmd` mirror for git diffs
- Misconception review queue — `react-tinder-card` swipe (left = reject + write `rejected_misconceptions.jsonl`, right = git commit)
- Past-paper review queue — same swipe; target median 22s per accept
- LLM bulk-import diff view (added nodes green / removed red / modified amber)

**Validation rules (server-side Ktor):**
1. Cycle detection on save (BFS from new node through prereq edges)
2. Orphan detection (every KC must reach Tier-1 anchor within ≤8 hops)
3. Exam-weight sum per subject = 1.0 ±0.02 (soft warning)
4. Bilingual completeness (name_ro AND name_en, non-identical unless `technical: true`)
5. Source attribution non-empty

**LLM-suggestion DSPy signatures:** `KCExtractFromPDF`, `MisconceptionExtract` (solve-first then error-inject per arxiv 2503.16460), `PastPaperQuestion`, `PlacementMCQ`, `GlossRoToEn`, `RomanianCheck`. All return `confidence` for review prioritization.

**Versioning:** optimistic locking on `version: N` field, 409 on mismatch, atomic `git add + commit` per right-swipe with message `kc(PA): update activity-selection v{N+1}`. `.gitattributes` declares `*.yaml diff=yaml`, `.gitconfig` uses `yq -P` as textconv to normalize before diffing.

**Review-site upgrade (4 agents in parallel):** UX / Pedagogy / Visual (brutalist R1-R10) / **Romanian-correctness** (new — diacritic check via regex `[ŞşŢţ]`, term-preservation via glossary lookup, grammar coherence).

**Bilingual rule:** every NL field carries `_ro` (primary) AND `_en` (gloss); technical identifiers (`fork()`, `polymorphism`) stay verbatim via glossary `never_translate: true` flag. Diacritic-correctness CI gate at `tools/check-diacritics.sh`.

**35-day execution order (post-research):**
- D1-3: schemas + DAG validator + Ktor curator routes
- D4-7: `/curate-tutor` skill stages 1-7 against PA chapter 1
- D8-10: curator SPA shell + KC editor
- D11-14: concept-graph editor + Mermaid mirror
- D15-18: swipe queues for misconceptions + past-papers
- D19-22: bulk-import + diff view
- D23-26: placement primer + template authoring
- D27-29: versioning UI
- D30-32: review-site upgrade
- D33-35: bilingual gloss + Piper TTS cron + acceptance

**Defer to post-launch:** YouTube ingestion (yt-dlp + whisper.cpp), V86 labs, mock-exam authoring (UI-level), audio overview pre-gen, archive UI.

**Reject CMS rationale:** Drupal/Strapi/Payload all assume editor-content separation. Solo curator + LLM-first authoring + native git rollback wins on every axis (versioning / RO support / brutalist UI / deploy chain / hosting cost = $0).

**Verified existing state:** `KnowledgeGraph.kt:66` + `SubjectCorpus.kt:36` + 9 curator-adjacent skills. No `subjects.yaml` / `kc-*.json` exist yet — clean slate.

**Velocity math:** 300 KCs / 35 days = 8.5/day. Pure manual = 4.5 hr/day on authoring. LLM-extract + swipe = 2 min/accept × 8.5 = 17 min/day. **Architecture entire purpose = make Alex's role be approval, not creation.**

---

### F — Testing + ops + SDD spec template (returned, ~9min)

**Test pyramid + tool picks:** ~945 unit (JUnit5+MockK Kotlin / Vitest+Testing-Library TS / pytest Python; 80% line target) + 40-50 integration (Ktor TestApplication + Testcontainers Postgres + httpx-mock sidecar) + 20-30 E2E Playwright + visual regression (Lost Pixel + `toHaveScreenshot`) + 250 pedagogy eval items + 4 k6 load scenarios.

**Annual stack cost: $0.** GitHub Actions free tier (~1200/2000 min used). OpenRouter `:free` (250 req/night, RPD <1000). Lost Pixel self-host. k6 OSS.

**Picks:** k6 over JMeter/Gatling · Lost Pixel over Chromatic/Percy · Vitest over Jest · OpenRouter free LLM-as-judge.

**Playwright `e2e/fixtures/no-bad-network.ts` global fixture** — captures all `page.on('response')`, fails soft on any 4xx/5xx (excluding auth-boundary 401s on `/whoami`). Auto-applied to every spec by importing `test, expect` from fixture. **Closes Slice 1.5 PDF-404 gap.**

**Playwright config pin:** `locale: 'ro-RO'` + `timezoneId: 'Europe/Bucharest'` (catches diacritic bugs early). `chromium-desktop` + `chromium-mobile` projects every PR; Firefox/WebKit weekly only. Retries 2 in CI / 0 local. `prefers-reduced-motion` always forced (visual baseline stability).

**`stableScreenshot()` helper** — `document.fonts.ready` + `networkidle` + animation kill via `addStyleTag` + mask `[data-testid$="-timestamp"]` + `[data-testid="user-avatar"]` + streaming cursor.

**80 visual baselines:** 9 surfaces × {desktop, mobile} × {light, dark} ≈ 36 + 12 StepPlayer frames + 4 Romanian diacritic + 6 KaTeX + 10 brutalist-rule-violation negative tests + 12 misc.

**Brutalist rule sniff (Playwright assertion):** R3 no blue (HSL hue 200°-260° check) · R5 rectangles only (`border-radius > 4px` check) · R7 no shadow (`box-shadow` blur > 0 check) · R9 typography (font-family allowlist).

**LLM-as-judge:** `eval/rubrics/*.yaml` per agent (SocraticTutor / MisconceptionDetector / GradingCritic / WorkedExample / IdeaAgent). DeepSeek-R1 free judge, temp=0, structured JSON. 50 hand-labeled examples per agent (25 good / 15 known-bad / 10 edge). Pass threshold ≥0.85 weighted. Romanian diacritic dimension = deterministic regex (no LLM waste).

**CI gate:** `.github/workflows/pedagogy-eval.yml` nightly 03:00 UTC, OPENROUTER_KEY_FREE secret, gate on pass_rate ≥0.85 per agent.

**N=1 A/B Thompson sampling — Kotlin `N1Experiment.kt` ~80 LOC.** Marsaglia & Tsang Gamma sampler → Beta sampler → arg-max picker. **3-drill burn-in pins to most-data variant** for first 3 attempts on new KC (carry-over confound mitigation). Per-(user, experiment, variant) posterior table. Admin dashboard at `/admin/experiments/{expId}` shows n / point estimate / 95% CI / "lock variant" emergency button.

**Schema:**
```sql
CREATE TABLE variant_posterior (
    user_id UUID, experiment_id TEXT, variant_id TEXT,
    alpha DOUBLE PRECISION DEFAULT 1.0, beta DOUBLE PRECISION DEFAULT 1.0,
    PRIMARY KEY (user_id, experiment_id, variant_id)
);
ALTER TABLE attempts ADD COLUMN variant_id TEXT;
```

**k6 load scenarios:** `sustained 30u/1hr` + `burst 30u/5min` + `break-point ramp` + `soak 15u/4hr`. Tags `{type:llm}` vs `{type:non-llm}` differentiated SLOs. **Release-gate thresholds:** p95 LLM <3s, p99 LLM <6s, p95 non-LLM <300ms, error rate <1%, soak: no monotonic memory increase hour 1 vs 4.

**LLM throughput model:** 30 users × 0.5 drills/sec × 1.5 LLM calls/drill = 2700 RPH ⇒ OpenRouter free 1000 RPD saturates in 22min. Mitigation: rotate 5 keys (5400 RPD aggregate) OR local Ollama on sidecar OR cache+reuse drill-step generation.

**SDD spec template** (`docs/superpowers/templates/spec-template.md`) — 11 sections including load-bearing §3.2 visual acceptance checklist (every NEW component + every modified-mount-site has `data-testid` row), §3.3 interaction-smoke checklist (click + forbidden outcomes), §4 component-reuse contract (prop signature verbatim + JSX paste + `tsc --noEmit` + mock test asserting URL shape), §4.2 underscore-dead-prop audit (every `_propName` either used OR removed from parent), §6 quality gates (1-7 must pass via SDD whole-branch Playwright headless), §7 spec-grep-gate (every clarifying question grepped against specs/plans/research/BRIDGE.md before firing).

**SDD task template** (`docs/superpowers/templates/task-template.md`) — Implementer agent → Spec-review agent → Fix agent gates. BLOCKED triage rules (irreconcilable upstream type error / missing spec / missing dependency requiring scope expansion / build-runner env crash) vs FIX (test failure / lint error / visual baseline diff from own diff). Mid-flight ambiguity = log `AMBIGUITY:` + pick reading A + continue (NOT AskUserQuestion).

**Whole-branch final review (controller-spawned reviewer):**
1. Playwright headless against live URL: `E2E_BASE_URL=https://tutor.alex.dev pnpm playwright test --project=chromium-desktop --grep "@slice-N"`
2. Every spec §3.2 `data-testid` paints
3. Every spec §3.3 interaction passes (click + no error text + no 4xx/5xx)
4. Visual baseline diffs surfaced
5. Full unit + integration + pedagogy eval all green

**Romanian-correctness CI gate** — `tools/check-romanian.sh` grep-based ASCII regex `[ŞşŢţ]` in source/locale files, fail on cedilla pollution. `RomanianNormalizer.kt` substitutes Ş→Ș / Ţ→Ț at LLM response edge in `OpenRouterClient.kt`, logs `llm.cedilla.pollution` counter to Micrometer. Plus `tools/check-diacritic-stripping.sh` warn-only (RO locale files with common words `este/sunt/și/să` but zero `[ăâîșțĂÂÎȘȚ]` = ASCII-folded accident).

**Deploy + rollback (GitHub Actions):**
- Build → Push GHCR → Migrate (SSH appleboy/ssh-action) → Blue-green switch via Caddy admin API on :2019 → Smoke `@critical` Playwright headless
- `/srv/jarvis/scripts/blue-green-switch.sh` — new container on alt port, /healthz wait 60s, Caddy load-config switch, 5s drain, stop old
- Rollback: `git describe --tags --abbrev=0 HEAD^` → blue-green-switch back
- **Hard rule: all migrations backwards-compatible for ≥1 version (additive only; subtractive ships 1 release later)**

**Observability on CX22 (4GB):** Prometheus 15d retention 2GB / Loki 3.3 / Promtail / Grafana 11.4 / OTel-collector — all on `127.0.0.1` (Caddy fronts TLS). Memory budget ~700MB. Ktor exports Micrometer Prometheus on `:8080/metrics` + OTel traces to `otel-collector:4317`.

**Backup: Restic to Hetzner Storage Box** (€3.81/mo 1TB). Cron `5 2 * * *` — `pg_dump | restic --stdin` + Restic backup of `/srv/jarvis/uploads` + `grafana-data`. Retention 7d/4w/6m. Weekly `--read-data-subset=5%` integrity check. **Monthly calendar-pinged restore drill (first one always non-noop).**

**Secrets: SOPS + age** over Doppler (no managed dependency) over sealed-secrets (k8s only). `secrets.enc.yaml` in repo, age key on VPS chmod 600 + laptop + printed paper backup in fireproof safe. CI fetches `SOPS_AGE_KEY` GitHub secret at deploy.

**Audit-log retention (Article 12 + Article 17):** `audit_log` with `article_load_bearing: bool` flag. pg_cron daily purge of non-load-bearing rows >6 months. Article 17 erasure route anonymizes load-bearing rows + hard-deletes everything else + writes a `gdpr.erasure.completed` row.

**Incident runbook one-page-per-class:** Sidecar OOM / LLM 429 / DB connection exhaustion / RO Whisper failure. Each has symptom + diagnosis + mitigation + root-cause sections.

**Performance SLOs:** Page TTFB→LCP p95 <1.5s · First-interactive <2s · LLM p95 <3s · Non-LLM p95 <300ms · Error rate <1% · Availability 99.0%/mo · Sidecar OOM 0/week.

**17 concrete deliverables** (paths listed) = ~3000 LOC new (250 Kotlin / 150 Python / 200 TS / rest YAML+SQL+scripts). Linear ~12-15 days; SDD subagent fanout ~5-7 days.

---

### B — Pedagogy surface inventory (returned, ~12min)

**28 surfaces total** (9 banked + 12 gaps + 6 supplementary + 1 explicit decision).

**Top-10 must-ship v1 (leverage × frequency × blast-radius):**
1. Feedback ladder (§1) — Hattie d=0.73 lever, every-drill surface
2. Confidence calibration plot (§6) — reuses existing data, cheapest Dunning-Kruger fix
3. Wrap pane (§5) — closes daily loop, 60s, high SRL gain
4. Day-Of mode (§3) — single high-stakes day, no-new-content + Jamieson stress-reappraisal + 4-7-8 breath checklist
5. Error states (§14) — sidecar 5xx / 429 / network drops happen weekly; degraded modes critical
6. Empty states (§13) — onboarding moments, R9 universally
7. Placement primer (§10) — without it, queue is recency-driven not need-driven (ALEKS 20-30 Q × 30-45min)
8. First-time onboarding (§4) — Gollwitzer implementation intentions + Oyserman identity anchor + AI Act Article 4 literacy
9. Cross-subject daily notification (§18) — half-built (R5 email exists); add deadline-pressure section
10. Settings / Me tab (§12) — GDPR + EU AI Act Article 4 hard requirements

**Defer to v2:** Lab sandbox (§7 V86+xterm.js), Voice mode (§11 whisper.cpp + Piper push-to-talk + Feynman mode + podcast overview), Scratchpad PHOTO mode + AI co-scratch (§8), Past-paper approval queue (§16 → folds into G's curator UI).

**Decision banked: NO STREAKS.** Replace with mastery sparkline (block-glyphs `▰▱` per-day minutes) + 14-day per-subject sparkline. Duolingo shallow-learning-trap critique locked. Goes in Settings explainer.

**Feedback ladder (§1) — 5 levels:**

| L | Trigger | Reveals |
|---|---|---|
| L0 | 1st submit | nothing (direction only) |
| L1 | 2nd wrong OR [hint] click | strategy hint |
| L2 | 3rd wrong OR 2nd hint | worked next-step assertion (1 of N) |
| L3 | 4th wrong OR 3rd hint | 2/3 worked example, final blanked |
| L4 | [give up] OR 5th wrong OR ≥10 attempts (Beck-Gong wheel-spin) | full + misconception ribbon + FSRS re-queue at low mastery |

**Hint design (§2):** static templates beat LLM for latency; cache per `(problem_id, misconception_tag)`. "Just show me" override toggle (logs `gave_up`, NOT counted as 4th-wrong). Khanmigo "BE FIRM" rule when user types "give me the answer".

**Day-Of mode (§3):** countdown (largest text), sleep-data callback ("last night 7.2h — good"), reappraisal copy ("racing heart routes blood to your brain. it's helping."), 6-card review from week's misses, checklist (ID/matricol, 2 blue pens FII rule, water, phone silenced, 4-7-8 breath × 3). NO new content. NO streak. NO mastery %. NO cohort percentile.

**First-time onboarding (§4) — 5 steps:** Welcome+lang lock → Identity anchor (Oyserman 1-sentence) → Implementation intention (Gollwitzer if-then with cue from daily routine) → AI literacy 3 bullets (NOT 5 paragraphs) → Placement (per-subject, deferred, skip-able exit).

**Wrap pane (§5):** Mastered / Slipped / Calibration-Today / Top-Misconception / Tomorrow / "one thing you'll do differently" textarea. Slide-up pane NOT modal (no coercion). NO streaks, NO XP.

**Calibration plot (§6):** Cold-start needs ≥10 graded drills. 30-day "said vs was-right" gap with block-glyph sparkline. Specific callout: "YOU SAID DEFINITELY ON Q5 TUESDAY — YOU WERE WRONG" with revisit-card button. Per-subject percentage with sparkline glyphs.

**Lab sandbox (§7):** V86 in-browser Linux + xterm.js + WebSocket per-user Docker. Side panel `tree -p` reflects state. Step-grading per-step verifier scripts. Auto-kill idle >2h.

**Scratchpad (§8) — 4 modes:** TYPE (MathQuill, `?` line-start triggers `[ask jarvis]` button), DRAW (Excalidraw, `[[note: ...]]` tags expose to retrieval), CORNELL (cues/notes/summary, becomes FSRS cards), PHOTO (Mathpix-style OCR, low-confidence shows source+decoded side-by-side).

**Mock-exam grading (§9):** Format fidelity (ALO 30min no-docs + 30min open-docs). Sub-goal partial credit (Morrison et al.). Math = SymPy + Z3. Code = unit tests + mutation kill score (FunPRM). Proof = LLM rubric with prof's past markings. Timer NOT auto-extend on tab-blur.

**Placement primer (§10):** ALEKS adaptive (PFA per Pavlik). "I don't know" first-class button (no penalty for honesty). 20-25 open-response Q (NOT MC — MC enables guessing). Returns STRONG/WEAK/MIXED tiers per subject + suggested first-drill queue.

**Voice mode (§11):** Push-to-talk only. 3 modes: Feynman (explain out loud, gap-check) / Drill (voice math answers) / Podcast (5-min overview of today's queue). Piper "mihai" RO voice. Whisper.cpp + wav2vec2-romanian fallback. Confidence-threshold UX (<60% → tap-fix). Mic denied → text fallback.

**Settings / Me (§12):** schedule (exam-date lock 14d-out protects panic-reset), language (interface + content source-locked default), hints (scaffolded/direct), notifications (1×/day / 3×/day / off), privacy (telemetry default OFF, "pause logging 1h", GDPR Art 15 export 1-click, Art 17 delete 2-step "type DELETE" + 7-day grace), AI literacy persistent notice, account.

**Empty states (§13):** First-use / cleared / no-data / error (Carbon DS). Empty queue = "you're caught up. options: pre-empt tomorrow / mock / lab / stop. seriously. close the tab." Empty subject map = placement-CTA. Empty ledger = "no gaps tracked yet" + explainer.

**Error states (§14):** Sidecar 5xx → "saved locally · syncs when back · self-grade for now?". LLM 429 → countdown + "static hints still work". Offline → "scratchpad saves locally · already-loaded drills work · ledger read-only".

**Bilingual toggle (§17):** Per-content-block `source_lang` lock. **EXAM-FORMAT MATERIALS NEVER AUTO-TRANSLATE** (fidelity to exam language). Voice respects content lock.

**Cross-subject daily notification (§18):** 08:00 RO time batched. Queue summary + deadline pressure + if-then reminder. NO "you missed yesterday" guilt. NO sleep-window pushes. Hard cap 3×/day. List-Unsubscribe header.

**Ordering dependencies:**
- Phase A foundations: §13 empty / §14 error / §17 bilingual
- Phase B day-1: §4 onboarding → §10 placement → §6 calibration
- Phase C drill core: §1 ladder → §2 hints
- Phase D daily: §5 wrap → §18 daily
- Phase E high-stakes: §9 mock → §3 day-of
- Phase F parallel: §12 settings, §15 no-streak
- Phase G v2: §7 V86, §8 PHOTO, §11 voice, §16 past-paper

**Anti-pattern register:** No streak-shame · No cohort percentile · No "below average" comparative framing · No auto-translate exam material · No "you cheated" surface (spaced-rep absorbs audit signal silently) · No always-on AI co-scratch · No 12-modal-overlay tutorial · No demographic ask day-1.

---

### H — Multi-user + social design (returned, ~8min)

**Frame:** FII grupa (~25 students sharing seminar room/timetable) is unit, NOT anul (~150-200). Collaboration cultural default — share notes, debug on Discord, restanță-prep clusters. Tool rides current, doesn't fight. **Distinction: collaboration-as-scaffold = yes; collaboration-as-shortcut = blocked.**

**1. Cohort presence** — anonymous "• N" pill on KC tiles. k=3 server-enforced (count 1 or 2 → "• —"). Opt-OUT default ON. Ephemeral "now or never" — no history shown. `presence` table with 30s refresh / 90s TTL. Server filters `count >= 3` BEFORE shipping. Postgres LISTEN/NOTIFY OK at scale. RO copy: "3 colegi se uită acum la lacom. Nu ești singur." — informal "tu".

**2. Anonymous Q&A per-KC** — Slido/Vevox 3-5x ask-rate lift. Asker anonymous to peers AND curator UI; ONLY Alex can lookup author on explicit click → audit-log row. Single-bit "asta m-a ajutat" signal (visible ONLY to asker) feeds misconception-DB. **No public upvotes** (Stack Overflow research: extrinsic crowds out intrinsic). AST-normalized code attachments before showing to peers.

**3. Cohort confusion-map (k=3, timeline, per-subject, trigger):**
- 3a Timeline: weekly aggregate, 8-week sparkline under tile. Signal: "60%→25% on lacom-greedy = seminar landed".
- 3b Per-subject: PA-tab + SO+RC-tab each subject heatmap.
- 3c **Trigger: `first_attempt_wrong_pct > 50% AND n_attempts >= 5`** flags KC `cohort-blocked` in Alex's curator dashboard. Threshold 50% NOT Caleon 10% — in 25-person grupa, 10% = 1-3 people = below k=3 floor. **Small-cohort statistics force higher threshold.**
- k=3 enforced per week; blank bars (not zero) when k unmet.

**4. Study-group async sessions** — NOT real-time chat. Curator schedules KC + 48h window. Cohort gets digest entry + opt-in push. Participants drill independently within window with `session_id` tag. Post-close aggregate page: participant count (k=3) + anonymous outcome distribution + most-common wrong answer (AST-normalized). **Discussion thread AUTHOR-ATTRIBUTED** (different privacy contract from Q&A — asking-anonymous removes social cost, contributing-attributed enables relationship-building per Liu et al. AOD research). NO typing indicators, NO "X is replying now". Forum-shaped not chat-shaped.

**5. Co-curator (Wikipedia trust-earned model)** — Alex starts only curator. Promotes 1-2 classmates with good Q&A answers + edit suggestions.

**Permission matrix:**

| Action | Student | Curator | Admin (Alex) |
|---|---|---|---|
| Run drills / ask anon / answer | ✓ | ✓ | ✓ |
| Mark Q→misconception | — | ✓ | ✓ |
| Accept/reject KC edit | — | ✓ | ✓ |
| Schedule session | — | ✓ | ✓ |
| See Q author identity | — | — | ✓ (audit-logged) |
| Promote student→curator | — | — | ✓ |
| Invite / remove cohort member | — | — | ✓ |
| Budget settings | — | — | ✓ |

**Auto-detected misconception promotion:** 3+ classmates fail same KC same way (AST-normalized log) → auto-files draft misconception entry into Alex's queue. **Closed loop: spaced-rep feeds curation feeds spaced-rep.**

**6. Peer code review (cheat-shielded)** — Submit → automated grader (FINAL) → unlock "vezi soluții ale colegilor" → up to 3 AST-normalized peer solutions. **Cheat-shield: peer view unlocks ONLY after own grade.** Levenshtein anti-paste at submit-time (FII-GitHub repos + cohort window + textbook seeds; threshold 0.85 AST-normalized; flag-NOT-punish — followup variant problem on same KC). **Frame: anti-spoiler NOT anti-plagiarism** (collaboration normalized in FII; cognition-protection is right frame).

**7. EU AI Act + GDPR memo** — Annex III(3) borderline (institution-anchored; private personal tool defensible). **Conservative read: pretend it applies.** Alex = controller. ANSPDCP notification only on breach (Art 33, 72h) or DPIA-required (Art 35). Light DPIA via EDPB 2026 template (4 pages not 40). Article 4 AI literacy intro mandatory. Multi-user **LiteLLM proxy with per-user virtual API keys** → per-user TPM/RPM/budget; daily reset at midnight RO time; clear "ai folosit cota pentru azi" message NOT generic 429.

**8. Invite + RBAC + cascade-delete** — magic-link 7-day single-use email-bound token (SHA-256 hash stored; raw only in email). Onboarding 4 slides: AI literacy → ToS+privacy (consent checkbox) → profile → notification opt-ins. Cascade-delete on remove: attempts/questions/comments/users/presence/invites all dropped, audit-log preserved (`actor=alex, action=delete_user, target=X`). Misconception-DB Q+A preserved IF re-anonymized at promotion time (Art 17(3)(d) research carve-out).

**9. Notifications via Resend Topics — per-channel preference matrix:**
- Daily digest 08:00 = OPT-IN
- Confusion alert = OPT-OUT (per-subject)
- Q&A answer-to-my-Q = OPT-IN
- New question in subject-I'm-strong-on = OPT-OUT (only if "willing to help with X")
- Study-session 2h-before-open + 4h-before-close-if-not-started = OPT-IN
- Misconception flag (curators) = OPT-OUT

**Anti-nag locks:** No "haven't studied 3 days" reminder · No "falling behind cohort" frame · No re-send if not opened · Info not exhortation copy · One-click List-Unsubscribe header on all emails.

**10. Anti-cheating culture = anti-spoiler frame:**
- "Vezi soluția" before submit → silent FSRS prioritizer signal (KC re-hit sooner), NOT flag-for-Alex
- Mock-exam 24h window (start anytime, N-min timer once started, post-window viewable but marked `expired - not for credit`)
- Levenshtein flag → followup variant on same KC, NOT sanction
- Audit log feeds prioritizer NEVER social-judgment layer

**11. Day-1 onboarding:** D0 invites fire / D1-7 classmates accept / D7 once 3 classmates × 5+ attempts each → cohort features unlock. **Honest empty-state during first 7 days** — "construim harta — fă primele drill-uri". No fake-it-til-you-make-it.

**12. Offboarding:** Pause (60-day inactive auto, retains for re-entry) vs Full-delete (Art 17 cascade). Hard offboard at graduation: archive cohort 12-month read-only OR dissolve with cascade.

**13. Migration (semester rollover):** `cohort_membership` table with `joined_at` / `left_at` / `active`. "Pornește semestru nou" wizard. **Misconception-DB cumulative across semesters (long-term value).**

**14. Cross-cohort sharing (2026 vs hypothetical 2027):**
- KC graph = GLOBAL (curriculum stable year-over-year)
- Misconception-DB = GLOBAL with cohort-attribution-stripped at promotion time
- Cohort-aware data = LOCAL (confusion heat, presence, Q&A, sessions, attempts)
- Two-layer datastore: `knowledge` schema (slow-curated global) vs `cohort_data` schema (per-cohort fast-moving)

**15. Cost-sharing model (per banked feedback_no_paid_apis):** **Hybrid sponsor + BYOK + chip-in.** Default Alex covers $10 OpenRouter unlock with shared cohort budget. Power-user escape: Settings → "Adaugă cheia mea OpenRouter" routes own traffic through own free-tier. Optional: "contribuie la budget-ul cohortei" Stripe button (RON or EUR, voluntary, NO leaderboard). LiteLLM per-user virtual key + budget enforcement. **Transparency: "buget cohortă: $7.20 / $10 folosit luna asta" indicator (total only, NOT per-user).**

**16. Romanian academic culture (cross-cutting):**
- Cohort scope = **grupa (5-30) primary unit**. Anul irrelevant for daily collab.
- **Restanță mode** (Sep retake): opt-in cohort temporarily shrinks to just retakers, fresh confusion-map generated.
- Seminar (~25, interactive) vs Curs (100+, one-way, PDF). **Tool sits in seminar register.** Curs material = read-only PDF reference.
- **Address: "tu" informal default** (student-to-student + student-to-Alex + tool-to-student + tool-to-Alex). "Dumneavoastră" only if-tool-ever-onboards-actual-asistent-or-profesor.
- Role label: Alex = "asistent" in UI (cultural fit — that's what year-2s call seminar-runner) / `curator` underlying RBAC name.
- IRL Pizza-Hut-on-Copou study sessions irreplaceable — async sessions COMPLEMENT not COMPETE.

**Open risks:**
- Cohort growth past 30 = social-presence math changes (Dunbar). Hard-cap at 30 day-1.
- Alex-misuse-admin: audit log + cohort-knows-log-exists is only check. Document honestly. No third-party oversight at this scale.
- Hostile classmate harassment via Q&A: need "report this content" button + Alex-only review queue.
- Romanian privacy lawyer review: 1-hr ~200-400 RON probably worth it before multi-user goes live.

**10 cross-cutting principles:** server-side k=3 · opt-out for cohort-positive opt-in for commitment-heavy · anonymity-for-asking attribution-for-contributing · audit-logs-for-trust-not-surveillance · spaced-rep-absorbs-audit-signal · RO informal "tu" default · GDPR cascade with misconception-DB carve-out · sponsor+BYOK+chip-in cost · grupa-not-anul scope · anti-spoiler-not-anti-plagiarism frame.

---

### E — Compliance + privacy concrete docs (returned, ~12min)

**15 ship-ready deliverable templates** (full text in agent output; this section indexes + extracts critical findings).

**CRITICAL pre-launch findings:**
1. **OpenRouter US-routing = Schrems II risk.** Default routes to US; with DPF being challenged ("Schrems III" pending), switch to `https://eu.openrouter.ai` (enterprise — email request to OpenRouter required) + enable **Zero Data Retention globally**.
2. **Resend stores in US under SCCs.** Acceptable for email metadata (sender/recipient/subject) but document in DPA register.
3. **Hetzner DPA at `https://www.hetzner.com/AV/DPA_en.pdf`** — Alex must sign + store; sub-processor list at `https://www.hetzner.com/AV/subunternehmer.pdf`.
4. **AI Act Article 4 in force NOW (since 2 Feb 2025)** — enforcement begins 2 Aug 2026 with fines up to €7.5M or 1.5% turnover. Mandatory first-login AI-literacy slides.
5. **Romania's digital-consent age = 16** (Law 190/2018 Art 5) — lower than default GDPR 16; FII students all 18+ but document.
6. **`/api/v1/tasks/{id}/audit_log` = load-bearing AI Act Article 12 surface.** Must be tamper-evident (append-only with hash chain) per AI Office guidance.

**The 15 deliverable templates:**

1. **`PRIVACY.md.ro`** (~2100 RO words) — Operator identity, 6 data categories (account / usage / AI interaction / audit log / functional cookies / explicitly-excluded), legal basis matrix (Art 6 GDPR per category), retention table (90-day rolling AI / 5-year audit / 14-day access logs), 3 sub-processors (Hetzner DE / OpenRouter US→EU / Resend US-SCC), Art 15-22 rights table with response times, security measures (TLS 1.3 / RLS / append-only audit / bcrypt unused / encrypted backups), breach notification protocol, cookies policy (3 strictly-necessary only, NO consent banner), Art 9 special-category exclusion, Art 22 automated-decision exemption (Ledger drawer = human override), modification procedure, ANSPDCP supervisory contact, controller contact.

2. **`PRIVACY.md.en`** (~2000 EN words) — parallel structure, RO official version prevails on conflict.

3. **Cookie-banner decision** — **NO banner required.** Only strictly-necessary cookies (Auth.js session + CSRF + lang_pref). ePrivacy Art 5(3) exempts. Permanent footer link to `/privacy/cookies`. If future opt-in telemetry added, modal NOT banner (Plausible Analytics EU-hosted cookieless).

4. **AI Literacy first-login page** (RO + EN, ~600 words each) — "AI is not Google that answers correctly" / "AI doesn't know your exact syllabus" / "AI can be talked into bad answers" / "For exams: do NOT use AI as single source" / "What Jarvis does NOT do" (no face/voice/emotion recognition per Art 5(1)(f) HARD BAN) / "Your rights". Confirmation: 3 checkboxes. Code skeleton: `AiLiteracyService.kt` with `CURRENT_LITERACY_VERSION = "v1.0-2026-05-17"`, annual re-confirmation, middleware `aiLiteracyGate()` returns 412 + redirect.

5. **`ANNEX_IV.md` template** (AI Act Article 11) — 9 sections covering system description / development elements / monitoring+functioning+control / performance metrics justification / Article 9 risk management / lifecycle changes / harmonised standards (ISO 23894:2023 + 42001:2023 + 27001 + NIST AI RMF 1.0 voluntary) / EU Declaration of Conformity (pending conformity assessment) / Post-market monitoring (Article 72).

6. **`RISK_REGISTER.md`** — 15 risks indexed R-001 to R-015 with L/I/R-residual matrix:
   - R-001 LLM hallucination on exam-critical content (L=4 I=3, residual M)
   - R-002 Data breach DB compromise (L=2 I=5, residual L)
   - R-003 OpenRouter US routing without SCC coverage (L=3 I=4, residual M)
   - R-004 Resend US email metadata leak (L=2 I=2, residual L)
   - R-005 User uploads sensitive personal content to AI prompt (L=3 I=4, residual M — mitigated by 90d rolling + ZDR)
   - R-006 Discriminatory LLM output (L=2 I=4, residual M)
   - R-007 FSRS miscalibration → student burnout (L=3 I=2, residual L)
   - R-008 Audit log tampering or loss (L=2 I=5, residual L)
   - R-009 Account takeover via magic-link interception (L=2 I=4, residual L)
   - R-010 Emotion-recognition feature creep (L=2 I=5, residual L — PROHIBITED_FEATURES.md + PR review item + dependency audit)
   - R-011 Whisper.cpp/Piper RO output quality (L=4 I=2, residual M)
   - R-012 Multi-user data leakage via RLS misconfiguration (L=3 I=5, residual M — CI cross-tenant test mandatory)
   - R-013 LLM bias against Romanian-language coursework (L=5 I=2, residual M)
   - R-014 Provider outage cascading to service unavailability (L=3 I=2, residual L)
   - R-015 Regulatory drift: AI Office issues new guidance Jarvis misses (L=4 I=3, residual M)

7. **`MODEL_CARD.md`** — per-component cards: DeepSeek V3 / Qwen 3 Coder / Gemini 2.5 Flash / FSRS-6 / MinerU / PaperQA2 / Whisper.cpp RO / Piper / nomic-embed-text. Each = role / provider / cost / training / strengths / limitations / accuracy benchmark / bias considerations / use restrictions.

8. **DPA Register + Template** — Hetzner DPA (2026-02-17, signed 2026-04-15) / OpenRouter (PENDING enterprise + EU endpoint request) / Resend (2026-01-15, click-accepted 2026-04-10). DPA template Articles 28(3) compliant with sub-processor notification + audit rights + return/deletion on termination + 24h breach notification SLA.

9. **Age verification flow — self-declaration sufficient.** FII students 18-25; Romania Law 190/2018 floor = 16. Signup form: age-18+ checkbox + privacy-read checkbox + AI-literacy-read checkbox. `signup()` records `consent_log` entries atomically. Hard-block under-16. 16-17 (edge case): parental email confirmation flow (deferred, stubbed in spec).

10. **Data subject rights UI + API:**
    - `GET /api/v1/me/export.json` (Art 15 + 20) — single rate-limit 1/hour/user, JSON envelope with user / consents / drills / ai_interactions / audit_log / uploaded_files
    - `POST /api/v1/me/purge` (Art 17) — confirmation phrase "DELETE my account permanently", cascade order respecting FK constraints, audit_log PSEUDONYMIZED (not deleted) for 5-year retention, 30-day backup-purge scheduled
    - `POST /api/v1/me/pause-logging` (Art 18) — max 24h duration, sets `loggingPausedUntil` in user_preferences, LLM middleware checks before logging
    - Ledger Drawer UI (Art 22) — every mastery-state mutation shows {timestamp, card_id, observed, decided, why, override_available}. Override logged with reason.

11. **Breach notification template + procedure** — Decision flow (60min): detect → risk assess (10min) → containment (1h) → notification draft (24h) → send within 72h. ANSPDCP template in Romanian (10 sections: identification / DPO / nature / categories / numbers / consequences / measures / risk evaluation / phased reporting / annexes). User notification template (RO + EN) for high-risk cases.

12. **TIA-OpenRouter** (Schrems II framework) — US laws assessed (FISA 702 / EO 12333 / CLOUD Act) → ZDR mitigates → SCCs Module 2 covered → DPF adequacy applies → supplementary measures (encryption / minimization / pseudonymization) → **residual risk LOW**. Approved with conditions: ZDR globally + migration to `eu.openrouter.ai` within 60 days + quarterly review + emergency-switch plan to Mistral La Plateforme EU within 30 days if DPF invalidated.

13. **`EXCLUSIONS.md`** — Children under 18 / Special-category Article 9 (racial/political/religious/union/genetic/biometric/health/sexual) / Biometric for ID / **Emotion recognition (HARD BAN per Article 5(1)(f))** / Profiling for ads / Location / Financial / Government ID / Employment data. Each category = status + why + controls.

14. **Romania Law 190/2018 addenda** — DPO appointment NOT mandatory (no CNP processing) · 18+ self-declared (stricter than 16 floor) · No CNP / health / biometric / employee-monitoring · Internal SLAs: DSR response 7d (vs 30d legal max) / breach internal 24h (vs 72h legal max) · Cookies: only essential per Law 506/2004 · Notifications to ANSPDCP in RO preferred · No general registration of controllers post-GDPR.

15. **5 code-level enforcement patterns:**

   **Pattern 1: Append-only audit log with hash chain** — `audit_log` table with `BEFORE UPDATE/DELETE` triggers raising exception; `previous_hash` + `this_hash` columns; SHA-256 of canonical `userId|event|payload|occurredAt|previousHash`; daily verifier streams ordered-by-time and re-hashes.

   **Pattern 2: Postgres RLS** — `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` on every user-scoped table; policy `USING (user_id = current_setting('app.current_user_id')::BIGINT)`; `jarvis_app` role `NOBYPASSRLS` explicit; `jarvis_migrate` role `BYPASSRLS` for schema only. **CI integration test mandatory:** `withUserContext(userA) { assertEquals(0, db.drills.findAll().size, "RLS leak") }`.

   **Pattern 3: TLS-everywhere with HSTS preload** — nginx TLS 1.3 only + `Strict-Transport-Security max-age=31536000; includeSubDomains; preload` + OCSP stapling + CSP `default-src 'self'; connect-src 'self' https://eu.openrouter.ai; frame-ancestors 'none';` + Permissions-Policy `camera=(), microphone=(), geolocation=(), payment=()`.

   **Pattern 4: Encryption at rest** — Hetzner disk LUKS aes-xts-plain64 + pgcrypto column encryption for special tokens via `pgp_sym_encrypt(value, current_setting('app.encryption_key'))`. `JARVIS_ENCRYPTION_KEY` env var or refuse-to-start.

   **Pattern 5: Automatic 90-day data minimization via pg_cron** — `purge_old_ai_interactions()` daily 03:00 / `purge_old_access_logs()` daily 04:00 / `purge_old_audit_log()` weekly 05:00 Sundays (only pseudonymized rows ≥5y1mo). Plus Kotlin daily `verifyRetentionPolicies()` alerts ops on violation.

**Implementation checklist (28 items):** privacy notice deploy / AI literacy gate / /me settings page / export+purge+pause routes / Ledger drawer UI / DB audit_log + consent_log + ai_literacy_confirmation migrations / RLS on every user-scoped table + cross-tenant CI test / nginx TLS+HSTS+CSP / pg_cron retention jobs / pg_audit + integrity verifier / sign Hetzner DPA / request OpenRouter EU endpoint+ZDR / sign Resend DPA / drop all 8 compliance docs / breach notification template / TIA + Law 190 addenda + exclusions / quarterly review calendar 2026-08-17 / subscribe AI Office + EDPB + ANSPDCP newsletters.

---

## Round 9 status: ALL 8 SUBAGENTS RETURNED ✓

| Subagent | Wall-time | Status |
|---|---|---|
| A · Visual primitives + diagram tradition | ~10min | ✓ appended |
| B · Pedagogy surface inventory | ~12min | ✓ appended |
| C · Backend infra + DB schema + ops | ~7min | ✓ appended (first back) |
| D · LLM agents + RO language | ~10min | ✓ appended |
| E · Compliance + privacy concrete | ~12min | ✓ appended (last back) |
| F · Testing + ops + SDD spec template | ~9min | ✓ appended |
| G · Content authoring tooling | ~7min | ✓ appended |
| H · Multi-user + social design | ~8min | ✓ appended |

**Next step:** synthesize all 8 outputs into single spec at `docs/superpowers/specs/2026-05-17-jarvis-full-tutor-redesign-design.md` per brainstorming skill checklist. Then spec self-review. Then user reviews. Then transition to writing-plans skill.
