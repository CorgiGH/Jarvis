/**
 * Plan 4a §0.9C — Impeccable applicable-subset, applied as a POST-PROCESS FILTER.
 *
 * impeccable@2.3.2 `detect` has NO --config / rule-subset flag (verified live, finding #1),
 * so the locked "applicable-subset" leg is implemented here: pipe `detect --json` through this
 * script, which keeps only findings whose `antipattern` id is in tools/impeccable-rules.json.
 *
 * Usage (the CI pipeline):
 *   npx --yes impeccable@2.3.2 detect --json src/ | node tools/impeccable-filter.mjs
 *
 * stdout: the filtered report, same shape as the input (array in -> array out; object in ->
 *         object out with the findings array replaced by the kept subset).
 * stderr: a one-line summary ("impeccable-filter: kept K of N findings (subset of M enabled)").
 * exit  : 0 when zero findings survive the subset, 1 when >=1 survives. The CI step appends
 *         `|| true` (fail-open) so this exit code does not fail the build until the PM unlock;
 *         the code is meaningful now so flipping to blocking later is just dropping `|| true`.
 *
 * The detect finding key is `antipattern` (e.g. "overused-font"). If the real binary emits a
 * different key, change FINDING_KEY below (and the allow-list) to match — see Task 3 Step 1 (c).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const FINDING_KEY = "antipattern";
const HERE = dirname(fileURLToPath(import.meta.url));
const RULES_PATH = resolve(HERE, "impeccable-rules.json");

/** Read all of stdin to a string (synchronous, fd 0). Empty stdin -> "". */
function readStdin() {
  try {
    return readFileSync(0, "utf8");
  } catch {
    return "";
  }
}

function loadEnabled() {
  const rules = JSON.parse(readFileSync(RULES_PATH, "utf8"));
  if (!Array.isArray(rules.enabled)) {
    throw new Error(`tools/impeccable-rules.json must have an "enabled" array`);
  }
  return new Set(rules.enabled);
}

/** Pull the findings array out of whatever top-level shape detect emitted. */
function extractFindings(parsed) {
  if (Array.isArray(parsed)) return { findings: parsed, container: null, key: null };
  for (const key of ["findings", "results", "issues", "violations"]) {
    if (parsed && Array.isArray(parsed[key])) {
      return { findings: parsed[key], container: parsed, key };
    }
  }
  // Unknown object shape with no recognizable array -> treat as zero findings (fail-open safe).
  return { findings: [], container: parsed, key: null };
}

function main() {
  const raw = readStdin().trim();
  const enabled = loadEnabled();

  // No JSON on stdin (detector printed nothing / wrote to stderr): emit an empty array, exit 0.
  if (raw === "") {
    process.stdout.write("[]\n");
    process.stderr.write(
      `impeccable-filter: no JSON on stdin; kept 0 of 0 findings (subset of ${enabled.size} enabled)\n`,
    );
    process.exit(0);
  }

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    // Mixed text+JSON or malformed: do not crash the fail-open CI step — report and pass through empty.
    process.stderr.write(`impeccable-filter: stdin was not valid JSON (${e.message}); kept 0\n`);
    process.stdout.write("[]\n");
    process.exit(0);
  }

  const { findings, container, key } = extractFindings(parsed);
  const total = findings.length;
  const kept = findings.filter((f) => enabled.has(f && f[FINDING_KEY]));

  let out;
  if (container && key) {
    out = { ...container, [key]: kept };
  } else {
    out = kept;
  }
  process.stdout.write(JSON.stringify(out, null, 2) + "\n");
  process.stderr.write(
    `impeccable-filter: kept ${kept.length} of ${total} findings (subset of ${enabled.size} enabled)\n`,
  );
  process.exit(kept.length > 0 ? 1 : 0);
}

main();
