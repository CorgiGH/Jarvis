#!/usr/bin/env node
/**
 * Slice 1.5 visual acceptance gate.
 * Runs Playwright headless against the live URL.
 * Asserts the seven `data-testid` selectors from the spec are visible.
 *
 * Usage:
 *   node tools/slice1-5-playwright-gate.mjs <url> [auth-cookie]
 *
 *   url           — e.g. https://corgflix.duckdns.org/tutor/?taskId=<REAL>
 *   auth-cookie   — optional, value of `jarvis_session` cookie if URL is auth-gated
 *
 * Exit code: 0 if all 7 selectors paint within 30s; 1 otherwise.
 *
 * Required: playwright installed in node_modules (`npm i -D playwright` in
 * tutor-web or repo root).
 */
import { chromium } from "playwright";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SELECTORS = [
  { sel: '[data-testid="problem-stepper"]', label: "ProblemStepper" },
  { sel: '[data-testid="progress-strip"]', label: "ProgressStrip" },
  { sel: '[data-testid="drill-stack"]', label: "DrillStack" },
  { sel: '[data-testid="drill-card"][data-state="open"]', label: "DrillCard (open)" },
  { sel: '[data-testid="drill-card"][data-state="locked"]', label: "DrillCard (locked)" },
  { sel: '[data-testid="resource-rail"]', label: "ResourceRail" },
  { sel: '[data-testid="sidekick-panel"]', label: "Sidekick" },
];

async function main() {
  const url = process.argv[2];
  const cookie = process.argv[3] || null;
  if (!url) {
    console.error("usage: node tools/slice1-5-playwright-gate.mjs <url> [auth-cookie]");
    process.exit(2);
  }
  console.log(`[gate] navigating to ${url}`);
  const browser = await chromium.launch();
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  if (cookie) {
    const u = new URL(url);
    await context.addCookies([{
      name: "jarvis_session", value: cookie,
      domain: u.hostname, path: "/", httpOnly: true, secure: u.protocol === "https:",
    }]);
  }
  const page = await context.newPage();
  page.on("console", msg => console.log(`[browser ${msg.type()}]`, msg.text()));
  page.on("pageerror", err => console.error(`[browser error]`, err.message));
  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });

  let fail = 0;
  for (const { sel, label } of SELECTORS) {
    try {
      await page.waitForSelector(sel, { state: "visible", timeout: 30000 });
      console.log(`[gate] ✅ ${label} (${sel})`);
    } catch (e) {
      console.error(`[gate] ❌ ${label} (${sel}) — not visible within 30s`);
      fail++;
    }
  }
  await page.screenshot({ path: path.join(__dirname, "..", "slice1-5-gate-evidence.png"), fullPage: true });
  console.log(`[gate] screenshot → slice1-5-gate-evidence.png`);
  await browser.close();
  if (fail > 0) {
    console.error(`[gate] FAIL — ${fail} selector(s) missing`);
    process.exit(1);
  }
  console.log(`[gate] ALL 7 SELECTORS PASS`);
  process.exit(0);
}
main().catch(err => { console.error(err); process.exit(1); });
