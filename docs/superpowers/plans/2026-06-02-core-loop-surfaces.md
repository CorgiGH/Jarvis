# Phase-5 Core-Loop UI — Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Wire the live study screen (`TutorWorkspace` → `DrillStack`) to the trust-engine so a learner sees, on every graded drill: the L0–L4 scaffolding ladder (keystone), the named misconception with its cited refutation, a per-KC "matches your lecture / unverified" trust badge, a faithful grounded-explanation card, and — for AI-generated practice — a DISTINCT honest "not checked against your lecture" provenance badge that NEVER visually spans authored/faithful content. Add a queue/today-driven path (additive, alongside task-prep) so the screen can serve trust-checked KCs.

**Architecture:** CLAIM-vs-PRESENTATION boundary. Every factual claim (definition, worked example, refutation, explanation) is grounded + cited by the backend; only scaffolding narration is freely generated. The frontend is PURE PRESENTATION of server-served verdicts — zero client-side re-derivation of trust state. We wire the *existing* screen to the *already-served* trust fields keystone-first (FeedbackLadder is render-queue #1: its data is already on the `/drill/grade` reply, so it needs zero new backend work). New badges/cards are additive renders gated on server-provided provenance/verification fields. The dual-path queue wiring (`getQueueToday`) is additive and does NOT touch the load-bearing task-prep bootstrap.

**Tech Stack:** React 19 + TS + Vite + Tailwind v4; vitest + @testing-library/react (unit), Playwright (e2e). Brutalist-yellow theme already applied (Phase 4). `tutor-web/.npmrc` already carries `legacy-peer-deps=true` (ERESOLVE fix landed). The SPA mounts under `<BrowserRouter basename="/tutor">` (main.tsx:17), served by vite at base `/tutor/` (vite.config.ts:8).

---

## Accommodation Ledger

This plan touches **one mounted-and-tested component** (`DrillStack`) and adds new sibling components. Account for every downstream consumer of the seams we change:

| Change | Kind | Downstream impact + mitigation |
|---|---|---|
| `DrillContent` interface gains optional `provenance?: {type, hasBeenFaithfulChecked}` (DrillStack.tsx:10) | **ADDITIVE (optional field)** | `TutorWorkspace.tsx:116-118` parses `drillsByProblem` from `prep.drillsJson` via `JSON.parse` — an optional field deserializes to `undefined` on legacy blobs (fail-closed render = no faithful badge). The existing `DrillStack.test.tsx` fixture omits `provenance`; it must still pass (it will — field is optional). No parent pass-down change. |
| `GradeResult` interface gains the already-served-but-ignored trust fields (`ladder_rungs`, `misconception_payload`, `verification_status`, `self_explanation_prompt`, `phase`, `next_phase_action`, `cross_checked`, `kc_quarantined`) (drillGrader.ts:10) | **ADDITIVE (optional/defaulted fields)** | `gradeDrill` already returns `res.json()` untyped-widened; existing readers (`gradeResult.correct/elaboratedFeedback/rubric/misconception`) are untouched. Backend already emits these fields (grep `ladder_rungs = served.ladder` in `TutorRoutes.kt` — VERIFIED served; the reply builder is the `ApiDrillGradeReply` block, ~line 2310 approx). Pure read-side widening. |
| New `StackPhase`-adjacent state in DrillStack: `currentRung`, `studentConfidence` (T0, H16) | **ADDITIVE (new useState)** | `StackPhase` enum (the VERDICT machine: idle/grading/correct/incorrect/given-up/check-done) does NOT change — ladder reveal is a separate `currentRung` counter. No consumer reads `StackPhase` externally. T0 lands FIRST so no consumer re-derives ladder state locally. |
| New components mounted INSIDE `DrillStack` (FeedbackLadder, MisconceptionRibbon, TrustBadge, GroundedExplanationCard, ProvenanceBadge) | **ADDITIVE renders** | All mounted within DrillStack's existing JSX tree; no change to `TutorWorkspace`→`DrillStack` prop contract. Each paired with an explicit mount task showing the JSX diff. |
| `GroundedExplanationCard` `kcId` source | **QUEUE-PATH-fed (additive prop)** | The card needs a `kcId` to fetch `/teaching`. The **task-prep blob carries NO kc id** (`drillsByProblem` from `prep.drillsJson` has no `kc_id`), so on the task-prep path `DrillStack` is mounted WITHOUT a `kcId` → the card honestly no-ops (renders nothing). The `kcId` is supplied by the **queue/today path** (`QueueItem.kc_id`): when the screen is queue-driven, the fetched `QueueItem.kc_id` is passed down as `DrillStack`'s optional `kcId` prop (wiring shown in Task 14). **No backend change is needed** — the queue already serves `kc_id` (`QueueItem.kt:16`, verified). The card is asserted in the queue-path unit coverage (Task 10), not the task-prep e2e. |
| New `getQueueToday()` + `QueueToday`/`QueueItem` types in taskPrep.ts | **ADDITIVE export** | New function alongside `getTaskPrep`; the load-bearing task-prep bootstrap (TutorWorkspace.tsx:48-86) is UNCHANGED. Queue wiring (Task 14) is a thin opt-in surfaced via a `?queue=1` debug-style path so the production task-prep path is never broken. |
| New lib `teaching.ts` (`getTeaching`) hitting `GET /api/v1/teaching/{kcId}` | **ADDITIVE export** | New endpoint defined in the sibling backend plan; consumed on-demand. |

**The §O honest-floor serve gate / provenance CI invariant is OWNED BY THE SIBLING BACKEND PLAN** `docs/superpowers/plans/2026-06-08-grounded-teaching-layer.md` (Task 5 = `DrillContentDto.provenance`; Task 6 = `DrillContentProvenanceInvariantTest` proving `type=="generated"` can never claim `hasBeenFaithfulChecked=true`; Task 7 = the faithful-gated `GET /api/v1/teaching/{kcId}`). **DO NOT re-fix the backend gate here.** This plan consumes the wire contract only and adds an INDEPENDENT frontend rendering-boundary invariant (the faithful badge and the generated provenance badge must never co-render on the same content block — enforced by a unit test + the final e2e assertion).

---

## Visual Acceptance

The testids below split into two groups by the data path that feeds them:

- **Task-prep-path surfaces** MUST paint on first load of `/tutor/?taskId=task-1` (drill stack) under the seeded fixtures, and every interactive element must click without producing a 4xx/5xx or `/404|HTTP \d{3}|not found|error/i` text. The task-prep e2e (Task 15) asserts ALL of these via Playwright headless. The task-prep blob carries NO `kc_id`, so any surface that needs a `kcId` (the grounded-explanation-card) lives in the queue-path group below, NOT here.
- **Queue-path surfaces** are fed by the queue/today path (`QueueItem.kc_id`), which DOES carry a KC id. They are asserted in the queue-path coverage (unit test, see Task 10 + the Task 15 queue-path note), NOT in the task-prep e2e — on the task-prep path they honestly no-op.

Pre-existing (already paint, regression guard):
- `drill-stack`, `drill-card` (×4), `drill-attempt-input`, `drill-prediction-input`, `grade-feedback`

New in Phase 5 — **task-prep-path surfaces** (paint after a graded drill, except the confidence row + provenance/trust badges which paint pre-grade; all asserted in the Task-15 task-prep e2e):
- `drill-confidence-row` — the DEFINITELY/MAYBE/GUESS/IDK selector (paints pre-grade, in the DRILL card)
- `drill-feedback-ladder` — the L0–L4 ladder container (paints post-grade)
- `feedback-rung-rail` — the 5-pip rail
- `feedback-rung-0`, `feedback-rung-1` — the first two rung panels the e2e exercises (the grade stub serves rungs 0/1/2; the e2e paints rung-0 then escalates once to rung-1)
- `feedback-rung-2` … `feedback-rung-4` — further rung panels: **unit-asserted (FeedbackLadder.test.tsx)**, NOT e2e-asserted (the e2e escalates only once; full L0→L4 escalation + the disabled give-up state are covered by the FeedbackLadder unit test)
- `feedback-rung-live-pip` — the current active pip
- `feedback-rung-escalate-button` — reveal-next-rung CTA
- `misconception-ribbon` — the named-misconception ribbon (paints post-grade when `misconception_payload` present + incorrect)
- `misconception-ribbon-kicker` — `MISCONCEPTION · {id}` kicker
- `misconception-ribbon-refutation` — cited refutation body
- `misconception-ribbon-citation` — the SourceRef citation pill
- `trust-badge` — the per-KC verification badge (paints in WORKED/DEFINITION header zone)
- `provenance-badge` — the DISTINCT honest "AI practice — not checked against your lecture" badge (paints only when `provenance.type === "generated"`). The Task-15 e2e drives an AUTHORED fixture, so it asserts this badge ABSENT (the faithful path owns the badge there); its paint-for-generated behavior is unit-asserted in `DrillStack.provenance.test.tsx` (Task 12).
- `app-shell` — shell anchor (always present; the e2e first-paint sanity gate, asserted in Task 15). `header-ledger-btn` is the pre-existing Phase-4 shell ledger button (regression context, not a Phase-5 must-paint — not held to the Phase-5 gate).

New in Phase 5 — **queue-path surfaces** (asserted in the queue-path coverage — the Task-10 unit test passes a `kcId` + mocks `getTeaching`; NOT asserted in the Task-15 task-prep e2e, where no `kc_id` exists so the card honestly no-ops):
- `grounded-explanation-card` — the faithful grounded teaching card. `kcId` comes from `QueueItem.kc_id` (queue path). Renders post-grade ONLY when a `kcId` is present AND the fetched `/teaching` reply has `provenance.hasBeenFaithfulChecked === true`. On the task-prep path (no kc_id) it renders nothing — documented honest no-op.

Mutual-exclusion invariant (Task 15 asserts on the task-prep path; the contract holds on every path): `grounded-explanation-card`/`trust-badge[data-faithful="true"]` and `provenance-badge` MUST NEVER both appear inside the same `[data-content-block]` element.

---

## Tasks

### Task 0 — Verify the wire contracts are served (no code; gate)

- [ ] Run `git rev-parse HEAD` and confirm you are on `main` (or a Phase-5 branch off it).
- [ ] Confirm the backend already serves the trust fields the keystone needs (read-only verification, do NOT edit backend):
  - `Grep` `ladder_rungs = served.ladder` in `src/main/kotlin/jarvis/web/TutorRoutes.kt` (approx line 2311 — VERIFIED present; trust the grep hit, not the number).
  - `Grep` `data class ApiVerifyStatusReply` in `src/main/kotlin/jarvis/web/TrustRoutes.kt` (approx line 74 — VERIFIED present; trust the grep hit, not the number).
  - `Grep` `get("/api/v1/queue/today")` in `src/main/kotlin/jarvis/web/QueueMasteryCalibrationRoutes.kt` (approx line 120 — VERIFIED present; trust the grep hit, not the number).
- [ ] Confirm the sibling backend plan owns provenance + teaching: `Grep` `provenance` and `api/v1/teaching` in `docs/superpowers/plans/2026-06-08-grounded-teaching-layer.md`. If `GET /api/v1/teaching/{kcId}` and `DrillContentDto.provenance` are NOT yet implemented in the backend, the unit tests in this plan still pass (they mock the wire), and the e2e gate (Task 15) stubs them at the Playwright layer — so Phase-5 UI does NOT block on the backend landing first.
- [ ] Run the existing suite to establish a green baseline: `cd tutor-web; npm test`. Record the pass count.

> No commit (verification only).

---

### Task 1 — T0: Extend the trust wire types (`drillGrader.ts`) BEFORE any consumer mounts

This is the H16 "T0 first" task: lock the served-field shapes the consumers read, so no consumer re-derives them locally.

- [ ] Write the failing test `tutor-web/src/lib/drillGrader.types.test.ts`:

```typescript
import { describe, it, expectTypeOf } from "vitest";
import type { GradeResult, LadderRung, MisconceptionPayload, StudentConfidence } from "./drillGrader";

describe("T0 trust wire types", () => {
  it("GradeResult carries the served-but-formerly-ignored trust fields", () => {
    expectTypeOf<GradeResult>().toHaveProperty("ladder_rungs").toEqualTypeOf<LadderRung[]>();
    expectTypeOf<GradeResult>().toHaveProperty("misconception_payload");
    expectTypeOf<GradeResult>().toHaveProperty("verification_status");
    expectTypeOf<GradeResult>().toHaveProperty("self_explanation_prompt");
  });

  it("LadderRung mirrors the Kotlin wire 1:1", () => {
    const r: LadderRung = { level: 0, text: "x" };
    expectTypeOf(r.level).toEqualTypeOf<number>();
    expectTypeOf(r.text).toEqualTypeOf<string>();
  });

  it("MisconceptionPayload uses snake_case figure_spec on the wire shape", () => {
    const m: MisconceptionPayload = {
      id: "OFF_BY_ONE", refutation: "…", figure_spec: null,
      self_explanation_prompt: null, source: null,
    };
    expectTypeOf(m.figure_spec).toEqualTypeOf<string | null>();
  });

  it("StudentConfidence is the four-value enum + null", () => {
    const c: StudentConfidence = "DEFINITELY";
    expectTypeOf<StudentConfidence>().toEqualTypeOf<"DEFINITELY" | "MAYBE" | "GUESS" | "IDK">();
    void c;
  });
});
```

- [ ] Run `cd tutor-web; npx vitest run src/lib/drillGrader.types.test.ts` — confirm it FAILS (types don't exist yet).
- [ ] Edit `tutor-web/src/lib/drillGrader.ts` — add the wire types and widen `GradeResult` (mirror the Kotlin reply 1:1, snake_case preserved). The reply shape is the `ApiDrillGradeReply` data class — grep `data class ApiDrillGradeReply` in `src/main/kotlin/jarvis/web/TutorRoutes.kt` (~line 2850 approx) for the authoritative field list. Append after the existing `GradeRubric` interface and rewrite `GradeResult`:

```typescript
/** Trust-engine wire types — mirror jarvis/tutor/GradeTeachingPayload.kt + QueueItem.kt 1:1.
 *  snake_case preserved where the Kotlin wire uses it (figure_spec, ladder_rungs, …). */
export type VerificationStatus =
  | "unverified" | "pending" | "faithful" | "uncertain" | "failed";

export type StudentConfidence = "DEFINITELY" | "MAYBE" | "GUESS" | "IDK";

/** SourceRef — the citation backing a CitedClaim / refutation.
 *  Mirrors jarvis.content.SourceRef (ContentSchema.kt) 1:1 — grep `data class SourceRef`.
 *  page is NON-nullable on the wire: Int default 0, where 0 = "page unspecified"
 *  (never null). provenance is "pdftotext" (machine) or "vision-confirmed". */
export interface SourceRef {
  doc: string;
  quote: string;
  page: number;            // 1-indexed; 0 = unspecified (NEVER null on the wire)
  span: { start: number; end: number } | null;
  provenance?: string;     // "pdftotext" | "vision-confirmed" (wire default "pdftotext")
}

/** §O LadderRung — L0..L4 scaffold rung copy (grep `data class LadderRung` in GradeTeachingPayload.kt). */
export interface LadderRung {
  level: number;   // 0..4
  text: string;
}

/** §O MisconceptionPayload — figure_spec is snake_case on the wire (grep `data class MisconceptionPayload` in GradeTeachingPayload.kt). */
export interface MisconceptionPayload {
  id: string;
  refutation: string;
  figure_spec: string | null;
  self_explanation_prompt: string | null;
  source: SourceRef | null;
}

export type NextPhaseAction = "advance" | "hold" | "remediate";
```

- [ ] Replace the `GradeResult` interface body with the additive trust fields (existing readers untouched; new fields optional/defaulted to mirror the Kotlin defaults):

```typescript
export interface GradeResult {
  correct: boolean;
  score: number;
  rubric: GradeRubric;
  misconception: string | null;        // grader CODE (pre-existing, e.g. "OFF_BY_ONE")
  elaboratedFeedback: string;
  // Phase-3 GROUP 7 served fields — ADDITIVE, already on the wire (grep `ladder_rungs = served.ladder` in TutorRoutes.kt):
  confidence?: string;                  // "HIGH" | "LOW"
  recorded?: boolean;
  answerMatch?: boolean | null;
  kc_quarantined?: boolean;
  misconception_payload?: MisconceptionPayload | null;  // structured, CITED refutation (DISTINCT from `misconception` code)
  ladder_rungs?: LadderRung[];          // ordered L0..L4
  self_explanation_prompt?: string | null;  // drill-level Chi/Renkl prompt
  verification_status?: VerificationStatus | null;
  phase?: string | null;
  next_phase_action?: NextPhaseAction | null;
  cross_checked?: boolean;
}
```

- [ ] Add `studentConfidence` to `GradeDrillArgs` and pass it through in `gradeDrill`. Edit the `GradeDrillArgs` interface to add:

```typescript
  /** H16: confidence committed BEFORE the verdict (DEFINITELY|MAYBE|GUESS|IDK).
   *  Persisted to AttemptsTable.studentConfidence (column `student_confidence`) —
   *  grep `it[studentConfidence] = req.student_confidence` in TutorRoutes.kt. */
  studentConfidence?: StudentConfidence;
```

- [ ] In `gradeDrill`'s body, add to the spread inside `JSON.stringify({ … })`:

```typescript
      ...(args.studentConfidence ? { studentConfidence: args.studentConfidence } : {}),
```

- [ ] Run `cd tutor-web; npx vitest run src/lib/drillGrader.types.test.ts` — confirm PASS.
- [ ] Run `cd tutor-web; npx tsc --noEmit` — confirm zero errors.
- [ ] Run `cd tutor-web; npm test` — confirm the full suite stays green (DrillStack.test.tsx still passes — `provenance` and trust fields are all optional).
- [ ] **Commit:** `feat(phase5-t0): widen GradeResult + GradeDrillArgs with served trust wire types (H16)`

---

### Task 2 — T0: Extend `DrillContent` with optional `provenance`

- [ ] Write the failing test `tutor-web/src/components/DrillContent.provenance.test.tsx`:

```typescript
import { describe, it, expectTypeOf } from "vitest";
import type { DrillContent, DrillProvenance } from "./DrillStack";

describe("T0 DrillContent.provenance", () => {
  it("provenance is an optional {type, hasBeenFaithfulChecked} marker", () => {
    const authored: DrillProvenance = { type: "authored", hasBeenFaithfulChecked: true };
    const generated: DrillProvenance = { type: "generated", hasBeenFaithfulChecked: false };
    expectTypeOf(authored.type).toEqualTypeOf<"authored" | "generated">();
    void generated;
    expectTypeOf<DrillContent>().toHaveProperty("provenance");
  });
});
```

- [ ] Run `npx vitest run src/components/DrillContent.provenance.test.tsx` — confirm FAILS.
- [ ] Edit `tutor-web/src/components/DrillStack.tsx` — export `DrillProvenance` and add the optional field to `DrillContent` (after `vizId?`):

```typescript
/** Provenance of the prose in `worked`/`definition` (DrillProvenanceDto, sibling backend plan Task 5).
 *  CI invariant: hasBeenFaithfulChecked may be true ONLY when type === "authored". */
export interface DrillProvenance {
  type: "authored" | "generated";
  hasBeenFaithfulChecked: boolean;
}
```

  And inside `DrillContent`, after `vizId?: string;`:

```typescript
  /** T0 (Phase-5): trust-leak fix — provenance of generated vs authored prose.
   *  null/undefined on legacy prep blobs → render fail-closed (no faithful badge). */
  provenance?: DrillProvenance;
```

- [ ] Run `npx vitest run src/components/DrillContent.provenance.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green.
- [ ] **Commit:** `feat(phase5-t0): add optional DrillContent.provenance marker`

---

### Task 3 — KEYSTONE: build `FeedbackLadder` (L0–L4)

The data is ALREADY served on `gradeResult.ladder_rungs`. This component is PURE rendering — zero re-derivation.

- [ ] Write the failing test `tutor-web/src/components/FeedbackLadder.test.tsx`:

```typescript
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { FeedbackLadder } from "./FeedbackLadder";
import type { LadderRung } from "../lib/drillGrader";

const rungs: LadderRung[] = [
  { level: 0, text: "Uită-te din nou la enunț." },
  { level: 1, text: "Explică-ți cu voce tare ce face funcția." },
  { level: 2, text: "Greșeala tipică: confunzi cazul de bază." },
  { level: 3, text: "De fapt: cazul de bază oprește recursia la n<=1." },
  { level: 4, text: "Soluția completă: fib(5)=fib(4)+fib(3)=5." },
];

describe("FeedbackLadder (keystone)", () => {
  it("renders the container + 5-pip rail, reveals only L0 initially", () => {
    render(<FeedbackLadder rungs={rungs} />);
    expect(screen.getByTestId("drill-feedback-ladder")).toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-rail")).toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-0")).toBeInTheDocument();
    expect(screen.queryByTestId("feedback-rung-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-live-pip")).toBeInTheDocument();
  });

  it("escalate reveals the next rung cumulatively", () => {
    render(<FeedbackLadder rungs={rungs} />);
    fireEvent.click(screen.getByTestId("feedback-rung-escalate-button"));
    expect(screen.getByTestId("feedback-rung-1")).toBeInTheDocument();
    expect(screen.getByTestId("feedback-rung-0")).toBeInTheDocument();
    expect(screen.queryByTestId("feedback-rung-2")).not.toBeInTheDocument();
  });

  it("at the last rung the escalate button shows the give-up label and disables", () => {
    render(<FeedbackLadder rungs={rungs} />);
    const btn = () => screen.getByTestId("feedback-rung-escalate-button");
    for (let i = 0; i < 4; i++) fireEvent.click(btn());
    expect(screen.getByTestId("feedback-rung-4")).toBeInTheDocument();
    expect(btn()).toBeDisabled();
  });

  it("renders the rung text verbatim (no re-derivation)", () => {
    render(<FeedbackLadder rungs={rungs} />);
    expect(screen.getByTestId("feedback-rung-0")).toHaveTextContent("Uită-te din nou la enunț.");
  });

  it("renders nothing when rungs is empty", () => {
    const { container } = render(<FeedbackLadder rungs={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it("calls onEscalate with the new level when escalating", () => {
    const onEscalate = vi.fn();
    render(<FeedbackLadder rungs={rungs} onEscalate={onEscalate} />);
    fireEvent.click(screen.getByTestId("feedback-rung-escalate-button"));
    expect(onEscalate).toHaveBeenCalledWith(1);
  });
});
```

- [ ] Run `npx vitest run src/components/FeedbackLadder.test.tsx` — confirm FAILS (no component).
- [ ] Create `tutor-web/src/components/FeedbackLadder.tsx`:

```typescript
import { useState } from "react";
import type { LadderRung } from "../lib/drillGrader";
import { MathText } from "./MathText";

interface FeedbackLadderProps {
  /** Ordered L0..L4 rungs, served verbatim by /drill/grade. */
  rungs: LadderRung[];
  /** Notified with the new visible level (1..n) on each escalate. */
  onEscalate?: (level: number) => void;
}

/**
 * KEYSTONE surface (render-queue #1). PURE rendering of server-served scaffold
 * rungs — NO client-side re-derivation. Least nudge first (L0), escalate to the
 * full solution (L4) one rung at a time. Data source: gradeResult.ladder_rungs.
 */
export function FeedbackLadder({ rungs, onEscalate }: FeedbackLadderProps) {
  const [revealed, setRevealed] = useState(0); // index of the highest revealed rung
  if (!rungs || rungs.length === 0) return null;

  const last = rungs.length - 1;
  const atTop = revealed >= last;

  function escalate() {
    if (atTop) return;
    const next = revealed + 1;
    setRevealed(next);
    onEscalate?.(next);
  }

  return (
    <section
      data-testid="drill-feedback-ladder"
      className="mt-3 border-4 border-border-strong bg-page-bg font-mono"
    >
      {/* 5-pip rail */}
      <div
        data-testid="feedback-rung-rail"
        className="flex gap-1.5 px-3 py-2 border-b-4 border-border-strong bg-panel-dark-bg"
      >
        {rungs.map((r, i) => (
          <span
            key={r.level}
            data-testid={i === revealed ? "feedback-rung-live-pip" : undefined}
            aria-label={`rung L${r.level}`}
            className={`h-3 w-3 border-2 border-panel-dark-fg ${
              i <= revealed ? "bg-accent" : "bg-transparent"
            }`}
          />
        ))}
      </div>

      {/* Revealed rung panels (cumulative) */}
      <ol className="px-4 py-3 flex flex-col gap-2">
        {rungs.slice(0, revealed + 1).map((r) => (
          <li
            key={r.level}
            data-testid={`feedback-rung-${r.level}`}
            data-rung-level={r.level}
            className={`text-xs leading-relaxed text-page-fg ${
              r.level === 1 ? "border-l-2 border-accent-rule pl-2" : ""
            }`}
          >
            <span className="mr-2 text-[10px] tracking-widest text-page-fg/60">
              L{r.level}
            </span>
            <MathText text={r.text} className="inline" />
          </li>
        ))}
      </ol>

      <div className="px-4 pb-3">
        <button
          type="button"
          data-testid="feedback-rung-escalate-button"
          onClick={escalate}
          disabled={atTop}
          className="px-4 py-1.5 bg-accent text-page-fg font-mono text-xs font-bold tracking-widest border-2 border-border-strong hover:bg-accent-hover disabled:opacity-40 transition-all duration-[280ms] ease-in-out active:scale-95"
        >
          {atTop ? "AM RENUNȚAT — VEZI SOLUȚIA" : "ARATĂ-MI MAI MULT →"}
        </button>
      </div>
    </section>
  );
}
```

- [ ] Run `npx vitest run src/components/FeedbackLadder.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` — confirm green.
- [ ] **Commit:** `feat(phase5-keystone): FeedbackLadder L0-L4 (pure render of served ladder_rungs)`

---

### Task 4 — KEYSTONE MOUNT: mount `FeedbackLadder` in `DrillStack`

BUILD+MOUNT pairing for Task 3. The ladder shows after a graded drill, inside the DRILL card, after the misconception banner.

- [ ] Write the failing integration test in `tutor-web/src/components/DrillStack.ladder.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
  check: "Trace fib(4).", expectedAnswerHint: "5",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: false, score: 0.2, rubric: {}, misconception: "OFF_BY_ONE",
        elaboratedFeedback: "Aproape.",
        ladder_rungs: [
          { level: 0, text: "Uită-te din nou." },
          { level: 1, text: "Explică-ți." },
        ],
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack mounts FeedbackLadder after grading", () => {
  it("paints the ladder with served rungs on an incorrect grade", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "4" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("drill-feedback-ladder")).toBeInTheDocument());
    expect(screen.getByTestId("feedback-rung-0")).toHaveTextContent("Uită-te din nou.");
  });
});
```

- [ ] Run `npx vitest run src/components/DrillStack.ladder.test.tsx` — confirm FAILS (ladder not mounted).
- [ ] Edit `tutor-web/src/components/DrillStack.tsx` — add the import at the top:

```typescript
import { FeedbackLadder } from "./FeedbackLadder";
```

  Then mount it inside the DRILL card, immediately AFTER the misconception banner block (after the `{gradeResult && !gradeResult.correct && gradeResult.misconception && ( … )}` block closes at line ~255, before the `{error && …}` block):

```tsx
        {gradeResult && gradeResult.ladder_rungs && gradeResult.ladder_rungs.length > 0 && (
          <FeedbackLadder rungs={gradeResult.ladder_rungs} />
        )}
