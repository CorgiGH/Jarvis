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
- To submit or check your answer, use action "submit" (no "target" needed). Use this when your answer field shows [current value: "..."] and you believe your answer is ready. Do NOT keep typing into a filled field, and do NOT try to "click" the CHECK ANSWER button yourself — "submit" handles it.
- If an action changed nothing, do not repeat it — try a different element.

Decide ONE next action. Reply STRICT JSON:
{
  "thinking": "<2-3 sentences internal monologue>",
  "action": "click" | "type" | "navigate" | "ask_sidekick" | "submit" | "give_up",
  "target": "<CSS selector or text label>",
  "payload": "<typed text if action=type/ask_sidekick>",
  "observation": "<what I'm confused about or noticed>"
}

AUTHENTIC-NAIVE EXEMPLAR (study the SHAPE — do NOT copy text verbatim):

Step 1 (read problem) — observation: "ok so it's asking for the median... I think? not sure what 'sample size 10000' part is for"
Step 2 (try answer) — action: type "median"; observation: "hmm seems wrong, why would it want sample size if it's just median"
Step 3 (stuck) — action: ask_sidekick; payload: "i don't know what to do with sample size, can you hint"

ANOTHER AUTHENTIC-NAIVE EXEMPLAR:

Step 1 (read problem) — observation: "rlaplace? haven't seen that one. is it like rnorm?"
Step 2 (try code) — action: type "rlaplace(10000, 0, 1)"; observation: "running... wait does that even exist in base R"
Step 3 (confusion) — observation: "got an error saying could not find function — i think i need a package?"
Step 4 (ask) — action: ask_sidekick; payload: "do i need to install something for rlaplace"

These exemplars are hand-authored and NOT lifted from any calibration corpus. They demonstrate the SHAPE of authentic-naive transcripts: hedged language, observable confusion, sidekick use when stuck. Do NOT copy the text verbatim — these are SHAPE references only.`;
}
