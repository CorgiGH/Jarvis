# Council review — 1778988899

**Problem:** Sidebar.tsx (174 lines + 4 vitest tests) is dead code in tutor-web — not imported by App.tsx or TutorWorkspace.tsx. KnowledgeLedger.tsx is also dead since Sidebar is its only non-test importer. LEDGER feature has no live UI entry point. 2026-05-17 audit (S-21..S-26) flagged this when every state requiring `sidebar-ledger-btn` failed UNREACHABLE.
**Proposed approach:** Pick from three options.
- A. Mount `<Sidebar>` in App.tsx between header and `<main>` (flex sm:w-48). Restores mockup vision.
- B. Delete Sidebar.tsx + KnowledgeLedger.tsx + tests + audit spec rows S-21..S-26.
- C. Replace Sidebar with single LEDGER button in existing header `<nav>` next to workspace/tasks/review/trust.
**Project context:** jarvis-kotlin tutor SPA (Kotlin/Ktor + React/Vite + node tools). Single-user app (Alex, FII Iași AI bachelor's, finals June 1-21 2026). Stays on `main`. Build-everything mode + brutalist-mono yellow-on-black + mobile-first-class durable. Audit tool (tools/audit-slice15.mjs) sweeps state matrix; 329 vitest + 714 Kotlin + 159 node tests green baseline. Live URL https://corgflix.duckdns.org/tutor/. Prior lesson 2026-05-11 ghost-component: components built+tested+bundled+deployed but never paint-mounted; new components must be wired at the mount site.
**Timestamp:** 2026-05-17T02:54:59Z

---

## 🔴 Devil's Advocate
AGENT: Devil's Advocate
STANCE: CONDITIONAL
REASONING: Option A is sunk-cost theater — "restores mockup vision" is rationalization for the week of wasted polish; flex sm:w-48 reflows every existing screen and breaks the brutalist full-width feel that already shipped, plus you're re-mounting a component nobody asked for at the original mockup phase that *Alex chose to ship without*. Option B is honest but throws away a real feature surface (LEDGER) the user presumably wanted when KnowledgeLedger.tsx got written; "feature dies" is a load-bearing cost being hand-waved. Option C survives because it's the smallest reversible move: one nav-link addition, two file deletions, LEDGER stays reachable, no layout reflow, and the subject-confidence-% badges were never visible to the user anyway (Sidebar was dead) so "losing" them costs zero real UX. C also forces the next honest question — does Alex actually want LEDGER, or did it get built speculatively too? — instead of burying it behind a sidebar he'll ignore.
KEY CONCERN: None of the three options ask whether KnowledgeLedger itself is dead-by-design. If the LEDGER feature was speculatively built (same failure mode as Sidebar — built, tested, bundled, never mounted, never requested), then C is just postponing B by one week. Before any of A/B/C, grep git log + BRIDGE.md for evidence Alex ever asked for LEDGER or used `/api/v1/ledger/*`. If zero user-driven signal exists, the correct move is B + delete KnowledgeLedger with no LEDGER button — stop shipping features the user never requested. The whole question framing assumes LEDGER must survive; that assumption is the actual bug.

## 📚 Domain Expert
AGENT: Domain Expert
STANCE: CONDITIONAL
REASONING: Option C matches established precedent for single-user learning tools best: Anki, Obsidian, and Quizlet all use header-only or top-tab navigation for primary feature access and reserve left rails for content hierarchy (decks, files, sets) — not metacognitive surfaces, which live as dedicated screens reached via nav. A left rail (Option A) is a VSCode/Linear/Notion pattern justified by tree-shaped content with hundreds of items; this app has 4 nav targets and would burn 192px of horizontal real estate on mobile-first brutalist canvas for badges Alex can already see inside the LEDGER screen itself. Option B throws away a genuine pedagogical surface (gap-resolution review is exactly what spaced-repetition learners under-use) for a cleanup win, which violates the "build-everything" preference. Option C preserves the feature, costs zero layout reflow, and the subject-confidence badges belong inside the LEDGER view next to each subject row anyway — putting them in a sidebar duplicates state and creates two surfaces that must stay in sync.
KEY CONCERN: The badges aren't actually lost in Option C — they should live inside the LEDGER screen header per row (Anki's deck-browser pattern), not in a persistent rail. The team is framing this as "C loses badges" when it's really "C relocates badges to where they pedagogically belong, next to the gaps they summarize." Also: mobile drawer toggles (Option A's escape hatch) are an anti-pattern for a 4-item nav — they add a tap for zero information density gain. Ship C, move badges into LEDGER, delete Sidebar.tsx.

