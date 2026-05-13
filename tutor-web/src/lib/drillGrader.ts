import { jarvisFetch } from "./api";

export interface GradeRubric {
  numeric: boolean;
  mechanism: boolean;
  justification: boolean;
  [key: string]: boolean;
}

export interface GradeResult {
  correct: boolean;
  score: number;
  rubric: GradeRubric;
  misconception: string | null;
  elaboratedFeedback: string;
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
    }),
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => "");
    throw new Error(`gradeDrill HTTP ${res.status}: ${msg.slice(0, 160)}`);
  }
  return res.json() as Promise<GradeResult>;
}
