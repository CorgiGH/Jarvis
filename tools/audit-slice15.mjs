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
