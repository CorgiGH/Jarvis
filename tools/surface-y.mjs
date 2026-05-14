// tools/surface-y.mjs
import { chromium } from "playwright";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { callLlm as defaultCallLlm } from "./lib/openrouter.mjs";
import { buildPersonaPrompt, updateLedger, sampleConfusionTuple } from "./surface-y-persona.mjs";
import { gateLoop } from "./surface-y-gate.mjs";
import { LINT_EVAL_SCRIPT } from "./surface-z-lints.mjs";
import { getStamp } from "./lib/provenance.mjs";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function parseYaml(text) {
  // Minimal YAML parser for the schema shape we use (flow style only).
  const schema = { concepts: [], confusion_tuples: [] };
  let inConcepts = false, inTuples = false;
  const scalar = (l, key) => l.slice(key.length).trim().replace(/^['"]|['"]$/g, "");
  for (const line of text.split("\n")) {
    if (line.startsWith("task_id:")) schema.task_id = scalar(line, "task_id:");
    else if (line.startsWith("subject:")) schema.subject = scalar(line, "subject:");
    else if (line.startsWith("title:")) schema.title = scalar(line, "title:");
    else if (line.startsWith("concepts:")) { inConcepts = true; inTuples = false; }
    else if (line.startsWith("confusion_tuples:")) { inConcepts = false; inTuples = true; }
    else if (inConcepts && /^\s+- /.test(line)) {
      const flow = line.replace(/^\s+- /, "").trim();
      const m = flow.match(/^\{([\s\S]+)\}$/);
      if (m) {
        const obj = {};
        for (const pair of m[1].split(/,\s*(?![^\[\]]*\])/)) {
          const [k, v] = pair.split(":").map(s => s.trim());
          if (v && v.startsWith("[")) obj[k] = v.replace(/[\[\]]/g, "").split(",").map(s => s.trim().replace(/^['"]|['"]$/g, ""));
          // Scalars stay strings — `generic: true` becomes the string "true",
          // which is truthy, matching every `if (c.generic)` consumer check.
          else obj[k] = v?.replace(/^['"]|['"]$/g, "");
        }
        schema.concepts.push(obj);
      }
    } else if (inTuples && /^\s+- /.test(line)) {
      const flow = line.replace(/^\s+- /, "").trim();
      const m = flow.match(/^\{([\s\S]+)\}$/);
      if (m) {
        const betweenMatch = m[1].match(/between:\s*\[([^\]]+)\]/);
        const whyMatch = m[1].match(/why:\s*['"]?([^'"}]+)['"]?/);
        if (betweenMatch) {
          schema.confusion_tuples.push({
            between: betweenMatch[1].split(",").map(s => s.trim().replace(/^['"]|['"]$/g, "")),
            why: whyMatch?.[1] ?? "",
          });
        }
      }
    }
  }
  return schema;
}

export async function runStandin({
  taskId,
  schemaPath,
  browser = null,
  callLlm = defaultCallLlm,
  // Standin key only — NO fallback to OPENROUTER_API_KEY (unlike Surface X/Z).
  // Surface Y can burn (maxRegens+1) * maxCallsPerSession calls in one run;
  // never let that land on the production key by accident.
  apiKey = process.env.OPENROUTER_API_KEY_STANDIN,
  model = "openai/gpt-oss-120b:free",
  maxCallsPerSession = 50,
  maxDurationMin = 10,
  maxRegens = 2,
  outputDir = "docs/standin-findings",
  screenshotDir = "docs/standin-findings/screenshots",
  sessionId = `y-${Date.now()}`,
  baseUrl = "https://corgflix.duckdns.org",
  authCookie = process.env.JARVIS_AUTH_COOKIE,
  piggybackZ = true,
}) {
  process.env.SURFACE_VERSION = "y-v1.0";
  if (!apiKey && callLlm === defaultCallLlm) throw new Error("runStandin: OPENROUTER_API_KEY_STANDIN required");

  const schema = parseYaml(readFileSync(schemaPath, "utf8"));
  const ownsBrowser = !browser;
  if (!browser) browser = await chromium.launch({ headless: true });

  let ctx;
  try {
    ctx = await browser.newContext({
      storageState: authCookie ? {
        cookies: [{ name: "jarvis_auth", value: authCookie, domain: new URL(baseUrl).hostname, path: "/", secure: true, httpOnly: false }],
        origins: [],
      } : undefined,
      extraHTTPHeaders: { "X-Standin-Run": "1" },
    });
    const page = await ctx.newPage();
    const t0 = Date.now();
    let ledger = new Set();
    // Fixed seed 0 → always confusion_tuples[0] for the whole session. V1 choice;
    // Task 3.5 findings reflect only that one tuple being active.
    const activeConfusion = sampleConfusionTuple(schema, 0);
    const transcript = [];
    const gateViolations = [];
    let callsUsed = 0;
    let lastJudgeModel = null, lastPromptSha = null;
    const zPiggyFindings = [];

    if (piggybackZ) mkdirSync(screenshotDir, { recursive: true });

    await page.goto(`${baseUrl}/tutor/?taskId=${taskId}`);
    await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});

    while (callsUsed < maxCallsPerSession && Date.now() - t0 < maxDurationMin * 60_000) {
      const bodyText = await page.evaluate("document.body.innerText").catch(() => "");
      // Persona is otherwise blind to inputs/buttons — innerText flattens them away.
      // Give it an explicit interactive-element list so it can choose real affordances.
      const affordances = await page.evaluate(() => {
        // Build a UNIQUE, copy-pasteable selector per element — the page has
        // multiple <textarea>/<button>, so a bare tag selector is ambiguous and
        // page.fill/click would throw strict-mode. Prefer #id, then a stable
        // attribute, else tag:nth-of-type(N).
        const sel = (el) => {
          if (el.id) return `#${CSS.escape(el.id)}`;
          const tag = el.tagName.toLowerCase();
          const ph = el.getAttribute("placeholder");
          if (ph) return `${tag}[placeholder="${ph.replace(/"/g, '\\"')}"]`;
          const al = el.getAttribute("aria-label");
          if (al) return `${tag}[aria-label="${al.replace(/"/g, '\\"')}"]`;
          const sibs = [...document.querySelectorAll(tag)];
          return `${tag}:nth-of-type(${sibs.indexOf(el) + 1})`;
        };
        const clean = (s) => (s || "").replace(/\s+/g, " ").trim();
        return [...document.querySelectorAll('button, a[href], input, textarea, select, [role="button"], [contenteditable="true"]')]
          .map((el) => {
            const tag = el.tagName.toLowerCase();
            const isField = tag === "input" || tag === "textarea" || tag === "select" || el.getAttribute("contenteditable") === "true";
            const disabled = (el.disabled || el.getAttribute("aria-disabled") === "true") ? " [disabled]" : "";
            if (isField) {
              // A FIELD you type into. The placeholder is a HINT, not content —
              // do NOT echo it back. Show current value separately so the persona
              // has feedback on its own typing (innerText omits input values).
              const aria = clean(el.getAttribute("aria-label"));
              const ph = clean(el.getAttribute("placeholder"));
              const desc = aria
                ? `"${aria.slice(0, 70)}"`
                : ph ? `placeholder hint (do NOT type this back): "${ph.slice(0, 70)}"` : `${tag} field`;
              const raw = typeof el.value === "string" ? clean(el.value) : "";
              const state = raw ? `[current value: "${raw.slice(0, 60)}"]` : "[EMPTY — type your OWN content here]";
              return `${sel(el)} — ${desc} ${state}${disabled}`;
            }
            // A BUTTON/LINK — its visible text IS the label.
            const label = clean(el.getAttribute("aria-label") || el.textContent || el.getAttribute("title")).slice(0, 70);
            return `${sel(el)} — "${label}"${disabled}`;
          })
          .slice(0, 40)
          .join("\n");
      }).catch(() => "");
      ledger = updateLedger(ledger, schema, bodyText);
      const prompt = buildPersonaPrompt({
        schema, ledger,
        sessionHistory: transcript.slice(-5),
        activeConfusionTuple: activeConfusion,
        currentDom: `INTERACTIVE ELEMENTS — format is "<css-selector> — <label>". To click/type, set "target" to the css-selector EXACTLY (the part before the —), not the label:\n${affordances}\n\nVISIBLE TEXT:\n${bodyText}`,
      });

      let r;
      try {
        r = await gateLoop({
          initialUserPrompt: "Decide next action.",
          systemPrompt: prompt,
          schema, ledger,
          callLlm, apiKey, model,
          maxRegens,
        });
      } catch (e) {
        // LLM unreachable — most likely a daily-quota 429 (openrouter.mjs does
        // NOT retry those). Record it and stop gracefully so the partial
        // finding doc is still written instead of the whole run rejecting.
        transcript.push({ action: "error", target: "", observation: `llm_error: ${String(e).slice(0, 150)}`, ts: new Date().toISOString() });
        break;
      }
      // Soft cap: checked before each iteration, but gateLoop can spend up to
      // maxRegens+1 calls, so callsUsed may overshoot maxCallsPerSession by ≤maxRegens.
      callsUsed += r.tries.length;
      if (r.leaked) gateViolations.push({ step: transcript.length, violations: r.finalCheck.violations });
      // gateLoop always returns llmMeta on both ok + leaked paths — guard kept
      // as a safety net if callLlm's return contract ever changes.
      if (r.llmMeta) {
        lastJudgeModel = r.llmMeta.model_resolved;
        lastPromptSha = r.llmMeta.prompt_sha256;
      }

      let action;
      try { action = JSON.parse(r.finalText); } catch { action = { action: "give_up", observation: "parse-error" }; }
      // Log the affordance list the persona was shown this step — diagnostic for whether
      // the type-loop is an affordance-exposure bug vs. a model action-selection floor.
      transcript.push({ ...action, affordancesShown: affordances, ts: new Date().toISOString() });

      // Loop-detection: a real naive student stops banging on the same dead element.
      // 3 identical (action,target) in a row → record a "stuck" finding and end the session.
      // `action` is the just-pushed transcript[-1]; comparing every recent entry to it
      // is equivalent to "all 3 are identical" (recent[-1] IS action).
      const recent = transcript.slice(-3);
      if (recent.length === 3 &&
          recent.every((t) => t.action === action.action && t.target === action.target)) {
        transcript.push({
          action: "stuck",
          target: action.target ?? "",
          observation: `Gave up — repeated "${action.action} ${action.target}" 3× with no progress.`,
          ts: new Date().toISOString(),
        });
        break;
      }

      if (action.action === "give_up") break;
      try {
        if (action.action === "click" && action.target) {
          await page.click(action.target, { timeout: 3000 });
        } else if (action.action === "type" && action.target) {
          await page.fill(action.target, action.payload ?? "");
        } else if (action.action === "navigate" && action.target) {
          const navTarget = action.target.startsWith("http")
            ? action.target
            : `${baseUrl}${action.target.startsWith("/") ? "" : "/"}${action.target}`;
          await page.goto(navTarget);
        } else if (action.action === "ask_sidekick") {
          await page.evaluate((q) => {
            const evt = new CustomEvent("standin-sidekick-ask", { detail: { question: q } });
            window.dispatchEvent(evt);
          }, action.payload).catch(() => {});
        } else if (action.action === "submit") {
          // Deterministic submit: the persona CHOSE `submit`; the controller resolves
          // CHECK ANSWER and clicks it. Role+name selector — the button has no
          // data-testid (see tutor-web/src/components/DrillStack.tsx:256-262).
          await page.getByRole("button", { name: /^check answer$/i }).click();
        }
      } catch (e) {
        transcript[transcript.length - 1].error = String(e).slice(0, 200);
      }

      await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});

      if (piggybackZ) {
        try {
          const lints = await page.evaluate(LINT_EVAL_SCRIPT);
          const screenshotPath = join(screenshotDir, `Y-Zpiggy-${sessionId}-${transcript.length}.png`);
          await page.screenshot({ path: screenshotPath, fullPage: true });
          zPiggyFindings.push({ step: transcript.length, lints, screenshot: screenshotPath });
        } catch (e) {
          zPiggyFindings.push({ step: transcript.length, lints: null, screenshot: null, error: String(e).slice(0, 150) });
        }
      }
    }

    const stamp = await getStamp(null, {
      judge_model_resolved: lastJudgeModel,
      judge_prompt_sha256: lastPromptSha,
    });
    const ts = stamp.ts_utc.replace(/[:.]/g, "-");
    mkdirSync(outputDir, { recursive: true });
    const docPath = join(outputDir, `DRAFT-Y-${taskId}-${ts}.md`);
    const md = [
      "---",
      "surface: Y",
      `session_id: ${sessionId}`,
      `task_id: ${taskId}`,
      `schema_path: ${schemaPath}`,
      "provenance:",
      `  git_head: ${stamp.git_head}`,
      `  bundle_hash: ${stamp.bundle_hash}`,
      `  live_dom_fingerprint: ${stamp.live_dom_fingerprint ?? "null"}`,
      `  ts_utc: ${stamp.ts_utc}`,
      `  surface_version: ${stamp.surface_version}`,
      `  judge_model_resolved: ${stamp.judge_model_resolved ?? "null"}`,
      `  judge_prompt_sha256: ${stamp.judge_prompt_sha256 ?? "null"}`,
      `model_resolved: ${lastJudgeModel ?? "null"}`,
      `calls_used: ${callsUsed}`,
      `duration_min: ${((Date.now() - t0) / 60000).toFixed(1)}`,
      `gate_violations: ${gateViolations.length}`,
      "---",
      "",
      `# Surface Y findings — task ${taskId}, session ${sessionId}`,
      "",
      "## Discovered unknown-unknowns",
      ...transcript.filter(t => t.observation).map(t => `- step ${transcript.indexOf(t) + 1}: ${t.observation}`),
      "",
      "## Schema-gate violations (naivety leakage)",
      ...gateViolations.map(v => `- step ${v.step}: referenced off-ledger concepts: ${v.violations.join(", ")}`),
      "",
      "## Session transcript",
      "| Step | Action | Target | Payload | Observation |",
      "|------|--------|--------|---------|-------------|",
      ...transcript.map((t, i) => {
        const payload = (t.payload || "").replace(/\s+/g, " ").replace(/\|/g, "\\|").trim().slice(0, 60);
        return `| ${i + 1} | ${t.action} | ${(t.target || "").slice(0, 40)} | ${payload} | ${(t.observation || "").slice(0, 80)} |`;
      }),
      "",
      ...(piggybackZ ? [
        `## Z piggyback (${zPiggyFindings.length} captures)`,
        ...zPiggyFindings.map(z => z.lints
          ? `- step ${z.step}: snake_case=${z.lints.snake_case.length}, low_contrast=${z.lints.low_contrast.length}, screenshot=\`${z.screenshot}\``
          : `- step ${z.step}: piggyback failed — ${z.error}`),
      ] : []),
      "",
      "## Affordances shown to persona (per step)",
      "_Diagnostic: the INTERACTIVE ELEMENTS block fed to the persona each step. Inspect whether CHECK ANSWER appeared as a clean, non-[disabled] clickable at the steps where the persona looped `type`._",
      "",
      ...transcript.flatMap((t, i) => t.affordancesShown
        ? [`### Step ${i + 1} — ${t.action} \`${(t.target || "").slice(0, 40)}\``, "```", t.affordancesShown, "```", ""]
        : []),
    ].join("\n");
    writeFileSync(docPath, md);
    return docPath;
  } finally {
    if (ownsBrowser) {
      if (ctx) await ctx.close().catch(() => {});
      await browser.close().catch(() => {});
    }
  }
}

// CLI
if (process.argv[1]?.endsWith("surface-y.mjs")) {
  const args = Object.fromEntries(process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.+)$/); return m ? [m[1], m[2]] : [a.replace(/^--/, ""), true];
  }));
  if (!args.task || !args.schema) {
    console.error("Usage: surface-y.mjs --task=<id> --schema=<path> [--model=<m>] [--max-calls=<n>] [--no-piggyback-z]");
    process.exit(2);
  }
  const docPath = await runStandin({
    taskId: args.task,
    schemaPath: args.schema,
    model: args.model,
    piggybackZ: !args["no-piggyback-z"],
    sessionId: args.session ?? `y-${Date.now()}`,
    // --max-calls bounds the OpenRouter spend for a controlled run; omitted = runStandin's 50 default.
    ...(args["max-calls"] ? { maxCallsPerSession: Number(args["max-calls"]) } : {}),
    outputDir: resolve(REPO_ROOT, "docs/standin-findings"),
    screenshotDir: resolve(REPO_ROOT, "docs/standin-findings/screenshots"),
  });
  console.log(`Wrote: ${docPath}`);
}
