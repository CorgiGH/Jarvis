export interface RankableTask {
  id: string;
  subject: string;
  title: string;
  deadline: string;
  status?: string;
}

const KIND_WEIGHT: Record<string, number> = {
  exam: 1.0, partial: 1.0, final: 1.0,
  tema: 0.8, hw: 0.8, homework: 0.8,
  lab: 0.5, laborator: 0.5,
  seminar: 0.3, sem: 0.3,
};

export function deadlineUrgency(deadlineIso: string, now: Date = new Date()): number {
  const days = (new Date(deadlineIso).getTime() - now.getTime()) / 86400000;
  if (days < 0) return 1;
  if (days >= 14) return 0;
  return 1 - days / 14;
}

export function kindWeight(title: string): number {
  const t = title.toLowerCase();
  for (const [k, w] of Object.entries(KIND_WEIGHT)) {
    if (t.includes(k)) return w;
  }
  return 0.4;
}

/**
 * Composite rank score per spec § 6.5:
 *   (deadline_urgency × 0.5) + (weight × 0.2) + (readiness × 0.3)
 * readiness defaults to 0.5 (mid-confidence) when no KnowledgeRepo
 * signal is available.
 */
export function rankTask(task: RankableTask, readiness: number = 0.5, now: Date = new Date()): number {
  const u = deadlineUrgency(task.deadline, now);
  const w = kindWeight(task.title);
  return u * 0.5 + w * 0.2 + readiness * 0.3;
}
