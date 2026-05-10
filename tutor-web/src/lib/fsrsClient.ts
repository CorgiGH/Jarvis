import { jarvisFetch } from "./api";

export interface FsrsCardView {
  id: string;
  front: string;
  back: string;
  sourceTaskId: string | null;
  difficulty: number;
  stability: number;
  retrievability: number;
  dueAt: string;
  lapses: number;
}

export interface FsrsDueReply {
  cards: FsrsCardView[];
}

export interface FsrsGradeReply {
  cardId: string;
  nextDueAt: string;
  newDifficulty: number;
  newStability: number;
}

export interface FsrsForecastReply {
  tomorrow: number;
  thisWeek: number;
  thisMonth: number;
}

async function requireOk(res: Response): Promise<Response> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res;
}

export async function getDue(limit?: number): Promise<FsrsCardView[]> {
  const qs = limit != null ? `?limit=${limit}` : "";
  const res = await requireOk(await jarvisFetch(`/api/v1/fsrs/due${qs}`));
  const body: FsrsDueReply = await res.json();
  return body.cards;
}

export async function gradeCard(
  id: string,
  grade: 1 | 2 | 3 | 4,
): Promise<FsrsGradeReply> {
  const res = await requireOk(
    await jarvisFetch(`/api/v1/fsrs/${encodeURIComponent(id)}/grade`, {
      method: "POST",
      body: JSON.stringify({ grade }),
    }),
  );
  return res.json();
}

export async function getForecast(): Promise<FsrsForecastReply> {
  const res = await requireOk(await jarvisFetch("/api/v1/fsrs/forecast"));
  return res.json();
}