```

- [ ] Run `npx vitest run src/components/DrillStack.ladder.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green.
- [ ] **Commit:** `feat(phase5-keystone): mount FeedbackLadder in DrillStack DRILL card`

---

### Task 5 — Build `MisconceptionRibbon` (cited refutation)

- [ ] Write the failing test `tutor-web/src/components/MisconceptionRibbon.test.tsx`:

```typescript
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MisconceptionRibbon } from "./MisconceptionRibbon";
import type { MisconceptionPayload } from "../lib/drillGrader";

const cited: MisconceptionPayload = {
  id: "OFF_BY_ONE",
  refutation: "Cazul de bază oprește recursia la n<=1, nu la n<=0.",
  figure_spec: null,
  self_explanation_prompt: null,
  source: { doc: "curs3.pdf", page: 12, span: { start: 100, end: 140 }, quote: "cazul de bază" },
};

describe("MisconceptionRibbon", () => {
  it("renders the kicker with the id, the refutation, and the citation", () => {
    render(<MisconceptionRibbon payload={cited} />);
    expect(screen.getByTestId("misconception-ribbon")).toBeInTheDocument();
    expect(screen.getByTestId("misconception-ribbon-kicker")).toHaveTextContent("OFF_BY_ONE");
    expect(screen.getByTestId("misconception-ribbon-refutation"))
      .toHaveTextContent("Cazul de bază oprește recursia");
    expect(screen.getByTestId("misconception-ribbon-citation")).toHaveTextContent("curs3.pdf");
  });

  it("renders nothing when payload is null", () => {
    const { container } = render(<MisconceptionRibbon payload={null} />);
    expect(container.firstChild).toBeNull();
  });

  it("omits the citation pill when source is null (legacy [from] builder)", () => {
    render(<MisconceptionRibbon payload={{ ...cited, source: null }} />);
    expect(screen.queryByTestId("misconception-ribbon-citation")).not.toBeInTheDocument();
  });
});
```

