# Council review — 1779189544

**Problem:** jarvis-kotlin strategic direction — 19/36 §6.4 viz primitives shipped on demo branch (gallery-only, ZERO drill imports), 13 days to finals (Jun 1), multiple unstarted carry-overs (drill-mount integration, S-24/S-30 backend bugs, push-not-pull metacog, plotly migration, deferred backend rewrite per prior council). Where to focus next sessions?

**Proposed approach — 7 candidates:**
- (a) Continue grinding §6.4 viz primitives (17 left)
- (b) Pivot to drill-page mounting — wire 19 viz tiles into real /tutor/?taskId
- (c) Backend bug fix sprint — S-24 + S-30 (live prod breakage)
- (d) Push-not-pull metacog plan + ship
- (e) ChatPane plotly migration (drop ~1MB)
- (f) Resume deferred backend rewrite per prior council (FSRS-6 + Whisper.cpp sidecar)
- (g) Reframe entirely

**Project context:** Jarvis = personal adaptive tutor for solo dev (Alex, FII Iași AI bachelor's). 715 backend tests green, working auth/memory/sensor/telemetry/tutor routes. Kotlin/Ktor + React 19/Vite. Brutalist-mono yellow-on-black. Build-everything mode. No paid LLM API spend. Branch `viz-foundation-demo` 68 commits ahead of main, pushed, NOT merged, NOT deployed.

**Timestamp:** 2026-05-19T11:19:04Z

---

## 🔴 Devil's Advocate

AGENT: Devil's Advocate
STANCE: REJECT
REASONING: Every option (a)–(f) treats this as a "what should I build next" question. That framing is the bug. You shipped 19 brand-new components on a 68-commit branch where the ONLY non-test, non-demo production change is `main.tsx +1 route`. Zero drill pages import viz/. The live student flow at corgflix.duckdns.org/tutor/?taskId is exactly as broken (S-24, S-30) and exactly as un-viz'd as it was on 2026-05-09. You don't have a "what to build next" problem — you have an integration debt that grows ~3 primitives per session and gets paid down ZERO per session. Picking (a) makes the debt bigger; (b) is the only option that converts existing inventory into user-visible value; everything else (c, d, e, f, g) is a context-switch that leaves 19 finished tiles rotting on an unmerged branch while you start a new workstream. The prior council told you to rebuild the backend and you pivoted to viz; if you now pivot away from viz without landing it, the pattern is the diagnosis.
KEY CONCERN: The viz-foundation-demo branch is a 68-commit inventory-overhang with one (1) production line of code in it and a `git merge` path that is currently a no-op for real students. Until those 19 tiles mount in actual drill pages on main, every additional primitive (a), every backend pivot (c/f), every new spec (d), and every refactor (e) increases the probability that this branch never lands at all — which is the exact "ghost component" failure mode your own CLAUDE.md added a load-bearing rule against on 2026-05-11. Candidate (b) is the only option that retires that risk. If the next session is not (b), the honest read is: viz-foundation-demo is on the same trajectory as the Slice 1 ghost components, only at 19x the scale.

## 📚 Domain Expert

AGENT: Domain Expert
STANCE: REJECT
REASONING: Path (a) is the textbook anti-pattern that killed early Khan Academy's "exercise library" experiment circa 2011-2013 — they shipped thousands of standalone interactives that lived in a gallery and were never threaded into the mastery graph; usage data was flat until Sal's team forced every exercise through the knowledge-state engine (the "Knowledge Map" → eventually the mastery framework now visible in their topic trees). Brilliant.org made the same mistake in their pre-2018 redesign with beautiful one-off viz that didn't feed into spaced review; their 2019-2021 rebuild explicitly subordinated content production to the SRS-integrated "course" container with checkpoint logging. The pattern is universal: in adaptive-tutor systems integration depth wins decisively over content breadth, typically at a 1:3 ratio (one new piece of content for every three integration/telemetry/scheduler hookups) — see Anki's plugin ecosystem where the 99% of adoption sits on FSRS-integrated decks not on the thousands of unused cosmetic add-ons, and Duolingo's well-documented internal rule that no new exercise type ships without event-bus instrumentation and Birdbrain/HLR scoring hooks on day one. The 19 viz tiles already shipped are functionally equivalent to 3Blue1Brown's standalone manim videos: pedagogically excellent, zero adaptive value, because manim outputs aren't wired to a learner model — which is precisely why 3b1b is a YouTube channel and not a tutor. Path (b) is the integration step that turns this from gallery rot into actual adaptive tutoring, and the prior council's FSRS-6 + Whisper sidecar (path f) is the OTHER half of the same integration layer; (c) is table stakes because broken auth/memory routes invalidate every telemetry event the integration would emit.
KEY CONCERN: Shipping 17 more viz primitives without drill-mount integration repeats the Khan Academy 2012 "interactive library" failure mode exactly — demo-gallery-without-real-drill-flow has, in every documented case I've seen (Khan pre-mastery, Brilliant pre-2019, the entire CodeAcademy "Labs" graveyard, MIT OCW Scholar's unused Mathlets), rotted into bit-rot within 6 months unless an explicit integration sprint forces every tile through the learner-state engine; with finals 13 days out the correct sequence is (c) → (b) → (f), and (a) only resumes after every shipped tile is drill-mounted and emitting FSRS-gradeable events.

