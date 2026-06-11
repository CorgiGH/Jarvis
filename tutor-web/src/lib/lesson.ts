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