- [ ] Run `npx vitest run src/components/MisconceptionRibbon.test.tsx` — confirm FAILS.
- [ ] Create `tutor-web/src/components/MisconceptionRibbon.tsx`:

```typescript
import type { MisconceptionPayload } from "../lib/drillGrader";
import { MathText } from "./MathText";

interface MisconceptionRibbonProps {
  /** Inline-served structured misconception (DISTINCT from the scalar grader code). */
  payload: MisconceptionPayload | null | undefined;
}

/**
 * "Your wrong intuition has a name — here is the cited refutation." Renders the
 * INLINE-served misconception_payload (NOT a separate fetch). The refutation is
 * pre-cited by the backend CitationGuard chokepoint; we only present it. When a
 * SourceRef is present we surface the citation pill (P0-2 cited serve path).
 *
 * The kicker renders the RAW `payload.id` (e.g. "OFF_BY_ONE") — misconception
 * ids are stable identifiers, not prose, so they are NOT run through formatEnum
 * (which would lowercase + de-underscore them into "off by one"). The raw code
 * is what the unit + e2e tests assert.
 */
export function MisconceptionRibbon({ payload }: MisconceptionRibbonProps) {
  if (!payload) return null;
  const { source } = payload;
  const cite = source
    ? `${source.doc}${source.page > 0 ? ` · p.${source.page}` : ""}`
    : null;

  return (
    <section
      data-testid="misconception-ribbon"
      data-content-block="misconception"
      className="mt-3 border-4 border-border-strong bg-page-bg font-mono"
    >
      <div
        data-testid="misconception-ribbon-kicker"
        className="px-3 py-1.5 border-b-4 border-border-strong bg-panel-dark-bg text-panel-dark-fg text-[10px] tracking-widest font-bold"
      >
        MISCONCEPTION · {payload.id}
      </div>
      <div className="px-4 py-3">
        <div data-testid="misconception-ribbon-refutation" className="text-xs leading-relaxed text-page-fg">
          <MathText text={payload.refutation} className="inline" />
        </div>
        {cite && (
          <span
            data-testid="misconception-ribbon-citation"
            className="mt-2 inline-block border-2 border-border-thin px-2 py-0.5 text-[10px] tracking-widest text-page-fg/80"
          >
            {cite}
          </span>
        )}
      </div>
    </section>
  );
}
```