## ⚙️ Pragmatist

AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: Candidate (a) is the cheap-feeling-trap dead-on — 21 components shipped, ZERO non-test imports outside `VizDemoPage.tsx`, branch sat 68 commits + unmerged + undeployed for days. Cost/hour: low (subagent ~4min/primitive) but value-to-Alex-or-classmate-per-hour: zero until mounted. 17 more = 17 more ghosts; this is the literal Slice-1 lesson playing out a second time, same repo, same actor. Senior-engineer move for ONE session: (b) drill-page mounting — pick the ONE highest-cardinality drill type (SO process FSM or PA recursion tree, since both already have shipped primitives), spec the mount-site contract (prop interface + which drill subtype triggers which tile), wire ONE primitive end-to-end into the live tutor flow, deploy, open the URL and confirm Alex sees a viz in a real drill. That alone proves the architectural seam and converts the 21 dead components from "potential value" to "validated pattern" — every subsequent primitive then costs ~5min of mounting instead of being abandoned. (c) backend bugs are 2-4hrs total and hit EVERY user every session (401 on `/last-task` + stale ledger taskId) — strong dark-horse pick because debugging-at-3am pain is real and the fix is small; could even pair with (b) in one session. (d)/(e)/(f) all defer-able: metacog is spec-only, plotly migration is bundle-optimization not user-impact, FSRS sidecar is multi-day infra. Handoff-to-classmate cost of more ghost viz = high (they install repo, see gallery, ask "where does this appear?", answer is "nowhere yet" — burns credibility). Handoff cost of one mounted-and-deployed drill viz = low (they see it on first drill).
KEY CONCERN: Building the spec for (b) is the actual blocker — without a contract for "which drill type triggers which viz primitive + what props get passed + where the renderer lives", mounting will replay the Slice-1.5 prop-mismatch failure. Concrete simpler alternative: in ONE session do (c) S-30 401 fix (~30min, isolated to TutorRoutes.kt, hits every user) + brainstorm-spec for (b) drill-mount contract (~2hr, no code, just nail down the prop interface + one-subject mapping). That ships a real user-facing bugfix AND unblocks all 21 existing primitives + the 17 remaining to land in flow without re-running the ghost lesson. Resist the urge to grind primitives in the same session — finish the architectural seam first or the entire viz investment stays gallery-only.

## 🧱 First Principles

AGENT: First Principles
STANCE: REJECT
REASONING: Strip the framing — Alex is a student 13 days from finals on PA / PS / POO / ALO / SO+RC. The "actual goal" is *passing those finals*, not "shipping a tutor product." A from-scratch design optimized for that goal asks one question: *what compounds Alex's exam score per dev-hour between now and Jun 1?* The answer is **active retrieval against his own course material** — graded recall, spaced over 13 days, on the 6 subjects he'll actually be tested on. That loop already half-exists in this repo: FsrsDueQueue + /api/v1/fsrs/{due,grade,forecast} + FsrsReview flip-card shipped in Slice 1 Phase C/F, plus 1110 .md scraped to `_extras/` per subject (PA=240 PS=165 POO=338 ALO=204 SO=639 RC=130), plus a working sidekick-with-citations, plus a grader that already handles R/Python/C++/free-text. The missing piece is not more viz, not drill-mounting 19 SVG tiles, not a backend rewrite — it's *seeded FSRS cards from his own corpus that he reviews every day for 13 days*. Viz primitives are static gallery art that doesn't compound; FSRS reviews compound exponentially across 13×daily passes. The candidate list (a) through (f) is six flavors of "ship more infrastructure"; (g) Reframe is the only honest answer. The right next session is: bulk-seed 200-400 FSRS cards from `_extras/{PA,PS,POO,ALO,SO,RC}/` using existing infra (LLM card-gen exists; grader exists; review UI exists; due-queue exists), wire a "REVIEW DUE (N)" entry tile, and *Alex opens the live URL tonight and does his first 30-card pass*. Every candidate on the framing list pushes that loop further away.
KEY CONCERN: The framing treats "next feature" and "next study session" as separate questions. They are the same question. Every dev-hour spent on (a)-(f) is one fewer review pass before finals; the viz-foundation-demo branch sitting 68 commits ahead, pushed, unmerged, undeployed, with zero student-facing reach is the load-bearing evidence that the project has been optimizing "what I can build" over "what I learn from." Reframe to: ship FSRS-on-real-corpus *tomorrow*, then study from it daily — the tutor and the finals become the same artifact.

