# Jarvis-Kotlin Tutor — End-to-End UI Design Plan (Design Spine)

_Design lead synthesis (workflow `wf_39474446-510`, 2026-06-02). This is the design skeleton: every locked decision, every flagged Alex/taste call, and the render queue. It feeds the build plan (writing-plans), not the build itself. All component names verified against `tutor-web/src/` as of HEAD `a2dd257`._

---

## 1. Purpose & method

This plan is assembled the way the rest of the tutor was: **surface-by-surface grounded UI design**. Each surface was reasoned through the DESIGN.md R1–R10 ruleset, locked where the rules decide it, flagged where a genuine taste/Alex call remains, then rolled up here into one coherent spine.

- **The door is surface #1 — the locked exemplar.** `DoorBrutalist` + the `--fig-*` Figure system + `ThemePicker` are already built and locked. Every other surface inherits the door's vocabulary: the masthead rule, the asymmetric grid, the open-figure-in-its-own-column treatment, the progress pips, the monumental type, the one-yellow-CTA discipline.
- **Method per surface:** focal claim → layout → reused components (real names only) → new components (justified against the inventory) → viz/figure → key interactions → R-compliance check → open decisions.
- **Decisive where rules decide, honest where taste decides.** Surfaces marked `needsSelfSeeRun=YES` are taste calls a builder can't settle from rules — they go in the render queue (§5). Surfaces marked `NO` are mechanical rule applications and can be built from this spec directly.
- **Verification done:** DESIGN.md (repo root), all 38 inventory components, the 5 door files, `Figure`/`brutalistVars`/`warmVars`/`PALETTES` exports, and all 6 palette ids confirmed present in the repo.

---

## 2. Foundation locks (the decisions that gate everything)

These gate every surface. Build nothing downstream until §2.1 (brand) is confirmed.

### 2.1 Brand / theme system — **NEEDS ALEX CONFIRM (load-bearing)**

Alex's directive ("rebrand the tutor") = make the theme system **real and production-driving**, not freeze at brutalist-only. Proposed lock:

| Decision | State |
|---|---|
| Canonical/default brand | **Brutalist-yellow** (`brand-yellow` palette, `#FDE047` on DARK). This always satisfies DESIGN.md in its own right. |
| Brutalist palette swaps | The 6 palettes drive only `--color-accent` / `--color-accent-hover` / `--color-panel-dark-fg` via `brutalistVars()` — one accent on black. All in-brand. |
| Warm skin | Opt-in **departure**, scoped under `.door-warm`, never the default, never contaminates brutalist tokens. Selectable via picker. |
| Production picker scope | **Palette + layout (skin) only.** The Concept row is **demo-only** — stripped in production (`concepts=[]` → row renders nothing). |
| One accent at a time | Enforced per theme, every surface (R7). |
| Picker entry point | The **brand-circle** (conic-gradient) in the masthead is the *only* theming entry point. No scattered color pickers. |

> **Why ALEX CONFIRM:** this is the one decision that contradicts a literal reading of DESIGN.md ("a single brutalist brand"). The plan treats theming as production reality per Alex's stated intent, but the spine should not lock a brand-identity change silently. Confirm: brutalist-yellow default + warm/palettes as user-selectable themes.

### 2.2 Type scale, token map, shape, motion — **LOCKED (mechanical from DESIGN.md)**

- **Type:** `font-display` = `font-body` = `ui-monospace / "SF Mono" / "JetBrains Mono" / Menlo / Consolas / monospace`. Scale: sm 12 · body clamp(14–16) · lg 18 · h2 20 · display clamp(28–40) · display-xl clamp(36–68). Tracking: tight −0.01em / wide 0.05em / widest 0.15em (chrome) / mega 0.3em (kickers). Chrome labels = uppercase + tracking-widest.
- **Tokens:** `page-bg #fff · page-fg #000 · panel-dark-bg #000 · panel-dark-fg #fde047 · accent #fde047 · accent-hover #facc15 · accent-rule #eab308 · accent-soft #fefce8 · border-strong #000 · border-thin rgba(0,0,0,.2) · ring-focus #facc15 · surface-muted #fafafa · danger-bg/fg · info-bg · disabled-bg/fg · overlay-fg #fff`. No raw hex in JSX; integration boundaries (xterm theme, canvas API) are the only exceptions.
- **Shape:** radius 0 everywhere (`rounded-*` is a lint error). Borders: hair 1px / rule 2px / bold 4px. Spacing: 4px grid (arbitrary values = lint error).
- **Shadow:** the only legal shadow is `elevation.hard = 8px 8px 0 0 border-strong`. No gradients, no blur. The picker circle's conic-gradient is the sole exception (demo chrome, scoped to its own button).
- **Motion:** fast 200ms (hover/click) · base 300ms (state) · slow 450ms (panels). ease-out entering / ease-in (shorter) exiting / ease-landing for panels. Stagger 70ms. `prefers-reduced-motion` honored (static default; motion inside `@media (prefers-reduced-motion: no-preference)`). Manual `localStorage` motion toggle for ADHD. **No idle/ambient/pulse animation.** Step control beats easing polish.

