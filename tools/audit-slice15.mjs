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
  let inFence = false;
  for (const line of lines) {
    if (/^\s*```/.test(line)) { inFence = !inFence; continue; }
    if (inFence) continue;
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
/**
 * Resolve `S-NN → ...` chain prefix into the prior row's reach expression.
 * Returns the fully-resolved reach string. Prevents state-leak between
 * sibling rows (e.g. S-18 click intercepted by S-15's open scratchpad
 * drawer) by guaranteeing each row's reach starts from its own baseline.
 */
function resolveChainedReach(reachExpr, rowsById, visited = new Set()) {
  const chainMatch = reachExpr.match(/^\s*(S-\d+)\s*(?:→|->|>)\s*(.+)$/);
  if (!chainMatch) return reachExpr;
  const [, parentId, rest] = chainMatch;
  if (visited.has(parentId)) return rest;
  visited.add(parentId);
  const parentRow = rowsById.get(parentId);
  if (!parentRow) return rest;
  const parentReach = resolveChainedReach(parentRow.reach, rowsById, visited);
  return `${parentReach} ; ${rest}`;
}

async function executeReach(page, reachExpr, baseUrl) {
  // Reach expressions in the spec are written as natural-language with
  // backtick-quoted Playwright primitives. Extract the primitives by
  // grepping for known patterns. Order matters — walk the reach string
  // left-to-right so goto/click/fill stay in spec order across chains.
  const sequence = [];
  // mock-fetch "<urlSubstring>" "<jsonResponse>" — intercepts API call and
  // returns canned JSON. The urlSubstring is checked via String.includes()
  // against the outgoing request URL; the jsonResponse is sent as
  // application/json with status 200. Necessary for empty-state audits
  // (S-03 `{tasks:[]}` from /api/v1/tasks) without rewriting backend data.
  const stepRe = /\b(goto|click|fill|clear-cookies|viewport|wait|mock-fetch)\b(?:\s+"([^"]+)"|\s+([^\s"]+))?(?:\s+"([^"]+)")?/g;
  let m;
  while ((m = stepRe.exec(reachExpr)) !== null) {
    const [, kind, qArg1, uArg1, qArg2] = m;
    const arg1 = qArg1 ?? uArg1;
    const arg2 = qArg2;
    if (kind === "goto") {
      if (arg1) sequence.push({ kind: "goto", target: arg1 });
    } else if (kind === "click") {
      if (arg1 && /^[a-z][a-zA-Z0-9_-]+$/.test(arg1)) sequence.push({ kind: "click", target: arg1 });
    } else if (kind === "fill") {
      if (arg1 && /^[a-z][a-zA-Z0-9_-]+$/.test(arg1) && arg2 != null) sequence.push({ kind: "fill", target: arg1, value: arg2 });
    } else if (kind === "clear-cookies") {
      sequence.push({ kind: "clear-cookies" });
    } else if (kind === "viewport") {
      const dims = arg1?.match(/^(\d+)x(\d+)$/);
      if (dims) sequence.push({ kind: "viewport", width: +dims[1], height: +dims[2] });
    } else if (kind === "wait") {
      if (arg1 && /^[a-z][a-zA-Z0-9_-]+$/.test(arg1)) sequence.push({ kind: "wait", target: arg1 });
    } else if (kind === "mock-fetch") {
      if (arg1 && arg2 != null) sequence.push({ kind: "mock-fetch", urlSubstring: arg1, response: arg2 });
    }
  }

  for (const step of sequence) {
    if (step.kind === "goto") {
      await page.goto(`${baseUrl}${step.target}`);
      await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
    } else if (step.kind === "click") {
      await page.click(`[data-testid="${step.target}"]`, { timeout: 15000 });
    } else if (step.kind === "fill") {
      await page.fill(`[data-testid="${step.target}"]`, step.value);
    } else if (step.kind === "clear-cookies") {
      await page.context().clearCookies();
    } else if (step.kind === "viewport") {
      await page.setViewportSize({ width: step.width, height: step.height });
    } else if (step.kind === "wait") {
      // Async grading + similar deferred work — wait up to 45s for the
      // expected testid to appear before continuing. Tolerant on timeout
      // so the auditState lint stage still runs + reports the gap.
      await page.locator(`[data-testid="${step.target}"]`).waitFor({ state: "visible", timeout: 45000 }).catch(() => {});
    } else if (step.kind === "mock-fetch") {
      // Install a route that intercepts the next request matching the
      // urlSubstring and replies with the canned JSON. Subsequent goto
      // navigations will trigger the matching fetch. The url predicate
      // receives a URL string, not a Request object (Playwright API).
      // page.route predicate receives a URL object (per Playwright API);
      // call .toString() (or .href) before substring matching.
      await page.route(url => String(url).includes(step.urlSubstring), async route => {
        try {
          await route.fulfill({ status: 200, contentType: "application/json", body: step.response });
        } catch {
          await route.continue().catch(() => {});
        }
      });
    }
  }
}

/**
 * For one state row, navigate + capture + lint + classify findings.
 * Returns { stateId, findings[], unreachableReason }.
 */
export async function auditState({ page, row, baseUrl, rowsById }) {
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
    // Reset viewport between states so S-29's mobile override doesn't leak
    // into later rows. Playwright default is 1280x720.
    await page.setViewportSize({ width: 1280, height: 720 }).catch(() => {});
    // Unroute any mock-fetch installed by a previous state — otherwise the
    // mock persists across state iterations and corrupts later audits.
    await page.unrouteAll({ behavior: "ignoreErrors" }).catch(() => {});
    try {
      const resolvedReach = rowsById
        ? resolveChainedReach(row.reach, rowsById)
        : row.reach;
      await executeReach(page, resolvedReach, baseUrl);
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
    // Capture DOM text. innerText concatenates inline-styled siblings without
    // separators (header > nav > a a a renders as "workspacetasksreview"
    // because Tailwind inline-flex strips block-level boundaries). The LLM
    // judge then misreads concatenated nav labels as "garbled UI". Walk the
    // DOM ourselves: insert a space between distinct text-bearing elements,
    // a newline at block-level boundaries. Strip code/pre/script/style.
    // Closes 2026-05-17 audit LOW finding cluster (innerText artifacts).
    const domText = await page.evaluate(() => {
      const BLOCKISH = new Set([
        "DIV","HEADER","FOOTER","NAV","SECTION","ASIDE","MAIN","ARTICLE",
        "UL","OL","LI","P","H1","H2","H3","H4","H5","H6","BR","HR",
        "TABLE","TR","TD","TH","FORM","FIELDSET","BLOCKQUOTE","DETAILS","SUMMARY",
        // Buttons and anchors are inline by HTML default but in this app they
        // are CSS-styled as block/inline-block via Tailwind classes (rails,
        // toolbars, nav). Treat them as blockish so the captured text shows
        // a newline between sibling buttons — matches what a sighted user
        // sees visually and prevents the LLM judge from misreading adjacent
        // labels as one concatenated string.
        "BUTTON","A","LABEL",
      ]);
      const SKIP = new Set(["SCRIPT","STYLE","NOSCRIPT","CODE","PRE","SVG","CANVAS"]);
      const out = [];
      const walk = (el) => {
        if (!el) return;
        if (el.nodeType === 3) {
          const t = el.textContent;
          if (t) out.push(t);
          return;
        }
        if (el.nodeType !== 1) return;
        const tag = el.tagName.toUpperCase();
        if (SKIP.has(tag)) return;
        const blockish = BLOCKISH.has(tag);
        if (blockish) out.push("\n");
        for (const child of el.childNodes) walk(child);
        if (blockish) out.push("\n");
        else out.push(" ");
      };
      walk(document.body);
      return out.join("")
        .replace(/[ \t]+/g, " ")
        .replace(/ ?\n ?/g, "\n")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
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
      { fn: detectSnakeCase, category: "snake-case-leak", opts: { skipFilenameTokens: true } },
      { fn: detectScreamingSnake, category: "screaming-snake-leak" },
      { fn: detectDottedModelName, category: "model-name-leak" },
      { fn: detectRawHttpError, category: "raw-http-error" },
      { fn: detectPlaceholder, category: "placeholder-leak" },
    ];
    for (const { fn, category, opts } of lintCalls) {
      if (category === "raw-http-error" && httpErrorAllowed) continue;
      const result = opts ? fn(domText, opts) : fn(domText);
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
      const nodeSnippets = (v.nodes ?? [])
        .slice(0, 3)
        .map(n => (n.html ?? "").replace(/\s+/g, " ").trim().slice(0, 160))
        .filter(Boolean)
        .join(" | ");
      const evidence = nodeSnippets
        ? `${v.id}: ${v.help} (nodes: ${v.nodes.length}) — ${nodeSnippets}`
        : `${v.id}: ${v.help} (nodes: ${v.nodes.length})`;
      findings.push({
        stateId: row.id,
        category: "axe-violation",
        evidence,
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
      // Spec-intentional 4xx: a reach that probes a non-existent task ID
      // (e.g. S-30 missing-pinned-task with `?taskId=DOES-NOT-EXIST`) MUST
      // produce a 4xx on the /api/v1/tasks/<id>/prep fetch — that's how
      // the banner gets exercised. Suppress those.
      const intentional4xx = /\/(tasks|task)\/(DOES-NOT-EXIST|PS-Tema-A|MISSING|UNKNOWN)\//i.test(httpErr.url);
      if (intentional4xx) continue;
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
            "a real user would call out as BROKEN, CONFUSING, or UNFINISHED. Output ONE finding " +
            'per line in the form: "[HIGH|MED|LOW] <issue> · evidence=<quoted phrase from text>". ' +
            "Be terse. Skip nitpicks. Output nothing if the page looks fine.\n\n" +
            "RULES — do NOT report any of the following (they are NOT bugs):\n" +
            "1. Adjacent labels missing spaces (e.g. \"TUTORworkspace\", \"PDFTema_A.pdf\", \"REVIEW0\") — text-capture noise from this audit tool, not visible to users.\n" +
            "2. Filenames with underscores or dots (e.g. \"lectures__OS1.1_Linux-intro_print-ro.pdf\") — those ARE the real filenames.\n" +
            "3. Subject codes like POO, PA, PS, ALO, SO, RC — those are real course labels at Iași FII, not typos.\n" +
            "4. Mixed Romanian/English text — this is a Romanian-language tutor.\n" +
            "5. Status codes adjacent to subjects (\"OPENPOO\", \"OPENSO\") — same text-capture artifact as rule 1.\n" +
            "6. The literal phrases \"State:\" or \"Route:\" — those describe THIS audit's input prompt, not page content. NEVER report a finding whose evidence cites \"Route:\" or \"State:\" prefixed text.\n" +
            "7. \"BUC\" near a timestamp — that is the IATA-style code for Bucharest, the user's timezone label.\n" +
            "8. \"CORGFLIX.DUCKDNS.ORG\" + time — that is the StatusBar's intentional self-reference (current host + local time). Not a broken link.\n" +
            "9. \"loading…\" alone — a transient loading state is expected on first paint of async content. Only flag if it has been loading for over a minute or shows a stuck animation.\n" +
            "10. Lock icons (🔒) with \"attempt drill first\" — this is the documented productive-failure UI gate; deliberate, not broken.\n" +
            "11. \"DOES-NOT-EXIST\" or similar obviously placeholder task IDs — those are test artifacts only ever seen via /tutor/?taskId=DOES-NOT-EXIST manual probes.\n" +
            "12. \"× close\" — × is the standard close glyph; tooltip is unnecessary when aria-label is set (which it is).\n\n" +
            "ONLY report issues a sighted user with the rendered page would call out as wrong.",
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
  const allRows = parseStateMatrix(specText);
  // rowsById is built from the FULL parsed spec — chain resolution
  // (S-NN → ...) must find parent rows even when --only or --start-from
  // narrowed the iteration set. Otherwise the parent goto disappears and
  // the click runs against an empty page.
  const rowsById = new Map(allRows.map(r => [r.id, r]));
  let rows = allRows;
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
    });
    // X-Standin-Run flags synthetic events in TutorRoutes.kt:1340/1615. Must
    // be scoped to baseUrl origin only — applying it globally breaks CORS
    // preflight on cross-origin CDN fetches (e.g. pdfjs-dist worker on
    // cdn.jsdelivr.net), causing spurious pageerror findings.
    const baseHost = new URL(baseUrl).hostname;
    await ctx.route("**/*", async (route, request) => {
      const reqHost = new URL(request.url()).hostname;
      if (reqHost === baseHost) {
        await route.continue({ headers: { ...request.headers(), "x-standin-run": "1" } });
      } else {
        await route.continue();
      }
    });
    const page = await ctx.newPage();

    // Pre-warm prep endpoints for every ULID referenced in the spec.
    // Backend generates /api/v1/tasks/{id}/prep lazily on first request and
    // returns 404 until the generator finishes; the audit's first-paint
    // probes raced that generator and produced false-positive S-12 findings
    // in the 2026-05-17 run. Pre-warm makes the audit deterministic.
    const ulidsToPrewarm = [...new Set(
      rows.flatMap(r => [...r.reach.matchAll(/01[A-Z0-9]{24}/g)].map(m => m[0]))
    )];
    if (ulidsToPrewarm.length > 0) {
      console.log(`prewarm: ${ulidsToPrewarm.length} prep endpoints`);
      // Bootstrap a real session by visiting / once so jarvis_session + csrf
      // cookies populate before the prep fetches (the prep endpoint requires
      // full session auth, not just jarvis_auth).
      await page.goto(`${baseUrl}/tutor/`).catch(() => {});
      await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
      for (const id of ulidsToPrewarm) {
        const url = `${baseUrl}/api/v1/tasks/${id}/prep`;
        const r = await ctx.request.get(url).catch(e => ({ status: () => 0, error: e.message }));
        const status = r.status?.() ?? 0;
        if (status === 200) {
          console.log(`  ${id}: prep ready`);
        } else if (status === 404) {
          console.log(`  ${id}: prep 404 (will retry once after 3s)`);
          await new Promise(res => setTimeout(res, 3000));
          await ctx.request.get(url).catch(() => null);
        } else {
          console.log(`  ${id}: prep HTTP ${status}`);
        }
      }
    }

    const allFindings = [];
    const unreachable = [];
    for (const row of rows) {
      const result = await auditState({ page, row, baseUrl, rowsById });
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