- [ ] Run `npx vitest run src/components/MisconceptionRibbon.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` — confirm green.
- [ ] **Commit:** `feat(phase5): MisconceptionRibbon (cited refutation render)`

---

### Task 6 — MOUNT `MisconceptionRibbon` in `DrillStack`

BUILD+MOUNT pairing for Task 5.

- [ ] Write the failing integration test `tutor-web/src/components/DrillStack.misconception.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
  check: "Trace fib(4).", expectedAnswerHint: "5",
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: false, score: 0.1, rubric: {}, misconception: "OFF_BY_ONE",
        elaboratedFeedback: "Aproape.",
        misconception_payload: {
          id: "OFF_BY_ONE", refutation: "Cazul de bază oprește la n<=1.",
          figure_spec: null, self_explanation_prompt: null,
          source: { doc: "curs3.pdf", page: 12, span: null, quote: "caz" },
        },
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack mounts MisconceptionRibbon", () => {
  it("paints the ribbon with the cited refutation on an incorrect grade", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "4" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("misconception-ribbon")).toBeInTheDocument());
    expect(screen.getByTestId("misconception-ribbon-citation")).toHaveTextContent("curs3.pdf");
  });
});
```

- [ ] Run `npx vitest run src/components/DrillStack.misconception.test.tsx` — confirm FAILS.
- [ ] Edit `tutor-web/src/components/DrillStack.tsx` — add the import:

```typescript
import { MisconceptionRibbon } from "./MisconceptionRibbon";
```

  Mount it inside the DRILL card, immediately AFTER the `FeedbackLadder` mount added in Task 4:

```tsx
        {gradeResult && !gradeResult.correct && gradeResult.misconception_payload && (
          <MisconceptionRibbon payload={gradeResult.misconception_payload} />
        )}
```

- [ ] Run `npx vitest run src/components/DrillStack.misconception.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green.
- [ ] **Commit:** `feat(phase5): mount MisconceptionRibbon in DrillStack`

---

### Task 7 — Build `TrustBadge` + `teaching.ts` lib (verification status)

The badge text is PINNED by the backend; the frontend NEVER invents "verified correct". `TrustBadge` renders from `verification_status` (served on `gradeResult`) with an optional on-demand refresh via `GET /api/v1/verify/{kcId}/status`.

- [ ] Write the failing lib test `tutor-web/src/lib/teaching.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { getVerifyStatus, getTeaching } from "./teaching";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
});

describe("teaching lib", () => {
  it("getVerifyStatus GETs /api/v1/verify/{kcId}/status and returns the reply", async () => {
    const calls: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      calls.push(url);
      return new Response(JSON.stringify({
        verification_status: "faithful", badge_text: "matches your lecture",
        claims: [], honest_floor: "FAITHFUL_TO_SOURCE",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }));
    const r = await getVerifyStatus("kc-1");
    expect(calls[0]).toContain("/api/v1/verify/kc-1/status");
    expect(r?.badge_text).toBe("matches your lecture");
  });

  it("getTeaching returns null on 404 (FAIL-LOUD non-faithful gate)", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("not found", { status: 404 })));
    const r = await getTeaching("kc-x");
    expect(r).toBeNull();
  });

  it("getTeaching returns the faithful teaching payload on 200", async () => {
    const calls: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      calls.push(url);
      return new Response(JSON.stringify({
        kcId: "kc-1", name_ro: "Recursie",
        explanation_ro: "O funcție definită prin ea însăși.",
        worked_example_ro: "fib(5)=5",
        provenance: { type: "authored", hasBeenFaithfulChecked: true },
      }), { status: 200, headers: { "content-type": "application/json" } });
    }));
    const r = await getTeaching("kc-1");
    expect(calls[0]).toContain("/api/v1/teaching/kc-1");
    expect(r?.explanation_ro).toBe("O funcție definită prin ea însăși.");
    expect(r?.provenance.hasBeenFaithfulChecked).toBe(true);
  });
});
```

- [ ] Run `npx vitest run src/lib/teaching.test.ts` — confirm FAILS.
- [ ] Create `tutor-web/src/lib/teaching.ts`:

```typescript
import { jarvisFetch } from "./api";
import type { DrillProvenance } from "../components/DrillStack";
import type { VerificationStatus, SourceRef } from "./drillGrader";

/** Wire reply for GET /api/v1/verify/{kcId}/status — grep `data class ApiVerifyStatusReply` in TrustRoutes.kt.
 *  badge_text is PINNED server-side — never "verified correct". */
export interface CitedClaim {
  claimKind: "DEFINITION" | "INVARIANT" | "GRADER_RULE" | "MISCONCEPTION_REFUTATION" | "STEM";
  status: VerificationStatus;
  source: SourceRef;
}
export type HonestFloor = "FAITHFUL_TO_SOURCE" | "UNVERIFIED";
export interface ApiVerifyStatusReply {
  verification_status: VerificationStatus;
  badge_text: string;
  claims: CitedClaim[];
  honest_floor: HonestFloor;
}

/** Wire reply for GET /api/v1/teaching/{kcId} (sibling backend plan Task 7).
 *  FAIL-LOUD: 404 when the KC is non-faithful / disputed / unknown. */
export interface ApiTeachingReply {
  kcId: string;
  name_ro: string;
  explanation_ro: string | null;
  worked_example_ro: string | null;
  provenance: DrillProvenance; // always {type:"authored", hasBeenFaithfulChecked:true}
}

/** GET /api/v1/verify/{kcId}/status. Returns null on non-2xx (badge falls closed to unverified). */
export async function getVerifyStatus(kcId: string): Promise<ApiVerifyStatusReply | null> {
  const res = await jarvisFetch(`/api/v1/verify/${encodeURIComponent(kcId)}/status`);
  if (!res.ok) return null;
  return res.json() as Promise<ApiVerifyStatusReply>;
}

/** GET /api/v1/teaching/{kcId}. Returns null on 404 (non-faithful/disputed FAIL-LOUD gate). */
export async function getTeaching(kcId: string): Promise<ApiTeachingReply | null> {
  const res = await jarvisFetch(`/api/v1/teaching/${encodeURIComponent(kcId)}`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`getTeaching ${res.status}: ${await res.text().catch(() => "")}`);
  return res.json() as Promise<ApiTeachingReply>;
}
```

- [ ] Run `npx vitest run src/lib/teaching.test.ts` — confirm PASS.
- [ ] Write the failing component test `tutor-web/src/components/TrustBadge.test.tsx`:

```typescript
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TrustBadge } from "./TrustBadge";

describe("TrustBadge", () => {
  it("renders the faithful badge with PINNED 'matches your lecture' copy", () => {
    render(<TrustBadge status="faithful" />);
    const b = screen.getByTestId("trust-badge");
    expect(b).toHaveTextContent(/matches your lecture/i);
    expect(b).toHaveAttribute("data-faithful", "true");
    expect(b).not.toHaveTextContent(/verified correct/i);
  });

  it("renders the honest unverified badge for non-faithful statuses", () => {
    render(<TrustBadge status="uncertain" />);
    const b = screen.getByTestId("trust-badge");
    expect(b).toHaveTextContent(/unverified/i);
    expect(b).toHaveAttribute("data-faithful", "false");
  });

  it("renders nothing when status is null/undefined", () => {
    const { container } = render(<TrustBadge status={null} />);
    expect(container.firstChild).toBeNull();
  });
});
```

- [ ] Run `npx vitest run src/components/TrustBadge.test.tsx` — confirm FAILS.
- [ ] Create `tutor-web/src/components/TrustBadge.tsx`:

```typescript
import type { VerificationStatus } from "../lib/drillGrader";

interface TrustBadgeProps {
  /** Served per-KC verification status (gradeResult.verification_status). */
  status: VerificationStatus | null | undefined;
}

