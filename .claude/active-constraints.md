# ACTIVE CONSTRAINTS -- jarvis-kotlin (LIVE; injected every turn; updated 2026-06-01)

Hard, currently-live lines. They override momentum. Re-read before EACH reply. (Maintained by /wrap; edit the instant Alex states a new hard line. **/wrap MUST retire any constraint that BRIDGE-HEAD marks `resolved:` — a stale constraint injected every turn poisons resume.**)

## ▶ RESUME RECIPE (next session — do in order; this is the locked plan)

1. **Read the plan-inputs** — `docs/superpowers/specs/2026-05-31-north-star-roadmap-design.md` (backend/teaching-engine, the WHAT) + `docs/superpowers/findings/2026-05-31-build-health-audit-gap-ledger.md` (6 blockers + Phase 0-5) + **this session's UI/viz docs:** `docs/superpowers/research/2026-06-01-viz-ui-excellence-playbook.md` (the quality METHOD), `docs/superpowers/findings/2026-06-01-viz-mount-audit.md` (1 wired / 19 ghosts) + `…-viz-score-backlog.md` (the 20-viz ranked backlog + 4 correctness bugs), `.superpowers/ui-runs/viz-coverage-verdict.md` (Lane A/B). Do NOT re-derive them.
2. **BUILD THE DESIGN FOUNDATION FIRST — the door pick is BLOCKED on it** (Alex 2026-06-01: "I can't pick a door if the design foundations aren't in place; I want a demo of what it would ACTUALLY look like, not a mediocre sketch"). Order: **(a)** ✅ `DESIGN.md` written; **(b)** ✅ self-seeing loop wired into the reworked `grounded-ui-design`; **(c)** ⚠️ render the door THROUGH the foundation = **BOTCHED last time** (rendered RECYCLED `DoorMockups`, NOT the real proposals). The proposals p1/p2/p3 ALREADY EXIST + verifier-pass + ranked at `.superpowers/ui-runs/door-441d42b/`. **DO `.superpowers/ui-runs/door-441d42b/RENDER-HANDOFF.md` EXACTLY — read it FIRST.** Do NOT regenerate; do NOT recycle DoorMockups/door12-8-7 PNGs; do NOT show ASCII; render the REAL p1/p2/p3, self-see, gates-on → 3 real renders; **(d)** Alex picks; **(e)** then drill + teaching surfaces via the same loop.
3. **`writing-plans`** → the STAGED end-to-end plan, FUSING 4 inputs: roadmap + gap-ledger + the design plan (step 2) + **viz-quality = Stage 0** (playbook + backlog). **Stage 0** also = the verification-net + off-box DB backup + clean git baseline. **Stage 1** = walking skeleton on ONE *computational* KC end-to-end on the live app. **Stage 2+** = breadth.
4. **Execute**, gated stage by stage.

**LOCKED decisions — CITE, do NOT re-litigate:**
- (backend) multi-user · real-PDF source-of-record · the concept "door" · D1 Claude-everywhere (pre-gen+cache, deterministic/async grade) · D2 local multilingual embedder · staged-with-trust-gates NOT end-to-end · verification-net FIRST.
- (viz, 2026-06-01) **Lane A = parametric viz on the UNBOXED `AlgoStepperShell` NOW** (G1 stress test: 10/12 archetypes feasible, discrete-step is *better* pedagogy). **Lane B = generated-motion / mini-Rive / ELK auto-layout DEFERRED** behind 2 gates: (i) parametric proven insufficient, (ii) "what is a citation span on a generated viz?" defined.
- **Rive REJECTED** for the build loop (opaque `.riv`, GUI-authored, agent can't read back, uncited). **Motion (Framer) stays** (already abstracted in `motion-helpers.tsx`); **Flubber** for morphs; Motion Canvas/Remotion = video-only.
- **"Awareness of where everything is" = a LAYOUT-engine problem, not a motion-lib problem.** Fix RecursionTree's overlap with `d3-hierarchy` tree layout (Reingold-Tilford, already installed, used nowhere). Full auto-layout (ELK) = Lane B.
- **viz-quality playbook + backlog = STAGE 0** (Alex confirmed 2026-06-01).
- **`grounded-ui-design` is REWORKED** (render-first, ASCII-BANNED, gates-before-ranking, contrast gate in `verify-proposals.mjs`, keep-gates-on-iteration). USE the reworked version.

---

1. **NEVER show Alex ASCII/sketch mockups — he is a visual learner and cannot evaluate them. Demos = REAL rendered screenshots only** (2026-06-01: ASCII doors were useless to him; the whole grounded-ui-design rework + DESIGN.md foundation exist to make the FIRST thing he sees a genuinely-good real render). Build the foundation before any surface pick.
2. **The ONLY registry-wired (student-reachable) viz, RecursionTree, is correctness-SUSPECT** (DFS-order layout → subtrees overlap). 4 viz correctness bugs total (also PageTableWalk, TcpCwnd, NumLineDirect). A wrong viz teaches a falsehood = worst bug, zero detector today → fix-correctness-FIRST, build the pedagogical-correctness gate (playbook §3.6).
3. **When Alex is in oversight / direction-setting mode, do NOT propose build/spec/slice/author work.** Synthesize, surface the decision; he opts into execution. (feedback_stay_strategic)
4. **Before listing "gaps / facilities we need" OR proposing next work, grep docs/superpowers/plans/ + the roadmap + gap-ledger + the playbook FIRST.** Cite path:line or say "net-new because <reason>." (feedback_get_it_right_first_time)
5. **Generation pipeline PROVEN on a real model** (`E3RealRelayProofTest` green); real content unblocked; remaining = F1 reliability (chatty-model self-solve false-reject), fix in Stage 0/1, do NOT block real work on it.

---

## PROPOSAL-GATE (mandatory)

Before ANY sentence proposing next-step work -- verbs like author / build / spec / generate / deploy / run-real-content / "let's" / "we need" / "should we" -- OR any "gaps/facilities we need" list, emit exactly ONE line FIRST:

PROPOSAL-GATE: [against-constraints: ok | CONFLICT#n] [in-plan: path:line | not-in-plan, net-new because reason | n/a]

If CONFLICT, or you cannot cite the plan for work that is slated, then STOP and ask. Do not propose. A missing gate line before a proposal IS the violation.
