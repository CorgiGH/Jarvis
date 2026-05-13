#!/usr/bin/env node
/**
 * Student stand-in pre-flight: verify whether two separate OpenRouter API keys
 * on the SAME account share account-level `:free`-tier daily quota.
 *
 * Procedure:
 *   1. Burn N free-tier calls on Key A (live `OPENROUTER_API_KEY`).
 *   2. Immediately probe N calls on Key B (proposed `OPENROUTER_API_KEY_STANDIN`).
 *   3. If Key B returns 200s → ISOLATED (separate per-key quotas).
 *      If Key B returns 429s → SHARED (account-level quota).
 *
 * Verdict written to `docs/notes/2026-05-13-openrouter-quota-isolation.md`.
 * Surface Y (and any future automation) is BLOCKED until this file exists.
 *
 * Run from repo root:
 *   OPENROUTER_API_KEY=<live> OPENROUTER_API_KEY_STANDIN=<new> \
 *     node tools/verify-openrouter-quota-isolation.mjs
 */
import { callLlm } from "./lib/openrouter.mjs";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const KEY_A = process.env.OPENROUTER_API_KEY;
const KEY_B = process.env.OPENROUTER_API_KEY_STANDIN;

if (!KEY_A || !KEY_B) {
  console.error("ERR: Set BOTH OPENROUTER_API_KEY (live) AND OPENROUTER_API_KEY_STANDIN (proposed standin key).");
  console.error("Provision a second key at https://openrouter.ai/settings/keys first.");
  process.exit(2);
}
if (KEY_A === KEY_B) {
  console.error("ERR: Keys are identical. Provision a SECOND OpenRouter API key first.");
  process.exit(2);
}

const MODEL = process.env.STANDIN_PROBE_MODEL ?? "google/gemma-4-26b-a4b-it:free";
const BURN_N = Number(process.env.STANDIN_BURN_N ?? 50);

async function burn(key, label) {
  let ok = 0, rate_limited = 0, other_err = 0;
  const errors = [];
  for (let i = 0; i < BURN_N; i++) {
    try {
      await callLlm({
        apiKey: key,
        model: MODEL,
        systemPrompt: "You reply with 'ok'.",
        userPrompt: `ping ${i}`,
        temperature: 0,
        maxTokens: 4,
      });
      ok++;
    } catch (e) {
      const msg = String(e.message ?? e);
      if (msg.includes("429")) {
        rate_limited++;
      } else {
        other_err++;
        if (errors.length < 3) errors.push(msg.slice(0, 120));
      }
    }
  }
  return { label, ok, rate_limited, other_err, errors };
}

console.log(`[quota-verify] model=${MODEL} burn_n=${BURN_N}`);
console.log(`[quota-verify] Burning ${BURN_N} calls on Key A (live)...`);
const a = await burn(KEY_A, "A");
console.log(`[quota-verify] Key A: ${JSON.stringify(a)}`);

console.log(`[quota-verify] Probing ${BURN_N} calls on Key B (standin) immediately after...`);
const b = await burn(KEY_B, "B");
console.log(`[quota-verify] Key B: ${JSON.stringify(b)}`);

// Verdict taxonomy:
//   ISOLATED  — Key B got OKs while Key A's quota was drained (b.ok > 0 OR
//               b had distinctly fewer 429s than a).
//   SHARED    — Both keys 429'd on free-tier daily quota; account-level pool.
//   INDETERMINATE — Model returned 4xx/5xx other than 429 (e.g. 404 endpoint
//               unavailable). Re-run after picking a known-live :free model.
let verdict;
if (a.rate_limited === 0 && b.rate_limited === 0) {
  verdict = "INDETERMINATE";
} else if (b.ok > 0) {
  verdict = "ISOLATED";
} else {
  verdict = "SHARED";
}

const out = `# OpenRouter \`:free\` quota isolation verdict — ${new Date().toISOString()}

**Model probed:** \`${MODEL}\`
**Burn N (per key):** ${BURN_N}

## Key A — \`OPENROUTER_API_KEY\` (live grader + sidekick)
- ok: ${a.ok}
- rate_limited (429): ${a.rate_limited}
- other_err: ${a.other_err}
${a.errors.length ? "- first errors: " + JSON.stringify(a.errors) : ""}

## Key B — \`OPENROUTER_API_KEY_STANDIN\` (proposed standin key)
- ok: ${b.ok}
- rate_limited (429): ${b.rate_limited}
- other_err: ${b.other_err}
${b.errors.length ? "- first errors: " + JSON.stringify(b.errors) : ""}

## VERDICT: ${verdict}

${verdict === "ISOLATED"
  ? "Separate keys on the same OpenRouter account have INDEPENDENT per-key daily quotas. Surface Y MAY run during study hours subject to per-key rate limits. Surface X advisory deploy-gate, Surface Z screenshot sweeps, and Surface Y stand-in runs are all permitted with the standin key."
  : verdict === "SHARED"
  ? "Separate keys SHARE account-level daily quota on the `:free` tier. Surface Y MUST be manual-only and MUST run only OUTSIDE Alex's study window (recommend 03:00-06:00 local). The standin key burning quota WILL drain the live grader+sidekick's available calls. Treat all automation as gated on a daily refill window."
  : "Probe inconclusive. No 429s on either key — model likely returned 404 or other non-rate-limit error. Pick a different STANDIN_PROBE_MODEL (must be a live `:free` endpoint) and re-run before relying on this verdict."}

## Reproduction

\`\`\`bash
OPENROUTER_API_KEY=<live> OPENROUTER_API_KEY_STANDIN=<standin> \\
  node tools/verify-openrouter-quota-isolation.mjs
\`\`\`

Re-run quarterly OR when OpenRouter announces account/quota structure changes. Verdict is point-in-time; the live VPS state at run time is the authoritative answer.
`;

const outPath = resolve(REPO_ROOT, "docs/notes/2026-05-13-openrouter-quota-isolation.md");
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, out);
console.log(`\n[quota-verify] Verdict: ${verdict}`);
console.log(`[quota-verify] Written: ${outPath}`);
process.exit(0);