/**
 * Per-KC trust-boundary sentinel. The copy is PINNED — only ever "matches your
 * lecture" (faithful) or "unverified". NEVER "verified correct". Falls closed to
 * unverified for any non-faithful status. `data-faithful` drives the rendering-
 * boundary test that proves this badge never co-renders with the generated
 * provenance badge.
 */
export function TrustBadge({ status }: TrustBadgeProps) {
  if (!status) return null;
  const faithful = status === "faithful";
  return (
    <span
      data-testid="trust-badge"
      data-faithful={faithful ? "true" : "false"}
      data-verification-status={status}
      className={`inline-block border-2 px-2 py-0.5 font-mono text-[10px] tracking-widest ${
        faithful
          ? "border-accent bg-accent text-page-fg"
          : "border-border-thin bg-page-bg text-page-fg/70"
      }`}
    >
      {faithful ? "matches your lecture" : "unverified"}
    </span>
  );
}
```

- [ ] Run `npx vitest run src/components/TrustBadge.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` — confirm green.
- [ ] **Commit:** `feat(phase5): TrustBadge + teaching.ts (verify-status + teaching lib)`

---

### Task 8 — MOUNT `TrustBadge` in `DrillStack` (where KC facts render)

BUILD+MOUNT pairing for Task 7. The badge goes in the WORKED card header zone (where authored facts render). It reads `gradeResult.verification_status`.

- [ ] Write the failing integration test `tutor-web/src/components/DrillStack.trustbadge.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
  check: "Trace fib(4).", expectedAnswerHint: "5",
  provenance: { type: "authored", hasBeenFaithfulChecked: true },
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: true, score: 1, rubric: {}, misconception: null,
        elaboratedFeedback: "Corect!", verification_status: "faithful",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack mounts TrustBadge for authored faithful content", () => {
  it("paints the faithful trust badge after a graded drill", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("trust-badge")).toBeInTheDocument());
    expect(screen.getByTestId("trust-badge")).toHaveAttribute("data-faithful", "true");
  });
});
```

- [ ] Run `npx vitest run src/components/DrillStack.trustbadge.test.tsx` — confirm FAILS.
- [ ] Edit `tutor-web/src/components/DrillStack.tsx` — add the import:

```typescript
import { TrustBadge } from "./TrustBadge";
```

  Mount it inside the WORKED card, as the first child of the card body, BUT ONLY for authored content (never over generated — the provenance badge owns that). Replace the WORKED card body line `<MathText text={content.worked} className="text-sm" />` with a content block wrapping the badge + prose:

```tsx
        <div data-content-block="worked">
          {content.provenance?.type !== "generated" && (
            <div className="mb-2">
              <TrustBadge status={gradeResult?.verification_status} />
            </div>
          )}
          <MathText text={content.worked} className="text-sm" />
        </div>
```

- [ ] Run `npx vitest run src/components/DrillStack.trustbadge.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green.
- [ ] **Commit:** `feat(phase5): mount TrustBadge in DrillStack WORKED card (authored only)`

---

### Task 9 — Build `GroundedExplanationCard` (faithful-gated teaching)

Renders `explanation_ro`/`worked_example_ro` ONLY when `provenance.hasBeenFaithfulChecked === true`. Fetches `GET /api/v1/teaching/{kcId}` on-demand; renders nothing on 404 (FAIL-LOUD non-faithful gate).

- [ ] Write the failing test `tutor-web/src/components/GroundedExplanationCard.test.tsx` (component-reuse contract: mocks the `getTeaching` external dep and asserts the URL/payload shape):

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { GroundedExplanationCard } from "./GroundedExplanationCard";
import * as teaching from "../lib/teaching";

beforeEach(() => { vi.restoreAllMocks(); });

describe("GroundedExplanationCard", () => {
  it("fetches /teaching/{kcId} and renders explanation + worked example when faithful", async () => {
    const spy = vi.spyOn(teaching, "getTeaching").mockResolvedValue({
      kcId: "kc-1", name_ro: "Recursie",
      explanation_ro: "O funcție definită prin ea însăși.",
      worked_example_ro: "fib(5)=5",
      provenance: { type: "authored", hasBeenFaithfulChecked: true },
    });
    render(<GroundedExplanationCard kcId="kc-1" />);
    await waitFor(() => expect(screen.getByTestId("grounded-explanation-card")).toBeInTheDocument());
    expect(spy).toHaveBeenCalledWith("kc-1");
    expect(screen.getByTestId("grounded-explanation-card"))
      .toHaveTextContent("O funcție definită prin ea însăși.");
    // Mutual-exclusion contract: the faithful card carries the faithful marker, never the generated one.
    expect(screen.getByTestId("grounded-explanation-card")).toHaveAttribute("data-faithful", "true");
  });

  it("renders nothing when /teaching returns null (404 non-faithful gate)", async () => {
    vi.spyOn(teaching, "getTeaching").mockResolvedValue(null);
    const { container } = render(<GroundedExplanationCard kcId="kc-x" />);
    await waitFor(() => expect(teaching.getTeaching).toHaveBeenCalled());
    expect(container.querySelector('[data-testid="grounded-explanation-card"]')).toBeNull();
  });

  it("renders nothing when kcId is undefined (no fetch)", () => {
    const spy = vi.spyOn(teaching, "getTeaching");
    const { container } = render(<GroundedExplanationCard kcId={undefined} />);
    expect(spy).not.toHaveBeenCalled();
    expect(container.firstChild).toBeNull();
  });

  it("never renders when provenance is not faithful-checked (defensive double-gate)", async () => {
    vi.spyOn(teaching, "getTeaching").mockResolvedValue({
      kcId: "kc-1", name_ro: "X", explanation_ro: "x", worked_example_ro: null,
      provenance: { type: "authored", hasBeenFaithfulChecked: false },
    });
    const { container } = render(<GroundedExplanationCard kcId="kc-1" />);
    await waitFor(() => expect(teaching.getTeaching).toHaveBeenCalled());
    expect(container.querySelector('[data-testid="grounded-explanation-card"]')).toBeNull();
  });
});
```

- [ ] Run `npx vitest run src/components/GroundedExplanationCard.test.tsx` — confirm FAILS.
- [ ] Create `tutor-web/src/components/GroundedExplanationCard.tsx`:

```typescript
import { useEffect, useState } from "react";
import { getTeaching } from "../lib/teaching";
import type { ApiTeachingReply } from "../lib/teaching";
import { MathText } from "./MathText";

interface GroundedExplanationCardProps {
  /** KC to fetch grounded teaching for. Sourced from QueueItem.kc_id on the
   *  queue/today path. UNDEFINED on the legacy task-prep path (the prep blob
   *  carries no kc id) → no fetch, renders nothing (honest no-op). */
  kcId: string | undefined;
}

/**
 * Faithful grounded teaching card. Fetches GET /api/v1/teaching/{kcId} on-demand.
 * Renders explanation_ro / worked_example_ro ONLY when the reply is present AND
 * provenance.hasBeenFaithfulChecked === true (double-gate: the backend already
 * 404s non-faithful KCs, the client re-checks the marker before painting).
 *
 * kcId SOURCE: the queue/today path (QueueItem.kc_id). The task-prep path carries
 * NO kc id, so on that path kcId is undefined and the card renders NOTHING — a
 * documented honest no-op, NOT a bug. (This is why grounded-explanation-card is a
 * queue-path Visual-Acceptance surface, asserted in the Task-10 unit test, not the
 * task-prep e2e.)
 */
