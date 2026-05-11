import { jarvisFetch } from "./api";
import type { SidekickEnvelope } from "./inlineAsk";

export interface Citation {
  path: string;
  snippet: string;
  score: number;
}

export interface SidekickReply {
  text: string;
  model: string;
  quotedContext: string | null;
  citations?: Citation[];
}

/** POST the envelope to /api/v1/sidekick/ask and return the reply.
 *  Throws on network error or non-OK status so callers can render
 *  the "(LLM unavailable)" fallback message. */
export async function askSidekick(env: SidekickEnvelope): Promise<SidekickReply> {
  const res = await jarvisFetch("/api/v1/sidekick/ask", {
    method: "POST",
    body: JSON.stringify(env),
  });
  if (!res.ok) {
    const msg = await res.text().catch(() => res.statusText);
    throw new Error(`sidekick ${res.status}: ${msg}`);
  }
  return res.json() as Promise<SidekickReply>;
}
