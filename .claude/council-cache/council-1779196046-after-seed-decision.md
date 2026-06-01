# Council review — 1779196046

**Problem:** Just seeded 287 FSRS cards into local DB. No card has been graded through any UI yet. What's next?

**Proposed approach — 4 candidates:**
- (a) Add REVIEW DUE (N) tile on tutor home (UX polish)
- (b) Combined deploy hotfix + FSRS to VPS (manual `tools/deploy.sh` + SSH re-seed; burns more claude quota at 82%+ used)
- (c) Alex opens /tutor/review locally first to validate loop end-to-end
- (d) Wrap session

**Project context:** Solo dev Alex, 13 days to finals. Live prod corgflix.duckdns.org/tutor/. Manual VPS deploy. Local port 8080 owned by unrelated service. Hotfix S-24/S-30 ff-merged to local main, not pushed. /review route already in main.tsx; /api/v1/fsrs/{due,grade,forecast} all live.

**Timestamp:** 2026-05-19T13:07:26Z

---

## 🔴 Devil's Advocate

STANCE: REJECT
KEY CONCERN: Option (b) — deploying FSRS to VPS before a single human or Playwright click has graded a single card locally — is a textbook ghost-feature ship that re-commits the exact failure mode CLAUDE.md's load-bearing "feature-shipped verification rule" was written to prevent. The instant Alex hits "Easy" on card #1 from his phone tomorrow morning and the POST 500s or the UI white-screens, he loses the FSRS loop for the finals window AND has a broken production tutor he can't fix from his phone. (c) must run FIRST and must produce a green grade-a-card screenshot before any of (a)/(b)/(d) is permitted.

## 📚 Domain Expert

STANCE: CONDITIONAL — (c) now, then (a), defer (b) to dedicated Saturday-morning slot
KEY CONCERN: The CLAUDE.md ghost-component rule is not satisfied by local-only validation in the general case — but for FSRS specifically it is, because the SRS scheduling math, the grade-update round-trip, and the next-due query are environment-invariant (the SQLite file is the same shape locally and on the VPS; only the seed contents differ). The industry parallel is Anki's own dev workflow: Damien Elmes validates scheduler changes against a local collection before touching AnkiWeb. Named patterns: Spolsky dogfooding (2001), Cockburn walking skeleton (2004, "Crystal Clear"), Honeycomb/SRE Friday-evening anti-pattern (Charity Majors), AnkiDroid local-first sync decoupling, Knight Capital 2012 bundled-deploy disaster. Re-seeding on VPS at 82% Max quota with no recovery budget is the Knight Capital 2012 pattern in miniature.

## ⚙️ Pragmatist

STANCE: CONDITIONAL
KEY CONCERN: The simpler alternative is the right first step and the prompt names it. Order: (1) kill 8080 owner OR pick a free port — but note JARVIS_PORT=8090 may not actually work end-to-end without a frontend rebuild because the bundle probably hardcodes localhost:8080. (2) boot jarvis web with JARVIS_AUTH_TOKEN set, (3) curl with auth header against /api/v1/fsrs/due and eyeball the JSON — if cards return with expected shape, that's 80% of verification at ~5min cost. THEN open the browser. Skip the browser only if willing to ship a tile to an unverified UI surface — which violates render-before-claim-done.

## 🧱 First Principles

STANCE: CONDITIONAL — (c) plus a 60s curl smoke BEFORE the browser
KEY CONCERN: The whole "what next?" menu hides the unfalsified assumption that LLM-generated Romanian-university-curriculum cards are actually gradeable by Alex. One curl + five graded cards collapses the decision tree. Everything else is premature optimization on inventory whose quality is currently unknown.

## ⚠️ Risk Analyst

STANCE: CONDITIONAL
KEY CONCERN: Highest risk across all = shipping (b) or wrapping on (d) without ever opening /tutor/review against real cards. Mitigation sequence: (1) boot backend with JARVIS_PORT=8090 to avoid the unknown 8080 owner, (2) curl http://localhost:8090/api/v1/fsrs/due and assert ≥1 card with non-empty front/back returns, (3) open http://localhost:8090/tutor/review in browser, grade 5-10 cards across 2-3 subjects, screenshot any rendering failure or junk card, (4) only after green light, decide between (a) tile-polish or (b) deploy. Wrapping (d) without (c) is the explicit failure mode the prior council already flagged ("Down to 4 if Alex prefers gallery over daily reviews").

---

## Sanity Check