export function GroundedExplanationCard({ kcId }: GroundedExplanationCardProps) {
  const [reply, setReply] = useState<ApiTeachingReply | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!kcId) { setReply(null); return; }
    getTeaching(kcId)
      .then((r) => { if (!cancelled) setReply(r); })
      .catch(() => { if (!cancelled) setReply(null); });
    return () => { cancelled = true; };
  }, [kcId]);

  if (!reply || !reply.provenance.hasBeenFaithfulChecked) return null;

  return (
    <section
      data-testid="grounded-explanation-card"
      data-content-block="grounded"
      data-faithful="true"
      className="mt-3 border-4 border-accent bg-page-bg font-mono"
    >
      <div className="px-3 py-1.5 border-b-4 border-accent bg-accent text-page-fg text-[10px] tracking-widest font-bold">
        EXPLICAȚIE · matches your lecture
      </div>
      <div className="px-4 py-3 flex flex-col gap-3">
        {reply.explanation_ro && (
          <MathText text={reply.explanation_ro} className="text-xs leading-relaxed" />
        )}
        {reply.worked_example_ro && (
          <div className="border-l-2 border-accent-rule pl-3">
            <div className="mb-1 text-[10px] tracking-widest text-page-fg/60">EXEMPLU REZOLVAT</div>
            <MathText text={reply.worked_example_ro} className="text-xs leading-relaxed" />
          </div>
        )}
      </div>
    </section>
  );
}
```

- [ ] Run `npx vitest run src/components/GroundedExplanationCard.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` — confirm green.
- [ ] **Commit:** `feat(phase5): GroundedExplanationCard (faithful-gated /teaching fetch)`

---

### Task 10 — MOUNT `GroundedExplanationCard` in `DrillStack`

BUILD+MOUNT pairing for Task 9. The card needs a `kcId`. The task-prep drill content does NOT carry one (`drillsByProblem` from `prep.drillsJson` has no `kc_id`), so plumb an optional `kcId` prop through `DrillStack` (additive). The `kcId` is supplied by the **queue/today path** (`QueueItem.kc_id`, wired in Task 14); on the task-prep path no `kcId` is passed and the card no-ops (honest no-op — see Task 9 docstring). It mounts after grade success (`correct` or `given-up`), inside the DEFINITION card body. This task's unit test (`DrillStack.grounded.test.tsx`) is the **queue-path coverage** for `grounded-explanation-card`: it passes `kcId="kc-1"` (as the queue path would) + mocks `getTeaching`, proving the card paints when a kc id is present.

- [ ] Write the failing integration test `tutor-web/src/components/DrillStack.grounded.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";
import * as teaching from "../lib/teaching";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
  check: "Trace fib(4).", expectedAnswerHint: "5",
  provenance: { type: "authored", hasBeenFaithfulChecked: true },
};

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.spyOn(teaching, "getTeaching").mockResolvedValue({
    kcId: "kc-1", name_ro: "Recursie", explanation_ro: "Definită prin ea însăși.",
    worked_example_ro: null, provenance: { type: "authored", hasBeenFaithfulChecked: true },
  });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: true, score: 1, rubric: {}, misconception: null, elaboratedFeedback: "Corect!",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack mounts GroundedExplanationCard with kcId after grade", () => {
  it("paints the grounded card once the drill is graded", async () => {
    render(<DrillStack taskId="t1" problemId="p1" kcId="kc-1" content={content} onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("grounded-explanation-card")).toBeInTheDocument());
  });

  it("does not paint the grounded card before grading", () => {
    render(<DrillStack taskId="t1" problemId="p1" kcId="kc-1" content={content} onProblemComplete={() => {}} />);
    expect(screen.queryByTestId("grounded-explanation-card")).not.toBeInTheDocument();
  });
});
```

- [ ] Run `npx vitest run src/components/DrillStack.grounded.test.tsx` — confirm FAILS.
- [ ] Edit `tutor-web/src/components/DrillStack.tsx`:
  - Add the import:

```typescript
import { GroundedExplanationCard } from "./GroundedExplanationCard";
```

  - Add an optional `kcId` to `DrillStackProps`:

```typescript
interface DrillStackProps {
  taskId: string;
  problemId: string;
  /** KC behind this drill, used to fetch grounded teaching. Sourced from
   *  QueueItem.kc_id on the queue/today path; absent on the task-prep path
   *  (prep blob has no kc id) → GroundedExplanationCard no-ops. */
  kcId?: string;
  content: DrillContent;
  onProblemComplete: (problemId: string) => void;
}
```

  - Destructure `kcId` in the component signature:

```typescript
export function DrillStack({
  taskId,
  problemId,
  kcId,
  content,
  onProblemComplete,
}: DrillStackProps) {
```

  - Mount the card inside the DEFINITION card, after the definition prose. Replace the DEFINITION card body line `<MathText text={content.definition} className="text-sm" />` with:

```tsx
        <MathText text={content.definition} className="text-sm" />
        {(phase === "correct" || phase === "given-up" || phase === "check-done") && (
          <GroundedExplanationCard kcId={kcId} />
        )}
```

- [ ] Run `npx vitest run src/components/DrillStack.grounded.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green (the existing `DrillStack.test.tsx` calls without `kcId`; optional prop keeps it green).
- [ ] **Commit:** `feat(phase5): mount GroundedExplanationCard in DrillStack DEFINITION card`

---

### Task 11 — Build `ProvenanceBadge` (the honest "AI practice" badge)

The DISTINCT badge for `provenance.type === "generated"`. It must be visually and textually segregated from the faithful badge.

- [ ] Write the failing test `tutor-web/src/components/ProvenanceBadge.test.tsx`:

```typescript
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProvenanceBadge } from "./ProvenanceBadge";

describe("ProvenanceBadge", () => {
  it("renders the honest 'AI practice — not checked' badge for generated content", () => {
    render(<ProvenanceBadge provenance={{ type: "generated", hasBeenFaithfulChecked: false }} />);
    const b = screen.getByTestId("provenance-badge");
    expect(b).toHaveTextContent(/AI practice — not checked against your lecture/i);
    expect(b).toHaveAttribute("data-provenance-type", "generated");
    expect(b).toHaveAttribute("data-faithful", "false");
  });

  it("renders nothing for authored content (the faithful path owns that badge)", () => {
    const { container } = render(
      <ProvenanceBadge provenance={{ type: "authored", hasBeenFaithfulChecked: true }} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders nothing when provenance is null/undefined", () => {
    const { container } = render(<ProvenanceBadge provenance={null} />);
    expect(container.firstChild).toBeNull();
  });
});
```

- [ ] Run `npx vitest run src/components/ProvenanceBadge.test.tsx` — confirm FAILS.
- [ ] Create `tutor-web/src/components/ProvenanceBadge.tsx`:

```typescript
import type { DrillProvenance } from "./DrillStack";

interface ProvenanceBadgeProps {
  provenance: DrillProvenance | null | undefined;
}

/**
 * The DISTINCT honest badge for AI-generated practice. Renders ONLY for
 * provenance.type === "generated". Its color + copy are deliberately segregated
 * from the faithful TrustBadge so the "matches your lecture" claim can NEVER
 * visually span generated content (council 1780928193 trust-leak fix). The
 * `data-provenance-type="generated"` + `data-faithful="false"` attributes back
 * the rendering-boundary invariant test (Task 12) and the e2e gate (Task 15).
 */
export function ProvenanceBadge({ provenance }: ProvenanceBadgeProps) {
  if (!provenance || provenance.type !== "generated") return null;
  return (
    <span
      data-testid="provenance-badge"
      data-provenance-type="generated"
      data-faithful="false"
      className="inline-block border-2 border-danger-text bg-danger-text/10 px-2 py-0.5 font-mono text-[10px] tracking-widest text-danger-text"
    >
      AI practice — not checked against your lecture
    </span>
  );
}
```

- [ ] Run `npx vitest run src/components/ProvenanceBadge.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` — confirm green.
- [ ] **Commit:** `feat(phase5): ProvenanceBadge (distinct honest 'AI practice' badge)`

---

### Task 12 — MOUNT `ProvenanceBadge` in `DrillStack` + the rendering-boundary invariant test

BUILD+MOUNT pairing for Task 11. The badge mounts in the SAME content block as the WORKED prose, but only for generated content — mutually exclusive with the TrustBadge mounted in Task 8 (which only renders for non-generated content). This task also adds the frontend invariant test proving the two badges never co-render.

- [ ] Write the failing integration + invariant test `tutor-web/src/components/DrillStack.provenance.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

function makeContent(p: DrillContent["provenance"]): DrillContent {
  return {
    drill: "Trace fib(5).", worked: "fib(5)=5", definition: "Recursion.",
    check: "Trace fib(4).", expectedAnswerHint: "5", provenance: p,
  };
}

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      return new Response(JSON.stringify({
        correct: true, score: 1, rubric: {}, misconception: null,
        elaboratedFeedback: "ok", verification_status: "faithful",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack provenance rendering boundary", () => {
  it("generated content shows the provenance badge, NOT the faithful trust badge", async () => {
    render(<DrillStack taskId="t1" problemId="p1"
      content={makeContent({ type: "generated", hasBeenFaithfulChecked: false })}
      onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("provenance-badge")).toBeInTheDocument());
    // The faithful badge must NOT span generated content.
    expect(screen.queryByTestId("trust-badge")).not.toBeInTheDocument();
  });

  it("authored content shows the faithful trust badge, NOT the provenance badge", async () => {
    render(<DrillStack taskId="t1" problemId="p1"
      content={makeContent({ type: "authored", hasBeenFaithfulChecked: true })}
      onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("trust-badge")).toBeInTheDocument());
    expect(screen.queryByTestId("provenance-badge")).not.toBeInTheDocument();
  });

  it("INVARIANT: no [data-content-block] ever contains both faithful + generated badges", async () => {
    render(<DrillStack taskId="t1" problemId="p1"
      content={makeContent({ type: "generated", hasBeenFaithfulChecked: false })}
      onProblemComplete={() => {}} />);
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(screen.getByTestId("provenance-badge")).toBeInTheDocument());
    document.querySelectorAll("[data-content-block]").forEach((block) => {
      const hasFaithful = block.querySelector('[data-faithful="true"]');
      const hasGenerated = block.querySelector('[data-provenance-type="generated"]');
      expect(hasFaithful && hasGenerated).toBeFalsy();
    });
  });
});
```

- [ ] Run `npx vitest run src/components/DrillStack.provenance.test.tsx` — confirm FAILS (provenance badge not mounted).
- [ ] Edit `tutor-web/src/components/DrillStack.tsx` — add the import:

```typescript
import { ProvenanceBadge } from "./ProvenanceBadge";
```

  Update the WORKED card content block (added in Task 8) to render the provenance badge for generated content — replace the Task-8 block with:

```tsx
        <div data-content-block="worked">
          {content.provenance?.type === "generated" ? (
            <div className="mb-2">
              <ProvenanceBadge provenance={content.provenance} />
            </div>
          ) : (
            <div className="mb-2">
              <TrustBadge status={gradeResult?.verification_status} />
            </div>
          )}
          <MathText text={content.worked} className="text-sm" />
        </div>
```

- [ ] Run `npx vitest run src/components/DrillStack.provenance.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green.
- [ ] **Commit:** `feat(phase5): mount ProvenanceBadge + rendering-boundary invariant (faithful never spans generated)`

---

### Task 13 — H16: `drill-confidence-row` (collect studentConfidence pre-grade)

DrillStack does not yet COLLECT confidence. Add the row in the DRILL card before the CHECK ANSWER button; pass it to `gradeDrill`.

- [ ] Write the failing test `tutor-web/src/components/DrillStack.confidence.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";

const content: DrillContent = {
  drill: "Trace fib(5).", worked: "w", definition: "d", check: "c", expectedAnswerHint: "5",
};

let lastBody: any = null;
beforeEach(() => {
  lastBody = null;
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    if (typeof url === "string" && url.includes("/api/v1/drill/grade")) {
      lastBody = JSON.parse(String(init?.body ?? "{}"));
      return new Response(JSON.stringify({
        correct: true, score: 1, rubric: {}, misconception: null, elaboratedFeedback: "ok",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("{}", { status: 200 });
  }));
});

describe("DrillStack confidence row (H16)", () => {
  it("renders the four-value confidence row pre-grade", () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    expect(screen.getByTestId("drill-confidence-row")).toBeInTheDocument();
  });

  it("passes the selected confidence to gradeDrill", async () => {
    render(<DrillStack taskId="t1" problemId="p1" content={content} onProblemComplete={() => {}} />);
    fireEvent.click(screen.getByTestId("confidence-MAYBE"));
    fireEvent.change(screen.getByTestId("drill-attempt-input"), { target: { value: "5" } });
    fireEvent.click(screen.getByTestId("drill-check-btn"));
    await waitFor(() => expect(lastBody?.studentConfidence).toBe("MAYBE"));
  });
});
```

- [ ] Run `npx vitest run src/components/DrillStack.confidence.test.tsx` — confirm FAILS.
- [ ] Edit `tutor-web/src/components/DrillStack.tsx`:
  - Add the type import (extend the existing `drillGrader` type import):

```typescript
import type { GradeResult, StudentConfidence } from "../lib/drillGrader";
```

  (Replace the existing `import type { GradeResult } from "../lib/drillGrader";` line.)

  - Add state after the `prediction` useState:

```typescript
  const [studentConfidence, setStudentConfidence] = useState<StudentConfidence | null>(null);
```

  - Pass it into BOTH `gradeDrill` calls (`handleCheckAnswer` and `handleGiveUp`) by adding to each args object:

```typescript
        studentConfidence: studentConfidence ?? undefined,
```

  - Render the confidence row in the DRILL card, immediately BEFORE the `{!unlocked && ( <div className="mt-3 flex gap-2"> … )}` button row:

```tsx
        {!unlocked && (
          <div
            data-testid="drill-confidence-row"
            role="radiogroup"
            aria-label="confidence before checking"
            className="mt-3 flex flex-wrap gap-2"
          >
            <span className="font-mono text-[10px] uppercase tracking-widest text-page-fg/70 self-center">
              cât de sigur ești?
            </span>
            {(["DEFINITELY", "MAYBE", "GUESS", "IDK"] as StudentConfidence[]).map((c) => (
              <button
                key={c}
                type="button"
                role="radio"
                aria-checked={studentConfidence === c}
                data-testid={`confidence-${c}`}
                onClick={() => setStudentConfidence(c)}
                disabled={phase === "grading"}
                className={`px-2 py-1 font-mono text-[10px] tracking-widest border-2 transition-all duration-[180ms] ${
                  studentConfidence === c
                    ? "bg-accent text-page-fg border-border-strong"
                    : "bg-page-bg text-page-fg/70 border-border-thin hover:border-border-strong"
                }`}
              >
                {c}
              </button>
            ))}
          </div>
        )}
```

- [ ] Run `npx vitest run src/components/DrillStack.confidence.test.tsx` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green.
- [ ] **Commit:** `feat(phase5-h16): drill-confidence-row collects studentConfidence pre-grade`

---

### Task 14 — Queue wiring: additive `getQueueToday` path (does NOT touch task-prep)

Per the mount-dataflow research's recommended Option (a): ADD a queue/today fetch alongside the load-bearing task-prep bootstrap. This task only lands the lib + types (the next plan decides queue-first migration); it proves the queue serves trust-checked KCs.

**Why this task matters for `GroundedExplanationCard`:** `QueueItem.kc_id` is the SOLE source of the `kcId` that `GroundedExplanationCard` needs. The task-prep blob has no kc id, so on the task-prep path the card no-ops; the queue path is what lights it up. **No backend change is needed — the queue already serves `kc_id` (`QueueItem.kt:16`, verified).** This task lands the lib/types; the queue-first DrillStack mount (where the wiring below actually executes) is the next plan. The intended wiring is recorded here so the next plan has the exact diff.

- [ ] Write the failing lib test `tutor-web/src/lib/queueToday.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { getQueueToday } from "./taskPrep";

beforeEach(() => {
  Object.defineProperty(document, "cookie", { value: "csrf=t", configurable: true, writable: true });
});

describe("getQueueToday", () => {
  it("GETs /api/v1/queue/today and returns the envelope", async () => {
    const calls: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      calls.push(url);
      return new Response(JSON.stringify({
        items: [{
          kc_id: "kc-1", kc_name_ro: "Recursie", kc_name_en: "Recursion",
          subject: "PA", phase: "practice", mastery_ewma: 0.4, fsrs_card_id: null,
          verification_status: "faithful", worked_example_first: true, mode: "drill",
        }],
        total_due: 1, day: "2026-06-08",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }));
    const q = await getQueueToday();
    expect(calls[0]).toContain("/api/v1/queue/today");
    expect(q?.items[0].kc_id).toBe("kc-1");
    expect(q?.items[0].mode).toBe("drill");
  });

  it("returns null on 401 (auth gate)", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 401 })));
    expect(await getQueueToday()).toBeNull();
  });
});
```

- [ ] Run `npx vitest run src/lib/queueToday.test.ts` — confirm FAILS.
- [ ] Edit `tutor-web/src/lib/taskPrep.ts` — append the additive export + types after `submitTask` (mirror QueueItem.kt 1:1, snake_case preserved):

```typescript
/** GET /api/v1/queue/today (Phase 5+). ADDITIVE — the task-prep path is unchanged.
 *  Returns null on auth failure (401). Companion fetch for the trust-engine
 *  queue-driven study path; the queue OMITS quarantined/non-faithful KCs server-side. */
