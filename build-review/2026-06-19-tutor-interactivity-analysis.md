# Tutor Interactivity Analysis — how it should HELP during studying, not be a static content site

## 1. The reframe

You already built every organ of a tutor, then wired them as a content site. A real tutor has two loops: an **outer loop** (pick the next thing from a model of *this* learner) and an **inner loop** (help *while* you're stuck on the current thing). Your outer loop is real and running — the KC selector orders by mastery + prerequisites, FSRS reschedules 828 live cards, and a resolver computes advance/hold/remediate. But the inner loop is **computed and then thrown away**: the server decides "remediate," the signal reaches the drill component, and the component ignores it. The conversion to a tutor isn't new ML — it's wiring signals already on the wire to a behavior.

## 2. Where it stands today

| Capability | Status | Grounded ref |
|---|---|---|
| Outer loop: KC selection by mastery + prereqs | **built** | `LockedNextKcSelector`, `GET /api/v1/queue/today` |
| FSRS scheduling (828 live cards) | **built** | `GET /api/v1/fsrs/due` |
| Phase resolver → `advance/hold/remediate` | **built** | `NextPhaseResolver.kt:28-37` |
| `next_phase_action` delivered to client | **built but ignored** | `drillGrader.ts:44`; `DrillStack` never reads it |
| L0–L4 feedback ladder (stored rungs) | **built, fires once post-grade** | `FeedbackLadderBuilder.kt:39`, `FeedbackLadder.tsx` |
| Confidence + PREDICT captured | **captured then discarded** | `DrillStack.tsx:120-143`, `RevealBeat.tsx:53-60` |
| Sidekick ask (citation + spoiler guard, Jaccard ≥ 0.7) | **built, mounted elsewhere** | endpoint `TutorRoutes.kt:1873`; guard `SelectionQueryBuilder.kt:51` |
| Practice graders (proof/trace/code, execution-leg-first) | **backend built, no frontend** | `PracticeRoutes.kt:250-456` |
| Misconception remediation re-queue | **planned** | spec §7.3 / Phase G |
| Struggle / wheel-spin detection (in-session) | **missing** (spec names trigger, not built) | spec §7.3 / EC3 |

## 3. The gap

The tutor **senses and decides, then doesn't act on its own decision.** `remediate` is display-only. The ladder fires once, with no escalation. There's no "stuck right now" detection, no hint contingent on the current stuck-point, no scaffold fade. The inner loop is the missing organ — and almost all of it is wiring an existing signal to an existing stored artifact.

## 4. Recommended interactive capabilities (ranked)

Leverage = closes a loop × reuses a built rail × small. Every item routes among **stored, pre-gated** artifacts — none asks an LLM to be correct in real time.

**1. Remediation routing** — *highest leverage.*
On a miss, auto re-route (re-queue a misconception drill or drop to prereq) instead of just showing feedback. · *Pedagogy:* VanLehn inner loop + mastery-based advancement (don't advance an unmastered KC). · *Reuses:* the signal is already on the wire (`NextPhaseResolver` → `drillGrader.ts:44` → `DrillStack`); just branch on `remediate` into the existing re-queue + selector. · *Truth-safety:* deterministic resolver + non-LLM grader decide; re-queued drill is faithful-checked stored content. · *Size:* small. · *Extends* (Phase G backend exists; client branch missing).

**2. Contingent hint ladder.**
"Un indiciu" gives the least-directive nudge first, escalates one rung per click, gates the bottom-out behind a dwell floor. · *Pedagogy:* contingent tutoring (Wood & Wood) — minimum assist that unblocks, preserving productive struggle. · *Reuses:* `FeedbackLadderBuilder` rungs already exist; repurpose post-hoc → request-driven, one rung per click. · *Truth-safety:* every rung is pre-authored stored text; bottom-out reveals the stored worked step. Dwell gate + a self-explanation rung before the next hint resist gaming. · *Size:* small–med. · *Extends.*

**3. Struggle / wheel-spin detection → strategy switch.**
After N consecutive misses on one KC, change tack (worked example / easier instance) instead of more of the same. · *Pedagogy:* wheel-spinning (Beck & Gong); intervene on flailing, not on effortful progress. · *Reuses:* attempts already server-recorded; spec already defines the N-failure trigger and the BeatSelector easier-variant bias. · *Truth-safety:* trigger is a **count of non-LLM verdicts**; response selects stored content. · *Size:* med. · *Extends §7.2/7.3.* · **Flag:** threshold is undefined in spec — ship behind a conservative default.

**4. Confidence-calibration callback.**
When a **high-confidence** answer is **wrong**, name that specific mismatch — the highest-yield teaching moment. · *Pedagogy:* calibration / illusion-of-knowing (Dunlosky) + predict-observe-explain. · *Reuses:* confidence and PREDICT are both already captured then discarded; per-option callback text is already stored (§4.1). Just cross confidence × correctness and surface the stored callback. · *Truth-safety:* correctness from non-LLM grader; callback is pre-authored text — pure echo. · *Size:* small. · *Extends.*

**5. EC1 ask-in-lesson panel.**
A small panel anchored to `(kc_id, beat)` lets a stuck learner ask without leaving the beat. · *Pedagogy:* help anchored to where the learner is; ADHD one-thing focus. · *Reuses:* the **Sidekick endpoint is fully built** (`TutorRoutes.kt:1873`) with subject-scoped retrieval + the drill-self-paste **Jaccard ≥ 0.7** spoiler guard (`SelectionQueryBuilder.kt:51`); mount the same call in the lesson. · *Truth-safety:* citation-anchored; spoiler-guarded; **never allowed to ask "is this right?"** · *Size:* med. · *Extends EC1.* · **Flag (real gap C1):** nothing gates the *answer's correctness* mid-lesson — keep it advisory/clearly-an-aside, never let its text feed the grader. Store starter prompts at digestion (no generation on the request path).

**6. Faded worked examples.**
Across a KC's sequence, support comes down: filled skeleton → blanked steps → solo. · *Pedagogy:* worked-example effect + completion/fading + expertise-reversal (fading is mandatory). · *Reuses:* worked examples already stored and mounted; fade level driven by the EWMA the selector already reads. · *Truth-safety:* faithful-checked content; deterministic fade; learner's steps graded by the non-LLM chain. · *Size:* med, net-new scheduling. · **Flag / demoted:** this is **gated on an unverified assumption** — that stored `worked_example_ro` is *step-decomposed*, not one prose blob. **Verify the corpus shape first.** If prose-only, this is net-new digestion work, not "built on stored content."

**7. Recovery mode ("azi recapitulăm").**
On broad decay, suppress new KCs and serve compressed re-lessons over the decayed prereq cluster. · *Pedagogy:* mastery-based advancement + spaced retrieval; ADHD load reduction, no guilt. · *Reuses:* FSRS due/decay state is live; re-lesson reuses stored beat fields; suppression reuses the selector's prereq ordering. · *Truth-safety:* FSRS arithmetic + non-LLM failure counts; stored re-lesson beats. · *Size:* med. · *Extends EC3.* · **Flag:** thresholds undefined — conservative defaults.

**8. Prereq-peek.**
A prereq term pops the prereq KC's compressed reveal without advancing or scoring. · *Pedagogy:* ZPD scaffolding; externalized working memory (ADHD). · *Reuses:* R-PREREQ edges + stored beat ③ + the figure-render chain. · *Truth-safety:* **read-only, zero mastery/FSRS writes** — structurally can't oracle-invert. · *Size:* small–med. · *Extends EC1.*

**9. Mount the Practice surfaces (cleanup, not new tutoring).**
Make the **proof / trace / code graders reachable** — backend complete, no frontend. · *Pedagogy:* doing, not reading. · *Reuses:* pure wiring of `PracticeRoutes.kt` to new mounted routes. · *Truth-safety:* **these graders are the strongest TRUTH surface in the system** — execution/oracle legs, not LLM judgment. · *Size:* small per surface. · **Heed the ghost-component lesson:** "in the bundle" ≠ "mounted." Each surface needs a first-paint + interaction-smoke probe before "shipped."

## 5. Top 3 to do first

The skeptic's grounded picks. Together, **#1 + #4 + #2 install the inner loop** — all small, all routing stored artifacts, all fully truth-safe.

1. **Remediation routing (#1).** Verified: server computes `remediate`, client ignores it. Smallest diff that converts site → tutor. Zero new model, zero LLM-correctness.
2. **Confidence-calibration callback (#4).** Verified: both signals captured then discarded; callback is stored text. Tiny, and targets the single highest-yield moment (confident-and-wrong).
3. **Contingent hint ladder (#2).** Verified: builder exists. Repurposing post-hoc → contingent preserves productive struggle; the dwell + self-explain gate is a concrete, ADHD-aware gaming countermeasure.

## 6. Honest caveats

**What NOT to do:**
- **No real-time Socratic dialogue.** Iterative "but why?" turns require LLM-generated correctness-bearing content — forbidden by the TRUTH law without a non-LLM dialogue oracle that doesn't exist. The stored self-explanation *prompt* (in #2/#4) is the sanctioned substitute.
- **Defer / pen the multi-turn ChatPane.** It's built but unmounted. Open multi-turn LLM chat is the single item most able to violate no-oracle-inversion / Socratic-ban / no-real-time-correctness. **Cut for now** — or mount only citation-anchored, advisory, never feeding grading. (Mount Practice first.)
- **No focus-breaking noise.** No "you've been idle 3 min" notification nudges as a standalone feature — that's the exact ADHD-focus-breaking failure mode. If re-engagement is wanted, fold it into #3 as a *stored next-micro-step*, not a notification.
- **No affect detection.** No interaction-trace affect model exists; net-new ML, out of scope.

**Truth-safety risks to hold the line on:**
- **EC1 (#5) has no correctness gate** on its mid-lesson answer (only anchoring). Ship advisory-only; never let its text reach the grader.
- **#6 depends on an unverified corpus shape.** Don't build the fade until you've confirmed `worked_example_ro` is step-decomposed.
- **The one place the TRUTH law leaks** is the LLM-judge fallback on quarantined KCs (`decided_by=llm-judge`). A learner-facing "this was machine-uncertain — flag it" affordance would close it by routing *doubt* (allowed), not correctness, to the user. *(Design proposal, not a current fact — the fields `kc_quarantined`/`decided_by` exist; the affordance doesn't.)*

**One capability the synthesis overlooked (design proposal):** a **"why am I seeing this?" loop-transparency readout.** Every recommendation makes the tutor *act* on the learner, but none makes the model *legible* — a glanceable "you're here because KC-X decayed / you missed this twice." The state is already live and read-only (EWMA, FSRS due, `next_phase_action`), so zero truth risk, and it directly serves an ADHD learner who distrusts opaque autonomous decisions. It's the outer-loop analogue of #4's inner-loop transparency.

---

_Grounded refs that matter most: `NextPhaseResolver.kt:28-37` · `drillGrader.ts:44` · `FeedbackLadderBuilder.kt:39` · `DrillStack.tsx:120-143` · `SelectionQueryBuilder.kt:51` (spoiler guard) · `PracticeRoutes.kt:250-456`. Workflow `wf_50c83b82-bea`, 2026-06-19._

---

## 7. Independent review corrections (2026-06-20, workflow `wf_312d2dd3-8d7`)

An independent review re-grounded every load-bearing claim against the code. Verdict: fixes are **good** (correct, truth-safe); set is **not complete** (2 edge additions). Corrections to §2/§4 above:

- **❌ CLAIM REFUTED — practice graders are NOT frontend-less.** `ProofDrill` / `StepTraceDrill` / `CodePractice` / `DeliverableTracker` are **built + route-mounted** (`main.tsx:278-282`) and POST to the graders (`ProofDrill.tsx:42`, `practiceApi.ts:80/102/123/146`). The real gap is **discoverability** — `/practice/*` is reachable only by direct URL, no nav link. So #9 is "add a nav link," not "build a frontend," and it should NOT sit at the tail labelled cleanup — it lights up the system's **strongest non-LLM TRUTH surface**. (This corrects an error repeated in §2 and the app-surface tour.)
- **◐ IMPRECISION (Claim 2).** The L0–L4 ladder already ships click-to-escalate rung reveal (`FeedbackLadder.tsx:24-29`). What's missing is the *contingent-before-answer* trigger + dwell/self-explain gate — not escalation itself.
- **✅ Verified TRUE:** remediation computed-then-ignored (#1); confidence/PREDICT captured-then-unused (#4); Sidekick ask + Jaccard ≥0.7 guard (#5).
- **Corrected Top-3 to start:** #1 → #4 → **(#8 prereq-peek OR one #9 practice surface)**. #2 (hint ladder) drops to immediate fast-follow — it carries the only real design risk (the anti-gaming logic). #8 is structurally safest (read-only, zero mastery/FSRS writes).
- **🆕 Load-bearing ADDITIONS (the edges where an ADHD learner falls off):**
  - **G1 — Session re-entry ("where was I").** A start-of-session resume card from the queue head + last incomplete beat. Pure read, truth-safe. Highest ADHD yield of anything missing.
  - **G2 — Help *offer* bridge (push, not just pull).** On detected struggle (#3), a quiet in-pane "Vrei un indiciu?" affordance offering the existing pre-gated ladder — counters help-avoidance. Must be in-pane, never a notification.
  - **G3 (conditional)** — a shared anti-gaming reveal gate, load-bearing only if #2/#5/#8 ship together (counts/timers only).
- **Confirmed:** `worked_example_ro` is a prose blob → #6 (faded examples) stays demoted = net-new digestion work, not stored-content reuse.