### 2.3 Shared `--fig-*` figure system — **LOCKED**

`figures.tsx` exports `Figure({ spec })`. Skin-neutral; reads only `--fig-accent / --fig-accent-ink / --fig-ink / --fig-line / --fig-rail / --fig-node-fill / --fig-node-ink / --fig-muted / --fig-font`. Kinds: `tree | merge | timeline | prob-tree`. Geometry computed from spec, never baked.

- **LIGHT-surface re-skin map (the carry-forward all working surfaces use):** `--fig-ink → page-fg · --fig-line → border-thin · --fig-rail → rgba(0,0,0,.15) · --fig-node-fill → accent · --fig-node-ink → page-fg · --fig-accent → accent · --fig-muted → border-thin`.
- Open-figure treatment (own column behind a 1px hairline + `FIG. N` caption) is **locked — never a boxed card**.
- For KCs whose figure kind isn't yet wired: honest `FIG. NOT YET WIRED` placeholder at opacity-40 (degraded mode, surface 14).

### 2.4 Nav shell — **LOCKED (one taste call inside, §5)**

New root `AppShell` replaces the inline header/nav in `App.tsx`. DARK masthead (48px = 12×4, 4px accent-rule bottom border). Brand mark + `ThemePicker` circle left; nav links (`workspace / oggi / materie / ledger / me`) right with `aria-current` accent-bg. Content well `flex-1 min-h-0 overflow-hidden`. Max-width 1600px, `clamp(28px, 6vw, 120px)` padding — **matches DoorBrutalist's container exactly so the door→workspace transition has zero layout shift.** Owns skin/palette state, writes CSS vars to `:root`, persists to `localStorage`, passes `concepts=[]` to strip the demo Concept row. Mobile bottom tabs are an overlay (surface 0i), desktop has no bottom chrome.

### 2.5 Correctness & trust system — **NEW (council `1780407101`)**

The system verifies content, never Alex (systematized [[feedback_no_oracle_inversion]]). Every KC fact (definition, invariant, grader-rule, misconception refutation) must clear (refined by council round 2):
- **Re-derive against the LIVE source, not the stored quote** — the verifier re-locates the span in the original PDF (page + char-offset locator + fuzzy-match guard), because the YAML `source.quote` is itself an extraction artifact that can be mis-selected / truncated / mis-attributed. Citation-to-a-stored-string proves nothing.
- **Two-family re-derivation + one NON-LLM leg** — a *different* model family independently re-derives the fact; they must agree. Critically, ≥1 leg must be non-LLM (SymPy / unit-test execution for grader-rules, or a small human-cleared gold span per subject) so the legs don't share LLM pretraining bias — two free models can agree on the *same* commonly-mistaught error. If a 429-fallback collapses two families to one, the fact stays **UNCERTAIN** (fail toward doubt).
- **Span↔claim round-trip** — a different family checks the live span actually *entails* the claim (catches a dropped "not", ≤ vs <, a refutation on the wrong KC).
- **FSRS quarantine** — a KC cannot enter spaced-repetition or the cohort cold-start corpus until it clears the audit. **No auto-clear by "N correct student attempts"** — an oracle-blind learner's N corrects measure *consistency, not truth*, and would launder a wrong fact into the shared corpus; attempts may lift the display hint but never promote a KC.
- **Grading fail-safe** — every "you were wrong" verdict on an open answer is cross-checked before shown; one `REPORT WRONG` hard-pauses FSRS for that KC (fail-safe, not fail-silent).

**Honest guarantee (First-Principles):** a closed stack of LLMs can only ever certify *faithfulness to your source*, never *truth* — and the irreducible floor is a claim that is wrong *in your lecture itself*, which both families faithfully reproduce. So the trust badge reads **"matches your lecture / faithful to your source," NEVER "verified" or "correct,"** and source-internal error is surfaced to the student as the explicit boundary of the guarantee.

