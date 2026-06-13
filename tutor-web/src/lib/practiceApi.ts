import { jarvisFetch } from "./api";
import type { ItemVerdict } from "./drillGrader";

/**
 * Plan-6 Task 8 — typed client for the practice surfaces endpoint groups (§0.9-G).
 * Mirrors jarvis/web/PracticeRoutes.kt 1:1, snake_case preserved on the wire (the drillGrader.ts
 * convention). One fetch site per endpoint, typed replies.
 *
 * Surfaces consuming this: ProofDrill / StepTraceDrill (Task 9), CodePractice / DeliverableTracker
 * (Task 10).
 */

// ── problems list (NEVER carries the reference solution — INV-6.6 server-enforced) ──────────────

export type PracticeSurface = "proof" | "trace" | "code";

export interface ProofSubstepLabel {
  id: string;
  label_ro: string;
}

export interface ProofFrame {
  template_ro: string;
  substeps: ProofSubstepLabel[];
}

export interface TraceStepLabel {
  index: number;
  label_ro: string | null;
}

export interface PracticeProblem {
  id: string;
  subject: string;
  archetype: string;
  surface: string; // "proof" | "trace" | "code"
  statement_ro: string;
  exam_language?: string | null;
  proof_frame?: ProofFrame | null;
  trace_steps?: TraceStepLabel[];
  exam_language_constraints?: string | null;
}

export interface PracticeProblemsReply {
  problems: PracticeProblem[];
}

export async function listPracticeProblems(
  subject: string,
  surface?: PracticeSurface,
): Promise<PracticeProblemsReply> {
  const params = new URLSearchParams({ subject });
  if (surface) params.set("surface", surface);
  const res = await jarvisFetch(`/api/v1/practice/problems?${params.toString()}`);
  if (!res.ok) {
    throw new Error(`listPracticeProblems HTTP ${res.status}`);
  }
  return res.json() as Promise<PracticeProblemsReply>;
}

// ── proof grade (per-substep verdicts, REQ-2) ──────────────────────────────────────────────────

export interface ProofSubstepAnswer {
  id: string;
  text: string;
}

export interface ProofGradeReply {
  item_verdicts: ItemVerdict[];
  score: number;
  correct: boolean;
  decided_by: string;
  feedback_ro: string;
}

export async function gradeProof(
  problemId: string,
  substeps: ProofSubstepAnswer[],
): Promise<ProofGradeReply> {
  const res = await jarvisFetch(`/api/v1/practice/proof/${encodeURIComponent(problemId)}/grade`, {
    method: "POST",
    body: JSON.stringify({ substeps }),
  });
  if (!res.ok) {
    throw new Error(`gradeProof HTTP ${res.status}`);
  }
  return res.json() as Promise<ProofGradeReply>;
}

// ── trace step (per-step, REQ-5 — wrong value at step 3 caught at step 3) ────────────────────────

export interface TraceStepReply {
  verdict: ItemVerdict;
  feedback_ro: string;
}

export async function gradeTraceStep(
  problemId: string,
  stepIndex: number,
  value: string,
): Promise<TraceStepReply> {
  const res = await jarvisFetch(`/api/v1/practice/trace/${encodeURIComponent(problemId)}/step`, {
    method: "POST",
    body: JSON.stringify({ step_index: stepIndex, value }),
  });
  if (!res.ok) {
    throw new Error(`gradeTraceStep HTTP ${res.status}`);
  }
  return res.json() as Promise<TraceStepReply>;
}

// ── code run (execution leg only, no grade write) ────────────────────────────────────────────────

export interface CodeRunReply {
  compiled: boolean;
  stdout_trunc: string;
  stderr_trunc: string;
  timed_out: boolean;
  degraded_legs_ro: string[];
}

export async function runCode(problemId: string, source: string): Promise<CodeRunReply> {
  const res = await jarvisFetch(`/api/v1/practice/code/${encodeURIComponent(problemId)}/run`, {
    method: "POST",
    body: JSON.stringify({ source }),
  });
  if (!res.ok) {
    throw new Error(`runCode HTTP ${res.status}`);
  }
  return res.json() as Promise<CodeRunReply>;
}

// ── code grade (the ONLY payload that carries the reference — attempt-gated, REQ-8/INV-6.6) ───────

export interface CodeGradeReply {
  item_verdicts: ItemVerdict[];
  score: number;
  correct: boolean;
  decided_by: string;
  feedback_ro: string;
  degraded_legs_ro: string[];
  reference_solution_ro: string | null;
}

export async function gradeCode(problemId: string, source: string): Promise<CodeGradeReply> {
  const res = await jarvisFetch(`/api/v1/practice/code/${encodeURIComponent(problemId)}/grade`, {
    method: "POST",
    body: JSON.stringify({ source }),
  });
  if (!res.ok) {
    throw new Error(`gradeCode HTTP ${res.status}`);
  }
  return res.json() as Promise<CodeGradeReply>;
}

// ── deliverables (Task-8 honest-empty stub; Task 10 fills it) ─────────────────────────────────────

export interface DeliverableSubProblem {
  label_ro: string;
  points: number;
}

export interface Deliverable {
  id: string;
  subject: string;
  title_ro: string;
  deadline?: string | null;
  sub_problems: DeliverableSubProblem[];
  prep_drill_ids: string[];
  source_doc?: string | null;
  synthetic: boolean;
}

export interface DeliverablesReply {
  deliverables: Deliverable[];
}

export async function listDeliverables(): Promise<DeliverablesReply> {
  const res = await jarvisFetch("/api/v1/practice/deliverables");
  if (!res.ok) {
    throw new Error(`listDeliverables HTTP ${res.status}`);
  }
  return res.json() as Promise<DeliverablesReply>;
}