export async function getQueueToday(): Promise<QueueToday | null> {
  const res = await jarvisFetch("/api/v1/queue/today");
  if (res.status === 401) return null;
  if (!res.ok) throw new Error(`getQueueToday ${res.status}: ${await res.text().catch(() => "")}`);
  return res.json() as Promise<QueueToday>;
}

export interface QueueToday {
  items: QueueItem[];
  total_due: number;
  day: string; // ISO
}

/** Mirrors jarvis/tutor/QueueItem.kt 1:1 (snake_case preserved on the wire). */
export interface QueueItem {
  kc_id: string;
  kc_name_ro: string;
  kc_name_en: string;
  subject: string;
  phase: "intro" | "practice" | "retrieval" | "mastered";
  mastery_ewma: number;
  fsrs_card_id: string | null;
  verification_status: "unverified" | "pending" | "faithful" | "uncertain" | "failed";
  worked_example_first: boolean;
  mode: "worked" | "drill" | "retrieve";
}
```

- [ ] Run `npx vitest run src/lib/queueToday.test.ts` — confirm PASS.
- [ ] Run `npx tsc --noEmit` and `npm test` — confirm green.

**Queue-path `kc_id` → `DrillStack.kcId` wiring (reference diff for the next plan's queue-first mount).** When the queue path mounts `DrillStack` from a fetched `QueueItem`, the selected item's `kc_id` is passed straight down as the optional `kcId` prop added in Task 10. `DrillStack` then hands it to `GroundedExplanationCard`. The diff at the queue-path mount site (e.g. a future `QueueWorkspace.tsx`, NOT the unchanged `TutorWorkspace.tsx` task-prep bootstrap):

```tsx
// queue-path mount (next plan): selected = the chosen QueueItem from getQueueToday()
const queueItem = queue?.items[activeIndex];   // QueueItem | undefined
// ...
<DrillStack
  taskId={taskId}
  problemId={problemId}
  kcId={queueItem?.kc_id}        // ← queue path supplies the kc id; task-prep path omits it
  content={drillContent}
  onProblemComplete={onProblemComplete}
/>
```

`DrillStack` already forwards it (Task 10): `<GroundedExplanationCard kcId={kcId} />`. So on the queue path the card fetches `/teaching/{kc_id}` and paints; on the task-prep path `kcId` is `undefined` and the card no-ops. No `DrillStack` signature change beyond the optional `kcId` prop landed in Task 10, and **no backend field to add** (the queue already carries `kc_id`).

- [ ] **Commit:** `feat(phase5): additive getQueueToday lib path (trust-checked KC queue)`

---

### Task 15 — FINAL ACCEPTANCE: the interaction-smoke e2e gate (Playwright)

This is the gate. It runs headless against the dev server with Playwright-layer stubs (CI has no JVM backend, same proven pattern as `drill-viz-paint.spec.ts`). It NEVER touches `~/.jarvis/tutor.db` — all data is stubbed at the Playwright route layer (a seeded/cloned fixture). It asserts every **task-prep-path** Visual-Acceptance `data-testid` paints, zero 4xx/5xx, clicks every interactive element with no error text, and the faithful + generated badges never co-render on the same content block.

**Scope note (queue-path surface):** `grounded-explanation-card` is a queue-path surface (it needs `QueueItem.kc_id`). The task-prep fixture this e2e drives has NO kc_id, so the card honestly no-ops; this e2e asserts it is ABSENT, and its paint-when-kcId-present behavior is covered by the queue-path unit test (`DrillStack.grounded.test.tsx`, Task 10). A full queue-path e2e is deferred to the next plan (which lands the queue-first mount). The narrow rung set (rung-0, rung-1) is exercised here; rung-2..4 + the give-up disabled state are unit-asserted in `FeedbackLadder.test.tsx`.

- [ ] Create `tutor-web/e2e/phase5-core-loop.spec.ts`:

```typescript
import { test, expect } from "@playwright/test";

/**
 * Phase-5 core-loop interaction-smoke gate.
 *
 * Mounts the REAL tutor workspace under /tutor/?taskId=task-1, stubs the
 * first-paint /api contract + the trust-engine routes at the Playwright layer
 * (CI runs no Kotlin backend; NEVER touches ~/.jarvis/tutor.db). Asserts:
 *   (1) every task-prep-path Visual-Acceptance data-testid paints (grounded-
 *       explanation-card is a queue-path surface — asserted ABSENT here, covered
 *       by DrillStack.grounded.test.tsx; rung-2..4 are unit-asserted)
 *   (2) zero 4xx/5xx during first paint
 *   (3) clicks every interactive element
 *   (4) no /404|HTTP \d{3}|not found|error/i text + no new 4xx/5xx after each click
 *   (5) the faithful badge + generated provenance badge never co-render on the
 *       same [data-content-block]
 */
