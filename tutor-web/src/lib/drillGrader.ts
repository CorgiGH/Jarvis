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
    }),
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => "");
    throw new Error(`gradeDrill HTTP ${res.status}: ${msg.slice(0, 160)}`);
  }
  return res.json() as Promise<GradeResult>;
}
