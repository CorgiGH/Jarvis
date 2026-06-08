import { jarvisFetch } from "./api";

export type RailItemType = "PDF" | "SCRATCHPAD" | "CONCEPT" | "PRIOR_GAP" | "FSRS_DUE";

export interface RailItem {
  type: RailItemType;
  label: string;
  action: "OPEN_DRAWER" | "NAVIGATE";
  payload: Record<string, unknown>;
}

export interface TaskPrepReply {
  taskId: string;
  generatedAt: string;
  version: number;
  problemsJson: string;
  drillsJson: string;
  railJson: string;
}

export interface TaskSubmitReply {
  taskId: string;
  status: string;
  submittedAt: string;
}

/** GET /api/v1/tasks/{id}/prep. Returns null on 404 (no prep cached yet). Throws on other non-2xx. */
export async function getTaskPrep(taskId: string): Promise<TaskPrepReply | null> {
  const res = await jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/prep`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`getTaskPrep ${res.status}: ${await res.text().catch(() => "")}`);
  return res.json() as Promise<TaskPrepReply>;
}

/** POST /api/v1/task/{id}/reprep. Fire-and-forget — callers handle polling. */
export async function triggerReprep(taskId: string): Promise<void> {
  await jarvisFetch(`/api/v1/task/${encodeURIComponent(taskId)}/reprep`, { method: "POST" });
}

/** POST /api/v1/tasks/{id}/submit. */
export async function submitTask(taskId: string, note?: string): Promise<TaskSubmitReply> {
  const res = await jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/submit`, {
    method: "POST",
    body: JSON.stringify({ note: note ?? null }),
  });
  if (!res.ok) throw new Error(`submitTask ${res.status}: ${await res.text().catch(() => "")}`);
  return res.json() as Promise<TaskSubmitReply>;
}

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