**This is also UI work (NEW surface → render queue).** A trust surface: the faithful/uncertain badge per fact, a quarantine "being checked" state, the report-wrong flow, and an honest "not yet verified" mode — never a fake-confident wrong answer. This surface is the *link* between the verification engine and the student.

---

## 3. Locked exemplar: the concept door

Already built and locked — the reference every surface inherits.

- **Two skins, both in-brand differently.** `DoorBrutalist` = the canonical DESIGN.md brand (DARK, mono, one yellow, hard rules, 8px hard shadow on the Begin CTA). `DoorWarm` = explicit, file-header-flagged off-brand exploration, scoped under `.door-warm`, opt-in only.
- **Page-aware Figure system.** `DoorConcept.figure` (discriminated union) drives the diagram; swapping concept changes geometry, not just a label. Color via `--fig-*` at the door root. (§2.3.)
- **ThemePicker = brand-circle popup.** Conic-gradient circle in the masthead → 288px dark panel: layout toggle + 6 palettes + custom accent/2nd pickers. Concept row demo-only.
- **Concept switcher is demo-only** — lives in `DoorCompare` / `/door-compare`; the student never picks which concept door to see (learning-engine-driven).
- **6 palettes** (`palettes.ts`): `coral-teal · indigo-mint · amber-sky · magenta-lime · forest-gold · brand-yellow` (default). `brutalistVars()` injects 3 vars; `warmVars()` the full 12-var family.

---

## 4. Surface-by-surface plan

Grouped by loop. Each block: focal claim · layout gist · reused/new components · viz · key interactions · R-watch · open decisions. **Self-see** flags render-before-lock.

### 4.1 Core working loop (the drill engine)

