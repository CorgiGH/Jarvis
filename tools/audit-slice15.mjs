// tools/audit-slice15.mjs — Slice-1.5 audit orchestrator + spec parser.
// Spec: docs/superpowers/specs/2026-05-17-slice15-audit-design.md

/**
 * Parse the "State matrix" markdown table out of the spec doc.
 * Returns an array of { id, route, reach, selectors, expectations }.
 *
 * Looks for a markdown table whose first column is S-NN. Strips backticks
 * from cell content. Splits selectors on comma (only outside parens).
 */
export function parseStateMatrix(specText) {
  const lines = specText.split("\n");
  const rows = [];
  for (const line of lines) {
    if (!/^\|\s*S-\d+\s*\|/.test(line)) continue;
    const cells = line.split("|").slice(1, -1).map(c => c.trim().replace(/`/g, ""));
    if (cells.length < 5) continue;
    const [id, route, reach, selectorsCell, expectations] = cells;
    const selectors = selectorsCell
      .split(/,\s*(?![^()]*\))/)
      .map(s => s.replace(/\s*\(.*\)\s*$/, "").trim())
      .filter(s => /^[a-z][a-z0-9-]+$/.test(s));
    rows.push({ id, route, reach, selectors, expectations });
  }
  return rows;
}

/**
 * Mechanical severity rules per spec § Phase B severity classifier.
 * Subjective LLM judge severities are bumped one band down: an LLM
 * "HIGH" maps to a deterministic-pipeline MED at most.
 */
export function classifySeverity(finding) {
  switch (finding.category) {
    case "missing-selector":
    case "pageerror":
    case "first-paint-http-error":
    case "raw-http-error":
      return "HIGH";
    case "axe-violation":
      return finding.axeLevel === "wcag2aa" ? "HIGH" : "MED";
    case "snake-case-leak":
    case "screaming-snake-leak":
    case "model-name-leak":
    case "placeholder-leak":
      return "MED";
    case "llm-judge":
      if (finding.judgeSeverity === "HIGH") return "MED";
      return "LOW";
    default:
      return "LOW";
  }
}
