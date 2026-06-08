import { jarvisFetch } from "./api";

export interface GradeRubric {
  numeric: boolean;
  mechanism: boolean;
  justification: boolean;
  [key: string]: boolean;
}

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

export interface GradeDrillArgs {
  taskId: string;
  problemId: string;
  problemStatement: string;
  userAttempt: string;
  expectedAnswerHint: string;
  giveUp?: boolean;
  /**
   * Code-grading extension (Slice 3 spike). When `language` is "r" / "python"
   * / "cpp", the backend uses the GRADE_PROMPT_CODE prompt path with the
   * `referenceSolution` + `rubricItems` baked into the LLM request. When
   * omitted or "text", the original one-line-answer grading path is used.
   * No code is ever executed server-side; grading is judge-from-reading.
   */
  language?: "r" | "python" | "cpp" | "text";
  referenceSolution?: string;
  rubricItems?: string[];
  /**
   * Plain-language prediction the user committed BEFORE typing the actual
   * answer. Slamecka & Graf 1978 generation-effect anchor. Optional; when
   * non-empty the grader is asked to recognize the commitment first and then
   * evaluate the attempt against the rubric.
   */
  prediction?: string;
  /** H16: confidence committed BEFORE the verdict (DEFINITELY|MAYBE|GUESS|IDK).
   *  Persisted to AttemptsTable.studentConfidence (column `student_confidence`) —
   *  grep `it[studentConfidence] = req.student_confidence` in TutorRoutes.kt. */
  studentConfidence?: StudentConfidence;
}

export async function gradeDrill(args: GradeDrillArgs): Promise<GradeResult> {
  const res = await jarvisFetch("/api/v1/drill/grade", {
    method: "POST",
    body: JSON.stringify({
      taskId: args.taskId,
      problemId: args.problemId,
      problemStatement: args.problemStatement,
      userAttempt: args.userAttempt,
      expectedAnswerHint: args.expectedAnswerHint,
      ...(args.giveUp ? { giveUp: true } : {}),
      ...(args.language ? { language: args.language } : {}),
      ...(args.referenceSolution ? { referenceSolution: args.referenceSolution } : {}),
      ...(args.rubricItems ? { rubricItems: args.rubricItems } : {}),
      ...(args.prediction ? { prediction: args.prediction } : {}),
      ...(args.studentConfidence ? { studentConfidence: args.studentConfidence } : {}),
    }),
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => "");
    throw new Error(`gradeDrill HTTP ${res.status}: ${msg.slice(0, 160)}`);
  }
  return res.json() as Promise<GradeResult>;
}
