import { createHash } from "node:crypto";

export async function callLlm({
  apiKey,
  model,
  systemPrompt,
  userPrompt,
  temperature = 0.0,
  seed = null,
  maxTokens = null,
  transport = globalThis.fetch,
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

  const resp = await transport("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "HTTP-Referer": "https://corgflix.duckdns.org/tutor/",
    },
    body: JSON.stringify(body),
  });
  const latency_ms = Date.now() - t0;
  if (!resp.ok) {
    const text = await resp.text().catch(() => "");
    throw new Error(`callLlm: ${resp.status} ${text.slice(0, 200)}`);
  }
  const data = await resp.json();
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
