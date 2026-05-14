// tools/surface-y-gate.mjs
export function extractConceptReferences(text, schema) {
  const lower = text.toLowerCase();
  const found = new Set();
  for (const c of schema.concepts ?? []) {
    if (c.generic) continue;
    // Same alias-scan shape as updateLedger() in surface-y-persona.mjs — keep in sync.
    const aliases = [c.id.replace(/_/g, " "), ...(c.aliases ?? [])];
    for (const a of aliases) {
      if (lower.includes(a.toLowerCase())) {
        found.add(c.id);
        break;
      }
    }
  }
  return [...found];
}

export function checkResponse({ text, schema, ledger }) {
  const refs = extractConceptReferences(text, schema);
  const violations = refs.filter(r => !ledger.has(r));
  return { ok: violations.length === 0, refs, violations };
}

export async function gateLoop({
  initialUserPrompt,
  systemPrompt = "",
  schema, ledger,
  callLlm,
  apiKey, model, temperature = 0.4, seed = null,
  maxRegens = 2,
}) {
  let userPrompt = initialUserPrompt;
  const tries = [];
  for (let i = 0; i <= maxRegens; i++) {
    const r = await callLlm({ apiKey, model, systemPrompt, userPrompt, temperature, seed });
    const check = checkResponse({ text: r.text, schema, ledger });
    tries.push({ text: r.text, model_resolved: r.model_resolved, prompt_sha256: r.prompt_sha256, check });
    if (check.ok) return { ok: true, leaked: false, finalText: r.text, finalCheck: check, tries, llmMeta: r };
    userPrompt = `${initialUserPrompt}\n\nYour previous reply referenced concepts you have not been shown: ${check.violations.join(", ")}. Stay confused. Do not mention these concepts again.`;
  }
  const last = tries[tries.length - 1];
  return {
    ok: false, leaked: true,
    finalText: last.text, finalCheck: last.check, tries,
    llmMeta: { model_resolved: last.model_resolved, prompt_sha256: last.prompt_sha256 },
  };
}
