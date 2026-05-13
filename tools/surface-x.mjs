// Surface X — trace-replay rubric-grader core.
//
// gradeOne(): sends ONE invariant statement + the scoped event slice to an
// LLM-as-judge, parses JSON reply into {status, evidence, reason}, threads
// envelope metadata (model_resolved, prompt_sha256, tokens, latency) back
// to the caller for finding-doc provenance.
//
// majorityVote(): collapses N independent runs of the same invariant to
// one verdict via plurality. Ties degrade to N_A so the deploy hook
// (Task 1.7) errs toward "not enough signal" rather than false-fail.
//
// ofN(): K-of-N exact-match gate for the calibration mode (Task 1.4).
// Compares judge verdict vs. hand-labelled gold per fixture trace and
// reports whether the catalog meets the agreement threshold.

import { callLlm as defaultCallLlm } from "./lib/openrouter.mjs";

const GRADER_SYSTEM = `You are an invariant judge. Given a session trace and one invariant statement, return strict JSON:
{
  "status": "PASS" | "FAIL" | "N_A",
  "evidence": {"event_ids": [...], "excerpt": "..."},
  "reason": "<one sentence>"
}
N_A is reserved for invariants that don't apply to the given trace (e.g. no relevant events). Reply JSON only.`;

export async function gradeOne({
  invariantId,
  invariantStatement,
  events,
  callLlm = defaultCallLlm,
  apiKey = process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY,
  model = "google/gemma-4-26b-a4b-it:free",
  temperature = 0,
  seed = 42,
}) {
  const userPrompt = `INVARIANT: ${invariantId} — ${invariantStatement}\n\nSESSION EVENTS (jsonl):\n${events.map(e => JSON.stringify(e)).join("\n")}\n\nReply: <JSON only>`;
  const r = await callLlm({
    apiKey,
    model,
    systemPrompt: GRADER_SYSTEM,
    userPrompt,
    temperature,
    seed,
  });
  let parsed;
  try { parsed = JSON.parse(r.text); } catch { parsed = { status: "N_A", evidence: {}, reason: "parse_error" }; }
  return {
    status: parsed.status ?? "N_A",
    evidence: parsed.evidence ?? {},
    reason: parsed.reason ?? "",
    model_resolved: r.model_resolved,
    prompt_sha256: r.prompt_sha256,
    tokens_in: r.tokens_in,
    tokens_out: r.tokens_out,
    latency_ms: r.latency_ms,
  };
}

export function majorityVote(runs) {
  const counts = {};
  for (const r of runs) counts[r.status] = (counts[r.status] ?? 0) + 1;
  const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  if (sorted.length === 1) return runs[0];
  if (sorted[0][1] === sorted[1][1]) return { ...runs[0], status: "N_A", reason: "no majority" };
  return runs.find(r => r.status === sorted[0][0]);
}

export function ofN(pairs, thresholdPct = 0.80) {
  const total = pairs.length;
  const matched = pairs.filter(p => p.judge === p.gold).length;
  const k = Math.ceil(thresholdPct * total);
  return { matched, total, k, threshold_pct: thresholdPct, passed: matched >= k };
}
