import { jarvisFetch } from "./api";
import type { DrillProvenance } from "../components/DrillStack";

/**
 * Wire reply for GET /api/v1/lesson/{kcId} (§NEW-L, interface-signatures-lock.md).
 * Faithful-gated first-encounter lesson. 404 when non-faithful / disputed / unknown.
 */
export interface ApiLessonReply {
  kcId: string;
  kc_name_ro: string;
  kc_name_en: string;
  /** KC stem_template or null (honest-null when no stem). */
  concrete_question_ro: string | null;
  /** Source quote for EchoBand or null. */
  echo_source_ro: string | null;
  /** 2-4 RO prediction options; always [] today (no authored options yet). */
  prediction_options: string[];
  /** = kc_name_ro (the primary Romanian label). */
  term_ro: string;
  /** Always null today (no dedicated definition field). */
  definition_ro: string | null;
  /** KnowledgeConcept.explanation_ro. */
  explanation_ro: string | null;
  /** KnowledgeConcept.worked_example_ro. */
  worked_example_ro: string | null;
  /** Always {type:"authored", hasBeenFaithfulChecked:true} — gate guarantees faithfulness. */
  provenance: DrillProvenance;
  /**
   * Plan-3 §0.9B — the ADDITIVE beats payload. Present iff the KC has complete beats for its
   * concept_type (server inline guard, Task 3); null ⇒ legacy payload (never served post-Task-3:
   * incomplete beats 404). Field names mirror Kotlin ApiLessonBeats EXACTLY (divergence = wire bug).
   */
  beats?: ApiLessonBeats | null;
}

/** Mirror of Kotlin ApiLessonBeats (core §0.9B). BeatType wire literals are lowercase. */
export interface ApiLessonBeats {
  /** BeatType lowercase names, in served order, e.g. ["predict","attempt","reveal","name","check"]. */
  plan: string[];
  /** The KC's concept_type wire literal. */
  concept_type: string;
  /** Present iff plan contains "predict". */
  predict?: ApiBeatPredict | null;
  attempt?: ApiBeatAttempt | null;
  reveal?: ApiBeatReveal | null;
  /** Present iff plan contains "name". */
  name?: ApiBeatName | null;
  check?: ApiBeatCheck | null;
}

export interface ApiPredictOption {
  text: string;
  callback: string;
  correct: boolean;
}
export interface ApiBeatPredict {
  prompt: string;
  options: ApiPredictOption[];
}
export interface ApiAttemptChoice {
  text: string;
  correct: boolean;
  feedback: string;
}
export interface ApiSkeletonRow {
  label: string;
  formula: string | null;
  is_decision_row: boolean;
}
export interface ApiTraceStep {
  row_index: number;
  value: string;
  callout: string | null;
}
export interface ApiBeatAttempt {
  statement: string;
  choices: ApiAttemptChoice[];
  skeleton_rows: ApiSkeletonRow[];
  trace_steps: ApiTraceStep[];
  input_schema: string | null;
  feedback_correct: string;
}
export interface ApiRevealStep {
  text: string;
  callout: string;
}
export interface ApiFigureBinding {
  family_id: string;
  instance_id: string;
}
export interface ApiBeatReveal {
  steps: ApiRevealStep[];
  figure?: ApiFigureBinding | null;
}
export interface ApiBeatName {
  definition: string;
  invariant_statement: string;
  why_matters: string;
}
export interface ApiBeatCheck {
  item_stem: string;
  choices: ApiAttemptChoice[];
  numeric_answer: string | null;
  numeric_tolerance: number | null;
}

/**
 * GET /api/v1/lesson/{kcId}.
 * Returns null on 404 (non-faithful / disputed / unknown — FAIL-LOUD gate).
 * Returns null on 401 (no session).
 * Throws on other non-2xx errors.
 */
export async function getLesson(kcId: string): Promise<ApiLessonReply | null> {
  const res = await jarvisFetch(`/api/v1/lesson/${encodeURIComponent(kcId)}`);
  if (res.status === 404 || res.status === 401) return null;
  if (!res.ok) {
    throw new Error(`getLesson ${res.status}: ${await res.text().catch(() => "")}`);
  }
  return res.json() as Promise<ApiLessonReply>;
}