test("Phase-5 core loop: all trust surfaces paint + interact with zero errors", async ({ page }) => {
  const bad: string[] = [];
  page.on("response", (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url()}`); });

  // ── First-paint /api contract stubs ──
  await page.route("**/api/v1/tutor/auto-session", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/v1/me/export", (r) =>
    r.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ aiLiteracyConfirmed: true, user: { lang: "ro" } }) }));
  await page.route("**/api/v1/last-task", (r) =>
    r.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify(r.request().method() === "GET" ? { taskId: "task-1" } : {}) }));
  await page.route("**/api/v1/fsrs/forecast", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ dueNow: 0 }) }));
  await page.route("**/api/v1/tasks", (r) => {
    if (r.request().method() === "GET") {
      r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ tasks: [{ id: "task-1" }] }) });
    } else r.continue();
  });

  // ── Prep bundle: an AUTHORED faithful drill (so the TrustBadge faithful path applies;
  //    note the prep blob carries NO kc_id, so GroundedExplanationCard no-ops — that is
  //    a queue-path surface, asserted ABSENT below) ──
  await page.route("**/api/v1/tasks/*/prep", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      taskId: "task-1", generatedAt: "2026-06-08T00:00:00Z", version: 1,
      problemsJson: JSON.stringify([{ problemId: "p1", page: 0, statement: "Trace fib(5)." }]),
      drillsJson: JSON.stringify({ p1: {
        drill: "Trace fib(5).", worked: "fib(5)=fib(4)+fib(3)=5",
        definition: "Recursion: a function defined in terms of itself.",
        check: "Trace fib(4).", expectedAnswerHint: "5",
        provenance: { type: "authored", hasBeenFaithfulChecked: true },
      } }),
      railJson: "[]",
    }) }));
  await page.route("**/api/v1/task/*/reprep", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: "{}" }));

  // ── Grade reply: incorrect, carrying ladder + cited misconception + faithful status ──
  await page.route("**/api/v1/drill/grade", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      correct: false, score: 0.2, rubric: {}, misconception: "OFF_BY_ONE",
      elaboratedFeedback: "Aproape — recitește cazul de bază.",
      verification_status: "faithful",
      ladder_rungs: [
        { level: 0, text: "Uită-te din nou la enunț." },
        { level: 1, text: "Explică-ți cu voce tare." },
        { level: 2, text: "Greșeala: confunzi cazul de bază." },
      ],
      misconception_payload: {
        id: "OFF_BY_ONE", refutation: "Cazul de bază oprește recursia la n<=1.",
        figure_spec: null, self_explanation_prompt: null,
        source: { doc: "curs3.pdf", page: 12, span: null, quote: "caz" },
      },
    }) }));

  // ── Grounded teaching (faithful) — defensive stub only. The task-prep fixture
  //    carries NO kc_id, so DrillStack mounts with kcId=undefined and
  //    GroundedExplanationCard never fetches /teaching (honest no-op). This route
  //    exists so any stray fetch returns 200 instead of a 404 (which would trip the
  //    zero-4xx gate). The card itself is asserted in the queue-path unit test
  //    (DrillStack.grounded.test.tsx, Task 10), NOT here — see the note below. ──
  await page.route("**/api/v1/teaching/*", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      kcId: "kc-1", name_ro: "Recursie",
      explanation_ro: "O funcție definită prin ea însăși.",
      worked_example_ro: "fib(5)=5",
      provenance: { type: "authored", hasBeenFaithfulChecked: true },
    }) }));

  // ── (1) shell + first paint ──
  await page.goto("/tutor/?taskId=task-1");
  expect(page.url()).toContain("/tutor");
  await expect(page.getByTestId("app-shell")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("drill-stack")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("drill-confidence-row")).toBeVisible();
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);
  expect(bad, `4xx/5xx on first paint:\n${bad.join("\n")}`).toEqual([]);

  // ── (3) interact: pick confidence, type, check ──
  await page.getByTestId("confidence-MAYBE").click();
  await page.getByTestId("drill-attempt-input").fill("4");
  await page.getByTestId("drill-check-btn").click();

  // ── trust surfaces paint post-grade ──
  await expect(page.getByTestId("drill-feedback-ladder")).toBeVisible({ timeout: 10000 });
  await expect(page.getByTestId("feedback-rung-rail")).toBeVisible();
  await expect(page.getByTestId("feedback-rung-0")).toBeVisible();
  await expect(page.getByTestId("feedback-rung-live-pip")).toBeVisible();
  await expect(page.getByTestId("misconception-ribbon")).toBeVisible();
  await expect(page.getByTestId("misconception-ribbon-kicker")).toContainText("OFF_BY_ONE");
  await expect(page.getByTestId("misconception-ribbon-refutation")).toBeVisible();
  await expect(page.getByTestId("misconception-ribbon-citation")).toContainText("curs3.pdf");
  await expect(page.getByTestId("trust-badge")).toBeVisible();
  await expect(page.getByTestId("trust-badge")).toContainText(/matches your lecture/i);
  // grounded-explanation-card is a QUEUE-PATH surface (needs QueueItem.kc_id). The
  // task-prep blob has no kc_id, so the card honestly no-ops here — assert it is
  // ABSENT on the task-prep path. Its paint-when-kcId-present behavior is covered by
  // the queue-path unit test (DrillStack.grounded.test.tsx, Task 10).
  await expect(page.getByTestId("grounded-explanation-card")).toHaveCount(0);

  // ── (3) escalate the ladder; (4) no error after click ──
  await page.getByTestId("feedback-rung-escalate-button").click();
  await expect(page.getByTestId("feedback-rung-1")).toBeVisible();
  await expect(page.getByText(/404|HTTP \d{3}|not found|error/i)).toHaveCount(0);

  // ── (5) rendering-boundary invariant: faithful badge never co-renders with the generated badge ──
  const coRender = await page.evaluate(() => {
    const blocks = Array.from(document.querySelectorAll("[data-content-block]"));
    return blocks.some((b) =>
      b.querySelector('[data-faithful="true"]') && b.querySelector('[data-provenance-type="generated"]'));
  });
  expect(coRender, "faithful + generated badge co-rendered in one content block").toBe(false);
  await expect(page.getByTestId("provenance-badge")).toHaveCount(0); // authored fixture → no generated badge

  // ── (2/4) zero 4xx/5xx across the whole flow ──
  expect(bad, `4xx/5xx during interaction:\n${bad.join("\n")}`).toEqual([]);
});
```

- [ ] Run the gate: `cd tutor-web; npm run e2e -- phase5-core-loop.spec.ts`. Confirm GREEN.
- [ ] **Prove it is failable (E4b, non-negotiable):** temporarily rename the `drill-feedback-ladder` assertion locator to `drill-feedback-ladder-BROKEN`, run the spec, confirm it FAILS; revert, confirm it PASSES. Record the red→green cycle in the commit body. (Alternatively, temporarily flip the `**/api/v1/drill/grade` stub to `status: 500` and confirm the spec goes red on the check click, then revert.)
- [ ] Run the FULL suite one final time: `cd tutor-web; npm test` (unit) — confirm green; then `npm run e2e` — confirm both pre-existing specs + the new one are green.
- [ ] Run `npx tsc --noEmit` — confirm zero errors.
- [ ] **Commit:** `test(phase5): interaction-smoke e2e gate — all trust surfaces paint + badge-boundary invariant (red→green proven)`

---

### Task 16 — Final review (whole-branch)

- [ ] Run the build-and-mount self-audit: `Grep` each new component filename (`FeedbackLadder`, `MisconceptionRibbon`, `TrustBadge`, `GroundedExplanationCard`, `ProvenanceBadge`) inside `tutor-web/src/components/DrillStack.tsx` — confirm each appears as an import AND a JSX element. (FeedbackLadder/MisconceptionRibbon/TrustBadge/GroundedExplanationCard/ProvenanceBadge all mount in DrillStack.)
- [ ] Confirm no `_`-prefixed dead props were introduced (`Grep` `: _` in `DrillStack.tsx`).
- [ ] Confirm the acceptance grep: `Grep` every Visual-Acceptance `data-testid` across `tutor-web/src` — each must appear in a component render AND be asserted by the gate the Visual-Acceptance list assigns it to:
  - **e2e-asserted** testids (every one EXCEPT those labeled below) must appear in `phase5-core-loop.spec.ts`.
  - **unit-asserted (not e2e)** testids are held only to their named unit gate, NOT the task-prep e2e bar: `feedback-rung-2`/`feedback-rung-3`/`feedback-rung-4` → `FeedbackLadder.test.tsx`; `feedback-rung-escalate-button`'s disabled give-up state → `FeedbackLadder.test.tsx`; `grounded-explanation-card` → **queue-path surface**, paint-asserted in the Task-10 unit test (`DrillStack.grounded.test.tsx`, which passes a `kcId` as the queue path would) and asserted ABSENT in the task-prep e2e (no kc_id → honest no-op). Do NOT flag these as task-prep-e2e gaps.
- [ ] If the sibling backend plan (`2026-06-08-grounded-teaching-layer.md`) has NOT yet landed `/api/v1/teaching/{kcId}` + `DrillContentDto.provenance` on `main`, note in the commit that the Phase-5 UI is wired-and-tested against the stubbed contract and will paint live once the backend lands (no further frontend work needed — the wire types are frozen here).
- [ ] **Commit (if any review fixes):** `chore(phase5): final build+mount + data-testid acceptance self-audit`

---

**Out of scope (deferred to a later plan):** the 0c/0d/0e first-time term-landing reading surfaces — `EchoBand` / `TermLanding` / `PredictionGate` / `RetrievalGate` / `ConfidenceRow` (and their `lesson-entry-*`, `term-landing-*`, `retrieval-gate-*`, `confidence-row` testids). This plan wires the existing post-prep DRILL loop to the trust-engine; the cold first-encounter reading flow is a separate surface family.