## ⚠️ Risk Analyst

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Top risk per candidate: (a) Continue viz grind = HIGH risk of producing 17 more dead-code primitives — gallery-only consumption means every new tile compounds the unmounted-surface debt; blast radius is wasted session budget + bit-rot on a 68-commit branch already idling against a moving main. (c) S-24 + S-30 unfixed = CRITICAL live impact — 401 on `/api/v1/last-task` is auth-layer catastrophic for any user (including Alex) hitting prod right now; every session that defers this extends the user-impact half-life linearly, and the bug touches the same task-id plumbing that drill pages depend on, so it silently pollutes any future drill-mount work. (b) Drill-mount pivot = HIGH risk of repeating the Slice 1 / Slice 1.5 ghost-component lesson if no Playwright interaction-smoke gate is wired — but it's the only candidate that converts the 19 already-shipped primitives from sunk cost into user-visible value. (f) Resume Python sidecar rewrite = CRITICAL scope risk — re-architecting backend during a period where (c) shows backend is already fragile is the worst possible time; new sidecar + new failure modes + zero rollback story. (d) (e) (g) are MEDIUM — push-not-pull is greenfield with no current consumer, plotly migration is a 1MB bundle win but cosmetic, reframe burns session-zero with no shipped artifact.

The 68-commit unmerged branch is itself a CRITICAL latent risk: every session main drifts (S-24/S-30 fixes, hotfixes, anything) the merge surface widens, and the longer it sits the higher the conflict-resolution cost when the drill-mount integration eventually forces a merge. Bit-rot on `tutor-dist/` bundle artifacts is already visible in git status (deleted assets + new hashes uncommitted).
KEY CONCERN: The compounding-debt scenario — (a) grinds 17 more primitives → branch grows to ~85 commits → main accumulates S-24/S-30 fixes + any production patches → drill-mount session (b) becomes a 3-way merge nightmare against a now-divergent main, AND the primitives never paint-mount because the "ghost-component" failure mode repeats. Mitigation: gate next session on a CONDITIONAL sequence — first land a hotfix branch for S-24 + S-30 directly on main (small surface, fast deploy, restores auth + ledger correctness), THEN open drill-mount spec on `viz-foundation-demo` with a hard rule that every new primitive task is paired with a mount-site task + Playwright interaction-smoke assertion (per the load-bearing rules in CLAUDE.md). No more standalone viz primitives until at least 5 are paint-mounted in a real drill page. This converts dead-code risk into shipped-value, caps merge divergence, and stops bleeding production users on auth failures.

---

## Sanity Check

SANITY Devil's Advocate: PASS — clean. Stays in lane (kills framing per persona). Cites concrete CLAUDE.md ghost-component rule.
SANITY Domain Expert: PASS — clean. Named real precedents (Khan Academy 2011-13, Brilliant pre-2019, Anki/FSRS plugin ecosystem, Duolingo Birdbrain, 3b1b manim, MIT OCW Mathlets, CodeAcademy Labs).
SANITY Pragmatist: PASS — clean. Cost/hour framing + concrete simpler alternative named (S-30 fix + brainstorm-spec for b in one session).
SANITY First Principles: PASS — clean. Rejected framing per persona rule. From-scratch reframe with citation of existing infra. **Reality-check note:** infra claims partially verified — `FsrsDueQueue.kt` + `FsrsCards.kt` + `GapPromotion.kt` + 3 routes (/due, /{id}/grade, /forecast) + `FsrsReview.tsx` + `fsrsClient.ts` all REAL. But `_extras/` directory does NOT exist; corpus actually lives in `tmp-md/` + `tmp-secondbrain-scrape/` + `tmp-study-guide-scrape/` (~611 .md, not 1110). And "LLM card-gen exists" is overstated — `FsrsSource` enum supports MANUAL/GAP_PROMOTION/RUBRIC_CRITERION but there is NO bulk corpus-to-cards seeder; that needs ~2-3hr build. Reframe still holds with this correction.
SANITY Risk Analyst: PASS — clean. CRITICAL/HIGH severity ranks per candidate. Concrete sequence + Playwright gate mitigation.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: WRONG APPROACH