**Loop-opening + sequencing fixes — council `1780407101` (apply across the loop):**
- **Worked-example-first for cold/low mastery.** A genuinely new concept opens with a *faded worked example* (study a solved example → completion problem with a blank → full problem), NOT a cold pretest. The worked→solve fade is keyed to PFA mastery (expertise-reversal: scaffolding drops as mastery rises). Pretest-first is kept only when mastery/placement shows enough prior schema.
- **Phase count gated by entry mastery** — a concept the placement signal shows half-known skips early phases instead of marching all five.
- **Interleaving** — the queue interleaves problem types within a subject (teaches "which method applies", a core exam skill), not just one-KC-at-a-time.
- **Self-explanation prompts** (Chi / Renkl) on the worked-example + misconception surfaces — the learner must explain *why* each shown step holds. Without this, a non-self-verifying novice studies solved examples passively and the worked-example effect degrades to mere reading. Highest-value cheap add; pairs with the existing PredictionGate/EchoBand.
- **Far-transfer probe** — each KC carries a variant that holds the deep structure but changes the surface story (Gick-Holyoak analogical transfer), not just reworded — pure rephrasing only buys near transfer and is another template. (Dual-coding via the `--fig-*` Figure system + the generation effect on completion-blanks are already latent in the design — name them so they're deliberate.)

#### 0c — Lesson Entry (mastery 0) / Door Handoff · **self-see: YES**
- **Focal claim:** the dark door cracks into working light; one invitation — answer the opening concrete question, don't read.
- **Layout:** LIGHT, 7fr/3fr. Door masthead carried onto white. Transitional **accent-rule band** bridges dark→light. Left: display-size pretest question + free-text input. Right: the same door Figure, re-skinned LIGHT, static, `FIG. 1`. No scroll on first paint @1280.
- **Reuse:** `DoorBrutalist`, `Figure`, `AlgoStepperShell`, `MathText`, `ProgressStrip`. **New:** `LessonEntryBand`, `ConcreteQuestionBlock`.
- **Open:** band height (4px rule vs ~32px solid stripe) · figure static here, interactive on 0d · answer box LaTeX · ProgressStrip placement.

#### 0d — Mid-lesson (Term Landing) · **self-see: YES**
- **Focal claim:** exactly ONE new term lands, wired to the student's own just-submitted intuition — "you did X, that's called Y." Yellow is on the term, nothing else.
- **Layout:** LIGHT, 7fr/3fr. Reading column: EchoBand (their answer quoted, 2px accent-rule) → bridge sentence with the TermLanding span (accent bg / black ink) + RO/EN gloss → definition plate → InvariantRule (4px accent blockquote) → PredictionGate (two hard-bordered buttons). Right: door Figure, interactive via `AlgoStepperShell`.
- **Reuse:** `AlgoStepperShell`, `MathText`, `ProgressStrip`, `InlineAskChip`, `ConceptInline`, `CitationPill`. **New:** `EchoBand`, `TermLanding`, `InvariantRule`, `PredictionGate`.
- **Open:** scroll-sync vs explicit-NEXT (ADHD → recommend explicit) · echo verbatim vs paraphrase (recommend verbatim) · example static vs LLM · CONTINUĂ sticky vs in-flow · CitationPill inline vs popover.

#### Drill canvas — predict-then-reveal · **self-see: YES**
- **Focal claim:** you must commit a hypothesis BEFORE you may see anything — prediction is the price of admission.
- **Layout:** LIGHT, door masthead verbatim. 7fr/3fr. Question stem (display) → predict plate (`DrillStack` `drill-prediction-input`) → answer textarea **visually gated** at 35% opacity + `LOCKED · submit a prediction to unlock` until prediction non-empty, then animates to 100% (opacity-only). CTA `CHECK ANSWER →` flat accent (no 8px shadow — reserved for the door Begin).
- **Reuse:** `DrillStack`, `DrillCard`, `ProblemStepper`, `MathText`, `RoutedViz`, `Figure`, `AlgoStepperShell`. **New:** `PredictGate`.
- **Open:** answer 35%-ghost vs hidden · CTRL+ENTER target when focus ambiguous · monument stem scale for long stems.

#### Surface 1 — Feedback ladder L0–L4 (**the keystone**) · **self-see: YES**
- **Focal claim:** you are exactly one rung up a visible ladder, and the system gives the LEAST that still moves you.
- **Layout:** inside `DrillCard` below the answer. Ladder rail (5 square pips, door pip-opacity logic) + reveal panel escalating L0 direction → L1 strategy (accent-rule) → L2 worked next-step → L3 2-of-3 example with blank → L4 full solution + Misconception ribbon on a DARK strip. `I GIVE UP` = low-contrast (friction is load-bearing).
- **Reuse:** `DrillCard`, `DrillStack`, `ConceptInline`, `KnowledgeGapCard`, `MathText`. **New:** `FeedbackLadder`.
- **R-watch:** **two-yellow tension** — live pip (`accent`) vs L1 left-rule (`accent-rule`). Verify on render.
- **Open:** is `#eab308` distinct enough vs `#fde047`, or drop yellow from L1 · all-5-rungs visible vs progressive · L4 DARK vs LIGHT · per-subject L0 copy.

#### Surface 2 — Hint design (static / LLM / show-me) · **self-see: NO**
- **Focal claim:** asking for help is a deliberate, costed, three-flavor choice — cheapest first; "show me" carries friction.
- **Layout:** compact control row at the bottom of `DrillCard`: `[ HINT ]` (→L1) · `[ ✨ ASK ]` (LLM, → `Sidekick`/`AskGutter`) · `[ SHOW ME ]` (low-contrast, →L4) + pip cost-meter. One accent, migrates to cheapest available.
- **Reuse:** `DrillCard`, `InlineAskChip`, `AskGutter`, `ChatPane`, `Sidekick`, `DrillStack`. **New:** `HintControlRow`.

#### 0e — Retrieval gate + confidence · **self-see: YES**
- **Focal claim:** closed-book recall under a soft timer; rate confidence BEFORE you see if right — the confidence read is the point.
- **Layout:** DARK, door composition. `RETRIEVE · NO NOTES`. Monumental prompt → answer plate → ConfidenceRow (`DEFINITELY/MAYBE/GUESS/IDK`, RO gloss). Soft timer = single depleting accent rule (no pulse). Post-reveal 2×2 calibration cell.
- **Reuse:** `AlgoStepperShell`, `Figure`, `MathText`, `FsrsReview`, `KnowledgeLedger`. **New:** `RetrievalGate`, `ConfidenceRow` (reused by surfaces 6, 9).
- **R-watch:** R7 sequencing must be code-enforced — one yellow per beat.
- **Open:** timer presentation · confidence-before-reveal scope · DARK vs LIGHT · 2×2 hit-cell vs full grid.

#### 0f — Misconception ribbon · **self-see: YES**
- **Focal claim:** your wrong intuition has a name — here is the refutation.
- **Layout:** LIGHT, inline below `DrillCard` at L4 (not modal). DARK kicker stripe (`MISCONCEPTION · {id}`) → refutation (one accent-highlighted wrong-intuition phrase) → worked example. Right: `GOT IT →` (accent, 8px shadow) · `ADD TO FSRS` · `REPORT WRONG`.
- **Reuse:** `DrillCard`, `KnowledgeGapCard`, `FsrsReview`. **New:** `MisconceptionRibbon`.
- **Open:** does the misconception YAML carry `figure_spec` (snake_case on the wire — `ContentSchema.kt` field + the inline `/drill/grade` misconception payload; `MisconceptionRibbon` reads it as the camelCase `figureSpec` prop) · kicker height for long ids · LIGHT black-shadow confirm.

#### 8 — Scratchpad (Type / Draw / Cornell / Photo) · **self-see: YES**
- **Focal claim:** always one keypress away, remembers the mode you left it in.
- **Layout:** extends `Scratchpad`/`ScratchpadDrawer`. DARK micro-bar + 4-tab switcher. TYPE textarea · DRAW canvas + tools · CORNELL 3fr/1fr split · FOTO dashed drop-zone + thumbnails.
- **Reuse:** `Scratchpad`, `ScratchpadDrawer`, `ChipRow`, `ScreenshotCapture`. **New:** `ScratchpadDrawCanvas`, `CornellLayout`, `PhotoStrip`.
- **Open:** DRAW/FOTO persistence · AI co-scratch scope · Cornell proportion.

### 4.2 High-stakes

#### 0g + 9 — Mock exam (shell + grading) · **self-see: YES**
- **Focal claim:** a real timed exam with an honest per-question grade — no drill, no scaffold, no hints.
- **Layout:** View A LIGHT 7fr/3fr (numbered blocks, sticky timer + nav + SUBMIT). View B LIGHT single-col (`REZULTAT` score plate + per-question table + `MockScoreSparkline` + redo-wrong CTA).
- **Reuse:** `MathText`, `DrillCard`, `ProgressStrip`, `CompileSubmitCard`, `StatusBar`. **New:** `MockExamShell`, `MockTimer`, `MockQuestionBlock`, `MockQuestionNav`, `MockGradeReport`, `MockScoreSparkline`.
- **Grading is SYNC, 200-only (LOCKED — H13, `master-impl-plan-v2.md` §2.2 `/mock-exam/submit`).** SUBMIT → blocking `200 {score, kc_results, narrative}` straight into View B; no spinner-poll, no 202, no "grading in progress" interstitial. Open-ended LLM-graded questions **degrade to an UNCERTAIN per-question state** (badge + `verification_status`, not a "still grading" state) rather than blocking the report. So there is no async grading UX to design.
- **Open:** bank source · per-question points · mobile nav · UNCERTAIN-question cell treatment in the per-question table.

### 4.3 Meta-nav

#### App Shell / Nav · **self-see: YES** — see §2.4 (`AppShell`; reuse `ThemePicker`, `KnowledgeLedger`, `DaemonHealthPill`). Open: nav label language · picker circle left/right · masthead always-dark vs skin-following.

#### 0a — LearnerQueue (Today / Oggi) · **self-see: YES**
- **Focal claim:** the single next card the system chose is already visible — CTRL+ENTER to begin.
- **Layout:** 7fr LIGHT queue / 3fr DARK next-KC mini-door. NEXT row = 4px accent-rule + accent-soft bg. Each row: subject pill + KC title + phase badge + 60px `MasterySparkline`.
- **Reuse:** `ProgressStrip`, `DrillCard`, `DrillStack`, `ChipRow`, `DoorBrutalist`. **New:** `LearnerQueueList`, `MasterySparkline` (shared), `ModePill`.
- **Open:** full door reuse vs compact excerpt · empty-queue copy · day-progress placement.

#### 0b — Subject Map · **self-see: NO**
- **Focal claim:** see at a glance which subject is bleeding mastery.
- **Layout:** LIGHT, 5-equal-column (PA/PS/POO/ALO/SO+RC). `SubjectCard`: code+name + SmallMultiples sparklines + accent fill-bar. Lowest-retention card gets the 4px accent-rule (the focal accent).
- **New:** `SubjectCard`, `MasterySparkline` (shared), `RetentionGapBadge`.
- **R-watch:** **5-equal-column breaks R6 (asymmetry)** — equal-peer justification, **needs Alex sign-off**.

#### 0h — Ledger drawer · **self-see: NO**
- Right-edge slide-in, 480px, DARK, 4px white left border. `JURNAL / LEDGER` + filter chips + gap rows (status square accent=open / white=resolved). **Reuse:** `KnowledgeLedger`. **New:** `LedgerRow`. Open: header sparkline? · confirm `ledger.opened` telemetry gate won't delete `KnowledgeLedger`.

#### 0i — Mobile bottom tabs · **self-see: NO**
- Fixed 56px DARK strip <768px, 4 cells AZI/MATERIE/JURNAL/EU, active = accent bg. **New:** `BottomTabBar`, `TabIcon`. Open: Day-Of replace vs 3-tabs · badge count · who authors glyphs.

#### 3 — Day-Of exam mode · **self-see: YES**
- **Focal claim:** calm and ready — the countdown is the only number, the checklist the only task.
- **Layout:** single-col DARK, 3 bands. Subject display-xl + monumental countdown `HH:MM:SS` clamp(56–120px) in accent + one Jamieson reappraisal sentence. 6-card review strip / FII checklist. **No** Subject Map / new lesson / mastery% / cohort / streak; nav hidden.
- **New:** `DayOfShell`, `DayOfCountdown`, `DayOfReviewCard`, `DayOfChecklist`.
- **Open:** exam-start-time UX · review-card subject scope · checklist persistence · Jamieson copy authorship.

#### 18 — Cross-subject daily notification — spec-listed (ships, extends R5 email). **Carried to build plan**, designed from the notification shape at build time.

### 4.4 Foundation surfaces

#### 4 — First-time onboarding (5 steps) · **self-see: YES (Step 2 only)**
- Full-page DARK each step (the door IS the onboarding): masthead + `PAS N DIN 5` pips + door grid. S1 AI literacy (`AiLiteracyGate`, prob-tree) · S2 ToS/privacy (merge figure as data-joining metaphor) · S3 placement intent (`ChipRow`) · S4 profile + `LangToggle` · S5 notifications. **New:** `OnboardingShell`, `ToggleRow`. Open: S2 metaphor clarity · S3 granularity · S5 channels.
- **Design input (frame, not a locked step list):** onboarding **is** the preference-collection sequence — it is where the user first sets the choices the rest of the app reads. Candidate prefs to surface when this screen is actually designed: **theme-pick** (onboarding = the *first-set*; masthead brand-circle = *change-later* — same `ThemePicker` palette+layout state per §2.4/§2.1) · **motion / reduced-motion** (the §2.3 manual `localStorage` motion toggle, ADHD) · **ADHD-mode** · **language** (RO/EN, the §4.4-17 `LangToggle` default) · **voice-later** (DEFERRED — tombstone only per Surface 11, parked). Do not lock the step order or which prefs land in which step here — that's a self-see/design call.

#### 10 — Cold-start placement primer · **self-see: YES**
- DARK door-language, 8 pips, one question at a time 7fr/3fr (input + "why this matters" aside, no figure). Forward-only, no hints. Post-submit `PlacementResultBanner` mini node-graph (8 KCs via `--fig-*`, **deterministic grid layout**). **New:** `PlacementShell`, `PlacementQuestion`, `PlacementResultBanner`. Open: KCs-per-subject=8? · hand-authored vs DSPy · counts as a session?

#### 13 — Empty states (3 variants) · **self-see: NO**
- LIGHT, left-aligned 560px (not centered). 2px ceiling rule, monumental reason (one accent verb via Gist), one accent CTA. V3 connection-lost = DARK, RETRY text-link. **New:** `EmptyState`. Open: V3 connectivity sparkline? · confirm RO copy.

#### 14 — Error / degraded states (3 tiers) · **self-see: YES (Tier 3 only)**
- Tier 1 LIGHT inline card (danger left-rule, hairline RETRY). Tier 2 DARK top strip (`StatusBar`). Tier 3 full-page DARK door masthead + monumental reason + one accent `REÎNCARCĂ`. **New:** `InlineErrorCard`, `DegradedBanner`, `FatalErrorPage`. Open: Tier-3 frozen figure vs negative space · who owns the Tier-2 top strip.

#### 17 — Bilingual language toggle · **self-see: NO**
- Persistent masthead chrome, `RO | EN` (active = accent fill). App-wide RO primary + EN gloss @60%. Term-glossary lock (fork/thread/mutex/pointer/stack/heap verbatim). Diacritics ț ș comma-below mandatory (cedilla = CI fail). **New:** `LangToggle`, `BilingualText`. Open: confirm the never-translate list from real FII materials.

### 4.5 Review / memory

#### 5 — End-of-session wrap pane · **self-see: YES**
- **Recommend the door-spread version** (1.05fr/0.95fr) over a centered 640px sheet — resolve on render.
- **Focal claim:** you finished — here is what moved today, one number that is not a streak.
- LIGHT overlay. Monumental `{N} CARDS / MASTERED` (accent on outcome) + retention plate + narrative + `DONE →`. Right open figure: `MasterySparkline` delta-mode. No streak/XP/badges. **New:** `SessionWrapPane`, `MasterySparkline`. Open: LIGHT vs DARK · KC cap (6 + "and N more") · auto vs on-demand.

#### 6 — Confidence calibration plot · **self-see: NO (single-col) / YES (7fr/3fr variant)**
- **Recommend single-col 4-bucket** (mechanical, maps to the DB confidence enum). LIGHT, max-900px, hand-SVG scatter (conf bucket × accuracy, dashed perfect-line, fill accent above / dark below, radius ∝ N) + `CalibrationTable` (OVER danger / UNDER accent / CALIBRATED). **New:** `CalibrationPlot`, `CalibrationTable`. `PlotlyEmbed` deprecated. Open: min attempts/band · route vs drawer.

#### FSRS spaced-repetition review (`FsrsReview` upgrade) · **self-see: YES**
- LIGHT 3 regions. Masthead `_ DUE · _ DONE` + `MasterySparkline`. Card 7fr/3fr (DARK header / LIGHT body, **opacity cross-fade — drop the rotateY flip**) / right `ForecastDotPlot` + 4 grade buttons (GOOD = the one accent). **New:** `MasterySparkline`, `ForecastDotPlot`.
- **R-watch:** `ProgressStrip` uses `rounded-full` dots = R3 violation → square pips when mounted (or patch `ProgressStrip`).

#### 15 — Mastery sparkline strip (replaces streak) · **self-see: NO**
- Inline primitive embedded in 0a / 0b / 5 / 6 / 0h. LIGHT, 28px, hand-SVG N segments (accent ≥0.8 / muted 0.3–0.8 / dark <0.3), height = score, hover tooltip. **New:** `MasterySparkline` — **the shared primitive (build once).** Open: API shape (`GET /api/v1/mastery?subject=X`?) · max-N · threshold bands (phase-aligned vs spec).

### 4.6 Specialized

#### 7 — Lab sandbox (V86 + xterm.js) · **self-see: YES**
- LIGHT 7fr/3fr. Left full-height xterm.js (DARK micro-bar, black term / white text / yellow cursor). Right LIGHT: objective card (4px accent-rule) / step guide / live `LabGradeReadout` (accent checks, WebSocket). RESET (ghost) + SUBMIT (accent). **New:** `LabShell`, `LabGradeReadout`, `LabObjectiveCard`. Open: V86 warm/cold · assertion DSL · first lab · mobile · no-retry pedagogy.

#### 11 — Voice mode · **DEFERRED · tombstone only**
- Ships now ONLY as a disabled `VoxButton` (`VOX [ÎN CURÂND]`) so surfaces aren't designed voice-hostile. **Open (load-bearing):** unlock condition — memory says ElevenLabs $5/mo or clone Alex's voice; spec says Piper ($0, MIT). **Confirm: is Piper sufficient?**

#### 12 — Settings / Me tab (GDPR + AI Act) · **self-see: NO layout / YES consent-sparkline**
- LIGHT `/me`, extends `SettingsMe`. 7fr/3fr: content / DARK `RightsSidebar` (GDPR Art 15/17/18/22 + AI Act). Export JSON (one accent CTA) · Pause (secondary) · Delete (danger two-click). **New:** `RightsSidebar`. Open: keep consent sparkline? · Annex IV link.

### 4.7 Cohort (C1–C7) — multi-user only

Presence pill · anon Q&A · confusion-map · study-group async · co-curator suggest-edit · peer code review · budget transparency. **Out of scope for the single-user spine** — carried to the build plan as a gated multi-user phase. `SuggestedEditCard` already exists and seeds C5.

---

## 5. Render queue (self-see loops, in build order)

The drill loop leads (keystone + most-used).

1. **Surface 1 — Feedback ladder L0–L4** — rung-rail + graduated reveal + two-yellow tension.
2. **Drill canvas (predict-then-reveal)** — monument stem + predict-gate unlock on LIGHT.
3. **0d — Mid-lesson / Term Landing** — scroll-sync + EchoBand-accent vs TermLanding-accent.
4. **0c — Lesson Entry / Door Handoff** — the dark→light transitional band.
5. **0e — Retrieval gate + confidence** — confidence on DARK door + depleting timer.
6. **0f — Misconception ribbon** — DARK kicker inside LIGHT frame.
7. **8 — Scratchpad** — 4-tab DARK switcher + Cornell layout.
8. **0a — LearnerQueue** — 7fr LIGHT / 3fr DARK live mini-door.
9. **FSRS review upgrade** — card+grade split, ForecastDotPlot.
10. **5 — Wrap pane** — monument type on LIGHT + slide-up.
11. **3 — Day-Of** — monumental countdown composition.
12. **0g+9 — Mock exam** — accent-fill score plate.
13. **7 — Lab sandbox** — DARK terminal / LIGHT panel split.
14. **10 — Placement primer** — prose aside + result node-graph.
15. **App Shell / Nav** — brand+picker in the dark strip.
16. **Partial renders:** Onboarding Step 2 · Error Tier 3 · Calibration 7fr/3fr (if chosen) · Settings consent sparkline.

**Mechanical (build from spec, no render):** 0b · 0h · 0i · Surface 2 · 13 · 14 Tiers 1–2 · 17 · 6 (single-col) · 15 · 12 layout · 11 tombstone.

---

## 6. Open decisions for Alex (deduped)

**Load-bearing / before build:**
1. **Brand lock (§2.1)** — brutalist-yellow default + warm/palettes as production themes, picker = palette+layout only, concept switcher demo-only. *(NEEDS CONFIRM)*
2. **Voice unlock** — Piper (free) sufficient, or wait for ElevenLabs/cloned voice? (memory vs spec disagree)
3. **0b Subject Map R6 exception** — 5-equal-column for equal-peer subjects OK?
4. **Term-glossary lock list (17)** — confirm never-translate terms from real FII materials.

**Taste calls (resolve on render or directly):** feedback-ladder two-yellow + rung visibility + L4 surface · drill answer-field ghost vs hidden · 0d scroll-sync + echo verbatim · 0e timer + retrieval surface · wrap pane door-spread vs centered + LIGHT/DARK + trigger · calibration single vs 7fr/3fr · scratchpad persistence · lab V86/DSL/first-lab · mastery bands · Day-Of timing + copy · FSRS cross-fade + kbd chips · misconception schema · ledger sparkline · onboarding S3/S5 · empty/error sparkline inclusion · settings consent sparkline.

**Backend contracts to lock (block specific components):** session-close API (wrap) · `/api/v1/fsrs/forecast` mastery bands · `GET /api/v1/mastery?subject=X` · calibration aggregation · lab assertion format · `exam_dates.start_time`. *(Mock-exam grading is NOT open: it is LOCKED SYNC 200-only — H13, `master-impl-plan-v2.md` §2.2 `/mock-exam/submit`; open-ended degrades to UNCERTAIN, no async path.)*

---

## 7. Handoff to the build plan

Once §2.1 (brand) is confirmed and the load-bearing calls (§6.1–4) resolve, the implementation plan (`superpowers:writing-plans`) sequences the build **foundation-first**: lock the token map + type scale + motion config, build `AppShell` (gates every surface's chrome + the production `ThemePicker` strip), then the **shared `MasterySparkline` primitive** (reused by 0a/0b/5/6/0h — build once), then the drill loop in render-queue order (ladder → predict-gate canvas → 0d → 0c → 0e), running the self-see loop per §5 before locking each. Every "create new component" task pairs with a "mount it in [exact file]" task showing the JSX diff (ghost-component lesson); the final Playwright gate asserts each surface's `data-testid` paints + zero 4xx/5xx on first paint and after every interactive click.

**Coupling to teaching-engine Stage 1:** the design assumes Stage-1 inputs exist — the FSRS write path, the `attempts.confidence` enum, per-KC mastery floats, the queue/today endpoint, and the KC YAML (definitions, invariants, misconceptions, `figureSpec`, placement questions, Jamieson copy). Surfaces consuming those build against mocked envelopes + the `FIG. NOT YET WIRED` / insufficient-data degraded modes until the matching Stage-1 contract lands.

**State model the correctness gate requires (council round 2 — define BEFORE wiring, else the gate degrades to a confidence flag = the "verification net" already rejected):** `fsrs_cards` today is keyed on free-text front/back with no KC link — add a `kc_id` FK + a `quarantined`/`paused` flag so "quarantine" and "REPORT WRONG pauses this KC's cards" have something to act on. The KC schema (`ContentSchema.kt`) has `grounding_tier` but no `verification_status` enum and lacks the `invariant`/`grader_rules`/`stem_template` fields the two-family re-derivation compares — add them. Mastery (`KcMastery`, flat EWMA) needs `phase` as its own column (not derived off the float) for the mastery-keyed worked-example fade. Resolve the operational seams to concrete state transitions: what counts as families "agreeing", what resurfaces a quarantined KC, and — since Alex provably cannot — what authority clears the audit (the non-LLM leg above).
