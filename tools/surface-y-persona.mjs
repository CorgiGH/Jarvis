// tools/surface-y-persona.mjs
export function updateLedger(ledger, schema, domText) {
  const next = new Set(ledger);
  const lower = domText.toLowerCase();
  for (const c of schema.concepts) {
    if (c.generic) continue;
    // NOTE: substring match — a single-word concept id (e.g. "pdf", "plot")
    // would false-positive on nav/footer text. Keep concept ids multi-word.
    const aliases = [c.id.replace(/_/g, " "), ...(c.aliases ?? [])];
    if (aliases.some(a => lower.includes(a.toLowerCase()))) {
      next.add(c.id);
    }
  }
  return next;
}

// Deterministic given `seed`: tuples[seed % length]. The default Date.now()
// just gives call-site variety across runs — two calls in the same ms return
// the same tuple, so callers that need a specific tuple should pass an explicit seed.
export function sampleConfusionTuple(schema, seed = Date.now()) {
  const tuples = schema.confusion_tuples ?? [];
  if (tuples.length === 0) return null;
  return tuples[seed % tuples.length];
}

// `currentDom` is expected to be document.body.innerText (visible text), not
// raw HTML — the 4000-char cap is safe on text but would cut mid-tag on markup.
export function buildPersonaPrompt({ schema, ledger, sessionHistory, activeConfusionTuple, currentDom }) {
  const unknownConcepts = schema.concepts
    .filter(c => !c.generic && !ledger.has(c.id))
    .map(c => c.id);
  const historyText = sessionHistory.slice(-5).map(e => `- ${e.action} ${e.target ?? ""}: ${e.observation ?? ""}`).join("\n");
  const confusionLine = activeConfusionTuple
    ? `\nYou frequently confuse ${activeConfusionTuple.between[0]} with ${activeConfusionTuple.between[1]} because ${activeConfusionTuple.why}.`
    : "";

  return `SYSTEM: You are Alex, a first-year FII Iași AI student. You have NEVER seen this course material before this session. You know basic high-school math and some R syntax. You do NOT know any of the following unless this session's history shows you've read about them: ${unknownConcepts.join(", ") || "(none)"}

If a concept name appears unfamiliar, stay confused — say so, ask sidekick, or stare at it. NEVER reason from knowledge you haven't been shown this session. If you find yourself "remembering" something off-ledger, mark [LEAK?] and explain.

SESSION HISTORY (last 5 events):
${historyText || "  (none)"}

SEEN-CONCEPTS LEDGER:
  ${[...ledger].join(", ") || "(empty)"}
${confusionLine}

CURRENT DOM (visible to you):
${currentDom.slice(0, 4000)}

ACTION RULES (basic UI literacy — you have this even as a confused beginner):
- Pick "target" from the INTERACTIVE ELEMENTS list — use the css-selector shown (the part before the —), not the label text.
- After you "type" into a field it will show [current value: "..."] next turn. That means it is FILLED. Do NOT type into it again — move on.
- To submit or check an answer, "click" the relevant button (e.g. one labelled CHECK / SUBMIT / ANSWER). A button only works when it is NOT marked [disabled].
- If an action changed nothing, do not repeat it — try a different element.

Decide ONE next action. Reply STRICT JSON:
{
  "thinking": "<2-3 sentences internal monologue>",
  "action": "click" | "type" | "navigate" | "ask_sidekick" | "give_up",
  "target": "<CSS selector or text label>",
  "payload": "<typed text if action=type/ask_sidekick>",
  "observation": "<what I'm confused about or noticed>"
}`;
}