CORE FINDING:
The 7-candidate framing is wrong. 5/5 agents converge on REJECTING (a) more viz primitives. 4/5 advocate (b) drill-mount integration as table-stakes for retiring viz inventory overhang. But **First Principles' reframe is the strongest single argument and overrides the integration-majority**: the project has been optimizing "what I can build" over "what I learn from," and the 13-day window before finals favors compounding daily review over one-shot infrastructure. The verified-real FSRS infra (FsrsDueQueue + FsrsCards + GapPromotion + 3 routes + FsrsReview UI) plus the ~611-doc real corpus in `tmp-md/`/`tmp-secondbrain-scrape/`/`tmp-study-guide-scrape/` means the daily-review loop can ship in ONE session with a ~2-3hr bulk-seeder build.

AGENT CONSENSUS: 3 REJECT framing, 2 CONDITIONAL on (b)+(c). No agent supports (a). No agent supports (d)/(e)/(f) for this window. First Principles' reframe (g) is the only candidate-list answer with daily-compounding value.

KEY ISSUES:

1. **Ghost-component pattern is recurring at scale.** Slice 1 was 5 ghost components; viz-foundation-demo is 19. Adding 17 more (path a) is procrastination by definition. Adding NO new tile until at least one drill paint-mounts is the only honest rule (CLAUDE.md ghost-component rule applies here load-bearing).

2. **Live production bugs are the wrong kind of compounding.** S-24 (ledger-row stale taskId) + S-30 (401 on `/api/v1/last-task`) hit every user (including Alex) every session. Half-life of pain is linear in sessions deferred. Hotfix surface is small (~30min-2hr both). Should land on a hotfix branch on main this session or next, NOT bundled into the demo branch.

3. **Compounding study loop beats compounding infrastructure.** First Principles wins on EV math: a daily 30-card FSRS pass over 13 days = 390 retrieval reps over Alex's exam material. 17 more viz primitives over 13 days = 17 more gallery tiles Alex glances at once. The first compounds his exam score; the second compounds his commit count. They are different products.

4. **Demo branch merge surface widens every session.** Risk Analyst's compounding-debt scenario is real — every main-side hotfix grows the 3-way merge cost when viz-foundation-demo eventually integrates. Either land drill-mount soon or accept the branch may need partial cherry-pick rather than merge.

RECOMMENDED PATH:

**Reframe to: "study tool + drill flow", in this order.**

**Session 1 (today, ~3-4hr) — close the study loop.**
- Build LLM card-gen tool: for each .md in `tmp-md/{PA,PS,POO,ALO,SO,RC}/` + `tmp-secondbrain-scrape/{...}/` + `tmp-study-guide-scrape/{...}/`, use claude-CLI subprocess (per "no paid LLM API spend") to generate 5-10 MANUAL `TutorCard`s per .md → insert via `FsrsCardRepo`. Target: 200-400 cards seeded across subjects (skew toward weak-coverage subjects per `tmp-study-guide-scrape/` zero count on PS+RC — pull those from `tmp-md/` + `tmp-secondbrain-scrape/`).
- Wire a "REVIEW DUE (N)" entry tile on tutor home.
- Deploy to corgflix.duckdns.org/tutor/.
- Alex opens live URL tonight, does first 30-card pass. Validation = Alex sees a queue, grades 30 cards, FsrsState advances on `fsrs_cards` table.

**Session 2 (~1-2hr) — hotfix S-24 + S-30 on main hotfix branch.**
- Small surface, separate from viz-foundation-demo. Restores auth + ledger correctness for any prod hit (including Alex's own FSRS-card grading endpoints). Deploy + verify.

**Session 3+ — decide drill+viz integration ONLY if Alex is doing daily reviews.**
- If Alex completes ≥3 daily review passes in the first week, his demonstrated daily-use proves the project loop closes. THEN mount viz primitives into the FSRS card BACK (or into a "/drill" flow). Viz primitives become drill-back assets in the FSRS loop — both projects collapse into one product.
- If Alex skips reviews after 1-2 days, the reframe failed and the drill-mount work is also moot. Return to the council.

**Defer to post-finals:**
- (a) More viz primitives
- (d) Push-not-pull metacog
- (e) ChatPane plotly migration
- (f) Backend rewrite per prior council

The 19 already-shipped primitives are NOT wasted under this plan — they become drill-back content in Session 3+ if the loop holds. But they only matter AFTER the daily-review loop is proven to compound.

CONFIDENCE: 7

What would shift this rating:
- **Up to 9:** Alex completes first 30-card pass tonight on live deploy. Loop closes.
- **Down to 5:** Bulk-seeder build runs into latency/cost issues with claude-CLI subprocess on 611 .md (could take >3hr if cards are LLM-generated synchronously without batching). Mitigation: subset to ~100 highest-priority docs first, validate loop, scale after.
- **Down to 4:** Alex prefers gallery + does not do daily reviews. Then we're optimizing for the wrong user behavior and (b) drill-mount becomes correct after all. Worth checking with Alex before building.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