## ⚙️ Pragmatist
AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: Option C wins on cost-of-ownership. A single-user app maintained by one person doesn't need a persistent rail eating mobile viewport on every page — it needs one discoverable button to reach LEDGER. ~1 hour of work, ~150 lines deleted, audit surface shrinks (S-21..S-26 collapse to one button selector instead of six rail rows), and KnowledgeLedger stays alive for when Alex actually wants confidence data during finals prep. Option A drags layout reflow + viewport test churn into every future audit run for a feature one user opens occasionally; Option B throws away the only built path to subject-confidence telemetry right before the exam window when that signal matters most. Move the confidence % badges into TasksScreen (where task selection already happens) so the data isn't orphaned.
KEY CONCERN: If LEDGER isn't actually useful to Alex day-to-day, even Option C is dead-code-with-extra-steps in 3 weeks — confirm with Alex that he'd open a LEDGER view at least weekly during June prep before spending the hour; otherwise Option B is correct and the week of polish is a sunk cost to accept, not to rescue.

## 🧱 First Principles
AGENT: First Principles
STANCE: CONDITIONAL
REASONING: Stripped down: the actual job is "help Alex pass June 2026 finals," and the metacognitive job-to-be-done — "what concepts did I just fail on, and have I revisited them?" — is real but bursty, not ambient. A persistent left rail of subject confidence percentages is decoration for a single user who already knows which subjects he's weak in (he lives in this app daily); it spends 12-15% of horizontal viewport to tell him things he already knows. The LEDGER is genuinely useful but only at two moments: end-of-drill ("did I just repeat a known gap?") and start-of-session ("what's still unresolved?"). Both are events, not ambient state — they belong as a button in the header `nav` that opens a drawer/modal, OR inline at the end of the drill result card. So Option C wins on UI economics, but only if the LEDGER button is paired with an automatic post-drill surfacing of the most recent unresolved gap — otherwise C is just B with extra steps.
KEY CONCERN: All three options assume the LEDGER needs to be reachable through a navigation affordance at all. The unquestioned assumption is that metacognitive review is a user-initiated action. For a single-user finals-prep tool, the LEDGER is more valuable as a push (auto-injected into the drill result panel: "you missed a similar concept 2 days ago — still unresolved") than a pull (a button the user has to remember to click). The three options are all framed as "where does the entry point live" when the real question is "should there be an entry point, or should the LEDGER surface itself contextually when relevant?"

## ⚠️ Risk Analyst
Verification commands run before the verdict (file reads):

- `tutor-web/src/components/Sidebar.tsx:87` uses `hidden sm:flex w-48` — desktop-only rail, **mobile gets ZERO LEDGER access if A ships as-is**.
- `tutor-web/src/App.tsx` (entire file 1-230) has zero `Sidebar` import — confirmed dead. App renders `<header>` + `<main>`, no flex parent for a side rail.
- `tutor-web/src/components/TutorWorkspace.tsx:203,232` already uses `flex-1 min-h-0 flex` with `<main>` + `<ResourceRail>` — 2-col layout that would compete with a 192px left rail squeezing both PDF + scratchpad on desktop tablet widths.
- `/api/v1/subject-confidence` is wired at `WebMain.kt:441` (live 401, not 404 — auth-gated, reachable). Not a dead-API risk for A.
- `/api/v1/gaps` wired at `TutorRoutes.kt:1237`. Ledger backend exists.
- Sidebar.test.tsx (4 tests) only renders `<Sidebar>` in `MemoryRouter` — does not depend on parent layout. Tests survive option A unchanged. Under B, all 4 tests + 3 KnowledgeLedger tests delete.
- App header `<nav>` already has `flex-wrap` (App.tsx:155) — option C's 5th link wraps cleanly on mobile, no cognitive cliff.
- Only non-test importer of `KnowledgeLedger` is `Sidebar.tsx:4`. Deleting Sidebar orphans KL automatically.

AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: Option C is the lowest-aggregate-risk path. Option A's layout reflow is real — `TutorWorkspace.tsx:203` already runs `<main>` + `<ResourceRail>` as a flex pair inside a `min-h-0` parent; adding a 192px left rail at the App level squeezes the PDF/scratchpad at desktop widths and creates a mobile-only LEDGER blackhole (Sidebar is `hidden sm:flex`). Option B is irreversible and discards working backend wiring (`WebMain.kt:441` subject-confidence + `TutorRoutes.kt:1237` gaps are both live, not dead-API), which is value-destruction 5 weeks from finals when metacognitive review surfaces matter most. Option C reuses the existing `flex-wrap` header (App.tsx:155 already wraps), keeps `KnowledgeLedger.tsx` mounted as a portal/drawer triggered by a header button, and reaches mobile users for free — at the cost of dropping the per-subject task list + confidence % rollup that only Sidebar currently provides. Residual risk for C: orphan the subject-confidence endpoint (Sidebar was its sole consumer), and lose the task-list navigation surface — either accept that and document it, or salvage subject pills into the header `<nav>` as a secondary strip.
KEY CONCERN: Option A ships LEDGER to desktop but creates a silent mobile gap — `hidden sm:flex` means a phone-using Alex literally cannot reach KnowledgeLedger during finals prep, and the audit tool (which appears to run desktop-viewport per `audit-slice15.mjs`) will report S-21..S-26 GREEN while real-world mobile usage stays broken. This is the exact "selectors-painted ≠ selectors-work" failure class from the 2026-05-11 Slice 1.5 PDF-404 lesson, applied to viewport instead of network.

---

## Sanity Check

SANITY Devil's Advocate: PASS
NOTE: Direct attack on the unquestioned premise that LEDGER must survive. Internally consistent. Recommends a pre-flight grep before commit which is sound rigor.

SANITY Domain Expert: PASS
NOTE: Cited Anki/Obsidian/Quizlet precedents are accurate; reframes badges-loss as badges-relocation cleanly. No internal contradiction.

SANITY Pragmatist: FLAG
NOTE: Recommends "confirm with Alex that he'd open LEDGER weekly" — conflicts with the durable project rule "don't ask user about architecture, convene council instead." Weight the cost-of-ownership recommendation; ignore the user-poll suggestion.

SANITY First Principles: PASS
NOTE: Strong reframe (event-not-ambient → push not pull). Expands scope beyond the three options but doesn't contradict the C recommendation.

SANITY Risk Analyst: PASS (highest confidence — only agent that opened files)
NOTE: Confirmed App.tsx has no Sidebar import; `/api/v1/subject-confidence` (`WebMain.kt:441`) + `/api/v1/gaps` (`TutorRoutes.kt:1237`) are live wiring; Sidebar is `hidden sm:flex` so A creates a silent mobile blackhole. Mobile-blackhole observation is load-bearing.

---

## Judge

VERDICT: PROCEED with Option C
CONFIDENCE: 9/10

RATIONALE: 5/5 agents converged on CONDITIONAL Option C with no dissent toward A or B. Convergence is structural not stylistic:
- A creates a silent mobile LEDGER blackhole (Risk Analyst's verified `hidden sm:flex` + audit running desktop-only — the selectors-painted ≠ selectors-work class from the 2026-05-11 Slice 1.5 lesson).
- B discards live backend wiring (subject-confidence + gaps both reachable per file verification) 5 weeks from finals when metacognitive review matters most.
- C reuses existing `flex-wrap` header (App.tsx:155), reaches mobile for free, costs ~1 hour, shrinks audit surface S-21..S-26 to one selector, KnowledgeLedger.tsx stays mounted via modal trigger, ~150 lines deleted.

RECOMMENDED PATH:
1. Add `<LedgerButton />` to header `<nav>` in `tutor-web/src/App.tsx` between "trust" and the READY/debug status pill. Click opens KnowledgeLedger as a portal/modal (KL already accepts `onClose` prop).
2. Delete `tutor-web/src/components/Sidebar.tsx` + `tutor-web/src/__tests__/Sidebar.test.tsx`.
3. Update audit spec rows S-21..S-26: change reach from "click `sidebar-ledger-btn`" → "click `header-ledger-btn`" (testid name follows the new mount site).
4. Document loss of per-subject task list nav + confidence % badges in `docs/superpowers/specs/2026-05-10-tutor-overhaul-backlog.md` under "deferred — Sidebar deletion 2026-05-17".

DEFERRED (separate spec, out of scope for this decision):
- First Principles' push-not-pull: auto-inject the most recent unresolved gap into the drill-result panel.
- Domain Expert's badges-into-LEDGER-rows: contingent on confidence-% earning UI footprint; defer until usage evidence.

KEY RISK CARRIED FORWARD: Devil's Advocate is correct that LEDGER itself may be speculatively built. If 2 weeks pass without user-driven LEDGER usage, next correct move is Option B (delete KnowledgeLedger too). Telemetry signal: add a 1-line counter on LedgerButton click, OR a `/wrap` checkpoint that greps server access logs for `/api/v1/gaps` hits.

Output saved to: .claude/council-cache/council-1778988899-sidebar-dead-code.md
