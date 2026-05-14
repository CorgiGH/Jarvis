import { createHash } from "node:crypto";

const DAILY_QUOTA_429_MARKERS = [
  "free-models-per-day",
  "daily limit",
];

function isDailyQuotaError(text) {
  const lc = text.toLowerCase();
  return DAILY_QUOTA_429_MARKERS.some((m) => lc.includes(m.toLowerCase()));
}

function parseRetryAfter(text) {
  try {
    const j = JSON.parse(text);
    const ra = j?.error?.metadata?.retry_after_seconds;
    if (typeof ra === "number" && ra > 0) return ra;
  } catch {}
  return null;
}

export async function callLlm({
  apiKey,
  model,
  systemPrompt,
  userPrompt,
  temperature = 0.0,
  seed = null,
  maxTokens = null,
  transport = globalThis.fetch,
  maxRetries = 3,
  baseDelayMs = 5000,
  sleep = (ms) => new Promise((r) => setTimeout(r, ms)),
}) {
  if (!apiKey) throw new Error("callLlm: apiKey required");
  const t0 = Date.now();
  const body = {
    model,
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: userPrompt },
    ],
    temperature,
  };
  if (seed !== null) body.seed = seed;
  if (maxTokens !== null) body.max_tokens = maxTokens;

  let lastErr = null;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const resp = await transport("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://corgflix.duckdns.org/tutor/",
      },
      body: JSON.stringify(body),
    });
    if (resp.ok) {
      const data = await resp.json();
      const latency_ms = Date.now() - t0;
      const prompt_sha256 = createHash("sha256")
        .update(systemPrompt + "\n---\n" + userPrompt)
        .digest("hex");
      return {
        text: data.choices?.[0]?.message?.content ?? "",
        model_resolved: data.model ?? model,
        prompt_sha256,
        tokens_in: data.usage?.prompt_tokens ?? null,
        tokens_out: data.usage?.completion_tokens ?? null,
        latency_ms,
      };
    }
    const text = await resp.text().catch(() => "");
    lastErr = `callLlm: ${resp.status} ${text.slice(0, 200)}`;

    const retryable = resp.status === 429 && !isDailyQuotaError(text);
    if (!retryable || attempt === maxRetries) {
      throw new Error(lastErr);
    }
    const retryAfterSec = parseRetryAfter(text);
    let delayMs = retryAfterSec !== null
      ? Math.max(retryAfterSec * 1000, baseDelayMs)
      : baseDelayMs * Math.pow(2, attempt);
    delayMs = Math.min(delayMs, 30000) + Math.floor(Math.random() * 500);
    await sleep(delayMs);
  }
  throw new Error(lastErr ?? "callLlm: retries exhausted");
}
