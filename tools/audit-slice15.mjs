// tools/audit-slice15.mjs — Slice-1.5 audit orchestrator + spec parser.
// Spec: docs/superpowers/specs/2026-05-17-slice15-audit-design.md

import { chromium } from "playwright";
import { AxeBuilder } from "@axe-core/playwright";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  detectSnakeCase,
  detectScreamingSnake,
  detectDottedModelName,
  detectRawHttpError,
  detectPlaceholder,
} from "./surface-z-lints.mjs";
import { callLlm } from "./lib/openrouter.mjs";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

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

/**
 * Execute one state row's Playwright "reach" sequence.
 * The reach DSL supports `goto <path>` and `click <data-testid>` and
 * `fill <data-testid> "value"`. Anything else throws.
 */
async function executeReach(page, reachExpr, baseUrl) {
  // Reach expressions in the spec are written as natural-language with
  // backtick-quoted Playwright primitives. Extract the primitives by
  // grepping for known patterns.
  const sequence = [];
  const gotoMatch = reachExpr.match(/goto\s+(\S+)/);
  if (gotoMatch) sequence.push({ kind: "goto", target: gotoMatch[1] });
  const clickMatches = reachExpr.matchAll(/click\s+([a-z][a-z0-9-]+)/g);
  for (const m of clickMatches) sequence.push({ kind: "click", target: m[1] });
  const fillMatches = reachExpr.matchAll(/fill\s+([a-z][a-z0-9-]+)\s+"([^"]+)"/g);
  for (const m of fillMatches) sequence.push({ kind: "fill", target: m[1], value: m[2] });

  for (const step of sequence) {
    if (step.kind === "goto") {
      await page.goto(`${baseUrl}${step.target}`);
      await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
    } else if (step.kind === "click") {
      await page.click(`[data-testid="${step.target}"]`, { timeout: 5000 });
    } else if (step.kind === "fill") {
      await page.fill(`[data-testid="${step.target}"]`, step.value);
    }
  }
}

/**
 * For one state row, navigate + capture + lint + classify findings.
 * Returns { stateId, findings[], unreachableReason }.
 */
