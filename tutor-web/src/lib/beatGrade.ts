import { jarvisFetch } from "./api";

/** Request to POST /api/v1/lesson/{kcId}/beat — mirror of Kotlin ApiBeatGradeRequest (core §0.9C). */
export interface ApiBeatGradeRequest {
  beat_type: string;                 // "predict" | "attempt" | "check"
  selected_index?: number | null;    // choice beats
  free_input?: string | null;        // numerical attempt / numeric check
  prediction_text?: string | null;   // predict beats — stored on the attempt row
}

/** Reply — mirror of Kotlin ApiBeatGradeReply (core §0.9C). */
export interface ApiBeatGradeReply {
  correct: boolean;
  score: number;
  feedback_ro: string;
  beat_type: string;
  lesson_complete: boolean;
  first_encounter: boolean;
  phase?: string | null;
  verification_status?: string | null;
}

/**
 * POST a gated beat to the server (the SERVER grades + writes; the client never self-grades).
 * Throws on non-2xx so the orchestrator's gate stays closed on failure (the learner retries).
 */
export async function postBeatGrade(
  kcId: string,
  req: ApiBeatGradeRequest,
): Promise<ApiBeatGradeReply> {
  const res = await jarvisFetch(`/api/v1/lesson/${encodeURIComponent(kcId)}/beat`, {
    method: "POST",
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    throw new Error(`postBeatGrade ${res.status}: ${await res.text().catch(() => "")}`);
  }
  return res.json() as Promise<ApiBeatGradeReply>;
}