SANITY Devil's Advocate: PASS — clean. Concrete CLAUDE.md citation, ghost-component pattern.
SANITY Domain Expert: PASS — clean. Multiple named refs.
SANITY Pragmatist: PASS — clean. Real cost + frontend-hardcode caveat surfaced.
SANITY First Principles: PASS — clean. Reframe to "card-quality is the unverified assumption."
SANITY Risk Analyst: PASS — clean. Severity ranks + concrete sequence.

All clean. Unanimous convergence on (c) as gate.

---

## Judge

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
The 4-candidate framing is FLAWED because it treats (a)/(b)/(c)/(d) as equally-available next steps. They are not. (c) is a HARD GATE on (a) and (b). Five agents converge unanimously: Devil's Advocate REJECTS the framing; Domain Expert/Pragmatist/First Principles/Risk Analyst CONDITIONAL on (c) running first. The 287 cards seeded into local SQLite are in the exact epistemic state as the 2026-05-11 Slice 1 ghost components — built, bundled, tested, never opened by a human. The compounding-review reframe that drove this entire session DEPENDS on the loop being walked end-to-end at least once. Until card #1 is graded, the seed is inventory, not learning.

AGENT CONSENSUS: 1 REJECT, 4 CONDITIONAL. Unanimous on (c) first.

KEY ISSUES:

1. **(c) is the irreducible-minimum walking-skeleton step.** Boot backend → curl /due → eyeball JSON → open /review in browser → grade 5-10 cards. Cheapest possible validation of the entire session's work.

2. **(b) without (c) is the Knight Capital 2012 deploy pattern in miniature.** Burns Max-quota to mirror potentially-broken inventory to prod. At 82%+ weekly quota, no recovery budget if a re-seed-on-VPS fails. The Slice 1 ghost-component lesson applies directly.

3. **(a) without (c) is the "decorate before you walk" anti-pattern** (Cockburn). UX polish on an unwalked skeleton.

4. **(d) wrap without (c) is the explicit failure mode prior council flagged.** "Cards rot. Loop never closes. Strategic reframe was wrong."

5. **Card quality is the unfalsified hidden assumption** (First Principles). LLM-generated Romanian/English mixed cards may be jargon-soup unfit for retrieval. Only Alex's eyeball can judge.

6. **Two operational caveats matter** (Pragmatist + Risk):
   - Frontend bundle MAY hardcode localhost:8080; JARVIS_PORT=8090 might break browser fetch URLs. Curl-smoke is port-agnostic; browser-smoke may need port reconciliation.
   - DON'T kill the unknown port-8080 owner per CLAUDE.md "investigate before deleting" rule. Use JARVIS_PORT=8090 OR investigate what owns 8080 first.

RECOMMENDED PATH — (c), in this exact order:

**Step 1 — curl smoke (~3min):**
   - `cd jarvis-kotlin && JARVIS_AUTH_TOKEN=$(cat tools/AUTH_TOKEN.txt) JARVIS_PORT=8090 /c/Tools/gradle-8.10/bin/gradle :run --args="web"` (background)
   - Wait for `/healthz` 200 on port 8090
   - Mint session: `curl -c /tmp/jc -H "Cookie: jarvis_auth=$TOKEN" http://localhost:8090/api/v1/tutor/auto-session`
   - Fetch cards: `curl -b /tmp/jc "http://localhost:8090/api/v1/fsrs/due?limit=10"`
   - Eyeball JSON: cards present? front/back non-empty? mix of subjects? Romanian/English shape sane?

**Step 2 — if curl green, browser-smoke (~10min):**
   - If frontend bundle works at :8090 directly (test with `curl http://localhost:8090/tutor/` and inspect the bundle href), open `http://localhost:8090/tutor/review` in browser.
   - If bundle is hardcoded to :8080, options: (a) reconcile vite proxy to :8090 + dev server, OR (b) kill port-8080 owner after investigating, OR (c) deploy local backend on :8080 by stopping the rogue service. Pick least invasive.
   - Grade 5-10 cards across 2-3 subjects. Screenshot any visible junk.

**Step 3 — branch decision after green light:**
   - If both pass → green-light (a) tile + (b) deploy in next session (combined, with Playwright smoke per prior council).
   - If curl OK + browser broken → fix FsrsReview UI bug; rebuild bundle; loop.
   - If curl shows junk cards → re-tune seed prompt; re-seed a few; loop.

**Do NOT (this session):** add tile / deploy to VPS / wrap. All three premature.

CONFIDENCE: 9

What would shift:
- → 5 if Alex confirms he's not doing reviews today regardless of validation result (would push (c) to lower priority and validate before any UI tile or deploy decision is real).
- → 10 if Alex picks (c) and successfully grades 5 cards (validates the entire session's reframe).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