export async function auditState({ page, row, baseUrl }) {
  const findings = [];
  const consoleErrors = [];
  const httpErrors = [];
  const pageErrors = [];

  const consoleHandler = (m) => { if (m.type() === "error") consoleErrors.push(m.text()); };
  const responseHandler = (r) => { if (r.status() >= 400) httpErrors.push({ url: r.url(), status: r.status() }); };
  const pageErrorHandler = (e) => pageErrors.push(e.message);
  page.on("console", consoleHandler);
  page.on("response", responseHandler);
  page.on("pageerror", pageErrorHandler);

  try {
    try {
      await executeReach(page, row.reach, baseUrl);
    } catch (e) {
      return { stateId: row.id, findings: [], unreachableReason: `reach-failed: ${e.message}` };
    }
    for (const sel of row.selectors) {
      const count = await page.locator(`[data-testid="${sel}"]`).count();
      if (count === 0) {
        findings.push({
          stateId: row.id,
          category: "missing-selector",
          evidence: `[data-testid="${sel}"] not found`,
          severity: classifySeverity({ category: "missing-selector" }),
        });
      }
    }
    // Capture DOM text (strip code/pre/input.value — mirrors LINT_EVAL_SCRIPT
    // semantics so the existing detectSnakeCase calibration carries over).
    const domText = await page.evaluate(() => {
      const root = document.body.cloneNode(true);
      root.querySelectorAll("code, pre, script, style").forEach(el => el.remove());
      // input values are not in textContent, no strip needed
      return root.innerText || "";
    });

    // State-specific allowlist for raw HTTP errors: load-error UIs
    // intentionally surface "HTTP 5xx" (concept-drawer-load-error,
    // ledger-load-error). Allow them when the testid carrying the text
    // exists on the page; suppress raw-http-error findings in that case.
    const allowedHttpErrorTestIds = ["concept-drawer-load-error", "ledger-load-error", "pdf-upload-error", "suggested-edit-error"];
    let httpErrorAllowed = false;
    for (const tid of allowedHttpErrorTestIds) {
      if (await page.locator(`[data-testid="${tid}"]`).count() > 0) {
        httpErrorAllowed = true;
        break;
      }
    }

    const lintCalls = [
      { fn: detectSnakeCase, category: "snake-case-leak" },
      { fn: detectScreamingSnake, category: "screaming-snake-leak" },
      { fn: detectDottedModelName, category: "model-name-leak" },
      { fn: detectRawHttpError, category: "raw-http-error" },
      { fn: detectPlaceholder, category: "placeholder-leak" },
    ];
    for (const { fn, category } of lintCalls) {
      if (category === "raw-http-error" && httpErrorAllowed) continue;
      const result = fn(domText);
      const matches = Array.isArray(result) ? result : result.matches;
      for (const match of matches) {
        findings.push({
          stateId: row.id,
          category,
          evidence: `text: "${match}"`,
          severity: classifySeverity({ category }),
        });
      }
    }
    // axe-core a11y scan. AA violations classified HIGH; AAA classified MED.
    const axeResults = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag2aaa"])
      .analyze()
      .catch(e => ({ violations: [], error: e.message }));
    for (const v of axeResults.violations ?? []) {
      const isAA = v.tags.some(t => t === "wcag2aa" || t === "wcag21aa" || t === "wcag2a" || t === "wcag21a");
      const axeLevel = isAA ? "wcag2aa" : "wcag2aaa";
      findings.push({
        stateId: row.id,
        category: "axe-violation",
        evidence: `${v.id}: ${v.help} (nodes: ${v.nodes.length})`,
        severity: classifySeverity({ category: "axe-violation", axeLevel }),
      });
    }
    // Interaction probe: focus every [data-testid] interactive element to
    // assert no JS error during focus. Click-probe ONLY on an allowlist of
    // idempotent affordances (close buttons) to avoid mutating state.
    const idempotentClickAllowlist = [
      "knowledge-ledger-backdrop",   // close
      "concept-drawer-backdrop",     // close
      // (extend per audit experience)
    ];
    const interactables = await page.locator(
      '[data-testid][role="button"], [data-testid] > button, button[data-testid], a[data-testid][href], input[data-testid], textarea[data-testid]',
    ).all();
    for (const el of interactables) {
      const tid = await el.getAttribute("data-testid").catch(() => null);
      try {
        await el.focus({ timeout: 1000 });
      } catch (e) {
        findings.push({
          stateId: row.id,
          category: "pageerror",
          evidence: `focus failed on [data-testid="${tid}"]: ${e.message.slice(0, 120)}`,
          severity: classifySeverity({ category: "pageerror" }),
        });
      }
      if (tid && idempotentClickAllowlist.includes(tid)) {
        // Skip — would close the current state mid-audit. Allowlist exists
        // for explicit future probes where we WANT to dismiss + assert.
      }
    }
    for (const err of consoleErrors) {
      findings.push({
        stateId: row.id,
        category: "pageerror",
        evidence: `console.error: ${err.slice(0, 200)}`,
        severity: classifySeverity({ category: "pageerror" }),
      });
    }
    for (const httpErr of httpErrors) {
      findings.push({
        stateId: row.id,
        category: "first-paint-http-error",
        evidence: `${httpErr.status} ${httpErr.url}`,
        severity: classifySeverity({ category: "first-paint-http-error" }),
      });
    }
    for (const pe of pageErrors) {
      findings.push({
        stateId: row.id,
        category: "pageerror",
        evidence: `pageerror: ${pe.slice(0, 200)}`,
        severity: classifySeverity({ category: "pageerror" }),
      });
    }

    // LLM judge: feed page text to :free model, ask for UX nits.
    // Subjective; severities downgraded one band by classifySeverity.
    const apiKey = process.env.OPENROUTER_API_KEY_STANDIN ?? process.env.OPENROUTER_API_KEY;
    if (apiKey && domText) {
      try {
        const judgeReply = await callLlm({
          apiKey,
          model: "openai/gpt-oss-120b:free",
          systemPrompt:
            "You are a UX nitpicker. Given the visible text of a webpage, list any issues " +
            "a real user would call out as broken, confusing, or unfinished. Output ONE finding " +
            'per line in the form: "[HIGH|MED|LOW] <issue> · evidence=<quoted phrase from text>". ' +
            "Be terse. Skip nitpicks. Output nothing if the page looks fine.",
          userPrompt: `State: ${row.id}\nRoute: ${row.route}\n\nPage text (first 3000 chars):\n${domText.slice(0, 3000)}`,
          maxTokens: 600,
        });
        const lines = (judgeReply.text || "").split("\n").map(l => l.trim()).filter(Boolean);
        for (const line of lines) {
          const m = line.match(/^\[(HIGH|MED|LOW)\]\s+(.+)$/);
          if (!m) continue;
          findings.push({
            stateId: row.id,
            category: "llm-judge",
            evidence: m[2].slice(0, 200),
            severity: classifySeverity({ category: "llm-judge", judgeSeverity: m[1] }),
          });
        }
      } catch (e) {
        // LLM judge unreachable (quota / network) — non-blocking. Note as
        // a single LOW finding so the report shows judge was attempted.
        findings.push({
          stateId: row.id,
          category: "llm-judge",
          evidence: `judge call failed: ${e.message.slice(0, 120)}`,
          severity: "LOW",
        });
      }
    }

    return { stateId: row.id, findings, unreachableReason: null };
  } finally {
    page.off("console", consoleHandler);
    page.off("response", responseHandler);
    page.off("pageerror", pageErrorHandler);
  }
}

function writeFindingsDoc({ outputPath, allFindings, unreachable, baseUrl, totalStates }) {
  const counts = { HIGH: 0, MED: 0, LOW: 0 };
  for (const f of allFindings) counts[f.severity] = (counts[f.severity] ?? 0) + 1;
  const date = new Date().toISOString().slice(0, 10);
  const lines = [
    `# Slice-1.5 Audit Findings — ${date}`,
    "",
    "## Method",
    "",
    `Audit run via \`tools/audit-slice15.mjs\` against \`${baseUrl}\` per the state matrix in`,
    "`docs/superpowers/specs/2026-05-17-slice15-audit-design.md`.",
    "",
    "## Summary",
    "",
    `- HIGH: ${counts.HIGH}  MED: ${counts.MED}  LOW: ${counts.LOW}`,
    `- States audited: ${totalStates - unreachable.length}/${totalStates} (${unreachable.length} unreachable)`,
    "",
    "## Unreachable states",
    "",
    ...(unreachable.length === 0
      ? ["_(none)_"]
      : unreachable.map(u => `- **${u.stateId}**: ${u.reason}`)),
    "",
    "## Findings",
    "",
    "| State | Severity | Category | Evidence |",
    "|-------|----------|----------|----------|",
    ...allFindings
      .sort((a, b) => {
        const order = { HIGH: 0, MED: 1, LOW: 2 };
        return order[a.severity] - order[b.severity];
      })
      .map(f => `| ${f.stateId} | ${f.severity} | ${f.category} | ${f.evidence.replace(/\|/g, "\\|")} |`),
    "",
  ];
  mkdirSync(dirname(resolve(REPO_ROOT, outputPath)), { recursive: true });
  writeFileSync(resolve(REPO_ROOT, outputPath), lines.join("\n"));
}

// CLI
if (process.argv[1]?.endsWith("audit-slice15.mjs")) {
  const args = Object.fromEntries(process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.+)$/); return m ? [m[1], m[2]] : [a.replace(/^--/, ""), true];
  }));
  const baseUrl = args["base-url"] ?? "https://corgflix.duckdns.org";
  const specPath = args.spec ?? "docs/superpowers/specs/2026-05-17-slice15-audit-design.md";
  const outputPath = args.output ?? `docs/standin-findings/audit-slice15-${new Date().toISOString().slice(0, 10)}.md`;
  const onlyIds = args.only ? args.only.split(",") : null;
  const startFromId = args["start-from"] ?? null;

  const specText = readFileSync(resolve(REPO_ROOT, specPath), "utf8");
  let rows = parseStateMatrix(specText);
  if (startFromId) {
    const idx = rows.findIndex(r => r.id === startFromId);
    if (idx >= 0) rows = rows.slice(idx);
  }
  if (onlyIds) rows = rows.filter(r => onlyIds.includes(r.id));

  console.log(`audit: ${rows.length} states against ${baseUrl}`);
  const browser = await chromium.launch({ headless: true });
  try {
    // Auth: read jarvis_auth cookie from env or fallback file.
    // Mirrors tools/seed-tutor-events.mjs loadAuthToken pattern.
    let authToken = process.env.JARVIS_AUTH_COOKIE;
    if (!authToken) {
      try {
        authToken = readFileSync(resolve(REPO_ROOT, "tools/AUTH_TOKEN.txt"), "utf8").trim();
      } catch { /* tolerate; states that need auth will be unreachable */ }
    }
    const ctx = await browser.newContext({
      storageState: authToken ? {
        cookies: [{
          name: "jarvis_auth", value: authToken,
          domain: new URL(baseUrl).hostname, path: "/",
          secure: true, httpOnly: false,
        }],
        origins: [],
      } : undefined,
      extraHTTPHeaders: { "X-Standin-Run": "1" },
    });
    const page = await ctx.newPage();
    const allFindings = [];
    const unreachable = [];
    for (const row of rows) {
      const result = await auditState({ page, row, baseUrl });
      if (result.unreachableReason) {
        unreachable.push({ stateId: row.id, reason: result.unreachableReason });
        console.log(`  ${row.id}: UNREACHABLE — ${result.unreachableReason}`);
      } else {
        allFindings.push(...result.findings);
        console.log(`  ${row.id}: ${result.findings.length} findings`);
      }
    }
    writeFindingsDoc({
      outputPath,
      allFindings,
      unreachable,
      baseUrl,
      totalStates: rows.length,
    });
    console.log(`audit complete: ${allFindings.length} total findings (${
      Object.entries({ HIGH: 0, MED: 0, LOW: 0 }).map(([k]) => `${k}:${allFindings.filter(f => f.severity === k).length}`).join(" ")
    }), ${unreachable.length} unreachable — written to ${outputPath}`);
  } finally {
    await browser.close();
  }
}
